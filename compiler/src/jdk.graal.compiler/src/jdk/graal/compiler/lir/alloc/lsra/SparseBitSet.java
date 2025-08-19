/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.alloc.lsra;

import java.util.Arrays;
import java.util.BitSet;

import jdk.graal.compiler.debug.Assertions;

/**
 * This class implements a very simple sparse bitset that's optimized for the measured usage by
 * linear scan. In practice the bitset cardinality is at most 60 elements while the range of values
 * in the set can be quite large, reaching over 100000 in extreme cases. Since lifetime analysis
 * uses 4 bitsets per block and the number of blocks can also be very large, space efficiency can
 * plays a major role in performance. A simple sorted list of integers performs significantly better
 * for large numbers of intervals and blocks while only modestly affecting the perform for small
 * values.
 */
public class SparseBitSet {

    /**
     * A bitset used to verify the operation of this set.
     */
    private BitSet sanity;

    /**
     * The number of elements in the set.
     */
    protected int size;

    /**
     * The elements in the set.
     */
    protected int[] elements;

    private boolean checkBitSet() {
        if (sanity != null) {
            assert sanity.cardinality() == cardinality() : sanity + " " + this;
            for (int bit = sanity.nextSetBit(0); bit >= 0; bit = sanity.nextSetBit(bit + 1)) {
                boolean found = false;
                for (int i = 0; i < size; i++) {
                    if (elements[i] == bit) {
                        found = true;
                        break;
                    }
                }
                assert found : sanity + " " + this;
            }
        }
        return true;
    }

    public SparseBitSet() {
        elements = new int[4];
        if (Assertions.assertionsEnabled()) {
            sanity = new BitSet();
        }
    }

    public SparseBitSet(SparseBitSet other) {
        elements = other.elements.clone();
        size = other.size;
        if (other.sanity != null) {
            sanity = (BitSet) other.sanity.clone();
        }
        iterator = 0;
    }

    private boolean checkOr(SparseBitSet other) {
        if (sanity != null) {
            sanity.or(other.sanity);
            checkBitSet();
        }
        return true;
    }

    public void or(SparseBitSet other) {
        assert checkBitSet();
        assert other.checkBitSet();

        if (size == 0) {
            if (elements.length < other.size) {
                elements = other.elements.clone();
            } else {
                System.arraycopy(other.elements, 0, elements, 0, other.size);
            }
            size = other.size;
        } else {
            ensureCapacity(other.size);
            int otherIndex = 0;
            int originalSize = size;
            for (int index = 0; index < originalSize && otherIndex < other.size;) {
                int value = elements[index];
                int otherValue = other.elements[otherIndex];
                if (value == otherValue) {
                    index++;
                    otherIndex++;
                } else if (value < otherValue) {
                    index++;
                } else {
                    // append new element
                    ensureCapacity(size + 1);
                    elements[size++] = otherValue;
                    otherIndex++;
                }
            }
            if (size != originalSize) {
                // sort the trailing elements into the list
                Arrays.sort(elements, 0, size);
            }
            if (otherIndex < other.size) {
                // Append trailing elements which already sorted
                int newElements = other.size - otherIndex;
                ensureCapacity(size + newElements);
                System.arraycopy(other.elements, otherIndex, elements, size, newElements);
                size += newElements;
            }
        }

        assert checkOr(other);
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > elements.length) {
            grow(minCapacity);
        }
    }

    /**
     * Increases the capacity to ensure that it can hold at least the number of elements specified
     * by the minimum capacity argument.
     *
     * @param minCapacity the desired minimum capacity
     * @throws OutOfMemoryError if minCapacity is less than zero
     */
    private int[] grow(int minCapacity) {
        int oldCapacity = elements.length;
        /* minimum growth */
        /* preferred growth */
        int newCapacity = oldCapacity + Math.max(minCapacity - oldCapacity, oldCapacity >> 1);
        return elements = Arrays.copyOf(elements, newCapacity);
    }

    private boolean checkAndNot(SparseBitSet other) {
        if (sanity != null) {
            sanity.andNot(other.sanity);
            checkBitSet();
        }
        return true;
    }

    public void andNot(SparseBitSet other) {
        int otherIndex = 0;
        int firstDeleted = -1;
        for (int index = 0; index < size && otherIndex < other.size;) {
            int value = elements[index];
            int otherValue = other.elements[otherIndex];
            if (value == otherValue) {
                elements[index] = -1;
                if (firstDeleted == -1) {
                    firstDeleted = index;
                }
                index++;
                otherIndex++;
            } else if (value < otherValue) {
                index++;
            } else {
                otherIndex++;
            }
        }
        if (firstDeleted != -1) {
            // Now compact the deleted elements
            int shift = 0;
            for (int i = firstDeleted; i < size; i++) {
                if (elements[i] < 0) {
                    shift++;
                } else {
                    elements[i - shift] = elements[i];
                }
            }
            size -= shift;
        }
        assert checkAndNot(other);
    }

    public int cardinality() {
        return size;
    }

    public boolean get(int value) {
        return Arrays.binarySearch(elements, 0, size, value) >= 0;
    }

    private boolean checkSet(int value) {
        if (value < 0) {
            throw new IllegalArgumentException();
        }
        if (sanity != null) {
            sanity.set(value);
            checkBitSet();
        }
        return true;
    }

    public void set(int value) {
        int location = Arrays.binarySearch(elements, 0, size, value);
        if (location < 0) {
            insertAt(value, -(location + 1));
        }
        assert checkSet(value);
    }

    private void insertAt(int value, int insertPoint) {
        if (size == elements.length) {
            grow(size + 1);
        }
        System.arraycopy(elements, insertPoint, elements, insertPoint + 1, size - insertPoint);
        elements[insertPoint] = value;
        size++;
    }

    private int iterator = 0;

    public int nextSetBit(int i) {
        if (i == 0) {
            iterator = 0;
        }
        if (iterator < size) {
            return elements[iterator++];
        }
        return -1;
    }

    public void clear() {
        size = 0;
        if (Assertions.assertionsEnabled() && sanity != null) {
            sanity.clear();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SparseBitSet bs) {
            if (size != bs.size) {
                return false;
            }
            int result = Arrays.compare(elements, 0, size, bs.elements, 0, size);
            assert sanity == null || sanity.equals(bs.sanity) == (result == 0) : sanity + " " + bs.sanity;
            return result == 0;
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("set(size=").append(size).append(", {");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(elements[i]);
        }
        sb.append("})");
        return sb.toString();
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }
}
