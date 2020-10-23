package org.graalvm.launcher.polybench;

import java.util.Optional;

@SuppressWarnings("unused")
public interface Metric {
    default void beforeIteration(boolean warmup, int iteration, Config config) {
    }

    default void afterIteration(boolean warmup, int iteration, Config config) {
    }

    default Optional<Double> reportAfterIteration(Config config) {
        return Optional.empty();
    }

    default Optional<Double> reportAfterAll() {
        return Optional.empty();
    }

    default void reset() {
    }

    default String unit() {
        return "";
    }

    String name();
}
