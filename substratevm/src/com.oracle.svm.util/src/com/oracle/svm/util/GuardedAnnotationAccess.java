/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;

/**
 * Wrapper class for annotation access that defends against
 * https://bugs.openjdk.java.net/browse/JDK-7183985: when an annotation declares a Class<?> array
 * parameter and one of the referenced classes is not present on the classpath parsing the
 * annotations will result in an ArrayStoreException instead of caching of a
 * TypeNotPresentExceptionProxy. This is a problem in JDK8 but was fixed in JDK11+. This wrapper
 * class also defends against incomplete class path issues. If the element for which annotations are
 * queried is a JMVCI value, i.e., a HotSpotResolvedJavaField, or HotSpotResolvedJavaMethod, the
 * annotations are read via HotSpotJDKReflection using the
 * getFieldAnnotation()/getMethodAnnotation() methods which first construct the field/method object
 * via CompilerToVM.asReflectionField()/CompilerToVM.asReflectionExecutable() which eagerly try to
 * resolve the types referenced in the element signature. If a field declared type or a method
 * return type is missing then JVMCI throws a NoClassDefFoundError.
 */
public final class GuardedAnnotationAccess {

    public static boolean isAnnotationPresent(AnnotatedElement element, Class<? extends Annotation> annotationClass) {
        if (ImageInfo.inImageBuildtimeCode()) {
            return isAnnotationPresent(ImageSingletons.lookup(AnnotationExtracter.class), element, annotationClass);
        } else {
            return DirectAnnotationAccess.isAnnotationPresent(element, annotationClass);
        }
    }

    public static boolean isAnnotationPresent(AnnotationExtracter extracter, AnnotatedElement element, Class<? extends Annotation> annotationClass) {
        try {
            return extracter.hasAnnotation(element, annotationClass);
        } catch (ArrayStoreException | LinkageError e) {
            /*
             * Returning null essentially means that the element doesn't declare the annotationType,
             * but we cannot know that since the annotation parsing failed. However, this allows us
             * to defend against crashing the image builder if the above JDK bug is encountered in
             * user code or if the user code references types missing from the classpath.
             */
            return false;
        }
    }

    public static <T extends Annotation> T getAnnotation(AnnotatedElement element, Class<T> annotationType) {
        if (ImageInfo.inImageBuildtimeCode()) {
            return getAnnotation(ImageSingletons.lookup(AnnotationExtracter.class), element, annotationType);
        } else {
            return DirectAnnotationAccess.getAnnotation(element, annotationType);
        }
    }

    public static <T extends Annotation> T getAnnotation(AnnotationExtracter extracter, AnnotatedElement element, Class<T> annotationType) {
        try {
            return extracter.extractAnnotation(element, annotationType, false);
        } catch (ArrayStoreException | LinkageError e) {
            /*
             * Returning null essentially means that the element doesn't declare the annotationType,
             * but we cannot know that since the annotation parsing failed. However, this allows us
             * to defend against crashing the image builder if the above JDK bug is encountered in
             * user code or if the user code references types missing from the classpath.
             */
            return null;
        }
    }

    public static Annotation[] getAnnotations(AnnotatedElement element) {
        try {
            return DirectAnnotationAccess.getAnnotations(element);
        } catch (ArrayStoreException | LinkageError e) {
            /*
             * Returning an empty array essentially means that the element doesn't declare any
             * annotations, but we know that it is not true since the reason the annotation parsing
             * failed is because some annotation referenced a missing class. However, this allows us
             * to defend against crashing the image builder if the above JDK bug is encountered in
             * user code or if the user code references types missing from the classpath.
             */
            return new Annotation[0];
        }
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends Annotation>[] getAnnotationTypes(AnnotatedElement element) {
        if (ImageInfo.inImageBuildtimeCode()) {
            return ImageSingletons.lookup(AnnotationExtracter.class).getAnnotationTypes(element);
        } else {
            return Arrays.stream(DirectAnnotationAccess.getAnnotations(element)).map(Annotation::annotationType).toArray(Class[]::new);
        }
    }

    public static <T extends Annotation> T getDeclaredAnnotation(AnnotatedElement element, Class<T> annotationType) {
        if (ImageInfo.inImageBuildtimeCode()) {
            return getDeclaredAnnotation(ImageSingletons.lookup(AnnotationExtracter.class), element, annotationType);
        } else {
            return DirectAnnotationAccess.getDeclaredAnnotation(element, annotationType);
        }
    }

    public static <T extends Annotation> T getDeclaredAnnotation(AnnotationExtracter extracter, AnnotatedElement element, Class<T> annotationType) {
        try {
            return extracter.extractAnnotation(element, annotationType, true);
        } catch (ArrayStoreException | LinkageError e) {
            /*
             * Returning null essentially means that the element doesn't declare the annotationType,
             * but we cannot know that since the annotation parsing failed. However, this allows us
             * to defend against crashing the image builder if the above JDK bug is encountered in
             * user code or if the user code references types missing from the classpath.
             */
            return null;
        }
    }

    public static Annotation[] getDeclaredAnnotations(AnnotatedElement element) {
        try {
            return DirectAnnotationAccess.getDeclaredAnnotations(element);
        } catch (ArrayStoreException | LinkageError e) {
            /*
             * Returning an empty array essentially means that the element doesn't declare any
             * annotations, but we know that it is not true since the reason the annotation parsing
             * failed is because it at least one annotation referenced a missing class. However,
             * this allows us to defend against crashing the image builder if the above JDK bug is
             * encountered in user code or if the user code references types missing from the
             * classpath.
             */
            return new Annotation[0];
        }
    }
}
