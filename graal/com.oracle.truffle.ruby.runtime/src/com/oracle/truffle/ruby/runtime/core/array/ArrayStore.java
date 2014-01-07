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
 * Interface to various ways to store values in arrays.
 */
public interface ArrayStore {

    /**
     * Get the size of the store.
     */
    int size();

    /**
     * Get a value from the array using a normalized index.
     */
    Object get(int normalisedIndex);

    /**
     * Get a range of values from an array store.
     */
    ArrayStore getRange(int normalisedBegin, int truncatedNormalisedExclusiveEnd);

    /**
     * Set a value at an index, or throw {@link GeneraliseArrayStoreException} if that's not
     * possible.
     */
    void set(int normalisedIndex, Object value) throws GeneraliseArrayStoreException;

    /**
     * Set a range to be a single value, or throw {@link GeneraliseArrayStoreException} if that's
     * not possible.
     */
    void setRangeSingle(int normalisedBegin, int truncatedNormalisedExclusiveEnd, Object value) throws GeneraliseArrayStoreException;

    /**
     * Set a range to be a copied from another array, or throw {@link GeneraliseArrayStoreException}
     * if that's not possible.
     */
    void setRangeArray(int normalisedBegin, int normalisedExclusiveEnd, ArrayStore other) throws GeneraliseArrayStoreException;

    /**
     * Insert a value at an index, or throw {@link GeneraliseArrayStoreException} if that's not
     * possible.
     */
    void insert(int normalisedIndex, Object value) throws GeneraliseArrayStoreException;

    /**
     * Push a value onto the end, or throw {@link GeneraliseArrayStoreException} if that's not
     * possible.
     */
    void push(Object value) throws GeneraliseArrayStoreException;

    /**
     * Delete a value at an index, returning the value.
     */
    Object deleteAt(int normalisedIndex);

    /**
     * Does a store contain a value?
     */
    boolean contains(Object value);

    /**
     * Duplicate the store.
     */
    ArrayStore dup();

    /**
     * Duplicate the store, in a format which can store an object.
     */
    ArrayStore generalizeFor(Object type);

    /**
     * Get the type of value stored.
     */
    Object getIndicativeValue();

    /**
     * Get the contents of the store as a new array.
     */
    Object[] toObjectArray();

    /**
     * Does one store equal another.
     */
    boolean equals(ArrayStore other);

}
