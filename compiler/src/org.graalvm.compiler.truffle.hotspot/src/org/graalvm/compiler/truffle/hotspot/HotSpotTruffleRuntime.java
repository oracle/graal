/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.hotspot;

import static org.graalvm.compiler.core.GraalCompiler.compileGraph;
import static org.graalvm.compiler.debug.DebugOptions.DebugStubsAndSnippets;
import static org.graalvm.compiler.hotspot.meta.HotSpotSuitesProvider.withNodeSourcePosition;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TraceTruffleStackTraceLimit;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TraceTruffleTransferToInterpreter;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.getOptions;
import static org.graalvm.compiler.truffle.hotspot.UnsafeAccess.UNSAFE;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompilationRequestIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Activation;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotCompilationIdentifier;
import org.graalvm.compiler.hotspot.HotSpotCompiledCodeBuilder;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
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
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.TruffleCallBoundary;
import org.graalvm.compiler.truffle.TruffleCompiler;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.TruffleTreeDebugHandlersFactory;
import org.graalvm.compiler.truffle.hotspot.nfi.HotSpotNativeFunctionInterface;
import org.graalvm.compiler.truffle.hotspot.nfi.RawNativeCallNodeFactory;

import com.oracle.nfi.api.NativeFunctionInterface;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * Implementation of the Truffle runtime when running on top of Graal.
 */
public final class HotSpotTruffleRuntime extends GraalTruffleRuntime {

    static class Lazy extends BackgroundCompileQueue {
        StackIntrospection stackIntrospection;

        Lazy(HotSpotTruffleRuntime runtime) {
            runtime.installDefaultListeners();
        }
    }

    public HotSpotTruffleRuntime(Supplier<GraalRuntime> graalRuntime) {
        super(graalRuntime);
        setDontInlineCallBoundaryMethod();
    }

    @Override
    public OptionValues getInitialOptions() {
        return HotSpotGraalOptionValues.HOTSPOT_OPTIONS;
    }

    List<DebugHandlersFactory> factories;

    private List<DebugHandlersFactory> getDebugHandlerFactories() {
        if (factories == null) {
            // Multiple initialization by racing threads is harmless
            SnippetReflectionProvider snippetReflection = getRequiredGraalCapability(SnippetReflectionProvider.class);
            factories = Arrays.asList(new GraalDebugHandlersFactory(snippetReflection), new TruffleTreeDebugHandlersFactory());
        }
        return factories;
    }

    @Override
    protected DebugContext openDebugContext(OptionValues options, CompilationIdentifier compilationId, OptimizedCallTarget callTarget) {
        HotSpotGraalRuntimeProvider runtime = (HotSpotGraalRuntimeProvider) getRequiredGraalCapability(RuntimeProvider.class);
        return runtime.openDebugContext(options, compilationId, callTarget, getDebugHandlerFactories());
    }

    @Override
    public String getName() {
        return "Graal Truffle Runtime";
    }

    private volatile Lazy lazy;

    private Lazy lazy() {
        if (lazy == null) {
            synchronized (this) {
                if (lazy == null) {
                    lazy = new Lazy(this);
                }
            }
        }
        return lazy;
    }

    @Override
    protected StackIntrospection getStackIntrospection() {
        Lazy l = lazy();
        if (l.stackIntrospection == null) {
            l.stackIntrospection = HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getStackIntrospection();
        }
        return l.stackIntrospection;
    }

    @Override
    public TruffleCompiler getTruffleCompiler() {
        if (truffleCompiler == null) {
            initializeTruffleCompiler();
        }
        return truffleCompiler;
    }

    protected boolean reportedTruffleCompilerInitializationFailure;

    private void initializeTruffleCompiler() {
        synchronized (this) {
            // might occur for multiple compiler threads at the same time.
            if (truffleCompiler == null) {
                try {
                    truffleCompiler = HotSpotTruffleCompiler.create(this);
                } catch (Throwable e) {
                    if (!reportedTruffleCompilerInitializationFailure) {
                        // This should never happen so report it (once)
                        reportedTruffleCompilerInitializationFailure = true;
                        e.printStackTrace(TTY.out);
                    }
                }
            }
        }
    }

