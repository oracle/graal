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

import static com.oracle.truffle.wasm.benchmark.WasmBenchmark.Defaults.FORKS;
import static com.oracle.truffle.wasm.benchmark.WasmBenchmark.Defaults.MEASUREMENT_ITERATIONS;
import static com.oracle.truffle.wasm.benchmark.WasmBenchmark.Defaults.WARMUP_ITERATIONS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.wasm.predefined.testutil.TestutilModule;
import com.oracle.truffle.wasm.utils.Assert;
import com.oracle.truffle.wasm.utils.WasmBinaryTools;
import com.oracle.truffle.wasm.utils.WasmInitialization;
import com.oracle.truffle.wasm.utils.WasmResource;

@Warmup(iterations = WARMUP_ITERATIONS)
@Measurement(iterations = MEASUREMENT_ITERATIONS)
@Fork(FORKS)
public class WasmBenchmark {
    public static class Defaults {
        public static final int MEASUREMENT_ITERATIONS = 10;
        public static final int WARMUP_ITERATIONS = 10;
        public static final int FORKS = 1;
    }

    public abstract static class WasmBenchmarkState {
        Map<String, Value> mainFunctions = new HashMap<>();
        Map<String, Value> resetContexts = new HashMap<>();
        Map<String, Value> customInitializers = new HashMap<>();
        Map<String, WasmInitialization> initializations = new HashMap<>();

        @Setup
        public void setup() throws IOException, InterruptedException {
            Collection<? extends WasmBenchCase> benchCases = collectBenchCases();

            Context.Builder contextBuilder = Context.newBuilder("wasm");

            for (WasmBenchCase benchCase : benchCases) {
                byte[] binary = benchCase.createBinary();
                Context context = contextBuilder.build();
                Source source = Source.newBuilder("wasm", ByteSequence.create(binary), "bench").build();

                context.eval(source);

                mainFunctions.put(benchCase.name, context.getBindings("wasm").getMember("_main"));
                resetContexts.put(benchCase.name, context.getBindings("wasm").getMember(TestutilModule.Names.RESET_CONTEXT));
                customInitializers.put(benchCase.name, context.getBindings("wasm").getMember(TestutilModule.Names.RUN_CUSTOM_INITIALIZATION));
                initializations.put(benchCase.name, benchCase.initialization);
            }
        }
    }

    // TODO: should we split the bench cases into bundles, like the test cases?
    private static Collection<? extends WasmBenchCase> collectBenchCases() throws IOException {
        Collection<WasmBenchCase> collectedCases = new ArrayList<>();

        // Open the wasm_bench_index file of the bench bundle.
        // The wasm_bench_index file contains the available benchmarks for that bundle.
        InputStream index = WasmBenchmark.class.getResourceAsStream("/bench/wasm_bench_index");
        BufferedReader indexReader = new BufferedReader(new InputStreamReader(index));

        // Iterate through the available test of the bundle.
        while (indexReader.ready()) {
            String benchName = indexReader.readLine().trim();
            if (benchName.equals("") || benchName.startsWith("#")) {
                // Skip empty lines or lines starting with a hash (treat as a comment).
                continue;
            }

            Object mainContent = WasmResource.getResourceAsTest(String.format("/bench/%s", benchName), true);
            String initContent = WasmResource.getResourceAsString(String.format("/bench/%s.init", benchName), false);
            WasmInitialization initializer = WasmInitialization.create(initContent);

            if (mainContent instanceof String) {
                collectedCases.add(benchCase(benchName, (String) mainContent, initializer));
            } else if (mainContent instanceof byte[]) {
                collectedCases.add(benchCase(benchName, (byte[]) mainContent, initializer));
            } else {
                Assert.fail("Unknown content type: " + mainContent.getClass());
            }
        }

        return collectedCases;
    }

    private static WasmStringBenchCase benchCase(String name, String program, WasmInitialization initialization) {
        return new WasmStringBenchCase(name, program, initialization);
    }

    private static WasmBinaryBenchCase benchCase(String name, byte[] program, WasmInitialization initialization) {
        return new WasmBinaryBenchCase(name, program, initialization);
    }

    private abstract static class WasmBenchCase {
        private final String name;
        private final WasmInitialization initialization;

        WasmBenchCase(String name, WasmInitialization initialization) {
            this.name = name;
            this.initialization = initialization;
        }

        public abstract byte[] createBinary() throws IOException, InterruptedException;

        public WasmInitialization initialization() {
            return initialization;
        }
    }

    private static class WasmStringBenchCase extends WasmBenchCase {
        private final String program;

        WasmStringBenchCase(String name, String program, WasmInitialization initialization) {
            super(name, initialization);
            this.program = program;
        }

        @Override
        public byte[] createBinary() throws IOException, InterruptedException {
            return WasmBinaryTools.compileWat(program);
        }
    }

    private static class WasmBinaryBenchCase extends WasmBenchCase {
        private final byte[] binary;

        WasmBinaryBenchCase(String name, byte[] binary, WasmInitialization initialization) {
            super(name, initialization);
            this.binary = binary;
        }


        @Override
        public byte[] createBinary() throws IOException, InterruptedException {
            return binary;
        }
    }
}
