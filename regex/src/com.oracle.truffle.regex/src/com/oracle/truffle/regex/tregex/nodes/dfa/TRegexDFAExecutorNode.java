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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import static com.oracle.truffle.api.CompilerDirectives.LIKELY_PROBABILITY;
import static com.oracle.truffle.api.CompilerDirectives.SLOWPATH_PROBABILITY;
import static com.oracle.truffle.api.CompilerDirectives.UNLIKELY_PROBABILITY;
import static com.oracle.truffle.api.CompilerDirectives.injectBranchProbability;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.RegexRootNode;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CharMatchers;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorBaseNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputOps;
import com.oracle.truffle.regex.tregex.parser.ast.InnerLiteral;
import com.oracle.truffle.regex.tregex.string.Encoding;
import com.oracle.truffle.regex.tregex.util.MathUtil;

public final class TRegexDFAExecutorNode extends TRegexExecutorNode {

    public static final int NO_MATCH = -2;
    private static final int FLAG_FORWARD = 1 << 0;
    private static final int FLAG_SEARCHING = 1 << 1;
    private static final int FLAG_GENERIC_CG = 1 << 2;
    private static final int FLAG_ALLOW_SIMPLE_CG = 1 << 3;
    private static final int FLAG_SIMPLE_CG = 1 << 4;
    private static final int FLAG_SIMPLE_CG_MUST_COPY = 1 << 5;
    private static final int FLAG_CAN_FIND_START = 1 << 6;
    private static final int FLAG_TRACK_LAST_GROUP = 1 << 7;

    @CompilationFinal private int flags;
    private final int minResultLength;
    private final int maxNumberOfNFAStates;
    private final int counterFixedDataSize;
    private final int counterIntArrayCount;
    @CompilationFinal(dimensions = 1) private final TruffleString.CodePointSet[] indexOfParameters;
    @CompilationFinal(dimensions = 1) private final byte[] dfaRecords;
    @CompilationFinal(dimensions = 1) private final long[] bqRecords;
    private final int numberOfDFAStates;
    private final InnerLiteral innerLiteral;
    @CompilationFinal(dimensions = 1) private final int[] encodedMatchers;
    @CompilationFinal(dimensions = 1) private final int[] sequentialMatcherRecords;
    @CompilationFinal(dimensions = 1) private final AllTransitionsInOneTreeMatcher[] treeMatchers;
    @CompilationFinal(dimensions = 1) private final byte[] cgTransitionRecords;
    @CompilationFinal(dimensions = 1) private final byte[] cgLazyTransitionRecords;
    @CompilationFinal(dimensions = 1) private final int[] cgResultOrder;
    private final TRegexDFAExecutorDebugRecorder debugRecorder;
    @CompilationFinal(dimensions = 1) private final CounterTracker[] counterTrackers;

    @Children private TruffleString.ByteIndexOfCodePointSetNode[] indexOfNodes;
    @Child private TruffleString.ByteIndexOfStringNode indexOfStringNode;
    /**
     * A TRegexDFAExecutorNode, or TRegexExecutorBaseNodeWrapper when instrumented.
     */
    @Child private TRegexExecutorBaseNode innerLiteralPrefixMatcher;

    public TRegexDFAExecutorNode(
                    RegexSource source,
                    TRegexDFAExecutorProperties props,
                    int numberOfCaptureGroups,
                    int maxNumberOfNFAStates,
                    TruffleString.CodePointSet[] indexOfParameters,
                    DFAAbstractNode[] states,
                    int[] encodedMatchers,
                    int[] sequentialMatcherRecords,
                    AllTransitionsInOneTreeMatcher[] treeMatchers,
                    byte[] cgTransitionRecords,
                    byte[] cgLazyTransitionRecords,
                    TRegexDFAExecutorDebugRecorder debugRecorder,
                    TRegexDFAExecutorNode innerLiteralPrefixMatcher,
                    CounterTrackerData.Builder counterDataBuilder,
                    CounterTracker[] counterTrackers) {
        this(source, createFlags(props), props.getMinResultLength(), numberOfCaptureGroups, maxNumberOfNFAStates,
                        indexOfParameters, DFAExecutorRecords.create(states, sequentialMatcherRecords), encodedMatchers, sequentialMatcherRecords, treeMatchers, cgTransitionRecords,
                        cgLazyTransitionRecords,
                        props.isGenericCG() && maxNumberOfNFAStates > 1 ? initResultOrder(maxNumberOfNFAStates, numberOfCaptureGroups, props.tracksLastGroup()) : null, debugRecorder,
                        innerLiteralPrefixMatcher, counterDataBuilder.getFixedDataSize(), counterDataBuilder.getNumberOfIntArrays(), counterTrackers);
    }

    private TRegexDFAExecutorNode(
                    RegexSource source,
                    int flags,
                    int minResultLength,
                    int numberOfCaptureGroups,
                    int maxNumberOfNFAStates,
                    TruffleString.CodePointSet[] indexOfParameters,
                    DFAExecutorRecords.Data dfaData,
                    int[] encodedMatchers,
                    int[] sequentialMatcherRecords,
                    AllTransitionsInOneTreeMatcher[] treeMatchers,
                    byte[] cgTransitionRecords,
                    byte[] cgLazyTransitionRecords,
                    int[] cgResultOrder,
                    TRegexDFAExecutorDebugRecorder debugRecorder,
                    TRegexDFAExecutorNode innerLiteralPrefixMatcher,
                    int counterFixedDataSize,
                    int counterIntArrayCount,
                    CounterTracker[] counterTrackers) {
        this(source, flags, minResultLength, numberOfCaptureGroups, dfaData.numberOfTransitions, maxNumberOfNFAStates, indexOfParameters,
                        dfaData.records, dfaData.bqRecords, dfaData.numberOfNodes, dfaData.innerLiteral, encodedMatchers, sequentialMatcherRecords, treeMatchers,
                        cgTransitionRecords, cgLazyTransitionRecords, cgResultOrder, debugRecorder, innerLiteralPrefixMatcher, counterFixedDataSize, counterIntArrayCount, counterTrackers);
        if (dfaData.numberOfTransitions > source.getOptions().getMaxDFASize()) {
            throw new UnsupportedRegexException("too many transitions");
        }
    }

