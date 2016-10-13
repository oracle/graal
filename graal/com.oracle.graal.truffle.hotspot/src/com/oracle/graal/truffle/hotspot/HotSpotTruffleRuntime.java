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
package com.oracle.graal.truffle.hotspot;

import com.oracle.graal.api.runtime.GraalRuntime;
import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.DebugEnvironment;
import com.oracle.graal.debug.GraalDebugConfig;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.hotspot.GraalHotSpotVMConfig;
import com.oracle.graal.hotspot.HotSpotBackend;
import com.oracle.graal.hotspot.HotSpotCompiledCodeBuilder;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.AbstractInliningPhase;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;
import com.oracle.graal.phases.tiers.SuitesProvider;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.runtime.RuntimeProvider;
import com.oracle.graal.serviceprovider.GraalServices;
import com.oracle.graal.truffle.DefaultTruffleCompiler;
import com.oracle.graal.truffle.GraalTruffleRuntime;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.graal.truffle.TruffleCallBoundary;
import com.oracle.graal.truffle.TruffleCompiler;
import com.oracle.graal.truffle.hotspot.nfi.HotSpotNativeFunctionInterface;
import com.oracle.graal.truffle.hotspot.nfi.RawNativeCallNodeFactory;
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
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.runtime.JVMCI;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ListIterator;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.oracle.graal.compiler.GraalCompiler.compileGraph;
import static com.oracle.graal.hotspot.meta.HotSpotSuitesProvider.withNodeSourcePosition;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TraceTruffleStackTraceLimit;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TraceTruffleTransferToInterpreter;
import static com.oracle.graal.truffle.hotspot.UnsafeAccess.UNSAFE;

/**
 * Implementation of the Truffle runtime when running on top of Graal.
 */
public final class HotSpotTruffleRuntime extends GraalTruffleRuntime {

    static class Lazy extends BackgroundCompileQueue {
        private StackIntrospection stackIntrospection;

        Lazy(HotSpotTruffleRuntime runtime) {
            runtime.installDefaultListeners();
        }

        @Override
        public GraalDebugConfig getDebugConfig() {
            if (Debug.isEnabled()) {
                return DebugEnvironment.initialize(TTY.out().out());
            } else {
                return null;
            }
        }
    }

    public HotSpotTruffleRuntime(Supplier<GraalRuntime> graalRuntime) {
        super(graalRuntime);
        setDontInlineCallBoundaryMethod();
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

    private void initializeTruffleCompiler() {
        synchronized (this) {
            // might occur for multiple compiler threads at the same time.
            if (truffleCompiler == null) {
                truffleCompiler = DefaultTruffleCompiler.create(this);
            }
        }
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

    @SuppressWarnings("try")
    public void installOptimizedCallTargetCallMethod() {
        Providers providers = getHotSpotProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaType type = metaAccess.lookupJavaType(OptimizedCallTarget.class);
        for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
            if (method.getAnnotation(TruffleCallBoundary.class) != null) {
                CompilationResult compResult = compileMethod(method);
                CodeCacheProvider codeCache = providers.getCodeCache();
                try (Scope s = Debug.scope("CodeInstall", codeCache, method, compResult)) {
                    CompiledCode compiledCode = HotSpotCompiledCodeBuilder.createCompiledCode(method, null, compResult);
                    codeCache.setDefaultCode(method, compiledCode);
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            }
        }
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

    private CompilationResult compileMethod(ResolvedJavaMethod javaMethod) {
        HotSpotProviders providers = getHotSpotProviders();
        SuitesProvider suitesProvider = providers.getSuites();
        Suites suites = suitesProvider.getDefaultSuites().copy();
        LIRSuites lirSuites = suitesProvider.getDefaultLIRSuites();
        removeInliningPhase(suites);
        StructuredGraph graph = new StructuredGraph(javaMethod, AllowAssumptions.NO);

        MetaAccessProvider metaAccess = providers.getMetaAccess();
        Plugins plugins = new Plugins(new InvocationPlugins(metaAccess));
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
        if (TraceTruffleTransferToInterpreter.getValue()) {
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
        return new HotSpotNativeFunctionInterface(getHotSpotProviders(), factory, backend, config.dllLoad, config.dllLookup, config.rtldDefault);
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
            final int limit = TraceTruffleStackTraceLimit.getValue();

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
