/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot;

import static org.graalvm.compiler.core.GraalCompiler.compileGraph;
import static org.graalvm.compiler.debug.DebugOptions.DebugStubsAndSnippets;
import static org.graalvm.compiler.hotspot.meta.HotSpotSuitesProvider.withNodeSourcePosition;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Activation;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.CompilerConfigurationFactory;
import org.graalvm.compiler.hotspot.EconomyCompilerConfigurationFactory;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotBackendFactory;
import org.graalvm.compiler.hotspot.HotSpotCompilationIdentifier;
import org.graalvm.compiler.hotspot.HotSpotCompiledCodeBuilder;
import org.graalvm.compiler.hotspot.HotSpotGraalCompilerFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotGraalServices;
import org.graalvm.compiler.hotspot.HotSpotGraphBuilderInstance;
import org.graalvm.compiler.hotspot.meta.HotSpotLoweringProvider;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.EntryPointDecorator;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.Speculative;
import org.graalvm.compiler.phases.common.AbstractInliningPhase;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.compiler.PartialEvaluatorConfiguration;
import org.graalvm.compiler.truffle.compiler.TruffleCompilationIdentifier;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerConfiguration;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.compiler.TruffleTierConfiguration;
import org.graalvm.compiler.truffle.compiler.host.HostInliningPhase;
import org.graalvm.compiler.truffle.compiler.host.InjectImmutableFrameFieldsPhase;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;
import com.oracle.truffle.compiler.hotspot.HotSpotTruffleCompiler;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCICompiler;

public final class HotSpotTruffleCompilerImpl extends TruffleCompilerImpl implements HotSpotTruffleCompiler {

    public static class Options {
        // @formatter:off
        @Option(help = "Select a compiler configuration for Truffle compilation (default: use Graal system compiler configuration).")
        public static final OptionKey<String> TruffleCompilerConfiguration = new OptionKey<>(null);
        // @formatter:on
    }

    /**
     * The HotSpot-specific Graal runtime associated with this compiler.
     */
    private final HotSpotGraalRuntimeProvider hotspotGraalRuntime;

    public static List<HotSpotBackend> ensureBackendsInitialized(OptionValues options) {
        HotSpotGraalRuntimeProvider graalRuntime = (HotSpotGraalRuntimeProvider) getCompiler(options).getGraalRuntime();
        List<HotSpotBackend> backends = new ArrayList<>();
        backends.add(createTruffleBackend(graalRuntime, options, null, null));
        backends.add(createTruffleBackend(graalRuntime, options, null, EconomyCompilerConfigurationFactory.NAME));
        return backends;
    }

    @Override
    protected OptionValues getGraalOptions() {
        return HotSpotGraalOptionValues.defaultOptions();
    }

    public static HotSpotTruffleCompilerImpl create(final TruffleCompilerRuntime runtime) {
        OptionValues options = HotSpotGraalOptionValues.defaultOptions();
        /*
         * Host inlining is not necessary for Truffle guest compilation so disable it.
         */
        options = new OptionValues(options,
                        HostInliningPhase.Options.TruffleHostInlining, Boolean.FALSE,
                        InjectImmutableFrameFieldsPhase.Options.TruffleImmutableFrameFields, Boolean.FALSE);

        HotSpotGraalRuntimeProvider graalRuntime = (HotSpotGraalRuntimeProvider) getCompiler(options).getGraalRuntime();
        SnippetReflectionProvider snippetReflection = graalRuntime.getRequiredCapability(SnippetReflectionProvider.class);
        HotSpotKnownTruffleTypes knownTruffleTypes = new HotSpotKnownTruffleTypes(runtime, graalRuntime.getHostProviders().getMetaAccess(), graalRuntime.getHostProviders().getConstantReflection());

        final TruffleTierConfiguration lastTier = createTierConfiguration(graalRuntime, options, knownTruffleTypes, null);
        final TruffleTierConfiguration firstTier = createTierConfiguration(graalRuntime, options, knownTruffleTypes, EconomyCompilerConfigurationFactory.NAME);

        HotSpotBackend hostbackend = graalRuntime.getHostBackend();

        Suites hostSuites = hostbackend.getSuites().getDefaultSuites(options, hostbackend.getTarget().arch);

        GraphBuilderPhase phase = (GraphBuilderPhase) lastTier.backend().getSuites().getDefaultGraphBuilderSuite().findPhase(GraphBuilderPhase.class).previous();
        Plugins plugins = phase.getGraphBuilderConfig().getPlugins();

        final TruffleCompilerConfiguration compilerConfig = new TruffleCompilerConfiguration(runtime, plugins, snippetReflection,
                        firstTier, lastTier, knownTruffleTypes, hostSuites);

        HotSpotTruffleCompilerImpl compiler = new HotSpotTruffleCompilerImpl(graalRuntime, compilerConfig);

        /*
         * Through inlining during host the truffle compiler may be used we also need to install the
         * code installation task.
         */
        assert !compilerConfig.backends().contains(hostbackend);
        compiler.initializeBackend(hostbackend);

        return compiler;
    }

