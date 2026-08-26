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
import java.util.Map;
import java.util.Objects;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

/**
 * Internal hosted-only metadata backend for annotations on guest-owned JVMCI elements.
 *
 * This class exposes annotation metadata across the builder-to-guest boundary. Methods inherited
 * from {@link AnnotatedObjectAccess} that materialize {@link Annotation} instances are disabled so
 * that {@link AnnotationValue} remains the boundary representation. Hosted callers must use
 * {@link GuestAnnotationAccess}.
 */
@Platforms(Platform.HOSTED_ONLY.class)
final class GuestAnnotationBackend extends AnnotatedObjectAccess {

    /** Creates the fully isolated builder-to-guest metadata backend. */
    GuestAnnotationBackend() {
    }

    /**
     * Queries by a builder annotation class only when that class is shared with and resolves in the
     * guest context. Guest-only annotation types must use the JVMCI overload.
     */
    @Override
    public boolean hasAnnotation(Annotated element, Class<? extends Annotation> annotationType) {
        return hasAnnotation(element, lookupSharedAnnotationType(annotationType));
    }

    /**
     * Queries by a builder annotation class only when that class is shared with and resolves in the
     * guest context. The result remains an {@link AnnotationValue}; this backend never materializes
     * a builder annotation instance.
     */
    @Override
    public <T extends Annotation> AnnotationValue getAnnotationValue(Annotated element, Class<T> annotationType) {
        return getAnnotationValue(element, lookupSharedAnnotationType(annotationType), false);
    }

    /**
     * Preserves best-effort guest metadata lookup by treating linkage and malformed-annotation
     * failures as an empty result.
     */
    @Override
    public Map<ResolvedJavaType, AnnotationValue> getAnnotationValues(Annotated element, boolean declaredOnly) {
        try {
            return super.getAnnotationValues(element, declaredOnly);
        } catch (LinkageError | AnnotationFormatError e) {
            return Map.of();
        }
    }

    /**
     * This metadata backend cannot materialize builder annotations from guest metadata. Callers
     * must use {@link GuestAnnotationAccess}, which owns boundary conversion.
     */
    @Override
    public <T extends Annotation> T getAnnotation(Annotated element, Class<T> annotationType) {
        throw annotationMaterializationUnsupported();
    }

    /**
     * This metadata backend cannot materialize builder annotations from guest metadata. Callers
     * must use {@link GuestAnnotationAccess}, which owns boundary conversion.
     */
    @Override
    protected <T extends Annotation> T getAnnotation(Annotated element, Class<T> annotationType, boolean declaredOnly) {
        throw annotationMaterializationUnsupported();
    }

    /**
     * This metadata backend exposes {@link AnnotationValue} as its boundary representation and
     * therefore cannot convert it to a builder annotation instance.
     */
    @Override
    public <T extends Annotation> T asAnnotation(AnnotationValue annotationValue, Class<T> annotationType) {
        throw annotationMaterializationUnsupported();
    }

    private static ResolvedJavaType lookupSharedAnnotationType(Class<? extends Annotation> annotationType) {
        Objects.requireNonNull(annotationType, "annotationType");
        GuestAccess access = GuestAccess.get();
        ResolvedJavaType resolvedType;
        try {
            resolvedType = access.lookupType(annotationType);
        } catch (LinkageError e) {
            throw new IllegalArgumentException("Annotation class must be shared and resolvable in the guest context: " + annotationType.getName(), e);
        }
        if (!resolvedType.isAnnotation()) {
            throw new IllegalArgumentException("Resolved guest type is not an annotation type: " + annotationType.getName());
        }
        return resolvedType;
    }

    /** Creates the failure used by APIs that would materialize annotations in the builder VM. */
    private static UnsupportedOperationException annotationMaterializationUnsupported() {
        return new UnsupportedOperationException("GuestAnnotationBackend exposes AnnotationValue metadata only; materialize annotations through GuestAnnotationAccess");
    }
}
