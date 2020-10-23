package org.graalvm.launcher.polybench;

import java.util.Optional;

public class PeakTimeMetric implements Metric {
    long startTime;
    long endTime;
    long totalTime;
    int totalIterations;

    public PeakTimeMetric() {
        this.totalTime = 0L;
    }

    @Override
    public void beforeIteration(boolean warmup, int iteration, Config config) {
        startTime = System.nanoTime();
    }

    @Override
    public void afterIteration(boolean warmup, int iteration, Config config) {
        endTime = System.nanoTime();

        totalTime += endTime - startTime;
        totalIterations++;
    }

    @Override
    public void reset() {
        startTime = 0L;
        endTime = 0L;
        totalTime = 0L;
        totalIterations = 0;
    }

    @Override
    public Optional<Double> reportAfterIteration(Config config) {
        return Optional.of((endTime - startTime) / 1_000_000.0);
    }

    @Override
    public Optional<Double> reportAfterAll() {
        return Optional.of(1.0 * totalTime / totalIterations / 1_000_000.0);
    }

    @Override
    public String unit() {
        return "ms";
    }

    @Override
    public String name() {
        return "peak time";
    }
}
