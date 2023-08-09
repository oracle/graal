/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime.collection;

import java.util.Arrays;

public final class ArrayQueue<E> implements SerialQueue<E> {
    private static final int INITIAL_SIZE = 128;

    private Object[] items;

    private int start;

    private int tail;

    public ArrayQueue() {
        this.items = new Object[INITIAL_SIZE];
        this.start = 0;
        this.tail = 0;
    }

    private void ensureIndex(int n) {
        if (n >= items.length) {
            int factor = 1;
            if (tail - start > items.length / 2) {
                factor = 2;
            }
            final Object[] nitems = new Object[items.length * factor];
            System.arraycopy(items, start, nitems, 0, tail - start);
            items = nitems;
            tail = tail - start;
            start = 0;
        }
    }

    @Override
    public void add(E x) {
        if (x == null) {
            throw new NullPointerException();
        }
        ensureIndex(tail);
        items[tail] = x;
        tail++;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E poll() {
        if (start == tail) {
            return null;
        }
        E result = (E) items[start];
        start++;
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E peek() {
        return (E) this.items[start];
    }

    @Override
    public void clear() {
        this.items = new Object[INITIAL_SIZE];
        this.start = 0;
        this.tail = 0;
    }

    @Override
    public int size() {
        return tail - start;
    }

    @Override
    public Object[] toArray() {
        Object[] result = new Object[tail - start];
        System.arraycopy(items, start, result, 0, tail - start);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        T[] result = (T[]) Arrays.copyOf(items, tail - start, a.getClass());
        return result;
    }

    @Override
    public int internalCapacity() {
        return items.length - size();
    }
}
