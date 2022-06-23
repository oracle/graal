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
            if (GuardedAnnotationAccess.isAnnotationPresent(getAnnotationRoot(), annotationClass)) {
                return true;
            }
        }
        if (getSecondaryAnnotationRoot() != null) {
            return GuardedAnnotationAccess.isAnnotationPresent(getSecondaryAnnotationRoot(), annotationClass);
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
            Annotation[] rootAnnotations = declaredOnly ? GuardedAnnotationAccess.getDeclaredAnnotations(element.getAnnotationRoot())
                            : GuardedAnnotationAccess.getAnnotations(element.getAnnotationRoot());
            for (Annotation rootAnnotation : rootAnnotations) {
                if (!ignoredAnnotations.contains(rootAnnotation.annotationType())) {
                    annotations.add(rootAnnotation);
                }
            }
        }
        if (element.getSecondaryAnnotationRoot() != null) {
            Annotation[] secondaryRootAnnotations = declaredOnly ? GuardedAnnotationAccess.getDeclaredAnnotations(element.getSecondaryAnnotationRoot())
                            : GuardedAnnotationAccess.getAnnotations(element.getSecondaryAnnotationRoot());
            for (Annotation secondaryRootAnnotation : secondaryRootAnnotations) {
                if (!ignoredAnnotations.contains(secondaryRootAnnotation.annotationType())) {
                    annotations.add(secondaryRootAnnotation);
                }
            }
        }
        return annotations.toArray(new Annotation[0]);
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
            T rootAnnotation = declaredOnly ? GuardedAnnotationAccess.getDeclaredAnnotation(element.getAnnotationRoot(), annotationClass)
                            : GuardedAnnotationAccess.getAnnotation(element.getAnnotationRoot(), annotationClass);
            if (rootAnnotation != null) {
                return rootAnnotation;
            }
        }
        if (element.getSecondaryAnnotationRoot() != null) {
            return declaredOnly ? GuardedAnnotationAccess.getDeclaredAnnotation(element.getSecondaryAnnotationRoot(), annotationClass)
                            : GuardedAnnotationAccess.getAnnotation(element.getSecondaryAnnotationRoot(), annotationClass);
        }
        return null;
    }
}
