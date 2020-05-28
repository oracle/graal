package org.graalvm.compiler.hotspot;

public interface Instrumentation {
    CpuLocalCounterArray<Long> getPathProfilingCounters();
}
