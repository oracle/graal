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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

import java.util.Arrays;

public class GroupBoundaries {

    private static final byte[] EMPTY_INDEX_ARRAY = {};

    private byte[] updateIndices;
    private byte[] clearIndices;

    private boolean hashComputed = false;
    private int cachedHash;

    public GroupBoundaries() {
        this(EMPTY_INDEX_ARRAY, EMPTY_INDEX_ARRAY);
    }

    private GroupBoundaries(byte[] updateIndices, byte[] clearIndices) {
        this.updateIndices = updateIndices;
        this.clearIndices = clearIndices;
    }

    /**
     * Returns an array containing the indices of all capture group boundaries traversed in this
     * object, where indices are seen in the following form: [start of CG 0, end of CG 0, start of
     * CG 1, end of CG 1, ... ].
     *
     * @return indices of all boundaries traversed.
     */
    public byte[] getUpdateIndices() {
        return updateIndices;
    }

    public byte[] getClearIndices() {
        return clearIndices;
    }

    public boolean hasIndexUpdates() {
        return updateIndices.length > 0;
    }

    public boolean hasIndexClears() {
        return clearIndices.length > 0;
    }

    public void setIndices(
                    CompilationFinalBitSet updateStarts,
                    CompilationFinalBitSet updateEnds,
                    CompilationFinalBitSet clearStarts,
                    CompilationFinalBitSet clearEnds) {
        updateIndices = createIndexArray(updateStarts, updateEnds);
        clearIndices = createIndexArray(clearStarts, clearEnds);
    }

    /**
     * Merge this GroupBoundaries object with another.
     *
     * @param o other GroupBoundaries object. Assumed to be disjoint with this.
     */
    public void addAll(GroupBoundaries o) {
        updateIndices = concatIndexArrays(updateIndices, o.updateIndices);
        clearIndices = concatIndexArrays(clearIndices, o.clearIndices);
    }

    private static byte[] concatIndexArrays(byte[] a, byte[] b) {
        if (b.length == 0) {
            return a;
        }
        if (a.length == 0) {
            return b;
        }
        final byte[] concat = new byte[a.length + b.length];
        System.arraycopy(a, 0, concat, 0, a.length);
        System.arraycopy(b, 0, concat, a.length, b.length);
        return concat;
    }

    private static byte[] createIndexArray(CompilationFinalBitSet starts, CompilationFinalBitSet ends) {
        if (starts.isEmpty() && ends.isEmpty()) {
            return EMPTY_INDEX_ARRAY;
        }
        byte[] indices = new byte[starts.numberOfSetBits() + ends.numberOfSetBits()];
        int i = 0;
        for (int g : starts) {
            indices[i++] = (byte) (g * 2);
        }
        for (int g : ends) {
            indices[i++] = (byte) (g * 2 + 1);
        }
        return indices;
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
        return Arrays.equals(updateIndices, o.updateIndices) && Arrays.equals(clearIndices, o.clearIndices);
    }

    @Override
    public int hashCode() {
        if (!hashComputed) {
            cachedHash = Arrays.hashCode(updateIndices) * 31 + Arrays.hashCode(clearIndices);
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
            sb.append(gbArrayGroupExitsToString(updateIndices, 0, false)).append(")");
            sb.append("(").append(gbArrayGroupEntriesToString(updateIndices, 0, false));
        }
        if (hasIndexClears()) {
            sb.append(" clr{");
            sb.append(gbArrayGroupExitsToString(clearIndices, 0, false)).append(")");
            sb.append("(").append(gbArrayGroupEntriesToString(clearIndices, 0, false));
            sb.append("}");
        }
        return sb.toString();
    }

    @CompilerDirectives.TruffleBoundary
    public DebugUtil.Table toTable() {
        return new DebugUtil.Table("GroupBoundaries",
                        new DebugUtil.Value("updateEnter", gbArrayGroupEntriesToString(updateIndices)),
                        new DebugUtil.Value("updateExit", gbArrayGroupExitsToString(updateIndices)),
                        new DebugUtil.Value("clearEnter", gbArrayGroupEntriesToString(clearIndices)),
                        new DebugUtil.Value("clearExit", gbArrayGroupExitsToString(clearIndices)));
    }

    @CompilerDirectives.TruffleBoundary
    public static String gbArrayGroupEntriesToString(byte[] gbArray) {
        return gbArrayGroupEntriesToString(gbArray, 0);
    }

    @CompilerDirectives.TruffleBoundary
    public static String gbArrayGroupEntriesToString(byte[] gbArray, int startFrom) {
        return gbArrayGroupEntriesToString(gbArray, startFrom, true);
    }

    @CompilerDirectives.TruffleBoundary
    public static String gbArrayGroupEntriesToString(byte[] gbArray, int startFrom, boolean addBrackets) {
        return gbArrayGroupPartToString(gbArray, startFrom, addBrackets, true);
    }

    @CompilerDirectives.TruffleBoundary
    public static String gbArrayGroupExitsToString(byte[] gbArray) {
        return gbArrayGroupExitsToString(gbArray, 0);
    }

    @CompilerDirectives.TruffleBoundary
    public static String gbArrayGroupExitsToString(byte[] gbArray, int startFrom) {
        return gbArrayGroupExitsToString(gbArray, startFrom, true);
    }

    @CompilerDirectives.TruffleBoundary
    public static String gbArrayGroupExitsToString(byte[] gbArray, int startFrom, boolean addBrackets) {
        return gbArrayGroupPartToString(gbArray, startFrom, addBrackets, false);
    }

    @CompilerDirectives.TruffleBoundary
    private static String gbArrayGroupPartToString(byte[] gbArray, int startFrom, boolean addBrackets, boolean printEntries) {
        StringBuilder sb = new StringBuilder();
        if (addBrackets) {
            sb.append("[");
        }
        for (int i = startFrom; i < gbArray.length; i++) {
            if ((gbArray[i] & 1) == (printEntries ? 0 : 1)) {
                if (sb.length() > (addBrackets ? 1 : 0)) {
                    sb.append(",");
                }
                sb.append(Byte.toUnsignedInt(gbArray[i]) / 2);
            }
        }
        if (addBrackets) {
            sb.append("]");
        }
        return sb.toString();
    }
}
