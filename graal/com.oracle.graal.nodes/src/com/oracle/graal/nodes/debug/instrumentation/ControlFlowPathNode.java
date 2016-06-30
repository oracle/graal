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
package com.oracle.graal.nodes.debug.instrumentation;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeFlood;
import com.oracle.graal.graph.NodeNodeMap;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.AbstractEndNode;
import com.oracle.graal.nodes.AbstractMergeNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.EndNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.LoopEndNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValuePhiNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;

/**
 * The {@code ControlFlowPathNode} represents an integer indicating which control flow path is taken
 * between an {@link InstrumentationNode} and its target node. Note that if a target node is
 * substituted by a snippet, the new target node will be the first control flow node of the inlined
 * sub-graph.
 */
@NodeInfo
public final class ControlFlowPathNode extends FixedWithNextNode implements InstrumentationInliningCallback, LIRLowerable {

    public static final NodeClass<ControlFlowPathNode> TYPE = NodeClass.create(ControlFlowPathNode.class);

    public ControlFlowPathNode() {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
    }

    /**
     * @return true if there is a control flow path between {@code from} and {@code to}.
     */
    private static boolean isCFGAccessible(FixedNode from, FixedNode to) {
        NodeFlood flood = from.graph().createNodeFlood();
        flood.add(from);
        for (Node current : flood) {
            if (current instanceof LoopEndNode) {
                continue;
            } else if (current instanceof AbstractEndNode) {
                flood.add(((AbstractEndNode) current).merge());
            } else {
                flood.addAll(current.successors());
            }
        }
        return flood.isMarked(to);
    }

    @Override
    public void preInlineInstrumentation(InstrumentationNode instrumentation) {
    }

    /**
     * The {@code PathFinder} identifies all paths between an {@link InstrumentationNode} and its
     * target.
     */
    static class PathFinder {

        private NodeNodeMap lastMerge; // keep track of the last preceding AbstractMergeNode for
                                       // each FixedNode between the given InstrumentationNode and
                                       // its target

        PathFinder(InstrumentationNode instrumentation) {
            lastMerge = new NodeNodeMap(instrumentation.graph());
            NodeFlood cfgFlood = instrumentation.graph().createNodeFlood();
            cfgFlood.add(instrumentation.getTarget());
            // iterate through the control flow until the InstrumentationNode is met
            for (Node current : cfgFlood) {
                if (current instanceof LoopEndNode || current instanceof InstrumentationNode) {
                    // do nothing
                } else if (current instanceof AbstractEndNode) {
                    AbstractMergeNode merge = ((AbstractEndNode) current).merge();
                    cfgFlood.add(merge);
                    lastMerge.put(merge, merge);
                } else {
                    for (Node successor : current.successors()) {
                        cfgFlood.add(successor);
                        AbstractMergeNode merge = (AbstractMergeNode) lastMerge.get(current);
                        if (merge != null) {
                            lastMerge.put(successor, merge);
                        }
                    }
                }
            }
        }

        private int pathIndex = 0;

        /**
         * Create a {@link ValuePhiNode} that represents the taken path for the given
         * {@link AbstractMergeNode}.
         */
        ValuePhiNode createPhi(AbstractMergeNode merge) {
            ValuePhiNode phi = merge.graph().addWithoutUnique(new ValuePhiNode(StampFactory.intValue(), merge));
            for (EndNode end : merge.cfgPredecessors()) {
                AbstractMergeNode mergeForEnd = (AbstractMergeNode) lastMerge.get(end);
                if (mergeForEnd != null) {
                    phi.addInput(createPhi(mergeForEnd));
                } else {
                    phi.addInput(ConstantNode.forInt(pathIndex++, merge.graph()));
                }
            }
            return phi;
        }

        AbstractMergeNode getLastMerge(Node node) {
            return (AbstractMergeNode) lastMerge.get(node);
        }

    }

    @Override
    public void postInlineInstrumentation(InstrumentationNode instrumentation) {
        ValueNode target = instrumentation.getTarget();
        if (target != null) {
            PathFinder pathFinder = new PathFinder(instrumentation);
            AbstractMergeNode merge = pathFinder.getLastMerge(instrumentation);
            if (merge != null && isCFGAccessible(merge, instrumentation)) { // ensure the scheduling
                // create a ValuePhiNode selecting between constant integers that represent the
                // control flow paths
                graph().replaceFixedWithFloating(this, pathFinder.createPhi(merge));
                return;
            }
        }
        graph().replaceFixedWithFloating(this, ConstantNode.forInt(-1, graph()));
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        GraalError.shouldNotReachHere("GraalDirectives.controlFlowPath() should be enclosed in an instrumentation");
    }

}
