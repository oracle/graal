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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.AnnotationWrapper;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Type which fully substitutes its original type, i.e. @{@link Substitute} on the class level.
 *
 * @see InjectedFieldsType
 */
public class SubstitutionType implements ResolvedJavaType, OriginalClassProvider, AnnotationWrapper {

    private final ResolvedJavaType original;
    private final ResolvedJavaType annotated;

    /**
     * This field is used in the {@link com.oracle.svm.hosted.SubstitutionReportFeature} class to
     * determine {@link SubstitutionType} objects which correspond to type.
     */
    private final boolean isUserSubstitution;

    private final ResolvedJavaField[][] instanceFields;

    public SubstitutionType(ResolvedJavaType original, ResolvedJavaType annotated, boolean isUserSubstitution) {
        this.annotated = annotated;
        this.original = original;
        this.isUserSubstitution = isUserSubstitution;
        this.instanceFields = new ResolvedJavaField[][]{annotated.getInstanceFields(false), annotated.getInstanceFields(true)};
    }

    public boolean isUserSubstitution() {
        return isUserSubstitution;
    }

    public ResolvedJavaType getOriginal() {
        return original;
    }

    public ResolvedJavaType getAnnotated() {
        return annotated;
    }

    @Override
    public ResolvedJavaType unwrapTowardsOriginalType() {
        return original;
    }

    void addInstanceField(ResolvedJavaField field) {
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
    public ResolvedJavaField[] getStaticFields() {
        return annotated.getStaticFields();
    }

    @Override
    public AnnotatedElement getAnnotationRoot() {
        return annotated;
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
        return getDeclaredConstructors(true);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        VMError.guarantee(forceLink == false, "only use getDeclaredConstructors without forcing to link, because linking can throw LinkageError");
        return annotated.getDeclaredConstructors(forceLink);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return getDeclaredMethods(true);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        VMError.guarantee(forceLink == false, "only use getDeclaredMethods without forcing to link, because linking can throw LinkageError");
        return annotated.getDeclaredMethods(forceLink);
    }

    @Override
    public List<ResolvedJavaMethod> getAllMethods(boolean forceLink) {
        VMError.guarantee(forceLink == false, "only use getAllMethods without forcing to link, because linking can throw LinkageError");
        return annotated.getAllMethods(forceLink);
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

    @SuppressWarnings("deprecation")
    @Override
    public ResolvedJavaType getHostClass() {
        return original.getHostClass();
    }

    @Override
    public String toString() {
        return "SubstitutionType<definition " + original.toString() + ", implementation " + annotated.toString() + ">";
    }
}
