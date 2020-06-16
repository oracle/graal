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
import java.lang.reflect.Field;
import java.util.Arrays;

import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.meta.ReadableJavaField;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.GraalAccess;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class InjectedAccessorsField implements ReadableJavaField, OriginalFieldProvider {

    private final ResolvedJavaField original;

    private final InjectAccessors injectAccessors;

    public InjectedAccessorsField(ResolvedJavaField original, InjectAccessors injectAccessors) {
        this.original = original;
        this.injectAccessors = injectAccessors;
    }

    @Override
    public Annotation[] getAnnotations() {
        Annotation[] result = original.getAnnotations();
        result = Arrays.copyOf(result, result.length + 1);
        result[result.length - 1] = injectAccessors;
        return result;
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return getAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (InjectAccessors.class.isAssignableFrom(annotationClass)) {
            return annotationClass.cast(injectAccessors);
        }
        return original.getAnnotation(annotationClass);
    }

    @Override
    public JavaConstant readValue(JavaConstant receiver) {
        return ReadableJavaField.readFieldValue(GraalAccess.getOriginalProviders().getConstantReflection(), original, receiver);
    }

    @Override
    public boolean allowConstantFolding() {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public boolean injectFinalForRuntimeCompilation() {
        return ReadableJavaField.injectFinalForRuntimeCompilation(original);
    }

    /* The remaining methods just forward to the original field. */

    @Override
    public String getName() {
        return original.getName();
    }

    @Override
    public JavaType getType() {
        return original.getType();
    }

    @Override
    public int getModifiers() {
        return original.getModifiers();
    }

    @Override
    public int getOffset() {
        return original.getOffset();
    }

    @Override
    public boolean isInternal() {
        return original.isInternal();
    }

    @Override
    public boolean isSynthetic() {
        return original.isSynthetic();
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return original.getDeclaringClass();
    }

    @Override
    public String toString() {
        return "InjectedAccessorsField<original " + original.toString() + ">";
    }

    @Override
    public Field getJavaField() {
        return OriginalFieldProvider.getJavaField(GraalAccess.getOriginalSnippetReflection(), original);
    }
}
