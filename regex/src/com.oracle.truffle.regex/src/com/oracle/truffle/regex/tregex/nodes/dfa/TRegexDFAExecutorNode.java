/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.RegexRootNode;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.Matchers.SimpleMatchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.Matchers.UTF16Matchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.Matchers.UTF16RawMatchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.Matchers.UTF8Matchers;
import com.oracle.truffle.regex.tregex.string.Encodings;

public final class TRegexDFAExecutorNode extends TRegexExecutorNode {

    private static final int IP_TRANSITION_MARKER = 0x8000;
    public static final int NO_MATCH = -2;
    private final TRegexDFAExecutorProperties props;
    private final int maxNumberOfNFAStates;
    @Children private final DFAAbstractStateNode[] states;
    @CompilationFinal(dimensions = 1) private final DFACaptureGroupLazyTransition[] cgTransitions;
    private final TRegexDFAExecutorDebugRecorder debugRecorder;

    public TRegexDFAExecutorNode(
                    TRegexDFAExecutorProperties props,
                    int maxNumberOfNFAStates,
                    DFAAbstractStateNode[] states,
                    DFACaptureGroupLazyTransition[] cgTransitions,
                    TRegexDFAExecutorDebugRecorder debugRecorder) {
        this.props = props;
        this.maxNumberOfNFAStates = maxNumberOfNFAStates;
        this.states = states;
        this.cgTransitions = cgTransitions;
        this.debugRecorder = debugRecorder;
    }

