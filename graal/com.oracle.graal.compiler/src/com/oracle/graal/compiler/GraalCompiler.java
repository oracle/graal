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
import com.oracle.graal.loop.phases.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.virtual.phases.ea.*;

public class GraalCompiler {

    /**
     * The target that this compiler has been configured for.
     */
    public final TargetDescription target;

    /**
     * The runtime that this compiler has been configured for.
     */
    public final GraalCodeCacheProvider runtime;

    /**
     * The backend that this compiler has been configured for.
     */
    public final Backend backend;

    public GraalCompiler(GraalCodeCacheProvider runtime, TargetDescription target, Backend backend) {
        this.runtime = runtime;
        this.target = target;
        this.backend = backend;
    }

    public CompilationResult compileMethod(final ResolvedJavaMethod method, final StructuredGraph graph, final GraphCache cache, final PhasePlan plan, final OptimisticOptimizations optimisticOpts) {
        assert (method.getModifiers() & Modifier.NATIVE) == 0 : "compiling native methods is not supported";

        return Debug.scope("GraalCompiler", new Object[]{graph, method, this}, new Callable<CompilationResult>() {

            public CompilationResult call() {
                final Assumptions assumptions = new Assumptions(GraalOptions.OptAssumptions);
                final LIR lir = Debug.scope("FrontEnd", new Callable<LIR>() {

                    public LIR call() {
                        return emitHIR(graph, assumptions, cache, plan, optimisticOpts);
                    }
                });
                final FrameMap frameMap = Debug.scope("BackEnd", lir, new Callable<FrameMap>() {

                    public FrameMap call() {
                        return emitLIR(lir, graph, method);
                    }
                });
                return Debug.scope("CodeGen", frameMap, new Callable<CompilationResult>() {

                    public CompilationResult call() {
                        return emitCode(assumptions, method, lir, frameMap);
                    }
                });
            }
        });
    }

    /**
     * Builds the graph, optimizes it.
     */
    public LIR emitHIR(StructuredGraph graph, Assumptions assumptions, GraphCache cache, PhasePlan plan, OptimisticOptimizations optimisticOpts) {

        if (graph.start().next() == null) {
            plan.runPhases(PhasePosition.AFTER_PARSING, graph);
            new DeadCodeEliminationPhase().apply(graph);
        } else {
            Debug.dump(graph, "initial state");
        }

        if (GraalOptions.ProbabilityAnalysis && graph.start().probability() == 0) {
            new ComputeProbabilityPhase().apply(graph);
        }

        if (GraalOptions.OptCanonicalizer) {
            new CanonicalizerPhase(target, runtime, assumptions).apply(graph);
        }

        if (GraalOptions.Inline && !plan.isPhaseDisabled(InliningPhase.class)) {
            new InliningPhase(target, runtime, null, assumptions, cache, plan, optimisticOpts).apply(graph);
            new DeadCodeEliminationPhase().apply(graph);

            if (GraalOptions.CheckCastElimination && GraalOptions.OptCanonicalizer) {
                new CanonicalizerPhase(target, runtime, assumptions).apply(graph);
                new IterativeConditionalEliminationPhase(target, runtime, assumptions).apply(graph);
            }
        }

        // new ConvertUnreachedToGuardPhase(optimisticOpts).apply(graph);

        plan.runPhases(PhasePosition.HIGH_LEVEL, graph);

        if (GraalOptions.FullUnroll) {
            new LoopFullUnrollPhase(runtime, assumptions).apply(graph);
            if (GraalOptions.OptCanonicalizer) {
                new CanonicalizerPhase(target, runtime, assumptions).apply(graph);
            }
        }

        if (GraalOptions.OptTailDuplication) {
            new TailDuplicationPhase().apply(graph);
            if (GraalOptions.OptCanonicalizer) {
                new CanonicalizerPhase(target, runtime, assumptions).apply(graph);
            }
        }

        if (GraalOptions.PartialEscapeAnalysis && !plan.isPhaseDisabled(PartialEscapeAnalysisPhase.class)) {
            new PartialEscapeAnalysisPhase(target, runtime, assumptions, true).apply(graph);
        }

        new LockEliminationPhase().apply(graph);

        if (GraalOptions.OptLoopTransform) {
            new LoopTransformHighPhase().apply(graph);
            new LoopTransformLowPhase().apply(graph);
        }
        new RemoveValueProxyPhase().apply(graph);

        if (GraalOptions.OptCanonicalizer) {
            new CanonicalizerPhase(target, runtime, assumptions).apply(graph);
        }

        new LoweringPhase(runtime, assumptions).apply(graph);

        if (GraalOptions.CullFrameStates) {
            new CullFrameStatesPhase().apply(graph);
        }

        if (GraalOptions.OptFloatingReads) {
            int mark = graph.getMark();
            new FloatingReadPhase().apply(graph);
            new CanonicalizerPhase(target, runtime, assumptions, mark, null).apply(graph);
            if (GraalOptions.OptReadElimination) {
                new ReadEliminationPhase().apply(graph);
            }
        }
        new RemoveValueProxyPhase().apply(graph);

        if (GraalOptions.OptCanonicalizer) {
            new CanonicalizerPhase(target, runtime, assumptions).apply(graph);
        }

        if (GraalOptions.OptEliminatePartiallyRedundantGuards) {
            new EliminatePartiallyRedundantGuardsPhase(false, true).apply(graph);
        }

        if (GraalOptions.CheckCastElimination && GraalOptions.OptCanonicalizer) {
            new IterativeConditionalEliminationPhase(target, runtime, assumptions).apply(graph);
        }

        if (GraalOptions.OptEliminatePartiallyRedundantGuards) {
            new EliminatePartiallyRedundantGuardsPhase(true, true).apply(graph);
        }

        plan.runPhases(PhasePosition.MID_LEVEL, graph);

        plan.runPhases(PhasePosition.LOW_LEVEL, graph);

        // Add safepoints to loops
        if (GraalOptions.GenLoopSafepoints) {
            new LoopSafepointInsertionPhase().apply(graph);
        }

        final SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph);
        Debug.dump(schedule, "final schedule");

