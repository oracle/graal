/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.common.CancellationBailoutException;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugCloseable;
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
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotGraalServices;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.EncodedGraph;
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
import org.graalvm.compiler.phases.common.AbstractInliningPhase;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompiler;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.EconomyPartialEvaluatorConfiguration;
import org.graalvm.compiler.truffle.compiler.PartialEvaluatorConfiguration;
import org.graalvm.compiler.truffle.compiler.TruffleCompilationIdentifier;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerConfiguration;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.compiler.TruffleTierConfiguration;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotNmethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.Assumptions;
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

    public static HotSpotTruffleCompilerImpl create(final TruffleCompilerRuntime runtime) {
        OptionValues options = runtime.getGraalOptions(OptionValues.class);
        HotSpotGraalRuntimeProvider hotspotGraalRuntime = (HotSpotGraalRuntimeProvider) getCompiler(options).getGraalRuntime();
        SnippetReflectionProvider snippetReflection = hotspotGraalRuntime.getRequiredCapability(SnippetReflectionProvider.class);

        HotSpotBackend backend = hotspotGraalRuntime.getHostBackend();
        GraphBuilderPhase phase = (GraphBuilderPhase) backend.getSuites().getDefaultGraphBuilderSuite().findPhase(GraphBuilderPhase.class).previous();
        Plugins plugins = phase.getGraphBuilderConfig().getPlugins();
        final PartialEvaluatorConfiguration lastTierPe = createPartialEvaluatorConfiguration(hotspotGraalRuntime.getCompilerConfigurationName());
        final TruffleTierConfiguration lastTierSetup = new TruffleTierConfiguration(lastTierPe, backend, options);

        CompilerConfigurationFactory lowTierCompilerConfigurationFactory = new EconomyCompilerConfigurationFactory();
        CompilerConfiguration compilerConfiguration = lowTierCompilerConfigurationFactory.createCompilerConfiguration();
        HotSpotBackendFactory backendFactory = lowTierCompilerConfigurationFactory.createBackendMap().getBackendFactory(backend.getTarget().arch);
        HotSpotBackend firstTierBackend = backendFactory.createBackend(hotspotGraalRuntime, compilerConfiguration, HotSpotJVMCIRuntime.runtime(), null);
        Suites firstTierSuites = firstTierBackend.getSuites().getDefaultSuites(options);
        LIRSuites firstTierLirSuites = firstTierBackend.getSuites().getDefaultLIRSuites(options);
        Providers firstTierProviders = firstTierBackend.getProviders();
        PartialEvaluatorConfiguration firstTierPe = new EconomyPartialEvaluatorConfiguration();
        firstTierBackend.completeInitialization(HotSpotJVMCIRuntime.runtime(), options);
        TruffleTierConfiguration firstTierSetup = new TruffleTierConfiguration(firstTierPe, firstTierBackend, firstTierProviders, firstTierSuites, firstTierLirSuites);
        final TruffleCompilerConfiguration compilerConfig = new TruffleCompilerConfiguration(runtime, plugins, snippetReflection, firstTierSetup, lastTierSetup);

        return new HotSpotTruffleCompilerImpl(hotspotGraalRuntime, compilerConfig);
    }

    private static GraalJVMCICompiler getCompiler(OptionValues options) {
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
    public TruffleCompilationIdentifier createCompilationIdentifier(CompilableTruffleAST compilable) {
        ResolvedJavaMethod rootMethod = partialEvaluator.rootForCallTarget(compilable);
        HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) rootMethod, JVMCICompiler.INVOCATION_ENTRY_BCI, 0L);
        return new HotSpotTruffleCompilationIdentifier(request, compilable);
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

    @Override
    public String getCompilerConfigurationName() {
        return hotspotGraalRuntime.getCompilerConfigurationName();
    }

    @Override
    protected DebugContext createDebugContext(OptionValues options, CompilationIdentifier compilationId, CompilableTruffleAST compilable, PrintStream logStream) {
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
    public void installTruffleCallBoundaryMethod(ResolvedJavaMethod method) {
        compileAndInstallStub(method, (debug, javaMethod, compilationId) -> {
            final Backend backend = config.lastTier().backend();
            return compileTruffleStub(debug, javaMethod, compilationId, getTruffleCallBoundaryInstrumentationFactory(backend.getTarget().arch.getName()), new InvocationPlugins());
        });
    }

    private CompilationResultBuilderFactory getTruffleCallBoundaryInstrumentationFactory(String arch) {
        for (TruffleCallBoundaryInstrumentationFactory factory : GraalServices.load(TruffleCallBoundaryInstrumentationFactory.class)) {
            if (factory.getArchitecture().equals(arch)) {
                return factory.create(config.lastTier().providers().getMetaAccess(), hotspotGraalRuntime.getVMConfig(), hotspotGraalRuntime.getHostProviders().getRegisters());
            }
        }
        // No specialization of OptimizedCallTarget on this platform.
        return CompilationResultBuilderFactory.Default;
    }

    /**
     * Compiles a method with fast thread local Truffle intrinsics. This allows to access the jvmci
     * reserved oop field. See HotSpotFastThreadLocal.
     */
    @Override
    public void installTruffleReservedOopMethod(ResolvedJavaMethod method) {
        int jvmciReservedReference0Offset = hotspotGraalRuntime.getVMConfig().jvmciReservedReference0Offset;
        if (jvmciReservedReference0Offset == -1) {
            throw GraalError.shouldNotReachHere("Trying to install reserved oop method when field is not available.");
        }
        compileAndInstallStub(method, (debug, javaMethod, compilationId) -> {
            InvocationPlugins p = new InvocationPlugins();
            HotSpotTruffleGraphBuilderPlugins.registerHotspotThreadLocalStubPlugins(p, config.lastTier().providers().getWordTypes(), hotspotGraalRuntime.getVMConfig().jvmciReservedReference0Offset);
            return compileTruffleStub(debug, javaMethod, compilationId, CompilationResultBuilderFactory.Default, p);
        });
    }

    @SuppressWarnings("try")
    private void compileAndInstallStub(ResolvedJavaMethod method, StubCompilation compilation) {
        HotSpotResolvedJavaMethod hotSpotMethod = (HotSpotResolvedJavaMethod) method;
        if (hotSpotMethod.hasCompiledCode()) {
            // nothing to do
            return;
        }
        HotSpotTruffleCompilerRuntime runtime = (HotSpotTruffleCompilerRuntime) TruffleCompilerRuntime.getRuntime();
        HotSpotCompilationIdentifier compilationId = (HotSpotCompilationIdentifier) config.lastTier().backend().getCompilationIdentifier(method);
        OptionValues options = runtime.getGraalOptions(OptionValues.class);
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
                    CompilationResultBuilderFactory resultFactory,
                    InvocationPlugins plugins) {
        TruffleTierConfiguration tier = config.lastTier();
        Suites newSuites = tier.suites().copy();
        removeInliningPhase(newSuites);

        StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug, AllowAssumptions.NO)//
                        .useProfilingInfo(false)//
                        .method(javaMethod) //
                        .compilationId(compilationId).build();

        final Providers lastTierProviders = tier.providers();
        final Backend backend = tier.backend();
        HotSpotCodeCacheProvider codeCache = (HotSpotCodeCacheProvider) lastTierProviders.getCodeCache();
        boolean infoPoints = codeCache.shouldDebugNonSafepoints();

        GraphBuilderConfiguration newBuilderConfig = GraphBuilderConfiguration.getDefault(new Plugins(plugins))//
                        .withEagerResolving(true)//
                        .withUnresolvedIsError(true)//
                        .withNodeSourcePosition(infoPoints);

        new GraphBuilderPhase.Instance(lastTierProviders, newBuilderConfig, OptimisticOptimizations.ALL, null).apply(graph);

        PhaseSuite<HighTierContext> graphBuilderSuite = getGraphBuilderSuite(codeCache, backend.getSuites());
        return compileGraph(graph, javaMethod, lastTierProviders, backend, graphBuilderSuite, OptimisticOptimizations.ALL, graph.getProfilingInfo(), newSuites, tier.lirSuites(),
                        new CompilationResult(compilationId), resultFactory, false);
    }

    @Override
    public int pendingTransferToInterpreterOffset(CompilableTruffleAST compilable) {
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

    private static void removeInliningPhase(Suites suites) {
        ListIterator<BasePhase<? super HighTierContext>> inliningPhase = suites.getHighTier().findPhase(AbstractInliningPhase.class);
        if (inliningPhase != null) {
            inliningPhase.remove();
        }
    }

    @Override
    protected InstalledCode createInstalledCode(CompilableTruffleAST compilable) {
        return null;
    }

    /**
     * {@link HotSpotNmethod#isDefault() Default} nmethods installed by Graal are executed through a
     * {@code Method::_code} field pointing to them. That is, they can be executed even when the
     * {@link HotSpotNmethod} created during code installation dies. As such, these objects must
     * remain strongly reachable from {@code OptimizedAssumption}s they depend on.
     */
    @Override
    protected boolean soleExecutionEntryPoint(InstalledCode installedCode) {
        if (installedCode instanceof HotSpotNmethod) {
            HotSpotNmethod nmethod = (HotSpotNmethod) installedCode;
            if (nmethod.isDefault()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void exitHostVM(int status) {
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        HotSpotGraalServices.exit(-1, runtime);
    }

    @Override
    protected CompilationResult createCompilationResult(String name, CompilationIdentifier compilationIdentifier, CompilableTruffleAST compilable) {
        return new HotSpotTruffleCompilationResult(compilationIdentifier, name, compilable);
    }

    @Override
    protected void afterCodeInstallation(CompilationResult result, InstalledCode installedCode) {
        if (result instanceof HotSpotTruffleCompilationResult) {
            HotSpotTruffleCompilerRuntime runtime = (HotSpotTruffleCompilerRuntime) TruffleCompilerRuntime.getRuntime();
            runtime.onCodeInstallation(((HotSpotTruffleCompilationResult) result).compilable, installedCode);
        }
    }

    @Override
    protected CompilableTruffleAST getCompilable(CompilationResult result) {
        if (result instanceof HotSpotTruffleCompilationResult) {
            return ((HotSpotTruffleCompilationResult) result).compilable;
        }
        return null;
    }

    private static final class HotSpotTruffleCompilationResult extends CompilationResult {

        final CompilableTruffleAST compilable;

        HotSpotTruffleCompilationResult(CompilationIdentifier compilationId, String name, CompilableTruffleAST compilable) {
            super(compilationId, name);
            this.compilable = compilable;
        }
    }

    @Override
    public HotSpotPartialEvaluator getPartialEvaluator() {
        return (HotSpotPartialEvaluator) super.getPartialEvaluator();
    }

    @Override
    public void purgeCaches() {
        getPartialEvaluator().purgeEncodedGraphCache();
    }

    @SuppressWarnings("try")
    @Override
    protected void handleBailout(DebugContext debug, StructuredGraph graph, BailoutException bailout) {
        /*
         * Catch non-permanent bailouts due to "failed dependencies" aka "invalid assumptions"
         * during code installation. Since there's no specific exception for such cases, it's
         * assumed that non-permanent, non-cancellation bailouts are due to "invalid dependencies"
         * during code installation.
         */
        if (!getPartialEvaluator().isEncodedGraphCacheEnabled()) {
            return;
        }
        if (!(bailout instanceof CancellationBailoutException)) {
            // Evict only the methods that could have caused the invalidation e.g. methods with
            // assumptions.
            if (!bailout.isPermanent() && graph != null && !graph.getAssumptions().isEmpty()) {
                try (DebugCloseable dummy = EncodedGraphCacheEvictionTime.start(debug)) {
                    assert graph.method() != null;
                    EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCache = partialEvaluator.getOrCreateEncodedGraphCache();

                    /*
                     * At this point, the cache containing invalid graphs may be already
                     * purged/dropped, but there's no way to know in which cache the invalid method
                     * is/was present, so all encoded graphs, including the root and all inlined
                     * methods must be evicted. These bailouts (invalid dependencies) are very rare,
                     * the over-evicting impact is negligible.
                     */
                    if (!graphCache.isEmpty()) {
                        debug.log(DebugContext.VERBOSE_LEVEL, "Evict root %s", graph.method());
                        graphCache.removeKey(graph.method());

                        // Bailout may have been caused by an assumption on some inlined method.
                        for (ResolvedJavaMethod method : graph.getMethods()) {
                            EncodedGraph encodedGraph = graphCache.get(method);
                            if (encodedGraph == null) {
                                continue;
                            }

                            Assumptions assumptions = encodedGraph.getAssumptions();
                            if (assumptions != null && !assumptions.isEmpty()) {
                                debug.log(DebugContext.VERBOSE_LEVEL, "\tEvict inlined %s", method);
                                graphCache.removeKey(method);
                            }
                        }
                    }
                }
            }
        }
    }

    @FunctionalInterface
    interface StubCompilation {

        CompilationResult compile(DebugContext debug, ResolvedJavaMethod javaMethod, CompilationIdentifier compilationId);

    }
}