    private TRegexDFAExecutorNode(
                    RegexSource source,
                    int flags,
                    int minResultLength,
                    int numberOfCaptureGroups,
                    int numberOfTransitions,
                    int maxNumberOfNFAStates,
                    TruffleString.CodePointSet[] indexOfParameters,
                    byte[] dfaRecords,
                    long[] bqRecords,
                    int numberOfDFAStates,
                    InnerLiteral innerLiteral,
                    int[] encodedMatchers,
                    int[] sequentialMatcherRecords,
                    AllTransitionsInOneTreeMatcher[] treeMatchers,
                    byte[] cgTransitionRecords,
                    byte[] cgLazyTransitionRecords,
                    int[] cgResultOrder,
                    TRegexDFAExecutorDebugRecorder debugRecorder,
                    TRegexDFAExecutorNode innerLiteralPrefixMatcher,
                    int counterFixedDataSize,
                    int counterIntArrayCount,
                    CounterTracker[] counterTrackers) {
        super(source, numberOfCaptureGroups, numberOfTransitions);
        this.flags = flags;
        this.minResultLength = minResultLength;
        this.maxNumberOfNFAStates = maxNumberOfNFAStates;
        this.counterFixedDataSize = counterFixedDataSize;
        this.counterIntArrayCount = counterIntArrayCount;
        this.indexOfParameters = indexOfParameters;
        this.dfaRecords = dfaRecords;
        this.bqRecords = bqRecords;
        this.numberOfDFAStates = numberOfDFAStates;
        this.innerLiteral = innerLiteral;
        this.encodedMatchers = encodedMatchers;
        this.sequentialMatcherRecords = sequentialMatcherRecords;
        this.treeMatchers = treeMatchers;
        this.cgTransitionRecords = cgTransitionRecords;
        this.cgLazyTransitionRecords = cgLazyTransitionRecords;
        this.cgResultOrder = cgResultOrder;
        this.debugRecorder = debugRecorder;
        this.innerLiteralPrefixMatcher = innerLiteralPrefixMatcher;
        this.counterTrackers = counterTrackers;
    }

    private TRegexDFAExecutorNode(TRegexDFAExecutorNode copy, TRegexDFAExecutorNode innerLiteralPrefixMatcher) {
        this(copy.getSource(),
                        copy.flags,
                        copy.minResultLength,
                        copy.getNumberOfCaptureGroups(),
                        copy.getNumberOfTransitions(),
                        copy.maxNumberOfNFAStates,
                        copy.indexOfParameters,
                        copy.dfaRecords,
                        copy.bqRecords,
                        copy.numberOfDFAStates,
                        copy.innerLiteral,
                        copy.encodedMatchers,
                        copy.sequentialMatcherRecords,
                        copy.treeMatchers,
                        copy.cgTransitionRecords,
                        copy.cgLazyTransitionRecords,
                        copy.cgResultOrder,
                        copy.debugRecorder,
                        innerLiteralPrefixMatcher,
                        copy.counterFixedDataSize,
                        copy.counterIntArrayCount,
                        copy.counterTrackers);
    }

    @Override
    public TRegexDFAExecutorNode shallowCopy() {
        return new TRegexDFAExecutorNode(this, innerLiteralPrefixMatcher == null ? null : (TRegexDFAExecutorNode) innerLiteralPrefixMatcher.shallowCopy());
    }

    byte[] getCGPartialTransitionRecords() {
        assert isGenericCG();
        return cgTransitionRecords;
    }

    byte[] getSimpleCGTransitionRecords() {
        assert isSimpleCG();
        return cgTransitionRecords;
    }

    byte[] getCGLazyTransitionRecords() {
        return cgLazyTransitionRecords;
    }

    public int getPrefixLength() {
        return (DFAInitialState.getSuccessorCount(dfaRecords) / 2) - 1;
    }

    public boolean isAnchored() {
        return !DFAInitialState.hasUnAnchoredEntry(dfaRecords);
    }

    @Override
    public String getName() {
        return "dfa";
    }

    @Override
    public boolean isForward() {
        return (flags & FLAG_FORWARD) != 0;
    }

    @Override
    public boolean isTrivial() {
        return getNumberOfTransitions() < (isGenericCG() ? (TRegexOptions.TRegexMaxTransitionsInTrivialExecutor * 3) / 4 : TRegexOptions.TRegexMaxTransitionsInTrivialExecutor);
    }

    public boolean isBackward() {
        return !isForward();
    }

    public boolean isSearching() {
        return (flags & FLAG_SEARCHING) != 0;
    }

    @Override
    public boolean isSimpleCG() {
        return (flags & FLAG_SIMPLE_CG) != 0;
    }

    public boolean isGenericCG() {
        return (flags & FLAG_GENERIC_CG) != 0;
    }

    public boolean canFindStart() {
        return (flags & FLAG_CAN_FIND_START) != 0;
    }

    public boolean isSimpleCGMustCopy() {
        return (flags & FLAG_SIMPLE_CG_MUST_COPY) != 0;
    }

    public void setSimpleCGMustCopy(boolean simpleCGMustCopy) {
        flags = simpleCGMustCopy ? flags | FLAG_SIMPLE_CG_MUST_COPY : flags & ~FLAG_SIMPLE_CG_MUST_COPY;
    }

    public boolean tracksLastGroup() {
        return (flags & FLAG_TRACK_LAST_GROUP) != 0;
    }

    @Override
    public int getNumberOfStates() {
        return numberOfDFAStates;
    }

    public boolean recordExecution() {
        return debugRecorder != null;
    }

    public TRegexDFAExecutorDebugRecorder getDebugRecorder() {
        return debugRecorder;
    }

    TruffleString.ByteIndexOfCodePointSetNode getIndexOfNode(int index) {
        if (indexOfNodes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            TruffleString.ByteIndexOfCodePointSetNode[] nodes = new TruffleString.ByteIndexOfCodePointSetNode[indexOfParameters.length];
            for (int i = 0; i < nodes.length; i++) {
                nodes[i] = TruffleString.ByteIndexOfCodePointSetNode.create();
            }
            indexOfNodes = insert(nodes);
        }
        return indexOfNodes[index];
    }

    TruffleString.ByteIndexOfStringNode getIndexOfStringNode() {
        if (indexOfStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            indexOfStringNode = insert(TruffleString.ByteIndexOfStringNode.create());
        }
        return indexOfStringNode;
    }

