/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public abstract class TypeState {

    /** Get the number of types. */
    public abstract int typesCount();

    /**
     * If this type state has a single, exact type it returns that type, otherwise it returns null.
     */
    public abstract AnalysisType exactType();

    /** Provides an iterator over the types. */
    protected abstract Iterator<AnalysisType> typesIterator(BigBang bb);

    /** Provides an iterable for the types for easy "for-each loop" iteration. */
    public Iterable<AnalysisType> types(BigBang bb) {
        return () -> typesIterator(bb);
    }

    /** Provides a stream for the types. */
    public Stream<AnalysisType> typesStream(BigBang bb) {
        return StreamSupport.stream(types(bb).spliterator(), false);
    }

    /** Returns true if this type state contains the type, otherwise it returns false. */
    public abstract boolean containsType(AnalysisType exactType);

    /* Objects accessing methods. */

    /** Get the number of objects. */
    public abstract int objectsCount();

    protected abstract Iterator<AnalysisObject> objectsIterator(BigBang bb);

    public final Iterable<AnalysisObject> objects(BigBang bb) {
        return () -> objectsIterator(bb);
    }

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

    public boolean isAllocation() {
        return false;
    }

    /**
     * Returns a non-null value when this type state represents a single constant value, or null if
     * this type state is not a single constant.
     *
     * Note that the {@link #canBeNull()} flag still applies when a constant is returned. A type
     * state that is a "constant or null" both returns a non-null result for {@link #asConstant()}}
     * and true for {@link #canBeNull()}.
     */
    public JavaConstant asConstant() {
        return null;
    }

    public boolean isEmpty() {
        return this == EmptyTypeState.SINGLETON;
    }

    public boolean isNull() {
        return this == NullTypeState.SINGLETON;
    }

    public boolean isPrimitive() {
        return this instanceof AnyPrimitiveTypeState;
    }

    public abstract boolean canBeNull();

    /** Note that the objects of this type state have been merged. */
    public void noteMerge(@SuppressWarnings("unused") PointsToAnalysis bb) {
    }

    public boolean isMerged() {
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public abstract boolean equals(Object o);

    /* Static methods. */

    public static TypeState forEmpty() {
        return EmptyTypeState.SINGLETON;
    }

    public static TypeState forNull() {
        return NullTypeState.SINGLETON;
    }

    public static TypeState defaultValueForKind(JavaKind javaKind) {
        if (javaKind.isPrimitive()) {
            return TypeState.forPrimitiveConstant(0);
        } else {
            return TypeState.forNull();
        }
    }

    public static TypeState forPrimitiveConstant(long value) {
        return PrimitiveConstantTypeState.forValue(value);
    }

    public static TypeState anyPrimitiveState() {
        return AnyPrimitiveTypeState.SINGLETON;
    }

    /** Wraps an analysis object into a non-null type state. */
    public static TypeState forNonNullObject(PointsToAnalysis bb, AnalysisObject object) {
        return bb.analysisPolicy().singleTypeState(bb, false, object.type(), object);
    }

    /** Wraps the analysis object corresponding to a JavaConstant into a non-null type state. */
    public static TypeState forConstant(PointsToAnalysis bb, JavaConstant constant, AnalysisType exactType) {
        assert !constant.isNull() : constant;
        assert exactType.isArray() || (exactType.isInstanceClass() && !Modifier.isAbstract(exactType.getModifiers())) : exactType;
        return bb.analysisPolicy().constantTypeState(bb, constant, exactType);
    }

    public static SingleTypeState forExactType(PointsToAnalysis bb, AnalysisType exactType, boolean canBeNull) {
        assert exactType.getContextInsensitiveAnalysisObject() != null : exactType;
        return forExactType(bb, exactType.getContextInsensitiveAnalysisObject(), canBeNull);
    }

    public static SingleTypeState forExactType(PointsToAnalysis bb, AnalysisObject object, boolean canBeNull) {
        assert object.type().isArray() || (object.type().isInstanceClass() && !Modifier.isAbstract(object.type().getModifiers())) : object.type();
        return bb.analysisPolicy().singleTypeState(bb, canBeNull, object.type(), object);
    }

    public static TypeState forType(PointsToAnalysis bb, AnalysisType type, boolean canBeNull) {
        return forType(bb, type.getContextInsensitiveAnalysisObject(), canBeNull);
    }

    public static TypeState forType(PointsToAnalysis bb, AnalysisObject object, boolean canBeNull) {
        return bb.analysisPolicy().singleTypeState(bb, canBeNull, object.type(), object);
    }

    public final TypeState forNonNull(PointsToAnalysis bb) {
        return forCanBeNull(bb, false);
    }

    public abstract TypeState forCanBeNull(PointsToAnalysis bb, boolean stateCanBeNull);

    public static TypeState forUnion(PointsToAnalysis bb, TypeState s1, TypeState s2) {
        if (s1.isEmpty()) {
            return s2;
        } else if (s1.isNull()) {
            return s2.forCanBeNull(bb, true);
        } else if (s2.isEmpty()) {
            return s1;
        } else if (s2.isNull()) {
            return s1.forCanBeNull(bb, true);
        } else if (s1 instanceof PrimitiveConstantTypeState c1 && s2 instanceof PrimitiveConstantTypeState c2 && c1.getValue() == c2.getValue()) {
            return s1;
        } else if (s1.isPrimitive()) {
            assert s2.isPrimitive() : s2;
            return TypeState.anyPrimitiveState();
        } else if (s2.isPrimitive()) {
            assert s1.isPrimitive() : s1;
            return TypeState.anyPrimitiveState();
        } else if (s1 instanceof SingleTypeState && s2 instanceof SingleTypeState) {
            return bb.analysisPolicy().doUnion(bb, (SingleTypeState) s1, (SingleTypeState) s2);
        } else if (s1 instanceof SingleTypeState && s2 instanceof MultiTypeState) {
            return bb.analysisPolicy().doUnion(bb, (MultiTypeState) s2, (SingleTypeState) s1);
        } else if (s1 instanceof MultiTypeState && s2 instanceof SingleTypeState) {
            return bb.analysisPolicy().doUnion(bb, (MultiTypeState) s1, (SingleTypeState) s2);
        } else {
            if (s1.objectsCount() >= s2.objectsCount()) {
                return bb.analysisPolicy().doUnion(bb, (MultiTypeState) s1, (MultiTypeState) s2);
            } else {
                return bb.analysisPolicy().doUnion(bb, (MultiTypeState) s2, (MultiTypeState) s1);
            }
        }
    }

    public static TypeState forIntersection(PointsToAnalysis bb, TypeState s1, TypeState s2) {
        if (s1.isEmpty()) {
            return s1;
        } else if (s1.isNull()) {
            return s1.forCanBeNull(bb, s2.canBeNull());
        } else if (s2.isEmpty()) {
            return s2;
        } else if (s2.isNull()) {
            return s2.forCanBeNull(bb, s1.canBeNull());
        } else if (s1 instanceof SingleTypeState && s2 instanceof SingleTypeState) {
            return bb.analysisPolicy().doIntersection(bb, (SingleTypeState) s1, (SingleTypeState) s2);
        } else if (s1 instanceof SingleTypeState && s2 instanceof MultiTypeState) {
            return bb.analysisPolicy().doIntersection(bb, (SingleTypeState) s1, (MultiTypeState) s2);
        } else if (s1 instanceof MultiTypeState && s2 instanceof SingleTypeState) {
            return bb.analysisPolicy().doIntersection(bb, (MultiTypeState) s1, (SingleTypeState) s2);
        } else {
            return bb.analysisPolicy().doIntersection(bb, (MultiTypeState) s1, (MultiTypeState) s2);
        }
    }

    public static TypeState forSubtraction(PointsToAnalysis bb, TypeState s1, TypeState s2) {
        if (s1.isEmpty()) {
            return s1;
        } else if (s1.isNull()) {
            return s1.forCanBeNull(bb, !s2.canBeNull());
        } else if (s2.isEmpty()) {
            return s1;
        } else if (s2.isNull()) {
            return s1.forCanBeNull(bb, false);
        } else if (s1 instanceof SingleTypeState && s2 instanceof SingleTypeState) {
            return bb.analysisPolicy().doSubtraction(bb, (SingleTypeState) s1, (SingleTypeState) s2);
        } else if (s1 instanceof SingleTypeState && s2 instanceof MultiTypeState) {
            return bb.analysisPolicy().doSubtraction(bb, (SingleTypeState) s1, (MultiTypeState) s2);
        } else if (s1 instanceof MultiTypeState && s2 instanceof SingleTypeState) {
            return bb.analysisPolicy().doSubtraction(bb, (MultiTypeState) s1, (SingleTypeState) s2);
        } else {
            return bb.analysisPolicy().doSubtraction(bb, (MultiTypeState) s1, (MultiTypeState) s2);
        }
    }
}

final class EmptyTypeState extends TypeState {

    static final TypeState SINGLETON = new EmptyTypeState();

    private EmptyTypeState() {
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
    public Iterator<AnalysisType> typesIterator(BigBang bb) {
        return Collections.emptyIterator();
    }

    @Override
    protected Iterator<AnalysisObject> objectsIterator(BigBang bb) {
        return Collections.emptyIterator();
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
    public TypeState forCanBeNull(PointsToAnalysis bb, boolean stateCanBeNull) {
        return stateCanBeNull ? NullTypeState.SINGLETON : EmptyTypeState.SINGLETON;
    }

    @Override
    public int objectsCount() {
        return 0;
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

    static final TypeState SINGLETON = new NullTypeState();

    private NullTypeState() {
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
    public Iterator<AnalysisType> typesIterator(BigBang bb) {
        return Collections.emptyIterator();
    }

    @Override
    protected Iterator<AnalysisObject> objectsIterator(BigBang bb) {
        return Collections.emptyIterator();
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
    public TypeState forCanBeNull(PointsToAnalysis bb, boolean stateCanBeNull) {
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
    public boolean canBeNull() {
        return true;
    }

    @Override
    public String toString() {
        return "Null";
    }

}
