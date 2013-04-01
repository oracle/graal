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
package com.oracle.truffle.api.codegen.test;

import org.junit.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.api.codegen.test.BinaryNodeTestFactory.AddNodeFactory;
import com.oracle.truffle.api.codegen.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.codegen.test.TypeSystemTest.ValueNode;

import static junit.framework.Assert.*;
import static com.oracle.truffle.api.codegen.test.TestHelper.*;

public class BinaryNodeTest {

    @Test
    public void testAdd() {
        TestRootNode<AddNode> node = create(AddNodeFactory.getInstance());
        assertEquals(42, executeWith(node, 19, 23));
        assertEquals(42d, executeWith(node, 19d, 23d));
        assertEquals(42d, executeWith(node, "19", "23"));
        assertEquals(42, executeWith(node, 19, 23));
    }

    @Test(expected = RuntimeException.class)
    public void testAddUnsupported() {
        TestRootNode<AddNode> node = create(AddNodeFactory.getInstance());
        executeWith(node, new Object(), new Object());
    }

    abstract static class BinaryNode extends ValueNode {

        @Child protected ValueNode leftNode;
        @Child protected ValueNode rightNode;

        public BinaryNode(ValueNode left, ValueNode right) {
            this.leftNode = left;
            this.rightNode = right;
        }

        public BinaryNode(BinaryNode prev) {
            this(prev.leftNode, prev.rightNode);
        }
    }

    abstract static class AddNode extends BinaryNode {

        public AddNode(ValueNode left, ValueNode right) {
            super(left, right);
        }

        public AddNode(AddNode prev) {
            super(prev);
        }

        @Specialization
        int add(int left, int right) {
            return left + right;
        }

        @Generic
        Object add(Object left, Object right) {
            return convertDouble(left) + convertDouble(right);
        }

        static double convertDouble(Object value) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
            throw new RuntimeException("Invalid datatype");
        }

    }

}
