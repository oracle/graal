/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.utils.cases.WasmCase.collectFileCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.utils.WasmBinaryTools;
import org.graalvm.wasm.utils.WasmResource;
import org.graalvm.wasm.utils.cases.WasmCase;

/**
 * For each benchmark case in {@code args}, measures the difference in heap size after forced GC
 * before and after the parsing phase. This corresponds to the memory allocated by the parser that
 * is needed to run the program.
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * $ java org.graalvm.wasm.benchmark.MemoryProfiler --warmup-iterations 10 --result-iterations 6 go-hello
 * </pre>
 *
 * <p>
 * Example result:
 * </p>
 *
 * <pre>
 * go-hello: warmup iteration[0]: 50.863 MB
 * go-hello: warmup iteration[1]: 12.708 MB
 * ...
 * go-hello: iteration[0]: 16.902 MB
 * go-hello: iteration[1]: 17.161 MB
 * ...
 * go-hello: median: 17.057 MB
 * go-hello: min: 17.161 MB
 * go-hello: max: 16.902 MB
 * go-hello: average: 17.044 MB
 * </pre>
 *
 * <p>
 * This class is used by the <code>memory</code> mx benchmark suite, runnable with
 * <code>mx --dy /compiler benchmark memory -- --jvm=server --jvm-config=graal-core</code>. The
 * suite is defined in <code>MemoryBenchmarkSuite</code> in <code>mx_benchmark.py</code>.
 * </p>
 */
public class MemoryFootprintBenchmarkRunner {
    // We currently hardcode the path to memory-footprint-related tests. We might in the future
    // generalize this to include more paths, if that turns out necessary.
    private static final String BENCHCASES_TYPE = "bench";
    private static final String BENCHCASES_RESOURCE = "wasm/memory";

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args[0].equals("--list")) {
            System.out.println(WasmResource.getResourceIndex(String.format("/%s/%s", BENCHCASES_TYPE, BENCHCASES_RESOURCE)));
            return;
        }

        if (args.length < 5 || !args[0].equals("--warmup-iterations") || !args[2].equals("--result-iterations")) {
            System.err.println("Usage: --warmup-iterations <n> --result-iterations <n> <case_spec>...");
        }

        final int warmup_iterations = Integer.parseInt(args[1]);
        final int result_iterations = Integer.parseInt(args[3]);

        // Support debugging
        int offset = 4;
        if (args[4].equals("Listening")) {
            offset = 11;
        }

        for (final String caseSpec : Arrays.copyOfRange(args, offset, args.length)) {
            final WasmCase benchmarkCase = collectFileCase(BENCHCASES_TYPE, BENCHCASES_RESOURCE, caseSpec);
            assert benchmarkCase != null : String.format("Test case %s/%s not found.", BENCHCASES_RESOURCE, caseSpec);

            final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
            contextBuilder.allowExperimentalOptions(true);
            contextBuilder.option("wasm.Builtins", "go");
            contextBuilder.option("wasm.MemoryOverheadMode", "true");

            final List<Double> results = new ArrayList<>();

            for (int i = 0; i < warmup_iterations + result_iterations; ++i) {
                final Context context = contextBuilder.build();

                final double heapSizeBefore = getHeapSize();

                // The code we want to profile:
                var sources = benchmarkCase.getSources(EnumSet.noneOf(WasmBinaryTools.WabtOption.class));
                sources.forEach(context::eval);
                context.getBindings(WasmLanguage.ID).getMember(benchmarkCase.name()).getMember("run");

                final double heapSizeAfter = getHeapSize();
                final double result = heapSizeAfter - heapSizeBefore;
                if (i < warmup_iterations) {
                    System.out.format("%s: warmup iteration[%d]: %.3f MB%n", caseSpec, i, result);
                } else {
                    results.add(result);
                    System.out.format("%s: iteration[%d]: %.3f MB%n", caseSpec, i, result);
                }

                context.close();
            }

            Collections.sort(results);

            System.out.format("%s: median: %.3f MB%n", caseSpec, median(results));
            System.out.format("%s: min: %.3f MB%n", caseSpec, results.get(0));
            System.out.format("%s: max: %.3f MB%n", caseSpec, results.get(results.size() - 1));
            System.out.format("%s: average: %.3f MB%n", caseSpec, average(results));
        }
    }

    static double getHeapSize() {
        sleep();
        System.gc();
        sleep();
        final Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1000000.0;
    }

    static void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static double median(List<Double> xs) {
        final int size = xs.size();
        if (size % 2 == 0) {
            return (xs.get(size / 2) + xs.get(size / 2 - 1)) / 2.0;
        } else {
            return xs.get(size / 2);
        }
    }

    private static double average(List<Double> xs) {
        double result = 0.0;
        for (double x : xs) {
            result += x;
        }
        return result / xs.size();
    }
}
