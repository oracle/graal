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
package com.oracle.svm.hosted.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.lang.annotation.Inherited;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericSignatureFormatError;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.BufferUnderflowException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.impl.AnnotationExtractor;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedElement;
import com.oracle.graal.pointsto.meta.BaseLayerElement;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.annotation.AnnotationValue;
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
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationSupport;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

/**
 * This class wraps all annotation accesses during the Native Image build. It relies on
 * {@link jdk.graal.compiler.annotation.AnnotationValueParser} to avoid class initialization.
 * <p>
 * The {@link SubstrateAnnotationExtractor} is tightly coupled with {@link AnnotationAccess}, which
 * provides implementations of {@link AnnotatedElement#isAnnotationPresent(Class)} and
 * {@link AnnotatedElement#getAnnotation(Class)}. {@link AnnotatedElement#getAnnotations()} must
 * never be used during Native Image generation because it initializes all annotation classes and
 * their dependencies.
 */
public class SubstrateAnnotationExtractor implements AnnotationExtractor, LayeredImageSingleton {
    private final Map<ResolvedJavaType, Map<ResolvedJavaType, AnnotationValue>> annotationCache = new ConcurrentHashMap<>();
    private final Map<Annotated, Map<ResolvedJavaType, AnnotationValue>> declaredAnnotationCache = new ConcurrentHashMap<>();
    private final Map<ResolvedJavaMethod, List<List<AnnotationValue>>> parameterAnnotationCache = new ConcurrentHashMap<>();
    private final Map<Annotated, List<TypeAnnotationValue>> typeAnnotationCache = new ConcurrentHashMap<>();
    private final Map<ResolvedJavaMethod, Object> annotationDefaultCache = new ConcurrentHashMap<>();
    private final Map<AnnotationValue, Annotation> resolvedAnnotationsCache = new ConcurrentHashMap<>();

    private static final Method packageGetPackageInfo = ReflectionUtil.lookupMethod(Package.class, "getPackageInfo");

