/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
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

    /** The AST to be executed when this call target is called. */
    private final RootNode rootNode;
    final EngineData engineData;

    /** Information about when and how the call target should get compiled. */
    @CompilationFinal protected volatile OptimizedCompilationProfile compilationProfile;

    /** Source target if this target was duplicated. */
    private final OptimizedCallTarget sourceCallTarget;

    /** Only set for a source CallTarget with a clonable RootNode. */
    private volatile RootNode uninitializedRootNode;

    private volatile int cachedNonTrivialNodeCount = -1;
    private volatile SpeculationLog speculationLog;
    private volatile int callSitesKnown;

    private static final AtomicReferenceFieldUpdater<OptimizedCallTarget, SpeculationLog> SPECULATION_LOG_UPDATER = AtomicReferenceFieldUpdater.newUpdater(OptimizedCallTarget.class,
                    SpeculationLog.class, "speculationLog");

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

    /**
     * When this call target is inlined, the inlining {@link InstalledCode} registers this
     * assumption. It gets invalidated when a node rewrite in this call target is performed. This
     * ensures that all compiled methods that inline this call target are properly invalidated.
     */
    private volatile Assumption nodeRewritingAssumption;
    private static final AtomicReferenceFieldUpdater<OptimizedCallTarget, Assumption> NODE_REWRITING_ASSUMPTION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(OptimizedCallTarget.class,
                    Assumption.class, "nodeRewritingAssumption");
    private volatile OptimizedDirectCallNode callSiteForSplit;
    @CompilationFinal private volatile String nameCache;
    private final int uninitializedNodeCount;

    private final List<WeakReference<OptimizedDirectCallNode>> knownCallNodes;
    private boolean needsSplit;
    private static final String SPLIT_LOG_FORMAT = "[truffle] [poly-event] %-70s %s";

    public OptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode) {
        assert sourceCallTarget == null || sourceCallTarget.sourceCallTarget == null : "Cannot create a clone of a cloned CallTarget";
        this.sourceCallTarget = sourceCallTarget;
        this.speculationLog = sourceCallTarget != null ? sourceCallTarget.getSpeculationLog() : null;
        this.rootNode = rootNode;
        final GraalTVMCI tvmci = runtime().getTvmci();
        engineData = tvmci.getEngineData(rootNode);
        uninitializedNodeCount = tvmci.adoptChildrenAndCount(this.rootNode);
        knownCallNodes = engineData.options.isLegacySplitting() ? null : new ArrayList<>(1);
        tvmci.setCallTarget(rootNode, this);
    }

    public Assumption getNodeRewritingAssumption() {
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
                        !TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TraceTruffleAssumptions) ? NODE_REWRITING_ASSUMPTION_NAME : NODE_REWRITING_ASSUMPTION_NAME + " of " + rootNode);
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

    public final OptimizedCompilationProfile getCompilationProfile() {
        OptimizedCompilationProfile profile = compilationProfile;
        if (profile != null) {
            return profile;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return initialize();
        }
    }

    public final void resetCompilationProfile() {
        this.compilationProfile = createCompilationProfile();
    }

    protected List<OptimizedAssumption> getProfiledTypesAssumptions() {
        return getCompilationProfile().getProfiledTypesAssumptions();
    }

    protected Class<?>[] getProfiledArgumentTypes() {
        return getCompilationProfile().getProfiledArgumentTypes();
    }

    protected Class<?> getProfiledReturnType() {
        return getCompilationProfile().getProfiledReturnType();
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
            OptimizedCompilationProfile profile = compilationProfile;
            if (profile != null) {
                profile.profileIndirectCall();
            }
            return doInvoke(args);
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert keepAlive(location);
        }
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    public final Object callDirect(Node location, Object... args) {
        try {
            getCompilationProfile().profileDirectCall(args);
            try {
                Object result = doInvoke(args);
                if (CompilerDirectives.inCompiledCode()) {
                    result = compilationProfile.injectReturnValueProfile(result);
                }
                return result;
            } catch (Throwable t) {
                throw rethrow(compilationProfile.profileExceptionType(t));
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
        try {
            getCompilationProfile().profileInlinedCall();
            return callProxy(createFrame(getRootNode().getFrameDescriptor(), arguments));
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert keepAlive(location);
        }
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    public final Object callInlinedForced(Node location, Object... arguments) {
        try {
            getCompilationProfile().profileInlinedCall();
            return callProxy(createFrame(getRootNode().getFrameDescriptor(), arguments));
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert keepAlive(location);
        }
    }

    protected Object doInvoke(Object[] args) {
        return callBoundary(args);
    }

    @TruffleCallBoundary
    protected final Object callBoundary(Object[] args) {
        if (CompilerDirectives.inInterpreter()) {
            // We are called and we are still in Truffle interpreter mode.
            if (isValid()) {
                // Native entry stubs were deoptimized => reinstall.
                runtime().bypassedInstalledCode();
            }
            if (getCompilationProfile().interpreterCall(this)) {
                // synchronous compile -> call again to take us to the compiled code
                return doInvoke(args);
            }
        } else {
            // We come here from compiled code
        }
        return callRoot(args);
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    protected final Object callRoot(Object[] originalArguments) {
        if (GraalCompilerDirectives.inFirstTier()) {
            getCompilationProfile().firstTierCall(this);
        }
        Object[] args = originalArguments;
        OptimizedCompilationProfile profile = this.compilationProfile;
        if (CompilerDirectives.inCompiledCode() && profile != null) {
            args = profile.injectArgumentProfile(originalArguments);
        }
        Object result = callProxy(createFrame(getRootNode().getFrameDescriptor(), args));
        if (profile != null) {
            profile.profileReturnValue(result);
        }
        return result;
    }

    protected final Object callProxy(VirtualFrame frame) {
        final boolean inCompiled = CompilerDirectives.inCompilationRoot();
        try {
            return getRootNode().execute(frame);
        } catch (ControlFlowException t) {
            throw rethrow(getCompilationProfile().profileExceptionType(t));
        } catch (Throwable t) {
            Throwable profiledT = getCompilationProfile().profileExceptionType(t);
            runtime().getTvmci().onThrowable(null, this, profiledT, frame);
            throw rethrow(profiledT);
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert frame != null && this != null;
            if (CompilerDirectives.inInterpreter() && inCompiled) {
                if (!isValid()) {
                    getCompilationProfile().reportInvalidated();
                }
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

    private synchronized OptimizedCompilationProfile initialize() {
        OptimizedCompilationProfile profile = this.compilationProfile;
        if (profile == null) {
            GraalTVMCI tvmci = runtime().getTvmci();
            if (sourceCallTarget == null && rootNode.isCloningAllowed() && !tvmci.isCloneUninitializedSupported(rootNode)) {
                // We are the source CallTarget, so make a copy.
                this.uninitializedRootNode = NodeUtil.cloneNode(rootNode);
            }
            tvmci.onFirstExecution(this);
            this.compilationProfile = profile = createCompilationProfile();
        }
        return profile;
    }

    public final OptionValues getOptionValues() {
        return runtime().getTvmci().getCompilerOptionValues(rootNode);
    }

    private OptimizedCompilationProfile createCompilationProfile() {
        return OptimizedCompilationProfile.create(PolyglotCompilerOptions.getPolyglotValues(rootNode));
    }

    /**
     * @deprecated Please use {@code compile(boolean)} instead.
     */
    @Deprecated
    public final boolean compile() {
        return compile(true);
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
                getCompilationProfile().reportCompilationIgnored();
                return false;
            }

            CancellableCompileTask task = null;
            // Do not try to compile this target concurrently,
            // but do not block other threads if compilation is not asynchronous.
            synchronized (this) {
                if (!needsCompile(lastTierCompilation)) {
                    return true;
                }
                if (this.compilationProfile == null) {
                    initialize();
                }
                if (!isCompiling()) {
                    this.compilationTask = task = runtime().submitForCompilation(this, lastTierCompilation);
                }
            }
            if (task != null) {
                boolean allowBackgroundCompilation = !TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TrufflePerformanceWarningsAreFatal) &&
                                !TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreThrown);
                boolean mayBeAsynchronous = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleBackgroundCompilation) && allowBackgroundCompilation;
                runtime().finishCompilation(this, task, mayBeAsynchronous);
                return !mayBeAsynchronous;
            }
        }
        return false;
    }

    private boolean needsCompile(boolean isLastTierCompilation) {
        return !isValid() || (isLastTierCompilation && !isValidLastTier());
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
    public void invalidate(Object source, CharSequence reason) {
        cachedNonTrivialNodeCount = -1;
        if (isValid()) {
            invalidateCode();
            runtime().getListener().onCompilationInvalidated(this, source, reason);
        }
        runtime().cancelInstalledTask(this, source, reason);
    }

    OptimizedCallTarget cloneUninitialized() {
        assert sourceCallTarget == null;
        if (compilationProfile == null) {
            initialize();
        }
        RootNode clonedRoot;
        GraalTVMCI tvmci = runtime().getTvmci();
        if (tvmci.isCloneUninitializedSupported(rootNode)) {
            assert uninitializedRootNode == null;
            clonedRoot = tvmci.cloneUninitialized(rootNode);
        } else {
            clonedRoot = NodeUtil.cloneNode(uninitializedRootNode);
        }
        return (OptimizedCallTarget) runtime().createClonedCallTarget(this, clonedRoot);
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

    void setSpeculationLog(SpeculationLog speculationLog) {
        this.speculationLog = speculationLog;
    }

    @Override
    public JavaConstant asJavaConstant() {
        return GraalTruffleRuntime.getRuntime().forObject(this);
    }

    @SuppressWarnings({"unchecked"})
    static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    boolean cancelInstalledTask(Node source, CharSequence reason) {
        return runtime().cancelInstalledTask(this, source, reason);
    }

    @Override
    public void onCompilationFailed(Supplier<String> reasonAndStackTrace, boolean bailout, boolean permanentBailout) {
        if (bailout && !permanentBailout) {
            /*
             * Non-permanent bailouts are expected cases. A non-permanent bailout would be for
             * example class redefinition during code installation. As opposed to permanent
             * bailouts, non-permanent bailouts will trigger recompilation and are not considered a
             * failure state.
             */
        } else {
            compilationProfile.reportCompilationFailure();
            if (TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreThrown)) {
                final InternalError error = new InternalError(reasonAndStackTrace.get());
                throw new OptimizationFailedException(error, this);
            }

            boolean truffleCompilationExceptionsAreFatal = TruffleRuntimeOptions.areTruffleCompilationExceptionsFatal();
            if (TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompilationExceptionsArePrinted) || truffleCompilationExceptionsAreFatal) {
                log(reasonAndStackTrace.get());
                if (truffleCompilationExceptionsAreFatal) {
                    log("Exiting VM due to " + (TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreFatal) ? "TruffleCompilationExceptionsAreFatal"
                                    : "TrufflePerformanceWarningsAreFatal") + "=true");
                    System.exit(-1);
                }
            }
        }
    }

    public static final void log(String message) {
        runtime().log(message);
    }

    final int getKnownCallSiteCount() {
        return callSitesKnown;
    }

    @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "All increments and decrements are synchronized.")
    final synchronized void incrementKnownCallSites() {
        callSitesKnown++;
    }

    @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "All increments and decrements are synchronized.")
    final synchronized void decrementKnownCallSites() {
        callSitesKnown--;
    }

    public final OptimizedCallTarget getSourceCallTarget() {
        return sourceCallTarget;
    }

    @Override
    public String getName() {
        String result = nameCache;
        if (result == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            result = rootNode.toString();
            nameCache = result;
        }
        return result;
    }

    @Override
    public String toString() {
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
    static Object[] castArrayFixedLength(Object[] args, int length) {
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
        getCompilationProfile().reportLoopCount(count);
    }

    @Override
    public boolean nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        CompilerAsserts.neverPartOfCompilation();
        invalidate(newNode, reason);
        /* Notify compiled method that have inlined this call target that the tree changed. */
        invalidateNodeRewritingAssumption();

        OptimizedCompilationProfile profile = this.compilationProfile;
        if (profile != null) {
            profile.reportNodeReplaced();
            if (cancelInstalledTask(newNode, reason)) {
                profile.reportInvalidated();
            }
        }
        return false;
    }

    public void accept(NodeVisitor visitor, TruffleInlining inlingDecision) {
        if (inlingDecision != null) {
            inlingDecision.accept(this, visitor);
        } else {
            getRootNode().accept(visitor);
        }
    }

    public Iterable<Node> nodeIterable(TruffleInlining inliningDecision) {
        Iterator<Node> iterator = nodeIterator(inliningDecision);
        return () -> iterator;
    }

    public Iterator<Node> nodeIterator(TruffleInlining inliningDecision) {
        Iterator<Node> iterator;
        if (inliningDecision != null) {
            iterator = inliningDecision.makeNodeIterator(this);
        } else {
            iterator = NodeUtil.makeRecursiveIterator(this.getRootNode());
        }
        return iterator;
    }

    public final int getNonTrivialNodeCount() {
        if (cachedNonTrivialNodeCount == -1) {
            cachedNonTrivialNodeCount = calculateNonTrivialNodes(getRootNode());
        }
        return cachedNonTrivialNodeCount;
    }

    public static int calculateNonTrivialNodes(Node node) {
        NonTrivialNodeCountVisitor visitor = new NonTrivialNodeCountVisitor();
        node.accept(visitor);
        return visitor.nodeCount;
    }

    public Map<String, Object> getDebugProperties(TruffleInlining inlining) {
        Map<String, Object> properties = new LinkedHashMap<>();
        GraalTruffleRuntimeListener.addASTSizeProperty(this, inlining, properties);
        properties.putAll(getCompilationProfile().getDebugProperties());
        return properties;
    }

    public CompilerOptions getCompilerOptions() {
        final CompilerOptions options = rootNode.getCompilerOptions();
        if (options != null) {
            return options;
        }
        return DefaultCompilerOptions.INSTANCE;
    }

    public void setCallSiteForSplit(OptimizedDirectCallNode callSiteForSplit) {
        if (sourceCallTarget == null) {
            throw new IllegalStateException("Attempting to set a split call site on a target that is not a split!");
        }
        this.callSiteForSplit = callSiteForSplit;
    }

    public OptimizedDirectCallNode getCallSiteForSplit() {
        return callSiteForSplit;
    }

    int getUninitializedNodeCount() {
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

    CancellableCompileTask getCompilationTask() {
        return compilationTask;
    }

    /**
     * This marks the end of the compilation.
     *
     * It may only ever be called by the thread that performed the compilation, and after the
     * compilation is completely done (either successfully or not successfully).
     */
    public void resetCompilationTask() {
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

    public <T> T getOptionValue(OptionKey<T> key) {
        return PolyglotCompilerOptions.getValue(rootNode, key);
    }

    synchronized void addKnownCallNode(OptimizedDirectCallNode directCallNode) {
        // Keeping all the known call sites can be too much to handle in some cases
        // so we are limiting to a 100 call sites for now
        if (knownCallNodes.size() < 100) {
            knownCallNodes.add(new WeakReference<>(directCallNode));
        }
    }

    // Also removes references to reclaimed objects
    synchronized void removeKnownCallSite(OptimizedDirectCallNode callNodeToRemove) {
        knownCallNodes.removeIf(new Predicate<WeakReference<OptimizedDirectCallNode>>() {
            @Override
            public boolean test(WeakReference<OptimizedDirectCallNode> nodeWeakReference) {
                return nodeWeakReference.get() == callNodeToRemove || nodeWeakReference.get() == null;
            }
        });
    }

    boolean isNeedsSplit() {
        return needsSplit;
    }

    void polymorphicSpecialize(Node source) {
        assert !engineData.options.isLegacySplitting();
        List<Node> toDump = null;
        if (engineData.options.isSplittingDumpDecisions()) {
            toDump = new ArrayList<>();
            pullOutParentChain(source, toDump);
        }
        logPolymorphicEvent(0, "Polymorphic event! Source:", source);
        this.maybeSetNeedsSplit(0, toDump);
    }

    private boolean maybeSetNeedsSplit(int depth, List<Node> toDump) {
        final int numberOfKnownCallNodes;
        final OptimizedDirectCallNode onlyCaller;
        synchronized (this) {
            numberOfKnownCallNodes = knownCallNodes.size();
            onlyCaller = numberOfKnownCallNodes == 1 ? knownCallNodes.get(0).get() : null;
        }
        if (depth > engineData.options.getSplittingMaxPropagationDepth() || needsSplit || numberOfKnownCallNodes == 0 || (compilationProfile != null && compilationProfile.getCallCount() == 1)) {
            logEarlyReturn(depth, numberOfKnownCallNodes);
            return needsSplit;
        }
        if (numberOfKnownCallNodes == 1) {
            if (onlyCaller != null) {
                final RootNode callerRootNode = onlyCaller.getRootNode();
                if (callerRootNode != null && callerRootNode.getCallTarget() != null) {
                    final OptimizedCallTarget callerTarget = (OptimizedCallTarget) callerRootNode.getCallTarget();
                    if (engineData.options.isSplittingDumpDecisions()) {
                        pullOutParentChain(onlyCaller, toDump);
                    }
                    logPolymorphicEvent(depth, "One caller! Analysing parent.");
                    if (callerTarget.maybeSetNeedsSplit(depth + 1, toDump)) {
                        logPolymorphicEvent(depth, "Set needs split to true via parent");
                        needsSplit = true;
                    }
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
        if (engineData.options.isSplittingTraceEvents()) {
            logPolymorphicEvent(depth, "Early return: " + needsSplit + " callCount: " + compilationProfile.getCallCount() + ", numberOfKnownCallNodes: " + numberOfKnownCallNodes);
        }
    }

    private void logPolymorphicEvent(int depth, String message) {
        logPolymorphicEvent(depth, message, null);
    }

    private void logPolymorphicEvent(int depth, String message, Object arg) {
        if (engineData.options.isSplittingTraceEvents()) {
            final String indent = new String(new char[depth]).replace("\0", "  ");
            final String argString = (arg == null) ? "" : " " + arg;
            log(String.format(SPLIT_LOG_FORMAT, indent + message + argString, this.toString()));
        }
    }

    private void maybeDump(List<Node> toDump) {
        if (engineData.options.isSplittingDumpDecisions()) {
            final List<OptimizedDirectCallNode> callers = new ArrayList<>();
            synchronized (this) {
                for (WeakReference<OptimizedDirectCallNode> nodeRef : knownCallNodes) {
                    if (nodeRef.get() != null) {
                        callers.add(nodeRef.get());
                    }
                }
            }
            PolymorphicSpecializeDump.dumpPolymorphicSpecialize(toDump, callers);
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
            assert castTarget.compilationProfile != null &&
                            castTarget.compilationProfile.isValidArgumentProfile(args) : "Invalid argument profile. UnsafeCalls need to explicity initialize the profile.";
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
}
