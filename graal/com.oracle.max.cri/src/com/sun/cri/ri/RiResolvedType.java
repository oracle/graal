/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.ri;

import java.lang.annotation.*;
import java.lang.reflect.*;

import com.sun.cri.ci.*;

/**
 * Represents a resolved in the compiler-runtime interface. Types include primitives, objects, {@code void},
 * and arrays thereof. Types, like fields and methods, are resolved through {@link RiConstantPool constant pools}, and
 * their actual implementation is provided by the {@link RiRuntime runtime} to the compiler.
 */
public interface RiResolvedType extends RiType {

    /**
     * Gets the encoding of (that is, a constant representing the value of) the specified part of this type.
     * @param r the part of the this type
     * @return a constant representing a reference to the specified part of this type
     */
    CiConstant getEncoding(Representation r);

    /**
     * Checks whether this type has any subclasses so far. Any decisions
     * based on this information require the registration of a dependency, since
     * this information may change.
     * @return {@code true} if this class has subclasses
     */
    boolean hasSubclass();

    /**
     * Checks whether this type has a finalizer method.
     * @return {@code true} if this class has a finalizer
     */
    boolean hasFinalizer();

    /**
     * Checks whether this type has any finalizable subclasses so far. Any decisions
     * based on this information require the registration of a dependency, since
     * this information may change.
     * @return {@code true} if this class has any subclasses with finalizers
     */
    boolean hasFinalizableSubclass();

    /**
     * Checks whether this type is an interface.
     * @return {@code true} if this type is an interface
     */
    boolean isInterface();

    /**
     * Checks whether this type is an instance class.
     * @return {@code true} if this type is an instance class
     */
    boolean isInstanceClass();

    /**
     * Checks whether this type is an array class.
     * @return {@code true} if this type is an array class
     */
    boolean isArrayClass();

    /**
     * Gets the access flags for this type. Only the flags specified in the JVM specification
     * will be included in the returned mask. The utility methods in the {@link Modifier} class
     * should be used to query the returned mask for the presence/absence of individual flags.
     * @return the mask of JVM defined class access flags defined for this type
     */
    int accessFlags();

    /**
     * Checks whether this type is initialized.
     * @return {@code true} if this type is initialized
     */
    boolean isInitialized();

    /**
     * Checks whether this type is a subtype of another type.
     * @param other the type to test
     * @return {@code true} if this type a subtype of the specified type
     */
    boolean isSubtypeOf(RiResolvedType other);

    /**
     * Checks whether the specified object is an instance of this type.
     * @param obj the object to test
     * @return {@code true} if the object is an instance of this type
     */
    boolean isInstance(CiConstant obj);

    /**
     * Attempts to get an exact type for this type. Final classes,
     * arrays of final classes, and primitive types all have exact types.
     * @return the exact type of this type, if it exists; {@code null} otherwise
     */
    RiResolvedType exactType();

    /**
     * Gets the super type of this type or {@code null} if no such type exists.
     */
    RiResolvedType superType();

    /**
     * Attempts to get the unique concrete subtype of this type.
     * @return the exact type of this type, if it exists; {@code null} otherwise
     */
    RiResolvedType uniqueConcreteSubtype();

    /**
     * For array types, gets the type of the components.
     * @return the component type of this array type
     */
    RiResolvedType componentType();

    /**
     * Gets the type representing an array with elements of this type.
     * @return a new compiler interface type representing an array of this type
     */
    RiResolvedType arrayOf();

    /**
     * Resolves the method implementation for virtual dispatches on objects
     * of this dynamic type.
     * @param method the method to select the implementation of
     * @return the method implementation that would be selected at runtime
     */
    RiResolvedMethod resolveMethodImpl(RiResolvedMethod method);

    /**
     * Given an RiMethod a, returns a concrete RiMethod b that is the only possible
     * unique target for a virtual call on a(). Returns {@code null} if either no
     * such concrete method or more than one such method exists. Returns the method a
     * if a is a concrete method that is not overridden. If the compiler uses the
     * result of this method for its compilation, it must register an assumption
     * (see {@link CiAssumptions}), because dynamic class loading can invalidate
     * the result of this method.
     * @param method the method a for which a unique concrete target is searched
     * @return the unique concrete target or {@code null} if no such target exists
     *         or assumptions are not supported by this runtime
     */
    RiResolvedMethod uniqueConcreteMethod(RiResolvedMethod method);

    /**
     * Returns the instance fields declared in this class sorted by field offset.
     * @return an array of instance fields
     */
    RiResolvedField[] declaredFields();

    /**
     * Returns this type's annotation of a specified type.
     *
     * @param annotationClass the Class object corresponding to the annotation type
     * @return the annotation of type {@code annotationClass} for this type if present, else null
     */
    <T extends Annotation> T getAnnotation(Class<T> annotationClass);

    /**
     * Returns the java.lang.Class object representing this RiType instance or {@code null} if none exists.
     * @return the java.lang.Class object
     */
    Class<?> toJava();
}
