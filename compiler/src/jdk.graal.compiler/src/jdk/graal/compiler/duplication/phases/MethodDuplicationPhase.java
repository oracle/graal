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
package jdk.graal.compiler.duplication.phases;

import static jdk.graal.compiler.duplication.phases.MethodDuplicationPhase.Options.MaxDuplicationAnchor;
import static jdk.graal.compiler.duplication.phases.MethodDuplicationPhase.Options.MinDuplicationAnchor;
import static jdk.graal.compiler.core.common.GraalOptions.MaximumDesiredSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableEconomicSet;
import org.graalvm.collections.UnmodifiableMapCursor;

import jdk.graal.compiler.duplication.util.LoopBeginAnchorNode;
import jdk.graal.compiler.duplication.util.MethodDuplicationAnchorNode;

import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.cfg.BasicBlockSet;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeWorkList;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.loop.phases.LoopTransformations;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GuardPhiNode;
import jdk.graal.compiler.nodes.GuardProxyNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnreachableBeginNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.OpaqueGuardNode;
import jdk.graal.compiler.nodes.extended.OpaqueLogicNode;
import jdk.graal.compiler.nodes.extended.OpaqueNode;
import jdk.graal.compiler.nodes.extended.OpaqueValueNode;
import jdk.graal.compiler.nodes.loop.LoopFragmentInside;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.graph.ReentrantBlockIterator;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.util.GraphOrder;

/**
 * The goal of this phase is to remove complex and uncommon control flow paths from the main flow of
 * the method and rather handle them in a duplicated version of the whole method. It pays off if the
 * original flow of the method becomes simpler (i.e., less locations killed, less merged and less
 * phis) *and* if at run time the branch off points to the duplicated version of the method is
 * rarely taken.
 * <p>
 * The phase is performing the following transformations to the graph: 1. Introduction of
 * "duplication anchor points" in the graph at branch off points that are selected by a heuristic.
 * 2. Duplicate the whole method. 3. Peel the duplicated version of the method until all duplicated
 * anchor points are outside of loops. 4. Connect the duplication anchor points from the original
 * version to the duplicated version, kill their control flow in the original version and introduce
 * a merge in the duplicated version. 5. Clean up.
 */
public class MethodDuplicationPhase extends BasePhase<CoreProviders> {

    /**
     * The frequency that is used to distinguish the "hot part" of a method from the "cold part".
     */
    private static final double HOT_BLOCKS_FREQUENCY_THRESHOLD = 0.1;

    /**
     * The maximum branch off probability from a hot to a cold part to consider the part cold.
     */
    private static final double COLD_BLOCKS_MAX_BRANCH_OFF = 0.25;

    /**
     * Maximum code size increase factor. The duplication itself is in most cases 2x. However,
     * peeling can make it more and there are also some more merge and phi nodes introduced.
     */
    private static final float MAX_CODE_SIZE_INCREASE = 5.0f;

    /**
     * Maximum number of peeling iterations to avoid endless loops or also just too high compile
     * times in corner case situations.
     */
    private static final int MAX_PEELING_ITERATIONS = 32;

    public static class Options {
        // @formatter:off

        @Option(help = "Duplicates methods to form hot part and cold part areas.", type = OptionType.Expert)
        public static final OptionKey<Boolean> OptMethodDuplication = new OptionKey<>(false);

        @Option(help = "Maximum duplication anchor number", type = OptionType.Debug)
        public static final OptionKey<Integer> MaxDuplicationAnchor = new OptionKey<>(Integer.MAX_VALUE);

        @Option(help = "Skip n duplication anchors", type = OptionType.Debug)
        public static final OptionKey<Integer> MinDuplicationAnchor = new OptionKey<>(1);

        // @formatter:on
    }

    private final CanonicalizerPhase canonicalizer;

    public MethodDuplicationPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    public float codeSizeIncrease() {
        return MAX_CODE_SIZE_INCREASE + 1;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {

        int initialSize = graph.getNodeCount();
        DebugContext debug = graph.getDebug();
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).modifiableBlocks(true).connectBlocks(true).computeFrequency(true).computeLoops(true).computeDominators(true).build();

        // Identify the hot blocks of the method.
        BasicBlockSet hotBlocks = identifyHotBlocks(cfg);

        if (hotBlocks.count() == cfg.getBlocks().length) {
            // There are no cold blocks => no duplication necessary.
            return;
        }

        // Identify cold blocks that are directly preceded by a hot block and have at least one hot
        // block successor.
        List<HIRBlock> coldSplitReentryBlocks = identifySplitOffBlocks(cfg, hotBlocks);
        debug.log(DebugContext.INFO_LEVEL, "Method: %s / hotBlocks %d / coldBlocks %d / hot cold split offs %d", graph.method(), hotBlocks.count(), cfg.getBlocks().length - hotBlocks.count(),
                        coldSplitReentryBlocks.size());
        if (coldSplitReentryBlocks.isEmpty()) {
            // No cold split reentry blocks => duplication would be useless.
            return;
        }

        int maxSize = Math.min((int) (initialSize * MAX_CODE_SIZE_INCREASE), (int) (MaximumDesiredSize.getValue(graph.getOptions()) * 1.5));

        // Phase 1: Insert marker nodes at the split off points and proxy loop phis at loop begins.
        List<Node> nodesToDuplicate = collectNodesToDuplicate(graph);
        if (nodesToDuplicate == null) {
            // Abort because of a control flow anchor.
            debug.log(DebugContext.INFO_LEVEL, "Aborting because a control flow anchor that must not be duplicated was found");
            return;
        }
        if (graph.getNodeCount() + nodesToDuplicate.size() > maxSize) {
            // Abort, because the graph is too large to perform this type of optimization without
            // blowing the graph size completely out of proportion.
            debug.log(DebugContext.INFO_LEVEL, "Aborting because the graph size would increase too much %d + %d > %d", graph.getNodeCount(), nodesToDuplicate.size(), maxSize);
            return;
        }
        OpaqueLogicNode opaqueLogicNode = graph.addWithoutUniqueWithInputs(new OpaqueLogicNode(LogicConstantNode.tautology()));
        Graph.Mark mark = graph.getMark();
        List<MethodDuplicationAnchorNode> duplicationAnchors = insertDuplicationAnchors(graph, coldSplitReentryBlocks);
        debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After inserting duplication anchors");
        for (Node n : graph.getNewNodes(mark)) {
            if (!skipDuplication(n)) {
                nodesToDuplicate.add(n);
            }
        }
        assert GraphOrder.assertSchedulableGraph(graph);

        // Phase 2: Duplicate the whole method and insert artificial if at the method start.
        EconomicMap<Node, Node> duplicationMap = duplicateMethod(graph, nodesToDuplicate, opaqueLogicNode);
        EconomicMap<Node, Node> reverseDuplicationMap = calculateReverseMap(duplicationMap);
        debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After duplicating the method");
        assert GraphOrder.assertSchedulableGraph(graph);

        // Phase 3: Peel until duplicated duplication anchors are out of loops.
        peelLoops(graph, context, duplicationAnchors, duplicationMap, reverseDuplicationMap, maxSize);
        ControlFlowGraph cfgAfterPeeling = ControlFlowGraph.newBuilder(graph).modifiableBlocks(true).connectBlocks(true).computeFrequency(true).computeLoops(true).computeDominators(true).build();

        debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After peeling loops");
        assert GraphOrder.assertSchedulableGraph(graph);

        // Phase 4: Connect and introduce phis.
        UnmodifiableEconomicMap<MethodDuplicationAnchorNode, EconomicSet<Node>> aliveAtAnchor = calculateLiveLocals(graph, debug, cfgAfterPeeling, reverseDuplicationMap);
        SchedulePhase.runWithoutContextOptimizations(graph, SchedulePhase.SchedulingStrategy.LATEST, true);
        debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After calculating live locals");
        StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        IntroduceMergesAndPhisClosure closure = new IntroduceMergesAndPhisClosure(debug, schedule, reverseDuplicationMap, duplicationMap, aliveAtAnchor, opaqueLogicNode);
        HIRBlock firstDuplicatedBlock = schedule.getCFG().getStartBlock().getSuccessorAt(1);
        ReentrantBlockIterator.apply(closure, firstDuplicatedBlock);
        for (MethodDuplicationAnchorNode anchorNode : graph.getNodes(MethodDuplicationAnchorNode.TYPE)) {
            GraphUtil.unlinkFixedNode(anchorNode);
            anchorNode.safeDelete();
        }

        debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After introducing merges and phis");

        /*
         * The graph has at this point "dead phis" that were optimistically introduced but will be
         * canonicalized. As we iterate over the duplicated graph, we are introducing phis for all
         * replaced values. While we know at anchor points which nodes are alive, we do not have the
         * same information for merge points. This means that once a value is replaced, there could
         * be phis introduced down all succeeding merge points, even if the value is no longer live
         * at this point. TODO: Improve the algorithm to avoid introducing those phis.
         */

        // Phase 5: Clean up.
        opaqueLogicNode.replaceAtUsages(opaqueLogicNode.value());

        for (LoopBeginAnchorNode loopBeginAnchorNode : graph.getNodes(LoopBeginAnchorNode.TYPE)) {
            for (OpaqueNode opaqueNode : loopBeginAnchorNode.values()) {
                opaqueNode.remove();
            }
            graph.removeFixed(loopBeginAnchorNode);
        }
        canonicalizer.apply(graph, context);
        debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After removing anchors and opaque nodes");
        assert GraphOrder.assertSchedulableGraph(graph);
    }