    /**
     * In the past we reused the JVMCI backend from the host compiler directly as a last tier, this
     * made certain things work in the last tier, but not in the first tier. In order to ensure that
     * host jvmci compilation and Truffle guest compilation is separated we recreate the
     * configuration for each tier and keep it separate from the host compiler configuration. This
     * also ensures that configuration that should only happen for Truffle runtime compilation
     * cannot influence host compilation and vice versa.
     */
    private static TruffleTierConfiguration createTierConfiguration(HotSpotGraalRuntimeProvider hotspotGraalRuntime, OptionValues options, HotSpotKnownTruffleTypes knownTruffleTypes,
                    String forceConfigName) {
        String configName = resolveConfigurationName(hotspotGraalRuntime, options, forceConfigName);
        PartialEvaluatorConfiguration peConfig = createPartialEvaluatorConfiguration(configName);
        Backend backend = createTruffleBackend(hotspotGraalRuntime, options, knownTruffleTypes, forceConfigName);
        Suites suites = backend.getSuites().getDefaultSuites(options, backend.getTarget().arch);
        LIRSuites lirSuites = backend.getSuites().getDefaultLIRSuites(options);
        return new TruffleTierConfiguration(peConfig, backend, backend.getProviders(), suites, lirSuites, knownTruffleTypes);
    }

    private static String resolveConfigurationName(HotSpotGraalRuntimeProvider hotspotGraalRuntime, OptionValues options, String forceConfigName) {
        String configName = forceConfigName;
        if (configName == null) {
            configName = Options.TruffleCompilerConfiguration.getValue(options);
        }
        if (configName == null) {
            // inherit config name from host compiler
            configName = hotspotGraalRuntime.getCompilerConfigurationName();
        }
        return configName;
    }

    private static HotSpotBackend createTruffleBackend(HotSpotGraalRuntimeProvider hotspotGraalRuntime, OptionValues options, HotSpotKnownTruffleTypes knownTruffleTypes,
                    String forceConfigName) {
        String configName = resolveConfigurationName(hotspotGraalRuntime, options, forceConfigName);
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        CompilerConfigurationFactory compilerConfigurationFactory = CompilerConfigurationFactory.selectFactory(configName, options, runtime);
        CompilerConfiguration compilerConfiguration = compilerConfigurationFactory.createCompilerConfiguration();
        HotSpotBackendFactory backendFactory = compilerConfigurationFactory.createBackendMap().getBackendFactory(hotspotGraalRuntime.getHostBackend().getTarget().arch);
        HotSpotBackend backend = backendFactory.createBackend(hotspotGraalRuntime, compilerConfiguration, runtime, null);
        HotSpotLoweringProvider loweringProvider = (HotSpotLoweringProvider) backend.getProviders().getLowerer();

        loweringProvider.initializeExtensions(options, null, backend.getProviders(), hotspotGraalRuntime.getVMConfig(),
                        List.of(new HotSpotTruffleLoweringExtensions(knownTruffleTypes)));
        backend.completeInitialization(runtime, options);

        return backend;

    }

