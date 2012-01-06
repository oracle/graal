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

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ci.CiCompiler.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.xir.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.alloc.simple.*;
import com.oracle.max.graal.compiler.alloc.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.phases.PhasePlan.PhasePosition;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.virtual.*;

/**
 * This class encapsulates global information about the compilation of a particular method,
 * including a reference to the runtime, statistics about the compiled code, etc.
 */
public final class GraalCompilation {
    public final GraalCompiler compiler;
    public final RiResolvedMethod method;
    public final RiRegisterConfig registerConfig;
    public final CiStatistics stats;
    public final FrameState placeholderState;

    public final StructuredGraph graph;
    public final CiAssumptions assumptions = GraalOptions.OptAssumptions ? new CiAssumptions() : null;
    public NodeMap<CiValue> nodeOperands;

    private FrameMap frameMap;

    private LIR lir;

    /**
     * Creates a new compilation for the specified method and runtime.
     *
     * @param context the compilation context
     * @param compiler the compiler
     * @param method the method to be compiled or {@code null} if generating code for a stub
     * @param osrBCI the bytecode index for on-stack replacement, if requested
     * @param stats externally supplied statistics object to be used if not {@code null}
     * @param debugInfoLevel TODO
     */
    private GraalCompilation(GraalContext context, GraalCompiler compiler, RiResolvedMethod method, StructuredGraph graph, int osrBCI, CiStatistics stats, DebugInfoLevel debugInfoLevel) {
        if (osrBCI != -1) {
            throw new CiBailout("No OSR supported");
        }
        this.compiler = compiler;
        this.graph = graph;
        this.method = method;
        this.stats = stats == null ? new CiStatistics() : stats;
        this.registerConfig = method == null ? compiler.compilerStubRegisterConfig : compiler.runtime.getRegisterConfig(method);
        this.placeholderState = debugInfoLevel == DebugInfoLevel.REF_MAPS ? new FrameState(method, 0, 0, 0, false) : null;

        if (context().isObserved() && method != null) {
            context().observable.fireCompilationStarted(this);
        }
    }

    public GraalCompilation(GraalContext context, GraalCompiler compiler, RiResolvedMethod method, int osrBCI, CiStatistics stats, DebugInfoLevel debugInfoLevel) {
        this(context, compiler, method, new StructuredGraph(method), osrBCI, stats, debugInfoLevel);
    }


    public void close() {
        // TODO(tw): Check if we can delete this method.
    }

    public LIR lir() {
        return lir;
    }

    public CiValue operand(ValueNode valueNode) {
        if (nodeOperands == null) {
            return null;
        }
        return nodeOperands.get(valueNode);
    }

    public void setOperand(ValueNode valueNode, CiValue operand) {
        assert operand(valueNode) == null : "operand cannot be set twice";
        assert operand != null && isLegal(operand) : "operand must be legal";
        assert operand.kind.stackKind() == valueNode.kind();
        assert !(valueNode instanceof VirtualObjectNode);
        nodeOperands.set(valueNode, operand);
    }

    /**
     * Converts this compilation to a string.
     * @return a string representation of this compilation
     */
    @Override
    public String toString() {
        return "compile: " + method;
    }

    /**
     * Returns the frame map of this compilation.
     * @return the frame map
     */
    public FrameMap frameMap() {
        return frameMap;
    }

    private TargetMethodAssembler createAssembler() {
        AbstractAssembler masm = compiler.backend.newAssembler(registerConfig);
        TargetMethodAssembler tasm = new TargetMethodAssembler(this, masm);
        tasm.setFrameSize(frameMap.frameSize());
        tasm.targetMethod.setCustomStackAreaOffset(frameMap.offsetToCustomArea());
        return tasm;
    }

    public CiTargetMethod compile(PhasePlan plan) {
        CiTargetMethod targetMethod;
        try {
            try {
                emitHIR(plan);
                emitLIR(compiler.xir);
                targetMethod = emitCode();

                if (GraalOptions.Meter) {
                    context().metrics.BytecodesCompiled += method.codeSize();
                }
            } catch (CiBailout bailout) {
                throw bailout;
            } catch (GraalInternalError e) {
                throw e.addContext("method", CiUtil.format("%H.%n(%p):%r", method));
            } catch (Throwable t) {
                throw new RuntimeException("Exception while compiling: " + method, t);
            }
        } catch (GraalInternalError error) {
            if (context().isObserved()) {
                if (error.node() != null) {
                    context().observable.fireCompilationEvent("VerificationError on Node " + error.node(), CompilationEvent.ERROR, this, error.node().graph());
                } else if (error.graph() != null) {
                    context().observable.fireCompilationEvent("VerificationError on Graph " + error.graph(), CompilationEvent.ERROR, this, error.graph());
                }
            }
            throw error;
        } finally {
            if (context().isObserved()) {
                context().observable.fireCompilationFinished(this);
            }
        }

        return targetMethod;
    }

