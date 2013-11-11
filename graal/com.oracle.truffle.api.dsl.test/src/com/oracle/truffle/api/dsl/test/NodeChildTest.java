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

import static com.oracle.truffle.api.dsl.test.TestHelper.*;
import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.NodeFieldTestFactory.IntFieldTestNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class NodeChildTest {

    @Test
    public void testIntField() {
        assertEquals(42, createCallTarget(IntFieldTestNodeFactory.create(42)).call());
    }

    @NodeChild("child0")
    abstract static class Base0Node extends ValueNode {

    }

    @NodeChild(value = "child1", type = ValueNode.class)
    abstract static class Child0Node extends Base0Node {

        @Specialization
        int intField(int child0, int child1) {
            return child0 + child1;
        }
    }

    @NodeChildren({@NodeChild("child0")})
    abstract static class Base1Node extends ValueNode {

    }

    @NodeChildren({@NodeChild(value = "child1", type = ValueNode.class)})
    abstract static class Child1Node extends Base1Node {

        @Specialization
        int intField(int child0, int child1) {
            return child0 + child1;
        }
    }

    @NodeChildren({@NodeChild("child0"), @NodeChild("child1")})
    abstract static class Base2Node extends ValueNode {

    }

    @NodeChildren({@NodeChild(value = "child2", type = ValueNode.class)})
    abstract static class Child2Node extends Base1Node {

        // TODO this is an error to fix
        @ExpectError("Method signature (int, int, int) does not match to the expected signature:%")
        @Specialization
        int intField(int child0, int child1, int child2) {
            return child0 + child1 + child2;
        }
    }

    @NodeChildren({@NodeChild(value = "receiver", type = ValueNode.class), @NodeChild(value = "arguments", type = ValueNode[].class)})
    abstract static class BaseNode extends ValueNode {
        public abstract ValueNode getReceiver();

        public abstract ValueNode[] getArguments();
    }

    abstract static class UnaryNode extends BaseNode {
        @Specialization
        public int doIt(int value) {
            return value;
        }
    }

    abstract static class BinaryNode extends BaseNode {
        @Specialization
        public int doIt(int value0, int value1) {
            return value0 + value1;
        }
    }

    abstract static class TernaryNode extends BaseNode {
        @Specialization
        public int doIt(int value0, int value1, int value2) {
            return value0 + value1 + value2;
        }
    }

}
