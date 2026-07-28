/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.vmaccess.guest;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationTypeMismatchException;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationSupport;
import sun.reflect.annotation.EnumConstantNotPresentExceptionProxy;
import sun.reflect.annotation.ExceptionProxy;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

/**
 * Guest-side entry point for creating JDK annotation proxy instances from element maps prepared by host-to-guest
 * VMAccess conversion.
 *
 * The conversion must materialize the annotation in the Espresso guest context. Calling
 * {@code AnnotationValue.toAnnotation(...)} on the host value would instead create and cache a host annotation instance,
 * which is not suitable for guest {@code AnnotationAccess}. Delegating to the guest JDK annotation parser here keeps the
 * returned annotation owned by the guest JDK while preserving standard annotation proxy behavior.
 */
final class GuestAnnotationProxyBuilder {
    /**
     * Prevents instantiation of this guest entry-point class.
     */
    private GuestAnnotationProxyBuilder() {
    }

    /**
     * Creates a guest JDK annotation proxy backed by the supplied member-value map.
     */
    public static Annotation annotationForMap(Class<? extends Annotation> annotationType, Map<String, Object> elements) {
        return AnnotationParser.annotationForMap(annotationType, elements);
    }

    /**
     * Reads a guest annotation member. JDK annotation proxies are inspected without invoking healthy
     * members; custom implementations are invoked here so standard deferred annotation failures can
     * be preserved before crossing into the host. If guest module encapsulation prevents reflective
     * access, the host falls back to Espresso interop.
     *
     * Standard deferred annotation failures are thrown so {@code VMAccess.invoke} can transport the
     * guest exception object to the host without a separate error protocol.
     *
     * @return the member value, or {@code null} when the member must be read through Espresso interop
     */
    public static Object annotationMemberValue(Annotation annotation, String memberName) {
        Map<String, Object> memberValues;
        try {
            memberValues = AnnotationSupport.memberValues(annotation);
        } catch (IllegalArgumentException | ClassCastException e) {
            // Custom annotation implementations have no JDK annotation-proxy backing map.
            return invokeAnnotationMember(annotation, memberName);
        }
        if (!memberValues.containsKey(memberName)) {
            throw new IncompleteAnnotationException(annotation.annotationType(), memberName);
        }
        Object value = memberValues.get(memberName);
        if (value instanceof TypeNotPresentExceptionProxy proxy) {
            throw new TypeNotPresentException(proxy.typeName(), proxy.getCause());
        }
        if (value instanceof DeferredAnnotationTypeMismatchExceptionProxy proxy) {
            throw proxy.generateException();
        }
        if (value instanceof ExceptionProxy) {
            /*
             * The remaining JDK proxies do not expose their error data: ExceptionProxy provides
             * only protected generateException(), and AnnotationTypeMismatchExceptionProxy is
             * package-private with a private found-type field. Invoke only the known failing member
             * so the annotation handler exposes the data through a public exception.
             */
            if (!(value instanceof EnumConstantNotPresentExceptionProxy) &&
                            !"sun.reflect.annotation.AnnotationTypeMismatchExceptionProxy".equals(value.getClass().getName())) {
                throw new IllegalArgumentException("Unsupported annotation exception proxy: " + value.getClass().getName());
            }
            Method member;
            try {
                member = annotation.annotationType().getDeclaredMethod(memberName);
                Proxy.getInvocationHandler(annotation).invoke(annotation, member, null);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            throw new IllegalArgumentException("Annotation exception proxy did not throw for " + member);
        }
        return value;
    }

    /**
     * Invokes a member on a custom annotation implementation. Standard deferred annotation failures
     * propagate through {@code VMAccess.invoke}; unsupported runtime exceptions and errors propagate
     * unchanged. If guest module encapsulation prevents access, the host retries through Espresso
     * interop.
     */
    private static Object invokeAnnotationMember(Annotation annotation, String memberName) {
        try {
            Method member = annotation.annotationType().getDeclaredMethod(memberName);
            if (!member.canAccess(annotation) && !member.trySetAccessible()) {
                return null;
            }
            return member.invoke(annotation);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot read annotation member " + memberName, e);
        }
    }

    /**
     * Creates a guest exception proxy that throws {@link TypeNotPresentException} when its annotation
     * member is accessed.
     */
    public static Object missingTypeProxy(String typeName) {
        return new TypeNotPresentExceptionProxy(typeName, new ClassNotFoundException(typeName));
    }

    /**
     * Creates a guest exception proxy that throws {@link AnnotationTypeMismatchException} when the
     * named annotation member is accessed.
     */
    public static Object elementTypeMismatchProxy(Class<? extends Annotation> annotationType, String memberName, String foundType) {
        try {
            return new DeferredAnnotationTypeMismatchExceptionProxy(annotationType.getDeclaredMethod(memberName), foundType);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Resolves a guest enum constant, returning a deferred exception proxy when the constant is not
     * present in the guest enum type.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object enumValue(Class<? extends Enum<?>> enumType, String constantName) {
        try {
            return Enum.valueOf((Class) enumType, constantName);
        } catch (IllegalArgumentException e) {
            return new EnumConstantNotPresentExceptionProxy(enumType, constantName);
        }
    }

    /**
     * Creates a typed guest enum array, or a member-level deferred exception proxy when any requested
     * constant is not present. A typed enum array cannot itself contain an exception proxy.
     */
    public static Object enumArray(Class<? extends Enum<?>> enumType, String[] constantNames) {
        Object array = Array.newInstance(enumType, constantNames.length);
        for (int i = 0; i < constantNames.length; i++) {
            Object value = enumValue(enumType, constantNames[i]);
            if (value instanceof ExceptionProxy) {
                return value;
            }
            Array.set(array, i, value);
        }
        return array;
    }

    /**
     * Accessible replacement for the guest JDK's package-private
     * {@code AnnotationTypeMismatchExceptionProxy}. The exception itself is public and can be
     * constructed here, but storing it directly in an annotation's member-value map would not defer
     * the failure. The JDK annotation invocation handler recognizes {@link ExceptionProxy} values and
     * calls {@link #generateException()} only when the corresponding member is accessed.
     */
    @SuppressWarnings("serial")
    private static final class DeferredAnnotationTypeMismatchExceptionProxy extends ExceptionProxy {
        private static final long serialVersionUID = 1L;

        /** The annotation member that reports the deferred mismatch. */
        private final Method member;

        /** The mismatched value type in the diagnostic form produced by annotation parsing. */
        private final String foundType;

        /**
         * Creates a deferred mismatch for {@code member} using the JDK diagnostic text in
         * {@code foundType}.
         */
        private DeferredAnnotationTypeMismatchExceptionProxy(Method member, String foundType) {
            this.member = member;
            this.foundType = foundType;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected RuntimeException generateException() {
            return new AnnotationTypeMismatchException(member, foundType);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "/* Warning type mismatch! \"" + foundType + "\" */";
        }
    }
}
