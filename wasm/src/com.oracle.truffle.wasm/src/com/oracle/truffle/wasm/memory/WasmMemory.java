/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.memory;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreter;

@ExportLibrary(InteropLibrary.class)
public abstract class WasmMemory implements TruffleObject {
    static final int PAGE_SIZE = 1 << 16;
    static final int LONG_SIZE = 8;

    public abstract void validateAddress(long address, int size);

    public abstract void copy(long src, long dst, long n);

    /**
     * The size of the memory, measured in number of pages.
     */
    public abstract long pageSize();

    /**
     * The size of the memory, measured in bytes.
     */
    public abstract long byteSize();

    public abstract boolean grow(long extraSize);

    public abstract long maxPageSize();

    // Checkstyle: stop
    public abstract int load_i32(long address);

    public abstract long load_i64(long address);

    public abstract float load_f32(long address);

    public abstract double load_f64(long address);

    public abstract int load_i32_8s(long address);

    public abstract int load_i32_8u(long address);

    public abstract int load_i32_16s(long address);

    public abstract int load_i32_16u(long address);

    public abstract long load_i64_8s(long address);

    public abstract long load_i64_8u(long address);

    public abstract long load_i64_16s(long address);

    public abstract long load_i64_16u(long address);

    public abstract long load_i64_32s(long address);

    public abstract long load_i64_32u(long address);

    public abstract void store_i32(long address, int value);

    public abstract void store_i64(long address, long value);

    public abstract void store_f32(long address, float value);

    public abstract void store_f64(long address, double value);

    public abstract void store_i32_8(long address, byte value);

    public abstract void store_i32_16(long address, short value);

    public abstract void store_i64_8(long address, byte value);

    public abstract void store_i64_16(long address, short value);

    public abstract void store_i64_32(long address, int value);
    // Checkstyle: resume

    public abstract void clear();

    public abstract WasmMemory duplicate();

    long[] view(long address, int length) {
        long[] chunk = new long[length / 8];
        for (long p = address; p < address + length; p += 8) {
            chunk[(int) (p - address) / 8] = load_i64(p);
        }
        return chunk;
    }

    String viewByte(long address) {
        final int value = load_i32_8u(address);
        String result = Integer.toHexString(value);
        if (result.length() == 1) {
            result = "0" + result;
        }
        return result;
    }

    public String hexView(long address, int length) {
        long[] chunk = view(address, length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunk.length; i++) {
            sb.append("0x").append(hex(address + i * 8)).append(" | ");
            for (int j = 0; j < 8; j++) {
                sb.append(viewByte(address + i * 8 + j)).append(" ");
            }
            sb.append("| ");
            sb.append(batch(hex(chunk[i]), 2)).append("\n");
        }
        return sb.toString();
    }

    private static String hex(long value) {
        return pad(Long.toHexString(value), 16);
    }

    private static String batch(String s, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            result.insert(0, s.charAt(i));
            if ((i + 1) % count == 0) {
                result.insert(0, " ");
            }
        }
        return result.reverse().toString();
    }

    private static String pad(String s, int length) {
        StringBuilder padded = new StringBuilder(s);
        while (padded.length() < length) {
            padded.insert(0, "0");
        }
        return padded.toString();
    }

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return byteSize() / LONG_SIZE;
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        return index >= 0 && index < getArraySize();
    }

    @ExportMessage
    final boolean isArrayElementModifiable(long index) {
        return isArrayElementReadable(index);
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    final boolean isArrayElementInsertable(long index) {
        return false;
    }

    @ExportMessage
    public Object readArrayElement(long index) throws InvalidArrayIndexException {
        if (!isArrayElementReadable(index)) {
            transferToInterpreter();
            throw InvalidArrayIndexException.create(index);
        }
        long address = index * LONG_SIZE;
        return load_i64(address);
    }

    @ExportMessage
    public void writeArrayElement(long index64, Object value) throws InvalidArrayIndexException, UnsupportedMessageException {
        if (!isArrayElementReadable(index64)) {
            transferToInterpreter();
            throw InvalidArrayIndexException.create(index64);
        }
        long rawValue;
        if (value instanceof Integer || value instanceof Long) {
            rawValue = ((Number) value).longValue();
        } else if (value instanceof Float) {
            rawValue = Float.floatToRawIntBits((Float) value);
        } else if (value instanceof Double) {
            rawValue = Double.doubleToRawLongBits((Double) value);
        } else {
            throw UnsupportedMessageException.create();
        }
        long address = index64 * LONG_SIZE;
        store_i64(address, rawValue);
    }
}
