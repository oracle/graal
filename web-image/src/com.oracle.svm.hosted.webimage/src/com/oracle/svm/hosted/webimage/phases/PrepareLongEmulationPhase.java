/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.phases;

import java.util.List;

import com.oracle.svm.webimage.longemulation.Long64;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnreachableNode;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;

/**
 * This phase strengthens the stamps of all {@link ValueNode} that produce a {@link Long64} that is
 * used to emulate a primitive long. The stamps are strengthened by guaranteeing that the
 * {@link Long64} value cannot be null (since a primitive long cannot be null). This avoids
 * unnecessary null pointer checks.
 *
 * Additionally, all {@link UnwindNode} in the class {@link Long64} are made unreachable because it
 * is guaranteed that they will never be reached. The only way an UnwindNode can be reached in the
 * methods of {@link Long64} is by either a nullpointer exception or a division by zero. Before an
 * {@link jdk.graal.compiler.nodes.calc.IntegerDivRemNode} it is checked that the divisor is not
 * zero, therefore we cannot have division by zero in the methods of {@link Long64}.
 */
public class PrepareLongEmulationPhase extends BasePhase<CoreProviders> {

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before prepare Long emulation");
        Stamp nonNull = StampFactory.objectNonNull();
        for (Node n : graph.getNodes()) {
            if (n instanceof ValueNode && ((ValueNode) n).stamp(NodeView.DEFAULT) instanceof ObjectStamp) {
                ValueNode vn = (ValueNode) n;
                if (vn.stamp(NodeView.DEFAULT).javaType(context.getMetaAccess()).equals(context.getMetaAccess().lookupJavaType(Long64.class))) {
                    ObjectStamp vnStamp = (ObjectStamp) vn.stamp(NodeView.DEFAULT);
                    vn.setStamp(vnStamp.join(nonNull));
                }
            }
        }
        if (graph.method().getDeclaringClass().equals(context.getMetaAccess().lookupJavaType(Long64.class))) {
            for (InvokeWithExceptionNode inv : graph.getNodes(InvokeWithExceptionNode.TYPE)) {
                inv.replaceWithInvoke();
            }
            /*
             * Make unwind nodes in Long64 unreachable. The only unwind nodes that we have come from
             * division by zero. These cannot be reached because we already have a zero check before
             * a division node.
             */
            List<UnwindNode> unwindNodes = graph.getNodes(UnwindNode.TYPE).snapshot();
            assert unwindNodes.size() <= 1 : unwindNodes;
            for (UnwindNode unwindNode : unwindNodes) {
                UnreachableNode unreachableNode = new UnreachableNode();
                unreachableNode = graph.add(unreachableNode);
                graph.addBeforeFixed(unwindNode, unreachableNode);
            }
        }
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After prepare Long emulation");
    }
}
