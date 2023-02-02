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
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.SequentialMatchers.SimpleSequentialMatchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.SequentialMatchers.UTF16Or32SequentialMatchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.SequentialMatchers.UTF16RawSequentialMatchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.SequentialMatchers.UTF8SequentialMatchers;
import com.oracle.truffle.regex.tregex.nodes.input.InputIndexOfNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputIndexOfStringNode;

public final class TRegexDFAExecutorNode extends TRegexExecutorNode {

    private static final int IP_TRANSITION_MARKER = 0x8000;
    public static final int NO_MATCH = -2;
    private final TRegexDFAExecutorProperties props;
    private final int maxNumberOfNFAStates;
    @CompilationFinal(dimensions = 1) private final DFAAbstractStateNode[] states;
    @CompilationFinal(dimensions = 1) private final int[] cgResultOrder;
    private final TRegexDFAExecutorDebugRecorder debugRecorder;

    @Child private InputIndexOfNode indexOfNode;
    @Child private InputIndexOfStringNode indexOfStringNode;
    @Child private TRegexDFAExecutorNode innerLiteralPrefixMatcher;

    public TRegexDFAExecutorNode(
                    RegexSource source,
                    TRegexDFAExecutorProperties props,
                    int numberOfCaptureGroups,
                    int maxNumberOfNFAStates,
                    DFAAbstractStateNode[] states,
                    TRegexDFAExecutorDebugRecorder debugRecorder,
                    TRegexDFAExecutorNode innerLiteralPrefixMatcher) {
        this(source, props, numberOfCaptureGroups, calcNumberOfTransitions(states), maxNumberOfNFAStates, states,
                        props.isGenericCG() && maxNumberOfNFAStates > 1 ? initResultOrder(maxNumberOfNFAStates, numberOfCaptureGroups, props) : null, debugRecorder,
                        innerLiteralPrefixMatcher);
    }

    public TRegexDFAExecutorNode(
                    RegexSource source,
                    TRegexDFAExecutorProperties props,
                    int numberOfCaptureGroups,
                    int numberOfTransitions,
                    int maxNumberOfNFAStates,
                    DFAAbstractStateNode[] states,
                    int[] cgResultOrder,
                    TRegexDFAExecutorDebugRecorder debugRecorder,
                    TRegexDFAExecutorNode innerLiteralPrefixMatcher) {
        super(source, numberOfCaptureGroups, numberOfTransitions);
        this.props = props;
        this.maxNumberOfNFAStates = maxNumberOfNFAStates;
        this.states = states;
        this.cgResultOrder = cgResultOrder;
        this.debugRecorder = debugRecorder;
        this.innerLiteralPrefixMatcher = innerLiteralPrefixMatcher;
    }

    private TRegexDFAExecutorNode(TRegexDFAExecutorNode copy, TRegexDFAExecutorNode innerLiteralPrefixMatcher) {
        this(copy.getSource(), copy.props, copy.getNumberOfCaptureGroups(), copy.getNumberOfTransitions(), copy.maxNumberOfNFAStates, copy.states, copy.cgResultOrder, copy.debugRecorder,
                        innerLiteralPrefixMatcher);
    }

