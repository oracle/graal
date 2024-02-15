/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.test;

import static jdk.graal.compiler.core.common.calc.CanonicalCondition.BT;
import static jdk.graal.compiler.core.common.calc.CanonicalCondition.LT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.test.GraphTest;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;

/**
 * This class tests that {@link NarrowNode#preservesOrder(CanonicalCondition)} returns correct
 * value.
 */
public class NarrowPreservesOrderTest extends GraphTest {

    private static IntegerStamp signExtend(Stamp stamp, int bits) {
        assertTrue(stamp instanceof IntegerStamp);
        IntegerStamp integerStamp = (IntegerStamp) stamp;
        return IntegerStamp.create(bits, integerStamp.lowerBound(), integerStamp.upperBound());
    }

    private static IntegerStamp zeroExtend(Stamp stamp, int bits) {
        assertTrue(stamp instanceof IntegerStamp);
        IntegerStamp integerStamp = (IntegerStamp) stamp;
        return IntegerStamp.create(bits, integerStamp.unsignedLowerBound(), integerStamp.unsignedUpperBound());
    }

    private static IntegerStamp forConstantInt(long cst) {
        return IntegerStamp.create(32, cst, cst);
    }

    private static IntegerStamp forConstantLong(long cst) {
        return IntegerStamp.create(64, cst, cst);
    }

    private static void testPreserveOrder(Stamp inputStamp, int resultBits, CanonicalCondition cond, boolean expected) {
        ParameterNode input = new ParameterNode(0, StampPair.createSingle(inputStamp));
        NarrowNode narrow = new NarrowNode(input, resultBits);
        assertEquals(expected, narrow.preservesOrder(cond));
    }

    @Test
    public void testBoolean() {
        testPreserveOrder(forConstantInt(0), 1, LT, true);
        testPreserveOrder(forConstantInt(0), 1, BT, true);
        testPreserveOrder(forConstantInt(1), 1, LT, false);
        testPreserveOrder(forConstantInt(1), 1, BT, true);
        testPreserveOrder(signExtend(StampFactory.forKind(JavaKind.Boolean), 32), 1, LT, false);
        testPreserveOrder(signExtend(StampFactory.forKind(JavaKind.Boolean), 32), 1, BT, true);

        testPreserveOrder(StampFactory.forKind(JavaKind.Byte), 1, LT, false);
        testPreserveOrder(StampFactory.forKind(JavaKind.Byte), 1, BT, false);
    }

    @Test
    public void testByte() {
        testPreserveOrder(forConstantInt(0), 8, LT, true);
        testPreserveOrder(forConstantInt(0), 8, BT, true);
        testPreserveOrder(forConstantInt(CodeUtil.maxValue(8)), 8, LT, true);
        testPreserveOrder(forConstantInt(CodeUtil.maxValue(8)), 8, BT, true);
        testPreserveOrder(forConstantInt(NumUtil.maxValueUnsigned(8)), 8, LT, false);
        testPreserveOrder(forConstantInt(NumUtil.maxValueUnsigned(8)), 8, BT, true);
        testPreserveOrder(signExtend(StampFactory.forKind(JavaKind.Byte), 32), 8, LT, true);
        testPreserveOrder(signExtend(StampFactory.forKind(JavaKind.Byte), 32), 8, BT, false);
        testPreserveOrder(zeroExtend(StampFactory.forUnsignedInteger(8), 32), 8, LT, false);
        testPreserveOrder(zeroExtend(StampFactory.forUnsignedInteger(8), 32), 8, BT, true);

        testPreserveOrder(StampFactory.forKind(JavaKind.Short), 8, LT, false);
        testPreserveOrder(StampFactory.forKind(JavaKind.Short), 8, BT, false);
    }

    @Test
    public void testShort() {
        testPreserveOrder(forConstantInt(0), 16, LT, true);
        testPreserveOrder(forConstantInt(0), 16, BT, true);
        testPreserveOrder(forConstantInt(CodeUtil.maxValue(16)), 16, LT, true);
        testPreserveOrder(forConstantInt(CodeUtil.maxValue(16)), 16, BT, true);
        testPreserveOrder(forConstantInt(NumUtil.maxValueUnsigned(16)), 16, LT, false);
        testPreserveOrder(forConstantInt(NumUtil.maxValueUnsigned(16)), 16, BT, true);
        testPreserveOrder(signExtend(StampFactory.forKind(JavaKind.Short), 32), 16, LT, true);
        testPreserveOrder(signExtend(StampFactory.forKind(JavaKind.Short), 32), 16, BT, false);
        testPreserveOrder(zeroExtend(StampFactory.forUnsignedInteger(16), 32), 16, LT, false);
        testPreserveOrder(zeroExtend(StampFactory.forUnsignedInteger(16), 32), 16, BT, true);

        testPreserveOrder(StampFactory.forKind(JavaKind.Int), 16, LT, false);
        testPreserveOrder(StampFactory.forKind(JavaKind.Int), 16, BT, false);
    }

    @Test
    public void testInt() {
        testPreserveOrder(forConstantLong(0), 32, LT, true);
        testPreserveOrder(forConstantLong(0), 32, BT, true);
        testPreserveOrder(forConstantLong(CodeUtil.maxValue(32)), 32, LT, true);
        testPreserveOrder(forConstantLong(CodeUtil.maxValue(32)), 32, BT, true);
        testPreserveOrder(forConstantLong(NumUtil.maxValueUnsigned(32)), 32, LT, false);
        testPreserveOrder(forConstantLong(NumUtil.maxValueUnsigned(32)), 32, BT, true);
        testPreserveOrder(signExtend(StampFactory.forKind(JavaKind.Int), 64), 32, LT, true);
        testPreserveOrder(signExtend(StampFactory.forKind(JavaKind.Int), 64), 32, BT, false);
        testPreserveOrder(zeroExtend(StampFactory.forUnsignedInteger(32), 64), 32, LT, false);
        testPreserveOrder(zeroExtend(StampFactory.forUnsignedInteger(32), 64), 32, BT, true);

        testPreserveOrder(StampFactory.forKind(JavaKind.Long), 32, LT, false);
        testPreserveOrder(StampFactory.forKind(JavaKind.Long), 32, BT, false);
    }
}
