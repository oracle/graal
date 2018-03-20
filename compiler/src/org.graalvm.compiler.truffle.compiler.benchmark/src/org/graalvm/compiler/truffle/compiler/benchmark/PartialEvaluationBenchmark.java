package org.graalvm.compiler.truffle.compiler.benchmark;

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

    private TruffleCompilerImpl truffleCompiler;
    private PartialEvaluator partialEvaluator;
    private OptimizedCallTarget callTarget;

    private DebugContext debugContext;
    private AllowAssumptions allowAssumptions;
    private CancellableCompileTask cancellable;
    private OptionValues optionValues;
    private TruffleInlining truffleInlining;
    private CompilationIdentifier identifier;
    private SpeculationLog speculationLog;
    private StructuredGraph structuredGraph;
    private PhaseContext phaseContext;
    private HighTierContext tierContext;

    protected abstract OptimizedCallTarget createCallTarget();

    @Setup
    public void setup() {
        truffleCompiler = (TruffleCompilerImpl) TruffleCompilerRuntime.getRuntime().getTruffleCompiler();
        partialEvaluator = truffleCompiler.getPartialEvaluator();
        callTarget = createCallTarget();

        debugContext = DebugContext.DISABLED;
        allowAssumptions = AllowAssumptions.YES;
        cancellable = new CancellableCompileTask();
        optionValues = TruffleCompilerOptions.getOptions();
        truffleInlining = new TruffleInlining(callTarget, new DefaultInliningPolicy());
        identifier = truffleCompiler.getCompilationIdentifier(callTarget);
        speculationLog = callTarget.getSpeculationLog();

        // @formatter:off
        structuredGraph =
            new StructuredGraph
                .Builder(optionValues, debugContext, allowAssumptions)
                .name(callTarget.toString())
                .method(partialEvaluator.rootForCallTarget(callTarget))
                .speculationLog(speculationLog)
                .compilationId(identifier)
                .cancellable(cancellable)
                .build();
        // @formatter:on

        phaseContext = new PhaseContext(partialEvaluator.getProviders());
        tierContext = new HighTierContext(partialEvaluator.getProviders(), new PhaseSuite<HighTierContext>(), OptimisticOptimizations.NONE);
    }

    @Benchmark
    public void createGraph() {
        partialEvaluator.createGraph(debugContext, callTarget, truffleInlining, allowAssumptions, identifier, speculationLog, cancellable);
    }

    @Benchmark
    public void fastPartialEvaluate() {
        partialEvaluator.fastPartialEvaluation(callTarget, truffleInlining, structuredGraph, phaseContext, tierContext);
    }

}
