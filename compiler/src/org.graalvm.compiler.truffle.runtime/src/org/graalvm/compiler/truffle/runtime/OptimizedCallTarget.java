/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.Assumption;
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
import com.oracle.truffle.api.impl.DefaultCompilerOptions;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime.LazyFrameBoxingQuery;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TraceTruffleAssumptions;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleBackgroundCompilation;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleCompilationExceptionsAreFatal;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleCompilationExceptionsArePrinted;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleCompilationExceptionsAreThrown;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleExperimentalSplittingDumpDecisions;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TrufflePerformanceWarningsAreFatal;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleExperimentalSplittingMaxPropagationDepth;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleExperimentalSplitting;

/**
 * Call target that is optimized by Graal upon surpassing a specific invocation threshold. That is,
 * this is a Truffle AST that can be optimized via partial evaluation and compiled to machine code.
 *
 * Note: {@code PartialEvaluator} looks up this class and a number of its methods by name.
 */
@SuppressWarnings("deprecation")
public abstract class OptimizedCallTarget implements CompilableTruffleAST, RootCallTarget, ReplaceObserver, com.oracle.truffle.api.LoopCountReceiver {

    private static final String NODE_REWRITING_ASSUMPTION_NAME = "nodeRewritingAssumption";
    static final String CALL_BOUNDARY_METHOD_NAME = "callProxy";

    /** The AST to be executed when this call target is called. */
    private final RootNode rootNode;

    /** Information about when and how the call target should get compiled. */
    @CompilationFinal protected volatile OptimizedCompilationProfile compilationProfile;

    /** Source target if this target was duplicated. */
    private final OptimizedCallTarget sourceCallTarget;

    /** Only set for a source CallTarget with a clonable RootNode. */
    private volatile RootNode uninitializedRootNode;

    private volatile int cachedNonTrivialNodeCount = -1;
    private volatile SpeculationLog speculationLog;
    private volatile int callSitesKnown;
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

