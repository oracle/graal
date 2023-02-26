/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedType;

import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class HostedType implements SharedType, WrappedJavaType, OriginalClassProvider {

    protected final HostedUniverse universe;
    protected final AnalysisType wrapped;

    private final JavaKind kind;
    private final JavaKind storageKind;

    private final HostedClass superClass;
    private final HostedInterface[] interfaces;

    protected HostedArrayClass arrayType;
    protected HostedType[] subTypes;
    protected HostedField[] staticFields;

    protected HostedMethod[] vtable;

    protected int typeID;
    protected HostedType uniqueConcreteImplementation;
    protected HostedMethod[] allDeclaredMethods;

    /**
     * Start of type check range check. See {@link DynamicHub}.typeCheckStart
     */
    protected short typeCheckStart;

    /**
     *
     * Number of values within type check range check. See {@link DynamicHub}.typeCheckRange
     */
    protected short typeCheckRange;

    /**
     * Type check array slot to read for type check range check. See
     * {@link DynamicHub}.typeCheckSlot
     */
    protected short typeCheckSlot;

    /**
     * Array used within type checks. See {@link DynamicHub}.typeCheckSlots
     */
    protected short[] typeCheckSlots;

    /**
     * A more precise subtype that can replace this type as the declared type of values. Null if
     * this type is never instantiated and does not have any instantiated subtype, i.e., if no value
     * of this type can ever exist. Equal to this type if this type is instantiated, i.e, this type
     * cannot be strengthened.
     */
    protected HostedType strengthenStampType;

    public HostedType(HostedUniverse universe, AnalysisType wrapped, JavaKind kind, JavaKind storageKind, HostedClass superClass, HostedInterface[] interfaces) {
        this.universe = universe;
        this.wrapped = wrapped;
        this.kind = kind;
        this.storageKind = storageKind;
        this.superClass = superClass;
        this.interfaces = interfaces;
        this.typeID = -1;
    }

    public HostedType getStrengthenStampType() {
        return strengthenStampType;
    }

    public HostedType[] getSubTypes() {
        assert subTypes != null;
        return subTypes;
    }

    public HostedMethod[] getVTable() {
        assert vtable != null;
        return vtable;
    }

    public int getTypeID() {
        assert typeID != -1;
        return typeID;
    }

    public void setTypeCheckRange(short typeCheckStart, short typeCheckRange) {
        this.typeCheckStart = typeCheckStart;
        this.typeCheckRange = typeCheckRange;
    }

    public void setTypeCheckSlot(short typeCheckSlot) {
        this.typeCheckSlot = typeCheckSlot;
    }

    public void setTypeCheckSlots(short[] typeCheckSlots) {
        this.typeCheckSlots = typeCheckSlots;
    }

    public short getTypeCheckStart() {
        return typeCheckStart;
    }

    public short getTypeCheckRange() {
        return typeCheckRange;
    }

    public short getTypeCheckSlot() {
        return typeCheckSlot;
    }

    public short[] getTypeCheckSlots() {
        assert typeCheckSlots != null;
        return typeCheckSlots;
    }

    /**
     * Returns true if this type is part of the word type hierarchy, i.e, implements
     * {@link WordBase}.
     */
    public boolean isWordType() {
        /* Word types have the kind Object, but a primitive storageKind. */
        return kind != storageKind;
    }

    /**
     * Returns all methods (including constructors and synthetic methods) that have this type as the
     * {@link HostedMethod#getDeclaringClass() declaring class}.
     */
    public HostedMethod[] getAllDeclaredMethods() {
        assert allDeclaredMethods != null : "not initialized yet";
        return allDeclaredMethods;
    }

    public HostedType getUniqueConcreteImplementation() {
        return uniqueConcreteImplementation;
    }

    @Override
    public DynamicHub getHub() {
        return universe.hostVM().dynamicHub(wrapped);
    }

    @Override
    public AnalysisType getWrapped() {
        return wrapped;
    }

    public boolean isInstantiated() {
        return wrapped.isInstantiated();
    }

    @Override
    public final String getName() {
        return wrapped.getName();
    }

    @Override
    public final JavaKind getJavaKind() {
        return kind;
    }

    /**
     * The kind of the field in memory (in contrast to {@link #getJavaKind()}, which is the kind of
     * the field on the Java type system level). For example {@link WordBase word types} have a
     * {@link #getJavaKind} of {@link JavaKind#Object}, but a primitive {@link #storageKind}.
     */
    @Override
    public final JavaKind getStorageKind() {
        return storageKind;
    }

    @Override
    public final ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    @Override
    public final boolean hasFinalizer() {
        /* We just ignore finalizers. */
        return false;
    }

    @Override
    public final AssumptionResult<Boolean> hasFinalizableSubclass() {
        /* We just ignore finalizers. */
        return new AssumptionResult<>(false);
    }

    @Override
    public final boolean isInitialized() {
        return wrapped.isInitialized();
    }

    @Override
    public void initialize() {
        wrapped.initialize();
    }

    @Override
    public final HostedArrayClass getArrayClass() {
        return arrayType;
    }

    public HostedType getArrayClass(int dimension) {
        HostedType result = this;
        for (int i = 0; i < dimension; i++) {
            result = result.arrayType;
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    @Override
    public abstract HostedField[] getInstanceFields(boolean includeSuperclasses);

    @Override
    public ResolvedJavaField[] getStaticFields() {
        assert staticFields != null;
        return staticFields;
    }

    @Override
    public final HostedClass getSuperclass() {
        return superClass;
    }

    @Override
    public final HostedInterface[] getInterfaces() {
        return interfaces;
    }

    @Override
    public abstract HostedType getComponentType();

    public abstract HostedType getBaseType();

    public abstract int getArrayDimension();

    @Override
    public AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        ResolvedJavaType result = getSingleImplementor();
        if (result == null) {
            return null;
        } else {
            return new AssumptionResult<>(result);
        }
    }

    @Override
    public HostedType getSingleImplementor() {
        return uniqueConcreteImplementation;
    }

    @Override
    public final boolean isAssignableFrom(ResolvedJavaType other) {
        return wrapped.isAssignableFrom(((HostedType) other).wrapped);
    }

    @Override
    public final ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        return universe.lookup(wrapped.findLeastCommonAncestor(((HostedType) otherType).wrapped));
    }

    @Override
    public ResolvedJavaMethod resolveConcreteMethod(ResolvedJavaMethod m, ResolvedJavaType callerType) {
        HostedMethod method = (HostedMethod) m;

        AnalysisMethod aResult = wrapped.resolveConcreteMethod(method.wrapped);
        HostedMethod hResult;
        if (aResult == null) {
            hResult = null;
        } else if (!aResult.isImplementationInvoked() && !isWordType()) {
            /*
             * Filter out methods that are not seen as invoked by the static analysis, e.g., because
             * the declaring type is not instantiated. Word types are an exception, because methods
             * of word types are never marked as invoked (they are always intrinsified).
             */
            hResult = null;
        } else {
            hResult = universe.lookup(aResult);
        }

        /**
         * Check that the SharedType implementation, which is used for JIT compilation, gives the
         * same result as the hosted implementation.
         */
        assert hResult == null || isWordType() || hResult.equals(SharedType.super.resolveConcreteMethod(method, callerType));

        return hResult;
    }

    @Override
    public final int getModifiers() {
        return wrapped.getModifiers();
    }

    @Override
    public final boolean isInstance(JavaConstant obj) {
        assert universe.lookup(obj) == obj : "constant should not have analysis-universe dependent value";
        return wrapped.isInstance(obj);
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        return null;
    }

    @Override
    public String getSourceFileName() {
        return wrapped.getSourceFileName();
    }

    @Override
    public String toString() {
        return "HostedType<" + toJavaName(true) + "   " + wrapped.toString() + ">";
    }

    @Override
    public boolean isLocal() {
        return wrapped.isLocal();
    }

    @Override
    public boolean isMember() {
        return wrapped.isLocal();
    }

    @Override
    public HostedType getEnclosingType() {
        return universe.lookup(wrapped.getEnclosingType());
    }

    @Override
    public HostedMethod[] getDeclaredConstructors() {
        return universe.lookup(wrapped.getDeclaredConstructors());
    }

    @Override
    public HostedMethod[] getDeclaredMethods() {
        return universe.lookup(wrapped.getDeclaredMethods());
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        return universe.lookup(wrapped.getClassInitializer());
    }

    @Override
    public boolean isLinked() {
        /*
         * If the wrapped type is referencing some missing types verification may fail and the type
         * will not be linked.
         */
        return wrapped.isLinked();
    }

    @Override
    public void link() {
        wrapped.link();
    }

    @Override
    public boolean hasDefaultMethods() {
        return wrapped.hasDefaultMethods();
    }

    @Override
    public boolean declaresDefaultMethods() {
        return wrapped.declaresDefaultMethods();
    }

    @Override
    public boolean isCloneableWithAllocation() {
        return wrapped.isCloneableWithAllocation();
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResolvedJavaType getHostClass() {
        return universe.lookup(wrapped.getHostClass());
    }

    @Override
    public Class<?> getJavaClass() {
        return OriginalClassProvider.getJavaClass(universe.getSnippetReflection(), wrapped);
    }
}
