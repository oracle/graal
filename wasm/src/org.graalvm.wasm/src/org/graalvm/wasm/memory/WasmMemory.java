/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_DECLARATION_SIZE;
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_INSTANCE_SIZE;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.graalvm.wasm.EmbedderDataHolder;
import org.graalvm.wasm.api.WebAssembly;
import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.constants.Sizes;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.nodes.WasmFunctionNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

@ExportLibrary(InteropLibrary.class)
public abstract class WasmMemory extends EmbedderDataHolder implements TruffleObject {

    /**
     * @see #declaredMinSize()
     */
    protected final int declaredMinSize;

    /**
     * @see #declaredMaxSize()
     */
    protected final int declaredMaxSize;

    /**
     * @see #minSize()
     */
    protected int currentMinSize;

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
    protected final int maxAllowedSize;

    /**
     * Optional grow callback to notify the embedder .
     */
    private Object growCallback;

    protected WasmMemory(int declaredMinSize, int declaredMaxSize, int initialSize, int maxAllowedSize) {
        assert compareUnsigned(declaredMinSize, initialSize) <= 0;
        assert compareUnsigned(initialSize, maxAllowedSize) <= 0;
        assert compareUnsigned(maxAllowedSize, declaredMaxSize) <= 0;
        assert compareUnsigned(maxAllowedSize, MAX_MEMORY_INSTANCE_SIZE) <= 0;
        assert compareUnsigned(declaredMaxSize, MAX_MEMORY_DECLARATION_SIZE) <= 0;

        this.declaredMinSize = declaredMinSize;
        this.declaredMaxSize = declaredMaxSize;
        this.currentMinSize = declaredMinSize;
        this.maxAllowedSize = maxAllowedSize;
    }

    public abstract void copy(Node node, int src, int dst, int n);

    /**
     * The current size of this memory instance (measured in number of {@link Sizes#MEMORY_PAGE_SIZE
     * pages}).
     */
    public abstract int size();

    /**
     * The current size of this memory instance (measured in bytes).
     */
    public abstract long byteSize();

    /**
     * The minimum size of this memory as declared in the binary (measured in number of
     * {@link Sizes#MEMORY_PAGE_SIZE pages}).
     * <p>
     * This is different from the current minimum size, which can be larger.
     */
    public final int declaredMinSize() {
        return declaredMinSize;
    }

    /**
     * The maximum size of this memory as declared in the binary (measured in number of
     * {@link Sizes#MEMORY_PAGE_SIZE pages}).
     * <p>
     * This is an upper bound on this memory's size. This memory can only be imported with a greater
     * or equal maximum size.
     * <p>
     * This is different from the internal maximum allowed size, which can be lower.
     */
    public final int declaredMaxSize() {
        return declaredMaxSize;
    }

    /**
     * The current minimum size of the memory (measured in number of {@link Sizes#MEMORY_PAGE_SIZE
     * pages}). The size can change based on calls to {@link #grow(int)}.
     * <p>
     * This is a lower bound on this memory's size. This memory can only be imported with a lower or
     * equal minimum size.
     */
    public final int minSize() {
        return currentMinSize;
    }

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

    public abstract WasmMemory duplicate();

    /**
     * Initializes the content of the memory based on the given data instance.
     * 
     * @param dataInstance The source data instance that should be copied to the memory
     * @param sourceOffset The offset in the source data segment
     * @param destinationOffset The offset in the memory
     * @param length The number of bytes that should be copied
     */
    public abstract void initialize(byte[] dataInstance, int sourceOffset, int destinationOffset, int length);

    /**
     * Fills the memory with the given value.
     * 
     * @param offset The offset in the memory
     * @param length The number of bytes that should be filled
     * @param value The value that should be used for filling the memory
     */
    public abstract void fill(int offset, int length, byte value);

    /**
     * Copies data from another memory into this memory.
     * 
     * @param source The source memory
     * @param sourceOffset The offset in the source memory
     * @param destinationOffset The offset in this memory
     * @param length The number of bytes that should be copied
     */
    public abstract void copyFrom(WasmMemory source, int sourceOffset, int destinationOffset, int length);

    @TruffleBoundary
    protected final WasmException trapOutOfBounds(Node node, long address, int length) {
        final String message = String.format("%d-byte memory access at address 0x%016X (%d) is out-of-bounds (memory size %d bytes).",
                        length, address, address, byteSize());
        return WasmException.create(Failure.OUT_OF_BOUNDS_MEMORY_ACCESS, node, message);
    }

    /**
     * Reads the null-terminated UTF-8 string starting at {@code startOffset}.
     *
     * @param startOffset memory index of the first character
     * @param node a node indicating the location where this read occurred in the Truffle AST. It
     *            may be {@code null} to indicate that the location is not available.
     * @return the read {@code String}
     */
    @CompilerDirectives.TruffleBoundary
    public String readString(int startOffset, WasmFunctionNode node) {
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
            sb.append("0x").append(hex(address + i * 8L)).append(" | ");
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

    @SuppressWarnings("static-method")
    @ExportMessage
    final boolean hasBufferElements() {
        return true;
    }

    @ExportMessage
    final long getBufferSize() {
        return byteSize();
    }

    private void checkOffset(long byteOffset, int opLength, BranchProfile errorBranch) throws InvalidBufferOffsetException {
        if (byteOffset < 0 || getBufferSize() - opLength < byteOffset) {
            errorBranch.enter();
            throw InvalidBufferOffsetException.create(byteOffset, opLength);
        }
    }

    @ExportMessage
    final byte readBufferByte(long byteOffset,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Byte.BYTES, errorBranch);
        return (byte) load_i32_8s(null, (int) byteOffset);
    }

