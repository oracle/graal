package org.graalvm.polybench;

public class NoMetric implements Metric {
    @Override
    public String name() {
        return "no metric";
    }
}
