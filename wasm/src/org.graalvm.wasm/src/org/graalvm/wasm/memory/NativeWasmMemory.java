/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.constants.Sizes.MEMORY_PAGE_SIZE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

import org.graalvm.wasm.MemoryContext;
import org.graalvm.wasm.WasmMath;
import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.api.Vector128Ops;
import org.graalvm.wasm.constants.Sizes;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

import sun.misc.Unsafe;

@ExportLibrary(WasmMemoryLibrary.class)
public final class NativeWasmMemory extends WasmMemory {

    private long startAddress;
    /**
     * The visible size of the Wasm linear memory.
     */
    private long size;
    /**
     * The actual size of the memory buffer allocated by GraalWasm.
     */
    private long bufferSize;

    public static final long MAX_ALLOWED_SIZE = Sizes.MAX_MEMORY_64_INSTANCE_SIZE;

    private static final Unsafe unsafe;
    private static final VarHandle SIZE_FIELD;

    private final MemoryContext memoryContext;
    private final Deallocator deallocator;

    @SuppressWarnings("this-escape")
    @TruffleBoundary
    private NativeWasmMemory(long declaredMinSize, long declaredMaxSize, long initialSize, long maxAllowedSize, boolean indexType64, MemoryContext memoryContext) {
        super(declaredMinSize, declaredMaxSize, initialSize, maxAllowedSize, indexType64, false);
        this.size = initialSize;
        this.memoryContext = memoryContext;
        final long initialBufferSize = byteSize();
        this.bufferSize = initialBufferSize;
        long addr = allocate(initialBufferSize);
        this.startAddress = addr;
        this.deallocator = registerDeallocator(this, memoryContext, addr);
    }

    @TruffleBoundary
    NativeWasmMemory(long declaredMinSize, long declaredMaxSize, boolean indexType64, MemoryContext memoryContext) {
        this(declaredMinSize, declaredMaxSize, declaredMinSize, WasmMath.minUnsigned(declaredMaxSize, MAX_ALLOWED_SIZE), indexType64, memoryContext);
    }

    private static long allocate(long newBufferSize) {
        try {
            final long address = unsafe.allocateMemory(newBufferSize);
            unsafe.setMemory(address, newBufferSize, (byte) 0);
            return address;
        } catch (OutOfMemoryError error) {
            throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
        }
    }

    @TruffleBoundary
    private long reallocate(long newBufferSize) {
        try {
            final long address = unsafe.reallocateMemory(startAddress, newBufferSize);
            unsafe.setMemory(address + bufferSize, newBufferSize - bufferSize, (byte) 0);
            return address;
        } catch (OutOfMemoryError error) {
            throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
        }
    }

    private static Deallocator registerDeallocator(NativeWasmMemory memory, MemoryContext memoryContext, long address) {
        var deallocator = new Deallocator(address);
        memoryContext.registerCleaner(memory, deallocator);
        return deallocator;
    }

    @ExportMessage
    public long size() {
        return (long) SIZE_FIELD.getVolatile(this);
    }

    @ExportMessage
    public long byteSize() {
        return size * MEMORY_PAGE_SIZE;
    }

    @TruffleBoundary
    @ExportMessage
    public synchronized long grow(long extraPageSize) {
        final long previousSize = size();
        if (extraPageSize == 0) {
            invokeGrowCallback();
            return previousSize;
        } else if (Long.compareUnsigned(extraPageSize, maxAllowedSize()) <= 0 && Long.compareUnsigned(previousSize + extraPageSize, maxAllowedSize()) <= 0) {
            // Condition above and limit on maxAllowedSize (see NativeWasmMemory#MAX_ALLOWED_SIZE)
            // ensure computation of targetByteSize does not overflow.
            final long targetPageSize = Math.addExact(previousSize, extraPageSize);
            final long targetByteSize = Math.multiplyExact(targetPageSize, MEMORY_PAGE_SIZE);
            if (Long.compareUnsigned(targetByteSize, bufferSize) > 0) {
                try {
                    long newBufferSize = newBufferSize(targetByteSize);
                    startAddress = updateDeallocatorAddress(reallocate(newBufferSize));
                    bufferSize = newBufferSize;
                } catch (WasmException error) {
                    // Over-allocating failed, so try to allocate at least the amount of memory that
                    // was requested.
                    try {
                        long newBufferSize = targetByteSize;
                        startAddress = updateDeallocatorAddress(reallocate(newBufferSize));
                        bufferSize = newBufferSize;
                    } catch (WasmException errorAgain) {
                        return -1;
                    }
                }
            }
            currentMinSize = targetPageSize;
            SIZE_FIELD.setVolatile(this, targetPageSize);
            invokeGrowCallback();
            return previousSize;
        } else {
            return -1;
        }
    }

