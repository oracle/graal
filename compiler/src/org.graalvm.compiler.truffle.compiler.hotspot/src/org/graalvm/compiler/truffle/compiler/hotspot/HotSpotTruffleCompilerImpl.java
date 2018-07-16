/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.getOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompilationRequestIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Activation;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.hotspot.HotSpotCompilationIdentifier;
import org.graalvm.compiler.hotspot.HotSpotCompiledCodeBuilder;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.AbstractInliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompiler;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleInstalledCode;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotNmethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCICompiler;

public final class HotSpotTruffleCompilerImpl extends TruffleCompilerImpl implements HotSpotTruffleCompiler {

    /**
     * The HotSpot-specific Graal runtime associated with this compiler.
     */
    private final HotSpotGraalRuntimeProvider hotspotGraalRuntime;

    public static HotSpotTruffleCompilerImpl create(TruffleCompilerRuntime runtime) {
        HotSpotGraalRuntimeProvider hotspotGraalRuntime = (HotSpotGraalRuntimeProvider) runtime.getGraalRuntime();
        Backend backend = hotspotGraalRuntime.getHostBackend();
        OptionValues options = TruffleCompilerOptions.getOptions();
        Suites suites = backend.getSuites().getDefaultSuites(options);
        LIRSuites lirSuites = backend.getSuites().getDefaultLIRSuites(options);
        GraphBuilderPhase phase = (GraphBuilderPhase) backend.getSuites().getDefaultGraphBuilderSuite().findPhase(GraphBuilderPhase.class).previous();
        Plugins plugins = phase.getGraphBuilderConfig().getPlugins();
        SnippetReflectionProvider snippetReflection = hotspotGraalRuntime.getRequiredCapability(SnippetReflectionProvider.class);
        return new HotSpotTruffleCompilerImpl(hotspotGraalRuntime, runtime, plugins, suites, lirSuites, backend, snippetReflection);
    }

    private HotSpotTruffleCompilerImpl(HotSpotGraalRuntimeProvider hotspotGraalRuntime, TruffleCompilerRuntime runtime, Plugins plugins, Suites suites, LIRSuites lirSuites, Backend backend,
                    SnippetReflectionProvider snippetReflection) {
        super(runtime, plugins, suites, lirSuites, backend, snippetReflection);
        this.hotspotGraalRuntime = hotspotGraalRuntime;
        installTruffleCallBoundaryMethods();
    }

