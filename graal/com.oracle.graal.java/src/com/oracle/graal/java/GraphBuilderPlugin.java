/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.nodes.*;

/**
 * Extensions for handling certain bytecode instructions while building a
 * {@linkplain StructuredGraph graph} from a bytecode stream.
 */
public interface GraphBuilderPlugin {

    /**
     * Processes an invocation parsed in a bytecode stream and add nodes to a graph being
     * constructed that implement the semantics of the invocation.
     *
     * @param builder object being used to build a graph
     * @param args the arguments to the invocation
     */
    boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args);

    /**
     * Gets the target method handled by {@link #handleInvocation(GraphBuilderContext, ValueNode[])}
     * .
     */
    ResolvedJavaMethod getInvocationTarget(MetaAccessProvider metaAccess);

    static ResolvedJavaMethod resolveTarget(MetaAccessProvider metaAccess, Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            return metaAccess.lookupJavaMethod(methodName.equals("<init>") ? clazz.getDeclaredConstructor(parameterTypes) : clazz.getDeclaredMethod(methodName, parameterTypes));
        } catch (NoSuchMethodException | SecurityException e) {
            throw new GraalInternalError(e);
        }
    }
}
