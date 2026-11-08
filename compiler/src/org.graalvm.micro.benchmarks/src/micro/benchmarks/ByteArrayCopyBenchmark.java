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
 * Copies of {@code byte[]} ranges through {@link System#arraycopy}: between two arrays, and
 * within one array with the destination after the source without overlap (the shape of an LZ4
 * match copy) and with overlap. Offsets change from call to call so that the copy addresses are
 * not always aligned and the size-dependent branches of the copy are not perfectly predicted.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ByteArrayCopyBenchmark extends BenchmarkBase {

    @Param({"4", "8", "16", "24", "32", "48", "64", "128", "256", "4096", "65536"}) int length;

    private byte[] src;
    private byte[] dst;
    private int offset;

    @Setup
    public void setup() {
        src = new byte[2 * length + 64 * 1024];
        dst = new byte[2 * length + 64 * 1024];
        for (int i = 0; i < src.length; i++) {
            src[i] = (byte) i;
        }
    }

    private int nextOffset() {
        offset = (offset + 7) & 0x3fff;
        return offset;
    }

    @Benchmark
    public void disjoint() {
        int off = nextOffset();
        System.arraycopy(src, off, dst, off + 1, length);
    }

    @Benchmark
    public void sameArrayApart() {
        int off = nextOffset();
        System.arraycopy(src, off, src, off + length + 3, length);
    }

    @Benchmark
    public void sameArrayOverlapping() {
        int off = nextOffset();
        System.arraycopy(src, off, src, off + 5, length);
    }
}
