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
import com.oracle.truffle.regex.tregex.nodes.input.InputIndexOfNode;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public class DFAStateNode extends DFAAbstractStateNode {

    public static class LoopOptimizationNode extends Node {

        private final short loopTransitionIndex;
        @CompilationFinal(dimensions = 1) private final char[] indexOfChars;
        @Child private InputIndexOfNode indexOfNode;

        public LoopOptimizationNode(short loopTransitionIndex, char[] indexOfChars) {
            this.loopTransitionIndex = loopTransitionIndex;
            this.indexOfChars = indexOfChars;
        }

        private LoopOptimizationNode nodeSplitCopy() {
            return new LoopOptimizationNode(loopTransitionIndex, indexOfChars);
        }

        public char[] getIndexOfChars() {
            return indexOfChars;
        }

        public InputIndexOfNode getIndexOfNode() {
            if (indexOfNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                indexOfNode = insert(InputIndexOfNode.create());
            }
            return indexOfNode;
        }
    }

    private static final byte FLAG_FINAL_STATE = 1;
    private static final byte FLAG_ANCHORED_FINAL_STATE = 1 << 1;
    private static final byte FLAG_HAS_BACKWARD_PREFIX_STATE = 1 << 2;

    private final byte flags;
    @Child LoopOptimizationNode loopOptimizationNode;
    @Children protected final CharMatcher[] matchers;
    private final DFASimpleCG simpleCG;
    private final AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher;
    private final BranchProfile stateReachedProfile = BranchProfile.create();

    DFAStateNode(DFAStateNode nodeSplitCopy, short copyID) {
        this(copyID, nodeSplitCopy.flags, nodeSplitCopy.loopOptimizationNode.nodeSplitCopy(),
                        Arrays.copyOf(nodeSplitCopy.getSuccessors(), nodeSplitCopy.getSuccessors().length),
                        nodeSplitCopy.getMatchers(), nodeSplitCopy.simpleCG, nodeSplitCopy.allTransitionsInOneTreeMatcher);
    }

    public DFAStateNode(short id, byte flags, LoopOptimizationNode loopOptimizationNode, short[] successors, CharMatcher[] matchers, DFASimpleCG simpleCG,
                    AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher) {
        super(id, successors);
        assert id > 0;
        this.flags = flags;
        this.loopOptimizationNode = loopOptimizationNode;
        this.matchers = matchers;
        this.simpleCG = simpleCG;
        this.allTransitionsInOneTreeMatcher = allTransitionsInOneTreeMatcher;
    }

    public static byte buildFlags(boolean finalState, boolean anchoredFinalState, boolean hasBackwardPrefixState) {
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
        return flags;
    }

    public static LoopOptimizationNode buildLoopOptimizationNode(short loopTransitionIndex, char[] indexOfChars) {
        return new LoopOptimizationNode(loopTransitionIndex, indexOfChars);
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new DFAStateNode(this, copyID);
    }

    public final CharMatcher[] getMatchers() {
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

    private boolean flagIsSet(byte flag) {
        return (flags & flag) != 0;
    }

    public boolean hasLoopToSelf() {
        return loopOptimizationNode != null;
    }

    boolean isLoopToSelf(int transitionIndex) {
        return hasLoopToSelf() && transitionIndex == getLoopToSelf();
    }

    short getLoopToSelf() {
        assert hasLoopToSelf();
        return loopOptimizationNode.loopTransitionIndex;
    }

    boolean treeTransitionMatching() {
        return allTransitionsInOneTreeMatcher != null;
    }

    AllTransitionsInOneTreeMatcher getTreeMatcher() {
        return allTransitionsInOneTreeMatcher;
    }

    boolean sameResultAsRegularMatchers(TRegexDFAExecutorNode executor, char c, boolean compactString, int allTransitionsMatcherResult) {
        CompilerAsserts.neverPartOfCompilation();
        if (executor.isRegressionTestMode()) {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].execute(c, compactString)) {
                    return i == allTransitionsMatcherResult;
                }
            }
            return allTransitionsMatcherResult == -1;
        }
        return true;
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
            if (executor.isForward() && loopOptimizationNode.indexOfChars != null) {
                runIndexOf(locals, executor, compactString);
            } else {
                while (executor.hasNext(locals)) {
                    if (executor.isSimpleCG()) {
                        // we have to write the final state transition before anything else in
                        // simpleCG mode
                        checkFinalState(locals, executor, curIndex(locals));
                    }
                    if (!checkMatch(locals, executor, compactString)) {
                        if (!executor.isSimpleCG()) {
                            // in ignore-capture-groups mode, we can delay the final state check
                            checkFinalState(locals, executor, prevIndex(locals));
                        }
                        return;
                    }
                }
                locals.setSuccessorIndex(atEnd(locals, executor));
            }
        } else {
            if (!executor.hasNext(locals)) {
                locals.setSuccessorIndex(atEnd(locals, executor));
                return;
            }
            checkFinalState(locals, executor, curIndex(locals));
            checkMatch(locals, executor, compactString);
        }
    }

    private void runIndexOf(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        final int preLoopIndex = locals.getIndex();
        int indexOfResult = loopOptimizationNode.getIndexOfNode().execute(locals.getInput(),
                        preLoopIndex,
                        locals.getCurMaxIndex(),
                        loopOptimizationNode.indexOfChars);
        if (indexOfResult < 0) {
            if (simpleCG != null && locals.getCurMaxIndex() > preLoopIndex) {
                applySimpleCGTransition(simpleCG.getTransitions()[getLoopToSelf()], locals, locals.getCurMaxIndex() - 1);
            }
            locals.setIndex(locals.getCurMaxIndex());
            locals.setSuccessorIndex(atEnd(locals, executor));
        } else {
            if (simpleCG != null && indexOfResult > preLoopIndex) {
                applySimpleCGTransition(simpleCG.getTransitions()[getLoopToSelf()], locals, indexOfResult - 1);
            }
            checkFinalState(locals, executor, indexOfResult);
            if (successors.length == 2) {
                int successor = (getLoopToSelf() + 1) % 2;
                CompilerAsserts.partialEvaluationConstant(successor);
                if (simpleCG != null) {
                    applySimpleCGTransition(simpleCG.getTransitions()[successor], locals, indexOfResult);
                }
                locals.setIndex(indexOfResult + 1);
                locals.setSuccessorIndex(successor);
            } else {
                locals.setIndex(indexOfResult);
                checkMatch(locals, executor, compactString);
            }
        }
    }

    /**
     * Finds the first matching transition. The index of the element of {@link #getMatchers()} that
     * matched the current input character (
     * {@link TRegexExecutorNode#getChar(TRegexExecutorLocals)}) or {@link #FS_RESULT_NO_SUCCESSOR}
     * is stored via {@link TRegexDFAExecutorLocals#setSuccessorIndex(int)}.
     *
     * @param locals a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        final char c = executor.getChar(locals);
        executor.advance(locals);
        if (treeTransitionMatching()) {
            int successor = getTreeMatcher().checkMatchTree(locals, executor, this, c);
            assert sameResultAsRegularMatchers(executor, c, compactString, successor) : this.toString();
            locals.setSuccessorIndex(successor);
            return isLoopToSelf(successor);
        } else {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].execute(c, compactString)) {
                    CompilerAsserts.partialEvaluationConstant(i);
                    locals.setSuccessorIndex(i);
                    successorFound(locals, executor, i);
                    return isLoopToSelf(i);
                }
            }
            locals.setSuccessorIndex(FS_RESULT_NO_SUCCESSOR);
            return false;
        }
    }

    protected void checkFinalState(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int index) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(locals, executor, index, false);
            if (simpleCG != null) {
                applySimpleCGFinalTransition(simpleCG.getTransitionToFinalState(), executor, locals, index);
            }
        }
    }

    /**
     * Gets called if {@link TRegexDFAExecutorLocals#getCurMaxIndex()} is reached (!
     * {@link TRegexDFAExecutorNode#hasNext(TRegexDFAExecutorLocals)}). In
     * {@link BackwardDFAStateNode}, execution may still continue here, which is why this method can
     * return a successor index.
     *
     * @param locals a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @return a successor index.
     */
    int atEnd(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        boolean anchored = isAnchoredFinalState() && executor.atEnd(locals);
        if (isFinalState() || anchored) {
            storeResult(locals, executor, curIndex(locals), anchored);
            if (simpleCG != null) {
                if (isAnchoredFinalState()) {
                    applySimpleCGFinalTransition(simpleCG.getTransitionToAnchoredFinalState(), executor, locals, curIndex(locals));
                } else if (isFinalState()) {
                    applySimpleCGFinalTransition(simpleCG.getTransitionToFinalState(), executor, locals, curIndex(locals));
                }
            }
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    void successorFound(TRegexDFAExecutorLocals locals, @SuppressWarnings("unused") TRegexDFAExecutorNode executor, int i) {
        if (simpleCG != null) {
            applySimpleCGTransition(simpleCG.getTransitions()[i], locals, prevIndex(locals));
        }
    }

    void storeResult(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int index, @SuppressWarnings("unused") boolean anchored) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (executor.isSimpleCG()) {
            if (executor.getProperties().isSimpleCGMustCopy()) {
                System.arraycopy(locals.getCGData().results, 0, locals.getCGData().currentResult, 0, locals.getCGData().currentResult.length);
            }
            locals.setResultInt(0);
        } else {
            locals.setResultInt(index);
        }
    }

    static int[] simpleCGFinalTransitionTargetArray(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        return executor.getProperties().isSimpleCGMustCopy() ? locals.getCGData().currentResult : locals.getCGData().results;
    }

    int curIndex(TRegexDFAExecutorLocals locals) {
        CompilerAsserts.partialEvaluationConstant(this);
        return locals.getIndex();
    }

    int prevIndex(TRegexDFAExecutorLocals locals) {
        CompilerAsserts.partialEvaluationConstant(this);
        return locals.getIndex() - 1;
    }

    int nextIndex(TRegexDFAExecutorLocals locals) {
        CompilerAsserts.partialEvaluationConstant(this);
        return locals.getIndex() + 1;
    }

    void applySimpleCGTransition(DFASimpleCGTransition transition, TRegexDFAExecutorLocals locals, int index) {
        transition.apply(locals.getCGData().results, index);
    }

    void applySimpleCGFinalTransition(DFASimpleCGTransition transition, @SuppressWarnings("unused") TRegexDFAExecutorNode executor, TRegexDFAExecutorLocals locals, int index) {
        transition.apply(simpleCGFinalTransitionTargetArray(locals, executor), index);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        DebugUtil.appendNodeId(sb, getId()).append(": ");
        if (!treeTransitionMatching()) {
            sb.append(matchers.length).append(" successors");
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
            for (int i = 0; i < matchers.length; i++) {
                sb.append("      ").append(i).append(": ").append(matchers[i]).append(" -> ");
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
            for (int i = 0; i < matchers.length; i++) {
                transitions.append(Json.obj(Json.prop("matcher", matchers[i].toString()), Json.prop("target", successors[i])));
            }
        }
        return Json.obj(Json.prop("id", getId()),
                        Json.prop("anchoredFinalState", isAnchoredFinalState()),
                        Json.prop("finalState", isFinalState()),
                        Json.prop("loopToSelf", hasLoopToSelf()),
                        Json.prop("transitions", transitions));
    }
}
