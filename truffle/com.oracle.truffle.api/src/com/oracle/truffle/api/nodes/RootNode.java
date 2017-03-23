/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.ParsingRequest;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
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
 * note that root nodes with <code>null</code> language are considered not instrumentable and have
 * no access to the {@link #getLanguage(Class) language} or its public {@link #getLanguageInfo()
 * information}.
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
 * <li>The AST contains at least one node that is annotated with
 * {@link com.oracle.truffle.api.instrumentation.Instrumentable} .
 * </ul>
 * <p>
 * <strong>Note:</strong> It is recommended to override {@link #getSourceSection()} and provide a
 * source section if available. This allows for better testing/tracing/tooling. If no concrete
 * source section is available please consider using {@link Source#createUnavailableSection()}.
 *
 * @since 0.8 or earlier
 */
@SuppressWarnings("rawtypes")
public abstract class RootNode extends Node {

    /*
     * Since languages were singletons in the past, we cannot use the Env instance stored in
     * TruffleLanguage for languages that are not yet migrated. We use this env reference instead
     * for compatibility.
     */
    private final LanguageInfo languageInfo;
    private RootCallTarget callTarget;
    @CompilationFinal private FrameDescriptor frameDescriptor;
    private final SourceSection sourceSection;
    final ReentrantLock lock = new ReentrantLock();

    /**
     * @deprecated use {@link RootNode(TruffleLanguage, FrameDescriptor)} instead. Root nodes do not
     *             support source sections by default any longer. Please override
     *             {@link #getSourceSection()} instead if a source section is available.
     * @since 0.8 or earlier
     */
    @Deprecated
    protected RootNode(Class<? extends TruffleLanguage> language, SourceSection sourceSection, FrameDescriptor frameDescriptor) {
        CompilerAsserts.neverPartOfCompilation();
        if (!TruffleLanguage.class.isAssignableFrom(language)) {
            throw new IllegalStateException();
        }

        this.languageInfo = Node.ACCESSOR.languageSupport().getLegacyLanguageInfo(language);
        this.sourceSection = sourceSection;
        if (frameDescriptor == null) {
            this.frameDescriptor = new FrameDescriptor();
        } else {
            this.frameDescriptor = frameDescriptor;
        }
    }

    /**
     * Creates new root node with a given language instance. The language instance is obtainable
     * while {@link TruffleLanguage#createContext(Env)} or
     * {@link TruffleLanguage#parse(ParsingRequest)} is executed. If no language environment is
     * available, then <code>null</code> can be passed. Please note that root nodes with
     * <code>null</code> language are considered not instrumentable and have no access to the
     * {@link #getLanguage(Class) language} or its public {@link #getLanguageInfo() information}.
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
     * <code>null</code> language are considered not instrumentable and have no access to the
     * {@link #getLanguage(Class) language} or its public {@link #getLanguageInfo() information}.
     *
     * @param language the language this root node is associated with
     * @since 0.25
     */
    protected RootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        CompilerAsserts.neverPartOfCompilation();
        if (language != null) {
            this.languageInfo = Node.ACCESSOR.languageSupport().getLanguageInfo(language);
        } else {
            this.languageInfo = null;
        }
        this.frameDescriptor = frameDescriptor == null ? new FrameDescriptor() : frameDescriptor;
        this.sourceSection = null;
    }

    /**
     * Returns the language instance associated with this root node. The language instance is
     * intended for internal use in languages and is only accessible if the concrete type of the
     * language is known. Public information about the language can be accessed using
     * {@link #getLanguageInfo()}. The language is <code>null</code> if the root node is not
     * associated with a <code>null</code> language.
     *
     * @see #getLanguageInfo()
     * @since 0.25
     */
    @SuppressWarnings("unchecked")
    public final <C extends TruffleLanguage> C getLanguage(Class<C> languageClass) {
        if (languageInfo == null) {
            return null;
        }
        TruffleLanguage<?> language = languageInfo.getSpi();
        if (language.getClass() != languageClass) {
            if (!languageClass.isInstance(language) || languageClass == TruffleLanguage.class || !TruffleLanguage.class.isAssignableFrom(languageClass)) {
                CompilerDirectives.transferToInterpreter();
                throw new ClassCastException("Illegal language class specified. Expected " + language.getClass().getName() + ".");
            }
        }
        return (C) language;

    }

    /**
     * Returns public information about the language. The language can be assumed equal if the
     * instances of the language info instance are the same. To access internal details of the
     * language within the language implementation use {@link #getLanguage(Class)}.
     *
     * @since 0.25
     */
    public final LanguageInfo getLanguageInfo() {
        return languageInfo;
    }

    /** @since 0.8 or earlier */
    @Override
    public Node copy() {
        RootNode root = (RootNode) super.copy();
        root.frameDescriptor = frameDescriptor;
        return root;
    }

    /**
     * Returns the source section associated with this {@link RootNode}. Returns <code>null</code>
     * if by default.
     *
     * @since 0.13
     */
    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    /**
     * A description of the AST (expected to be a method or procedure name in most languages) that
     * identifies the AST for the benefit of guest language programmers using tools; it might
     * appear, for example in the context of a stack dump or trace and is not expected to be called
     * often.
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
     * @since 0.8 or earlier
     * @deprecated use {@link LoopNode#reportLoopCount(Node,int)} instead
     */
    @Deprecated
    public final void reportLoopCount(int iterations) {
        LoopNode.reportLoopCount(this, iterations);
    }

    /**
     * Executes this function using the specified frame and returns the result value.
     *
     * @param frame the frame of the currently executing guest language method
     * @return the value of the execution
     * @since 0.8 or earlier
     */
    public abstract Object execute(VirtualFrame frame);

    /** @since 0.8 or earlier */
    public final RootCallTarget getCallTarget() {
        return callTarget;
    }

    /** @since 0.8 or earlier */
    public final FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    /**
     * @since 0.8 or earlier
     * @deprecated No replacement. Changing {@link CallTarget} of an existing {@link RootNode} isn't
     *             a supported operation
     */
    @Deprecated
    public final void setCallTarget(RootCallTarget callTarget) {
        this.callTarget = callTarget;
    }

    /**
     * Returns the {@link ExecutionContext} associated with this <code>RootNode</code>. This allows
     * the correct <code>ExecutionContext</code> to be determined for a <code>RootNode</code> (and
     * so also for a {@link RootCallTarget} and a {@link FrameInstance} obtained from the call
     * stack) without prior knowledge of the language it has come from.
     *
     * Returns <code>null</code> by default.
     *
     * @since 0.8 or earlier
     * @deprecated in 0.25 use {@link #getLanguage(Class) getLanguage(Language.class)}.
     *             {@link TruffleLanguage#getCurrentContext(Class) getCurrentContext()} instead.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public com.oracle.truffle.api.ExecutionContext getExecutionContext() {
        return null;
    }

    /**
     * Get compiler options specific to this <code>RootNode</code>.
     *
     * @since 0.8 or earlier
     */
    @SuppressWarnings("deprecation")
    public CompilerOptions getCompilerOptions() {
        final com.oracle.truffle.api.ExecutionContext context = getExecutionContext();

        if (context == null) {
            return DefaultCompilerOptions.INSTANCE;
        } else {
            return context.getCompilerOptions();
        }
    }

    /**
     * Does this contain AST content that it is possible to instrument.
     *
     * @since 0.8 or earlier
     */
    protected boolean isInstrumentable() {
        return true;
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
