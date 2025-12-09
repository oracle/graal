/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto;

import java.io.PrintStream;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.svm.common.option.CommonOptionParser;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.graal.code.SubstrateCompilationIdentifier;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionParser;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.graal.RuntimeCompilationSupport;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.graal.meta.RuntimeCodeInstaller;
import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.hosted.image.PreserveOptionsSupport;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoGraphBuilderPhase;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoProfileProvider;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.CompilationWatchDog;
import jdk.graal.compiler.core.CompilationWrapper;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.spi.ProfileProvider;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.LowTierLoweringPhase;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

public class RistrettoUtils {

    /**
     * Forces the given type to be preserved in the native image and registered for runtime
     * reflection.
     * <p>
     * Registers the class with the native-image
     * {@link org.graalvm.nativeimage.impl.RuntimeReflectionSupport} so that its type metadata is
     * retained at runtime. This prevents the image builder from pruning the type and makes it
     * available to the Ristretto interpreter/JIT infrastructure at run time. This means classes
     * loaded at runtime can use the preserved types.
     *
     * @see org.graalvm.nativeimage.impl.RuntimeReflectionSupport
     * @see com.oracle.svm.hosted.image.PreserveOptionsSupport
     */
    public static void forcePreserveType(Class<?> c) {
        final RuntimeReflectionSupport reflection = ImageSingletons.lookup(RuntimeReflectionSupport.class);
        PreserveOptionsSupport.registerType(reflection, c);
    }

    /**
     * Determines if the given method has bytecode available to use at runtime. This means we have,
     * through whatever way (class files or serialized to runtime) bytecodes available to interpret
     * and use for bytecode-based JIT compilation.
     */
    public static boolean runtimeBytecodesAvailable(ResolvedJavaMethod method) {
        ResolvedJavaType type = method.getDeclaringClass();
        if (type.isPrimitive()) {
            return false; // Primitives are always hosted (no bytecode interpretation)
        }
        InterpreterResolvedJavaMethod iMethod = null;
        if (method instanceof RistrettoMethod rMethod) {
            iMethod = rMethod.getInterpreterMethod();
        } else if (method instanceof InterpreterResolvedJavaMethod interpreterResolvedJavaMethod) {
            iMethod = interpreterResolvedJavaMethod;
        } else {
            throw GraalError.shouldNotReachHere("Unknown interpreter method " + method);
        }
        return iMethod.getInterpretedCode() != null;
    }

    /**
     * Determines if the given method was compiled hosted already during image building.
     */
    public static boolean wasAOTCompiled(ResolvedJavaMethod method) {
        ResolvedJavaType type = method.getDeclaringClass();
        if (type.isPrimitive()) {
            return true;
        }
        InterpreterResolvedJavaMethod iMethod = null;
        if (method instanceof RistrettoMethod rMethod) {
            iMethod = rMethod.getInterpreterMethod();
        } else if (method instanceof InterpreterResolvedJavaMethod interpreterResolvedJavaMethod) {
            iMethod = interpreterResolvedJavaMethod;
        } else {
            throw GraalError.shouldNotReachHere("Unknown interpreter method " + method);
        }
        return iMethod.hasNativeEntryPoint();
    }

    public static CompilationResult compile(DebugContext debug, final SubstrateMethod method) {
        return doCompile(debug, RuntimeCompilationSupport.getRuntimeConfig(), RuntimeCompilationSupport.getLIRSuites(), method);
    }

    public static InstalledCode compileAndInstall(SubstrateMethod method) {
        return compileAndInstall(method, () -> new SubstrateInstalledCodeImpl(method));
    }

