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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.GraalError;
import jdk.internal.reflect.ConstantPool;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

public class AnnotationMetadata {

    @SuppressWarnings("serial")
    static final class AnnotationExtractionError extends Error {
        AnnotationExtractionError(Object targetElement, Throwable cause) {
            super("Failed to process '%s': %s".formatted(targetElement, cause), cause);
        }

        AnnotationExtractionError(Object targetElement, String message) {
            super("Failed to process '%s': %s".formatted(targetElement, message));
        }
    }

    private static final Method annotationParserParseSig = ReflectionUtil.lookupMethod(AnnotationParser.class, "parseSig", String.class, Class.class);
    private static final Constructor<?> annotationTypeMismatchExceptionProxyConstructor;

    static {
        try {
            annotationTypeMismatchExceptionProxyConstructor = ReflectionUtil.lookupConstructor(Class.forName("sun.reflect.annotation.AnnotationTypeMismatchExceptionProxy"), String.class);
        } catch (ClassNotFoundException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    static Object extractType(ByteBuffer buf, ConstantPool cp, Class<?> container, boolean skip) {
        int typeIndex = buf.getShort() & 0xFFFF;
        if (skip) {
            return null;
        }
        Class<?> type;
        String signature = cp.getUTF8At(typeIndex);
        try {
            type = (Class<?>) annotationParserParseSig.invoke(null, signature, container);
        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();
            if (targetException instanceof LinkageError || targetException instanceof TypeNotPresentException) {
                return new TypeNotPresentExceptionProxy(signature, targetException);
            }
            throw new AnnotationExtractionError(signature, e);
        } catch (ReflectiveOperationException e) {
            throw new AnnotationExtractionError(signature, e);
        }
        return type;
    }

    static String extractString(ByteBuffer buf, ConstantPool cp, boolean skip) {
        int index = buf.getShort() & 0xFFFF;
        return skip ? null : cp.getUTF8At(index);
    }

    static Object checkResult(Object value, Class<?> expectedType) {
        if (!expectedType.isInstance(value)) {
            if (value instanceof Annotation) {
                return createAnnotationTypeMismatchExceptionProxy(value.toString());
            } else {
                return createAnnotationTypeMismatchExceptionProxy(value.getClass().getName() + "[" + value + "]");
            }
        }
        return value;
    }

    static Object createAnnotationTypeMismatchExceptionProxy(String message) {
        try {
            return annotationTypeMismatchExceptionProxyConstructor.newInstance(message);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new AnnotationExtractionError(message, e);
        }
    }
}
