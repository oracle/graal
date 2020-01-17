/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode.OSRRootNode;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.OptimizationFailedException;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor.CallInlined;
import com.oracle.truffle.api.impl.Accessor.CallProfiled;
import com.oracle.truffle.api.impl.DefaultCompilerOptions;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Call target that is optimized by Graal upon surpassing a specific invocation threshold. That is,
 * this is a Truffle AST that can be optimized via partial evaluation and compiled to machine code.
 *
 * Note: {@code PartialEvaluator} looks up this class and a number of its methods by name.
 */
@SuppressWarnings("deprecation")
public abstract class OptimizedCallTarget implements CompilableTruffleAST, RootCallTarget, ReplaceObserver {

    private static final String NODE_REWRITING_ASSUMPTION_NAME = "nodeRewritingAssumption";
    static final String CALL_BOUNDARY_METHOD_NAME = "callProxy";
    static final String CALL_INLINED_METHOD_NAME = "call";
    private static final AtomicReferenceFieldUpdater<OptimizedCallTarget, SpeculationLog> SPECULATION_LOG_UPDATER = AtomicReferenceFieldUpdater.newUpdater(OptimizedCallTarget.class,
                    SpeculationLog.class, "speculationLog");
    private static final AtomicReferenceFieldUpdater<OptimizedCallTarget, Assumption> NODE_REWRITING_ASSUMPTION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(OptimizedCallTarget.class,
                    Assumption.class, "nodeRewritingAssumption");
    private static final WeakReference<OptimizedDirectCallNode> NO_CALL = new WeakReference<>(null);
    private static final WeakReference<OptimizedDirectCallNode> MULTIPLE_CALLS = null;
    private static final String SPLIT_LOG_FORMAT = "[truffle] [poly-event] %-70s %s";
    private static final int MAX_PROFILED_ARGUMENTS = 256;
    private static final String ARGUMENT_TYPES_ASSUMPTION_NAME = "Profiled Argument Types";
    private static final String RETURN_TYPE_ASSUMPTION_NAME = "Profiled Return Type";

    /** The AST to be executed when this call target is called. */
    private final RootNode rootNode;

    /** Whether this call target was cloned, compiled or called. */
    @CompilationFinal protected volatile boolean initialized;

    /**
     * The call threshold is counted down for each real call until it reaches zero and triggers a
     * {@link #compile(boolean) compilation}. It is decremented for each real call to the call
     * target. Reset by TruffleFeature after boot image generation.
     */
    private int callThreshold;
    /**
     * The call and loop threshold is counted down for each real call and reported loop count until
     * it reaches zero and triggers a {@link #compile(boolean) compilation}. Reset by TruffleFeature
     * after boot image generation.
     */
    private int callAndLoopThreshold;

    /*
     * Updating profiling information and its Assumption objects is done without synchronization and
     * atomic operations to keep the overhead as low as possible. This means that there can be races
     * and we might see imprecise profiles. That is OK. But we must not install and run compiled
     * code that depends on wrong profiling information, because that leads to crashes. Therefore,
     * the Assumption objects need to be updated in the correct order: A new valid assumption is
     * installed *after* installing profile information, but the assumption is invalidated *before*
     * invalidating profile information. This ensures that the compiler sees an invalidated
     * assumption in case a race happens. Note that OptimizedAssumption.invalidate() performs
     * synchronization and is therefore a memory barrier. Reset by TruffleFeature after boot image
     * generation.
     */
    @CompilationFinal(dimensions = 1) private Class<?>[] profiledArgumentTypes;
    @CompilationFinal private OptimizedAssumption profiledArgumentTypesAssumption;
    @CompilationFinal private Class<?> profiledReturnType;
    @CompilationFinal private OptimizedAssumption profiledReturnTypeAssumption;
    @CompilationFinal private Class<? extends Throwable> profiledExceptionType;

    /**
     * Set if compilation failed or was ignored. Reset by TruffleFeature after boot image
     * generation.
     */
    private volatile boolean compilationFailed;
    /**
     * Whether the call profile was preinitialized with a fixed set of type classes. In such a case
     * the arguments will be cast using unsafe and the arguments array for calls is not checked
     * against the profile, only asserted.
     */
    @CompilationFinal private boolean callProfiled;

    /**
     * Timestamp when the call target was initialized e.g. used the first time. Reset by
     * TruffleFeature after boot image generation.
     */
    private volatile long initializedTimestamp;

    /**
     * When this field is not null, this {@link OptimizedCallTarget} is {@linkplain #isCompiling()
     * being compiled}.<br/>
     *
     * It is only set to non-null in {@link #compile(boolean)} in a synchronized block. It is only
     * {@linkplain #resetCompilationTask() set to null} by the compilation thread once the
     * compilation is over.<br/>
     *
     * Note that {@link #resetCompilationTask()} waits for the field to have been set to a non-null
     * value before resetting it.<br/>
     *
     * Once it has been set to a non-null value, the compilation <em>must</em> complete and call
     * {@link #resetCompilationTask()} even if that compilation fails or is cancelled.
     */
    private volatile CancellableCompileTask compilationTask;

    private volatile boolean needsSplit;

    /**
     * The engine data associated with this call target. Used to cache option lookups and to gather
     * engine specific statistics.
     */
    public final EngineData engine;

    /** Only set for a source CallTarget with a clonable RootNode. */
    private volatile RootNode uninitializedRootNode;

