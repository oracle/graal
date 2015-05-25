/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.compiler.GraalCompiler.*;
import static com.oracle.graal.graph.util.CollectionsAccess.*;
import static com.oracle.graal.hotspot.meta.HotSpotSuitesProvider.*;
import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.jvmci.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.runtime.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.hotspot.nfi.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.runtime.*;
import com.oracle.nfi.api.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Implementation of the Truffle runtime when running on top of Graal.
 */
public final class HotSpotTruffleRuntime extends GraalTruffleRuntime {

    public static TruffleRuntime makeInstance() {
        if (GraalTruffleRuntime.alternateRuntime != null) {
            return GraalTruffleRuntime.alternateRuntime;
        }
        return new HotSpotTruffleRuntime();
    }

    private Map<OptimizedCallTarget, Future<?>> compilations = newIdentityMap();
    private final ThreadPoolExecutor compileQueue;

    private final Map<RootCallTarget, Void> callTargets = Collections.synchronizedMap(new WeakHashMap<RootCallTarget, Void>());
    private static final long THREAD_EETOP_OFFSET = eetopOffset();

    private HotSpotTruffleRuntime() {
        installOptimizedCallTargetCallMethod();
        lookupCallMethods(getGraalProviders().getMetaAccess());

        installDefaultListeners();

        // Create compilation queue.
        CompilerThreadFactory factory = new CompilerThreadFactory("TruffleCompilerThread", new CompilerThreadFactory.DebugConfigAccess() {
            public GraalDebugConfig getDebugConfig() {
                if (Debug.isEnabled()) {
                    GraalDebugConfig debugConfig = DebugEnvironment.initialize(TTY.out().out());
                    debugConfig.dumpHandlers().add(new TruffleTreeDumpHandler());
                    return debugConfig;
                } else {
                    return null;
                }
            }
        });
        int selectedProcessors = TruffleCompilerOptions.TruffleCompilerThreads.getValue();
        if (selectedProcessors == 0) {
            // No manual selection made, check how many processors are available.
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            if (availableProcessors >= 4) {
                selectedProcessors = 2;
            } else if (availableProcessors >= 12) {
                selectedProcessors = 4;
            }
        }
        selectedProcessors = Math.max(1, selectedProcessors);
        compileQueue = new ThreadPoolExecutor(selectedProcessors, selectedProcessors, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), factory);

    }

    @Override
    public String getName() {
        return "Graal Truffle Runtime";
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

    public static void installOptimizedCallTargetCallMethod() {
        Providers providers = getGraalProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaType type = metaAccess.lookupJavaType(OptimizedCallTarget.class);
        for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
            if (method.getAnnotation(TruffleCallBoundary.class) != null) {
                CompilationResult compResult = compileMethod(method);
                CodeCacheProvider codeCache = providers.getCodeCache();
                try (Scope s = Debug.scope("CodeInstall", codeCache, method)) {
                    codeCache.setDefaultMethod(method, compResult);
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            }
        }
    }

    private static CompilationResultBuilderFactory getOptimizedCallTargetInstrumentationFactory(String arch, ResolvedJavaMethod method) {
        for (OptimizedCallTargetInstrumentationFactory factory : Services.load(OptimizedCallTargetInstrumentationFactory.class)) {
            if (factory.getArchitecture().equals(arch)) {
                factory.setInstrumentedMethod(method);
                return factory;
            }
        }
        // No specialization of OptimizedCallTarget on this platform.
        return CompilationResultBuilderFactory.Default;
    }

    private static CompilationResult compileMethod(ResolvedJavaMethod javaMethod) {
        HotSpotProviders providers = getGraalProviders();
        SuitesProvider suitesProvider = providers.getSuites();
        Suites suites = suitesProvider.createSuites();
        LIRSuites lirSuites = suitesProvider.createLIRSuites();
        removeInliningPhase(suites);
        StructuredGraph graph = new StructuredGraph(javaMethod, AllowAssumptions.NO);

        MetaAccessProvider metaAccess = providers.getMetaAccess();
        Plugins plugins = new Plugins(new InvocationPlugins(metaAccess));
        boolean infoPoints = HotSpotGraalRuntime.runtime().getCompilerToVM().shouldDebugNonSafepoints();
        GraphBuilderConfiguration config = infoPoints ? GraphBuilderConfiguration.getInfopointEagerDefault(plugins) : GraphBuilderConfiguration.getEagerDefault(plugins);
        new GraphBuilderPhase.Instance(metaAccess, providers.getStampProvider(), providers.getConstantReflection(), config, OptimisticOptimizations.ALL, null).apply(graph);

        PhaseSuite<HighTierContext> graphBuilderSuite = getGraphBuilderSuite(suitesProvider);
        CallingConvention cc = getCallingConvention(providers.getCodeCache(), Type.JavaCallee, graph.method(), false);
        Backend backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        CompilationResultBuilderFactory factory = getOptimizedCallTargetInstrumentationFactory(backend.getTarget().arch.getName(), javaMethod);
        return compileGraph(graph, cc, javaMethod, providers, backend, providers.getCodeCache().getTarget(), graphBuilderSuite, OptimisticOptimizations.ALL, getProfilingInfo(graph), null, suites,
                        lirSuites, new CompilationResult(), factory);
    }

    private static HotSpotProviders getGraalProviders() {
        RuntimeProvider runtimeProvider = Graal.getRequiredCapability(RuntimeProvider.class);
        return (HotSpotProviders) runtimeProvider.getHostBackend().getProviders();
    }

    private static PhaseSuite<HighTierContext> getGraphBuilderSuite(SuitesProvider suitesProvider) {
        PhaseSuite<HighTierContext> graphBuilderSuite = suitesProvider.getDefaultGraphBuilderSuite();
        return withSimpleDebugInfoIfRequested(graphBuilderSuite);
    }

    private static void removeInliningPhase(Suites suites) {
        ListIterator<BasePhase<? super HighTierContext>> inliningPhase = suites.getHighTier().findPhase(InliningPhase.class);
        if (inliningPhase != null) {
            inliningPhase.remove();
        }
    }

    @Override
    public void compile(OptimizedCallTarget optimizedCallTarget, boolean mayBeAsynchronous) {
        if (truffleCompiler == null) {
            truffleCompiler = DefaultTruffleCompiler.create();
        }
        Runnable r = new Runnable() {
            @Override
            public void run() {
                doCompile(optimizedCallTarget);
            }
        };
        Future<?> future = compileQueue.submit(r);
        this.compilations.put(optimizedCallTarget, future);
        getCompilationNotify().notifyCompilationQueued(optimizedCallTarget);

        if (!mayBeAsynchronous) {
            try {
                future.get();
            } catch (ExecutionException e) {
                if (TruffleCompilationExceptionsAreThrown.getValue() && !(e.getCause() instanceof BailoutException && !((BailoutException) e.getCause()).isPermanent())) {
                    throw new RuntimeException(e.getCause());
                } else {
                    // silently ignored
                }
            } catch (InterruptedException | CancellationException e) {
                // silently ignored
            }
        }
    }

    @Override
    public boolean cancelInstalledTask(OptimizedCallTarget optimizedCallTarget, Object source, CharSequence reason) {
        Future<?> codeTask = this.compilations.get(optimizedCallTarget);
        if (codeTask != null && isCompiling(optimizedCallTarget)) {
            this.compilations.remove(optimizedCallTarget);
            boolean result = codeTask.cancel(true);
            if (result) {
                optimizedCallTarget.notifyCompilationFinished(false);
                getCompilationNotify().notifyCompilationDequeued(optimizedCallTarget, source, reason);
            }
            return result;
        }
        return false;
    }

    @Override
    public void waitForCompilation(OptimizedCallTarget optimizedCallTarget, long timeout) throws ExecutionException, TimeoutException {
        Future<?> codeTask = this.compilations.get(optimizedCallTarget);
        if (codeTask != null && isCompiling(optimizedCallTarget)) {
            try {
                codeTask.get(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // ignore interrupted
            }
        }
    }

    @Override
    public Collection<OptimizedCallTarget> getQueuedCallTargets() {
        return compilations.keySet().stream().filter(e -> !compilations.get(e).isDone()).collect(Collectors.toList());
    }

    @Override
    public boolean isCompiling(OptimizedCallTarget optimizedCallTarget) {
        Future<?> codeTask = this.compilations.get(optimizedCallTarget);
        if (codeTask != null) {
            if (codeTask.isCancelled() || codeTask.isDone()) {
                this.compilations.remove(optimizedCallTarget);
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void invalidateInstalledCode(OptimizedCallTarget optimizedCallTarget, Object source, CharSequence reason) {
        HotSpotGraalRuntime.runtime().getCompilerToVM().invalidateInstalledCode(optimizedCallTarget);
        getCompilationNotify().notifyCompilationInvalidated(optimizedCallTarget, source, reason);
    }

    @Override
    public void reinstallStubs() {
        installOptimizedCallTargetCallMethod();
    }

    @Override
    public boolean platformEnableInfopoints() {
        return HotSpotGraalRuntime.runtime().getCompilerToVM().shouldDebugNonSafepoints();
    }

    @Override
    public Collection<RootCallTarget> getCallTargets() {
        return Collections.unmodifiableSet(callTargets.keySet());
    }

    @Override
    public void notifyTransferToInterpreter() {
        CompilerAsserts.neverPartOfCompilation();
        if (TraceTruffleTransferToInterpreter.getValue()) {
            long thread = UnsafeAccess.unsafe.getLong(Thread.currentThread(), THREAD_EETOP_OFFSET);
            long pendingTransferToInterpreterAddress = thread + HotSpotGraalRuntime.runtime().getConfig().pendingTransferToInterpreterOffset;
            boolean deoptimized = UnsafeAccess.unsafe.getByte(pendingTransferToInterpreterAddress) != 0;
            if (deoptimized) {
                UnsafeAccess.unsafe.putByte(pendingTransferToInterpreterAddress, (byte) 0);

                logTransferToInterpreter();
            }
        }
    }

    private static void logTransferToInterpreter() {
        final int skip = 2;
        final int limit = TraceTruffleStackTraceLimit.getValue();
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        String suffix = stackTrace.length > skip + limit ? "\n  ..." : "";
        TTY.out().out().println(Arrays.stream(stackTrace).skip(skip).limit(limit).map(StackTraceElement::toString).collect(Collectors.joining("\n  ", "", suffix)));
    }

    private static long eetopOffset() {
        try {
            return UnsafeAccess.unsafe.objectFieldOffset(Thread.class.getDeclaredField("eetop"));
        } catch (Exception e) {
            throw new GraalInternalError(e);
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

    /**
     * Called from the VM.
     */
    public static NativeFunctionInterface createNativeFunctionInterface() {
        HotSpotVMConfig config = HotSpotGraalRuntime.runtime().getConfig();
        Backend backend = HotSpotGraalRuntime.runtime().getHostBackend();
        RawNativeCallNodeFactory factory = getRawNativeCallNodeFactory(backend.getTarget().arch.getName());
        if (factory == null) {
            return null;
        }
        return new HotSpotNativeFunctionInterface(HotSpotGraalRuntime.runtime().getHostProviders(), factory, backend, config.dllLoad, config.dllLookup, config.rtldDefault);
    }
}
