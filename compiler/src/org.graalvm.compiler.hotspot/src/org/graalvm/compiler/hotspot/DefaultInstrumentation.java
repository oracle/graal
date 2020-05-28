package org.graalvm.compiler.hotspot;

public class DefaultInstrumentation implements Instrumentation {
    @Override
    public CpuLocalCounterArray<Long> getPathProfilingCounters() {
        return null;
    }
}
