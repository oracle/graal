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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;

public class CGTrackingDFAStateNode extends DFAStateNode {

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final short[] captureGroupTransitions;

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final short[] precedingCaptureGroupTransitions;

    @Child private DFACaptureGroupPartialTransitionDispatchNode transitionDispatchNode;

    public CGTrackingDFAStateNode(short id,
                    boolean finalState,
                    boolean anchoredFinalState,
                    boolean findSingleChar,
                    short loopToSelf,
                    short[] successors,
                    CharMatcher[] matchers,
                    short[] captureGroupTransitions,
                    short[] precedingCaptureGroupTransitions) {
        super(id, finalState, anchoredFinalState, findSingleChar, loopToSelf, successors, matchers);
        this.captureGroupTransitions = captureGroupTransitions;
        this.precedingCaptureGroupTransitions = precedingCaptureGroupTransitions;
        transitionDispatchNode = precedingCaptureGroupTransitions.length > 1 ? DFACaptureGroupPartialTransitionDispatchNode.create(precedingCaptureGroupTransitions) : null;
    }

    private CGTrackingDFAStateNode(CGTrackingDFAStateNode copy, short copyID) {
        super(copy, copyID);
        this.captureGroupTransitions = copy.captureGroupTransitions;
        this.precedingCaptureGroupTransitions = copy.precedingCaptureGroupTransitions;
        transitionDispatchNode = precedingCaptureGroupTransitions.length > 1 ? DFACaptureGroupPartialTransitionDispatchNode.create(precedingCaptureGroupTransitions) : null;
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new CGTrackingDFAStateNode(this, copyID);
    }

    @Override
    protected void beforeFindSuccessor(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (executor.isSearching()) {
            checkFinalState(frame, executor, curIndex(frame, executor) + 1);
        }
    }

    @Override
    protected void successorFound1(VirtualFrame frame, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        if (precedingCaptureGroupTransitions.length == 1) {
            executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getPartialTransitions()[i].apply(executor.getCGData(frame), executor.getIndex(frame));
        } else {
            transitionDispatchNode.applyPartialTransition(frame, executor, executor.getLastTransition(frame), i, executor.getIndex(frame));
        }
        executor.setLastTransition(frame, captureGroupTransitions[i]);
    }

    @Override
    protected int atEnd1(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isAnchoredFinalState() && executor.atEnd(frame)) {
            if (precedingCaptureGroupTransitions.length == 1) {
                executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getTransitionToAnchoredFinalState().applyFinalStateTransition(executor.getCGData(frame), executor.isSearching(),
                                nextIndex(frame, executor));
            } else {
                transitionDispatchNode.applyAnchoredFinalTransition(frame, executor, executor.getLastTransition(frame), nextIndex(frame, executor));
            }
            storeResult(frame, executor);
        } else {
            checkFinalState(frame, executor, nextIndex(frame, executor));
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    @Override
    protected void successorFound2(VirtualFrame frame, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        assert executor.getLastTransition(frame) == captureGroupTransitions[loopToSelf];
        if (executor.isSearching()) {
            checkFinalStateLoop(frame, executor, curIndex(frame, executor));
        }
        executor.getCGTransitions()[captureGroupTransitions[loopToSelf]].getPartialTransitions()[i].apply(executor.getCGData(frame), executor.getIndex(frame));
        executor.setLastTransition(frame, captureGroupTransitions[i]);
    }

    @Override
    protected void noSuccessor2(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.isSearching();
        checkFinalStateLoop(frame, executor, curIndex(frame, executor));
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
        applyLoopTransitions(frame, executor, preLoopIndex, prevIndex(frame, executor));
        if (executor.isSearching()) {
            checkFinalStateLoop(frame, executor, curIndex(frame, executor));
        }
        executor.getCGTransitions()[captureGroupTransitions[loopToSelf]].getPartialTransitions()[i].apply(executor.getCGData(frame), executor.getIndex(frame));
        executor.setLastTransition(frame, captureGroupTransitions[i]);
    }

    @Override
    protected void noSuccessor3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.isSearching();
        applyLoopTransitions(frame, executor, preLoopIndex, prevIndex(frame, executor));
        checkFinalStateLoop(frame, executor, curIndex(frame, executor));
    }

    @Override
    protected int atEnd3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        applyLoopTransitions(frame, executor, preLoopIndex, executor.getIndex(frame));
        return atEndLoop(frame, executor);
    }

    private void applyLoopTransitions(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex, int postLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        DFACaptureGroupPartialTransitionNode transition = executor.getCGTransitions()[captureGroupTransitions[loopToSelf]].getPartialTransitions()[loopToSelf];
        if (transition.doesReorderResults()) {
            for (int i = preLoopIndex; i <= postLoopIndex; i++) {
                transition.apply(executor.getCGData(frame), i);
            }
        } else {
            transition.apply(executor.getCGData(frame), postLoopIndex);
        }
    }

    private int atEndLoop(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.getLastTransition(frame) == captureGroupTransitions[loopToSelf];
        if (isAnchoredFinalState() && executor.atEnd(frame)) {
            executor.getCGTransitions()[captureGroupTransitions[loopToSelf]].getTransitionToAnchoredFinalState().applyFinalStateTransition(executor.getCGData(frame), executor.isSearching(),
                            nextIndex(frame, executor));
            storeResult(frame, executor);
        } else if (isFinalState()) {
            executor.getCGTransitions()[captureGroupTransitions[loopToSelf]].getTransitionToFinalState().applyFinalStateTransition(executor.getCGData(frame), executor.isSearching(),
                            nextIndex(frame, executor));
            storeResult(frame, executor);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    private void checkFinalState(VirtualFrame frame, TRegexDFAExecutorNode executor, int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            if (precedingCaptureGroupTransitions.length == 1) {
                executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getTransitionToFinalState().applyFinalStateTransition(executor.getCGData(frame), executor.isSearching(), currentIndex);
            } else {
                transitionDispatchNode.applyFinalTransition(frame, executor, executor.getLastTransition(frame), currentIndex);
            }
            storeResult(frame, executor);
        }
    }

    private void checkFinalStateLoop(VirtualFrame frame, TRegexDFAExecutorNode executor, int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.getLastTransition(frame) == captureGroupTransitions[loopToSelf];
        if (isFinalState()) {
            executor.getCGTransitions()[captureGroupTransitions[loopToSelf]].getTransitionToFinalState().applyFinalStateTransition(executor.getCGData(frame), executor.isSearching(), currentIndex);
            storeResult(frame, executor);
        }
    }

    private void storeResult(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (executor.isSearching()) {
            executor.setResultObject(frame, executor.getCGData(frame).currentResult);
        } else {
            executor.setResultObject(frame, executor.getCGData(frame).results[executor.getCGData(frame).currentResultOrder[DFACaptureGroupPartialTransitionNode.FINAL_STATE_RESULT_INDEX]]);
        }
    }
}
