/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.regex.tregex.buffer;

import java.util.Arrays;
import java.util.Iterator;

/**
 * This class is designed as a "scratchpad" for generating many Object arrays of unknown size. It
 * will never shrink its internal buffer, so it should be disposed as soon as it is no longer
 * needed.
 * <p>
 * Usage Example:
 * </p>
 * 
 * <pre>
 * SomeClass[] typedArray = new SomeClass[0];
 * ObjectArrayBuffer buf = new ObjectArrayBuffer();
 * List<SomeClass[]> results = new ArrayList<>();
 * for (Object obj : listOfThingsToProcess) {
 *     for (Object x : obj.thingsThatShouldBecomeSomeClass()) {
 *         buf.add(someCalculation(x));
 *     }
 *     results.add(buf.toArray(typedArray));
 *     buf.clear();
 * }
 * </pre>
 */
public final class ObjectArrayBuffer extends AbstractArrayBuffer implements Iterable<Object> {

    private Object[] buf;

    public ObjectArrayBuffer() {
        this(16);
    }

    public ObjectArrayBuffer(int initialSize) {
        buf = new Object[initialSize];
    }

    @Override
    int getBufferLength() {
        return buf.length;
    }

    @Override
    void grow(int newSize) {
        buf = Arrays.copyOf(buf, newSize);
    }

    public Object get(int i) {
        return buf[i];
    }

    public void add(Object o) {
        if (length == buf.length) {
            grow(length * 2);
        }
        buf[length] = o;
        length++;
    }

    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        if (a.length < length) {
            return (T[]) Arrays.copyOf(buf, length, a.getClass());
        }
        System.arraycopy(buf, 0, a, 0, length);
        return a;
    }

    @Override
    public Iterator<Object> iterator() {
        return new ObjectBufferIterator(buf, length);
    }

    private static final class ObjectBufferIterator implements Iterator<Object> {

        private final Object[] buf;
        private final int size;
        private int i = 0;

        private ObjectBufferIterator(Object[] buf, int size) {
            this.buf = buf;
            this.size = size;
        }

        @Override
        public boolean hasNext() {
            return i < size;
        }

        @Override
        public Object next() {
            return buf[i++];
        }
    }
}
