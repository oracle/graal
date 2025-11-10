/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.AnnotationExtractor;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueType;
import jdk.graal.compiler.annotation.EnumElement;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

/**
 * This complements {@link org.graalvm.nativeimage.AnnotationAccess} for use by SVM internal
 * features and code. It avoids relying on JVMCI types (such as {@link ResolvedJavaType})
 * implementing {@link java.lang.reflect.AnnotatedElement}. This inheritance is planned for removal
 * (GR-69713) as part of reducing use of core reflection in Native Image.
 */
public final class AnnotationUtil {

    /**
     * Lazily created singleton to be used when outside the scope of a Native Image build.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    static class Lazy {
        static final AnnotatedObjectAccess instance;
        static final Throwable initLocation;
        static {
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, AnnotatedObjectAccess.class, false, "java.base", "sun.reflect.annotation");
            instance = new AnnotatedObjectAccess();
            initLocation = new Throwable("Lazy.instance created here:");
        }
    }

    /**
     * Used to ensure only one path through {@link #instance()} is taken per VM execution to prevent
     * leaking data via {@link Lazy#instance}.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private static Boolean instanceIsSingleton;

    @Platforms(Platform.HOSTED_ONLY.class)
    private static AnnotatedObjectAccess instance() {
        if (ImageSingletonsSupport.isInstalled() && ImageSingletons.contains(AnnotationExtractor.class)) {
            if (instanceIsSingleton == null) {
                instanceIsSingleton = true;
            } else if (!instanceIsSingleton) {
                throw new GraalError(Lazy.initLocation, "Cannot use image singleton AnnotatedObjectAccess after Lazy.instance initialized");
            }
            return (AnnotatedObjectAccess) ImageSingletons.lookup(AnnotationExtractor.class);
        }
        // Fall back to singleton when no AnnotationExtractor singleton is available (e.g.,
        // running `mx unittest com.oracle.graal.pointsto.standalone.test`).
        GraalError.guarantee(instanceIsSingleton == null || !instanceIsSingleton, "Cannot use image singleton AnnotatedObjectAccess and Lazy.instance in one process");
        instanceIsSingleton = false;
        return Lazy.instance;
    }

    /**
     * Converts an {@link Annotation} to an {@link AnnotationValue}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static AnnotationValue asAnnotationValue(Annotation annotation) {
        return instance().asAnnotationValue(annotation);
    }

    /**
     * Gets the declared annotations of {@code annotated}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static Map<ResolvedJavaType, AnnotationValue> getDeclaredAnnotationValues(Annotated annotated) {
        return instance().getDeclaredAnnotationValues(annotated);
    }

    /**
     * Gets the annotation of type {@code annotationType} from {@code element} if such an annotation
     * is present, else null.
     */
    public static <T extends Annotation> T getAnnotation(Annotated element, Class<T> annotationType) {
        // Checkstyle: allow direct annotation access
        if (ImageInfo.inImageRuntimeCode()) {
            if (element instanceof RuntimeAnnotated ra) {
                return ra.getAnnotation(annotationType);
            }
            throw new IllegalArgumentException("Cannot cast " + element.getClass() + " to " + RuntimeAnnotated.class.getName() + ": " + element);
        }
        return instance().getAnnotation(element, annotationType);
        // Checkstyle: disallow direct annotation access
    }

    @SuppressWarnings("unchecked")
    @Platforms(Platform.HOSTED_ONLY.class)
    public static Annotation[][] getParameterAnnotations(ResolvedJavaMethod method) {
        List<List<AnnotationValue>> values = instance().getParameterAnnotationValues(method);
        if (values == null) {
            return null;
        }
        Annotation[][] res = new Annotation[values.size()][];
        for (int i = 0; i < values.size(); i++) {
            List<AnnotationValue> annotations = values.get(i);
            res[i] = new Annotation[annotations.size()];
            for (int j = 0; j < annotations.size(); j++) {
                AnnotationValue a = annotations.get(j);
                Class<? extends Annotation> aType = (Class<? extends Annotation>) OriginalClassProvider.getJavaClass(a.getAnnotationType());
                res[i][j] = instance().asAnnotation(a, aType);
            }
        }
        return res;
    }

