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
package com.oracle.graal.graphbuilderconf;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;

public interface TypeCheckPlugin extends GraphBuilderPlugin {
    /**
     * Intercept the parsing of a CHECKCAST bytecode. If the method returns true, it must push
     * {@link GraphBuilderContext#push push} an object value as the result of the cast.
     *
     * @param b The context.
     * @param object The object to be type checked.
     * @param type The type that the object is checked against.
     * @param profile The profiling information for the type check, or null if no profiling
     *            information is available.
     * @return True if the plugin handled the cast, false if the bytecode parser should handle the
     *         cast.
     */
    default boolean checkCast(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        return false;
    }

    /**
     * Intercept the parsing of a INSTANCEOF bytecode. If the method returns true, it must push
     * {@link GraphBuilderContext#push push} an integer value with the result of the instanceof.
     *
     * @param b The context.
     * @param object The object to be type checked.
     * @param type The type that the object is checked against.
     * @param profile The profiling information for the type check, or null if no profiling
     *            information is available.
     * @return True if the plugin handled the instanceof, false if the bytecode parser should handle
     *         the instanceof.
     */
    default boolean instanceOf(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        return false;
    }
}
