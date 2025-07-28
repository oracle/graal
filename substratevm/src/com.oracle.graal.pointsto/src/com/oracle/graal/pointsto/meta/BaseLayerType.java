/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import java.lang.annotation.Annotation;
import java.util.List;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This type is used in the context of Layered Image, when loading a base layer in another layer.
 * <p>
 * Some types used in constants from the base layer cannot be looked up by name in the new layer. In
 * this case, a {@link BaseLayerType} is created using information from the base layer and wrapped
 * in an {@link AnalysisType} to replace this missing type is the new layer.
 */
public class BaseLayerType extends BaseLayerElement implements ResolvedJavaType, OriginalClassProvider {
    /**
     * The type corresponding to this {@link BaseLayerType} can be created later while building the
     * new layer. To avoid both types having the same name, the name of the {@link BaseLayerType} is
     * suffixed.
     */
    public static final String BASE_LAYER_SUFFIX = "_BaseLayer;";
    private final String name;
    private final int baseLayerId;
    private final int modifiers;
    private final boolean isInterface;
    private final boolean isEnum;
    private final boolean isInitialized;
    private final boolean isLinked;
    private final String sourceFileName;
    private final ResolvedJavaType enclosingType;
    private final ResolvedJavaType componentType;
    private final ResolvedJavaType superClass;
    private final ResolvedJavaType[] interfaces;
    private final ResolvedJavaType objectType;
    private ResolvedJavaField[] instanceFields;
    private ResolvedJavaField[] instanceFieldsWithSuper;

    public BaseLayerType(String name, int baseLayerId, int modifiers, boolean isInterface, boolean isEnum, boolean isInitialized, boolean isLinked,
                    String sourceFileName, ResolvedJavaType enclosingType, ResolvedJavaType componentType, ResolvedJavaType superClass, ResolvedJavaType[] interfaces, ResolvedJavaType objectType,
                    Annotation[] annotations) {
        super(annotations);
        this.name = name.substring(0, name.length() - 1) + BASE_LAYER_SUFFIX;
        this.baseLayerId = baseLayerId;
        this.modifiers = modifiers;
        this.isInterface = isInterface;
        this.isEnum = isEnum;
        this.isInitialized = isInitialized;
        this.isLinked = isLinked;
        this.sourceFileName = sourceFileName;
        this.enclosingType = enclosingType;
        this.componentType = componentType;
        this.superClass = superClass;
        this.interfaces = interfaces;
        this.objectType = objectType;
    }

    public void setInstanceFields(ResolvedJavaField[] instanceFields) {
        this.instanceFields = instanceFields;
    }

    public void setInstanceFieldsWithSuper(ResolvedJavaField[] instanceFieldsWithSuper) {
        this.instanceFieldsWithSuper = instanceFieldsWithSuper;
    }

    @Override
    public boolean isArray() {
        return name.charAt(0) == '[';
    }

    @Override
    public boolean hasFinalizer() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public Assumptions.AssumptionResult<Boolean> hasFinalizableSubclass() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    public boolean isInterface() {
        return isInterface;
    }

    @Override
    public boolean isInstanceClass() {
        return !isArray() && !isInterface();
    }

    @Override
    public boolean isPrimitive() {
        /* All the primitive types can be looked up by name */
        return false;
    }

    @Override
    public boolean isEnum() {
        return isEnum;
    }

    @Override
    public boolean isInitialized() {
        return isInitialized;
    }

    @Override
    public void initialize() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public boolean isLinked() {
        return isLinked;
    }

    @Override
    public boolean hasDefaultMethods() {
        /*
         * Types that cannot be looked up by names are hidden classes that currently do not have
         * default methods.
         */
        return false;
    }

    @Override
    public boolean declaresDefaultMethods() {
        /*
         * Types that cannot be looked up by names are hidden classes that currently do not declare
         * default methods.
         */
        return false;
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        return other.equals(objectType) || other.equals(this);
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public ResolvedJavaType getSuperclass() {
        return superClass;
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        return interfaces;
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        if (otherType.equals(this)) {
            return this;
        }
        return objectType;
    }

    @Override
    public Assumptions.AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ResolvedJavaType getComponentType() {
        return componentType;
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public JavaKind getJavaKind() {
        /* All the primitive types can be looked up by name */
        return JavaKind.Object;
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        /*
         * For now, the base layer types have no methods. If they are needed, a BaseLayerMethod can
         * be created and put in an AnalysisMethod in a similar way to this BaseLayerType.
         */
        return null;
    }

    @Override
    public Assumptions.AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        return new ResolvedJavaMethod[0];
    }

    @Override
    public List<ResolvedJavaMethod> getAllMethods(boolean forceLink) {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        return includeSuperclasses ? instanceFieldsWithSuper : instanceFields;
    }

    @Override
    public ResolvedJavaField[] getStaticFields() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public String getSourceFileName() {
        return sourceFileName;
    }

    @Override
    public boolean isLocal() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public boolean isMember() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        return enclosingType;
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        /*
         * Types that cannot be looked up by names are hidden classes that currently do not have a
         * class initializer.
         */
        return null;
    }

    @Override
    public boolean isCloneableWithAllocation() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public Annotation[] getAnnotations() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        throw AnalysisError.shouldNotReachHere("This type is incomplete and should not be used.");
    }

    @Override
    public ResolvedJavaType unwrapTowardsOriginalType() {
        /*
         * This is a temporary workaround until the use of the OriginalClassProvider is minimized
         * and OriginalClassProvider.getJavaClass(type) is allowed to return null.
         *
         * Using java.lang.Object is safe as long as the OriginalClassProvider is used to determine
         * if the type is an instance of another type.
         */
        return objectType;
    }

    public int getBaseLayerId() {
        return baseLayerId;
    }
}
