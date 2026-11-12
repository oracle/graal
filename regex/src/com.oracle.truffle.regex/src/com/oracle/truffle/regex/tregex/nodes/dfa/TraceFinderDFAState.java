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

/**
 * A DFA state with two one-byte pre-calculated result indices appended to the common
 * {@link DFAState} record. Its tail is:
 *
 * <pre>
 * +-------------+------+----------------------------+
 * | tail offset | size | field                      |
 * +-------------+------+----------------------------+
 * | 0           | 1    | unanchored result (uint8)  |
 * | 1           | 1    | anchored result (uint8)    |
 * +-------------+------+----------------------------+
 * </pre>
 */
public class TraceFinderDFAState extends DFAState {

    public static final byte NO_PRE_CALC_RESULT = (byte) 0xff;

    private static final int FIELD_UNANCHORED_RESULT = 0;
    private static final int FIELD_ANCHORED_RESULT = FIELD_UNANCHORED_RESULT + Byte.BYTES;
    private static final int TAIL_SIZE = FIELD_ANCHORED_RESULT + Byte.BYTES;

    private final byte preCalculatedUnAnchoredResult;
    private final byte preCalculatedAnchoredResult;

    public TraceFinderDFAState(short id, byte flags, short loopTransitionIndex, short indexOfNodeId, byte indexOfIsFast, short[] successors, int matcherRef,
                    byte preCalculatedUnAnchoredResult, byte preCalculatedAnchoredResult) {
        super(id, flags, loopTransitionIndex, indexOfNodeId, indexOfIsFast, successors, matcherRef, (short) -1);
        this.preCalculatedUnAnchoredResult = preCalculatedUnAnchoredResult;
        this.preCalculatedAnchoredResult = initPreCalculatedAnchoredResult(preCalculatedUnAnchoredResult, preCalculatedAnchoredResult);
    }

    private static byte initPreCalculatedAnchoredResult(byte preCalculatedUnAnchoredResult, byte preCalculatedAnchoredResult) {
        if (Byte.toUnsignedInt(preCalculatedUnAnchoredResult) < Byte.toUnsignedInt(preCalculatedAnchoredResult)) {
            // unanchored result has higher priority, so the anchored result will never be chosen
            return NO_PRE_CALC_RESULT;
        }
        return preCalculatedAnchoredResult;
    }

    @Override
    DFAExecutorRecords.Kind getRecordKind() {
        return DFAExecutorRecords.Kind.traceFinderState;
    }

    @Override
    int encodedTailSize() {
        return TAIL_SIZE;
    }

    @Override
    void encodeTail(DFAExecutorRecords.Encoder encoder, int tailRef) {
        encoder.putByte(tailRef + FIELD_UNANCHORED_RESULT, preCalculatedUnAnchoredResult);
        encoder.putByte(tailRef + FIELD_ANCHORED_RESULT, preCalculatedAnchoredResult);
    }

    static int getUnAnchoredResult(byte[] records, int recordRef) {
        return Byte.toUnsignedInt(records[DFAState.tailOffset(records, recordRef) + FIELD_UNANCHORED_RESULT]);
    }

    static int getAnchoredResult(byte[] records, int recordRef) {
        return Byte.toUnsignedInt(records[DFAState.tailOffset(records, recordRef) + FIELD_ANCHORED_RESULT]);
    }

    static int getEncodedSize(byte[] records, int recordRef) {
        return DFAState.getEncodedSize(records, recordRef) + TAIL_SIZE;
    }

    static boolean checkFinalState(byte[] records, int recordRef, TRegexDFAExecutorLocals locals) {
        if (DFAState.isFinalState(records, recordRef)) {
            storeResult(records, recordRef, locals, false);
        }
        return false;
    }

    static void afterIndexOf(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, int postLoopIndex) {
        locals.setIndex(postLoopIndex);
        checkFinalState(records, recordRef, locals);
    }

    static void atEnd(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, boolean inputAtEnd) {
        boolean anchored = DFAState.isAnchoredFinalState(records, recordRef) && inputAtEnd;
        if (DFAState.isFinalState(records, recordRef) || anchored) {
            storeResult(records, recordRef, locals, anchored);
        }
    }

    private static void storeResult(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, boolean anchored) {
        int noPreCalculatedResult = Byte.toUnsignedInt(NO_PRE_CALC_RESULT);
        int anchoredResult = getAnchoredResult(records, recordRef);
        if (anchored && anchoredResult != noPreCalculatedResult) {
            locals.setResultInt(anchoredResult);
        } else {
            int unAnchoredResult = getUnAnchoredResult(records, recordRef);
            assert unAnchoredResult != noPreCalculatedResult;
            locals.setResultInt(unAnchoredResult);
        }
    }
}
