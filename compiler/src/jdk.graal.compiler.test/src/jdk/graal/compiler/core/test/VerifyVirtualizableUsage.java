/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.core.test;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.phases.VerifyPhase;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies that node types implementing the {@link Virtualizable} interface use it correctly.
 * Implementors of {@link Virtualizable#virtualize(VirtualizerTool)} must not apply effects on their
 * {@link Graph graph} that cannot be easily undone.
 */
public class VerifyVirtualizableUsage extends VerifyPhase<CoreProviders> {
    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
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
                Stamp argStamp = ((ValueNode) arg).stamp(NodeView.DEFAULT);
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
