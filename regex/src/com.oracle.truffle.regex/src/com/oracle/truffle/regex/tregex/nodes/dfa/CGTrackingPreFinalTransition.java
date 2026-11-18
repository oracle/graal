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

/**
 * A generic capture-group pre-final transition. Its packed record appends a lazy-transition
 * reference to the common {@link DFAAbstractTransition} fields.
 *
 * <pre>
 * +-------------+------+---------------------------+
 * | byte offset | size | field                     |
 * +-------------+------+---------------------------+
 * | 5           | 4    | lazy-transition reference |
 * +-------------+------+---------------------------+
 * </pre>
 */
public final class CGTrackingPreFinalTransition extends CGTrackingAbstractTransition {

    private static final int RECORD_SIZE = FIELD_TRANSITION_REF + Integer.BYTES;

    /** Reference to a {@link DFACaptureGroupLazyTransition}. */
    private final int transitionRef;

    private CGTrackingPreFinalTransition(short id, short successor, int transitionRef) {
        super(id, successor);
        this.transitionRef = transitionRef;
    }

    public static CGTrackingPreFinalTransition create(short id, short successor, int transitionRef) {
        return new CGTrackingPreFinalTransition(id, successor, transitionRef);
    }

    @Override
    int encodedSize() {
        return RECORD_SIZE;
    }

    @Override
    void encode(DFAExecutorRecords.Encoder encoder, int recordRef) {
        encodeCommon(encoder, recordRef, DFAExecutorRecords.Kind.genericCGPreFinalTransition);
        encoder.putInt(recordRef + FIELD_TRANSITION_REF, transitionRef);
    }

    static int getEncodedSize() {
        return RECORD_SIZE;
    }

    static int apply(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        DFACaptureGroupLazyTransition.apply(getTransitionRef(records, recordRef), locals, executor, true);
        return getSuccessor(records, recordRef);
    }

    static int getCGTrackingCost(byte[] records, int recordRef, TRegexDFAExecutorNode executor) {
        return DFACaptureGroupLazyTransition.getCost(getTransitionRef(records, recordRef), executor);
    }
}
