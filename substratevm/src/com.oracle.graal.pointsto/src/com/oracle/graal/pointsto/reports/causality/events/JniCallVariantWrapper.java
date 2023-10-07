package com.oracle.graal.pointsto.reports.causality.events;

import jdk.vm.ci.meta.Signature;

public final class JniCallVariantWrapper extends CausalityEvent {
    public final Signature signature;
    public final boolean virtual;

    JniCallVariantWrapper(Signature signature, boolean virtual) {
        this.signature = signature;
        this.virtual = virtual;
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public String toString() {
        return signature + (virtual ? " [Virtual JNI Call Variant Wrapper]" : " [JNI Call Variant Wrapper]");
    }
}
