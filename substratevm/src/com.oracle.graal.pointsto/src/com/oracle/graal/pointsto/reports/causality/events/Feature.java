package com.oracle.graal.pointsto.reports.causality.events;

public final class Feature extends CausalityEvent {
    public final org.graalvm.nativeimage.hosted.Feature f;

    Feature(org.graalvm.nativeimage.hosted.Feature f) {
        this.f = f;
    }

    @Override
    public String toString() {
        String str = f.getClass().getTypeName();
        String description = f.getDescription();
        if (description != null)
            str += " [Feature: " + description + "]";
        else
            str += " [Feature]";
        return str;
    }
}
