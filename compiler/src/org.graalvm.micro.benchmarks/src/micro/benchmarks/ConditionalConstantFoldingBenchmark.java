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
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures integer operations applied to conditionals with at least one constant value.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ConditionalConstantFoldingBenchmark {
    private static final int LENGTH = 1024;

    private int[] values;
    private int[] firstResults;
    private int[] secondResults;

    @Setup
    public void setup() {
        values = new int[LENGTH];
        firstResults = new int[LENGTH];
        secondResults = new int[LENGTH];
        Random random = new Random(42);
        for (int i = 0; i < values.length; i++) {
            values[i] = random.nextInt();
        }
    }

    @Benchmark
    public int leftShift() {
        int sum = 0;
        for (int value : values) {
            sum += (value < 0 ? 3 : 5) << 4;
        }
        return sum;
    }

    @Benchmark
    public int rightShift() {
        int sum = 0;
        for (int value : values) {
            sum += (value < 0 ? -16 : 8) >> 2;
        }
        return sum;
    }

    @Benchmark
    public int unsignedRightShift() {
        int sum = 0;
        for (int value : values) {
            sum += (value < 0 ? -16 : 8) >>> 2;
        }
        return sum;
    }

    @Benchmark
    public int negateOneConstant() {
        int sum = 0;
        for (int value : values) {
            sum += -(value < 0 ? 4 : value);
        }
        return sum;
    }

    @Benchmark
    public int addOneConstant() {
        int sum = 0;
        for (int value : values) {
            sum += (value < 0 ? 4 : value) + 10;
        }
        return sum;
    }

    @Benchmark
    public int subtractFromOneConstant() {
        int sum = 0;
        for (int value : values) {
            sum += 10 - (value < 0 ? 4 : value);
        }
        return sum;
    }

    @Benchmark
    public int multiUseOneConstant() {
        int sum = 0;
        for (int value : values) {
            int selected = value < 0 ? 4 : value;
            sum += (-selected) ^ (selected + 10);
        }
        return sum;
    }

    /**
     * Keeps both uses observable. One-constant folding must leave the shared selection in place to
     * avoid duplicating its comparison and conditional select in generated code.
     */
    @Benchmark
    public int multiUseGraphGrowth() {
        int sum = 0;
        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            int selected = value < 0 ? 4 : value;
            int first = -selected;
            int second = selected + 10;
            firstResults[i] = first;
            secondResults[i] = second;
            sum += first ^ second;
        }
        return sum;
    }
}
