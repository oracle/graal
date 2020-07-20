/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.PrintStream;
import java.util.EnumMap;
import java.util.Map;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.CompilationWrapper;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.code.SubstrateCompilationIdentifier;
import com.oracle.svm.core.graal.code.SubstrateCompilationResult;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.graal.meta.SubstrateMethod;

import jdk.vm.ci.code.Architecture;

public class SubstrateGraalUtils {

    /** Does the compilation of the method and returns the compilation result. */
    public static CompilationResult compile(DebugContext debug, final SubstrateMethod method) {
        return doCompile(debug, GraalSupport.getRuntimeConfig(), GraalSupport.getSuites(), GraalSupport.getLIRSuites(), method);
    }

    private static final Map<ExceptionAction, Integer> compilationProblemsPerAction = new EnumMap<>(ExceptionAction.class);

    public static CompilationResult doCompile(DebugContext initialDebug, RuntimeConfiguration runtimeConfig, Suites suites, LIRSuites lirSuites, final SubstrateMethod method) {
        updateGraalArchitectureWithHostCPUFeatures(runtimeConfig.lookupBackend(method));

        String methodString = method.format("%H.%n(%p)");
        SubstrateCompilationIdentifier compilationId = new SubstrateCompilationIdentifier();

        return new CompilationWrapper<CompilationResult>(GraalSupport.get().getDebugOutputDirectory(), compilationProblemsPerAction) {
            @SuppressWarnings({"unchecked", "unused"})
            <E extends Throwable> RuntimeException silenceThrowable(Class<E> type, Throwable ex) throws E {
                throw (E) ex;
            }

            @Override
            protected CompilationResult handleException(Throwable t) {
                throw silenceThrowable(RuntimeException.class, t);
            }

            @Override
            protected CompilationResult performCompilation(DebugContext debug) {
                StructuredGraph graph = GraalSupport.decodeGraph(debug, null, compilationId, method);
                return compileGraph(runtimeConfig, suites, lirSuites, method, graph);
            }

            @Override
            public String toString() {
                return methodString;
            }

            @SuppressWarnings("hiding")
            @Override
            protected DebugContext createRetryDebugContext(DebugContext initialDebug, OptionValues options, PrintStream logStream) {
                return GraalSupport.get().openDebugContext(options, compilationId, method, logStream);
            }

            @Override
            protected void exitHostVM(int status) {
                System.exit(status);
            }
        }.run(initialDebug);
    }

    private static boolean architectureInitialized;

    /**
     * Updates the architecture in Graal at run-time in order to enable best code generation on the
     * given machine.
     *
     * Note: this method is not synchronized as it only introduces new features to the enum map
     * which is backed by an array. If two threads repeat the work nothing can go wrong.
     *
     * @param graalBackend The graal backend that should be updated.
     */
    public static void updateGraalArchitectureWithHostCPUFeatures(Backend graalBackend) {
        if (SubstrateUtil.HOSTED) {
            throw shouldNotReachHere("Architecture should be updated only at runtime.");
        }

        if (!architectureInitialized) {
            architectureInitialized = true;
            CPUFeatureAccess cpuFeatureAccess = ImageSingletons.lookup(CPUFeatureAccess.class);
            if (cpuFeatureAccess != null) {
                cpuFeatureAccess.verifyHostSupportsArchitecture(graalBackend.getCodeCache().getTarget().arch);
                Architecture architecture = graalBackend.getCodeCache().getTarget().arch;
                cpuFeatureAccess.enableFeatures(architecture);
            }
        }
    }

    public static CompilationResult compileGraph(final SharedMethod method, final StructuredGraph graph) {
        return compileGraph(GraalSupport.getRuntimeConfig(), GraalSupport.getSuites(), GraalSupport.getLIRSuites(), method, graph);
    }

    public static class Options {
        @Option(help = "Force-dump graphs before compilation")//
        public static final RuntimeOptionKey<Boolean> ForceDumpGraphsBeforeCompilation = new RuntimeOptionKey<>(false);
    }

    @SuppressWarnings("try")
    private static CompilationResult compileGraph(RuntimeConfiguration runtimeConfig, Suites suites, LIRSuites lirSuites, final SharedMethod method, final StructuredGraph graph) {
        assert runtimeConfig != null : "no runtime";

        if (Options.ForceDumpGraphsBeforeCompilation.getValue()) {
            /*
             * forceDump is often used during debugging, and we want to make sure that it keeps
             * working, i.e., does not lead to image generation problems when adding a call to it.
             * This code ensures that forceDump is seen as reachable for all images that include
             * Graal, because it is conditional on a runtime option.
             */
            graph.getDebug().forceDump(graph, "Force dump before compilation");
        }

        String methodName = method.format("%h.%n");

        try (DebugContext debug = graph.getDebug();
                        Indent indent = debug.logAndIndent("compile graph %s for method %s", graph, methodName)) {
            OptimisticOptimizations optimisticOpts = OptimisticOptimizations.ALL.remove(OptimisticOptimizations.Optimization.UseLoopLimitChecks);

            final Backend backend = runtimeConfig.lookupBackend(method);

            try (Indent indent2 = debug.logAndIndent("do compilation")) {
                SubstrateCompilationResult result = new SubstrateCompilationResult(graph.compilationId(), method.format("%H.%n(%p)"));
                GraalCompiler.compileGraph(graph, method, backend.getProviders(), backend, null, optimisticOpts, null, suites, lirSuites, result,
                                CompilationResultBuilderFactory.Default, false);
                return result;
            }
        }
    }
}
