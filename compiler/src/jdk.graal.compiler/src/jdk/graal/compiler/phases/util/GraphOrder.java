/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.util;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.GraalGraphError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.GraphState.GuardsStage;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.VirtualState.NodePositionClosure;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.graph.ReentrantBlockIterator;
import jdk.graal.compiler.phases.graph.StatelessPostOrderNodeIterator;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;

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

    public static boolean assertSchedulableGraph(StructuredGraph g) {
        assert GraphOrder.assertNonCyclicGraph(g);
        assert g.getGuardsStage() == GuardsStage.AFTER_FSA || GraphOrder.assertScheduleableBeforeFSA(g);
        if (g.getGuardsStage() == GuardsStage.AFTER_FSA && Assertions.detailedAssertionsEnabled(g.getOptions())) {
            // we still want to do a memory verification of the schedule even if we can
            // no longer use assertSchedulableGraph after the floating reads phase
            SchedulePhase.runWithoutContextOptimizations(g, SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS, true);
        }
        return true;
    }

    /**
     * Maximum number of graph searches to detect dead nodes: this is a heuristic to keep
     * compilation time reasonable.
     */
    private static final int MAX_DEAD_NODE_SEARCHES = 8;

    /**
     * This method schedules the graph and makes sure that, for every node, all inputs are available
     * at the position where it is scheduled. This is a very expensive assertion.
     */
    @SuppressWarnings("try")
    private static boolean assertScheduleableBeforeFSA(final StructuredGraph graph) {
        assert graph.getGuardsStage() != GuardsStage.AFTER_FSA : "Cannot use the BlockIteratorClosure after FrameState Assignment, HIR Loop Data Structures are no longer valid.";

        try (DebugContext.Scope s = graph.getDebug().scope("AssertSchedulableGraph")) {
            SchedulePhase.runWithoutContextOptimizations(graph, getSchedulingPolicy(graph), true);
            final EconomicMap<LoopBeginNode, NodeBitMap> loopEntryStates = EconomicMap.create(Equivalence.IDENTITY);
            final ScheduleResult schedule = graph.getLastSchedule();

            final NodeBitMap deadNodes = computeDeadFloatingNodes(graph);

            ReentrantBlockIterator.BlockIteratorClosure<NodeBitMap> closure = new ReentrantBlockIterator.BlockIteratorClosure<>() {

                @Override
                protected NodeBitMap processBlock(final HIRBlock block, final NodeBitMap currentState) {
                    final List<Node> list = graph.getLastSchedule().getBlockToNodesMap().get(block);

                    AbstractBeginNode blockBegin = block.getBeginNode();
                    if (blockBegin instanceof AbstractMergeNode merge) {
                        /**
                         * Phis aren't scheduled, so they need to be added explicitly. We must do
                         * this first because there might be floating nodes depending on the phis
                         * that have floated to the beginning of the block.
                         * <p/>
                         *
                         * That is, we might have a graph like this:
                         *
                         * <pre>
                         *      ...   ...  (inputs from predecessor blocks)
                         *        \   /
                         *  +----- Phi
                         *  |       |
                         *  |      Pi (or any other floating node)
                         *  |       |
                         *  |   FrameState
                         *  |       |
                         *  +---> Merge
                         * </pre>
                         *
                         * In the schedule the order of these nodes can be:
                         *
                         * <pre>
                         *     Pi
                         *     FrameState
                         *     Merge
                         *     ...
                         * </pre>
                         *
                         * This may seem unnatural, but due to the cyclic dependency on the state,
                         * any other order would be unnatural as well. For the use case of checking
                         * the schedule, pretend that all phis in the block precede everything else.
                         */
                        currentState.markAll(merge.phis());
                        if (merge instanceof LoopBeginNode loopBegin) {
                            // remember the state at the loop entry, it's restored at exits
                            loopEntryStates.put(loopBegin, currentState.copy());
                        }
                    }

                    /*
                     * A stateAfter is not valid directly after its associated state split, but
                     * right before the next fixed node. Therefore a pending stateAfter is kept that
                     * will be checked at the correct position.
                     */
                    FrameState pendingStateAfter = null;
                    for (final Node node : list) {
                        if (deadNodes.isMarked(node)) {
                            continue;
                        }
                        if (node instanceof ValueNode) {
                            FrameState stateAfter = node instanceof StateSplit ? ((StateSplit) node).stateAfter() : null;
                            if (node instanceof FullInfopointNode) {
                                stateAfter = ((FullInfopointNode) node).getState();
                            }

                            if (pendingStateAfter != null && node instanceof FixedNode) {

                                pendingStateAfter.applyToNonVirtual(new NodePositionClosure<>() {

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
                                GraalError.guarantee(node == blockBegin, "block should contain only one merge, found %s, expected %s", node, blockBegin);
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
                                            ((FrameState) input).applyToNonVirtual(new NodePositionClosure<>() {

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
                                    if (!deadNodes.isMarked(phi)) {
                                        ValueNode phiValue = phi.valueAt((AbstractEndNode) node);
                                        assert phiValue == null || currentState.isMarked(phiValue) || phiValue instanceof ConstantNode : phiValue + " not available at phi " + phi + " / end " + node +
                                                        " in block " + block;
                                    }
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
                        pendingStateAfter.applyToNonVirtual(new NodePositionClosure<>() {
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
                protected NodeBitMap merge(HIRBlock merge, List<NodeBitMap> states) {
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

    /**
     * We run verification of the graph with schedule.immutableGraph=true because verification
     * should not have any impact on the graph it verifies (we want verification to be side effect
     * free).
     *
     * There are certain sets of dead nodes that are normally only deleted by the schedule or the
     * canonicalizer - dead phi cycles (and floating nodes that are kept alive by such dead cycles).
     */
    private static NodeBitMap computeDeadFloatingNodes(final StructuredGraph graph) {
        NodeBitMap deadNodes = graph.createNodeBitMap();
        for (PhiNode phi : graph.getNodes().filter(PhiNode.class)) {
            if (!phi.isLoopPhi()) {
                continue;
            }
            NodeFlood nf = graph.createNodeFlood();
            if (CanonicalizerPhase.isDeadLoopPhiCycle(phi, nf)) {
                for (Node visitedNode : nf.getVisited()) {
                    deadNodes.mark(visitedNode);
                }
            }
        }

        // now we collected all dead loop phi nodes, collect all floating nodes whose usages
        // are only in the dead set (transitive)
        int computes = 0;
        boolean change = true;
        NodeBitMap toProcess = graph.createNodeBitMap();
        while (change && (computes++ <= MAX_DEAD_NODE_SEARCHES)) {
            toProcess.clearAll();
            change = false;

            for (Node dead : deadNodes) {
                for (Node input : dead.inputs()) {
                    if (!deadNodes.contains(input)) {
                        toProcess.mark(input);
                    }
                }
            }

            inner: for (Node n : toProcess) {
                if (GraphUtil.isFloatingNode(n) && !isNeverDeadFloatingNode(n)) {
                    if (deadNodes.isMarked(n)) {
                        continue inner;
                    }
                    for (Node usage : n.usages()) {
                        if (!deadNodes.isMarked(usage)) {
                            continue inner;
                        }
                    }
                    deadNodes.mark(n);
                    change = true;
                }
            }
        }
        return deadNodes;
    }

    private static boolean isNeverDeadFloatingNode(Node n) {
        return n instanceof GuardNode || n instanceof ProxyNode || n instanceof VirtualState;
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
