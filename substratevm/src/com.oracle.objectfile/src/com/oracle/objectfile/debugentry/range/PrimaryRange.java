package com.oracle.objectfile.debugentry.range;

import java.util.HashMap;

import com.oracle.objectfile.debugentry.MethodEntry;

public class PrimaryRange extends CallRange {
    private final long codeOffset;

    protected PrimaryRange(MethodEntry methodEntry, int lo, int hi, int line, long codeOffset) {
        super(null, methodEntry, new HashMap<>(), lo, hi, line, null, -1);
        this.codeOffset = codeOffset;
    }

    @Override
    public long getCodeOffset() {
        return codeOffset;
    }

    @Override
    public boolean isPrimary() {
        return true;
    }

    @Override
    public PrimaryRange getPrimary() {
        return this;
    }
}
