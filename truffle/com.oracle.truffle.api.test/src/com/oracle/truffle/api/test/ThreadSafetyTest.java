/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Test node rewriting in a tree shared across multiple threads (run with -ea).
 */
public class ThreadSafetyTest {

    @Test
    @Ignore("sporadic failures with \"expected:<1000000> but was:<999999>\"")
    public void test() throws InterruptedException {
        TruffleRuntime runtime = Truffle.getRuntime();
        TestRootNode rootNode1 = new TestRootNode(new RewritingNode(new RewritingNode(new RewritingNode(new RewritingNode(new RewritingNode(new ConstNode(42)))))));
        final CallTarget target1 = runtime.createCallTarget(rootNode1);
        NodeUtil.verify(rootNode1);

        RecursiveCallNode callNode = new RecursiveCallNode(new ConstNode(42));
        TestRootNode rootNode2 = new TestRootNode(new RewritingNode(new RewritingNode(new RewritingNode(new RewritingNode(new RewritingNode(callNode))))));
        final CallTarget target2 = runtime.createCallTarget(rootNode2);
        callNode.setCallNode(runtime.createDirectCallNode(target2));
        NodeUtil.verify(rootNode2);

        testTarget(target1, 47, 1_000_000);
        testTarget(target2, 72, 1_000_000);
    }

    private static void testTarget(final CallTarget target, final int expectedResult, final int numberOfIterations) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        final AtomicInteger ai = new AtomicInteger();
        for (int i = 0; i < numberOfIterations; i++) {
            executorService.submit(new Runnable() {
                public void run() {
                    try {
                        Object result = target.call(new Object[]{5});
                        assertEquals(expectedResult, result);
                        ai.incrementAndGet();
                    } catch (Throwable t) {
                        t.printStackTrace(System.out);
                    }
                }
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(90, TimeUnit.SECONDS);
        assertTrue("test did not terminate", executorService.isTerminated());
        assertEquals(numberOfIterations, ai.get());
    }

    static class TestRootNode extends RootNode {

        @Child private ValueNode child;

        public TestRootNode(ValueNode child) {
            super(null);
            this.child = child;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return child.execute(frame);
        }
    }

    abstract static class ValueNode extends Node {

        public ValueNode() {
            super(null);
        }

        abstract int execute(VirtualFrame frame);
    }

    static class RewritingNode extends ValueNode {

        @Child private ValueNode child;
        private final Random random;

        public RewritingNode(ValueNode child) {
            this(child, new Random());
        }

        public RewritingNode(ValueNode child, Random random) {
            this.child = child;
            this.random = random;
        }

        @Override
        int execute(VirtualFrame frame) {
            boolean replace = random.nextBoolean();
            if (replace) {
                ValueNode newNode = this.replace(new OtherRewritingNode(child, random));
                return newNode.execute(frame);
            }
            return 1 + child.execute(frame);
        }
    }

    static class OtherRewritingNode extends ValueNode {

        @Child private ValueNode child;
        private final Random random;

        public OtherRewritingNode(ValueNode child, Random random) {
            this.child = child;
            this.random = random;
        }

        @Override
        int execute(VirtualFrame frame) {
            boolean replace = random.nextBoolean();
            if (replace) {
                ValueNode newNode = this.replace(new RewritingNode(child, random));
                return newNode.execute(frame);
            }
            return 1 + child.execute(frame);
        }
    }

    static class ConstNode extends ValueNode {

        private final int value;

        ConstNode(int value) {
            this.value = value;
        }

        @Override
        int execute(VirtualFrame frame) {
            return value;
        }
    }

    static class RecursiveCallNode extends ValueNode {
        @Child DirectCallNode callNode;
        @Child private ValueNode valueNode;

        RecursiveCallNode(ValueNode value) {
            this.valueNode = value;
        }

        @Override
        int execute(VirtualFrame frame) {
            int arg = (Integer) frame.getArguments()[0];
            if (arg > 0) {
                return (int) callNode.call(frame, new Object[]{(arg - 1)});
            } else {
                return valueNode.execute(frame);
            }
        }

        void setCallNode(DirectCallNode callNode) {
            this.callNode = insert(callNode);
        }
    }
}
