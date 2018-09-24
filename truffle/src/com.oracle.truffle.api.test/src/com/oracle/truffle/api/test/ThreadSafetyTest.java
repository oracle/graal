/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

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

        TestRootNode(ValueNode child) {
            super(null);
            this.child = child;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return child.execute(frame);
        }
    }

    abstract static class ValueNode extends Node {

        ValueNode() {
        }

        abstract int execute(VirtualFrame frame);
    }

    static class RewritingNode extends ValueNode {

        @Child private ValueNode child;
        private final Random random;

        RewritingNode(ValueNode child) {
            this(child, new Random());
        }

        RewritingNode(ValueNode child, Random random) {
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

        OtherRewritingNode(ValueNode child, Random random) {
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
                return (int) callNode.call(new Object[]{(arg - 1)});
            } else {
                return valueNode.execute(frame);
            }
        }

        void setCallNode(DirectCallNode callNode) {
            this.callNode = insert(callNode);
        }
    }
}
