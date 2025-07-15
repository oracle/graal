/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.truffle.benchmark;

import java.util.Random;
import java.util.stream.DoubleStream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ExactMath;

/**
 * Benchmarks {@link ExactMath} functions.
 *
 * Checkstyle: stop
 */
@Warmup(iterations = 5, time = TruffleBenchmark.Defaults.ITERATION_TIME)
@Measurement(iterations = 5, time = TruffleBenchmark.Defaults.ITERATION_TIME)
@Fork(TruffleBenchmark.Defaults.FORKS)
public class ExactMathBenchmark extends TruffleBenchmark {

    @State(Scope.Benchmark)
    public static class ThreadState {
        // fixed random seed for reproducibility
        private static final int RANDOM_SEED = 42;
        private static final int LENGTH = 10_000;

        Random r = new Random(RANDOM_SEED);
        long[] longs = r.longs(LENGTH).toArray();
        double[] doubles = DoubleStream.concat(
                        DoubleStream.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.0, 0.0),
                        r.doubles(LENGTH, -0x1p10, 0x1p+65)).toArray();

        double[] doublesValidUI64 = r.doubles(LENGTH, Math.nextUp(-1.0), 0x1p64).toArray();
        double[] doublesValidSI64 = r.doubles(LENGTH, -0x1p63, 0x1p63).toArray();
    }

    /**
     * Signed version as a performance reference point for unsigned.
     */
    @Benchmark
    public void doubleToSignedLongSat(ThreadState state, Blackhole blackhole) {
        for (double input : state.doubles) {
            blackhole.consume((long) input);
        }
    }

    @Benchmark
    public void doubleToUnsignedLongSat(ThreadState state, Blackhole blackhole) {
        for (double input : state.doubles) {
            blackhole.consume(ExactMath.truncateToUnsignedLong(input));
        }
    }

    @Benchmark
    public void doubleToUnsignedLongSatFallback(ThreadState state, Blackhole blackhole) {
        for (double input : state.doubles) {
            blackhole.consume(truncateToUnsignedLongFallback(input));
        }
    }

    /**
     * Signed version as a performance reference point for unsigned.
     */
    @Benchmark
    public void doubleToSignedLongChecked(ThreadState state, Blackhole blackhole) {
        for (double input : state.doublesValidSI64) {
            blackhole.consume(i64_trunc_f64_s(input));
        }
    }

    @Benchmark
    public void doubleToUnsignedLongChecked(ThreadState state, Blackhole blackhole) {
        for (double input : state.doublesValidUI64) {
            blackhole.consume(i64_trunc_f64_u(input));
        }
    }

    /**
     * Signed version as a performance reference point for unsigned.
     */
    @Benchmark
    public void longToDoubleSigned(ThreadState state, Blackhole blackhole) {
        for (long input : state.longs) {
            blackhole.consume((double) input);
        }
    }

    @Benchmark
    public void longToDoubleUnsigned(ThreadState state, Blackhole blackhole) {
        for (long input : state.longs) {
            blackhole.consume(ExactMath.unsignedToDouble(input));
        }
    }

    @Benchmark
    public void longToDoubleUnsignedFallback(ThreadState state, Blackhole blackhole) {
        for (long input : state.longs) {
            blackhole.consume(unsignedToDoubleFallback(input));
        }
    }

    /**
     * @see ExactMath#unsignedToDouble(long)
     */
    public static double unsignedToDoubleFallback(long x) {
        if (x >= 0) {
            return x;
        } else {
            double halfRoundUp = ((x >>> 1) | (x & 1));
            return halfRoundUp + halfRoundUp;
        }
    }

    /**
     * @see ExactMath#truncateToUnsignedLong(double)
     */
    private static long truncateToUnsignedLongFallback(double x) {
        if (x >= 0x1p63) {
            long signedResult = (long) (x - 0x1p63);
            return signedResult | (1L << 63);
        } else {
            long signedResult = (long) x;
            return signedResult & ~(signedResult >> 63); // max(result, 0)
        }
    }

    private long i64_trunc_f64_u(double x) {
        if (x > -1.0 && x < 0x1p64) {
            return ExactMath.truncateToUnsignedLong(x);
        } else {
            return truncError(x);
        }
    }

    private long i64_trunc_f64_s(double x) {
        if (x >= -0x1p63 && x < 0x1p63) {
            return (long) x;
        } else {
            return truncError(x);
        }
    }

    private static long truncError(double x) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (Double.isNaN(x)) {
            throw new AssertionError(x);
        } else {
            throw new AssertionError(x);
        }
    }
}
