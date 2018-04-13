/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.pelang.benchmark;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.microbenchmarks.graal.GraalBenchmark;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.runtime.CancellableCompileTask;
import org.graalvm.compiler.truffle.runtime.DefaultInliningPolicy;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import jdk.vm.ci.meta.SpeculationLog;

@State(Scope.Thread)
public abstract class PartialEvaluationBenchmark extends GraalBenchmark {

    private final PartialEvaluatorProxy proxy = new PartialEvaluatorProxy();
    private CompileState state;

    protected abstract OptimizedCallTarget createCallTarget();

    @Setup
    public void setup() {
        OptimizedCallTarget callTarget = createCallTarget();

        // call the target a few times
        callTarget.call();
        callTarget.call();
        callTarget.call();

        state = proxy.prepare(callTarget);
    }

    @Benchmark
    public void createGraph() {
        proxy.createGraph(state);
    }

    @Benchmark
    public void fastPartialEvaluate() {
        proxy.fastPartialEvaluation(state);
    }

    private static class PartialEvaluatorProxy {

        final TruffleCompilerImpl truffleCompiler = (TruffleCompilerImpl) TruffleCompilerRuntime.getRuntime().getTruffleCompiler();
        final PartialEvaluator partialEvaluator = truffleCompiler.getPartialEvaluator();

        CompileState prepare(OptimizedCallTarget callTarget) {
            CompileState state = new CompileState();
            state.callTarget = callTarget;
            state.debugContext = DebugContext.DISABLED;
            state.allowAssumptions = AllowAssumptions.YES;
            state.cancellable = new CancellableCompileTask();
            state.optionValues = TruffleCompilerOptions.getOptions();
            state.truffleInlining = new TruffleInlining(callTarget, new DefaultInliningPolicy());
            state.identifier = truffleCompiler.getCompilationIdentifier(callTarget);
            state.speculationLog = callTarget.getSpeculationLog();
            state.structuredGraph = new StructuredGraph.Builder(state.optionValues, state.debugContext, state.allowAssumptions).name(callTarget.toString()).method(
                            partialEvaluator.rootForCallTarget(callTarget)).speculationLog(
                                            state.speculationLog).compilationId(state.identifier).cancellable(state.cancellable).build();
            state.phaseContext = new PhaseContext(partialEvaluator.getProviders());
            state.tierContext = new HighTierContext(partialEvaluator.getProviders(), new PhaseSuite<HighTierContext>(), OptimisticOptimizations.NONE);

            return state;
        }

        StructuredGraph createGraph(CompileState state) {
            return partialEvaluator.createGraph(state.debugContext, state.callTarget, state.truffleInlining, state.allowAssumptions, state.identifier, state.speculationLog, state.cancellable);
        }

        void fastPartialEvaluation(CompileState state) {
            partialEvaluator.fastPartialEvaluation(state.callTarget, state.truffleInlining, state.structuredGraph, state.phaseContext, state.tierContext);
        }

    }

    private static class CompileState {

        OptimizedCallTarget callTarget;
        DebugContext debugContext;
        AllowAssumptions allowAssumptions;
        CancellableCompileTask cancellable;
        OptionValues optionValues;
        TruffleInlining truffleInlining;
        CompilationIdentifier identifier;
        SpeculationLog speculationLog;
        StructuredGraph structuredGraph;
        PhaseContext phaseContext;
        HighTierContext tierContext;

    }

}
