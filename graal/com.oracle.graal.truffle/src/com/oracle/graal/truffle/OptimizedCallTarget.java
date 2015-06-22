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

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import com.oracle.graal.truffle.debug.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.meta.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.utilities.*;

/**
 * Call target that is optimized by Graal upon surpassing a specific invocation threshold.
 */
public class OptimizedCallTarget extends InstalledCode implements RootCallTarget, LoopCountReceiver, ReplaceObserver {

    protected final GraalTruffleRuntime runtime;
    private SpeculationLog speculationLog;
    protected final CompilationProfile compilationProfile;
    protected final CompilationPolicy compilationPolicy;
    private final OptimizedCallTarget sourceCallTarget;
    private final AtomicInteger callSitesKnown = new AtomicInteger(0);
    private final ValueProfile exceptionProfile = ValueProfile.createClassProfile();

    @CompilationFinal private Class<?>[] profiledArgumentTypes;
    @CompilationFinal private Assumption profiledArgumentTypesAssumption;
    @CompilationFinal private Class<?> profiledReturnType;
    @CompilationFinal private Assumption profiledReturnTypeAssumption;

    private final RootNode uninitializedRootNode;
    private final RootNode rootNode;

    /* Experimental fields for new splitting. */
    private final Map<TruffleStamp, OptimizedCallTarget> splitVersions = new HashMap<>();
    private TruffleStamp argumentStamp = DefaultTruffleStamp.getInstance();

    private TruffleInlining inlining;
    private int cachedNonTrivialNodeCount = -1;
    private boolean compiling;

    /**
     * When this call target is inlined, the inlining {@link InstalledCode} registers this
     * assumption. It gets invalidated when a node rewriting is performed. This ensures that all
     * compiled methods that have this call target inlined are properly invalidated.
     */
    private final CyclicAssumption nodeRewritingAssumption;

    public final RootNode getRootNode() {
        return rootNode;
    }

