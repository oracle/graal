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
import com.oracle.truffle.api.dsl.test.BinaryNodeTest.BinaryNode;
import com.oracle.truffle.api.dsl.test.PolymorphicTest2Factory.Node1Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.nodes.*;

public class PolymorphicTest2 {

    @Test
    public void testMultipleTypes() {
        /* Tests the unexpected polymorphic case. */
        TestRootNode<Node1> node = TestHelper.createRoot(Node1Factory.getInstance());
        assertEquals(21, executeWith(node, false, false));
        assertEquals(42, executeWith(node, 21, 21));
        assertEquals("(boolean,int)", executeWith(node, false, 42));
        assertEquals(NodeCost.POLYMORPHIC, node.getNode().getCost());
    }

    @SuppressWarnings("unused")
    @PolymorphicLimit(3)
    abstract static class Node1 extends BinaryNode {

        @Specialization(order = 1)
        int add(int left, int right) {
            return 42;
        }

        @Specialization(order = 2)
        int add(boolean left, boolean right) {
            return 21;
        }

        @Specialization(order = 4)
        String add(boolean left, int right) {
            return "(boolean,int)";
        }

    }

}