    public static InstalledCode compileAndInstall(SubstrateMethod method, SubstrateInstalledCode.Factory installedCodeFactory) {
        if (RistrettoRuntimeOptions.JITTraceCompilation.getValue()) {
            Log.log().string("[Ristretto Compiler] Starting compilation of").string(method.format("%H.%n(%p)")).newline();
        }
        RuntimeConfiguration runtimeConfiguration = RuntimeCompilationSupport.getRuntimeConfig();
        DebugContext debug = new DebugContext.Builder(RuntimeOptionValues.singleton(), new GraalDebugHandlersFactory(runtimeConfiguration.getProviders().getSnippetReflection())).build();
        SubstrateInstalledCode installedCode = installedCodeFactory.createSubstrateInstalledCode();
        CompilationResult compilationResult = doCompile(debug, RuntimeCompilationSupport.getRuntimeConfig(), RuntimeCompilationSupport.getLIRSuites(), method);
        RuntimeCodeInstaller.install(method, compilationResult, installedCode);
        if (RistrettoRuntimeOptions.JITTraceCompilation.getValue()) {
            Log.log().string("[Ristretto Compiler] Finished compilation, code for ").string(method.format("%H.%n(%p)")).string(": ").signed(compilationResult.getTargetCodeSize()).string(" bytes")
                            .newline();
        }
        return (InstalledCode) installedCode;
    }

    public static CompilationResult doCompile(DebugContext initialDebug, RuntimeConfiguration runtimeConfig, LIRSuites lirSuites, SubstrateMethod method) {
        SubstrateGraalUtils.updateGraalArchitectureWithHostCPUFeatures(runtimeConfig.lookupBackend(method));

        String methodString = method.format("%H.%n(%p)");
        SubstrateCompilationIdentifier compilationId = new SubstrateCompilationIdentifier(method);

        return new CompilationWrapper<CompilationResult>(RuntimeCompilationSupport.get().getDebugOutputDirectory(), SubstrateGraalUtils.COMPILATION_PROBLEMS_PER_ACTION) {
            @SuppressWarnings({"unchecked", "unused"})
            <E extends Throwable> RuntimeException silenceThrowable(Class<E> type, Throwable ex) throws E {
                throw (E) ex;
            }

            @Override
            protected CompilationResult handleException(Throwable t) {
                throw silenceThrowable(RuntimeException.class, t);
            }

            @Override
            protected void parseRetryOptions(String[] options, EconomicMap<OptionKey<?>, Object> values) {
                // Use name=value boolean format for compatibility with Graal options
                CommonOptionParser.BooleanOptionFormat booleanFormat = CommonOptionParser.BooleanOptionFormat.NAME_VALUE;
                for (String option : options) {
                    RuntimeOptionParser.singleton().parseOptionAtRuntime(option, "", booleanFormat, values, false);
                }
            }

            @Override
            protected CompilationResult performCompilation(DebugContext debug) {
                try (CompilationWatchDog _ = CompilationWatchDog.watch(compilationId, debug.getOptions(), false, SubstrateGraalUtils.COMPILATION_WATCH_DOG_EVENT_HANDLER, null)) {
                    StructuredGraph graph;
                    Suites suites;
                    if (method instanceof RistrettoMethod rMethod) {
                        final OptionValues options = debug.getOptions();
                        // final int entryBCI = 0;
                        final SpeculationLog speculationLog = new SubstrateSpeculationLog();
                        final ProfileProvider profileProvider = new RistrettoProfileProvider(rMethod);
                        final StructuredGraph.AllowAssumptions allowAssumptions = StructuredGraph.AllowAssumptions.NO;
                        // TODO GR-71494 - OSR support will require setting the entry BCI for
                        // parsing
                        graph = new StructuredGraph.Builder(options, debug, allowAssumptions).method(method).speculationLog(speculationLog)
                                        .profileProvider(profileProvider).compilationId(compilationId).build();
                        assert graph != null;
                        suites = RuntimeCompilationSupport.getMatchingSuitesForGraph(graph);
                        parseFromBytecode(graph, runtimeConfig);
                        if (TestingBackdoor.shouldRememberGraph()) {
                            // override the suites with graph capturing phases
                            suites = suites.copy();
                            TestingBackdoor.installLastGraphThieves(suites, graph);
                        }
                    } else {
                        graph = RuntimeCompilationSupport.decodeGraph(debug, null, compilationId, method, null);
                        suites = RuntimeCompilationSupport.getMatchingSuitesForGraph(graph);
                    }
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After parsing ");
                    return SubstrateGraalUtils.compileGraph(runtimeConfig, suites, lirSuites, method, graph);
                }
            }

            @Override
            public String toString() {
                return methodString;
            }

            @SuppressWarnings("hiding")
            @Override
            protected DebugContext createRetryDebugContext(DebugContext initialDebug, OptionValues options, PrintStream logStream) {
                return RuntimeCompilationSupport.get().openDebugContext(options, compilationId, method, logStream);
            }

            @Override
            protected void exitHostVM(int status) {
                System.exit(status);
            }
        }.run(initialDebug);
    }

