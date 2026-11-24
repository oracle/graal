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
package com.oracle.truffle.api.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class CallTargetBytecodeIndexTest {

    @Test
    public void testCallWithoutExplicitBytecodeIndexUsesRootLookup() {
        CalleeRootNode callee = new CalleeRootNode();
        CallerRootNode caller = new CallerRootNode(callee.getCallTarget(), 11, 42, false);

        StackSummary summary = (StackSummary) caller.getCallTarget().call();

        assertCurrentFrame(callee, summary);
        assertSame(caller.getCallTarget(), summary.caller.target);
        assertSame(caller.callLocation, summary.caller.callNode);
        assertEquals(caller.callLocation.bytecodeIndex, summary.caller.bytecodeIndex);
        assertSame(caller.callLocation, summary.caller.instrumentableCallNode);
        assertSame(caller.marker, summary.caller.frameValue);
    }

    @Test
    public void testCallWithExplicitBytecodeIndexOverridesRootLookup() {
        CalleeRootNode callee = new CalleeRootNode();
        CallerRootNode caller = new CallerRootNode(callee.getCallTarget(), 11, 42, true);

        StackSummary summary = (StackSummary) caller.getCallTarget().call();

        assertCurrentFrame(callee, summary);
        assertSame(caller.getCallTarget(), summary.caller.target);
        assertSame(caller.callLocation, summary.caller.callNode);
        assertEquals(caller.explicitBytecodeIndex, summary.caller.bytecodeIndex);
        assertSame(caller.instrumentableCallNode, summary.caller.instrumentableCallNode);
        assertSame(caller.marker, summary.caller.frameValue);
    }

    @Test
    public void testCallWithSentinelBytecodeIndexUsesRootLookup() {
        CalleeRootNode callee = new CalleeRootNode();
        CallerRootNode caller = new CallerRootNode(callee.getCallTarget(), 11, -1, true);

        StackSummary summary = (StackSummary) caller.getCallTarget().call();

        assertCurrentFrame(callee, summary);
        assertSame(caller.getCallTarget(), summary.caller.target);
        assertSame(caller.callLocation, summary.caller.callNode);
        assertEquals(caller.callLocation.bytecodeIndex, summary.caller.bytecodeIndex);
        assertSame(caller.callLocation, summary.caller.instrumentableCallNode);
        assertSame(caller.marker, summary.caller.frameValue);
    }

    @Test
    public void testDirectCallNodeWithoutExplicitBytecodeIndexUsesRootLookup() {
        CalleeRootNode callee = new CalleeRootNode();
        DirectCallerRootNode caller = new DirectCallerRootNode(callee.getCallTarget(), 13, 42, false);

        StackSummary summary = (StackSummary) caller.getCallTarget().call();

        assertCurrentFrame(callee, summary);
        assertSame(caller.getCallTarget(), summary.caller.target);
        assertSame(caller.directCall, summary.caller.callNode);
        assertEquals(caller.callLocation.bytecodeIndex, summary.caller.bytecodeIndex);
        assertSame(caller.directCall, summary.caller.instrumentableCallNode);
        assertSame(caller.marker, summary.caller.frameValue);
    }

    @Test
    public void testDirectCallNodeWithExplicitBytecodeIndexOverridesRootLookup() {
        CalleeRootNode callee = new CalleeRootNode();
        DirectCallerRootNode caller = new DirectCallerRootNode(callee.getCallTarget(), 13, 42, true);

        StackSummary summary = (StackSummary) caller.getCallTarget().call();

        assertCurrentFrame(callee, summary);
        assertSame(caller.getCallTarget(), summary.caller.target);
        assertSame(caller.directCall, summary.caller.callNode);
        assertEquals(caller.explicitBytecodeIndex, summary.caller.bytecodeIndex);
        assertSame(caller.instrumentableCallNode, summary.caller.instrumentableCallNode);
        assertSame(caller.marker, summary.caller.frameValue);
    }

    @Test
    public void testDirectCallNodeWithSentinelBytecodeIndexUsesRootLookup() {
        CalleeRootNode callee = new CalleeRootNode();
        DirectCallerRootNode caller = new DirectCallerRootNode(callee.getCallTarget(), 13, -1, true);

        StackSummary summary = (StackSummary) caller.getCallTarget().call();

        assertCurrentFrame(callee, summary);
        assertSame(caller.getCallTarget(), summary.caller.target);
        assertSame(caller.directCall, summary.caller.callNode);
        assertEquals(caller.callLocation.bytecodeIndex, summary.caller.bytecodeIndex);
        assertSame(caller.directCall, summary.caller.instrumentableCallNode);
        assertSame(caller.marker, summary.caller.frameValue);
    }

    @Test
    public void testDirectCallNodeWithRootTypeAndExplicitBytecodeIndex() {
        CalleeRootNode callee = new CalleeRootNode();
        DirectCallerRootNode caller = new DirectCallerRootNode(callee.getCallTarget(), 13, 42, true, true);

        StackSummary summary = (StackSummary) caller.getCallTarget().call();

        assertCurrentFrame(callee, summary);
        assertSame(caller.getCallTarget(), summary.caller.target);
        assertSame(caller.directCall, summary.caller.callNode);
        assertEquals(caller.explicitBytecodeIndex, summary.caller.bytecodeIndex);
        assertSame(caller.instrumentableCallNode, summary.caller.instrumentableCallNode);
        assertSame(caller.marker, summary.caller.frameValue);
    }

    @Test
    public void testIndirectCallNodeWithoutExplicitBytecodeIndexUsesRootLookup() {
        CalleeRootNode callee = new CalleeRootNode();
        IndirectCallerRootNode caller = new IndirectCallerRootNode(callee.getCallTarget(), 17, 42, false);

        StackSummary summary = (StackSummary) caller.getCallTarget().call();

        assertCurrentFrame(callee, summary);
        assertSame(caller.getCallTarget(), summary.caller.target);
        assertSame(caller.indirectCall, summary.caller.callNode);
        assertEquals(caller.callLocation.bytecodeIndex, summary.caller.bytecodeIndex);
        assertSame(caller.indirectCall, summary.caller.instrumentableCallNode);
        assertSame(caller.marker, summary.caller.frameValue);
    }

    @Test
    public void testIndirectCallNodeWithExplicitBytecodeIndexOverridesRootLookup() {
        CalleeRootNode callee = new CalleeRootNode();
        IndirectCallerRootNode caller = new IndirectCallerRootNode(callee.getCallTarget(), 17, 42, true);

        StackSummary summary = (StackSummary) caller.getCallTarget().call();

        assertCurrentFrame(callee, summary);
        assertSame(caller.getCallTarget(), summary.caller.target);
        assertSame(caller.indirectCall, summary.caller.callNode);
        assertEquals(caller.explicitBytecodeIndex, summary.caller.bytecodeIndex);
        assertSame(caller.instrumentableCallNode, summary.caller.instrumentableCallNode);
        assertSame(caller.marker, summary.caller.frameValue);
    }

    @Test
    public void testIndirectCallNodeWithSentinelBytecodeIndexUsesRootLookup() {
        CalleeRootNode callee = new CalleeRootNode();
        IndirectCallerRootNode caller = new IndirectCallerRootNode(callee.getCallTarget(), 17, -1, true);

        StackSummary summary = (StackSummary) caller.getCallTarget().call();

        assertCurrentFrame(callee, summary);
        assertSame(caller.getCallTarget(), summary.caller.target);
        assertSame(caller.indirectCall, summary.caller.callNode);
        assertEquals(caller.callLocation.bytecodeIndex, summary.caller.bytecodeIndex);
        assertSame(caller.indirectCall, summary.caller.instrumentableCallNode);
        assertSame(caller.marker, summary.caller.frameValue);
    }

    @Test
    public void testIndirectCallNodeWithRootAndExplicitBytecodeIndex() {
        CalleeRootNode callee = new CalleeRootNode();
        IndirectCallerRootNode caller = new IndirectCallerRootNode(callee.getCallTarget(), 17, 42, true, true);

        StackSummary summary = (StackSummary) caller.getCallTarget().call();

        assertCurrentFrame(callee, summary);
        assertSame(caller.getCallTarget(), summary.caller.target);
        assertSame(caller.indirectCall, summary.caller.callNode);
        assertEquals(caller.explicitBytecodeIndex, summary.caller.bytecodeIndex);
        assertSame(caller.instrumentableCallNode, summary.caller.instrumentableCallNode);
        assertSame(caller.marker, summary.caller.frameValue);
    }

    @Test
    public void testUncachedIndirectCallNodeWithExplicitBytecodeIndexUsesEncapsulatingNode() {
        CalleeRootNode callee = new CalleeRootNode();
        UncachedIndirectCallerRootNode caller = new UncachedIndirectCallerRootNode(callee, 23, 42, false);

        assertUncachedIndirectCall(callee, caller);
    }

    @Test
    public void testUncachedIndirectCallNodeWithRootAndExplicitBytecodeIndexUsesEncapsulatingNode() {
        CalleeRootNode callee = new CalleeRootNode();
        UncachedIndirectCallerRootNode caller = new UncachedIndirectCallerRootNode(callee, 23, 42, true);

        assertUncachedIndirectCall(callee, caller);
    }

    private static void assertUncachedIndirectCall(CalleeRootNode callee, UncachedIndirectCallerRootNode caller) {
        callee.initializeCallTarget();
        StackSummary summary = (StackSummary) caller.getCallTarget().call();

        assertCurrentFrame(callee, summary);
        assertSame(caller.getCallTarget(), summary.caller.target);
        assertSame(caller.callLocation, summary.caller.callNode);
        assertEquals(caller.explicitBytecodeIndex, summary.caller.bytecodeIndex);
        assertSame(caller.instrumentableCallNode, summary.caller.instrumentableCallNode);
        assertSame(caller.marker, summary.caller.frameValue);
    }

    @Test
    public void testTruffleExceptionWithExplicitBytecodeIndex() throws UnsupportedMessageException {
        try (Context context = Context.create()) {
            context.enter();
            SourceSection sourceSection = Source.newBuilder("test", "return 42", "explicit-bci.test").build().createSection(1, 6);
            ExceptionRootNode root = new ExceptionRootNode(19, 42, sourceSection);
            try {
                root.getCallTarget().call();
                fail("call should have thrown");
            } catch (TestException ex) {
                assertEquals(42, ex.getBytecodeIndex());
                assertExceptionStackTrace(root, ex, 42, sourceSection);
            }
        }
    }

    @Test
    public void testTruffleExceptionWithSentinelBytecodeIndexUsesRootLookup() throws UnsupportedMessageException {
        try (Context context = Context.create()) {
            context.enter();
            SourceSection sourceSection = Source.newBuilder("test", "return 42", "root-lookup-bci.test").build().createSection(1, 6);
            ExceptionRootNode root = new ExceptionRootNode(19, -1, sourceSection);
            try {
                root.getCallTarget().call();
                fail("call should have thrown");
            } catch (TestException ex) {
                assertEquals(-1, ex.getBytecodeIndex());
                assertExceptionStackTrace(root, ex, root.callLocation.bytecodeIndex, sourceSection);
            }
        }
    }

    private static void assertExceptionStackTrace(ExceptionRootNode root, TestException exception, int expectedBytecodeIndex, SourceSection expectedSourceSection) throws UnsupportedMessageException {
        List<TruffleStackTraceElement> stackTrace = TruffleStackTrace.getStackTrace(exception);
        assertEquals(1, stackTrace.size());
        TruffleStackTraceElement element = stackTrace.get(0);
        assertSame(root.getCallTarget(), element.getTarget());
        assertSame(root.callLocation, element.getLocation());
        assertEquals(expectedBytecodeIndex, element.getBytecodeIndex());
        assertSame(expectedSourceSection, exception.getEncapsulatingSourceSection());

        Object guestObject = element.getGuestObject();
        InteropLibrary interop = InteropLibrary.getUncached();
        assertTrue(interop.hasBytecodeIndex(guestObject));
        assertEquals(expectedBytecodeIndex, interop.getBytecodeIndex(guestObject));
        assertTrue(interop.hasSourceLocation(guestObject));
        assertSame(expectedSourceSection, interop.getSourceLocation(guestObject));
    }

    private static void assertCurrentFrame(CalleeRootNode callee, StackSummary summary) {
        assertEquals(2, summary.frameCount);
        assertSame(callee.getCallTarget(), summary.current.target);
        assertNull(summary.current.callNode);
        assertEquals(-1, summary.current.bytecodeIndex);
        assertSame(callee.marker, summary.current.frameValue);
    }

    private static final class StackSummary {
        final FrameSummary current;
        final FrameSummary caller;
        final int frameCount;

        private StackSummary(FrameSummary current, FrameSummary caller, int frameCount) {
            this.current = current;
            this.caller = caller;
            this.frameCount = frameCount;
        }
    }

    private static final class FrameSummary {
        final CallTarget target;
        final Node callNode;
        final Node instrumentableCallNode;
        final int bytecodeIndex;
        final Object frameValue;

        private FrameSummary(CallTarget target, Node callNode, Node instrumentableCallNode, int bytecodeIndex, Object frameValue) {
            this.target = target;
            this.callNode = callNode;
            this.instrumentableCallNode = instrumentableCallNode;
            this.bytecodeIndex = bytecodeIndex;
            this.frameValue = frameValue;
        }

        static FrameSummary capture(FrameInstance frameInstance, int slot) {
            Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
            return new FrameSummary(frameInstance.getCallTarget(), frameInstance.getCallNode(), frameInstance.getInstrumentableCallNode(), frameInstance.getBytecodeIndex(), frame.getObject(slot));
        }
    }

    private static final class FrameRecorder {
        private final int slot;

        private FrameRecorder(int slot) {
            this.slot = slot;
        }

        StackSummary capture() {
            List<FrameSummary> frames = new ArrayList<>(2);
            Truffle.getRuntime().iterateFrames(frameInstance -> {
                frames.add(FrameSummary.capture(frameInstance, slot));
                return frames.size() >= 2 ? Boolean.TRUE : null;
            });
            return new StackSummary(frames.get(0), frames.get(1), frames.size());
        }
    }

    private abstract static class BytecodeIndexRootNode extends RootNode {
        static final int SLOT = 0;

        final Object marker = new Object();
        @Child TestCallLocationNode callLocation;
        @Child Node instrumentableCallNode = new Node() {
        };

        BytecodeIndexRootNode(int nodeBytecodeIndex) {
            this(nodeBytecodeIndex, null);
        }

        BytecodeIndexRootNode(int nodeBytecodeIndex, SourceSection sourceSection) {
            super(null, createFrameDescriptor());
            this.callLocation = new TestCallLocationNode(nodeBytecodeIndex, sourceSection);
        }

        private static FrameDescriptor createFrameDescriptor() {
            FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
            builder.addSlot(FrameSlotKind.Object, "marker", null);
            return builder.build();
        }

        @Override
        protected int findBytecodeIndex(Node node, Frame frame) {
            if (node instanceof TestCallLocationNode testCallLocationNode) {
                return testCallLocationNode.bytecodeIndex;
            }
            return -1;
        }
    }

    private static final class CallerRootNode extends BytecodeIndexRootNode {
        final int explicitBytecodeIndex;
        final boolean useExplicitCall;
        final CallTarget callee;

        CallerRootNode(CallTarget callee, int nodeBytecodeIndex, int explicitBytecodeIndex, boolean useExplicitCall) {
            super(nodeBytecodeIndex);
            this.callee = callee;
            this.explicitBytecodeIndex = explicitBytecodeIndex;
            this.useExplicitCall = useExplicitCall;
        }

        @Override
        protected Node findInstrumentableCallNode(Node callNode, Frame frame, int bytecodeIndex) {
            if (callNode == callLocation && bytecodeIndex == explicitBytecodeIndex) {
                return instrumentableCallNode;
            }
            return callNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setObject(SLOT, marker);
            if (useExplicitCall) {
                return callee.call(callLocation, explicitBytecodeIndex, new Object[0]);
            }
            return callee.call(callLocation, new Object[0]);
        }
    }

    private abstract static class CallNodeCallerRootNode extends BytecodeIndexRootNode {
        final int explicitBytecodeIndex;
        final boolean useExplicitCall;
        final CallTarget callee;

        CallNodeCallerRootNode(CallTarget callee, int nodeBytecodeIndex, int explicitBytecodeIndex, boolean useExplicitCall) {
            super(nodeBytecodeIndex);
            this.callee = callee;
            this.explicitBytecodeIndex = explicitBytecodeIndex;
            this.useExplicitCall = useExplicitCall;
        }

        protected abstract Node getActiveCallNode();

        @Override
        protected int findBytecodeIndex(Node node, Frame frame) {
            Node activeCallNode = getActiveCallNode();
            if (activeCallNode != null && node == activeCallNode) {
                return callLocation.bytecodeIndex;
            }
            return super.findBytecodeIndex(node, frame);
        }

        @Override
        protected Node findInstrumentableCallNode(Node callNode, Frame frame, int bytecodeIndex) {
            Node activeCallNode = getActiveCallNode();
            if (activeCallNode != null && callNode == activeCallNode && bytecodeIndex == explicitBytecodeIndex) {
                return instrumentableCallNode;
            }
            return callNode;
        }
    }

    private static final class DirectCallerRootNode extends CallNodeCallerRootNode {
        @Child DirectCallNode directCall;
        final boolean useRootCall;

        DirectCallerRootNode(CallTarget callee, int nodeBytecodeIndex, int explicitBytecodeIndex, boolean useExplicitCall) {
            this(callee, nodeBytecodeIndex, explicitBytecodeIndex, useExplicitCall, false);
        }

        DirectCallerRootNode(CallTarget callee, int nodeBytecodeIndex, int explicitBytecodeIndex, boolean useExplicitCall, boolean useRootCall) {
            super(callee, nodeBytecodeIndex, explicitBytecodeIndex, useExplicitCall);
            this.useRootCall = useRootCall;
        }

        @Override
        protected Node getActiveCallNode() {
            return directCall;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setObject(SLOT, marker);
            if (directCall == null || directCall.getCallTarget() != callee) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                directCall = insert(Truffle.getRuntime().createDirectCallNode(callee));
            }
            if (useExplicitCall) {
                if (useRootCall) {
                    return directCall.call(CalleeRootNode.class, explicitBytecodeIndex, new Object[0]);
                }
                return directCall.call(explicitBytecodeIndex, new Object[0]);
            }
            return directCall.call(new Object[0]);
        }
    }

    private static final class IndirectCallerRootNode extends CallNodeCallerRootNode {
        @Child IndirectCallNode indirectCall = Truffle.getRuntime().createIndirectCallNode();
        final boolean useRootCall;

        IndirectCallerRootNode(CallTarget callee, int nodeBytecodeIndex, int explicitBytecodeIndex, boolean useExplicitCall) {
            this(callee, nodeBytecodeIndex, explicitBytecodeIndex, useExplicitCall, false);
        }

        IndirectCallerRootNode(CallTarget callee, int nodeBytecodeIndex, int explicitBytecodeIndex, boolean useExplicitCall, boolean useRootCall) {
            super(callee, nodeBytecodeIndex, explicitBytecodeIndex, useExplicitCall);
            this.useRootCall = useRootCall;
        }

        @Override
        protected Node getActiveCallNode() {
            return indirectCall;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setObject(SLOT, marker);
            if (useExplicitCall) {
                if (useRootCall) {
                    CalleeRootNode root = (CalleeRootNode) ((RootCallTarget) callee).getRootNode();
                    return indirectCall.call(root, explicitBytecodeIndex, new Object[0]);
                }
                return indirectCall.call(callee, explicitBytecodeIndex, new Object[0]);
            }
            return indirectCall.call(callee, new Object[0]);
        }
    }

    private static final class UncachedIndirectCallerRootNode extends BytecodeIndexRootNode {
        final int explicitBytecodeIndex;
        final CalleeRootNode callee;
        final boolean useRootCall;

        UncachedIndirectCallerRootNode(CalleeRootNode callee, int nodeBytecodeIndex, int explicitBytecodeIndex, boolean useRootCall) {
            super(nodeBytecodeIndex);
            this.callee = callee;
            this.explicitBytecodeIndex = explicitBytecodeIndex;
            this.useRootCall = useRootCall;
        }

        @Override
        protected Node findInstrumentableCallNode(Node callNode, Frame frame, int bytecodeIndex) {
            if (callNode == callLocation && bytecodeIndex == explicitBytecodeIndex) {
                return instrumentableCallNode;
            }
            return callNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setObject(SLOT, marker);
            EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
            Node previous = encapsulating.set(callLocation);
            try {
                if (useRootCall) {
                    return IndirectCallNode.getUncached().call(callee, explicitBytecodeIndex, new Object[0]);
                }
                return IndirectCallNode.getUncached().call(callee.getCallTarget(), explicitBytecodeIndex, new Object[0]);
            } finally {
                encapsulating.set(previous);
            }
        }
    }

    private static final class CalleeRootNode extends BytecodeIndexRootNode {
        private final FrameRecorder recorder = new FrameRecorder(SLOT);

        CalleeRootNode() {
            super(99);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setObject(SLOT, marker);
            return recorder.capture();
        }
    }

    private static final class ExceptionRootNode extends BytecodeIndexRootNode {
        final int exceptionBytecodeIndex;

        ExceptionRootNode(int nodeBytecodeIndex, int exceptionBytecodeIndex, SourceSection sourceSection) {
            super(nodeBytecodeIndex, sourceSection);
            this.exceptionBytecodeIndex = exceptionBytecodeIndex;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw new TestException(callLocation, exceptionBytecodeIndex);
        }
    }

    @SuppressWarnings("serial")
    private static final class TestException extends AbstractTruffleException {
        TestException(Node location, int bytecodeIndex) {
            super("test", null, AbstractTruffleException.UNLIMITED_STACK_TRACE, location, bytecodeIndex);
        }
    }

    private static final class TestCallLocationNode extends Node {
        final int bytecodeIndex;
        final SourceSection sourceSection;

        TestCallLocationNode(int bytecodeIndex, SourceSection sourceSection) {
            this.bytecodeIndex = bytecodeIndex;
            this.sourceSection = sourceSection;
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }
    }
}
