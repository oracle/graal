/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.FirstTierCompilationThreshold;
import static com.oracle.truffle.runtime.OptimizedRuntimeOptions.LastTierCompilationThreshold;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class MultiTierCompilationTest extends PartialEvaluationTest {

    public static class MultiTierCalleeNode extends RootNode {
        protected MultiTierCalleeNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (CompilerDirectives.inInterpreter()) {
                return "callee:interpreter";
            }
            boundary();
            if (CompilerDirectives.hasNextTier()) {
                return "callee:first-tier";
            }
            if (CompilerDirectives.inCompilationRoot()) {
                return "callee:second-tier-root";
            }
            return "callee:inlined";
        }
    }

    private static class MultiTierRootNode extends RootNode {
        @Child private DirectCallNode callNode;

        MultiTierRootNode(CallTarget target) {
            super(null);
            this.callNode = Truffle.getRuntime().createDirectCallNode(target);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            boundary();
            Object result = callNode.call(frame.getArguments());
            if (CompilerDirectives.inInterpreter()) {
                return "root:interpreter";
            }
            boundary();
            return result;
        }
    }

    private static class MultiTierWithFrequentCalleeRootNode extends RootNode {
        @Child private DirectCallNode callNode;
        private int frequency;

        MultiTierWithFrequentCalleeRootNode(CallTarget target, int frequency) {
            super(null);
            this.callNode = Truffle.getRuntime().createDirectCallNode(target);
            this.frequency = frequency;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (CompilerDirectives.inInterpreter()) {
                return "root:interpreter";
            }
            boundary();
            for (int i = 0; i < frequency; i++) {
                callNode.call(frame.getArguments());
            }
            return callNode.call(frame.getArguments());
        }
    }

    private static class MultiTierWithLoopRootNode extends RootNode {
        @Child private LoopNode loop;
        private final MultiTierCompilationTest.MultiTierLoopBodyNode body;
        public int firstTierCallCount;

        MultiTierWithLoopRootNode(MultiTierLoopBodyNode body) {
            super(null);
            this.loop = Truffle.getRuntime().createLoopNode(body);
            this.body = body;
            this.firstTierCallCount = 0;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            body.iteration = 0;
            if (!CompilerDirectives.inInterpreter() && CompilerDirectives.hasNextTier()) {
                this.firstTierCallCount += 1;
            }
            final Object result = loop.execute(frame);
            return result;
        }
    }

    private static final class MultiTierLoopBodyNode extends Node implements RepeatingNode {
        private final int total;
        public int iteration = 0;

        private MultiTierLoopBodyNode(int total) {
            this.total = total;
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            throw new RuntimeException("This method must not be called.");
        }

        @Override
        public Object initialLoopStatus() {
            return "continue";
        }

        @Override
        public boolean shouldContinue(Object returnValue) {
            String value = (String) returnValue;
            return value.startsWith("continue");
        }

        @Override
        public Object executeRepeatingWithValue(VirtualFrame frame) {
            iteration += 1;
            if (iteration == total) {
                if (CompilerDirectives.inInterpreter()) {
                    return "break:interpreter";
                }
                if (CompilerDirectives.hasNextTier()) {
                    return "break:first-tier";
                }
                return "break:second-tier";
            }
            if (CompilerDirectives.inInterpreter()) {
                return "continue:interpreter";
            }
            if (CompilerDirectives.hasNextTier()) {
                return "continue:first-tier";
            } else {
                return "continue:last-tier";
            }
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static void boundary() {
    }

    @Test
    public void testDefault() {
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option("engine.BackgroundCompilation", "false").option("engine.MultiTier",
                        "true").option("compiler.FirstTierInliningPolicy", "None").option("engine.Splitting", "false").option("engine.FirstTierCompilationThreshold", "100").option(
                                        "engine.LastTierCompilationThreshold", "1000").build());

        OptimizedCallTarget calleeTarget = (OptimizedCallTarget) new MultiTierCalleeNode().getCallTarget();
        OptimizedCallTarget multiTierTarget = (OptimizedCallTarget) new MultiTierRootNode(calleeTarget).getCallTarget();
        final int firstTierCompilationThreshold = calleeTarget.getOptionValue(FirstTierCompilationThreshold);
        final int compilationThreshold = calleeTarget.getOptionValue(LastTierCompilationThreshold);

        Assert.assertEquals("root:interpreter", multiTierTarget.call());
        for (int i = 0; i < firstTierCompilationThreshold; i++) {
            multiTierTarget.call();
        }
        Assert.assertEquals("callee:first-tier", multiTierTarget.call());
        for (int i = 0; i < compilationThreshold; i++) {
            multiTierTarget.call();
        }
        Assert.assertEquals("callee:inlined", multiTierTarget.call());
    }

    @Test
    public void testFirstTierInlining() {
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option("engine.BackgroundCompilation", "false").option("engine.MultiTier",
                        "true").option("compiler.FirstTierInliningPolicy", "Default").option("engine.Splitting", "false").option("engine.FirstTierCompilationThreshold", "100").option(
                                        "engine.LastTierCompilationThreshold", "1000").build());

        OptimizedCallTarget calleeTarget = (OptimizedCallTarget) new MultiTierCalleeNode().getCallTarget();
        OptimizedCallTarget multiTierTarget = (OptimizedCallTarget) new MultiTierRootNode(calleeTarget).getCallTarget();
        final int firstTierCompilationThreshold = calleeTarget.getOptionValue(FirstTierCompilationThreshold);
        final int compilationThreshold = calleeTarget.getOptionValue(LastTierCompilationThreshold);

        Assert.assertEquals("root:interpreter", multiTierTarget.call());
        for (int i = 0; i < firstTierCompilationThreshold; i++) {
            multiTierTarget.call();
        }
        Assert.assertEquals("callee:first-tier", multiTierTarget.call());
        for (int i = 0; i < compilationThreshold; i++) {
            multiTierTarget.call();
        }
        Assert.assertEquals("callee:inlined", multiTierTarget.call());
    }

    @Test
    public void testWhenCalleeCompiledFirst() {
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option("engine.BackgroundCompilation", "false").option("engine.MultiTier",
                        "true").option("compiler.FirstTierInliningPolicy", "None").option("engine.Splitting", "false").option("engine.FirstTierCompilationThreshold", "100").option(
                                        "engine.LastTierCompilationThreshold", "1000").build());

        OptimizedCallTarget calleeTarget = (OptimizedCallTarget) new MultiTierCalleeNode().getCallTarget();
        final int firstTierCompilationThreshold = calleeTarget.getOptionValue(FirstTierCompilationThreshold);
        final int compilationThreshold = calleeTarget.getOptionValue(LastTierCompilationThreshold);
        OptimizedCallTarget multiTierTarget = (OptimizedCallTarget) new MultiTierWithFrequentCalleeRootNode(calleeTarget, firstTierCompilationThreshold).getCallTarget();

        Assert.assertEquals("root:interpreter", multiTierTarget.call());
        for (int i = 0; i < firstTierCompilationThreshold - 2; i++) {
            // Callee starts getting called only in the last iteration of this loop, after the root
            // is first-tier compiled.
            // The last root-node call triggers the callee's first-tier compilation.
            // multiTierTarget.call();
            Assert.assertEquals("iteration: " + i, "root:interpreter", multiTierTarget.call());
        }
        Assert.assertEquals("callee:first-tier", multiTierTarget.call());
        Assert.assertEquals(10, compilationThreshold / firstTierCompilationThreshold);
        // The following loop will trigger the callee's last-tier compilation.
        for (int i = 0; i < compilationThreshold / firstTierCompilationThreshold; i++) {
            multiTierTarget.call();
        }
        Assert.assertEquals("callee:second-tier-root", multiTierTarget.call());
        // The following loop will trigger the root's last-tier compilation.
        for (int i = 0; i < compilationThreshold; i++) {
            multiTierTarget.call();
        }
        Assert.assertEquals("callee:inlined", multiTierTarget.call());
    }

    @Test
    public void testLoop() {
        int firstThreshold = 100;
        int secondThreshold = 1000;
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option("engine.BackgroundCompilation", "false").option("engine.MultiTier",
                        "true").option("compiler.FirstTierInliningPolicy", "None").option("engine.Splitting", "false").option("engine.FirstTierCompilationThreshold",
                                        String.valueOf(firstThreshold)).option("engine.LastTierCompilationThreshold", String.valueOf(secondThreshold)).build());

        MultiTierLoopBodyNode body = new MultiTierLoopBodyNode(firstThreshold);
        final MultiTierWithLoopRootNode rootNode = new MultiTierWithLoopRootNode(body);
        OptimizedCallTarget rootTarget = (OptimizedCallTarget) rootNode.getCallTarget();

        Assert.assertEquals("break:interpreter", rootTarget.call());
        Assert.assertEquals("break:first-tier", rootTarget.call());
        for (int i = 0; i < secondThreshold / firstThreshold - 2; i++) {
            Assert.assertEquals("at iteration " + i, "break:first-tier", rootTarget.call());
        }
        rootTarget.call();
        Assert.assertEquals("break:second-tier", rootTarget.call());
        Assert.assertEquals(10, rootNode.firstTierCallCount);
    }

}
