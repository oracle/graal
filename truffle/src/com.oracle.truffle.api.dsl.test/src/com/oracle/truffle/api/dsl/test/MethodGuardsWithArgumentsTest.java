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

import static com.oracle.truffle.api.dsl.test.TestHelper.createRoot;
import static com.oracle.truffle.api.dsl.test.TestHelper.executeWith;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
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

        @Specialization(guards = "guard()")
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
            return a1 == 42 && a2.equals(Integer.valueOf(42));
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
            return Double.valueOf(41.0).equals(a1) && a2 == 41;
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

        @ExpectError("Error parsing expression 'guard(': line 1:6 mismatched input%")
        @Specialization(guards = "guard(")
        int do1() {
            return 42;
        }
    }

    abstract static class MArgumentsError1 extends ValueNode {

        static boolean guard() {
            return true;
        }

        @ExpectError("Error parsing expression 'guard)': line 1:5 extraneous input%")
        @Specialization(guards = "guard)")
        int do1() {
            return 42;
        }

    }

    abstract static class MArgumentsError2 extends ValueNode {

        static boolean guard() {
            return true;
        }

        @ExpectError("Error parsing expression 'guard(a)': a cannot be resolved.")
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

        @ExpectError("Error parsing expression 'guard(a)': a cannot be resolved.")
        @Specialization(guards = "guard(a)")
        int do1(int b) {
            return b;
        }
    }

}
