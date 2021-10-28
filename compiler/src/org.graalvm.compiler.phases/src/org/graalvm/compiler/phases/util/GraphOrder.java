/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.util;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.GraalGraphError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.StructuredGraph.StageFlag;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.VirtualState.NodePositionClosure;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.phases.graph.ReentrantBlockIterator;
import org.graalvm.compiler.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;
import org.graalvm.compiler.phases.graph.StatelessPostOrderNodeIterator;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;

public final class GraphOrder {

    private GraphOrder() {
    }

    /**
     * Quick (and imprecise) assertion that there are no (invalid) cycles in the given graph. First,
     * an ordered list of all nodes in the graph (a total ordering) is created. A second run over
     * this list checks whether inputs are scheduled before their usages.
     *
     * @param graph the graph to be checked.
     * @throws AssertionError if a cycle was detected.
     */
    public static boolean assertNonCyclicGraph(StructuredGraph graph) {
        List<Node> order = createOrder(graph);
        NodeBitMap visited = graph.createNodeBitMap();
        visited.clearAll();
        for (Node node : order) {
            if (node instanceof PhiNode && ((PhiNode) node).merge() instanceof LoopBeginNode) {
                assert visited.isMarked(((PhiNode) node).valueAt(0));
                // nothing to do
            } else {
                for (Node input : node.inputs()) {
                    if (!visited.isMarked(input)) {
                        if (input instanceof FrameState) {
                            // nothing to do - frame states are known, allowed cycles
                        } else {
                            assert false : "unexpected cycle detected at input " + node + " -> " + input;
                        }
                    }
                }
            }
            visited.mark(node);
        }
        return true;
    }

    private static List<Node> createOrder(StructuredGraph graph) {
        final ArrayList<Node> nodes = new ArrayList<>();
        final NodeBitMap visited = graph.createNodeBitMap();

        new StatelessPostOrderNodeIterator(graph.start()) {
            @Override
            protected void node(FixedNode node) {
                visitForward(nodes, visited, node, false);
            }
        }.apply();
        return nodes;
    }

    private static void visitForward(ArrayList<Node> nodes, NodeBitMap visited, Node node, boolean floatingOnly) {
        try {
            assert node == null || node.isAlive() : node + " not alive";
            if (node != null && !visited.isMarked(node)) {
                if (floatingOnly && node instanceof FixedNode) {
                    throw new GraalError("unexpected reference to fixed node: %s (this indicates an unexpected cycle)", node);
                }
                visited.mark(node);
                FrameState stateAfter = null;
                if (node instanceof StateSplit) {
                    stateAfter = ((StateSplit) node).stateAfter();
                }
                for (Node input : node.inputs()) {
                    if (input != stateAfter) {
                        visitForward(nodes, visited, input, true);
                    }
                }
                if (node instanceof EndNode) {
                    EndNode end = (EndNode) node;
                    for (PhiNode phi : end.merge().phis()) {
                        visitForward(nodes, visited, phi.valueAt(end), true);
                    }
                }
                nodes.add(node);
                if (node instanceof AbstractMergeNode) {
                    for (PhiNode phi : ((AbstractMergeNode) node).phis()) {
                        visited.mark(phi);
                        nodes.add(phi);
                    }
                }
                if (stateAfter != null) {
                    visitForward(nodes, visited, stateAfter, true);
                }
            }
        } catch (GraalError e) {
            throw GraalGraphError.transformAndAddContext(e, node);
        }
    }

