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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import java.util.Arrays;

public final class ByteArrayWasmMemory extends WasmMemory {
    private byte[] buffer;
    private final int maxPageSize;

    public ByteArrayWasmMemory(int initPageSize, int maxPageSize) {
        this.buffer = new byte[initPageSize * PAGE_SIZE];
        this.maxPageSize = maxPageSize;
    }

    @TruffleBoundary
    private WasmException trapOutOfBounds(Node node, int address, long offset) {
        String message = String.format("%d-byte memory access at address 0x%016X (%d) is out-of-bounds (memory size %d bytes).",
                        offset, address, address, byteSize());
        return WasmException.create(Failure.UNSPECIFIED_TRAP, node, message);
    }

    @Override
    public void copy(Node node, int src, int dst, int n) {
        try {
            System.arraycopy(buffer, src, buffer, dst, n);
        } catch (IndexOutOfBoundsException e) {
            // TODO: out of bounds might be in (dest, dest+n).
            throw trapOutOfBounds(node, src, n);
        }
    }

    @Override
    public void clear() {
        Arrays.fill(buffer, (byte) 0);
    }

    @Override
    public int pageSize() {
        return buffer.length / PAGE_SIZE;
    }

    @Override
    public int byteSize() {
        return buffer.length;
    }

    @Override
    public int maxPageSize() {
        return maxPageSize;
    }

    @Override
    @TruffleBoundary
    public synchronized boolean grow(int extraPageSize) {
        if (extraPageSize < 0) {
            throw WasmException.create(Failure.UNSPECIFIED_TRAP, null, "Extra size cannot be negative.");
        }
        int targetSize = byteSize() + extraPageSize * PAGE_SIZE;
        if (maxPageSize >= 0 && targetSize > maxPageSize * PAGE_SIZE) {
            // Cannot grow the memory beyond maxPageSize bytes.
            return false;
        }
        if (targetSize * PAGE_SIZE == byteSize()) {
            return true;
        }
        byte[] newBuffer = new byte[targetSize];
        System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
        buffer = newBuffer;
        return true;
    }

    @Override
    public int load_i32(Node node, int address) {
        int value;
        try {
            value = ByteArraySupport.littleEndian().getInt(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
        return value;
    }

    @Override
    public long load_i64(Node node, int address) {
        long value;
        try {
            value = ByteArraySupport.littleEndian().getLong(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
        return value;
    }

    @Override
    public float load_f32(Node node, int address) {
        float value;
        try {
            value = ByteArraySupport.littleEndian().getFloat(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
        return value;
    }

    @Override
    public double load_f64(Node node, int address) {
        double value;
        try {
            value = ByteArraySupport.littleEndian().getDouble(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
        return value;
    }

    @Override
    public int load_i32_8s(Node node, int address) {
        int value;
        try {
            value = ByteArraySupport.littleEndian().getByte(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
        return value;
    }

    @Override
    public int load_i32_8u(Node node, int address) {
        int value;
        try {
            value = 0x0000_00ff & ByteArraySupport.littleEndian().getByte(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
        return value;
    }

    @Override
    public int load_i32_16s(Node node, int address) {
        int value;
        try {
            value = ByteArraySupport.littleEndian().getShort(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
        return value;
    }

    @Override
    public int load_i32_16u(Node node, int address) {
        int value;
        try {
            value = 0x0000_ffff & ByteArraySupport.littleEndian().getShort(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
        return value;
    }

    @Override
    public long load_i64_8s(Node node, int address) {
        long value;
        try {
            value = 0x0000_ffff & ByteArraySupport.littleEndian().getByte(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
        return value;
    }

    @Override
    public long load_i64_8u(Node node, int address) {
        long value;
        try {
            value = 0x0000_0000_0000_00ffL & ByteArraySupport.littleEndian().getByte(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
        return value;
    }

    @Override
    public long load_i64_16s(Node node, int address) {
        short value;
        try {
            value = ByteArraySupport.littleEndian().getShort(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
        return value;
    }

    @Override
    public long load_i64_16u(Node node, int address) {
        long value;
        try {
            value = 0x0000_0000_0000_ffffL & ByteArraySupport.littleEndian().getShort(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
        return value;
    }

    @Override
    public long load_i64_32s(Node node, int address) {
        long value;
        try {
            value = ByteArraySupport.littleEndian().getInt(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
        return value;
    }

    @Override
    public long load_i64_32u(Node node, int address) {
        long value;
        try {
            value = 0x0000_0000_ffff_ffffL & ByteArraySupport.littleEndian().getInt(buffer, address);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
        return value;
    }

    @Override
    public void store_i32(Node node, int address, int value) {
        try {
            ByteArraySupport.littleEndian().putInt(buffer, address, value);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public void store_i64(Node node, int address, long value) {
        try {
            ByteArraySupport.littleEndian().putLong(buffer, address, value);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }

    }

    @Override
    public void store_f32(Node node, int address, float value) {
        try {
            ByteArraySupport.littleEndian().putFloat(buffer, address, value);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }

    }

    @Override
    public void store_f64(Node node, int address, double value) {
        try {
            ByteArraySupport.littleEndian().putDouble(buffer, address, value);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 8);
        }
    }

    @Override
    public void store_i32_8(Node node, int address, byte value) {
        try {
            ByteArraySupport.littleEndian().putByte(buffer, address, value);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void store_i32_16(Node node, int address, short value) {
        try {
            ByteArraySupport.littleEndian().putShort(buffer, address, value);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void store_i64_8(Node node, int address, byte value) {
        try {
            ByteArraySupport.littleEndian().putByte(buffer, address, value);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 1);
        }
    }

    @Override
    public void store_i64_16(Node node, int address, short value) {
        try {
            ByteArraySupport.littleEndian().putShort(buffer, address, value);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 2);
        }
    }

    @Override
    public void store_i64_32(Node node, int address, int value) {
        try {
            ByteArraySupport.littleEndian().putInt(buffer, address, value);
        } catch (IndexOutOfBoundsException e) {
            throw trapOutOfBounds(node, address, 4);
        }
    }

    @Override
    public WasmMemory duplicate() {
        final ByteArrayWasmMemory other = new ByteArrayWasmMemory(pageSize(), maxPageSize());
        System.arraycopy(buffer, 0, other.buffer, 0, buffer.length);
        return other;
    }
}
