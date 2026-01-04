/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import static jdk.graal.compiler.core.GraalCompilerOptions.CompilationFailureAction;
import static jdk.graal.compiler.core.common.GraalOptions.OptAssumptions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;

import jdk.graal.compiler.api.runtime.GraalJVMCICompiler;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.CompilationWatchDog;
import jdk.graal.compiler.core.CompilationWrapper;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Activation;
import jdk.graal.compiler.debug.DebugDumpHandlersFactory;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime.HotSpotGC;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.phases.OnStackReplacementPhase;
import jdk.graal.compiler.hotspot.phases.VerifyLockDepthPhase;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.Cancellable;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.spi.ProfileProvider;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.OptimisticOptimizations.Optimization;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.ForceDeoptSpeculationPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.internal.misc.Unsafe;
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

public class HotSpotGraalCompiler implements GraalJVMCICompiler, Cancellable, JVMCICompilerShadow, GraalCompiler.RequestedCrashHandler {

    public static final class Options {
        @Option(help = "If non-zero, converts an exception triggered by the CrashAt option into a fatal error " +
                        "if a non-null pointer was passed in the _fatal option to JNI_CreateJavaVM. " +
                        "The value of this option is the number of milliseconds to sleep before calling _fatal. " +
                        "This option exists for the purpose of testing fatal error handling in libgraal.") //
        public static final OptionKey<Integer> CrashAtIsFatal = new OptionKey<>(0);
        @Option(help = "The fully qualified name of a no-arg, void, static method to be invoked " +
                        "in HotSpot when a HotSpotGraalRuntime is being shutdown." +
                        "This option exists for the purpose of testing callbacks in this context.") //
        public static final OptionKey<String> OnShutdownCallback = new OptionKey<>(null);
        @Option(help = "Replaces first exception thrown by the CrashAt option with an OutOfMemoryError. " +
                        "Subsequently CrashAt exceptions are suppressed. " +
                        "This option exists to test HeapDumpOnOutOfMemoryError. " +
                        "See the MethodFilter option for the pattern syntax.") //
        public static final OptionKey<Boolean> CrashAtThrowsOOME = new OptionKey<>(false);
    }

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private final HotSpotJVMCIRuntime jvmciRuntime;
    private final HotSpotGraalRuntimeProvider graalRuntime;
    private final CompilationCounters compilationCounters;
    private final BootstrapWatchDog bootstrapWatchDog;
    private List<DebugDumpHandlersFactory> factories;

    HotSpotGraalCompiler(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider graalRuntime, OptionValues options) {
        this.jvmciRuntime = jvmciRuntime;
        this.graalRuntime = graalRuntime;
        // It is sufficient to have one compilation counter object per compiler object.
        this.compilationCounters = CompilationCounters.Options.CompilationCountLimit.getValue(options) > 0 ? new CompilationCounters(options) : null;
        this.bootstrapWatchDog = graalRuntime.isBootstrapping() && !DebugOptions.BootstrapInitializeOnly.getValue(options) ? BootstrapWatchDog.maybeCreate(graalRuntime) : null;
    }

    public List<DebugDumpHandlersFactory> getDebugHandlersFactories() {
        if (factories == null) {
            factories = Collections.singletonList(new GraalDebugHandlersFactory(graalRuntime.getHostProviders().getSnippetReflection()));
        }
        return factories;
    }

    @Override
    public HotSpotGraalRuntimeProvider getGraalRuntime() {
        return graalRuntime;
    }

    @SuppressWarnings("try")
    @Override
    public CompilationRequestResult compileMethod(CompilationRequest request) {
        LibGraalSupport libgraal = LibGraalSupport.INSTANCE;
        try (AutoCloseable ignored = libgraal != null ? libgraal.openCompilationRequestScope() : null) {
            return compileMethod(request, true, getGraalRuntime().getOptions());
        } catch (Exception e) {
            return HotSpotCompilationRequestResult.failure(e.toString(), false);
        }
    }

