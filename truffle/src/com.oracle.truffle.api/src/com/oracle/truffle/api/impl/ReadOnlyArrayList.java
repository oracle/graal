/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class ReadOnlyArrayList<T> implements List<T> {
    private final T[] arr;
    private final int first;
    private final int last;

    private ReadOnlyArrayList(T[] arr, int first, int last) {
        this.arr = arr;
        this.first = first;
        this.last = last;
        if (first > last) {
            throw new IllegalArgumentException();
        }
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
        if (at < first || at >= last) {
            throw new ArrayIndexOutOfBoundsException();
        }
        T ret = arr[at];
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

    @TruffleBoundary
    @Override
    public String toString() {
        Iterator<T> it = iterator();
        if (!it.hasNext()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (;;) {
            T e = it.next();
            sb.append(e == this ? "(this Collection)" : e);
            if (!it.hasNext()) {
                return sb.append(']').toString();
            }
            sb.append(',').append(' ');
        }
    }

    private final class LI implements ListIterator<T>, Iterator<T> {
        private int index;

        LI(int index) {
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
