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

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.Fallback1Factory;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.Fallback2Factory;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.Fallback3Factory;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.Fallback4Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.*;
import com.oracle.truffle.api.nodes.*;

public class FallbackTest {

    private static final Object UNKNOWN_OBJECT = new Object() {
    };

    @Test
    public void testFallback1() {
        assertRuns(Fallback1Factory.getInstance(), //
                        array(42, UNKNOWN_OBJECT), //
                        array("(int)", "(fallback)"));
    }

    /**
     * test with fallback handler defined
     */
    @SuppressWarnings("unused")
    @NodeChild("a")
    abstract static class Fallback1 extends ValueNode {

        @Specialization
        String f1(int a) {
            return "(int)";
        }

        @Fallback
        String f2(Object a) {
            return "(fallback)";
        }
    }

    @Test
    public void testFallback2() {
        assertRuns(Fallback2Factory.getInstance(), //
                        array(42, UNKNOWN_OBJECT), //
                        array("(int)", UnsupportedSpecializationException.class));
    }

    /**
     * test without fallback handler defined
     */
    @SuppressWarnings("unused")
    @NodeChild("a")
    abstract static class Fallback2 extends ValueNode {

        @Specialization
        String f1(int a) {
            return "(int)";
        }

    }

    @Test
    public void testFallback3() {
        assertRuns(Fallback3Factory.getInstance(), //
                        array(42, 43, UNKNOWN_OBJECT, "somestring"), //
                        array("(int)", "(int)", "(object)", "(object)"));
    }

    /**
     * test without fallback handler and unreachable
     */
    @SuppressWarnings("unused")
    @NodeChild("a")
    abstract static class Fallback3 extends ValueNode {

        @Specialization
        String f1(int a) {
            return "(int)";
        }

        @Specialization(guards = "notInt")
        String f2(Object a) {
            return "(object)";
        }

        boolean notInt(Object value) {
            return !(value instanceof Integer);
        }

    }

    /**
     * Tests the contents of the {@link UnsupportedSpecializationException} contents in polymorphic
     * nodes.
     */
    @Test
    public void testFallback4() {
        TestRootNode<Fallback4> node = createRoot(Fallback4Factory.getInstance());

        Assert.assertEquals("(int)", executeWith(node, 1));
        Assert.assertEquals("(boolean)", executeWith(node, true));
        try {
            executeWith(node, UNKNOWN_OBJECT);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
            Assert.assertEquals(node.getNode(), e.getNode());
            Assert.assertArrayEquals(NodeUtil.findNodeChildren(node.getNode()).subList(0, 1).toArray(new Node[0]), e.getSuppliedNodes());
            Assert.assertArrayEquals(new Object[]{UNKNOWN_OBJECT}, e.getSuppliedValues());
        }
    }

    /**
     * test without fallback handler and unreachable
     */
    @SuppressWarnings("unused")
    @NodeChild("a")
    abstract static class Fallback4 extends ValueNode {

        @Specialization
        String f1(int a) {
            return "(int)";
        }

        @Specialization
        String f2(boolean a) {
            return "(boolean)";
        }
    }

    /**
     * Tests the contents of the {@link UnsupportedSpecializationException} contents in monomorphic
     * nodes.
     */
    @Test
    public void testFallback5() {
        TestRootNode<Fallback4> node = createRoot(Fallback4Factory.getInstance());

        Assert.assertEquals("(int)", executeWith(node, 1));
        try {
            executeWith(node, UNKNOWN_OBJECT);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
            Assert.assertEquals(node.getNode(), e.getNode());
            Assert.assertArrayEquals(NodeUtil.findNodeChildren(node.getNode()).subList(0, 1).toArray(new Node[0]), e.getSuppliedNodes());
            Assert.assertArrayEquals(new Object[]{UNKNOWN_OBJECT}, e.getSuppliedValues());
        }
    }

    // test without fallback handler and unreachable
    @SuppressWarnings("unused")
    @NodeChild("a")
    abstract static class Fallback5 extends ValueNode {

        @Specialization
        String f1(int a) {
            return "(int)";
        }
    }

}
