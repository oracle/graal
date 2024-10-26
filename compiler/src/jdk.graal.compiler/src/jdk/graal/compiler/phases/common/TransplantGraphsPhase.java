/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodes.cfg.ControlFlowGraph.BlockTransplantOrigin.CALLEE;
import static jdk.graal.compiler.nodes.cfg.ControlFlowGraph.BlockTransplantOrigin.CALLER;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Graph.DuplicationReplacement;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph.BlockTransplantData;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.tiers.TargetProvider;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.nodes.LateLoweredNode;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.TargetDescription;

/**
 * Phase that implements low tier graph transplanting. Low tier graph transplant is a form of very
 * late inlining in Graal. The basic idea is that we have 2 "fully" compiled graphs, i.e., their IR
 * is at the end of the frontend. One graph contains a call-like node (normally a
 * {@link LateLoweredNode}) and is the caller graph and the other is the callee graph. Both caller
 * and callee graph are fully scheduled. We transplant the nodes, schedule and CFG from the callee
 * into the caller.
 *
 * The basic idea of the algorithm is explained with the 2 methods
 *
 * <pre>
 * void caller(int a) {
 *     if (a % 2 == 0) {
 *         callee(a);
 *     } else {
 *         sink1();
 *     }
 *     sink2();
 * }
 * </pre>
 *
 * and
 *
 * <pre>
 * void callee(int a) {
 *     if (a == 10) {
 *         use(a);
 *     } else {
 *         sink3();
 *     }
 *     sink4();
 * }
 * </pre>
 *
 *
 * the CFG of caller looks like this (the subscript _ca identifies the caller and _ce the callee)
 *
 * <pre>
*              |--------|b0_ca
*         |----| a%2==0 |------|
*         |    |--------|      |
*         |                    |
*    |----v------|b1_ca   |----v----|b2_ca
*    | callee(a) |        | sink1(); |
*    |-----------|    |---|----------|
*                |    |
*             |--v----v--|b3_ca
*             |sink2();  |
*             |----------|
 *
 * </pre>
 *
 * and the CFG of the callee looks like this
 *
 * <pre>
 *                |--------|b0_ce
 *            |---| a==10; |---|
 *            |   |--------|   |
 *            |                |
 *       |----v----|b1_ce |----v---|b2_ce
 *       | use(a); |      |sink3();|
 *       |---------||    ||--------|
 *                  |    |
 *                |-v----v-|b3_ce
 *                |sink4();|
 *                |--------|
 *
 * </pre>
 *
 * Note that for the application of the phase we guarantee that the callee only has a single return
 * block. This is ensured during graph parsing of the callee graph.
 *
 * We use a little trick - the target nodes ({@link LateLoweredNode} for late inlining) are all sub
 * classes of {@link AbstractBeginNode} so the CFG computation algorithm explicitly always starts a
 * new basic block at it. This simplifies later inlining and graph transplant.
 *
 * So in reality the caller CFG looks like this
 *
 * <pre>
/*
 *                   |--------|b0_ca
 *               |---| a%2==0 |------|
 *               |   |--------|      |
 *               |   b1_ca           |
 *           |---v-------|     |-----v----|b3_ca
 *           | BeginNode |     | sink1(); |
 *           |-----------|     |----------|
 *                  |           |
 *                  |  b2_ca    |
 *          |-------v---------| |
 *          |LateLoweredNode  | |
 *          |    callee       | |
 *          |-----------------| |
 *                       |      |
 *                       |      |
 *                       |      |
 *                    |--v------v-|b4_ca
 *                    | sink2();  |
 *                    |-----------|
 *
 * </pre>
 *
 * When transplanting the graph we do the following steps:
 * <ul>
 * <li>We copy nodes from the callee graph into the caller graph</li>
 * <li>Then we transplant the CFG: for this we take the reverse post order of the caller graph and
 * identify the block of the callee we transplant the schedule result from. That is the mapping of
 * nodes to blocks and blocks to nodes.</li>
 * <li>Finally we recompute dominators.</li>
 * </ul>
 *
 * Note that we could recompute dominators after every inlining but compile time wise there is no
 * real benefit to do that. This means the dominator tree is broken during inlining and only correct
 * after inlining.
 *
 * The inlining operation itself is straight forward. Parameter nodes are replaced by the caller
 * nodes for them and the parameter nodes of the callee graph are deleted in the callee portion of
 * the schedule.
 *
 * The transplant of basic blocks and their renumbering is more interesting. For this consider the
 * CFG of the caller. The algorithm works as follows (all done in
 * {@link ControlFlowGraph#transplantAndRenumber(LateLoweredNode, ControlFlowGraph, EconomicMap)}):
 *
 * <ul>
 * <li>we identify the original basic block of the LateLoweredNode, this one will be deleted since
 * the LateLoweredNode is deleted</li>
 * <li>we find the index in the CFG's reverse post order list of basic blocks which is
 * (b0,b1,b2,b3,b4), index of b2=2</li>
 * <li>we allocate the new reverse post order block array: we know that the number of basic blocks
 * will be the original blocks + callee's blocks -1 (for the LateLoweredNode). For our running
 * example this is 5+4-1=8</li>
 * <li>we copy over all basic blocks from the old reverse post order that are retained. We do this
 * from index 0 to the index that is deleted (2)</li>
 * <li>then we copy in the new basic blocks transplanted from the callee graph, for our example that
 * are all 4 basic blocks from the callee</li>
 * <li>we then skip the single basic block from the caller since that one will be deleted (starting
 * with the LateLoweredNode which will be deleted). Note that the callee nodes after the
 * LateLoweredNode that have been connected with next pointers from the LateLoweredNode are
 * connected by the callee return substitute in the caller.</li>
 * <li>we then copy over the rest of the basic blocks</li>
 * <li>in a final step we renumber all basic blocks and set begin and end nodes accordingly and
 * predecessors and successors</li>
 * </ul>
 *
 * After that the CFG looks like this
 *
 * <pre>
 *                      |--------|b0_ca
 *                  |---| a%2==0 |------|
 *                  |   |--------|      |
 *                  |   b1_ca           |
 *              |---v-------|     |-----v----|b6_ca
 *              | BeginNode |     | sink1(); |
 *              |-----------|     |----------|
 *                    |                   |
 *                    |                   |
 *                |---v----|b2_ce         |
 *            |---| a==10; |---|          |
 *            |   |--------|   |          |
 *            |                |          |
 *       |----v----|b3_ce |----v---|b4_ce |
 *       | use(a); |      |sink3();|      |
 *       |---------||    ||--------|      |
 *                  |    |                |
 *                |-v----v-|b5_ce         |
 *                |sink4();|              |
 *                |--------|------|       |
 *                                |       |
 *                                |       |
 *                             |--v-------v|b7_ca
 *                             | sink4();  |
 *                             |-----------|
 * </pre>
 *
 * At this point the IR and the CFG data structure are correct again, only the schedule result needs
 * adjustment. We correct it by going over all basic blocks and querying respective block data from
 * the caller or callee schedule and map everything into the caller graph schedule result. This can
 * be seen in {@link #transplantScheduleResult}.
 *
 * The final step of the algorithm is to compute dominators. This is API from the control flow graph
 * and can be done any time ({@link ControlFlowGraph#computeDominators()}.
 *
 * A note on global value numbering and scheduling: This algorithm DOES NOT perform global value
 * numbering during inlining. This is not a bug but a feature - graph transplanting is a low level
 * inlining that does not perform any high level optimizations. This is done on purpose.
 * Applications of this phase must be aware of that.
 *
 * A note on tranplantees and exception handlers: at the moment transplanting callee graphs with
 * exception handlers ({@link UnwindNode}) is not supported. This is an implementation restriction
 * that might be lifted in the future.
 */
