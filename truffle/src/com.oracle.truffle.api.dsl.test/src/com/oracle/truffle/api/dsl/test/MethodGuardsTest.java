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

import static com.oracle.truffle.api.dsl.test.TestHelper.createCallTarget;
import static com.oracle.truffle.api.dsl.test.TestHelper.getNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardCompareWithFieldTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardComplexTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardEqualByteIntTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardEqualIntLongTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardEqualLongIntTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardEqualShortIntTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardEqualTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardFieldTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardGreaterEqualTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardGreaterTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardLessEqualTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardLessTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardMethodTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardMultipleAndMethodTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardMultipleOrMethodTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardNotTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardOrTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardStaticFieldTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardStaticFinalFieldCompareTestFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardUnboundMethodTestFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

@SuppressWarnings("unused")
public class MethodGuardsTest {

    @Test
    public void testGuardEqual() {
        CallTarget root = createCallTarget(GuardEqualTestFactory.getInstance());
        assertEquals("do1", root.call(1));
        assertEquals("do2", root.call(2));
        assertEquals("do1", root.call(1));
    }

    @NodeChild
    static class GuardEqualTest extends ValueNode {
        @Specialization(guards = "value == 1")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testGuardEqualIntLong() {
        CallTarget root = createCallTarget(GuardEqualIntLongTestFactory.getInstance());
        assertEquals("do1", root.call(1));
        assertEquals("do2", root.call(2));
        assertEquals("do1", root.call(1));
    }

    @NodeChild
    static class GuardEqualIntLongTest extends ValueNode {
        @Specialization(guards = "1 == value")
        static String do1(long value) {
            return "do1";
        }

        @Specialization
        static String do2(long value) {
            return "do2";
        }
    }

    @Test
    public void testGuardEqualByteInt() {
        CallTarget root = createCallTarget(GuardEqualByteIntTestFactory.getInstance());
        assertEquals("do1", root.call((byte) 1));
        assertEquals("do2", root.call((byte) 2));
        assertEquals("do1", root.call((byte) 1));
    }

    @NodeChild
    static class GuardEqualByteIntTest extends ValueNode {
        @Specialization(guards = "value == 1")
        static String do1(byte value) {
            return "do1";
        }

        @Specialization
        static String do2(byte value) {
            return "do2";
        }
    }

    @Test
    public void testGuardEqualShortInt() {
        CallTarget root = createCallTarget(GuardEqualShortIntTestFactory.getInstance());
        assertEquals("do1", root.call((short) 1));
        assertEquals("do2", root.call((short) 2));
        assertEquals("do1", root.call((short) 1));
    }

    @NodeChild
    static class GuardEqualShortIntTest extends ValueNode {
        @Specialization(guards = "value == 1")
        static String do1(short value) {
            return "do1";
        }

        @Specialization
        static String do2(short value) {
            return "do2";
        }
    }

    @Test
    public void testGuardEqualLongInt() {
        CallTarget root = createCallTarget(GuardEqualLongIntTestFactory.getInstance());
        assertEquals("do1", root.call(1));
        assertEquals("do2", root.call(2));
        assertEquals("do1", root.call(1));
    }

    @NodeChild
    static class GuardEqualLongIntTest extends ValueNode {
        @Specialization(guards = "value == 1")
        static String do1(long value) {
            return "do1";
        }

        @Specialization
        static String do2(long value) {
            return "do2";
        }
    }

    @Test
    public void testGuardLessEqual() {
        CallTarget root = createCallTarget(GuardLessEqualTestFactory.getInstance());
        assertEquals("do1", root.call(1));
        assertEquals("do1", root.call(0));
        assertEquals("do2", root.call(2));
        assertEquals("do1", root.call(0));
    }

    @NodeChild
    static class GuardLessEqualTest extends ValueNode {
        @Specialization(guards = "value <= 1")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testGuardLess() {
        CallTarget root = createCallTarget(GuardLessTestFactory.getInstance());
        assertEquals("do1", root.call(0));
        assertEquals("do2", root.call(1));
        assertEquals("do2", root.call(2));
        assertEquals("do1", root.call(-1));
    }

