/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex;

import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

public class BitSetOracle implements Iterable<Integer> {

    private final boolean[] matchTable;

    public BitSetOracle(int size) {
        matchTable = new boolean[size];
    }

    public boolean get(int i) {
        return matchTable[i];
    }

    public void set(int i) {
        matchTable[i] = true;
    }

    public void setRange(int lo, int hi) {
        for (int i = lo; i <= hi; i++) {
            matchTable[i] = true;
        }
    }

    public void setMulti(int... values) {
        for (int value : values) {
            set(value);
        }
    }

    public void clear(int i) {
        matchTable[i] = false;
    }

    public void invert() {
        for (int i = 0; i < matchTable.length; i++) {
            matchTable[i] = !matchTable[i];
        }
    }

    public void intersect(BitSetOracle other) {
        for (int i = 0; i < matchTable.length; i++) {
            matchTable[i] = matchTable[i] && other.matchTable[i];
        }
    }

    public void union(BitSetOracle other) {
        for (int i = 0; i < matchTable.length; i++) {
            matchTable[i] = matchTable[i] || other.matchTable[i];
        }
    }

    public void subtract(BitSetOracle other) {
        for (int i = 0; i < matchTable.length; i++) {
            if (other.get(i)) {
                clear(i);
            }
        }
    }

    public void reset() {
        for (int i = 0; i < matchTable.length; i++) {
            matchTable[i] = false;
        }
    }

    public int nextSetBit(int fromIndex) {
        for (int i = fromIndex; i < matchTable.length; i++) {
            if (matchTable[i]) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {

        class BitSetOracleIterator implements PrimitiveIterator.OfInt {

            private int next = nextSetBit(0);

            @Override
            public boolean hasNext() {
                return next != -1;
            }

            @Override
            public int nextInt() {
                if (next >= 0) {
                    int ret = next;
                    next = nextSetBit(next + 1);
                    return ret;
                } else {
                    throw new NoSuchElementException();
                }
            }
        }
        return new BitSetOracleIterator();
    }
}
