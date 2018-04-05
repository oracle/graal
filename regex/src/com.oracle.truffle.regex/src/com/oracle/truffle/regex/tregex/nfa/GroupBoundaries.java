/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nfa;

import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

import java.util.Objects;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class GroupBoundaries implements JsonConvertible {

    private CompilationFinalBitSet updateIndices;
    private CompilationFinalBitSet clearIndices;

    private boolean hashComputed = false;
    private int cachedHash;

    /**
     * Returns an array containing the indices of all capture group boundaries traversed in this
     * object, where indices are seen in the following form: [start of CG 0, end of CG 0, start of
     * CG 1, end of CG 1, ... ].
     *
     * @return indices of all boundaries traversed.
     */
    public CompilationFinalBitSet getUpdateIndices() {
        return updateIndices;
    }

    public CompilationFinalBitSet getClearIndices() {
        return clearIndices;
    }

    public boolean hasIndexUpdates() {
        assert updateIndices == null || !updateIndices.isEmpty();
        return updateIndices != null;
    }

    public boolean hasIndexClears() {
        assert clearIndices == null || !clearIndices.isEmpty();
        return clearIndices != null;
    }

    public void setIndices(CompilationFinalBitSet updates, CompilationFinalBitSet clears) {
        if (!updates.isEmpty()) {
            updateIndices = updates.copy();
        }
        if (!clears.isEmpty()) {
            clearIndices = clears.copy();
        }
        hashComputed = false;
    }

    /**
     * Merge this GroupBoundaries object with another.
     *
     * @param o other GroupBoundaries object. Assumed to be disjoint with this.
     */
    public void addAll(GroupBoundaries o) {
        if (updateIndices == null) {
            if (o.updateIndices != null) {
                updateIndices = o.updateIndices.copy();
            }
        } else {
            if (o.updateIndices != null) {
                updateIndices.union(o.updateIndices);
            }
        }
        if (clearIndices == null) {
            if (o.clearIndices != null) {
                clearIndices = o.clearIndices.copy();
            }
        } else {
            if (o.clearIndices != null) {
                clearIndices.union(o.clearIndices);
            }
        }
        hashComputed = false;
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
        if (!hashComputed) {
            cachedHash = Objects.hashCode(updateIndices) * 31 + Objects.hashCode(clearIndices);
            hashComputed = true;
        }
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