    public TRegexDFAExecutorNode(
                    TRegexDFAExecutorProperties props,
                    int maxNumberOfNFAStates,
                    DFAAbstractStateNode[] states,
                    DFACaptureGroupLazyTransition[] cgTransitions) {
        this(props, maxNumberOfNFAStates, states, cgTransitions, null);
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

    public DFACaptureGroupLazyTransition[] getCGTransitions() {
        return cgTransitions;
    }

    public int getNumberOfStates() {
        return states.length;
    }

    public boolean recordExecution() {
        return debugRecorder != null;
    }

    public TRegexDFAExecutorDebugRecorder getDebugRecorder() {
        return debugRecorder;
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
        if (isGenericCG() || isSimpleCG()) {
            return new DFACaptureGroupTrackingData(getMaxNumberOfNFAStates(), getNumberOfCaptureGroups(), props);
        } else {
            return null;
        }
    }

    /**
     * records position of the END of the match found, or -1 if no match exists.
     */
    @Override
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    public Object execute(final TRegexExecutorLocals abstractLocals, final boolean compactString) {
        TRegexDFAExecutorLocals locals = (TRegexDFAExecutorLocals) abstractLocals;
        CompilerDirectives.ensureVirtualized(locals);
        CompilerAsserts.compilationConstant(states);
        CompilerAsserts.compilationConstant(states.length);
        if (!validArgs(locals)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException(String.format("Got illegal args! (fromIndex %d, initialIndex %d, maxIndex %d)",
                            locals.getFromIndex(), locals.getIndex(), locals.getMaxIndex()));
        }
        if (isGenericCG()) {
            initResultOrder(locals);
            locals.setLastTransition((short) -1);
            Arrays.fill(locals.getCGData().results, -1);
        } else if (isSimpleCG()) {
            CompilerDirectives.ensureVirtualized(locals.getCGData());
            Arrays.fill(locals.getCGData().results, -1);
        }
        // check if input is long enough for a match
        if (props.getMinResultLength() > 0 &&
                        (isForward() ? locals.getMaxIndex() - locals.getIndex() : locals.getIndex() - Math.max(0, locals.getFromIndex() - getPrefixLength())) < props.getMinResultLength()) {
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
                if (isGenericCG()) {
                    locals.setLastTransition((short) 0);
                }
                if (isSearching()) {
                    assert isForward();
                    /*
                     * We are in search mode - rewind up to prefixLength code points and select
                     * successors[n], where n is the number of skipped code points.
                     */
                    for (int i = 0; i < getPrefixLength(); i++) {
                        if (locals.getIndex() > 0) {
                            inputSkipIntl(locals, false);
                        } else {
                            initNextIndex(locals);
                            ip = successors[i];
                            continue outer;
                        }
                    }
                    initNextIndex(locals);
                    if (inputAtBegin(locals)) {
                        ip = successors[getPrefixLength()];
                        continue outer;
                    } else {
                        ip = successors[getPrefixLength() + (successors.length / 2)];
                        continue outer;
                    }
                } else {
                    /*
                     * We are in non-searching mode - if we start behind fromIndex, select
                     * successors[n], where n is the number of code points between the current index
                     * and fromIndex.
                     */
                    initNextIndex(locals);
                    boolean atBegin = inputAtBegin(locals);
                    for (int i = 0; i < getPrefixLength(); i++) {
                        assert isForward();
                        if (locals.getIndex() < locals.getFromIndex()) {
                            inputSkipIntl(locals, true);
                        } else {
                            if (atBegin) {
                                ip = successors[i];
                                continue outer;
                            } else {
                                ip = successors[i + (successors.length / 2)];
                                continue outer;
                            }
                        }
                    }
                    if (atBegin) {
                        ip = successors[getPrefixLength()];
                        continue outer;
                    } else {
                        ip = successors[getPrefixLength() + (successors.length / 2)];
                        continue outer;
                    }
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
                    /*
                     * find matching DFA state transition
                     */
                    state.getStateReachedProfile().enter();
                    inputAdvance(locals);
                    state.beforeFindSuccessor(locals, this);
                    if (isForward() && state.canDoIndexOf()) {
                        int indexOfResult = state.loopOptimizationNode.execute(locals.getInput(), locals.getIndex(), getMaxIndex(locals));
                        int postLoopIndex = indexOfResult < 0 ? getMaxIndex(locals) : indexOfResult;
                        state.afterIndexOf(locals, this, locals.getIndex(), postLoopIndex);
                        assert locals.getIndex() == postLoopIndex;
                        if (successors.length == 2 && indexOfResult >= 0) {
                            int successor = (state.getLoopToSelf() + 1) & 1;
                            CompilerAsserts.partialEvaluationConstant(successor);
                            inputIncNextIndexRaw(locals, state.loopOptimizationNode.encodedLength());
                            ip = execTransition(locals, state, successor);
                            continue outer;
                        }
                    }
                    if (!inputHasNext(locals)) {
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
                        assert !isRegressionTestMode() || state.sameResultAsRegularMatchers(c, treeSuccessor);
                        // TODO: this switch loop should be replaced with a PE intrinsic
                        for (int i = 0; i < successors.length; i++) {
                            if (i == treeSuccessor) {
                                ip = transitionMatch(state, i);
                                continue outer;
                            }
                        }
                        break;
                    }
                    Matchers matchers = state.getMatchers();
                    if (matchers instanceof SimpleMatchers) {
                        final int c = inputReadAndDecode(locals);
                        CharMatcher[] cMatchers = ((SimpleMatchers) matchers).getMatchers();
                        if (cMatchers != null) {
                            for (int i = 0; i < cMatchers.length; i++) {
                                if (match(cMatchers, i, c)) {
                                    ip = transitionMatch(state, i);
                                    continue outer;
                                }
                            }
                        }
                    } else if (matchers instanceof UTF8Matchers) {
                        /*
                         * UTF-8 on-the fly decoding
                         */
                        UTF8Matchers utf8Matchers = (UTF8Matchers) matchers;
                        CharMatcher[] ascii = utf8Matchers.getAscii();
                        CharMatcher[] enc2 = utf8Matchers.getEnc2();
                        CharMatcher[] enc3 = utf8Matchers.getEnc3();
                        CharMatcher[] enc4 = utf8Matchers.getEnc4();
                        int c = inputReadRaw(locals);
                        if (getInputProfile().profile(c < 128)) {
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
                            int codepoint = c & 0x3f;
                            if (isBackward()) {
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
                            if (isBackward()) {
                                codepoint |= (c & (0xff >>> nBytes)) << (6 * (nBytes - 1));
                            }
                            switch (nBytes) {
                                case 2:
                                    if (enc2 != null) {
                                        codepoint = inputUTF8Decode2(locals, c, codepoint);
                                        for (int i = 0; i < enc2.length; i++) {
                                            if (match(enc2, i, codepoint)) {
                                                ip = transitionMatch(state, i);
                                                continue outer;
                                            }
                                        }
                                    }
                                    break;
                                case 3:
                                    if (enc3 != null) {
                                        codepoint = inputUTF8Decode3(locals, c, codepoint);
                                        for (int i = 0; i < enc3.length; i++) {
                                            if (match(enc3, i, codepoint)) {
                                                ip = transitionMatch(state, i);
                                                continue outer;
                                            }
                                        }
                                    }
                                    break;
                                case 4:
                                    if (enc4 != null) {
                                        codepoint = inputUTF8Decode4(locals, c, codepoint);
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
                    } else if (matchers instanceof UTF16RawMatchers) {
                        /*
                         * UTF-16 interpreted as raw 16-bit values, no decoding
                         */
                        final int c = inputReadAndDecode(locals);
                        CharMatcher[] latin1 = ((UTF16RawMatchers) matchers).getLatin1();
                        CharMatcher[] bmp = ((UTF16RawMatchers) matchers).getBmp();
                        if (latin1 != null && (bmp == null || compactString || getInputProfile().profile(c < 256))) {
                            for (int i = 0; i < latin1.length; i++) {
                                if (match(latin1, i, c)) {
                                    ip = transitionMatch(state, i);
                                    continue outer;
                                }
                            }
                        } else if (bmp != null) {
                            for (int i = 0; i < bmp.length; i++) {
                                if (match(bmp, i, c)) {
                                    ip = transitionMatch(state, i);
                                    continue outer;
                                }
                            }
                        }
                    } else {
                        /*
                         * UTF-16 on-the fly decoding
                         */
                        assert matchers instanceof UTF16Matchers;
                        assert getEncoding() == Encodings.UTF_16;
                        UTF16Matchers utf16Matchers = (UTF16Matchers) matchers;
                        CharMatcher[] latin1 = utf16Matchers.getLatin1();
                        CharMatcher[] bmp = utf16Matchers.getBmp();
                        CharMatcher[] astral = utf16Matchers.getAstral();

                        int c = inputReadRaw(locals);
                        inputIncNextIndexRaw(locals);

                        if (!compactString && state.utf16MustDecode() && inputUTF16IsHighSurrogate(c) && inputHasNext(locals, locals.getNextIndex())) {
                            int c2 = inputReadRaw(locals, locals.getNextIndex());
                            if (inputUTF16IsLowSurrogate(c2)) {
                                locals.setNextIndex(inputIncRaw(locals.getNextIndex()));
                                if (astral != null) {
                                    c = inputUTF16ToCodePoint(c, c2);
                                    for (int i = 0; i < astral.length; i++) {
                                        if (match(astral, i, c)) {
                                            ip = transitionMatch(state, i);
                                            continue outer;
                                        }
                                    }
                                }
                                ip = transitionNoMatch(state);
                                continue outer;
                            }
                        } else if (latin1 != null && (bmp == null || compactString || getInputProfile().profile(c < 256))) {
                            for (int i = 0; i < latin1.length; i++) {
                                if (match(latin1, i, c)) {
                                    ip = transitionMatch(state, i);
                                    continue outer;
                                }
                            }
                        }
                        if (bmp != null) {
                            for (int i = 0; i < bmp.length; i++) {
                                if (match(bmp, i, c)) {
                                    ip = transitionMatch(state, i);
                                    continue outer;
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
                    if (!inputHasNext(locals)) {
                        break outer;
                    }
                    locals.setIndex(state.executeInnerLiteralSearch(locals, this));
                    if (locals.getIndex() < 0) {
                        break outer;
                    }
                    if (!state.hasPrefixMatcher() || state.prefixMatcherMatches(locals, compactString)) {
                        if (!state.hasPrefixMatcher() && isSimpleCG()) {
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

    private void initNextIndex(TRegexDFAExecutorLocals locals) {
        locals.setNextIndex(locals.getIndex());
        if (recordExecution()) {
            getDebugRecorder().setInitialIndex(locals.getIndex());
        }
    }

    private static boolean match(CharMatcher[] matchers, int i, final int c) {
        return matchers[i] != null && matchers[i].execute(c);
    }

    /**
     * Returns a new instruction pointer value that denotes transition {@code i} of {@code state}.
     */
    private static int transitionMatch(DFAStateNode state, int i) {
        return state.getId() | IP_TRANSITION_MARKER | (i << 16);
    }

    /**
     * Returns a new instruction pointer value that denotes the
     * {@link Matchers#getNoMatchSuccessor() no-match successor} of {@code state}.
     */
    private static int transitionNoMatch(DFAStateNode state) {
        return state.getId() | IP_TRANSITION_MARKER | (state.getMatchers().getNoMatchSuccessor() << 16);
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

    private int inputUTF8Decode2(TRegexDFAExecutorLocals locals, int c, int codepoint) {
        if (isForward()) {
            return ((c & 0x3f) << 6) |
                            (inputReadRaw(locals, locals.getIndex() + 1) & 0x3f);
        }
        return codepoint;
    }

    private int inputUTF8Decode3(TRegexDFAExecutorLocals locals, int c, int codepoint) {
        if (isForward()) {
            return ((c & 0x1f) << 12) |
                            ((inputReadRaw(locals, locals.getIndex() + 1) & 0x3f) << 6) |
                            (inputReadRaw(locals, locals.getIndex() + 2) & 0x3f);
        }
        return codepoint;
    }

    private int inputUTF8Decode4(TRegexDFAExecutorLocals locals, int c, int codepoint) {
        if (isForward()) {
            return ((c & 0x0f) << 18) |
                            ((inputReadRaw(locals, locals.getIndex() + 1) & 0x3f) << 12) |
                            ((inputReadRaw(locals, locals.getIndex() + 2) & 0x3f) << 6) |
                            (inputReadRaw(locals, locals.getIndex() + 3) & 0x3f);
        }
        return codepoint;
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

    @ExplodeLoop
    private void initResultOrder(TRegexDFAExecutorLocals locals) {
        DFACaptureGroupTrackingData cgData = locals.getCGData();
        for (int i = 0; i < maxNumberOfNFAStates; i++) {
            cgData.currentResultOrder[i] = i * getNumberOfCaptureGroups() * 2;
        }
    }

    public TRegexDFAExecutorProperties getProperties() {
        return props;
    }

    public int getMaxNumberOfNFAStates() {
        return maxNumberOfNFAStates;
    }

    public double getCGReorderRatio() {
        if (!isGenericCG()) {
            return 0;
        }
        int nPT = 0;
        int nReorder = 0;
        for (DFACaptureGroupLazyTransition t : cgTransitions) {
            nPT += t.getPartialTransitions().length;
            for (DFACaptureGroupPartialTransition pt : t.getPartialTransitions()) {
                if (pt.doesReorderResults()) {
                    nReorder++;
                }
            }
        }
        if (nPT > 0) {
            return (double) nReorder / nPT;
        }
        return 0;
    }

    public double getCGArrayCopyRatio() {
        if (!isGenericCG()) {
            return 0;
        }
        int nPT = 0;
        int nArrayCopy = 0;
        for (DFACaptureGroupLazyTransition t : cgTransitions) {
            nPT += t.getPartialTransitions().length;
            for (DFACaptureGroupPartialTransition pt : t.getPartialTransitions()) {
                nArrayCopy += pt.getArrayCopies().length / 2;
            }
        }
        if (nPT > 0) {
            return (double) nArrayCopy / nPT;
        }
        return 0;
    }
}
