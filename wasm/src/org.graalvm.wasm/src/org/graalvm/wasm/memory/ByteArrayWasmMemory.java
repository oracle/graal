/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_DECLARATION_SIZE;
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_INSTANCE_SIZE;
import static org.graalvm.wasm.constants.Sizes.MEMORY_PAGE_SIZE;

import org.graalvm.wasm.constants.Sizes;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;

public final class ByteArrayWasmMemory extends WasmMemory {
    /**
     * @see #declaredMinSize()
     */
    private final int declaredMinSize;

    /**
     * @see #declaredMaxSize()
     */
    private final int declaredMaxSize;

    /**
     * The maximum practical size of this memory instance (measured in number of
     * {@link Sizes#MEMORY_PAGE_SIZE pages}).
     * <p>
     * It is the minimum between {@link #declaredMaxSize the limit defined in the module binary},
     * {@link Sizes#MAX_MEMORY_INSTANCE_SIZE the GraalWasm limit} and any additional limit (the JS
     * API for example has lower limits).
     * <p>
     * This is different from {@link #declaredMaxSize()}, which can be higher.
     */
    private final int maxAllowedSize;

    private byte[] buffer;

    private ByteArrayWasmMemory(int declaredMinSize, int declaredMaxSize, int initialSize, int maxAllowedSize) {
        assert compareUnsigned(declaredMinSize, initialSize) <= 0;
        assert compareUnsigned(initialSize, maxAllowedSize) <= 0;
        assert compareUnsigned(maxAllowedSize, declaredMaxSize) <= 0;
        assert compareUnsigned(maxAllowedSize, MAX_MEMORY_INSTANCE_SIZE) <= 0;
        assert compareUnsigned(declaredMaxSize, MAX_MEMORY_DECLARATION_SIZE) <= 0;

        this.declaredMinSize = declaredMinSize;
        this.declaredMaxSize = declaredMaxSize;
        this.maxAllowedSize = maxAllowedSize;
        try {
            this.buffer = new byte[initialSize * MEMORY_PAGE_SIZE];
        } catch (OutOfMemoryError error) {
            CompilerDirectives.transferToInterpreter();
            throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
        }
    }

    public ByteArrayWasmMemory(int declaredMinSize, int declaredMaxSize, int maxAllowedSize) {
        this(declaredMinSize, declaredMaxSize, declaredMinSize, maxAllowedSize);
    }

    @TruffleBoundary
    private WasmException trapOutOfBounds(Node node, int address, long size) {
        final String message = String.format("%d-byte memory access at address 0x%016X (%d) is out-of-bounds (memory size %d bytes).",
                        size, address, address, byteSize());
        return WasmException.create(Failure.OUT_OF_BOUNDS_MEMORY_ACCESS, node, message);
    }

    @Override
    public void copy(Node node, int src, int dst, int n) {
        try {
            System.arraycopy(buffer, src, buffer, dst, n);
        } catch (final IndexOutOfBoundsException e) {
            // TODO: out of bounds might be in (dest, dest+n).
            throw trapOutOfBounds(node, src, n);
        }
    }

    @Override
    public int size() {
        return buffer.length / MEMORY_PAGE_SIZE;
    }

    @Override
    public int byteSize() {
        return buffer.length;
    }

    @Override
    public int declaredMinSize() {
        return declaredMinSize;
    }

    @Override
    public int declaredMaxSize() {
        return declaredMaxSize;
    }

    @Override
    @TruffleBoundary
    public synchronized boolean grow(int extraPageSize) {
        if (extraPageSize == 0) {
            return true;
        } else if (compareUnsigned(extraPageSize, maxAllowedSize) <= 0 && compareUnsigned(size() + extraPageSize, maxAllowedSize) <= 0) {
            try {
                // Condition above and limit on maxPageSize (see ModuleLimits#MAX_MEMORY_SIZE)
                // ensure computation of targetByteSize does not overflow.
                final int targetByteSize = multiplyExact(addExact(size(), extraPageSize), MEMORY_PAGE_SIZE);
                final byte[] newBuffer = new byte[targetByteSize];
                System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
                buffer = newBuffer;
                return true;
            } catch (OutOfMemoryError error) {
                throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
            }
        } else {
            return false;
        }
    }

    @Override
    public void reset() {
        buffer = new byte[declaredMinSize * MEMORY_PAGE_SIZE];
    }

    @Override
    public int load_i32(Node node, int address) {
        try {
            return ByteArraySupport.littleEndian().getInt(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long load_i64(Node node, int address) {
        try {
            return ByteArraySupport.littleEndian().getLong(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public float load_f32(Node node, int address) {
        try {
            return ByteArraySupport.littleEndian().getFloat(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public double load_f64(Node node, int address) {
        try {
            return ByteArraySupport.littleEndian().getDouble(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public int load_i32_8s(Node node, int address) {
        try {
            return ByteArraySupport.littleEndian().getByte(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int load_i32_8u(Node node, int address) {
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getByte(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int load_i32_16s(Node node, int address) {
        try {
            return ByteArraySupport.littleEndian().getShort(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public int load_i32_16u(Node node, int address) {
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getShort(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_8s(Node node, int address) {
        try {
            return ByteArraySupport.littleEndian().getByte(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long load_i64_8u(Node node, int address) {
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getByte(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long load_i64_16s(Node node, int address) {
        try {
            return ByteArraySupport.littleEndian().getShort(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_16u(Node node, int address) {
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getShort(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_32s(Node node, int address) {
        try {
            return ByteArraySupport.littleEndian().getInt(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long load_i64_32u(Node node, int address) {
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getInt(buffer, address);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_i32(Node node, int address, int value) {
        try {
            ByteArraySupport.littleEndian().putInt(buffer, address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_i64(Node node, int address, long value) {
        try {
            ByteArraySupport.littleEndian().putLong(buffer, address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }

    }

    @Override
    public void store_f32(Node node, int address, float value) {
        try {
            ByteArraySupport.littleEndian().putFloat(buffer, address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_f64(Node node, int address, double value) {
        try {
            ByteArraySupport.littleEndian().putDouble(buffer, address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public void store_i32_8(Node node, int address, byte value) {
        try {
            ByteArraySupport.littleEndian().putByte(buffer, address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void store_i32_16(Node node, int address, short value) {
        try {
            ByteArraySupport.littleEndian().putShort(buffer, address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void store_i64_8(Node node, int address, byte value) {
        try {
            ByteArraySupport.littleEndian().putByte(buffer, address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void store_i64_16(Node node, int address, short value) {
        try {
            ByteArraySupport.littleEndian().putShort(buffer, address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void store_i64_32(Node node, int address, int value) {
        try {
            ByteArraySupport.littleEndian().putInt(buffer, address, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public WasmMemory duplicate() {
        final ByteArrayWasmMemory other = new ByteArrayWasmMemory(declaredMinSize, declaredMaxSize, size(), maxAllowedSize);
        System.arraycopy(buffer, 0, other.buffer, 0, buffer.length);
        return other;
    }
}
