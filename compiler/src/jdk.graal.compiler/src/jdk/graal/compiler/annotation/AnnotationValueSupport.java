/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.annotation;

import static jdk.graal.compiler.core.common.NativeImageSupport.inRuntimeCode;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

/**
 * Support for parsing class file annotation attributes.
 */
public class AnnotationValueSupport {

    /**
     * Gets the annotation directly present on {@code annotated} whose type is
     * {@code annotationType}. Class initialization is not triggered for enum types referenced by
     * the returned annotation. This method ignores inherited annotations.
     *
     * @param annotationType the type object corresponding to the annotation interface type
     * @return {@code annotated}'s annotation for the specified annotation type if directly present
     *         on this element, else null
     * @throws IllegalArgumentException if {@code annotationType} is not an annotation interface
     *             type
     */
    public static AnnotationValue getDeclaredAnnotationValue(ResolvedJavaType annotationType, Annotated annotated) {
        if (!annotationType.isAnnotation()) {
            throw new IllegalArgumentException(annotationType.toJavaName() + " is not an annotation interface");
        }
        return getDeclaredAnnotationValues(annotated).get(annotationType);
    }

    /**
     * Gets the annotations directly present on {@code annotated}. Class initialization is not
     * triggered for enum types referenced by the returned annotations. This method ignores
     * inherited annotations.
     *
     * @return an immutable map from annotation type to annotation of the annotations directly
     *         present on this element
     */
    public static Map<ResolvedJavaType, AnnotationValue> getDeclaredAnnotationValues(Annotated annotated) {
        return annotated.getDeclaredAnnotationInfo(ANNOTATIONS_INFO_PARSER).values;
    }

    /**
     * Result type returned by {@link AnnotationValueSupport#ANNOTATIONS_INFO_PARSER}.
     *
     * @param values the parsed annotations
     * @param container used to resolve type names in the annotations
     */
    public record ParsedDeclaredAnnotationValues(Map<ResolvedJavaType, AnnotationValue> values, ResolvedJavaType container) {
        public static ParsedDeclaredAnnotationValues NONE = new ParsedDeclaredAnnotationValues(CollectionsUtil.mapOf(), null);
    }

    public static final Function<AnnotationsInfo, ParsedDeclaredAnnotationValues> ANNOTATIONS_INFO_PARSER = info -> {
        if (info == null) {
            return ParsedDeclaredAnnotationValues.NONE;
        }
        ResolvedJavaType container = info.container();
        return new ParsedDeclaredAnnotationValues(AnnotationValueParser.parseAnnotations(info.bytes(), info.constPool(), container), container);
    };

    /**
     * Gets the type annotations for {@code annotated} that back the implementation of
     * {@link Method#getAnnotatedReturnType()}, {@link Method#getAnnotatedReceiverType()},
     * {@link Method#getAnnotatedExceptionTypes()}, {@link Method#getAnnotatedParameterTypes()},
     * {@link Field#getAnnotatedType()}, {@link RecordComponent#getAnnotatedType()},
     * {@link Class#getAnnotatedSuperclass()} or {@link Class#getAnnotatedInterfaces()}. This method
     * returns an empty list if there are no type annotations.
     */
    public static List<TypeAnnotationValue> getTypeAnnotationValues(Annotated annotated) {
        AnnotationsInfo info = annotated.getTypeAnnotationInfo();
        if (info == null) {
            return List.of();
        }
        return TypeAnnotationValueParser.parseTypeAnnotations(info.bytes(), info.constPool(), info.container());
    }

    /**
     * Result type returned by {@link AnnotationValueSupport#getParameterAnnotationValues}.
     *
     * @param values the parsed annotations. This is an immutable list of lists of
     *            {@link AnnotationValue}s that represents the
     *            {@code RuntimeVisibleParameterAnnotations} for a method. Note that this differs
     *            from {@link Method#getParameterAnnotations()} in that it excludes entries for
     *            synthetic and mandated parameters.
     * @param container used to resolve type names in {@code values}
     */
    public record ParsedParameterAnnotationValues(List<List<AnnotationValue>> values, ResolvedJavaType container) {
    }

