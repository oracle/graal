/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.impl.Accessor.InstrumentSupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * An interface between Truffle API and hosting virtual machine. Not interesting for regular Truffle
 * API users. Acronym for Truffle Virtual Machine Compiler Interface.
 *
 * @since 0.12
 */
public abstract class TVMCI {

    /**
     * An interface between the Truffle test runner and hosting virtual machine.
     *
     * @param <T> the {@link CallTarget} subclass of the hosting virtual machine
     *
     * @since 0.25
     */
    public abstract static class Test<T extends CallTarget> {

        /**
         * Create a call target for the purpose of running a unit test.
         *
         * @param testName the name of the unit test
         * @param testNode the root node containing the test code
         * @return a call target
         *
         * @since 0.25
         */
        protected abstract T createTestCallTarget(String testName, RootNode testNode);

        /**
         * Notify the VM that the warmup is finished, and it should now compile the test code.
         *
         * @param callTarget a call target that was created with {@link #createTestCallTarget}
         *
         * @since 0.25
         */
        protected abstract void finishWarmup(T callTarget);
    }

    /**
     * Only useful for virtual machine implementors.
     *
     * @since 0.12
     */
    protected TVMCI() {
        // export only for select packages
        assert getClass().getPackage().getName().equals("org.graalvm.compiler.truffle") ||
                        getClass().getPackage().getName().equals("com.oracle.graal.truffle") ||
                        getClass().getPackage().getName().equals("com.oracle.truffle.api.impl");
    }

    /**
     * Reports the execution count of a loop.
     *
     * @param source the Node which invoked the loop.
     * @param iterations the number iterations to report to the runtime system
     * @since 0.12
     */
    protected abstract void onLoopCount(Node source, int iterations);

    /**
     * Reports when a new root node is loaded into the system.
     *
     * @since 0.15
     */
    protected void onLoad(RootNode rootNode) {
        InstrumentSupport support = Accessor.instrumentAccess();
        if (support != null) {
            support.onLoad(rootNode);
        }
    }

    /**
     * Makes sure the <code>rootNode</code> is initialized.
     *
     * @param rootNode
     * @since 0.12
     */
    protected void onFirstExecution(RootNode rootNode) {
        final Accessor.InstrumentSupport accessor = Accessor.instrumentAccess();
        if (accessor != null) {
            accessor.onFirstExecution(rootNode);
        }
    }

    /**
     * Finds the language associated with given root node.
     *
     * @param root the node
     * @return the language of the node
     * @since 0.12
     * @deprecated no replacement
     */
    @SuppressWarnings({"rawtypes"})
    @Deprecated
    protected Class<? extends TruffleLanguage> findLanguageClass(RootNode root) {
        return root.getLanguage(TruffleLanguage.class).getClass();
    }

    /**
     * Accessor for non-public state in {@link FrameDescriptor}.
     *
     * @since 0.14
     */
    protected void markFrameMaterializeCalled(FrameDescriptor descriptor) {
        Accessor.framesAccess().markMaterializeCalled(descriptor);
    }

    /**
     * Accessor for non-public state in {@link FrameDescriptor}.
     *
     * @since 0.14
     */
    protected boolean getFrameMaterializeCalled(FrameDescriptor descriptor) {
        return Accessor.framesAccess().getMaterializeCalled(descriptor);
    }

    /**
     * Accessor for non-public API in {@link RootNode}.
     *
     * @since 0.24
     */
    protected boolean isCloneUninitializedSupported(RootNode root) {
        return Accessor.nodesAccess().isCloneUninitializedSupported(root);
    }

    protected void onThrowable(RootNode root, Throwable e) {
        final Accessor.LanguageSupport language = Accessor.languageAccess();
        if (language != null) {
            language.onThrowable(root, e);
        }
    }

    /**
     * Accessor for non-public API in {@link RootNode}.
     *
     * @since 0.24
     */
    protected RootNode cloneUninitialized(RootNode root) {
        return Accessor.nodesAccess().cloneUninitialized(root);
    }

    /**
     * Returns the compiler options specified available from the runtime.
     *
     * @since 0.27
     */
    protected OptionDescriptors getCompilerOptionDescriptors() {
        return OptionDescriptors.EMPTY;
    }

    /**
     * Invoked when a call target is invoked to find out its option values.
     * {@link OptionValues#getDescriptors()} must match the value returned by
     * {@link #getCompilerOptionDescriptors()}.
     *
     * @since 0.27
     */
    protected OptionValues getCompilerOptionValues(RootNode rootNode) {
        EngineSupport engine = Accessor.engineAccess();
        return engine != null ? engine.getCompilerOptionValues(rootNode) : null;
    }

    /**
     * Returns <code>true</code> if the java stack frame is a representing a guest language call.
     * Needs to return <code>true</code> only once per java stack frame per guest language call.
     *
     * @since 0.27
     */
    protected boolean isGuestCallStackFrame(@SuppressWarnings("unused") StackTraceElement e) {
        return false;
    }

    /**
     * Accessor for {@link TVMCI#Test} class.
     *
     * @param <T>
     *
     * @since 0.25
     */
    public static class TestAccessor<T extends CallTarget> {

        private final TVMCI.Test<T> testTvmci;

        protected TestAccessor(TVMCI.Test<T> testTvmci) {
            if (!this.getClass().getPackage().getName().equals("com.oracle.truffle.tck")) {
                throw new IllegalStateException();
            }
            this.testTvmci = testTvmci;
        }

        protected final T createTestCallTarget(String testName, RootNode testNode) {
            return testTvmci.createTestCallTarget(testName, testNode);
        }

        protected final void finishWarmup(T callTarget) {
            testTvmci.finishWarmup(callTarget);
        }
    }

}
