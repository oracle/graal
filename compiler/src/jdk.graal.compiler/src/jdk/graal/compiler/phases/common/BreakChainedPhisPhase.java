/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import java.util.List;
import java.util.Optional;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.ChainedPhiAdapterNode;
import jdk.graal.compiler.nodes.ChainedPhiValueSplitNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;

/**
 * Chained Phi values, i.e. a Phi which has an input to a sibling Phi through a back-edge, can make
 * it hard or even impossible for the register allocator to do a good job. For example:
 *
 * <pre>
 * loop:
 *   v1 = Phi1(x, v2)
 *   v4 = Phi2(y, v1)
 *   ... use v1, v4 ...
 *   v2 = v1 + 1
 *   jmp loop
 * </pre>
 *
 * Phi2 will keep v1 alive until the loop end. Therefore, v2 cannot be assigned to the location of
 * v1. Let's assume v1, v4 are in reg1, reg4:
 *
 * <pre>
 *   reg1(v1) = x
 *   reg4(v4) = y
 * loop:
 *   ... use v1, v4 ...
 *   tmp(v2) = reg1(v1) + 1
 *   reg4(v4) = reg1(v1)
 *   reg1(v1) = tmp(v2)
 *   jmp loop
 * </pre>
 *
 * In the worst case, tmp is reg4, and we need to introduce a cycle breaking spill move.
 *
 * This phase tackles the issue by splitting chained phi values at HIR. That is, in our example,
 * split the value v1 so the usage in Phi2 gets a new value:
 *
 * <pre>
 * loop:
 *   v1 = Phi1(x, v2)
 *   v4 = Phi2(y, v3) // use copy of v1
 *   ... use v1, v4 ...
 *   v3 = v1          // new copy of v1
 *   v2 = v1 + 1      // use old instance of the value, i.e. v1
 *   jmp loop
 * </pre>
 *
 * Now v2 can follow the hint on v1, so no resolution move there. In addition, v3 gets a hint on v4
 * which it can also fulfill. Again, Let's assume v1, v4 are in reg1, reg4:
 *
 * <pre>
 *   reg1 (v1) = x
 *   reg4 (v4) = y
 * loop:
 *   ... use v1, v4 ...
 *   reg4(v3) = reg1 (v1)      // v3 hint on v4
 *   reg1(v2) = reg1 (v1) + 1  // v2 hint on v1
 *   jmp loop
 * </pre>
 */
public class BreakChainedPhisPhase extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    public void run(StructuredGraph graph) {
        if (graph.getLastSchedule() == null || !graph.isLastScheduleValid()) {
            runSchedulePhase(graph);
        }
        StructuredGraph.ScheduleResult result = graph.getLastSchedule();
        NodeMap<HIRBlock> nodeToBlockMap = result.getNodeToBlockMap();
        BlockMap<List<Node>> blockToNodesMap = result.getBlockToNodesMap();

        for (LoopBeginNode loopBeginNode : graph.getNodes(LoopBeginNode.TYPE)) {
            for (ValuePhiNode phi : loopBeginNode.valuePhis()) {
                for (int i = 1; i < phi.valueCount(); i++) {
                    AbstractEndNode endNode = loopBeginNode.phiPredecessorAt(i);
                    ValueNode value = phi.valueAt(i);
                    if (value == phi || !(value instanceof ValuePhiNode) || !(endNode instanceof LoopEndNode) || nodeToBlockMap.isNew(endNode)) {
                        continue;
                    }
                    ValuePhiNode inputPhi = (ValuePhiNode) value;
                    if (inputPhi.merge() != loopBeginNode) {
                        continue;
                    }
                    // Looks like we have a chained phi here.
                    HIRBlock block = nodeToBlockMap.get(endNode);
                    if (block == null) {
                        // Skip unscheduled loop ends.
                        continue;
                    }
                    final List<Node> nodes = blockToNodesMap.get(block);
                    if (mightKillAllRegisters(nodes) || !hasUsageIn(inputPhi, nodes)) {
                        continue;
                    }
                    ChainedPhiValueSplitNode proxy = graph.addWithoutUnique(new ChainedPhiValueSplitNode(inputPhi));
                    proxy.setNodeSourcePosition(inputPhi.getNodeSourcePosition());
                    inputPhi.replaceAtUsages(proxy, new NodePredicate() {
                        @Override
                        public boolean apply(Node n) {
                            return nodes.contains(n);
                        }
                    });
                    ChainedPhiAdapterNode adapter = graph.addWithoutUnique(new ChainedPhiAdapterNode(proxy));
                    adapter.setNodeSourcePosition(proxy.getNodeSourcePosition());
                    phi.setValueAt(i, adapter);
                    graph.getOptimizationLog().report(BreakChainedPhisPhase.class, "CircularPhiBreakerInsertion", inputPhi);
                }
            }
        }
    }

    private static boolean mightKillAllRegisters(List<Node> nodes) {
        for (Node node : nodes) {
            if (node instanceof ForeignCall || node instanceof Invoke || node instanceof BinaryMathIntrinsicNode ||
                            node instanceof UnaryMathIntrinsicNode) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUsageIn(Node nodes, List<Node> potentialUsages) {
        for (Node usage : nodes.usages().filter(ValueNode.class)) {
            if (potentialUsages.contains(usage)) {
                return true;
            }
        }
        return false;
    }

    private static void runSchedulePhase(StructuredGraph graph) {
        SchedulePhase.runWithoutContextOptimizations(graph, SchedulingStrategy.LATEST_OUT_OF_LOOPS_IMPLICIT_NULL_CHECKS);
    }

}
