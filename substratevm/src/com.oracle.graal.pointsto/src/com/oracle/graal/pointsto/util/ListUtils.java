/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.util;

import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;

import java.util.Arrays;

public class ListUtils {

    public static final class UnsafeArrayListClosable<E> implements AutoCloseable {
        private UnsafeArrayList<E> list;
        private boolean closed = true;

        private UnsafeArrayListClosable(UnsafeArrayList<E> list) {
            this.list = list;
        }

        public UnsafeArrayList<E> list() {
            return list;
        }

        @Override
        public void close() {
            list.clear();
            closed = true;
        }
    }

    public static UnsafeArrayListClosable<AnalysisObject> getTLArrayList(ThreadLocal<UnsafeArrayListClosable<AnalysisObject>> tl, int initialCapacity) {
        UnsafeArrayListClosable<AnalysisObject> result = tl.get();
        if (result == null) {
            result = new UnsafeArrayListClosable<>(new UnsafeArrayList<>(new AnalysisObject[initialCapacity]));
            tl.set(result);
        }
        if (result.closed) {
            result.closed = false;
            return result;
        } else {
            /*
             * Happens very rarely that the same operation is done recursively. If this happens more
             * often we should introduce a stack of arrays.
             */
            return new UnsafeArrayListClosable<>(new UnsafeArrayList<>(new AnalysisObject[initialCapacity]));
        }
    }

    public static class UnsafeArrayList<E> {

        static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
        E[] elementData;
        int size;

        UnsafeArrayList(E[] initial) {
            elementData = initial;
        }

        public <T> T[] copyToArray(T[] a) {
            System.arraycopy(elementData, 0, a, 0, size);
            return a;
        }

        public <T> T[] copyToArray(T[] a, int dstPos) {
            System.arraycopy(elementData, 0, a, dstPos, size);
            return a;
        }

        public <E1 extends E> void addAll(E1[] c, int startIndex, int endIndex) {
            assert startIndex <= endIndex : "start index can't be smaller than the end index.";
            int newElements = endIndex - startIndex;
            ensureCapacity(size() + newElements);
            System.arraycopy(c, startIndex, elementData, size, newElements);
            size += newElements;
        }

        public int size() {
            return size;
        }

        public E[] elementData() {
            return elementData;
        }

        public void add(E e) {
            ensureCapacity(size + 1);
            elementData[size] = e;
            size = size + 1;
        }

        public void clear() {
            for (int i = 0; i < size; i++) {
                elementData[i] = null;
            }

            size = 0;
        }

        public E get(int i) {
            assert i < size && i >= 0;
            return elementData[i];
        }

        private void ensureCapacity(int minCapacity) {
            if (minCapacity - elementData.length > 0) {
                grow(minCapacity);
            }
        }

        private void grow(int minCapacity) {
            int oldCapacity = elementData.length;
            int newCapacity = oldCapacity + (oldCapacity >> 1);
            if (newCapacity - minCapacity < 0) {
                newCapacity = minCapacity;
            }
            if (newCapacity - MAX_ARRAY_SIZE > 0) {
                if (minCapacity < 0) {
                    throw new OutOfMemoryError();
                }
                newCapacity = (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
            }
            elementData = Arrays.copyOf(elementData, newCapacity);
        }

    }

}
