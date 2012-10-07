/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;

public class IntrinsificationPhase extends Phase {

    private final GraalCodeCacheProvider runtime;

    public IntrinsificationPhase(GraalCodeCacheProvider runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (InvokeNode invoke : graph.getNodes(InvokeNode.class)) {
            tryIntrinsify(invoke, runtime);
        }
        for (InvokeWithExceptionNode invoke : graph.getNodes(InvokeWithExceptionNode.class)) {
            tryIntrinsify(invoke, runtime);
        }
    }

    public static boolean canIntrinsify(Invoke invoke, ResolvedJavaMethod target, GraalCodeCacheProvider runtime) {
        return getIntrinsicGraph(invoke, target, runtime) != null;
    }

    private static void tryIntrinsify(Invoke invoke, GraalCodeCacheProvider runtime) {
        if (invoke.callTarget() instanceof MethodCallTargetNode && invoke.methodCallTarget().targetMethod() != null) {
            tryIntrinsify(invoke, invoke.methodCallTarget().targetMethod(), runtime);
        }
    }

    private static void tryIntrinsify(Invoke invoke, ResolvedJavaMethod target, GraalCodeCacheProvider runtime) {
        StructuredGraph intrinsicGraph = getIntrinsicGraph(invoke, target, runtime);
        if (intrinsicGraph != null) {
            Debug.log(" > Intrinsify %s", target);
            InliningUtil.inline(invoke, intrinsicGraph, true);
        }
    }

    private static StructuredGraph getIntrinsicGraph(Invoke invoke, ResolvedJavaMethod target, GraalCodeCacheProvider runtime) {
        StructuredGraph intrinsicGraph = (StructuredGraph) target.compilerStorage().get(Graph.class);
        if (intrinsicGraph == null) {
            // TODO remove once all intrinsics are available via compilerStorage
            intrinsicGraph = runtime.intrinsicGraph(invoke.stateAfter().method(), invoke.bci(), target, invoke.callTarget().arguments());
        }
        return intrinsicGraph;
    }
}
