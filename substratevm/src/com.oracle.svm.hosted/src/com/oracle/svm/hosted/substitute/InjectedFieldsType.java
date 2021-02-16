/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.svm.hosted.c.GraalAccess;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class InjectedFieldsType implements ResolvedJavaType, OriginalClassProvider {

    private final ResolvedJavaType original;

    private final ResolvedJavaField[][] instanceFields;

    public InjectedFieldsType(ResolvedJavaType original) {
        this.original = original;

        this.instanceFields = new ResolvedJavaField[][]{original.getInstanceFields(false), original.getInstanceFields(true)};
    }

    public ResolvedJavaType getOriginal() {
        return original;
    }

    void addInjectedField(ResolvedJavaField field) {
        for (int i = 0; i < instanceFields.length; i++) {
            ResolvedJavaField[] newFields = Arrays.copyOf(instanceFields[i], instanceFields[i].length + 1, ResolvedJavaField[].class);
            newFields[newFields.length - 1] = field;
            instanceFields[i] = newFields;
        }
    }

    @Override
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        return instanceFields[includeSuperclasses ? 1 : 0];
    }

    @Override
    public String getName() {
        return original.getName();
    }

    @Override
    public JavaKind getJavaKind() {
        return original.getJavaKind();
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
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
    public boolean isArray() {
        return original.isArray();
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
    public int getModifiers() {
        return original.getModifiers();
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
    public boolean isAssignableFrom(ResolvedJavaType other) {
        return original.isAssignableFrom(other);
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
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        return original.findLeastCommonAncestor(otherType);
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        return original.getSingleImplementor();
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
    public ResolvedJavaType getArrayClass() {
        return original.getArrayClass();
    }

    @Override
    public ResolvedJavaMethod resolveConcreteMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        return original.resolveConcreteMethod(method, callerType);
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        return original.resolveMethod(method, callerType);
    }

    @Override
    public AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        return original.findUniqueConcreteMethod(method);
    }

    @Override
    public ResolvedJavaField[] getStaticFields() {
        return original.getStaticFields();
    }

    @Override
    public Annotation[] getAnnotations() {
        return original.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return original.getDeclaredAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return original.getAnnotation(annotationClass);
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
        return "InjectedFieldsType<" + original.toString() + ">";
    }
}
