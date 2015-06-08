/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.impl;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public final class ReadOnlyArrayList<T> implements List<T> {
    private final T[] arr;
    private final int first;
    private final int last;

    private ReadOnlyArrayList(T[] arr, int first, int last) {
        this.arr = arr;
        this.first = first;
        this.last = last;
    }

    public static <T> List<T> asList(T[] arr, int first, int last) {
        return new ReadOnlyArrayList<>(arr, first, last);
    }

    @Override
    public int size() {
        return last - first;
    }

    @Override
    public boolean isEmpty() {
        return first == last;
    }

    @Override
    public boolean contains(Object o) {
        for (int i = first; i < last; i++) {
            if (o == arr[i] || (o != null && o.equals(arr[i]))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return new LI(first);
    }

    @Override
    public Object[] toArray() {
        return toArray(new Object[size()]);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> A[] toArray(A[] b) {
        A[] a;
        if (b.length < size()) {
            a = (A[]) Array.newInstance(b.getClass().getComponentType(), size());
        } else {
            a = b;
        }
        for (int i = 0, at = first; at < last; i++, at++) {
            a[i] = (A) arr[at];
        }
        return a;
    }

    @Override
    public boolean add(Object e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object obj : c) {
            if (!contains(obj)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T get(int index) {
        int at = first + index;
        T ret = arr[at];
        if (at >= last) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return ret;
    }

    @Override
    public T set(int index, Object element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, Object element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        for (int i = first; i < last; i++) {
            if (arr[i] == null) {
                if (o == null) {
                    return i - first;
                }
            } else {
                if (arr[i].equals(o)) {
                    return i - first;
                }
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        for (int i = last - 1; i >= first; i--) {
            if (arr[i] == null) {
                if (o == null) {
                    return i - first;
                }
            } else {
                if (arr[i].equals(o)) {
                    return i - first;
                }
            }
        }
        return -1;
    }

    @Override
    public ListIterator<T> listIterator() {
        return new LI(first);
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return new LI(first + index);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return new ReadOnlyArrayList<>(arr, first + fromIndex, first + toIndex);
    }

    private final class LI implements ListIterator<T>, Iterator<T> {
        private int index;

        public LI(int index) {
            this.index = index;
        }

        @Override
        public boolean hasNext() {
            return index < last;
        }

        @Override
        public T next() {
            if (index >= last) {
                throw new NoSuchElementException();
            }
            return arr[index++];
        }

        @Override
        public boolean hasPrevious() {
            return index > first;
        }

        @Override
        public T previous() {
            if (first == index) {
                throw new NoSuchElementException();
            }
            return arr[--index];
        }

        @Override
        public int nextIndex() {
            return index - first;
        }

        @Override
        public int previousIndex() {
            return index - 1 - first;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(Object e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(Object e) {
            throw new UnsupportedOperationException();
        }

    }
}
