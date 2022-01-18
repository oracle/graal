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

import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.RegexRootNode;
import com.oracle.truffle.regex.charset.CharMatchers;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.nfa.PureNFA;
import com.oracle.truffle.regex.tregex.nfa.PureNFAMap;
import com.oracle.truffle.regex.tregex.nfa.PureNFAState;
import com.oracle.truffle.regex.tregex.nfa.PureNFATransition;
import com.oracle.truffle.regex.tregex.nfa.QuantifierGuard;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputIndexOfStringNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputRegionMatchesNode;
import com.oracle.truffle.regex.tregex.parser.CaseFoldTable;
import com.oracle.truffle.regex.tregex.parser.Token.Quantifier;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.InnerLiteral;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.QuantifiableTerm;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;

/**
 * This regex executor uses a backtracking algorithm on the NFA. It is used for all expressions that
 * cannot be matched with the DFA, such as expressions with backreferences.
 */
public final class TRegexBacktrackingNFAExecutorNode extends TRegexExecutorNode {

    public static final TRegexExecutorNode[] NO_LOOK_AROUND_EXECUTORS = {};

    private final PureNFA nfa;
    private final int nQuantifiers;
    private final int nZeroWidthQuantifiers;
    private final int maxNTransitions;
    private final boolean writesCaptureGroups;
    private final boolean forward;
    private final boolean ignoreCase;
    private final boolean unicode;
    /**
     * Should a backreference to an unmatched capture group succeed or fail?
     */
    private final boolean backrefWithNullTargetFails;
    /**
     * Should the empty check in {@code exitZeroWidth} quantifier guards also check the contents of
     * capture groups? If the capture groups were modified, the empty check passes, even if only the
     * empty string was matched.
     */
    private final boolean monitorCaptureGroupsInEmptyCheck;
    /**
     * When generating NFAs for Ruby regular expressions, the sequence of quantifier guards on a
     * single transition can become quite complex. This necessitates evaluating the effects of each
     * guard one by one in order to arrive at the correct answer. This flag controls whether such
     * detailed handling of the quantifiers is to be used or not.
     */
    private final boolean transitionMatchesStepByStep;
    private final boolean trackLastGroup;
    /**
     * Should the reported lastGroup point to the first group that *begins* instead of the last
     * group that *ends*? This is needed when executing Python lookbehind expressions. The semantics
     * of the lastGroup field should correspond to the left-to-right evaluation of lookbehind
     * assertions in Python, but we run lookbehinds in the right-to-left direction.
     */
    private final boolean returnsFirstGroup;
    private final boolean mustAdvance;
    private final boolean loneSurrogates;
    private final boolean loopbackInitialState;
    private final InnerLiteral innerLiteral;
    @CompilationFinal(dimensions = 1) private final TRegexExecutorNode[] lookAroundExecutors;
    @CompilationFinal(dimensions = 1) private CharMatcher[] matchers;
    private final int[] zeroWidthTermEnclosedCGLow;
    private final int[] zeroWidthQuantifierCGOffsets;

    @Child InputRegionMatchesNode regionMatchesNode;
    @Child InputIndexOfStringNode indexOfNode;
    private final CharMatcher loopbackInitialStateMatcher;

