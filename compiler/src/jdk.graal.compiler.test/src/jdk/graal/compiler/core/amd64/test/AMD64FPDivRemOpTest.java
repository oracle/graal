/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.amd64.test;

import static jdk.graal.compiler.lir.LIRValueUtil.isStackSlotValue;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.amd64.AMD64Arithmetic.FPDivRemOp;
import jdk.graal.compiler.replacements.nodes.DoubleModStubNode;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.Value;
import org.junit.Assert;
import org.junit.Test;

public class AMD64FPDivRemOpTest extends AMD64MatchRuleTest {

    public static float floatRemSnippet(float a, float b) {
        return a % b;
    }

    public static double doubleRemSnippet(double a, double b) {
        return a % b;
    }

    public static long doubleRemRawBitsSnippet(double a, double b) {
        return Double.doubleToRawLongBits(a % b);
    }

    @Test
    public void testFloatRemUsesStackTemp() {
        checkFPDivRemOp("floatRemSnippet", AMD64Kind.SINGLE);
    }

    @Test
    public void testDoubleRemUsesStackTemp() {
        if (DoubleModStubNode.isSupported(getArchitecture())) {
            checkFPDivRemOpCount("doubleRemSnippet", 0);
        } else {
            checkFPDivRemOp("doubleRemSnippet", AMD64Kind.DOUBLE);
        }
    }

    @Test
    public void testDoubleRemResult() {
        test("doubleRemSnippet", 1.0d, 0.1d);
        test("doubleRemSnippet", 5.3d, 2.0d);
        test("doubleRemSnippet", 0.25d, 1.0d);
        test("doubleRemSnippet", -0.25d, 1.0d);
        test("doubleRemSnippet", 0x1.0p200d, 3.0d);
        test("doubleRemSnippet", 0x1.0p300d, 0.25d);
        test("doubleRemSnippet", 0x1.0p900d, 0x1.0p-500d);
        test("doubleRemSnippet", -0x1.23456789abcdep900d, 0x1.0p-300d);
        test("doubleRemSnippet", 0x1.fffffffffffffp1023d, 0x1.0p-1020d);
        test("doubleRemSnippet", 0x1.0p1023d, 0x1.0p-1022d);
        test("doubleRemSnippet", Double.POSITIVE_INFINITY, 3.0d);
        test("doubleRemSnippet", 1.0d, 0.0d);
        test("doubleRemSnippet", Double.NaN, 1.0d);
        test("doubleRemSnippet", 1.0d, Double.NaN);
        test("doubleRemSnippet", 1.0d, Double.POSITIVE_INFINITY);
    }

    @Test
    public void testDoubleRemRawBitsResult() {
        test("doubleRemRawBitsSnippet", 0.0d, 1.0d);
        test("doubleRemRawBitsSnippet", -0.0d, 1.0d);
        test("doubleRemRawBitsSnippet", -4.0d, 2.0d);
        test("doubleRemRawBitsSnippet", -0.25d, 1.0d);
    }

    private void checkFPDivRemOp(String methodName, AMD64Kind expectedStackTempKind) {
        Assert.assertEquals(1, checkFPDivRemOps(methodName, expectedStackTempKind));
    }

    private void checkFPDivRemOpCount(String methodName, int expectedCount) {
        Assert.assertEquals(expectedCount, checkFPDivRemOps(methodName, null));
    }

    private int checkFPDivRemOps(String methodName, AMD64Kind expectedStackTempKind) {
        compile(getResolvedJavaMethod(methodName), null);
        LIR lir = getLIR();
        int matches = 0;
        for (int blockId : lir.codeEmittingOrder()) {
            if (LIR.isBlockDeleted(blockId)) {
                continue;
            }
            BasicBlock<?> block = lir.getBlockById(blockId);
            for (var op : lir.getLIRforBlock(block)) {
                if (op instanceof FPDivRemOp fpDivRemOp) {
                    matches++;
                    assertFalse(fpDivRemOp.modifiesStackPointer());

                    Value[] stackTemp = {null};
                    int[] registerTempCount = {0};
                    int[] stackTempCount = {0};
                    fpDivRemOp.visitEachTemp((value, mode, flags) -> {
                        if (isRegister(value)) {
                            registerTempCount[0]++;
                        }
                        if (isStackSlotValue(value)) {
                            stackTempCount[0]++;
                            stackTemp[0] = value;
                        }
                    });

                    Assert.assertEquals(1, registerTempCount[0]);
                    Assert.assertEquals(1, stackTempCount[0]);
                    assertNotNull(stackTemp[0]);
                    Assert.assertEquals(expectedStackTempKind, stackTemp[0].getPlatformKind());
                }
            }
        }
        return matches;
    }
}
