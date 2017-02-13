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
package org.graalvm.compiler.truffle;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.TVMCI;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.LayoutFactory;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.stack.InspectedFrame;
import jdk.vm.ci.code.stack.InspectedFrameVisitor;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.CompilerThreadFactory;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleOptionsOverrideScope;
import org.graalvm.compiler.truffle.debug.CompilationStatisticsListener;
import org.graalvm.compiler.truffle.debug.PrintCallTargetProfiling;
import org.graalvm.compiler.truffle.debug.TraceCompilationASTListener;
import org.graalvm.compiler.truffle.debug.TraceCompilationCallTreeListener;
import org.graalvm.compiler.truffle.debug.TraceCompilationFailureListener;
import org.graalvm.compiler.truffle.debug.TraceCompilationListener;
import org.graalvm.compiler.truffle.debug.TraceCompilationPolymorphismListener;
import org.graalvm.compiler.truffle.debug.TraceInliningListener;
import org.graalvm.compiler.truffle.debug.TraceSplittingListener;
import org.graalvm.compiler.truffle.phases.InstrumentPhase;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCompilationExceptionsAreThrown;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCompilationRepeats;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCompileOnly;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCompilerThreads;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleEnableInfopoints;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleInstrumentBoundaries;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleInstrumentBranches;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleProfilingEnabled;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleUseFrameWithoutBoxing;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.getValue;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.overrideOptions;

public abstract class GraalTruffleRuntime implements TruffleRuntime {

    private final Map<RootCallTarget, Void> callTargets = Collections.synchronizedMap(new WeakHashMap<RootCallTarget, Void>());

    protected abstract static class BackgroundCompileQueue implements CompilerThreadFactory.DebugConfigAccess {
        private final ExecutorService compileQueue;

        protected BackgroundCompileQueue() {
            CompilerThreadFactory factory = new CompilerThreadFactory("TruffleCompilerThread", this);

            int selectedProcessors = TruffleCompilerOptions.getValue(TruffleCompilerThreads);
            if (selectedProcessors == 0) {
                // No manual selection made, check how many processors are available.
                int availableProcessors = Runtime.getRuntime().availableProcessors();
                if (availableProcessors >= 4) {
                    selectedProcessors = 2;
                }
            }
            selectedProcessors = Math.max(1, selectedProcessors);
            compileQueue = Executors.newFixedThreadPool(selectedProcessors, factory);
        }
    }

    private Object cachedIncludesExcludes;
    private ArrayList<String> includes;
    private ArrayList<String> excludes;

    private final List<GraalTruffleCompilationListener> compilationListeners = new ArrayList<>();
    private final GraalTruffleCompilationListener compilationNotify = new DispatchTruffleCompilationListener();

    protected TruffleCompiler truffleCompiler;
    protected LoopNodeFactory loopNodeFactory;
    protected CallMethods callMethods;

    private final Supplier<GraalRuntime> graalRuntime;
    private final GraalTVMCI tvmci = new GraalTVMCI();

    /**
     * The instrumentation object is used by the Truffle instrumentation to count executions. The
     * value is lazily initialized the first time it is requested because it depends on the Truffle
     * options, and tests that need the instrumentation table need to override these options after
     * the TruffleRuntime object is created.
     */
    private volatile InstrumentPhase.Instrumentation instrumentation;

    /**
     * Utility method that casts the singleton {@link TruffleRuntime}.
     */
    public static GraalTruffleRuntime getRuntime() {
        return (GraalTruffleRuntime) Truffle.getRuntime();
    }

    public GraalTruffleRuntime(Supplier<GraalRuntime> graalRuntime) {
        this.graalRuntime = graalRuntime;

    }

    protected GraalTVMCI getTvmci() {
        return tvmci;
    }

    public abstract TruffleCompiler getTruffleCompiler();

    public <T> T getRequiredGraalCapability(Class<T> clazz) {
        T ret = graalRuntime.get().getCapability(clazz);
        if (ret == null) {
            throw new GraalError("The VM does not expose the required Graal capability %s.", clazz.getName());
        }
        return ret;
    }

