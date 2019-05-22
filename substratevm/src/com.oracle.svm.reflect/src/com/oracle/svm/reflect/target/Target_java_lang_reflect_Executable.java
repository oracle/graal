/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.target;

// Checkstyle: allow reflection

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Map;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK8OrEarlier;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@TargetClass(value = Executable.class)
public final class Target_java_lang_reflect_Executable {

    /**
     * The parameters field doesn't need a value recomputation. Its value is pre-loaded in the
     * {@link com.oracle.svm.reflect.hosted.ReflectionMetadataFeature}.
     */
    @Alias //
    Parameter[] parameters;

    /**
     * The declaredAnnotations field doesn't need a value recomputation. Its value is pre-loaded in
     * the {@link com.oracle.svm.reflect.hosted.ReflectionMetadataFeature}.
     */
    @Alias //
    Map<Class<? extends Annotation>, Annotation> declaredAnnotations;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = ParameterAnnotationsComputer.class) //
    Annotation[][] parameterAnnotations;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = AnnotatedReceiverTypeComputer.class) //
    AnnotatedType annotatedReceiverType;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = AnnotatedParameterTypesComputer.class) //
    AnnotatedType[] annotatedParameterTypes;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = AnnotatedReturnTypeComputer.class) //
    AnnotatedType annotatedReturnType;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = AnnotatedExceptionTypesComputer.class) //
    AnnotatedType[] annotatedExceptionTypes;

    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    native Target_java_lang_reflect_Executable getRoot();

    @Substitute
    private Parameter[] privateGetParameters() {
        Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
        return ReflectionHelper.requireNonNull(holder.parameters, "Parameters must be computed during native image generation");
    }

    @Substitute
    Map<Class<? extends Annotation>, Annotation> declaredAnnotations() {
        Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
        return ReflectionHelper.requireNonNull(holder.declaredAnnotations, "Annotations must be computed during native image generation");
    }

    @Substitute
    @SuppressWarnings("unused")
    Annotation[][] sharedGetParameterAnnotations(Class<?>[] parameterTypes, byte[] annotations) {
        Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
        return ReflectionHelper.requireNonNull(holder.parameterAnnotations, "Parameter annotations must be computed during native image generation");
    }

    @Substitute
    public AnnotatedType getAnnotatedReceiverType() {
        Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
        /* The annotatedReceiverType can be null. */
        return holder.annotatedReceiverType;
    }

    @Substitute
    public AnnotatedType[] getAnnotatedParameterTypes() {
        Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
        return ReflectionHelper.requireNonNull(holder.annotatedParameterTypes, "Annotated parameter types must be computed during native image generation");
    }

    @Substitute
    public AnnotatedType getAnnotatedReturnType0(@SuppressWarnings("unused") Type returnType) {
        Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
        return ReflectionHelper.requireNonNull(holder.annotatedReturnType, "Annotated return type must be computed during native image generation");
    }

    @Substitute
    public AnnotatedType[] getAnnotatedExceptionTypes() {
        Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
        return ReflectionHelper.requireNonNull(holder.annotatedExceptionTypes, "Annotated exception types must be computed during native image generation");
    }

    public static final class ParameterAnnotationsComputer implements CustomFieldValueComputer {

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Executable executable = (Executable) receiver;
            return executable.getParameterAnnotations();
        }
    }

    public static final class AnnotatedReceiverTypeComputer implements CustomFieldValueComputer {

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Executable executable = (Executable) receiver;
            return executable.getAnnotatedReceiverType();
        }
    }

    public static final class AnnotatedParameterTypesComputer implements CustomFieldValueComputer {

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Executable executable = (Executable) receiver;
            return executable.getAnnotatedParameterTypes();
        }
    }

    public static final class AnnotatedReturnTypeComputer implements CustomFieldValueComputer {

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Executable executable = (Executable) receiver;
            return executable.getAnnotatedReturnType();
        }
    }

    public static final class AnnotatedExceptionTypesComputer implements CustomFieldValueComputer {

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Executable executable = (Executable) receiver;
            return executable.getAnnotatedExceptionTypes();
        }
    }
}

@TargetClass(value = AccessibleObject.class)
final class Target_java_lang_reflect_AccessibleObject {
    @Alias //
    @TargetElement(onlyWith = JDK11OrLater.class)
    native Target_java_lang_reflect_AccessibleObject getRoot();
}
