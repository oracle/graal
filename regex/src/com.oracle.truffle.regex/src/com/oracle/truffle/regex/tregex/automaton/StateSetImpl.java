/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collection;
import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.util.BitSets;

/**
 * A specialized set for sequentially indexed objects. Up to a size of four, this set uses a list of
 * {@code short} values compressed into one {@code long} to store the indices. When the set grows
 * larger than four elements, the indices are stored in a bit set.
 */
final class StateSetImpl<SI extends StateIndex<? super S>, S> implements StateSet<SI, S> {

    private static final int ELEMENT_SIZE = Integer.SIZE;
    private static final int SWITCH_TO_BACKING_SET_THRESHOLD = 2;
    private static final long ELEMENT_MASK = 0xffffffff;

    private final SI stateIndex;
    private long[] backingSet;
    private int size = 0;
    private long stateList = 0;

    StateSetImpl(SI stateIndex) {
        this.stateIndex = stateIndex;
    }

    StateSetImpl(StateSetImpl<SI, S> copy) {
        this.stateIndex = copy.stateIndex;
        this.size = copy.size;
        this.backingSet = copy.backingSet == null ? null : Arrays.copyOf(copy.backingSet, copy.backingSet.length);
        this.stateList = copy.stateList;
    }

    @Override
    public SI getStateIndex() {
        return stateIndex;
    }

