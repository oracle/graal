/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.regex.tregex.nodes.nfa;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.RegexRootNode;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorNode;

/**
 * This regex executor matches a given expression by calculating DFA states from the NFA on the fly,
 * without any caching. It is used as a placeholder for {@link TRegexDFAExecutorNode} until the
 * expression is executed {@link TRegexOptions#TRegexGenerateDFAThreshold} times, in order to avoid
 * the costly DFA generation on all expressions that are not on any hot code paths.
 */
public final class TRegexNFAExecutorNode extends TRegexExecutorNode {

    private final NFA nfa;
    private final boolean searching;

    public TRegexNFAExecutorNode(NFA nfa) {
        this.nfa = nfa;
        nfa.setInitialLoopBack(false);
        this.searching = !nfa.getAst().getFlags().isSticky() && !nfa.getAst().getRoot().startsWithCaret();
        for (int i = 0; i < nfa.getNumberOfTransitions(); i++) {
            if (nfa.getTransitions()[i] != null) {
                nfa.getTransitions()[i].getGroupBoundaries().materializeArrays();
            }
        }
    }

    public NFA getNFA() {
        return nfa;
    }

    @Override
    public boolean isForward() {
        return true;
    }

    @Override
    public boolean writesCaptureGroups() {
        return true;
    }

    @Override
    public TRegexExecutorLocals createLocals(Object input, int fromIndex, int index, int maxIndex) {
        return new TRegexNFAExecutorLocals(input, fromIndex, index, maxIndex, getNumberOfCaptureGroups(), nfa.getNumberOfStates());
    }

    @Override
    public Object execute(TRegexExecutorLocals abstractLocals, boolean compactString) {
        TRegexNFAExecutorLocals locals = (TRegexNFAExecutorLocals) abstractLocals;
        CompilerDirectives.ensureVirtualized(locals);

        final int offset = rewindUpTo(locals, 0, nfa.getAnchoredEntry().length - 1);
        int anchoredInitialState = nfa.getAnchoredEntry()[offset].getTarget().getId();
        int unAnchoredInitialState = nfa.getUnAnchoredEntry()[offset].getTarget().getId();
        if (unAnchoredInitialState != anchoredInitialState && inputAtBegin(locals)) {
            locals.addInitialState(anchoredInitialState);
        }
        if (nfa.getState(unAnchoredInitialState) != null) {
            locals.addInitialState(unAnchoredInitialState);
        }
        if (locals.curStatesEmpty()) {
            return null;
        }
        while (true) {
            if (CompilerDirectives.inInterpreter()) {
                RegexRootNode.checkThreadInterrupted();
            }
            if (inputHasNext(locals)) {
                findNextStates(locals);
                // If locals.successorsEmpty() is true, then all of our paths have either been
                // finished, discarded due to priority or failed to match. If we managed to finish
                // any path to a final state (i.e. locals.hasResult() is true), we can terminate
                // the search now.
                // We can also terminate the search now if we were interested only in matches at
                // the very start of the string (i.e. searching is false). Such a search would
                // only have walked through the rest of the string without considering any other
                // paths.
                if (locals.successorsEmpty() && (!searching || locals.hasResult())) {
                    return locals.getResult();
                }
            } else {
                findNextStatesAtEnd(locals);
                return locals.getResult();
            }
            locals.nextState();
            inputAdvance(locals);
        }
    }

    private void findNextStates(TRegexNFAExecutorLocals locals) {
        int c = inputReadAndDecode(locals);
        while (locals.hasNext()) {
            expandState(locals, locals.next(), c, false);
            // If we have found a path to a final state, then we will trim all paths with lower
            // priority (i.e. the rest of the elements in curStates).
            if (locals.isResultPushed()) {
                return;
            }
        }
        // We are supposed to find the first match of the regular expression. A match starting
        // at a higher index has lower priority and so we give the lowest priority to the loopback
        // transition.
        // The loopback priority has to be lower than the priority of any path completed so far.
        // Therefore, we only follow the loopback if no path has been completed so far
        // (i.e. !locals.hasResult()).
        if (searching && !locals.hasResult() && locals.getIndex() >= locals.getFromIndex()) {
            expandState(locals, nfa.getInitialLoopBackTransition().getTarget().getId(), c, true);
        }
    }

    private void expandState(TRegexNFAExecutorLocals locals, int stateId, int c, boolean isLoopBack) {
        NFAState state = nfa.getState(stateId);
        // If we manage to find a path to the (unanchored) final state, then we will trim all other
        // paths leading from the current state as they all have lower priority. We do this by
        // iterating through the transitions in priority order and stopping on the first transition
        // to a final state.
        for (int i = 0; i < maxTransitionIndex(state); i++) {
            NFAStateTransition t = state.getSuccessors()[i];
            int targetId = t.getTarget().getId();
            int markIndex = targetId >> 6;
            long markBit = 1L << targetId;
            if (!t.getTarget().isAnchoredFinalState(true) && (locals.getMarks()[markIndex] & markBit) == 0) {
                locals.getMarks()[markIndex] |= markBit;
                if (t.getTarget().isUnAnchoredFinalState(true)) {
                    locals.pushResult(t, !isLoopBack);
                } else if (t.getCodePointSet().contains(c)) {
                    locals.pushSuccessor(t, !isLoopBack);
                }
            }
        }
    }

    private static int maxTransitionIndex(NFAState state) {
        return state.hasTransitionToUnAnchoredFinalState(true) ? state.getTransitionToUnAnchoredFinalStateId(true) + 1 : state.getSuccessors().length;
    }

    private void findNextStatesAtEnd(TRegexNFAExecutorLocals locals) {
        while (locals.hasNext()) {
            expandStateAtEnd(locals, nfa.getState(locals.next()), false);
            if (locals.isResultPushed()) {
                return;
            }
        }
        if (searching && !locals.hasResult()) {
            expandStateAtEnd(locals, nfa.getInitialLoopBackTransition().getTarget(), true);
        }
    }

    private static void expandStateAtEnd(TRegexNFAExecutorLocals locals, NFAState state, boolean isLoopBack) {
        if (state.hasTransitionToFinalState(true)) {
            locals.pushResult(state.getFirstTransitionToFinalState(true), !isLoopBack);
        }
    }
}
