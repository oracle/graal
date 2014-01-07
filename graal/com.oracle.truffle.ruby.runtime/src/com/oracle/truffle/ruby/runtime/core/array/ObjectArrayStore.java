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

import java.util.*;

import com.oracle.truffle.ruby.runtime.*;

/**
 * A store for an array of any objects.
 */
public final class ObjectArrayStore extends BaseArrayStore {

    private Object[] values;

    public ObjectArrayStore() {
        this(new Object[]{});
    }

    public ObjectArrayStore(Object[] values) {
        this.values = values;
        size = values.length;
        capacity = values.length;
    }

    @Override
    public Object get(int normalisedIndex) {
        if (normalisedIndex >= size) {
            return NilPlaceholder.INSTANCE;
        }

        return values[normalisedIndex];
    }

    @Override
    public ArrayStore getRange(int normalisedBegin, int normalisedExclusiveEnd) {
        if (normalisedBegin >= size) {
            return null; // Represents Nil
        }

        return new ObjectArrayStore(Arrays.copyOfRange(values, normalisedBegin, normalisedExclusiveEnd));
    }

    @Override
    public void set(int normalisedIndex, Object value) throws GeneraliseArrayStoreException {
        if (normalisedIndex > size) {
            final int originalLength = size;
            createSpace(size, normalisedIndex - size + 1);
            Arrays.fill(values, originalLength, normalisedIndex, NilPlaceholder.INSTANCE);
            values[normalisedIndex] = value;
        } else if (normalisedIndex == size) {
            push(value);
        } else {
            values[normalisedIndex] = value;
        }
    }

    @Override
    public void setRangeSingle(int normalisedBegin, int normalisedExclusiveEnd, Object value) throws GeneraliseArrayStoreException {
        // Is the range the whole array?

        if (normalisedBegin == 0 && normalisedExclusiveEnd == size) {
            // Reset length and set the value.
            size = 1;
            values[0] = value;
        } else {
            // Delete the range, except for the first value.
            deleteSpace(normalisedBegin + 1, normalisedExclusiveEnd - normalisedBegin - 1);

            // Set the value we left in.
            System.err.println(normalisedBegin + " in " + size + " with " + values.length);
            values[normalisedBegin] = value;
        }
    }

    @Override
    public void setRangeArray(int normalisedBegin, int normalisedExclusiveEnd, ArrayStore other) throws GeneraliseArrayStoreException {
        setRangeArray(normalisedBegin, normalisedExclusiveEnd, (ObjectArrayStore) other.generalizeFor(null));
    }

    public void setRangeArray(int normalisedBegin, int normalisedExclusiveEnd, ObjectArrayStore other) {
        setRangeArrayMatchingTypes(normalisedBegin, normalisedExclusiveEnd, other.values, other.size);
    }

    @Override
    public void insert(int normalisedIndex, Object value) throws GeneraliseArrayStoreException {
        if (normalisedIndex > size) {
            final int originalLength = size;
            createSpaceAtEnd(normalisedIndex - size + 1);
            Arrays.fill(values, originalLength, normalisedIndex, NilPlaceholder.INSTANCE);
            values[normalisedIndex] = value;
        } else {
            createSpace(normalisedIndex, 1);
            values[normalisedIndex] = value;
        }
    }

    @Override
    public void push(Object value) throws GeneraliseArrayStoreException {
        createSpaceAtEnd(1);
        values[size - 1] = value;
    }

    @Override
    public Object deleteAt(int normalisedIndex) {
        if (normalisedIndex >= size) {
            return NilPlaceholder.INSTANCE;
        }

        final Object value = values[normalisedIndex];

        deleteSpace(normalisedIndex, 1);

        return value;
    }

    @Override
    public ArrayStore dup() {
        return new ObjectArrayStore(Arrays.copyOf(values, size));
    }

    @Override
    public boolean contains(Object value) {
        for (int n = 0; n < size; n++) {
            if (values[n].equals(value)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public ObjectArrayStore generalizeFor(Object type) {
        return this;
    }

    @Override
    public Object getIndicativeValue() {
        return null;
    }

    @Override
    protected void setCapacityByCopying(int newCapacity) {
        values = Arrays.copyOf(values, newCapacity);
        capacity = values.length;
    }

    @Override
    protected void setCapacityWithNewArray(int newCapacity) {
        values = new Object[newCapacity];
        capacity = values.length;
    }

    @Override
    protected Object getValuesArrayObject() {
        return values;
    }

    public Object[] getValues() {
        return values;
    }

    @Override
    public Object[] toObjectArray() {
        if (values.length == size) {
            return values;
        } else {
            return Arrays.copyOf(values, size);
        }
    }

    @Override
    public boolean equals(ArrayStore other) {
        if (other instanceof ObjectArrayStore) {
            return equals((ObjectArrayStore) other);
        } else {
            return super.equals(other);
        }
    }

    public boolean equals(ObjectArrayStore other) {
        if (other == null) {
            return false;
        } else if (other == this) {
            return true;
        } else if (other.size != size) {
            return false;
        } else if (other.capacity == capacity) {
            return Arrays.equals(other.values, values);
        } else {
            for (int n = 0; n < size; n++) {
                if (!other.values[n].equals(values[n])) {
                    return false;
                }
            }

            return true;
        }
    }
}
