/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class NodeSplittingStrategyTest extends AbstractSplittingStrategyTest {

    @Before
    public void boostBudget() {
        createDummyTargetsToBoostGrowingSplitLimit();
    }

    @NodeChild
    abstract static class TurnsPolymorphicOnZeroNode extends SplittingTestNode {
        @Specialization(guards = "value != 0")
        static int do1(int value) {
            return value;
        }

        @Specialization
        static int do2(int value) {
            return value;
        }

        @Fallback
        static int do3(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object value) {
            return 0;
        }
    }

    @NodeChild
    abstract static class TurnsPolymorphicOnZeroButSpecializationIsExcludedNode extends SplittingTestNode {
        @Specialization(guards = "value != 0")
        int do1(int value) {
            return value;
        }

        @ReportPolymorphism.Exclude
        @Specialization
        int do2(int value) {
            return value;
        }

        @Fallback
        int do3(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object value) {
            return 0;
        }
    }

    @NodeChild
    @ReportPolymorphism.Exclude
    abstract static class TurnsPolymorphicOnZeroButClassIsExcludedNode extends SplittingTestNode {
        @Specialization(guards = "value != 0")
        int do1(int value) {
            return value;
        }

        @Specialization
        int do2(int value) {
            return value;
        }

        @Fallback
        int do3(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object value) {
            return 0;
        }
    }

    @NodeChild
    @ReportPolymorphism
    abstract static class HasInlineCacheNode extends SplittingTestNode {

        @Specialization(limit = "2", //
                        guards = "target.getRootNode() == cachedNode")
        protected static Object doDirect(RootCallTarget target, @Cached("target.getRootNode()") @SuppressWarnings("unused") RootNode cachedNode) {
            return target.call(noArguments);
        }

        @Specialization(replaces = "doDirect")
        protected static Object doIndirect(RootCallTarget target) {
            return target.call(noArguments);
        }
    }

    static class TwoDummiesAndAnotherNode extends SplittingTestNode {
        int counter;
        RootCallTarget dummy = runtime.createCallTarget(new DummyRootNode());

        @Override
        public Object execute(VirtualFrame frame) {
            if (counter < 2) {
                counter++;
            } else {
                counter = 0;
                dummy = runtime.createCallTarget(new DummyRootNode());
            }
            return dummy;
        }
    }

    @Test
    public void testSplitsDirectCalls() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(NodeSplittingStrategyTestFactory.HasInlineCacheNodeGen.create(new ReturnsFirstArgumentNode())));
        Object[] first = new Object[]{runtime.createCallTarget(new DummyRootNode())};
        Object[] second = new Object[]{runtime.createCallTarget(new DummyRootNode())};
        testSplitsDirectCallsHelper(callTarget, first, second);

        callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode())));
        // two callers for a target are needed
        testSplitsDirectCallsHelper(callTarget, new Object[]{1}, new Object[]{0});
    }

    @Test
    public void testDoesNotSplitsDirectCalls() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroButClassIsExcludedNodeGen.create(new ReturnsFirstArgumentNode())));
        testDoesNotSplitDirectCallHelper(callTarget, new Object[]{1}, new Object[]{0});

        callTarget = (OptimizedCallTarget) runtime.createCallTarget(new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroButSpecializationIsExcludedNodeGen.create(new ReturnsFirstArgumentNode())));
        testDoesNotSplitDirectCallHelper(callTarget, new Object[]{1}, new Object[]{0});
    }

    class CallsInnerNode extends SplittableRootNode {

        private final RootCallTarget toCall;
        @Child private OptimizedDirectCallNode callNode;

        CallsInnerNode(RootCallTarget toCall) {
            this.toCall = toCall;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            /*
             * We lazily initialize the direct call node as this is the case typically for inline
             * caches in languages.
             */
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert((OptimizedDirectCallNode) runtime.createDirectCallNode(toCall));
            }
            return callNode.call(frame.getArguments());
        }
    }

    @Test
    public void testSplitPropagatesThrongSoleCallers() {
        OptimizedCallTarget turnsPolymorphic = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode())));
        testPropagatesThroughSoleCallers(turnsPolymorphic, new Object[]{1}, new Object[]{0});
        turnsPolymorphic = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(NodeSplittingStrategyTestFactory.HasInlineCacheNodeGen.create(new ReturnsFirstArgumentNode())));
        Object[] first = new Object[]{runtime.createCallTarget(new DummyRootNode())};
        Object[] second = new Object[]{runtime.createCallTarget(new DummyRootNode())};
        testPropagatesThroughSoleCallers(turnsPolymorphic, first, second);
    }

    private void testPropagatesThroughSoleCallers(OptimizedCallTarget turnsPolymorphic, Object[] firstArgs, Object[] secondArgs) {
        final OptimizedCallTarget callsInner = (OptimizedCallTarget) runtime.createCallTarget(new CallsInnerNode(turnsPolymorphic));
        final OptimizedCallTarget callsCallsInner = (OptimizedCallTarget) runtime.createCallTarget(new CallsInnerNode(callsInner));
        // two callers for a target are needed
        runtime.createDirectCallNode(callsCallsInner);
        final DirectCallNode directCallNode = runtime.createDirectCallNode(callsCallsInner);
        directCallNode.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callsCallsInner));
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callsInner));
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(turnsPolymorphic));
        directCallNode.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callsCallsInner));
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callsInner));
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(turnsPolymorphic));
        directCallNode.call(secondArgs);
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(callsCallsInner));
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(callsInner));
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(turnsPolymorphic));

        directCallNode.call(secondArgs);
        Assert.assertTrue("Target needs split but not split", directCallNode.isCallTargetCloned());

        // Test new dirrectCallNode will split
        DirectCallNode newCallNode = runtime.createDirectCallNode(callsCallsInner);
        newCallNode.call(secondArgs);
        Assert.assertTrue("new call node to \"needs split\" target is not split", newCallNode.isCallTargetCloned());

        newCallNode = runtime.createDirectCallNode(callsInner);
        newCallNode.call(secondArgs);
        Assert.assertTrue("new call node to \"needs split\" target is not split", newCallNode.isCallTargetCloned());

        newCallNode = runtime.createDirectCallNode(turnsPolymorphic);
        newCallNode.call(secondArgs);
        Assert.assertTrue("new call node to \"needs split\" target is not split", newCallNode.isCallTargetCloned());
    }

    @Test
    public void testNoSplitsDirectCallsBecauseFirstExecution() {
        final OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(new SplittableRootNode() {
            @Child private OptimizedDirectCallNode callNode = (OptimizedDirectCallNode) runtime.createDirectCallNode(runtime.createCallTarget(
                            new SplittingTestRootNode(NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode()))));

            @Override
            public Object execute(VirtualFrame frame) {
                final Object[] first = {1};
                callNode.call(first);
                callNode.call(first);
                // This call turns the node polymorphic
                final Object[] second = {0};
                callNode.call(second);
                return null;
            }
        });
        // Multiple call nodes
        runtime.createDirectCallNode(callTarget);
        runtime.createDirectCallNode(callTarget);
        final DirectCallNode directCallNode = runtime.createDirectCallNode(callTarget);

        directCallNode.call(new Object[]{0});
        Assert.assertFalse("Target needs split after first execution", getNeedsSplit(callTarget));
    }

    @Test
    public void testIncreaseInPolymorphism() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode())));
        final RootCallTarget outerTarget = runtime.createCallTarget(new CallsInnerNode(callTarget));
        Object[] firstArgs = new Object[]{1};
        outerTarget.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        outerTarget.call(firstArgs);
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callTarget));
        Object[] secondArgs = new Object[]{0};
        // Turns polymorphic
        outerTarget.call(secondArgs);
        Assert.assertFalse("Target needs split even though there is only 1 caller", getNeedsSplit(callTarget));

        // Add second caller
        final DirectCallNode directCallNode = runtime.createDirectCallNode(callTarget);
        outerTarget.call(secondArgs);
        Assert.assertFalse("Target needs split with no increase in polymorphism", getNeedsSplit(callTarget));

        outerTarget.call(new Object[]{"foo"});
        Assert.assertTrue("Target does not need split after increase in polymorphism", getNeedsSplit(callTarget));

        // Test new dirrectCallNode will split
        outerTarget.call(firstArgs);
        directCallNode.call(firstArgs);
        Assert.assertTrue("new call node to \"needs split\" target is not split", directCallNode.isCallTargetCloned());
    }

    static class ExposesReportPolymorphicSpecializeNode extends Node {
        void report() {
            reportPolymorphicSpecialize();
        }
    }

    @Test
    public void testUnadopted() {
        final ExposesReportPolymorphicSpecializeNode node = new ExposesReportPolymorphicSpecializeNode();
        node.report();
    }

    static class ExposesReportPolymorphicSpecializeRootNode extends RootNode {

        @Child ExposesReportPolymorphicSpecializeNode node = new ExposesReportPolymorphicSpecializeNode();

        protected ExposesReportPolymorphicSpecializeRootNode() {
            super(null);
        }

        void report() {
            node.report();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }
    }

    @Test
    public void testSoloTarget() {
        final ExposesReportPolymorphicSpecializeRootNode rootNode = new ExposesReportPolymorphicSpecializeRootNode();
        final RootCallTarget callTarget = runtime.createCallTarget(rootNode);
        callTarget.call(noArguments);
        rootNode.report();
    }

    static class CallableOnlyOnceRootNode extends ExposesReportPolymorphicSpecializeRootNode {
        boolean called;
        boolean active;

        @Override
        public Object execute(VirtualFrame frame) {
            if (active && called) {
                throw new AssertionError("This is illegal state. Seems a split happened but the original was called.");
            }
            called = true;
            return super.execute(frame);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }
    }

    @Test
    public void testSplitsCalledAfterSplit() {
        final CallableOnlyOnceRootNode rootNode = new CallableOnlyOnceRootNode();
        final RootCallTarget reportsPolymorphism = runtime.createCallTarget(rootNode);
        reportsPolymorphism.call(noArguments);
        final RootCallTarget callsInner1 = runtime.createCallTarget(new CallsInnerNode(reportsPolymorphism));
        final RootCallTarget callsInner2 = runtime.createCallTarget(new CallsInnerNode(reportsPolymorphism));
        // make sure the runtime has seen these calls
        callsInner1.call(noArguments);
        callsInner2.call(noArguments);
        rootNode.active = true;
        rootNode.report();
        callsInner1.call(noArguments);
        callsInner2.call(noArguments);
    }
}
