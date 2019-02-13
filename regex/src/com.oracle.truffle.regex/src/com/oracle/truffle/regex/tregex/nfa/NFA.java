/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nfa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.automaton.StateIndex;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

import java.util.Arrays;
import java.util.Collection;

public class NFA implements StateIndex<NFAState>, JsonConvertible {

    private final RegexAST ast;
    private final NFAState dummyInitialState;
    private final NFAStateTransition[] anchoredEntry;
    private final NFAStateTransition[] unAnchoredEntry;
    private final NFAStateTransition reverseAnchoredEntry;
    private final NFAStateTransition reverseUnAnchoredEntry;
    private final NFAState[] states;
    private final NFAStateTransition[] transitions;
    private final PreCalculatedResultFactory[] preCalculatedResults;
    private final NFAStateTransition initialLoopBack;

    public NFA(RegexAST ast,
                    NFAState dummyInitialState,
                    NFAStateTransition[] anchoredEntry,
                    NFAStateTransition[] unAnchoredEntry,
                    NFAStateTransition reverseAnchoredEntry,
                    NFAStateTransition reverseUnAnchoredEntry,
                    Collection<NFAState> states,
                    Counter.ThresholdCounter stateIDCounter,
                    Counter.ThresholdCounter transitionIDCounter,
                    PreCalculatedResultFactory[] preCalculatedResults) {
        this.ast = ast;
        this.dummyInitialState = dummyInitialState;
        this.anchoredEntry = anchoredEntry;
        this.unAnchoredEntry = unAnchoredEntry;
        this.reverseAnchoredEntry = reverseAnchoredEntry;
        this.reverseUnAnchoredEntry = reverseUnAnchoredEntry;
        this.preCalculatedResults = preCalculatedResults;
        this.states = new NFAState[stateIDCounter.getCount()];
        // reserve last slot for loopBack matcher
        this.transitions = new NFAStateTransition[transitionIDCounter.getCount() + 1];
        if (isTraceFinderNFA()) {
            this.initialLoopBack = null;
        } else {
            this.initialLoopBack = new NFAStateTransition(
                            (short) transitionIDCounter.inc(),
                            getUnAnchoredInitialState(),
                            getUnAnchoredInitialState(),
                            GroupBoundaries.getEmptyInstance());
            this.transitions[initialLoopBack.getId()] = initialLoopBack;
        }
        for (NFAState s : states) {
            assert this.states[s.getId()] == null;
            this.states[s.getId()] = s;
            if (s.getNext() == null) {
                continue;
            }
            for (NFAStateTransition t : s.getNext()) {
                assert this.transitions[t.getId()] == null || (s == dummyInitialState && this.transitions[t.getId()] == t);
                this.transitions[t.getId()] = t;
            }
            if (s == dummyInitialState) {
                for (NFAStateTransition t : s.getPrev()) {
                    assert this.transitions[t.getId()] == null;
                    this.transitions[t.getId()] = t;
                }
            }
        }
    }

    private NFAState getUnAnchoredInitialState() {
        return unAnchoredEntry[0].getTarget();
    }

    public boolean hasReverseUnAnchoredEntry() {
        return reverseUnAnchoredEntry != null && !reverseUnAnchoredEntry.getSource().getPrev().isEmpty();
    }

    public RegexAST getAst() {
        return ast;
    }

    public NFAState getDummyInitialState() {
        return dummyInitialState;
    }

    public boolean isEntry(NFAState state, boolean forward) {
        return isAnchoredEntry(state, forward) || isUnAnchoredEntry(state, forward);
    }

    public boolean isAnchoredEntry(NFAState state, boolean forward) {
        return forward ? transitionListContainsTarget(anchoredEntry, state) : reverseAnchoredEntry.getSource() == state;
    }

    public boolean isUnAnchoredEntry(NFAState state, boolean forward) {
        return forward ? transitionListContainsTarget(unAnchoredEntry, state) : reverseUnAnchoredEntry.getSource() == state;
    }

    public int getAnchoredEntryOffset(NFAState state, boolean forward) {
        assert isAnchoredEntry(state, forward);
        return forward ? transitionListIndexOfTarget(anchoredEntry, state) : 0;
    }

    public int getUnAnchoredEntryOffset(NFAState state, boolean forward) {
        assert isUnAnchoredEntry(state, forward);
        return forward ? transitionListIndexOfTarget(unAnchoredEntry, state) : 0;
    }

