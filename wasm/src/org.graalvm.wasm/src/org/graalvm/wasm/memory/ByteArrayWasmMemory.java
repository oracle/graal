/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static java.lang.Math.addExact;
import static java.lang.Math.multiplyExact;
import static org.graalvm.wasm.constants.Sizes.MEMORY_PAGE_SIZE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.api.Vector128Ops;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;

@ExportLibrary(WasmMemoryLibrary.class)
final class ByteArrayWasmMemory extends WasmMemory {
    private byte[] dynamicBuffer;

    public static final long MAX_ALLOWED_SIZE = Integer.MAX_VALUE / MEMORY_PAGE_SIZE;

    @TruffleBoundary
    private ByteArrayWasmMemory(long declaredMinSize, long declaredMaxSize, long initialSize, long maxAllowedSize, boolean indexType64) {
        super(declaredMinSize, declaredMaxSize, initialSize, maxAllowedSize, indexType64, false);
        this.dynamicBuffer = allocateBuffer(initialSize * MEMORY_PAGE_SIZE);
    }

    @TruffleBoundary
    ByteArrayWasmMemory(long declaredMinSize, long declaredMaxSize, boolean indexType64) {
        this(declaredMinSize, declaredMaxSize, declaredMinSize, Math.min(declaredMaxSize, MAX_ALLOWED_SIZE), indexType64);
    }

    private byte[] buffer() {
        return dynamicBuffer;
    }

    @ExportMessage
    public long size() {
        return byteSize() / MEMORY_PAGE_SIZE;
    }

    @ExportMessage
    public long byteSize() {
        return buffer().length;
    }

    @ExportMessage
    @TruffleBoundary
    public synchronized long grow(long extraPageSize) {
        long previousSize = size();
        if (extraPageSize == 0) {
            invokeGrowCallback();
            return previousSize;
        } else if (compareUnsigned(extraPageSize, maxAllowedSize()) <= 0 && compareUnsigned(previousSize + extraPageSize, maxAllowedSize()) <= 0) {
            /*
             * Condition above and limit on maxAllowedSize (see
             * ByteArrayWasmMemory#MAX_ALLOWED_SIZE) ensure computation of targetByteSize does not
             * overflow.
             */
            final long targetPageSize = addExact(previousSize, extraPageSize);
            final long targetByteSize = multiplyExact(targetPageSize, MEMORY_PAGE_SIZE);
            final byte[] currentBuffer = buffer();
            final byte[] newBuffer;
            try {
                newBuffer = allocateBuffer(targetByteSize);
            } catch (WasmException oome) {
                return -1;
            }
            System.arraycopy(currentBuffer, 0, newBuffer, 0, currentBuffer.length);
            dynamicBuffer = newBuffer;
            currentMinSize = targetPageSize;
            invokeGrowCallback();
            return previousSize;
        } else {
            return -1;
        }
    }

    @ExportMessage
    @TruffleBoundary
    public void reset() {
        dynamicBuffer = allocateBuffer(declaredMinSize * MEMORY_PAGE_SIZE);
        currentMinSize = declaredMinSize;
    }

    private void validateAddress(Node node, long address, long length) {
        validateAddress(node, address, length, byteSize());
    }

    private WasmException trapOutOfBounds(Node node, long address, long length) {
        return trapOutOfBounds(node, address, length, byteSize());
    }

