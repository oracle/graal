package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

import java.util.Objects;

public final class ContextKey {

    private final AnalysisMethod method;
    private final int callStringHash;
    private final int depth;

    public ContextKey(AnalysisMethod method, int callStringHash, int depth) {
        this.method = method;
        this.callStringHash = callStringHash;
        this.depth = depth;
    }

    public AnalysisMethod getMethod() {
        return method;
    }

    public int getCallStringHash() {
        return callStringHash;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ContextKey that)) return false;
        return callStringHash == that.callStringHash && depth == that.depth && Objects.equals(method, that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, callStringHash, depth);
    }

    @Override
    public String toString() {
        return "ContextKey{" +
                "method=" + method +
                ", callStringHash=" + callStringHash +
                ", depth=" + depth +
                '}';
    }
}
