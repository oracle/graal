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
package jdk.graal.compiler.loop.test;

import java.util.Arrays;
import java.util.Random;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.loop.phases.LoopPeelingPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Test;

import jdk.graal.compiler.loop.phases.SimulationBasedLoopPeeling;
import jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase;

@SuppressWarnings("cast")
public class SimulationBasedLoopPeelingRegressionTest extends GraalCompilerTest {
    // Checkstyle: stop
    static class Class0 {
        public static double Class0_sfield0 = -968.3461869275629;
        public static boolean[] Class0_sfield1 = new boolean[847];
    }

    static class Class1 extends Class0 {

    }

    static class Class2 {
        public static volatile boolean Class2_sfield0 = true;

        public synchronized boolean Class2_method0() {
            boolean var0, var5, var6, var9;
            double[] var10;
            boolean[] var1;
            float var8;
            float var2 = -2.94185651E9f;
            boolean var3 = true;
            boolean var4 = true;
            boolean var7 = false;
            synchronized (this) {
                Class2.Class2_sfield0 = var4 != true;
            }
            var9 = Class2.Class2_sfield0;
            synchronized (new Class0()) {
                synchronized (new Class1()) {
                    var10 = new double[970];
                    for (double i0 : var10) {
                        var8 = (float) i0;
                        var3 = !var9;
                        var2 = var2 - var8;
                    }
                    synchronized (this) {
                        var6 = !var4;
                        synchronized (new Class0()) {
                            Class1.Class0_sfield0 = -((double) var2);
                        }
                        var0 = var7;
                        Class1.Class0_sfield1 = new boolean[]{var0, var4, var4, var3};
                    }
                }
            }
            if (var0) {
                var1 = Class0.Class0_sfield1;
                var0 = Arrays.equals(var1, var1);
            } else if (Float.isInfinite(var2)) {
                var0 = Boolean.logicalXor(var3, var4);
            } else if (Double.isInfinite(((double) var2))) {
                var5 = !var6;
                synchronized (this) {
                    var0 = var5 || true;
                }
            }
            return var0;
        }
    }

    static class Class3 extends Class2 {
        public final Class1 Class3_field0;

        public Class3() {
            this.Class3_field0 = new Class1();
        }
    }

    static class TestClass {

        public static final Class3 Test_sfield0 = new Class3();

        public static final float[] Test_sfield1 = new float[]{(-2.9807358E7f), 0.0f, 2.3278454E38f, (-1.0f)};

        public static String Test_sfield2 = "�s��LX�Q\u0012\bؐ";

        public static boolean[] Test_sfield3 = new boolean[]{false, true, true};

        public static final Class1 Test_sfield4 = new Class1();
    }