    @ExplodeLoop
    @Override
    public TRegexExecutorLocals createLocals(TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index) {
        long[] fixedData = new long[counterFixedDataSize];
        int[][] intArrays = new int[counterIntArrayCount][];
        for (CounterTracker tracker : counterTrackers) {
            tracker.init(fixedData, intArrays);
        }
        return new TRegexDFAExecutorLocals(input, fromIndex, maxIndex, regionFrom, regionTo, index, createCGData(), fixedData, intArrays);
    }

    @Override
    public boolean writesCaptureGroups() {
        return isSimpleCG();
    }

    private DFACaptureGroupTrackingData createCGData() {
        if (isSimpleCG()) {
            return new DFACaptureGroupTrackingData(null,
                            createResultsArray(resultLength()),
                            isSimpleCGMustCopy() ? new int[resultLength()] : null);
        } else if (isGenericCG()) {
            int resultLength = resultLength();
            return new DFACaptureGroupTrackingData(
                            maxNumberOfNFAStates == 1 ? null : Arrays.copyOf(cgResultOrder, cgResultOrder.length),
                            createResultsArray(paddedResultsLength(maxNumberOfNFAStates, resultLength)),
                            new int[resultLength]);
        } else {
            return null;
        }
    }

    private int resultLength() {
        return getNumberOfCaptureGroups() * 2 + (tracksLastGroup() ? 1 : 0);
    }

    private static int paddedResultsLength(int maxNumberOfNFAStates, int resultLength) {
        /*
         * Pad so that results.length - resultLength is an all-ones mask. This lets row
         * starts be masked before fixed-length row copies and row-local writes while preserving
         * all valid row offsets: array.length - resultLength + 1 is the power-of-two exclusive
         * upper bound.
         */
        assert maxNumberOfNFAStates >= 1;
        assert resultLength >= 0;
        int maxStartIndex = (maxNumberOfNFAStates - 1) * resultLength;
        int exclusiveUpperBound = 1 << MathUtil.log2ceil(maxStartIndex + 1);
        assert Integer.bitCount(exclusiveUpperBound) == 1;
        return exclusiveUpperBound + resultLength - 1;
    }

    private static int[] createResultsArray(int length) {
        int[] results = new int[length];
        Arrays.fill(results, -1);
        return results;
    }

