/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_INSTANCE_SIZE;
import static org.graalvm.wasm.constants.Sizes.MEMORY_PAGE_SIZE;

import java.nio.ByteBuffer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;

final class ByteArrayWasmMemory extends WasmMemory {
    private final WasmByteArrayBuffer byteArrayBuffer;

    private ByteArrayWasmMemory(long declaredMinSize, long declaredMaxSize, long initialSize, long maxAllowedSize, boolean indexType64) {
        super(declaredMinSize, declaredMaxSize, initialSize, maxAllowedSize, indexType64);
        if (maxAllowedSize > MAX_MEMORY_INSTANCE_SIZE) {
            this.byteArrayBuffer = new WasmMultiByteArrayBuffer();
        } else {
            this.byteArrayBuffer = new WasmSingleByteArrayBuffer();
        }
        this.byteArrayBuffer.allocate(initialSize * MEMORY_PAGE_SIZE);
    }

    ByteArrayWasmMemory(long declaredMinSize, long declaredMaxSize, long maxAllowedSize, boolean indexType64) {
        this(declaredMinSize, declaredMaxSize, declaredMinSize, maxAllowedSize, indexType64);
    }

    @Override
    public long size() {
        return byteArrayBuffer.size();
    }

    @Override
    public long byteSize() {
        return byteArrayBuffer.byteSize();
    }

    @Override
    @TruffleBoundary
    public synchronized boolean grow(long extraPageSize) {
        if (extraPageSize == 0) {
            invokeGrowCallback();
            return true;
        } else if (compareUnsigned(extraPageSize, maxAllowedSize) <= 0 && compareUnsigned(size() + extraPageSize, maxAllowedSize) <= 0) {
            // Condition above and limit on maxPageSize (see ModuleLimits#MAX_MEMORY_SIZE)
            // ensure computation of targetByteSize does not overflow.
            final long targetByteSize = multiplyExact(addExact(size(), extraPageSize), MEMORY_PAGE_SIZE);
            byteArrayBuffer.grow(targetByteSize);
            currentMinSize = size() + extraPageSize;
            invokeGrowCallback();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void reset() {
        byteArrayBuffer.reset(declaredMinSize * MEMORY_PAGE_SIZE);
        currentMinSize = declaredMinSize;
    }

    @Override
    public int load_i32(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return ByteArraySupport.littleEndian().getInt(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long load_i64(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return ByteArraySupport.littleEndian().getLong(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public float load_f32(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return ByteArraySupport.littleEndian().getFloat(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public double load_f64(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return ByteArraySupport.littleEndian().getDouble(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public int load_i32_8s(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return ByteArraySupport.littleEndian().getByte(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int load_i32_8u(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getByte(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int load_i32_16s(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return ByteArraySupport.littleEndian().getShort(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public int load_i32_16u(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getShort(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_8s(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return ByteArraySupport.littleEndian().getByte(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long load_i64_8u(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getByte(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long load_i64_16s(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return ByteArraySupport.littleEndian().getShort(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_16u(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getShort(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_32s(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return ByteArraySupport.littleEndian().getInt(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long load_i64_32u(Node node, long address) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getInt(buffer, offset);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_i32(Node node, long address, int value) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            ByteArraySupport.littleEndian().putInt(buffer, offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_i64(Node node, long address, long value) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            ByteArraySupport.littleEndian().putLong(buffer, offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }

    }

    @Override
    public void store_f32(Node node, long address, float value) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            ByteArraySupport.littleEndian().putFloat(buffer, offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_f64(Node node, long address, double value) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            ByteArraySupport.littleEndian().putDouble(buffer, offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public void store_i32_8(Node node, long address, byte value) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            ByteArraySupport.littleEndian().putByte(buffer, offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void store_i32_16(Node node, long address, short value) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            ByteArraySupport.littleEndian().putShort(buffer, offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void store_i64_8(Node node, long address, byte value) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            ByteArraySupport.littleEndian().putByte(buffer, offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void store_i64_16(Node node, long address, short value) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            ByteArraySupport.littleEndian().putShort(buffer, offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void store_i64_32(Node node, long address, int value) {
        final byte[] buffer = byteArrayBuffer.segment(address);
        final long offset = byteArrayBuffer.segmentOffsetAsLong(address);
        try {
            ByteArraySupport.littleEndian().putInt(buffer, offset, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void initialize(byte[] dataInstance, int sourceOffset, long destinationOffset, int length) {
        assert destinationOffset + length <= byteSize();
        System.arraycopy(dataInstance, sourceOffset, byteArrayBuffer.segment(destinationOffset), byteArrayBuffer.segmentOffsetAsInt(destinationOffset), length);
    }

    @Override
    @TruffleBoundary
    public void fill(long offset, long length, byte value) {
        assert offset + length <= byteSize();
        byteArrayBuffer.fill(offset, length, value);
    }

    @Override
    public void copyFrom(WasmMemory source, long sourceOffset, long destinationOffset, long length) {
        assert source instanceof ByteArrayWasmMemory;
        assert destinationOffset < byteSize();
        ByteArrayWasmMemory s = (ByteArrayWasmMemory) source;
        byteArrayBuffer.copyFrom(s.byteArrayBuffer, sourceOffset, destinationOffset, length);
    }

    @Override
    public WasmMemory duplicate() {
        final ByteArrayWasmMemory other = new ByteArrayWasmMemory(declaredMinSize, declaredMaxSize, size(), maxAllowedSize, indexType64);
        byteArrayBuffer.copyTo(other.byteArrayBuffer);
        return other;
    }

    @Override
    public void close() {
        byteArrayBuffer.close();
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return null;
    }
}
