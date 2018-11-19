/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.NullLiteralGuardsTestFactory.CompareNotNullNodeFactory;
import com.oracle.truffle.api.dsl.test.NullLiteralGuardsTestFactory.CompareObjectsNullNodeFactory;
import com.oracle.truffle.api.dsl.test.NullLiteralGuardsTestFactory.CompareStringNullNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ChildrenNode;

@SuppressWarnings("unused")
public class NullLiteralGuardsTest {

    @Test
    public void testCompareObjectsNull() {
        CallTarget root = createCallTarget(CompareObjectsNullNodeFactory.getInstance());
        assertEquals("do1", root.call((Object) null));
        assertEquals("do2", root.call("42"));
    }

    abstract static class CompareObjectsNullNode extends ChildrenNode {
        @Specialization(guards = "value == null")
        String do1(Object value) {
            return "do1";
        }

        @Specialization
        String do2(Object value) {
            return "do2";
        }
    }

    @Test
    public void testCompareStringNull() {
        CallTarget root = createCallTarget(CompareStringNullNodeFactory.getInstance());
        assertEquals("do1", root.call("42"));
        assertEquals("do2", root.call((Object) null));
    }

    abstract static class CompareStringNullNode extends ChildrenNode {
        @Specialization(guards = "value != null")
        String do1(String value) {
            return "do1";
        }

        @Specialization
        String do2(Object value) {
            return "do2";
        }
    }

    @Test
    public void testCompareNotNull() {
        CallTarget root = createCallTarget(CompareNotNullNodeFactory.getInstance());
        assertEquals("do1", root.call("42"));
        assertEquals("do2", root.call((Object) null));
    }

    abstract static class CompareNotNullNode extends ChildrenNode {
        @Specialization(guards = "value != null")
        String do1(Object value) {
            return "do1";
        }

        @Specialization
        String do2(Object value) {
            return "do2";
        }
    }

    abstract static class ErrorNullIntComparison1 extends ChildrenNode {
        @ExpectError("Error parsing expression 'value == null': Incompatible operand types int and null.")
        @Specialization(guards = "value == null")
        String do1(int value) {
            return "do1";
        }
    }

    abstract static class ErrorNullIntComparison2 extends ChildrenNode {
        @ExpectError("Error parsing expression '1 == null': Incompatible operand types int and null.")
        @Specialization(guards = "1 == null")
        String do1(int value) {
            return "do1";
        }
    }

    abstract static class ErrorNullNullComparison extends ChildrenNode {
        @ExpectError("Error parsing expression 'null == null': The operator == is undefined for the argument type(s) null null.")
        @Specialization(guards = "null == null")
        String do1(int value) {
            return "do1";
        }
    }

    abstract static class ErrorObjectVoidComparison extends ChildrenNode {
        protected static void returnVoid() {
        }

        @ExpectError("Error parsing expression 'value == returnVoid()': Incompatible operand types Object and void.")
        @Specialization(guards = "value == returnVoid()")
        String do1(Object value) {
            return "do1";
        }
    }

}
