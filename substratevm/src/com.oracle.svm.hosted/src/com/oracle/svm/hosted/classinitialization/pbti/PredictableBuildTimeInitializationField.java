/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization.pbti;

import java.lang.reflect.Modifier;

import com.oracle.svm.util.InterceptedField;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaField;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.OriginalFieldProvider;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

/**
 * Field implementing {@link InterceptedField} to be able to read field without
 * accessing analysis elements in {@link com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport}.
 */
public final class PredictableBuildTimeInitializationField implements ResolvedJavaField, WrappedJavaField, OriginalFieldProvider, InterceptedField {

    private final PredictableBuildTimeInitializationLookupLayer lookupLayer;
    private final ResolvedJavaField wrapped;
    private final PredictableBuildTimeInitializationType declaringClass;
    private final PredictableBuildTimeInitializationType fieldType;

    private volatile Object fieldValueInterceptor;

    PredictableBuildTimeInitializationField(PredictableBuildTimeInitializationLookupLayer lookupLayer, ResolvedJavaField wrapped) {
        this.lookupLayer = lookupLayer;
        this.wrapped = wrapped;
        this.declaringClass = lookupLayer.lookup(wrapped.getDeclaringClass());
        this.fieldType = resolveFieldType(lookupLayer, wrapped);
    }

    PredictableBuildTimeInitializationLookupLayer getLookupLayer() {
        return lookupLayer;
    }

    @Override
    public ResolvedJavaField getWrapped() {
        return wrapped;
    }

    @Override
    public ResolvedJavaField unwrapTowardsOriginalField() {
        return wrapped;
    }

    @Override
    public Object getFieldValueInterceptor() {
        return fieldValueInterceptor;
    }

    @Override
    public void setFieldValueInterceptor(Object fieldValueInterceptor) {
        this.fieldValueInterceptor = fieldValueInterceptor;
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    @Override
    public PredictableBuildTimeInitializationType getType() {
        return fieldType;
    }

    @Override
    public int getModifiers() {
        return wrapped.getModifiers();
    }

    @Override
    public int getOffset() {
        return wrapped.getOffset();
    }

    @Override
    public boolean isInternal() {
        return wrapped.isInternal();
    }

    @Override
    public boolean isSynthetic() {
        return wrapped.isSynthetic();
    }

    @Override
    public PredictableBuildTimeInitializationType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public JavaConstant getConstantValue() {
        return wrapped.getConstantValue();
    }

    @Override
    public JavaKind getStorageKind() {
        return fieldType.getStorageKind();
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(getModifiers());
    }

    @Override
    public void beforeFieldValueAccess() {
        lookupLayer.hostVM().beforeFieldValueAccess(declaringClass);
    }

    @Override
    public String toString() {
        return "PBTIField<" + format("%h.%n") + " -> " + wrapped + ">";
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public int hashCode() {
        return wrapped.hashCode();
    }

    @Override
    public Annotated getWrappedAnnotated() {
        return wrapped;
    }

    /**
     * Resolve field type similarly to {@code com.oracle.graal.pointsto.meta.AnalysisField#getDeclaredType}.
     */
    private static PredictableBuildTimeInitializationType resolveFieldType(PredictableBuildTimeInitializationLookupLayer lookupLayer, ResolvedJavaField field) {
        try {
            JavaType type = field.getType();
            ResolvedJavaType resolvedType = type.resolve(OriginalClassProvider.getOriginalType(field.getDeclaringClass()));
            PredictableBuildTimeInitializationType fieldType = lookupLayer.lookup(resolvedType);
            return fieldType != null ? fieldType : lookupLayer.objectType();
        } catch (LinkageError e) {
            return lookupLayer.objectType();
        }
    }
}
