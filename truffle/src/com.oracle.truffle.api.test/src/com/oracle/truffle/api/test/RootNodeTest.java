/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * <h3>Creating a Root Node</h3>
 *
 * <p>
 * A Truffle root node is the entry point into a Truffle tree that represents a guest language
 * method. It contains a {@link RootNode#execute(VirtualFrame)} method that can return a
 * {@link java.lang.Object} value as the result of the guest language method invocation. This method
 * must however never be called directly. Instead, the Truffle runtime must be used to create a
 * {@link CallTarget} object from a root node using the
 * {@link TruffleRuntime#createCallTarget(RootNode)} method. This call target object can then be
 * executed using the {@link CallTarget#call(Object...)} method or one of its overloads.
 * </p>
 *
 * <p>
 * The next part of the Truffle API introduction is at
 * {@link com.oracle.truffle.api.test.ChildNodeTest}.
 * </p>
 */
public class RootNodeTest {

    @Test
    public void test() {
        TruffleRuntime runtime = Truffle.getRuntime();
        TestRootNode rootNode = new TestRootNode();
        CallTarget target = runtime.createCallTarget(rootNode);
        Object result = target.call();
        Assert.assertEquals(42, result);
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
            Truffle.getRuntime().createCallTarget(rootNode).call(marker);
        } catch (TestException e) {
            List<TruffleStackTraceElement> stackTrace = TruffleStackTrace.getStackTrace(e);
            Assert.assertEquals(1, stackTrace.size());
            Assert.assertNull(stackTrace.get(0).getLocation());
            Assert.assertEquals(rootNode.getCallTarget(), stackTrace.get(0).getTarget());
            Assert.assertNull(stackTrace.get(0).getFrame());
        }
    }

    @Test
    public void testCapturingFrames() {
        TestRootNode3 rootNode = new TestRootNode3(true);
        Object marker = new Object();
        try {
            Truffle.getRuntime().createCallTarget(rootNode).call(marker);
        } catch (TestException e) {
            MaterializedFrame frame = e.frame;
            List<TruffleStackTraceElement> stackTrace = TruffleStackTrace.getStackTrace(e);
            Assert.assertEquals(1, stackTrace.size());
            Assert.assertNull(stackTrace.get(0).getLocation());
            Assert.assertEquals(rootNode.getCallTarget(), stackTrace.get(0).getTarget());
            Assert.assertNotNull(stackTrace.get(0).getFrame());
            Assert.assertEquals(1, stackTrace.get(0).getFrame().getArguments().length);
            Assert.assertEquals(marker, stackTrace.get(0).getFrame().getArguments()[0]);
            Assert.assertEquals(marker, frame.getArguments()[0]);
        }
    }

    class TestRootNode extends RootNode {

        TestRootNode() {
            super(null);
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
    class TestException extends RuntimeException implements TruffleException {
        MaterializedFrame frame;

        TestException(VirtualFrame frame) {
            this.frame = frame.materialize();
        }

        public Node getLocation() {
            return null;
        }
    }

    class TestRootNode3 extends RootNode {
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
}