    /**
     * This method schedules the graph and makes sure that, for every node, all inputs are available
     * at the position where it is scheduled. This is a very expensive assertion.
     */
    @SuppressWarnings("try")
    public static boolean assertSchedulableGraph(final StructuredGraph graph) {
        assert graph.getGuardsStage() != GuardsStage.AFTER_FSA : "Cannot use the BlockIteratorClosure after FrameState Assignment, HIR Loop Data Structures are no longer valid.";
        try (DebugContext.Scope s = graph.getDebug().scope("AssertSchedulableGraph")) {
            SchedulePhase.runWithoutContextOptimizations(graph, getSchedulingPolicy(graph), true);
            final EconomicMap<LoopBeginNode, NodeBitMap> loopEntryStates = EconomicMap.create(Equivalence.IDENTITY);
            final ScheduleResult schedule = graph.getLastSchedule();

            BlockIteratorClosure<NodeBitMap> closure = new BlockIteratorClosure<NodeBitMap>() {

                @Override
                protected List<NodeBitMap> processLoop(Loop<Block> loop, NodeBitMap initialState) {
                    return ReentrantBlockIterator.processLoop(this, loop, initialState).exitStates;
                }

                @Override
                protected NodeBitMap processBlock(final Block block, final NodeBitMap currentState) {
                    final List<Node> list = graph.getLastSchedule().getBlockToNodesMap().get(block);

                    /*
                     * A stateAfter is not valid directly after its associated state split, but
                     * right before the next fixed node. Therefore a pending stateAfter is kept that
                     * will be checked at the correct position.
                     */
                    FrameState pendingStateAfter = null;
                    for (final Node node : list) {
                        if (node instanceof ValueNode) {
                            FrameState stateAfter = node instanceof StateSplit ? ((StateSplit) node).stateAfter() : null;
                            if (node instanceof FullInfopointNode) {
                                stateAfter = ((FullInfopointNode) node).getState();
                            }

                            if (pendingStateAfter != null && node instanceof FixedNode) {

                                pendingStateAfter.applyToNonVirtual(new NodePositionClosure<Node>() {

                                    @Override
                                    public void apply(Node from, Position p) {
                                        Node usage = from;
                                        Node nonVirtualNode = p.get(from);
                                        assert currentState.isMarked(nonVirtualNode) || nonVirtualNode instanceof VirtualObjectNode || nonVirtualNode instanceof ConstantNode : nonVirtualNode +
                                                        " not available at virtualstate " + usage + " before " + node + " in block " + block + " \n" + list;

                                    }
                                });
                                pendingStateAfter = null;
                            }

                            if (node instanceof AbstractMergeNode) {
                                // phis aren't scheduled, so they need to be added explicitly
                                currentState.markAll(((AbstractMergeNode) node).phis());
                                if (node instanceof LoopBeginNode) {
                                    // remember the state at the loop entry, it's restored at exits
                                    loopEntryStates.put((LoopBeginNode) node, currentState.copy());
                                }
                            } else if (node instanceof ProxyNode) {
                                assert false : "proxy nodes should not be in the schedule";
                            } else if (node instanceof LoopExitNode) {
                                if (graph.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL)) {
                                    for (ProxyNode proxy : ((LoopExitNode) node).proxies()) {
                                        for (Node input : proxy.inputs()) {
                                            if (input != proxy.proxyPoint()) {
                                                assert currentState.isMarked(input) : input + " not available at " + proxy + " in block " + block + "\n" + list;
                                            }
                                        }
                                    }
                                    // loop contents are only accessible via proxies at the exit
                                    currentState.clearAll();
                                    currentState.markAll(loopEntryStates.get(((LoopExitNode) node).loopBegin()));
                                }

                                // Loop proxies aren't scheduled, so they need to be added
                                // explicitly
                                currentState.markAll(((LoopExitNode) node).proxies());
                            } else {
                                for (Node input : node.inputs()) {
                                    if (input != stateAfter) {
                                        if (input instanceof FrameState) {
                                            ((FrameState) input).applyToNonVirtual(new NodePositionClosure<Node>() {

                                                @Override
                                                public void apply(Node from, Position p) {
                                                    Node nonVirtual = p.get(from);
                                                    assert currentState.isMarked(nonVirtual) : nonVirtual + " not available at " + node + " in block " + block + "\n" + list;
                                                }
                                            });
                                        } else {
                                            assert currentState.isMarked(input) || input instanceof VirtualObjectNode || input instanceof ConstantNode : input + " not available at " + node +
                                                            " in block " + block + "\n" + list;
                                        }
                                    }
                                }
                            }
                            if (node instanceof AbstractEndNode) {
                                AbstractMergeNode merge = ((AbstractEndNode) node).merge();
                                for (PhiNode phi : merge.phis()) {
                                    ValueNode phiValue = phi.valueAt((AbstractEndNode) node);
                                    assert phiValue == null || currentState.isMarked(phiValue) || phiValue instanceof ConstantNode : phiValue + " not available at phi " + phi + " / end " + node +
                                                    " in block " + block;
                                }
                            }
                            if (stateAfter != null) {
                                assert pendingStateAfter == null;
                                pendingStateAfter = stateAfter;
                            }
                            currentState.mark(node);
                        }
                    }
                    if (pendingStateAfter != null) {
                        pendingStateAfter.applyToNonVirtual(new NodePositionClosure<Node>() {
                            @Override
                            public void apply(Node from, Position p) {
                                Node usage = from;
                                Node nonVirtualNode = p.get(from);
                                assert currentState.isMarked(nonVirtualNode) || nonVirtualNode instanceof VirtualObjectNode || nonVirtualNode instanceof ConstantNode : nonVirtualNode +
                                                " not available at virtualstate " + usage + " at end of block " + block + " \n" + list;
                            }
                        });
                    }
                    return currentState;
                }

                @Override
                protected NodeBitMap merge(Block merge, List<NodeBitMap> states) {
                    NodeBitMap result = states.get(0);
                    for (int i = 1; i < states.size(); i++) {
                        result.intersect(states.get(i));
                    }
                    return result;
                }

                @Override
                protected NodeBitMap getInitialState() {
                    NodeBitMap ret = graph.createNodeBitMap();
                    ret.markAll(graph.getNodes().filter(ConstantNode.class));
                    return ret;
                }

                @Override
                protected NodeBitMap cloneState(NodeBitMap oldState) {
                    return oldState.copy();
                }
            };

            ReentrantBlockIterator.apply(closure, schedule.getCFG().getStartBlock());

        } catch (Throwable t) {
            graph.getDebug().handle(t);
        }
        return true;
    }

    /*
     * Complexity of verification for LATEST_OUT_OF_LOOPS with value proxies exceeds the benefits.
     * The problem are floating values that can be scheduled before the loop and have proxies only
     * on some use edges after the loop. These values, which are hard to detect, get scheduled
     * before the loop exit and are not visible in the state after the loop exit.
     */
    private static SchedulingStrategy getSchedulingPolicy(StructuredGraph graph) {
        return graph.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL) ? SchedulingStrategy.EARLIEST : SchedulingStrategy.LATEST_OUT_OF_LOOPS;
    }
}
