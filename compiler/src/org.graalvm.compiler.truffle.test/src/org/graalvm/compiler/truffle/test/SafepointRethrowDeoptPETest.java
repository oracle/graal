/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.util.concurrent.CountDownLatch;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class SafepointRethrowDeoptPETest extends PartialEvaluationTest {

    static final Object RETURN_VALUE = "1 2 3";
    static final RuntimeException BREAK_EX = new RuntimeException();
    static final RuntimeException CONTINUE_EX = new RuntimeException();
    static volatile int terminate;
    static volatile int entered;

    public static class Test0RootNode extends RootNode {
        public Test0RootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            entered = 1;
            for (;;) {
                try {
                    if (terminate != 0) {
                        throw BREAK_EX;
                    } else {
                        throw CONTINUE_EX;
                    }
                } catch (RuntimeException e) {
                    if (e == BREAK_EX) {
                        break;
                    } else if (e == CONTINUE_EX) {
                        continue;
                    }
                    throw e;
                }
            }
            return RETURN_VALUE;
        }
    }

    public abstract static class TestNode extends Node {
        public abstract void executeVoid();
    }

    public static class ThrowNode extends TestNode {
        private final RuntimeException exception;

        public ThrowNode(RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public void executeVoid() {
            throw exception;
        }
    }

    public static class Test1RootNode extends RootNode {
        @Child private ThrowNode throwBreak = new ThrowNode(BREAK_EX);
        @Child private ThrowNode throwContinue = new ThrowNode(CONTINUE_EX);

        public Test1RootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            entered = 1;
            for (;;) {
                try {
                    if (terminate != 0) {
                        throwBreak.executeVoid();
                    } else {
                        throwContinue.executeVoid();
                    }
                } catch (RuntimeException e) {
                    if (e == BREAK_EX) {
                        break;
                    } else if (e == CONTINUE_EX) {
                        continue;
                    }
                    throw e;
                }
            }
            return RETURN_VALUE;
        }
    }

    public static class BreakOrContinueNode extends TestNode {
        @Child private ThrowNode throwBreak = new ThrowNode(BREAK_EX);
        @Child private ThrowNode throwContinue = new ThrowNode(CONTINUE_EX);

        @Override
        public void executeVoid() {
            if (terminate != 0) {
                throwBreak.executeVoid();
            } else {
                throwContinue.executeVoid();
            }
        }
    }

    public static class ExceptionTargetNode extends TestNode {
        @Child private TestNode body;
        private final RuntimeException exception;

        public ExceptionTargetNode(RuntimeException exception, TestNode body) {
            this.body = body;
            this.exception = exception;
        }

        @Override
        public void executeVoid() {
            try {
                body.executeVoid();
            } catch (RuntimeException e) {
                if (e != exception) {
                    throw e;
                }
            }
        }
    }

    public static class LoopNode extends TestNode {
        @Child private TestNode body;

        public LoopNode(TestNode body) {
            this.body = body;
        }

        @Override
        public void executeVoid() {
            for (;;) {
                body.executeVoid();
            }
        }
    }

    public static class Test2RootNode extends RootNode {
        @Child private TestNode body;

        public Test2RootNode() {
            super(null);
            this.body = new ExceptionTargetNode(BREAK_EX, new LoopNode(new ExceptionTargetNode(CONTINUE_EX, new BreakOrContinueNode())));
        }

        @Override
        public Object execute(VirtualFrame frame) {
            entered = 1;
            body.executeVoid();
            return RETURN_VALUE;
        }
    }

    @Test
    public void test() {
        Assume.assumeTrue(GraalOptions.GenLoopSafepoints.getValue(getInitialOptions()));
        synchronized (SafepointRethrowDeoptPETest.class) { // safeguard static fields
            testInner(new Test0RootNode());
            testInner(new Test1RootNode());
            testInner(new Test2RootNode());
        }
    }

    private void testInner(RootNode rootNode) {
        terminate = 1; // executed 3 times
        OptimizedCallTarget compiledMethod = compileHelper(rootNode.toString(), rootNode, new Object[0]);

        terminate = 0;
        entered = 0;
        CountDownLatch cdl = new CountDownLatch(1);
        Thread t1 = new Thread(() -> {
            try {
                cdl.await();
                while (entered == 0) {
                    /* spin */
                }
                /* Thread.sleep(100); */
                compiledMethod.invalidate(cdl, "timed out");
            } catch (InterruptedException e) {
                Assert.fail("interrupted");
            }
            terminate = 1;
        });
        Thread t2 = new Thread(() -> {
            cdl.countDown();
            Object result = compiledMethod.call();
            Assert.assertEquals(RETURN_VALUE, result);
        });

        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            Assert.fail("interrupted");
        }
    }
}
