/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot;

import static jdk.compiler.graal.core.common.GraalOptions.OptAssumptions;

import java.util.Collections;
import java.util.List;

import jdk.compiler.graal.api.runtime.GraalJVMCICompiler;
import jdk.compiler.graal.code.CompilationResult;
import jdk.compiler.graal.core.CompilationWatchDog;
import jdk.compiler.graal.core.GraalCompiler;
import jdk.compiler.graal.core.common.CompilationIdentifier;
import jdk.compiler.graal.core.common.util.CompilationAlarm;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.debug.DebugContext.Activation;
import jdk.compiler.graal.debug.DebugHandlersFactory;
import jdk.compiler.graal.debug.DebugOptions;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.HotSpotGraalRuntime.HotSpotGC;
import jdk.compiler.graal.hotspot.phases.OnStackReplacementPhase;
import jdk.compiler.graal.java.GraphBuilderPhase;
import jdk.compiler.graal.java.StableMethodNameFormatter;
import jdk.compiler.graal.lir.asm.CompilationResultBuilderFactory;
import jdk.compiler.graal.lir.phases.LIRSuites;
import jdk.compiler.graal.nodes.Cancellable;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.StructuredGraph.AllowAssumptions;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.compiler.graal.nodes.spi.ProfileProvider;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.phases.OptimisticOptimizations;
import jdk.compiler.graal.phases.OptimisticOptimizations.Optimization;
import jdk.compiler.graal.phases.PhaseSuite;
import jdk.compiler.graal.phases.tiers.HighTierContext;
import jdk.compiler.graal.phases.tiers.Suites;
import jdk.compiler.graal.printer.GraalDebugHandlersFactory;
import jdk.compiler.graal.serviceprovider.GraalUnsafeAccess;

import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.TriState;
import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.services.Services;
import sun.misc.Unsafe;

public class HotSpotGraalCompiler implements GraalJVMCICompiler, Cancellable, JVMCICompilerShadow {

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();
    private final HotSpotJVMCIRuntime jvmciRuntime;
    private final HotSpotGraalRuntimeProvider graalRuntime;
    private final CompilationCounters compilationCounters;
    private final BootstrapWatchDog bootstrapWatchDog;
    private List<DebugHandlersFactory> factories;

    HotSpotGraalCompiler(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider graalRuntime, OptionValues options) {
        this.jvmciRuntime = jvmciRuntime;
        this.graalRuntime = graalRuntime;
        // It is sufficient to have one compilation counter object per compiler object.
        this.compilationCounters = CompilationCounters.Options.CompilationCountLimit.getValue(options) > 0 ? new CompilationCounters(options) : null;
        this.bootstrapWatchDog = graalRuntime.isBootstrapping() && !DebugOptions.BootstrapInitializeOnly.getValue(options) ? BootstrapWatchDog.maybeCreate(graalRuntime) : null;
    }

    public List<DebugHandlersFactory> getDebugHandlersFactories() {
        if (factories == null) {
            factories = Collections.singletonList(new GraalDebugHandlersFactory(graalRuntime.getHostProviders().getSnippetReflection()));
        }
        return factories;
    }

    @Override
    public HotSpotGraalRuntimeProvider getGraalRuntime() {
        return graalRuntime;
    }

    @Override
    public CompilationRequestResult compileMethod(CompilationRequest request) {
        return compileMethod(this, request);
    }

    /**
     * Substituted by
     * {@code com.oracle.svm.graal.hotspot.libgraal.Target_jdk_compiler_graal_hotspot_HotSpotGraalCompiler}
     * to create a {@code org.graalvm.libgraal.jni.JNILibGraalScope}.
     */
    private static CompilationRequestResult compileMethod(HotSpotGraalCompiler compiler, CompilationRequest request) {
        return compiler.compileMethod(request, true, compiler.getGraalRuntime().getOptions());
    }

