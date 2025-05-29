/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.io;

import java.util.Arrays;

/**
 * Implementation created in {@link ByteSequence#create(byte[])}.
 */
final class ByteArraySequence implements ByteSequence {

    private final byte[] buffer;
    private final int start;
    private final int length;

    /** Cache the hash code for the byte array sequences. */
    private int hash; // Default to 0

    ByteArraySequence(byte[] buffer, int start, int length) {
        assert buffer.length >= start + length;
        assert start >= 0;
        assert length >= 0;
        this.buffer = buffer;
        this.start = start;
        this.length = length;
    }

    public int length() {
        return length;
    }

    public byte byteAt(int index) {
        int resolvedIndex = start + index;
        if (resolvedIndex >= start + length) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
        return buffer[resolvedIndex];
    }

    public byte[] toByteArray() {
        return Arrays.copyOfRange(buffer, start, start + length);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof ByteArraySequence other) {
            if (buffer == other.buffer) {
                return start == other.start && length == other.length;
            }
            if (length != other.length) {
                return false;
            }
            int thisHash = this.hash;
            int otherHash = other.hash;
            if (thisHash != 0 && otherHash != 0 && thisHash != otherHash) {
                // hash was already computed and hash is not equal
                return false;
            }
            return Arrays.equals(
                            this.buffer, this.start, this.start + this.length,
                            other.buffer, other.start, other.start + other.length);
        } else if (obj instanceof ByteSequence other) {
            if (length != other.length()) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                if (buffer[start + i] != other.byteAt(i)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0 && length > 0) {
            if (start == 0 && length == buffer.length) {
                h = Arrays.hashCode(buffer);
            } else {
                h = hashCode(buffer, start, length, 1);
            }
            hash = h;
        }
        return h;
    }

    /**
     * Calculates the hash code for a subrange of a byte array in the same way as
     * {@code jdk.internal.util.ArraysSupport.hashCode(byte[], int, int, int)}.
     *
     * @see Arrays#hashCode(byte[])
     */
    private static int hashCode(byte[] a, int fromIndex, int length, int initialValue) {
        int result = initialValue;
        int end = fromIndex + length;
        for (int i = fromIndex; i < end; i++) {
            result = 31 * result + a[i];
        }
        return result;
    }

    public ByteSequence subSequence(int startIndex, int endIndex) {
        if (startIndex < 0) {
            throw new IndexOutOfBoundsException(String.valueOf(startIndex));
        }
        if (endIndex < startIndex) {
            throw new IndexOutOfBoundsException(String.valueOf((long) endIndex - startIndex));
        }
        if (endIndex > length()) {
            throw new IndexOutOfBoundsException(String.valueOf(endIndex));
        }
        return new ByteArraySequence(buffer, start + startIndex, endIndex - startIndex);
    }

}
