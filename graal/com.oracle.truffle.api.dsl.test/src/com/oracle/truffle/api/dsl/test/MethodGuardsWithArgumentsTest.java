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

import static com.oracle.truffle.api.dsl.test.TestHelper.*;

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.MethodGuardsWithArgumentsTestFactory.MArguments0Factory;
import com.oracle.truffle.api.dsl.test.MethodGuardsWithArgumentsTestFactory.MArguments1Factory;
import com.oracle.truffle.api.dsl.test.MethodGuardsWithArgumentsTestFactory.MArgumentsDouble0Factory;
import com.oracle.truffle.api.dsl.test.MethodGuardsWithArgumentsTestFactory.MArgumentsDouble1Factory;
import com.oracle.truffle.api.dsl.test.MethodGuardsWithArgumentsTestFactory.MArgumentsDouble2Factory;
import com.oracle.truffle.api.dsl.test.MethodGuardsWithArgumentsTestFactory.MArgumentsDouble3Factory;
import com.oracle.truffle.api.dsl.test.MethodGuardsWithArgumentsTestFactory.MArgumentsSingle2Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class MethodGuardsWithArgumentsTest {

    @Test
    public void testMArguments0() {
        TestRootNode<MArguments0> root = createRoot(MArguments0Factory.getInstance());
        Assert.assertEquals(42, executeWith(root));
    }

    abstract static class MArguments0 extends ValueNode {

        static boolean guard() {
            return true;
        }

        @Specialization(guards = "guard()")
        int do1() {
            return 42;
        }
    }

    @Test
    public void testMArguments1() {
        TestRootNode<MArguments1> root = createRoot(MArguments1Factory.getInstance());
        Assert.assertEquals(42, executeWith(root));
    }

    abstract static class MArguments1 extends ValueNode {

        static boolean guard() {
            return true;
        }

        @Specialization(guards = "guard ()")
        int do1() {
            return 42;
        }
    }

    @Test
    public void testMArgumentsSingle0() {
        TestRootNode<MArguments1> root = createRoot(MArguments1Factory.getInstance());
        Assert.assertEquals(42, executeWith(root, 42));
    }

    @NodeChild("a")
    abstract static class MArgumentsSingle0 extends ValueNode {

        static boolean guard() {
            return true;
        }

        @Specialization(guards = "guard()")
        int do1(int a) {
            return a;
        }
    }

    @Test
    public void testMArgumentsSingle1() {
        TestRootNode<MArguments1> root = createRoot(MArguments1Factory.getInstance());
        Assert.assertEquals(42, executeWith(root, 42));
    }

    @NodeChild("a")
    abstract static class MArgumentsSingle1 extends ValueNode {

        static boolean guard(int a) {
            return a == 42;
        }

        @Specialization(guards = "guard(a)")
        int do1(int a) {
            return a;
        }
    }

    @Test
    public void testMArgumentsSingle2() {
        TestRootNode<MArgumentsSingle2> root = createRoot(MArgumentsSingle2Factory.getInstance());
        Assert.assertEquals(42, executeWith(root, 42));
    }

    @NodeChild("a")
    abstract static class MArgumentsSingle2 extends ValueNode {

        static boolean guard(int a1, int a2) {
            return a1 == 42 && a2 == 42;
        }

        @Specialization(guards = "guard(a,a)")
        int do1(int a) {
            return a;
        }
    }

    @Test
    public void testMArgumentsDouble0() {
        TestRootNode<MArgumentsDouble0> root = createRoot(MArgumentsDouble0Factory.getInstance());
        Assert.assertEquals(42, executeWith(root, 42, 0));
    }

    @NodeChild("a")
    abstract static class MArgumentsDouble0 extends ValueNode {

        static boolean guard(int a1, Object a2) {
            return a1 == 42 && a2.equals(new Integer(42));
        }

        @Specialization(guards = "guard(a,a)")
        int do1(int a) {
            return a;
        }
    }

    @Test
    public void testMArgumentsDouble1() {
        TestRootNode<MArgumentsDouble1> root = createRoot(MArgumentsDouble1Factory.getInstance());
        Assert.assertEquals(42, executeWith(root, 42, 41));
    }

    @NodeChildren({@NodeChild("a"), @NodeChild("b")})
    abstract static class MArgumentsDouble1 extends ValueNode {

        static boolean guard(int a1, double a2) {
            return a1 == 42 && a2 == 41;
        }

        @Specialization(guards = "guard(a,b)")
        int do1(int a, @SuppressWarnings("unused") double b) {
            return a;
        }
    }

    @Test
    public void testMArgumentsDouble2() {
        TestRootNode<MArgumentsDouble2> root = createRoot(MArgumentsDouble2Factory.getInstance());
        Assert.assertEquals(42, executeWith(root, 42, 41.0));
    }

    @NodeChildren({@NodeChild("a"), @NodeChild("b")})
    abstract static class MArgumentsDouble2 extends ValueNode {

        static boolean guard(double a1, int a2) {
            return a1 == 41 && a2 == 42;
        }

        @Specialization(guards = "guard(b,a)")
        int do1(int a, @SuppressWarnings("unused") double b) {
            return a;
        }
    }

    @Test
    public void testMArgumentsDouble3() {
        TestRootNode<MArgumentsDouble3> root = createRoot(MArgumentsDouble3Factory.getInstance());
        Assert.assertEquals(42, executeWith(root, 42, 41.0));
    }

    @NodeChildren({@NodeChild("a"), @NodeChild("b")})
    abstract static class MArgumentsDouble3 extends ValueNode {

        static boolean guard(Object a1, double a2) {
            return new Double(41.0).equals(a1) && a2 == 41;
        }

        @Specialization(guards = "guard(b,b)")
        int do1(int a, @SuppressWarnings("unused") double b) {
            return a;
        }
    }

    abstract static class MArgumentsError0 extends ValueNode {

        static boolean guard() {
            return true;
        }

        @ExpectError("No compatible guard with method name 'guard(' found.%")
        @Specialization(guards = "guard(")
        int do1() {
            return 42;
        }
    }

    abstract static class MArgumentsError1 extends ValueNode {

        static boolean guard() {
            return true;
        }

        @ExpectError("No compatible guard with method name 'guard)' found.%")
        @Specialization(guards = "guard)")
        int do1() {
            return 42;
        }

    }

    abstract static class MArgumentsError2 extends ValueNode {

        static boolean guard() {
            return true;
        }

        @ExpectError("Guard parameter 'a' for guard 'guard' could not be mapped to a declared child node.")
        @Specialization(guards = "guard(a)")
        int do1() {
            return 42;
        }
    }

    @NodeChild("b")
    abstract static class MArgumentsError3 extends ValueNode {

        static boolean guard() {
            return true;
        }

        @ExpectError("Guard parameter 'a' for guard 'guard' could not be mapped to a declared child node.")
        @Specialization(guards = "guard(a)")
        int do1(int b) {
            return b;
        }
    }

}
