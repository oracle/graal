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
package com.oracle.truffle.regex.tregex.dfa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFACaptureGroupPartialTransition;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public final class DFACaptureGroupLazyTransitionBuilder implements JsonConvertible {

    private static final int UNINITIALIZED = -2;
    public static final int DO_NOT_SET_LAST_TRANSITION = -1;

    private final DFAGenerator dfaGen;
    private final short id;
    /** Array of {@link DFACaptureGroupPartialTransition} records. */
    private final byte[][] partialTransitionRecords;
    /** A {@link DFACaptureGroupPartialTransition} record. */
    private final byte[] transitionToFinalStateRecord;
    /** A {@link DFACaptureGroupPartialTransition} record. */
    private final byte[] transitionToAnchoredFinalStateRecord;
    private short lastTransitionIndex = UNINITIALIZED;

    public DFACaptureGroupLazyTransitionBuilder(DFAGenerator dfaGen,
                    short id,
                    byte[][] partialTransitionRecords,
                    byte[] transitionToFinalStateRecord,
                    byte[] transitionToAnchoredFinalStateRecord) {
        this.dfaGen = dfaGen;
        this.id = id;
        this.partialTransitionRecords = partialTransitionRecords;
        this.transitionToFinalStateRecord = transitionToFinalStateRecord;
        this.transitionToAnchoredFinalStateRecord = transitionToAnchoredFinalStateRecord;
    }

    public short getId() {
        return id;
    }

    public byte[][] getPartialTransitionRecords() {
        return partialTransitionRecords;
    }

    public byte[] getTransitionToFinalStateRecord() {
        return transitionToFinalStateRecord;
    }

    public byte[] getTransitionToAnchoredFinalStateRecord() {
        return transitionToAnchoredFinalStateRecord;
    }

    public short getLastTransitionIndex() {
        assert this.lastTransitionIndex != UNINITIALIZED;
        return lastTransitionIndex;
    }

    public void setLastTransitionIndex(int lastTransitionIndex) {
        assert this.lastTransitionIndex == UNINITIALIZED;
        assert lastTransitionIndex <= Short.MAX_VALUE;
        this.lastTransitionIndex = (short) lastTransitionIndex;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        JsonArray partialTransitionsJson = Json.array();
        for (byte[] partialTransitionRecord : partialTransitionRecords) {
            partialTransitionsJson.append(DFACaptureGroupPartialTransition.toJson(partialTransitionRecord, dfaGen.getCGPartialTransitionId(partialTransitionRecord)));
        }
        JsonObject json = Json.obj(Json.prop("partialTransitions", partialTransitionsJson));
        if (transitionToAnchoredFinalStateRecord != null) {
            json.append(Json.prop("transitionToAnchoredFinalState",
                            DFACaptureGroupPartialTransition.toJson(transitionToAnchoredFinalStateRecord, dfaGen.getCGPartialTransitionId(transitionToAnchoredFinalStateRecord))));
        }
        if (transitionToFinalStateRecord != null) {
            json.append(Json.prop("transitionToFinalState",
                            DFACaptureGroupPartialTransition.toJson(transitionToFinalStateRecord, dfaGen.getCGPartialTransitionId(transitionToFinalStateRecord))));
        }
        return json;
    }
}
