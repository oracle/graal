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
import java.lang.annotation.AnnotationFormatError;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Objects;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.impl.AnnotationExtractor;

import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.util.AnnotatedObjectAccess;
import com.oracle.svm.util.AnnotatedObjectAccessError;
import com.oracle.svm.util.GraalAccess;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.vm.ci.meta.annotation.Annotated;

/**
 * This class wraps all annotation accesses during the Native Image build. It relies on
 * {@link jdk.graal.compiler.annotation.AnnotationValueParser} to avoid class initialization.
 * <p>
 * The {@link SubstrateAnnotationExtractor} is tightly coupled with {@link AnnotationAccess}, which
 * provides implementations of {@link AnnotatedElement#isAnnotationPresent(Class)} and
 * {@link AnnotatedElement#getAnnotation(Class)}. {@link AnnotatedElement#getAnnotations()} must
 * never be used during Native Image generation because it initializes all annotation classes and
 * their dependencies.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
public class SubstrateAnnotationExtractor extends AnnotatedObjectAccess implements AnnotationExtractor {

    @Override
    public <T extends Annotation> T extractAnnotation(AnnotatedElement element, Class<T> annotationType, boolean declaredOnly) {
        try {
            return getAnnotation(toAnnotated(element), annotationType, declaredOnly);
        } catch (LinkageError | AnnotationFormatError e) {
            /*
             * Returning null essentially means that the element doesn't declare the annotationType,
             * but we cannot know that since the annotation parsing failed. However, this allows us
             * to defend against crashing the image builder if the user code references types
             * missing from the classpath.
             */
            return null;
        }
    }

    @Override
    public boolean hasAnnotation(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        try {
            return hasAnnotation(toAnnotated(element), annotationType);
        } catch (LinkageError | AnnotationFormatError e) {
            /*
             * Returning false essentially means that the element doesn't declare the
             * annotationType, but we cannot know that since the annotation parsing failed. However,
             * this allows us to defend against crashing the image builder if the user code
             * references types missing from the classpath.
             */
            return false;
        }
    }

    private static final Method packageGetPackageInfo = ReflectionUtil.lookupMethod(Package.class, "getPackageInfo");

    public static Annotated toAnnotated(AnnotatedElement element) {
        switch (element) {
            case null -> {
                return null;
            }
            case Annotated annotated -> {
                return annotated;
            }
            case Class<?> clazz -> {
                return GraalAccess.lookupType(clazz);
            }
            case Executable executable -> {
                return GraalAccess.lookupMethod(executable);
            }
            case Field field -> {
                return GraalAccess.lookupField(field);
            }
            case RecordComponent rc -> {
                return GraalAccess.lookupRecordComponent(rc);
            }
            case Package packageObject -> {
                try {
                    return GraalAccess.lookupType((Class<?>) packageGetPackageInfo.invoke(packageObject));
                } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    if (targetException instanceof LinkageError) {
                        throw (LinkageError) targetException;
                    }
                    throw new AnnotatedObjectAccessError(element, e);
                } catch (IllegalAccessException e) {
                    throw new AnnotatedObjectAccessError(element, e);
                }
            }
            default -> throw new AnnotatedObjectAccessError(element, (Throwable) null);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<? extends Annotation>[] getAnnotationTypes(AnnotatedElement element) {
        return getAnnotationValues(toAnnotated(element), false).values().stream() //
                        .map(AnnotationValue::getAnnotationType) //
                        .map(OriginalClassProvider::getJavaClass) //
                        .filter(Objects::nonNull) //
                        .toArray(Class[]::new);
    }
}
