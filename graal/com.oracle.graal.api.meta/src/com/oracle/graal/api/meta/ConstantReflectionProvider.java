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
package com.oracle.graal.api.meta;

/**
 * Reflection operations on values represented as {@linkplain Constant constants}.
 */
public interface ConstantReflectionProvider {

    /**
     * Compares two constants for equality. The equality relationship is symmetric. If the constants
     * cannot be compared at this point, the return value is {@code null};
     * 
     * @return {@code true} if the two parameters represent the same runtime object, {@code false}
     *         if they are different, or {@code null} if the parameters cannot be compared.
     */
    Boolean constantEquals(Constant x, Constant y);

    /**
     * Returns the length of an array that is wrapped in a {@link Constant} object. If {@code array}
     * is not an array, or the array length is not available at this point, the return value is
     * {@code null}.
     */
    Integer lookupArrayLength(Constant array);

    /**
     * Reads a value of this kind using a base address and a displacement.
     * 
     * @param base the base address from which the value is read
     * @param displacement the displacement within the object in bytes
     * @param compressible whether this is a read of a compressed or an uncompressed pointer
     * @return the read value encapsulated in a {@link Constant} object, or {@code null} if the
     *         value cannot be read.
     */
    Constant readUnsafeConstant(Kind kind, Object base, long displacement, boolean compressible);
}
