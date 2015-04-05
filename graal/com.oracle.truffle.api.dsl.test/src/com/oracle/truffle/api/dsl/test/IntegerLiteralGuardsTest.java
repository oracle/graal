/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.IntegerLiteralGuardsTestFactory.BinaryLiteralTestFactory;
import com.oracle.truffle.api.dsl.test.IntegerLiteralGuardsTestFactory.DecimalLiteralTestFactory;
import com.oracle.truffle.api.dsl.test.IntegerLiteralGuardsTestFactory.HexLiteralTestFactory;
import com.oracle.truffle.api.dsl.test.IntegerLiteralGuardsTestFactory.OctalLiteralTestFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

@SuppressWarnings("unused")
public class IntegerLiteralGuardsTest {

    @Test
    public void testDecimalLiteral() {
        CallTarget root = createCallTarget(DecimalLiteralTestFactory.getInstance());
        assertEquals("do1", root.call(14));
    }

    @NodeChild
    static class DecimalLiteralTest extends ValueNode {
        @Specialization(guards = "value == 14")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testHexLiteral() {
        CallTarget root = createCallTarget(HexLiteralTestFactory.getInstance());
        assertEquals("do1", root.call(20));
    }

    @NodeChild
    static class HexLiteralTest extends ValueNode {
        @Specialization(guards = "value == 0x14")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testOctalLiteral() {
        CallTarget root = createCallTarget(OctalLiteralTestFactory.getInstance());
        assertEquals("do1", root.call(12));
    }

    @NodeChild
    static class OctalLiteralTest extends ValueNode {
        @Specialization(guards = "value == 014")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

    @Test
    public void testBinaryLiteral() {
        CallTarget root = createCallTarget(BinaryLiteralTestFactory.getInstance());
        assertEquals("do1", root.call(50));
    }

    @NodeChild
    static class BinaryLiteralTest extends ValueNode {
        @Specialization(guards = "value == 0b110010")
        static String do1(int value) {
            return "do1";
        }

        @Specialization
        static String do2(int value) {
            return "do2";
        }
    }

}
