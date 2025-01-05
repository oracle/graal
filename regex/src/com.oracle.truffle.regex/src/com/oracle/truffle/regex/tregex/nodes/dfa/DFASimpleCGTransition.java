/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.EmptyArrays;

public final class DFASimpleCGTransition implements JsonConvertible {

    private static final byte[] EMPTY_ARRAY = EmptyArrays.BYTE;
    /**
     * Separate object because this is used as a marker value!
     */
    private static final byte[] FULL_CLEAR_ARRAY = {};

    private static final DFASimpleCGTransition EMPTY_INSTANCE = new DFASimpleCGTransition(EMPTY_ARRAY, EMPTY_ARRAY, -1);

    @CompilationFinal(dimensions = 1) private final byte[] indexUpdates;
    @CompilationFinal(dimensions = 1) private final byte[] indexClears;
    private final int lastGroup;

    private DFASimpleCGTransition(byte[] indexUpdates, byte[] indexClears, int lastGroup) {
        this.indexUpdates = indexUpdates;
        this.indexClears = indexClears;
        this.lastGroup = lastGroup;
    }

    public static DFASimpleCGTransition create(NFAStateTransition t, boolean fullClear) {
        if (t == null || (!fullClear && t.getGroupBoundaries().isEmpty())) {
            return getEmptyInstance();
        }
        t.getGroupBoundaries().materializeArrays();
        return new DFASimpleCGTransition(t.getGroupBoundaries().isEmpty() ? EMPTY_ARRAY : t.getGroupBoundaries().updatesToByteArray(),
                        fullClear ? FULL_CLEAR_ARRAY : t.getGroupBoundaries().clearsToByteArray(), t.getGroupBoundaries().getLastGroup());
    }

    public static DFASimpleCGTransition getEmptyInstance() {
        return EMPTY_INSTANCE;
    }

    public void apply(int[] result, int currentIndex, boolean trackLastGroup, boolean forward) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (indexClears == FULL_CLEAR_ARRAY) {
            Arrays.fill(result, -1);
        } else {
            applyIndexClear(result);
        }
        applyIndexUpdate(result, currentIndex);
        if (trackLastGroup && lastGroup != -1) {
            applyLastGroup(result, forward);
        }
    }

    public void applyFinal(DFACaptureGroupTrackingData cgData, int currentIndex, boolean simpleCGMustCopy, boolean trackLastGroup, boolean forward) {
        CompilerAsserts.partialEvaluationConstant(this);
        int[] result = simpleCGMustCopy ? cgData.currentResult : cgData.results;
        if (indexClears == FULL_CLEAR_ARRAY) {
            Arrays.fill(result, -1);
        } else {
            applyIndexClear(result);
        }
        applyIndexUpdate(result, currentIndex);
        if (trackLastGroup && lastGroup != -1) {
            if (simpleCGMustCopy) {
                applyLastGroup(cgData.currentResult, forward);
            } else {
                applyLastGroup(cgData.results, forward);
            }
        }
    }

    private void applyLastGroup(int[] result, boolean forward) {
        if (forward || result[result.length - 1] == -1) {
            result[result.length - 1] = lastGroup;
        }
    }

    @ExplodeLoop
    private void applyIndexUpdate(int[] result, int currentIndex) {
        for (int i = 0; i < indexUpdates.length; i++) {
            final int targetIndex = Byte.toUnsignedInt(indexUpdates[i]);
            result[targetIndex] = currentIndex;
        }
    }

    @ExplodeLoop
    private void applyIndexClear(int[] result) {
        for (int i = 0; i < indexClears.length; i++) {
            final int targetIndex = Byte.toUnsignedInt(indexClears[i]);
            result[targetIndex] = -1;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DFASimpleCGTransition)) {
            return false;
        }
        DFASimpleCGTransition o = (DFASimpleCGTransition) obj;
        return Arrays.equals(indexUpdates, o.indexUpdates) && Arrays.equals(indexClears, o.indexClears);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(indexUpdates) * 31 + Arrays.hashCode(indexClears);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("indexUpdates", DFACaptureGroupPartialTransition.IndexOperation.groupBoundariesToJsonObject(indexUpdates)),
                        Json.prop("indexClears", DFACaptureGroupPartialTransition.IndexOperation.groupBoundariesToJsonObject(indexClears)));
    }
}
