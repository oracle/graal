/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.lang.annotation.Inherited;
import java.lang.reflect.Array;
import java.lang.reflect.GenericSignatureFormatError;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueParser;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.annotation.ElementTypeMismatch;
import jdk.graal.compiler.annotation.EnumElement;
import jdk.graal.compiler.annotation.MissingType;
import jdk.graal.compiler.annotation.TypeAnnotationValue;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationSupport;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

/**
 * Provides methods to query annotation information on {@link Annotated} objects. Caches are
 * employed to speed up most queries.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class AnnotatedObjectAccess {

    /**
     * Gets the annotation of type {@code annotationType} from {@code element}.
     *
     * @param element the annotated element to retrieve the annotation value from
     * @param annotationType the type of annotation to retrieve
     * @return the annotation value of the specified type, or null if no such annotation exists
     */
    public <T extends Annotation> T getAnnotation(Annotated element, Class<T> annotationType) {
        // Checkstyle: allow direct annotation access
        Inherited inherited = annotationType.getAnnotation(Inherited.class);
        // Checkstyle: disallow direct annotation access
        Map<ResolvedJavaType, AnnotationValue> annotationValues = getAnnotationValues(element, inherited == null);
        AnnotationValue annotationValue = annotationValues.get(GraalAccess.lookupType(annotationType));
        if (annotationValue != null) {
            return asAnnotation(annotationValue, annotationType);
        }
        return null;
    }

    /**
     * Gets the annotation of type {@code annotationType} from {@code annotated}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AnnotationValue toAnnotationValue(Annotation annotation) {
        ResolvedJavaType type = GraalAccess.lookupType(annotation.annotationType());
        Map<String, Object> values = AnnotationSupport.memberValues(annotation);
        Map.Entry<String, Object>[] elements = new Map.Entry[values.size()];
        int i = 0;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String name = e.getKey();
            Object aElement = e.getValue();
            Object avElement = toAnnotationValueElement(aElement);
            elements[i++] = Map.entry(name, avElement);
        }
        return new AnnotationValue(type, CollectionsUtil.mapOfEntries(elements));
    }

    private static final Class<?> AnnotationTypeMismatchExceptionProxy = ReflectionUtil.lookupClass("sun.reflect.annotation.AnnotationTypeMismatchExceptionProxy");

    /**
     * Converts an annotation element value from its core reflection representation to its JVMCI
     * representation. That is, this method converts a value as found in the map returned by
     * {@link AnnotationSupport#memberValues(Annotation)} to the corresponding value in the map
     * returned by {@link AnnotationValue#getElements()}.
     * <p>
     * This is the inverse of the conversion performed by
     * {@link #toAnnotationElement(Class, Object)}.
     *
     * @param aElement core reflection representation of an annotation element value
     * @return the JVMCI representation of the same value
     */
    private static Object toAnnotationValueElement(Object aElement) {
        return switch (aElement) {
            case Enum<?> ev -> new EnumElement(GraalAccess.lookupType(aElement.getClass()), ev.name());
            case Class<?> cls -> GraalAccess.lookupType(cls);
            case Annotation a -> toAnnotationValue(a);
            case TypeNotPresentExceptionProxy proxy -> new MissingType(proxy.typeName(), proxy.getCause());
            default -> {
                Class<?> valueType = aElement.getClass();
                if (valueType.isArray()) {
                    int length = Array.getLength(aElement);
                    Object[] array = new Object[length];
                    for (int i = 0; i < length; i++) {
                        array[i] = toAnnotationValueElement(Array.get(aElement, i));
                    }
                    yield List.of(array);
                } else if (AnnotationTypeMismatchExceptionProxy.isInstance(aElement)) {
                    String foundType = ReflectionUtil.readField(AnnotationTypeMismatchExceptionProxy, "foundType", aElement);
                    yield new ElementTypeMismatch(foundType);
                } else {
                    yield aElement;
                }
            }
        };
    }

    /**
     * Converts an annotation element value from its JVMCI representation to its core reflection
     * representation. That is, this method converts a value as found in the map returned by
     * {@link AnnotationValue#getElements()} to the corresponding value in the map returned by
     * {@link AnnotationSupport#memberValues(Annotation)}.
     * <p>
     * This is the inverse of the conversion performed by {@link #toAnnotationValueElement(Object)}.
     *
     * @param returnType the return type of the method representing the annotation element whose
     *            value is being converted
     * @param avElement the annotation element value to convert
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object toAnnotationElement(Class<?> returnType, Object avElement) {
        switch (avElement) {
            case EnumElement ee -> {
                Class<? extends Enum> enumType = (Class<? extends Enum>) OriginalClassProvider.getJavaClass(ee.enumType);
                return Enum.valueOf(enumType, ee.name);
            }
            case ResolvedJavaType rjt -> {
                return OriginalClassProvider.getJavaClass(rjt);
            }
            case AnnotationValue av -> {
                Class<? extends Annotation> type = (Class<? extends Annotation>) OriginalClassProvider.getJavaClass(av.getAnnotationType());
                return toAnnotation0(av, type);
            }
            case List adList -> {
                int length = adList.size();
                if (returnType == byte[].class) {
                    byte[] result = new byte[length];
                    for (int i = 0; i < length; i++) {
                        result[i] = (byte) adList.get(i);
                    }
                    return result;
                }
                if (returnType == char[].class) {
                    char[] result = new char[length];
                    for (int i = 0; i < length; i++) {
                        result[i] = (char) adList.get(i);
                    }
                    return result;
                }
                if (returnType == short[].class) {
                    short[] result = new short[length];
                    for (int i = 0; i < length; i++) {
                        result[i] = (short) adList.get(i);
                    }
                    return result;
                }
                if (returnType == int[].class) {
                    int[] result = new int[length];
                    for (int i = 0; i < length; i++) {
                        result[i] = (int) adList.get(i);
                    }
                    return result;
                }
                if (returnType == float[].class) {
                    float[] result = new float[length];
                    for (int i = 0; i < length; i++) {
                        result[i] = (float) adList.get(i);
                    }
                    return result;
                }
                if (returnType == long[].class) {
                    long[] result = new long[length];
                    for (int i = 0; i < length; i++) {
                        result[i] = (long) adList.get(i);
                    }
                    return result;
                }
                if (returnType == double[].class) {
                    double[] result = new double[length];
                    for (int i = 0; i < length; i++) {
                        result[i] = (double) adList.get(i);
                    }
                    return result;
                }
                if (returnType == boolean[].class) {
                    boolean[] result = new boolean[length];
                    for (int i = 0; i < length; i++) {
                        result[i] = (boolean) adList.get(i);
                    }
                    return result;
                }
                Class<?> componentType = returnType.getComponentType();
                assert componentType != null && !componentType.isArray() && !componentType.isPrimitive() : componentType;
                Object[] result = (Object[]) Array.newInstance(componentType, length);
                for (int i = 0; i < length; i++) {
                    result[i] = toAnnotationElement(componentType, adList.get(i));
                }
                return result;
            }
            default -> {
                return avElement;
            }
        }
    }

    /**
     * Converts {@code annotationValue} to an instance of {@code type}.
     */
    private static <T extends Annotation> T toAnnotation(AnnotationValue annotationValue, Class<T> type) {
        return annotationValue.toAnnotation(type, AnnotatedObjectAccess::toAnnotation0);
    }

    private static <T extends Annotation> T toAnnotation0(AnnotationValue annotationValue, Class<T> type) {
        AnnotationType annotationType = AnnotationType.getInstance(type);
        Map<String, Object> memberValues = new EconomicHashMap<>();
        for (var e : annotationType.members().entrySet()) {
            String name = e.getKey();
            Object o = annotationValue.get(name, Object.class);
            memberValues.put(name, toAnnotationElement(e.getValue().getReturnType(), o));
        }
        return type.cast(AnnotationParser.annotationForMap(type, memberValues));
    }

    /**
     * Retrieves the annotation of type {@code annotationType} from {@code element}, considering
     * only declared annotations iff {@code declaredOnly} is true.
     *
     * @param element the annotated element to retrieve the annotation value from
     * @param annotationType the type of annotation to retrieve
     * @param declaredOnly whether to consider only declared annotations
     * @return the annotation value of the specified type, or null if no such annotation exists
     */
    @SuppressWarnings("unchecked")
    protected <T extends Annotation> T getAnnotation(Annotated element, Class<T> annotationType, boolean declaredOnly) {
        Map<ResolvedJavaType, AnnotationValue> annotationValues = getAnnotationValues(element, declaredOnly);
        AnnotationValue annotation = annotationValues.get(GraalAccess.lookupType(annotationType));
        if (annotation != null) {
            return toAnnotation(annotation, annotationType);
        }
        return null;
    }

    /**
     * Converts an {@link AnnotationValue} to an {@link Annotation} of type {@code annotationType}.
     */
    public <T extends Annotation> T asAnnotation(AnnotationValue annotationValue, Class<T> annotationType) {
        return toAnnotation(annotationValue, annotationType);
    }

    /**
     * Converts an {@link Annotation} to an {@link AnnotationValue}.
     */
    public AnnotationValue asAnnotationValue(Annotation annotation) {
        return toAnnotationValue(annotation);
    }

    protected boolean hasAnnotation(Annotated element, Class<? extends Annotation> annotationType) {
        try {
            // Checkstyle: allow direct annotation access
            Inherited inherited = annotationType.getAnnotation(Inherited.class);
            // Checkstyle: disallow direct annotation access
            return getAnnotationValues(element, inherited == null).containsKey(GraalAccess.lookupType(annotationType));
        } catch (LinkageError e) {
            /*
             * Returning false essentially means that the element doesn't declare the
             * annotationType, but we cannot know that since the annotation parsing failed. However,
             * this allows us to defend against crashing the image builder if the user code
             * references types missing from the classpath.
             */
            return false;
        }
    }

    /**
     * Gets the declared annotations of {@code element}.
     */
    public Map<ResolvedJavaType, AnnotationValue> getDeclaredAnnotationValues(Annotated element) {
        return getAnnotationValues(element, true);
    }

    protected Map<ResolvedJavaType, AnnotationValue> getAnnotationValues(Annotated element, boolean declaredOnly) {
        Map<ResolvedJavaType, AnnotationValue> result = null;
        List<AnnotationValue> annotationValues = new ArrayList<>();
        Annotated root = unwrap(element, annotationValues);
        if (root instanceof AnnotationsContainer ac) {
            List<Annotation> annotations = ac.getContainedAnnotations();
            if (annotations.isEmpty()) {
                return Map.of();
            }
            result = new EconomicHashMap<>(annotations.size());
            for (var a : annotations) {
                AnnotationValue annotationValue = toAnnotationValue(a);
                result.put(annotationValue.getAnnotationType(), annotationValue);
            }
        } else {
            if (root != null) {
                if (declaredOnly) {
                    annotationValues.addAll(getDeclaredAnnotationValuesFromRoot(root).values());
                } else {
                    annotationValues.addAll(getAnnotationValuesFromRoot(root).values());
                }
            }
            if (!annotationValues.isEmpty()) {
                result = new EconomicHashMap<>(annotationValues.size());
                for (AnnotationValue a : annotationValues) {
                    ResolvedJavaType annotationType = a.isError() ? ANNOTATION_FORMAT_ERROR_TYPE : a.getAnnotationType();
                    result.put(annotationType, a);
                }
            }
        }
        return result == null ? Map.of() : result;
    }

    /**
     * Gets the annotations on the super class hierarchy of {@code clazz} that are annotated by
     * {@link Inherited}.
     */
    private Map<ResolvedJavaType, AnnotationValue> getInheritableAnnotations(ResolvedJavaType clazz) {
        Map<ResolvedJavaType, AnnotationValue> inheritedAnnotations = null;
        ResolvedJavaType superClass = clazz.getSuperclass();
        if (superClass != null) {
            for (var e : getAnnotationValuesFromRoot(superClass).entrySet()) {
                ResolvedJavaType annotationType = e.getKey();
                if (hasAnnotation(annotationType, Inherited.class)) {
                    if (inheritedAnnotations == null) {
                        inheritedAnnotations = new EconomicHashMap<>();
                    }
                    inheritedAnnotations.put(annotationType, e.getValue());
                }
            }
        }
        return inheritedAnnotations;
    }

    private Map<ResolvedJavaType, AnnotationValue> getAnnotationValuesFromRoot(Annotated rootElement) {
        if (!(rootElement instanceof ResolvedJavaType clazz)) {
            return getDeclaredAnnotationValuesFromRoot(rootElement);
        }

        /*
         * Inheritable annotations must be computed first to avoid recursively updating
         * annotationCache.
         */
        Map<ResolvedJavaType, AnnotationValue> inheritableAnnotations = getInheritableAnnotations(clazz);
        Map<ResolvedJavaType, AnnotationValue> declaredAnnotations = getDeclaredAnnotationValuesFromRoot(rootElement);
        Map<ResolvedJavaType, AnnotationValue> annotations = null;
        if (inheritableAnnotations != null) {
            for (var e : inheritableAnnotations.entrySet()) {
                if (!declaredAnnotations.containsKey(e.getKey())) {
                    if (annotations == null) {
                        annotations = new EconomicHashMap<>(declaredAnnotations);
                    }
                    annotations.put(e.getKey(), e.getValue());
                }
            }
        }
        return annotations != null ? annotations : declaredAnnotations;
    }

    private static Map<ResolvedJavaType, AnnotationValue> getDeclaredAnnotationValuesFromRoot(Annotated rootElement) {
        return rootElement.getDeclaredAnnotationInfo(ANNOTATIONS_INFO_PARSER);
    }

    /**
     * Annotation type for a {@link AnnotationValue#isError() value representing a parse error}.
     */
    public static final ResolvedJavaType ANNOTATION_FORMAT_ERROR_TYPE = GraalAccess.lookupType(Void.TYPE);

    /**
     * Annotation parser function stored as a singleton as recommended by
     * {@link Annotated#getDeclaredAnnotationInfo(Function)}.
     */
    private static final Function<AnnotationsInfo, Map<ResolvedJavaType, AnnotationValue>> ANNOTATIONS_INFO_PARSER = info -> {
        if (info == null) {
            return Map.of();
        }
        ResolvedJavaType container = info.container();
        try {
            return AnnotationValueParser.parseAnnotations(info.bytes(), info.constPool(), container);
        } catch (AnnotationFormatError e) {
            return Map.of(ANNOTATION_FORMAT_ERROR_TYPE, new AnnotationValue(e));
        } catch (IllegalArgumentException | BufferUnderflowException | GenericSignatureFormatError e) {
            return Map.of(ANNOTATION_FORMAT_ERROR_TYPE, new AnnotationValue(new AnnotationFormatError(e)));
        }
    };

    /**
     * Gets the annotation values associated with the parameters of {@code element}.
     *
     * @param element the annotated element to retrieve parameter annotation values from
     * @return a list of lists, where each inner list contains the annotation values for a single
     *         parameter of the annotated element, or an empty list if the element has no parameters
     *         or no annotations
     */
    public List<List<AnnotationValue>> getParameterAnnotationValues(Annotated element) {
        Annotated root = unwrap(element, null);
        return root != null ? getParameterAnnotationValuesFromRoot((ResolvedJavaMethod) root) : List.of();
    }

    private static List<List<AnnotationValue>> getParameterAnnotationValuesFromRoot(ResolvedJavaMethod rootElement) {
        try {
            List<List<AnnotationValue>> parameterAnnotationValues = AnnotationValueSupport.getParameterAnnotationValues(rootElement);
            return parameterAnnotationValues == null ? List.of() : parameterAnnotationValues;
        } catch (IllegalArgumentException | BufferUnderflowException | GenericSignatureFormatError e) {
            return List.of(List.of(new AnnotationValue(new AnnotationFormatError(e))));
        } catch (AnnotationFormatError e) {
            return List.of(List.of(new AnnotationValue(e)));
        }
    }

    /**
     * Gets the type annotations associated with {@code element}.
     *
     * @param element the annotated element to retrieve type annotations from
     * @return a list of type annotations, or an empty list if none exist
     */
    public List<TypeAnnotationValue> getTypeAnnotationValues(Annotated element) {
        Annotated root = unwrap(element, null);
        return root != null ? getTypeAnnotationValuesFromRoot(root) : List.of();
    }

    private static List<TypeAnnotationValue> getTypeAnnotationValuesFromRoot(Annotated rootElement) {
        try {
            return switch (rootElement) {
                case ResolvedJavaType type -> AnnotationValueSupport.getTypeAnnotationValues(type);
                case ResolvedJavaMethod method -> AnnotationValueSupport.getTypeAnnotationValues(method);
                case ResolvedJavaField field -> AnnotationValueSupport.getTypeAnnotationValues(field);
                case ResolvedJavaRecordComponent recordComponent ->
                    AnnotationValueSupport.getTypeAnnotationValues(recordComponent);
                default ->
                    throw new AnnotatedObjectAccessError(rootElement, "Unexpected annotated element type: " + rootElement.getClass());
            };
        } catch (IllegalArgumentException | BufferUnderflowException | GenericSignatureFormatError e) {
            return List.of(new TypeAnnotationValue(new AnnotationFormatError(e)));
        } catch (AnnotationFormatError e) {
            return List.of(new TypeAnnotationValue(e));
        }
    }

    /**
     * Gets the default value for the annotation member represented by {@code method}.
     *
     * @see AnnotationValueSupport#getAnnotationDefaultValue
     */
    public Object getAnnotationDefaultValue(Annotated method) {
        Annotated root = unwrap(method, null);
        if (root == null) {
            return null;
        }
        try {
            return AnnotationValueSupport.getAnnotationDefaultValue((ResolvedJavaMethod) root);
        } catch (IllegalArgumentException | BufferUnderflowException | GenericSignatureFormatError e) {
            return new AnnotationFormatError(e);
        } catch (AnnotationFormatError e) {
            return e;
        }
    }

    private static Annotated unwrap(Annotated element, List<AnnotationValue> injectedAnnotationsCollector) {
        Annotated cur = element;
        while (cur instanceof AnnotatedWrapper wrapper) {
            if (injectedAnnotationsCollector != null) {
                List<AnnotationValue> injectedAnnotations = wrapper.getInjectedAnnotations();
                if (!injectedAnnotations.isEmpty()) {
                    injectedAnnotationsCollector.addAll(injectedAnnotations);
                }
            }
            cur = wrapper.getWrappedAnnotated();
        }
        return cur;
    }
}
