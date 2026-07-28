/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vmaccess;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationTypeMismatchException;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueType;
import jdk.graal.compiler.annotation.ElementTypeMismatch;
import jdk.graal.compiler.annotation.EnumElement;
import jdk.graal.compiler.annotation.ErrorElement;
import jdk.graal.compiler.annotation.MissingType;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationSupport;
import sun.reflect.annotation.EnumConstantNotPresentExceptionProxy;
import sun.reflect.annotation.ExceptionProxy;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

/**
 * Converts between JVMCI {@link AnnotationValue} metadata and host-owned JDK annotation objects.
 * Callers supply the mappings between {@link ResolvedJavaType} and {@code Class<?>} because a
 * {@link ResolvedJavaType} may describe either a host type or a type from an isolated guest VM.
 */
public final class HostAnnotationValueConverter {
    /**
     * Prevents instantiation of this utility class.
     */
    private HostAnnotationValueConverter() {
    }

    /**
     * Converts {@code annotationValue} to an annotation assignable to {@code expectedType}.
     * Explicit elements are validated, metadata defaults are supplied, absent required members
     * remain absent, and malformed members retain the JDK's deferred exception behavior.
     *
     * @param annotationValue the annotation metadata to convert
     * @param expectedType the annotation type to which the result must be assignable
     * @param typeToClass maps JVMCI types to their corresponding host classes
     * @return the converted annotation, or {@code null} when {@code annotationValue} is null
     * @throws IllegalArgumentException if the annotation or one of its elements is incompatible
     *             with its declared type
     */
    public static <T extends Annotation> T toAnnotation(AnnotationValue annotationValue, Class<T> expectedType, Function<ResolvedJavaType, Class<?>> typeToClass) {
        if (annotationValue == null) {
            return null;
        }
        Objects.requireNonNull(expectedType);
        Objects.requireNonNull(typeToClass);
        if (annotationValue.isError()) {
            throw annotationValue.getError();
        }
        Class<?> actualType = typeToClass.apply(annotationValue.getAnnotationType());
        if (actualType == null) {
            throw new IllegalArgumentException("Annotation value type " + annotationValue.getAnnotationType().toJavaName() + " has no host class");
        }
        if (!actualType.isAnnotation()) {
            throw new IllegalArgumentException("Annotation value type " + actualType.getName() + " is not an annotation interface");
        }
        if (!expectedType.isAssignableFrom(actualType)) {
            throw new IllegalArgumentException("Annotation value type " + actualType.getName() + " is not assignable to " + expectedType.getName());
        }
        Class<? extends Annotation> annotationType = actualType.asSubclass(Annotation.class);
        Annotation annotation = annotationValue.toAnnotation(annotationType, (value, type) -> createAnnotation(value, type, typeToClass));
        return expectedType.cast(annotation);
    }

    /**
     * Materializes a host-owned JDK annotation proxy from JVMCI annotation metadata.
     */
    private static <T extends Annotation> T createAnnotation(AnnotationValue annotationValue, Class<T> annotationType, Function<ResolvedJavaType, Class<?>> typeToClass) {
        Map<String, Object> memberValues = new LinkedHashMap<>();
        Map<String, Object> annotationElements = annotationValue.getElements();
        AnnotationValueType annotationValueType = AnnotationValueType.getInstance(annotationValue.getAnnotationType());
        AnnotationValueValidation.validateElements(annotationValue, annotationValueType);
        Map<String, Object> memberDefaults = annotationValueType.memberDefaults();
        for (Method member : annotationType.getDeclaredMethods()) {
            String memberName = member.getName();
            Object memberValue = annotationElements.get(memberName);
            if (memberValue == null) {
                memberValue = memberDefaults.get(memberName);
                if (memberValue == null) {
                    continue;
                }
            }
            memberValues.put(memberName, annotationValueElementAsHostValue(member, member.getReturnType(), memberValue, typeToClass));
        }
        return annotationType.cast(AnnotationParser.annotationForMap(annotationType, memberValues));
    }

