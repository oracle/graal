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
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import com.oracle.truffle.espresso.polyglot.Interop;
import com.oracle.truffle.espresso.polyglot.InteropException;
import com.oracle.truffle.espresso.polyglot.Polyglot;
import com.oracle.truffle.espresso.polyglot.TypeLiteral;
import com.oracle.truffle.espresso.polyglot.UnsupportedMessageException;

public class EspressoForeignList<T> extends AbstractList<T> implements List<T> {

    @Override
    public int size() {
        try {
            return (int) Interop.getArraySize(this);
        } catch (UnsupportedMessageException e) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean add(T t) {
        int size = size();
        if (Interop.isArrayElementWritable(this, size)) {
            try {
                Interop.writeArrayElement(this, size, t);
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
            if (Interop.isArrayElementReadable(this, index)) {
                Object rawObject = Interop.readArrayElement(this, index);
                TypeLiteral<T> typeLiteral = TypeLiteral.getReifiedType(this, 0);
                return Polyglot.castWithGenerics(rawObject, typeLiteral);
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
        if (Interop.isArrayElementWritable(this, index)) {
            try {
                T previous = get(index);
                Interop.writeArrayElement(this, index, element);
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
            long size = Interop.getArraySize(this);
            if (Interop.isArrayElementInsertable(this, size)) {
                // shift elements to the right if any
                long cur = size;
                while (cur > index) {
                    Object o = Polyglot.cast(Object.class, Interop.readArrayElement(this, cur - 1));
                    Interop.writeArrayElement(this, cur, o);
                    cur--;
                }
                // write new element to list
                Interop.writeArrayElement(this, index, element);
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
        if (Interop.isArrayElementRemovable(this, index)) {
            try {
                T previous = get(index);
                Interop.removeArrayElement(this, index);
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
            return Interop.asString(Interop.toDisplayString(this));
        } catch (UnsupportedMessageException e) {
            return super.toString();
        }
    }

    @Override
    public Iterator<T> iterator() {
        return new Itr();
    }

    // Copied from AbstractList
    private final class Itr implements Iterator<T> {
        /**
         * Index of element to be returned by subsequent call to next.
         */
        int cursor = 0;

        /**
         * Index of element returned by most recent call to next or previous. Reset to -1 if this
         * element is deleted by a call to remove.
         */
        int lastRet = -1;

        @Override
        public boolean hasNext() {
            return cursor != size();
        }

        @Override
        @SuppressWarnings("unchecked")
        public T next() {
            try {
                int i = cursor;
                T next = get(i);
                lastRet = i;
                cursor = i + 1;
                return next;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            if (lastRet < 0) {
                throw new UnsupportedOperationException();
            }

            try {
                EspressoForeignList.this.remove(lastRet);
                if (lastRet < cursor) {
                    cursor--;
                }
                lastRet = -1;
            } catch (IndexOutOfBoundsException e) {
                throw new ConcurrentModificationException();
            }
        }
    }
}
