/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark;

import org.graalvm.polyglot.Context;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@Warmup(iterations = 10, time = 1)
public class FirstTierBenchmark extends TruffleBenchmark {

    public static final String TEST_LANGUAGE = "bm";
    static final int ITERATIONS = 5_000_000;
    static final int SETUP_ITERATIONS = 10_000;
    static final String LOW_COMPILATION_THRESHOLD = "5";
    static final String HIGH_LAST_TIER_COMPILATION_THRESHOLD = Integer.toString((ITERATIONS + SETUP_ITERATIONS) * 3);

    public abstract static class BaseSetup {

        final Context context;
        RootNode root;
        CallTarget target;

        public BaseSetup(boolean multiTier) {
            Context.Builder b = Context.newBuilder(TEST_LANGUAGE);
            b.allowExperimentalOptions(true);
            b.option("engine.BackgroundCompilation", "false");
            b.option("engine.DynamicCompilationThresholds", "false");
            b.option("engine.MultiTier", Boolean.toString(multiTier));
            if (multiTier) {
                b.option("engine.FirstTierCompilationThreshold", LOW_COMPILATION_THRESHOLD);
                b.option("engine.LastTierCompilationThreshold", HIGH_LAST_TIER_COMPILATION_THRESHOLD);
            } else {
                b.option("engine.SingleTierCompilationThreshold", LOW_COMPILATION_THRESHOLD);
            }
            context = b.build();
        }

        @Setup(Level.Invocation)
        public void setup() {
            context.enter(); // binds a valid polyglot engine
            try {
                root = createRootNode();
                target = root.getCallTarget();
                for (int i = 0; i < SETUP_ITERATIONS; i++) {
                    target.call((Node) null);
                }
            } finally {
                context.leave();
            }
        }

        @TearDown
        public void tearDown() {
            context.close();
        }

        protected abstract RootNode createRootNode();
    }

    @State(Scope.Benchmark)
    public static class CallFirstTierSetup extends BaseSetup {

        public CallFirstTierSetup() {
            super(true);
        }

        @Override
        protected RootNode createRootNode() {
            return new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return 42;
                }
            };
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void callFirstTier(CallFirstTierSetup state, Blackhole blackhole) {
        CallTarget target = state.target;
        for (int i = 0; i < ITERATIONS; i++) {
            blackhole.consume(target.call((Node) null));
        }
    }

    @State(Scope.Benchmark)
    public static class CallLastTierSetup extends BaseSetup {

        public CallLastTierSetup() {
            super(false);
        }

        @Override
        protected RootNode createRootNode() {
            return new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return 42;
                }
            };
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void callLastTier(CallLastTierSetup state, Blackhole blackhole) {
        CallTarget target = state.target;
        for (int i = 0; i < ITERATIONS; i++) {
            blackhole.consume(target.call((Node) null));
        }
    }

    @State(Scope.Benchmark)
    public static class BackEdgeFirstTierSetup extends BaseSetup {

        public BackEdgeFirstTierSetup() {
            super(true);
        }

        @Override
        protected RootNode createRootNode() {
            return new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    LoopNode.reportLoopCount(this, 1);
                    return 42;
                }
            };
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void backEdgeFirstTier(BackEdgeFirstTierSetup state, Blackhole blackhole) {
        CallTarget target = state.target;
        for (int i = 0; i < ITERATIONS; i++) {
            blackhole.consume(target.call((Node) null));
        }
    }

    @State(Scope.Benchmark)
    public static class BackEdgeLastTierSetup extends BaseSetup {

        public BackEdgeLastTierSetup() {
            super(false);
        }

        @Override
        protected RootNode createRootNode() {
            return new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    LoopNode.reportLoopCount(this, 1);
                    return 42;
                }
            };
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITERATIONS)
    public void backEdgeLastTier(BackEdgeLastTierSetup state, Blackhole blackhole) {
        CallTarget target = state.target;
        for (int i = 0; i < ITERATIONS; i++) {
            blackhole.consume(target.call((Node) null));
        }
    }

}