    @Override
    public CompilationRequestIdentifier getCompilationIdentifier(CompilableTruffleAST compilable) {
        ResolvedJavaMethod rootMethod = partialEvaluator.rootForCallTarget(compilable);
        HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) rootMethod, JVMCICompiler.INVOCATION_ENTRY_BCI, 0L);
        return new HotSpotTruffleCompilationIdentifier(request, compilable);
    }

    private volatile List<DebugHandlersFactory> factories;

    private List<DebugHandlersFactory> getDebugHandlerFactories() {
        if (factories == null) {
            // Multiple initialization by racing threads is harmless
            List<DebugHandlersFactory> list = new ArrayList<>();
            list.add(new GraalDebugHandlersFactory(snippetReflection));
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
    public DebugContext openDebugContext(OptionValues options, CompilationIdentifier compilationId, CompilableTruffleAST compilable) {
        return hotspotGraalRuntime.openDebugContext(options, compilationId, compilable, getDebugHandlerFactories());
    }

    @Override
    protected HotSpotPartialEvaluator createPartialEvaluator() {
        return new HotSpotPartialEvaluator(providers, config, snippetReflection, backend.getTarget().arch);
    }

    @Override
    public PhaseSuite<HighTierContext> createGraphBuilderSuite() {
        return backend.getSuites().getDefaultGraphBuilderSuite().copy();
    }

    /**
     * @see #compileTruffleCallBoundaryMethod
     */
    @Override
    @SuppressWarnings("try")
    public void installTruffleCallBoundaryMethods() {
        HotSpotTruffleCompilerRuntime runtime = (HotSpotTruffleCompilerRuntime) TruffleCompilerRuntime.getRuntime();
        for (ResolvedJavaMethod method : runtime.getTruffleCallBoundaryMethods()) {
            HotSpotCompilationIdentifier compilationId = (HotSpotCompilationIdentifier) backend.getCompilationIdentifier(method);
            OptionValues options = getOptions();
            try (DebugContext debug = DebugStubsAndSnippets.getValue(options) ? hotspotGraalRuntime.openDebugContext(options, compilationId, method, getDebugHandlerFactories())
                            : DebugContext.DISABLED;
                            Activation a = debug.activate();
                            DebugContext.Scope d = debug.scope("InstallingTruffleStub")) {
                CompilationResult compResult = compileTruffleCallBoundaryMethod(method, compilationId, debug);
                CodeCacheProvider codeCache = providers.getCodeCache();
                try (DebugContext.Scope s = debug.scope("CodeInstall", codeCache, method, compResult)) {
                    CompiledCode compiledCode = HotSpotCompiledCodeBuilder.createCompiledCode(codeCache, method, compilationId.getRequest(), compResult);
                    codeCache.setDefaultCode(method, compiledCode);
                } catch (Throwable e) {
                    throw debug.handle(e);
                }
            }
        }
    }

    @Override
    protected DiagnosticsOutputDirectory getDebugOutputDirectory() {
        return hotspotGraalRuntime.getOutputDirectory();
    }

    @Override
    protected Map<ExceptionAction, Integer> getCompilationProblemsPerAction() {
        return hotspotGraalRuntime.getCompilationProblemsPerAction();
    }

    private CompilationResultBuilderFactory getTruffleCallBoundaryInstrumentationFactory(String arch) {
        for (TruffleCallBoundaryInstrumentationFactory factory : GraalServices.load(TruffleCallBoundaryInstrumentationFactory.class)) {
            if (factory.getArchitecture().equals(arch)) {
                factory.init(providers.getMetaAccess(), hotspotGraalRuntime.getVMConfig(), hotspotGraalRuntime.getHostProviders().getRegisters());
                return factory;
            }
        }
        // No specialization of OptimizedCallTarget on this platform.
        return CompilationResultBuilderFactory.Default;
    }

    /**
     * Compiles a method denoted as a
     * {@linkplain HotSpotTruffleCompilerRuntime#getTruffleCallBoundaryMethods() Truffle call
     * boundary}. The compiled code has a special entry point generated by an
     * {@link TruffleCallBoundaryInstrumentationFactory}.
     */
    private CompilationResult compileTruffleCallBoundaryMethod(ResolvedJavaMethod javaMethod, CompilationIdentifier compilationId, DebugContext debug) {
        Suites newSuites = this.suites.copy();
        removeInliningPhase(newSuites);
        OptionValues options = TruffleCompilerOptions.getOptions();
        StructuredGraph graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.NO).method(javaMethod).compilationId(compilationId).build();

        MetaAccessProvider metaAccess = providers.getMetaAccess();
        Plugins plugins = new Plugins(new InvocationPlugins());
        HotSpotCodeCacheProvider codeCache = (HotSpotCodeCacheProvider) providers.getCodeCache();
        boolean infoPoints = codeCache.shouldDebugNonSafepoints();
        GraphBuilderConfiguration newConfig = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withUnresolvedIsError(true).withNodeSourcePosition(infoPoints);
        new GraphBuilderPhase.Instance(metaAccess, providers.getStampProvider(), providers.getConstantReflection(), providers.getConstantFieldProvider(), newConfig, OptimisticOptimizations.ALL,
                        null).apply(graph);
        PhaseSuite<HighTierContext> graphBuilderSuite = getGraphBuilderSuite(codeCache, backend.getSuites());
        CompilationResultBuilderFactory factory = getTruffleCallBoundaryInstrumentationFactory(backend.getTarget().arch.getName());
        return compileGraph(graph, javaMethod, providers, backend, graphBuilderSuite, OptimisticOptimizations.ALL, graph.getProfilingInfo(), newSuites, lirSuites, new CompilationResult(compilationId),
                        factory, false);
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
        return new HotSpotTruffleInstalledCode(compilable);
    }

    @Override
    protected void afterCodeInstallation(InstalledCode installedCode) {
        if (installedCode instanceof HotSpotTruffleInstalledCode) {
            HotSpotTruffleCompilerRuntime runtime = (HotSpotTruffleCompilerRuntime) TruffleCompilerRuntime.getRuntime();
            HotSpotTruffleInstalledCode hotspotTruffleInstalledCode = (HotSpotTruffleInstalledCode) installedCode;
            runtime.onCodeInstallation(hotspotTruffleInstalledCode);
        }
    }

    /**
     * {@link HotSpotNmethod#isDefault() Default} nmethods installed by Graal remain valid and can
     * still be executed once the associated {@link HotSpotNmethod} object becomes unreachable. As
     * such, these objects must remain strongly reachable from {@code OptimizedAssumption}s they
     * depend on.
     */
    @Override
    protected boolean reachabilityDeterminesValidity(InstalledCode installedCode) {
        if (installedCode instanceof HotSpotNmethod) {
            HotSpotNmethod nmethod = (HotSpotNmethod) installedCode;
            if (nmethod.isDefault()) {
                return false;
            }
        }
        return true;
    }
}
