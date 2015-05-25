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

import static com.oracle.graal.compiler.GraalCompiler.Options.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig.*;
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CompilationResult.ConstantReference;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.LIRGenerationPhase.LIRGenerationContext;
import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.alloc.lsra.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.phases.AllocationPhase.AllocationContext;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.options.*;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;

/**
 * Static methods for orchestrating the compilation of a {@linkplain StructuredGraph graph}.
 */
public class GraalCompiler {

    private static final DebugTimer FrontEnd = Debug.timer("FrontEnd");
    private static final DebugTimer BackEnd = Debug.timer("BackEnd");
    private static final DebugTimer EmitLIR = Debug.timer("EmitLIR");
    private static final DebugTimer EmitCode = Debug.timer("EmitCode");

    static class Options {

        // @formatter:off
        @Option(help = "Repeatedly run the LIR code generation pass to improve statistical profiling results.", type = OptionType.Debug)
        public static final OptionValue<Integer> EmitLIRRepeatCount = new OptionValue<>(0);
        // @formatter:on

    }

    /**
     * Encapsulates all the inputs to a {@linkplain GraalCompiler#compile(Request) compilation}.
     */
    public static class Request<T extends CompilationResult> {
        public final StructuredGraph graph;
        public final CallingConvention cc;
        public final ResolvedJavaMethod installedCodeOwner;
        public final Providers providers;
        public final Backend backend;
        public final TargetDescription target;
        public final PhaseSuite<HighTierContext> graphBuilderSuite;
        public final OptimisticOptimizations optimisticOpts;
        public final ProfilingInfo profilingInfo;
        public final SpeculationLog speculationLog;
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
         * @param target
         * @param graphBuilderSuite
         * @param optimisticOpts
         * @param profilingInfo
         * @param speculationLog
         * @param suites
         * @param lirSuites
         * @param compilationResult
         * @param factory
         */
        public Request(StructuredGraph graph, CallingConvention cc, ResolvedJavaMethod installedCodeOwner, Providers providers, Backend backend, TargetDescription target,
                        PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, SpeculationLog speculationLog, Suites suites,
                        LIRSuites lirSuites, T compilationResult, CompilationResultBuilderFactory factory) {
            this.graph = graph;
            this.cc = cc;
            this.installedCodeOwner = installedCodeOwner;
            this.providers = providers;
            this.backend = backend;
            this.target = target;
            this.graphBuilderSuite = graphBuilderSuite;
            this.optimisticOpts = optimisticOpts;
            this.profilingInfo = profilingInfo;
            this.speculationLog = speculationLog;
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
                    TargetDescription target, PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, SpeculationLog speculationLog,
                    Suites suites, LIRSuites lirSuites, T compilationResult, CompilationResultBuilderFactory factory) {
        return compile(new Request<>(graph, cc, installedCodeOwner, providers, backend, target, graphBuilderSuite, optimisticOpts, profilingInfo, speculationLog, suites, lirSuites, compilationResult,
                        factory));
    }

