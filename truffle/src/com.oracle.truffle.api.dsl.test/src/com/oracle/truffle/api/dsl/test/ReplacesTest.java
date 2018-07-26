/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.dsl.test.TestHelper.array;
import static com.oracle.truffle.api.dsl.test.TestHelper.assertRuns;
import static com.oracle.truffle.api.dsl.test.TestHelper.createRoot;
import static com.oracle.truffle.api.dsl.test.TestHelper.executeWith;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.ReplacesTestFactory.PolymorphicToMonomorphic0Factory;
import com.oracle.truffle.api.dsl.test.ReplacesTestFactory.Replaces2Factory;
import com.oracle.truffle.api.dsl.test.ReplacesTestFactory.Replaces3Factory;
import com.oracle.truffle.api.dsl.test.ReplacesTestFactory.Replaces4Factory;
import com.oracle.truffle.api.dsl.test.TestHelper.TestExecutionListener;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.NodeCost;

@SuppressWarnings("unused")
public class ReplacesTest {

    /*
     * Tests an inclusion in within a polymorphic chain.
     */
    @Test
    public void testReplaces2() {
        assertRuns(Replaces2Factory.getInstance(), //
                        array(true, 1, 0, false), //
                        array(false, -1, 1, true) //
        );
    }

    @NodeChild("a")
    abstract static class Replaces2 extends ValueNode {

        static boolean isZero(int a) {
            return a == 0;
        }

        @Specialization(guards = "isZero(a)")
        int f1(int a) {
            return a + 1;
        }

        @Specialization(replaces = "f1")
        int f2(int a) {
            if (a == 0) {
                return a + 1;
            }
            return -a;
        }

        @Specialization
        boolean f3(boolean a) {
            return !a;
        }
    }

