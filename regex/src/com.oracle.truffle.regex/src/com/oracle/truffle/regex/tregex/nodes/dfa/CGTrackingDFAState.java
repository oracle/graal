/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A generic capture-group state. Its packed record appends the anchored-final successor, pre-final
 * transition, final transition, loop partial-transition, and loop-dependency flag to the common
 * {@link DFAState} fields.
 *
 * <pre>
 * +-------------+------+----------------------------------+
 * | tail offset | size | field                            |
 * +-------------+------+----------------------------------+
 * | 0           | 4    | anchored-final successor         |
 * | 4           | 4    | pre-final transition reference   |
 * | 8           | 4    | final transition reference       |
 * | 12          | 4    | loop transition reference        |
 * | 16          | 1    | loop-transition dependency flag  |
 * +-------------+------+----------------------------------+
 * </pre>
 */
public final class CGTrackingDFAState extends DFAState {

    private static final int FIELD_ANCHORED_FINAL_SUCCESSOR = 0;
    private static final int FIELD_PRE_FINAL_TRANSITION_REF = Integer.BYTES;
    private static final int FIELD_FINAL_TRANSITION_REF = 2 * Integer.BYTES;
    private static final int FIELD_LOOP_TRANSITION_REF = 3 * Integer.BYTES;
    private static final int FIELD_LOOP_TRANSITION_HAS_DEPENDENCY = 4 * Integer.BYTES;
    private static final int TAIL_SIZE = 4 * Integer.BYTES + 1;

    /** Reference to a {@link DFACaptureGroupLazyTransition}. */
    private final int preUnAnchoredFinalStateTransitionRef;
    /** Reference to a {@link DFACaptureGroupPartialTransition}. */
    private final int unAnchoredFinalStateTransitionRef;
    /** Reference to a {@link DFACaptureGroupPartialTransition}. */
    private final int cgLoopToSelfRef;
    private final boolean cgLoopToSelfHasDependency;

    public CGTrackingDFAState(short id,
                    byte flags,
                    short loopTransitionIndex,
                    short indexOfNodeId,
                    byte indexOfIsFast,
                    short[] successors,
                    int matcherRef,
                    short anchoredFinalSuccessor,
                    int preUnAnchoredFinalStateTransitionRef,
                    int unAnchoredFinalStateTransitionRef,
                    int cgLoopToSelfRef,
                    boolean cgLoopToSelfHasDependency) {
        super(id, flags, loopTransitionIndex, indexOfNodeId, indexOfIsFast, successors, matcherRef, anchoredFinalSuccessor);
        this.unAnchoredFinalStateTransitionRef = unAnchoredFinalStateTransitionRef;
        this.preUnAnchoredFinalStateTransitionRef = preUnAnchoredFinalStateTransitionRef;
        this.cgLoopToSelfRef = cgLoopToSelfRef;
        this.cgLoopToSelfHasDependency = cgLoopToSelfHasDependency;
    }

    @Override
    DFAExecutorRecords.Kind getRecordKind() {
        return DFAExecutorRecords.Kind.genericCGState;
    }

    @Override
    int encodedTailSize() {
        return TAIL_SIZE;
    }

    @Override
    void encodeTail(DFAExecutorRecords.Encoder encoder, int tailRef) {
        encoder.putNodeRef(tailRef + FIELD_ANCHORED_FINAL_SUCCESSOR, getAnchoredFinalSuccessor());
        encoder.putInt(tailRef + FIELD_PRE_FINAL_TRANSITION_REF, preUnAnchoredFinalStateTransitionRef);
        encoder.putInt(tailRef + FIELD_FINAL_TRANSITION_REF, unAnchoredFinalStateTransitionRef);
        encoder.putInt(tailRef + FIELD_LOOP_TRANSITION_REF, cgLoopToSelfRef);
        encoder.putByte(tailRef + FIELD_LOOP_TRANSITION_HAS_DEPENDENCY, cgLoopToSelfHasDependency ? 1 : 0);
    }

    static int getAnchoredFinalSuccessor(byte[] records, int recordRef) {
        return DFAExecutorRecords.getInt(records, DFAState.tailOffset(records, recordRef) + FIELD_ANCHORED_FINAL_SUCCESSOR);
    }

    static int getPreUnAnchoredFinalTransitionRef(byte[] records, int recordRef) {
        return DFAExecutorRecords.getInt(records, DFAState.tailOffset(records, recordRef) + FIELD_PRE_FINAL_TRANSITION_REF);
    }

    static int getUnAnchoredFinalTransitionRef(byte[] records, int recordRef) {
        return DFAExecutorRecords.getInt(records, DFAState.tailOffset(records, recordRef) + FIELD_FINAL_TRANSITION_REF);
    }

