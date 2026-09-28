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

import java.util.Arrays;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.buffer.ByteArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * Static operations on immutable, canonical capture-group partial-transition records. In the
 * executor-owned array, records are concatenated without alignment or padding and addressed by
 * their absolute byte offset. Offset {@code 0} contains the canonical empty record.
 * <p>
 * A record starts with a six-byte header:
 *
 * <pre>
 * header (6 bytes)
 *
 * +-------------+------------------------------------------------+
 * | byte offset | field                                          |
 * +-------------+------------------------------------------------+
 * | 0           | pre-reorder final-state result index           |
 * | 1           | reorder-swap section byte length               |
 * | 2           | array-copy section byte length                 |
 * | 3           | number of index-update operations              |
 * | 4           | number of index-clear operations               |
 * | 5           | number of last-group-update operations         |
 * +-------------+------------------------------------------------+
 * </pre>
 *
 * Let {@code reorderSwapBytes}, {@code arrayCopyBytes}, {@code indexUpdateBytes}, and
 * {@code indexClearBytes} be the byte lengths of the corresponding payload sections. Let
 * {@code lastGroupUpdateCount} be the number of last-group-update operations. The complete record
 * is laid out as follows:
 *
 * <pre>
 * recordSize = 6 + reorderSwapBytes + arrayCopyBytes + indexUpdateBytes
 *                + indexClearBytes + 2 * lastGroupUpdateCount
 *
 * +--------+--------------------+------------------+-------------------+------------------+--------------------+
 * | header | reorder-swap bytes | array-copy bytes | index-update ops  | index-clear ops  | last-group updates |
 * +--------+--------------------+------------------+-------------------+------------------+--------------------+
 *
 * section start offsets
 *
 * header:              0
 * reorder swaps:       6
 * array copies:        6 + reorderSwapBytes
 * index updates:       6 + reorderSwapBytes + arrayCopyBytes
 * index clears:        6 + reorderSwapBytes + arrayCopyBytes + indexUpdateBytes
 * last-group updates:  6 + reorderSwapBytes + arrayCopyBytes + indexUpdateBytes + indexClearBytes
 * </pre>
 *
 * Every header field and operation value occupies one byte. Reorder swaps and array copies are
 * pairs:
 *
 * <pre>
 * reorder swap                 array copy
 * +------------+------------+  +------------+------------+
 * | first row  | second row |  | source row | target row |
 * +------------+------------+  +------------+------------+
 * </pre>
 *
 * Each index-update or index-clear operation carries its own payload length, so its section must
 * be parsed sequentially:
 *
 * <pre>
 * index update or clear operation (2 + indexCount bytes)
 * +------------+------------+----------------------------+
 * | target row | indexCount | indexCount result indices  |
 * +------------+------------+----------------------------+
 *
 * last-group update (2 bytes)
 * +------------+------------+
 * | target row | last group |
 * +------------+------------+
 * </pre>
 *
 * Operations occur in the order shown: reorder swaps, array copies, index updates, index clears,
 * and last-group updates. The header stores byte lengths for the two fixed-pair sections but
 * operation counts for the three remaining sections.
 */
public final class DFACaptureGroupPartialTransition {

    public static final int NO_TRANSITION = -1;
    public static final int FINAL_STATE_RESULT_INDEX = 0;

    private static final int FIELD_PRE_REORDER_FINAL_STATE_RESULT_INDEX = 0;
    private static final int FIELD_REORDER_SWAPS_LENGTH = 1;
    private static final int FIELD_ARRAY_COPIES_LENGTH = 2;
    private static final int FIELD_INDEX_UPDATES_LENGTH = 3;
    private static final int FIELD_INDEX_CLEARS_LENGTH = 4;
    private static final int FIELD_LAST_GROUP_UPDATES_LENGTH = 5;
    private static final int RECORD_HEADER_SIZE = 6;

    private static final byte[] EMPTY_RECORD = new byte[RECORD_HEADER_SIZE];

    private DFACaptureGroupPartialTransition() {
    }

    /**
     * Creates a packed partial-transition record. All numeric values encoded by this class refer to
     * indices of the first or second dimension of
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
     *            {@link #apply(byte[], int, TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int)},
     *            {@link #applyPreFinalStateTransition(byte[], int, TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int, boolean)}
     *            and
     *            {@link #applyFinalStateTransition(byte[], int, TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int)}.
     *            Each operation is encoded as the target row, the number of indices, and the
     *            indices themselves.
     *            <p>
     *            Example: <br>
     *            If {@code results = DFACaptureGroupTrackingData#results} and
     *            {@code indexUpdates = [0, 2, 1, 2, 3, 1, 4]}, then {@code results[0][1]},
     *            {@code results[0][2]} and {@code results[3][4]} will be set to
     *            {@code currentIndex}.
     *            </p>
     * @param numberOfIndexUpdates number of operations encoded in {@code indexUpdates}.
     * @param indexClears denotes which index of which array in
     *            {@link DFACaptureGroupTrackingData#results} shall be updated to {@code 0} in
     *            {@link #apply(byte[], int, TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int)},
     *            {@link #applyPreFinalStateTransition(byte[], int, TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int, boolean)}
     *            and
     *            {@link #applyFinalStateTransition(byte[], int, TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int)},
     *            analogous to {@code indexUpdates}.
     * @param numberOfIndexClears number of operations encoded in {@code indexClears}.
     * @param lastGroupUpdates updates of the last matched capture group, encoded as pairs of target
     *            row and capture group number.
     * @param preReorderFinalStateResultIndex denotes the row (1st dimension element) of
     *            {@link DFACaptureGroupTrackingData#results} that corresponds to the NFA final
     *            state <em>before</em> the reordering given by {@code reorderSwaps} is applied.
     *            This is needed in
     *            {@link #applyPreFinalStateTransition(byte[], int, TRegexDFAExecutorNode, DFACaptureGroupTrackingData, int, boolean)}
     *            when {@link TRegexDFAExecutorNode#isSearching()} is {@code true}, because in that
     *            case we need to be able to apply copy the current result corresponding to the NFA
     *            final state without doing any reordering.
     * @return a packed transition record, or the shared empty record if all arguments are empty or
     *         zero.
     */
    public static byte[] create(
                    ByteArrayBuffer reorderSwaps,
                    ByteArrayBuffer arrayCopies,
                    ByteArrayBuffer indexUpdates,
                    int numberOfIndexUpdates,
                    ByteArrayBuffer indexClears,
                    int numberOfIndexClears,
                    ByteArrayBuffer lastGroupUpdates,
                    byte preReorderFinalStateResultIndex) {
        return createInternal(reorderSwaps, arrayCopies, indexUpdates, numberOfIndexUpdates, indexClears, numberOfIndexClears, lastGroupUpdates, preReorderFinalStateResultIndex);
    }

