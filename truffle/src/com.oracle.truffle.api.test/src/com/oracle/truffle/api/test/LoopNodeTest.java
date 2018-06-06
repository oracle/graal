/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

    private static class TestWhileNode extends GuestLanguageNode {

        @Child private LoopNode loop;

        int breaks;
        int continues;

        TestWhileNode(GuestLanguageNode conditionNode, GuestLanguageNode bodyNode) {
            loop = Truffle.getRuntime().createLoopNode(new WhileRepeatingNode(conditionNode, bodyNode));
        }

        @Override
        public Object execute(VirtualFrame frame) {
            loop.executeLoop(frame);
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
