/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.dsl.test.UnsupportedSpecializationTestFactory.Unsupported1Factory;
import com.oracle.truffle.api.dsl.test.UnsupportedSpecializationTestFactory.UnsupportedNoChildNodeGen;
import com.oracle.truffle.api.nodes.Node;

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
    public void testUnsupportedNoChildNode() {
        UnsupportedNoChildNode child = UnsupportedNoChildNodeGen.create();

        try {
            child.execute(42d);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
            Assert.assertNotNull(e.getSuppliedValues());
            Assert.assertNotNull(e.getSuppliedNodes());
            Assert.assertEquals(1, e.getSuppliedValues().length);
            Assert.assertEquals(1, e.getSuppliedNodes().length);
            Assert.assertEquals(42d, e.getSuppliedValues()[0]);
            Assert.assertNull(e.getSuppliedNodes()[0]);
        }
    }

    abstract static class UnsupportedNoChildNode extends Node {

        abstract Object execute(Object value);

        @Specialization
        String s1(String v) {
            return v;
        }

        @Specialization
        int s2(int v) {
            return v;
        }
    }

}