    public static double method2(double param0, int param1, boolean param2, char param3,
                    char param4) {
        boolean var1;
        double var4;
        boolean[] var18, var2, var8;
        Class2 var11, var12, var15, var7;
        boolean var6 = true;
        double var0 = (double) param3;
        Class2 var3 = new Class2();
        Class2 var9 = new Class2();
        Class2 var13 = new Class2();
        boolean[] var14 = new boolean[]{true, param2};
        Class1 var10 = new Class1();
        Class0 var16 = new Class0();
        String var17 = "���\u0003n��P�^�z!;";
        boolean[] var5 = new boolean[343];
        Class3 var19 = new Class3();
        for (int i5 = 0; (i5 < var5.length) && (i5 < var5.length); i5 = i5 + 1) {
            var6 = var5[i5];
            var3 = (Class2) TestClass.Test_sfield0;
            var10 = ((~(~((int) param4))) <= ((int) var0))
                            ? (var6 ? ((!(((short) param3) <= ((short) (((short) param4) % (((short) var0) + 663))))) ? TestClass.Test_sfield4
                                            : (Character.isLowerCase(((char) param1)) ? (new Class3()).Class3_field0 : TestClass.Test_sfield0.Class3_field0)) : TestClass.Test_sfield4)
                            : ((((byte) var0) >= ((byte) param1))
                                            ? (param2 ? ((param2 ^ var6) ? (var6 ? (new Class3()) : (new Class3())) : (param2 ? (new Class3()) : (new Class3()))).Class3_field0
                                                            : (((var6 ? (new Class3()) : (new Class3())) instanceof Class2) ? TestClass.Test_sfield4 : TestClass.Test_sfield4))
                                            : (param2 ? (((~((int) param0)) == (-((int) var0))) ? (param2 ? (new Class3()) : (new Class3())).Class3_field0
                                                            : (Class3.Class2_sfield0 ? TestClass.Test_sfield4 : var19.Class3_field0))
                                                            : ((!(!var6)) ? ((var6 & var6) ? (param2 ? (new Class1()) : (new Class1())) : var19.Class3_field0) : TestClass.Test_sfield4)));
            var5[i5] = var6 == param2;
        }
        var18 = (TestClass.Test_sfield4 instanceof Class0) ? var14 : var14;
        for (int i4 = 0; (i4 < Class0.Class0_sfield1.length) && (i4 < var18.length); i4 = i4 + 1) {
            var6 = var18[i4];
            if (((byte) (-((byte) (-((byte) param0))))) < ((byte) Math.log((-(-var0))))) {
                Class0.Class0_sfield1 = var14;
            } else if (((byte) (-((byte) (((byte) Class1.Class0_sfield0) * ((byte) var0))))) <= ((byte) var0)) {
                var0 = -(((double) param1) * (-((-param0) % ((-(-((double) param4))) + 214))));
            }
            if (((float) var0) > ((float) Integer.reverse(((int) var0)))) {
                Class1.Class0_sfield0 = (double) param1;
            } else if (null == var10) {
                var5 = var14;
                var16 = (Class0) var10;
            }
            Class0.Class0_sfield1[i4] = var6;
        }
        if (var6 && (((!((~((int) var0)) == (-((int) param0)))) == (!(((byte) param1) < ((byte) (-((byte) var0)))))) == param2)) {
            synchronized (TestClass.Test_sfield0) {
                var10 = TestClass.Test_sfield0.Class3_field0;
            }
        } else if (!param2) {
            var9 = var3;
            var13 = new Class3();
            synchronized (TestClass.Test_sfield0) {
                var10 = TestClass.Test_sfield4;
            }
            synchronized (java.lang.Object.class) {
                Class0.Class0_sfield1 = var5;
            }
        } else {
            Class3.Class2_sfield0 = !param2;
            if (param2) {
                var6 = ((var16 == null) != (!Class3.Class2_sfield0)) & (((byte) param0) > ((byte) (~((byte) var0))));
                var3 = var13;
            } else if (!(((short) (((short) (-((short) (~((short) (~((short) var0))))))) | ((short) (-((short) (((short) (~((short) var0))) | ((short) var0))))))) >= ((short) (((short) param1) +
                            ((short) Byte.toUnsignedInt(((byte) Class0.Class0_sfield0))))))) {
                TestClass.Test_sfield2 = ((((TestClass.Test_sfield2 + (("q{����I{�X\u0004�'�") + ("��J�r�\u0014�lY��,��둌��"))) + ((("gh") + ("kBT* .h^>Rr*.mPu/ J0/F")) + var17.trim())) + var17) +
                                var17) +
                                var17;
            }
            for (int i3 = 0; i3 < var14.length; i3 = i3 + 1) {
                var14[i3] = ((byte) (~((byte) param0))) > ((byte) (((byte) (~((byte) param1))) % (((byte) param4) + 270)));
            }
        }
        TestClass.Test_sfield3 = var5;
        var15 = var9;
        if (!(((byte) (~((byte) Math.addExact(((int) param0), param1)))) == ((byte) var0))) {
            Class0.Class0_sfield0 = (double) param3;
            var7 = var9;
            var8 = Class1.Class0_sfield1;
            for (int i2 = 0; (i2 < var5.length) && (i2 < var8.length); i2 = i2 + 1) {
                var6 = var8[i2];
                var3 = var7;
                var5[i2] = var6;
            }
            var4 = param0;
            for (int i1 = 0; i1 < var5.length; i1 = i1 + 1) {
                var5[i1] = !(((short) param3) <= ((short) (~((short) (((short) Class0.Class0_sfield0) / (((short) Class0.Class0_sfield0) + 905))))));
            }
            if (param2) {
                Class3.Class2_sfield0 = Character.isDigit(((char) (-((char) (((char) (-param3)) - ((char) (-param4)))))));
            } else if (!param2) {
                var3 = new Class3();
                var0 = (double) param4;
            } else if (TestClass.Test_sfield4 != null) {
                Class1.Class0_sfield1 = var5;
                var0 = (++var4) % ((-(-(-((double) param3)))) + 369);
            }
        } else if (!(null == var10)) {
            var12 = var13;
            synchronized (Class3.class) {
                var11 = var12;
                var3 = var11;
            }
            var0 = -((double) param1);
            Class0.Class0_sfield0 = param0;
        } else if (!param2) {
            var3 = var15;
            Class1.Class0_sfield1 = var14;
        }
        var1 = !var3.Class2_method0();
        var2 = Class1.Class0_sfield1;
        for (int i0 = 0; (i0 < TestClass.Test_sfield3.length) && (i0 < var2.length); i0 = i0 + 1) {
            var1 = var2[i0];
            TestClass.Test_sfield3[i0] = var1;
        }
        return var0;
    }

