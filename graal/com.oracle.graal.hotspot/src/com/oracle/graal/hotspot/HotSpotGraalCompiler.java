/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.compiler.common.GraalOptions.OptAssumptions;
import static com.oracle.graal.nodes.StructuredGraph.NO_PROFILING_INFO;
import static com.oracle.graal.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.ROOT_COMPILATION;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Formattable;
import java.util.Formatter;

import com.oracle.graal.api.runtime.GraalJVMCICompiler;
import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.GraalCompiler;
import com.oracle.graal.compiler.common.CompilationIdentifier;
import com.oracle.graal.compiler.common.util.CompilationAlarm;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugConfigScope;
import com.oracle.graal.debug.DebugEnvironment;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.debug.TopLevelDebugConfig;
import com.oracle.graal.debug.internal.DebugScope;
import com.oracle.graal.debug.internal.method.MethodMetricsRootScopeInfo;
import com.oracle.graal.hotspot.CompilationCounters.Options;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.phases.OnStackReplacementPhase;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.IntrinsicContext;
import com.oracle.graal.nodes.spi.Replacements;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.OptimisticOptimizations.Optimization;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;

import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.TriState;
import jdk.vm.ci.runtime.JVMCICompiler;

public class HotSpotGraalCompiler implements GraalJVMCICompiler {

    private final HotSpotJVMCIRuntimeProvider jvmciRuntime;
    private final HotSpotGraalRuntimeProvider graalRuntime;
    private final CompilationCounters compilationCounters;
    private final BootstrapWatchDog bootstrapWatchDog;

    HotSpotGraalCompiler(HotSpotJVMCIRuntimeProvider jvmciRuntime, HotSpotGraalRuntimeProvider graalRuntime) {
        this.jvmciRuntime = jvmciRuntime;
        this.graalRuntime = graalRuntime;
        // It is sufficient to have one compilation counter object per Graal compiler object.
        this.compilationCounters = Options.CompilationCountLimit.getValue() > 0 ? new CompilationCounters() : null;
        this.bootstrapWatchDog = graalRuntime.isBootstrapping() ? BootstrapWatchDog.maybeCreate(graalRuntime) : null;
    }

    @Override
    public HotSpotGraalRuntimeProvider getGraalRuntime() {
        return graalRuntime;
    }

    @Override
    @SuppressWarnings("try")
    public CompilationRequestResult compileMethod(CompilationRequest request) {
        if (bootstrapWatchDog != null && graalRuntime.isBootstrapping()) {
            if (bootstrapWatchDog.hitCriticalCompilationRateOrTimeout()) {
                // Drain the compilation queue to expedite completion of the bootstrap
                return HotSpotCompilationRequestResult.failure("hit critical bootstrap compilation rate or timeout", true);
            }
        }
        ResolvedJavaMethod method = request.getMethod();
        HotSpotCompilationRequest hsRequest = (HotSpotCompilationRequest) request;
        try (CompilationWatchDog w1 = CompilationWatchDog.watch(method, hsRequest.getId());
                        BootstrapWatchDog.Watch w2 = bootstrapWatchDog == null ? null : bootstrapWatchDog.watch(request);
                        CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod();) {
            if (compilationCounters != null) {
                compilationCounters.countCompilation(method);
            }
            // Ensure a debug configuration for this thread is initialized
            if (Debug.isEnabled() && DebugScope.getConfig() == null) {
                DebugEnvironment.initialize(TTY.out, graalRuntime.getHostProviders().getSnippetReflection());
            }
            CompilationTask task = new CompilationTask(jvmciRuntime, this, hsRequest, true, true);
            CompilationRequestResult r = null;
            try (DebugConfigScope dcs = Debug.setConfig(new TopLevelDebugConfig());
                            Debug.Scope s = Debug.methodMetricsScope("HotSpotGraalCompiler", MethodMetricsRootScopeInfo.create(method), true, method)) {
                r = task.runCompilation();
            }
            assert r != null;
            return r;
        }
    }

    public void compileTheWorld() throws Throwable {
        HotSpotCodeCacheProvider codeCache = (HotSpotCodeCacheProvider) jvmciRuntime.getHostJVMCIBackend().getCodeCache();
        int iterations = CompileTheWorldOptions.CompileTheWorldIterations.getValue();
        for (int i = 0; i < iterations; i++) {
            codeCache.resetCompilationStatistics();
            TTY.println("CompileTheWorld : iteration " + i);
            CompileTheWorld ctw = new CompileTheWorld(jvmciRuntime, this);
            ctw.compile();
        }
        System.exit(0);
    }

    public CompilationResult compile(ResolvedJavaMethod method, int entryBCI, boolean useProfilingInfo, CompilationIdentifier compilationId) {
        HotSpotBackend backend = graalRuntime.getHostBackend();
        HotSpotProviders providers = backend.getProviders();
        final boolean isOSR = entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;
        StructuredGraph graph = method.isNative() || isOSR ? null : getIntrinsicGraph(method, providers, compilationId);

        if (graph == null) {
            SpeculationLog speculationLog = method.getSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }
            graph = new StructuredGraph(method, entryBCI, AllowAssumptions.from(OptAssumptions.getValue()), speculationLog, useProfilingInfo, compilationId);
        }

