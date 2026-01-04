/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.SubstrateMetadata;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.crema.CremaResolvedJavaRecordComponent;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Represents a primitive or reference resolved Java type, including additional capabilities of the
 * closed world e.g. instantiable, instantiated, effectively final ...
 */
public abstract class InterpreterResolvedJavaType extends InterpreterAnnotated implements ResolvedJavaType, CremaTypeAccess, SubstrateMetadata {
    public static final InterpreterResolvedJavaType[] EMPTY_ARRAY = new InterpreterResolvedJavaType[0];

    private final Symbol<Type> type;
    protected final Class<?> clazz;
    private final JavaConstant clazzConstant;
    private final boolean isWordType;
    private volatile boolean methodEnterEventEnabled;
    private volatile boolean methodExitEventEnabled;

    // TODO move to crema once GR-71517 is resolved
    private volatile ResolvedJavaType ristrettoType;
    private static final AtomicReferenceFieldUpdater<InterpreterResolvedJavaType, ResolvedJavaType> RISTRETTO_TYPE_UPDATER = AtomicReferenceFieldUpdater
                    .newUpdater(InterpreterResolvedJavaType.class, ResolvedJavaType.class, "ristrettoType");

    // Only called at build time universe creation.
    @Platforms(Platform.HOSTED_ONLY.class)
    protected InterpreterResolvedJavaType(Symbol<Type> type, Class<?> javaClass) {
        this.type = MetadataUtil.requireNonNull(type);
        this.clazzConstant = null;
        this.clazz = MetadataUtil.requireNonNull(javaClass);
        this.isWordType = WordBase.class.isAssignableFrom(javaClass);
    }

    // Called by the interpreter.
    protected InterpreterResolvedJavaType(Symbol<Type> type, Class<?> javaClass, boolean isWordType) {
        this.type = MetadataUtil.requireNonNull(type);
        this.clazzConstant = null;
        this.clazz = MetadataUtil.requireNonNull(javaClass);
        this.isWordType = isWordType;
    }

    protected InterpreterResolvedJavaType(Symbol<Type> type, JavaConstant clazzConstant, boolean isWordType) {
        this.type = MetadataUtil.requireNonNull(type);
        this.clazzConstant = MetadataUtil.requireNonNull(clazzConstant);
        this.clazz = null;
        this.isWordType = isWordType;
    }

    public ResolvedJavaType getRistrettoType(Function<InterpreterResolvedJavaType, ResolvedJavaType> ristrettoTypeSupplier) {
        if (this.ristrettoType != null) {
            return this.ristrettoType;
        }
        /*
         * We allow concurrent allocation of a ristretto type per interpreter type. Eventually
         * however we CAS on the pointer in the interpreter representation, if another thread was
         * faster return its type.
         */
        return getOrSetRistrettoType(ristrettoTypeSupplier.apply(this));
    }

    private ResolvedJavaType getOrSetRistrettoType(ResolvedJavaType newRistrettoType) {
        if (RISTRETTO_TYPE_UPDATER.compareAndSet(this, null, newRistrettoType)) {
            return newRistrettoType;
        }
        var rType = this.ristrettoType;
        assert rType != null : "If CAS for null fails must have written a type already";
        return rType;
    }

    @Override
    public final String getName() {
        return type.toString();
    }

    // This is only here for performance, otherwise the clazzConstant must be unwrapped every time.
    public final Class<?> getJavaClass() {
        return MetadataUtil.requireNonNull(clazz);
    }

    public final boolean isWordType() {
        return isWordType;
    }

    @Override
    public final boolean isPrimitive() {
        return this instanceof InterpreterResolvedPrimitiveType;
    }

    @Override
    public final boolean isInterface() {
        return Modifier.isInterface(getModifiers());
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }
        if (obj instanceof InterpreterResolvedJavaType that) {
            if (this.clazz != null) {
                return this.clazz == that.clazz;
            }
            // We have no access to the classes (debugger side), only opaque constants.
            assert this.clazzConstant != null && that.clazzConstant != null;
            return this.clazzConstant.equals(that.clazzConstant);
        } else {
            return false;
        }
    }

    @Override
    public final int hashCode() {
        return getName().hashCode();
    }

    @Override
    public final String toString() {
        return "InterpreterResolvedJavaType<" + getName() + ">";
    }

    public void toggleMethodEnterEvent(boolean enable) {
        methodEnterEventEnabled = enable;
    }

    public void toggleMethodExitEvent(boolean enable) {
        methodExitEventEnabled = enable;
    }

    public boolean isMethodEnterEvent() {
        return methodEnterEventEnabled;
    }

    public boolean isMethodExitEvent() {
        return methodExitEventEnabled;
    }

    @Override
    public boolean isJavaLangObject() {
        return ResolvedJavaType.super.isJavaLangObject();
    }

    @Override
    public Symbol<Name> getSymbolicName() {
        // This is assumed to be low-traffic
        return SymbolsSupport.getNames().getOrCreate(TypeSymbols.toClassNameEntry(type));
    }

    @Override
    public Symbol<Type> getSymbolicType() {
        return type;
    }

    @Override
    public final boolean isAssignableFrom(InterpreterResolvedJavaType other) {
        return clazz.isAssignableFrom(other.clazz);
    }

    @Override
    public final boolean hasSameDefiningClassLoader(InterpreterResolvedJavaType other) {
        return this.clazz.getClassLoader() == other.clazz.getClassLoader();
    }

    @Override
    public abstract InterpreterResolvedJavaMethod[] getDeclaredMethods(boolean forceLink);

    @Override
    public final boolean isMagicAccessor() {
        return false;
    }

    @Override
    public final boolean isConcrete() {
        return ResolvedJavaType.super.isConcrete();
    }

    // region Unimplemented methods

    @Override
    public final boolean hasFinalizer() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final Assumptions.AssumptionResult<Boolean> hasFinalizableSubclass() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isInstanceClass() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isEnum() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isRecord() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public List<? extends CremaResolvedJavaRecordComponent> getRecordComponents() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isInitialized() {
        return DynamicHub.fromClass(clazz).isInitialized();
    }

    @Override
    public final void initialize() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isLinked() {
        return DynamicHub.fromClass(clazz).isLinked();
    }

    @Override
    public void link() {
        RuntimeClassLoading.ensureLinked(DynamicHub.fromClass(clazz));
    }

    @Override
    public final boolean isInstance(JavaConstant obj) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final ResolvedJavaType getSingleImplementor() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final Assumptions.AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final Assumptions.AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public ResolvedJavaType lookupType(UnresolvedJavaType unresolvedJavaType, boolean resolve) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final InterpreterResolvedObjectType getArrayClass() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isLocal() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isMember() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final ResolvedJavaType getEnclosingType() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public ResolvedJavaMethod getEnclosingMethod() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public InterpreterResolvedJavaMethod[] getDeclaredMethods() {
        return getDeclaredMethods(true);
    }

    @Override
    public List<ResolvedJavaMethod> getAllMethods(boolean forceLink) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        // We currently do not expect this to be called for any other type than
        // CremaResolvedObjectType.
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isCloneableWithAllocation() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public ResolvedJavaType[] getDeclaredTypes() {
        throw VMError.intentionallyUnimplemented();
    }

    // endregion Unimplemented methods
}
