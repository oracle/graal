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
import com.oracle.truffle.api.dsl.internal.*;
import com.oracle.truffle.api.dsl.test.ContainsTestFactory.Contains1Factory;
import com.oracle.truffle.api.dsl.test.ContainsTestFactory.Contains2Factory;
import com.oracle.truffle.api.dsl.test.ContainsTestFactory.Contains3Factory;
import com.oracle.truffle.api.dsl.test.ContainsTestFactory.Contains4Factory;
import com.oracle.truffle.api.dsl.test.TestHelper.ExecutionListener;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.*;

@SuppressWarnings("unused")
public class ContainsTest {

    /*
     * Tests a simple monomorphic inclusion.
     */
    @Test
    public void testContains1() {
        assertRuns(Contains1Factory.getInstance(), //
                        array(1, "a", 2, "b"), //
                        array(2, "aa", 3, "ba"),//
                        new ExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                if (value instanceof String) {
                                    // assert that the final specialization is always Object
                                    Assert.assertEquals(Object.class, ((DSLNode) node.getNode()).getMetadata0().getSpecializedTypes()[0]);
                                }
                            }
                        });
    }

    @NodeChild("a")
    abstract static class Contains1 extends ValueNode {

        @Specialization
        int f1(int a) {
            return a + 1;
        }

        @Specialization(contains = "f1")
        Object f2(Object a) {
            if (a instanceof Integer) {
                return ((Integer) a) + 1;
            }
            return a + "a";
        }
    }

    /*
     * Tests an inclusion in within a polymorphic chain.
     */
    @Test
    public void testContains2() {
        assertRuns(Contains2Factory.getInstance(), //
                        array(true, 1, 0, false), //
                        array(false, -1, 1, true) //
        );
    }

    @NodeChild("a")
    abstract static class Contains2 extends ValueNode {

        static boolean isZero(int a) {
            return a == 0;
        }

        @Specialization(guards = "isZero")
        int f1(int a) {
            return a + 1;
        }

        @Specialization(contains = "f1")
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
    public void testContains3() {
        assertRuns(Contains3Factory.getInstance(), //
                        array(2, 1, 2, -3, -4), //
                        array(-2, 2, -2, -3, -4),//
                        new ExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                // assert that we are always monomorphic
                                Assert.assertEquals(NodeCost.MONOMORPHIC, node.getNode().getCost());
                            }
                        });
    }

    @NodeChild("a")
    abstract static class Contains3 extends ValueNode {

        static boolean isGreaterZero(int a) {
            return a > 0;
        }

        @Implies("isGreaterZero")
        static boolean isOne(int a) {
            return a == 1;
        }

        @Specialization(guards = {"isOne"})
        int f1(int a) {
            return a + 1;
        }

        @Specialization(contains = "f1", guards = {"isGreaterZero"})
        int f2(int a) {
            if (a == 1) {
                return 2;
            }
            return -a;
        }

        @Specialization(contains = "f2")
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
    public void testContains4() {
        assertRuns(Contains4Factory.getInstance(), //
                        array(-1, 0, 1, 2), //
                        array(1, 0, 1, 2),//
                        new ExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                Assert.assertEquals(NodeCost.MONOMORPHIC, node.getNode().getCost());
                            }
                        });
    }

    @NodeChild("a")
    abstract static class Contains4 extends ValueNode {

        static boolean isGreaterEqualZero(int a) {
            return a >= 0;
        }

        @Implies("isGreaterEqualZero")
        static boolean isOne(int a) {
            return a == 1;
        }

        @Specialization(guards = {"isOne"})
        int f0(int a) {
            return 1;
        }

        @Specialization(contains = "f0", guards = {"isGreaterEqualZero"})
        int f1(int a) {
            return a;
        }

        @Specialization(contains = {"f1"})
        int f2(int a) {
            return Math.abs(a);
        }

    }

    @NodeChild("a")
    abstract static class ContainsError1 extends ValueNode {
        @ExpectError("The contained specialization 'f1' must be declared before the containing specialization.")
        @Specialization(contains = "f1")
        int f0(int a) {
            return a;
        }

        @Specialization
        Object f1(String a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ContainsError2 extends ValueNode {

        @ExpectError("The referenced specialization 'does not exist' could not be found.")
        @Specialization(contains = "does not exist")
        int f0(int a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ContainsError3 extends ValueNode {

        @Specialization
        int f0(int a) {
            return a;
        }

        @ExpectError("Duplicate contains declaration 'f0'.")
        @Specialization(contains = {"f0", "f0"})
        Object f1(double a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ContainsError4 extends ValueNode {

        @ExpectError("Circular contained specialization 'f1(double)' found.")
        @Specialization(contains = {"f1"})
        Object f1(double a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ContainsError5 extends ValueNode {

        @ExpectError({"Circular contained specialization 'f0(int)' found.", "Circular contained specialization 'f1(double)' found.",
                        "The contained specialization 'f1' must be declared before the containing specialization."})
        @Specialization(contains = "f1")
        int f0(int a) {
            return a;
        }

        @ExpectError("Circular contained specialization 'f1(double)' found.")
        @Specialization(contains = {"f0"})
        Object f1(double a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ContainsType1 extends ValueNode {
        @Specialization
        int f0(int a) {
            return a;
        }

        @ExpectError("Specialization is not reachable. It is shadowed by f0(int).")
        @Specialization(contains = "f0")
        Object f1(int a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ContainsType2 extends ValueNode {
        @Specialization
        int f0(int a) {
            return a;
        }

        @Specialization(contains = "f0")
        Object f1(Object a) {
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ContainsType3 extends ValueNode {
        @Specialization
        int f0(int a) {
            return a;
        }

        @Specialization(contains = "f0")
        Object f1(double a) { // implicit type
            return a;
        }
    }

    @NodeChild("a")
    abstract static class ContainsType4 extends ValueNode {
        @Specialization
        double f0(double a) {
            return a;
        }

        @ExpectError({"Specialization is not reachable. It is shadowed by f0(double).", "The contained specialization 'f0' is not fully compatible.%"})
        @Specialization(contains = "f0")
        int f1(int a) { // implicit type
            return a;
        }
    }

    @NodeChildren({@NodeChild("a"), @NodeChild("b")})
    abstract static class ContainsType5 extends ValueNode {
        @Specialization
        Object f0(Object a, int b) {
            return a;
        }

        @ExpectError("The contained specialization 'f0' is not fully compatible.%")
        @Specialization(contains = "f0")
        Object f1(int a, Object b) {
            return a;
        }
    }

    @NodeChildren({@NodeChild("a"), @NodeChild("b")})
    abstract static class ContainsType6 extends ValueNode {
        @Specialization
        Object f0(double a, int b) {
            return a;
        }

        @ExpectError("The contained specialization 'f0' is not fully compatible.%")
        @Specialization(contains = "f0")
        Object f1(int a, double b) { // implicit type
            return a;
        }
    }

    abstract static class ContainsGuard1 extends ValueNode {

        boolean g1() {
            return true;
        }

        @Specialization(guards = "g1")
        Object f0() {
            return null;
        }

        @Specialization(contains = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ContainsGuard2 extends ValueNode {

        boolean g1() {
            return true;
        }

        @Specialization
        Object f0() {
            return null;
        }

        @ExpectError({"Specialization is not reachable. It is shadowed by f0().", "The contained specialization 'f0' is not fully compatible.%"})
        @Specialization(guards = "g1", contains = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ContainsGuard3 extends ValueNode {

        boolean g1() {
            return true;
        }

        @Specialization(guards = "g1")
        Object f0() {
            return null;
        }

        @ExpectError({"The contained specialization 'f0' is not fully compatible.%"})
        @Specialization(guards = "!g1", contains = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ContainsGuard4 extends ValueNode {

        boolean g1() {
            return true;
        }

        boolean g2() {
            return true;
        }

        @Specialization(guards = "g1")
        Object f0() {
            return null;
        }

        @ExpectError({"The contained specialization 'f0' is not fully compatible.%"})
        @Specialization(guards = "g2", contains = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ContainsGuard5 extends ValueNode {

        @Implies("g2")
        boolean g1() {
            return true;
        }

        boolean g2() {
            return true;
        }

        @Specialization(guards = "g1")
        Object f0() {
            return null;
        }

        @Specialization(guards = "g2", contains = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ContainsGuard6 extends ValueNode {

        @Implies("!g2")
        boolean g1() {
            return true;
        }

        boolean g2() {
            return true;
        }

        @Specialization(guards = "g1")
        Object f0() {
            return null;
        }

        @Specialization(guards = "!g2", contains = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ContainsGuard7 extends ValueNode {

        boolean g1() {
            return true;
        }

        boolean g2() {
            return true;
        }

        @Specialization(guards = {"g1", "g2"})
        Object f0() {
            return null;
        }

        @Specialization(guards = "g2", contains = "f0")
        Object f1() {
            return null;
        }
    }

    @NodeAssumptions("a1")
    abstract static class ContainsAssumption1 extends ValueNode {

        @Specialization(assumptions = "a1")
        Object f0() {
            return null;
        }

        @Specialization(contains = "f0")
        Object f1() {
            return null;
        }
    }

    @NodeAssumptions("a1")
    abstract static class ContainsAssumption2 extends ValueNode {

        @Specialization
        Object f0() {
            return null;
        }

        @ExpectError({"Specialization is not reachable. It is shadowed by f0().", "The contained specialization 'f0' is not fully compatible.%"})
        @Specialization(contains = "f0", assumptions = "a1")
        Object f1() {
            return null;
        }
    }

    @NodeAssumptions({"a1", "a2"})
    abstract static class ContainsAssumption3 extends ValueNode {

        @Specialization(assumptions = "a1")
        Object f0() {
            return null;
        }

        @ExpectError({"The contained specialization 'f0' is not fully compatible.%"})
        @Specialization(contains = "f0", assumptions = "a2")
        Object f1() {
            return null;
        }
    }

    @NodeAssumptions({"a1", "a2"})
    abstract static class ContainsAssumption4 extends ValueNode {

        @Specialization(assumptions = {"a1", "a2"})
        Object f0() {
            return null;
        }

        @Specialization(contains = "f0", assumptions = "a1")
        Object f1() {
            return null;
        }
    }

    @NodeAssumptions({"a1", "a2"})
    abstract static class ContainsAssumption5 extends ValueNode {

        @Specialization(assumptions = {"a2", "a1"})
        Object f0() {
            return null;
        }

        @Specialization(contains = "f0", assumptions = "a1")
        Object f1() {
            return null;
        }
    }

    abstract static class ContainsThrowable1 extends ValueNode {

        @Specialization(rewriteOn = RuntimeException.class)
        Object f0() throws RuntimeException {
            throw new RuntimeException();
        }

        @Specialization(contains = "f0")
        Object f1() {
            return null;
        }
    }

    abstract static class ContainsThrowable2 extends ValueNode {

        @Specialization(rewriteOn = RuntimeException.class)
        Object f0() throws RuntimeException {
            throw new RuntimeException();
        }

        @Specialization(contains = "f0", rewriteOn = RuntimeException.class)
        Object f1() throws RuntimeException {
            throw new RuntimeException();
        }

        @Specialization(contains = "f1")
        Object f2() {
            return null;
        }
    }

}