    /**
     * records position of the END of the match found, or -1 if no match exists.
     */
    @Override
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    public Object execute(VirtualFrame frame, final TRegexExecutorLocals abstractLocals, final TruffleString.CodeRange codeRange) {
        TRegexDFAExecutorLocals locals = (TRegexDFAExecutorLocals) abstractLocals;
        CompilerDirectives.ensureVirtualized(locals);
        CompilerAsserts.partialEvaluationConstant(dfaRecords);
        CompilerAsserts.partialEvaluationConstant(codeRange);

        if (injectBranchProbability(SLOWPATH_PROBABILITY, !validArgs(locals))) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException(String.format("Got illegal args! (fromIndex %d, maxIndex %d, regionFrom: %d, regionTo: %d, initialIndex %d)",
                            locals.getFromIndex(), locals.getMaxIndex(), locals.getRegionFrom(), locals.getRegionTo(), locals.getIndex()));
        }
        if (isGenericCG() || isSimpleCG()) {
            CompilerDirectives.ensureVirtualized(locals.getCGData());
        }
        // check if input is long enough for a match
        if (injectBranchProbability(UNLIKELY_PROBABILITY, minResultLength > 0 &&
                        (isForward() ? locals.getMaxIndex() - locals.getIndex()
                                        : locals.getIndex() - Math.max(locals.getRegionFrom(), locals.getFromIndex() - getPrefixLength())) < minResultLength)) {
            // no match possible, break immediately
            return isGenericCG() || isSimpleCG() ? null : (long) TRegexDFAExecutorNode.NO_MATCH;
        }
        if (recordExecution()) {
            debugRecorder.startRecording(locals);
        }
        if (isBackward()) {
            locals.setCurMinIndex(locals.getFromIndex());
        }
        int ip = DFAExecutorRecords.INITIAL_STATE_REF;
        outer: while (true) {
            if (CompilerDirectives.inInterpreter()) {
                RegexRootNode.checkThreadInterrupted();
            }
            CompilerAsserts.partialEvaluationConstant(ip);
            if (ip < 0) {
                break;
            }
            DFAExecutorRecords.Kind kind = DFAExecutorRecords.getKind(dfaRecords, ip);
            CompilerAsserts.partialEvaluationConstant(kind);
            if (DFAExecutorRecords.isTransition(kind)) {
                ip = applyTransitionRecord(kind, ip, locals);
            } else {
                final int stateRef = ip;
                if (kind == DFAExecutorRecords.Kind.initialState) {
                    final int successorCount = DFAInitialState.getSuccessorCount(dfaRecords);
                    CompilerAsserts.partialEvaluationConstant(successorCount);
                    /*
                     * initial state selection
                     */
                    final boolean atBegin;
                    if (isSearching()) {
                        assert isForward();
                        /*
                         * We are in search mode - rewind up to prefixLength code points and select
                         * successors[n], where n is the number of skipped code points.
                         */
                        for (int i = 0; i < getPrefixLength(); i++) {
                            if (injectBranchProbability(UNLIKELY_PROBABILITY, locals.getIndex() > locals.getRegionFrom())) {
                                inputSkipIntl(locals, false, codeRange);
                            } else {
                                if (canFindStart()) {
                                    locals.setMatchStart(locals.getIndex());
                                }
                                initNextIndex(locals);
                                ip = initialStateSuccessor(locals, i);
                                continue outer;
                            }
                        }
                        if (canFindStart()) {
                            locals.setMatchStart(locals.getIndex());
                        }
                        initNextIndex(locals);
                        atBegin = inputAtBegin(locals);
                    } else {
                        /*
                         * We are in non-searching mode - if we start behind fromIndex, select
                         * successors[n], where n is the number of code points between the current
                         * index and fromIndex.
                         */
                        initNextIndex(locals);
                        atBegin = inputAtBegin(locals);
                        for (int i = 0; i < getPrefixLength(); i++) {
                            assert isForward();
                            if (locals.getIndex() < locals.getFromIndex()) {
                                inputSkipIntl(locals, true, codeRange);
                            } else {
                                if (injectBranchProbability(LIKELY_PROBABILITY, atBegin)) {
                                    ip = initialStateSuccessor(locals, i);
                                    continue outer;
                                } else {
                                    ip = initialStateSuccessor(locals, i + (successorCount / 2));
                                    continue outer;
                                }
                            }
                        }
                    }
                    if (injectBranchProbability(LIKELY_PROBABILITY, atBegin)) {
                        ip = initialStateSuccessor(locals, getPrefixLength());
                        continue outer;
                    } else {
                        ip = initialStateSuccessor(locals, getPrefixLength() + (successorCount / 2));
                        continue outer;
                    }
                } else if (DFAExecutorRecords.isDFAState(kind)) {
                    final int successorCount = DFAState.getSuccessorCount(dfaRecords, stateRef);
                    CompilerAsserts.partialEvaluationConstant(successorCount);
                    if (CompilerDirectives.hasNextTier()) {
                        locals.incLoopCount(this);
                    }
                    /*
                     * find matching DFA state transition
                     */
                    if (isGenericCG()) {
                        locals.setLastIndex();
                    }
                    inputAdvance(locals);
                    if (beforeFindSuccessor(kind, stateRef, locals)) {
                        return 0L;
                    }
                    boolean canDoIndexOf = isForward() && DFAState.canDoIndexOf(dfaRecords, stateRef, codeRange);
                    CompilerAsserts.partialEvaluationConstant(canDoIndexOf);
                    if (canDoIndexOf && injectBranchProbability(CONTINUE_PROBABILITY, inputHasNext(locals))) {
                        int indexOfNodeId = DFAState.getIndexOfNodeId(dfaRecords, stateRef);
                        TruffleString.ByteIndexOfCodePointSetNode indexOfNode = getIndexOfNode(indexOfNodeId);
                        TruffleString.CodePointSet indexOfParameter = indexOfParameters[indexOfNodeId];
                        CompilerAsserts.partialEvaluationConstant(indexOfNodeId);
                        CompilerAsserts.partialEvaluationConstant(indexOfNode);
                        CompilerAsserts.partialEvaluationConstant(indexOfParameter);
                        int indexOfResult = InputOps.indexOf(locals.getInput(), locals.getIndex(), getMaxIndex(locals), indexOfParameter, getEncoding(), indexOfNode);
                        int postLoopIndex = indexOfResult < 0 ? getMaxIndex(locals) : indexOfResult;
                        afterIndexOf(kind, stateRef, locals, locals.getIndex(), postLoopIndex, codeRange);
                        assert locals.getIndex() == postLoopIndex;
                        boolean canTakeDirectSuccessor = successorCount == 2 && !DFAState.indexOfMayHaveNoSuccessor(dfaRecords, stateRef);
                        CompilerAsserts.partialEvaluationConstant(canTakeDirectSuccessor);
                        if (canTakeDirectSuccessor && injectBranchProbability(CONTINUE_PROBABILITY, indexOfResult >= 0)) {
                            int successor = (DFAState.getLoopTransitionIndex(dfaRecords, stateRef) + 1) & 1;
                            CompilerAsserts.partialEvaluationConstant(successor);
                            inputIncNextIndexRaw(locals, inputGetCodePointSize(locals, codeRange));
                            ip = DFAState.getSuccessor(dfaRecords, stateRef, successor);
                            continue outer;
                        }
                    }
                    if (injectBranchProbability(EXIT_PROBABILITY, !inputHasNext(locals))) {
                        boolean inputAtEnd = inputAtEnd(locals);
                        atEnd(kind, stateRef, locals, inputAtEnd);
                        int anchoredFinalSuccessor = getAnchoredFinalSuccessor(kind, stateRef);
                        CompilerAsserts.partialEvaluationConstant(anchoredFinalSuccessor);
                        if (DFAState.isAnchoredFinalState(dfaRecords, stateRef) && anchoredFinalSuccessor >= 0 && inputAtEnd) {
                            ip = anchoredFinalSuccessor;
                            continue outer;
                        }
                        if (isBackward() && DFAState.hasBackwardPrefixState(dfaRecords, stateRef) && locals.getIndex() > locals.getRegionFrom()) {
                            assert locals.getIndex() == locals.getFromIndex();
                            /*
                             * We have reached the starting index of the forward matcher, so we have
                             * to switch to backward-prefix-states. These states will match
                             * look-behind assertions only.
                             */
                            locals.setCurMinIndex(locals.getRegionFrom());
                            int i = successorCount - 1;
                            ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                            continue outer;
                        }
                        break;
                    }
                    int matcherRecordRef = DFAState.getMatcherRef(dfaRecords, stateRef);
                    CompilerAsserts.partialEvaluationConstant(matcherRecordRef);
                    boolean treeTransitionMatching = matcherRecordRef < 0;
                    CompilerAsserts.partialEvaluationConstant(treeTransitionMatching);
                    if (treeTransitionMatching) {
                        int c = inputReadAndDecode(locals, codeRange);
                        AllTransitionsInOneTreeMatcher treeMatcher = treeMatchers[~matcherRecordRef];
                        CompilerAsserts.partialEvaluationConstant(treeMatcher);
                        int treeSuccessor = treeMatcher.checkMatchTree(c);
                        // TODO: this switch loop should be replaced with a PE intrinsic
                        for (int i = 0; i < successorCount; i++) {
                            if (i == treeSuccessor) {
                                ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                // Guards matching is not supported (and therefore not used) with
                                // treeTransitionMatchers so no guard checks are needed here
                                assert kind != DFAExecutorRecords.Kind.bqState;
                                continue outer;
                            }
                        }
                        break;
                    }
                    CompilerAsserts.partialEvaluationConstant(sequentialMatcherRecords);
                    int transitionCount = SequentialMatchers.getTransitionCount(sequentialMatcherRecords, matcherRecordRef);
                    CompilerAsserts.partialEvaluationConstant(transitionCount);
                    boolean isSimpleMatchers = getEncoding() == Encoding.LATIN_1 || getEncoding() == Encoding.BYTES || getEncoding() == Encoding.ASCII;
                    boolean isUTF8 = isUTF8();
                    CompilerAsserts.partialEvaluationConstant(isSimpleMatchers);
                    CompilerAsserts.partialEvaluationConstant(isUTF8);
                    if (isSimpleMatchers) {
                        assert SequentialMatchers.getMaxBytes(sequentialMatcherRecords, matcherRecordRef) <= 1;
                        final int c = inputReadAndDecode(locals, codeRange);
                        int matcherRefs = SequentialMatchers.getLaneRef(sequentialMatcherRecords, matcherRecordRef, 0);
                        CompilerAsserts.partialEvaluationConstant(matcherRefs);
                        if (matcherRefs != SequentialMatchers.NO_LANE) {
                            for (int i = 0; i < transitionCount; i++) {
                                if (match(encodedMatchers, sequentialMatcherRecords, matcherRefs, i, c)) {
                                    ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                    continue outer;
                                }
                            }
                        }
                    } else if (isUTF8) {
                        /*
                         * UTF-8 on-the fly decoding
                         */
                        final int asciiRefs = SequentialMatchers.getLaneRef(sequentialMatcherRecords, matcherRecordRef, 0);
                        final int enc2Refs = SequentialMatchers.getLaneRef(sequentialMatcherRecords, matcherRecordRef, 1);
                        final int enc3Refs = SequentialMatchers.getLaneRef(sequentialMatcherRecords, matcherRecordRef, 2);
                        final int enc4Refs = SequentialMatchers.getLaneRef(sequentialMatcherRecords, matcherRecordRef, 3);
                        final int maxBytes = SequentialMatchers.getMaxBytes(sequentialMatcherRecords, matcherRecordRef);
                        CompilerAsserts.partialEvaluationConstant(asciiRefs);
                        CompilerAsserts.partialEvaluationConstant(enc2Refs);
                        CompilerAsserts.partialEvaluationConstant(enc3Refs);
                        CompilerAsserts.partialEvaluationConstant(enc4Refs);
                        CompilerAsserts.partialEvaluationConstant(maxBytes);
                        int c = inputReadRaw(locals);
                        boolean isAscii = codeRange == TruffleString.CodeRange.ASCII;
                        CompilerAsserts.partialEvaluationConstant(isAscii);
                        if (isAscii || injectBranchProbability(LATIN1_PROBABILITY, c < 128)) {
                            inputIncNextIndexRaw(locals);
                            if (asciiRefs != SequentialMatchers.NO_LANE) {
                                for (int i = 0; i < transitionCount; i++) {
                                    if (match(encodedMatchers, sequentialMatcherRecords, asciiRefs, i, c)) {
                                        ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                        continue outer;
                                    }
                                }
                            }
                        } else {
                            getBMPProfile().enter();
                            int codepoint = 0;
                            if (isBackward()) {
                                codepoint = c & 0x3f;
                                assert c >> 6 == 2;
                                for (int i = 1; i < 4; i++) {
                                    c = inputReadRaw(locals, locals.getIndex() - i);
                                    if (i < 3 && c >> 6 == 2) {
                                        codepoint |= (c & 0x3f) << (6 * i);
                                    } else {
                                        break;
                                    }
                                }
                            }
                            int nBytes = Integer.numberOfLeadingZeros(~(c << 24));
                            assert 1 < nBytes && nBytes < 5 : nBytes;
                            inputIncNextIndexRaw(locals, nBytes);
                            if (maxBytes > 1 && nBytes <= maxBytes) {
                                if (isBackward()) {
                                    codepoint |= (c & (0xff >>> nBytes)) << (6 * (nBytes - 1));
                                }
                                if (isForward()) {
                                    int index = locals.getIndex();
                                    codepoint = (c & (0xff >>> nBytes)) << 6 | (inputReadRaw(locals, ++index) & 0x3f);
                                    if (maxBytes > 2 && nBytes > 2) {
                                        codepoint = codepoint << 6 | (inputReadRaw(locals, ++index) & 0x3f);
                                    }
                                    if (maxBytes > 3 && nBytes > 3) {
                                        getAstralProfile().enter();
                                        codepoint = codepoint << 6 | (inputReadRaw(locals, ++index) & 0x3f);
                                    }
                                }
                                switch (nBytes - 2) {
                                    case 0:
                                        if (enc2Refs != SequentialMatchers.NO_LANE) {
                                            for (int i = 0; i < transitionCount; i++) {
                                                if (match(encodedMatchers, sequentialMatcherRecords, enc2Refs, i, codepoint)) {
                                                    ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                                    continue outer;
                                                }
                                            }
                                        }
                                        break;
                                    case 1:
                                        if (enc3Refs != SequentialMatchers.NO_LANE) {
                                            for (int i = 0; i < transitionCount; i++) {
                                                if (match(encodedMatchers, sequentialMatcherRecords, enc3Refs, i, codepoint)) {
                                                    ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                                    continue outer;
                                                }
                                            }
                                        }
                                        break;
                                    case 2:
                                        if (enc4Refs != SequentialMatchers.NO_LANE) {
                                            getAstralProfile().enter();
                                            for (int i = 0; i < transitionCount; i++) {
                                                if (match(encodedMatchers, sequentialMatcherRecords, enc4Refs, i, codepoint)) {
                                                    ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                                    continue outer;
                                                }
                                            }
                                        }
                                        break;
                                }
                            }
                        }
                    } else {
                        boolean codeRangeIsSubsetOfLatin1 = codeRange.isSubsetOf(TruffleString.CodeRange.LATIN_1);
                        CompilerAsserts.partialEvaluationConstant(codeRangeIsSubsetOfLatin1);
                        boolean isUTF16Raw = getEncoding() == Encoding.UTF_16_RAW;
                        CompilerAsserts.partialEvaluationConstant(isUTF16Raw);
                        if (isUTF16Raw) {
                            /*
                             * UTF-16 interpreted as raw 16-bit values, no decoding
                             */
                            final int c = inputReadAndDecode(locals, codeRange);
                            int asciiRefs = SequentialMatchers.getLaneRef(sequentialMatcherRecords, matcherRecordRef, 0);
                            int latin1Refs = SequentialMatchers.getLaneRef(sequentialMatcherRecords, matcherRecordRef, 1);
                            int bmpRefs = SequentialMatchers.getLaneRef(sequentialMatcherRecords, matcherRecordRef, 2);
                            CompilerAsserts.partialEvaluationConstant(asciiRefs);
                            CompilerAsserts.partialEvaluationConstant(latin1Refs);
                            CompilerAsserts.partialEvaluationConstant(bmpRefs);
                            boolean hasLatin1 = latin1Refs != SequentialMatchers.NO_LANE;
                            boolean doLatin1 = bmpRefs == SequentialMatchers.NO_LANE || codeRangeIsSubsetOfLatin1;
                            CompilerAsserts.partialEvaluationConstant(hasLatin1);
                            CompilerAsserts.partialEvaluationConstant(doLatin1);
                            if (hasLatin1 && (doLatin1 || injectBranchProbability(LATIN1_PROBABILITY, c < 256))) {
                                int byteMatcherRefs = asciiOrLatin1Refs(codeRange, asciiRefs, latin1Refs);
                                for (int i = 0; i < transitionCount; i++) {
                                    if (match(encodedMatchers, sequentialMatcherRecords, byteMatcherRefs, i, c)) {
                                        ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                        continue outer;
                                    }
                                }
                            } else if (bmpRefs != SequentialMatchers.NO_LANE) {
                                getBMPProfile().enter();
                                for (int i = 0; i < transitionCount; i++) {
                                    if (match(encodedMatchers, sequentialMatcherRecords, bmpRefs, i, c)) {
                                        ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                        continue outer;
                                    }
                                }
                            }
                        } else {
                            /*
                             * UTF-32 or UTF-16 on-the fly decoding
                             */
                            assert isUTF16() || isUTF32();
                            int asciiRefs = SequentialMatchers.getLaneRef(sequentialMatcherRecords, matcherRecordRef, 0);
                            int latin1Refs = SequentialMatchers.getLaneRef(sequentialMatcherRecords, matcherRecordRef, 1);
                            int bmpRefs = SequentialMatchers.getLaneRef(sequentialMatcherRecords, matcherRecordRef, 2);
                            int astralRefs = SequentialMatchers.getLaneRef(sequentialMatcherRecords, matcherRecordRef, 3);
                            CompilerAsserts.partialEvaluationConstant(asciiRefs);
                            CompilerAsserts.partialEvaluationConstant(latin1Refs);
                            CompilerAsserts.partialEvaluationConstant(bmpRefs);
                            CompilerAsserts.partialEvaluationConstant(astralRefs);

                            int c = inputReadRaw(locals);
                            inputIncNextIndexRaw(locals);

                            boolean isCodeRangeValid = codeRange == TruffleString.CodeRange.VALID;
                            CompilerAsserts.partialEvaluationConstant(isCodeRangeValid);
                            if (isUTF16()) {
                                boolean mustDecode = codeRange.isSupersetOf(TruffleString.CodeRange.VALID) && DFAState.utf16MustDecode(dfaRecords, stateRef);
                                CompilerAsserts.partialEvaluationConstant(mustDecode);
                                if (mustDecode && injectBranchProbability(ASTRAL_PROBABILITY, inputUTF16IsHighSurrogate(c)) &&
                                                (isCodeRangeValid || injectBranchProbability(LIKELY_PROBABILITY, inputHasNext(locals, locals.getNextIndex())))) {
                                    getAstralProfile().enter();
                                    int c2 = inputReadRaw(locals, locals.getNextIndex());
                                    if (isCodeRangeValid || injectBranchProbability(LIKELY_PROBABILITY, inputUTF16IsLowSurrogate(c2))) {
                                        assert inputUTF16IsLowSurrogate(c2);
                                        locals.setNextIndex(inputIncRaw(locals.getNextIndex()));
                                        if (astralRefs != SequentialMatchers.NO_LANE) {
                                            c = inputUTF16ToCodePoint(c, c2);
                                        }
                                    }
                                    if (astralRefs != SequentialMatchers.NO_LANE) {
                                        for (int i = 0; i < transitionCount; i++) {
                                            if (match(encodedMatchers, sequentialMatcherRecords, astralRefs, i, c)) {
                                                ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                                continue outer;
                                            }
                                        }
                                    }
                                } else if (latin1Refs != SequentialMatchers.NO_LANE &&
                                                (bmpRefs == SequentialMatchers.NO_LANE || codeRangeIsSubsetOfLatin1 || injectBranchProbability(LATIN1_PROBABILITY, c < 256))) {
                                    int byteMatcherRefs = asciiOrLatin1Refs(codeRange, asciiRefs, latin1Refs);
                                    for (int i = 0; i < transitionCount; i++) {
                                        if (match(encodedMatchers, sequentialMatcherRecords, byteMatcherRefs, i, c)) {
                                            ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                            continue outer;
                                        }
                                    }
                                } else if (bmpRefs != SequentialMatchers.NO_LANE && codeRange.isSupersetOf(TruffleString.CodeRange.BMP)) {
                                    getBMPProfile().enter();
                                    for (int i = 0; i < transitionCount; i++) {
                                        if (match(encodedMatchers, sequentialMatcherRecords, bmpRefs, i, c)) {
                                            ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                            continue outer;
                                        }
                                    }
                                }
                            } else {
                                assert isUTF32();
                                if (latin1Refs != SequentialMatchers.NO_LANE && (codeRangeIsSubsetOfLatin1 || injectBranchProbability(LATIN1_PROBABILITY, c < 256))) {
                                    int byteMatcherRefs = asciiOrLatin1Refs(codeRange, asciiRefs, latin1Refs);
                                    for (int i = 0; i < transitionCount; i++) {
                                        if (match(encodedMatchers, sequentialMatcherRecords, byteMatcherRefs, i, c)) {
                                            ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                            continue outer;
                                        }
                                    }
                                } else if (bmpRefs != SequentialMatchers.NO_LANE && (codeRange == TruffleString.CodeRange.BMP || (injectBranchProbability(BMP_PROBABILITY, c <= 0xffff) &&
                                                (isCodeRangeValid || injectBranchProbability(BMP_PROBABILITY, !Character.isSurrogate((char) c)))))) {
                                    getBMPProfile().enter();
                                    for (int i = 0; i < transitionCount; i++) {
                                        if (match(encodedMatchers, sequentialMatcherRecords, bmpRefs, i, c)) {
                                            ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                            continue outer;
                                        }
                                    }
                                } else if (astralRefs != SequentialMatchers.NO_LANE && codeRange.isSupersetOf(TruffleString.CodeRange.VALID)) {
                                    getAstralProfile().enter();
                                    for (int i = 0; i < transitionCount; i++) {
                                        if (match(encodedMatchers, sequentialMatcherRecords, astralRefs, i, c)) {
                                            ip = DFAState.getSuccessor(dfaRecords, stateRef, i);
                                            continue outer;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    /*
                     * no matching transition found, go to the default transition
                     */
                    ip = transitionNoMatch(stateRef);
                    continue outer;
                } else {
                    assert kind == DFAExecutorRecords.Kind.innerLiteralState;
                    assert isForward();
                    if (isGenericCG()) {
                        locals.setLastIndex();
                    }
                    inputAdvance(locals);
                    while (true) {
                        if (injectBranchProbability(EXIT_PROBABILITY, !inputHasNext(locals))) {
                            break outer;
                        }
                        locals.setIndex(DFAFindInnerLiteralState.executeInnerLiteralSearch(innerLiteral, locals, this));
                        if (injectBranchProbability(EXIT_PROBABILITY, locals.getIndex() < 0)) {
                            break outer;
                        }
                        if (innerLiteralPrefixMatcher == null ||
                                        injectBranchProbability(CONTINUE_PROBABILITY, prefixMatcherMatches(frame, innerLiteralPrefixMatcher, locals, codeRange, canFindStart()))) {
                            if (innerLiteralPrefixMatcher == null) {
                                if (isSimpleCG()) {
                                    DFACaptureGroupTrackingData cgData = locals.getCGData();
                                    DFACaptureGroupTrackingData.writeRowElement(cgData.results, 0, cgData.results.length, 0, locals.getIndex());
                                } else if (canFindStart()) {
                                    locals.setMatchStart(locals.getIndex());
                                }
                            }
                            inputIncRaw(locals, innerLiteral.getEncodedLength());
                            locals.setNextIndex(locals.getIndex());
                            ip = DFAFindInnerLiteralState.getSuccessor(dfaRecords, stateRef);
                            continue outer;
                        }
                        inputIncRaw(locals);
                    }
                }
            }
        }
        if (recordExecution()) {
            debugRecorder.finishRecording();
        }
        if (isSimpleCG()) {
            int[] result = isSimpleCGMustCopy() ? locals.getCGData().currentResult : locals.getCGData().results;
            return locals.getResultInt() == NO_MATCH ? null : result;
        }
        if (isGenericCG()) {
            return locals.getResultInt() == 0 ? locals.getCGData().currentResult : null;
        }
        if (isForward() && canFindStart()) {
            return locals.getResultInt() | ((long) locals.getMatchStart() << 32);
        }
        return (long) locals.getResultInt();
    }

    private static boolean prefixMatcherMatches(VirtualFrame frame, TRegexExecutorBaseNode prefixMatcher, TRegexDFAExecutorLocals locals, TruffleString.CodeRange codeRange, boolean canFindStart) {
        Object result = prefixMatcher.execute(frame, locals.toInnerLiteralBackwardLocals(), codeRange);
        if (prefixMatcher.isSimpleCG()) {
            return result != null;
        }
        if (canFindStart) {
            locals.setMatchStart((int) ((long) result));
        }
        return (int) ((long) result) != NO_MATCH;
    }

    private static int asciiOrLatin1Refs(TruffleString.CodeRange codeRange, int asciiRefs, int latin1Refs) {
        return codeRange == TruffleString.CodeRange.ASCII && asciiRefs != SequentialMatchers.NO_LANE ? asciiRefs : latin1Refs;
    }

    @CompilerDirectives.EarlyInline
    private int initialStateSuccessor(TRegexDFAExecutorLocals locals, int i) {
        if (isGenericCG()) {
            locals.setLastIndex();
            int lastTransition = DFAInitialState.getLastTransition(dfaRecords, i);
            if (lastTransition >= 0) {
                locals.setLastTransition((short) lastTransition);
            }
        }
        return DFAInitialState.getSuccessor(dfaRecords, i);
    }

    private void initNextIndex(TRegexDFAExecutorLocals locals) {
        locals.setNextIndex(locals.getIndex());
        if (recordExecution()) {
            getDebugRecorder().setInitialIndex(locals.getIndex());
        }
    }

    private static boolean match(int[] encodedMatchers, int[] matcherRecords, int matcherRefs, int i, final int c) {
        CompilerAsserts.partialEvaluationConstant(matcherRecords);
        CompilerAsserts.partialEvaluationConstant(matcherRefs);
        CompilerAsserts.partialEvaluationConstant(i);
        int matcherRef = matcherRecords[matcherRefs + i];
        CompilerAsserts.partialEvaluationConstant(matcherRef);
        return matcherRef != SequentialMatchers.NO_MATCHER && CharMatchers.match(encodedMatchers, matcherRef, c);
    }

    /**
     * Returns a new instruction pointer value that denotes the
     * {@link SequentialMatchers#getNoMatchSuccessor(int[], int) no-match successor} of
     * {@code state}.
     */
    @CompilerDirectives.EarlyInline
    private int transitionNoMatch(int stateRef) {
        CompilerAsserts.partialEvaluationConstant(stateRef);
        int matcherRecordRef = DFAState.getMatcherRef(dfaRecords, stateRef);
        CompilerAsserts.partialEvaluationConstant(matcherRecordRef);
        short noMatchSuccessor = SequentialMatchers.getNoMatchSuccessor(sequentialMatcherRecords, matcherRecordRef);
        if (noMatchSuccessor < 0) {
            return noMatchSuccessor;
        }
        return DFAState.getSuccessor(dfaRecords, stateRef, noMatchSuccessor);
    }

    @CompilerDirectives.EarlyInline
    private int getAnchoredFinalSuccessor(DFAExecutorRecords.Kind kind, int stateRef) {
        return switch (kind) {
            case simpleCGState -> DFASimpleCGTrackingState.getAnchoredFinalSuccessor(dfaRecords, stateRef);
            case genericCGState -> CGTrackingDFAState.getAnchoredFinalSuccessor(dfaRecords, stateRef);
            default -> DFAExecutorRecords.NO_REF;
        };
    }

    @CompilerDirectives.EarlyInline
    private int applyTransitionRecord(DFAExecutorRecords.Kind kind, int transitionRef, TRegexDFAExecutorLocals locals) {
        CompilerAsserts.partialEvaluationConstant(kind);
        CompilerAsserts.partialEvaluationConstant(transitionRef);
        return switch (kind) {
            case genericCGTransition -> CGTrackingTransition.apply(dfaRecords, transitionRef, locals, this);
            case genericCGPreFinalTransition -> CGTrackingPreFinalTransition.apply(dfaRecords, transitionRef, locals, this);
            case genericCGAnchoredFinalTransition -> CGTrackingAnchoredFinalTransition.apply(dfaRecords, transitionRef, locals, this);
            case simpleCGTransition -> DFASimpleCGTransition.apply(dfaRecords, transitionRef, locals, this);
            case bqTransitionOps -> DFABQTrackingTransitionOps.apply(dfaRecords, bqRecords, transitionRef, locals, counterTrackers);
            case bqTransitionConstraints -> DFABQTrackingTransitionConstraints.apply(dfaRecords, bqRecords, transitionRef, locals, counterTrackers);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    private boolean beforeFindSuccessor(DFAExecutorRecords.Kind kind, int stateRef, TRegexDFAExecutorLocals locals) {
        CompilerAsserts.partialEvaluationConstant(kind);
        CompilerAsserts.partialEvaluationConstant(stateRef);
        return switch (kind) {
            case state -> DFAState.beforeFindSuccessor(dfaRecords, stateRef, locals);
            case traceFinderState -> TraceFinderDFAState.checkFinalState(dfaRecords, stateRef, locals);
            case simpleCGState -> DFASimpleCGTrackingState.checkFinalState(dfaRecords, stateRef, locals, this);
            case genericCGState -> CGTrackingDFAState.beforeFindSuccessor(dfaRecords, stateRef, locals, this);
            case bqState -> DFABQTrackingState.checkFinalState(dfaRecords, bqRecords, stateRef, locals, counterTrackers);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    private void afterIndexOf(DFAExecutorRecords.Kind kind, int stateRef, TRegexDFAExecutorLocals locals, int preLoopIndex, int postLoopIndex, TruffleString.CodeRange codeRange) {
        CompilerAsserts.partialEvaluationConstant(kind);
        CompilerAsserts.partialEvaluationConstant(stateRef);
        switch (kind) {
            case state -> DFAState.afterIndexOf(dfaRecords, stateRef, locals, postLoopIndex);
            case traceFinderState -> TraceFinderDFAState.afterIndexOf(dfaRecords, stateRef, locals, postLoopIndex);
            case simpleCGState -> DFASimpleCGTrackingState.afterIndexOf(dfaRecords, stateRef, locals, this, preLoopIndex, postLoopIndex, codeRange);
            case genericCGState -> CGTrackingDFAState.afterIndexOf(dfaRecords, stateRef, locals, this, preLoopIndex, postLoopIndex, codeRange);
            case bqState -> DFABQTrackingState.afterIndexOf(dfaRecords, bqRecords, stateRef, locals, postLoopIndex, counterTrackers);
            default -> throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private void atEnd(DFAExecutorRecords.Kind kind, int stateRef, TRegexDFAExecutorLocals locals, boolean inputAtEnd) {
        CompilerAsserts.partialEvaluationConstant(kind);
        CompilerAsserts.partialEvaluationConstant(stateRef);
        switch (kind) {
            case state -> DFAState.atEnd(dfaRecords, stateRef, locals, inputAtEnd);
            case traceFinderState -> TraceFinderDFAState.atEnd(dfaRecords, stateRef, locals, inputAtEnd);
            case simpleCGState -> DFASimpleCGTrackingState.atEnd(dfaRecords, stateRef, locals, this, inputAtEnd);
            case genericCGState -> CGTrackingDFAState.atEnd(dfaRecords, stateRef, locals, this, inputAtEnd);
            case bqState -> DFABQTrackingState.atEnd(dfaRecords, bqRecords, stateRef, locals, inputAtEnd, counterTrackers);
            default -> throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Override
    public int getMinIndex(TRegexExecutorLocals locals) {
        return isForward() ? super.getMinIndex(locals) : ((TRegexDFAExecutorLocals) locals).getCurMinIndex();
    }

    private static boolean validArgs(TRegexDFAExecutorLocals locals) {
        final int initialIndex = locals.getIndex();
        final int fromIndex = locals.getFromIndex();
        final int maxIndex = locals.getMaxIndex();
        final int regionFrom = locals.getRegionFrom();
        final int regionTo = locals.getRegionTo();
        return 0 <= regionFrom && regionFrom <= fromIndex && fromIndex <= maxIndex && maxIndex <= regionTo &&
                        regionFrom <= initialIndex && initialIndex <= regionTo;
    }

    private static int[] initResultOrder(int maxNumberOfNFAStates, int numberOfCaptureGroups, boolean trackLastGroup) {
        int[] resultOrder = new int[maxNumberOfNFAStates];
        for (int i = 0; i < maxNumberOfNFAStates; i++) {
            resultOrder[i] = i * (numberOfCaptureGroups * 2 + (trackLastGroup ? 1 : 0));
        }
        return resultOrder;
    }

    private static int createFlags(TRegexDFAExecutorProperties props) {
        int ret = 0;
        ret |= props.isForward() ? FLAG_FORWARD : 0;
        ret |= props.isSearching() ? FLAG_SEARCHING : 0;
        ret |= props.isGenericCG() ? FLAG_GENERIC_CG : 0;
        ret |= props.isAllowSimpleCG() ? FLAG_ALLOW_SIMPLE_CG : 0;
        ret |= props.isSimpleCG() ? FLAG_SIMPLE_CG : 0;
        ret |= props.isSimpleCGMustCopy() ? FLAG_SIMPLE_CG_MUST_COPY : 0;
        ret |= props.canFindStart() ? FLAG_CAN_FIND_START : 0;
        ret |= props.tracksLastGroup() ? FLAG_TRACK_LAST_GROUP : 0;
        return ret;
    }

    public int getMaxNumberOfNFAStates() {
        return maxNumberOfNFAStates;
    }

    public int getCGTrackingCost() {
        int cost = 0;
        for (int ref = 0; ref < dfaRecords.length;) {
            DFAExecutorRecords.Kind kind = DFAExecutorRecords.getKind(dfaRecords, ref);
            if (kind == DFAExecutorRecords.Kind.genericCGState) {
                cost += CGTrackingDFAState.getCGTrackingCost(dfaRecords, ref, this);
            } else if (kind == DFAExecutorRecords.Kind.genericCGTransition) {
                cost += CGTrackingTransition.getCGTrackingCost(dfaRecords, ref, this);
            } else if (kind == DFAExecutorRecords.Kind.genericCGPreFinalTransition) {
                cost += CGTrackingPreFinalTransition.getCGTrackingCost(dfaRecords, ref, this);
            } else if (kind == DFAExecutorRecords.Kind.genericCGAnchoredFinalTransition) {
                cost += CGTrackingAnchoredFinalTransition.getCGTrackingCost(dfaRecords, ref, this);
            }
            ref += DFAExecutorRecords.getRecordSize(dfaRecords, ref, kind);
        }
        return cost;
    }
}
