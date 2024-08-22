/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(jvmArgsAppend = "-Djdk.graal.VectorizeLoops=false")
public class FPComparisonBenchmark {
    static final int LENGTH = 1000;

    double[] x;
    double[] y;

    @Param({"0.0", "0.5", "1.0", "NaN"}) double d;

    @Setup
    public void setup() {
        Random random = new Random(1000);
        x = new double[LENGTH];
        y = new double[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            boolean mayBeNaN = random.nextInt(10) == 0;
            if (mayBeNaN) {
                x[i] = Double.NaN;
            } else {
                x[i] = random.nextDouble();
            }
            mayBeNaN = random.nextInt(10) == 0;
            if (mayBeNaN) {
                y[i] = Double.NaN;
            } else {
                y[i] = random.nextDouble();
            }
        }
    }

    @Benchmark
    public void testMemMem(Blackhole bh) {
        for (int i = 0; i < LENGTH; i++) {
            if (x[i] < y[i]) {
                bh.consume(0);
            }
        }
    }

    @Benchmark
    public void testMemReg(Blackhole bh) {
        double d = this.d;
        for (int i = 0; i < LENGTH; i++) {
            if (x[i] < d) {
                bh.consume(0);
            }
        }
    }

    @Benchmark
    public void testRegMem(Blackhole bh) {
        double d = this.d;
        for (int i = 0; i < LENGTH; i++) {
            if (d < x[i]) {
                bh.consume(0);
            }
        }
    }
}
