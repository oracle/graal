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

/**
 * Describes the effect of an optimization on the overall benefit and budget of a cost model.
 */
public final class OptimizationEffect {
    private final double directBenefitEffect;
    private final double directCostEffect;
    private final double budgetFilling;

    public OptimizationEffect(double directBenefitEffect, double directCostEffect, double budgetFilling) {
        super();
        this.directBenefitEffect = directBenefitEffect;
        this.directCostEffect = directCostEffect;
        this.budgetFilling = budgetFilling;
    }

    /**
     * Gets the direct benefit factor of an optimization. This factor reflects how the benefit of
     * the optimization compares against the best benefit seen so far.
     *
     * @return the direct benefit factor of an optimization.
     */
    public double getDirectBenefitFactor() {
        return directBenefitEffect;
    }

    /**
     * Gets the direct cost factor of an optimization. This factor reflects how the cost of the
     * optimization compares against the highest cost seen so far.
     *
     * @return the direct cost factor of an optimization.
     */
    public double getDirectCostFactor() {
        return directCostEffect;
    }

    /**
     * Gets the budget filling of the cost model after the optimization.
     *
     * @return the budget filling of the cost model, a value between {@code [0,1]}
     */
    public double getBudgetFilling() {
        return budgetFilling;
    }

    public boolean budgetExceeded() {
        return budgetFilling > 1.0D;
    }

    public static final OptimizationEffect STOP = new OptimizationEffect(-1, -1, Double.MAX_VALUE);
    public static final OptimizationEffect NO_BENEFIT = new OptimizationEffect(0, 0, 0);
}
