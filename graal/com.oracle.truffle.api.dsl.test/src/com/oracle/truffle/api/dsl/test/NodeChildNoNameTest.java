/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.NodeChildNoNameTestFactory.OneArgNoNameFactory;
import com.oracle.truffle.api.dsl.test.NodeChildNoNameTestFactory.ThreeArgsNoNameFactory;
import com.oracle.truffle.api.dsl.test.NodeChildNoNameTestFactory.TwoArgsNoNameFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.frame.*;

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
