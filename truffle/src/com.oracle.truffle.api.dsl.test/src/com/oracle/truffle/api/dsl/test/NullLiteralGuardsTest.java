/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
