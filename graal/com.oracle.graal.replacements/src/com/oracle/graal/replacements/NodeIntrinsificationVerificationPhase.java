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
package com.oracle.graal.replacements;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.replacements.Snippet.Fold;

/**
 * Checks that a graph contains no calls to {@link NodeIntrinsic} or {@link Fold} methods.
 */
public class NodeIntrinsificationVerificationPhase extends Phase {

    public static void verify(StructuredGraph graph) {
        new NodeIntrinsificationVerificationPhase().apply(graph);
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (MethodCallTargetNode n : graph.getNodes(MethodCallTargetNode.class)) {
            checkInvoke(n);
        }
    }

    private static void checkInvoke(MethodCallTargetNode n) {
        ResolvedJavaMethod target = n.targetMethod();
        if (target.getAnnotation(Node.NodeIntrinsic.class) != null) {
            error(n, "Intrinsification");
        } else if (target.getAnnotation(Fold.class) != null) {
            error(n, "Folding");
        }
    }

    private static void error(MethodCallTargetNode n, String failedAction) throws GraalInternalError {
        String context = n.graph().method().format("%H.%n");
        String target = n.invoke().callTarget().targetName();
        throw new GraalInternalError(failedAction + " of call to '" + target + "' in '" + context + "' failed, most likely due to a parameter annotated with @" +
                        ConstantNodeParameter.class.getSimpleName() + " not being resolvable to a constant during compilation");
    }
}
