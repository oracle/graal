/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.svm.hosted.c.GraalAccess;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class CustomSubstitutionType<F extends CustomSubstitutionField, M extends CustomSubstitutionMethod> implements ResolvedJavaType, OriginalClassProvider {

    protected final ResolvedJavaType original;
    protected final List<F> fields;
    protected final Map<ResolvedJavaMethod, M> methods;

    public CustomSubstitutionType(ResolvedJavaType original) {
        this.original = original;

        fields = new ArrayList<>();
        methods = new HashMap<>();
    }

    public ResolvedJavaType getOriginal() {
        return original;
    }

    public M getSubstitutionMethod(ResolvedJavaMethod method) {
        return methods.get(method);
    }

    public F getSubstitutionField(ResolvedJavaField field) {
        assert fields.size() > 0;

        for (F f : fields) {
            if (f.getName().equals(field.getName())) {
                return f;
            }
        }

        throw new IllegalArgumentException("No matching field foundf or " + field);
    }

    public void addSubstitutionMethod(ResolvedJavaMethod originalMethod, M substitution) {
        methods.put(originalMethod, substitution);
    }

    public void addSubstitutionField(F field) {
        fields.add(field);
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    @Override
    public boolean hasFinalizer() {
        return false;
    }

    @Override
    public AssumptionResult<Boolean> hasFinalizableSubclass() {
        return new AssumptionResult<>(false);
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isInstanceClass() {
        return true;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isPrimitive() {
        return false;
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
        return true;
    }

    @Override
    public void initialize() {
        assert isInitialized();
    }

    @Override
    public boolean isLinked() {
        return true;
    }

    @Override
    public void link() {
        assert isLinked();
    }

    @Override
    public boolean hasDefaultMethods() {
        assert !isInterface() : "only interfaces can have default methods";
        return false;
    }

    @Override
    public boolean declaresDefaultMethods() {
        assert !isInterface() : "only interfaces can have default methods";
        return false;
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
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        return fields.toArray(new ResolvedJavaField[fields.size()]);
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
        /* All of our instance fields are synthetic and do not exist in the hosted VM. */
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
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        return new ResolvedJavaMethod[0];
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return original.getDeclaredMethods();
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
    public ResolvedJavaType getHostClass() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public Class<?> getJavaClass() {
        return OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), original);
    }

}
