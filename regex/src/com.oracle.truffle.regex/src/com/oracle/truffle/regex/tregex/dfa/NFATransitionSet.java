/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.dfa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.automaton.TransitionSet;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * This class represents a set of NFA transitions leading to a set of NFA states. The uniqueness of
 * one {@link NFAStateTransition} inside this class is defined by its target {@link NFAState} as
 * returned by {@link NFAStateTransition#getTarget(boolean)}, where
 * {@link NFATransitionSet#isForward()} is used as the parameter {@code forward}.
 * {@link #iterator()} reflects insertion order.
 *
 * @see DFAGenerator
 * @see DFAStateTransitionBuilder
 */
public class NFATransitionSet implements TransitionSet, Iterable<NFAStateTransition> {

    private static final byte FLAG_FORWARD = 1;
    private static final byte FLAG_LEADS_TO_FINAL_STATE = 1 << 1;
    private static final byte FLAG_HASH_COMPUTED = 1 << 2;

    private final NFA nfa;
    private final NFAStateSet stateSet;
    private byte flags = 0;
    private short[] transitions;
    private short size = 0;
    private int cachedHash;

    NFATransitionSet(NFA nfa, boolean forward) {
        this.nfa = nfa;
        stateSet = new NFAStateSet(nfa);
        if (forward) {
            flags |= FLAG_FORWARD;
        }
        this.transitions = new short[20];
    }

    NFATransitionSet(NFATransitionSet copy, int capacity) {
        this.nfa = copy.nfa;
        this.stateSet = copy.stateSet.copy();
        this.flags = copy.flags;
        this.transitions = new short[capacity];
        System.arraycopy(copy.transitions, 0, this.transitions, 0, copy.size);
        this.size = copy.size;
        this.cachedHash = copy.cachedHash;
    }

    public static NFATransitionSet create(NFA nfa, boolean forward, NFAStateTransition transition) {
        NFATransitionSet transitionSet = new NFATransitionSet(nfa, forward);
        transitionSet.add(transition);
        return transitionSet;
    }

    @Override
    public NFATransitionSet createMerged(TransitionSet other) {
        NFATransitionSet merged = new NFATransitionSet(this, mergedInitialCapacity((NFATransitionSet) other));
        merged.addAll(other);
        return merged;
    }

    int mergedInitialCapacity(NFATransitionSet other) {
        return size() + other.size() + 20;
    }

    private boolean isFlagSet(byte flag) {
        return (flags & flag) != 0;
    }

    private void setFlag(byte flag) {
        flags |= flag;
    }

    private void clearFlag(byte flag) {
        flags &= ~flag;
    }

    public boolean isForward() {
        return isFlagSet(FLAG_FORWARD);
    }

    /**
     * Returns {@code true} if the set contains a transition to a NFA state that in turn contains a
     * transition to an un-anchored NFA final state as denoted by
     * {@link NFAState#hasTransitionToUnAnchoredFinalState(boolean)}, where {@link #isForward()} is
     * used as the {@code forward} parameter.
     */
    public boolean leadsToFinalState() {
        return isFlagSet(FLAG_LEADS_TO_FINAL_STATE);
    }

    private void setLeadsToFinalState() {
        setFlag(FLAG_LEADS_TO_FINAL_STATE);
    }

    boolean isHashComputed() {
        return isFlagSet(FLAG_HASH_COMPUTED);
    }

    void setHashComputed() {
        setFlag(FLAG_HASH_COMPUTED);
    }

    private void clearHashComputed() {
        clearFlag(FLAG_HASH_COMPUTED);
    }

    int getCachedHash() {
        return cachedHash;
    }

    void setCachedHash(int cachedHash) {
        this.cachedHash = cachedHash;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    NFAStateTransition getTransition(int i) {
        return nfa.getTransitions()[transitions[i]];
    }

    /**
     * Add a {@link NFAStateTransition} to this set. Note that this set will refuse to add the
     * transition if it already contains a transition leading to the same <em>target state</em> as
     * denoted by {@link NFAStateTransition#getTarget(boolean)}, where {@link #isForward()} is used
     * as the {@code forward} parameter.
     */
    public void add(NFAStateTransition transition) {
        doAdd(transition);
    }

    void doAdd(NFAStateTransition transition) {
        NFAState target = transition.getTarget(isForward());
        assert !target.isFinalState(isForward());
        if (!stateSet.add(target)) {
            return;
        }
        if (target.hasTransitionToUnAnchoredFinalState(isForward())) {
            setLeadsToFinalState();
        }
        appendTransition(transition);
    }

    void ensureCapacity(int newSize) {
        if (newSize < transitions.length) {
            return;
        }
        int newLength = transitions.length * 2;
        while (newLength < newSize) {
            newLength *= 2;
        }
        transitions = Arrays.copyOf(transitions, newLength);
    }

    private void appendTransition(NFAStateTransition transition) {
        ensureCapacity(size + 1);
        assert nfa.getTransitions()[transition.getId()] == transition;
        transitions[size] = transition.getId();
        size++;
        clearHashComputed();
    }

    /**
     * Add all transitions contained in the given transition set.
     *
     * @see #add(NFAStateTransition)
     */
    @Override
    public void addAll(TransitionSet other) {
        NFATransitionSet o = (NFATransitionSet) other;
        ensureCapacity(size + o.size);
        for (int i = 0; i < o.size; i++) {
            doAdd(o.getTransition(i));
        }
    }

    /**
     * Returns the hash code value for this set.
     *
     * The hash is equal to the hashcode of a {@link NFAStateSet} containing all <em>target
     * states</em> of the transitions in this transition set.
     */
    @Override
    public int hashCode() {
        if (!isHashComputed()) {
            cachedHash = Objects.hashCode(stateSet);
            setHashComputed();
        }
        return cachedHash;
    }

    /**
     * Checks if the set is equal to another given set. Two sets are considered equal only if both
     * are {@link NFATransitionSet}s and have the same set of <em>target states</em> (as returned by
     * {@link NFAStateTransition#getTarget(boolean)}).
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof NFATransitionSet)) {
            return false;
        }
        assert !(obj instanceof PrioritySensitiveNFATransitionSet) : "Do not mix NFATransitionSet and PrioritySensitiveNFATransitionSet!";
        return Objects.equals(stateSet, ((NFATransitionSet) obj).stateSet);
    }

    /**
     * Returns an iterator that will yield the elements contained in this set <em>in insertion
     * order</em>.
     */
    @Override
    public Iterator<NFAStateTransition> iterator() {
        return new NFATransitionSetIterator(nfa, transitions, size);
    }

    private static final class NFATransitionSetIterator implements Iterator<NFAStateTransition> {

        private final NFA nfa;
        private final short[] transitions;
        private final short size;
        private short i = 0;

        private NFATransitionSetIterator(NFA nfa, short[] transitions, short size) {
            this.nfa = nfa;
            this.transitions = transitions;
            this.size = size;
        }

        @Override
        public boolean hasNext() {
            return i < size;
        }

        @Override
        public NFAStateTransition next() {
            return nfa.getTransitions()[transitions[i++]];
        }
    }

    /**
     * Returns a stream that will yield the elements contained in this set <em>in insertion
     * order</em>.
     */
    @TruffleBoundary
    public Stream<NFAStateTransition> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return stream().map(x -> x.getTarget(isForward()).idToString()).collect(Collectors.joining(",", "{", "}"));
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.array(this);
    }
}
