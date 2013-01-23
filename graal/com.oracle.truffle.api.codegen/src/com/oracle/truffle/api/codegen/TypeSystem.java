/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.codegen;

import java.lang.annotation.*;

/**
 * <p>
 * Annotates a type system class that represents type information for a node. Generates code for
 * converting and managing types. Methods contained in the type system may be annotated with
 * {@link TypeCast}, {@link TypeCheck} or {@link GuardCheck}. These methods alter the default
 * behavior of the type system.
 * </p>
 * 
 * 
 * <b>Example:</b>
 * <p>
 * Shows a <code>@TypeSystem</code> definition with three types. In this example BigIntegers can be
 * also treated as integers if their bit width is less than 32.
 * </p>
 * 
 * <pre>
 * 
 * {@literal @}TypeSystem(types = {int.class, BigInteger.class, String.class}, nodeBaseClass = TypedNode.class)
 * public abstract class Types {
 * 
 *     {@literal @}TypeCheck
 *     public boolean isInteger(Object value) {
 *         return value instanceof Integer || (value instanceof BigInteger &amp;&amp; ((BigInteger) value).bitLength() &lt; Integer.SIZE);
 *     }
 * 
 *     {@literal @}TypeCast
 *     public int asInteger(Object value) {
 *         if (value instanceof Integer) {
 *             return (int) value;
 *         } else {
 *             return ((BigInteger) value).intValue();
 *         }
 *     }
 * }
 * </pre>
 * 
 * @see TypeCast
 * @see TypeCheck
 * @see GuardCheck
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface TypeSystem {

    /**
     * Sets the types contained by this type system. The order of types also determines the order of
     * specialization.
     */
    Class[] value();

}
