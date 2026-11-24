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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.automaton.TransitionConstraint;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * A bounded-quantifier constraint transition. Its packed record has a three-byte header followed by
 * one ten-byte record per constraint branch.
 *
 * <pre>
 * +--------------+-----------------------+-------------------+------------------------+----------------------+
 * | kind (uint8) | branch count (uint16) | successor (int32) | long-array ref (int32) | value count (uint16) |
 * +--------------+-----------------------+-------------------+------------------------+----------------------+
 * | 1 B          | 2 B                   | 4 B               | 4 B                    | 2 B                  |
 * +--------------+-----------------------+-------------------+------------------------+----------------------+
 * | header                               | 10 * branchCount                                                  |
 * +--------------------------------------+-------------------------------------------------------------------+
 * </pre>
 */
public final class DFABQTrackingTransitionConstraints extends DFAAbstractState {

    private static final int FIELD_BRANCH_COUNT = 1;
    private static final int FIELD_BRANCHES = 3;
    private static final int BRANCH_SIZE = 2 * Integer.BYTES + Short.BYTES;
    private static final int FIELD_BRANCH_SUCCESSOR = 0;
    private static final int FIELD_BRANCH_REF = Integer.BYTES;
    private static final int FIELD_BRANCH_VALUE_COUNT = 2 * Integer.BYTES;

    private final long[][] constraints;

    public DFABQTrackingTransitionConstraints(short id, short[] successors, long[][] constraints) {
        super(id, successors);
        this.constraints = constraints;
    }

    @Override
    int encodedSize() {
        return Math.addExact(FIELD_BRANCHES, Math.multiplyExact(BRANCH_SIZE, constraints.length));
    }

    @Override
    void encode(DFAExecutorRecords.Encoder encoder, int recordRef) {
        if (successors.length != constraints.length) {
            throw CompilerDirectives.shouldNotReachHere("invalid bounded-quantifier branch table");
        }
        encoder.putKind(recordRef, DFAExecutorRecords.Kind.bqTransitionConstraints);
        encoder.putUnsignedShort(recordRef + FIELD_BRANCH_COUNT, constraints.length);
        for (int i = 0; i < constraints.length; i++) {
            int branchRef = branchRef(recordRef, i);
            encoder.putNodeRef(branchRef + FIELD_BRANCH_SUCCESSOR, successors[i]);
            encoder.putBQValues(branchRef + FIELD_BRANCH_REF, branchRef + FIELD_BRANCH_VALUE_COUNT, constraints[i]);
        }
    }

    static int getBranchCount(byte[] records, int recordRef) {
        return DFAExecutorRecords.getUnsignedShort(records, recordRef + FIELD_BRANCH_COUNT);
    }

    @CompilerDirectives.EarlyInline
    static int getBranchSuccessor(byte[] records, int recordRef, int index) {
        return DFAExecutorRecords.getInt(records, branchRef(recordRef, index) + FIELD_BRANCH_SUCCESSOR);
    }

    static int getBranchConstraintsRef(byte[] records, int recordRef, int index) {
        return DFAExecutorRecords.getInt(records, branchRef(recordRef, index) + FIELD_BRANCH_REF);
    }

    static int getBranchConstraintCount(byte[] records, int recordRef, int index) {
        return DFAExecutorRecords.getUnsignedShort(records, branchRef(recordRef, index) + FIELD_BRANCH_VALUE_COUNT);
    }

    static int getEncodedSize(byte[] records, int recordRef) {
        return FIELD_BRANCHES + BRANCH_SIZE * getBranchCount(records, recordRef);
    }

    private static int branchRef(int recordRef, int index) {
        return recordRef + FIELD_BRANCHES + BRANCH_SIZE * index;
    }

    @CompilerDirectives.EarlyInline
    static int apply(byte[] records, long[] bqRecords, int recordRef, TRegexDFAExecutorLocals locals, CounterTracker[] counterTrackers) {
        int branchCount = getBranchCount(records, recordRef);
        CompilerAsserts.partialEvaluationConstant(branchCount);
        for (int i = 0; i < branchCount; i++) {
            int constraintRef = getBranchConstraintsRef(records, recordRef, i);
            int constraintCount = getBranchConstraintCount(records, recordRef, i);
            if (constraintsAreSatisfied(bqRecords, constraintRef, constraintCount, locals, counterTrackers)) {
                return getBranchSuccessor(records, recordRef, i);
            }
        }
        return DFAExecutorRecords.NO_REF;
    }

    @ExplodeLoop
    static boolean constraintsAreSatisfied(long[] bqRecords, int constraintsRef, int constraintCount, TRegexDFAExecutorLocals locals, CounterTracker[] counterTrackers) {
        CompilerAsserts.partialEvaluationConstant(bqRecords);
        CompilerAsserts.partialEvaluationConstant(constraintsRef);
        CompilerAsserts.partialEvaluationConstant(constraintCount);
        for (int i = 0; i < constraintCount; i++) {
            long constraint = bqRecords[constraintsRef + i];
            CompilerAsserts.partialEvaluationConstant(constraint);
            int quantifierId = TransitionConstraint.getQuantifierID(constraint);
            CompilerAsserts.partialEvaluationConstant(quantifierId);
            CounterTracker counterTracker = counterTrackers[quantifierId];
            CompilerAsserts.partialEvaluationConstant(counterTracker);
            if (!counterTracker.canExecute(constraint, locals.getFixedData(), locals.getIntArrays())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public JsonValue toJson() {
        return Json.nullValue();
    }
}
