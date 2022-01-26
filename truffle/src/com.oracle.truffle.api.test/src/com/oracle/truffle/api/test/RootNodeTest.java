/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
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
        Object result = new TestRootNode().getCallTarget().call();
        Assert.assertEquals(42, result);
    }

    @Test
    public void testCopy() {
        TestRootNode originalRoot = new TestRootNode(new FrameDescriptor());
        // Trigger the lazy initialization
        TestAPIAccessor.nodeAccess().getLock(originalRoot);
        originalRoot.getCallTarget();
        TestAPIAccessor.nodeAccess().setRootNodeBits(originalRoot, 1);

        Node copy = originalRoot.copy();
        assertThat(copy, instanceOf(TestRootNode.class));

        TestRootNode rootCopy = (TestRootNode) copy;
        assertEquals(originalRoot.getFrameDescriptor(), rootCopy.getFrameDescriptor());
        assertNull(TestAPIAccessor.nodeAccess().getCallTargetWithoutInitialization(rootCopy));
        assertNotEquals(TestAPIAccessor.nodeAccess().getLock(originalRoot),
                        TestAPIAccessor.nodeAccess().getLock(rootCopy));
        assertEquals(0, TestAPIAccessor.nodeAccess().getRootNodeBits(rootCopy));
    }

    @Test(expected = IllegalStateException.class)
    public void testNotReplacable1() {
        TestRootNode rootNode = new TestRootNode();
        rootNode.replace(rootNode);
    }

    @Test(expected = IllegalStateException.class)
    @Ignore
    public void testNotReplacable2() {
        TestRootNode2 rootNode = new TestRootNode2();
        rootNode.rootNodeAsChild = new TestRootNode();
        rootNode.adoptChildren();
    }

    @Test(expected = IllegalStateException.class)
    @Ignore
    public void testNotReplacable3() {
        TestRootNode2 rootNode = new TestRootNode2();
        rootNode.rootNodeAsChild = new Node() {
        };
        rootNode.adoptChildren();
        rootNode.rootNodeAsChild.replace(new TestRootNode());
    }

    @Test
    public void testNotCapturingFrames() {
        TestRootNode3 rootNode = new TestRootNode3(false);
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
        TestRootNode3 rootNode = new TestRootNode3(true);
        Object marker = new Object();
        try {
            rootNode.getCallTarget().call(marker);
            Assert.fail();
        } catch (TestException e) {
            asserCapturedFrames(rootNode, marker, e, e.frame);
        }
    }

    @Test
    public void testCapturingFramesLegacyException() {
        TestRootNode4 rootNode = new TestRootNode4(true);
        Object marker = new Object();
        try {
            rootNode.getCallTarget().call(marker);
            Assert.fail();
        } catch (LegacyTestException e) {
            asserCapturedFrames(rootNode, marker, e, e.frame);
        }
    }

    @Test
    public void testTranslateStackTraceElementNotEntered() {
        RootNode rootNode = new TestRootNode3(true);
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
            RootNode rootNode = new TestRootNode3(true);
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
        RootNode rootNode = new TestRootNode5(hasExecutableName, hasDeclaringMetaObject, isString, isMetaObject);
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
        RootNode rootNode = new TestRootNode5(hasExecutableName, hasDeclaringMetaObject, isString, isMetaObject);
        try {
            rootNode.getCallTarget().call();
            Assert.fail();
        } catch (TestException e) {
            TruffleStackTraceElement stackTraceElement = getStackTraceElementFor(e, rootNode);
            Assert.assertNotNull(stackTraceElement);
            AbstractPolyglotTest.assertFails(() -> stackTraceElement.getGuestObject(), AssertionError.class);
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

    class TestRootNode extends RootNode {

        TestRootNode() {
            super(null);
        }

        TestRootNode(FrameDescriptor frameDescriptor) {
            super(null, frameDescriptor);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return 42;
        }
    }

    class TestRootNode2 extends RootNode {

        @Child Node rootNodeAsChild;

        TestRootNode2() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return 42;
        }
    }

    @SuppressWarnings("serial")
    static final class TestException extends AbstractTruffleException {
        MaterializedFrame frame;

        TestException(VirtualFrame frame) {
            this.frame = frame.materialize();
        }
    }

    static final class TestRootNode3 extends RootNode {
        private boolean shouldCaptureFrames;

        TestRootNode3(boolean shouldCaptureFrames) {
            super(null);
            this.shouldCaptureFrames = shouldCaptureFrames;
        }

        @Override
        public boolean isCaptureFramesForTrace() {
            return this.shouldCaptureFrames;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw new TestException(frame);
        }
    }

    @SuppressWarnings({"serial", "deprecation"})
    static final class LegacyTestException extends RuntimeException implements com.oracle.truffle.api.TruffleException {
        MaterializedFrame frame;

        LegacyTestException(VirtualFrame frame) {
            this.frame = frame.materialize();
        }

        @Override
        public Node getLocation() {
            return null;
        }
    }

    static final class TestRootNode4 extends RootNode {
        private boolean shouldCaptureFrames;

        TestRootNode4(boolean shouldCaptureFrames) {
            super(null);
            this.shouldCaptureFrames = shouldCaptureFrames;
        }

        @Override
        public boolean isCaptureFramesForTrace() {
            return this.shouldCaptureFrames;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw new LegacyTestException(frame);
        }
    }

    static final class TestRootNode5 extends RootNode {

        private final TruffleStackTraceElementGuestObject truffleStackTraceElementGuestObject;

        TestRootNode5(boolean hasExecutableName, boolean hasDeclaringMetaObject, boolean isString, boolean isMetaObject) {
            super(null);
            truffleStackTraceElementGuestObject = new TruffleStackTraceElementGuestObject(hasExecutableName, hasDeclaringMetaObject, isString, isMetaObject);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw new TestException(frame);
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
}
