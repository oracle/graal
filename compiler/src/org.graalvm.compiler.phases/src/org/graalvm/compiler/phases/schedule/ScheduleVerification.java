/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.schedule;

import java.util.ArrayDeque;
import java.util.List;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MemoryProxyNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.VirtualState;
import org.graalvm.compiler.nodes.StructuredGraph.StageFlag;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.HIRLoop;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.memory.MemoryPhiNode;
import org.graalvm.compiler.nodes.memory.MultiMemoryKill;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.phases.graph.ReentrantBlockIterator;
import org.graalvm.compiler.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;
import org.graalvm.word.LocationIdentity;

/**
 * Verifies that the schedule of the graph is correct. Checks that floating reads are not killed
 * between definition and usage. Also checks that there are no usages spanning loop exits without a
 * proper proxy node.
 */
public final class ScheduleVerification extends BlockIteratorClosure<EconomicSet<FloatingReadNode>> {

    private final BlockMap<List<Node>> blockToNodesMap;
    private final NodeMap<Block> nodeMap;
    private final StructuredGraph graph;

    public static boolean check(Block startBlock, BlockMap<List<Node>> blockToNodesMap, NodeMap<Block> nodeMap) {
        ReentrantBlockIterator.apply(new ScheduleVerification(blockToNodesMap, nodeMap, startBlock.getBeginNode().graph()), startBlock);
        return true;
    }

    private ScheduleVerification(BlockMap<List<Node>> blockToNodesMap, NodeMap<Block> nodeMap, StructuredGraph graph) {
        this.blockToNodesMap = blockToNodesMap;
        this.nodeMap = nodeMap;
        this.graph = graph;
    }

    @Override
    protected EconomicSet<FloatingReadNode> getInitialState() {
        return EconomicSet.create(Equivalence.IDENTITY);
    }