    /**
     * Builds the graph, optimizes it.
     */
    public void emitHIR(PhasePlan plan) {
        try {
            context().timers.startScope("HIR");

            if (graph.start().next() == null) {
                plan.runPhases(PhasePosition.AFTER_PARSING, graph, context());
                new DeadCodeEliminationPhase().apply(graph, context());
            } else {
                if (context().isObserved()) {
                    context().observable.fireCompilationEvent("initial state", graph);
                }
            }

            new PhiStampPhase().apply(graph);

            if (GraalOptions.ProbabilityAnalysis && graph.start().probability() == 0) {
                new ComputeProbabilityPhase().apply(graph, context());
            }

            if (GraalOptions.Intrinsify) {
                new IntrinsificationPhase(compiler.runtime).apply(graph, context());
            }

            if (GraalOptions.Inline && !plan.isPhaseDisabled(InliningPhase.class)) {
                new InliningPhase(compiler.target, compiler.runtime, null, assumptions, plan).apply(graph, context());
                new DeadCodeEliminationPhase().apply(graph, context());
                new PhiStampPhase().apply(graph);
            }

            if (GraalOptions.OptCanonicalizer) {
                new CanonicalizerPhase(compiler.target, compiler.runtime, assumptions).apply(graph, context());
            }

            plan.runPhases(PhasePosition.HIGH_LEVEL, graph, context());

            if (GraalOptions.OptLoops) {
                graph.mark();
                new FindInductionVariablesPhase().apply(graph, context());
                if (GraalOptions.OptCanonicalizer) {
                    new CanonicalizerPhase(compiler.target, compiler.runtime, true, assumptions).apply(graph, context());
                }
                new SafepointPollingEliminationPhase().apply(graph, context());
            }

            if (GraalOptions.EscapeAnalysis && !plan.isPhaseDisabled(EscapeAnalysisPhase.class)) {
                new EscapeAnalysisPhase(compiler.target, compiler.runtime, assumptions, plan).apply(graph, context());
                new PhiStampPhase().apply(graph);
                new CanonicalizerPhase(compiler.target, compiler.runtime, assumptions).apply(graph, context());
            }

            if (GraalOptions.OptGVN) {
                new GlobalValueNumberingPhase().apply(graph, context());
            }

            graph.mark();
            new LoweringPhase(compiler.runtime).apply(graph, context());
            new CanonicalizerPhase(compiler.target, compiler.runtime, true, assumptions).apply(graph, context());

            if (GraalOptions.OptLoops) {
                graph.mark();
                new RemoveInductionVariablesPhase().apply(graph, context());
                if (GraalOptions.OptCanonicalizer) {
                    new CanonicalizerPhase(compiler.target, compiler.runtime, true, assumptions).apply(graph, context());
                }
            }

            if (GraalOptions.Lower) {
                new FloatingReadPhase().apply(graph, context());
                if (GraalOptions.OptReadElimination) {
                    new ReadEliminationPhase().apply(graph, context());
                }
            }
            new RemovePlaceholderPhase().apply(graph, context());
            new DeadCodeEliminationPhase().apply(graph, context());

            plan.runPhases(PhasePosition.MID_LEVEL, graph, context());

            plan.runPhases(PhasePosition.LOW_LEVEL, graph, context());

            IdentifyBlocksPhase schedule = new IdentifyBlocksPhase(true, LIRBlock.FACTORY);
            schedule.apply(graph, context());
            if (stats != null) {
                stats.loopCount = schedule.loopCount();
            }

            if (context().isObserved()) {
                context().observable.fireCompilationEvent("After IdentifyBlocksPhase", this, graph, schedule);
            }

            List<Block> blocks = schedule.getBlocks();
            NodeMap<LIRBlock> valueToBlock = new NodeMap<>(graph);
            for (Block b : blocks) {
                for (Node i : b.getInstructions()) {
                    valueToBlock.set(i, (LIRBlock) b);
                }
            }
            LIRBlock startBlock = valueToBlock.get(graph.start());
            assert startBlock != null;
            assert startBlock.numberOfPreds() == 0;

            context().timers.startScope("Compute Linear Scan Order");
            try {
                ComputeLinearScanOrder clso = new ComputeLinearScanOrder(blocks.size(), stats.loopCount, startBlock);
                List<LIRBlock> linearScanOrder = clso.linearScanOrder();
                List<LIRBlock> codeEmittingOrder = clso.codeEmittingOrder();

                int z = 0;
                for (LIRBlock b : linearScanOrder) {
                    b.setLinearScanNumber(z++);
                }

                lir = new LIR(startBlock, linearScanOrder, codeEmittingOrder, valueToBlock);

                if (context().isObserved()) {
                    context().observable.fireCompilationEvent("After linear scan order", this, graph, lir);
                }
            } catch (AssertionError t) {
                    context().observable.fireCompilationEvent("AssertionError in ComputeLinearScanOrder", CompilationEvent.ERROR, this, graph);
                throw t;
            } catch (RuntimeException t) {
                    context().observable.fireCompilationEvent("RuntimeException in ComputeLinearScanOrder", CompilationEvent.ERROR, this, graph);
                throw t;
            } finally {
                context().timers.endScope();
            }
        } finally {
            context().timers.endScope();
        }
    }