    // Checkstyle: resume

    @Test
    public void test0() {
        OptionValues opt = new OptionValues(getInitialOptions(), SimulationBasedLoopPeeling.Options.PeelingConsideredMinRelativeFrequency, 0D,
                        SimulationBasedLoopPeeling.Options.TrivialLoopSizeLimitForPeeling, 10000D, SimulationBasedLoopPeeling.Options.PeelingMidTierCostReductionFactor, 10000D);
        test(opt, "method2", 0.0, -1642083614, false, 'x', 'y');
    }

    @Test
    public void test1() {
        test("method2", 0.0, -1642083614, false, 'x', 'y');
    }

    /**
     * Compiles a snippet where loop peeling simulation sees an unused unsigned division whose divisor
     * is improved from a loop phi to a nonzero constant.
     */
    @Test
    public void testUnusedUnsignedDivCanonicalizesToNull() {
        OptionValues opt = new OptionValues(getInitialOptions(), SimulationBasedLoopPeeling.Options.PeelingConsideredMinRelativeFrequency, 0D,
                        SimulationBasedLoopPeeling.Options.TrivialLoopSizeLimitForPeeling, 10000D, SimulationBasedLoopPeeling.Options.PeelingMidTierCostReductionFactor, 10000D);
        test(opt, "unusedUnsignedDivCanonicalizesToNullSnippet");
    }

    /**
     * The unsigned division cannot be deleted before peeling because the divisor phi may include zero.
     * During simulation, the divisor is replaced with the first iteration value, which lets
     * canonicalization delete the unused division.
     */
    public static int unusedUnsignedDivCanonicalizesToNullSnippet() {
        int i = 2;
        int checksum = 0;
        while (true) {
            Integer.divideUnsigned(0, i);
            checksum += i & 1;
            if (GraalDirectives.injectBranchProbability(0.01, i > 100)) {
                break;
            }
            i = GraalDirectives.opaque(i + 1);
        }
        return checksum;
    }

    public static void testMonitorExitSnippet() {
        boolean stop = true;
        Object g = new Object();
        for (;;) {
            /*
             * This monitor's ID is shared by two monitor exit nodes, one of which is on the loop
             * exit path. The MonitorIdNode itself is considered part of the loop and duplicated
             * along with it by peeling. After removing the rest of the useless loop, we are left
             * with a pair of monitor enter/exit with MonitorId nodes that are equal but not shared.
             */
            synchronized (g) {
                if (stop) {
                    break;
                }
            }
            stop = false;
        }
    }

    @Test
    public void testMonitorExit() {
        test("testMonitorExitSnippet");
    }

    public static final int LowerBoundLargeDiff = 10;
    public static final int UpperBoundLargeDiff = 10000;