    private static <T extends PrioritizedServiceProvider> T loadPrioritizedServiceProvider(Class<T> clazz) {
        Iterable<T> providers = GraalServices.load(clazz);
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
        TraceCompilationASTListener.install(this);
        installShutdownHooks();
        compilationNotify.notifyStartup(this);
    }

    protected void installShutdownHooks() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    protected void lookupCallMethods(MetaAccessProvider metaAccess) {
        callMethods = CallMethods.lookup(metaAccess);
    }

    /** Accessor for non-public state in {@link FrameDescriptor}. */
    public void markFrameMaterializeCalled(FrameDescriptor descriptor) {
        try {
            getTvmci().markFrameMaterializeCalled(descriptor);
        } catch (Throwable ex) {
            /*
             * Backward compatibility: do nothing on old Truffle version where the field in
             * FrameDescriptor does not exist.
             */
        }
    }

    /** Accessor for non-public state in {@link FrameDescriptor}. */
    public boolean getFrameMaterializeCalled(FrameDescriptor descriptor) {
        try {
            return getTvmci().getFrameMaterializeCalled(descriptor);
        } catch (Throwable ex) {
            /*
             * Backward compatibility: be conservative on old Truffle version where the field in
             * FrameDescriptor does not exist.
             */
            return true;
        }
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
        if (useFrameWithoutBoxing) {
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
        private final CallMethods methods;

        private int skipFrames;

        private InspectedFrame callNodeFrame;

        FrameVisitor(FrameInstanceVisitor<T> visitor, CallMethods methods, int skip) {
            this.visitor = visitor;
            this.methods = methods;
            this.skipFrames = skip;
        }

        @Override
        public T visitFrame(InspectedFrame frame) {
            if (frame.isMethod(methods.callOSRMethod)) {
                // we ignore OSR frames.
                skipFrames++;
                return null;
            } else if (frame.isMethod(methods.callTargetMethod)) {
                try {
                    if (skipFrames == 0) {
                        return visitor.visitFrame(new GraalFrameInstance(frame, callNodeFrame));
                    } else {
                        skipFrames--;
                    }
                } finally {
                    callNodeFrame = null;
                }
            } else if (frame.isMethod(methods.callNodeMethod)) {
                callNodeFrame = frame;
            }
            return null;
        }
    }

    private <T> T iterateImpl(FrameInstanceVisitor<T> visitor, final int skip) {
        CallMethods methods = getCallMethods();
        FrameVisitor<T> jvmciVisitor = new FrameVisitor<>(visitor, methods, skip);
        return getStackIntrospection().iterateFrames(methods.anyFrameMethod, methods.anyFrameMethod, 0, jvmciVisitor);
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

    @Override
    public <T> T getCapability(Class<T> capability) {
        if (capability == TVMCI.class) {
            return capability.cast(tvmci);
        } else if (capability == LayoutFactory.class) {
            return capability.cast(loadObjectLayoutFactory());
        }
        try {
            Iterator<T> services = GraalServices.load(capability).iterator();
            if (services.hasNext()) {
                return services.next();
            }
            return null;
        } catch (ServiceConfigurationError e) {
            // Happens on JDK9 when a service type has not been exported to Graal
            // or Graal's module descriptor does not declare a use of capability.
            return null;
        }
    }

    @SuppressFBWarnings(value = "", justification = "Cache that does not need to use equals to compare.")
    final boolean acceptForCompilation(RootNode rootNode) {
        String includesExcludes = getValue(TruffleCompileOnly);
        if (includesExcludes != null) {
            if (cachedIncludesExcludes != includesExcludes) {
                parseCompileOnly();
                this.cachedIncludesExcludes = includesExcludes;
            }

            String name = rootNode.getName();
            boolean included = includes.isEmpty();
            if (name != null) {
                for (int i = 0; !included && i < includes.size(); i++) {
                    if (name.contains(includes.get(i))) {
                        included = true;
                    }
                }
            }
            if (!included) {
                return false;
            }
            if (name != null) {
                for (String exclude : excludes) {
                    if (name.contains(exclude)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    protected void parseCompileOnly() {
        ArrayList<String> includesList = new ArrayList<>();
        ArrayList<String> excludesList = new ArrayList<>();

        String[] items = getValue(TruffleCompileOnly).split(",");
        for (String item : items) {
            if (item.startsWith("~")) {
                excludesList.add(item.substring(1));
            } else {
                includesList.add(item);
            }
        }
        this.includes = includesList;
        this.excludes = excludesList;
    }

    public abstract SpeculationLog createSpeculationLog();

    @Override
    public RootCallTarget createCallTarget(RootNode rootNode) {
        return createClonedCallTarget(null, rootNode);
    }

    @SuppressWarnings("deprecation")
    public RootCallTarget createClonedCallTarget(OptimizedCallTarget source, RootNode rootNode) {
        CompilerAsserts.neverPartOfCompilation();

        OptimizedCallTarget target = createOptimizedCallTarget(source, rootNode);
        rootNode.setCallTarget(target);
        tvmci.onLoad(target.getRootNode());
        callTargets.put(target, null);
        return target;
    }

    protected abstract OptimizedCallTarget createOptimizedCallTarget(OptimizedCallTarget source, RootNode rootNode);

    @SuppressWarnings("deprecation")
    @Override
    public Collection<RootCallTarget> getCallTargets() {
        return Collections.unmodifiableSet(callTargets.keySet());
    }

    public void addCompilationListener(GraalTruffleCompilationListener listener) {
        compilationListeners.add(listener);
    }

    public void removeCompilationListener(GraalTruffleCompilationListener listener) {
        compilationListeners.remove(listener);
    }

    private void shutdown() {
        getCompilationNotify().notifyShutdown(this);
        OptionValues options = TruffleCompilerOptions.getOptions();
        if (getValue(TruffleInstrumentBranches) || getValue(TruffleInstrumentBoundaries)) {
            instrumentation.dumpAccessTable(options);
        }
    }

    protected void doCompile(OptimizedCallTarget optimizedCallTarget, CancellableCompileTask task) {
        int repeats = TruffleCompilerOptions.getValue(TruffleCompilationRepeats);
        if (repeats <= 1) {
            /* Normal compilation. */
            doCompile0(optimizedCallTarget, task);

        } else {
            /* Repeated compilation for compilation time benchmarking. */
            for (int i = 0; i < repeats; i++) {
                doCompile0(optimizedCallTarget, task);
            }
            System.exit(0);
        }
    }

    @SuppressWarnings("try")
    private void doCompile0(OptimizedCallTarget optimizedCallTarget, CancellableCompileTask task) {
        try (Scope s = Debug.scope("Truffle", new TruffleDebugJavaMethod(optimizedCallTarget))) {
            getTruffleCompiler().compileMethod(optimizedCallTarget, this, task);
        } catch (Throwable e) {
            optimizedCallTarget.notifyCompilationFailed(e);
        } finally {
            optimizedCallTarget.resetCompilationTask();
        }
    }

    /**
     * @param optimizedCallTarget
     * @param callRootMethod
     * @param backend
     */
    public CompilationIdentifier getCompilationIdentifier(OptimizedCallTarget optimizedCallTarget, ResolvedJavaMethod callRootMethod, Backend backend) {
        return backend.getCompilationIdentifier(callRootMethod);
    }

    protected abstract BackgroundCompileQueue getCompileQueue();

    @SuppressWarnings("try")
    public CancellableCompileTask submitForCompilation(OptimizedCallTarget optimizedCallTarget) {
        BackgroundCompileQueue l = getCompileQueue();
        final WeakReference<OptimizedCallTarget> weakCallTarget = new WeakReference<>(optimizedCallTarget);
        final OptionValues optionOverrides = TruffleCompilerOptions.getCurrentOptionOverrides();
        CancellableCompileTask cancellable = new CancellableCompileTask();
        cancellable.setFuture(l.compileQueue.submit(new Runnable() {
            @Override
            public void run() {
                OptimizedCallTarget callTarget = weakCallTarget.get();
                if (callTarget != null) {
                    try (TruffleOptionsOverrideScope scope = optionOverrides != null ? overrideOptions(optionOverrides.getMap()) : null) {
                        doCompile(callTarget, cancellable);
                    }
                }
            }
        }));
        // task and future must never diverge from each other
        assert cancellable.future != null;
        return cancellable;
    }

    public void finishCompilation(OptimizedCallTarget optimizedCallTarget, Future<?> future, boolean mayBeAsynchronous) {
        getCompilationNotify().notifyCompilationQueued(optimizedCallTarget);

        if (!mayBeAsynchronous) {
            try {
                future.get();
            } catch (ExecutionException e) {
                if (TruffleCompilerOptions.getValue(TruffleCompilationExceptionsAreThrown) && !(e.getCause() instanceof BailoutException && !((BailoutException) e.getCause()).isPermanent())) {
                    throw new RuntimeException(e.getCause());
                } else {
                    // silenlty ignored
                }
            } catch (InterruptedException e) {
                /*
                 * Compilation cancellation happens cooperatively. A compiler thread (which is a VM
                 * thread) must never throw an interrupted exception.
                 */
                GraalError.shouldNotReachHere(e);
            } catch (CancellationException e) {
                /*
                 * Silently ignored as future might have undergone a "soft" cancel(false).
                 */
            }
        }
    }

    public boolean cancelInstalledTask(OptimizedCallTarget optimizedCallTarget, Object source, CharSequence reason) {
        CancellableCompileTask task = optimizedCallTarget.getCompilationTask();
        if (task != null) {
            Future<?> compilationFuture = task.getFuture();
            if (compilationFuture != null && isCompiling(optimizedCallTarget)) {
                optimizedCallTarget.resetCompilationTask();
                /*
                 * Cancellation of an installed task: There are two dimensions here: First we set
                 * the cancel bit in the task, this allows the compiler to, cooperatively, stop
                 * compilation and throw a non permanent bailout and then we cancel the future which
                 * might have already stopped at that point in time.
                 */
                task.cancel();
                // Either the task finished already, or it was cancelled.
                boolean result = !task.isRunning();
                if (result) {
                    optimizedCallTarget.resetCompilationTask();
                    getCompilationNotify().notifyCompilationDequeued(optimizedCallTarget, source, reason);
                }
                return result;
            }
        }
        return false;
    }

    public void waitForCompilation(OptimizedCallTarget optimizedCallTarget, long timeout) throws ExecutionException, TimeoutException {
        CancellableCompileTask task = optimizedCallTarget.getCompilationTask();
        if (task != null) {
            Future<?> compilationFuture = task.getFuture();
            if (compilationFuture != null && isCompiling(optimizedCallTarget)) {
                try {
                    compilationFuture.get(timeout, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // ignore interrupted
                }
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
        CancellableCompileTask task = optimizedCallTarget.getCompilationTask();
        if (task != null) {
            Future<?> compilationFuture = task.getFuture();
            if (compilationFuture != null) {
                if (compilationFuture.isCancelled() || compilationFuture.isDone()) {
                    optimizedCallTarget.resetCompilationTask();
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    public abstract void invalidateInstalledCode(OptimizedCallTarget optimizedCallTarget, Object source, CharSequence reason);

    public abstract void reinstallStubs();

    public final boolean enableInfopoints() {
        /* Currently infopoints can change code generation so don't enable them automatically */
        return platformEnableInfopoints() && TruffleCompilerOptions.getValue(TruffleEnableInfopoints);
    }

    protected abstract boolean platformEnableInfopoints();

    protected CallMethods getCallMethods() {
        return callMethods;
    }

    public InstrumentPhase.Instrumentation getInstrumentation() {
        if (instrumentation == null) {
            synchronized (this) {
                if (instrumentation == null) {
                    OptionValues options = TruffleCompilerOptions.getOptions();
                    long[] accessTable = new long[TruffleCompilerOptions.TruffleInstrumentationTableSize.getValue(options)];
                    instrumentation = new InstrumentPhase.Instrumentation(accessTable);
                }
            }
        }
        return instrumentation;
    }

    // cached field access to make it fast in the interpreter
    private static final boolean PROFILING_ENABLED = TruffleCompilerOptions.getValue(TruffleProfilingEnabled);

    @Override
    public final boolean isProfilingEnabled() {
        return PROFILING_ENABLED;
    }

    private static Object loadObjectLayoutFactory() {
        LayoutFactory bestLayoutFactory = null;
        ServiceLoader<LayoutFactory> serviceLoader = ServiceLoader.load(LayoutFactory.class, GraalTruffleRuntime.class.getClassLoader());
        for (LayoutFactory currentLayoutFactory : serviceLoader) {
            if (bestLayoutFactory == null) {
                bestLayoutFactory = currentLayoutFactory;
            } else if (currentLayoutFactory.getPriority() >= bestLayoutFactory.getPriority()) {
                assert currentLayoutFactory.getPriority() != bestLayoutFactory.getPriority();
                bestLayoutFactory = currentLayoutFactory;
            }
        }
        return bestLayoutFactory;
    }

    private final class DispatchTruffleCompilationListener implements GraalTruffleCompilationListener {

        @Override
        public void notifyCompilationQueued(OptimizedCallTarget target) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationQueued(target);
            }
        }

        @Override
        public void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationInvalidated(target, source, reason);
            }
        }

        @Override
        public void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationDequeued(target, source, reason);
            }
        }

        @Override
        public void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationFailed(target, graph, t);
            }
        }

        @Override
        public void notifyCompilationSplit(OptimizedDirectCallNode callNode) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationSplit(callNode);
            }
        }

        @Override
        public void notifyCompilationGraalTierFinished(OptimizedCallTarget target, StructuredGraph graph) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationGraalTierFinished(target, graph);
            }
        }

        @Override
        public void notifyCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationDeoptimized(target, frame);
            }
        }

        @Override
        public void notifyCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph, CompilationResult result) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationSuccess(target, inliningDecision, graph, result);
            }
        }

        @Override
        public void notifyCompilationStarted(OptimizedCallTarget target) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationStarted(target);
            }
        }

        @Override
        public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyCompilationTruffleTierFinished(target, inliningDecision, graph);
            }
        }

        @Override
        public void notifyShutdown(GraalTruffleRuntime runtime) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyShutdown(runtime);
            }
        }

        @Override
        public void notifyStartup(GraalTruffleRuntime runtime) {
            for (GraalTruffleCompilationListener l : compilationListeners) {
                l.notifyStartup(runtime);
            }
        }

    }

    protected static final class CallMethods {
        public final ResolvedJavaMethod callNodeMethod;
        public final ResolvedJavaMethod callTargetMethod;
        public final ResolvedJavaMethod callOSRMethod;
        public final ResolvedJavaMethod[] anyFrameMethod;

        private CallMethods(MetaAccessProvider metaAccess) {
            this.callNodeMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_NODE_METHOD);
            this.callTargetMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_TARGET_METHOD);
            this.callOSRMethod = metaAccess.lookupJavaMethod(GraalFrameInstance.CALL_OSR_METHOD);
            this.anyFrameMethod = new ResolvedJavaMethod[]{callNodeMethod, callTargetMethod, callOSRMethod};
        }

        public static CallMethods lookup(MetaAccessProvider metaAccess) {
            return new CallMethods(metaAccess);
        }
    }

    /**
     * The flag is checked from within a Truffle compilation and we need to constant fold the
     * decision. In addition, we want only one of {@link FrameWithoutBoxing} and
     * {@link FrameWithBoxing} seen as reachable in AOT mode, so we need to be able to constant fold
     * the decision as early as possible.
     */
    public static final boolean useFrameWithoutBoxing = TruffleCompilerOptions.getValue(TruffleUseFrameWithoutBoxing);
}
