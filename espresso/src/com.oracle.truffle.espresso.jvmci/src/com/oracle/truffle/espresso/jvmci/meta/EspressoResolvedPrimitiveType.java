/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class EspressoResolvedPrimitiveType extends EspressoResolvedJavaType {
    private static final EspressoResolvedPrimitiveType[] primitives;
    static {
        EspressoResolvedPrimitiveType[] prims = new EspressoResolvedPrimitiveType[JavaKind.Void.getBasicType() + 1];
        prims[JavaKind.Boolean.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Boolean);
        prims[JavaKind.Byte.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Byte);
        prims[JavaKind.Short.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Short);
        prims[JavaKind.Char.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Char);
        prims[JavaKind.Int.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Int);
        prims[JavaKind.Float.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Float);
        prims[JavaKind.Long.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Long);
        prims[JavaKind.Double.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Double);
        prims[JavaKind.Void.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Void);
        primitives = prims;
    }

    private final JavaKind kind;

    private EspressoResolvedPrimitiveType(JavaKind kind) {
        assert kind.isPrimitive();
        this.kind = kind;
    }

    public static EspressoResolvedPrimitiveType forKind(JavaKind kind) {
        if (!kind.isPrimitive()) {
            throw new IllegalArgumentException("Not a primitive kind: " + kind);
        }
        return forBasicType(kind.getBasicType());
    }

    private static EspressoResolvedPrimitiveType forBasicType(int basicType) {
        if (primitives[basicType] == null) {
            throw new IllegalArgumentException("No primitive type for basic type " + basicType);
        }
        return primitives[basicType];
    }

    @Override
    public boolean hasFinalizer() {
        return false;
    }

    @Override
    public Assumptions.AssumptionResult<Boolean> hasFinalizableSubclass() {
        return new Assumptions.AssumptionResult<>(false);
    }

    @Override
    public int getModifiers() {
        return Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC;
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isInstanceClass() {
        return false;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean isEnum() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public void initialize() {
    }

    @Override
    public boolean isLinked() {
        return true;
    }

    @Override
    public void link() {
    }

    @Override
    public boolean hasDefaultMethods() {
        return false;
    }

    @Override
    public boolean declaresDefaultMethods() {
        return false;
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        assert other != null;
        return other.equals(this);
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        return obj.getJavaKind() == kind;
    }

    @Override
    public ResolvedJavaType getSuperclass() {
        return null;
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        return NO_TYPES;
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        throw new JVMCIError("Cannot call getSingleImplementor() on a non-interface type: %s", this);
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        return null;
    }

    @Override
    public Assumptions.AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        return new Assumptions.AssumptionResult<>(this);
    }

    @Override
    public String getName() {
        return String.valueOf(kind.getTypeChar());
    }

    @Override
    public ResolvedJavaType getComponentType() {
        return null;
    }

    @Override
    public JavaKind getJavaKind() {
        return kind;
    }

    @Override
    public EspressoResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        requireNonNull(accessingClass);
        return this;
    }

    @Override
    public boolean isDefinitelyResolvedWithRespectTo(ResolvedJavaType accessingClass) {
        requireNonNull(accessingClass);
        return true;
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        return null;
    }

    @Override
    public Assumptions.AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        return null;
    }

    @Override
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        return NO_FIELDS;
    }

    @Override
    public ResolvedJavaField[] getStaticFields() {
        return NO_FIELDS;
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        return null;
    }

    @Override
    public String getSourceFileName() {
        return null;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean isMember() {
        return false;
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        return null;
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        return NO_METHODS;
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        return NO_METHODS;
    }

    @Override
    public List<ResolvedJavaMethod> getAllMethods(boolean forceLink) {
        return Collections.emptyList();
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        return null;
    }

    @Override
    public boolean isCloneableWithAllocation() {
        return false;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }

    @Override
    public Annotation[] getAnnotations() {
        return NO_ANNOTATIONS;
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return NO_ANNOTATIONS;
    }

    @Override
    public Class<?> getMirror() {
        return kind.toJavaClass();
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EspressoResolvedPrimitiveType)) {
            return false;
        }
        EspressoResolvedPrimitiveType that = (EspressoResolvedPrimitiveType) obj;
        return that.kind == kind;
    }

    @Override
    public int hashCode() {
        return kind.hashCode();
    }
}
