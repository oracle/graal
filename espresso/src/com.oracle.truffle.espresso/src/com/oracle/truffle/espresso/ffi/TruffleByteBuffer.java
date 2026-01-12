/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.ffi;

import static com.oracle.truffle.espresso.ffi.memory.NativeMemory.MemoryAllocationException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.ffi.memory.MemoryBuffer;
import com.oracle.truffle.espresso.ffi.memory.NativeMemory;

@ExportLibrary(InteropLibrary.class)
public final class TruffleByteBuffer implements TruffleObject, AutoCloseable {

    private final MemoryBuffer byteBufferRecord;

    private TruffleByteBuffer(MemoryBuffer memoryBuffer) {
        this.byteBufferRecord = Objects.requireNonNull(memoryBuffer);
    }

    /**
     * Creates a TruffleByteBuffer from the given MemoryBuffer. The returned TruffleByteBuffer must
     * be explicitly closed to avoid leaking resources.
     */
    public static TruffleByteBuffer create(MemoryBuffer memoryBuffer) {
        return new TruffleByteBuffer(memoryBuffer);
    }

    /**
     * Creates a TruffleByteBuffer containing the UTF8 encoded bytes of the given string. The
     * returned TruffleByteBuffer must be explicitly closed to avoid leaking resources.
     * 
     * @param string the string to be buffered.
     * @param nativeMemory the nativeMemory where the TruffleByteBuffer is allocated.
     * @return a TruffleByteBuffer containing the UTF-8 bytes of the string
     */
    @TruffleBoundary
    public static TruffleByteBuffer allocateDirectStringUTF8(String string, NativeMemory nativeMemory) throws MemoryAllocationException {
        return allocateDirectString(string, StandardCharsets.UTF_8, nativeMemory);
    }

