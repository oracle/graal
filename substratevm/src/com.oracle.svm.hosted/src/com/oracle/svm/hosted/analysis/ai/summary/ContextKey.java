package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

import java.util.Objects;

public record ContextKey(AnalysisMethod method, int callStringHash, int depth) {

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ContextKey(AnalysisMethod method1, int stringHash, int depth1))) return false;
        return callStringHash == stringHash && depth == depth1 && Objects.equals(method, method1);
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
