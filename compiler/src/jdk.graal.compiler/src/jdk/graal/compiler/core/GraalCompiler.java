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

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.RetryableBailoutException;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.CompilerPhaseScope;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.MemUseTrackerKey;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.asm.EntryPointDecorator;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.tiers.TargetProvider;
import jdk.graal.compiler.phases.util.Providers;
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
        public final EntryPointDecorator entryPointDecorator;
        public final boolean verifySourcePositions;

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
                        OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, Suites suites, LIRSuites lirSuites, T compilationResult, CompilationResultBuilderFactory factory,
                        EntryPointDecorator entryPointDecorator, boolean verifySourcePositions) {
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
            this.entryPointDecorator = entryPointDecorator;
            this.verifySourcePositions = verifySourcePositions;
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
                    CompilationResultBuilderFactory factory, boolean verifySourcePositions) {
        return compile(new Request<>(graph, installedCodeOwner, providers, backend, graphBuilderSuite, optimisticOpts, profilingInfo, suites, lirSuites, compilationResult, factory, null,
                        verifySourcePositions));
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
                    CompilationResultBuilderFactory factory, EntryPointDecorator entryPointDecorator, boolean verifySourcePositions) {
        return compile(new Request<>(graph, installedCodeOwner, providers, backend, graphBuilderSuite, optimisticOpts, profilingInfo, suites, lirSuites, compilationResult, factory,
                        entryPointDecorator, verifySourcePositions));
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
                if (r.verifySourcePositions) {
                    assert r.graph.verifySourcePositions(true);
                }
                checkForRequestedCrash(r.graph);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
            checkForRequestedDelay(r.graph);
            return r.compilationResult;
        }
    }

    /**
     * Checks whether the {@link GraalCompilerOptions#CrashAt} option indicates that the compilation
     * of {@code graph} should result in an exception.
     *
     * @param graph a graph currently being compiled
     * @throws RuntimeException if the value of {@link GraalCompilerOptions#CrashAt} matches
     *             {@code graph.method()} or {@code graph.name}
     */
    private static void checkForRequestedCrash(StructuredGraph graph) {
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
                if (notifyCrash(crashMessage)) {
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
        long delay = Math.max(0, GraalCompilerOptions.InjectedCompilationDelay.getValue(graph.getOptions())) * 1000;
        if (delay != 0) {
            String methodPattern = DebugOptions.MethodFilter.getValue(graph.getOptions());
            String matchedLabel = match(graph, methodPattern);
            if (matchedLabel != null) {
                long start = System.currentTimeMillis();
                TTY.printf("[%s] delaying compilation of %s for %d ms%n", Thread.currentThread().getName(), matchedLabel, delay);
                while (System.currentTimeMillis() - start < delay) {
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
        String label = null;
        if (graph.name != null && graph.name.contains(methodPattern)) {
            label = graph.name;
        }
        if (label == null) {
            ResolvedJavaMethod method = graph.method();
            MethodFilter filter = MethodFilter.parse(methodPattern);
            if (filter.matches(method)) {
                label = method.format("%H.%n(%p)");
            }
        }
        return label;
    }

    /**
     * Substituted by {@code com.oracle.svm.graal.hotspot.libgraal.
     * Target_jdk_graal_compiler_core_GraalCompiler} to optionally test routing fatal error handling
     * from libgraal to HotSpot.
     *
     * @return true if the caller should proceed to throw an exception
     */
    @SuppressWarnings("unused")
    private static boolean notifyCrash(String crashMessage) {
        return true;
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
                try (CompilerPhaseScope cps = debug.enterCompilerPhase("Parsing")) {
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
