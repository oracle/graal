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

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.MultiTypeState.Range;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.BitArrayUtils;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;

public abstract class TypeState {

    /** TypeState id is only be used for statistics. */
    private int id = -1;

    /** A bit array of properties for this type state. */
    protected final int properties;

    public TypeState(int properties) {
        this.properties = properties;
    }

    /* Instance methods. */

    public int getProperties() {
        return properties;
    }

    /* Types accessing methods. */

    /** Returns true if the type state contains exact the same types as the bit set. */
    public abstract boolean hasExactTypes(BitSet typesBitSet);

    /** Get the number of types. */
    public abstract int typesCount();

    /**
     * If this type state has a single, exact type it returns that type, otherwise it returns null.
     */
    public abstract AnalysisType exactType();

    /** Provides an iterator over the types. */
    protected abstract Iterator<AnalysisType> typesIterator();

    /** Provides an iterable for the types for easy "for-each loop" iteration. */
    public Iterable<AnalysisType> types() {
        return this::typesIterator;
    }

    /** Provides a stream for the types. */
    public Stream<AnalysisType> typesStream() {
        return StreamSupport.stream(types().spliterator(), false);
    }

    /** Returns true if this type state contains the type, otherwise it returns false. */
    public abstract boolean containsType(AnalysisType exactType);

    /* Objects accessing methods. */

    /** Get the number of objects. */
    public abstract int objectsCount();

    /** Returns the objects as an array. */
    public abstract AnalysisObject[] objects();

    /** Returns the objects corresponding to the type. It copies those objects to a new array. */
    public abstract AnalysisObject[] objectsArray(AnalysisType type);

    /**
     * Provides an iterator for the objects corresponding to the type. The objects are returned from
     * the internal objects array and are not materialized to a different data structure.
     */
    protected abstract Iterator<AnalysisObject> objectsIterator(AnalysisType type);

    /**
     * Provides an iterable for the objects corresponding to the type. The objects are returned from
     * the internal objects array and are not materialized to a different data structure.
     */
    public Iterable<AnalysisObject> objects(AnalysisType type) {
        return () -> objectsIterator(type);
    }

    /** Provides a stream for the objects. */
    public Stream<AnalysisObject> objectsStream() {
        return Arrays.stream(objects());
    }

    /** Returns true if this type state contains the object, otherwise it returns false. */
    public boolean containsObject(AnalysisObject object) {
        /* AnalysisObject implements Comparable and the objects array is always sorted by ID. */
        return containsType(object.type()) && Arrays.binarySearch(objects(), object) >= 0;
    }

    /**
     * Provides a special iterator for the type state. It iterates over analysis types and objects
     * in tandem doing a single pass over the objects array. See {@link TypesObjectsIterator} for a
     * complete explanation.
     */
    public TypesObjectsIterator getTypesObjectsIterator() {
        return new TypesObjectsIterator(this);
    }

    /**
     * This is a special iterator for the type state. It iterates over analysis types and objects in
     * tandem doing a single pass over the objects array. It relies on the fact that the types and
     * objects are sorted by ID. It is meant for situations where the types need some pre-processing
     * or checking before processing their respective objects, e.g., as in virtual method resolution
     * for InvokeTypeFlow. It those situations it avoids iterating over the types first and then
     * searching for the range of objects corresponding to that type. When only objects, or only
     * types, or only objects of a certain type need to be iterated use the other provided
     * iterators. A correct use of this iterator is as follows:
     *
     * <code>
     * TypesObjectsIterator toi = state.getTypesObjectsIterator();
     *
     * while(toi.hasNextType()) {
     *      AnalysisType t = toi.nextType();
     *      // use type here
     *
     *      while(toi.hasNextObject(t)) {
     *          AnalysisObject o = toi.nextObject(t);
     *          // use object here
     *      }
     * }
     * </code>
     */
    public static class TypesObjectsIterator {

        private final TypeState state;
        private int typeIdx = 0;
        private int objectIdx = 0;

        public TypesObjectsIterator(TypeState state) {
            this.state = state;
        }

        /**
         * Returns true if there is a next type in the objects array, i.e., there are objects of a
         * type other than the current type.
         */
        public boolean hasNextType() {
            return typeIdx < state.typesCount();
        }

        /** Returns true if there are more objects of the current type. */
        public boolean hasNextObject(AnalysisType type) {
            return objectIdx < state.objects().length && state.objects()[objectIdx].getTypeId() == type.getId();
        }

        /** Gets the next type. */
        public AnalysisType nextType() {
            /* Check that there is a next type. */
            assert hasNextType();
            /* Increment the type index. */
            typeIdx++;
            /* Return the type at the 'objectIdx. */
            return state.objects()[objectIdx].type();
        }

        /** Gets the next object. */
        public AnalysisObject nextObject(AnalysisType type) {
            /* Check that there is a next object of the desired type. */
            assert hasNextObject(type);
            /* Return the next object and increment objectIdx. */
            return state.objects()[objectIdx++];
        }
    }

    public boolean isAllocation() {
        return objects().length == 1 && objects()[0].isAllocationContextSensitiveObject();
    }

    public boolean isConstant() {
        return objects().length == 1 && objects()[0].isConstantContextSensitiveObject();
    }

    public boolean isEmpty() {
        return this == EmptyTypeState.SINGLETON;
    }

    public boolean isUnknown() {
        return this == UnknownTypeState.SINGLETON;
    }

    public boolean isSingleTypeState() {
        return this.typesCount() == 1;
    }

    public boolean isMultiTypeState() {
        return this instanceof MultiTypeState;
    }

    public boolean isNull() {
        return this == NullTypeState.SINGLETON;
    }

    public abstract boolean canBeNull();

    /** Note that the objects of this type state have been merged. */
    public void noteMerge(@SuppressWarnings("unused") BigBang bb) {
    }

    /**
     * This method is needed for accessing the SingleTypeState associated with an specific type of a
     * MutiTypeState, e.g. for transferring the state from a virtual invoke type flow to the formal
     * receiver flow of a specific callee resolved on the specified type.
     */
    public abstract TypeState exactTypeState(BigBang bb, AnalysisType exactType);

