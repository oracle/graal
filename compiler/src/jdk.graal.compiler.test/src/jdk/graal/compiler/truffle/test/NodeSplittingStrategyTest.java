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

import com.oracle.truffle.api.dsl.test.ExpectWarning;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instruction.Argument;
import com.oracle.truffle.api.bytecode.Instruction.Argument.Kind;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.DebugBytecodeRootNode;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
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

        caller.getCallTarget().call(0);
        Assert.assertTrue("Target does not need split after the node went polymorphic", getNeedsSplit(callee));
        for (DirectCallNode callNode : callNodes) {
            Assert.assertFalse("Target split before compilation preparation", callNode.isCallTargetCloned());
        }

        OptimizedCallTarget callerTarget = (OptimizedCallTarget) caller.getCallTarget();
        Assert.assertFalse("Compilation was not delayed after splitting", callerTarget.prepareForCompilation(true, 1, false));
        for (DirectCallNode callNode : callNodes) {
            Assert.assertTrue("Bytecode DSL cached call target was not split during compilation preparation", callNode.isCallTargetCloned());
        }
        Assert.assertTrue("Compilation was delayed more than once", callerTarget.prepareForCompilation(true, 1, false));
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

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
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

    class EagerCallsInnerNode extends SplittableRootNode {

        @Child private OptimizedDirectCallNode callNode;

        EagerCallsInnerNode(RootCallTarget toCall) {
            this.callNode = (OptimizedDirectCallNode) runtime.createDirectCallNode(toCall);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.call(frame.getArguments());
        }
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
        CallsInnerNode outerRoot = new CallsInnerNode(callTarget);
        final OptimizedCallTarget outerTarget = (OptimizedCallTarget) outerRoot.getCallTarget();
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

        Assert.assertFalse("Compilation was not delayed after splitting", outerTarget.prepareForCompilation(true, 1, false));
        Assert.assertTrue("Call site in compiling root was not split", outerRoot.callNode.isCallTargetCloned());
        directCallNode.call(firstArgs);
        Assert.assertFalse("Unadopted call node was split", directCallNode.isCallTargetCloned());
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

    @Test
    public void testRootPreparationDoesNotTraverseUnmarkedCallees() {
        CallableOnlyOnceRootNode innerRoot = new CallableOnlyOnceRootNode();
        OptimizedCallTarget inner = (OptimizedCallTarget) innerRoot.getCallTarget();
        EagerCallsInnerNode middleRoot = new EagerCallsInnerNode(inner);
        OptimizedCallTarget middle = (OptimizedCallTarget) middleRoot.getCallTarget();
        EagerCallsInnerNode outerRoot = new EagerCallsInnerNode(middle);
        OptimizedCallTarget outer = (OptimizedCallTarget) outerRoot.getCallTarget();
        DirectCallNode outerCaller1 = runtime.createDirectCallNode(outer);
        DirectCallNode outerCaller2 = runtime.createDirectCallNode(outer);

        int innerCallers = inner.getKnownCallSiteCount();
        outer.call(noArguments);
        outer.call(noArguments);
        Assert.assertTrue("Retained copied call node was not registered", inner.getKnownCallSiteCount() > innerCallers);
        innerRoot.report();

        // Retained copies make caller counts imprecise and stop propagation at inner.
        Assert.assertFalse(getNeedsSplit(outer));
        Assert.assertFalse(getNeedsSplit(middle));
        Assert.assertTrue(getNeedsSplit(inner));
        Assert.assertTrue(outer.prepareForCompilation(true, 1, false));
        Assert.assertEquals(0, listener.splitCount);
        Assert.assertFalse(outerRoot.callNode.isCallTargetCloned());
        Assert.assertFalse(middleRoot.callNode.isCallTargetCloned());
        Assert.assertFalse(middle.prepareForCompilation(true, 1, false));
        Assert.assertEquals(1, listener.splitCount);
        Assert.assertTrue(middleRoot.callNode.isCallTargetCloned());
        Assert.assertSame(outer, outerCaller1.getCurrentCallTarget());
        Assert.assertSame(outer, outerCaller2.getCurrentCallTarget());
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
        Assert.assertFalse(((OptimizedCallTarget) callsInner1).prepareForCompilation(true, 1, false));
        Assert.assertFalse(((OptimizedCallTarget) callsInner2).prepareForCompilation(true, 1, false));
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

    private static void testMegamorphicHelper(OptimizedCallTarget callTarget, int[] args) {
        CallsTargetRootNode callerRoot1 = new CallsTargetRootNode(callTarget);
        CallsTargetRootNode callerRoot2 = new CallsTargetRootNode(callTarget);
        OptimizedCallTarget caller1 = (OptimizedCallTarget) callerRoot1.getCallTarget();
        OptimizedCallTarget caller2 = (OptimizedCallTarget) callerRoot2.getCallTarget();
        // Goes monomorphic
        caller1.call(args[0]);
        Assert.assertFalse(callerRoot1.callNode.isCallTargetCloned());
        Assert.assertFalse(callerRoot2.callNode.isCallTargetCloned());
        // Goes polymorphic
        caller2.call(args[1]);
        Assert.assertFalse(callerRoot1.callNode.isCallTargetCloned());
        Assert.assertFalse(callerRoot2.callNode.isCallTargetCloned());
        // Goes megamorphic and marks the target for splitting.
        caller1.call(args[2]);
        Assert.assertFalse(callerRoot1.callNode.isCallTargetCloned());
        Assert.assertFalse(callerRoot2.callNode.isCallTargetCloned());
        Assert.assertFalse(caller1.prepareForCompilation(true, 1, false));
        Assert.assertFalse(caller2.prepareForCompilation(true, 1, false));
        Assert.assertTrue(callerRoot1.callNode.isCallTargetCloned());
        Assert.assertTrue(callerRoot2.callNode.isCallTargetCloned());
        caller2.call(args[3]);
        caller1.call(args[4]);
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
        int[] args = {1, 2, 3, 4, 5};
        testMegamorphicHelper(callTarget, args);
    }

    @Test
    public void testMegamorphicInPolyAndMegamorpic() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) new SplittingTestRootNode(
                        NodeSplittingStrategyTestFactory.PolymorphicAndMegamorpicNodeGen.create(new ReturnsFirstArgumentNode())).getCallTarget();
        // activates the first spec 2 times than the last (megamorphic)
        int[] args = {1, 0, 3, 4, 5};
        testMegamorphicHelper(callTarget, args);
    }

    abstract static class CrossPreparationLanguage extends TruffleLanguage<Object> {

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }
    }

    @TruffleLanguage.Registration(id = CrossPreparationLanguageC.ID, name = CrossPreparationLanguageC.ID)
    static final class CrossPreparationLanguageC extends CrossPreparationLanguage {
        static final String ID = "CrossPreparationLanguageC";
        static final LanguageReference<CrossPreparationLanguageC> LANGUAGE_REFERENCE = LanguageReference.create(CrossPreparationLanguageC.class);
        static final ContextReference<Object> CONTEXT_REFERENCE = ContextReference.create(CrossPreparationLanguageC.class);

        static volatile OptimizedCallTarget target;

        @Override
        protected CallTarget parse(ParsingRequest request) {
            target = (OptimizedCallTarget) new CrossPreparationCloneRootNode(this, LANGUAGE_REFERENCE, CONTEXT_REFERENCE, null).getCallTarget();
            return target;
        }
    }

    @TruffleLanguage.Registration(id = CrossPreparationLanguageB.ID, name = CrossPreparationLanguageB.ID)
    static final class CrossPreparationLanguageB extends CrossPreparationLanguage {
        static final String ID = "CrossPreparationLanguageB";
        static final LanguageReference<CrossPreparationLanguageB> LANGUAGE_REFERENCE = LanguageReference.create(CrossPreparationLanguageB.class);
        static final ContextReference<Object> CONTEXT_REFERENCE = ContextReference.create(CrossPreparationLanguageB.class);

        static volatile OptimizedCallTarget target;

        @Override
        protected CallTarget parse(ParsingRequest request) {
            target = (OptimizedCallTarget) new CrossPreparationCloneRootNode(this, LANGUAGE_REFERENCE, CONTEXT_REFERENCE, CrossPreparationLanguageC.target).getCallTarget();
            return target;
        }
    }

    @TruffleLanguage.Registration(id = CrossPreparationLanguageA.ID, name = CrossPreparationLanguageA.ID)
    static final class CrossPreparationLanguageA extends CrossPreparationLanguage {
        static final String ID = "CrossPreparationLanguageA";
        static final LanguageReference<CrossPreparationLanguageA> LANGUAGE_REFERENCE = LanguageReference.create(CrossPreparationLanguageA.class);
        static final ContextReference<Object> CONTEXT_REFERENCE = ContextReference.create(CrossPreparationLanguageA.class);

        static volatile CrossPreparationState state;

        @Override
        protected CallTarget parse(ParsingRequest request) {
            CrossPreparationCallerRootNode callerRoot = new CrossPreparationCallerRootNode(this, CrossPreparationLanguageB.target);
            state = new CrossPreparationState(callerRoot, (OptimizedDirectCallNode) runtime.createDirectCallNode(CrossPreparationLanguageB.target),
                            (OptimizedDirectCallNode) runtime.createDirectCallNode(CrossPreparationLanguageC.target));
            return callerRoot.getCallTarget();
        }
    }

    static final class CrossPreparationState {
        final CrossPreparationCallerRootNode callerRoot;
        final OptimizedDirectCallNode additionalBCaller;
        final OptimizedDirectCallNode additionalCCaller;

        CrossPreparationState(CrossPreparationCallerRootNode callerRoot, OptimizedDirectCallNode additionalBCaller, OptimizedDirectCallNode additionalCCaller) {
            this.callerRoot = callerRoot;
            this.additionalBCaller = additionalBCaller;
            this.additionalCCaller = additionalCCaller;
        }
    }

    static final class CrossPreparationCloneRootNode extends RootNode {
        private final TruffleLanguage<?> language;
        private final LanguageReference<?> languageReference;
        private final ContextReference<?> contextReference;
        @Child private SplittingTestNode body = NodeSplittingStrategyTestFactory.TurnsPolymorphicOnZeroNodeGen.create(new ReturnsFirstArgumentNode());
        @Child private OptimizedDirectCallNode callNode;

        CrossPreparationCloneRootNode(TruffleLanguage<?> language, LanguageReference<?> languageReference, ContextReference<?> contextReference, OptimizedCallTarget calleeTarget) {
            super(language);
            this.language = language;
            this.languageReference = languageReference;
            this.contextReference = contextReference;
            this.callNode = calleeTarget == null ? null : (OptimizedDirectCallNode) runtime.createDirectCallNode(calleeTarget);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        protected boolean isCloneUninitializedSupported() {
            return true;
        }

        @Override
        protected RootNode cloneUninitialized() {
            Assert.assertSame(language, languageReference.get(this));
            Assert.assertNull(contextReference.get(this));
            OptimizedCallTarget calleeTarget = callNode == null ? null : callNode.getCallTarget();
            return new CrossPreparationCloneRootNode(language, languageReference, contextReference, calleeTarget);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (frame.getArguments().length == 0) {
                return 42;
            }
            if (callNode != null) {
                callNode.call(frame.getArguments());
            }
            return body.execute(frame);
        }
    }

    static final class CrossPreparationCallerRootNode extends RootNode {
        private final CrossPreparationLanguageA language;
        @Child private OptimizedDirectCallNode callNode;
        boolean rootPrepared;
        boolean inlinePrepared;

        CrossPreparationCallerRootNode(CrossPreparationLanguageA language, OptimizedCallTarget calleeTarget) {
            super(language);
            this.language = language;
            this.callNode = (OptimizedDirectCallNode) runtime.createDirectCallNode(calleeTarget);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            callNode.call(1);
            callNode.call(1);
            callNode.call(0);
            return 42;
        }

        @Override
        protected boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
            Assert.assertSame(language, CrossPreparationLanguageA.LANGUAGE_REFERENCE.get(this));
            Assert.assertNull(CrossPreparationLanguageA.CONTEXT_REFERENCE.get(this));
            if (rootCompilation) {
                rootPrepared = true;
            } else {
                inlinePrepared = true;
            }
            return true;
        }
    }

    @Test
    public void testCalleeLanguagesEnteredDuringSeparateRootPreparation() throws Exception {
        try (Context context = Context.newBuilder(CrossPreparationLanguageA.ID, CrossPreparationLanguageB.ID, CrossPreparationLanguageC.ID).allowExperimentalOptions(true).option(
                        "engine.Compilation", "false").option("engine.SplittingGrowthLimit", "10.0").build()) {
            context.eval(CrossPreparationLanguageC.ID, "");
            context.eval(CrossPreparationLanguageB.ID, "");
            context.eval(CrossPreparationLanguageA.ID, "");
            CrossPreparationState state = CrossPreparationLanguageA.state;
            OptimizedCallTarget callerTarget = (OptimizedCallTarget) state.callerRoot.getCallTarget();
            Assert.assertTrue(getNeedsSplit(CrossPreparationLanguageB.target));
            Assert.assertTrue(getNeedsSplit(CrossPreparationLanguageC.target));
            int cCallers = CrossPreparationLanguageC.target.getKnownCallSiteCount();

            FutureTask<Void> preparation = new FutureTask<>(() -> {
                Assert.assertTrue(callerTarget.prepareForCompilation(false, 1, false));
                Assert.assertFalse(state.callerRoot.callNode.isCallTargetCloned());
                Assert.assertFalse(callerTarget.prepareForCompilation(true, 1, false));
                Assert.assertTrue(callerTarget.prepareForCompilation(true, 1, false));
                return null;
            });
            new Thread(preparation).start();
            preparation.get();

            Assert.assertTrue(state.callerRoot.callNode.isCallTargetCloned());
            Assert.assertTrue(state.callerRoot.rootPrepared);
            Assert.assertTrue(state.callerRoot.inlinePrepared);
            Assert.assertSame(CrossPreparationLanguageB.target, state.additionalBCaller.getCurrentCallTarget());
            Assert.assertTrue("Recreated call node was not registered", CrossPreparationLanguageC.target.getKnownCallSiteCount() > cCallers);
            CrossPreparationCloneRootNode clonedBRoot = (CrossPreparationCloneRootNode) state.callerRoot.callNode.getCurrentCallTarget().getRootNode();
            Assert.assertFalse(clonedBRoot.callNode.isCallTargetCloned());

            // B's clone must execute before its own root preparation can split its call to C.
            context.enter();
            try {
                state.callerRoot.callNode.getCurrentCallTarget().call(1);
            } finally {
                context.leave();
            }
            FutureTask<Void> calleePreparation = new FutureTask<>(() -> {
                OptimizedCallTarget clonedB = (OptimizedCallTarget) clonedBRoot.getCallTarget();
                Assert.assertFalse(clonedB.prepareForCompilation(true, 1, false));
                Assert.assertTrue(clonedB.prepareForCompilation(true, 1, false));
                return null;
            });
            new Thread(calleePreparation).start();
            calleePreparation.get();
            Assert.assertTrue(clonedBRoot.callNode.isCallTargetCloned());
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

                @Child private OptimizedDirectCallNode callNode1 = (OptimizedDirectCallNode) runtime.createDirectCallNode(target);
                @Child private OptimizedDirectCallNode callNode2 = (OptimizedDirectCallNode) runtime.createDirectCallNode(target);

                @Override
                public Object execute(VirtualFrame frame) {
                    // Target turns monomorphic on 1
                    callNode1.call(1);
                    // Target turns polymorphic on 0
                    callNode2.call(0);
                    assertExpectations();
                    return 42;
                }

                @CompilerDirectives.TruffleBoundary
                private void assertExpectations() {
                    Assert.assertTrue(getNeedsSplit(target));
                    Assert.assertTrue(((OptimizedCallTarget) getCallTarget()).prepareForCompilation(true, 1, false));
                    // The context has no remaining splitting budget.
                    Assert.assertFalse(callNode1.isCallTargetCloned());
                    Assert.assertFalse(callNode2.isCallTargetCloned());
                }
            }.getCallTarget();
        }
    }

    @Test
    public void testSplittingBudgetLimit() {
        try (Context c = Context.newBuilder(SplittingLimitTestLanguage.ID).allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option(
                        "engine.SplittingGrowthLimit", "0.0").build()) {
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
