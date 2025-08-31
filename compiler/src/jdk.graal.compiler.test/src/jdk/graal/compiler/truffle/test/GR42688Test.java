/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.SingleTierCompilationThreshold;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;

/*
 * Test that reproduces the not-yet fixed bug GR-42688 and makes sure the issue is alleviated by MaximumCompilations limit.
 */
public class GR42688Test {

    @SuppressWarnings("truffle-inlining")
    public abstract static class ClassNode extends Node {

        public abstract Class<?> execute(Object value);

        @SuppressWarnings("unused")
        @Specialization
        public Class<?> doInt(int n) {
            return Integer.class;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Class<?> doDouble(double n) {
            return Double.class;
        }
    }

    public abstract static class CalleeNode extends ExecutableNode {
        protected CalleeNode() {
            super(null);
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            return execute(frame.getArguments()[0], frame.getArguments()[1]);
        }

        public abstract Object execute(Object value, Object klass);

        // One classNode per specialization instance, intended here and representative of more
        // complex nodes which also create new helper node instances for new specialization
        // instances
        @SuppressWarnings("unused")
        @Specialization(guards = "classNode.execute(value) == cachedClass", limit = "4")
        public boolean doCached(Object value, Class<?> checkedAgainst,
                        @Cached ClassNode classNode,
                        @Cached("classNode.execute(value)") Class<?> cachedClass) {
            return cachedClass == checkedAgainst;
        }
    }

    public static class CalleeRoot extends RootNode {

        @Child private CalleeNode calleeNode;
        private final CountDownLatch compilationStartLatch;
        private final CountDownLatch compilationFinishedLatch;

        protected CalleeRoot(CountDownLatch compilationStartLatch, CountDownLatch compilationFinishedLatch) {
            super(null);
            this.compilationStartLatch = compilationStartLatch;
            this.compilationFinishedLatch = compilationFinishedLatch;
            this.calleeNode = GR42688TestFactory.CalleeNodeGen.create();
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            if (CompilerDirectives.inInterpreter()) {
                if (frame.getArguments()[0] instanceof Double) {
                    compilationStartLatch.countDown();
                    try {
                        compilationFinishedLatch.await();
                    } catch (InterruptedException ie) {
                        throw new AssertionError(ie);
                    }
                }
            }
            return calleeNode.execute(frame);
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

    private static class CallerRoot extends RootNode {
        @Child private DirectCallNode callNode;
        private final String name;

        CallerRoot(CallTarget target, String name) {
            super(null);
            this.name = name;
            this.callNode = Truffle.getRuntime().createDirectCallNode(target);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.call(frame.getArguments());
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    @Test
    public void testDeoptLoopDetected() {
        Assume.assumeTrue(Truffle.getRuntime() instanceof OptimizedTruffleRuntime);
        OptimizedTruffleRuntime optimizedTruffleRuntime = (OptimizedTruffleRuntime) Truffle.getRuntime();
        AtomicReference<OptimizedCallTarget> calleeRef = new AtomicReference<>();
        AtomicReference<OptimizedCallTarget> callerRef = new AtomicReference<>();
        CountDownLatch calleeCompilationStartLatch = new CountDownLatch(1);
        CountDownLatch calleeCompilationFinishedLatch = new CountDownLatch(1);
        AtomicBoolean intCallerCompilationFailed = new AtomicBoolean();
        OptimizedTruffleRuntimeListener listener = new OptimizedTruffleRuntimeListener() {
            @Override
            public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph, TruffleCompilerListener.CompilationResultInfo result) {
                if (target == calleeRef.get()) {
                    calleeCompilationFinishedLatch.countDown();
                }
            }

            @Override
            public void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
                if (target == calleeRef.get()) {
                    try {
                        calleeCompilationStartLatch.await();
                    } catch (InterruptedException ie) {
                        throw new AssertionError(ie);
                    }
                }
            }

            @Override
            public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
                if (target == callerRef.get() && reason != null && reason.contains("Deopt taken too many times")) {
                    intCallerCompilationFailed.set(true);
                }
            }
        };
        optimizedTruffleRuntime.addListener(listener);
        try (Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option("engine.BackgroundCompilation", "true").option(
                        "engine.DynamicCompilationThresholds", "false").option("engine.MultiTier", "false").option("engine.Splitting", "false").option("engine.SingleTierCompilationThreshold",
                                        "10").option("engine.CompilationFailureAction", "Silent").build()) {
            context.enter();
            OptimizedCallTarget callee = (OptimizedCallTarget) new CalleeRoot(calleeCompilationStartLatch, calleeCompilationFinishedLatch).getCallTarget();
            OptimizedCallTarget intCaller = (OptimizedCallTarget) new CallerRoot(callee, "intCaller").getCallTarget();
            calleeRef.set(callee);
            callerRef.set(intCaller);

            final int compilationThreshold = callee.getOptionValue(SingleTierCompilationThreshold);

            for (int i = 0; i < compilationThreshold; i++) {
                callee.call(42, Integer.class);
            }
            /*
             * The loop above triggers compilation in a separate thread, but using a compilation
             * listener, the compilation is delayed until the following call reaches a certain
             * point.
             */
            callee.call(3.14, Double.class);
            Assert.assertTrue(callee.isValid());
            /*
             * The compiled callee is still valid for integer, because the compilation finished
             * before the AST respecialized for double. The respecialization for double made it
             * invalid for integer, it would need another specialization round to be valid both for
             * integer and double.
             */
            callee.call(42, Integer.class);
            Assert.assertTrue(callee.isValid());
            /*
             * The intCaller uses the compiled callee for executions in the interpreter, which is
             * valid for integer, but when intCaller is compiled, in inlines the AST for callee,
             * which is not valid for integer and calling the compiled callee results in an
             * immediate deopt, but the deopt lands outside the callee AST, which means the compiled
             * callee is used again without the AST getting a chance to respecialize. Therefore, the
             * deopts of the intCaller are repeated until the deopt cycle detection kicks in and
             * marks the intCaller call target as permanent opt fail which means no further
             * compilations of it are attempted.
             */
            for (int i = 0; i < 1000000000 && !intCallerCompilationFailed.get(); i++) {
                intCaller.call(42, Integer.class);
            }
            Assert.assertTrue(intCallerCompilationFailed.get());
        } finally {
            optimizedTruffleRuntime.removeListener(listener);
        }
    }
}
