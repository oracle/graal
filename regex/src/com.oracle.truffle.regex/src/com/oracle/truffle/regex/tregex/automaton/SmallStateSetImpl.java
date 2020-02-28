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

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Specialized implementation of {@link StateSet} for {@link StateIndex state indices} with less
 * than 65 elements. Uses a single {@code long} value as a bit set.
 */
final class SmallStateSetImpl<S> implements StateSet<S> {

    private final StateIndex<? super S> stateIndex;
    private long set = 0;

    SmallStateSetImpl(StateIndex<? super S> stateIndex) {
        this(stateIndex, 0);
    }

    private SmallStateSetImpl(StateIndex<? super S> stateIndex, long set) {
        assert stateIndex.getNumberOfStates() <= 64;
        this.stateIndex = stateIndex;
        this.set = set;
    }

    @Override
    public StateSet<S> copy() {
        return new SmallStateSetImpl<>(stateIndex, set);
    }

    @Override
    public StateIndex<? super S> getStateIndex() {
        return stateIndex;
    }

    @Override
    public int size() {
        return Long.bitCount(set);
    }

    @Override
    public boolean isEmpty() {
        return set == 0;
    }

    @SuppressWarnings("unchecked")
    private long toBit(Object o) {
        return toBit(stateIndex.getId((S) o));
    }

    private static long toBit(short id) {
        assert 0 <= id && id < 64;
        return 1L << id;
    }

    @Override
    public boolean contains(Object o) {
        return (set & toBit(o)) != 0;
    }

    @Override
    public boolean add(S e) {
        long bit = toBit(e);
        boolean notPresent = (set & bit) == 0;
        set |= bit;
        return notPresent;
    }

    private void removeId(short id) {
        assert (set & toBit(id)) != 0;
        set &= ~toBit(id);
    }

    @Override
    public boolean remove(Object o) {
        long bit = toBit(o);
        boolean present = (set & bit) != 0;
        set &= ~bit;
        return present;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean containsAll(Collection<?> c) {
        if (c instanceof SmallStateSetImpl) {
            return (set & ((SmallStateSetImpl<S>) c).set) == ((SmallStateSetImpl<S>) c).set;
        }
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean addAll(Collection<? extends S> c) {
        long old = set;
        if (c instanceof SmallStateSetImpl) {
            set |= ((SmallStateSetImpl<S>) c).set;
        } else {
            for (S s : c) {
                add(s);
            }
        }
        return old != set;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean retainAll(Collection<?> c) {
        long old = set;
        if (c instanceof SmallStateSetImpl) {
            set &= ((SmallStateSetImpl<S>) c).set;
        } else {
            for (S s : this) {
                if (!c.contains(s)) {
                    remove(s);
                }
            }
        }
        return old != set;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean removeAll(Collection<?> c) {
        long old = set;
        if (c instanceof SmallStateSetImpl) {
            set &= ~((SmallStateSetImpl<S>) c).set;
        } else {
            for (Object o : c) {
                remove(o);
            }
        }
        return old != set;
    }

    @Override
    public void clear() {
        set = 0;
    }

    @Override
    public void addBatch(S state) {
        add(state);
    }

    @Override
    public void addBatchFinish() {
    }

    @Override
    public void replace(S oldState, S newState) {
        remove(oldState);
        add(newState);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean isDisjoint(StateSet<? extends S> other) {
        return (set & ((SmallStateSetImpl<S>) other).set) == 0;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(set);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        return obj instanceof SmallStateSetImpl && ((SmallStateSetImpl<S>) obj).set == set || (obj instanceof Set) && size() == ((Set<S>) obj).size() && containsAll((Set<S>) obj);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return defaultToString();
    }

    /**
     * Yields all elements in this set, ordered by their {@link StateIndex#getId(Object) ID}.
     */
    @Override
    public Iterator<S> iterator() {
        return new SmallStateSetIterator<>(this, set);
    }

    static final class SmallStateSetIterator<T> implements Iterator<T> {

        private final SmallStateSetImpl<T> stateSet;
        private byte bitIndex = 0;
        private long set;

        private SmallStateSetIterator(SmallStateSetImpl<T> stateSet, long set) {
            this.stateSet = stateSet;
            this.set = set;
        }

        @Override
        public boolean hasNext() {
            return set != 0;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T next() {
            assert hasNext();
            int trailingZeros = Long.numberOfTrailingZeros(set);
            set >>>= trailingZeros;
            set >>>= 1;
            bitIndex += trailingZeros;
            return (T) stateSet.getStateIndex().getState(bitIndex++);
        }

        @Override
        public void remove() {
            assert bitIndex > 0;
            stateSet.removeId((short) (bitIndex - 1));
        }
    }
}
