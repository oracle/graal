/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.stream.Stream;

import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

public class LoopNodePartialEvaluationTest extends PartialEvaluationTest {

    private static class TestLoopRootNode extends RootNode {
        @Child private LoopNode loop;
        final RepeatNTimesNode body;

        TestLoopRootNode(RepeatNTimesNode body) {
            super(null);
            this.loop = Truffle.getRuntime().createLoopNode(body);
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            assert body.iteration == 0;
            loop.execute(frame);
            assert body.iteration == 0;
            return body.total;
        }
    }

    private static final class RepeatNTimesNode extends Node implements RepeatingNode {
        private final int total;
        int iteration = 0;

        private RepeatNTimesNode(int total) {
            this.total = total;
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            throw CompilerDirectives.shouldNotReachHere("This method must not be called.");
        }

        @Override
        public Object executeRepeatingWithValue(VirtualFrame frame) {
            if (iteration < total) {
                iteration++;
                return CONTINUE_LOOP_STATUS;
            } else {
                iteration = 0;
                return BREAK_LOOP_STATUS;
            }
        }
    }

    @Test
    public void testLoopConditionProfile() {
        // Must not compile immediately, the profile is not initialized until the first execution.
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option("engine.BackgroundCompilation", "false").build());

        TestLoopRootNode repeatRootNode = new TestLoopRootNode(new RepeatNTimesNode(9));
        OptimizedCallTarget target = (OptimizedCallTarget) repeatRootNode.getCallTarget();
        StructuredGraph graph = partialEval(target, new Object[0]);

        Stream<IfNode> ifWithInjectedProfile = graph.getNodes().filter(IfNode.class).stream().filter(i -> i.getProfileData().getProfileSource() == ProfileSource.INJECTED);
        IfNode ifNode = ifWithInjectedProfile.findFirst().orElseThrow(() -> new AssertionError("If with injected branch probability not found"));
        Assert.assertEquals("Expected true successor probability", 0.9, ifNode.getTrueSuccessorProbability(), 0.01);

        List<LoopBeginNode> loopBegins = graph.getNodes().filter(LoopBeginNode.class).snapshot();
        Assert.assertEquals(loopBegins.toString(), 1, loopBegins.size());
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, false, false, false);
        for (LoopBeginNode loopBegin : loopBegins) {
            Assert.assertEquals("Expected loop frequency", 10.0, cfg.localLoopFrequency(loopBegin), 0.01);
        }
    }

    @Test
    public void testPrepareLoopForAOT() {
        preventProfileCalls = true;

        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "true").option("engine.BackgroundCompilation", "false").build());

        TestLoopRootNode repeatRootNode = new TestLoopRootNode(new RepeatNTimesNode(9));
        OptimizedCallTarget target = (OptimizedCallTarget) repeatRootNode.getCallTarget();
        target.prepareForAOT();

        StructuredGraph graph = partialEval(target, new Object[0]);

        Stream<IfNode> ifWithInjectedProfile = graph.getNodes().filter(IfNode.class).stream().filter(i -> i.getProfileData().getProfileSource() == ProfileSource.INJECTED);
        IfNode ifNode = ifWithInjectedProfile.findFirst().orElseThrow(() -> new AssertionError("If with injected branch probability not found"));
        Assert.assertEquals("Expected true successor probability", 0.5, ifNode.getTrueSuccessorProbability(), 0.01);

        List<LoopBeginNode> loopBegins = graph.getNodes().filter(LoopBeginNode.class).snapshot();
        Assert.assertEquals(loopBegins.toString(), 1, loopBegins.size());
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, false, false, false);
        for (LoopBeginNode loopBegin : loopBegins) {
            Assert.assertEquals("Expected loop frequency", 2.0, cfg.localLoopFrequency(loopBegin), 0.01);
        }

        target.compile(true);
        assertTrue(target.isValidLastTier());
        target.call();
        assertTrue(target.isValidLastTier());
    }

    private static class TestPropagateInjectedProfileRootNode extends RootNode {
        @Child private LoopNode loop;
        final TestRepeatingNode body;

        TestPropagateInjectedProfileRootNode(TestRepeatingNode body) {
            super(null);
            this.loop = Truffle.getRuntime().createLoopNode(body);
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            body.reset();
            loop.execute(frame);
            body.reset();
            return body.total;
        }
    }

    private static final class TestRepeatingNode extends Node implements RepeatingNode {
        final int total;
        final boolean twoEnds;
        final boolean trueContinue;

        int iteration = 0;
        int[] hole = new int[4];

        private TestRepeatingNode(int total, boolean twoEnds, boolean trueContinue) {
            this.total = total;
            this.twoEnds = twoEnds;
            this.trueContinue = trueContinue;
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            if (executeCondition(frame)) {
                executeBody(frame);
                return true;
            }
            return false;
        }

        private boolean executeCondition(@SuppressWarnings("unused") VirtualFrame frame) {
            // actual loop condition
            if (trueContinue) {
                return iteration < total;
            } else {
                return iteration > 0;
            }
        }

        private void advance() {
            if (trueContinue) {
                iteration++;
            } else {
                iteration--;
            }
        }

        void reset() {
            if (trueContinue) {
                iteration = 0;
            } else {
                iteration = total;
            }
        }

        private void executeBody(@SuppressWarnings("unused") VirtualFrame frame) {
            if (!twoEnds) {
                advance();
            }
            // just some code with branches
            double rand = random();
            int[] h = hole;
            if (rand < 0.75) {
                if (rand < 0.25) {
                    h[0]++;
                }
                if (rand < 0.5) {
                    h[1]++;
                } else {
                    h[2]++;
                }
            } else {
                h[3]++;
            }
            if (twoEnds) {
                // produces another merge with exactly two ends
                advance();
            }
        }

        @TruffleBoundary
        private static double random() {
            return Math.random();
        }
    }

    @Test
    public void testPropagateInjectedProfileTwoEnds() {
        testPropagateInjectedProfile(true, true);
        testPropagateInjectedProfile(true, false);
    }

    @Test
    public void testPropagateInjectedProfileMoreEnds() {
        testPropagateInjectedProfile(false, true);
        testPropagateInjectedProfile(false, false);
    }

    public void testPropagateInjectedProfile(boolean twoEnds, boolean trueContinue) {
        // Must not compile immediately, the profile is not initialized until the first execution.
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option("engine.BackgroundCompilation", "false").build());

        RootNode rootNode = new TestPropagateInjectedProfileRootNode(new TestRepeatingNode(9, twoEnds, trueContinue));
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        StructuredGraph graph = partialEval(target, new Object[0]);

        Stream<IfNode> ifWithInjectedProfile = graph.getNodes().filter(IfNode.class).stream().filter(i -> i.getProfileData().getProfileSource() == ProfileSource.INJECTED);
        IfNode ifNode = ifWithInjectedProfile.findFirst().orElseThrow(() -> new AssertionError("If with injected branch probability not found"));
        double expectedProbability = trueContinue ? 0.9 : 0.1;
        Assert.assertEquals("Expected true successor probability", expectedProbability, ifNode.getTrueSuccessorProbability(), 0.01);

        List<LoopBeginNode> loopBegins = graph.getNodes().filter(LoopBeginNode.class).snapshot();
        Assert.assertEquals(loopBegins.toString(), 1, loopBegins.size());
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, false, false, false);
        for (LoopBeginNode loopBegin : loopBegins) {
            Assert.assertEquals("Expected loop frequency", 10.0, cfg.localLoopFrequency(loopBegin), 0.01);
        }
    }

}
