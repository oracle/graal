/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.alloc.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.alloc.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.spi.Lowerable.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.virtual.phases.ea.*;

public class GraalCompiler {

    public static CompilationResult compileMethod(final GraalCodeCacheProvider runtime, final Replacements replacements, final Backend backend, final TargetDescription target,
                    final ResolvedJavaMethod method, final StructuredGraph graph, final GraphCache cache, final PhasePlan plan, final OptimisticOptimizations optimisticOpts,
                    final SpeculationLog speculationLog) {
        assert (method.getModifiers() & Modifier.NATIVE) == 0 : "compiling native methods is not supported";

        final CompilationResult compilationResult = new CompilationResult();
        Debug.scope("GraalCompiler", new Object[]{graph, method, runtime}, new Runnable() {

            public void run() {
                final Assumptions assumptions = new Assumptions(GraalOptions.OptAssumptions);
                final LIR lir = Debug.scope("FrontEnd", new Callable<LIR>() {

                    public LIR call() {
                        return emitHIR(runtime, target, graph, replacements, assumptions, cache, plan, optimisticOpts, speculationLog);
                    }
                });
                final LIRGenerator lirGen = Debug.scope("BackEnd", lir, new Callable<LIRGenerator>() {

                    public LIRGenerator call() {
                        return emitLIR(backend, target, lir, graph, method);
                    }
                });
                Debug.scope("CodeGen", lirGen, new Runnable() {

                    public void run() {
                        emitCode(backend, getLeafGraphIdArray(graph), assumptions, method, lirGen, compilationResult);
                    }

                });
            }
        });

        return compilationResult;
    }

    private static long[] getLeafGraphIdArray(StructuredGraph graph) {
        long[] leafGraphIdArray = new long[graph.getLeafGraphIds().size() + 1];
        int i = 0;
        leafGraphIdArray[i++] = graph.graphId();
        for (long id : graph.getLeafGraphIds()) {
            leafGraphIdArray[i++] = id;
        }
        return leafGraphIdArray;
    }

    /**
     * Builds the graph, optimizes it.
     * 
     * @param runtime
     * 
     * @param target
     */
    public static LIR emitHIR(GraalCodeCacheProvider runtime, TargetDescription target, final StructuredGraph graph, Replacements replacements, Assumptions assumptions, GraphCache cache,
                    PhasePlan plan, OptimisticOptimizations optimisticOpts, final SpeculationLog speculationLog) {

        if (speculationLog != null) {
            speculationLog.snapshot();
        }

        if (graph.start().next() == null) {
            plan.runPhases(PhasePosition.AFTER_PARSING, graph);
            new DeadCodeEliminationPhase().apply(graph);
        } else {
            Debug.dump(graph, "initial state");
        }

        if (GraalOptions.OptCanonicalizer) {
            new CanonicalizerPhase.Instance(runtime, assumptions).apply(graph);
        }

        HighTierContext highTierContext = new HighTierContext(runtime, assumptions, replacements);

        if (GraalOptions.Inline && !plan.isPhaseDisabled(InliningPhase.class)) {
            if (GraalOptions.IterativeInlining) {
                new IterativeInliningPhase(replacements, cache, plan, optimisticOpts, GraalOptions.OptEarlyReadElimination).apply(graph, highTierContext);
            } else {
                new InliningPhase(runtime, null, replacements, assumptions, cache, plan, optimisticOpts).apply(graph);
                new DeadCodeEliminationPhase().apply(graph);

                if (GraalOptions.ConditionalElimination && GraalOptions.OptCanonicalizer) {
                    new CanonicalizerPhase.Instance(runtime, assumptions).apply(graph);
                    new IterativeConditionalEliminationPhase().apply(graph, highTierContext);
                }
            }
        }

        plan.runPhases(PhasePosition.HIGH_LEVEL, graph);

        Suites.DEFAULT.getHighTier().apply(graph, highTierContext);

        new LoweringPhase(LoweringType.BEFORE_GUARDS).apply(graph, highTierContext);

        MidTierContext midTierContext = new MidTierContext(runtime, assumptions, replacements, target);
        Suites.DEFAULT.getMidTier().apply(graph, midTierContext);

        // Add safepoints to loops
        new SafepointInsertionPhase().apply(graph);

        new GuardLoweringPhase().apply(graph, midTierContext);

        plan.runPhases(PhasePosition.LOW_LEVEL, graph);

        LowTierContext lowTierContext = new LowTierContext(runtime, assumptions, replacements, target);
        Suites.DEFAULT.getLowTier().apply(graph, lowTierContext);

        new LoweringPhase(LoweringType.AFTER_GUARDS).apply(graph, lowTierContext);

        new FrameStateAssignmentPhase().apply(graph);

        new DeadCodeEliminationPhase().apply(graph);

        final SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph);
        Debug.dump(schedule, "final schedule");

        final Block[] blocks = schedule.getCFG().getBlocks();
        final Block startBlock = schedule.getCFG().getStartBlock();
        assert startBlock != null;
        assert startBlock.getPredecessorCount() == 0;

        return Debug.scope("ComputeLinearScanOrder", new Callable<LIR>() {

            @Override
            public LIR call() {
                NodesToDoubles nodeProbabilities = new ComputeProbabilityClosure(graph).apply();
                List<Block> codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blocks.length, startBlock, nodeProbabilities);
                List<Block> linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blocks.length, startBlock, nodeProbabilities);

                LIR lir = new LIR(schedule.getCFG(), schedule.getBlockToNodesMap(), linearScanOrder, codeEmittingOrder, speculationLog);
                Debug.dump(lir, "After linear scan order");
                return lir;

            }
        });

    }

    public static LIRGenerator emitLIR(Backend backend, final TargetDescription target, final LIR lir, StructuredGraph graph, final ResolvedJavaMethod method) {
        final FrameMap frameMap = backend.newFrameMap();
        final LIRGenerator lirGen = backend.newLIRGenerator(graph, frameMap, method, lir);

        Debug.scope("LIRGen", lirGen, new Runnable() {

            public void run() {
                for (Block b : lir.linearScanOrder()) {
                    emitBlock(b);
                }

                Debug.dump(lir, "After LIR generation");
            }

            private void emitBlock(Block b) {
                if (lir.lir(b) == null) {
                    for (Block pred : b.getPredecessors()) {
                        if (!b.isLoopHeader() || !pred.isLoopEnd()) {
                            emitBlock(pred);
                        }
                    }
                    lirGen.doBlock(b);
                }
            }
        });

        lirGen.beforeRegisterAllocation();

        Debug.scope("Allocator", new Runnable() {

            public void run() {
                new LinearScan(target, method, lir, lirGen, frameMap).allocate();
            }
        });
        return lirGen;
    }

    public static void emitCode(Backend backend, long[] leafGraphIds, Assumptions assumptions, ResolvedJavaMethod method, LIRGenerator lirGen, CompilationResult compilationResult) {
        TargetMethodAssembler tasm = backend.newAssembler(lirGen, compilationResult);
        backend.emitCode(tasm, method, lirGen);
        CompilationResult result = tasm.finishTargetMethod(method, false);
        if (!assumptions.isEmpty()) {
            result.setAssumptions(assumptions);
        }
        result.setLeafGraphIds(leafGraphIds);

        Debug.dump(result, "After code generation");
    }
}
