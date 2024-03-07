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

import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.RelevantForCompilationIsolates;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.PrintStream;
import java.util.EnumMap;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.code.SubstrateCompilationIdentifier;
import com.oracle.svm.core.graal.code.SubstrateCompilationResult;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.meta.SubstrateMethod;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.CompilationWatchDog;
import jdk.graal.compiler.core.CompilationWrapper;
import jdk.graal.compiler.core.CompilationWrapper.ExceptionAction;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;

public class SubstrateGraalUtils {

    /** Does the compilation of the method and returns the compilation result. */
    public static CompilationResult compile(DebugContext debug, final SubstrateMethod method) {
        return doCompile(debug, TruffleRuntimeCompilationSupport.getRuntimeConfig(), TruffleRuntimeCompilationSupport.getLIRSuites(), method);
    }

    private static final Map<ExceptionAction, Integer> compilationProblemsPerAction = new EnumMap<>(ExceptionAction.class);

    private static final CompilationWatchDog.EventHandler COMPILATION_WATCH_DOG_EVENT_HANDLER = new CompilationWatchDog.EventHandler() {
        @Override
        public void onStuckCompilation(CompilationWatchDog watchDog, Thread watched, CompilationIdentifier compilation, StackTraceElement[] stackTrace, int stuckTime) {
            CompilationWatchDog.EventHandler.super.onStuckCompilation(watchDog, watched, compilation, stackTrace, stuckTime);
            TTY.println("Compilation %s on %s appears stuck - exiting VM", compilation, watched);
            System.exit(STUCK_COMPILATION_EXIT_CODE);
        }
    };

    public static CompilationResult doCompile(DebugContext initialDebug, RuntimeConfiguration runtimeConfig, LIRSuites lirSuites, final SubstrateMethod method) {
        updateGraalArchitectureWithHostCPUFeatures(runtimeConfig.lookupBackend(method));

        String methodString = method.format("%H.%n(%p)");
        SubstrateCompilationIdentifier compilationId = new SubstrateCompilationIdentifier(method);

        return new CompilationWrapper<CompilationResult>(TruffleRuntimeCompilationSupport.get().getDebugOutputDirectory(), compilationProblemsPerAction) {
            @SuppressWarnings({"unchecked", "unused"})
            <E extends Throwable> RuntimeException silenceThrowable(Class<E> type, Throwable ex) throws E {
                throw (E) ex;
            }

            @Override
            protected CompilationResult handleException(Throwable t) {
                throw silenceThrowable(RuntimeException.class, t);
            }

            @SuppressWarnings("try")
            @Override
            protected CompilationResult performCompilation(DebugContext debug) {
                try (CompilationWatchDog watchdog = CompilationWatchDog.watch(compilationId, debug.getOptions(), false, COMPILATION_WATCH_DOG_EVENT_HANDLER)) {
                    StructuredGraph graph = TruffleRuntimeCompilationSupport.decodeGraph(debug, null, compilationId, method, null);
                    return compileGraph(runtimeConfig, TruffleRuntimeCompilationSupport.getMatchingSuitesForGraph(graph), lirSuites, method, graph);
                }
            }

            @Override
            public String toString() {
                return methodString;
            }

            @SuppressWarnings("hiding")
            @Override
            protected DebugContext createRetryDebugContext(DebugContext initialDebug, OptionValues options, PrintStream logStream) {
                return TruffleRuntimeCompilationSupport.get().openDebugContext(options, compilationId, method, logStream);
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
                Architecture architecture = graalBackend.getCodeCache().getTarget().arch;
                cpuFeatureAccess.enableFeatures(architecture);
            }
        }
    }

    public static CompilationResult compileGraph(final SharedMethod method, final StructuredGraph graph) {
        return compileGraph(TruffleRuntimeCompilationSupport.getRuntimeConfig(), TruffleRuntimeCompilationSupport.getMatchingSuitesForGraph(graph), TruffleRuntimeCompilationSupport.getLIRSuites(),
                        method, graph);
    }

    public static class Options {
        @Option(help = "Force-dump graphs before compilation")//
        public static final RuntimeOptionKey<Boolean> ForceDumpGraphsBeforeCompilation = new RuntimeOptionKey<>(false, RelevantForCompilationIsolates);
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

    /** Prepares a hosted {@link JavaConstant} for runtime compilation. */
    public static JavaConstant hostedToRuntime(JavaConstant constant, ConstantReflectionProvider constantReflection) {
        if (constant instanceof ImageHeapConstant heapConstant) {
            return hostedToRuntime(heapConstant, constantReflection);
        }
        return constant;
    }

    /**
     * Prepares a hosted {@link ImageHeapConstant} for runtime compilation: it unwraps the
     * {@link HotSpotObjectConstant} and wraps the hosted object into a
     * {@link SubstrateObjectConstant}. We reuse the identity hash code of the heap constant.
     */
    public static JavaConstant hostedToRuntime(ImageHeapConstant heapConstant, ConstantReflectionProvider constantReflection) {
        JavaConstant hostedConstant = heapConstant.getHostedObject();
        VMError.guarantee(hostedConstant instanceof HotSpotObjectConstant, "Expected to find HotSpotObjectConstant, found %s", hostedConstant);
        Object hostedObject = GraalAccess.getOriginalSnippetReflection().asObject(Object.class, hostedConstant);
        return SubstrateObjectConstant.forObject(hostedObject, ((IdentityHashCodeProvider) constantReflection).identityHashCode(heapConstant));
    }

    /**
     * Transforms a {@link SubstrateObjectConstant} from an encoded graph into an
     * {@link ImageHeapConstant} for hosted processing: it unwraps the hosted object from the
     * {@link SubstrateObjectConstant}, wraps it into an {@link HotSpotObjectConstant}, then
     * redirects the lookup through the {@link ImageHeapScanner}.
     */
    public static JavaConstant runtimeToHosted(JavaConstant constant, ImageHeapScanner scanner) {
        if (constant instanceof SubstrateObjectConstant) {
            JavaConstant hostedConstant = GraalAccess.getOriginalSnippetReflection().forObject(SubstrateObjectConstant.asObject(constant));
            return scanner.getImageHeapConstant(hostedConstant);
        }
        return constant;
    }

}
