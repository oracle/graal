/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta;

import java.lang.annotation.*;
import java.lang.reflect.*;

/**
 * Represents a reference to a resolved Java field. Fields, like methods and types, are resolved
 * through {@link ConstantPool constant pools}.
 */
public interface ResolvedJavaField extends JavaField, LocationIdentity {

    /**
     * Returns the Java language modifiers for this field, as an integer. The {@link Modifier} class
     * should be used to decode the modifiers. Only the {@linkplain Modifier#fieldModifiers() field
     * flags} specified in the JVM specification will be included in the returned mask.
     */
    int getModifiers();

    /**
     * Determines if this field was injected by the VM. Such a field, for example, is not derived
     * from a class file.
     */
    boolean isInternal();

    /**
     * Determines if this field is a synthetic field as defined by the Java Language Specification.
     */
    boolean isSynthetic();

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
    Constant readConstantValue(Constant receiver);

    /**
     * Gets the current value of this field for a given object, if available. There is no guarantee
     * that the same value will be returned by this method for a field unless the field is
     * considered to be {@link #readConstantValue(Constant)} by the runtime.
     * 
     * @param receiver object from which this field's value is to be read. This value is ignored if
     *            this field is static.
     * @return the value of this field or {@code null} if the value is not available (e.g., because
     *         the field holder is not yet initialized).
     */
    Constant readValue(Constant receiver);

    /**
     * Returns the {@link ResolvedJavaType} object representing the class or interface that declares
     * this field.
     */
    ResolvedJavaType getDeclaringClass();

    /**
     * Returns the annotation for the specified type of this field, if such an annotation is
     * present.
     * 
     * @param annotationClass the Class object corresponding to the annotation type
     * @return this element's annotation for the specified annotation type if present on this field,
     *         else {@code null}
     */
    <T extends Annotation> T getAnnotation(Class<T> annotationClass);
}
