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
import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.code.stack.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.runtime.*;
import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Implementation of the Truffle runtime when running on top of Graal.
 */
public final class HotSpotTruffleRuntime implements GraalTruffleRuntime {

    public static HotSpotTruffleRuntime makeInstance() {
        return new HotSpotTruffleRuntime();
    }

    private TruffleCompilerImpl truffleCompiler;
    private Replacements truffleReplacements;
    private StackIntrospection stackIntrospection;
    private ArrayList<String> includes;
    private ArrayList<String> excludes;
    private Map<OptimizedCallTarget, Future<?>> compilations = new IdentityHashMap<>();
    private final ThreadPoolExecutor compileQueue;

    private final ResolvedJavaMethod[] callNodeMethod;
    private final ResolvedJavaMethod[] callTargetMethod;
    private final ResolvedJavaMethod[] anyFrameMethod;

    private HotSpotTruffleRuntime() {
        installOptimizedCallTargetCallMethod();

        callNodeMethod = new ResolvedJavaMethod[]{getGraalProviders().getMetaAccess().lookupJavaMethod(HotSpotFrameInstance.CallNodeFrame.METHOD)};
        callTargetMethod = new ResolvedJavaMethod[]{getGraalProviders().getMetaAccess().lookupJavaMethod(HotSpotFrameInstance.CallTargetFrame.METHOD)};
        anyFrameMethod = new ResolvedJavaMethod[]{callNodeMethod[0], callTargetMethod[0]};

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
        compileQueue = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), factory);
    }

    public String getName() {
        return "Graal Truffle Runtime";
    }

    public RootCallTarget createCallTarget(RootNode rootNode) {
        CompilationPolicy compilationPolicy;
        if (acceptForCompilation(rootNode)) {
            compilationPolicy = new CounterBasedCompilationPolicy();
        } else {
            compilationPolicy = new InterpreterOnlyCompilationPolicy();
        }
        return new OptimizedCallTarget(rootNode, this, TruffleMinInvokeThreshold.getValue(), TruffleCompilationThreshold.getValue(), compilationPolicy, new HotSpotSpeculationLog());
    }

    public DirectCallNode createDirectCallNode(CallTarget target) {
        if (target instanceof OptimizedCallTarget) {
            return OptimizedDirectCallNode.create((OptimizedCallTarget) target);
        } else {
            return new DefaultDirectCallNode(target);
        }
    }

    public IndirectCallNode createIndirectCallNode() {
        return new OptimizedIndirectCallNode();
    }

    @Override
    public VirtualFrame createVirtualFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return OptimizedCallTarget.createFrame(frameDescriptor, arguments);
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Object[] arguments) {
        return createMaterializedFrame(arguments, new FrameDescriptor());
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Object[] arguments, FrameDescriptor frameDescriptor) {
        return new FrameWithoutBoxing(frameDescriptor, arguments);
    }

    @Override
    public Assumption createAssumption() {
        return createAssumption(null);
    }

    @Override
    public Assumption createAssumption(String name) {
        return new OptimizedAssumption(name);
    }

    public Replacements getReplacements() {
        if (truffleReplacements == null) {
            truffleReplacements = HotSpotTruffleReplacements.makeInstance();
        }
        return truffleReplacements;
    }

    private boolean acceptForCompilation(RootNode rootNode) {
        if (TruffleCompileOnly.getValue() != null) {
            if (includes == null) {
                parseCompileOnly();
            }

            String name = rootNode.toString();
            boolean included = includes.isEmpty();
            for (int i = 0; !included && i < includes.size(); i++) {
                if (name.contains(includes.get(i))) {
                    included = true;
                }
            }
            if (!included) {
                return false;
            }
            for (String exclude : excludes) {
                if (name.contains(exclude)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void parseCompileOnly() {
        includes = new ArrayList<>();
        excludes = new ArrayList<>();

        String[] items = TruffleCompileOnly.getValue().split(",");
        for (String item : items) {
            if (item.startsWith("~")) {
                excludes.add(item.substring(1));
            } else {
                includes.add(item);
            }
        }
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
                }
            }
        }
    }

    private static CompilationResultBuilderFactory getOptimizedCallTargetInstrumentationFactory(String arch, ResolvedJavaMethod method) {
        for (OptimizedCallTargetInstrumentationFactory factory : ServiceLoader.loadInstalled(OptimizedCallTargetInstrumentationFactory.class)) {
            if (factory.getArchitecture().equals(arch)) {
                factory.setInstrumentedMethod(method);
                return factory;
            }
        }
        // No specialization of OptimizedCallTarget on this platform.
        return CompilationResultBuilderFactory.Default;
    }

    private static CompilationResult compileMethod(ResolvedJavaMethod javaMethod) {
        Providers providers = getGraalProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        SuitesProvider suitesProvider = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites();
        Suites suites = suitesProvider.createSuites();
        suites.getHighTier().findPhase(InliningPhase.class).remove();
        StructuredGraph graph = new StructuredGraph(javaMethod);
        new GraphBuilderPhase.Instance(metaAccess, GraphBuilderConfiguration.getEagerDefault(), OptimisticOptimizations.ALL).apply(graph);
        PhaseSuite<HighTierContext> graphBuilderSuite = suitesProvider.getDefaultGraphBuilderSuite();
        CallingConvention cc = getCallingConvention(providers.getCodeCache(), Type.JavaCallee, graph.method(), false);
        Backend backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        CompilationResultBuilderFactory factory = getOptimizedCallTargetInstrumentationFactory(backend.getTarget().arch.getName(), javaMethod);
        return compileGraph(graph, null, cc, javaMethod, providers, backend, providers.getCodeCache().getTarget(), null, graphBuilderSuite, OptimisticOptimizations.ALL, getProfilingInfo(graph), null,
                        suites, new CompilationResult(), factory);
    }

    private static Providers getGraalProviders() {
        RuntimeProvider runtimeProvider = Graal.getRequiredCapability(RuntimeProvider.class);
        return runtimeProvider.getHostBackend().getProviders();
    }

    @SlowPath
    public Iterable<FrameInstance> getStackTrace() {
        if (stackIntrospection == null) {
            stackIntrospection = Graal.getRequiredCapability(StackIntrospection.class);
        }
        final Iterator<InspectedFrame> frames = stackIntrospection.getStackTrace(anyFrameMethod, anyFrameMethod, 1).iterator();
        class FrameIterator implements Iterator<FrameInstance> {

            public boolean hasNext() {
                return frames.hasNext();
            }

            public FrameInstance next() {
                InspectedFrame frame = frames.next();
                if (frame.getMethod().equals(callNodeMethod[0])) {
                    assert frames.hasNext();
                    InspectedFrame calltarget2 = frames.next();
                    assert calltarget2.getMethod().equals(callTargetMethod[0]);
                    return new HotSpotFrameInstance.CallNodeFrame(frame);
                } else {
                    assert frame.getMethod().equals(callTargetMethod[0]);
                    return new HotSpotFrameInstance.CallTargetFrame(frame, false);
                }
            }
        }
        return new Iterable<FrameInstance>() {
            public Iterator<FrameInstance> iterator() {
                return new FrameIterator();
            }
        };
    }

    public FrameInstance getCurrentFrame() {
        if (stackIntrospection == null) {
            stackIntrospection = Graal.getRequiredCapability(StackIntrospection.class);
        }
        Iterator<InspectedFrame> frames = stackIntrospection.getStackTrace(callTargetMethod, callTargetMethod, 0).iterator();
        if (frames.hasNext()) {
            return new HotSpotFrameInstance.CallTargetFrame(frames.next(), true);
        } else {
            System.out.println("no current frame found");
            return null;
        }
    }

    public void compile(OptimizedCallTarget optimizedCallTarget, boolean mayBeAsynchronous) {
        if (truffleCompiler == null) {
            truffleCompiler = new TruffleCompilerImpl();
        }
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try (Scope s = Debug.scope("Truffle", new TruffleDebugJavaMethod(optimizedCallTarget))) {
                    truffleCompiler.compileMethodImpl(optimizedCallTarget);
                    optimizedCallTarget.compilationFinished(null);
                } catch (Throwable e) {
                    optimizedCallTarget.compilationFinished(e);
                }
            }
        };
        if (mayBeAsynchronous) {
            Future<?> future = compileQueue.submit(r);
            this.compilations.put(optimizedCallTarget, future);
        } else {
            r.run();
        }
    }

    public boolean cancelInstalledTask(OptimizedCallTarget optimizedCallTarget) {
        Future<?> codeTask = this.compilations.get(optimizedCallTarget);
        if (codeTask != null && isCompiling(optimizedCallTarget)) {
            this.compilations.remove(optimizedCallTarget);
            return codeTask.cancel(true);
        }
        return false;
    }

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

    public void invalidateInstalledCode(OptimizedCallTarget optimizedCallTarget) {
        HotSpotGraalRuntime.runtime().getCompilerToVM().invalidateInstalledCode(optimizedCallTarget);
    }

    public void reinstallStubs() {
        installOptimizedCallTargetCallMethod();
    }
}