    public TRegexBacktrackingNFAExecutorNode(PureNFAMap nfaMap, PureNFA nfa, TRegexExecutorNode[] lookAroundExecutors, boolean mustAdvance, CompilationBuffer compilationBuffer) {
        RegexASTSubtreeRootNode subtree = nfaMap.getASTSubtree(nfa);
        this.nfa = nfa;
        this.writesCaptureGroups = subtree.hasCaptureGroups();
        this.forward = !(subtree instanceof LookBehindAssertion);
        this.ignoreCase = nfaMap.getAst().getFlags().isIgnoreCase();
        this.unicode = nfaMap.getAst().getFlags().isUnicode();
        this.backrefWithNullTargetFails = nfaMap.getAst().getOptions().getFlavor().backreferencesToUnmatchedGroupsFail();
        this.monitorCaptureGroupsInEmptyCheck = nfaMap.getAst().getOptions().getFlavor().emptyChecksMonitorCaptureGroups();
        this.transitionMatchesStepByStep = nfaMap.getAst().getOptions().getFlavor().emptyChecksMonitorCaptureGroups();
        this.trackLastGroup = nfaMap.getAst().getOptions().getFlavor().usesLastGroupResultField();
        this.returnsFirstGroup = !this.forward && nfaMap.getAst().getOptions().getFlavor().lookBehindsRunLeftToRight();
        this.mustAdvance = mustAdvance;
        this.loneSurrogates = nfaMap.getAst().getProperties().hasLoneSurrogates();
        this.nQuantifiers = nfaMap.getAst().getQuantifierCount().getCount();
        this.nZeroWidthQuantifiers = nfaMap.getAst().getZeroWidthQuantifiables().size();
        List<QuantifiableTerm> zeroWidthQuantifiables = nfaMap.getAst().getZeroWidthQuantifiables();
        this.zeroWidthTermEnclosedCGLow = new int[nZeroWidthQuantifiers];
        this.zeroWidthQuantifierCGOffsets = new int[zeroWidthTermEnclosedCGLow.length + 1];
        int offset = 0;
        for (int i = 0; i < nZeroWidthQuantifiers; i++) {
            QuantifiableTerm quantifiable = zeroWidthQuantifiables.get(i);
            if (quantifiable.isGroup()) {
                Group group = quantifiable.asGroup();
                this.zeroWidthTermEnclosedCGLow[i] = group.getEnclosedCaptureGroupsLow();
                offset += 2 * (group.getEnclosedCaptureGroupsHigh() - group.getEnclosedCaptureGroupsLow());
            }
            this.zeroWidthQuantifierCGOffsets[i + 1] = offset;
        }
        this.lookAroundExecutors = lookAroundExecutors;
        this.loopbackInitialState = nfa == nfaMap.getRoot() && !nfaMap.getAst().getFlags().isSticky() && !nfaMap.getAst().getRoot().startsWithCaret();
        if (nfa == nfaMap.getRoot() && nfaMap.getAst().getProperties().hasInnerLiteral()) {
            this.innerLiteral = nfaMap.getAst().extractInnerLiteral();
        } else {
            this.innerLiteral = null;
        }
        if (loopbackInitialState && innerLiteral == null) {
            CodePointSet initialCharSet = nfaMap.getMergedInitialStateCharSet(compilationBuffer);
            loopbackInitialStateMatcher = initialCharSet == null ? null : CharMatchers.createMatcher(initialCharSet, compilationBuffer);
        } else {
            loopbackInitialStateMatcher = null;
        }
        nfa.materializeGroupBoundaries();
        matchers = new CharMatcher[nfa.getNumberOfStates()];
        int maxTransitions = 0;
        for (int i = 0; i < matchers.length; i++) {
            PureNFAState s = nfa.getState(i);
            if (s.isCharacterClass()) {
                matchers[i] = CharMatchers.createMatcher(s.getCharSet(), compilationBuffer);
            }
            maxTransitions = Math.max(maxTransitions, s.getSuccessors(forward).length);
            s.initIsDeterministic(forward, compilationBuffer);
        }
        this.maxNTransitions = maxTransitions;
    }

    public void initialize(TRegexExecNode rootNode) {
        for (TRegexExecutorNode executor : lookAroundExecutors) {
            executor.setRoot(rootNode);
            insert(executor);
        }
    }

    @Override
    public boolean writesCaptureGroups() {
        return writesCaptureGroups;
    }

