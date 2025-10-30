package com.oracle.svm.hosted.analysis.ai.analyzer.metadata;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Per-invocation context passed along call edges.
 * Contains a bounded call stack and/or a derived signature, and optionally actual argument abstract values.
 *
 * @param callStack        representation by analysis methods
 * @param contextSignature optional precomputed signature (e.g., k-CFA)
 * @param actualArgs       optional
 */
public record CallContext<Domain extends AbstractDomain<Domain>>(Deque<AnalysisMethod> callStack,
                                                                 String contextSignature, List<Domain> actualArgs) {
    public CallContext(Deque<AnalysisMethod> callStack, String contextSignature, List<Domain> actualArgs) {
        this.callStack = new ArrayDeque<>(Objects.requireNonNull(callStack));
        this.contextSignature = contextSignature;
        this.actualArgs = (actualArgs == null) ? List.of() : new ArrayList<>(actualArgs);
    }

    @Override
    public Deque<AnalysisMethod> callStack() {
        return new ArrayDeque<>(callStack);
    }

    /**
     * Build a compact k-CFA signature from the tail of the call stack (k last methods).
     */
    public static String buildKCFASignature(Deque<AnalysisMethod> stack, int k) {
        if (stack == null || stack.isEmpty() || k <= 0) return "";
        List<AnalysisMethod> list = new ArrayList<>(stack);
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, list.size() - k);
        for (int i = start; i < list.size(); i++) {
            AnalysisMethod m = list.get(i);
            sb.append(m.getQualifiedName());
            if (i < list.size() - 1) sb.append(" -> ");
        }
        return sb.toString();
    }
}
