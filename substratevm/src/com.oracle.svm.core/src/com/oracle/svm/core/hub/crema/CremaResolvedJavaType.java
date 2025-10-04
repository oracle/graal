/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub.crema;

import java.util.List;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

public interface CremaResolvedJavaType extends ResolvedJavaType {

    /**
     * Returns an array all the declared fields of this type.
     *
     * @return An array of {@code CremaResolvedJavaField} objects representing all the declared
     *         fields of this type
     */
    CremaResolvedJavaField[] getDeclaredFields();

    /**
     * Returns an array all the declared method of this type.
     *
     * @return An array of {@code CremaResolvedJavaMethod} objects representing all the declared
     *         methods of this type
     */
    CremaResolvedJavaMethod[] getDeclaredCremaMethods();

    /**
     * Returns an array all the declared constructors of this type.
     *
     * @return An array of {@code CremaResolvedJavaMethod} objects representing all the declared
     *         constructors of this type
     */
    @Override
    CremaResolvedJavaMethod[] getDeclaredConstructors();

    /**
     * Returns an array of {@code CremaResolvedJavaRecordComponent} objects representing all the
     * record components of this record class, or {@code null} if this type does not represent a
     * record class.
     *
     * @return An array of {@code RecordComponent} objects representing all the record components of
     *         this record class, or {@code null} if this type does not represent a record class
     */
    @Override
    List<? extends CremaResolvedJavaRecordComponent> getRecordComponents();

    /**
     * Retrieves the raw annotation bytes for this field.
     *
     * @return the raw annotations as a byte array
     */
    byte[] getRawAnnotations();

    /**
     * Retrieves the raw type annotation bytes for this field.
     *
     * @return the raw type annotations as a byte array
     */
    byte[] getRawTypeAnnotations();

    /**
     * If this object represents a local or anonymous class within a method, returns a
     * {@link CremaEnclosingMethodInfo} object representing the immediately enclosing method of the
     * underlying class. Returns {@code null} otherwise.
     *
     * @return the immediately enclosing method of the underlying class, if that class is a local or
     *         anonymous class; otherwise {@code null}.
     */
    CremaEnclosingMethodInfo getEnclosingMethod();

    /**
     * Returns an array of {@code JavaType} objects reflecting all the classes and interfaces
     * declared as members of the class represented by this object. This includes public, protected,
     * default (package) access, and private classes and interfaces declared by the this object, but
     * excludes inherited classes and interfaces. This method returns an array of length 0 if the
     * class declares no classes or interfaces as members, or if this object represents a primitive
     * type, an array class, or void.
     *
     * <p>
     * See JLS 8.5 Member Class and Interface Declarations
     *
     * @return the array of {@code JavaType} objects representing all the declared members of this
     *         type
     */
    JavaType[] getDeclaredClasses();

    /**
     * Returns the permitted subclasses for a sealed class. Note that this method must only be
     * called on a known sealed resolved type.
     *
     * @return array of permitted subclasses as a JavaType array
     */
    JavaType[] getPermittedSubClasses();

    /**
     * Returns the resolved nest members of this resolved java type.
     *
     * @return a ResolvedJavaType representing the nest host
     */
    ResolvedJavaType[] getNestMembers();

    /**
     * Returns the resolved nest host of this resolved java type.
     *
     * @return a JavaType representing the nest host
     */
    ResolvedJavaType getNestHost();

    record CremaEnclosingMethodInfo(JavaType enclosingType, String name, String description) {
    }
}