public class TransplantGraphsPhase extends BasePhase<LowTierContext> {

    /**
     * The suites to use for compiling the callee graph.
     */
    private final Suites suitesForLateCallee;

    public TransplantGraphsPhase(Suites suitesForLateSnippets) {
        this.suitesForLateCallee = suitesForLateSnippets;
    }

    /**
     * Log the transplant of graph, cfg and schedule result to TTY.
     */
    static final boolean LOG_TTY = Boolean.parseBoolean(GraalServices.getSavedProperty("debug.graal.TransplantLowTierSnippets"));

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(NotApplicable.unlessRunAfter(this, StageFlag.FINAL_SCHEDULE, graphState));
    }

    /**
     * Verify that the given callee graph can be inlined without a rescheduling.
     */
    private static void verifyCalleeIntegrity(StructuredGraph graph) {
        // Can only have a single return for low tier graph transplant
        GraalError.guarantee(graph.getNodes(ReturnNode.TYPE).count() == 1, "Need one return");
        // We do not allow unwinds in low tier graph transplant, reconnecting the exception handlers
        // requires the LateLoweredNode to be a with exception node, not yet supported
        GraalError.guarantee(graph.getNodes(UnwindNode.TYPE).count() == 0, "No unwinds allowed");
    }

    /**
     * Data used to perform the late inlining.
     */
    private static class LateInliningData {
        /**
         * Duplicates built during inlining.
         */
        private EconomicMap<Node, Node> duplicates;
        /**
         * Next node after the inlined node.
         */
        private FixedNode nextAfterInline;

        LateInliningData(EconomicMap<Node, Node> duplicates, FixedNode nextAfterInline) {
            this.duplicates = duplicates;
            this.nextAfterInline = nextAfterInline;
        }
    }

    /**
     * Performs the late inlining of {@code lateLoweredNode} into the caller graph.
     */
    private static LateInliningData lateInline(LateLoweredNode lateLoweredNode, StructuredGraph callee) {
        final StructuredGraph callerGraph = lateLoweredNode.graph();
        NodeSourcePosition callerNSP = lateLoweredNode.getNodeSourcePosition();

        // setup next nodes
        BeginNode b = callerGraph.add(new BeginNode());
        FixedWithNextNode oldPred = (FixedWithNextNode) lateLoweredNode.predecessor();
        oldPred.setNext(null);
        b.setNext(lateLoweredNode);
        oldPred.setNext(b);

        // handle parameter replacements
        FixedNode oldNext = lateLoweredNode.next();
        final AbstractBeginNode prevBegin = b;
        final StartNode entryPointNode = callee.start();
        DuplicationReplacement localReplacement = new DuplicationReplacement() {

            @Override
            public Node replacement(Node node) {
                if (node instanceof ParameterNode parameterNode) {
                    ValueNode argument = lateLoweredNode.getArguments().get(parameterNode.index());
                    return argument;
                } else if (node == entryPointNode) {
                    return prevBegin;
                }
                return node;
            }
        };

        // record the method for inlining
        callerGraph.recordMethod(callee.method());

        callerGraph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, callerGraph, "Before inlining at LateLoweredNode %s", lateLoweredNode);
        /*
         * We DO NOT GVN constant nodes in late inlining. That would require a re-scheduling which
         * is too compile time intensive.
         */
        final boolean gvn = false;
        Graph.Mark before = callerGraph.getMark();
        EconomicMap<Node, Node> duplicates = callerGraph.addDuplicates(callee.getNodes(), callee, callee.getNodeCount(), localReplacement, gvn);
        callerGraph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, callerGraph, "After inlining at LateLoweredNode %s", lateLoweredNode);

        // update the NSP and the outer frame states if we inline with states
        for (Node n : callerGraph.getNewNodes(before)) {
            if (n.isAlive() && n.getNodeSourcePosition() != null) {
                n.setNodeSourcePosition(n.getNodeSourcePosition().addCaller(callerNSP));
            }

            if (n.isAlive() && n instanceof FrameState fs) {
                if (fs.outerFrameState() == null) {
                    // we are at an outer most framestate, set the state of the call
                    fs.setOuterFrameState(lateLoweredNode.stateDuring());
                }
                if (fs.bci == BytecodeFrame.BEFORE_BCI) {
                    fs.replaceAtUsages(lateLoweredNode.stateBefore());
                } else if (fs.bci == BytecodeFrame.AFTER_BCI) {
                    fs.replaceAtUsages(lateLoweredNode.stateAfter());
                }
            }
        }

        lateLoweredNode.setNext(null);

        // we only support single return graphs
        ReturnNode calleeReturn = callee.getNodes(ReturnNode.TYPE).first();
        ReturnNode copyInCaller = (ReturnNode) duplicates.get(calleeReturn);
        assert copyInCaller != null;

        lateLoweredNode.replaceAtUsages(copyInCaller.result());

        FixedWithNextNode retNext = (FixedWithNextNode) copyInCaller.predecessor();
        retNext.setNext(null);
        copyInCaller.safeDelete();
        retNext.setNext(oldNext);

        // we would like to delete the LateInovke node here already however we need it for the
        // cfg patching still and thus do not touch it for now

        callerGraph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, callerGraph, "After fixing control flow at %s", lateLoweredNode);

        return new LateInliningData(duplicates, oldNext);
    }

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        List<LateLoweredNode> lateLoweredNodes = graph.getNodes(LateLoweredNode.TYPE).snapshot();
        if (lateLoweredNodes.size() == 0) {
            return;
        }

        final ScheduleResult finalScheduleBeforeTransplant = graph.getLastSchedule();
        final ControlFlowGraph finalCFGBeforeTransplant = finalScheduleBeforeTransplant.getCFG();

        // the cfg and block to nodes map we incrementally update during transplant
        ControlFlowGraph patchedFinalCFG = finalCFGBeforeTransplant.deepCopyReversePostOrderOnly();
        BlockMap<List<Node>> patchedBlockToNodesMap = finalScheduleBeforeTransplant.getBlockToNodesMap();

        for (LateLoweredNode lateLoweredNode : lateLoweredNodes) {
            StructuredGraph calleeGraph = null;
            SnippetTemplate template = lateLoweredNode.getTemplateProducer().get();
            calleeGraph = (StructuredGraph) template.getSnippet().copy(graph.getDebug());

            // compile the callee until the end
            // GR-56005 cache in Snippet template
            suitesForLateCallee.getHighTier().apply(calleeGraph, new HighTierContext((Providers) context.getProviders(), null, OptimisticOptimizations.NONE));
            suitesForLateCallee.getMidTier().apply(calleeGraph, new MidTierContext((Providers) context.getProviders(), new TargetProvider() {

                @Override
                public TargetDescription getTarget() {
                    return context.getTarget();
                }
            }, OptimisticOptimizations.NONE, null));
            suitesForLateCallee.getLowTier().apply(calleeGraph, context);

            verifyCalleeIntegrity(calleeGraph);

            LateInliningData lateInlineData = lateInline(lateLoweredNode, calleeGraph);
            int oldBlockBeforeTransplantThatIsSplit = patchedFinalCFG.blockFor(lateLoweredNode).getId();

            EconomicMap<HIRBlock, BlockTransplantData> newToOldBlocks = patchedFinalCFG.transplantAndRenumber(lateLoweredNode, calleeGraph.getLastCFG(), lateInlineData.duplicates);
            GraphUtil.killCFG(lateLoweredNode);

            if (LOG_TTY) {
                patchedFinalCFG.printCFGToStdout();
            }

            BlockMap<List<Node>> newBlockToNodesMap = transplantScheduleResult(graph, patchedFinalCFG, patchedBlockToNodesMap, calleeGraph, lateInlineData, oldBlockBeforeTransplantThatIsSplit,
                            newToOldBlocks);

            patchedBlockToNodesMap = newBlockToNodesMap;
        }

        // now that the CFG is stable we can compute dominators again. We could also propagate
        // this data on the fly but the dominator numbers used in the data representation are
        // more complex to recompute on the fly. Refrain from doing so until this becomes a
        // compile time bottle-neck.
        recomputeDominators(patchedFinalCFG);
        graph.setLastCFG(patchedFinalCFG);

        NodeMap<HIRBlock> patchedNodeToBlock = new NodeMap<>(graph);
        for (HIRBlock b : patchedFinalCFG.reversePostOrder()) {
            for (Node n : patchedBlockToNodesMap.get(b)) {
                patchedNodeToBlock.put(n, b);
            }
        }
        ScheduleResult patchedFinalSchedule = new ScheduleResult(patchedFinalCFG, patchedNodeToBlock, patchedBlockToNodesMap, finalScheduleBeforeTransplant.strategy);
        graph.setLastSchedule(patchedFinalSchedule);

        if (LOG_TTY) {
            patchedFinalCFG.printCFGToStdout();
            TTY.printf("%s%n", graph.getLastSchedule().print());
        }

        assert verifyTransplantedGraphAndSchedule(graph, context, patchedFinalSchedule);
    }

    private static boolean verifyTransplantedGraphAndSchedule(StructuredGraph graph, LowTierContext context, ScheduleResult patchedFinalSchedule) {
        NodeMap<Integer> seenOccurences = new NodeMap<>(graph);
        for (Node n : graph.getNodes()) {
            if (!(n instanceof PhiNode)) {
                int seen = seenOccurences.containsKey(n) ? seenOccurences.get(n) : 0;
                seen++;
                seenOccurences.put(n, seen);
                HIRBlock b = graph.getLastSchedule().getNodeToBlockMap().get(n);
                assert b != null : Assertions.errorMessage("Must have a block for every node", n);
            }
        }

        for (Node n : graph.getNodes()) {
            Integer seen = seenOccurences.get(n);
            if (seen != null) {
                assert seen == 1 : Assertions.errorMessage("Must only see a node once, but found node more often", n, seen);
            }
        }

        for (HIRBlock b : patchedFinalSchedule.getCFG().getBlocks()) {
            List<Node> nodes = patchedFinalSchedule.getBlockToNodesMap().get(b);
            for (Node n : nodes) {
                assert n.isAlive();
                assert patchedFinalSchedule.getNodeToBlockMap().get(n) == b : Assertions.errorMessage("Node to block gives different block for node ", patchedFinalSchedule.getNodeToBlockMap().get(n),
                                n, b);
                StructuredGraph g = (StructuredGraph) n.graph();
                if (g.hasLoops() && g.getGuardsStage().areFrameStatesAtDeopts() && n instanceof DeoptimizeNode) {
                    assert b.getLoopDepth() == 0 : n;
                }
            }
        }
        StructuredGraph copyToSchedule = graph.copy(graph.method(), graph.getOptions(), graph.getDebug(), false);
        new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(copyToSchedule, context);
        return true;
    }

    /**
     * Transplant the result of the scheduling, depending on whether the graph parts are from the
     * caller or or the callee graph.
     */
    private static BlockMap<List<Node>> transplantScheduleResult(StructuredGraph graph, ControlFlowGraph patchedFinalCFG, BlockMap<List<Node>> patchedBlockToNodesMap,
                    final StructuredGraph calleeGraph, LateInliningData lateInlineData, int oldBlockBeforeTransplantThatIsSplit,
                    EconomicMap<HIRBlock, BlockTransplantData> newToOldBlocks) {
        if (LOG_TTY) {
            TTY.printf("Transplanting schedule result with old one %s%n", patchedBlockToNodesMap);
        }

        BlockMap<List<Node>> newBlockToNodesMap = new BlockMap<>(patchedFinalCFG);
        for (HIRBlock b : patchedFinalCFG.reversePostOrder()) {
            ArrayList<Node> blockNodes = new ArrayList<>();
            BlockTransplantData data = newToOldBlocks.get(b);
            assert data != null;
            if (data.source() == CALLER) {
                // we are dealing with caller data, transplant that one first
                for (Node n : patchedBlockToNodesMap.get(data.oldBlockIDBeforeTransplant())) {
                    if (n.isAlive()) {
                        assert n.graph() == graph : Assertions.errorMessage("Node Graph must be graph", n, n.graph(), graph);
                        blockNodes.add(n);
                    }
                }
            } else if (data.source() == CALLEE) {
                HIRBlock oldBlock = data.oldBlock();
                assert oldBlock.getCfg().graph == calleeGraph : Assertions.errorMessage("Graphs must be the same", oldBlock.getCfg().graph, calleeGraph);
                for (Node calleeGraphNode : calleeGraph.getLastSchedule().getBlockToNodesMap().get(data.oldBlockIDBeforeTransplant())) {
                    if (calleeGraphNode instanceof ParameterNode) {
                        // already in the parent graph
                        continue;
                    }
                    Node callerGraphNode = lateInlineData.duplicates.get(calleeGraphNode);
                    if (callerGraphNode.isAlive()) {
                        assert callerGraphNode.graph() == graph : Assertions.errorMessage("Node Graph must be graph", callerGraphNode, callerGraphNode.graph(), graph);
                        blockNodes.add(callerGraphNode);
                    }
                }
            } else {
                throw GraalError.shouldNotReachHere("Unkown block source");
            }
            newBlockToNodesMap.put(b, blockNodes);
        }

        HIRBlock currentBlock = patchedFinalCFG.blockFor(lateInlineData.nextAfterInline);
        List<Node> callerBlockList = newBlockToNodesMap.get(currentBlock);
        List<Node> oldPatchList = patchedBlockToNodesMap.get(oldBlockBeforeTransplantThatIsSplit);
        boolean successorBlock = currentBlock.getBeginNode() == lateInlineData.nextAfterInline;
        if (successorBlock) {
            callerBlockList = newBlockToNodesMap.get(patchedFinalCFG.blockFor(lateInlineData.nextAfterInline.predecessor()));
        }
        for (Node n : oldPatchList) {
            if (n.isAlive()) {
                assert n.graph() == graph : Assertions.errorMessage("Node Graph must be graph", n, n.graph(), graph);
                callerBlockList.add(n);
            }
        }

        return newBlockToNodesMap;
    }

    /**
     * Recompute all the dominance and frequency data that is needed by the backend.
     */
    private static void recomputeDominators(ControlFlowGraph patchedFinalCFG) {
        for (BasicBlock<?> b : patchedFinalCFG.reversePostOrder()) {
            b.resetDominators();
        }
        patchedFinalCFG.computeDominators();
        patchedFinalCFG.computePostdominators();
        patchedFinalCFG.computeLoopInformation();
        patchedFinalCFG.computeFrequencies();
    }
}
