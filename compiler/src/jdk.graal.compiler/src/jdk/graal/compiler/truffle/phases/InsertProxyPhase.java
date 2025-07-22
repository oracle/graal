/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.phases;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GuardPhiNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;

/**
 * Phase that inserts proxies after partial evaluation. Performing the proxy insertion in a separate
 * phase avoids proxying all values during partial evaluation and performing an expensive cleanup
 * afterward.
 * <p>
 * Proxies are required for values that are defined inside a loop and are used outside this loop.
 * <p>
 * The basic idea of the algorithm is as follows:
 * <li>Perform a schedule of all nodes.</li>
 * <li>Iterate all nodes (input nodes) that are inside loops and check whether any of their usages
 * is outside the loop of their input node.</li>
 * <li>If a usage is outside the loop, find its corresponding loop exit (the loop exit block at
 * which the value exits the loop) and insert a proxy for the node.</li>
 * <li>If a loop is nested inside another loop, the added proxy of the inner loop gets added to the
 * loop exit block inside the schedule, so it is processed again for the outer loop.</li>
 */
public class InsertProxyPhase extends BasePhase<CoreProviders> {
    public static final class Options {
        @Option(help = "Use an earliest instead of a latest schedule for inserting proxies after partial evaluation", type = OptionType.Debug) //
        public static final OptionKey<Boolean> UseEarliestScheduleForProxyInsertion = new OptionKey<>(false);
    }

    private final SchedulePhase.SchedulingStrategy strategy;
    private final SchedulePhase scheduler;

