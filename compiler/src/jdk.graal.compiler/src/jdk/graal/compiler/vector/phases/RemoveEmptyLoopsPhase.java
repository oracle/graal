/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.phases;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeWorkList;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariableHelper;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.RecursivePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.util.CollectionsUtil;

/**
 * Tries to remove loops which do nothing apart from computations where the net result can be
 * determined by the compiler and reduced to a simple value.
 *
 * For example:
 *
 * <pre>
 * int i = start;
 * if (i < 1000) {
 *     do {
 *         i++;
 *     } while (i < 1000);
 * }
 * return i;
 * </pre>
 *
 * becomes:
 *
 * <pre>
 * if (start < 1000) {
 *     return 1000
 * }
 * return start;
 * </pre>
 */
public class RemoveEmptyLoopsPhase extends PostRunCanonicalizationPhase<CoreProviders> implements RecursivePhase {

    public RemoveEmptyLoopsPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public boolean shouldApply(StructuredGraph graph) {
        return graph.hasLoops();
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        while (graph.hasLoops()) {
            boolean removedLoop = false;
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            for (Loop loop : loopsData.loops()) {
                if (!loop.loopBegin().isAlive()) {
                    // This may be a loop nested inside one that has already been removed because it
                    // could never be entered.
                    continue;
                }
                if (loop.detectCounted() && (loop.counted().counterNeverOverflows() || loop.counted().getOverFlowGuard() != null)) {
                    removedLoop = tryRemoveEmptyLoop(graph, loop);
                }
                loop.deleteUnusedNodes();
                if (removedLoop) {
                    break;
                }
            }
            if (!removedLoop) {
                break;
            }
        }
    }

    @SuppressWarnings("try")
    private static boolean tryRemoveEmptyLoop(StructuredGraph graph, final Loop loop) {
        if (loop.loopBegin().loopEnds().count() > 1) {
            return false;
        }

        final boolean beforeFSA = loop.loopBegin().stateAfter() != null;

        // skip all value anchors, infopoints, and unused reads inside the loop
        FixedNode node = loop.loopBegin().next();
        outer: while (node != null) {
            if (node instanceof IfNode && node == loop.counted().getLimitTest()) {
                // Continue on the non-exiting branch. For head counted loops this is the
                // regular body, for inverted ones it should just be the begin node.
                node = loop.counted().getCountedExit() == ((IfNode) node).trueSuccessor() ? ((IfNode) node).falseSuccessor() : ((IfNode) node).trueSuccessor();
                continue;
            }
            for (Node usage : node.usages()) {
                if (usage instanceof PiNode pi && loop.getInductionVariables().get(pi) != null) {
                    /*
                     * A Pi is a DerivedConvertedInductionVariable if its input is an IV. Normal IV
                     * handling will take care of it by replacing it by its exit value.
                     */
                } else {
                    /* Any other anchored value prevents removal of the loop. */
                    break outer;
                }
            }
            if (safeNodeToSkip(node)) {
                node = ((FixedWithNextNode) node).next();
            } else {
                break;
            }
        }

        if (!(node instanceof LoopEndNode) && loop.counted().loopMightBeEntered()) {
            // loop is not empty and might be entered
            return false;
        }

        // Find all values derived from induction variables that have outside usages.
        List<InductionVariable> ivs = new ArrayList<>();
        List<PhiNode> optimizablePhis = new ArrayList<>();
        outer: for (PhiNode phi : loop.loopBegin().usages().filter(PhiNode.class)) {
            if (isOptimizablePhi(phi, loop)) {
                optimizablePhis.add(phi);
                continue;
            }

            NodeWorkList worklist = graph.createNodeWorkList();
            worklist.add(phi);
            for (Node n : worklist) {
                for (Node usage : n.usages()) {
                    if (loop.isOutsideLoop(usage) || beforeFSA) {
                        /*
                         * Before FSA, even if phi has no outside usage, it still has to be an IV so
                         * that an exit value for the stateAfter can be computed. Otherwise, deopts
                         * after the loop can fall back to the state before the loop.
                         */
                        InductionVariable iv = loop.getInductionVariables().get(phi);
                        if (iv == null) {
                            // loop can not be removed, since it contains complex arithmetic
                            return false;
                        } else {
                            ivs.add(iv);
                            continue outer;
                        }
                    } else {
                        worklist.add(usage);
                    }
                }
            }
        }

        /*
         * Replace induction variables with extremum values: first create them and then replace them
         * to avoid having to think about an order when deleting dependent (on the init node for the
         * max trip count) IVs.
         */
        EconomicMap<InductionVariable, ValueNode> exitValue = EconomicMap.create();
        for (InductionVariable iv : ivs) {
            exitValue.put(iv, iv.exitValueNode());
        }

        // move optimizable phis to a new if node
        if (!optimizablePhis.isEmpty() && loop.loopBegin().forwardEnd().predecessor() instanceof FixedWithNextNode) {
            try (DebugCloseable position = loop.loopBegin().withNodeSourcePosition()) {
                // Create an if node to determine if the loop can be entered, given the initial
                // values.
                MergeNode merge = graph.add(new MergeNode());
                if (loop.loopBegin().stateAfter() != null) {
                    /* We're running before FSA. */
                    merge.setStateAfter(loop.loopBegin().stateAfter());
                }
                EndNode trueEnd = graph.add(new EndNode());
                EndNode falseEnd = graph.add(new EndNode());
                merge.addForwardEnd(trueEnd);
                merge.addForwardEnd(falseEnd);

                boolean exitOnFalse = (loop.counted().getCountedExit() == loop.counted().getLimitTest().falseSuccessor());
                LogicNode exitCondition = (LogicNode) initialValue(loop, loop.counted().getLimitTest().condition());
                LogicNode loopBypassed = (exitOnFalse ? graph.addOrUniqueWithInputs(LogicNegationNode.create(exitCondition)) : exitCondition);

                IfNode ifNode = graph.add(new IfNode(loopBypassed, BeginNode.begin(trueEnd), BeginNode.begin(falseEnd), BranchProbabilityNode.NOT_LIKELY_PROFILE));
                AbstractEndNode forwardEnd = loop.loopBegin().forwardEnd();
                ((FixedWithNextNode) forwardEnd.predecessor()).setNext(ifNode);
                merge.setNext(loop.loopBegin().forwardEnd());

                for (PhiNode optimizablePhi : optimizablePhis) {
                    optimizablePhi.setMerge(merge);
                    ValueNode loopEnteredValue = optimizablePhi.valueAt(1);
                    if (loop.getInductionVariables().containsKey(loopEnteredValue)) {
                        /**
                         * A phi using an IV gets a value delayed by one iteration, i.e., in:
                         *
                         * <pre>
                         * int lastValue = -1;
                         * for (int i = 0; i < limit; i++) {
                         *     lastValue = i;
                         * }
                         * </pre>
                         *
                         * after the loop (if entered) i will equal limit, but lastValue will be the
                         * last value from inside the loop, i.e., limit - 1.
                         */
                        InductionVariable iv = loop.getInductionVariables().get(loopEnteredValue);
                        optimizablePhi.setValueAt(1, InductionVariableHelper.previousIteration(iv).valueNode());
                    }
                }
            }
        } else if (beforeFSA) {
            // keep the stateAfter with the folded IVs alive for potential deopts
            var proxy = graph.add(new StateSplitProxyNode(loop.loopBegin().stateAfter()));
            graph.addBeforeFixed(loop.loopBegin().forwardEnd(), proxy);
        }

        MapCursor<InductionVariable, ValueNode> cursor = exitValue.getEntries();
        while (cursor.advance()) {
            cursor.getKey().valueNode().replaceAtUsagesAndDelete(cursor.getValue());
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing IV %s", cursor.getKey());
        }

        loop.loopBegin().removeSafepoints();

        // remove the loop by simplifying the counter test.
        LogicNode condition = loop.counted().getLimitTest().condition();
        graph.removeSplitPropagate(loop.counted().getLimitTest(), loop.counted().getCountedExit());
        GraphUtil.tryKillUnused(condition);
        graph.getOptimizationLog().report(RemoveEmptyLoopsPhase.class, "LoopRemoval", loop.loopBegin());
        return true;
    }

