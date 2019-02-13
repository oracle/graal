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
package com.oracle.truffle.regex.tregex.automaton;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import java.util.Collection;
import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A specialized set for sequentially indexed objects. The objects stored in this set must implement
 * {@link IndexedState}, they are referenced by {@link IndexedState#getId()}. Up to a size of four,
 * this set uses a list of {@code short} values compressed into one {@code long} to store the
 * indices. When the set grows larger than four elements, the indices are stored in a
 * {@link StateSetBackingSet}, which can be set in the constructor.
 */
public class StateSet<S extends IndexedState> implements Set<S>, Iterable<S> {

    private static final int SWITCH_TO_BACKING_SET_THRESHOLD = 4;

    private static final byte FLAG_HASH_COMPUTED = 1;
    private static final byte FLAG_STATE_LIST_SORTED = 1 << 1;

    private static final long SHORT_MASK = 0xffff;

    private final StateIndex<? super S> stateIndex;
    private final StateSetBackingSet backingSet;
    private byte flags = 0;
    private int size = 0;
    private long stateList = 0;
    private int cachedHash;

    public StateSet(StateIndex<? super S> stateIndex, StateSetBackingSet backingSet) {
        this.stateIndex = stateIndex;
        this.backingSet = backingSet;
    }

    public StateSet(StateIndex<? super S> stateIndex) {
        this(stateIndex, new StateSetBackingBitSet());
    }

    protected StateSet(StateSet<S> copy) {
        this.stateIndex = copy.stateIndex;
        this.flags = copy.flags;
        this.size = copy.size;
        this.backingSet = copy.backingSet.copy();
        this.stateList = copy.stateList;
        this.cachedHash = copy.cachedHash;
    }

    public StateSet<S> copy() {
        return new StateSet<>(this);
    }

    public StateIndex<? super S> getStateIndex() {
        return stateIndex;
    }

    private void checkSwitchToBitSet(int newSize) {
        if (!useBackingSet() && newSize > SWITCH_TO_BACKING_SET_THRESHOLD) {
            backingSet.create(stateIndex.getNumberOfStates());
            for (int i = 0; i < size(); i++) {
                backingSet.addBatch(stateListElement(stateList));
                stateList >>>= Short.SIZE;
            }
            backingSet.addBatchFinish();
        }
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
        return backingSet.isActive();
    }

    private void setFlag(byte flag, boolean value) {
        if (value) {
            flags |= flag;
        } else {
            flags &= ~flag;
        }
    }

    private boolean isFlagSet(byte flag) {
        return (flags & flag) != 0;
    }

    private boolean isStateListSorted() {
        return isFlagSet(FLAG_STATE_LIST_SORTED);
    }

    private void setStateListSorted(boolean stateListSorted) {
        setFlag(FLAG_STATE_LIST_SORTED, stateListSorted);
    }

    private boolean isHashComputed() {
        return isFlagSet(FLAG_HASH_COMPUTED);
    }

    private void setHashComputed(boolean hashComputed) {
        setFlag(FLAG_HASH_COMPUTED, hashComputed);
    }

    private void increaseSize() {
        size++;
        setHashComputed(false);
    }

    private void decreaseSize() {
        size--;
        setHashComputed(false);
    }

    private static short stateListElement(long stateList, int i) {
        return stateListElement(stateList >>> (Short.SIZE * i));
    }

    private static short stateListElement(long stateList) {
        return (short) (stateList & SHORT_MASK);
    }

    private void setStateListElement(int index, int value) {
        stateList = (stateList & ~(SHORT_MASK << (Short.SIZE * index))) | ((long) value << (Short.SIZE * index));
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(Object state) {
        return contains(((S) state).getId());
    }

    private boolean contains(short id) {
        if (useBackingSet()) {
            return backingSet.contains(id);
        }
        long sl = stateList;
        for (int i = 0; i < size(); i++) {
            if (stateListElement(sl) == id) {
                return true;
            }
            sl >>>= Short.SIZE;
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean add(S state) {
        return add(state.getId());
    }

    private boolean add(short id) {
        if (useBackingSet()) {
            if (backingSet.add(id)) {
                increaseSize();
                return true;
            }
            return false;
        } else {
            if (contains(id)) {
                return false;
            }
            checkSwitchToBitSet(size() + 1);
            if (useBackingSet()) {
                backingSet.add(id);
                increaseSize();
            } else {
                stateList = (stateList << Short.SIZE) | id;
                increaseSize();
                setStateListSorted(false);
            }
            return true;
        }
    }

    @Override
    public boolean addAll(Collection<? extends S> c) {
        checkSwitchToBitSet(size() + c.size());
        boolean ret = false;
        for (S s : c) {
            ret |= add(s);
        }
        return ret;
    }

    public void addBatch(S state) {
        assert !contains(state);
        if (useBackingSet()) {
            backingSet.addBatch(state.getId());
            increaseSize();
        } else {
            add(state);
        }
    }

    public void addBatchFinish() {
        if (useBackingSet()) {
            backingSet.addBatchFinish();
        }
    }

    public void replace(S oldState, S newState) {
        assert contains(oldState) && !contains(newState);
        if (useBackingSet()) {
            backingSet.replace(oldState.getId(), newState.getId());
            setHashComputed(false);
        } else {
            remove(oldState);
            add(newState);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean remove(Object o) {
        return remove(((S) o).getId());
    }

    private boolean remove(short id) {
        if (useBackingSet()) {
            if (backingSet.remove(id)) {
                decreaseSize();
                return true;
            }
            return false;
        } else {
            long sl = stateList;
            for (int i = 0; i < size(); i++) {
                if (stateListElement(sl) == id) {
                    removeStateListElement(i);
                    decreaseSize();
                    return true;
                }
                sl >>>= Short.SIZE;
            }
            return false;
        }
    }

    private void removeStateListElement(int i) {
        switch (i) {
            case 0:
                stateList >>>= Short.SIZE;
                break;
            case 1:
                stateList = ((stateList >>> Short.SIZE) & 0xffff_ffff_0000L) | (stateList & 0xffffL);
                break;
            case 2:
                stateList = ((stateList >>> Short.SIZE) & 0xffff_0000_0000L) | (stateList & 0xffff_ffffL);
                break;
            case 3:
                stateList &= 0xffff_ffff_ffffL;
                break;
            default:
                throw new IllegalArgumentException("stateList cannot be larger than 4 elements!");
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean ret = false;
        for (Object s : c) {
            ret |= remove(s);
        }
        return ret;
    }

    @Override
    public void clear() {
        if (useBackingSet()) {
            backingSet.clear();
        } else {
            stateList = 0;
        }
        size = 0;
        setHashComputed(false);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean isDisjoint(StateSet<? extends S> other) {
        if (other.useBackingSet()) {
            if (useBackingSet()) {
                return backingSet.isDisjoint(other.backingSet);
            }
            long sl = stateList;
            for (int i = 0; i < size(); i++) {
                if (other.contains(stateListElement(sl))) {
                    return false;
                }
                sl >>>= Short.SIZE;
            }
            return true;
        }
        long sl = other.stateList;
        for (int i = 0; i < other.size(); i++) {
            if (contains(stateListElement(sl))) {
                return false;
            }
            sl >>>= Short.SIZE;
        }
        return true;
    }

    private void requireStateListSorted() {
        if (!isStateListSorted()) {
            for (int i = 1; i < size(); i++) {
                int sli = stateListElement(stateList, i);
                int j;
                for (j = i - 1; j >= 0; j--) {
                    int slj = stateListElement(stateList, j);
                    if (slj <= sli) {
                        break;
                    }
                    setStateListElement(j + 1, slj);
                }
                setStateListElement(j + 1, sli);
            }
            setStateListSorted(true);
        }
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
        if (!isHashComputed()) {
            if (useBackingSet()) {
                if (size <= SWITCH_TO_BACKING_SET_THRESHOLD) {
                    long hashStateList = 0;
                    int shift = 0;
                    for (int i : backingSet) {
                        hashStateList |= (long) i << shift;
                        shift += Short.SIZE;
                    }
                    cachedHash = Long.hashCode(hashStateList);
                } else {
                    cachedHash = backingSet.hashCode();
                }
            } else {
                if (size == 0) {
                    cachedHash = 0;
                } else {
                    requireStateListSorted();
                    if (size < SWITCH_TO_BACKING_SET_THRESHOLD) {
                        // clear unused slots in stateList
                        stateList &= (~0L >>> (Short.SIZE * (SWITCH_TO_BACKING_SET_THRESHOLD - size)));
                    }
                    cachedHash = Long.hashCode(stateList);
                }
            }
            setHashComputed(true);
        }
        return cachedHash;
    }

    /**
     * Compares the specified object with this set for equality.
     * 
     * Note that unlike other {@link Set} s, {@link StateSet}s are only equal to other
     * {@link StateSet}s.
     * 
     * @see Set#equals(Object)
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StateSet)) {
            return false;
        }
        StateSet<S> o = (StateSet<S>) obj;
        if (size() != o.size()) {
            return false;
        }
        if (useBackingSet() && o.useBackingSet()) {
            return backingSet.equals(o.backingSet);
        }
        if (useBackingSet() != o.useBackingSet()) {
            PrimitiveIterator.OfInt thisIterator = intIterator();
            PrimitiveIterator.OfInt otherIterator = o.intIterator();
            while (thisIterator.hasNext()) {
                if (thisIterator.nextInt() != otherIterator.nextInt()) {
                    return false;
                }
            }
            return true;
        }
        assert !useBackingSet() && !o.useBackingSet();
        requireStateListSorted();
        o.requireStateListSorted();
        return stateList == o.stateList;
    }

    protected PrimitiveIterator.OfInt intIterator() {
        if (useBackingSet()) {
            return backingSet.iterator();
        }
        requireStateListSorted();
        return new StateListIterator();
    }

    @Override
    public Iterator<S> iterator() {
        return new StateSetIterator(stateIndex, intIterator());
    }

    @Override
    public Object[] toArray() {
        Object[] ret = new Object[size()];
        int i = 0;
        for (S s : this) {
            ret[i++] = s;
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a) {
        T[] r = a.length >= size() ? a : (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size());
        int i = 0;
        for (S s : this) {
            r[i++] = (T) s;
        }
        return r;
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
            StateSet.this.removeStateListElement(--i);
        }
    }

    private final class StateSetIterator implements Iterator<S> {

        private final StateIndex<? super S> stateIndex;
        private final PrimitiveIterator.OfInt intIterator;

        private StateSetIterator(StateIndex<? super S> stateIndex, PrimitiveIterator.OfInt intIterator) {
            this.stateIndex = stateIndex;
            this.intIterator = intIterator;
        }

        @Override
        public boolean hasNext() {
            return intIterator.hasNext();
        }

        @SuppressWarnings("unchecked")
        @Override
        public S next() {
            return (S) stateIndex.getState(intIterator.nextInt());
        }

        @Override
        public void remove() {
            intIterator.remove();
            decreaseSize();
        }
    }

    @TruffleBoundary
    @Override
    public Stream<S> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return stream().map(S::toString).collect(Collectors.joining(",", "{", "}"));
    }
}
