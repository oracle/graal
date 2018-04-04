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

import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAAnchoredFinalState;
import com.oracle.truffle.regex.tregex.nfa.NFAFinalState;
import com.oracle.truffle.regex.tregex.nfa.NFAMatcherState;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.TraceFinderDFAStateNode;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class NFATransitionSet implements Iterable<NFAStateTransition> {

    private static final byte FLAG_FORWARD = 1;
    private static final byte FLAG_PRIORITY_SENSITIVE = 1 << 1;
    private static final byte FLAG_CONTAINS_PREFIX_STATES = 1 << 2;
    private static final byte FLAG_HASH_COMPUTED = 1 << 3;

    private final NFA nfa;
    private final NFAStateSet stateSet;
    private byte flags = 0;
    private short[] transitions;
    private short size = 0;
    private byte preCalculatedUnAnchoredResult = TraceFinderDFAStateNode.NO_PRE_CALC_RESULT;
    private byte preCalculatedAnchoredResult = TraceFinderDFAStateNode.NO_PRE_CALC_RESULT;
    private short finalState = -1;
    private short anchoredFinalState = -1;
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
        this.preCalculatedUnAnchoredResult = copy.preCalculatedUnAnchoredResult;
        this.preCalculatedAnchoredResult = copy.preCalculatedAnchoredResult;
        this.finalState = copy.finalState;
        this.anchoredFinalState = copy.anchoredFinalState;
        this.cachedHash = copy.cachedHash;
    }

    public static NFATransitionSet create(NFA nfa, boolean forward, boolean prioritySensitive) {
        return new NFATransitionSet(nfa, forward, prioritySensitive, new short[20]);
    }

    public static NFATransitionSet create(NFA nfa, boolean forward, boolean prioritySensitive, List<NFAStateTransition> transitions) {
        NFATransitionSet transitionSet = new NFATransitionSet(nfa, forward, prioritySensitive, new short[(transitions.size() * 2) < 20 ? 20 : transitions.size() * 2]);
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

    private boolean isForward() {
        return isFlagSet(FLAG_FORWARD);
    }

    public boolean isPrioritySensitive() {
        return isFlagSet(FLAG_PRIORITY_SENSITIVE);
    }

    public boolean containsPrefixStates() {
        return isFlagSet(FLAG_CONTAINS_PREFIX_STATES);
    }

    private void setContainsPrefixStates() {
        setFlag(FLAG_CONTAINS_PREFIX_STATES);
    }

    private boolean isHashComputed() {
        return isFlagSet(FLAG_HASH_COMPUTED);
    }

    private void setHashComputed() {
        setFlag(FLAG_HASH_COMPUTED);
    }

    private void clearHashComputed() {
        clearFlag(FLAG_HASH_COMPUTED);
    }

    public NFAFinalState getFinalState() {
        return containsFinalState() ? (NFAFinalState) nfa.getStates()[finalState] : null;
    }

    public NFAAnchoredFinalState getAnchoredFinalState() {
        return containsAnchoredFinalState() ? (NFAAnchoredFinalState) nfa.getStates()[anchoredFinalState] : null;
    }

    public boolean containsAnchoredFinalState() {
        return anchoredFinalState >= 0;
    }

    public boolean containsFinalState() {
        return finalState >= 0;
    }

    public byte getPreCalculatedUnAnchoredResult() {
        return preCalculatedUnAnchoredResult;
    }

    public byte getPreCalculatedAnchoredResult() {
        return preCalculatedAnchoredResult;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    public void add(NFAStateTransition transition) {
        NFAState target = transition.getTarget(isForward());
        if (isPrioritySensitive() && (containsFinalState() || (target instanceof NFAAnchoredFinalState && containsAnchoredFinalState()))) {
            return;
        }
        if (!stateSet.add(target)) {
            return;
        }
        if (target.hasPrefixStates()) {
            setContainsPrefixStates();
        }
        if (target instanceof NFAMatcherState) {
            appendTransition(transition);
        } else if (target instanceof NFAAnchoredFinalState) {
            anchoredFinalState = target.getId();
            if (target.hasPossibleResults()) {
                updatePreCalcAnchoredResult(target.getPossibleResults().get(0));
            }
            appendTransition(transition);
        } else {
            assert target instanceof NFAFinalState;
            finalState = target.getId();
            if (target.hasPossibleResults()) {
                updatePreCalcUnAnchoredResult(target.getPossibleResults().get(0));
            }
            appendTransition(transition);
        }
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
        transitions[size] = transition.getId();
        size++;
        clearHashComputed();
    }

    public void addAll(NFATransitionSet other) {
        if (isPrioritySensitive() && containsFinalState()) {
            return;
        }
        ensureCapacity(size + other.size);
        for (int i = 0; i < other.size; i++) {
            add(nfa.getTransitions()[other.transitions[i]]);
        }
    }

    public void addAll(List<NFAStateTransition> addTransitions) {
        if (isPrioritySensitive() && containsFinalState()) {
            return;
        }
        ensureCapacity(size + addTransitions.size());
        for (NFAStateTransition t : addTransitions) {
            add(t);
        }
    }

    private void updatePreCalcUnAnchoredResult(int newResult) {
        if (newResult >= 0) {
            if (preCalculatedUnAnchoredResult == TraceFinderDFAStateNode.NO_PRE_CALC_RESULT || Byte.toUnsignedInt(preCalculatedUnAnchoredResult) > newResult) {
                preCalculatedUnAnchoredResult = (byte) newResult;
            }
        }
    }

    private void updatePreCalcAnchoredResult(int newResult) {
        if (newResult >= 0) {
            if (preCalculatedAnchoredResult == TraceFinderDFAStateNode.NO_PRE_CALC_RESULT || Byte.toUnsignedInt(preCalculatedAnchoredResult) > newResult) {
                preCalculatedAnchoredResult = (byte) newResult;
            }
        }
    }

    @Override
    public int hashCode() {
        if (!isHashComputed()) {
            if (isPrioritySensitive()) {
                cachedHash = 1;
                for (int i = 0; i < size; i++) {
                    cachedHash = 31 * cachedHash + nfa.getTransitions()[transitions[i]].getTarget(isForward()).hashCode();
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
                if (!nfa.getTransitions()[transitions[i]].getTarget(isForward()).equals(nfa.getTransitions()[o.transitions[i]].getTarget(isForward()))) {
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

    public Stream<NFAStateTransition> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    @Override
    public String toString() {
        return stream().map(x -> x.getTarget(isForward()).idToString()).collect(Collectors.joining(",", "{", "}"));
    }

    public DebugUtil.Table toTable(String name) {
        DebugUtil.Table table = new DebugUtil.Table(name);
        for (NFAStateTransition transition : this) {
            table.append(transition.toTable());
        }
        return table;
    }
}
