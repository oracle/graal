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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * A store for a pair of Fixnums.
 */
public final class FixnumImmutablePairArrayStore extends BaseArrayStore {

    private final int first;
    private final int second;

    public FixnumImmutablePairArrayStore(int first, int second) {
        size = 2;
        capacity = 2;
        this.first = first;
        this.second = second;
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public Object get(int normalisedIndex) {
        switch (normalisedIndex) {
            case 0:
                return first;
            case 1:
                return second;
            default:
                return NilPlaceholder.INSTANCE;
        }
    }

    public int getFixnum(int normalisedIndex) throws UnexpectedResultException {
        switch (normalisedIndex) {
            case 0:
                return first;
            case 1:
                return second;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new UnexpectedResultException(NilPlaceholder.INSTANCE);
        }
    }

    @Override
    public ArrayStore getRange(int normalisedBegin, int truncatedNormalisedExclusiveEnd) {
        if (normalisedBegin >= size) {
            return null; // Represents Nil
        }

        return new FixnumArrayStore(Arrays.copyOfRange(new int[]{first, second}, normalisedBegin, truncatedNormalisedExclusiveEnd));
    }

    @Override
    public void set(int normalisedIndex, Object value) throws GeneraliseArrayStoreException {
        CompilerDirectives.transferToInterpreter();
        throw new GeneraliseArrayStoreException();
    }

    @Override
    public void setRangeSingle(int normalisedBegin, int truncatedNormalisedExclusiveEnd, Object value) throws GeneraliseArrayStoreException {
        CompilerDirectives.transferToInterpreter();
        throw new GeneraliseArrayStoreException();
    }

    @Override
    public void setRangeArray(int normalisedBegin, int normalisedExclusiveEnd, ArrayStore other) throws GeneraliseArrayStoreException {
        CompilerDirectives.transferToInterpreter();
        throw new GeneraliseArrayStoreException();
    }

    @Override
    public void insert(int normalisedIndex, Object value) throws GeneraliseArrayStoreException {
        CompilerDirectives.transferToInterpreter();
        throw new GeneraliseArrayStoreException();
    }

    @Override
    public void push(Object value) throws GeneraliseArrayStoreException {
        CompilerDirectives.transferToInterpreter();
        throw new GeneraliseArrayStoreException();
    }

    @Override
    public Object deleteAt(int normalisedIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayStore dup() {
        return this;
    }

    @Override
    public boolean contains(Object value) {
        if (value instanceof Integer) {
            final int intValue = (int) value;
            return first == intValue || second == intValue;
        } else {
            return false;
        }
    }

    @Override
    public ArrayStore generalizeFor(Object type) {
        return new ObjectArrayStore(toObjectArray());
    }

    @Override
    public Object getIndicativeValue() {
        return 0;
    }

    @Override
    protected void setCapacityByCopying(int newCapacity) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void setCapacityWithNewArray(int newCapacity) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object getValuesArrayObject() {
        return new int[]{first, second};
    }

    @Override
    public Object[] toObjectArray() {
        return new Object[]{first, second};
    }

    @Override
    public boolean equals(ArrayStore other) {
        if (other instanceof FixnumImmutablePairArrayStore) {
            return equals((FixnumImmutablePairArrayStore) other);
        } else {
            return super.equals(other);
        }
    }

    public boolean equals(FixnumImmutablePairArrayStore other) {
        if (other == null) {
            return false;
        } else if (other == this) {
            return true;
        } else {
            return other.first == first && other.second == second;
        }
    }
}
