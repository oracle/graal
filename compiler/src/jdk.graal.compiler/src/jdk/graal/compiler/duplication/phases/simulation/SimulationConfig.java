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

import static jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions.ScheduledDuplicationSimulation;

import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.options.OptionValues;

public interface SimulationConfig {

    /**
     * Returns the maximum depth of control flow graph {@link ControlFlowGraph} {@link HIRBlock}s to
     * visit during duplication simulation.
     *
     * @return the number of blocks a duplication path is allowed to visit, values lower than zero
     *         indicate that the simulation can go as deep as possible.
     */
    int maxSimulationBlockDepth(OptionValues options);

    /**
     * Determines if the duplication simulation should look for potential canonicalizations that are
     * possible due to the usage of phi inputs at duplication merges.
     *
     * @return {@code true} if the simulation should try to find canonicalizations, {@code false}
     *         else
     */
    boolean findCanonicalizations();

    /**
     * Determines if the duplication simulation should look for potential conditional eliminations
     * that are possible due to the usage of phi inputs and their (better) stamps at duplication
     * merges.
     *
     * @return {@code true} of the simulation should try to find conditional eliminations,
     *         {@code false} else
     */
    boolean findConditionalEliminations();

    /**
     * Determines if the duplication simulation should be run in a "flat" mode. A "flat" mode
     * assumes that simulation cannot proceed beyond non sequential control flow nodes as this
     * requires the subsequent duplication util to be able to deal with post dominating merges etc.
     *
     * @return {@code true} if the simulation should stop at control flow barriers, {@code false}
     *         else.
     */
    //@formatter:off
    //
    // There are three different scenarios when duplicating.
    //
    //  (1) Between the merge and the duplication target is only sequential control flow
    //  (2) Between the merge and the duplication target is/are a split(s): Every split must be duplicated along the path and the successor
    //      not related to the simulation path must be merged to the non followed successor in the original split.
    //      For n splits along the path where each split has 2 successors this creates n copies of the splits (so we end up with 2n splits)
    //      and 2 * n -1 merges.
    //
    //  (3a) Between the merge and the duplication target is/are a merge(s):
    //          1 - If the original merge dominates all merges along a path we have valid diamond structures which can be fully (size - cost - model)
    //              duplicated without much effort. The original merge will be moved after the duplication target.
    //          2 - If the original merge does not dominate a merge along the path the not dominated merge will have one more predecessor
    //              as the original merge is duplicated below.
    //
    //  (3b) Between the merge and duplication target is/are a merge(s) AND recursive simulation is enabled:
    //          1 - As above
    //          2 - Cannot recursively duplicate along a path where a merge is not dominated by the original merge.
    //
    //
    //@formatter:on
    boolean stopSimulationAtControlFlow(OptionValues options);

    default boolean scheduleSimulation(OptionValues options) {
        return ScheduledDuplicationSimulation.getValue(options);
    }

}
