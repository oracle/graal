package com.oracle.graal.pointsto.reports.causality.events;

import org.graalvm.nativeimage.hosted.Feature;

import java.util.function.BiConsumer;

public final class SubtypeReachableNotificationCallback extends CausalityEvent {
    public final BiConsumer<Feature.DuringAnalysisAccess, Class<?>> callback;

    SubtypeReachableNotificationCallback(BiConsumer<Feature.DuringAnalysisAccess, Class<?>> callback) {
        this.callback = callback;
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public String toString() {
        return callback + " [Subtype Reachable Callback]";
    }
}
