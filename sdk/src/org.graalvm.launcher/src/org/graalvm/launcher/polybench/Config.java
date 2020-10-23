package org.graalvm.launcher.polybench;

public class Config {
    public String path;
    public int warmupIterations;
    public int iterations;
    public String mode;
    public Metric metric;

    public Config() {
        this.path = null;
        this.warmupIterations = 10;
        this.iterations = 10;
        this.mode = "default";
        this.metric = new NoMetric();
    }

    @Override
    public String toString() {
        return "execution-mode:    " + mode + "\n" +
                "metric:            " + metric.name() + " (" + metric.unit() + ")" + "\n" +
                "warmup-iterations: " + warmupIterations + "\n" +
                "iterations:        " + iterations;
    }
}
