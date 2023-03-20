package com.oracle.truffle.api.operation.test.bml;

import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;

@Warmup(iterations = BaseBenchmark.WARMUP_ITERATIONS, time = BaseBenchmark.ITERATION_TIME)
@Measurement(iterations = BaseBenchmark.MEASUREMENT_ITERATIONS, time = BaseBenchmark.ITERATION_TIME)
@Fork(BaseBenchmark.FORKS)
class BaseBenchmark {
    public static final int MEASUREMENT_ITERATIONS = 10;
    public static final int WARMUP_ITERATIONS = 10;
    public static final int ITERATION_TIME = 1;
    public static final int FORKS = 1;
}