/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.regex.tregex.buffer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.function.IntFunction;

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
 * List&lt;SomeClass[]> results = new ArrayList&lt;>();
 * for (Object obj : listOfThingsToProcess) {
 *     for (Object x : obj.thingsThatShouldBecomeSomeClass()) {
 *         buf.add(someCalculation(x));
 *     }
 *     results.add(buf.toArray(typedArray));
 *     buf.clear();
 * }
 * </pre>
 */
public final class ObjectArrayBuffer<T> extends AbstractArrayBuffer implements Iterable<T> {

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

    @SuppressWarnings("unchecked")
    public T get(int i) {
        return (T) buf[i];
    }

    public void set(int i, T obj) {
        buf[i] = obj;
    }

    public void add(T o) {
        if (length == buf.length) {
            grow(length * 2);
        }
        buf[length] = o;
        length++;
    }

    public void addAll(ObjectArrayBuffer<T> other) {
        addAll(other.buf, 0, other.length);
    }

    public void addAll(Object[] arr) {
        ensureCapacity(length + arr.length);
        System.arraycopy(arr, 0, buf, length, arr.length);
        length += arr.length;
    }

    public void addAll(Object[] arr, int fromIndex, int toIndex) {
        int len = toIndex - fromIndex;
        ensureCapacity(length + len);
        System.arraycopy(arr, fromIndex, buf, length, len);
        length += len;
    }

    public Object pop() {
        return buf[--length];
    }

    public Object peek() {
        return buf[length - 1];
    }

    public ObjectArrayBuffer<T> asFixedSizeArray(int size) {
        ensureCapacity(size);
        Arrays.fill(buf, null);
        length = size;
        return this;
    }

    @SuppressWarnings("unchecked")
    public void sort(Comparator<T> comparator) {
        Arrays.sort((T[]) buf, 0, length, comparator);
    }

    @SuppressWarnings("unchecked")
    public <ST> ST[] toArray(ST[] a) {
        if (a.length < length) {
            return (ST[]) Arrays.copyOf(buf, length, a.getClass());
        }
        System.arraycopy(buf, 0, a, 0, length);
        return a;
    }

    public <ST> ST[] toArray(IntFunction<ST[]> generator) {
        return toArray(generator.apply(length));
    }

    @Override
    public Iterator<T> iterator() {
        return new ObjectBufferIterator<>(buf, length);
    }

    private static final class ObjectBufferIterator<T> implements Iterator<T> {

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

        @SuppressWarnings("unchecked")
        @Override
        public T next() {
            return (T) buf[i++];
        }
    }
}
