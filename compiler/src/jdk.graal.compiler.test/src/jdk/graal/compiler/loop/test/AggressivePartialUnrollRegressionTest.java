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

import java.util.List;
import java.util.Random;

import org.graalvm.collections.EconomicMap;
import org.junit.Test;

import jdk.graal.compiler.loop.phases.AggressivePartialUnrollPhase;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.virtual.phases.ea.FinalPartialEscapePhase;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;

public class AggressivePartialUnrollRegressionTest extends GraalCompilerTest {

    static int InstanceCount;
    static int IFld;
    static int VMeth1CheckSum;
    public static final int N = 400;
    public static int[] IArrFld = new int[N];
    public static long[][] LArrFld = new long[N][N];
    public float[] fArrFld = new float[N];

    static void vMeth() {
        @SuppressWarnings("unused")
        int b = 3;
        int c;
        float e = 102.253F;
        byte by = 1;
        InstanceCount = c = 1;
        IFld = VMeth1CheckSum = 1;
        do {
            b ^= InstanceCount;
            double f = 0.5189;
            IFld *= (int) f;
            e += by;
        } while (GraalDirectives.injectIterationCount(196, ++c < 197));
        VMeth1CheckSum += (int) e;
    }

    public static void init(boolean[] a, boolean seed) {
        for (int j = 0; j < a.length; j++) {
            a[j] = (j % 2 == 0) ? seed : (j % 3 == 0);
        }
    }

    // double --------------------------------------------------
    public static void init(double[] a, double seed) {
        for (int j = 0; j < a.length; j++) {
            a[j] = (j % 2 == 0) ? seed + j : seed - j;
        }
    }

    public static void init(double[][] a, double seed) {
        for (int j = 0; j < a.length; j++) {
            init(a[j], seed);
        }
    }

    // double --------------------------------------------------
    public static double checkSum(double[] a) {
        double sum = 0;
        for (int j = 0; j < a.length; j++) {
            sum += (a[j] / (j + 1) + a[j] % (j + 1));
        }
        return sum;
    }

    public static double checkSum(double[][] a) {
        double sum = 0;
        for (int j = 0; j < a.length; j++) {
            sum += checkSum(a[j]);
        }
        return sum;
    }

    public long mainTest() {
        int g;
        int h;
        int c = 6;
        double[][] b = new double[400][400];
        init(b, 91.20226);
        vMeth();
        for (g = 1; g < 400; g += 16) {
            h = 1;
            do {
                for (int i = 0; i < 2; i++) {
                    IArrFld[g] -= h;
                }
                b[h][g - 1] *= c;
                h++;
            } while (h < 224);
        }
        long e = Double.doubleToLongBits(checkSum(b));
        return e;
    }

    @Test
    public void test01() {
        test("vMeth");
    }

    @Test
    public void test02() {
        test("mainTest");
    }

    public static void mainFuzz2() {
        try {
            Test2 k = new Test2();
            for (int e = 0; e < 10; e++) {
                k.c();
            }
        } catch (Exception ex) {
        }
    }

    static class Test2 {
        int a = 400;
        byte[][] b = new byte[a][a];

        void c() {
            int e;
            int f;
            G h = new G();
            for (e = 4; e < 314; ++e) {
                for (f = 1; f > e - 400; --f) {
                    b[1][1] -= (byte) (h.i = f);
                }
            }
        }
    }

    static class G {
        float i;
    }

    @Test
    public void test03() {
        test("mainFuzz2");
    }

    static int[] startPosition;

    public static int build(char[] src, int max) {
        int i = 0;
        if (i >= max) {
            return 0;
        }
        do {
            char ch = src[i];
            GraalDirectives.blackhole(ch);
            GraalDirectives.sideEffect(i);
            if (++i < max) {
                continue;
            } else {
                GraalDirectives.deoptimize(DeoptimizationAction.InvalidateReprofile,
                                DeoptimizationReason.UnreachedCode, false);
                break;
            }
        } while (true/* ++i < max */);
        return i;
    }

    @Test
    public void javac01() {
        char[] c = "I".toCharArray();
        build(c, c.length);
        OptionValues opts = new OptionValues(getInitialOptions(),
                        AggressivePartialUnrollPhase.Options.ForceUnroll, true,
                        AggressivePartialUnrollPhase.Options.PartialUnrollMaxIterationsHighTier, 4,
                        DuplicationOptions.DuplicateALot, true, DuplicationOptions.DuplicationMinBranchFrequency, 0D,
                        DuplicationOptions.DuplicationBudgetFactor, 100D,
                        GraalOptions.OptDuplication, true,
                        GraalOptions.OptDeoptimizationGrouping, false,
                        AggressivePartialUnrollPhase.Options.InsertPreMainPostOnly, true);
        test(opts, "build", c, c.length);
        c = "Hello world".toCharArray();
        test(opts, "build", c, c.length);
        opts = new OptionValues(getInitialOptions(),
                        AggressivePartialUnrollPhase.Options.ForceUnroll, true,
                        AggressivePartialUnrollPhase.Options.PartialUnrollMaxIterationsHighTier, 4,
                        DuplicationOptions.DuplicateALot, true, DuplicationOptions.DuplicationMinBranchFrequency, 0D,
                        DuplicationOptions.DuplicationBudgetFactor, 100D,
                        GraalOptions.OptDuplication, true,
                        GraalOptions.OptDeoptimizationGrouping, false);
        test(opts, "build", c, c.length);
        c = "Hello world.".toCharArray();
        test(opts, "build", c, c.length);
        c = "Hello world..".toCharArray();
        test(opts, "build", c, c.length);
    }