    /**
     * Services a given compilation request.
     *
     * @return the result of the compilation
     */
    public static <T extends CompilationResult> T compile(Request<T> r) {
        assert !r.graph.isFrozen();
        try (Scope s0 = Debug.scope("GraalCompiler", r.graph, r.providers.getCodeCache())) {
            SchedulePhase schedule = emitFrontEnd(r.providers, r.target, r.graph, r.graphBuilderSuite, r.optimisticOpts, r.profilingInfo, r.speculationLog, r.suites);
            emitBackEnd(r.graph, null, r.cc, r.installedCodeOwner, r.backend, r.target, r.compilationResult, r.factory, schedule, null, r.lirSuites);
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
    public static SchedulePhase emitFrontEnd(Providers providers, TargetDescription target, StructuredGraph graph, PhaseSuite<HighTierContext> graphBuilderSuite,
                    OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, SpeculationLog speculationLog, Suites suites) {
        try (Scope s = Debug.scope("FrontEnd"); DebugCloseable a = FrontEnd.start()) {
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }

            HighTierContext highTierContext = new HighTierContext(providers, graphBuilderSuite, optimisticOpts);
            if (graph.start().next() == null) {
                graphBuilderSuite.apply(graph, highTierContext);
                new DeadCodeEliminationPhase(Optional).apply(graph);
            } else {
                Debug.dump(graph, "initial state");
            }

            suites.getHighTier().apply(graph, highTierContext);
            graph.maybeCompress();

            MidTierContext midTierContext = new MidTierContext(providers, target, optimisticOpts, profilingInfo, speculationLog);
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

    public static <T extends CompilationResult> void emitBackEnd(StructuredGraph graph, Object stub, CallingConvention cc, ResolvedJavaMethod installedCodeOwner, Backend backend,
                    TargetDescription target, T compilationResult, CompilationResultBuilderFactory factory, SchedulePhase schedule, RegisterConfig registerConfig, LIRSuites lirSuites) {
        try (Scope s = Debug.scope("BackEnd", schedule); DebugCloseable a = BackEnd.start()) {
            // Repeatedly run the LIR code generation pass to improve statistical profiling results.
            for (int i = 0; i < EmitLIRRepeatCount.getValue(); i++) {
                SchedulePhase dummySchedule = new SchedulePhase();
                dummySchedule.apply(graph);
                emitLIR(backend, target, dummySchedule, graph, stub, cc, registerConfig, lirSuites);
            }

            LIRGenerationResult lirGen = null;
            lirGen = emitLIR(backend, target, schedule, graph, stub, cc, registerConfig, lirSuites);
            try (Scope s2 = Debug.scope("CodeGen", lirGen, lirGen.getLIR())) {
                emitCode(backend, graph.getAssumptions(), graph.method(), graph.getInlinedMethods(), lirGen, compilationResult, installedCodeOwner, factory);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    public static LIRGenerationResult emitLIR(Backend backend, TargetDescription target, SchedulePhase schedule, StructuredGraph graph, Object stub, CallingConvention cc,
                    RegisterConfig registerConfig, LIRSuites lirSuites) {
        try {
            return emitLIR0(backend, target, schedule, graph, stub, cc, registerConfig, lirSuites);
        } catch (OutOfRegistersException e) {
            if (RegisterPressure.getValue() != null && !RegisterPressure.getValue().equals(ALL_REGISTERS)) {
                try (OverrideScope s = OptionValue.override(RegisterPressure, ALL_REGISTERS)) {
                    // retry with default register set
                    return emitLIR0(backend, target, schedule, graph, stub, cc, registerConfig, lirSuites);
                }
            } else {
                throw e;
            }
        }
    }

    private static LIRGenerationResult emitLIR0(Backend backend, TargetDescription target, SchedulePhase schedule, StructuredGraph graph, Object stub, CallingConvention cc,
                    RegisterConfig registerConfig, LIRSuites lirSuites) {
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
                new LIRGenerationPhase().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }

            try (Scope s = Debug.scope("LIRStages", nodeLirGen, lir)) {
                return emitLowLevel(target, codeEmittingOrder, linearScanOrder, lirGenRes, lirGen, lirSuites);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    public static <T extends AbstractBlockBase<T>> LIRGenerationResult emitLowLevel(TargetDescription target, List<T> codeEmittingOrder, List<T> linearScanOrder, LIRGenerationResult lirGenRes,
                    LIRGeneratorTool lirGen, LIRSuites lirSuites) {
        PreAllocationOptimizationContext preAllocOptContext = new PreAllocationOptimizationContext(lirGen);
        lirSuites.getPreAllocationOptimizationStage().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, preAllocOptContext);

        AllocationContext allocContext = new AllocationContext(lirGen.getSpillMoveFactory());
        lirSuites.getAllocationStage().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, allocContext);

        PostAllocationOptimizationContext postAllocOptContext = new PostAllocationOptimizationContext(lirGen);
        lirSuites.getPostAllocationOptimizationStage().apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, postAllocOptContext);

        return lirGenRes;
    }

    public static void emitCode(Backend backend, Assumptions assumptions, ResolvedJavaMethod rootMethod, Set<ResolvedJavaMethod> inlinedMethods, LIRGenerationResult lirGenRes,
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
            }

            if (Debug.isMeterEnabled()) {
                List<DataPatch> ldp = compilationResult.getDataPatches();
                Kind[] kindValues = Kind.values();
                DebugMetric[] dms = new DebugMetric[kindValues.length];
                for (int i = 0; i < dms.length; i++) {
                    dms[i] = Debug.metric("DataPatches-%s", kindValues[i]);
                }

                for (DataPatch dp : ldp) {
                    Kind kind = Kind.Illegal;
                    if (dp.reference instanceof ConstantReference) {
                        VMConstant constant = ((ConstantReference) dp.reference).getConstant();
                        kind = ((JavaConstant) constant).getKind();
                    }
                    dms[kind.ordinal()].add(1);
                }

                Debug.metric("CompilationResults").increment();
                Debug.metric("CodeBytesEmitted").add(compilationResult.getTargetCodeSize());
                Debug.metric("InfopointsEmitted").add(compilationResult.getInfopoints().size());
                Debug.metric("DataPatches").add(ldp.size());
                Debug.metric("ExceptionHandlersEmitted").add(compilationResult.getExceptionHandlers().size());
            }

            if (Debug.isLogEnabled()) {
                Debug.log("%s", backend.getProviders().getCodeCache().disassemble(compilationResult, null));
            }

            Debug.dump(compilationResult, "After code generation");
        }
    }
}
