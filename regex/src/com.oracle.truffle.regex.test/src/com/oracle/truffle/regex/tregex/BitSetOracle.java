/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