    static GraalJVMCICompiler getCompiler(OptionValues options) {
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        if (!Options.TruffleCompilerConfiguration.hasBeenSet(options)) {
            JVMCICompiler compiler = runtime.getCompiler();
            if (compiler instanceof GraalJVMCICompiler) {
                return (GraalJVMCICompiler) compiler;
            }
        }
        CompilerConfigurationFactory compilerConfigurationFactory = CompilerConfigurationFactory.selectFactory(Options.TruffleCompilerConfiguration.getValue(options), options, runtime);
        return HotSpotGraalCompilerFactory.createCompiler("Truffle", runtime, options, compilerConfigurationFactory);
    }

    public HotSpotTruffleCompilerImpl(HotSpotGraalRuntimeProvider hotspotGraalRuntime, TruffleCompilerConfiguration config) {
        super(config);
        this.hotspotGraalRuntime = hotspotGraalRuntime;
        getPartialEvaluator().setJvmciReservedReference0Offset(hotspotGraalRuntime.getVMConfig().jvmciReservedReference0Offset);
    }

    @Override
    public TruffleCompilationIdentifier createCompilationIdentifier(TruffleCompilationTask task, TruffleCompilable compilable) {
        ResolvedJavaMethod rootMethod = partialEvaluator.rootForCallTarget(compilable);
        HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) rootMethod, JVMCICompiler.INVOCATION_ENTRY_BCI, 0L);
        return new HotSpotTruffleCompilationIdentifier(request, task, compilable);
    }

    private volatile List<DebugHandlersFactory> factories;

    private List<DebugHandlersFactory> getDebugHandlerFactories() {
        if (factories == null) {
            // Multiple initialization by racing threads is harmless
            List<DebugHandlersFactory> list = new ArrayList<>();
            list.add(new GraalDebugHandlersFactory(getSnippetReflection()));
            for (DebugHandlersFactory factory : DebugHandlersFactory.LOADER) {
                // Ignore other instances of GraalDebugHandlersFactory
                if (!(factory instanceof GraalDebugHandlersFactory)) {
                    list.add(factory);
                }
            }
            factories = list;
        }
        return factories;
    }

    public HotSpotGraalRuntimeProvider getHotspotGraalRuntime() {
        return hotspotGraalRuntime;
    }

    @Override
    protected DebugContext createDebugContext(OptionValues options, CompilationIdentifier compilationId, TruffleCompilable compilable, PrintStream logStream) {
        return hotspotGraalRuntime.openDebugContext(options, compilationId, compilable, getDebugHandlerFactories(), logStream);
    }

    @Override
    protected HotSpotPartialEvaluator createPartialEvaluator(TruffleCompilerConfiguration configuration) {
        return new HotSpotPartialEvaluator(configuration, builderConfig);
    }

    @Override
    public PhaseSuite<HighTierContext> createGraphBuilderSuite(TruffleTierConfiguration tier) {
        return tier.backend().getSuites().getDefaultGraphBuilderSuite().copy();
    }

    /**
     * Compiles a method denoted as an entry point to truffle code invoked by the runtime. The
     * compiled code has a special entry point to jump to Truffle-compiled code generated by an
     * {@link TruffleCallBoundaryInstrumentationFactory}.
     */
    @Override
    public void installTruffleCallBoundaryMethod(ResolvedJavaMethod method, TruffleCompilable compilable) {
        compileAndInstallStub(method, (debug, javaMethod, compilationId) -> {
            final Backend backend = config.lastTier().backend();
            return compileTruffleStub(debug, javaMethod, compilationId, getTruffleCallBoundaryInstrumentationFactory(backend.getTarget().arch.getName()), new InvocationPlugins());
        });
    }

    private EntryPointDecorator getTruffleCallBoundaryInstrumentationFactory(String arch) {
        for (TruffleCallBoundaryInstrumentationFactory factory : GraalServices.load(TruffleCallBoundaryInstrumentationFactory.class)) {
            if (factory.getArchitecture().equals(arch)) {
                return factory.create(config, hotspotGraalRuntime.getVMConfig(), hotspotGraalRuntime.getHostProviders().getRegisters());
            }
        }
        // No specialization of OptimizedCallTarget on this platform.
        return null;
    }

    /**
     * Compiles a method with fast thread local Truffle intrinsics. This allows to access the jvmci
     * reserved oop field. See HotSpotFastThreadLocal.
     */
    @Override
    public void installTruffleReservedOopMethod(ResolvedJavaMethod method, TruffleCompilable compilable) {
        int jvmciReservedReference0Offset = hotspotGraalRuntime.getVMConfig().jvmciReservedReference0Offset;
        if (jvmciReservedReference0Offset == -1) {
            throw GraalError.shouldNotReachHere("Trying to install reserved oop method when field is not available."); // ExcludeFromJacocoGeneratedReport
        }
        compileAndInstallStub(method, (debug, javaMethod, compilationId) -> {
            InvocationPlugins p = new InvocationPlugins();
            HotSpotTruffleGraphBuilderPlugins.registerHotspotThreadLocalStubPlugins(p, config.lastTier().providers().getWordTypes(), hotspotGraalRuntime.getVMConfig().jvmciReservedReference0Offset);
            return compileTruffleStub(debug, javaMethod, compilationId, null, p);
        });
    }

    @SuppressWarnings("try")
    private void compileAndInstallStub(ResolvedJavaMethod method, StubCompilation compilation) {
        HotSpotResolvedJavaMethod hotSpotMethod = (HotSpotResolvedJavaMethod) method;
        if (hotSpotMethod.hasCompiledCode()) {
            // nothing to do
            return;
        }
        HotSpotCompilationIdentifier compilationId = (HotSpotCompilationIdentifier) config.lastTier().backend().getCompilationIdentifier(method);
        OptionValues options = getGraalOptions();
        try (DebugContext debug = DebugStubsAndSnippets.getValue(options)
                        ? hotspotGraalRuntime.openDebugContext(options, compilationId, method, getDebugHandlerFactories(), DebugContext.getDefaultLogStream())
                        : DebugContext.disabled(options);
                        Activation a = debug.activate();
                        DebugContext.Scope d = debug.scope("InstallingTruffleStub")) {
            CompilationResult compResult = compilation.compile(debug, hotSpotMethod, compilationId);
            CodeCacheProvider codeCache = config.lastTier().providers().getCodeCache();
            try (DebugContext.Scope s = debug.scope("CodeInstall", codeCache, method, compResult)) {
                CompiledCode compiledCode = HotSpotCompiledCodeBuilder.createCompiledCode(codeCache, method, compilationId.getRequest(), compResult, options);
                codeCache.setDefaultCode(method, compiledCode);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }
    }

    /**
     * Utility to compile a truffle stub. Truffle stubs avoid deoptimization as much as possible and
     * therefore deactivate all speculation and use of profiling information. Also note that truffle
     * stubs do not perform any inlining and resolve classes eagerly.
     */
    private CompilationResult compileTruffleStub(DebugContext debug, ResolvedJavaMethod javaMethod, CompilationIdentifier compilationId,
                    EntryPointDecorator resultFactory, InvocationPlugins plugins) {
        TruffleTierConfiguration tier = config.lastTier();
        Suites newSuites = config.hostSuite().copy();
        removeInliningPhases(newSuites);
        removeSpeculativePhases(newSuites);

        final Providers lastTierProviders = tier.providers();
        final Backend backend = tier.backend();
        HotSpotCodeCacheProvider codeCache = (HotSpotCodeCacheProvider) lastTierProviders.getCodeCache();
        boolean infoPoints = codeCache.shouldDebugNonSafepoints();

        GraphBuilderConfiguration newBuilderConfig = GraphBuilderConfiguration.getDefault(new Plugins(plugins))//
                        .withEagerResolving(true)//
                        .withUnresolvedIsError(true)//
                        .withNodeSourcePosition(infoPoints);

        StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug, AllowAssumptions.NO)//
                        .profileProvider(null)//
                        .method(javaMethod) //
                        .compilationId(compilationId) //
                        .build();

        new HotSpotGraphBuilderInstance(lastTierProviders, newBuilderConfig, OptimisticOptimizations.ALL, null).apply(graph);

        PhaseSuite<HighTierContext> graphBuilderSuite = getGraphBuilderSuite(codeCache, backend.getSuites());
        return compileGraph(graph, javaMethod, lastTierProviders, backend, graphBuilderSuite, OptimisticOptimizations.ALL, graph.getProfilingInfo(), newSuites, tier.lirSuites(),
                        new CompilationResult(compilationId), CompilationResultBuilderFactory.Default, resultFactory, false);
    }

    @Override
    public int pendingTransferToInterpreterOffset(TruffleCompilable compilable) {
        return hotspotGraalRuntime.getVMConfig().pendingTransferToInterpreterOffset;
    }

    @Override
    protected DiagnosticsOutputDirectory getDebugOutputDirectory() {
        return hotspotGraalRuntime.getOutputDirectory();
    }

    @Override
    protected Map<ExceptionAction, Integer> getCompilationProblemsPerAction() {
        return hotspotGraalRuntime.getCompilationProblemsPerAction();
    }

    private static PhaseSuite<HighTierContext> getGraphBuilderSuite(CodeCacheProvider codeCache, SuitesProvider suitesProvider) {
        PhaseSuite<HighTierContext> graphBuilderSuite = suitesProvider.getDefaultGraphBuilderSuite();
        if (codeCache.shouldDebugNonSafepoints()) {
            graphBuilderSuite = withNodeSourcePosition(graphBuilderSuite);
        }
        return graphBuilderSuite;
    }

    private static void removeInliningPhases(Suites suites) {
        ListIterator<BasePhase<? super HighTierContext>> inliningPhase = suites.getHighTier().findPhase(AbstractInliningPhase.class);
        while (inliningPhase != null) {
            inliningPhase.remove();
            inliningPhase = suites.getHighTier().findPhase(AbstractInliningPhase.class);
        }
    }

    private static void removeSpeculativePhases(Suites suites) {
        suites.getHighTier().removeSubTypePhases(Speculative.class);
        suites.getMidTier().removeSubTypePhases(Speculative.class);
        suites.getLowTier().removeSubTypePhases(Speculative.class);
    }

    @Override
    protected InstalledCode createInstalledCode(TruffleCompilable compilable) {
        return null;
    }

    @Override
    protected void exitHostVM(int status) {
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        HotSpotGraalServices.exit(status, runtime);
    }

    @Override
    protected CompilationResult createCompilationResult(String name, CompilationIdentifier compilationIdentifier, TruffleCompilable compilable) {
        return new HotSpotTruffleCompilationResult(compilationIdentifier, name, compilable);
    }

    @Override
    protected void afterCodeInstallation(CompilationResult result, InstalledCode installedCode) {
        if (result instanceof HotSpotTruffleCompilationResult) {
            config.runtime().onCodeInstallation(((HotSpotTruffleCompilationResult) result).compilable, installedCode);
        }
    }

    @Override
    protected TruffleCompilable getCompilable(CompilationResult result) {
        if (result instanceof HotSpotTruffleCompilationResult) {
            return ((HotSpotTruffleCompilationResult) result).compilable;
        }
        return null;
    }

    private static final class HotSpotTruffleCompilationResult extends CompilationResult {

        final TruffleCompilable compilable;

        HotSpotTruffleCompilationResult(CompilationIdentifier compilationId, String name, TruffleCompilable compilable) {
            super(compilationId, name);
            this.compilable = compilable;
        }
    }

    @Override
    public HotSpotPartialEvaluator getPartialEvaluator() {
        return (HotSpotPartialEvaluator) super.getPartialEvaluator();
    }

    @Override
    public void purgePartialEvaluationCaches() {
        getPartialEvaluator().purgeEncodedGraphCache();
    }

    @FunctionalInterface
    interface StubCompilation {

        CompilationResult compile(DebugContext debug, ResolvedJavaMethod javaMethod, CompilationIdentifier compilationId);

    }
}
