package com.oracle.truffle.espresso.descriptors;

final class SymbolKey {
    private final int hash;
    // mutable
    protected ByteSequence seq;

    SymbolKey(ByteSequence seq) {
        this.seq = seq;
        this.hash = seq.hashCode;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        // Always a SymbolKey.
        SymbolKey that = (SymbolKey) other;
        ByteSequence thisSeq = seq;
        ByteSequence thatSeq = that.seq;
        if (thisSeq == thatSeq) {
            return true;
        }
        if (thisSeq.hashCode != thatSeq.hashCode) {
            return false;
        }
        int len = thisSeq.length();
        if (len != thatSeq.length()) {
            return false;
        }
        byte[] thisBytes = thisSeq.getUnderlyingBytes();
        byte[] thatBytes = thatSeq.getUnderlyingBytes();
        int thisOffset = thisSeq.offset();
        int thatOffset = thatSeq.offset();
        for (int i = 0; i < len; i++) {
            if (thisBytes[i + thisOffset] != thatBytes[i + thatOffset]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public final int hashCode() {
        return hash;
    }
}
