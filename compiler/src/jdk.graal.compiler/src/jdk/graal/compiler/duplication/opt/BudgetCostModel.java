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
package jdk.graal.compiler.duplication.opt;

import static jdk.graal.compiler.duplication.opt.OptimizationEffect.NO_BENEFIT;
import static jdk.graal.compiler.duplication.opt.OptimizationEffect.STOP;

import jdk.graal.compiler.core.common.NumUtil;

/**
 * A simple budget cost model that collects benefit and costs. The budget model allows compiler
 * optimizations to reason about the impact of a given optimization in terms of benefit and cost.
 *
 * Benefit and costs are numbers and optimization agnostic. A specific optimization specifies the
 * meaning of benefit and budget.
 *
 * Benefits and costs are summed up.
 */
public class BudgetCostModel {

    /**
     * The currently used budget.
     */
    protected double usedBudget = 0D;
    /**
     * The currently used benefit.
     */
    protected double overallBenefit = 0D;
    /**
     * The maximal budget that can be used by the optimization.
     */
    protected double maxBudget;
    /**
     * The maximal benefit seen so far.
     */
    private double maxBenefitSeen = Double.MIN_VALUE;
    /**
     * The maximal cost seen so far.
     */
    private double maxCostSeen = Double.MIN_VALUE;

    /**
     * Creates a new budget cost model with a given maximum budget.
     *
     * @param maxBudget The maximum budget to be used by this cost model.
     */
    public BudgetCostModel(double maxBudget) {
        this.maxBudget = maxBudget;
    }

    public void increaseBudget(double additionalBudget) {
        maxBudget += additionalBudget;
    }

    public void setUsedBudget(double budget) {
        this.usedBudget = budget;
    }

    /**
     * The last tradeoffs set after a call to {@link BudgetCostModel#potentialOpt(double, long)}.
     */
    private double lastBudgetAfter = Double.MIN_VALUE;
    private double lastBenefitAfter = Double.MIN_VALUE;

    /**
     * Computes an {@linkplain OptimizationEffect} effect for the given new cost and benefit. This
     * method only computes the effect of an optimization but does not apply it yet to the cost
     * model.
     *
     * The computation is cached if the user decides to apply it to the cost model later by calling
     * {@linkplain BudgetCostModel#applyLastOp()}.
     *
     * The new costs are adjusted by the overall used budget and added to the current budget. The
     * new benefit is added to the overall benefit. Based on the new used budget the budgeteffect of
     * the returned {@linkplain OptimizationEffect} is computed. It is computed based on the overall
     * current filling of the budget. The effects for direct cost and benefit are computed based on
     * the highest cost and benefit seen.
     *
     *
     * @param benefit the benefit of an optimization opportunity
     * @param cost the cost of an optimization opportunity
     * @return the effect benefit and cost have on the overall cost model expressed as an instance
     *         of {@linkplain OptimizationEffect}
     */
    public OptimizationEffect potentialOpt(double benefit, long cost) {
        // we exceeded the budget
        boolean budgetExceeded = false;
        if (usedBudget >= maxBudget) {
            budgetExceeded = true;
        }
        // we don't have any benefit
        if (isZero(benefit)) {
            return NO_BENEFIT;
        }

        assert NumUtil.assertPositiveDouble(cost);
        double budgetAfter = usedBudget + cost;
        assert NumUtil.assertPositiveDouble(budgetAfter);
        double benefitAfter = overallBenefit + benefit;

        double budgetOverFactor = budgetEffect(budgetAfter);
        assert NumUtil.assertPositiveDouble(budgetOverFactor);
        if (budgetExceeded) {
            if (cost > 0.D) {
                return STOP;
            } else {
                return new OptimizationEffect(benefit / maxBenefitSeen, 0, budgetOverFactor);
            }
        }
        double benefitEffect = maxBenefitSeen == Double.MIN_VALUE ? 1 : (benefit / maxBenefitSeen);
        double costEffect = maxCostSeen == Double.MIN_VALUE ? 1 : (cost / maxCostSeen);
        OptimizationEffect op = new OptimizationEffect(benefitEffect, costEffect, budgetOverFactor);

        lastBenefitAfter = benefitAfter;
        lastBudgetAfter = budgetAfter;

        maxBenefitSeen = Math.max(maxBenefitSeen, benefit);
        maxCostSeen = Math.max(maxCostSeen, cost);

        return op;
    }

    public double currentFilling() {
        return budgetEffect(usedBudget);
    }

    public double maximalBudget() {
        return maxBudget;
    }

    /**
     * Applies the last optimization operation to the current cost model. Benefit is increased by
     * the laster benefit computed, the same applies for costs.
     */
    public void applyLastOp() {
        if (lastBenefitAfter == Double.MIN_VALUE && lastBudgetAfter == Double.MIN_VALUE) {
            return;
        }
        usedBudget = lastBudgetAfter;
        overallBenefit = lastBenefitAfter;
        assert NumUtil.assertFiniteDouble(usedBudget);
        assert NumUtil.assertFiniteDouble(overallBenefit);
        // reset to non-set marker value
        lastBudgetAfter = Double.MIN_VALUE;
        lastBenefitAfter = Double.MIN_VALUE;
    }

    public double usedBudget() {
        return usedBudget;
    }

    public double overallBenefit() {
        return overallBenefit;
    }

    private double budgetEffect(double d) {
        assert NumUtil.assertFiniteDouble(d);
        return maxBudget == 0 ? d : d / maxBudget;
    }

    private static boolean isZero(double d) {
        return d == 0;
    }

    public static final double HALF_FILLING = 0.5D;

}
