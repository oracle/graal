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
     * Encodes a deoptimization action and a deoptimization reason in an integer value.
     * 
     * @param debugId an integer that can be used to track the origin of a deoptimization at
     *            runtime. There is no guarantee that the runtime will use this value. The runtime
     *            may even keep fewer than 32 bits.
     * 
     * @return the encoded value as an integer
     */
    Constant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int debugId);

    DeoptimizationReason decodeDeoptReason(Constant constant);

    DeoptimizationAction decodeDeoptAction(Constant constant);

    int decodeDebugId(Constant constant);
}
