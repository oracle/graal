/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Benchmarks for java.util.Arrays.fill.
 */
@State(Scope.Benchmark)
public class ArrayFillBenchmark extends BenchmarkBase {
    @Param({"1", "4", "16", "128", "1024", "8192"}) public static int size;

    public boolean[] bools = new boolean[size];
    public byte[] bytes = new byte[size];
    public char[] chars = new char[size];
    public short[] shorts = new short[size];
    public int[] ints = new int[size];
    public long[] longs = new long[size];
    public float[] floats = new float[size];
    public double[] doubles = new double[size];

    @Benchmark
    public void fill_bytes() {
        Arrays.fill(bytes, (byte) 123);
    }

    @Benchmark
    public void fill_shorts() {
        Arrays.fill(shorts, (short) 123123);
    }

    @Benchmark
    public void fill_ints() {
        Arrays.fill(ints, 123123123);
    }
}
