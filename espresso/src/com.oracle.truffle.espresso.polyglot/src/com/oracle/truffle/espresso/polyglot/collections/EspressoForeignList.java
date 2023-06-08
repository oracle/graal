/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.polyglot.collections;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.oracle.truffle.espresso.polyglot.Interop;
import com.oracle.truffle.espresso.polyglot.InteropException;
import com.oracle.truffle.espresso.polyglot.UnsupportedMessageException;

public class EspressoForeignList<T> extends AbstractList<T> implements List<T> {

    protected Object foreignObject;

    @Override
    public int size() {
        try {
            return (int) Interop.getArraySize(foreignObject);
        } catch (UnsupportedMessageException e) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean add(T t) {
        int size = size();
        if (Interop.isArrayElementWritable(foreignObject, size)) {
            try {
                Interop.writeArrayElement(foreignObject, size, t);
                modCount++;
                return true;
            } catch (InteropException e) {
                throw new UnsupportedOperationException();
            }
        }
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(int index) {
        Objects.checkIndex(index, size());
        try {
            if (Interop.isArrayElementReadable(foreignObject, index)) {
                return (T) Interop.readArrayElement(foreignObject, index);
            } else {
                throw new UnsupportedOperationException();
            }
        } catch (InteropException e) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public T set(int index, T element) {
        Objects.checkIndex(index, size() + 1);
        if (Interop.isArrayElementWritable(foreignObject, index)) {
            try {
                T previous = get(index);
                Interop.writeArrayElement(foreignObject, index, element);
                modCount++;
                return previous;
            } catch (InteropException e) {
                throw new UnsupportedOperationException();
            }
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, T element) {
        Objects.checkIndex(index, size() + 1);
        try {
            long size = Interop.getArraySize(foreignObject);
            if (Interop.isArrayElementWritable(foreignObject, size)) {
                // shift elements to the right if any
                long cur = size;
                while (cur > index) {
                    Object o = Interop.readArrayElement(foreignObject, cur - 1);
                    Interop.writeArrayElement(foreignObject, cur, o);
                    cur--;
                }
                // write new element to list
                Interop.writeArrayElement(foreignObject, index, element);
                modCount++;
                return;
            }
            throw new UnsupportedOperationException();
        } catch (InteropException e) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public T remove(int index) {
        Objects.checkIndex(index, size());
        if (Interop.isArrayElementRemovable(foreignObject, index)) {
            try {
                T previous = get(index);
                Interop.removeArrayElement(foreignObject, index);
                modCount++;
                return previous;
            } catch (InteropException e) {
                throw new UnsupportedOperationException();
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String toString() {
        try {
            return Interop.asString(Interop.toDisplayString(foreignObject));
        } catch (UnsupportedMessageException e) {
            return super.toString();
        }
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return super.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        return super.addAll(index, c);
    }

    @Override
    public boolean remove(Object o) {
        return super.remove(o);
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        return super.removeIf(filter);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return super.removeAll(c);
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        super.removeRange(fromIndex, toIndex);
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        super.forEach(action);
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Stream<T> stream() {
        return super.stream();
    }

    @Override
    public Stream<T> parallelStream() {
        return super.parallelStream();
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator) {
        super.replaceAll(operator);
    }

    @Override
    public void clear() {
        super.clear();
    }

    @Override
    public int indexOf(Object o) {
        return super.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return super.lastIndexOf(o);
    }

    @Override
    public Object[] toArray() {
        return super.toArray();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T1> T1[] toArray(T1[] a) {
        return super.toArray(a);
    }

    @Override
    public Iterator<T> iterator() {
        return new Itr();
    }

    // Copied from AbstractList
    private class Itr implements Iterator<T> {
        /**
         * Index of element to be returned by subsequent call to next.
         */
        int cursor = 0;

        /**
         * Index of element returned by most recent call to next or previous. Reset to -1 if this
         * element is deleted by a call to remove.
         */
        int lastRet = -1;

        /**
         * The modCount value that the iterator believes that the backing List should have. If this
         * expectation is violated, the iterator has detected concurrent modification.
         */
        int expectedModCount = modCount;

        @Override
        public boolean hasNext() {
            return cursor != size();
        }

        @Override
        @SuppressWarnings("unchecked")
        public T next() {
            checkForComodification();
            try {
                int i = cursor;
                T next = get(i);
                lastRet = i;
                cursor = i + 1;
                return next;
            } catch (IndexOutOfBoundsException e) {
                checkForComodification();
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            if (lastRet < 0) {
                throw new UnsupportedOperationException();
            }
            checkForComodification();

            try {
                EspressoForeignList.this.remove(lastRet);
                if (lastRet < cursor) {
                    cursor--;
                }
                lastRet = -1;
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException e) {
                throw new ConcurrentModificationException();
            }
        }

        final void checkForComodification() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ListIterator<T> listIterator() {
        return super.listIterator();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ListIterator<T> listIterator(int index) {
        return super.listIterator(index);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> subList(int fromIndex, int toIndex) {
        return super.subList(fromIndex, toIndex);
    }

    @Override
    public Spliterator<T> spliterator() {
        return super.spliterator();
    }

    @Override
    public boolean contains(Object o) {
        return super.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return super.containsAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return super.retainAll(c);
    }

    @Override
    public void sort(Comparator<? super T> c) {
        super.sort(c);
    }

    @Override
    public <T1> T1[] toArray(IntFunction<T1[]> generator) {
        return super.toArray(generator);
    }
}