        Suites suites = getSuites(providers);
        LIRSuites lirSuites = getLIRSuites(providers);
        ProfilingInfo profilingInfo = useProfilingInfo ? method.getProfilingInfo(!isOSR, isOSR) : DefaultProfilingInfo.get(TriState.FALSE);
        OptimisticOptimizations optimisticOpts = getOptimisticOpts(profilingInfo);
        if (isOSR) {
            // In OSR compiles, we cannot rely on never executed code profiles, because
            // all code after the OSR loop is never executed.
            optimisticOpts.remove(Optimization.RemoveNeverExecutedCode);
        }
        CompilationResult result = new CompilationResult();
        result.setEntryBCI(entryBCI);
        boolean shouldDebugNonSafepoints = providers.getCodeCache().shouldDebugNonSafepoints();
        PhaseSuite<HighTierContext> graphBuilderSuite = configGraphBuilderSuite(providers.getSuites().getDefaultGraphBuilderSuite(), shouldDebugNonSafepoints, isOSR);
        GraalCompiler.compileGraph(graph, method, providers, backend, graphBuilderSuite, optimisticOpts, profilingInfo, suites, lirSuites, result, CompilationResultBuilderFactory.Default);

        if (!isOSR && useProfilingInfo) {
            ProfilingInfo profile = profilingInfo;
            profile.setCompilerIRSize(StructuredGraph.class, graph.getNodeCount());
        }

        return result;
    }

    /**
     * Gets a graph produced from the intrinsic for a given method that can be compiled and
     * installed for the method.
     *
     * @param method
     * @param compilationId
     * @return an intrinsic graph that can be compiled and installed for {@code method} or null
     */
    @SuppressWarnings("try")
    public StructuredGraph getIntrinsicGraph(ResolvedJavaMethod method, HotSpotProviders providers, CompilationIdentifier compilationId) {
        Replacements replacements = providers.getReplacements();
        ResolvedJavaMethod substMethod = replacements.getSubstitutionMethod(method);
        if (substMethod != null) {
            assert !substMethod.equals(method);
            StructuredGraph graph = new StructuredGraph(substMethod, AllowAssumptions.YES, NO_PROFILING_INFO, compilationId);
            try (Debug.Scope scope = Debug.scope("GetIntrinsicGraph", graph)) {
                Plugins plugins = new Plugins(providers.getGraphBuilderPlugins());
                GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
                IntrinsicContext initialReplacementContext = new IntrinsicContext(method, substMethod, replacements.getReplacementBytecodeProvider(), ROOT_COMPILATION);
                new GraphBuilderPhase.Instance(providers.getMetaAccess(), providers.getStampProvider(), providers.getConstantReflection(), providers.getConstantFieldProvider(), config,
                                OptimisticOptimizations.NONE, initialReplacementContext).apply(graph);
                assert !graph.isFrozen();
                return graph;
            } catch (Throwable e) {
                Debug.handle(e);
            }
        }
        return null;
    }

    protected OptimisticOptimizations getOptimisticOpts(ProfilingInfo profilingInfo) {
        return new OptimisticOptimizations(profilingInfo);
    }

    protected Suites getSuites(HotSpotProviders providers) {
        return providers.getSuites().getDefaultSuites();
    }

    protected LIRSuites getLIRSuites(HotSpotProviders providers) {
        return providers.getSuites().getDefaultLIRSuites();
    }

    /**
     * Reconfigures a given graph builder suite (GBS) if one of the given GBS parameter values is
     * not the default.
     *
     * @param suite the graph builder suite
     * @param shouldDebugNonSafepoints specifies if extra debug info should be generated (default is
     *            false)
     * @param isOSR specifies if extra OSR-specific post-processing is required (default is false)
     * @return a new suite derived from {@code suite} if any of the GBS parameters did not have a
     *         default value otherwise {@code suite}
     */
    protected PhaseSuite<HighTierContext> configGraphBuilderSuite(PhaseSuite<HighTierContext> suite, boolean shouldDebugNonSafepoints, boolean isOSR) {
        if (shouldDebugNonSafepoints || isOSR) {
            PhaseSuite<HighTierContext> newGbs = suite.copy();

            if (shouldDebugNonSafepoints) {
                GraphBuilderPhase graphBuilderPhase = (GraphBuilderPhase) newGbs.findPhase(GraphBuilderPhase.class).previous();
                GraphBuilderConfiguration graphBuilderConfig = graphBuilderPhase.getGraphBuilderConfig();
                graphBuilderConfig = graphBuilderConfig.withNodeSourcePosition(true);
                GraphBuilderPhase newGraphBuilderPhase = new GraphBuilderPhase(graphBuilderConfig);
                newGbs.findPhase(GraphBuilderPhase.class).set(newGraphBuilderPhase);
            }
            if (isOSR) {
                newGbs.appendPhase(new OnStackReplacementPhase());
            }
            return newGbs;
        }
        return suite;
    }

    /**
     * Converts {@code method} to a String with {@link JavaMethod#format(String)} and the format
     * string {@code "%H.%n(%p)"}.
     */
    static String str(JavaMethod method) {
        return method.format("%H.%n(%p)");
    }

    /**
     * Wraps {@code obj} in a {@link Formatter} that standardizes formatting for certain objects.
     */
    static Formattable fmt(Object obj) {
        return new Formattable() {
            @Override
            public void formatTo(Formatter buf, int flags, int width, int precision) {
                if (obj instanceof Throwable) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ((Throwable) obj).printStackTrace(new PrintStream(baos));
                    buf.format("%s", baos.toString());
                } else if (obj instanceof StackTraceElement[]) {
                    for (StackTraceElement e : (StackTraceElement[]) obj) {
                        buf.format("\t%s%n", e);
                    }
                } else if (obj instanceof JavaMethod) {
                    buf.format("%s", str((JavaMethod) obj));
                } else {
                    buf.format("%s", obj);
                }
            }
        };
    }
}
