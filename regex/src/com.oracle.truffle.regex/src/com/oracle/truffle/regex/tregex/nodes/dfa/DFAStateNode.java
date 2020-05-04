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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.Matchers.SimpleMatchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.Matchers.UTF16Matchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.Matchers.UTF16RawMatchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.Matchers.UTF8Matchers;
import com.oracle.truffle.regex.tregex.nodes.input.InputIndexOfNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputIndexOfStringNode;
import com.oracle.truffle.regex.tregex.string.AbstractString;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public class DFAStateNode extends DFAAbstractStateNode {

    public abstract static class LoopOptimizationNode extends Node {

        public abstract int execute(Object input, int preLoopIndex, int maxIndex);

        public abstract int encodedLength();

        abstract LoopOptimizationNode nodeSplitCopy();
    }

    public abstract static class LoopOptIndexOfAnyNode extends LoopOptimizationNode {

        @Child private InputIndexOfNode indexOfNode;

        @Override
        public int encodedLength() {
            return 1;
        }

        InputIndexOfNode getIndexOfNode() {
            if (indexOfNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                indexOfNode = insert(InputIndexOfNode.create());
            }
            return indexOfNode;
        }
    }

    public static final class LoopOptIndexOfAnyCharNode extends LoopOptIndexOfAnyNode {

        @CompilationFinal(dimensions = 1) private final char[] chars;

        public LoopOptIndexOfAnyCharNode(char[] chars) {
            this.chars = chars;
        }

        private LoopOptIndexOfAnyCharNode(LoopOptIndexOfAnyCharNode copy) {
            this.chars = copy.chars;
        }

        @Override
        public int execute(Object input, int fromIndex, int maxIndex) {
            return getIndexOfNode().execute(input, fromIndex, maxIndex, chars);
        }

        @Override
        LoopOptimizationNode nodeSplitCopy() {
            return new LoopOptIndexOfAnyCharNode(this);
        }
    }

    public static final class LoopOptIndexOfAnyByteNode extends LoopOptIndexOfAnyNode {

        @CompilationFinal(dimensions = 1) private final byte[] bytes;

        public LoopOptIndexOfAnyByteNode(byte[] bytes) {
            this.bytes = bytes;
        }

        private LoopOptIndexOfAnyByteNode(LoopOptIndexOfAnyByteNode copy) {
            this.bytes = copy.bytes;
        }

        @Override
        public int execute(Object input, int fromIndex, int maxIndex) {
            return getIndexOfNode().execute(input, fromIndex, maxIndex, bytes);
        }

        @Override
        LoopOptimizationNode nodeSplitCopy() {
            return new LoopOptIndexOfAnyByteNode(this);
        }
    }

    public static final class LoopOptIndexOfStringNode extends LoopOptimizationNode {

        private final AbstractString str;
        private final AbstractString mask;
        @Child private InputIndexOfStringNode indexOfNode;

        public LoopOptIndexOfStringNode(AbstractString str, AbstractString mask) {
            this.str = str;
            this.mask = mask;
        }

        private LoopOptIndexOfStringNode(LoopOptIndexOfStringNode copy) {
            this.str = copy.str;
            this.mask = copy.mask;
        }

        @Override
        public int execute(Object input, int fromIndex, int maxIndex) {
            return getIndexOfNode().execute(input, fromIndex, maxIndex, str.content(), mask == null ? null : mask.content());
        }

        @Override
        public int encodedLength() {
            return str.encodedLength();
        }

        @Override
        LoopOptimizationNode nodeSplitCopy() {
            return new LoopOptIndexOfStringNode(this);
        }

        private InputIndexOfStringNode getIndexOfNode() {
            if (indexOfNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                indexOfNode = insert(InputIndexOfStringNode.create());
            }
            return indexOfNode;
        }
    }

    private static final byte FLAG_FINAL_STATE = 1;
    private static final byte FLAG_ANCHORED_FINAL_STATE = 1 << 1;
    private static final byte FLAG_HAS_BACKWARD_PREFIX_STATE = 1 << 2;
    private static final byte FLAG_UTF_16_MUST_DECODE = 1 << 3;

    private final byte flags;
    private final short loopTransitionIndex;
    @Child LoopOptimizationNode loopOptimizationNode;
    @Child Matchers matchers;
    private final DFASimpleCG simpleCG;
    private final AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher;
    private final BranchProfile stateReachedProfile = BranchProfile.create();

    DFAStateNode(DFAStateNode nodeSplitCopy, short copyID) {
        this(copyID, nodeSplitCopy.flags, nodeSplitCopy.loopTransitionIndex, nodeSplitCopy.loopOptimizationNode.nodeSplitCopy(),
                        Arrays.copyOf(nodeSplitCopy.getSuccessors(), nodeSplitCopy.getSuccessors().length),
                        nodeSplitCopy.getMatchers(), nodeSplitCopy.simpleCG, nodeSplitCopy.allTransitionsInOneTreeMatcher);
    }

    public DFAStateNode(short id, byte flags, short loopTransitionIndex, LoopOptimizationNode loopOptimizationNode, short[] successors, Matchers matchers, DFASimpleCG simpleCG,
                    AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher) {
        super(id, successors);
        assert id > 0;
        this.flags = flags;
        this.loopTransitionIndex = loopTransitionIndex;
        this.loopOptimizationNode = loopOptimizationNode;
        this.matchers = matchers;
        this.simpleCG = simpleCG;
        this.allTransitionsInOneTreeMatcher = allTransitionsInOneTreeMatcher;
    }

    public static byte buildFlags(boolean finalState, boolean anchoredFinalState, boolean hasBackwardPrefixState, boolean utf16MustDecode) {
        byte flags = 0;
        if (finalState) {
            flags |= FLAG_FINAL_STATE;
        }
        if (anchoredFinalState) {
            flags |= FLAG_ANCHORED_FINAL_STATE;
        }
        if (hasBackwardPrefixState) {
            flags |= FLAG_HAS_BACKWARD_PREFIX_STATE;
        }
        if (utf16MustDecode) {
            flags |= FLAG_UTF_16_MUST_DECODE;
        }
        return flags;
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new DFAStateNode(this, copyID);
    }

    public final Matchers getMatchers() {
        return matchers;
    }

    public BranchProfile getStateReachedProfile() {
        return stateReachedProfile;
    }

    public boolean isFinalState() {
        return flagIsSet(FLAG_FINAL_STATE);
    }

    public boolean isAnchoredFinalState() {
        return flagIsSet(FLAG_ANCHORED_FINAL_STATE);
    }

    public boolean hasBackwardPrefixState() {
        return flagIsSet(FLAG_HAS_BACKWARD_PREFIX_STATE);
    }

    public boolean utf16MustDecode() {
        return flagIsSet(FLAG_UTF_16_MUST_DECODE);
    }

    private boolean flagIsSet(byte flag) {
        return (flags & flag) != 0;
    }

    public boolean hasLoopToSelf() {
        return loopTransitionIndex >= 0;
    }

    boolean isLoopToSelf(int transitionIndex) {
        return hasLoopToSelf() && transitionIndex == getLoopToSelf();
    }

    short getLoopToSelf() {
        assert hasLoopToSelf();
        return loopTransitionIndex;
    }

    boolean treeTransitionMatching() {
        return allTransitionsInOneTreeMatcher != null;
    }

    AllTransitionsInOneTreeMatcher getTreeMatcher() {
        return allTransitionsInOneTreeMatcher;
    }

    /**
     * Calculates this state's successor by finding a transition that matches the current input. If
     * the successor is the state itself, this method continues consuming input characters until a
     * different successor is found. This special handling allows for partial loop unrolling inside
     * the DFA, as well as some optimizations in {@link CGTrackingDFAStateNode}.
     *
     * @param locals a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     */
    @Override
    public void executeFindSuccessor(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(compactString);
        if (hasLoopToSelf()) {
            if (executor.isForward() && loopOptimizationNode != null) {
                runIndexOf(locals, executor, compactString);
            } else {
                while (executor.inputHasNext(locals)) {
                    if (executor.isSimpleCG()) {
                        // we have to write the final state transition before anything else in
                        // simpleCG mode
                        checkFinalState(locals, executor);
                    }
                    if (!checkMatchOrTree(locals, executor, compactString)) {
                        if (!executor.isSimpleCG()) {
                            // in ignore-capture-groups mode, we can delay the final state check
                            checkFinalState(locals, executor);
                        }
                        executor.inputAdvance(locals);
                        return;
                    }
                    executor.inputAdvance(locals);
                }
                locals.setSuccessorIndex(atEnd(locals, executor));
            }
        } else {
            if (!executor.inputHasNext(locals)) {
                locals.setSuccessorIndex(atEnd(locals, executor));
                return;
            }
            checkFinalState(locals, executor);
            checkMatchOrTree(locals, executor, compactString);
            executor.inputAdvance(locals);
        }
    }

    private void runIndexOf(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        assert executor.isForward();
        final int preLoopIndex = locals.getIndex();
        int indexOfResult = loopOptimizationNode.execute(locals.getInput(), preLoopIndex, executor.getMaxIndex(locals));
        locals.setIndex(indexOfResult < 0 ? executor.getMaxIndex(locals) : indexOfResult);
        if (simpleCG != null && locals.getIndex() > preLoopIndex) {
            int curIndex = locals.getIndex();
            executor.inputSkipReverse(locals);
            applySimpleCGTransition(simpleCG.getTransitions()[getLoopToSelf()], locals);
            locals.setIndex(curIndex);
        }
        if (indexOfResult < 0) {
            locals.setSuccessorIndex(atEnd(locals, executor));
        } else {
            checkFinalState(locals, executor);
            if (successors.length == 2) {
                int successor = (getLoopToSelf() + 1) & 1;
                CompilerAsserts.partialEvaluationConstant(successor);
                if (simpleCG != null) {
                    applySimpleCGTransition(simpleCG.getTransitions()[successor], locals);
                }
                executor.inputIncRaw(locals, loopOptimizationNode.encodedLength());
                locals.setSuccessorIndex(successor);
            } else {
                checkMatchOrTree(locals, executor, compactString);
                executor.inputAdvance(locals);
            }
        }
    }

    private boolean checkMatchOrTree(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        if (treeTransitionMatching()) {
            return doTreeMatch(locals, executor, compactString);
        } else {
            return checkMatch(locals, executor, compactString, false, 0);
        }
    }

    boolean doTreeMatch(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        final int c = executor.inputRead(locals);
        int successor = getTreeMatcher().checkMatchTree(locals, executor, this, c);
        assert sameResultAsRegularMatchers(executor, c, compactString, successor) : this.toString();
        locals.setSuccessorIndex(successor);
        return isLoopToSelf(successor);
    }

    boolean sameResultAsRegularMatchers(TRegexDFAExecutorNode executor, int c, boolean compactString, int allTransitionsMatcherResult) {
        CompilerAsserts.neverPartOfCompilation();
        if (executor.isRegressionTestMode()) {
            return allTransitionsMatcherResult == matchers.match(c, compactString);
        }
        return true;
    }

    /**
     * Finds the first matching transition. The index of the element of {@link #getMatchers()} that
     * matched the current input character (
     * {@link TRegexExecutorNode#inputRead(TRegexExecutorLocals)}) or
     * {@link #FS_RESULT_NO_SUCCESSOR} is stored via
     * {@link TRegexDFAExecutorLocals#setSuccessorIndex(int)}.
     *
     * @param locals a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    boolean checkMatch(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString, boolean loopMode, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(loopMode);
        if (matchers instanceof SimpleMatchers) {
            final int c = executor.inputRead(locals);
            CharMatcher[] cMatchers = ((SimpleMatchers) matchers).getMatchers();
            if (cMatchers != null) {
                for (int i = 0; i < cMatchers.length; i++) {
                    if (match(cMatchers, i, c, compactString)) {
                        return checkMatchSuccessorFoundHook(locals, executor, loopMode, preLoopIndex, i);
                    }
                }
            }
        } else if (matchers instanceof UTF8Matchers) {
            UTF8Matchers utf8Matchers = (UTF8Matchers) matchers;
            CharMatcher[] ascii = utf8Matchers.getAscii();
            CharMatcher[] enc2 = utf8Matchers.getEnc2();
            CharMatcher[] enc3 = utf8Matchers.getEnc2();
            CharMatcher[] enc4 = utf8Matchers.getEnc2();

            final int c = executor.inputReadRaw(locals);

            if (executor.isForward()) {
                if (executor.getInputProfile().profile(c < 128)) {
                    locals.setNextIndex(executor.inputIncRaw(locals.getIndex()));
                    if (ascii != null) {
                        for (int i = 0; i < ascii.length; i++) {
                            if (match(ascii, i, c, compactString)) {
                                return checkMatchSuccessorFoundHook(locals, executor, loopMode, preLoopIndex, i);
                            }
                        }
                    }
                } else {
                    int nBytes = Integer.numberOfLeadingZeros(~(c << 24));
                    assert 1 < nBytes && nBytes < 5 : nBytes;
                    locals.setNextIndex(executor.inputIncRaw(nBytes));
                    int codepoint;
                    switch (nBytes) {
                        case 2:
                            locals.setNextIndex(executor.inputIncRaw(2));
                            if (enc2 != null) {
                                codepoint = ((c & 0x3f) << 6) | (executor.inputReadRaw(locals, locals.getIndex() + 1) & 0x3f);
                                for (int i = 0; i < enc2.length; i++) {
                                    if (match(enc2, i, codepoint, compactString)) {
                                        return checkMatchSuccessorFoundHook(locals, executor, loopMode, preLoopIndex, i);
                                    }
                                }
                            }
                            break;
                        case 3:
                            locals.setNextIndex(executor.inputIncRaw(3));
                            if (enc3 != null) {
                                codepoint = ((c & 0x1f) << 12) | ((executor.inputReadRaw(locals, locals.getIndex() + 1) & 0x3f) << 6) | (executor.inputReadRaw(locals, locals.getIndex() + 2) & 0x3f);
                                for (int i = 0; i < enc3.length; i++) {
                                    if (match(enc3, i, codepoint, compactString)) {
                                        return checkMatchSuccessorFoundHook(locals, executor, loopMode, preLoopIndex, i);
                                    }
                                }
                            }
                            break;
                        case 4:
                            locals.setNextIndex(executor.inputIncRaw(4));
                            if (enc4 != null) {
                                codepoint = ((c & 0x0f) << 18) |
                                                ((executor.inputReadRaw(locals, locals.getIndex() + 1) & 0x3f) << 6) |
                                                ((executor.inputReadRaw(locals, locals.getIndex() + 2) & 0x3f) << 12) |
                                                (executor.inputReadRaw(locals, locals.getIndex() + 3) & 0x3f);
                                for (int i = 0; i < enc4.length; i++) {
                                    if (match(enc4, i, codepoint, compactString)) {
                                        return checkMatchSuccessorFoundHook(locals, executor, loopMode, preLoopIndex, i);
                                    }
                                }
                            }
                            break;
                    }
                }
            }

        } else if (matchers instanceof UTF16RawMatchers) {
            final int c = executor.inputRead(locals);
            CharMatcher[] latin1 = ((UTF16RawMatchers) matchers).getLatin1();
            CharMatcher[] bmp = ((UTF16RawMatchers) matchers).getBmp();
            if (latin1 != null && (bmp == null || compactString || executor.getInputProfile().profile(c < 256))) {
                for (int i = 0; i < latin1.length; i++) {
                    if (match(latin1, i, c, compactString)) {
                        return checkMatchSuccessorFoundHook(locals, executor, loopMode, preLoopIndex, i);
                    }
                }
            } else if (bmp != null) {
                for (int i = 0; i < bmp.length; i++) {
                    if (match(bmp, i, c, compactString)) {
                        return checkMatchSuccessorFoundHook(locals, executor, loopMode, preLoopIndex, i);
                    }
                }
            }
        } else {
            assert matchers instanceof UTF16Matchers;
            assert executor.getEncoding() == Encodings.UTF_16;
            UTF16Matchers utf16Matchers = (UTF16Matchers) matchers;
            CharMatcher[] latin1 = utf16Matchers.getLatin1();
            CharMatcher[] bmp = utf16Matchers.getBmp();
            CharMatcher[] astral = utf16Matchers.getAstral();

            locals.setNextIndex(executor.inputIncRaw(locals.getIndex()));
            int c = executor.inputReadRaw(locals);

            if (utf16MustDecode() && executor.inputUTF16IsHighSurrogate(c) && executor.inputHasNext(locals, locals.getNextIndex())) {
                int c2 = executor.inputReadRaw(locals, locals.getNextIndex());
                if (executor.inputUTF16IsLowSurrogate(c2)) {
                    locals.setNextIndex(executor.inputIncRaw(locals.getNextIndex()));
                    if (astral != null) {
                        c = executor.inputUTF16ToCodePoint(c, c2);
                        for (int i = 0; i < astral.length; i++) {
                            if (match(astral, i, c, compactString)) {
                                return checkMatchSuccessorFoundHook(locals, executor, loopMode, preLoopIndex, i);
                            }
                        }
                    }
                    return checkMatchNoMatch(locals, executor, loopMode, preLoopIndex);
                }
            } else if (latin1 != null && (bmp == null || compactString || executor.getInputProfile().profile(c < 256))) {
                for (int i = 0; i < latin1.length; i++) {
                    if (match(latin1, i, c, compactString)) {
                        return checkMatchSuccessorFoundHook(locals, executor, loopMode, preLoopIndex, i);
                    }
                }
            }
            if (bmp != null) {
                for (int i = 0; i < bmp.length; i++) {
                    if (match(bmp, i, c, compactString)) {
                        return checkMatchSuccessorFoundHook(locals, executor, loopMode, preLoopIndex, i);
                    }
                }
            }
        }
        return checkMatchNoMatch(locals, executor, loopMode, preLoopIndex);
    }

    private boolean checkMatchNoMatch(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean loopMode, int preLoopIndex) {
        if (matchers.getNoMatchSuccessor() == -1) {
            if (loopMode) {
                noSuccessorLoop(locals, executor, preLoopIndex);
            }
            locals.setSuccessorIndex(-1);
            return false;
        } else {
            return checkMatchSuccessorFoundHook(locals, executor, loopMode, preLoopIndex, matchers.getNoMatchSuccessor());
        }
    }

    private static boolean match(CharMatcher[] matchers, int i, final int c, boolean compactString) {
        return matchers[i] != null && matchers[i].execute(c, compactString);
    }

    /**
     * TODO: move code that is executed after finding the successor into separate states of
     * {@link TRegexDFAExecutorNode#execute(TRegexExecutorLocals, boolean)}, for code deduplication.
     */
    private boolean checkMatchSuccessorFoundHook(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean loopMode, int preLoopIndex, int i) {
        CompilerAsserts.partialEvaluationConstant(i);
        locals.setSuccessorIndex(i);
        if (loopMode) {
            successorFoundLoop(locals, executor, i, preLoopIndex);
        } else {
            successorFound(locals, executor, i);
        }
        return isLoopToSelf(i);
    }

    private void checkFinalState(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(locals, executor, false);
            if (simpleCG != null) {
                applySimpleCGFinalTransition(simpleCG.getTransitionToFinalState(), executor, locals);
            }
        }
    }

    /**
     * Gets called if {@link TRegexExecutorNode#getMaxIndex(TRegexExecutorLocals)} is reached (!
     * {@link TRegexExecutorNode#inputHasNext(TRegexExecutorLocals)}). In
     * {@link BackwardDFAStateNode}, execution may still continue here, which is why this method can
     * return a successor index.
     *
     * @param locals a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @return a successor index.
     */
    int atEnd(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        boolean anchored = isAnchoredFinalState() && executor.inputAtEnd(locals);
        if (isFinalState() || anchored) {
            storeResult(locals, executor, anchored);
            if (simpleCG != null) {
                if (isAnchoredFinalState()) {
                    applySimpleCGFinalTransition(simpleCG.getTransitionToAnchoredFinalState(), executor, locals);
                } else if (isFinalState()) {
                    applySimpleCGFinalTransition(simpleCG.getTransitionToFinalState(), executor, locals);
                }
            }
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    void successorFound(TRegexDFAExecutorLocals locals, @SuppressWarnings("unused") TRegexDFAExecutorNode executor, int i) {
        if (simpleCG != null) {
            applySimpleCGTransition(simpleCG.getTransitions()[i], locals);
        }
    }

    /**
     * Hook for {@link CGTrackingDFAStateNode}.
     */
    @SuppressWarnings("unused")
    void successorFoundLoop(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int i, int preLoopIndex) {
    }

    /**
     * Hook for {@link CGTrackingDFAStateNode}.
     */
    @SuppressWarnings("unused")
    void noSuccessorLoop(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int preLoopIndex) {
    }

    void storeResult(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, @SuppressWarnings("unused") boolean anchored) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (executor.isSimpleCG()) {
            if (executor.getProperties().isSimpleCGMustCopy()) {
                System.arraycopy(locals.getCGData().results, 0, locals.getCGData().currentResult, 0, locals.getCGData().currentResult.length);
            }
            locals.setResultInt(0);
        } else {
            locals.setResultInt(locals.getIndex());
        }
    }

    static int[] simpleCGFinalTransitionTargetArray(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        return executor.getProperties().isSimpleCGMustCopy() ? locals.getCGData().currentResult : locals.getCGData().results;
    }

    void applySimpleCGTransition(DFASimpleCGTransition transition, TRegexDFAExecutorLocals locals) {
        transition.apply(locals.getCGData().results, locals.getIndex());
    }

    void applySimpleCGFinalTransition(DFASimpleCGTransition transition, TRegexDFAExecutorNode executor, TRegexDFAExecutorLocals locals) {
        transition.apply(simpleCGFinalTransitionTargetArray(locals, executor), locals.getIndex());
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        DebugUtil.appendNodeId(sb, getId()).append(": ");
        if (!treeTransitionMatching()) {
            sb.append(matchers.size()).append(" successors");
        }
        if (isAnchoredFinalState()) {
            sb.append(", AFS");
        }
        if (isFinalState()) {
            sb.append(", FS");
        }
        sb.append(":\n");
        if (treeTransitionMatching()) {
            sb.append("      ").append(getTreeMatcher()).append("\n      successors: ").append(Arrays.toString(successors)).append("\n");
        } else {
            for (int i = 0; i < matchers.size(); i++) {
                sb.append("      ").append(i).append(": ").append(matchers.toString(i)).append(" -> ");
                DebugUtil.appendNodeId(sb, getSuccessors()[i]).append("\n");
            }
        }
        return sb.toString();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        JsonArray transitions = Json.array();
        if (matchers != null) {
            for (int i = 0; i < matchers.size(); i++) {
                transitions.append(Json.obj(Json.prop("matcher", matchers.toString(i)), Json.prop("target", successors[i])));
            }
        }
        return Json.obj(Json.prop("id", getId()),
                        Json.prop("anchoredFinalState", isAnchoredFinalState()),
                        Json.prop("finalState", isFinalState()),
                        Json.prop("loopToSelf", hasLoopToSelf()),
                        Json.prop("transitions", transitions));
    }
}