    /**
     * Calculate the set of compiler nodes that are live at each anchor point.
     */
    private static UnmodifiableEconomicMap<MethodDuplicationAnchorNode, EconomicSet<Node>> calculateLiveLocals(StructuredGraph graph, DebugContext debug, ControlFlowGraph cfg,
                    EconomicMap<Node, Node> reverseDuplicationMap) {
        SchedulePhase.runWithoutContextOptimizations(graph, SchedulePhase.SchedulingStrategy.LATEST, cfg, true);
        StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        CollectLiveLocalsClosure closure = new CollectLiveLocalsClosure(debug, schedule, reverseDuplicationMap);
        ReentrantBlockIterator.apply(closure, schedule.getCFG().getStartBlock().getSuccessorAt(1));
        UnmodifiableEconomicMap<MethodDuplicationAnchorNode, EconomicSet<Node>> aliveAtAnchor = closure.aliveAtAnchor();

        // Post-processing for special handling of frame states at anchor nodes.
        for (MethodDuplicationAnchorNode anchor : aliveAtAnchor.getKeys()) {
            MethodDuplicationAnchorNode originalAnchor = (MethodDuplicationAnchorNode) reverseDuplicationMap.get(anchor);

            // Make sure to visit every input flowing into the state after of the anchor node and
            // mark it live.
            originalAnchor.stateAfter().applyToNonVirtual((from, p) -> {
                if (!p.get(from).getNodeClass().isLeafNode()) {
                    aliveAtAnchor.get(anchor).add(p.get(from));
                }
            });

            if (debug.isLogEnabled(DebugContext.VERBOSE_LEVEL)) {
                debug.log(DebugContext.VERBOSE_LEVEL, "Alive at anchor %s", anchor);
                for (Node n : aliveAtAnchor.get(anchor)) {
                    debug.log(DebugContext.VERBOSE_LEVEL, "Alive node %s", n);
                }
            }
        }
        return aliveAtAnchor;
    }

    /**
     * Peel loops until every duplication anchor entry is outside any loop. This is necessary to
     * keep the invariant that every loop can only have a single entry point.
     */
    private static void peelLoops(StructuredGraph graph, CoreProviders context, List<MethodDuplicationAnchorNode> duplicationAnchors, EconomicMap<Node, Node> duplicationMap,
                    EconomicMap<Node, Node> reverseDuplicationMap, int maxSize) {

        int iteration = 0;
        DebugContext debug = graph.getDebug();
        outer: while (iteration < MAX_PEELING_ITERATIONS) {
            iteration++;
            debug.log(DebugContext.INFO_LEVEL, "Peeling round %d, before graph size is %d", iteration, graph.getNodeCount());

            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            EconomicSet<CFGLoop<HIRBlock>> loopsToPeel = EconomicSet.create();

            /*
             * Always peel inner loops before outer loops => create a set of loops that are outer
             * loops of currently peeled loops and mark them to delay their own peeling.
             */
            EconomicSet<CFGLoop<HIRBlock>> mustDelayPeeling = EconomicSet.create();

            // Iterate over all duplication anchor entries and mark the loops they are in for
            // peeling.
            for (MethodDuplicationAnchorNode anchorNode : duplicationAnchors) {
                MethodDuplicationAnchorNode duplicatedAnchor = (MethodDuplicationAnchorNode) duplicationMap.get(anchorNode);
                CFGLoop<HIRBlock> loop = loopsData.getCFG().getNodeToBlock().get(duplicatedAnchor).getLoop();
                if (loop != null) {
                    loopsToPeel.add(loop);

                    while (loop.getParent() != null) {
                        debug.log(DebugContext.VERY_DETAILED_LEVEL, "Found a method duplication anchor in nested loop %s, outer loop %s", loop.getHeader().getBeginNode(),
                                        loop.getParent().getHeader().getBeginNode());
                        loop = loop.getParent();
                        mustDelayPeeling.add(loop);
                    }
                }
            }

            loopsToPeel.removeAll(mustDelayPeeling);

            for (CFGLoop<HIRBlock> loop : loopsToPeel) {
                int estimatedNewNodes = loopsData.loop(loop).inside().nodes().count();

                // Check whether the graph became too big and in this case abort the rest of the
                // peeling.
                if (estimatedNewNodes + graph.getNodeCount() > maxSize) {
                    debug.log(DebugContext.INFO_LEVEL, "Bailing out as the graph gets too big, initial size %d, current size %d", maxSize, graph.getNodeCount());
                    break outer;
                }
                peelLoop(debug, duplicationMap, reverseDuplicationMap, loopsData, loop);
            }

            if (mustDelayPeeling.isEmpty()) {
                // No more iterations required as all duplication anchors must be out of loops by
                // now.
                break;
            }
        }

        /*
         * Discard anchors that are remaining in loops, we can no longer use them. This can happen
         * if we had to bail out of the previous loop either because of too many iterations or
         * because the graph got too big.
         */
        LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
        for (MethodDuplicationAnchorNode anchorNode : duplicationAnchors) {
            MethodDuplicationAnchorNode duplicatedAnchor = (MethodDuplicationAnchorNode) duplicationMap.get(anchorNode);
            CFGLoop<HIRBlock> curLoop = loopsData.getCFG().getNodeToBlock().get(duplicatedAnchor).getLoop();
            if (curLoop != null) {
                graph.removeFixed(duplicatedAnchor);
            }
        }

        graph.getDebug().log(DebugContext.INFO_LEVEL, "Final graph size is %d", graph.getNodeCount());
    }

