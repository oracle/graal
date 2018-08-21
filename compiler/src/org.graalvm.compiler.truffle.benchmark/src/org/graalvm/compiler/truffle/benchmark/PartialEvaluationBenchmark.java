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
package org.graalvm.compiler.truffle.benchmark;

import java.util.Arrays;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.Cancellable;
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
import org.graalvm.compiler.truffle.runtime.TruffleTreeDebugHandlersFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

@State(Scope.Benchmark)
public abstract class PartialEvaluationBenchmark {

    private static final int AST_WARMUPS = 5;

    private final TruffleCompilerImpl compiler = (TruffleCompilerImpl) TruffleCompilerRuntime.getRuntime().getTruffleCompiler();
    private final PartialEvaluator partialEvaluator = compiler.getPartialEvaluator();

    private final OptimizedCallTarget callTarget;
    private final Object[] callArguments;
    private final OptionValues optionValues;
    private final DebugContext debugContext;
    private final TruffleInlining truffleInlining;
    private final CompilationIdentifier identifier;
    private final SpeculationLog speculationLog;
    private final Cancellable cancellable;

    private final String name;
    private final ResolvedJavaMethod method;
    private final PhaseContext phaseContext;
    private final HighTierContext tierContext;

    public PartialEvaluationBenchmark() {
        callTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(rootNode());
        callArguments = callArguments();
        optionValues = TruffleCompilerOptions.getOptions();
        debugContext = DebugContext.create(optionValues, Arrays.asList(new TruffleTreeDebugHandlersFactory()));
        truffleInlining = new TruffleInlining(callTarget, new DefaultInliningPolicy());
        identifier = compiler.getCompilationIdentifier(callTarget);
        speculationLog = callTarget.getSpeculationLog();
        cancellable = new CancellableCompileTask();

        name = callTarget.getName();
        method = compiler.getPartialEvaluator().rootForCallTarget(callTarget);
        phaseContext = new PhaseContext(compiler.getPartialEvaluator().getProviders());
        tierContext = new HighTierContext(compiler.getPartialEvaluator().getProviders(), new PhaseSuite<HighTierContext>(), OptimisticOptimizations.NONE);

        // run call target so that all classes are loaded and initialized
        for (int i = 0; i < AST_WARMUPS; i++) {
            callTarget.call(callArguments);
        }
    }

    protected abstract RootNode rootNode();

    protected Object[] callArguments() {
        return new Object[0];
    }

    @Benchmark
    public Object call() {
        return callTarget.call(callArguments);
    }

    @Benchmark
    public StructuredGraph createGraph() {
        // @formatter:off
        return partialEvaluator.createGraph(
            debugContext, callTarget, truffleInlining,
            AllowAssumptions.YES, identifier, speculationLog, cancellable);
        // @formatter:on
    }

    @Benchmark
    public void fastPartialEvaluate() {
        // @formatter:off
        StructuredGraph structuredGraph =
            new StructuredGraph.Builder(optionValues, debugContext, AllowAssumptions.YES)
                               .name(name)
                               .method(method)
                               .speculationLog(speculationLog)
                               .compilationId(identifier)
                               .cancellable(cancellable)
                               .build();
        // @formatter:on
        partialEvaluator.fastPartialEvaluation(callTarget, truffleInlining, structuredGraph, phaseContext, tierContext);
    }

}
