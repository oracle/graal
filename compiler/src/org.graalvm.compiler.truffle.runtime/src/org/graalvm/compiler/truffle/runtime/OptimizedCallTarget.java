/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ExceptionAction;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.OptimizationFailedException;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultCompilerOptions;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.ExecutionSignature;
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
 *
 * The end-goal of executing a {@link OptimizedCallTarget} is executing its root node. The following
 * call-graph shows all the paths that can be taken from calling a call target (through all the
 * public <code>call*</code> methods) to the
 * {@linkplain #executeRootNode(VirtualFrame, CompilationState) execution of the root node}
 * depending on the type of call.
 *
 * <pre>
 *              GraalRuntimeSupport#callProfiled                    GraalRuntimeSupport#callInlined
 *                                |                                               |
 *                                |                                               V
 *  PUBLIC   call -> callIndirect | callOSR   callDirect <================> callInlined
 *                           |  +-+    |           |             ^                |
 *                           |  |  +---+           |     substituted by the       |
 *                           V  V  V               |     compiler if inlined      |
 *  PROTECTED               doInvoke <-------------+                              |
 *                             |                                                  |
 *                             | <= Jump to installed code                        |
 *                             V                                                  |
 *  PROTECTED              callBoundary                                           |
 *                             |                                                  |
 *                             | <= Tail jump to installed code in Int.           |
 *                             V                                                  |
 *  PROTECTED           profiledPERoot                                            |
 *                             |                                                  |
 *  PRIVATE                    +----------> executeRootNode <---------------------+
 *                                                 |
 *                                                 V
 *                                         rootNode.execute()
 * </pre>
 */
@SuppressWarnings({"deprecation", "hiding"})
public abstract class OptimizedCallTarget implements CompilableTruffleAST, RootCallTarget, ReplaceObserver {

    private static final String NODE_REWRITING_ASSUMPTION_NAME = "nodeRewritingAssumption";
    private static final String VALID_ROOT_ASSUMPTION_NAME = "validRootAssumption";
    static final String EXECUTE_ROOT_NODE_METHOD_NAME = "executeRootNode";
    private static final AtomicReferenceFieldUpdater<OptimizedCallTarget, SpeculationLog> SPECULATION_LOG_UPDATER = AtomicReferenceFieldUpdater.newUpdater(OptimizedCallTarget.class,
                    SpeculationLog.class, "speculationLog");
    private static final AtomicReferenceFieldUpdater<OptimizedCallTarget, Assumption> NODE_REWRITING_ASSUMPTION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(OptimizedCallTarget.class,
                    Assumption.class, "nodeRewritingAssumption");
    private static final AtomicReferenceFieldUpdater<OptimizedCallTarget, Assumption> VALID_ROOT_ASSUMPTION_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(OptimizedCallTarget.class, Assumption.class, "validRootAssumption");
    private static final AtomicReferenceFieldUpdater<OptimizedCallTarget, ArgumentsProfile> ARGUMENTS_PROFILE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(
                    OptimizedCallTarget.class, ArgumentsProfile.class, "argumentsProfile");
    private static final AtomicReferenceFieldUpdater<OptimizedCallTarget, ReturnProfile> RETURN_PROFILE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(
                    OptimizedCallTarget.class, ReturnProfile.class, "returnProfile");
    private static final WeakReference<OptimizedDirectCallNode> NO_CALL = new WeakReference<>(null);
    private static final WeakReference<OptimizedDirectCallNode> MULTIPLE_CALLS = null;
    private static final String SPLIT_LOG_FORMAT = "[poly-event] %-70s %s";
    private static final int MAX_PROFILED_ARGUMENTS = 256;

    /** The AST to be executed when this call target is called. */
    private final RootNode rootNode;

    /** Whether this call target was cloned, compiled or called. */
    @CompilationFinal protected volatile boolean initialized;
    @CompilationFinal private volatile boolean loaded;

    /**
     * The call threshold is counted up for each real call until it reaches a
     * {@link PolyglotCompilerOptions#FirstTierCompilationThreshold first tier} or
     * {@link PolyglotCompilerOptions#LastTierCompilationThreshold second tier} compilation
     * threshold, and triggers a {@link #compile(boolean) compilation}. It is incremented for each
     * real call to the call target.
     */
    private int callCount;

    /**
     * The call and loop threshold is counted up for each real call until it reaches a
     * {@link PolyglotCompilerOptions#FirstTierCompilationThreshold first tier} or
     * {@link PolyglotCompilerOptions#LastTierCompilationThreshold second tier} compilation
     * threshold, and triggers a {@link #compile(boolean) compilation}. It is incremented for each
     * real call to the call target.
     */
    private int callAndLoopCount;
    private int highestCompiledTier = 0;

    public void compiledTier(int tier) {
        highestCompiledTier = Math.max(highestCompiledTier, tier);
    }

    public int highestCompiledTier() {
        return highestCompiledTier;
    }

    /*
     * Profiling information (types and Assumption) are kept in 2-final-fields objects to ensure to
     * always observe consistent types and assumption. This also enables using atomic operations to
     * update the profiles safely. It is of utmost importance to never use profiled types without
     * checking the Assumption, as otherwise it could cast objects to a different and incorrect
     * type, which would crash.
     *
     * The profiled types are either an exact Class, or if it does not match then the profile type
     * goes directly to null which means unprofiled. This is necessary because checking and casting
     * is done in separate methods (in caller and callee respectively for arguments profiles, and
     * the reverse for return profile). Therefore, we need two reads from the profile field, which
     * means we could get a newer ArgumentsProfile, which would be invalid for the value passed in
     * the CallTarget, except that the newer profile can only be less precise (= more null) and so
     * will end up not casting at all the argument indices that did not match the profile.
     *
     * These fields are initially null, and once they become non-null they never become null again.
     */
    @CompilationFinal private volatile ArgumentsProfile argumentsProfile;
    @CompilationFinal private volatile ReturnProfile returnProfile;
    @CompilationFinal private Class<? extends Throwable> profiledExceptionType;

    /**
     * Was the target already dequeued due to inlining. We keep track of this to prevent
     * continuously dequeuing the target for single caller when the single caller itself has
     * multiple callers.
     */
    private volatile boolean dequeueInlined;
    private volatile boolean aotInitialized;

    public static final class ArgumentsProfile {
        private static final String ARGUMENT_TYPES_ASSUMPTION_NAME = "Profiled Argument Types";
        private static final Class<?>[] EMPTY_ARGUMENT_TYPES = new Class<?>[0];
        private static final ArgumentsProfile INVALID = new ArgumentsProfile();

        // Invariant to simplify conditions: types is non-null if assumption is valid
        final OptimizedAssumption assumption;
        @CompilationFinal(dimensions = 1) final Class<?>[] types;

        private ArgumentsProfile() {
            this.assumption = createInvalidAssumption(ARGUMENT_TYPES_ASSUMPTION_NAME);
            this.types = null;
        }

        private ArgumentsProfile(Class<?>[] types, String assumptionName) {
            assert types != null;
            this.assumption = createValidAssumption(assumptionName);
            this.types = types;
        }

        public OptimizedAssumption getAssumption() {
            return assumption;
        }

        /**
         * The returned array is read-only.
         */
        public Class<?>[] getTypes() {
            return types;
        }
    }

    public static final class ReturnProfile {
        private static final String RETURN_TYPE_ASSUMPTION_NAME = "Profiled Return Type";
        private static final ReturnProfile INVALID = new ReturnProfile();

        // Invariant to simplify conditions: type is non-null if assumption is valid
        final OptimizedAssumption assumption;
        final Class<?> type;

        private ReturnProfile() {
            this.assumption = createInvalidAssumption(RETURN_TYPE_ASSUMPTION_NAME);
            this.type = null;
        }

        private ReturnProfile(Class<?> type) {
            assert type != null;
            this.assumption = createValidAssumption(RETURN_TYPE_ASSUMPTION_NAME);
            this.type = type;
        }

        public OptimizedAssumption getAssumption() {
            return assumption;
        }

        public Class<?> getType() {
            return type;
        }
    }

    /**
     * Set if compilation failed or was ignored. Reset by TruffleBaseFeature after image generation.
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
     * TruffleBaseFeature after image generation.
     */
    private volatile long initializedTimestamp;

    /**
     * When this field is not null, this {@link OptimizedCallTarget} is
     * {@linkplain #isSubmittedForCompilation() submited for compilation}.<br/>
     *
     * It is only set to non-null in {@link #compile(boolean)} in a synchronized block.
     *
     * It is only {@linkplain #resetCompilationTask() set to null} by the
     * {@linkplain CompilationTask task} itself when: 1) The task is canceled before the compilation
     * has started, or 2) The compilation has finished (successfully or not). Canceling the task
     * after the compilation has started does not reset the task until the compilation finishes.
     */
    private volatile CompilationTask compilationTask;

    private volatile boolean needsSplit;

    /**
     * The engine data associated with this call target. Used to cache option lookups and to gather
     * engine specific statistics.
     */
    public final EngineData engine;

    /** Only set for a source CallTarget with a clonable RootNode. */
    private volatile RootNode uninitializedRootNode;

    /**
     * The speculation log to keep track of assumptions taken and failed for previous compilations.
     */
    protected volatile SpeculationLog speculationLog;

    /** Source target if this target was duplicated. */
    private final OptimizedCallTarget sourceCallTarget;

    /**
     * When this call target is inlined, the inlining {@link InstalledCode} registers this
     * assumption. It gets invalidated when a node rewrite in this call target is performed. This
     * ensures that all compiled methods that inline this call target are properly invalidated.
     */
    private volatile Assumption nodeRewritingAssumption;

    /**
     * When this call target is compiled, the resulting {@link InstalledCode} registers this
     * assumption. It gets invalidated when this call target is invalidated.
     */
    private volatile Assumption validRootAssumption;

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
    volatile List<OptimizedCallTarget> blockCompilations;
    public final int id;
    private static final AtomicInteger idCounter = new AtomicInteger(0);

    protected OptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode) {
        assert sourceCallTarget == null || sourceCallTarget.sourceCallTarget == null : "Cannot create a clone of a cloned CallTarget";
        this.sourceCallTarget = sourceCallTarget;
        this.speculationLog = sourceCallTarget != null ? sourceCallTarget.getSpeculationLog() : null;
        this.rootNode = rootNode;
        this.engine = GraalTVMCI.getEngineData(rootNode);
        this.resetCompilationProfile();
        // Do not adopt children of OSRRootNodes; we want to preserve the parent of the child
        // node(s).
        this.uninitializedNodeCount = isOSR() ? -1 : GraalRuntimeAccessor.NODES.adoptChildrenAndCount(rootNode);
        id = idCounter.getAndIncrement();
    }

    final Assumption getNodeRewritingAssumption() {
        Assumption assumption = nodeRewritingAssumption;
        if (assumption == null) {
            assumption = initializeNodeRewritingAssumption();
        }
        return assumption;
    }

    @Override
    public JavaConstant getNodeRewritingAssumptionConstant() {
        return runtime().forObject(getNodeRewritingAssumption());
    }

    final Assumption getValidRootAssumption() {
        Assumption assumption = validRootAssumption;
        if (assumption == null) {
            assumption = initializeValidRootAssumption();
        }
        return assumption;
    }

    @Override
    public JavaConstant getValidRootAssumptionConstant() {
        return runtime().forObject(getValidRootAssumption());
    }

    @Override
    public boolean isTrivial() {
        return GraalRuntimeAccessor.NODES.isTrivial(rootNode);
    }

    /**
     * We intentionally do not synchronize here since as it's not worth the sync costs.
     */
    @Override
    public void dequeueInlined() {
        if (!dequeueInlined) {
            dequeueInlined = true;
            cancelCompilation("Target inlined into only caller");
        }
    }

    /**
     * @return an existing or the newly initialized node rewriting assumption.
     */
    private Assumption initializeNodeRewritingAssumption() {
        return initializeAssumption(NODE_REWRITING_ASSUMPTION_UPDATER, NODE_REWRITING_ASSUMPTION_NAME);
    }

    private Assumption initializeAssumption(AtomicReferenceFieldUpdater<OptimizedCallTarget, Assumption> updater, String name) {
        Assumption newAssumption = runtime().createAssumption(!getOptionValue(PolyglotCompilerOptions.TraceAssumptions) ? name : name + " of " + rootNode);
        if (updater.compareAndSet(this, null, newAssumption)) {
            return newAssumption;
        } else { // if CAS failed, assumption is already initialized; cannot be null after that.
            return Objects.requireNonNull(updater.get(this));
        }
    }

    /** Invalidate node rewriting assumption iff it has been initialized. */
    private void invalidateNodeRewritingAssumption() {
        invalidateAssumption(NODE_REWRITING_ASSUMPTION_UPDATER, "");
    }

    private boolean invalidateAssumption(AtomicReferenceFieldUpdater<OptimizedCallTarget, Assumption> updater, CharSequence reason) {
        Assumption oldAssumption = updater.getAndUpdate(this, prev -> (prev == null) ? null : runtime().createAssumption(prev.getName()));
        if (oldAssumption == null) {
            return false;
        }
        oldAssumption.invalidate((reason != null) ? reason.toString() : "");
        return true;
    }

    /** @return an existing or the newly initialized valid root assumption. */
    private Assumption initializeValidRootAssumption() {
        return initializeAssumption(VALID_ROOT_ASSUMPTION_UPDATER, VALID_ROOT_ASSUMPTION_NAME);
    }

    /** Invalidate valid root assumption iff it has been initialized. */
    private boolean invalidateValidRootAssumption(CharSequence reason) {
        return invalidateAssumption(VALID_ROOT_ASSUMPTION_UPDATER, reason);
    }

    @Override
    public final RootNode getRootNode() {
        return rootNode;
    }

    public final void resetCompilationProfile() {
        this.callCount = 0;
        this.callAndLoopCount = 0;
    }

    @Override
    @TruffleBoundary
    public final Object call(Object... args) {
        // Use the encapsulating node as call site and clear it inside as we cross the call boundary
        EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
        Node prev = encapsulating.set(null);
        try {
            return callIndirect(prev, args);
        } catch (Throwable t) {
            GraalRuntimeAccessor.LANGUAGE.onThrowable(prev, null, t, null);
            throw rethrow(t);
        } finally {
            encapsulating.set(prev);
        }
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    public Object callIndirect(Node location, Object... args) {
        /*
         * Indirect calls should not invalidate existing compilations of the callee, and the callee
         * should still be able to use profiled arguments until an incompatible argument is passed.
         * So we profile arguments for indirect calls too, but behind a truffle boundary.
         */
        profileIndirectArguments(args);

        try {
            return doInvoke(args);
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert keepAlive(location);
        }
    }

    /**
     * In compiled code, this is only used if the callee is <i>not</i> inlined. If the callee is
     * inlined, {@link #callInlined(Node, Object...)} is used instead, which does not profile
     * arguments, as it is estimated redundant. See the docs of {@link OptimizedCallTarget}.
     */
    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    public final Object callDirect(Node location, Object... args) {
        try {
            try {
                Object result;
                profileArguments(args);
                result = doInvoke(args);
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

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    public final Object callInlined(Node location, Object... arguments) {
        try {
            ensureInitialized();
            return executeRootNode(createFrame(getRootNode().getFrameDescriptor(), arguments), getTier());
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert keepAlive(location);
        }
    }

    private static CompilationState getTier() {
        if (CompilerDirectives.inCompiledCode()) {
            if (CompilerDirectives.hasNextTier()) {
                if (CompilerDirectives.inCompilationRoot()) {
                    return CompilationState.FIRST_TIER_ROOT;
                } else {
                    return CompilationState.FIRST_TIER_INLINED;
                }
            } else {
                if (CompilerDirectives.inCompilationRoot()) {
                    return CompilationState.LAST_TIER_ROOT;
                } else {
                    return CompilationState.LAST_TIER_INLINED;
                }
            }
        } else {
            return CompilationState.INTERPRETED;
        }
    }

    private static boolean keepAlive(@SuppressWarnings("unused") Object o) {
        return true;
    }

    // This call method is hidden from stack traces.
    public final Object callOSR(Object... args) {
        return doInvoke(args);
    }

    /**
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
         * the optimized code or if the call target is not yet optimized calls into profiledPERoot
         * directly. In order to avoid deoptimizations in this method it has optimizations disabled.
         * Any additional code here will likely have significant impact on the interpreter call
         * performance.
         */
        if (interpreterCall()) {
            return doInvoke(args);
        }
        return profiledPERoot(args);
    }

    private boolean interpreterCall() {
        boolean bypassedInstalledCode = false;
        if (isValid()) {
            // Native entry stubs were deoptimized => reinstall.
            runtime().bypassedInstalledCode(this);
            bypassedInstalledCode = true;
        }
        ensureInitialized();
        int intCallCount = this.callCount;
        this.callCount = intCallCount == Integer.MAX_VALUE ? intCallCount : ++intCallCount;
        int intLoopCallCount = this.callAndLoopCount;
        this.callAndLoopCount = intLoopCallCount == Integer.MAX_VALUE ? intLoopCallCount : ++intLoopCallCount;

        // Check if call target is hot enough to compile
        if (shouldCompileImpl(intCallCount, intLoopCallCount)) {
            boolean isCompiled = compile(!engine.multiTier);
            /*
             * If we bypassed the installed code chances are high that the code is currently being
             * debugged. This means that returning true for the interpreter call will retry the call
             * boundary. If the call boundary is retried and debug stepping would invalidate the
             * entry stub again then this leads to an inconvenient stack overflow error. In order to
             * avoid this we just do not return true and wait for the second execution to jump to
             * the optimized code. In practice the installed code should rarely be bypassed.
             *
             * This is only important for HotSpot. We can safely ignore this behavior for SVM as
             * there is no regular JDWP debugging supported.
             */
            return isCompiled && (TruffleOptions.AOT || !bypassedInstalledCode);
        }
        return false;
    }

    private boolean shouldCompileImpl(int intCallCount, int intLoopCallCount) {
        return !compilationFailed //
                        && !isSubmittedForCompilation() //
                        /*
                         * Compilation of OSR loop nodes is managed separately.
                         */
                        && !isOSR() //
                        && intCallCount >= engine.callThresholdInInterpreter //
                        && intLoopCallCount >= scaledThreshold(engine.callAndLoopThresholdInInterpreter); //
    }

    private static int scaledThreshold(int callAndLoopThresholdInInterpreter) {
        return FixedPointMath.multiply(runtime().compilationThresholdScale(), callAndLoopThresholdInInterpreter);
    }

    public final boolean shouldCompile() {
        return !isValid() && shouldCompileImpl(this.callCount, this.callAndLoopCount);
    }

    public final boolean wasExecuted() {
        return this.callCount > 0 || this.callAndLoopCount > 0;
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    protected final Object profiledPERoot(Object[] originalArguments) {
        Object[] args = originalArguments;
        if (!CompilerDirectives.inInterpreter() && CompilerDirectives.hasNextTier()) {
            firstTierCall();
        }
        if (CompilerDirectives.inCompiledCode()) {
            args = injectArgumentsProfile(originalArguments);
        }
        Object result = executeRootNode(createFrame(getRootNode().getFrameDescriptor(), args), getTier());
        profileReturnValue(result);
        return result;
    }

    // This should be private but can't be. GR-19397
    public final boolean firstTierCall() {
        // this is partially evaluated so the second part should fold to a constant.
        int firstTierCallCount = this.callCount;
        this.callCount = firstTierCallCount == Integer.MAX_VALUE ? firstTierCallCount : ++firstTierCallCount;
        int firstTierLoopCallCount = this.callAndLoopCount;
        this.callAndLoopCount = firstTierLoopCallCount == Integer.MAX_VALUE ? firstTierLoopCallCount : ++firstTierLoopCallCount;
        if (!compilationFailed //
                        && !isSubmittedForCompilation()//
                        && firstTierCallCount >= engine.callThresholdInFirstTier //
                        && firstTierLoopCallCount >= scaledThreshold(engine.callAndLoopThresholdInFirstTier)) {
            return lastTierCompile();
        }
        return false;
    }

    @TruffleBoundary
    private boolean lastTierCompile() {
        return compile(true);
    }

    private Object executeRootNode(VirtualFrame frame, CompilationState tier) {
        try {
            Object toRet = rootNode.execute(frame);
            TruffleSafepoint.poll(rootNode);
            return toRet;
        } catch (ControlFlowException t) {
            throw rethrow(profileExceptionType(t));
        } catch (Throwable t) {
            Throwable profiledT = profileExceptionType(t);
            GraalRuntimeAccessor.LANGUAGE.onThrowable(null, this, profiledT, frame);
            throw rethrow(profiledT);
        } finally {
            if (CompilerDirectives.inInterpreter() && tier != CompilationState.INTERPRETED) {
                notifyDeoptimized(frame);
            }
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert frame != null && this != null && tier != null;
        }
    }

    private void notifyDeoptimized(VirtualFrame frame) {
        runtime().getListener().onCompilationDeoptimized(this, frame);
    }

    protected static GraalTruffleRuntime runtime() {
        return (GraalTruffleRuntime) Truffle.getRuntime();
    }

    // This should be private but can't be due to SVM bug.
    public final void ensureInitialized() {
        if (!initialized) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initialize(true);
        }
    }

    public final boolean isInitialized() {
        return initialized;
    }

    private synchronized void initialize(boolean validate) {
        if (!initialized) {
            if (sourceCallTarget == null && rootNode.isCloningAllowed() && !GraalRuntimeAccessor.NODES.isCloneUninitializedSupported(rootNode)) {
                // We are the source CallTarget, so make a copy.
                this.uninitializedRootNode = NodeUtil.cloneNode(rootNode);
            }

            assert !validate || GraalRuntimeAccessor.NODES.getCallTargetWithoutInitialization(rootNode) == this : "Call target out of sync.";

            GraalRuntimeAccessor.INSTRUMENT.onFirstExecution(getRootNode(), validate);
            if (engine.callTargetStatistics) {
                this.initializedTimestamp = System.nanoTime();
            } else {
                this.initializedTimestamp = 0L;
            }
            initialized = true;
        }
    }

    public final OptionValues getOptionValues() {
        return engine.getEngineOptions();
    }

    public final <T> T getOptionValue(OptionKey<T> key) {
        return getOptionValues().get(key);
    }

    /**
     * Returns <code>true</code> if this target can be compiled in principle, else
     * <code>false</code>.
     */
    final boolean acceptForCompilation() {
        return engine.acceptForCompilation(getRootNode());
    }

    final boolean isCompilationFailed() {
        return compilationFailed;
    }

    /**
     * Returns <code>true</code> if the call target was already compiled or was compiled
     * synchronously. Returns <code>false</code> if compilation was not scheduled or is happening in
     * the background. Use {@link #isSubmittedForCompilation()} to find out whether it is submitted
     * for compilation.
     */
    public final boolean compile(boolean lastTierCompilation) {
        boolean lastTier = !engine.firstTierOnly && lastTierCompilation;
        if (!needsCompile(lastTier)) {
            return true;
        }
        if (!isSubmittedForCompilation()) {
            if (!engine.acceptForCompilation(getRootNode())) {
                // do not try to compile again
                compilationFailed = true;
                return false;
            }

            CompilationTask task = null;
            // Do not try to compile this target concurrently,
            // but do not block other threads if compilation is not asynchronous.
            synchronized (this) {
                if (!needsCompile(lastTier)) {
                    return true;
                }

                ensureInitialized();
                if (!isSubmittedForCompilation()) {
                    if (!wasExecuted() && !engine.backgroundCompilation) {
                        prepareForAOTImpl();
                    }

                    try {
                        assert compilationTask == null;
                        this.compilationTask = task = runtime().submitForCompilation(this, lastTier);
                    } catch (RejectedExecutionException e) {
                        return false;
                    }
                }
            }
            if (task != null) {
                runtime().getListener().onCompilationQueued(this, lastTier ? 2 : 1);
                return maybeWaitForTask(task);
            }
        }
        return false;
    }

    public final boolean maybeWaitForTask(CompilationTask task) {
        boolean mayBeAsynchronous = engine.backgroundCompilation;
        runtime().finishCompilation(this, task, mayBeAsynchronous);
        // not async compile and compilation successful
        return !mayBeAsynchronous && isValid();
    }

    public final boolean needsCompile(boolean isLastTierCompilation) {
        return !isValid() || (engine.multiTier && isLastTierCompilation && !isValidLastTier());
    }

    public final boolean isSubmittedForCompilation() {
        return compilationTask != null;
    }

    public final void waitForCompilation() {
        CompilationTask task = compilationTask;
        if (task != null) {
            runtime().finishCompilation(this, task, false);
        }
    }

    boolean isCompiling() {
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
     * Determines if this call target has valid machine code that can be entered attached to it.
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
     * @param reason a textual description of the reason why the machine code was invalidated. May
     *            be {@code null}.
     *
     * @return imprecise: whether code has possibly been invalidated or a compilation has been
     *         cancelled. Returns {@code false} only if both are guaranteed to not have happened,
     *         {@code true} otherwise.
     */
    public final boolean invalidate(CharSequence reason) {
        cachedNonTrivialNodeCount = -1;
        boolean invalidated = invalidateValidRootAssumption(reason);
        return cancelCompilation(reason) || invalidated;
    }

    final OptimizedCallTarget cloneUninitialized() {
        assert sourceCallTarget == null : "Cannot clone a clone.";
        ensureInitialized();
        RootNode clonedRoot = GraalRuntimeAccessor.NODES.cloneUninitialized(this, rootNode, uninitializedRootNode);
        return (OptimizedCallTarget) clonedRoot.getCallTarget();
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
    public final boolean isSameOrSplit(CompilableTruffleAST ast) {
        if (!(ast instanceof OptimizedCallTarget)) {
            return false;
        }
        OptimizedCallTarget other = (OptimizedCallTarget) ast;
        return this == other || this == other.sourceCallTarget || other == this.sourceCallTarget ||
                        (this.sourceCallTarget != null && other.sourceCallTarget != null && this.sourceCallTarget == other.sourceCallTarget);
    }

    @Override
    public boolean cancelCompilation(CharSequence reason) {
        if (!initialized) {
            /* no cancellation necessary if the call target was initialized */
            return false;
        }
        CompilationTask task = this.compilationTask;
        if (cancelAndResetCompilationTask()) {
            runtime().getListener().onCompilationDequeued(this, null, reason, task != null ? task.tier() : 0);
            return true;
        }
        return false;
    }

    private boolean cancelAndResetCompilationTask() {
        CompilationTask task = this.compilationTask;
        if (task != null) {
            synchronized (this) {
                task = this.compilationTask;
                if (task != null) {
                    return task.cancel();
                }
            }
        }
        return false;
    }

    /**
     * Computes block compilation using {@link BlockNode} APIs. If no block node is used in the AST
     * or block node compilation is disabled then this method always returns <code>false</code>.
     */
    public final boolean computeBlockCompilations() {
        if (blockCompilations == null) {
            this.blockCompilations = OptimizedBlockNode.preparePartialBlockCompilations(this);
            if (!blockCompilations.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public final boolean onInvalidate(Object source, CharSequence reason, boolean wasActive) {
        cachedNonTrivialNodeCount = -1;
        if (wasActive) {
            GraalTruffleRuntime.getRuntime().getListener().onCompilationInvalidated(this, source, reason);
        }
        return cancelCompilation(reason) || wasActive;
    }

    @Override
    public final void onCompilationFailed(Supplier<String> serializedException, boolean silent, boolean bailout, boolean permanentBailout, boolean graphTooBig) {
        if (graphTooBig) {
            if (computeBlockCompilations()) {
                // retry compilation
                return;
            }
        }

        ExceptionAction action;
        if (bailout && !permanentBailout) {
            /*
             * Non-permanent bailouts are expected cases. A non-permanent bailout would be for
             * example class redefinition during code installation. As opposed to permanent
             * bailouts, non-permanent bailouts will trigger recompilation and are not considered a
             * failure state.
             */
            action = ExceptionAction.Silent;
        } else {
            compilationFailed = true;
            action = silent ? ExceptionAction.Silent : engine.compilationFailureAction;
        }
        if (action == ExceptionAction.Throw) {
            final InternalError error = new InternalError(serializedException.get());
            throw new OptimizationFailedException(error, this);
        }
        if (action.ordinal() >= ExceptionAction.Print.ordinal()) {
            GraalTruffleRuntime rt = runtime();
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("AST", getNonTrivialNodeCount());
            rt.logEvent(this, 0, "opt fail", toString(), properties, serializedException.get());
            if (action == ExceptionAction.ExitVM) {
                String reason;
                if (getOptionValue(PolyglotCompilerOptions.CompilationFailureAction) == ExceptionAction.ExitVM) {
                    reason = "engine.CompilationFailureAction=ExitVM";
                } else if (getOptionValue(PolyglotCompilerOptions.CompilationExceptionsAreFatal)) {
                    reason = "engine.CompilationExceptionsAreFatal=true";
                } else {
                    reason = "engine.PerformanceWarningsAreFatal=true";
                }
                log(String.format("Exiting VM due to %s", reason));
                System.exit(-1);
            }
        }
    }

    public final void log(String message) {
        runtime().log(this, message);
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
            if (sourceCallTarget != null) {
                result += " <split-" + id + ">";
            }
            nameCache = result;
        }
        return result;
    }

    @Override
    public final String toString() {
        return getName();
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
        assert count >= 0;
        int oldLoopCallCount = this.callAndLoopCount;
        int newLoopCallCount = oldLoopCallCount + count;
        this.callAndLoopCount = newLoopCallCount >= oldLoopCallCount ? newLoopCallCount : Integer.MAX_VALUE;
    }

    @Override
    public final boolean nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        CompilerAsserts.neverPartOfCompilation();
        invalidate(reason);
        /*
         * Notify compiled method that have inlined this call target that the tree changed. It also
         * ensures that compiled code that might be installed by currently running compilation task
         * that can no longer be cancelled is invalidated.
         */
        invalidateNodeRewritingAssumption();
        return false;
    }

    public final void accept(NodeVisitor visitor) {
        getRootNode().accept(visitor);
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
        return callCount;
    }

    public final int getCallAndLoopCount() {
        return callAndLoopCount;
    }

    public final long getInitializedTimestamp() {
        return initializedTimestamp;
    }

    public static int calculateNonTrivialNodes(Node node) {
        NonTrivialNodeCountVisitor visitor = new NonTrivialNodeCountVisitor();
        node.accept(visitor);
        return visitor.nodeCount;
    }

    public final Map<String, Object> getDebugProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        GraalTruffleRuntimeListener.addASTSizeProperty(this, properties);
        String callsThresholdInInterpreter = String.format("%7d/%5d", getCallCount(), engine.callThresholdInInterpreter);
        String loopsThresholdInInterpreter = String.format("%7d/%5d", getCallAndLoopCount(), engine.callAndLoopThresholdInInterpreter);
        if (engine.multiTier) {
            if (isValidLastTier()) {
                String callsThresholdInFirstTier = String.format("%7d/%5d", getCallCount(), engine.callThresholdInFirstTier);
                String loopsThresholdInFirstTier = String.format("%7d/%5d", getCallCount(), engine.callAndLoopThresholdInFirstTier);
                properties.put("Tier", "2");
                properties.put("Calls/Thres", callsThresholdInFirstTier);
                properties.put("CallsAndLoop/Thres", loopsThresholdInFirstTier);
            } else {
                properties.put("Tier", "1");
                properties.put("Calls/Thres", callsThresholdInInterpreter);
                properties.put("CallsAndLoop/Thres", loopsThresholdInInterpreter);
            }
        } else {
            properties.put("Calls/Thres", callsThresholdInInterpreter);
            properties.put("CallsAndLoop/Thres", loopsThresholdInInterpreter);
        }
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
     * Profiling related code.
     */

    // region Arguments profiling

    // region Manual Arguments profiling
    final void initializeUnsafeArgumentTypes(Class<?>[] argumentTypes) {
        CompilerAsserts.neverPartOfCompilation();
        ArgumentsProfile newProfile = new ArgumentsProfile(argumentTypes, "Custom profiled argument types");
        if (updateArgumentsProfile(null, newProfile)) {
            this.callProfiled = true;
        } else {
            transitionToInvalidArgumentsProfile();
            throw new AssertionError("Argument types already initialized. initializeArgumentTypes() must be called before any profile is initialized.");
        }
    }

    final boolean isValidArgumentProfile(Object[] args) {
        assert callProfiled;
        ArgumentsProfile argumentsProfile = this.argumentsProfile;
        return argumentsProfile.assumption.isValid() && checkProfiledArgumentTypes(args, argumentsProfile.types);
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
    // endregion

    private void transitionToInvalidArgumentsProfile() {
        while (true) {
            ArgumentsProfile oldProfile = argumentsProfile;
            if (oldProfile == ArgumentsProfile.INVALID) {
                /* Profile already invalid, nothing to do. */
                return;
            }
            if (updateArgumentsProfile(oldProfile, ArgumentsProfile.INVALID)) {
                return;
            }
            /* We lost the race, try again. */
        }
    }

    private boolean updateArgumentsProfile(ArgumentsProfile oldProfile, ArgumentsProfile newProfile) {
        if (oldProfile != null) {
            /*
             * The assumption for the old profile must be invalidated before installing a new
             * profile.
             */
            oldProfile.assumption.invalidate();
        }
        return ARGUMENTS_PROFILE_UPDATER.compareAndSet(this, oldProfile, newProfile);
    }

    @TruffleBoundary
    private void profileIndirectArguments(Object[] args) {
        profileArguments(args);
    }

    // This should be private but can't be. GR-19397
    @ExplodeLoop
    public final void profileArguments(Object[] args) {
        assert !callProfiled;
        ArgumentsProfile argumentsProfile = this.argumentsProfile;
        if (argumentsProfile == null) {
            if (CompilerDirectives.inInterpreter()) {
                initializeProfiledArgumentTypes(args);
            }
        } else {
            Class<?>[] types = argumentsProfile.types;
            if (types != null) {
                if (types.length != args.length) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    transitionToInvalidArgumentsProfile();
                } else if (argumentsProfile.assumption.isValid()) {
                    for (int i = 0; i < types.length; i++) {
                        Class<?> type = types[i];
                        Object value = args[i];
                        if (type != null && (value == null || value.getClass() != type)) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            updateProfiledArgumentTypes(args, argumentsProfile);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void initializeProfiledArgumentTypes(Object[] args) {
        CompilerAsserts.neverPartOfCompilation();
        assert !callProfiled;
        final ArgumentsProfile newProfile;
        if (args.length <= MAX_PROFILED_ARGUMENTS && engine.argumentTypeSpeculation) {
            Class<?>[] types = args.length == 0 ? ArgumentsProfile.EMPTY_ARGUMENT_TYPES : new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                types[i] = classOf(args[i]);
            }
            newProfile = new ArgumentsProfile(types, ArgumentsProfile.ARGUMENT_TYPES_ASSUMPTION_NAME);
        } else {
            newProfile = ArgumentsProfile.INVALID;
        }

        if (!updateArgumentsProfile(null, newProfile)) {
            // Another thread initialized the profile, we need to check it
            profileArguments(args);
        }
    }

    private void updateProfiledArgumentTypes(Object[] args, ArgumentsProfile oldProfile) {
        CompilerAsserts.neverPartOfCompilation();
        assert !callProfiled;
        Class<?>[] oldTypes = oldProfile.types;
        Class<?>[] newTypes = new Class<?>[oldProfile.types.length];
        for (int j = 0; j < oldTypes.length; j++) {
            newTypes[j] = joinTypes(oldTypes[j], classOf(args[j]));
        }

        ArgumentsProfile newProfile = new ArgumentsProfile(newTypes, ArgumentsProfile.ARGUMENT_TYPES_ASSUMPTION_NAME);
        if (!updateArgumentsProfile(oldProfile, newProfile)) {
            // Another thread updated the profile, we need to retry
            profileArguments(args);
        }
    }

    // This should be private but can't be. GR-19397
    public final Object[] injectArgumentsProfile(Object[] originalArguments) {
        assert CompilerDirectives.inCompiledCode();
        ArgumentsProfile argumentsProfile = this.argumentsProfile;
        Object[] args = originalArguments;
        if (argumentsProfile != null && argumentsProfile.assumption.isValid()) {
            Class<?>[] types = argumentsProfile.types;
            args = unsafeCast(castArrayFixedLength(args, types.length), Object[].class, true, true, true);
            args = castArgumentsImpl(args, types);
        }
        return args;
    }

    @ExplodeLoop
    private Object[] castArgumentsImpl(Object[] originalArguments, Class<?>[] types) {
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

    protected final ArgumentsProfile getInitializedArgumentsProfile() {
        if (argumentsProfile == null) {
            /*
             * We always need an assumption. If this method is called before the profile was
             * initialized, we have to be conservative and disable profiling.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateArgumentsProfile(null, ArgumentsProfile.INVALID);
            /* It does not matter if we lost the race, any non-null profile is sufficient. */
            assert argumentsProfile != null;
        }

        return argumentsProfile;
    }

    // endregion
    // region Return value profiling

    private void profileReturnValue(Object result) {
        ReturnProfile returnProfile = this.returnProfile;
        if (returnProfile == null) {
            // we only profile return values in the interpreter as we don't want to deoptimize for
            // immediate compiles.
            if (CompilerDirectives.inInterpreter() && engine.returnTypeSpeculation) {
                final Class<?> type = classOf(result);
                ReturnProfile newProfile = type == null ? ReturnProfile.INVALID : new ReturnProfile(type);
                if (!RETURN_PROFILE_UPDATER.compareAndSet(this, null, newProfile)) {
                    // Another thread initialized the profile, we need to check it
                    profileReturnValue(result);
                }
            }
        } else {
            if (returnProfile.assumption.isValid() && (result == null || returnProfile.type != result.getClass())) {
                // Immediately go to invalid, do not try to widen the type.
                // See the comment above the returnProfile field.
                CompilerDirectives.transferToInterpreterAndInvalidate();
                /*
                 * The assumption for the old profile must be invalidated before installing a new
                 * profile.
                 */
                returnProfile.assumption.invalidate();
                ReturnProfile previous = RETURN_PROFILE_UPDATER.getAndSet(this, ReturnProfile.INVALID);
                assert previous == returnProfile || previous == ReturnProfile.INVALID;
            }
        }
    }

    private Object injectReturnValueProfile(Object result) {
        ReturnProfile returnProfile = this.returnProfile;
        if (CompilerDirectives.inCompiledCode() && returnProfile != null && returnProfile.assumption.isValid()) {
            return OptimizedCallTarget.unsafeCast(result, returnProfile.type, true, true, true);
        }
        return result;
    }

    protected final ReturnProfile getInitializedReturnProfile() {
        if (returnProfile == null) {
            /*
             * We always need an assumption. If this method is called before the profile was
             * initialized, we have to be conservative and disable profiling.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            RETURN_PROFILE_UPDATER.compareAndSet(this, null, ReturnProfile.INVALID);
            assert returnProfile != null;
        }

        return returnProfile;
    }

    // endregion
    // region Exception profiling

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

    // endregion

    private static Class<?> classOf(Object arg) {
        return arg != null ? arg.getClass() : null;
    }

    private static Class<?> joinTypes(Class<?> class1, Class<?> class2) {
        // Immediately give up for that argument, do not try to widen the type.
        // See the comment above the argumentsProfile field.
        if (class1 == class2) {
            return class1;
        } else {
            return null;
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

    final CompilationTask getCompilationTask() {
        return compilationTask;
    }

    /**
     * This marks the end or cancellation of the compilation.
     *
     * Once the compilation has started it may only ever be called by the thread performing the
     * compilation, and after the compilation is completely done (either successfully or not
     * successfully).
     */
    final synchronized void resetCompilationTask() {
        /*
         * We synchronize because this is called from the compilation threads so we want to make
         * sure we have finished setting the compilationTask in #compile. Otherwise
         * `this.compilationTask = null` might run before then the field is set in #compile and this
         * will get stuck in a "compiling" state.
         */
        assert this.compilationTask != null;
        this.compilationTask = null;
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

    public final void resetNeedsSplit() {
        needsSplit = false;
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
            PolymorphicSpecializeDump.dumpPolymorphicSpecialize(this, toDump);
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

    final void setNonTrivialNodeCount(int nonTrivialNodeCount) {
        this.cachedNonTrivialNodeCount = nonTrivialNodeCount;
    }

    final boolean isLoaded() {
        return loaded;
    }

    final void setLoaded() {
        CompilerAsserts.neverPartOfCompilation();
        this.loaded = true;
    }

    public final boolean prepareForAOT() {
        if (wasExecuted()) {
            throw new IllegalStateException("Cannot prepare for AOT if call target was already executed.");
        }
        /*
         * We do not validate the call target as we are not technically entered in any context when
         * we do AOT compilation.
         */
        initialize(false);
        return prepareForAOTImpl();
    }

    private boolean prepareForAOTImpl() {
        if (aotInitialized) {
            return false;
        }

        ExecutionSignature profile = GraalRuntimeAccessor.NODES.prepareForAOT(rootNode);
        if (profile == null) {
            return false;
        }
        if (callProfiled) {
            // call profile already initialized
            return true;
        }

        assert returnProfile == null : "return profile already initialized";
        assert argumentsProfile == null : "argument profile already initialized";

        Class<?>[] argumentTypes = profile.getArgumentTypes();

        ArgumentsProfile newProfile;
        if (argumentTypes != null && argumentTypes.length <= MAX_PROFILED_ARGUMENTS && engine.argumentTypeSpeculation) {
            newProfile = new ArgumentsProfile(argumentTypes, ArgumentsProfile.ARGUMENT_TYPES_ASSUMPTION_NAME);
        } else {
            newProfile = ArgumentsProfile.INVALID;
        }

        ReturnProfile returnProfile;
        Class<?> returnType = profile.getReturnType();
        if (returnType != null && returnType != Object.class) {
            returnProfile = new ReturnProfile(returnType);
        } else {
            returnProfile = ReturnProfile.INVALID;
        }

        this.returnProfile = returnProfile;
        this.argumentsProfile = newProfile;
        this.aotInitialized = true;
        return true;
    }

    boolean isOSR() {
        return rootNode instanceof BaseOSRRootNode;
    }
}
