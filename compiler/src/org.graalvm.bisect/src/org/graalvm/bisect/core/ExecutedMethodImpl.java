package org.graalvm.bisect.core;

import java.util.List;

import org.graalvm.bisect.core.optimization.Optimization;

public class ExecutedMethodImpl implements ExecutedMethod {
    private final String compilationId;
    private final String compilationMethodName;
    private final double share;
    private final List<Optimization> optimizations;

    public ExecutedMethodImpl(String compilationId,
                              String compilationMethodName,
                              List<Optimization> optimizations,
                              double share) {
        this.compilationId = compilationId;
        this.compilationMethodName = compilationMethodName;
        this.share = share;
        this.optimizations = optimizations;
    }

    @Override
    public String getCompilationId() {
        return compilationId;
    }

    @Override
    public String getCompilationMethodName() {
        return compilationMethodName;
    }

    @Override
    public List<Optimization> getOptimizations() {
        return optimizations;
    }

    @Override
    public double getShare() {
        return share;
    }
}
