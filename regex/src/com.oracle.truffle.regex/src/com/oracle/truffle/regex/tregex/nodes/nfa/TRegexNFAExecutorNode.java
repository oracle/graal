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

package com.oracle.truffle.regex.tregex.nodes.nfa;

import static com.oracle.truffle.api.CompilerDirectives.injectBranchProbability;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;
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
 * expression is executed {@link TRegexOptions#TRegexGenerateDFAThresholdCalls} times, in order to
 * avoid the costly DFA generation on all expressions that are not on any hot code paths.
 */
public final class TRegexNFAExecutorNode extends TRegexExecutorNode {

    private final NFA nfa;
    private final boolean searching;
    private final boolean trackLastGroup;
    private boolean dfaGeneratorBailedOut;

    private TRegexNFAExecutorNode(NFA nfa, int numberOfTransitions) {
        super(nfa.getAst(), numberOfTransitions);
        this.nfa = nfa;
        this.searching = !nfa.getAst().getFlags().isSticky() && !nfa.getAst().getRoot().startsWithCaret() && nfa.getInitialLoopBackTransition() != null;
        this.trackLastGroup = nfa.getAst().getOptions().getFlavor().usesLastGroupResultField();
    }

    private TRegexNFAExecutorNode(TRegexNFAExecutorNode copy) {
        super(copy);
        this.nfa = copy.nfa;
        this.searching = copy.searching;
        this.trackLastGroup = copy.trackLastGroup;
        this.dfaGeneratorBailedOut = copy.dfaGeneratorBailedOut;
    }

    public static TRegexNFAExecutorNode create(NFA nfa) {
        nfa.setInitialLoopBack(false);
        int numberOfTransitions = 0;
        for (int i = 0; i < nfa.getNumberOfTransitions(); i++) {
            if (nfa.getTransitions()[i] != null) {
                nfa.getTransitions()[i].getGroupBoundaries().materializeArrays();
                numberOfTransitions++;
            }
        }
        return new TRegexNFAExecutorNode(nfa, numberOfTransitions);
    }

    @Override
    public TRegexNFAExecutorNode shallowCopy() {
        return new TRegexNFAExecutorNode(this);
    }

    public NFA getNFA() {
        return nfa;
    }

    public void notifyDfaGeneratorBailedOut() {
        dfaGeneratorBailedOut = true;
    }

    @Override
    public String getName() {
        return "nfa";
    }

    @Override
    public boolean isForward() {
        return true;
    }

    @Override
    public boolean isTrivial() {
        return false;
    }

    @Override
    public boolean writesCaptureGroups() {
        return true;
    }

    @Override
    public int getNumberOfStates() {
        return nfa.getNumberOfStates();
    }

    @Override
    public TRegexExecutorLocals createLocals(TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index) {
        return new TRegexNFAExecutorLocals(input, fromIndex, maxIndex, regionFrom, regionTo, index, getNumberOfCaptureGroups(), nfa.getNumberOfStates(), trackLastGroup);
    }

    @Override
    public Object execute(VirtualFrame frame, TRegexExecutorLocals abstractLocals, TruffleString.CodeRange codeRange) {
        TRegexNFAExecutorLocals locals = (TRegexNFAExecutorLocals) abstractLocals;
        CompilerDirectives.ensureVirtualized(locals);

        final int offset = rewindUpTo(locals, locals.getRegionFrom(), nfa.getAnchoredEntry().length - 1, codeRange);
        NFAState anchoredInitialState = nfa.getAnchoredEntry()[offset] == null ? null : nfa.getAnchoredEntry()[offset].getTarget();
        NFAState unAnchoredInitialState = nfa.getUnAnchoredEntry()[offset] == null ? null : nfa.getUnAnchoredEntry()[offset].getTarget();
        if (anchoredInitialState != unAnchoredInitialState && inputAtBegin(locals)) {
            locals.addInitialState(anchoredInitialState.getId());
        }
        if (unAnchoredInitialState != null) {
            locals.addInitialState(unAnchoredInitialState.getId());
        }
        if (locals.curStatesEmpty()) {
            return null;
        }
        while (true) {
            if (dfaGeneratorBailedOut && CompilerDirectives.hasNextTier()) {
                locals.incLoopCount(this);
            }
            if (CompilerDirectives.inInterpreter()) {
                RegexRootNode.checkThreadInterrupted();
            }
            if (injectBranchProbability(CONTINUE_PROBABILITY, inputHasNext(locals))) {
                findNextStates(locals, codeRange);
                // If locals.successorsEmpty() is true, then all of our paths have either been
                // finished, discarded due to priority or failed to match. If we managed to finish
                // any path to a final state (i.e. locals.hasResult() is true), we can terminate
                // the search now.
                // We can also terminate the search now if we were interested only in matches at
                // the very start of the string (i.e. searching is false). Such a search would
                // only have walked through the rest of the string without considering any other
                // paths.
                if (injectBranchProbability(EXIT_PROBABILITY, locals.successorsEmpty() && (!searching || locals.hasResult()))) {
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

    private void findNextStates(TRegexNFAExecutorLocals locals, TruffleString.CodeRange codeRange) {
        int c = inputReadAndDecode(locals, codeRange);
        while (injectBranchProbability(CONTINUE_PROBABILITY, locals.hasNext())) {
            expandState(locals, locals.next(), c, false);
            // If we have found a path to a final state, then we will trim all paths with lower
            // priority (i.e. the rest of the elements in curStates).
            if (injectBranchProbability(EXIT_PROBABILITY, locals.isResultPushed())) {
                return;
            }
        }
        // We are supposed to find the first match of the regular expression. A match starting
        // at a higher index has lower priority and so we give the lowest priority to the loopback
        // transition.
        // The loopback priority has to be lower than the priority of any path completed so far.
        // Therefore, we only follow the loopback if no path has been completed so far
        // (i.e. !locals.hasResult()).
        if (injectBranchProbability(CONTINUE_PROBABILITY, searching && !locals.hasResult() && locals.getIndex() > locals.getFromIndex())) {
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
        while (injectBranchProbability(CONTINUE_PROBABILITY, locals.hasNext())) {
            expandStateAtEnd(locals, nfa.getState(locals.next()), false);
            if (injectBranchProbability(EXIT_PROBABILITY, locals.isResultPushed())) {
                return;
            }
        }
        // We only expand the loopBack state if index > fromIndex. Expanding the loopBack state
        // when index == fromIndex is: a) redundant and b) breaks MustAdvance where the actual
        // loopBack state is only accessible after consuming at least one character.
        if (searching && injectBranchProbability(CONTINUE_PROBABILITY, !locals.hasResult() && locals.getIndex() > locals.getFromIndex())) {
            expandStateAtEnd(locals, nfa.getInitialLoopBackTransition().getTarget(), true);
        }
    }

    private static void expandStateAtEnd(TRegexNFAExecutorLocals locals, NFAState state, boolean isLoopBack) {
        if (state.hasTransitionToFinalState(true)) {
            locals.pushResult(state.getFirstTransitionToFinalState(true), !isLoopBack);
        }
    }
}
