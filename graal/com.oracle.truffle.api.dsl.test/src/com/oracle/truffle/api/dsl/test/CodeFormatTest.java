/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.CodeFormatTestFactory.LineWrappingTestFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

/**
 * Tests the generated code compiles without warnings for unusual large guard names.
 */
public class CodeFormatTest {

    @Test
    public void test() {
        Assert.assertEquals(42, TestHelper.createCallTarget(LineWrappingTestFactory.create()).call());
    }

    abstract static class LineWrappingTest extends ValueNode {

        public LineWrappingTest() {
        }

        protected static boolean guardWithaReeeeeeeeaaaaaaaaaaalllllllllyyyyyyyyLLLLLLLLLLLLLoooooooonnnnnnngggggggName1() {
            return true;
        }

        protected static boolean guardWithaReeeeeeeeaaaaaaaaaaalllllllllyyyyyyyyLLLLLLLLLLLLLoooooooonnnnnnngggggggName2() {
            return true;
        }

        @Specialization(guards = {"guardWithaReeeeeeeeaaaaaaaaaaalllllllllyyyyyyyyLLLLLLLLLLLLLoooooooonnnnnnngggggggName1",
                        "guardWithaReeeeeeeeaaaaaaaaaaalllllllllyyyyyyyyLLLLLLLLLLLLLoooooooonnnnnnngggggggName2",
                        "guardWithaReeeeeeeeaaaaaaaaaaalllllllllyyyyyyyyLLLLLLLLLLLLLoooooooonnnnnnngggggggName1",
                        "guardWithaReeeeeeeeaaaaaaaaaaalllllllllyyyyyyyyLLLLLLLLLLLLLoooooooonnnnnnngggggggName2",
                        "guardWithaReeeeeeeeaaaaaaaaaaalllllllllyyyyyyyyLLLLLLLLLLLLLoooooooonnnnnnngggggggName1"})
        public int execute() {
            return 42;
        }
    }

}
