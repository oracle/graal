/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

// Copied from jdk/test/micro/org/openjdk/bench/java/lang/FloatClassCheck.java
@State(Scope.Thread)
public class FloatClassCheckBenchmark extends BenchmarkBase {

    RandomGenerator rng;
    static final int BUFFER_SIZE = 1024;
    float[] inputs;
    boolean[] storeOutputs;
    int[] cmovOutputs;
    int[] branchOutputs;

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    static int call() {
        return 1;
    }

    @Setup
    public void setup() {
        storeOutputs = new boolean[BUFFER_SIZE];
        cmovOutputs = new int[BUFFER_SIZE];
        branchOutputs = new int[BUFFER_SIZE];
        inputs = new float[BUFFER_SIZE];
        RandomGenerator rng = RandomGeneratorFactory.getDefault().create(0);
        float input;
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (i % 5 == 0) {
                input = (i % 2 == 0) ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
            } else if (i % 3 == 0) {
                input = Float.NaN;
            } else {
                input = rng.nextFloat();
            }
            inputs[i] = input;
        }
    }

    @Benchmark
    @OperationsPerInvocation(BUFFER_SIZE)
    public void testIsInfiniteStore() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            storeOutputs[i] = Float.isInfinite(inputs[i]);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BUFFER_SIZE)
    public void testIsInfiniteCMov() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            cmovOutputs[i] = Float.isInfinite(inputs[i]) ? 9 : 7;
        }
    }

    @Benchmark
    @OperationsPerInvocation(BUFFER_SIZE)
    public void testIsInfiniteBranch() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            cmovOutputs[i] = Float.isInfinite(inputs[i]) ? call() : 7;
        }
    }

}
