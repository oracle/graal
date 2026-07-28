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
package micro.benchmarks;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class FloatingPointRemainderBenchmark extends BenchmarkBase {

    private static final int OPERATIONS = 1024;
    private static final double DOUBLE_DIVISOR = 0.1d;
    private static final double DOUBLE_INCREMENT = 1.0d;
    private static final float FLOAT_DIVISOR = 0.1f;
    private static final float FLOAT_INCREMENT = 1.0f;
    private double sum = 1.0d;
    private double comp = 0.1d;

    private double doubleValue;
    private float floatValue;
    private double[] doubleDividends;
    private double[] doubleDivisors;
    private double[] doubleResults;
    private float[] floatDividends;
    private float[] floatDivisors;
    private float[] floatResults;

    static double twoSumLow(double a, double b, double sum) {
        final double bVirtual = sum - a;
        return (a - (sum - bVirtual)) + (b - bVirtual);
    }

    @Setup
    public void setup() {
        doubleValue = 1.0d;
        floatValue = 1.0f;
        doubleDividends = new double[OPERATIONS];
        doubleDivisors = new double[OPERATIONS];
        doubleResults = new double[OPERATIONS];
        floatDividends = new float[OPERATIONS];
        floatDivisors = new float[OPERATIONS];
        floatResults = new float[OPERATIONS];

        Random random = new Random(61951);
        for (int i = 0; i < OPERATIONS; i++) {
            doubleDividends[i] = 0.5d + random.nextDouble() * 4096.0d;
            doubleDivisors[i] = 0.01d + random.nextDouble();
            floatDividends[i] = (float) (0.5d + random.nextDouble() * 4096.0d);
            floatDivisors[i] = (float) (0.01d + random.nextDouble());
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public double doubleRemainderDependencyChain() {
        double value = doubleValue;
        for (int i = 0; i < OPERATIONS; i++) {
            value = (value + DOUBLE_INCREMENT) % DOUBLE_DIVISOR;
        }
        doubleValue = value;
        return value;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public void doubleRemainderArray() {
        double[] dividends = doubleDividends;
        double[] divisors = doubleDivisors;
        double[] results = doubleResults;
        for (int i = 0; i < OPERATIONS; i++) {
            results[i] = dividends[i] % divisors[i];
        }
    }

    @Benchmark
    @Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
    @Fork(3)
    public void doubleRemainderCompensatedSum() {
        final double newSum = sum % comp;
        comp += twoSumLow(0.1d, comp, newSum);
        sum += comp;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public float floatRemainderDependencyChain() {
        float value = floatValue;
        for (int i = 0; i < OPERATIONS; i++) {
            value = (value + FLOAT_INCREMENT) % FLOAT_DIVISOR;
        }
        floatValue = value;
        return value;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS)
    public void floatRemainderArray() {
        float[] dividends = floatDividends;
        float[] divisors = floatDivisors;
        float[] results = floatResults;
        for (int i = 0; i < OPERATIONS; i++) {
            results[i] = dividends[i] % divisors[i];
        }
    }
}
