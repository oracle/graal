/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorBaseNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.SequentialMatchers.SimpleSequentialMatchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.SequentialMatchers.UTF16Or32SequentialMatchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.SequentialMatchers.UTF16RawSequentialMatchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.SequentialMatchers.UTF8SequentialMatchers;
import com.oracle.truffle.regex.tregex.nodes.input.InputOps;

public final class TRegexDFAExecutorNode extends TRegexExecutorNode {

    private static final int IP_TRANSITION_MARKER = 0x8000;
    public static final int NO_MATCH = -2;
    private final TRegexDFAExecutorProperties props;
    private final int maxNumberOfNFAStates;
    @CompilationFinal(dimensions = 1) private final TruffleString.CodePointSet[] indexOfParameters;
    @CompilationFinal(dimensions = 1) private final DFAAbstractStateNode[] states;
    @CompilationFinal(dimensions = 1) private final int[] cgResultOrder;
    private final TRegexDFAExecutorDebugRecorder debugRecorder;

    @Children private TruffleString.ByteIndexOfCodePointSetNode[] indexOfNodes;
    @Child private TruffleString.ByteIndexOfStringNode indexOfStringNode;
    /** A TRegexDFAExecutorNode, or TRegexExecutorBaseNodeWrapper when instrumented. */
    @Child private TRegexExecutorBaseNode innerLiteralPrefixMatcher;

    public TRegexDFAExecutorNode(
                    RegexSource source,
                    TRegexDFAExecutorProperties props,
                    int numberOfCaptureGroups,
                    int maxNumberOfNFAStates,
                    TruffleString.CodePointSet[] indexOfParameters,
                    DFAAbstractStateNode[] states,
                    TRegexDFAExecutorDebugRecorder debugRecorder,
                    TRegexDFAExecutorNode innerLiteralPrefixMatcher) {
        this(source, props, numberOfCaptureGroups, calcNumberOfTransitions(source, states), maxNumberOfNFAStates, indexOfParameters, states,
                        props.isGenericCG() && maxNumberOfNFAStates > 1 ? initResultOrder(maxNumberOfNFAStates, numberOfCaptureGroups, props) : null, debugRecorder,
                        innerLiteralPrefixMatcher);
    }

    public TRegexDFAExecutorNode(
                    RegexSource source,
                    TRegexDFAExecutorProperties props,
                    int numberOfCaptureGroups,
                    int numberOfTransitions,
                    int maxNumberOfNFAStates,
                    TruffleString.CodePointSet[] indexOfParameters,
                    DFAAbstractStateNode[] states,
                    int[] cgResultOrder,
                    TRegexDFAExecutorDebugRecorder debugRecorder,
                    TRegexDFAExecutorNode innerLiteralPrefixMatcher) {
        super(source, numberOfCaptureGroups, numberOfTransitions);
        this.props = props;
        this.maxNumberOfNFAStates = maxNumberOfNFAStates;
        this.indexOfParameters = indexOfParameters;
        this.states = states;
        this.cgResultOrder = cgResultOrder;
        this.debugRecorder = debugRecorder;
        this.innerLiteralPrefixMatcher = innerLiteralPrefixMatcher;
    }

    private TRegexDFAExecutorNode(TRegexDFAExecutorNode copy, TRegexDFAExecutorNode innerLiteralPrefixMatcher) {
        this(copy.getSource(), copy.props, copy.getNumberOfCaptureGroups(), copy.getNumberOfTransitions(), copy.maxNumberOfNFAStates, copy.indexOfParameters, copy.states, copy.cgResultOrder,
                        copy.debugRecorder,
                        innerLiteralPrefixMatcher);
    }

    @Override
    public TRegexDFAExecutorNode shallowCopy() {
        return new TRegexDFAExecutorNode(this, innerLiteralPrefixMatcher == null ? null : (TRegexDFAExecutorNode) innerLiteralPrefixMatcher.shallowCopy());
    }

