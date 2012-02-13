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

import static com.oracle.max.graal.graph.iterators.NodePredicates.*;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.iterators.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.PhiNode.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.virtual.*;

public class BoxingEliminationPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        if (graph.getNodes(UnboxNode.class).isNotEmpty()) {

            Map<PhiNode, PhiNode> phiReplacements = new HashMap<>();
            for (UnboxNode unboxNode : graph.getNodes(UnboxNode.class)) {
                tryEliminate(graph, unboxNode, phiReplacements);
            }

            new DeadCodeEliminationPhase().apply(graph);

            for (BoxNode boxNode : graph.getNodes(BoxNode.class)) {
                tryEliminate(boxNode);
            }
        }
    }

    private void tryEliminate(StructuredGraph graph, UnboxNode unboxNode, Map<PhiNode, PhiNode> phiReplacements) {
        ValueNode unboxedValue = unboxedValue(unboxNode.source(), unboxNode.destinationKind(), phiReplacements);
        if (unboxedValue != null) {
            assert unboxedValue.kind() == unboxNode.kind();
            unboxNode.replaceAtUsages(unboxedValue);
            graph.removeFixed(unboxNode);
        }
    }

    private PhiNode getReplacementPhi(PhiNode phiNode, CiKind kind, Map<PhiNode, PhiNode> phiReplacements) {
        if (!phiReplacements.containsKey(phiNode)) {
            PhiNode result = null;
            if (phiNode.stamp().nonNull()) {
                RiResolvedType exactType = phiNode.stamp().exactType();
                if (exactType != null && exactType.toJava() == kind.toUnboxedJavaClass()) {
                    StructuredGraph graph = (StructuredGraph) phiNode.graph();
                    result = graph.add(new PhiNode(kind, phiNode.merge(), PhiType.Value));
                    phiReplacements.put(phiNode, result);
                    virtualizeUsages(phiNode, result, exactType);
                    int i = 0;
                    for (ValueNode n : phiNode.values()) {
                        ValueNode unboxedValue = unboxedValue(n, kind, phiReplacements);
                        if (unboxedValue != null) {
                            assert unboxedValue.kind() == kind;
                            result.addInput(unboxedValue);
                        } else {
                            UnboxNode unboxNode = graph.add(new UnboxNode(kind, n));
                            FixedNode pred = phiNode.merge().phiPredecessorAt(i);
                            graph.addBeforeFixed(pred, unboxNode);
                            result.addInput(unboxNode);
                        }
                        ++i;
                    }
                }
            }
        }
        return phiReplacements.get(phiNode);
    }

    private ValueNode unboxedValue(ValueNode n, CiKind kind, Map<PhiNode, PhiNode> phiReplacements) {
        if (n instanceof BoxNode) {
            BoxNode boxNode = (BoxNode) n;
            return boxNode.source();
        } else if (n instanceof PhiNode) {
            PhiNode phiNode = (PhiNode) n;
            return getReplacementPhi(phiNode, kind, phiReplacements);
        } else {
            return null;
        }
    }

    private static void tryEliminate(BoxNode boxNode) {

        virtualizeUsages(boxNode, boxNode.source(), boxNode.exactType());

        if (boxNode.usages().filter(isNotA(FrameState.class).nor(VirtualObjectFieldNode.class)).isNotEmpty()) {
            // Elimination failed, because boxing object escapes.
            return;
        }

        // TODO(ls) this seems weird: there might still be references to boxNode, yet it is deleted...

        FrameState stateAfter = boxNode.stateAfter();
        boxNode.setStateAfter(null);
        stateAfter.safeDelete();

        ((StructuredGraph) boxNode.graph()).removeFixed(boxNode);
    }

    private static void virtualizeUsages(ValueNode boxNode, ValueNode replacement, RiResolvedType exactType) {
        ValueNode virtualValueNode = null;
        VirtualObjectNode virtualObjectNode = null;
        for (Node n : boxNode.usages().filter(NodePredicates.isA(FrameState.class).or(VirtualObjectFieldNode.class)).snapshot()) {
            if (virtualValueNode == null) {
                virtualObjectNode = n.graph().unique(new BoxedVirtualObjectNode(exactType, replacement));
            }
            n.replaceFirstInput(boxNode, virtualObjectNode);
        }
    }
}
