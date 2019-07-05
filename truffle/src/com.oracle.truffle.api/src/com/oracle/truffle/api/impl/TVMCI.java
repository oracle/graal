/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.io.Closeable;
import java.util.Objects;
import java.util.function.Supplier;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.impl.Accessor.CallInlined;
import com.oracle.truffle.api.impl.Accessor.CallProfiled;
import com.oracle.truffle.api.impl.Accessor.CastUnsafe;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.impl.Accessor.InstrumentSupport;
import com.oracle.truffle.api.nodes.IndirectCallNode;
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
     * @param <C> an {@link Closeable} subclass for cleaning up after a test run
     * @param <T> the {@link CallTarget} subclass of the hosting virtual machine
     *
     * @since 0.25
     */
    public abstract static class Test<C extends Closeable, T extends CallTarget> {

        /**
         * Create a test context object. This method will be called once for every unit test run,
         * and the returned object will be closed when the unit test is finished. Implementors may
         * return null if no context object is needed.
         *
         * @param testName the name of the unit test
         * @return a context object
         *
         * @since 19.0
         */
        protected abstract C createTestContext(String testName);

        /**
         * Create a call target for the purpose of running a unit test.
         *
         * @param testContext the context of the current unit test
         * @param testNode the root node containing the test code
         * @return a call target
         *
         * @since 0.25
         */
        protected abstract T createTestCallTarget(C testContext, RootNode testNode);

        /**
         * Notify the VM that the warmup is finished, and it should now compile the test code.
         *
         * @param testContext the context of the current unit test
         * @param callTarget a call target that was created with {@link #createTestCallTarget}
         *
         * @since 0.25
         */
        protected abstract void finishWarmup(C testContext, T callTarget);
    }

    /**
     * Only useful for virtual machine implementors.
     *
     * @since 0.12
     */
    protected TVMCI() {
        // export only for select packages
        assert checkCaller();
    }

    private boolean checkCaller() {
        final String packageName = getClass().getPackage().getName();
        assert packageName.equals("org.graalvm.compiler.truffle.runtime") ||
                        packageName.equals("org.graalvm.graal.truffle") ||
                        packageName.equals("com.oracle.graal.truffle") ||
                        packageName.equals("com.oracle.truffle.api.impl") : //
        TVMCI.class.getName() + " subclass is not in trusted package: " + getClass().getName();
        return true;
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
        InstrumentSupport support = TVMCIAccessor.instrumentAccess();
        if (support != null) {
            support.onLoad(rootNode);
        }
    }

    protected void setCallTarget(RootNode root, RootCallTarget callTarget) {
        TVMCIAccessor.nodesAccess().setCallTarget(root, callTarget);
    }

    /**
     * Makes sure the <code>rootNode</code> is initialized.
     *
     * @param rootNode
     * @since 0.12
     */
    protected void onFirstExecution(RootNode rootNode) {
        final Accessor.InstrumentSupport accessor = TVMCIAccessor.instrumentAccess();
        if (accessor != null) {
            accessor.onFirstExecution(rootNode);
        }
    }

    /**
     * Accessor for non-public state in {@link FrameDescriptor}.
     *
     * @since 0.14
     */
    protected void markFrameMaterializeCalled(FrameDescriptor descriptor) {
        TVMCIAccessor.framesAccess().markMaterializeCalled(descriptor);
    }

    /**
     * Accessor for non-public state in {@link FrameDescriptor}.
     *
     * @since 0.14
     */
    protected boolean getFrameMaterializeCalled(FrameDescriptor descriptor) {
        return TVMCIAccessor.framesAccess().getMaterializeCalled(descriptor);
    }

    /**
     * Accessor for non-public API in {@link RootNode}.
     *
     * @since 0.24
     */
    protected boolean isCloneUninitializedSupported(RootNode root) {
        return TVMCIAccessor.nodesAccess().isCloneUninitializedSupported(root);
    }

    protected void onThrowable(Node callNode, RootCallTarget root, Throwable e, Frame frame) {
        final Accessor.LanguageSupport language = TVMCIAccessor.languageAccess();
        if (language != null) {
            language.onThrowable(callNode, root, e, frame);
        }
    }

    /**
     * Accessor for non-public API in {@link RootNode}.
     *
     * @since 0.24
     */
    protected RootNode cloneUninitialized(RootNode root) {
        return TVMCIAccessor.nodesAccess().cloneUninitialized(root);
    }

    protected int adoptChildrenAndCount(RootNode root) {
        return TVMCIAccessor.nodesAccess().adoptChildrenAndCount(root);
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
        EngineSupport engine = TVMCIAccessor.engineAccess();
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

    @SuppressWarnings("unused")
    protected void initializeProfile(CallTarget target, Class<?>[] argumentTypes) {
    }

    /**
     * Accessor for {@link TVMCI#Test} class.
     *
     * @param <C>
     * @param <T>
     *
     * @since 0.25
     */
    public static class TestAccessor<C extends Closeable, T extends CallTarget> {

        private final TVMCI.Test<C, T> testTvmci;

        protected TestAccessor(TVMCI.Test<C, T> testTvmci) {
            if (!this.getClass().getPackage().getName().equals("com.oracle.truffle.tck")) {
                throw new IllegalStateException();
            }
            this.testTvmci = testTvmci;
        }

        protected final C createTestContext(String testName) {
            return testTvmci.createTestContext(testName);
        }

        protected final T createTestCallTarget(C testContext, RootNode testNode) {
            return testTvmci.createTestCallTarget(testContext, testNode);
        }

        protected final void finishWarmup(C testContext, T callTarget) {
            testTvmci.finishWarmup(testContext, callTarget);
        }
    }

    private static volatile Object fallbackEngineData;

    protected <T> T getOrCreateRuntimeData(RootNode rootNode, Supplier<T> constructor) {
        Objects.requireNonNull(constructor);
        final Accessor.NodeSupport nodesAccess = TVMCIAccessor.nodesAccess();
        final EngineSupport engineAccess = TVMCIAccessor.engineAccess();
        if (rootNode != null && nodesAccess != null && engineAccess != null) {
            final Object sourceVM = nodesAccess.getSourceVM(rootNode);
            if (sourceVM != null) {
                final T runtimeData = engineAccess.getOrCreateRuntimeData(sourceVM, constructor);
                if (runtimeData != null) {
                    return runtimeData;
                }
            }

        }
        return getOrCreateFallbackEngineData(constructor);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getOrCreateFallbackEngineData(Supplier<T> constructor) {
        if (fallbackEngineData == null) {
            fallbackEngineData = constructor.get();
        }
        return (T) fallbackEngineData;
    }

    @SuppressWarnings("unused")
    protected void reportPolymorphicSpecialize(Node node) {
    }

    protected ThreadLocal<Object> createFastThreadLocal() {
        return new ThreadLocal<>();
    }

    protected IndirectCallNode createUncachedIndirectCall() {
        return null;
    }

    protected CallInlined getCallInlined() {
        return null;
    }

    protected CallProfiled getCallProfiled() {
        return null;
    }

    protected CastUnsafe getCastUnsafe() {
        return null;
    }

}
