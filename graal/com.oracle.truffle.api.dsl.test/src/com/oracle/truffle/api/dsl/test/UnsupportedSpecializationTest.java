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

import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.dsl.test.UnsupportedSpecializationTestFactory.Unsupported1Factory;
import com.oracle.truffle.api.dsl.test.UnsupportedSpecializationTestFactory.Unsupported2Factory;
import com.oracle.truffle.api.nodes.*;

public class UnsupportedSpecializationTest {

    @Test
    public void testUnsupported1() {
        TestRootNode<Unsupported1> root = TestHelper.createRoot(Unsupported1Factory.getInstance());
        try {
            TestHelper.executeWith(root, "");
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
            Assert.assertNotNull(e.getSuppliedValues());
            Assert.assertEquals(1, e.getSuppliedValues().length);
            Assert.assertEquals("", e.getSuppliedValues()[0]);
            Assert.assertSame(root.getNode().getChildren().iterator().next(), e.getSuppliedNodes()[0]);
            Assert.assertEquals(root.getNode(), e.getNode());
        }
    }

    @NodeChild("a")
    abstract static class Unsupported1 extends ValueNode {

        @Specialization
        public int doInteger(@SuppressWarnings("unused") int a) {
            throw new AssertionError();
        }
    }

    @Test
    public void testUnsupported2() {
        TestRootNode<Unsupported2> root = TestHelper.createRoot(Unsupported2Factory.getInstance());
        try {
            TestHelper.executeWith(root, "", 1);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
            Assert.assertNotNull(e.getSuppliedValues());
            Assert.assertNotNull(e.getSuppliedNodes());
            Assert.assertEquals(3, e.getSuppliedValues().length);
            Assert.assertEquals(3, e.getSuppliedNodes().length);
            Assert.assertEquals("", e.getSuppliedValues()[0]);
            Assert.assertEquals(false, e.getSuppliedValues()[1]);
            Assert.assertEquals(null, e.getSuppliedValues()[2]);
            List<Node> children = NodeUtil.findNodeChildren(root.getNode());
            Assert.assertSame(children.get(0), e.getSuppliedNodes()[0]);
            Assert.assertNull(e.getSuppliedNodes()[1]);
            Assert.assertSame(children.get(1), e.getSuppliedNodes()[2]);
            Assert.assertEquals(root.getNode(), e.getNode());
        }
    }

    @SuppressWarnings("unused")
    @NodeChildren({@NodeChild("a"), @NodeChild("b")})
    abstract static class Unsupported2 extends ValueNode {

        @ShortCircuit("b")
        public boolean needsB(Object a) {
            return false;
        }

        @Specialization
        public int doInteger(int a, boolean hasB, int b) {
            throw new AssertionError();
        }
    }

}
