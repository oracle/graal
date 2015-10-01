/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.GraalCompilerOptions.EmitLIRRepeatCount;
import static com.oracle.graal.compiler.common.GraalOptions.RegisterPressure;
import static com.oracle.graal.compiler.common.GraalOptions.UseGraalQueries;
import static com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig.ALL_REGISTERS;
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import java.util.List;
import java.util.Set;

import jdk.internal.jvmci.code.CallingConvention;
import jdk.internal.jvmci.code.CompilationResult;
import jdk.internal.jvmci.code.CompilationResult.ConstantReference;
import jdk.internal.jvmci.code.CompilationResult.DataPatch;
import jdk.internal.jvmci.code.RegisterConfig;
import jdk.internal.jvmci.code.TargetDescription;
import jdk.internal.jvmci.meta.Assumptions;
import jdk.internal.jvmci.meta.DefaultProfilingInfo;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.ProfilingInfo;
import jdk.internal.jvmci.meta.ResolvedJavaMethod;
import jdk.internal.jvmci.meta.TriState;
import jdk.internal.jvmci.meta.VMConstant;
import jdk.internal.jvmci.options.OptionValue;
import jdk.internal.jvmci.options.OptionValue.OverrideScope;

import com.oracle.graal.compiler.LIRGenerationPhase.LIRGenerationContext;
import com.oracle.graal.compiler.common.alloc.ComputeBlockOrder;
import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.DebugCloseable;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.debug.DebugTimer;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.alloc.lsra.OutOfRegistersException;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.framemap.FrameMap;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.phases.AllocationPhase.AllocationContext;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.common.query.ExtractICGPhase;
import com.oracle.graal.phases.schedule.SchedulePhase;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.LowTierContext;
import com.oracle.graal.phases.tiers.MidTierContext;
import com.oracle.graal.phases.tiers.Suites;
import com.oracle.graal.phases.tiers.TargetProvider;
import com.oracle.graal.phases.util.Providers;

/**
 * Static methods for orchestrating the compilation of a {@linkplain StructuredGraph graph}.
 */
public class GraalCompiler {

    private static final DebugTimer FrontEnd = Debug.timer("FrontEnd");
    private static final DebugTimer BackEnd = Debug.timer("BackEnd");
    private static final DebugTimer EmitLIR = Debug.timer("EmitLIR");
    private static final DebugTimer EmitCode = Debug.timer("EmitCode");

    /**
     * Encapsulates all the inputs to a {@linkplain GraalCompiler#compile(Request) compilation}.
     */
    public static class Request<T extends CompilationResult> {
        public final StructuredGraph graph;
        public final CallingConvention cc;
        public final ResolvedJavaMethod installedCodeOwner;
        public final Providers providers;
        public final Backend backend;
        public final PhaseSuite<HighTierContext> graphBuilderSuite;
        public final OptimisticOptimizations optimisticOpts;
        public final ProfilingInfo profilingInfo;
        public final Suites suites;
        public final LIRSuites lirSuites;
        public final T compilationResult;
        public final CompilationResultBuilderFactory factory;

        /**
         * @param graph the graph to be compiled
         * @param cc the calling convention for calls to the code compiled for {@code graph}
         * @param installedCodeOwner the method the compiled code will be associated with once
         *            installed. This argument can be null.
         * @param providers
         * @param backend
         * @param graphBuilderSuite
         * @param optimisticOpts
         * @param profilingInfo
         * @param suites
         * @param lirSuites
         * @param compilationResult
         * @param factory
         */
        public Request(StructuredGraph graph, CallingConvention cc, ResolvedJavaMethod installedCodeOwner, Providers providers, Backend backend, PhaseSuite<HighTierContext> graphBuilderSuite,
                        OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, Suites suites, LIRSuites lirSuites, T compilationResult, CompilationResultBuilderFactory factory) {
            this.graph = graph;
            this.cc = cc;
            this.installedCodeOwner = installedCodeOwner;
            this.providers = providers;
            this.backend = backend;
            this.graphBuilderSuite = graphBuilderSuite;
            this.optimisticOpts = optimisticOpts;
            this.profilingInfo = profilingInfo;
            this.suites = suites;
            this.lirSuites = lirSuites;
            this.compilationResult = compilationResult;
            this.factory = factory;
        }