    @Override
    public TRegexDFAExecutorNode shallowCopy() {
        return new TRegexDFAExecutorNode(this, innerLiteralPrefixMatcher == null ? null : innerLiteralPrefixMatcher.shallowCopy());
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

    public boolean isBackward() {
        return !props.isForward();
    }

    public boolean isSearching() {
        return props.isSearching();
    }

    public boolean isSimpleCG() {
        return props.isSimpleCG();
    }

    public boolean isGenericCG() {
        return props.isGenericCG();
    }

    public boolean isRegressionTestMode() {
        return props.isRegressionTestMode();
    }

    public int getNumberOfStates() {
        return states.length;
    }

    private static int calcNumberOfTransitions(DFAAbstractStateNode[] states) {
        int sum = 0;
        for (DFAAbstractStateNode state : states) {
            sum += state.getSuccessors().length;
            if (state instanceof DFAStateNode && !((DFAStateNode) state).treeTransitionMatching() &&
                            ((DFAStateNode) state).getSequentialMatchers().getNoMatchSuccessor() >= 0) {
                sum++;
            }
        }
        return sum;
    }

    public boolean recordExecution() {
        return debugRecorder != null;
    }

    public TRegexDFAExecutorDebugRecorder getDebugRecorder() {
        return debugRecorder;
    }

    InputIndexOfNode getIndexOfNode() {
        if (indexOfNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            indexOfNode = insert(InputIndexOfNode.create());
        }
        return indexOfNode;
    }

    InputIndexOfStringNode getIndexOfStringNode() {
        if (indexOfStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            indexOfStringNode = insert(InputIndexOfStringNode.create());
        }
        return indexOfStringNode;
    }

    @Override
    public TRegexExecutorLocals createLocals(Object input, int fromIndex, int index, int maxIndex) {
        return new TRegexDFAExecutorLocals(input, fromIndex, index, maxIndex, createCGData());
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
    public Object execute(VirtualFrame frame, final TRegexExecutorLocals abstractLocals, final TruffleString.CodeRange codeRange, boolean tString) {
        TRegexDFAExecutorLocals locals = (TRegexDFAExecutorLocals) abstractLocals;
        CompilerDirectives.ensureVirtualized(locals);
        CompilerAsserts.partialEvaluationConstant(states);
        CompilerAsserts.partialEvaluationConstant(states.length);
        CompilerAsserts.partialEvaluationConstant(codeRange);
        if (injectBranchProbability(SLOWPATH_PROBABILITY, !validArgs(locals))) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException(String.format("Got illegal args! (fromIndex %d, initialIndex %d, maxIndex %d)",
                            locals.getFromIndex(), locals.getIndex(), locals.getMaxIndex()));
        }
        if (isGenericCG() || isSimpleCG()) {
            CompilerDirectives.ensureVirtualized(locals.getCGData());
        }
        // check if input is long enough for a match
        if (injectBranchProbability(UNLIKELY_PROBABILITY, props.getMinResultLength() > 0 &&
                        (isForward() ? locals.getMaxIndex() - locals.getIndex() : locals.getIndex() - Math.max(0, locals.getFromIndex() - getPrefixLength())) < props.getMinResultLength())) {
            // no match possible, break immediately
            return isGenericCG() || isSimpleCG() ? null : TRegexDFAExecutorNode.NO_MATCH;
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
                        if (injectBranchProbability(UNLIKELY_PROBABILITY, locals.getIndex() > 0)) {
                            inputSkipIntl(locals, false);
                        } else {
                            initNextIndex(locals);
                            ip = initialStateSuccessor(locals, curState, successors, i);
                            continue outer;
                        }
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
                            inputSkipIntl(locals, true);
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
                    if (injectBranchProbability(CONTINUE_PROBABILITY, isForward() && state.canDoIndexOf() && inputHasNext(locals))) {
                        int indexOfResult = state.indexOfCall.execute(this, locals.getInput(), locals.getIndex(), getMaxIndex(locals), getEncoding(), tString);
                        int postLoopIndex = indexOfResult < 0 ? getMaxIndex(locals) : indexOfResult;
                        state.afterIndexOf(locals, this, locals.getIndex(), postLoopIndex);
                        assert locals.getIndex() == postLoopIndex;
                        if (injectBranchProbability(CONTINUE_PROBABILITY, successors.length == 2 && indexOfResult >= 0)) {
                            int successor = (state.getLoopToSelf() + 1) & 1;
                            CompilerAsserts.partialEvaluationConstant(successor);
                            inputIncNextIndexRaw(locals, state.indexOfCall.encodedLength());
                            ip = execTransition(locals, state, successor);
                            continue outer;
                        }
                    }
                    if (injectBranchProbability(EXIT_PROBABILITY, !inputHasNext(locals))) {
                        state.atEnd(locals, this);
                        if (isBackward() && state.hasBackwardPrefixState() && locals.getIndex() > 0) {
                            assert locals.getIndex() == locals.getFromIndex();
                            /*
                             * We have reached the starting index of the forward matcher, so we have
                             * to switch to backward-prefix-states. These states will match
                             * look-behind assertions only.
                             */
                            locals.setCurMinIndex(0);
                            ip = transitionMatch(state, ((BackwardDFAStateNode) state).getBackwardPrefixStateIndex());
                            continue outer;
                        }
                        break;
                    }
                    if (state.treeTransitionMatching()) {
                        int c = inputReadAndDecode(locals);
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
                    if (matchers instanceof SimpleSequentialMatchers) {
                        final int c = inputReadAndDecode(locals);
                        CharMatcher[] cMatchers = ((SimpleSequentialMatchers) matchers).getMatchers();
                        if (cMatchers != null) {
                            for (int i = 0; i < cMatchers.length; i++) {
                                if (match(cMatchers, i, c)) {
                                    ip = transitionMatch(state, i);
                                    continue outer;
                                }
                            }
                        }
                    } else if (matchers instanceof UTF8SequentialMatchers) {
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
                        if (injectBranchProbability(LATIN1_PROBABILITY, codeRange == TruffleString.CodeRange.ASCII || c < 128)) {
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
                    } else if (matchers instanceof UTF16RawSequentialMatchers) {
                        /*
                         * UTF-16 interpreted as raw 16-bit values, no decoding
                         */
                        final int c = inputReadAndDecode(locals);
                        CharMatcher[] ascii = ((UTF16RawSequentialMatchers) matchers).getAscii();
                        CharMatcher[] latin1 = ((UTF16RawSequentialMatchers) matchers).getLatin1();
                        CharMatcher[] bmp = ((UTF16RawSequentialMatchers) matchers).getBmp();
                        if (injectBranchProbability(LATIN1_PROBABILITY, latin1 != null && (bmp == null || codeRange.isSubsetOf(TruffleString.CodeRange.LATIN_1) || c < 256))) {
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

                        if (isUTF16()) {
                            if (injectBranchProbability(ASTRAL_PROBABILITY,
                                            codeRange.isSupersetOf(TruffleString.CodeRange.VALID) && state.utf16MustDecode() && inputUTF16IsHighSurrogate(c) &&
                                                            (codeRange == TruffleString.CodeRange.VALID || inputHasNext(locals, locals.getNextIndex())))) {
                                getAstralProfile().enter();
                                int c2 = inputReadRaw(locals, locals.getNextIndex());
                                if (injectBranchProbability(LIKELY_PROBABILITY, codeRange == TruffleString.CodeRange.VALID || inputUTF16IsLowSurrogate(c2))) {
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
                            } else if (injectBranchProbability(LATIN1_PROBABILITY, latin1 != null && (bmp == null || codeRange.isSubsetOf(TruffleString.CodeRange.LATIN_1) || c < 256))) {
                                CharMatcher[] byteMatchers = asciiOrLatin1Matchers(codeRange, ascii, latin1);
                                for (int i = 0; i < byteMatchers.length; i++) {
                                    if (match(byteMatchers, i, c)) {
                                        ip = transitionMatch(state, i);
                                        continue outer;
                                    }
                                }
                            } else if (injectBranchProbability(BMP_PROBABILITY, bmp != null && codeRange.isSupersetOf(TruffleString.CodeRange.BMP))) {
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
                            if (injectBranchProbability(LATIN1_PROBABILITY, latin1 != null && (codeRange.isSubsetOf(TruffleString.CodeRange.LATIN_1) || c < 256))) {
                                CharMatcher[] byteMatchers = asciiOrLatin1Matchers(codeRange, ascii, latin1);
                                for (int i = 0; i < byteMatchers.length; i++) {
                                    if (match(byteMatchers, i, c)) {
                                        ip = transitionMatch(state, i);
                                        continue outer;
                                    }
                                }
                            } else if (injectBranchProbability(BMP_PROBABILITY, bmp != null &&
                                            (codeRange == TruffleString.CodeRange.BMP || (c <= 0xffff && (codeRange == TruffleString.CodeRange.VALID || !Character.isSurrogate((char) c)))))) {
                                getBMPProfile().enter();
                                for (int i = 0; i < bmp.length; i++) {
                                    if (match(bmp, i, c)) {
                                        ip = transitionMatch(state, i);
                                        continue outer;
                                    }
                                }
                            } else if (injectBranchProbability(ASTRAL_PROBABILITY, astral != null && codeRange.isSupersetOf(TruffleString.CodeRange.VALID))) {
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
                    locals.setIndex(state.executeInnerLiteralSearch(locals, this, tString));
                    if (injectBranchProbability(EXIT_PROBABILITY, locals.getIndex() < 0)) {
                        break outer;
                    }
                    if (injectBranchProbability(CONTINUE_PROBABILITY, innerLiteralPrefixMatcher == null || prefixMatcherMatches(frame, innerLiteralPrefixMatcher, locals, codeRange, tString))) {
                        if (innerLiteralPrefixMatcher == null && isSimpleCG()) {
                            locals.getCGData().results[0] = locals.getIndex();
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
        return locals.getResultInt();
    }

    private static boolean prefixMatcherMatches(VirtualFrame frame, TRegexDFAExecutorNode prefixMatcher, TRegexDFAExecutorLocals locals, TruffleString.CodeRange codeRange, boolean tString) {
        Object result = prefixMatcher.execute(frame, locals.toInnerLiteralBackwardLocals(), codeRange, tString);
        return prefixMatcher.isSimpleCG() ? result != null : (int) result != NO_MATCH;
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

    private boolean validArgs(TRegexDFAExecutorLocals locals) {
        final int initialIndex = locals.getIndex();
        final int inputLength = getInputLength(locals);
        final int fromIndex = locals.getFromIndex();
        final int maxIndex = locals.getMaxIndex();
        return inputLength >= 0 && inputLength < Integer.MAX_VALUE - 20 &&
                        fromIndex >= 0 && fromIndex <= inputLength &&
                        initialIndex >= 0 && initialIndex <= maxIndex &&
                        maxIndex >= fromIndex && maxIndex <= inputLength;
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
}
