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

import com.sun.cri.ci.*;

/**
 * Represents a resolved or unresolved type in the compiler-runtime interface. Types include primitives, objects, {@code void},
 * and arrays thereof.
 */
public interface RiType {

    /**
     * Represents each of the several different parts of the runtime representation of
     * a type which compiled code may need to reference individually. These may or may not be
     * different objects or data structures, depending on the runtime system.
     */
    public enum Representation {
        /**
         * The runtime representation of the data structure containing the static fields of this type.
         */
        StaticFields,

        /**
         * The runtime representation of the Java class object of this type.
         */
        JavaClass,

        /**
         * The runtime representation of the "hub" of this type--that is, the closest part of the type
         * representation which is typically stored in the object header.
         */
        ObjectHub,

        /**
         * The runtime representation of the type information for an object, which is typically used
         * for subtype tests.
         */
        TypeInfo
    }

    /**
     * Gets the name of this type in internal form. The following are examples of strings returned by this method:
     * <pre>
     *     "Ljava/lang/Object;"
     *     "I"
     *     "[[B"
     * </pre>
     *
     * @return the name of this type in internal form
     */
    String name();

    /**
     * For array types, gets the type of the components.
     * @return the component type of this array type
     */
    RiType componentType();

    /**
     * Gets the type representing an array with elements of this type.
     * @return a new compiler interface type representing an array of this type
     */
    RiType arrayOf();

    /**
     * Gets the kind of this compiler interface type.
     * @param architecture When true, the architecture-specific kind used for emitting machine code is returned.
     *        When false, the kind according to the Java specification is returned.
     * @return the kind
     */
    CiKind kind(boolean architecture);

    /**
     * Gets the kind used to represent the specified part of this type.
     * @param r the part of the this type
     * @return the kind of constants for the specified part of the type
     */
    CiKind getRepresentationKind(Representation r);
}
