/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.wasm.utils.cases.WasmCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.graalvm.wasm.utils.cases.WasmCase.collectFileCase;

public class MemoryProfiler {
    private static int WARMUP_ITERATIONS = 10;
    private static int ITERATIONS = 10;

    public static void main(String[] args) throws IOException, InterruptedException {
        final String resource = args[0];
        final String caseSpec = args[1];
        final WasmCase benchmarkCase = collectFileCase("bench", resource, caseSpec);
        assert benchmarkCase != null : String.format("Test case %s/%s not found.", resource, caseSpec);

        final Context.Builder contextBuilder = Context.newBuilder("wasm");
        contextBuilder.option("wasm.Builtins", "testutil,env:emscripten,memory");

        final List<Double> results = new ArrayList<>();

        for (int i = 0; i < WARMUP_ITERATIONS + ITERATIONS; ++i) {
            if (i < WARMUP_ITERATIONS) {
                System.out.println("# Warm up iteration " + i);
            } else {
                System.out.println("# Iteration " + (i - WARMUP_ITERATIONS));
            }

            final Context context = contextBuilder.build();

            final double heapSizeBefore = getHeapSize();

            // The code we want to profile:
            benchmarkCase.getSources().forEach(context::eval);

            final double heapSizeAfter = getHeapSize();
            final double result = heapSizeAfter - heapSizeBefore;
            System.out.format("%.3f  MB%n", result);
            if (i >= WARMUP_ITERATIONS) {
                results.add(heapSizeAfter - heapSizeBefore);
            }

            context.close();
        }

        Collections.sort(results);

        System.out.println("\n# Aggregation");
        System.out.format("Median:  %.3f MB%n", median(results));
        System.out.format("Min:     %.3f MB%n", Collections.min(results));
        System.out.format("Max:     %.3f MB%n", Collections.max(results));
        System.out.format("Average: %.3f MB%n", average(results));
    }

    private static double getHeapSize() throws InterruptedException {
        Thread.sleep(100);
        System.gc();
        Thread.sleep(100);
        final Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1000000.0;
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