    @Override
    protected DiagnosticsOutputDirectory getDebugOutputDirectory() {
        HotSpotGraalRuntimeProvider runtime = (HotSpotGraalRuntimeProvider) getRequiredGraalCapability(RuntimeProvider.class);
        return runtime.getOutputDirectory();
    }

    @Override
    protected OptimizedCallTarget createOptimizedCallTarget(OptimizedCallTarget source, RootNode rootNode) {
        /* No HotSpot-specific subclass is currently necessary for call targets. */
        return new OptimizedCallTarget(source, rootNode);
    }

    @Override
    public SpeculationLog createSpeculationLog() {
        return new HotSpotSpeculationLog();
    }

    public static void setDontInlineCallBoundaryMethod() {
        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
        ResolvedJavaType type = metaAccess.lookupJavaType(OptimizedCallTarget.class);
        for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
            if (method.getAnnotation(TruffleCallBoundary.class) != null) {
                ((HotSpotResolvedJavaMethod) method).setNotInlineable();
            }
        }
    }

    /**
     * Compiles and installs code for {@link OptimizedCallTarget#callBoundary}.
     *
     * @see HotSpotTruffleRuntime#compileTruffleCallBoundaryMethod
     */
    @SuppressWarnings("try")
    public void installOptimizedCallTargetCallMethod() {
        Providers providers = getHotSpotProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaType type = metaAccess.lookupJavaType(OptimizedCallTarget.class);
        for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
            if (method.getAnnotation(TruffleCallBoundary.class) != null) {

                HotSpotGraalRuntimeProvider runtime = (HotSpotGraalRuntimeProvider) getRequiredGraalCapability(RuntimeProvider.class);
                HotSpotCompilationIdentifier compilationId = (HotSpotCompilationIdentifier) getHotSpotBackend().getCompilationIdentifier(method);
                OptionValues options = getOptions();
                try (DebugContext debug = DebugStubsAndSnippets.getValue(options) ? runtime.openDebugContext(options, compilationId, method, getDebugHandlerFactories()) : DebugContext.DISABLED;
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
    }

    @Override
    public CompilationRequestIdentifier getCompilationIdentifier(OptimizedCallTarget optimizedCallTarget, ResolvedJavaMethod callRootMethod, Backend backend) {
        HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) callRootMethod, JVMCICompiler.INVOCATION_ENTRY_BCI, 0L);
        return new HotSpotTruffleCompilationIdentifier(request, optimizedCallTarget);
    }

    private CompilationResultBuilderFactory getOptimizedCallTargetInstrumentationFactory(String arch) {
        for (OptimizedCallTargetInstrumentationFactory factory : GraalServices.load(OptimizedCallTargetInstrumentationFactory.class)) {
            if (factory.getArchitecture().equals(arch)) {
                factory.init(getVMConfig(), getHotSpotProviders().getRegisters());
                return factory;
            }
        }
        // No specialization of OptimizedCallTarget on this platform.
        return CompilationResultBuilderFactory.Default;
    }

    /**
     * Compiles a method annotated by {@link TruffleCallBoundary}. The compiled code has a special
     * entry point generated by an {@link OptimizedCallTargetInstrumentationFactory}.
     */
    private CompilationResult compileTruffleCallBoundaryMethod(ResolvedJavaMethod javaMethod, CompilationIdentifier compilationId, DebugContext debug) {
        HotSpotProviders providers = getHotSpotProviders();
        SuitesProvider suitesProvider = providers.getSuites();
        OptionValues options = getOptions();
        Suites suites = suitesProvider.getDefaultSuites(options).copy();
        LIRSuites lirSuites = suitesProvider.getDefaultLIRSuites(options);
        removeInliningPhase(suites);
        StructuredGraph graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.NO).method(javaMethod).compilationId(compilationId).build();

        MetaAccessProvider metaAccess = providers.getMetaAccess();
        Plugins plugins = new Plugins(new InvocationPlugins());
        HotSpotCodeCacheProvider codeCache = providers.getCodeCache();
        boolean infoPoints = codeCache.shouldDebugNonSafepoints();
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withNodeSourcePosition(infoPoints);
        new GraphBuilderPhase.Instance(metaAccess, providers.getStampProvider(), providers.getConstantReflection(), providers.getConstantFieldProvider(), config, OptimisticOptimizations.ALL,
                        null).apply(graph);

        PhaseSuite<HighTierContext> graphBuilderSuite = getGraphBuilderSuite(codeCache, suitesProvider);
        Backend backend = getHotSpotBackend();
        CompilationResultBuilderFactory factory = getOptimizedCallTargetInstrumentationFactory(backend.getTarget().arch.getName());
        return compileGraph(graph, javaMethod, providers, backend, graphBuilderSuite, OptimisticOptimizations.ALL, graph.getProfilingInfo(), suites, lirSuites, new CompilationResult(), factory);
    }

    private HotSpotBackend getHotSpotBackend() {
        RuntimeProvider runtimeProvider = getRequiredGraalCapability(RuntimeProvider.class);
        return (HotSpotBackend) runtimeProvider.getHostBackend();
    }

    private GraalHotSpotVMConfig getVMConfig() {
        RuntimeProvider runtimeProvider = getRequiredGraalCapability(RuntimeProvider.class);
        return ((HotSpotGraalRuntimeProvider) runtimeProvider).getVMConfig();
    }

    private HotSpotProviders getHotSpotProviders() {
        return getHotSpotBackend().getProviders();
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
    protected BackgroundCompileQueue getCompileQueue() {
        return lazy();
    }

    @Override
    public boolean cancelInstalledTask(OptimizedCallTarget optimizedCallTarget, Object source, CharSequence reason) {
        if (lazy == null) {
            // if truffle wasn't initialized yet, this is a noop
            return false;
        }

        return super.cancelInstalledTask(optimizedCallTarget, source, reason);
    }

    private static CodeCacheProvider getCodeCache() {
        return JVMCI.getRuntime().getHostJVMCIBackend().getCodeCache();
    }

    @Override
    public void invalidateInstalledCode(OptimizedCallTarget optimizedCallTarget, Object source, CharSequence reason) {
        getCodeCache().invalidateInstalledCode(optimizedCallTarget);
        getCompilationNotify().notifyCompilationInvalidated(optimizedCallTarget, source, reason);
    }

    @SuppressWarnings("try")
    @Override
    public void reinstallStubs() {
        installOptimizedCallTargetCallMethod();
    }

    @Override
    protected boolean platformEnableInfopoints() {
        return getCodeCache().shouldDebugNonSafepoints();
    }

    @Override
    protected CallMethods getCallMethods() {
        if (callMethods == null) {
            lookupCallMethods(JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess());
        }
        return callMethods;
    }

    @Override
    public void notifyTransferToInterpreter() {
        CompilerAsserts.neverPartOfCompilation();
        if (TruffleCompilerOptions.getValue(TraceTruffleTransferToInterpreter)) {
            TraceTransferToInterpreterHelper.traceTransferToInterpreter(this, getVMConfig());
        }
    }

    private static RawNativeCallNodeFactory getRawNativeCallNodeFactory(String arch) {
        for (RawNativeCallNodeFactory factory : GraalServices.load(RawNativeCallNodeFactory.class)) {
            if (factory.getArchitecture().equals(arch)) {
                return factory;
            }
        }
        // No RawNativeCallNodeFactory on this platform.
        return null;
    }

    NativeFunctionInterface createNativeFunctionInterface() {
        GraalHotSpotVMConfig config = getVMConfig();
        Backend backend = getHotSpotBackend();
        RawNativeCallNodeFactory factory = getRawNativeCallNodeFactory(backend.getTarget().arch.getName());
        if (factory == null) {
            return null;
        }

        return new HotSpotNativeFunctionInterface(getOptions(), getHotSpotProviders(), factory, backend, config.dllLoad, config.dllLookup, config.rtldDefault);
    }

    private static class TraceTransferToInterpreterHelper {
        private static final long THREAD_EETOP_OFFSET;

        static {
            try {
                THREAD_EETOP_OFFSET = UNSAFE.objectFieldOffset(Thread.class.getDeclaredField("eetop"));
            } catch (Exception e) {
                throw new GraalError(e);
            }
        }

        static void traceTransferToInterpreter(HotSpotTruffleRuntime runtime, GraalHotSpotVMConfig config) {
            long thread = UNSAFE.getLong(Thread.currentThread(), THREAD_EETOP_OFFSET);
            long pendingTransferToInterpreterAddress = thread + config.pendingTransferToInterpreterOffset;
            boolean deoptimized = UNSAFE.getByte(pendingTransferToInterpreterAddress) != 0;
            if (deoptimized) {
                logTransferToInterpreter(runtime);
                UNSAFE.putByte(pendingTransferToInterpreterAddress, (byte) 0);
            }
        }

        private static String formatStackFrame(FrameInstance frameInstance, CallTarget target) {
            StringBuilder builder = new StringBuilder();
            if (target instanceof RootCallTarget) {
                RootNode root = ((RootCallTarget) target).getRootNode();
                String name = root.getName();
                if (name == null) {
                    builder.append("unnamed-root");
                } else {
                    builder.append(name);
                }
                Node callNode = frameInstance.getCallNode();
                SourceSection sourceSection = null;
                if (callNode != null) {
                    sourceSection = callNode.getEncapsulatingSourceSection();
                }
                if (sourceSection == null) {
                    sourceSection = root.getSourceSection();
                }

                if (sourceSection == null || sourceSection.getSource() == null) {
                    builder.append("(Unknown)");
                } else {
                    builder.append("(").append(formatPath(sourceSection)).append(":").append(sourceSection.getStartLine()).append(")");
                }

                if (target instanceof OptimizedCallTarget) {
                    OptimizedCallTarget callTarget = ((OptimizedCallTarget) target);
                    if (callTarget.isValid()) {
                        builder.append(" <opt>");
                    }
                    if (callTarget.getSourceCallTarget() != null) {
                        builder.append(" <split-" + Integer.toHexString(callTarget.hashCode()) + ">");
                    }
                }

            } else {
                builder.append(target.toString());
            }
            return builder.toString();
        }

        private static String formatPath(SourceSection sourceSection) {
            if (sourceSection.getSource().getPath() != null) {
                Path path = FileSystems.getDefault().getPath(".").toAbsolutePath();
                Path filePath = FileSystems.getDefault().getPath(sourceSection.getSource().getPath()).toAbsolutePath();

                try {
                    return path.relativize(filePath).toString();
                } catch (IllegalArgumentException e) {
                    // relativization failed
                }
            }
            return sourceSection.getSource().getName();
        }

        private static void logTransferToInterpreter(final HotSpotTruffleRuntime runtime) {
            final int limit = TruffleCompilerOptions.getValue(TraceTruffleStackTraceLimit);

            runtime.log("[truffle] transferToInterpreter at");
            runtime.iterateFrames(new FrameInstanceVisitor<Object>() {
                int frameIndex = 0;

                @Override
                public Object visitFrame(FrameInstance frameInstance) {
                    CallTarget target = frameInstance.getCallTarget();
                    StringBuilder line = new StringBuilder("  ");
                    if (frameIndex > 0) {
                        line.append("  ");
                    }
                    line.append(formatStackFrame(frameInstance, target));
                    frameIndex++;

                    runtime.log(line.toString());
                    if (frameIndex < limit) {
                        return null;
                    } else {
                        runtime.log("    ...");
                        return frameInstance;
                    }
                }

            });
            final int skip = 3;

            StackTraceElement[] stackTrace = new Throwable().getStackTrace();
            String suffix = stackTrace.length > skip + limit ? "\n    ..." : "";
            runtime.log(Arrays.stream(stackTrace).skip(skip).limit(limit).map(StackTraceElement::toString).collect(Collectors.joining("\n    ", "  ", suffix)));
        }
    }
}
