/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.compiler.benchmark;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ResultRole;
import org.openjdk.jmh.results.SampleTimeResult;
import org.openjdk.jmh.runner.IterationType;
import org.openjdk.jmh.util.SampleBuffer;

import com.oracle.truffle.compiler.TruffleCompilerListener;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;

/**
 * Custom profiler to report compilation timings. This profiler uses
 * {@link OptimizedTruffleRuntimeListener} callbacks to record compilation events and then reports
 * the timings to JMH through {@link #afterIteration}.
 * <p>
 * Enable this profiler on the JMH command line using "-prof [qualified class name]". The profiler
 * will emit secondary metrics with percentiles that can be parsed out of the JMH result file.
 * <p>
 * NOTE: Compilation benchmarks need at least 1 warmup iteration. JMH runs @Setup lazily before the
 * first "iteration" -- if that iteration is a measurement iteration, this profiler will collect
 * timings for compilations triggered by @Setup.
 */
public class CompilationTimingsProfiler implements InternalProfiler, OptimizedTruffleRuntimeListener {

    private Datapoint current = null;
    private final List<Datapoint> datapoints = new ArrayList<>();

    public Error error(String message, Object... args) {
        throw new AssertionError(message.formatted(args));
    }

    /**
     * Logs an error without throwing an exception. Compilation callbacks should not throw errors,
     * so we instead log them and configure mx benchmark to check for error logs.
     */
    private void logError(String message, Object... args) {
        // Checkstyle: stop
        System.err.println(CompilationTimingsProfiler.class.getSimpleName() + " error: " + message.formatted(args));
        // Checkstyle: start
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        if (iterationParams.getType() == IterationType.WARMUP) {
            return;
        }

        if (!datapoints.isEmpty()) {
            throw error("Some datapoints (probably from a previous benchmark) were not cleared before a new iteration started. This is a bug in the %s.",
                            CompilationTimingsProfiler.class.getName());
        }

        // Start listening to compilation events.
        OptimizedTruffleRuntime.getRuntime().addListener(this);
    }

    @Override
    public Collection<? extends Result<?>> afterIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams, IterationResult result) {
        if (iterationParams.getType() == IterationType.WARMUP) {
            return List.of();
        }

        // Stop listening to compilation events.
        OptimizedTruffleRuntime.getRuntime().removeListener(this);

        if (current != null) {
            throw error("Incomplete compilation datapoint found. Was there a compilation issue?");
        }
        if (datapoints.isEmpty()) {
            throw error("No compilation data points were collected.");
        }

        SampleBuffer truffleTierBuffer = new SampleBuffer();
        SampleBuffer graalTierBuffer = new SampleBuffer();
        SampleBuffer afterGraalTierBuffer = new SampleBuffer();
        for (Datapoint datapoint : datapoints) {
            truffleTierBuffer.add(datapoint.timeInTruffleTierNs());
            graalTierBuffer.add(datapoint.timeInGraalTierNs());
            afterGraalTierBuffer.add(datapoint.timeAfterGraalTierNs());
        }
        datapoints.clear();

        return List.of(
                        new SampleTimeResult(ResultRole.SECONDARY, "pe-time", truffleTierBuffer, TimeUnit.MILLISECONDS),
                        new SampleTimeResult(ResultRole.SECONDARY, "compile-time", graalTierBuffer, TimeUnit.MILLISECONDS),
                        new SampleTimeResult(ResultRole.SECONDARY, "code-install-time", afterGraalTierBuffer, TimeUnit.MILLISECONDS));
    }

    @Override
    public String getDescription() {
        return "Compilation timings profiler using Truffle runtime compilation callbacks.";
    }

    @Override
    public void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
        if (current != null) {
            logError("Compilation started before previous compilation completed. Data will be lost.");
        }
        current = new Datapoint(System.nanoTime());
    }

    @Override
    public void onCompilationTruffleTierFinished(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph) {
        if (current == null) {
            logError("Truffle tier was reported finished, but no compilation was started in the profiler.");
            return;
        } else if (current.truffleTierCompleteNs != 0) {
            logError("Truffle tier timing already recorded.");
        }
        current.truffleTierCompleteNs = System.nanoTime();
    }

    @Override
    public void onCompilationGraalTierFinished(OptimizedCallTarget target, TruffleCompilerListener.GraphInfo graph) {
        if (current == null) {
            logError("Graal tier was reported finished, but no compilation was started in the profiler.");
            return;
        } else if (current.graalTierCompleteNs != 0) {
            logError("Graal tier timing already recorded.");
        }
        current.graalTierCompleteNs = System.nanoTime();
    }

    @Override
    public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, TruffleCompilerListener.GraphInfo graph, TruffleCompilerListener.CompilationResultInfo result) {
        if (current == null) {
            logError("Compilation was reported successful, but no compilation was started in the profiler.");
            return;
        } else if (current.compilationCompleteNs != 0) {
            logError("Compilation timing already recorded.");
        }
        current.compilationCompleteNs = System.nanoTime();
        if (!current.hasAllTimings()) {
            logError("Incomplete data point; some timings were missing: %s", current);
        }
        datapoints.add(current);
        current = null;
    }

    @Override
    public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
        logError("Compilation failed unexpectedly: %s", reason);
        current = null;
    }

    @Override
    public void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason, int tier) {
        logError("Compilation dequeued unexpectedly: %s", reason);
        current = null;
    }

    /**
     * Timings for the current compilation.
     */
    private static class Datapoint {
        long startNs;
        long truffleTierCompleteNs;
        long graalTierCompleteNs;
        long compilationCompleteNs;

        Datapoint(long startNs) {
            this.startNs = startNs;
        }

        boolean hasAllTimings() {
            return startNs != 0 && truffleTierCompleteNs != 0 && graalTierCompleteNs != 0 && compilationCompleteNs != 0;
        }

        long timeInTruffleTierNs() {
            return truffleTierCompleteNs - startNs;
        }

        long timeInGraalTierNs() {
            return graalTierCompleteNs - truffleTierCompleteNs;
        }

        long timeAfterGraalTierNs() {
            return compilationCompleteNs - graalTierCompleteNs;
        }

        @Override
        public String toString() {
            return String.format("Datapoint(start=%s, truffleTierComplete=%s, graalTierComplete=%s, compilationComplete=%s)", startNs, truffleTierCompleteNs, graalTierCompleteNs,
                            compilationCompleteNs);
        }
    }
}
