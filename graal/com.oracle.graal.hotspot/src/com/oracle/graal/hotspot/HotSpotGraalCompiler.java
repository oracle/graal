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

import java.io.PrintStream;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.oracle.graal.api.runtime.GraalJVMCICompiler;
import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.GraalCompiler;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugConfigScope;
import com.oracle.graal.debug.DebugEnvironment;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.debug.TopLevelDebugConfig;
import com.oracle.graal.debug.internal.DebugScope;
import com.oracle.graal.debug.internal.method.MethodMetricsRootScopeInfo;
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
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.StableOptionValue;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.OptimisticOptimizations.Optimization;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;

import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.TriState;
import jdk.vm.ci.runtime.JVMCICompiler;

public class HotSpotGraalCompiler implements GraalJVMCICompiler {

    static class CompilationMonitoring {

        public static class Options {
            // @formatter:off
            @Option(help = "Enable Compilation counters. Compilation counters count the number of compilations for each method.", type = OptionType.Debug)
            public static final OptionValue<Boolean> EnableCompilationCounters = new StableOptionValue<>(true);
            @Option(help = "", type = OptionType.Debug)
            public static final OptionValue<Integer> CompilationCountersUpperBound = new StableOptionValue<>(64);
            @Option(help = "", type = OptionType.Debug)
            public static final OptionValue<Boolean> CompilationCountersExceededIsFatal = new StableOptionValue<>(true);
            @Option(help = "", type = OptionType.Debug)
            public static final OptionValue<Boolean> MonitorCompilerThreads = new StableOptionValue<>(true);
            @Option(help = "Kill a Compiler Thread and Exit VM if N last stack traces are the same", type = OptionType.Debug)
            public static final OptionValue<Boolean> StaleCompilerThreadsAreFatal = new StableOptionValue<>(true);
            @Option(help = "Number of equal stack traces for the compiler thread until it is killed", type = OptionType.Debug)
            public static final OptionValue<Integer> FatalNumberOfCompilerThreadStackTraces = new StableOptionValue<>(8);
            @Option(help = "Start monitoring after 2 minutes", type = OptionType.Debug)
            public static final OptionValue<Integer> WatchDogStartMonitoringTimeout = new StableOptionValue<>(30 * 1000);
            @Option(help = "Take Stack Trace Every 30s", type = OptionType.Debug)
            public static final OptionValue<Integer> WatchDogStackTraceTimeout = new StableOptionValue<>(5 * 1000);
            // @formatter:on
        }

        private static final ReentrantReadWriteLock RW_LOCK;

        static {
            if (Options.EnableCompilationCounters.getValue()) {
                RW_LOCK = new ReentrantReadWriteLock();
            } else {
                RW_LOCK = null;
            }
        }

        private static class CompilationCounters {
            private final IdentityHashMap<ResolvedJavaMethod, Integer> counters = new IdentityHashMap<>();

            void compilationStarted(CompilationRequest request) {
                final ResolvedJavaMethod method = request.getMethod();
                Integer val = null;
                try {
                    RW_LOCK.readLock().lock();
                    val = counters.get(method);
                } finally {
                    RW_LOCK.readLock().unlock();
                }
                val = val != null ? val + 1 : 1;
                try {
                    RW_LOCK.writeLock().lock();
                    counters.put(method, val);
                } finally {
                    RW_LOCK.writeLock().unlock();
                }

                if (val > Options.CompilationCountersUpperBound.getValue()) {
                    throw new CompilationCounterExceededException(method, val);
                }
            }

            void dumpCounters(PrintStream s) {
                try {
                    RW_LOCK.readLock().lock();
                    for (Map.Entry<ResolvedJavaMethod, Integer> entry : counters.entrySet()) {
                        s.printf("Method %s compiled %d times.%s", entry.getKey(), entry.getValue(), System.lineSeparator());
                    }
                } finally {
                    RW_LOCK.readLock().unlock();
                }
            }
        }

        private static class CompilationCounterExceededException extends RuntimeException {

            private static final long serialVersionUID = -5202508391961867237L;