    /**
     * Converts one JVMCI annotation element representation to the value expected by the JDK
     * annotation invocation handler.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object annotationValueElementAsHostValue(Method member, Class<?> targetType, Object value, Function<ResolvedJavaType, Class<?>> typeToClass) {
        if (value instanceof MissingType missingType) {
            return new TypeNotPresentExceptionProxy(missingType.getTypeName(), missingType.getCause());
        }
        if (value instanceof ElementTypeMismatch mismatch) {
            return new DeferredAnnotationTypeMismatchExceptionProxy(member, mismatch.getFoundType());
        }
        if (value instanceof ErrorElement) {
            throw new IllegalArgumentException("Unsupported annotation error element: " + value);
        }
        if (targetType.isArray()) {
            Class<?> componentType = targetType.getComponentType();
            List<?> elements = (List<?>) value;
            Object array = Array.newInstance(componentType, elements.size());
            for (int i = 0; i < elements.size(); i++) {
                Object element = annotationValueElementAsHostValue(member, componentType, elements.get(i), typeToClass);
                if (element instanceof ExceptionProxy) {
                    return element;
                }
                Array.set(array, i, element);
            }
            return array;
        }
        if (targetType == Class.class) {
            ResolvedJavaType classType = (ResolvedJavaType) value;
            Class<?> clazz = typeToClass.apply(classType);
            if (clazz == null) {
                throw new IllegalArgumentException("Annotation class element type " + classType.toJavaName() + " has no host class");
            }
            return clazz;
        }
        if (targetType.isEnum()) {
            EnumElement enumElement = (EnumElement) value;
            Class<?> enumType = typeToClass.apply(enumElement.enumType);
            if (enumType == null) {
                throw new IllegalArgumentException("Annotation enum element type " + enumElement.enumType.toJavaName() + " has no host class");
            }
            if (enumType != targetType) {
                throw new IllegalArgumentException("Unexpected enum type: " + enumType.getName());
            }
            try {
                return Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), enumElement.name);
            } catch (IllegalArgumentException e) {
                return new EnumConstantNotPresentExceptionProxy((Class<? extends Enum<?>>) enumType, enumElement.name);
            }
        }
        if (targetType.isAnnotation()) {
            return toAnnotation((AnnotationValue) value, targetType.asSubclass(Annotation.class), typeToClass);
        }
        return value;
    }

    /**
     * Accessible replacement for the JDK's package-private
     * {@code AnnotationTypeMismatchExceptionProxy}. The JDK annotation invocation handler calls
     * {@link #generateException()} only when the affected member is accessed.
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

    /**
     * Converts a host annotation to its JVMCI metadata representation without eagerly reading
     * deferred failures from a JDK annotation proxy.
     *
     * @param annotation the host annotation to convert
     * @param classToType maps host classes to types in the JVMCI context that owns the metadata
     * @return the converted metadata, or {@code null} when {@code annotation} is null
     */
    public static AnnotationValue toAnnotationValue(Annotation annotation, Function<Class<?>, ResolvedJavaType> classToType) {
        if (annotation == null) {
            return null;
        }
        Objects.requireNonNull(classToType);
        Class<? extends Annotation> annotationType = annotation.annotationType();
        Map<String, Object> memberValues;
        try {
            memberValues = AnnotationSupport.memberValues(annotation);
        } catch (IllegalArgumentException | ClassCastException e) {
            // Annotation implementations other than JDK proxies have no backing member-value map.
            memberValues = null;
        }
        Map<String, Object> elements = new LinkedHashMap<>();
        for (Method member : annotationType.getDeclaredMethods()) {
            Object value;
            if (memberValues != null) {
                value = memberValues.get(member.getName());
                if (value == null) {
                    continue;
                }
            } else {
                makeAccessible(member);
                try {
                    value = member.invoke(annotation);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IncompleteAnnotationException) {
                        continue;
                    }
                    value = annotationFailureAsAnnotationValueElement(member, cause, classToType);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            elements.put(member.getName(), hostAnnotationMemberAsAnnotationValueElement(annotation, member, value, classToType));
        }
        return new AnnotationValue(classToType.apply(annotationType), elements);
    }

    /**
     * Makes {@code accessibleMember} accessible from the VMAccess module. This operation must
     * remain in that module because reflective access checks use the module containing the caller
     * of {@link AccessibleObject#setAccessible(boolean)}.
     */
    @SuppressWarnings("deprecation")
    private static <T extends AccessibleObject & Member> void makeAccessible(T accessibleMember) {
        try {
            if (!accessibleMember.isAccessible()) {
                accessibleMember.setAccessible(true);
            }
        } catch (InaccessibleObjectException e) {
            Class<?> declaringClass = accessibleMember.getDeclaringClass();
            ModuleSupport.addOpens(HostAnnotationValueConverter.class.getModule(), declaringClass.getModule(), declaringClass.getPackageName());
            accessibleMember.setAccessible(true);
        }
    }

    /**
     * Converts a standard exception thrown by a custom annotation member into the corresponding
     * deferred JVMCI error element.
     */
    private static Object annotationFailureAsAnnotationValueElement(Method member, Throwable failure, Function<Class<?>, ResolvedJavaType> classToType) {
        return switch (failure) {
            case TypeNotPresentException missingType -> new MissingType(missingType.typeName(), missingType.getCause());
            case AnnotationTypeMismatchException mismatch -> new ElementTypeMismatch(mismatch.foundType());
            case EnumConstantNotPresentException missingEnum -> {
                EnumElement enumElement = new EnumElement(classToType.apply(missingEnum.enumType()), missingEnum.constantName());
                yield member.getReturnType().isArray() ? List.of(enumElement) : enumElement;
            }
            default -> throw sneakyThrow(failure);
        };
    }

    /**
     * Converts one reflection annotation value, including JDK exception proxies, into the
     * representation used by {@link AnnotationValue}.
     */
    private static Object hostAnnotationMemberAsAnnotationValueElement(Annotation annotation, Method member, Object value, Function<Class<?>, ResolvedJavaType> classToType) {
        if (value instanceof TypeNotPresentExceptionProxy proxy) {
            return new MissingType(proxy.typeName(), proxy.getCause());
        }
        if (value instanceof DeferredAnnotationTypeMismatchExceptionProxy proxy) {
            return new ElementTypeMismatch(proxy.foundType);
        }
        if (value instanceof EnumConstantNotPresentExceptionProxy) {
            return invokeFailingAnnotationMember(annotation, member, classToType);
        }
        if (value instanceof ExceptionProxy) {
            if (!"sun.reflect.annotation.AnnotationTypeMismatchExceptionProxy".equals(value.getClass().getName())) {
                throw new IllegalArgumentException("Unsupported annotation exception proxy: " + value.getClass().getName());
            }
            return invokeFailingAnnotationMember(annotation, member, classToType);
        }
        if (value instanceof Enum<?> enumValue) {
            return new EnumElement(classToType.apply(enumValue.getDeclaringClass()), enumValue.name());
        }
        if (value instanceof Class<?> classValue) {
            return classToType.apply(classValue);
        }
        if (value instanceof Annotation annotationValue) {
            return toAnnotationValue(annotationValue, classToType);
        }
        Class<?> valueType = value.getClass();
        if (valueType.isArray()) {
            int length = Array.getLength(value);
            List<Object> elements = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                elements.add(hostAnnotationMemberAsAnnotationValueElement(annotation, member, Array.get(value, i), classToType));
            }
            return List.copyOf(elements);
        }
        return value;
    }

    /**
     * Invokes a member known to contain an opaque JDK exception proxy so the public annotation API
     * exposes the deferred failure details.
     */
    private static Object invokeFailingAnnotationMember(Annotation annotation, Method member, Function<Class<?>, ResolvedJavaType> classToType) {
        try {
            Proxy.getInvocationHandler(annotation).invoke(annotation, member, null);
        } catch (EnumConstantNotPresentException | AnnotationTypeMismatchException failure) {
            return annotationFailureAsAnnotationValueElement(member, failure, classToType);
        } catch (Throwable failure) {
            throw sneakyThrow(failure);
        }
        throw new IllegalArgumentException("Annotation exception proxy did not throw for " + member);
    }

    /**
     * Rethrows {@code failure} without changing its type.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable failure) throws T {
        throw (T) failure;
    }
}
