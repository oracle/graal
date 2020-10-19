package org.graalvm.launcher.polybench;

public class Config {
    public int warmupIterations;
    public int iterations;

    public Config() {
        this.warmupIterations = 1;
        this.iterations = 1;
    }
}