    /**
     * Method for peeling a single loop and updating the duplication map and reverse duplication map
     * data structures accordingly.
     */
    private static void peelLoop(DebugContext debug, EconomicMap<Node, Node> duplicationMap, EconomicMap<Node, Node> reverseDuplicationMap, LoopsData loopsData, CFGLoop<HIRBlock> loop) {
        debug.log(DebugContext.INFO_LEVEL, "Peeling loop %s", loop);

        StructuredGraph graph = loop.getHeader().getBeginNode().graph();
        LoopFragmentInside inside = LoopTransformations.peel(loopsData.loop(loop));

        // Special processing for phis introduced by the peeling to update their duplication map and
        // reverse duplication map entries.
        for (PhiNode phiNode : inside.getIntroducedPhis()) {
            Node value = phiNode.valueAt(0);
            GraalError.guarantee(value instanceof ProxyNode, "Must be a proxy node %s %s", phiNode, value);
            Node originalProxy = reverseDuplicationMap.get(value);
            GraalError.guarantee(originalProxy != null, "Original proxy must not be null %s", phiNode);
            reverseDuplicationMap.put(phiNode, originalProxy);
            duplicationMap.put(originalProxy, phiNode);
        }

        // Special processing for merges introduced by the peeling to update their duplication map
        // and reverse duplication map entries.
        EconomicMap<Node, Node> delayedDuplicationMapUpdate = EconomicMap.create();
        for (MergeNode mergeNode : inside.getIntroducedMerges()) {
            Node nonPeeledPredecessor = mergeNode.forwardEndAt(0).predecessor();
            GraalError.guarantee(nonPeeledPredecessor instanceof LoopExitNode, "Must be a loop exit node %s %s", nonPeeledPredecessor, mergeNode);
            Node originalLoopExit = reverseDuplicationMap.get(nonPeeledPredecessor);
            GraalError.guarantee(originalLoopExit != null, "Original loop exit must not be null %s", nonPeeledPredecessor, mergeNode);
            reverseDuplicationMap.put(mergeNode, originalLoopExit);
            delayedDuplicationMapUpdate.put(originalLoopExit, mergeNode);
        }

        // Register other new peeled nodes correctly in the reverse duplication map.
        UnmodifiableMapCursor<Node, Node> cursor = inside.duplicationMap().getEntries();
        while (cursor.advance()) {
            if (cursor.getKey() instanceof VirtualState) {
                // No tracking of virtual state necessary.
            } else {
                Node originalNode = reverseDuplicationMap.get(cursor.getKey());
                if (originalNode != null) {
                    Node newNodeAfterPeeling = cursor.getValue();
                    Node nodeBeforePeeling = cursor.getKey();
                    if (newNodeAfterPeeling.isAlive()) {
                        assert nodeBeforePeeling != newNodeAfterPeeling : nodeBeforePeeling + "==" + newNodeAfterPeeling;
                        reverseDuplicationMap.put(newNodeAfterPeeling, originalNode);
                        duplicationMap.put(originalNode, newNodeAfterPeeling);
                        reverseDuplicationMap.removeKey(nodeBeforePeeling);
                        GraalError.guarantee(newNodeAfterPeeling.isAlive(), "Node in the duplication map must be alive %s", newNodeAfterPeeling);

                        // After peeling the anchor node, we don't need the anchor node in the
                        // original loop anymore.
                        // The whole goal of the peeling is to move the anchor node out of loops.
                        if (nodeBeforePeeling instanceof MethodDuplicationAnchorNode anchorNodeBeforePeeling) {
                            GraphUtil.removeFixedWithUnusedInputs(anchorNodeBeforePeeling);
                        }
                    } else {
                        // The new node is not alive after peeling and can be discarded.
                    }
                } else {
                    // This case can happen when nested loops are peeled.
                }
            }
        }

        cursor = delayedDuplicationMapUpdate.getEntries();
        while (cursor.advance()) {
            duplicationMap.put(cursor.getKey(), cursor.getValue());
        }

        debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After peeling loop %s", loop.getHeader().getLoop().getHeader().getBeginNode());
    }

    /**
     * Utility method for calculating the reverse of a map.
     */
    private static EconomicMap<Node, Node> calculateReverseMap(UnmodifiableEconomicMap<Node, Node> map) {
        EconomicMap<Node, Node> reverseMap = EconomicMap.create();
        UnmodifiableMapCursor<Node, Node> cursor = map.getEntries();
        while (cursor.advance()) {
            reverseMap.put(cursor.getValue(), cursor.getKey());
        }
        return reverseMap;
    }

    /**
     * Duplicate the whole method and introduce an if node with an opaque logic node as input at the
     * start of the method to dispatch between the two versions.
     */
    private static EconomicMap<Node, Node> duplicateMethod(StructuredGraph graph, List<Node> nodesToDuplicate, OpaqueLogicNode opaqueLogicNode) {
        StartNode start = graph.start();
        EconomicMap<Node, Node> duplicationMap = graph.addDuplicates(nodesToDuplicate, graph, nodesToDuplicate.size(), (EconomicMap<Node, Node>) null);
        FixedNode startNext = start.next();
        start.setNext(null);
        IfNode ifNode = graph.addWithoutUniqueWithInputs(new IfNode(opaqueLogicNode, startNext, (FixedNode) duplicationMap.get(startNext), ProfileData.BranchProbabilityData.unknown()));
        start.setNext(ifNode);
        return duplicationMap;
    }

    /**
     * Collect the nodes that need to be duplicated in this method. This is all nodes except for
     * floating leaf nodes and the start node. Also, returns null if a control flow anchored node is
     * found as in this case the whole duplication cannot be performed at all.
     */
    private static List<Node> collectNodesToDuplicate(StructuredGraph graph) {
        List<Node> nodesToDuplicate = new ArrayList<>();
        for (Node n : graph.getNodes()) {
            if (skipDuplication(n)) {
                // Do not duplicate.
            } else {
                if (n instanceof ControlFlowAnchored) {
                    graph.getDebug().log("Found unexpected control flow anchored node: %s", n);
                    // Cannot perform this optimization.
                    return null;
                }
                // Duplicate everything else
                nodesToDuplicate.add(n);
            }
        }
        return nodesToDuplicate;
    }

    // Do not duplicate start, or leaf nodes including parameter nodes.

    /**
     * Checks whether this is a node for which the duplication is skipped. Specifically floating
     * leaf nodes and the start node.
     */
    private static boolean skipDuplication(Node n) {
        return (!(n instanceof FixedNode) && n.getNodeClass().isLeafNode()) || (n instanceof StartNode);
    }

    /**
     * Insert duplication anchors at the points where the split off to the duplicated version of the
     * method should happen.
     */
    private static List<MethodDuplicationAnchorNode> insertDuplicationAnchors(StructuredGraph graph, List<HIRBlock> coldSplitReentryBlocks) {

        EconomicSet<CFGLoop<HIRBlock>> peelingCandidates = EconomicSet.create();
        List<MethodDuplicationAnchorNode> duplicationAnchors = new ArrayList<>();
        int start = Math.max(0, MinDuplicationAnchor.getValue(graph.getOptions()) - 1);
        int maxCount = Math.min(coldSplitReentryBlocks.size(), MaxDuplicationAnchor.getValue(graph.getOptions()));
        for (int i = start; i < maxCount; ++i) {
            HIRBlock block = coldSplitReentryBlocks.get(i);
            AbstractBeginNode beginNode = block.getBeginNode();
            FrameState state = GraphUtil.findLastFrameState(beginNode);
            MethodDuplicationAnchorNode anchor = graph.add(new MethodDuplicationAnchorNode(i + 1));
            anchor.setStateAfter(state.duplicateWithVirtualState());
            beginNode.graph().addAfterFixed(beginNode, anchor);
            duplicationAnchors.add(anchor);

            CFGLoop<HIRBlock> loop = block.getLoop();
            while (loop != null) {
                peelingCandidates.add(loop);
                loop = loop.getParent();
            }
        }

        // Introduce proxy points for loops that need to be peeled.
        for (CFGLoop<HIRBlock> loop : peelingCandidates) {
            LoopBeginNode loopBeginNode = (LoopBeginNode) loop.getHeader().getBeginNode();
            LoopBeginAnchorNode loopBeginAnchorNode = graph.addWithoutUnique(new LoopBeginAnchorNode());
            graph.addAfterFixed(loopBeginNode, loopBeginAnchorNode);
            ArrayList<OpaqueNode> opaqueNodes = new ArrayList<>();
            for (PhiNode phiNode : loopBeginNode.phis()) {
                OpaqueNode opaqueNode;
                if (phiNode instanceof ValuePhiNode) {
                    opaqueNode = graph.addWithoutUnique(new OpaqueValueNode(phiNode));
                } else if (phiNode instanceof GuardPhiNode) {
                    opaqueNode = graph.addWithoutUnique(new OpaqueGuardNode(phiNode));
                } else {
                    throw GraalError.shouldNotReachHere(String.format("Only value or guard phis are supported by this phase %s", phiNode));
                }
                opaqueNodes.add(opaqueNode);
                phiNode.replaceAtMatchingUsages(opaqueNode, n -> n != opaqueNode);
                loopBeginAnchorNode.addValue(opaqueNode);

            }

            for (OpaqueNode opaqueNode : opaqueNodes) {
                opaqueNode.setAnchor(loopBeginNode);
            }

            // Make sure that there are no opaque nodes when phis are making direct cycles (keep the
            // opaque nodes in case of cycles via back edges).
            for (PhiNode phiNode : loopBeginNode.phis()) {
                Node value = phiNode.valueAt(0);
                if (value instanceof OpaqueNode opaqueNode) {
                    ValueNode opaqueValue = opaqueNode.getValue();
                    if (opaqueValue instanceof PhiNode referencedPhiNode) {
                        if (referencedPhiNode.merge() == loopBeginNode) {
                            referencedPhiNode.setValueAt(0, opaqueValue);
                        }
                    }
                }
            }

            // Make sure that we are really only modifying the state of the loop begin and no other
            // node references his state after or any of its input states.
            loopBeginNode.setStateAfter(loopBeginNode.stateAfter().duplicateWithVirtualState());

            // Remove opaque proxy from non-virtual inputs of the loop begin state after.
            loopBeginNode.stateAfter().applyToNonVirtual((from, p) -> {
                Node value = p.get(from);
                if (value instanceof OpaqueNode opaqueNode) {
                    ValueNode opaqueValue = opaqueNode.getValue();
                    if (opaqueValue instanceof PhiNode phiNode) {
                        if (phiNode.merge() == loopBeginNode) {
                            p.set(from, opaqueValue);
                        }
                    }
                }
            });
        }

        return duplicationAnchors;
    }

