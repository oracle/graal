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

import com.oracle.truffle.api.nodes.*;

/**
 * <p>
 * Each {@link Node} has one {@link TypeSystem} at its root to define the types that can be used
 * throughout the system. Multiple {@link TypeSystem}s are allowed, but they cannot be mixed inside
 * a single {@link Node} hierarchy. A {@link TypeSystem} defines a list of types as its child
 * elements, in which every type precedes its super types.The latter condition ensures that the most
 * concrete type is found first when searching the list sequentially for the type of a given generic
 * value.
 * </p>
 * 
 * <p>
 * Each {@link #value()} is represented as a java type. A type can specify two annotations:
 * {@link TypeCheck} and {@link TypeCast}. The {@link TypeCheck} checks whether a given generic
 * value matches to the current type. The {@link TypeCast} casts a generic type value to the current
 * type. If the {@link TypeCheck} and {@link TypeCast} annotations are not declared in the
 * {@link TypeSystem} the a default implementation is provided. The default implementation of
 * {@link TypeCheck} returns <code>true</code> only on an exact type match and {@link TypeCast} is
 * only a cast to this type. Specified methods with {@link TypeCheck} and {@link TypeCast} may be
 * used to extend the definition of a type in the language. In our example, the
 * <code>isInteger</code> and <code>asInteger</code> methods are defined in a way so that they
 * accept also {@link Integer} values, implicitly converting them to {@link Double} . This example
 * points out how we express implicit type conversions.
 * </p>
 * 
 * <p>
 * <b>Example:</b> The {@link TypeSystem} contains the types {@link Boolean}, {@link Integer}, and
 * {@link Double}. The type {@link Object} is always used implicitly as the generic type represent
 * all values.
 * 
 * <pre>
 * 
 * {@literal @}TypeSystem(types = {boolean.class, int.class, double.class})
 * public abstract class ExampleTypeSystem {
 * 
 *     {@literal @}TypeCheck
 *     public boolean isInteger(Object value) {
 *         return value instanceof Integer || value instanceof Double;
 *     }
 * 
 *     {@literal @}TypeCast
 *     public double asInteger(Object value) {
 *         return ((Number)value).doubleValue();
 *     }
 * }
 * </pre>
 * 
 * </p>
 * 
 * @see TypeCast
 * @see TypeCheck
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface TypeSystem {

    /**
     * The list of types as child elements of the {@link TypeSystem}. Each precedes its super type.
     */
    Class[] value();

}
