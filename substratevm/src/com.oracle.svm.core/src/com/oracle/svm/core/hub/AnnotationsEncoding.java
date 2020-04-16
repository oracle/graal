/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Objects;

public final class AnnotationsEncoding {

    final Annotation[] allAnnotations;
    final Annotation[] declaredAnnotations;
    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];
    private static final AnnotationsEncoding EMPTY_ANNOTATIONS_ENCODING = new AnnotationsEncoding(null, null);

    public AnnotationsEncoding(Annotation[] allAnnotations, Annotation[] declaredAnnotations) {
        this.allAnnotations = allAnnotations;
        this.declaredAnnotations = declaredAnnotations;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        AnnotationsEncoding that = (AnnotationsEncoding) other;
        return Arrays.equals(allAnnotations, that.allAnnotations) &&
                        Arrays.equals(declaredAnnotations, that.declaredAnnotations);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(allAnnotations) + Arrays.hashCode(declaredAnnotations);
    }

    public static AnnotationsEncoding decodeAnnotations(Object annotationsEncoding) {
        if (annotationsEncoding == null) {
            return EMPTY_ANNOTATIONS_ENCODING;
        } else if (annotationsEncoding instanceof ArrayStoreException) {
            /* JDK-7183985 was hit at image build time when the annotations were encoded. */
            throw (ArrayStoreException) annotationsEncoding;
        } else if (annotationsEncoding instanceof AnnotationsEncoding) {
            return (AnnotationsEncoding) annotationsEncoding;
        } else {
            throw new ArrayStoreException("annotations encoding should be of type: " + AnnotationsEncoding.class.getName());
        }
    }

    public Annotation[] getAnnotations() {
        return allAnnotations == null ? EMPTY_ANNOTATION_ARRAY : allAnnotations;
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return filterByType(getAnnotations(), annotationClass);
    }

    public Annotation[] getDeclaredAnnotations() {
        return allAnnotations == null ? EMPTY_ANNOTATION_ARRAY : declaredAnnotations;
    }

    public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
        return filterByType(getDeclaredAnnotations(), annotationClass);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Annotation> T filterByType(Annotation[] all, Class<T> annotationClass) {
        Objects.requireNonNull(annotationClass);
        for (Annotation annotation : all) {
            if (annotationClass.isInstance(annotation)) {
                return (T) annotation;
            }
        }
        return null;
    }
}