    /**
     * State used during introduction of merges and phis. It contains a mapping to identify nodes
     * that need to be replaced with new values. There is separate bookkeeping for value, guard, and
     * anchor replacements as a single node could be used as multiple of those kinds of dependencies
     * and require a different replacement for each.
     */
    private static class IntroduceMergesAndPhisState {
        // Key is original node, replaced is phi node or duplicated node
        private final EconomicMap<Node, Node> replacedNodesValue = EconomicMap.create();
        private final EconomicMap<Node, Node> replacedNodesGuard = EconomicMap.create();
        private final EconomicMap<Node, Node> replacedNodesAnchor = EconomicMap.create();

        IntroduceMergesAndPhisState() {
        }

        IntroduceMergesAndPhisState(IntroduceMergesAndPhisState other) {
            replacedNodesValue.putAll(other.replacedNodesValue);
            replacedNodesGuard.putAll(other.replacedNodesGuard);
            replacedNodesAnchor.putAll(other.replacedNodesAnchor);
        }

        void registerReplacedNodeValue(Node originalNode, Node replacement) {
            GraalError.guarantee(!originalNode.getNodeClass().isLeafNode(), "Never need to register a leaf node! %s %s", originalNode, replacement);
            GraalError.guarantee(originalNode instanceof ValueNode && replacement instanceof ValueNode, "Both nodes must be value nodes %s %s", originalNode, replacement);
            GraalError.guarantee(((ValueNode) originalNode).stamp(NodeView.DEFAULT) != StampFactory.forVoid(), "Must not have void stamp %s (%s)", originalNode, replacement);
            GraalError.guarantee(((ValueNode) replacement).stamp(NodeView.DEFAULT) != StampFactory.forVoid(), "Must not have void stamp %s (%s)", originalNode, replacement);
            replacedNodesValue.put(originalNode, replacement);
        }

        public void registerReplacedNodeGuard(Node originalNode, Node replacement) {
            replacedNodesGuard.put(originalNode, replacement);
        }

        public void registerReplacedNodeAnchor(Node originalNode, Node replacement) {
            replacedNodesAnchor.put(originalNode, replacement);
        }

        public EconomicMap<Node, Node> replacedNodesValue() {
            return replacedNodesValue;
        }

        public EconomicMap<Node, Node> replacedNodesGuard() {
            return replacedNodesGuard;
        }

        public EconomicMap<Node, Node> replacedNodesAnchor() {
            return replacedNodesAnchor;
        }

        public Node lookupReplacedInput(InputType inputType, Node originalNodeOfInput) {
            return lookupReplacedInput(inputType, originalNodeOfInput, null);
        }

        public Node lookupReplacedInput(InputType inputType, Node originalNodeOfInput, Node defaultValue) {
            if (inputType == InputType.Value) {
                return replacedNodesValue().get(originalNodeOfInput, defaultValue);
            } else if (inputType == InputType.Guard) {
                return replacedNodesGuard().get(originalNodeOfInput, defaultValue);
            } else if (inputType == InputType.Anchor) {
                return replacedNodesAnchor().get(originalNodeOfInput, defaultValue);
            }
            return defaultValue;
        }
    }

    private static class IntroduceMergesAndPhisClosure extends ReentrantBlockIterator.BlockIteratorClosure<IntroduceMergesAndPhisState> {

        private final DebugContext debug;
        private final StructuredGraph.ScheduleResult schedule;
        private final EconomicMap<Node, Node> reverseDuplicationMap;
        private final UnmodifiableEconomicMap<MethodDuplicationAnchorNode, EconomicSet<Node>> aliveAtAnchor;
        private final EconomicMap<Node, Node> duplicationMap;
        private final OpaqueLogicNode opaqueLogicNode;

        IntroduceMergesAndPhisClosure(DebugContext debug, StructuredGraph.ScheduleResult schedule,
                        EconomicMap<Node, Node> reverseDuplicationMap, EconomicMap<Node, Node> duplicationMap,
                        UnmodifiableEconomicMap<MethodDuplicationAnchorNode, EconomicSet<Node>> aliveAtAnchor,
                        OpaqueLogicNode opaqueLogicNode) {
            this.debug = debug;
            this.schedule = schedule;
            this.reverseDuplicationMap = reverseDuplicationMap;
            this.aliveAtAnchor = aliveAtAnchor;
            this.duplicationMap = duplicationMap;
            this.opaqueLogicNode = opaqueLogicNode;
        }

        @Override
        protected IntroduceMergesAndPhisState getInitialState() {
            return new IntroduceMergesAndPhisState();
        }

        @Override
        protected IntroduceMergesAndPhisState processBlock(HIRBlock block, IntroduceMergesAndPhisState currentState) {
            debug.log(DebugContext.VERY_DETAILED_LEVEL, "Processing block %s (%s)", block, block.getBeginNode());
            AbstractBeginNode beginNode = block.getBeginNode();
            StructuredGraph graph = beginNode.graph();

            for (Node n : schedule.getBlockToNodesMap().get(block)) {

                if (n instanceof LoopExitNode) {
                    for (Node proxy : ((LoopExitNode) n).proxies()) {
                        processNode(proxy, currentState);
                    }
                }

                if (n instanceof AbstractEndNode endNode) {
                    processEndNode(currentState, endNode);
                }

                processNode(n, currentState);

                if (n instanceof MethodDuplicationAnchorNode anchor) {
                    processAnchor(currentState, anchor, graph);
                }
            }

            return currentState;
        }