    @Override
    public StateSet<SI, S> copy() {
        return new StateSetImpl<>(this);
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public int size() {
        return size;
    }

    private boolean useBackingSet() {
        return size > SWITCH_TO_BACKING_SET_THRESHOLD;
    }

    private static int stateListElement(long stateList, int i) {
        return stateListElement(stateList >>> (ELEMENT_SIZE * i));
    }

    private static int stateListElement(long stateList) {
        return (int) (stateList & ELEMENT_MASK);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(Object o) {
        return contains(stateIndex.getId((S) o));
    }

    @Override
    public boolean add(S e) {
        return add(stateIndex.getId(e));
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean remove(Object o) {
        return remove(stateIndex.getId((S) o));
    }

    private boolean contains(int id) {
        if (useBackingSet()) {
            return BitSets.get(backingSet, id);
        }
        long sl = stateList;
        for (int i = 0; i < size(); i++) {
            if (stateListElement(sl) == id) {
                return true;
            }
            sl >>>= ELEMENT_SIZE;
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        if (c instanceof StateSetImpl) {
            StateSetImpl<?, ?> o = (StateSetImpl<?, ?>) c;
            if (useBackingSet() && o.useBackingSet()) {
                return BitSets.contains(backingSet, o.backingSet);
            }
        }
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    private boolean add(int id) {
        if (useBackingSet()) {
            if (BitSets.add(backingSet, id)) {
                size++;
                return true;
            }
            return false;
        } else {
            long sl = stateList;
            int i;
            for (i = 0; i < size(); i++) {
                if (stateListElement(sl) == id) {
                    return false;
                }
                if (stateListElement(sl) > id) {
                    break;
                }
                sl >>>= ELEMENT_SIZE;
            }
            if (size() == SWITCH_TO_BACKING_SET_THRESHOLD) {
                if (backingSet == null) {
                    backingSet = BitSets.createBitSetArray(stateIndex.getNumberOfStates());
                }
                for (int j = 0; j < size(); j++) {
                    BitSets.set(backingSet, stateListElement(stateList));
                    stateList >>>= ELEMENT_SIZE;
                }
            }
            size++;
            if (useBackingSet()) {
                BitSets.set(backingSet, id);
            } else {
                stateList = (((sl << ELEMENT_SIZE) | id) << (i * ELEMENT_SIZE)) | (stateList & ~((~0L) << i * ELEMENT_SIZE));
            }
            return true;
        }
    }

    @Override
    public boolean addAll(Collection<? extends S> c) {
        if (c instanceof StateSetImpl) {
            StateSetImpl<?, ?> o = (StateSetImpl<?, ?>) c;
            if (useBackingSet() && o.useBackingSet()) {
                int oldSize = size;
                size = BitSets.addAll(backingSet, o.backingSet);
                return size != oldSize;
            }
        }
        boolean ret = false;
        for (S s : c) {
            ret |= add(s);
        }
        return ret;
    }

    private boolean remove(int id) {
        if (useBackingSet()) {
            if (BitSets.remove(backingSet, id)) {
                size--;
                if (size == SWITCH_TO_BACKING_SET_THRESHOLD) {
                    assert stateList == 0;
                    int shift = 0;
                    PrimitiveIterator.OfInt it = BitSets.iterator(backingSet);
                    while (it.hasNext()) {
                        stateList |= (long) it.nextInt() << shift;
                        shift += ELEMENT_SIZE;
                    }
                    BitSets.clear(backingSet);
                }
                return true;
            }
            return false;
        } else {
            long sl = stateList;
            for (int i = 0; i < size(); i++) {
                if (stateListElement(sl) == id) {
                    removeStateListElement(i);
                    size--;
                    return true;
                }
                sl >>>= ELEMENT_SIZE;
            }
            return false;
        }
    }

    private void removeStateListElement(int i) {
        // clears all elements < i
        long maskClrLo = (~0L) << (i * ELEMENT_SIZE);
        // clears all elements >= i;
        long maskClrHi = ~maskClrLo;
        stateList = ((stateList >>> ELEMENT_SIZE) & maskClrLo) | (stateList & maskClrHi);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        if (c instanceof StateSetImpl) {
            StateSetImpl<?, ?> o = (StateSetImpl<?, ?>) c;
            if (useBackingSet() && o.useBackingSet()) {
                int oldSize = size;
                size = BitSets.removeAll(backingSet, o.backingSet);
                return size != oldSize;
            }
        }
        boolean ret = false;
        for (Object s : c) {
            ret |= remove(s);
        }
        return ret;
    }

    @Override
    public void clear() {
        stateList = 0;
        if (useBackingSet()) {
            BitSets.clear(backingSet);
        }
        size = 0;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        if (c instanceof StateSetImpl) {
            StateSetImpl<?, ?> o = (StateSetImpl<?, ?>) c;
            if (useBackingSet() && o.useBackingSet()) {
                int oldSize = size;
                size = BitSets.retainAll(backingSet, o.backingSet);
                return size != oldSize;
            }
        }
        boolean ret = false;
        for (S s : this) {
            if (!c.contains(s)) {
                ret |= remove(s);
            }
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean isDisjoint(StateSet<SI, ? extends S> other) {
        StateSetImpl<SI, S> o = (StateSetImpl<SI, S>) other;
        if (o.useBackingSet()) {
            if (useBackingSet()) {
                return BitSets.isDisjoint(backingSet, o.backingSet);
            }
            long sl = stateList;
            for (int i = 0; i < size(); i++) {
                if (o.contains(stateListElement(sl))) {
                    return false;
                }
                sl >>>= ELEMENT_SIZE;
            }
            return true;
        }
        long sl = o.stateList;
        for (int i = 0; i < o.size(); i++) {
            if (contains(stateListElement(sl))) {
                return false;
            }
            sl >>>= ELEMENT_SIZE;
        }
        return true;
    }

    private boolean stateListSorted() {
        int last = -1;
        for (int i = 0; i < size(); i++) {
            if (stateListElement(stateList, i) <= last) {
                return false;
            }
            last = stateListElement(stateList, i);
        }
        for (int i = size(); i < SWITCH_TO_BACKING_SET_THRESHOLD; i++) {
            if (stateListElement(stateList, i) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the hash code value for this set.
     *
     * Note that unlike other {@link Set}s, the hash code value returned by this implementation is
     * <em>not</em> the sum of the hash codes of its elements.
     *
     * @see Set#hashCode()
     */
    @Override
    public int hashCode() {
        if (useBackingSet()) {
            return BitSets.hashCode(backingSet);
        } else {
            assert stateListSorted();
            return Long.hashCode(stateList);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof StateSetImpl) {
            StateSetImpl<SI, S> o = (StateSetImpl<SI, S>) obj;
            if (size() != o.size()) {
                return false;
            }
            assert useBackingSet() == o.useBackingSet();
            if (useBackingSet()) {
                return BitSets.equals(backingSet, o.backingSet);
            }
            assert stateListSorted();
            assert o.stateListSorted();
            return stateList == o.stateList;
        }
        if (!(obj instanceof Set)) {
            return false;
        }
        Set<S> o = (Set<S>) obj;
        return size() == o.size() && containsAll(o);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return defaultToString();
    }

    private PrimitiveIterator.OfInt intIterator() {
        if (useBackingSet()) {
            return BitSets.iterator(backingSet);
        }
        assert stateListSorted();
        return new StateListIterator();
    }

    /**
     * Yields all elements in this set, ordered by their {@link StateIndex#getId(Object) ID}.
     */
    @Override
    public Iterator<S> iterator() {
        return new StateSetIterator(intIterator());
    }

    private final class StateListIterator implements PrimitiveIterator.OfInt {

        private int i;

        @Override
        public int nextInt() {
            return stateListElement(stateList, i++);
        }

        @Override
        public boolean hasNext() {
            return i < size;
        }

        @Override
        public void remove() {
            removeStateListElement(--i);
        }
    }

    private final class StateSetIterator implements Iterator<S> {

        private final PrimitiveIterator.OfInt intIterator;

        private StateSetIterator(PrimitiveIterator.OfInt intIterator) {
            this.intIterator = intIterator;
        }

        @Override
        public boolean hasNext() {
            return intIterator.hasNext();
        }

        @SuppressWarnings("unchecked")
        @Override
        public S next() {
            return (S) getStateIndex().getState(intIterator.nextInt());
        }

        @Override
        public void remove() {
            intIterator.remove();
            size--;
        }
    }
}
