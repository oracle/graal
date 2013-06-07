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

/**
 * Interface implemented by the runtime to allow access to its metadata.
 */
public interface MetaAccessProvider {

    /**
     * Returns the resolved Java type representing a given Java class.
     * 
     * @param clazz the Java class object
     * @return the resolved Java type object
     */
    ResolvedJavaType lookupJavaType(Class<?> clazz);

    /**
     * Provides the {@link ResolvedJavaMethod} for a {@link Method} obtained via reflection.
     */
    ResolvedJavaMethod lookupJavaMethod(Method reflectionMethod);

    /**
     * Provides the {@link ResolvedJavaMethod} for a {@link Constructor} obtained via reflection.
     */
    ResolvedJavaMethod lookupJavaConstructor(Constructor reflectionConstructor);

    /**
     * Provides the {@link ResolvedJavaField} for a {@link Field} obtained via reflection.
     */
    ResolvedJavaField lookupJavaField(Field reflectionField);

    /**
     * Returns the resolved Java type of the given {@link Constant} object.
     * 
     * @return {@code null} if {@code constant.isNull() || !constant.kind.isObject()}
     */
    ResolvedJavaType lookupJavaType(Constant constant);

    /**
     * Parses a <a
     * href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">method
     * descriptor</a> into a {@link Signature}. The behavior of this method is undefined if the
     * method descriptor is not well formed.
     */
    Signature parseMethodDescriptor(String methodDescriptor);

    /**
     * Compares two constants for equality. This is used instead of {@link Constant#equals(Object)}
     * in case the runtime has an interpretation for object equality other than
     * {@code x.asObject() == y.asObject()}. For primitive constants, this is equivalent to calling
     * {@code x.equals(y)}. The equality relationship is symmetric.
     * 
     * @return {@code true} if the two parameters represent the same runtime object, {@code false}
     *         otherwise
     */
    boolean constantEquals(Constant x, Constant y);

    /**
     * Returns the length of an array that is wrapped in a {@link Constant} object.
     * 
     * @throws IllegalArgumentException if {@code array} is not an array
     */
    int lookupArrayLength(Constant array);

    /**
     * Reads a value of this kind using a base address and a displacement.
     * 
     * @param base the base address from which the value is read
     * @param displacement the displacement within the object in bytes
     * @return the read value encapsulated in a {@link Constant} object, or {@code null} if the
     *         value cannot be read.
     */
    Constant readUnsafeConstant(Kind kind, Object base, long displacement);

    /**
     * Determines if a given foreign call is side-effect free. Deoptimization cannot return
     * execution to a point before a foreign call that has a side effect.
     */
    boolean isReexecutable(ForeignCallDescriptor descriptor);

    /**
     * Gets the set of memory locations killed by a given foreign call. Returning the special value
     * {@link LocationIdentity#ANY_LOCATION} denotes that the call kills all memory locations.
     * Returning any empty array denotes that the call does not kill any memory locations.
     */
    LocationIdentity[] getKilledLocations(ForeignCallDescriptor descriptor);

    /**
     * Determines if deoptimization can occur during a given foreign call.
     */
    boolean canDeoptimize(ForeignCallDescriptor descriptor);
}
