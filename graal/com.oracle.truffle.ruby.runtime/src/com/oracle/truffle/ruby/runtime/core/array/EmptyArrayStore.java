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

import com.oracle.truffle.ruby.runtime.*;

/**
 * An array store that can only be empty.
 */
public final class EmptyArrayStore implements ArrayStore {

    public static final EmptyArrayStore INSTANCE = new EmptyArrayStore();

    private EmptyArrayStore() {
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Object get(int normalisedIndex) {
        return NilPlaceholder.INSTANCE;
    }

    @Override
    public ArrayStore getRange(int normalisedBegin, int truncatedNormalisedExclusiveEnd) {
        return null; // Represents Nil
    }

    @Override
    public void set(int normalisedIndex, Object value) throws GeneraliseArrayStoreException {
        throw new GeneraliseArrayStoreException();
    }

    @Override
    public void setRangeSingle(int normalisedBegin, int truncatedNormalisedExclusiveEnd, Object value) throws GeneraliseArrayStoreException {
        throw new GeneraliseArrayStoreException();
    }

    @Override
    public void setRangeArray(int normalisedBegin, int normalisedExclusiveEnd, ArrayStore other) throws GeneraliseArrayStoreException {
        throw new GeneraliseArrayStoreException();
    }

    @Override
    public void insert(int normalisedIndex, Object value) throws GeneraliseArrayStoreException {
        throw new GeneraliseArrayStoreException();
    }

    @Override
    public void push(Object value) throws GeneraliseArrayStoreException {
        throw new GeneraliseArrayStoreException();
    }

    @Override
    public Object deleteAt(int normalisedIndex) {
        throw new UnsupportedOperationException("Cannot delete from an empty array");
    }

    @Override
    public ArrayStore dup() {
        return this;
    }

    @Override
    public boolean contains(Object value) {
        return false;
    }

    @Override
    public ArrayStore generalizeFor(Object type) {
        if (type instanceof Integer) {
            return new FixnumArrayStore();
        } else {
            return new ObjectArrayStore();
        }
    }

    @Override
    public Object getIndicativeValue() {
        return null;
    }

    @Override
    public Object[] toObjectArray() {
        return new Object[]{};
    }

    @Override
    public boolean equals(ArrayStore other) {
        if (other == null) {
            return false;
        } else if (other == this) {
            return true;
        } else {
            return other.size() == 0;
        }
    }

}
