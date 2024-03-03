/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nfa;

import java.util.Arrays;
import java.util.Collection;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.automaton.StateIndex;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.TBitSet;

public final class NFA implements StateIndex<NFAState>, JsonConvertible {

    private final RegexAST ast;
    private final NFAState dummyInitialState;
    @CompilationFinal(dimensions = 1) private final NFAStateTransition[] anchoredEntry;
    @CompilationFinal(dimensions = 1) private final NFAStateTransition[] unAnchoredEntry;
    private final NFAStateTransition reverseAnchoredEntry;
    private final NFAStateTransition reverseUnAnchoredEntry;
    @CompilationFinal(dimensions = 1) private final NFAState[] states;
    @CompilationFinal(dimensions = 1) private final NFAStateTransition[] transitions;
    @CompilationFinal(dimensions = 1) private final PreCalculatedResultFactory[] preCalculatedResults;
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
                    NFAStateTransition initialLoopBack,
                    PreCalculatedResultFactory[] preCalculatedResults) {
        this.ast = ast;
        this.dummyInitialState = dummyInitialState;
        this.anchoredEntry = anchoredEntry;
        this.unAnchoredEntry = unAnchoredEntry;
        this.reverseAnchoredEntry = reverseAnchoredEntry;
        this.reverseUnAnchoredEntry = reverseUnAnchoredEntry;
        this.initialLoopBack = initialLoopBack;
        this.preCalculatedResults = preCalculatedResults;
        this.states = new NFAState[stateIDCounter.getCount()];
        // reserve last slot for loopBack matcher
        this.transitions = new NFAStateTransition[transitionIDCounter.getCount() + 1];
        for (NFAState s : states) {
            assert this.states[s.getId()] == null;
            this.states[s.getId()] = s;
            if (s.getSuccessors() == null) {
                continue;
            }
            for (NFAStateTransition t : s.getSuccessors()) {
                assert this.transitions[t.getId()] == null || (s == dummyInitialState && this.transitions[t.getId()] == t);
                this.transitions[t.getId()] = t;
            }
            if (s == dummyInitialState) {
                for (NFAStateTransition t : s.getPredecessors()) {
                    assert this.transitions[t.getId()] == null;
                    this.transitions[t.getId()] = t;
                }
            }
        }
        if (initialLoopBack != null) {
            assert this.transitions[initialLoopBack.getId()] == null;
            this.transitions[initialLoopBack.getId()] = initialLoopBack;
        }
    }

    public NFAState getUnAnchoredInitialState() {
        return unAnchoredEntry[0] == null ? null : unAnchoredEntry[0].getTarget();
    }

    public NFAState getAnchoredInitialState() {
        return anchoredEntry[0] == null ? null : anchoredEntry[0].getTarget();
    }

    public boolean hasReverseUnAnchoredEntry() {
        return reverseUnAnchoredEntry != null && reverseUnAnchoredEntry.getSource().getPredecessors().length > 0;
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
            if (t != null && t.getTarget() == target) {
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

    public NFAStateTransition getInitialLoopBackTransition() {
        return initialLoopBack;
    }

    public boolean isTraceFinderNFA() {
        return preCalculatedResults != null;
    }

    @Override
    public int getNumberOfStates() {
        return states.length;
    }

    @Override
    public int getId(NFAState state) {
        return state.getId();
    }

    @Override
    public NFAState getState(int id) {
        return states[id];
    }

    public int getNumberOfTransitions() {
        return transitions.length;
    }

    public boolean isDead() {
        return anchoredEntry != null ? allDead(anchoredEntry) : (reverseAnchoredEntry.getSource().isDead(false) && reverseUnAnchoredEntry.getSource().isDead(false));
    }

    private static boolean allDead(NFAStateTransition[] entries) {
        if (entries == null) {
            return true;
        }
        for (NFAStateTransition t : entries) {
            if (t != null) {
                assert !t.getTarget().isDead(true);
                return false;
            }
        }
        return true;
    }

    public void setInitialLoopBack(boolean enable) {
        if (getUnAnchoredInitialState() == null || initialLoopBack == null) {
            return;
        }
        NFAState loopbackState = initialLoopBack.getSource();
        NFAStateTransition lastInitTransition = loopbackState.getSuccessors()[loopbackState.getSuccessors().length - 1];
        if (enable) {
            if (lastInitTransition != initialLoopBack) {
                loopbackState.addLoopBackNext(initialLoopBack);
            }
        } else {
            if (lastInitTransition == initialLoopBack) {
                loopbackState.removeLoopBackNext();
            }
        }
    }

    public boolean isFixedCodePointWidth() {
        boolean fixedCodePointWidth = true;
        for (NFAState state : states) {
            if (state != null && !ast.getEncoding().isFixedCodePointWidth(state.getCharSet())) {
                fixedCodePointWidth = false;
                break;
            }
        }
        return fixedCodePointWidth;
    }

    /**
     * Creates a deep copy of the {@code original} NFA. The copy is deep insofar as the network of
     * {@link NFAState} and {@link NFAStateTransition} instances. Any annotations on the states,
     * transitions or the NFA are shared with the original NFA.
     */
    public NFA(NFA original) {
        this.ast = original.ast;
        this.preCalculatedResults = original.preCalculatedResults;
        this.states = new NFAState[original.states.length];
        for (NFAState state : original.states) {
            if (state != null) {
                this.states[state.getId()] = new NFAState(state);
            }
        }
        this.transitions = new NFAStateTransition[original.transitions.length];
        for (NFAStateTransition transition : original.transitions) {
            if (transition != null) {
                this.transitions[transition.getId()] = new NFAStateTransition(transition);
            }
        }
        if (original.anchoredEntry == null) {
            this.anchoredEntry = null;
        } else {
            this.anchoredEntry = new NFAStateTransition[original.anchoredEntry.length];
            for (int i = 0; i < original.anchoredEntry.length; i++) {
                this.anchoredEntry[i] = original.anchoredEntry[i] == null ? null : this.transitions[original.anchoredEntry[i].getId()];
            }
        }
        if (original.unAnchoredEntry == null) {
            this.unAnchoredEntry = null;
        } else {
            this.unAnchoredEntry = new NFAStateTransition[original.unAnchoredEntry.length];
            for (int i = 0; i < original.unAnchoredEntry.length; i++) {
                this.unAnchoredEntry[i] = original.unAnchoredEntry[i] == null ? null : this.transitions[original.unAnchoredEntry[i].getId()];
            }
        }
        this.dummyInitialState = this.states[original.dummyInitialState.getId()];
        this.reverseAnchoredEntry = this.transitions[original.reverseAnchoredEntry.getId()];
        this.reverseUnAnchoredEntry = this.transitions[original.reverseUnAnchoredEntry.getId()];
        this.initialLoopBack = original.initialLoopBack == null ? null : this.transitions[original.initialLoopBack.getId()];

        for (NFAState state : this.states) {
            if (state != null) {
                NFAStateTransition[] successors = state.getSuccessors();
                for (int i = 0; i < successors.length; i++) {
                    successors[i] = this.transitions[successors[i].getId()];
                }
                NFAStateTransition[] predecessors = state.getPredecessors();
                for (int i = 0; i < predecessors.length; i++) {
                    predecessors[i] = this.transitions[predecessors[i].getId()];
                }
            }
        }

        for (NFAStateTransition transition : this.transitions) {
            if (transition != null) {
                transition.setSource(this.states[transition.getSource().getId()]);
                transition.setTarget(this.states[transition.getTarget().getId()]);
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
        TBitSet reachable = new TBitSet(transitions.length);
        for (NFAState s : states) {
            if (s == null || s == dummyInitialState) {
                continue;
            }
            for (NFAStateTransition t : s.getSuccessors(forward)) {
                reachable.set(t.getId());
                if (t.getTarget(forward).isAnchoredFinalState(forward)) {
                    anchoredFinalStateReachable = true;
                }
            }
        }
        final boolean afsReachable = anchoredFinalStateReachable;
        return Json.obj(Json.prop("states",
                        Arrays.stream(states).map(x -> x == null || x == dummyInitialState || (x.isAnchoredFinalState(forward) && !afsReachable) ? Json.nullValue() : x.toJson(forward))),
                        Json.prop("transitions", Arrays.stream(transitions).map(x -> x == null || !reachable.get(x.getId()) ? Json.nullValue() : x.toJson(forward))),
                        Json.prop("anchoredEntry", forward ? fwdEntryToJson(anchoredEntry) : revEntryToJson(reverseAnchoredEntry)),
                        Json.prop("unAnchoredEntry", forward ? fwdEntryToJson(unAnchoredEntry) : revEntryToJson(reverseUnAnchoredEntry)),
                        Json.prop("preCalculatedResults", Json.array(preCalculatedResults)));
    }

    @TruffleBoundary
    private static JsonArray fwdEntryToJson(NFAStateTransition[] entryArray) {
        return Json.array(Arrays.stream(entryArray).map(x -> x == null ? Json.nullValue() : Json.val(x.getTarget().getId())));
    }

    @TruffleBoundary
    private static JsonArray revEntryToJson(NFAStateTransition revEntry) {
        return Json.array(Json.val(revEntry.getSource().getId()));
    }
}
