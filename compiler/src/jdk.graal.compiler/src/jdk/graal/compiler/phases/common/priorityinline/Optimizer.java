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
package jdk.graal.compiler.phases.common.priorityinline;

import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.MaxPriorityInliningPeelingIterations;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.UsePriorityInliningPEA;

import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.loop.phases.LoopPeelingPhase;
import jdk.graal.compiler.loop.phases.LoopTransformations;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.DeletedNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.InlineCacheNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;

public class Optimizer {

    private static final int MAX_PEELS_PER_LOOP = 10;
    private static final double FREQUENCY_UPDATE_THRESHOLD = 0.1;

    private final EconomicMap<LoopBeginNode, Integer> peelCounts = EconomicMap.create();
    private int escapeAnalysisCount = 0;
    private TimerKey optimizationDuration;

    private int escapeAnalysisIndicator = 0;

    public Optimizer(TimerKey optimizationDuration) {
        this.optimizationDuration = optimizationDuration;
    }

    private boolean run(CallTree callTree, boolean forcedEscapeAnalysis, boolean enablePeeling, CoreProviders providers) {
        StructuredGraph compilerGraph = callTree.root().getReadonlySubgraph();
        long graphSizeBeforeOptimizations = compilerGraph.getNodeCount();
        Mark mark = compilerGraph.getMark();

        tryEscapeAnalyzeGraph(callTree, compilerGraph, forcedEscapeAnalysis);

        if (enablePeeling) {
            int iterations = 0;
            int maxPeelingIterations = MaxPriorityInliningPeelingIterations.getValue(callTree.getOptions());
            while (iterations < maxPeelingIterations && !callTree.getPolicy().shouldStopPeeling(callTree)) {
                if (!tryPeeling(callTree, compilerGraph, providers)) {
                    break;
                }
                tryEscapeAnalyzeGraph(callTree, compilerGraph, true);
                iterations++;
            }
        }

        long spendingDifference = Math.max(0, compilerGraph.getNodeCount() - graphSizeBeforeOptimizations);
        callTree.addSpending(spendingDifference);
        return updateTree(callTree, compilerGraph, mark);
    }

    @SuppressWarnings("try")
    public boolean performEscapeAnalysis(CallTree callTree, CoreProviders providers) {
        try (DebugCloseable c = optimizationDuration.start(callTree.getDebug())) {
            return run(callTree, true, false, providers);
        }
    }

    @SuppressWarnings("try")
    public boolean performPeeling(CallTree callTree, CoreProviders providers) {
        try (DebugCloseable c = optimizationDuration.start(callTree.getDebug())) {
            return run(callTree, false, true, providers);
        }
    }

    private void tryEscapeAnalyzeGraph(CallTree callTree, StructuredGraph compilerGraph, boolean forced) {
        if (GraalOptions.PartialEscapeAnalysis.getValue(callTree.getOptions()) && UsePriorityInliningPEA.getValue(callTree.getOptions())) {
            if (forced || escapeAnalysisIndicator <= 0) {
                new PartialEscapePhase(false, false, callTree.getCanonicalizer(), null, compilerGraph.getOptions()).apply(compilerGraph, callTree.getContext());
                escapeAnalysisCount++;
                escapeAnalysisIndicator = InliningMath.defaultFrequencyForAllOptimizations(callTree);
            } else {
                if (!callTree.state().hasInlinedSinceLastExpansion()) {
                    escapeAnalysisIndicator = escapeAnalysisIndicator / 2;
                } else {
                    escapeAnalysisIndicator -= 1;
                }
            }
        }
    }

    @SuppressWarnings("try")
    private boolean tryPeeling(CallTree callTree, StructuredGraph compilerGraph, CoreProviders coreProviders) {
        EconomicSet<LoopBeginNode> loopsToPeel = findLoopsToPeel(compilerGraph);
        if (loopsToPeel != null) {
            EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
            try (NodeEventScope nes = compilerGraph.trackNodeEvents(listener)) {
                LoopsData data = coreProviders.getLoopsDataProvider().getLoopsData(compilerGraph);
                for (Loop loop : data.outerFirst()) {
                    if (!LoopPeelingPhase.canPeel(loop)) {
                        // we must never peel loops that must not be duplicated
                        continue;
                    }
                    if (loopsToPeel.contains(loop.loopBegin())) {
                        DebugContext debug = compilerGraph.getDebug();
                        debug.log("Peeling %s", loop);
                        incrementPeelCount(loop.loopBegin());
                        LoopTransformations.peel(loop);
                        debug.dump(DebugContext.VERBOSE_LEVEL, compilerGraph, "Peeling %s", loop);
                    }
                }
                data.deleteUnusedNodes();
            }
            callTree.getCanonicalizer().applyIncremental(compilerGraph, callTree.getContext(), listener.getNodes());
            return true;
        }
        return false;
    }

