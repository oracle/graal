/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.compiler.common.GraalOptions.UseGraalInstrumentation;
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import java.util.List;

import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.VMConstant;

import com.oracle.graal.code.CompilationResult;
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
import com.oracle.graal.lir.BailoutAndRestartBackendException;
import com.oracle.graal.lir.LIR;
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
import com.oracle.graal.nodes.StructuredGraph.ScheduleResult;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.common.instrumentation.ExtractInstrumentationPhase;
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
        public Request(StructuredGraph graph, ResolvedJavaMethod installedCodeOwner, Providers providers, Backend backend, PhaseSuite<HighTierContext> graphBuilderSuite,
                        OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, Suites suites, LIRSuites lirSuites, T compilationResult, CompilationResultBuilderFactory factory) {
            this.graph = graph;
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
     * @param installedCodeOwner the method the compiled code will be associated with once
     *            installed. This argument can be null.
     * @return the result of the compilation
     */
    public static <T extends CompilationResult> T compileGraph(StructuredGraph graph, ResolvedJavaMethod installedCodeOwner, Providers providers, Backend backend,
                    PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, Suites suites, LIRSuites lirSuites, T compilationResult,
                    CompilationResultBuilderFactory factory) {
        return compile(new Request<>(graph, installedCodeOwner, providers, backend, graphBuilderSuite, optimisticOpts, profilingInfo, suites, lirSuites, compilationResult, factory));
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
            emitFrontEnd(r.providers, r.backend, r.graph, r.graphBuilderSuite, r.optimisticOpts, r.profilingInfo, r.suites);
            emitBackEnd(r.graph, null, r.installedCodeOwner, r.backend, r.compilationResult, r.factory, null, r.lirSuites);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
        return r.compilationResult;
    }

    /**
     * Builds the graph, optimizes it.
     */
    @SuppressWarnings("try")
    public static void emitFrontEnd(Providers providers, TargetProvider target, StructuredGraph graph, PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts,
                    ProfilingInfo profilingInfo, Suites suites) {
        try (Scope s = Debug.scope("FrontEnd"); DebugCloseable a = FrontEnd.start()) {
            HighTierContext highTierContext = new HighTierContext(providers, graphBuilderSuite, optimisticOpts);
            if (graph.start().next() == null) {
                graphBuilderSuite.apply(graph, highTierContext);
                if (UseGraalInstrumentation.getValue()) {
                    new ExtractInstrumentationPhase().apply(graph, highTierContext);
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

            Debug.dump(graph.getLastSchedule(), "Final HIR schedule");
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    @SuppressWarnings("try")
    public static <T extends CompilationResult> void emitBackEnd(StructuredGraph graph, Object stub, ResolvedJavaMethod installedCodeOwner, Backend backend, T compilationResult,
                    CompilationResultBuilderFactory factory, RegisterConfig registerConfig, LIRSuites lirSuites) {
        try (Scope s = Debug.scope("BackEnd", graph.getLastSchedule()); DebugCloseable a = BackEnd.start()) {
            // Repeatedly run the LIR code generation pass to improve statistical profiling results.
            for (int i = 0; i < EmitLIRRepeatCount.getValue(); i++) {
                SchedulePhase dummySchedule = new SchedulePhase();
                dummySchedule.apply(graph);
                emitLIR(backend, graph, stub, registerConfig, lirSuites, compilationResult);
            }

            LIRGenerationResult lirGen = null;
            lirGen = emitLIR(backend, graph, stub, registerConfig, lirSuites, compilationResult);
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
    public static <T extends CompilationResult> LIRGenerationResult emitLIR(Backend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, LIRSuites lirSuites, T compilationResult) {
        OverrideScope overrideScope = null;
        LIRSuites lirSuites0 = lirSuites;
        while (true) {
            try (OverrideScope scope = overrideScope) {
                return emitLIR0(backend, graph, stub, registerConfig, lirSuites0, compilationResult);
            } catch (BailoutAndRestartBackendException e) {
                if (BailoutAndRestartBackendException.Options.LIRUnlockBackendRestart.getValue() && e.shouldRestart()) {
                    overrideScope = e.getOverrideScope();
                    lirSuites0 = e.updateLIRSuites(lirSuites);
                    if (lirSuites0 != null) {
                        continue;
                    }
                }
                /* If the restart fails we convert the exception into a "hard" failure */
                throw new JVMCIError(e);
            }
        }
    }

    @SuppressWarnings("try")
    private static <T extends CompilationResult> LIRGenerationResult emitLIR0(Backend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, LIRSuites lirSuites,
                    T compilationResult) {
        try (Scope ds = Debug.scope("EmitLIR"); DebugCloseable a = EmitLIR.start()) {
            ScheduleResult schedule = graph.getLastSchedule();
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
            String compilationUnitName = getCompilationUnitName(graph, compilationResult);
            LIRGenerationResult lirGenRes = backend.newLIRGenerationResult(compilationUnitName, lir, frameMapBuilder, graph, stub);
            LIRGeneratorTool lirGen = backend.newLIRGenerator(lirGenRes);
            NodeLIRBuilderTool nodeLirGen = backend.newNodeLIRBuilder(graph, lirGen);

            // LIR generation
            LIRGenerationContext context = new LIRGenerationContext(lirGen, nodeLirGen, graph, schedule);
            try (Scope s = Debug.scope("LIRGeneration", nodeLirGen, lir)) {
                new LIRGenerationPhase().apply(backend.getTarget(), lirGenRes, codeEmittingOrder, linearScanOrder, context);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }

            try (Scope s = Debug.scope("LIRStages", nodeLirGen, lir)) {
                Debug.dump(1, lir, "After LIR generation");
                LIRGenerationResult result = emitLowLevel(backend.getTarget(), codeEmittingOrder, linearScanOrder, lirGenRes, lirGen, lirSuites, backend.newRegisterAllocationConfig(registerConfig));
                Debug.dump(1, lir, "Before code generation");
                return result;
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    protected static <T extends CompilationResult> String getCompilationUnitName(StructuredGraph graph, T compilationResult) {
        if (compilationResult != null && compilationResult.getName() != null) {
            return compilationResult.getName();
        }
        ResolvedJavaMethod method = graph.method();
        if (method == null) {
            return "<unknown>";
        }
        return method.format("%H.%n(%p)");
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
    public static void emitCode(Backend backend, Assumptions assumptions, ResolvedJavaMethod rootMethod, List<ResolvedJavaMethod> inlinedMethods, int bytecodeSize, LIRGenerationResult lirGenRes,
                    CompilationResult compilationResult, ResolvedJavaMethod installedCodeOwner, CompilationResultBuilderFactory factory) {
        try (DebugCloseable a = EmitCode.start()) {
            FrameMap frameMap = lirGenRes.getFrameMap();
            CompilationResultBuilder crb = backend.newCompilationResultBuilder(lirGenRes, frameMap, compilationResult, factory);
            backend.emitCode(crb, lirGenRes.getLIR(), installedCodeOwner);
            if (assumptions != null && !assumptions.isEmpty()) {
                compilationResult.setAssumptions(assumptions.toArray());
            }
            if (rootMethod != null) {
                compilationResult.setMethods(rootMethod, inlinedMethods);
                compilationResult.setBytecodeSize(bytecodeSize);
            }
            crb.finish();
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
                        if (constant instanceof JavaConstant) {
                            kind = ((JavaConstant) constant).getJavaKind();
                        }
                    }
                    dms[kind.ordinal()].add(1);
                }

                Debug.metric("CompilationResults").increment();
                Debug.metric("CodeBytesEmitted").add(compilationResult.getTargetCodeSize());
                Debug.metric("InfopointsEmitted").add(compilationResult.getInfopoints().size());
                Debug.metric("DataPatches").add(ldp.size());
                Debug.metric("ExceptionHandlersEmitted").add(compilationResult.getExceptionHandlers().size());
            }

            Debug.dump(1, compilationResult, "After code generation");
        }
    }
}
