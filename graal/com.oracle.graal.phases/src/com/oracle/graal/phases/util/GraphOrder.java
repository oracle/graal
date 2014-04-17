/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.util;

import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.VirtualState.NodeClosure;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.schedule.SchedulePhase.MemoryScheduling;
import com.oracle.graal.phases.schedule.SchedulePhase.SchedulingStrategy;

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
                    throw new GraalInternalError("unexpected reference to fixed node: %s (this indicates an unexpected cycle)", node);
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
                if (node instanceof MergeNode) {
                    for (PhiNode phi : ((MergeNode) node).phis()) {
                        visited.mark(phi);
                        nodes.add(phi);
                    }
                }
                if (stateAfter != null) {
                    visitForward(nodes, visited, stateAfter, true);
                }
            }
        } catch (GraalInternalError e) {
            throw GraalGraphInternalError.transformAndAddContext(e, node);
        }
    }

    /**
     * This method schedules the graph and makes sure that, for every node, all inputs are available
     * at the position where it is scheduled. This is a very expensive assertion.
     *
     * Also, this phase assumes ProxyNodes to exist at LoopExitNodes, so that it cannot be run after
     * phases that remove loop proxies or move proxies to BeginNodes.
     */
    public static boolean assertSchedulableGraph(final StructuredGraph graph) {
        try {
            final SchedulePhase schedule = new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS, MemoryScheduling.NONE);
            final IdentityHashMap<LoopBeginNode, NodeBitMap> loopEntryStates = new IdentityHashMap<>();
            schedule.apply(graph, false);

            BlockIteratorClosure<NodeBitMap> closure = new BlockIteratorClosure<NodeBitMap>() {

                @Override
                protected List<NodeBitMap> processLoop(Loop<Block> loop, NodeBitMap initialState) {
                    return ReentrantBlockIterator.processLoop(this, loop, initialState).exitStates;
                }

                @Override
                protected NodeBitMap processBlock(final Block block, final NodeBitMap currentState) {
                    final List<ScheduledNode> list = schedule.getBlockToNodesMap().get(block);

                    /*
                     * A stateAfter is not valid directly after its associated state split, but
                     * right before the next fixed node. Therefore a pending stateAfter is kept that
                     * will be checked at the correct position.
                     */
                    FrameState pendingStateAfter = null;
                    for (final ScheduledNode node : list) {
                        FrameState stateAfter = node instanceof StateSplit ? ((StateSplit) node).stateAfter() : null;
                        if (node instanceof InfopointNode) {
                            stateAfter = ((InfopointNode) node).getState();
                        }

                        if (pendingStateAfter != null && node instanceof FixedNode) {
                            pendingStateAfter.applyToNonVirtual(new NodeClosure<Node>() {
                                public void apply(Node usage, Node nonVirtualNode) {
                                    assert currentState.isMarked(nonVirtualNode) : nonVirtualNode + " not available at virtualstate " + usage + " before " + node + " in block " + block + " \n" + list;
                                }
                            });
                            pendingStateAfter = null;
                        }

                        if (node instanceof MergeNode) {
                            // phis aren't scheduled, so they need to be added explicitly
                            currentState.markAll(((MergeNode) node).phis());
                            if (node instanceof LoopBeginNode) {
                                // remember the state at the loop entry, it's restored at exits
                                loopEntryStates.put((LoopBeginNode) node, currentState.copy());
                            }
                        } else if (node instanceof ProxyNode) {
                            for (Node input : node.inputs()) {
                                if (input != ((ProxyNode) node).proxyPoint()) {
                                    assert currentState.isMarked(input) : input + " not available at " + node + " in block " + block + "\n" + list;
                                }
                            }
                        } else if (node instanceof LoopExitNode) {
                            if (!graph.isAfterFloatingReadPhase()) {
                                // loop contents are only accessible via proxies at the exit
                                currentState.clearAll();
                                currentState.markAll(loopEntryStates.get(((LoopExitNode) node).loopBegin()));
                            }
                            // Loop proxies aren't scheduled, so they need to be added explicitly
                            currentState.markAll(((LoopExitNode) node).proxies());
                        } else {
                            for (Node input : node.inputs()) {
                                if (input != stateAfter) {
                                    if (input instanceof FrameState) {
                                        ((FrameState) input).applyToNonVirtual((usage, nonVirtual) -> {
                                            assert currentState.isMarked(nonVirtual) : nonVirtual + " not available at " + node + " in block " + block + "\n" + list;
                                        });
                                    } else {
                                        assert currentState.isMarked(input) : input + " not available at " + node + " in block " + block + "\n" + list;
                                    }
                                }
                            }
                        }
                        if (node instanceof AbstractEndNode) {
                            MergeNode merge = ((AbstractEndNode) node).merge();
                            for (PhiNode phi : merge.phis()) {
                                assert currentState.isMarked(phi.valueAt((AbstractEndNode) node)) : phi.valueAt((AbstractEndNode) node) + " not available at phi " + phi + " / end " + node +
                                                " in block " + block;
                            }
                        }
                        if (stateAfter != null) {
                            assert pendingStateAfter == null;
                            pendingStateAfter = stateAfter;
                        }
                        currentState.mark(node);
                    }
                    if (pendingStateAfter != null) {
                        pendingStateAfter.applyToNonVirtual(new NodeClosure<Node>() {
                            public void apply(Node usage, Node nonVirtualNode) {
                                assert currentState.isMarked(nonVirtualNode) : nonVirtualNode + " not available at virtualstate " + usage + " at end of block " + block + " \n" + list;
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
                    return graph.createNodeBitMap();
                }

                @Override
                protected NodeBitMap cloneState(NodeBitMap oldState) {
                    return oldState.copy();
                }
            };

            ReentrantBlockIterator.apply(closure, schedule.getCFG().getStartBlock());

        } catch (Throwable t) {
            throw new AssertionError("unschedulable graph", t);
        }
        return true;
    }
}
