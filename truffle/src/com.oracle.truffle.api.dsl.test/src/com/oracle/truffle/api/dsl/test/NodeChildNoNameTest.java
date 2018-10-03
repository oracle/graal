/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.NodeChildNoNameTestFactory.OneArgNoNameFactory;
import com.oracle.truffle.api.dsl.test.NodeChildNoNameTestFactory.ThreeArgsNoNameFactory;
import com.oracle.truffle.api.dsl.test.NodeChildNoNameTestFactory.TwoArgsNoNameFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public class NodeChildNoNameTest {

    @Test
    public void testOneArg() {
        ValueNode node = new ConstantNode();
        OneArgNoName arg = OneArgNoNameFactory.create(node);
        Assert.assertEquals(node, arg.getChild0());
        Assert.assertEquals(43, TestHelper.createCallTarget(arg).call());
    }

    @Test
    public void testTwoArg() {
        ValueNode node1 = new ConstantNode();
        ValueNode node2 = new ConstantNode();
        TwoArgsNoName arg = TwoArgsNoNameFactory.create(node1, node2);
        Assert.assertEquals(node1, arg.getChild0());
        Assert.assertEquals(node2, arg.getChild1());
        Assert.assertEquals(84, TestHelper.createCallTarget(arg).call());
    }

    @Test
    public void testThreeArg() {
        ValueNode node1 = new ConstantNode();
        ValueNode node2 = new ConstantNode();
        ValueNode node3 = new ConstantNode();
        ThreeArgsNoName arg = ThreeArgsNoNameFactory.create(node1, node2, node3);
        Assert.assertEquals(node1, arg.getChild0());
        Assert.assertEquals(node2, arg.getChild1());
        Assert.assertEquals(node3, arg.getChild2());
        Assert.assertEquals(126, TestHelper.createCallTarget(arg).call());
    }

    private static class ConstantNode extends ValueNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return 42;
        }
    }

    @NodeChild
    abstract static class OneArgNoName extends ValueNode {

        public abstract ValueNode getChild0();

        @Specialization
        int doIt(int exp) {
            return exp + 1;
        }

    }

    @NodeChildren({@NodeChild, @NodeChild})
    abstract static class TwoArgsNoName extends ValueNode {

        public abstract ValueNode getChild0();

        public abstract ValueNode getChild1();

        @Specialization
        int doIt(int exp0, int exp1) {
            return exp0 + exp1;
        }
    }

    @NodeChildren({@NodeChild, @NodeChild, @NodeChild})
    abstract static class ThreeArgsNoName extends ValueNode {

        public abstract ValueNode getChild0();

        public abstract ValueNode getChild1();

        public abstract ValueNode getChild2();

        @Specialization
        int doIt(int exp0, int exp1, int exp2) {
            return exp0 + exp1 + exp2;
        }
    }

}
