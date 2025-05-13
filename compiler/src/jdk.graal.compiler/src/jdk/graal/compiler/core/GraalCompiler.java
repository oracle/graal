/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.RetryableBailoutException;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.CompilerPhaseScope;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.GraphFilter;
import jdk.graal.compiler.debug.MemUseTrackerKey;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.asm.EntryPointDecorator;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.tiers.TargetProvider;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Static methods for orchestrating the compilation of a {@linkplain StructuredGraph graph}.
 */
public class GraalCompiler {

    private static final TimerKey CompilerTimer = DebugContext.timer("GraalCompiler").doc("Time spent in compilation (excludes code installation).");
    private static final MemUseTrackerKey CompilerMemory = DebugContext.memUseTracker("GraalCompiler");
    private static final TimerKey FrontEnd = DebugContext.timer("FrontEnd").doc("Time spent processing HIR.");

    /**
     * Encapsulates all the inputs to a {@linkplain GraalCompiler#compile(Request) compilation}.
     *
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
    public record Request<T extends CompilationResult>(StructuredGraph graph,
                    ResolvedJavaMethod installedCodeOwner,
                    Providers providers,
                    Backend backend,
                    PhaseSuite<HighTierContext> graphBuilderSuite,
                    OptimisticOptimizations optimisticOpts,
                    ProfilingInfo profilingInfo,
                    Suites suites,
                    LIRSuites lirSuites,
                    T compilationResult,
                    CompilationResultBuilderFactory factory,
                    EntryPointDecorator entryPointDecorator,
                    RequestedCrashHandler requestedCrashHandler,
                    boolean verifySourcePositions) {

        public Request(StructuredGraph graph,
                        ResolvedJavaMethod installedCodeOwner,
                        Providers providers,
                        Backend backend,
                        PhaseSuite<HighTierContext> graphBuilderSuite,
                        OptimisticOptimizations optimisticOpts,
                        ProfilingInfo profilingInfo,
                        Suites suites,
                        LIRSuites lirSuites,
                        T compilationResult,
                        CompilationResultBuilderFactory factory,
                        boolean verifySourcePositions) {
            this(graph,
                            installedCodeOwner,
                            providers,
                            backend,
                            graphBuilderSuite,
                            optimisticOpts,
                            profilingInfo,
                            suites,
                            lirSuites,
                            compilationResult,
                            factory,
                            null,
                            null,
                            verifySourcePositions);
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
     * Services a given compilation request.
     *
     * @return the result of the compilation
     */
    @SuppressWarnings("try")
    public static <T extends CompilationResult> T compile(Request<T> r) {
        DebugContext debug = r.graph.getDebug();
        try (CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(r.graph.getOptions())) {
            assert !r.graph.isFrozen();
            try (DebugContext.Scope s0 = debug.scope("GraalCompiler", r.graph, r.providers.getCodeCache());
                            DebugCloseable a = CompilerTimer.start(debug);
                            DebugCloseable b = CompilerMemory.start(debug)) {
                emitFrontEnd(r.providers, r.backend, r.graph, r.graphBuilderSuite, r.optimisticOpts, r.profilingInfo, r.suites);
                r.backend.emitBackEnd(r.graph, null, r.installedCodeOwner, r.compilationResult, r.factory, r.entryPointDecorator, null, r.lirSuites);
                assert !r.verifySourcePositions || r.graph.verifySourcePositions(true);
                checkForRequestedCrash(r.graph, r.requestedCrashHandler());
            } catch (Throwable e) {
                throw debug.handle(e);
            }
            checkForRequestedDelay(r.graph);

            checkForHeapDump(r, debug);

            return r.compilationResult;
        }
    }

    /**
     * Checks if {@link GraalCompilerOptions#DumpHeapAfter} is enabled for the compilation in
     * {@code request} and if so, dumps the heap to a file specified by the debug context.
     */
    private static <T extends CompilationResult> void checkForHeapDump(Request<T> request, DebugContext debug) {
        if (GraalCompilerOptions.DumpHeapAfter.matches(debug.getOptions(), null, request.graph)) {
            try {
                final String path = debug.getDumpPath(".compilation.hprof", false);
                GraalServices.dumpHeap(path, false);
            } catch (IOException | UnsupportedOperationException e) {
                e.printStackTrace(System.out);
            }
        }
    }

    /**
     * Support for extra processing of a crash triggered by {@link GraalCompilerOptions#CrashAt}.
     */
    public interface RequestedCrashHandler {
        /**
         * @return true if the caller should proceed to throw an exception
         */
        @SuppressWarnings("unused")
        boolean notifyCrash(OptionValues options, String crashMessage);
    }

