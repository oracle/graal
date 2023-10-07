package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import org.graalvm.nativeimage.hosted.Feature;

import java.lang.reflect.Executable;
import java.util.function.BiConsumer;

public final class OverrideReachableNotificationCallbackInvocation extends CausalityEvent {
    public final BiConsumer<Feature.DuringAnalysisAccess, Executable> callback;
    public final AnalysisMethod override;

    OverrideReachableNotificationCallbackInvocation(BiConsumer<Feature.DuringAnalysisAccess, Executable> callback, AnalysisMethod override) {
        this.callback = callback;
        this.override = override;
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public String toString() {
        return callback + " + " + override.format("%H.%n(%P):%R") + " [Method Override Reachable Callback Invocation]";
    }
}
