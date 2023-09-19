/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.EmptyArrays;

public final class DFACaptureGroupPartialTransition implements JsonConvertible {

    public static final int FINAL_STATE_RESULT_INDEX = 0;

    public static final byte[] EMPTY = EmptyArrays.BYTE;
    public static final IndexOperation[] EMPTY_INDEX_OPS = {};
    public static final LastGroupUpdate[] EMPTY_LAST_GROUP_UPDATES = {};

    private static final DFACaptureGroupPartialTransition EMPTY_INSTANCE = new DFACaptureGroupPartialTransition(
                    0, EMPTY, EMPTY, EMPTY_INDEX_OPS, EMPTY_INDEX_OPS, EMPTY_LAST_GROUP_UPDATES, (byte) 0);

    private final int id;
    @CompilationFinal(dimensions = 1) private final byte[] reorderSwaps;
    @CompilationFinal(dimensions = 1) private final byte[] arrayCopies;
    @CompilationFinal(dimensions = 1) private final IndexOperation[] indexUpdates;
    @CompilationFinal(dimensions = 1) private final IndexOperation[] indexClears;
    @CompilationFinal(dimensions = 1) private final LastGroupUpdate[] lastGroupUpdates;
    private final byte preReorderFinalStateResultIndex;

    private DFACaptureGroupPartialTransition(int id, byte[] reorderSwaps, byte[] arrayCopies, IndexOperation[] indexUpdates, IndexOperation[] indexClears,
                    LastGroupUpdate[] lastGroupUpdates, byte preReorderFinalStateResultIndex) {
        this.id = id;
        this.reorderSwaps = reorderSwaps;
        this.arrayCopies = arrayCopies;
        this.indexUpdates = indexUpdates;
        this.indexClears = indexClears;
        this.lastGroupUpdates = lastGroupUpdates;
        this.preReorderFinalStateResultIndex = preReorderFinalStateResultIndex;
    }

    /**
     * Creates a new {@link DFACaptureGroupPartialTransition}. All numeric values stored in this
     * class refer to indices of the first or second dimension of
     * {@link DFACaptureGroupTrackingData#results} and are stored as {@code byte} to save space.
     * This is OK since the dimensions of {@link DFACaptureGroupTrackingData#results} are capped by
     * {@link com.oracle.truffle.regex.tregex.TRegexOptions#TRegexMaxNumberOfNFAStatesInOneDFATransition}
     * (1st dimension) and
     * {@link com.oracle.truffle.regex.tregex.TRegexOptions#TRegexMaxNumberOfCaptureGroupsForDFA}
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
     *            {@link #applyPreFinalStateTransition(TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int, boolean)}
     *            and
     *            {@link #applyFinalStateTransition(TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int)}
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
     *            {@link #applyPreFinalStateTransition(TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int, boolean)}
     *            and
     *            {@link #applyFinalStateTransition(TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int)}
     *            , analogous to {@code indexUpdates}.
     * @param preReorderFinalStateResultIndex denotes the row (1st dimension element) of
     *            {@link DFACaptureGroupTrackingData#results} that corresponds to the NFA final
     *            state <em>before</em> the reordering given by {@code reorderSwaps} is applied.
     *            This is needed in
     *            {@link #applyPreFinalStateTransition(TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int, boolean)}
     *            when {@link TRegexDFAExecutorNode#isSearching()} is {@code true}, because in that
     *            case we need to be able to apply copy the current result corresponding to the NFA
     *            final state without doing any reordering.
     * @return a new {@link DFACaptureGroupPartialTransition}, or a static empty instance if all
     *         arguments are empty or zero.
     */
    public static DFACaptureGroupPartialTransition create(
                    DFAGenerator dfaGen,
                    byte[] reorderSwaps,
                    byte[] arrayCopies,
                    IndexOperation[] indexUpdates,
                    IndexOperation[] indexClears,
                    LastGroupUpdate[] lastGroupUpdates,
                    byte preReorderFinalStateResultIndex) {
        Counter idCounter = dfaGen.getCgPartialTransitionIDCounter();
        DFACaptureGroupPartialTransition ret = createInternal(idCounter.getCount(), reorderSwaps, arrayCopies, indexUpdates, indexClears, lastGroupUpdates, preReorderFinalStateResultIndex);
        if (ret.isEmpty()) {
            return ret;
        }
        EconomicMap<DFACaptureGroupPartialTransition, DFACaptureGroupPartialTransition> dedup = dfaGen.getCompilationBuffer().getLazyTransitionDeduplicationMap();
        DFACaptureGroupPartialTransition lookup = dedup.get(ret);
        if (lookup != null) {
            return lookup;
        }
        dedup.put(ret, ret);
        idCounter.inc();
        return ret;
    }

