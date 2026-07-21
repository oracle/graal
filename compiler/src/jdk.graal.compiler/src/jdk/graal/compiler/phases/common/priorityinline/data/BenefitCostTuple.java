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
package jdk.graal.compiler.phases.common.priorityinline.data;

import jdk.graal.compiler.core.common.NumUtil;

public final class BenefitCostTuple implements Comparable<BenefitCostTuple> {

    public static final double ZERO_BENEFIT = 0.0D;

    public static final double ZERO_COST_ADJUSTMENT = 1e-7;
    public static final int ZERO_COST = 0;
    public static final int INFINITE_COST = Integer.MAX_VALUE;

    private final double benefit;
    private final int cost;

    public static final BenefitCostTuple NEUTRAL = new BenefitCostTuple(ZERO_BENEFIT, ZERO_COST);
    public static final BenefitCostTuple IMPOSSIBLE = new BenefitCostTuple(ZERO_BENEFIT, INFINITE_COST);

    public static BenefitCostTuple createFreeBenefit(double benefit) {
        return new BenefitCostTuple(benefit, ZERO_COST);
    }

    public BenefitCostTuple(double benefit, int cost) {
        this.benefit = benefit;
        this.cost = cost;
    }

    public double getBenefit() {
        return benefit;
    }

    public boolean hasNegativeBenefit() {
        return !hasPositiveBenefit();
    }

    public boolean hasPositiveBenefit() {
        return benefit >= ZERO_BENEFIT;
    }

    public boolean isImpossible() {
        return cost == INFINITE_COST;
    }

    public boolean isPossible() {
        return !isImpossible();
    }

    public BenefitCostTuple add(BenefitCostTuple other) {
        double newBenefit = benefit + other.benefit;
        return new BenefitCostTuple(Math.abs(newBenefit) <= ZERO_COST_ADJUSTMENT ? 0.0D : newBenefit, sumCost(cost, other.cost));
    }

    private static int sumCost(int cost0, int cost1) {
        assert NumUtil.assertNonNegativeInt(cost0);
        assert NumUtil.assertNonNegativeInt(cost1);
        int sum = cost0 + cost1;
        return sum >= 0 ? sum : INFINITE_COST;
    }

    @Override
    public int compareTo(BenefitCostTuple other) {
        if (other.isImpossible()) {
            if (isImpossible()) {
                return 0;
            } else {
                return -1;
            }
        } else if (isImpossible()) {
            assert !other.isImpossible() : "Other must not be impossible " + other;
            return 1;
        }

        double v1 = benefit / (ZERO_COST_ADJUSTMENT + cost);
        double v2 = other.benefit / (ZERO_COST_ADJUSTMENT + other.cost);
        if (v1 > v2) {
            return -1;
        } else if (v2 > v1) {
            return 1;
        } else {
            return 0;
        }
    }

    public boolean isBetterThan(BenefitCostTuple other) {
        return compareTo(other) < 0;
    }

    public double relativeBenefit() {
        if (isImpossible()) {
            return ZERO_BENEFIT;
        }
        return this.benefit / (ZERO_COST_ADJUSTMENT + cost);
    }

    public BenefitCostTuple tryImprove(BenefitCostTuple other) {
        if (isBetterThan(other)) {
            return null;
        }
        return add(other);
    }

    @Override
    public String toString() {
        return String.format("[%4f / %s]", benefit, (isImpossible() ? "inf" : cost));
    }

    public int getCost() {
        return cost;
    }

    public boolean isZeroCost() {
        return cost == ZERO_COST;
    }
}
