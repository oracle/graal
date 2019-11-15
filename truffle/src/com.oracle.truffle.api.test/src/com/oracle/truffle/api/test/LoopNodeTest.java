/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

public class LoopNodeTest {

    @Test
    public void testHundredInvocations() {
        IterateNode iterate = new IterateNode(100);
        BodyNode bodyNode = new BodyNode();
        TestWhileNode whileNode = new TestWhileNode(iterate, bodyNode);
        whileNode.execute(null);

        Assert.assertEquals(100, bodyNode.invocations);
        Assert.assertEquals(101, iterate.invocations);
    }

    @Test
    public void testNoInvocations() {
        IterateNode iterate = new IterateNode(0);
        BodyNode bodyNode = new BodyNode();
        TestWhileNode whileNode = new TestWhileNode(iterate, bodyNode);
        whileNode.execute(null);

        Assert.assertEquals(0, bodyNode.invocations);
        Assert.assertEquals(1, iterate.invocations);
    }

    @Test
    public void testBreak() {
        IterateNode iterate = new IterateNode(5);
        BodyNode bodyNode = new BodyNode() {

            @Override
            public Object execute(VirtualFrame frame) {
                super.execute(frame);
                throw new BreakException();
            }

        };
        TestWhileNode whileNode = new TestWhileNode(iterate, bodyNode);
        whileNode.execute(null);

        Assert.assertEquals(1, whileNode.breaks);
        Assert.assertEquals(1, bodyNode.invocations);
        Assert.assertEquals(1, iterate.invocations);
    }

    @Test
    public void testContinue() {
        IterateNode iterate = new IterateNode(3);
        BodyNode bodyNode = new BodyNode() {

            @Override
            public Object execute(VirtualFrame frame) {
                super.execute(frame);
                throw new ContinueException();
            }

        };
        TestWhileNode whileNode = new TestWhileNode(iterate, bodyNode);
        whileNode.execute(null);

        Assert.assertEquals(3, whileNode.continues);
        Assert.assertEquals(3, bodyNode.invocations);
        Assert.assertEquals(4, iterate.invocations);
    }

    @Test
    public void testLoopCountReportingWithNode() {
        GuestLanguageNode node = new GuestLanguageNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                int[] data = new int[]{1, 2, 3, 4, 5};
                try {
                    int sum = 0;
                    for (int i = 0; i < data.length; i++) {
                        sum += data[i];
                    }
                    return sum;
                } finally {
                    LoopNode.reportLoopCount(this, data.length);
                }
            }
        };
        for (int i = 0; i < 1000; i++) {
            Assert.assertEquals(15, node.execute(null));
        }
    }

    @Test
    public void testLoopCountReportingInCallTarget() {
        final GuestLanguageNode node = new GuestLanguageNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                int[] data = new int[]{1, 2, 3, 4, 5};
                try {
                    int sum = 0;
                    for (int i = 0; i < data.length; i++) {
                        sum += data[i];
                    }
                    return sum;
                } finally {
                    LoopNode.reportLoopCount(this, data.length);
                }
            }
        };
        RootNode root = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(frame);
            }
        };

        CallTarget target = Truffle.getRuntime().createCallTarget(root);
        for (int i = 0; i < 1000; i++) {
            Assert.assertEquals(15, target.call());
        }
    }

    @Test
    public void testSpecialValue() {
        IterateAndReturnValueNode iterate = new IterateAndReturnValueNode("Ronaldo", 3);
        TestWhileWithValueNode whileNode = new TestWhileWithValueNode(iterate);
        final Object specialValue = whileNode.execute(null);
        Assert.assertEquals(3, whileNode.continues);
        Assert.assertEquals("Ronaldo", specialValue);
    }

    private static class BodyNode extends GuestLanguageNode {

        int invocations;

        @Override
        public Object execute(VirtualFrame frame) {
            invocations++;
            return null;
        }
    }

    private static class IterateNode extends GuestLanguageNode {

        int invocations;
        int iterations;

        IterateNode(int iterations) {
            this.iterations = iterations;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            invocations++;
            if (iterations == 0) {
                return false;
            } else {
                iterations--;
                return true;
            }
        }

    }

    private static class IterateAndReturnValueNode extends GuestLanguageNode {
        final Object specialValue;
        int iterations;

        IterateAndReturnValueNode(Object specialValue, int iterations) {
            this.specialValue = specialValue;
            this.iterations = iterations;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (iterations == 0) {
                return specialValue;
            } else {
                iterations--;
                return RepeatingNode.CONTINUE_LOOP_STATUS;
            }
        }
    }

    private static class TestWhileNode extends GuestLanguageNode {

        @Child private LoopNode loop;

        int breaks;
        int continues;

        TestWhileNode(GuestLanguageNode conditionNode, GuestLanguageNode bodyNode) {
            loop = Truffle.getRuntime().createLoopNode(new WhileRepeatingNode(conditionNode, bodyNode));
        }

        @Override
        public Object execute(VirtualFrame frame) {
            loop.execute(frame);
            return null;
        }

        private class WhileRepeatingNode extends Node implements RepeatingNode {

            @Child private GuestLanguageNode conditionNode;
            @Child private GuestLanguageNode bodyNode;

            WhileRepeatingNode(GuestLanguageNode conditionNode, GuestLanguageNode bodyNode) {
                this.conditionNode = conditionNode;
                this.bodyNode = bodyNode;
            }

            public boolean executeRepeating(VirtualFrame frame) {
                if ((boolean) conditionNode.execute(frame)) {
                    try {
                        bodyNode.execute(frame);
                    } catch (ContinueException ex) {
                        continues++;
                        // the body might throw a continue control-flow exception
                        // continue loop invocation
                    } catch (BreakException ex) {
                        breaks++;
                        // the body might throw a break control-flow exception
                        // break loop invocation by returning false
                        return false;
                    }
                    return true;
                } else {
                    return false;
                }
            }
        }

    }

    private static class TestWhileWithValueNode extends GuestLanguageNode {

        @Child private LoopNode loop;

        int continues;

        TestWhileWithValueNode(GuestLanguageNode bodyNode) {
            loop = Truffle.getRuntime().createLoopNode(new WhileWithValueRepeatingNode(bodyNode));
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return loop.execute(frame);
        }

        private class WhileWithValueRepeatingNode extends Node implements RepeatingNode {
            @Child private GuestLanguageNode bodyNode;

            WhileWithValueRepeatingNode(GuestLanguageNode bodyNode) {
                this.bodyNode = bodyNode;
            }

            @Override
            public boolean executeRepeating(VirtualFrame frame) {
                throw new RuntimeException("This method will not be called.");
            }

            @Override
            public Object executeRepeatingWithValue(VirtualFrame frame) {
                final Object result = bodyNode.execute(frame);
                if (result == CONTINUE_LOOP_STATUS) {
                    continues++;
                }
                return result;
            }
        }
    }

    // substitute with a guest language node type
    private abstract static class GuestLanguageNode extends Node {

        public abstract Object execute(VirtualFrame frame);

    }

    // thrown by guest language continue statements
    @SuppressWarnings("serial")
    private static final class ContinueException extends ControlFlowException {
    }

    // thrown by guest language break statements
    @SuppressWarnings("serial")
    private static final class BreakException extends ControlFlowException {
    }

}