    @SuppressWarnings("try")
    public CompilationRequestResult compileMethod(CompilationRequest request, boolean installAsDefault, OptionValues initialOptions) {
        try (CompilationContext scope = HotSpotGraalServices.openLocalCompilationContext(request)) {
            if (graalRuntime.isShutdown()) {
                return HotSpotCompilationRequestResult.failure("Shutdown entered", true);
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
            CompilationTask task = new CompilationTask(jvmciRuntime, this, hsRequest, true, shouldRetainLocalVariables(hsRequest.getJvmciEnv()), shouldUsePreciseUnresolvedDeopts(), installAsDefault);
            OptionValues options = task.filterOptions(initialOptions);
            int decompileCount = HotSpotGraalServices.getDecompileCount(task.getMethod());
            if (decompileCount != -1) {
                if (CompilationTask.Options.MethodRecompilationLimit.getValue(options) >= 0 && decompileCount >= CompilationTask.Options.MethodRecompilationLimit.getValue(options)) {
                    if (CompilationFailureAction.getValue(options) == CompilationWrapper.ExceptionAction.Diagnose) {
                        // If Diagnose is enabled then allow the compile to proceed and throw an
                        // exception afterwards to allow the retry machinery to capture a graph.
                        task.checkRecompileCycle = true;
                    } else {
                        // Treat this as a permanent bailout. This is similar to HotSpots
                        // PerMethodRecompilationCutoff flag but since it's under our control we can
                        // produce more useful diagnostics. The default HotSpot limit of 400 is
                        // probably too large as well.
                        ProfilingInfo info = task.getProfileProvider().getProfilingInfo(request.getMethod());
                        return HotSpotCompilationRequestResult.failure("too many decompiles: " + decompileCount + " " + ForceDeoptSpeculationPhase.getDeoptSummary(info), false);
                    }

                } else if (CompilationTask.Options.DetectRecompilationLimit.getValue(options) >= 0 &&
                                decompileCount >= CompilationTask.Options.DetectRecompilationLimit.getValue(options)) {
                    task.checkRecompileCycle = true;
                }
            }

            HotSpotVMConfigAccess config = new HotSpotVMConfigAccess(graalRuntime.getVMConfig().getStore());
            LibGraalSupport libgraal = LibGraalSupport.INSTANCE;
            boolean oneIsolatePerCompilation = libgraal != null &&
                            config.getFlag("JVMCIThreadsPerNativeLibraryRuntime", Integer.class, 0) == 1 &&
                            config.getFlag("JVMCICompilerIdleDelay", Integer.class, 1000) == 0;
            ThreadFactory factory = libgraal != null ? HotSpotGraalServiceThread::new : null;
            try (CompilationWatchDog w1 = CompilationWatchDog.watch(task.getCompilationIdentifier(), options, oneIsolatePerCompilation, task, factory);
                            BootstrapWatchDog.Watch w2 = bootstrapWatchDog == null ? null : bootstrapWatchDog.watch(request);
                            CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(options);) {
                if (compilationCounters != null) {
                    compilationCounters.countCompilation(method);
                }
                try (DebugContext debug = graalRuntime.openDebugContext(options, task.getCompilationIdentifier(), method, getDebugHandlersFactories(), DebugContext.getDefaultLogStream());
                                Activation a = debug.activate()) {
                    return Objects.requireNonNull(task.runCompilation(debug));
                }
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

    private boolean shouldUsePreciseUnresolvedDeopts() {
        GraalHotSpotVMConfig config = graalRuntime.getVMConfig();
        /*
         * When running with -Xcomp, use precise deopts for unresolved elements. This is necessary
         * to avoid deopt loops: An imprecise deopt could cause us to deoptimize even if the path
         * using the unresolved element is not executed. As -Xcomp forces compilations, this would
         * cause us to recompile the method in the same way, so we would trigger the same imprecise
         * deopt again.
         *
         * Without -Xcomp we rely on normal warmup in the interpreter to resolve unresolved items
         * and prune never executed paths, and we use speculations for additional protection against
         * deopt loops.
         */
        return config.xcompMode;
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
    public CompilationResult compileHelper(CompilationResultBuilderFactory crbf, CompilationResult result, StructuredGraph graph, boolean shouldRetainLocalVariables,
                    boolean shouldUsePreciseUnresolvedDeopts, boolean eagerResolving, Suites suites, OptionValues options) {
        int entryBCI = graph.getEntryBCI();
        ResolvedJavaMethod method = graph.method();
        assert options == graph.getOptions() : Assertions.errorMessage(options, graph.getOptions());
        HotSpotBackend backend = graalRuntime.getHostBackend();
        HotSpotProviders providers = backend.getProviders();
        final boolean isOSR = entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;

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
                        shouldUsePreciseUnresolvedDeopts, eagerResolving, isOSR);

        GraalCompiler.compile(new GraalCompiler.Request<>(graph,
                        method,
                        providers,
                        backend,
                        graphBuilderSuite,
                        optimisticOpts,
                        profilingInfo,
                        suites,
                        lirSuites,
                        result,
                        crbf,
                        null,
                        this,
                        true));
        graph.getOptimizationLog().emit();
        if (!isOSR) {
            profilingInfo.setCompilerIRSize(StructuredGraph.class, graph.getNodeCount());
        }

        return result;
    }

    public CompilationResult compile(StructuredGraph graph,
                    boolean shouldRetainLocalVariables,
                    boolean shouldUsePreciseUnresolvedDeopts,
                    boolean eagerResolving,
                    CompilationIdentifier compilationId,
                    DebugContext debug,
                    Suites suites) {
        CompilationResult result = new CompilationResult(compilationId);
        return compileHelper(CompilationResultBuilderFactory.Default, result, graph, shouldRetainLocalVariables, shouldUsePreciseUnresolvedDeopts, eagerResolving, suites, debug.getOptions());
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
     * @param shouldUsePreciseUnresolvedDeopts specifies if Unresolved deoptimizations should use
     *            precise frame states (default is false, except in Xcomp mode)
     * @param isOSR specifies if extra OSR-specific post-processing is required (default is false)
     * @return a new suite derived from {@code suite} if any of the GBS parameters did not have a
     *         default value otherwise {@code suite}
     */
    protected PhaseSuite<HighTierContext> configGraphBuilderSuite(PhaseSuite<HighTierContext> suite, boolean shouldDebugNonSafepoints, boolean shouldRetainLocalVariables,
                    boolean shouldUsePreciseUnresolvedDeopts, boolean eagerResolving, boolean isOSR) {
        PhaseSuite<HighTierContext> newGbs = suite.copy();
        GraphBuilderPhase graphBuilderPhase = (GraphBuilderPhase) newGbs.findPhase(GraphBuilderPhase.class).previous();
        GraphBuilderConfiguration graphBuilderConfig = graphBuilderPhase.getGraphBuilderConfig();
        if (shouldDebugNonSafepoints) {
            graphBuilderConfig = graphBuilderConfig.withNodeSourcePosition(true);
        }
        if (shouldRetainLocalVariables) {
            graphBuilderConfig = graphBuilderConfig.withRetainLocalVariables(true);
        }
        if (shouldUsePreciseUnresolvedDeopts) {
            graphBuilderConfig = graphBuilderConfig.withUsePreciseUnresolvedDeopts(true);
        }
        if (eagerResolving) {
            graphBuilderConfig = graphBuilderConfig.withEagerResolving(true);
            graphBuilderConfig = graphBuilderConfig.withUnresolvedIsError(true);
        }
        if (graalRuntime.getVMConfig().alwaysSafeConstructors) {
            graphBuilderConfig = graphBuilderConfig.withAlwaysSafeConstructors();
        }
        GraphBuilderPhase newGraphBuilderPhase = new HotSpotGraphBuilderPhase(graphBuilderConfig);
        newGbs.findPhase(GraphBuilderPhase.class).set(newGraphBuilderPhase);
        if (Assertions.assertionsEnabled()) {
            newGbs.appendPhase(new VerifyLockDepthPhase());
        }
        if (isOSR) {
            newGbs.appendPhase(new OnStackReplacementPhase());
        }
        return newGbs;
    }

    @Override
    public boolean isGCSupported(int gcIdentifier) {
        HotSpotGC gc = HotSpotGC.forName(gcIdentifier, graalRuntime.getVMConfig());
        if (gc != null) {
            return gc.supported;
        }
        return false;
    }

    // Support for CrashAtThrowsOOME
    private static final GlobalAtomicLong OOME_CRASH_DONE = new GlobalAtomicLong("OOME_CRASH_DONE", 0);

    @Override
    public boolean notifyCrash(OptionValues options, String crashMessage) {
        LibGraalSupport libgraal = LibGraalSupport.INSTANCE;
        if (libgraal != null) {
            if (HotSpotGraalCompiler.Options.CrashAtThrowsOOME.getValue(options)) {
                if (OOME_CRASH_DONE.compareAndSet(0L, 1L)) {
                    // The -Djdk.libgraal.Xmx option should also be employed to make
                    // this allocation fail quicky
                    String largeString = Arrays.toString(new int[Integer.MAX_VALUE - 1]);
                    throw new InternalError("Failed to trigger OOME: largeString.length=" + largeString.length());
                } else {
                    // Remaining compilations should proceed so that test finishes quickly.
                    return false;
                }
            } else {
                int crashAtIsFatal = HotSpotGraalCompiler.Options.CrashAtIsFatal.getValue(options);
                if (crashAtIsFatal != 0) {
                    try {
                        Thread.sleep(crashAtIsFatal);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    libgraal.fatalError(crashMessage);

                    // If changing this message, update the test for it in mx_vm_gate.py
                    System.out.println("CrashAtIsFatal: no fatalError function pointer installed");
                }
            }
        }
        return true;
    }
}
