/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.strings;

/**
 * Wrapper object containing a {@link TruffleString}'s internal byte array, along with a byte offset
 * and length defining the region in use.
 *
 * @since 22.1
 */
public final class InternalByteArray {

    static final InternalByteArray EMPTY = new InternalByteArray(TruffleString.Encoding.EMPTY_BYTES, 0, 0);

    private final byte[] array;
    private final int offset;
    private final int length;

    InternalByteArray(byte[] array, int offset, int length) {
        this.array = array;
        this.offset = offset;
        this.length = length;
    }

    /**
     * Get the internal byte array. Do not modify the array's contents!
     *
     * @since 22.1
     */
    public byte[] getArray() {
        return array;
    }

    /**
     * Get the string region's starting index.
     *
     * @since 22.1
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Get the string region's length.
     *
     * @since 22.1
     */
    public int getLength() {
        return length;
    }

    /**
     * Get the string region's end ({@code offset + length}).
     *
     * @since 22.1
     */
    public int getEnd() {
        return offset + length;
    }

    /**
     * Read the byte at {@code getArray()[getOffset() + index]} and return it as a byte, similar to
     * {@link TruffleString.ReadByteNode}. Consider using {@link TruffleString.ReadByteNode} (and
     * {@link TruffleString.MaterializeNode} before) instead if not needing the byte[] for other
     * purposes, as that will avoid extra copying if the string {@link TruffleString#isNative() is
     * stored in native memory}.
     *
     * @since 22.2
     */
    public byte get(int index) {
        return array[offset + index];
    }
}
