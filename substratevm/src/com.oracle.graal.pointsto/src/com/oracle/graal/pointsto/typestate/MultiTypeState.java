/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.BitSet;
import java.util.Iterator;

import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;

public class MultiTypeState extends TypeState {

    protected final BigBang bigbang;
    /** The objects of this type state. */
    protected final AnalysisObject[] objects;
    /** See {@link #getObjectTypeIds()}. */
    protected int[] objectTypeIds;
    /**
     * Keep a bit set for types to easily answer queries like contains type or types count, and
     * quickly iterate over the types. It costs us one linear pass over the objects when the state
     * is first created but the cost is amortized for frequently used states.
     */
    final BitSet typesBitSet;
    /** Cache the number of types since BitSet.cardinality() computes it every time is called. */
    private final int typesCount;
    /** Can this type state represent the null value? */
    protected final boolean canBeNull;
    /** Has this type state been merged with the all-instantiated type state? */
    protected boolean merged;

    /** Creates a new type state using the provided types bit set and objects. */
    MultiTypeState(BigBang bb, boolean canBeNull, int properties, BitSet typesBitSet, AnalysisObject... objects) {
        super(properties);
        this.bigbang = bb;
        this.objects = objects;
        /*
         * Trim the typesBitSet to size eagerly. The typesBitSet is effectively immutable, i.e., no
         * calls to mutating methods are made on it after it is set in the MultiTypeState, thus we
         * don't need to use any external synchronization. However, to keep it immutable we use
         * BitSet.clone() when deriving a new BitSet since the set operations (and, or, etc.) mutate
         * the original object. The problem is that BitSet.clone() breaks the informal contract that
         * the clone method should not modify the original object; it calls trimToSize() before
         * creating a copy. Thus, trimming the bit set here ensures that cloning does not modify the
         * typesBitSet. Since BitSet is not thread safe mutating it during cloning is problematic in
         * a multithreaded environment. If for example you iterate over the bits at the same time as
         * another thread calls clone() the words[] array can be in an inconsistent state.
         */
        TypeStateUtils.trimBitSetToSize(typesBitSet);
        this.typesBitSet = typesBitSet;
        long cardinality = typesBitSet.cardinality();
        assert cardinality < Integer.MAX_VALUE : "We don't expect so much types.";
        this.typesCount = (int) cardinality;
        this.canBeNull = canBeNull;
        this.merged = false;
        assert typesCount > 1 : "Multi type state with single type.";
        assert objects.length > 1 : "Multi type state with single object.";
        assert !PointstoOptions.ExtendedAsserts.getValue(bb.getOptions()) || checkObjects(bb.getOptions());
        PointsToStats.registerTypeState(bb, this);
    }

    /** Create a type state with the same content and a reversed canBeNull value. */
    private MultiTypeState(BigBang bb, boolean canBeNull, MultiTypeState other) {
        super(other.properties);
        this.bigbang = bb;
        this.objects = other.objects;
        this.typesBitSet = other.typesBitSet;
        this.typesCount = other.typesCount;
        this.canBeNull = canBeNull;
        this.merged = other.merged;
        PointsToStats.registerTypeState(bb, this);
    }

    /**
     * Returns an array of all type ids from the {@link #objects} array. This mitigates the CPU
     * cache misses when iterating over all AnalysisObject and dereferencing the type field over and
     * over again.
     */
    public int[] getObjectTypeIds() {
        if (objectTypeIds == null) {
            // One item longer, so we can support readahead of one in the loop without
            // ArrayOutOfBoundsException
            int[] result = new int[objects.length + 1];
            for (int i = 0; i < objects.length; i++) {
                result[i] = objects[i].getTypeId();
            }
            this.objectTypeIds = result;
        }
        return objectTypeIds;
    }

    private boolean checkObjects(OptionValues options) {
        assert PointstoOptions.ExtendedAsserts.getValue(options);

        for (int idx = 0; idx < objects.length - 1; idx++) {
            AnalysisObject o0 = objects[idx];
            AnalysisObject o1 = objects[idx + 1];

            assert o0 != null && o1 != null : "Object state must contain non null elements.";

            /* Check that the objects array are sorted by type. */
            assert (o0.type().equals(o1.type()) && o0.getId() < o1.getId()) || o0.type().getId() < o1.type().getId() : "Analysis objects must be sorted by type ID and ID.";

            /* Check that the bit is set for the types. */
            assert typesBitSet.get(o0.type().getId());
            assert typesBitSet.get(o1.type().getId());
        }

        return true;
    }

    /** Get the number of objects. */
    @Override
    public int objectsCount() {
        return objects.length;
    }

    /** Returns the objects as an array. */
    @Override
    public final AnalysisObject[] objects() {
        return objects;
    }

    @Override
    public boolean hasExactTypes(BitSet inputTypesBitSet) {
        return typesBitSet.equals(inputTypesBitSet);
    }

    @Override
    public AnalysisType exactType() {
        return typesCount == 1 ? objects[0].type() : null;
    }

    @Override
    public int typesCount() {
        return typesCount;
    }

    /** Get the type of the first object group. */
    AnalysisType firstType() {
        return objects[0].type();
    }

    /** Get the type of the last object group. */
    AnalysisType lastType() {
        return objects[objects.length - 1].type();
    }

