/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.ImplicitCast0NodeFactory;
import com.oracle.truffle.api.dsl.test.NodeContainerTest.Str;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.frame.*;

public class ImplicitCastTest {

    @TypeSystem({int.class, boolean.class, String.class, Str.class})
    static class ImplicitCast0Types {

        @ImplicitCast
        boolean castInt(int intvalue) {
            return intvalue == 1 ? true : false;
        }

        @ImplicitCast
        boolean castString(String strvalue) {
            return strvalue.equals("1");
        }

    }

    @TypeSystemReference(ImplicitCast0Types.class)
    @NodeChild(value = "operand", type = ImplicitCast0Node.class)
    abstract static class ImplicitCast0Node extends ValueNode {

        public abstract Object executeEvaluated(VirtualFrame frame, Object value2);

        @Specialization
        public String op1(String value) {
            return value;
        }

        @Specialization
        public boolean op1(boolean value) {
            return value;
        }

    }

    @Test
    public void testImplicitCast0() {
        ImplicitCast0Node node = ImplicitCast0NodeFactory.create(null);
        TestRootNode<ImplicitCast0Node> root = new TestRootNode<>(node);
        Assert.assertEquals("2", root.getNode().executeEvaluated(null, "2"));
        Assert.assertEquals(true, root.getNode().executeEvaluated(null, 1));
        Assert.assertEquals("1", root.getNode().executeEvaluated(null, "1"));
        Assert.assertEquals(true, root.getNode().executeEvaluated(null, 1));
        Assert.assertEquals(true, root.getNode().executeEvaluated(null, true));
    }

// @TypeSystemReference(ImplicitCast0Types.class)
// @NodeChild(value = "operand", type = ImplicitCast0Node.class)
// abstract static class ImplicitCast1Node extends ValueNode {
//
// @Specialization
// public String op0(String value) {
// return value;
// }
//
// @Specialization(order = 1, rewriteOn = RuntimeException.class)
// public boolean op1(@SuppressWarnings("unused") boolean value) throws RuntimeException {
// throw new RuntimeException();
// }
//
// @Specialization(order = 2)
// public boolean op2(boolean value) {
// return value;
// }
//
// }
//
// @Test
// public void testImplicitCast1() {
// ImplicitCast0Node node = ImplicitCast0NodeFactory.create(null);
// TestRootNode<ImplicitCast0Node> root = new TestRootNode<>(node);
// Assert.assertEquals("2", root.getNode().executeEvaluated(null, "2"));
// Assert.assertEquals(true, root.getNode().executeEvaluated(null, 1));
// Assert.assertEquals("1", root.getNode().executeEvaluated(null, "1"));
// Assert.assertEquals(true, root.getNode().executeEvaluated(null, 1));
// Assert.assertEquals(true, root.getNode().executeEvaluated(null, true));
// }

    // TODO assert implicit casts only in one direction

    // test example that covers the most cases

}
