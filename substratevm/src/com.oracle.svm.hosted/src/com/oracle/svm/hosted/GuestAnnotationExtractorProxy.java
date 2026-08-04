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
package com.oracle.svm.hosted;

import java.lang.annotation.Inherited;
import java.lang.annotation.AnnotationFormatError;
import java.util.List;

import com.oracle.svm.util.GuestAnnotationAccess;
import com.oracle.svm.util.GuestAccess;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.vmaccess.ResolvedJavaPackage;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

/**
 * Host target for the guest-context {@link org.graalvm.nativeimage.impl.AnnotationExtractor}
 * proxy. Method signatures intentionally use JVMCI types where the guest interface uses core
 * reflection types; {@link GuestAccess#createHostProxy} and the Espresso proxy conversion layer
 * adapt the boundary. This class is internal proxy wiring; hosted annotation queries must use
 * {@link GuestAnnotationAccess}.
 */
@SuppressWarnings("static-method")
final class GuestAnnotationExtractorProxy {

    /** Creates the host target for the guest annotation extractor proxy. */
    GuestAnnotationExtractorProxy() {
    }

    public boolean hasAnnotation(JavaConstant element, ResolvedJavaType annotationType) {
        try {
            Annotated annotated = toAnnotated(element);
            return annotated != null && GuestAnnotationAccess.isAnnotationPresent(annotated, annotationType);
        } catch (LinkageError | AnnotationFormatError e) {
            return false;
        }
    }

    /**
     * Implements the guest interface convenience method using guest metadata so the host proxy
     * does not need reflective access to the interface default method.
     */
    public AnnotationValue extractAnnotation(JavaConstant element, ResolvedJavaType annotationType) {
        AnnotationValue inherited = GuestAnnotationAccess.getAnnotationValue(annotationType, Inherited.class);
        return extractAnnotation(element, annotationType, inherited == null);
    }

    public AnnotationValue extractAnnotation(JavaConstant element, ResolvedJavaType annotationType, boolean declaredOnly) {
        try {
            Annotated annotated = toAnnotated(element);
            if (annotated == null) {
                return null;
            }
            return GuestAnnotationAccess.getAnnotationValue(annotated, annotationType, declaredOnly);
        } catch (LinkageError | AnnotationFormatError e) {
            return null;
        }
    }

    public JavaConstant getAnnotationTypes(JavaConstant element) {
        GuestAccess access = GuestAccess.get();
        JavaConstant emptyAnnotationTypes = access.asArrayConstant(access.lookupType(Class.class));
        try {
            Annotated annotated = toAnnotated(element);
            if (annotated == null) {
                /* Return an empty guest Class[]; getAnnotationTypes must not return null. */
                return emptyAnnotationTypes;
            }
            List<ResolvedJavaType> annotationTypes = GuestAnnotationAccess.getAnnotationTypes(annotated);
            JavaConstant[] annotationClasses = annotationTypes.stream()
                            .map(access.getProviders().getConstantReflection()::asJavaClass)
                            .toArray(JavaConstant[]::new);
            return access.asArrayConstant(access.lookupType(Class.class), annotationClasses);
        } catch (LinkageError | AnnotationFormatError e) {
            return emptyAnnotationTypes;
        }
    }

    private static Annotated toAnnotated(JavaConstant element) {
        if (element == null || element.isNull()) {
            return null;
        }
        GuestAccess access = GuestAccess.get();
        JavaType javaType = access.getProviders().getConstantReflection().asJavaType(element);
        if (javaType instanceof ResolvedJavaType resolvedType) {
            return resolvedType;
        }
        ResolvedJavaMethod method = access.asResolvedJavaMethod(element);
        if (method != null) {
            return method;
        }
        ResolvedJavaField field = access.asResolvedJavaField(element);
        if (field != null) {
            return field;
        }
        ResolvedJavaPackage javaPackage = access.asResolvedJavaPackage(element);
        if (javaPackage != null) {
            return javaPackage;
        }
        ResolvedJavaRecordComponent recordComponent = access.asResolvedJavaRecordComponent(element);
        if (recordComponent != null) {
            return recordComponent;
        }
        throw new IllegalArgumentException("Unsupported annotated element constant: " + element);
    }
}
