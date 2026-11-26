/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeVersion;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instruction.Argument;
import com.oracle.truffle.api.bytecode.Instruction.Argument.Kind;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.DebugBytecodeRootNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.ExpectWarning;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedDirectCallNode;
import com.oracle.truffle.runtime.OptimizedRuntimeOptions;

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
        RootCallTarget dummy = new DummyRootNode().getCallTarget();

        @Override
        public Object execute(VirtualFrame frame) {
            if (counter < 2) {
                counter++;
            } else {
                counter = 0;
                dummy = new DummyRootNode().getCallTarget();
            }
            return dummy;
        }
    }

    @Test
    public void testSplitsDirectCalls() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) new SplittingTestRootNode(NodeSplittingStrategyTestFactory.HasInlineCacheNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        Object[] first = new Object[]{new DummyRootNode().getCallTarget()};
        Object[] second = new Object[]{new DummyRootNode().getCallTarget()};
        testSplitsDirectCallsHelper(callTarget, first, second);

        callTarget = (OptimizedCallTarget) new SplittingTestRootNode(NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        // two callers for a target are needed
        testSplitsDirectCallsHelper(callTarget, new Object[]{1}, new Object[]{0});
    }

    @Test
    public void testDirectCallOverloadsSplitDuringExecution() {
        OptimizedCallTarget callee = (OptimizedCallTarget) new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        DirectCallNode caller = runtime.createDirectCallNode(callee);
        DirectCallNode bytecodeIndexCaller = runtime.createDirectCallNode(callee);
        DirectCallNode rootTypeCaller = runtime.createDirectCallNode(callee);
        Object[] arguments = new Object[]{1};
        callee.call(arguments);
        callee.call(0);
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(callee));

        Assert.assertEquals(1, caller.call(arguments));
        Assert.assertTrue("Legacy call did not split during execution", caller.isCallTargetCloned());
        Assert.assertEquals(1, bytecodeIndexCaller.call(42, arguments));
        Assert.assertTrue("Bytecode-index call did not split during execution", bytecodeIndexCaller.isCallTargetCloned());
        Assert.assertEquals(1, rootTypeCaller.call(SplittingTestRootNode.class, 42, arguments));
        Assert.assertTrue("Root-type call did not split during execution", rootTypeCaller.isCallTargetCloned());
        Assert.assertEquals(3, listener.splitCount);
    }

    @Test
    public void testSplitsDirectCallsCachedByBytecodeDslOperations() {
        Context.getCurrent().initialize(BytecodeDSLTestLanguage.ID);
        OptimizedCallTarget callee = (OptimizedCallTarget) new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        SplittingBytecodeRootNode caller = createBytecodeCaller(callee);

        caller.getCallTarget().call(1);
        List<DirectCallNode> callNodes = findBytecodeDirectCallNodes(caller);
        Assert.assertEquals(2, callNodes.size());
        for (DirectCallNode callNode : callNodes) {
            Assert.assertTrue(callNode instanceof OptimizedDirectCallNode);
        }
        Assert.assertFalse("Target needs split before the node went polymorphic", getNeedsSplit(callee));

        callee.call(0);
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(callee));
        for (DirectCallNode callNode : callNodes) {
            Assert.assertFalse("Target split before the next direct call", callNode.isCallTargetCloned());
        }

        OptimizedCallTarget callerTarget = (OptimizedCallTarget) caller.getCallTarget();
        Assert.assertTrue(callerTarget.prepareForCompilation(false, 1, false));
        Assert.assertTrue(callerTarget.prepareForCompilation(true, 1, false));
        for (DirectCallNode callNode : callNodes) {
            Assert.assertFalse("Compilation preparation split the call target", callNode.isCallTargetCloned());
        }
        callerTarget.call(1);
        for (DirectCallNode callNode : callNodes) {
            Assert.assertTrue("Bytecode DSL cached call target was not split during execution", callNode.isCallTargetCloned());
        }
    }

    private static SplittingBytecodeRootNode createBytecodeCaller(RootCallTarget callee) {
        BytecodeRootNodes<SplittingBytecodeRootNode> nodes = SplittingBytecodeRootNodeGen.create(BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginBlock();
            b.beginCall();
            b.emitLoadConstant(callee);
            b.emitLoadArgument(0);
            b.endCall();
            b.beginReturn();
            b.beginCall();
            b.emitLoadConstant(callee);
            b.emitLoadArgument(0);
            b.endCall();
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });
        SplittingBytecodeRootNode root = nodes.getNode(0);
        root.getBytecodeNode().setUncachedThreshold(0);
        return root;
    }

    private static List<DirectCallNode> findBytecodeDirectCallNodes(SplittingBytecodeRootNode root) {
        List<DirectCallNode> callNodes = new ArrayList<>();
        for (Instruction instruction : root.getBytecodeNode().getInstructions()) {
            for (Argument argument : instruction.getArguments()) {
                if (argument.getKind() == Kind.NODE_PROFILE) {
                    callNodes.addAll(NodeUtil.findAllNodeInstances(argument.asCachedNode(), DirectCallNode.class));
                }
            }
        }
        return callNodes;
    }

    @GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class)
    abstract static class SplittingBytecodeRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected SplittingBytecodeRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Call {

            @Specialization
            static Object doCall(RootCallTarget target, Object argument, @Cached("create(target)") DirectCallNode callNode) {
                return callNode.call(argument);
            }

            static DirectCallNode create(RootCallTarget target) {
                return DirectCallNode.create(target);
            }
        }
    }

    @Test
    public void testPreparationDoesNotSplitDirectCalls() {
        OptimizedCallTarget callee = (OptimizedCallTarget) new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        runtime.createDirectCallNode(callee);
        CallsInnerNode caller = new CallsInnerNode(callee);
        OptimizedCallTarget callerTarget = (OptimizedCallTarget) caller.getCallTarget();
        Assert.assertFalse(callerTarget.prepareForCompilation(true, 1, false));
        callerTarget.call(1);
        callerTarget.call(1);
        callerTarget.call(0);
        Assert.assertTrue(getNeedsSplit(callee));
        Assert.assertFalse(caller.callNode.isCallTargetCloned());

        Assert.assertTrue(callerTarget.prepareForCompilation(false, 1, false));
        Assert.assertFalse("Inline preparation split the call target", caller.callNode.isCallTargetCloned());
        Assert.assertTrue(callerTarget.prepareForCompilation(true, 1, false));
        Assert.assertFalse("Root preparation split the call target", caller.callNode.isCallTargetCloned());

        callerTarget.call(1);
        Assert.assertTrue("The next interpreter call did not split", caller.callNode.isCallTargetCloned());
    }

    @Test
    public void testDoesNotSplitsDirectCalls() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroButClassIsExcludedNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        testDoesNotSplitDirectCallHelper(callTarget, new Object[]{1}, new Object[]{0});

        callTarget = (OptimizedCallTarget) new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroButSpecializationIsExcludedNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
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
    public void testSplitPropagatesThroughSoleCallers() {
        OptimizedCallTarget turnsPolymorphic = (OptimizedCallTarget) new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        testPropagatesThroughSoleCallers(turnsPolymorphic, new Object[]{1}, new Object[]{0});
        turnsPolymorphic = (OptimizedCallTarget) new SplittingTestRootNode(NodeSplittingStrategyTestFactory.HasInlineCacheNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        Object[] first = new Object[]{new DummyRootNode().getCallTarget()};
        Object[] second = new Object[]{new DummyRootNode().getCallTarget()};
        testPropagatesThroughSoleCallers(turnsPolymorphic, first, second);
    }

    private void testPropagatesThroughSoleCallers(OptimizedCallTarget turnsPolymorphic, Object[] firstArgs, Object[] secondArgs) {
        final OptimizedCallTarget callsInner = (OptimizedCallTarget) new CallsInnerNode(turnsPolymorphic).getCallTarget();
        final OptimizedCallTarget callsCallsInner = (OptimizedCallTarget) new CallsInnerNode(callsInner).getCallTarget();
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
        CallsInnerNode clonedOuter = (CallsInnerNode) ((RootCallTarget) directCallNode.getCurrentCallTarget()).getRootNode();
        Assert.assertTrue("Call in the outer clone did not split", clonedOuter.callNode.isCallTargetCloned());
        CallsInnerNode clonedInner = (CallsInnerNode) clonedOuter.callNode.getCurrentCallTarget().getRootNode();
        Assert.assertTrue("Call in the inner clone did not split", clonedInner.callNode.isCallTargetCloned());

        // Test new directCallNode will split
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
        final OptimizedCallTarget callTarget = (OptimizedCallTarget) new SplittableRootNode() {
            @Child private OptimizedDirectCallNode callNode = (OptimizedDirectCallNode) runtime.createDirectCallNode(
                            new SplittingTestRootNode(NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget());

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
        }.getCallTarget();
        // Multiple call nodes
        runtime.createDirectCallNode(callTarget);
        runtime.createDirectCallNode(callTarget);
        final DirectCallNode directCallNode = runtime.createDirectCallNode(callTarget);

        directCallNode.call(new Object[]{0});
        Assert.assertFalse("Target needs split after first execution", getNeedsSplit(callTarget));
    }

    @Test
    public void testIncreaseInPolymorphism() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        final RootCallTarget outerTarget = new CallsInnerNode(callTarget).getCallTarget();
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

        // A new call node to a marked target also splits.
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
        rootNode.getCallTarget().call(noArguments);
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
        final RootCallTarget reportsPolymorphism = rootNode.getCallTarget();
        reportsPolymorphism.call(noArguments);
        final RootCallTarget callsInner1 = new CallsInnerNode(reportsPolymorphism).getCallTarget();
        final RootCallTarget callsInner2 = new CallsInnerNode(reportsPolymorphism).getCallTarget();
        // make sure the runtime has seen these calls
        callsInner1.call(noArguments);
        callsInner2.call(noArguments);
        rootNode.active = true;
        rootNode.report();
        callsInner1.call(noArguments);
        callsInner2.call(noArguments);
    }

    @NodeChild
    @ReportPolymorphism.Exclude
    abstract static class Megamorpic extends SplittingTestNode {

        @Specialization(limit = "2", guards = "val == cachedVal")
        protected static Object doCached(int val, @Cached("val") int cachedVal) {
            return val + cachedVal;
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "doCached")
        protected static Object doIndirect(int val) {
            return val + val;
        }
    }

    @Test
    public void testMegamorpic() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) new SplittingTestRootNode(NodeSplittingStrategyTestFactory.MegamorpicNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        int[] args = {1, 2, 3, 4, 5};
        testMegamorphicHelper(callTarget, args);
    }

    private static void testMegamorphicHelper(CallTarget callTarget, int[] args) {
        final DirectCallNode callNode1 = runtime.createDirectCallNode(callTarget);
        final DirectCallNode callNode2 = runtime.createDirectCallNode(callTarget);
        // Goes monomorphic
        callNode1.call(args[0]);
        Assert.assertFalse(callNode1.isCallTargetCloned());
        Assert.assertFalse(callNode2.isCallTargetCloned());
        // Goes polymorphic
        callNode2.call(args[1]);
        Assert.assertFalse(callNode1.isCallTargetCloned());
        Assert.assertFalse(callNode2.isCallTargetCloned());
        // Goes megamorphic
        callNode1.call(args[2]);
        Assert.assertFalse(callNode1.isCallTargetCloned());
        Assert.assertFalse(callNode2.isCallTargetCloned());
        // Gets split
        callNode2.call(args[3]);
        Assert.assertFalse(callNode1.isCallTargetCloned());
        Assert.assertTrue(callNode2.isCallTargetCloned());
        // Gets split
        callNode1.call(args[4]);
        Assert.assertTrue(callNode1.isCallTargetCloned());
    }

    @NodeChild
    @ReportPolymorphism.Exclude
    abstract static class TwoMegamorpicSpec extends SplittingTestNode {

        @Specialization(limit = "2", guards = "val == cachedVal")
        protected static Object doCached(int val, @Cached("val") int cachedVal) {
            return val + cachedVal;
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "doCached", guards = "val == 3")
        protected static Object doSpecific(int val) {
            return val + val;
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "doCached")
        protected static Object doIndirect(int val) {
            return val + val;
        }
    }

    @Test
    public void testTwoMegamorpicSpec1() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.TwoMegamorpicSpecNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        // activates the first spec 2 times than the second (megamorphic)
        int[] args = {1, 2, 3, 4, 5};
        testMegamorphicHelper(callTarget, args);
    }

    @Test
    public void testTwoMegamorpicSpec2() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.TwoMegamorpicSpecNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        // activates the first spec 2 times than the last (megamorphic)
        int[] args = {1, 2, 4, 5, 6};
        testMegamorphicHelper(callTarget, args);
    }

    @ExpectWarning("This node uses @ReportPolymorphism on the class and @ReportPolymorphism.Megamorphic on some specializations, the latter annotation has no effect. Remove one of the annotations to resolve this.")
    @NodeChild
    abstract static class PolymorphicAndMegamorpic extends SplittingTestNode {

        @Specialization(limit = "2", guards = {"val == cachedVal", "val != 0"})
        protected static Object doCached(int val, @Cached("val") int cachedVal) {
            return val + cachedVal;
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "doCached")
        protected static Object doIndirect(int val) {
            return val + val;
        }
    }

    @Test
    public void testPolymorphicInPolyAndMegamorpic() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.PolymorphicAndMegamorpicNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        // activates the first spec 2 times than the last (megamorphic)
        int[] args = {1, 2, 3, 4};
        final DirectCallNode callNode1 = runtime.createDirectCallNode(callTarget);
        final DirectCallNode callNode2 = runtime.createDirectCallNode(callTarget);
        // Goes monomorphic
        callNode1.call(args[0]);
        Assert.assertFalse(callNode1.isCallTargetCloned());
        Assert.assertFalse(callNode2.isCallTargetCloned());
        // Goes polymorphic
        callNode2.call(args[1]);
        Assert.assertFalse(callNode1.isCallTargetCloned());
        Assert.assertFalse(callNode2.isCallTargetCloned());
        // Gets split
        callNode2.call(args[2]);
        Assert.assertFalse(callNode1.isCallTargetCloned());
        Assert.assertTrue(callNode2.isCallTargetCloned());
        // Gets split
        callNode1.call(args[3]);
        Assert.assertTrue(callNode1.isCallTargetCloned());
    }

    @Test
    public void testMegamorphicInPolyAndMegamorpic() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.PolymorphicAndMegamorpicNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        // activates the first spec 2 times than the last (megamorphic)
        int[] args = {1, 0, 3, 4};
        final DirectCallNode callNode1 = runtime.createDirectCallNode(callTarget);
        final DirectCallNode callNode2 = runtime.createDirectCallNode(callTarget);
        // Goes monomorphic
        callNode1.call(args[0]);
        Assert.assertFalse(callNode1.isCallTargetCloned());
        Assert.assertFalse(callNode2.isCallTargetCloned());
        // Goes megamorphic
        callNode2.call(args[1]);
        Assert.assertFalse(callNode1.isCallTargetCloned());
        Assert.assertFalse(callNode2.isCallTargetCloned());
        // Gets split
        callNode2.call(args[2]);
        Assert.assertFalse(callNode1.isCallTargetCloned());
        Assert.assertTrue(callNode2.isCallTargetCloned());
        // Gets split
        callNode1.call(args[3]);
        Assert.assertTrue(callNode1.isCallTargetCloned());
    }

    @TruffleLanguage.Registration(id = PreparationLanguage.ID, name = PreparationLanguage.ID)
    static final class PreparationLanguage extends TruffleLanguage<Object> {
        static final String ID = "SplittingPreparationLanguage";
        static final LanguageReference<PreparationLanguage> LANGUAGE_REFERENCE = LanguageReference.create(PreparationLanguage.class);
        static final ContextReference<Object> CONTEXT_REFERENCE = ContextReference.create(PreparationLanguage.class);
        static volatile OptimizedCallTarget target;

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) {
            target = (OptimizedCallTarget) new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return 42;
                }

                @Override
                protected boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
                    Assert.assertSame(PreparationLanguage.this, LANGUAGE_REFERENCE.get(this));
                    Assert.assertNull(CONTEXT_REFERENCE.get(this));
                    return true;
                }
            }.getCallTarget();
            return target;
        }
    }

    @Test
    public void testLanguageEnteredDuringPreparation() throws Exception {
        try (Context context = Context.newBuilder(PreparationLanguage.ID).allowExperimentalOptions(true).option("engine.Compilation", "false").build()) {
            context.eval(PreparationLanguage.ID, "");
            OptimizedCallTarget target = PreparationLanguage.target;
            FutureTask<Void> preparation = new FutureTask<>(() -> {
                Assert.assertTrue(target.prepareForCompilation(false, 1, false));
                Assert.assertTrue(target.prepareForCompilation(true, 1, false));
                return null;
            });
            new Thread(preparation).start();
            preparation.get();
        }
    }

    @TruffleLanguage.Registration(id = SplittingLimitTestLanguage.ID, name = SplittingLimitTestLanguage.ID)
    static class SplittingLimitTestLanguage extends ProxyLanguage {
        static final String ID = "SplittingLimitTestLanguage";

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(null) {

                final OptimizedCallTarget target = (OptimizedCallTarget) new SplittingTestRootNode(
                                NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();

                final OptimizedDirectCallNode callNode1 = (OptimizedDirectCallNode) insert(runtime.createDirectCallNode(target));
                final OptimizedDirectCallNode callNode2 = (OptimizedDirectCallNode) insert(runtime.createDirectCallNode(target));

                @Override
                public Object execute(VirtualFrame frame) {
                    // Target turns monomorphic on 1
                    callNode1.call(1);
                    // Target turns polymorphic on 0
                    callNode2.call(0);
                    // Give each a chance to split
                    callNode1.call(0);
                    callNode2.call(0);
                    assertExpectations();
                    return 42;
                }

                @CompilerDirectives.TruffleBoundary
                private void assertExpectations() {
                    // First is split because we have the budget
                    Assert.assertTrue(callNode1.isCallTargetCloned());
                    // Second is not because we don't have the budget
                    Assert.assertFalse(callNode2.isCallTargetCloned());
                }
            }.getCallTarget();
        }
    }

    @Test
    public void testSplittingBudgetLimit() {
        try (Context c = Context.newBuilder(SplittingLimitTestLanguage.ID).option("engine.CompileImmediately", "false").build()) {
            c.eval(SplittingLimitTestLanguage.ID, "");
        }
    }

    @Test
    public void testRootNodeSizeSmaller() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) new SplittingTestRootNode(NodeSplittingStrategyTestFactory.HasInlineCacheNodeGen.create(new ReturnsFirstArgumentNode())) {
            @Override
            protected int computeSize() {
                return OptimizedRuntimeOptions.SplittingMaxCalleeSize.getDefaultValue() - 1;
            }
        }.getCallTarget();
        Object[] first = new Object[]{new DummyRootNode().getCallTarget()};
        Object[] second = new Object[]{new DummyRootNode().getCallTarget()};
        testSplitsDirectCallsHelper(callTarget, first, second);
    }

    @Test
    public void testRootNodeSizeGreater() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) new SplittingTestRootNode(NodeSplittingStrategyTestFactory.HasInlineCacheNodeGen.create(new ReturnsFirstArgumentNode())) {
            @Override
            protected int computeSize() {
                return OptimizedRuntimeOptions.SplittingMaxCalleeSize.getDefaultValue() + 1;
            }
        }.getCallTarget();
        Object[] first = new Object[]{new DummyRootNode().getCallTarget()};
        Object[] second = new Object[]{new DummyRootNode().getCallTarget()};
        testNeedsSplitButDoesNotSplitDirectCallHelper(callTarget, first, second);

    }
}
