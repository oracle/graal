/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ChildrenNode;

@SuppressWarnings("unused")
public class BooleanLiteralGuardsTest {

    @Test
    public void testCompareObjectsBoolean() {
        CallTarget root = createCallTarget(BooleanLiteralGuardsTestFactory.CompareObjectsTrueFalseNodeFactory.getInstance());
        assertEquals("do1", root.call(true));
        assertEquals("do2", root.call(false));
        assertEquals("do3", root.call("42"));
    }

    abstract static class CompareObjectsTrueFalseNode extends ChildrenNode {
        @Specialization(guards = "value == true")
        String do1(boolean value) {
            return "do1";
        }

        @Specialization(guards = "value == false")
        String do2(boolean value) {
            return "do2";
        }

        @Specialization
        String do3(Object value) {
            return "do3";
        }
    }

    @Test
    public void testCompareObjectsNegatedBoolean() {
        CallTarget root = createCallTarget(BooleanLiteralGuardsTestFactory.CompareObjectsTrueFalseNegationNodeFactory.getInstance());
        assertEquals("do1", root.call(true));
        assertEquals("do2", root.call(false));
        assertEquals("do3", root.call("42"));
    }

    abstract static class CompareObjectsTrueFalseNegationNode extends ChildrenNode {
        @Specialization(guards = "value == !false")
        String do1(boolean value) {
            return "do1";
        }

        @Specialization(guards = "value == !true")
        String do2(boolean value) {
            return "do2";
        }

        @Specialization
        String do3(Object value) {
            return "do3";
        }
    }

    abstract static class ErrorBooleanIntComparison1t extends ChildrenNode {
        @ExpectError("Error parsing expression 'value == true': Incompatible operand types int and boolean.")
        @Specialization(guards = "value == true")
        String do1(int value) {
            return "do1";
        }
    }

    abstract static class ErrorBooleanIntComparison1f extends ChildrenNode {
        @ExpectError("Error parsing expression 'value == false': Incompatible operand types int and boolean.")
        @Specialization(guards = "value == false")
        String do1(int value) {
            return "do1";
        }
    }

    abstract static class ErrorBooleanIntComparison2t extends ChildrenNode {
        @ExpectError("Error parsing expression '1 == true': Incompatible operand types int and boolean.")
        @Specialization(guards = "1 == true")
        String do1(int value) {
            return "do1";
        }
    }

    abstract static class ErrorBooleanIntComparison2f extends ChildrenNode {
        @ExpectError("Error parsing expression '1 == false': Incompatible operand types int and boolean.")
        @Specialization(guards = "1 == false")
        String do1(int value) {
            return "do1";
        }
    }
}
