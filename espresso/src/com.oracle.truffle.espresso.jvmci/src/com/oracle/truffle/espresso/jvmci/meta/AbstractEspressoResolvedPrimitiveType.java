/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jvmci.meta;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

public abstract class AbstractEspressoResolvedPrimitiveType extends EspressoResolvedJavaType {
    private final JavaKind kind;

    protected AbstractEspressoResolvedPrimitiveType(JavaKind kind) {
        assert kind.isPrimitive();
        this.kind = kind;
    }

    @Override
    public final boolean hasFinalizer() {
        return false;
    }

    @Override
    public final Assumptions.AssumptionResult<Boolean> hasFinalizableSubclass() {
        return new Assumptions.AssumptionResult<>(false);
    }

    @Override
    public final int getModifiers() {
        return Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC;
    }

    @Override
    public final boolean isInterface() {
        return false;
    }

    @Override
    public final boolean isInstanceClass() {
        return false;
    }

    @Override
    public final boolean isPrimitive() {
        return true;
    }

    @Override
    public final boolean isEnum() {
        return false;
    }

    @Override
    public final boolean isInitialized() {
        return true;
    }

    @Override
    public final void initialize() {
    }

    @Override
    public final boolean isLinked() {
        return true;
    }

    @Override
    public final void link() {
    }

    @Override
    public final boolean hasDefaultMethods() {
        return false;
    }

    @Override
    public final boolean declaresDefaultMethods() {
        return false;
    }

    @Override
    public final boolean isAssignableFrom(ResolvedJavaType other) {
        assert other != null;
        return other.equals(this);
    }

    @Override
    public final boolean isInstance(JavaConstant obj) {
        return obj.getJavaKind() == kind;
    }

    @Override
    public final ResolvedJavaType getSuperclass() {
        return null;
    }

    @Override
    public final ResolvedJavaType[] getInterfaces() {
        return NO_TYPES;
    }

    @Override
    public final ResolvedJavaType getSingleImplementor() {
        throw new JVMCIError("Cannot call getSingleImplementor() on a non-interface type: %s", this);
    }

    @Override
    public final ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        return null;
    }

    @Override
    public final Assumptions.AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        return new Assumptions.AssumptionResult<>(this);
    }

    @Override
    public final String getName() {
        return String.valueOf(kind.getTypeChar());
    }

    @Override
    public final ResolvedJavaType getComponentType() {
        return null;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public List<JavaType> getPermittedSubclasses() {
        return null;
    }

    @Override
    public final JavaKind getJavaKind() {
        return kind;
    }

    @Override
    public final EspressoResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        requireNonNull(accessingClass);
        return this;
    }

    @Override
    public final boolean isDefinitelyResolvedWithRespectTo(ResolvedJavaType accessingClass) {
        requireNonNull(accessingClass);
        return true;
    }

    @Override
    public final ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        return null;
    }

    @Override
    public final Assumptions.AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        return null;
    }

    @Override
    public final ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        return NO_FIELDS;
    }

    @Override
    public final ResolvedJavaField[] getStaticFields() {
        return NO_FIELDS;
    }

    @Override
    public final ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        return null;
    }

    @Override
    public final String getSourceFileName() {
        return null;
    }

    @Override
    public final boolean isLocal() {
        return false;
    }

    @Override
    public final boolean isMember() {
        return false;
    }

    @Override
    public ResolvedJavaType[] getDeclaredTypes() {
        return NO_TYPES;
    }

    @Override
    public final ResolvedJavaType getEnclosingType() {
        return null;
    }

    @Override
    public ResolvedJavaMethod getEnclosingMethod() {
        return null;
    }

    @Override
    public final ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        return NO_METHODS;
    }

    @Override
    public final ResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        return NO_METHODS;
    }

    @Override
    public final List<ResolvedJavaMethod> getAllMethods(boolean forceLink) {
        return Collections.emptyList();
    }

    @Override
    public final ResolvedJavaMethod getClassInitializer() {
        return null;
    }

    @Override
    public final boolean isCloneableWithAllocation() {
        return false;
    }

    @Override
    public AnnotationsInfo getRawDeclaredAnnotationInfo() {
        return null;
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        return null;
    }

    @Override
    public final Class<?> getMirror() {
        return kind.toJavaClass();
    }

    @Override
    public final boolean isArray() {
        return false;
    }

    @Override
    public boolean isRecord() {
        return false;
    }

    @Override
    public List<? extends ResolvedJavaRecordComponent> getRecordComponents() {
        return null;
    }

    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof AbstractEspressoResolvedPrimitiveType that)) {
            return false;
        }
        return that.kind == kind;
    }

    @Override
    public final int hashCode() {
        return kind.hashCode();
    }
}
