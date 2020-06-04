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

import com.oracle.svm.core.util.VMError;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AnnotationsEncoding {

    final Annotation[] allAnnotations;
    final int startOfDeclaredAnnotations;

    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];
    private static final AnnotationsEncoding EMPTY_ANNOTATIONS_ENCODING = new AnnotationsEncoding(null, 0);

    private AnnotationsEncoding(Annotation[] allAnnotations, int startOfDeclaredAnnotations) {
        this.allAnnotations = allAnnotations;
        this.startOfDeclaredAnnotations = startOfDeclaredAnnotations;
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
        return startOfDeclaredAnnotations == that.startOfDeclaredAnnotations &&
                        Arrays.equals(allAnnotations, that.allAnnotations);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(startOfDeclaredAnnotations);
        result = 31 * result + Arrays.hashCode(allAnnotations);
        return result;
    }

    public Annotation[] getAnnotations() {
        return allAnnotations == null ? EMPTY_ANNOTATION_ARRAY : allAnnotations.clone();
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return filterByType(getAnnotations(), annotationClass);
    }

    public Annotation[] getDeclaredAnnotations() {
        if (allAnnotations == null) {
            return EMPTY_ANNOTATION_ARRAY;

        }

        int size = allAnnotations.length - startOfDeclaredAnnotations;
        if (size == 0) {
            return EMPTY_ANNOTATION_ARRAY;
        }

        Annotation[] declAnns = new Annotation[size];
        System.arraycopy(allAnnotations, startOfDeclaredAnnotations, declAnns, 0, size);
        return declAnns;
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

    public static AnnotationsEncoding decodeAnnotations(Object annotationsEncoding) {
        if (annotationsEncoding == null) {
            return EMPTY_ANNOTATIONS_ENCODING;
        } else if (annotationsEncoding instanceof ArrayStoreException) {
            /* JDK-7183985 was hit at image build time when the annotations were encoded. */
            throw (ArrayStoreException) annotationsEncoding;
        } else if (annotationsEncoding instanceof AnnotationsEncoding) {
            return (AnnotationsEncoding) annotationsEncoding;
        } else {
            VMError.shouldNotReachHere("Unexpected encoding for annotations in class: " + annotationsEncoding.getClass().getName());
            return null;
        }
    }

    public static Object encodeAnnotations(Set<Annotation> allAnnotations, Set<Annotation> declaredAnnotations) {
        if (allAnnotations == null || allAnnotations.isEmpty()) {
            return null;
        }

        if (declaredAnnotations == null || declaredAnnotations.isEmpty()) {
            return new AnnotationsEncoding(allAnnotations.toArray(new Annotation[0]), allAnnotations.size());
        }

        assert allAnnotations.size() >= declaredAnnotations.size();
        List<Annotation> head = new ArrayList<>();
        List<Annotation> tail = new ArrayList<>();
        for (Annotation a : allAnnotations) {
            if (!declaredAnnotations.contains(a)) {
                head.add(a);
            } else {
                tail.add(a);
            }
        }

        int position = head.size();
        Annotation[] encoding = new Annotation[head.size() + tail.size()];
        System.arraycopy(head.toArray(new Annotation[0]), 0, encoding, 0, head.size());
        System.arraycopy(tail.toArray(new Annotation[0]), 0, encoding, position, tail.size());

        return new AnnotationsEncoding(encoding, position);
    }
}