    private static byte[] createInternal(
                    ByteArrayBuffer reorderSwaps,
                    ByteArrayBuffer arrayCopies,
                    ByteArrayBuffer indexUpdates,
                    int numberOfIndexUpdates,
                    ByteArrayBuffer indexClears,
                    int numberOfIndexClears,
                    ByteArrayBuffer lastGroupUpdates,
                    byte preReorderFinalStateResultIndex) {
        assert (reorderSwaps.length() & 1) == 0 : "reorderSwaps must have an even number of elements";
        assert (arrayCopies.length() & 1) == 0 : "arrayCopies must have an even number of elements";
        assert (lastGroupUpdates.length() & 1) == 0 : "lastGroupUpdates must have an even number of elements";
        assert (numberOfIndexUpdates == 0) == indexUpdates.isEmpty();
        assert (numberOfIndexClears == 0) == indexClears.isEmpty();
        assert numberOfIndexUpdates <= indexUpdates.length() / 2;
        assert numberOfIndexClears <= indexClears.length() / 2;
        int numberOfLastGroupUpdates = lastGroupUpdates.length() / 2;
        if (isEmptyTransition(reorderSwaps.length(), arrayCopies.length(), numberOfIndexUpdates, numberOfIndexClears, numberOfLastGroupUpdates, preReorderFinalStateResultIndex)) {
            return getEmptyRecord();
        }
        byte[] partialTransitionRecord = createRecord(preReorderFinalStateResultIndex, reorderSwaps.length(), arrayCopies.length(), numberOfIndexUpdates, numberOfIndexClears, numberOfLastGroupUpdates,
                        indexUpdates.length() + indexClears.length() + lastGroupUpdates.length());
        int offset = RECORD_HEADER_SIZE;
        offset = reorderSwaps.copyTo(partialTransitionRecord, offset);
        offset = arrayCopies.copyTo(partialTransitionRecord, offset);
        offset = indexUpdates.copyTo(partialTransitionRecord, offset);
        offset = indexClears.copyTo(partialTransitionRecord, offset);
        offset = lastGroupUpdates.copyTo(partialTransitionRecord, offset);
        assert offset == partialTransitionRecord.length;
        return partialTransitionRecord;
    }

    private static byte[] createRecord(byte preReorderFinalStateResultIndex, int reorderSwapsLength, int arrayCopiesLength, int numberOfIndexUpdates, int numberOfIndexClears,
                    int numberOfLastGroupUpdates, int operationsByteLength) {
        if (reorderSwapsLength > 0xff || arrayCopiesLength > 0xff || numberOfIndexUpdates > 0xff || numberOfIndexClears > 0xff || numberOfLastGroupUpdates > 0xff) {
            throw new UnsupportedRegexException("capture group partial transition is too large");
        }
        byte[] partialTransitionRecord = new byte[RECORD_HEADER_SIZE + reorderSwapsLength + arrayCopiesLength + operationsByteLength];
        partialTransitionRecord[FIELD_PRE_REORDER_FINAL_STATE_RESULT_INDEX] = preReorderFinalStateResultIndex;
        partialTransitionRecord[FIELD_REORDER_SWAPS_LENGTH] = (byte) reorderSwapsLength;
        partialTransitionRecord[FIELD_ARRAY_COPIES_LENGTH] = (byte) arrayCopiesLength;
        partialTransitionRecord[FIELD_INDEX_UPDATES_LENGTH] = (byte) numberOfIndexUpdates;
        partialTransitionRecord[FIELD_INDEX_CLEARS_LENGTH] = (byte) numberOfIndexClears;
        partialTransitionRecord[FIELD_LAST_GROUP_UPDATES_LENGTH] = (byte) numberOfLastGroupUpdates;
        return partialTransitionRecord;
    }

    private static boolean isEmptyTransition(int reorderSwapsLength, int arrayCopiesLength, int numberOfIndexUpdates, int numberOfIndexClears, int numberOfLastGroupUpdates,
                    byte preReorderFinalStateResultIndex) {
        return reorderSwapsLength == 0 &&
                        arrayCopiesLength == 0 &&
                        numberOfIndexUpdates == 0 &&
                        numberOfIndexClears == 0 &&
                        numberOfLastGroupUpdates == 0 &&
                        preReorderFinalStateResultIndex == 0;
    }

    private enum OperationKind {
        indexUpdates,
        indexClears,
        lastGroupUpdates
    }

