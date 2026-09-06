/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.truffle.PartialEvaluator;
import jdk.graal.compiler.truffle.PostPartialEvaluationSuite;
import jdk.graal.compiler.truffle.TruffleCompilation;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.graal.compiler.truffle.TruffleTierContext;
import jdk.graal.compiler.truffle.phases.inlining.AgnosticInliningPhase;
import jdk.graal.compiler.truffle.phases.inlining.CallNode;
import jdk.graal.compiler.truffle.phases.inlining.CallTree;
import jdk.graal.compiler.truffle.phases.inlining.InliningPolicy;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedDirectCallNode;

public class AgnosticInliningPhaseTest extends PartialEvaluationTest {

    private static final double DELTA = 0.000001D;

    protected final TruffleRuntime runtime = Truffle.getRuntime();

    @Before
    public void before() {
        setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("compiler.InliningPolicy", "Agnostic").option(
                        "compiler.InliningInliningBudget", "1").build());
    }

    protected StructuredGraph runLanguageAgnosticInliningPhase(OptimizedCallTarget callTarget) {
        TruffleCompilerImpl compiler = getTruffleCompiler(callTarget);
        final PartialEvaluator partialEvaluator = compiler.getPartialEvaluator();
        final CompilationIdentifier compilationIdentifier = new CompilationIdentifier() {
            @Override
            public String toString(Verbosity verbosity) {
                return "";
            }
        };
        final TruffleTierContext context = TruffleTierContext.createInitialContext(
                        partialEvaluator,
                        compiler.getOrCreateCompilerOptions(callTarget),
                        getDebugContext(), callTarget,
                        compilationIdentifier, getSpeculationLog(),
                        new TruffleCompilationTask() {

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }

                            @Override
                            public boolean isLastTier() {
                                return true;
                            }

                            @Override
                            public boolean hasNextTier() {
                                return false;
                            }
                        }, null);
        final AgnosticInliningPhase agnosticInliningPhase = new AgnosticInliningPhase(
                        new PostPartialEvaluationSuite(compiler.getOrCreateCompilerOptions(callTarget), false));
        agnosticInliningPhase.apply(context.graph, context);
        return context.graph;
    }

    @Test
    public void testInitialAndCumulativeFrequency() {
        OptimizedCallTarget leaf = createDummyNode();
        OptimizedCallTarget middle = callTarget(new BranchCallRootNode(leaf, 0.25D));
        CallTree tree = createCallTree(callTarget(new BranchCallRootNode(middle, 0.2D)), true);

        CallNode middleCall = onlyChild(tree.getRoot());
        Assert.assertEquals(0.2D, middleCall.getRootRelativeFrequency(), DELTA);

        middleCall.expand();
        CallNode leafCall = onlyChild(middleCall);
        Assert.assertEquals(0.05D, leafCall.getRootRelativeFrequency(), DELTA);
    }

    @Test
    public void testInitialFrequencyIsClamped() {
        OptimizedCallTarget leaf = createDummyNode();
        OptimizedCallTarget coldMiddle = callTarget(new BranchCallRootNode(leaf, 0.001D));
        CallTree coldTree = createCallTree(callTarget(new BranchCallRootNode(coldMiddle, 0.001D)), true);
        CallTree hotTree = createCallTree(callTarget(new LoopCallRootNode(leaf)), 1);

        CallNode coldMiddleCall = onlyChild(coldTree.getRoot());
        Assert.assertEquals(0.01D, coldMiddleCall.getRootRelativeFrequency(), 0D);
        coldMiddleCall.expand();
        Assert.assertEquals(0.0001D, onlyChild(coldMiddleCall).getRootRelativeFrequency(), DELTA);
        Assert.assertEquals(100D, onlyChild(hotTree.getRoot()).getRootRelativeFrequency(), DELTA);
    }

    @Test
    public void testColdCallInHotLoopIsNotAdjusted() {
        OptimizedCallTarget leaf = createDummyNode();
        CallTree tree = createCallTree(callTarget(new ColdLoopCallRootNode(leaf)), 1, true);

        Assert.assertEquals(0.999D, onlyChild(tree.getRoot()).getRootRelativeFrequency(), DELTA);
    }

    @Test
    public void testRootFrequencyUpdateThroughInlinedNode() {
        OptimizedCallTarget leaf = createDummyNode();
        OptimizedCallTarget nested = callTarget(new BranchCallRootNode(leaf, 0.25D));
        OptimizedCallTarget intermediate = callTarget(new DirectCallRootNode(nested));
        CallTree tree = createCallTree(callTarget(new BranchCallRootNode(intermediate, 0.2D)), true);

        CallNode intermediateCall = onlyChild(tree.getRoot());
        intermediateCall.expand();
        CallNode nestedCall = onlyChild(intermediateCall);
        nestedCall.expand();
        CallNode leafCall = onlyChild(nestedCall);
        Assert.assertEquals(0.2D, nestedCall.getRootRelativeFrequency(), DELTA);
        Assert.assertEquals(0.05D, leafCall.getRootRelativeFrequency(), DELTA);

        intermediateCall.inline();
        Assert.assertSame(tree.getRoot().getIR(), nestedCall.getInvoke().asNode().graph());
        boolean callOnTrueBranch = isCallOnTrueBranch(tree, nestedCall);
        setRootBranchProbability(tree, callOnTrueBranch, 0.8D);

        Assert.assertTrue(tree.updateRootFrequencies());
        Assert.assertEquals(0.8D, nestedCall.getRootRelativeFrequency(), DELTA);
        Assert.assertEquals(0.2D, leafCall.getRootRelativeFrequency(), DELTA);

        // Changes below the 10% update threshold are ignored.
        setRootBranchProbability(tree, callOnTrueBranch, 0.84D);
        Assert.assertFalse(tree.updateRootFrequencies());
        Assert.assertEquals(0.8D, nestedCall.getRootRelativeFrequency(), DELTA);
        Assert.assertEquals(0.2D, leafCall.getRootRelativeFrequency(), DELTA);
    }

    private CallTree createCallTree(OptimizedCallTarget target, Object... arguments) {
        target.call(arguments);
        target.call(arguments);
        target.call(arguments);
        TruffleCompilerImpl compiler = getTruffleCompiler(target);
        TruffleCompilationTask task = newTask();
        try (TruffleCompilation compilation = compiler.openCompilation(task, target)) {
            TruffleTierContext context = TruffleTierContext.createInitialContext(
                            compiler.getPartialEvaluator(),
                            compiler.getOrCreateCompilerOptions(target),
                            getDebugContext(), target,
                            compilation.getCompilationId(), getSpeculationLog(),
                            task, null);
            return ReflectionUtils.newInstance(CallTree.class, new Class<?>[]{PostPartialEvaluationSuite.class, TruffleTierContext.class, InliningPolicy.class},
                            new PostPartialEvaluationSuite(compiler.getOrCreateCompilerOptions(target), false), context, new InliningPolicy() {
                            });
        }
    }

    private static CallNode onlyChild(CallNode parent) {
        Assert.assertEquals(1, parent.getChildren().size());
        return parent.getChildren().get(0);
    }

    private static OptimizedCallTarget callTarget(RootNode root) {
        return (OptimizedCallTarget) root.getCallTarget();
    }

    private static boolean isCallOnTrueBranch(CallTree tree, CallNode call) {
        IfNode ifNode = tree.getRoot().getIR().getNodes(IfNode.TYPE).first();
        double currentFrequency = call.getRootRelativeFrequency();
        if (Math.abs(ifNode.probability(ifNode.trueSuccessor()) - currentFrequency) < DELTA) {
            return true;
        }
        Assert.assertEquals(currentFrequency, ifNode.probability(ifNode.falseSuccessor()), DELTA);
        return false;
    }

    private static void setRootBranchProbability(CallTree tree, boolean callOnTrueBranch, double probability) {
        IfNode ifNode = tree.getRoot().getIR().getNodes(IfNode.TYPE).first();
        double trueProbability = callOnTrueBranch ? probability : 1.0D - probability;
        Assert.assertTrue(ifNode.setProbability(ifNode.trueSuccessor(), BranchProbabilityData.injected(trueProbability)));
        tree.getRoot().getIR().clearLastCFG();
    }

    protected static final OptimizedCallTarget createDummyNode() {
        return (OptimizedCallTarget) new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        }.getCallTarget();
    }

    private static final class BranchCallRootNode extends RootNode {

        @Child private OptimizedDirectCallNode callNode;
        private final double probability;

        BranchCallRootNode(RootCallTarget target, double probability) {
            super(null);
            this.callNode = (OptimizedDirectCallNode) Truffle.getRuntime().createDirectCallNode(target);
            this.probability = probability;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (CompilerDirectives.injectBranchProbability(probability, (boolean) frame.getArguments()[0])) {
                return callNode.call(frame.getArguments());
            }
            return null;
        }
    }

    private static final class LoopCallRootNode extends RootNode {

        @Child private OptimizedDirectCallNode callNode;

        LoopCallRootNode(RootCallTarget target) {
            super(null);
            this.callNode = (OptimizedDirectCallNode) Truffle.getRuntime().createDirectCallNode(target);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object result = null;
            for (int i = 0; GraalDirectives.injectIterationCount(1000D, i < (int) frame.getArguments()[0]); i++) {
                result = callNode.call(frame.getArguments());
            }
            return result;
        }
    }

    private static final class ColdLoopCallRootNode extends RootNode {

        @Child private OptimizedDirectCallNode callNode;

        ColdLoopCallRootNode(RootCallTarget target) {
            super(null);
            this.callNode = (OptimizedDirectCallNode) Truffle.getRuntime().createDirectCallNode(target);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object result = null;
            for (int i = 0; GraalDirectives.injectIterationCount(1000D, i < (int) frame.getArguments()[0]); i++) {
                if (CompilerDirectives.injectBranchProbability(0.001D, (boolean) frame.getArguments()[1])) {
                    result = callNode.call(frame.getArguments());
                }
            }
            return result;
        }
    }

    private static final class DirectCallRootNode extends RootNode {

        @Child private OptimizedDirectCallNode callNode;

        DirectCallRootNode(RootCallTarget target) {
            super(null);
            this.callNode = (OptimizedDirectCallNode) Truffle.getRuntime().createDirectCallNode(target);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.call(frame.getArguments());
        }
    }

    protected class CallsInnerNodeTwice extends RootNode {

        @Child private OptimizedDirectCallNode callNode1;
        @Child private OptimizedDirectCallNode callNode2;

        public CallsInnerNodeTwice(RootCallTarget toCall) {
            super(null);
            this.callNode1 = (OptimizedDirectCallNode) runtime.createDirectCallNode(toCall);
            this.callNode2 = (OptimizedDirectCallNode) runtime.createDirectCallNode(toCall);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            callNode1.call(frame.getArguments());
            return callNode2.call(12345);
        }
    }
}
