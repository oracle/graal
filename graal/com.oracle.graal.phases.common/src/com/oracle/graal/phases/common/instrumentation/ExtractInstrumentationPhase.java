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
package com.oracle.graal.phases.common.instrumentation;

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
import com.oracle.graal.phases.common.instrumentation.nodes.InstrumentationBeginNode;
import com.oracle.graal.phases.common.instrumentation.nodes.InstrumentationContentNode;
import com.oracle.graal.phases.common.instrumentation.nodes.InstrumentationEndNode;
import com.oracle.graal.phases.common.instrumentation.nodes.InstrumentationNode;
import com.oracle.graal.phases.common.instrumentation.nodes.MonitorProxyNode;
import com.oracle.graal.phases.tiers.HighTierContext;

import jdk.vm.ci.common.JVMCIError;

public class ExtractInstrumentationPhase extends BasePhase<HighTierContext> {

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        for (InstrumentationBeginNode begin : graph.getNodes().filter(InstrumentationBeginNode.class)) {
            Instrumentation instrumentation = new Instrumentation(begin);
            InstrumentationNode instrumentationNode = instrumentation.createInstrumentationNode();

            graph.addBeforeFixed(begin, instrumentationNode);
            Debug.dump(instrumentationNode.instrumentationGraph(), "After extracted instrumentation at " + instrumentation);

            instrumentation.unlink();
        }

        for (InstrumentationNode instrumentationNode : graph.getNodes().filter(InstrumentationNode.class)) {
            for (InstrumentationContentNode query : instrumentationNode.instrumentationGraph().getNodes().filter(InstrumentationContentNode.class)) {
                query.onExtractInstrumentation(instrumentationNode);
            }
        }
    }

    static class Instrumentation {

        protected InstrumentationBeginNode begin;
        protected InstrumentationEndNode end;
        protected ValueNode target;
        protected NodeBitMap nodes;

        public Instrumentation(InstrumentationBeginNode begin) {
            this.begin = begin;

            resolveInstrumentationNodes();
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

        private void resolveInstrumentationNodes() {
            NodeFlood cfgFlood = begin.graph().createNodeFlood();
            cfgFlood.add(begin.next());
            for (Node current : cfgFlood) {
                if (current instanceof InstrumentationEndNode) {
                    end = (InstrumentationEndNode) current;
                } else if (current instanceof LoopEndNode) {
                    // do nothing
                } else if (current instanceof AbstractEndNode) {
                    cfgFlood.add(((AbstractEndNode) current).merge());
                } else {
                    cfgFlood.addAll(current.successors());
                }
            }

            NodeBitMap cfgNodes = cfgFlood.getVisited();
            NodeFlood dfgFlood = begin.graph().createNodeFlood();
            dfgFlood.addAll(cfgNodes);
            for (Node current : dfgFlood) {
                if (current instanceof FrameState) {
                    continue;
                }
                for (Node input : current.inputs()) {
                    if (shouldIncludeInput(input, cfgNodes)) {
                        dfgFlood.add(input);
                    }
                }
            }
            nodes = dfgFlood.getVisited();

            if (end == null) {
                // this may be caused by DeoptimizationReason.Unresolved
                throw JVMCIError.shouldNotReachHere("could not find invocation to instrumentationEnd()");
            }
        }

        private void resolveTarget() {
            int offset = begin.getOffset();
            if (offset < 0) {
                Node pred = begin;
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
                FixedNode next = end;
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
                begin.graph().addWithoutUnique(newTarget);
            }
            InstrumentationNode instrumentationNode = new InstrumentationNode(newTarget, begin.getOffset());
            begin.graph().addWithoutUnique(instrumentationNode);
            // copy instrumentation nodes to the InstrumentationNode instance
            StructuredGraph instrumentationGraph = instrumentationNode.instrumentationGraph();
            Map<Node, Node> replacements = Node.newMap();
            int index = 0;
            for (Node current : nodes) {
                if (current instanceof FrameState) {
                    continue;
                }
                for (Node input : current.inputs()) {
                    if (!(input instanceof ValueNode)) {
                        continue;
                    }
                    if (!nodes.isMarked(input) && !replacements.containsKey(input)) {
                        ParameterNode parameter = new ParameterNode(index++, ((ValueNode) input).stamp());
                        instrumentationGraph.addWithoutUnique(parameter);
                        replacements.put(input, parameter);
                        instrumentationNode.addInput(input);
                    }
                }
            }
            replacements = instrumentationGraph.addDuplicates(nodes, begin.graph(), nodes.count(), replacements);
            instrumentationGraph.start().setNext((FixedNode) replacements.get(begin.next()));
            replacements.get(end).replaceAtPredecessor(instrumentationGraph.addWithoutUnique(new ReturnNode(null)));

            new DeadCodeEliminationPhase().apply(instrumentationGraph, false);
            return instrumentationNode;
        }

        public void unlink() {
            FixedNode next = end.next();
            end.setNext(null);
            begin.replaceAtPredecessor(next);
            GraphUtil.killCFG(begin);
        }

    }

}
