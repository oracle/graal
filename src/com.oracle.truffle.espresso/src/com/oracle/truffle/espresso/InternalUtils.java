/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso;

import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Utility class to spy Espresso's underlying representation from the guest. Mostly used to validate
 * Espresso's design choices from a guest point of view.
 * 
 * @see StaticObject
 */
public class InternalUtils {
    public static final String[] EMPTY_BYTE_ARRAY = new String[0];

    /**
     * Returns a representation of how the primitive fields are stored in memory. If
     * {@code fieldName} appears at index i in the returned array, it means that, in class clazz,
     * the field named {@code fieldName} uses the i-th byte. A field can use multiple entries.
     * 
     * @param clazz the class you want to see the underlying representation of primitive fields.
     * @return the layout in memory of the primitive fields.
     */
    public static native String[] getUnderlyingPrimitiveFieldArray(@SuppressWarnings("unused") Class<?> clazz);

    /**
     * @param clazz The class you want to know how many bytes are required for the primitive fields.
     * @return the requested number of bytes
     */
    public static native int getPrimitiveFieldByteCount(@SuppressWarnings("unused") Class<?> clazz);

    /**
     * Returns a String representing all (even private ones) known fields in obj, except for hidden
     * ones.
     * 
     * @param obj the object you want to represent.
     * @return The representation of the obj.
     */
    public static native String toVerboseString(@SuppressWarnings("unused") Object obj);

    /**
     * Returns the total number of bytes an instance of clazz takes up in memory.
     * 
     * @param clazz the class you want to know the memory consumption of
     * @return the total number of bytes used by an instance of clazz.
     */
    public static native int bytesUsed(Class<?> clazz);

    /**
     * Checks whether or not we are running in espresso.
     * 
     * @return true iff we are running in Espresso.
     */
    public static native boolean inEspresso();
}
