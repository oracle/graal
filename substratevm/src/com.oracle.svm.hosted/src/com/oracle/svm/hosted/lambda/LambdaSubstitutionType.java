/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.lambda;

import java.lang.annotation.Annotation;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.hosted.c.GraalAccess;

import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Simply changes the name of Lambdas from a random ID into a stable name.
 */
public class LambdaSubstitutionType implements ResolvedJavaType, OriginalClassProvider {
    private final ResolvedJavaType original;
    private final String stableName;

    @SuppressWarnings("try")
    LambdaSubstitutionType(ResolvedJavaType original, String stableName) {
        this.original = original;
        this.stableName = stableName;
    }

    @Override
    public String getName() {
        return stableName;
    }

    @Override
    public Annotation[] getAnnotations() {
        return InternalVMMethod.Holder.ARRAY;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return annotationClass == InternalVMMethod.class;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (annotationClass == InternalVMMethod.class) {
            return annotationClass.cast(InternalVMMethod.Holder.INSTANCE);
        }
        return null;
    }

    @Override
    public boolean hasFinalizer() {
        return original.hasFinalizer();
    }

    @Override
    public AssumptionResult<Boolean> hasFinalizableSubclass() {
        return original.hasFinalizableSubclass();
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
    public boolean isLeaf() {
        return original.isLeaf();
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
    public boolean isAssignableFrom(ResolvedJavaType other) {
        return original.isAssignableFrom(other);
    }

    @Override
    public ResolvedJavaType getHostClass() {
        return original.getHostClass();
    }

    @Override
    public boolean isJavaLangObject() {
        return original.isJavaLangObject();
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
    public AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        return original.findLeafConcreteSubtype();
    }

    @Override
    public ResolvedJavaType getComponentType() {
        return original.getComponentType();
    }

    @Override
    public ResolvedJavaType getElementalType() {
        return original.getElementalType();
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        return original.getArrayClass();
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
    public AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
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
    public ResolvedJavaMethod findMethod(String name, Signature signature) {
        return original.findMethod(name, signature);
    }

    @Override
    public boolean isCloneableWithAllocation() {
        return original.isCloneableWithAllocation();
    }

    @Override
    public ResolvedJavaType lookupType(UnresolvedJavaType unresolvedJavaType, boolean resolve) {
        return original.lookupType(unresolvedJavaType, resolve);
    }

    @Override
    public ResolvedJavaField resolveField(UnresolvedJavaField unresolvedJavaField, ResolvedJavaType accessingClass) {
        return original.resolveField(unresolvedJavaField, accessingClass);
    }

    @Override
    public String getUnqualifiedName() {
        return original.getUnqualifiedName();
    }

    @Override
    public boolean isArray() {
        return original.isArray();
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
    public String toJavaName() {
        return original.toJavaName();
    }

    @Override
    public String toJavaName(boolean qualified) {
        return original.toJavaName(qualified);
    }

    @Override
    public String toClassName() {
        return original.toClassName();
    }

    @Override
    public int getModifiers() {
        return original.getModifiers();
    }

    @Override
    public boolean isSynchronized() {
        return original.isSynchronized();
    }

    @Override
    public boolean isStatic() {
        return original.isStatic();
    }

    @Override
    public boolean isFinalFlagSet() {
        return original.isFinalFlagSet();
    }

    @Override
    public boolean isPublic() {
        return original.isPublic();
    }

    @Override
    public boolean isPackagePrivate() {
        return original.isPackagePrivate();
    }

    @Override
    public boolean isPrivate() {
        return original.isPrivate();
    }

    @Override
    public boolean isProtected() {
        return original.isProtected();
    }

    @Override
    public boolean isTransient() {
        return original.isTransient();
    }

    @Override
    public boolean isStrict() {
        return original.isStrict();
    }

    @Override
    public boolean isVolatile() {
        return original.isVolatile();
    }

    @Override
    public boolean isNative() {
        return original.isNative();
    }

    @Override
    public boolean isAbstract() {
        return original.isAbstract();
    }

    @Override
    public boolean isConcrete() {
        return original.isConcrete();
    }

    @Override
    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        return original.getAnnotationsByType(annotationClass);
    }

    @Override
    public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
        return original.getDeclaredAnnotation(annotationClass);
    }

    @Override
    public <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationClass) {
        return original.getDeclaredAnnotationsByType(annotationClass);
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return original.getDeclaredAnnotations();
    }

    public ResolvedJavaType getOriginal() {
        return original;
    }

    @Override
    public Class<?> getJavaClass() {
        return OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), original);
    }
}