            CompilationCounterExceededException(ResolvedJavaMethod method, int nrOfCompilations) {
                super(String.format("Method %s compiled %d times.", method, nrOfCompilations));
            }
        }

        private static class CompilationWatchDogThread extends Thread {

            private enum WatchDogState {
                /**
                 * The watchdog thread sleeps currently, either no method is currently compiled, or
                 * no method is compiled long enough to be monitored.
                 */
                SLEEPING,
                /**
                 * The watchdog thread identified a compilation that already takes long enough to be
                 * interesting. It will sleep and wake up periodically and check if the current
                 * compilation takes too long. If it takes too long it will start collecting stack
                 * traces from the compiler thread.
                 */
                WATCHING_NO_STACK_INSPECTION,
                /**
                 * The watchdog thread is fully monitoring the compiler thread. It takes stake
                 * traces periodically and sleeps again until the next period. If the number of
                 * stack traces reaches a certain upper bound and those stack traces are equal it
                 * will shut down the entire VM with an error.
                 */
                WATCHING_STACK_INSPECTION
            }

            /*
             * Methods below this sleep timeout will, mostly, not even be recognized by the
             * watchdog. The watchdog thread only wakes up each SPIN_TIMEOUT ms to check weather it
             * should change its internal state to monitoring as the same method is compiled enough
             * to be interesting.
             */
            private static final int SPIN_TIMEOUT = 500/* ms */;

            private static final boolean TRACE_WATCHDOG = true;

            private final Thread compilerThread;

            CompilationWatchDogThread(Thread compilerThread) {
                this.compilerThread = compilerThread;
                this.setName("Watch dog thread " + getId());
                this.setPriority(Thread.MAX_PRIORITY);
                this.setDaemon(true);
            }

            private volatile ResolvedJavaMethod lastSet;
            private ResolvedJavaMethod lastWatched;

            public void startCompilation(ResolvedJavaMethod newMethod) {
                TTY.println("%s is notified that compilation started for method %s", getTracePrefix(), newMethod);
                this.lastSet = newMethod;
            }

            public void stopCompilation() {
                TTY.println("%s notified that compilation is finished", getTracePrefix());
                this.lastSet = null;
            }

            private long ellapesWatchingNoStackTime;
            private long ellapsedWatchingTime;
            private int nrOfStackTraces;
            private WatchDogState state = WatchDogState.SLEEPING;

            private void sleep() {
                ellapsedWatchingTime = 0;
                ellapesWatchingNoStackTime = 0;
                nrOfStackTraces = 0;
                lastWatched = null;
                lastStackTrace = null;
                state = WatchDogState.SLEEPING;
            }

            private void watch() {
                state = WatchDogState.WATCHING_NO_STACK_INSPECTION;
            }

            private void watchStack() {
                state = WatchDogState.WATCHING_STACK_INSPECTION;
            }

            private StackTraceElement[] lastStackTrace;

            private boolean recordStackTrace(StackTraceElement[] newStackTrace) {
                if (lastStackTrace == null) {
                    lastStackTrace = newStackTrace;
                    return true;
                }
                if (!Arrays.equals(lastStackTrace, newStackTrace)) {
                    lastStackTrace = newStackTrace;
                    return false;
                }
                return true;
            }

            private static void traceWatchDog(String s, Object... args) {
                if (TRACE_WATCHDOG) {
                    TTY.println(String.format(s, args));
                }
            }

            private String getTracePrefix() {
                return "Watchdog Thread for Compiler thread [" + compilerThread.toString() + "]";
            }

