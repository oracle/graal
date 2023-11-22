/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import org.graalvm.nativeimage.impl.AnnotationExtractor;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;

public class StandaloneAnnotationExtractor implements AnnotationExtractor {
    // Checkstyle: allow direct annotation access
    @Override
    public boolean hasAnnotation(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return element.isAnnotationPresent(annotationType);
    }

    @Override
    public <T extends Annotation> T extractAnnotation(AnnotatedElement element, Class<T> annotationType, boolean declaredOnly) {
        if (declaredOnly) {
            return element.getDeclaredAnnotation(annotationType);
        } else {
            return element.getAnnotation(annotationType);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<? extends Annotation>[] getAnnotationTypes(AnnotatedElement element) {
        return Arrays.stream(element.getAnnotations()).map(Annotation::annotationType).toArray(Class[]::new);
    }
}
