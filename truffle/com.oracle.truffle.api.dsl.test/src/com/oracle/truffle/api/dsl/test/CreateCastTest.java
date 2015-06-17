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
import com.oracle.truffle.api.dsl.test.CreateCastTestFactory.CreateCastNode1Factory;
import com.oracle.truffle.api.dsl.test.CreateCastTestFactory.CreateCastNode2Factory;
import com.oracle.truffle.api.dsl.test.CreateCastTestFactory.CreateCastNode3Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ChildrenNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.*;

@SuppressWarnings("unused")
public class CreateCastTest {

    @Test
    public void testCastNode1() {
        TestRootNode<CreateCastNode1> root = TestHelper.createRoot(CreateCastNode1Factory.getInstance());
        Assert.assertEquals(1, root.getNode().invocations);
    }

    @Test
    public void testCastNode2() {
        TestRootNode<CreateCastNode2> root = TestHelper.createRoot(CreateCastNode2Factory.getInstance());
        Assert.assertEquals(2, root.getNode().invocations);
    }

    @Test
    public void testCastNode3() {
        TestRootNode<CreateCastNode3> root = TestHelper.createRoot(CreateCastNode3Factory.getInstance());
        Assert.assertEquals(1, root.getNode().invocations);
    }

    @NodeChild("a")
    abstract static class CreateCastNode1 extends ValueNode {

        int invocations = 0;

        @CreateCast({"a"})
        public ValueNode createCast(ValueNode node) {
            invocations++;
            return node;
        }

        @Specialization
        public int doInteger(int a) {
            throw new AssertionError();
        }
    }

    @NodeChildren({@NodeChild("a"), @NodeChild("b")})
    abstract static class CreateCastNode2 extends ValueNode {

        int invocations = 0;

        @CreateCast({"a", "b"})
        public ValueNode createCast(ValueNode node) {
            invocations++;
            return node;
        }

        @Specialization
        public int doInteger(int a, int b) {
            throw new AssertionError();
        }
    }

    abstract static class CreateCastNode3 extends ChildrenNode {

        int invocations = 0;

        @CreateCast("children")
        public ValueNode[] createCast(ValueNode[] node) {
            invocations++;
            return node;
        }

        @Specialization
        public int doInteger(int a) {
            throw new AssertionError();
        }
    }

    @NodeChild("a")
    abstract static class CreateCastFailNode1 extends ValueNode {

        @ExpectError({"Specified child '' not found."})
        @CreateCast({""})
        public ValueNode createCast(Node child) {
            throw new AssertionError();
        }

        @Specialization
        public int doInteger(int a) {
            throw new AssertionError();
        }
    }

}
