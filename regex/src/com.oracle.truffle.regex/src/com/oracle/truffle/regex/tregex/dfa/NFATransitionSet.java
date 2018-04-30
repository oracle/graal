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
package com.oracle.truffle.regex.tregex.dfa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * This class represents a set of NFA transitions leading to a set of NFA states. The uniqueness of
 * one {@link NFAStateTransition} inside this class is defined by its target {@link NFAState} as
 * returned by {@link NFAStateTransition#getTarget(boolean)}, where
 * {@link NFATransitionSet#isForward()} is used as the parameter <code>forward</code>. If
 * {@link #isPrioritySensitive()} is set, the transition set becomes sensitive to insertion order,
 * so sets with the same elements are no longer considered equal if their element insertion order
 * differs. The insertion reflects the priority of every transition, where elements added earlier
 * are of higher priority. {@link #iterator()} will always reflect insertion order.
 * {@link #isPrioritySensitive()} will also cause the transition set to stop accepting new elements
 * as soon as a transition to a final state was added (more specific: a transition that leads to a
 * state that has another transition to a final state, as denoted by
 * {@link NFAState#hasTransitionToAnchoredFinalState(boolean)} and
 * {@link NFAState#hasTransitionToUnAnchoredFinalState(boolean)}. The transition to the final state
 * in the NFA does not consume a character, so it is treated as part of the transition leading to
 * its source state in the DFA generator. Example: NFA state 1 leads to NFA state 2, which leads to
 * final NFA state 3. The DFA generator will treat the transition from state 1 to state 2 as
 * "transition to final state" and incorporate the transition from state 2 to state 3 in it.). This
 * is necessary for correct DFA generation, since after reaching a final state, the DFA shall drop
 * all NFA states whose priority is lower than that of the final state. Since in this set insertion
 * order reflects priority, we do just that implicitly by dropping all add operations after a
 * (transition to) a final state was added.
 *
 * @see DFAGenerator
 * @see DFAStateTransitionBuilder
 */
public final class NFATransitionSet implements Iterable<NFAStateTransition>, JsonConvertible {

    private static final byte FLAG_FORWARD = 1;
    private static final byte FLAG_PRIORITY_SENSITIVE = 1 << 1;
    private static final byte FLAG_LEADS_TO_FINAL_STATE = 1 << 2;
    private static final byte FLAG_HASH_COMPUTED = 1 << 3;
    private static final byte MASK_NO_ADD = FLAG_PRIORITY_SENSITIVE | FLAG_LEADS_TO_FINAL_STATE;

    private final NFA nfa;
    private final NFAStateSet stateSet;
    private byte flags = 0;
    private short[] transitions;
    private short size = 0;
    private int cachedHash;

    private NFATransitionSet(NFA nfa, boolean forward, boolean prioritySensitive, short[] transitions) {
        this.nfa = nfa;
        stateSet = new NFAStateSet(nfa);
        if (forward) {
            flags |= FLAG_FORWARD;
        }
        if (prioritySensitive) {
            flags |= FLAG_PRIORITY_SENSITIVE;
        }
        this.transitions = transitions;
    }

    private NFATransitionSet(NFATransitionSet copy, int capacity) {
        this.nfa = copy.nfa;
        this.stateSet = copy.stateSet.copy();
        this.flags = copy.flags;
        this.transitions = new short[capacity];
        System.arraycopy(copy.transitions, 0, this.transitions, 0, copy.size);
        this.size = copy.size;
        this.cachedHash = copy.cachedHash;
    }

    public static NFATransitionSet create(NFA nfa, boolean forward, boolean prioritySensitive) {
        return new NFATransitionSet(nfa, forward, prioritySensitive, new short[20]);
    }

    public static NFATransitionSet create(NFA nfa, boolean forward, boolean prioritySensitive, NFAStateTransition transition) {
        NFATransitionSet transitionSet = new NFATransitionSet(nfa, forward, prioritySensitive, new short[20]);
        transitionSet.add(transition);
        return transitionSet;
    }

    public static NFATransitionSet create(NFA nfa, boolean forward, boolean prioritySensitive, List<NFAStateTransition> transitions) {
        NFATransitionSet transitionSet = new NFATransitionSet(nfa, forward, prioritySensitive, new short[(transitions.size() * 2) < 20 ? 20 : transitions.size() * 2]);
        transitionSet.addAll(transitions);
        return transitionSet;
    }

    public static NFATransitionSet create(NFA nfa, boolean forward, boolean prioritySensitive, NFAStateTransition... transitions) {
        NFATransitionSet transitionSet = new NFATransitionSet(nfa, forward, prioritySensitive, new short[(transitions.length * 2) < 20 ? 20 : transitions.length * 2]);
        transitionSet.addAll(transitions);
        return transitionSet;
    }

    public NFATransitionSet createMerged(NFATransitionSet otherTargetState) {
        NFATransitionSet merged = new NFATransitionSet(this, size() + otherTargetState.size() + 20);
        merged.addAll(otherTargetState);
        return merged;
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

    public boolean isPrioritySensitive() {
        return isFlagSet(FLAG_PRIORITY_SENSITIVE);
    }

    private boolean isHashComputed() {
        return isFlagSet(FLAG_HASH_COMPUTED);
    }

    private void setLeadsToFinalState() {
        setFlag(FLAG_LEADS_TO_FINAL_STATE);
    }

    private void setHashComputed() {
        setFlag(FLAG_HASH_COMPUTED);
    }

    private void clearHashComputed() {
        clearFlag(FLAG_HASH_COMPUTED);
    }

    /**
     * If the transition set is priority sensitive, it must stop accepting new elements after a
     * transition to a final state was added. This method checks both of these conditions at once.
     *
     * @return <code>true</code> if no more elements are allowed to be added to the set.
     */
    private boolean addNotAllowed() {
        return (flags & MASK_NO_ADD) == MASK_NO_ADD;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    private NFAStateTransition getTransition(int i) {
        return nfa.getTransitions()[transitions[i]];
    }

    /**
     * Add a {@link NFAStateTransition} to this set. Note that this set will refuse to add the
     * transition if it already contains a transition leading to the same <em>target state</em> as
     * denoted by {@link NFAStateTransition#getTarget(boolean)}, where {@link #isForward()} is used
     * for the <code>forward</code> parameter. The new element will also be refused if
     * {@link #isPrioritySensitive()} is <code>true</code> and the transition already contains a
     * transition to a final state (see JavaDoc of this class).
     */
    public void add(NFAStateTransition transition) {
        if (addNotAllowed()) {
            return;
        }
        doAdd(transition);
    }

    private void doAdd(NFAStateTransition transition) {
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

    private void ensureCapacity(int newSize) {
        if (newSize < transitions.length) {
            return;
        }
        int newLength = transitions.length * 2;
        while (newLength < newSize) {
            newLength *= 2;
        }
        short[] newTransitions = new short[newLength];
        System.arraycopy(transitions, 0, newTransitions, 0, size);
        transitions = newTransitions;
    }

    private void appendTransition(NFAStateTransition transition) {
        ensureCapacity(size + 1);
        assert nfa.getTransitions()[transition.getId()] == transition;
        transitions[size] = transition.getId();
        size++;
        clearHashComputed();
    }

    /**
     * Add all transitions contained in the given transition set. Note this set's special behavior
     * on add-operations as documented in {@link #add(NFAStateTransition)}.
     *
     * @see #add(NFAStateTransition)
     */
    public void addAll(NFATransitionSet other) {
        if (addNotAllowed()) {
            return;
        }
        ensureCapacity(size + other.size);
        for (int i = 0; i < other.size; i++) {
            if (addNotAllowed()) {
                return;
            }
            doAdd(other.getTransition(i));
        }
    }

    /**
     * Add all transitions contained in the given list. Note this set's special behavior on
     * add-operations as documented in {@link #add(NFAStateTransition)}.
     *
     * @see #add(NFAStateTransition)
     */
    public void addAll(List<NFAStateTransition> addTransitions) {
        if (addNotAllowed()) {
            return;
        }
        ensureCapacity(size + addTransitions.size());
        for (NFAStateTransition t : addTransitions) {
            if (addNotAllowed()) {
                return;
            }
            doAdd(t);
        }
    }

    /**
     * Add all transitions contained in the given list. Note this set's special behavior on
     * add-operations as documented in {@link #add(NFAStateTransition)}.
     *
     * @see #add(NFAStateTransition)
     */
    public void addAll(NFAStateTransition... addTransitions) {
        if (addNotAllowed()) {
            return;
        }
        ensureCapacity(size + addTransitions.length);
        for (NFAStateTransition t : addTransitions) {
            if (addNotAllowed()) {
                return;
            }
            doAdd(t);
        }
    }

    /**
     * Returns the hash code value for this set.
     *
     * Note that {@link #isPrioritySensitive()} affects the hash calculation. If
     * {@link #isPrioritySensitive()} is true, the hash is calculated using the target states (
     * {@link NFAStateTransition#getTarget(boolean)} of all {@link NFAStateTransition}s in this set
     * <em>in insertion order</em>. Otherwise, the hash is equal to the hashcode of a
     * {@link NFAStateSet} containing all target states of the transitions in this transition set.
     */
    @Override
    public int hashCode() {
        if (!isHashComputed()) {
            if (isPrioritySensitive()) {
                cachedHash = 1;
                for (int i = 0; i < size; i++) {
                    cachedHash = 31 * cachedHash + getTransition(i).getTarget(isForward()).hashCode();
                }
            } else {
                cachedHash = stateSetHashCode(stateSet);
            }
            setHashComputed();
        }
        return cachedHash;
    }

    private static int stateSetHashCode(NFAStateSet nfaStates) {
        if (nfaStates == null) {
            return 0;
        }
        return nfaStates.hashCode();
    }

    /**
     * Checks if the set is equal to another given set. Note that the definition of equality in this
     * class changes if {@link #isPrioritySensitive()} is changed: If {@link #isPrioritySensitive()}
     * is <code>false</code>, it is equal to another {@link NFATransitionSet} if it has the same set
     * of <em>target states</em>. Otherwise, the two sets are equal only if both have the same
     * target states <em>in the same order</em> - This means that if both sets are iterated in
     * <em>insertion order</em>, their transitions must yield the same sequence of target states (as
     * returned by {@link NFAStateTransition#getTarget(boolean)}).
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof NFATransitionSet)) {
            return false;
        }
        NFATransitionSet o = (NFATransitionSet) obj;
        if (isPrioritySensitive()) {
            if (size() != o.size()) {
                return false;
            }
            for (int i = 0; i < size(); i++) {
                if (!getTransition(i).getTarget(isForward()).equals(o.getTransition(i).getTarget(isForward()))) {
                    return false;
                }
            }
            return true;
        } else {
            return stateSetEquals(stateSet, o.stateSet);
        }
    }

    private static boolean stateSetEquals(NFAStateSet a, NFAStateSet b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
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
