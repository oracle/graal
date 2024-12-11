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

import java.util.Arrays;
import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks for java.util.Arrays.fill.
 */
@State(Scope.Thread)
public class ArrayFillBenchmark extends BenchmarkBase {
    @Param({"16", "128", "1024", "4096"}) private int size;

    // Just a random index that we'll use to feed to bh.consume
    public int indexCheck;

    // Target arrays
    public boolean[] booleans;
    public byte[] bytes;
    public char[] chars;
    public short[] shorts;
    public int[] ints;
    public long[] longs;
    public float[] floats;
    public double[] doubles;

    @Setup
    public void setup() {
        Random rnd = new Random();

        indexCheck = rnd.nextInt(size);
        booleans = new boolean[size];
        bytes = new byte[size];
        chars = new char[size];
        shorts = new short[size];
        ints = new int[size];
        longs = new long[size];
        floats = new float[size];
        doubles = new double[size];
    }

    @Benchmark
    public void fillBooleans(Blackhole bh) {
        Arrays.fill(booleans, Boolean.TRUE);
        bh.consume(booleans[indexCheck]);
    }

    @Benchmark
    public void fillBytes(Blackhole bh) {
        Arrays.fill(bytes, Byte.MAX_VALUE);
        bh.consume(bytes[indexCheck]);
    }

    @Benchmark
    public void fillChars(Blackhole bh) {
        Arrays.fill(chars, Character.MAX_VALUE);
        bh.consume(chars[indexCheck]);
    }

    @Benchmark
    public void fillShorts(Blackhole bh) {
        Arrays.fill(shorts, Short.MAX_VALUE);
        bh.consume(shorts[indexCheck]);
    }

    @Benchmark
    public void fillInts(Blackhole bh) {
        Arrays.fill(ints, Integer.MAX_VALUE);
        bh.consume(ints[indexCheck]);
    }

    @Benchmark
    public void fillLongs(Blackhole bh) {
        Arrays.fill(longs, Long.MAX_VALUE);
        bh.consume(longs[indexCheck]);
    }

    @Benchmark
    public void fillFloats(Blackhole bh) {
        Arrays.fill(floats, Float.MAX_VALUE);
        bh.consume(floats[indexCheck]);
    }

    @Benchmark
    public void fillDoubles(Blackhole bh) {
        Arrays.fill(doubles, Double.MAX_VALUE);
        bh.consume(doubles[indexCheck]);
    }
}
