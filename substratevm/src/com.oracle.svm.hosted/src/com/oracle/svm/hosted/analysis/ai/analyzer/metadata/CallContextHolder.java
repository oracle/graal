package com.oracle.svm.hosted.analysis.ai.analyzer.metadata;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Thread-local holder for the current CallContext so components that do not
 * receive it explicitly (e.g., interpreters) can query a compact context signature
 * to implement context-sensitive behaviors (like allocation-site sensitivity).
 * <p>
 * This is a pragmatic bridge until IteratorContext is extended to carry CallContext.
 */
public final class CallContextHolder {
    private static final ThreadLocal<CallContext<? extends AbstractDomain<?>>> CTX = new ThreadLocal<>();

    private CallContextHolder() {
    }

    public static void set(CallContext<? extends AbstractDomain<?>> ctx) {
        CTX.set(ctx);
    }

    public static void clear() {
        CTX.remove();
    }

    public static CallContext<? extends AbstractDomain<?>> get() {
        return CTX.get();
    }

    public static String getSignatureOrEmpty() {
        CallContext<? extends AbstractDomain<?>> ctx = CTX.get();
        return ctx == null ? "" : (ctx.contextSignature() == null ? "" : ctx.contextSignature());
    }

    public static String buildKCFASignature(java.util.Deque<AnalysisMethod> stack, int k) {
        return CallContext.buildKCFASignature(stack, k);
    }
}

