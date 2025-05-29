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
package com.oracle.truffle.espresso.impl.generics.reflectiveObjects;

import java.util.Objects;

import com.oracle.truffle.espresso.impl.EspressoType;
import com.oracle.truffle.espresso.impl.GenericArrayEspressoType;
import com.oracle.truffle.espresso.impl.Klass;

/**
 * Implementation of GenericArrayType interface.
 */
public final class GenericArrayTypeImpl
                implements GenericArrayEspressoType {
    private final EspressoType genericComponentType;

    // private constructor enforces use of static factory
    private GenericArrayTypeImpl(EspressoType ct) {
        genericComponentType = ct;
    }

    /**
     * Factory method.
     * 
     * @param ct - the desired component type of the generic array type being created
     * @return a generic array type with the desired component type
     */
    public static GenericArrayTypeImpl make(EspressoType ct) {
        return new GenericArrayTypeImpl(ct);
    }

    /**
     * Returns a {@code Type} object representing the component type of this array.
     *
     * @return a {@code Type} object representing the component type of this array
     * @since 1.5
     */
    public EspressoType getGenericComponentType() {
        return genericComponentType; // return cached component type
    }

    @Override
    public String toString() {
        return getGenericComponentType().getRawType().getTypeName() + "[]";
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GenericArrayEspressoType) {
            GenericArrayEspressoType that = (GenericArrayEspressoType) o;

            return Objects.equals(genericComponentType, that.getGenericComponentType());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(genericComponentType);
    }

    @Override
    public Klass getRawType() {
        return genericComponentType.getRawType();
    }
}
