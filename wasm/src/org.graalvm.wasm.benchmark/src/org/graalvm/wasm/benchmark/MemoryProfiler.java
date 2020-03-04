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
