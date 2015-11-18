/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.GraalCompiler.compileGraph;
import static com.oracle.graal.compiler.GraalCompiler.getProfilingInfo;
import static com.oracle.graal.hotspot.meta.HotSpotSuitesProvider.withSimpleDebugInfo;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TraceTruffleStackTraceLimit;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TraceTruffleTransferToInterpreter;
import static com.oracle.graal.truffle.hotspot.UnsafeAccess.UNSAFE;
import static jdk.vm.ci.code.CodeUtil.getCallingConvention;
import static jdk.vm.ci.hotspot.HotSpotVMConfig.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ListIterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.service.Services;

import com.oracle.graal.api.runtime.GraalRuntime;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.DebugEnvironment;
import com.oracle.graal.debug.GraalDebugConfig;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.hotspot.HotSpotBackend;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.inlining.InliningPhase;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;
import com.oracle.graal.phases.tiers.SuitesProvider;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.runtime.RuntimeProvider;
import com.oracle.graal.truffle.CompilationPolicy;
import com.oracle.graal.truffle.CounterAndTimeBasedCompilationPolicy;
import com.oracle.graal.truffle.DefaultTruffleCompiler;
import com.oracle.graal.truffle.GraalTruffleRuntime;
import com.oracle.graal.truffle.InterpreterOnlyCompilationPolicy;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.graal.truffle.TruffleCallBoundary;
import com.oracle.graal.truffle.TruffleCompiler;
import com.oracle.graal.truffle.TruffleTreeDumpHandler;
import com.oracle.graal.truffle.hotspot.nfi.HotSpotNativeFunctionInterface;
import com.oracle.graal.truffle.hotspot.nfi.RawNativeCallNodeFactory;
import com.oracle.nfi.api.NativeFunctionInterface;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Implementation of the Truffle runtime when running on top of Graal.
 */
public final class HotSpotTruffleRuntime extends GraalTruffleRuntime {

    private final Map<RootCallTarget, Void> callTargets = Collections.synchronizedMap(new WeakHashMap<RootCallTarget, Void>());

    static class Lazy extends BackgroundCompileQueue {
        private StackIntrospection stackIntrospection;

        public Lazy(HotSpotTruffleRuntime runtime) {
            runtime.installDefaultListeners();
        }

        @Override
        public GraalDebugConfig getDebugConfig() {
            if (Debug.isEnabled()) {
                GraalDebugConfig debugConfig = DebugEnvironment.initialize(TTY.out().out());
                debugConfig.dumpHandlers().add(new TruffleTreeDumpHandler());
                return debugConfig;
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
            truffleCompiler = DefaultTruffleCompiler.create(this);
        }
        return truffleCompiler;
    }

    @Override
    public RootCallTarget createCallTarget(RootNode rootNode) {
        return createCallTargetImpl(null, rootNode);
    }

    private RootCallTarget createCallTargetImpl(OptimizedCallTarget source, RootNode rootNode) {
        CompilationPolicy compilationPolicy;
        if (acceptForCompilation(rootNode)) {
            compilationPolicy = new CounterAndTimeBasedCompilationPolicy();
        } else {
            compilationPolicy = new InterpreterOnlyCompilationPolicy();
        }
        OptimizedCallTarget target = new OptimizedCallTarget(source, rootNode, this, compilationPolicy, new HotSpotSpeculationLog());
        rootNode.setCallTarget(target);
        callTargets.put(target, null);

        return target;
    }

    @Override
    public RootCallTarget createClonedCallTarget(OptimizedCallTarget source, RootNode root) {
        return createCallTargetImpl(source, root);
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
                try (Scope s = Debug.scope("CodeInstall", codeCache, method)) {
                    codeCache.setDefaultCode(method, compResult);
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            }
        }
    }

    private CompilationResultBuilderFactory getOptimizedCallTargetInstrumentationFactory(String arch) {
        for (OptimizedCallTargetInstrumentationFactory factory : Services.load(OptimizedCallTargetInstrumentationFactory.class)) {
            if (factory.getArchitecture().equals(arch)) {
                factory.init(config(), getHotSpotProviders().getRegisters());
                return factory;
            }
        }
        // No specialization of OptimizedCallTarget on this platform.
        return CompilationResultBuilderFactory.Default;
    }

