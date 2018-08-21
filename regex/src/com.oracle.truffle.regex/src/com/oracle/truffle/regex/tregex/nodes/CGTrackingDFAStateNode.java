/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;

public class CGTrackingDFAStateNode extends DFAStateNode {

    private final DFACaptureGroupPartialTransitionNode anchoredFinalStateTransition;
    private final DFACaptureGroupPartialTransitionNode unAnchoredFinalStateTransition;

    @CompilationFinal(dimensions = 1) private final short[] captureGroupTransitions;

    @CompilationFinal(dimensions = 1) private final short[] precedingCaptureGroupTransitions;

    @Child private DFACaptureGroupPartialTransitionDispatchNode transitionDispatchNode;

    public CGTrackingDFAStateNode(short id, byte flags, LoopOptimizationNode loopOptimizationNode, short[] successors, CharMatcher[] matchers,
                    short[] captureGroupTransitions,
                    short[] precedingCaptureGroupTransitions,
                    DFACaptureGroupPartialTransitionNode anchoredFinalStateTransition,
                    DFACaptureGroupPartialTransitionNode unAnchoredFinalStateTransition) {
        super(id, flags, loopOptimizationNode, successors, matchers);
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
    protected void beforeFindSuccessor(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (executor.isSearching()) {
            checkFinalState(frame, executor);
        }
    }

    @Override
    protected void successorFound1(VirtualFrame frame, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        if (precedingCaptureGroupTransitions.length == 1) {
            executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getPartialTransitions()[i].apply(executor, executor.getCGData(frame), prevIndex(frame, executor));
        } else {
            transitionDispatchNode.applyPartialTransition(frame, executor, executor.getLastTransition(frame), i, prevIndex(frame, executor));
        }
        executor.setLastTransition(frame, captureGroupTransitions[i]);
    }

    @Override
    protected int atEnd1(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isAnchoredFinalState() && executor.atEnd(frame)) {
            applyAnchoredFinalStateTransition(frame, executor);
        } else {
            checkFinalState(frame, executor);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    @Override
    protected void successorFound2(VirtualFrame frame, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        assert executor.getLastTransition(frame) == captureGroupTransitions[getLoopToSelf()];
        if (executor.isSearching()) {
            checkFinalStateLoop(frame, executor);
        }
        getCGTransitionToSelf(executor).getPartialTransitions()[i].apply(executor, executor.getCGData(frame), prevIndex(frame, executor));
        executor.setLastTransition(frame, captureGroupTransitions[i]);
    }

    @Override
    protected void noSuccessor2(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.isSearching();
        checkFinalStateLoop(frame, executor);
    }

    @Override
    protected int atEnd2(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return atEndLoop(frame, executor);
    }

    @Override
    protected void successorFound3(VirtualFrame frame, TRegexDFAExecutorNode executor, int i, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        applyLoopTransitions(frame, executor, preLoopIndex, prevIndex(frame, executor) - 1);
        if (executor.isSearching()) {
            checkFinalStateLoop(frame, executor);
        }
        getCGTransitionToSelf(executor).getPartialTransitions()[i].apply(executor, executor.getCGData(frame), prevIndex(frame, executor));
        executor.setLastTransition(frame, captureGroupTransitions[i]);
    }

    @Override
    protected void noSuccessor3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.isSearching();
        applyLoopTransitions(frame, executor, preLoopIndex, prevIndex(frame, executor) - 1);
        checkFinalStateLoop(frame, executor);
    }

    @Override
    protected int atEnd3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
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
