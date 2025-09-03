/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.util.TBitSet;

/**
 * Specialized map for mapping all members of a {@link StateSet} of size {@code n} to integer values
 * {@code [0-n]}, where no two states are mapped to the same value.
 */
public final class StateSetToIntMap<S extends AbstractState<S, T>, T extends AbstractTransition<S, T>> {

    private final int[] keys;
    private final int[] values;
    private final TBitSet usedValues;

    private StateSetToIntMap(int[] keys) {
        this.keys = keys;
        this.values = new int[keys.length];
        this.usedValues = new TBitSet(keys.length);
        Arrays.fill(values, -1);
    }

    public static <SI extends StateIndex<? super S>, S extends AbstractState<S, T>, T extends AbstractTransition<S, T>> StateSetToIntMap<S, T> create(StateSet<SI, S> stateSet) {
        return new StateSetToIntMap<>(stateSet.toArrayOfIndices());
    }

    public static <S extends AbstractState<S, T>, T extends AbstractTransition<S, T>> StateSetToIntMap<S, T> create(S singleState) {
        return new StateSetToIntMap<>(new int[]{singleState.getId()});
    }

    public int getKey(int stateID) {
        int key = Arrays.binarySearch(keys, stateID);
        assert key >= 0;
        return key;
    }

    public int getKey(S state) {
        return getKey(state.getId());
    }

    public void put(int stateID, int value) {
        values[getKey(stateID)] = value;
        assert !usedValues.get(value);
        usedValues.set(value);
    }

    public void put(S state, int value) {
        put(state.getId(), value);
    }

    public int getValue(int stateID) {
        return values[getKey(stateID)];
    }

    public int getValue(S state) {
        return getValue(state.getId());
    }

    public boolean isValueUsed(int value) {
        return usedValues.get(value);
    }

    /**
     * Maps all states that don't have an assigned mapping yet to one of the remaining unused
     * values.
     */
    public void fillRest() {
        if (usedValues.numberOfSetBits() >= values.length) {
            return;
        }
        usedValues.invert();
        PrimitiveIterator.OfInt it = usedValues.iterator();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == -1) {
                assert it.hasNext();
                values[i] = it.nextInt();
            }
        }
        usedValues.setRange(0, values.length - 1);
    }
}
