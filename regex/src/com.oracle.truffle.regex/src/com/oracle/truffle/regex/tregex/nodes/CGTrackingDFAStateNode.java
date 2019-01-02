/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;

public class CGTrackingDFAStateNode extends DFAStateNode {

    private final DFACaptureGroupPartialTransitionNode anchoredFinalStateTransition;
    private final DFACaptureGroupPartialTransitionNode unAnchoredFinalStateTransition;

    @CompilationFinal(dimensions = 1) private final short[] captureGroupTransitions;

    @CompilationFinal(dimensions = 1) private final short[] precedingCaptureGroupTransitions;

    @Child private DFACaptureGroupPartialTransitionDispatchNode transitionDispatchNode;

    public CGTrackingDFAStateNode(short id, byte flags, LoopOptimizationNode loopOptimizationNode, short[] successors, CharMatcher[] matchers,
                    AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher, short[] captureGroupTransitions,
                    short[] precedingCaptureGroupTransitions,
                    DFACaptureGroupPartialTransitionNode anchoredFinalStateTransition,
                    DFACaptureGroupPartialTransitionNode unAnchoredFinalStateTransition) {
        super(id, flags, loopOptimizationNode, successors, matchers, allTransitionsInOneTreeMatcher);
        this.captureGroupTransitions = captureGroupTransitions;
        this.precedingCaptureGroupTransitions = precedingCaptureGroupTransitions;
        transitionDispatchNode = precedingCaptureGroupTransitions.length > 1 ? DFACaptureGroupPartialTransitionDispatchNode.create(precedingCaptureGroupTransitions) : null;
        this.anchoredFinalStateTransition = anchoredFinalStateTransition;
        this.unAnchoredFinalStateTransition = unAnchoredFinalStateTransition;
    }

    private CGTrackingDFAStateNode(CGTrackingDFAStateNode copy, short copyID) {
        super(copy, copyID);
        this.captureGroupTransitions = copy.captureGroupTransitions;
        this.precedingCaptureGroupTransitions = copy.precedingCaptureGroupTransitions;
        this.anchoredFinalStateTransition = copy.anchoredFinalStateTransition;
        this.unAnchoredFinalStateTransition = copy.unAnchoredFinalStateTransition;
        transitionDispatchNode = precedingCaptureGroupTransitions.length > 1 ? DFACaptureGroupPartialTransitionDispatchNode.create(precedingCaptureGroupTransitions) : null;
    }

    private DFACaptureGroupLazyTransitionNode getCGTransitionToSelf(TRegexDFAExecutorNode executor) {
        return executor.getCGTransitions()[captureGroupTransitions[getLoopToSelf()]];
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new CGTrackingDFAStateNode(this, copyID);
    }

