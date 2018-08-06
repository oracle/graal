/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.regex.tregex.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.nodes.input.InputIndexOfNode;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.Arrays;

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

        private InputIndexOfNode getIndexOfNode() {
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

    private final short id;
    private final byte flags;
    @Child private LoopOptimizationNode loopOptimizationNode;
    @CompilationFinal(dimensions = 1) protected final CharMatcher[] matchers;

    DFAStateNode(DFAStateNode nodeSplitCopy, short copyID) {
        this(copyID, nodeSplitCopy.flags, nodeSplitCopy.loopOptimizationNode.nodeSplitCopy(),
                        Arrays.copyOf(nodeSplitCopy.getSuccessors(), nodeSplitCopy.getSuccessors().length),
                        nodeSplitCopy.getMatchers());
    }

    public DFAStateNode(short id, byte flags, LoopOptimizationNode loopOptimizationNode, short[] successors, CharMatcher[] matchers) {
        super(successors);
        assert id > 0;
        this.id = id;
        this.flags = flags;
        this.loopOptimizationNode = loopOptimizationNode;
        this.matchers = matchers;
    }

    public static byte flags(boolean finalState, boolean anchoredFinalState, boolean hasBackwardPrefixState) {
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

    public static LoopOptimizationNode loopOptimizationNode(short loopTransitionIndex, char[] indexOfChars) {
        return new LoopOptimizationNode(loopTransitionIndex, indexOfChars);
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new DFAStateNode(this, copyID);
    }

    @Override
    public short getId() {
        return id;
    }

    public CharMatcher[] getMatchers() {
        return matchers;
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

    private boolean treeTransitionMatching() {
        return matchers.length > 0 && matchers[matchers.length - 1] instanceof AllTransitionsInOneTreeMatcher;
    }

    private AllTransitionsInOneTreeMatcher getTreeMatcher() {
        return (AllTransitionsInOneTreeMatcher) matchers[matchers.length - 1];
    }

    private boolean sameResultAsRegularMatchers(TRegexDFAExecutorNode executor, char c, int allTransitionsMatcherResult) {
        CompilerAsserts.neverPartOfCompilation();
        if (executor.isRegressionTestMode()) {
            for (int i = 0; i < matchers.length - 1; i++) {
                if (matchers[i].match(c)) {
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
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     */
    @Override
    public void executeFindSuccessor(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        beforeFindSuccessor(frame, executor);
        if (!executor.hasNext(frame)) {
            executor.setSuccessorIndex(frame, atEnd1(frame, executor));
            return;
        }
        if (checkMatch1(frame, executor)) {
            if (executor.hasNext(frame)) {
                if (!checkMatch2(frame, executor)) {
                    return;
                }
            } else {
                executor.setSuccessorIndex(frame, atEnd2(frame, executor));
                return;
            }
            final int preLoopIndex = executor.getIndex(frame);
            if (executor.isForward() && hasLoopToSelf() && loopOptimizationNode.indexOfChars != null) {
                int indexOfResult = loopOptimizationNode.getIndexOfNode().execute(executor.getInput(frame),
                                preLoopIndex,
                                executor.getCurMaxIndex(frame),
                                loopOptimizationNode.indexOfChars);
                if (indexOfResult < 0) {
                    executor.setIndex(frame, executor.getCurMaxIndex(frame));
                    executor.setSuccessorIndex(frame, atEnd3(frame, executor, preLoopIndex));
                    return;
                } else {
                    if (successors.length == 2) {
                        int successor = (getLoopToSelf() + 1) % 2;
                        CompilerAsserts.partialEvaluationConstant(successor);
                        executor.setIndex(frame, indexOfResult + 1);
                        executor.setSuccessorIndex(frame, successor);
                        successorFound3(frame, executor, successor, preLoopIndex);
                        return;
                    } else {
                        executor.setIndex(frame, indexOfResult);
                    }
                }
            }
            while (executor.hasNext(frame)) {
                if (!checkMatch3(frame, executor, preLoopIndex)) {
                    return;
                }
            }
            executor.setSuccessorIndex(frame, atEnd3(frame, executor, preLoopIndex));
        }
    }

    /**
     * Finds the first matching transition. If a transition matches,
     * {@link #successorFound1(VirtualFrame, TRegexDFAExecutorNode, int)} is called. The index of
     * the element of {@link #getMatchers()} that matched the current input character (
     * {@link TRegexDFAExecutorNode#getChar(VirtualFrame)}) or {@link #FS_RESULT_NO_SUCCESSOR} is
     * stored via {@link TRegexDFAExecutorNode#setSuccessorIndex(VirtualFrame, int)}.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch1(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        final char c = executor.getChar(frame);
        executor.advance(frame);
        if (treeTransitionMatching()) {
            int successor = getTreeMatcher().checkMatchTree1(frame, executor, this, c);
            assert sameResultAsRegularMatchers(executor, c, successor) : this.toString();
            executor.setSuccessorIndex(frame, successor);
            return isLoopToSelf(successor);
        } else {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].match(c)) {
                    CompilerAsserts.partialEvaluationConstant(i);
                    successorFound1(frame, executor, i);
                    executor.setSuccessorIndex(frame, i);
                    return isLoopToSelf(i);
                }
            }
            executor.setSuccessorIndex(frame, FS_RESULT_NO_SUCCESSOR);
            return false;
        }
    }

    /**
     * Finds the first matching transition. This method is called only if the transition found by
     * {@link #checkMatch1(VirtualFrame, TRegexDFAExecutorNode)} was a loop back to this state
     * (indicated by {@link #isLoopToSelf(int)}). If a transition <i>other than</i> the looping
     * transition matches, {@link #successorFound2(VirtualFrame, TRegexDFAExecutorNode, int)} is
     * called. The index of the element of {@link #getMatchers()} that matched the current input
     * character ( {@link TRegexDFAExecutorNode#getChar(VirtualFrame)}) or
     * {@link #FS_RESULT_NO_SUCCESSOR} is stored via
     * {@link TRegexDFAExecutorNode#setSuccessorIndex(VirtualFrame, int)}. If no transition matches,
     * {@link #noSuccessor2(VirtualFrame, TRegexDFAExecutorNode)} is called.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch2(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        final char c = executor.getChar(frame);
        executor.advance(frame);
        if (treeTransitionMatching()) {
            int successor = getTreeMatcher().checkMatchTree2(frame, executor, this, c);
            assert sameResultAsRegularMatchers(executor, c, successor) : this.toString();
            executor.setSuccessorIndex(frame, successor);
            return isLoopToSelf(successor);
        } else {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].match(c)) {
                    executor.setSuccessorIndex(frame, i);
                    if (!isLoopToSelf(i)) {
                        CompilerAsserts.partialEvaluationConstant(i);
                        successorFound2(frame, executor, i);
                        return false;
                    }
                    return true;
                }
            }
            executor.setSuccessorIndex(frame, FS_RESULT_NO_SUCCESSOR);
            noSuccessor2(frame, executor);
            return false;
        }
    }

    /**
     * Finds the first matching transition. This method is called only if the transitions found by
     * {@link #checkMatch1(VirtualFrame, TRegexDFAExecutorNode)} AND
     * {@link #checkMatch2(VirtualFrame, TRegexDFAExecutorNode)} both were a loop back to this state
     * (indicated by {@link #isLoopToSelf(int)}), and will be called in a loop until a transition
     * other than the loop back transition matches. If a transition <i>other than</i> the looping
     * transition matches, {@link #successorFound3(VirtualFrame, TRegexDFAExecutorNode, int, int)}
     * is called. The index of the element of {@link #getMatchers()} that matched the current input
     * character ({@link TRegexDFAExecutorNode#getChar(VirtualFrame)}) or
     * {@link #FS_RESULT_NO_SUCCESSOR} is stored via
     * {@link TRegexDFAExecutorNode#setSuccessorIndex(VirtualFrame, int)}. If no transition matches,
     * {@link #noSuccessor3(VirtualFrame, TRegexDFAExecutorNode, int)} is called.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param preLoopIndex the index pointed to by
     *            {@link TRegexDFAExecutorNode#getIndex(VirtualFrame)} <i>before</i> this method is
     *            called for the first time.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        final char c = executor.getChar(frame);
        executor.advance(frame);
        if (treeTransitionMatching()) {
            int successor = getTreeMatcher().checkMatchTree3(frame, executor, this, c, preLoopIndex);
            assert sameResultAsRegularMatchers(executor, c, successor) : this.toString();
            executor.setSuccessorIndex(frame, successor);
            return isLoopToSelf(successor);
        } else {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].match(c)) {
                    executor.setSuccessorIndex(frame, i);
                    if (!isLoopToSelf(i)) {
                        CompilerAsserts.partialEvaluationConstant(i);
                        successorFound3(frame, executor, i, preLoopIndex);
                        return false;
                    }
                    return true;
                }
            }
            executor.setSuccessorIndex(frame, FS_RESULT_NO_SUCCESSOR);
            noSuccessor3(frame, executor, preLoopIndex);
            return false;
        }
    }

    /**
     * Gets called at the very beginning of
     * {@link #executeFindSuccessor(VirtualFrame, TRegexDFAExecutorNode)}.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     */
    protected void beforeFindSuccessor(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(frame, executor, curIndex(frame, executor), false);
        }
    }

    /**
     * Gets called when {@link #checkMatch1(VirtualFrame, TRegexDFAExecutorNode)} finds a successor.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param i the index of the matching transition (corresponds to an entry in
     *            {@link #getMatchers()} and {@link #getSuccessors()}).
     */
    protected void successorFound1(VirtualFrame frame, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
    }

    /**
     * Gets called if the end of the input is reached (!
     * {@link TRegexDFAExecutorNode#hasNext(VirtualFrame)}) before
     * {@link #checkMatch1(VirtualFrame, TRegexDFAExecutorNode)} is called. In
     * {@link BackwardDFAStateNode}, execution may still continue here, which is why this method can
     * return a successor index.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @return a successor index.
     */
    protected int atEnd1(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isAnchoredFinalState() && executor.atEnd(frame)) {
            storeResult(frame, executor, curIndex(frame, executor), true);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    /**
     * Gets called when {@link #checkMatch2(VirtualFrame, TRegexDFAExecutorNode)} finds a successor
     * <i>other than</i> the looping transition indicated by {@link #isLoopToSelf(int)}.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param i the index of the matching transition (corresponds to an entry in
     *            {@link #getMatchers()} and {@link #getSuccessors()}).
     */
    protected void successorFound2(VirtualFrame frame, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(frame, executor, prevIndex(frame, executor), false);
        }
    }

    /**
     * Gets called if {@link #checkMatch2(VirtualFrame, TRegexDFAExecutorNode)} does not find a
     * successor.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     */
    protected void noSuccessor2(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(frame, executor, prevIndex(frame, executor), false);
        }
    }

    /**
     * Gets called if the end of the input is reached (!
     * {@link TRegexDFAExecutorNode#hasNext(VirtualFrame)}) directly after
     * {@link #checkMatch1(VirtualFrame, TRegexDFAExecutorNode)} is called. In
     * {@link BackwardDFAStateNode}, execution may still continue here, which is why this method can
     * return a successor index.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @return a successor index.
     */
    protected int atEnd2(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        boolean anchored = isAnchoredFinalState() && executor.atEnd(frame);
        if (isFinalState() || anchored) {
            storeResult(frame, executor, curIndex(frame, executor), anchored);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    /**
     * Gets called when {@link #checkMatch2(VirtualFrame, TRegexDFAExecutorNode)} finds a successor
     * <i>other than</i> the looping transition indicated by {@link #isLoopToSelf(int)}.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param i the index of the matching transition (corresponds to an entry in
     *            {@link #getMatchers()} and {@link #getSuccessors()}).
     * @param preLoopIndex the index pointed to by
     *            {@link TRegexDFAExecutorNode#getIndex(VirtualFrame)} <i>before</i>
     *            {@link #checkMatch3(VirtualFrame, TRegexDFAExecutorNode, int)} is called for the
     *            first time.
     */
    protected void successorFound3(VirtualFrame frame, TRegexDFAExecutorNode executor, int i, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(frame, executor, prevIndex(frame, executor), false);
        }
    }

    /**
     * Gets called if {@link #checkMatch3(VirtualFrame, TRegexDFAExecutorNode, int)} does not find a
     * successor.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param preLoopIndex the index pointed to by
     *            {@link TRegexDFAExecutorNode#getIndex(VirtualFrame)} <i>before</i>
     *            {@link #checkMatch3(VirtualFrame, TRegexDFAExecutorNode, int)} is called for the
     *            first time.
     */
    protected void noSuccessor3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(frame, executor, prevIndex(frame, executor), false);
        }
    }

    /**
     * Gets called if the end of the input is reached (!
     * {@link TRegexDFAExecutorNode#hasNext(VirtualFrame)}) in the loop that calls
     * {@link #checkMatch3(VirtualFrame, TRegexDFAExecutorNode, int)}. In
     * {@link BackwardDFAStateNode}, execution may still continue here, which is why this method can
     * return a successor index.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param preLoopIndex the index pointed to by
     *            {@link TRegexDFAExecutorNode#getIndex(VirtualFrame)} <i>before</i>
     *            {@link #checkMatch3(VirtualFrame, TRegexDFAExecutorNode, int)} is called for the
     *            first time.
     * @return a successor index.
     */
    protected int atEnd3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        boolean anchored = isAnchoredFinalState() && executor.atEnd(frame);
        if (isFinalState() || anchored) {
            storeResult(frame, executor, curIndex(frame, executor), anchored);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    protected void storeResult(VirtualFrame frame, TRegexDFAExecutorNode executor, int index, @SuppressWarnings("unused") boolean anchored) {
        CompilerAsserts.partialEvaluationConstant(this);
        executor.setResultInt(frame, index);
    }

    protected int curIndex(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return executor.getIndex(frame);
    }

    protected int prevIndex(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return executor.getIndex(frame) - 1;
    }

    protected int nextIndex(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return executor.getIndex(frame) + 1;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        DebugUtil.appendNodeId(sb, id).append(": ");
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
            sb.append("      ").append(matchers[0]).append("\n      successors: ").append(Arrays.toString(successors)).append("\n");
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
        for (int i = 0; i < matchers.length; i++) {
            transitions.append(Json.obj(Json.prop("matcher", matchers[i].toString()), Json.prop("target", successors[i])));
        }
        return Json.obj(Json.prop("id", id),
                        Json.prop("anchoredFinalState", isAnchoredFinalState()),
                        Json.prop("finalState", isFinalState()),
                        Json.prop("loopToSelf", hasLoopToSelf()),
                        Json.prop("transitions", transitions));
    }
}