    /**
     * Extracts a packed record containing all operations common to all partial transitions in the given
     * array.
     */
    public static byte[] intersect(byte[][] partialTransitionRecords, CompilationBuffer compilationBuffer) {
        byte[] firstRecord = partialTransitionRecords[0];
        if (!haveSameReordering(partialTransitionRecords)) {
            // can't extract common operations from partial-transition records that re-arrange the target
            // arrays in different ways
            return getEmptyRecord();
        }
        ByteArrayBuffer commonOperations = compilationBuffer.getByteArrayBuffer();
        int numberOfIndexUpdates = appendCommonOperations(partialTransitionRecords, OperationKind.indexUpdates, commonOperations);
        int numberOfIndexClears = appendCommonOperations(partialTransitionRecords, OperationKind.indexClears, commonOperations);
        int numberOfLastGroupUpdates = appendCommonOperations(partialTransitionRecords, OperationKind.lastGroupUpdates, commonOperations);
        int reorderSwapsLength = getReorderSwapsLength(firstRecord);
        int arrayCopiesLength = getArrayCopiesLength(firstRecord);
        if (isEmptyTransition(reorderSwapsLength, arrayCopiesLength, numberOfIndexUpdates, numberOfIndexClears, numberOfLastGroupUpdates, getPreReorderFinalStateResultIndex(firstRecord))) {
            return getEmptyRecord();
        }
        byte[] commonRecord = createRecord(getPreReorderFinalStateResultIndex(firstRecord),
                        reorderSwapsLength,
                        arrayCopiesLength,
                        numberOfIndexUpdates,
                        numberOfIndexClears,
                        numberOfLastGroupUpdates,
                        commonOperations.length());
        int offset = copyRecordTo(firstRecord, getReorderSwapsOffset(), commonRecord, getReorderSwapsOffset(), reorderSwapsLength + arrayCopiesLength);
        offset = commonOperations.copyTo(commonRecord, offset);
        assert offset == commonRecord.length;
        return commonRecord;
    }

    private static boolean haveSameReordering(byte[][] partialTransitionRecords) {
        byte[] firstRecord = partialTransitionRecords[0];
        int length = getReorderSwapsLength(firstRecord) + getArrayCopiesLength(firstRecord);
        for (int i = 1; i < partialTransitionRecords.length; i++) {
            byte[] currentRecord = partialTransitionRecords[i];
            if (getPreReorderFinalStateResultIndex(firstRecord) != getPreReorderFinalStateResultIndex(currentRecord) ||
                            getReorderSwapsLength(firstRecord) != getReorderSwapsLength(currentRecord) ||
                            getArrayCopiesLength(firstRecord) != getArrayCopiesLength(currentRecord) ||
                            !recordRangeEquals(firstRecord, getReorderSwapsOffset(), currentRecord, getReorderSwapsOffset(), length)) {
                return false;
            }
        }
        return true;
    }

    private static int appendCommonOperations(byte[][] partialTransitionRecords, OperationKind operationKind, ByteArrayBuffer commonOperations) {
        byte[] firstRecord = partialTransitionRecords[0];
        int offset = getOperationsOffset(firstRecord, operationKind);
        int end = getOperationsEnd(firstRecord, operationKind);
        int count = 0;
        while (offset < end) {
            int operationLength = operationLength(firstRecord, offset, operationKind);
            if (allContain(partialTransitionRecords, firstRecord, offset, operationLength, operationKind)) {
                commonOperations.addAll(firstRecord, offset, operationLength);
                count++;
            }
            offset += operationLength;
        }
        return count;
    }