    @Override
    public void executeFindSuccessor(VirtualFrame frame, TRegexDFAExecutorNode executor, boolean compactString) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(compactString);
        beforeFindSuccessor(frame, executor);
        if (!executor.hasNext(frame)) {
            executor.setSuccessorIndex(frame, atEnd1(frame, executor));
            return;
        }
        if (checkMatch1(frame, executor, compactString)) {
            if (executor.hasNext(frame)) {
                if (!checkMatch2(frame, executor, compactString)) {
                    return;
                }
            } else {
                executor.setSuccessorIndex(frame, atEnd2(frame, executor));
                return;
            }
            final int preLoopIndex = executor.getIndex(frame);
            if (executor.isForward() && hasLoopToSelf() && loopOptimizationNode.getIndexOfChars() != null) {
                int indexOfResult = loopOptimizationNode.getIndexOfNode().execute(executor.getInput(frame),
                                preLoopIndex,
                                executor.getCurMaxIndex(frame),
                                loopOptimizationNode.getIndexOfChars());
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
                if (!checkMatch3(frame, executor, compactString, preLoopIndex)) {
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
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch1(VirtualFrame frame, TRegexDFAExecutorNode executor, boolean compactString) {
        final char c = executor.getChar(frame);
        executor.advance(frame);
        if (treeTransitionMatching()) {
            int successor = getTreeMatcher().checkMatchTree1(frame, executor, this, c);
            assert sameResultAsRegularMatchers(executor, c, compactString, successor) : this.toString();
            executor.setSuccessorIndex(frame, successor);
            return isLoopToSelf(successor);
        } else {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].execute(c, compactString)) {
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
     * {@link #checkMatch1(VirtualFrame, TRegexDFAExecutorNode, boolean)} was a loop back to this
     * state (indicated by {@link #isLoopToSelf(int)}). If a transition <i>other than</i> the
     * looping transition matches,
     * {@link #successorFound2(VirtualFrame, TRegexDFAExecutorNode, int)} is called. The index of
     * the element of {@link #getMatchers()} that matched the current input character (
     * {@link TRegexDFAExecutorNode#getChar(VirtualFrame)}) or {@link #FS_RESULT_NO_SUCCESSOR} is
     * stored via {@link TRegexDFAExecutorNode#setSuccessorIndex(VirtualFrame, int)}. If no
     * transition matches, {@link #noSuccessor2(VirtualFrame, TRegexDFAExecutorNode)} is called.
     *
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch2(VirtualFrame frame, TRegexDFAExecutorNode executor, boolean compactString) {
        final char c = executor.getChar(frame);
        executor.advance(frame);
        if (treeTransitionMatching()) {
            int successor = getTreeMatcher().checkMatchTree2(frame, executor, this, c);
            assert sameResultAsRegularMatchers(executor, c, compactString, successor) : this.toString();
            executor.setSuccessorIndex(frame, successor);
            return isLoopToSelf(successor);
        } else {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].execute(c, compactString)) {
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
     * {@link #checkMatch1(VirtualFrame, TRegexDFAExecutorNode, boolean)} AND
     * {@link #checkMatch2(VirtualFrame, TRegexDFAExecutorNode, boolean)} both were a loop back to
     * this state (indicated by {@link #isLoopToSelf(int)}), and will be called in a loop until a
     * transition other than the loop back transition matches. If a transition <i>other than</i> the
     * looping transition matches,
     * {@link #successorFound3(VirtualFrame, TRegexDFAExecutorNode, int, int)} is called. The index
     * of the element of {@link #getMatchers()} that matched the current input character (
     * {@link TRegexDFAExecutorNode#getChar(VirtualFrame)}) or {@link #FS_RESULT_NO_SUCCESSOR} is
     * stored via {@link TRegexDFAExecutorNode#setSuccessorIndex(VirtualFrame, int)}. If no
     * transition matches, {@link #noSuccessor3(VirtualFrame, TRegexDFAExecutorNode, int)} is
     * called.
     *
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     * @param preLoopIndex the index pointed to by
     *            {@link TRegexDFAExecutorNode#getIndex(VirtualFrame)} <i>before</i> this method is
     *            called for the first time.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch3(VirtualFrame frame, TRegexDFAExecutorNode executor, boolean compactString, int preLoopIndex) {
        final char c = executor.getChar(frame);
        executor.advance(frame);
        if (treeTransitionMatching()) {
            int successor = getTreeMatcher().checkMatchTree3(frame, executor, this, c, preLoopIndex);
            assert sameResultAsRegularMatchers(executor, c, compactString, successor) : this.toString();
            executor.setSuccessorIndex(frame, successor);
            return isLoopToSelf(successor);
        } else {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].execute(c, compactString)) {
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

    private void beforeFindSuccessor(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (executor.isSearching()) {
            checkFinalState(frame, executor);
        }
    }

    void successorFound1(VirtualFrame frame, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        if (precedingCaptureGroupTransitions.length == 1) {
            executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getPartialTransitions()[i].apply(executor, executor.getCGData(frame), prevIndex(frame, executor));
        } else {
            transitionDispatchNode.applyPartialTransition(frame, executor, executor.getLastTransition(frame), i, prevIndex(frame, executor));
        }
        executor.setLastTransition(frame, captureGroupTransitions[i]);
    }

    private int atEnd1(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isAnchoredFinalState() && executor.atEnd(frame)) {
            applyAnchoredFinalStateTransition(frame, executor);
        } else {
            checkFinalState(frame, executor);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    void successorFound2(VirtualFrame frame, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        assert executor.getLastTransition(frame) == captureGroupTransitions[getLoopToSelf()];
        if (executor.isSearching()) {
            checkFinalStateLoop(frame, executor);
        }
        getCGTransitionToSelf(executor).getPartialTransitions()[i].apply(executor, executor.getCGData(frame), prevIndex(frame, executor));
        executor.setLastTransition(frame, captureGroupTransitions[i]);
    }

    void noSuccessor2(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.isSearching();
        checkFinalStateLoop(frame, executor);
    }

    private int atEnd2(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return atEndLoop(frame, executor);
    }

    void successorFound3(VirtualFrame frame, TRegexDFAExecutorNode executor, int i, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        applyLoopTransitions(frame, executor, preLoopIndex, prevIndex(frame, executor) - 1);
        if (executor.isSearching()) {
            checkFinalStateLoop(frame, executor);
        }
        getCGTransitionToSelf(executor).getPartialTransitions()[i].apply(executor, executor.getCGData(frame), prevIndex(frame, executor));
        executor.setLastTransition(frame, captureGroupTransitions[i]);
    }

    void noSuccessor3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.isSearching();
        applyLoopTransitions(frame, executor, preLoopIndex, prevIndex(frame, executor) - 1);
        checkFinalStateLoop(frame, executor);
    }

    private int atEnd3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        applyLoopTransitions(frame, executor, preLoopIndex, prevIndex(frame, executor));
        return atEndLoop(frame, executor);
    }

    private void applyLoopTransitions(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex, int postLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        DFACaptureGroupPartialTransitionNode transition = getCGTransitionToSelf(executor).getPartialTransitions()[getLoopToSelf()];
        if (transition.doesReorderResults()) {
            for (int i = preLoopIndex - 1; i <= postLoopIndex; i++) {
                transition.apply(executor, executor.getCGData(frame), i);
            }
        } else {
            transition.apply(executor, executor.getCGData(frame), postLoopIndex);
        }
    }

    private int atEndLoop(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.getLastTransition(frame) == captureGroupTransitions[getLoopToSelf()];
        if (isAnchoredFinalState() && executor.atEnd(frame)) {
            getCGTransitionToSelf(executor).getTransitionToAnchoredFinalState().applyPreFinalStateTransition(executor, executor.getCGData(frame), executor.isSearching(), curIndex(frame, executor));
            anchoredFinalStateTransition.applyFinalStateTransition(executor, executor.getCGData(frame), executor.isSearching(), nextIndex(frame, executor));
            storeResult(frame, executor);
        } else if (isFinalState()) {
            getCGTransitionToSelf(executor).getTransitionToFinalState().applyPreFinalStateTransition(executor, executor.getCGData(frame), executor.isSearching(), curIndex(frame, executor));
            unAnchoredFinalStateTransition.applyFinalStateTransition(executor, executor.getCGData(frame), executor.isSearching(), nextIndex(frame, executor));
            storeResult(frame, executor);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    private void checkFinalState(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            if (precedingCaptureGroupTransitions.length == 1) {
                executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getTransitionToFinalState().applyPreFinalStateTransition(
                                executor, executor.getCGData(frame), executor.isSearching(), curIndex(frame, executor));
            } else {
                transitionDispatchNode.applyPreFinalTransition(frame, executor, executor.getLastTransition(frame), curIndex(frame, executor));
            }
            unAnchoredFinalStateTransition.applyFinalStateTransition(executor, executor.getCGData(frame), executor.isSearching(), nextIndex(frame, executor));
            storeResult(frame, executor);
        }
    }

    private void applyAnchoredFinalStateTransition(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        if (precedingCaptureGroupTransitions.length == 1) {
            executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getTransitionToAnchoredFinalState().applyPreFinalStateTransition(
                            executor, executor.getCGData(frame), executor.isSearching(), curIndex(frame, executor));
        } else {
            transitionDispatchNode.applyPreAnchoredFinalTransition(frame, executor, executor.getLastTransition(frame), curIndex(frame, executor));
        }
        anchoredFinalStateTransition.applyFinalStateTransition(executor, executor.getCGData(frame), executor.isSearching(), nextIndex(frame, executor));
        storeResult(frame, executor);
    }

    private void checkFinalStateLoop(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.getLastTransition(frame) == captureGroupTransitions[getLoopToSelf()];
        if (isFinalState()) {
            getCGTransitionToSelf(executor).getTransitionToFinalState().applyPreFinalStateTransition(executor, executor.getCGData(frame), executor.isSearching(), prevIndex(frame, executor));
            unAnchoredFinalStateTransition.applyFinalStateTransition(executor, executor.getCGData(frame), executor.isSearching(), curIndex(frame, executor));
            storeResult(frame, executor);
        }
    }

    private void storeResult(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (!executor.isSearching()) {
            executor.getCGData(frame).exportResult((byte) DFACaptureGroupPartialTransitionNode.FINAL_STATE_RESULT_INDEX);
        }
        executor.setResultObject(frame, executor.getCGData(frame).currentResult);
    }
}
