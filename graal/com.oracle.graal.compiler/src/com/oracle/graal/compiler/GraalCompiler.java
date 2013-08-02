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

import static com.oracle.graal.phases.GraalOptions.*;

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
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.verify.*;
import com.oracle.graal.virtual.phases.ea.*;

/**
 * Static methods for orchestrating the compilation of a {@linkplain StructuredGraph graph}.
 */
public class GraalCompiler {

    // @formatter:off
    @Option(help = "")
    public static final OptionValue<Boolean> VerifyUsageWithEquals = new OptionValue<>(true);
    @Option(help = "Enable inlining")
    public static final OptionValue<Boolean> Inline = new OptionValue<>(true);
    // @formatter:on

    /**
     * Requests compilation of a given graph.
     * 
     * @param graph the graph to be compiled
     * @param cc the calling convention for calls to the code compiled for {@code graph}
     * @param installedCodeOwner the method the compiled code will be
     *            {@linkplain InstalledCode#getMethod() associated} with once installed. This
     *            argument can be null.
     * @return the result of the compilation
     */
    public static CompilationResult compileGraph(final StructuredGraph graph, final CallingConvention cc,
                                                 final ResolvedJavaMethod installedCodeOwner, final GraalCodeCacheProvider runtime,
                                                 final Replacements replacements, final Backend backend,
                                                 final TargetDescription target, final GraphCache cache,
                                                 final PhasePlan plan, final OptimisticOptimizations optimisticOpts,
                                                 final SpeculationLog speculationLog, final Suites suites,
                                                 final CompilationResult compilationResult) {
        Debug.scope("GraalCompiler", new Object[]{graph, runtime}, new Runnable() {

            public void run() {
                final Assumptions assumptions = new Assumptions(OptAssumptions.getValue());
                final LIR lir = Debug.scope("FrontEnd", new Callable<LIR>() {

                    public LIR call() {
                        return emitHIR(runtime, target, graph, replacements, assumptions, cache, plan, optimisticOpts, speculationLog, suites);
                    }
                });
                final LIRGenerator lirGen = Debug.scope("BackEnd", lir, new Callable<LIRGenerator>() {

                    public LIRGenerator call() {
                        return emitLIR(backend, target, lir, graph, cc);
                    }
                });
                Debug.scope("CodeGen", lirGen, new Runnable() {

                    public void run() {
                        emitCode(backend, getLeafGraphIdArray(graph), assumptions, lirGen, compilationResult, installedCodeOwner);
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
                    PhasePlan plan, OptimisticOptimizations optimisticOpts, final SpeculationLog speculationLog, final Suites suites) {

        if (speculationLog != null) {
            speculationLog.snapshot();
        }

        if (graph.start().next() == null) {
            plan.runPhases(PhasePosition.AFTER_PARSING, graph);
            new DeadCodeEliminationPhase().apply(graph);
        } else {
            Debug.dump(graph, "initial state");
        }
        if (VerifyUsageWithEquals.getValue()) {
            new VerifyUsageWithEquals(runtime, Value.class).apply(graph);
            new VerifyUsageWithEquals(runtime, Register.class).apply(graph);
        }

        CanonicalizerPhase canonicalizer = new CanonicalizerPhase(!AOTCompilation.getValue());
        HighTierContext highTierContext = new HighTierContext(runtime, assumptions, replacements);

        if (OptCanonicalizer.getValue()) {
            canonicalizer.apply(graph, highTierContext);
        }

        if (Inline.getValue() && !plan.isPhaseDisabled(InliningPhase.class)) {
            if (IterativeInlining.getValue()) {
                new IterativeInliningPhase(cache, plan, optimisticOpts, canonicalizer).apply(graph, highTierContext);
            } else {
                new InliningPhase(runtime, null, replacements, assumptions, cache, plan, optimisticOpts).apply(graph);
                new DeadCodeEliminationPhase().apply(graph);

                if (ConditionalElimination.getValue() && OptCanonicalizer.getValue()) {
                    canonicalizer.apply(graph, highTierContext);
                    new IterativeConditionalEliminationPhase().apply(graph, highTierContext);
                }
            }
        }
        TypeProfileProxyNode.cleanFromGraph(graph);

        suites.getHighTier().apply(graph, highTierContext);

        MidTierContext midTierContext = new MidTierContext(runtime, assumptions, replacements, target, optimisticOpts);
        suites.getMidTier().apply(graph, midTierContext);

        LowTierContext lowTierContext = new LowTierContext(runtime, assumptions, replacements, target);
        suites.getLowTier().apply(graph, lowTierContext);

        // we do not want to store statistics about OSR compilations because it may prevent inlining
        if (!graph.isOSR()) {
            InliningPhase.storeStatisticsAfterLowTier(graph);
        }

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

    public static LIRGenerator emitLIR(Backend backend, final TargetDescription target, final LIR lir, StructuredGraph graph, CallingConvention cc) {
        final FrameMap frameMap = backend.newFrameMap();
        final LIRGenerator lirGen = backend.newLIRGenerator(graph, frameMap, cc, lir);

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
                new LinearScan(target, lir, lirGen, frameMap).allocate();
            }
        });
        return lirGen;
    }

    public static void emitCode(Backend backend, long[] leafGraphIds, Assumptions assumptions, LIRGenerator lirGen, CompilationResult compilationResult, ResolvedJavaMethod installedCodeOwner) {
        TargetMethodAssembler tasm = backend.newAssembler(lirGen, compilationResult);
        backend.emitCode(tasm, lirGen, installedCodeOwner);
        CompilationResult result = tasm.finishTargetMethod(lirGen.getGraph());
        if (!assumptions.isEmpty()) {
            result.setAssumptions(assumptions);
        }
        result.setLeafGraphIds(leafGraphIds);

        Debug.dump(result, "After code generation");
    }
}
