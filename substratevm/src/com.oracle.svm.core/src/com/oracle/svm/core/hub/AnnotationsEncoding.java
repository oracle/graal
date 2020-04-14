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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AnnotationsEncoding {

    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];

    public static boolean equals(Object encode, Object other) {
        if (encode == other) {
            return true;
        }

        try {
            Map.Entry<Annotation[], Annotation[]> first = AnnotationsEncoding.decodeAll(encode);
            Map.Entry<Annotation[], Annotation[]> second = AnnotationsEncoding.decodeAll(other);
            return Arrays.equals(first.getKey(), second.getKey()) && Arrays.equals(first.getValue(), second.getValue());
        } catch (ArrayStoreException ignored) {
            return false;
        }
    }

    public static Map.Entry<Annotation[], Annotation[]> decodeAll(Object encoding) {
        if (encoding == null) {
            return new AbstractMap.SimpleEntry<>(EMPTY_ANNOTATION_ARRAY, EMPTY_ANNOTATION_ARRAY);
        }

        if (encoding instanceof ArrayStoreException) {
            /* JDK-7183985 was hit at image build time when the annotations were encoded. */
            throw (ArrayStoreException) encoding;
        }

        if (!(encoding instanceof Object[])) {
            throw new ArrayStoreException("bad annotations encoding ");
        }

        Object[] objAnnotations = ((Object[]) encoding);
        if (objAnnotations[0] instanceof Annotation) {
            return new AbstractMap.SimpleEntry<>(((Annotation[]) objAnnotations), EMPTY_ANNOTATION_ARRAY);
        }

        if (!(objAnnotations[0] instanceof Integer)) {
            throw new ArrayStoreException("bad annotations encoding.First element should be the size of declared annotations");
        }
        int position = (Integer) objAnnotations[0];
        Annotation[] allAnnotations = new Annotation[objAnnotations.length - 1];
        for (int i = 0; i < allAnnotations.length; i++) {
            allAnnotations[i] = (Annotation) objAnnotations[i + 1];
        }
        Annotation[] declared = new Annotation[allAnnotations.length - position];
        System.arraycopy(allAnnotations, position, declared, 0, declared.length);
        return new AbstractMap.SimpleEntry<>(allAnnotations, declared);

    }

    public static Object encodeAnnotations(Set<Annotation> allAnnotations, Set<Annotation> declaredAnnotations) {
        if (allAnnotations == null || allAnnotations.isEmpty()) {
            return null;
        }

        if (declaredAnnotations == null || declaredAnnotations.isEmpty()) {
            return allAnnotations.toArray(new Annotation[0]);
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
        Object[] encoding = new Object[head.size() + tail.size() + 1];
        encoding[0] = position;
        System.arraycopy(head.toArray(new Annotation[0]), 0, encoding, 1, head.size());
        System.arraycopy(tail.toArray(new Annotation[0]), 0, encoding, position + 1, tail.size());

        return encoding;
    }

    public static <T extends Annotation> T getAnnotation(Object annotationsEncoding, Class<T> annotationClass) {
        return filterByType(getAnnotations(annotationsEncoding), annotationClass);
    }

    public static Annotation[] getDeclaredAnnotations(Object annotationsEncoding) {
        return decodeAll(annotationsEncoding).getValue();
    }

    public static Annotation[] getAnnotations(Object annotationsEncoding) {
        return decodeAll(annotationsEncoding).getKey();
    }

    public static <T extends Annotation> T getDeclaredAnnotation(Object annotationsEncoding, Class<T> annotationClass) {
        return filterByType(getDeclaredAnnotations(annotationsEncoding), annotationClass);
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