    /**
     * The speculation log to keep track of assumptions taken and failed for previous compialtions.
     */
    private volatile SpeculationLog speculationLog;

    /** Source target if this target was duplicated. */
    private final OptimizedCallTarget sourceCallTarget;

    /**
     * When this call target is inlined, the inlining {@link InstalledCode} registers this
     * assumption. It gets invalidated when a node rewrite in this call target is performed. This
     * ensures that all compiled methods that inline this call target are properly invalidated.
     */
    private volatile Assumption nodeRewritingAssumption;

    /**
     * Traversing the AST to cache non trivial nodes is expensive so we don't want to repeat it only
     * if the AST changes.
     */
    private volatile int cachedNonTrivialNodeCount = -1;

    /**
     * Number of known direct call sites of this call target. Used in splitting and inlinig
     * heuristics.
     */
    private volatile int callSitesKnown;

    private volatile String nameCache;
    private final int uninitializedNodeCount;

    private volatile WeakReference<OptimizedDirectCallNode> singleCallNode = NO_CALL;

    protected OptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode) {
        assert sourceCallTarget == null || sourceCallTarget.sourceCallTarget == null : "Cannot create a clone of a cloned CallTarget";
        this.sourceCallTarget = sourceCallTarget;
        this.speculationLog = sourceCallTarget != null ? sourceCallTarget.getSpeculationLog() : null;
        this.rootNode = rootNode;
        this.engine = GraalTVMCI.getEngineData(rootNode);
        this.resetCompilationProfile();
        // Do not adopt children of OSRRootNodes; we want to preserve the parent of the LoopNode.
        final GraalTVMCI tvmci = runtime().getTvmci();
        this.uninitializedNodeCount = !(rootNode instanceof OSRRootNode) ? tvmci.adoptChildrenAndCount(rootNode) : -1;
        tvmci.setCallTarget(rootNode, this);
    }

    public final Assumption getNodeRewritingAssumption() {
        Assumption assumption = nodeRewritingAssumption;
        if (assumption == null) {
            assumption = initializeNodeRewritingAssumption();
        }
        return assumption;
    }

    /**
     * @return an existing or the newly initialized node rewriting assumption.
     */
    private Assumption initializeNodeRewritingAssumption() {
        Assumption newAssumption = runtime().createAssumption(
                        !getOptionValue(PolyglotCompilerOptions.TraceAssumptions) ? NODE_REWRITING_ASSUMPTION_NAME : NODE_REWRITING_ASSUMPTION_NAME + " of " + rootNode);
        if (NODE_REWRITING_ASSUMPTION_UPDATER.compareAndSet(this, null, newAssumption)) {
            return newAssumption;
        } else {
            // if CAS failed, assumption is already initialized; cannot be null after that.
            return Objects.requireNonNull(nodeRewritingAssumption);
        }
    }

    /**
     * Invalidate node rewriting assumption iff it has been initialized.
     */
    private void invalidateNodeRewritingAssumption() {
        Assumption oldAssumption = NODE_REWRITING_ASSUMPTION_UPDATER.getAndUpdate(this, new UnaryOperator<Assumption>() {
            @Override
            public Assumption apply(Assumption prev) {
                return prev == null ? null : runtime().createAssumption(prev.getName());
            }
        });
        if (oldAssumption != null) {
            oldAssumption.invalidate();
        }
    }

    @Override
    public final RootNode getRootNode() {
        return rootNode;
    }

    public final void resetCompilationProfile() {
        this.callThreshold = engine.firstTierCallThreshold;
        this.callAndLoopThreshold = engine.firstTierCallAndLoopThreshold;
    }

    protected List<OptimizedAssumption> getProfiledTypesAssumptions() {
        List<OptimizedAssumption> result = new ArrayList<>();
        if (getProfiledArgumentTypes() != null) {
            result.add(profiledArgumentTypesAssumption);
        }
        if (getProfiledReturnType() != null) {
            result.add(profiledReturnTypeAssumption);
        }
        return result;
    }

    protected final Class<?>[] getProfiledArgumentTypes() {
        if (profiledArgumentTypesAssumption == null) {
            /*
             * We always need an assumption. If this method is called before the profile was
             * initialized, we have to be conservative and disable profiling, which is done by
             * creating an invalid assumption but leaving the type field null.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            profiledArgumentTypesAssumption = createInvalidAssumption(ARGUMENT_TYPES_ASSUMPTION_NAME);
        }

        if (profiledArgumentTypesAssumption.isValid()) {
            return profiledArgumentTypes;
        } else {
            return null;
        }
    }

    protected final Class<?> getProfiledReturnType() {
        if (profiledReturnTypeAssumption == null) {
            /*
             * We always need an assumption. If this method is called before the profile was
             * initialized, we have to be conservative and disable profiling, which is done by
             * creating an invalid assumption but leaving the type field null.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            profiledReturnTypeAssumption = createInvalidAssumption(RETURN_TYPE_ASSUMPTION_NAME);
        }

        if (profiledReturnTypeAssumption.isValid()) {
            return profiledReturnType;
        } else {
            return null;
        }
    }

    @Override
    public final Object call(Object... args) {
        Node encapsulatingNode = NodeUtil.pushEncapsulatingNode(null);
        try {
            return callIndirect(encapsulatingNode, args);
        } finally {
            NodeUtil.popEncapsulatingNode(encapsulatingNode);
        }
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    public final Object callIndirect(Node location, Object... args) {
        try {
            profileIndirectCall();
            return doInvoke(args);
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert keepAlive(location);
        }
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    public final Object callDirect(Node location, Object... args) {
        try {
            profileDirectCall(args);
            try {
                final boolean isInlined = InlineDecision.get();
                /*
                 * Language agnostic inlining depends on this call to callBoundary to inline. The
                 * isInlined value is passed in to create a data dependency needed by the compiler
                 * and despite being "always true" should not be replaced with true (or anything
                 * else).
                 */
                Object result = isInlined ? callBoundary(InlineDecision.inject(args, isInlined)) : doInvoke(args);
                if (CompilerDirectives.inCompiledCode()) {
                    result = injectReturnValueProfile(result);
                }
                return result;
            } catch (Throwable t) {
                throw rethrow(profileExceptionType(t));
            }
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert keepAlive(location);
        }
    }

    private static boolean keepAlive(@SuppressWarnings("unused") Object o) {
        return true;
    }

    public final Object callOSR(Object... args) {
        return doInvoke(args);
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    public final Object callInlined(Node location, Object... arguments) {
        ensureInitialized();
        try {
            return callProxy(createFrame(getRootNode().getFrameDescriptor(), arguments));
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert keepAlive(location);
        }
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    public final Object callInlinedAgnostic(Object... arguments) {
        ensureInitialized();
        return callProxy(createFrame(getRootNode().getFrameDescriptor(), arguments));
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    public final Object callInlinedForced(Node location, Object... arguments) {
        ensureInitialized();
        try {
            return callProxy(createFrame(getRootNode().getFrameDescriptor(), arguments));
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert keepAlive(location);
        }
    }

    /*
     * Overridden by SVM.
     */
    protected Object doInvoke(Object[] args) {
        return callBoundary(args);
    }

    @TruffleCallBoundary
    protected final Object callBoundary(Object[] args) {
        /*
         * Note this method compiles without any inlining or other optimizations. It is therefore
         * important that this method stays small. It is compiled as a special stub that calls into
         * the optimized code or if the call target is not yet optimized calls into callRoot
         * directly. In order to avoid deoptimizations in this method it has optimizations disabled.
         * Any additional code here will likely have significant impact on the intepreter call
         * performance.
         */
        if (interpreterCall()) {
            return doInvoke(args);
        }
        return callRoot(args);
    }

    private boolean interpreterCall() {
        if (isValid()) {
            // Native entry stubs were deoptimized => reinstall.
            runtime().bypassedInstalledCode();
        }
        ensureInitialized();
        int intCallCount = --callThreshold;
        int intAndLoopCallCount = --callAndLoopThreshold;
        // Check if call target is hot enough to compile, but took not too long to get hot.
        if (intCallCount <= 0 //
                        && intAndLoopCallCount <= 0 //
                        && !compilationFailed //
                        && !isCompiling()) {
            return compile(!engine.multiTier);
        }
        return false;
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    protected final Object callRoot(Object[] originalArguments) {
        Object[] args = originalArguments;
        if (GraalCompilerDirectives.inFirstTier()) {
            firstTierCall();
        }
        if (CompilerDirectives.inCompiledCode()) {
            args = injectArgumentProfile(originalArguments);
        }
        Object result = callProxy(createFrame(getRootNode().getFrameDescriptor(), args));
        profileReturnValue(result);
        return result;
    }

    /*
     * This should be private but can't be. GR-19397
     */
    public final boolean firstTierCall() {
        // this is partially evaluated so the second part should fold to a constant.
        int firstTierCallThreshold = (--callThreshold) - engine.firstTierCallThreshold + engine.lastTierCallThreshold;
        if (firstTierCallThreshold <= 0 && !isCompiling() && !compilationFailed) {
            return lastTierCompile(this);
        }
        return false;
    }

    @CompilerDirectives.TruffleBoundary
    private static boolean lastTierCompile(OptimizedCallTarget callTarget) {
        return callTarget.compile(true);
    }

    protected final Object callProxy(VirtualFrame frame) {
        final boolean inCompiled = CompilerDirectives.inCompilationRoot();
        try {
            return getRootNode().execute(frame);
        } catch (ControlFlowException t) {
            throw rethrow(profileExceptionType(t));
        } catch (Throwable t) {
            Throwable profiledT = profileExceptionType(t);
            runtime().getTvmci().onThrowable(null, this, profiledT, frame);
            throw rethrow(profiledT);
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert frame != null && this != null;
            if (CompilerDirectives.inInterpreter() && inCompiled) {
                notifyDeoptimized(frame);
            }
        }
    }

    private void notifyDeoptimized(VirtualFrame frame) {
        runtime().getListener().onCompilationDeoptimized(this, frame);
    }

    static GraalTruffleRuntime runtime() {
        return (GraalTruffleRuntime) Truffle.getRuntime();
    }

    /*
     * This should be private but can't be due to SVM bug.
     */
    public final void ensureInitialized() {
        if (!initialized) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initialize();
        }
    }

    private synchronized void initialize() {
        if (!initialized) {
            GraalTVMCI tvmci = runtime().getTvmci();
            if (sourceCallTarget == null && rootNode.isCloningAllowed() && !tvmci.isCloneUninitializedSupported(rootNode)) {
                // We are the source CallTarget, so make a copy.
                this.uninitializedRootNode = NodeUtil.cloneNode(rootNode);
            }
            tvmci.onFirstExecution(this);
            if (engine.callTargetStatistics) {
                this.initializedTimestamp = System.nanoTime();
            } else {
                this.initializedTimestamp = 0L;
            }
            initialized = true;
        }
    }

    public final OptionValues getOptionValues() {
        return engine.engineOptions;
    }

    public final <T> T getOptionValue(OptionKey<T> key) {
        return TruffleRuntimeOptions.getPolyglotOptionValue(getOptionValues(), key);
    }

    /**
     * Returns <code>true</code> if the call target was already compiled or was compiled
     * synchronously. Returns <code>false</code> if compilation was not scheduled or is happening in
     * the background. Use {@link #isCompiling()} to find out whether it is actually compiling.
     */
    public final boolean compile(boolean lastTierCompilation) {
        if (!needsCompile(lastTierCompilation)) {
            return true;
        }
        if (!isCompiling()) {
            if (!runtime().acceptForCompilation(getRootNode())) {
                // do not try to compile again
                compilationFailed = true;
                return false;
            }

            CancellableCompileTask task = null;
            // Do not try to compile this target concurrently,
            // but do not block other threads if compilation is not asynchronous.
            synchronized (this) {
                if (!needsCompile(lastTierCompilation)) {
                    return true;
                }
                ensureInitialized();
                if (!isCompiling()) {
                    try {
                        this.compilationTask = task = runtime().submitForCompilation(this, lastTierCompilation);
                    } catch (RejectedExecutionException e) {
                        return false;
                    }
                }
            }
            if (task != null) {
                return maybeWaitForTask(task);
            }
        }
        return false;
    }

    public final boolean maybeWaitForTask(CancellableCompileTask task) {
        boolean allowBackgroundCompilation = !engine.performanceWarningsAreFatal &&
                        !engine.compilationExceptionsAreThrown;
        boolean mayBeAsynchronous = allowBackgroundCompilation && engine.backgroundCompilation;
        runtime().finishCompilation(this, task, mayBeAsynchronous);
        // not async compile and compilation successful
        return !mayBeAsynchronous && isValid();
    }

    private boolean needsCompile(boolean isLastTierCompilation) {
        return !isValid() || (engine.multiTier && isLastTierCompilation && !isValidLastTier());
    }

    public final boolean isCompiling() {
        return getCompilationTask() != null;
    }

    /**
     * Gets the address of the machine code for this call target. A non-zero return value denotes
     * the contiguous memory block containing the machine code but does not necessarily represent an
     * entry point for the machine code or even the address of executable instructions. This value
     * is only for informational purposes (e.g., use in a log message).
     */
    public abstract long getCodeAddress();

    /**
     * Determines if this call target has valid machine code attached to it.
     */
    public abstract boolean isValid();

    /**
     * Determines if this call target has valid machine code attached to it, and that this code was
     * compiled in the last tier.
     */
    public abstract boolean isValidLastTier();

    /**
     * Invalidates this call target by invalidating any machine code attached to it.
     *
     * @param source the source object that caused the machine code to be invalidated. For example
     *            the source {@link Node} object. May be {@code null}.
     * @param reason a textual description of the reason why the machine code was invalidated. May
     *            be {@code null}.
     */
    public final void invalidate(Object source, CharSequence reason) {
        cachedNonTrivialNodeCount = -1;
        if (isValid()) {
            invalidateCode();
            runtime().getListener().onCompilationInvalidated(this, source, reason);
        }
        runtime().cancelInstalledTask(this, source, reason);
    }

    final OptimizedCallTarget cloneUninitialized() {
        assert sourceCallTarget == null;
        ensureInitialized();
        RootNode clonedRoot;
        GraalTVMCI tvmci = runtime().getTvmci();
        if (tvmci.isCloneUninitializedSupported(rootNode)) {
            assert uninitializedRootNode == null;
            clonedRoot = tvmci.cloneUninitialized(rootNode);
        } else {
            clonedRoot = NodeUtil.cloneNode(uninitializedRootNode);
        }
        return runtime().createClonedCallTarget(clonedRoot, this);
    }

    /**
     * Gets the speculation log used to collect all failed speculations in the compiled code for
     * this call target. Note that this may differ from the speculation log
     * {@linkplain CompilableTruffleAST#getCompilationSpeculationLog() used for compilation}.
     */
    public SpeculationLog getSpeculationLog() {
        if (speculationLog == null) {
            SPECULATION_LOG_UPDATER.compareAndSet(this, null, ((GraalTruffleRuntime) Truffle.getRuntime()).createSpeculationLog());
        }
        return speculationLog;
    }

    final void setSpeculationLog(SpeculationLog speculationLog) {
        this.speculationLog = speculationLog;
    }

    @Override
    public final JavaConstant asJavaConstant() {
        return GraalTruffleRuntime.getRuntime().forObject(this);
    }

    @SuppressWarnings({"unchecked"})
    static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    @Override
    public final void cancelInstalledTask() {
        runtime().cancelInstalledTask(this, null, "got inlined. callsite count: " + getKnownCallSiteCount());
    }

    @Override
    public final boolean isSameOrSplit(CompilableTruffleAST ast) {
        if (!(ast instanceof OptimizedCallTarget)) {
            return false;
        }
        OptimizedCallTarget other = (OptimizedCallTarget) ast;
        return this == other || this == other.sourceCallTarget || other == this.sourceCallTarget ||
                        (this.sourceCallTarget != null && other.sourceCallTarget != null && this.sourceCallTarget == other.sourceCallTarget);
    }

    final boolean cancelInstalledTask(Node source, CharSequence reason) {
        return runtime().cancelInstalledTask(this, source, reason);
    }

    @Override
    public final void onCompilationFailed(Supplier<String> reasonAndStackTrace, boolean bailout, boolean permanentBailout) {
        if (bailout && !permanentBailout) {
            /*
             * Non-permanent bailouts are expected cases. A non-permanent bailout would be for
             * example class redefinition during code installation. As opposed to permanent
             * bailouts, non-permanent bailouts will trigger recompilation and are not considered a
             * failure state.
             */
        } else {
            compilationFailed = true;
            if (getOptionValue(PolyglotCompilerOptions.CompilationExceptionsAreThrown)) {
                final InternalError error = new InternalError(reasonAndStackTrace.get());
                throw new OptimizationFailedException(error, this);
            }

            boolean truffleCompilationExceptionsAreFatal = TruffleRuntimeOptions.areTruffleCompilationExceptionsFatal(this);
            if (getOptionValue(PolyglotCompilerOptions.CompilationExceptionsArePrinted) || truffleCompilationExceptionsAreFatal) {
                log(reasonAndStackTrace.get());
                if (truffleCompilationExceptionsAreFatal) {
                    log("Exiting VM due to " + (getOptionValue(PolyglotCompilerOptions.CompilationExceptionsAreFatal) ? "TruffleCompilationExceptionsAreFatal"
                                    : "TrufflePerformanceWarningsAreFatal") + "=true");
                    System.exit(-1);
                }
            }
        }
    }

    public static final void log(String message) {
        runtime().log(message);
    }

    @Override
    public final int getKnownCallSiteCount() {
        return callSitesKnown;
    }

    public final OptimizedCallTarget getSourceCallTarget() {
        return sourceCallTarget;
    }

    @Override
    public final String getName() {
        CompilerAsserts.neverPartOfCompilation();
        String result = nameCache;
        if (result == null) {
            result = rootNode.toString();
            nameCache = result;
        }
        return result;
    }

    @Override
    public final String toString() {
        CompilerAsserts.neverPartOfCompilation();
        String superString = rootNode.toString();
        if (isValid()) {
            superString += " <opt>";
        }
        if (sourceCallTarget != null) {
            superString += " <split-" + Integer.toHexString(hashCode()) + ">";
        }
        return superString;
    }

    /**
     * Intrinsifiable compiler directive to tighten the type information for {@code args}.
     *
     * @param length the length of {@code args} that is guaranteed to be final at compile time
     */
    static final Object[] castArrayFixedLength(Object[] args, int length) {
        return args;
    }

    /**
     * Intrinsifiable compiler directive to tighten the type information for {@code value}.
     *
     * @param type the type the compiler should assume for {@code value}
     * @param condition the condition that guards the assumptions expressed by this directive
     * @param nonNull the nullness info the compiler should assume for {@code value}
     * @param exact if {@code true}, the compiler should assume exact type info
     */
    @SuppressWarnings({"unchecked"})
    static <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull, boolean exact) {
        return (T) value;
    }

    /**
     * Intrinsifiable compiler directive for creating a frame.
     */
    public static VirtualFrame createFrame(FrameDescriptor descriptor, Object[] args) {
        return new FrameWithoutBoxing(descriptor, args);
    }

    final void onLoopCount(int count) {
        callAndLoopThreshold -= count;
    }

    @Override
    public final boolean nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        CompilerAsserts.neverPartOfCompilation();
        invalidate(newNode, reason);
        /* Notify compiled method that have inlined this call target that the tree changed. */
        invalidateNodeRewritingAssumption();

        cancelInstalledTask(newNode, reason);
        return false;
    }

    public final void accept(NodeVisitor visitor, TruffleInlining inlingDecision) {
        if (inlingDecision != null) {
            inlingDecision.accept(this, visitor);
        } else {
            getRootNode().accept(visitor);
        }
    }

    public final Iterable<Node> nodeIterable(TruffleInlining inliningDecision) {
        Iterator<Node> iterator = nodeIterator(inliningDecision);
        return () -> iterator;
    }

    public final Iterator<Node> nodeIterator(TruffleInlining inliningDecision) {
        Iterator<Node> iterator;
        if (inliningDecision != null) {
            iterator = inliningDecision.makeNodeIterator(this);
        } else {
            iterator = NodeUtil.makeRecursiveIterator(this.getRootNode());
        }
        return iterator;
    }

    @Override
    public final int getNonTrivialNodeCount() {
        if (cachedNonTrivialNodeCount == -1) {
            cachedNonTrivialNodeCount = calculateNonTrivialNodes(getRootNode());
        }
        return cachedNonTrivialNodeCount;
    }

    @Override
    public final int getCallCount() {
        return -(callThreshold - engine.firstTierCallThreshold);
    }

    public final int getCallAndLoopCount() {
        return -(callAndLoopThreshold - engine.firstTierCallAndLoopThreshold);
    }

    public final long getInitializedTimestamp() {
        return initializedTimestamp;
    }

    public static int calculateNonTrivialNodes(Node node) {
        NonTrivialNodeCountVisitor visitor = new NonTrivialNodeCountVisitor();
        node.accept(visitor);
        return visitor.nodeCount;
    }

    public final Map<String, Object> getDebugProperties(TruffleInlining inlining) {
        Map<String, Object> properties = new LinkedHashMap<>();
        GraalTruffleRuntimeListener.addASTSizeProperty(this, inlining, properties);
        String callsThreshold = String.format("%7d/%5d", getCallCount(), engine.firstTierCallThreshold);
        String loopsThreshold = String.format("%7d/%5d", getCallAndLoopCount(), engine.firstTierCallAndLoopThreshold);
        properties.put("Calls/Thres", callsThreshold);
        properties.put("CallsAndLoop/Thres", loopsThreshold);
        return properties;
    }

    @Override
    public final TruffleCallNode[] getCallNodes() {
        final List<OptimizedDirectCallNode> callNodes = new ArrayList<>();
        getRootNode().accept(new NodeVisitor() {
            @Override
            public boolean visit(Node node) {
                if (node instanceof OptimizedDirectCallNode) {
                    callNodes.add((OptimizedDirectCallNode) node);
                }
                return true;
            }
        });
        return callNodes.toArray(new TruffleCallNode[0]);
    }

    public final CompilerOptions getCompilerOptions() {
        final CompilerOptions options = rootNode.getCompilerOptions();
        if (options != null) {
            return options;
        }
        return DefaultCompilerOptions.INSTANCE;
    }

    /*
     * Call profiling related code.
     */
    /*
     * This should be private but can't be. GR-19397
     */
    public final void profileIndirectCall() {
        Assumption argumentTypesAssumption = profiledArgumentTypesAssumption;
        if (argumentTypesAssumption != null && argumentTypesAssumption.isValid()) {
            // Argument profiling is not possible for targets of indirect calls.
            CompilerDirectives.transferToInterpreter();
            argumentTypesAssumption.invalidate();
            profiledArgumentTypes = null;
        }
    }

    /*
     * This should be private but can't be. GR-19397
     */
    @ExplodeLoop
    public final void profileDirectCall(Object[] args) {
        Assumption typesAssumption = profiledArgumentTypesAssumption;
        if (typesAssumption == null) {
            if (CompilerDirectives.inInterpreter()) {
                initializeProfiledArgumentTypes(args);
            }
        } else {
            Class<?>[] types = profiledArgumentTypes;
            if (types != null) {
                if (types.length != args.length) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    typesAssumption.invalidate();
                    profiledArgumentTypes = null;
                } else if (typesAssumption.isValid()) {
                    for (int i = 0; i < types.length; i++) {
                        Class<?> type = types[i];
                        Object value = args[i];
                        if (type != null && (value == null || value.getClass() != type)) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            updateProfiledArgumentTypes(args, types);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void profileReturnValue(Object result) {
        Assumption returnTypeAssumption = profiledReturnTypeAssumption;
        if (CompilerDirectives.inInterpreter() && returnTypeAssumption == null) {
            // we only profile return values in the interpreter as we don't want to deoptimize
            // for immediate compiles.
            if (engine.returnTypeSpeculation) {
                profiledReturnType = classOf(result);
                profiledReturnTypeAssumption = createValidAssumption(RETURN_TYPE_ASSUMPTION_NAME);
            }
        } else if (profiledReturnType != null) {
            if (result == null || profiledReturnType != result.getClass()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                returnTypeAssumption.invalidate();
                profiledReturnType = null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Throwable> T profileExceptionType(T value) {
        Class<? extends Throwable> clazz = profiledExceptionType;
        if (clazz != Throwable.class) {
            if (clazz != null && value.getClass() == clazz) {
                if (CompilerDirectives.inInterpreter()) {
                    return value;
                } else {
                    return (T) CompilerDirectives.castExact(value, clazz);
                }
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (clazz == null) {
                    profiledExceptionType = value.getClass();
                } else {
                    profiledExceptionType = Throwable.class;
                }
            }
        }
        return value;
    }

    /*
     * This should be private but can't be. GR-19397
     */
    public final Object[] injectArgumentProfile(Object[] originalArguments) {
        Assumption argumentTypesAssumption = profiledArgumentTypesAssumption;
        Object[] args = originalArguments;
        if (argumentTypesAssumption != null && argumentTypesAssumption.isValid()) {
            args = unsafeCast(castArrayFixedLength(args, profiledArgumentTypes.length), Object[].class, true, true, true);
            args = castArgumentsImpl(args);
        }
        return args;
    }

    private Object injectReturnValueProfile(Object result) {
        Class<?> klass = profiledReturnType;
        if (klass != null && CompilerDirectives.inCompiledCode() && profiledReturnTypeAssumption.isValid()) {
            return OptimizedCallTarget.unsafeCast(result, klass, true, true, true);
        }
        return result;
    }

    @ExplodeLoop
    private Object[] castArgumentsImpl(Object[] originalArguments) {
        Class<?>[] types = profiledArgumentTypes;
        Object[] castArguments = new Object[types.length];
        boolean isCallProfiled = callProfiled;
        for (int i = 0; i < types.length; i++) {
            // callProfiled: only the receiver type is exact.
            Class<?> targetType = types[i];
            boolean exact = !isCallProfiled || i == 0;
            castArguments[i] = targetType != null ? unsafeCast(originalArguments[i], targetType, true, true, exact) : originalArguments[i];
        }
        return castArguments;
    }

    private void updateProfiledArgumentTypes(Object[] args, Class<?>[] types) {
        CompilerAsserts.neverPartOfCompilation();
        profiledArgumentTypesAssumption.invalidate();
        for (int j = 0; j < types.length; j++) {
            types[j] = joinTypes(types[j], classOf(args[j]));
        }
        profiledArgumentTypesAssumption = createValidAssumption(ARGUMENT_TYPES_ASSUMPTION_NAME);
    }

    private void initializeProfiledArgumentTypes(Object[] args) {
        CompilerAsserts.neverPartOfCompilation();
        if (args.length <= MAX_PROFILED_ARGUMENTS && engine.argumentTypeSpeculation) {
            Class<?>[] result = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                result[i] = classOf(args[i]);
            }
            profiledArgumentTypes = result;
            profiledArgumentTypesAssumption = createValidAssumption(ARGUMENT_TYPES_ASSUMPTION_NAME);
        } else {
            profiledArgumentTypesAssumption = createInvalidAssumption(ARGUMENT_TYPES_ASSUMPTION_NAME);
        }
    }

    private boolean isValidArgumentProfile(Object[] args) {
        return profiledArgumentTypesAssumption != null && profiledArgumentTypesAssumption.isValid() && checkProfiledArgumentTypes(args, profiledArgumentTypes);
    }

    private static boolean checkProfiledArgumentTypes(Object[] args, Class<?>[] types) {
        assert types != null;
        if (args.length != types.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        // receiver type is always non-null and exact
        if (types[0] != args[0].getClass()) {
            throw new ClassCastException();
        }
        // other argument types may be inexact
        for (int j = 1; j < types.length; j++) {
            if (types[j] == null) {
                continue;
            }
            types[j].cast(args[j]);
            Objects.requireNonNull(args[j]);
        }
        return true;
    }

    private static Class<?> classOf(Object arg) {
        return arg != null ? arg.getClass() : null;
    }

    private static Class<?> joinTypes(Class<?> class1, Class<?> class2) {
        if (class1 == class2) {
            return class1;
        } else {
            return null;
        }
    }

    final void initializeArgumentTypes(Class<?>[] argumentTypes) {
        CompilerAsserts.neverPartOfCompilation();
        if (profiledArgumentTypesAssumption != null) {
            this.profiledArgumentTypesAssumption.invalidate();
            throw new AssertionError("Argument types already initialized. initializeArgumentTypes must be called before any profile is initialized.");
        } else {
            this.profiledArgumentTypes = argumentTypes;
            this.profiledArgumentTypesAssumption = createValidAssumption("Custom profiled argument types");
            this.callProfiled = true;
        }
    }

    private static OptimizedAssumption createInvalidAssumption(String name) {
        OptimizedAssumption result = createValidAssumption(name);
        result.invalidate();
        return result;
    }

    private static OptimizedAssumption createValidAssumption(String name) {
        return (OptimizedAssumption) Truffle.getRuntime().createAssumption(name);
    }

    /*
     * Splitting related code.
     */

    public final boolean isSplit() {
        return sourceCallTarget != null;
    }

    public final OptimizedDirectCallNode getCallSiteForSplit() {
        if (isSplit()) {
            OptimizedDirectCallNode callNode = getSingleCallNode();
            assert callNode != null;
            return callNode;
        } else {
            return null;
        }
    }

    final int getUninitializedNodeCount() {
        assert uninitializedNodeCount >= 0;
        return uninitializedNodeCount;
    }

    private static final class NonTrivialNodeCountVisitor implements NodeVisitor {
        public int nodeCount;

        @Override
        public boolean visit(Node node) {
            if (!node.getCost().isTrivial()) {
                nodeCount++;
            }
            return true;
        }
    }

    @Override
    public final boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    final CancellableCompileTask getCompilationTask() {
        return compilationTask;
    }

    /**
     * This marks the end of the compilation.
     *
     * It may only ever be called by the thread that performed the compilation, and after the
     * compilation is completely done (either successfully or not successfully).
     */
    public final void resetCompilationTask() {
        /*
         * We synchronize because this is called from the compilation threads so we want to make
         * sure we have finished setting the compilationTask in #compile. Otherwise
         * `this.compilationTask = null` might run before then the field is set in #compile and this
         * will get stuck in a "compiling" state.
         */
        synchronized (this) {
            assert this.compilationTask != null;
            this.compilationTask = null;
        }
    }

    @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "All increments and decrements are synchronized.")
    final synchronized void addDirectCallNode(OptimizedDirectCallNode directCallNode) {
        Objects.requireNonNull(directCallNode);
        WeakReference<OptimizedDirectCallNode> nodeRef = singleCallNode;
        if (nodeRef != MULTIPLE_CALLS) {
            // we only remember at most one call site
            if (nodeRef == NO_CALL) {
                singleCallNode = new WeakReference<>(directCallNode);
            } else if (nodeRef.get() == directCallNode) {
                // nothing to do same call site
                return;
            } else {
                singleCallNode = MULTIPLE_CALLS;
            }
        }
        callSitesKnown++;
    }

    @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "All increments and decrements are synchronized.")
    final synchronized void removeDirectCallNode(OptimizedDirectCallNode directCallNode) {
        Objects.requireNonNull(directCallNode);
        WeakReference<OptimizedDirectCallNode> nodeRef = singleCallNode;
        if (nodeRef != MULTIPLE_CALLS) {
            // we only remember at most one call site
            if (nodeRef == NO_CALL) {
                // nothing to do
                return;
            } else if (nodeRef.get() == directCallNode) {
                // reset if its the only call site
                singleCallNode = NO_CALL;
            } else {
                singleCallNode = MULTIPLE_CALLS;
            }
        }
        callSitesKnown--;
    }

    public final boolean isSingleCaller() {
        WeakReference<OptimizedDirectCallNode> nodeRef = singleCallNode;
        if (nodeRef != null) {
            return nodeRef.get() != null;
        }
        return false;
    }

    public final OptimizedDirectCallNode getSingleCallNode() {
        WeakReference<OptimizedDirectCallNode> nodeRef = singleCallNode;
        if (nodeRef != null) {
            return nodeRef.get();
        }
        return null;
    }

    final boolean isNeedsSplit() {
        return needsSplit;
    }

    final void polymorphicSpecialize(Node source) {
        List<Node> toDump = null;
        if (engine.splittingDumpDecisions) {
            toDump = new ArrayList<>();
            pullOutParentChain(source, toDump);
        }
        logPolymorphicEvent(0, "Polymorphic event! Source:", source);
        this.maybeSetNeedsSplit(0, toDump);
    }

    private boolean maybeSetNeedsSplit(int depth, List<Node> toDump) {
        final OptimizedDirectCallNode onlyCaller = getSingleCallNode();
        if (depth > engine.splittingMaxPropagationDepth || needsSplit || callSitesKnown == 0 || getCallCount() == 1) {
            logEarlyReturn(depth, callSitesKnown);
            return needsSplit;
        }
        if (onlyCaller != null) {
            final RootNode callerRootNode = onlyCaller.getRootNode();
            if (callerRootNode != null && callerRootNode.getCallTarget() != null) {
                final OptimizedCallTarget callerTarget = (OptimizedCallTarget) callerRootNode.getCallTarget();
                if (engine.splittingDumpDecisions) {
                    pullOutParentChain(onlyCaller, toDump);
                }
                logPolymorphicEvent(depth, "One caller! Analysing parent.");
                if (callerTarget.maybeSetNeedsSplit(depth + 1, toDump)) {
                    logPolymorphicEvent(depth, "Set needs split to true via parent");
                    needsSplit = true;
                }
            }
        } else {
            logPolymorphicEvent(depth, "Set needs split to true");
            needsSplit = true;
            maybeDump(toDump);
        }

        logPolymorphicEvent(depth, "Return:", needsSplit);
        return needsSplit;
    }

    private void logEarlyReturn(int depth, int numberOfKnownCallNodes) {
        if (engine.splittingTraceEvents) {
            logPolymorphicEvent(depth, "Early return: " + needsSplit + " callCount: " + getCallCount() + ", numberOfKnownCallNodes: " + numberOfKnownCallNodes);
        }
    }

    private void logPolymorphicEvent(int depth, String message) {
        logPolymorphicEvent(depth, message, null);
    }

    private void logPolymorphicEvent(int depth, String message, Object arg) {
        if (engine.splittingTraceEvents) {
            final String indent = new String(new char[depth]).replace("\0", "  ");
            final String argString = (arg == null) ? "" : " " + arg;
            log(String.format(SPLIT_LOG_FORMAT, indent + message + argString, this.toString()));
        }
    }

    private void maybeDump(List<Node> toDump) {
        if (engine.splittingDumpDecisions) {
            final List<OptimizedDirectCallNode> callers = new ArrayList<>();
            OptimizedDirectCallNode callNode = getSingleCallNode();
            if (callNode != null) {
                callers.add(callNode);
            }
            PolymorphicSpecializeDump.dumpPolymorphicSpecialize(this, toDump, callers);
        }
    }

    private static void pullOutParentChain(Node node, List<Node> toDump) {
        Node rootNode = node;
        while (rootNode.getParent() != null) {
            toDump.add(rootNode);
            rootNode = rootNode.getParent();
        }
        toDump.add(rootNode);
    }

    /**
     * Call without verifying the argument profile. Needs to be initialized by
     * {@link GraalTVMCI#initializeProfile(CallTarget, Class[])}. Potentially crashes the VM if the
     * argument profile is incompatible with the actual arguments. Use with caution.
     */
    static class OptimizedCallProfiled extends CallProfiled {
        @Override
        public Object call(CallTarget target, Object... args) {
            OptimizedCallTarget castTarget = (OptimizedCallTarget) target;
            assert castTarget.isValidArgumentProfile(args) : "Invalid argument profile. UnsafeCalls need to explicity initialize the profile.";
            return castTarget.doInvoke(args);
        }
    }

    static class OptimizedCallInlined extends CallInlined {
        @Override
        public Object call(Node callNode, CallTarget target, Object... arguments) {
            try {
                return ((OptimizedCallTarget) target).callInlinedForced(callNode, arguments);
            } catch (Throwable t) {
                OptimizedCallTarget.runtime().getTvmci().onThrowable(callNode, ((OptimizedCallTarget) target), t, null);
                throw OptimizedCallTarget.rethrow(t);
            }
        }
    }

    final void setNonTrivialNodeCount(int nonTrivialNodeCount) {
        this.cachedNonTrivialNodeCount = nonTrivialNodeCount;
    }
}
