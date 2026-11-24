/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.reflect.target;

import static com.oracle.svm.core.annotate.TargetElement.CONSTRUCTOR_NAME;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.guest.staging.layered.LayerIdComputer;
import com.oracle.svm.guest.staging.reflect.RuntimeMetadataDecoder;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.TypeAnnotation;
import sun.reflect.annotation.TypeAnnotationParser;
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

    /**
     * The layer that encoded the annotation metadata, which may differ from the declaring class's
     * layer.
     */
    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = LayerIdComputer.class) //
    public int layerId;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClassName = "com.oracle.svm.core.reflect.target.ReflectionFieldValueComputers$FieldOffsetComputer") //
    public int offset;

    /**
     * If a static field and building a layered image, stores the layer the field was installed in.
     */
    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClassName = "com.oracle.svm.core.reflect.target.ReflectionFieldValueComputers$LayerNumberComputer") //
    public int installedLayerNumber;

    /** If non-null, the field was deleted via substitution and this string provides the reason. */
    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClassName = "com.oracle.svm.core.reflect.target.ReflectionFieldValueComputers$FieldDeletionReasonComputer") //
    String deletedReason;

    @Alias //
    boolean override;

    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) //
    public int modifiers;

    @Alias //
    public Target_java_lang_reflect_Field root;

    @Alias
    native Target_java_lang_reflect_Field copy();

    /**
     * The JDK uses the declaring class's constant pool. A later layer can register fields of a
     * base-layer class, so annotation indexes must instead use the field metadata's layer.
     */
    @Substitute
    @TargetElement(onlyWith = ReflectionSupportPredicates.BuildingImageLayer.class)
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.6-b27/src/java.base/share/classes/java/lang/reflect/Field.java#L1267-L1287")
    private Map<Class<? extends Annotation>, Annotation> declaredAnnotations() {
        Map<Class<? extends Annotation>, Annotation> declAnnos;
        if ((declAnnos = declaredAnnotations) == null) {
            synchronized (this) {
                if ((declAnnos = declaredAnnotations) == null) {
                    if (root != null) {
                        declAnnos = root.declaredAnnotations();
                    } else {
                        Field field = SubstrateUtil.cast(this, Field.class);
                        Class<?> declaringClass = field.getDeclaringClass();
                        declAnnos = AnnotationParser.parseAnnotations(annotations, ReflectionHelper.getAnnotationConstantPool(declaringClass, layerId), declaringClass);
                    }
                    declaredAnnotations = declAnnos;
                }
            }
        }
        return declAnnos;
    }

    /** Type annotations use the same layer-local lookup tables as declaration annotations. */
    @Substitute
    @TargetElement(onlyWith = ReflectionSupportPredicates.BuildingImageLayer.class)
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.6-b27/src/java.base/share/classes/java/lang/reflect/Field.java#L1299-L1307")
    public AnnotatedType getAnnotatedType() {
        Field field = SubstrateUtil.cast(this, Field.class);
        Class<?> declaringClass = field.getDeclaringClass();
        return TypeAnnotationParser.buildAnnotatedType(getTypeAnnotationBytes0(), ReflectionHelper.getAnnotationConstantPool(declaringClass, layerId), field, declaringClass, field.getGenericType(),
                        TypeAnnotation.TypeAnnotationTarget.FIELD);
    }

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

    @Substitute
    public int getModifiers() {
        return RuntimeMetadataDecoder.clearInternalModifiers(modifiers);
    }

    static class AnnotationsComputer extends ReflectionMetadataComputer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            return ImageSingletons.lookup(EncodedRuntimeMetadataSupplier.class).getAnnotationsEncoding((AccessibleObject) receiver);
        }
    }

}
