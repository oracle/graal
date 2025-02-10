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

import java.lang.reflect.MalformedParameterizedTypeException;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.impl.EspressoType;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ParameterizedEspressoType;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/** Implementing class for ParameterizedType interface. */

public final class ParameterizedTypeImpl implements ParameterizedEspressoType {
    private final EspressoType[] actualTypeArguments;
    private final Klass rawType;
    private final EspressoType ownerType;
    @CompilationFinal private StaticObject guestTypeLiteral;

    private ParameterizedTypeImpl(Klass rawType,
                    EspressoType[] actualTypeArguments,
                    EspressoType ownerType) {
        this.actualTypeArguments = actualTypeArguments;
        this.rawType = rawType;
        this.ownerType = (ownerType != null) ? ownerType : rawType;
    }

    @Override
    public StaticObject getGuestTypeLiteral() {
        if (guestTypeLiteral == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            EspressoContext context = getRawType().getContext();
            StaticObject result = context.getAllocator().createNew(context.getMeta().polyglot.TypeLiteral$InternalTypeLiteral);
            context.getMeta().polyglot.HIDDEN_TypeLiteral_internalType.setHiddenObject(result, this);
            context.getMeta().polyglot.TypeLiteral_rawType.setObject(result, getRawType().mirror());
            guestTypeLiteral = result;
        }
        return guestTypeLiteral;
    }

    /**
     * Static factory. Given a (generic) class, actual type arguments and an owner type, creates a
     * parameterized type. This class can be instantiated with a raw type that does not represent a
     * generic type, provided the list of actual type arguments is empty. If the ownerType argument
     * is null, the declaring class of the raw type is used as the owner type.
     * <p>
     * This method throws a MalformedParameterizedTypeException under the following circumstances:
     * If the number of actual type arguments (i.e., the size of the array {@code typeArgs}) does
     * not correspond to the number of formal type arguments. If any of the actual type arguments is
     * not an instance of the bounds on the corresponding formal.
     * 
     * @param rawType the Class representing the generic type declaration being instantiated
     * @param actualTypeArguments a (possibly empty) array of types representing the actual type
     *            arguments to the parameterized type
     * @param ownerType the enclosing type, if known.
     * @return An instance of {@code ParameterizedType}
     * @throws MalformedParameterizedTypeException if the instantiation is invalid
     */
    public static EspressoType make(Klass rawType,
                    EspressoType[] actualTypeArguments,
                    EspressoType ownerType) {
        return new ParameterizedTypeImpl(rawType, actualTypeArguments,
                        ownerType);
    }

    /**
     * Returns an array of {@code Type} objects representing the actual type arguments to this type.
     *
     * <p>
     * Note that in some cases, the returned array be empty. This can occur if this type represents
     * a non-parameterized type nested within a parameterized type.
     *
     * @return an array of {@code Type} objects representing the actual type arguments to this type
     * @throws TypeNotPresentException if any of the actual type arguments refers to a non-existent
     *             type declaration
     * @throws MalformedParameterizedTypeException if any of the actual type parameters refer to a
     *             parameterized type that cannot be instantiated for any reason
     * @since 1.5
     */
    @Override
    public EspressoType[] getTypeArguments() {
        return actualTypeArguments.clone();
    }

    /**
     * Returns the {@code Type} object representing the class or interface that declared this type.
     *
     * @return the {@code Type} object representing the class or interface that declared this type
     */
    @Override
    public Klass getRawType() {
        return rawType;
    }

    /**
     * Returns a {@code Type} object representing the type that this type is a member of. For
     * example, if this type is {@code O<T>.I<S>}, return a representation of {@code O<T>}.
     *
     * <p>
     * If this type is a top-level type, {@code null} is returned.
     *
     * @return a {@code Type} object representing the type that this type is a member of. If this
     *         type is a top-level type, {@code null} is returned
     * @throws TypeNotPresentException if the owner type refers to a non-existent type declaration
     * @throws MalformedParameterizedTypeException if the owner type refers to a parameterized type
     *             that cannot be instantiated for any reason
     *
     */
    @Override
    public EspressoType getOwnerType() {
        return ownerType;
    }

    /*
     * From the JavaDoc for java.lang.reflect.ParameterizedType "Instances of classes that implement
     * this interface must implement an equals() method that equates any two instances that share
     * the same generic type declaration and have equal type parameters."
     */
    @Override
    @TruffleBoundary
    public boolean equals(Object o) {
        if (o instanceof ParameterizedEspressoType that) {
            // Check that information is equivalent

            if (this == that) {
                return true;
            }

            EspressoType thatOwner = that.getOwnerType();
            EspressoType thatRawType = that.getRawType();

            return Objects.equals(ownerType, thatOwner) &&
                            Objects.equals(rawType, thatRawType) &&
                            Arrays.equals(actualTypeArguments, // avoid clone
                                            that.getTypeArguments());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(actualTypeArguments) ^
                        Objects.hashCode(ownerType) ^
                        Objects.hashCode(rawType);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (ownerType != null) {
            sb.append(ownerType.getRawType().getTypeName());

            sb.append("$");

            if (ownerType instanceof ParameterizedTypeImpl) {
                // Find simple name of nested type by removing the
                // shared prefix with owner.
                sb.append(rawType.getNameAsString().replace(((ParameterizedTypeImpl) ownerType).rawType.getName() + "$",
                                ""));
            } else {
                sb.append(rawType.getNameAsString());
            }
        } else {
            sb.append(rawType.getName());
        }

        if (actualTypeArguments != null) {
            StringJoiner sj = new StringJoiner(", ", "<", ">");
            sj.setEmptyValue("");
            for (EspressoType t : actualTypeArguments) {
                sj.add(t.getRawType().getTypeName());
            }
            sb.append(sj);
        }

        return sb.toString();
    }
}
