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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.ExecutionContext;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultCompilerOptions;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A root node is a node with a method to execute it given only a frame as a parameter. Therefore, a
 * root node can be used to create a call target using
 * {@link TruffleRuntime#createCallTarget(RootNode)}.
 *
 * @since 0.8 or earlier
 */
@SuppressWarnings("rawtypes")
public abstract class RootNode extends Node {
    final Class<? extends TruffleLanguage> language;
    private RootCallTarget callTarget;
    @CompilationFinal private FrameDescriptor frameDescriptor;
    private final SourceSection sourceSection;

    final ReentrantLock lock = new ReentrantLock();

    /**
     * Creates new root node. Each {@link RootNode} is associated with a particular language - if
     * the root node represents a method it is assumed the method is written in such language.
     * <p>
     * <strong>Note:</strong> Although the {@link SourceSection} <em>can</em> be {@code null}, this
     * is strongly discouraged for the purposes of testing/tracing/tooling. Please use
     * {@link Source#createUnavailableSection()} to create a descriptive instance with a
     * language-specific <em>kind</em> such as "SL Builtin" and a <em>name</em> if possible.
     *
     * @param language the language of the node, <b>cannot be</b> <code>null</code>
     * @param sourceSection a part of source associated with this node, can be <code>null</code>
     * @param frameDescriptor descriptor of slots, can be <code>null</code>
     * @since 0.8 or earlier
     */
    protected RootNode(Class<? extends TruffleLanguage> language, SourceSection sourceSection, FrameDescriptor frameDescriptor) {
        this(language, sourceSection, frameDescriptor, true);
    }

    private RootNode(Class<? extends TruffleLanguage> language, SourceSection sourceSection, FrameDescriptor frameDescriptor, boolean checkLanguage) {
        super();
        if (checkLanguage) {
            if (!TruffleLanguage.class.isAssignableFrom(language)) {
                throw new IllegalStateException();
            }
        }
        this.sourceSection = sourceSection;
        this.language = language;
        if (frameDescriptor == null) {
            this.frameDescriptor = new FrameDescriptor();
        } else {
            this.frameDescriptor = frameDescriptor;
        }
    }

    /** @since 0.8 or earlier */
    @Override
    public Node copy() {
        RootNode root = (RootNode) super.copy();
        root.frameDescriptor = frameDescriptor;
        return root;
    }

    /**
     * Returns source section specified when
     * {@link #RootNode(java.lang.Class, com.oracle.truffle.api.source.SourceSection, com.oracle.truffle.api.frame.FrameDescriptor)
     * constructing the node}.
     *
     * @return source section passed into the constructor
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
     * its own and might therefore be able to safe memory.
     *
     * @return <code>true</code> if calls {@link #cloneUninitialized() uninitialized copies} are
     *         supported.
     * @see #cloneUninitialized()
     * @since 0.24
     */
    public boolean isCloneUninitializedSupported() {
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
     * and might therefore be able to safe memory.
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
    public RootNode cloneUninitialized() {
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
     */
    public ExecutionContext getExecutionContext() {
        return null;
    }

    /**
     * Get compiler options specific to this <code>RootNode</code>.
     *
     * @since 0.8 or earlier
     */
    public CompilerOptions getCompilerOptions() {
        final ExecutionContext context = getExecutionContext();

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
            super(TruffleLanguage.class, null, null);
            this.value = value;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return value;
        }
    }

}