    /*
     * Tests transitive monomorphic inclusion.
     */
    @Test
    public void testReplaces3() {
        assertRuns(Replaces3Factory.getInstance(), //
                        array(2, 1, 2, -3, -4), //
                        array(-2, 2, -2, -3, -4), //
                        new TestExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                // assert that we are always monomorphic
                                Assert.assertEquals(NodeCost.MONOMORPHIC, node.getNode().getCost());
                            }
                        });
    }

    @NodeChild("a")
    abstract static class Replaces3 extends ValueNode {

        static boolean isGreaterZero(int a) {
            return a > 0;
        }

        static boolean isOne(int a) {
            return a == 1;
        }

        @Specialization(guards = {"isOne(a)"})
        int f1(int a) {
            return a + 1;
        }

        @Specialization(replaces = "f1", guards = {"isGreaterZero(a)"})
        int f2(int a) {
            if (a == 1) {
                return 2;
            }
            return -a;
        }

        @Specialization(replaces = "f2")
        int f3(int a) {
            if (a > 0) {
                return a == 1 ? 2 : -a;
            } else {
                return a;
            }
        }

    }

    /*
     * Tests that if it can be derived that two specializations actually a as powerful as the latter
     * we can combine them. Therefore operation should always become monomorphic in the end.
     */
    @Test
    public void testReplaces4() {
        assertRuns(Replaces4Factory.getInstance(), //
                        array(-1, 0, 1, 2), //
                        array(1, 0, 1, 2), //
                        new TestExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                Assert.assertEquals(NodeCost.MONOMORPHIC, node.getNode().getCost());
                            }
                        });
    }

    @NodeChild("a")
    abstract static class Replaces4 extends ValueNode {

        static boolean isOne(int a) {
            return a == 1;
        }

        @Specialization(guards = "isOne(a)")
        int f0(int a) {
            return 1;
        }

        @Specialization(replaces = "f0", guards = "a >= 0")
        int f1(int a) {
            return a;
        }

        @Specialization(replaces = {"f1"})
        int f2(int a) {
            return Math.abs(a);
        }

    }

    @NodeChild("a")
    abstract static class ReplacesError1 extends ValueNode {
        @ExpectError("The replaced specialization 'f1' must be declared before the replacing specialization.")
        @Specialization(replaces = "f1")
        int f0(int a) {
            return a;
        }

        @Specialization
        Object f1(String a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ReplacesError2 extends ValueNode {

        @ExpectError("The referenced specialization 'does not exist' could not be found.")
        @Specialization(replaces = "does not exist")
        int f0(int a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ReplacesError3 extends ValueNode {

        @Specialization
        int f0(int a) {
            return a;
        }

        @ExpectError("Duplicate replace declaration 'f0'.")
        @Specialization(replaces = {"f0", "f0"})
        Object f1(double a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ReplacesError4 extends ValueNode {

        @ExpectError("Circular replaced specialization 'f1(double)' found.")
        @Specialization(replaces = {"f1"})
        Object f1(double a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ReplacesError5 extends ValueNode {

        @ExpectError({"Circular replaced specialization 'f0(int)' found.", "Circular replaced specialization 'f1(double)' found.",
                        "The replaced specialization 'f1' must be declared before the replacing specialization."})
        @Specialization(replaces = "f1")
        int f0(int a) {
            return a;
        }

        @ExpectError("Circular replaced specialization 'f1(double)' found.")
        @Specialization(replaces = {"f0"})
        Object f1(double a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ReplacesType1 extends ValueNode {
        @Specialization
        int f0(int a) {
            return a;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by f0(int).")
        @Specialization(replaces = "f0")
        Object f1(int a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ReplacesType2 extends ValueNode {
        @Specialization
        int f0(int a) {
            return a;
        }

        @Specialization(replaces = "f0")
        Object f1(Object a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ReplacesType3 extends ValueNode {
        @Specialization
        int f0(int a) {
            return a;
        }

        @Specialization(replaces = "f0")
        Object f1(double a) { // implicit type
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ReplacesType4 extends ValueNode {
        @Specialization
        double f0(double a) {
            return a;
        }

        @ExpectError({"Specialization is not reachable. It is shadowed by f0(double)."})
        @Specialization(replaces = "f0")
        int f1(int a) { // implicit type
            return a;
        }
    }

    @NodeChildren({@NodeChild("a"), @NodeChild("b")})
    abstract static class ReplacesType5 extends ValueNode {
        @Specialization
        Object f0(Object a, int b) {
            return a;
        }

        @Specialization(replaces = "f0")
        Object f1(int a, Object b) {
            return a;
        }
    }

    @NodeChildren({@NodeChild("a"), @NodeChild("b")})
    abstract static class ReplacesType6 extends ValueNode {
        @Specialization
        Object f0(double a, int b) {
            return a;
        }

        @Specialization(replaces = "f0")
        Object f1(int a, double b) { // implicit type
            return a;
        }
    }

    abstract static class ReplacesGuard1 extends ValueNode {

        boolean g1() {
            return true;
        }

        @Specialization(guards = "g1()")
        Object f0() {
            return null;
        }

        @Specialization(replaces = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ReplacesGuard2 extends ValueNode {

        boolean g1() {
            return true;
        }

        @Specialization
        Object f0() {
            return null;
        }

        @ExpectError({"Specialization is not reachable. It is shadowed by f0()."})
        @Specialization(guards = "g1()", replaces = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ReplacesGuard3 extends ValueNode {

        boolean g1() {
            return true;
        }

        @Specialization(guards = "g1()")
        Object f0() {
            return null;
        }

        @Specialization(guards = "!g1()", replaces = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ReplacesGuard4 extends ValueNode {

        boolean g1() {
            return true;
        }

        boolean g2() {
            return true;
        }

        @Specialization(guards = "g1()")
        Object f0() {
            return null;
        }

        @Specialization(guards = "g2()", replaces = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ReplacesGuard5 extends ValueNode {

        boolean g1() {
            return true;
        }

        boolean g2() {
            return true;
        }

        @Specialization(guards = "g1()")
        Object f0() {
            return null;
        }

        @Specialization(guards = "g2()", replaces = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ReplacesGuard6 extends ValueNode {

        boolean g1() {
            return true;
        }

        boolean g2() {
            return true;
        }

        @Specialization(guards = "g1()")
        Object f0() {
            return null;
        }

        @Specialization(guards = "!g2()", replaces = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ReplacesGuard7 extends ValueNode {

        boolean g1() {
            return true;
        }

        boolean g2() {
            return true;
        }

        @Specialization(guards = {"g1()", "g2()"})
        Object f0() {
            return null;
        }

        @Specialization(guards = "g2()", replaces = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ReplacesThrowable1 extends ValueNode {

        @Specialization(rewriteOn = RuntimeException.class)
        Object f0() throws RuntimeException {
            throw new RuntimeException();
        }

        @Specialization(replaces = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ReplacesThrowable2 extends ValueNode {

        @Specialization(rewriteOn = RuntimeException.class)
        Object f0() throws RuntimeException {
            throw new RuntimeException();
        }

        @Specialization(replaces = "f0", rewriteOn = RuntimeException.class)
        Object f1() throws RuntimeException {
            throw new RuntimeException();
        }

        @Specialization(replaces = "f1")
        Object f2() {
            return null;
        }
    }

    @Test
    public void testPolymorphicToMonomorphic0() {
        TestRootNode<PolymorphicToMonomorphic0> root = createRoot(PolymorphicToMonomorphic0Factory.getInstance());
        assertThat((int) executeWith(root, 1), is(1));
        assertThat((int) executeWith(root, 2), is(2));
        assertThat((int) executeWith(root, 3), is(3));
        assertThat(root.getNode().getCost(), is(NodeCost.MONOMORPHIC));
    }

    @NodeChild("a")
    static class PolymorphicToMonomorphic0 extends ValueNode {

        @Specialization(guards = "a == 1")
        int do1(int a) {
            return a;
        }

        @Specialization(guards = "a == 2")
        int do2(int a) {
            return a;
        }

        @Specialization(replaces = {"do1", "do2"})
        int do3(int a) {
            return a;
        }

    }
}
