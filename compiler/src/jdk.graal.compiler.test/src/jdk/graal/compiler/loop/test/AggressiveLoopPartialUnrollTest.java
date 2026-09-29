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

import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;

import org.junit.Test;

import jdk.graal.compiler.loop.phases.AggressivePartialUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;
import jdk.graal.compiler.vector.phases.LoopVectorizationPhase;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;

public class AggressiveLoopPartialUnrollTest extends GraalCompilerTest {

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        for (LoopBeginNode loop : graph.getNodes().filter(LoopBeginNode.class)) {
            if (loop.isMainLoop()) {
                return;
            }
        }
        fail("expected a main loop");
    }

    public static int sumArray(int[] text) {
        int sum = 0;
        for (int i = 0; injectBranchProbability(0.99, i < text.length); ++i) {
            sum += text[i];
        }
        return sum;
    }

    @Test
    public void testSumArray() {
        int[] data = new int[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
        test(disableVectorization(), "sumArray", data);
    }

    @Test
    public void testSumReductionFloat() {
        float[] a = new float[512];
        float[] b = new float[512];
        float[] c = new float[512];
        float[] d = new float[512];

        for (int k = 50; k < 512; k++) {
            sumInitListFloat(a, b, c, k);
            test(disableVectorization(), "sumListReductionFloat", a, b, c, d, 0.0f, k);
        }

    }

    public static void sumInitListFloat(float[] a, float[] b, float[] c, int processLen) {
        for (int i = 0; i < processLen; i++) {
            a[i] = i * 2;
            b[i] = i;
            c[i] = i + 5;
        }
    }

    public static float sumListReductionFloat(float[] a, float[] b, float[] c, float[] d, float total, int processLen) {
        float newTotal = total;
        for (int j = 0; j < 2; j++) {
            for (int i = 1; injectBranchProbability(0.99, i < processLen); i++) {
                d[i] = (a[i] * b[i - i]) + (a[i - 1] * c[i]) + (b[i] * c[i - 1]);
            }
        }

        newTotal += d[0];
        newTotal += d[processLen - 1];
        return newTotal;
    }

    @Test
    public void testSumReductionDouble() {
        double[] a = new double[512];
        double[] b = new double[512];
        double[] c = new double[512];
        double[] d = new double[512];

        for (int k = 50; k < 512; k++) {
            sumInitListDouble(a, b, c, k);
            test(disableVectorization(), "sumListReductionDouble", a, b, c, d, 0.0, k);
        }

    }

    public static void sumInitListDouble(double[] a, double[] b, double[] c, int processLen) {
        for (int i = 0; i < processLen; i++) {
            a[i] = i * 2;
            b[i] = i;
            c[i] = i + 5;
        }
    }

    public static double sumListReductionDouble(double[] a, double[] b, double[] c, double[] d, double total, int processLen) {
        double newTotal = total;
        for (int j = 0; j < 2; j++) {
            for (int i = 1; injectBranchProbability(0.99, i < processLen); i++) {
                d[i] = (a[i] * b[i - i]) + (a[i - 1] * c[i]) + (b[i] * c[i - 1]);
            }
        }

        newTotal += d[0];
        newTotal += d[processLen - 1];
        return newTotal;
    }

    public static long testMultiplySnippet(int arg) {
        long r = 1;
        for (int i = 0; injectBranchProbability(0.99, i < arg); i++) {
            r *= i;
        }
        return r;
    }

    @Test
    public void testMultiply() {
        test(disableVectorization(), "testMultiplySnippet", 9);
    }

    public static int testNestedSumSnippet(int d) {
        int c = 0;
        for (int i = 0; i < d; i++) {
            for (int j = 0; injectBranchProbability(0.99, j < i); j++) {
                c += j;
            }
        }
        return c;
    }

    @Test
    public void testNestedSum() {
        for (int i = 0; i < 1000; i++) {
            test(disableVectorization(), "testNestedSumSnippet", i);
        }
    }

    public static int testSumDownSnippet(int d) {
        int c = 0;
        for (int j = d; injectBranchProbability(0.99, j > -4); j--) {
            c += j & 0x3;
        }
        return c;
    }

    @Test
    public void testSumDown() {
        test(disableVectorization(), "testSumDownSnippet", 1);
        for (int i = 0; i < 8; i++) {
            test(disableVectorization(), "testSumDownSnippet", i);
        }
    }

    public static long init = Runtime.getRuntime().totalMemory();
    private int x;
    private int z;

    public int[] testComplexSnippet(int d) {
        x = 3;
        int y = 5;
        z = 7;
        for (int i = 0; i < d; i++) {
            for (int j = 0; injectBranchProbability(0.99, j < i); j++) {
                GraalDirectives.neverWriteSink();
                z += x;
            }
            y = x ^ z;
            if ((i & 4) == 0) {
                z--;
            } else if ((i & 8) == 0) {
                Runtime.getRuntime().totalMemory();
            }
        }
        return new int[]{x, y, z};
    }

    @Test
    public void testComplex() {
        for (int i = 0; i < 10; i++) {
            test("testComplexSnippet", i);
        }
        test("testComplexSnippet", 10);
        test("testComplexSnippet", 100);
        test("testComplexSnippet", 1000);
    }

    public double[] execute(double[] doubles) {
        int m1 = doubles.length - 1;
        for (int i = 1; i < m1; i++) {
            for (int j = 1; injectBranchProbability(0.99, j < m1); j++) {
                doubles[j] = doubles[j + 1];
            }
        }
        return doubles;
    }

    public static double[] getDoubles(int length) {
        double[] result = new double[length];
        for (int i = 0; i < result.length; i++) {
            result[i] = 1;
        }
        return result;
    }

    @Test
    public void testSOR() {
        OptionValues options = new OptionValues(getInitialOptions(), VectorIntrinsics.Options.Vectorization, false);
        for (int size = 250; size >= 3; size--) {
            final int finalSize = size;
            test(options, "execute", supply(() -> getDoubles(finalSize)));
        }
    }

    public static int countNullsSnippet(Object[] array) {
        int sortedLen = 0;
        for (int i = 0; injectBranchProbability(0.99, i < array.length); i++) {
            Object object = array[i];
            if (injectBranchProbability(0.50, object != null)) {
                sortedLen++;
            }
        }
        return sortedLen;
    }

    @Test
    public void testCountNulls() {
        for (int size = 250; size >= 1; size--) {
            Object[] testArray = new Object[size];
            for (int i = 0; i < testArray.length; i++) {
                test(disableVectorization(), "countNullsSnippet", (Object) testArray);
                testArray[i] = testArray;
            }
        }
    }

    public static int[] copyData(int in, int out, int[] oPix, int[] pixel) {
        int inIndex = in;
        int outIndex = out;
        while (injectBranchProbability(0.99, inIndex >= 0)) {
            oPix[outIndex--] = pixel[inIndex--];
            oPix[outIndex--] = pixel[inIndex--];
            oPix[outIndex--] = pixel[inIndex--];
            outIndex--;
        }
        return oPix;
    }

    @Test
    public void testCopyData() {
        for (int size = 250; size >= 1; size--) {
            final int finalSize = size;
            int[] pixel = new int[size * 3];
            for (int i = 0; i < pixel.length; i++) {
                pixel[i] = i;
            }
            for (int i = 0; i < size / 3; i++) {
                test("copyData", 3 * i + 2, 4 * i + 3, supply(() -> new int[finalSize * 4]), pixel);
            }
        }
    }

    private static OptionValues disableVectorization() {
        return new OptionValues(getInitialOptions(), LoopVectorizationPhase.Options.VectorizeFoldShaped, false, LoopVectorizationPhase.Options.VectorizeMapShaped, false,
                        LoopVectorizationPhase.Options.VectorizeConditional, false);
    }

    @Test
    public void testIDiv() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, AggressivePartialUnrollPhase.Options.AggressivePartialUnroll, false,
                        VectorIntrinsics.Options.Vectorization, false, LoopInversionPhase.Options.LoopInversion, false);
        test(opt, "idivSnippet", Integer.MAX_VALUE);
    }

    public static int idivSnippet(int limit) {
        int res = 0;
        for (int i = 1; injectBranchProbability(0.99, i < limit); i++) {
            res += 100 / i;
        }
        return res;
    }
}
