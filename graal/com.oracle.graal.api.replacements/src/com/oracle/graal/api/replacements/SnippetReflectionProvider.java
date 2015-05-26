/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.replacements;

import com.oracle.jvmci.meta.JavaField;
import com.oracle.jvmci.meta.JavaType;
import com.oracle.jvmci.meta.ResolvedJavaType;
import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.Kind;
import com.oracle.jvmci.meta.ResolvedJavaMethod;
import java.lang.reflect.*;
import java.util.*;


/**
 * Reflection operations on values represented as {@linkplain JavaConstant constants} for the
 * processing of snippets. Snippets need a direct access to the value of object constants, which is
 * not allowed in other parts of Graal to enforce compiler-VM separation.
 * <p>
 * This interface must not be used in Graal code that is not related to snippet processing.
 */
public interface SnippetReflectionProvider {

    /**
     * Creates a boxed {@link Kind#Object object} constant.
     *
     * @param object the object value to box
     * @return a constant containing {@code object}
     */
    JavaConstant forObject(Object object);

    /**
     * Gets the object reference a given constant represents if it is of a given type. The constant
     * must have kind {@link Kind#Object}.
     *
     * @param type the expected type of the object represented by {@code constant}. If the object is
     *            required to be of this type, then wrap the call to this method in
     *            {@link Objects#requireNonNull(Object)}.
     * @param constant an object constant
     * @return the object value represented by {@code constant} cast to {@code type} if it is an
     *         {@link Class#isInstance(Object) instance of} {@code type} otherwise {@code null}
     */
    <T> T asObject(Class<T> type, JavaConstant constant);

    /**
     * Gets the object reference a given constant represents if it is of a given type. The constant
     * must have kind {@link Kind#Object}.
     *
     * @param type the expected type of the object represented by {@code constant}. If the object is
     *            required to be of this type, then wrap the call to this method in
     *            {@link Objects#requireNonNull(Object)}.
     * @param constant an object constant
     * @return the object value represented by {@code constant} if it is an
     *         {@link ResolvedJavaType#isInstance(JavaConstant) instance of} {@code type} otherwise
     *         {@code null}
     */
    Object asObject(ResolvedJavaType type, JavaConstant constant);

    /**
     * Creates a boxed constant for the given kind from an Object. The object needs to be of the
     * Java boxed type corresponding to the kind.
     *
     * @param kind the kind of the constant to create
     * @param value the Java boxed value: a {@link Byte} instance for {@link Kind#Byte}, etc.
     * @return the boxed copy of {@code value}
     */
    JavaConstant forBoxed(Kind kind, Object value);

    /**
     * Resolves a parameter or return type involved in snippet code to a {@link Class}.
     */
    static Class<?> resolveClassForSnippet(JavaType type) throws ClassNotFoundException {
        try {
            return Class.forName(type.toClassName());
        } catch (ClassNotFoundException e) {
            // Support for -XX:-UseGraalClassLoader
            return Class.forName(type.toClassName(), false, ClassLoader.getSystemClassLoader());
        }
    }

    /**
     * Invokes a given method via {@link Method#invoke(Object, Object...)}.
     */
    default Object invoke(ResolvedJavaMethod method, Object receiver, Object... args) {
        try {
            JavaType[] parameterTypes = method.toParameterTypes();
            Class<?>[] parameterClasses = new Class<?>[parameterTypes.length];
            for (int i = 0; i < parameterClasses.length; ++i) {
                JavaType type = parameterTypes[i];
                if (type.getKind() != Kind.Object) {
                    parameterClasses[i] = type.getKind().toJavaClass();
                } else {
                    parameterClasses[i] = resolveClassForSnippet(parameterTypes[i]);
                }
            }

            Class<?> c = resolveClassForSnippet(method.getDeclaringClass());
            if (method.isConstructor()) {
                Constructor<?> javaConstructor = c.getDeclaredConstructor(parameterClasses);
                javaConstructor.setAccessible(true);
                return javaConstructor.newInstance(args);
            } else {
                Method javaMethod = c.getDeclaredMethod(method.getName(), parameterClasses);
                javaMethod.setAccessible(true);
                return javaMethod.invoke(receiver, args);
            }
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException | ClassNotFoundException | NoSuchMethodException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    /**
     * Gets the constant value of this field. Note that a {@code static final} field may not be
     * considered constant if its declaring class is not yet initialized or if it is a well known
     * field that can be updated via other means (e.g., {@link System#setOut(java.io.PrintStream)}).
     *
     * @param receiver object from which this field's value is to be read. This value is ignored if
     *            this field is static.
     * @return the constant value of this field or {@code null} if this field is not considered
     *         constant by the runtime
     */
    default Object readConstantFieldValue(JavaField field, Object receiver) {
        try {
            Class<?> c = resolveClassForSnippet(field.getDeclaringClass());
            Field javaField = c.getDeclaredField(field.getName());
            javaField.setAccessible(true);
            return javaField.get(receiver);
        } catch (IllegalAccessException | ClassNotFoundException | NoSuchFieldException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    /**
     * Creates a new array with a given type as the component type and the specified length. This
     * method is similar to {@link Array#newInstance(Class, int)}.
     */
    default Object newArray(ResolvedJavaType componentType, int length) {
        try {
            return Array.newInstance(resolveClassForSnippet(componentType), length);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Gets the value to bind to a parameter in a {@link SubstitutionGuard} constructor.
     *
     * @param type the type of a parameter in a {@link SubstitutionGuard} constructor
     * @return the value that should be bound to the parameter when invoking the constructor or null
     *         if this provider cannot provide a value of the requested type
     */
    Object getSubstitutionGuardParameter(Class<?> type);

    /**
     * Gets the value to bind to an injected parameter in a node intrinsic.
     *
     * @param type the type of a parameter in a node intrinsic constructor
     * @return the value that should be bound to the parameter when invoking the constructor or null
     *         if this provider cannot provide a value of the requested type
     */
    Object getInjectedNodeIntrinsicParameter(ResolvedJavaType type);
}
