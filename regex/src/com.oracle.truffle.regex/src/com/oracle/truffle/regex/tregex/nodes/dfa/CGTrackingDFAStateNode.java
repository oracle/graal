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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.strings.TruffleString;

public class CGTrackingDFAStateNode extends DFAStateNode {

    @CompilationFinal(dimensions = 1) private final short[] lastTransitionIndex;
    @CompilationFinal(dimensions = 1) private final DFACaptureGroupLazyTransition[] lazyTransitions;
    private final DFACaptureGroupLazyTransition preAnchoredFinalStateTransition;
    private final DFACaptureGroupLazyTransition preUnAnchoredFinalStateTransition;
    private final DFACaptureGroupPartialTransition anchoredFinalStateTransition;
    private final DFACaptureGroupPartialTransition unAnchoredFinalStateTransition;
    private final DFACaptureGroupPartialTransition cgLoopToSelf;
    private final boolean cgLoopToSelfHasDependency;

    public CGTrackingDFAStateNode(short id,
                    byte flags,
                    short loopTransitionIndex,
                    short indexOfNodeId,
                    byte indexOfIsFast,
                    short[] successors,
                    Matchers matchers,
                    short[] lastTransitionIndex,
                    DFACaptureGroupLazyTransition[] lazyTransitions,
                    DFACaptureGroupLazyTransition preAnchoredFinalStateTransition,
                    DFACaptureGroupLazyTransition preUnAnchoredFinalStateTransition,
                    DFACaptureGroupPartialTransition anchoredFinalStateTransition,
                    DFACaptureGroupPartialTransition unAnchoredFinalStateTransition,
                    DFACaptureGroupPartialTransition cgLoopToSelf,
                    boolean cgLoopToSelfHasDependency) {
        super(id, flags, loopTransitionIndex, indexOfNodeId, indexOfIsFast, successors, matchers, null);
        this.anchoredFinalStateTransition = anchoredFinalStateTransition;
        this.unAnchoredFinalStateTransition = unAnchoredFinalStateTransition;
        this.lastTransitionIndex = lastTransitionIndex;
        this.lazyTransitions = lazyTransitions;
        this.preAnchoredFinalStateTransition = preAnchoredFinalStateTransition;
        this.preUnAnchoredFinalStateTransition = preUnAnchoredFinalStateTransition;
        this.cgLoopToSelf = cgLoopToSelf;
        this.cgLoopToSelfHasDependency = cgLoopToSelfHasDependency;
    }

    private CGTrackingDFAStateNode(CGTrackingDFAStateNode copy, short copyID) {
        super(copy, copyID);
        this.lastTransitionIndex = copy.lastTransitionIndex;
        this.lazyTransitions = copy.lazyTransitions;
        this.preAnchoredFinalStateTransition = copy.preAnchoredFinalStateTransition;
        this.preUnAnchoredFinalStateTransition = copy.preUnAnchoredFinalStateTransition;
        this.anchoredFinalStateTransition = copy.anchoredFinalStateTransition;
        this.unAnchoredFinalStateTransition = copy.unAnchoredFinalStateTransition;
        this.cgLoopToSelf = copy.cgLoopToSelf;
        this.cgLoopToSelfHasDependency = copy.cgLoopToSelfHasDependency;
    }

    private DFACaptureGroupPartialTransition getCGTransitionToSelf() {
        return cgLoopToSelf;
    }

    public short[] getLastTransitionIndex() {
        return lastTransitionIndex;
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
    void afterIndexOf(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, final int preLoopIndex, int postLoopIndex, TruffleString.CodeRange codeRange) {
        assert locals.getIndex() == preLoopIndex;
        if (locals.getIndex() < postLoopIndex) {
            successorFound(locals, executor, getLoopToSelf());
            locals.setLastIndex();
            executor.inputSkip(locals, codeRange);
        }
        int secondIndex = locals.getIndex();
        DFACaptureGroupPartialTransition transition = getCGTransitionToSelf();
        if (transition.doesReorderResults()) {
            while (locals.getIndex() < postLoopIndex) {
                transition.apply(executor, locals.getCGData(), locals.getLastIndex());
                locals.setLastIndex();
                executor.inputSkip(locals, codeRange);
            }
        } else if (postLoopIndex > preLoopIndex) {
            locals.setIndex(postLoopIndex);
            executor.inputSkipReverse(locals, codeRange);
            locals.setLastIndex();
            if (secondIndex < postLoopIndex) {
                executor.inputSkipReverse(locals, codeRange);
            }
            if (cgLoopToSelfHasDependency && secondIndex < locals.getLastIndex()) {
                int postLoopMinusTwoIndex = locals.getIndex();
                executor.inputSkipReverse(locals, codeRange);
                transition.apply(executor, locals.getCGData(), locals.getIndex());
                locals.setIndex(postLoopMinusTwoIndex);
            }
            if (secondIndex < postLoopIndex) {
                transition.apply(executor, locals.getCGData(), locals.getIndex());
            }
            locals.setIndex(postLoopIndex);
        }
        if (!executor.inputAtEnd(locals)) {
            executor.inputIncNextIndexRaw(locals, executor.inputGetCodePointSize(locals, codeRange));
        }
        if (executor.isSearching()) {
            checkFinalState(locals, executor);
        }
    }

    @Override
    void successorFound(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        lazyTransitions[i].apply(locals, executor);
        locals.setLastIndex();
        if (lastTransitionIndex[i] >= 0) {
            locals.setLastTransition(lastTransitionIndex[i]);
        }
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
        preAnchoredFinalStateTransition.applyPreFinal(locals, executor);
        anchoredFinalStateTransition.applyFinalStateTransition(executor, data, locals.getIndex());
        storeResult(locals, executor);
    }

    private void applyUnAnchoredFinalStateTransition(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        DFACaptureGroupTrackingData data = locals.getCGData();
        preUnAnchoredFinalStateTransition.applyPreFinal(locals, executor);
        unAnchoredFinalStateTransition.applyFinalStateTransition(executor, data, locals.getIndex());
        storeResult(locals, executor);
    }

    private void storeResult(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (!executor.isSearching()) {
            locals.getCGData().exportResult(executor, (byte) DFACaptureGroupPartialTransition.FINAL_STATE_RESULT_INDEX);
        }
        locals.setResultInt(0);
    }

    public int getCGTrackingCost() {
        int cost = getCost(preAnchoredFinalStateTransition) + getCost(preUnAnchoredFinalStateTransition) + getCost(anchoredFinalStateTransition) + getCost(unAnchoredFinalStateTransition);
        for (DFACaptureGroupLazyTransition t : lazyTransitions) {
            cost += t.getCost();
        }
        return cost;
    }

    private static int getCost(DFACaptureGroupLazyTransition t) {
        return t == null ? 0 : t.getCost();
    }

    private static int getCost(DFACaptureGroupPartialTransition t) {
        return t == null ? 0 : t.getCost();
    }
}
