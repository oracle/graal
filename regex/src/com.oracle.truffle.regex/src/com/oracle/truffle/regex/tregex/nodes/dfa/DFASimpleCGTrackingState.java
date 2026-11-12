/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * A simple capture-group state. Its packed record appends an anchored-final successor and a final
 * simple capture-group transition reference to the common {@link DFAState} fields.
 *
 * <pre>
 * +-------------+------+------------------------------+
 * | tail offset | size | field                        |
 * +-------------+------+------------------------------+
 * | 0           | 4    | anchored-final successor     |
 * | 4           | 4    | final-transition reference   |
 * +-------------+------+------------------------------+
 * </pre>
 */
public class DFASimpleCGTrackingState extends DFAState {

    private static final int FIELD_ANCHORED_FINAL_SUCCESSOR = 0;
    private static final int FIELD_FINAL_TRANSITION_REF = Integer.BYTES;
    private static final int TAIL_SIZE = 2 * Integer.BYTES;

    /** Reference to a packed {@link DFASimpleCGTransition} record. */
    private final int transitionToFinalStateRef;

    public DFASimpleCGTrackingState(short id, byte flags, short loopTransitionIndex, short indexOfNodeId, byte indexOfIsFast,
                    short[] successors,
                    int matcherRef,
                    int transitionToFinalStateRef,
                    short anchoredFinalSuccessor) {
        super(id, flags, loopTransitionIndex, indexOfNodeId, indexOfIsFast, successors, matcherRef, anchoredFinalSuccessor);
        this.transitionToFinalStateRef = transitionToFinalStateRef;
    }

    @Override
    DFAExecutorRecords.Kind getRecordKind() {
        return DFAExecutorRecords.Kind.simpleCGState;
    }

    @Override
    int encodedTailSize() {
        return TAIL_SIZE;
    }

    @Override
    void encodeTail(DFAExecutorRecords.Encoder encoder, int tailRef) {
        encoder.putNodeRef(tailRef + FIELD_ANCHORED_FINAL_SUCCESSOR, getAnchoredFinalSuccessor());
        encoder.putInt(tailRef + FIELD_FINAL_TRANSITION_REF, transitionToFinalStateRef);
    }

    static int getAnchoredFinalSuccessor(byte[] records, int recordRef) {
        return DFAExecutorRecords.getInt(records, DFAState.tailOffset(records, recordRef) + FIELD_ANCHORED_FINAL_SUCCESSOR);
    }

    static int getFinalTransitionRef(byte[] records, int recordRef) {
        return DFAExecutorRecords.getInt(records, DFAState.tailOffset(records, recordRef) + FIELD_FINAL_TRANSITION_REF);
    }

    static int getEncodedSize(byte[] records, int recordRef) {
        return DFAState.getEncodedSize(records, recordRef) + TAIL_SIZE;
    }

    static void afterIndexOf(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor,
                    int preLoopIndex, int postLoopIndex, TruffleString.CodeRange codeRange) {
        locals.setIndex(postLoopIndex);
        int loopTransitionRef = DFAState.getSuccessor(records, recordRef, DFAState.getLoopTransitionIndex(records, recordRef));
        DFAExecutorRecords.Kind loopTransitionKind = DFAExecutorRecords.getKind(records, loopTransitionRef);
        CompilerAsserts.partialEvaluationConstant(loopTransitionKind);
        if (loopTransitionKind == DFAExecutorRecords.Kind.simpleCGTransition && locals.getIndex() > preLoopIndex) {
            int curIndex = locals.getIndex();
            executor.inputSkipReverse(locals, codeRange);
            DFASimpleCGTransition.apply(records, loopTransitionRef, locals, executor);
            locals.setIndex(curIndex);
        }
        checkFinalState(records, recordRef, locals, executor);
    }

    static boolean checkFinalState(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        if (DFAState.isFinalState(records, recordRef) && !(DFAState.isAnchoredFinalState(records, recordRef) && executor.inputAtEnd(locals))) {
            storeResult(locals, executor);
            applyFinalTransition(records, recordRef, locals, executor);
        }
        return false;
    }

    static void atEnd(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean inputAtEnd) {
        boolean anchored = DFAState.isAnchoredFinalState(records, recordRef) && inputAtEnd;
        if (DFAState.isFinalState(records, recordRef) || anchored) {
            storeResult(locals, executor);
            if (!DFAState.isAnchoredFinalState(records, recordRef) && DFAState.isFinalState(records, recordRef)) {
                applyFinalTransition(records, recordRef, locals, executor);
            }
        }
    }

    static void storeResult(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        if (executor.isSimpleCGMustCopy()) {
            ArrayUtils.arraycopy(locals.getCGData().results, 0, locals.getCGData().currentResult, 0, locals.getCGData().currentResult.length);
        }
        locals.setResultInt(0);
    }

    private static void applyFinalTransition(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        int transitionRef = getFinalTransitionRef(records, recordRef);
        if (transitionRef != DFASimpleCGTransition.NO_TRANSITION) {
            DFASimpleCGTransition.apply(transitionRef, locals, executor);
        }
    }
}
