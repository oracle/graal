/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.wasm.constants.Sizes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

@GenerateLibrary(dynamicDispatchEnabled = false, pushEncapsulatingNode = false)
public abstract class WasmMemoryLibrary extends Library {

    private static final LibraryFactory<WasmMemoryLibrary> FACTORY = LibraryFactory.resolve(WasmMemoryLibrary.class);

    public static LibraryFactory<WasmMemoryLibrary> getFactory() {
        return FACTORY;
    }

    public static WasmMemoryLibrary getUncached() {
        return FACTORY.getUncached();
    }

    /**
     * The current size of this memory instance (measured in number of {@link Sizes#MEMORY_PAGE_SIZE
     * pages}).
     */
    public abstract long size(WasmMemory memory);

    /**
     * The current size of this memory instance (measured in bytes).
     */
    public abstract long byteSize(WasmMemory memory);

    /**
     * Increases the size of the memory by the specified number of pages.
     *
     * @return The previous size of the memory if successful, otherwise {@code -1}.
     */
    public abstract long grow(WasmMemory memory, long extraPageSize);

    /**
     * Initializes the content of a byte array based memory with the given data instance.
     *
     * @param node the node used for errors
     * @param source The source data instance that should be copied to the memory
     * @param sourceOffset The offset in the source data segment
     * @param destinationOffset The offset in the memory
     * @param length The number of bytes that should be copied
     */
    public abstract void initialize(WasmMemory memory, Node node, byte[] source, int sourceOffset, long destinationOffset, int length);

    /**
     * Fills the memory with the given value.
     *
     * @param node the node used for errors
     * @param offset The offset in the memory
     * @param length The number of bytes that should be filled
     * @param value The value that should be used for filling the memory
     */
    public abstract void fill(WasmMemory memory, Node node, long offset, long length, byte value);

    /**
     * Copies data from another memory into this memory.
     *
     * @param node the node used for errors
     * @param source The source memory
     * @param sourceOffset The offset in the source memory
     * @param destinationOffset The offset in this memory
     * @param length The number of bytes that should be copied
     */
    public abstract void copyFrom(WasmMemory memory, Node node, WasmMemory source, long sourceOffset, long destinationOffset, long length);

    /**
     * Copy data from an input stream into memory.
     *
     * @param node the node used for errors
     * @param stream the input stream
     * @param offset the offset in the memory
     * @param length the length of the data
     * @return the number of read bytes, -1 if the end of the stream has been reached.
     * @throws IOException if reading the stream leads to an error.
     */
    public abstract int copyFromStream(WasmMemory memory, Node node, InputStream stream, int offset, int length) throws IOException;

    /**
     * Copy data from memory into an output stream.
     *
     * @param node the node used for errors
     * @param stream the output stream
     * @param offset the offset in the memory
     * @param length the length of the data
     * @throws IOException if writing the stream leads to an error.
     */
    public abstract void copyToStream(WasmMemory memory, Node node, OutputStream stream, int offset, int length) throws IOException;

    /**
     * Copy data from memory into a byte[] array.
     *
     * @param node the node used for errors
     * @param dst the output buffer
     * @param srcOffset the offset in the memory
     * @param dstOffset the offset in the byte[] array
     * @param length the length of the data
     */
    public abstract void copyToBuffer(WasmMemory memory, Node node, byte[] dst, long srcOffset, int dstOffset, int length);

    // Checkstyle: stop
    public abstract int load_i32(WasmMemory memory, Node node, long address);

    public abstract long load_i64(WasmMemory memory, Node node, long address);

    public abstract float load_f32(WasmMemory memory, Node node, long address);

    public abstract double load_f64(WasmMemory memory, Node node, long address);

    public abstract int load_i32_8s(WasmMemory memory, Node node, long address);

    public abstract int load_i32_8u(WasmMemory memory, Node node, long address);

    public abstract int load_i32_16s(WasmMemory memory, Node node, long address);

    public abstract int load_i32_16u(WasmMemory memory, Node node, long address);

    public abstract long load_i64_8s(WasmMemory memory, Node node, long address);

    public abstract long load_i64_8u(WasmMemory memory, Node node, long address);

    public abstract long load_i64_16s(WasmMemory memory, Node node, long address);

    public abstract long load_i64_16u(WasmMemory memory, Node node, long address);