    public static int explicitExceptionLoop(int[] arr) {
        int i = 0;
        int result = 0;
        // will not have any usages after, we just need to force necessary phi nodes
        class L {
            @SuppressWarnings("unused") int x;
        }
        // needs to PEA
        L l = new L();
        while (true) {
            int len = arr.length;
            if (i >= len) {
                break;
            }
            if (arr[i] >= LowerBoundLargeDiff) {
                GraalDirectives.sideEffect(1);
                if (arr[i] < UpperBoundLargeDiff) {
                    GraalDirectives.sideEffect(2);
                    l.x--;
                }
            }
            l.x++;
            i++;
        }
        return result;
    }

    @Test
    public void testExplicitException() {
        OptionValues op = new OptionValues(getInitialOptions(), GraalOptions.LoopUnswitch, false, LoopPolicies.Options.PeelALot, true, PriorityInliningPhase.Options.UsePriorityInlining, false,
                        LoopPeelingPhase.Options.IterativePeelingLimit, 1);
        int[] arr = new int[1000];
        Random r = new Random(17);
        for (int i = 0; i < arr.length; i++) {
            arr[i] = r.nextInt();
        }
        StructuredGraph g = parseEager(getResolvedJavaMethod("explicitExceptionLoop"), AllowAssumptions.NO, op);
        new DisableOverflownCountedLoopsPhase().apply(g);

        new PartialEscapePhase(true, CanonicalizerPhase.create(), op).apply(g, getDefaultHighTierContext());
        new LoopPeelingPhase(new DefaultLoopPolicies(), CanonicalizerPhase.create()).apply(g, getDefaultHighTierContext());
        g.clearAllStateAfterForTestingOnly();
        CanonicalizerPhase.create().apply(g, getDefaultHighTierContext());
        assert GraphOrder.assertSchedulableGraph(g);
    }

    @Test
    public void testPeelingNegative01() {
        testPeelingOnly("originalTest");
        test("originalTest");
    }

    @Test
    public void testPeelingNegative02() {
        iFld = -1;
        originalTestVariation1();
        iFld = Integer.MAX_VALUE - 100_000;
        test("originalTestVariation1");
        testPeelingOnly("originalTestVariation1");
    }

    @Test
    public void testPeelingNegative03() {
        iFld = Integer.MAX_VALUE;
        originalTestVariation2();
        iFld = Integer.MIN_VALUE + 100000;
        test("originalTestVariation2");
        testPeelingOnly("originalTestVariation2");
    }

    @Test
    public void testPeelingNegative04() {
        testPeelingOnly("testUCMP");
        test("testUCMP");
    }

    static int iFld;

    private void testPeelingOnly(String snippet) {
        StructuredGraph g = parseEager(getResolvedJavaMethod(snippet), AllowAssumptions.NO);
        CanonicalizerPhase.create().apply(g, getDefaultHighTierContext());
        new DisableOverflownCountedLoopsPhase().apply(g);
        createSuites(getInitialOptions()).getHighTier().findPhase(LoopPeelingPhase.class, true).previous().apply(g, getDefaultHighTierContext());

    }

    // Ported from
    // jdk/test/hotspot/jtreg/compiler/loopopts/TestPartialPeelAtUnsignedTestsNegativeLimit.java
    public static boolean originalTest() {
        for (int i = Integer.MAX_VALUE - 50_000; Integer.compareUnsigned(i, -1) < 0; i++) {
            if (Integer.compareUnsigned(Integer.MIN_VALUE, i) < 0) {
                return true;
            }
        }
        return false;
    }

    public static int originalTestVariation1() {
        int a = 0;
        for (int i = iFld; Integer.compareUnsigned(i, -1) < 0; ++i) { // i <u -1

            if (i >= Integer.MIN_VALUE + 1 && i <= 100) { // Transformed to unsigned test.
                return a + 1;
            }
            a *= 23;
        }
        return a;
    }

