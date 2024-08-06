/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/**
 * <h3>Creating a Root Node</h3>
 *
 * <p>
 * A Truffle root node is the entry point into a Truffle tree that represents a guest language
 * method. It contains a {@link RootNode#execute(VirtualFrame)} method that can return a
 * {@link java.lang.Object} value as the result of the guest language method invocation. This method
 * must however never be called directly. Instead, its {@link CallTarget} object must be obtained
 * via {@link RootNode#getCallTarget()}. This call target object can then be executed using the
 * {@link CallTarget#call(Object...)} method or one of its overloads.
 * </p>
 *
 * <p>
 * The next part of the Truffle API introduction is at
 * {@link com.oracle.truffle.api.test.ChildNodeTest}.
 * </p>
 */
public class RootNodeTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void test() {
        Object result = new TestCopyAndReplaceRootNode().getCallTarget().call();
        Assert.assertEquals(42, result);
    }

    @Test
    public void testCopy() {
        TestCopyAndReplaceRootNode originalRoot = new TestCopyAndReplaceRootNode(new FrameDescriptor());
        // Trigger the lazy initialization
        TestAPIAccessor.nodeAccess().getLock(originalRoot);
        originalRoot.getCallTarget();
        TestAPIAccessor.nodeAccess().setRootNodeBits(originalRoot, 1);

        Node copy = originalRoot.copy();
        assertThat(copy, instanceOf(TestCopyAndReplaceRootNode.class));

        TestCopyAndReplaceRootNode rootCopy = (TestCopyAndReplaceRootNode) copy;
        assertEquals(originalRoot.getFrameDescriptor(), rootCopy.getFrameDescriptor());
        assertNull(TestAPIAccessor.nodeAccess().getCallTargetWithoutInitialization(rootCopy));
        assertNotEquals(TestAPIAccessor.nodeAccess().getLock(originalRoot),
                        TestAPIAccessor.nodeAccess().getLock(rootCopy));
        assertEquals(0, TestAPIAccessor.nodeAccess().getRootNodeBits(rootCopy));
    }

    @Test(expected = IllegalStateException.class)
    public void testNotReplacable1() {
        TestCopyAndReplaceRootNode rootNode = new TestCopyAndReplaceRootNode();
        rootNode.replace(rootNode);
    }

    class TestNonReplacableRootNode extends RootNode {

        @Child Node rootNodeAsChild;

        TestNonReplacableRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return 42;
        }
    }

    class TestCopyAndReplaceRootNode extends RootNode {

        TestCopyAndReplaceRootNode() {
            super(null);
        }

        TestCopyAndReplaceRootNode(FrameDescriptor frameDescriptor) {
            super(null, frameDescriptor);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return 42;
        }
    }

    @Test
    public void testNotCapturingFrames() {
        TestStackTraceRootNode rootNode = new TestStackTraceRootNode(false);
        Object marker = new Object();
        try {
            rootNode.getCallTarget().call(marker);
            Assert.fail();
        } catch (TestException e) {
            List<TruffleStackTraceElement> stackTrace = TruffleStackTrace.getStackTrace(e);
            Assert.assertEquals(1, stackTrace.size());
            assertNull(stackTrace.get(0).getLocation());
            Assert.assertEquals(rootNode.getCallTarget(), stackTrace.get(0).getTarget());
            assertNull(stackTrace.get(0).getFrame());
        }
    }

    @Test
    public void testCapturingFrames() {
        TestStackTraceRootNode rootNode = new TestStackTraceRootNode(true);
        Object marker = new Object();
        try {
            rootNode.getCallTarget().call(marker);
            Assert.fail();
        } catch (TestException e) {
            asserCapturedFrames(rootNode, marker, e, e.frame);
        }
    }

    @Test
    public void testTranslateStackTraceElementNotEntered() {
        RootNode rootNode = new TestStackTraceRootNode(true);
        try {
            rootNode.getCallTarget().call();
            Assert.fail();
        } catch (TestException e) {
            TruffleStackTraceElement stackTraceElement = getStackTraceElementFor(e, rootNode);
            Assert.assertNotNull(stackTraceElement);
            AbstractPolyglotTest.assertFails(() -> stackTraceElement.getGuestObject(), AssertionError.class);
        }
    }

    @Test
    public void testTranslateStackTraceElementEntered() throws UnsupportedMessageException {
        try (Context ctx = Context.create()) {
            ctx.enter();
            RootNode rootNode = new TestStackTraceRootNode(true);
            try {
                rootNode.getCallTarget().call();
                Assert.fail();
            } catch (TestException e) {
                TruffleStackTraceElement stackTraceElement = getStackTraceElementFor(e, rootNode);
                Assert.assertNotNull(stackTraceElement);
                Object guestObject = stackTraceElement.getGuestObject();
                verifyStackTraceElementGuestObject(guestObject);
            }
        }
    }

    static final class TestStackTraceRootNode extends RootNode {
        private boolean shouldCaptureFrames;

        TestStackTraceRootNode(boolean shouldCaptureFrames) {
            super(null);
            this.shouldCaptureFrames = shouldCaptureFrames;
        }

        @Override
        public boolean isCaptureFramesForTrace(boolean compiled) {
            return this.shouldCaptureFrames;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw new TestException(frame, null);
        }
    }

    @Test
    public void testTranslateStackTraceElementCustomGuestObject() throws UnsupportedMessageException {
        try (Context ctx = Context.create()) {
            ctx.enter();
            testTranslateStackTraceElementCustomGuestObjectImpl(true, true, true, true);
            testTranslateStackTraceElementCustomGuestObjectImpl(true, false, true, false);
            testTranslateStackTraceElementCustomGuestObjectImpl(false, true, false, true);
            testTranslateStackTraceElementCustomGuestObjectImpl(false, false, false, false);
        }
    }

    private static void testTranslateStackTraceElementCustomGuestObjectImpl(boolean hasExecutableName,
                    boolean hasDeclaringMetaObject, boolean isString, boolean isMetaObject) throws UnsupportedMessageException {
        RootNode rootNode = new TestTranslateStackTraceRootNode(hasExecutableName, hasDeclaringMetaObject, isString, isMetaObject);
        try {
            rootNode.getCallTarget().call();
            Assert.fail();
        } catch (TestException e) {
            TruffleStackTraceElement stackTraceElement = getStackTraceElementFor(e, rootNode);
            Assert.assertNotNull(stackTraceElement);
            Object guestObject = stackTraceElement.getGuestObject();
            verifyStackTraceElementGuestObject(guestObject);
        }
    }

    @Test
    public void testTranslateStackTraceElementInvalidCustomGuestObject() {
        try (Context ctx = Context.create()) {
            ctx.enter();
            testTranslateStackTraceElementInvalidCustomGuestObjectImpl(true, true, false, true);
            testTranslateStackTraceElementInvalidCustomGuestObjectImpl(true, true, true, false);
        }
    }

    private static void testTranslateStackTraceElementInvalidCustomGuestObjectImpl(boolean hasExecutableName,
                    boolean hasDeclaringMetaObject, boolean isString, boolean isMetaObject) {
        RootNode rootNode = new TestTranslateStackTraceRootNode(hasExecutableName, hasDeclaringMetaObject, isString, isMetaObject);
        try {
            rootNode.getCallTarget().call();
            Assert.fail();
        } catch (TestException e) {
            TruffleStackTraceElement stackTraceElement = getStackTraceElementFor(e, rootNode);
            Assert.assertNotNull(stackTraceElement);
            AbstractPolyglotTest.assertFails(() -> stackTraceElement.getGuestObject(), AssertionError.class);
        }
    }

    static final class TestTranslateStackTraceRootNode extends RootNode {

        private final TruffleStackTraceElementGuestObject truffleStackTraceElementGuestObject;

        TestTranslateStackTraceRootNode(boolean hasExecutableName, boolean hasDeclaringMetaObject, boolean isString, boolean isMetaObject) {
            super(null);
            truffleStackTraceElementGuestObject = new TruffleStackTraceElementGuestObject(hasExecutableName, hasDeclaringMetaObject, isString, isMetaObject);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw new TestException(frame, null);
        }

        @Override
        protected Object translateStackTraceElement(TruffleStackTraceElement element) {
            return truffleStackTraceElementGuestObject;
        }

        @ExportLibrary(InteropLibrary.class)
        static final class TruffleStackTraceElementGuestObject implements TruffleObject {

            private static final TruffleObject EMPTY = new TruffleObject() {
            };

            private final boolean hasExecutableName;
            private final boolean hasDeclaringMetaObject;
            private final boolean isString;
            private final boolean isMetaObject;

            TruffleStackTraceElementGuestObject(boolean hasExecutableName, boolean hasDeclaringMetaObject, boolean isString, boolean isMetaObject) {
                this.hasExecutableName = hasExecutableName;
                this.hasDeclaringMetaObject = hasDeclaringMetaObject;
                this.isString = isString;
                this.isMetaObject = isMetaObject;
            }

            @ExportMessage
            boolean hasExecutableName() {
                return hasExecutableName;
            }

            @ExportMessage
            Object getExecutableName() throws UnsupportedMessageException {
                if (!hasExecutableName) {
                    throw UnsupportedMessageException.create();
                }
                return isString ? "main" : EMPTY;
            }

            @ExportMessage
            boolean hasDeclaringMetaObject() {
                return hasDeclaringMetaObject;
            }

            @ExportMessage
            Object getDeclaringMetaObject() throws UnsupportedMessageException {
                if (!hasDeclaringMetaObject) {
                    throw UnsupportedMessageException.create();
                }
                return isMetaObject ? MetaObject.INSTANCE : EMPTY;
            }

            @ExportLibrary(InteropLibrary.class)
            static final class MetaObject implements TruffleObject {

                static final MetaObject INSTANCE = new MetaObject();

                private MetaObject() {
                }

                @SuppressWarnings("static-method")
                @ExportMessage
                boolean isMetaObject() {
                    return true;
                }

                @SuppressWarnings({"static-method", "unused"})
                @ExportMessage
                boolean isMetaInstance(Object object) {
                    return false;
                }

                @SuppressWarnings("static-method")
                @ExportMessage
                Object getMetaQualifiedName() {
                    return "std";
                }

                @SuppressWarnings("static-method")
                @ExportMessage
                Object getMetaSimpleName() {
                    return "std";
                }
            }
        }
    }

    private static TruffleStackTraceElement getStackTraceElementFor(Throwable t, RootNode rootNode) {
        for (TruffleStackTraceElement stackTraceElement : TruffleStackTrace.getStackTrace(t)) {
            if (rootNode == stackTraceElement.getTarget().getRootNode()) {
                return stackTraceElement;
            }
        }
        return null;
    }

    static void verifyStackTraceElementGuestObject(Object guestObject) throws UnsupportedMessageException {
        Assert.assertNotNull(guestObject);
        InteropLibrary interop = InteropLibrary.getUncached();
        if (interop.hasExecutableName(guestObject)) {
            Object executableName = interop.getExecutableName(guestObject);
            Assert.assertTrue(interop.isString(executableName));
        } else {
            AbstractPolyglotTest.assertFails(() -> interop.getExecutableName(guestObject), UnsupportedMessageException.class);
        }
        if (interop.hasDeclaringMetaObject(guestObject)) {
            Object metaObject = interop.getDeclaringMetaObject(guestObject);
            Assert.assertTrue(interop.isMetaObject(metaObject));
        }
    }

    private static void asserCapturedFrames(RootNode rootNode, Object arg, Throwable e, MaterializedFrame frame) {
        List<TruffleStackTraceElement> stackTrace = TruffleStackTrace.getStackTrace(e);
        Assert.assertEquals(1, stackTrace.size());
        assertNull(stackTrace.get(0).getLocation());
        Assert.assertEquals(rootNode.getCallTarget(), stackTrace.get(0).getTarget());
        Assert.assertNotNull(stackTrace.get(0).getFrame());
        Assert.assertEquals(1, stackTrace.get(0).getFrame().getArguments().length);
        Assert.assertEquals(arg, stackTrace.get(0).getFrame().getArguments()[0]);
        Assert.assertEquals(arg, frame.getArguments()[0]);
    }

    @SuppressWarnings("serial")
    static final class TestException extends AbstractTruffleException {
        MaterializedFrame frame;

        TestException(VirtualFrame frame, Node location) {
            super(location);
            this.frame = frame.materialize();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNodeBasedBytecodeIndex() {
        var result = (List<TruffleStackTraceElement>) new NodeBasedBytecodeIndexRootNode().getCallTarget().call();

        assertEquals(1, result.size());
        assertEquals(42, result.get(0).getBytecodeIndex());
        assertTrue(result.get(0).hasBytecodeIndex());
    }

    static class NodeWithBytecode extends Node {

        int bytecodeIndex;

        NodeWithBytecode(int bytecodeIndex) {
            this.bytecodeIndex = bytecodeIndex;
        }

    }

    static final class NodeBasedBytecodeIndexRootNode extends RootNode {

        NodeBasedBytecodeIndexRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return boundary(frame.materialize());
        }

        @TruffleBoundary
        private static Object boundary(MaterializedFrame frame) {
            NodeWithBytecode node = new NodeWithBytecode(42);
            var stackTrace = TruffleStackTrace.getStackTrace(new TestException(frame, node));

            assertTrue(Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Boolean>() {
                @Override
                public Boolean visitFrame(FrameInstance frameInstance) {
                    assertEquals(-3, frameInstance.getBytecodeIndex());
                    return true;
                }
            }));

            return stackTrace;
        }

        @Override
        protected int findBytecodeIndex(Node node, Frame frame) {
            assertNull(frame);
            if (node instanceof NodeWithBytecode n) {
                return n.bytecodeIndex;
            }
            return -3;
        }

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFrameBasedBytecodeIndex() {
        var result = (List<TruffleStackTraceElement>) new FrameBasedBytecodeIndexRootNode().getCallTarget().call();

        assertEquals(1, result.size());
        assertEquals(42, result.get(0).getBytecodeIndex());
        assertTrue(result.get(0).hasBytecodeIndex());

    }

    static final class FrameBasedBytecodeIndexRootNode extends RootNode {

        FrameBasedBytecodeIndexRootNode() {
            super(null, createFrameDescriptor());
        }

        static FrameDescriptor createFrameDescriptor() {
            var builder = FrameDescriptor.newBuilder();
            builder.addSlots(1, FrameSlotKind.Int);
            return builder.build();
        }

        @Override
        public boolean isCaptureFramesForTrace(boolean compiled) {
            return true;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return boundary(frame.materialize());
        }

        @TruffleBoundary
        private static List<TruffleStackTraceElement> boundary(MaterializedFrame frame) {
            frame.setInt(0, 42);
            var stackTrace = TruffleStackTrace.getStackTrace(new TestException(frame, null));
            assertTrue(Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Boolean>() {
                @Override
                public Boolean visitFrame(FrameInstance frameInstance) {
                    assertEquals(42, frameInstance.getBytecodeIndex());
                    return true;
                }
            }));

            frame.setInt(0, 43);

            assertTrue(Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Boolean>() {
                @Override
                public Boolean visitFrame(FrameInstance frameInstance) {
                    assertEquals(43, frameInstance.getBytecodeIndex());
                    return true;
                }
            }));
            return stackTrace;
        }

        @Override
        protected int findBytecodeIndex(Node node, Frame frame) {
            assertNull(node);
            return frame.getInt(0);
        }

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHybridBytecodeIndex() {
        var result = (List<TruffleStackTraceElement>) new FrameHybridBytecodeIndexRootNode().getCallTarget().call();
        assertEquals(1, result.size());
        assertEquals(42, result.get(0).getBytecodeIndex());
        assertTrue(result.get(0).hasBytecodeIndex());

    }

    static final class FrameHybridBytecodeIndexRootNode extends RootNode {

        FrameHybridBytecodeIndexRootNode() {
            super(null, createFrameDescriptor());
        }

        static FrameDescriptor createFrameDescriptor() {
            var builder = FrameDescriptor.newBuilder();
            builder.addSlots(1, FrameSlotKind.Int);
            return builder.build();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return boundary(frame.materialize(), CompilerDirectives.inCompiledCode());
        }

        @TruffleBoundary
        private static List<TruffleStackTraceElement> boundary(MaterializedFrame frame, boolean compiled) {
            Node node;
            if (compiled) {
                frame.setInt(0, 43);
                node = new NodeWithBytecode(42);
            } else {
                frame.setInt(0, 42);
                node = null;
            }

            var stackTrace = TruffleStackTrace.getStackTrace(new TestException(frame, node));
            assertTrue(Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Boolean>() {
                @Override
                public Boolean visitFrame(FrameInstance frameInstance) {
                    if (frameInstance.getCompilationTier() == 0) {
                        assertEquals(42, frameInstance.getBytecodeIndex());
                    } else {
                        assertEquals(-1, frameInstance.getBytecodeIndex());
                    }
                    return true;
                }
            }));
            return stackTrace;
        }

        @Override
        public boolean isCaptureFramesForTrace(boolean compiled) {
            return !compiled;
        }

        @Override
        protected int findBytecodeIndex(Node node, Frame frame) {
            if (frame != null) {
                return frame.getInt(0);
            } else if (node instanceof NodeWithBytecode n) {
                assertNull(frame);
                return n.bytecodeIndex;
            }
            return -1;
        }

    }

}
