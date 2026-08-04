/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases.priorityinline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.AnalysisResult.Materialization;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InliningLog;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.common.priorityinline.CallTree;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.ParentNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;
import jdk.graal.compiler.virtual.phases.ea.EffectsPhase;
import jdk.graal.compiler.virtual.phases.ea.GraphEffectList;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapeBlockState;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The goal of this phase is identify methods where partial escape analysis will be beneficial. This
 * phase injects virtual state of objects across method boundaries and computes the reduction in
 * materialization frequencies, simulating the effects of inlining. The results of the analysis
 * phase are used by the priority inliner (see {@link SubstratePriorityInliningPhase}) and are
 * integrated using the policies.
 * <p>
 * The impact of inlining is simulated by altering the behaviour of the partial escape analysis on
 * several nodes. See
 * {@link InterproceduralPartialEscapeAnalysisClosure#virtualize(ValueNode, VirtualizerTool)}. The
 * materializations are kept track of by overriding
 * {@link InterproceduralPartialEscapeAnalysisClosure#ensureMaterialized(PartialEscapeBlockState, int, FixedNode, GraphEffectList, CounterKey)}
 * and
 * {@link InterproceduralPartialEscapeAnalysisClosure#shouldMaterializeNonVirtualizable(PartialEscapeBlockState.Final, int, FixedNode)}.
 * <p>
 * The phase is run in the {@link SubstratePolicyFactory.SubstrateExpanderPolicy}. The initial run
 * happens before the expansion phase and incrementally on a subtree each time a CutoffNode is
 * expanded. The results are used to boost the local benefits of CallTreeNodes in a CallTree. See
 * {@link SubstratePolicyFactory.SubstrateExpanderPolicy#updateParentNodeLocalBenefit(ParentNode)}
 * and
 * {@link SubstratePolicyFactory.SubstrateExpanderPolicy#updateCutoffNodeLocalBenefit(CutoffNode)}}.
 * The calculations for boosts are done in {@link InterproceduralPartialEscapeAnalysisUtil}.
 * <p>
 * The phase can be run on any subtree of the CallTree and produces an {@link AnalysisResult}
 * object, containing information of where every virtual object escapes and all materializations
 * occur, assuming CallTreeNodes are injected with virtual states of escaping objects.
 * {@link InterproceduralPartialEscapeAnalysisPhase#runFromRoot(CallTree, StructuredGraph, CoreProviders)}
 * creates a fresh AnalysisResult object, while
 * {@link InterproceduralPartialEscapeAnalysisPhase#runFromCallerContext(CallerContext, CoreProviders)}
 * reuses the previous AnalysisResult stored in the CallerContext.
 *
 * See {@link InterProceduralPartialEscapeAnalysisStatistics} for displaying frequency reduction
 * statistics and dump of applied boosts.
 */
public class InterproceduralPartialEscapeAnalysisPhase extends PartialEscapePhase {

    /**
     * This method retrieves either the current virtual object node used to represent this node, or
     * null if the node has been materialized or is linked to a concrete value. This is helpful for
     * determining whether the node is still in a virtual state.
     */
    static VirtualObjectNode lookupVirtual(ValueNode node, VirtualizerTool vt) {
        ValueNode virtual = vt.getAlias(node);
        if (virtual instanceof VirtualObjectNode) {
            return (VirtualObjectNode) virtual;
        }
        return null;
    }

    /**
     * This class holds virtual state which is injected across different instances of the
     * InterproceduralPartialEscapeAnalysisPhase.
     */
    // Checkstyle: stop
    static class VirtualInfo {
        // Checkstyle: resume
        final VirtualObjectNode virtual;
        final ValueNode[] entries;

        private VirtualInfo() {
            virtual = null;
            entries = null;
        }

        static VirtualInfo Placeholder = new VirtualInfo() {
            @Override
            public String toString() {
                return "V(Placeholder)";
            }
        };
        static VirtualInfo MissingParam = new VirtualInfo() {
            @Override
            public String toString() {
                return "V(MissingParam)";
            }
        };

        private VirtualInfo(VirtualObjectNode node, ValueNode[] entries) {
            assert node != null && entries != null;
            this.virtual = node;
            this.entries = entries;
        }

        static VirtualInfo capture(ValueNode node, VirtualizerTool vt) {
            return capture(lookupVirtual(node, vt), vt);
        }

        static VirtualInfo capture(VirtualObjectNode node, VirtualizerTool vt) {
            if (node == null) {
                return null;
            }
            ValueNode[] entries = new ValueNode[node.entryCount()];
            for (int index = 0; index < entries.length; index++) {
                entries[index] = vt.getEntry(node, index);
            }
            return new VirtualInfo(node, entries);
        }

        @Override
        public String toString() {
            return "V(" + virtual + ", " + Arrays.toString(entries) + ")";
        }
    }

    /**
     * This class is used to hold data which is shared across different instances of
     * InterproceduralPartialEscapeAnalysisPhase.
     */
    static final class AnalysisResult {

        /*
         * This class is used to store a location where an object has been materialized. A
         * materialization is always triggered by a FixedNode, causing an object to escape. We store
         * the local frequency to compute the reduction in materializations.
         */
        static final class Materialization {
            FixedNode materializedBefore;
            double localFrequency;

            Materialization(FixedNode materializedBefore) {
                this.materializedBefore = materializedBefore;
                this.localFrequency = materializedBefore.graph().getLastSchedule().blockFor(materializedBefore).getRelativeFrequency();
            }
        }

        /**
         * This class represents an object escaping at an invoke, which corresponds to a CutoffNode
         * in the CallTree. If the VirtualCutoffEscapee has type ARGUMENT, this means an object is
         * still virtual at the call site. If the VirtualCutoffEscapee has type RETURN, this means
         * an object is returned by the invoke. Both these cases imply that there is potential for a
         * reduction in materializations, and we want to boost the chance of exploration.
         */
        static final class VirtualCutoffEscapee {
            enum EscapeType {
                ARGUMENT,
                RETURN
            }

            private final EscapeType escapeType;

            VirtualCutoffEscapee(EscapeType escapeType) {
                this.escapeType = escapeType;
            }

            boolean isVirtualArgument() {
                return escapeType == EscapeType.ARGUMENT;
            }
        }

        /*
         * For each CallTreeNode, we keep track of each location where a materialization is
         * triggered. Materializations are mapped to from the materialized objects origin
         * (VirtualizableAllocation). This information is used to calculate the expected reduction
         * in materializations due to inlining, which in turn is used to boost local benefit of
         * SubgraphNodes.
         */
        public final EconomicMap<CallTreeNode, EconomicMap<VirtualizableAllocation, EconomicSet<Materialization>>> materializations = EconomicMap.create(Equivalence.IDENTITY);

        /*
         * This map contains a list of objects escaping from or to the callTreeNode. This is
         * required to increase benefit of CutoffNodes, such that we prioritize exploring them.
         */
        private final EconomicMap<CallTreeNode, List<VirtualCutoffEscapee>> cutoffEscapees = EconomicMap.create(Equivalence.IDENTITY);

        /* Reverse mapping from callTreeNode to CallerContext. */
        private final EconomicMap<CallTreeNode, CallerContext> callTreeNodeToCallerContext = EconomicMap.create(Equivalence.IDENTITY);

        /*
         * Since IPEA runs on a copied version of the graph, we require a mapping to the invoke
         * Nodes in the original graph to find the callTreeNode corresponding to the invoke.
         */
        private final EconomicMap<Invoke, Invoke> originalInvokeNodes = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);

        /*
         * Contains all VirtualizableAllocations of a single CallTreeNode. This does not cross
         * method boundaries. This is used to compute boost functions.
         */
        private final EconomicMap<CallTreeNode, EconomicSet<VirtualizableAllocation>> allocations = EconomicMap.create(Equivalence.IDENTITY);

        /*
         * Maps each virtual object to its original allocation, including across method
         * boundaries.Is a utility data-structure to compute materializations. This does not
         * correspond to precise behaviour of VirtualObjects, since VirtualObjects have a
         * one-to-many relationship to VirtualizableAllocations, (GR-39611).
         */
        private final EconomicMap<VirtualObjectNode, VirtualizableAllocation> virtualObjectAllocationMap = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);

        CallTreeNode callTreeNodeForInvokeInCopiedGraph(Invoke invoke, CallTreeNode callTreeNode) {
            return callTreeNode.childForInvoke(originalInvokeNodes.get(invoke));
        }

        public void clearSubtreeResults(CallTreeNode parent) {
            parent.preOrderTraverse(callTreeNode -> {
                materializations().removeKey(callTreeNode);
                cutoffEscapees().removeKey(callTreeNode);
            });
        }

        EconomicMap<CallTreeNode, EconomicMap<VirtualizableAllocation, EconomicSet<Materialization>>> materializations() {
            return materializations;
        }

        public EconomicMap<CallTreeNode, List<VirtualCutoffEscapee>> cutoffEscapees() {
            return cutoffEscapees;
        }

        public EconomicMap<CallTreeNode, CallerContext> callTreeNodeToCallerContext() {
            return callTreeNodeToCallerContext;
        }

        public EconomicMap<Invoke, Invoke> originalInvokes() {
            return originalInvokeNodes;
        }

        public EconomicMap<CallTreeNode, EconomicSet<VirtualizableAllocation>> allocations() {
            return allocations;
        }

        public EconomicMap<VirtualObjectNode, VirtualizableAllocation> virtualObjectAllocationMap() {
            return virtualObjectAllocationMap;
        }
    }

    /**
     * Stores information specific to the current inlining context of the method.
     */
    static final class CallerContext {

        final CallerContext parent;
        final int depth;
        VirtualizerTool tool;
        VirtualizerTool callSiteSnapshot;
        final SubstrateMethodCallTargetNode callTarget;
        final CanonicalizerPhase canonicalizer;
        private final CallTreeNode callTreeNode;

        /*
         * This holds the state of all arguments that are virtual at the call site. This is required
         * to replace parameters with injected virtual objects.
         */
        private final ArrayList<VirtualInfo> virtualArgumentInfos;

        /*
         * This holds the state of the returned object when the method terminates. This is required
         * to replace the invoke with an injected virtual object in the parent.
         */
        private final EconomicMap<Invoke, VirtualInfo> virtualReturnInfos = EconomicMap.create();

        /*
         * Holds all data shared across different instances of
         * InterproceduralPartialEscapeAnalysisPhase.
         */
        AnalysisResult result;

        /**
         * Context when PartialEscapeSelectivePhase is triggered from a parent method.
         */
        CallerContext(CallerContext parent, VirtualizerTool tool, SubstrateMethodCallTargetNode callTarget, CallTreeNode callTreeNode, ArrayList<VirtualInfo> vis) {
            this.parent = parent;
            this.canonicalizer = parent.canonicalizer;
            this.result = parent.result;
            this.depth = parent.depth + 1;
            this.tool = tool;
            this.callSiteSnapshot = tool.createSnapshot();
            this.callTarget = callTarget;
            this.callTreeNode = callTreeNode;
            /* Map the callerContext to the callTreeNode */
            analysisResult().callTreeNodeToCallerContext.put(callTreeNode, this);
            this.virtualArgumentInfos = vis;
        }

        /**
         * Initial CallerContext for root method.
         */
        CallerContext(CanonicalizerPhase canonicalizer, AnalysisResult results, CallTreeNode callTreeNode) {
            this.parent = null;
            this.depth = 0;
            this.tool = null;
            this.callTarget = null;
            this.canonicalizer = canonicalizer;
            this.result = results;
            this.callTreeNode = callTreeNode;
            analysisResult().callTreeNodeToCallerContext.put(callTreeNode, this);
            this.virtualArgumentInfos = null; // No virtual arguments from Root method.
        }

        boolean isRootContext() {
            return callTarget == null;
        }

        AnalysisResult analysisResult() {
            return result;
        }

        StructuredGraph graph() {
            return ((SubgraphNode) callTreeNode).getReadonlySubgraph();
        }

        void putVirtualReturnObject(VirtualInfo virtualInfo) {
            parent.virtualReturnInfos.put(callTarget.invoke(), virtualInfo);
        }

        VirtualInfo getVirtualReturnObject(Invoke invoke) {
            return virtualReturnInfos.get(invoke);
        }

        VirtualInfo getVirtualParam(int paramIndex) {
            return virtualArgumentInfos.get(paramIndex);
        }

        void addMaterialization(FixedNode materializedBefore, VirtualizableAllocation allocationNode) {
            if (allocationNode == null) {
                return;
            }
            EconomicMap<VirtualizableAllocation, EconomicSet<Materialization>> callTreeNodeMaterializationMap = analysisResult().materializations.get(callTreeNode);
            if (callTreeNodeMaterializationMap == null) {
                callTreeNodeMaterializationMap = EconomicMap.create(Equivalence.IDENTITY);
                analysisResult().materializations.put(callTreeNode, callTreeNodeMaterializationMap);
            }
            EconomicSet<Materialization> materializations = callTreeNodeMaterializationMap.get(allocationNode);
            if (materializations == null) {
                materializations = EconomicSet.create(Equivalence.IDENTITY);
                callTreeNodeMaterializationMap.put(allocationNode, materializations);
            }
            materializations.add(new Materialization(materializedBefore));
        }

        CallTreeNode getCallTreeNode() {
            return callTreeNode;
        }

        public void putVirtualAllocation(CallTreeNode node, VirtualizableAllocation allocationNode) {
            EconomicSet<VirtualizableAllocation> callTreeNodeVirtualAllocations = analysisResult().allocations.get(node);
            if (callTreeNodeVirtualAllocations == null) {
                callTreeNodeVirtualAllocations = EconomicSet.create(Equivalence.IDENTITY);
                analysisResult().allocations.put(node, callTreeNodeVirtualAllocations);
            }
            callTreeNodeVirtualAllocations.add(allocationNode);
        }

        public void putVirtualObjectAllocationNode(VirtualObjectNode createdVirtualObject, VirtualizableAllocation allocation) {
            analysisResult().virtualObjectAllocationMap.put(createdVirtualObject, allocation);
        }

        public VirtualizableAllocation getAllocationNode(VirtualObjectNode virtualObjectNode) {
            return analysisResult().virtualObjectAllocationMap.get(virtualObjectNode);
        }
    }

    public InterproceduralPartialEscapeAnalysisPhase(CanonicalizerPhase canonicalizer, OptionValues options) {
        super(true, canonicalizer, options);
    }

    /**
     * This class adds the CallerContext to the information available during PartialEscapeAnalysis.
     */
    static final class InterproceduralPartialEscapeAnalysisProviders extends CoreProvidersDelegate {
        final CallerContext callerContext;

        InterproceduralPartialEscapeAnalysisProviders(CoreProviders other, CallerContext callerContext) {
            super(other);
            this.callerContext = callerContext;
        }
    }

    StructuredGraph copyGraph(StructuredGraph graph, CallerContext callerContext) {
        // Step 1: create copy of graph
        // We don't want to change the original graph during analysis
        StructuredGraph graphCopy = new StructuredGraph.Builder(graph.getOptions(), DebugContext.forCurrentThread()).name(graph.name).method(graph.method()).build();
        graphCopy.getGraphState().setGuardsStage(graph.getGuardsStage());
        EconomicMap<Node, Node> replacements = EconomicMap.create(Equivalence.IDENTITY);
        replacements.put(graph.start(), graphCopy.start());

        UnmodifiableEconomicMap<Node, Node> nodeNodeMap;
        try (InliningLog.UpdateScope _ = InliningLog.openDefaultUpdateScope(graphCopy.getInliningLog())) {
            nodeNodeMap = graphCopy.addDuplicates(graph.getNodes(), graph, graph.getNodeCount(), replacements);
        }
        /*
         * Add mapping of all Invoke nodes to the original graph.
         */
        for (Node node : graph.getNodes()) {
            if (node instanceof Invoke) {
                callerContext.analysisResult().originalInvokes().put((Invoke) nodeNodeMap.get(node), (Invoke) node);
            }
        }

        // Step 2: When not the root method, simplify the call graph to have only 1 return
        if (callerContext.callTarget != null) {
            /*
             * Transform multiple return values/state into single return values/state. This will
             * ensure we never have more than a single return node in the graph that gets processed
             * by the PEA, thus simplifying the passing of the return state to the caller analysis.
             * Additionally, we map all newly created Nodes in the graph copy to a placeholder
             * MergedReturnNode, which has a special case in calculating relative frequency.
             */
            List<ReturnNode> returnNodes = new ArrayList<>();
            graphCopy.getNodes(ReturnNode.TYPE).forEach(returnNodes::add);
            if (returnNodes.size() > 1) {
                MergeNode merge = graphCopy.add(new MergeNode());
                ValueNode returnValue = InliningUtil.mergeReturns(merge, returnNodes);
                ReturnNode returnNode = graphCopy.add(new ReturnNode(returnValue));
                merge.setStateAfter(graphCopy.add(returnValue != null ? new FrameState(BytecodeFrame.AFTER_BCI, returnValue) : new FrameState(BytecodeFrame.AFTER_BCI)));
                graphCopy.addAfterFixed(merge, returnNode);
            }
        }
        return graphCopy;
    }

    /*
     * This method runs the custom partial escape analysis pass.
     */
    CallerContext runNested(StructuredGraph graph, CoreProviders context, CallerContext callerContext) {
        if (tooDeepForInterproceduralAnalysis(graph)) {
            return callerContext;
        }
        StructuredGraph graphCopy = copyGraph(graph, callerContext);
        super.run(graphCopy, new InterproceduralPartialEscapeAnalysisProviders(context, callerContext));
        return callerContext;
    }

    /**
     * Determines whether a graph should skip IPEA because its loop nesting exceeds the cutoff that
     * PEA uses to avoid exponential loop processing. IPEA is an optional inlining heuristic, so
     * these graphs are left to the normal inliner path instead of risking repeated nested-loop PEA
     * traversal during call-tree exploration.
     */
    private static boolean tooDeepForInterproceduralAnalysis(StructuredGraph graph) {
        int loopCutoff = GraalOptions.EscapeAnalysisLoopCutoff.getValue(graph.getOptions());
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).build();
        for (CFGLoop<HIRBlock> loop : cfg.getLoops()) {
            if (loop.getDepth() > loopCutoff) {
                graph.getDebug().log(DebugContext.DETAILED_LEVEL, "Skipping interprocedural partial escape analysis for %s: loop depth %d exceeds cutoff %d", graph,
                                loop.getDepth(), loopCutoff);
                return true;
            }
        }
        return false;
    }

    /*
     * This method initiates the custom partial escape analysis at the root method. Builds the
     * initial callerContext using both the StructuredGraph and CallTree.
     */
    public CallerContext runFromRoot(CallTree callTree, StructuredGraph compilerGraph, CoreProviders providers) {
        DebugContext debug = DebugContext.forCurrentThread();
        if (debug.isLogEnabled()) {
            debug.log(2, "Performing PartialEscapeSelectiveInliningAnalysisPhase for %s {{{", strRep(compilerGraph.method()));
        }
        AnalysisResult results = new AnalysisResult();
        return runNested(compilerGraph, providers, new CallerContext(canonicalizer, results, callTree.root()));
    }

    /*
     * This method initiates the custom partial escape analysis at from a CallerContext, or a
     * subtree in the CallTree. This allows us to perform the analysis incrementally when
     * CutoffNodes are expanded.
     */
    public CallerContext runFromCallerContext(CallerContext callerContext, CoreProviders coreProviders) {
        /*
         * We need to reset the state of the VirtualizerTool to the state it had at the call site.
         */
        callerContext.tool = callerContext.callSiteSnapshot.createSnapshot();
        return runNested(callerContext.graph(), coreProviders, callerContext);
    }

    @Override
    protected EffectsPhase.Closure<?> createEffectsClosure(CoreProviders context, ScheduleResult schedule, ControlFlowGraph cfg, OptionValues options) {
        return new InterproceduralPartialEscapeAnalysisClosure(schedule, context, ((InterproceduralPartialEscapeAnalysisProviders) context).callerContext);
    }

    static String strRep(ValueNode node) {
        if (node == null) {
            return "null";
        }
        return strRep(node.stamp(NodeView.DEFAULT));
    }

    static String strRep(Stamp stamp) {
        ResolvedJavaType typeForStamp = StampTool.typeOrNull(stamp);
        return typeForStamp == null ? "Object" : typeForStamp.getUnqualifiedName();
    }

    static String strRep(ResolvedJavaMethod method) {
        if (method == null) {
            return "??Invalid!";
        }
        return method.format("%h" + (method.isStatic() ? ":" : ".") + "%n(%p)");
    }
}
