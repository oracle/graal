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
import java.lang.annotation.Inherited;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.impl.AnnotationExtractor;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedElement;
import com.oracle.graal.pointsto.meta.BaseLayerElement;
import com.oracle.graal.pointsto.meta.BaseLayerField;
import com.oracle.graal.pointsto.meta.BaseLayerMethod;
import com.oracle.graal.pointsto.meta.BaseLayerType;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.hosted.annotation.AnnotationMetadata.AnnotationExtractionError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.reflect.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.reflect.annotation.AnnotationParser;

/**
 * This class wraps all annotation accesses during the Native Image build. This is necessary to
 * avoid initializing classes that should be initialized at run-time, since looking up annotations
 * through the JDK's {@link AnnotationParser} initializes the class of every annotation on the
 * queried element.
 *
 * When queried, the extractor looks for the root of the provided element, which can be a
 * {@link Field}, {@link Method}, {@link Constructor} or {@link Class} object, as well as a record
 * component on JDK 17. It then looks into the byte arrays representing annotations in the root
 * object and outputs wrapper classes containing all the information necessary to reconstruct the
 * annotation on demand in an {@link AnnotationValue} or {@link TypeAnnotationValue} object or any
 * subclass of {@link AnnotationMemberValue}. The actual annotation can then be created using the
 * {@link AnnotationMemberValue#get(Class)} method.
 *
 * The {@link SubstrateAnnotationExtractor} is tightly coupled with {@link AnnotationAccess}, which
 * provides implementations of {@link AnnotatedElement#isAnnotationPresent(Class)} and
 * {@link AnnotatedElement#getAnnotation(Class)}. {@link AnnotatedElement#getAnnotations()} must
 * never be used during Native Image generation because it initializes all annotation classes and
 * their dependencies.
 */
public class SubstrateAnnotationExtractor implements AnnotationExtractor, LayeredImageSingleton {
    private final Map<Class<?>, AnnotationValue[]> annotationCache = new ConcurrentHashMap<>();
    private final Map<AnnotatedElement, AnnotationValue[]> declaredAnnotationCache = new ConcurrentHashMap<>();
    private final Map<Executable, AnnotationValue[][]> parameterAnnotationCache = new ConcurrentHashMap<>();
    private final Map<AnnotatedElement, TypeAnnotationValue[]> typeAnnotationCache = new ConcurrentHashMap<>();
    private final Map<Method, AnnotationMemberValue> annotationDefaultCache = new ConcurrentHashMap<>();
    private final Map<AnnotationValue, Annotation> resolvedAnnotationsCache = new ConcurrentHashMap<>();

    private static final AnnotationValue[] NO_ANNOTATIONS = new AnnotationValue[0];
    private static final AnnotationValue[][] NO_PARAMETER_ANNOTATIONS = new AnnotationValue[0][0];
    private static final TypeAnnotationValue[] NO_TYPE_ANNOTATIONS = new TypeAnnotationValue[0];

    private static final Method classGetRawAnnotations = ReflectionUtil.lookupMethod(Class.class, "getRawAnnotations");
    private static final Method classGetRawTypeAnnotations = ReflectionUtil.lookupMethod(Class.class, "getRawTypeAnnotations");
    private static final Method classGetConstantPool = ReflectionUtil.lookupMethod(Class.class, "getConstantPool");
    private static final Field fieldAnnotations = ReflectionUtil.lookupField(Field.class, "annotations");
    private static final Method fieldGetTypeAnnotationBytes = ReflectionUtil.lookupMethod(Field.class, "getTypeAnnotationBytes0");
    private static final Method executableGetAnnotationBytes = ReflectionUtil.lookupMethod(Executable.class, "getAnnotationBytes");
    private static final Method executableGetTypeAnnotationBytes = ReflectionUtil.lookupMethod(Executable.class, "getTypeAnnotationBytes");
    private static final Field methodParameterAnnotations = ReflectionUtil.lookupField(Method.class, "parameterAnnotations");
    private static final Field methodAnnotationDefault = ReflectionUtil.lookupField(Method.class, "annotationDefault");
    private static final Field constructorParameterAnnotations = ReflectionUtil.lookupField(Constructor.class, "parameterAnnotations");
    private static final Class<?> recordComponentClass = ReflectionUtil.lookupClass(true, "java.lang.reflect.RecordComponent");
    private static final Field recordComponentAnnotations = recordComponentClass == null ? null : ReflectionUtil.lookupField(recordComponentClass, "annotations");
    private static final Field recordComponentTypeAnnotations = recordComponentClass == null ? null : ReflectionUtil.lookupField(recordComponentClass, "typeAnnotations");
    private static final Method recordComponentGetDeclaringRecord = recordComponentClass == null ? null : ReflectionUtil.lookupMethod(recordComponentClass, "getDeclaringRecord");
    private static final Method packageGetPackageInfo = ReflectionUtil.lookupMethod(Package.class, "getPackageInfo");