    /**
     * Determines if the current node is "empty" with respect to this transformation, i.e., it can be skipped.
     */
    private static boolean safeNodeToSkip(FixedNode node) {
        return node instanceof ValueAnchorNode || node instanceof SafepointNode || (node instanceof ReadNode read && !read.ordersMemoryAccesses()) || node instanceof BeginNode;
    }

    /**
     * Determines if the given {@code usage}, if it is a usage of a phi on the loop, is to be
     * considered outside the loop for the purposes of empty loop removal. This includes not only
     * nodes strictly outside the loop, but also states and loop proxies. Proxies are no longer
     * present during RemoveEmptyLoopsPhase, but this information may be queried by other phases
     * earlier in the pipeline.
     */
    private static boolean isOutsidePhiUsage(Node usage, Loop loop) {
        return loop.isOutsideLoop(usage) ||
                        (usage instanceof ValueProxyNode proxy && proxy.proxyPoint().loopBegin() == loop.loopBegin()) ||
                        usage instanceof FrameState;
    }

    /**
     * Determines whether the non-IV {@code phi} can be transformed to a closed form and attached to
     * a merge node for an {@code if} that only checks if the original loop would have been entered.
     * This method is used by {@link RemoveEmptyLoopsPhase} itself but may also be called by other
     * phases earlier in the pipeline.
     */
    public static boolean isOptimizablePhi(PhiNode phi, Loop loop) {
        if (!CollectionsUtil.allMatch(phi.usages(), usage -> isOutsidePhiUsage(usage, loop))) {
            return false;
        }
        if (!loop.isOutsideLoop(phi.firstValue())) {
            return false;
        }
        for (int i = 1; i < phi.valueCount(); i++) {
            ValueNode loopValue = phi.valueAt(i);
            if (!(loop.isOutsideLoop(loopValue) || loop.getInductionVariables().get(loopValue) != null)) {
                return false;
            }
        }

        return true;
    }

    private static Node initialValue(Loop loop, Node node) {
        // Compute the value of the given node during the first iteration of the loop.
        if (loop.isOutsideLoop(node) || !(node instanceof ValueNode)) {
            return node;
        } else if (loop.getInductionVariables().containsKey(node)) {
            InductionVariable iv = loop.getInductionVariables().get(node);
            return iv.initNode();
        } else {
            ValueNode copy = (ValueNode) node.copyWithInputs();
            for (Position position : copy.inputPositions()) {
                Node input = position.get(copy);
                if (input instanceof ValueNode) {
                    ValueNode initialValue = (ValueNode) initialValue(loop, input);
                    if (initialValue != input) {
                        position.set(copy, initialValue);
                    }
                }
            }
            return copy;
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 2.5f;
    }
}
