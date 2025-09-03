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
package com.oracle.svm.hosted.webimage.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class AnnotationUtil {

    /**
     * This method is similar to {@link AnnotatedElement#getDeclaredAnnotationsByType} but avoids
     * the access of all annotations of the element, which is not allowed at image build time
     * because it initializes all annotation classes and their dependencies.
     *
     * In contrast to {@link AnnotatedElement#getDeclaredAnnotationsByType}, the order of the
     * annotations in the source code is not taken into account: this method always puts the direct
     * annotation before container annotations. Also, information about the container annotation
     * needs to be passed in as additional arguments to avoid complicated reflective lookups.
     */
    public static <A extends Annotation, C extends Annotation> List<A> getDeclaredAnnotationsByType(AnnotatedElement element,
                    Class<A> annotationClass, Class<C> containerClass, Function<C, A[]> valueFunction) {

        List<A> result = new ArrayList<>();
        A direct = element.getDeclaredAnnotation(annotationClass);
        if (direct != null) {
            result.add(direct);
        }
        C container = element.getDeclaredAnnotation(containerClass);
        if (container != null) {
            result.addAll(List.of(valueFunction.apply(container)));
        }
        return result;
    }
}
