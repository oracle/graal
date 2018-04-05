/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.regex.tregex.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.Arrays;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class DFACaptureGroupPartialTransitionNode extends Node implements JsonConvertible {

    public static final int FINAL_STATE_RESULT_INDEX = 0;

    public static final byte[] EMPTY_ARRAY_COPIES = {};
    public static final byte[][] EMPTY_INDEX_UPDATES = {};
    public static final byte[][] EMPTY_INDEX_CLEARS = {};

    private static final DFACaptureGroupPartialTransitionNode EMPTY_INSTANCE = new DFACaptureGroupPartialTransitionNode(null, EMPTY_ARRAY_COPIES, EMPTY_INDEX_UPDATES, EMPTY_INDEX_CLEARS);

    @CompilationFinal(dimensions = 1) private final byte[] newOrder;
    @CompilationFinal(dimensions = 1) private final byte[] arrayCopies;
    @CompilationFinal(dimensions = 2) private final byte[][] indexUpdates;
    @CompilationFinal(dimensions = 2) private final byte[][] indexClears;

    private DFACaptureGroupPartialTransitionNode(byte[] newOrder, byte[] arrayCopies, byte[][] indexUpdates, byte[][] indexClears) {
        this.newOrder = newOrder;
        this.arrayCopies = arrayCopies;
        this.indexUpdates = indexUpdates;
        this.indexClears = indexClears;
    }

    public static DFACaptureGroupPartialTransitionNode create(byte[] newOrder, byte[] arrayCopies, byte[][] indexUpdates, byte[][] indexClears) {
        if ((newOrder == null || newOrder.length == 0) && arrayCopies.length == 0 && indexUpdates.length == 0 && indexClears.length == 0) {
            return createEmpty();
        }
        return new DFACaptureGroupPartialTransitionNode(newOrder, arrayCopies, indexUpdates, indexClears);
    }

    public static DFACaptureGroupPartialTransitionNode createEmpty() {
        return EMPTY_INSTANCE;
    }

    public boolean doesReorderResults() {
        return newOrder != null;
    }

    public byte[] getArrayCopies() {
        return arrayCopies;
    }

    public void apply(DFACaptureGroupTrackingData d, final int currentIndex) {
        if (DebugUtil.DEBUG_STEP_EXECUTION) {
            System.out.println("applying " + this);
        }
        CompilerAsserts.partialEvaluationConstant(this);
        if (newOrder != null) {
            System.arraycopy(d.currentResultOrder, 0, d.swap, 0, newOrder.length);
            applyNewOrder(d.currentResultOrder, d.swap);
        }
        applyArrayCopy(d.results, d.currentResultOrder);
        applyIndexUpdate(d.results, d.currentResultOrder, currentIndex);
        applyIndexClear(d.results, d.currentResultOrder);
    }

    public void applyPreFinalStateTransition(DFACaptureGroupTrackingData d, boolean searching, final int currentIndex) {
        if (DebugUtil.DEBUG_STEP_EXECUTION) {
            System.out.println("applying final state transition " + this);
        }
        CompilerAsserts.partialEvaluationConstant(this);
        if (!searching) {
            apply(d, currentIndex);
            return;
        }
        final int source;
        if (newOrder == null) {
            source = 0;
        } else {
            source = Byte.toUnsignedInt(newOrder[0]);
        }
        System.arraycopy(d.results[d.currentResultOrder[source]], 0, d.currentResult, 0, d.currentResult.length);
        applyFinalStateTransition(d, true, currentIndex);
    }

    public void applyFinalStateTransition(DFACaptureGroupTrackingData d, boolean searching, int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (!searching) {
            apply(d, currentIndex);
            return;
        }
        assert arrayCopies.length == 0;
        assert indexUpdates.length <= 1;
        assert indexClears.length <= 1;
        if (indexUpdates.length == 1) {
            assert indexUpdates[0][0] == 0;
            applyFinalStateTransitionIndexUpdates(d, currentIndex);
        }
        if (indexClears.length == 1) {
            assert indexClears[0][0] == 0;
            applyFinalStateTransitionIndexClears(d);
        }
    }

    @ExplodeLoop
    private void applyFinalStateTransitionIndexUpdates(DFACaptureGroupTrackingData d, int currentIndex) {
        for (int i = 1; i < indexUpdates[0].length; i++) {
            final int targetIndex = Byte.toUnsignedInt(indexUpdates[0][i]);
            d.currentResult[targetIndex] = currentIndex;
        }
    }

    @ExplodeLoop
    private void applyFinalStateTransitionIndexClears(DFACaptureGroupTrackingData d) {
        for (int i = 1; i < indexClears[0].length; i++) {
            final int targetIndex = Byte.toUnsignedInt(indexClears[0][i]);
            d.currentResult[targetIndex] = 0;
        }
    }

    @ExplodeLoop
    private void applyNewOrder(int[] currentResultOrder, int[] swap) {
        for (int i = 0; i < newOrder.length; i++) {
            currentResultOrder[i] = swap[Byte.toUnsignedInt(newOrder[i])];
        }
    }

    @ExplodeLoop
    private void applyArrayCopy(int[][] results, int[] currentResultOrder) {
        for (int i = 0; i < arrayCopies.length; i += 2) {
            final int source = Byte.toUnsignedInt(arrayCopies[i]);
            final int target = Byte.toUnsignedInt(arrayCopies[i + 1]);
            System.arraycopy(results[currentResultOrder[source]], 0, results[currentResultOrder[target]], 0, results[currentResultOrder[source]].length);
        }
    }

    @ExplodeLoop
    private void applyIndexUpdate(int[][] results, int[] currentResultOrder, int currentIndex) {
        for (byte[] indexUpdate : indexUpdates) {
            assert indexUpdate.length > 1;
            final int targetArray = Byte.toUnsignedInt(indexUpdate[0]);
            for (int i = 1; i < indexUpdate.length; i++) {
                final int targetIndex = Byte.toUnsignedInt(indexUpdate[i]);
                results[currentResultOrder[targetArray]][targetIndex] = currentIndex;
            }
        }
    }

    @ExplodeLoop
    private void applyIndexClear(int[][] results, int[] currentResultOrder) {
        for (byte[] indexClear : indexClears) {
            assert indexClear.length > 1;
            final int targetArray = Byte.toUnsignedInt(indexClear[0]);
            for (int i = 1; i < indexClear.length; i++) {
                final int targetIndex = Byte.toUnsignedInt(indexClear[i]);
                results[currentResultOrder[targetArray]][targetIndex] = 0;
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DFACaptureGroupPartialTransitionNode)) {
            return false;
        }
        DFACaptureGroupPartialTransitionNode o = (DFACaptureGroupPartialTransitionNode) obj;
        return Arrays.equals(newOrder, o.newOrder) && Arrays.equals(arrayCopies, o.arrayCopies) &&
                        Arrays.deepEquals(indexUpdates, o.indexUpdates) && Arrays.deepEquals(indexClears, o.indexClears);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = Arrays.hashCode(newOrder);
        result = prime * result + Arrays.hashCode(arrayCopies);
        result = prime * result + Arrays.deepHashCode(indexUpdates);
        result = prime * result + Arrays.deepHashCode(indexClears);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DfaCGTransition");
        if (newOrder != null && newOrder.length > 0) {
            sb.append(System.lineSeparator()).append("newOrder: ").append(Arrays.toString(newOrder));
        }
        if (arrayCopies.length > 0) {
            sb.append(System.lineSeparator()).append("arrayCopies: ");
            for (int i = 0; i < arrayCopies.length; i += 2) {
                final int source = Byte.toUnsignedInt(arrayCopies[i]);
                final int target = Byte.toUnsignedInt(arrayCopies[i + 1]);
                sb.append(System.lineSeparator()).append("    ").append(source).append(" -> ").append(target);
            }
        }
        indexManipulationsToString(sb, indexUpdates, "indexUpdates");
        indexManipulationsToString(sb, indexClears, "indexClears");
        return sb.toString();
    }

    private static void indexManipulationsToString(StringBuilder sb, byte[][] indexManipulations, String name) {
        if (indexManipulations.length > 0) {
            sb.append(System.lineSeparator()).append(name).append(": ");
            for (byte[] indexManipulation : indexManipulations) {
                final int targetArray = Byte.toUnsignedInt(indexManipulation[0]);
                sb.append(System.lineSeparator()).append("    ").append(targetArray).append(" <- [");
                for (int i = 1; i < indexManipulation.length; i++) {
                    if (i > 1) {
                        sb.append(", ");
                    }
                    sb.append(Byte.toUnsignedInt(indexManipulation[i]));
                }
                sb.append("]");
            }
        }
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        JsonObject json = Json.obj(Json.prop("newOrder", Json.arrayUnsigned(newOrder)));
        JsonArray copies = Json.array();
        for (int i = 0; i < arrayCopies.length; i += 2) {
            final int source = Byte.toUnsignedInt(arrayCopies[i]);
            final int target = Byte.toUnsignedInt(arrayCopies[i + 1]);
            copies.append(Json.obj(Json.prop("source", source), Json.prop("target", target)));
        }
        json.append(Json.prop("arrayCopies", copies));
        for (byte[] indexUpdate : indexUpdates) {
            json.append(indexManipulationToProp("indexUpdates", indexUpdate));
        }
        for (byte[] indexClear : indexClears) {
            json.append(indexManipulationToProp("indexClears", indexClear));
        }
        return json;
    }

    private static JsonObject.JsonObjectProperty indexManipulationToProp(String name, byte[] values) {
        return Json.prop(name, Json.obj(
                        Json.prop("target", Byte.toUnsignedInt(values[0])),
                        Json.prop("groupStarts", groupEntriesToJsonArray(values)),
                        Json.prop("groupEnds", groupExitsToJsonArray(values))));
    }

    @TruffleBoundary
    private static JsonArray groupEntriesToJsonArray(byte[] gbArray) {
        return groupBoundariesToJsonArray(gbArray, true);
    }

    @TruffleBoundary
    private static JsonArray groupExitsToJsonArray(byte[] gbArray) {
        return groupBoundariesToJsonArray(gbArray, false);
    }

    @TruffleBoundary
    private static JsonArray groupBoundariesToJsonArray(byte[] gbArray, boolean entries) {
        JsonArray array = Json.array();
        for (int i = 1; i < gbArray.length; i++) {
            int intValue = Byte.toUnsignedInt(gbArray[i]);
            if ((intValue & 1) == (entries ? 0 : 1)) {
                array.append(Json.val(intValue / 2));
            }
        }
        return array;
    }
}
