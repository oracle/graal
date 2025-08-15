/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.api.test.common.NullObject;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;

import jdk.graal.compiler.util.CollectionsUtil;

public class NeverInlineFailedTest {

    static class Caller extends RootNode {
        @Child private DirectCallNode callNode;

        Caller(CallTarget target) {
            super(null);
            this.callNode = Truffle.getRuntime().createDirectCallNode(target);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.call(frame.getArguments());
        }

        @Override
        public String getName() {
            return "caller";
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    static class Callee extends RootNode {

        Callee() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return NullObject.SINGLETON;
        }

        @Override
        protected boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
            throw new RuntimeException("Intentionally fail compilation");
        }

        @Override
        public String getName() {
            return "callee";
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    static class Callee2 extends RootNode {

        private static class MyClass {
            int[] array = new int[100];

            MyClass() {
                for (int i = 0; i < array.length; i++) {
                    array[i] = value();
                }
            }

            @CompilerDirectives.TruffleBoundary
            private static int value() {
                return 1;
            }

            public int sum() {
                int sum = 0;
                for (int i : array) {
                    sum += i;
                }
                return sum;
            }
        }

        @SuppressWarnings("unused") private int global;
        @SuppressWarnings("unused") private int globalI;

        Callee2() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            recurse();
            foo();
            return null;
        }

        @ExplodeLoop
        private void recurse() {
            for (int i = 0; i < 1000; i++) {
                getF().apply(0);
            }
        }

        private Function<Integer, Integer> getF() {
            return new Function<>() {
                @Override
                public Integer apply(Integer integer) {
                    return integer < 500 ? getF().apply(integer + 1) : 0;
                }
            };
        }

        protected void foo() {
            for (; globalI < 1000; globalI++) {
                global += new MyClass().sum();
            }
        }
    };

    @Test
    public void testNeverInlineFailed() throws IOException, InterruptedException {
        testNeverInlineFailedImpl(Callee::new, CollectionsUtil.mapOfEntries(), "java.lang.RuntimeException: Intentionally fail compilation");
    }

    @Test
    public void testNeverInlineFailed2() throws IOException, InterruptedException {
        testNeverInlineFailedImpl(Callee2::new, CollectionsUtil.mapOf("compiler.CompilationTimeout", "1"), "jdk.graal.compiler.core.common.PermanentBailoutException: Compilation exceeded");
    }

    private static void testNeverInlineFailedImpl(Supplier<RootNode> calleeSupplier, Map<String, String> extraOptions, String reasonContains) throws IOException, InterruptedException {
        Assume.assumeTrue(Truffle.getRuntime() instanceof OptimizedTruffleRuntime);
        Runnable test = () -> {
            OptimizedTruffleRuntime optimizedTruffleRuntime = (OptimizedTruffleRuntime) Truffle.getRuntime();
            AtomicReference<CallTarget> calleeRef = new AtomicReference<>();
            AtomicReference<CallTarget> callerRef = new AtomicReference<>();
            AtomicReference<String> calleeCompilationFailure = new AtomicReference<>();
            AtomicInteger callerCompilationInlinedCalls = new AtomicInteger(-1);
            OptimizedTruffleRuntimeListener listener = new OptimizedTruffleRuntimeListener() {
                @Override
                public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
                    if (target == calleeRef.get()) {
                        calleeCompilationFailure.set(reason);
                    }
                }

                @Override
                public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph,
                                TruffleCompilerListener.CompilationResultInfo result) {
                    if (target == callerRef.get()) {
                        callerCompilationInlinedCalls.set(task.countInlinedCalls());
                    }
                }
            };
            optimizedTruffleRuntime.addListener(listener);
            try (Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "true").option("engine.BackgroundCompilation", "false").option(
                            "engine.CompilationFailureAction", "Silent").options(extraOptions).build()) {
                context.enter();
                CallTarget calleeTarget = calleeSupplier.get().getCallTarget();
                calleeRef.set(calleeTarget);
                calleeTarget.call();
                Assert.assertNotNull(calleeCompilationFailure.get());
                Assert.assertTrue("Unexpected compilation failure reason: " + calleeCompilationFailure.get(),
                                calleeCompilationFailure.get().contains(reasonContains));
                CallTarget callerTarget = new Caller(calleeTarget).getCallTarget();
                callerRef.set(callerTarget);
                callerTarget.call();
                Assert.assertEquals(0, callerCompilationInlinedCalls.get());
                Assert.assertTrue(((OptimizedCallTarget) callerTarget).isValid());
            } finally {
                optimizedTruffleRuntime.removeListener(listener);
            }
        };
        SubprocessTestUtils.newBuilder(NeverInlineFailedTest.class, test).postfixVmOption("-Djdk.graal.CompilationFailureAction=Silent").run();
    }
}
