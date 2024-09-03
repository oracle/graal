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

import static com.oracle.truffle.api.CompilerDirectives.LIKELY_PROBABILITY;
import static com.oracle.truffle.api.CompilerDirectives.injectBranchProbability;

import java.util.List;

import org.graalvm.collections.Pair;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.RegexRootNode;
import com.oracle.truffle.regex.charset.CharMatchers;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntRingBuffer;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.nfa.PureNFA;
import com.oracle.truffle.regex.tregex.nfa.PureNFAState;
import com.oracle.truffle.regex.tregex.nfa.PureNFATransition;
import com.oracle.truffle.regex.tregex.nfa.TransitionGuard;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorBaseNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputOps;
import com.oracle.truffle.regex.tregex.parser.CaseFoldData;
import com.oracle.truffle.regex.tregex.parser.MultiCharacterCaseFolding;
import com.oracle.truffle.regex.tregex.parser.Token.Quantifier;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.InnerLiteral;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.QuantifiableTerm;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;

/**
 * This regex executor uses a backtracking algorithm on the NFA. It is used for all expressions that
 * cannot be matched with the DFA, such as expressions with backreferences.
 */
public final class TRegexBacktrackingNFAExecutorNode extends TRegexBacktrackerSubExecutorNode {

    private static final int FLAG_WRITES_CAPTURE_GROUPS = 1 << 0;
    private static final int FLAG_FORWARD = 1 << 1;
    private static final int FLAG_BACKREF_WITH_NULL_TARGET_FAILS = 1 << 2;
    private static final int FLAG_MONITOR_CAPTURE_GROUPS_IN_EMPTY_CHECK = 1 << 3;
    private static final int FLAG_TRANSITION_MATCHES_STEP_BY_STEP = 1 << 4;
    private static final int FLAG_EMPTY_CHECKS_ON_MANDATORY_LOOP_ITERATIONS = 1 << 5;
    private static final int FLAG_TRACK_LAST_GROUP = 1 << 6;
    private static final int FLAG_RETURNS_FIRST_GROUP = 1 << 7;
    private static final int FLAG_MUST_ADVANCE = 1 << 8;
    private static final int FLAG_LONE_SURROGATES = 1 << 9;
    private static final int FLAG_LOOPBACK_INITIAL_STATE = 1 << 10;
    private static final int FLAG_USE_MERGE_EXPLODE = 1 << 11;
    private static final int FLAG_RECURSIVE_BACK_REFERENCES = 1 << 12;
    private static final int FLAG_BACKREF_IGNORE_CASE_MULTI_CHARACTER_EXPANSION = 1 << 13;
    private static final int FLAG_MATCH_BOUNDARY_ASSERTIONS = 1 << 14;

    private final PureNFA nfa;
    private final int numberOfStates;
    private final int nQuantifiers;
    private final int nZeroWidthQuantifiers;
    private final int maxNTransitions;
    private final int flags;
    private final InnerLiteral innerLiteral;
    @CompilationFinal(dimensions = 1) private final CharMatcher[] matchers;
    @CompilationFinal(dimensions = 1) private final Quantifier[] quantifiers;
    @CompilationFinal(dimensions = 1) private final Quantifier[] zeroWidthQuantifiers;
    private final int[] zeroWidthTermEnclosedCGLow;
    private final int[] zeroWidthQuantifierCGOffsets;
    private final RegexFlavor.EqualsIgnoreCasePredicate equalsIgnoreCase;
    private final CaseFoldData.CaseFoldAlgorithm multiCharacterExpansionCaseFoldAlgorithm;

    @Child TruffleString.RegionEqualByteIndexNode regionMatchesNode;
    @Child TruffleString.ByteIndexOfStringNode indexOfNode;
    private final CharMatcher loopbackInitialStateMatcher;

    public TRegexBacktrackingNFAExecutorNode(RegexAST ast, PureNFA nfa, int numberOfStates, int numberOfTransitions, TRegexExecutorBaseNode[] subExecutors, boolean mustAdvance,
                    CompilationBuffer compilationBuffer) {
        super(ast, numberOfTransitions, subExecutors);
        this.numberOfStates = numberOfStates;
        RegexASTSubtreeRootNode subtree = nfa.getASTSubtree(ast);
        this.nfa = nfa;
        this.flags = createFlags(ast, nfa, mustAdvance, subtree, numberOfStates, numberOfTransitions);
        this.nQuantifiers = ast.getQuantifierCount();
        this.quantifiers = ast.getQuantifierArray();
        this.nZeroWidthQuantifiers = ast.getZeroWidthQuantifiables().size();
        List<QuantifiableTerm> zeroWidthQuantifiables = ast.getZeroWidthQuantifiables();
        this.zeroWidthQuantifiers = new Quantifier[nZeroWidthQuantifiers];
        this.zeroWidthTermEnclosedCGLow = new int[nZeroWidthQuantifiers];
        this.zeroWidthQuantifierCGOffsets = new int[zeroWidthTermEnclosedCGLow.length + 1];
        int offset = 0;
        for (int i = 0; i < nZeroWidthQuantifiers; i++) {
            QuantifiableTerm quantifiable = zeroWidthQuantifiables.get(i);
            if (quantifiable.isGroup()) {
                Group group = quantifiable.asGroup();
                this.zeroWidthTermEnclosedCGLow[i] = group.getCaptureGroupsLow();
                offset += 2 * (group.getCaptureGroupsHigh() - group.getCaptureGroupsLow());
            }
            this.zeroWidthQuantifierCGOffsets[i + 1] = offset;
            this.zeroWidthQuantifiers[quantifiable.getQuantifier().getZeroWidthIndex()] = quantifiable.getQuantifier();
        }
        if (nfa.isRoot() && ast.getProperties().hasInnerLiteral()) {
            this.innerLiteral = ast.extractInnerLiteral();
        } else {
            this.innerLiteral = null;
        }
        this.equalsIgnoreCase = ast.getOptions().getFlavor().getEqualsIgnoreCasePredicate(ast);
        if (isBackreferenceIgnoreCaseMultiCharExpansion() && ast.getProperties().hasBackReferences()) {
            this.multiCharacterExpansionCaseFoldAlgorithm = ast.getOptions().getFlavor().getCaseFoldAlgorithm(ast);
        } else {
            this.multiCharacterExpansionCaseFoldAlgorithm = null;
        }
        if (isLoopbackInitialState() && innerLiteral == null) {
            CodePointSet initialCharSet = nfa.getMergedInitialStateCharSet(ast, compilationBuffer);
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
            maxTransitions = Math.max(maxTransitions, s.getSuccessors().length);
            s.initIsDeterministic(compilationBuffer);
        }
        for (TRegexExecutorBaseNode subExecutor : subExecutors) {
            if (subExecutor instanceof TRegexBacktrackingNFAExecutorNode) {
                maxTransitions = Math.max(maxTransitions, ((TRegexBacktrackingNFAExecutorNode) subExecutor).maxNTransitions);
            }
        }
        this.maxNTransitions = maxTransitions;
    }

