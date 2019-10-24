/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.benchmark;

import static com.oracle.truffle.wasm.benchmark.WasmBenchmarkSuiteBase.Defaults.FORKS;
import static com.oracle.truffle.wasm.benchmark.WasmBenchmarkSuiteBase.Defaults.MEASUREMENT_ITERATIONS;
import static com.oracle.truffle.wasm.benchmark.WasmBenchmarkSuiteBase.Defaults.WARMUP_ITERATIONS;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.wasm.predefined.testutil.TestutilModule;
import com.oracle.truffle.wasm.utils.Assert;
import com.oracle.truffle.wasm.utils.WasmInitialization;
import com.oracle.truffle.wasm.utils.cases.WasmCase;

@Warmup(iterations = WARMUP_ITERATIONS)
@Measurement(iterations = MEASUREMENT_ITERATIONS)
@Fork(FORKS)
public abstract class WasmBenchmarkSuiteBase {
    public static class Defaults {
        public static final int MEASUREMENT_ITERATIONS = 10;
        public static final int WARMUP_ITERATIONS = 10;
        public static final int FORKS = 1;
    }

    public abstract static class WasmBenchmarkState {
        private WasmCase benchmarkCase;

        private Value benchmarkSetupOnce;
        private Value benchmarkRun;
        private Value resetContext;
        private Value customInitializer;
        private WasmInitialization initialization;
        private Value result;
        /**
         * Benchmarks must not be validated via their standard out, unlike tests.
         */
        private ByteArrayOutputStream dummyStdout = new ByteArrayOutputStream();

        @Setup(Level.Trial)
        public void setup() throws IOException, InterruptedException {
            final String wantedBenchmarkName = WasmBenchmarkOptions.BENCHMARK_NAME;

            if (wantedBenchmarkName == null || wantedBenchmarkName.trim().equals("")) {
                Assert.fail("Please select a benchmark by setting -Dwasmbench.benchmarkName");
            }

            benchmarkCase = WasmCase.collectFileCase("bench", benchmarkResource(), WasmBenchmarkOptions.BENCHMARK_NAME);

            Assert.assertNotNull(String.format("Benchmark %s.%s not found", benchmarkResource(), wantedBenchmarkName), benchmarkCase);

            Context.Builder contextBuilder = Context.newBuilder("wasm");
            contextBuilder.option("wasm.PredefinedModules", "testutil:testutil,env:emscripten");

            byte[] binary = benchmarkCase.createBinary();
            Context context = contextBuilder.build();
            Source source = Source.newBuilder("wasm", ByteSequence.create(binary), "test").build();

            context.eval(source);

            Value wasmBindings = context.getBindings("wasm");
            benchmarkSetupOnce = wasmBindings.getMember("_benchmarkSetupOnce");
            benchmarkRun = wasmBindings.getMember("_benchmarkRun");
            resetContext = wasmBindings.getMember(TestutilModule.Names.RESET_CONTEXT);
            customInitializer = wasmBindings.getMember(TestutilModule.Names.RUN_CUSTOM_INITIALIZATION);
            initialization = benchmarkCase.initialization();

            // Initialization is done only once, and before the module starts.
            // It is the benchmark's job to ensure that it executes meaningful workloads
            // that run correctly despite the fact that the VM state changed.
            // I.e. benchmark workloads must assume that they are run multiple times.
            if (initialization != null) {
                customInitializer.execute(initialization);
            }

            if (benchmarkSetupOnce != null) {
                benchmarkSetupOnce.execute();
            }
        }

        @Setup(Level.Iteration)
        public void setupIteration() {
            // Reset result.
            result = null;
        }

        @TearDown(Level.Iteration)
        public void teardownIteration() {
            // Validate result.
            WasmCase.validateResult(benchmarkCase.data().resultValidator(), result, dummyStdout);

            // Note: we deliberately not reset the context here.
            // It would be slow, and the invariant we expect from the benchmarks
            // is that they can handle VM-state side-effects.
            // We may support benchmark-specific teardown actions in the future (at the invocation level).
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
        }

        public Value benchmarkRun() {
            return benchmarkRun;
        }

        public void setResult(Value result) {
            this.result = result;
        }

        protected abstract String benchmarkResource();
    }
}
