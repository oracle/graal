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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.RegexRootNode;
import com.oracle.truffle.regex.tregex.nfa.PureNFA;
import com.oracle.truffle.regex.tregex.nfa.PureNFAMap;
import com.oracle.truffle.regex.tregex.nfa.PureNFAState;
import com.oracle.truffle.regex.tregex.nfa.PureNFATransition;
import com.oracle.truffle.regex.tregex.nfa.QuantifierGuard;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecRootNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputRegionMatchesNode;
import com.oracle.truffle.regex.tregex.parser.CaseFoldTable;
import com.oracle.truffle.regex.tregex.parser.Token.Quantifier;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;

/**
 * This regex executor uses a backtracking algorithm on the NFA. It is used for all expressions that
 * cannot be matched with the DFA, such as expressions with backreferences.
 */
public class TRegexBacktrackingNFAExecutorNode extends TRegexExecutorNode {

    public static final TRegexExecutorNode[] NO_LOOK_AROUND_EXECUTORS = {};

    private final PureNFA nfa;
    private final int nQuantifiers;
    private final int nZeroWidthQuantifiers;
    private final boolean writesCaptureGroups;
    private final boolean forward;
    private final boolean ignoreCase;
    private final boolean unicode;
    private final boolean loneSurrogates;
    @CompilationFinal(dimensions = 1) private final TRegexExecutorNode[] lookAroundExecutors;

    @Child InputRegionMatchesNode regionMatchesNode;

    public TRegexBacktrackingNFAExecutorNode(PureNFAMap nfaMap, PureNFA nfa, TRegexExecutorNode[] lookAroundExecutors) {
        RegexASTSubtreeRootNode subtree = nfaMap.getASTSubtree(nfa);
        this.nfa = nfa;
        this.writesCaptureGroups = subtree.hasCaptureGroups();
        this.forward = !(subtree instanceof LookBehindAssertion);
        this.ignoreCase = nfaMap.getAst().getFlags().isIgnoreCase();
        this.unicode = nfaMap.getAst().getFlags().isUnicode();
        this.loneSurrogates = nfaMap.getAst().getProperties().hasLoneSurrogates();
        this.nQuantifiers = nfaMap.getAst().getQuantifierCount().getCount();
        this.nZeroWidthQuantifiers = nfaMap.getAst().getZeroWidthQuantifierCount().getCount();
        this.lookAroundExecutors = lookAroundExecutors;
        if (nfa == nfaMap.getRoot()) {
            boolean loopback = !nfaMap.getAst().getFlags().isSticky() && !nfaMap.getAst().getRoot().startsWithCaret();
            nfa.setInitialLoopBack(loopback);
            if (nfa.getAnchoredInitialState() != nfa.getUnAnchoredInitialState() && loopback) {
                nfa.getAnchoredInitialState().addLoopBackNext(new PureNFATransition((short) -1,
                                nfa.getAnchoredInitialState(),
                                nfa.getUnAnchoredInitialState(),
                                GroupBoundaries.getEmptyInstance(),
                                false, false, QuantifierGuard.NO_GUARDS));
            }
        }
        nfa.materializeGroupBoundaries();
    }

    public void initialize(TRegexExecRootNode rootNode) {
        for (TRegexExecutorNode executor : lookAroundExecutors) {
            executor.setRoot(rootNode);
        }
    }

    @Override
    public boolean writesCaptureGroups() {
        return writesCaptureGroups;
    }