    private TRegexBacktrackingNFAExecutorNode(TRegexBacktrackingNFAExecutorNode copy) {
        super(copy);
        this.nfa = copy.nfa;
        this.numberOfStates = copy.numberOfStates;
        this.quantifiers = copy.quantifiers;
        this.nQuantifiers = copy.nQuantifiers;
        this.zeroWidthQuantifiers = copy.zeroWidthQuantifiers;
        this.nZeroWidthQuantifiers = copy.nZeroWidthQuantifiers;
        this.maxNTransitions = copy.maxNTransitions;
        this.flags = copy.flags;
        this.innerLiteral = copy.innerLiteral;
        this.matchers = copy.matchers;
        this.zeroWidthTermEnclosedCGLow = copy.zeroWidthTermEnclosedCGLow;
        this.zeroWidthQuantifierCGOffsets = copy.zeroWidthQuantifierCGOffsets;
        this.equalsIgnoreCase = copy.equalsIgnoreCase;
        this.loopbackInitialStateMatcher = copy.loopbackInitialStateMatcher;
        this.multiCharacterExpansionCaseFoldAlgorithm = copy.multiCharacterExpansionCaseFoldAlgorithm;
    }

    @Override
    public TRegexBacktrackerSubExecutorNode shallowCopy() {
        return new TRegexBacktrackingNFAExecutorNode(this);
    }

    @Override
    public int getNumberOfStates() {
        return numberOfStates;
    }

    private static int createFlags(RegexAST ast, PureNFA nfa, boolean mustAdvance, RegexASTSubtreeRootNode subtree, int nStates, int nTransitions) {
        int flags = 0;
        flags = setFlag(flags, FLAG_WRITES_CAPTURE_GROUPS, subtree.hasCaptureGroups());
        flags = setFlag(flags, FLAG_FORWARD, !(subtree instanceof LookBehindAssertion));
        flags = setFlag(flags, FLAG_BACKREF_WITH_NULL_TARGET_FAILS, ast.getOptions().getFlavor().backreferencesToUnmatchedGroupsFail());
        flags = setFlag(flags, FLAG_MONITOR_CAPTURE_GROUPS_IN_EMPTY_CHECK, ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups());
        flags = setFlag(flags, FLAG_TRANSITION_MATCHES_STEP_BY_STEP, ast.getOptions().getFlavor().matchesTransitionsStepByStep());
        flags = setFlag(flags, FLAG_EMPTY_CHECKS_ON_MANDATORY_LOOP_ITERATIONS, ast.getOptions().getFlavor().emptyChecksOnMandatoryLoopIterations());
        flags = setFlag(flags, FLAG_TRACK_LAST_GROUP, ast.getOptions().getFlavor().usesLastGroupResultField());
        flags = setFlag(flags, FLAG_RETURNS_FIRST_GROUP, !isFlagSet(flags, FLAG_FORWARD) && ast.getOptions().getFlavor().lookBehindsRunLeftToRight());
        flags = setFlag(flags, FLAG_MUST_ADVANCE, mustAdvance);
        flags = setFlag(flags, FLAG_LONE_SURROGATES, ast.getProperties().hasLoneSurrogates());
        flags = setFlag(flags, FLAG_LOOPBACK_INITIAL_STATE, nfa.isRoot() && !ast.getFlags().isSticky() && !ast.getRoot().startsWithCaret());
        flags = setFlag(flags, FLAG_USE_MERGE_EXPLODE, nStates <= ast.getOptions().getMaxBackTrackerCompileSize() && nTransitions <= ast.getOptions().getMaxBackTrackerCompileSize());
        flags = setFlag(flags, FLAG_RECURSIVE_BACK_REFERENCES, ast.getProperties().hasRecursiveBackReferences());
        flags = setFlag(flags, FLAG_BACKREF_IGNORE_CASE_MULTI_CHARACTER_EXPANSION, ast.getOptions().getFlavor().backreferenceIgnoreCaseMultiCharExpansion() && ast.getProperties().hasBackReferences());
        flags = setFlag(flags, FLAG_MATCH_BOUNDARY_ASSERTIONS, ast.getProperties().hasMatchBoundaryAssertions());
        return flags;
    }

    @Override
    public boolean writesCaptureGroups() {
        return isFlagSet(FLAG_WRITES_CAPTURE_GROUPS);
    }

    @Override
    public String getName() {
        return "bt";
    }

    @Override
    public boolean isForward() {
        return isFlagSet(FLAG_FORWARD);
    }

    /**
     * Should a backreference to an unmatched capture group succeed or fail?
     */
    public boolean isBackrefWithNullTargetFails() {
        return isFlagSet(FLAG_BACKREF_WITH_NULL_TARGET_FAILS);
    }

    /**
     * Should the empty check in {@code exitZeroWidth} quantifier guards also check the contents of
     * capture groups? If the capture groups were modified, the empty check passes, even if only the
     * empty string was matched.
     */
    public boolean isMonitorCaptureGroupsInEmptyCheck() {
        return isFlagSet(FLAG_MONITOR_CAPTURE_GROUPS_IN_EMPTY_CHECK);
    }

    /**
     * When generating NFAs for Ruby regular expressions, the sequence of quantifier guards on a
     * single transition can become quite complex. This necessitates evaluating the effects of each
     * guard one by one in order to arrive at the correct answer. This flag controls whether such
     * detailed handling of the quantifiers is to be used or not.
     */
    public boolean isTransitionMatchesStepByStep() {
        return isFlagSet(FLAG_TRANSITION_MATCHES_STEP_BY_STEP);
    }

