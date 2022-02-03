/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;

public final class ByteArrayWasmMemory extends WasmMemory {

    private byte[] buffer;

    private ByteArrayWasmMemory(int declaredMinSize, int declaredMaxSize, int initialSize, int maxAllowedSize) {
        super(declaredMinSize, declaredMaxSize, initialSize, maxAllowedSize);
        this.buffer = allocateBuffer(initialSize * MEMORY_PAGE_SIZE);
    }

    public ByteArrayWasmMemory(int declaredMinSize, int declaredMaxSize, int maxAllowedSize) {
        this(declaredMinSize, declaredMaxSize, declaredMinSize, maxAllowedSize);
    }

    @TruffleBoundary
    private static byte[] allocateBuffer(final int byteSize) {
        try {
            return new byte[byteSize];
        } catch (OutOfMemoryError error) {
            throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
        }
    }

    private int validateAddress(Node node, long address, int length) {
        assert length >= 1;
        if (address < 0 || address > Integer.MAX_VALUE) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw trapOutOfBounds(node, address, length);
        }
        return (int) address;
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
    public long byteSize() {
        return buffer.length;
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
            final int sourceByteSize = buffer.length;
            final byte[] newBuffer = allocateBuffer(targetByteSize);
            System.arraycopy(buffer, 0, newBuffer, 0, sourceByteSize);
            buffer = newBuffer;
            invokeGrowCallback();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void reset() {
        buffer = allocateBuffer(declaredMinSize * MEMORY_PAGE_SIZE);
    }

    @Override
    public int load_i32(Node node, long address) {
        int intAddress = validateAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getInt(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long load_i64(Node node, long address) {
        int intAddress = validateAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getLong(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public float load_f32(Node node, long address) {
        int intAddress = validateAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getFloat(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public double load_f64(Node node, long address) {
        int intAddress = validateAddress(node, address, 8);
        try {
            return ByteArraySupport.littleEndian().getDouble(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public int load_i32_8s(Node node, long address) {
        int intAddress = validateAddress(node, address, 1);
        try {
            return ByteArraySupport.littleEndian().getByte(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int load_i32_8u(Node node, long address) {
        int intAddress = validateAddress(node, address, 1);
        try {
            return 0x0000_00ff & ByteArraySupport.littleEndian().getByte(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public int load_i32_16s(Node node, long address) {
        int intAddress = validateAddress(node, address, 2);
        try {
            return ByteArraySupport.littleEndian().getShort(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public int load_i32_16u(Node node, long address) {
        int intAddress = validateAddress(node, address, 2);
        try {
            return 0x0000_ffff & ByteArraySupport.littleEndian().getShort(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_8s(Node node, long address) {
        int intAddress = validateAddress(node, address, 1);
        try {
            return ByteArraySupport.littleEndian().getByte(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long load_i64_8u(Node node, long address) {
        int intAddress = validateAddress(node, address, 1);
        try {
            return 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getByte(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public long load_i64_16s(Node node, long address) {
        int intAddress = validateAddress(node, address, 2);
        try {
            return ByteArraySupport.littleEndian().getShort(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_16u(Node node, long address) {
        int intAddress = validateAddress(node, address, 2);
        try {
            return 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getShort(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public long load_i64_32s(Node node, long address) {
        int intAddress = validateAddress(node, address, 4);
        try {
            return ByteArraySupport.littleEndian().getInt(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public long load_i64_32u(Node node, long address) {
        int intAddress = validateAddress(node, address, 4);
        try {
            return 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getInt(buffer, intAddress);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_i32(Node node, long address, int value) {
        int intAddress = validateAddress(node, address, 4);
        try {
            ByteArraySupport.littleEndian().putInt(buffer, intAddress, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_i64(Node node, long address, long value) {
        int intAddress = validateAddress(node, address, 8);
        try {
            ByteArraySupport.littleEndian().putLong(buffer, intAddress, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }

    }

    @Override
    public void store_f32(Node node, long address, float value) {
        int intAddress = validateAddress(node, address, 4);
        try {
            ByteArraySupport.littleEndian().putFloat(buffer, intAddress, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_f64(Node node, long address, double value) {
        int intAddress = validateAddress(node, address, 8);
        try {
            ByteArraySupport.littleEndian().putDouble(buffer, intAddress, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public void store_i32_8(Node node, long address, byte value) {
        int intAddress = validateAddress(node, address, 1);
        try {
            ByteArraySupport.littleEndian().putByte(buffer, intAddress, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void store_i32_16(Node node, long address, short value) {
        int intAddress = validateAddress(node, address, 2);
        try {
            ByteArraySupport.littleEndian().putShort(buffer, intAddress, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void store_i64_8(Node node, long address, byte value) {
        int intAddress = validateAddress(node, address, 1);
        try {
            ByteArraySupport.littleEndian().putByte(buffer, intAddress, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void store_i64_16(Node node, long address, short value) {
        int intAddress = validateAddress(node, address, 2);
        try {
            ByteArraySupport.littleEndian().putShort(buffer, intAddress, value);
        } catch (final IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void store_i64_32(Node node, long address, int value) {
        int intAddress = validateAddress(node, address, 4);
        try {
            ByteArraySupport.littleEndian().putInt(buffer, intAddress, value);
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

    @Override
    public void close() {
        buffer = null;
    }

    @Override
    public ByteBuffer asByteBuffer() {
        return null;
    }
}
