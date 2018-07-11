/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.tregex.automaton.TransitionSet;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;

/**
 * This class is a {@link NFATransitionSet} that is sensitive to insertion order, so sets with the
 * same elements are no longer considered equal if their element insertion order differs. The
 * insertion order reflects the priority of every transition, where elements added earlier are of
 * higher priority. This set will also stop accepting new elements as soon as a transition to a
 * final state was added (more specific: a transition that leads to a state that has another
 * transition to a final state, as denoted by
 * {@link NFAState#hasTransitionToAnchoredFinalState(boolean)} and
 * {@link NFAState#hasTransitionToUnAnchoredFinalState(boolean)}. The transition to the final state
 * in the NFA does not consume a character, so it is treated as part of the transition leading to
 * its source state in the DFA generator. Example: NFA state 1 leads to NFA state 2, which leads to
 * final NFA state 3. The DFA generator will treat the transition from state 1 to state 2 as
 * "transition to final state" and incorporate the transition from state 2 to state 3 in it.). This
 * is necessary for correct DFA generation, since after reaching a final state, the DFA shall drop
 * all NFA states whose priority is lower than that of the final state. Since in this set insertion
 * order reflects priority, we do just that implicitly by dropping all add operations after a
 * transition to a final state was added.
 *
 * @see DFAGenerator
 * @see DFAStateTransitionBuilder
 */
public final class PrioritySensitiveNFATransitionSet extends NFATransitionSet {

    private PrioritySensitiveNFATransitionSet(NFA nfa, boolean forward) {
        super(nfa, forward);
    }

    private PrioritySensitiveNFATransitionSet(NFATransitionSet copy, int capacity) {
        super(copy, capacity);
    }

    public static PrioritySensitiveNFATransitionSet create(NFA nfa, boolean forward, NFAStateTransition transition) {
        PrioritySensitiveNFATransitionSet transitionSet = new PrioritySensitiveNFATransitionSet(nfa, forward);
        transitionSet.add(transition);
        return transitionSet;
    }

    @Override
    public PrioritySensitiveNFATransitionSet createMerged(TransitionSet other) {
        PrioritySensitiveNFATransitionSet merged = new PrioritySensitiveNFATransitionSet(this, mergedInitialCapacity((PrioritySensitiveNFATransitionSet) other));
        merged.addAll(other);
        return merged;
    }

    private boolean addNotAllowed() {
        return leadsToFinalState();
    }

    /**
     * Analogous to {@link NFATransitionSet#add(NFAStateTransition)}, but the new element will also
     * be refused if the transition set already contains a transition to a final state (see JavaDoc
     * of this class), i.e. {@link NFATransitionSet#leadsToFinalState()} is {@code true}.
     */
    @Override
    public void add(NFAStateTransition transition) {
        if (addNotAllowed()) {
            return;
        }
        doAdd(transition);
    }

    /**
     * Add all transitions contained in the given transition set. Note this set's special behavior
     * on add-operations as documented in {@link #add(NFAStateTransition)}.
     *
     * @see #add(NFAStateTransition)
     */
    @Override
    public void addAll(TransitionSet other) {
        PrioritySensitiveNFATransitionSet o = (PrioritySensitiveNFATransitionSet) other;
        if (addNotAllowed()) {
            return;
        }
        ensureCapacity(size() + o.size());
        for (int i = 0; i < o.size(); i++) {
            if (addNotAllowed()) {
                return;
            }
            doAdd(o.getTransition(i));
        }
    }

    /**
     * Returns the hash code value for this set.
     *
     * The hash is calculated using the target states (
     * {@link NFAStateTransition#getTarget(boolean)} of all {@link NFAStateTransition}s in this set
     * <em>in insertion order</em>.
     */
    @Override
    public int hashCode() {
        if (!isHashComputed()) {
            int hash = 1;
            for (int i = 0; i < size(); i++) {
                hash = 31 * hash + getTransition(i).getTarget(isForward()).hashCode();
            }
            setCachedHash(hash);
            setHashComputed();
        }
        return getCachedHash();
    }

    /**
     * Checks if the set is equal to another given set. Two sets are considered equal only if both
     * are {@link PrioritySensitiveNFATransitionSet}s and have the same target states <em>in the
     * same order</em> - This means that if both sets are iterated in <em>insertion order</em>,
     * their transitions must yield the same sequence of target states (as returned by
     * {@link NFAStateTransition#getTarget(boolean)}).
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof PrioritySensitiveNFATransitionSet)) {
            assert !(obj instanceof NFATransitionSet) : "Do not mix NFATransitionSet and PrioritySensitiveNFATransitionSet!";
            return false;
        }
        PrioritySensitiveNFATransitionSet o = (PrioritySensitiveNFATransitionSet) obj;
        if (size() != o.size()) {
            return false;
        }
        for (int i = 0; i < size(); i++) {
            if (!getTransition(i).getTarget(isForward()).equals(o.getTransition(i).getTarget(isForward()))) {
                return false;
            }
        }
        return true;
    }
}
