/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.analysis.liveness;

import java.util.Arrays;
import java.util.Iterator;

public final class History implements Iterable<Record> {
    private static final int DEFAULT_CAPACITY = 2;

    private Record[] history;
    private int size;
    private int capacity;

    public History() {
        this.size = 0;
        this.capacity = DEFAULT_CAPACITY;
        this.history = new Record[capacity];
    }

    public void add(Record record) {
        if (size == capacity) {
            history = Arrays.copyOf(history, capacity <<= 1);
        }
        history[size] = record;
        size++;
    }

    @Override
    public Iterator<Record> iterator() {
        return new OrderedIterator();
    }

    public Iterable<Record> reverse() {
        return new ReverseIterator();
    }

    private final class OrderedIterator implements Iterator<Record> {
        private int pos = 0;

        @Override
        public boolean hasNext() {
            return pos < size;
        }

        @Override
        public Record next() {
            return history[pos++];
        }
    }

    private final class ReverseIterator implements Iterable<Record>, Iterator<Record> {
        private int pos = size - 1;

        @Override
        public boolean hasNext() {
            return pos >= 0;
        }

        @Override
        public Record next() {
            return history[pos--];
        }

        @Override
        public Iterator<Record> iterator() {
            return this;
        }
    }
}
