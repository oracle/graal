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
package com.oracle.graal.truffle;

import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleCompilationExceptionsAreThrown;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleCompileOnly;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleEnableInfopoints;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.stack.InspectedFrame;
import jdk.vm.ci.code.stack.InspectedFrameVisitor;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;

import com.oracle.graal.api.runtime.GraalRuntime;
import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.CompilerThreadFactory;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.truffle.debug.CompilationStatisticsListener;
import com.oracle.graal.truffle.debug.PrintCallTargetProfiling;
import com.oracle.graal.truffle.debug.TraceCompilationCallTreeListener;
import com.oracle.graal.truffle.debug.TraceCompilationFailureListener;
import com.oracle.graal.truffle.debug.TraceCompilationListener;
import com.oracle.graal.truffle.debug.TraceCompilationPolymorphismListener;
import com.oracle.graal.truffle.debug.TraceInliningListener;
import com.oracle.graal.truffle.debug.TraceSplittingListener;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class GraalTruffleRuntime implements TruffleRuntime {

    protected abstract static class BackgroundCompileQueue implements CompilerThreadFactory.DebugConfigAccess {
        private final ExecutorService compileQueue;

        protected BackgroundCompileQueue() {
            CompilerThreadFactory factory = new CompilerThreadFactory("TruffleCompilerThread", this);

            int selectedProcessors = TruffleCompilerOptions.TruffleCompilerThreads.getValue();
            if (selectedProcessors == 0) {
                // No manual selection made, check how many processors are available.
                int availableProcessors = Runtime.getRuntime().availableProcessors();
                if (availableProcessors >= 12) {
                    selectedProcessors = 4;
                } else if (availableProcessors >= 4) {
                    selectedProcessors = 2;
                }
            }
            selectedProcessors = Math.max(1, selectedProcessors);
            compileQueue = Executors.newFixedThreadPool(selectedProcessors, factory);
        }
    }

    private ArrayList<String> includes;
    private ArrayList<String> excludes;

    private final List<GraalTruffleCompilationListener> compilationListeners = new ArrayList<>();
    private final GraalTruffleCompilationListener compilationNotify = new DispatchTruffleCompilationListener();

    protected TruffleCompiler truffleCompiler;
    protected LoopNodeFactory loopNodeFactory;
    protected CallMethods callMethods;

    private final Supplier<GraalRuntime> graalRuntime;

    public GraalTruffleRuntime(Supplier<GraalRuntime> graalRuntime) {
        this.graalRuntime = graalRuntime;
    }

    public abstract TruffleCompiler getTruffleCompiler();

    public <T> T getRequiredGraalCapability(Class<T> clazz) {
        T ret = graalRuntime.get().getCapability(clazz);
        if (ret == null) {
            throw new JVMCIError("The VM does not expose the required Graal capability %s.", clazz.getName());
        }
        return ret;
    }

    private static <T extends PrioritizedServiceProvider> T loadPrioritizedServiceProvider(Class<T> clazz) {
        Iterable<T> providers = Services.load(clazz);
        T bestFactory = null;
        for (T factory : providers) {
            if (bestFactory == null) {
                bestFactory = factory;
            } else if (factory.getPriority() > bestFactory.getPriority()) {
                bestFactory = factory;
            }
        }
        if (bestFactory == null) {
            throw new IllegalStateException("Unable to load a factory for " + clazz.getName());
        }
        return bestFactory;
    }

    public void log(String message) {
        TTY.out().println(message);
    }

    protected void installDefaultListeners() {
        TraceCompilationFailureListener.install(this);
        TraceCompilationListener.install(this);
        TraceCompilationPolymorphismListener.install(this);
        TraceCompilationCallTreeListener.install(this);
        TraceInliningListener.install(this);
        TraceSplittingListener.install(this);
        PrintCallTargetProfiling.install(this);
        CompilationStatisticsListener.install(this);
        installShutdownHooks();
        compilationNotify.notifyStartup(this);
    }

    protected void installShutdownHooks() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    protected void lookupCallMethods(MetaAccessProvider metaAccess) {
        callMethods = CallMethods.lookup(metaAccess);
    }

    @Override
    public LoopNode createLoopNode(RepeatingNode repeatingNode) {
        if (!(repeatingNode instanceof Node)) {
            throw new IllegalArgumentException("Repeating node must be of type Node.");
        }
        return getLoopNodeFactory().create(repeatingNode);
    }

    protected LoopNodeFactory getLoopNodeFactory() {
        if (loopNodeFactory == null) {
            loopNodeFactory = loadPrioritizedServiceProvider(LoopNodeFactory.class);
        }
        return loopNodeFactory;
    }

    @Override
    public DirectCallNode createDirectCallNode(CallTarget target) {
        if (target instanceof OptimizedCallTarget) {
            return new OptimizedDirectCallNode(this, (OptimizedCallTarget) target);
        } else {
            throw new IllegalStateException(String.format("Unexpected call target class %s!", target.getClass()));
        }
    }

    @Override
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
        if (TruffleCompilerOptions.TruffleUseFrameWithoutBoxing.getValue()) {
            return new FrameWithoutBoxing(frameDescriptor, arguments);
        } else {
            return new FrameWithBoxing(frameDescriptor, arguments);
        }
    }

    @Override
    public CompilerOptions createCompilerOptions() {
        return new GraalCompilerOptions();
    }

    @Override
    public Assumption createAssumption() {
        return createAssumption(null);
    }

    @Override
    public Assumption createAssumption(String name) {
        return new OptimizedAssumption(name);
    }

    public GraalTruffleCompilationListener getCompilationNotify() {
        return compilationNotify;
    }

    @TruffleBoundary
    @Override
    public <T> T iterateFrames(final FrameInstanceVisitor<T> visitor) {
        return iterateImpl(visitor, 0);
    }

    private static final class FrameVisitor<T> implements InspectedFrameVisitor<T> {

        private final FrameInstanceVisitor<T> visitor;
        private final ResolvedJavaMethod callTargetMethod;
        private final ResolvedJavaMethod callNodeMethod;

        private GraalFrameInstance next;
        private boolean nextAvailable;
        private boolean first = true;
        private int skipFrames;

        FrameVisitor(FrameInstanceVisitor<T> visitor, CallMethods methods, int skip) {
            this.visitor = visitor;
            this.callTargetMethod = methods.callTargetMethod;
            this.callNodeMethod = methods.callNodeMethod;
            this.skipFrames = skip;
        }

        public T visitFrame(InspectedFrame frame) {
            if (nextAvailable) {
                T result = onNext(frame);
                if (result != null) {
                    return result;
                }
            }
            if (frame.isMethod(callTargetMethod)) {
                nextAvailable = true;
                if (skipFrames == 0) {
                    GraalFrameInstance graalFrame = new GraalFrameInstance(first);
                    graalFrame.setCallTargetFrame(frame);
                    next = graalFrame;
                }
                first = false;
            }
            return null;
        }

        private T onNext(InspectedFrame frame) {
            try {
                if (skipFrames == 0) {
                    if (frame != null && frame.isMethod(callNodeMethod)) {
                        next.setCallNodeFrame(frame);
                    }
                    return visitor.visitFrame(next);
                } else {
                    skipFrames--;
                    return null;
                }
            } finally {
                next = null;
                nextAvailable = false;
            }
        }

        /* Method to collect the last result. */
        public T afterVisitation() {
            if (nextAvailable) {
                return onNext(null);
            } else {
                return null;
            }
        }

    }

    private <T> T iterateImpl(FrameInstanceVisitor<T> visitor, final int skip) {
        CallMethods methods = getCallMethods();
        FrameVisitor<T> jvmciVisitor = new FrameVisitor<>(visitor, methods, skip);
        T result = getStackIntrospection().iterateFrames(methods.anyFrameMethod, methods.anyFrameMethod, 0, jvmciVisitor);
        if (result != null) {
            return result;
        } else {
            return jvmciVisitor.afterVisitation();
        }
    }

    protected abstract StackIntrospection getStackIntrospection();

    @Override
    public FrameInstance getCallerFrame() {
        return iterateImpl(frame -> frame, 1);
    }

    @TruffleBoundary
    @Override
    public FrameInstance getCurrentFrame() {
        return iterateImpl(frame -> frame, 0);
    }

    public <T> T getCapability(Class<T> capability) {
        return null;
    }

    protected boolean acceptForCompilation(RootNode rootNode) {
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

    protected void parseCompileOnly() {
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

    public abstract RootCallTarget createClonedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode root);

    public void addCompilationListener(GraalTruffleCompilationListener listener) {
        compilationListeners.add(listener);
    }

    public void removeCompilationListener(GraalTruffleCompilationListener listener) {
        compilationListeners.remove(listener);
    }

    private void shutdown() {
        getCompilationNotify().notifyShutdown(this);
    }

    protected void doCompile(OptimizedCallTarget optimizedCallTarget) {
        int repeats = TruffleCompilerOptions.TruffleCompilationRepeats.getValue();
        if (repeats <= 1) {
            /* Normal compilation. */
            doCompile0(optimizedCallTarget);

        } else {
            /* Repeated compilation for compilation time benchmarking. */
            for (int i = 0; i < repeats; i++) {
                doCompile0(optimizedCallTarget);
            }
            System.exit(0);
        }
    }

    @SuppressWarnings("try")
    private void doCompile0(OptimizedCallTarget optimizedCallTarget) {
        boolean success = true;
        try (Scope s = Debug.scope("Truffle", new TruffleDebugJavaMethod(optimizedCallTarget))) {
            getTruffleCompiler().compileMethod(optimizedCallTarget);
        } catch (Throwable e) {
            optimizedCallTarget.notifyCompilationFailed(e);
            success = false;
        } finally {
            optimizedCallTarget.notifyCompilationFinished(success);
        }
    }

    protected abstract BackgroundCompileQueue getCompileQueue();

    public void compile(OptimizedCallTarget optimizedCallTarget, boolean mayBeAsynchronous) {
        BackgroundCompileQueue l = getCompileQueue();
        final WeakReference<OptimizedCallTarget> weakCallTarget = new WeakReference<>(optimizedCallTarget);
        Future<?> future = l.compileQueue.submit(new Runnable() {
            @Override
            public void run() {
                OptimizedCallTarget callTarget = weakCallTarget.get();
                if (callTarget != null) {
                    doCompile(callTarget);
                }
            }
        });
        optimizedCallTarget.setCompilationTask(future);
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

    public boolean cancelInstalledTask(OptimizedCallTarget optimizedCallTarget, Object source, CharSequence reason) {
        Future<?> codeTask = optimizedCallTarget.getCompilationTask();
        if (codeTask != null && isCompiling(optimizedCallTarget)) {
            optimizedCallTarget.setCompilationTask(null);
            boolean result = codeTask.cancel(true);
            if (result) {
                optimizedCallTarget.notifyCompilationFinished(false);
                getCompilationNotify().notifyCompilationDequeued(optimizedCallTarget, source, reason);
            }
            return result;
        }
        return false;
    }

    public void waitForCompilation(OptimizedCallTarget optimizedCallTarget, long timeout) throws ExecutionException, TimeoutException {
        Future<?> codeTask = optimizedCallTarget.getCompilationTask();
        if (codeTask != null && isCompiling(optimizedCallTarget)) {
            try {
                codeTask.get(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // ignore interrupted
            }
        }
    }

    @Deprecated
    public Collection<OptimizedCallTarget> getQueuedCallTargets() {
        return Collections.emptyList();
    }

    public int getCompilationQueueSize() {
        ExecutorService executor = getCompileQueue().compileQueue;
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getQueue().size();
        } else {
            return 0;
        }
    }

    public boolean isCompiling(OptimizedCallTarget optimizedCallTarget) {
        Future<?> codeTask = optimizedCallTarget.getCompilationTask();
        if (codeTask != null) {
            if (codeTask.isCancelled() || codeTask.isDone()) {
                optimizedCallTarget.setCompilationTask(null);
                return false;
            }
            return true;
        }
        return false;
    }

    public abstract void invalidateInstalledCode(OptimizedCallTarget optimizedCallTarget, Object source, CharSequence reason);

    public abstract void reinstallStubs();

    public final boolean enableInfopoints() {
        /* Currently infopoints can change code generation so don't enable them automatically */
        return platformEnableInfopoints() && TruffleEnableInfopoints.getValue();
    }

    protected abstract boolean platformEnableInfopoints();

    protected CallMethods getCallMethods() {
        return callMethods;
    }

    // cached field access to make it fast in the interpreter
    private static final boolean PROFILING_ENABLED = TruffleCompilerOptions.TruffleProfilingEnabled.getValue();

    public final boolean isProfilingEnabled() {
        return PROFILING_ENABLED;
    }

    private final class DispatchTruffleCompilationListener implements GraalTruffleCompilationListener {

        public void notifyCompilationQueued(OptimizedCallTarget target) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationQueued(target);
            }
        }

        public void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationInvalidated(target, source, reason);
            }
        }

        public void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationDequeued(target, source, reason);
            }
        }

        public void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationFailed(target, graph, t);
            }
        }

        public void notifyCompilationSplit(OptimizedDirectCallNode callNode) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationSplit(callNode);
            }
        }

        public void notifyCompilationGraalTierFinished(OptimizedCallTarget target, StructuredGraph graph) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationGraalTierFinished(target, graph);
            }
        }

        public void notifyCompilationSuccess(OptimizedCallTarget target, StructuredGraph graph, CompilationResult result) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationSuccess(target, graph, result);
            }
        }

        public void notifyCompilationStarted(OptimizedCallTarget target) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationStarted(target);
            }
        }

        public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, StructuredGraph graph) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationTruffleTierFinished(target, graph);
            }
        }

        public void notifyShutdown(GraalTruffleRuntime runtime) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyShutdown(runtime);
            }
        }

        public void notifyStartup(GraalTruffleRuntime runtime) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyStartup(runtime);
            }
        }

    }

    protected static final class CallMethods {
        public final ResolvedJavaMethod callNodeMethod;
        public final ResolvedJavaMethod callTargetMethod;
        public final ResolvedJavaMethod[] anyFrameMethod;

        private CallMethods(MetaAccessProvider metaAccess) {
            this.callNodeMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_NODE_METHOD);
            this.callTargetMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_TARGET_METHOD);
            this.anyFrameMethod = new ResolvedJavaMethod[]{callNodeMethod, callTargetMethod};
        }

        public static CallMethods lookup(MetaAccessProvider metaAccess) {
            return new CallMethods(metaAccess);
        }
    }
}