        /**
         * Process an end node. Need to update the values flowing from this end node into the phis
         * of the succeeding merge node.
         */
        private void processEndNode(IntroduceMergesAndPhisState currentState, AbstractEndNode endNode) {
            AbstractMergeNode succeedingMerge = endNode.merge();
            debug.log(DebugContext.VERY_DETAILED_LEVEL, "Updating phis for merge %s coming from %s", succeedingMerge, endNode);
            int phiIndex = succeedingMerge.phiPredecessorIndex(endNode);
            for (PhiNode phi : succeedingMerge.phis()) {
                ValueNode inputValue = phi.valueAt(phiIndex);
                ValueNode inputValueOriginal = (ValueNode) reverseDuplicationMap.get(phi.valueAt(phiIndex));
                if (inputValueOriginal != null) {
                    ValueNode newInputValue = (ValueNode) (phi instanceof GuardPhiNode ? currentState.replacedNodesGuard() : currentState.replacedNodesValue()).get(inputValueOriginal);
                    if (newInputValue != null) {
                        debug.log(DebugContext.VERY_DETAILED_LEVEL, "Replacing %s with %s", inputValue, newInputValue);
                        phi.setValueAt(phiIndex, newInputValue);
                    }
                }
            }
        }

        /**
         * Process a duplication anchor by introducing now the control flow merge between the
         * original part and the duplicated part.
         */
        private void processAnchor(IntroduceMergesAndPhisState currentState, MethodDuplicationAnchorNode anchor, StructuredGraph graph) {
            MethodDuplicationAnchorNode originalAnchor = (MethodDuplicationAnchorNode) reverseDuplicationMap.get(anchor);

            // Create merge point and wire it up with the control flow from original and
            // duplicated graph.
            MergeNode merge = graph.add(new MergeNode());
            EndNode originalEndNode = graph.add(new EndNode());
            EndNode duplicatedEnd = graph.add(new EndNode());
            merge.addForwardEnd(originalEndNode);
            merge.addForwardEnd(duplicatedEnd);

            // Create loop exit nodes at original end.
            HIRBlock originalAnchorBlock = schedule.getNodeToBlockMap().get(originalAnchor);
            FrameState frameState = originalAnchor.stateAfter();
            EconomicSet<Node> aliveAtCurrentAnchor = aliveAtAnchor.get(anchor);
            List<LoopExitNode> loopExits = new ArrayList<>();
            CFGLoop<HIRBlock> loop = originalAnchorBlock.getLoop();
            IntroduceMergesAndPhisState loopExitState = introduceLoopExits(loop, graph, loopExits, aliveAtCurrentAnchor, frameState, originalEndNode);

            // Introduce phis at this merge from original and duplicated method parts at the anchor.
            for (Node aliveNode : aliveAtCurrentAnchor) {

                Node incomingValue = duplicationMap.get(aliveNode);

                // Handle value inputs.
                if (aliveNode.isAllowedUsageType(InputType.Value)) {
                    incomingValue = currentState.replacedNodesValue().get(aliveNode, incomingValue);
                    ValueNode aliveNodeValue = (ValueNode) loopExitState.replacedNodesValue.get(aliveNode, aliveNode);
                    ValuePhiNode valuePhiNode = graph.addWithoutUnique(new ValuePhiNode(((ValueNode) aliveNode).stamp(NodeView.DEFAULT).unrestricted(), merge, aliveNodeValue,
                                    (ValueNode) incomingValue));
                    debug.log(DebugContext.VERY_DETAILED_LEVEL, "Registering replaced node (value) %s: %s", aliveNode, valuePhiNode);
                    currentState.registerReplacedNodeValue(aliveNode, valuePhiNode);
                }

                // Handle guard inputs.
                if (aliveNode.isAllowedUsageType(InputType.Guard)) {
                    incomingValue = currentState.replacedNodesGuard().get(aliveNode, incomingValue);
                    GuardingNode aliveNodeGuard = (GuardingNode) loopExitState.replacedNodesGuard.get(aliveNode, aliveNode);
                    GuardPhiNode guardPhiNode = graph.addWithoutUnique(new GuardPhiNode(merge, (ValueNode) aliveNodeGuard, (ValueNode) incomingValue));
                    debug.log(DebugContext.VERY_DETAILED_LEVEL, "Registering replaced node (guard) %s: %s", aliveNode, guardPhiNode);
                    currentState.registerReplacedNodeGuard(aliveNode, guardPhiNode);
                }

                // Handle anchor inputs.
                if (aliveNode.isAllowedUsageType(InputType.Anchor)) {
                    debug.log(DebugContext.VERY_DETAILED_LEVEL, "Registering replaced node (anchor) %s: %s", aliveNode, merge);
                    currentState.registerReplacedNodeAnchor(aliveNode, merge);
                }
            }

            // Introduce a placeholder if node at the original anchor such that the control flow can
            // be later
            // cleaned up, and we don't change the graph structure right now.
            FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) originalAnchor.predecessor();
            fixedWithNextNode.setNext(null);
            IfNode ifNode = graph.add(new IfNode(opaqueLogicNode, (loopExits.isEmpty() ? originalEndNode : loopExits.get(0)), originalAnchor, ProfileData.BranchProbabilityData.unknown()));
            fixedWithNextNode.setNext(ifNode);

            // Special case handling of the frame state at the merge (which is a duplicate of the
            // frame state
            // of the duplication anchor). All non-virtual inputs need to be replaced based on the
            // current
            // state of the replacement maps.
            FrameState frameStateAtMerge = frameState.duplicateWithVirtualState();
            frameStateAtMerge.applyToNonVirtual((from, p) -> {
                Node inputInOriginal = p.get(from);
                Node duplicatedNode = duplicationMap.get(inputInOriginal);
                if (duplicatedNode == null) {
                    // The input has not been duplicated at all, so no need to change.
                    return;
                }
                Node newInput = currentState.lookupReplacedInput(p.getInputType(), inputInOriginal, duplicationMap.get(inputInOriginal));
                p.set(from, newInput);
            });
            merge.setStateAfter(frameStateAtMerge);
            anchor.replaceAtPredecessor(duplicatedEnd);
            merge.setNext(anchor);
            processNode(frameStateAtMerge, currentState);
        }

        /**
         * Introduce loop exits and associated proxy nodes.
         */
        private static IntroduceMergesAndPhisState introduceLoopExits(CFGLoop<HIRBlock> initialLoop, StructuredGraph graph, List<LoopExitNode> loopExits, EconomicSet<Node> aliveAtCurrentAnchor,
                        FrameState frameState, EndNode originalEndNode) {
            IntroduceMergesAndPhisState loopExitState = new IntroduceMergesAndPhisState();
            CFGLoop<HIRBlock> loop = initialLoop;
            while (loop != null) {
                LoopExitNode loopExit = graph.add(new LoopExitNode((LoopBeginNode) loop.getHeader().getBeginNode()));
                loopExits.add(loopExit);

                for (Node aliveNode : aliveAtCurrentAnchor) {
                    if (aliveNode instanceof ValueNode aliveValueNode) {
                        if (aliveNode.isAllowedUsageType(InputType.Value)) {
                            ValueProxyNode valueProxyNode = graph.addWithoutUnique(new ValueProxyNode((ValueNode) loopExitState.replacedNodesValue().get(aliveValueNode, aliveValueNode), loopExit));
                            loopExitState.registerReplacedNodeValue(aliveValueNode, valueProxyNode);
                        }

                        if (aliveNode.isAllowedUsageType(InputType.Guard)) {
                            GuardProxyNode guardProxyNode = graph.addWithoutUnique(new GuardProxyNode((GuardingNode) loopExitState.replacedNodesGuard().get(aliveValueNode, aliveValueNode), loopExit));
                            loopExitState.registerReplacedNodeGuard(aliveValueNode, guardProxyNode);
                        }
                    }
                }

                FrameState newState = frameState.duplicateWithVirtualState();
                newState.applyToNonVirtual((from, p) -> {
                    Node value = p.get(from);
                    if (!value.getNodeClass().isLeafNode() && (!(value instanceof StartNode))) {
                        Node newValue = loopExitState.lookupReplacedInput(p.getInputType(), value, value);
                        p.set(from, newValue);
                    }
                });
                loopExit.setStateAfter(newState);
                loop = loop.getParent();
            }

            for (int i = 0; i < loopExits.size(); ++i) {
                if (i == loopExits.size() - 1) {
                    loopExits.get(i).setNext(originalEndNode);
                } else {
                    loopExits.get(i).setNext(loopExits.get(i + 1));
                }
            }
            return loopExitState;
        }

