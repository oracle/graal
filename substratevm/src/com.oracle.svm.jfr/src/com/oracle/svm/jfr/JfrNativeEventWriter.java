/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.jfr;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.annotate.DuplicatedInNativeCode;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.JavaLangSubstitutions;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.util.VMError;

/**
 * A JFR event writer that does not allocate any objects in the Java heap. Can only be used from
 * {@link Uninterruptible} code to prevent races between threads that try to write a native JFR
 * event and JFR-related code that may run at a safepoint (e.g., code that flushes the native buffer
 * of another thread). {@link Uninterruptible} is also necessary to ensure that all
 * {@link JfrNativeEventWriter}s are finished before {@link SubstrateJVM#endRecording} enters the
 * safepoint.
 */
@DuplicatedInNativeCode
public final class JfrNativeEventWriter {
    /*
     * Extra size added as a safety cushion when dimensioning memory. With varint encoding, the
     * worst case is associated with writing negative values. For example, writing a negative s1
     * (-1) will encode as 0xff 0x0f (2 bytes).
     */
    private static final int SIZE_SAFETY_CUSHION = 1;
    private static final int MAX_PADDED_INT_VALUE = (1 << 29) - 1;
    public static final int MAX_COMPRESSED_BYTE_VALUE = 127;

    private JfrNativeEventWriter() {
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static void beginEventWrite(JfrNativeEventWriterData data, boolean large) {
        assert SubstrateJVM.isRecording();
        assert isValid(data);
        assert getUncommittedSize(data).equal(0);
        if (large) {
            reserve(data, Integer.BYTES);
        } else {
            reserve(data, Byte.BYTES);
        }
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static UnsignedWord endEventWrite(JfrNativeEventWriterData data, boolean large) {
        if (!isValid(data)) {
            return WordFactory.unsigned(0);
        }

        UnsignedWord written = getUncommittedSize(data);
        if (large) {
            // Write a 4 byte size and commit the event if any payload was written.
            if (written.aboveThan(Integer.BYTES)) {
                assert written.belowOrEqual(MAX_PADDED_INT_VALUE);
                Pointer currentPos = data.getCurrentPos();
                data.setCurrentPos(data.getStartPos());
                putInt(data, makePaddedInt((int) written.rawValue()));
                data.setCurrentPos(currentPos);
                commitEvent(data);
            }
        } else {
            // Abort if event size will not fit in one byte (compressed).
            if (written.aboveThan(MAX_COMPRESSED_BYTE_VALUE)) {
                reset(data);
                written = WordFactory.unsigned(0);
            } else {
                // Write a 1 byte size and commit the event if any payload was written.
                if (written.aboveThan(Byte.BYTES)) {
                    assert written.belowOrEqual(MAX_COMPRESSED_BYTE_VALUE);
                    Pointer currentPos = data.getCurrentPos();
                    data.setCurrentPos(data.getStartPos());
                    putByte(data, (byte) written.rawValue());
                    data.setCurrentPos(currentPos);
                    commitEvent(data);
                }
            }
        }
        return written;
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static void putBoolean(JfrNativeEventWriterData data, boolean i) {
        byte value = (byte) (i ? 1 : 0);
        putByte(data, value);
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static void putByte(JfrNativeEventWriterData data, byte i) {
        if (ensureSize(data, Byte.BYTES)) {
            putUncheckedByte(data, i);
        }
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static void putChar(JfrNativeEventWriterData data, char v) {
        if (ensureSize(data, Character.BYTES)) {
            putUncheckedLong(data, v);
        }
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static void putShort(JfrNativeEventWriterData data, short v) {
        if (ensureSize(data, Short.BYTES)) {
            putUncheckedLong(data, v & 0xFFFF);
        }
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static void putInt(JfrNativeEventWriterData data, int v) {
        if (ensureSize(data, Integer.BYTES)) {
            putUncheckedLong(data, v & 0x00000000ffffffffL);
        }
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static void putLong(JfrNativeEventWriterData data, long v) {
        if (ensureSize(data, Long.BYTES)) {
            putUncheckedLong(data, v);
        }
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static void putString(JfrNativeEventWriterData data, String string) {
        byte[] byteString = JavaLangSubstitutions.getBytes(string);
        if (byteString.length == 0) {
            putByte(data, JfrChunkWriter.StringEncoding.EMPTY_STRING.byteValue);
        } else {
            int mUTF8Length = UninterruptibleUtils.String.modifiedUTF8Length(string, false);
            putByte(data, JfrChunkWriter.StringEncoding.UTF8_BYTE_ARRAY.byteValue);
            putInt(data, mUTF8Length);
            if (ensureSize(data, mUTF8Length)) {
                Pointer newPosition = UninterruptibleUtils.String.toModifiedUTF8(string, data.getCurrentPos(), data.getEndPos(), false);
                data.setCurrentPos(newPosition);
            }
        }
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static void putEventThread(JfrNativeEventWriterData data) {
        putThread(data, CurrentIsolate.getCurrentThread());
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static void putThread(JfrNativeEventWriterData data, IsolateThread isolateThread) {
        if (isolateThread.isNull()) {
            putLong(data, 0L);
        } else {
            long threadId = SubstrateJVM.get().getThreadId(isolateThread);
            putLong(data, threadId);
        }
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static void putClass(JfrNativeEventWriterData data, Class<?> aClass) {
        if (aClass == null) {
            putLong(data, 0L);
        } else {
            putLong(data, SubstrateJVM.get().getClassId(aClass));
        }
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static boolean ensureSize(JfrNativeEventWriterData data, int requested) {
        assert requested > 0;
        if (!isValid(data)) {
            return false;
        }

        int totalRequested = requested + SIZE_SAFETY_CUSHION;
        if (getAvailableSize(data).belowThan(totalRequested)) {
            if (!accommodate(data, getUncommittedSize(data), totalRequested)) {
                assert !isValid(data);
                return false;
            }
        }
        assert getAvailableSize(data).aboveOrEqual(totalRequested);
        return true;
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static void reserve(JfrNativeEventWriterData data, int size) {
        if (ensureSize(data, size)) {
            increaseCurrentPos(data, size);
        }
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static void hardReset(JfrNativeEventWriterData data) {
        JfrBuffer buffer = data.getJfrBuffer();
        data.setStartPos(buffer.getPos());
        data.setCurrentPos(buffer.getPos());
        data.setEndPos(JfrBufferAccess.getDataEnd(buffer));
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static void reset(JfrNativeEventWriterData data) {
        data.setCurrentPos(data.getStartPos());
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static void cancel(JfrNativeEventWriterData data) {
        data.setEndPos(WordFactory.nullPointer());
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static boolean accommodate(JfrNativeEventWriterData data, UnsignedWord uncommitted, int requested) {
        JfrBuffer newBuffer = accomodate0(data, uncommitted, requested);
        if (newBuffer.isNull()) {
            cancel(data);
            return false;
        }

        data.setJfrBuffer(newBuffer);
        hardReset(data);
        increaseCurrentPos(data, uncommitted);
        return true;
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static JfrBuffer accomodate0(JfrNativeEventWriterData data, UnsignedWord uncommitted, int requested) {
        JfrBuffer oldBuffer = data.getJfrBuffer();
        switch (oldBuffer.getBufferType()) {
            case THREAD_LOCAL_NATIVE:
                return JfrThreadLocal.flush(oldBuffer, uncommitted, requested);
            case C_HEAP:
                return reuseOrReallocateBuffer(oldBuffer, uncommitted, requested);
            default:
                throw VMError.shouldNotReachHere("Unexpected type of buffer.");
        }
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static JfrBuffer reuseOrReallocateBuffer(JfrBuffer oldBuffer, UnsignedWord uncommitted, int requested) {
        UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(oldBuffer);
        UnsignedWord totalUsedBytes = unflushedSize.add(uncommitted);
        UnsignedWord minNewSize = totalUsedBytes.add(requested);

        if (oldBuffer.getSize().belowThan(minNewSize)) {
            // Grow the buffer because it is too small.
            UnsignedWord newSize = oldBuffer.getSize();
            while (newSize.belowThan(minNewSize)) {
                newSize = newSize.multiply(2);
            }

            JfrBuffer result = JfrBufferAccess.allocate(newSize, oldBuffer.getBufferType());
            if (result.isNull()) {
                return WordFactory.nullPointer();
            }

            // Copy all unflushed data (no matter if committed or uncommitted) from the old buffer
            // to the new buffer.
            UnmanagedMemoryUtil.copy(oldBuffer.getTop(), result.getPos(), totalUsedBytes);
            JfrBufferAccess.increasePos(result, unflushedSize);

            JfrBufferAccess.free(oldBuffer);

            assert result.getSize().aboveThan(minNewSize);
            return result;
        } else {
            // Reuse the existing buffer because enough data was already flushed in the meanwhile.
            // For that, copy all unflushed data (no matter if committed or uncommitted) to the
            // beginning of the buffer.
            UnmanagedMemoryUtil.copy(oldBuffer.getTop(), JfrBufferAccess.getDataStart(oldBuffer), totalUsedBytes);
            JfrBufferAccess.reinitialize(oldBuffer);
            JfrBufferAccess.increasePos(oldBuffer, unflushedSize);
            return oldBuffer;
        }
    }

    /**
     * Only needs to be called when non-event data is written to a buffer. For events,
     * {@link #beginEventWrite} and {@link #endEventWrite} should be used instead.
     */
    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static boolean commit(JfrNativeEventWriterData data) {
        if (!isValid(data)) {
            return false;
        }
        return commitEvent(data);
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static boolean commitEvent(JfrNativeEventWriterData data) {
        JfrBuffer buffer = data.getJfrBuffer();
        assert isValid(data);
        assert buffer.getPos().equal(data.getStartPos());
        assert JfrBufferAccess.getDataEnd(data.getJfrBuffer()).equal(data.getEndPos());

        Pointer newPosition = data.getCurrentPos();
        buffer.setPos(newPosition);
        data.setStartPos(newPosition);
        return true;
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static boolean isValid(JfrNativeEventWriterData data) {
        return data.getEndPos().isNonNull();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int makePaddedInt(int v) {
        assert v <= MAX_PADDED_INT_VALUE;
        // bit 0-6 + pad => bit 24 - 31
        long b1 = (((v >>> 0) & 0x7F) | 0x80) << 24;

        // bit 7-13 + pad => bit 16 - 23
        long b2 = (((v >>> 7) & 0x7F) | 0x80) << 16;

        // bit 14-20 + pad => bit 8 - 15
        long b3 = (((v >>> 14) & 0x7F) | 0x80) << 8;

        // bit 21-28 => bit 0 - 7
        long b4 = (((v >>> 21) & 0x7F)) << 0;

        return (int) (b1 + b2 + b3 + b4);
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static void putUncheckedLong(JfrNativeEventWriterData data, long value) {
        long v = value;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte(data, (byte) v); // 0-6
            return;
        }
        putUncheckedByte(data, (byte) (v | 0x80L)); // 0-6
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte(data, (byte) v); // 7-13
            return;
        }
        putUncheckedByte(data, (byte) (v | 0x80L)); // 7-13
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte(data, (byte) v); // 14-20
            return;
        }
        putUncheckedByte(data, (byte) (v | 0x80L)); // 14-20
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte(data, (byte) v); // 21-27
            return;
        }
        putUncheckedByte(data, (byte) (v | 0x80L)); // 21-27
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte(data, (byte) v); // 28-34
            return;
        }
        putUncheckedByte(data, (byte) (v | 0x80L)); // 28-34
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte(data, (byte) v); // 35-41
            return;
        }
        putUncheckedByte(data, (byte) (v | 0x80L)); // 35-41
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte(data, (byte) v); // 42-48
            return;
        }
        putUncheckedByte(data, (byte) (v | 0x80L)); // 42-48
        v >>>= 7;

        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte(data, (byte) v); // 49-55
            return;
        }
        putUncheckedByte(data, (byte) (v | 0x80L)); // 49-55
        putUncheckedByte(data, (byte) (v >>> 7)); // 56-63, last byte as is.
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static void putUncheckedByte(JfrNativeEventWriterData data, byte i) {
        // This method is only called if ensureSize() succeeded earlier.
        assert getAvailableSize(data).aboveOrEqual(Byte.BYTES);
        data.getCurrentPos().writeByte(0, i);
        increaseCurrentPos(data, Byte.BYTES);
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static UnsignedWord getAvailableSize(JfrNativeEventWriterData data) {
        return data.getEndPos().subtract(data.getCurrentPos());
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static UnsignedWord getUncommittedSize(JfrNativeEventWriterData data) {
        return data.getCurrentPos().subtract(data.getStartPos());
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static void increaseCurrentPos(JfrNativeEventWriterData data, int bytes) {
        data.setCurrentPos(data.getCurrentPos().add(bytes));
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    private static void increaseCurrentPos(JfrNativeEventWriterData data, UnsignedWord bytes) {
        data.setCurrentPos(data.getCurrentPos().add(bytes));
    }
}
