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
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputRegionMatchesNode;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;

/**
 * This regex executor uses a backtracking algorithm on the NFA. It is used for all expressions that
 * cannot be matched with the DFA, such as expressions with backreferences.
 */
public class TRegexBacktrackingNFAExecutorNode extends TRegexExecutorNode {

    private final NFA nfa;
    private final int numberOfCaptureGroups;

    @Child InputRegionMatchesNode regionMatchesNode;

    public TRegexBacktrackingNFAExecutorNode(NFA nfa, int numberOfCaptureGroups) {
        this.nfa = nfa;
        nfa.setInitialLoopBack(!nfa.getAst().getFlags().isSticky());
        this.numberOfCaptureGroups = numberOfCaptureGroups;
        for (int i = 0; i < nfa.getAnchoredEntry().length; i++) {
            if (nfa.getState(nfa.getUnAnchoredEntry()[i].getTarget().getId()) != null && nfa.getAnchoredEntry()[i].getTarget() != nfa.getUnAnchoredEntry()[i].getTarget()) {
                nfa.getAnchoredEntry()[i].getTarget().addLoopBackNext(new NFAStateTransition((short) -1,
                                nfa.getAnchoredEntry()[i].getTarget(),
                                nfa.getUnAnchoredEntry()[i].getTarget(),
                                GroupBoundaries.getEmptyInstance()));
            }
        }
        for (int i = 0; i < nfa.getNumberOfTransitions(); i++) {
            if (nfa.getTransitions()[i] != null) {
                nfa.getTransitions()[i].getGroupBoundaries().materializeArrays();
            }
        }
    }

    public int getNumberOfCaptureGroups() {
        return numberOfCaptureGroups;
    }

    @Override
    public TRegexExecutorLocals createLocals(Object input, int fromIndex, int index, int maxIndex) {
        return new TRegexBacktrackingNFAExecutorLocals(input, fromIndex, index, maxIndex, numberOfCaptureGroups);
    }

    @Override
    public Object execute(TRegexExecutorLocals abstractLocals, boolean compactString) {
        TRegexBacktrackingNFAExecutorLocals locals = (TRegexBacktrackingNFAExecutorLocals) abstractLocals;

        final int offset = Math.min(locals.getIndex(), nfa.getAnchoredEntry().length - 1);
        locals.setIndex(locals.getIndex() - offset);
        int pc = (locals.getIndex() == 0 ? nfa.getAnchoredEntry() : nfa.getUnAnchoredEntry())[offset].getTarget().getId();
        if (nfa.getState(pc) == null) {
            return null;
        }

        while (true) {
            NFAState curState = nfa.getState(pc);
            if (curState.isFinalState(true)) {
                return locals.toResult();
            }
            int firstMatch = -1;
            if (locals.getIndex() < getInputLength(locals)) {
                char c = getChar(locals);
                for (int i = getStartingTransition(curState); i >= 0; i--) {
                    if (curState.getNext()[i].getTarget().isAnchoredFinalState(true)) {
                        continue;
                    }
                    if (curState.getNext()[i].getTarget().getCharSet().contains(c)) {
                        if (firstMatch >= 0) {
                            if (curState.getNext()[firstMatch].getTarget().isUnAnchoredFinalState(true)) {
                                locals.pushResult(curState.getNext()[firstMatch]);
                            } else {
                                locals.push(curState.getNext()[firstMatch]);
                            }
                        }
                        firstMatch = i;
                    }
                }
            } else if (curState.hasTransitionToFinalState(true)) {
                firstMatch = curState.getFirstTransitionToFinalStateIndex(true);
            }
            if (firstMatch < 0) {
                if (locals.canPopResult()) {
                    return locals.popResult();
                } else if (locals.canPop()) {
                    pc = locals.pop();
                } else {
                    return null;
                }
            } else {
                locals.apply(curState.getNext()[firstMatch]);
                locals.incIndex(1);
                pc = curState.getNext()[firstMatch].getTarget().getId();
            }
        }
    }

    private static int getStartingTransition(NFAState curState) {
        return Math.min(Short.toUnsignedInt(curState.getTransitionToUnAnchoredFinalStateId(true)), curState.getNext().length - 1);
    }

    public boolean regionMatches(TRegexExecutorLocals locals, int startIndex1, int startIndex2, int length) {
        if (regionMatchesNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            regionMatchesNode = InputRegionMatchesNode.create();
        }
        return regionMatchesNode.execute(locals.getInput(), startIndex1, locals.getInput(), startIndex2, length, null);
    }
}
