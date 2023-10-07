package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisType;
import org.graalvm.nativeimage.hosted.Feature;

import java.util.function.BiConsumer;

public final class SubtypeReachableNotificationCallbackInvocation extends CausalityEvent {
    public final BiConsumer<Feature.DuringAnalysisAccess, Class<?>> callback;
    public final AnalysisType subtype;

    SubtypeReachableNotificationCallbackInvocation(BiConsumer<Feature.DuringAnalysisAccess, Class<?>> callback, AnalysisType subtype) {
        this.callback = callback;
        this.subtype = subtype;
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public String toString() {
        return callback + " + " + subtype.toJavaName() + " [Subtype Reachable Callback Invocation]";
    }
}
