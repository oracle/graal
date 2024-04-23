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
package com.oracle.svm.core.reflect.target;

import static com.oracle.svm.core.annotate.TargetElement.CONSTRUCTOR_NAME;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.util.VMError;

import sun.reflect.generics.repository.FieldRepository;

@TargetClass(value = Field.class)
public final class Target_java_lang_reflect_Field {
    /** Generic info is created on demand at run time. */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    private FieldRepository genericInfo;

    /** Field accessor and annotation objects are created on demand at image runtime. */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    Target_jdk_internal_reflect_FieldAccessor fieldAccessor;

    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    Target_jdk_internal_reflect_FieldAccessor overrideFieldAccessor;

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    Map<Class<? extends Annotation>, Annotation> declaredAnnotations;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = AnnotationsComputer.class) //
    byte[] annotations;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = FieldOffsetComputer.class) //
    public int offset;

    /** If non-null, the field was deleted via substitution and this string provides the reason. */
    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = FieldDeletionReasonComputer.class) //
    String deletedReason;

    @Alias //
    boolean override;

    @Alias //
    Target_java_lang_reflect_Field root;

    @Alias
    native Target_java_lang_reflect_Field copy();

    @Alias//
    native Target_jdk_internal_reflect_FieldAccessor acquireFieldAccessor();

    @Alias//
    public native Target_jdk_internal_reflect_FieldAccessor acquireOverrideFieldAccessor();

    @Alias
    @TargetElement(name = CONSTRUCTOR_NAME)
    @SuppressWarnings("hiding")
    native void constructor(Class<?> declaringClass, String name, Class<?> type, int modifiers, boolean trustedFinal, int slot, String signature, byte[] annotations);

    @Substitute
    Target_jdk_internal_reflect_FieldAccessor getFieldAccessor() {
        Target_jdk_internal_reflect_FieldAccessor accessor = fieldAccessor;
        if (accessor != null) {
            return accessor;
        }
        if (deletedReason != null) {
            Field field = SubstrateUtil.cast(this, Field.class);
            throw VMError.unsupportedFeature("Unsupported field " + field.getDeclaringClass().getTypeName() +
                            "." + field.getName() + " is reachable: " + deletedReason);
        }
        return acquireFieldAccessor();
    }

    @Substitute
    Target_jdk_internal_reflect_FieldAccessor getOverrideFieldAccessor() {
        Target_jdk_internal_reflect_FieldAccessor accessor = overrideFieldAccessor;
        if (accessor != null) {
            return accessor;
        }
        if (deletedReason != null) {
            Field field = SubstrateUtil.cast(this, Field.class);
            throw VMError.unsupportedFeature("Unsupported field " + field.getDeclaringClass().getTypeName() +
                            "." + field.getName() + " is reachable: " + deletedReason);
        }
        return acquireOverrideFieldAccessor();
    }

    @Substitute
    private byte[] getTypeAnnotationBytes0() {
        return SubstrateUtil.cast(this, Target_java_lang_reflect_AccessibleObject.class).typeAnnotations;
    }

    public static final class FieldDeletionReasonComputer implements FieldValueTransformerWithAvailability {
        @Override
        public ValueAvailability valueAvailability() {
            return ValueAvailability.AfterAnalysis;
        }

        @Override
        public Object transform(Object receiver, Object originalValue) {
            return ReflectionSubstitutionSupport.singleton().getDeletionReason((Field) receiver);
        }
    }

    static class AnnotationsComputer extends ReflectionMetadataComputer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            return ImageSingletons.lookup(EncodedRuntimeMetadataSupplier.class).getAnnotationsEncoding((AccessibleObject) receiver);
        }
    }
}