    // Checkstyle: stop
    @ExportMessage
    public int load_i32(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getInt(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long load_i64(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getLong(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @ExportMessage
    public float load_f32(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getFloat(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public double load_f64(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getDouble(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @ExportMessage
    public int load_i32_8s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getByte(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public int load_i32_8u(Node node, long address) {
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getByte(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public int load_i32_16s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getShort(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public int load_i32_16u(Node node, long address) {
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getShort(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public long load_i64_8s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getByte(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public long load_i64_8u(Node node, long address) {
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getByte(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public long load_i64_16s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getShort(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public long load_i64_16u(Node node, long address) {
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getShort(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public long load_i64_32s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getInt(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long load_i64_32u(Node node, long address) {
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getInt(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public Object load_i128(Node node, long address) {
        if (ByteArraySupport.littleEndian().inBounds(buffer(), address, Vector128.BYTES)) {
            return Vector128Ops.SINGLETON_IMPLEMENTATION.fromArray(buffer(), (int) address);
        } else {
            throw trapOutOfBounds(node, address, 16);
        }
    }

    @ExportMessage
    public void store_i32(Node node, long address, int value) {
        try {
            ByteArraySupport.littleEndian().putInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public void store_i64(Node node, long address, long value) {
        try {
            ByteArraySupport.littleEndian().putLong(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }

    }

    @ExportMessage
    public void store_f32(Node node, long address, float value) {
        try {
            ByteArraySupport.littleEndian().putFloat(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public void store_f64(Node node, long address, double value) {
        try {
            ByteArraySupport.littleEndian().putDouble(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @ExportMessage
    public void store_i32_8(Node node, long address, byte value) {
        try {
            ByteArraySupport.littleEndian().putByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public void store_i32_16(Node node, long address, short value) {
        try {
            ByteArraySupport.littleEndian().putShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public void store_i64_8(Node node, long address, byte value) {
        try {
            ByteArraySupport.littleEndian().putByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public void store_i64_16(Node node, long address, short value) {
        try {
            ByteArraySupport.littleEndian().putShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public void store_i64_32(Node node, long address, int value) {
        try {
            ByteArraySupport.littleEndian().putInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public void store_i128(Node node, long address, Object value) {
        if (ByteArraySupport.littleEndian().inBounds(buffer(), address, 16)) {
            Vector128Ops.SINGLETON_IMPLEMENTATION.intoArray(Vector128Ops.cast(value), buffer(), (int) address);
        } else {
            throw trapOutOfBounds(node, address, 16);
        }
    }

    @ExportMessage
    public int atomic_load_i32(Node node, long address) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getIntVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_load_i64(Node node, long address) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getLongVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @ExportMessage
    public int atomic_load_i32_8u(Node node, long address) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getByteVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public int atomic_load_i32_16u(Node node, long address) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getShortVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public long atomic_load_i64_8u(Node node, long address) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getByteVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public long atomic_load_i64_16u(Node node, long address) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getShortVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public long atomic_load_i64_32u(Node node, long address) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getIntVolatile(buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public void atomic_store_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            ByteArraySupport.littleEndian().putIntVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public void atomic_store_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            ByteArraySupport.littleEndian().putLongVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @ExportMessage
    public void atomic_store_i32_8(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            ByteArraySupport.littleEndian().putByteVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public void atomic_store_i32_16(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            ByteArraySupport.littleEndian().putShortVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public void atomic_store_i64_8(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            ByteArraySupport.littleEndian().putByteVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public void atomic_store_i64_16(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            ByteArraySupport.littleEndian().putShortVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public void atomic_store_i64_32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            ByteArraySupport.littleEndian().putIntVolatile(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public int atomic_rmw_add_i32_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getAndAddByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public int atomic_rmw_add_i32_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getAndAddShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public int atomic_rmw_add_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getAndAddInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_add_i64_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getAndAddByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public long atomic_rmw_add_i64_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getAndAddShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public long atomic_rmw_add_i64_32u(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getAndAddInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_add_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getAndAddLong(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @ExportMessage
    public int atomic_rmw_sub_i32_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getAndAddByte(buffer(), address, (byte) -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public int atomic_rmw_sub_i32_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getAndAddShort(buffer(), address, (short) -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public int atomic_rmw_sub_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getAndAddInt(buffer(), address, -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_sub_i64_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getAndAddByte(buffer(), address, (byte) -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public long atomic_rmw_sub_i64_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getAndAddShort(buffer(), address, (short) -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public long atomic_rmw_sub_i64_32u(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getAndAddInt(buffer(), address, -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_sub_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getAndAddLong(buffer(), address, -value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @ExportMessage
    public int atomic_rmw_and_i32_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getAndBitwiseAndByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public int atomic_rmw_and_i32_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getAndBitwiseAndShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public int atomic_rmw_and_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getAndBitwiseAndInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_and_i64_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getAndBitwiseAndByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public long atomic_rmw_and_i64_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getAndBitwiseAndShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public long atomic_rmw_and_i64_32u(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getAndBitwiseAndInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_and_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getAndBitwiseAndLong(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @ExportMessage
    public int atomic_rmw_or_i32_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getAndBitwiseOrByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public int atomic_rmw_or_i32_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getAndBitwiseOrShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public int atomic_rmw_or_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getAndBitwiseOrInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_or_i64_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getAndBitwiseOrByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public long atomic_rmw_or_i64_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getAndBitwiseOrShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public long atomic_rmw_or_i64_32u(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getAndBitwiseOrInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_or_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getAndBitwiseOrLong(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @ExportMessage
    public int atomic_rmw_xor_i32_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getAndBitwiseXorByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public int atomic_rmw_xor_i32_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getAndBitwiseXorShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public int atomic_rmw_xor_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getAndBitwiseXorInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_xor_i64_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getAndBitwiseXorByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public long atomic_rmw_xor_i64_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getAndBitwiseXorShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public long atomic_rmw_xor_i64_32u(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getAndBitwiseXorInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_xor_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getAndBitwiseXorLong(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @ExportMessage
    public int atomic_rmw_xchg_i32_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getAndSetByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public int atomic_rmw_xchg_i32_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getAndSetShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public int atomic_rmw_xchg_i32(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getAndSetInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_xchg_i64_8u(Node node, long address, byte value) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getAndSetByte(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public long atomic_rmw_xchg_i64_16u(Node node, long address, short value) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getAndSetShort(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public long atomic_rmw_xchg_i64_32u(Node node, long address, int value) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getAndSetInt(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_xchg_i64(Node node, long address, long value) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getAndSetLong(buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @ExportMessage
    public int atomic_rmw_cmpxchg_i32_8u(Node node, long address, byte expected, byte replacement) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().compareAndExchangeByte(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public int atomic_rmw_cmpxchg_i32_16u(Node node, long address, short expected, short replacement) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().compareAndExchangeShort(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public int atomic_rmw_cmpxchg_i32(Node node, long address, int expected, int replacement) {
        validateAtomicAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().compareAndExchangeInt(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_cmpxchg_i64_8u(Node node, long address, byte expected, byte replacement) {
        validateAtomicAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().compareAndExchangeByte(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @ExportMessage
    public long atomic_rmw_cmpxchg_i64_16u(Node node, long address, short expected, short replacement) {
        validateAtomicAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().compareAndExchangeShort(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @ExportMessage
    public long atomic_rmw_cmpxchg_i64_32u(Node node, long address, int expected, int replacement) {
        validateAtomicAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().compareAndExchangeInt(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @ExportMessage
    public long atomic_rmw_cmpxchg_i64(Node node, long address, long expected, long replacement) {
        validateAtomicAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().compareAndExchangeLong(buffer(), address, expected, replacement);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
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
        validateAddress(node, address, 4, 8);
        validateAtomicAddress(node, address, 8);
        if (!this.isShared()) {
            throw trapUnsharedMemory(node);
        }
        return invokeWaitCallback(node, address, expected, timeout, true);
    }
    // Checkstyle: resume

    @ExportMessage
    public void initialize(Node node, byte[] source, int sourceOffset, long destinationOffset, int length) {
        validateLength(node, length);
        validateAddress(node, destinationOffset, length);
        if (sourceOffset < 0 || sourceOffset > source.length - length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw trapOutOfBoundsBuffer(node, sourceOffset, length, source.length);
        }
        System.arraycopy(source, sourceOffset, buffer(), (int) destinationOffset, length);
    }

    @ExportMessage
    @TruffleBoundary
    public void fill(Node node, long offset, long length, byte value) {
        validateLength(node, length);
        validateAddress(node, offset, length);
        Arrays.fill(buffer(), (int) offset, (int) (offset + length), value);
    }

    @ExportMessage
    public void copyFrom(Node node, WasmMemory source, long sourceOffset, long destinationOffset, long length) {
        assert source instanceof ByteArrayWasmMemory;
        ByteArrayWasmMemory s = (ByteArrayWasmMemory) source;
        validateLength(node, length);
        s.validateAddress(node, sourceOffset, length);
        validateAddress(node, destinationOffset, length);
        System.arraycopy(s.buffer(), (int) sourceOffset, buffer(), (int) destinationOffset, (int) length);
    }

    @ExportMessage
    public WasmMemory duplicate() {
        final ByteArrayWasmMemory other = new ByteArrayWasmMemory(declaredMinSize, declaredMaxSize, size(), maxAllowedSize, indexType64);
        System.arraycopy(buffer(), 0, other.buffer(), 0, (int) byteSize());
        return other;
    }

    @ExportMessage
    public void close() {
        dynamicBuffer = null;
    }

    @ExportMessage
    @TruffleBoundary
    public int copyFromStream(Node node, InputStream stream, int offset, int length) throws IOException {
        validateLength(node, length);
        validateAddress(node, offset, length);
        return stream.read(buffer(), offset, length);
    }

    @ExportMessage
    @TruffleBoundary
    public void copyToStream(Node node, OutputStream stream, int offset, int length) throws IOException {
        validateLength(node, length);
        validateAddress(node, offset, length);
        stream.write(buffer(), offset, length);
    }

    @ExportMessage
    public void copyToBuffer(Node node, byte[] dst, long srcOffset, int dstOffset, int length) {
        validateLength(node, length);
        validateAddress(node, srcOffset, length);
        if (dstOffset < 0 || dstOffset > dst.length - length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw trapOutOfBoundsBuffer(node, dstOffset, length, dst.length);
        }
        System.arraycopy(buffer(), (int) srcOffset, dst, dstOffset, length);
    }

    @TruffleBoundary
    private static byte[] allocateBuffer(long byteSize) {
        try {
            return new byte[Math.toIntExact(byteSize)];
        } catch (OutOfMemoryError error) {
            throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
        }
    }

    @ExportMessage
    public static boolean freed(@SuppressWarnings("unused") ByteArrayWasmMemory memory) {
        return true;
    }
}
