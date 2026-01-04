/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

import jdk.graal.compiler.util.EconomicHashSet;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.test.GraphTest;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.calc.CopySignNode;
import jdk.graal.compiler.nodes.calc.RoundNode;
import jdk.graal.compiler.nodes.calc.SignumNode;
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
            return FloatStamp.createNaN(bits);
        }
        return FloatStamp.create(bits, value, value, true);
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
        assertFalse(FloatStamp.create(32, 0, Float.POSITIVE_INFINITY, true).join(FloatStamp.create(32, Float.NEGATIVE_INFINITY, -Float.MIN_VALUE, true)).hasValues());
        assertFalse(FloatStamp.create(32, Float.NaN, Float.NaN, false).join(FloatStamp.create(32, 0, 0, true)).hasValues());
        assertTrue(((FloatStamp) FloatStamp.create(32, 0, Float.POSITIVE_INFINITY, false).join(FloatStamp.create(32, Float.NEGATIVE_INFINITY, -Float.MIN_VALUE, false))).isNaN());
        assertTrue(((FloatStamp) FloatStamp.create(32, Float.NaN, Float.NaN, false).join(FloatStamp.create(32, 0, 0, false))).isNaN());
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

    @Test
    public void testFoldStamp() {
        runFoldStamp(32);
        runFoldStamp(64);
    }

    static void runFoldStamp(int bits) {
        Random random = GraalCompilerTest.getRandomInstance();
        ArrayList<FloatStamp> stamps = generateStamps(bits, random);
        verify(bits, random, stamps);
    }

    private static ArrayList<FloatStamp> generateStamps(int bits, Random random) {
        double[] specialValues;
        if (bits == Float.SIZE) {
            specialValues = new double[floatNonNaNs.length];
            for (int i = 0; i < floatNonNaNs.length; i++) {
                specialValues[i] = floatNonNaNs[i];
            }
        } else {
            specialValues = doubleNonNaNs;
        }
        ArrayList<FloatStamp> stamps = new ArrayList<>();
        FloatStamp nan = FloatStamp.createNaN(bits);
        stamps.add(nan);
        stamps.add(nan.empty());
        for (int i = 0; i < specialValues.length; i++) {
            double currentValue = specialValues[i];
            for (int j = i; j < specialValues.length; j++) {
                double otherValue = specialValues[i];
                if (Double.compare(currentValue, otherValue) > 0) {
                    stamps.add(FloatStamp.create(bits, otherValue, currentValue, true));
                    stamps.add(FloatStamp.create(bits, otherValue, currentValue, false));
                } else {
                    stamps.add(FloatStamp.create(bits, currentValue, otherValue, true));
                    stamps.add(FloatStamp.create(bits, currentValue, otherValue, false));
                }
            }

            for (int j = 0; j < 10; j++) {
                double otherBound;
                if (bits == Float.SIZE) {
                    otherBound = Float.intBitsToFloat(random.nextInt());
                } else {
                    otherBound = Double.longBitsToDouble(random.nextLong());
                }
                if (Double.isNaN(otherBound)) {
                    continue;
                }

                if (Double.compare(currentValue, otherBound) < 0) {
                    stamps.add(FloatStamp.create(bits, currentValue, otherBound, true));
                    stamps.add(FloatStamp.create(bits, currentValue, otherBound, false));
                } else {
                    stamps.add(FloatStamp.create(bits, otherBound, currentValue, true));
                    stamps.add(FloatStamp.create(bits, otherBound, currentValue, false));
                }
            }
        }

        for (int i = 0; i < 10; i++) {
            double first;
            double second;
            if (bits == Float.SIZE) {
                first = Float.intBitsToFloat(random.nextInt());
                second = Float.intBitsToFloat(random.nextInt());
            } else {
                first = Double.longBitsToDouble(random.nextLong());
                second = Double.longBitsToDouble(random.nextLong());
            }
            if (Double.isNaN(first) || Double.isNaN(second)) {
                continue;
            }

            if (Double.compare(first, second) > 0) {
                double temp = first;
                first = second;
                second = temp;
            }

            stamps.add(FloatStamp.create(bits, first, first, true));
            stamps.add(FloatStamp.create(bits, first, first, false));
            stamps.add(FloatStamp.create(bits, first, second, true));
            stamps.add(FloatStamp.create(bits, first, second, false));
        }

        return stamps;
    }

    private static Set<Double> sample(Random random, FloatStamp stamp) {
        Set<Double> samples = new EconomicHashSet<>(20);
        if (stamp.isEmpty()) {
            return samples;
        }

        if (!stamp.isNonNaN()) {
            samples.add(Double.NaN);
            if (stamp.isNaN()) {
                return samples;
            }
        }

        samples.add(stamp.lowerBound());
        samples.add(stamp.upperBound());
        if (stamp.lowerBound() == stamp.upperBound()) {
            return samples;
        }

        double neighbor = stamp.getBits() == Float.SIZE ? Math.nextUp((float) stamp.lowerBound()) : Math.nextUp(stamp.lowerBound());
        samples.add(neighbor);
        neighbor = stamp.getBits() == Float.SIZE ? Math.nextDown((float) stamp.upperBound()) : Math.nextDown(stamp.upperBound());
        samples.add(neighbor);

        if (stamp.getBits() == Float.SIZE) {
            for (double d : floatNonNaNs) {
                if (stamp.contains(d)) {
                    samples.add(d);
                }
            }
        } else {
            for (double d : doubleNonNaNs) {
                if (stamp.contains(d)) {
                    samples.add(d);
                }
            }
        }

        double lowerBound = stamp.lowerBound();
        double upperBound = stamp.upperBound();
        if (lowerBound == Double.NEGATIVE_INFINITY) {
            lowerBound = stamp.getBits() == Float.SIZE ? -Float.MAX_VALUE : -Double.MAX_VALUE;
        }
        if (upperBound == Double.POSITIVE_INFINITY) {
            upperBound = stamp.getBits() == Float.SIZE ? Float.MAX_VALUE : Double.MAX_VALUE;
        }
        for (int i = 0; i < 10; i++) {
            double current;
            if (stamp.getBits() == Float.SIZE) {
                current = random.nextFloat((float) lowerBound, (float) upperBound);
            } else {
                current = random.nextDouble(lowerBound, upperBound);
            }
            samples.add(current);
        }
        return samples;
    }

    private static void verify(int bits, Random random, ArrayList<FloatStamp> stamps) {
        ArrayList<double[]> samples = new ArrayList<>(stamps.size());
        for (FloatStamp stamp : stamps) {
            Set<Double> sampleSet = sample(random, stamp);
            double[] sampleArray = new double[sampleSet.size()];
            int i = 0;
            for (double d : sampleSet) {
                sampleArray[i] = d;
                i++;
            }
            samples.add(sampleArray);
        }

        ParameterNode param = new ParameterNode(0, StampPair.createSingle(StampFactory.forKind(bits == Float.SIZE ? JavaKind.Float : JavaKind.Double)));
        RoundNode rint = new RoundNode(param, ArithmeticLIRGeneratorTool.RoundingMode.NEAREST);
        RoundNode ceil = new RoundNode(param, ArithmeticLIRGeneratorTool.RoundingMode.UP);
        RoundNode floor = new RoundNode(param, ArithmeticLIRGeneratorTool.RoundingMode.DOWN);
        SignumNode signum = new SignumNode(param);

        for (int i = 0; i < stamps.size(); i++) {
            FloatStamp stamp = stamps.get(i);
            double[] sample = samples.get(i);
            verifyUnary(stamp, sample, FloatStamp.OPS.getAbs()::foldStamp, Math::abs);
            verifyUnary(stamp, sample, FloatStamp.OPS.getNeg()::foldStamp, x -> -x);
            verifyUnary(stamp, sample, FloatStamp.OPS.getSqrt()::foldStamp, bits == Float.SIZE ? x -> (float) Math.sqrt(x) : Math::sqrt);
            verifyUnary(stamp, sample, signum::foldStamp, Math::signum);
            if (bits == Double.SIZE) {
                verifyUnary(stamp, sample, rint::foldStamp, Math::rint);
                verifyUnary(stamp, sample, ceil::foldStamp, Math::ceil);
                verifyUnary(stamp, sample, floor::foldStamp, Math::floor);
            }
        }

        for (int i = 0; i < stamps.size(); i++) {
            FloatStamp stamp1 = stamps.get(i);
            double[] sample1 = samples.get(i);
            for (int j = i; j < stamps.size(); j++) {
                FloatStamp stamp2 = stamps.get(j);
                double[] sample2 = samples.get(j);
                verifyBinary(stamp1, stamp2, sample1, sample2, FloatStamp.OPS.getAdd()::foldStamp, bits == Float.SIZE ? (x, y) -> (float) x + (float) y : Double::sum);
                verifyBinary(stamp1, stamp2, sample1, sample2, FloatStamp.OPS.getDiv()::foldStamp, bits == Float.SIZE ? (x, y) -> (float) x / (float) y : (x, y) -> x / y);
                verifyBinary(stamp1, stamp2, sample1, sample2, FloatStamp.OPS.getMax()::foldStamp, Math::max);
                verifyBinary(stamp1, stamp2, sample1, sample2, FloatStamp.OPS.getMin()::foldStamp, Math::min);
                verifyBinary(stamp1, stamp2, sample1, sample2, FloatStamp.OPS.getMul()::foldStamp, bits == Float.SIZE ? (x, y) -> (float) x * (float) y : (x, y) -> x * y);
                verifyBinary(stamp1, stamp2, sample1, sample2, FloatStamp.OPS.getSub()::foldStamp, bits == Float.SIZE ? (x, y) -> (float) x - (float) y : (x, y) -> x - y);
                verifyBinary(stamp1, stamp2, sample1, sample2, CopySignNode::computeStamp, Math::copySign);
            }
        }
    }

    private static void verifyUnary(FloatStamp stamp, double[] samples, Function<FloatStamp, Stamp> compute, DoubleUnaryOperator op) {
        FloatStamp res = (FloatStamp) compute.apply(stamp);
        if (stamp.isEmpty()) {
            assertTrue(res.isEmpty());
            return;
        }

        for (double x : samples) {
            double y = op.applyAsDouble(x);
            assertTrue(stamp.getBits() == Double.SIZE || (Double.compare((float) x, x) == 0 && Double.compare((float) y, y) == 0));
            assertTrue(res.contains(y));
        }
    }

    private static void verifyBinary(FloatStamp stamp1, FloatStamp stamp2, double[] sample1, double[] sample2, BiFunction<FloatStamp, FloatStamp, Stamp> compute, DoubleBinaryOperator op) {
        FloatStamp res = (FloatStamp) compute.apply(stamp1, stamp2);
        if (stamp1.isEmpty() || stamp2.isEmpty()) {
            assertTrue(res.isEmpty());
            return;
        }

        for (double x1 : sample1) {
            for (double x2 : sample2) {
                double y = op.applyAsDouble(x1, x2);
                assertTrue(stamp1.getBits() == Double.SIZE || (Double.compare((float) x1, x1) == 0 && Double.compare((float) x2, x2) == 0 && Double.compare((float) y, y) == 0));
                assertTrue(res.contains(y));
            }
        }
    }
}
