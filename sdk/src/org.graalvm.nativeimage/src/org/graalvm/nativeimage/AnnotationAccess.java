/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;

import org.graalvm.nativeimage.impl.AnnotationExtractor;

/**
 * This class provides methods to query annotation information on {@link AnnotatedElement}s while
 * trying to prevent, at image build time, side-effecting changes that impact the analysis results.
 *
 * The getAnnotation implementation in the JDK for Class, Field, and Method initializes the classes
 * of all annotations present on that element, not just the class of the queried annotation. This
 * leads to problems when not all annotation classes are present on the classpath/modulepath:
 * querying an annotation whose class is present can fail with an exception because not all
 * annotation classes are present. When this class's methods are called at image build time, then
 * the minimal amount of class initialization is done: {@link #getAnnotation} only initializes the
 * queried annotation class (when the annotation is actually present and therefore instantiated);
 * {@link #isAnnotationPresent} and {@link #getAnnotationTypes} do not initialize any classes.
 *
 * When methods of this class are called not at image build time, i.e., at image run time or during
 * the execution of a Java application not involving native image, then the JDK method to query the
 * annotation is invoked. In these cases, there is no difference to the class initialization
 * behavior of the JDK.
 *
 * Note that there is intentionally no `getAnnotations` method to query all annotations: all
 * annotation classes must be initialized anyways by this method, so the JDK method can be invoke
 * directly. In the image generator it should be generally avoided to use `getAnnotations`.
 *
 * @since 22.3
 */
public final class AnnotationAccess {

    /**
     * Implementation of {@link AnnotatedElement#isAnnotationPresent(Class)}.
     *
     * @since 22.3
     */
    public static boolean isAnnotationPresent(AnnotatedElement element, Class<? extends Annotation> annotationClass) {
        if (ImageInfo.inImageBuildtimeCode()) {
            return ImageSingletons.lookup(AnnotationExtractor.class).hasAnnotation(element, annotationClass);
        } else {
            return element.isAnnotationPresent(annotationClass);
        }
    }

    /**
     * Implementation of {@link AnnotatedElement#getAnnotation(Class)} .
     *
     * @since 22.3
     */
    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T getAnnotation(AnnotatedElement element, Class<T> annotationType) {
        if (ImageInfo.inImageBuildtimeCode()) {
            return ImageSingletons.lookup(AnnotationExtractor.class).extractAnnotation(element, annotationType, false);
        } else {
            return element.getAnnotation(annotationType);
        }
    }

    /**
     * Implementation for retrieving all {@link Annotation#annotationType()}s for a {@code element}.
     *
     * @since 22.3
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends Annotation>[] getAnnotationTypes(AnnotatedElement element) {
        if (ImageInfo.inImageBuildtimeCode()) {
            return ImageSingletons.lookup(AnnotationExtractor.class).getAnnotationTypes(element);
        } else {
            return Arrays.stream(element.getAnnotations()).map(Annotation::annotationType).toArray(Class[]::new);
        }
    }

    private AnnotationAccess() {
    }
}