    @SuppressWarnings("try")
    public CompilationRequestResult compileMethod(CompilationRequest request, boolean installAsDefault, OptionValues initialOptions) {
        try (CompilationContext scope = HotSpotGraalServices.openLocalCompilationContext(request)) {
            if (graalRuntime.isShutdown()) {
                return HotSpotCompilationRequestResult.failure(String.format("Shutdown entered"), true);
            }

            ResolvedJavaMethod method = request.getMethod();

            if (graalRuntime.isBootstrapping()) {
                if (DebugOptions.BootstrapInitializeOnly.getValue(initialOptions)) {
                    return HotSpotCompilationRequestResult.failure(String.format("Skip compilation because %s is enabled", DebugOptions.BootstrapInitializeOnly.getName()), true);
                }
                if (bootstrapWatchDog != null) {
                    if (bootstrapWatchDog.hitCriticalCompilationRateOrTimeout()) {
                        // Drain the compilation queue to expedite completion of the bootstrap
                        return HotSpotCompilationRequestResult.failure("hit critical bootstrap compilation rate or timeout", true);
                    }
                }
            }
            HotSpotCompilationRequest hsRequest = (HotSpotCompilationRequest) request;
            CompilationTask task = new CompilationTask(jvmciRuntime, this, hsRequest, true, shouldRetainLocalVariables(hsRequest.getJvmciEnv()), installAsDefault);
            OptionValues options = task.filterOptions(initialOptions);

            HotSpotVMConfigAccess config = new HotSpotVMConfigAccess(graalRuntime.getVMConfig().getStore());
            boolean oneIsolatePerCompilation = Services.IS_IN_NATIVE_IMAGE &&
                            config.getFlag("JVMCIThreadsPerNativeLibraryRuntime", Integer.class, 0) == 1 &&
                            config.getFlag("JVMCICompilerIdleDelay", Integer.class, 1000) == 0;
            try (CompilationWatchDog w1 = CompilationWatchDog.watch(task.getCompilationIdentifier(), options, oneIsolatePerCompilation, task);
                            BootstrapWatchDog.Watch w2 = bootstrapWatchDog == null ? null : bootstrapWatchDog.watch(request);
                            CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(options);) {
                if (compilationCounters != null) {
                    compilationCounters.countCompilation(method);
                }
                CompilationRequestResult r = null;
                try (DebugContext debug = graalRuntime.openDebugContext(options, task.getCompilationIdentifier(), method, getDebugHandlersFactories(), DebugContext.getDefaultLogStream());
                                Activation a = debug.activate()) {
                    r = task.runCompilation(debug);
                }
                assert r != null;
                return r;
            }
        }
    }

