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
package com.oracle.max.graal.compiler.phases;

import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.sun.cri.ri.*;

public class IntrinsificationPhase extends Phase {

    private final GraalRuntime runtime;

    public IntrinsificationPhase(GraalRuntime runtime) {
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

    public static void tryIntrinsify(Invoke invoke, GraalRuntime runtime) {
        RiResolvedMethod target = invoke.callTarget().targetMethod();
        tryIntrinsify(invoke, target, runtime);
    }

    @SuppressWarnings("unchecked")
    public static void tryIntrinsify(Invoke invoke, RiResolvedMethod target, GraalRuntime runtime) {
        StructuredGraph intrinsicGraph = (StructuredGraph) target.compilerStorage().get(Graph.class);
        if (intrinsicGraph == null) {
            // TODO (ph) remove once all intrinsics are available via RiMethod
            intrinsicGraph = runtime.intrinsicGraph(invoke.stateAfter().method(), invoke.bci(), target, invoke.callTarget().arguments());
        }
        if (intrinsicGraph != null) {
            InliningUtil.inline(invoke, intrinsicGraph, true);
        }
    }
}
