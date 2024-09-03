/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.dsl.test.TestHelper.array;
import static com.oracle.truffle.api.dsl.test.TestHelper.assertRuns;
import static com.oracle.truffle.api.dsl.test.TestHelper.createRoot;
import static com.oracle.truffle.api.dsl.test.TestHelper.executeWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.Introspectable;
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

@SuppressWarnings({"truffle-inlining", "truffle-neverdefault", "truffle-sharing", "unused"})
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
    @Introspectable
    abstract static class ReplaceMaxLimitNode extends ValueNode {

        static boolean isOne(int a) {
            return a == 1;
        }

        @Specialization(guards = "a == 0")
        int s0(int a) {
            return a;
        }

        @Specialization(guards = "a == 1")
        int s1(int a) {
            return a;
        }

        @Specialization(guards = "a == 2")
        int s2(int a) {
            return a;
        }

        @Specialization(guards = "a == 3")
        int s3(int a) {
            return a;
        }

        @Specialization(guards = "a == 4")
        int s4(int a) {
            return a;
        }

        @Specialization(guards = "a == 5")
        int s5(int a) {
            return a;
        }

        @Specialization(guards = "a == 6")
        int s6(int a) {
            return a;
        }

        @Specialization(guards = "a ==7")
        int s7(int a) {
            return a;
        }

        @Specialization(guards = "a == 8")
        int s8(int a) {
            return a;
        }

        @Specialization(guards = "a == 9")
        int s9(int a) {
            return a;
        }

        @Specialization(guards = "a == 10")
        int s10(int a) {
            return a;
        }

        @Specialization(guards = "a == 11")
        int s11(int a) {
            return a;
        }

        @Specialization(guards = "a == 12")
        int s12(int a) {
            return a;
        }

        @Specialization(guards = "a == 13")
        int s13(int a) {
            return a;
        }

        @Specialization(guards = "a == 14")
        int s14(int a) {
            return a;
        }

        @Specialization(guards = "a == 15")
        int s15(int a) {
            return a;
        }

        @Specialization(guards = "a == 16")
        int s16(int a) {
            return a;
        }

        @Specialization(guards = "a == 17")
        int s17(int a) {
            return a;
        }

        @Specialization(guards = "a == 18")
        int s18(int a) {
            return a;
        }

        @Specialization(guards = "a == 19")
        int s19(int a) {
            return a;
        }

        @Specialization(guards = "a == 20")
        int s20(int a) {
            return a;
        }

        @Specialization(guards = "a == 21")
        int s21(int a) {
            return a;
        }

        @Specialization(guards = "a == 22")
        int s22(int a) {
            return a;
        }

        @Specialization(guards = "a == 23")
        int s23(int a) {
            return a;
        }

        @Specialization(guards = "a == 24")
        int s24(int a) {
            return a;
        }

        @Specialization(guards = "a == 25")
        int s25(int a) {
            return a;
        }

        @Specialization(guards = "a == 26")
        int s26(int a) {
            return a;
        }

        @Specialization(guards = "a == 27")
        int s27(int a) {
            return a;
        }

        @Specialization(guards = "a == 28")
        int s28(int a) {
            return a;
        }

        @Specialization(guards = "a == 29")
        int s29(int a) {
            return a;
        }

        @Specialization(guards = "a == 30")
        int s30(int a) {
            return a;
        }

        @Specialization(replaces = {"s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", //
                        "s10", "s11", "s12", "s13", "s14", "s15", "s16", "s17", "s18", "s19", //
                        "s20", "s21", "s22", "s23", "s24", "s25", "s26", "s27", "s28", "s29", //
                        "s30",
        })
        int generic(int a) {
            return a;
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

        @ExpectError({"The replaced specialization 'f1' must be declared before the replacing specialization."})
        @Specialization(replaces = "f1")
        int f0(int a) {
            return a;
        }

        @Specialization
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

        @Idempotent
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

        @Idempotent
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
        @Idempotent
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
        @Idempotent
        boolean g1() {
            return true;
        }

        @Idempotent
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
        @Idempotent
        boolean g1() {
            return true;
        }

        @Idempotent
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
        @Idempotent
        boolean g1() {
            return true;
        }

        @Idempotent
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
        @Idempotent
        boolean g1() {
            return true;
        }

        @Idempotent
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
