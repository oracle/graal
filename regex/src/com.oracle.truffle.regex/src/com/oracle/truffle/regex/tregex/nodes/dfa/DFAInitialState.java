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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * This state is responsible for selecting a DFA's initial state based on the index the search
 * starts from. Successors are entry points in case we start matching at the beginning of the input
 * string, followed by entry points in case we do not start matching at the beginning of the input
 * string. If possible matches must start at the beginning of the input string, entry points may be
 * -1.
 *
 * <pre>
 * +-------------+------+-----------------------------------------------+
 * | byte offset | size | field                                         |
 * +-------------+------+-----------------------------------------------+
 * | 0           | 1    | kind                                          |
 * | 1           | 1    | flags                                         |
 * | 2           | 2    | successor count (uint16)                      |
 * | 4           | 4*n  | successor references (int32[])                |
 * | 4 + 4*n     | 2*n  | generic-CG last-transition table (int16[])   |
 * +-------------+------+-----------------------------------------------+
 * </pre>
 */
public class DFAInitialState extends DFAAbstractState {

    private static final int FIELD_FLAGS = 1;
    private static final int FIELD_SUCCESSOR_COUNT = 2;
    private static final int FIELD_SUCCESSORS = 4;
    private static final int FLAG_HAS_UNANCHORED_ENTRY = 1;
    private static final int FLAG_HAS_CG_LAST_TRANSITIONS = 1 << 1;

    private final short[] cgLastTransition;
    private final boolean hasUnanchoredEntry;

    public DFAInitialState(short[] successors, short[] cgLastTransition) {
        super((short) 0, successors);
        this.cgLastTransition = cgLastTransition;
        this.hasUnanchoredEntry = initUnanchoredEntry(successors);
    }

    private static boolean initUnanchoredEntry(short[] successors) {
        for (int i = successors.length / 2; i < successors.length; i++) {
            if (successors[i] != -1) {
                return true;
            }
        }
        return false;
    }

    @Override
    int encodedSize() {
        int n = successors.length;
        if (cgLastTransition != null && cgLastTransition.length != n) {
            throw CompilerDirectives.shouldNotReachHere("invalid initial-state transition table");
        }
        int size = Math.addExact(FIELD_SUCCESSORS, Math.multiplyExact(Integer.BYTES, n));
        return cgLastTransition == null ? size : Math.addExact(size, Math.multiplyExact(Short.BYTES, n));
    }

    @Override
    void encode(DFAExecutorRecords.Encoder encoder, int recordRef) {
        assert recordRef == DFAExecutorRecords.INITIAL_STATE_REF;
        encoder.putKind(recordRef, DFAExecutorRecords.Kind.initialState);
        int flags = (hasUnanchoredEntry ? FLAG_HAS_UNANCHORED_ENTRY : 0) | (cgLastTransition != null ? FLAG_HAS_CG_LAST_TRANSITIONS : 0);
        encoder.putByte(recordRef + FIELD_FLAGS, flags);
        encoder.putUnsignedShort(recordRef + FIELD_SUCCESSOR_COUNT, successors.length);
        for (int i = 0; i < successors.length; i++) {
            encoder.putNodeRef(recordRef + FIELD_SUCCESSORS + Integer.BYTES * i, successors[i]);
        }
        if (cgLastTransition != null) {
            int lastTransitionRef = recordRef + FIELD_SUCCESSORS + Integer.BYTES * successors.length;
            for (int i = 0; i < cgLastTransition.length; i++) {
                encoder.putShort(lastTransitionRef + Short.BYTES * i, cgLastTransition[i]);
            }
        }
    }

    static int getSuccessorCount(byte[] records) {
        return DFAExecutorRecords.getUnsignedShort(records, FIELD_SUCCESSOR_COUNT);
    }

    @CompilerDirectives.EarlyInline
    static int getSuccessor(byte[] records, int index) {
        return DFAExecutorRecords.getInt(records, FIELD_SUCCESSORS + Integer.BYTES * index);
    }

    static int getLastTransition(byte[] records, int index) {
        int count = getSuccessorCount(records);
        return DFAExecutorRecords.getShort(records, FIELD_SUCCESSORS + Integer.BYTES * count + Short.BYTES * index);
    }

    static boolean hasUnAnchoredEntry(byte[] records) {
        return (records[FIELD_FLAGS] & FLAG_HAS_UNANCHORED_ENTRY) != 0;
    }

    static int getEncodedSize(byte[] records) {
        int n = getSuccessorCount(records);
        int size = FIELD_SUCCESSORS + Integer.BYTES * n;
        if ((records[FIELD_FLAGS] & FLAG_HAS_CG_LAST_TRANSITIONS) != 0) {
            size += Short.BYTES * n;
        }
        return size;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj();
    }
}
