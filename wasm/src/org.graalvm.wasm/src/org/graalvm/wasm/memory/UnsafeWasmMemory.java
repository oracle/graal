/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.Long.compareUnsigned;
import static java.lang.StrictMath.addExact;
import static java.lang.StrictMath.multiplyExact;
import static org.graalvm.wasm.constants.Sizes.MEMORY_PAGE_SIZE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

import sun.misc.Unsafe;

public final class UnsafeWasmMemory extends WasmMemory {

    private long startAddress;
    private long size;

    private ByteBuffer buffer;

    private static final Unsafe unsafe;
    private static final long addressOffset;

    private UnsafeWasmMemory(long declaredMinSize, long declaredMaxSize, long initialSize, long maxAllowedSize, boolean indexType64, boolean shared) {
        super(declaredMinSize, declaredMaxSize, initialSize, maxAllowedSize, indexType64, shared);
        this.size = declaredMinSize;
        final long byteSize = byteSize();
        this.buffer = allocateBuffer(byteSize);
        this.startAddress = getBufferAddress(buffer);
    }

    UnsafeWasmMemory(long declaredMinSize, long declaredMaxSize, long maxAllowedSize, boolean indexType64, boolean shared) {
        this(declaredMinSize, declaredMaxSize, declaredMinSize, maxAllowedSize, indexType64, shared);
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

    private static void validateAtomicAddress(Node node, long address, int length) {
        if ((address & (length - 1)) != 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw trapUnalignedAtomic(node, address, length);
        }
    }

    @Override
    public void reset() {
        size = declaredMinSize;
        buffer = allocateBuffer(byteSize());
        startAddress = getBufferAddress(buffer);
        currentMinSize = declaredMinSize;
    }

    @Override
    public synchronized long size() {
        return size;
    }

    @Override
    public long byteSize() {
        return size * MEMORY_PAGE_SIZE;
    }

    @Override
    @TruffleBoundary
    public synchronized boolean grow(long extraPageSize) {
        if (extraPageSize == 0) {
            invokeGrowCallback();
            return true;
        } else if (compareUnsigned(extraPageSize, maxAllowedSize) <= 0 && compareUnsigned(size() + extraPageSize, maxAllowedSize) <= 0) {
            // Condition above and limit on maxPageSize (see ModuleLimits#MAX_MEMORY_SIZE) ensure
            // computation of targetByteSize does not overflow.
            final long targetByteSize = multiplyExact(addExact(size(), extraPageSize), MEMORY_PAGE_SIZE);
            final long sourceByteSize = byteSize();
            ByteBuffer updatedBuffer = allocateBuffer(targetByteSize);
            final long updatedStartAddress = getBufferAddress(updatedBuffer);
            unsafe.copyMemory(startAddress, updatedStartAddress, sourceByteSize);
            buffer = updatedBuffer;
            startAddress = updatedStartAddress;
            size += extraPageSize;
            currentMinSize = size;
            invokeGrowCallback();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int load_i32(Node node, long address) {
        validateAddress(node, address, 4);
        return unsafe.getInt(startAddress + address);
    }

    @Override
    public long load_i64(Node node, long address) {
        validateAddress(node, address, 8);
        return unsafe.getLong(startAddress + address);
    }

    @Override
    public float load_f32(Node node, long address) {
        validateAddress(node, address, 4);
        return unsafe.getFloat(startAddress + address);
    }

    @Override
    public double load_f64(Node node, long address) {
        validateAddress(node, address, 8);
        return unsafe.getDouble(startAddress + address);
    }

    @Override
    public int load_i32_8s(Node node, long address) {
        validateAddress(node, address, 1);
        return unsafe.getByte(startAddress + address);
    }

    @Override
    public int load_i32_8u(Node node, long address) {
        validateAddress(node, address, 1);
        return 0x0000_00ff & unsafe.getByte(startAddress + address);
    }

    @Override
    public int load_i32_16s(Node node, long address) {
        validateAddress(node, address, 2);
        return unsafe.getShort(startAddress + address);
    }

    @Override
    public int load_i32_16u(Node node, long address) {
        validateAddress(node, address, 2);
        return 0x0000_ffff & unsafe.getShort(startAddress + address);
    }

    @Override
    public long load_i64_8s(Node node, long address) {
        validateAddress(node, address, 1);
        return unsafe.getByte(startAddress + address);
    }

    @Override
    public long load_i64_8u(Node node, long address) {
        validateAddress(node, address, 1);
        return 0x0000_0000_0000_00ffL & unsafe.getByte(startAddress + address);
    }

    @Override
    public long load_i64_16s(Node node, long address) {
        validateAddress(node, address, 2);
        return unsafe.getShort(startAddress + address);
    }

    @Override
    public long load_i64_16u(Node node, long address) {
        validateAddress(node, address, 2);
        return 0x0000_0000_0000_ffffL & unsafe.getShort(startAddress + address);
    }

    @Override
    public long load_i64_32s(Node node, long address) {
        validateAddress(node, address, 4);
        return unsafe.getInt(startAddress + address);
    }

    @Override
    public long load_i64_32u(Node node, long address) {
        validateAddress(node, address, 4);
        return 0x0000_0000_ffff_ffffL & unsafe.getInt(startAddress + address);
    }

    @Override
    public Vector128 load_i128(Node node, long address) {
        validateAddress(node, address, 16);
        byte[] bytes = new byte[16];
        unsafe.copyMemory(null, startAddress + address, bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET, 16);
        return Vector128.ofBytes(bytes);
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
    public void store_i128(Node node, long address, Vector128 value) {
        validateAddress(node, address, 16);
        unsafe.copyMemory(value.asBytes(), Unsafe.ARRAY_BYTE_BASE_OFFSET, null, startAddress + address, 16);
    }

    @Override
    public int atomic_load_i32(Node node, long address) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        return unsafe.getIntVolatile(null, startAddress + address);
    }

    @Override
    public long atomic_load_i64(Node node, long address) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        return unsafe.getLongVolatile(null, startAddress + address);
    }

    @Override
    public int atomic_load_i32_8u(Node node, long address) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        return 0x0000_00ff & unsafe.getByteVolatile(null, startAddress + address);
    }

    @Override
    public int atomic_load_i32_16u(Node node, long address) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        return 0x0000_ffff & unsafe.getShortVolatile(null, startAddress + address);
    }

