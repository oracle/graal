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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.api.codegen.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.codegen.test.TypeSystemTest.ValueNode;

public class RuntimeStringTest {

    @Test
    public void testSubstr() {
        assertExecute(new RuntimeString("es"), "substr", new RuntimeString("test"), 1, 3);
    }

    @Test
    public void testConcat() {
        assertExecute(new RuntimeString("concatconcat"), "concat", new RuntimeString("concat"), new RuntimeString("concat"));
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testConcatFail() {
        assertExecute(new RuntimeString("concatconcat"), "concat", new RuntimeString("concat"));
    }

    @Test
    public void testFindMethodByMethodName() {
        // TODO
    }

    private static void assertExecute(Object expectedResult, String name, Object... argumentsArray) {
        ArgNode[] args = new ArgNode[argumentsArray.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = new ArgNode(argumentsArray, i);
        }

        BuiltinNode node = null;
        for (NodeFactory<BuiltinNode> nodeFactory : RuntimeStringTestFactory.getFactories()) {
            GeneratedBy generated = nodeFactory.getClass().getAnnotation(GeneratedBy.class);
            Assert.assertNotNull(generated);
            Assert.assertNotSame("", generated.methodName());
            if (generated.methodName().equals(name)) {
                node = nodeFactory.createNode((Object) args);
                break;
            }
        }
        Assert.assertNotNull("Node not found", node);
        CallTarget target = Truffle.getRuntime().createCallTarget(new TestRootNode(node));
        Assert.assertEquals(expectedResult, target.call());
    }

    static class ArgNode extends ValueNode {

        final Object[] arguments;
        final int index;

        ArgNode(Object[] args, int index) {
            this.arguments = args;
            this.index = index;
        }

        @Override
        Object execute() {
            return arguments[index];
        }

    }

    abstract static class BuiltinNode extends ValueNode {

        @Children ArgNode[] parameters;

        BuiltinNode(ArgNode[] parameters) {
            this.parameters = adoptChildren(parameters);
        }

        BuiltinNode(BuiltinNode prev) {
            this(prev.parameters);
        }

    }

    @NodeClass(BuiltinNode.class)
    static class RuntimeString {

        private final String internal;

        public RuntimeString(String internal) {
            this.internal = internal;
        }

        @Specialization
        static RuntimeString concat(RuntimeString s1, RuntimeString s2) {
            return new RuntimeString(s1.internal + s2.internal);
        }

        @Specialization
        RuntimeString substr(int beginIndex, int endIndex) {
            return new RuntimeString(internal.substring(beginIndex, endIndex));
        }

        @Generic
        RuntimeString substr(Object beginIndex, Object endIndex) {
            return substr(convertInt(beginIndex), convertInt(endIndex));
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
            if (obj instanceof RuntimeString) {
                return internal.equals(((RuntimeString) obj).internal);
            }
            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            return internal.hashCode();
        }

        @Override
        public String toString() {
            return internal;
        }

    }

}
