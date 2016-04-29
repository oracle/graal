/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.verify;

import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.Graph;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.phases.VerifyPhase;
import com.oracle.graal.phases.tiers.PhaseContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 *
 * Verifies that node types implementing the {@link Virtualizable} interface use it correctly.
 * Implementors of {@link Virtualizable#virtualize(com.oracle.graal.nodes.spi.VirtualizerTool)} must
 * not apply effects on their {@link Graph graph} that cannot be easily undone.
 */
public class VerifyVirtualizableUsage extends VerifyPhase<PhaseContext> {

    @Override
    protected boolean verify(StructuredGraph graph, PhaseContext context) {
        final ResolvedJavaType graphType = context.getMetaAccess().lookupJavaType(Graph.class);
        final ResolvedJavaType virtualizableType = context.getMetaAccess().lookupJavaType(Virtualizable.class);
        final ResolvedJavaType constantNodeType = context.getMetaAccess().lookupJavaType(ConstantNode.class);
        if (virtualizableType.isAssignableFrom(graph.method().getDeclaringClass()) && graph.method().getName().equals("virtualize")) {
            for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
                int bci = t.invoke().bci();
                ResolvedJavaMethod callee = t.targetMethod();
                String calleeName = callee.getName();
                if (callee.getDeclaringClass().equals(graphType)) {
                    if (calleeName.equals("add") || calleeName.equals("addWithoutUnique") || calleeName.equals("addOrUnique") || calleeName.equals("addWithoutUniqueWithInputs") ||
                                    calleeName.equals("addOrUniqueWithInputs")) {
                        verifyVirtualizableEffectArguments(constantNodeType, graph.method(), callee, bci, t.arguments(), 1);
                    }
                }
            }
        }
        return true;
    }

    private static void verifyVirtualizableEffectArguments(ResolvedJavaType constantNodeType, ResolvedJavaMethod caller, ResolvedJavaMethod callee, int bciCaller,
                    NodeInputList<? extends Node> arguments, int startIdx) {
        /*
         * Virtualizable.virtualize should never apply effects on the graph during the execution of
         * the call as the handling of loops during pea might be speculative and does not hold. We
         * should only allow nodes changing the graph that do no harm like constants.
         */
        int i = 0;
        for (Node arg : arguments) {
            if (i >= startIdx) {
                Stamp argStamp = ((ValueNode) arg).stamp();
                if (argStamp instanceof ObjectStamp) {
                    ObjectStamp objectStamp = (ObjectStamp) argStamp;
                    ResolvedJavaType argStampType = objectStamp.type();
                    if (!(argStampType.equals(constantNodeType))) {
                        StackTraceElement e = caller.asStackTraceElement(bciCaller);
                        throw new VerificationError("%s:Parameter %d in call to %s (which has effects on the graph) is not a " +
                                        "constant and thus not safe to apply during speculative virtualization.", e, i, callee.format("%H.%n(%p)"));
                    }
                }
            }
            i++;
        }
    }

}