        /**
         * Process the inputs of the node to modified based on the state of the replacement maps.
         */
        private void processNode(Node n, IntroduceMergesAndPhisState currentState) {
            debug.log(DebugContext.VERY_DETAILED_LEVEL, "Processing inputs of node %s", n);
            for (Position p : n.inputPositions()) {
                processNodePosition(n, currentState, p);
            }
        }

        /**
         * Process the input at the specific position to be modified based on the state of the
         * replacement maps.
         */
        private void processNodePosition(Node n, IntroduceMergesAndPhisState currentState, Position p) {
            Node currentInputValue = p.get(n);
            if (currentInputValue != null) {
                Node originalNodeOfInput = reverseDuplicationMap.get(currentInputValue);
                if (originalNodeOfInput != null) {
                    Node newInputValue = currentState.lookupReplacedInput(p.getInputType(), originalNodeOfInput);
                    if (newInputValue != null) {
                        debug.log(DebugContext.VERY_DETAILED_LEVEL, "Changing input value of %s from %s to %s", n, currentInputValue, newInputValue);
                        p.set(n, newInputValue);
                    }
                }
            }
        }

        /**
         * Process the merge and create phis based on the state of the replacement maps.
         */
        @Override
        protected IntroduceMergesAndPhisState merge(HIRBlock merge, List<IntroduceMergesAndPhisState> states) {
            AbstractMergeNode mergeNode = (AbstractMergeNode) merge.getBeginNode();
            StructuredGraph graph = mergeNode.graph();
            IntroduceMergesAndPhisState newState = new IntroduceMergesAndPhisState();
            for (IntroduceMergesAndPhisState incomingState : states) {
                MapCursor<Node, Node> cursor = incomingState.replacedNodesValue.getEntries();
                while (cursor.advance()) {
                    Node n = cursor.getKey();
                    if (newState.replacedNodesValue().containsKey(n)) {
                        // This node has already been processed.
                        continue;
                    }

                    HIRBlock definitionBlock = schedule.blockFor(duplicationMap.get(n));
                    if (definitionBlock.dominates(merge)) {
                        ValueNode singleValue = (ValueNode) cursor.getValue();
                        ValueNode[] values = new ValueNode[states.size()];
                        for (int i = 0; i < states.size(); ++i) {
                            IntroduceMergesAndPhisState curState = states.get(i);
                            ValueNode curMapping;
                            if (curState == incomingState) {
                                curMapping = (ValueNode) cursor.getValue();
                            } else {
                                curMapping = (ValueNode) curState.replacedNodesValue().get(cursor.getKey());
                                if (curMapping == null) {
                                    curMapping = (ValueNode) duplicationMap.get(n);
                                }
                            }
                            GraalError.guarantee(curMapping != null, "Unexpected null mapping: %s, %s", n, duplicationMap.get(n));
                            values[i] = curMapping;
                            if (curMapping != singleValue) {
                                singleValue = null;
                            }
                        }

                        if (singleValue != null) {
                            // All incoming states agree => no phi necessary.
                            newState.registerReplacedNodeValue(n, singleValue);
                        } else {
                            Stamp stamp = values[0].stamp(NodeView.DEFAULT).unrestricted();
                            GraalError.guarantee(stamp != StampFactory.forVoid(), "Void stamp is illegal here %s %s %s", mergeNode, n, values[0]);
                            ValuePhiNode phiNode = graph.addWithoutUnique(new ValuePhiNode(stamp, mergeNode, values));
                            newState.registerReplacedNodeValue(n, phiNode);
                            debug.log(DebugContext.VERY_DETAILED_LEVEL, "Registering new value phi replacement for node %s: %s", n, phiNode);
                        }
                    }
                }

                cursor = incomingState.replacedNodesGuard.getEntries();
                while (cursor.advance()) {
                    Node n = cursor.getKey();
                    if (newState.replacedNodesGuard().containsKey(n)) {
                        // This node has already been processed.
                        continue;
                    }

                    HIRBlock definitionBlock = schedule.blockFor(duplicationMap.get(n));
                    if (definitionBlock.dominates(merge)) {
                        ValueNode singleValue = (ValueNode) cursor.getValue();
                        ValueNode[] values = new ValueNode[states.size()];
                        for (int i = 0; i < states.size(); ++i) {
                            IntroduceMergesAndPhisState curState = states.get(i);
                            ValueNode curMapping;
                            if (curState == incomingState) {
                                curMapping = (ValueNode) cursor.getValue();
                            } else {
                                curMapping = (ValueNode) curState.replacedNodesGuard().get(cursor.getKey());
                                if (curMapping == null) {
                                    curMapping = (ValueNode) duplicationMap.get(n);
                                }
                            }
                            values[i] = curMapping;
                            if (curMapping != singleValue) {
                                singleValue = null;
                            }
                        }

                        if (singleValue != null) {
                            // All incoming states agree => no phi necessary.
                            newState.registerReplacedNodeGuard(n, singleValue);
                        } else {
                            GuardPhiNode phiNode = graph.addWithoutUnique(new GuardPhiNode(mergeNode, values));
                            newState.registerReplacedNodeGuard(n, phiNode);
                            debug.log(DebugContext.VERY_DETAILED_LEVEL, "Registering new guard phi replacement for node %s: %s", n, phiNode);
                        }
                    }
                }
                cursor = incomingState.replacedNodesAnchor.getEntries();
                while (cursor.advance()) {
                    Node n = cursor.getKey();
                    if (newState.replacedNodesAnchor().containsKey(n)) {
                        // This node has already been processed.
                        continue;
                    }

                    Node anchorNode = cursor.getValue();
                    for (IntroduceMergesAndPhisState curState : states) {
                        Node curMapping;
                        if (curState == incomingState) {
                            curMapping = cursor.getValue();
                        } else {
                            curMapping = curState.replacedNodesAnchor().get(cursor.getKey());
                            if (curMapping == null) {
                                curMapping = duplicationMap.get(n);
                            }
                        }
                        if (anchorNode != curMapping) {
                            anchorNode = mergeNode;
                            break;
                        }
                    }
                    newState.registerReplacedNodeAnchor(n, anchorNode);
                    debug.log(DebugContext.VERY_DETAILED_LEVEL, "Registering new anchor replacement for node %s: %s", n, mergeNode);
                }
            }

            return newState;
        }

