/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.memory;

import static java.lang.Integer.compareUnsigned;
import static java.lang.StrictMath.addExact;
import static java.lang.StrictMath.multiplyExact;
import static org.graalvm.wasm.constants.Sizes.MEMORY_PAGE_SIZE;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

import sun.misc.Unsafe;

public final class UnsafeWasmMemory extends WasmMemory {

    private long startAddress;
    private int size;

    private ByteBuffer buffer;

    private static final Unsafe unsafe;
    private static final long addressOffset;

    private UnsafeWasmMemory(int declaredMinSize, int declaredMaxSize, int initialSize, int maxAllowedSize) {
        super(declaredMinSize, declaredMaxSize, initialSize, maxAllowedSize);
        this.size = declaredMinSize;
        final long byteSize = byteSize();
        this.buffer = allocateBuffer(byteSize);
        this.startAddress = getBufferAddress(buffer);
    }

    public UnsafeWasmMemory(int declaredMinSize, int declaredMaxSize, int maxAllowedSize) {
        this(declaredMinSize, declaredMaxSize, declaredMinSize, maxAllowedSize);
    }

    @TruffleBoundary
    private static ByteBuffer allocateBuffer(final long byteSize) {
        assert (int) byteSize == byteSize : byteSize;
        try {
            return ByteBuffer.allocateDirect((int) byteSize);
        } catch (OutOfMemoryError error) {
            throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
        }
    }

    private static long getBufferAddress(ByteBuffer buffer) {
        return unsafe.getLong(buffer, addressOffset);
    }

    private void validateAddress(Node node, long address, int length) {
        assert length >= 1;
        long byteSize = byteSize();
        assert byteSize >= 0;
        if (address < 0 || address > byteSize - length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw trapOutOfBounds(node, address, length);
        }
    }

    @Override
    public void copy(Node node, int src, int dst, int n) {
        validateAddress(node, src, n);
        validateAddress(node, dst, n);
        unsafe.copyMemory(startAddress + src, startAddress + dst, n);
    }

