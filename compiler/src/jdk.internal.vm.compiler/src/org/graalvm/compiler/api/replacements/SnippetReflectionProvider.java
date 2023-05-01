/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.api.replacements;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Objects;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Reflection operations on values represented as {@linkplain JavaConstant constants} for the
 * processing of snippets. Snippets need a direct access to the value of object constants, which is
 * not allowed in other parts of Graal to enforce compiler-VM separation.
 * <p>
 * This interface must not be used in Graal code that is not related to snippet processing.
 */
public interface SnippetReflectionProvider {

    /**
     * Creates a boxed {@link JavaKind#Object object} constant.
     *
     * @param object the object value to box
     * @return a constant containing {@code object}
     */
    JavaConstant forObject(Object object);

    /**
     * Gets the object reference a given constant represents if it is of a given type. The constant
     * must have kind {@link JavaKind#Object}.
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
     * Creates a boxed constant for the given kind from an Object. The object needs to be of the
     * Java boxed type corresponding to the kind.
     *
     * @param kind the kind of the constant to create
     * @param value the Java boxed value: a {@link Byte} instance for {@link JavaKind#Byte}, etc.
     * @return the boxed copy of {@code value}
     */
    default JavaConstant forBoxed(JavaKind kind, Object value) {
        if (kind == JavaKind.Object) {
            return forObject(value);
        } else {
            return JavaConstant.forBoxedPrimitive(value);
        }
    }

    /**
     * Gets the value to bind to an injected parameter in a node intrinsic.
     *
     * @param type the type of a parameter in a node intrinsic constructor
     * @return the value that should be bound to the parameter when invoking the constructor or null
     *         if this provider cannot provide a value of the requested type
     */
    <T> T getInjectedNodeIntrinsicParameter(Class<T> type);

    /**
     * Get the original Java class corresponding to a {@link ResolvedJavaType}.
     *
     * @param type the type for which the original Java class is requested
     * @return the original Java class corresponding to {@code type} or {@code null} if this object
     *         cannot map {@link ResolvedJavaType} instances to {@link Class} instances
     */
    Class<?> originalClass(ResolvedJavaType type);

    /**
     * Get the original Java method/constructor corresponding to a {@link ResolvedJavaMethod}.
     *
     * @param method the method for which the original Java method/constructor is requested
     * @return the original Java method corresponding to {@code method} or {@code null} if this
     *         object cannot map {@link ResolvedJavaMethod} instances to {@link Executable}
     *         instances
     */
    Executable originalMethod(ResolvedJavaMethod method);

    /**
     * Get the original Java field corresponding to a {@link ResolvedJavaField}.
     *
     * @param field the field for which the original Java field is requested
     * @return the original Java field corresponding to {@code field} or {@code null} if this object
     *         cannot map {@link ResolvedJavaField} instances to {@link Field} instances.
     */
    Field originalField(ResolvedJavaField field);
}
