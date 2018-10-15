/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Arm Limited and affiliates. All rights reserved.
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

import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp;
import org.junit.Assert;
import org.junit.Test;

public class AArch64AddSubShiftTest extends AArch64MatchRuleTest {
    /**
     * addSubShift match rule test for add operation with int type.
     */
    private static int addLeftShiftInt(int input) {
        int output = (input << 5) + input;
        output += output << -5;
        output += output << 32;
        return output;
    }

    private static int addRightShiftInt(int input) {
        int output = (input >> 5) + input;
        output += output >> -5;
        output += output >> 32;
        return output;
    }

    private static int addUnsignedRightShiftInt(int input) {
        int output = (input >>> 5) + input;
        output += output >>> -5;
        output += output >>> 32;
        return output;
    }

    private static int addShiftInt(int input) {
        return addLeftShiftInt(input) + addRightShiftInt(input) + addUnsignedRightShiftInt(input);
    }

    /**
     * Check whether the addSubShift match rule in AArch64NodeMatchRules does work for add operation
     * with int type and check if the expected LIR instructions show up.
     */
    @Test
    public void testAddShiftInt() {
        compile(getResolvedJavaMethod("addShiftInt"), null);
        LIR lir = getLIR();
        int addSubShiftOpNum = 0;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof AArch64ArithmeticOp.AddSubShiftOp) {
                addSubShiftOpNum++;
            }
        }
        Assert.assertEquals(addSubShiftOpNum, 6);

        test("addLeftShiftInt", 123);
        test("addRightShiftInt", 123);
        test("addUnsignedRightShiftInt", 123);
    }

    /**
     * addSubShift match rule test for add operation with long type.
     */
    private static long addLeftShiftLong(long input) {
        long output = (input << 5) + input;
        output += output << -5;
        output += output << 64;
        return output;
    }

    private static long addRightShiftLong(long input) {
        long output = (input >> 5) + input;
        output += output >> -5;
        output += output >> 64;
        return output;
    }

    private static long addUnsignedRightShiftLong(long input) {
        long output = (input >>> 5) + input;
        output += output >>> -5;
        output += output >>> 64;
        return output;
    }

    private static long addShiftLong(long input) {
        return addLeftShiftLong(input) + addRightShiftLong(input) + addUnsignedRightShiftLong(input);
    }

    /**
     * Check whether the addSubShift match rule in AArch64NodeMatchRules does work for add operation
     * with long type and check if the expected LIR instructions show up.
     */
    @Test
    public void testAddShiftLong() {
        compile(getResolvedJavaMethod("addShiftLong"), null);
        LIR lir = getLIR();
        int addSubShiftOpNum = 0;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof AArch64ArithmeticOp.AddSubShiftOp) {
                addSubShiftOpNum++;
            }
        }
        Assert.assertEquals(addSubShiftOpNum, 6);

        test("addLeftShiftLong", (long) 1234567);
        test("addRightShiftLong", (long) 1234567);
        test("addUnsignedRightShiftLong", (long) 1234567);
    }

    /**
     * addSubShift match rule test for sub operation with int type.
     */
    private static int subLeftShiftInt(int input0, int input1) {
        return input0 - (input1 << 5);
    }

    private static int subRightShiftInt(int input0, int input1) {
        return input0 - (input1 >> 5);
    }

    private static int subUnsignedRightShiftInt(int input0, int input1) {
        return input0 - (input1 >>> 5);
    }

    private static int subShiftInt(int input0, int input1) {
        return subLeftShiftInt(input0, input1) + subRightShiftInt(input0, input1) + subUnsignedRightShiftInt(input0, input1);
    }

    /**
     * Check whether the addSubShift match rule in AArch64NodeMatchRules does work for sub operation
     * with int type and check if the expected LIR instructions show up.
     */
    @Test
    public void testSubShiftInt() {
        compile(getResolvedJavaMethod("subShiftInt"), null);
        LIR lir = getLIR();
        int addSubShiftOpNum = 0;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof AArch64ArithmeticOp.AddSubShiftOp) {
                addSubShiftOpNum++;
            }
        }
        Assert.assertEquals(addSubShiftOpNum, 3);

        test("subLeftShiftInt", 4567, 123);
        test("subRightShiftInt", 4567, 123);
        test("subUnsignedRightShiftInt", 4567, 123);
    }

    /**
     * addSubShift match rule test for sub operation with long type.
     */
    private static long subLeftShiftLong(long input0, long input1) {
        return input0 - (input1 << 5);
    }

    private static long subRightShiftLong(long input0, long input1) {
        return input0 - (input1 >> 5);
    }

    private static long subUnsignedRightShiftLong(long input0, long input1) {
        return input0 - (input1 >>> 5);
    }

    private static long subShiftLong(long input0, long input1) {
        return subLeftShiftLong(input0, input1) + subRightShiftLong(input0, input1) + subUnsignedRightShiftLong(input0, input1);
    }

    /**
     * Check whether the addSubShift match rule in AArch64NodeMatchRules does work for sub operation
     * with long type and check if the expected LIR instructions show up.
     */
    @Test
    public void testSubShiftLong() {
        compile(getResolvedJavaMethod("subShiftLong"), null);
        LIR lir = getLIR();
        int addSubShiftOpNum = 0;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (ins instanceof AArch64ArithmeticOp.AddSubShiftOp) {
                addSubShiftOpNum++;
            }
        }
        Assert.assertEquals(addSubShiftOpNum, 3);

        test("subLeftShiftLong", (long) 1234567, (long) 123);
        test("subRightShiftLong", (long) 1234567, (long) 123);
        test("subUnsignedRightShiftLong", (long) 1234567, (long) 123);
    }
}
