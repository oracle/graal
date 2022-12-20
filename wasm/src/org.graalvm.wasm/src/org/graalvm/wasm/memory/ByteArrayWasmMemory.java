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

import static java.lang.Integer.compareUnsigned;
import static java.lang.StrictMath.addExact;
import static java.lang.StrictMath.multiplyExact;
import static org.graalvm.wasm.constants.Sizes.MEMORY_PAGE_SIZE;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.oracle.truffle.api.Assumption;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;

public final class ByteArrayWasmMemory extends WasmMemory {
    private final WasmByteArrayBuffer byteArrayBuffer;

    private ByteArrayWasmMemory(int declaredMinSize, int declaredMaxSize, int initialSize, int maxAllowedSize) {
        super(declaredMinSize, declaredMaxSize, initialSize, maxAllowedSize);
        this.byteArrayBuffer = new WasmByteArrayBuffer();
        this.byteArrayBuffer.allocate(initialSize * MEMORY_PAGE_SIZE);
    }

    public ByteArrayWasmMemory(int declaredMinSize, int declaredMaxSize, int maxAllowedSize) {
        this(declaredMinSize, declaredMaxSize, declaredMinSize, maxAllowedSize);
    }

    @Override
    public void copy(Node node, int src, int dst, int n) {
        try {
            System.arraycopy(byteArrayBuffer.buffer(), src, byteArrayBuffer.buffer(), dst, n);
        } catch (final IndexOutOfBoundsException e) {
            // TODO: out of bounds might be in (dest, dest+n).
            throw trapOutOfBounds(node, src, n);
        }
    }

    @Override
    public int size() {
        return byteArrayBuffer.size();
    }

    @Override
    public long byteSize() {
        return byteArrayBuffer.byteSize();
    }

    @Override
    @TruffleBoundary
    public synchronized boolean grow(int extraPageSize) {
        if (extraPageSize == 0) {
            invokeGrowCallback();
            return true;
        } else if (compareUnsigned(extraPageSize, maxAllowedSize) <= 0 && compareUnsigned(size() + extraPageSize, maxAllowedSize) <= 0) {
            // Condition above and limit on maxPageSize (see ModuleLimits#MAX_MEMORY_SIZE)
            // ensure computation of targetByteSize does not overflow.
            final int targetByteSize = multiplyExact(addExact(size(), extraPageSize), MEMORY_PAGE_SIZE);
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
        try {
            return ByteArraySupport.littleEndian().getInt(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long load_i64(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getLong(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public float load_f32(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getFloat(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public double load_f64(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getDouble(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public int load_i32_8s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getByte(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int load_i32_8u(Node node, long address) {
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getByte(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int load_i32_16s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getShort(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public int load_i32_16u(Node node, long address) {
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getShort(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_8s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getByte(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long load_i64_8u(Node node, long address) {
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getByte(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long load_i64_16s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getShort(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_16u(Node node, long address) {
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getShort(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_32s(Node node, long address) {
        try {
            return ByteArraySupport.littleEndian().getInt(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long load_i64_32u(Node node, long address) {
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getInt(byteArrayBuffer.buffer(), address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_i32(Node node, long address, int value) {
        try {
            ByteArraySupport.littleEndian().putInt(byteArrayBuffer.buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_i64(Node node, long address, long value) {
        try {
            ByteArraySupport.littleEndian().putLong(byteArrayBuffer.buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }

    }

    @Override
    public void store_f32(Node node, long address, float value) {
        try {
            ByteArraySupport.littleEndian().putFloat(byteArrayBuffer.buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_f64(Node node, long address, double value) {
        try {
            ByteArraySupport.littleEndian().putDouble(byteArrayBuffer.buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public void store_i32_8(Node node, long address, byte value) {
        try {
            ByteArraySupport.littleEndian().putByte(byteArrayBuffer.buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void store_i32_16(Node node, long address, short value) {
        try {
            ByteArraySupport.littleEndian().putShort(byteArrayBuffer.buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void store_i64_8(Node node, long address, byte value) {
        try {
            ByteArraySupport.littleEndian().putByte(byteArrayBuffer.buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void store_i64_16(Node node, long address, short value) {
        try {
            ByteArraySupport.littleEndian().putShort(byteArrayBuffer.buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void store_i64_32(Node node, long address, int value) {
        try {
            ByteArraySupport.littleEndian().putInt(byteArrayBuffer.buffer(), address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void initialize(byte[] dataInstance, int sourceOffset, int destinationOffset, int length) {
        assert destinationOffset + length <= byteSize();
        System.arraycopy(dataInstance, sourceOffset, byteArrayBuffer.buffer(), destinationOffset, length);
    }

    @Override
    @TruffleBoundary
    public void fill(int offset, int length, byte value) {
        assert offset + length <= byteSize();
        Arrays.fill(byteArrayBuffer.buffer(), offset, offset + length, value);
    }

    @Override
    public void copyFrom(WasmMemory source, int sourceOffset, int destinationOffset, int length) {
        assert source instanceof ByteArrayWasmMemory;
        assert destinationOffset < byteSize();
        ByteArrayWasmMemory s = (ByteArrayWasmMemory) source;
        System.arraycopy(s.byteArrayBuffer.buffer(), sourceOffset, byteArrayBuffer.buffer(), destinationOffset, length);
    }

    @Override
    public WasmMemory duplicate() {
        final ByteArrayWasmMemory other = new ByteArrayWasmMemory(declaredMinSize, declaredMaxSize, size(), maxAllowedSize);
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

    private static final class WasmByteArrayBuffer {
        private static final int MAX_CONSTANT_ATTEMPTS = 5;

        @CompilationFinal private Assumption constantMemoryBufferAssumption;

        @CompilationFinal(dimensions = 0) private byte[] constantBuffer;
        private byte[] dynamicBuffer;

        private int constantAttempts = 0;

        private WasmByteArrayBuffer() {
        }

        @TruffleBoundary
        void allocate(final int byteSize) {
            constantBuffer = null;
            dynamicBuffer = null;
            if (constantAttempts < MAX_CONSTANT_ATTEMPTS) {
                constantMemoryBufferAssumption = Assumption.create("ConstantMemoryBuffer");
                constantAttempts++;
            }
            try {
                if (constantMemoryBufferAssumption.isValid()) {
                    constantBuffer = new byte[byteSize];
                } else {
                    dynamicBuffer = new byte[byteSize];
                }
            } catch (OutOfMemoryError error) {
                throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
            }
        }

        byte[] buffer() {
            if (constantMemoryBufferAssumption.isValid()) {
                return constantBuffer;
            }
            return dynamicBuffer;
        }

        int size() {
            return buffer().length / MEMORY_PAGE_SIZE;
        }

        int byteSize() {
            return buffer().length;
        }

        void grow(final int targetSize) {
            final byte[] currentBuffer = buffer();
            constantMemoryBufferAssumption.invalidate("Memory grow");
            allocate(targetSize);
            System.arraycopy(currentBuffer, 0, buffer(), 0, currentBuffer.length);
        }

        void reset(final int byteSize) {
            constantMemoryBufferAssumption.invalidate("Memory reset");
            allocate(byteSize);
        }

        void close() {
            constantBuffer = null;
            dynamicBuffer = null;
        }

        @TruffleBoundary
        void copyTo(final WasmByteArrayBuffer other) {
            final byte[] currentBuffer = buffer();
            final byte[] otherBuffer = other.buffer();
            System.arraycopy(currentBuffer, 0, otherBuffer, 0, currentBuffer.length);
        }
    }
}