    public boolean isForward() {
        return forward;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    @Override
    public TRegexExecutorLocals createLocals(Object input, int fromIndex, int index, int maxIndex) {
        return new TRegexBacktrackingNFAExecutorLocals(input, fromIndex, index, maxIndex, getNumberOfCaptureGroups(), nQuantifiers, nZeroWidthQuantifiers);
    }

    @Override
    public Object execute(TRegexExecutorLocals abstractLocals, boolean compactString) {
        TRegexBacktrackingNFAExecutorLocals locals = (TRegexBacktrackingNFAExecutorLocals) abstractLocals;
        CompilerDirectives.ensureVirtualized(locals);
        int pc = (atBegin(locals) ? nfa.getAnchoredInitialState(isForward()) : nfa.getUnAnchoredInitialState(isForward())).getId();
        while (pc >= 0) {
            if (CompilerDirectives.inInterpreter()) {
                RegexRootNode.checkThreadInterrupted();
            }
            pc = runState(locals, compactString, pc);
        }
        return locals.popResult();
    }

    protected boolean atBegin(TRegexBacktrackingNFAExecutorLocals locals) {
        return locals.getIndex() == (isForward() ? 0 : getInputLength(locals));
    }

    @ExplodeLoop
    private int runState(TRegexBacktrackingNFAExecutorLocals locals, boolean compactString, int pc) {
        for (int stateID = 0; stateID < nfa.getNumberOfStates(); stateID++) {
            if (stateID == pc) {
                PureNFAState curState = nfa.getState(stateID);
                CompilerDirectives.isPartialEvaluationConstant(curState);
                if (curState.isFinalState(isForward())) {
                    locals.pushResult();
                    return -1;
                }
                if (curState.isLookAround() && !canInlineLookAroundIntoTransition(curState)) {
                    int[] subMatchResult = runSubMatcher(locals.createSubNFALocals(), compactString, curState);
                    if (subMatchFailed(curState, subMatchResult)) {
                        return backtrack(locals);
                    } else if (!curState.isLookAroundNegated() && lookAroundExecutors[curState.getLookAroundId()].writesCaptureGroups()) {
                        locals.overwriteCaptureGroups(subMatchResult);
                    }
                }
                if (curState.isBackReference() && !canInlineBackReferenceIntoTransition()) {
                    int backrefResult = matchBackReferenceGeneric(locals, locals.getCaptureGroupStart(curState.getBackRefNumber()), locals.getCaptureGroupEnd(curState.getBackRefNumber()));
                    if (backrefResult < 0) {
                        return backtrack(locals);
                    } else {
                        locals.setIndex(backrefResult);
                    }
                }
                int firstMatch = -1;
                PureNFATransition[] successors = curState.getSuccessors(isForward());
                boolean atEnd = isForward() ? locals.getIndex() >= getInputLength(locals) : locals.getIndex() == 0;
                char c = atEnd ? 0 : inputGetChar(locals);
                for (int i = successors.length - 1; i >= 0; i--) {
                    PureNFATransition transition = successors[i];
                    CompilerDirectives.isPartialEvaluationConstant(transition);
                    if (transitionMatches(locals, compactString, transition, atEnd, c)) {
                        if (firstMatch >= 0) {
                            PureNFATransition firstMatchTransition = successors[firstMatch];
                            PureNFAState firstMatchTarget = firstMatchTransition.getTarget(isForward());
                            if (firstMatchTarget.isUnAnchoredFinalState(isForward())) {
                                locals.pushResult(firstMatchTransition);
                            } else {
                                locals.dupFrame();
                                updateState(locals, firstMatchTransition);
                                locals.setPc(firstMatchTarget.getId());
                                locals.push();
                            }
                        }
                        firstMatch = i;
                    }
                }
                if (firstMatch < 0) {
                    return backtrack(locals);
                } else {
                    PureNFATransition firstMatchTransition = successors[firstMatch];
                    PureNFAState firstMatchTarget = firstMatchTransition.getTarget(isForward());
                    updateState(locals, firstMatchTransition);
                    return firstMatchTarget.getId();
                }
            }
        }
        return -1;
    }

    private boolean canInlineLookAroundIntoTransition(PureNFAState s) {
        return s.getPredecessors().length == 1 && (s.isLookAroundNegated() || !lookAroundExecutors[s.getLookAroundId()].writesCaptureGroups());
    }

    protected int[] runSubMatcher(TRegexBacktrackingNFAExecutorLocals subLocals, boolean compactString, PureNFAState lookAroundState) {
        return (int[]) lookAroundExecutors[lookAroundState.getLookAroundId()].execute(subLocals, compactString);
    }

    protected boolean subMatchFailed(PureNFAState curState, int[] subMatchResult) {
        return (subMatchResult == null) != curState.isLookAroundNegated();
    }

    protected void updateState(TRegexBacktrackingNFAExecutorLocals locals, PureNFATransition transition) {
        locals.apply(transition);
        int nGuards = transition.getQuantifierGuards().length;
        for (int i = isForward() ? 0 : nGuards - 1; isForward() ? i < nGuards : i >= 0; i += (isForward() ? 1 : -1)) {
            QuantifierGuard guard = transition.getQuantifierGuards()[i];
            Quantifier q = guard.getQuantifier();
            switch (isForward() ? guard.getKind() : guard.getKindReverse()) {
                case enter:
                case enterInc:
                case loop:
                case loopInc:
                    locals.incQuantifierCount(q.getIndex());
                    break;
                case exit:
                case exitReset:
                    locals.resetQuantifierCount(q.getIndex());
                    break;
                case enterZeroWidth:
                    locals.setZeroWidthQuantifierGuardIndex(q.getZeroWidthIndex());
                    break;
                case enterEmptyMatch:
                    if (!transition.hasCaretGuard() && !transition.hasDollarGuard()) {
                        locals.setQuantifierCount(q.getIndex(), q.getMin());
                    } else {
                        locals.incQuantifierCount(q.getIndex());
                    }
                    break;
                default:
                    break;
            }
        }
        locals.setIndex(getNewIndex(locals, transition.getTarget(isForward())));
    }

    protected boolean transitionMatches(TRegexBacktrackingNFAExecutorLocals locals, boolean compactString, PureNFATransition transition, boolean atEnd, char c) {
        PureNFAState target = transition.getTarget(isForward());
        CompilerDirectives.isPartialEvaluationConstant(target);
        if (transition.hasCaretGuard() && locals.getIndex() != 0) {
            return false;
        }
        if (transition.hasDollarGuard() && locals.getIndex() < getInputLength(locals)) {
            return false;
        }
        int nGuards = transition.getQuantifierGuards().length;
        for (int i = isForward() ? 0 : nGuards - 1; isForward() ? i < nGuards : i >= 0; i = inputIncIndex(i)) {
            QuantifierGuard guard = transition.getQuantifierGuards()[i];
            Quantifier q = guard.getQuantifier();
            switch (isForward() ? guard.getKind() : guard.getKindReverse()) {
                case enter:
                case loop:
                    // retreat if quantifier count is at maximum
                    if (locals.getQuantifierCount(q.getIndex()) == q.getMax()) {
                        return false;
                    }
                    break;
                case exit:
                    // retreat if quantifier count is less than minimum
                    if (locals.getQuantifierCount(q.getIndex()) < q.getMin()) {
                        return false;
                    }
                    break;
                case exitZeroWidth:
                    if (locals.getZeroWidthQuantifierGuardIndex(q.getZeroWidthIndex()) == locals.getIndex() && (!q.hasIndex() || locals.getQuantifierCount(q.getIndex()) > q.getMin())) {
                        return false;
                    }
                    break;
                case enterEmptyMatch:
                    // retreat if quantifier count is greater or equal to minimum
                    if (locals.getQuantifierCount(q.getIndex()) >= q.getMin()) {
                        return false;
                    }
                    break;
                default:
                    break;
            }
        }
        switch (target.getKind()) {
            case PureNFAState.KIND_INITIAL_OR_FINAL_STATE:
                assert !target.isAnchoredInitialState(isForward()) : target.isUnAnchoredInitialState(isForward());
                return target.isUnAnchoredInitialState(isForward()) ? !atEnd : (target.isAnchoredFinalState(isForward()) ? atEnd : true);
            case PureNFAState.KIND_CHARACTER_CLASS:
                return !atEnd && target.getCharSet().contains(c);
            case PureNFAState.KIND_LOOK_AROUND:
                if (canInlineLookAroundIntoTransition(target)) {
                    return !subMatchFailed(target, runSubMatcher(locals.createSubNFALocals(transition), compactString, target));
                } else {
                    return true;
                }
            case PureNFAState.KIND_BACK_REFERENCE:
                if (canInlineBackReferenceIntoTransition()) {
                    int start = getBackRefBoundary(locals, transition, target.getBackRefNumber() * 2);
                    int end = getBackRefBoundary(locals, transition, target.getBackRefNumber() * 2 + 1);
                    if (start < 0 || end < 0) {
                        return true;
                    }
                    return matchBackReferenceSimple(locals, start, end);
                } else {
                    return true;
                }
            case PureNFAState.KIND_EMPTY_MATCH:
                return true;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException();
        }
    }

    protected int getBackRefBoundary(TRegexBacktrackingNFAExecutorLocals locals, PureNFATransition transition, int index) {
        return transition.getGroupBoundaries().getUpdateIndices().get(index) ? locals.getIndex()
                        : transition.getGroupBoundaries().getClearIndices().get(index) ? -1 : locals.getCaptureGroupBoundary(index);
    }

    private int getNewIndex(TRegexBacktrackingNFAExecutorLocals locals, PureNFAState target) {
        switch (target.getKind()) {
            case PureNFAState.KIND_INITIAL_OR_FINAL_STATE:
                return inputIncIndex(locals);
            case PureNFAState.KIND_CHARACTER_CLASS:
                return inputIncIndex(locals);
            case PureNFAState.KIND_LOOK_AROUND:
                return locals.getIndex();
            case PureNFAState.KIND_BACK_REFERENCE:
                if (canInlineBackReferenceIntoTransition()) {
                    int end = locals.getCaptureGroupEnd(target.getBackRefNumber());
                    int start = locals.getCaptureGroupStart(target.getBackRefNumber());
                    if (start < 0 || end < 0) {
                        return locals.getIndex();
                    }
                    int length = end - start;
                    return isForward() ? locals.getIndex() + length : locals.getIndex() - length;
                } else {
                    return locals.getIndex();
                }
            case PureNFAState.KIND_EMPTY_MATCH:
                return locals.getIndex();
            default:
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException();
        }
    }

    private static int backtrack(TRegexBacktrackingNFAExecutorLocals locals) {
        if (locals.canPopResult()) {
            return -1;
        } else if (locals.canPop()) {
            return locals.pop();
        } else {
            return -1;
        }
    }

    private boolean canInlineBackReferenceIntoTransition() {
        return !(ignoreCase || loneSurrogates);
    }

    private boolean matchBackReferenceSimple(TRegexBacktrackingNFAExecutorLocals locals, int backrefStart, int backrefEnd) {
        assert !(ignoreCase || loneSurrogates);
        if (regionMatchesNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            regionMatchesNode = InputRegionMatchesNode.create();
        }
        int index = locals.getIndex();
        int inputLength = getInputLength(locals);
        int backrefLength = backrefEnd - backrefStart;
        if (backrefLength == 0) {
            return true;
        }
        if (isForward() ? index + backrefLength > inputLength : index - backrefLength < 0) {
            return false;
        }
        return regionMatchesNode.execute(locals.getInput(), backrefStart, locals.getInput(), isForward() ? index : index - backrefLength, backrefLength, null);
    }

    private int matchBackReferenceGeneric(TRegexBacktrackingNFAExecutorLocals locals, int backrefStart, int backrefEnd) {
        assert ignoreCase || loneSurrogates;
        int index = locals.getIndex();
        int inputLength = getInputLength(locals);
        int iBR = isForward() ? backrefStart : backrefEnd;
        int i = index;
        while (inputBoundsCheck(iBR, backrefStart, backrefEnd)) {
            if (!inputBoundsCheck(i, 0, inputLength)) {
                return -1;
            }
            int codePointBR = inputGetChar(locals, iBR);
            if (unicode && inputIsHighSurrogate((char) codePointBR) && inputBoundsCheck(inputIncIndex(iBR), backrefStart, backrefEnd)) {
                char lowSurrogate = inputGetChar(locals, inputIncIndex(iBR));
                if (inputIsLowSurrogate(lowSurrogate)) {
                    codePointBR = inputToCodePoint((char) codePointBR, lowSurrogate);
                    iBR = inputIncIndex(iBR);
                }
            }
            int codePointI = inputGetChar(locals, i);
            if (unicode && inputIsHighSurrogate((char) codePointI) && inputBoundsCheck(inputIncIndex(i), 0, inputLength)) {
                char lowSurrogate = inputGetChar(locals, inputIncIndex(i));
                if (inputIsLowSurrogate(lowSurrogate)) {
                    codePointI = inputToCodePoint((char) codePointI, lowSurrogate);
                    i = inputIncIndex(i);
                }
            }
            if (!(isIgnoreCase() ? equalsIgnoreCase(codePointBR, codePointI) : codePointBR == codePointI)) {
                return -1;
            }
            iBR = inputIncIndex(iBR);
            i = inputIncIndex(i);
        }
        return i;
    }

    private char inputGetChar(TRegexBacktrackingNFAExecutorLocals locals) {
        return inputGetChar(locals, locals.getIndex());
    }

    private char inputGetChar(TRegexBacktrackingNFAExecutorLocals locals, int index) {
        return forward ? getCharAt(locals, index) : getCharAt(locals, index - 1);
    }

    private int inputIncIndex(TRegexBacktrackingNFAExecutorLocals locals) {
        return inputIncIndex(locals.getIndex());
    }

    private int inputIncIndex(int i) {
        return forward ? i + 1 : i - 1;
    }

    private boolean inputBoundsCheck(int i, int min, int max) {
        return forward ? i < max : i > min;
    }

    protected boolean inputIsLowSurrogate(char c) {
        return forward ? Character.isLowSurrogate(c) : Character.isHighSurrogate(c);
    }

    protected boolean inputIsHighSurrogate(char c) {
        return forward ? Character.isHighSurrogate(c) : Character.isLowSurrogate(c);
    }

    protected int inputToCodePoint(char highSurrogate, char lowSurrogate) {
        return forward ? Character.toCodePoint(highSurrogate, lowSurrogate) : Character.toCodePoint(lowSurrogate, highSurrogate);
    }

    private boolean equalsIgnoreCase(int a, int b) {
        return CaseFoldTable.equalsIgnoreCase(a, b, unicode ? CaseFoldTable.CaseFoldingAlgorithm.ECMAScriptUnicode : CaseFoldTable.CaseFoldingAlgorithm.ECMAScriptNonUnicode);
    }
}
