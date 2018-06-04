/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleRuntime;
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
            List<TruffleStackTraceElement> stackTrace = TruffleStackTraceElement.getStackTrace(e);
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
            List<TruffleStackTraceElement> stackTrace = TruffleStackTraceElement.getStackTrace(e);
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