    public boolean isEmptyChecksOnMandatoryLoopIterations() {
        return isFlagSet(FLAG_EMPTY_CHECKS_ON_MANDATORY_LOOP_ITERATIONS);
    }

    public boolean isTrackLastGroup() {
        return isFlagSet(FLAG_TRACK_LAST_GROUP);
    }

    public boolean isMustAdvance() {
        return isFlagSet(FLAG_MUST_ADVANCE);
    }

    public boolean isLoneSurrogates() {
        return isFlagSet(FLAG_LONE_SURROGATES);
    }

    public boolean isLoopbackInitialState() {
        return isFlagSet(FLAG_LOOPBACK_INITIAL_STATE);
    }

    public boolean isUseMergeExplode() {
        return isFlagSet(FLAG_USE_MERGE_EXPLODE);
    }

    public boolean isRecursiveBackreferences() {
        return isFlagSet(FLAG_RECURSIVE_BACK_REFERENCES);
    }

    public boolean isBackreferenceIgnoreCaseMultiCharExpansion() {
        return isFlagSet(FLAG_BACKREF_IGNORE_CASE_MULTI_CHARACTER_EXPANSION);
    }

    public boolean isMatchBoundaryAssertions() {
        return isFlagSet(FLAG_MATCH_BOUNDARY_ASSERTIONS);
    }

    private boolean isFlagSet(int flag) {
        return isFlagSet(flags, flag);
    }

    private Quantifier getQuantifier(long guard) {
        CompilerAsserts.partialEvaluationConstant(guard);
        int quantifierIndex = TransitionGuard.getQuantifierIndex(guard);
        CompilerAsserts.partialEvaluationConstant(quantifierIndex);
        return quantifiers[quantifierIndex];
    }