    private DFAInitialStateNode getInitialState() {
        return (DFAInitialStateNode) states[0];
    }

    public int getPrefixLength() {
        return getInitialState().getPrefixLength();
    }

    public boolean isAnchored() {
        return !getInitialState().hasUnAnchoredEntry();
    }

    @Override
    public String getName() {
        return "dfa";
    }

    @Override
    public boolean isForward() {
        return props.isForward();
    }

    @Override
    public boolean isTrivial() {
        return getNumberOfTransitions() < (isGenericCG() ? (TRegexOptions.TRegexMaxTransitionsInTrivialExecutor * 3) / 4 : TRegexOptions.TRegexMaxTransitionsInTrivialExecutor);
    }

    public boolean isBackward() {
        return !props.isForward();
    }

    public boolean isSearching() {
        return props.isSearching();
    }

    @Override
    public boolean isSimpleCG() {
        return props.isSimpleCG();
    }

    public boolean isGenericCG() {
        return props.isGenericCG();
    }

    public boolean canFindStart() {
        return props.canFindStart();
    }

    @Override
    public int getNumberOfStates() {
        return states.length;
    }

    private static int calcNumberOfTransitions(RegexSource source, DFAAbstractStateNode[] states) {
        int sum = 0;
        for (DFAAbstractStateNode state : states) {
            sum += state.getSuccessors().length;
            if (state instanceof DFAStateNode && !((DFAStateNode) state).treeTransitionMatching() &&
                            ((DFAStateNode) state).getSequentialMatchers().getNoMatchSuccessor() >= 0) {
                sum++;
            }
        }
        if (sum > source.getOptions().getMaxDFASize()) {
            throw new UnsupportedRegexException("too many transitions");
        }
        return sum;
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

    @Override
    public TRegexExecutorLocals createLocals(TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index) {
        return new TRegexDFAExecutorLocals(input, fromIndex, maxIndex, regionFrom, regionTo, index, createCGData());
    }

    @Override
    public boolean writesCaptureGroups() {
        return isSimpleCG();
    }

    private DFACaptureGroupTrackingData createCGData() {
        if (isSimpleCG()) {
            return new DFACaptureGroupTrackingData(null,
                            createResultsArray(resultLength()),
                            props.isSimpleCGMustCopy() ? new int[resultLength()] : null);
        } else if (isGenericCG()) {
            return new DFACaptureGroupTrackingData(
                            maxNumberOfNFAStates == 1 ? null : Arrays.copyOf(cgResultOrder, cgResultOrder.length),
                            createResultsArray(maxNumberOfNFAStates * resultLength()),
                            new int[resultLength()]);
        } else {
            return null;
        }
    }

    private int resultLength() {
        return getNumberOfCaptureGroups() * 2 + (props.tracksLastGroup() ? 1 : 0);
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
        CompilerAsserts.partialEvaluationConstant(states);
        CompilerAsserts.partialEvaluationConstant(states.length);
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
        if (injectBranchProbability(UNLIKELY_PROBABILITY, props.getMinResultLength() > 0 &&
                        (isForward() ? locals.getMaxIndex() - locals.getIndex()
                                        : locals.getIndex() - Math.max(locals.getRegionFrom(), locals.getFromIndex() - getPrefixLength())) < props.getMinResultLength())) {
            // no match possible, break immediately
            return isGenericCG() || isSimpleCG() ? null : (long) TRegexDFAExecutorNode.NO_MATCH;
        }
        if (recordExecution()) {
            debugRecorder.startRecording(locals);
        }
        if (isBackward()) {
            locals.setCurMinIndex(locals.getFromIndex());
        }
        int ip = 0;
        outer: while (true) {
            if (CompilerDirectives.inInterpreter()) {
                RegexRootNode.checkThreadInterrupted();
            }
            CompilerAsserts.partialEvaluationConstant(ip);
            if (ip < 0) {
                break;
            }
            final DFAAbstractStateNode curState = states[ip & 0x7fff];
            CompilerAsserts.partialEvaluationConstant(curState);
            final short[] successors = curState.getSuccessors();
            CompilerAsserts.partialEvaluationConstant(successors);
            CompilerAsserts.partialEvaluationConstant(successors.length);
            if (curState instanceof DFAInitialStateNode) {
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
                            if (props.canFindStart()) {
                                locals.setMatchStart(locals.getIndex());
                            }
                            initNextIndex(locals);
                            ip = initialStateSuccessor(locals, curState, successors, i);
                            continue outer;
                        }
                    }
                    if (props.canFindStart()) {
                        locals.setMatchStart(locals.getIndex());
                    }
                    initNextIndex(locals);
                    atBegin = inputAtBegin(locals);
                } else {
                    /*
                     * We are in non-searching mode - if we start behind fromIndex, select
                     * successors[n], where n is the number of code points between the current index
                     * and fromIndex.
                     */
                    initNextIndex(locals);
                    atBegin = inputAtBegin(locals);
                    for (int i = 0; i < getPrefixLength(); i++) {
                        assert isForward();
                        if (locals.getIndex() < locals.getFromIndex()) {
                            inputSkipIntl(locals, true, codeRange);
                        } else {
                            if (injectBranchProbability(LIKELY_PROBABILITY, atBegin)) {
                                ip = initialStateSuccessor(locals, curState, successors, i);
                                continue outer;
                            } else {
                                ip = initialStateSuccessor(locals, curState, successors, i + (successors.length / 2));
                                continue outer;
                            }
                        }
                    }
                }
                if (injectBranchProbability(LIKELY_PROBABILITY, atBegin)) {
                    ip = initialStateSuccessor(locals, curState, successors, getPrefixLength());
                    continue outer;
                } else {
                    ip = initialStateSuccessor(locals, curState, successors, getPrefixLength() + (successors.length / 2));
                    continue outer;
                }
            } else if (curState instanceof DFAStateNode) {
                DFAStateNode state = (DFAStateNode) curState;
                if (ip > IP_TRANSITION_MARKER) {
                    /*
                     * execute DFA state transition
                     */
                    int i = ip >> 16;
                    ip = execTransition(locals, state, i);
                    continue outer;
                } else {
                    if (CompilerDirectives.hasNextTier()) {
                        locals.incLoopCount(this);
                    }
                    /*
                     * find matching DFA state transition
                     */
                    inputAdvance(locals);
                    state.beforeFindSuccessor(locals, this);
                    boolean canDoIndexOf = isForward() && state.canDoIndexOf(codeRange);
                    CompilerAsserts.partialEvaluationConstant(canDoIndexOf);
                    if (canDoIndexOf && injectBranchProbability(CONTINUE_PROBABILITY, inputHasNext(locals))) {
                        int indexOfNodeId = state.getIndexOfNodeId();
                        TruffleString.ByteIndexOfCodePointSetNode indexOfNode = getIndexOfNode(indexOfNodeId);
                        TruffleString.CodePointSet indexOfParameter = indexOfParameters[indexOfNodeId];
                        CompilerAsserts.partialEvaluationConstant(indexOfNodeId);
                        CompilerAsserts.partialEvaluationConstant(indexOfNode);
                        CompilerAsserts.partialEvaluationConstant(indexOfParameter);
                        int indexOfResult = InputOps.indexOf(locals.getInput(), locals.getIndex(), getMaxIndex(locals), indexOfParameter, getEncoding(), indexOfNode);
                        int postLoopIndex = indexOfResult < 0 ? getMaxIndex(locals) : indexOfResult;
                        state.afterIndexOf(locals, this, locals.getIndex(), postLoopIndex, codeRange);
                        assert locals.getIndex() == postLoopIndex;
                        boolean twoSuccessors = successors.length == 2;
                        if (twoSuccessors && injectBranchProbability(CONTINUE_PROBABILITY, indexOfResult >= 0)) {
                            int successor = (state.getLoopToSelf() + 1) & 1;
                            CompilerAsserts.partialEvaluationConstant(successor);
                            inputIncNextIndexRaw(locals, inputGetCodePointSize(locals, codeRange));
                            ip = execTransition(locals, state, successor);
                            continue outer;
                        }
                    }
                    if (injectBranchProbability(EXIT_PROBABILITY, !inputHasNext(locals))) {
                        state.atEnd(locals, this);
                        if (isBackward() && state.hasBackwardPrefixState() && locals.getIndex() > locals.getRegionFrom()) {
                            assert locals.getIndex() == locals.getFromIndex();
                            /*
                             * We have reached the starting index of the forward matcher, so we have
                             * to switch to backward-prefix-states. These states will match
                             * look-behind assertions only.
                             */
                            locals.setCurMinIndex(locals.getRegionFrom());
                            ip = transitionMatch(state, ((BackwardDFAStateNode) state).getBackwardPrefixStateIndex());
                            continue outer;
                        }
                        break;
                    }
                    boolean treeTransitionMatching = state.treeTransitionMatching();
                    CompilerAsserts.partialEvaluationConstant(treeTransitionMatching);
                    if (treeTransitionMatching) {
                        int c = inputReadAndDecode(locals, codeRange);
                        int treeSuccessor = state.getTreeMatcher().checkMatchTree(c);
                        // TODO: this switch loop should be replaced with a PE intrinsic
                        for (int i = 0; i < successors.length; i++) {
                            if (i == treeSuccessor) {
                                ip = transitionMatch(state, i);
                                continue outer;
                            }
                        }
                        break;
                    }
                    Matchers matchers = state.getSequentialMatchers();
                    CompilerAsserts.partialEvaluationConstant(matchers);
                    boolean isSimpleMatchers = matchers instanceof SimpleSequentialMatchers;
                    boolean isUTF8 = matchers instanceof UTF8SequentialMatchers;
                    CompilerAsserts.partialEvaluationConstant(isSimpleMatchers);
                    CompilerAsserts.partialEvaluationConstant(isUTF8);
                    if (isSimpleMatchers) {
                        final int c = inputReadAndDecode(locals, codeRange);
                        CharMatcher[] cMatchers = ((SimpleSequentialMatchers) matchers).getMatchers();
                        if (cMatchers != null) {
                            for (int i = 0; i < cMatchers.length; i++) {
                                if (match(cMatchers, i, c)) {
                                    ip = transitionMatch(state, i);
                                    continue outer;
                                }
                            }
                        }
                    } else if (isUTF8) {
                        /*
                         * UTF-8 on-the fly decoding
                         */
                        final UTF8SequentialMatchers utf8Matchers = (UTF8SequentialMatchers) matchers;
                        final CharMatcher[] ascii = utf8Matchers.getAscii();
                        final CharMatcher[] enc2 = utf8Matchers.getEnc2();
                        final CharMatcher[] enc3 = utf8Matchers.getEnc3();
                        final CharMatcher[] enc4 = utf8Matchers.getEnc4();
                        final int maxBytes = utf8Matchers.getMaxBytes();
                        CompilerAsserts.partialEvaluationConstant(maxBytes);
                        int c = inputReadRaw(locals);
                        boolean isAscii = codeRange == TruffleString.CodeRange.ASCII;
                        CompilerAsserts.partialEvaluationConstant(isAscii);
                        if (isAscii || injectBranchProbability(LATIN1_PROBABILITY, c < 128)) {
                            inputIncNextIndexRaw(locals);
                            if (ascii != null) {
                                for (int i = 0; i < ascii.length; i++) {
                                    if (match(ascii, i, c)) {
                                        ip = transitionMatch(state, i);
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
                                        if (enc2 != null) {
                                            for (int i = 0; i < enc2.length; i++) {
                                                if (match(enc2, i, codepoint)) {
                                                    ip = transitionMatch(state, i);
                                                    continue outer;
                                                }
                                            }
                                        }
                                        break;
                                    case 1:
                                        if (enc3 != null) {
                                            for (int i = 0; i < enc3.length; i++) {
                                                if (match(enc3, i, codepoint)) {
                                                    ip = transitionMatch(state, i);
                                                    continue outer;
                                                }
                                            }
                                        }
                                        break;
                                    case 2:
                                        if (enc4 != null) {
                                            getAstralProfile().enter();
                                            for (int i = 0; i < enc4.length; i++) {
                                                if (match(enc4, i, codepoint)) {
                                                    ip = transitionMatch(state, i);
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
                        boolean isUTF16Raw = matchers instanceof UTF16RawSequentialMatchers;
                        CompilerAsserts.partialEvaluationConstant(isUTF16Raw);
                        if (isUTF16Raw) {
                            /*
                             * UTF-16 interpreted as raw 16-bit values, no decoding
                             */
                            final int c = inputReadAndDecode(locals, codeRange);
                            CharMatcher[] ascii = ((UTF16RawSequentialMatchers) matchers).getAscii();
                            CharMatcher[] latin1 = ((UTF16RawSequentialMatchers) matchers).getLatin1();
                            CharMatcher[] bmp = ((UTF16RawSequentialMatchers) matchers).getBmp();
                            boolean hasLatin1 = latin1 != null;
                            boolean doLatin1 = bmp == null || codeRangeIsSubsetOfLatin1;
                            CompilerAsserts.partialEvaluationConstant(hasLatin1);
                            CompilerAsserts.partialEvaluationConstant(doLatin1);
                            if (hasLatin1 && (doLatin1 || injectBranchProbability(LATIN1_PROBABILITY, c < 256))) {
                                CharMatcher[] byteMatchers = asciiOrLatin1Matchers(codeRange, ascii, latin1);
                                for (int i = 0; i < byteMatchers.length; i++) {
                                    if (match(byteMatchers, i, c)) {
                                        ip = transitionMatch(state, i);
                                        continue outer;
                                    }
                                }
                            } else if (bmp != null) {
                                getBMPProfile().enter();
                                for (int i = 0; i < bmp.length; i++) {
                                    if (match(bmp, i, c)) {
                                        ip = transitionMatch(state, i);
                                        continue outer;
                                    }
                                }
                            }
                        } else {
                            /*
                             * UTF-32 or UTF-16 on-the fly decoding
                             */
                            assert matchers instanceof UTF16Or32SequentialMatchers;
                            UTF16Or32SequentialMatchers utf16Or32Matchers = (UTF16Or32SequentialMatchers) matchers;
                            CharMatcher[] ascii = utf16Or32Matchers.getAscii();
                            CharMatcher[] latin1 = utf16Or32Matchers.getLatin1();
                            CharMatcher[] bmp = utf16Or32Matchers.getBmp();
                            CharMatcher[] astral = utf16Or32Matchers.getAstral();

                            int c = inputReadRaw(locals);
                            inputIncNextIndexRaw(locals);

                            boolean isCodeRangeValid = codeRange == TruffleString.CodeRange.VALID;
                            CompilerAsserts.partialEvaluationConstant(isCodeRangeValid);
                            if (isUTF16()) {
                                boolean mustDecode = codeRange.isSupersetOf(TruffleString.CodeRange.VALID) && state.utf16MustDecode();
                                CompilerAsserts.partialEvaluationConstant(mustDecode);
                                if (mustDecode && injectBranchProbability(ASTRAL_PROBABILITY, inputUTF16IsHighSurrogate(c)) &&
                                                (isCodeRangeValid || injectBranchProbability(LIKELY_PROBABILITY, inputHasNext(locals, locals.getNextIndex())))) {
                                    getAstralProfile().enter();
                                    int c2 = inputReadRaw(locals, locals.getNextIndex());
                                    if (isCodeRangeValid || injectBranchProbability(LIKELY_PROBABILITY, inputUTF16IsLowSurrogate(c2))) {
                                        assert inputUTF16IsLowSurrogate(c2);
                                        locals.setNextIndex(inputIncRaw(locals.getNextIndex()));
                                        if (astral != null) {
                                            c = inputUTF16ToCodePoint(c, c2);
                                        }
                                    }
                                    if (astral != null) {
                                        for (int i = 0; i < astral.length; i++) {
                                            if (match(astral, i, c)) {
                                                ip = transitionMatch(state, i);
                                                continue outer;
                                            }
                                        }
                                    }
                                } else if (latin1 != null && (bmp == null || codeRangeIsSubsetOfLatin1 || injectBranchProbability(LATIN1_PROBABILITY, c < 256))) {
                                    CharMatcher[] byteMatchers = asciiOrLatin1Matchers(codeRange, ascii, latin1);
                                    for (int i = 0; i < byteMatchers.length; i++) {
                                        if (match(byteMatchers, i, c)) {
                                            ip = transitionMatch(state, i);
                                            continue outer;
                                        }
                                    }
                                } else if (bmp != null && codeRange.isSupersetOf(TruffleString.CodeRange.BMP)) {
                                    getBMPProfile().enter();
                                    for (int i = 0; i < bmp.length; i++) {
                                        if (match(bmp, i, c)) {
                                            ip = transitionMatch(state, i);
                                            continue outer;
                                        }
                                    }
                                }
                            } else {
                                assert isUTF32();
                                if (latin1 != null && (codeRangeIsSubsetOfLatin1 || injectBranchProbability(LATIN1_PROBABILITY, c < 256))) {
                                    CharMatcher[] byteMatchers = asciiOrLatin1Matchers(codeRange, ascii, latin1);
                                    for (int i = 0; i < byteMatchers.length; i++) {
                                        if (match(byteMatchers, i, c)) {
                                            ip = transitionMatch(state, i);
                                            continue outer;
                                        }
                                    }
                                } else if (bmp != null && (codeRange == TruffleString.CodeRange.BMP || (injectBranchProbability(BMP_PROBABILITY, c <= 0xffff) &&
                                                (isCodeRangeValid || injectBranchProbability(BMP_PROBABILITY, !Character.isSurrogate((char) c)))))) {
                                    getBMPProfile().enter();
                                    for (int i = 0; i < bmp.length; i++) {
                                        if (match(bmp, i, c)) {
                                            ip = transitionMatch(state, i);
                                            continue outer;
                                        }
                                    }
                                } else if (astral != null && codeRange.isSupersetOf(TruffleString.CodeRange.VALID)) {
                                    getAstralProfile().enter();
                                    for (int i = 0; i < astral.length; i++) {
                                        if (match(astral, i, c)) {
                                            ip = transitionMatch(state, i);
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
                    ip = transitionNoMatch(state);
                    continue outer;
                }
            } else {
                assert curState instanceof DFAFindInnerLiteralStateNode;
                assert isForward();
                DFAFindInnerLiteralStateNode state = (DFAFindInnerLiteralStateNode) curState;
                while (true) {
                    if (injectBranchProbability(EXIT_PROBABILITY, !inputHasNext(locals))) {
                        break outer;
                    }
                    locals.setIndex(state.executeInnerLiteralSearch(locals, this));
                    if (injectBranchProbability(EXIT_PROBABILITY, locals.getIndex() < 0)) {
                        break outer;
                    }
                    if (innerLiteralPrefixMatcher == null || injectBranchProbability(CONTINUE_PROBABILITY, prefixMatcherMatches(frame, innerLiteralPrefixMatcher, locals, codeRange, canFindStart()))) {
                        if (innerLiteralPrefixMatcher == null) {
                            if (isSimpleCG()) {
                                locals.getCGData().results[0] = locals.getIndex();
                            } else if (canFindStart()) {
                                locals.setMatchStart(locals.getIndex());
                            }
                        }
                        inputIncRaw(locals, state.getInnerLiteral().getLiteral().encodedLength());
                        locals.setNextIndex(locals.getIndex());
                        ip = successors[0];
                        continue outer;
                    }
                    inputIncRaw(locals);
                }
            }
        }
        if (recordExecution()) {
            debugRecorder.finishRecording();
        }
        if (isSimpleCG()) {
            int[] result = props.isSimpleCGMustCopy() ? locals.getCGData().currentResult : locals.getCGData().results;
            return locals.getResultInt() == 0 ? result : null;
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

    private static CharMatcher[] asciiOrLatin1Matchers(TruffleString.CodeRange codeRange, CharMatcher[] ascii, CharMatcher[] latin1) {
        return codeRange == TruffleString.CodeRange.ASCII && ascii != null ? ascii : latin1;
    }

    private short initialStateSuccessor(TRegexDFAExecutorLocals locals, DFAAbstractStateNode curState, short[] successors, int i) {
        if (isGenericCG()) {
            locals.setLastIndex();
            short lastTransition = ((DFAInitialStateNode) curState).getCgLastTransition()[i];
            if (lastTransition >= 0) {
                locals.setLastTransition(lastTransition);
            }
        } else if (isSimpleCG()) {
            DFASimpleCG simpleCG = ((DFAInitialStateNode) curState).getSimpleCG();
            if (simpleCG != null) {
                simpleCG.getTransitions()[i].apply(locals.getCGData().results, locals.getIndex(), getProperties().tracksLastGroup(), isForward());
            }
        }
        return successors[i];
    }

    private void initNextIndex(TRegexDFAExecutorLocals locals) {
        locals.setNextIndex(locals.getIndex());
        if (recordExecution()) {
            getDebugRecorder().setInitialIndex(locals.getIndex());
        }
    }

    private static boolean match(CharMatcher[] matchers, int i, final int c) {
        return matchers[i] != null && matchers[i].match(c);
    }

    /**
     * Returns a new instruction pointer value that denotes transition {@code i} of {@code state}.
     */
    private static int transitionMatch(DFAStateNode state, int i) {
        CompilerAsserts.partialEvaluationConstant(state);
        return state.getId() | IP_TRANSITION_MARKER | (i << 16);
    }

    /**
     * Returns a new instruction pointer value that denotes the
     * {@link SequentialMatchers#getNoMatchSuccessor() no-match successor} of {@code state}.
     */
    private static int transitionNoMatch(DFAStateNode state) {
        CompilerAsserts.partialEvaluationConstant(state);
        return state.getId() | IP_TRANSITION_MARKER | (state.getSequentialMatchers().getNoMatchSuccessor() << 16);
    }

    private int execTransition(TRegexDFAExecutorLocals locals, DFAStateNode state, int i) {
        CompilerAsserts.partialEvaluationConstant(state);
        CompilerAsserts.partialEvaluationConstant(i);
        if (recordExecution()) {
            debugRecorder.recordTransition(locals.getIndex(), state.getId(), i);
        }
        state.successorFound(locals, this, i);
        return state.successors[i];
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

    private static int[] initResultOrder(int maxNumberOfNFAStates, int numberOfCaptureGroups, TRegexDFAExecutorProperties props) {
        int[] resultOrder = new int[maxNumberOfNFAStates];
        for (int i = 0; i < maxNumberOfNFAStates; i++) {
            resultOrder[i] = i * (numberOfCaptureGroups * 2 + (props.tracksLastGroup() ? 1 : 0));
        }
        return resultOrder;
    }

    public TRegexDFAExecutorProperties getProperties() {
        return props;
    }

    public int getMaxNumberOfNFAStates() {
        return maxNumberOfNFAStates;
    }

    public int getCGTrackingCost() {
        int cost = 0;
        for (DFAAbstractStateNode s : states) {
            if (s instanceof CGTrackingDFAStateNode) {
                cost += ((CGTrackingDFAStateNode) s).getCGTrackingCost();
            }
        }
        return cost;
    }
}