        @Override
        protected IntroduceMergesAndPhisState cloneState(IntroduceMergesAndPhisState oldState) {
            return new IntroduceMergesAndPhisState(oldState);
        }
    }

    private static class CollectLiveLocalsState {

        private final EconomicSet<MethodDuplicationAnchorNode> activeAnchors = EconomicSet.create();

        CollectLiveLocalsState() {
        }

        CollectLiveLocalsState(CollectLiveLocalsState other) {
            activeAnchors.addAll(other.activeAnchors);
        }

        void registerAnchor(MethodDuplicationAnchorNode anchor) {
            activeAnchors.add(anchor);
        }

        void mergeWith(CollectLiveLocalsState state) {
            activeAnchors.addAll(state.activeAnchors);
        }

        UnmodifiableEconomicSet<MethodDuplicationAnchorNode> activeAnchors() {
            return activeAnchors;
        }

        boolean hasActiveAnchors() {
            return !activeAnchors.isEmpty();
        }
    }

    private static class CollectLiveLocalsClosure extends ReentrantBlockIterator.BlockIteratorClosure<CollectLiveLocalsState> {

        private final DebugContext debug;
        private final StructuredGraph.ScheduleResult schedule;
        private final EconomicMap<MethodDuplicationAnchorNode, EconomicSet<Node>> aliveAtAnchor = EconomicMap.create();
        private final EconomicMap<Node, Node> reverseDuplicationMap;

        CollectLiveLocalsClosure(DebugContext debug, StructuredGraph.ScheduleResult schedule, EconomicMap<Node, Node> reverseDuplicationMap) {
            this.debug = debug;
            this.schedule = schedule;
            this.reverseDuplicationMap = reverseDuplicationMap;
        }

        @Override
        protected CollectLiveLocalsState getInitialState() {
            return new CollectLiveLocalsState();
        }

        @Override
        protected CollectLiveLocalsState processBlock(HIRBlock block, CollectLiveLocalsState currentState) {
            debug.log(DebugContext.VERY_DETAILED_LEVEL, "Processing block %s", block);
            AbstractBeginNode beginNode = block.getBeginNode();
            if (beginNode instanceof AbstractMergeNode mergeNode) {
                for (PhiNode phi : mergeNode.phis()) {
                    processNode(phi, block, currentState);
                }
            }

            for (Node n : schedule.getBlockToNodesMap().get(block)) {
                if (n instanceof MethodDuplicationAnchorNode anchor) {
                    currentState.registerAnchor(anchor);
                    EconomicSet<Node> value = EconomicSet.create();
                    aliveAtAnchor.put(anchor, value);
                } else if (n instanceof LoopExitNode) {
                    for (Node proxy : ((LoopExitNode) n).proxies()) {
                        processNode(proxy, block, currentState);
                    }
                }
                processNode(n, block, currentState);
            }

            return currentState;
        }

        private void processNode(Node n, HIRBlock block, CollectLiveLocalsState currentState) {
            if (currentState.hasActiveAnchors()) {
                debug.log(DebugContext.VERY_DETAILED_LEVEL, "Checking inputs of node %s ", n);

                if (n instanceof AbstractEndNode endNode) {
                    // Process phi values coming from this end node.
                    AbstractMergeNode mergeNode = endNode.merge();
                    int phiIndex = mergeNode.phiPredecessorIndex(endNode);
                    for (PhiNode phi : mergeNode.phis()) {
                        phi.setValueAt(phiIndex, processInput(currentState, phi, phi.valueAt(phiIndex), phi.valueInputType(), block));
                    }
                }

                for (Position inputPosition : n.inputPositions()) {
                    Node input = inputPosition.get(n);
                    if (input != null) {
                        inputPosition.set(n, processInput(currentState, n, input, inputPosition.getInputType(), getInputBlock(input)));
                    }
                }
            }
        }

        private HIRBlock getInputBlock(Node input) {
            if (input instanceof ProxyNode proxyNode) {
                return getInputBlock(proxyNode.proxyPoint());
            } else if (input instanceof PhiNode) {
                return getInputBlock(((PhiNode) input).merge());
            }
            return schedule.getNodeToBlockMap().get(input);
        }

        private <T extends Node> boolean isBeforeAnchorNode(T input, MethodDuplicationAnchorNode anchorNode) {
            if (input instanceof PhiNode) {
                return true;
            }

            if (input instanceof ProxyNode) {
                return isBeforeAnchorNode(((ProxyNode) input).proxyPoint(), anchorNode);
            }

            HIRBlock hirBlock = schedule.getNodeToBlockMap().get(anchorNode);
            for (Node n : schedule.getBlockToNodesMap().get(hirBlock)) {
                if (n == anchorNode) {
                    return false;
                } else if (n == input) {
                    return true;
                }
            }
            GraalError.shouldNotReachHere("Could not find anchor node in block");
            return false;
        }

        @SuppressWarnings("unchecked")
        private <T extends Node> T processInput(CollectLiveLocalsState currentState, Node node, T input, InputType inputType, HIRBlock inputBlock) {
            assert inputBlock != null;
            debug.log(DebugContext.VERY_DETAILED_LEVEL, "Processing input node %s, input block is %s", input, inputBlock);
            if (input instanceof AbstractEndNode || input.getNodeClass().isLeafNode()) {
                // Nothing to do as no phis need to be ever introduced for such nodes.
                return input;
            } else {
                // Collect anchors across which the input type is alive.
                List<MethodDuplicationAnchorNode> aliveAtAnchors = null;
                for (MethodDuplicationAnchorNode anchorNode : currentState.activeAnchors()) {
                    HIRBlock anchorBlock = getInputBlock(anchorNode);
                    if (inputBlock.strictlyDominates(anchorBlock) || (inputBlock == anchorBlock && isBeforeAnchorNode(input, anchorNode))) {
                        // This edge goes across this anchor.
                        if (aliveAtAnchors == null) {
                            aliveAtAnchors = new ArrayList<>();
                        }
                        aliveAtAnchors.add(anchorNode);
                    }
                }

                // This edge is live across at least one anchor
                if (aliveAtAnchors != null) {
                    if (canHandleInputType(inputType)) {
                        // Regular registration of the value.
                        registerInput(input, aliveAtAnchors);
                        return input;
                    } else if (isDuplicationInputType(inputType)) {
                        if (input.getNodeClass().isLeafNode() || input instanceof LoopBeginNode) {
                            // No need to duplicate.
                            return input;
                        } else {
                            // Need to duplicate this value and its inputs at this position.
                            Graph graph = input.graph();
                            EconomicSet<Node> nodesToDuplicate = EconomicSet.create();
                            NodeWorkList workList = graph.createNodeWorkList();
                            GraalError.guarantee(!(input instanceof FixedNode), "Cannot duplicate fixed node %s", input);
                            workList.add(input);
                            nodesToDuplicate.add(input);

                            // TODO: Can reuse some nodes from previously instead of duplication in
                            // some scenarios.
                            for (Node n : workList) {
                                for (Position inputPosition : n.inputPositions()) {
                                    InputType curInputType = inputPosition.getInputType();
                                    Node curInput = inputPosition.get(n);
                                    if (curInput != null) {
                                        if (canHandleInputType(curInputType)) {
                                            if (!curInput.getNodeClass().isLeafNode()) {
                                                registerInput(curInput, aliveAtAnchors);
                                            }
                                        } else if (isDuplicationInputType(curInputType)) {
                                            if (curInput.getNodeClass().isLeafNode()) {
                                                // No need to duplicate.
                                            } else {
                                                GraalError.guarantee(!(curInput instanceof FixedNode), "Cannot duplicate fixed node %s", curInput);
                                                debug.log(DebugContext.VERY_DETAILED_LEVEL, "Duplicating input %s", curInput);
                                                nodesToDuplicate.add(curInput);
                                                workList.add(curInput);
                                            }
                                        } else {
                                            throw GraalError.shouldNotReachHere(
                                                            String.format("Unexpected input type is live across the boundary edge between %s and %s: %s", n, curInput, curInputType));
                                        }
                                    }
                                }
                            }

                            EconomicMap<Node, Node> duplicationMap = graph.addDuplicates(nodesToDuplicate, graph, nodesToDuplicate.size(), (EconomicMap<Node, Node>) null);
                            return (T) duplicationMap.get(input);
                        }
                    } else {
                        throw GraalError.shouldNotReachHere(String.format("Unexpected input type is live across the boundary input: %s / %s / %s / %s", node, input, inputType, aliveAtAnchors));
                    }
                } else {
                    return input;
                }
            }
        }

        private void registerInput(Node input, List<MethodDuplicationAnchorNode> aliveAtAnchors) {
            if (input instanceof StartNode || input.getNodeClass().isLeafNode()) {
                // This node can be anyway used in both original and duplicated versions => do not
                // register.
            } else {
                for (MethodDuplicationAnchorNode anchorNode : aliveAtAnchors) {
                    Node inputAsOriginal = reverseDuplicationMap.get(input);
                    debug.log(DebugContext.VERY_DETAILED_LEVEL, "Registering input %s (original %s) as alive at %s", input, inputAsOriginal, anchorNode);
                    GraalError.guarantee(inputAsOriginal != null, "Alive node must have an entry in the reverse duplication map! %s", input);
                    aliveAtAnchor.get(anchorNode).add(inputAsOriginal);
                }
            }
        }

        @Override
        protected CollectLiveLocalsState merge(HIRBlock merge, List<CollectLiveLocalsState> states) {
            CollectLiveLocalsState newState = new CollectLiveLocalsState(states.get(0));
            for (int i = 1; i < states.size(); ++i) {
                CollectLiveLocalsState state = states.get(i);
                newState.mergeWith(state);
            }
            return newState;
        }

        @Override
        protected CollectLiveLocalsState cloneState(CollectLiveLocalsState oldState) {
            return new CollectLiveLocalsState(oldState);
        }

        public UnmodifiableEconomicMap<MethodDuplicationAnchorNode, EconomicSet<Node>> aliveAtAnchor() {
            return aliveAtAnchor;
        }
    }

    private static boolean canHandleInputType(InputType type) {
        return type == InputType.Value || type == InputType.Guard || type == InputType.Anchor;
    }

    private static boolean isDuplicationInputType(InputType type) {
        return type == InputType.Condition || type == InputType.State || type == InputType.Association;
    }

    /**
     * @return Returns a set identifying the blocks where a split off point should be introduced
     *         from the hot part to the cold part.
     */
    private static List<HIRBlock> identifySplitOffBlocks(ControlFlowGraph cfg, BasicBlockSet hotBlocks) {
        List<HIRBlock> selectedSplitBlocks = new ArrayList<>();
        List<HIRBlock> coldSplitBlocks = new ArrayList<>();
        // TODO: Make use of this to start the duplication not at the start node, but only at this
        // first common
        // dominator of all split offs.
        HIRBlock coldSplitReentryCommonDominator = null;
        BasicBlockSet leadsIntoHotBlockSet = cfg.createBasicBlockSet();
        HIRBlock[] blocks = cfg.reversePostOrder();
        for (int i = blocks.length - 1; i >= 0; --i) {
            HIRBlock b = blocks[i];
            if (hotBlocks.get(b)) {
                if (b.getEndNode() instanceof ReturnNode returnNode && returnNode.predecessor() == b.getBeginNode()) {
                    // This is a trivial return block, do not count as "hot".
                    cfg.graph.getDebug().log(DebugContext.VERBOSE_LEVEL, "Do not count trivial return block as hot %s", b);
                } else {
                    leadsIntoHotBlockSet.set(b);
                    // For a hot block check if there is a successor block cold that leads into a
                    // hot block to register a split off.
                    int successorCount = b.getSuccessorCount();
                    for (int j = 0; j < successorCount; ++j) {
                        HIRBlock succ = b.getSuccessorAt(j);
                        if (!hotBlocks.get(succ)) {
                            if (succ.getBeginNode() instanceof UnreachableBeginNode) {
                                // This block is unreachable anyway, do not split off.
                            } else {
                                // Check if the successor leads into a hot block.
                                if (leadsIntoHotBlockSet.get(succ)) {
                                    if (succ.getBeginNode().next() == succ.getEndNode() && succ.getEndNode() instanceof EndNode && succ.getSuccessorCount() == 1 &&
                                                    hotBlocks.get(succ.getSuccessorAt(0))) {
                                        cfg.graph.getDebug().log(DebugContext.VERBOSE_LEVEL, "Do not register trivial split off %s", succ);
                                    } else {
                                        coldSplitReentryCommonDominator = (HIRBlock) AbstractControlFlowGraph.commonDominator(coldSplitReentryCommonDominator, succ);
                                        selectedSplitBlocks.add(succ);
                                    }
                                } else {
                                    coldSplitBlocks.add(succ);
                                }
                            }
                        }
                    }
                }
            } else {
                // For a cold block check if there is a path leading into a hot block.
                int successorCount = b.getSuccessorCount();
                for (int j = 0; j < successorCount; ++j) {
                    HIRBlock succ = b.getSuccessorAt(j);
                    if (leadsIntoHotBlockSet.get(succ)) {
                        leadsIntoHotBlockSet.set(b);
                        break;
                    }
                }

                // Also check if this is a loop end leading into a hot loop header, which means it
                // also has to be marked accordingly.
                if (b.isLoopEnd() && hotBlocks.get(b.getLoop().getHeader())) {
                    leadsIntoHotBlockSet.set(b);
                }
            }
        }
        if (selectedSplitBlocks.isEmpty()) {
            // There are no cold splits that reenter the hot code => the optimization won't gain
            // anything.
            return selectedSplitBlocks;
        } else {
            // We have at least one interesting split off. Check which pure cold splits are relevant
            // to be shared
            // to reduce code size by calculating all reachable blocks from selected split blocks.
            BasicBlockSet reachableFromSplitOff = cfg.createBasicBlockSet();
            for (HIRBlock b : selectedSplitBlocks) {
                reachableFromSplitOff.set(b);
            }

            for (HIRBlock b : cfg.reversePostOrder()) {
                if (reachableFromSplitOff.get(b)) {
                    for (int i = 0; i < b.getSuccessorCount(); ++i) {
                        reachableFromSplitOff.set(b.getSuccessorAt(i));
                    }
                }
            }

            for (HIRBlock b : coldSplitBlocks) {
                if (reachableFromSplitOff.get(b)) {
                    selectedSplitBlocks.add(b);
                }
            }

        }
        return selectedSplitBlocks;
    }

    /**
     * @return Returns a set identifying the hot blocks in the method. A block is considered hot if
     *         its relative frequency is >= {@link #HOT_BLOCKS_FREQUENCY_THRESHOLD} and the branch
     *         off leading into the block is at maximum {@link #COLD_BLOCKS_MAX_BRANCH_OFF}
     *         probability.
     */
    private static BasicBlockSet identifyHotBlocks(ControlFlowGraph cfg) {
        BasicBlockSet hotBlocks = cfg.createBasicBlockSet();

        // The start block is always marked hot.
        ArrayList<HIRBlock> worklist = new ArrayList<>();
        worklist.add(cfg.getStartBlock());
        while (!worklist.isEmpty()) {
            // Poll and remove last block in worklist.
            HIRBlock block = worklist.get(worklist.size() - 1);
            worklist.remove(worklist.size() - 1);

            // Select the hottest straight line path from the current block.
            while (true) { // TERMINATION ARGUMENT: processing nodes of a defined type in a graph
                CompilationAlarm.checkProgress(cfg.graph);
                if (hotBlocks.get(block)) {
                    // This block was already selected as hot in the meantime.
                    break;
                }
                cfg.graph.getDebug().log(DebugContext.VERBOSE_LEVEL, "Identified block %s (%s) as hot", block, block.getBeginNode());
                hotBlocks.set(block);
                if (block.getSuccessorCount() == 0 || block.isLoopEnd()) {
                    // The path is already finished.
                    break;
                } else {
                    HIRBlock mostProbableSucc = block.getSuccessorAt(0);
                    double mostProbableSuccValue = block.getSuccessorProbabilityAt(0);
                    for (int i = 1; i < block.getSuccessorCount(); ++i) {
                        double curValue = block.getSuccessorProbabilityAt(i);
                        if (curValue > mostProbableSuccValue) {
                            mostProbableSuccValue = curValue;
                            mostProbableSucc = block.getSuccessorAt(i);
                        }
                    }
                    for (int i = 0; i < block.getSuccessorCount(); ++i) {
                        HIRBlock curSucc = block.getSuccessorAt(i);
                        if (curSucc != mostProbableSucc && !hotBlocks.get(curSucc) &&
                                        (curSucc.getRelativeFrequency() >= HOT_BLOCKS_FREQUENCY_THRESHOLD ||
                                                        block.getSuccessorProbabilityAt(i) >= COLD_BLOCKS_MAX_BRANCH_OFF)) {
                            // Add this block as a candidate.
                            worklist.add(curSucc);
                        }
                    }
                    block = mostProbableSucc;
                }
            }
        }
        return hotBlocks;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.unlessRunBefore(this, GraphState.StageFlag.HIGH_TIER_LOWERING, graphState),
                        NotApplicable.unlessRunBefore(this, GraphState.StageFlag.FLOATING_READS, graphState));
    }
}
