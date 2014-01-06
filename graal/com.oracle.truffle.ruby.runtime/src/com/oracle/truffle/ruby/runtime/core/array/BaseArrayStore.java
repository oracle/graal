/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core.array;

/**
 * Contains implementations of as much of the array stores that we could easily share. Much of the
 * rest depends on static types in method signatures, so is lexically almost the same, but isn't the
 * same in type, and as the whole point is to avoid boxing, we can't use Java's generics.
 */
public abstract class BaseArrayStore implements ArrayStore {

    protected int capacity;
    protected int size;

    @Override
    public int size() {
        return size;
    }

    /**
     * Set a range in the array to be another range. You must ensure that the otherValues array is
     * of the same type as your values array.
     */
    protected void setRangeArrayMatchingTypes(int normalisedBegin, int normalisedExclusiveEnd, Object otherValues, int otherSize) {
        // Is the range the whole array?

        if (normalisedBegin == 0 && normalisedExclusiveEnd == size) {
            // Do we already have enough space?

            if (otherSize <= capacity) {
                // Copy to our existing array.
                final Object values = getValuesArrayObject();
                System.arraycopy(otherValues, 0, values, 0, otherSize);
            } else {
                // Create a new copy of their array.
                setCapacityWithNewArray(otherSize);
                final Object values = getValuesArrayObject();
                System.arraycopy(otherValues, 0, values, 0, otherSize);
            }

            size = otherSize;
        } else {
            final int rangeLength = normalisedExclusiveEnd - normalisedBegin;

            // Create extra space - might be negative if the new range is shorter, or zero.

            final int extraSpaceNeeded = otherSize - rangeLength;

            if (extraSpaceNeeded > 0) {
                createSpace(normalisedBegin, extraSpaceNeeded);
            } else if (extraSpaceNeeded < 0) {
                deleteSpace(normalisedBegin, -extraSpaceNeeded);
            }

            // Copy across the new values.
            final Object values = getValuesArrayObject();
            System.arraycopy(otherValues, 0, values, normalisedBegin, otherSize);
        }
    }

    protected void createSpace(int normalisedBegin, int count) {
        /*
         * Is this space at the end or in the middle?
         */

        if (normalisedBegin == size) {
            createSpaceAtEnd(count);
        } else {
            /*
             * Create space in the middle - is the array already big enough?
             */

            final int elementsToMove = size - normalisedBegin;

            if (size + count > capacity) {
                /*
                 * The array isn't big enough. We don't want to use Arrays.copyOf because that will
                 * do wasted copying of the elements we are about to move. However - is
                 * Arrays.copyOf clever enough to see that only one instance of Array is using the
                 * block and use realloc, potentially avoiding a malloc and winning?
                 */

                final Object values = getValuesArrayObject();
                setCapacityWithNewArray(ArrayUtilities.capacityFor(size + count));
                final Object newValues = getValuesArrayObject();
                System.arraycopy(values, 0, newValues, 0, normalisedBegin);
                System.arraycopy(values, normalisedBegin, newValues, normalisedBegin + count, elementsToMove);
            } else {
                /*
                 * The array is already big enough - we can copy elements already in the array to
                 * make space.
                 */

                final Object values = getValuesArrayObject();
                System.arraycopy(values, normalisedBegin, values, normalisedBegin + count, elementsToMove);
            }

            size += count;
        }
    }

    protected void createSpaceAtEnd(int count) {
        /*
         * Create space at the end - we can do this by creating a copy of the array if needed.
         */

        if (size + count > capacity) {
            setCapacityByCopying(ArrayUtilities.capacityFor(size + count));
        }

        size += count;
    }

    protected void deleteSpace(int normalisedBegin, int count) {
        final Object values = getValuesArrayObject();
        final int elementsToMove = size - normalisedBegin - count;

        if (elementsToMove > 0) {
            System.arraycopy(values, normalisedBegin + count, values, normalisedBegin, elementsToMove);
        }

        size -= count;
    }

    protected abstract void setCapacityByCopying(int newCapacity);

    protected abstract void setCapacityWithNewArray(int newCapacity);

    protected abstract Object getValuesArrayObject();

    @Override
    public boolean equals(ArrayStore other) {
        for (int n = 0; n < size; n++) {
            if (!other.get(n).equals(get(n))) {
                return false;
            }
        }

        return true;
    }

}
