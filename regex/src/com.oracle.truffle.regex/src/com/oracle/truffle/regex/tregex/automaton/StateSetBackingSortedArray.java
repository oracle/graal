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
package com.oracle.truffle.regex.tregex.automaton;

import java.util.Arrays;
import java.util.PrimitiveIterator;

public class StateSetBackingSortedArray implements StateSetBackingSet {

    private short[] array;
    private short size;

    public StateSetBackingSortedArray() {
        array = new short[8];
    }

    private StateSetBackingSortedArray(StateSetBackingSortedArray copy) {
        array = Arrays.copyOf(copy.array, copy.array.length);
        size = copy.size;
    }

    @Override
    public StateSetBackingSet copy() {
        return new StateSetBackingSortedArray(this);
    }

    private int find(short id) {
        return Arrays.binarySearch(array, 0, size, id);
    }

    private void checkGrow() {
        if (array.length == size) {
            array = Arrays.copyOf(array, array.length * 2);
        }
    }

    @Override
    public boolean contains(short id) {
        return find(id) >= 0;
    }

    @Override
    public boolean add(short id) {
        checkGrow();
        int searchResult = find(id);
        if (searchResult >= 0) {
            return false;
        }
        int insertionPoint = (searchResult + 1) * (-1);
        System.arraycopy(array, insertionPoint, array, insertionPoint + 1, size - insertionPoint);
        size++;
        array[insertionPoint] = id;
        return true;
    }

    @Override
    public void addBatch(short id) {
        checkGrow();
        array[size++] = id;
    }

    @Override
    public void addBatchFinish() {
        Arrays.sort(array, 0, size);
    }

    @Override
    public void replace(short oldId, short newId) {
        int searchResult = find(newId);
        assert searchResult < 0;
        int insertionPoint = (searchResult + 1) * (-1);
        int deletionPoint = find(oldId);
        assert deletionPoint >= 0;
        if (insertionPoint < deletionPoint) {
            System.arraycopy(array, insertionPoint, array, insertionPoint + 1, deletionPoint - insertionPoint);
        } else if (insertionPoint > deletionPoint) {
            insertionPoint--;
            System.arraycopy(array, deletionPoint + 1, array, deletionPoint, insertionPoint - deletionPoint);
        }
        array[insertionPoint] = newId;
    }

    @Override
    public boolean remove(short id) {
        int searchResult = find(id);
        if (searchResult < 0) {
            return false;
        }
        removeElement(searchResult);
        return true;
    }

    private void removeElement(int i) {
        assert i >= 0;
        System.arraycopy(array, i + 1, array, i, size - (i + 1));
        size--;
    }

    @Override
    public void clear() {
        size = 0;
    }

    @Override
    public boolean isDisjoint(StateSetBackingSet other) {
        for (int i : this) {
            if (other.contains((short) i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean contains(StateSetBackingSet other) {
        for (int i : other) {
            if (!contains((short) i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return new PrimitiveIterator.OfInt() {

            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < size;
            }

            @Override
            public int nextInt() {
                return array[i++];
            }

            @Override
            public void remove() {
                removeElement(--i);
            }
        };
    }

    @Override
    public int hashCode() {
        if (array == null) {
            return 0;
        }
        int result = 1;
        for (int i = 0; i < size; i++) {
            result = 31 * result + array[i];
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StateSetBackingSortedArray)) {
            return false;
        }
        StateSetBackingSortedArray o = (StateSetBackingSortedArray) obj;
        if (size != o.size) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (array[i] != o.array[i]) {
                return false;
            }
        }
        return true;
    }
}
