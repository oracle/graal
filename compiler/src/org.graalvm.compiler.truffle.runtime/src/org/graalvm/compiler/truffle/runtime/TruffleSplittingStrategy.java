/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.graphio.GraphStructure;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleSplitting;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleSplittingGrowthLimit;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleSplittingMaxCalleeSize;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleSplittingMaxNumberOfSplitNodes;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleTraceSplittingSummary;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleUsePollutionBasedSplittingStrategy;


final class TruffleSplittingStrategy {

    static void beforeCall(OptimizedDirectCallNode call, GraalTVMCI tvmci) {
        final GraalTVMCI.EngineData engineData = tvmci.getEngineData(call.getRootNode());
        if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
            getReporter().engineDataSet.add(engineData);
            if (call.getCurrentCallTarget().getCompilationProfile().getInterpreterCallCount() == 0) {
                getReporter().totalExecutedNodeCount += call.getCurrentCallTarget().getUninitializedNodeCount();
            }
        }
        if (TruffleCompilerOptions.getValue(TruffleUsePollutionBasedSplittingStrategy)) {
            if (pollutionBasedShouldSplit(call, engineData)) {
                engineData.splitCount += call.getCallTarget().getUninitializedNodeCount();
                doSplit(call);
            }
            return;
        }
        if (call.getCallCount() == 2) {
            if (shouldSplit(call, engineData)) {
                engineData.splitCount += call.getCurrentCallTarget().getUninitializedNodeCount();
                doSplit(call);
            }
        }
    }


    private static void doSplit(OptimizedDirectCallNode call) {
        if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
            calculateSplitWasteImpl(call.getCurrentCallTarget());
        }
        call.split();
        if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
            getReporter().splitNodeCount += call.getCurrentCallTarget().getUninitializedNodeCount();
            getReporter().splitCount++;
        }
    }

    private static boolean pollutionBasedShouldSplit(OptimizedDirectCallNode call, GraalTVMCI.EngineData engineData) {
        if (!call.isNeedsSplit()) {
            return false;
        }
        if (!canSplit(call) || engineData.splitCount + call.getCallTarget().getUninitializedNodeCount() >= engineData.splitLimit) {
            return false;
        }
        OptimizedCallTarget callTarget = call.getCurrentCallTarget();
        if (callTarget.getNonTrivialNodeCount() > TruffleCompilerOptions.getValue(TruffleSplittingMaxCalleeSize)) {
            return false;
        }
        if (callTarget.isValid()) {
            return false;
        }
        return true;
    }

    static void forceSplitting(OptimizedDirectCallNode call, GraalTVMCI tvmci) {
        final GraalTVMCI.EngineData engineData = tvmci.getEngineData(call.getRootNode());
        if (!canSplit(call)) {
            return;
        }
        engineData.splitCount += call.getCurrentCallTarget().getUninitializedNodeCount();
        doSplit(call);
        if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
            reporter.forcedSplitCount++;
        }
    }

    private static boolean canSplit(OptimizedDirectCallNode call) {
        if (call.isCallTargetCloned()) {
            return false;
        }
        if (!TruffleCompilerOptions.getValue(TruffleSplitting)) {
            return false;
        }
        if (!call.isCallTargetCloningAllowed()) {
            return false;
        }
        return true;
    }

    private static boolean shouldSplit(OptimizedDirectCallNode call, GraalTVMCI.EngineData engineData) {
        if (engineData.splitCount + call.getCurrentCallTarget().getUninitializedNodeCount() > engineData.splitLimit) {
            return false;
        }
        if (!canSplit(call)) {
            return false;
        }

        OptimizedCallTarget callTarget = call.getCallTarget();
        int nodeCount = callTarget.getNonTrivialNodeCount();
        if (nodeCount > TruffleCompilerOptions.getValue(TruffleSplittingMaxCalleeSize)) {
            return false;
        }

        RootNode rootNode = call.getRootNode();
        if (rootNode == null) {
            return false;
        }
        // disable recursive splitting for now
        OptimizedCallTarget root = (OptimizedCallTarget) rootNode.getCallTarget();
        if (root == callTarget || root.getSourceCallTarget() == callTarget) {
            // recursive call found
            return false;
        }

        // Disable splitting if it will cause a deep split-only recursion
        if (isRecursiveSplit(call)) {
            return false;
        }

        // max one child call and callCount > 2 and kind of small number of nodes
        if (isMaxSingleCall(call)) {
            return true;
        }

        return countPolymorphic(call) >= 1;
    }

    private static boolean isRecursiveSplit(OptimizedDirectCallNode call) {
        final OptimizedCallTarget splitCandidateTarget = call.getCallTarget();

        OptimizedCallTarget callRootTarget = (OptimizedCallTarget) call.getRootNode().getCallTarget();
        OptimizedCallTarget callSourceTarget = callRootTarget.getSourceCallTarget();
        int depth = 0;
        while (callSourceTarget != null) {
            if (callSourceTarget == splitCandidateTarget) {
                depth++;
                if (depth == 2) {
                    return true;
                }
            }
            final OptimizedDirectCallNode splitCallSite = callRootTarget.getCallSiteForSplit();
            if (splitCallSite == null) {
                break;
            }
            final RootNode splitCallSiteRootNode = splitCallSite.getRootNode();
            if (splitCallSiteRootNode == null) {
                break;
            }
            callRootTarget = (OptimizedCallTarget) splitCallSiteRootNode.getCallTarget();
            if (callRootTarget == null) {
                break;
            }
            callSourceTarget = callRootTarget.getSourceCallTarget();
        }
        return false;
    }

    private static boolean isMaxSingleCall(OptimizedDirectCallNode call) {
        return NodeUtil.countNodes(call.getCallTarget().getRootNode(), new NodeCountFilter() {
            @Override
            public boolean isCounted(Node node) {
                return node instanceof DirectCallNode;
            }
        }) <= 1;
    }

    private static int countPolymorphic(OptimizedDirectCallNode call) {
        return NodeUtil.countNodes(call.getCallTarget().getRootNode(), new NodeCountFilter() {
            @Override
            public boolean isCounted(Node node) {
                NodeCost cost = node.getCost();
                boolean polymorphic = cost == NodeCost.POLYMORPHIC || cost == NodeCost.MEGAMORPHIC;
                return polymorphic;
            }
        });
    }

    public static void logSplitOf(OptimizedDirectCallNode call) {
        System.out.println("@Splitting " + extractSourceSection(call) + ":" + call.getCallTarget());
    }

    private static String extractSourceSection(OptimizedDirectCallNode node) {
        Node cnode = node;
        while (cnode.getSourceSection() == null && !(cnode instanceof RootNode)) {
            cnode = cnode.getParent();
            if (cnode == null) {
                return "";
            }
        }
        return getShortDescription(cnode.getSourceSection());
    }

    static String getShortDescription(SourceSection sourceSection) {
        if (sourceSection.getSource() == null) {
            // TODO the source == null branch can be removed if the deprecated
            // SourceSection#createUnavailable has be removed.
            return "<Unknown>";
        }
        StringBuilder b = new StringBuilder();
        if (sourceSection.getSource().getPath() == null) {
            b.append(sourceSection.getSource().getName());
        } else {
            Path pathAbsolute = Paths.get(sourceSection.getSource().getPath());
            Path pathBase = new File("").getAbsoluteFile().toPath();
            try {
                Path pathRelative = pathBase.relativize(pathAbsolute);
                b.append(pathRelative.toFile());
            } catch (IllegalArgumentException e) {
                b.append(sourceSection.getSource().getName());
            }
        }

        b.append("~").append(formatIndices(sourceSection, true));
        return b.toString();
    }

    static String formatIndices(SourceSection sourceSection, boolean needsColumnSpecifier) {
        StringBuilder b = new StringBuilder();
        boolean singleLine = sourceSection.getStartLine() == sourceSection.getEndLine();
        if (singleLine) {
            b.append(sourceSection.getStartLine());
        } else {
            b.append(sourceSection.getStartLine()).append("-").append(sourceSection.getEndLine());
        }
        if (needsColumnSpecifier) {
            b.append(":");
            if (sourceSection.getCharLength() <= 1) {
                b.append(sourceSection.getCharIndex());
            } else {
                b.append(sourceSection.getCharIndex()).append("-").append(sourceSection.getCharIndex() + sourceSection.getCharLength() - 1);
            }
        }
        return b.toString();
    }

    static void newTargetCreated(GraalTVMCI tvmci, RootCallTarget target) {
        final OptimizedCallTarget callTarget = (OptimizedCallTarget) target;
        if (TruffleCompilerOptions.getValue(TruffleSplitting)) {
            final GraalTVMCI.EngineData engineData = tvmci.getEngineData(target.getRootNode());
            final int newLimit = (int) (engineData.splitLimit + TruffleCompilerOptions.getValue(TruffleSplittingGrowthLimit) * callTarget.getUninitializedNodeCount());
            engineData.splitLimit = Math.min(newLimit, TruffleCompilerOptions.getValue(TruffleSplittingMaxNumberOfSplitNodes));
        }
        if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
            getReporter().totalCreatedNodeCount += callTarget.getUninitializedNodeCount();
        }
    }

    static Set<OptimizedCallTarget> waste = new HashSet<>();

    private static void calculateSplitWasteImpl(OptimizedCallTarget callTarget) {
        final List<OptimizedDirectCallNode> callNodes = NodeUtil.findAllNodeInstances(callTarget.getRootNode(), OptimizedDirectCallNode.class);
        callNodes.removeIf(callNode -> !callNode.isCallTargetCloned());
        for (OptimizedDirectCallNode node : callNodes) {
            final OptimizedCallTarget clonedCallTarget = node.getClonedCallTarget();
            if (waste.add(clonedCallTarget)) {
                reporter.wastedTargetCount++;
                reporter.wastedNodeCount += clonedCallTarget.getUninitializedNodeCount();
                calculateSplitWasteImpl(clonedCallTarget);
            }
        }
    }

    private static SplitStatisticsReporter reporter;

    private static SplitStatisticsReporter getReporter() {
        if (reporter == null) {
            reporter = new SplitStatisticsReporter();
            Runtime.getRuntime().addShutdownHook(reporter);
        }
        return reporter;
    }

    static void newPolluteCall(Node node) {
        if (TruffleCompilerOptions.getValue(TruffleTraceSplittingSummary)) {
            final Map<Class<? extends Node>, Integer> pollutedNodes = getReporter().pollutedNodes;
            final Class<? extends Node> aClass = node.getClass();
            if (pollutedNodes.containsKey(aClass)) {
                pollutedNodes.put(aClass, pollutedNodes.get(aClass) + 1);
            } else {
                pollutedNodes.put(aClass, 1);
            }
        }
    }

    static class SplitStatisticsReporter extends Thread {
        final Set<GraalTVMCI.EngineData> engineDataSet = new HashSet<>();
        final Map<Class<? extends Node>, Integer> pollutedNodes = new HashMap<>();
        int splitCount;
        int forcedSplitCount;
        int splitNodeCount;
        int totalExecutedNodeCount;
        int totalCreatedNodeCount;
        int wastedNodeCount;
        int wastedTargetCount;

        static final String D_FORMAT = "[truffle] %-40s: %10d";
        static final String D_LONG_FORMAT = "[truffle] %-120s: %10d";
        static final String P_FORMAT = "[truffle] %-40s: %9.2f%%";

        @Override
        public void run() {
            for (GraalTVMCI.EngineData engineData : engineDataSet) {
                System.out.println(String.format(D_FORMAT, "Split count", engineData.splitCount));
                System.out.println(String.format(D_FORMAT, "Split limit", engineData.splitLimit));
            }
            System.out.println(String.format(D_FORMAT, "Splits", splitCount));
            System.out.println(String.format(D_FORMAT, "Forced splits", forcedSplitCount));
            System.out.println(String.format(D_FORMAT, "Nodes created through splitting", splitNodeCount));
            System.out.println(String.format(D_FORMAT, "Nodes created without splitting", totalCreatedNodeCount));
            System.out.println(String.format(P_FORMAT, "Increase in nodes", (splitNodeCount * 100.0) / (totalCreatedNodeCount)));
            System.out.println(String.format(D_FORMAT, "Split nodes wasted", wastedNodeCount));
            System.out.println(String.format(P_FORMAT, "Percent of split nodes wasted", (wastedNodeCount * 100.0) / (splitNodeCount)));
            System.out.println(String.format(D_FORMAT, "Targets wasted due to splitting", wastedTargetCount));
            System.out.println(String.format(D_FORMAT, "Total nodes executed", totalExecutedNodeCount));
            for (Map.Entry<Class<? extends Node>, Integer> entry : pollutedNodes.entrySet()) {
                System.out.println(String.format(D_LONG_FORMAT, entry.getKey(), entry.getValue()));
            }
        }
    }

    static class PollutionEvenGraph {
        int idCounter = 0;
        final List<DumpNode> nodes = new ArrayList<>();

        class DumpNode {

            DumpNode(Node node) {
                this.node = node;
            }

            final Node node;
            final int id = idCounter++;
            DumpEdge edge;
        }

        class DumpEdge {
            DumpEdge(DumpNode node) {
                this.node = node;
            }

            final DumpNode node;
        }

        enum DumpEdgeEnum {
            CHILD
        }

        DumpNode makeNode(Node node) {
            DumpNode n = new DumpNode(node);
            nodes.add(n);
            return n;
        }

        PollutionEvenGraph(List<OptimizedDirectCallNode> needsSplitCallNodes, List<Node> nodeChain) {
            DumpNode last = null;
            for (int i = 0; i < nodeChain.size(); i++) {
                if (i == 0) {
                    for (OptimizedDirectCallNode callNode : needsSplitCallNodes) {
                        makeNode(callNode);
                    }
                    last = makeNode(nodeChain.get(i));
                    for (DumpNode dumpNode : nodes) {
                        dumpNode.edge = new DumpEdge(last);
                    }
                } else {
                    DumpNode n = makeNode(nodeChain.get(i));
                    last.edge = new DumpEdge(n);
                    last = n;
                }
            }
        }
    }

    static class PollutionEventGraphStructure implements GraphStructure<PollutionEvenGraph, PollutionEvenGraph.DumpNode, PollutionEvenGraph.DumpNode, PollutionEvenGraph.DumpEdge> {

        @Override
        public PollutionEvenGraph graph(PollutionEvenGraph currentGraph, Object obj) {
            return (obj instanceof PollutionEvenGraph) ? (PollutionEvenGraph) obj : null;
        }

        @Override
        public Iterable<? extends PollutionEvenGraph.DumpNode> nodes(PollutionEvenGraph graph) {
            return graph.nodes;
        }

        @Override
        public int nodesCount(PollutionEvenGraph graph) {
            return graph.nodes.size();
        }

        @Override
        public int nodeId(PollutionEvenGraph.DumpNode node) {
            return node.id;
        }

        @Override
        public boolean nodeHasPredecessor(PollutionEvenGraph.DumpNode node) {
            return false;
        }

        @Override
        public void nodeProperties(PollutionEvenGraph graph, PollutionEvenGraph.DumpNode node, Map<String, ? super Object> properties) {
            properties.put("label", node.node.toString());
            properties.put("ROOT?", node.node instanceof RootNode);
            properties.put("LEAF?", node.edge == null);
            properties.put("RootNode", node.node.getRootNode());
            properties.putAll(node.node.getDebugProperties());
            properties.put("SourceSection", node.node.getSourceSection());
            if (Introspection.isIntrospectable(node.node)) {
                final List<Introspection.SpecializationInfo> specializations = Introspection.getSpecializations(node.node);
                for (Introspection.SpecializationInfo specialization : specializations) {
                    properties.put(specialization.getMethodName() + ".isActive", specialization.isActive());
                    properties.put(specialization.getMethodName() + ".isExcluded", specialization.isExcluded());
                    properties.put(specialization.getMethodName() + ".instances", specialization.getInstances());
                }
            }
        }

        @Override
        public PollutionEvenGraph.DumpNode node(Object obj) {
            return (obj instanceof PollutionEvenGraph.DumpNode) ? (PollutionEvenGraph.DumpNode) obj : null;
        }

        @Override
        public PollutionEvenGraph.DumpNode nodeClass(Object obj) {
            return (obj instanceof PollutionEvenGraph.DumpNode) ? (PollutionEvenGraph.DumpNode) obj : null;

        }

        @Override
        public PollutionEvenGraph.DumpNode classForNode(PollutionEvenGraph.DumpNode node) {
            return node;
        }

        @Override
        public String nameTemplate(PollutionEvenGraph.DumpNode nodeClass) {
            return "{p#label}";
        }

        @Override
        public Object nodeClassType(PollutionEvenGraph.DumpNode nodeClass) {
            return nodeClass.getClass();
        }

        @Override
        public PollutionEvenGraph.DumpEdge portInputs(PollutionEvenGraph.DumpNode nodeClass) {
            return null;
        }

        @Override
        public PollutionEvenGraph.DumpEdge portOutputs(PollutionEvenGraph.DumpNode nodeClass) {
            return nodeClass.edge;
        }

        @Override
        public int portSize(PollutionEvenGraph.DumpEdge port) {
            return port == null ? 0 : 1;
        }

        @Override
        public boolean edgeDirect(PollutionEvenGraph.DumpEdge port, int index) {
            return port != null;
        }

        @Override
        public String edgeName(PollutionEvenGraph.DumpEdge port, int index) {
            return "";
        }

        @Override
        public Object edgeType(PollutionEvenGraph.DumpEdge port, int index) {
            return PollutionEvenGraph.DumpEdgeEnum.CHILD;
        }

        @Override
        public Collection<? extends PollutionEvenGraph.DumpNode> edgeNodes(PollutionEvenGraph graph, PollutionEvenGraph.DumpNode node, PollutionEvenGraph.DumpEdge port, int index) {
            return Collections.singleton(node.edge.node);
        }
    }
}
