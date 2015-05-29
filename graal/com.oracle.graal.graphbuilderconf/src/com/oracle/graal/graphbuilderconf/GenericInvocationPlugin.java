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

import com.oracle.jvmci.meta.ResolvedJavaMethod;
import com.oracle.graal.nodes.*;

/**
 * Plugin for handling an invocation based on some property of the method being invoked such as any
 * annotations it may have.
 */
public interface GenericInvocationPlugin extends GraphBuilderPlugin {
    /**
     * Executes this plugin for an invocation of a given method with a given set of arguments.
     *
     * @return {@code true} if this plugin handled the invocation, {@code false} if not
     */
    boolean apply(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args);
}
