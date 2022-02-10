package com.oracle.truffle.llvm.runtime;

public class LLVMThreadLocalPointer {

    private LLVMSymbol symbol;
    private long offset;

    public LLVMThreadLocalPointer(LLVMSymbol symbol, long offset) {
        this.symbol = symbol;
        this.offset = offset;
    }

    public LLVMSymbol getSymbol() {
        return symbol;
    }

    public long getOffset() {
        return offset;
    }
}
