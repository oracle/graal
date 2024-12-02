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
    public int index_check;

    // Target arrays
    public byte[] bytes;
    public short[] shorts;
    public int[] ints;

    @Setup
    public void setup() {
        Random rnd = new Random();

        index_check = rnd.nextInt(size);

        bytes = new byte[size];
        shorts = new short[size];
        ints = new int[size];
    }

    @Benchmark
    public void fill_bytes(Blackhole bh) {
        Arrays.fill(bytes, (byte) 123);
        bh.consume(bytes[index_check]);
    }

    @Benchmark
    public void fill_shorts(Blackhole bh) {
        Arrays.fill(shorts, (short) 123123);
        bh.consume(shorts[index_check]);
    }

    @Benchmark
    public void fill_ints(Blackhole bh) {
        Arrays.fill(ints, 123123123);
        bh.consume(ints[index_check]);
    }
}
