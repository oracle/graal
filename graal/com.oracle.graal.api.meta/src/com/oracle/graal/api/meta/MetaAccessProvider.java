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

import java.lang.reflect.*;


public interface MetaAccessProvider {


    /**
     * Returns the JavaType object representing the base type for the given kind.
     */
    ResolvedJavaType getResolvedJavaType(Kind kind);

    /**
     * Returns the type of the given constant object.
     *
     * @return {@code null} if {@code constant.isNull() || !constant.kind.isObject()}
     */
    ResolvedJavaType getTypeOf(Constant constant);


    ResolvedJavaType getResolvedJavaType(Class<?> clazz);

    /**
     * Used by the canonicalizer to compare objects, since a given runtime might not want to expose the real objects to the compiler.
     *
     * @return true if the two parameters represent the same runtime object, false otherwise
     */
    boolean areConstantObjectsEqual(Constant x, Constant y);

    /**
     * Provides the {@link JavaMethod} for a {@link Method} obtained via reflection.
     */
    ResolvedJavaMethod getResolvedJavaMethod(Method reflectionMethod);

    /**
     * Gets the length of the array that is wrapped in a CiConstant object.
     */
    int getArrayLength(Constant array);
}
