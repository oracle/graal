/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.os;

import java.nio.ByteOrder;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.BufferedFileOperationSupport.BufferedFileOperationSupportHolder;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;

/**
 * Provides buffered, OS-independent operations on files. Most of the code is implemented in a way
 * that it can be used from uninterruptible code.
 */
public class BufferedFileOperationSupport {
    /**
     * Returns a {@link BufferedFileOperationSupport} singleton that uses little endian byte order.
     */
    @Fold
    public static BufferedFileOperationSupport littleEndian() {
        return BufferedFileOperationSupportHolder.singleton().littleEndian;
    }

    /**
     * Returns a {@link BufferedFileOperationSupport} singleton that uses big endian byte order.
     */
    @Fold
    public static BufferedFileOperationSupport bigEndian() {
        return BufferedFileOperationSupportHolder.singleton().bigEndian;
    }

    /**
     * Returns a {@link BufferedFileOperationSupport} singleton that uses the native byte order of
     * the underlying architecture.
     */
    @Fold
    public static BufferedFileOperationSupport nativeByteOrder() {
        return BufferedFileOperationSupportHolder.singleton().nativeOrder;
    }

    private static final int BUFFER_SIZE = 4 * 1024;
    private static final int LARGE_DATA_THRESHOLD = 1024;

    private final boolean useNativeByteOrder;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected BufferedFileOperationSupport(boolean useNativeByteOrder) {
        this.useNativeByteOrder = useNativeByteOrder;
    }

    /**
     * Allocate a {@link BufferedFile} for a {@link RawFileDescriptor}.
     * 
     * @return a {@link BufferedFile} if the {@link RawFileDescriptor} was
     *         {@link RawFileOperationSupport#isValid valid} and if the allocation was successful.
     *         Returns a null pointer otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public BufferedFile allocate(RawFileDescriptor fd, NmtCategory nmtCategory) {
        if (!rawFiles().isValid(fd)) {
            return WordFactory.nullPointer();
        }
        long filePosition = rawFiles().position(fd);
        if (filePosition < 0) {
            return WordFactory.nullPointer();
        }

        /* Use a single allocation for the struct and the corresponding buffer. */
        UnsignedWord totalSize = SizeOf.unsigned(BufferedFile.class).add(WordFactory.unsigned(BUFFER_SIZE));
        BufferedFile result = NullableNativeMemory.malloc(totalSize, nmtCategory);
        if (result.isNull()) {
            return WordFactory.nullPointer();
        }

