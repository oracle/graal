/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package micro.benchmarks;

import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

// Copied from jdk/test/micro/org/openjdk/bench/java/math/FpRoundingBenchmark.java
@State(Scope.Thread)
public class FpRoundingBenchmark extends BenchmarkBase {

    @Param({"1024"}) public int testSize;

    public double[] dArgV1;
    public double[] resD;
    public long[] resL;
    public float[] fArgV1;
    public float[] resF;
    public int[] resI;

    public final double[] doubleSpecialVals = {
                    0.0, -0.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_VALUE, -Double.MIN_VALUE,
                    Double.MIN_NORMAL
    };

    public final float[] floatSpecialVals = {
                    0.0f, -0.0f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
                    Float.MAX_VALUE, -Float.MAX_VALUE, Float.MIN_VALUE, -Float.MIN_VALUE,
                    Float.MIN_NORMAL
    };

    @Setup(Level.Trial)
    public void bmSetup() {
        int i = 0;
        Random r = new Random(1024);

        dArgV1 = new double[testSize];
        resD = new double[testSize];

        for (; i < doubleSpecialVals.length; i++) {
            dArgV1[i] = doubleSpecialVals[i];
        }

        for (; i < testSize; i++) {
            dArgV1[i] = Double.longBitsToDouble(r.nextLong());
        }

        fArgV1 = new float[testSize];
        resF = new float[testSize];

        i = 0;
        for (; i < floatSpecialVals.length; i++) {
            fArgV1[i] = floatSpecialVals[i];
        }

        for (; i < testSize; i++) {
            fArgV1[i] = Float.intBitsToFloat(r.nextInt());
        }

        resI = new int[testSize];
        resL = new long[testSize];
    }

    @Benchmark
    public void testCeil() {
        for (int i = 0; i < testSize; i++) {
            resD[i] = Math.ceil(dArgV1[i]);
        }
    }

    @Benchmark
    public void testFloor() {
        for (int i = 0; i < testSize; i++) {
            resD[i] = Math.floor(dArgV1[i]);
        }
    }

    @Benchmark
    public void testRint() {
        for (int i = 0; i < testSize; i++) {
            resD[i] = Math.rint(dArgV1[i]);
        }
    }

    @Benchmark
    public void testRoundDouble() {
        for (int i = 0; i < testSize; i++) {
            resL[i] = Math.round(dArgV1[i]);
        }
    }

    @Benchmark
    public void testRoundFloat() {
        for (int i = 0; i < testSize; i++) {
            resI[i] = Math.round(fArgV1[i]);
        }
    }
}
