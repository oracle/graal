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
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.AnnotationExtractor;

// Checkstyle: allow direct annotation access

public interface AnnotationWrapper extends AnnotatedElement {
    AnnotatedElement getAnnotationRoot();

    default AnnotatedElement getSecondaryAnnotationRoot() {
        return null;
    }

    default Annotation[] getInjectedAnnotations() {
        return null;
    }

    default Class<? extends Annotation>[] getIgnoredAnnotations() {
        return null;
    }

    @Override
    default boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        if (getIgnoredAnnotations() != null) {
            for (Class<? extends Annotation> ignoredAnnotation : getIgnoredAnnotations()) {
                if (ignoredAnnotation == annotationClass) {
                    return false;
                }
            }
        }
        if (getInjectedAnnotations() != null) {
            for (Annotation injectedAnnotation : getInjectedAnnotations()) {
                if (injectedAnnotation.annotationType() == annotationClass) {
                    return true;
                }
            }
        }
        if (getAnnotationRoot() != null) {
            if (AnnotationAccess.isAnnotationPresent(getAnnotationRoot(), annotationClass)) {
                return true;
            }
        }
        if (getSecondaryAnnotationRoot() != null) {
            return AnnotationAccess.isAnnotationPresent(getSecondaryAnnotationRoot(), annotationClass);
        }
        return false;
    }

    @Override
    default Annotation[] getAnnotations() {
        return getAnnotations(this, false);
    }

    @Override
    default Annotation[] getDeclaredAnnotations() {
        return getAnnotations(this, true);
    }

    private static Annotation[] getAnnotations(AnnotationWrapper element, boolean declaredOnly) {
        List<Annotation> annotations = new ArrayList<>();
        List<Class<? extends Annotation>> ignoredAnnotations = element.getIgnoredAnnotations() == null ? Collections.emptyList() : Arrays.asList(element.getIgnoredAnnotations());
        if (element.getInjectedAnnotations() != null) {
            annotations.addAll(Arrays.asList(element.getInjectedAnnotations()));
        }
        if (element.getAnnotationRoot() != null) {
            for (Annotation rootAnnotation : getRootAnnotations(element.getAnnotationRoot(), declaredOnly)) {
                if (!ignoredAnnotations.contains(rootAnnotation.annotationType())) {
                    annotations.add(rootAnnotation);
                }
            }
        }
        if (element.getSecondaryAnnotationRoot() != null) {
            for (Annotation secondaryRootAnnotation : getRootAnnotations(element.getSecondaryAnnotationRoot(), declaredOnly)) {
                if (!ignoredAnnotations.contains(secondaryRootAnnotation.annotationType())) {
                    annotations.add(secondaryRootAnnotation);
                }
            }
        }
        return annotations.toArray(new Annotation[0]);
    }

    private static Annotation[] getRootAnnotations(AnnotatedElement annotationRoot, boolean declaredOnly) {
        try {
            if (declaredOnly) {
                return annotationRoot.getDeclaredAnnotations();
            } else {
                return annotationRoot.getAnnotations();
            }
        } catch (LinkageError e) {
            /*
             * Returning an empty array essentially means that the element doesn't declare any
             * annotations, but we know that it is not true since the reason the annotation parsing
             * failed is because some annotation referenced a missing class. However, this allows us
             * to defend against crashing the image builder if the user code references types
             * missing from the classpath.
             */
            return new Annotation[0];
        }
    }

    @Override
    default <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return getAnnotation(this, annotationClass, false);
    }

    @Override
    default <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
        return getAnnotation(this, annotationClass, true);
    }

    private static <T extends Annotation> T getAnnotation(AnnotationWrapper element, Class<T> annotationClass, boolean declaredOnly) {
        if (element.getIgnoredAnnotations() != null) {
            for (Class<? extends Annotation> ignoredAnnotation : element.getIgnoredAnnotations()) {
                if (ignoredAnnotation == annotationClass) {
                    return null;
                }
            }
        }
        if (element.getInjectedAnnotations() != null) {
            for (Annotation injectedAnnotation : element.getInjectedAnnotations()) {
                if (injectedAnnotation.annotationType() == annotationClass) {
                    return annotationClass.cast(injectedAnnotation);
                }
            }
        }
        if (element.getAnnotationRoot() != null) {
            T rootAnnotation = getRootAnnotation(element.getAnnotationRoot(), annotationClass, declaredOnly);
            if (rootAnnotation != null) {
                return rootAnnotation;
            }
        }
        if (element.getSecondaryAnnotationRoot() != null) {
            return getRootAnnotation(element.getSecondaryAnnotationRoot(), annotationClass, declaredOnly);
        }
        return null;
    }

    private static <T extends Annotation> T getRootAnnotation(AnnotatedElement annotationRoot, Class<T> annotationClass, boolean declaredOnly) {
        if (ImageInfo.inImageBuildtimeCode()) {
            return ImageSingletons.lookup(AnnotationExtractor.class).extractAnnotation(annotationRoot, annotationClass, declaredOnly);
        } else {
            if (declaredOnly) {
                return annotationRoot.getDeclaredAnnotation(annotationClass);
            } else {
                return annotationRoot.getAnnotation(annotationClass);
            }
        }
    }
}
