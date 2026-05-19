/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

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
import com.oracle.truffle.regex.tregex.automaton.BasicState;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntRingBuffer;
import com.oracle.truffle.regex.tregex.buffer.LongArrayBuffer;
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
import com.oracle.truffle.regex.tregex.parser.RegexFlavor;
import com.oracle.truffle.regex.tregex.parser.Token.Quantifier;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.InnerLiteral;
import com.oracle.truffle.regex.tregex.parser.ast.QuantifiableTerm;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.util.EmptyArrays;

/**
 * This regex executor uses a backtracking algorithm on a compact NFA representation. It is used
 * for all expressions that cannot be matched with the DFA, such as expressions with backreferences.
 */
public final class TRegexBacktrackingNFAExecutorNode extends TRegexBacktrackerSubExecutorNode {

    private static final int FLAG_BACKREF_IGNORE_CASE_MULTI_CHARACTER_EXPANSION = 1 << 0;
    private static final int FLAG_BACKREF_WITH_NULL_TARGET_FAILS = 1 << 1;
    private static final int FLAG_EMPTY_CHECKS_ON_MANDATORY_LOOP_ITERATIONS = 1 << 2;
    private static final int FLAG_FORWARD = 1 << 3;
    private static final int FLAG_LONE_SURROGATES = 1 << 4;
    private static final int FLAG_LOOPBACK_INITIAL_STATE = 1 << 5;
    private static final int FLAG_MATCH_BOUNDARY_ASSERTIONS = 1 << 6;
    private static final int FLAG_MONITOR_CAPTURE_GROUPS_IN_EMPTY_CHECK = 1 << 7;
    private static final int FLAG_MUST_ADVANCE = 1 << 8;
    private static final int FLAG_RECURSIVE_BACK_REFERENCES = 1 << 9;
    private static final int FLAG_RETURNS_FIRST_GROUP = 1 << 10;
    private static final int FLAG_REWIND_FIXED_WIDTH_LOOK_BEHIND = 1 << 11;
    private static final int FLAG_TRACK_LAST_GROUP = 1 << 12;
    private static final int FLAG_TRANSITION_MATCHES_STEP_BY_STEP = 1 << 13;
    private static final int FLAG_USE_MERGE_EXPLODE = 1 << 14;
    private static final int FLAG_WRITES_CAPTURE_GROUPS = 1 << 15;

    private static final int NO_MATCHER = -1;

    private static final int STATE_HEADER_SIZE = 4;
    private static final int STATE_FIELD_FLAGS = 0;
    private static final int STATE_FIELD_KIND = 1;
    private static final int STATE_FIELD_TRANSITION_COUNT = 2;
    private static final int STATE_FIELD_DATA = 3;

    private static final int TRANSITION_RECORD_SIZE = 4;
    private static final int TRANSITION_FIELD_TARGET = 0;
    private static final int TRANSITION_FIELD_FLAGS = 1;
    private static final int TRANSITION_FIELD_GROUP_BOUNDARIES = 2;
    private static final int TRANSITION_FIELD_GUARDS = 3;

    private static final int QUANTIFIER_RECORD_SIZE = 2;
    private static final int QUANTIFIER_FIELD_MIN = 0;
    private static final int QUANTIFIER_FIELD_MAX = 1;

    private static final int ZERO_WIDTH_QUANTIFIER_RECORD_SIZE = 2;
    private static final int ZERO_WIDTH_QUANTIFIER_FIELD_MIN = 0;
    private static final int ZERO_WIDTH_QUANTIFIER_FIELD_INDEX = 1;

    private static final int GUARD_BOUNDARY_EFFECT_UNCHANGED = 0;
    private static final int GUARD_BOUNDARY_EFFECT_UPDATE = 1;
    private static final int GUARD_BOUNDARY_EFFECT_CLEAR = 2;
    private static final int BACKREF_START_BOUNDARY_EFFECT_SHIFT = 0;
    private static final int BACKREF_END_BOUNDARY_EFFECT_SHIFT = 2;
    private static final int GUARD_START_BOUNDARY_EFFECT_SHIFT = 40;
    private static final int GUARD_END_BOUNDARY_EFFECT_SHIFT = 42;
    private static final long GUARD_BOUNDARY_EFFECT_MASK = 0x3L;

    /**
     * Compact backtracking NFA graph data. The first {@link #getStateDirectoryLength()} entries form
     * the state directory. State directory values are offsets of state records in this array.
     *
     * <pre>
     * nfa = [ state directory | state records... ]
     *
     * state directory:
     *   dispatchIndex -> stateRecordOffset
     *
     * state record at stateRecordOffset:
     *   [ flags, kind, transitionCount, stateData,
     *     transition record 0,
     *     transition record 1,
     *     ... ]
     *
     * stateData by state kind:
     *   initial/final state, empty-match: unused
     *   character class: matcher record (reference into matchers)
     *   sub-matcher: sub-executor index
     *   back-reference: backreference group-number record (reference into backRefNumbers)
     *
     * transition record:
     *   [ targetStateRecord, flags, groupBoundaryRecord, guardRecord ]
     *
     * guard record in guards:
     *   [ guardCount, guard0, guard1, ..., optional back-reference boundary effects... ]
     *
     * quantifier record in quantifiers:
     *   [ min, max ]
     *
     * zero-width quantifier record in zeroWidthQuantifiers:
     *   [ min, index-or--1 ]
     *
     * backreference group-number record in backRefNumbers:
     *   [ groupNumberCount, groupNumber0, groupNumber1, ... ]
     * </pre>
     */
    @CompilationFinal(dimensions = 1) private final int[] nfa;
    @CompilationFinal(dimensions = 1) private final int[] matchers;
    @CompilationFinal(dimensions = 1) private final int[] groupBoundaries;
    @CompilationFinal(dimensions = 1) private final long[] guards;
    @CompilationFinal(dimensions = 1) private final int[] quantifiers;
    @CompilationFinal(dimensions = 1) private final int[] zeroWidthQuantifiers;
    @CompilationFinal(dimensions = 1) private final int[] backRefNumbers;
    private final int fixedWidth;
    private final int anchoredInitialStateRecord;
    private final int unAnchoredInitialStateRecord;
    private final int numberOfStates;
    private final int maxNTransitions;
    private final int flags;
    private final InnerLiteral innerLiteral;
    private final int[] zeroWidthTermEnclosedCGLow;
    private final int[] zeroWidthQuantifierCGOffsets;
    private final RegexFlavor.EqualsIgnoreCasePredicate equalsIgnoreCase;
    private final CaseFoldData.CaseFoldAlgorithm multiCharacterExpansionCaseFoldAlgorithm;

    @Child TruffleString.RegionEqualByteIndexNode regionMatchesNode;
    @Child TruffleString.ByteIndexOfStringNode indexOfNode;
    private final int loopbackInitialStateMatcherRef;

    public static TRegexBacktrackingNFAExecutorNode create(RegexAST ast, PureNFA nfa, int numberOfStates, int numberOfTransitions, TRegexExecutorBaseNode[] subExecutors, boolean mustAdvance,
                    CompilationBuffer compilationBuffer) {
        return Builder.create(ast, nfa, numberOfStates, numberOfTransitions, subExecutors, mustAdvance, compilationBuffer);
    }

    private TRegexBacktrackingNFAExecutorNode(RegexAST ast,
                    int numberOfTransitions,
                    TRegexExecutorBaseNode[] subExecutors,
                    int[] compactNFA,
                    int[] matchers,
                    int[] groupBoundaries,
                    long[] guards,
                    int[] quantifiers,
                    int[] zeroWidthQuantifiers,
                    int[] backRefNumbers,
                    int fixedWidth,
                    int anchoredInitialStateRecord,
                    int unAnchoredInitialStateRecord,
                    int numberOfStates,
                    int maxNTransitions,
                    int flags,
                    InnerLiteral innerLiteral,
                    int[] zeroWidthTermEnclosedCGLow,
                    int[] zeroWidthQuantifierCGOffsets,
                    RegexFlavor.EqualsIgnoreCasePredicate equalsIgnoreCase,
                    int loopbackInitialStateMatcherRef,
                    CaseFoldData.CaseFoldAlgorithm multiCharacterExpansionCaseFoldAlgorithm) {
        super(ast, numberOfTransitions, subExecutors);
        this.nfa = compactNFA;
        this.matchers = matchers;
        this.groupBoundaries = groupBoundaries;
        this.guards = guards;
        this.quantifiers = quantifiers;
        this.zeroWidthQuantifiers = zeroWidthQuantifiers;
        this.backRefNumbers = backRefNumbers;
        this.fixedWidth = fixedWidth;
        this.anchoredInitialStateRecord = anchoredInitialStateRecord;
        this.unAnchoredInitialStateRecord = unAnchoredInitialStateRecord;
        this.numberOfStates = numberOfStates;
        this.maxNTransitions = maxNTransitions;
        this.flags = flags;
        this.innerLiteral = innerLiteral;
        this.zeroWidthTermEnclosedCGLow = zeroWidthTermEnclosedCGLow;
        this.zeroWidthQuantifierCGOffsets = zeroWidthQuantifierCGOffsets;
        this.equalsIgnoreCase = equalsIgnoreCase;
        this.loopbackInitialStateMatcherRef = loopbackInitialStateMatcherRef;
        this.multiCharacterExpansionCaseFoldAlgorithm = multiCharacterExpansionCaseFoldAlgorithm;
    }