    @Override
    protected EconomicSet<FloatingReadNode> processBlock(Block block, EconomicSet<FloatingReadNode> currentState) {
        AbstractBeginNode beginNode = block.getBeginNode();
        if (beginNode instanceof AbstractMergeNode) {
            AbstractMergeNode abstractMergeNode = (AbstractMergeNode) beginNode;
            for (PhiNode phi : abstractMergeNode.phis()) {
                if (phi instanceof MemoryPhiNode) {
                    MemoryPhiNode memoryPhiNode = (MemoryPhiNode) phi;
                    addFloatingReadUsages(currentState, memoryPhiNode);
                }
            }
        }
        if (beginNode instanceof LoopExitNode) {
            LoopExitNode loopExitNode = (LoopExitNode) beginNode;
            for (ProxyNode proxy : loopExitNode.proxies()) {
                if (proxy instanceof MemoryProxyNode) {
                    MemoryProxyNode memoryProxyNode = (MemoryProxyNode) proxy;
                    addFloatingReadUsages(currentState, memoryProxyNode);
                }
            }
        }
        for (Node n : blockToNodesMap.get(block)) {
            if (n instanceof MemoryKill) {
                if (n instanceof SingleMemoryKill) {
                    SingleMemoryKill single = (SingleMemoryKill) n;
                    processLocation(n, single.getKilledLocationIdentity(), currentState);
                } else if (n instanceof MultiMemoryKill) {
                    MultiMemoryKill multi = (MultiMemoryKill) n;
                    for (LocationIdentity location : multi.getKilledLocationIdentities()) {
                        processLocation(n, location, currentState);
                    }
                }

                addFloatingReadUsages(currentState, n);
            } else if (n instanceof MemoryAccess) {
                addFloatingReadUsages(currentState, n);
            } else if (n instanceof FloatingReadNode) {
                FloatingReadNode floatingReadNode = (FloatingReadNode) n;
                if (floatingReadNode.getLastLocationAccess() != null && floatingReadNode.getLocationIdentity().isMutable()) {
                    if (currentState.contains(floatingReadNode)) {
                        // Floating read was found in the state.
                        currentState.remove(floatingReadNode);
                    } else {
                        throw new RuntimeException("Floating read node " + n + " was not found in the state, i.e., it was killed by a memory check point before its place in the schedule. Block=" +
                                        block + ", block begin: " + block.getBeginNode() + " block loop: " + block.getLoop() + ", " + blockToNodesMap.get(block).get(0));
                    }
                }
            }
            assert nodeMap.get(n) == block;
            if (graph.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL) && block.getLoop() != null && !(n instanceof VirtualState)) {
                for (Node usage : n.usages()) {
                    Node usageNode = usage;

                    if (usageNode instanceof PhiNode) {
                        PhiNode phiNode = (PhiNode) usage;
                        usageNode = phiNode.merge();
                    }

                    if (usageNode instanceof LoopExitNode) {
                        LoopExitNode loopExitNode = (LoopExitNode) usageNode;
                        if (loopExitNode.loopBegin() == n || loopExitNode.stateAfter() == n) {
                            continue;
                        }
                    }
                    Block usageBlock = nodeMap.get(usageNode);

                    if (usageBlock == null) {
                        if (usage instanceof FloatingNode || usage instanceof VirtualState || usage instanceof CallTargetNode) {
                            if (!(usage instanceof GuardNode)) {
                                /*
                                 * We do not want to run the schedule behind the verification with
                                 * dead code elimination, i.e., floating nodes without usages are
                                 * not removed, thus we must handle the case that a floating node
                                 * without a usage occurs here.
                                 */
                                if (nonFixedNodeTreeWithoutUsages(usage)) {
                                    continue;
                                }
                            }
                        }
                    }

                    assert usageBlock != null || usage instanceof ProxyNode : "Usage " + usageNode + " of node " + n + " has no block";

                    Loop<Block> usageLoop = null;
                    if (usageNode instanceof ProxyNode) {
                        ProxyNode proxyNode = (ProxyNode) usageNode;
                        usageLoop = nodeMap.get(proxyNode.proxyPoint().loopBegin()).getLoop();
                    } else {
                        if (usageBlock.getBeginNode() instanceof LoopExitNode) {
                            // For nodes in the loop exit node block, we don't know for sure
                            // whether they are "in the loop" or not. It depends on whether
                            // one of their transient usages is a loop proxy node.
                            // For now, let's just assume those nodes are OK, i.e., "in the loop".
                            LoopExitNode loopExitNode = (LoopExitNode) usageBlock.getBeginNode();
                            usageLoop = nodeMap.get(loopExitNode.loopBegin()).getLoop();
                        } else {
                            usageLoop = usageBlock.getLoop();
                        }
                    }

                    assert usageLoop != null : n + ", " + nodeMap.get(n) + " / " + usageNode + ", " + nodeMap.get(usageNode);
                    while (usageLoop != block.getLoop() && usageLoop != null) {
                        usageLoop = usageLoop.getParent();
                    }
                    assert usageLoop != null : n + ", " + usageNode + ", " + usageBlock + ", " + usageBlock.getLoop() + ", " + block + ", " + block.getLoop();
                }
            }
        }
        return currentState;
    }

    private static boolean nonFixedNodeTreeWithoutUsages(Node n) {
        if (!(n instanceof FloatingNode || n instanceof VirtualState || n instanceof CallTargetNode)) {
            return false;
        }
        NodeBitMap visited = n.graph().createNodeBitMap();
        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(n);
        while (!stack.isEmpty()) {
            Node cur = stack.pop();
            if (visited.isMarked(cur)) {
                continue;
            }
            if (!(cur instanceof FloatingNode || cur instanceof VirtualState || cur instanceof CallTargetNode)) {
                return false;
            }
            visited.mark(cur);
            for (Node usage : cur.usages()) {
                stack.push(usage);
            }
        }
        return true;
    }

    private static void addFloatingReadUsages(EconomicSet<FloatingReadNode> currentState, Node n) {
        for (FloatingReadNode read : n.usages().filter(FloatingReadNode.class)) {
            if (read.getLastLocationAccess() == n && read.getLocationIdentity().isMutable()) {
                currentState.add(read);
            }
        }
    }

    private void processLocation(Node n, LocationIdentity location, EconomicSet<FloatingReadNode> currentState) {
        assert n != null;
        if (location.isImmutable()) {
            return;
        }

        for (FloatingReadNode r : cloneState(currentState)) {
            if (r.getLocationIdentity().overlaps(location)) {
                // This read is killed by this location.
                r.getDebug().log(DebugContext.VERBOSE_LEVEL, "%s removing %s from state", n, r);
                currentState.remove(r);
            }
        }
    }

    @Override
    protected EconomicSet<FloatingReadNode> merge(Block merge, List<EconomicSet<FloatingReadNode>> states) {
        EconomicSet<FloatingReadNode> result = states.get(0);
        for (int i = 1; i < states.size(); ++i) {
            result.retainAll(states.get(i));
        }
        return result;
    }

    @Override
    protected EconomicSet<FloatingReadNode> cloneState(EconomicSet<FloatingReadNode> oldState) {
        EconomicSet<FloatingReadNode> result = EconomicSet.create(Equivalence.IDENTITY);
        if (oldState != null) {
            result.addAll(oldState);
        }
        return result;
    }

    @Override
    protected List<EconomicSet<FloatingReadNode>> processLoop(Loop<Block> loop, EconomicSet<FloatingReadNode> initialState) {
        HIRLoop l = (HIRLoop) loop;
        for (MemoryPhiNode memoryPhi : ((LoopBeginNode) l.getHeader().getBeginNode()).memoryPhis()) {
            for (FloatingReadNode r : cloneState(initialState)) {
                if (r.getLocationIdentity().overlaps(memoryPhi.getLocationIdentity())) {
                    initialState.remove(r);
                }
            }
        }
        return ReentrantBlockIterator.processLoop(this, loop, initialState).exitStates;
    }
}
