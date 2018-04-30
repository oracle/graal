/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex;

import com.oracle.truffle.regex.tregex.automaton.IndexedState;
import com.oracle.truffle.regex.tregex.automaton.StateIndex;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class StateSetTest {

    private static final int INDEX_SIZE = 0xFF;
    private static final int SWITCH_TO_BITSET_THRESHOLD = 4;

    private ShortStateIndex index;
    private List<ShortState> tooManyForStateList;

    @Before
    public void setUp() {
        index = new ShortStateIndex(INDEX_SIZE);
        tooManyForStateList = new ArrayList<>();
        for (int i = 1; i <= SWITCH_TO_BITSET_THRESHOLD + 1; i++) {
            tooManyForStateList.add(new ShortState(i));
        }
    }

    private StateSet<ShortState> bitSet(int... elems) {
        StateSet<ShortState> result = new StateSet<>(index);

        // force the use of a bit set instead of a state list
        result.addAll(tooManyForStateList);
        result.removeAll(tooManyForStateList);

        for (int elem : elems) {
            result.add(new ShortState(elem));
        }

        return result;
    }

    private StateSet<ShortState> stateList(int... elems) {
        StateSet<ShortState> result = new StateSet<>(index);

        assert elems.length <= SWITCH_TO_BITSET_THRESHOLD;

        for (int elem : elems) {
            result.add(new ShortState(elem));
        }

        return result;
    }

    @Test
    public void consistentHashCodes() {
        StateSet<ShortState> usingBitSet = bitSet(1, 2, 3, 4);
        StateSet<ShortState> usingStateList = stateList(1, 2, 3, 4);

        Assert.assertEquals(usingBitSet, usingStateList);

        Assert.assertEquals("hash codes of equal StateSets should be equal", usingBitSet.hashCode(), usingStateList.hashCode());
    }

    private static class ShortState implements IndexedState {

        private final short id;

        ShortState(int id) {
            this.id = (short) id;
        }

        @Override
        public short getId() {
            return id;
        }
    }

    private static class ShortStateIndex implements StateIndex<ShortState> {

        private final ShortState[] index;

        ShortStateIndex(int size) {
            index = new ShortState[size];
            Arrays.setAll(index, ShortState::new);
        }

        @Override
        public int getNumberOfStates() {
            return index.length;
        }

        @Override
        public ShortState getState(int id) {
            return index[id];
        }
    }
}
