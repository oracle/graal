/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.common.CancellationBailoutException;
import org.graalvm.compiler.core.common.CompilationRequestIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.truffle.CancellableCompileTask;
import org.graalvm.compiler.truffle.DefaultInliningPolicy;
import org.graalvm.compiler.truffle.DefaultTruffleCompiler;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.TruffleCompiler;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.TruffleInlining;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Test case that tests the cooperative compilation cancellation mechanism of Graal.
 */
public class TruffleCompilationCancellationTest {

    private static final GraalTruffleRuntime runtime;
    private static final TruffleCompiler truffleCompiler;

    static {
        runtime = (GraalTruffleRuntime) Truffle.getRuntime();
        Backend backend = runtime.getRequiredGraalCapability(RuntimeProvider.class).getHostBackend();
        OptionValues options = TruffleCompilerOptions.getOptions();
        Suites suites = backend.getSuites().getDefaultSuites(options);
        Suites copy = suites.copy();
        /*
         * Stuff in some longer running phases to ensure that at least some portion of the
         * compilation happened (e.g. high tier) before the bailout triggers.
         */
        copy.getHighTier().appendPhase(new SleepingPhase(50/* ms */));
        copy.getMidTier().appendPhase(new SleepingPhase(50/* ms */));
        copy.getLowTier().appendPhase(new SleepingPhase(50/* ms */));
        truffleCompiler = DefaultTruffleCompiler.createWithSuites(runtime, copy);
    }

    private static final class SleepingPhase extends Phase {
        private final long ms;

        SleepingPhase(long ms) {
            this.ms = ms;
        }

        @Override
        protected void run(StructuredGraph graph) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                GraalError.shouldNotReachHere(e);
            }
        }
    }

    private static final OptimizedCallTarget EMPTY_CALLEE = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(TruffleLanguage.class, null, null) {
        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

        @Override
        public String toString() {
            return "CALLEE_NO_EXCEPTION";
        }
    });

    private static void partialEval(OptimizedCallTarget compilable, Object[] arguments, AllowAssumptions allowAssumptions, CancellableCompileTask c) {
        compilable.call(arguments);
        compilable.call(arguments);
        compilable.call(arguments);
        StructuredGraph g = null;
        g = truffleCompiler.getPartialEvaluator().createGraph(compilable, new TruffleInlining(compilable, new DefaultInliningPolicy()), allowAssumptions, INVALID_COMPILATION_ID, c);
        truffleCompiler.compileMethodHelper(g, "test", null, compilable, CompilationRequestIdentifier.asCompilationRequest(INVALID_COMPILATION_ID), null);
        Assert.fail("Finished compilation on non exceptional path.");
    }

    private volatile boolean caught;

    @Test
    public void testCompilationCancellation() {
        CancellableCompileTask cc = new CancellableCompileTask();
        Callable<Object> c = new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                try {
                    partialEval(EMPTY_CALLEE, new Object[0], AllowAssumptions.YES, cc);
                    Assert.fail("Finished compilation on non exceptional path.");
                } catch (CancellationBailoutException t) {
                    caught = true;
                }
                return null;
            }
        };
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newScheduledThreadPool(1);
        cc.setFuture(executor.submit(c));

        // wait some time, compilation must have started
        try {
            Thread.sleep(50/* ms */);
        } catch (InterruptedException e) {
            GraalError.shouldNotReachHere(e);
        }

        // cancel the task, future will be visible as done() && cancelled()
        cc.cancel();
        // give the cancel some time to get through to the cooperative check by the compiler thread,
        // future will be done already
        try {
            Thread.sleep(500/* ms */);
        } catch (InterruptedException e) {
            GraalError.shouldNotReachHere(e);
        }

        // bailout must already been caught
        Assert.assertTrue(caught);

        // query the state, cancellation bit must be set on the future
        try {
            cc.getFuture().get(100, TimeUnit.MILLISECONDS);
            Assert.fail("Future must be cancelled()");
        } catch (CancellationException e) {
            /*
             * Expected behavior if the future is soft cancelled via a call to
             * CancellableCompileTask#cancel.
             */
        } catch (Throwable t) {
            t.printStackTrace();
            Assert.fail("Took too much time for the compiler thread to cancel the current compilation.");
        }
    }

}
