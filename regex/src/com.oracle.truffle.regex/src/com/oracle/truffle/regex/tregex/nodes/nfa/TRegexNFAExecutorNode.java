/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.regex.tregex.nodes.nfa;

import com.oracle.truffle.api.CompilerDirectives;
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
public class TRegexNFAExecutorNode extends TRegexExecutorNode {

    private final NFA nfa;
    private final int numberOfCaptureGroups;
    private final boolean searching;

    public TRegexNFAExecutorNode(NFA nfa, int numberOfCaptureGroups) {
        this.nfa = nfa;
        nfa.setInitialLoopBack(false);
        this.numberOfCaptureGroups = numberOfCaptureGroups;
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

    public int getNumberOfCaptureGroups() {
        return numberOfCaptureGroups;
    }

    @Override
    public TRegexExecutorLocals createLocals(Object input, int fromIndex, int index, int maxIndex) {
        return new TRegexNFAExecutorLocals(input, fromIndex, index, maxIndex, numberOfCaptureGroups, nfa.getNumberOfStates());
    }

    @Override
    public Object execute(TRegexExecutorLocals abstractLocals, boolean compactString) {
        TRegexNFAExecutorLocals locals = (TRegexNFAExecutorLocals) abstractLocals;
        CompilerDirectives.ensureVirtualized(locals);

        final int offset = Math.min(locals.getIndex(), nfa.getAnchoredEntry().length - 1);
        locals.setIndex(locals.getIndex() - offset);
        int anchoredInitialState = nfa.getAnchoredEntry()[offset].getTarget().getId();
        int unAnchoredInitialState = nfa.getUnAnchoredEntry()[offset].getTarget().getId();
        if (locals.getIndex() == 0) {
            locals.addInitialState(anchoredInitialState);
            if (unAnchoredInitialState != anchoredInitialState && nfa.getState(unAnchoredInitialState) != null) {
                locals.addInitialState(unAnchoredInitialState);
            }
        } else if (nfa.getState(unAnchoredInitialState) != null) {
            locals.addInitialState(unAnchoredInitialState);
        } else {
            return null;
        }
        while (true) {
            if (locals.getIndex() < getInputLength(locals)) {
                findNextStates(locals);
                if (locals.successorsEmpty() && (!searching || locals.hasResult())) {
                    return locals.getResult();
                }
            } else {
                findNextStatesAtEnd(locals);
                return locals.getResult();
            }
            locals.nextChar();
        }
    }

    private void findNextStates(TRegexNFAExecutorLocals locals) {
        char c = getChar(locals);
        while (locals.hasNext()) {
            expandState(locals, locals.next(), c, false);
            if (locals.isResultPushed()) {
                return;
            }
        }
        if (searching && !locals.hasResult() && locals.getIndex() >= locals.getFromIndex()) {
            expandState(locals, nfa.getInitialLoopBackTransition().getTarget().getId(), c, true);
        }
    }

    private void expandState(TRegexNFAExecutorLocals locals, int stateId, char c, boolean isLoopBack) {
        NFAState state = nfa.getState(stateId);
        for (int i = 0; i < maxTransitionIndex(state); i++) {
            NFAStateTransition t = state.getNext()[i];
            NFAState target = t.getTarget();
            int targetId = t.getTarget().getId();
            int markIndex = targetId >> 6;
            long markBit = 1L << targetId;
            if (!t.getTarget().isAnchoredFinalState(true) && (locals.getMarks()[markIndex] & markBit) == 0) {
                locals.getMarks()[markIndex] |= markBit;
                if (t.getTarget().isUnAnchoredFinalState(true)) {
                    locals.pushResult(t, !isLoopBack);
                } else if (target.getCharSet().contains(c)) {
                    locals.pushSuccessor(t, !isLoopBack);
                }
            }
        }
    }

    private static int maxTransitionIndex(NFAState state) {
        return state.hasTransitionToUnAnchoredFinalState(true) ? state.getTransitionToUnAnchoredFinalStateId(true) + 1 : state.getNext().length;
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
