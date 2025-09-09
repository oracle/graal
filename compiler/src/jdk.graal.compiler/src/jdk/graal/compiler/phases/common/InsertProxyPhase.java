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
package jdk.graal.compiler.phases.common;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GuardPhiNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.util.GraphOrder;

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
public class InsertProxyPhase extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    private record ProxyKey(HIRBlock block, InputType type, ValueNode value) {
    }

    private record PhiKey(HIRBlock block, InputType type, ValueNode value) {
    }

    /**
     * Loop information required for inserting value proxies.
     *
     * @param innerLoop Whether the loop is nested inside another loop.
     * @param blockMap The block ids of blocks that are contained inside this loop.
     * @param exits The exit blocks of this loop.
     * @param replacements The replacement nodes for each exit block of the current loop. If a value
     *            was already proxied for one of its usages, we can reuse the replacement value.
     * @param blockToNode The block to node map of the schedule.
     */
    private record LoopScope(boolean innerLoop, BitSet blockMap, List<HIRBlock> exits,
                    EconomicMap<ProxyKey, ValueNode> replacements, BlockMap<List<Node>> blockToNode) {

        /**
         * Searches for the loop exit that dominates the given block. If so, by definition there can
         * only be a single exit that dominates this block. Else we would have multiple loop exits
         * that dominate each other which would be a broken graph.
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
         * Creates a proxy for the given value. If a proxy already exists for the value at the given
         * loop exit block, the proxy is reused.
         *
         * @param loopExitBlock The loop exit at which the value must be proxied (the point at which
         *            the value leaves the loop).
         * @param value The value that must be proxied.
         * @return A proxy for the given value that is associated with the loop exit.
         */
        private ValueNode getOrCreateProxy(HIRBlock loopExitBlock, ValueNode value, InputType type, LoopScope loop, NodeMap<HIRBlock> nodeToBlock) {
            ValueNode replacement = replacements.get(new ProxyKey(loopExitBlock, type, value));
            if (replacement == null) {
                assert loopExitBlock.getBeginNode() instanceof LoopExitNode : "Loop exit block begin must be a loop exit node";
                final LoopExitNode loopExit = (LoopExitNode) loopExitBlock.getBeginNode();
                if (type == InputType.Value) {
                    replacement = ProxyNode.forValue(value, loopExit);
                } else if (type == InputType.Guard) {
                    assert value instanceof GuardingNode : "Input type guard requires a guarding node for proxying";
                    replacement = ProxyNode.forGuard((GuardingNode) value, loopExit);
                } else if (type == InputType.Memory) {
                    GraalError.guarantee(MemoryKill.isSingleMemoryKill(value), "Can only proxy single kills and not multi kills %s", value);
                    replacement = ProxyNode.forMemory(MemoryKill.asSingleMemoryKill(value), loopExit, MemoryKill.asSingleMemoryKill(value).getKilledLocationIdentity());
                } else if (type == InputType.Association || type == InputType.Extension || type == InputType.Condition) {
                    replacement = cloneNodesUntilNodesCanBeProxied(loopExitBlock, value, type, loop, nodeToBlock);
                } else {
                    throw GraalError.shouldNotReachHere("Unsupported input type for proxy value " + type + " at node " + value);
                }
                replacements.put(new ProxyKey(loopExitBlock, type, value), replacement);
                if (innerLoop) {
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
         * Recursively duplicates a floating node until all of its inputs can be safely proxied at a
         * given loop exit.
         * <p>
         * This method is used for handling inputs of types such as Association, Extension, or
         * Condition, where normal proxy creation is not supported directly. The method creates a
         * duplicate of the specified floating node, replacing its inputs that originate from inside
         * the loop with appropriate proxies. For each input within the loop, the process recurses
         * to ensure all required proxies are created, and the result is inserted into the schedule
         * and block-to-node mapping for future reference.
         * <p>
         * If the value is not a floating node, an error is thrown. On success, the newly cloned
         * node is fully set up for safe use outside the original loop.
         *
         * @return a duplicated node whose inputs have been appropriately handled so they may be
         *         proxied at the loop exit
         */
        private ValueNode cloneNodesUntilNodesCanBeProxied(HIRBlock loopExitBlock, Node value, InputType type, LoopScope loop, NodeMap<HIRBlock> nodeToBlock) {
            if (!(value instanceof FloatingNode)) {
                throw GraalError.shouldNotReachHere("Cannot duplicate non floating nodes when creating proxies " + value + " for edge type=" + type);
            }
            ensureNodeCanBeDuplicated(value);
            ValueNode duplicate = (ValueNode) value.copyWithInputs(true);
            for (Position pos : duplicate.inputPositions()) {
                Node currentInput = pos.get(duplicate);
                if (pos.isInputOptional() && currentInput == null) {
                    continue;
                }
                HIRBlock inputBlock = getEffectiveBlock(currentInput, nodeToBlock);
                if (!loop.blockOutsideLoop(inputBlock)) {
                    // only create a proxy if we are inside the loop
                    ValueNode newInput = getOrCreateProxy(loopExitBlock, (ValueNode) currentInput, pos.getInputType(), loop, nodeToBlock);
                    pos.set(duplicate, newInput);
                }
            }

            // update the schedule with the new node, we might query it later again
            nodeToBlock.setAndGrow(duplicate, loopExitBlock);
            blockToNode.get(loopExitBlock).add(duplicate);

            return duplicate;
        }

        /**
         * Creates a phi for the given value at the nearest dominating merge with a predecessor
         * block dominated by a loop exit.
         *
         * Consider a loop with a random node {@code value} that is defined inside the loop. The
         * value is used after the loop - the usage block itself has no single loop exit dominating
         * it but a merge (b2) that has 2 predecessors where all of them are dominated by a
         * different loop exit. Since bX dominates b2 there is no phi necessary without proxies.
         * After adding proxies to b0 (and a block dominating b1) the values no longer dominate b2,
         * and thus we need a phi.
         *
         * <pre>
         *
         *                 |------------|
         *              bX |   Some     |
         *                 |   Block    |    <-define(value)
         *                 |    in      |
         *                 |   Loop     |
         *                 |------------|
         *
         *
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
         *              b7 |   Block   | <-- usage(value)
         *                 |-----------|
         * </pre>
         * <p>
         * If we assume that the value is in b7 in the example above, we would move up the
         * dominators and first find b6. Since b6 does not have a predecessor block that is
         * dominated by a loop exit, we do not need to insert a phi, since there is another common
         * dominator, b3, in between the current block and the loop. Since b3 is not a merge, we
         * continue at its dominator b2. Since b2 has a predecessor block dominated by a loop exit
         * (b0), we insert a proxy at the loop exit and create a phi at the merge (b2).
         */
        private ValueNode createPhiAtLoopExitMerge(Node value, InputType type, HIRBlock usageBlock, StructuredGraph graph, LoopScope loop, NodeMap<HIRBlock> nodeToBlock,
                        EconomicMap<PhiKey, ValueNode> phiCache) {
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before creating phi at lex merge value=%s type=%s usageBlock.Begin=%s.%s", value, type, usageBlock,
                            usageBlock.getBeginNode());

            HIRBlock block = usageBlock;
            while (block != null && block.getDominatorDepth() > 0) {

                // As long as we are not at a merge, move up the dominators
                while (block.getPredecessorCount() == 1) {
                    block = block.getDominator();
                }

                // after skipping predecessor blocks check if we need a phi
                if (mergeNeedsExitPhi(block)) {
                    break;
                }

                CompilationAlarm.checkProgress(graph);

                // Move up the dominators to find the next candidate block
                block = block.getDominator();
            }

            GraalError.guarantee(block != null && block != block.getCfg().getStartBlock(),
                            "Must find a block(!=null) and must not walk up to the start, need to find an exit block for %s.%s, found =%s", usageBlock, usageBlock.getBeginNode(), block);

            PhiKey phiKey = new PhiKey(block, type, (ValueNode) value);
            if (phiCache.containsKey(phiKey)) {
                return phiCache.get(phiKey);
            }

            final AbstractMergeNode merge = (AbstractMergeNode) block.getBeginNode();
            final int predecessorCount = block.getPredecessorCount();

            if (type == InputType.Association || type == InputType.Extension || type == InputType.Condition) {
                /*
                 * We have to duplicate this node and create phis for the inputs.
                 */
                ValueNode duplicate = duplicateNonProxyAbleFloatingNodes(value, usageBlock, graph, loop, nodeToBlock, phiCache, predecessorCount, block, merge);

                // update the schedule with the new node, we might query it later again
                // put the new node in the map as well
                nodeToBlock.setAndGrow(duplicate, block);
                blockToNode.get(block).add(duplicate);

                // cache the entry
                phiCache.put(phiKey, duplicate);

                return duplicate;
            } else if (type == InputType.Guard || type == InputType.Value || type == InputType.Memory) {
                ValueNode[] phiValues = proxyInputsIfNecessary(graph, loop, nodeToBlock, phiCache, predecessorCount, block, value, type);
                if (type == InputType.Value) {
                    ValueNode replacement = graph.addOrUniqueWithInputs(new ValuePhiNode(phiValues[0].stamp(NodeView.DEFAULT), merge, phiValues));
                    phiCache.put(phiKey, replacement);
                    return replacement;
                } else if (type == InputType.Guard) {
                    ValueNode replacement = graph.addOrUniqueWithInputs(new GuardPhiNode(merge, phiValues));
                    phiCache.put(phiKey, replacement);
                    return replacement;
                } else if (type == InputType.Memory) {
                    ValueNode replacement = graph.addOrUniqueWithInputs(new MemoryPhiNode(merge, MemoryKill.asSingleMemoryKill(phiValues[0]).getKilledLocationIdentity(), phiValues));
                    phiCache.put(phiKey, replacement);
                    return replacement;
                } else {
                    throw GraalError.shouldNotReachHere(
                                    "Unsupported input type for proxy value " + value + " with edge type=" + type + " usageBlock=" + usageBlock + ".begin=" + usageBlock.getBeginNode());
                }
            }
            throw GraalError.shouldNotReachHere(
                            "Unsupported input type for proxy value " + value + " with edge type=" + type + " usageBlock=" + usageBlock + ".begin=" + usageBlock.getBeginNode());
        }

        /**
         * Determines if the current merge need phis for proxies of the given loop exits. That is,
         * this merge is not dominated by a single exit and no predecessor either.
         */
        private boolean mergeNeedsExitPhi(HIRBlock block) {
            /*
             * We are at a merge block, if our dominator is dominated by an exit or one our
             * predecessors we can stop iterating.
             */
            if (block.getBeginNode() instanceof MergeNode) {
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
                        // predecssor exits, require a phi here
                        return true;
                    }
                }

                final HIRBlock predecessorLoopExit = exitForBlock(block.getDominator());
                if (predecessorLoopExit == null) {
                    /*
                     * Neither one of our predecessors (forward ends for the current merge) nor our
                     * dominator is dominated by a loop exit. We have to create this phi. This can
                     * happen for very convoluted merge patterns of non-canonical graphs.
                     */
                    return true;
                }
            }
            return false;
        }

        /**
         * Creates a duplicate of a non-proxyable floating node, replacing its inputs with proxies
         * or phis as necessary.
         * <p>
         * This method recursively traverses the inputs of a given floating node and, for inputs
         * that are still within the current loop, replaces them with value phis, guard phis, or
         * memory phis at the merge block corresponding to the given usage context. This ensures
         * that all (nested) inputs are correctly proxied as needed for values that flow out of
         * their defining loop.
         * <p>
         * The duplicate node will have the same structural characteristics as the original, except
         * where any inputs are replaced with suitable proxies or phis, guaranteeing proper
         * loop-exit semantics.
         */
        private ValueNode duplicateNonProxyAbleFloatingNodes(Node value, HIRBlock usageBlock, StructuredGraph graph, LoopScope loop, NodeMap<HIRBlock> nodeToBlock,
                        EconomicMap<PhiKey, ValueNode> phiCache, int predecessorCount, HIRBlock block, AbstractMergeNode merge) {
            ensureNodeCanBeDuplicated(value);
            ValueNode duplicate = (ValueNode) value.copyWithInputs(true);
            for (Position pos : duplicate.inputPositions()) {
                Node currentInput = pos.get(duplicate);
                if (pos.isInputOptional() && currentInput == null) {
                    continue;
                }
                InputType currentInputType = pos.getInputType();
                final HIRBlock currentInputBlock = getEffectiveBlock(currentInput, nodeToBlock);
                if (!loop.blockOutsideLoop(currentInputBlock)) {
                    ValueNode[] phiValues = proxyInputsIfNecessary(graph, loop, nodeToBlock, phiCache, predecessorCount, block, currentInput, currentInputType);
                    ValueNode replacement = null;
                    if (currentInputType == InputType.Value) {
                        replacement = graph.addOrUniqueWithInputs(new ValuePhiNode(phiValues[0].stamp(NodeView.DEFAULT), merge, phiValues));
                    } else if (currentInputType == InputType.Guard) {
                        replacement = graph.addOrUniqueWithInputs(new GuardPhiNode(merge, phiValues));
                    } else if (currentInputType == InputType.Memory) {
                        // cannot phi multi kills
                        replacement = graph.addOrUniqueWithInputs(new MemoryPhiNode(merge, ((SingleMemoryKill) phiValues[0]).getKilledLocationIdentity(), phiValues));
                    } else {
                        throw GraalError.shouldNotReachHere(
                                        "Unsupported input type for proxy value " + value + " with edge type=" + currentInputType + " usageBlock=" + usageBlock + ".begin=" +
                                                        usageBlock.getBeginNode());
                    }
                    // only create a proxy if we are inside the loop
                    pos.set(duplicate, replacement);
                }
            }
            return duplicate;
        }

        /**
         * Generates an array of value nodes (proxies or phis as needed) for a given node input at a
         * merge point.
         * <p>
         * For each predecessor of the given merge block, this method determines whether the input
         * node needs to be proxied because it leaves a loop, or whether it should recursively
         * create phi nodes if the predecessor is itself dominated by multiple loop exits. This
         * logic ensures that all values flowing between loops and merge points are correctly
         * represented with the appropriate node (proxy or phi) to maintain correct data and control
         * dependencies across loop boundaries.
         */
        private ValueNode[] proxyInputsIfNecessary(StructuredGraph graph, LoopScope loop, NodeMap<HIRBlock> nodeToBlock, EconomicMap<PhiKey, ValueNode> phiCache, int predecessorCount, HIRBlock block,
                        Node currentInput, InputType currentInputType) {
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
                    phiValue = createPhiAtLoopExitMerge(currentInput, currentInputType, predecessor, graph, loop, nodeToBlock, phiCache);
                } else {
                    phiValue = getOrCreateProxy(predecessorLoopExit, (ValueNode) currentInput, currentInputType, loop, nodeToBlock);
                }
                phiValues[i] = phiValue;
            }
            return phiValues;
        }

        /**
         * Replaces the inputs of the phi that correspond to the given value by a proxy.
         */
        private void processPhiInputs(ValueNode value, InputType type, PhiNode phi, HIRBlock usageBlock, StructuredGraph graph, LoopScope loop, NodeMap<HIRBlock> nodeToBlock,
                        EconomicMap<PhiKey, ValueNode> phiCache) {
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
                        phiValue = createPhiAtLoopExitMerge(value, type, predecessor, graph, loop, nodeToBlock, phiCache);
                    } else {
                        phiValue = getOrCreateProxy(predecessorLoopExit, value, type, loop, nodeToBlock);
                    }
                    phi.setValueAt(i, phiValue);
                }
            }
        }

        /**
         * Adds a proxy for the inputs of the phi.
         */
        public void processPhiNodeUsage(ValueNode value, PhiNode phi, HIRBlock usageBlock, StructuredGraph graph, LoopScope loop, NodeMap<HIRBlock> nodeToBlock,
                        EconomicMap<PhiKey, ValueNode> phiCache) {
            if (phi instanceof ValuePhiNode) {
                processPhiInputs(value, InputType.Value, phi, usageBlock, graph, loop, nodeToBlock, phiCache);
            } else if (phi instanceof GuardPhiNode) {
                processPhiInputs(value, InputType.Guard, phi, usageBlock, graph, loop, nodeToBlock, phiCache);
            } else if (phi instanceof MemoryPhiNode) {
                processPhiInputs(value, InputType.Memory, phi, usageBlock, graph, loop, nodeToBlock, phiCache);
            } else {
                throw GraalError.shouldNotReachHere("Unsupported input type for proxy value " + phi);
            }
        }

        public void processValueNodeUsage(ValueNode value, Node usage, HIRBlock usageBlock, StructuredGraph graph, LoopScope loop, NodeMap<HIRBlock> nodeToBlock,
                        EconomicMap<PhiKey, ValueNode> phiCache) {
            final HIRBlock exit = exitForBlock(usageBlock);
            for (Position pos : usage.inputPositions()) {
                if (pos.get(usage) == value) {
                    final ValueNode proxy;
                    if (exit == null) {
                        /*
                         * If there's no single loop exit dominating the usage block, it may be due
                         * to a control-flow merge between the usage block and loop exits. In this
                         * case, we search for the next merge dominating the usage block that
                         * requires a phi for the value. See {@link #createPhiAtLoopExitMerge} for
                         * details.
                         */
                        proxy = createPhiAtLoopExitMerge(value, pos.getInputType(), usageBlock, graph, loop, nodeToBlock, phiCache);
                    } else {
                        proxy = getOrCreateProxy(exit, value, pos.getInputType(), loop, nodeToBlock);
                    }
                    pos.set(usage, proxy);

                }
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
    protected void run(StructuredGraph graph) {
        if (!graph.hasLoops()) {
            return;
        }
        removeProxies(graph);

        // if there is dead code clean it up already
        final boolean immutableGraph = false;
        // do not verify proxies because we are building them
        final boolean verifyProxies = false;
        SchedulePhase.runWithoutContextOptimizations(graph, SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS, ControlFlowGraph.computeForSchedule(graph), immutableGraph, verifyProxies);

        final HIRBlock[] reversePostOrder = graph.getLastCFG().reversePostOrder();

        final StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        final BlockMap<List<Node>> blockToNode = schedule.getBlockToNodesMap();
        final NodeMap<HIRBlock> nodeToBlock = schedule.getNodeToBlockMap();

        final EconomicMap<CFGLoop<HIRBlock>, LoopScope> loopScopes = EconomicMap.create(Equivalence.IDENTITY);
        final EconomicMap<ProxyKey, ValueNode> replacements = EconomicMap.create();
        final EconomicMap<PhiKey, ValueNode> phiCache = EconomicMap.create();

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
                if (node instanceof AbstractMergeNode merge) {
                    for (PhiNode phi : merge.phis()) {
                        processNode(phi, graph, workingSet, nodeToBlock, scope, replacements, phiCache);
                    }
                } else {
                    processNode(node, graph, workingSet, nodeToBlock, scope, replacements, phiCache);
                }
            }
        }

        assert GraphOrder.assertSchedulableGraph(graph);
    }

    /**
     * <p>
     * <b>Note on Pre-existing Proxies and Cleanup:</b> Due to Truffle-level inlining, proxies may
     * already exist in parts of the graph. Since proxies (and phis) are handled specially by the
     * scheduler, they can create subtle issues with floating nodes or chains, making it unclear
     * whether a node is inside or outside a loop. This can lead to non-decidable schedules. To
     * prevent such issues, all proxies must be deleted from the graph before running this phase.
     * </p>
     *
     * See InsertProxyReProxyTest.java for a background.
     */
    private static void removeProxies(StructuredGraph graph) {
        for (LoopExitNode exit : graph.getNodes(LoopExitNode.TYPE)) {
            exit.removeProxies();
        }
    }

    private static void processNode(Node node, StructuredGraph graph, ArrayDeque<Node> workingSet, NodeMap<HIRBlock> nodeToBlock, LoopScope loop, EconomicMap<ProxyKey, ValueNode> replacements,
                    EconomicMap<PhiKey, ValueNode> phiCache) {
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
                        loop.processPhiNodeUsage(value, phi, phiUsageBlock, graph, loop, nodeToBlock, phiCache);
                    }
                } else if (usage instanceof ProxyNode proxy) {
                    final HIRBlock proxyUsageBlock = nodeToBlock.get(proxy.proxyPoint());
                    /*
                     * If the proxy is already at an exit of the current loop (the dominating loop
                     * exit is the usage block), we don't have to process it.
                     */
                    if (loop.exitForBlock(proxyUsageBlock) != proxyUsageBlock) {
                        if (loop.blockOutsideLoop(proxyUsageBlock)) {
                            loop.processValueNodeUsage(value, proxy, proxyUsageBlock, graph, loop, nodeToBlock, phiCache);
                        }
                    }
                } else {

                    final HIRBlock usageBlock = nodeToBlock.get(usage);
                    if (loop.blockOutsideLoop(usageBlock)) {
                        loop.processValueNodeUsage(value, usage, usageBlock, graph, loop, nodeToBlock, phiCache);
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

    /**
     * Retrieves the effective block for a given node, handling {@link PhiNode} and
     * {@link ProxyNode} by resolving to their associated {@link MergeNode} or {@link LoopExitNode}
     * block, respectively.
     */
    private static HIRBlock getEffectiveBlock(Node current, NodeMap<HIRBlock> blockToNode) {
        // if we created the phi or proxy
        HIRBlock block = blockToNode.isNew(current) ? null : blockToNode.get(current);
        if (block == null) {
            assert current instanceof PhiNode || current instanceof ProxyNode : Assertions.errorMessage("Only phis are not scheduled (in a late schedule)", current);
            if (current instanceof PhiNode phi) {
                block = blockToNode.get(phi.merge());
            } else if (current instanceof ProxyNode proxy) {
                block = blockToNode.get(proxy.proxyPoint());
            } else {
                throw GraalError.shouldNotReachHere("Node without a schedule " + current);
            }
        }
        return block;
    }

    /**
     * Verifies that a given node can be safely duplicated.
     * <p>
     * A node is considered duplicable if it is a floating node and its semantics is safe in and
     * outside the loop. This is generally true for all floating nodes (since they are side effect
     * free) except for memory accesses. This is crucial because duplicating a memory access outside
     * its original loop could interfere with the memory graph, potentially causing inconsistencies
     * or incorrect behavior if the loop overlaps with the locations of the access.
     */
    private static void ensureNodeCanBeDuplicated(Node n) {
        GraalError.guarantee(GraphUtil.isFloatingNode(n), "Node must be a floating node to be duplicated");
        GraalError.guarantee(!(n instanceof MemoryAccess), "Cannot duplicate memory access outside of loop, memory graph could be in the way");
    }
}