    private long updateDeallocatorAddress(long newAddress) {
        deallocator.setAddress(newAddress);
        return newAddress;
    }

    public long newBufferSize(long targetByteSize) {
        // bufferSize <= Sizes.MAX_MEMORY_64_INSTANCE_BYTE_SIZE, so this should not overflow
        long prefBufferSize = Math.addExact(bufferSize, bufferSize >> 1);
        // maxAllowedByteSize <= Sizes.MAX_MEMORY_64_INSTANCE_BYTE_SIZE, so no overflow
        long maxAllowedByteSize = Math.multiplyExact(maxAllowedSize(), MEMORY_PAGE_SIZE);
        return Math.max(targetByteSize, Math.min(prefBufferSize, maxAllowedByteSize));
    }

    @ExportMessage
    @TruffleBoundary
    public synchronized void reset() {
        free();
        size = declaredMinSize;
        bufferSize = byteSize();
        startAddress = updateDeallocatorAddress(allocate(bufferSize));
        currentMinSize = declaredMinSize;
    }

    private void validateAddress(Node node, long address, long length) {
        assert size == 0 || !freed();
        validateAddress(node, address, length, byteSize());
    }

    // Checkstyle: stop
    @ExportMessage
    public int load_i32(Node node, long address) {
        validateAddress(node, address, 4);
        return unsafe.getInt(startAddress + address);
    }

    @ExportMessage
    public long load_i64(Node node, long address) {
        validateAddress(node, address, 8);
        return unsafe.getLong(startAddress + address);
    }

    @ExportMessage
    public float load_f32(Node node, long address) {
        validateAddress(node, address, 4);
        return unsafe.getFloat(startAddress + address);
    }

    @ExportMessage
    public double load_f64(Node node, long address) {
        validateAddress(node, address, 8);
        return unsafe.getDouble(startAddress + address);
    }

    @ExportMessage
    public int load_i32_8s(Node node, long address) {
        validateAddress(node, address, 1);
        return unsafe.getByte(startAddress + address);
    }

    @ExportMessage
    public int load_i32_8u(Node node, long address) {
        validateAddress(node, address, 1);
        return 0x0000_00ff & unsafe.getByte(startAddress + address);
    }

    @ExportMessage
    public int load_i32_16s(Node node, long address) {
        validateAddress(node, address, 2);
        return unsafe.getShort(startAddress + address);
    }

    @ExportMessage
    public int load_i32_16u(Node node, long address) {
        validateAddress(node, address, 2);
        return 0x0000_ffff & unsafe.getShort(startAddress + address);
    }

    @ExportMessage
    public long load_i64_8s(Node node, long address) {
        validateAddress(node, address, 1);
        return unsafe.getByte(startAddress + address);
    }

    @ExportMessage
    public long load_i64_8u(Node node, long address) {
        validateAddress(node, address, 1);
        return 0x0000_0000_0000_00ffL & unsafe.getByte(startAddress + address);
    }

    @ExportMessage
    public long load_i64_16s(Node node, long address) {
        validateAddress(node, address, 2);
        return unsafe.getShort(startAddress + address);
    }

    @ExportMessage
    public long load_i64_16u(Node node, long address) {
        validateAddress(node, address, 2);
        return 0x0000_0000_0000_ffffL & unsafe.getShort(startAddress + address);
    }

    @ExportMessage
    public long load_i64_32s(Node node, long address) {
        validateAddress(node, address, 4);
        return unsafe.getInt(startAddress + address);
    }

    @ExportMessage
    public long load_i64_32u(Node node, long address) {
        validateAddress(node, address, 4);
        return 0x0000_0000_ffff_ffffL & unsafe.getInt(startAddress + address);
    }

