/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;

final class ByteArrayWasmMemory extends WasmMemory {
    private byte[] dynamicBuffer;

    private ByteArrayWasmMemory(long declaredMinSize, long declaredMaxSize, long initialSize, long maxAllowedSize, boolean indexType64, boolean shared) {
        super(declaredMinSize, declaredMaxSize, initialSize, maxAllowedSize, indexType64, shared);
        this.dynamicBuffer = allocateStatic(initialSize * MEMORY_PAGE_SIZE);
    }

    ByteArrayWasmMemory(long declaredMinSize, long declaredMaxSize, long maxAllowedSize, boolean indexType64, boolean shared) {
        this(declaredMinSize, declaredMaxSize, declaredMinSize, maxAllowedSize, indexType64, shared);
    }

    private byte[] buffer() {
        return dynamicBuffer;
    }

    @Override
    public long size() {
        return byteSize() / MEMORY_PAGE_SIZE;
    }

    @Override
    public long byteSize() {
        return buffer().length;
    }

    @Override
    @TruffleBoundary
    public synchronized long grow(long extraPageSize) {
        long previousSize = size();
        if (extraPageSize == 0) {
            invokeGrowCallback();
            return previousSize;
        } else if (compareUnsigned(extraPageSize, maxAllowedSize) <= 0 && compareUnsigned(size() + extraPageSize, maxAllowedSize) <= 0) {
            // Condition above and limit on maxPageSize (see ModuleLimits#MAX_MEMORY_SIZE)
            // ensure computation of targetByteSize does not overflow.
            final long targetByteSize = multiplyExact(addExact(size(), extraPageSize), MEMORY_PAGE_SIZE);
            final byte[] currentBuffer = buffer();
            allocate(targetByteSize);
            System.arraycopy(currentBuffer, 0, buffer(), 0, currentBuffer.length);
            currentMinSize = size() + extraPageSize;
            invokeGrowCallback();
            return previousSize;
        } else {
            return -1;
        }
    }

    @Override
    @TruffleBoundary
    public void reset() {
        allocate(declaredMinSize * MEMORY_PAGE_SIZE);
        currentMinSize = declaredMinSize;
    }

