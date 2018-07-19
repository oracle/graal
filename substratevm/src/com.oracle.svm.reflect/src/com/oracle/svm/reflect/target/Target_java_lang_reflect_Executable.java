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
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Map;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.reflect.hosted.ReflectionFeature;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@TargetClass(value = Executable.class, onlyWith = ReflectionFeature.IsEnabled.class)
public final class Target_java_lang_reflect_Executable {

    public static final class ParameterAnnotationsComputer implements CustomFieldValueComputer {

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Executable executable = (Executable) receiver;
            return executable.getParameterAnnotations();
        }
    }

    /**
     * The declaredAnnotations field doesn't need a value recomputation. Its value is pre-loaded in
     * the {@link com.oracle.svm.reflect.hosted.ReflectionMetadataFeature}.
     */
    @Alias //
    Map<Class<? extends Annotation>, Annotation> declaredAnnotations;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = ParameterAnnotationsComputer.class) //
    Annotation[][] parameterAnnotations;

    /**
     * The parameters field doesn't need a value recomputation. Its value is pre-loaded in the
     * {@link com.oracle.svm.reflect.hosted.ReflectionMetadataFeature}.
     */
    @Alias //
    Parameter[] parameters;

    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    native Target_java_lang_reflect_Executable getRoot();

    @Substitute
    Map<Class<? extends Annotation>, Annotation> declaredAnnotations() {
        Target_java_lang_reflect_Executable holder = getRoot();
        if (holder == null) {
            holder = this;
        }

        if (holder.declaredAnnotations == null) {
            throw VMError.shouldNotReachHere("Annotations must be computed during native image generation");
        }
        return holder.declaredAnnotations;
    }

    @Substitute
    @SuppressWarnings("unused")
    Annotation[][] sharedGetParameterAnnotations(Class<?>[] parameterTypes, byte[] annotations) {
        Target_java_lang_reflect_Executable holder = getRoot();
        if (holder == null) {
            holder = this;
        }

        if (holder.parameterAnnotations == null) {
            throw VMError.shouldNotReachHere("Parameter annotations must be computed during native image generation");
        }
        return holder.parameterAnnotations;
    }

    @Substitute
    private Parameter[] privateGetParameters() {
        Target_java_lang_reflect_Executable holder = getRoot();
        if (holder == null) {
            holder = this;
        }

        if (holder.parameters == null) {
            throw VMError.shouldNotReachHere("Parameters must be computed during native image generation");
        }
        return holder.parameters;
    }
}