    private static DFACaptureGroupPartialTransition createInternal(
                    int id,
                    byte[] reorderSwaps,
                    byte[] arrayCopies,
                    IndexOperation[] indexUpdates,
                    IndexOperation[] indexClears,
                    LastGroupUpdate[] lastGroupUpdates,
                    byte preReorderFinalStateResultIndex) {
        assert (reorderSwaps.length & 1) == 0 : "reorderSwaps must have an even number of elements";
        if (reorderSwaps.length == 0 && arrayCopies.length == 0 && indexUpdates.length == 0 && indexClears.length == 0 && lastGroupUpdates.length == 0 && preReorderFinalStateResultIndex == 0) {
            return getEmptyInstance();
        }
        return new DFACaptureGroupPartialTransition(id, reorderSwaps, arrayCopies, indexUpdates, indexClears, lastGroupUpdates, preReorderFinalStateResultIndex);
    }

    public static DFACaptureGroupPartialTransition intersect(DFACaptureGroupPartialTransition[] transitions) {
        byte[] reorderSwaps = commonArray(transitions, DFACaptureGroupPartialTransition::getReorderSwaps);
        byte[] arrayCopies = commonArray(transitions, DFACaptureGroupPartialTransition::getArrayCopies);
        if (reorderSwaps == null || arrayCopies == null || !samePreReorderFinalStateResultIndex(transitions)) {
            // can't extract common operations from partial transitions that re-arrange the target
            // arrays in different ways
            return getEmptyInstance();
        }
        return createInternal(0,
                        reorderSwaps,
                        arrayCopies,
                        commonOps(transitions, DFACaptureGroupPartialTransition::getIndexUpdates, IndexOperation[]::new, EMPTY_INDEX_OPS),
                        commonOps(transitions, DFACaptureGroupPartialTransition::getIndexClears, IndexOperation[]::new, EMPTY_INDEX_OPS),
                        commonOps(transitions, DFACaptureGroupPartialTransition::getLastGroupUpdates, LastGroupUpdate[]::new, EMPTY_LAST_GROUP_UPDATES),
                        transitions[0].preReorderFinalStateResultIndex);
    }

    private static boolean samePreReorderFinalStateResultIndex(DFACaptureGroupPartialTransition[] transitions) {
        byte cmp = transitions[0].preReorderFinalStateResultIndex;
        for (int i = 1; i < transitions.length; i++) {
            if (cmp != transitions[i].preReorderFinalStateResultIndex) {
                return false;
            }
        }
        return true;
    }

    private static byte[] commonArray(DFACaptureGroupPartialTransition[] transitions, Function<DFACaptureGroupPartialTransition, byte[]> getter) {
        byte[] array = getter.apply(transitions[0]);
        for (int i = 1; i < transitions.length; i++) {
            if (!Arrays.equals(array, getter.apply(transitions[i]))) {
                return null;
            }
        }
        return array;
    }

    private static <T> T[] commonOps(DFACaptureGroupPartialTransition[] transitions, Function<DFACaptureGroupPartialTransition, T[]> getter, IntFunction<T[]> arraySupplier, T[] emptyInstance) {
        T[] first = getter.apply(transitions[0]);
        if (first == emptyInstance) {
            return emptyInstance;
        }
        T[] common = arraySupplier.apply(first.length);
        int iC = 0;
        for (T op : first) {
            if (allContain(transitions, op, getter)) {
                common[iC++] = op;
            }
        }
        return iC == 0 ? emptyInstance : iC == common.length ? first : Arrays.copyOf(common, iC);
    }

