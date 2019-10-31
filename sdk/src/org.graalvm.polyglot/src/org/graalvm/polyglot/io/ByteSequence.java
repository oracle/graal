/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * A <tt>ByteSequence</tt> is a readable sequence of <code>byte</code> values. This interface
 * provides uniform, read-only access to many different kinds of <code>byte</code> sequences.
 * <p>
 * This interface does not refine the general contracts of the
 * {@link java.lang.Object#equals(java.lang.Object) equals} and {@link java.lang.Object#hashCode()
 * hashCode} methods. The result of comparing two objects that implement <tt>ByteSequence</tt> is
 * therefore, in general, undefined. Each object may be implemented by a different class, and there
 * is no guarantee that each class will be capable of testing its instances for equality with those
 * of the other. It is therefore inappropriate to use arbitrary <tt>ByteSequence</tt> instances as
 * elements in a set or as keys in a map.
 * </p>
 *
 * @since 19.0
 */
public interface ByteSequence {

    /**
     * Returns the length of this byte sequence.
     *
     * @return the number of <code>byte</code>s in this sequence
     * @since 19.0
     */
    int length();

    /**
     * Returns the <code>byte</code> value at the specified index. An index ranges from zero to
     * <tt>length() - 1</tt>. The first <code>char</code> value of the sequence is at index zero,
     * the next at index one, and so on, as for array indexing.
     *
     * @param index the index of the <code>byte</code> value to be returned
     * @return the specified <code>byte</code> value
     * @throws IndexOutOfBoundsException if the <tt>index</tt> argument is negative or not less than
     *             <tt>length()</tt>
     * @since 19.0
     */
    byte byteAt(int index);

    /**
     * Returns a <code>ByteSequence</code> that is a subsequence of this sequence. The subsequence
     * starts with the <code>byte</code> value at the specified index and ends with the
     * <code>byte</code> value at index <tt>end - 1</tt>. The length (in <code>byte</code>s) of the
     * returned sequence is <tt>end - start</tt>, so if <tt>start == end</tt> then an empty sequence
     * is returned.
     *
     * @param startIndex the start index, inclusive
     * @param endIndex the end index, exclusive
     * @return the specified subsequence
     * @throws IndexOutOfBoundsException if <tt>start</tt> or <tt>end</tt> are negative, if
     *             <tt>end</tt> is greater than <tt>length()</tt>, or if <tt>start</tt> is greater
     *             than <tt>end</tt>
     * @since 19.0
     */
    default ByteSequence subSequence(int startIndex, int endIndex) {
        int l = endIndex - startIndex;
        if (l < 0) {
            throw new IndexOutOfBoundsException(String.valueOf(l));
        }
        if (startIndex < 0) {
            throw new IndexOutOfBoundsException(String.valueOf(startIndex));
        }
        if (startIndex + l > length()) {
            throw new IndexOutOfBoundsException(String.valueOf(startIndex + l));
        }
        return new ByteSequence() {
            public int length() {
                return l;
            }

            public byte byteAt(int index) {
                return ByteSequence.this.byteAt(startIndex + index);
            }
        };
    }

    /**
     * Returns a <code>byte[]</code> containing the bytes in this sequence in the same order as this
     * sequence. The length of the byte array will be the length of this sequence. Creates a new
     * byte array with every invocation.
     *
     * @since 19.0
     */
    default byte[] toByteArray() {
        byte[] b = new byte[length()];
        for (int i = 0; i < b.length; i++) {
            b[i] = byteAt(i);
        }
        return b;
    }

    /**
     * Returns a stream of {@code int} zero-extending the {@code byte} values from this sequence.
     *
     * @return an IntStream of byte values from this sequence
     * @since 19.0
     */
    default IntStream bytes() {
        class ByteIterator implements PrimitiveIterator.OfInt {
            int cur = 0;

            public boolean hasNext() {
                return cur < length();
            }

            public int nextInt() {
                if (hasNext()) {
                    return byteAt(cur++) & 0xFF;
                } else {
                    throw new NoSuchElementException();
                }
            }

            @Override
            public void forEachRemaining(IntConsumer block) {
                for (; cur < length(); cur++) {
                    block.accept(byteAt(cur) & 0xFF);
                }
            }
        }
        return StreamSupport.intStream(() -> Spliterators.spliterator(
                        new ByteIterator(),
                        length(),
                        Spliterator.ORDERED),
                        Spliterator.SUBSIZED | Spliterator.SIZED | Spliterator.ORDERED,
                        false);
    }

    /**
     * Creates a <code>ByteSequence</code> from an existing <code>byte[]</code>. The byte array is
     * not defensively copied, therefore the given bytes must not mutate to ensure the contract of
     * an immutable ByteSequence.
     *
     * @since 19.0
     */
    static ByteSequence create(byte[] buffer) {
        return new ByteArraySequence(buffer, 0, buffer.length);
    }

}
