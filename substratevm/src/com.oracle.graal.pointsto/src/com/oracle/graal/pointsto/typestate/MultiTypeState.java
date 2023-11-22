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
     * quickly iterate over the types.
     */
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
        assert !TypeStateUtils.needsTrim(typesBitSet) : typesBitSet;
        this.typesBitSet = typesBitSet;
        this.typesCount = typesCount;
        this.canBeNull = canBeNull;
        this.merged = false;
        assert this.typesCount > 1 : "Multi type state with single type.";
        PointsToStats.registerTypeState(bb, this);
    }

    /** Create a type state with the same content and a reversed canBeNull value. */
    @SuppressWarnings("this-escape")
    protected MultiTypeState(PointsToAnalysis bb, boolean canBeNull, MultiTypeState other) {
        this.typesBitSet = other.typesBitSet;
        this.typesCount = other.typesCount;
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

    protected BitSet typesBitSet() {
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

    /** Iterates over the types bit set and returns the type IDs in ascending order. */
    private abstract class BitSetIterator<T> implements Iterator<T> {
        private int current = typesBitSet.nextSetBit(0);

        @Override
        public boolean hasNext() {
            return current >= 0;
        }

        public Integer nextSetBit() {
            int next = current;
            current = typesBitSet.nextSetBit(current + 1);
            return next;
        }
    }

    @Override
    public Iterator<AnalysisObject> objectsIterator(AnalysisType exactType) {
        throw AnalysisError.shouldNotReachHere("unimplemented");
    }

    @Override
    public final boolean containsType(AnalysisType exactType) {
        return typesBitSet.get(exactType.getId());
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
        assert bb.analysisPolicy().isMergingEnabled() : "policy mismatch";

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
        result = 31 * result + typesBitSet.hashCode();
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
                        this.typesCount == that.typesCount && this.typesBitSet.equals(that.typesBitSet);
    }

    @Override
    public String toString() {
        return "MType<" + typesCount + ":" + (canBeNull ? "null," : "") + "TODO" + ">";
    }
}