    private static <T> boolean allContain(DFACaptureGroupPartialTransition[] transitions, T op, Function<DFACaptureGroupPartialTransition, T[]> getter) {
        for (int i = 1; i < transitions.length; i++) {
            if (!contains(getter.apply(transitions[i]), op)) {
                return false;
            }
        }
        return true;
    }

    public DFACaptureGroupPartialTransition subtract(DFACaptureGroupPartialTransition other) {
        assert other.reorderSwaps == EMPTY || Arrays.equals(other.reorderSwaps, reorderSwaps);
        assert other.arrayCopies == EMPTY || Arrays.equals(other.arrayCopies, arrayCopies);
        return createInternal(id,
                        other.reorderSwaps != EMPTY ? EMPTY : reorderSwaps,
                        other.arrayCopies != EMPTY ? EMPTY : arrayCopies,
                        subtract(indexUpdates, other.indexUpdates, IndexOperation[]::new, EMPTY_INDEX_OPS),
                        subtract(indexClears, other.indexClears, IndexOperation[]::new, EMPTY_INDEX_OPS),
                        subtract(lastGroupUpdates, other.lastGroupUpdates, LastGroupUpdate[]::new, EMPTY_LAST_GROUP_UPDATES),
                        preReorderFinalStateResultIndex);
    }

    private static <T> T[] subtract(T[] a, T[] b, IntFunction<T[]> arraySupplier, T[] emptyInstance) {
        if (b == emptyInstance) {
            return a;
        }
        if (b.length == a.length) {
            return emptyInstance;
        }
        assert a.length > b.length;
        T[] subtracted = arraySupplier.apply(a.length - b.length);
        int i = 0;
        for (T op : a) {
            if (!contains(b, op)) {
                subtracted[i++] = op;
            }
        }
        assert i == subtracted.length;
        return subtracted;
    }

    private static <T> boolean contains(T[] ops, T op) {
        for (T cmp : ops) {
            if (op.equals(cmp)) {
                return true;
            }
        }
        return false;
    }

    public static DFACaptureGroupPartialTransition getEmptyInstance() {
        return EMPTY_INSTANCE;
    }

    public boolean isEmpty() {
        return this == EMPTY_INSTANCE;
    }

    public int getId() {
        return id;
    }

    public boolean doesReorderResults() {
        return reorderSwaps.length > 0;
    }

    public byte[] getReorderSwaps() {
        return reorderSwaps;
    }

    public byte[] getArrayCopies() {
        return arrayCopies;
    }

    public IndexOperation[] getIndexUpdates() {
        return indexUpdates;
    }

    public IndexOperation[] getIndexClears() {
        return indexClears;
    }

    public LastGroupUpdate[] getLastGroupUpdates() {
        return lastGroupUpdates;
    }

    public int getCost() {
        int cost = reorderSwaps.length + arrayCopies.length + lastGroupUpdates.length;
        for (IndexOperation op : indexUpdates) {
            cost += op.indices.length;
        }
        for (IndexOperation op : indexClears) {
            cost += op.indices.length;
        }
        return cost;
    }

    public void apply(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, final int currentIndex) {
        apply(executor, d, currentIndex, false, false);
    }

    public void apply(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, final int currentIndex, boolean preFinal, boolean export) {
        if (preFinal) {
            applyPreFinalStateTransition(executor, d, currentIndex, export);
        } else {
            applyRegular(executor, d, currentIndex);
        }
    }

