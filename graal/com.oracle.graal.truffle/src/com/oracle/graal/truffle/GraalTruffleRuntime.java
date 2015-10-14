/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleCompileOnly;
import static com.oracle.graal.truffle.TruffleCompilerOptions.TruffleEnableInfopoints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.code.stack.InspectedFrame;
import jdk.vm.ci.code.stack.InspectedFrameVisitor;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.service.Services;

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

    private ArrayList<String> includes;
    private ArrayList<String> excludes;

    private final List<GraalTruffleCompilationListener> compilationListeners = new ArrayList<>();
    private final GraalTruffleCompilationListener compilationNotify = new DispatchTruffleCompilationListener();

    protected TruffleCompiler truffleCompiler;
    protected LoopNodeFactory loopNodeFactory;
    protected CallMethods callMethods;

    public GraalTruffleRuntime() {
    }

    public abstract TruffleCompiler getTruffleCompiler();

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
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        compilationNotify.notifyStartup(this);
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
    public <T> T iterateFrames(FrameInstanceVisitor<T> visitor) {
        StackIntrospection stackIntrospection = getStackIntrospection();

        InspectedFrameVisitor<T> inspectedFrameVisitor = new InspectedFrameVisitor<T>() {
            private boolean skipNext = false;

            public T visitFrame(InspectedFrame frame) {
                if (skipNext) {
                    assert frame.isMethod(getCallMethods().callTargetMethod[0]);
                    skipNext = false;
                    return null;
                }

                if (frame.isMethod(getCallMethods().callNodeMethod[0])) {
                    skipNext = true;
                    return visitor.visitFrame(new GraalFrameInstance.CallNodeFrame(frame));
                } else {
                    assert frame.isMethod(getCallMethods().callTargetMethod[0]);
                    return visitor.visitFrame(new GraalFrameInstance.CallTargetFrame(frame, false));
                }

            }
        };
        return stackIntrospection.iterateFrames(getCallMethods().anyFrameMethod, getCallMethods().anyFrameMethod, 1, inspectedFrameVisitor);
    }

    protected abstract StackIntrospection getStackIntrospection();

    @Override
    public FrameInstance getCallerFrame() {
        return iterateFrames(frame -> frame);
    }

    @TruffleBoundary
    @Override
    public FrameInstance getCurrentFrame() {
        return getStackIntrospection().iterateFrames(getCallMethods().callTargetMethod, getCallMethods().callTargetMethod, 0, frame -> new GraalFrameInstance.CallTargetFrame(frame, true));
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

    public abstract Collection<OptimizedCallTarget> getQueuedCallTargets();

    public abstract void compile(OptimizedCallTarget optimizedCallTarget, boolean mayBeAsynchronous);

    @SuppressWarnings("try")
    protected void doCompile(OptimizedCallTarget optimizedCallTarget) {
        boolean success = true;
        try (Scope s = Debug.scope("Truffle", new TruffleDebugJavaMethod(optimizedCallTarget))) {
            truffleCompiler.compileMethod(optimizedCallTarget);
        } catch (Throwable e) {
            optimizedCallTarget.notifyCompilationFailed(e);
            success = false;
        } finally {
            optimizedCallTarget.notifyCompilationFinished(success);
        }
    }

    public abstract boolean cancelInstalledTask(OptimizedCallTarget optimizedCallTarget, Object source, CharSequence reason);

    public abstract void waitForCompilation(OptimizedCallTarget optimizedCallTarget, long timeout) throws ExecutionException, TimeoutException;

    public abstract boolean isCompiling(OptimizedCallTarget optimizedCallTarget);

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
        public final ResolvedJavaMethod[] callNodeMethod;
        public final ResolvedJavaMethod[] callTargetMethod;
        public final ResolvedJavaMethod[] anyFrameMethod;

        private CallMethods(MetaAccessProvider metaAccess) {
            this.callNodeMethod = new ResolvedJavaMethod[]{metaAccess.lookupJavaMethod(GraalFrameInstance.CallNodeFrame.METHOD)};
            this.callTargetMethod = new ResolvedJavaMethod[]{metaAccess.lookupJavaMethod(GraalFrameInstance.CallTargetFrame.METHOD)};
            this.anyFrameMethod = new ResolvedJavaMethod[]{callNodeMethod[0], callTargetMethod[0]};
        }

        public static CallMethods lookup(MetaAccessProvider metaAccess) {
            return new CallMethods(metaAccess);
        }
    }
}
