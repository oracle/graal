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
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.meta.JavaKind;

@ExportLibrary(InteropLibrary.class)
public final class TruffleByteBuffer implements TruffleObject {

    private final ByteBuffer byteBuffer;

    private TruffleByteBuffer(@Pointer TruffleObject addressPtr, long byteCapacity) {
        if (byteCapacity < 0) {
            throw new IllegalArgumentException("negative requested capacity");
        }
        this.byteBuffer = NativeUtils.directByteBuffer(NativeUtils.interopAsPointer(addressPtr), byteCapacity);
    }

    private TruffleByteBuffer(ByteBuffer byteBuffer) {
        this.byteBuffer = Objects.requireNonNull(byteBuffer);
    }

    public static @Buffer TruffleObject create(ByteBuffer byteBuffer) {
        return new TruffleByteBuffer(byteBuffer);
    }

    public static @Buffer TruffleObject create(@Pointer TruffleObject addressPtr, long size, JavaKind kind) {
        long byteCapacity = Math.multiplyExact(size, kind.getByteCount());
        return new TruffleByteBuffer(addressPtr, byteCapacity);
    }

    @TruffleBoundary
    public static @Buffer TruffleObject allocateDirectStringUTF8(String string) {
        return allocateDirectString(string, StandardCharsets.UTF_8);
    }

    @TruffleBoundary
    public static @Buffer TruffleObject allocateDirectString(String string, Charset charset) {
        byte[] bytes = string.getBytes(charset);
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length + 1);
        buffer.put(bytes);
        buffer.put((byte) 0);
        return create(buffer);
    }

    /**
     * Allocates a managed ByteBuffer, the returned TruffleBuffer is <b>not</b> an
     * {@link InteropLibrary#isPointer(Object) interop pointer}.
     *
     * <p>
     * The returned object is managed by the GC, along with its native resources and cannot be
     * de-allocated explicitly.
     * 
     * @param byteCapacity buffer size in bytes
     * @throws IllegalArgumentException if the supplied capacity is negative
     * @throws OutOfMemoryError if the buffer cannot be allocated
     */
    public static @Buffer TruffleObject allocate(int byteCapacity) {
        return create(ByteBuffer.allocate(byteCapacity));
    }

    /**
     * Allocates a managed direct/native buffer, the returned TruffleBuffer is an
     * {@link InteropLibrary#isPointer(Object) interop pointer}.
     *
     * <p>
     * The returned object is managed by the GC, along with its native resources and cannot be
     * de-allocated explicitly.
     * 
     * @param byteCapacity buffer size in bytes
     * @throws IllegalArgumentException if the supplied capacity is negative
     * @throws OutOfMemoryError if the buffer cannot be allocated
     */
    public static @Buffer TruffleObject allocateDirect(int byteCapacity) {
        return create(ByteBuffer.allocateDirect(byteCapacity));
    }

    /**
     * Creates an unmanaged, native TruffleBuffer wrapping a of native memory. The returned
     * TruffleBuffer is an {@link InteropLibrary#isPointer(Object) interop pointer}.
     */
    public static @Buffer TruffleObject wrap(@Pointer TruffleObject address, int byteCapacity) {
        assert InteropLibrary.getUncached().isPointer(address);
        return create(NativeUtils.directByteBuffer(address, byteCapacity));
    }

    public static @Buffer TruffleObject wrap(@Pointer TruffleObject addressPtr, int elemCount, JavaKind kind) {
        return wrap(addressPtr, Math.multiplyExact(elemCount, kind.getByteCount()));
    }

    @ExportMessage
    boolean isPointer() {
        return this.byteBuffer.isDirect();
    }

    @ExportMessage
    long asPointer() throws UnsupportedMessageException {
        if (!isPointer()) {
            throw UnsupportedMessageException.create();
        }
        return NativeUtils.byteBufferAddress(this.byteBuffer);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasBufferElements() {
        return true;
    }

    @ExportMessage
    long getBufferSize() {
        return byteBuffer.capacity();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isBufferWritable() {
        return !byteBuffer.isReadOnly();
    }

    @ExportMessage
    byte readBufferByte(long byteOffset, @Shared("error") @Cached BranchProfile error) throws InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            return readByte(this.byteBuffer, index);
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
            readBytes(this.byteBuffer, index, destination, destinationOffset, length);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, length);
        }
    }

    @ExportMessage
    void writeBufferByte(long byteOffset, byte value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeByte(this.byteBuffer, index, value);
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
            return readShort(this.byteBuffer, order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferShort(ByteOrder order, long byteOffset, short value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeShort(this.byteBuffer, order, index, value);
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
            return readInt(this.byteBuffer, order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferInt(ByteOrder order, long byteOffset, int value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeInt(this.byteBuffer, order, index, value);
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
            return readLong(this.byteBuffer, order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferLong(ByteOrder order, long byteOffset, long value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeLong(this.byteBuffer, order, index, value);
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
            return readFloat(this.byteBuffer, order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferFloat(ByteOrder order, long byteOffset, float value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeFloat(this.byteBuffer, order, index, value);
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
            return readDouble(this.byteBuffer, order, index);
        } catch (ArithmeticException | IndexOutOfBoundsException e) {
            error.enter();
            throw InvalidBufferOffsetException.create(byteOffset, getBufferSize());
        }
    }

    @ExportMessage
    void writeBufferDouble(ByteOrder order, long byteOffset, double value, @Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException, InvalidBufferOffsetException {
        try {
            int index = Math.toIntExact(byteOffset);
            writeDouble(this.byteBuffer, order, index, value);
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
