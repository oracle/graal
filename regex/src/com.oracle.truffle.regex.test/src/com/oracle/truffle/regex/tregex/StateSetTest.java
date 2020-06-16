/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.regex.tregex.automaton.StateIndex;
import com.oracle.truffle.regex.tregex.automaton.StateSet;

public class StateSetTest {

    static final int MAX_SMALL_STATE_INDEX_SIZE = 64;
    private static final int INDEX_SIZE = 256;
    private static final int SWITCH_TO_BITSET_THRESHOLD = 4;

    private ShortStateIndex smallIndex;
    private ShortStateIndex largeIndex;
    private StateSet<ShortStateIndex, ShortState> small;
    private StateSet<ShortStateIndex, ShortState> large;
    private List<ShortState> tooManyForStateList;

    @Before
    public void setUp() {
        smallIndex = new ShortStateIndex(MAX_SMALL_STATE_INDEX_SIZE);
        largeIndex = new ShortStateIndex(INDEX_SIZE);
        small = StateSet.create(smallIndex);
        large = StateSet.create(largeIndex);
        tooManyForStateList = new ArrayList<>();
        for (int i = 1; i <= SWITCH_TO_BITSET_THRESHOLD + 1; i++) {
            tooManyForStateList.add(new ShortState(i));
        }
    }

    private StateSet<ShortStateIndex, ShortState> bitSet(ShortStateIndex idx, int... elems) {
        StateSet<ShortStateIndex, ShortState> result = StateSet.create(idx);

        // force the use of a bit set instead of a state list
        result.addAll(tooManyForStateList);
        result.removeAll(tooManyForStateList);

        for (int elem : elems) {
            result.add(new ShortState(elem));
        }

        return result;
    }

    private static StateSet<ShortStateIndex, ShortState> stateList(ShortStateIndex idx, int... elems) {
        StateSet<ShortStateIndex, ShortState> result = StateSet.create(idx);

        assert elems.length <= SWITCH_TO_BITSET_THRESHOLD;

        for (int elem : elems) {
            result.add(new ShortState(elem));
        }

        return result;
    }

    @Test
    public void consistentHashCodes() {
        for (ShortStateIndex idx : new ShortStateIndex[]{largeIndex, smallIndex}) {
            StateSet<ShortStateIndex, ShortState> usingBitSet = bitSet(idx, 1, 2, 3, 4);
            StateSet<ShortStateIndex, ShortState> usingStateList = stateList(idx, 1, 2, 3, 4);

            Assert.assertEquals(usingBitSet, usingStateList);

            Assert.assertEquals("hash codes of equal StateSets should be equal", usingBitSet.hashCode(), usingStateList.hashCode());
        }
    }

    private static ShortState state(int id) {
        return new ShortState(id);
    }

    private StateSet<ShortStateIndex, ShortState> small(int... id) {
        StateSet<ShortStateIndex, ShortState> ret = StateSet.create(smallIndex);
        for (int i : id) {
            ret.add(state(i));
        }
        return ret;
    }

    private StateSet<ShortStateIndex, ShortState> large(int... id) {
        StateSet<ShortStateIndex, ShortState> ret = StateSet.create(largeIndex);
        for (int i : id) {
            ret.add(state(i));
        }
        return ret;
    }

    private void addNew(ShortState s) {
        assertTrue(small.add(s));
        assertTrue(small.contains(s));
        assertTrue(large.add(s));
        assertTrue(large.contains(s));
    }

    private void addExisting(ShortState s) {
        assertFalse(small.add(s));
        assertTrue(small.contains(s));
        assertFalse(large.add(s));
        assertTrue(large.contains(s));
    }

    private void removeExisting(ShortState s) {
        assertTrue(small.remove(s));
        assertFalse(small.contains(s));
        assertTrue(large.remove(s));
        assertFalse(large.contains(s));
    }

    private void removeNotExisting(ShortState s) {
        assertFalse(small.remove(s));
        assertFalse(small.contains(s));
        assertFalse(large.remove(s));
        assertFalse(large.contains(s));
    }

