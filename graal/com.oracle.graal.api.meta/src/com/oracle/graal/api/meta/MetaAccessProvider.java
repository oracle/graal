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
 * Provides access to the metadata of a class typically provided in a class file.
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

    /**
     * Encodes a deoptimization action and a deoptimization reason in an integer value.
     * 
     * @return the encoded value as an integer
     */
    Constant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason);
}