    @NodeChild
    static class GuardLessTest extends ValueNode {
        @Specialization(guards = "value < 1")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testGuardGreaterEqual() {
        CallTarget root = createCallTarget(GuardGreaterEqualTestFactory.getInstance());
        assertEquals("do1", root.call(1));
        assertEquals("do2", root.call(0));
        assertEquals("do1", root.call(2));
        assertEquals("do2", root.call(0));
    }

    @NodeChild
    static class GuardGreaterEqualTest extends ValueNode {
        @Specialization(guards = "value >= 1")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testGuardGreater() {
        CallTarget root = createCallTarget(GuardGreaterTestFactory.getInstance());
        assertEquals("do1", root.call(2));
        assertEquals("do2", root.call(0));
        assertEquals("do2", root.call(1));
        assertEquals("do2", root.call(0));
    }

    @NodeChild
    static class GuardGreaterTest extends ValueNode {
        @Specialization(guards = "value > 1")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testGuardOr() {
        CallTarget root = createCallTarget(GuardOrTestFactory.getInstance());
        assertEquals("do1", root.call(1));
        assertEquals("do1", root.call(0));
        assertEquals("do2", root.call(2));
        assertEquals("do2", root.call(-1));
    }

    @NodeChild
    static class GuardOrTest extends ValueNode {
        @Specialization(guards = "value == 1 || value == 0")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testGuardNot() {
        CallTarget root = createCallTarget(GuardNotTestFactory.getInstance());
        assertEquals("do1", root.call(0));
        assertEquals("do1", root.call(2));
        assertEquals("do2", root.call(1));
        assertEquals("do1", root.call(0));
    }

    @NodeChild
    static class GuardNotTest extends ValueNode {
        @Specialization(guards = "!(value == 1)")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testGuardField() {
        CallTarget root = createCallTarget(GuardFieldTestFactory.getInstance());
        GuardFieldTest node = getNode(root);
        node.field = true;
        assertEquals("do1", root.call(0));
        assertEquals("do1", root.call(2));

        node.field = false;
        try {
            root.call(2);
            fail("expected Assertion failed");
        } catch (AssertionError e) {
        }
    }

    @NodeChild
    static class GuardFieldTest extends ValueNode {

        boolean field;

        @Specialization(guards = "field")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testGuardCompareWithField() {
        CallTarget root = createCallTarget(GuardCompareWithFieldTestFactory.getInstance());
        GuardCompareWithFieldTest node = getNode(root);
        node.field = 1;
        assertEquals("do1", root.call(1));
        assertEquals("do2", root.call(2));

        node.field = 2;
        assertEquals("do2", root.call(1));
        assertEquals("do1", root.call(2));
    }

    @NodeChild
    static class GuardCompareWithFieldTest extends ValueNode {

        int field;

        @Specialization(guards = "value == field")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testGuardStaticField() {
        CallTarget root = createCallTarget(GuardStaticFieldTestFactory.getInstance());
        GuardStaticFieldTest.field = true;
        assertEquals("do1", root.call(1));
        assertEquals("do1", root.call(2));
        GuardStaticFieldTest.field = false;
        try {
            root.call(2);
            fail("expected Assertion failed");
        } catch (AssertionError e) {
        }
    }

    @NodeChild
    static class GuardStaticFieldTest extends ValueNode {

        static boolean field;

        @Specialization(guards = "field")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testGuardStaticFinalFieldCompare() {
        CallTarget root = createCallTarget(GuardStaticFinalFieldCompareTestFactory.getInstance());
        GuardStaticFieldTest.field = true;
        assertEquals("do1", root.call(1));
        assertEquals("do2", root.call(2));
    }

    @NodeChild
    static class GuardStaticFinalFieldCompareTest extends ValueNode {

        protected static final int FIELD = 1;

        @Specialization(guards = "value == FIELD")
        static String do1(int value) {
            return "do1";
        }

