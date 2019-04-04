/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
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

    public static final byte[] EMPTY_REORDER_SWAPS = {};
    public static final byte[] EMPTY_ARRAY_COPIES = {};
    public static final byte[][] EMPTY_INDEX_UPDATES = {};
    public static final byte[][] EMPTY_INDEX_CLEARS = {};

    private static final DFACaptureGroupPartialTransitionNode EMPTY_INSTANCE = new DFACaptureGroupPartialTransitionNode(
                    0, EMPTY_REORDER_SWAPS, EMPTY_ARRAY_COPIES, EMPTY_INDEX_UPDATES, EMPTY_INDEX_CLEARS, (byte) 0);

    private final int id;
    @CompilationFinal(dimensions = 1) private final byte[] reorderSwaps;
    @CompilationFinal(dimensions = 1) private final byte[] arrayCopies;
    @CompilationFinal(dimensions = 2) private final byte[][] indexUpdates;
    @CompilationFinal(dimensions = 2) private final byte[][] indexClears;
    private final byte preReorderFinalStateResultIndex;

    private DFACaptureGroupPartialTransitionNode(int id, byte[] reorderSwaps, byte[] arrayCopies, byte[][] indexUpdates, byte[][] indexClears,
                    byte preReorderFinalStateResultIndex) {
        this.id = id;
        this.reorderSwaps = reorderSwaps;
        this.arrayCopies = arrayCopies;
        this.indexUpdates = indexUpdates;
        this.indexClears = indexClears;
        this.preReorderFinalStateResultIndex = preReorderFinalStateResultIndex;
    }

    /**
     * Creates a new {@link DFACaptureGroupPartialTransitionNode}. All numeric values stored in this
     * class refer to indices of the first or second dimension of
     * {@link DFACaptureGroupTrackingData#results} and are stored as {@code byte} to save space.
     * This is OK since the dimensions of {@link DFACaptureGroupTrackingData#results} are capped by
     * {@link com.oracle.truffle.regex.tregex.TRegexOptions#TRegexMaxNumberOfNFAStatesInOneDFATransition}
     * (1st dimension) and
     * {@link com.oracle.truffle.regex.tregex.TRegexOptions#TRegexMaxNumberOfCaptureGroups}
     * {@code * 2} (2nd dimension, times two because we need two array slots per capture group, one
     * for the beginning and one for the end). <br>
     * Although we treat {@link DFACaptureGroupTrackingData#results} as a 2D-array here, it is
     * actually flattened into one dimension for performance. For that reason, we additionally have
     * {@link DFACaptureGroupTrackingData#currentResultOrder}, which stores the offset of every
     * "row" in the 2D-array. We need to be able to reorder the rows of the 2D-array, and we do that
     * by simply reordering {@link DFACaptureGroupTrackingData#currentResultOrder}. <br>
     * Example: <br>
     * If {@link DFACaptureGroupTrackingData#results} is a 3x2 array, then
     * {@link DFACaptureGroupTrackingData#currentResultOrder} will initially contain [0, 2, 4]. If
     * we want to swap the first two rows of the 2D array,
     * {@link DFACaptureGroupTrackingData#currentResultOrder} becomes [2, 0, 4].
     *
     * @param reorderSwaps reorder {@link DFACaptureGroupTrackingData#currentResultOrder} using a
     *            sequence of swap operations described in this array. Every two elements in this
     *            array denote one swap operation.
     *            <p>
     *            Example: <br>
     *            If {@code currentResultOrder = DFACaptureGroupTrackingData#currentResultOrder} and
     *            {@code reorderSwaps = [0, 1, 1, 2]}, then {@code currentResultOrder[0]} will be
     *            swapped with {@code currentResultOrder[1]}, and {@code currentResultOrder[1]} will
     *            be swapped with {@code currentResultOrder[2]}, in that order.
     *            </p>
     * @param arrayCopies copy rows of {@link DFACaptureGroupTrackingData#results} (1st dimension)
     *            as described in this array. Every two elements in this array denote one copy
     *            operation, where the first element is the source, and the second is the target.
     *            The copy operations will be applied <em>after</em> the reordering of
     *            {@link DFACaptureGroupTrackingData#currentResultOrder} with {@code reorderSwaps}.
     *            <p>
     *            Example: <br>
     *            If {@code results = DFACaptureGroupTrackingData#results} and
     *            {@code arrayCopies = [0, 1, 2, 3]}, then the contents of {@code results[0]} will
     *            be copied into {@code results[1]}, and the contents of {@code results[2]} will be
     *            copied into {@code results[3]}.
     *            </p>
     * @param indexUpdates denotes which index of which array in
     *            {@link DFACaptureGroupTrackingData#results} shall be updated to
     *            {@code currentIndex} in
     *            {@link #apply(TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int)},
     *            {@link #applyPreFinalStateTransition(TRegexDFAExecutorNode, DFACaptureGroupTrackingData, boolean, int)}
     *            and
     *            {@link #applyFinalStateTransition(TRegexDFAExecutorNode, DFACaptureGroupTrackingData, boolean, int)}
     *            . In every row (1st dimension element) of this 2D array, the first value is the
     *            index of one row in {@link DFACaptureGroupTrackingData#results}, all following
     *            values are indices in that row that shall be set to {@code currentIndex}.
     *            <p>
     *            Example: <br>
     *            If {@code results = DFACaptureGroupTrackingData#results} and
     *            {@code indexUpdates = [[0, 1, 2], [3, 4]]}, then {@code results[0][1]},
     *            {@code results[0][2]} and {@code results[3][4]} will be set to
     *            {@code currentIndex}.
     *            </p>
     * @param indexClears denotes which index of which array in
     *            {@link DFACaptureGroupTrackingData#results} shall be updated to {@code 0} in
     *            {@link #apply(TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int)},
     *            {@link #applyPreFinalStateTransition(TRegexDFAExecutorNode, DFACaptureGroupTrackingData, boolean, int)}
     *            and
     *            {@link #applyFinalStateTransition(TRegexDFAExecutorNode, DFACaptureGroupTrackingData, boolean, int)}
     *            , analogous to {@code indexUpdates}.
     * @param preReorderFinalStateResultIndex denotes the row (1st dimension element) of
     *            {@link DFACaptureGroupTrackingData#results} that corresponds to the NFA final
     *            state <em>before</em> the reordering given by {@code reorderSwaps} is applied.
     *            This is needed in
     *            {@link #applyPreFinalStateTransition(TRegexDFAExecutorNode, DFACaptureGroupTrackingData, boolean, int)}
     *            when {@link TRegexDFAExecutorNode#isSearching()} is {@code true}, because in that
     *            case we need to be able to apply copy the current result corresponding to the NFA
     *            final state without doing any reordering.
     * @return a new {@link DFACaptureGroupPartialTransitionNode}, or a static empty instance if all
     *         arguments are empty or zero.
     */
    public static DFACaptureGroupPartialTransitionNode create(
                    DFAGenerator dfaGen,
                    byte[] reorderSwaps,
                    byte[] arrayCopies,
                    byte[][] indexUpdates,
                    byte[][] indexClears,
                    byte preReorderFinalStateResultIndex) {
        assert (reorderSwaps.length & 1) == 0 : "reorderSwaps must have an even number of elements";
        if (reorderSwaps.length == 0 && arrayCopies.length == 0 && indexUpdates.length == 0 && indexClears.length == 0 && preReorderFinalStateResultIndex == 0) {
            return getEmptyInstance();
        }
        return new DFACaptureGroupPartialTransitionNode(dfaGen.getCgPartialTransitionIDCounter().inc(),
                        reorderSwaps, arrayCopies, indexUpdates, indexClears, preReorderFinalStateResultIndex);
    }

    public static DFACaptureGroupPartialTransitionNode getEmptyInstance() {
        return EMPTY_INSTANCE;
    }

    public int getId() {
        return id;
    }

    public boolean doesReorderResults() {
        return reorderSwaps.length > 0;
    }

    public byte[] getArrayCopies() {
        return arrayCopies;
    }

    public void apply(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, final int currentIndex) {
        if (executor.recordExecution()) {
            executor.getDebugRecorder().recordCGPartialTransition(currentIndex, id);
        }
        CompilerAsserts.partialEvaluationConstant(this);
        applyReorder(d.currentResultOrder);
        applyArrayCopy(d.results, d.currentResultOrder, d.currentResult.length);
        applyIndexUpdate(d.results, d.currentResultOrder, currentIndex);
        applyIndexClear(d.results, d.currentResultOrder);
    }

    public void applyPreFinalStateTransition(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, boolean searching, final int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (!searching) {
            apply(executor, d, currentIndex);
            return;
        }
        if (executor.recordExecution()) {
            executor.getDebugRecorder().recordCGPartialTransition(currentIndex, id);
        }
        d.exportResult(preReorderFinalStateResultIndex);
        applyFinalStateTransition(executor, d, true, currentIndex);
    }

    public void applyFinalStateTransition(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, boolean searching, int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (!searching) {
            apply(executor, d, currentIndex);
            return;
        }
        if (executor.recordExecution()) {
            executor.getDebugRecorder().recordCGPartialTransition(currentIndex, id);
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
    private void applyReorder(int[] currentResultOrder) {
        for (int i = 0; i < reorderSwaps.length; i += 2) {
            final int source = Byte.toUnsignedInt(reorderSwaps[i]);
            final int target = Byte.toUnsignedInt(reorderSwaps[i + 1]);
            final int tmp = currentResultOrder[source];
            currentResultOrder[source] = currentResultOrder[target];
            currentResultOrder[target] = tmp;
        }
    }

    @ExplodeLoop
    private void applyArrayCopy(int[] results, int[] currentResultOrder, int length) {
        for (int i = 0; i < arrayCopies.length; i += 2) {
            final int source = Byte.toUnsignedInt(arrayCopies[i]);
            final int target = Byte.toUnsignedInt(arrayCopies[i + 1]);
            System.arraycopy(results, currentResultOrder[source], results, currentResultOrder[target], length);
        }
    }

    @ExplodeLoop
    private void applyIndexUpdate(int[] results, int[] currentResultOrder, int currentIndex) {
        for (byte[] indexUpdate : indexUpdates) {
            assert indexUpdate.length > 1;
            final int targetArray = Byte.toUnsignedInt(indexUpdate[0]);
            for (int i = 1; i < indexUpdate.length; i++) {
                final int targetIndex = Byte.toUnsignedInt(indexUpdate[i]);
                results[currentResultOrder[targetArray] + targetIndex] = currentIndex;
            }
        }
    }

    @ExplodeLoop
    private void applyIndexClear(int[] results, int[] currentResultOrder) {
        for (byte[] indexClear : indexClears) {
            assert indexClear.length > 1;
            final int targetArray = Byte.toUnsignedInt(indexClear[0]);
            for (int i = 1; i < indexClear.length; i++) {
                final int targetIndex = Byte.toUnsignedInt(indexClear[i]);
                results[currentResultOrder[targetArray] + targetIndex] = 0;
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DFACaptureGroupPartialTransitionNode)) {
            return false;
        }
        DFACaptureGroupPartialTransitionNode o = (DFACaptureGroupPartialTransitionNode) obj;
        return Arrays.equals(reorderSwaps, o.reorderSwaps) && Arrays.equals(arrayCopies, o.arrayCopies) &&
                        Arrays.deepEquals(indexUpdates, o.indexUpdates) && Arrays.deepEquals(indexClears, o.indexClears);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = Arrays.hashCode(reorderSwaps);
        result = prime * result + Arrays.hashCode(arrayCopies);
        result = prime * result + Arrays.deepHashCode(indexUpdates);
        result = prime * result + Arrays.deepHashCode(indexClears);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DfaCGTransition");
        if (reorderSwaps.length > 0) {
            sb.append(System.lineSeparator()).append("reorderSwaps: ").append(Arrays.toString(reorderSwaps));
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
        JsonObject json = Json.obj(Json.prop("id", id), Json.prop("reorderSwaps", Json.arrayUnsigned(reorderSwaps)));
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
