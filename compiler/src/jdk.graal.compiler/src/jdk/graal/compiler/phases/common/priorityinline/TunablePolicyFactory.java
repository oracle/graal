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

import java.util.Collections;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitCostTuple;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.InlineCacheNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.ParentNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;
import jdk.graal.compiler.serviceprovider.ServiceProvider;

/**
 * The tunable inlining policy allows specifying the amount of inlining.
 *
 * The higher the {@code InliningCoefficient}, the more code will be inlined, which usually results
 * in better performance, and an increase in code size.
 */
@ServiceProvider(PolicyFactory.class)
public class TunablePolicyFactory extends DefaultPolicyFactory {

    public static class Options {
        //@formatter:off
        @Option(help = "The coefficient used to compute the inlining threshold; the higher, the more to inline.", type = OptionType.Debug)
        public static final OptionKey<Double> InliningCoefficient = new OptionKey<>(0.02);
        //@formatter:on
    }

    @Override
    public int priority() {
        // Must be lower than TruffleInliningPolicyFactory
        return 5;
    }

    @Override
    public Inliner.Policy createInlinerPolicy(OptionValues options) {
        return new InlinerPolicy();
    }

    private static final class InlinerPolicy extends Inliner.DefaultPolicy {

        private static boolean hasAllChildrenExpanded(InlineCacheNode node) {
            for (CallTreeNode child : node.children()) {
                if (child instanceof CutoffNode) {
                    return false;
                }
            }
            return true;
        }

        private static void updateCostBenefitTuple(CallTreeNode node) {
            if (node instanceof InlineCacheNode && !hasAllChildrenExpanded((InlineCacheNode) node)) {
                // We do not consider an inline cache node for inlining unless it has been fully
                // expanded, and it has at least one inlineable child.
                node.setNonInlinedDescendants(Collections.emptyList());
                node.setCostBenefitTuple(BenefitCostTuple.IMPOSSIBLE);
                node.setInlineAllCostBenefitTuple(BenefitCostTuple.IMPOSSIBLE);
                return;
            }
            if (!(node instanceof ParentNode)) {
                node.setCostBenefitTuple(BenefitCostTuple.IMPOSSIBLE);
                return;
            }
            node.setCostBenefitTuple(new BenefitCostTuple(node.getLocalBenefit(), node instanceof SubgraphNode ? ((SubgraphNode) node).getLocalCost() : BenefitCostTuple.ZERO_COST));
            node.setNonInlinedDescendants(node.children());
        }

        @Override
        protected boolean isWithinBudget(CallTreeNode node, int expansionRound) {
            BenefitCostTuple tuple = node.getCostBenefit();
            double relativeBenefit = tuple.relativeBenefit();
            double inliningCoefficient = Options.InliningCoefficient.getValue(node.getOptions());
            long baseSpending = (long) (120 + (3000 - 120) * inliningCoefficient);
            long nodesSpent = node.callTree().getCurrentSpending();
            long overBudgetFactor = (nodesSpent + tuple.getCost()) / baseSpending;
            overBudgetFactor += expansionRound / 100;
            double inliningDivisor = inliningCoefficient * 10000;
            double threshold = (1 + overBudgetFactor) * (1 << (overBudgetFactor >> 4)) / inliningDivisor;
            boolean decision = relativeBenefit > threshold;
            logDecision(node, decision, overBudgetFactor, relativeBenefit);
            return decision;
        }

        @Override
        public void analyzeCostBenefit(CallTree callTree) {
            callTree.filteredPostOrder(CallTreeNode::needsCostBenefitUpdate, InlinerPolicy::updateCostBenefitTuple);
        }
    }
}
