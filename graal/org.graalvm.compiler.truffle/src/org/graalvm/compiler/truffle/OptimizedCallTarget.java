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

import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TraceTruffleAssumptions;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleBackgroundCompilation;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCallTargetProfiling;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCompilationExceptionsAreFatal;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCompilationExceptionsArePrinted;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCompilationExceptionsAreThrown;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.UnaryOperator;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.truffle.GraalTruffleRuntime.LazyFrameBoxingQuery;
import org.graalvm.compiler.truffle.debug.AbstractDebugCompilationListener;
import org.graalvm.compiler.truffle.substitutions.TruffleGraphBuilderPlugins;

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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Call target that is optimized by Graal upon surpassing a specific invocation threshold.
 */
@SuppressWarnings("deprecation")
public class OptimizedCallTarget extends InstalledCode implements RootCallTarget, ReplaceObserver, com.oracle.truffle.api.LoopCountReceiver {

    private static final String NODE_REWRITING_ASSUMPTION_NAME = "nodeRewritingAssumption";

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
     * assumption. It gets invalidated when a node rewriting is performed. This ensures that all
     * compiled methods that have this call target inlined are properly invalidated.
     */
    private volatile Assumption nodeRewritingAssumption;
    private static final AtomicReferenceFieldUpdater<OptimizedCallTarget, Assumption> NODE_REWRITING_ASSUMPTION_UPDATER = AtomicReferenceFieldUpdater.newUpdater(OptimizedCallTarget.class,
                    Assumption.class, "nodeRewritingAssumption");

