/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
 * @since 1.0
 */
public interface ProxyArray extends Proxy {

    /**
     * Returns the element at the given index.
     *
     * @throws ArrayIndexOutOfBoundsException if the index is out of bounds
     * @throws UnsupportedOperationException if the operation is not supported
     * @since 1.0
     */
    Object get(long index);

    /**
     * Sets the element at the given index.
     *
     * @throws ArrayIndexOutOfBoundsException if the index is out of bounds
     * @throws UnsupportedOperationException if the operation is not supported
     * @since 1.0
     */
    void set(long index, Value value);

    /**
     * Removes the element at the given index.
     *
     * @return <code>true</code> when the element was removed, <code>false</code> when the element
     *         didn't exist.
     * @throws ArrayIndexOutOfBoundsException if the index is out of bounds
     * @throws UnsupportedOperationException if the operation is not supported
     * @since 1.0
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
     * @since 1.0
     */
    long getSize();

    /**
     * Creates a proxy array backed by a Java array. If the set values of the array are host values
     * then the they will be {@link Value#asHostObject() unboxed}.
     *
     * @since 1.0
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
     * @since 1.0
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
