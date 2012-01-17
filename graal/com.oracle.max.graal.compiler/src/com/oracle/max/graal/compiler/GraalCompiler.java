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
package com.oracle.max.graal.compiler;

import java.util.*;
import java.util.concurrent.*;

import com.oracle.max.asm.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.xir.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.alloc.simple.*;
import com.oracle.max.graal.compiler.alloc.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.phases.PhasePlan.PhasePosition;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.target.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;

public class GraalCompiler {

    /**
     * The target that this compiler has been configured for.
     */
    public final CiTarget target;

    /**
     * The runtime that this compiler has been configured for.
     */
    public final GraalRuntime runtime;

    /**
     * The XIR generator that lowers Java operations to machine operations.
     */
    public final RiXirGenerator xir;

    /**
     * The backend that this compiler has been configured for.
     */
    public final Backend backend;

    public GraalCompiler(GraalRuntime runtime, CiTarget target, Backend backend, RiXirGenerator xirGen) {
        this.runtime = runtime;
        this.target = target;
        this.xir = xirGen;
        this.backend = backend;
    }

    public CiTargetMethod compileMethod(RiResolvedMethod method, int osrBCI, PhasePlan plan) {
        return compileMethod(method, new StructuredGraph(method), osrBCI, plan);
    }

    public CiTargetMethod compileMethod(final RiResolvedMethod method, final StructuredGraph graph, int osrBCI, final PhasePlan plan) {
        if (osrBCI != -1) {
            throw new CiBailout("No OSR supported");
        }
        return Debug.scope("CompileMethod", method, new Callable<CiTargetMethod>() {
                public CiTargetMethod call() {
                        final CiAssumptions assumptions = GraalOptions.OptAssumptions ? new CiAssumptions() : null;
                        LIR lir = Debug.scope("EmitHIR", graph, new Callable<LIR>() {
                            public LIR call() {
                                return emitHIR(graph, assumptions, plan);
                            }
                        });
                        FrameMap frameMap = emitLIR(lir, graph, method);
                        return emitCode(assumptions, method, lir, frameMap);
                }
        });
    }

    /**
     * Builds the graph, optimizes it.
     */
    public LIR emitHIR(StructuredGraph graph, CiAssumptions assumptions, PhasePlan plan) {

        if (graph.start().next() == null) {
            plan.runPhases(PhasePosition.AFTER_PARSING, graph);
            new DeadCodeEliminationPhase().apply(graph);
        } else {
            Debug.dump(graph, "initial state");
        }

        new PhiStampPhase().apply(graph);

        if (GraalOptions.ProbabilityAnalysis && graph.start().probability() == 0) {
            new ComputeProbabilityPhase().apply(graph);
        }

        if (GraalOptions.Intrinsify) {
            new IntrinsificationPhase(runtime).apply(graph);
        }

        if (GraalOptions.Inline && !plan.isPhaseDisabled(InliningPhase.class)) {
            new InliningPhase(target, runtime, null, assumptions, plan).apply(graph);
            new DeadCodeEliminationPhase().apply(graph);
            new PhiStampPhase().apply(graph);
        }

        if (GraalOptions.OptCanonicalizer) {
            new CanonicalizerPhase(target, runtime, assumptions).apply(graph);
        }

        plan.runPhases(PhasePosition.HIGH_LEVEL, graph);

        if (GraalOptions.OptLoops) {
            graph.mark();
            new FindInductionVariablesPhase().apply(graph);
            if (GraalOptions.OptCanonicalizer) {
                new CanonicalizerPhase(target, runtime, true, assumptions).apply(graph);
            }
            new SafepointPollingEliminationPhase().apply(graph);
        }

        if (GraalOptions.EscapeAnalysis && !plan.isPhaseDisabled(EscapeAnalysisPhase.class)) {
            new EscapeAnalysisPhase(target, runtime, assumptions, plan).apply(graph);
            new PhiStampPhase().apply(graph);
            new CanonicalizerPhase(target, runtime, assumptions).apply(graph);
        }

        if (GraalOptions.OptGVN) {
            new GlobalValueNumberingPhase().apply(graph);
        }

        graph.mark();
        new LoweringPhase(runtime).apply(graph);
        new CanonicalizerPhase(target, runtime, true, assumptions).apply(graph);

        if (GraalOptions.OptLoops) {
            graph.mark();
            new RemoveInductionVariablesPhase().apply(graph);
            if (GraalOptions.OptCanonicalizer) {
                new CanonicalizerPhase(target, runtime, true, assumptions).apply(graph);
            }
        }

        if (GraalOptions.Lower) {
            new FloatingReadPhase().apply(graph);
            if (GraalOptions.OptReadElimination) {
                new ReadEliminationPhase().apply(graph);
            }
        }
        new RemovePlaceholderPhase().apply(graph);
        new DeadCodeEliminationPhase().apply(graph);

        plan.runPhases(PhasePosition.MID_LEVEL, graph);

        plan.runPhases(PhasePosition.LOW_LEVEL, graph);

        final IdentifyBlocksPhase schedule = new IdentifyBlocksPhase(true, LIRBlock.FACTORY);
        schedule.apply(graph);

        final List<Block> blocks = schedule.getBlocks();
        final NodeMap<LIRBlock> valueToBlock = new NodeMap<>(graph);
        for (Block b : blocks) {
            for (Node i : b.getInstructions()) {
                valueToBlock.set(i, (LIRBlock) b);
            }
        }
        final LIRBlock startBlock = valueToBlock.get(graph.start());
        assert startBlock != null;
        assert startBlock.numberOfPreds() == 0;

        return Debug.scope("Compute Linear Scan Order", new Callable<LIR>() {

            @Override
            public LIR call() {
                ComputeLinearScanOrder clso = new ComputeLinearScanOrder(blocks.size(), schedule.loopCount(), startBlock);
                List<LIRBlock> linearScanOrder = clso.linearScanOrder();
                List<LIRBlock> codeEmittingOrder = clso.codeEmittingOrder();

                int z = 0;
                for (LIRBlock b : linearScanOrder) {
                    b.setLinearScanNumber(z++);
                }

                LIR lir = new LIR(startBlock, linearScanOrder, codeEmittingOrder, valueToBlock, schedule.loopCount());
                Debug.dump(lir, "After linear scan order");
                return lir;

            }
        });
    }

