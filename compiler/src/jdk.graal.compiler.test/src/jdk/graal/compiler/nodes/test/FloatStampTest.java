/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.test.GraphTest;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;

/**
 * This class tests that float stamps are created correctly for constants.
 */
public class FloatStampTest extends GraphTest {

    public static final float[] floatNonNaNs = new float[]{0.0f, -0.0f, 0.1f, -0.1f, Float.MIN_VALUE, Float.MIN_NORMAL, Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
    public static final float[] floatNaNs = new float[]{Float.NaN, Float.intBitsToFloat(0x7fffffff), Float.intBitsToFloat(0xffffffff)};

    public static final double[] doubleNonNaNs = new double[]{0.0d, -0.0d, 0.1d, -0.1d, Double.MIN_VALUE, Double.MIN_NORMAL, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
    public static final double[] doubleNaNs = new double[]{Double.NaN, Double.longBitsToDouble(0x7fffffffffffffffL), Double.longBitsToDouble(0xffffffffffffffffL)};

    private static FloatStamp createFloatStamp(int bits, double value) {
        if (Double.isNaN(value)) {
            return new FloatStamp(bits, Double.NaN, Double.NaN, false);
        }
        return new FloatStamp(bits, value, value, true);
    }

    @Test
    public void testFloatConstant() {
        for (float f : floatNonNaNs) {
            assertEquals(createFloatStamp(32, f), ConstantNode.forFloat(f).stamp(NodeView.DEFAULT));
        }

        for (float f : floatNaNs) {
            assertEquals(createFloatStamp(32, f), ConstantNode.forFloat(f).stamp(NodeView.DEFAULT));
        }
    }

    @Test
    public void testDoubleConstant() {
        for (double d : doubleNonNaNs) {
            assertEquals(createFloatStamp(64, d), ConstantNode.forDouble(d).stamp(NodeView.DEFAULT));
        }

        for (double d : doubleNaNs) {
            assertEquals(createFloatStamp(64, d), ConstantNode.forDouble(d).stamp(NodeView.DEFAULT));
        }
    }

    @Test
    public void testXor() {
        assertEquals(createFloatStamp(32, Float.intBitsToFloat(0x64707411)),
                        FloatStamp.OPS.getXor().foldStamp(createFloatStamp(32, Float.intBitsToFloat(0xBADDCAFE)), createFloatStamp(32, Float.intBitsToFloat(0xDEADBEEF))));
    }

    @Test
    public void testAnd() {
        assertEquals(createFloatStamp(32, Float.intBitsToFloat(0x9A8D8AEE)),
                        FloatStamp.OPS.getAnd().foldStamp(createFloatStamp(32, Float.intBitsToFloat(0xBADDCAFE)), createFloatStamp(32, Float.intBitsToFloat(0xDEADBEEF))));
    }

    @Test
    public void testNot() {
        assertEquals(createFloatStamp(32, Float.intBitsToFloat(0x45223501)),
                        FloatStamp.OPS.getNot().foldStamp(createFloatStamp(32, Float.intBitsToFloat(0xBADDCAFE))));
    }

    @Test
    public void testAdd() {
        assertEquals(createFloatStamp(32, 3.0f),
                        FloatStamp.OPS.getAdd().foldStamp(createFloatStamp(32, 1.0f), createFloatStamp(32, 2.0f)));
        assertEquals(createFloatStamp(32, Float.intBitsToFloat(0xDEADBEEF)),
                        FloatStamp.OPS.getAdd().foldStamp(createFloatStamp(32, Float.intBitsToFloat(0xBADDCAFE)), createFloatStamp(32, Float.intBitsToFloat(0xDEADBEEF))));
        assertEquals(createFloatStamp(32, Float.POSITIVE_INFINITY),
                        FloatStamp.OPS.getAdd().foldStamp(createFloatStamp(32, Float.MAX_VALUE), createFloatStamp(32, Float.intBitsToFloat(0x7F000001))));
    }

    @Test
    public void testMul() {
        assertEquals(createFloatStamp(32, 2.0f),
                        FloatStamp.OPS.getMul().foldStamp(createFloatStamp(32, 1.0f), createFloatStamp(32, 2.0f)));
        assertEquals(createFloatStamp(32, Float.intBitsToFloat(0x5A168799)),
                        FloatStamp.OPS.getMul().foldStamp(createFloatStamp(32, Float.intBitsToFloat(0xBADDCAFE)), createFloatStamp(32, Float.intBitsToFloat(0xDEADBEEF))));
        assertEquals(createFloatStamp(32, Float.POSITIVE_INFINITY),
                        FloatStamp.OPS.getMul().foldStamp(createFloatStamp(32, Float.MAX_VALUE), createFloatStamp(32, 2.0f)));
    }

    @Test
    public void testDiv() {
        assertEquals(createFloatStamp(32, 0.5f),
                        FloatStamp.OPS.getDiv().foldStamp(createFloatStamp(32, 1.0f), createFloatStamp(32, 2.0f)));
        assertEquals(createFloatStamp(32, Float.intBitsToFloat(0x1BA3658E)),
                        FloatStamp.OPS.getDiv().foldStamp(createFloatStamp(32, Float.intBitsToFloat(0xBADDCAFE)), createFloatStamp(32, Float.intBitsToFloat(0xDEADBEEF))));
        assertEquals(createFloatStamp(32, Float.POSITIVE_INFINITY),
                        FloatStamp.OPS.getDiv().foldStamp(createFloatStamp(32, Float.MAX_VALUE), createFloatStamp(32, 0.5f)));
    }

    @Test
    public void testMeetJoin() {
        for (float f1Value : floatNonNaNs) {
            FloatStamp f1 = createFloatStamp(32, f1Value);
            for (float f2Value : floatNonNaNs) {
                FloatStamp f2 = createFloatStamp(32, f2Value);
                FloatStamp f1Meetf2 = (FloatStamp) f1.meet(f2);
                assertTrue(f1Meetf2.contains(f1Value));
                assertTrue(f1Meetf2.contains(f2Value));

                FloatStamp f1Meetf2Joinf1 = (FloatStamp) f1Meetf2.join(f1);
                assertTrue(f1Meetf2Joinf1.contains(f1Value));
                assertTrue(f1Value == f2Value || !f1Meetf2Joinf1.contains(f2Value));
            }
        }

        FloatStamp f1 = createFloatStamp(32, 0);
        FloatStamp f2 = createFloatStamp(32, Float.NaN);
        assertTrue(((FloatStamp) f1.meet(f2)).canBeNaN());
        assertFalse(((FloatStamp) f1.join(f2)).canBeNaN());
    }

    @Test
    public void testIllegalJoin() {
        assertFalse(new FloatStamp(32, 0, Float.POSITIVE_INFINITY, true).join(new FloatStamp(32, Float.NEGATIVE_INFINITY, -Float.MIN_VALUE, true)).hasValues());
        assertFalse(new FloatStamp(32, Float.NaN, Float.NaN, false).join(new FloatStamp(32, 0, 0, true)).hasValues());
    }

    @Test
    public void testEmpty() {
        Stamp floatStamp = StampFactory.forKind(JavaKind.Float);
        Stamp floatEmpty = StampFactory.empty(JavaKind.Float);

        assertEquals(floatStamp.join(floatEmpty), floatEmpty);
        assertEquals(floatStamp.meet(floatEmpty), floatStamp);

        Stamp doubleStamp = StampFactory.forKind(JavaKind.Double);
        Stamp doubleEmpty = StampFactory.empty(JavaKind.Double);

        assertEquals(doubleStamp.join(doubleEmpty), doubleEmpty);
        assertEquals(doubleStamp.meet(doubleEmpty), doubleStamp);
    }

    @Test
    public void testUnrestricted() {
        Stamp floatStamp = createFloatStamp(32, 0.0f);
        Stamp floatUnrestricted = floatStamp.unrestricted();

        assertEquals(floatStamp.join(floatUnrestricted), floatStamp);
        assertEquals(floatStamp.meet(floatUnrestricted), floatUnrestricted);

        Stamp doubleStamp = createFloatStamp(64, 0.0d);
        Stamp doubleUnrestricted = doubleStamp.unrestricted();

        assertEquals(doubleStamp.join(doubleUnrestricted), doubleStamp);
        assertEquals(doubleStamp.meet(doubleUnrestricted), doubleUnrestricted);
    }

    @Test
    public void testUnaryOpFoldEmpty() {
        for (ArithmeticOpTable.UnaryOp<?> op : FloatStamp.OPS.getUnaryOps()) {
            if (op != null) {
                Assert.assertTrue(op.foldStamp(StampFactory.empty(JavaKind.Float)).isEmpty());
                Assert.assertTrue(op.foldStamp(StampFactory.empty(JavaKind.Double)).isEmpty());
            }
        }
    }
}