    private CompilationResult compileMethod(ResolvedJavaMethod javaMethod) {
        HotSpotProviders providers = getHotSpotProviders();
        SuitesProvider suitesProvider = providers.getSuites();
        Suites suites = suitesProvider.createSuites();
        LIRSuites lirSuites = suitesProvider.createLIRSuites();
        removeInliningPhase(suites);
        StructuredGraph graph = new StructuredGraph(javaMethod, AllowAssumptions.NO);

        MetaAccessProvider metaAccess = providers.getMetaAccess();
        Plugins plugins = new Plugins(new InvocationPlugins(metaAccess));
        HotSpotCodeCacheProvider codeCache = providers.getCodeCache();
        boolean infoPoints = codeCache.shouldDebugNonSafepoints();
        GraphBuilderConfiguration config = infoPoints ? GraphBuilderConfiguration.getInfopointEagerDefault(plugins) : GraphBuilderConfiguration.getEagerDefault(plugins);
        new GraphBuilderPhase.Instance(metaAccess, providers.getStampProvider(), providers.getConstantReflection(), config, OptimisticOptimizations.ALL, null).apply(graph);

        PhaseSuite<HighTierContext> graphBuilderSuite = getGraphBuilderSuite(codeCache, suitesProvider);
        CallingConvention cc = getCallingConvention(codeCache, Type.JavaCallee, graph.method(), false);
        Backend backend = getHotSpotBackend();
        CompilationResultBuilderFactory factory = getOptimizedCallTargetInstrumentationFactory(backend.getTarget().arch.getName());
        return compileGraph(graph, cc, javaMethod, providers, backend, graphBuilderSuite, OptimisticOptimizations.ALL, getProfilingInfo(graph), suites, lirSuites, new CompilationResult(), factory);
    }

    private HotSpotBackend getHotSpotBackend() {
        RuntimeProvider runtimeProvider = getRequiredGraalCapability(RuntimeProvider.class);
        return (HotSpotBackend) runtimeProvider.getHostBackend();
    }

    private HotSpotProviders getHotSpotProviders() {
        return getHotSpotBackend().getProviders();
    }

    private static PhaseSuite<HighTierContext> getGraphBuilderSuite(CodeCacheProvider codeCache, SuitesProvider suitesProvider) {
        PhaseSuite<HighTierContext> graphBuilderSuite = suitesProvider.getDefaultGraphBuilderSuite();
        if (codeCache.shouldDebugNonSafepoints()) {
            graphBuilderSuite = withSimpleDebugInfo(graphBuilderSuite);
        }
        return graphBuilderSuite;
    }

    private static void removeInliningPhase(Suites suites) {
        ListIterator<BasePhase<? super HighTierContext>> inliningPhase = suites.getHighTier().findPhase(InliningPhase.class);
        if (inliningPhase != null) {
            inliningPhase.remove();
        }
    }

    @Override
    protected BackgroundCompileQueue getCompileQueue() {
        return lazy();
    }

    @Override
    public void compile(OptimizedCallTarget optimizedCallTarget, boolean mayBeAsynchronous) {
        /* Ensure compiler is created. */
        getTruffleCompiler();

        super.compile(optimizedCallTarget, mayBeAsynchronous);
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
    public Collection<RootCallTarget> getCallTargets() {
        return Collections.unmodifiableSet(callTargets.keySet());
    }

    @Override
    public void notifyTransferToInterpreter() {
        CompilerAsserts.neverPartOfCompilation();
        if (TraceTruffleTransferToInterpreter.getValue()) {
            TraceTraceTransferToInterpreterHelper.traceTransferToInterpreter();
        }
    }

    private static RawNativeCallNodeFactory getRawNativeCallNodeFactory(String arch) {
        for (RawNativeCallNodeFactory factory : Services.load(RawNativeCallNodeFactory.class)) {
            if (factory.getArchitecture().equals(arch)) {
                return factory;
            }
        }
        // No RawNativeCallNodeFactory on this platform.
        return null;
    }

    public NativeFunctionInterface createNativeFunctionInterface() {
        HotSpotVMConfig config = config();
        Backend backend = getHotSpotBackend();
        RawNativeCallNodeFactory factory = getRawNativeCallNodeFactory(backend.getTarget().arch.getName());
        if (factory == null) {
            return null;
        }
        return new HotSpotNativeFunctionInterface(getHotSpotProviders(), factory, backend, config.dllLoad, config.dllLookup, config.rtldDefault);
    }

    private static class TraceTraceTransferToInterpreterHelper {
        private static final long THREAD_EETOP_OFFSET;

        static {
            try {
                THREAD_EETOP_OFFSET = UNSAFE.objectFieldOffset(Thread.class.getDeclaredField("eetop"));
            } catch (Exception e) {
                throw new JVMCIError(e);
            }
        }

        static void traceTransferToInterpreter() {
            long thread = UNSAFE.getLong(Thread.currentThread(), THREAD_EETOP_OFFSET);
            long pendingTransferToInterpreterAddress = thread + config().pendingTransferToInterpreterOffset;
            boolean deoptimized = UNSAFE.getByte(pendingTransferToInterpreterAddress) != 0;
            if (deoptimized) {
                UNSAFE.putByte(pendingTransferToInterpreterAddress, (byte) 0);

                logTransferToInterpreter();
            }
        }

        private static void logTransferToInterpreter() {
            final int skip = 3;
            final int limit = TraceTruffleStackTraceLimit.getValue();
            StackTraceElement[] stackTrace = new Throwable().getStackTrace();
            String suffix = stackTrace.length > skip + limit ? "\n  ..." : "";
            TTY.out().out().println(Arrays.stream(stackTrace).skip(skip).limit(limit).map(StackTraceElement::toString).collect(Collectors.joining("\n  ", "", suffix)));
        }
    }
}
