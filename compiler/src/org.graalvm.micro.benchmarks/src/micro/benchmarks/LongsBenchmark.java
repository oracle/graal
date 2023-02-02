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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
public class LongsBenchmark extends BenchmarkBase {

    @Param("500") private int size;

    private long bound;
    private long[] longArraySmall;

    @Setup
    public void setup() {
        bound = 20000L;
        longArraySmall = new long[size];
        for (int i = 0; i < size; i++) {
            longArraySmall[i] = 100L * i + i + 103L;
        }
    }

    @Benchmark
    public int compareUnsignedIndirect() {
        int r = 0;
        for (int i = 0; i < size; i++) {
            r += (Long.compareUnsigned(longArraySmall[i], bound - 16) < 0) ? 1 : 0;
        }
        return r;
    }

    @Benchmark
    public int compareUnsignedDirect(Blackhole bh) {
        int r = 0;
        for (int i = 0; i < size; i++) {
            r += Long.compareUnsigned(longArraySmall[i], bound - 16);
        }
        return r;
    }
}