    /**
     * Returns the result of parsing the parameter annotations for {@code method}.
     *
     * @return null if {@code method} has no parameter annotations
     */
    public static ParsedParameterAnnotationValues getParameterAnnotationValues(ResolvedJavaMethod method) {
        AnnotationsInfo info = method.getParameterAnnotationInfo();
        if (info == null) {
            return null;
        }
        ResolvedJavaType container = info.container();
        return new ParsedParameterAnnotationValues(AnnotationValueParser.parseParameterAnnotations(info.bytes(), info.constPool(), container), container);
    }

    /**
     * Returns the default value for the annotation member represented by {@code method}. Returns
     * null if no default is associated with {@code method}, or if {@code method} does not represent
     * a declared member of an annotation type.
     *
     * @return the default value for the annotation member represented by this object. The type of
     *         the returned value is specified by {@link AnnotationValue#get}
     * @see Method#getDefaultValue()
     */
    public static Object getAnnotationDefaultValue(ResolvedJavaMethod method) {
        AnnotationsInfo info = method.getAnnotationDefaultInfo();
        if (info == null) {
            return null;
        }
        ResolvedJavaType container = info.container();
        ResolvedJavaType memberType = method.getSignature().getReturnType(container).resolve(container);
        return AnnotationValueParser.parseMemberValue(memberType, ByteBuffer.wrap(info.bytes()), info.constPool(), container);
    }

    /**
     * Gets the annotation value of type {@code annotationType} present on {@code annotated}. This
     * method must only be called in jargraal as it requires the ability convert a {@link Class}
     * value to a {@link ResolvedJavaType} value.
     */
    @LibGraalSupport.HostedOnly
    public static AnnotationValue getAnnotationValue(Annotated annotated, Class<? extends Annotation> annotationType) {
        if (inRuntimeCode()) {
            throw new GraalError("Cannot look up %s annotation at Native Image runtime", annotationType.getName());
        }
        boolean inherited = annotationType.getAnnotation(Inherited.class) != null;
        return getAnnotationValue0(annotated, annotationType, inherited);
    }

    /**
     * Looks for an entry in {@code values} whose {@linkplain AnnotationValue#getAnnotationType()
     * type} matches {@code annotationType}.
     *
     * @param container used to resolve type names in {@code values}
     */
    @LibGraalSupport.HostedOnly
    public static AnnotationValue findAnnotationValue(List<AnnotationValue> values, Class<? extends Annotation> annotationType, ResolvedJavaType container) {
        if (inRuntimeCode()) {
            throw new GraalError("Cannot look up %s annotation at Native Image runtime", annotationType.getName());
        }
        String internalName = "L" + annotationType.getName().replace(".", "/") + ";";
        for (var e : values) {
            ResolvedJavaType type = e.getAnnotationType();
            if (type.getName().equals(internalName)) {
                // The name matches so now double-check the resolved type matches
                if (UnresolvedJavaType.create(internalName).resolve(container).equals(type)) {
                    return e;
                }
            }
        }
        return null;
    }

    @LibGraalSupport.HostedOnly
    private static AnnotationValue getAnnotationValue0(Annotated annotated, Class<? extends Annotation> annotationType, boolean inherited) {
        ParsedDeclaredAnnotationValues parsed = annotated.getDeclaredAnnotationInfo(ANNOTATIONS_INFO_PARSER);
        if (parsed.values == null && !inherited) {
            return null;
        }

        AnnotationValue res = lookup(annotationType, parsed.values, parsed.container);
        if (res != null) {
            return res;
        }
        if (inherited && annotated instanceof ResolvedJavaType type && !type.isJavaLangObject()) {
            ResolvedJavaType superclass = type.getSuperclass();
            return getAnnotationValue0(superclass, annotationType, true);
        }
        return null;
    }

    /**
     * Looks up the annotation value of type {@code annotationType} in {@code map}.
     *
     * @param annotationType the type of annotation to look up
     * @param map the map of annotation values to search
     * @param container used to resolve the annotation type
     * @return the annotation value if found, or null if not found
     */
    @LibGraalSupport.HostedOnly
    private static AnnotationValue lookup(Class<? extends Annotation> annotationType, Map<ResolvedJavaType, AnnotationValue> map, ResolvedJavaType container) {
        String internalName = "L" + annotationType.getName().replace(".", "/") + ";";
        for (var e : map.entrySet()) {
            ResolvedJavaType type = e.getKey();
            if (type.getName().equals(internalName)) {
                // The name matches so now double-check the resolved type matches
                if (UnresolvedJavaType.create(internalName).resolve(container).equals(type)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }
}
