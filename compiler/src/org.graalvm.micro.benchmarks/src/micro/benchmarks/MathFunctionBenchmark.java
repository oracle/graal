/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks cost of Math intrinsics.
 */
public class MathFunctionBenchmark extends BenchmarkBase {

    @State(Scope.Benchmark)
    public static class ThreadState {
        double[] data = randomDoubles(100);
        double[] result = new double[100];
        double k = data[0];

        static double[] randomDoubles(int len) {
            double[] data = new double[len];
            Random r = new Random();
            for (int i = 0; i < data.length; i++) {
                data[i] = r.nextDouble();
            }
            return data;
        }
    }

    @Benchmark
    public void mathLog(ThreadState state) {
        double[] data = state.data;
        for (int i = 0; i < data.length; i++) {
            double[] result = state.result;
            result[i] = Math.log(data[i]);
        }
    }

    @Benchmark
    public void mathLog10(ThreadState state) {
        double[] data = state.data;
        for (int i = 0; i < data.length; i++) {
            double[] result = state.result;
            result[i] = Math.log10(data[i]);
        }
    }

    @Benchmark
    public void mathSin(ThreadState state) {
        double[] data = state.data;
        for (int i = 0; i < data.length; i++) {
            double[] result = state.result;
            result[i] = Math.sin(data[i]);
        }
    }

    @Benchmark
    public void mathCos(ThreadState state) {
        double[] data = state.data;
        for (int i = 0; i < data.length; i++) {
            double[] result = state.result;
            result[i] = Math.cos(data[i]);
        }
    }

    @Benchmark
    public void mathTan(ThreadState state) {
        double[] data = state.data;
        for (int i = 0; i < data.length; i++) {
            double[] result = state.result;
            result[i] = Math.tan(data[i]);
        }
    }

    @Benchmark
    public void mathSqrt(ThreadState state, Blackhole blackhole) {
        blackhole.consume(Math.sqrt(state.k));
    }

    @Benchmark
    public void strictMathSqrt(ThreadState state, Blackhole blackhole) {
        blackhole.consume(StrictMath.sqrt(state.k));
    }

    @Benchmark
    public void mathSignum(ThreadState state, Blackhole blackhole) {
        double[] data = state.data;
        for (int i = 0; i < data.length; i++) {
            double[] result = state.result;
            result[i] = Math.signum(data[i]);
        }
    }

    @Benchmark
    public void mathCopySign(ThreadState state, Blackhole blackhole) {
        double[] data = state.data;
        for (int i = 0; i < data.length; i++) {
            double[] result = state.result;
            result[i] = Math.copySign(data[i], data[i] + 1.0D);
        }
    }
}
