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

import java.util.PrimitiveIterator;

/**
 * A set of short values to be used as backing set by {@link StateSet}.
 */
public interface StateSetBackingSet extends Iterable<Integer> {

    /**
     * @return a deep copy of this set.
     */
    StateSetBackingSet copy();

    /**
     * Analogous to {@link java.util.Set#contains(Object)}.
     */
    boolean contains(short id);

    /**
     * Analogous to {@link java.util.Set#add(Object)}.
     */
    boolean add(short id);

    /**
     * Add a value in batch mode. After calling this method, the set is in "add batch mode" until
     * {@link #addBatchFinish()} is called. The purpose of this is to allow more efficient add
     * operations that temporarily break the set's consistency. While this mode is active, other
     * methods are not guaranteed to work correctly!
     *
     * @param id the id to add to the set.
     */
    void addBatch(short id);

    /**
     * Stop "add batch mode". This method will restore the set to a sane state after a batch add
     * operation.
     */
    void addBatchFinish();

    /**
     * Efficient version of {@code remove(oldId); add(newId)}. This method assumes that the set
     * contains {@code oldId} and the set does not contain {@code newId}!
     *
     * @param oldId id to remove. Assumed to be contained in the set.
     * @param newId id to add. Assumed to be absent in the set.
     */
    void replace(short oldId, short newId);

    /**
     * Analogous to {@link java.util.Set#remove(Object)}.
     */
    boolean remove(short id);

    /**
     * Analogous to {@link java.util.Set#clear()} .
     */
    void clear();

    /**
     * Check whether this set is disjoint to another {@link StateSetBackingSet}.
     *
     * @param other the other backing set.
     * @return {@code true} if this set is disjoint to the other set.
     */
    boolean isDisjoint(StateSetBackingSet other);

    /**
     * Check whether this set is contains all entries of another {@link StateSetBackingSet}.
     *
     * @param other the other backing set.
     * @return {@code true} if this set contains the other set.
     */
    boolean contains(StateSetBackingSet other);

    @Override
    PrimitiveIterator.OfInt iterator();
}