    public static int originalTestVariation2() {
        int a = 0;
        for (int i = iFld; Integer.compareUnsigned(i, -1000) < 0; i--) { // i <u -1
            if (Integer.compareUnsigned(Integer.MAX_VALUE - 20, i) > 0) {
                return a - 1;
            }
            a = i;
        }
        return a;
    }

    // Ported from
    // jdk/test/hotspot/jtreg/compiler/ccp/TestPushCmpU3Node.java
    public static void testUCMP() {
        for (int i = Integer.MAX_VALUE - 50_000; Integer.compareUnsigned(i, -1) < 0; ++i) {
            if (Integer.compareUnsigned(Integer.MIN_VALUE, i) < 0) {
                return;
            }
        }
    }

    // Ported from jdk c1/TestRangeCheckEliminationOverflow.java
    // Checkstyle: stop
    public static int c1Test1() {
        int res = 0;
        int[] array = new int[0];
        for (int i = -2; i <= Integer.MAX_VALUE - 1; ++i) {
            // Index bounds: 0 <= x <= Integer.MIN_VALUE due to overflow
            // when computing upper bound via Integer.MAX_VALUE-1 + 2.
            res += array[i + 2];
            array[i + 2] = i;
            if (i >= 100)
                break;
        }
        return res;
    }

    public static int c1Test2() {
        int res = 0;
        int[] array = new int[50];
        for (int i = Integer.MIN_VALUE + 1; i <= 0; ++i) {
            // Index bounds: Integer.MAX_VALUE <= x <= -2 due to underflow
            // when computing lower bound via Integer.MIN_VALUE+1 - 2.
            res += array[i - 2];
            array[i - 2] = i;
            if (i >= (Integer.MIN_VALUE + 100))
                break;
        }
        return res;
    }

    public static int c1Test3() {
        int res = 0;
        int[] array = new int[0];
        for (int i = -1; i <= 100; ++i) {
            // Index bounds: Integer.MAX_VALUE <= x <= -2147483548 due to underflow
            // when computing lower bound via -1 + Integer.MIN_VALUE.
            res += array[i + Integer.MIN_VALUE];
            array[i + Integer.MIN_VALUE] = i;
        }
        return res;
    }

    public static int c1Test4() {
        int res = 0;
        int[] array = new int[0];
        for (int i = -1; i <= 1; ++i) {
            // Index bound: 2147483646 <= x <= Integer.MIN_VALUE due to overflow
            // when computing upper bound via 1 + Integer.MAX_VALUE.
            res += array[i + Integer.MAX_VALUE];
            array[i + Integer.MAX_VALUE] = i;
        }
        return res;
    }
    // Checkstyle: resume

    @Test
    public void testC1Overflow() {
        testPeelingOnly("c1Test1");
        testPeelingOnly("c1Test2");
        testPeelingOnly("c1Test3");
        testPeelingOnly("c1Test4");
    }

    // Checkstyle: stop
    static boolean flag0_1;
    static boolean flag0_2 = true;

    // ported from c2/TestAlignmentAdjustmentLimitOverflow.java

    @SuppressWarnings("unused")
    static void testAlignmentAdjustmentLimitOverflowTest0() {
        int zero = 3;
        int limit = 2;
        for (; limit < 4; limit *= 2) {

        }
        for (int i = 2; i < limit; i++) {
            zero = Integer.MAX_VALUE;
        }

        int[] iArr = new int[10];
        int i = Integer.MIN_VALUE + 1;
        do {
            if (flag0_1) {
            }

            try {
                iArr[i + -1 * Integer.MAX_VALUE + 1] = 34;
            } catch (Exception e) {
                if (flag0_2) {
                    return;
                }
            }
            i++;
        } while (i < Integer.MAX_VALUE);
    }
    // Checkstyle: resume

    @Test
    public void testC2Overflow() {
        test("testAlignmentAdjustmentLimitOverflowTest0");
    }

    public static void maxIntRangeSnippet() {
        for (int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; i++) {
            GraalDirectives.sideEffect();
        }
    }

    @Test
    public void maxIntRange() {
        testPeelingOnly("maxIntRangeSnippet");
    }

}