    public InsertProxyPhase(OptionValues options) {
        this.strategy = Options.UseEarliestScheduleForProxyInsertion.getValue(options) ? SchedulePhase.SchedulingStrategy.EARLIEST
                        : SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS;
        this.scheduler = new SchedulePhase(this.strategy);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    private record ProxyKey(HIRBlock block, InputType type) {
    }

    /**
     * Loop information required for inserting value proxies.
     */
    private static class LoopScope {
        /**
         * Whether the loop is nested inside another loop.
         */
        private final boolean parent;

        /**
         * The block ids of blocks that are contained inside this loop.
         */
        private final BitSet blockMap;
        /**
         * The exit blocks of this loop.
         */
        private final List<HIRBlock> exits;
        /**
         * The replacement nodes for each exit block of the current loop. If a value was already
         * proxied for one of its usages, we can reuse the replacement value.
         */
        private final EconomicMap<ProxyKey, ValueNode> replacements;

        /**
         * The block to node map of the schedule.
         */
        private final BlockMap<List<Node>> blockToNode;

        LoopScope(boolean parent, BitSet blockMap, List<HIRBlock> exits, EconomicMap<ProxyKey, ValueNode> replacements, BlockMap<List<Node>> blockToNode) {
            this.parent = parent;
            this.blockMap = blockMap;
            this.exits = exits;
            this.replacements = replacements;
            this.blockToNode = blockToNode;
        }

        /**
         * Searches for the loop exit that dominates the given block.
         *
         * @return The loop exit that dominates the given block, or <code>null</code> if the block
         *         is not dominated by any of the loop exits or dominated by multiple of the loop
         *         exits.
         */
        public HIRBlock exitForBlock(HIRBlock block) {
            for (final HIRBlock exit : exits) {
                if (exit.dominates(block)) {
                    return exit;
                }
            }
            return null;
        }

        /**
         * @return Whether the given block is outside this loop.
         */
        private boolean blockOutsideLoop(HIRBlock block) {
            return !blockMap.get(block.getId());
        }

        /**
         * Creates a proxy for the given value. If a proxy already exits for the value at the given
         * loop exit block, the proxy is reused.
         *
         * @param loopExitBlock The loop exit at which the value must be proxied (the point at which
         *            the value leaves the loop).
         * @param value The value that must be proxied.
         * @return A proxy for the given value that is associated with the loop exit.
         */
        private ValueNode getOrCreateProxy(HIRBlock loopExitBlock, Node value, InputType type) {
            ValueNode replacement = replacements.get(new ProxyKey(loopExitBlock, type));
            if (replacement == null) {
                assert loopExitBlock.getBeginNode() instanceof LoopExitNode : "Loop exit block begin must be a loop exit node";
                final LoopExitNode loopExit = (LoopExitNode) loopExitBlock.getBeginNode();
                if (type == InputType.Value) {
                    assert value instanceof ValueNode : "Input type value requires a value node for proxying";
                    replacement = ProxyNode.forValue((ValueNode) value, loopExit);
                } else if (type == InputType.Condition) {
                    assert value instanceof LogicNode : "Input type condition requires a logic node for proxying";
                    /*
                     * Since logic nodes cannot be proxied, we insert a conditional, which we can
                     * proxy, and also insert an equals node for the proxy outside the loop, so we
                     * again have a logic node.
                     */
                    final StructuredGraph graph = loopExit.graph();
                    /*
                     * We use without unique here, since we must make sure that the conditional is
                     * created inside the current loop. GVN could return a conditional that is
                     * outside the current loop. If another conditional already exist in the correct
                     * loop, canonicalization after this phase will merge the conditional nodes.
                     */
                    final ConditionalNode conditional = graph.addWithoutUnique(new ConditionalNode((LogicNode) value));
                    final ProxyNode proxy = ProxyNode.forValue(conditional, loopExit);
                    /*
                     * We use without unique again here for the same reason as for the conditional.
                     * If we have multiple nested loops, the GVNed equals node could be outside the
                     * loop in which we want to insert the equals node. If an equals node already
                     * exists in the correct loop, canonicalization after this phase will merge the
                     * equals nodes.
                     */
                    replacement = graph.addWithoutUnique(new IntegerEqualsNode(proxy, ConstantNode.forInt(1, graph)));
                } else if (type == InputType.Guard) {
                    assert value instanceof GuardingNode : "Input type guard requires a guarding node for proxying";
                    replacement = ProxyNode.forGuard((GuardingNode) value, loopExit);
                } else {
                    throw GraalError.shouldNotReachHere("Unsupported input type for proxy value");
                }
                replacements.put(new ProxyKey(loopExitBlock, type), replacement);
                if (parent) {
                    /*
                     * If the current loop is nested in another loop, we add the replacement to the
                     * exit block, so it is processed again for the outer loop.
                     */
                    blockToNode.get(loopExitBlock).add(replacement);
                }
            }
            return replacement;
        }

        /**
         * Creates a phi for the given value at the next merge dominating the given usage block that
         * has at least one predecessor block that is dominated by a loop exit of the current loop.
         *
         * <pre>
         *     |-----------|           |-----------|
         *  b1 |   Begin   |        b0 | Loop Exit | <-- requires a proxy
         *     |-----------|           |-----------|
         *                 \           /
         *                  \         /
         *                 |-----------|
         *              b2 |   Merge   | <-- requires a phi
         *                 |-----------|
         *                       |
         *                 |-----------|
         *              b3 |    If     |
         *                 |-----------|
         *                  /         \
         *                 /           \
         *     |-----------|           |-----------|
         *  b4 |   Begin   |        b5 |   Begin   |
         *     |-----------|           |-----------|
         *                 \          /
         *                  \        /
         *                 |-----------|
         *              b6 |   Merge   |
         *                 |-----------|
         *                       |
         *                 |-----------|
         *              b7 |   Block   | <-- value
         *                 |-----------|
         * </pre>
         *
         * If we assume that the value is in b7 in the example above, we would move up the
         * dominators and first find b6. Since b6 does not have a predecessor block that is
         * dominated by a loop exit, we do not need to insert a phi, since there is another common
         * dominator, b3, inbetween the current block and the loop. Since b3 is not a merge, we
         * continue at its dominator b2. Since b2 has a predecessor block dominated by a loop exit
         * (b0), we insert a proxy at the loop exit and create a phi at the merge (b2).
         *
         * @param value The value for which to create a phi.
         * @param usageBlock The block of the value.
         * @param graph The current graph.
         */
        private PhiNode createPhiAtLoopExitMerge(Node value, InputType type, HIRBlock usageBlock, StructuredGraph graph) {
            HIRBlock block = usageBlock;
            outer: while (true) { // TERMINATION ARGUMENT: modified dominator walk, terminates at a
                                  // loop exit for a schedulable graph
                // As long as we are not at a merge, move up the dominators
                while (block.getPredecessorCount() == 1) {
                    block = block.getDominator();
                }

                /*
                 * Check, if at least one of the merge predecessors is dominated by a loop exit. If
                 * this is the case, create a new phi for the value. Otherwise, we are at a merge
                 * that does not require a phi for the value.
                 */
                final int predecessorCount = block.getPredecessorCount();
                for (int i = 0; i < predecessorCount; i++) {
                    final HIRBlock predecessor = block.getPredecessorAt(i);
                    final HIRBlock predecessorLoopExit = exitForBlock(predecessor);
                    if (predecessorLoopExit != null) {
                        break outer;
                    }
                }
                CompilationAlarm.checkProgress(graph);
                // Move up the dominators to find the next candidate block
                block = block.getDominator();
            }

            final AbstractMergeNode merge = (AbstractMergeNode) block.getBeginNode();
            final int predecessorCount = block.getPredecessorCount();

            final ValueNode[] phiValues = new ValueNode[predecessorCount];
            for (int i = 0; i < predecessorCount; i++) {
                final HIRBlock predecessor = block.getPredecessorAt(i);
                final HIRBlock predecessorLoopExit = exitForBlock(predecessor);
                final ValueNode phiValue;
                if (predecessorLoopExit == null) {
                    /*
                     * The predecessor is again dominated by a merge of multiple loop exits, so we
                     * recursively create another phi as input to this phi.
                     */
                    phiValue = createPhiAtLoopExitMerge(value, type, predecessor, graph);
                } else {
                    phiValue = getOrCreateProxy(predecessorLoopExit, value, type);
                }
                phiValues[i] = phiValue;
            }
            if (type == InputType.Value || type == InputType.Condition) {
                return graph.addOrUniqueWithInputs(new ValuePhiNode(phiValues[0].stamp(NodeView.DEFAULT), merge, phiValues));
            } else if (type == InputType.Guard) {
                return graph.addOrUniqueWithInputs(new GuardPhiNode(merge, phiValues));
            } else {
                throw GraalError.shouldNotReachHere("Unsupported input type for proxy value");
            }
        }

        /**
         * Replaces the inputs of the phi that correspond to the given value by a proxy.
         */
        private void processPhiInputs(ValueNode value, InputType type, PhiNode phi, HIRBlock usageBlock, StructuredGraph graph) {
            for (int i = 0; i < phi.valueCount(); i++) {
                if (phi.valueAt(i) == value) {
                    final HIRBlock predecessor = usageBlock.getPredecessorAt(i);
                    final HIRBlock predecessorLoopExit = exitForBlock(predecessor);
                    final ValueNode phiValue;
                    if (predecessorLoopExit == null) {
                        /*
                         * The predecessor is dominated by a merge of multiple loop exits, so we
                         * create a phi for the given value.
                         */
                        phiValue = createPhiAtLoopExitMerge(value, type, predecessor, graph);
                    } else {
                        phiValue = getOrCreateProxy(predecessorLoopExit, value, type);
                    }
                    phi.setValueAt(i, phiValue);
                }
            }
        }

        /**
         * Adds a proxy for the inputs of the phi.
         */
        public void processPhiNodeUsage(ValueNode value, PhiNode phi, HIRBlock usageBlock, StructuredGraph graph) {
            if (phi instanceof ValuePhiNode) {
                processPhiInputs(value, InputType.Value, phi, usageBlock, graph);
            } else if (phi instanceof GuardPhiNode) {
                processPhiInputs(value, InputType.Guard, phi, usageBlock, graph);
            } else {
                throw GraalError.shouldNotReachHere("Unsupported input type for proxy value");
            }
        }

        /**
         * Replaces the first occurrence of value in the inputs of usage by a proxy.
         */
        public void processValueNodeUsage(ValueNode value, Node usage, HIRBlock usageBlock, StructuredGraph graph) {
            final HIRBlock exit = exitForBlock(usageBlock);
            for (Position pos : usage.inputPositions()) {
                if (pos.get(usage) == value) {
                    final ValueNode proxy;
                    if (exit == null) {
                        /*
                         * If there is no single loop exit that dominates the usage block, we must
                         * be at a block dominated by multiple different loop exits. Therefore, we
                         * try to find the next merge dominating the usage block that requires a phi
                         * for the value.
                         */
                        proxy = createPhiAtLoopExitMerge(value, pos.getInputType(), usageBlock, graph);
                    } else {
                        proxy = getOrCreateProxy(exit, value, pos.getInputType());
                    }
                    pos.set(usage, proxy);
                    break;
                }
            }
        }

    }

    /**
     * Moves the frame states of loop begin nodes to the loop begin block and the frame states of
     * loop exit nodes to the loop exit block.
     */
    private static void moveLoopFrameStates(HIRBlock loopBegin, List<HIRBlock> loopExits, NodeMap<HIRBlock> nodeToBlock) {
        final LoopBeginNode loopBeginNode = (LoopBeginNode) loopBegin.getBeginNode();
        final FrameState entryFrameState = loopBeginNode.stateAfter();
        if (entryFrameState != null) {
            assert entryFrameState.hasExactlyOneUsage() : "loop begin frame state must only have a single usage, the loop begin node";
            nodeToBlock.set(entryFrameState, loopBegin);
        }
        for (HIRBlock loopExitBlock : loopExits) {
            final LoopExitNode loopExit = (LoopExitNode) loopExitBlock.getBeginNode();
            final FrameState exitFrameState = loopExit.stateAfter();
            if (exitFrameState != null) {
                assert exitFrameState.hasExactlyOneUsage() : "loop exit frame state must only have a single usage, the loop exit node";
                nodeToBlock.set(exitFrameState, loopExitBlock);
            }
        }
    }

    /**
     * Searches for a matching loop scope for the given loop. If the loop was not seen before, a new
     * loop scope is created.
     */
    private static LoopScope getOrCreateLoopScope(CFGLoop<HIRBlock> loop, EconomicMap<CFGLoop<HIRBlock>, LoopScope> loopScopes, EconomicMap<ProxyKey, ValueNode> replacements,
                    BlockMap<List<Node>> blockToNode) {
        LoopScope scope = loopScopes.get(loop);
        if (scope == null) {
            final BitSet blockMap = new BitSet();
            for (HIRBlock block : loop.getBlocks()) {
                blockMap.set(block.getId());
            }
            final List<HIRBlock> loopExits = loop.getLoopExits();
            scope = new LoopScope(loop.getParent() != null, blockMap, loopExits, replacements, blockToNode);
            loopScopes.put(loop, scope);
        }
        return scope;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (!graph.hasLoops()) {
            return;
        }
        scheduler.apply(graph, context);

        final HIRBlock[] reversePostOrder = graph.getLastCFG().reversePostOrder();

        final StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        final BlockMap<List<Node>> blockToNode = schedule.getBlockToNodesMap();
        final NodeMap<HIRBlock> nodeToBlock = schedule.getNodeToBlockMap();

        final EconomicMap<CFGLoop<HIRBlock>, LoopScope> loopScopes = EconomicMap.create(Equivalence.IDENTITY);
        final EconomicMap<ProxyKey, ValueNode> replacements = EconomicMap.create();

        final Graph.Mark mark = graph.getMark();

        if (strategy.isEarliest()) {
            /*
             * if we use the earliest schedule, we need to move the frame states of loop begin and
             * loop exit nodes into their corresponding blocks.
             */
            for (CFGLoop<HIRBlock> loop : graph.getLastCFG().getLoops()) {
                moveLoopFrameStates(loop.getHeader(), loop.getLoopExits(), nodeToBlock);
            }
        }

        /*
         * We maintain a working set, so we don't have to create a new array for each set of node
         * usages.
         */
        final ArrayDeque<Node> workingSet = new ArrayDeque<>();

        for (HIRBlock block : reversePostOrder) {
            final CFGLoop<HIRBlock> loop = block.getLoop();
            if (loop == null) {
                // We are outside a loop, no nodes to process
                continue;
            }
            final LoopScope scope = getOrCreateLoopScope(loop, loopScopes, replacements, blockToNode);

            for (Node node : blockToNode.get(block)) {
                if (node instanceof LoopExitNode loopExit) {
                    for (ProxyNode proxy : loopExit.proxies()) {
                        if (!graph.isNew(mark, proxy)) {
                            /*
                             * We only process existing proxies. If we introduce a new one, we
                             * anyway add the lowerest added node (proxy for values and guards,
                             * equals node for logic nodes) to the block of the loop exit, so it is
                             * processed again.
                             */
                            processNode(proxy, graph, workingSet, nodeToBlock, scope, replacements);
                        }
                    }
                } else if (node instanceof AbstractMergeNode merge) {
                    for (PhiNode phi : merge.phis()) {
                        processNode(phi, graph, workingSet, nodeToBlock, scope, replacements);
                    }
                } else {
                    processNode(node, graph, workingSet, nodeToBlock, scope, replacements);
                }
            }
        }
    }

    private static void processNode(Node node, StructuredGraph graph, ArrayDeque<Node> workingSet, NodeMap<HIRBlock> nodeToBlock, LoopScope loop, EconomicMap<ProxyKey, ValueNode> replacements) {
        if (node.hasUsages() && node instanceof ValueNode value) {
            node.usages().snapshotTo(workingSet);
            while (!workingSet.isEmpty()) {
                final Node usage = workingSet.pop();
                /*
                 * PhiNodes and ProxyNodes are not scheduled into a block. Therefore, we use their
                 * associated merges and proxy points as reference points.
                 */
                if (usage instanceof PhiNode phi) {
                    final HIRBlock phiUsageBlock = nodeToBlock.get(phi.merge());
                    if (loop.blockOutsideLoop(phiUsageBlock)) {
                        loop.processPhiNodeUsage(value, phi, phiUsageBlock, graph);
                    }
                } else if (usage instanceof ProxyNode proxy) {
                    final HIRBlock proxyUsageBlock = nodeToBlock.get(proxy.proxyPoint());
                    /*
                     * If the proxy is already at an exit of the current loop (the dominating loop
                     * exit is the usage block), we don't have to process it.
                     */
                    if (loop.exitForBlock(proxyUsageBlock) != proxyUsageBlock) {
                        if (loop.blockOutsideLoop(proxyUsageBlock)) {
                            loop.processValueNodeUsage(value, proxy, proxyUsageBlock, graph);
                        }
                    }
                } else {
                    final HIRBlock usageBlock = nodeToBlock.get(usage);
                    if (loop.blockOutsideLoop(usageBlock)) {
                        loop.processValueNodeUsage(value, usage, usageBlock, graph);
                    }
                }
            }
            /*
             * The replacements are cached for each node. We have to clear the map when we are
             * finished with processing the node.
             */
            replacements.clear();
        }
    }
}