    /**
     * Checks whether the {@link GraalCompilerOptions#CrashAt} option indicates that the compilation
     * of {@code graph} should result in an exception.
     *
     * @param graph a graph currently being compiled
     * @param requestedCrashHandler
     * @throws RuntimeException if the value of {@link GraalCompilerOptions#CrashAt} matches
     *             {@code graph.method()} or {@code graph.name}
     */
    private static void checkForRequestedCrash(StructuredGraph graph, RequestedCrashHandler requestedCrashHandler) {
        String value = GraalCompilerOptions.CrashAt.getValue(graph.getOptions());
        if (value != null) {
            boolean bailout = false;
            boolean permanentBailout = false;
            String methodPattern = value;
            if (value.endsWith(":Bailout")) {
                methodPattern = value.substring(0, value.length() - ":Bailout".length());
                bailout = true;
            } else if (value.endsWith(":PermanentBailout")) {
                methodPattern = value.substring(0, value.length() - ":PermanentBailout".length());
                permanentBailout = true;
            }
            String matchedLabel = match(graph, methodPattern);
            if (matchedLabel != null) {
                String crashMessage = "Forced crash after compiling " + matchedLabel;
                if (requestedCrashHandler == null || requestedCrashHandler.notifyCrash(graph.getOptions(), crashMessage)) {
                    if (permanentBailout) {
                        throw new PermanentBailoutException(crashMessage);
                    }
                    if (bailout) {
                        throw new RetryableBailoutException(crashMessage);
                    }
                    throw new RuntimeException(crashMessage);
                }
            }
        }
    }

    /**
     * Checks whether the {@link GraalCompilerOptions#InjectedCompilationDelay} option indicates
     * that the compilation of {@code graph} should be delayed.
     *
     * @param graph a graph currently being compiled
     */
    private static void checkForRequestedDelay(StructuredGraph graph) {
        long delayNS = Math.max(0, TimeUnit.SECONDS.toNanos(GraalCompilerOptions.InjectedCompilationDelay.getValue(graph.getOptions())));
        if (delayNS != 0) {
            String methodPattern = DebugOptions.MethodFilter.getValue(graph.getOptions());
            String matchedLabel = match(graph, methodPattern);
            if (matchedLabel != null) {
                long startNS = System.nanoTime();
                TTY.printf("[%s] delaying compilation of %s for %d ms%n", Thread.currentThread().getName(), matchedLabel, TimeUnit.NANOSECONDS.toMillis(delayNS));
                while (System.nanoTime() - startNS < delayNS) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    private static String match(StructuredGraph graph, String methodPattern) {
        if (methodPattern == null) {
            // Absence of methodPattern means match everything
            return graph.name != null ? graph.name : graph.method().format("%H.%n(%p)");
        }
        return new GraphFilter(methodPattern).matchedLabel(graph);
    }

    /**
     * Builds the graph, optimizes it.
     */
    @SuppressWarnings("try")
    public static void emitFrontEnd(Providers providers, TargetProvider target, StructuredGraph graph, PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts,
                    ProfilingInfo profilingInfo, Suites suites) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("FrontEnd"); DebugCloseable a = FrontEnd.start(debug)) {
            HighTierContext highTierContext = new HighTierContext(providers, graphBuilderSuite, optimisticOpts);
            if (graph.start().next() == null) {
                try (CompilerPhaseScope cps = debug.enterCompilerPhase("Parsing", graph)) {
                    graphBuilderSuite.apply(graph, highTierContext);
                    new DeadCodeEliminationPhase(DeadCodeEliminationPhase.Optionality.Optional).apply(graph);
                    assert graph.verifySourcePositions(true);
                    debug.dump(DebugContext.BASIC_LEVEL, graph, "After parsing");
                }
            } else {
                debug.dump(DebugContext.INFO_LEVEL, graph, "initial state");
            }

            suites.getHighTier().apply(graph, highTierContext);
            graph.maybeCompress();
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After high tier");

            MidTierContext midTierContext = new MidTierContext(providers, target, optimisticOpts, profilingInfo);
            suites.getMidTier().apply(graph, midTierContext);
            graph.maybeCompress();
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After mid tier");

            LowTierContext lowTierContext = new LowTierContext(providers, target);
            suites.getLowTier().apply(graph, lowTierContext);
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After low tier");

            debug.dump(DebugContext.BASIC_LEVEL, graph.getLastSchedule(), "Final HIR schedule");
            graph.logInliningTree();
        } catch (Throwable e) {
            throw debug.handle(e);
        } finally {
            graph.checkCancellation();
        }
    }
}