        /**
         * Executes this compilation request.
         *
         * @return the result of the compilation
         */
        public T execute() {
            return GraalCompiler.compile(this);
        }
    }

    /**
     * Requests compilation of a given graph.
     *
     * @param graph the graph to be compiled
     * @param cc the calling convention for calls to the code compiled for {@code graph}
     * @param installedCodeOwner the method the compiled code will be associated with once
     *            installed. This argument can be null.
     * @return the result of the compilation
     */
    public static <T extends CompilationResult> T compileGraph(StructuredGraph graph, CallingConvention cc, ResolvedJavaMethod installedCodeOwner, Providers providers, Backend backend,
                    PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, Suites suites, LIRSuites lirSuites, T compilationResult,
                    CompilationResultBuilderFactory factory) {
        return compile(new Request<>(graph, cc, installedCodeOwner, providers, backend, graphBuilderSuite, optimisticOpts, profilingInfo, suites, lirSuites, compilationResult, factory));
    }

    /**
     * Services a given compilation request.
     *
     * @return the result of the compilation
     */
    @SuppressWarnings("try")
    public static <T extends CompilationResult> T compile(Request<T> r) {
        assert !r.graph.isFrozen();
        try (Scope s0 = Debug.scope("GraalCompiler", r.graph, r.providers.getCodeCache())) {
            SchedulePhase schedule = emitFrontEnd(r.providers, r.backend, r.graph, r.graphBuilderSuite, r.optimisticOpts, r.profilingInfo, r.suites);
            emitBackEnd(r.graph, null, r.cc, r.installedCodeOwner, r.backend, r.compilationResult, r.factory, schedule, null, r.lirSuites);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
        return r.compilationResult;
    }

    public static ProfilingInfo getProfilingInfo(StructuredGraph graph) {
        if (graph.method() != null) {
            return graph.method().getProfilingInfo();
        } else {
            return DefaultProfilingInfo.get(TriState.UNKNOWN);
        }
    }

    /**
     * Builds the graph, optimizes it.
     */
    @SuppressWarnings("try")
    public static SchedulePhase emitFrontEnd(Providers providers, TargetProvider target, StructuredGraph graph, PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts,
                    ProfilingInfo profilingInfo, Suites suites) {
        try (Scope s = Debug.scope("FrontEnd"); DebugCloseable a = FrontEnd.start()) {
            HighTierContext highTierContext = new HighTierContext(providers, graphBuilderSuite, optimisticOpts);
            if (graph.start().next() == null) {
                graphBuilderSuite.apply(graph, highTierContext);
                if (UseGraalQueries.getValue()) {
                    new ExtractICGPhase().apply(graph, highTierContext);
                }
                new DeadCodeEliminationPhase(Optional).apply(graph);
            } else {
                Debug.dump(graph, "initial state");
            }

            suites.getHighTier().apply(graph, highTierContext);
            graph.maybeCompress();

            MidTierContext midTierContext = new MidTierContext(providers, target, optimisticOpts, profilingInfo);
            suites.getMidTier().apply(graph, midTierContext);
            graph.maybeCompress();

            LowTierContext lowTierContext = new LowTierContext(providers, target);
            suites.getLowTier().apply(graph, lowTierContext);
            graph.maybeCompress();

            SchedulePhase schedule = new SchedulePhase();
            schedule.apply(graph);
            Debug.dump(schedule, "Final HIR schedule");
            return schedule;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    @SuppressWarnings("try")
    public static <T extends CompilationResult> void emitBackEnd(StructuredGraph graph, Object stub, CallingConvention cc, ResolvedJavaMethod installedCodeOwner, Backend backend, T compilationResult,
                    CompilationResultBuilderFactory factory, SchedulePhase schedule, RegisterConfig registerConfig, LIRSuites lirSuites) {
        try (Scope s = Debug.scope("BackEnd", schedule); DebugCloseable a = BackEnd.start()) {
            // Repeatedly run the LIR code generation pass to improve statistical profiling results.
            for (int i = 0; i < EmitLIRRepeatCount.getValue(); i++) {
                SchedulePhase dummySchedule = new SchedulePhase();
                dummySchedule.apply(graph);
                emitLIR(backend, dummySchedule, graph, stub, cc, registerConfig, lirSuites);
            }

            LIRGenerationResult lirGen = null;
            lirGen = emitLIR(backend, schedule, graph, stub, cc, registerConfig, lirSuites);
            try (Scope s2 = Debug.scope("CodeGen", lirGen, lirGen.getLIR())) {
                int bytecodeSize = graph.method() == null ? 0 : graph.getBytecodeSize();
                compilationResult.setHasUnsafeAccess(graph.hasUnsafeAccess());
                emitCode(backend, graph.getAssumptions(), graph.method(), graph.getInlinedMethods(), bytecodeSize, lirGen, compilationResult, installedCodeOwner, factory);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    @SuppressWarnings("try")
    public static LIRGenerationResult emitLIR(Backend backend, SchedulePhase schedule, StructuredGraph graph, Object stub, CallingConvention cc, RegisterConfig registerConfig, LIRSuites lirSuites) {
        try {
            return emitLIR0(backend, schedule, graph, stub, cc, registerConfig, lirSuites);
        } catch (OutOfRegistersException e) {
            if (RegisterPressure.getValue() != null && !RegisterPressure.getValue().equals(ALL_REGISTERS)) {
                try (OverrideScope s = OptionValue.override(RegisterPressure, ALL_REGISTERS)) {
                    // retry with default register set
                    return emitLIR0(backend, schedule, graph, stub, cc, registerConfig, lirSuites);
                }
            } else {
                throw e;
            }
        }
    }

    @SuppressWarnings("try")
    private static LIRGenerationResult emitLIR0(Backend backend, SchedulePhase schedule, StructuredGraph graph, Object stub, CallingConvention cc, RegisterConfig registerConfig, LIRSuites lirSuites) {
        try (Scope ds = Debug.scope("EmitLIR"); DebugCloseable a = EmitLIR.start()) {
            List<Block> blocks = schedule.getCFG().getBlocks();
            Block startBlock = schedule.getCFG().getStartBlock();
            assert startBlock != null;
            assert startBlock.getPredecessorCount() == 0;

            LIR lir = null;
            List<Block> codeEmittingOrder = null;
            List<Block> linearScanOrder = null;
            try (Scope s = Debug.scope("ComputeLinearScanOrder", lir)) {
                codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blocks.size(), startBlock);
                linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blocks.size(), startBlock);

                lir = new LIR(schedule.getCFG(), linearScanOrder, codeEmittingOrder);
                Debug.dump(lir, "After linear scan order");
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            FrameMapBuilder frameMapBuilder = backend.newFrameMapBuilder(registerConfig);
            String compilationUnitName;
            ResolvedJavaMethod method = graph.method();
            if (method == null) {
                compilationUnitName = "<unknown>";
            } else {
                compilationUnitName = method.format("%H.%n(%p)");
            }
            LIRGenerationResult lirGenRes = backend.newLIRGenerationResult(compilationUnitName, lir, frameMapBuilder, graph.method(), stub);
            LIRGeneratorTool lirGen = backend.newLIRGenerator(cc, lirGenRes);
            NodeLIRBuilderTool nodeLirGen = backend.newNodeLIRBuilder(graph, lirGen);

            // LIR generation
            LIRGenerationContext context = new LIRGenerationContext(lirGen, nodeLirGen, graph, schedule);
            try (Scope s = Debug.scope("LIRGeneration", nodeLirGen, lir)) {
                new LIRGenerationPhase().apply(backend.getTarget(), lirGenRes, codeEmittingOrder, linearScanOrder, context);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }

            try (Scope s = Debug.scope("LIRStages", nodeLirGen, lir)) {
                return emitLowLevel(backend.getTarget(), codeEmittingOrder, linearScanOrder, lirGenRes, lirGen, lirSuites, backend.newRegisterAllocationConfig(registerConfig));
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    public static <T extends AbstractBlockBase<T>> LIRGenerationResult emitLowLevel(TargetDescription target, List<T> codeEmittingOrder, List<T> linearScanOrder, LIRGenerationResult lirGenRes,
                    LIRGeneratorTool lirGen, LIRSuites lirSuites, RegisterAllocationConfig registerAllocationConfig) {
        PreAllocationOptimizationContext preAllocOptContext = new PreAllocationOptimizationContext(lirGen);
        lirSuites.getPreAllocationOptimizationStage().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, preAllocOptContext);

        AllocationContext allocContext = new AllocationContext(lirGen.getSpillMoveFactory(), registerAllocationConfig);
        lirSuites.getAllocationStage().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, allocContext);

        PostAllocationOptimizationContext postAllocOptContext = new PostAllocationOptimizationContext(lirGen);
        lirSuites.getPostAllocationOptimizationStage().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, postAllocOptContext);

        return lirGenRes;
    }

    @SuppressWarnings("try")
    public static void emitCode(Backend backend, Assumptions assumptions, ResolvedJavaMethod rootMethod, Set<ResolvedJavaMethod> inlinedMethods, int bytecodeSize, LIRGenerationResult lirGenRes,
                    CompilationResult compilationResult, ResolvedJavaMethod installedCodeOwner, CompilationResultBuilderFactory factory) {
        try (DebugCloseable a = EmitCode.start()) {
            FrameMap frameMap = lirGenRes.getFrameMap();
            CompilationResultBuilder crb = backend.newCompilationResultBuilder(lirGenRes, frameMap, compilationResult, factory);
            backend.emitCode(crb, lirGenRes.getLIR(), installedCodeOwner);
            crb.finish();
            if (assumptions != null && !assumptions.isEmpty()) {
                compilationResult.setAssumptions(assumptions.toArray());
            }
            if (inlinedMethods != null) {
                compilationResult.setMethods(rootMethod, inlinedMethods);
                compilationResult.setBytecodeSize(bytecodeSize);
            }

            if (Debug.isMeterEnabled()) {
                List<DataPatch> ldp = compilationResult.getDataPatches();
                JavaKind[] kindValues = JavaKind.values();
                DebugMetric[] dms = new DebugMetric[kindValues.length];
                for (int i = 0; i < dms.length; i++) {
                    dms[i] = Debug.metric("DataPatches-%s", kindValues[i]);
                }

                for (DataPatch dp : ldp) {
                    JavaKind kind = JavaKind.Illegal;
                    if (dp.reference instanceof ConstantReference) {
                        VMConstant constant = ((ConstantReference) dp.reference).getConstant();
                        kind = ((JavaConstant) constant).getJavaKind();
                    }
                    dms[kind.ordinal()].add(1);
                }

                Debug.metric("CompilationResults").increment();
                Debug.metric("CodeBytesEmitted").add(compilationResult.getTargetCodeSize());
                Debug.metric("InfopointsEmitted").add(compilationResult.getInfopoints().size());
                Debug.metric("DataPatches").add(ldp.size());
                Debug.metric("ExceptionHandlersEmitted").add(compilationResult.getExceptionHandlers().size());
            }

            Debug.dump(compilationResult, "After code generation");
        }
    }
}
