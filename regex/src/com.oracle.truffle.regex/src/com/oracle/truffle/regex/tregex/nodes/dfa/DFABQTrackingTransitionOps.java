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
import com.oracle.truffle.regex.tregex.automaton.TransitionOp;

/**
 * A bounded-quantifier operation transition. Its packed record appends a reference into the
 * executor's bounded-quantifier {@code long[]} and a value count to the common
 * {@link DFAAbstractTransition} fields.
 *
 * <pre>
 * +-------------+------+----------------------+
 * | byte offset | size | field                |
 * +-------------+------+----------------------+
 * | 5           | 4    | long-array reference |
 * | 9           | 2    | value count (uint16) |
 * +-------------+------+----------------------+
 * </pre>
 */
public final class DFABQTrackingTransitionOps extends DFAAbstractTransition {

    private static final int FIELD_OPERATIONS_REF = COMMON_RECORD_SIZE;
    private static final int FIELD_OPERATIONS_COUNT = FIELD_OPERATIONS_REF + Integer.BYTES;
    private static final int RECORD_SIZE = FIELD_OPERATIONS_COUNT + Short.BYTES;

    private final long[] operations;

    public DFABQTrackingTransitionOps(short id, short successor, long[] operations) {
        super(id, successor);
        this.operations = operations;
    }

    public long[] getOperations() {
        return operations;
    }

    @Override
    int encodedSize() {
        return RECORD_SIZE;
    }

    @Override
    void encode(DFAExecutorRecords.Encoder encoder, int recordRef) {
        encodeCommon(encoder, recordRef, DFAExecutorRecords.Kind.bqTransitionOps);
        encoder.putBQValues(recordRef + FIELD_OPERATIONS_REF, recordRef + FIELD_OPERATIONS_COUNT, operations);
    }

    static int getOperationsRef(byte[] records, int recordRef) {
        return DFAExecutorRecords.getInt(records, recordRef + FIELD_OPERATIONS_REF);
    }

    static int getOperationsCount(byte[] records, int recordRef) {
        return DFAExecutorRecords.getUnsignedShort(records, recordRef + FIELD_OPERATIONS_COUNT);
    }

    static int getEncodedSize() {
        return RECORD_SIZE;
    }

    static int apply(byte[] records, long[] bqRecords, int recordRef, TRegexDFAExecutorLocals locals, CounterTracker[] counterTrackers) {
        applyOperations(bqRecords, getOperationsRef(records, recordRef), getOperationsCount(records, recordRef), locals, counterTrackers);
        return getSuccessor(records, recordRef);
    }

    @ExplodeLoop
    private static void applyOperations(long[] bqRecords, int operationsRef, int operationCount, TRegexDFAExecutorLocals locals, CounterTracker[] counterTrackers) {
        CompilerAsserts.partialEvaluationConstant(bqRecords);
        CompilerAsserts.partialEvaluationConstant(operationsRef);
        CompilerAsserts.partialEvaluationConstant(operationCount);
        for (int i = 0; i < operationCount; i++) {
            long operation = bqRecords[operationsRef + i];
            CompilerAsserts.partialEvaluationConstant(operation);
            int quantifierId = TransitionOp.getQuantifierID(operation);
            CompilerAsserts.partialEvaluationConstant(quantifierId);
            CounterTracker counterTracker = counterTrackers[quantifierId];
            CompilerAsserts.partialEvaluationConstant(counterTracker);
            counterTracker.apply(operation, locals.getFixedData(), locals.getIntArrays());
        }
    }
}