    private static void parseFromBytecode(StructuredGraph graph, RuntimeConfiguration runtimeConfig) {
        Providers runtimeProviders = runtimeConfig.getProviders();
        Replacements runtimeReplacements = runtimeProviders.getReplacements();
        GraphBuilderConfiguration.Plugins gbp = runtimeReplacements.getGraphBuilderPlugins();
        GraphBuilderConfiguration gpc = GraphBuilderConfiguration.getDefault(gbp);
        HighTierContext hc = new HighTierContext(runtimeConfig.getProviders(), null, OptimisticOptimizations.NONE);
        RistrettoGraphBuilderPhase graphBuilderPhase = new RistrettoGraphBuilderPhase(gpc);
        graphBuilderPhase.apply(graph, hc);
        assert graph.getNodeCount() > 1 : "Must have nodes after parsing";
    }

    public static final class TestingBackdoor {
        private TestingBackdoor() {
            // this type should never be allocated
        }

        /**
         * Export access to the last compiled graph of a ristretto compile to be able to white box
         * test various properties.
         */
        public static final ThreadLocal<StructuredGraph> LastCompiledGraph = new ThreadLocal<>();
        public static final ThreadLocal<StructuredGraph> LastCompiledGraphAfterHighTierLowering = new ThreadLocal<>();
        public static final ThreadLocal<StructuredGraph> LastCompiledGraphAfterMidTierLowering = new ThreadLocal<>();
        public static final ThreadLocal<StructuredGraph> LastCompiledGraphAfterLowTierLowering = new ThreadLocal<>();

        public static boolean shouldRememberGraph() {
            String prop = System.getProperty("com.oracle.svm.interpreter.ristretto.RistrettoUtils.PreserveLastCompiledGraph", "false");
            return Boolean.parseBoolean(prop);
        }

        static void installLastGraphThieves(Suites suites, StructuredGraph rootGraph) {
            TestingBackdoor.LastCompiledGraph.set(rootGraph);
            suites.getHighTier().insertAfterPhase(HighTierLoweringPhase.class, new Phase() {
                @Override
                public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                    return ALWAYS_APPLICABLE;
                }

                @Override
                protected void run(StructuredGraph graph) {
                    TestingBackdoor.LastCompiledGraphAfterHighTierLowering.set((StructuredGraph) graph.copy(graph.getDebug()));
                }
            });
            suites.getMidTier().insertAfterPhase(MidTierLoweringPhase.class, new Phase() {
                @Override
                public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                    return ALWAYS_APPLICABLE;
                }

                @Override
                protected void run(StructuredGraph graph) {
                    TestingBackdoor.LastCompiledGraphAfterMidTierLowering.set((StructuredGraph) graph.copy(graph.getDebug()));
                }
            });
            suites.getLowTier().insertAfterPhase(LowTierLoweringPhase.class, new Phase() {
                @Override
                public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                    return ALWAYS_APPLICABLE;
                }

                @Override
                protected void run(StructuredGraph graph) {
                    TestingBackdoor.LastCompiledGraphAfterLowTierLowering.set((StructuredGraph) graph.copy(graph.getDebug()));
                }
            });
        }
    }
}
