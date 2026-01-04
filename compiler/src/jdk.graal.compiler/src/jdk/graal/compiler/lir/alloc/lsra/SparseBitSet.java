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

/**
 * This class implements a very simple sparse bitset that's optimized for the measured usage by
 * linear scan. In practice the bitset cardinality is at most 60 elements while the range of values
 * in the set can be quite large, reaching over 100000 in extreme cases. Since lifetime analysis
 * uses 4 bitsets per block and the number of blocks can also be very large, space efficiency can
 * play a major role in performance. A simple sorted list of integers performs significantly better
 * for large numbers of intervals and blocks while having no measurable performance impact for small
 * numbers of intervals when compared to {@link java.util.BitSet}.
 */
public class SparseBitSet {

    /**
     * The number of elements in the set.
     */
    protected int size;

    /**
     * The elements in the set.
     */
    protected int[] elements;

    /**
     * Internal element iterator value used by {@link #iterateValues(int)}.
     */
    private int iterator = 0;

    /**
     * The sets are almost never empty and commonly contain just a few elements so start at a small
     * initial size.
     */
    static final int INITIAL_SIZE = 4;

    public SparseBitSet() {
        elements = new int[INITIAL_SIZE];
    }

    public SparseBitSet(SparseBitSet other) {
        elements = other.elements.clone();
        size = other.size;
        iterator = 0;
    }

    /**
     * Adds all elements from {@code other} to this set.
     */
    public void addAll(SparseBitSet other) {
        if (size == 0) {
            if (elements.length < other.size) {
                elements = other.elements.clone();
            } else {
                System.arraycopy(other.elements, 0, elements, 0, other.size);
            }
            size = other.size;
        } else {
            ensureCapacity(other.size);
            int originalSize = size;
            int index = 0;
            int otherIndex = 0;
            while (index < originalSize && otherIndex < other.size) {
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
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > elements.length) {
            int oldCapacity = elements.length;
            elements = Arrays.copyOf(elements, oldCapacity + Math.max(minCapacity - oldCapacity, oldCapacity >> 1));
        }
    }

    /**
     * Remove all the values that are set in {@ocde other} from this set.
     */
    public void removeAll(SparseBitSet other) {
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
    }

    /**
     * Returns the number of bits set to {@code true} in this set.
     */
    public int cardinality() {
        return size;
    }

    /**
     * Returns the bitIndex of the bit with the specified index. The bitIndex is {@code true} if the
     * bit with the index {@code bitIndex} is currently set in this {@code BitSet}; otherwise, the
     * result is {@code false}.
     */
    public boolean get(int bitIndex) {
        return Arrays.binarySearch(elements, 0, size, bitIndex) >= 0;
    }

    /**
     * Sets the bit at the specified index to {@code true}.
     */
    public void set(int bitIndex) {
        if (bitIndex < 0) {
            throw new IllegalArgumentException("negative values are not permitted");
        }
        int location = Arrays.binarySearch(elements, 0, size, bitIndex);
        if (location < 0) {
            /*
             * If an element isn't found, binarySearch returns the proper insertion location as
             * (-(insertion point) - 1 so reverse this computation and insert it.
             */
            int insertPoint = -(location + 1);
            ensureCapacity(size + 1);
            System.arraycopy(elements, insertPoint, elements, insertPoint + 1, size - insertPoint);
            elements[insertPoint] = bitIndex;
            size++;
        }
    }

    /**
     * Uses an internal iterator to visit the values in the set in order. {@code iterateValues(0)}
     * starts the iteration and returns the first value. Repeated calls of {@code iterateValues}
     * with non-zero values return the next element in the set. It returns -1 when there are no more
     * elements to visit.
     */
    public int iterateValues(int i) {
        if (i == 0) {
            iterator = 0;
        }
        if (iterator < size) {
            return elements[iterator++];
        }
        return -1;
    }

    /**
     * Remove all values from the bit set.
     */
    public void clear() {
        size = 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SparseBitSet bs) {
            if (size != bs.size) {
                return false;
            }
            return Arrays.compare(elements, 0, size, bs.elements, 0, size) == 0;
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(elements[i]);
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }
}