    private void applyRegular(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, final int currentIndex) {
        if (executor.recordExecution()) {
            executor.getDebugRecorder().recordCGPartialTransition(currentIndex, id);
        }
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(executor);
        if (executor.getMaxNumberOfNFAStates() == 1) {
            assert d.currentResultOrder == null;
            assert reorderSwaps.length == 0;
            assert arrayCopies.length == 0;
            assert indexUpdates.length <= 1;
            assert indexClears.length <= 1;
            assert lastGroupUpdates.length <= 1;
            if (indexUpdates.length > 0) {
                writeDirect(d.results, 0, indexUpdates[0].indices, currentIndex);
            }
            if (indexClears.length > 0) {
                writeDirect(d.results, 0, indexClears[0].indices, -1);
            }
            if (lastGroupUpdates.length > 0 && executor.getProperties().tracksLastGroup()) {
                assert lastGroupUpdates[0].getTargetArray() == 0;
                d.results[d.results.length - 1] = lastGroupUpdates[0].getLastGroup();
            }
        } else {
            applyReorder(d.currentResultOrder);
            applyArrayCopy(d.results, d.currentResultOrder, d.currentResult.length);
            applyIndexOps(indexUpdates, d.results, d.currentResultOrder, currentIndex);
            applyIndexOps(indexClears, d.results, d.currentResultOrder, -1);
            if (executor.getProperties().tracksLastGroup()) {
                applyLastGroupUpdate(d.results, d.currentResultOrder, d.currentResult.length);
            }
        }
    }

