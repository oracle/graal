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

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.dsl.test.UnsupportedSpecializationTestFactory.Unsupported1Factory;
import com.oracle.truffle.api.dsl.test.UnsupportedSpecializationTestFactory.UnsupportedNoChildNodeGen;
import com.oracle.truffle.api.dsl.test.UnsupportedSpecializationTestFactory.UnsupportedUncachedNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

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
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("UnsupportedSpecializationTestFactory.UnsupportedNoChildNodeGen"));
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

    @Test
    public void testUnsupportedUncached() {
        UnsupportedUncached child = UnsupportedUncachedNodeGen.create();

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
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("UnsupportedSpecializationTestFactory.UnsupportedUncachedNodeGen"));
        }
        child = UnsupportedUncachedNodeGen.getUncached();

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
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("UnsupportedSpecializationTestFactory.UnsupportedUncachedNodeGen.Uncached"));
        }
    }

    @GenerateUncached
    abstract static class UnsupportedUncached extends Node {

        abstract Object execute(Object value);

        @Specialization
        static String s1(String v) {
            return v;
        }
    }

    @Test
    public void testLibrary() {
        Object obj = new TypeWithUnsupported();

        InteropLibrary lib1 = InteropLibrary.getFactory().create(obj);
        RootNode root = new RootNode(null) {
            @Child InteropLibrary child = lib1;

            @Override
            public Object execute(VirtualFrame frame) {
                Assert.fail();
                return null;
            }
        };
        root.getCallTarget();
        InteropLibrary lib2 = InteropLibrary.getFactory().getUncached(obj);

        try {
            lib1.writeMember(obj, "foo", 42d);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
            Assert.assertNotNull(e.getSuppliedValues());
            Assert.assertNotNull(e.getSuppliedNodes());
            Assert.assertEquals(3, e.getSuppliedValues().length);
            Assert.assertEquals(3, e.getSuppliedNodes().length);
            Assert.assertEquals(42d, e.getSuppliedValues()[2]);
            Assert.assertNull(e.getSuppliedNodes()[0]);
            Assert.assertNull(e.getSuppliedNodes()[1]);
            Assert.assertNull(e.getSuppliedNodes()[2]);
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("TypeWithUnsupportedGen.InteropLibraryExports.Cached"));
        } catch (Throwable e) {
            Assert.fail("exception: " + e);
        }
        try {
            lib2.writeMember(obj, "foo", 42d);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
            Assert.assertNotNull(e.getSuppliedValues());
            Assert.assertNotNull(e.getSuppliedNodes());
            Assert.assertEquals(3, e.getSuppliedValues().length);
            Assert.assertEquals(3, e.getSuppliedNodes().length);
            Assert.assertEquals(42d, e.getSuppliedValues()[2]);
            Assert.assertNull(e.getSuppliedNodes()[0]);
            Assert.assertNull(e.getSuppliedNodes()[1]);
            Assert.assertNull(e.getSuppliedNodes()[2]);
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("TypeWithUnsupportedGen.InteropLibraryExports.Uncached"));
        } catch (Throwable e) {
            Assert.fail("exception: " + e);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TypeWithUnsupported implements TruffleObject {

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object getMembers(boolean includeInternal) {
            Assert.fail("unexpected: " + includeInternal);
            return null;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isMemberModifiable(String member) {
            return "foo".equals(member);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isMemberInsertable(String member) {
            return "foo".equals(member);
        }

        @ExportMessage
        static final class WriteMember {

            @Specialization
            static void write(TypeWithUnsupported receiver, String name, int value) {
                Assert.fail("unexpected: " + receiver + ", " + name + ", " + value);
            }
        }
    }
}
