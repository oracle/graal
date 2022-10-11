/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.FutureTask;

import org.graalvm.compiler.truffle.common.TruffleCompilerListener;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.GCUtils;

public class CompilationMemoryTest extends TestWithPolyglotOptions {

    @Before
    public void setUp() {
        setupContext("engine.CompileImmediately", "true", "engine.BackgroundCompilation", "false");
    }

    @Test
    public void testFieldsFreedAfterCompilation() throws InterruptedException {
        TestObject expected = new TestObject();
        OptimizedCallTarget callTarget = (OptimizedCallTarget) RootNode.createConstantNode(expected).getCallTarget();
        GraalTruffleRuntimeListenerImpl listener = new GraalTruffleRuntimeListenerImpl(callTarget);
        GraalTruffleRuntime.getRuntime().addListener(listener);
        try {
            Assert.assertEquals(expected, callTarget.call());
            Assert.assertEquals(expected, callTarget.call());
            Assert.assertTrue(callTarget.isValid());
        } finally {
            GraalTruffleRuntime.getRuntime().removeListener(listener);
        }
        Thread compilerTread = listener.thread;
        Assert.assertNotNull("Calltarget was not successfully compiled", compilerTread);
        // Even after a finished synchronous compilation, there may be a CompilationTask on the
        // compiler thread stack. It's because the FutureTask unparks waiting threads before its run
        // method that references the Callable, in our case CompilationTask, finishes. The
        // CompilationTask holds the CallTarget via inliningData. Therefore, we have to wait for the
        // compiler thread to actually finish FutureTask execution.
        awaitDone(compilerTread, 10_000);
        Reference<?> ref = new WeakReference<>(expected);
        expected = null;
        callTarget = null;
        GCUtils.assertGc("JavaConstant for TestObject should be freed after compilation.", ref);
    }

    private static boolean awaitDone(Thread t, long timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            boolean working = Arrays.stream(t.getStackTrace()).map(StackTraceElement::getClassName).anyMatch((name) -> name.equals(FutureTask.class.getName()));
            if (!working) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static final class GraalTruffleRuntimeListenerImpl implements GraalTruffleRuntimeListener {

        private volatile OptimizedCallTarget callTarget;
        volatile Thread thread;

        GraalTruffleRuntimeListenerImpl(OptimizedCallTarget callTarget) {
            this.callTarget = callTarget;
        }

        @Override
        public synchronized void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, TruffleCompilerListener.GraphInfo graph,
                        TruffleCompilerListener.CompilationResultInfo result, int tier) {
            if (callTarget.equals(target)) {
                callTarget = null;
                thread = Thread.currentThread();
            }
        }
    }

    private static final class TestObject implements TruffleObject {
    }
}
