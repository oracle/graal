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
import com.oracle.truffle.api.dsl.test.NodeContainerTestFactory.StrFactory.StrAccessContextFactory;
import com.oracle.truffle.api.dsl.test.NodeContainerTestFactory.StrFactory.StrConcatFactory;
import com.oracle.truffle.api.dsl.test.NodeContainerTestFactory.StrFactory.StrLengthFactory;
import com.oracle.truffle.api.dsl.test.NodeContainerTestFactory.StrFactory.StrSubstrFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class NodeContainerTest {

    @Test
    public void testConcat() {
        TestRootNode<BuiltinNode> node = createRoot(StrConcatFactory.getInstance(), new Context());
        Str str1 = new Str("42");
        Str str2 = new Str(" is the number.");
        assertEquals(str1.concat(str2), executeWith(node, str1, str2));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testConcatUnsupported() {
        TestRootNode<BuiltinNode> node = createRoot(StrConcatFactory.getInstance(), new Context());
        executeWith(node, 42, new Str(" is the number."));
    }

    @Test
    public void testSubstrSpecialized() {
        TestRootNode<BuiltinNode> node = createRoot(StrSubstrFactory.getInstance(), new Context());
        Str str = new Str("test 42");

        assertEquals(str.substr(5, 7), executeWith(node, str, 5, 7));
    }

    @Test
    public void testSubstrGeneric() {
        TestRootNode<BuiltinNode> node = createRoot(StrSubstrFactory.getInstance(), new Context());
        Str str = new Str("test 42");

        assertEquals(Str.substr(str, "5", "7"), executeWith(node, str, "5", "7"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSubstrUnsupported() {
        TestRootNode<BuiltinNode> node = createRoot(StrSubstrFactory.getInstance(), new Context());
        executeWith(node, new Object(), "5", "7");
    }

    @Test
    public void testLength() {
        TestRootNode<BuiltinNode> node = createRoot(StrLengthFactory.getInstance(), new Context());
        Str testStr = new Str("test 42");
        assertEquals(testStr.length(), executeWith(node, testStr));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLengthUnsupported() {
        TestRootNode<BuiltinNode> node = createRoot(StrLengthFactory.getInstance(), new Context());
        executeWith(node, new Object());
    }

    @Test
    public void testAccessContext() {
        Context context = new Context();
        TestRootNode<BuiltinNode> node = createRoot(StrAccessContextFactory.getInstance(), context);
        // accessible by node
        assertSame(context, node.getNode().getContext());
        // accessible by execution
        assertSame(context, executeWith(node));
    }

    @NodeContainer(BuiltinNode.class)
    static class Str {

        private final String internal;

        public Str(String internal) {
            this.internal = internal;
        }

        @Specialization
        Str concat(Str s1) {
            return new Str(internal + s1.internal);
        }

        @Specialization
        Str substr(int beginIndex, int endIndex) {
            return new Str(internal.substring(beginIndex, endIndex));
        }

        @Generic
        static Str substr(Object thisValue, Object beginIndex, Object endIndex) {
            if (!(thisValue instanceof Str)) {
                throw new UnsupportedOperationException();
            }
            return ((Str) thisValue).substr(convertInt(beginIndex), convertInt(endIndex));
        }

        @Specialization
        int length() {
            return internal.length();
        }

        @Specialization
        static Object accessContext(Context context) {
            return context;
        }

        static int convertInt(Object value) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            }
            throw new RuntimeException("Invalid datatype");
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Str) {
                return internal.equals(((Str) obj).internal);
            }
            return super.equals(obj);
        }

        @Override
        public String toString() {
            return internal;
        }

        @Override
        public int hashCode() {
            return internal.hashCode();
        }
    }

    @NodeChild(value = "children", type = ValueNode[].class)
    abstract static class BuiltinNode extends ValueNode {

        protected final Context context;

        public BuiltinNode(BuiltinNode node) {
            this(node.context);
        }

        public BuiltinNode(Context context) {
            this.context = context;
        }

        public Context getContext() {
            return context;
        }
    }

    static class Context {

    }

}
