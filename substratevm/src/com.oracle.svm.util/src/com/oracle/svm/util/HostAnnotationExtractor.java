/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.annotation.AnnotationFormatError;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.impl.AnnotationExtractor;

import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

/**
 * Internal {@link AnnotationAccess} backend for elements owned by the builder VM. This
 * implementation intentionally uses core reflection because builder-only classes do not
 * necessarily have guest JVMCI types. Consequently, lookups follow the JDK class initialization
 * behavior; in particular, {@link #getAnnotationTypes(AnnotatedElement)} materializes builder-owned
 * annotations and can initialize their annotation interfaces. Guest and application annotation
 * metadata must be queried through {@link GuestAnnotationAccess}.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
final class HostAnnotationExtractor implements AnnotationExtractor {

    /** Creates the fully isolated builder-context annotation backend. */
    HostAnnotationExtractor() {
    }

    @Override
    public <T extends Annotation> T extractAnnotation(AnnotatedElement element, Class<T> annotationType, boolean declaredOnly) {
        try {
            T result;
            // Checkstyle: allow direct annotation access
            result = declaredOnly ? element.getDeclaredAnnotation(annotationType) : element.getAnnotation(annotationType);
            // Checkstyle: disallow direct annotation access
            return result;
        } catch (LinkageError | AnnotationFormatError e) {
            /*
             * Preserve the best-effort lookup contract from SubstrateAnnotationExtractor: parse or
             * linkage failures behave as if the requested annotation was not present.
             */
            return null;
        }
    }

    @Override
    public boolean hasAnnotation(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        try {
            boolean result;
            // Checkstyle: allow direct annotation access
            result = element.isAnnotationPresent(annotationType);
            // Checkstyle: disallow direct annotation access
            return result;
        } catch (LinkageError | AnnotationFormatError e) {
            /*
             * Preserve the best-effort lookup contract from SubstrateAnnotationExtractor: parse or
             * linkage failures behave as if the requested annotation was not present.
             */
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<? extends Annotation>[] getAnnotationTypes(AnnotatedElement element) {
        try {
            Annotation[] annotations;
            // Checkstyle: allow direct annotation access
            annotations = element.getAnnotations();
            // Checkstyle: disallow direct annotation access
            return Arrays.stream(annotations)
                            .map(Annotation::annotationType)
                            .toArray(Class[]::new);
        } catch (LinkageError | AnnotationFormatError e) {
            /*
             * Mirror extractAnnotation/hasAnnotation and the guest extractor proxy: if annotation
             * metadata cannot be parsed, expose no annotation types instead of failing lookup.
             */
            return (Class<? extends Annotation>[]) new Class<?>[0];
        }
    }
}
