/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.BitSet;
import java.util.Iterator;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;

public class MultiTypeState extends TypeState {

    /**
     * Keep a bit set for types to easily answer queries like contains type or types count, and
     * quickly iterate over the types. This bit set is represented by SmallBitSet if the cardinality
     * is small (below SmallBitSet.MAX_CARDINALITY) and typesBitSet otherwise.
     */
    protected final SmallBitSet smallTypesBitSet;
    protected final BitSet typesBitSet;
    /** Cache the number of types since BitSet.cardinality() computes it every time is called. */
    protected final int typesCount;
    /** Can this type state represent the null value? */
    protected final boolean canBeNull;
    /** Has this type state been merged with the all-instantiated type state? */
    protected boolean merged;

    /** Creates a new type state using the provided types bit set and objects. */
    @SuppressWarnings("this-escape")
    public MultiTypeState(PointsToAnalysis bb, boolean canBeNull, BitSet typesBitSet, int typesCount) {
        assert !TypeStateUtils.needsTrim(typesBitSet);
        this.typesCount = typesCount;
        if (isSmall()) {
            this.smallTypesBitSet = new SmallBitSet(typesBitSet);
            this.typesBitSet = null;
        } else {
            this.typesBitSet = typesBitSet;
            this.smallTypesBitSet = null;
        }
        this.canBeNull = canBeNull;
        this.merged = false;
        assert this.typesCount > 1 : "Multi type state with single type.";
        PointsToStats.registerTypeState(bb, this);
    }

    /** Create a type state with the same content and a reversed canBeNull value. */
    @SuppressWarnings("this-escape")
    protected MultiTypeState(PointsToAnalysis bb, boolean canBeNull, MultiTypeState other) {
        this.typesCount = other.typesCount;
        if (isSmall()) {
            this.smallTypesBitSet = other.smallTypesBitSet;
            this.typesBitSet = null;
        } else {
            this.typesBitSet = other.typesBitSet;
            this.smallTypesBitSet = null;
        }
        this.canBeNull = canBeNull;
        this.merged = other.merged;
        PointsToStats.registerTypeState(bb, this);
    }

    /** Get the number of objects. */
    @Override
    public int objectsCount() {
        return typesCount;
    }

    @Override
    public AnalysisType exactType() {
        return null;
    }

    @Override
    public int typesCount() {
        return typesCount;
    }

    private boolean isSmall() {
        return typesCount <= SmallBitSet.MAX_CARDINALITY;
    }

    public BitSet typesBitSet() {
        if (isSmall()) {
            return smallTypesBitSet.asBitSet();
        }
        return typesBitSet;
    }

    @Override
    public final Iterator<AnalysisType> typesIterator(BigBang bb) {
        return new BitSetIterator<>() {
            @Override
            public AnalysisType next() {
                return bb.getUniverse().getType(nextSetBit());
            }
        };
    }

    @Override
    protected Iterator<AnalysisObject> objectsIterator(BigBang bb) {
        return new BitSetIterator<>() {
            @Override
            public AnalysisObject next() {
                return bb.getUniverse().getType(nextSetBit()).getContextInsensitiveAnalysisObject();
            }
        };
    }

    protected int firstSetBit() {
        return nextSetBitSmall(0);
    }

    protected int nextSetBitSmall(int fromIndex) {
        if (isSmall()) {
            return smallTypesBitSet.nextSetBit(fromIndex);
        }
        return typesBitSet.nextSetBit(fromIndex);
    }

    /** Iterates over the types bit set and returns the type IDs in ascending order. */
    private abstract class BitSetIterator<T> implements Iterator<T> {
        private int current = firstSetBit();

        @Override
        public boolean hasNext() {
            return current >= 0;
        }

        public Integer nextSetBit() {
            int next = current;
            current = nextSetBitSmall(current + 1);
            return next;
        }
    }

    @Override
    public Iterator<AnalysisObject> objectsIterator(AnalysisType exactType) {
        throw AnalysisError.shouldNotReachHere("unimplemented");
    }

    @Override
    public final boolean containsType(AnalysisType exactType) {
        if (isSmall()) {
            return this.smallTypesBitSet.get(exactType.getId());
        }
        return this.typesBitSet.get(exactType.getId());
    }

    public boolean bitSetEquals(MultiTypeState that) {
        if (isSmall()) {
            return this.smallTypesBitSet.equals(that.smallTypesBitSet);
        }
        return this.typesBitSet.equals(that.typesBitSet);
    }

