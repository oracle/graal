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

public final class NFATransitionSet implements Iterable<NFAStateTransition>, JsonConvertible {

    private static final byte FLAG_FORWARD = 1;
    private static final byte FLAG_PRIORITY_SENSITIVE = 1 << 1;
    private static final byte FLAG_LEADS_TO_FINAL_STATE = 1 << 2;
    private static final byte FLAG_HASH_COMPUTED = 1 << 4;
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

    private boolean leadsToFinalState() {
        return isFlagSet(FLAG_LEADS_TO_FINAL_STATE);
    }

    private void setHashComputed() {
        setFlag(FLAG_HASH_COMPUTED);
    }

    private void clearHashComputed() {
        clearFlag(FLAG_HASH_COMPUTED);
    }

    private boolean noAdd() {
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

    public void add(NFAStateTransition transition) {
        if (noAdd()) {
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

    public void addAll(NFATransitionSet other) {
        if (noAdd()) {
            return;
        }
        ensureCapacity(size + other.size);
        for (int i = 0; i < other.size; i++) {
            if (noAdd()) {
                return;
            }
            doAdd(other.getTransition(i));
        }
    }

    public void addAll(List<NFAStateTransition> addTransitions) {
        if (noAdd()) {
            return;
        }
        ensureCapacity(size + addTransitions.size());
        for (NFAStateTransition t : addTransitions) {
            if (noAdd()) {
                return;
            }
            doAdd(t);
        }
    }

    public void addAll(NFAStateTransition... addTransitions) {
        if (noAdd()) {
            return;
        }
        ensureCapacity(size + addTransitions.length);
        for (NFAStateTransition t : addTransitions) {
            if (noAdd()) {
                return;
            }
            doAdd(t);
        }
    }

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
