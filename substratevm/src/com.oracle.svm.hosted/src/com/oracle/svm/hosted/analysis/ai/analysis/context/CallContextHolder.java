package com.oracle.svm.hosted.analysis.ai.analysis.context;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Thread-local holder for the current CallContext so components that do not
 * receive it explicitly (e.g., interpreters) can query a compact context signature
 * to implement context-sensitive behaviors (like allocation-site sensitivity).
 * This is a bridge until IteratorContext is extended to carry CallContext.
 */
public final class CallContextHolder {
    private static final ThreadLocal<CallContext<? extends AbstractDomain<?>>> context = new ThreadLocal<>();

    private CallContextHolder() {
    }

    public static void set(CallContext<? extends AbstractDomain<?>> ctx) {
        context.set(ctx);
    }

    public static void clear() {
        context.remove();
    }

    public static CallContext<? extends AbstractDomain<?>> get() {
        return context.get();
    }

    public static String getSignatureOrEmpty() {
        CallContext<? extends AbstractDomain<?>> ctx = context.get();
        return ctx == null ? "" : (ctx.contextSignature() == null ? "" : ctx.contextSignature());
    }

    public static String buildKCFASignature(java.util.Deque<AnalysisMethod> stack, int k) {
        return CallContext.buildKCFASignature(stack, k);
    }
}

