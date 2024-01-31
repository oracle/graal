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
package com.oracle.svm.hosted.substitute;

import java.lang.reflect.AnnotatedElement;

import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.svm.hosted.ameta.ReadableJavaField;
import com.oracle.svm.hosted.annotation.AnnotationWrapper;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstitutionField implements ReadableJavaField, OriginalFieldProvider, AnnotationWrapper {

    private final ResolvedJavaField original;
    private final ResolvedJavaField annotated;

    /**
     * This field is used in the {@link com.oracle.svm.hosted.SubstitutionReportFeature} class to
     * determine {@link SubstitutionMethod} objects which correspond to annotated substitutions.
     */
    private final boolean isUserSubstitution;

    public SubstitutionField(ResolvedJavaField original, ResolvedJavaField annotated, boolean isUserSubstitution) {
        this.original = original;
        this.annotated = annotated;
        this.isUserSubstitution = isUserSubstitution;
    }

    @Override
    public boolean isValueAvailable() {
        return true;
    }

    @Override
    public boolean injectFinalForRuntimeCompilation() {
        return false;
    }

    @Override
    public JavaConstant readValue(ClassInitializationSupport classInitializationSupport, JavaConstant receiver) {
        /* First try reading the value using the original field. */
        JavaConstant value = ReadableJavaField.readFieldValue(classInitializationSupport, original, receiver);
        if (value == null) {
            /*
             * If the original field didn't yield a value, try reading using the annotated field.
             * The value can be null only if the receiver doesn't contain the field.
             */
            value = ReadableJavaField.readFieldValue(classInitializationSupport, annotated, receiver);
        }
        return value;
    }

    public boolean isUserSubstitution() {
        return isUserSubstitution;
    }

    public ResolvedJavaField getOriginal() {
        return original;
    }

    public ResolvedJavaField getAnnotated() {
        return annotated;
    }

    @Override
    public int getModifiers() {
        return annotated.getModifiers();
    }

    @Override
    public int getOffset() {
        return annotated.getOffset();
    }

    @Override
    public boolean isInternal() {
        return annotated.isInternal();
    }

    @Override
    public boolean isSynthetic() {
        return annotated.isSynthetic();
    }

    @Override
    public String getName() {
        return annotated.getName();
    }

    @Override
    public JavaType getType() {
        return annotated.getType();
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return annotated.getDeclaringClass();
    }

    @Override
    public AnnotatedElement getAnnotationRoot() {
        return annotated;
    }

    @Override
    public ResolvedJavaField unwrapTowardsOriginalField() {
        return original;
    }

    @Override
    public JavaConstant getConstantValue() {
        return original.getConstantValue();
    }
}