    /**
     * It iterates over the types bit set and gets the types using
     * {@link AnalysisUniverse#getType(int)}. The types are iterated in ascending order of their IDs
     * by way of bit set iteration.
     */
    @Override
    public Iterator<AnalysisType> typesIterator() {
        return new Iterator<AnalysisType>() {

            /** Initialize to the index of the first set bit. */
            private int currentTypeId = typesBitSet.nextSetBit(0);

            @Override
            public boolean hasNext() {
                return currentTypeId >= 0;
            }

            @Override
            public AnalysisType next() {
                AnalysisType next = bigbang.getUniverse().getType(currentTypeId);
                currentTypeId = typesBitSet.nextSetBit(currentTypeId + 1);
                return next;
            }
        };
    }

    @Override
    public boolean containsType(AnalysisType exactType) {
        return typesBitSet.get(exactType.getId());
    }

    @Override
    public TypeState exactTypeState(BigBang bb, AnalysisType exactType) {
        if (containsType(exactType)) {
            AnalysisObject[] resultObjects = objectsArray(exactType);
            return new SingleTypeState(bb, canBeNull, bb.analysisPolicy().makePoperties(bb, resultObjects), resultObjects);
        } else {
            return EmptyTypeState.SINGLETON;
        }
    }

    @Override
    public TypeState forCanBeNull(BigBang bb, boolean resultCanBeNull) {
        if (resultCanBeNull == this.canBeNull()) {
            return this;
        } else {
            /* Just flip the canBeNull flag and copy the rest of the values from this. */
            return new MultiTypeState(bb, resultCanBeNull, this);
        }
    }

    /**
     * A [left, right) range (interval), i.e., left is inclusive, right is exclusive. The values in
     * the defined range can be naturally iterated using
     * <code> for(int i = left; i < right; i++) {} </code>. A range with {@code left} equal to
     * {@code right} is an empty range.
     */
    public static class Range {

        static final Range EMPTY = new Range(0, 0);

        protected static Range range(int up, int low) {
            return new Range(up, low);
        }

        /** An inclusive left end point. */
        final int left;
        /** An exclusive right end point. */
        protected final int right;

        Range(int left, int right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public String toString() {
            return "[" + left + ", " + right + ")";
        }
    }

    Range findTypeRange(AnalysisType type) {

        /* First do a quick check using the types bit set. */
        if (!containsType(type)) {
            /* There is no object of the inquired type in this array. */
            return Range.EMPTY;
        }

        /* Then binary search to find some object of the inquired type. */
        int someIdx = Arrays.binarySearch(objects, type.getContextInsensitiveAnalysisObject(), AnalysisObject.objectsTypeComparator);
        assert someIdx >= 0 : "The inquired type must be in the array.";

        int firstIdx = someIdx;
        while (firstIdx >= 0 && objects[firstIdx].getTypeId() == type.getId()) {
            /* Find the first index by walking down from the found index until the type changes. */
            firstIdx--;
        }
        int lastIdx = someIdx;
        while (lastIdx < objects.length && objects[lastIdx].getTypeId() == type.getId()) {
            /* Find the last index by walking up from the found index until the type changes. . */
            lastIdx++;
        }

        /*
         * Range.left is inclusive, so we must increment firstIdx. Range.right is exclusive so we
         * just use lastIdx.
         */
        return Range.range(firstIdx + 1, lastIdx);
    }

    @Override
    public AnalysisObject[] objectsArray(AnalysisType type) {
        Range typeRange = findTypeRange(type);
        return Arrays.copyOfRange(objects, typeRange.left, typeRange.right);
    }

    AnalysisObject[] objectsArray(Range typeRange) {
        return Arrays.copyOfRange(objects, typeRange.left, typeRange.right);
    }

    @Override
    public Iterator<AnalysisObject> objectsIterator(AnalysisType exactType) {
        return new Iterator<AnalysisObject>() {
            private Range typeRange = findTypeRange(exactType);
            private int idx = typeRange.left;

            @Override
            public boolean hasNext() {
                return idx < typeRange.right;
            }

            @Override
            public AnalysisObject next() {
                return objects[idx++];
            }
        };
    }

    @Override
    public final boolean canBeNull() {
        return canBeNull;
    }

    /** Note that the objects of this type state have been merged. */
    @Override
    public void noteMerge(BigBang bb) {
        assert bb.analysisPolicy().isMergingEnabled();

        if (!merged) {
            for (AnalysisObject obj : objects) {
                obj.noteMerge(bb);
            }
            merged = true;
        }
    }

    @Override
    public boolean closeToAllInstantiated(BigBang bb) {
        if (typesCount > 200 && bb != null) {
            MultiTypeState allInstState = (MultiTypeState) bb.getAllInstantiatedTypeFlow().getState();
            return typesCount * 100L / allInstState.typesCount > 75;
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Arrays.hashCode(objects);
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
                        this.typesCount == that.typesCount && this.typesBitSet.equals(that.typesBitSet) &&
                        Arrays.equals(this.objects, that.objects);
    }

    @Override
    public String toString() {
        return "MTypeMObject<" + objects.length + ":" + (canBeNull ? "null," : "") + Arrays.toString(objects) + ">";
    }
}
