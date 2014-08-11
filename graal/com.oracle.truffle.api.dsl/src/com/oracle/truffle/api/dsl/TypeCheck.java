/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl;

import java.lang.annotation.*;

/**
 * <p>
 * Provides a way to define a custom type check for a defined type. The name of the annotated method
 * must fit to the pattern is${typeName} (eg. isInteger), where ${typeName} must be a valid type
 * defined in the parent {@link TypeSystem}. The annotated method must have exactly one argument
 * where the type of the argument is the generic type {@link Object} or a more specific one from the
 * {@link TypeSystem}. You can define multiple overloaded {@link TypeCheck} methods for the same
 * type. This can be used to reduce the boxing overhead in type conversions.
 * </p>
 *
 * <p>
 * By default the system generates type checks for all types in the parent {@link TypeSystem} which
 * look like the follows:
 *
 * <pre>
 * {@literal @}TypeCheck
 * boolean is${typeName}(Object value) {
 *         return value instanceof ${typeName};
 * }
 * </pre>
 *
 * <b>Example:</b>
 * <p>
 * A type check for BigInteger with one overloaded optimized variant to reduce boxing.
 * </p>
 *
 * <pre>
 *
 *
 * {@literal @}TypeSystem(types = {int.class, BigInteger.class, String.class}, nodeBaseClass = TypedNode.class)
 * public abstract class Types {
 * 
 *     {@literal @}TypeCheck
 *     public boolean isBigInteger(Object value) {
 *         return value instanceof Integer || value instanceof BigInteger;
 *     }
 * 
 *     {@literal @}TypeCheck
 *     public boolean isBigInteger(int value) {
 *         return true;
 *     }
 * 
 * }
 * </pre>
 *
 *
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface TypeCheck {

}