    private static boolean allContain(byte[][] partialTransitionRecords, byte[] operation, int operationOffset, int operationLength, OperationKind operationKind) {
        for (int i = 1; i < partialTransitionRecords.length; i++) {
            if (!contains(partialTransitionRecords[i], operationKind, operation, operationOffset, operationLength)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes all operations present in {@code recordB} from {@code recordA}. Reorder and
     * array-copy sections in {@code recordB} may be empty, in which case the corresponding section
     * in {@code recordA} is retained. Non-empty sections must be identical to those in
     * {@code recordA} and are removed.
     */
    public static byte[] subtract(byte[] recordA, byte[] recordB, CompilationBuffer compilationBuffer) {
        int reorderSwapsLength = getReorderSwapsLength(recordB) == 0 ? getReorderSwapsLength(recordA) : 0;
        int arrayCopiesLength = getArrayCopiesLength(recordB) == 0 ? getArrayCopiesLength(recordA) : 0;
        assert getReorderSwapsLength(recordB) == 0 || getReorderSwapsLength(recordA) == getReorderSwapsLength(recordB) &&
                        recordRangeEquals(recordA, getReorderSwapsOffset(), recordB, getReorderSwapsOffset(), getReorderSwapsLength(recordA));
        assert getArrayCopiesLength(recordB) == 0 || getArrayCopiesLength(recordA) == getArrayCopiesLength(recordB) &&
                        recordRangeEquals(recordA, getArrayCopiesOffset(recordA), recordB, getArrayCopiesOffset(recordB), getArrayCopiesLength(recordA));
        ByteArrayBuffer subtractedOperations = compilationBuffer.getByteArrayBuffer();
        int numberOfIndexUpdates = appendSubtractedOperations(recordA, recordB, OperationKind.indexUpdates, subtractedOperations);
        int numberOfIndexClears = appendSubtractedOperations(recordA, recordB, OperationKind.indexClears, subtractedOperations);
        int numberOfLastGroupUpdates = appendSubtractedOperations(recordA, recordB, OperationKind.lastGroupUpdates, subtractedOperations);
        if (reorderSwapsLength == getReorderSwapsLength(recordA) &&
                        arrayCopiesLength == getArrayCopiesLength(recordA) &&
                        numberOfIndexUpdates == getOperationCount(recordA, OperationKind.indexUpdates) &&
                        numberOfIndexClears == getOperationCount(recordA, OperationKind.indexClears) &&
                        numberOfLastGroupUpdates == getOperationCount(recordA, OperationKind.lastGroupUpdates)) {
            return recordA;
        }
        if (isEmptyTransition(reorderSwapsLength, arrayCopiesLength, numberOfIndexUpdates, numberOfIndexClears, numberOfLastGroupUpdates, getPreReorderFinalStateResultIndex(
                        recordA))) {
            return getEmptyRecord();
        }
        byte[] subtractedRecord = createRecord(getPreReorderFinalStateResultIndex(recordA),
                        reorderSwapsLength,
                        arrayCopiesLength,
                        numberOfIndexUpdates,
                        numberOfIndexClears,
                        numberOfLastGroupUpdates,
                        subtractedOperations.length());
        int offset = getReorderSwapsOffset();
        if (reorderSwapsLength + arrayCopiesLength != 0) {
            offset = copyRecordTo(recordA, getReorderSwapsOffset(), subtractedRecord, getReorderSwapsOffset(), reorderSwapsLength + arrayCopiesLength);
        }
        offset = subtractedOperations.copyTo(subtractedRecord, offset);
        assert offset == subtractedRecord.length;
        return subtractedRecord;
    }

    private static int appendSubtractedOperations(byte[] recordA, byte[] recordB, OperationKind operationKind, ByteArrayBuffer subtractedOperations) {
        int offset = getOperationsOffset(recordA, operationKind);
        int end = getOperationsEnd(recordA, operationKind);
        int count = 0;
        while (offset < end) {
            int operationLength = operationLength(recordA, offset, operationKind);
            if (!contains(recordB, operationKind, recordA, offset, operationLength)) {
                subtractedOperations.addAll(recordA, offset, operationLength);
                count++;
            }
            offset += operationLength;
        }
        return count;
    }

    private static boolean contains(byte[] partialTransitionRecord, OperationKind operationKind, byte[] operation, int operationOffset, int operationLength) {
        int offset = getOperationsOffset(partialTransitionRecord, operationKind);
        int end = getOperationsEnd(partialTransitionRecord, operationKind);
        while (offset < end) {
            int currentOperationLength = operationLength(partialTransitionRecord, offset, operationKind);
            if (currentOperationLength == operationLength && recordRangeEquals(partialTransitionRecord, offset, operation, operationOffset, operationLength)) {
                return true;
            }
            offset += currentOperationLength;
        }
        return false;
    }

    private static int operationLength(byte[] partialTransitionRecord, int offset, OperationKind operationKind) {
        return operationKind == OperationKind.lastGroupUpdates ? 2 : 2 + Byte.toUnsignedInt(partialTransitionRecord[offset + 1]);
    }

    private static int getOperationsOffset(byte[] partialTransitionRecord, OperationKind operationKind) {
        return switch (operationKind) {
            case indexUpdates -> getIndexUpdatesOffset(partialTransitionRecord);
            case indexClears -> getIndexClearsOffset(partialTransitionRecord);
            case lastGroupUpdates -> getLastGroupUpdatesOffset(partialTransitionRecord);
        };
    }

    private static int getOperationsEnd(byte[] partialTransitionRecord, OperationKind operationKind) {
        return switch (operationKind) {
            case indexUpdates -> getIndexClearsOffset(partialTransitionRecord);
            case indexClears -> getLastGroupUpdatesOffset(partialTransitionRecord);
            case lastGroupUpdates -> partialTransitionRecord.length;
        };
    }

    private static int copyRecordTo(byte[] sourceRecord, int sourceOffset, byte[] targetRecord, int targetOffset, int length) {
        System.arraycopy(sourceRecord, sourceOffset, targetRecord, targetOffset, length);
        return targetOffset + length;
    }

    private static boolean recordRangeEquals(byte[] firstRecord, int firstOffset, byte[] secondRecord, int secondOffset, int length) {
        return Arrays.equals(firstRecord, firstOffset, firstOffset + length, secondRecord, secondOffset, secondOffset + length);
    }

    public static byte[] getEmptyRecord() {
        return EMPTY_RECORD;
    }

    public static boolean isEmpty(byte[] partialTransitionRecord) {
        return partialTransitionRecord == EMPTY_RECORD;
    }

    private static boolean doesReorderResults(byte[] partialTransitionRecord) {
        return doesReorderResults(partialTransitionRecord, 0);
    }

    public static boolean doesReorderResults(byte[] partialTransitionRecords, int partialTransitionRef) {
        return getReorderSwapsLength(partialTransitionRecords, partialTransitionRef) > 0;
    }

    /**
     * Checks if the capture group updates in this packed looping transition depend on their previous
     * application in the loop. This happens when the transition contains a copy operation where
     * the source array isn't updated at the same indices as the target array, for example:
     *
     * <pre>
     * {@code copy array 0 -> 1}
     * {@code update array 0, indices [0, 1]}
     * {@code update array 1, indices [1]}
     * </pre>
     * <p>
     * In this case, array 1 at index 0 will always contain the value of array 0 from the previous
     * loop iteration. {@link CGTrackingDFAStateNode} takes this into account in its
     * {@code afterIndexOf} method.
     */
    public static boolean hasLoopToSelfDependency(byte[] partialTransitionRecord) {
        if (doesReorderResults(partialTransitionRecord)) {
            // Reordered transitions are applied for every loop iteration, so this flag is unused.
            return false;
        }
        int arrayCopiesOffset = getArrayCopiesOffset(partialTransitionRecord);
        int arrayCopiesEnd = arrayCopiesOffset + getArrayCopiesLength(partialTransitionRecord);
        for (int offset = arrayCopiesOffset; offset < arrayCopiesEnd; offset += 2) {
            int arraycopySource = Byte.toUnsignedInt(partialTransitionRecord[offset]);
            int arraycopyTarget = Byte.toUnsignedInt(partialTransitionRecord[offset + 1]);
            // find update operations targeting the arraycopy source array
            int sourceOperation = findIndexUpdateOperation(partialTransitionRecord, arraycopySource);
            if (sourceOperation < 0) {
                // no updates, so no dependency
                continue;
            }
            // find update operations targeting the arraycopy target array
            int targetOperation = findIndexUpdateOperation(partialTransitionRecord, arraycopyTarget);
            if (targetOperation < 0) {
                // no updates in target, but at least one in source => dependency found
                return true;
            }
            // for all updates in the source array, check if there's an equivalent update in the target array.
            int sourceEnd = sourceOperation + operationLength(partialTransitionRecord, sourceOperation, OperationKind.indexUpdates);
            for (int i = sourceOperation + 2; i < sourceEnd; i++) {
                if (!indexOperationContains(partialTransitionRecord, targetOperation, partialTransitionRecord[i])) {
                    // found an update in source with no equivalent update in target => dependency found
                    return true;
                }
            }
        }
        return false;
    }

    private static int findIndexUpdateOperation(byte[] partialTransitionRecord, int targetArray) {
        int offset = getIndexUpdatesOffset(partialTransitionRecord, 0);
        int end = getIndexClearsOffset(partialTransitionRecord);
        while (offset < end) {
            if (Byte.toUnsignedInt(partialTransitionRecord[offset]) == targetArray) {
                return offset;
            }
            offset += operationLength(partialTransitionRecord, offset, OperationKind.indexUpdates);
        }
        return -1;
    }

    private static boolean indexOperationContains(byte[] partialTransitionRecord, int operationOffset, byte index) {
        int end = operationOffset + operationLength(partialTransitionRecord, operationOffset, OperationKind.indexUpdates);
        for (int i = operationOffset + 2; i < end; i++) {
            if (partialTransitionRecord[i] == index) {
                return true;
            }
        }
        return false;
    }

    private static byte getPreReorderFinalStateResultIndex(byte[] partialTransitionRecord) {
        return getPreReorderFinalStateResultIndex(partialTransitionRecord, 0);
    }

    private static byte getPreReorderFinalStateResultIndex(byte[] partialTransitionRecords, int partialTransitionRef) {
        return partialTransitionRecords[partialTransitionRef + FIELD_PRE_REORDER_FINAL_STATE_RESULT_INDEX];
    }

    private static int getReorderSwapsLength(byte[] partialTransitionRecord) {
        return getReorderSwapsLength(partialTransitionRecord, 0);
    }

    private static int getReorderSwapsLength(byte[] partialTransitionRecords, int partialTransitionRef) {
        return Byte.toUnsignedInt(partialTransitionRecords[partialTransitionRef + FIELD_REORDER_SWAPS_LENGTH]);
    }

    private static int getArrayCopiesLength(byte[] partialTransitionRecord) {
        return getArrayCopiesLength(partialTransitionRecord, 0);
    }

    private static int getArrayCopiesLength(byte[] partialTransitionRecords, int partialTransitionRef) {
        return Byte.toUnsignedInt(partialTransitionRecords[partialTransitionRef + FIELD_ARRAY_COPIES_LENGTH]);
    }

    private static int getOperationCount(byte[] partialTransitionRecord, OperationKind operationKind) {
        return getOperationCount(partialTransitionRecord, 0, operationKind);
    }

    private static int getOperationCount(byte[] partialTransitionRecords, int partialTransitionRef, OperationKind operationKind) {
        return Byte.toUnsignedInt(partialTransitionRecords[partialTransitionRef + FIELD_INDEX_UPDATES_LENGTH + operationKind.ordinal()]);
    }

    private static int getReorderSwapsOffset() {
        return RECORD_HEADER_SIZE;
    }

    private static int getReorderSwapsOffset(int partialTransitionRef) {
        return partialTransitionRef + RECORD_HEADER_SIZE;
    }

    private static int getArrayCopiesOffset(byte[] partialTransitionRecord) {
        return getArrayCopiesOffset(partialTransitionRecord, 0);
    }

    private static int getArrayCopiesOffset(byte[] partialTransitionRecords, int partialTransitionRef) {
        return getReorderSwapsOffset(partialTransitionRef) + getReorderSwapsLength(partialTransitionRecords, partialTransitionRef);
    }

    private static int getIndexUpdatesOffset(byte[] partialTransitionRecord) {
        return getIndexUpdatesOffset(partialTransitionRecord, 0);
    }

    private static int getIndexUpdatesOffset(byte[] partialTransitionRecords, int partialTransitionRef) {
        return getArrayCopiesOffset(partialTransitionRecords, partialTransitionRef) + getArrayCopiesLength(partialTransitionRecords, partialTransitionRef);
    }

    private static int getIndexClearsOffset(byte[] partialTransitionRecord) {
        return skipIndexOperations(partialTransitionRecord, getIndexUpdatesOffset(partialTransitionRecord, 0), getOperationCount(partialTransitionRecord, 0,
                        OperationKind.indexUpdates));
    }

    private static int getLastGroupUpdatesOffset(byte[] partialTransitionRecord) {
        return skipIndexOperations(partialTransitionRecord, getIndexClearsOffset(partialTransitionRecord), getOperationCount(partialTransitionRecord, 0,
                        OperationKind.indexClears));
    }

    private static int skipIndexOperations(byte[] partialTransitionRecords, int offset, int length) {
        int currentOffset = offset;
        for (int i = 0; i < length; i++) {
            currentOffset += 2 + Byte.toUnsignedInt(partialTransitionRecords[currentOffset + 1]);
        }
        return currentOffset;
    }

    public static int getCost(byte[] partialTransitionRecords, int partialTransitionRef) {
        int cost = getReorderSwapsLength(partialTransitionRecords, partialTransitionRef) + getArrayCopiesLength(partialTransitionRecords, partialTransitionRef) + getOperationCount(
                        partialTransitionRecords, partialTransitionRef, OperationKind.lastGroupUpdates);
        int offset = getIndexUpdatesOffset(partialTransitionRecords, partialTransitionRef);
        int numberOfIndexOperations = getOperationCount(partialTransitionRecords, partialTransitionRef, OperationKind.indexUpdates) + getOperationCount(partialTransitionRecords, partialTransitionRef,
                        OperationKind.indexClears);
        for (int i = 0; i < numberOfIndexOperations; i++) {
            int numberOfIndices = Byte.toUnsignedInt(partialTransitionRecords[offset + 1]);
            cost += numberOfIndices;
            offset += 2 + numberOfIndices;
        }
        return cost;
    }

    public static void apply(byte[] partialTransitionRecords, int partialTransitionRef, TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, final int currentIndex) {
        apply(partialTransitionRecords, partialTransitionRef, executor, d, currentIndex, false, false);
    }

    public static void apply(byte[] partialTransitionRecords, int partialTransitionRef, TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, final int currentIndex, boolean preFinal,
                    boolean export) {
        if (preFinal) {
            applyPreFinalStateTransition(partialTransitionRecords, partialTransitionRef, executor, d, currentIndex, export);
        } else {
            applyRegular(partialTransitionRecords, partialTransitionRef, executor, d, currentIndex);
        }
    }

    private static void applyRegular(byte[] partialTransitionRecords, int partialTransitionRef, TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, final int currentIndex) {
        if (executor.recordExecution()) {
            executor.getDebugRecorder().recordCGPartialTransition(currentIndex, partialTransitionRef);
        }
        CompilerAsserts.partialEvaluationConstant(partialTransitionRecords);
        CompilerAsserts.partialEvaluationConstant(partialTransitionRef);
        CompilerAsserts.partialEvaluationConstant(executor);
        if (executor.getMaxNumberOfNFAStates() == 1) {
            assert d.currentResultOrder == null;
            assert getReorderSwapsLength(partialTransitionRecords, partialTransitionRef) == 0;
            assert getArrayCopiesLength(partialTransitionRecords, partialTransitionRef) == 0;
            applySingleRowOperations(partialTransitionRecords, partialTransitionRef, executor, d.results, d.currentResult.length, currentIndex);
        } else {
            int indexUpdatesLength = getOperationCount(partialTransitionRecords, partialTransitionRef, OperationKind.indexUpdates);
            int indexClearsLength = getOperationCount(partialTransitionRecords, partialTransitionRef, OperationKind.indexClears);
            int lastGroupUpdatesLength = getOperationCount(partialTransitionRecords, partialTransitionRef, OperationKind.lastGroupUpdates);
            applyReorder(partialTransitionRecords, partialTransitionRef, d.currentResultOrder);
            applyArrayCopy(partialTransitionRecords, partialTransitionRef, d);
            int offset = applyIndexOps(partialTransitionRecords, getIndexUpdatesOffset(partialTransitionRecords, partialTransitionRef), indexUpdatesLength, d.results, d.currentResultOrder,
                            d.currentResult.length, currentIndex);
            offset = applyIndexOps(partialTransitionRecords, offset, indexClearsLength, d.results, d.currentResultOrder, d.currentResult.length, -1);
            if (executor.tracksLastGroup()) {
                applyLastGroupUpdate(partialTransitionRecords, offset, lastGroupUpdatesLength, d.results, d.currentResultOrder, d.currentResult.length);
            }
        }
    }

    private static void applyPreFinalStateTransition(byte[] partialTransitionRecords, int partialTransitionRef, TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, final int currentIndex,
                    boolean export) {
        CompilerAsserts.partialEvaluationConstant(partialTransitionRecords);
        CompilerAsserts.partialEvaluationConstant(partialTransitionRef);
        CompilerAsserts.partialEvaluationConstant(executor);
        if (!executor.isSearching()) {
            apply(partialTransitionRecords, partialTransitionRef, executor, d, currentIndex);
            return;
        }
        if (executor.recordExecution()) {
            executor.getDebugRecorder().recordCGPartialTransition(currentIndex, partialTransitionRef);
        }
        if (export) {
            d.exportResult(executor, getPreReorderFinalStateResultIndex(partialTransitionRecords, partialTransitionRef));
        }
        applyFinalStateTransition(partialTransitionRecords, partialTransitionRef, executor, d, currentIndex);
    }

    public static void applyFinalStateTransition(byte[] partialTransitionRecords, int partialTransitionRef, TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(partialTransitionRecords);
        CompilerAsserts.partialEvaluationConstant(partialTransitionRef);
        CompilerAsserts.partialEvaluationConstant(executor);
        if (!executor.isSearching()) {
            apply(partialTransitionRecords, partialTransitionRef, executor, d, currentIndex);
            return;
        }
        if (executor.recordExecution()) {
            executor.getDebugRecorder().recordCGPartialTransition(currentIndex, partialTransitionRef);
        }
        assert getArrayCopiesLength(partialTransitionRecords, partialTransitionRef) == 0;
        applySingleRowOperations(partialTransitionRecords, partialTransitionRef, executor, d.currentResult, d.currentResult.length, currentIndex);
    }

    private static void applySingleRowOperations(byte[] partialTransitionRecords, int partialTransitionRef, TRegexDFAExecutorNode executor, int[] results, int rowLength, int currentIndex) {
        int indexUpdatesLength = getOperationCount(partialTransitionRecords, partialTransitionRef, OperationKind.indexUpdates);
        int indexClearsLength = getOperationCount(partialTransitionRecords, partialTransitionRef, OperationKind.indexClears);
        int lastGroupUpdatesLength = getOperationCount(partialTransitionRecords, partialTransitionRef, OperationKind.lastGroupUpdates);
        assert indexUpdatesLength <= 1;
        assert indexClearsLength <= 1;
        assert lastGroupUpdatesLength <= 1;
        int offset = getIndexUpdatesOffset(partialTransitionRecords, partialTransitionRef);
        if (indexUpdatesLength != 0) {
            assert Byte.toUnsignedInt(partialTransitionRecords[offset]) == 0;
            offset++;
            int numberOfIndices = Byte.toUnsignedInt(partialTransitionRecords[offset++]);
            writeDirect(partialTransitionRecords, offset, numberOfIndices, results, 0, rowLength, currentIndex);
            offset += numberOfIndices;
        }
        if (indexClearsLength != 0) {
            assert Byte.toUnsignedInt(partialTransitionRecords[offset]) == 0;
            offset++;
            int numberOfIndices = Byte.toUnsignedInt(partialTransitionRecords[offset++]);
            writeDirect(partialTransitionRecords, offset, numberOfIndices, results, 0, rowLength, -1);
            offset += numberOfIndices;
        }
        if (executor.tracksLastGroup() && lastGroupUpdatesLength == 1) {
            assert Byte.toUnsignedInt(partialTransitionRecords[offset]) == 0;
            DFACaptureGroupTrackingData.writeRowElement(results, 0, rowLength, rowLength - 1, Byte.toUnsignedInt(partialTransitionRecords[offset + 1]));
        }
    }

    @ExplodeLoop
    private static void applyReorder(byte[] partialTransitionRecords, int partialTransitionRef, int[] currentResultOrder) {
        int offset = getReorderSwapsOffset(partialTransitionRef);
        int length = getReorderSwapsLength(partialTransitionRecords, partialTransitionRef);
        for (int i = 0; i < length; i += 2) {
            final int source = Byte.toUnsignedInt(partialTransitionRecords[offset + i]);
            final int target = Byte.toUnsignedInt(partialTransitionRecords[offset + i + 1]);
            CompilerAsserts.partialEvaluationConstant(source);
            CompilerAsserts.partialEvaluationConstant(target);
            final int tmp = currentResultOrder[source];
            currentResultOrder[source] = currentResultOrder[target];
            currentResultOrder[target] = tmp;
        }
    }

    @ExplodeLoop
    private static void applyArrayCopy(byte[] partialTransitionRecords, int partialTransitionRef, DFACaptureGroupTrackingData d) {
        int rowLength = d.currentResult.length;
        int offset = getArrayCopiesOffset(partialTransitionRecords, partialTransitionRef);
        int length = getArrayCopiesLength(partialTransitionRecords, partialTransitionRef);
        for (int i = 0; i < length; i += 2) {
            final int source = Byte.toUnsignedInt(partialTransitionRecords[offset + i]);
            final int target = Byte.toUnsignedInt(partialTransitionRecords[offset + i + 1]);
            CompilerAsserts.partialEvaluationConstant(source);
            CompilerAsserts.partialEvaluationConstant(target);
            ArrayUtils.arraycopy(d.results,
                            DFACaptureGroupTrackingData.maskRowStart(d.results, d.currentResultOrder[source], rowLength), d.results,
                            DFACaptureGroupTrackingData.maskRowStart(d.results, d.currentResultOrder[target], rowLength), rowLength);
        }
    }

    @ExplodeLoop
    private static int applyIndexOps(byte[] partialTransitionRecords, int offset, int length, int[] results, int[] currentResultOrder, int rowLength, int currentIndex) {
        int currentOffset = offset;
        for (int i = 0; i < length; i++) {
            int targetArray = Byte.toUnsignedInt(partialTransitionRecords[currentOffset++]);
            int numberOfIndices = Byte.toUnsignedInt(partialTransitionRecords[currentOffset++]);
            writeDirect(partialTransitionRecords, currentOffset, numberOfIndices, results, currentResultOrder[targetArray], rowLength, currentIndex);
            currentOffset += numberOfIndices;
        }
        return currentOffset;
    }

    @ExplodeLoop
    private static void writeDirect(byte[] partialTransitionRecords, int indicesOffset, int numberOfIndices, int[] array, int offset, int rowLength, int value) {
        int maskedOffset = DFACaptureGroupTrackingData.maskRowStart(array, offset, rowLength);
        for (int i = 0; i < numberOfIndices; i++) {
            int index = Byte.toUnsignedInt(partialTransitionRecords[indicesOffset + i]);
            CompilerAsserts.partialEvaluationConstant(index);
            assert index < rowLength;
            array[maskedOffset + index] = value;
        }
    }

    @ExplodeLoop
    private static void applyLastGroupUpdate(byte[] partialTransitionRecords, int offset, int numberOfUpdates, int[] results, int[] currentResultOrder, int length) {
        int currentOffset = offset;
        for (int i = 0; i < numberOfUpdates; i++) {
            int targetArray = Byte.toUnsignedInt(partialTransitionRecords[currentOffset++]);
            int lastGroup = Byte.toUnsignedInt(partialTransitionRecords[currentOffset++]);
            CompilerAsserts.partialEvaluationConstant(targetArray);
            CompilerAsserts.partialEvaluationConstant(lastGroup);
            DFACaptureGroupTrackingData.writeRowElement(results, currentResultOrder[targetArray], length, length - 1, lastGroup);
        }
    }

    @TruffleBoundary
    public static String toString(byte[] partialTransitionRecord) {
        StringBuilder sb = new StringBuilder("DfaCGTransition");
        int reorderSwapsLength = getReorderSwapsLength(partialTransitionRecord);
        if (reorderSwapsLength > 0) {
            sb.append(System.lineSeparator()).append("reorderSwaps: [");
            int reorderSwapsOffset = getReorderSwapsOffset();
            for (int i = 0; i < reorderSwapsLength; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(Byte.toUnsignedInt(partialTransitionRecord[reorderSwapsOffset + i]));
            }
            sb.append("]");
        }
        int arrayCopiesOffset = getArrayCopiesOffset(partialTransitionRecord);
        int arrayCopiesLength = getArrayCopiesLength(partialTransitionRecord);
        if (arrayCopiesLength > 0) {
            sb.append(System.lineSeparator()).append("arrayCopies: ");
            for (int i = 0; i < arrayCopiesLength; i += 2) {
                final int source = Byte.toUnsignedInt(partialTransitionRecord[arrayCopiesOffset + i]);
                final int target = Byte.toUnsignedInt(partialTransitionRecord[arrayCopiesOffset + i + 1]);
                sb.append(System.lineSeparator()).append("    ").append(source).append(" -> ").append(target);
            }
        }
        indexManipulationsToString(partialTransitionRecord, sb, getIndexUpdatesOffset(partialTransitionRecord), getIndexClearsOffset(partialTransitionRecord), "indexUpdates");
        indexManipulationsToString(partialTransitionRecord, sb, getIndexClearsOffset(partialTransitionRecord), getLastGroupUpdatesOffset(partialTransitionRecord), "indexClears");
        int lastGroupUpdatesLength = getOperationCount(partialTransitionRecord, OperationKind.lastGroupUpdates);
        if (lastGroupUpdatesLength > 0) {
            sb.append(System.lineSeparator()).append("lastGroupUpdates: ");
            int offset = getLastGroupUpdatesOffset(partialTransitionRecord);
            for (int i = 0; i < lastGroupUpdatesLength; i++) {
                int targetArray = Byte.toUnsignedInt(partialTransitionRecord[offset++]);
                int lastGroup = Byte.toUnsignedInt(partialTransitionRecord[offset++]);
                sb.append(System.lineSeparator()).append("    ").append(targetArray).append(" <- ").append(lastGroup);
            }
        }
        return sb.toString();
    }

    @TruffleBoundary
    private static void indexManipulationsToString(byte[] partialTransitionRecord, StringBuilder sb, int offset, int end, String name) {
        if (offset < end) {
            sb.append(System.lineSeparator()).append(name).append(": ");
            int currentOffset = offset;
            while (currentOffset < end) {
                int targetArray = Byte.toUnsignedInt(partialTransitionRecord[currentOffset++]);
                int numberOfIndices = Byte.toUnsignedInt(partialTransitionRecord[currentOffset++]);
                sb.append(System.lineSeparator()).append("    ").append(targetArray).append(" <- [");
                for (int i = 0; i < numberOfIndices; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(Byte.toUnsignedInt(partialTransitionRecord[currentOffset++]));
                }
                sb.append("]");
            }
        }
    }

    @TruffleBoundary
    public static JsonValue toJson(byte[] partialTransitionRecord, int id) {
        JsonObject json = Json.obj(Json.prop("id", id),
                        Json.prop("reorderSwaps", dataRangeToJsonArray(partialTransitionRecord, getReorderSwapsOffset(), getReorderSwapsLength(partialTransitionRecord))));
        int arrayCopiesOffset = getArrayCopiesOffset(partialTransitionRecord);
        JsonArray copies = Json.array();
        for (int i = 0; i < getArrayCopiesLength(partialTransitionRecord); i += 2) {
            final int source = Byte.toUnsignedInt(partialTransitionRecord[arrayCopiesOffset + i]);
            final int target = Byte.toUnsignedInt(partialTransitionRecord[arrayCopiesOffset + i + 1]);
            copies.append(Json.obj(Json.prop("source", source), Json.prop("target", target)));
        }
        json.append(Json.prop("arrayCopies", copies));
        json.append(Json.prop("indexUpdates",
                        indexManipulationsToJson(partialTransitionRecord, getIndexUpdatesOffset(partialTransitionRecord), getIndexClearsOffset(partialTransitionRecord))));
        json.append(Json.prop("indexClears",
                        indexManipulationsToJson(partialTransitionRecord, getIndexClearsOffset(partialTransitionRecord), getLastGroupUpdatesOffset(partialTransitionRecord))));
        JsonArray lastGroupUpdates = Json.array();
        int offset = getLastGroupUpdatesOffset(partialTransitionRecord);
        int lastGroupUpdatesLength = getOperationCount(partialTransitionRecord, OperationKind.lastGroupUpdates);
        for (int i = 0; i < lastGroupUpdatesLength; i++) {
            int targetArray = Byte.toUnsignedInt(partialTransitionRecord[offset++]);
            int lastGroup = Byte.toUnsignedInt(partialTransitionRecord[offset++]);
            lastGroupUpdates.append(Json.obj(Json.prop("target", targetArray), Json.prop("lastGroup", lastGroup)));
        }
        json.append(Json.prop("lastGroupUpdates", lastGroupUpdates));
        return json;
    }

    @TruffleBoundary
    private static JsonArray indexManipulationsToJson(byte[] partialTransitionRecord, int offset, int end) {
        JsonArray operations = Json.array();
        int currentOffset = offset;
        while (currentOffset < end) {
            int targetArray = Byte.toUnsignedInt(partialTransitionRecord[currentOffset++]);
            int numberOfIndices = Byte.toUnsignedInt(partialTransitionRecord[currentOffset++]);
            operations.append(Json.obj(Json.prop("target", targetArray),
                            Json.prop("groupStarts", groupBoundariesToJsonArray(partialTransitionRecord, currentOffset, numberOfIndices, true)),
                            Json.prop("groupEnds", groupBoundariesToJsonArray(partialTransitionRecord, currentOffset, numberOfIndices, false))));
            currentOffset += numberOfIndices;
        }
        return operations;
    }

    @TruffleBoundary
    private static JsonArray dataRangeToJsonArray(byte[] partialTransitionRecord, int offset, int length) {
        JsonArray array = Json.array();
        for (int i = offset; i < offset + length; i++) {
            array.append(Json.val(Byte.toUnsignedInt(partialTransitionRecord[i])));
        }
        return array;
    }

    @TruffleBoundary
    private static JsonArray groupBoundariesToJsonArray(byte[] gbArray, int offset, int length, boolean entries) {
        JsonArray array = Json.array();
        for (int i = offset; i < offset + length; i++) {
            int intValue = Byte.toUnsignedInt(gbArray[i]);
            if ((intValue & 1) == (entries ? 0 : 1)) {
                array.append(Json.val(intValue / 2));
            }
        }
        return array;
    }

    @TruffleBoundary
    public static JsonValue groupBoundariesToJsonObject(byte[] arr) {
        return Json.obj(Json.prop("groupStarts", groupBoundariesToJsonArray(arr, 0, arr.length, true)),
                        Json.prop("groupEnds", groupBoundariesToJsonArray(arr, 0, arr.length, false)));
    }
}
