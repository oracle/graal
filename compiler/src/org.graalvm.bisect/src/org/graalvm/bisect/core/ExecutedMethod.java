package org.graalvm.bisect.core;

import org.graalvm.bisect.core.optimization.Optimization;

import java.util.List;

public interface ExecutedMethod {
    String getCompilationId();
    String getCompilationMethodName();
    List<Optimization> getOptimizations();
    double getShare();
}
