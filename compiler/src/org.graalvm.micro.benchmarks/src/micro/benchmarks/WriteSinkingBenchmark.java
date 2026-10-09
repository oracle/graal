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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import static org.openjdk.jmh.annotations.Mode.Throughput;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Throughput)
@Fork(value = 1)
@Warmup(iterations = 3)
@Measurement(iterations = 10)
public class WriteSinkingBenchmark {

    @State(Scope.Thread)
    public static class Context {
        int a;
        int b;
        int c;
        int d;
        int e;
        int f;

        static int sa;
        static int sb;
        static int sc;
        static int sd;
        static int se;
        static int sf;

        int limit = 16384;
    }

    @State(Scope.Thread)
    public static class LowTripContext extends Context {
        @Param({"0", "1"}) int lowTripLimit;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=false", "-Djdk.graal.PartialUnroll=false"})
    public Object benchSimpleLoopWithoutWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            c.a = i;
        }
        return c.a;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=true", "-Djdk.graal.PartialUnroll=false"})
    public Object benchSimpleWithWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            c.a = i;
        }
        return c.a;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=false", "-Djdk.graal.PartialUnroll=false"})
    public Object benchSequence2WritesLoopWithoutWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            c.a = i;
            c.b = i;
        }
        return c.a;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=true", "-Djdk.graal.PartialUnroll=false"})
    public Object benchSequence2WritesWithWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            c.a = i;
            c.b = i;
        }
        return c.a;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=false", "-Djdk.graal.PartialUnroll=false"})
    public Object benchSequence3WritesLoopWithoutWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            c.a = i;
            c.b = i;
            c.c = i;
        }
        return c.a;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=true", "-Djdk.graal.PartialUnroll=false"})
    public Object benchSequence3WritesWithWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            c.a = i;
            c.b = i;
            c.c = i;
        }
        return c.a;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=false", "-Djdk.graal.PartialUnroll=false"})
    public Object benchSequence6WritesLoopWithoutWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            c.a = i;
            c.b = i;
            c.c = i;
            c.d = i;
            c.e = i;
            c.f = i;
        }
        return c.a;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=true", "-Djdk.graal.PartialUnroll=false"})
    public Object benchSequence6WritesWithWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            c.a = i;
            c.b = i;
            c.c = i;
            c.d = i;
            c.e = i;
            c.f = i;
        }
        return c.a;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=false", "-Djdk.graal.PartialUnroll=false"})
    public Object benchStaticSimpleLoopWithoutWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            Context.sa = i;
        }
        return Context.sa;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=true", "-Djdk.graal.PartialUnroll=false"})
    public Object benchStaticSimpleWithWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            Context.sa = i;
        }
        return Context.sa;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=false", "-Djdk.graal.PartialUnroll=false"})
    public Object benchStaticSequence2WritesLoopWithoutWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            Context.sa = i;
            Context.sb = i;
        }
        return Context.sa;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=true", "-Djdk.graal.PartialUnroll=false"})
    public Object benchStaticSequence2WritesWithWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            Context.sa = i;
            Context.sb = i;
        }
        return Context.sa;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=false", "-Djdk.graal.PartialUnroll=false"})
    public Object benchStaticSequence3WritesLoopWithoutWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            Context.sa = i;
            Context.sb = i;
            Context.sc = i;
        }
        return Context.sa;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=true", "-Djdk.graal.PartialUnroll=false"})
    public Object benchStaticSequence3WritesWithWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            Context.sa = i;
            Context.sb = i;
            Context.sc = i;
        }
        return Context.sa;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=false", "-Djdk.graal.PartialUnroll=false"})
    public Object benchStaticSequence6WritesLoopWithoutWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            Context.sa = i;
            Context.sb = i;
            Context.sc = i;
            Context.sd = i;
            Context.se = i;
            Context.sf = i;
        }
        return Context.sa;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=true", "-Djdk.graal.PartialUnroll=false"})
    public Object benchStaticSequence6WritesWithWriteSinking(Context c) {
        for (int i = 0; i < c.limit; i++) {
            Context.sa = i;
            Context.sb = i;
            Context.sc = i;
            Context.sd = i;
            Context.se = i;
            Context.sf = i;
        }
        return Context.sa;
    }

    @Benchmark
    @Warmup(iterations = 1, time = 1)
    @Measurement(iterations = 1, time = 1)
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=false", "-Djdk.graal.PartialUnroll=false"})
    public Object benchLowTripSimpleWithoutWriteSinking(LowTripContext c) {
        for (int i = 0; i < c.lowTripLimit; i++) {
            c.a = i;
        }
        return c.a;
    }

    @Benchmark
    @Warmup(iterations = 1, time = 1)
    @Measurement(iterations = 1, time = 1)
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=true", "-Djdk.graal.PartialUnroll=false"})
    public Object benchLowTripSimpleWithWriteSinking(LowTripContext c) {
        for (int i = 0; i < c.lowTripLimit; i++) {
            c.a = i;
        }
        return c.a;
    }

    @Benchmark
    @Warmup(iterations = 1, time = 1)
    @Measurement(iterations = 1, time = 1)
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=false", "-Djdk.graal.PartialUnroll=false"})
    public Object benchLowTripSequence3WritesWithoutWriteSinking(LowTripContext c) {
        for (int i = 0; i < c.lowTripLimit; i++) {
            c.a = i;
            c.b = i;
            c.c = i;
        }
        return c.a;
    }

    @Benchmark
    @Warmup(iterations = 1, time = 1)
    @Measurement(iterations = 1, time = 1)
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=true", "-Djdk.graal.PartialUnroll=false"})
    public Object benchLowTripSequence3WritesWithWriteSinking(LowTripContext c) {
        for (int i = 0; i < c.lowTripLimit; i++) {
            c.a = i;
            c.b = i;
            c.c = i;
        }
        return c.a;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=true", "-Djdk.graal.PartialUnroll=false"})
    public int benchSplitOrderWithWriteSinking(Context c1, Context c2) {
        for (int i = 0; i < c1.limit; i++) {
            if (c1.c < c2.c) {
                c1.a = c1.e;
                c2.a = c1.f;
            } else {
                c2.a = c1.e;
                c1.a = c1.f;
            }
            c1.c = c2.d;
        }
        return c1.e + c1.f;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=false", "-Djdk.graal.PartialUnroll=false"})
    public int benchSplitOrderWithoutWriteSinking(Context c1, Context c2) {
        for (int i = 0; i < c1.limit; i++) {
            if (c1.c < c2.c) {
                c1.a = c1.e;
                c2.a = c1.f;
            } else {
                c2.a = c1.e;
                c1.a = c1.f;
            }
            c1.c = c2.d;
        }
        return c1.e + c1.f;
    }

    @Benchmark
    @Warmup(iterations = 1, time = 1)
    @Measurement(iterations = 1, time = 1)
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=false", "-Djdk.graal.PartialUnroll=false"})
    public int benchLowTripSplitOrderWithoutWriteSinking(LowTripContext c1, Context c2) {
        for (int i = 0; i < c1.lowTripLimit; i++) {
            if (c1.c < c2.c) {
                c1.a = c1.e;
                c2.a = c1.f;
            } else {
                c2.a = c1.e;
                c1.a = c1.f;
            }
            c1.c = c2.d;
        }
        return c1.e + c1.f;
    }

    @Benchmark
    @Warmup(iterations = 1, time = 1)
    @Measurement(iterations = 1, time = 1)
    @Fork(jvmArgsAppend = {"-Djdk.graal.OptWriteSinking=true", "-Djdk.graal.PartialUnroll=false"})
    public int benchLowTripSplitOrderWithWriteSinking(LowTripContext c1, Context c2) {
        for (int i = 0; i < c1.lowTripLimit; i++) {
            if (c1.c < c2.c) {
                c1.a = c1.e;
                c2.a = c1.f;
            } else {
                c2.a = c1.e;
                c1.a = c1.f;
            }
            c1.c = c2.d;
        }
        return c1.e + c1.f;
    }
}
