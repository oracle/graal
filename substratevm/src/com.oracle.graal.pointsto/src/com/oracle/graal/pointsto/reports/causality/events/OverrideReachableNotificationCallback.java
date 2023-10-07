package com.oracle.graal.pointsto.reports.causality.events;

import org.graalvm.nativeimage.hosted.Feature;

import java.lang.reflect.Executable;
import java.util.function.BiConsumer;

public final class OverrideReachableNotificationCallback extends CausalityEvent {
    public final BiConsumer<Feature.DuringAnalysisAccess, Executable> callback;

    OverrideReachableNotificationCallback(BiConsumer<Feature.DuringAnalysisAccess, Executable> callback) {
        this.callback = callback;
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public String toString() {
        return callback + " [Method Override Reachable Callback]";
    }
}
