/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.nodes;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.TruffleLanguage.ParsingRequest;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents the root node in a Truffle AST. The root node is a {@link Node node} that allows to be
 * {@link #execute(VirtualFrame) executed} using a {@link VirtualFrame frame} instance created by
 * the framework. Please note that the {@link RootNode} should not be executed directly but using
 * {@link CallTarget#call(Object...)}. The structure of the frame is provided by the
 * {@link FrameDescriptor frame descriptor} passed in the constructor. A root node has always a
 * <code>null</code> {@link #getParent() parent} and cannot be {@link #replace(Node) replaced}.
 *
 * <h4>Construction</h4>
 *
 * The root node can be constructed with a {@link TruffleLanguage language implementation} if it is
 * available. The language implementation instance is obtainable while
 * {@link TruffleLanguage#createContext(Env)} or {@link TruffleLanguage#parse(ParsingRequest)} is
 * executed. If no language environment is available, then <code>null</code> can be passed. Please
 * note that root nodes with <code>null</code> language are considered not instrumentable and don't
 * have access to its public {@link #getLanguageInfo() language information}.
 *
 * <h4>Execution</h4>
 *
 * In order to execute a root node, its call target is lazily created and can be accessed via
 * {@link RootNode#getCallTarget()}. This allows the runtime system to optimize the execution of the
 * AST. The {@link CallTarget} can either be {@link CallTarget#call(Object...) called} directly from
 * runtime code or {@link DirectCallNode direct} and {@link IndirectCallNode indirect} call nodes
 * can be created, inserted in a child field and {@link DirectCallNode#call(Object[]) called}. The
 * use of direct call nodes allows the framework to automatically inline and further optimize call
 * sites based on heuristics.
 * <p>
 * After several calls to a call target or call node, the root node might get compiled using partial
 * evaluation. The details of the compilation heuristic are unspecified, therefore the Truffle
 * runtime system might decide to not compile at all.
 *
 * <h4>Cardinalities</h4>
 *
 * <i>One</i> root node instance refers to other classes using the following cardinalities:
 * <ul>
 * <li><i>one</i> {@link TruffleLanguage language}
 * <li><i>one</i> {@link CallTarget call target}
 * <li><i>many</i> {@link TruffleLanguage#createContext(Env) created} language contexts
 * </ul>
 *
 * <h4>Instrumentation</h4>
 *
 * A root node can be {@linkplain com.oracle.truffle.api.instrumentation instrumented} if the
 * following conditions apply:
 * <ul>
 * <li>A non-null {@link TruffleLanguage language} is passed in the root node constructor.
 * <li>{@link #isInstrumentable()} is overridden and returns <code>true</code>.
 * <li>{@link #getSourceSection()} is overridden and returns a non-null value.
 * <li>The AST contains at least one node that implements
 * {@link com.oracle.truffle.api.instrumentation.InstrumentableNode}.
 * <li>It is recommended that children of instrumentable root nodes are tagged with
 * <code>StandardTags</code>.
 * </ul>
 * <p>
 * <strong>Note:</strong> It is recommended to override {@link #getSourceSection()} and provide a
 * source section if available. This allows for better testing/tracing/tooling. If no concrete
 * source section is available please consider using {@link Source#createUnavailableSection()}.
 *
 * @since 0.8 or earlier
 */
// @DefaultSymbol("$rootNode")
public abstract class RootNode extends ExecutableNode {

    private static final AtomicReferenceFieldUpdater<RootNode, ReentrantLock> LOCK_UPDATER = AtomicReferenceFieldUpdater.newUpdater(RootNode.class, ReentrantLock.class, "lock");

    @CompilationFinal private volatile RootCallTarget callTarget;
    @CompilationFinal private FrameDescriptor frameDescriptor;
    private volatile ReentrantLock lock;

    volatile byte instrumentationBits;

    /**
     * Creates new root node with a given language instance. The language instance is obtainable
     * while {@link TruffleLanguage#createContext(Env)} or
     * {@link TruffleLanguage#parse(ParsingRequest)} is executed. If no language environment is
     * available, then <code>null</code> can be passed. Please note that root nodes with
     * <code>null</code> language are considered not instrumentable and don't have access to its
     * public {@link #getLanguageInfo() language information}.
     *
     * @param language the language this root node is associated with
     * @since 0.25
     */
    protected RootNode(TruffleLanguage<?> language) {
        this(language, null);
    }

    /**
     * Creates new root node given an language environment and frame descriptor. The language
     * instance is obtainable while {@link TruffleLanguage#createContext(Env)} or
     * {@link TruffleLanguage#parse(ParsingRequest)} is executed. If no language environment is
     * available, then <code>null</code> can be passed. Please note that root nodes with
     * <code>null</code> language are considered not instrumentable and don't have access to its
     * public {@link #getLanguageInfo() language information}.
     *
     * @param language the language this root node is associated with
     * @since 0.25
     */
    protected RootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language);
        CompilerAsserts.neverPartOfCompilation();
        this.frameDescriptor = frameDescriptor == null ? new FrameDescriptor() : frameDescriptor;
    }

    /** @since 0.8 or earlier */
    @Override
    public Node copy() {
        RootNode root = (RootNode) super.copy();
        root.frameDescriptor = frameDescriptor;
        resetFieldsAfterCopy(root);
        return root;
    }

    private static void resetFieldsAfterCopy(RootNode root) {
        root.callTarget = null;
        root.instrumentationBits = 0;
        root.lock = null;
    }

    /**
     * Returns a qualified name of the AST that in the best case uniquely identifiers the method. If
     * the qualified name is not specified by the root, then the {@link #getName() name} is used by
     * default. A root node that represents a Java method could consist of the package name, the
     * class name and the method name. E.g. <code>mypackage.MyClass.myMethod</code>
     *
     * @since 20.0
     */
    public String getQualifiedName() {
        return getName();
    }

    /**
     * Returns a simple name of the AST (expected to be a method or procedure name in most
     * languages) that identifies the AST for the benefit of guest language programmers using tools;
     * it might appear, for example in the context of a stack dump or trace and is not expected to
     * be called often. Can be called on any thread and without a language context. The name of a
     * root node that represents a Java method could consist of the method name. E.g.
     * <code>myMethod</code>
     * <p>
     * In some languages AST "compilation units" may have no intrinsic names. When no information is
     * available, language implementations might simply use the first few characters of the code,
     * followed by "{@code ...}". Language implementations should assign a more helpful name
     * whenever it becomes possible, for example when a functional value is assigned. This means
     * that the name might not be stable over time.
     * <p>
     * Language execution semantics should not depend on either this name or the way that it is
     * formatted. The name should be presented in the way expected to be most useful for
     * programmers.
     *
     * @return a name that helps guest language programmers identify code corresponding to the AST,
     *         possibly {@code null} if the language implementation is unable to provide any useful
     *         information.
     * @since 0.15
     */
    public String getName() {
        return null;
    }

    /**
     * Returns <code>true</code> if this root node should be considered internal and not be shown to
     * a guest language programmer. This method has effect on tools and guest language stack traces.
     * By default a {@link RootNode} is internal if no language was passed in the constructor or if
     * the {@link #getSourceSection() root source section} is set and points to an internal source.
     * This method is intended to be overwritten by guest languages, when the node's source is
     * internal, the implementation should respect that. Can be called on any thread and without a
     * language context.
     * <p>
     * This method may be invoked on compiled code paths. It is recommended to implement this method
     * such that it returns a compilation final constant.
     *
     * @since 0.27
     */
    public boolean isInternal() {
        if (getLanguageInfo() == null) {
            return true;
        }
        SourceSection sc = materializeSourceSection();
        if (sc != null) {
            return sc.getSource().isInternal();
        }
        return false;
    }

    /**
     * Returns <code>true</code> if this root node should count towards
     * {@link com.oracle.truffle.api.exception.AbstractTruffleException#getStackTraceElementLimit}.
     * <p>
     * By default, returns the negation of {@link #isInternal()}.
     * <p>
     * This method may be invoked on compiled code paths. It is recommended to implement this method
     * or #isInternal() such that it returns a partial evaluation constant.
     *
     * @since 21.2.0
     */
    protected boolean countsTowardsStackTraceLimit() {
        return !isInternal();
    }

    /**
     * Returns the current byte code index of the root node using a given node location and a frame.
     * Depending on the strategy (see below) either the node or the frame may be used to find the
     * bytecode index.
     * <p>
     * This method is called by Truffle to determine the bytecode index when constructing
     * {@link TruffleStackTraceElement} objects. There are two common strategies to implement this
     * method:
     * <ul>
     * <li>If the bytecode index is stored in the frame, then
     * {@link #isCaptureFramesForTrace(boolean)} should be overridden and return <code>true</code>.
     * Next use the frame argument and read the bytecode index from the frame. Note that the
     * provided frame may be <code>null</code> even if {@link #isCaptureFramesForTrace(boolean)}
     * returns <code>true</code>.
     * <li>If the bytecode index is stored in the call node, then {@link Node#getParent() parent}
     * nodes should be walked to find the node containing the bytecode index.
     * </ul>
     * <p>
     * This method should return a negative bytecode index if it is unavailable or invalid. A
     * language implementation may assign additional semantics for individual negative byte code
     * indices, other languages will interpret any negative index as if the index is unavailable.
     *
     * @param node the top-most node of the activation or <code>null</code>
     * @param frame the current frame of the activation or <code>null</code>
     * @see FrameInstance#getBytecodeIndex() to access byte code indices
     * @since 24.1
     */
    protected int findBytecodeIndex(Node node, Frame frame) {
        return -1;
    }

    @TruffleBoundary
    private SourceSection materializeSourceSection() {
        return getSourceSection();
    }

    /**
     * @since 24.1
     * @deprecated in 24.1, implement and use {@link #isCaptureFramesForTrace(boolean)} instead
     */
    @Deprecated
    protected boolean isCaptureFramesForTrace(@SuppressWarnings("unused") Node compiledFrame) {
        return isCaptureFramesForTrace();
    }

    /**
     * Returns <code>true</code> if an AbstractTruffleException leaving this node should capture
     * {@link Frame} objects in its stack trace in addition to the default information. This is
     * <code>false</code> by default to avoid the attached overhead. The captured frames are then
     * accessible through {@link TruffleStackTraceElement#getFrame()}.
     * <p>
     * Using the compiledFrame argument can be useful to capture the frame only for interpreted
     * frames. This way it is possible to store the {@link #findBytecodeIndex(Node, Frame) bytecode
     * index} in the frame only in the interpreter, but never in compiled code. This is more
     * efficient, because capturing the frame is a fast operation in the interpreter, but a slow
     * operation for compiled frames.
     *
     * @param compiledFrame whether the frame would be from a compiled execution.
     * @since 24.1
     */
    protected boolean isCaptureFramesForTrace(boolean compiledFrame) {
        return isCaptureFramesForTrace(null);
    }

    /**
     * @since 0.31
     * @deprecated in 24.1, implement and use {@link #isCaptureFramesForTrace(boolean)} instead
     */
    @Deprecated
    public boolean isCaptureFramesForTrace() {
        return false;
    }

    /**
     * Returns <code>true</code> if this {@link RootNode} is allowed to be cloned. The runtime
     * system might decide to create deep copies of the {@link RootNode} in order to gather context
     * sensitive profiling feedback. The default implementation returns <code>false</code>. Guest
     * language specific implementations may want to return <code>true</code> here to indicate that
     * gathering call site specific profiling information might make sense for this {@link RootNode}
     * .
     *
     * @return <code>true</code> if cloning is allowed else <code>false</code>.
     * @since 0.8 or earlier
     */
    public boolean isCloningAllowed() {
        return false;
    }

    /**
     * Returns <code>true</code> if {@link #cloneUninitialized()} can be used to create
     * uninitialized copies of an already initialized / executed root node. By default, or if this
     * method returns <code>false</code>, an optimizing Truffle runtime might need to copy the AST
     * before it is executed for the first time to ensure it is able to create new uninitialized
     * copies when needed. By returning <code>true</code> and therefore supporting uninitialized
     * copies an optimizing runtime does not need to keep a reference to an uninitialized copy on
     * its own and might therefore be able to save memory. The returned boolean needs to be
     * immutable for a {@link RootNode} instance.
     *
     * @return <code>true</code> if calls to {@link #cloneUninitialized() uninitialized copies} are
     *         supported.
     * @see #cloneUninitialized()
     * @since 0.24
     */
    protected boolean isCloneUninitializedSupported() {
        return false;
    }

    /**
     * Creates an uninitialized copy of an already initialized/executed root node if it is
     * {@link #isCloneUninitializedSupported() supported}. Throws an
     * {@link UnsupportedOperationException} exception by default. By default, or if
     * {@link #isCloneUninitializedSupported()} returns <code>false</code>, an optimizing Truffle
     * runtime might need to copy the root node before it is executed for the first time to ensure
     * it is able to create new uninitialized copies when needed. By supporting uninitialized copies
     * an optimizing runtime does not need to keep a reference to an uninitialized copy on its own
     * and might therefore be able to save memory.
     *
     * <p>
     * Two common strategies to implement {@link #cloneUninitialized()} are:
     * <ul>
     * <li><b>Reparsing:</b> Support it by keeping a reference to the original source code including
     * the lexical scope and create the uninitialized copy of the root node by reparsing the source.
     * <li><b>Resetting:</b> Support it by traversing the {@link Node} tree and derive an
     * uninitialized copy from each initialized node.
     * </ul>
     *
     * @return an uninitialized copy of this root node if supported.
     * @throws UnsupportedOperationException if not supported
     * @see #isCloneUninitializedSupported()
     * @since 0.24
     */
    protected RootNode cloneUninitialized() {
        throw new UnsupportedOperationException();
    }

    final RootNode cloneUninitializedImpl(CallTarget sourceCallTarget, RootNode uninitializedRootNode) {
        RootNode clonedRoot;
        if (isCloneUninitializedSupported()) {
            assert uninitializedRootNode == null : "uninitializedRootNode should not have been created";
            clonedRoot = cloneUninitialized();

            // if the language copied we cannot be sure
            // that the call target is not reset (with their own means of copying)
            // so better make sure they are reset.
            resetFieldsAfterCopy(clonedRoot);
        } else {
            clonedRoot = NodeUtil.cloneNode(uninitializedRootNode);
            // regular cloning guarantees that call target, instrumentation bits,
            // and lock are null. See #copy().
            assert clonedRoot.callTarget == null;
            assert clonedRoot.instrumentationBits == 0;
            assert clonedRoot.lock == null;
        }

        RootCallTarget clonedTarget = NodeAccessor.RUNTIME.newCallTarget(sourceCallTarget, clonedRoot);

        ReentrantLock l = clonedRoot.getLazyLock();
        l.lock();
        try {
            clonedRoot.setupCallTarget(clonedTarget, "callTarget not null. Was getCallTarget on the result of RootNode.cloneUninitialized called?");
        } finally {
            l.unlock();
        }

        return clonedRoot;
    }

    /**
     * Executes this function using the specified frame and returns the result value.
     *
     * @param frame the frame of the currently executing guest language method
     * @return the value of the execution
     * @since 0.8 or earlier
     */
    @Override
    public abstract Object execute(VirtualFrame frame);

    /** @since 0.8 or earlier */
    public final RootCallTarget getCallTarget() {
        RootCallTarget target = this.callTarget;
        // Check isLoaded to avoid returning a CallTarget before notifyOnLoad() is done
        if (target == null || !NodeAccessor.RUNTIME.isLoaded(target)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            target = initializeTarget();
        }
        return target;
    }

    private RootCallTarget initializeTarget() {
        RootCallTarget target;
        ReentrantLock l = getLazyLock();
        l.lock();
        try {
            target = this.callTarget;
            if (target == null) {
                target = NodeAccessor.RUNTIME.newCallTarget(null, this);
                this.setupCallTarget(target, "callTarget was set by newCallTarget but should not");
            }
        } finally {
            l.unlock();
        }
        return target;
    }

    private void setupCallTarget(RootCallTarget callTarget, String message) {
        assert getLazyLock().isHeldByCurrentThread();

        if (this.callTarget != null) {
            throw CompilerDirectives.shouldNotReachHere(message);
        }
        prepareForCall();
        this.callTarget = callTarget;

        // Call notifyOnLoad() after the callTarget field is set, so the invariant that if a
        // CallTarget exists for a RootNode then that rootNode.callTarget points to the CallTarget
        // always holds, and no matter what notifyOnLoad() does the 1-1 relation between the
        // RootNode and CallTarget is already there.
        NodeAccessor.RUNTIME.notifyOnLoad(callTarget);
    }

    final RootCallTarget getCallTargetWithoutInitialization() {
        return callTarget;
    }

    /** @since 0.8 or earlier */
    public final FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    /**
     * Does this contain AST content that it is possible to instrument. Can be called on any thread
     * and without a language context.
     *
     * @since 0.8 or earlier
     */
    protected boolean isInstrumentable() {
        return true;
    }

    /**
     * Prepares this {@link RootNode} to be called with a {@link CallTarget}. This method will be
     * called exactly once when a {@link #getCallTarget() call target is requested} for the first
     * time. This method can be used to lazily initialize parts of a root node, or to validate that
     * a root node can be used as a call target.
     * 
     * @since 25.0
     */
    protected void prepareForCall() {
        // no default implementation
    }

    /**
     * Prepares this {@link RootNode} for compilation. This method is guaranteed to be called at
     * least once before any code paths are executed as compiled code (see
     * {@link CompilerDirectives#inCompiledCode()}) for a given compilation tier. It may be called
     * multiple times per compilation and can be invoked concurrently by multiple compiler threads
     * without synchronization; therefore, it must be thread-safe.
     *
     * By default, this method returns <code>true</code> to indicate sufficient profiling for
     * compilation, but implementations can return <code>false</code> if not. Returning
     * <code>false</code> for root compilations will defer the compilation to allow for additional
     * profiling, whereas otherwise returning <code>false</code> during inlining will disable
     * inlining for this call site. Any exception thrown will fail the compilation for this
     * {@link RootNode} permanently.
     *
     * Compilations are initiated by the runtime for optimization through partial evaluation and
     * compilation. The timing and threading of compilations are runtime-specific and should not be
     * relied upon. This method is not called for Truffle runtimes that do not support compilation,
     * such as the fallback runtime.
     *
     * Compilations may be initiated for the following reasons:
     * <ul>
     * <li>The {@link CallTarget} for this {@link RootNode} has been invoked frequently enough to
     * trigger compilation.
     * <li>A {@link LoopNode} or {@link BytecodeOSRNode} has executed frequently enough to trigger
     * OSR compilation.
     * <li>The compiler is exploring inlining during compilation, preparing to inline a
     * {@link DirectCallNode} or an indirect call with a partially evaluated constant
     * {@link CallTarget}.
     * </ul>
     *
     * Work performed in this method counts towards compilation time, not interpretation time.
     * Implementing this method can help offload expensive computations to compiler threads. Cache
     * any preparation work to avoid repeating it for every compilation and inlining. To prevent
     * deadlocks when using synchronization, adhere to the following guidelines:
     * <ul>
     * <li>Do not execute guest code or invoke {@link CallTarget#call(Object...)} within this
     * method.
     * <li>Avoid acquiring locks shared with execution threads, especially across call boundaries or
     * loop back-edges when OSR compilation is enabled.
     * <li>Use {@link AtomicReferenceFieldUpdater} CAS-locking for caching non-expensive preparation
     * work to minimize contention.
     * <li>Ensure all synchronization primitives are released upon method exit; use finally blocks
     * for safety.
     * </ul>
     *
     * Note that during the execution of this method, no language context is entered. Therefore, you
     * cannot use {@link ContextThreadLocal}, {@link ContextLocal}, {@link LanguageReference}, or
     * {@link ContextReference}.
     *
     * @param rootCompilation <code>true</code> if this is a root compilation; <code>false</code> if
     *            inlining this root.
     * @param compilationTier the current compilation tier, as per
     *            {@link FrameInstance#getCompilationTier()}.
     * @param lastTier <code>true</code> if this is the last compilation tier for which this
     *            {@link RootNode} is prepared; <code>false</code> otherwise. Useful for performing
     *            preparation only at the highest tier.
     * @return <code>true</code> if the method is sufficiently profiled; <code>false</code>
     *         otherwise.
     *
     * @since 24.2
     */
    protected boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
        return true;
    }

    /**
     * Is this root node to be considered trivial by the runtime.
     *
     * A trivial root node is defined as a root node that:
     * <ol>
     * <li>Never increases code size when inlined, i.e. is always less complex then the call.</li>
     * <li>Never performs guest language calls.</li>
     * <li>Never contains loops.</li>
     * <li>Is small (for a language-specific definition of small).</li>
     * </ol>
     *
     * An good example of trivial root nodes would be getters and setters in java.
     *
     * @since 20.3.0
     * @return <code>true </code>if this root node should be considered trivial by the runtime.
     *         <code>false</code> otherwise.
     */
    protected boolean isTrivial() {
        return false;
    }

    /**
     * Prepares a root node for use with the Truffle instrumentation framework. This is similar to
     * materialization of syntax nodes in an InstrumentableNode, but this method should be preferred
     * if the root node is updated as a whole and the individual materialization of nodes is not
     * needed. Another advantage of this method is that it is always invoked before
     * {@link #getSourceSection()} by the instrumentation framework. This allows to perform the
     * materialization of sources and tags in one operation.
     * <p>
     * This method is invoked repeatedly and should not perform any operation if a set of tags was
     * already prepared before. In other words, this method should stabilize and eventually not
     * perform any operation if the same tags were observed before.
     *
     * @since 24.2
     */
    protected void prepareForInstrumentation(@SuppressWarnings("unused") Set<Class<?>> tags) {
        // no default implementation
    }

    /**
     * Returns an instrumentable call node from a node and frame. By default, returns the given
     * <code>callNode</code>. If the returned node is not instrumentable, the
     * {@link Node#getParent() parent} chain of the node may be traversed until an instrumentable
     * node is found (thus, the returned node should be adopted).
     * <p>
     * This method should be implemented if the instrumentable call node is not reachable through
     * the {@link Node#getParent() parent} chain of a {@link FrameInstance#getCallNode() call node}.
     * For example, in bytecode interpreters instrumentable nodes may be stored in a side data
     * structure and the instrumentable node must be looked up using the bytecode index. This method
     * is intended to be overridden to implement specify such behavior.
     * <p>
     * A {@link Frame frame} parameter is only provided if {@link #isCaptureFramesForTrace(boolean)}
     * returns <code>true</code>. If the frame is not captured then the frame parameter is
     * <code>null</code>.
     * <p>
     * The <code>bytecodeIndex</code> parameter takes on the value produced by calling
     * {@link #findBytecodeIndex(Node, Frame)} (<code>-1</code> unless overridden).
     *
     * @param callNode the top-most node of the activation or <code>null</code>
     * @param frame the current frame of the activation or <code>null</code>
     * @param bytecodeIndex the current bytecode index of the activation or a negative number
     * @since 24.2
     */
    protected Node findInstrumentableCallNode(Node callNode, Frame frame, int bytecodeIndex) {
        return callNode;
    }

    /**
     * Provide a list of stack frames that led to a schedule of asynchronous execution of this root
     * node on the provided frame. The asynchronous frames are expected to be found here when
     * {@link TruffleLanguage#getAsynchronousStackDepth()} is positive. The language is free to
     * provide asynchronous frames or longer list of frames when it's of no performance penalty, or
     * if requested by other options. This method is invoked on slow-paths only and with a context
     * entered.
     *
     * @param frame A frame, never <code>null</code>
     * @return a list of {@link TruffleStackTraceElement}, or <code>null</code> when no asynchronous
     *         stack is available.
     * @see TruffleLanguage#getAsynchronousStackDepth()
     * @since 20.1.0
     */
    protected List<TruffleStackTraceElement> findAsynchronousFrames(Frame frame) {
        return null;
    }

    /**
     * Translates the {@link TruffleStackTraceElement} into an interop object supporting the
     * {@code hasExecutableName} and potentially {@code hasDeclaringMetaObject} and
     * {@code hasSourceLocation} messages. An executable name must be provided, whereas the
     * declaring meta object and source location is optional. Guest languages may typically return
     * their function objects that typically already implement the required contracts.
     * <p>
     * The intention of this method is to provide a guest language object for other languages that
     * they can inspect using interop. An implementation of this method is expected to not fail with
     * a guest error. Implementations are allowed to do {@link ContextReference#get(Node) context
     * reference lookups} in the implementation of the method. This may be useful to access the
     * function objects needed to resolve the stack trace element.
     *
     * @see TruffleStackTraceElement#getGuestObject() to access the guest object of a stack trace
     *      element.
     * @since 20.3
     */
    protected Object translateStackTraceElement(TruffleStackTraceElement element) {
        Node location = element.getLocation();
        return NodeAccessor.EXCEPTION.createDefaultStackTraceElementObject(element.getTarget().getRootNode(), location != null ? location.getEncapsulatingSourceSection() : null);
    }

    /**
     * Allows languages to perform actions before a root node is attempted to be compiled without
     * prior call to {@link #execute(VirtualFrame)}. By default this method returns
     * <code>null</code> to indicate that AOT compilation is not supported. Any non-null value
     * indicates that compilation without execution is supported for this root node. This method is
     * guaranteed to not be invoked prior to any calls to {@link #execute(VirtualFrame) execute}.
     * <p>
     * Common tasks that need to be performed by this method:
     * <ul>
     * <li>Initialize local variable types in the {@link FrameDescriptor} of the root node. Without
     * that any access to the frame will invalidate the code on first execute.
     * <li>Initialize specializing nodes with profiles that do not invalidate on first execution.
     * For initialization of Truffle DSL nodes see {@link com.oracle.truffle.api.dsl.AOTSupport}.
     * <li>Compute the expected execution signature of a root node and return it.
     * </ul>
     * <p>
     * If possible an {@link ExecutionSignature execution signature} should be returned for better
     * call efficiency. If the argument and return profile is not available or cannot be derived the
     * {@link ExecutionSignature#GENERIC} can be used to indicate that any value needs to be
     * expected for as argument from or as return value of the method. To indicate that a type is
     * unknown a <code>null</code> return or argument type should be used. The type
     * <code>Object</code> type should not be used in that case.
     * <p>
     * This method is invoked when no context is currently {@link TruffleContext#enter(Node)
     * entered} therefore no guest application code must be executed. The execution might happen on
     * any thread, even threads unknown to the guest language implementation. It is allowed to
     * create new {@link CallTarget call targets} during preparation of the root node or perform
     * modifications to the {@link TruffleLanguage language} of this root node.
     * <p>
     *
     * @see #prepareForCompilation(boolean, int, boolean)
     * @since 20.3
     */
    protected ExecutionSignature prepareForAOT() {
        return null;
    }

    /**
     * Helper method to create a root node that always returns the same value. Certain operations
     * (especially {@link com.oracle.truffle.api.interop inter-operability} API) require return of
     * stable {@link RootNode root nodes}. To simplify creation of such nodes, here is a factory
     * method that can create {@link RootNode} that returns always the same value.
     *
     * @param constant the constant to return
     * @return root node returning the constant
     * @since 0.8 or earlier
     */
    public static RootNode createConstantNode(Object constant) {
        return new Constant(constant);
    }

    /**
     * If this root node has a lexical scope parent, this method returns its frame descriptor.
     *
     * As an example, consider the following pseudocode:
     *
     * <pre>
     * def m {
     *   # For the "m" root node:
     *   # getFrameDescriptor       returns FrameDescriptor(m)
     *   # getParentFrameDescriptor returns null
     *   var_method = 0
     *   a = () -> {
     *     # For the "a lambda" root node:
     *     # getFrameDescriptor       returns FrameDescriptor(a)
     *     # getParentFrameDescriptor returns FrameDescriptor(m)
     *     var_lambda1 = 1
     *     b = () -> {
     *       # For the "b lambda" root node:
     *       # getFrameDescriptor       returns FrameDescriptor(b)
     *       # getParentFrameDescriptor returns FrameDescriptor(a)
     *       var_method + var_lambda1
     *     }
     *     b.call
     *   }
     *   a.call
     * }
     * </pre>
     *
     * This info is used by the runtime to optimize compilation order by giving more priority to
     * lexical parents which are likely to inline the child thus resulting in better performance
     * sooner rather than waiting for the lexical parent to get hot on its own.
     *
     * @return The frame descriptor of the lexical parent scope if it exists. <code>null</code>
     *         otherwise.
     * @since 22.3.0
     */
    protected FrameDescriptor getParentFrameDescriptor() {
        return null;
    }

    /**
     * Tests if two frames are the same. This method is mainly used by instruments in case of
     * <code>yield</code> of the execution and later resume. Frame comparison is used to match the
     * particular yielded and resumed execution.
     * <p>
     * The default implementation compares the frames identity.
     *
     * @since 24.0
     */
    protected boolean isSameFrame(Frame frame1, Frame frame2) {
        return frame1 == frame2;
    }

    final ReentrantLock getLazyLock() {
        ReentrantLock l = this.lock;
        if (l == null) {
            l = initializeLock();
        }
        return l;
    }

    private ReentrantLock initializeLock() {
        ReentrantLock l = new ReentrantLock();
        if (!RootNode.LOCK_UPDATER.compareAndSet(this, null, l)) {
            // if CAS failed, lock is already initialized; cannot be null after that.
            l = Objects.requireNonNull(this.lock);
        }
        return l;
    }

    /**
     * Computes a size estimate of this root node. This is intended to be overwritten if the size of
     * the root node cannot be estimated from the natural AST size. For example, in case the root
     * node is represented by a bytecode interpreter. If <code>-1</code> is returned, the regular
     * AST size estimate is going to be used. By default this method returns <code>-1</code>.
     * <p>
     * The size estimate is guaranteed to be invoked only once when the {@link CallTarget} is
     * created. This corresponds to calls to {@link #getCallTarget()} for the first time. This
     * method will never be invoked after the root node was already executed.
     *
     * @since 23.0
     */
    protected int computeSize() {
        return -1;
    }

    private static final class Constant extends RootNode {

        private final Object value;

        Constant(Object value) {
            super(null);
            this.value = value;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return value;
        }
    }

}
