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
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.util.Map;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.reflect.hosted.AccessorComputer;
import com.oracle.svm.reflect.hosted.FieldOffsetComputer;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import sun.reflect.generics.repository.FieldRepository;

@TargetClass(value = Field.class)
public final class Target_java_lang_reflect_Field {

    @Alias FieldRepository genericInfo;

    @Alias //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = AccessorComputer.class) //
    Target_jdk_internal_reflect_FieldAccessor fieldAccessor;
    @Alias //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = AccessorComputer.class) //
    Target_jdk_internal_reflect_FieldAccessor overrideFieldAccessor;

    /**
     * The declaredAnnotations field doesn't need a value recomputation. Its value is pre-loaded in
     * the {@link com.oracle.svm.reflect.hosted.ReflectionMetadataFeature}.
     */
    @Alias //
    Map<Class<? extends Annotation>, Annotation> declaredAnnotations;

    @Alias //
    Target_java_lang_reflect_Field root;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = FieldOffsetComputer.class) //
    int offset;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = AnnotatedTypeComputer.class) //
    AnnotatedType annotatedType;

    @Alias
    native Target_java_lang_reflect_Field copy();

    @Substitute
    Target_jdk_internal_reflect_FieldAccessor acquireFieldAccessor(@SuppressWarnings("unused") boolean overrideFinalCheck) {
        if (fieldAccessor == null) {
            throw VMError.unsupportedFeature("Runtime reflection is not supported.");
        }
        return fieldAccessor;
    }

    @Substitute
    Map<Class<? extends Annotation>, Annotation> declaredAnnotations() {
        Target_java_lang_reflect_Field holder = ReflectionHelper.getHolder(this);
        return ReflectionHelper.requireNonNull(holder.declaredAnnotations, "Declared annotations must be computed during native image generation.");
    }

    @Substitute
    public AnnotatedType getAnnotatedType() {
        Target_java_lang_reflect_Field holder = ReflectionHelper.getHolder(this);
        return ReflectionHelper.requireNonNull(holder.annotatedType, "Annotated type must be computed during native image generation.");
    }

    public static final class AnnotatedTypeComputer implements CustomFieldValueComputer {
        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Field field = (Field) receiver;
            return field.getAnnotatedType();
        }
    }

}
