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
import jdk.graal.compiler.annotation.TypeAnnotationValue;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.vmaccess.HostAnnotationValueConverter;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

/**
 * Provides methods to query annotation information on {@link Annotated} objects. Caches are
 * employed to speed up most queries.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class AnnotatedObjectAccess {

    /**
     * Gets the annotation of type {@code annotationType} from {@code element} as an
     * {@link AnnotationValue} object.
     *
     * @param element the annotated element to retrieve the annotation value from
     * @param annotationType the type of annotation to retrieve
     * @return the annotation value of the specified type, or null if no such annotation exists
     */
    public <T extends Annotation> AnnotationValue getAnnotationValue(Annotated element, Class<T> annotationType) {
        // Checkstyle: allow direct annotation access
        Inherited inherited = annotationType.getAnnotation(Inherited.class);
        // Checkstyle: disallow direct annotation access
        Map<ResolvedJavaType, AnnotationValue> annotationValues = getAnnotationValues(element, inherited == null);
        return annotationValues.get(GuestAccess.get().lookupType(annotationType));
    }

    /**
     * Gets the annotation of type {@code annotationType} from {@code element}.
     *
     * @param element the annotated element to retrieve the annotation value from
     * @param annotationType the type of annotation to retrieve
     * @return the annotation value of the specified type, or null if no such annotation exists
     */
    public <T extends Annotation> T getAnnotation(Annotated element, Class<T> annotationType) {
        AnnotationValue annotationValue = getAnnotationValue(element, annotationType);
        if (annotationValue != null) {
            return asAnnotation(annotationValue, annotationType);
        }
        return null;
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
        AnnotationValue annotation = annotationValues.get(GuestAccess.get().lookupType(annotationType));
        if (annotation != null) {
            return asAnnotation(annotation, annotationType);
        }
        return null;
    }

    /**
     * Converts an {@link AnnotationValue} to an {@link Annotation} of type {@code annotationType}.
     */
    public <T extends Annotation> T asAnnotation(AnnotationValue annotationValue, Class<T> annotationType) {
        return HostAnnotationValueConverter.toAnnotation(annotationValue, annotationType, OriginalClassProvider::getJavaClass);
    }

    /**
     * Converts an {@link Annotation} to an {@link AnnotationValue}.
     */
    public AnnotationValue asAnnotationValue(Annotation annotation) {
        return HostAnnotationValueConverter.toAnnotationValue(annotation, GuestAccess.get()::lookupType);
    }

    protected boolean hasAnnotation(Annotated element, Class<? extends Annotation> annotationType) {
        try {
            // Checkstyle: allow direct annotation access
            Inherited inherited = annotationType.getAnnotation(Inherited.class);
            // Checkstyle: disallow direct annotation access
            return getAnnotationValues(element, inherited == null).containsKey(GuestAccess.get().lookupType(annotationType));
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
            List<AnnotationValue> containedAnnotations = ac.getContainedAnnotations();
            if (containedAnnotations.isEmpty()) {
                return Map.of();
            }
            result = new EconomicHashMap<>(containedAnnotations.size());
            for (var annotationValue : containedAnnotations) {
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
                    ResolvedJavaType annotationType = a.isError() ? getAnnotationFormatErrorType() : a.getAnnotationType();
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
     * Gets the annotation type for a {@link AnnotationValue#isError() value representing a parse
     * error}.
     */
    private static ResolvedJavaType getAnnotationFormatErrorType() {
        return GuestAccess.get().lookupType(Void.TYPE);
    }

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
            return Map.of(getAnnotationFormatErrorType(), new AnnotationValue(e));
        } catch (IllegalArgumentException | BufferUnderflowException | GenericSignatureFormatError e) {
            return Map.of(getAnnotationFormatErrorType(), new AnnotationValue(new AnnotationFormatError(e)));
        }
    };

    /**
     * Gets the annotation values associated with the parameters of {@code method}.
     *
     * @param method the annotated method to retrieve parameter annotation values from
     * @return a list of lists, where each inner list contains the annotation values for a single
     *         parameter of the annotated method, or null if the method has no annotated parameters
     */
    public List<List<AnnotationValue>> getParameterAnnotationValues(ResolvedJavaMethod method) {
        Annotated root = unwrap(method, null);
        return root != null ? getParameterAnnotationValuesFromRoot((ResolvedJavaMethod) root) : null;
    }

    private static List<List<AnnotationValue>> getParameterAnnotationValuesFromRoot(ResolvedJavaMethod rootElement) {
        try {
            var parsed = AnnotationValueSupport.getParameterAnnotationValues(rootElement);
            return parsed == null ? null : parsed.values();
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
            return AnnotationValueSupport.getTypeAnnotationValues(rootElement);
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