    private TRegexBacktrackingNFAExecutorNode(TRegexBacktrackingNFAExecutorNode copy) {
        super(copy);
        this.nfa = copy.nfa;
        this.matchers = copy.matchers;
        this.groupBoundaries = copy.groupBoundaries;
        this.guards = copy.guards;
        this.quantifiers = copy.quantifiers;
        this.zeroWidthQuantifiers = copy.zeroWidthQuantifiers;
        this.backRefNumbers = copy.backRefNumbers;
        this.fixedWidth = copy.fixedWidth;
        this.anchoredInitialStateRecord = copy.anchoredInitialStateRecord;
        this.unAnchoredInitialStateRecord = copy.unAnchoredInitialStateRecord;
        this.numberOfStates = copy.numberOfStates;
        this.maxNTransitions = copy.maxNTransitions;
        this.flags = copy.flags;
        this.innerLiteral = copy.innerLiteral;
        this.zeroWidthTermEnclosedCGLow = copy.zeroWidthTermEnclosedCGLow;
        this.zeroWidthQuantifierCGOffsets = copy.zeroWidthQuantifierCGOffsets;
        this.equalsIgnoreCase = copy.equalsIgnoreCase;
        this.loopbackInitialStateMatcherRef = copy.loopbackInitialStateMatcherRef;
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
        RegexFlavor flavor = ast.getFlavor();
        int flags = 0;
        flags = setFlag(flags, FLAG_WRITES_CAPTURE_GROUPS, subtree.hasCaptureGroups());
        flags = setFlag(flags, FLAG_REWIND_FIXED_WIDTH_LOOK_BEHIND, subtree.isLookBehindAssertion() && flavor.lookBehindsRunLeftToRight() && nfa.isFixedWidth());
        flags = setFlag(flags, FLAG_FORWARD, !subtree.isLookBehindAssertion() || isFlagSet(flags, FLAG_REWIND_FIXED_WIDTH_LOOK_BEHIND));
        flags = setFlag(flags, FLAG_BACKREF_WITH_NULL_TARGET_FAILS, flavor.backreferencesToUnmatchedGroupsFail());
        flags = setFlag(flags, FLAG_MONITOR_CAPTURE_GROUPS_IN_EMPTY_CHECK, flavor.emptyChecksMonitorCaptureGroups());
        flags = setFlag(flags, FLAG_TRANSITION_MATCHES_STEP_BY_STEP, flavor.matchesTransitionsStepByStep());
        flags = setFlag(flags, FLAG_EMPTY_CHECKS_ON_MANDATORY_LOOP_ITERATIONS, flavor.emptyChecksOnMandatoryLoopIterations());
        flags = setFlag(flags, FLAG_TRACK_LAST_GROUP, flavor.usesLastGroupResultField());
        flags = setFlag(flags, FLAG_RETURNS_FIRST_GROUP, !isFlagSet(flags, FLAG_FORWARD) && flavor.lookBehindsRunLeftToRight());
        flags = setFlag(flags, FLAG_MUST_ADVANCE, mustAdvance);
        flags = setFlag(flags, FLAG_LONE_SURROGATES, ast.getProperties().hasLoneSurrogates());
        flags = setFlag(flags, FLAG_LOOPBACK_INITIAL_STATE, nfa.isRoot() && !ast.getFlags().isSticky() && !ast.getRoot().startsWithCaret());
        flags = setFlag(flags, FLAG_USE_MERGE_EXPLODE, nStates <= ast.getOptions().getMaxBackTrackerCompileSize() && nTransitions <= ast.getOptions().getMaxBackTrackerCompileSize());
        flags = setFlag(flags, FLAG_RECURSIVE_BACK_REFERENCES, ast.getProperties().hasRecursiveBackReferences());
        flags = setFlag(flags, FLAG_BACKREF_IGNORE_CASE_MULTI_CHARACTER_EXPANSION, flavor.backreferenceIgnoreCaseMultiCharExpansion() && ast.getProperties().hasBackReferences());
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

    public boolean isRewindFixedWidthLookBehind() {
        return isFlagSet(FLAG_REWIND_FIXED_WIDTH_LOOK_BEHIND);
    }

    private boolean isFlagSet(int flag) {
        return isFlagSet(flags, flag);
    }

    private static int getQuantifierRecord(int quantifierIndex) {
        return quantifierIndex * QUANTIFIER_RECORD_SIZE;
    }

    private static int getZeroWidthQuantifierRecord(int zeroWidthQuantifierIndex) {
        return zeroWidthQuantifierIndex * ZERO_WIDTH_QUANTIFIER_RECORD_SIZE;
    }

    private int getQuantifierMin(long guard) {
        CompilerAsserts.partialEvaluationConstant(guard);
        int quantifierIndex = TransitionGuard.getQuantifierIndex(guard);
        CompilerAsserts.partialEvaluationConstant(quantifierIndex);
        return quantifiers[getQuantifierRecord(quantifierIndex) + QUANTIFIER_FIELD_MIN];
    }

    private int getQuantifierMax(long guard) {
        CompilerAsserts.partialEvaluationConstant(guard);
        int quantifierIndex = TransitionGuard.getQuantifierIndex(guard);
        CompilerAsserts.partialEvaluationConstant(quantifierIndex);
        return quantifiers[getQuantifierRecord(quantifierIndex) + QUANTIFIER_FIELD_MAX];
    }

    private int getZeroWidthQuantifierMin(int zeroWidthQuantifierIndex) {
        CompilerAsserts.partialEvaluationConstant(zeroWidthQuantifiers);
        CompilerAsserts.partialEvaluationConstant(zeroWidthQuantifierIndex);
        return zeroWidthQuantifiers[getZeroWidthQuantifierRecord(zeroWidthQuantifierIndex) + ZERO_WIDTH_QUANTIFIER_FIELD_MIN];
    }

    private int getQuantifierIndexOfZeroWidthQuantifier(int zeroWidthQuantifierIndex) {
        CompilerAsserts.partialEvaluationConstant(zeroWidthQuantifiers);
        CompilerAsserts.partialEvaluationConstant(zeroWidthQuantifierIndex);
        return zeroWidthQuantifiers[getZeroWidthQuantifierRecord(zeroWidthQuantifierIndex) + ZERO_WIDTH_QUANTIFIER_FIELD_INDEX];
    }

    @Override
    public TRegexExecutorLocals createLocals(TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index) {
        return TRegexBacktrackingNFAExecutorLocals.create(input, fromIndex, maxIndex, regionFrom, regionTo, index, getNumberOfCaptureGroups(),
                        quantifiers.length / QUANTIFIER_RECORD_SIZE, zeroWidthQuantifiers.length / ZERO_WIDTH_QUANTIFIER_RECORD_SIZE, zeroWidthTermEnclosedCGLow, zeroWidthQuantifierCGOffsets,
                        isTransitionMatchesStepByStep(), maxNTransitions, isTrackLastGroup(), returnsFirstGroup(), isRecursiveBackreferences(), isBackreferenceIgnoreCaseMultiCharExpansion(),
                        isMatchBoundaryAssertions());
    }

    private static final int IP_BEGIN = -1;
    private static final int IP_BACKTRACK = -2;
    private static final int IP_END = -3;

    @Override
    public Object execute(VirtualFrame frame, TRegexExecutorLocals abstractLocals, TruffleString.CodeRange codeRange) {
        TRegexBacktrackingNFAExecutorLocals locals = (TRegexBacktrackingNFAExecutorLocals) abstractLocals;
        if (isRewindFixedWidthLookBehind()) {
            assert isForward();
            if (rewindUpTo(locals, 0, fixedWidth, codeRange) != fixedWidth) {
                return null;
            }
        }
        if (innerLiteral != null) {
            locals.setIndex(locals.getFromIndex());
            CompilerAsserts.partialEvaluationConstant(this);
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
                if (anchoredInitialStateRecord != unAnchoredInitialStateRecord && inputAtBegin(locals)) {
                    ip = anchoredInitialStateRecord;
                    continue;
                } else {
                    ip = unAnchoredInitialStateRecord;
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
                        } else if (loopbackInitialStateMatcherRef != NO_MATCHER) {
                            // find the next character that matches any of the initial state's
                            // successors.
                            assert isForward();
                            while (injectBranchProbability(CONTINUE_PROBABILITY, inputHasNext(locals))) {
                                if (injectBranchProbability(EXIT_PROBABILITY, CharMatchers.match(matchers, loopbackInitialStateMatcherRef, inputReadAndDecode(locals, codeRange)))) {
                                    break;
                                }
                                inputAdvance(locals);
                            }
                        }
                        locals.setLastInitialStateIndex(locals.getIndex());
                        locals.resetToInitialState();
                        ip = unAnchoredInitialStateRecord;
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
                    int stateDirectoryLength = getStateDirectoryLength();
                    CompilerAsserts.partialEvaluationConstant(stateDirectoryLength);
                    for (int stateIndex = 0; stateIndex < stateDirectoryLength; stateIndex++) {
                        CompilerAsserts.partialEvaluationConstant(stateIndex);
                        int stateRecord = getStateRecord(stateIndex);
                        CompilerAsserts.partialEvaluationConstant(stateRecord);
                        if (stateRecord == nextIp) {
                            ip = stateRecord;
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
            final int stateRecord = ip;
            CompilerAsserts.partialEvaluationConstant(stateRecord);
            final int transitionCount = getStateTransitionCount(stateRecord);
            CompilerAsserts.partialEvaluationConstant(transitionCount);
            final int nextIp = runState(frame, locals, codeRange, stateRecord);
            for (int i = 0; i < transitionCount; i++) {
                int targetStateRecord = getTransitionTargetStateRecord(getTransitionRecord(stateRecord, i));
                if (targetStateRecord == nextIp) {
                    CompilerAsserts.partialEvaluationConstant(targetStateRecord);
                    ip = targetStateRecord;
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
    private int runState(VirtualFrame frame, TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, int stateRecord) {
        CompilerAsserts.partialEvaluationConstant(stateRecord);
        if (injectBranchProbability(EXIT_PROBABILITY, isAcceptableFinalState(stateRecord, locals))) {
            locals.setResult();
            locals.pushResult();
            return IP_END;
        }
        /*
         * Do very expensive operations per-state instead of per-transition, to avoid code size
         * explosion. Drawback: these postponed operations cannot be checked eagerly, so their state
         * will always be pushed to the stack.
         */
        if (isSubMatcher(stateRecord) && !canInlineLookAroundIntoTransition(stateRecord)) {
            TRegexBacktrackingNFAExecutorLocals subLocals = locals.createSubNFALocals(subExecutorReturnsFirstGroup(stateRecord));
            int[] subMatchResult = runSubMatcher(frame, subLocals, codeRange, stateRecord);
            if (injectBranchProbability(EXIT_PROBABILITY, subMatchFailed(stateRecord, subMatchResult))) {
                return IP_BACKTRACK;
            } else {
                if (!isSubMatcherNegated(stateRecord) && getSubExecutor(stateRecord).writesCaptureGroups()) {
                    locals.overwriteCaptureGroups(subMatchResult);
                }
                if (!isLookAround(stateRecord)) {
                    locals.saveIndex(subLocals.getIndex());
                    locals.restoreIndex();
                }
            }
        }
        if (isBackReference(stateRecord) && !canInlineBackReferenceIntoTransition(stateRecord)) {
            int backrefResult = matchBackReferenceGeneric(locals, stateRecord, codeRange);
            if (injectBranchProbability(EXIT_PROBABILITY, backrefResult < 0)) {
                return IP_BACKTRACK;
            } else {
                locals.setIndex(backrefResult);
            }
        }
        int transitionCount = getStateTransitionCount(stateRecord);
        CompilerAsserts.partialEvaluationConstant(transitionCount);
        boolean atEnd = inputAtEnd(locals);
        int c = atEnd ? 0 : inputReadAndDecode(locals, codeRange);
        final int index = locals.getIndex();
        if (isDeterministic(stateRecord)) {
            /*
             * We know that in this state only one transition can match at a time, so we can always
             * break after the first match.
             */
            if (isTransitionMatchesStepByStep()) {
                // Use the tryUpdateState method instead of transitionMatches and updateState
                int[] currentFrame = locals.getStackFrameBuffer();
                locals.readFrame(currentFrame);
                for (int i = 0; i < transitionCount; i++) {
                    int transitionRecord = getTransitionRecord(stateRecord, i);
                    CompilerAsserts.partialEvaluationConstant(transitionRecord);
                    int targetStateRecord = getTransitionTargetStateRecord(transitionRecord);
                    if (tryUpdateState(frame, locals, codeRange, transitionRecord, targetStateRecord, index, atEnd, c)) {
                        locals.restoreIndex();
                        return targetStateRecord;
                    } else {
                        locals.writeFrame(currentFrame);
                    }
                }
                return IP_BACKTRACK;
            } else {
                for (int i = 0; i < transitionCount; i++) {
                    int transitionRecord = getTransitionRecord(stateRecord, i);
                    CompilerAsserts.partialEvaluationConstant(transitionRecord);
                    int targetStateRecord = getTransitionTargetStateRecord(transitionRecord);
                    if (transitionMatches(frame, locals, codeRange, transitionRecord, targetStateRecord, index, atEnd, c)) {
                        updateState(locals, transitionRecord, targetStateRecord, index);
                        locals.restoreIndex();
                        return targetStateRecord;
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
            for (int i = transitionCount - 1; i >= 0; i--) {
                int transitionRecord = getTransitionRecord(stateRecord, i);
                CompilerAsserts.partialEvaluationConstant(transitionRecord);
                int targetStateRecord = getTransitionTargetStateRecord(transitionRecord);
                if (tryUpdateState(frame, locals, codeRange, transitionRecord, targetStateRecord, index, atEnd, c)) {
                    hasMatchingTransition = true;
                    CompilerAsserts.partialEvaluationConstant(targetStateRecord);
                    if (isAcceptableFinalState(targetStateRecord, locals)) {
                        locals.setResult();
                        locals.pushResult();
                        transitionToFinalStateWins = true;
                        // Prepare a fresh frame by resetting the current one, as the current
                        // frame is no longer needed.
                        locals.writeFrame(currentFrame);
                    } else {
                        locals.setPc(targetStateRecord);
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
                // We erase the leftover frame from the stack and set the index and state to the
                // values stored in the top frame (index is set to locals, the state is returned).
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
            final int bitSetWords = ((transitionCount - 1) >> 6) + 1;
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
                final int iStart = transitionCount - (iBS << 6) - 1;
                final int iEnd = Math.max(-1, iStart - (1 << 6));
                CompilerAsserts.partialEvaluationConstant(iStart);
                CompilerAsserts.partialEvaluationConstant(iEnd);
                for (int i = iStart; i > iEnd; i--) {
                    int transitionRecord = getTransitionRecord(stateRecord, i);
                    CompilerAsserts.partialEvaluationConstant(transitionRecord);
                    int targetStateRecord = getTransitionTargetStateRecord(transitionRecord);
                    if (transitionMatches(frame, locals, codeRange, transitionRecord, targetStateRecord, index, atEnd, c)) {
                        bs |= bit;
                        lastMatch = i;
                        /*
                         * We can avoid pushing final states on the stack, since no more than one
                         * will ever be popped. Therefore, we have a dedicated slot in our "locals"
                         * object that represents the last pushed final state.
                         */
                        if (isAcceptableFinalState(targetStateRecord, locals)) {
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
                final int iStart = transitionCount - (iBS << 6) - 1;
                final int iEnd = Math.max(-1, iStart - (1 << 6));
                CompilerAsserts.partialEvaluationConstant(iStart);
                CompilerAsserts.partialEvaluationConstant(iEnd);
                for (int i = iStart; i > iEnd; i--) {
                    int transitionRecord = getTransitionRecord(stateRecord, i);
                    CompilerAsserts.partialEvaluationConstant(transitionRecord);
                    int targetStateRecord = getTransitionTargetStateRecord(transitionRecord);
                    CompilerAsserts.partialEvaluationConstant(targetStateRecord);
                    if ((bs & 1) != 0) {
                        if (isAcceptableFinalState(targetStateRecord, locals)) {
                            if (i == lastFinal) {
                                locals.pushResult(groupBoundaries, getTransitionGroupBoundaries(transitionRecord), index);
                            }
                            if (i == lastMatch) {
                                return IP_END;
                            }
                        } else {
                            updateState(locals, transitionRecord, targetStateRecord, index);
                            if (i == lastMatch) {
                                locals.restoreIndex();
                                return targetStateRecord;
                            } else {
                                locals.setPc(targetStateRecord);
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

    private boolean isAcceptableFinalState(int stateRecord, TRegexBacktrackingNFAExecutorLocals locals) {
        return isFinalState(stateRecord) && !(isMustAdvance() && locals.getIndex() == locals.getFromIndex());
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

    private TRegexExecutorBaseNode getSubExecutor(int stateRecord) {
        assert isSubMatcher(stateRecord);
        return subExecutors[getStateData(stateRecord)];
    }

    private boolean lookAroundExecutorIsLiteral(int stateRecord) {
        return getSubExecutor(stateRecord).unwrap() instanceof TRegexLiteralLookAroundExecutorNode;
    }

    private boolean subExecutorReturnsFirstGroup(int stateRecord) {
        assert isSubMatcher(stateRecord);
        TRegexExecutorNode executor = getSubExecutor(stateRecord).unwrap();
        if (executor instanceof TRegexBacktrackingNFAExecutorNode) {
            return ((TRegexBacktrackingNFAExecutorNode) executor).returnsFirstGroup();
        } else {
            return false;
        }
    }

    private boolean canInlineLookAroundIntoTransition(int stateRecord) {
        return isLookAround(stateRecord) && lookAroundExecutorIsLiteral(stateRecord) && (isSubMatcherNegated(stateRecord) || !getSubExecutor(stateRecord).writesCaptureGroups());
    }

    private boolean checkSubMatcherInline(VirtualFrame frame, TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, int transitionRecord, int targetStateRecord) {
        if (lookAroundExecutorIsLiteral(targetStateRecord)) {
            int saveIndex = locals.getIndex();
            int saveNextIndex = locals.getNextIndex();
            boolean result = (boolean) getSubExecutor(targetStateRecord).execute(frame, locals, codeRange);
            locals.setIndex(saveIndex);
            locals.setNextIndex(saveNextIndex);
            return result;
        } else {
            return !subMatchFailed(targetStateRecord, runSubMatcher(frame, locals.createSubNFALocals(groupBoundaries, getTransitionGroupBoundaries(transitionRecord), subExecutorReturnsFirstGroup(
                            targetStateRecord)), codeRange, targetStateRecord));
        }
    }

    private int[] runSubMatcher(VirtualFrame frame, TRegexBacktrackingNFAExecutorLocals subLocals, TruffleString.CodeRange codeRange, int stateRecord) {
        return (int[]) getSubExecutor(stateRecord).execute(frame, subLocals, codeRange);
    }

    private boolean subMatchFailed(int stateRecord, Object subMatchResult) {
        return (subMatchResult == null) != isSubMatcherNegated(stateRecord);
    }

    @ExplodeLoop
    private boolean transitionMatches(VirtualFrame frame, TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, int transitionRecord, int targetStateRecord, int index,
                    boolean atEnd, int c) {
        CompilerAsserts.partialEvaluationConstant(targetStateRecord);
        if (hasCaretGuard(transitionRecord) && index != locals.getRegionFrom()) {
            return false;
        }
        if (hasDollarGuard(transitionRecord) && index < locals.getRegionTo()) {
            return false;
        }
        if (isMatchBoundaryAssertions()) {
            if (locals.isMatchEndAssertionTraversed() && (isCharacterClass(targetStateRecord) || isBackReference(targetStateRecord))) {
                return false;
            }
            if (hasMatchBeginGuard(transitionRecord) && index != locals.getCaptureGroupStart(0)) {
                // we omit this guard on transitions containing a capture group update of the
                // beginning of group 0, so doing this check before capture group updates is fine
                return false;
            }
        }
        int guardRecord = getTransitionGuards(transitionRecord);
        int guardCount = getGuardCount(guardRecord);
        CompilerAsserts.partialEvaluationConstant(guardCount);
        for (int i = 0; i < guardCount; i++) {
            CompilerAsserts.partialEvaluationConstant(i);
            long guard = guardAt(guardRecord, i);
            CompilerAsserts.partialEvaluationConstant(guard);
            TransitionGuard.Kind kind = TransitionGuard.getKind(guard);
            CompilerAsserts.partialEvaluationConstant(kind);
            switch (kind) {
                case countLtMin -> {
                    // retreat if quantifier count is greater or equal to minimum
                    if (locals.getQuantifierCount(TransitionGuard.getQuantifierIndex(guard)) >= getQuantifierMin(guard)) {
                        return false;
                    }
                }
                case countGeMin -> {
                    // retreat if quantifier count is less than minimum
                    if (locals.getQuantifierCount(TransitionGuard.getQuantifierIndex(guard)) < getQuantifierMin(guard)) {
                        return false;
                    }
                }
                case countLtMax -> {
                    // retreat if quantifier count is at maximum
                    if (locals.getQuantifierCount(TransitionGuard.getQuantifierIndex(guard)) >= getQuantifierMax(guard)) {
                        return false;
                    }
                }
                case exitZeroWidth -> {
                    int zeroWidthIndex = TransitionGuard.getZeroWidthQuantifierIndex(guard);
                    int quantifierIndex = getQuantifierIndexOfZeroWidthQuantifier(zeroWidthIndex);
                    if (locals.getZeroWidthQuantifierGuardIndex(zeroWidthIndex) == index &&
                                    (!isMonitorCaptureGroupsInEmptyCheck() || locals.isResultUnmodifiedByZeroWidthQuantifier(zeroWidthIndex)) &&
                                    (isEmptyChecksOnMandatoryLoopIterations() || quantifierIndex < 0 || locals.getQuantifierCount(quantifierIndex) > getZeroWidthQuantifierMin(zeroWidthIndex))) {
                        return false;
                    }
                }
                case escapeZeroWidth -> {
                    int zeroWidthIndex = TransitionGuard.getZeroWidthQuantifierIndex(guard);
                    if (locals.getZeroWidthQuantifierGuardIndex(zeroWidthIndex) != index ||
                                    (isMonitorCaptureGroupsInEmptyCheck() && !locals.isResultUnmodifiedByZeroWidthQuantifier(zeroWidthIndex))) {
                        return false;
                    }
                }
                case checkGroupMatched, checkGroupNotMatched -> {
                    int groupNumber = TransitionGuard.getGroupNumber(guard);
                    int start = getBackRefBoundary(locals, guard, GUARD_START_BOUNDARY_EFFECT_SHIFT, Group.groupNumberToBoundaryIndexStart(groupNumber), index);
                    int end = getBackRefBoundary(locals, guard, GUARD_END_BOUNDARY_EFFECT_SHIFT, Group.groupNumberToBoundaryIndexEnd(groupNumber), index);
                    boolean matched = start != -1 && end != -1;
                    if (kind == TransitionGuard.Kind.checkGroupMatched ? !matched : matched) {
                        return false;
                    }
                }
                default -> {
                }
            }
        }
        switch (getStateKind(targetStateRecord)) {
            case PureNFAState.KIND_INITIAL_OR_FINAL_STATE:
                return isAnchoredFinalState(targetStateRecord) ? atEnd : true;
            case PureNFAState.KIND_CHARACTER_CLASS:
                return !atEnd && CharMatchers.matchPE(matchers, getStateData(targetStateRecord), c);
            case PureNFAState.KIND_SUB_MATCHER:
                if (canInlineLookAroundIntoTransition(targetStateRecord)) {
                    return checkSubMatcherInline(frame, locals, codeRange, transitionRecord, targetStateRecord);
                } else {
                    return true;
                }
            case PureNFAState.KIND_BACK_REFERENCE:
                if (canInlineBackReferenceIntoTransition(targetStateRecord)) {
                    return matchBackReferenceSimple(locals, targetStateRecord, transitionRecord, index);
                } else {
                    return true;
                }
            case PureNFAState.KIND_EMPTY_MATCH:
                return true;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static int getBackRefBoundary(TRegexBacktrackingNFAExecutorLocals locals, long boundaryEffects, int boundaryEffectShift, int cgIndex, int index) {
        return switch (getBoundaryEffect(boundaryEffects, boundaryEffectShift)) {
            case GUARD_BOUNDARY_EFFECT_UPDATE -> index;
            case GUARD_BOUNDARY_EFFECT_CLEAR -> -1;
            default -> locals.getCaptureGroupBoundary(cgIndex);
        };
    }

    private static int getBoundaryEffect(long boundaryEffects, int shift) {
        return (int) ((boundaryEffects >>> shift) & GUARD_BOUNDARY_EFFECT_MASK);
    }

    @ExplodeLoop
    private void updateState(TRegexBacktrackingNFAExecutorLocals locals, int transitionRecord, int targetStateRecord, int index) {
        CompilerAsserts.partialEvaluationConstant(transitionRecord);
        assert !isRecursiveBackreferences();
        if (hasMatchEndGuard(transitionRecord)) {
            assert isMatchBoundaryAssertions();
            locals.setMatchEndAssertionTraversed();
        }
        locals.apply(groupBoundaries, getTransitionGroupBoundaries(transitionRecord), index);
        int guardRecord = getTransitionGuards(transitionRecord);
        int guardCount = getGuardCount(guardRecord);
        CompilerAsserts.partialEvaluationConstant(guardCount);
        for (int i = 0; i < guardCount; i++) {
            CompilerAsserts.partialEvaluationConstant(i);
            long guard = guardAt(guardRecord, i);
            CompilerAsserts.partialEvaluationConstant(guard);
            switch (TransitionGuard.getKind(guard)) {
                case countInc -> {
                    locals.incQuantifierCount(TransitionGuard.getQuantifierIndex(guard));
                }
                case countSet1 -> {
                    locals.setQuantifierCount(TransitionGuard.getQuantifierIndex(guard), 1);
                }
                case countSetMinInc -> {
                    locals.setQuantifierCount(TransitionGuard.getQuantifierIndex(guard), getQuantifierMin(guard) + 1);
                }
                case enterZeroWidth -> {
                    locals.setZeroWidthQuantifierGuardIndex(TransitionGuard.getZeroWidthQuantifierIndex(guard));
                    locals.setZeroWidthQuantifierResults(TransitionGuard.getZeroWidthQuantifierIndex(guard));
                }
                default -> {
                }
            }
        }
        locals.saveIndex(getNewIndex(locals, targetStateRecord, index));
    }

    /**
     * This method composes {@link #transitionMatches} with {@link #updateState}. It is somewhat
     * equivalent to the following:
     *
     * <pre>
     * if (transitionMatches(frame, locals, codeRange, transitionRecord, targetStateRecord, index, atEnd, c)) {
     *     updateState(locals, transitionRecord, targetStateRecord, index);
     *     return true;
     * } else {
     *     return false;
     * }
     * </pre>
     * <p>
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
    private boolean tryUpdateState(VirtualFrame frame, TRegexBacktrackingNFAExecutorLocals locals, TruffleString.CodeRange codeRange, int transitionRecord, int targetStateRecord, int index,
                    boolean atEnd, int c) {
        CompilerAsserts.partialEvaluationConstant(transitionRecord);
        CompilerAsserts.partialEvaluationConstant(targetStateRecord);
        if (hasCaretGuard(transitionRecord) && index != locals.getRegionFrom()) {
            return false;
        }
        if (hasDollarGuard(transitionRecord) && index < locals.getRegionTo()) {
            return false;
        }
        if (isMatchBoundaryAssertions()) {
            if (locals.isMatchEndAssertionTraversed() && (isCharacterClass(targetStateRecord) || isBackReference(targetStateRecord))) {
                return false;
            }
            if (hasMatchBeginGuard(transitionRecord) && index != locals.getCaptureGroupStart(0)) {
                // we omit this guard on transitions containing a capture group update of the
                // beginning of group 0, so doing this check before capture group updates is fine
                return false;
            }
        }
        switch (getStateKind(targetStateRecord)) {
            case PureNFAState.KIND_INITIAL_OR_FINAL_STATE:
                if (isAnchoredFinalState(targetStateRecord) && !atEnd) {
                    return false;
                }
                break;
            case PureNFAState.KIND_CHARACTER_CLASS:
                if (atEnd || !CharMatchers.matchPE(matchers, getStateData(targetStateRecord), c)) {
                    return false;
                }
                break;
            case PureNFAState.KIND_SUB_MATCHER:
                if (canInlineLookAroundIntoTransition(targetStateRecord) && !checkSubMatcherInline(frame, locals, codeRange, transitionRecord, targetStateRecord)) {
                    return false;
                }
                break;
            case PureNFAState.KIND_BACK_REFERENCE:
                if (canInlineBackReferenceIntoTransition(targetStateRecord)) {
                    if (!matchBackReferenceSimple(locals, targetStateRecord, transitionRecord, index)) {
                        return false;
                    }
                }
                break;
            case PureNFAState.KIND_EMPTY_MATCH:
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        if (hasMatchEndGuard(transitionRecord)) {
            assert isMatchBoundaryAssertions();
            locals.setMatchEndAssertionTraversed();
        }
        int guardRecord = getTransitionGuards(transitionRecord);
        int guardCount = getGuardCount(guardRecord);
        CompilerAsserts.partialEvaluationConstant(guardCount);
        for (int i = 0; i < guardCount; i++) {
            CompilerAsserts.partialEvaluationConstant(i);
            long guard = guardAt(guardRecord, i);
            TransitionGuard.Kind kind = TransitionGuard.getKind(guard);
            switch (kind) {
                case countInc -> {
                    locals.incQuantifierCount(TransitionGuard.getQuantifierIndex(guard));
                }
                case countSet1 -> {
                    locals.setQuantifierCount(TransitionGuard.getQuantifierIndex(guard), 1);
                }
                case countSetMinInc -> {
                    locals.setQuantifierCount(TransitionGuard.getQuantifierIndex(guard), getQuantifierMin(guard) + 1);
                }
                case countLtMin -> {
                    // retreat if quantifier count is greater or equal to minimum
                    if (locals.getQuantifierCount(TransitionGuard.getQuantifierIndex(guard)) >= getQuantifierMin(guard)) {
                        return false;
                    }
                }
                case countGeMin -> {
                    // retreat if quantifier count is less than minimum
                    if (locals.getQuantifierCount(TransitionGuard.getQuantifierIndex(guard)) < getQuantifierMin(guard)) {
                        return false;
                    }
                }
                case countLtMax -> {
                    // retreat if quantifier count is at maximum
                    if (locals.getQuantifierCount(TransitionGuard.getQuantifierIndex(guard)) >= getQuantifierMax(guard)) {
                        return false;
                    }
                }
                case updateCG -> {
                    locals.setCaptureGroupBoundary(TransitionGuard.getGroupBoundaryIndex(guard), index);
                    if (isTrackLastGroup() && TransitionGuard.getGroupBoundaryIndex(guard) % 2 != 0 && TransitionGuard.getGroupBoundaryIndex(guard) > 1) {
                        locals.setLastGroup(TransitionGuard.getGroupBoundaryIndex(guard) / 2);
                    }
                }
                case updateRecursiveBackrefPointer -> {
                    locals.saveRecursiveBackrefGroupStart(TransitionGuard.getGroupNumber(guard));
                }
                case enterZeroWidth -> {
                    locals.setZeroWidthQuantifierGuardIndex(TransitionGuard.getZeroWidthQuantifierIndex(guard));
                    locals.setZeroWidthQuantifierResults(TransitionGuard.getZeroWidthQuantifierIndex(guard));
                }
                case exitZeroWidth -> {
                    int zeroWidthIndex = TransitionGuard.getZeroWidthQuantifierIndex(guard);
                    int quantifierIndex = getQuantifierIndexOfZeroWidthQuantifier(zeroWidthIndex);
                    if (locals.getZeroWidthQuantifierGuardIndex(zeroWidthIndex) == index &&
                                    (!isMonitorCaptureGroupsInEmptyCheck() || locals.isResultUnmodifiedByZeroWidthQuantifier(zeroWidthIndex)) &&
                                    (isEmptyChecksOnMandatoryLoopIterations() || quantifierIndex < 0 || locals.getQuantifierCount(quantifierIndex) > getZeroWidthQuantifierMin(zeroWidthIndex))) {
                        return false;
                    }
                }
                case escapeZeroWidth -> {
                    int zeroWidthIndex = TransitionGuard.getZeroWidthQuantifierIndex(guard);
                    if (locals.getZeroWidthQuantifierGuardIndex(zeroWidthIndex) != index ||
                                    (isMonitorCaptureGroupsInEmptyCheck() && !locals.isResultUnmodifiedByZeroWidthQuantifier(zeroWidthIndex))) {
                        return false;
                    }
                }
                case checkGroupMatched, checkGroupNotMatched -> {
                    int start = locals.getCaptureGroupStart(TransitionGuard.getGroupNumber(guard));
                    int end = locals.getCaptureGroupEnd(TransitionGuard.getGroupNumber(guard));
                    switch (kind) {
                        case checkGroupMatched -> {
                            if (start == -1 || end == -1) {
                                return false;
                            }
                        }
                        case checkGroupNotMatched -> {
                            if (start != -1 && end != -1) {
                                return false;
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }
        locals.saveIndex(getNewIndex(locals, targetStateRecord, index));
        return true;
    }

    private long getBackRefBounds(TRegexBacktrackingNFAExecutorLocals locals, int backReferenceStateRecord) {
        int record = getStateData(backReferenceStateRecord);
        int length = backRefNumbers[record];
        for (int i = 0; i < length; i++) {
            int backRefNumber = backRefNumbers[record + 1 + i];
            final int start;
            if (isRecursiveBackreferences() && isRecursiveReference(backReferenceStateRecord)) {
                start = locals.getRecursiveCaptureGroupStart(backRefNumber);
            } else {
                start = locals.getCaptureGroupStart(backRefNumber);
            }
            int end = locals.getCaptureGroupEnd(backRefNumber);
            if (start >= 0 && end >= 0) {
                return packBackRefBounds(start, end);
            }
        }
        return packBackRefBounds(-1, -1);
    }

    private long getBackRefBounds(TRegexBacktrackingNFAExecutorLocals locals, int backReferenceStateRecord, int transitionRecord, int index) {
        assert !isRecursiveBackreferences();
        int record = getStateData(backReferenceStateRecord);
        int length = backRefNumbers[record];
        int guardRecord = getTransitionGuards(transitionRecord);
        int backRefBoundaryEffectsRecord = guardRecord + 1 + getGuardCount(guardRecord);
        for (int i = 0; i < length; i++) {
            int backRefNumber = backRefNumbers[record + 1 + i];
            long boundaryEffects = guards[backRefBoundaryEffectsRecord + i];
            int start = getBackRefBoundary(locals, boundaryEffects, BACKREF_START_BOUNDARY_EFFECT_SHIFT, Group.groupNumberToBoundaryIndexStart(backRefNumber), index);
            int end = getBackRefBoundary(locals, boundaryEffects, BACKREF_END_BOUNDARY_EFFECT_SHIFT, Group.groupNumberToBoundaryIndexEnd(backRefNumber), index);
            if (start >= 0 && end >= 0) {
                return packBackRefBounds(start, end);
            }
        }
        return packBackRefBounds(-1, -1);
    }

    private static long packBackRefBounds(int start, int end) {
        return ((long) start << 32) | (end & 0xffffffffL);
    }

    private static int getBackRefBoundsStart(long bounds) {
        return (int) (bounds >> 32);
    }

    private static int getBackRefBoundsEnd(long bounds) {
        return (int) bounds;
    }

    private int getNewIndex(TRegexBacktrackingNFAExecutorLocals locals, int targetStateRecord, int index) {
        CompilerAsserts.partialEvaluationConstant(getStateKind(targetStateRecord));
        switch (getStateKind(targetStateRecord)) {
            case PureNFAState.KIND_INITIAL_OR_FINAL_STATE, PureNFAState.KIND_SUB_MATCHER, PureNFAState.KIND_EMPTY_MATCH:
                return index;
            case PureNFAState.KIND_CHARACTER_CLASS:
                return locals.getNextIndex();
            case PureNFAState.KIND_BACK_REFERENCE:
                if (canInlineBackReferenceIntoTransition(targetStateRecord)) {
                    long backRefBounds = getBackRefBounds(locals, targetStateRecord);
                    final int start = getBackRefBoundsStart(backRefBounds);
                    final int end = getBackRefBoundsEnd(backRefBounds);
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
    private boolean canInlineBackReferenceIntoTransition(int backReferenceStateRecord) {
        assert isBackReference(backReferenceStateRecord);
        return canInlineBackReferenceIntoTransition(isIgnoreCaseReference(backReferenceStateRecord), flags);
    }

    private static boolean canInlineBackReferenceIntoTransition(PureNFAState backReferenceState, int flags) {
        assert backReferenceState.isBackReference();
        return canInlineBackReferenceIntoTransition(PureNFAState.isIgnoreCaseReference(backReferenceState.getFlags()), flags);
    }

    private static boolean canInlineBackReferenceIntoTransition(boolean ignoreCaseReference, int flags) {
        return !(ignoreCaseReference || isFlagSet(flags, FLAG_LONE_SURROGATES) || isFlagSet(flags, FLAG_RECURSIVE_BACK_REFERENCES));
    }

    private boolean matchBackReferenceSimple(TRegexBacktrackingNFAExecutorLocals locals, int backReferenceStateRecord, int transitionRecord, int index) {
        assert isBackReference(backReferenceStateRecord);
        assert canInlineBackReferenceIntoTransition(backReferenceStateRecord);
        assert !isRecursiveBackreferences();
        long backRefBounds = getBackRefBounds(locals, backReferenceStateRecord, transitionRecord, index);
        final int backrefStart = getBackRefBoundsStart(backRefBounds);
        final int backrefEnd = getBackRefBoundsEnd(backRefBounds);
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

    private int matchBackReferenceGeneric(TRegexBacktrackingNFAExecutorLocals locals, int backReferenceStateRecord, TruffleString.CodeRange codeRange) {
        assert isBackReference(backReferenceStateRecord);
        assert !canInlineBackReferenceIntoTransition(backReferenceStateRecord);
        long backRefBounds = getBackRefBounds(locals, backReferenceStateRecord);
        final int backrefStart = getBackRefBoundsStart(backRefBounds);
        final int backrefEnd = getBackRefBoundsEnd(backRefBounds);
        if (backrefStart < 0 || backrefEnd < 0) {
            return isBackrefWithNullTargetFails() ? -1 : locals.getIndex();
        }
        if (isBackreferenceIgnoreCaseMultiCharExpansion() && isIgnoreCaseReference(backReferenceStateRecord)) {
            return matchBackreferenceGenericMultiCharExpansion(locals, codeRange, backrefStart, backrefEnd);
        } else {
            return matchBackreferenceGenericSingleChars(locals, backReferenceStateRecord, codeRange, backrefStart, backrefEnd);
        }
    }

    private int matchBackreferenceGenericSingleChars(TRegexBacktrackingNFAExecutorLocals locals, int backReferenceStateRecord, TruffleString.CodeRange codeRange, int backrefStart, int backrefEnd) {
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
                            !(isIgnoreCaseReference(backReferenceStateRecord) ? equalsIgnoreCase(codePointBR, codePointI, isIgnoreCaseReferenceAlternativeMode(backReferenceStateRecord))
                                            : codePointBR == codePointI))) {
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

    private int getStateDirectoryLength() {
        return nfa[0];
    }

    private int getStateRecord(int stateIndex) {
        return nfa[stateIndex];
    }

    private int getStateFlags(int stateRecord) {
        return nfa[stateRecord + STATE_FIELD_FLAGS];
    }

    private int getStateKind(int stateRecord) {
        return nfa[stateRecord + STATE_FIELD_KIND];
    }

    private int getStateTransitionCount(int stateRecord) {
        return nfa[stateRecord + STATE_FIELD_TRANSITION_COUNT];
    }

    private int getStateData(int stateRecord) {
        return nfa[stateRecord + STATE_FIELD_DATA];
    }

    private static int getTransitionRecord(int stateRecord, int localTransitionIndex) {
        return stateRecord + STATE_HEADER_SIZE + localTransitionIndex * TRANSITION_RECORD_SIZE;
    }

    private int getTransitionTargetStateRecord(int transitionRecord) {
        return nfa[transitionRecord + TRANSITION_FIELD_TARGET];
    }

    private int getTransitionFlags(int transitionRecord) {
        return nfa[transitionRecord + TRANSITION_FIELD_FLAGS];
    }

    private int getTransitionGroupBoundaries(int transitionRecord) {
        return nfa[transitionRecord + TRANSITION_FIELD_GROUP_BOUNDARIES];
    }

    private int getTransitionGuards(int transitionRecord) {
        return nfa[transitionRecord + TRANSITION_FIELD_GUARDS];
    }

    private boolean hasCaretGuard(int transitionRecord) {
        return PureNFATransition.hasCaretGuard(getTransitionFlags(transitionRecord));
    }

    private boolean hasDollarGuard(int transitionRecord) {
        return PureNFATransition.hasDollarGuard(getTransitionFlags(transitionRecord));
    }

    private boolean hasMatchBeginGuard(int transitionRecord) {
        return PureNFATransition.hasMatchBeginGuard(getTransitionFlags(transitionRecord));
    }

    private boolean hasMatchEndGuard(int transitionRecord) {
        return PureNFATransition.hasMatchEndGuard(getTransitionFlags(transitionRecord));
    }

    private int getGuardCount(int guardRecord) {
        return (int) guards[guardRecord];
    }

    private long guardAt(int guardRecord, int i) {
        return guards[guardRecord + 1 + i];
    }

    private boolean isAnchoredFinalState(int stateRecord) {
        return BasicState.isAnchoredFinalState(getStateFlags(stateRecord));
    }

    private boolean isUnAnchoredFinalState(int stateRecord) {
        return BasicState.isUnAnchoredFinalState(getStateFlags(stateRecord));
    }

    private boolean isFinalState(int stateRecord) {
        return isAnchoredFinalState(stateRecord) || isUnAnchoredFinalState(stateRecord);
    }

    private boolean isDeterministic(int stateRecord) {
        return PureNFAState.isDeterministic(getStateFlags(stateRecord));
    }

    private boolean isCharacterClass(int stateRecord) {
        return getStateKind(stateRecord) == PureNFAState.KIND_CHARACTER_CLASS;
    }

    private boolean isBackReference(int stateRecord) {
        return getStateKind(stateRecord) == PureNFAState.KIND_BACK_REFERENCE;
    }

    private boolean isSubMatcher(int stateRecord) {
        return getStateKind(stateRecord) == PureNFAState.KIND_SUB_MATCHER;
    }

    private boolean isSubMatcherNegated(int stateRecord) {
        return PureNFAState.isSubMatcherNegated(getStateFlags(stateRecord));
    }

    private boolean isLookAround(int stateRecord) {
        return PureNFAState.isLookAround(getStateFlags(stateRecord));
    }

    private boolean isIgnoreCaseReference(int stateRecord) {
        return PureNFAState.isIgnoreCaseReference(getStateFlags(stateRecord));
    }

    private boolean isRecursiveReference(int stateRecord) {
        return PureNFAState.isRecursiveReference(getStateFlags(stateRecord));
    }

    private boolean isIgnoreCaseReferenceAlternativeMode(int stateRecord) {
        return PureNFAState.isIgnoreCaseReferenceAlternativeMode(getStateFlags(stateRecord));
    }

    private static final class Builder {

        private static TRegexBacktrackingNFAExecutorNode create(RegexAST ast, PureNFA nfa, int numberOfStates, int numberOfTransitions, TRegexExecutorBaseNode[] subExecutors, boolean mustAdvance,
                        CompilationBuffer compilationBuffer) {
            RegexASTSubtreeRootNode subtree = nfa.getASTSubtree(ast);
            int flags = createFlags(ast, nfa, mustAdvance, subtree, numberOfStates, numberOfTransitions);
            int[] quantifiers = createQuantifierRecords(ast);
            int nZeroWidthQuantifiers = ast.getZeroWidthQuantifiables().size();
            int[] zeroWidthQuantifiers = nZeroWidthQuantifiers == 0 ? EmptyArrays.INT : new int[nZeroWidthQuantifiers * ZERO_WIDTH_QUANTIFIER_RECORD_SIZE];
            int[] zeroWidthTermEnclosedCGLow = nZeroWidthQuantifiers == 0 ? EmptyArrays.INT : new int[nZeroWidthQuantifiers];
            int[] zeroWidthQuantifierCGOffsets = new int[zeroWidthTermEnclosedCGLow.length + 1];
            int offset = 0;
            for (int i = 0; i < nZeroWidthQuantifiers; i++) {
                QuantifiableTerm quantifiable = ast.getZeroWidthQuantifiables().get(i);
                if (quantifiable.isGroup()) {
                    Group group = quantifiable.asGroup();
                    zeroWidthTermEnclosedCGLow[i] = group.getCaptureGroupsLo();
                    offset += 2 * (group.getCaptureGroupsHi() - group.getCaptureGroupsLo());
                }
                zeroWidthQuantifierCGOffsets[i + 1] = offset;
                Quantifier quantifier = quantifiable.getQuantifier();
                int zeroWidthIndex = quantifier.getZeroWidthIndex();
                zeroWidthQuantifiers[getZeroWidthQuantifierRecord(zeroWidthIndex) + ZERO_WIDTH_QUANTIFIER_FIELD_MIN] = quantifier.getMin();
                zeroWidthQuantifiers[getZeroWidthQuantifierRecord(zeroWidthIndex) + ZERO_WIDTH_QUANTIFIER_FIELD_INDEX] = quantifier.hasIndex() ? quantifier.getIndex() : -1;
            }
            InnerLiteral innerLiteral = nfa.isRoot() && ast.getProperties().hasInnerLiteral() ? ast.extractInnerLiteral() : null;
            RegexFlavor.EqualsIgnoreCasePredicate equalsIgnoreCase = ast.getFlavor().getEqualsIgnoreCasePredicate(ast);
            CaseFoldData.CaseFoldAlgorithm multiCharacterExpansionCaseFoldAlgorithm = isFlagSet(flags, FLAG_BACKREF_IGNORE_CASE_MULTI_CHARACTER_EXPANSION) && ast.getProperties().hasBackReferences()
                            ? ast.getFlavor().getCaseFoldAlgorithm(ast)
                            : null;
            nfa.materializeGroupBoundaries();
            CharMatchers.Builder matcherBuilder = new CharMatchers.Builder();
            int loopbackInitialStateMatcherRef;
            if (isFlagSet(flags, FLAG_LOOPBACK_INITIAL_STATE) && innerLiteral == null) {
                CodePointSet initialCharSet = nfa.getMergedInitialStateCharSet(ast, compilationBuffer);
                loopbackInitialStateMatcherRef = initialCharSet == null ? NO_MATCHER : matcherBuilder.getOrCreateMatcher(initialCharSet, compilationBuffer);
            } else {
                loopbackInitialStateMatcherRef = NO_MATCHER;
            }
            EncodedGroupBoundaries.Builder groupBoundariesBuilder = new EncodedGroupBoundaries.Builder();
            LongArrayBuffer guardsBuffer = new LongArrayBuffer();
            guardsBuffer.add(0);
            EconomicMap<LongArrayKey, Integer> guardMap = EconomicMap.create(Equivalence.DEFAULT);
            IntArrayBuffer backRefNumbersBuffer = new IntArrayBuffer();
            EconomicMap<IntArrayKey, Integer> backRefNumbersMap = EconomicMap.create(Equivalence.DEFAULT);
            int nStates = nfa.getNumberOfStates();
            int recordsOffset = nStates;
            int compactNFASize = recordsOffset;
            for (int i = 0; i < nStates; i++) {
                PureNFAState s = nfa.getState(i);
                compactNFASize += STATE_HEADER_SIZE + s.getSuccessors().length * TRANSITION_RECORD_SIZE;
            }
            int[] compactNFA = new int[compactNFASize];
            int stateRecord = recordsOffset;
            for (int i = 0; i < nStates; i++) {
                PureNFAState s = nfa.getState(i);
                compactNFA[i] = stateRecord;
                stateRecord += STATE_HEADER_SIZE + s.getSuccessors().length * TRANSITION_RECORD_SIZE;
            }
            int maxTransitions = 0;
            int record = recordsOffset;
            for (int i = 0; i < nStates; i++) {
                PureNFAState s = nfa.getState(i);
                s.initIsDeterministic(compilationBuffer);
                PureNFATransition[] successors = s.getSuccessors();
                assert compactNFA[i] == record;
                maxTransitions = Math.max(maxTransitions, successors.length);
                compactNFA[record + STATE_FIELD_FLAGS] = s.getFlags();
                compactNFA[record + STATE_FIELD_KIND] = s.getKind();
                compactNFA[record + STATE_FIELD_TRANSITION_COUNT] = successors.length;
                compactNFA[record + STATE_FIELD_DATA] = getStateData(s, matcherBuilder, compilationBuffer, backRefNumbersBuffer, backRefNumbersMap);
                record += STATE_HEADER_SIZE;
                for (PureNFATransition transition : successors) {
                    PureNFAState targetState = transition.getTarget();
                    compactNFA[record + TRANSITION_FIELD_TARGET] = compactNFA[targetState.getId()];
                    compactNFA[record + TRANSITION_FIELD_FLAGS] = transition.getFlags();
                    GroupBoundaries transitionGroupBoundaries = transition.getGroupBoundaries();
                    compactNFA[record + TRANSITION_FIELD_GROUP_BOUNDARIES] = groupBoundariesBuilder.getOrCreate(transitionGroupBoundaries);
                    int[] transitionBackRefNumbers = targetState.isBackReference() && canInlineBackReferenceIntoTransition(targetState, flags) ? targetState.getBackRefNumbers() : EmptyArrays.INT;
                    compactNFA[record + TRANSITION_FIELD_GUARDS] = guardRecordOf(guardsBuffer, guardMap, transition.getGuards(), transitionGroupBoundaries, transitionBackRefNumbers);
                    record += TRANSITION_RECORD_SIZE;
                }
            }
            assert record == compactNFA.length;
            for (TRegexExecutorBaseNode subExecutor : subExecutors) {
                if (subExecutor instanceof TRegexBacktrackingNFAExecutorNode) {
                    maxTransitions = Math.max(maxTransitions, ((TRegexBacktrackingNFAExecutorNode) subExecutor).maxNTransitions);
                }
            }
            return new TRegexBacktrackingNFAExecutorNode(ast, numberOfTransitions, subExecutors, compactNFA, matcherBuilder.toArray(), groupBoundariesBuilder.toArray(), guardsBuffer.toArray(),
                            quantifiers, zeroWidthQuantifiers, backRefNumbersBuffer.toArray(), nfa.getFixedWidth(), compactNFA[nfa.getAnchoredInitialState().getId()],
                            compactNFA[nfa.getUnAnchoredInitialState().getId()], numberOfStates, maxTransitions, flags, innerLiteral, zeroWidthTermEnclosedCGLow,
                            zeroWidthQuantifierCGOffsets, equalsIgnoreCase, loopbackInitialStateMatcherRef, multiCharacterExpansionCaseFoldAlgorithm);
        }

        private static int[] createQuantifierRecords(RegexAST ast) {
            Quantifier[] astQuantifiers = ast.getQuantifierArray();
            if (astQuantifiers.length == 0) {
                return EmptyArrays.INT;
            }
            int[] ret = new int[astQuantifiers.length * QUANTIFIER_RECORD_SIZE];
            for (int i = 0; i < astQuantifiers.length; i++) {
                Quantifier quantifier = astQuantifiers[i];
                ret[getQuantifierRecord(i) + QUANTIFIER_FIELD_MIN] = quantifier.getMin();
                ret[getQuantifierRecord(i) + QUANTIFIER_FIELD_MAX] = quantifier.getMax();
            }
            return ret;
        }

        private static int getStateData(PureNFAState s, CharMatchers.Builder matcherBuilder, CompilationBuffer compilationBuffer,
                        IntArrayBuffer backRefNumbersBuffer, EconomicMap<IntArrayKey, Integer> backRefNumbersMap) {
            switch (s.getKind()) {
                case PureNFAState.KIND_CHARACTER_CLASS:
                    return matcherBuilder.getOrCreateMatcher(s.getCharSet(), compilationBuffer);
                case PureNFAState.KIND_SUB_MATCHER:
                    return s.getSubtreeId();
                case PureNFAState.KIND_BACK_REFERENCE:
                    return backRefNumbersRecordOf(backRefNumbersBuffer, backRefNumbersMap, s.getBackRefNumbers());
                default:
                    return 0;
            }
        }

        private static int backRefNumbersRecordOf(IntArrayBuffer backRefNumbersBuffer, EconomicMap<IntArrayKey, Integer> backRefNumbersMap, int[] groupNumbers) {
            IntArrayKey key = new IntArrayKey(groupNumbers);
            Integer record = backRefNumbersMap.get(key);
            if (record == null) {
                record = backRefNumbersBuffer.length();
                backRefNumbersMap.put(key, record);
                backRefNumbersBuffer.add(groupNumbers.length);
                for (int groupNumber : groupNumbers) {
                    backRefNumbersBuffer.add(groupNumber);
                }
            }
            return record;
        }

        private static int guardRecordOf(LongArrayBuffer guardsBuffer, EconomicMap<LongArrayKey, Integer> guardMap, long[] transitionGuards, GroupBoundaries transitionGroupBoundaries,
                        int[] backRefNumbers) {
            if (transitionGuards.length == 0 && backRefNumbers.length == 0) {
                return 0;
            }
            long[] encodedGuardRecord = encodeGuardRecord(transitionGuards, transitionGroupBoundaries, backRefNumbers);
            LongArrayKey key = new LongArrayKey(transitionGuards.length, encodedGuardRecord);
            Integer record = guardMap.get(key);
            if (record == null) {
                record = guardsBuffer.length();
                guardMap.put(key, record);
                guardsBuffer.add(transitionGuards.length);
                guardsBuffer.addAll(encodedGuardRecord);
            }
            return record;
        }

        private static long[] encodeGuardRecord(long[] guards, GroupBoundaries groupBoundaries, int[] backRefNumbers) {
            long[] ret = backRefNumbers.length == 0 ? null : Arrays.copyOf(guards, guards.length + backRefNumbers.length);
            for (int i = 0; i < guards.length; i++) {
                long guard = guards[i];
                long encodedGuard = encodeGuardBoundaryEffects(guard, groupBoundaries);
                if (ret != null) {
                    ret[i] = encodedGuard;
                } else if (encodedGuard != guard) {
                    ret = Arrays.copyOf(guards, guards.length);
                    ret[i] = encodedGuard;
                }
            }
            if (backRefNumbers.length != 0) {
                for (int i = 0; i < backRefNumbers.length; i++) {
                    ret[guards.length + i] = encodeBackRefBoundaryEffects(groupBoundaries, backRefNumbers[i]);
                }
            }
            return ret == null ? guards : ret;
        }

        private static long encodeGuardBoundaryEffects(long guard, GroupBoundaries groupBoundaries) {
            TransitionGuard.Kind kind = TransitionGuard.getKind(guard);
            if (kind != TransitionGuard.Kind.checkGroupMatched && kind != TransitionGuard.Kind.checkGroupNotMatched) {
                return guard;
            }
            int groupNumber = TransitionGuard.getGroupNumber(guard);
            return guard |
                            ((long) getBoundaryEffect(groupBoundaries, Group.groupNumberToBoundaryIndexStart(groupNumber)) << GUARD_START_BOUNDARY_EFFECT_SHIFT) |
                            ((long) getBoundaryEffect(groupBoundaries, Group.groupNumberToBoundaryIndexEnd(groupNumber)) << GUARD_END_BOUNDARY_EFFECT_SHIFT);
        }

        private static long encodeBackRefBoundaryEffects(GroupBoundaries groupBoundaries, int groupNumber) {
            return ((long) getBoundaryEffect(groupBoundaries, Group.groupNumberToBoundaryIndexStart(groupNumber)) << BACKREF_START_BOUNDARY_EFFECT_SHIFT) |
                            ((long) getBoundaryEffect(groupBoundaries, Group.groupNumberToBoundaryIndexEnd(groupNumber)) << BACKREF_END_BOUNDARY_EFFECT_SHIFT);
        }

        private static int getBoundaryEffect(GroupBoundaries groupBoundaries, int boundaryIndex) {
            if (groupBoundaries.getUpdateIndices().get(boundaryIndex)) {
                return GUARD_BOUNDARY_EFFECT_UPDATE;
            }
            if (groupBoundaries.getClearIndices().get(boundaryIndex)) {
                return GUARD_BOUNDARY_EFFECT_CLEAR;
            }
            return GUARD_BOUNDARY_EFFECT_UNCHANGED;
        }

        private static final class IntArrayKey {
            private final int[] values;
            private final int hashCode;

            IntArrayKey(int[] values) {
                this.values = values.length == 0 ? EmptyArrays.INT : Arrays.copyOf(values, values.length);
                this.hashCode = Arrays.hashCode(values);
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof IntArrayKey other && Arrays.equals(values, other.values);
            }

            @Override
            public int hashCode() {
                return hashCode;
            }
        }

        private static final class LongArrayKey {
            private final int guardCount;
            private final long[] values;
            private final int hashCode;

            LongArrayKey(int guardCount, long[] values) {
                this.guardCount = guardCount;
                this.values = values.length == 0 ? EmptyArrays.LONG : Arrays.copyOf(values, values.length);
                this.hashCode = 31 * guardCount + Arrays.hashCode(values);
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof LongArrayKey other && guardCount == other.guardCount && Arrays.equals(values, other.values);
            }

            @Override
            public int hashCode() {
                return hashCode;
            }
        }

    }

}
