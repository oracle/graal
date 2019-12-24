/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.benchmark;

import static org.graalvm.wasm.benchmark.WasmBenchmarkSuiteBase.Defaults.FORKS;
import static org.graalvm.wasm.benchmark.WasmBenchmarkSuiteBase.Defaults.MEASUREMENT_ITERATIONS;
import static org.graalvm.wasm.benchmark.WasmBenchmarkSuiteBase.Defaults.WARMUP_ITERATIONS;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.predefined.testutil.TestutilModule;
import org.graalvm.wasm.utils.Assert;
import org.graalvm.wasm.utils.cases.WasmCase;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import org.graalvm.wasm.utils.WasmInitialization;

@Warmup(iterations = WARMUP_ITERATIONS)
@Measurement(iterations = MEASUREMENT_ITERATIONS)
@Fork(FORKS)
public abstract class WasmBenchmarkSuiteBase {
    public static class Defaults {
        public static final int MEASUREMENT_ITERATIONS = 6;
        public static final int WARMUP_ITERATIONS = 8;
        public static final int FORKS = 1;
    }

    public abstract static class WasmBenchmarkState {
        private WasmCase benchmarkCase;

        private Value benchmarkSetupOnce;
        private Value benchmarkSetupEach;
        private Value benchmarkTeardownEach;
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
            contextBuilder.option("wasm.Builtins", "testutil,env:emscripten,memory");

            Map<String, byte[]> binaries = benchmarkCase.createBinaries();
            Context context = contextBuilder.build();

            for (Map.Entry<String, byte[]> entry : binaries.entrySet()) {
                Source source = Source.newBuilder("wasm", ByteSequence.create(entry.getValue()), entry.getKey()).build();
                context.eval(source);
            }

            Value wasmBindings = context.getBindings("wasm");
            benchmarkSetupOnce = wasmBindings.getMember("_benchmarkSetupOnce");
            benchmarkSetupEach = wasmBindings.getMember("_benchmarkSetupEach");
            benchmarkTeardownEach = wasmBindings.getMember("_benchmarkTeardownEach");
            benchmarkRun = wasmBindings.getMember("_benchmarkRun");
            Assert.assertNotNull(String.format("No benchmarkRun method in %s.", wantedBenchmarkName), benchmarkRun);
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
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            // Note: we deliberately not reset the context here.
            // It would be slow, and the invariant we expect from the benchmarks
            // is that they can handle VM-state side-effects.
            // We may support benchmark-specific teardown actions in the future (at the invocation
            // level).

            benchmarkSetupEach.execute();
        }

        @TearDown(Level.Invocation)
        public void teardownInvocation() {
            benchmarkTeardownEach.execute();
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