    private int getPeelCount(LoopBeginNode node) {
        return peelCounts.get(node, 0);
    }

    private void incrementPeelCount(LoopBeginNode node) {
        peelCounts.put(node, getPeelCount(node) + 1);
    }

    private EconomicSet<LoopBeginNode> findLoopsToPeel(StructuredGraph graph) {
        EconomicSet<LoopBeginNode> loopsToPeel = null;
        for (LoopBeginNode node : graph.getNodes(LoopBeginNode.TYPE)) {
            if (shouldPeel(node)) {
                if (loopsToPeel == null) {
                    loopsToPeel = EconomicSet.create(Equivalence.IDENTITY);
                }
                loopsToPeel.add(node);
            }
        }
        return loopsToPeel;
    }

    private boolean shouldPeel(LoopBeginNode loopBegin) {
        if (getPeelCount(loopBegin) >= MAX_PEELS_PER_LOOP) {
            return false;
        }
        for (PhiNode phi : loopBegin.phis()) {
            Stamp stamp = phi.stamp(NodeView.DEFAULT);
            if (stamp instanceof ObjectStamp) {
                ObjectStamp objectStamp = (ObjectStamp) stamp;
                ValueNode firstValue = phi.firstValue();
                ObjectStamp firstStamp = (ObjectStamp) firstValue.stamp(NodeView.DEFAULT);
                if (firstValue instanceof VirtualizableAllocation) {
                    for (int i = 1; i < phi.valueCount(); i++) {
                        ValueNode backedgeValue = phi.valueAt(i);
                        ObjectStamp backedgeStamp = (ObjectStamp) backedgeValue.stamp(NodeView.DEFAULT);
                        if (!Objects.equals(firstStamp.type(), backedgeStamp.type())) {
                            return true;
                        }
                    }
                } else {
                    ObjectStamp backedgeStamp = (ObjectStamp) phi.valueAt(1).stamp(NodeView.DEFAULT);
                    for (int j = 2; j < phi.valueCount(); ++j) {
                        backedgeStamp = (ObjectStamp) backedgeStamp.meet(phi.valueAt(j).stamp(NodeView.DEFAULT));
                    }
                    if (!Objects.equals(backedgeStamp.type(), objectStamp.type())) {
                        for (Node n : phi.usages()) {
                            if (n instanceof MethodCallTargetNode) {
                                MethodCallTargetNode methodCallTargetNode = (MethodCallTargetNode) n;
                                if (!methodCallTargetNode.isStatic() && methodCallTargetNode.arguments().get(0) == phi) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean updateTree(CallTree callTree, StructuredGraph compilerGraph, Mark mark) {
        final SubgraphNode root = callTree.root();
        boolean childrenChanged = checkAfterChange(root);
        ControlFlowGraph newCFG = ControlFlowGraph.newBuilder(compilerGraph).connectBlocks(true).computeFrequency(true).build();
        for (Node node : compilerGraph.getNewNodes(mark)) {
            if (node instanceof Invoke) {
                double frequency = InliningMath.restrictFrequency(newCFG.blockFor(node).getRelativeFrequency());
                CallTreeNode newChild = callTree.createChild(root, (Invoke) node, frequency);
                root.addChild(newChild);
                if (newChild instanceof InlineCacheNode) {
                    callTree.restoreSubtreeInvariants(newChild, true);
                }
                childrenChanged = true;
            }
        }
        for (CallTreeNode topLevelChild : root.children()) {
            Invoke invoke = topLevelChild.invoke();
            if (invoke.asNode().isAlive()) {
                double newFrequency = InliningMath.restrictFrequency(newCFG.blockFor(invoke.asNode()).getRelativeFrequency());
                double factor = newFrequency / Math.max(0.01, topLevelChild.getFrequency());
                if (Math.abs(1.0 - factor) > FREQUENCY_UPDATE_THRESHOLD) {
                    topLevelChild.setFrequency(Math.max(0.01, topLevelChild.getFrequency()));
                    topLevelChild.adjustSubtreeFrequency(factor);
                }
                callTree.restoreSubtreeInvariants(topLevelChild, false);
            }
        }
        for (CallTreeNode topLevelChild : root.children()) {
            childrenChanged |= topLevelChild.enhanceParameters();
        }
        callTree.restoreSubtreeInvariants(root, false);
        return childrenChanged;
    }

    private static boolean checkAfterChange(CallTreeNode node) {
        CallTree callTree = node.callTree();
        boolean childrenChanged = false;
        for (CallTreeNode child : node.children()) {
            if (child.invoke().asNode().isDeleted()) {
                DeletedNode deletedNode = child.replaceWithDeleted();
                callTree.restoreSubtreeInvariants(deletedNode, false);
                childrenChanged = true;
            }
        }
        return childrenChanged;
    }

    public int getEscapeAnalysisCount() {
        return escapeAnalysisCount;
    }
}