    @ExportMessage
    public Object load_i128(Node node, long address) {
        validateAddress(node, address, Vector128.BYTES);
        byte[] bytes = new byte[Vector128.BYTES];
        unsafe.copyMemory(null, startAddress + address, bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET, Vector128.BYTES);
        // Use ByteVector.fromMemorySegment after adopting FFM
        return Vector128Ops.SINGLETON_IMPLEMENTATION.fromArray(bytes);
    }

    @ExportMessage
    public void store_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        unsafe.putInt(startAddress + address, value);
    }

    @ExportMessage
    public void store_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        unsafe.putLong(startAddress + address, value);
    }

    @ExportMessage
    public void store_f32(Node node, long address, float value) {
        validateAddress(node, address, 4);
        unsafe.putFloat(startAddress + address, value);
    }

    @ExportMessage
    public void store_f64(Node node, long address, double value) {
        validateAddress(node, address, 8);
        unsafe.putDouble(startAddress + address, value);
    }

    @ExportMessage
    public void store_i32_8(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        unsafe.putByte(startAddress + address, value);
    }

    @ExportMessage
    public void store_i32_16(Node node, long address, short value) {
        validateAddress(node, address, 2);
        unsafe.putShort(startAddress + address, value);
    }

    @ExportMessage
    public void store_i64_8(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        unsafe.putByte(startAddress + address, value);
    }

    @ExportMessage
    public void store_i64_16(Node node, long address, short value) {
        validateAddress(node, address, 2);
        unsafe.putShort(startAddress + address, value);
    }

    @ExportMessage
    public void store_i64_32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        unsafe.putInt(startAddress + address, value);
    }

    @ExportMessage
    public void store_i128(Node node, long address, Object value) {
        validateAddress(node, address, 16);
        // Use intoMemorySegment after adopting the FFM API
        unsafe.copyMemory(Vector128Ops.SINGLETON_IMPLEMENTATION.toArray(Vector128Ops.cast(value)), Unsafe.ARRAY_BYTE_BASE_OFFSET, null, startAddress + address, 16);
    }

    @ExportMessage
    public int atomic_load_i32(Node node, long address) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        return unsafe.getIntVolatile(null, startAddress + address);
    }

    @ExportMessage
    public long atomic_load_i64(Node node, long address) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        return unsafe.getLongVolatile(null, startAddress + address);
    }

    @ExportMessage
    public int atomic_load_i32_8u(Node node, long address) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        return 0x0000_00ff & unsafe.getByteVolatile(null, startAddress + address);
    }

    @ExportMessage
    public int atomic_load_i32_16u(Node node, long address) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        return 0x0000_ffff & unsafe.getShortVolatile(null, startAddress + address);
    }

    @ExportMessage
    public long atomic_load_i64_8u(Node node, long address) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        return 0x0000_0000_0000_00ffL & unsafe.getByteVolatile(null, startAddress + address);
    }

    @ExportMessage
    public long atomic_load_i64_16u(Node node, long address) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        return 0x0000_0000_0000_ffffL & unsafe.getShortVolatile(null, startAddress + address);
    }

    @ExportMessage
    public long atomic_load_i64_32u(Node node, long address) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        return 0x0000_0000_ffff_ffffL & unsafe.getIntVolatile(null, startAddress + address);
    }

    @ExportMessage
    public void atomic_store_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        unsafe.putIntVolatile(null, startAddress + address, value);
    }

    @ExportMessage
    public void atomic_store_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        unsafe.putLongVolatile(null, startAddress + address, value);
    }

    @ExportMessage
    public void atomic_store_i32_8(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        unsafe.putByteVolatile(null, startAddress + address, value);
    }

    @ExportMessage
    public void atomic_store_i32_16(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        unsafe.putShortVolatile(null, startAddress + address, value);
    }

    @ExportMessage
    public void atomic_store_i64_8(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        unsafe.putByteVolatile(null, startAddress + address, value);
    }

    @ExportMessage
    public void atomic_store_i64_16(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        unsafe.putShortVolatile(null, startAddress + address, value);
    }

    @ExportMessage
    public void atomic_store_i64_32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        unsafe.putIntVolatile(null, startAddress + address, value);
    }

    @ExportMessage
    public int atomic_rmw_add_i32_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v + value)) != v);
        return 0x0000_00ff & v;
    }

    @ExportMessage
    public int atomic_rmw_add_i32_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v + value)) != v);
        return 0x0000_ffff & v;
    }

    @ExportMessage
    public int atomic_rmw_add_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        return unsafe.getAndAddInt(null, startAddress + address, value);
    }

    @ExportMessage
    public long atomic_rmw_add_i64_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v + value)) != v);
        return 0x0000_0000_0000_00ffL & v;
    }

    @ExportMessage
    public long atomic_rmw_add_i64_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v + value)) != v);
        return 0x0000_0000_0000_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_add_i64_32u(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v = unsafe.getAndAddInt(null, startAddress + address, value);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_add_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        return unsafe.getAndAddLong(null, startAddress + address, value);
    }

    @ExportMessage
    public int atomic_rmw_sub_i32_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v - value)) != v);
        return 0x0000_00ff & v;
    }

    @ExportMessage
    public int atomic_rmw_sub_i32_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v - value)) != v);
        return 0x0000_ffff & v;
    }

    @ExportMessage
    public int atomic_rmw_sub_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        return unsafe.getAndAddInt(null, startAddress + address, -value);
    }

    @ExportMessage
    public long atomic_rmw_sub_i64_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v - value)) != v);
        return 0x0000_0000_0000_00ffL & v;
    }

    @ExportMessage
    public long atomic_rmw_sub_i64_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v - value)) != v);
        return 0x0000_0000_0000_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_sub_i64_32u(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v = unsafe.getAndAddInt(null, startAddress + address, -value);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_sub_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        return unsafe.getAndAddLong(null, startAddress + address, -value);
    }

    @ExportMessage
    public int atomic_rmw_and_i32_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v & value)) != v);
        return 0x0000_00ff & v;
    }

    @ExportMessage
    public int atomic_rmw_and_i32_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v & value)) != v);
        return 0x0000_ffff & v;
    }

    @ExportMessage
    public int atomic_rmw_and_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v;
        do {
            v = unsafe.getIntVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeInt(startAddress, address, v, v & value) != v);
        return v;
    }

    @ExportMessage
    public long atomic_rmw_and_i64_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v & value)) != v);
        return 0x0000_0000_0000_00ffL & v;
    }

    @ExportMessage
    public long atomic_rmw_and_i64_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v & value)) != v);
        return 0x0000_0000_0000_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_and_i64_32u(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v;
        do {
            v = unsafe.getIntVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeInt(startAddress, address, v, v & value) != v);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_and_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        long v;
        do {
            v = unsafe.getLongVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeLong(startAddress, address, v, v & value) != v);
        return v;
    }

    @ExportMessage
    public int atomic_rmw_or_i32_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v | value)) != v);
        return 0x0000_00ff & v;
    }

    @ExportMessage
    public int atomic_rmw_or_i32_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v | value)) != v);
        return 0x0000_ffff & v;
    }

    @ExportMessage
    public int atomic_rmw_or_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v;
        do {
            v = unsafe.getIntVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeInt(startAddress, address, v, v | value) != v);
        return v;
    }

    @ExportMessage
    public long atomic_rmw_or_i64_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v | value)) != v);
        return 0x0000_0000_0000_00ffL & v;
    }

    @ExportMessage
    public long atomic_rmw_or_i64_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v | value)) != v);
        return 0x0000_0000_0000_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_or_i64_32u(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v;
        do {
            v = unsafe.getIntVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeInt(startAddress, address, v, v | value) != v);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_or_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        long v;
        do {
            v = unsafe.getLongVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeLong(startAddress, address, v, v | value) != v);
        return v;
    }

    @ExportMessage
    public int atomic_rmw_xor_i32_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v ^ value)) != v);
        return 0x0000_00ff & v;
    }

    @ExportMessage
    public int atomic_rmw_xor_i32_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v ^ value)) != v);
        return 0x0000_ffff & v;
    }

    @ExportMessage
    public int atomic_rmw_xor_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v;
        do {
            v = unsafe.getIntVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeInt(startAddress, address, v, v ^ value) != v);
        return v;
    }

    @ExportMessage
    public long atomic_rmw_xor_i64_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, (byte) (v ^ value)) != v);
        return 0x0000_0000_0000_00ffL & v;
    }

    @ExportMessage
    public long atomic_rmw_xor_i64_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, (short) (v ^ value)) != v);
        return 0x0000_0000_0000_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_xor_i64_32u(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v;
        do {
            v = unsafe.getIntVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeInt(startAddress, address, v, v ^ value) != v);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_xor_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        long v;
        do {
            v = unsafe.getLongVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeLong(startAddress, address, v, v ^ value) != v);
        return v;
    }

    @ExportMessage
    public int atomic_rmw_xchg_i32_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, value) != v);
        return 0x0000_00ff & v;
    }

    @ExportMessage
    public int atomic_rmw_xchg_i32_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, value) != v);
        return 0x0000_ffff & v;
    }

    @ExportMessage
    public int atomic_rmw_xchg_i32(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        return unsafe.getAndSetInt(null, startAddress + address, value);
    }

    @ExportMessage
    public long atomic_rmw_xchg_i64_8u(Node node, long address, byte value) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v;
        do {
            v = unsafe.getByteVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeByte(startAddress, address, v, value) != v);
        return 0x0000_0000_0000_00ffL & v;
    }

    @ExportMessage
    public long atomic_rmw_xchg_i64_16u(Node node, long address, short value) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v;
        do {
            v = unsafe.getShortVolatile(null, startAddress + address);
        } while (UnsafeUtilities.compareAndExchangeShort(startAddress, address, v, value) != v);
        return 0x0000_0000_0000_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_xchg_i64_32u(Node node, long address, int value) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v = unsafe.getAndSetInt(null, startAddress + address, value);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_xchg_i64(Node node, long address, long value) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        return unsafe.getAndSetLong(null, startAddress + address, value);
    }

    @ExportMessage
    public int atomic_rmw_cmpxchg_i32_8u(Node node, long address, byte expected, byte replacement) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v = UnsafeUtilities.compareAndExchangeByte(startAddress, address, expected, replacement);
        return 0x0000_00ff & v;
    }

    @ExportMessage
    public int atomic_rmw_cmpxchg_i32_16u(Node node, long address, short expected, short replacement) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v = UnsafeUtilities.compareAndExchangeShort(startAddress, address, expected, replacement);
        return 0x0000_ffff & v;
    }

    @ExportMessage
    public int atomic_rmw_cmpxchg_i32(Node node, long address, int expected, int replacement) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        return UnsafeUtilities.compareAndExchangeInt(startAddress, address, expected, replacement);
    }

    @ExportMessage
    public long atomic_rmw_cmpxchg_i64_8u(Node node, long address, byte expected, byte replacement) {
        validateAddress(node, address, 1);
        validateAtomicAddress(node, address, 1);
        byte v = UnsafeUtilities.compareAndExchangeByte(startAddress, address, expected, replacement);
        return 0x0000_0000_0000_00ffL & v;
    }

    @ExportMessage
    public long atomic_rmw_cmpxchg_i64_16u(Node node, long address, short expected, short replacement) {
        validateAddress(node, address, 2);
        validateAtomicAddress(node, address, 2);
        short v = UnsafeUtilities.compareAndExchangeShort(startAddress, address, expected, replacement);
        return 0x0000_0000_0000_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_cmpxchg_i64_32u(Node node, long address, int expected, int replacement) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        int v = UnsafeUtilities.compareAndExchangeInt(startAddress, address, expected, replacement);
        return 0x0000_0000_ffff_ffffL & v;
    }

    @ExportMessage
    public long atomic_rmw_cmpxchg_i64(Node node, long address, long expected, long replacement) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        return UnsafeUtilities.compareAndExchangeLong(startAddress, address, expected, replacement);
    }

    @ExportMessage
    @TruffleBoundary
    public int atomic_notify(Node node, long address, int count) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        if (!this.isShared()) {
            return 0;
        }
        return invokeNotifyCallback(node, address, count);
    }

    @ExportMessage
    @TruffleBoundary
    public int atomic_wait32(Node node, long address, int expected, long timeout) {
        validateAddress(node, address, 4);
        validateAtomicAddress(node, address, 4);
        if (!this.isShared()) {
            throw trapUnsharedMemory(node);
        }
        return invokeWaitCallback(node, address, expected, timeout, false);
    }

    @ExportMessage
    @TruffleBoundary
    public int atomic_wait64(Node node, long address, long expected, long timeout) {
        validateAddress(node, address, 8);
        validateAtomicAddress(node, address, 8);
        if (!this.isShared()) {
            throw trapUnsharedMemory(node);
        }
        return invokeWaitCallback(node, address, expected, timeout, true);
    }
    // Checkstyle: resume

    @ExportMessage
    public WasmMemory duplicate() {
        final NativeWasmMemory other = new NativeWasmMemory(declaredMinSize, declaredMaxSize, size, maxAllowedSize, indexType64, memoryContext);
        unsafe.copyMemory(this.startAddress, other.startAddress, this.byteSize());
        return other;
    }

    @ExportMessage
    public void initialize(Node node, byte[] source, int sourceOffset, long destinationOffset, int length) {
        validateLength(node, length);
        validateAddress(node, destinationOffset, length);
        if (sourceOffset < 0 || sourceOffset > source.length - length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw trapOutOfBoundsBuffer(node, sourceOffset, length, source.length);
        }
        unsafe.copyMemory(source, Unsafe.ARRAY_BYTE_BASE_OFFSET + sourceOffset * Unsafe.ARRAY_BYTE_INDEX_SCALE, null, startAddress + destinationOffset, length);
    }

    @ExportMessage
    public void fill(Node node, long offset, long length, byte value) {
        validateLength(node, length);
        validateAddress(node, offset, length);
        unsafe.setMemory(startAddress + offset, length, value);
    }

    @ExportMessage
    public void copyFrom(Node node, WasmMemory source, long sourceOffset, long destinationOffset, long length) {
        assert source instanceof NativeWasmMemory;
        final NativeWasmMemory s = (NativeWasmMemory) source;
        validateLength(node, length);
        s.validateAddress(node, sourceOffset, length);
        validateAddress(node, destinationOffset, length);
        unsafe.copyMemory(s.startAddress + sourceOffset, this.startAddress + destinationOffset, length);
    }

    @ExportMessage
    public boolean freed() {
        return startAddress == 0 || deallocator.getAddress() == 0;
    }

    /**
     * Called by {@link Deallocator#run()}.
     */
    private static void free(long addr) {
        unsafe.freeMemory(addr);
    }

    @TruffleBoundary
    private synchronized void free() {
        deallocator.run();
        bufferSize = 0;
        startAddress = 0;
        size = 0;
    }

    @ExportMessage
    public void close() {
        if (!freed()) {
            free();
        }
    }

    @ExportMessage
    @TruffleBoundary
    public int copyFromStream(Node node, InputStream stream, int offset, int length) throws IOException {
        validateLength(node, length);
        validateAddress(node, offset, length);
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

    @ExportMessage
    @TruffleBoundary
    public void copyToStream(Node node, OutputStream stream, int offset, int length) throws IOException {
        validateLength(node, length);
        validateAddress(node, offset, length);
        for (int i = 0; i < length; i++) {
            byte b = unsafe.getByte(startAddress + offset + i);
            stream.write(b & 0x0000_00ff);
        }
    }

    @ExportMessage
    public void copyToBuffer(Node node, byte[] dst, long srcOffset, int dstOffset, int length) {
        validateLength(node, length);
        validateAddress(node, srcOffset, length);
        if (dstOffset < 0 || dstOffset > dst.length - length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw trapOutOfBoundsBuffer(node, dstOffset, length, dst.length);
        }
        unsafe.copyMemory(null, startAddress + srcOffset, dst, Unsafe.ARRAY_BYTE_BASE_OFFSET + (long) dstOffset * Unsafe.ARRAY_BYTE_INDEX_SCALE, length);
    }

    private static final class Deallocator implements Runnable {
        private volatile long address;
        private static final VarHandle ADDRESS;

        static {
            try {
                ADDRESS = MethodHandles.lookup().findVarHandle(Deallocator.class, "address", long.class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        Deallocator(long address) {
            this.address = address;
        }

        @Override
        public void run() {
            // Atomic update to prevent double free.
            long addr = (long) ADDRESS.getAndSet(this, 0);
            if (addr != 0) {
                free(addr);
            }
        }

        void setAddress(long address) {
            this.address = address;
        }

        long getAddress() {
            return address;
        }
    }

    static {
        try {
            var lookup = MethodHandles.lookup();
            SIZE_FIELD = lookup.findVarHandle(NativeWasmMemory.class, "size", long.class);

            final Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }
}