    public OptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode) {
        super(rootNode.toString());
        assert sourceCallTarget == null || sourceCallTarget.sourceCallTarget == null : "Cannot create a clone of a cloned CallTarget";
        this.sourceCallTarget = sourceCallTarget;
        this.speculationLog = sourceCallTarget != null ? sourceCallTarget.getSpeculationLog() : null;
        this.rootNode = rootNode;
        this.rootNode.adoptChildren();
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

    @Override
    public final Object call(Object... args) {
        getCompilationProfile().profileIndirectCall();
        return doInvoke(args);
    }

    public final Object callDirect(Object... args) {
        try {
            getCompilationProfile().profileDirectCall(args);
            Object result = doInvoke(args);
            if (CompilerDirectives.inCompiledCode()) {
                result = compilationProfile.injectReturnValueProfile(result);
            }
            return result;
        } catch (Throwable t) {
            throw rethrow(compilationProfile.profileExceptionType(t));
        }
    }

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
            compilationProfile.interpreterCall(this);
            if (isValid()) {
                // Stubs were deoptimized => reinstall.
                runtime().reinstallStubs();
            }
        } else {
            // We come here from compiled code
        }

        return callRoot(args);
    }

    /* TODO needs to remain public? */
    public final Object callRoot(Object[] originalArguments) {
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
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert frame != null && this != null;
            if (CompilerDirectives.inInterpreter() && inCompiled) {
                notifyDeoptimized(frame);
            }
        }
    }

    private void notifyDeoptimized(VirtualFrame frame) {
        runtime().getCompilationNotify().notifyCompilationDeoptimized(this, frame);
    }

    private static GraalTruffleRuntime runtime() {
        return (GraalTruffleRuntime) Truffle.getRuntime();
    }

    private synchronized void initialize() {
        if (compilationProfile == null) {
            GraalTVMCI tvmci = runtime().getTvmci();
            if (sourceCallTarget == null && rootNode.isCloningAllowed() && !tvmci.isCloneUninitializedSupported(rootNode)) {
                // We are the source CallTarget, so make a copy.
                this.uninitializedRootNode = cloneRootNode(rootNode);
            }
            tvmci.onFirstExecution(this);
            this.compilationProfile = createCompilationProfile();
        }
    }

    private static OptimizedCompilationProfile createCompilationProfile() {
        if (TruffleCompilerOptions.getValue(TruffleCallTargetProfiling)) {
            return TraceCompilationProfile.create();
        } else {
            return OptimizedCompilationProfile.create();
        }
    }

    public final void compile() {
        if (!isCompiling()) {
            if (compilationProfile == null) {
                initialize();
            }

            if (!runtime().acceptForCompilation(getRootNode())) {
                return;
            }

            CancellableCompileTask task = null;
            // Do not try to compile this target concurrently,
            // but do not block other threads if compilation is not asynchronous.
            synchronized (this) {
                if (!isCompiling()) {
                    compilationTask = task = runtime().submitForCompilation(this);
                }
            }
            if (task != null) {
                Future<?> submitted = task.getFuture();
                if (submitted != null) {
                    boolean mayBeAsynchronous = TruffleCompilerOptions.getValue(TruffleBackgroundCompilation) && !TruffleCompilerOptions.getValue(TruffleCompilationExceptionsAreThrown);
                    runtime().finishCompilation(this, submitted, mayBeAsynchronous);
                }
            }
        }
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

    @Override
    public void invalidate() {
        invalidate(null, null);
    }

    protected void invalidate(Object source, CharSequence reason) {
        cachedNonTrivialNodeCount = -1;
        if (isValid()) {
            runtime().invalidateInstalledCode(this, source, reason);
        }
        runtime().cancelInstalledTask(this, source, reason);
    }

    private static RootNode cloneRootNode(RootNode root) {
        assert root.isCloningAllowed();
        GraalTVMCI tvmci = runtime().getTvmci();
        if (tvmci.isCloneUninitializedSupported(root)) {
            assert root == null;
            return tvmci.cloneUnitialized(root);
        } else {
            return NodeUtil.cloneNode(root);
        }
    }

    OptimizedCallTarget cloneUninitialized() {
        assert sourceCallTarget == null;
        if (compilationProfile == null) {
            initialize();
        }
        RootNode copiedRoot = cloneRootNode(uninitializedRootNode);
        return (OptimizedCallTarget) runtime().createClonedCallTarget(this, copiedRoot);
    }

    protected synchronized SpeculationLog getSpeculationLog() {
        if (speculationLog == null) {
            speculationLog = ((GraalTruffleRuntime) Truffle.getRuntime()).createSpeculationLog();
        }
        return speculationLog;
    }

    synchronized void setSpeculationLog(SpeculationLog speculationLog) {
        this.speculationLog = speculationLog;
    }

    @SuppressWarnings({"unchecked"})
    private static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    boolean cancelInstalledTask(Node source, CharSequence reason) {
        return runtime().cancelInstalledTask(this, source, reason);
    }

    void notifyCompilationFailed(Throwable t) {
        if (t instanceof BailoutException && !((BailoutException) t).isPermanent()) {
            /*
             * Non permanent bailouts are expected cases. A non permanent bailout would be for
             * example class redefinition during code installation. As opposed to permanent
             * bailouts, non permanent bailouts will trigger recompilation and are not considered a
             * failure state.
             */
        } else {
            compilationProfile.reportCompilationFailure();
            if (TruffleCompilerOptions.getValue(TruffleCompilationExceptionsAreThrown)) {
                throw new OptimizationFailedException(t, this);
            }
            /*
             * Automatically enable TruffleCompilationExceptionsAreFatal when asserts are enabled
             * but respect TruffleCompilationExceptionsAreFatal if it's been explicitly set.
             */
            boolean truffleCompilationExceptionsAreFatal = TruffleCompilerOptions.getValue(TruffleCompilationExceptionsAreFatal);
            assert TruffleCompilationExceptionsAreFatal.hasBeenSet(TruffleCompilerOptions.getOptions()) || (truffleCompilationExceptionsAreFatal = true) == true;

            if (TruffleCompilerOptions.getValue(TruffleCompilationExceptionsArePrinted) || truffleCompilationExceptionsAreFatal) {
                printException(t);
                if (truffleCompilationExceptionsAreFatal) {
                    System.exit(-1);
                }
            }
        }
    }

    private static void printException(Throwable e) {
        StringWriter string = new StringWriter();
        e.printStackTrace(new PrintWriter(string));
        log(string.toString());
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
     * Intrinsified in {@link TruffleGraphBuilderPlugins}.
     *
     * @param length avoid warning
     */
    static Object castArrayFixedLength(Object[] args, int length) {
        return args;
    }

    /**
     * Intrinsified in {@link TruffleGraphBuilderPlugins}.
     *
     * @param type avoid warning
     * @param condition avoid warning
     * @param nonNull avoid warning
     */
    @SuppressWarnings({"unchecked"})
    static <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull) {
        return (T) value;
    }

    /** Intrinsified in {@link TruffleGraphBuilderPlugins}. */
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
        if (isValid()) {
            invalidate(newNode, reason);
        }
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
        AbstractDebugCompilationListener.addASTSizeProperty(this, inlining, properties);
        properties.putAll(getCompilationProfile().getDebugProperties());
        return properties;
    }

    static Method getCallDirectMethod() {
        try {
            return OptimizedCallTarget.class.getDeclaredMethod("callDirect", Object[].class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new GraalError(e);
        }
    }

    static Method getCallInlinedMethod() {
        try {
            return OptimizedCallTarget.class.getDeclaredMethod("callInlined", Object[].class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new GraalError(e);
        }
    }

    CompilerOptions getCompilerOptions() {
        final CompilerOptions options = rootNode.getCompilerOptions();
        if (options != null) {
            return options;
        }
        return DefaultCompilerOptions.INSTANCE;
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

    void resetCompilationTask() {
        this.compilationTask = null;
    }
}