    /**
     * Gets the annotation of type {@code annotationType} from {@code annotated}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static AnnotationValue toAnnotationValue(Annotation annotation) {
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
                return toAnnotation(av, type);
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
        AnnotationType annotationType = AnnotationType.getInstance(type);
        Map<String, Object> memberValues = new EconomicHashMap<>();
        for (var e : annotationType.members().entrySet()) {
            String name = e.getKey();
            Object o = annotationValue.get(name, Object.class);
            memberValues.put(name, toAnnotationElement(e.getValue().getReturnType(), o));
        }
        return type.cast(AnnotationParser.annotationForMap(type, memberValues));
    }

    public static List<AnnotationValue> prepareInjectedAnnotations(Annotation... annotations) {
        if (annotations == null || annotations.length == 0) {
            return List.of();
        }
        return Stream.of(annotations).map(SubstrateAnnotationExtractor::toAnnotationValue).toList();
    }

    @SuppressWarnings("unchecked")
    private <T extends Annotation> T extractAnnotation(Annotated element, Class<T> annotationType, boolean declaredOnly) {
        Map<ResolvedJavaType, AnnotationValue> annotationValues = getAnnotationValues(element, declaredOnly);
        AnnotationValue annotation = annotationValues.get(GraalAccess.lookupType(annotationType));
        if (annotation != null) {
            return (T) resolvedAnnotationsCache.computeIfAbsent(annotation, value -> toAnnotation(value, annotationType));
        }
        return null;
    }

    @Override
    public <T extends Annotation> T extractAnnotation(AnnotatedElement element, Class<T> annotationType, boolean declaredOnly) {
        try {
            return extractAnnotation(toAnnotated(element), annotationType, declaredOnly);
        } catch (LinkageError | AnnotationFormatError e) {
            /*
             * Returning null essentially means that the element doesn't declare the annotationType,
             * but we cannot know that since the annotation parsing failed. However, this allows us
             * to defend against crashing the image builder if the user code references types
             * missing from the classpath.
             */
            return null;
        }
    }

    @Override
    public boolean hasAnnotation(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        try {
            return hasAnnotation(toAnnotated(element), annotationType);
        } catch (LinkageError | AnnotationFormatError e) {
            /*
             * Returning false essentially means that the element doesn't declare the
             * annotationType, but we cannot know that since the annotation parsing failed. However,
             * this allows us to defend against crashing the image builder if the user code
             * references types missing from the classpath.
             */
            return false;
        }
    }

    private static Annotated toAnnotated(AnnotatedElement element) {
        switch (element) {
            case null -> {
                return null;
            }
            case Annotated annotated -> {
                return annotated;
            }
            case Class<?> clazz -> {
                return GraalAccess.lookupType(clazz);
            }
            case Executable executable -> {
                return GraalAccess.lookupMethod(executable);
            }
            case Field field -> {
                return GraalAccess.lookupField(field);
            }
            case RecordComponent rc -> {
                return GraalAccess.lookupRecordComponent(rc);
            }
            case Package packageObject -> {
                try {
                    return GraalAccess.lookupType((Class<?>) packageGetPackageInfo.invoke(packageObject));
                } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    if (targetException instanceof LinkageError) {
                        throw (LinkageError) targetException;
                    }
                    throw new AnnotationExtractionError(element, e);
                } catch (IllegalAccessException e) {
                    throw new AnnotationExtractionError(element, e);
                }
            }
            default -> throw new AnnotationExtractionError(element, (Throwable) null);
        }
    }

    public boolean hasAnnotation(Annotated element, Class<? extends Annotation> annotationType) {
        try {
            return getAnnotationValues(element, false).containsKey(GraalAccess.lookupType(annotationType));
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

    @SuppressWarnings("unchecked")
    @Override
    public Class<? extends Annotation>[] getAnnotationTypes(AnnotatedElement element) {
        return getAnnotationValues(toAnnotated(element), false).values().stream() //
                        .map(AnnotationValue::getAnnotationType) //
                        .map(OriginalClassProvider::getJavaClass) //
                        .filter(Objects::nonNull) //
                        .toArray(Class[]::new);
    }

    public Map<ResolvedJavaType, AnnotationValue> getDeclaredAnnotationValues(AnnotatedElement element) {
        return getAnnotationValues(toAnnotated(element), true);
    }

    private Map<ResolvedJavaType, AnnotationValue> getAnnotationValues(Annotated element, boolean declaredOnly) {
        Annotated cur = element;
        while (cur instanceof WrappedElement wrapped) {
            cur = toAnnotated(wrapped.getWrapped());
        }
        Map<ResolvedJavaType, AnnotationValue> result = Map.of();
        while (cur instanceof AnnotationWrapper wrapper) {
            result = concat(result, wrapper.getInjectedAnnotations());
            cur = toAnnotated(wrapper.getAnnotationRoot());
        }

        Annotated root = cur;
        if (root instanceof BaseLayerElement baseLayerElement) {
            Annotation[] baseLayerAnnotations = baseLayerElement.getBaseLayerAnnotations();
            if (baseLayerAnnotations.length == 0) {
                return Map.of();
            }
            result = new EconomicHashMap<>(baseLayerAnnotations.length);
            for (var a : baseLayerAnnotations) {
                AnnotationValue annotationValue = toAnnotationValue(a);
                result.put(annotationValue.getAnnotationType(), annotationValue);
            }
        } else if (root != null) {
            result = concat(result, declaredOnly ? getDeclaredAnnotationValuesFromRoot(root).values() : getAnnotationValuesFromRoot(root).values());
        }
        return result;
    }

    private static Map<ResolvedJavaType, AnnotationValue> concat(Map<ResolvedJavaType, AnnotationValue> a1, Collection<AnnotationValue> a2) {
        if (a2 == null || a2.isEmpty()) {
            return a1;
        } else {
            Map<ResolvedJavaType, AnnotationValue> result = a1 == null || a1.isEmpty() ? new EconomicHashMap<>(a2.size()) : new EconomicHashMap<>(a1);
            for (AnnotationValue a : a2) {
                ResolvedJavaType annotationType = a.isError() ? ANNOTATION_FORMAT_ERROR_TYPE : a.getAnnotationType();
                result.put(annotationType, a);
            }
            return result;
        }
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
                if (hasAnnotation((Annotated) annotationType, Inherited.class)) {
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

        Map<ResolvedJavaType, AnnotationValue> existing = annotationCache.get(clazz);
        if (existing != null) {
            return existing;
        }

        /*
         * Inheritable annotations must be computed first to avoid recursively updating
         * annotationCache.
         */
        Map<ResolvedJavaType, AnnotationValue> inheritableAnnotations = getInheritableAnnotations(clazz);
        return annotationCache.computeIfAbsent(clazz, element -> {
            Map<ResolvedJavaType, AnnotationValue> declaredAnnotations = getDeclaredAnnotationValuesFromRoot(element);
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
        });
    }

    private Map<ResolvedJavaType, AnnotationValue> getDeclaredAnnotationValuesFromRoot(Annotated rootElement) {
        return declaredAnnotationCache.computeIfAbsent(rootElement, SubstrateAnnotationExtractor::parseDeclaredAnnotationValues);
    }

    /**
     * Annotation type for a {@link AnnotationValue#isError() value representing a parse error}.
     */
    public static final ResolvedJavaType ANNOTATION_FORMAT_ERROR_TYPE = GraalAccess.lookupType(Void.TYPE);

    private static Map<ResolvedJavaType, AnnotationValue> parseDeclaredAnnotationValues(Annotated element) {
        try {
            return AnnotationValueSupport.getDeclaredAnnotationValues(element);
        } catch (AnnotationFormatError e) {
            return Map.of(ANNOTATION_FORMAT_ERROR_TYPE, new AnnotationValue(e));
        } catch (IllegalArgumentException | BufferUnderflowException | GenericSignatureFormatError e) {
            return Map.of(ANNOTATION_FORMAT_ERROR_TYPE, new AnnotationValue(new AnnotationFormatError(e)));
        }
    }

    public List<List<AnnotationValue>> getParameterAnnotationValues(AnnotatedElement element) {
        Annotated root = toAnnotated(unwrap(element));
        return root != null ? getParameterAnnotationValuesFromRoot((ResolvedJavaMethod) root) : List.of();
    }

    private List<List<AnnotationValue>> getParameterAnnotationValuesFromRoot(ResolvedJavaMethod rootElement) {
        return parameterAnnotationCache.computeIfAbsent(rootElement, element -> {
            try {
                List<List<AnnotationValue>> parameterAnnotationValues = AnnotationValueSupport.getParameterAnnotationValues(element);
                return parameterAnnotationValues == null ? List.of() : parameterAnnotationValues;
            } catch (IllegalArgumentException | BufferUnderflowException | GenericSignatureFormatError e) {
                return List.of(List.of(new AnnotationValue(new AnnotationFormatError(e))));
            } catch (AnnotationFormatError e) {
                return List.of(List.of(new AnnotationValue(e)));
            }
        });
    }

    public List<TypeAnnotationValue> getTypeAnnotationValues(AnnotatedElement element) {
        Annotated root = toAnnotated(unwrap(element));
        return root != null ? getTypeAnnotationValuesFromRoot(root) : List.of();
    }

    private List<TypeAnnotationValue> getTypeAnnotationValuesFromRoot(Annotated rootElement) {
        return typeAnnotationCache.computeIfAbsent(rootElement, SubstrateAnnotationExtractor::parseTypeAnnotationValues);
    }

    private static List<TypeAnnotationValue> parseTypeAnnotationValues(Annotated element) {
        try {
            return switch (element) {
                case ResolvedJavaType type -> AnnotationValueSupport.getTypeAnnotationValues(type);
                case ResolvedJavaMethod method -> AnnotationValueSupport.getTypeAnnotationValues(method);
                case ResolvedJavaField field -> AnnotationValueSupport.getTypeAnnotationValues(field);
                case ResolvedJavaRecordComponent recordComponent ->
                    AnnotationValueSupport.getTypeAnnotationValues(recordComponent);
                default ->
                    throw new AnnotationExtractionError(element, "Unexpected annotated element type: " + element.getClass());
            };
        } catch (IllegalArgumentException | BufferUnderflowException | GenericSignatureFormatError e) {
            return List.of(new TypeAnnotationValue(new AnnotationFormatError(e)));
        } catch (AnnotationFormatError e) {
            return List.of(new TypeAnnotationValue(e));
        }
    }

    public Object getAnnotationDefaultValue(AnnotatedElement element) {
        Annotated root = toAnnotated(unwrap(element));
        return root != null ? getAnnotationDefaultValueFromRoot((ResolvedJavaMethod) root) : null;
    }

    private Object getAnnotationDefaultValueFromRoot(ResolvedJavaMethod accessorMethod) {
        return annotationDefaultCache.computeIfAbsent(accessorMethod, method -> {
            try {
                return AnnotationValueSupport.getAnnotationDefaultValue(method);
            } catch (IllegalArgumentException | BufferUnderflowException | GenericSignatureFormatError e) {
                return new AnnotationFormatError(e);
            } catch (AnnotationFormatError e) {
                return e;
            }
        });
    }

    private static AnnotatedElement unwrap(AnnotatedElement element) {
        AnnotatedElement cur = element;
        while (cur instanceof WrappedElement) {
            cur = ((WrappedElement) cur).getWrapped();
        }
        while (cur instanceof AnnotationWrapper) {
            cur = ((AnnotationWrapper) cur).getAnnotationRoot();
        }
        return cur;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        return PersistFlags.NOTHING;
    }
}