        final Block[] blocks = schedule.getCFG().getBlocks();
        final Block startBlock = schedule.getCFG().getStartBlock();
        assert startBlock != null;
        assert startBlock.getPredecessorCount() == 0;

        new ComputeProbabilityPhase().apply(graph);

        return Debug.scope("ComputeLinearScanOrder", new Callable<LIR>() {

            @Override
            public LIR call() {
                List<Block> codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blocks.length, startBlock);
                List<Block> linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blocks.length, startBlock);

                LIR lir = new LIR(schedule.getCFG(), schedule.getBlockToNodesMap(), linearScanOrder, codeEmittingOrder);
                Debug.dump(lir, "After linear scan order");
                return lir;

            }
        });

    }

    public FrameMap emitLIR(final LIR lir, StructuredGraph graph, final ResolvedJavaMethod method) {
        final FrameMap frameMap = backend.newFrameMap(runtime.lookupRegisterConfig(method));
        final LIRGenerator lirGenerator = backend.newLIRGenerator(graph, frameMap, method, lir);

        Debug.scope("LIRGen", lirGenerator, new Runnable() {

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
                    lirGenerator.doBlock(b);
                }
            }
        });

        Debug.scope("Allocator", new Runnable() {

            public void run() {
                new LinearScan(target, method, lir, lirGenerator, frameMap).allocate();
            }
        });
        return frameMap;
    }

    public CompilationResult emitCode(Assumptions assumptions, ResolvedJavaMethod method, LIR lir, FrameMap frameMap) {
        TargetMethodAssembler tasm = backend.newAssembler(frameMap, lir);
        backend.emitCode(tasm, method, lir);
        CompilationResult targetMethod = tasm.finishTargetMethod(method, false);
        if (!assumptions.isEmpty()) {
            targetMethod.setAssumptions(assumptions);
        }

        Debug.dump(targetMethod, "After code generation");
        return targetMethod;
    }
}
