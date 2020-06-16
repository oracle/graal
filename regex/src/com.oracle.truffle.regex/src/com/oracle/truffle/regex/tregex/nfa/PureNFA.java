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
package com.oracle.truffle.regex.tregex.nfa;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.automaton.StateIndex;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * A NFA that corresponds to the subtree of one {@link RegexASTSubtreeRootNode}.
 */
public final class PureNFA implements StateIndex<PureNFAState> {

    private final int subTreeId;
    @CompilationFinal(dimensions = 1) private final PureNFAState[] states;
    @CompilationFinal(dimensions = 1) private final PureNFATransition[] transitions;

    public PureNFA(RegexASTSubtreeRootNode astSubRoot,
                    PureNFAState[] states,
                    Counter.ThresholdCounter stateIDCounter,
                    Counter.ThresholdCounter transitionIDCounter) {
        this.subTreeId = astSubRoot.getSubTreeId();
        this.states = new PureNFAState[stateIDCounter.getCount()];
        this.transitions = new PureNFATransition[transitionIDCounter.getCount()];
        for (PureNFAState s : states) {
            if (s == null) {
                continue;
            }
            assert this.states[s.getId()] == null;
            this.states[s.getId()] = s;
            for (PureNFATransition t : s.getSuccessors()) {
                if (s.getId() != 0) {
                    // don't link the dummy initial state as predecessor of initial states.
                    t.getTarget().addPredecessor(t);
                }
                assert this.transitions[t.getId()] == null || (s.getId() == 0 && this.transitions[t.getId()] == t);
                this.transitions[t.getId()] = t;
            }
        }
    }

    /**
     * {@link RegexASTSubtreeRootNode#getSubTreeId() Subtree ID} of the
     * {@link RegexASTSubtreeRootNode} this NFA was generated from.
     */
    public int getSubTreeId() {
        return subTreeId;
    }

    /**
     * Get this NFA's "dummy initial state". Since {@link DFAGenerator} works on sets of NFA
     * transitions, we need pseudo-transitions to the NFA's initial states as entry points for the
     * DFA generator. The dummy initial state provides these transitions, in a fixed layout: The
     * first half of its {@link PureNFAState#getSuccessors() successors} lead to the NFA's anchored
     * initial states, and the second half leads to the unanchored initial states. The dummy initial
     * state's {@link PureNFAState#getPredecessors() predecessors} are the NFA's anchored and
     * unanchored final state, in that order. They serve as entry points for reverse DFAs.
     */
    public PureNFAState getDummyInitialState() {
        assert states[0].getSuccessors().length == 2 && states[0].getPredecessors().length == 2;
        return states[0];
    }

    public int getNumberOfEntryPoints() {
        return getDummyInitialState().getSuccessors().length / 2;
    }

    public PureNFATransition getAnchoredEntry() {
        return getDummyInitialState().getSuccessors()[0];
    }

    public PureNFATransition getUnAnchoredEntry() {
        return getDummyInitialState().getSuccessors()[1];
    }

    public PureNFAState getUnAnchoredInitialState() {
        return getUnAnchoredEntry().getTarget();
    }

    public PureNFAState getAnchoredInitialState() {
        return getAnchoredEntry().getTarget();
    }

    public PureNFATransition getReverseAnchoredEntry() {
        return getDummyInitialState().getPredecessors()[0];
    }

    public PureNFATransition getReverseUnAnchoredEntry() {
        return getDummyInitialState().getPredecessors()[1];
    }

    public PureNFAState getUnAnchoredFinalState() {
        return getReverseUnAnchoredEntry().getSource();
    }

    public PureNFAState getAnchoredFinalState() {
        return getReverseAnchoredEntry().getSource();
    }

    public PureNFAState getUnAnchoredInitialState(boolean forward) {
        return forward ? getUnAnchoredInitialState() : getUnAnchoredFinalState();
    }

    public PureNFAState getAnchoredInitialState(boolean forward) {
        return forward ? getAnchoredInitialState() : getAnchoredFinalState();
    }

    public PureNFAState[] getStates() {
        return states;
    }

    public PureNFATransition[] getTransitions() {
        return transitions;
    }

    @Override
    public int getNumberOfStates() {
        return states.length;
    }

    @Override
    public int getId(PureNFAState state) {
        assert states[state.getId()] == state;
        return state.getId();
    }

    @Override
    public PureNFAState getState(int id) {
        return states[id];
    }

    public void materializeGroupBoundaries() {
        for (PureNFATransition t : transitions) {
            if (t != null) {
                t.getGroupBoundaries().materializeArrays();
            }
        }
    }

    @TruffleBoundary
    public JsonValue toJson(RegexAST ast) {
        return Json.obj(Json.prop("states",
                        Arrays.stream(states).map(x -> x == null || x == getDummyInitialState() || (x.isAnchoredFinalState() && !x.hasPredecessors()) ? Json.nullValue() : x.toJson(ast))),
                        Json.prop("transitions", Arrays.stream(transitions).map(x -> x == null || x.getSource() == getDummyInitialState() ? Json.nullValue() : x.toJson(ast))),
                        Json.prop("anchoredEntry", Json.array(Json.val(getAnchoredInitialState().getId()))),
                        Json.prop("unAnchoredEntry", Json.array(Json.val(getUnAnchoredInitialState().getId()))));
    }
}