    @Override
    public void reset() {
        size = declaredMinSize;
        buffer = allocateBuffer(byteSize());
        startAddress = getBufferAddress(buffer);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public long byteSize() {
        return Integer.toUnsignedLong(size) * MEMORY_PAGE_SIZE;
    }

    @Override
    @TruffleBoundary
    public boolean grow(int extraPageSize) {
        if (extraPageSize == 0) {
            invokeGrowCallback();
            return true;
        } else if (compareUnsigned(extraPageSize, maxAllowedSize) <= 0 && compareUnsigned(size() + extraPageSize, maxAllowedSize) <= 0) {
            // Condition above and limit on maxPageSize (see ModuleLimits#MAX_MEMORY_SIZE) ensure
            // computation of targetByteSize does not overflow.
            final int targetByteSize = multiplyExact(addExact(size(), extraPageSize), MEMORY_PAGE_SIZE);
            final long sourceByteSize = byteSize();
            ByteBuffer updatedBuffer = allocateBuffer(targetByteSize);
            final long updatedStartAddress = getBufferAddress(updatedBuffer);
            unsafe.copyMemory(startAddress, updatedStartAddress, sourceByteSize);
            buffer = updatedBuffer;
            startAddress = updatedStartAddress;
            size += extraPageSize;
            invokeGrowCallback();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int load_i32(Node node, long address) {
        validateAddress(node, address, 4);
        final int value = unsafe.getInt(startAddress + address);
        return value;
    }

    @Override
    public long load_i64(Node node, long address) {
        validateAddress(node, address, 8);
        final long value = unsafe.getLong(startAddress + address);
        return value;
    }

    @Override
    public float load_f32(Node node, long address) {
        validateAddress(node, address, 4);
        final float value = unsafe.getFloat(startAddress + address);
        return value;
    }

    @Override
    public double load_f64(Node node, long address) {
        validateAddress(node, address, 8);
        final double value = unsafe.getDouble(startAddress + address);
        return value;
    }

    @Override
    public int load_i32_8s(Node node, long address) {
        validateAddress(node, address, 1);
        final int value = unsafe.getByte(startAddress + address);
        return value;
    }

    @Override
    public int load_i32_8u(Node node, long address) {
        validateAddress(node, address, 1);
        final int value = 0x0000_00ff & unsafe.getByte(startAddress + address);
        return value;
    }

    @Override
    public int load_i32_16s(Node node, long address) {
        validateAddress(node, address, 2);
        final int value = unsafe.getShort(startAddress + address);
        return value;
    }

    @Override
    public int load_i32_16u(Node node, long address) {
        validateAddress(node, address, 2);
        final int value = 0x0000_ffff & unsafe.getShort(startAddress + address);
        return value;
    }

    @Override
    public long load_i64_8s(Node node, long address) {
        validateAddress(node, address, 1);
        final long value = unsafe.getByte(startAddress + address);
        return value;
    }

    @Override
    public long load_i64_8u(Node node, long address) {
        validateAddress(node, address, 1);
        final long value = 0x0000_0000_0000_00ffL & unsafe.getByte(startAddress + address);
        return value;
    }

    @Override
    public long load_i64_16s(Node node, long address) {
        validateAddress(node, address, 2);
        final long value = unsafe.getShort(startAddress + address);
        return value;
    }

    @Override
    public long load_i64_16u(Node node, long address) {
        validateAddress(node, address, 2);
        final long value = 0x0000_0000_0000_ffffL & unsafe.getShort(startAddress + address);
        return value;
    }

    @Override
    public long load_i64_32s(Node node, long address) {
        validateAddress(node, address, 4);
        final long value = unsafe.getInt(startAddress + address);
        return value;
    }

    @Override
    public long load_i64_32u(Node node, long address) {
        validateAddress(node, address, 4);
        final long value = 0x0000_0000_ffff_ffffL & unsafe.getInt(startAddress + address);
        return value;
    }

    @Override
    public void store_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        unsafe.putInt(startAddress + address, value);
    }

    @Override
    public void store_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        unsafe.putLong(startAddress + address, value);

    }

    @Override
    public void store_f32(Node node, long address, float value) {
        validateAddress(node, address, 4);
        unsafe.putFloat(startAddress + address, value);

    }

    @Override
    public void store_f64(Node node, long address, double value) {
        validateAddress(node, address, 8);
        unsafe.putDouble(startAddress + address, value);
    }

    @Override
    public void store_i32_8(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        unsafe.putByte(startAddress + address, value);
    }

    @Override
    public void store_i32_16(Node node, long address, short value) {
        validateAddress(node, address, 2);
        unsafe.putShort(startAddress + address, value);
    }

    @Override
    public void store_i64_8(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        unsafe.putByte(startAddress + address, value);
    }

    @Override
    public void store_i64_16(Node node, long address, short value) {
        validateAddress(node, address, 2);
        unsafe.putShort(startAddress + address, value);
    }

    @Override
    public void store_i64_32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        unsafe.putInt(startAddress + address, value);
    }

    @Override
    public WasmMemory duplicate() {
        final UnsafeWasmMemory other = new UnsafeWasmMemory(declaredMinSize, declaredMaxSize, size, maxAllowedSize);
        unsafe.copyMemory(this.startAddress, other.startAddress, this.byteSize());
        return other;
    }

    public void free() {
        buffer = null;
        startAddress = 0;
        size = 0;
    }

    public boolean freed() {
        return startAddress == 0;
    }

    @Override
    public void close() {
        if (!freed()) {
            free();
        }
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return buffer.duplicate();
    }

    static {
        try {
            final Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
            Field addressField = Buffer.class.getDeclaredField("address");
            addressOffset = unsafe.objectFieldOffset(addressField);
        } catch (Exception e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }
}