    public abstract long load_i64_32s(WasmMemory memory, Node node, long address);

    public abstract long load_i64_32u(WasmMemory memory, Node node, long address);

    public abstract Object load_i128(WasmMemory memory, Node node, long address);

    public abstract void store_i32(WasmMemory memory, Node node, long address, int value);

    public abstract void store_i64(WasmMemory memory, Node node, long address, long value);

    public abstract void store_f32(WasmMemory memory, Node node, long address, float value);

    public abstract void store_f64(WasmMemory memory, Node node, long address, double value);

    public abstract void store_i32_8(WasmMemory memory, Node node, long address, byte value);

    public abstract void store_i32_16(WasmMemory memory, Node node, long address, short value);

    public abstract void store_i64_8(WasmMemory memory, Node node, long address, byte value);

    public abstract void store_i64_16(WasmMemory memory, Node node, long address, short value);

    public abstract void store_i64_32(WasmMemory memory, Node node, long address, int value);

    public abstract void store_i128(WasmMemory memory, Node node, long address, Object value);

    public abstract int atomic_load_i32(WasmMemory memory, Node node, long address);

    public abstract long atomic_load_i64(WasmMemory memory, Node node, long address);

    public abstract int atomic_load_i32_8u(WasmMemory memory, Node node, long address);

    public abstract int atomic_load_i32_16u(WasmMemory memory, Node node, long address);

    public abstract long atomic_load_i64_8u(WasmMemory memory, Node node, long address);

    public abstract long atomic_load_i64_16u(WasmMemory memory, Node node, long address);

    public abstract long atomic_load_i64_32u(WasmMemory memory, Node node, long address);

    public abstract void atomic_store_i32(WasmMemory memory, Node node, long address, int value);

    public abstract void atomic_store_i64(WasmMemory memory, Node node, long address, long value);

    public abstract void atomic_store_i32_8(WasmMemory memory, Node node, long address, byte value);

    public abstract void atomic_store_i32_16(WasmMemory memory, Node node, long address, short value);

    public abstract void atomic_store_i64_8(WasmMemory memory, Node node, long address, byte value);

    public abstract void atomic_store_i64_16(WasmMemory memory, Node node, long address, short value);

    public abstract void atomic_store_i64_32(WasmMemory memory, Node node, long address, int value);

    public abstract int atomic_rmw_add_i32_8u(WasmMemory memory, Node node, long address, byte value);

    public abstract int atomic_rmw_add_i32_16u(WasmMemory memory, Node node, long address, short value);

    public abstract int atomic_rmw_add_i32(WasmMemory memory, Node node, long address, int value);

    public abstract long atomic_rmw_add_i64_8u(WasmMemory memory, Node node, long address, byte value);

    public abstract long atomic_rmw_add_i64_16u(WasmMemory memory, Node node, long address, short value);

    public abstract long atomic_rmw_add_i64_32u(WasmMemory memory, Node node, long address, int value);

    public abstract long atomic_rmw_add_i64(WasmMemory memory, Node node, long address, long value);

    public abstract int atomic_rmw_sub_i32_8u(WasmMemory memory, Node node, long address, byte value);

    public abstract int atomic_rmw_sub_i32_16u(WasmMemory memory, Node node, long address, short value);

    public abstract int atomic_rmw_sub_i32(WasmMemory memory, Node node, long address, int value);

    public abstract long atomic_rmw_sub_i64_8u(WasmMemory memory, Node node, long address, byte value);

    public abstract long atomic_rmw_sub_i64_16u(WasmMemory memory, Node node, long address, short value);

    public abstract long atomic_rmw_sub_i64_32u(WasmMemory memory, Node node, long address, int value);

    public abstract long atomic_rmw_sub_i64(WasmMemory memory, Node node, long address, long value);

    public abstract int atomic_rmw_and_i32_8u(WasmMemory memory, Node node, long address, byte value);

    public abstract int atomic_rmw_and_i32_16u(WasmMemory memory, Node node, long address, short value);

    public abstract int atomic_rmw_and_i32(WasmMemory memory, Node node, long address, int value);

    public abstract long atomic_rmw_and_i64_8u(WasmMemory memory, Node node, long address, byte value);

    public abstract long atomic_rmw_and_i64_16u(WasmMemory memory, Node node, long address, short value);

