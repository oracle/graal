package com.oracle.truffle.wasm.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

public class EmscriptenBenchmark extends WasmBenchmark {

    @State(Scope.Benchmark)
    public static class EmscriptenFibBenchmarkState extends WasmBenchmarkState {
        @Override
        String testName() {
            return "fib";
        }
    }

    @Benchmark
    public void fib(EmscriptenFibBenchmarkState state) {
        state.function.execute();
    }

}