    public void initFrameMap() {
        frameMap = this.compiler.backend.newFrameMap(this);
    }

    private void emitLIR(RiXirGenerator xir) {
        context().timers.startScope("LIR");
        try {
            if (GraalOptions.GenLIR) {
                context().timers.startScope("Create LIR");
                nodeOperands = graph.createNodeMap();
                LIRGenerator lirGenerator = null;
                try {
                    initFrameMap();

                    lirGenerator = compiler.backend.newLIRGenerator(this, xir);

                    for (LIRBlock b : lir.linearScanOrder()) {
                        lirGenerator.doBlock(b);
                    }

                    for (LIRBlock b : lir.linearScanOrder()) {
                        if (b.phis != null) {
                            b.phis.fillInputs(lirGenerator);
                        }
                    }
                } finally {
                    context().timers.endScope();
                }

                if (context().isObserved()) {
                    context().observable.fireCompilationEvent("After LIR generation", this, graph, lir);
                }
                if (GraalOptions.PrintLIR && !TTY.isSuppressed()) {
                    LIR.printLIR(lir.linearScanOrder());
                }

                if (GraalOptions.AllocSSA) {
                    new SpillAllAllocator(context(), lir, this, registerConfig).execute();
                } else {
                    new LinearScan(this, lir, lirGenerator, frameMap()).allocate();
                }
            }
        } catch (Error e) {
            if (context().isObserved() && GraalOptions.PlotOnError) {
                context().observable.fireCompilationEvent(e.getClass().getSimpleName() + " in emitLIR", CompilationEvent.ERROR, this, graph);
            }
            throw e;
        } catch (RuntimeException e) {
            if (context().isObserved() && GraalOptions.PlotOnError) {
                context().observable.fireCompilationEvent(e.getClass().getSimpleName() + " in emitLIR", CompilationEvent.ERROR, this, graph);
            }
            throw e;
        } finally {
            context().timers.endScope();
        }
    }

    private CiTargetMethod emitCode() {
        if (GraalOptions.GenLIR && GraalOptions.GenCode) {
            context().timers.startScope("Create Code");
            try {
                TargetMethodAssembler tasm = createAssembler();
                lir.emitCode(tasm);

                CiTargetMethod targetMethod = tasm.finishTargetMethod(method, compiler.runtime, false);
                if (assumptions != null && !assumptions.isEmpty()) {
                    targetMethod.setAssumptions(assumptions);
                }

                if (context().isObserved()) {
                    context().observable.fireCompilationEvent("After code generation", this, lir, targetMethod);
                }
                return targetMethod;
            } finally {
                context().timers.endScope();
            }
        }

        return null;
    }

    private GraalContext context() {
        return compiler.context;
    }

    public void printGraph(String phase, Graph printedGraph) {
        if (context().isObserved()) {
            context().observable.fireCompilationEvent(phase, this, printedGraph);
        }
    }
}