    @Override
    public void close() {
        this.byteBufferRecord.close();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isPointer() {
        return true;
    }

    /**
     * Creates a TruffleByteBuffer containing bytes of the given string encoded with the charset.
     * The returned TruffleByteBuffer must be explicitly closed to avoid leaking resources.
     *
     * @param string the string to be buffered.
     * @param charset the charset used for encoding
     * @param nativeMemory the nativeMemory where the TruffleByteBuffer is allocated.
     * @return a TruffleByteBuffer containing the UTF-8 bytes of the string
     */
    @TruffleBoundary
    public static TruffleByteBuffer allocateDirectString(String string, Charset charset, NativeMemory nativeMemory) throws MemoryAllocationException {
        byte[] bytes = string.getBytes(charset);
        MemoryBuffer memoryBuffer = nativeMemory.allocateMemoryBuffer(bytes.length + 1);
        ByteBuffer buffer = memoryBuffer.buffer();
        buffer.put(bytes);
        buffer.put((byte) 0);
        return create(memoryBuffer);
    }

    /**
     * There is no guarantee that the address actually corresponds to a host-native-address. It
     * should correspond to one of the following:
     * <ul>
     * <li>A {@link NativeMemory} address</li>
     * <li>A handle, such as in
     * {@link com.oracle.truffle.espresso.vm.VM#JVM_LoadLibrary(String, boolean)}</li>
     * <li>A special value, such as:
     * <ul>
     * <li>The NULL pointer in {@link RawPointer}</li>
     * <li>The sentinelPointer in
     * {@link com.oracle.truffle.espresso.libs.libjvm.impl.LibJVMSubstitutions}</li>
     * </ul>
     * </li>
     * </ul>
     */
    @ExportMessage
    long asPointer() {
        return byteBufferRecord.address();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasBufferElements() {
        return true;
    }

    @ExportMessage
    long getBufferSize() {
        return byteBufferRecord.buffer().capacity();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isBufferWritable() {
        return !byteBufferRecord.buffer().isReadOnly();
    }

    @ExportMessage
    byte readBufferByte(long byteOffset, @Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readByte(this.byteBufferRecord.buffer(), index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void readBuffer(long byteOffset, byte[] destination, int destinationOffset, int length, @Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        if (length < 0) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, length);
        }
        try {
            int index = Math.toIntExact(byteOffset);
            readBytes(this.byteBufferRecord.buffer(), index, destination, destinationOffset, length);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, length);
        }
    }

    @ExportMessage
    void writeBufferByte(long byteOffset, byte value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeByte(this.byteBufferRecord.buffer(), index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    short readBufferShort(ByteOrder order, long byteOffset, @Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readShort(this.byteBufferRecord.buffer(), order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferShort(ByteOrder order, long byteOffset, short value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeShort(this.byteBufferRecord.buffer(), order, index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    int readBufferInt(ByteOrder order, long byteOffset, @Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readInt(this.byteBufferRecord.buffer(), order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferInt(ByteOrder order, long byteOffset, int value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeInt(this.byteBufferRecord.buffer(), order, index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    long readBufferLong(ByteOrder order, long byteOffset, @Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readLong(this.byteBufferRecord.buffer(), order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferLong(ByteOrder order, long byteOffset, long value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeLong(this.byteBufferRecord.buffer(), order, index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    float readBufferFloat(ByteOrder order, long byteOffset, @Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readFloat(this.byteBufferRecord.buffer(), order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferFloat(ByteOrder order, long byteOffset, float value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeFloat(this.byteBufferRecord.buffer(), order, index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    double readBufferDouble(ByteOrder order, long byteOffset, @Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readDouble(this.byteBufferRecord.buffer(), order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferDouble(ByteOrder order, long byteOffset, double value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeDouble(this.byteBufferRecord.buffer(), order, index, value);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        } catch (ReadOnlyBufferException e) {
            error.enter();
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary(allowInlining = true)
    private static byte readByte(ByteBuffer byteBuffer, int index) {
        return byteBuffer.get(index);
    }

    @TruffleBoundary(allowInlining = true)
    private static void readBytes(ByteBuffer byteBuffer, int index, byte[] destination, int destinationOffset, int length) {
        byteBuffer.get(index, destination, destinationOffset, length);
    }

    @TruffleBoundary(allowInlining = true)
    private static short readShort(ByteBuffer byteBuffer, ByteOrder order, int index) {
        return byteBuffer.order(order).asShortBuffer().get(index);
    }

    @TruffleBoundary(allowInlining = true)
    private static double readDouble(ByteBuffer byteBuffer, ByteOrder order, int index) {
        return byteBuffer.order(order).asDoubleBuffer().get(index);
    }

    @TruffleBoundary(allowInlining = true)
    private static float readFloat(ByteBuffer byteBuffer, ByteOrder order, int index) {
        return byteBuffer.order(order).asFloatBuffer().get(index);
    }

    @TruffleBoundary(allowInlining = true)
    private static long readLong(ByteBuffer byteBuffer, ByteOrder order, int index) {
        return byteBuffer.order(order).asLongBuffer().get(index);
    }

    @TruffleBoundary(allowInlining = true)
    private static int readInt(ByteBuffer byteBuffer, ByteOrder order, int index) {
        return byteBuffer.order(order).asIntBuffer().get(index);
    }

    @TruffleBoundary(allowInlining = true)
    private static void writeByte(ByteBuffer byteBuffer, int index, byte value) {
        byteBuffer.put(index, value);
    }

    @TruffleBoundary(allowInlining = true)
    private static void writeShort(ByteBuffer byteBuffer, ByteOrder order, int index, short value) {
        byteBuffer.order(order).asShortBuffer().put(index, value);
    }

    @TruffleBoundary(allowInlining = true)
    private static void writeDouble(ByteBuffer byteBuffer, ByteOrder order, int index, double value) {
        byteBuffer.order(order).asDoubleBuffer().put(index, value);
    }

    @TruffleBoundary(allowInlining = true)
    private static void writeFloat(ByteBuffer byteBuffer, ByteOrder order, int index, float value) {
        byteBuffer.order(order).asFloatBuffer().put(index, value);
    }

    @TruffleBoundary(allowInlining = true)
    private static void writeLong(ByteBuffer byteBuffer, ByteOrder order, int index, long value) {
        byteBuffer.order(order).asLongBuffer().put(index, value);
    }

    @TruffleBoundary(allowInlining = true)
    private static void writeInt(ByteBuffer byteBuffer, ByteOrder order, int index, int value) {
        byteBuffer.order(order).asIntBuffer().put(index, value);
    }
}
