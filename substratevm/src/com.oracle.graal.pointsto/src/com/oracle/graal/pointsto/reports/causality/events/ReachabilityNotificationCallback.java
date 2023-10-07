package com.oracle.graal.pointsto.reports.causality.events;

import org.graalvm.nativeimage.hosted.Feature;

import java.util.function.Consumer;

public final class ReachabilityNotificationCallback extends CausalityEvent {
    public final Consumer<Feature.DuringAnalysisAccess> callback;

    ReachabilityNotificationCallback(Consumer<Feature.DuringAnalysisAccess> callback) {
        this.callback = callback;
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public String toString() {
        return callback + " [Reachability Callback]";
    }
}