    public boolean bitSetShallowEquals(MultiTypeState that) {
        if (isSmall()) {
            return this.smallTypesBitSet == that.smallTypesBitSet;
        }
        return this.typesBitSet == that.typesBitSet;
    }

    public int length() {
        if (isSmall()) {
            return this.smallTypesBitSet.length();
        }
        return this.typesBitSet.length();
    }

    public boolean intersects(MultiTypeState other) {
        if (other.isSmall()) {
            // The other TypeState is small. Drive the loop with it.
            SmallBitSet obs = other.smallTypesBitSet;
            if (this.isSmall()) {
                for (int i = obs.nextSetBit(0); i >= 0; i = obs.nextSetBit(i + 1)) {
                    if (smallTypesBitSet.get(i)) {
                        return true;
                    }
                }
            } else {
                for (int i = obs.nextSetBit(0); i >= 0; i = obs.nextSetBit(i + 1)) {
                    if (typesBitSet.get(i)) {
                        return true;
                    }
                }
            }
        } else {
            if (this.isSmall()) {
                // This TypeState is small. Drive the loop with it.
                for (int i = smallTypesBitSet.nextSetBit(0); i >= 0; i = smallTypesBitSet.nextSetBit(i + 1)) {
                    if (other.typesBitSet.get(i)) {
                        return true;
                    }
                }
            } else {
                return this.typesBitSet.intersects(other.typesBitSet);
            }
        }
        return false;
    }

    @Override
    public TypeState forCanBeNull(PointsToAnalysis bb, boolean resultCanBeNull) {
        if (resultCanBeNull == this.canBeNull()) {
            return this;
        } else {
            /* Just flip the canBeNull flag and copy the rest of the values from this. */
            return new MultiTypeState(bb, resultCanBeNull, this);
        }
    }

    @Override
    public final boolean canBeNull() {
        return canBeNull;
    }

    /** Note that the objects of this type state have been merged. */
    @Override
    public void noteMerge(PointsToAnalysis bb) {
        assert bb.analysisPolicy().isMergingEnabled();

        if (!merged) {
            for (AnalysisType type : types(bb)) {
                type.getContextInsensitiveAnalysisObject().noteMerge(bb);
            }
            merged = true;
        }
    }

    @Override
    public boolean isMerged() {
        return merged;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + (isSmall() ? smallTypesBitSet.hashCode() : typesBitSet.hashCode());
        result = 31 * result + (canBeNull ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MultiTypeState that = (MultiTypeState) o;
        return this.canBeNull == that.canBeNull &&
                        this.typesCount == that.typesCount && this.bitSetEquals(that);
    }

    @Override
    public String toString() {
        return "MType<" + typesCount + ":" + (canBeNull ? "null," : "") + "TODO" + ">";
    }

    class SmallBitSet {
        SmallBitSet(BitSet other) {
            assert other.cardinality() <= MAX_CARDINALITY;
            set = new int[other.cardinality()];
            for (int i = other.nextSetBit(0); i >= 0; i = other.nextSetBit(i + 1)) {
                set[cardinality++] = i;
            }
            assert cardinality == other.cardinality();
        }

        public boolean get(int bit) {
            assert bit >= 0 : "get() bit must be non-negative";
            for (int i = 0; i < cardinality; i++) {
                if (set[i] == bit) {
                    return true;
                }
            }
            return false;
        }

        // Returns the "logical size" of this BitSet: the index of the highest
        // set bit in the BitSet plus one.
        public int length() {
            return set[cardinality - 1] + 1;
        }

        public BitSet asBitSet() {
            BitSet result = new BitSet(length());
            for (int i = 0; i < cardinality; i++) {
                result.set(set[i]);
            }
            // Clone will trim to size
            return (BitSet) result.clone();
        }

        public int nextSetBit(int fromIndex) {
            if (fromIndex < 0) {
                throw new IndexOutOfBoundsException();
            }
            for (int i = 0; i < cardinality; i++) {
                int current = set[i];
                assert current != UNSET : "found UNSET in valid section";
                if (current >= fromIndex) {
                    return current;
                }
            }
            return UNSET;
        }

        @Override
        public String toString() {
            String result = "{";
            String sep = "";
            for (int i = 0; i < cardinality; i++) {
                result += sep;
                result += String.valueOf(set[i]);
                sep = ", ";
            }
            result += "}";
            return result;
        }

        public static final int UNSET = -1;
        public static final int MAX_CARDINALITY = 10;
        int[] set;
        int cardinality = 0;
    }
}
