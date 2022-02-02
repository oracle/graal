/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.annotation;

import java.lang.annotation.Annotation;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.util.GraalAccess;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class is used to correct the behavior of
 * {@link ResolvedJavaType#isAssignableFrom(ResolvedJavaType)} for
 * {@link AnnotationSupport#constantAnnotationMarkerInterface}. For all other methods, this class
 * simply forwards calls to the original ResolvedJavaType. See
 * AnnotationSupport#constantAnnotationMarkerSubstitutionType for more details.
 */
public class ConstantAnnotationMarkerSubstitutionType implements ResolvedJavaType, OriginalClassProvider {
    private final ResolvedJavaType original;
    private final SubstitutionProcessor substitutionProcessor;

    public ConstantAnnotationMarkerSubstitutionType(ResolvedJavaType original, SubstitutionProcessor substitutionProcessor) {
        this.original = original;
        this.substitutionProcessor = substitutionProcessor;
    }

    /**
     * Since AnnotationSubstitutionTypes do not naturally implement the constant marker interface,
     * but are artificially forced to implement it via
     * AnnotationObjectReplacer#replacementComputer(Object), they should not be considered
     * assignable from the marker interface.
     */
    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        ResolvedJavaType substitution = substitutionProcessor.lookup(other);
        if (substitution instanceof AnnotationSubstitutionType) {
            return false;
        }
        return original.isAssignableFrom(other);
    }

    @Override
    public Class<?> getJavaClass() {
        return OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), original);
    }

    @Override
    public boolean hasFinalizer() {
        return original.hasFinalizer();
    }

    @Override
    public Assumptions.AssumptionResult<Boolean> hasFinalizableSubclass() {
        return original.hasFinalizableSubclass();
    }

    @Override
    public int getModifiers() {
        return original.getModifiers();
    }

    @Override
    public boolean isInterface() {
        return original.isInterface();
    }

    @Override
    public boolean isInstanceClass() {
        return original.isInstanceClass();
    }

    @Override
    public boolean isPrimitive() {
        return original.isPrimitive();
    }

    @Override
    public boolean isEnum() {
        return original.isEnum();
    }

    @Override
    public boolean isInitialized() {
        return original.isInitialized();
    }

    @Override
    public void initialize() {
        original.initialize();
    }

    @Override
    public boolean isLinked() {
        return original.isLinked();
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResolvedJavaType getHostClass() {
        return original.getHostClass();
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        return original.isInstance(obj);
    }

    @Override
    public ResolvedJavaType getSuperclass() {
        return original.getSuperclass();
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        return original.getInterfaces();
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        return original.getSingleImplementor();
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        return original.findLeastCommonAncestor(otherType);
    }

    @Override
    public Assumptions.AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        return original.findLeafConcreteSubtype();
    }

    @Override
    public String getName() {
        return original.getName();
    }

    @Override
    public ResolvedJavaType getComponentType() {
        return original.getComponentType();
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        return original.getArrayClass();
    }

    @Override
    public JavaKind getJavaKind() {
        return original.getJavaKind();
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return original.resolve(accessingClass);
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        return original.resolveMethod(method, callerType);
    }

    @Override
    public ResolvedJavaMethod resolveConcreteMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        return original.resolveConcreteMethod(method, callerType);
    }

    @Override
    public Assumptions.AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        return original.findUniqueConcreteMethod(method);
    }

    @Override
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        return original.getInstanceFields(includeSuperclasses);
    }

    @Override
    public ResolvedJavaField[] getStaticFields() {
        return original.getStaticFields();
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        return original.findInstanceFieldWithOffset(offset, expectedKind);
    }

    @Override
    public String getSourceFileName() {
        return original.getSourceFileName();
    }

    @Override
    public boolean isLocal() {
        return original.isLocal();
    }

    @Override
    public boolean isMember() {
        return original.isMember();
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        return original.getEnclosingType();
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        return original.getDeclaredConstructors();
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return original.getDeclaredMethods();
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        return original.getClassInitializer();
    }

    @Override
    public void link() {
        original.link();
    }

    @Override
    public boolean hasDefaultMethods() {
        return original.hasDefaultMethods();
    }

    @Override
    public boolean declaresDefaultMethods() {
        return original.declaresDefaultMethods();
    }

    @Override
    public boolean isCloneableWithAllocation() {
        return original.isCloneableWithAllocation();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return original.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return original.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return original.getDeclaredAnnotations();
    }
}
