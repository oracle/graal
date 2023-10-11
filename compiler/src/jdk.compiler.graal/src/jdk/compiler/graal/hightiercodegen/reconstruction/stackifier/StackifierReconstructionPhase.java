/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hightiercodegen.reconstruction.stackifier;

import java.util.Optional;

import jdk.compiler.graal.hightiercodegen.irwalk.StackifierIRWalker;
import jdk.compiler.graal.hightiercodegen.reconstruction.ReconstructionPhase;
import jdk.compiler.graal.hightiercodegen.reconstruction.ScheduleWithReconstructionResult;
import jdk.compiler.graal.hightiercodegen.reconstruction.StackifierData;
import jdk.compiler.graal.hightiercodegen.reconstruction.stackifier.blocks.LabeledBlockGeneration;
import jdk.compiler.graal.nodes.GraphState;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.cfg.ControlFlowGraph;
import jdk.compiler.graal.nodes.spi.CoreProviders;
import jdk.compiler.graal.phases.BasePhase;
import jdk.compiler.graal.phases.schedule.SchedulePhase;

/**
 * Computes a {@link ScheduleWithReconstructionResult} for the graph, meant specifically for use by
 * {@link StackifierIRWalker}.
 */
public class StackifierReconstructionPhase extends ReconstructionPhase {
    @Override
    protected void run(StructuredGraph graph, CoreProviders providers) {
        SchedulePhase schedulePhase = new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS);
        schedulePhase.apply(graph, providers);
        StackifierData stackifierData = new StackifierData();
        new StackifierScopeComputation(graph).computeScopes(stackifierData);
        new CFStackifierSortPhase().apply(graph, stackifierData);
        LabeledBlockGeneration blockGeneration = createLabeledBlockGeneration(stackifierData, graph.getLastSchedule().getCFG());
        blockGeneration.generateLabeledBlocks();
        stackifierData.setLabeledBlockGeneration(blockGeneration);
        graph.setLastSchedule(new ScheduleWithReconstructionResult(graph.getLastSchedule(), stackifierData));
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return BasePhase.ALWAYS_APPLICABLE;
    }

    protected LabeledBlockGeneration createLabeledBlockGeneration(StackifierData stackifierData, ControlFlowGraph cfg) {
        return new LabeledBlockGeneration(stackifierData, cfg);
    }
}
