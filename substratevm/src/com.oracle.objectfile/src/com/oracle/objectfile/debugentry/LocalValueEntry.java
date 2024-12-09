package com.oracle.objectfile.debugentry;

public abstract class LocalValueEntry {
    private final LocalEntry local;

    protected LocalValueEntry(LocalEntry local) {
        this.local = local;
    }

    public LocalEntry getLocal() {
        return local;
    }
}