    public FrameMap emitLIR(LIR lir, StructuredGraph graph, RiResolvedMethod method) {
                LIRGenerator lirGenerator = null;
                FrameMap frameMap;
                    frameMap = backend.newFrameMap(runtime.getRegisterConfig(method));

                    lirGenerator = backend.newLIRGenerator(graph, frameMap, method, lir, xir);

                    for (LIRBlock b : lir.linearScanOrder()) {
                        lirGenerator.doBlock(b);
                    }

                    for (LIRBlock b : lir.linearScanOrder()) {
                        if (b.phis != null) {
                            b.phis.fillInputs(lirGenerator);
                        }
                    }

                Debug.dump(lirGenerator, "After LIR generation");
                if (GraalOptions.PrintLIR && !TTY.isSuppressed()) {
                    LIR.printLIR(lir.linearScanOrder());
                }

                if (GraalOptions.AllocSSA) {
                    new SpillAllAllocator(lir, frameMap).execute();
                } else {
                    new LinearScan(target, method, graph, lir, lirGenerator, frameMap).allocate();
                }
                return frameMap;
    }

    private TargetMethodAssembler createAssembler(FrameMap frameMap, LIR lir) {
        AbstractAssembler masm = backend.newAssembler(frameMap.registerConfig);
        TargetMethodAssembler tasm = new TargetMethodAssembler(target, runtime, frameMap, lir.slowPaths, masm);
        tasm.setFrameSize(frameMap.frameSize());
        tasm.targetMethod.setCustomStackAreaOffset(frameMap.offsetToCustomArea());
        return tasm;
    }

    public CiTargetMethod emitCode(CiAssumptions assumptions, RiResolvedMethod method, LIR lir, FrameMap frameMap) {
        TargetMethodAssembler tasm = createAssembler(frameMap, lir);
        lir.emitCode(tasm);

        CiTargetMethod targetMethod = tasm.finishTargetMethod(method, false);
        if (assumptions != null && !assumptions.isEmpty()) {
            targetMethod.setAssumptions(assumptions);
        }

        Debug.dump(targetMethod, "After code generation");
        return targetMethod;
    }
}
