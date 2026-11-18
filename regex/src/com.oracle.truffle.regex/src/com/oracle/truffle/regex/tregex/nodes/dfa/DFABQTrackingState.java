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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.ExplodeLoop;

/**
 * A bounded-quantifier state. Its packed record appends unanchored and anchored constraint lists to
 * the common {@link DFAState} fields. Each list has a two-byte entry count followed by six-byte
 * entries containing a {@code long[]} reference and value count.
 *
 * <pre>
 * +----------------------+------------------------+----------------------+
 * | entry count (uint16) | long-array ref (int32) | value count (uint16) |
 * +----------------------+------------------------+----------------------+
 * | 2 B                  | 4 B                    | 2 B                  |
 * +----------------------+------------------------+----------------------+
 * |                      | repeated entryCount times                     |
 * +----------------------+-----------------------------------------------+
 * </pre>
 */
public class DFABQTrackingState extends DFAState {

    private static final int LIST_HEADER_SIZE = Short.BYTES;
    private static final int LIST_ENTRY_SIZE = Integer.BYTES + Short.BYTES;
    private static final int FIELD_LIST_ENTRY_REF = 0;
    private static final int FIELD_LIST_ENTRY_COUNT = Integer.BYTES;

    private final long[][] unAnchoredFinalConstraints;
    private final long[][] anchoredFinalConstraints;

    public DFABQTrackingState(short id, byte flags, short loopTransitionIndex, short indexOfNodeId, byte indexOfIsFast,
                    short[] successors,
                    int matcherRef,
                    long[][] unAnchoredFinalConstraints,
                    long[][] anchoredFinalConstraints) {
        super(id, flags, loopTransitionIndex, indexOfNodeId, indexOfIsFast, successors, matcherRef, (short) -1);
        this.unAnchoredFinalConstraints = unAnchoredFinalConstraints;
        this.anchoredFinalConstraints = anchoredFinalConstraints;
    }

    @Override
    DFAExecutorRecords.Kind getRecordKind() {
        return DFAExecutorRecords.Kind.bqState;
    }

    @Override
    int encodedTailSize() {
        return Math.addExact(listSize(unAnchoredFinalConstraints), listSize(anchoredFinalConstraints));
    }

    @Override
    void encodeTail(DFAExecutorRecords.Encoder encoder, int tailRef) {
        int anchoredListRef = encodeList(encoder, tailRef, unAnchoredFinalConstraints);
        encodeList(encoder, anchoredListRef, anchoredFinalConstraints);
    }

    private static int encodeList(DFAExecutorRecords.Encoder encoder, int listRef, long[][] constraints) {
        int n = length(constraints);
        encoder.putUnsignedShort(listRef, n);
        int entryRef = listRef + LIST_HEADER_SIZE;
        for (int i = 0; i < n; i++) {
            encoder.putBQValues(entryRef + FIELD_LIST_ENTRY_REF, entryRef + FIELD_LIST_ENTRY_COUNT, constraints[i]);
            entryRef += LIST_ENTRY_SIZE;
        }
        return entryRef;
    }

    static int getConstraintListCount(byte[] records, int recordRef, boolean anchored) {
        return DFAExecutorRecords.getUnsignedShort(records, constraintListRef(records, recordRef, anchored));
    }

    static int getConstraintRef(byte[] records, int recordRef, boolean anchored, int index) {
        int entryRef = constraintListRef(records, recordRef, anchored) + LIST_HEADER_SIZE + LIST_ENTRY_SIZE * index;
        return DFAExecutorRecords.getInt(records, entryRef + FIELD_LIST_ENTRY_REF);
    }

    static int getConstraintCount(byte[] records, int recordRef, boolean anchored, int index) {
        int entryRef = constraintListRef(records, recordRef, anchored) + LIST_HEADER_SIZE + LIST_ENTRY_SIZE * index;
        return DFAExecutorRecords.getUnsignedShort(records, entryRef + FIELD_LIST_ENTRY_COUNT);
    }

    static int getEncodedSize(byte[] records, int recordRef) {
        int tailRef = DFAState.tailOffset(records, recordRef);
        int anchoredListRef = nextListRef(records, tailRef);
        return nextListRef(records, anchoredListRef) - recordRef;
    }

    private static int constraintListRef(byte[] records, int recordRef, boolean anchored) {
        int listRef = DFAState.tailOffset(records, recordRef);
        return anchored ? nextListRef(records, listRef) : listRef;
    }

    private static int nextListRef(byte[] records, int listRef) {
        return listRef + LIST_HEADER_SIZE + LIST_ENTRY_SIZE * DFAExecutorRecords.getUnsignedShort(records, listRef);
    }

    private static int listSize(long[][] constraints) {
        return Math.addExact(LIST_HEADER_SIZE, Math.multiplyExact(LIST_ENTRY_SIZE, length(constraints)));
    }

    static boolean checkFinalState(byte[] records, long[] bqRecords, int recordRef, TRegexDFAExecutorLocals locals, CounterTracker[] counterTrackers) {
        if (DFAState.isFinalState(records, recordRef)) {
            DFAState.storeResult(locals);
            return true;
        }
        if (DFAState.isGuardedFinalState(records, recordRef) && checkFinalStateConstraints(records, bqRecords, recordRef, locals, false, counterTrackers)) {
            DFAState.storeResult(locals);
            return true;
        }
        return false;
    }

    static void afterIndexOf(byte[] records, long[] bqRecords, int recordRef, TRegexDFAExecutorLocals locals, int postLoopIndex, CounterTracker[] counterTrackers) {
        locals.setIndex(postLoopIndex);
        checkFinalState(records, bqRecords, recordRef, locals, counterTrackers);
    }

    static void atEnd(byte[] records, long[] bqRecords, int recordRef, TRegexDFAExecutorLocals locals, boolean inputAtEnd, CounterTracker[] counterTrackers) {
        DFAState.atEnd(records, recordRef, locals, inputAtEnd);
        if (DFAState.isGuardedFinalState(records, recordRef) && checkFinalStateConstraints(records, bqRecords, recordRef, locals, false, counterTrackers)) {
            DFAState.storeResult(locals);
            return;
        }
        if (DFAState.isGuardedAnchoredFinalState(records, recordRef) && inputAtEnd && checkFinalStateConstraints(records, bqRecords, recordRef, locals, true, counterTrackers)) {
            DFAState.storeResult(locals);
        }
    }

    @ExplodeLoop
    private static boolean checkFinalStateConstraints(byte[] records, long[] bqRecords, int recordRef, TRegexDFAExecutorLocals locals, boolean anchored, CounterTracker[] counterTrackers) {
        int listCount = getConstraintListCount(records, recordRef, anchored);
        CompilerAsserts.partialEvaluationConstant(listCount);
        for (int i = 0; i < listCount; i++) {
            int constraintRef = getConstraintRef(records, recordRef, anchored, i);
            int constraintCount = getConstraintCount(records, recordRef, anchored, i);
            if (DFABQTrackingTransitionConstraints.constraintsAreSatisfied(bqRecords, constraintRef, constraintCount, locals, counterTrackers)) {
                return true;
            }
        }
        return false;
    }

    private static int length(long[][] values) {
        return values == null ? 0 : values.length;
    }
}