    public abstract long atomic_rmw_and_i64_32u(WasmMemory memory, Node node, long address, int value);

    public abstract long atomic_rmw_and_i64(WasmMemory memory, Node node, long address, long value);

    public abstract int atomic_rmw_or_i32_8u(WasmMemory memory, Node node, long address, byte value);

    public abstract int atomic_rmw_or_i32_16u(WasmMemory memory, Node node, long address, short value);

    public abstract int atomic_rmw_or_i32(WasmMemory memory, Node node, long address, int value);

    public abstract long atomic_rmw_or_i64_8u(WasmMemory memory, Node node, long address, byte value);

    public abstract long atomic_rmw_or_i64_16u(WasmMemory memory, Node node, long address, short value);

    public abstract long atomic_rmw_or_i64_32u(WasmMemory memory, Node node, long address, int value);

    public abstract long atomic_rmw_or_i64(WasmMemory memory, Node node, long address, long value);

    public abstract int atomic_rmw_xor_i32_8u(WasmMemory memory, Node node, long address, byte value);

    public abstract int atomic_rmw_xor_i32_16u(WasmMemory memory, Node node, long address, short value);

    public abstract int atomic_rmw_xor_i32(WasmMemory memory, Node node, long address, int value);

    public abstract long atomic_rmw_xor_i64_8u(WasmMemory memory, Node node, long address, byte value);

    public abstract long atomic_rmw_xor_i64_16u(WasmMemory memory, Node node, long address, short value);

    public abstract long atomic_rmw_xor_i64_32u(WasmMemory memory, Node node, long address, int value);

    public abstract long atomic_rmw_xor_i64(WasmMemory memory, Node node, long address, long value);

    public abstract int atomic_rmw_xchg_i32_8u(WasmMemory memory, Node node, long address, byte value);

    public abstract int atomic_rmw_xchg_i32_16u(WasmMemory memory, Node node, long address, short value);

    public abstract int atomic_rmw_xchg_i32(WasmMemory memory, Node node, long address, int value);

    public abstract long atomic_rmw_xchg_i64_8u(WasmMemory memory, Node node, long address, byte value);

    public abstract long atomic_rmw_xchg_i64_16u(WasmMemory memory, Node node, long address, short value);

    public abstract long atomic_rmw_xchg_i64_32u(WasmMemory memory, Node node, long address, int value);

    public abstract long atomic_rmw_xchg_i64(WasmMemory memory, Node node, long address, long value);

    public abstract int atomic_rmw_cmpxchg_i32_8u(WasmMemory memory, Node node, long address, byte expected, byte replacement);

    public abstract int atomic_rmw_cmpxchg_i32_16u(WasmMemory memory, Node node, long address, short expected, short replacement);

    public abstract int atomic_rmw_cmpxchg_i32(WasmMemory memory, Node node, long address, int expected, int replacement);

    public abstract long atomic_rmw_cmpxchg_i64_8u(WasmMemory memory, Node node, long address, byte expected, byte replacement);

    public abstract long atomic_rmw_cmpxchg_i64_16u(WasmMemory memory, Node node, long address, short expected, short replacement);

    public abstract long atomic_rmw_cmpxchg_i64_32u(WasmMemory memory, Node node, long address, int expected, int replacement);

    public abstract long atomic_rmw_cmpxchg_i64(WasmMemory memory, Node node, long address, long expected, long replacement);

    public abstract int atomic_notify(WasmMemory memory, Node node, long address, int count);

    public abstract int atomic_wait32(WasmMemory memory, Node node, long address, int expected, long timeout);

    public abstract int atomic_wait64(WasmMemory memory, Node node, long address, long expected, long timeout);
    // Checkstyle: resume

    public abstract void close(WasmMemory memory);

    /**
     * Shrinks this memory's size to its {@link WasmMemory#declaredMinSize()} initial size}, and
     * sets all bytes to 0.
     * <p>
     * Note: this does not restore content from data section. For this, use
     * {@link org.graalvm.wasm.parser.bytecode.BytecodeParser#resetMemoryState}.
     */
    public abstract void reset(WasmMemory memory);

    @SuppressWarnings("unused")
    public ByteBuffer asByteBuffer(WasmMemory memory) {
        return null;
    }

    public abstract WasmMemory duplicate(WasmMemory memory);

    public abstract boolean freed(WasmMemory memory);
}
