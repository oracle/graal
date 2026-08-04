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
package jdk.graal.compiler.duplication.phases.simulation;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.FixedNode;

import jdk.graal.compiler.duplication.opt.BudgetCostModel;

/**
 * A cost function deciding which duplications should be made.
 */
public interface DuplicationCostFunction {

    /**
     * Determines if the given {@linkplain SimulationEndInfo} should be duplicated. The latest post
     * dominating fixed node is regionEnd that is still valid.
     *
     */
    DuplicationDecision shouldDuplicate(DuplicationConfig factors, SimulationEndInfo s, FixedNode regionEnd, int phisLastIterationCreated, int iteration);

    /**
     * Must be called after duplication to inform cost models about the duplicated code.
     */
    void afterDuplication(SimulationEndInfo s);

    /**
     * Determines if the duplication budget is already exceeded.
     *
     * @return {@code true} if there is still code budget left for the duplication,{@code false}
     *         else
     */
    boolean stopDuplication(DebugContext debug);

    /**
     *
     * @return the overall benefit reached by this cost function.
     */
    double overallBenefit();

    /**
     *
     * @return The {@linkplain BudgetCostModel} of this cost function.
     */
    BudgetCostModel costModel();

}
