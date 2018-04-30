/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.automaton;

import java.util.Arrays;
import java.util.PrimitiveIterator;

public class StateSetBackingSortedArray implements StateSetBackingSet {

    private short[] array;
    private short size;

    public StateSetBackingSortedArray() {
    }

    private StateSetBackingSortedArray(StateSetBackingSortedArray copy) {
        if (copy.isActive()) {
            array = Arrays.copyOf(copy.array, copy.array.length);
        }
        size = copy.size;
    }

    @Override
    public StateSetBackingSet copy() {
        return new StateSetBackingSortedArray(this);
    }

    @Override
    public void create(int stateIndexSize) {
        array = new short[8];
    }

    @Override
    public boolean isActive() {
        return array != null;
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
        if (size != o.size || isActive() != o.isActive()) {
            return false;
        }
        if (!isActive()) {
            return true;
        }
        for (int i = 0; i < size; i++) {
            if (array[i] != o.array[i]) {
                return false;
            }
        }
        return true;
    }
}
