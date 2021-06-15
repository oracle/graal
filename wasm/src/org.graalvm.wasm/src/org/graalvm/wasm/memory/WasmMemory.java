/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.constants.Sizes;
import org.graalvm.wasm.nodes.WasmNode;

import java.nio.charset.StandardCharsets;

import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreter;
import static java.lang.Math.toIntExact;

@ExportLibrary(InteropLibrary.class)
public abstract class WasmMemory implements TruffleObject {

    public abstract void copy(Node node, int src, int dst, int n);

    /**
     * The current size of this memory instance (measured in number of {@link Sizes#MEMORY_PAGE_SIZE
     * pages}).
     */
    public abstract int size();

    /**
     * The current size of this memory instance (measured in bytes).
     */
    public abstract int byteSize();

    /**
     * The minimum size of this memory as declared in the binary (measured in number of
     * {@link Sizes#MEMORY_PAGE_SIZE pages}).
     * <p>
     * This is a lower bound on this memory's size. This memory can only be imported with a lower or
     * equal minimum size.
     */
    public abstract int declaredMinSize();

    /**
     * The maximum size of this memory as declared in the binary (measured in number of
     * {@link Sizes#MEMORY_PAGE_SIZE pages}).
     * <p>
     * This is an upper bound on this memory's size. This memory can only be imported with a greater
     * or equal maximum size.
     * <p>
     * This is different from the internal maximum allowed size, which can be lower.
     */
    public abstract int declaredMaxSize();

    public abstract boolean grow(int extraPageSize);

    /**
     * Shrinks this memory's size to its {@link #declaredMinSize()} initial size}, and sets all
     * bytes to 0.
     * <p>
     * Note: this does not restore content from data section. For this, use
     * {@link org.graalvm.wasm.BinaryParser#resetMemoryState}.
     */
    public abstract void reset();

    // Checkstyle: stop
    public abstract int load_i32(Node node, int address);

    public abstract long load_i64(Node node, int address);

    public abstract float load_f32(Node node, int address);

    public abstract double load_f64(Node node, int address);

    public abstract int load_i32_8s(Node node, int address);

    public abstract int load_i32_8u(Node node, int address);

    public abstract int load_i32_16s(Node node, int address);

    public abstract int load_i32_16u(Node node, int address);

    public abstract long load_i64_8s(Node node, int address);

    public abstract long load_i64_8u(Node node, int address);

    public abstract long load_i64_16s(Node node, int address);

    public abstract long load_i64_16u(Node node, int address);

    public abstract long load_i64_32s(Node node, int address);

    public abstract long load_i64_32u(Node node, int address);

    public abstract void store_i32(Node node, int address, int value);

    public abstract void store_i64(Node node, int address, long value);

    public abstract void store_f32(Node node, int address, float value);

    public abstract void store_f64(Node node, int address, double value);

    public abstract void store_i32_8(Node node, int address, byte value);

    public abstract void store_i32_16(Node node, int address, short value);

    public abstract void store_i64_8(Node node, int address, byte value);

    public abstract void store_i64_16(Node node, int address, short value);

    public abstract void store_i64_32(Node node, int address, int value);
    // Checkstyle: resume

    public abstract WasmMemory duplicate();

    /**
     * Reads the null-terminated UTF-8 string starting at {@code startOffset}.
     *
     * @param startOffset memory index of the first character
     * @param node a node indicating the location where this read occurred in the Truffle AST. It
     *            may be {@code null} to indicate that the location is not available.
     * @return the read {@code String}
     */
    @CompilerDirectives.TruffleBoundary
    public String readString(int startOffset, WasmNode node) {
        ByteArrayList bytes = new ByteArrayList();
        byte currentByte;
        int offset = startOffset;

        while ((currentByte = (byte) load_i32_8u(node, offset)) != 0) {
            bytes.add(currentByte);
            ++offset;
        }

        return new String(bytes.toArray(), StandardCharsets.UTF_8);
    }

    /**
     * Reads the UTF-8 string of length {@code length} starting at {@code startOffset}.
     *
     * @param startOffset memory index of the first character
     * @param length length of the UTF-8 string to read in bytes
     * @param node a node indicating the location where this read occurred in the Truffle AST. It
     *            may be {@code null} to indicate that the location is not available.
     * @return the read {@code String}
     */
    @CompilerDirectives.TruffleBoundary
    public final String readString(int startOffset, int length, Node node) {
        ByteArrayList bytes = new ByteArrayList();

        for (int i = 0; i < length; ++i) {
            bytes.add((byte) load_i32_8u(node, startOffset + i));
        }

        return new String(bytes.toArray(), StandardCharsets.UTF_8);
    }

    /**
     * Writes a Java String at offset {@code offset}.
     * <p>
     * The written string is encoded as UTF-8 and <em>not</em> terminated with a null character.
     *
     * @param node a node indicating the location where this write occurred in the Truffle AST. It
     *            may be {@code null} to indicate that the location is not available.
     * @param string the string to write
     * @param offset memory index where to write the string
     * @param length the maximum number of bytes to write, including the trailing null character
     * @return the number of bytes written, including the trailing null character
     */
    @CompilerDirectives.TruffleBoundary
    public final int writeString(Node node, String string, int offset, int length) {
        final byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        int i = 0;
        for (; i < bytes.length && i < length; ++i) {
            store_i32_8(node, offset + i, bytes[i]);
        }
        return i;
    }

    public final int writeString(Node node, String string, int offset) {
        return writeString(node, string, offset, Integer.MAX_VALUE);
    }

    /**
     * Returns the number of bytes needed to write {@code string} with {@link #writeString}.
     *
     * @param string the string to write
     * @return the number of bytes needed to write {@code string}
     */
    @CompilerDirectives.TruffleBoundary
    public static int encodedStringLength(String string) {
        return string.getBytes(StandardCharsets.UTF_8).length;
    }

    long[] view(int address, int length) {
        long[] chunk = new long[length / 8];
        for (int p = address; p < address + length; p += 8) {
            chunk[(p - address) / 8] = load_i64(null, p);
        }
        return chunk;
    }

    String viewByte(int address) {
        final int value = load_i32_8u(null, address);
        String result = Integer.toHexString(value);
        if (result.length() == 1) {
            result = "0" + result;
        }
        return result;
    }

    public String hexView(int address, int length) {
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
        if (!isArrayElementReadable(toIntExact(address))) {
            transferToInterpreter();
            throw InvalidArrayIndexException.create(address);
        }
        return load_i32_8u(null, toIntExact(address));
    }

    @ExportMessage(limit = "3")
    public void writeArrayElement(long address, Object value, @CachedLibrary("value") InteropLibrary valueLib)
                    throws InvalidArrayIndexException, UnsupportedMessageException, UnsupportedTypeException {
        if (!isArrayElementReadable(toIntExact(address))) {
            transferToInterpreter();
            throw InvalidArrayIndexException.create(address);
        }
        byte rawValue;
        if (valueLib.fitsInByte(value)) {
            rawValue = valueLib.asByte(value);
        } else {
            throw UnsupportedTypeException.create(new Object[]{value}, "Only bytes can be stored into WebAssembly memory.");
        }
        store_i32_8(null, toIntExact(address), rawValue);
    }
}
