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
package com.oracle.svm.hosted.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.AnnotationExtractor;

import com.oracle.svm.core.util.VMError;

public interface AnnotationWrapper extends AnnotatedElement {
    AnnotatedElement getAnnotationRoot();

    default AnnotationValue[] getInjectedAnnotations() {
        return null;
    }

    @Override
    default boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return ImageSingletons.lookup(AnnotationExtractor.class).hasAnnotation(this, annotationClass);
    }

    @Override
    default <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return ImageSingletons.lookup(AnnotationExtractor.class).extractAnnotation(this, annotationClass, false);
    }

    @Override
    default <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
        return ImageSingletons.lookup(AnnotationExtractor.class).extractAnnotation(this, annotationClass, true);
    }

    @Override
    default Annotation[] getAnnotations() {
        throw VMError.shouldNotReachHere("Getting all annotations is not supported because it initializes all annotation classes and their dependencies");
    }

    @Override
    default Annotation[] getDeclaredAnnotations() {
        throw VMError.shouldNotReachHere("Getting all annotations is not supported because it initializes all annotation classes and their dependencies");
    }
}