    private void applyPreFinalStateTransition(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, final int currentIndex, boolean export) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(executor);
        if (!executor.isSearching()) {
            apply(executor, d, currentIndex);
            return;
        }
        if (executor.recordExecution()) {
            executor.getDebugRecorder().recordCGPartialTransition(currentIndex, id);
        }
        if (export) {
            d.exportResult(executor, preReorderFinalStateResultIndex);
        }
        applyFinalStateTransition(executor, d, currentIndex);
    }

    public void applyFinalStateTransition(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(executor);
        if (!executor.isSearching()) {
            apply(executor, d, currentIndex);
            return;
        }
        if (executor.recordExecution()) {
            executor.getDebugRecorder().recordCGPartialTransition(currentIndex, id);
        }
        assert arrayCopies.length == 0;
        assert indexUpdates.length <= 1;
        assert indexClears.length <= 1;
        assert lastGroupUpdates.length <= 1;
        if (indexUpdates.length == 1) {
            assert indexUpdates[0].targetArray == 0;
            writeDirect(d.currentResult, 0, indexUpdates[0].indices, currentIndex);
        }
        if (indexClears.length == 1) {
            assert indexClears[0].targetArray == 0;
            writeDirect(d.currentResult, 0, indexClears[0].indices, -1);
        }
        if (executor.getProperties().tracksLastGroup()) {
            if (lastGroupUpdates.length == 1) {
                assert lastGroupUpdates[0].targetArray == 0;
                d.currentResult[d.currentResult.length - 1] = lastGroupUpdates[0].getLastGroup();
            }
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
    private static void applyIndexOps(IndexOperation[] indexOps, int[] results, int[] currentResultOrder, int currentIndex) {
        for (IndexOperation op : indexOps) {
            writeDirect(results, currentResultOrder[op.getTargetArray()], op.indices, currentIndex);
        }
    }

    @ExplodeLoop
    private static void writeDirect(int[] array, int offset, byte[] indices, int value) {
        for (int i = 0; i < indices.length; i++) {
            array[offset + Byte.toUnsignedInt(indices[i])] = value;
        }
    }

    @ExplodeLoop
    private void applyLastGroupUpdate(int[] results, int[] currentResultOrder, int length) {
        for (LastGroupUpdate lastGroupUpdate : lastGroupUpdates) {
            final int targetArray = lastGroupUpdate.getTargetArray();
            results[currentResultOrder[targetArray] + length - 1] = lastGroupUpdate.getLastGroup();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DFACaptureGroupPartialTransition)) {
            return false;
        }
        DFACaptureGroupPartialTransition o = (DFACaptureGroupPartialTransition) obj;
        return Arrays.equals(reorderSwaps, o.reorderSwaps) &&
                        Arrays.equals(arrayCopies, o.arrayCopies) &&
                        Arrays.equals(indexUpdates, o.indexUpdates) &&
                        Arrays.equals(indexClears, o.indexClears) &&
                        Arrays.equals(lastGroupUpdates, o.lastGroupUpdates) &&
                        preReorderFinalStateResultIndex == o.preReorderFinalStateResultIndex;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = Arrays.hashCode(reorderSwaps);
        result = prime * result + Arrays.hashCode(arrayCopies);
        result = prime * result + Arrays.hashCode(indexUpdates);
        result = prime * result + Arrays.hashCode(indexClears);
        result = prime * result + Arrays.hashCode(lastGroupUpdates);
        result = prime * result + preReorderFinalStateResultIndex;
        return result;
    }

    @TruffleBoundary
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

    @TruffleBoundary
    private static void indexManipulationsToString(StringBuilder sb, IndexOperation[] indexManipulations, String name) {
        if (indexManipulations.length > 0) {
            sb.append(System.lineSeparator()).append(name).append(": ");
            for (IndexOperation indexManipulation : indexManipulations) {
                sb.append(System.lineSeparator()).append("    ").append(indexManipulation);
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
        for (IndexOperation indexUpdate : indexUpdates) {
            json.append(Json.prop("indexUpdates", indexUpdate));
        }
        for (IndexOperation indexClear : indexClears) {
            json.append(Json.prop("indexClears", indexClear));
        }
        return json;
    }

    public static final class IndexOperation implements JsonConvertible {

        private final byte targetArray;
        @CompilationFinal(dimensions = 1) private final byte[] indices;

        public IndexOperation(int targetArray, byte[] indices) {
            assert targetArray < 256;
            this.targetArray = (byte) targetArray;
            this.indices = indices;
        }

        public int getTargetArray() {
            return Byte.toUnsignedInt(targetArray);
        }

        public int getNumberOfIndices() {
            return indices.length;
        }

        public int getIndex(int i) {
            return Byte.toUnsignedInt(indices[i]);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            IndexOperation that = (IndexOperation) o;
            return targetArray == that.targetArray && Arrays.equals(indices, that.indices);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(targetArray);
            result = 31 * result + Arrays.hashCode(indices);
            return result;
        }

        @TruffleBoundary
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getTargetArray()).append(" <- [");
            for (int i = 0; i < getNumberOfIndices(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(getIndex(i));
            }
            sb.append("]");
            return sb.toString();
        }

        @TruffleBoundary
        @Override
        public JsonValue toJson() {
            return Json.obj(Json.prop("target", getTargetArray()),
                            Json.prop("groupStarts", groupEntriesToJsonArray(indices)),
                            Json.prop("groupEnds", groupExitsToJsonArray(indices)));
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
            for (int i = 0; i < gbArray.length; i++) {
                int intValue = Byte.toUnsignedInt(gbArray[i]);
                if ((intValue & 1) == (entries ? 0 : 1)) {
                    array.append(Json.val(intValue / 2));
                }
            }
            return array;
        }

        @TruffleBoundary
        public static JsonValue groupBoundariesToJsonObject(byte[] arr) {
            return Json.obj(Json.prop("groupStarts", groupEntriesToJsonArray(arr)),
                            Json.prop("groupEnds", groupExitsToJsonArray(arr)));
        }
    }

    public static final class LastGroupUpdate implements JsonConvertible {

        private final byte targetArray;
        private final byte lastGroup;

        public LastGroupUpdate(int targetArray, int lastGroup) {
            assert targetArray < 256;
            assert lastGroup < Byte.MAX_VALUE;
            assert lastGroup > 0;
            this.targetArray = (byte) targetArray;
            this.lastGroup = (byte) lastGroup;
        }

        public int getTargetArray() {
            return Byte.toUnsignedInt(targetArray);
        }

        public int getLastGroup() {
            return lastGroup;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            return o instanceof LastGroupUpdate && targetArray == ((LastGroupUpdate) o).targetArray && lastGroup == ((LastGroupUpdate) o).lastGroup;
        }

        @Override
        public int hashCode() {
            return (Byte.toUnsignedInt(targetArray) << 8) | Byte.toUnsignedInt(lastGroup);
        }

        @TruffleBoundary
        @Override
        public JsonValue toJson() {
            return Json.obj(Json.prop("target", getTargetArray()),
                            Json.prop("lastGroup", getLastGroup()));
        }
    }
}
