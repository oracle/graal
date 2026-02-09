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

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

public abstract class AbstractEspressoResolvedArrayType extends EspressoResolvedObjectType {
    protected final EspressoResolvedJavaType elementalType;
    protected final int dimensions;
    private EspressoResolvedJavaType componentType;

    protected AbstractEspressoResolvedArrayType(EspressoResolvedJavaType elementalType, int dimensions) {
        this(elementalType, dimensions, null);
    }

    protected AbstractEspressoResolvedArrayType(EspressoResolvedJavaType elementalType, int dimensions, EspressoResolvedJavaType componentType) {
        assert dimensions > 0;
        assert !elementalType.isArray();
        this.elementalType = elementalType;
        this.dimensions = dimensions;
        this.componentType = componentType;
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
    public final boolean isArray() {
        return true;
    }

    @Override
    public final int getModifiers() {
        return (getElementalType().getModifiers() & (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED)) | Modifier.FINAL | Modifier.ABSTRACT;
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
    public final boolean isAssignableFrom(ResolvedJavaType other) {
        if (other instanceof AbstractEspressoResolvedArrayType otherArrayType) {
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
    public final EspressoResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        EspressoResolvedJavaType resolvedElementalType = getElementalType().resolve(accessingClass);
        if (resolvedElementalType.equals(elementalType)) {
            return this;
        }
        return withNewElementalType(resolvedElementalType);
    }

    protected abstract AbstractEspressoResolvedArrayType withNewElementalType(EspressoResolvedJavaType resolvedElementalType);

    @Override
    public final boolean declaresDefaultMethods() {
        return false;
    }

    @Override
    public final boolean hasDefaultMethods() {
        return false;
    }

    @Override
    public ResolvedJavaType getSuperclass() {
        return getJavaLangObject();
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        return getArrayInterfaces();
    }

    @Override
    public final ResolvedJavaType getSingleImplementor() {
        throw new JVMCIError("Cannot call getSingleImplementor() on a non-interface type: %s", this);
    }

    @Override
    public EspressoResolvedObjectType getSupertype() {
        AbstractEspressoResolvedInstanceType javaLangObject = getJavaLangObject();
        ResolvedJavaType component = getComponentType();
        if (component.isPrimitive() || component.equals(javaLangObject)) {
            return javaLangObject;
        }
        EspressoResolvedObjectType supertype = ((EspressoResolvedObjectType) component).getSupertype();
        return supertype.getArrayClass();
    }

    @Override
    public final Assumptions.AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
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
    public final String getName() {
        StringBuilder sb = new StringBuilder();
        sb.repeat('[', dimensions);
        sb.append(elementalType.getName());
        return sb.toString();
    }

    @Override
    public final EspressoResolvedJavaType getElementalType() {
        return elementalType;
    }

    @Override
    public final ResolvedJavaType getComponentType() {
        if (componentType == null) {
            if (dimensions == 1) {
                componentType = elementalType;
            } else {
                componentType = getArrayComponentType0();
            }
        }
        return componentType;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public List<JavaType> getPermittedSubclasses() {
        return null;
    }

    protected abstract AbstractEspressoResolvedArrayType getArrayComponentType0();

    @Override
    public final boolean isDefinitelyResolvedWithRespectTo(ResolvedJavaType accessingClass) {
        return getElementalType().isDefinitelyResolvedWithRespectTo(accessingClass);
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        return getJavaLangObject().resolveMethod(method, callerType);
    }

    @Override
    public final Assumptions.AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        throw JVMCIError.unimplemented();
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
        return new ResolvedJavaType[0];
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
    public final ResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        return NO_METHODS;
    }

    @Override
    public final ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
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
        throw JVMCIError.unimplemented();
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
    public final ResolvedJavaType lookupType(UnresolvedJavaType unresolvedJavaType, boolean resolve) {
        return getElementalType().lookupType(unresolvedJavaType, resolve);
    }

    @Override
    public boolean isRecord() {
        return false;
    }

    @Override
    public List<? extends ResolvedJavaRecordComponent> getRecordComponents() {
        return null;
    }

    public int getDimensions() {
        return dimensions;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractEspressoResolvedArrayType that = (AbstractEspressoResolvedArrayType) o;
        return dimensions == that.dimensions && Objects.equals(elementalType, that.elementalType);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(elementalType, dimensions);
    }
}
