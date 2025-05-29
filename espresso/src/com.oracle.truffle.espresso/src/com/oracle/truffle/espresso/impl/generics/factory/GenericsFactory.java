/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl.generics.factory;

import com.oracle.truffle.espresso.impl.EspressoType;

/**
 * This is a modified version of sun.reflect.generics.factory.GenericsFactory. We have replaced Type
 * with EspressoType to represent Espresso guest types.
 * <p>
 * A factory interface for reflective objects representing generic types. Implementors (such as core
 * reflection or JDI, or possibly javadoc will manufacture instances of (potentially) different
 * classes in response to invocations of the methods described here.
 * <p>
 * The intent is that reflective systems use these factories to produce generic type information on
 * demand. Certain components of such reflective systems can be independent of a specific
 * implementation by using this interface. For example, repositories of generic type information are
 * initialized with a factory conforming to this interface, and use it to generate the type
 * information they are required to provide. As a result, such repository code can be shared across
 * different reflective systems.
 */
public interface GenericsFactory {

    /**
     * Returns an instance of the {@code ParameterizedType} interface that corresponds to a generic
     * type instantiation of the generic declaration {@code declaration} with actual type arguments
     * {@code typeArgs}. If {@code owner} is {@code null}, the declaring class of
     * {@code declaration} is used as the owner of this parameterized type.
     * <p>
     * This method throws a MalformedParameterizedTypeException under the following circumstances:
     * If the type declaration does not represent a generic declaration (i.e., it is not an instance
     * of {@code GenericDeclaration}). If the number of actual type arguments (i.e., the size of the
     * array {@code typeArgs}) does not correspond to the number of formal type arguments. If any of
     * the actual type arguments is not an instance of the bounds on the corresponding formal.
     * 
     * @param declaration - the generic type declaration that is to be instantiated
     * @param typeArgs - the list of actual type arguments
     * @return - a parameterized type representing the instantiation of the declaration with the
     *         actual type arguments
     * @throws NullPointerException if any of {@code declaration}, {@code typeArgs} or any of the
     *             elements of {@code typeArgs} are {@code null}
     */
    EspressoType makeParameterizedType(EspressoType declaration,
                    EspressoType[] typeArgs,
                    EspressoType owner);

    EspressoType makeNamedType(String name);

    EspressoType makeTypeVariable(String name, EspressoType javaLangObjectType);

    EspressoType makeJavaLangObject();

    /**
     * Returns a (possibly generic) array type. If the component type is a parameterized type, it
     * must only have unbounded wildcard arguments, otherwise a MalformedParameterizedTypeException
     * is thrown.
     * 
     * @param componentType - the component type of the array
     * @return a (possibly generic) array type.
     * @throws NullPointerException if any of the actual parameters are {@code null}
     */
    EspressoType makeArrayType(EspressoType componentType);

    /**
     * Returns the reflective representation of type {@code byte}.
     * 
     * @return the reflective representation of type {@code byte}.
     */
    EspressoType makeByte();

    /**
     * Returns the reflective representation of type {@code boolean}.
     * 
     * @return the reflective representation of type {@code boolean}.
     */
    EspressoType makeBool();

    /**
     * Returns the reflective representation of type {@code short}.
     * 
     * @return the reflective representation of type {@code short}.
     */
    EspressoType makeShort();

    /**
     * Returns the reflective representation of type {@code char}.
     * 
     * @return the reflective representation of type {@code char}.
     */
    EspressoType makeChar();

    /**
     * Returns the reflective representation of type {@code int}.
     * 
     * @return the reflective representation of type {@code int}.
     */
    EspressoType makeInt();

    /**
     * Returns the reflective representation of type {@code long}.
     * 
     * @return the reflective representation of type {@code long}.
     */
    EspressoType makeLong();

    /**
     * Returns the reflective representation of type {@code float}.
     * 
     * @return the reflective representation of type {@code float}.
     */
    EspressoType makeFloat();

    /**
     * Returns the reflective representation of type {@code double}.
     * 
     * @return the reflective representation of type {@code double}.
     */
    EspressoType makeDouble();

    /**
     * Returns the reflective representation of {@code void}.
     * 
     * @return the reflective representation of {@code void}.
     */
    EspressoType makeVoid();
}