    public OptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode, GraalTruffleRuntime runtime, CompilationPolicy compilationPolicy, SpeculationLog speculationLog) {
        super(rootNode.toString());
        this.sourceCallTarget = sourceCallTarget;
        this.runtime = runtime;
        this.speculationLog = speculationLog;
        this.rootNode = rootNode;
        this.compilationPolicy = compilationPolicy;
        this.rootNode.adoptChildren();
        this.rootNode.applyInstrumentation();
        this.uninitializedRootNode = sourceCallTarget == null ? cloneRootNode(rootNode) : sourceCallTarget.uninitializedRootNode;
        if (TruffleCallTargetProfiling.getValue()) {
            this.compilationProfile = new TraceCompilationProfile();
        } else {
            this.compilationProfile = new CompilationProfile();
        }
        this.nodeRewritingAssumption = new CyclicAssumption("nodeRewritingAssumption of " + rootNode.toString());
    }

    public final void log(String message) {
        runtime.log(message);
    }

    public final boolean isCompiling() {
        return compiling;
    }

    private static RootNode cloneRootNode(RootNode root) {
        if (root == null || !root.isCloningAllowed()) {
            return null;
        }
        return NodeUtil.cloneNode(root);
    }

    public Assumption getNodeRewritingAssumption() {
        return nodeRewritingAssumption.getAssumption();
    }

    public final void mergeArgumentStamp(TruffleStamp p) {
        this.argumentStamp = this.argumentStamp.join(p);
    }

    public final TruffleStamp getArgumentStamp() {
        return argumentStamp;
    }

    private int cloneIndex;

    public int getCloneIndex() {
        return cloneIndex;
    }

    public OptimizedCallTarget cloneUninitialized() {
        RootNode copiedRoot = cloneRootNode(uninitializedRootNode);
        if (copiedRoot == null) {
            return null;
        }
        OptimizedCallTarget splitTarget = (OptimizedCallTarget) runtime.createClonedCallTarget(this, copiedRoot);
        splitTarget.cloneIndex = cloneIndex++;
        return splitTarget;
    }

    public Map<TruffleStamp, OptimizedCallTarget> getSplitVersions() {
        return splitVersions;
    }

    public SpeculationLog getSpeculationLog() {
        return speculationLog;
    }

    @Override
    public Object call(Object... args) {
        compilationProfile.reportIndirectCall();
        if (profiledArgumentTypesAssumption != null && profiledArgumentTypesAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            profiledArgumentTypesAssumption.invalidate();
            profiledArgumentTypes = null;
        }
        return doInvoke(args);
    }

    public final Object callDirect(Object... args) {
        compilationProfile.reportDirectCall();
        profileArguments(args);
        try {
            Object result = doInvoke(args);
            Class<?> klass = profiledReturnType;
            if (klass != null && CompilerDirectives.inCompiledCode() && profiledReturnTypeAssumption.isValid()) {
                result = FrameWithoutBoxing.unsafeCast(result, klass, true, true);
            }
            return result;
        } catch (Throwable t) {
            t = exceptionProfile.profile(t);
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new RuntimeException(t);
            }
        }
    }

    public final Object callInlined(Object... arguments) {
        compilationProfile.reportInlinedCall();
        VirtualFrame frame = createFrame(getRootNode().getFrameDescriptor(), arguments);
        return callProxy(frame);
    }

    @ExplodeLoop
    void profileArguments(Object[] args) {
        if (profiledArgumentTypesAssumption == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initializeProfiledArgumentTypes(args);
        } else if (profiledArgumentTypes != null) {
            if (profiledArgumentTypes.length != args.length) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                profiledArgumentTypesAssumption.invalidate();
                profiledArgumentTypes = null;
            } else if (TruffleArgumentTypeSpeculation.getValue() && profiledArgumentTypesAssumption.isValid()) {
                for (int i = 0; i < profiledArgumentTypes.length; i++) {
                    if (profiledArgumentTypes[i] != null && !profiledArgumentTypes[i].isInstance(args[i])) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        updateProfiledArgumentTypes(args);
                        break;
                    }
                }
            }
        }
    }

    private void initializeProfiledArgumentTypes(Object[] args) {
        CompilerAsserts.neverPartOfCompilation();
        profiledArgumentTypesAssumption = Truffle.getRuntime().createAssumption("Profiled Argument Types");
        profiledArgumentTypes = new Class<?>[args.length];
        if (TruffleArgumentTypeSpeculation.getValue()) {
            for (int i = 0; i < args.length; i++) {
                profiledArgumentTypes[i] = classOf(args[i]);
            }
        }
    }

    private void updateProfiledArgumentTypes(Object[] args) {
        CompilerAsserts.neverPartOfCompilation();
        profiledArgumentTypesAssumption.invalidate();
        for (int j = 0; j < profiledArgumentTypes.length; j++) {
            profiledArgumentTypes[j] = joinTypes(profiledArgumentTypes[j], classOf(args[j]));
        }
        profiledArgumentTypesAssumption = Truffle.getRuntime().createAssumption("Profiled Argument Types");
    }

    private static Class<?> classOf(Object arg) {
        return arg != null ? arg.getClass() : null;
    }

    private static Class<?> joinTypes(Class<?> class1, Class<?> class2) {
        if (class1 == class2) {
            return class1;
        } else if (class1 == null || class2 == null) {
            return null;
        } else if (class1.isAssignableFrom(class2)) {
            return class1;
        } else if (class2.isAssignableFrom(class1)) {
            return class2;
        } else {
            return Object.class;
        }
    }

    protected Object doInvoke(Object[] args) {
        return callBoundary(args);
    }

    @TruffleCallBoundary
    protected final Object callBoundary(Object[] args) {
        if (CompilerDirectives.inInterpreter()) {
            // We are called and we are still in Truffle interpreter mode.
            interpreterCall();
        } else {
            // We come here from compiled code
        }

        return callRoot(args);
    }

    public final Object callRoot(Object[] originalArguments) {
        Object[] args = originalArguments;
        if (this.profiledArgumentTypesAssumption != null && CompilerDirectives.inCompiledCode() && profiledArgumentTypesAssumption.isValid()) {
            args = FrameWithoutBoxing.unsafeCast(castArrayFixedLength(args, profiledArgumentTypes.length), Object[].class, true, true);
            if (TruffleArgumentTypeSpeculation.getValue()) {
                args = castArguments(args);
            }
        }

        VirtualFrame frame = createFrame(getRootNode().getFrameDescriptor(), args);
        Object result = callProxy(frame);

        profileReturnType(result);

        return result;
    }

    void profileReturnType(Object result) {
        if (profiledReturnTypeAssumption == null) {
            if (TruffleReturnTypeSpeculation.getValue()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                profiledReturnType = (result == null ? null : result.getClass());
                profiledReturnTypeAssumption = Truffle.getRuntime().createAssumption("Profiled Return Type");
            }
        } else if (profiledReturnType != null) {
            if (result == null || profiledReturnType != result.getClass()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                profiledReturnType = null;
                profiledReturnTypeAssumption.invalidate();
            }
        }
    }

    @Override
    public void invalidate() {
        invalidate(null, null);
    }

    protected void invalidate(Object source, CharSequence reason) {
        if (isValid()) {
            this.runtime.invalidateInstalledCode(this, source, reason);
        }
        cachedNonTrivialNodeCount = -1;
    }

    public TruffleInlining getInlining() {
        return inlining;
    }

    public void setInlining(TruffleInlining inliningDecision) {
        this.inlining = inliningDecision;
    }

    private boolean cancelInstalledTask(Node source, CharSequence reason) {
        return this.runtime.cancelInstalledTask(this, source, reason);
    }

    private void interpreterCall() {
        if (isValid()) {
            // Stubs were deoptimized => reinstall.
            this.runtime.reinstallStubs();
        } else {
            compilationProfile.reportInterpreterCall();
            if (!isCompiling() && compilationPolicy.shouldCompile(compilationProfile, getCompilerOptions())) {
                compile();
            }
        }
    }

    public void compile() {
        if (!isCompiling()) {
            compiling = true;
            runtime.compile(this, TruffleBackgroundCompilation.getValue() && !TruffleCompilationExceptionsAreThrown.getValue());
        }
    }

    public void notifyCompilationFailed(Throwable t) {
        if (t instanceof BailoutException && !((BailoutException) t).isPermanent()) {
            /*
             * Non permanent bailouts are expected cases. A non permanent bailout would be for
             * example class redefinition during code installation. As opposed to permanent
             * bailouts, non permanent bailouts will trigger recompilation and are not considered a
             * failure state.
             */
        } else {
            compilationPolicy.recordCompilationFailure(t);
            if (TruffleCompilationExceptionsAreThrown.getValue()) {
                throw new OptimizationFailedException(t, this);
            }
            if (TruffleCompilationExceptionsAreFatal.getValue()) {
                printException(t);
                System.exit(-1);
            }
        }
    }

    private void printException(Throwable e) {
        StringWriter string = new StringWriter();
        e.printStackTrace(new PrintWriter(string));
        log(string.toString());
    }

    public void notifyCompilationFinished(boolean successful) {
        if (successful && inlining != null) {
            dequeueInlinedCallSites(inlining);
        }
        compiling = false;
    }

    private void dequeueInlinedCallSites(TruffleInlining parentDecision) {
        for (TruffleInliningDecision decision : parentDecision) {
            if (decision.isInline()) {
                OptimizedCallTarget target = decision.getTarget();
                target.cancelInstalledTask(decision.getProfile().getCallNode(), "Inlining caller compiled.");
                dequeueInlinedCallSites(decision);
            }
        }
    }

    protected final Object callProxy(VirtualFrame frame) {
        try {
            return getRootNode().execute(frame);
        } finally {
            // this assertion is needed to keep the values from being cleared as non-live locals
            assert frame != null && this != null;
        }
    }

    public final int getKnownCallSiteCount() {
        return callSitesKnown.get();
    }

    public final void incrementKnownCallSites() {
        callSitesKnown.incrementAndGet();
    }

    public final void decrementKnownCallSites() {
        callSitesKnown.decrementAndGet();
    }

    public final OptimizedCallTarget getSourceCallTarget() {
        return sourceCallTarget;
    }

    @Override
    public String toString() {
        String superString = rootNode.toString();
        if (isValid()) {
            superString += " <opt>";
        }
        if (sourceCallTarget != null) {
            superString += " <split-" + cloneIndex + "-" + argumentStamp.toStringShort() + ">";
        }
        return superString;
    }

    public CompilationProfile getCompilationProfile() {
        return compilationProfile;
    }

    @ExplodeLoop
    private Object[] castArguments(Object[] originalArguments) {
        Object[] castArguments = new Object[profiledArgumentTypes.length];
        for (int i = 0; i < profiledArgumentTypes.length; i++) {
            castArguments[i] = profiledArgumentTypes[i] != null ? FrameWithoutBoxing.unsafeCast(originalArguments[i], profiledArgumentTypes[i], true, true) : originalArguments[i];
        }
        return castArguments;
    }

    private static Object castArrayFixedLength(Object[] args, @SuppressWarnings("unused") int length) {
        return args;
    }

    public static VirtualFrame createFrame(FrameDescriptor descriptor, Object[] args) {
        if (TruffleCompilerOptions.TruffleUseFrameWithoutBoxing.getValue()) {
            return new FrameWithoutBoxing(descriptor, args);
        } else {
            return new FrameWithBoxing(descriptor, args);
        }
    }

    public List<OptimizedDirectCallNode> getCallNodes() {
        final List<OptimizedDirectCallNode> callNodes = new ArrayList<>();
        getRootNode().accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (node instanceof OptimizedDirectCallNode) {
                    callNodes.add((OptimizedDirectCallNode) node);
                }
                return true;
            }
        });
        return callNodes;
    }

    @Override
    public void reportLoopCount(int count) {
        compilationProfile.reportLoopCount(count);
    }

    @Override
    public boolean nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        CompilerAsserts.neverPartOfCompilation();
        if (isValid()) {
            invalidate(newNode, reason);
        }
        /* Notify compiled method that have inlined this call target that the tree changed. */
        nodeRewritingAssumption.invalidate();

        compilationProfile.reportNodeReplaced();
        if (cancelInstalledTask(newNode, reason)) {
            compilationProfile.reportInvalidated();
        }
        return false;
    }

    public void accept(NodeVisitor visitor, boolean includeInlinedNodes) {
        TruffleInlining inliner = getInlining();
        if (includeInlinedNodes && inliner != null) {
            inlining.accept(this, visitor);
        } else {
            getRootNode().accept(visitor);
        }
    }

    public Stream<Node> nodeStream(boolean includeInlinedNodes) {
        Iterator<Node> iterator;
        TruffleInlining inliner = getInlining();
        if (includeInlinedNodes && inliner != null) {
            iterator = inliner.makeNodeIterator(this);
        } else {
            iterator = NodeUtil.makeRecursiveIterator(this.getRootNode());
        }
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false);
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

    public Map<String, Object> getDebugProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        AbstractDebugCompilationListener.addASTSizeProperty(this, properties);
        properties.putAll(getCompilationProfile().getDebugProperties());
        return properties;
    }

    public static Method getCallDirectMethod() {
        try {
            return OptimizedCallTarget.class.getDeclaredMethod("callDirect", Object[].class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new JVMCIError(e);
        }
    }

    public static Method getCallInlinedMethod() {
        try {
            return OptimizedCallTarget.class.getDeclaredMethod("callInlined", Object[].class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new JVMCIError(e);
        }
    }

    private CompilerOptions getCompilerOptions() {
        final ExecutionContext context = rootNode.getExecutionContext();

        if (context == null) {
            return DefaultCompilerOptions.INSTANCE;
        }

        return context.getCompilerOptions();
    }

    private static final class NonTrivialNodeCountVisitor implements NodeVisitor {

        public int nodeCount;

        public boolean visit(Node node) {
            if (!node.getCost().isTrivial()) {
                nodeCount++;
            }
            return true;
        }

    }

}
