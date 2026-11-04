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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Copies of {@code Object[]} ranges of different lengths, through the entry points that copy
 * references: {@link System#arraycopy}, {@link Arrays#copyOf} and {@link Object#clone}. The
 * source and the long-lived destination are allocated in the setup, so after the first
 * collections they are old objects and every copy into them needs the GC's post-write barrier.
 * The overlapping cases are the moves of {@code ArrayList.add(0, e)} and
 * {@code ArrayList.remove(0)}; the odd-offset cases start the copy at element 1 so that, with
 * 4-byte references, the addresses are not 8-byte aligned.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ObjectArrayCopyBenchmark extends BenchmarkBase {

    @Param({"4", "16", "32", "64", "256", "4096", "65536"}) int length;

    private Object[] src;
    private Object[] dst;

    @Setup
    public void setup() {
        src = new Object[length + 1];
        dst = new Object[length + 1];
        for (int i = 0; i < src.length; i++) {
            src[i] = Integer.valueOf(i);
            dst[i] = Integer.valueOf(-i);
        }
    }

    /** Copy into an existing array. */
    @Benchmark
    public Object[] arraycopy() {
        System.arraycopy(src, 0, dst, 0, length);
        return dst;
    }

    /** Copy where source and destination both start at an odd element. */
    @Benchmark
    public Object[] arraycopyOddOffsets() {
        System.arraycopy(src, 1, dst, 1, length);
        return dst;
    }

    /** Copy where only the destination starts at an odd element. */
    @Benchmark
    public Object[] arraycopyMixedOffsets() {
        System.arraycopy(src, 0, dst, 1, length);
        return dst;
    }

    /** Overlapping move to higher indexes, as in {@code ArrayList.add(0, e)}. */
    @Benchmark
    public Object[] shiftRight() {
        System.arraycopy(dst, 0, dst, 1, length);
        return dst;
    }

    /** Overlapping move to lower indexes, as in {@code ArrayList.remove(0)}. */
    @Benchmark
    public Object[] shiftLeft() {
        System.arraycopy(dst, 1, dst, 0, length);
        return dst;
    }

    /** Copy into a new array, as in {@code new ArrayList<>(list)}. */
    @Benchmark
    public Object[] copyOf() {
        return Arrays.copyOf(src, length);
    }

    @Benchmark
    public Object[] cloneArray() {
        return src.clone();
    }
}