    public OptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode) {
        assert sourceCallTarget == null || sourceCallTarget.sourceCallTarget == null : "Cannot create a clone of a cloned CallTarget";
        this.sourceCallTarget = sourceCallTarget;
        this.speculationLog = sourceCallTarget != null ? sourceCallTarget.getSpeculationLog() : null;
        this.rootNode = rootNode;
        uninitializedNodeCount = runtime().getTvmci().adoptChildrenAndCount(this.rootNode);
        knownCallNodes = TruffleCompilerOptions.getValue(TruffleExperimentalSplitting) ? new ArrayList<>(1) : null;
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
                        !TruffleCompilerOptions.getValue(TraceTruffleAssumptions) ? NODE_REWRITING_ASSUMPTION_NAME : NODE_REWRITING_ASSUMPTION_NAME + " of " + rootNode);
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
            initialize();
            return compilationProfile;
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
        OptimizedCompilationProfile profile = compilationProfile;
        if (profile != null) {
            profile.profileIndirectCall();
        }
        return doInvoke(args);
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    public final Object callDirect(Object... args) {
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
    }

    // Note: {@code PartialEvaluator} looks up this method by name and signature.
    public final Object callInlined(Object... arguments) {
        getCompilationProfile().profileInlinedCall();
        return callProxy(createFrame(getRootNode().getFrameDescriptor(), arguments));
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
        final boolean inCompiled = CompilerDirectives.inCompiledCode();
        try {
            return getRootNode().execute(frame);
        } catch (ControlFlowException t) {
            throw rethrow(compilationProfile.profileExceptionType(t));
        } catch (Throwable t) {
            Throwable profiledT = compilationProfile.profileExceptionType(t);
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

    private synchronized void initialize() {
        if (compilationProfile == null) {
            GraalTVMCI tvmci = runtime().getTvmci();
            if (sourceCallTarget == null && rootNode.isCloningAllowed() && !tvmci.isCloneUninitializedSupported(rootNode)) {
                // We are the source CallTarget, so make a copy.
                this.uninitializedRootNode = NodeUtil.cloneNode(rootNode);
            }
            tvmci.onFirstExecution(this);
            this.compilationProfile = createCompilationProfile();
        }
    }

    public final OptionValues getOptionValues() {
        return runtime().getTvmci().getCompilerOptionValues(rootNode);
    }

    private OptimizedCompilationProfile createCompilationProfile() {
        return OptimizedCompilationProfile.create(PolyglotCompilerOptions.getPolyglotValues(rootNode));
    }

    /**
     * Returns <code>true</code> if the call target was already compiled or was compiled
     * synchronously. Returns <code>false</code> if compilation was not scheduled or is happening in
     * the background. Use {@link #isCompiling()} to find out whether it is actually compiling.
     */
    public final boolean compile() {
        if (isValid()) {
            return true;
        }
        if (!isCompiling()) {
            if (!runtime().acceptForCompilation(getRootNode())) {
                return false;
            }

            CancellableCompileTask task = null;
            // Do not try to compile this target concurrently,
            // but do not block other threads if compilation is not asynchronous.
            synchronized (this) {
                if (isValid()) {
                    return true;
                }
                if (this.compilationProfile == null) {
                    initialize();
                }
                if (!isCompiling()) {
                    this.compilationTask = task = runtime().submitForCompilation(this);
                }
            }
            if (task != null) {
                Future<?> submitted = task.getFuture();
                if (submitted != null) {
                    boolean allowBackgroundCompilation = !TruffleCompilerOptions.getValue(TrufflePerformanceWarningsAreFatal) &&
                                    !TruffleCompilerOptions.getValue(TruffleCompilationExceptionsAreThrown);
                    boolean mayBeAsynchronous = TruffleCompilerOptions.getValue(TruffleBackgroundCompilation) && allowBackgroundCompilation;
                    runtime().finishCompilation(this, submitted, mayBeAsynchronous);
                    return !mayBeAsynchronous;
                }
            }
        }
        return false;
    }

    public final boolean isCompiling() {
        CancellableCompileTask task = getCompilationTask();
        if (task != null) {
            if (task.getFuture() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the address of the machine code for this call target. A non-zero return value denotes
     * the contiguous memory block containing the machine code but does not necessarily represent an
     * entry point for the machine code or even the address of executable instructions. This value
     * is only for informational purposes (e.g., use in a log message).
     */
    public abstract long getCodeAddress();

    /**
     * Invalidates any machine code attached to this call target.
     */
    protected abstract void invalidateCode();

    /**
     * Determines if this call target has valid machine code attached to it.
     */
    public abstract boolean isValid();

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

    @Override
    public synchronized SpeculationLog getSpeculationLog() {
        if (speculationLog == null) {
            speculationLog = ((GraalTruffleRuntime) Truffle.getRuntime()).createSpeculationLog();
        }
        return speculationLog;
    }

    synchronized void setSpeculationLog(SpeculationLog speculationLog) {
        this.speculationLog = speculationLog;
    }

    @Override
    public JavaConstant asJavaConstant() {
        SnippetReflectionProvider snippetReflection = runtime().getGraalRuntime().getRequiredCapability(SnippetReflectionProvider.class);
        return snippetReflection.forObject(this);
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
            if (TruffleCompilerOptions.getValue(TruffleCompilationExceptionsAreThrown)) {
                final InternalError error = new InternalError(reasonAndStackTrace.get());
                throw new OptimizationFailedException(error, this);
            }

            boolean truffleCompilationExceptionsAreFatal = TruffleCompilerRuntime.areTruffleCompilationExceptionsFatal();
            if (TruffleCompilerOptions.getValue(TruffleCompilationExceptionsArePrinted) || truffleCompilationExceptionsAreFatal) {
                log(reasonAndStackTrace.get());
                if (truffleCompilationExceptionsAreFatal) {
                    log("Exiting VM due to " + (TruffleCompilerOptions.getValue(TruffleCompilationExceptionsAreFatal) ? TruffleCompilationExceptionsAreFatal.getName()
                                    : TrufflePerformanceWarningsAreFatal.getName()) + "=true");
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
        if (LazyFrameBoxingQuery.useFrameWithoutBoxing) {
            return new FrameWithoutBoxing(descriptor, args);
        } else {
            return new FrameWithBoxing(descriptor, args);
        }
    }

    final void onLoopCount(int count) {
        getCompilationProfile().reportLoopCount(count);
    }

    /*
     * For compatibility of Graal runtime with older Truffle runtime. Remove after 0.12.
     */
    @Override
    public void reportLoopCount(int count) {
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

    public void resetCompilationTask() {
        this.compilationTask = null;
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
        if (TruffleCompilerOptions.getValue(TruffleExperimentalSplitting)) {
            List<Node> toDump = null;
            if (TruffleCompilerOptions.getValue(TruffleExperimentalSplittingDumpDecisions)) {
                toDump = new ArrayList<>();
                pullOutParentChain(source, toDump);
            }
            this.maybeSetNeedsSplit(0, toDump);
        }
    }

    private boolean maybeSetNeedsSplit(int depth, List<Node> toDump) {
        final int numberOfKnownCallNodes;
        final OptimizedDirectCallNode onlyCaller;
        synchronized (this) {
            numberOfKnownCallNodes = knownCallNodes.size();
            onlyCaller = numberOfKnownCallNodes == 1 ? knownCallNodes.get(0).get() : null;
        }
        if (depth > TruffleCompilerOptions.getValue(TruffleExperimentalSplittingMaxPropagationDepth) || needsSplit || numberOfKnownCallNodes == 0 ||
                        compilationProfile.getInterpreterCallCount() == 1) {
            return false;
        }
        if (numberOfKnownCallNodes == 1) {
            if (onlyCaller != null) {
                final RootNode callerRootNode = onlyCaller.getRootNode();
                if (callerRootNode != null && callerRootNode.getCallTarget() != null) {
                    final OptimizedCallTarget callerTarget = (OptimizedCallTarget) callerRootNode.getCallTarget();
                    if (TruffleCompilerOptions.getValue(TruffleExperimentalSplittingDumpDecisions)) {
                        pullOutParentChain(onlyCaller, toDump);
                    }
                    needsSplit = callerTarget.maybeSetNeedsSplit(depth + 1, toDump);
                }
            }
        } else {
            needsSplit = true;
            maybeDump(toDump);
        }
        return needsSplit;
    }

    private void maybeDump(List<Node> toDump) {
        if (TruffleCompilerOptions.getValue(TruffleExperimentalSplittingDumpDecisions)) {
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
}