        @Specialization(guards = "value != FIELD")
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testGuardMethod() {
        CallTarget root = createCallTarget(GuardMethodTestFactory.getInstance());
        assertEquals("do1", root.call(1));
        assertEquals("do2", root.call(2));
        assertEquals("do1", root.call(1));
        assertEquals("do2", root.call(0));
    }

    @NodeChild
    static class GuardMethodTest extends ValueNode {

        @Specialization(guards = "method(value)")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }

        boolean method(int value) {
            return value == 1;
        }
    }

    @Test
    public void testGuardUnboundMethodField() {
        CallTarget root = createCallTarget(GuardUnboundMethodTestFactory.getInstance());
        GuardUnboundMethodTest node = getNode(root);
        node.hiddenValue = true;
        assertEquals("do1", root.call(1));
        assertEquals("do1", root.call(2));
        node.hiddenValue = false;
        try {
            root.call(2);
            fail("expected Assertion failed");
        } catch (AssertionError e) {
        }
    }

    @NodeChild
    static class GuardUnboundMethodTest extends ValueNode {

        private boolean hiddenValue;

        @Specialization(guards = "method()")
        static String do1(int value) {
            return "do1";
        }

        boolean method() {
            return hiddenValue;
        }
    }

    @Test
    public void testStaticGuardMethod() {
        CallTarget root = createCallTarget(GuardMethodTestFactory.getInstance());
        assertEquals("do1", root.call(1));
        assertEquals("do2", root.call(2));
        assertEquals("do1", root.call(1));
        assertEquals("do2", root.call(0));
    }

    @NodeChild
    static class StaticGuardMethodTest extends ValueNode {

        @Specialization(guards = "method(value)")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }

        static boolean method(int value) {
            return value == 1;
        }
    }

    @Test
    public void testMultipleGuardAndMethod() {
        CallTarget root = createCallTarget(GuardMultipleAndMethodTestFactory.getInstance());
        assertEquals("do1", root.call(1));
        assertEquals("do1", root.call(2));
        assertEquals("do2", root.call(3));
        assertEquals("do2", root.call(0));
    }

    @NodeChild
    static class GuardMultipleAndMethodTest extends ValueNode {

        @Specialization(guards = {"method1(value)", "method2(value)"})
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }

        boolean method1(int value) {
            return value >= 1;
        }

        boolean method2(int value) {
            return value <= 2;
        }
    }

    @Test
    public void testMultipleGuardOrMethod() {
        CallTarget root = createCallTarget(GuardMultipleOrMethodTestFactory.getInstance());
        assertEquals("do1", root.call(1));
        assertEquals("do1", root.call(2));
        assertEquals("do2", root.call(3));
        assertEquals("do2", root.call(0));
    }

    @NodeChild
    static class GuardMultipleOrMethodTest extends ValueNode {

        @Specialization(guards = {"method1(value) || method2(value)"})
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }

        boolean method1(int value) {
            return value == 1;
        }

        boolean method2(int value) {
            return value == 2;
        }
    }

    @Test
    public void testComplexGuard() {
        CallTarget root = createCallTarget(GuardComplexTestFactory.getInstance());
        assertEquals("do1", root.call(1));
        assertEquals("do1", root.call(2));
        assertEquals("do2", root.call(3));
        assertEquals("do1", root.call(0));
    }

    @NodeChild
    static class GuardComplexTest extends ValueNode {

        int field1 = 1;
        static int field2 = 2;

        @Specialization(guards = {"method2(method1(field1 == 1), value <= 2)", "field2 == 2"})
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }

        static boolean method1(boolean value) {
            return value;
        }

        boolean method2(boolean value1, boolean value2) {
            return value1 && value2;
        }
    }

    @NodeChild
    static class ErrorGuardNotTest extends ValueNode {
        @ExpectError("Error parsing expression '!value == 1': The operator ! is undefined for the argument type int.")
        @Specialization(guards = "!value == 1")
        static String do1(int value) {
            return "do1";
        }
    }

    @NodeChild
    static class ErrorIncompatibleReturnTypeTest extends ValueNode {

        @ExpectError("Incompatible return type int. Guards must return boolean.")
        @Specialization(guards = "1")
        static String do1(int value) {
            return "do1";
        }

    }

}
