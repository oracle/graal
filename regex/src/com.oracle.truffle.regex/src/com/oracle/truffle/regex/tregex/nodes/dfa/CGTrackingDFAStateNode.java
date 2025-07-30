/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.strings.TruffleString;

public final class CGTrackingDFAStateNode extends DFAStateNode {

    private final DFACaptureGroupLazyTransition preUnAnchoredFinalStateTransition;
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
                    short anchoredFinalSuccessor,
                    DFACaptureGroupLazyTransition preUnAnchoredFinalStateTransition,
                    DFACaptureGroupPartialTransition unAnchoredFinalStateTransition,
                    DFACaptureGroupPartialTransition cgLoopToSelf,
                    boolean cgLoopToSelfHasDependency) {
        super(id, flags, loopTransitionIndex, indexOfNodeId, indexOfIsFast, successors, matchers, anchoredFinalSuccessor);
        this.unAnchoredFinalStateTransition = unAnchoredFinalStateTransition;
        this.preUnAnchoredFinalStateTransition = preUnAnchoredFinalStateTransition;
        this.cgLoopToSelf = cgLoopToSelf;
        this.cgLoopToSelfHasDependency = cgLoopToSelfHasDependency;
    }

    private DFACaptureGroupPartialTransition getCGTransitionToSelf() {
        return cgLoopToSelf;
    }

    @Override
    boolean beforeFindSuccessor(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (executor.isSearching()) {
            checkFinalStateCG(locals, executor);
        }
        return false;
    }

    @Override
    void afterIndexOf(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, final int preLoopIndex, int postLoopIndex, TruffleString.CodeRange codeRange) {
        assert locals.getIndex() == preLoopIndex;
        if (locals.getIndex() < postLoopIndex) {
            DFAAbstractNode loopTransitionNode = executor.getNodes()[successors[getLoopToSelf()]];
            if (loopTransitionNode instanceof CGTrackingTransitionNode cgTrackingLoopTransitionNode) {
                cgTrackingLoopTransitionNode.apply(locals, executor);
            }
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
            checkFinalStateCG(locals, executor);
        }
    }

    @Override
    void atEnd(TRegexDFAExecutorLocals frame, TRegexDFAExecutorNode executor, boolean inputAtEnd) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (!isAnchoredFinalState() || !inputAtEnd) {
            checkFinalStateCG(frame, executor);
        }
    }

    private void checkFinalStateCG(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            preUnAnchoredFinalStateTransition.applyPreFinal(locals, executor);
            unAnchoredFinalStateTransition.applyFinalStateTransition(executor, locals.getCGData(), locals.getIndex());
            storeResult(locals, executor);
        }
    }

    static void storeResult(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        if (!executor.isSearching()) {
            locals.getCGData().exportResult(executor, (byte) DFACaptureGroupPartialTransition.FINAL_STATE_RESULT_INDEX);
        }
        locals.setResultInt(0);
    }

    public int getCGTrackingCost() {
        return getCost(preUnAnchoredFinalStateTransition) + getCost(unAnchoredFinalStateTransition);
    }

    private static int getCost(DFACaptureGroupLazyTransition t) {
        return t == null ? 0 : t.getCost();
    }

    private static int getCost(DFACaptureGroupPartialTransition t) {
        return t == null ? 0 : t.getCost();
    }
}
