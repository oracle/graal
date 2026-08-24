/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
import java.util.function.Function;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.AnnotationExtractor;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueType;
import jdk.graal.compiler.annotation.EnumElement;
import jdk.graal.compiler.annotation.TypeAnnotationValue;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.vmaccess.HostAnnotationValueConverter;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

/**
 * At image build time, provides builder-to-guest annotation access for SVM internal features and
 * code. Same-VM annotation queries must use {@link org.graalvm.nativeimage.AnnotationAccess}.
 * Class-based builder-to-guest convenience methods require the annotation class to be shared and
 * resolvable in both contexts; guest-only annotation types must use the JVMCI overloads. Runtime
 * use is restricted to Crema and Ristretto compiler paths that query runtime JVMCI elements; all
 * other runtime code must use {@link org.graalvm.nativeimage.AnnotationAccess}.
 */
public final class GuestAnnotationAccess {

    /** Prevents instantiation. */
    private GuestAnnotationAccess() {
    }

    /**
     * Lazily created singleton to be used when outside the scope of a Native Image build.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    static class Lazy {
        static final AnnotatedObjectAccess instance;
        static final Throwable initLocation;
        static {
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

    /**
     * The hosted image builder creates the {@link AnnotationExtractor} before publishing it
     * globally. Registering it here avoids transient fallback to {@link Lazy} in that startup
     * window.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private static AnnotatedObjectAccess builderToGuestBackend;

    /**
     * Initializes the same-context and builder-to-guest annotation backends after
     * {@link GuestAccess} has been planted.
     *
     * Non-isolated mode uses one legacy extractor for both directions. Fully isolated mode uses a
     * reflection-based extractor for builder-owned metadata and a JVMCI-based backend for guest
     * metadata.
     *
     * @return the same-context extractor to publish through
     *         {@link org.graalvm.nativeimage.AnnotationAccess}
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static AnnotationExtractor initializeBackends() {
        AnnotationExtractor sameContextExtractor;
        AnnotatedObjectAccess backend;
        if (GuestAccess.get().isFullyIsolated()) {
            sameContextExtractor = new HostAnnotationExtractor();
            backend = new GuestAnnotationBackend();
        } else {
            SubstrateAnnotationExtractor legacyExtractor = new SubstrateAnnotationExtractor();
            sameContextExtractor = legacyExtractor;
            backend = legacyExtractor;
        }
        installBuilderToGuestBackend(backend);
        return sameContextExtractor;
    }

    /*
     * These accesses do not need synchronization: the hosted backend is installed during the
     * single-threaded image-builder bootstrap, and the fallback path only publishes immutable
     * singletons while rejecting any attempt to mix the hosted and lazy variants in one VM.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    private static void installBuilderToGuestBackend(AnnotatedObjectAccess backend) {
        if (instanceIsSingleton == null) {
            instanceIsSingleton = true;
        } else if (!instanceIsSingleton) {
            throw new GraalError(Lazy.initLocation, "Cannot install the image-build annotation backend after Lazy.instance initialized");
        }
        GraalError.guarantee(builderToGuestBackend == null || builderToGuestBackend == backend,
                        "Conflicting builder-to-guest annotation backends");
        builderToGuestBackend = backend;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static AnnotatedObjectAccess instance() {
        if (builderToGuestBackend != null) {
            GraalError.guarantee(instanceIsSingleton == null || instanceIsSingleton, "Cannot use the image-build annotation backend and Lazy.instance in one process");
            instanceIsSingleton = true;
            return builderToGuestBackend;
        }
        // Fall back to a local backend when no image-build backend is available (e.g.,
        // running `mx unittest com.oracle.graal.pointsto.standalone.test`).
        GraalError.guarantee(instanceIsSingleton == null || !instanceIsSingleton, "Cannot use the image-build annotation backend and Lazy.instance in one process");
        instanceIsSingleton = false;
        return Lazy.instance;
    }

    /**
     * Gets the declared annotations of {@code annotated}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static Map<ResolvedJavaType, AnnotationValue> getDeclaredAnnotationValues(Annotated annotated) {
        return instance().getDeclaredAnnotationValues(annotated);
    }

    /**
     * Gets the annotations associated with the parameters of {@code method}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static List<List<AnnotationValue>> getParameterAnnotationValues(ResolvedJavaMethod method) {
        return instance().getParameterAnnotationValues(method);
    }

    /**
     * Gets the type annotations associated with {@code annotated}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static List<TypeAnnotationValue> getTypeAnnotationValues(Annotated annotated) {
        return instance().getTypeAnnotationValues(annotated);
    }

    /**
     * Gets the default value for the annotation member represented by {@code method}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static Object getAnnotationDefaultValue(ResolvedJavaMethod method) {
        return instance().getAnnotationDefaultValue(method);
    }

    /**
     * Gets the annotation of type {@code annotationType} from {@code element} as an
     * {@link AnnotationValue} object if such an annotation is present, else null.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static <T extends Annotation> AnnotationValue getAnnotationValue(Annotated element, Class<T> annotationType) {
        return instance().getAnnotationValue(element, annotationType);
    }

    /**
     * Gets the annotation of type {@code annotationType} from {@code element} and immediately
     * applies {@code factory} to its {@link AnnotationValue}. The factory receives {@code null}
     * when the annotation is not present. Generated guest-value factories eagerly read every
     * annotation member, so malformed member values fail during this call rather than when an
     * individual record component is accessed.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static <T extends Annotation, U> U getAnnotationValue(Annotated element, Class<T> annotationType, Function<AnnotationValue, U> factory) {
        return factory.apply(getAnnotationValue(element, annotationType));
    }

    /**
     * Gets the annotation represented by {@code annotationType}.
     *
     * @param declaredOnly whether inherited annotations must be ignored
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static AnnotationValue getAnnotationValue(Annotated element, ResolvedJavaType annotationType, boolean declaredOnly) {
        return instance().getAnnotationValue(element, requireAnnotationType(annotationType), declaredOnly);
    }

    /** Gets the JVMCI types of all annotations present on {@code element}. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static List<ResolvedJavaType> getAnnotationTypes(Annotated element) {
        return instance().getAnnotationTypes(element);
    }

    /**
     * Gets the annotation of type {@code annotationType} from {@code element} if such an annotation
     * is present, else null.
     * <p>
     * This method is reserved for compiler code shared between image building and the Crema or
     * Ristretto runtime compiler paths. At image build time, it materializes a builder annotation
     * from guest metadata; at image runtime, it performs a same-context lookup on
     * {@link RuntimeAnnotated} JVMCI elements. Hosted-only code must consume {@link AnnotationValue}
     * or a specialized guest annotation DTO instead.
     */
    public static <T extends Annotation> T getAnnotation(Annotated element, Class<T> annotationType) {
        // Checkstyle: allow direct annotation access
        if (ImageInfo.inImageRuntimeCode()) {
            if (element instanceof RuntimeAnnotated ra) {
                return ra.getAnnotation(annotationType);
            }
            throw new IllegalArgumentException("Cannot cast " + element.getClass() + " to " + RuntimeAnnotated.class.getName() + ": " + element);
        }
        return getHostedAnnotation(element, annotationType);
        // Checkstyle: disallow direct annotation access
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static <T extends Annotation> T getHostedAnnotation(Annotated element, Class<T> annotationType) {
        AnnotationValue annotationValue = instance().getAnnotationValue(element, annotationType);
        return annotationValue == null ? null : HostAnnotationValueConverter.toAnnotation(annotationValue, annotationType, OriginalClassProvider::getJavaClass);
    }

    /**
     * Retrieves annotation values of type {@code annotationClass} from {@code element}, including
     * both a direct annotation and annotations nested in the {@code value} member of an annotation
     * of type {@code containerClass}.
     * <p>
     * Unlike {@link AnnotatedElement#getAnnotationsByType}, this method does not initialize all
     * annotation classes and their dependencies or materialize builder annotation objects. A
     * direct annotation is returned first, followed by annotations from the container.
     *
     * @param element the annotated element to retrieve annotation values from
     * @param annotationClass the type of annotation to retrieve
     * @param containerClass the type of container annotation that may contain the requested
     *            annotations
     * @return the matching annotation values in direct-first, container-second order
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static List<AnnotationValue> getAnnotationValuesByType(Annotated element, Class<? extends Annotation> annotationClass,
                    Class<? extends Annotation> containerClass) {
        List<AnnotationValue> result = new ArrayList<>();
        AnnotationValue direct = getAnnotationValue(element, annotationClass);
        if (direct != null) {
            result.add(direct);
        }
        AnnotationValue container = getAnnotationValue(element, containerClass);
        if (container != null) {
            result.addAll(container.getList("value", AnnotationValue.class));
        }
        return result;
    }

    /**
     * Determines if an annotation of type {@code annotationType} is present on {@code element}.
     */
    public static boolean isAnnotationPresent(Annotated element, Class<? extends Annotation> annotationType) {
        if (ImageInfo.inImageRuntimeCode()) {
            return getAnnotation(element, annotationType) != null;
        }
        return instance().hasAnnotation(element, annotationType);
    }

    /**
     * Determines if an annotation of type {@code annotationType} is present on {@code element}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean isAnnotationPresent(Annotated element, ResolvedJavaType annotationType) {
        return instance().hasAnnotation(element, requireAnnotationType(annotationType));
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
    @Platforms(Platform.HOSTED_ONLY.class)
    public static <T extends Annotation> AnnotationValue newAnnotationValue(Class<T> annotationType, Object... elements) {
        return newAnnotationValue(GuestAccess.get().lookupType(annotationType), elements);
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
    public static <T extends Annotation> AnnotationValue newAnnotationValue(ResolvedJavaType annotationType, Object... elements) {
        requireAnnotationType(annotationType);
        if ((elements.length % 2) != 0) {
            throw new IllegalArgumentException("Elements must be a sequence of (name,value) pairs");
        }
        AnnotationValueType annotationValueType = AnnotationValueType.getInstance(annotationType);
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
                throw new IllegalArgumentException(String.format("%s does not define an element named %s", annotationType.toClassName(), name));
            }
            if (elementValue instanceof Class<?> c) {
                String internalName = "L" + c.getName().replace(".", "/") + ";";
                elementValue = UnresolvedJavaType.create(internalName).resolve(annotationType);
            } else if (elementValue instanceof Enum<?> e) {
                String internalName = "L" + e.getClass().getName().replace(".", "/") + ";";
                ResolvedJavaType enumType = UnresolvedJavaType.create(internalName).resolve(annotationType);
                elementValue = new EnumElement(enumType, e.name());
            }
            if (!AnnotationValueType.matchesElementType(elementValue, elementType)) {
                throw new IllegalArgumentException(String.format("element '%s' is not of type %s: %s", name, elementType.toJavaName(), elementValue));
            }
            elementsMap.put(name, elementValue);
        }
        return new AnnotationValue(annotationType, elementsMap);
    }

    private static ResolvedJavaType requireAnnotationType(ResolvedJavaType annotationType) {
        Objects.requireNonNull(annotationType, "annotationType");
        if (!annotationType.isAnnotation()) {
            throw new IllegalArgumentException("Type is not an annotation: " + annotationType.toJavaName());
        }
        return annotationType;
    }
}
