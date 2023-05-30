/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Arm Limited and affiliates. All rights reserved.
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
package org.graalvm.compiler.core.aarch64.test;

import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp;
import org.junit.Test;

import java.util.function.Predicate;

public class AArch64NegateShiftTest extends AArch64MatchRuleTest {
    private static final Predicate<LIRInstruction> predicate = op -> (op instanceof AArch64ArithmeticOp.BinaryShiftOp);

    /**
     * negateShift match rule tests for shift operations with int type.
     */
    public int negShiftLeftLogicInt(int input) {
        return -(input << 5);
    }

    @Test
    public void testNegShiftLeftLogicInt() {
        test("negShiftLeftLogicInt", 123);
        checkLIR("negShiftLeftLogicInt", predicate, 1);
    }

    public int negShiftRightLogicInt(int input) {
        return -(input >>> 6);
    }

    @Test
    public void testNegShiftRightLogicInt() {
        test("negShiftRightLogicInt", 123);
        checkLIR("negShiftRightLogicInt", predicate, 1);
    }

    public int negShiftRightArithInt(int input) {
        return -(input >> 7);
    }

    @Test
    public void testNegShiftRightArithInt() {
        test("negShiftRightArithInt", 123);
        checkLIR("negShiftRightArithInt", predicate, 1);
    }

    /**
     * negateShift match rule tests for shift operations with long type.
     */
    public long negShiftLeftLogicLong(long input) {
        return -(input << 8);
    }

    @Test
    public void testNegShiftLeftLogicLong() {
        test("negShiftLeftLogicLong", 123L);
        checkLIR("negShiftLeftLogicLong", predicate, 1);
    }

    public long negShiftRightLogicLong(long input) {
        return -(input >>> 9);
    }

    @Test
    public void testNegShiftRightLogicLong() {
        test("negShiftRightLogicLong", 123L);
        checkLIR("negShiftRightLogicLong", predicate, 1);
    }

    public long negShiftRightArithLong(long input) {
        return -(input >> 10);
    }

    @Test
    public void testNegShiftRightArithLong() {
        test("negShiftRightArithLong", 123L);
        checkLIR("negShiftRightArithLong", predicate, 1);
    }
}