    int result = 0;
    int[] a = new int[10000];

    public int snippet(int x, int size) {
        result = 0;
        for (int i = 0; GraalDirectives.injectBranchProbability(BranchProbabilityNode.FAST_PATH_PROBABILITY, i < GraalDirectives.opaque(size)); i++) {
            result = Integer.compareUnsigned(a[i], x);
        }
        return result;
    }

    @Test
    public void testUnsigned() {
        Random r = new Random(17);
        for (int i = 0; i < a.length; i++) {
            test(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false, GraalOptions.PreferUnsignedComparison, false), "snippet", r.nextInt(), i);
        }
    }

    static int res = 0;

    static void countedAfterSnippet(int i) {
        int j = i;
        do {
            res += 42;
            j += Integer.MAX_VALUE;
        } while (j <= 100);
    }

    @Test
    public void testCountedAfter() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, AggressivePartialUnrollPhase.Options.HighTierPartialUnrolling, false,
                        AggressivePartialUnrollPhase.Options.ForceUnroll, true);
        getCode(getResolvedJavaMethod("countedAfterSnippet"), opt);
        test(opt, "countedAfterSnippet", 0);
    }

    static class Class0 {
        public StringBuilder f1;

        @SuppressWarnings("unused")
        Class0() {
            short var5 = 6260;
            boolean var3 = getRandomInstance().nextBoolean();
            StringBuilder var1;
            short var4 = Integer.SIZE;
            var1 = this.f1.append(var3);
        }

        public StringBuilder m0(StringBuilder param0) {
            StringBuilder var0 = param0;
            return var0;
        }

    }

    @SuppressWarnings("unused")
    public static void method2(short param0, boolean param3) {
        double[] var25;
        Class0 var13;
        int var18;
        Class0 var10 = new Class0();
        Class0 var17 = new Class0();
        Class0 var4 = new Class0();
        double[] var20 = new double[6];
        var25 = var20;
        var18 = 0;
        while (param0 >= param0) {
            var20 = param3 ? var25 : new double[]{};
            var17 = (byte) param0 < (byte) var18 ? new Class0() : var10;
            if (GraalDirectives.injectBranchProbability(0.01, var18 > 100)) {
                break;
            }
            var18 = var18 + 1;
        }
        var13 = var10;
        var13.m0(new StringBuilder());
    }

    @Test
    public void testFuzzed01() {
        test("method2", (short) getRandomInstance().nextInt(), getRandomInstance().nextBoolean());
    }

    public static int strangeSignPattern(int limit) {
        int i = 1;
        if (Integer.compareUnsigned(0, limit) < 0) {
            while (true) {
                // the loop in question is inverted
                if (i == -124) {
                    // first deopt
                    GraalDirectives.deoptimizeAndInvalidate();
                    GraalDirectives.sideEffect();
                }
                if (i == -12224) {
                    // second deopt
                    GraalDirectives.deoptimizeAndInvalidate();
                    GraalDirectives.sideEffect();
                }
                int oldI = i;
                i++;
                if (Integer.compareUnsigned(oldI - 1, limit) < 0) {
                    continue;
                }
                break;
            }
        }
        return i;
    }

    @Test
    public void testSignStrangePattern() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("strangeSignPattern"), AllowAssumptions.NO);
        new DisableOverflownCountedLoopsPhase().apply(g);
        new FinalPartialEscapePhase(false, createCanonicalizerPhase(), null, getInitialOptions()).apply(g, getDefaultHighTierContext());
        for (LoopBeginNode lb : g.getNodes(LoopBeginNode.TYPE)) {
            // to bypass the "must have protection" logic
            lb.markCountedStripMinedInner();
        }
        new AggressivePartialUnrollPhase(new LoopPolicies() {

            @Override
            public boolean shouldInvert(Loop loop, IfNode controlSplit, CoreProviders providers) {
                return false;
            }

            @Override
            public UnswitchingDecision shouldUnswitch(Loop loop, EconomicMap<ValueNode, List<ControlSplitNode>> controlSplits) {
                return null;
            }

            @Override
            public boolean shouldTryUnswitch(Loop loop) {
                return false;
            }

            @Override
            public boolean shouldPeel(Loop loop, ControlFlowGraph cfg, CoreProviders providers, int peelingIteration) {
                return false;
            }

            @Override
            public boolean shouldPartiallyUnroll(Loop loop, CoreProviders providers) {
                return loop.loopBegin().isSimpleLoop() || loop.loopBegin().isMainLoop() && loop.loopBegin().getUnrollFactor() < 16;
            }

            @Override
            public boolean shouldFullUnroll(Loop loop) {
                return false;
            }
        }, createCanonicalizerPhase()).apply(g, getDefaultHighTierContext());
        createCanonicalizerPhase().apply(g, getDefaultHighTierContext());
    }
}