    @Override
    public boolean isForward() {
        return forward;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    @Override
    public TRegexExecutorLocals createLocals(Object input, int fromIndex, int index, int maxIndex) {
        return new TRegexBacktrackingNFAExecutorLocals(input, fromIndex, index, maxIndex, getNumberOfCaptureGroups(), nQuantifiers, nZeroWidthQuantifiers, zeroWidthTermEnclosedCGLow,
                        zeroWidthQuantifierCGOffsets, transitionMatchesStepByStep, maxNTransitions, trackLastGroup, returnsFirstGroup);
    }

    private static final int IP_BEGIN = -1;
    private static final int IP_BACKTRACK = -2;
    private static final int IP_END = -3;

    @Override
    public Object execute(TRegexExecutorLocals abstractLocals, TruffleString.CodeRange codeRange) {
        TRegexBacktrackingNFAExecutorLocals locals = (TRegexBacktrackingNFAExecutorLocals) abstractLocals;
        CompilerDirectives.ensureVirtualized(locals);
        if (innerLiteral != null) {
            locals.setIndex(locals.getFromIndex());
            int innerLiteralIndex = findInnerLiteral(locals);
            if (innerLiteralIndex < 0) {
                return null;
            }
            locals.setLastInnerLiteralIndex(innerLiteralIndex);
            locals.setIndex(innerLiteralIndex);
            rewindUpTo(locals, locals.getFromIndex(), innerLiteral.getMaxPrefixSize());
        }
        if (loopbackInitialState) {
            locals.setLastInitialStateIndex(locals.getIndex());
        }
        runMergeExplode(locals, codeRange);
        return locals.popResult();
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    protected void runMergeExplode(TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange) {
        int ip = IP_BEGIN;
        outer: while (true) {
            locals.incLoopCount(this);
            if (CompilerDirectives.inInterpreter()) {
                RegexRootNode.checkThreadInterrupted();
            }
            CompilerAsserts.partialEvaluationConstant(ip);
            if (ip == IP_BEGIN) {
                /*
                 * Begin of the regex match. Here, we select the inital state based on "^".
                 */
                if (nfa.getAnchoredInitialState(isForward()) != nfa.getUnAnchoredInitialState(isForward()) && inputAtBegin(locals)) {
                    ip = nfa.getAnchoredInitialState(isForward()).getId();
                    continue;
                } else {
                    ip = nfa.getUnAnchoredInitialState(isForward()).getId();
                    continue;
                }
            } else if (ip == IP_BACKTRACK) {
                if (locals.canPopResult()) {
                    // there is a result on the stack, break and return it.
                    break;
                } else if (!locals.canPop()) {
                    if (loopbackInitialState) {
                        /*
                         * We are out of states to pop from the stack, so we start with the initial
                         * state again.
                         */
                        assert isForward();
                        locals.setIndex(locals.getLastInitialStateIndex());
                        if (!inputHasNext(locals)) {
                            // break if we are at the end of the string.
                            break;
                        }
                        inputSkip(locals);
                        if (innerLiteral != null) {
                            // we can search for the inner literal again, but only if we tried all
                            // offsets between the last inner literal match and maxPrefixSize.
                            if (locals.getLastInitialStateIndex() == locals.getLastInnerLiteralIndex()) {
                                int innerLiteralIndex = findInnerLiteral(locals);
                                if (innerLiteralIndex < 0) {
                                    break;
                                } else {
                                    locals.setLastInnerLiteralIndex(innerLiteralIndex);
                                    locals.setIndex(innerLiteralIndex);
                                    rewindUpTo(locals, locals.getFromIndex(), innerLiteral.getMaxPrefixSize());
                                }
                            }
                        } else if (loopbackInitialStateMatcher != null) {
                            // find the next character that matches any of the initial state's
                            // successors.
                            assert isForward();
                            while (inputHasNext(locals)) {
                                if (loopbackInitialStateMatcher.match(inputReadAndDecode(locals))) {
                                    break;
                                }
                                inputAdvance(locals);
                            }
                        }
                        locals.setLastInitialStateIndex(locals.getIndex());
                        locals.resetToInitialState();
                        ip = nfa.getUnAnchoredInitialState(isForward()).getId();
                        continue;
                    } else {
                        break;
                    }
                } else {
                    /*
                     * We can pop a state from the stack, and since we don't know which one it will
                     * be we have to dispatch to it with a big switch.
                     */
                    final int nextIp = locals.pop();
                    for (int i = 0; i < nfa.getNumberOfStates(); i++) {
                        int stateId = nfa.getState(i).getId();
                        CompilerAsserts.partialEvaluationConstant(stateId);
                        if (stateId == nextIp) {
                            ip = stateId;
                            continue outer;
                        }
                    }
                    break;
                }
            } else if (ip == IP_END) {
                break;
            }
            /*
             * Compilation of the actual states.
             */
            final PureNFAState curState = nfa.getState(ip);
            CompilerAsserts.partialEvaluationConstant(curState);
            final PureNFATransition[] successors = curState.getSuccessors(isForward());
            CompilerAsserts.partialEvaluationConstant(successors);
            CompilerAsserts.partialEvaluationConstant(successors.length);
            final int nextIp = runState(locals, codeRange, curState);
            for (int i = 0; i < successors.length; i++) {
                int targetIp = successors[i].getTarget(isForward()).getId();
                if (targetIp == nextIp) {
                    CompilerAsserts.partialEvaluationConstant(targetIp);
                    ip = targetIp;
                    continue outer;
                }
            }
            if (nextIp == IP_BACKTRACK) {
                ip = IP_BACKTRACK;
                continue;
            }
            assert nextIp == IP_END;
            break;
        }
    }

    /**
     * Executes the given NFA state.
     */
    @ExplodeLoop
    private int runState(TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, PureNFAState curState) {
        CompilerAsserts.partialEvaluationConstant(curState);
        if (isAcceptableFinalState(curState, locals)) {
            locals.setResult();
            locals.pushResult();
            return IP_END;
        }
        /*
         * Do very expensive operations per-state instead of per-transition, to avoid code size
         * explosion. Drawback: these postponed operations cannot be checked eagerly, so their state
         * will always be pushed to the stack.
         */
        if (curState.isLookAround() && !canInlineLookAroundIntoTransition(curState)) {
            int[] subMatchResult = runSubMatcher(locals.createSubNFALocals(lookAroundExecutorReturnsFirstGroup(curState)), codeRange, curState);
            if (subMatchFailed(curState, subMatchResult)) {
                return IP_BACKTRACK;
            } else if (!curState.isLookAroundNegated() && getLookAroundExecutor(curState).writesCaptureGroups()) {
                locals.overwriteCaptureGroups(subMatchResult);
            }
        }
        if (curState.isBackReference() && !canInlineBackReferenceIntoTransition()) {
            int backrefResult = matchBackReferenceGeneric(locals, locals.getCaptureGroupStart(curState.getBackRefNumber()), locals.getCaptureGroupEnd(curState.getBackRefNumber()));
            if (backrefResult < 0) {
                return IP_BACKTRACK;
            } else {
                locals.setIndex(backrefResult);
            }
        }
        PureNFATransition[] successors = curState.getSuccessors(isForward());
        CompilerAsserts.partialEvaluationConstant(successors);
        CompilerAsserts.partialEvaluationConstant(successors.length);
        boolean atEnd = inputAtEnd(locals);
        int c = atEnd ? 0 : inputReadAndDecode(locals);
        final int index = locals.getIndex();
        if (curState.isDeterministic()) {
            /*
             * We know that in this state only one transition can match at a time, so we can always
             * break after the first match.
             */
            if (transitionMatchesStepByStep) {
                // Use the tryUpdateState method instead of transitionMatches and updateState
                int[] currentFrame = locals.getStackFrameBuffer();
                locals.readFrame(currentFrame);
                for (int i = 0; i < successors.length; i++) {
                    PureNFATransition transition = successors[i];
                    CompilerAsserts.partialEvaluationConstant(transition);
                    if (tryUpdateState(locals, codeRange, transition, index, atEnd, c)) {
                        locals.restoreIndex();
                        return transition.getTarget(isForward()).getId();
                    } else {
                        locals.writeFrame(currentFrame);
                    }
                }
                return IP_BACKTRACK;
            } else {
                for (int i = 0; i < successors.length; i++) {
                    PureNFATransition transition = successors[i];
                    CompilerAsserts.partialEvaluationConstant(transition);
                    if (transitionMatches(locals, codeRange, transition, index, atEnd, c)) {
                        updateState(locals, transition, index);
                        locals.restoreIndex();
                        return transition.getTarget(isForward()).getId();
                    }
                }
                return IP_BACKTRACK;
            }
        } else if (transitionMatchesStepByStep) {
            boolean hasMatchingTransition = false;
            boolean transitionToFinalStateWins = false;
            // We make a copy of the current stack frame, as we will need it to undo speculative
            // changes done by tryUpdateState and to duplicate the current stack frame on demand.
            int[] currentFrame = locals.getStackFrameBuffer();
            locals.readFrame(currentFrame);
            for (int i = successors.length - 1; i >= 0; i--) {
                PureNFATransition transition = successors[i];
                CompilerAsserts.partialEvaluationConstant(transition);
                if (tryUpdateState(locals, codeRange, transition, index, atEnd, c)) {
                    hasMatchingTransition = true;
                    PureNFAState target = transition.getTarget(isForward());
                    CompilerAsserts.partialEvaluationConstant(target);
                    if (isAcceptableFinalState(target, locals)) {
                        locals.setResult();
                        locals.pushResult();
                        transitionToFinalStateWins = true;
                        // Prepare a fresh frame by resetting the current one, as the current
                        // frame is no longer needed.
                        locals.writeFrame(currentFrame);
                    } else {
                        locals.setPc(target.getId());
                        transitionToFinalStateWins = false;
                        // Prepare a fresh frame by pushing a new one.
                        locals.pushFrame(currentFrame);
                    }
                } else {
                    // Prepare a fresh frame by undoing any changes done by tryUpdateState.
                    locals.writeFrame(currentFrame);
                }
            }
            // The stack always one leftover stack frame that is to be discarded
            // (that frame is a copy of the current frame that would have been used by the next
            // transition, if there was any).
            if (transitionToFinalStateWins) {
                // We are successfully terminating the search, hence we can leave the leftover
                // frame be.
                return IP_END;
            } else if (hasMatchingTransition) {
                // We erase the leftover frame from the stack and set the index and stateId to the
                // values stored in the top frame (index is set to locals, the stateId is returned).
                locals.pop();
                return locals.getPc();
            } else {
                // We will backtrack, which will remove the leftover frame from the stack.
                return IP_BACKTRACK;
            }
        } else {
            /*
             * Multiple transitions may match, and we may have to push states to the stack. We avoid
             * one stack push and some code duplication with a three-phased approach:
             *
             * First, we check every transition for a match, and save the boolean result in a bit
             * set.
             *
             * Second, we push (number of matched transitions) - 1 copies of the current state to
             * the stack.
             *
             * Third, we check all the bits in the bit set and update the state copies on the stack
             * accordingly. The highest-priority match becomes this state's successor.
             */
            long[] transitionBitSet = locals.getTransitionBitSet();
            CompilerDirectives.ensureVirtualized(transitionBitSet);
            final int bitSetWords = ((successors.length - 1) >> 6) + 1;
            CompilerAsserts.partialEvaluationConstant(bitSetWords);
            int lastMatch = 0;
            int lastFinal = 0;
            // Fill the bit set.
            // We check the transitions in reverse order, so the last match will be the highest
            // priority one.
            int nMatched = -1;
            for (int iBS = 0; iBS < bitSetWords; iBS++) {
                CompilerAsserts.partialEvaluationConstant(iBS);
                long bs = 0;
                long bit = 1;
                final int iStart = successors.length - (iBS << 6) - 1;
                final int iEnd = Math.max(-1, iStart - (1 << 6));
                CompilerAsserts.partialEvaluationConstant(iStart);
                CompilerAsserts.partialEvaluationConstant(iEnd);
                for (int i = iStart; i > iEnd; i--) {
                    PureNFATransition transition = successors[i];
                    CompilerAsserts.partialEvaluationConstant(transition);
                    if (transitionMatches(locals, codeRange, transition, index, atEnd, c)) {
                        bs |= bit;
                        lastMatch = i;
                        /*
                         * We can avoid pushing final states on the stack, since no more than one
                         * will ever be popped. Therefore, we have a dedicated slot in our "locals"
                         * object that represents the last pushed final state.
                         */
                        if (isAcceptableFinalState(transition.getTarget(isForward()), locals)) {
                            locals.setResult();
                            lastFinal = i;
                            nMatched--;
                        }
                    }
                    bit <<= 1;
                }
                transitionBitSet[iBS] = bs;
            }
            // Create the new stack frames.
            for (int iBS = 0; iBS < bitSetWords; iBS++) {
                nMatched += Long.bitCount(transitionBitSet[iBS]);
            }
            if (nMatched > 0) {
                locals.dupFrame(nMatched);
            }
            // Update the new stack frames.
            for (int iBS = 0; iBS < bitSetWords; iBS++) {
                CompilerAsserts.partialEvaluationConstant(iBS);
                long bs = transitionBitSet[iBS];
                final int iStart = successors.length - (iBS << 6) - 1;
                final int iEnd = Math.max(-1, iStart - (1 << 6));
                CompilerAsserts.partialEvaluationConstant(iStart);
                CompilerAsserts.partialEvaluationConstant(iEnd);
                for (int i = iStart; i > iEnd; i--) {
                    PureNFATransition transition = successors[i];
                    CompilerAsserts.partialEvaluationConstant(transition);
                    PureNFAState target = transition.getTarget(isForward());
                    CompilerAsserts.partialEvaluationConstant(target);
                    if ((bs & 1) != 0) {
                        if (isAcceptableFinalState(target, locals)) {
                            if (i == lastFinal) {
                                locals.pushResult(transition, index);
                            }
                            if (i == lastMatch) {
                                return IP_END;
                            }
                        } else {
                            updateState(locals, transition, index);
                            if (i == lastMatch) {
                                locals.restoreIndex();
                                return target.getId();
                            } else {
                                locals.setPc(target.getId());
                                locals.push();
                            }
                        }
                    }
                    bs >>>= 1;
                }
            }
            return IP_BACKTRACK;
        }
    }

    private boolean isAcceptableFinalState(PureNFAState state, TRegexBacktrackingNFAExecutorLocals locals) {
        return state.isFinalState(isForward()) && !(mustAdvance && locals.getIndex() == locals.getFromIndex());
    }

    public boolean returnsFirstGroup() {
        return returnsFirstGroup;
    }

    private TRegexExecutorNode getLookAroundExecutor(PureNFAState lookAroundState) {
        return lookAroundExecutors[lookAroundState.getLookAroundId()];
    }

    protected boolean lookAroundExecutorIsLiteral(PureNFAState s) {
        return getLookAroundExecutor(s) instanceof TRegexLiteralLookAroundExecutorNode;
    }

    private boolean lookAroundExecutorReturnsFirstGroup(PureNFAState s) {
        TRegexExecutorNode executor = getLookAroundExecutor(s);
        if (executor instanceof TRegexBacktrackingNFAExecutorNode) {
            return ((TRegexBacktrackingNFAExecutorNode) executor).returnsFirstGroup();
        } else {
            return false;
        }
    }

    private boolean canInlineLookAroundIntoTransition(PureNFAState s) {
        return (s.getPredecessors().length == 1 || lookAroundExecutorIsLiteral(s)) && (s.isLookAroundNegated() || !getLookAroundExecutor(s).writesCaptureGroups());
    }

    private boolean checkSubMatcherInline(TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, PureNFATransition transition, PureNFAState target) {
        if (lookAroundExecutorIsLiteral(target)) {
            TRegexLiteralLookAroundExecutorNode literal = (TRegexLiteralLookAroundExecutorNode) getLookAroundExecutor(target);
            int saveIndex = locals.getIndex();
            int saveNextIndex = locals.getNextIndex();
            boolean result = (boolean) literal.execute(locals, codeRange);
            locals.setIndex(saveIndex);
            locals.setNextIndex(saveNextIndex);
            return result;
        } else {
            return !subMatchFailed(target, runSubMatcher(locals.createSubNFALocals(transition, lookAroundExecutorReturnsFirstGroup(target)), codeRange, target));
        }
    }

    protected int[] runSubMatcher(TRegexBacktrackingNFAExecutorLocals subLocals, TruffleString.CodeRange codeRange, PureNFAState lookAroundState) {
        return (int[]) getLookAroundExecutor(lookAroundState).execute(subLocals, codeRange);
    }

    protected static boolean subMatchFailed(PureNFAState curState, Object subMatchResult) {
        return (subMatchResult == null) != curState.isLookAroundNegated();
    }

    @ExplodeLoop
    protected boolean transitionMatches(TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, PureNFATransition transition, int index, boolean atEnd, int c) {
        PureNFAState target = transition.getTarget(isForward());
        CompilerAsserts.partialEvaluationConstant(target);
        if (transition.hasCaretGuard() && index != 0) {
            return false;
        }
        if (transition.hasDollarGuard() && index < locals.getMaxIndex()) {
            return false;
        }
        int nGuards = transition.getQuantifierGuards().length;
        for (int i = isForward() ? 0 : nGuards - 1; isForward() ? i < nGuards : i >= 0; i = inputIncRaw(i)) {
            QuantifierGuard guard = transition.getQuantifierGuards()[i];
            CompilerAsserts.partialEvaluationConstant(guard);
            Quantifier q = guard.getQuantifier();
            CompilerAsserts.partialEvaluationConstant(q);
            switch (isForward() ? guard.getKind() : guard.getKindReverse()) {
                case loop:
                    // retreat if quantifier count is at maximum
                    if (locals.getQuantifierCount(q) == q.getMax()) {
                        return false;
                    }
                    break;
                case exit:
                    // retreat if quantifier count is less than minimum
                    if (locals.getQuantifierCount(q) < q.getMin()) {
                        return false;
                    }
                    break;
                case exitZeroWidth:
                    if (locals.getZeroWidthQuantifierGuardIndex(q) == index &&
                                    (!monitorCaptureGroupsInEmptyCheck || locals.isResultUnmodifiedByZeroWidthQuantifier(q)) &&
                                    (!q.hasIndex() || locals.getQuantifierCount(q) > q.getMin())) {
                        return false;
                    }
                    break;
                case escapeZeroWidth:
                    if (locals.getZeroWidthQuantifierGuardIndex(q) != index ||
                                    (monitorCaptureGroupsInEmptyCheck && !locals.isResultUnmodifiedByZeroWidthQuantifier(q))) {
                        return false;
                    }
                    break;
                case enterEmptyMatch:
                    // retreat if quantifier count is greater or equal to minimum
                    if (locals.getQuantifierCount(q) >= q.getMin()) {
                        return false;
                    }
                    break;
                default:
                    break;
            }
        }
        switch (target.getKind()) {
            case PureNFAState.KIND_INITIAL_OR_FINAL_STATE:
                assert !target.isInitialState(isForward());
                return target.isAnchoredFinalState(isForward()) ? atEnd : true;
            case PureNFAState.KIND_CHARACTER_CLASS:
                return !atEnd && matchers[target.getId()].match(c);
            case PureNFAState.KIND_LOOK_AROUND:
                if (canInlineLookAroundIntoTransition(target)) {
                    return checkSubMatcherInline(locals, codeRange, transition, target);
                } else {
                    return true;
                }
            case PureNFAState.KIND_BACK_REFERENCE:
                if (canInlineBackReferenceIntoTransition()) {
                    int start = getBackRefBoundary(locals, transition, target.getBackRefNumber() * 2, index);
                    int end = getBackRefBoundary(locals, transition, target.getBackRefNumber() * 2 + 1, index);
                    if (start < 0 || end < 0) {
                        return !backrefWithNullTargetFails;
                    }
                    return matchBackReferenceSimple(locals, start, end, index);
                } else {
                    return true;
                }
            case PureNFAState.KIND_EMPTY_MATCH:
                return true;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    protected static int getBackRefBoundary(TRegexBacktrackingNFAExecutorLocals locals, PureNFATransition transition, int cgIndex, int index) {
        return transition.getGroupBoundaries().getUpdateIndices().get(cgIndex) ? index
                        : transition.getGroupBoundaries().getClearIndices().get(cgIndex) ? -1 : locals.getCaptureGroupBoundary(cgIndex);
    }

    @ExplodeLoop
    protected void updateState(TRegexBacktrackingNFAExecutorLocals locals, PureNFATransition transition, int index) {
        CompilerAsserts.partialEvaluationConstant(transition);
        locals.apply(transition, index);
        int nGuards = transition.getQuantifierGuards().length;
        for (int i = isForward() ? 0 : nGuards - 1; isForward() ? i < nGuards : i >= 0; i += (isForward() ? 1 : -1)) {
            QuantifierGuard guard = transition.getQuantifierGuards()[i];
            CompilerAsserts.partialEvaluationConstant(guard);
            Quantifier q = guard.getQuantifier();
            CompilerAsserts.partialEvaluationConstant(q);
            switch (isForward() ? guard.getKind() : guard.getKindReverse()) {
                case enter:
                case loop:
                case loopInc:
                    locals.incQuantifierCount(q);
                    break;
                case exit:
                case exitReset:
                    locals.resetQuantifierCount(q);
                    break;
                case enterZeroWidth:
                    locals.setZeroWidthQuantifierGuardIndex(q);
                    locals.setZeroWidthQuantifierResults(q);
                    break;
                case enterEmptyMatch:
                    if (!transition.hasCaretGuard() && !transition.hasDollarGuard()) {
                        locals.setQuantifierCount(q, q.getMin());
                    } else {
                        locals.incQuantifierCount(q);
                    }
                    break;
                default:
                    break;
            }
        }
        locals.saveIndex(getNewIndex(locals, transition.getTarget(isForward()), index));
    }

    /**
     * This method composes {@link #transitionMatches} with {@link #updateState}. It is somewhat
     * equivalent to the following:
     * 
     * <pre>
     * if (transitionMatches(locals, compactString, transition, index, atEnd, c)) {
     *     updateState(locals, transition, index);
     *     return true;
     * } else {
     *     return false;
     * }
     * </pre>
     *
     * The key difference is that in this method, the {@link #updateState} effects of quantifier
     * guards are evaluated in parallel with their {@link #transitionMatches} assertions. This more
     * detailed behavior is necessary when working with NFA transitions that have complex chains of
     * quantifier guards, such as the ones used in Ruby.
     *
     * <p>
     * NB: This method writes to {@code locals} even if it returns {@code false}.
     * </p>
     */
    @ExplodeLoop
    protected boolean tryUpdateState(TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, PureNFATransition transition, int index, boolean atEnd, int c) {
        CompilerAsserts.partialEvaluationConstant(transition);
        PureNFAState target = transition.getTarget(isForward());
        CompilerAsserts.partialEvaluationConstant(target);
        if (transition.hasCaretGuard() && index != 0) {
            return false;
        }
        if (transition.hasDollarGuard() && index < locals.getMaxIndex()) {
            return false;
        }
        switch (target.getKind()) {
            case PureNFAState.KIND_INITIAL_OR_FINAL_STATE:
                assert !target.isInitialState(isForward());
                if (target.isAnchoredFinalState(isForward()) && !atEnd) {
                    return false;
                }
                break;
            case PureNFAState.KIND_CHARACTER_CLASS:
                if (atEnd || !matchers[target.getId()].match(c)) {
                    return false;
                }
                break;
            case PureNFAState.KIND_LOOK_AROUND:
                if (canInlineLookAroundIntoTransition(target) && !checkSubMatcherInline(locals, codeRange, transition, target)) {
                    return false;
                }
                break;
            case PureNFAState.KIND_BACK_REFERENCE:
                if (canInlineBackReferenceIntoTransition()) {
                    int start = getBackRefBoundary(locals, transition, target.getBackRefNumber() * 2, index);
                    int end = getBackRefBoundary(locals, transition, target.getBackRefNumber() * 2 + 1, index);
                    if ((start < 0 || end < 0) && backrefWithNullTargetFails) {
                        return false;
                    }
                    if (!matchBackReferenceSimple(locals, start, end, index)) {
                        return false;
                    }
                }
                break;
            case PureNFAState.KIND_EMPTY_MATCH:
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        int nGuards = transition.getQuantifierGuards().length;
        for (int i = isForward() ? 0 : nGuards - 1; isForward() ? i < nGuards : i >= 0; i = inputIncRaw(i)) {
            QuantifierGuard guard = transition.getQuantifierGuards()[i];
            CompilerAsserts.partialEvaluationConstant(guard);
            Quantifier q = guard.getQuantifier();
            CompilerAsserts.partialEvaluationConstant(q);
            switch (isForward() ? guard.getKind() : guard.getKindReverse()) {
                case enter:
                case loopInc:
                    locals.incQuantifierCount(q);
                    break;
                case loop:
                    // retreat if quantifier count is at maximum
                    if (locals.getQuantifierCount(q) == q.getMax()) {
                        return false;
                    }
                    locals.incQuantifierCount(q);
                    break;
                case exit:
                    // retreat if quantifier count is less than minimum
                    if (locals.getQuantifierCount(q) < q.getMin()) {
                        return false;
                    }
                    locals.resetQuantifierCount(q);
                    break;
                case exitReset:
                    locals.resetQuantifierCount(q);
                    break;
                case updateCG:
                    locals.setCaptureGroupBoundary(guard.getIndex(), index);
                    break;
                case enterZeroWidth:
                    locals.setZeroWidthQuantifierGuardIndex(q);
                    locals.setZeroWidthQuantifierResults(q);
                    break;
                case exitZeroWidth:
                    if (locals.getZeroWidthQuantifierGuardIndex(q) == index &&
                                    (!monitorCaptureGroupsInEmptyCheck || locals.isResultUnmodifiedByZeroWidthQuantifier(q)) &&
                                    (!q.hasIndex() || locals.getQuantifierCount(q) > q.getMin())) {
                        return false;
                    }
                    break;
                case escapeZeroWidth:
                    if (locals.getZeroWidthQuantifierGuardIndex(q) != index ||
                                    (monitorCaptureGroupsInEmptyCheck && !locals.isResultUnmodifiedByZeroWidthQuantifier(q))) {
                        return false;
                    }
                    break;
                case enterEmptyMatch:
                    // retreat if quantifier count is greater or equal to minimum
                    if (locals.getQuantifierCount(q) >= q.getMin()) {
                        return false;
                    }
                    if (!transition.hasCaretGuard() && !transition.hasDollarGuard()) {
                        locals.setQuantifierCount(q, q.getMin());
                    } else {
                        locals.incQuantifierCount(q);
                    }
                    break;
                default:
                    break;
            }
        }
        locals.saveIndex(getNewIndex(locals, target, index));
        return true;
    }

    private int getNewIndex(TRegexBacktrackingNFAExecutorLocals locals, PureNFAState target, int index) {
        CompilerAsserts.partialEvaluationConstant(target.getKind());
        switch (target.getKind()) {
            case PureNFAState.KIND_INITIAL_OR_FINAL_STATE:
                return index;
            case PureNFAState.KIND_CHARACTER_CLASS:
                return locals.getNextIndex();
            case PureNFAState.KIND_LOOK_AROUND:
                return index;
            case PureNFAState.KIND_BACK_REFERENCE:
                if (canInlineBackReferenceIntoTransition()) {
                    int end = locals.getCaptureGroupEnd(target.getBackRefNumber());
                    int start = locals.getCaptureGroupStart(target.getBackRefNumber());
                    if (start < 0 || end < 0) {
                        // only can happen when backrefWithNullTargetSucceeds == true
                        return index;
                    }
                    int length = end - start;
                    return isForward() ? index + length : index - length;
                } else {
                    return index;
                }
            case PureNFAState.KIND_EMPTY_MATCH:
                return index;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     * Returns {@code true} if we can inline the back-reference check with
     * {@link #matchBackReferenceSimple(TRegexBacktrackingNFAExecutorLocals, int, int, int)}. This
     * is the case when we are not in ignore-case mode and the regex cannot match lone surrogate
     * characters. The reason for lone surrogates being a problem is cases like this:
     *
     * <pre>
     * /(\ud800)\1/.exec("\ud800\ud800\udc00")
     * </pre>
     *
     * Here, the referenced group matches a lone surrogate, but the back-reference must decode the
     * {@code "\ud800\udc00"} to {@code \u10000}, and therefore must not match.
     *
     * See also: testV8 suite, mjsunit/es6/unicode-regexp-backrefs.js
     */
    private boolean canInlineBackReferenceIntoTransition() {
        return !(ignoreCase || loneSurrogates);
    }

    private boolean matchBackReferenceSimple(TRegexBacktrackingNFAExecutorLocals locals, int backrefStart, int backrefEnd, int index) {
        assert !(ignoreCase || loneSurrogates);
        if (regionMatchesNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            regionMatchesNode = insert(InputRegionMatchesNode.create());
        }
        int inputLength = locals.getMaxIndex();
        int backrefLength = backrefEnd - backrefStart;
        if (backrefLength == 0) {
            return true;
        }
        if (isForward() ? index + backrefLength > inputLength : index - backrefLength < 0) {
            return false;
        }
        return regionMatchesNode.execute(locals.getInput(), backrefStart, locals.getInput(), isForward() ? index : index - backrefLength, backrefLength, null, getEncoding());
    }

    private int matchBackReferenceGeneric(TRegexBacktrackingNFAExecutorLocals locals, int backrefStart, int backrefEnd) {
        assert ignoreCase || loneSurrogates;
        if (backrefWithNullTargetFails && (backrefStart < 0 || backrefEnd < 0)) {
            return -1;
        }
        int index = locals.getIndex();
        int inputLength = locals.getMaxIndex();
        int iBR = isForward() ? backrefStart : backrefEnd;
        int i = index;
        while (inputBoundsCheck(iBR, backrefStart, backrefEnd)) {
            if (!inputBoundsCheck(i, 0, inputLength)) {
                return -1;
            }
            int codePointBR = inputReadRaw(locals, iBR);
            if (unicode && inputUTF16IsHighSurrogate(codePointBR) && inputBoundsCheck(inputIncRaw(iBR), backrefStart, backrefEnd)) {
                int lowSurrogate = inputReadRaw(locals, inputIncRaw(iBR));
                if (inputUTF16IsLowSurrogate(lowSurrogate)) {
                    codePointBR = inputUTF16ToCodePoint(codePointBR, lowSurrogate);
                    iBR = inputIncRaw(iBR);
                }
            }
            int codePointI = inputReadRaw(locals, i);
            if (unicode && inputUTF16IsHighSurrogate(codePointI) && inputBoundsCheck(inputIncRaw(i), 0, inputLength)) {
                int lowSurrogate = inputReadRaw(locals, inputIncRaw(i));
                if (inputUTF16IsLowSurrogate(lowSurrogate)) {
                    codePointI = inputUTF16ToCodePoint(codePointI, lowSurrogate);
                    i = inputIncRaw(i);
                }
            }
            if (!(isIgnoreCase() ? equalsIgnoreCase(codePointBR, codePointI) : codePointBR == codePointI)) {
                return -1;
            }
            iBR = inputIncRaw(iBR);
            i = inputIncRaw(i);
        }
        return i;
    }

    private int findInnerLiteral(TRegexBacktrackingNFAExecutorLocals locals) {
        if (indexOfNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            indexOfNode = insert(InputIndexOfStringNode.create());
        }
        return indexOfNode.execute(locals.getInput(), locals.getIndex(), locals.getMaxIndex(), innerLiteral.getLiteralContent(locals.getInput()), innerLiteral.getMaskContent(locals.getInput()),
                        getEncoding());
    }

    private boolean inputBoundsCheck(int i, int min, int max) {
        return forward ? i < max : i > min;
    }

    private boolean equalsIgnoreCase(int a, int b) {
        return CaseFoldTable.equalsIgnoreCase(a, b, unicode ? CaseFoldTable.CaseFoldingAlgorithm.ECMAScriptUnicode : CaseFoldTable.CaseFoldingAlgorithm.ECMAScriptNonUnicode);
    }
}