    @Override
    public int load_i32(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getInt(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long load_i64(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getLong(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public float load_f32(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getFloat(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public double load_f64(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getDouble(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public int load_i32_8s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getByte(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int load_i32_8u(Node node, long address) {
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getByte(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int load_i32_16s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getShort(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public int load_i32_16u(Node node, long address) {
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getShort(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_8s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getByte(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long load_i64_8u(Node node, long address) {
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getByte(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long load_i64_16s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getShort(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_16u(Node node, long address) {
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getShort(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_32s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getInt(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long load_i64_32u(Node node, long address) {
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getInt(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public Vector128 load_i128(Node node, long address) {
        if (ByteArraySupport.littleEndian().inBounds(buffer(), address, Vector128.BYTES)) {
            return new Vector128(Arrays.copyOfRange(buffer(), (int) address, (int) address + Vector128.BYTES));
        } else {
            throw trapOutOfBounds(node, address, 16);
        }
    }

    @Override
    public void store_i32(Node node, long address, int value) {
        try {
            ByteArraySupport.littleEndian().putInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_i64(Node node, long address, long value) {
        try {
            ByteArraySupport.littleEndian().putLong(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }

    }

    @Override
    public void store_f32(Node node, long address, float value) {
        try {
            ByteArraySupport.littleEndian().putFloat(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_f64(Node node, long address, double value) {
        try {
            ByteArraySupport.littleEndian().putDouble(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public void store_i32_8(Node node, long address, byte value) {
        try {
            ByteArraySupport.littleEndian().putByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void store_i32_16(Node node, long address, short value) {
        try {
            ByteArraySupport.littleEndian().putShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void store_i64_8(Node node, long address, byte value) {
        try {
            ByteArraySupport.littleEndian().putByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void store_i64_16(Node node, long address, short value) {
        try {
            ByteArraySupport.littleEndian().putShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void store_i64_32(Node node, long address, int value) {
        try {
            ByteArraySupport.littleEndian().putInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_i128(Node node, long address, Vector128 value) {
        if (ByteArraySupport.littleEndian().inBounds(buffer(), address, 16)) {
            System.arraycopy(value.getBytes(), 0, buffer(), (int) address, 16);
        } else {
            throw trapOutOfBounds(node, address, 16);
        }
    }

    private static void validateAtomicAddress(Node node, long address, int length) {
        if ((address & (length - 1)) != 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw trapUnalignedAtomic(node, address, length);
        }
    }

    @Override
    public int atomic_load_i32(Node node, long address) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getIntVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_load_i64(Node node, long address) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getLongVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public int atomic_load_i32_8u(Node node, long address) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getByteVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int atomic_load_i32_16u(Node node, long address) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getShortVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long atomic_load_i64_8u(Node node, long address) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getByteVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long atomic_load_i64_16u(Node node, long address) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getShortVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long atomic_load_i64_32u(Node node, long address) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getIntVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void atomic_store_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            ByteArraySupport.littleEndian().putIntVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void atomic_store_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            ByteArraySupport.littleEndian().putLongVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public void atomic_store_i32_8(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            ByteArraySupport.littleEndian().putByteVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void atomic_store_i32_16(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            ByteArraySupport.littleEndian().putShortVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void atomic_store_i64_8(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            ByteArraySupport.littleEndian().putByteVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void atomic_store_i64_16(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            ByteArraySupport.littleEndian().putShortVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void atomic_store_i64_32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            ByteArraySupport.littleEndian().putIntVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public int atomic_rmw_add_i32_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getAndAddByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int atomic_rmw_add_i32_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getAndAddShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public int atomic_rmw_add_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getAndAddInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_add_i64_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getAndAddByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long atomic_rmw_add_i64_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getAndAddShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long atomic_rmw_add_i64_32u(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getAndAddInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_add_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getAndAddLong(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public int atomic_rmw_sub_i32_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getAndAddByte(buffer(), address, (byte) -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int atomic_rmw_sub_i32_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getAndAddShort(buffer(), address, (short) -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public int atomic_rmw_sub_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getAndAddInt(buffer(), address, -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_sub_i64_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getAndAddByte(buffer(), address, (byte) -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long atomic_rmw_sub_i64_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getAndAddShort(buffer(), address, (short) -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long atomic_rmw_sub_i64_32u(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getAndAddInt(buffer(), address, -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_sub_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getAndAddLong(buffer(), address, -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public int atomic_rmw_and_i32_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getAndBitwiseAndByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int atomic_rmw_and_i32_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getAndBitwiseAndShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public int atomic_rmw_and_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getAndBitwiseAndInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_and_i64_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getAndBitwiseAndByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long atomic_rmw_and_i64_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getAndBitwiseAndShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long atomic_rmw_and_i64_32u(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getAndBitwiseAndInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_and_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getAndBitwiseAndLong(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public int atomic_rmw_or_i32_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getAndBitwiseOrByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int atomic_rmw_or_i32_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getAndBitwiseOrShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public int atomic_rmw_or_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getAndBitwiseOrInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_or_i64_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getAndBitwiseOrByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long atomic_rmw_or_i64_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getAndBitwiseOrShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long atomic_rmw_or_i64_32u(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getAndBitwiseOrInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_or_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getAndBitwiseOrLong(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public int atomic_rmw_xor_i32_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getAndBitwiseXorByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int atomic_rmw_xor_i32_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getAndBitwiseXorShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public int atomic_rmw_xor_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getAndBitwiseXorInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_xor_i64_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getAndBitwiseXorByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long atomic_rmw_xor_i64_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getAndBitwiseXorShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long atomic_rmw_xor_i64_32u(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getAndBitwiseXorInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_xor_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getAndBitwiseXorLong(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public int atomic_rmw_xchg_i32_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getAndSetByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int atomic_rmw_xchg_i32_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getAndSetShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public int atomic_rmw_xchg_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getAndSetInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_xchg_i64_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getAndSetByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long atomic_rmw_xchg_i64_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getAndSetShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long atomic_rmw_xchg_i64_32u(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getAndSetInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_xchg_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getAndSetLong(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public int atomic_rmw_cmpxchg_i32_8u(Node node, long address, byte expected, byte replacement) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().compareAndExchangeByte(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int atomic_rmw_cmpxchg_i32_16u(Node node, long address, short expected, short replacement) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().compareAndExchangeShort(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public int atomic_rmw_cmpxchg_i32(Node node, long address, int expected, int replacement) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().compareAndExchangeInt(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_cmpxchg_i64_8u(Node node, long address, byte expected, byte replacement) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().compareAndExchangeByte(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long atomic_rmw_cmpxchg_i64_16u(Node node, long address, short expected, short replacement) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().compareAndExchangeShort(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long atomic_rmw_cmpxchg_i64_32u(Node node, long address, int expected, int replacement) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().compareAndExchangeInt(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long atomic_rmw_cmpxchg_i64(Node node, long address, long expected, long replacement) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().compareAndExchangeLong(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    @TruffleBoundary
    public int atomic_notify(Node node, long address, int count) {
        validateAtomicAddress(node, address, 4);
        if (outOfBounds(address, 4)) {
            throw trapOutOfBounds(node, address, 4);
        }
        if (!this.isShared()) {
            return 0;
        }
        return invokeNotifyCallback(node, address, count);
    }

    @Override
    @TruffleBoundary
    public int atomic_wait32(Node node, long address, int expected, long timeout) {
        validateAtomicAddress(node, address, 4);
        if (outOfBounds(address, 4)) {
            throw trapOutOfBounds(node, address, 4);
        }
        if (!this.isShared()) {
            throw trapUnsharedMemory(node);
        }
        return invokeWaitCallback(node, address, expected, timeout, false);
    }

    @Override
    @TruffleBoundary
    public int atomic_wait64(Node node, long address, long expected, long timeout) {
        validateAtomicAddress(node, address, 8);
        if (outOfBounds(address, 8)) {
            throw trapOutOfBounds(node, address, 8);
        }
        if (!this.isShared()) {
            throw trapUnsharedMemory(node);
        }
        return invokeWaitCallback(node, address, expected, timeout, true);
    }

    @Override
    public void initialize(byte[] source, int sourceOffset, long destinationOffset, int length) {
        assert destinationOffset + length <= byteSize();
        System.arraycopy(source, sourceOffset, buffer(), (int) destinationOffset, length);
    }

    @Override
    @TruffleBoundary
    public void fill(long offset, long length, byte value) {
        assert offset + length <= byteSize();
        Arrays.fill(buffer(), (int) offset, (int) (offset + length), value);
    }

    @Override
    public void copyFrom(WasmMemory source, long sourceOffset, long destinationOffset, long length) {
        assert source instanceof ByteArrayWasmMemory;
        assert destinationOffset < byteSize();
        ByteArrayWasmMemory s = (ByteArrayWasmMemory) source;
        System.arraycopy(s.buffer(), (int) sourceOffset, buffer(), (int) destinationOffset, (int) length);
    }

    @Override
    public WasmMemory duplicate() {
        final ByteArrayWasmMemory other = new ByteArrayWasmMemory(declaredMinSize, declaredMaxSize, size(), maxAllowedSize, indexType64, shared);
        System.arraycopy(buffer(), 0, other.buffer(), 0, (int) byteSize());
        return other;
    }

    @Override
    public void close() {
        dynamicBuffer = null;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return null;
    }

    @Override
    @TruffleBoundary
    public int copyFromStream(Node node, InputStream stream, int offset, int length) throws IOException {
        if (outOfBounds(offset, length)) {
            throw trapOutOfBounds(node, offset, length);
        }
        return stream.read(buffer(), offset, length);
    }

    @Override
    @TruffleBoundary
    public void copyToStream(Node node, OutputStream stream, int offset, int length) throws IOException {
        if (outOfBounds(offset, length)) {
            throw trapOutOfBounds(node, offset, length);
        }
        stream.write(buffer(), offset, length);
    }

    @Override
    public void copyToBuffer(Node node, byte[] dst, long srcOffset, int dstOffset, int length) {
        if (outOfBounds(srcOffset, length)) {
            throw trapOutOfBounds(node, srcOffset, length);
        }
        System.arraycopy(buffer(), (int) srcOffset, dst, dstOffset, length);
    }

    @TruffleBoundary
    private void allocate(long byteSize) {
        dynamicBuffer = null;
        dynamicBuffer = allocateStatic(byteSize);
    }

    @TruffleBoundary
    private static byte[] allocateStatic(long byteSize) {
        assert byteSize <= Integer.MAX_VALUE : byteSize;
        final int effectiveByteSize = (int) byteSize;
        try {
            return new byte[effectiveByteSize];
        } catch (OutOfMemoryError error) {
            throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
        }
    }
}
