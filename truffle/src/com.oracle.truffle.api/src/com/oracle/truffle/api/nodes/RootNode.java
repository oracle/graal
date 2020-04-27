/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.ParsingRequest;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultCompilerOptions;
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
 * In order to execute a root node, a call target needs to be created using
 * {@link TruffleRuntime#createCallTarget(RootNode)}. This allows the runtime system to optimize the
 * execution of the AST. The {@link CallTarget} can either be {@link CallTarget#call(Object...)
 * called} directly from runtime code or {@link DirectCallNode direct} and {@link IndirectCallNode
 * indirect} call nodes can be created, inserted in a child field and
 * {@link DirectCallNode#call(Object[]) called}. The use of direct call nodes allows the framework
 * to automatically inline and further optimize call sites based on heuristics.
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
public abstract class RootNode extends ExecutableNode {

    private static final AtomicReferenceFieldUpdater<RootNode, ReentrantLock> LOCK_UPDATER = AtomicReferenceFieldUpdater.newUpdater(RootNode.class, ReentrantLock.class, "lock");

    private volatile RootCallTarget callTarget;
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

    /**
     * @see TruffleLanguage#getContextReference()
     * @since 0.27
     * @deprecated use {@link #lookupContextReference(Class)} instead.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public final <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass) {
        if (getLanguage() == null) {
            return null;
        }
        return getLanguage(languageClass).getContextReference().get();
    }

    /** @since 0.8 or earlier */
    @Override
    public Node copy() {
        RootNode root = (RootNode) super.copy();
        root.frameDescriptor = frameDescriptor;
        return root;
    }

    /**
     * Returns a qualified name of the AST that in the best case uniquely identifiers the method. If
     * the qualified name is not specified by the root, then the {@link #getName() name} is used by
     * default. A root node that represents a Java method could consist of the package name, the
     * class name and the method name. E.g. <code>mypackage.MyClass.myMethod</code>
     *
     * @since 20.0.0 beta 1
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

    @TruffleBoundary
    private SourceSection materializeSourceSection() {
        return getSourceSection();
    }

    /**
     * Returns <code>true</code> if a TruffleException leaving this node should capture
     * {@link Frame} objects in its stack trace in addition to the default information. This is
     * <code>false</code> by default to avoid the attached overhead. The captured frames are then
     * accessible through {@link TruffleStackTraceElement#getFrame()}
     *
     * @since 0.31
     */
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
        return callTarget;
    }

    /** @since 0.8 or earlier */
    public final FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    /** @since 19.0 */
    protected final void setCallTarget(RootCallTarget callTarget) {
        this.callTarget = callTarget;
    }

    /**
     * Get compiler options specific to this <code>RootNode</code>.
     *
     * @since 0.8 or earlier
     */
    public CompilerOptions getCompilerOptions() {
        return DefaultCompilerOptions.INSTANCE;
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
     * Provide a list of stack frames that led to a schedule of asynchronous execution of this root
     * node on the provided frame. The asynchronous frames are expected to be found here when
     * {@link Env#getAsynchronousStackDepth()} is positive. The language is free to provide
     * asynchronous frames or longer list of frames when it's of no performance penalty, or if
     * requested by other options. This method is invoked on slow-paths only and with a context
     * entered.
     *
     * @param frame A frame, never <code>null</code>
     * @return a list of {@link TruffleStackTraceElement}, or <code>null</code> when no asynchronous
     *         stack is available.
     * @see Env#getAsynchronousStackDepth()
     * @since 20.1.0
     */
    protected List<TruffleStackTraceElement> findAsynchronousFrames(Frame frame) {
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
