/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.collection;

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
