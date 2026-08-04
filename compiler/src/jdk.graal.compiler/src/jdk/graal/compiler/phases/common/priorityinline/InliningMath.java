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

import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.BaseTargetSpending;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.ExpandAllProximityBonus;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.ExpandAllProximityBonusInertia;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.ExpansionInertiaMax;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.RelativeBenefitInliningCoefficient;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.TypicalGraphSizeMax;

import java.util.EnumSet;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitCostTuple;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitKind;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.ParentNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class InliningMath {
    private static final int COST_NODE_DISCOUNT = 20;
    private static final int IC_CHECK_NODE_COST = 10;
    private static final int MAX_OPTIMIZATION_PERIOD = 30;
    private static final int LARGE_GRAPH_SIZE = 600;
    private static final int EXPANSION_LIMIT_PER_RECURSIVE_METHOD = 10;
    private static final double MINIMUM_FREQUENCY_FOR_EXPANSION = 1e-3;
    private static final int RECURSION_DEPTH_LIMIT = 8;

    /**
     * Restrict the inputFrequency to [0.01d, 100.0d].
     */
    public static double restrictFrequency(double inputFrequency) {
        assert NumUtil.assertNonNegativeDouble(inputFrequency);
        if (inputFrequency < 0.01d) {
            return 0.01d;
        }
        if (inputFrequency > 100.0d) {
            return 100.0d;
        }
        return inputFrequency;
    }

    /**
     * Estimate the code size cost of a single node in the call tree.
     */
    public static int getLocalCost(StructuredGraph compilerGraph, boolean requiresDispatching) {
        // We favor trivial methods that are with less than 20 nodes.
        int cost = compilerGraph.getNodeCount() - COST_NODE_DISCOUNT;
        if (requiresDispatching) {
            cost += IC_CHECK_NODE_COST;
        }
        return Math.max(1, cost);
    }

    /**
     * Estimate the code size cost of a method, without inspecting the IR of that method. This
     * method is typically used before parsing the IR from the bytecode.
     */
    public static int getLocalCostEstimate(ResolvedJavaMethod resolvedJavaMethod) {
        return Math.max(1, (resolvedJavaMethod.getCodeSize() - COST_NODE_DISCOUNT) / 2);
    }

    /**
     * Compute the recursion penalty of an unexpanded node in the call tree. The recursion penalty
     * grows exponentially with the recursion depth.
     */
    public static double defaultRecursionPenalty(CutoffNode cutoffNode) {
        int depth = cutoffNode.getRecursionDepth();
        double recursionPenalty = 0.0D;
        recursionPenalty += Math.max(0.0D, depth - 2 + Math.pow(2.0D, depth));

        // Make sure to scale recursion penalty with frequency, and conservative lower bound
        // which is biased against methods that have low frequency anyway.
        recursionPenalty *= Math.max(1.0D, cutoffNode.getFrequency());

        return recursionPenalty;
    }

    /**
     * Compute the lock bonus for exploration, if the call is within an acquired monitor.
     */
    public static double defaultLockBonus(CutoffNode cutoffNode) {
        if (cutoffNode.invoke() != null && cutoffNode.invoke().stateAfter() != null) {
            return cutoffNode.invoke().stateAfter().locksSize() > 0 ? cutoffNode.getFrequency() : 0.0D;
        }
        return 0;
    }

    /**
     * Compute the number of inlining rounds after which the inliner must perform all optimizations.
     */
    public static int defaultFrequencyForAllOptimizations(CallTree callTree) {
        int nodeCount = callTree.root().getReadonlySubgraph().getNodeCount();
        return (int) (1 + MAX_OPTIMIZATION_PERIOD * Math.min(1.0, 1.0 * nodeCount / LARGE_GRAPH_SIZE));
    }

    /**
     * Default limit of expansions of a recursive call to a particular method, across the entire
     * graph.
     */
    public static int defaultExpansionLimitPerRecursiveMethod() {
        return EXPANSION_LIMIT_PER_RECURSIVE_METHOD;
    }

    /**
     * Maximum allowed depth of recursion.
     */
    public static int defaultMaximumRecursionDepth() {
        return RECURSION_DEPTH_LIMIT;
    }

    /**
     * Default minimum frequency for expansion.
     */
    public static double defaultMinimumFrequencyForExpansion() {
        return MINIMUM_FREQUENCY_FOR_EXPANSION;
    }

    /**
     * Compute the default value of the benefit amplifier.
     */
    public static double defaultBenefitAmplifier(CallTreeNode node) {
        if (node instanceof ParentNode || node instanceof CutoffNode) {
            final EnumSet<BenefitKind> benefits = node instanceof ParentNode ? ((ParentNode) node).getBenefits() : ((CutoffNode) node).getBenefits();
            return BenefitKind.containsTypeOrConstant(benefits) ? 2.0D : 1.0D;
        } else {
            return 1.0D;
        }
    }

    /**
     * Compute the adaptive expansion threshold for expanding the target node.
     */
    public static double defaultExpansionThreshold(Expander.Policy policy, CutoffNode node) {
        OptionValues options = node.getOptions();
        CallTree callTree = node.callTree();
        SubgraphNode root = callTree.root();
        final double expansionInertia = Math.min(policy.expansionInertiaBaseValue(options) + policy.expansionInertiaInvokeBonus(options) * callTree.getInitialRootInvokes(),
                        ExpansionInertiaMax.getValue(options));
        final double typicalGraphSize = Math.min(policy.typicalGraphSize(options) + policy.typicalGraphSizeInvokeBonus(options) * callTree.getInitialRootInvokes(),
                        TypicalGraphSizeMax.getValue(options));

        // Lower the threshold slightly if it is likely that the entire tree could be inlined.
        int cutoffCount = root.cutoffCount();
        double inlineAllLikelihood = Math.max(0, ExpandAllProximityBonus.getValue(options) -
                        cutoffCount * cutoffCount / ExpandAllProximityBonusInertia.getValue(options));

        // Calculate the exponent for the threshold.
        double totalNodeCountExponent = (root.getSubtreeTotalCompilerNodeCount() - typicalGraphSize) / expansionInertia;
        return Math.exp(totalNodeCountExponent - inlineAllLikelihood);
    }

    /**
     * Compute the adaptive inlining threshold for inlining the target node.
     */
    public static double defaultInliningThreshold(CallTreeNode node, int expansionRound) {
        int baseTargetSpending = BaseTargetSpending.getValue(node.getOptions());
        double relativeBenefitCoefficient = RelativeBenefitInliningCoefficient.getValue(node.getOptions());
        BenefitCostTuple tuple = node.getCostBenefit();
        long nodesSpent = node.callTree().getCurrentSpending();
        long overBudgetFactor = (nodesSpent + tuple.getCost()) / baseTargetSpending;
        overBudgetFactor += expansionRound / 100;
        return relativeBenefitCoefficient * (1 + overBudgetFactor) * (1 << (overBudgetFactor >> 4));
    }
}
