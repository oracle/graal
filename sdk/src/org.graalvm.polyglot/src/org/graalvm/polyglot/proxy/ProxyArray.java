/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.proxy;

import java.util.List;

import org.graalvm.polyglot.Value;

/**
 * Interface to be implemented to mimic guest language arrays. Arrays are always interpreted as
 * zero-based arrays, independent of whether the Graal language uses one-based arrays. For example
 * an access to array index one in a language with one-based arrays will access the proxy array at
 * index zero.
 *
 * @see Proxy
 * @since 19.0
 */
public interface ProxyArray extends Proxy {

    /**
     * Returns the element at the given index.
     *
     * @throws ArrayIndexOutOfBoundsException if the index is out of bounds
     * @throws UnsupportedOperationException if the operation is not supported
     * @since 19.0
     */
    Object get(long index);

    /**
     * Sets the element at the given index.
     *
     * @throws ArrayIndexOutOfBoundsException if the index is out of bounds
     * @throws UnsupportedOperationException if the operation is not supported
     * @since 19.0
     */
    void set(long index, Value value);

    /**
     * Removes the element at the given index.
     *
     * @return <code>true</code> when the element was removed, <code>false</code> when the element
     *         didn't exist.
     * @throws ArrayIndexOutOfBoundsException if the index is out of bounds
     * @throws UnsupportedOperationException if the operation is not supported
     * @since 19.0
     */
    @SuppressWarnings("unused")
    default boolean remove(long index) {
        throw new UnsupportedOperationException("remove() not supported.");
    }

    /**
     * Returns the reported size of the array. The returned size of an array does not limit a guest
     * language to get and set values using arbitrary indices. The array size is typically used by
     * Graal languages to traverse the array.
     *
     * @since 19.0
     */
    long getSize();

    /**
     * Creates a proxy array backed by a Java array. If the set values of the array are host values
     * then the they will be {@link Value#asHostObject() unboxed}.
     *
     * @since 19.0
     */
    static ProxyArray fromArray(Object... values) {
        return new ProxyArray() {
            public Object get(long index) {
                checkIndex(index);
                return values[(int) index];
            }

            public void set(long index, Value value) {
                checkIndex(index);
                values[(int) index] = value.isHostObject() ? value.asHostObject() : value;
            }

            private void checkIndex(long index) {
                if (index > Integer.MAX_VALUE || index < 0) {
                    throw new ArrayIndexOutOfBoundsException("invalid index.");
                }
            }

            public long getSize() {
                return values.length;
            }
        };
    }

    /**
     * Creates a proxy array backed by a Java List. If the set values of the list are host values
     * then the they will be {@link Value#asHostObject() unboxed}.
     *
     * @since 19.0
     */
    static ProxyArray fromList(List<Object> values) {
        return new ProxyArray() {

            @Override
            public Object get(long index) {
                checkIndex(index);
                return values.get((int) index);
            }

            @Override
            public void set(long index, Value value) {
                checkIndex(index);
                values.set((int) index, value.isHostObject() ? value.asHostObject() : value);
            }

            @Override
            public boolean remove(long index) {
                checkIndex(index);
                values.remove((int) index);
                return true;
            }

            private void checkIndex(long index) {
                if (index > Integer.MAX_VALUE || index < 0) {
                    throw new ArrayIndexOutOfBoundsException("invalid index.");
                }
            }

            public long getSize() {
                return values.size();
            }

        };
    }

}
