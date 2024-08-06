/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class StackTraceTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void testFirstFrameIsCurrentFrame() {
        var builder = FrameDescriptor.newBuilder();
        int demoIndex = builder.addSlot(FrameSlotKind.Illegal, "demo", null);
        CallTarget callTarget = createCallTarget(new ReturnStackTraceNode(), builder.build(), demoIndex);
        StackTrace stack = (StackTrace) callTarget.call();
        Assert.assertEquals(1, stack.frames.size());
        assertFrameEquals(stack.currentFrame, stack.frames.get(0));
    }

    @Test
    public void testNoStackTrace() {
        StackTrace stack = new StackTrace();
        Assert.assertNull(stack.callerFrame);
        Assert.assertNull(stack.currentFrame);
        Assert.assertEquals(0, stack.frames.size());
    }

    @Test
    public void testSingleStackTrace() {
        var builder = FrameDescriptor.newBuilder();
        int demoIndex = builder.addSlot(FrameSlotKind.Illegal, "demo", null);
        CallTarget callTarget = createCallTarget(new ReturnStackTraceNode(), builder.build(), demoIndex);
        StackTrace stack = (StackTrace) callTarget.call();

        Assert.assertEquals(1, stack.frames.size());
        Assert.assertSame(callTarget, stack.currentFrame.getCallTarget());
        Assert.assertNull(stack.currentFrame.getCallNode());
        assertInvariants(stack);
    }

    @Test
    public void testDirectStackTrace() {
        var builder = FrameDescriptor.newBuilder();
        int demoIndex = builder.addSlot(FrameSlotKind.Illegal, "demo", null);
        CallTarget createStackTrace = createCallTarget(new ReturnStackTraceNode(), builder.build(), demoIndex);
        CallTarget call = createCallTarget(new TestCallWithDirectTargetNode(createStackTrace), builder.build(), demoIndex);
        StackTrace stack = (StackTrace) call.call();

        assertInvariants(stack);
        Assert.assertEquals(2, stack.frames.size());
        Assert.assertSame(createStackTrace, stack.currentFrame.getCallTarget());
        Assert.assertNull(stack.currentFrame.getCallNode());
        Assert.assertSame(call, stack.callerFrame.getCallTarget());
        Assert.assertSame(findCallNode(call), stack.callerFrame.getCallNode());
    }

    @Test
    public void testIndirectStackTrace() {
        var builder = FrameDescriptor.newBuilder();
        int demoIndex = builder.addSlot(FrameSlotKind.Illegal, "demo", null);
        CallTarget createStackTrace = createCallTarget(new ReturnStackTraceNode(), builder.build(), demoIndex);
        CallTarget call = createCallTarget(new TestCallWithIndirectTargetNode(createStackTrace), builder.build(), demoIndex);
        StackTrace stack = (StackTrace) call.call();

        Assert.assertEquals(2, stack.frames.size());
        Assert.assertSame(createStackTrace, stack.currentFrame.getCallTarget());
        Assert.assertNull(stack.currentFrame.getCallNode());
        Assert.assertSame(call, stack.callerFrame.getCallTarget());
        Assert.assertSame(findCallNode(call), stack.callerFrame.getCallNode());
        assertInvariants(stack);
    }

    @Test
    public void testCallTargetStackTrace() {
        var builder = FrameDescriptor.newBuilder();
        int demoIndex = builder.addSlot(FrameSlotKind.Illegal, "demo", null);
        CallTarget createStackTrace = createCallTarget(new ReturnStackTraceNode(), builder.build(), demoIndex);
        CallTarget call = createCallTarget(new TestCallWithCallTargetNode(createStackTrace), builder.build(), demoIndex);
        StackTrace stack = (StackTrace) call.call();

        assertInvariants(stack);
        Assert.assertEquals(2, stack.frames.size());
        Assert.assertSame(createStackTrace, stack.currentFrame.getCallTarget());
        Assert.assertNull(stack.currentFrame.getCallNode());
        Assert.assertSame(call, stack.callerFrame.getCallTarget());
        Assert.assertNull(stack.callerFrame.getCallNode());
    }

    @Test
    public void testCombinedStackTrace() {
        var builder = FrameDescriptor.newBuilder();
        int demoIndex = builder.addSlot(FrameSlotKind.Illegal, "demo", null);
        CallTarget createStackTrace = createCallTarget(new ReturnStackTraceNode(), builder.build(), demoIndex);
        CallTarget callTarget = createCallTarget(new TestCallWithCallTargetNode(createStackTrace), builder.build(), demoIndex);
        CallTarget indirect = createCallTarget(new TestCallWithIndirectTargetNode(callTarget), builder.build(), demoIndex);
        CallTarget direct = createCallTarget(new TestCallWithDirectTargetNode(indirect), builder.build(), demoIndex);
        StackTrace stack = (StackTrace) direct.call();

        assertInvariants(stack);
        Assert.assertEquals(4, stack.frames.size());

        Assert.assertSame(createStackTrace, stack.currentFrame.getCallTarget());
        Assert.assertNull(stack.currentFrame.getCallNode());
        Assert.assertSame(callTarget, stack.callerFrame.getCallTarget());
        Assert.assertNull(stack.callerFrame.getCallNode());

        Assert.assertSame(indirect, stack.frames.get(2).getCallTarget());
        Assert.assertSame(findCallNode(indirect), stack.frames.get(2).getCallNode());

        Assert.assertSame(direct, stack.frames.get(3).getCallTarget());
        Assert.assertSame(findCallNode(direct), stack.frames.get(3).getCallNode());
    }

    @Test
    public void testFrameAccess() {
        var builder = FrameDescriptor.newBuilder();
        int demoIndex = builder.addSlot(FrameSlotKind.Illegal, "demo", null);
        CallTarget callTarget = createCallTarget(new TestCallWithCallTargetNode(null), builder.build(), demoIndex);
        CallTarget indirect = createCallTarget(new TestCallWithIndirectTargetNode(callTarget), builder.build(), demoIndex);
        CallTarget direct = createCallTarget(new TestCallWithDirectTargetNode(indirect), builder.build(), demoIndex);
        CallTarget test = createCallTarget(new TestCallNode(null) {
            @Override
            Object execute(VirtualFrame frame) {
                Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<>() {
                    public Object visitFrame(FrameInstance frameInstance) {

                        Frame readOnlyFrame = frameInstance.getFrame(FrameAccess.READ_ONLY);
                        assertEquals(42, readOnlyFrame.getValue(demoIndex));

                        Frame readWriteFrame = frameInstance.getFrame(FrameAccess.READ_WRITE);
                        assertEquals(42, readWriteFrame.getValue(demoIndex));
                        readWriteFrame.setObject(demoIndex, 43);

                        Frame materializedFrame = frameInstance.getFrame(FrameAccess.MATERIALIZE);
                        assertEquals(43, materializedFrame.getValue(demoIndex));

                        materializedFrame.setObject(demoIndex, 44);
                        assertEquals(44, readOnlyFrame.getValue(demoIndex));
                        assertEquals(44, readWriteFrame.getValue(demoIndex));

                        return null;
                    }
                });
                return null;
            }
        }, builder.build(), demoIndex);
        findTestCallNode(callTarget).setNext(test);
        direct.call();
    }

    @Test
    public void testStackTraversal() {
        var builder = FrameDescriptor.newBuilder();
        int demoIndex = builder.addSlot(FrameSlotKind.Illegal, "demo", null);
        CallTarget callTarget = createCallTarget(new TestCallWithCallTargetNode(null), builder.build(), demoIndex);
        CallTarget indirect = createCallTarget(new TestCallWithIndirectTargetNode(callTarget), builder.build(), demoIndex);
        CallTarget direct = createCallTarget(new TestCallWithDirectTargetNode(indirect), builder.build(), demoIndex);
        CallTarget test = createCallTarget(new TestCallNode(null) {
            int visitCount = 0;

            @Override
            Object execute(VirtualFrame frame) {
                Object result = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<>() {
                    public Object visitFrame(FrameInstance frameInstance) {
                        visitCount++;
                        return "foobar";
                    }
                });
                assertEquals(1, visitCount);
                assertEquals("foobar", result);

                visitCount = 0;
                result = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<>() {
                    public Object visitFrame(FrameInstance frameInstance) {
                        visitCount++;
                        if (visitCount == 2) {
                            return "foobar";
                        } else {
                            return null; // continue traversing
                        }
                    }
                });
                assertEquals(2, visitCount);
                assertEquals("foobar", result);

                return null;
            }
        }, builder.build(), demoIndex);
        findTestCallNode(callTarget).setNext(test);
        direct.call();
    }

    @Test
    public void testAsynchronousFrameAccess() throws InterruptedException, ExecutionException, TimeoutException {
        // Takes too long with immediate compilation
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
        final ExecutorService exec = Executors.newFixedThreadPool(50);
        try {
            List<Callable<Void>> callables = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                callables.add(new Callable<Void>() {
                    @Override
                    public Void call() {
                        var builder = FrameDescriptor.newBuilder();
                        int demoIndex = builder.addSlot(FrameSlotKind.Illegal, "demo", null);

                        final CallTarget createStackTrace = createCallTarget(new ReturnStackTraceNode(), builder.build(), demoIndex);
                        final CallTarget callTarget = createCallTarget(new TestCallWithCallTargetNode(createStackTrace), builder.build(), demoIndex);
                        final CallTarget indirect = createCallTarget(new TestCallWithIndirectTargetNode(callTarget), builder.build(), demoIndex);
                        final CallTarget direct = createCallTarget(new TestCallWithDirectTargetNode(indirect), builder.build(), demoIndex);

                        for (int j = 0; j < 10; j++) {
                            StackTrace stack = (StackTrace) direct.call();
                            assertInvariants(stack);
                            Assert.assertEquals(4, stack.frames.size());
                            Assert.assertSame(createStackTrace, stack.currentFrame.getCallTarget());
                            Assert.assertNull(stack.currentFrame.getCallNode());
                            Assert.assertSame(callTarget, stack.callerFrame.getCallTarget());
                            Assert.assertNull(stack.callerFrame.getCallNode());

                            Assert.assertSame(indirect, stack.frames.get(2).getCallTarget());
                            Assert.assertSame(findCallNode(indirect), stack.frames.get(2).getCallNode());

                            Assert.assertSame(direct, stack.frames.get(3).getCallTarget());
                            Assert.assertSame(findCallNode(direct), stack.frames.get(3).getCallNode());
                        }
                        return null;
                    }
                });
            }
            for (Future<?> future : exec.invokeAll(callables)) {
                future.get(5000, TimeUnit.MILLISECONDS);
            }

        } finally {
            exec.shutdown();
        }

    }

    private static TestCallNode findTestCallNode(CallTarget target) {
        return ((TestRootNode) ((RootCallTarget) target).getRootNode()).callNode;
    }

    private static Node findCallNode(CallTarget target) {
        return findTestCallNode(target).getCallNode();
    }

    @CompilerDirectives.TruffleBoundary
    private static void assertEquals(int expected, int actual) {
        Assert.assertEquals(expected, actual);
    }

    @CompilerDirectives.TruffleBoundary
    private static void assertEquals(Object expected, Object actual) {
        Assert.assertEquals(expected, actual);
    }

    private static void assertInvariants(StackTrace stack) {
        if (stack.frames.size() == 0) {
            Assert.assertNull(stack.currentFrame);
        } else {
            Assert.assertNotNull(stack.currentFrame);
        }

        if (stack.frames.size() <= 1) {
            Assert.assertNull(stack.callerFrame);
        } else {
            Assert.assertNotNull(stack.callerFrame);
        }

        for (int i = 0; i < stack.frames.size(); i++) {
            FrameInstance frame = stack.frames.get(i);
            if (i == 0) {
                assertFrameEquals(stack.currentFrame, frame);
            } else if (i == 1) {
                assertFrameEquals(stack.callerFrame, frame);
            }
            Assert.assertNotNull(frame.getCallTarget());
            Assert.assertNotNull(frame.toString()); // # does not crash
        }
    }

    private static void assertFrameEquals(FrameInstance expected, FrameInstance other) {
        Assert.assertEquals(expected.isVirtualFrame(), other.isVirtualFrame());
        Assert.assertSame(expected.getCallNode(), other.getCallNode());
        Assert.assertSame(expected.getCallTarget(), other.getCallTarget());
    }

    private static CallTarget createCallTarget(TestCallNode callNode, FrameDescriptor descriptor, int demoIndex) {
        return new TestRootNode(callNode, descriptor, demoIndex).getCallTarget();
    }

    private static class TestCallWithCallTargetNode extends TestCallNode {

        TestCallWithCallTargetNode(CallTarget next) {
            super(next);
        }

        @Override
        Object execute(VirtualFrame frame) {
            return next.call();
        }

    }

    private static class TestCallWithIndirectTargetNode extends TestCallNode {

        @Child IndirectCallNode indirectCall = Truffle.getRuntime().createIndirectCallNode();

        TestCallWithIndirectTargetNode(CallTarget next) {
            super(next);
        }

        @Override
        Object execute(VirtualFrame frame) {
            return indirectCall.call(next, new Object[0]);
        }

        @Override
        public Node getCallNode() {
            return indirectCall;
        }

    }

    private static class TestCallWithDirectTargetNode extends TestCallNode {

        @Child DirectCallNode directCall;

        TestCallWithDirectTargetNode(CallTarget next) {
            super(next);
        }

        @Override
        Object execute(VirtualFrame frame) {
            if (directCall == null || directCall.getCallTarget() != next) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                directCall = insert(Truffle.getRuntime().createDirectCallNode(next));
            }
            return directCall.call(new Object[0]);
        }

        @Override
        public Node getCallNode() {
            return directCall;
        }

    }

    private static class ReturnStackTraceNode extends TestCallNode {

        ReturnStackTraceNode() {
            super(null);
        }

        @Override
        Object execute(VirtualFrame frame) {
            return new StackTrace();
        }
    }

    private static class StackTrace {

        final List<FrameInstance> frames;
        final FrameInstance currentFrame;
        final FrameInstance callerFrame;

        @SuppressWarnings("deprecation")
        StackTrace() {
            frames = new ArrayList<>();
            Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {
                public Void visitFrame(FrameInstance frameInstance) {
                    frames.add(frameInstance);
                    return null;
                }
            });

            currentFrame = Truffle.getRuntime().iterateFrames((f) -> f, 0);
            callerFrame = Truffle.getRuntime().iterateFrames((f) -> f, 1);
        }

    }

    private abstract static class TestCallNode extends Node {

        protected CallTarget next;

        TestCallNode(CallTarget next) {
            this.next = next;
        }

        public void setNext(CallTarget next) {
            this.next = next;
        }

        abstract Object execute(VirtualFrame frame);

        public Node getCallNode() {
            return null;
        }

    }

    private static class TestRootNode extends RootNode {

        @Child private TestCallNode callNode;
        final int demoIndex;

        TestRootNode(TestCallNode callNode, FrameDescriptor descriptor, int demoIndex) {
            super(null, descriptor);
            this.callNode = callNode;
            this.demoIndex = demoIndex;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            prepareFrame(frame.materialize());
            return callNode.execute(frame);
        }

        @CompilerDirectives.TruffleBoundary
        private void prepareFrame(MaterializedFrame frame) {
            frame.setObject(demoIndex, 42);
        }

    }

}
