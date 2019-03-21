/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.ast;

import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.nfa.ASTTransition;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.DFACaptureGroupPartialTransitionNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

import java.util.Objects;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

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

    private static final GroupBoundaries EMPTY_INSTANCE = new GroupBoundaries(new CompilationFinalBitSet(0), new CompilationFinalBitSet(0));

    private final CompilationFinalBitSet updateIndices;
    private final CompilationFinalBitSet clearIndices;
    private final int cachedHash;

    GroupBoundaries(CompilationFinalBitSet updateIndices, CompilationFinalBitSet clearIndices) {
        this.updateIndices = updateIndices;
        this.clearIndices = clearIndices;
        // both bit sets are immutable, and the hash is always needed immediately in
        // RegexAST#createGroupBoundaries()
        this.cachedHash = Objects.hashCode(updateIndices) * 31 + Objects.hashCode(clearIndices);
    }

    public static GroupBoundaries getEmptyInstance() {
        return EMPTY_INSTANCE;
    }

    /**
     * Creates a byte array suitable to be part of the {@code indexUpdates} parameter passed to
     * {@link DFACaptureGroupPartialTransitionNode#create(DFAGenerator, byte[], byte[], byte[][], byte[][], byte)}
     * from this object.
     * 
     * @param targetArray the index of the row to be targeted.
     *
     * @see DFACaptureGroupPartialTransitionNode#create(DFAGenerator, byte[], byte[], byte[][],
     *      byte[][], byte)
     */
    public byte[] updatesToPartialTransitionArray(int targetArray) {
        return createPartialTransitionArray(targetArray, updateIndices);
    }

    /**
     * Creates a byte array suitable to be part of the {@code indexClears} parameter passed to
     * {@link DFACaptureGroupPartialTransitionNode#create(DFAGenerator, byte[], byte[], byte[][], byte[][], byte)}
     * from this object.
     * 
     * @param targetArray the index of the row to be targeted.
     *
     * @see DFACaptureGroupPartialTransitionNode#create(DFAGenerator, byte[], byte[], byte[][],
     *      byte[][], byte)
     */
    public byte[] clearsToPartialTransitionArray(int targetArray) {
        return createPartialTransitionArray(targetArray, clearIndices);
    }

    private static byte[] createPartialTransitionArray(int targetArray, CompilationFinalBitSet indices) {
        assert !indices.isEmpty() : "should not be called on empty sets";
        final byte[] indexUpdate = new byte[indices.numberOfSetBits() + 1];
        indexUpdate[0] = (byte) targetArray;
        int i = 1;
        for (int j : indices) {
            assert j < 256;
            indexUpdate[i++] = (byte) j;
        }
        return indexUpdate;
    }

    /**
     * Directly returns the {@link CompilationFinalBitSet} used to store the indices of all capture
     * group boundaries that should be updated when traversed. <br>
     * CAUTION: Do not alter the returned object!
     */
    public CompilationFinalBitSet getUpdateIndices() {
        return updateIndices;
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
}
