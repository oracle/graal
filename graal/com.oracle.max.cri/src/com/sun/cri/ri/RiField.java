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
 * Represents a reference to a field, including both resolved and unresolved fields. Fields, like methods and types, are
 * resolved through {@link RiConstantPool constant pools}, and their actual implementation is provided by the
 * {@link RiRuntime runtime} to the compiler.
 */
public interface RiField {
    /**
     * Gets the name of this field as a string.
     * @return the name of this field
     */
    String name();

    /**
     * Gets the type of this field as a compiler-runtime interface type.
     * @return the type of this field
     */
    RiType type();

    /**
     * Gets the kind of this field.
     * @param architecture When true, the architecture-specific kind used for emitting machine code is returned.
     *        When false, the kind according to the Java specification is returned.
     * @return the kind
     */
    CiKind kind(boolean architecture);

    /**
     * Gets the holder of this field as a compiler-runtime interface type.
     * @return the holder of this field
     */
    RiType holder();
}