    public static AnnotationValue[] prepareInjectedAnnotations(Annotation... annotations) {
        if (annotations == null || annotations.length == 0) {
            return NO_ANNOTATIONS;
        }
        AnnotationValue[] result = new AnnotationValue[annotations.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = new AnnotationValue(Objects.requireNonNull(annotations[i]));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Annotation> T extractAnnotation(AnnotatedElement element, Class<T> annotationType, boolean declaredOnly) {
        try {
            for (AnnotationValue annotation : getAnnotationData(element, declaredOnly)) {
                if (annotation.type != null && annotation.type.equals(annotationType)) {
                    return (T) resolvedAnnotationsCache.computeIfAbsent(annotation, value -> (Annotation) value.get(annotationType));
                }
            }
            return null;
        } catch (LinkageError e) {
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
            for (AnnotationValue annotation : getAnnotationData(element, false)) {
                if (annotation.type != null && annotation.type.equals(annotationType)) {
                    return true;
                }
            }
            return false;
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
        return Arrays.stream(getAnnotationData(element, false)).map(AnnotationValue::getType).filter(Objects::nonNull).toArray(Class[]::new);
    }

    public AnnotationValue[] getDeclaredAnnotationData(AnnotatedElement element) {
        return getAnnotationData(element, true);
    }

    private AnnotationValue[] getAnnotationData(AnnotatedElement element, boolean declaredOnly) {
        AnnotatedElement cur = element;
        while (cur instanceof WrappedElement) {
            cur = ((WrappedElement) cur).getWrapped();
        }
        AnnotationValue[] result = NO_ANNOTATIONS;
        while (cur instanceof AnnotationWrapper wrapper) {
            result = concat(result, wrapper.getInjectedAnnotations());
            cur = wrapper.getAnnotationRoot();
        }

        AnnotatedElement root = findRoot(cur);
        if (root instanceof BaseLayerElement baseLayerElement) {
            result = Arrays.stream(baseLayerElement.getBaseLayerAnnotations()).map(AnnotationValue::new).toList().toArray(new AnnotationValue[0]);
        } else if (root != null) {
            result = concat(result, declaredOnly ? getDeclaredAnnotationDataFromRoot(root) : getAnnotationDataFromRoot(root));
        }
        return result;
    }

    private static AnnotationValue[] concat(AnnotationValue[] a1, AnnotationValue[] a2) {
        if (a2 == null || a2.length == 0) {
            return a1;
        } else if (a1 == null || a1.length == 0) {
            return a2;
        } else {
            AnnotationValue[] result = Arrays.copyOf(a1, a1.length + a2.length, AnnotationValue[].class);
            System.arraycopy(a2, 0, result, a1.length, a2.length);
            return result;
        }
    }

    private AnnotationValue[] getAnnotationDataFromRoot(AnnotatedElement rootElement) {
        if (!(rootElement instanceof Class<?> clazz)) {
            return getDeclaredAnnotationDataFromRoot(rootElement);
        }

        AnnotationValue[] existing = annotationCache.get(clazz);
        if (existing != null) {
            return existing;
        }

        List<AnnotationValue> inheritedAnnotations = new ArrayList<>();
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
            for (AnnotationValue superclassAnnotation : getAnnotationDataFromRoot(superClass)) {
                if (hasAnnotation(superclassAnnotation.type, Inherited.class)) {
                    inheritedAnnotations.add(superclassAnnotation);
                }
            }
        }

        return annotationCache.computeIfAbsent(clazz, element -> {
            AnnotationValue[] declaredAnnotations = getDeclaredAnnotationDataFromRoot(element);
            Map<Class<? extends Annotation>, AnnotationValue> annotations = new LinkedHashMap<>();
            for (AnnotationValue declaredAnnotation : declaredAnnotations) {
                annotations.put(declaredAnnotation.type, declaredAnnotation);
            }
            boolean modified = false;
            for (AnnotationValue inheritedAnnotation : inheritedAnnotations) {
                if (!annotations.containsKey(inheritedAnnotation.type)) {
                    annotations.put(inheritedAnnotation.type, inheritedAnnotation);
                    modified = true;
                }
            }
            return modified ? annotations.values().toArray(NO_ANNOTATIONS) : declaredAnnotations;
        });
    }

    private AnnotationValue[] getDeclaredAnnotationDataFromRoot(AnnotatedElement rootElement) {
        return declaredAnnotationCache.computeIfAbsent(rootElement, element -> {
            byte[] rawAnnotations = getRawAnnotations(element);
            if (rawAnnotations == null) {
                return NO_ANNOTATIONS;
            }
            ByteBuffer buf = ByteBuffer.wrap(rawAnnotations);
            try {
                List<AnnotationValue> annotations = new ArrayList<>();
                int numAnnotations = buf.getShort() & 0xFFFF;
                for (int i = 0; i < numAnnotations; i++) {
                    AnnotationValue annotation = AnnotationValue.extract(buf, getConstantPool(element), getContainer(element), false, false);
                    if (annotation != null) {
                        annotations.add(annotation);
                    }
                }
                return annotations.toArray(NO_ANNOTATIONS);
            } catch (IllegalArgumentException | BufferUnderflowException ex) {
                return new AnnotationValue[]{AnnotationValue.forAnnotationFormatException()};
            }
        });
    }

    public AnnotationValue[][] getParameterAnnotationData(AnnotatedElement element) {
        AnnotatedElement root = findRoot(unwrap(element));
        return root != null ? getParameterAnnotationDataFromRoot((Executable) root) : NO_PARAMETER_ANNOTATIONS;
    }

    private AnnotationValue[][] getParameterAnnotationDataFromRoot(Executable rootElement) {
        return parameterAnnotationCache.computeIfAbsent(rootElement, element -> {
            byte[] rawParameterAnnotations = getRawParameterAnnotations(element);
            if (rawParameterAnnotations == null) {
                return NO_PARAMETER_ANNOTATIONS;
            }
            ByteBuffer buf = ByteBuffer.wrap(rawParameterAnnotations);
            try {
                int numParameters = buf.get() & 0xFF;
                if (numParameters == 0) {
                    return NO_PARAMETER_ANNOTATIONS;
                }
                AnnotationValue[][] parameterAnnotations = new AnnotationValue[numParameters][];
                for (int i = 0; i < numParameters; i++) {
                    List<AnnotationValue> parameterAnnotationList = new ArrayList<>();
                    int numAnnotations = buf.getShort() & 0xFFFF;
                    for (int j = 0; j < numAnnotations; j++) {
                        AnnotationValue parameterAnnotation = AnnotationValue.extract(buf, getConstantPool(element), getContainer(element), false, false);
                        if (parameterAnnotation != null) {
                            parameterAnnotationList.add(parameterAnnotation);
                        }
                    }
                    parameterAnnotations[i] = parameterAnnotationList.toArray(NO_ANNOTATIONS);
                }
                return parameterAnnotations;
            } catch (IllegalArgumentException | BufferUnderflowException ex) {
                return new AnnotationValue[][]{new AnnotationValue[]{AnnotationValue.forAnnotationFormatException()}};
            }
        });
    }

    public TypeAnnotationValue[] getTypeAnnotationData(AnnotatedElement element) {
        AnnotatedElement root = findRoot(unwrap(element));
        return root != null ? getTypeAnnotationDataFromRoot(root) : NO_TYPE_ANNOTATIONS;
    }

    private TypeAnnotationValue[] getTypeAnnotationDataFromRoot(AnnotatedElement rootElement) {
        return typeAnnotationCache.computeIfAbsent(rootElement, element -> {
            byte[] rawTypeAnnotations = getRawTypeAnnotations(element);
            if (rawTypeAnnotations == null) {
                return NO_TYPE_ANNOTATIONS;
            }
            ByteBuffer buf = ByteBuffer.wrap(rawTypeAnnotations);
            try {
                int annotationCount = buf.getShort() & 0xFFFF;
                TypeAnnotationValue[] typeAnnotationValues = new TypeAnnotationValue[annotationCount];
                for (int i = 0; i < annotationCount; i++) {
                    typeAnnotationValues[i] = TypeAnnotationValue.extract(buf, getConstantPool(element), getContainer(element));
                }
                return typeAnnotationValues;
            } catch (IllegalArgumentException | BufferUnderflowException ex) {
                /*
                 * The byte[] arrrays in the TypeAnnotationValue are structurally correct, but have
                 * an illegal first targetInfo byte that will throw an AnnotationFormatException
                 * during parsing.
                 */
                return new TypeAnnotationValue[]{new TypeAnnotationValue(new byte[]{0x77}, new byte[]{0}, AnnotationValue.forAnnotationFormatException())};
            }
        });
    }

    public AnnotationMemberValue getAnnotationDefaultData(AnnotatedElement element) {
        AnnotatedElement root = findRoot(unwrap(element));
        return root != null ? getAnnotationDefaultDataFromRoot((Method) root) : null;
    }

    private AnnotationMemberValue getAnnotationDefaultDataFromRoot(Method accessorMethod) {
        return annotationDefaultCache.computeIfAbsent(accessorMethod, method -> {
            byte[] rawAnnotationDefault = getRawAnnotationDefault(method);
            if (rawAnnotationDefault == null) {
                return null;
            }
            ByteBuffer buf = ByteBuffer.wrap(rawAnnotationDefault);
            try {
                return AnnotationMemberValue.extract(buf, getConstantPool(method), getContainer(method), false);
            } catch (IllegalArgumentException | BufferUnderflowException ex) {
                return AnnotationValue.forAnnotationFormatException();
            }
        });
    }

    private static byte[] getRawAnnotations(AnnotatedElement rootElement) {
        try {
            if (rootElement instanceof Class<?>) {
                return (byte[]) classGetRawAnnotations.invoke(rootElement);
            } else if (rootElement instanceof Field) {
                return (byte[]) fieldAnnotations.get(rootElement);
            } else if (rootElement instanceof Executable) {
                return (byte[]) executableGetAnnotationBytes.invoke(rootElement);
            } else if (recordComponentClass != null && recordComponentClass.isInstance(rootElement)) {
                return (byte[]) recordComponentAnnotations.get(rootElement);
            } else {
                throw new AnnotationExtractionError(rootElement, "Unexpected annotated element type: " + rootElement.getClass());
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new AnnotationExtractionError(rootElement, e);
        }
    }

    private static byte[] getRawParameterAnnotations(Executable rootElement) {
        try {
            if (rootElement instanceof Method) {
                return (byte[]) methodParameterAnnotations.get(rootElement);
            } else if (rootElement instanceof Constructor<?>) {
                return (byte[]) constructorParameterAnnotations.get(rootElement);
            } else {
                throw new AnnotationExtractionError(rootElement, "Unexpected annotated element type: " + rootElement.getClass());
            }
        } catch (IllegalAccessException e) {
            throw new AnnotationExtractionError(rootElement, e);
        }
    }

    private static byte[] getRawTypeAnnotations(AnnotatedElement rootElement) {
        try {
            if (rootElement instanceof Class<?>) {
                return (byte[]) classGetRawTypeAnnotations.invoke(rootElement);
            } else if (rootElement instanceof Field) {
                return (byte[]) fieldGetTypeAnnotationBytes.invoke(rootElement);
            } else if (rootElement instanceof Executable) {
                return (byte[]) executableGetTypeAnnotationBytes.invoke(rootElement);
            } else if (recordComponentClass != null && recordComponentClass.isInstance(rootElement)) {
                return (byte[]) recordComponentTypeAnnotations.get(rootElement);
            } else {
                throw new AnnotationExtractionError(rootElement, "Unexpected annotated element type: " + rootElement.getClass());
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new AnnotationExtractionError(rootElement, e);
        }
    }

    private static byte[] getRawAnnotationDefault(Method method) {
        try {
            return (byte[]) methodAnnotationDefault.get(method);
        } catch (IllegalAccessException e) {
            throw new AnnotationExtractionError(method, e);
        }
    }

    private static ConstantPool getConstantPool(AnnotatedElement rootElement) {
        Class<?> container = getContainer(rootElement);
        try {
            return (ConstantPool) classGetConstantPool.invoke(container);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new AnnotationExtractionError(rootElement, e);
        }
    }

    private static Class<?> getContainer(AnnotatedElement rootElement) {
        if (rootElement instanceof Class<?>) {
            return (Class<?>) rootElement;
        } else if (rootElement instanceof Field) {
            return ((Field) rootElement).getDeclaringClass();
        } else if (rootElement instanceof Executable) {
            return ((Executable) rootElement).getDeclaringClass();
        } else if (recordComponentClass != null && recordComponentClass.isInstance(rootElement)) {
            try {
                return (Class<?>) recordComponentGetDeclaringRecord.invoke(rootElement);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new AnnotationExtractionError(rootElement, e);
            }
        } else {
            throw new AnnotationExtractionError(rootElement, "Unexpected annotated element type: " + rootElement.getClass());
        }
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

    private static AnnotatedElement findRoot(AnnotatedElement element) {
        assert !(element instanceof WrappedElement || element instanceof AnnotationWrapper);
        try {
            if (element instanceof BaseLayerType || element instanceof BaseLayerMethod || element instanceof BaseLayerField) {
                return element;
            } else if (element instanceof ResolvedJavaType type) {
                return OriginalClassProvider.getJavaClass(type);
            } else if (element instanceof ResolvedJavaMethod method) {
                return OriginalMethodProvider.getJavaMethod(method);
            } else if (element instanceof ResolvedJavaField field) {
                return OriginalFieldProvider.getJavaField(field);
            } else if (element instanceof Package packageObject) {
                return (Class<?>) packageGetPackageInfo.invoke(packageObject);
            } else {
                return element;
            }
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

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        return PersistFlags.NOTHING;
    }
}