    private static int transitionListIndexOfTarget(NFAStateTransition[] transitions, NFAState target) {
        for (int i = 0; i < transitions.length; i++) {
            if (transitions[i].getTarget() == target) {
                return i;
            }
        }
        return -1;
    }

    private static boolean transitionListContainsTarget(NFAStateTransition[] transitions, NFAState target) {
        for (NFAStateTransition t : transitions) {
            if (t.getTarget() == target) {
                return true;
            }
        }
        return false;
    }

    public NFAStateTransition[] getAnchoredEntry() {
        return anchoredEntry;
    }

    public NFAStateTransition[] getUnAnchoredEntry() {
        return unAnchoredEntry;
    }

    public NFAStateTransition getReverseAnchoredEntry() {
        return reverseAnchoredEntry;
    }

    public NFAStateTransition getReverseUnAnchoredEntry() {
        return reverseUnAnchoredEntry;
    }

    public NFAState[] getStates() {
        return states;
    }

    public NFAStateTransition[] getTransitions() {
        return transitions;
    }

    public PreCalculatedResultFactory[] getPreCalculatedResults() {
        return preCalculatedResults;
    }

    public boolean isTraceFinderNFA() {
        return preCalculatedResults != null;
    }

    @Override
    public int getNumberOfStates() {
        return states.length;
    }

    @Override
    public NFAState getState(int id) {
        return states[id];
    }

    public void setInitialLoopBack(boolean enable) {
        if (getUnAnchoredInitialState().getNext().isEmpty()) {
            return;
        }
        NFAStateTransition lastInitTransition = getUnAnchoredInitialState().getNext().get(getUnAnchoredInitialState().getNext().size() - 1);
        if (enable) {
            if (lastInitTransition != initialLoopBack) {
                getUnAnchoredInitialState().addLoopBackNext(initialLoopBack);
            }
        } else {
            if (lastInitTransition == initialLoopBack) {
                getUnAnchoredInitialState().removeLoopBackNext();
            }
        }
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("states", Json.array(states)),
                        Json.prop("transitions", Json.array(transitions)),
                        Json.prop("anchoredEntry", anchoredEntry == null ? null : fwdEntryToJson(anchoredEntry)),
                        Json.prop("unAnchoredEntry", unAnchoredEntry == null ? null : fwdEntryToJson(unAnchoredEntry)),
                        Json.prop("reverseAnchoredEntry", revEntryToJson(reverseAnchoredEntry)),
                        Json.prop("reverseUnAnchoredEntry", revEntryToJson(reverseUnAnchoredEntry)),
                        Json.prop("preCalculatedResults", Json.array(preCalculatedResults)));
    }

    @TruffleBoundary
    public JsonValue toJson(boolean forward) {
        boolean anchoredFinalStateReachable = false;
        CompilationFinalBitSet bitSet = new CompilationFinalBitSet(transitions.length);
        for (NFAState s : states) {
            if (s == null || s == dummyInitialState) {
                continue;
            }
            for (NFAStateTransition t : s.getNext(forward)) {
                bitSet.set(t.getId());
                if (t.getTarget(forward).isAnchoredFinalState(forward)) {
                    anchoredFinalStateReachable = true;
                }
            }
        }
        final boolean afsReachable = anchoredFinalStateReachable;
        return Json.obj(Json.prop("states",
                        Arrays.stream(states).map(x -> x == null || x == dummyInitialState || (x.isAnchoredFinalState(forward) && !afsReachable) ? Json.nullValue() : x.toJson(forward))),
                        Json.prop("transitions", Arrays.stream(transitions).map(x -> x == null || !bitSet.get(x.getId()) ? Json.nullValue() : x.toJson(forward))),
                        Json.prop("anchoredEntry", forward ? fwdEntryToJson(anchoredEntry) : revEntryToJson(reverseAnchoredEntry)),
                        Json.prop("unAnchoredEntry", forward ? fwdEntryToJson(unAnchoredEntry) : revEntryToJson(reverseUnAnchoredEntry)),
                        Json.prop("preCalculatedResults", Json.array(preCalculatedResults)));
    }

    @TruffleBoundary
    private static JsonArray fwdEntryToJson(NFAStateTransition[] entryArray) {
        return Json.array(Arrays.stream(entryArray).map(x -> Json.val(x.getTarget().getId())));
    }

    @TruffleBoundary
    private static JsonArray revEntryToJson(NFAStateTransition revEntry) {
        return Json.array(Json.val(revEntry.getSource().getId()));
    }
}
