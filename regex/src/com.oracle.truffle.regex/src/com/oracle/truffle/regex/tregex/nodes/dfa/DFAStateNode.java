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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputOps;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public class DFAStateNode extends DFAAbstractStateNode {

    private static final byte FLAG_FINAL_STATE = 1;
    private static final byte FLAG_ANCHORED_FINAL_STATE = 1 << 1;
    private static final byte FLAG_HAS_BACKWARD_PREFIX_STATE = 1 << 2;
    private static final byte FLAG_UTF_16_MUST_DECODE = 1 << 3;

    private final byte flags;
    private final short loopTransitionIndex;
    private final short indexOfNodeId;
    private final byte indexOfIsFast;
    private final Matchers matchers;
    private final DFASimpleCG simpleCG;

    DFAStateNode(DFAStateNode nodeSplitCopy, short copyID) {
        this(copyID, nodeSplitCopy.flags, nodeSplitCopy.loopTransitionIndex, nodeSplitCopy.indexOfNodeId,
                        nodeSplitCopy.indexOfIsFast, Arrays.copyOf(nodeSplitCopy.getSuccessors(), nodeSplitCopy.getSuccessors().length),
                        nodeSplitCopy.getMatchers(), nodeSplitCopy.simpleCG);
    }

    public DFAStateNode(short id, byte flags, short loopTransitionIndex, short indexOfNodeId, byte indexOfIsFast, short[] successors, Matchers matchers, DFASimpleCG simpleCG) {
        super(id, successors);
        assert id > 0;
        this.flags = flags;
        this.loopTransitionIndex = loopTransitionIndex;
        this.indexOfNodeId = indexOfNodeId;
        this.indexOfIsFast = indexOfIsFast;
        this.matchers = matchers;
        this.simpleCG = simpleCG;
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

    public final SequentialMatchers getSequentialMatchers() {
        return (SequentialMatchers) matchers;
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
        return matchers instanceof AllTransitionsInOneTreeMatcher;
    }

    AllTransitionsInOneTreeMatcher getTreeMatcher() {
        return (AllTransitionsInOneTreeMatcher) matchers;
    }

    public boolean hasIndexOfNodeId() {
        return indexOfNodeId >= 0;
    }

    public int getIndexOfNodeId() {
        return indexOfNodeId;
    }

    /**
     * Returns {@code true} if this state has a {@code TruffleString.ByteIndexOfCodePointSetNode}.
     */
    boolean canDoIndexOf(TruffleString.CodeRange codeRange) {
        CompilerAsserts.partialEvaluationConstant(codeRange);
        CompilerAsserts.partialEvaluationConstant(codeRange.ordinal());
        return hasLoopToSelf() && hasIndexOfNodeId() && (indexOfIsFast & (1 << codeRange.ordinal())) != 0;
    }

    /**
     * Gets called when a new DFA state is entered.
     */
    void beforeFindSuccessor(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        checkFinalState(locals, executor);
    }

    /**
     * Gets called after every call to
     * {@link InputOps#indexOf(TruffleString, int, int, TruffleString.CodePointSet, Encodings.Encoding, TruffleString.ByteIndexOfCodePointSetNode)}
     * which we call an {@code indexOf}-operation.
     *
     * @param preLoopIndex the starting index of the {@code indexOf}-operation.
     * @param postLoopIndex the index found by the {@code indexOf}-operation. If the {@code indexOf}
     *            -operation did not find a match, this value is equal to
     *            {@link TRegexDFAExecutorLocals#getMaxIndex()}.
     * @param codeRange
     */
    void afterIndexOf(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, final int preLoopIndex, int postLoopIndex, TruffleString.CodeRange codeRange) {
        locals.setIndex(postLoopIndex);
        if (simpleCG != null && locals.getIndex() > preLoopIndex) {
            int curIndex = locals.getIndex();
            executor.inputSkipReverse(locals, codeRange);
            applySimpleCGTransition(simpleCG.getTransitions()[getLoopToSelf()], executor, locals);
            locals.setIndex(curIndex);
        }
        checkFinalState(locals, executor);
    }

    /**
     * Save the current result iff we are in a final state.
     */
    private void checkFinalState(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(simpleCG);
        if (isFinalState()) {
            if (simpleCG == null) {
                storeResult(locals, executor, false);
            } else if (!(isAnchoredFinalState() && executor.inputAtEnd(locals))) {
                storeResult(locals, executor, false);
                applySimpleCGFinalTransition(simpleCG.getTransitionToFinalState(), executor, locals);
            }
        }
    }

    /**
     * Gets called if {@link TRegexExecutorNode#getMaxIndex(TRegexExecutorLocals)} is reached (!
     * {@link TRegexExecutorNode#inputHasNext(TRegexExecutorLocals)}).
     */
    void atEnd(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
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
    }

    /**
     * Gets called when a matching transition was found.
     */
    void successorFound(TRegexDFAExecutorLocals locals, @SuppressWarnings("unused") TRegexDFAExecutorNode executor, int i) {
        if (simpleCG != null) {
            applySimpleCGTransition(simpleCG.getTransitions()[i], executor, locals);
        }
    }

    /**
     * Saves the current result (single index or all capture group boundaries).
     */
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

    void applySimpleCGTransition(DFASimpleCGTransition transition, TRegexDFAExecutorNode executor, TRegexDFAExecutorLocals locals) {
        int index = executor.isForward() ? locals.getIndex() : locals.getNextIndex();
        transition.apply(locals.getCGData().results, index, executor.getProperties().tracksLastGroup(), executor.isForward());
    }

    void applySimpleCGFinalTransition(DFASimpleCGTransition transition, TRegexDFAExecutorNode executor, TRegexDFAExecutorLocals locals) {
        transition.applyFinal(locals.getCGData(), locals.getIndex(), executor.getProperties().isSimpleCGMustCopy(), executor.getProperties().tracksLastGroup(), executor.isForward());
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        DebugUtil.appendNodeId(sb, getId()).append(": ");
        if (!treeTransitionMatching()) {
            sb.append(getSequentialMatchers().size()).append(" successors");
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
            for (int i = 0; i < getSequentialMatchers().size(); i++) {
                sb.append("      ").append(i).append(": ").append(getSequentialMatchers().toString(i)).append(" -> ");
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
            for (int i = 0; i < getSequentialMatchers().size(); i++) {
                transitions.append(Json.obj(Json.prop("matcher", getSequentialMatchers().toString(i)), Json.prop("target", successors[i])));
            }
        }
        return Json.obj(Json.prop("id", getId()),
                        Json.prop("anchoredFinalState", isAnchoredFinalState()),
                        Json.prop("finalState", isFinalState()),
                        Json.prop("loopToSelf", hasLoopToSelf()),
                        Json.prop("transitions", transitions));
    }
}
