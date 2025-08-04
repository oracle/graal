/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.typestate;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;

/**
 * Array-based {@link MultiTypeState} implementation that stores the type ids in a sorted array.
 * Most of the sets used during {@link PointsToAnalysis} are small due to <i>saturation</i>, while
 * the Type IDs can be spread out. Thus, storing the ids in an array saves space. The upper limit
 * after which a {@link MultiTypeStateWithBitSet} is used is configurable via
 * {@link PointstoOptions#MultiTypeStateArrayBitSetThreshold}
 * <p>
 * <b>Invariant</b>: {@link #typeIds} is sorted and contains no duplicates, checked during
 * construction by {@link #checkTypeIdArray}.
 */
public final class MultiTypeStateWithArray extends MultiTypeState {

    private final boolean canBeNull;
    private final int[] typeIds;

    public MultiTypeStateWithArray(boolean canBeNull, int[] typeIds) {
        assert checkTypeIdArray(typeIds) : "The typeIds array should be sorted and contain no duplicates: " + Arrays.toString(typeIds);
        assert typeIds.length > 1 : "MultiTypeStateWithArray should be used only for sets containing at least two types: " + Arrays.toString(typeIds);
        this.canBeNull = canBeNull;
        this.typeIds = typeIds;
    }

    /**
     * Check that the array is sorted and contains no duplicates.
     */
    private static boolean checkTypeIdArray(int[] typesId) {
        for (int i = 1; i < typesId.length; i++) {
            if (typesId[i] <= typesId[i - 1]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int typesCount() {
        return typeIds.length;
    }

    @Override
    public AnalysisType exactType() {
        return null;
    }

    public int[] getTypeIdArray() {
        return typeIds;
    }

    @Override
    protected Iterator<AnalysisType> typesIterator(BigBang bb) {
        return new Iterator<>() {
            int pos = 0;

            @Override
            public boolean hasNext() {
                return pos < typeIds.length;
            }

            @Override
            public AnalysisType next() {
                return bb.getUniverse().getType(typeIds[pos++]);
            }
        };
    }

    @Override
    public Iterator<Integer> typeIdsIterator() {
        return new Iterator<>() {
            int pos = 0;

            @Override
            public boolean hasNext() {
                return pos < typeIds.length;
            }

            @Override
            public Integer next() {
                return typeIds[pos++];
            }
        };
    }

    @Override
    public boolean containsType(AnalysisType exactType) {
        return containsType(exactType.getId());
    }

    @Override
    public boolean containsType(int typeId) {
        return Arrays.binarySearch(typeIds, typeId) >= 0;
    }

    @Override
    public int objectsCount() {
        return typeIds.length;
    }

    @Override
    protected Iterator<AnalysisObject> objectsIterator(BigBang bb) {
        return new Iterator<>() {
            int pos = 0;

            @Override
            public boolean hasNext() {
                return pos < typeIds.length;
            }

            @Override
            public AnalysisObject next() {
                return bb.getUniverse().getType(typeIds[pos++]).getContextInsensitiveAnalysisObject();
            }
        };
    }

    @Override
    protected Iterator<AnalysisObject> objectsIterator(AnalysisType type) {
        throw AnalysisError.shouldNotReachHere("unimplemented");
    }

    @Override
    public boolean canBeNull() {
        return canBeNull;
    }

    @Override
    public void noteMerge(PointsToAnalysis bb) {
        AnalysisError.shouldNotReachHere("MultiTypeStateWithArray doesn't support merging. It is indented to be used with a context-insensitive analysis.");
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MultiTypeStateWithArray that = (MultiTypeStateWithArray) o;

        return canBeNull == that.canBeNull && Arrays.equals(typeIds, that.typeIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), canBeNull, Arrays.hashCode(typeIds));
    }

    @Override
    public TypeState forCanBeNull(PointsToAnalysis bb, boolean stateCanBeNull) {
        if (canBeNull == stateCanBeNull) {
            return this;
        }
        return PointsToStats.registerTypeState(bb, new MultiTypeStateWithArray(stateCanBeNull, typeIds));
    }

    @Override
    public String toString() {
        return "MType<" + typesCount() + ":" + (canBeNull ? "null," : "") + "TODO" + ">";
    }

    public int maxTypeId() {
        return typeIds[typeIds.length - 1];
    }

    /**
     * Returns {@code true} if this {@code TypeState} is a superset of {@code other}.
     */
    public boolean isSuperSet(MultiTypeStateWithArray other) {
        int thisIdx = 0;
        int otherIdx = 0;
        while (thisIdx < this.typeIds.length && otherIdx < other.typeIds.length) {
            if (this.typeIds[thisIdx] == other.typeIds[otherIdx]) {
                // the element is contained in both this.typeIds and other.typeIds, skip it
                thisIdx++;
                otherIdx++;
            } else if (this.typeIds[thisIdx] < other.typeIds[otherIdx]) {
                // this.typeIds contains an element not present in other.typeIds, skip it
                thisIdx++;
            } else {
                // if this.typeIds[thisIdx] > other.typeIds[otherIdx] then not a super-set
                // since other.typeIds contains an element not present in this.typeIds
                return false;
            }
        }
        // check if all elements of other.typeIds were matched in this.typeIds
        return otherIdx == other.typeIds.length;
    }
}
