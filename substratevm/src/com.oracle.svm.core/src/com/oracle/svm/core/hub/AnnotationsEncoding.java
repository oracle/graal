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
import java.util.Objects;

public final class AnnotationsEncoding {

    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];

    public static Annotation[] getAnnotations(Object annotationsEncoding) {
        if (annotationsEncoding == null) {
            return EMPTY_ANNOTATION_ARRAY;
        } else if (annotationsEncoding instanceof Annotation[]) {
            return ((Annotation[]) annotationsEncoding).clone();
        } else {
            return new Annotation[]{(Annotation) annotationsEncoding};
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T getAnnotation(Object annotationsEncoding, Class<T> annotationClass) {
        Objects.requireNonNull(annotationClass);

        if (annotationsEncoding instanceof Annotation[]) {
            for (Annotation annotation : (Annotation[]) annotationsEncoding) {
                if (annotationClass.isInstance(annotation)) {
                    return (T) annotation;
                }
            }
        } else if (annotationClass.isInstance(annotationsEncoding)) {
            return (T) annotationsEncoding;
        }
        return null;
    }
}