    private void addAllNew(int... id) {
        StateSet<ShortStateIndex, ShortState> sSmall = small(id);
        StateSet<ShortStateIndex, ShortState> sLarge = large(id);
        assertTrue(small.addAll(sSmall));
        assertTrue(small.containsAll(sSmall));
        assertTrue(large.addAll(sLarge));
        assertTrue(large.containsAll(sLarge));
    }

    private void addAllExisting(int... id) {
        StateSet<ShortStateIndex, ShortState> sSmall = small(id);
        StateSet<ShortStateIndex, ShortState> sLarge = large(id);
        assertFalse(small.addAll(sSmall));
        assertTrue(small.containsAll(sSmall));
        assertFalse(large.addAll(sLarge));
        assertTrue(large.containsAll(sLarge));
    }

    private void removeAllExisting(int... id) {
        StateSet<ShortStateIndex, ShortState> sSmall = small(id);
        StateSet<ShortStateIndex, ShortState> sLarge = large(id);
        assertTrue(small.removeAll(sSmall));
        assertFalse(small.containsAll(sSmall));
        assertTrue(small.isDisjoint(sSmall));
        assertTrue(large.removeAll(sLarge));
        assertFalse(large.containsAll(sLarge));
        assertTrue(large.isDisjoint(sLarge));
    }

    private void removeAllNotExisting(int... id) {
        StateSet<ShortStateIndex, ShortState> sSmall = small(id);
        StateSet<ShortStateIndex, ShortState> sLarge = large(id);
        assertFalse(small.removeAll(sSmall));
        assertFalse(small.containsAll(sSmall));
        assertTrue(small.isDisjoint(sSmall));
        assertFalse(large.removeAll(sLarge));
        assertFalse(large.containsAll(sLarge));
        assertTrue(large.isDisjoint(sLarge));
    }

    private void checkIteratorsEqual(int size) {
        assertEquals(small.size(), size);
        assertEquals(large.size(), size);
        Iterator<ShortState> itSmall = small.iterator();
        Iterator<ShortState> itLarge = large.iterator();
        for (int i = 0; i < size; i++) {
            assertTrue(itSmall.hasNext());
            assertTrue(itLarge.hasNext());
            assertEquals(itSmall.next(), itLarge.next());
        }
    }

    @Test
    public void addRemove() {
        for (int stride = 1; stride < 16; stride++) {
            for (int i = 0; i < MAX_SMALL_STATE_INDEX_SIZE; i += stride) {
                addNew(state(i));
                checkIteratorsEqual((i / stride) + 1);
            }
            for (int i = 0; i < MAX_SMALL_STATE_INDEX_SIZE; i += stride) {
                addExisting(state(i));
            }
            for (int i = 0; i < MAX_SMALL_STATE_INDEX_SIZE; i += stride) {
                removeExisting(state(i));
            }
            for (int i = 0; i < MAX_SMALL_STATE_INDEX_SIZE; i += stride) {
                removeNotExisting(state(i));
            }
        }
    }

    @Test
    public void addAllRemoveAll() {
        for (int stride = 2; stride < 16; stride++) {
            for (int i = 0; i < MAX_SMALL_STATE_INDEX_SIZE - 1; i += stride) {
                addAllNew(i, i + 1);
                checkIteratorsEqual(((i / stride) + 1) * 2);
            }
            for (int i = 0; i < MAX_SMALL_STATE_INDEX_SIZE - 1; i += stride) {
                addAllExisting(i, i + 1);
            }
            for (int i = 0; i < MAX_SMALL_STATE_INDEX_SIZE - 1; i += stride) {
                removeAllExisting(i, i + 1);
            }
            for (int i = 0; i < MAX_SMALL_STATE_INDEX_SIZE - 1; i += stride) {
                removeAllNotExisting(i, i + 1);
            }
        }
    }

    private static class ShortState {

        private final short id;

        ShortState(int id) {
            this.id = (short) id;
        }

        public short getId() {
            return id;
        }

        @Override
        public String toString() {
            return "s" + id;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ShortState && id == ((ShortState) obj).id;
        }
    }

    private static final class ShortStateIndex implements StateIndex<ShortState> {

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
        public int getId(ShortState state) {
            return state.getId();
        }

        @Override
        public ShortState getState(int id) {
            return index[id];
        }
    }
}