    private boolean shouldRetainLocalVariables(long envAddress) {
        GraalHotSpotVMConfig config = graalRuntime.getVMConfig();
        if (envAddress == 0) {
            return false;
        }
        if (config.jvmciCompileStateCanPopFrameOffset != Integer.MIN_VALUE) {
            if ((UNSAFE.getByte(envAddress + config.jvmciCompileStateCanPopFrameOffset) & 0xFF) != 0) {
                return true;
            }
        }
        if (config.jvmciCompileStateCanAccessLocalVariablesOffset != Integer.MIN_VALUE) {
            if ((UNSAFE.getByte(envAddress + config.jvmciCompileStateCanAccessLocalVariablesOffset) & 0xFF) != 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCancelled() {
        return graalRuntime.isShutdown();
    }

    public StructuredGraph createGraph(ResolvedJavaMethod method, int entryBCI, ProfileProvider profileProvider, CompilationIdentifier compilationId, OptionValues options, DebugContext debug) {
        AllowAssumptions allowAssumptions = AllowAssumptions.ifTrue(OptAssumptions.getValue(options));
        SpeculationLog speculationLog = method.getSpeculationLog();
        if (speculationLog != null) {
            speculationLog.collectFailedSpeculations();
        }
        /*
         * For methods that have plugins it would be possible to produces graphs from those plugins
         * instead of the bytecodees but it's somewhat complicated to cover all the possible cases
         * and doesn't seem worth the complexity as plugins are already processed at call sites. In
         * HotSpot plugins are just optimized implementations of the method so compiling them as
         * root methods isn't required for correctness.
         */

        // @formatter:off
        return new StructuredGraph.Builder(options, debug, allowAssumptions).
                                   method(method).
                                   cancellable(this).
                                   entryBCI(entryBCI).
                                   speculationLog(speculationLog).
                                   profileProvider(profileProvider).
                                   compilationId(compilationId).
                                   build();
        // @formatter:on
    }

    @SuppressWarnings("try")
    public CompilationResult compileHelper(CompilationResultBuilderFactory crbf, CompilationResult result, StructuredGraph graph, boolean shouldRetainLocalVariables, boolean eagerResolving,
                    OptionValues options) {
        int entryBCI = graph.getEntryBCI();
        ResolvedJavaMethod method = graph.method();
        assert options == graph.getOptions();
        HotSpotBackend backend = graalRuntime.getHostBackend();
        HotSpotProviders providers = backend.getProviders();
        final boolean isOSR = entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;

        Suites suites = getSuites(providers, options);
        LIRSuites lirSuites = getLIRSuites(providers, options);
        ProfilingInfo profilingInfo = graph.getProfileProvider() != null ? graph.getProfileProvider().getProfilingInfo(method, !isOSR, isOSR) : DefaultProfilingInfo.get(TriState.FALSE);
        OptimisticOptimizations optimisticOpts = getOptimisticOpts(profilingInfo, options);

        /*
         * Cut off never executed code profiles if there is code, e.g. after the osr loop, that is
         * never executed.
         */
        if (isOSR && !OnStackReplacementPhase.Options.DeoptAfterOSR.getValue(options)) {
            optimisticOpts.remove(Optimization.RemoveNeverExecutedCode);
        }

        result.setEntryBCI(entryBCI);
        boolean shouldDebugNonSafepoints = providers.getCodeCache().shouldDebugNonSafepoints();
        PhaseSuite<HighTierContext> graphBuilderSuite = configGraphBuilderSuite(providers.getSuites().getDefaultGraphBuilderSuite(), shouldDebugNonSafepoints, shouldRetainLocalVariables,
                        eagerResolving, isOSR);

        GraalCompiler.compileGraph(graph, method, providers, backend, graphBuilderSuite, optimisticOpts, profilingInfo, suites, lirSuites, result, crbf, true);
        graph.getOptimizationLog().emit(new StableMethodNameFormatter(providers, graph.getDebug()));
        if (!isOSR) {
            profilingInfo.setCompilerIRSize(StructuredGraph.class, graph.getNodeCount());
        }

        return result;
    }

    public CompilationResult compile(StructuredGraph graph,
                    boolean shouldRetainLocalVariables,
                    boolean eagerResolving,
                    CompilationIdentifier compilationId,
                    DebugContext debug) {
        CompilationResult result = new CompilationResult(compilationId);
        return compileHelper(CompilationResultBuilderFactory.Default, result, graph, shouldRetainLocalVariables, eagerResolving, debug.getOptions());
    }

    protected OptimisticOptimizations getOptimisticOpts(ProfilingInfo profilingInfo, OptionValues options) {
        return new OptimisticOptimizations(profilingInfo, options);
    }

    protected Suites getSuites(HotSpotProviders providers, OptionValues options) {
        return providers.getSuites().getDefaultSuites(options, providers.getLowerer().getTarget().arch);
    }

    protected LIRSuites getLIRSuites(HotSpotProviders providers, OptionValues options) {
        return providers.getSuites().getDefaultLIRSuites(options);
    }

    /**
     * Reconfigures a given graph builder suite (GBS) if one of the given GBS parameter values is
     * not the default.
     *
     * @param suite the graph builder suite
     * @param shouldDebugNonSafepoints specifies if extra debug info should be generated (default is
     *            false)
     * @param shouldRetainLocalVariables specifies if local variables should be retained for
     *            debugging purposes (default is false)
     * @param isOSR specifies if extra OSR-specific post-processing is required (default is false)
     * @return a new suite derived from {@code suite} if any of the GBS parameters did not have a
     *         default value otherwise {@code suite}
     */
    protected PhaseSuite<HighTierContext> configGraphBuilderSuite(PhaseSuite<HighTierContext> suite, boolean shouldDebugNonSafepoints, boolean shouldRetainLocalVariables, boolean eagerResolving,
                    boolean isOSR) {
        if (shouldDebugNonSafepoints || shouldRetainLocalVariables || isOSR || eagerResolving) {
            PhaseSuite<HighTierContext> newGbs = suite.copy();
            GraphBuilderPhase graphBuilderPhase = (GraphBuilderPhase) newGbs.findPhase(GraphBuilderPhase.class).previous();
            GraphBuilderConfiguration graphBuilderConfig = graphBuilderPhase.getGraphBuilderConfig();
            if (shouldDebugNonSafepoints) {
                graphBuilderConfig = graphBuilderConfig.withNodeSourcePosition(true);
            }
            if (shouldRetainLocalVariables) {
                graphBuilderConfig = graphBuilderConfig.withRetainLocalVariables(true);
            }
            if (eagerResolving) {
                graphBuilderConfig = graphBuilderConfig.withEagerResolving(true);
                graphBuilderConfig = graphBuilderConfig.withUnresolvedIsError(true);
            }
            GraphBuilderPhase newGraphBuilderPhase = new HotSpotGraphBuilderPhase(graphBuilderConfig);
            newGbs.findPhase(GraphBuilderPhase.class).set(newGraphBuilderPhase);
            if (isOSR) {
                newGbs.appendPhase(new OnStackReplacementPhase());
            }
            return newGbs;
        }
        return suite;
    }

    @Override
    public boolean isGCSupported(int gcIdentifier) {
        HotSpotGC gc = HotSpotGC.forName(gcIdentifier, graalRuntime.getVMConfig());
        if (gc != null) {
            return gc.supported;
        }
        return false;
    }

}
