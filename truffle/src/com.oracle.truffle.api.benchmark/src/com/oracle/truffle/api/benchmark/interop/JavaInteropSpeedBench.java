/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.benchmark.interop;

import java.util.Random;
import java.util.function.IntBinaryOperator;

import org.graalvm.polyglot.Context;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(1)
@State(Scope.Thread)
public class JavaInteropSpeedBench {
    private static final int REPEAT = 10000;
    private static final long SEED = 42;
    private static int[] arr;

    @Setup
    public void beforeTesting() {
        arr = initArray(REPEAT);
    }

    private static int[] initArray(int size) {
        Random r = new Random(SEED);
        int[] tmp = new int[size];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = r.nextInt(100000);
        }
        return tmp;
    }

    @Benchmark
    public int doMinMaxInJava() {
        int max = 0;
        for (int i = 0; i < arr.length; i++) {
            max = Math.max(arr[i], max);
        }
        return max;
    }

    private static IntBinaryOperator MAX;

    static {
        Context context = Context.create();
        MAX = context.asValue(new IntBinaryOperator() {
            @Override
            public int applyAsInt(int left, int right) {
                return Math.max(left, right);
            }
        }).as(IntBinaryOperator.class);
    }

    @Benchmark
    public int doMinMaxWithInterOp() {
        int max = 0;
        for (int i = 0; i < arr.length; i++) {
            max = MAX.applyAsInt(arr[i], max);
        }
        return max;
    }
}
