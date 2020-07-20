/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.ast;

import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.nfa.ASTTransition;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Objects of this class represent the capture group boundaries traversed in a single
 * {@link NFAStateTransition} or {@link ASTTransition}. The boundaries of one capture group
 * correspond to its opening and closing brackets. All capture group boundaries are mapped to
 * indices according to {@link Group#getBoundaryIndexStart()} and
 * {@link Group#getBoundaryIndexEnd()}. A transition may <em>update</em> or <em>clear</em> any
 * boundary when traversed. <br>
 * To save space, instances of this class are deduplicated in
 * {@link com.oracle.truffle.regex.tregex.parser.ast.RegexAST}. Due to the deduplication, every
 * instance must be treated as immutable!
 *
 * @see ASTTransition
 * @see NFAStateTransition
 * @see com.oracle.truffle.regex.tregex.parser.ast.RegexAST
 */
public class GroupBoundaries implements JsonConvertible {

    private static final GroupBoundaries[] STATIC_INSTANCES = new GroupBoundaries[CompilationFinalBitSet.getNumberOfStaticInstances()];

    static {
        for (int i = 0; i < CompilationFinalBitSet.getNumberOfStaticInstances(); i++) {
            STATIC_INSTANCES[i] = new GroupBoundaries(CompilationFinalBitSet.getStaticInstance(i), CompilationFinalBitSet.getEmptyInstance());
        }
    }

    private static final byte[] EMPTY_BYTE_ARRAY = {};
    private static final short[] EMPTY_SHORT_ARRAY = {};

    private final CompilationFinalBitSet updateIndices;
    private final CompilationFinalBitSet clearIndices;
    private final int cachedHash;
    @CompilationFinal(dimensions = 1) private byte[] updateArrayByte;
    @CompilationFinal(dimensions = 1) private byte[] clearArrayByte;
    @CompilationFinal(dimensions = 1) private short[] updateArray;
    @CompilationFinal(dimensions = 1) private short[] clearArray;

    GroupBoundaries(CompilationFinalBitSet updateIndices, CompilationFinalBitSet clearIndices) {
        this.updateIndices = updateIndices;
        this.clearIndices = clearIndices;
        // both bit sets are immutable, and the hash is always needed immediately in
        // RegexAST#createGroupBoundaries()
        this.cachedHash = Objects.hashCode(updateIndices) * 31 + Objects.hashCode(clearIndices);
    }

    /**
     * Used for deduplication of very common instances of this class.
     */
    public static GroupBoundaries getStaticInstance(CompilationFinalBitSet updateIndices, CompilationFinalBitSet clearIndices) {
        if (clearIndices.isEmpty()) {
            int key = updateIndices.getStaticCacheKey();
            if (key >= 0) {
                return STATIC_INSTANCES[key];
            }
        }
        return null;
    }

    public static GroupBoundaries getEmptyInstance() {
        return STATIC_INSTANCES[0];
    }

    public boolean isEmpty() {
        return updateIndices.isEmpty() && clearIndices.isEmpty();
    }

    public byte[] updatesToByteArray() {
        if (updateArrayByte == null) {
            updateArrayByte = indicesToByteArray(updateIndices);
        }
        return updateArrayByte;
    }

    public byte[] clearsToByteArray() {
        if (clearArrayByte == null) {
            clearArrayByte = indicesToByteArray(clearIndices);
        }
        return clearArrayByte;
    }

    private static byte[] indicesToByteArray(CompilationFinalBitSet indices) {
        if (indices.isEmpty()) {
            return EMPTY_BYTE_ARRAY;
        }
        final byte[] array = new byte[indices.numberOfSetBits()];
        int i = 0;
        for (int j : indices) {
            assert j < 256;
            array[i++] = (byte) j;
        }
        return array;
    }

    public void materializeArrays() {
        if (updateArray == null) {
            updateArray = indicesToShortArray(updateIndices);
            clearArray = indicesToShortArray(clearIndices);
        }
    }

    private static short[] indicesToShortArray(CompilationFinalBitSet indices) {
        if (indices.isEmpty()) {
            return EMPTY_SHORT_ARRAY;
        }
        final short[] array = new short[indices.numberOfSetBits()];
        writeIndicesToArray(indices, array, 0);
        return array;
    }

    private static void writeIndicesToArray(CompilationFinalBitSet indices, final short[] array, int offset) {
        int i = offset;
        for (int j : indices) {
            assert j < (1 << 16);
            array[i++] = (short) j;
        }
    }

    /**
     * Directly returns the {@link CompilationFinalBitSet} used to store the indices of all capture
     * group boundaries that should be updated when traversed. <br>
     * CAUTION: Do not alter the returned object!
     */
    public CompilationFinalBitSet getUpdateIndices() {
        return updateIndices;
    }

    /**
     * Directly returns the {@link CompilationFinalBitSet} used to store the indices of all capture
     * group boundaries that should be cleared when traversed. <br>
     * CAUTION: Do not alter the returned object!
     */
    public CompilationFinalBitSet getClearIndices() {
        return clearIndices;
    }

    public boolean hasIndexUpdates() {
        return !updateIndices.isEmpty();
    }

    public boolean hasIndexClears() {
        return !clearIndices.isEmpty();
    }

    /**
     * Updates the given {@link CompilationFinalBitSet}s with the values contained in this
     * {@link GroupBoundaries} object.
     */
    public void updateBitSets(CompilationFinalBitSet foreignUpdateIndices, CompilationFinalBitSet foreignClearIndices) {
        foreignUpdateIndices.union(updateIndices);
        foreignClearIndices.subtract(updateIndices);
        foreignClearIndices.union(clearIndices);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof GroupBoundaries)) {
            return false;
        }
        GroupBoundaries o = (GroupBoundaries) obj;
        return Objects.equals(updateIndices, o.updateIndices) && Objects.equals(clearIndices, o.clearIndices);
    }

    @Override
    public int hashCode() {
        return cachedHash;
    }

    /**
     * Updates a resultFactory in respect to a single transition and index.
     *
     * @param resultFactory the resultFactory to update.
     * @param index current index. All group boundaries contained in this object will be set to this
     *            value in the resultFactory.
     */
    public void applyToResultFactory(PreCalculatedResultFactory resultFactory, int index) {
        if (hasIndexUpdates()) {
            resultFactory.updateIndices(updateIndices, index);
        }
    }

    @ExplodeLoop
    public void applyExploded(int[] array, int offset, int index) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(clearArray);
        CompilerAsserts.partialEvaluationConstant(updateArray);
        for (int i = 0; i < clearArray.length; i++) {
            array[offset + Short.toUnsignedInt(clearArray[i])] = -1;
        }
        for (int i = 0; i < updateArray.length; i++) {
            array[offset + Short.toUnsignedInt(updateArray[i])] = index;
        }
    }

    public void apply(int[] array, int offset, int index) {
        for (int i = 0; i < clearArray.length; i++) {
            array[offset + Short.toUnsignedInt(clearArray[i])] = -1;
        }
        for (int i = 0; i < updateArray.length; i++) {
            array[offset + Short.toUnsignedInt(updateArray[i])] = index;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (hasIndexUpdates()) {
            appendBitSet(sb, updateIndices, false).append(")(");
            appendBitSet(sb, updateIndices, true);
        }
        if (hasIndexClears()) {
            sb.append(" clr{");
            appendBitSet(sb, clearIndices, false).append(")(");
            appendBitSet(sb, clearIndices, true);
            sb.append("}");
        }
        return sb.toString();
    }

    @TruffleBoundary
    private static StringBuilder appendBitSet(StringBuilder sb, CompilationFinalBitSet gbBitSet, boolean entries) {
        boolean first = true;
        if (gbBitSet != null) {
            for (int i : gbBitSet) {
                if ((i & 1) == (entries ? 0 : 1)) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(",");
                    }
                    sb.append(Json.val(i / 2));
                }
            }
        }
        return sb;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("updateEnter", gbBitSetGroupEntriesToJsonArray(updateIndices)),
                        Json.prop("updateExit", gbBitSetGroupExitsToJsonArray(updateIndices)),
                        Json.prop("clearEnter", gbBitSetGroupEntriesToJsonArray(clearIndices)),
                        Json.prop("clearExit", gbBitSetGroupExitsToJsonArray(clearIndices)));
    }

    @TruffleBoundary
    private static JsonArray gbBitSetGroupEntriesToJsonArray(CompilationFinalBitSet gbArray) {
        return gbBitSetGroupPartToJsonArray(gbArray, true);
    }

    @TruffleBoundary
    private static JsonArray gbBitSetGroupExitsToJsonArray(CompilationFinalBitSet gbArray) {
        return gbBitSetGroupPartToJsonArray(gbArray, false);
    }

    @TruffleBoundary
    private static JsonArray gbBitSetGroupPartToJsonArray(CompilationFinalBitSet gbBitSet, boolean entries) {
        JsonArray array = Json.array();
        if (gbBitSet != null) {
            for (int i : gbBitSet) {
                if ((i & 1) == (entries ? 0 : 1)) {
                    array.append(Json.val(i / 2));
                }
            }
        }
        return array;
    }

    @TruffleBoundary
    public JsonArray indexUpdateSourceSectionsToJson(RegexAST ast) {
        if (!hasIndexUpdates()) {
            return Json.array();
        }
        return RegexAST.sourceSectionsToJson(getUpdateIndices().stream().mapToObj(x -> ast.getSourceSections(ast.getGroupByBoundaryIndex(x)).get(x & 1)));
    }

}
