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

import static com.oracle.truffle.espresso.jvmci.EspressoJVMCIRuntime.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

public final class EspressoResolvedArrayType extends EspressoResolvedObjectType {
    private final EspressoResolvedJavaType elementalType;
    private final int dimensions;
    private final Class<?> mirror;
    private EspressoResolvedJavaType componentType;

    EspressoResolvedArrayType(EspressoResolvedJavaType elementalType, int dimensions, Class<?> mirror) {
        this(elementalType, dimensions, null, mirror);
    }

    EspressoResolvedArrayType(EspressoResolvedJavaType elementalType, int dimensions, EspressoResolvedJavaType componentType, Class<?> mirror) {
        assert dimensions > 0;
        assert !elementalType.isArray();
        this.elementalType = elementalType;
        this.dimensions = dimensions;
        this.componentType = componentType;
        this.mirror = mirror;
    }

    @Override
    protected Class<?> getMirror0() {
        return mirror;
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
    public boolean isArray() {
        return true;
    }

    @Override
    public int getModifiers() {
        return (getElementalType().getModifiers() & (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED)) | Modifier.FINAL | Modifier.ABSTRACT;
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
    public boolean isAssignableFrom(ResolvedJavaType other) {
        if (other instanceof EspressoResolvedArrayType) {
            EspressoResolvedArrayType otherArrayType = (EspressoResolvedArrayType) other;
            if (otherArrayType.dimensions > dimensions) {
                return elementalType.isAssignableFrom(otherArrayType);
            } else if (otherArrayType.dimensions == dimensions) {
                return elementalType.isAssignableFrom(otherArrayType.elementalType);
            }
            return false;
        }
        return false;
    }

    @Override
    public boolean declaresDefaultMethods() {
        return false;
    }

    @Override
    public boolean hasDefaultMethods() {
        return false;
    }

    @Override
    public ResolvedJavaType getSuperclass() {
        return runtime().getJavaLangObject();
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        return runtime().getArrayInterfaces();
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        throw new JVMCIError("Cannot call getSingleImplementor() on a non-interface type: %s", this);
    }

    @Override
    public EspressoResolvedObjectType getSupertype() {
        EspressoResolvedInstanceType javaLangObject = runtime().getJavaLangObject();
        ResolvedJavaType component = getComponentType();
        if (component.isPrimitive() || component.equals(javaLangObject)) {
            return javaLangObject;
        }
        EspressoResolvedObjectType supertype = ((EspressoResolvedObjectType) component).getSupertype();
        return supertype.getArrayClass();
    }

    @Override
    public Assumptions.AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        Assumptions.AssumptionResult<ResolvedJavaType> elementType = elementalType.findLeafConcreteSubtype();
        if (elementType != null && elementType.getResult().equals(elementalType)) {
            /*
             * If the elementType is leaf then the array is leaf under the same assumptions but only
             * if the element type is exactly the leaf type. The element type can be abstract even
             * if there is only one implementor of the abstract type.
             */
            Assumptions.AssumptionResult<ResolvedJavaType> result = new Assumptions.AssumptionResult<>(this);
            result.add(elementType);
            return result;
        }
        return null;
    }

    @Override
    public String getName() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dimensions; i++) {
            sb.append('[');
        }
        sb.append(elementalType.getName());
        return sb.toString();
    }

    @Override
    public EspressoResolvedJavaType getElementalType() {
        return elementalType;
    }

    @Override
    public ResolvedJavaType getComponentType() {
        if (componentType == null) {
            if (dimensions == 1) {
                componentType = elementalType;
            } else {
                componentType = new EspressoResolvedArrayType(elementalType, dimensions - 1, mirror.getComponentType());
            }
        }
        return componentType;
    }

    @Override
    public EspressoResolvedArrayType getArrayClass() {
        if (arrayType == null) {
            arrayType = new EspressoResolvedArrayType(elementalType, dimensions + 1, this, findArrayClass(mirror, 1));
        }
        return arrayType;
    }

    @Override
    public EspressoResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        EspressoResolvedJavaType resolvedElementalType = getElementalType().resolve(accessingClass);
        if (resolvedElementalType.equals(elementalType)) {
            return this;
        }
        return new EspressoResolvedArrayType(resolvedElementalType, dimensions, findArrayClass(resolvedElementalType.getMirror(), dimensions));
    }

    @Override
    public boolean isDefinitelyResolvedWithRespectTo(ResolvedJavaType accessingClass) {
        return getElementalType().isDefinitelyResolvedWithRespectTo(accessingClass);
    }

    static native Class<?> findArrayClass(Class<?> base, int dimensionsDelta);

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        return runtime().getJavaLangObject().resolveMethod(method, callerType);
    }

    @Override
    public Assumptions.AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        throw JVMCIError.unimplemented();
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
    public ResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        return NO_METHODS;
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
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
        throw JVMCIError.unimplemented();
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
    public ResolvedJavaType lookupType(UnresolvedJavaType unresolvedJavaType, boolean resolve) {
        return getElementalType().lookupType(unresolvedJavaType, resolve);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EspressoResolvedArrayType that = (EspressoResolvedArrayType) o;
        return dimensions == that.dimensions && Objects.equals(elementalType, that.elementalType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elementalType, dimensions);
    }
}