    @Override
    public long atomic_load_i64_8u(Node node, long address) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        return 0x0000_0000_0000_00ffL & unsafe.getByteVolatile(null, startAddress + address);
    }

    @Override
    public long atomic_load_i64_16u(Node node, long address) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        return 0x0000_0000_0000_ffffL & unsafe.getShortVolatile(null, startAddress + address);
    }

    @Override
    public long atomic_load_i64_32u(Node node, long address) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        return 0x0000_0000_ffff_ffffL & unsafe.getIntVolatile(null, startAddress + address);
    }

    @Override
    public void atomic_store_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        unsafe.putIntVolatile(null, startAddress + address, value);
    }

    @Override
    public void atomic_store_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        unsafe.putLongVolatile(null, startAddress + address, value);
    }

    @Override
    public void atomic_store_i32_8(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        unsafe.putByteVolatile(null, startAddress + address, value);
    }

    @Override
    public void atomic_store_i32_16(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        unsafe.putShortVolatile(null, startAddress + address, value);
    }

    @Override
    public void atomic_store_i64_8(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        unsafe.putByteVolatile(null, startAddress + address, value);
    }

    @Override
    public void atomic_store_i64_16(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        unsafe.putShortVolatile(null, startAddress + address, value);
    }

    @Override
    public void atomic_store_i64_32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        unsafe.putIntVolatile(null, startAddress + address, value);
    }

    @Override
    public int atomic_rmw_add_i32_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v + value)) != v);
        return 0x0000_00ff & v;
    }

    @Override
    public int atomic_rmw_add_i32_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v + value)) != v);
        return 0x0000_ffff & v;
    }

    @Override
    public int atomic_rmw_add_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        return unsafe.getAndAddInt(null, startAddress + address, value);
    }

    @Override
    public long atomic_rmw_add_i64_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v + value)) != v);
        return 0x0000_0000_0000_00ffL & v;
    }

    @Override
    public long atomic_rmw_add_i64_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v + value)) != v);
        return 0x0000_0000_0000_ffffL & v;
    }

    @Override
    public long atomic_rmw_add_i64_32u(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v = unsafe.getAndAddInt(null, startAddress + address, value);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @Override
    public long atomic_rmw_add_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        return unsafe.getAndAddLong(null, startAddress + address, value);
    }

    @Override
    public int atomic_rmw_sub_i32_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v - value)) != v);
        return 0x0000_00ff & v;
    }

    @Override
    public int atomic_rmw_sub_i32_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v - value)) != v);
        return 0x0000_ffff & v;
    }

    @Override
    public int atomic_rmw_sub_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        return unsafe.getAndAddInt(null, startAddress + address, -value);
    }

    @Override
    public long atomic_rmw_sub_i64_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v - value)) != v);
        return 0x0000_0000_0000_00ffL & v;
    }

    @Override
    public long atomic_rmw_sub_i64_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v - value)) != v);
        return 0x0000_0000_0000_ffffL & v;
    }

    @Override
    public long atomic_rmw_sub_i64_32u(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v = unsafe.getAndAddInt(null, startAddress + address, -value);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @Override
    public long atomic_rmw_sub_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        return unsafe.getAndAddLong(null, startAddress + address, -value);
    }

    @Override
    public int atomic_rmw_and_i32_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v & value)) != v);
        return 0x0000_00ff & v;
    }

    @Override
    public int atomic_rmw_and_i32_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v & value)) != v);
        return 0x0000_ffff & v;
    }

    @Override
    public int atomic_rmw_and_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v;
        do {
            v = unsafe.getIntVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeInt(startAddress, address, v, v & value) != v);
        return v;
    }

    @Override
    public long atomic_rmw_and_i64_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v & value)) != v);
        return 0x0000_0000_0000_00ffL & v;
    }

    @Override
    public long atomic_rmw_and_i64_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v & value)) != v);
        return 0x0000_0000_0000_ffffL & v;
    }

    @Override
    public long atomic_rmw_and_i64_32u(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v;
        do {
            v = unsafe.getIntVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeInt(startAddress, address, v, v & value) != v);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @Override
    public long atomic_rmw_and_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        long v;
        do {
            v = unsafe.getLongVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeLong(startAddress, address, v, v & value) != v);
        return v;
    }

    @Override
    public int atomic_rmw_or_i32_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v | value)) != v);
        return 0x0000_00ff & v;
    }

    @Override
    public int atomic_rmw_or_i32_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v | value)) != v);
        return 0x0000_ffff & v;
    }

    @Override
    public int atomic_rmw_or_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v;
        do {
            v = unsafe.getIntVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeInt(startAddress, address, v, v | value) != v);
        return v;
    }

    @Override
    public long atomic_rmw_or_i64_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v | value)) != v);
        return 0x0000_0000_0000_00ffL & v;
    }

    @Override
    public long atomic_rmw_or_i64_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v | value)) != v);
        return 0x0000_0000_0000_ffffL & v;
    }

    @Override
    public long atomic_rmw_or_i64_32u(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v;
        do {
            v = unsafe.getIntVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeInt(startAddress, address, v, v | value) != v);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @Override
    public long atomic_rmw_or_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        long v;
        do {
            v = unsafe.getLongVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeLong(startAddress, address, v, v | value) != v);
        return v;
    }

    @Override
    public int atomic_rmw_xor_i32_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v ^ value)) != v);
        return 0x0000_00ff & v;
    }

    @Override
    public int atomic_rmw_xor_i32_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v ^ value)) != v);
        return 0x0000_ffff & v;
    }

    @Override
    public int atomic_rmw_xor_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v;
        do {
            v = unsafe.getIntVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeInt(startAddress, address, v, v ^ value) != v);
        return v;
    }

    @Override
    public long atomic_rmw_xor_i64_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v ^ value)) != v);
        return 0x0000_0000_0000_00ffL & v;
    }

    @Override
    public long atomic_rmw_xor_i64_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v ^ value)) != v);
        return 0x0000_0000_0000_ffffL & v;
    }

    @Override
    public long atomic_rmw_xor_i64_32u(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v;
        do {
            v = unsafe.getIntVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeInt(startAddress, address, v, v ^ value) != v);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @Override
    public long atomic_rmw_xor_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        long v;
        do {
            v = unsafe.getLongVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeLong(startAddress, address, v, v ^ value) != v);
        return v;
    }

    @Override
    public int atomic_rmw_xchg_i32_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, value) != v);
        return 0x0000_00ff & v;
    }

    @Override
    public int atomic_rmw_xchg_i32_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, value) != v);
        return 0x0000_ffff & v;
    }

    @Override
    public int atomic_rmw_xchg_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        return unsafe.getAndSetInt(null, startAddress + address, value);
    }

    @Override
    public long atomic_rmw_xchg_i64_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, value) != v);
        return 0x0000_0000_0000_00ffL & v;
    }

    @Override
    public long atomic_rmw_xchg_i64_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, value) != v);
        return 0x0000_0000_0000_ffffL & v;
    }

    @Override
    public long atomic_rmw_xchg_i64_32u(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v = unsafe.getAndSetInt(null, startAddress + address, value);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @Override
    public long atomic_rmw_xchg_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        return unsafe.getAndSetLong(null, startAddress + address, value);
    }

    @Override
    public int atomic_rmw_cmpxchg_i32_8u(Node node, long address, byte expected, byte replacement) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v = UnsafeUtilities.compareAndExchangeByte(startAddress, address, expected, replacement);
        return 0x0000_00ff & v;
    }

    @Override
    public int atomic_rmw_cmpxchg_i32_16u(Node node, long address, short expected, short replacement) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v = UnsafeUtilities.compareAndExchangeShort(startAddress, address, expected, replacement);
        return 0x0000_ffff & v;
    }

    @Override
    public int atomic_rmw_cmpxchg_i32(Node node, long address, int expected, int replacement) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        return UnsafeUtilities.compareAndExchangeInt(startAddress, address, expected, replacement);
    }

    @Override
    public long atomic_rmw_cmpxchg_i64_8u(Node node, long address, byte expected, byte replacement) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v = UnsafeUtilities.compareAndExchangeByte(startAddress, address, expected, replacement);
        return 0x0000_0000_0000_00ffL & v;
    }

    @Override
    public long atomic_rmw_cmpxchg_i64_16u(Node node, long address, short expected, short replacement) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v = UnsafeUtilities.compareAndExchangeShort(startAddress, address, expected, replacement);
        return 0x0000_0000_0000_ffffL & v;
    }

    @Override
    public long atomic_rmw_cmpxchg_i64_32u(Node node, long address, int expected, int replacement) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v = UnsafeUtilities.compareAndExchangeInt(startAddress, address, expected, replacement);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @Override
    public long atomic_rmw_cmpxchg_i64(Node node, long address, long expected, long replacement) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        return UnsafeUtilities.compareAndExchangeLong(startAddress, address, expected, replacement);
    }

    @Override
    @TruffleBoundary
    public int atomic_notify(Node node, long address, int count) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        if (!this.isShared()) {
            return 0;
        }
        return invokeNotifyCallback(address, count);
    }

    @Override
    @TruffleBoundary
    public int atomic_wait32(Node node, long address, int expected, long timeout) {
        validateAtomicAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        if (!this.isShared()) {
            throw trapUnsharedMemory(node);
        }
        return invokeWaitCallback(address, expected, timeout, false);
    }

    @Override
    @TruffleBoundary
    public int atomic_wait64(Node node, long address, long expected, long timeout) {
        validateAtomicAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        if (!this.isShared()) {
            throw trapUnsharedMemory(node);
        }
        return invokeWaitCallback(address, expected, timeout, true);
    }

    @Override
    public void initialize(byte[] source, int sourceOffset, long destinationOffset, int length) {
        for (int i = 0; i < length; i++) {
            unsafe.putByte(startAddress + destinationOffset + i, source[sourceOffset + i]);
        }
    }

    @Override
    public void initializeUnsafe(long sourceAddress, int sourceOffset, long destinationOffset, int length) {
        assert destinationOffset + length <= byteSize();
        unsafe.copyMemory(sourceAddress + sourceOffset, startAddress + destinationOffset, length);
    }

    @Override
    public void fill(long offset, long length, byte value) {
        assert offset + length <= byteSize();
        unsafe.setMemory(startAddress + offset, length, value);
    }

    @Override
    public void copyFrom(WasmMemory source, long sourceOffset, long destinationOffset, long length) {
        assert source instanceof UnsafeWasmMemory;
        assert destinationOffset + length < byteSize();
        final UnsafeWasmMemory s = (UnsafeWasmMemory) source;
        unsafe.copyMemory(s.startAddress + sourceOffset, this.startAddress + destinationOffset, length);
    }

    @Override
    public WasmMemory duplicate() {
        final UnsafeWasmMemory other = new UnsafeWasmMemory(declaredMinSize, declaredMaxSize, size, maxAllowedSize, indexType64, shared);
        unsafe.copyMemory(this.startAddress, other.startAddress, this.byteSize());
        return other;
    }

    public void free() {
        buffer = null;
        startAddress = 0;
        size = 0;
    }

    @Override
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

    @Override
    @TruffleBoundary
    public int copyFromStream(Node node, InputStream stream, int offset, int length) throws IOException {
        if (outOfBounds(offset, length)) {
            throw trapOutOfBounds(node, offset, length);
        }
        int totalBytesRead = 0;
        for (int i = 0; i < length; i++) {
            int byteRead = stream.read();
            if (byteRead == -1) {
                if (totalBytesRead == 0) {
                    return -1;
                }
                break;
            }
            unsafe.putByte(startAddress + offset + i, (byte) byteRead);
            totalBytesRead++;
        }
        return totalBytesRead;
    }

    @Override
    @TruffleBoundary
    public void copyToStream(Node node, OutputStream stream, int offset, int length) throws IOException {
        if (outOfBounds(offset, length)) {
            throw trapOutOfBounds(node, offset, length);
        }
        for (int i = 0; i < length; i++) {
            byte b = unsafe.getByte(startAddress + offset + i);
            stream.write(b & 0x0000_00ff);
        }
    }

    @Override
    public void copyToBuffer(Node node, byte[] dst, long srcOffset, int dstOffset, int length) {
        if (outOfBounds(srcOffset, length)) {
            throw trapOutOfBounds(node, srcOffset, length);
        }
        unsafe.copyMemory(null, startAddress + srcOffset, dst, Unsafe.ARRAY_BYTE_BASE_OFFSET + (long) dstOffset * Unsafe.ARRAY_BYTE_INDEX_SCALE, length);
    }

    @Override
    public boolean isUnsafe() {
        return true;
    }

    @SuppressWarnings("deprecation"/* JDK-8277863 */)
    private static long getObjectFieldOffset(Field field) {
        return unsafe.objectFieldOffset(field);
    }

    static {
        try {
            final Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
            Field addressField = Buffer.class.getDeclaredField("address");
            addressOffset = getObjectFieldOffset(addressField);
        } catch (Exception e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }
}