        result.setFileDescriptor(fd);
        result.setFilePosition(filePosition);
        result.setBufferPos(getBufferStart(result));
        return result;
    }

    /**
     * Free the {@link BufferedFile} and its corresponding buffer. Be aware that this operation does
     * neither flush pending data nor close the underlying {@link RawFileDescriptor}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void free(BufferedFile f) {
        NullableNativeMemory.free(f);
    }

    /**
     * Flush the buffered data to the file.
     *
     * @return true if the data was flushed successful or there was no pending data that needed
     *         flushing.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean flush(BufferedFile f) {
        int unflushed = getUnflushedDataSize(f);
        if (unflushed == 0) {
            return true;
        }

        boolean success = rawFiles().write(f.getFileDescriptor(), getBufferStart(f), WordFactory.unsigned(unflushed));
        if (success) {
            f.setBufferPos(getBufferStart(f));
            f.setFilePosition(f.getFilePosition() + unflushed);
            assert f.getFilePosition() == rawFiles().position(f.getFileDescriptor());
        }
        return success;
    }

    /**
     * Gets the current position within a file.
     *
     * @return If the operation is successful, it returns the current file position. Otherwise, it
     *         returns a value less than 0.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long position(BufferedFile f) {
        return f.getFilePosition() + getUnflushedDataSize(f);
    }

    /**
     * Sets the current position within a file. As a side effect of this operation, pending data may
     * be flushed.
     *
     * @return true if the file position was updated to the given value, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean seek(BufferedFile f, long position) {
        if (position >= 0 && flush(f) && rawFiles().seek(f.getFileDescriptor(), position)) {
            f.setFilePosition(position);
            return true;
        }
        return false;
    }

    /**
     * Writes data to the current file position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean write(BufferedFile f, Pointer data, UnsignedWord size) {
        /* Large data is written directly to the file without any buffering. */
        if (size.aboveOrEqual(LARGE_DATA_THRESHOLD)) {
            if (flush(f) && rawFiles().write(f.getFileDescriptor(), data, size)) {
                f.setFilePosition(f.getFilePosition() + size.rawValue());
                assert f.getFilePosition() == rawFiles().position(f.getFileDescriptor());
                assert f.getBufferPos() == getBufferStart(f);
                return true;
            }
            return false;
        }

        /* Try to write the data to the buffer. */
        assert (int) size.rawValue() == size.rawValue();
        if (!ensureBufferSpace(f, (int) size.rawValue())) {
            return false;
        }

        Pointer pos = f.getBufferPos();
        UnmanagedMemoryUtil.copy(data, pos, size);
        f.setBufferPos(pos.add(size));
        return true;
    }

    /**
     * Writes data to the current file position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Array must not move.")
    public boolean write(BufferedFile f, byte[] data) {
        DynamicHub hub = KnownIntrinsics.readHub(data);
        UnsignedWord baseOffset = LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding());
        Pointer dataPtr = Word.objectToUntrackedPointer(data).add(baseOffset);
        return write(f, dataPtr, WordFactory.unsigned(data.length));
    }

    /**
     * Writes data to the current file position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeBoolean(BufferedFile f, boolean data) {
        return writeByte(f, (byte) (data ? 1 : 0));
    }

    /**
     * Writes data to the current file position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeByte(BufferedFile f, byte data) {
        if (!ensureBufferSpace(f, Byte.BYTES)) {
            return false;
        }

        Pointer pos = f.getBufferPos();
        pos.writeByte(0, data);
        f.setBufferPos(pos.add(Byte.BYTES));
        return true;
    }

    /**
     * Writes a short value in the specified byte order to the current file position and advances
     * the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeShort(BufferedFile f, short data) {
        if (!ensureBufferSpace(f, Short.BYTES)) {
            return false;
        }

        Pointer pos = f.getBufferPos();
        pos.writeShort(0, useNativeByteOrder ? data : Short.reverseBytes(data));
        f.setBufferPos(pos.add(Short.BYTES));
        return true;
    }

    /**
     * Writes a char value in the specified byte order to the current file position and advances the
     * file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeChar(BufferedFile f, char data) {
        if (!ensureBufferSpace(f, Character.BYTES)) {
            return false;
        }

        Pointer pos = f.getBufferPos();
        pos.writeChar(0, useNativeByteOrder ? data : Character.reverseBytes(data));
        f.setBufferPos(pos.add(Character.BYTES));
        return true;
    }

    /**
     * Writes an integer value in the specified byte order to the current file position and advances
     * the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeInt(BufferedFile f, int data) {
        if (!ensureBufferSpace(f, Integer.BYTES)) {
            return false;
        }

        Pointer pos = f.getBufferPos();
        pos.writeInt(0, useNativeByteOrder ? data : Integer.reverseBytes(data));
        f.setBufferPos(pos.add(Integer.BYTES));
        return true;
    }

    /**
     * Writes a long value in the specified byte order to the current file position and advances the
     * file position.
     *
     * @return true if the v was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeLong(BufferedFile f, long v) {
        if (!ensureBufferSpace(f, Long.BYTES)) {
            return false;
        }

        Pointer pos = f.getBufferPos();
        pos.writeLong(0, useNativeByteOrder ? v : Long.reverseBytes(v));
        f.setBufferPos(pos.add(Long.BYTES));
        return true;
    }

    /**
     * Writes a float value in the specified byte order to the current file position and advances
     * the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeFloat(BufferedFile f, float v) {
        return writeInt(f, Float.floatToIntBits(v));
    }

    /**
     * Writes a double value in the specified byte order to the current file position and advances
     * the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeDouble(BufferedFile f, double v) {
        return writeLong(f, Double.doubleToLongBits(v));
    }

    /**
     * Writes the String characters encoded as UTF8 to the current file position and advances the
     * file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeUTF8(BufferedFile f, String string) {
        boolean success = true;
        for (int index = 0; index < string.length() && success; index++) {
            success &= writeUTF8(f, UninterruptibleUtils.String.charAt(string, index));
        }
        return success;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean writeUTF8(BufferedFile f, char c) {
        boolean success;
        if (c <= 0x007F) {
            success = writeByte(f, (byte) c);
        } else if (c <= 0x07FF) {
            success = writeByte(f, (byte) (0xC0 | (c >> 6)));
            success = success && writeByte(f, (byte) (0x80 | (c & 0x3F)));
        } else {
            success = writeByte(f, (byte) (0xE0 | (c >> 12)));
            success = success && writeByte(f, (byte) (0x80 | ((c >> 6) & 0x3F)));
            success = success && writeByte(f, (byte) (0x80 | (c & 0x3F)));
        }
        return success;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getUnflushedDataSize(BufferedFile f) {
        UnsignedWord result = f.getBufferPos().subtract(getBufferStart(f));
        assert result.belowOrEqual(BUFFER_SIZE);
        return (int) result.rawValue();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer getBufferStart(BufferedFile f) {
        return ((Pointer) f).add(SizeOf.unsigned(BufferedFile.class));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean ensureBufferSpace(BufferedFile f, int size) {
        assert size <= BUFFER_SIZE : "only called for small data";
        if (getUnflushedDataSize(f) + size >= BUFFER_SIZE) {
            return flush(f);
        }
        return true;
    }

    @Fold
    static RawFileOperationSupport rawFiles() {
        /* The byte order does not matter because we only use byte-order-independent methods. */
        return RawFileOperationSupport.nativeByteOrder();
    }

    @RawStructure
    public interface BufferedFile extends PointerBase {
        @RawField
        RawFileDescriptor getFileDescriptor();

        @RawField
        void setFileDescriptor(RawFileDescriptor value);

        @RawField
        Pointer getBufferPos();

        @RawField
        void setBufferPos(Pointer value);

        @RawField
        long getFilePosition();

        @RawField
        void setFilePosition(long value);
    }

    public static class BufferedFileOperationSupportHolder {
        private final BufferedFileOperationSupport littleEndian;
        private final BufferedFileOperationSupport bigEndian;
        private final BufferedFileOperationSupport nativeOrder;

        @Platforms(Platform.HOSTED_ONLY.class)
        public BufferedFileOperationSupportHolder() {
            ByteOrder nativeByteOrder = ByteOrder.nativeOrder();
            assert nativeByteOrder == ByteOrder.LITTLE_ENDIAN || nativeByteOrder == ByteOrder.BIG_ENDIAN;

            this.littleEndian = new BufferedFileOperationSupport(ByteOrder.LITTLE_ENDIAN == nativeByteOrder);
            this.bigEndian = new BufferedFileOperationSupport(ByteOrder.BIG_ENDIAN == nativeByteOrder);
            this.nativeOrder = nativeByteOrder == ByteOrder.LITTLE_ENDIAN ? littleEndian : bigEndian;
        }

        @Fold
        static BufferedFileOperationSupportHolder singleton() {
            return ImageSingletons.lookup(BufferedFileOperationSupportHolder.class);
        }
    }
}

@AutomaticallyRegisteredFeature
class BufferedFileOperationFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (RawFileOperationSupport.isPresent()) {
            ImageSingletons.add(BufferedFileOperationSupportHolder.class, new BufferedFileOperationSupportHolder());
        }
    }
}
