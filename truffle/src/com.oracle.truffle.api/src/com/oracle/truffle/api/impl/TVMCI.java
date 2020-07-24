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
package com.oracle.truffle.api.impl;

import java.io.Closeable;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.impl.Accessor.RuntimeSupport;
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

    protected abstract RuntimeSupport createRuntimeSupport(Object permission);

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

    /**
     * Used to get an {@link org.graalvm.compiler.truffle.runtime.EngineData}, which contains the
     * option values for --engine options, and other per-Engine data. Called in the
     * {@link org.graalvm.compiler.truffle.runtime.OptimizedCallTarget} constructor.
     * <p>
     * The resulting instance is cached in the Engine.
     */
    @SuppressWarnings("unchecked")
    protected static <T> T getOrCreateRuntimeData(RootNode rootNode, BiFunction<OptionValues, Supplier<TruffleLogger>, T> constructor) {
        Objects.requireNonNull(constructor);
        final Accessor.NodeSupport nodesAccess = DefaultRuntimeAccessor.NODES;
        final EngineSupport engineAccess = DefaultRuntimeAccessor.ENGINE;

        final Object polyglotEngine;
        if (rootNode == null) {
            polyglotEngine = engineAccess.getCurrentPolyglotEngine();
        } else {
            polyglotEngine = nodesAccess.getPolyglotEngine(rootNode);
        }

        if (polyglotEngine != null) {
            return engineAccess.getOrCreateRuntimeData(polyglotEngine, constructor);
        } else {
            if (fallbackEngineData == null) {
                fallbackEngineData = engineAccess.getOrCreateRuntimeData(null, constructor);
            }
            return (T) fallbackEngineData;
        }
    }

    protected static void resetFallbackEngineData() {
        fallbackEngineData = null;
    }

}
