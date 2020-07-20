/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.wasm.predefined.testutil.TestutilModule;
import org.graalvm.wasm.utils.Assert;
import org.graalvm.wasm.utils.cases.WasmCase;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

import static org.graalvm.wasm.utils.SystemProperties.DISABLE_COMPILATION_FLAG;

@Warmup(iterations = 6)
@Measurement(iterations = 8)
@Fork(1)
public abstract class WasmBenchmarkSuiteBase {
    public abstract static class WasmBenchmarkState {
        private WasmCase benchmarkCase;
        private Context context;
        private Value benchmarkSetupEach;
        private Value benchmarkTeardownEach;
        private Value benchmarkRun;
        private Value result;

        /**
         * Benchmarks must not be validated via their standard out, unlike tests.
         */
        private ByteArrayOutputStream dummyStdout = new ByteArrayOutputStream();

        abstract protected String benchmarkResource();

        @Setup(Level.Trial)
        public void setup() throws IOException, InterruptedException {
            final Context.Builder contextBuilder = Context.newBuilder("wasm");
            contextBuilder.option("wasm.Builtins", "testutil,env:emscripten,wasi_snapshot_preview1");
            if (!Objects.isNull(DISABLE_COMPILATION_FLAG)) {
                contextBuilder.allowExperimentalOptions(true);
                contextBuilder.option("engine.Compilation", "false");
            }
            context = contextBuilder.build();
            benchmarkCase = WasmCase.loadBenchmarkCase(benchmarkResource());
            benchmarkCase.getSources().forEach(context::eval);

            Value wasmBindings = context.getBindings("wasm");
            Value benchmarkSetupOnce = wasmBindings.getMember("benchmarkSetupOnce");
            benchmarkSetupEach = wasmBindings.getMember("benchmarkSetupEach");
            benchmarkTeardownEach = wasmBindings.getMember("benchmarkTeardownEach");
            benchmarkRun = wasmBindings.getMember("benchmarkRun");
            Assert.assertNotNull(String.format("No benchmarkRun method in %s.", benchmarkCase.name()), benchmarkRun);

            if (benchmarkSetupOnce != null) {
                benchmarkSetupOnce.execute();
            }
        }

        @TearDown(Level.Trial)
        public void teardown() {
            context.close();
        }

        @Setup(Level.Iteration)
        public void setupIteration() throws InterruptedException {
            // Reset result.
            result = null;
        }

        @TearDown(Level.Iteration)
        public void teardownIteration() throws InterruptedException {
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
            benchmarkTeardownEach.execute(0);
        }

        public void run() {
            this.result = benchmarkRun.execute();
        }

        public Value benchmarkRun() {
            return benchmarkRun;
        }

        public void setResult(Value result) {
            this.result = result;
        }
    }
}