    /**
     * Retrieves the annotations of type {@code annotationClass} from the given {@code element},
     * including both direct annotations and those contained within a container annotation of type
     * {@code containerClass}.
     * <p>
     * Unlike {@link AnnotatedElement#getAnnotationsByType}, this method does not initialize all
     * annotation classes and their dependencies, making it suitable for use during image build
     * time.
     * <p>
     * The order of annotations returned by this method differs from that of
     * {@link AnnotatedElement#getAnnotationsByType}: direct annotations are always returned first,
     * followed by container annotations.
     * <p>
     * To avoid complex reflective lookups, information about the container annotation must be
     * provided through the {@code containerClass} and {@code valueFunction} parameters.
     *
     * @param element the annotated element to retrieve annotations from
     * @param annotationClass the type of annotation to retrieve
     * @param containerClass the type of container annotation that may contain the desired
     *            annotations
     * @param valueFunction a function that extracts the desired annotations from a container
     *            annotation
     * @return a list of annotations of type {@code annotationClass} found on the given element
     */
    public static <A extends Annotation, C extends Annotation> List<A> getAnnotationsByType(Annotated element,
                    Class<A> annotationClass, Class<C> containerClass, Function<C, A[]> valueFunction) {

        List<A> result = new ArrayList<>();
        A direct = getAnnotation(element, annotationClass);
        if (direct != null) {
            result.add(direct);
        }
        C container = getAnnotation(element, containerClass);
        if (container != null) {
            result.addAll(List.of(valueFunction.apply(container)));
        }
        return result;
    }

    /**
     * Determines if an annotation of type {@code annotationType} is present on {@code element}.
     */
    public static boolean isAnnotationPresent(Annotated element, Class<? extends Annotation> annotationType) {
        return getAnnotation(element, annotationType) != null;
    }

    /**
     * Creates an {@link Annotation} for the given annotation type and element values.
     *
     * @param elements a sequence of (name,value) pairs where each name must denote an existing
     *            element of the annotation type and the value must have the right type according to
     *            {@link AnnotationValueType#matchesElementType}. Note that {@link Enum} and
     *            {@link Class} values are automatically converted to {@link EnumElement} and
     *            {@link ResolvedJavaType} values respectively.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static <T extends Annotation> T newAnnotation(Class<T> annotationType, Object... elements) {
        return instance().asAnnotation(newAnnotationValue(annotationType, elements), annotationType);
    }

    /**
     * Creates an {@link AnnotationValue} for the given annotation type and element values.
     *
     * @param elements a sequence of (name,value) pairs where name must denote an existing element
     *            of the annotation type and value must have a type according to
     *            {@link AnnotationValueType#matchesElementType}. Note that {@link Enum} and
     *            {@link Class} values are automatically converted to {@link EnumElement} and
     *            {@link ResolvedJavaType} values respectively.
     */
    public static <T extends Annotation> AnnotationValue newAnnotationValue(Class<T> annotationType, Object... elements) {
        if ((elements.length % 2) != 0) {
            throw new IllegalArgumentException("Elements must be a sequence of (name,value) pairs");
        }
        ResolvedJavaType jvmciAnnotationType = GraalAccess.lookupType(annotationType);
        AnnotationValueType annotationValueType = AnnotationValueType.getInstance(jvmciAnnotationType);
        var elementTypes = annotationValueType.memberTypes();
        Map<String, Object> elementsMap = new EconomicHashMap<>(annotationValueType.memberDefaults());
        for (int i = 0; i < elements.length; i += 2) {
            if (!(elements[i] instanceof String name)) {
                throw new IllegalArgumentException(String.format("entry %d of elements is not a String: %s", i, elements[i]));
            }
            Object elementValue = elements[i + 1];
            if (elementValue == null) {
                throw new IllegalArgumentException(String.format("entry %d of elements is null", i));
            }
            ResolvedJavaType elementType = elementTypes.get(name);
            if (elementType == null) {
                throw new IllegalArgumentException(String.format("%s does not define an element named %s", annotationType.getName(), name));
            }
            if (elementValue instanceof Class<?> c) {
                String internalName = "L" + c.getName().replace(".", "/") + ";";
                elementValue = UnresolvedJavaType.create(internalName).resolve(jvmciAnnotationType);
            } else if (elementValue instanceof Enum<?> e) {
                String internalName = "L" + e.getClass().getName().replace(".", "/") + ";";
                ResolvedJavaType enumType = UnresolvedJavaType.create(internalName).resolve(jvmciAnnotationType);
                elementValue = new EnumElement(enumType, e.name());
            }
            if (!AnnotationValueType.matchesElementType(elementValue, elementType)) {
                throw new IllegalArgumentException(String.format("element '%s' is not of type %s: %s", name, elementType.toJavaName(), elementValue));
            }
            elementsMap.put(name, elementValue);
        }
        return new AnnotationValue(jvmciAnnotationType, elementsMap);
    }
}
