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
package org.graalvm.wasm.collection;

import java.util.Arrays;

public final class ByteArrayList {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private byte[] array;
    private int offset;

    public ByteArrayList() {
        this.array = null;
        this.offset = 0;
    }

    public void add(byte b) {
        ensureSize();
        array[offset] = b;
        offset++;
    }

    public byte popBack() {
        offset--;
        return array[offset];
    }

    public void set(int index, byte b) {
        if (index >= size()) {
            throw new IndexOutOfBoundsException("Index " + index + " greater or equal to list size" + size() + ".");
        }
        array[index] = b;
    }

    public byte get(int index) {
        if (index >= size()) {
            throw new IndexOutOfBoundsException("Index " + index + " greater or equal to list size" + size() + ".");
        }
        return array[index];
    }

    public int size() {
        return offset;
    }

    private void ensureSize() {
        if (array == null) {
            array = new byte[4];
        } else if (offset == array.length) {
            byte[] narray = new byte[array.length * 2];
            System.arraycopy(array, 0, narray, 0, offset);
            array = narray;
        }
    }

    public byte[] toArray() {
        byte[] result = new byte[offset];
        if (array != null) {
            System.arraycopy(array, 0, result, 0, offset);
            return result;
        } else {
            return EMPTY_BYTE_ARRAY;
        }
    }

    public static byte[] concat(ByteArrayList... byteArrayLists) {
        int totalSize = Arrays.stream(byteArrayLists).mapToInt(ByteArrayList::size).sum();
        byte[] result = new byte[totalSize];
        int resultOffset = 0;
        for (ByteArrayList byteArrayList : byteArrayLists) {
            if (byteArrayList.array != null) {
                System.arraycopy(byteArrayList.array, 0, result, resultOffset, byteArrayList.offset);
                resultOffset += byteArrayList.offset;
            }
        }
        return result;
    }
}