    private Quantifier getZeroWidthQuantifier(long guard) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(guard);
        int zeroWidthQuantifierIndex = TransitionGuard.getZeroWidthQuantifierIndex(guard);
        CompilerAsserts.partialEvaluationConstant(zeroWidthQuantifiers);
        CompilerAsserts.partialEvaluationConstant(zeroWidthQuantifierIndex);
        Quantifier zeroWidthQuantifier = zeroWidthQuantifiers[zeroWidthQuantifierIndex];
        CompilerAsserts.partialEvaluationConstant(zeroWidthQuantifier);
        return zeroWidthQuantifier;
    }

    @Override
    public TRegexExecutorLocals createLocals(TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index) {
        return TRegexBacktrackingNFAExecutorLocals.create(input, fromIndex, maxIndex, regionFrom, regionTo, index, getNumberOfCaptureGroups(),
                        nQuantifiers, nZeroWidthQuantifiers, zeroWidthTermEnclosedCGLow, zeroWidthQuantifierCGOffsets,
                        isTransitionMatchesStepByStep(), maxNTransitions, isTrackLastGroup(), returnsFirstGroup(), isRecursiveBackreferences(), isBackreferenceIgnoreCaseMultiCharExpansion(),
                        isMatchBoundaryAssertions());
    }

    private static final int IP_BEGIN = -1;
    private static final int IP_BACKTRACK = -2;
    private static final int IP_END = -3;

    @Override
    public Object execute(VirtualFrame frame, TRegexExecutorLocals abstractLocals, TruffleString.CodeRange codeRange) {
        TRegexBacktrackingNFAExecutorLocals locals = (TRegexBacktrackingNFAExecutorLocals) abstractLocals;
        if (innerLiteral != null) {
            locals.setIndex(locals.getFromIndex());
            int innerLiteralIndex = findInnerLiteral(locals);
            if (injectBranchProbability(EXIT_PROBABILITY, innerLiteralIndex < 0)) {
                return null;
            }
            locals.setLastInnerLiteralIndex(innerLiteralIndex);
            if (innerLiteral.getMaxPrefixSize() < 0) {
                locals.setIndex(locals.getFromIndex());
            } else {
                locals.setIndex(innerLiteralIndex);
                rewindUpTo(locals, locals.getFromIndex(), innerLiteral.getMaxPrefixSize(), codeRange);
            }
        }
        if (isLoopbackInitialState()) {
            locals.setLastInitialStateIndex(locals.getIndex());
        }
        if (isUseMergeExplode()) {
            runMergeExplode(frame, locals, codeRange);
        } else {
            runSlowPath(frame.materialize(), locals, codeRange);
        }
        return locals.popResult();
    }

    @TruffleBoundary
    private void runSlowPath(MaterializedFrame frame, TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange) {
        runMergeExplode(frame, locals, codeRange);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    private void runMergeExplode(VirtualFrame frame, TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange) {
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
                if (nfa.getAnchoredInitialState() != nfa.getUnAnchoredInitialState() && inputAtBegin(locals)) {
                    ip = nfa.getAnchoredInitialState().getId();
                    continue;
                } else {
                    ip = nfa.getUnAnchoredInitialState().getId();
                    continue;
                }
            } else if (ip == IP_BACKTRACK) {
                if (injectBranchProbability(EXIT_PROBABILITY, locals.canPopResult())) {
                    // there is a result on the stack, break and return it.
                    break;
                } else if (!locals.canPop()) {
                    if (isLoopbackInitialState()) {
                        /*
                         * We are out of states to pop from the stack, so we start with the initial
                         * state again.
                         */
                        assert isForward();
                        locals.setIndex(locals.getLastInitialStateIndex());
                        if (injectBranchProbability(EXIT_PROBABILITY, !inputHasNext(locals))) {
                            // break if we are at the end of the string.
                            break;
                        }
                        inputSkip(locals, codeRange);
                        if (innerLiteral != null) {
                            // we can search for the inner literal again, but only if we tried all
                            // offsets between the last inner literal match and maxPrefixSize.
                            if (locals.getLastInitialStateIndex() == locals.getLastInnerLiteralIndex()) {
                                int innerLiteralIndex = findInnerLiteral(locals);
                                if (injectBranchProbability(EXIT_PROBABILITY, innerLiteralIndex < 0)) {
                                    break;
                                } else {
                                    if (innerLiteral.getMaxPrefixSize() < 0) {
                                        locals.setIndex(locals.getLastInnerLiteralIndex());
                                        locals.setLastInnerLiteralIndex(innerLiteralIndex);
                                    } else {
                                        locals.setLastInnerLiteralIndex(innerLiteralIndex);
                                        locals.setIndex(innerLiteralIndex);
                                        rewindUpTo(locals, locals.getFromIndex(), innerLiteral.getMaxPrefixSize(), codeRange);
                                    }
                                }
                            }
                        } else if (loopbackInitialStateMatcher != null) {
                            // find the next character that matches any of the initial state's
                            // successors.
                            assert isForward();
                            while (injectBranchProbability(CONTINUE_PROBABILITY, inputHasNext(locals))) {
                                if (injectBranchProbability(EXIT_PROBABILITY, loopbackInitialStateMatcher.match(inputReadAndDecode(locals, codeRange)))) {
                                    break;
                                }
                                inputAdvance(locals);
                            }
                        }
                        locals.setLastInitialStateIndex(locals.getIndex());
                        locals.resetToInitialState();
                        ip = nfa.getUnAnchoredInitialState().getId();
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
            final PureNFATransition[] successors = curState.getSuccessors();
            CompilerAsserts.partialEvaluationConstant(successors);
            CompilerAsserts.partialEvaluationConstant(successors.length);
            final int nextIp = runState(frame, locals, codeRange, curState);
            for (int i = 0; i < successors.length; i++) {
                int targetIp = successors[i].getTarget().getId();
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
    private int runState(VirtualFrame frame, TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, PureNFAState curState) {
        CompilerAsserts.partialEvaluationConstant(curState);
        if (injectBranchProbability(EXIT_PROBABILITY, isAcceptableFinalState(curState, locals))) {
            locals.setResult();
            locals.pushResult();
            return IP_END;
        }
        /*
         * Do very expensive operations per-state instead of per-transition, to avoid code size
         * explosion. Drawback: these postponed operations cannot be checked eagerly, so their state
         * will always be pushed to the stack.
         */
        if (curState.isSubMatcher() && !canInlineLookAroundIntoTransition(curState)) {
            TRegexBacktrackingNFAExecutorLocals subLocals = locals.createSubNFALocals(subExecutorReturnsFirstGroup(curState));
            int[] subMatchResult = runSubMatcher(frame, subLocals, codeRange, curState);
            if (injectBranchProbability(EXIT_PROBABILITY, subMatchFailed(curState, subMatchResult))) {
                return IP_BACKTRACK;
            } else {
                if (!curState.isSubMatcherNegated() && getSubExecutor(curState).writesCaptureGroups()) {
                    locals.overwriteCaptureGroups(subMatchResult);
                }
                if (!curState.isLookAround()) {
                    locals.saveIndex(subLocals.getIndex());
                    locals.restoreIndex();
                }
            }
        }
        if (curState.isBackReference() && !canInlineBackReferenceIntoTransition(curState)) {
            int backrefResult = matchBackReferenceGeneric(locals, curState, codeRange);
            if (injectBranchProbability(EXIT_PROBABILITY, backrefResult < 0)) {
                return IP_BACKTRACK;
            } else {
                locals.setIndex(backrefResult);
            }
        }
        PureNFATransition[] successors = curState.getSuccessors();
        CompilerAsserts.partialEvaluationConstant(successors);
        CompilerAsserts.partialEvaluationConstant(successors.length);
        boolean atEnd = inputAtEnd(locals);
        int c = atEnd ? 0 : inputReadAndDecode(locals, codeRange);
        final int index = locals.getIndex();
        if (curState.isDeterministic()) {
            /*
             * We know that in this state only one transition can match at a time, so we can always
             * break after the first match.
             */
            if (isTransitionMatchesStepByStep()) {
                // Use the tryUpdateState method instead of transitionMatches and updateState
                int[] currentFrame = locals.getStackFrameBuffer();
                locals.readFrame(currentFrame);
                for (int i = 0; i < successors.length; i++) {
                    PureNFATransition transition = successors[i];
                    CompilerAsserts.partialEvaluationConstant(transition);
                    if (tryUpdateState(frame, locals, codeRange, transition, index, atEnd, c)) {
                        locals.restoreIndex();
                        return transition.getTarget().getId();
                    } else {
                        locals.writeFrame(currentFrame);
                    }
                }
                return IP_BACKTRACK;
            } else {
                for (int i = 0; i < successors.length; i++) {
                    PureNFATransition transition = successors[i];
                    CompilerAsserts.partialEvaluationConstant(transition);
                    if (transitionMatches(frame, locals, codeRange, transition, index, atEnd, c)) {
                        updateState(locals, transition, index);
                        locals.restoreIndex();
                        return transition.getTarget().getId();
                    }
                }
                return IP_BACKTRACK;
            }
        } else if (isTransitionMatchesStepByStep()) {
            boolean hasMatchingTransition = false;
            boolean transitionToFinalStateWins = false;
            // We make a copy of the current stack frame, as we will need it to undo speculative
            // changes done by tryUpdateState and to duplicate the current stack frame on demand.
            int[] currentFrame = locals.getStackFrameBuffer();
            locals.readFrame(currentFrame);
            for (int i = successors.length - 1; i >= 0; i--) {
                PureNFATransition transition = successors[i];
                CompilerAsserts.partialEvaluationConstant(transition);
                if (tryUpdateState(frame, locals, codeRange, transition, index, atEnd, c)) {
                    hasMatchingTransition = true;
                    PureNFAState target = transition.getTarget();
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
            // The stack always contains one leftover stack frame that is to be discarded
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
                    if (transitionMatches(frame, locals, codeRange, transition, index, atEnd, c)) {
                        bs |= bit;
                        lastMatch = i;
                        /*
                         * We can avoid pushing final states on the stack, since no more than one
                         * will ever be popped. Therefore, we have a dedicated slot in our "locals"
                         * object that represents the last pushed final state.
                         */
                        if (isAcceptableFinalState(transition.getTarget(), locals)) {
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
            if (injectBranchProbability(LIKELY_PROBABILITY, nMatched > 0)) {
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
                    PureNFAState target = transition.getTarget();
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
        return state.isFinalState() && !(isMustAdvance() && locals.getIndex() == locals.getFromIndex());
    }

    /**
     * Should the reported lastGroup point to the first group that *begins* instead of the last
     * group that *ends*? This is needed when executing Python lookbehind expressions. The semantics
     * of the lastGroup field should correspond to the left-to-right evaluation of lookbehind
     * assertions in Python, but we run lookbehinds in the right-to-left direction.
     */
    public boolean returnsFirstGroup() {
        return isFlagSet(FLAG_RETURNS_FIRST_GROUP);
    }

    private TRegexExecutorBaseNode getSubExecutor(PureNFAState subMatcherState) {
        return subExecutors[subMatcherState.getSubtreeId()];
    }

    protected boolean lookAroundExecutorIsLiteral(PureNFAState s) {
        return getSubExecutor(s).unwrap() instanceof TRegexLiteralLookAroundExecutorNode;
    }

    private boolean subExecutorReturnsFirstGroup(PureNFAState s) {
        TRegexExecutorNode executor = getSubExecutor(s).unwrap();
        if (executor instanceof TRegexBacktrackingNFAExecutorNode) {
            return ((TRegexBacktrackingNFAExecutorNode) executor).returnsFirstGroup();
        } else {
            return false;
        }
    }

    private boolean canInlineLookAroundIntoTransition(PureNFAState s) {
        return s.isLookAround() && lookAroundExecutorIsLiteral(s) && (s.isSubMatcherNegated() || !getSubExecutor(s).writesCaptureGroups());
    }

    private boolean checkSubMatcherInline(VirtualFrame frame, TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, PureNFATransition transition, PureNFAState target) {
        if (lookAroundExecutorIsLiteral(target)) {
            int saveIndex = locals.getIndex();
            int saveNextIndex = locals.getNextIndex();
            boolean result = (boolean) getSubExecutor(target).execute(frame, locals, codeRange);
            locals.setIndex(saveIndex);
            locals.setNextIndex(saveNextIndex);
            return result;
        } else {
            return !subMatchFailed(target, runSubMatcher(frame, locals.createSubNFALocals(transition, subExecutorReturnsFirstGroup(target)), codeRange, target));
        }
    }

    protected int[] runSubMatcher(VirtualFrame frame, TRegexBacktrackingNFAExecutorLocals subLocals, TruffleString.CodeRange codeRange, PureNFAState subMatcherState) {
        return (int[]) getSubExecutor(subMatcherState).execute(frame, subLocals, codeRange);
    }

    protected static boolean subMatchFailed(PureNFAState curState, Object subMatchResult) {
        return (subMatchResult == null) != curState.isSubMatcherNegated();
    }

    @ExplodeLoop
    protected boolean transitionMatches(VirtualFrame frame, TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, PureNFATransition transition, int index,
                    boolean atEnd, int c) {
        PureNFAState target = transition.getTarget();
        CompilerAsserts.partialEvaluationConstant(target);
        if (transition.hasCaretGuard() && index != locals.getRegionFrom()) {
            return false;
        }
        if (transition.hasDollarGuard() && index < locals.getRegionTo()) {
            return false;
        }
        if (isMatchBoundaryAssertions()) {
            if (locals.isMatchEndAssertionTraversed() && (target.isCharacterClass() || target.isBackReference())) {
                return false;
            }
            if (transition.hasMatchBeginGuard() && index != locals.getCaptureGroupStart(0)) {
                // we omit this guard on transitions containing a capture group update of the
                // beginning of group 0, so doing this check before capture group updates is fine
                return false;
            }
        }
        long[] guards = transition.getGuards();
        CompilerAsserts.partialEvaluationConstant(guards);
        for (int i = 0; i < guards.length; i++) {
            CompilerAsserts.partialEvaluationConstant(i);
            long guard = guards[i];
            CompilerAsserts.partialEvaluationConstant(guard);
            TransitionGuard.Kind kind = TransitionGuard.getKind(guard);
            CompilerAsserts.partialEvaluationConstant(kind);
            switch (kind) {
                case loop -> {
                    // retreat if quantifier count is at maximum
                    if (locals.getQuantifierCount(TransitionGuard.getQuantifierIndex(guard)) == getQuantifier(guard).getMax()) {
                        return false;
                    }
                }
                case exit -> {
                    // retreat if quantifier count is less than minimum
                    if (locals.getQuantifierCount(TransitionGuard.getQuantifierIndex(guard)) < getQuantifier(guard).getMin()) {
                        return false;
                    }
                }
                case exitZeroWidth -> {
                    Quantifier q = getZeroWidthQuantifier(guard);
                    CompilerAsserts.partialEvaluationConstant(q);
                    if (locals.getZeroWidthQuantifierGuardIndex(TransitionGuard.getZeroWidthQuantifierIndex(guard)) == index &&
                                    (!isMonitorCaptureGroupsInEmptyCheck() || locals.isResultUnmodifiedByZeroWidthQuantifier(TransitionGuard.getZeroWidthQuantifierIndex(guard))) &&
                                    // In JS, we allow this guard to pass if we are still in the
                                    // optional part of the quantifier. This allows JS to fast-
                                    // forward past all the empty mandatory iterations.
                                    (isEmptyChecksOnMandatoryLoopIterations() || !q.hasIndex() || locals.getQuantifierCount(q.getIndex()) > q.getMin())) {
                        return false;
                    }
                }
                case escapeZeroWidth -> {
                    if (locals.getZeroWidthQuantifierGuardIndex(TransitionGuard.getZeroWidthQuantifierIndex(guard)) != index ||
                                    (isMonitorCaptureGroupsInEmptyCheck() && !locals.isResultUnmodifiedByZeroWidthQuantifier(TransitionGuard.getZeroWidthQuantifierIndex(guard)))) {
                        return false;
                    }
                }
                case checkGroupMatched -> {
                    if (getBackRefBoundary(locals, transition, Group.groupNumberToBoundaryIndexStart(TransitionGuard.getGroupNumber(guard)), index) == -1 ||
                                    getBackRefBoundary(locals, transition, Group.groupNumberToBoundaryIndexEnd(TransitionGuard.getGroupNumber(guard)), index) == -1) {
                        return false;
                    }
                }
                case checkGroupNotMatched -> {
                    if (getBackRefBoundary(locals, transition, Group.groupNumberToBoundaryIndexStart(TransitionGuard.getGroupNumber(guard)), index) != -1 &&
                                    getBackRefBoundary(locals, transition, Group.groupNumberToBoundaryIndexEnd(TransitionGuard.getGroupNumber(guard)), index) != -1) {
                        return false;
                    }
                }
                default -> {
                }
            }
        }
        switch (target.getKind()) {
            case PureNFAState.KIND_INITIAL_OR_FINAL_STATE:
                assert !target.isInitialState();
                return target.isAnchoredFinalState() ? atEnd : true;
            case PureNFAState.KIND_CHARACTER_CLASS:
                return !atEnd && matchers[target.getId()].match(c);
            case PureNFAState.KIND_SUB_MATCHER:
                if (canInlineLookAroundIntoTransition(target)) {
                    return checkSubMatcherInline(frame, locals, codeRange, transition, target);
                } else {
                    return true;
                }
            case PureNFAState.KIND_BACK_REFERENCE:
                if (canInlineBackReferenceIntoTransition(target)) {
                    return matchBackReferenceSimple(locals, target, transition, index);
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
        assert !isRecursiveBackreferences();
        if (transition.hasMatchEndGuard()) {
            assert isMatchBoundaryAssertions();
            locals.setMatchEndAssertionTraversed();
        }
        locals.apply(transition, index);
        for (long guard : transition.getGuards()) {
            CompilerAsserts.partialEvaluationConstant(guard);
            switch (TransitionGuard.getKind(guard)) {
                case loop, loopInc -> {
                    locals.incQuantifierCount(TransitionGuard.getQuantifierIndex(guard));
                }
                case exit, exitReset -> {
                    locals.resetQuantifierCount(TransitionGuard.getQuantifierIndex(guard));
                }
                case enterZeroWidth -> {
                    locals.setZeroWidthQuantifierGuardIndex(TransitionGuard.getZeroWidthQuantifierIndex(guard));
                    locals.setZeroWidthQuantifierResults(TransitionGuard.getZeroWidthQuantifierIndex(guard));
                }
                case exitZeroWidth -> {
                    Quantifier q = getZeroWidthQuantifier(guard);
                    CompilerAsserts.partialEvaluationConstant(q);
                    boolean emptyCheckFailed = locals.getZeroWidthQuantifierGuardIndex(TransitionGuard.getZeroWidthQuantifierIndex(guard)) == index &&
                                    (!isMonitorCaptureGroupsInEmptyCheck() || locals.isResultUnmodifiedByZeroWidthQuantifier(TransitionGuard.getZeroWidthQuantifierIndex(guard)));
                    boolean advancePastOptionalIterations = !isEmptyChecksOnMandatoryLoopIterations() && q.hasIndex() && locals.getQuantifierCount(q.getIndex()) < q.getMin();
                    if (emptyCheckFailed && advancePastOptionalIterations && !transition.hasCaretGuard() && !transition.hasDollarGuard()) {
                        // We advance the counter to min - 1 to skip past all but one mandatory
                        // iteration. We do not skip the last mandatory iteration and set the
                        // counter to min, because of the way JavaScript regexes are executed. The
                        // JavaScript flavor does not set matchesTransitionStepByStep and therefore
                        // all guards are tested against the same original state. In the case of the
                        // last mandatory iteration, we would like it to be possible to match the
                        // exitZeroWidth guard followed by the exit guard, so that it is possible to
                        // hit the exact minimum number of iterations. However, this relies on first
                        // updating the state with exitZeroWidth and then testing this new state
                        // with the exit guard. This would mean having to enable
                        // matchesTransitionStepByStep for JavaScript and implementing this logic in
                        // tryUpdateState instead, which would lead to degraded performance for JS
                        // regexps. Instead, we choose to advance the counter to just before the
                        // last mandatory iteration so that this fast-forwarding behavior does not
                        // coincide with an exit guard that should pass.
                        locals.setQuantifierCount(q.getIndex(), q.getMin() - 1);
                    }
                }
                default -> {
                }
            }
        }
        locals.saveIndex(getNewIndex(locals, transition.getTarget(), index));
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
    protected boolean tryUpdateState(VirtualFrame frame, TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, PureNFATransition transition, int index,
                    boolean atEnd, int c) {
        CompilerAsserts.partialEvaluationConstant(transition);
        PureNFAState target = transition.getTarget();
        CompilerAsserts.partialEvaluationConstant(target);
        if (transition.hasCaretGuard() && index != locals.getRegionFrom()) {
            return false;
        }
        if (transition.hasDollarGuard() && index < locals.getRegionTo()) {
            return false;
        }
        if (isMatchBoundaryAssertions()) {
            if (locals.isMatchEndAssertionTraversed() && (target.isCharacterClass() || target.isBackReference())) {
                return false;
            }
            if (transition.hasMatchBeginGuard() && index != locals.getCaptureGroupStart(0)) {
                // we omit this guard on transitions containing a capture group update of the
                // beginning of group 0, so doing this check before capture group updates is fine
                return false;
            }
        }
        switch (target.getKind()) {
            case PureNFAState.KIND_INITIAL_OR_FINAL_STATE:
                assert !target.isInitialState();
                if (target.isAnchoredFinalState() && !atEnd) {
                    return false;
                }
                break;
            case PureNFAState.KIND_CHARACTER_CLASS:
                if (atEnd || !matchers[target.getId()].match(c)) {
                    return false;
                }
                break;
            case PureNFAState.KIND_SUB_MATCHER:
                if (canInlineLookAroundIntoTransition(target) && !checkSubMatcherInline(frame, locals, codeRange, transition, target)) {
                    return false;
                }
                break;
            case PureNFAState.KIND_BACK_REFERENCE:
                if (canInlineBackReferenceIntoTransition(target)) {
                    if (!matchBackReferenceSimple(locals, target, transition, index)) {
                        return false;
                    }
                }
                break;
            case PureNFAState.KIND_EMPTY_MATCH:
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        if (transition.hasMatchEndGuard()) {
            assert isMatchBoundaryAssertions();
            locals.setMatchEndAssertionTraversed();
        }
        for (long guard : transition.getGuards()) {
            switch (TransitionGuard.getKind(guard)) {
                case loopInc:
                    locals.incQuantifierCount(TransitionGuard.getQuantifierIndex(guard));
                    break;
                case loop:
                    // retreat if quantifier count is at maximum
                    if (locals.getQuantifierCount(TransitionGuard.getQuantifierIndex(guard)) == getQuantifier(guard).getMax()) {
                        return false;
                    }
                    locals.incQuantifierCount(TransitionGuard.getQuantifierIndex(guard));
                    break;
                case exit:
                    // retreat if quantifier count is less than minimum
                    if (locals.getQuantifierCount(TransitionGuard.getQuantifierIndex(guard)) < getQuantifier(guard).getMin()) {
                        return false;
                    }
                    locals.resetQuantifierCount(TransitionGuard.getQuantifierIndex(guard));
                    break;
                case exitReset:
                    locals.resetQuantifierCount(TransitionGuard.getQuantifierIndex(guard));
                    break;
                case updateCG:
                    locals.setCaptureGroupBoundary(TransitionGuard.getGroupBoundaryIndex(guard), index);
                    if (isTrackLastGroup() && TransitionGuard.getGroupBoundaryIndex(guard) % 2 != 0 && TransitionGuard.getGroupBoundaryIndex(guard) > 1) {
                        locals.setLastGroup(TransitionGuard.getGroupBoundaryIndex(guard) / 2);
                    }
                    break;
                case updateRecursiveBackrefPointer:
                    locals.saveRecursiveBackrefGroupStart(TransitionGuard.getGroupNumber(guard));
                    break;
                case enterZeroWidth:
                    locals.setZeroWidthQuantifierGuardIndex(TransitionGuard.getZeroWidthQuantifierIndex(guard));
                    locals.setZeroWidthQuantifierResults(TransitionGuard.getZeroWidthQuantifierIndex(guard));
                    break;
                case exitZeroWidth:
                    if (locals.getZeroWidthQuantifierGuardIndex(TransitionGuard.getZeroWidthQuantifierIndex(guard)) == index &&
                                    (!isMonitorCaptureGroupsInEmptyCheck() || locals.isResultUnmodifiedByZeroWidthQuantifier(TransitionGuard.getZeroWidthQuantifierIndex(guard)))) {
                        return false;
                    }
                    break;
                case escapeZeroWidth:
                    if (locals.getZeroWidthQuantifierGuardIndex(TransitionGuard.getZeroWidthQuantifierIndex(guard)) != index ||
                                    (isMonitorCaptureGroupsInEmptyCheck() && !locals.isResultUnmodifiedByZeroWidthQuantifier(TransitionGuard.getZeroWidthQuantifierIndex(guard)))) {
                        return false;
                    }
                    break;
                case checkGroupMatched:
                    if (locals.getCaptureGroupStart(TransitionGuard.getGroupNumber(guard)) == -1 || locals.getCaptureGroupEnd(TransitionGuard.getGroupNumber(guard)) == -1) {
                        return false;
                    }
                    break;
                case checkGroupNotMatched:
                    if (locals.getCaptureGroupStart(TransitionGuard.getGroupNumber(guard)) != -1 && locals.getCaptureGroupEnd(TransitionGuard.getGroupNumber(guard)) != -1) {
                        return false;
                    }
                    break;
                default:
                    break;
            }
        }
        locals.saveIndex(getNewIndex(locals, target, index));
        return true;
    }

    private Pair<Integer, Integer> getBackRefBounds(TRegexBacktrackingNFAExecutorLocals locals, PureNFAState backReference) {
        for (int backRefNumber : backReference.getBackRefNumbers()) {
            final int start;
            if (isRecursiveBackreferences() && backReference.isRecursiveReference()) {
                start = locals.getRecursiveCaptureGroupStart(backRefNumber);
            } else {
                start = locals.getCaptureGroupStart(backRefNumber);
            }
            int end = locals.getCaptureGroupEnd(backRefNumber);
            if (start >= 0 && end >= 0) {
                return Pair.create(start, end);
            }
        }
        return Pair.create(-1, -1);
    }

    private Pair<Integer, Integer> getBackRefBounds(TRegexBacktrackingNFAExecutorLocals locals, PureNFAState backReference, PureNFATransition transition, int index) {
        for (int backRefNumber : backReference.getBackRefNumbers()) {
            final int start;
            if (isRecursiveBackreferences() && backReference.isRecursiveReference()) {
                start = locals.getRecursiveCaptureGroupStart(backRefNumber);
            } else {
                start = getBackRefBoundary(locals, transition, Group.groupNumberToBoundaryIndexStart(backRefNumber), index);
            }
            int end = getBackRefBoundary(locals, transition, Group.groupNumberToBoundaryIndexEnd(backRefNumber), index);
            if (start >= 0 && end >= 0) {
                return Pair.create(start, end);
            }
        }
        return Pair.create(-1, -1);
    }

    private int getNewIndex(TRegexBacktrackingNFAExecutorLocals locals, PureNFAState target, int index) {
        CompilerAsserts.partialEvaluationConstant(target.getKind());
        switch (target.getKind()) {
            case PureNFAState.KIND_INITIAL_OR_FINAL_STATE, PureNFAState.KIND_SUB_MATCHER, PureNFAState.KIND_EMPTY_MATCH:
                return index;
            case PureNFAState.KIND_CHARACTER_CLASS:
                return locals.getNextIndex();
            case PureNFAState.KIND_BACK_REFERENCE:
                if (canInlineBackReferenceIntoTransition(target)) {
                    Pair<Integer, Integer> backRefBounds = getBackRefBounds(locals, target);
                    final int start = backRefBounds.getLeft();
                    final int end = backRefBounds.getRight();
                    if (start < 0 || end < 0) {
                        // only can happen when backrefWithNullTargetSucceeds == true
                        return index;
                    }
                    int length = end - start;
                    return isForward() ? index + length : index - length;
                } else {
                    return index;
                }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     * Returns {@code true} if we can inline the back-reference check with
     * {@link #matchBackReferenceSimple(TRegexBacktrackingNFAExecutorLocals, PureNFAState, PureNFATransition, int)}.
     * This is the case when we are not in ignore-case mode and the regex cannot match lone
     * surrogate characters. The reason for lone surrogates being a problem is cases like this:
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
    private boolean canInlineBackReferenceIntoTransition(PureNFAState backRef) {
        assert backRef.isBackReference();
        return !(backRef.isIgnoreCaseReference() || isLoneSurrogates() || isRecursiveBackreferences());
    }

    private boolean matchBackReferenceSimple(TRegexBacktrackingNFAExecutorLocals locals, PureNFAState backReference, PureNFATransition transition, int index) {
        assert backReference.isBackReference();
        assert canInlineBackReferenceIntoTransition(backReference);
        Pair<Integer, Integer> backRefBounds = getBackRefBounds(locals, backReference, transition, index);
        final int backrefStart = backRefBounds.getLeft();
        final int backrefEnd = backRefBounds.getRight();
        if (backrefStart < 0 || backrefEnd < 0) {
            return !isBackrefWithNullTargetFails();
        }
        int backrefLength = backrefEnd - backrefStart;
        if (backrefLength == 0) {
            return true;
        }
        if (isForward() ? index + backrefLength > locals.getRegionTo() : index - backrefLength < locals.getRegionFrom()) {
            return false;
        }
        int stride = getEncoding().getStride();
        return getRegionMatchesNode().execute(locals.getInput(), backrefStart << stride, locals.getInput(), (isForward() ? index : index - backrefLength) << stride, backrefLength << stride,
                        getEncoding().getTStringEncoding());
    }

    private int matchBackReferenceGeneric(TRegexBacktrackingNFAExecutorLocals locals, PureNFAState backReference, TruffleString.CodeRange codeRange) {
        assert backReference.isBackReference();
        assert !canInlineBackReferenceIntoTransition(backReference);
        Pair<Integer, Integer> backRefBounds = getBackRefBounds(locals, backReference);
        final int backrefStart = backRefBounds.getLeft();
        final int backrefEnd = backRefBounds.getRight();
        if (backrefStart < 0 || backrefEnd < 0) {
            return isBackrefWithNullTargetFails() ? -1 : locals.getIndex();
        }
        if (isBackreferenceIgnoreCaseMultiCharExpansion() && backReference.isIgnoreCaseReference()) {
            return matchBackreferenceGenericMultiCharExpansion(locals, codeRange, backrefStart, backrefEnd);
        } else {
            return matchBackreferenceGenericSingleChars(locals, backReference, codeRange, backrefStart, backrefEnd);
        }
    }

    private int matchBackreferenceGenericSingleChars(TRegexBacktrackingNFAExecutorLocals locals, PureNFAState backReference, TruffleString.CodeRange codeRange, int backrefStart, int backrefEnd) {
        int saveNextIndex = locals.getNextIndex();
        int iBR = isForward() ? backrefStart : backrefEnd;
        int i = locals.getIndex();
        while (injectBranchProbability(CONTINUE_PROBABILITY, inputBoundsCheck(iBR, backrefStart, backrefEnd))) {
            if (injectBranchProbability(EXIT_PROBABILITY, !inputBoundsCheck(i, locals.getRegionFrom(), locals.getRegionTo()))) {
                locals.setNextIndex(saveNextIndex);
                return -1;
            }
            int codePointBR = inputReadAndDecode(locals, iBR, codeRange);
            iBR = locals.getNextIndex();
            int codePointI = inputReadAndDecode(locals, i, codeRange);
            i = locals.getNextIndex();
            if (injectBranchProbability(EXIT_PROBABILITY,
                            !(backReference.isIgnoreCaseReference() ? equalsIgnoreCase(codePointBR, codePointI, backReference.isIgnoreCaseReferenceAlternativeMode()) : codePointBR == codePointI))) {
                locals.setNextIndex(saveNextIndex);
                return -1;
            }
        }
        locals.setNextIndex(saveNextIndex);
        return i;
    }

    @TruffleBoundary
    private int matchBackreferenceGenericMultiCharExpansion(TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, int backrefStart, int backrefEnd) {
        IntRingBuffer bufA = locals.getBackrefMultiCharExpansionBufferA();
        IntRingBuffer bufB = locals.getBackrefMultiCharExpansionBufferB();
        bufA.clear();
        bufB.clear();
        int saveNextIndex = locals.getNextIndex();
        int iBR = isForward() ? backrefStart : backrefEnd;
        int i = locals.getIndex();
        while (true) {
            if (bufA.isEmpty()) {
                if (!inputBoundsCheck(iBR, backrefStart, backrefEnd)) {
                    break;
                }
                int codePointBR = inputReadAndDecode(locals, iBR, codeRange);
                iBR = locals.getNextIndex();
                matchBackreferenceGenericMultiCharExpansionAddFolded(bufA, codePointBR);
            }
            if (bufB.isEmpty()) {
                if (!inputBoundsCheck(i, locals.getRegionFrom(), locals.getRegionTo())) {
                    break;
                }
                int codePointI = inputReadAndDecode(locals, i, codeRange);
                i = locals.getNextIndex();
                matchBackreferenceGenericMultiCharExpansionAddFolded(bufB, codePointI);
            }
            while (!bufA.isEmpty() && !bufB.isEmpty()) {
                if (bufA.removeFirst() != bufB.removeFirst()) {
                    locals.setNextIndex(saveNextIndex);
                    return -1;
                }
            }
        }
        locals.setNextIndex(saveNextIndex);
        if (bufA.isEmpty() && bufB.isEmpty()) {
            return i;
        } else {
            return -1;
        }
    }

    private void matchBackreferenceGenericMultiCharExpansionAddFolded(IntRingBuffer buf, int codePoint) {
        int[] folded = MultiCharacterCaseFolding.caseFold(multiCharacterExpansionCaseFoldAlgorithm, codePoint);
        if (folded == null) {
            buf.add(codePoint);
        } else {
            buf.addAll(folded);
        }
    }

    private TruffleString.RegionEqualByteIndexNode getRegionMatchesNode() {
        if (regionMatchesNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            regionMatchesNode = insert(TruffleString.RegionEqualByteIndexNode.create());
        }
        return regionMatchesNode;
    }

    private TruffleString.ByteIndexOfStringNode getIndexOfNode() {
        if (indexOfNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            indexOfNode = insert(TruffleString.ByteIndexOfStringNode.create());
        }
        return indexOfNode;
    }

    private int findInnerLiteral(TRegexBacktrackingNFAExecutorLocals locals) {
        return InputOps.indexOf(locals.getInput(), locals.getIndex(), locals.getMaxIndex(), innerLiteral, getEncoding(), getIndexOfNode());
    }

    private boolean inputBoundsCheck(int i, int min, int max) {
        return isForward() ? i < max : i > min;
    }

    @TruffleBoundary
    private boolean equalsIgnoreCase(int a, int b, boolean alternativeMode) {
        return equalsIgnoreCase.test(a, b, alternativeMode);
    }

    private static int setFlag(int flags, int flag, boolean value) {
        if (value) {
            return flags | flag;
        } else {
            return flags & ~flag;
        }
    }

    private static boolean isFlagSet(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