    public boolean verifyDeclaredType(AnalysisType declaredType) {
        if (!this.isUnknown() && declaredType != null) {
            for (AnalysisType e : types()) {
                if (!declaredType.isAssignableFrom(e)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The {@link MultiTypeState} overrides this method and provides the proper test. All the other
     * type states have only 0 or 1 types.
     */
    public boolean closeToAllInstantiated(@SuppressWarnings("unused") BigBang bb) {
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public abstract boolean equals(Object o);

    public int getId(BigBang bb) {
        assert bb.reportAnalysisStatistics() : "TypeState id should only be used for statistics.";
        return id;
    }

    public void setId(BigBang bb, int id) {
        assert bb.reportAnalysisStatistics() : "TypeState id should only be used for statistics.";
        this.id = id;
    }

    /* Static methods. */

    public static TypeState forEmpty() {
        return EmptyTypeState.SINGLETON;
    }

    public static TypeState forNull() {
        return NullTypeState.SINGLETON;
    }

    public static TypeState forUnknown() {
        return UnknownTypeState.SINGLETON;
    }

    /** Wraps an analysis object into a non-null type state. */
    public static TypeState forNonNullObject(BigBang bb, AnalysisObject object) {
        return new SingleTypeState(bb, false, bb.analysisPolicy().makePoperties(bb, object), object);
    }

    /** Wraps the analysis object corresponding to a JavaConstant into a non-null type state. */
    public static TypeState forConstant(BigBang bb, JavaConstant constant, AnalysisType exactType) {
        assert !constant.isNull();
        assert exactType.isArray() || (exactType.isInstanceClass() && !Modifier.isAbstract(exactType.getModifiers())) : exactType;

        AnalysisObject constantObject = bb.analysisPolicy().createConstantObject(bb, constant, exactType);
        return forNonNullObject(bb, constantObject);
    }

    /** Wraps the analysis object corresponding to an allocation site into a non-null type state. */
    public static TypeState forAllocation(BigBang bb, BytecodeLocation allocationLabel, AnalysisType exactType) {
        return forAllocation(bb, allocationLabel, exactType, bb.contextPolicy().emptyContext());
    }

    /**
     * Wraps the analysis object corresponding to an allocation site for a given context into a
     * non-null type state.
     */
    public static TypeState forAllocation(BigBang bb, BytecodeLocation allocationSite, AnalysisType objectType, AnalysisContext allocationContext) {
        assert objectType.isArray() || (objectType.isInstanceClass() && !Modifier.isAbstract(objectType.getModifiers())) : objectType;

        AnalysisObject allocationObject = bb.analysisPolicy().createHeapObject(bb, objectType, allocationSite, allocationContext);
        return forNonNullObject(bb, allocationObject);
    }

    /**
     * Wraps the analysis object corresponding to a clone site for a given context into a non-null
     * type state.
     */
    public static TypeState forClone(BigBang bb, BytecodeLocation cloneSite, AnalysisType type, AnalysisContext allocationContext) {
        return forAllocation(bb, cloneSite, type, allocationContext);
    }

    public static TypeState forExactType(BigBang bb, AnalysisType exactType, boolean canBeNull) {
        return forExactType(bb, exactType.getContextInsensitiveAnalysisObject(), canBeNull);
    }

    public static TypeState forExactType(BigBang bb, AnalysisObject object, boolean canBeNull) {
        assert object.type().isArray() || (object.type().isInstanceClass() && !Modifier.isAbstract(object.type().getModifiers())) : object.type();
        return new SingleTypeState(bb, canBeNull, bb.analysisPolicy().makePoperties(bb, object), object);
    }

    public static TypeState forExactTypes(BigBang bb, BitSet exactTypes, boolean canBeNull) {
        int numTypes = exactTypes.cardinality();
        if (numTypes == 0) {
            return forEmpty().forCanBeNull(bb, canBeNull);
        } else if (numTypes == 1) {
            AnalysisType type = bb.getUniverse().getType(exactTypes.nextSetBit(0));
            AnalysisObject analysisObject = type.getContextInsensitiveAnalysisObject();
            return new SingleTypeState(bb, canBeNull, bb.analysisPolicy().makePoperties(bb, analysisObject), analysisObject);
        } else {
            AnalysisObject[] objectsArray = new AnalysisObject[numTypes];
            int idx = 0;
            for (int id = exactTypes.nextSetBit(0); id >= 0; id = exactTypes.nextSetBit(id + 1)) {
                objectsArray[idx] = bb.getUniverse().getType(id).getContextInsensitiveAnalysisObject();
                idx++;
            }
            assert idx == objectsArray.length;
            /*
             * For types use the already created bit set, but clone it since it can change outside.
             */
            BitSet typesBitSet = (BitSet) exactTypes.clone();
            int properties = bb.analysisPolicy().makePoperties(bb, objectsArray);
            return new MultiTypeState(bb, canBeNull, properties, typesBitSet, objectsArray);
        }
    }

    /**
     * Simplifies a type state by replacing all context sensitive objects with context insensitive
     * objects.
     */
    public static TypeState forContextInsensitiveTypeState(BigBang bb, TypeState state) {
        if (!PointstoOptions.AllocationSiteSensitiveHeap.getValue(bb.getOptions()) ||
                        state.isEmpty() || state.isNull() || state.isUnknown()) {
            /* The type state is already context insensitive. */
            return state;
        } else {
            if (state.isSingleTypeState()) {
                AnalysisType type = state.exactType();
                AnalysisObject analysisObject = type.getContextInsensitiveAnalysisObject();
                return new SingleTypeState(bb, state.canBeNull(), bb.analysisPolicy().makePoperties(bb, analysisObject), analysisObject);
            } else {
                MultiTypeState multiState = (MultiTypeState) state;
                AnalysisObject[] objectsArray = new AnalysisObject[multiState.typesCount()];

                int i = 0;
                for (AnalysisType type : multiState.types()) {
                    objectsArray[i++] = type.getContextInsensitiveAnalysisObject();
                }
                /*
                 * For types use the already created bit set. Since the original type state is
                 * immutable its types bit set cannot change.
                 */

                BitSet typesBitSet = multiState.typesBitSet;
                int properties = bb.analysisPolicy().makePoperties(bb, objectsArray);
                return new MultiTypeState(bb, multiState.canBeNull(), properties, typesBitSet, objectsArray);
            }
        }
    }

    public final TypeState forNonNull(BigBang bb) {
        return forCanBeNull(bb, false);
    }

    protected abstract TypeState forCanBeNull(BigBang bb, boolean stateCanBeNull);

    public static TypeState forUnion(BigBang bb, TypeState s1, TypeState s2) {

        if (s1.isUnknown()) {
            return s1;
        } else if (s2.isUnknown()) {
            return s2;
        } else if (s1.isEmpty()) {
            return s2;
        } else if (s1.isNull()) {
            return s2.forCanBeNull(bb, true);
        } else if (s2.isEmpty()) {
            return s1;
        } else if (s2.isNull()) {
            return s1.forCanBeNull(bb, true);
        } else if (s1 instanceof SingleTypeState && s2 instanceof SingleTypeState) {
            return doUnion(bb, (SingleTypeState) s1, (SingleTypeState) s2);
        } else if (s1 instanceof SingleTypeState && s2 instanceof MultiTypeState) {
            return doUnion(bb, (MultiTypeState) s2, (SingleTypeState) s1);
        } else if (s1 instanceof MultiTypeState && s2 instanceof SingleTypeState) {
            return doUnion(bb, (MultiTypeState) s1, (SingleTypeState) s2);
        } else {
            assert s1 instanceof MultiTypeState && s2 instanceof MultiTypeState;
            if (s1.objectsCount() >= s2.objectsCount()) {
                return doUnion(bb, (MultiTypeState) s1, (MultiTypeState) s2);
            } else {
                return doUnion(bb, (MultiTypeState) s2, (MultiTypeState) s1);
            }
        }
    }

    public static TypeState forIntersection(BigBang bb, TypeState s1, TypeState s2) {
        if (s1.isUnknown() || s2.isUnknown()) {
            throw AnalysisError.shouldNotReachHere("Intersection with unknown type state is undefined.");
        }

        if (s1.isEmpty()) {
            return s1;
        } else if (s1.isNull()) {
            return s1.forCanBeNull(bb, s2.canBeNull());
        } else if (s2.isEmpty()) {
            return s2;
        } else if (s2.isNull()) {
            return s2.forCanBeNull(bb, s1.canBeNull());
        } else if (s1 instanceof SingleTypeState && s2 instanceof SingleTypeState) {
            return doIntersection(bb, (SingleTypeState) s1, (SingleTypeState) s2);
        } else if (s1 instanceof SingleTypeState && s2 instanceof MultiTypeState) {
            return doIntersection(bb, (SingleTypeState) s1, (MultiTypeState) s2);
        } else if (s1 instanceof MultiTypeState && s2 instanceof SingleTypeState) {
            return doIntersection(bb, (MultiTypeState) s1, (SingleTypeState) s2);
        } else {
            assert s1 instanceof MultiTypeState && s2 instanceof MultiTypeState;
            return doIntersection(bb, (MultiTypeState) s1, (MultiTypeState) s2);
        }
    }

    public static TypeState forSubtraction(BigBang bb, TypeState s1, TypeState s2) {
        if (s1.isUnknown() || s2.isUnknown()) {
            throw AnalysisError.shouldNotReachHere("Subtraction of unknown type state is undefined");
        }

        if (s1.isEmpty()) {
            return s1;
        } else if (s1.isNull()) {
            return s1.forCanBeNull(bb, !s2.canBeNull());
        } else if (s2.isEmpty()) {
            return s1;
        } else if (s2.isNull()) {
            return s1.forCanBeNull(bb, false);
        } else if (s1 instanceof SingleTypeState && s2 instanceof SingleTypeState) {
            return doSubtraction(bb, (SingleTypeState) s1, (SingleTypeState) s2);
        } else if (s1 instanceof SingleTypeState && s2 instanceof MultiTypeState) {
            return doSubtraction(bb, (SingleTypeState) s1, (MultiTypeState) s2);
        } else if (s1 instanceof MultiTypeState && s2 instanceof SingleTypeState) {
            return doSubtraction(bb, (MultiTypeState) s1, (SingleTypeState) s2);
        } else {
            assert s1 instanceof MultiTypeState && s2 instanceof MultiTypeState;
            return doSubtraction(bb, (MultiTypeState) s1, (MultiTypeState) s2);
        }
    }

    /* Implementation of union. */

    private static TypeState doUnion(BigBang bb, SingleTypeState s1, SingleTypeState s2) {
        if (s1.equals(s2)) {
            return s1;
        }

        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();
        if (s1.exactType().equals(s2.exactType())) {

            /* The inputs have the same type, so the result is a SingleTypeState. */

            /* Create the resulting objects array. */
            AnalysisObject[] resultObjects = TypeStateUtils.union(bb, s1.objects, s2.objects);

            /* Check if any of the arrays contains the other. */
            if (resultObjects == s1.objects) {
                return s1.forCanBeNull(bb, resultCanBeNull);
            } else if (resultObjects == s2.objects) {
                return s2.forCanBeNull(bb, resultCanBeNull);
            }

            /* Due to the test above the union set cannot be equal to any of the two arrays. */
            assert !PointstoOptions.ExtendedAsserts.getValue(bb.getOptions()) || !Arrays.equals(resultObjects, s1.objects) && !Arrays.equals(resultObjects, s2.objects);

            /* Create the resulting exact type state. */
            SingleTypeState result = new SingleTypeState(bb, resultCanBeNull, bb.analysisPolicy().makePopertiesForUnion(s1, s2), resultObjects);
            assert !s1.equals(result) && !s2.equals(result);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        } else {
            /* The inputs have different types, so the result is a MultiTypeState. */
            AnalysisObject[] resultObjects;
            if (s1.exactType().getId() < s2.exactType().getId()) {
                resultObjects = TypeStateUtils.concat(s1.objects, s2.objects);
            } else {
                resultObjects = TypeStateUtils.concat(s2.objects, s1.objects);
            }

            /* We know the types, construct the types bit set without walking the objects. */
            BitSet typesBitSet = new BitSet();
            typesBitSet.set(s1.exactType().getId());
            typesBitSet.set(s2.exactType().getId());

            int properties = bb.analysisPolicy().makePopertiesForUnion(s1, s2);

            TypeState result = new MultiTypeState(bb, resultCanBeNull, properties, typesBitSet, resultObjects);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        }
    }

    private static TypeState doUnion(BigBang bb, MultiTypeState s1, SingleTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();

        AnalysisObject[] so1 = s1.objects;
        AnalysisObject[] so2 = s2.objects;
        if (so2.length == 1 && s1.containsObject(so2[0])) {
            /*
             * Speculate that s2 has a single object and s1 already contains that object. This
             * happens often during object scanning where we repeatedly add the scanned constants to
             * field or array elements flows. The binary search executed by containsObject should be
             * faster than the linear search bellow.
             */
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        if (s1.containsType(s2.exactType())) {
            /* Objects of the same type as s2 are contained in s1. */

            /* Get the range of objects in s1 corresponding to the type of s2. */
            Range typeRange = s1.findTypeRange(s2.exactType());
            /* Get the slice of objects in s1 corresponding to the type of s2. */
            AnalysisObject[] s1ObjectsSlice = s1.objectsArray(typeRange);

            /* Create the resulting objects array. */
            AnalysisObject[] unionObjects = TypeStateUtils.union(bb, s1ObjectsSlice, so2);

            /* Check if s1 contains s2's objects for this type. */
            if (unionObjects == s1ObjectsSlice) {
                return s1.forCanBeNull(bb, resultCanBeNull);
            }

            /*
             * Due to the test above and to the fact that TypeStateUtils.union checks if one array
             * contains the other the union set cannot be equal to s1's objects slice.
             */
            assert !PointstoOptions.ExtendedAsserts.getValue(bb.getOptions()) || !Arrays.equals(unionObjects, s1ObjectsSlice);

            /*
             * Replace the s1 objects slice of the same type as s2 with the union objects and create
             * a new state.
             */
            int resultSize = so1.length + unionObjects.length - s1ObjectsSlice.length;
            AnalysisObject[] resultObjects = new AnalysisObject[resultSize];

            System.arraycopy(so1, 0, resultObjects, 0, typeRange.left);
            System.arraycopy(unionObjects, 0, resultObjects, typeRange.left, unionObjects.length);
            System.arraycopy(so1, typeRange.right, resultObjects, typeRange.left + unionObjects.length, so1.length - typeRange.right);

            /* The types bit set of the result and s1 are the same. */

            int properties = bb.analysisPolicy().makePopertiesForUnion(s1, s2);

            MultiTypeState result = new MultiTypeState(bb, resultCanBeNull, properties, s1.typesBitSet, resultObjects);
            assert !result.equals(s1);
            /*
             * No need to check the result size against the all-instantiated since the type count
             * didn't change.
             */
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        } else {
            AnalysisObject[] resultObjects;
            if (s2.exactType().getId() < s1.firstType().getId()) {
                resultObjects = TypeStateUtils.concat(so2, so1);
            } else if (s2.exactType().getId() > s1.lastType().getId()) {
                resultObjects = TypeStateUtils.concat(so1, so2);
            } else {

                /* Find insertion point within the s1.objects. */
                int idx1 = 0;
                while (idx1 < so1.length && so1[idx1].getTypeId() < s2.exactType().getId()) {
                    idx1++;
                }

                /* Create the resulting objects array and insert the s2 objects. */
                resultObjects = new AnalysisObject[so1.length + so2.length];

                System.arraycopy(so1, 0, resultObjects, 0, idx1);
                System.arraycopy(so2, 0, resultObjects, idx1, so2.length);
                System.arraycopy(so1, idx1, resultObjects, idx1 + so2.length, so1.length - idx1);
            }

            /* Create the types bit set by adding the s2 type to avoid walking the objects. */
            BitSet typesBitSet = TypeStateUtils.set(s1.typesBitSet, s2.exactType().getId());
            int properties = bb.analysisPolicy().makePopertiesForUnion(s1, s2);

            MultiTypeState result = new MultiTypeState(bb, resultCanBeNull, properties, typesBitSet, resultObjects);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        }
    }

    private static TypeState doUnion(BigBang bb, MultiTypeState s1, MultiTypeState s2) {
        assert s1.objectsCount() >= s2.objectsCount() : "Union is commutative, must call it with s1 being the bigger state";
        boolean resultCanBeNull = s1.canBeNull() || s2.canBeNull();

        /*
         * No need for a deep equality check (which would need to iterate the arrays), since the
         * speculation logic below is doing that anyway.
         */
        if (s1.objects == s2.objects) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        return doUnion0(bb, s1, s2, resultCanBeNull);
    }

    private static TypeState doUnion0(BigBang bb, MultiTypeState s1, MultiTypeState s2, boolean resultCanBeNull) {

        /* Speculate that s1 and s2 are distinct sets. */

        if (s1.lastType().getId() < s2.firstType().getId()) {
            /* Speculate that objects in s2 follow after objects in s1. */

            /* Concatenate the objects. */
            AnalysisObject[] resultObjects = TypeStateUtils.concat(s1.objects, s2.objects);

            /* Logical OR the type bit sets. */
            BitSet resultTypesBitSet = TypeStateUtils.or(s1.typesBitSet, s2.typesBitSet);
            int properties = bb.analysisPolicy().makePopertiesForUnion(s1, s2);

            MultiTypeState result = new MultiTypeState(bb, resultCanBeNull, properties, resultTypesBitSet, resultObjects);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;

        } else if (s2.lastType().getId() < s1.firstType().getId()) {
            /* Speculate that objects in s1 follow after objects in s2. */

            /* Concatenate the objects. */
            AnalysisObject[] resultObjects = TypeStateUtils.concat(s2.objects, s1.objects);

            /* Logical OR the type bit sets. */
            BitSet resultTypesBitSet = TypeStateUtils.or(s1.typesBitSet, s2.typesBitSet);
            int properties = bb.analysisPolicy().makePopertiesForUnion(s1, s2);

            MultiTypeState result = new MultiTypeState(bb, resultCanBeNull, properties, resultTypesBitSet, resultObjects);
            PointsToStats.registerUnionOperation(bb, s1, s2, result);
            return result;
        }

        return doUnion1(bb, s1, s2, resultCanBeNull);
    }

    private static TypeState doUnion1(BigBang bb, MultiTypeState s1, MultiTypeState s2, boolean resultCanBeNull) {
        if (PointstoOptions.AllocationSiteSensitiveHeap.getValue(bb.getOptions())) {
            return allocationSensitiveSpeculativeUnion1(bb, s1, s2, resultCanBeNull);
        } else {
            return allocationInsensitiveSpeculativeUnion1(bb, s1, s2, resultCanBeNull);
        }
    }

    /**
     * Optimization that gives 1.5-3x in performance for the (typeflow) phase.
     */
    private static TypeState allocationInsensitiveSpeculativeUnion1(BigBang bb, MultiTypeState s1, MultiTypeState s2, boolean resultCanBeNull) {
        if (s1.typesBitSet.length() >= s2.typesBitSet.length()) {
            long[] bits1 = TypeStateUtils.extractBitSetField(s1.typesBitSet);
            long[] bits2 = TypeStateUtils.extractBitSetField(s2.typesBitSet);
            assert s2.typesBitSet.cardinality() == s2.objects.length : "Cardinality and length of objects must match.";

            boolean speculate = true;
            int numberOfWords = Math.min(bits1.length, bits2.length);
            for (int i = 0; i < numberOfWords; i++) {
                /* bits2 is a subset of bits1 */
                if ((bits1[i] & bits2[i]) != bits2[i]) {
                    speculate = false;
                    break;
                }
            }
            if (speculate) {
                return s1.forCanBeNull(bb, resultCanBeNull);
            }
        }
        return doUnion2(bb, s1, s2, resultCanBeNull, 0, 0);
    }

    private static TypeState allocationSensitiveSpeculativeUnion1(BigBang bb, MultiTypeState s1, MultiTypeState s2, boolean resultCanBeNull) {
        int idx1 = 0;
        int idx2 = 0;
        AnalysisPolicy analysisPolicy = bb.analysisPolicy();
        AnalysisObject[] so1 = s1.objects;
        AnalysisObject[] so2 = s2.objects;
        while (idx1 < so1.length && idx2 < so2.length) {
            AnalysisObject o1 = so1[idx1];
            AnalysisObject o2 = so2[idx2];
            if (analysisPolicy.isSummaryObject(o1) && o1.getTypeId() == o2.getTypeId()) {
                idx1++;
                /* Skip over s2 objects of this type while marking them as merged. */
                while (idx2 < s2.objectsCount() && so2[idx2].getTypeId() == o1.getTypeId()) {
                    analysisPolicy.noteMerge(bb, so2[idx2]);
                    idx2++;
                }
            } else if (o1.getId() < o2.getId()) {
                idx1++;
            } else if (o1.getId() == o2.getId()) {
                /* If the objects are equal continue. */
                idx1++;
                idx2++;
            } else {
                /* Our speculation failed. */
                break;
            }

            if (idx2 == so2.length) {
                return s1.forCanBeNull(bb, resultCanBeNull);
            }
        }
        return doUnion2(bb, s1, s2, resultCanBeNull, idx1, idx2);
    }

    private static ThreadLocal<UnsafeArrayListClosable<AnalysisObject>> doUnion2TL = new ThreadLocal<>();
    private static ThreadLocal<UnsafeArrayListClosable<AnalysisObject>> doUnion2ObjectsTL = new ThreadLocal<>();

    private static TypeState doUnion2(BigBang bb, MultiTypeState s1, MultiTypeState s2, boolean resultCanBeNull, int startId1, int startId2) {
        try (UnsafeArrayListClosable<AnalysisObject> resultObjectsClosable = getTLArrayList(doUnion2TL, s1.objects.length + s2.objects.length)) {
            UnsafeArrayList<AnalysisObject> resultObjects = resultObjectsClosable.list;
            /* Add the beginning of the s1 list that we already walked above. */
            AnalysisObject[] objects = s1.objects;
            resultObjects.addAll(objects, 0, startId1);

            int idx1 = startId1;
            int idx2 = startId2;

            /* Create the union of the overlapping sections of the s1 and s2. */
            try (UnsafeArrayListClosable<AnalysisObject> tlUnionObjectsClosable = getTLArrayList(doUnion2ObjectsTL, s1.objects.length + s2.objects.length)) {
                UnsafeArrayList<AnalysisObject> unionObjects = tlUnionObjectsClosable.list;

                AnalysisObject[] so1 = s1.objects;
                AnalysisObject[] so2 = s2.objects;
                AnalysisPolicy analysisPolicy = bb.analysisPolicy();
                while (idx1 < so1.length && idx2 < so2.length) {
                    AnalysisObject o1 = so1[idx1];
                    AnalysisObject o2 = so2[idx2];
                    int t1 = o1.getTypeId();
                    int t2 = o2.getTypeId();
                    if (analysisPolicy.isSummaryObject(o1) && t1 == t2) {
                        unionObjects.add(o1);
                        /* Skip over s2 objects of this type while marking them as merged. */
                        while (idx2 < so2.length && t1 == so2[idx2].getTypeId()) {
                            analysisPolicy.noteMerge(bb, so2[idx2]);
                            idx2++;
                        }
                        idx1++;
                    } else if (analysisPolicy.isSummaryObject(o2) && t1 == t2) {
                        unionObjects.add(o2);
                        /* Skip over s1 objects of this type while marking them as merged. */
                        while (idx1 < so1.length && so1[idx1].getTypeId() == t2) {
                            analysisPolicy.noteMerge(bb, so1[idx1]);
                            idx1++;
                        }
                        idx2++;
                    } else if (o1.getId() < o2.getId()) {
                        unionObjects.add(o1);
                        idx1++;
                    } else if (o1.getId() > o2.getId()) {
                        unionObjects.add(o2);
                        idx2++;
                    } else {
                        assert o1.equals(o2);
                        unionObjects.add(o1);
                        idx1++;
                        idx2++;
                    }
                }

                /*
                 * Check if the union of objects of a type in the overlapping section reached the
                 * limit. The limit, bb.options().maxObjectSetSize(), has a minimum value of 1.
                 */
                if (PointstoOptions.LimitObjectArrayLength.getValue(bb.getOptions()) && unionObjects.size() > PointstoOptions.MaxObjectSetSize.getValue(bb.getOptions())) {
                    int idxStart = 0;
                    int idxEnd = 0;
                    while (idxEnd < unionObjects.size()) {
                        AnalysisObject oStart = unionObjects.get(idxStart);

                        /* While types are equal and the end is not reached, advance idxEnd. */
                        while (idxEnd < unionObjects.size() && oStart.equals(unionObjects.get(idxEnd))) {
                            idxEnd = idxEnd + 1;
                        }
                        /*
                         * Process the type change or, if idxEnd reached the end, process the last
                         * stride
                         */
                        int size = idxEnd - idxStart;
                        if (size > PointstoOptions.MaxObjectSetSize.getValue(bb.getOptions())) {
                            /*
                             * Object count exceeds the limit. Mark the objects in the stride as
                             * merged.
                             */
                            for (int i = idxStart; i < idxEnd; i += 1) {
                                bb.analysisPolicy().noteMerge(bb, unionObjects.get(i));
                            }
                            /* Add the context insensitive object in the result list. */
                            resultObjects.add(oStart.type().getContextInsensitiveAnalysisObject());
                        } else {
                            /* Object count is within the limit, add them to the result. */
                            resultObjects.addAll(unionObjects.elementData, idxStart, idxEnd);
                        }
                        idxStart = idxEnd;
                    }

                } else {
                    resultObjects.addAll(unionObjects.elementData, 0, unionObjects.size);
                }
            }

            /*
             * Add the leftover objects in the result list.
             *
             * Arrays.asList(a).subList(from, to) first creates a list wrapper over the array then
             * it creates a view of a portion of the list, thus it only allocates the list and
             * sub-list wrappers. Then ArrayList.addAll() calls System.arraycopy() which should be
             * more efficient than copying one element at a time.
             */
            if (idx1 < s1.objects.length) {
                resultObjects.addAll(s1.objects, idx1, s1.objects.length);
            } else if (idx2 < s2.objects.length) {
                resultObjects.addAll(s2.objects, idx2, s2.objects.length);
            }

            assert resultObjects.size() > 1 : "The result state of a (Multi U Multi) operation must have at least 2 objects";

            /* Logical OR the type bit sets. */
            BitSet resultTypesBitSet = TypeStateUtils.or(s1.typesBitSet, s2.typesBitSet);
            int properties = bb.analysisPolicy().makePopertiesForUnion(s1, s2);

            MultiTypeState result = new MultiTypeState(bb, resultCanBeNull, properties, resultTypesBitSet, resultObjects.copyToArray(new AnalysisObject[resultObjects.size()]));
            assert !result.equals(s1) : "speculation code should prevent this case";

            /* The result can be equal to s2 only if s1 and s2 have the same number of types. */
            if (s1.typesCount() == s2.typesCount() && result.equals(s2)) {
                return s2.forCanBeNull(bb, resultCanBeNull);
            }

            PointsToStats.registerUnionOperation(bb, s1, s2, result);

            return result;
        }
    }

    /*
     * Implementation of intersection.
     *
     * The implementation of intersection is specific to our current use case, i.e., it is not a
     * general set intersection implementation. The limitation, checked by the assertions bellow,
     * refers to the fact that when we use intersection we only care about selecting all the objects
     * of a certain type or types, e.g., for filtering. We don't currently have a situation where we
     * only want to select a subset of objects of a type. In our use the types whose objects need to
     * be selected are always specified in s2 through their context insensitive objects, thus s2
     * must only contain context insensitive objects.
     */
    private static TypeState doIntersection(BigBang bb, SingleTypeState s1, SingleTypeState s2) {
        assert s2.objects.length == 1 && s2.objects[0].isContextInsensitiveObject() : "Current implementation limitation.";

        boolean resultCanBeNull = s1.canBeNull() && s2.canBeNull();

        TypeState result;
        if (s1.exactType().equals(s2.exactType())) {
            /* The inputs have the same type, the result will be s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        } else {
            /* The inputs have different types then the result is empty or null. */
            result = TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        return result;
    }

    private static TypeState doIntersection(BigBang bb, SingleTypeState s1, MultiTypeState s2) {
        assert !PointstoOptions.ExtendedAsserts.getValue(bb.getOptions()) || TypeStateUtils.isContextInsensitiveTypeState(s2) : "Current implementation limitation.";

        boolean resultCanBeNull = s1.canBeNull() && s2.canBeNull();

        if (s2.containsType(s1.exactType())) {
            AnalysisObject[] s2Objects = s2.objectsArray(s1.exactType());
            /* See comment above for the limitation explanation. */
            assert s2Objects.length == 1 && s2Objects[0].isContextInsensitiveObject() : "Current implementation limitation.";
            return s1.forCanBeNull(bb, resultCanBeNull);
        } else {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }
    }

    private static TypeState doIntersection(BigBang bb, MultiTypeState s1, SingleTypeState s2) {
        /* See comment above for the limitation explanation. */
        assert s2.objects.length == 1 && s2.objects[0].isContextInsensitiveObject() : "Current implementation limitation.";

        boolean resultCanBeNull = s1.canBeNull() && s2.canBeNull();
        if (s1.containsType(s2.exactType())) {
            /* The s2's type is contained in s1, so pick all objects of the same type from s1. */
            AnalysisObject[] resultObjects = s1.objectsArray(s2.exactType());
            /* All objects must have the same type. */
            assert TypeStateUtils.holdsSingleTypeState(resultObjects);
            return new SingleTypeState(bb, resultCanBeNull, bb.analysisPolicy().makePoperties(bb, resultObjects), resultObjects);
        } else {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }
    }

    private static TypeState doIntersection(BigBang bb, MultiTypeState s1, MultiTypeState s2) {
        assert !PointstoOptions.ExtendedAsserts.getValue(bb.getOptions()) || TypeStateUtils.isContextInsensitiveTypeState(s2) : "Current implementation limitation.";

        boolean resultCanBeNull = s1.canBeNull() && s2.canBeNull();

        /*
         * No need for a deep equality check (which would need to iterate the arrays), since the
         * speculation logic below is doing that anyway.
         */
        if (s1.objects == s2.objects) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        return doIntersection0(bb, s1, s2, resultCanBeNull);
    }

    private static TypeState doIntersection0(BigBang bb, MultiTypeState s1, MultiTypeState s2, boolean resultCanBeNull) {
        /* Speculate that s1 and s2 have either the same types, or no types in common. */

        if (s1.typesBitSet.equals(s2.typesBitSet)) {
            /* Speculate that s1 and s2 have the same types, i.e., the result is s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        if (!s1.typesBitSet.intersects(s2.typesBitSet)) {
            /* Speculate that s1 and s2 have no types in common, i.e., the result is empty. */
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        return doIntersection1(bb, s1, s2, resultCanBeNull);
    }

    private static TypeState doIntersection1(BigBang bb, MultiTypeState s1, MultiTypeState s2, boolean resultCanBeNull) {
        /*
         * Speculate that s2 contains all types of s1, i.e., the filter is broader than s1, thus the
         * result is s1.
         */

        int idx1 = 0;
        int idx2 = 0;
        AnalysisObject[] so1 = s1.objects;
        AnalysisObject[] so2 = s2.objects;
        while (idx2 < so2.length) {
            AnalysisObject o1 = so1[idx1];
            AnalysisObject o2 = so2[idx2];

            /* See comment above for the limitation explanation. */
            assert o2.isContextInsensitiveObject() : "Current implementation limitation.";

            if (o1.getTypeId() > o2.getTypeId()) {
                /* s2 is behind, advance s2. */
                idx2++;
            } else if (o1.getTypeId() == o2.getTypeId()) {
                /* If the types are equal continue with speculation. */
                while (idx1 < so1.length && so1[idx1].getTypeId() == o2.getTypeId()) {
                    /* Walk over the s1 objects of the same type as o2. */
                    idx1++;
                }
                idx2++;
            } else {
                /* Our speculation failed. */
                break;
            }

            if (idx1 == so1.length) {
                /*
                 * Our speculation succeeded: we walked down the whole s1 list, and all of its types
                 * are included in s2.
                 */

                return s1.forCanBeNull(bb, resultCanBeNull);
            }

        }

        return doIntersection2(bb, s1, s2, resultCanBeNull, idx1, idx2);
    }

    private static ThreadLocal<UnsafeArrayListClosable<AnalysisObject>> intersectionArrayListTL = new ThreadLocal<>();

    private static class UnsafeArrayList<E> {

        static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
        E[] elementData;
        int size;

        UnsafeArrayList(E[] initial) {
            elementData = initial;
        }

        <T> T[] copyToArray(T[] a) {
            System.arraycopy(elementData, 0, a, 0, size);
            return a;
        }

        <T> T[] copyToArray(T[] a, int dstPos) {
            System.arraycopy(elementData, 0, a, dstPos, size);
            return a;
        }

        public <E1 extends E> void addAll(E1[] c, int startIndex, int endIndex) {
            assert startIndex <= endIndex : "start index can't be smaller than the end index.";
            int newElements = endIndex - startIndex;
            ensureCapacity(size() + newElements);
            System.arraycopy(c, startIndex, elementData, size, newElements);
            size += newElements;
        }

        private int size() {
            return size;
        }

        public void add(E e) {
            ensureCapacity(size + 1);
            elementData[size] = e;
            size = size + 1;
        }

        public void clear() {
            for (int i = 0; i < size; i++) {
                elementData[i] = null;
            }

            size = 0;
        }

        public E get(int i) {
            assert i < size && i >= 0;
            return elementData[i];
        }

        private void ensureCapacity(int minCapacity) {
            if (minCapacity - elementData.length > 0) {
                grow(minCapacity);
            }
        }

        private void grow(int minCapacity) {
            int oldCapacity = elementData.length;
            int newCapacity = oldCapacity + (oldCapacity >> 1);
            if (newCapacity - minCapacity < 0) {
                newCapacity = minCapacity;
            }
            if (newCapacity - MAX_ARRAY_SIZE > 0) {
                if (minCapacity < 0) {
                    throw new OutOfMemoryError();
                }
                newCapacity = (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
            }
            elementData = Arrays.copyOf(elementData, newCapacity);
        }

    }

    private static TypeState doIntersection2(BigBang bb, MultiTypeState s1, MultiTypeState s2, boolean resultCanBeNull, int idx1Param, int idx2Param) {

        try (UnsafeArrayListClosable<AnalysisObject> tlArrayClosable = getTLArrayList(intersectionArrayListTL, 256)) {
            UnsafeArrayList<AnalysisObject> resultObjects = tlArrayClosable.list;

            AnalysisObject[] so1 = s1.objects;
            AnalysisObject[] so2 = s2.objects;
            int[] types1 = s1.getObjectTypeIds();
            int[] types2 = s2.getObjectTypeIds();
            int idx1 = idx1Param;
            int idx2 = idx2Param;
            int l1 = so1.length;
            int l2 = so2.length;
            int t1 = types1[idx1];
            int t2 = types2[idx2];
            while (idx1 < l1 && idx2 < l2) {
                assert so2[idx2].isContextInsensitiveObject() : "Current implementation limitation.";
                if (t1 == t2) {
                    assert so1[idx1].type().equals(so2[idx2].type());
                    resultObjects.add(so1[idx1]);
                    t1 = types1[++idx1];
                } else if (t1 < t2) {
                    t1 = types1[++idx1];
                } else if (t1 > t2) {
                    t2 = types2[++idx2];
                }
            }

            int totalLength = idx1Param + resultObjects.size();

            if (totalLength == 0) {
                return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
            } else {
                AnalysisObject[] objects = new AnalysisObject[totalLength];
                /* Copy the recently touched first */
                resultObjects.copyToArray(objects, idx1Param);
                /* Add the beginning of the s1 list that we already walked above. */
                System.arraycopy(s1.objects, 0, objects, 0, idx1Param);

                if (TypeStateUtils.holdsSingleTypeState(objects, objects.length)) {
                    /* Multiple objects of the same type. */
                    return new SingleTypeState(bb, resultCanBeNull, bb.analysisPolicy().makePoperties(bb, objects), objects);
                } else {
                    /* Logical AND the type bit sets. */
                    BitSet resultTypesBitSet = TypeStateUtils.and(s1.typesBitSet, s2.typesBitSet);
                    MultiTypeState result = new MultiTypeState(bb, resultCanBeNull, bb.analysisPolicy().makePoperties(bb, objects), resultTypesBitSet, objects);

                    /*
                     * The result can be equal to s1 if and only if s1 and s2 have the same type
                     * count.
                     */
                    if (s1.typesCount() == s2.typesCount() && result.equals(s1)) {
                        return s1.forCanBeNull(bb, resultCanBeNull);
                    }

                    /*
                     * Don't need to check if the result is close-to-all-instantiated since result
                     * <= s1.
                     */
                    return result;
                }
            }
        }
    }

    private static final class UnsafeArrayListClosable<E> implements AutoCloseable {
        private UnsafeArrayList<E> list;
        private boolean closed = true;

        private UnsafeArrayListClosable(UnsafeArrayList<E> list) {
            this.list = list;
        }

        @Override
        public void close() {
            list.clear();
            closed = true;
        }
    }

    private static UnsafeArrayListClosable<AnalysisObject> getTLArrayList(ThreadLocal<UnsafeArrayListClosable<AnalysisObject>> tl, int initialCapacity) {
        UnsafeArrayListClosable<AnalysisObject> result = tl.get();
        if (result == null) {
            result = new UnsafeArrayListClosable<>(new UnsafeArrayList<>(new AnalysisObject[initialCapacity]));
            tl.set(result);
        }
        if (result.closed) {
            result.closed = false;
            return result;
        } else {
            /*
             * Happens very rarely that the same operation is done recursively. If this happens more
             * often we should introduce a stack of arrays.
             */
            return new UnsafeArrayListClosable<>(new UnsafeArrayList<>(new AnalysisObject[initialCapacity]));
        }
    }

    /*
     * Implementation of subtraction.
     *
     * The implementation of subtraction is specific to our current use case, i.e., it is not a
     * general set subtraction implementation. The limitation, checked by the assertions bellow,
     * refers to the fact that when we use subtraction we only care about eliminating all the
     * objects of a certain type or types, e.g., for filtering. We don't currently have a situation
     * where we only want to remove a subset of objects of a type. In our use the types whose
     * objects need to be eliminated are always specified in s2 through their context insensitive
     * objects, thus s2 must only contain context insensitive objects.
     */
    private static TypeState doSubtraction(BigBang bb, SingleTypeState s1, SingleTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();
        if (s1.exactType().equals(s2.exactType())) {
            /* See comment above for the limitation explanation. */
            assert s2.objects.length == 1 && s2.objects[0].isContextInsensitiveObject() : "Current implementation limitation.";
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }
    }

    private static TypeState doSubtraction(BigBang bb, SingleTypeState s1, MultiTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();
        if (s2.containsType(s1.exactType())) {
            AnalysisObject[] array = s2.objectsArray(s1.exactType());
            /* See comment above for the limitation explanation. */
            assert array.length == 1 && array[0].isContextInsensitiveObject() : "Current implementation limitation.";
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }
    }

    private static TypeState doSubtraction(BigBang bb, MultiTypeState s1, SingleTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();
        if (s1.containsType(s2.exactType())) {
            /* s2 is contained in s1, so remove all objects of the same type from s1. */

            /* See comment above for the limitation explanation. */
            assert s2.objects.length == 1 && s2.objects[0].isContextInsensitiveObject() : "Current implementation limitation.";

            /* Find the range of objects of s2.exactType() in s1. */
            Range typeRange = s1.findTypeRange(s2.exactType());
            int newLength = s1.objects.length - (typeRange.right - typeRange.left);
            AnalysisObject[] resultObjects = new AnalysisObject[newLength];

            /* Copy all the objects in s1 but the ones inside the range to the result list. */
            System.arraycopy(s1.objects, 0, resultObjects, 0, typeRange.left);
            System.arraycopy(s1.objects, typeRange.right, resultObjects, typeRange.left, s1.objects.length - typeRange.right);

            if (resultObjects.length == 1) {
                return new SingleTypeState(bb, resultCanBeNull, bb.analysisPolicy().makePoperties(bb, resultObjects[0]), resultObjects[0]);
            } else if (TypeStateUtils.holdsSingleTypeState(resultObjects)) {
                /* Multiple objects of the same type. */
                return new SingleTypeState(bb, resultCanBeNull, bb.analysisPolicy().makePoperties(bb, resultObjects), resultObjects);
            } else {
                BitSet resultTypesBitSet = TypeStateUtils.clear(s1.typesBitSet, s2.exactType().getId());
                return new MultiTypeState(bb, resultCanBeNull, bb.analysisPolicy().makePoperties(bb, resultObjects), resultTypesBitSet, resultObjects);
            }

        } else {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }
    }

    private static TypeState doSubtraction(BigBang bb, MultiTypeState s1, MultiTypeState s2) {
        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();
        /*
         * No need for a deep equality check (which would need to iterate the arrays), since the
         * speculation logic below is doing that anyway.
         */
        if (s1.objects == s2.objects) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        return doSubtraction0(bb, s1, s2, resultCanBeNull);
    }

    private static TypeState doSubtraction0(BigBang bb, MultiTypeState s1, MultiTypeState s2, boolean resultCanBeNull) {
        /* Speculate that s1 and s2 have either the same types, or no types in common. */

        if (s1.typesBitSet.equals(s2.typesBitSet)) {
            /* Speculate that s1 and s2 have the same types, i.e., the result is empty set. */
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }

        if (!s1.typesBitSet.intersects(s2.typesBitSet)) {
            /* Speculate that s1 and s2 have no types in common, i.e., the result is s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        }

        return doSubtraction1(bb, s1, s2, resultCanBeNull);
    }

    private static TypeState doSubtraction1(BigBang bb, MultiTypeState s1, MultiTypeState s2, boolean resultCanBeNull) {
        /*
         * Speculate that s1 and s2 have no overlap, i.e., they don't have any objects in common. In
         * that case, the result is just s1.
         */
        int idx1 = 0;
        int idx2 = 0;

        AnalysisObject[] so1 = s1.objects;
        AnalysisObject[] so2 = s2.objects;
        while (true) {
            AnalysisObject o1 = so1[idx1];
            AnalysisObject o2 = so2[idx2];

            /* See comment above for the limitation explanation. */
            assert o2.isContextInsensitiveObject() : "Current implementation limitation.";

            if (o1.getTypeId() < o2.getTypeId()) {
                idx1++;
                if (idx1 == so1.length) {
                    return s1.forCanBeNull(bb, resultCanBeNull);
                }
            } else if (o1.getTypeId() > o2.getTypeId()) {
                idx2++;
                if (idx2 == so2.length) {
                    return s1.forCanBeNull(bb, resultCanBeNull);
                }
            } else {
                /* Our speculation failed. */
                break;
            }
        }

        return doSubtraction2(bb, s1, s2, resultCanBeNull, idx1, idx2);
    }

    private static TypeState doSubtraction2(BigBang bb, MultiTypeState s1, MultiTypeState s2, boolean resultCanBeNull, int idx1Param, int idx2Param) {
        try (UnsafeArrayListClosable<AnalysisObject> tlArrayClosable = getTLArrayList(intersectionArrayListTL, 256)) {
            UnsafeArrayList<AnalysisObject> resultObjects = tlArrayClosable.list;

            AnalysisObject[] so1 = s1.objects;
            AnalysisObject[] so2 = s2.objects;
            int[] types1 = s1.getObjectTypeIds();
            int[] types2 = s2.getObjectTypeIds();
            int idx1 = idx1Param;
            int idx2 = idx2Param;
            int l1 = so1.length;
            int l2 = so2.length;
            int t1 = types1[idx1];
            int t2 = types2[idx2];
            while (idx1 < l1 && idx2 < l2) {
                assert so2[idx2].isContextInsensitiveObject() : "Current implementation limitation.";
                if (t1 < t2) {
                    resultObjects.add(so1[idx1]);
                    t1 = types1[++idx1];
                } else if (t1 > t2) {
                    t2 = types2[++idx2];
                } else if (t1 == t2) {
                    assert so1[idx1].type().equals(so2[idx2].type());
                    t1 = types1[++idx1];
                }
            }

            int remainder = s1.objects.length - idx1;
            int totalLength = idx1Param + resultObjects.size + remainder;

            if (totalLength == 0) {
                return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
            } else {
                AnalysisObject[] objects = new AnalysisObject[totalLength];
                /* Copy recently touched first */
                resultObjects.copyToArray(objects, idx1Param);
                /* leading elements */
                System.arraycopy(s1.objects, 0, objects, 0, idx1Param);
                /* trailing elements (remainder) */
                System.arraycopy(s1.objects, idx1, objects, totalLength - remainder, remainder);

                if (TypeStateUtils.holdsSingleTypeState(objects, totalLength)) {
                    /* Multiple objects of the same type. */
                    return new SingleTypeState(bb, resultCanBeNull, bb.analysisPolicy().makePoperties(bb, objects), objects);
                } else {
                    BitSet resultTypesBitSet = TypeStateUtils.andNot(s1.typesBitSet, s2.typesBitSet);
                    /*
                     * Don't need to check if the result is close-to-all-instantiated since result
                     * <= s1.
                     */
                    return new MultiTypeState(bb, resultCanBeNull, bb.analysisPolicy().makePoperties(bb, objects), resultTypesBitSet, objects);
                }
            }
        }
    }
}

final class EmptyTypeState extends TypeState {

    protected static final TypeState SINGLETON = new EmptyTypeState();

    private EmptyTypeState() {
        super(BitArrayUtils.EMPTY_BIT_ARRAY);
    }

    @Override
    public void noteMerge(BigBang bb) {
    }

    @Override
    public boolean hasExactTypes(BitSet typesBitSet) {
        if (typesBitSet.isEmpty()) {
            return true;
        }
        return false;
    }

    @Override
    public AnalysisType exactType() {
        return null;
    }

    @Override
    public int typesCount() {
        return 0;
    }

    @Override
    public Iterator<AnalysisType> typesIterator() {
        return Collections.emptyIterator();
    }

    @Override
    public AnalysisObject[] objectsArray(AnalysisType type) {
        return AnalysisObject.EMPTY_ARRAY;
    }

    @Override
    public Iterator<AnalysisObject> objectsIterator(AnalysisType type) {
        return Collections.emptyIterator();
    }

    @Override
    public boolean containsType(AnalysisType exactType) {
        return false;
    }

    @Override
    public boolean containsObject(AnalysisObject object) {
        return false;
    }

    @Override
    public TypeState exactTypeState(BigBang bb, AnalysisType exactType) {
        return this;
    }

    @Override
    protected TypeState forCanBeNull(BigBang bb, boolean stateCanBeNull) {
        return stateCanBeNull ? NullTypeState.SINGLETON : EmptyTypeState.SINGLETON;
    }

    @Override
    public int objectsCount() {
        return 0;
    }

    @Override
    public AnalysisObject[] objects() {
        return AnalysisObject.EMPTY_ARRAY;
    }

    @Override
    public boolean canBeNull() {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public String toString() {
        return "Empty";
    }
}

final class NullTypeState extends TypeState {

    protected static final TypeState SINGLETON = new NullTypeState();

    private NullTypeState() {
        super(BitArrayUtils.EMPTY_BIT_ARRAY);
    }

    @Override
    public void noteMerge(BigBang bb) {
    }

    @Override
    public boolean hasExactTypes(BitSet typesBitSet) {
        if (typesBitSet.isEmpty()) {
            return true;
        }
        return false;
    }

    @Override
    public AnalysisType exactType() {
        return null;
    }

    @Override
    public int typesCount() {
        return 0;
    }

    @Override
    public Iterator<AnalysisType> typesIterator() {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<AnalysisObject> objectsIterator(AnalysisType type) {
        return Collections.emptyIterator();
    }

    @Override
    public AnalysisObject[] objectsArray(AnalysisType type) {
        return AnalysisObject.EMPTY_ARRAY;
    }

    @Override
    public boolean containsType(AnalysisType exactType) {
        return false;
    }

    @Override
    public boolean containsObject(AnalysisObject object) {
        return false;
    }

    @Override
    public TypeState exactTypeState(BigBang bb, AnalysisType exactType) {
        return this;
    }

    @Override
    public TypeState forCanBeNull(BigBang bb, boolean stateCanBeNull) {
        return stateCanBeNull ? NullTypeState.SINGLETON : EmptyTypeState.SINGLETON;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int objectsCount() {
        return 0;
    }

    @Override
    public AnalysisObject[] objects() {
        return AnalysisObject.EMPTY_ARRAY;
    }

    @Override
    public boolean canBeNull() {
        return true;
    }

    @Override
    public String toString() {
        return "Null";
    }

}

final class UnknownTypeState extends TypeState {

    protected static final TypeState SINGLETON = new UnknownTypeState();

    private UnknownTypeState() {
        super(BitArrayUtils.EMPTY_BIT_ARRAY);
    }

    @Override
    public void noteMerge(BigBang bb) {
    }

    @Override
    public boolean hasExactTypes(BitSet typesBitSet) {
        return false;
    }

    @Override
    public AnalysisType exactType() {
        throw JVMCIError.shouldNotReachHere("UnknownTypeState.exactType()");
    }

    @Override
    public int typesCount() {
        throw JVMCIError.shouldNotReachHere("UnknownTypeState.typesCount()");
    }

    @Override
    public Iterator<AnalysisType> typesIterator() {
        throw JVMCIError.shouldNotReachHere("UnknownTypeState.typesIterator()");
    }

    @Override
    public Iterator<AnalysisObject> objectsIterator(AnalysisType type) {
        throw JVMCIError.shouldNotReachHere("UnknownTypeState.objectsIterator(AnalysisType)");
    }

    @Override
    public AnalysisObject[] objectsArray(AnalysisType type) {
        throw JVMCIError.shouldNotReachHere("UnknownTypeState.objectsArray(AnalysisType)");
    }

    @Override
    public boolean containsType(AnalysisType exactType) {
        throw JVMCIError.shouldNotReachHere("UnknownTypeState.containsType(AnalysisType)");
    }

    @Override
    public boolean containsObject(AnalysisObject object) {
        throw JVMCIError.shouldNotReachHere("UnknownTypeState.containsObject(AnalysisObject)");
    }

    @Override
    public TypeState exactTypeState(BigBang bb, AnalysisType exactType) {
        throw JVMCIError.shouldNotReachHere("UnknownTypeState.exactTypeState(AnalysisType)");
    }

    @Override
    public TypeState forCanBeNull(BigBang bb, boolean stateCanBeNull) {
        return UnknownTypeState.SINGLETON;
    }

    @Override
    public AnalysisObject[] objects() {
        throw JVMCIError.shouldNotReachHere("UnknownTypeState.objects()");
    }

    @Override
    public int objectsCount() {
        throw JVMCIError.shouldNotReachHere("UnknownTypeState.objectsCount()");
    }

    @Override
    public boolean canBeNull() {
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public String toString() {
        return "Unknown";
    }
}
