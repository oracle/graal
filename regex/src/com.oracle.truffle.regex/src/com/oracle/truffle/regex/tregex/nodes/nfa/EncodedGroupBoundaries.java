/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.nfa;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;

/**
 * Flattened encoding for {@link GroupBoundaries} records shared by compact NFA executors.
 *
 * <pre>
 * group-boundary record at groupBoundaryRecord:
 *   [ clearLength, updateLength, lastGroup,
 *     clear index 0,
 *     clear index 1,
 *     ...,
 *     update index 0,
 *     update index 1,
 *     ... ]
 * </pre>
 */
final class EncodedGroupBoundaries {

    private static final int FIELD_CLEAR_LENGTH = 0;
    private static final int FIELD_UPDATE_LENGTH = 1;
    private static final int FIELD_LAST_GROUP = 2;
    private static final int FIELD_INDICES_START = 3;

    private EncodedGroupBoundaries() {
    }

    static void apply(int[] groupBoundaries, int groupBoundaryRecord, int[] array, int cgOffset, int lgOffset, int index, boolean trackLastGroup) {
        int updateLength = getUpdateLength(groupBoundaries, groupBoundaryRecord);
        int clearLength = getClearLength(groupBoundaries, groupBoundaryRecord);
        int lastGroup = getLastGroup(groupBoundaries, groupBoundaryRecord);
        int clearStart = getClearStart(groupBoundaryRecord);
        int updateStart = getUpdateStart(groupBoundaries, groupBoundaryRecord);
        for (int i = 0; i < clearLength; i++) {
            array[cgOffset + groupBoundaries[clearStart + i]] = -1;
        }
        for (int i = 0; i < updateLength; i++) {
            array[cgOffset + groupBoundaries[updateStart + i]] = index;
        }
        if (trackLastGroup && lastGroup != -1) {
            array[lgOffset] = lastGroup;
        }
    }

    @ExplodeLoop
    static void applyExploded(int[] groupBoundaries, int groupBoundaryRecord, int[] array, int cgOffset, int lgOffset, int index, boolean trackLastGroup, boolean dontOverwriteLastGroup) {
        int updateLength = getUpdateLength(groupBoundaries, groupBoundaryRecord);
        int clearLength = getClearLength(groupBoundaries, groupBoundaryRecord);
        int lastGroup = getLastGroup(groupBoundaries, groupBoundaryRecord);
        int clearStart = getClearStart(groupBoundaryRecord);
        int updateStart = getUpdateStart(groupBoundaries, groupBoundaryRecord);
        CompilerAsserts.partialEvaluationConstant(groupBoundaries);
        CompilerAsserts.partialEvaluationConstant(groupBoundaryRecord);
        CompilerAsserts.partialEvaluationConstant(updateLength);
        CompilerAsserts.partialEvaluationConstant(clearLength);
        CompilerAsserts.partialEvaluationConstant(lastGroup);
        CompilerAsserts.partialEvaluationConstant(clearStart);
        CompilerAsserts.partialEvaluationConstant(updateStart);
        for (int i = 0; i < clearLength; i++) {
            array[cgOffset + groupBoundaries[clearStart + i]] = -1;
        }
        for (int i = 0; i < updateLength; i++) {
            array[cgOffset + groupBoundaries[updateStart + i]] = index;
        }
        if (trackLastGroup && lastGroup != -1 && (!dontOverwriteLastGroup || array[lgOffset] == -1)) {
            array[lgOffset] = lastGroup;
        }
    }

    private static int getUpdateLength(int[] groupBoundaries, int groupBoundaryRecord) {
        return groupBoundaries[groupBoundaryRecord + FIELD_UPDATE_LENGTH];
    }

    private static int getClearLength(int[] groupBoundaries, int groupBoundaryRecord) {
        return groupBoundaries[groupBoundaryRecord + FIELD_CLEAR_LENGTH];
    }

    private static int getLastGroup(int[] groupBoundaries, int groupBoundaryRecord) {
        return groupBoundaries[groupBoundaryRecord + FIELD_LAST_GROUP];
    }

    private static int getClearStart(int groupBoundaryRecord) {
        return groupBoundaryRecord + FIELD_INDICES_START;
    }

    private static int getUpdateStart(int[] groupBoundaries, int groupBoundaryRecord) {
        return getClearStart(groupBoundaryRecord) + getClearLength(groupBoundaries, groupBoundaryRecord);
    }

    static final class Builder {

        private final EconomicMap<GroupBoundaries, Integer> groupBoundariesMap = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        private final IntArrayBuffer groupBoundariesBuffer = new IntArrayBuffer();

        int getOrCreate(GroupBoundaries gb) {
            Integer groupBoundaryRecord = groupBoundariesMap.get(gb);
            if (groupBoundaryRecord == null) {
                groupBoundaryRecord = groupBoundariesBuffer.length();
                groupBoundariesMap.put(gb, groupBoundaryRecord);
                groupBoundariesBuffer.add(gb.getClearIndices().numberOfSetBits());
                groupBoundariesBuffer.add(gb.getUpdateIndices().numberOfSetBits());
                groupBoundariesBuffer.add(gb.getLastGroup());
                for (int index : gb.getClearIndices()) {
                    groupBoundariesBuffer.add(index);
                }
                for (int index : gb.getUpdateIndices()) {
                    groupBoundariesBuffer.add(index);
                }
            }
            return groupBoundaryRecord;
        }

        int[] toArray() {
            return groupBoundariesBuffer.toArray();
        }
    }
}
