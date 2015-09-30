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
package com.oracle.graal.phases.common.query;

import java.util.Map;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeBitMap;
import com.oracle.graal.graph.NodeFlood;
import com.oracle.graal.graph.NodePosIterator;
import com.oracle.graal.graph.Position;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodes.AbstractEndNode;
import com.oracle.graal.nodes.AbstractLocalNode;
import com.oracle.graal.nodes.CallTargetNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.LoopEndNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.ReturnNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.java.MonitorEnterNode;
import com.oracle.graal.nodes.java.MonitorIdNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.common.query.nodes.GraalQueryNode;
import com.oracle.graal.phases.common.query.nodes.InstrumentationBeginNode;
import com.oracle.graal.phases.common.query.nodes.InstrumentationEndNode;
import com.oracle.graal.phases.common.query.nodes.InstrumentationNode;
import com.oracle.graal.phases.common.query.nodes.MonitorProxyNode;
import com.oracle.graal.phases.tiers.HighTierContext;

public class ExtractICGPhase extends BasePhase<HighTierContext> {

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        for (InstrumentationBeginNode icgBegin : graph.getNodes().filter(InstrumentationBeginNode.class)) {
            Instrumentation instrumentation = new Instrumentation(icgBegin);
            InstrumentationNode instrumentationNode = instrumentation.createInstrumentationNode();

            graph.addBeforeFixed(icgBegin, instrumentationNode);
            Debug.dump(instrumentationNode.icg(), "After extracted ICG at " + instrumentation);

            instrumentation.unlink();
        }

        for (InstrumentationNode instrumentationNode : graph.getNodes().filter(InstrumentationNode.class)) {
            for (GraalQueryNode query : instrumentationNode.icg().getNodes().filter(GraalQueryNode.class)) {
                query.onExtractICG(instrumentationNode);
            }
        }
    }

    static class Instrumentation {

        protected InstrumentationBeginNode icgBegin;
        protected InstrumentationEndNode icgEnd;
        protected ValueNode target;
        protected NodeBitMap icgNodes;

        public Instrumentation(InstrumentationBeginNode icgBegin) {
            this.icgBegin = icgBegin;

            resolveICGNodes();
            resolveTarget();
        }

        private static boolean shouldIncludeInput(Node node, NodeBitMap cfgNodes) {
            if (node instanceof FloatingNode && !(node instanceof AbstractLocalNode)) {
                NodePosIterator iterator = node.inputs().iterator();
                while (iterator.hasNext()) {
                    Position pos = iterator.nextPosition();
                    if (pos.getInputType() == InputType.Value) {
                        continue;
                    }
                    if (!cfgNodes.isMarked(pos.get(node))) {
                        return false;
                    }
                }
                return true;
            }
            if (node instanceof CallTargetNode || node instanceof MonitorIdNode || node instanceof FrameState) {
                return true;
            }
            return false;
        }

        private void resolveICGNodes() {
            NodeFlood icgCFG = icgBegin.graph().createNodeFlood();
            icgCFG.add(icgBegin.next());
            for (Node current : icgCFG) {
                if (current instanceof InstrumentationEndNode) {
                    icgEnd = (InstrumentationEndNode) current;
                } else if (current instanceof LoopEndNode) {
                    // do nothing
                } else if (current instanceof AbstractEndNode) {
                    icgCFG.add(((AbstractEndNode) current).merge());
                } else {
                    icgCFG.addAll(current.successors());
                }
            }

            NodeBitMap cfgNodes = icgCFG.getVisited();
            NodeFlood icgDFG = icgBegin.graph().createNodeFlood();
            icgDFG.addAll(cfgNodes);
            for (Node current : icgDFG) {
                if (current instanceof FrameState) {
                    continue;
                }
                for (Node input : current.inputs()) {
                    if (shouldIncludeInput(input, cfgNodes)) {
                        icgDFG.add(input);
                    }
                }
            }
            icgNodes = icgDFG.getVisited();
        }

        private void resolveTarget() {
            int offset = icgBegin.getOffset();
            if (offset < 0) {
                Node pred = icgBegin;
                while (offset < 0) {
                    pred = pred.predecessor();
                    if (pred == null || !(pred instanceof FixedNode)) {
                        target = null;
                        return;
                    }
                    offset++;
                }
                target = (FixedNode) pred;
            } else if (offset > 0) {
                FixedNode next = icgEnd;
                while (offset > 0) {
                    next = ((FixedWithNextNode) next).next();
                    if (next == null || !(next instanceof FixedWithNextNode)) {
                        target = null;
                        return;
                    }
                    offset--;
                }
                target = next;
            }
        }

        public InstrumentationNode createInstrumentationNode() {
            ValueNode newTarget = target;
            // MonitorEnterNode may be deleted during PEA
            if (newTarget instanceof MonitorEnterNode) {
                newTarget = new MonitorProxyNode(newTarget, ((MonitorEnterNode) newTarget).getMonitorId());
                icgBegin.graph().addWithoutUnique(newTarget);
            }
            InstrumentationNode instrumentationNode = new InstrumentationNode(newTarget, icgBegin.getOffset(), icgBegin.getType());
            icgBegin.graph().addWithoutUnique(instrumentationNode);
            // copy icg nodes to the InstrumentationNode instance
            StructuredGraph icg = instrumentationNode.icg();
            Map<Node, Node> replacements = Node.newMap();
            int index = 0;
            for (Node current : icgNodes) {
                if (current instanceof FrameState) {
                    continue;
                }
                for (Node input : current.inputs()) {
                    if (!(input instanceof ValueNode)) {
                        continue;
                    }
                    if (!icgNodes.isMarked(input) && !replacements.containsKey(input)) {
                        ParameterNode parameter = new ParameterNode(index++, ((ValueNode) input).stamp());
                        icg.addWithoutUnique(parameter);
                        replacements.put(input, parameter);
                        instrumentationNode.addInput(input);
                    }
                }
            }
            replacements = icg.addDuplicates(icgNodes, icgBegin.graph(), icgNodes.count(), replacements);
            icg.start().setNext((FixedNode) replacements.get(icgBegin.next()));
            replacements.get(icgEnd).replaceAtPredecessor(icg.addWithoutUnique(new ReturnNode(null)));

            new DeadCodeEliminationPhase().apply(icg, false);
            return instrumentationNode;
        }

        public void unlink() {
            FixedNode next = icgEnd.next();
            icgEnd.setNext(null);
            icgBegin.replaceAtPredecessor(next);
            GraphUtil.killCFG(icgBegin);
        }

    }

}