            @Override
            public void run() {
                try {
                    while (true) {
                        // get a copy of the last set method
                        final ResolvedJavaMethod currentlyCompiled = lastSet;
                        if (currentlyCompiled == null) {
                            // continue sleeping, compilation is either over before starting
                            // to watch the compiler thread or no compilation at all started
                            // (now)
                            sleep();
                        } else {
                            switch (state) {
                                case SLEEPING:
                                    traceWatchDog("%s picked up a method to monitor", getTracePrefix());
                                    lastWatched = currentlyCompiled;
                                    watch();
                                    break;
                                case WATCHING_NO_STACK_INSPECTION:
                                    if (currentlyCompiled == lastWatched) {
                                        if (ellapesWatchingNoStackTime > Options.WatchDogStartMonitoringTimeout.getValue()) {
                                            // we looked at the same thread for a certain time and
                                            // it still compiles one method, now we start to take
                                            // stake traces
                                            watchStack();
                                            traceWatchDog("%s changes mode to watching with stack traces", getTracePrefix());
                                        } else {
                                            // we still compile the same method, watch until we
                                            // start
                                            // to collect stack traces
                                            traceWatchDog("%s still watching, ellapsed time watching %d", getTracePrefix(), ellapesWatchingNoStackTime);
                                            ellapesWatchingNoStackTime += SPIN_TIMEOUT;
                                        }
                                    } else {
                                        // compilation finished before we exceeded the watching
                                        // period of n minutes
                                        sleep();
                                    }
                                    break;
                                case WATCHING_STACK_INSPECTION:
                                    if (currentlyCompiled == lastWatched) {
                                        if (ellapsedWatchingTime > Options.WatchDogStackTraceTimeout.getValue()) {
                                            traceWatchDog("%s took a stack trace", getTracePrefix());
                                            boolean newStackTrace = recordStackTrace(compilerThread.getStackTrace());
                                            traceWatchDog("%s:Last stack trace was %b equal?, took %d equal stack traces", getTracePrefix(), newStackTrace, nrOfStackTraces);
                                            if (!newStackTrace) {
                                                nrOfStackTraces = 0;
                                            }
                                            nrOfStackTraces++;
                                            ellapsedWatchingTime = 0;
                                            if (Options.StaleCompilerThreadsAreFatal.getValue()) {
                                                if (nrOfStackTraces > Options.FatalNumberOfCompilerThreadStackTraces.getValue()) {
                                                    TTY.println("%s took N stack traces, which is considered fatal, we quit now", getTracePrefix());
                                                    TTY.println("======================= STACK TRACE =======================");
                                                    for (StackTraceElement e : lastStackTrace) {
                                                        TTY.println(e.toString());
                                                    }
                                                    System.exit(-1);
                                                }
                                            }
                                        } else {
                                            // we still compile the same method watch until the
                                            // stack trace timeout happens
                                            traceWatchDog("%s still watching with stack traces, ellapsedtime in watching period " + ellapsedWatchingTime, getTracePrefix());
                                            ellapsedWatchingTime += SPIN_TIMEOUT;
                                        }
                                    } else {
                                        // compilation finished before we are able to collect stack
                                        // traces
                                        sleep();
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                        Thread.sleep(SPIN_TIMEOUT);
                    }
                } catch (

                Throwable t) {
                    TTY.println("Watch dog thread encountered an exception. Shutting down.");
                    t.printStackTrace(TTY.out);
                    System.exit(-1);
                }
            }
        }

    }

    private final HotSpotJVMCIRuntimeProvider jvmciRuntime;
    private final HotSpotGraalRuntimeProvider graalRuntime;
    private final CompilationMonitoring.CompilationCounters compilationCounters;
    private static final IdentityHashMap<Thread, CompilationMonitoring.CompilationWatchDogThread> watchdogs = CompilationMonitoring.Options.MonitorCompilerThreads.getValue() ? new IdentityHashMap<>()
                    : null;

    HotSpotGraalCompiler(HotSpotJVMCIRuntimeProvider jvmciRuntime, HotSpotGraalRuntimeProvider graalRuntime) {
        this.jvmciRuntime = jvmciRuntime;
        this.graalRuntime = graalRuntime;
        /*
         * It is sufficient to have one compilation counter object per graal compiler.
         */
        if (CompilationMonitoring.Options.EnableCompilationCounters.getValue()) {
            compilationCounters = new CompilationMonitoring.CompilationCounters();
        } else {
            compilationCounters = null;
        }
    }

    @Override
    public HotSpotGraalRuntimeProvider getGraalRuntime() {
        return graalRuntime;
    }

    @Override
    @SuppressWarnings("try")
    public CompilationRequestResult compileMethod(CompilationRequest request) {

        if (CompilationMonitoring.Options.MonitorCompilerThreads.getValue()) {
            /*
             * lazily get a watch dog thread for the current compiler thread
             */
            CompilationMonitoring.CompilationWatchDogThread watchDog = watchdogs.get(Thread.currentThread());
            if (watchDog == null) {
                watchDog = new CompilationMonitoring.CompilationWatchDogThread(Thread.currentThread());
                watchdogs.put(Thread.currentThread(), watchDog);
                watchDog.start();
            }
            watchDog.startCompilation(request.getMethod());
        }

        if (CompilationMonitoring.Options.EnableCompilationCounters.getValue()) {
            assert compilationCounters != null;
            try {
                compilationCounters.compilationStarted(request);
            } catch (Throwable t) {
                if (t instanceof CompilationMonitoring.CompilationCounterExceededException) {
                    CompilationMonitoring.CompilationCounterExceededException e = (CompilationMonitoring.CompilationCounterExceededException) t;
                    if (CompilationMonitoring.Options.CompilationCountersExceededIsFatal.getValue()) {
                        TTY.println("Error: Option " + CompilationMonitoring.Options.CompilationCountersExceededIsFatal.getName() + " is enabled and method " + request.getMethod() +
                                        " was compiled too many times.");
                        e.printStackTrace(TTY.out);
                        TTY.println("==================================== Compilation Counters ====================================");
                        compilationCounters.dumpCounters(TTY.out);
                        TTY.flush();
                        System.exit(-1);
                    }
                } else {
                    GraalError.shouldNotReachHere(t.getCause());
                }
            }
        }

        // Ensure a debug configuration for this thread is initialized
        if (Debug.isEnabled() && DebugScope.getConfig() == null) {
            DebugEnvironment.initialize(TTY.out);
        }
        CompilationTask task = new CompilationTask(jvmciRuntime, this, (HotSpotCompilationRequest) request, true, true);
        CompilationRequestResult r = null;
        try (DebugConfigScope dcs = Debug.setConfig(new TopLevelDebugConfig());
                        Debug.Scope s = Debug.methodMetricsScope("HotSpotGraalCompiler", MethodMetricsRootScopeInfo.create(request.getMethod()), true, request.getMethod())) {
            r = task.runCompilation();
        }
        if (CompilationMonitoring.Options.MonitorCompilerThreads.getValue()) {
            watchdogs.get(Thread.currentThread()).stopCompilation();
        }
        return r;
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

    public CompilationResult compile(ResolvedJavaMethod method, int entryBCI, boolean useProfilingInfo) {
        HotSpotBackend backend = graalRuntime.getHostBackend();
        HotSpotProviders providers = backend.getProviders();
        final boolean isOSR = entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;
        StructuredGraph graph = method.isNative() || isOSR ? null : getIntrinsicGraph(method, providers);

        if (graph == null) {
            SpeculationLog speculationLog = method.getSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }
            graph = new StructuredGraph(method, entryBCI, AllowAssumptions.from(OptAssumptions.getValue()), speculationLog, useProfilingInfo);
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
     * @return an intrinsic graph that can be compiled and installed for {@code method} or null
     */
    protected StructuredGraph getIntrinsicGraph(ResolvedJavaMethod method, HotSpotProviders providers) {
        Replacements replacements = providers.getReplacements();
        ResolvedJavaMethod substMethod = replacements.getSubstitutionMethod(method);
        if (substMethod != null) {
            assert !substMethod.equals(method);
            StructuredGraph graph = new StructuredGraph(substMethod, AllowAssumptions.YES, NO_PROFILING_INFO);
            Plugins plugins = new Plugins(providers.getGraphBuilderPlugins());
            GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
            IntrinsicContext initialReplacementContext = new IntrinsicContext(method, substMethod, ROOT_COMPILATION);
            new GraphBuilderPhase.Instance(providers.getMetaAccess(), providers.getStampProvider(), providers.getConstantReflection(), providers.getConstantFieldProvider(), config,
                            OptimisticOptimizations.NONE, initialReplacementContext).apply(graph);
            assert !graph.isFrozen();
            return graph;
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
}