    static int getLoopTransitionRef(byte[] records, int recordRef) {
        return DFAExecutorRecords.getInt(records, DFAState.tailOffset(records, recordRef) + FIELD_LOOP_TRANSITION_REF);
    }

    static boolean loopTransitionHasDependency(byte[] records, int recordRef) {
        return records[DFAState.tailOffset(records, recordRef) + FIELD_LOOP_TRANSITION_HAS_DEPENDENCY] != 0;
    }

    static int getEncodedSize(byte[] records, int recordRef) {
        return DFAState.getEncodedSize(records, recordRef) + TAIL_SIZE;
    }

    static boolean beforeFindSuccessor(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        if (executor.isSearching()) {
            checkFinalState(records, recordRef, locals, executor);
        }
        return false;
    }

    static void afterIndexOf(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor,
                    int preLoopIndex, int postLoopIndex, TruffleString.CodeRange codeRange) {
        assert locals.getIndex() == preLoopIndex;
        if (locals.getIndex() < postLoopIndex) {
            int loopTransitionRef = DFAState.getSuccessor(records, recordRef, DFAState.getLoopTransitionIndex(records, recordRef));
            DFAExecutorRecords.Kind loopTransitionKind = DFAExecutorRecords.getKind(records, loopTransitionRef);
            CompilerAsserts.partialEvaluationConstant(loopTransitionKind);
            if (loopTransitionKind == DFAExecutorRecords.Kind.genericCGTransition) {
                CGTrackingTransition.apply(records, loopTransitionRef, locals, executor);
            }
            locals.setLastIndex();
            executor.inputSkip(locals, codeRange);
        }
        int secondIndex = locals.getIndex();
        int transitionRef = getLoopTransitionRef(records, recordRef);
        byte[] partialTransitionRecords = executor.getCGPartialTransitionRecords();
        if (DFACaptureGroupPartialTransition.doesReorderResults(partialTransitionRecords, transitionRef)) {
            while (locals.getIndex() < postLoopIndex) {
                DFACaptureGroupPartialTransition.apply(partialTransitionRecords, transitionRef, executor, locals.getCGData(), locals.getLastIndex());
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
            if (loopTransitionHasDependency(records, recordRef) && secondIndex < locals.getLastIndex()) {
                int postLoopMinusTwoIndex = locals.getIndex();
                executor.inputSkipReverse(locals, codeRange);
                DFACaptureGroupPartialTransition.apply(partialTransitionRecords, transitionRef, executor, locals.getCGData(), locals.getIndex());
                locals.setIndex(postLoopMinusTwoIndex);
            }
            if (secondIndex < postLoopIndex) {
                DFACaptureGroupPartialTransition.apply(partialTransitionRecords, transitionRef, executor, locals.getCGData(), locals.getIndex());
            }
            locals.setIndex(postLoopIndex);
        }
        if (!executor.inputAtEnd(locals)) {
            executor.inputIncNextIndexRaw(locals, executor.inputGetCodePointSize(locals, codeRange));
        }
        if (executor.isSearching()) {
            checkFinalState(records, recordRef, locals, executor);
        }
    }

    static void atEnd(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean inputAtEnd) {
        if (!DFAState.isAnchoredFinalState(records, recordRef) || !inputAtEnd) {
            checkFinalState(records, recordRef, locals, executor);
        }
    }

    private static void checkFinalState(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        if (DFAState.isFinalState(records, recordRef)) {
            DFACaptureGroupLazyTransition.applyPreFinal(getPreUnAnchoredFinalTransitionRef(records, recordRef), locals, executor);
            DFACaptureGroupPartialTransition.applyFinalStateTransition(executor.getCGPartialTransitionRecords(),
                            getUnAnchoredFinalTransitionRef(records, recordRef), executor, locals.getCGData(), locals.getIndex());
            storeResult(locals, executor);
        }
    }

    static void storeResult(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        if (!executor.isSearching()) {
            locals.getCGData().exportResult(executor, (byte) DFACaptureGroupPartialTransition.FINAL_STATE_RESULT_INDEX);
        }
        locals.setResultInt(0);
    }

    static int getCGTrackingCost(byte[] records, int recordRef, TRegexDFAExecutorNode executor) {
        int cost = 0;
        int preFinalRef = getPreUnAnchoredFinalTransitionRef(records, recordRef);
        int finalRef = getUnAnchoredFinalTransitionRef(records, recordRef);
        if (preFinalRef != DFACaptureGroupLazyTransition.NO_TRANSITION) {
            cost += DFACaptureGroupLazyTransition.getCost(preFinalRef, executor);
        }
        if (finalRef != DFACaptureGroupLazyTransition.NO_TRANSITION) {
            cost += DFACaptureGroupPartialTransition.getCost(executor.getCGPartialTransitionRecords(), finalRef);
        }
        return cost;
    }
}
