/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.*;

/**
 * Reflection operations on values represented as {@linkplain JavaConstant constants}. All methods
 * in this interface require the VM to access the actual object encapsulated in {@link Kind#Object
 * object} constants. This access is not always possible, depending on kind of VM and the state that
 * the VM is in. Therefore, all methods can return {@code null} at any time, to indicate that the
 * result is not available at this point. The caller is responsible to check for {@code null}
 * results and handle them properly, e.g., not perform an optimization.
 */
public interface ConstantReflectionProvider extends Remote {

    /**
     * Compares two constants for equality. The equality relationship is symmetric. Returns
     * {@link Boolean#TRUE true} if the two constants represent the same run time value,
     * {@link Boolean#FALSE false} if they are different. Returns {@code null} if the constants
     * cannot be compared at this point.
     */
    Boolean constantEquals(Constant x, Constant y);

    /**
     * Returns the length of the array constant. Returns {@code null} if the constant is not an
     * array, or if the array length is not available at this point.
     */
    Integer readArrayLength(JavaConstant array);

    /**
     * Reads a value from the given array at the given index. Returns {@code null} if the constant
     * is not an array, if the index is out of bounds, or if the value is not available at this
     * point.
     */
    JavaConstant readArrayElement(JavaConstant array, int index);

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
    JavaConstant readConstantFieldValue(JavaField field, JavaConstant receiver);

    /**
     * Gets the current value of this field for a given object, if available. There is no guarantee
     * that the same value will be returned by this method for a field unless the field is
     * considered to be {@linkplain #readConstantFieldValue(JavaField, JavaConstant) constant} by
     * the runtime.
     *
     * @param receiver object from which this field's value is to be read. This value is ignored if
     *            this field is static.
     * @return the value of this field or {@code null} if the value is not available (e.g., because
     *         the field holder is not yet initialized).
     */
    JavaConstant readFieldValue(JavaField field, JavaConstant receiver);

    /**
     * Reads a value of this kind using a base address and a displacement. No bounds checking or
     * type checking is performed. Returns {@code null} if the value is not available at this point.
     *
     * @param base the base address from which the value is read.
     * @param displacement the displacement within the object in bytes
     * @return the read value encapsulated in a {@link JavaConstant} object, or {@code null} if the
     *         value cannot be read.
     */
    JavaConstant readUnsafeConstant(Kind kind, JavaConstant base, long displacement);

    /**
     * Reads a primitive value using a base address and a displacement.
     *
     * @param kind the {@link Kind} of the returned {@link JavaConstant} object
     * @param base the base address from which the value is read
     * @param displacement the displacement within the object in bytes
     * @param bits the number of bits to read from memory
     * @return the read value encapsulated in a {@link Constant} object of {@link Kind} kind
     */
    JavaConstant readRawConstant(Kind kind, Constant base, long displacement, int bits);

    /**
     * Reads a pointer value using a base address and a displacement.
     *
     * @param type the {@link PointerType} of the returned {@link Constant} object
     * @param base the base address from which the value is read
     * @param displacement the displacement within the object in bytes
     * @return the read value encapsulated in a {@link Constant} object
     */
    Constant readPointerConstant(PointerType type, Constant base, long displacement);

    /**
     * Converts the given {@link Kind#isPrimitive() primitive} constant to a boxed
     * {@link Kind#Object object} constant, according to the Java boxing rules. Returns {@code null}
     * if the source is is not a primitive constant, or the boxed value is not available at this
     * point.
     */
    JavaConstant boxPrimitive(JavaConstant source);

    /**
     * Converts the given {@link Kind#Object object} constant to a {@link Kind#isPrimitive()
     * primitive} constant, according to the Java unboxing rules. Returns {@code null} if the source
     * is is not an object constant that can be unboxed, or the unboxed value is not available at
     * this point.
     */
    JavaConstant unboxPrimitive(JavaConstant source);

    /**
     * Gets a string as a {@link JavaConstant}.
     */
    JavaConstant forString(String value);

    /**
     * Returns the {@link ResolvedJavaType} for a {@link Class} object (or any other object regarded
     * as a class by the VM) encapsulated in the given constant. Returns {@code null} if the
     * constant does not encapsulate a class, or if the type is not available at this point.
     */
    ResolvedJavaType asJavaType(JavaConstant constant);

    /**
     * Gets access to the internals of {@link MethodHandle}.
     */
    MethodHandleAccessProvider getMethodHandleAccess();
}
