/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.substitute;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.svm.hosted.c.GraalAccess;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstitutionType implements ResolvedJavaType, OriginalClassProvider {

    private final ResolvedJavaType original;
    private final ResolvedJavaType annotated;

    public SubstitutionType(ResolvedJavaType original, ResolvedJavaType annotated) {
        this.annotated = annotated;
        this.original = original;
    }

    public ResolvedJavaType getOriginal() {
        return original;
    }

    public ResolvedJavaType getAnnotated() {
        return annotated;
    }

    @Override
    public String getName() {
        return original.getName();
    }

    @Override
    public JavaKind getJavaKind() {
        return annotated.getJavaKind();
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    @Override
    public boolean hasFinalizer() {
        return annotated.hasFinalizer();
    }

    @Override
    public AssumptionResult<Boolean> hasFinalizableSubclass() {
        return annotated.hasFinalizableSubclass();
    }

    @Override
    public boolean isInterface() {
        return annotated.isInterface();
    }

    @Override
    public boolean isInstanceClass() {
        return annotated.isInstanceClass();
    }

    @Override
    public boolean isArray() {
        return annotated.isArray();
    }

    @Override
    public boolean isPrimitive() {
        return annotated.isPrimitive();
    }

    @Override
    public boolean isEnum() {
        return annotated.isEnum();
    }

    @Override
    public int getModifiers() {
        int result = annotated.getModifiers();
        if (!original.isLeaf()) {
            /*
             * Substitution classes are final on the source code level. But the original class can
             * be non-final and have subclasses, so we drop the final modifier unless the original
             * class is final.
             */
            result = result & ~Modifier.FINAL;
        }
        return result;
    }

    @Override
    public boolean isInitialized() {
        return annotated.isInitialized();
    }

    @Override
    public void initialize() {
        original.initialize();
        annotated.initialize();
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        return annotated.isAssignableFrom(other) || original.isAssignableFrom(other);
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        return annotated.isInstance(obj) || original.isInstance(obj);
    }

    @Override
    public ResolvedJavaType getSuperclass() {
        return annotated.getSuperclass();
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        return annotated.getInterfaces();
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        return annotated.findLeastCommonAncestor(otherType);
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        return annotated.getSingleImplementor();
    }

    @Override
    public AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        /* We don't want to speculate with substitutions. */
        return null;
    }

    @Override
    public ResolvedJavaType getComponentType() {
        return annotated.getComponentType();
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        return annotated.getArrayClass();
    }

    @Override
    public ResolvedJavaMethod resolveConcreteMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        /* First check the annotated class. @Substitute methods are found there. */
        ResolvedJavaMethod result = annotated.resolveConcreteMethod(method, callerType);
        if (result == null) {
            /* Then check the original class. @KeepOriginal methods are found there. */
            result = original.resolveConcreteMethod(method, callerType);
        }
        return result;
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        ResolvedJavaMethod result = annotated.resolveMethod(method, callerType);
        if (result == null) {
            result = original.resolveMethod(method, callerType);
        }
        return result;
    }

    @Override
    public AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        /* We don't want to speculate with substitutions. */
        return null;
    }

    @Override
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        return annotated.getInstanceFields(includeSuperclasses);
    }

    @Override
    public ResolvedJavaField[] getStaticFields() {
        return annotated.getStaticFields();
    }

    @Override
    public Annotation[] getAnnotations() {
        return annotated.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return annotated.getDeclaredAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return annotated.getAnnotation(annotationClass);
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        return annotated.findInstanceFieldWithOffset(offset, expectedKind);
    }

    @Override
    public String getSourceFileName() {
        return annotated.getSourceFileName();
    }

    @Override
    public boolean isLocal() {
        return annotated.isLocal();
    }

    @Override
    public boolean isMember() {
        return annotated.isMember();
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        return annotated.getEnclosingType();
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        return annotated.getDeclaredConstructors();
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return annotated.getDeclaredMethods();
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        return annotated.getClassInitializer();
    }

    @Override
    public boolean isLinked() {
        assert original.isLinked() && annotated.isLinked();
        return true;
    }

    @Override
    public void link() {
        assert isLinked();
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
        throw JVMCIError.unimplemented();
    }

    @Override
    public ResolvedJavaType getHostClass() {
        return original.getHostClass();
    }

    @Override
    public Class<?> getJavaClass() {
        return OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), original);
    }

    @Override
    public String toString() {
        return "SubstitutionType<definition " + original.toString() + ", implementation " + annotated.toString() + ">";
    }
}
