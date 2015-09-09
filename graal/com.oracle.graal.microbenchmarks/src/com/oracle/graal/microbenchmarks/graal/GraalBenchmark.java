package com.oracle.graal.microbenchmarks.graal;

import static com.oracle.graal.microbenchmarks.graal.GraalBenchmark.Defaults.*;

import org.openjdk.jmh.annotations.*;

/**
 * All classes defining Graal benchmarks must subclass this class as it defines the default value
 * for each benchmark option. Individual options can be overridden in the subclasses or by an
 * individual benchmark.
 */
@Warmup(iterations = WARMUP_ITERATIONS)
@Measurement(iterations = MEASUREMENT_ITERATIONS)
@Fork(FORKS)
public class GraalBenchmark {

    public static class Defaults {
        public static final int MEASUREMENT_ITERATIONS = 10;
        public static final int WARMUP_ITERATIONS = 10;
        public static final int FORKS = 1;
    }
}
