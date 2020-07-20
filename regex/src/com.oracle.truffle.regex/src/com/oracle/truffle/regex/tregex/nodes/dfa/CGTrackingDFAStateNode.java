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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class CGTrackingDFAStateNode extends DFAStateNode {

    private final DFACaptureGroupPartialTransition anchoredFinalStateTransition;
    private final DFACaptureGroupPartialTransition unAnchoredFinalStateTransition;

    @CompilationFinal(dimensions = 1) private final short[] captureGroupTransitions;

    @CompilationFinal(dimensions = 1) private final short[] precedingCaptureGroupTransitions;

    @Child private DFACaptureGroupPartialTransitionDispatchNode transitionDispatchNode;

    public CGTrackingDFAStateNode(short id, byte flags, short loopTransitionIndex, LoopOptimizationNode loopOptimizationNode, short[] successors, Matchers matchers,
                    AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher, short[] captureGroupTransitions,
                    short[] precedingCaptureGroupTransitions,
                    DFACaptureGroupPartialTransition anchoredFinalStateTransition,
                    DFACaptureGroupPartialTransition unAnchoredFinalStateTransition) {
        super(id, flags, loopTransitionIndex, loopOptimizationNode, successors, matchers, null, allTransitionsInOneTreeMatcher);
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

    private DFACaptureGroupLazyTransition getCGTransitionToSelf(TRegexDFAExecutorNode executor) {
        return executor.getCGTransitions()[captureGroupTransitions[getLoopToSelf()]];
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new CGTrackingDFAStateNode(this, copyID);
    }

    @Override
    void beforeFindSuccessor(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (executor.isSearching()) {
            checkFinalState(locals, executor);
        }
    }

    @Override
    void afterIndexOf(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, final int preLoopIndex, int postLoopIndex) {
        assert locals.getIndex() == preLoopIndex;
        if (locals.getIndex() < postLoopIndex) {
            successorFound(locals, executor, getLoopToSelf());
            locals.setLastIndex(locals.getIndex());
            executor.inputSkip(locals);
        }
        int secondIndex = locals.getIndex();
        DFACaptureGroupPartialTransition transition = getCGTransitionToSelf(executor).getPartialTransitions()[getLoopToSelf()];
        if (transition.doesReorderResults()) {
            while (locals.getIndex() < postLoopIndex) {
                transition.apply(executor, locals.getCGData(), locals.getLastIndex());
                locals.setLastIndex(locals.getIndex());
                executor.inputSkip(locals);
            }
        } else {
            locals.setIndex(postLoopIndex);
            executor.inputSkipReverse(locals);
            locals.setLastIndex(locals.getIndex());
            if (secondIndex < postLoopIndex) {
                transition.apply(executor, locals.getCGData(), locals.getIndex());
            }
            locals.setIndex(postLoopIndex);
        }
        executor.inputIncNextIndexRaw(locals, loopOptimizationNode.encodedLength());
        if (executor.isSearching()) {
            checkFinalState(locals, executor);
        }
    }

    @Override
    void successorFound(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        if (precedingCaptureGroupTransitions.length == 1) {
            executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getPartialTransitions()[i].apply(executor, locals.getCGData(), locals.getLastIndex());
        } else {
            transitionDispatchNode.applyPartialTransition(locals, executor, locals.getLastTransition(), i, locals.getLastIndex());
        }
        locals.setLastTransition(captureGroupTransitions[i]);
    }

    @Override
    void atEnd(TRegexDFAExecutorLocals frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isAnchoredFinalState() && executor.inputAtEnd(frame)) {
            applyAnchoredFinalStateTransition(frame, executor);
        } else {
            checkFinalState(frame, executor);
        }
    }

    private void checkFinalState(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            applyUnAnchoredFinalStateTransition(locals, executor);
        }
    }

    private void applyAnchoredFinalStateTransition(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        DFACaptureGroupTrackingData data = locals.getCGData();
        if (precedingCaptureGroupTransitions.length == 1) {
            executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getTransitionToAnchoredFinalState().applyPreFinalStateTransition(executor, data, locals.getLastIndex());
        } else {
            transitionDispatchNode.applyPreAnchoredFinalTransition(locals, executor, locals.getLastTransition(), locals.getLastIndex());
        }
        anchoredFinalStateTransition.applyFinalStateTransition(executor, data, locals.getIndex());
        storeResult(locals, executor);
    }

    private void applyUnAnchoredFinalStateTransition(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        DFACaptureGroupTrackingData data = locals.getCGData();
        if (precedingCaptureGroupTransitions.length == 1) {
            executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getTransitionToFinalState().applyPreFinalStateTransition(executor, data, locals.getLastIndex());
        } else {
            transitionDispatchNode.applyPreFinalTransition(locals, executor, locals.getLastTransition(), locals.getLastIndex());
        }
        unAnchoredFinalStateTransition.applyFinalStateTransition(executor, data, locals.getIndex());
        storeResult(locals, executor);
    }

    private void storeResult(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (!executor.isSearching()) {
            locals.getCGData().exportResult((byte) DFACaptureGroupPartialTransition.FINAL_STATE_RESULT_INDEX);
        }
        locals.setResultInt(0);
    }
}
