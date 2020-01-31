/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.nfa.PureNFA;
import com.oracle.truffle.regex.tregex.nfa.PureNFAMap;
import com.oracle.truffle.regex.tregex.nfa.PureNFAState;
import com.oracle.truffle.regex.tregex.nfa.PureNFATransition;
import com.oracle.truffle.regex.tregex.nfa.QuantifierGuard;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputRegionMatchesNode;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;

/**
 * This regex executor uses a backtracking algorithm on the NFA. It is used for all expressions that
 * cannot be matched with the DFA, such as expressions with backreferences.
 */
public class TRegexBacktrackingNFAExecutorNode extends TRegexExecutorNode {

    private final PureNFA nfa;
    private final int numberOfCaptureGroups;

    @Child InputRegionMatchesNode regionMatchesNode;

    public TRegexBacktrackingNFAExecutorNode(PureNFAMap nfaMap, PureNFA nfa, int numberOfCaptureGroups) {
        this.nfa = nfa;
        nfa.setInitialLoopBack(!nfaMap.getAst().getFlags().isSticky());
        this.numberOfCaptureGroups = numberOfCaptureGroups;
        if (nfa == nfaMap.getRoot() && nfa.getAnchoredInitialState() != nfa.getUnAnchoredInitialState()) {
            nfa.getAnchoredInitialState().addLoopBackNext(new PureNFATransition((short) -1,
                            nfa.getAnchoredInitialState(),
                            nfa.getUnAnchoredInitialState(),
                            GroupBoundaries.getEmptyInstance(),
                            PureNFATransition.NO_LOOK_AROUNDS,
                            QuantifierGuard.NO_GUARDS));
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
        int pc = (locals.getIndex() == 0 ? nfa.getAnchoredEntry() : nfa.getUnAnchoredEntry()).getTarget().getId();
        while (pc >= 0) {
            pc = runState(locals, pc);
        }
        return locals.popResult();
    }

    @ExplodeLoop
    private int runState(TRegexBacktrackingNFAExecutorLocals locals, int pc) {
        for (int stateID = 0; stateID < nfa.getNumberOfStates(); stateID++) {
            if (stateID == pc) {
                PureNFAState curState = nfa.getState(stateID);
                CompilerDirectives.isPartialEvaluationConstant(curState);
                if (curState.isFinalState()) {
                    locals.pushResult();
                    return -1;
                }
                int firstMatch = -1;
                PureNFATransition[] successors = curState.getSuccessors();
                char c;
                boolean atEnd = locals.getIndex() >= getInputLength(locals);
                if (atEnd) {
                    c = 0;
                } else {
                    c = getChar(locals);
                }
                for (int i = successors.length - 1; i >= 0; i--) {
                    PureNFATransition transition = successors[i];
                    CompilerDirectives.isPartialEvaluationConstant(transition);
                    if (transition.getTarget().isAnchoredFinalState()) {
                        if (atEnd) {
                            firstMatch = i;
                            break;
                        } else {
                            continue;
                        }
                    }
                    if (transition.getTarget().getCharSet().contains(c)) {
                        if (firstMatch >= 0) {
                            if (successors[firstMatch].getTarget().isUnAnchoredFinalState(true)) {
                                locals.pushResult(successors[firstMatch]);
                            } else {
                                locals.push(successors[firstMatch]);
                            }
                        }
                        firstMatch = i;
                    }
                }
                if (firstMatch < 0) {
                    if (locals.canPopResult()) {
                        return -1;
                    } else if (locals.canPop()) {
                        return locals.pop();
                    } else {
                        return -1;
                    }
                } else {
                    locals.apply(successors[firstMatch]);
                    locals.incIndex(1);
                    return successors[firstMatch].getTarget().getId();
                }
            }
        }
        return -1;
    }

    public boolean regionMatches(TRegexExecutorLocals locals, int startIndex1, int startIndex2, int length) {
        if (regionMatchesNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            regionMatchesNode = InputRegionMatchesNode.create();
        }
        return regionMatchesNode.execute(locals.getInput(), startIndex1, locals.getInput(), startIndex2, length, null);
    }
}
