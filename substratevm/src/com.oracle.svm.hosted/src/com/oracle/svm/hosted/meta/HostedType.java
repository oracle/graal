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
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class HostedType extends HostedElement implements SharedType, WrappedJavaType, OriginalClassProvider {

    public static final HostedType[] EMPTY_ARRAY = new HostedType[0];

    protected final HostedUniverse universe;
    protected final AnalysisType wrapped;

    private final JavaKind kind;
    private final JavaKind storageKind;

    private final HostedClass superClass;
    private final HostedInterface[] interfaces;

    protected HostedArrayClass arrayType;
    protected HostedType[] subTypes;
    protected HostedField[] staticFields;

    protected int typeID;
    protected HostedType uniqueConcreteImplementation;
    protected HostedMethod[] allDeclaredMethods;

    // region closed-world only fields

    protected HostedMethod[] closedTypeWorldVTable;

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
    protected short[] closedTypeWorldTypeCheckSlots;

    // endregion closed-world only fields

    // region open-world only fields

    protected HostedType[] typeCheckInterfaceOrder;
    protected HostedMethod[] openTypeWorldDispatchTables;
    protected int[] itableStartingOffsets;

    /**
     * Instance class depth. Due to single-inheritance a parent class will be at the same depth in
     * all subtypes. For interface types we set this to a negative value.
     */
    protected int typeIDDepth;

    /*
     * Since we store interfaces within the openTypeWorldTypeCheckSlots, we must know both the
     * number of class and interface types to ensure we read from the correct locations.
     */

    protected int numClassTypes;

    protected int numInterfaceTypes;

    protected int[] openTypeWorldTypeCheckSlots;

    // endregion open-world only fields

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

    protected HostedMethod[] getClosedTypeWorldVTable() {
        assert closedTypeWorldVTable != null;
        return closedTypeWorldVTable;
    }

    protected HostedMethod[] getOpenTypeWorldDispatchTables() {
        assert openTypeWorldDispatchTables != null;
        return openTypeWorldDispatchTables;
    }

    public HostedMethod[] getVTable() {
        return SubstrateOptions.closedTypeWorld() ? getClosedTypeWorldVTable() : getOpenTypeWorldDispatchTables();
    }

    @Override
    public int getTypeID() {
        assert typeID != -1;
        return typeID;
    }

    public void setTypeCheckRange(short typeCheckStart, short typeCheckRange) {
        assert SubstrateOptions.closedTypeWorld();
        this.typeCheckStart = typeCheckStart;
        this.typeCheckRange = typeCheckRange;
    }

    public void setTypeCheckSlot(short typeCheckSlot) {
        assert SubstrateOptions.closedTypeWorld();
        this.typeCheckSlot = typeCheckSlot;
    }

    public void setClosedTypeWorldTypeCheckSlots(short[] closedTypeWorldTypeCheckSlots) {
        assert SubstrateOptions.closedTypeWorld();
        this.closedTypeWorldTypeCheckSlots = closedTypeWorldTypeCheckSlots;
    }

    public short getTypeCheckStart() {
        assert SubstrateOptions.closedTypeWorld();
        return typeCheckStart;
    }

    public short getTypeCheckRange() {
        assert SubstrateOptions.closedTypeWorld();
        return typeCheckRange;
    }

    public short getTypeCheckSlot() {
        assert SubstrateOptions.closedTypeWorld();
        return typeCheckSlot;
    }

    public short[] getClosedTypeWorldTypeCheckSlots() {
        assert SubstrateOptions.closedTypeWorld();
        assert closedTypeWorldTypeCheckSlots != null;
        return closedTypeWorldTypeCheckSlots;
    }

    public void setTypeIDDepth(int typeIDDepth) {
        assert !SubstrateOptions.closedTypeWorld();
        this.typeIDDepth = typeIDDepth;
    }

    public void setNumClassTypes(int numClassTypes) {
        assert !SubstrateOptions.closedTypeWorld();
        this.numClassTypes = numClassTypes;
    }

    public void setNumInterfaceTypes(int numInterfaceTypes) {
        assert !SubstrateOptions.closedTypeWorld();
        this.numInterfaceTypes = numInterfaceTypes;
    }

    public void setOpenTypeWorldTypeCheckSlots(int[] openTypeWorldTypeCheckSlots) {
        assert !SubstrateOptions.closedTypeWorld();
        this.openTypeWorldTypeCheckSlots = openTypeWorldTypeCheckSlots;
    }

    public int getTypeIDDepth() {
        assert !SubstrateOptions.closedTypeWorld();
        return typeIDDepth;
    }

    public int getNumClassTypes() {
        assert !SubstrateOptions.closedTypeWorld();
        return numClassTypes;
    }

    public int getNumInterfaceTypes() {
        assert !SubstrateOptions.closedTypeWorld();
        return numInterfaceTypes;
    }

    public int[] getOpenTypeWorldTypeCheckSlots() {
        assert !SubstrateOptions.closedTypeWorld();
        assert openTypeWorldTypeCheckSlots != null : this;
        return openTypeWorldTypeCheckSlots;
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
    public String toJavaName() {
        return wrapped.toJavaName();
    }

    @Override
    public String toJavaName(boolean qualified) {
        return wrapped.toJavaName(qualified);
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
        /*
         * Note that we do not delegate to wrapped.isInitialized here: when a class initializer is
         * simulated at image build time, then AnalysisType.isInitialized() returns false but
         * DynamicHub.isInitialized returns true. We want to treat such classes as initialized
         * during AOT compilation.
         */
        return getHub().isInitialized();
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

        return hResult;
    }

    @Override
    public final int getModifiers() {
        return wrapped.getModifiers();
    }

    @Override
    public final boolean isInstance(JavaConstant obj) {
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
        return "HostedType<" + toJavaName(false) + " -> " + wrapped.toString() + ">";
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
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        return getDeclaredConstructors(true);
    }

    @Override
    public HostedMethod[] getDeclaredConstructors(boolean forceLink) {
        VMError.guarantee(forceLink == false, "only use getDeclaredConstructors without forcing to link, because linking can throw LinkageError");
        return universe.lookup(wrapped.getDeclaredConstructors(forceLink));
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return getDeclaredMethods(true);
    }

    @Override
    public HostedMethod[] getDeclaredMethods(boolean forceLink) {
        VMError.guarantee(forceLink == false, "only use getDeclaredMethods without forcing to link, because linking can throw LinkageError");
        return universe.lookup(wrapped.getDeclaredMethods(forceLink));
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
    public ResolvedJavaType unwrapTowardsOriginalType() {
        return wrapped;
    }

    public Class<?> getJavaClass() {
        return OriginalClassProvider.getJavaClass(this);
    }
}
