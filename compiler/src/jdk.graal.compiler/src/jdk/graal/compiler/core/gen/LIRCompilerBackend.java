/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.gen;

import java.util.Collection;
import java.util.List;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.LIRGenerationPhase;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.alloc.LinearScanOrder;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.cfg.CodeEmissionOrder;
import jdk.graal.compiler.core.common.cfg.CodeEmissionOrder.ComputationTime;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.lir.ComputeCodeEmissionOrder;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.alloc.OutOfRegistersException;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.asm.EntryPointDecorator;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.phases.AllocationPhase.AllocationContext;
import jdk.graal.compiler.lir.phases.FinalCodeAnalysisPhase.FinalCodeAnalysisContext;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.VMConstant;

public class LIRCompilerBackend {
    private static final TimerKey EmitLIR = DebugContext.timer("EmitLIR").doc("Time spent generating LIR from HIR.");
    private static final TimerKey EmitCode = DebugContext.timer("EmitCode").doc("Time spent generating machine code from LIR.");
    private static final TimerKey BackEnd = DebugContext.timer("BackEnd").doc("Time spent in EmitLIR and EmitCode.");

    @SuppressWarnings("try")
    public static void emitBackEnd(StructuredGraph graph, Object stub, ResolvedJavaMethod installedCodeOwner, Backend backend, CompilationResult compilationResult,
                    CompilationResultBuilderFactory factory, EntryPointDecorator entryPointDecorator, RegisterConfig registerConfig, LIRSuites lirSuites) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("BackEnd", graph, graph.getLastSchedule()); DebugCloseable a = BackEnd.start(debug)) {
            LIRGenerationResult lirGen = emitLIR(backend, graph, stub, registerConfig, lirSuites, entryPointDecorator);
            try (DebugContext.Scope s2 = debug.scope("CodeGen", lirGen, lirGen.getLIR())) {
                int bytecodeSize = graph.method() == null ? 0 : graph.getBytecodeSize();
                compilationResult.setHasUnsafeAccess(graph.hasUnsafeAccess());
                emitCode(backend,
                                graph.getAssumptions(),
                                graph.method(),
                                graph.getMethods(),
                                graph.getSpeculationLog(),
                                bytecodeSize,
                                lirGen,
                                compilationResult,
                                installedCodeOwner,
                                factory,
                                entryPointDecorator);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        } finally {
            graph.checkCancellation();
        }
    }

    @SuppressWarnings("try")
    public static LIRGenerationResult emitLIR(Backend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, LIRSuites lirSuites,
                    EntryPointDecorator entryPointDecorator) {
        String registerPressure = GraalOptions.RegisterPressure.getValue(graph.getOptions());
        String[] allocationRestrictedTo = registerPressure == null ? null : registerPressure.split(",");
        try {
            return emitLIR0(backend, graph, stub, registerConfig, lirSuites, allocationRestrictedTo, entryPointDecorator);
        } catch (OutOfRegistersException e) {
            if (allocationRestrictedTo != null) {
                allocationRestrictedTo = null;
                return emitLIR0(backend, graph, stub, registerConfig, lirSuites, allocationRestrictedTo, entryPointDecorator);
            }
            /* If the re-execution fails we convert the exception into a "hard" failure */
            throw new GraalError(e);
        } finally {
            graph.checkCancellation();
        }
    }

    @SuppressWarnings("try")
    private static LIRGenerationResult emitLIR0(Backend backend,
                    StructuredGraph graph,
                    Object stub,
                    RegisterConfig registerConfig,
                    LIRSuites lirSuites,
                    String[] allocationRestrictedTo, EntryPointDecorator entryPointDecorator) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope ds = debug.scope("EmitLIR"); DebugCloseable a = EmitLIR.start(debug)) {
            assert graph.isAfterStage(StageFlag.VALUE_PROXY_REMOVAL);

            ScheduleResult schedule = graph.getLastSchedule();
            HIRBlock[] blocks = schedule.getCFG().getBlocks();
            HIRBlock startBlock = schedule.getCFG().getStartBlock();
            assert startBlock != null;
            assert startBlock.getPredecessorCount() == 0 : Assertions.errorMessage(startBlock);

            CodeEmissionOrder<?> blockOrder = backend.newBlockOrder(blocks.length, startBlock);
            int[] linearScanOrder = LinearScanOrder.computeLinearScanOrder(blocks.length, startBlock);
            LIR lir = new LIR(schedule.getCFG(), linearScanOrder, graph.getOptions(), graph.getDebug());
            if (ComputeCodeEmissionOrder.Options.EarlyCodeEmissionOrder.getValue(graph.getOptions())) {
                lir.setCodeEmittingOrder(blockOrder.computeCodeEmittingOrder(graph.getOptions(), ComputationTime.BEFORE_CONTROL_FLOW_OPTIMIZATIONS));
            }

            LIRGenerationProvider lirBackend = (LIRGenerationProvider) backend;
            RegisterAllocationConfig registerAllocationConfig = backend.newRegisterAllocationConfig(registerConfig, allocationRestrictedTo, stub);
            LIRGenerationResult lirGenRes = lirBackend.newLIRGenerationResult(graph.compilationId(), lir, registerAllocationConfig, graph, stub);
            LIRGeneratorTool lirGen = lirBackend.newLIRGenerator(lirGenRes);
            NodeLIRBuilderTool nodeLirGen = lirBackend.newNodeLIRBuilder(graph, lirGen);

            // LIR generation
            LIRGenerationPhase.LIRGenerationContext context = new LIRGenerationPhase.LIRGenerationContext(lirGen, nodeLirGen, graph, schedule);
            new LIRGenerationPhase().apply(backend.getTarget(), lirGenRes, context);

            if (entryPointDecorator != null) {
                entryPointDecorator.initialize(backend.getProviders(), lirGenRes);
            }

            try (DebugContext.Scope s = debug.scope("LIRStages", nodeLirGen, lirGenRes, lir)) {
                // Dump LIR along with HIR (the LIR is looked up from context)
                debug.dump(DebugContext.BASIC_LEVEL, graph.getLastSchedule(), "After LIR generation");
                LIRGenerationResult result = emitLowLevel(backend.getTarget(), lirGenRes, lirGen, lirSuites, registerAllocationConfig, blockOrder);
                /*
                 * LIRGeneration may have changed the CFG, so in case a schedule is needed afterward
                 * we make sure it will be recomputed.
                 */
                graph.clearLastSchedule();
                return result;
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        } finally {
            graph.checkCancellation();
        }
    }

    private static LIRGenerationResult emitLowLevel(TargetDescription target, LIRGenerationResult lirGenRes, LIRGeneratorTool lirGen, LIRSuites lirSuites,
                    RegisterAllocationConfig registerAllocationConfig, CodeEmissionOrder<?> blockOrder) {
        DebugContext debug = lirGenRes.getLIR().getDebug();
        PreAllocationOptimizationContext preAllocOptContext = new PreAllocationOptimizationContext(lirGen);
        lirSuites.getPreAllocationOptimizationStage().apply(target, lirGenRes, preAllocOptContext);
        debug.dump(DebugContext.BASIC_LEVEL, lirGenRes.getLIR(), "After PreAllocationOptimizationStage");

        AllocationContext allocContext = new AllocationContext(lirGen.getSpillMoveFactory(), registerAllocationConfig);
        lirSuites.getAllocationStage().apply(target, lirGenRes, allocContext);
        debug.dump(DebugContext.BASIC_LEVEL, lirGenRes.getLIR(), "After AllocationStage");

        PostAllocationOptimizationContext postAllocOptContext = new PostAllocationOptimizationContext(lirGen, blockOrder);
        lirSuites.getPostAllocationOptimizationStage().apply(target, lirGenRes, postAllocOptContext);
        debug.dump(DebugContext.BASIC_LEVEL, lirGenRes.getLIR(), "After PostAllocationOptimizationStage");

        FinalCodeAnalysisContext finalCodeAnalysisContext = new FinalCodeAnalysisContext(lirGen);
        lirSuites.getFinalCodeAnalysisStage().apply(target, lirGenRes, finalCodeAnalysisContext);
        debug.dump(DebugContext.BASIC_LEVEL, lirGenRes.getLIR(), "After FinalCodeAnalysisStage");

        return lirGenRes;
    }

    @SuppressWarnings("try")
    public static void emitCode(Backend backend,
                    Assumptions assumptions,
                    ResolvedJavaMethod rootMethod,
                    Collection<ResolvedJavaMethod> inlinedMethods,
                    SpeculationLog speculationLog,
                    int bytecodeSize,
                    LIRGenerationResult lirGenRes,
                    CompilationResult compilationResult,
                    ResolvedJavaMethod installedCodeOwner,
                    CompilationResultBuilderFactory factory,
                    EntryPointDecorator entryPointDecorator) {
        DebugContext debug = lirGenRes.getLIR().getDebug();
        try (DebugCloseable a = EmitCode.start(debug); DebugContext.CompilerPhaseScope cps = debug.enterCompilerPhase("Emit code");) {
            LIRGenerationProvider lirBackend = (LIRGenerationProvider) backend;

            FrameMap frameMap = lirGenRes.getFrameMap();
            CompilationResultBuilder crb = lirBackend.newCompilationResultBuilder(lirGenRes, frameMap, compilationResult, factory);
            lirBackend.emitCode(crb, installedCodeOwner, entryPointDecorator);
            if (assumptions != null && !assumptions.isEmpty()) {
                compilationResult.setAssumptions(assumptions.toArray());
            }
            if (rootMethod != null) {
                compilationResult.setMethods(rootMethod, inlinedMethods);
                compilationResult.setBytecodeSize(bytecodeSize);
            }
            if (speculationLog != null) {
                compilationResult.setSpeculationLog(speculationLog);
            }
            crb.finish();
            if (debug.isCountEnabled()) {
                List<DataPatch> ldp = compilationResult.getDataPatches();
                JavaKind[] kindValues = JavaKind.values();
                CounterKey[] dms = new CounterKey[kindValues.length];
                for (int i = 0; i < dms.length; i++) {
                    dms[i] = DebugContext.counter("DataPatches-%s", kindValues[i]);
                }

                for (DataPatch dp : ldp) {
                    JavaKind kind = JavaKind.Illegal;
                    if (dp.reference instanceof ConstantReference) {
                        VMConstant constant = ((ConstantReference) dp.reference).getConstant();
                        if (constant instanceof JavaConstant) {
                            kind = ((JavaConstant) constant).getJavaKind();
                        }
                    }
                    dms[kind.ordinal()].add(debug, 1);
                }

                DebugContext.counter("CompilationResults").increment(debug);
                DebugContext.counter("CodeBytesEmitted").add(debug, compilationResult.getTargetCodeSize());
                DebugContext.counter("InfopointsEmitted").add(debug, compilationResult.getInfopoints().size());
                DebugContext.counter("DataPatches").add(debug, ldp.size());
                DebugContext.counter("ExceptionHandlersEmitted").add(debug, compilationResult.getExceptionHandlers().size());
            }

            debug.dump(DebugContext.BASIC_LEVEL, compilationResult, "After code generation");
        }
    }
}