    @ExportMessage
    final short readBufferShort(ByteOrder order, long byteOffset,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Short.BYTES, errorBranch);
        short result = (short) load_i32_16s(null, (int) byteOffset);
        if (order == ByteOrder.BIG_ENDIAN) {
            result = Short.reverseBytes(result);
        }
        return result;
    }

    @ExportMessage
    final int readBufferInt(ByteOrder order, long byteOffset,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Integer.BYTES, errorBranch);
        int result = load_i32(null, (int) byteOffset);
        if (order == ByteOrder.BIG_ENDIAN) {
            result = Integer.reverseBytes(result);
        }
        return result;
    }

    @ExportMessage
    final long readBufferLong(ByteOrder order, long byteOffset,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Long.BYTES, errorBranch);
        long result = load_i64(null, (int) byteOffset);
        if (order == ByteOrder.BIG_ENDIAN) {
            result = Long.reverseBytes(result);
        }
        return result;
    }

    @ExportMessage
    final float readBufferFloat(ByteOrder order, long byteOffset,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Float.BYTES, errorBranch);
        float result = load_f32(null, (int) byteOffset);
        if (order == ByteOrder.BIG_ENDIAN) {
            result = Float.intBitsToFloat(Integer.reverseBytes(Float.floatToRawIntBits(result)));
        }
        return result;
    }

    @ExportMessage
    final double readBufferDouble(ByteOrder order, long byteOffset,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Double.BYTES, errorBranch);
        double result = load_f64(null, (int) byteOffset);
        if (order == ByteOrder.BIG_ENDIAN) {
            result = Double.longBitsToDouble(Long.reverseBytes(Double.doubleToRawLongBits(result)));
        }
        return result;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    final boolean isBufferWritable() {
        return true;
    }

    @ExportMessage
    final void writeBufferByte(long byteOffset, byte value,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Byte.BYTES, errorBranch);
        store_i32_8(null, (int) byteOffset, value);
    }

    @ExportMessage
    final void writeBufferShort(ByteOrder order, long byteOffset, short value,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Short.BYTES, errorBranch);
        short actualValue = (order == ByteOrder.LITTLE_ENDIAN) ? value : Short.reverseBytes(value);
        store_i32_16(null, (int) byteOffset, actualValue);
    }

    @ExportMessage
    final void writeBufferInt(ByteOrder order, long byteOffset, int value,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Integer.BYTES, errorBranch);
        int actualValue = (order == ByteOrder.LITTLE_ENDIAN) ? value : Integer.reverseBytes(value);
        store_i32(null, (int) byteOffset, actualValue);
    }

    @ExportMessage
    final void writeBufferLong(ByteOrder order, long byteOffset, long value,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Long.BYTES, errorBranch);
        long actualValue = (order == ByteOrder.LITTLE_ENDIAN) ? value : Long.reverseBytes(value);
        store_i64(null, (int) byteOffset, actualValue);
    }

    @ExportMessage
    final void writeBufferFloat(ByteOrder order, long byteOffset, float value,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Float.BYTES, errorBranch);
        float actualValue = (order == ByteOrder.LITTLE_ENDIAN) ? value : Float.intBitsToFloat(Integer.reverseBytes(Float.floatToRawIntBits(value)));
        store_f32(null, (int) byteOffset, actualValue);
    }

    @ExportMessage
    final void writeBufferDouble(ByteOrder order, long byteOffset, double value,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Double.BYTES, errorBranch);
        double actualValue = (order == ByteOrder.LITTLE_ENDIAN) ? value : Double.longBitsToDouble(Long.reverseBytes(Double.doubleToRawLongBits(value)));
        store_f64(null, (int) byteOffset, actualValue);
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
    public Object readArrayElement(long address,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws InvalidArrayIndexException {
        if (!isArrayElementReadable(address)) {
            errorBranch.enter();
            throw InvalidArrayIndexException.create(address);
        }
        return load_i32_8u(null, (int) address);
    }

    @ExportMessage
    public void writeArrayElement(long address, Object value,
                    @CachedLibrary(limit = "3") InteropLibrary valueLib,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch)
                    throws InvalidArrayIndexException, UnsupportedMessageException, UnsupportedTypeException {
        if (!isArrayElementModifiable(address)) {
            errorBranch.enter();
            throw InvalidArrayIndexException.create(address);
        }
        byte rawValue;
        if (valueLib.fitsInByte(value)) {
            rawValue = valueLib.asByte(value);
        } else {
            errorBranch.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, "Only bytes can be stored into WebAssembly memory.");
        }
        store_i32_8(null, (int) address, rawValue);
    }

    public void setGrowCallback(Object growCallback) {
        this.growCallback = growCallback;
    }

    public Object getGrowCallback() {
        return growCallback;
    }

    protected void invokeGrowCallback() {
        WebAssembly.invokeMemGrowCallback(this);
    }

    public abstract void close();

    public abstract ByteBuffer asByteBuffer();
}
