package org.graalvm.bisect.core;

import org.graalvm.bisect.core.optimization.Optimization;

import java.util.ArrayList;
import java.util.List;

public class ExecutedMethodBuilder {
    public void setCompilationId(String compilationId) {
        this.compilationId = compilationId;
    }

    public void setCompilationMethodName(String compilationMethodName) {
        this.compilationMethodName = compilationMethodName;
    }

    public void setShare(double share) {
        this.share = share;
    }

    public String getCompilationId() {
        return compilationId;
    }

    private String compilationId;
    private String compilationMethodName;
    private Double share;
    private final List<Optimization> optimizations = new ArrayList<>();

    public boolean isHot() {
        return isHot;
    }

    public void setHot(boolean hot) {
        isHot = hot;
    }

    private boolean isHot = false;

    public ExecutedMethod build() {
        assert compilationId != null;
        assert compilationMethodName != null;
        assert share != null;
        assert isHot;
        return new ExecutedMethodImpl(compilationId, compilationMethodName, optimizations, share);
    }

    public void addOptimization(Optimization optimization) {
        optimizations.add(optimization);
    }
}
