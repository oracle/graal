/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreter;

@ExportLibrary(InteropLibrary.class)
public abstract class WasmMemory implements TruffleObject {
    static final int PAGE_SIZE = 1 << 16;
    static final int LONG_SIZE = 8;

    public abstract void validateAddress(Node node, long address, long offset);

    public abstract void copy(Node node, long src, long dst, long n);

    /**
     * The size of the memory, measured in number of pages.
     */
    public abstract long pageSize();

    /**
     * The size of the memory, measured in bytes.
     */
    public abstract long byteSize();

    public abstract boolean grow(long extraPageSize);

    public boolean growToAddress(long address) {
        final long requiredPageCount = address / PAGE_SIZE + 1;
        final long extraPageCount = Math.max(0, requiredPageCount - pageSize());
        return grow(extraPageCount);
    }

    public abstract long maxPageSize();

    // Checkstyle: stop
    public abstract int load_i32(Node node, long address);

    public abstract long load_i64(Node node, long address);

    public abstract float load_f32(Node node, long address);

    public abstract double load_f64(Node node, long address);

    public abstract int load_i32_8s(Node node, long address);

    public abstract int load_i32_8u(Node node, long address);

    public abstract int load_i32_16s(Node node, long address);

    public abstract int load_i32_16u(Node node, long address);

    public abstract long load_i64_8s(Node node, long address);

    public abstract long load_i64_8u(Node node, long address);

    public abstract long load_i64_16s(Node node, long address);

    public abstract long load_i64_16u(Node node, long address);

    public abstract long load_i64_32s(Node node, long address);

    public abstract long load_i64_32u(Node node, long address);

    public abstract void store_i32(Node node, long address, int value);

    public abstract void store_i64(Node node, long address, long value);

    public abstract void store_f32(Node node, long address, float value);

    public abstract void store_f64(Node node, long address, double value);

    public abstract void store_i32_8(Node node, long address, byte value);

    public abstract void store_i32_16(Node node, long address, short value);

    public abstract void store_i64_8(Node node, long address, byte value);

    public abstract void store_i64_16(Node node, long address, short value);

    public abstract void store_i64_32(Node node, long address, int value);
    // Checkstyle: resume

    public abstract void clear();

    public abstract WasmMemory duplicate();

    long[] view(long address, int length) {
        long[] chunk = new long[length / 8];
        for (long p = address; p < address + length; p += 8) {
            chunk[(int) (p - address) / 8] = load_i64(null, p);
        }
        return chunk;
    }

    String viewByte(long address) {
        final int value = load_i32_8u(null, address);
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
        return byteSize();
    }

    @ExportMessage
    boolean isArrayElementReadable(long address) {
        return address >= 0 && address < getArraySize();
    }

    @ExportMessage
    final boolean isArrayElementModifiable(long address) {
        return isArrayElementReadable(address);
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    final boolean isArrayElementInsertable(long address) {
        return false;
    }

    @ExportMessage
    public Object readArrayElement(long address) throws InvalidArrayIndexException {
        if (!isArrayElementReadable(address)) {
            transferToInterpreter();
            throw InvalidArrayIndexException.create(address);
        }
        return load_i32_8u(null, address);
    }

    @ExportMessage(limit = "3")
    public void writeArrayElement(long address, Object value, @CachedLibrary("value") InteropLibrary valueLib)
                    throws InvalidArrayIndexException, UnsupportedMessageException, UnsupportedTypeException {
        if (!isArrayElementReadable(address)) {
            transferToInterpreter();
            throw InvalidArrayIndexException.create(address);
        }
        byte rawValue;
        if (valueLib.fitsInByte(value)) {
            rawValue = valueLib.asByte(value);
        } else {
            throw UnsupportedTypeException.create(new Object[]{value}, "Only bytes can be stored into WebAssembly memory.");
        }
        store_i32_8(null, address, rawValue);
    }
}
