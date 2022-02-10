package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;

public class LLVMThreadLocalSymbol extends LLVMSymbol {

    private final String name;

    public LLVMThreadLocalSymbol(String name, IDGenerater.BitcodeID bitcodeID, int symbolIndex, boolean exported, boolean externalWeak) {
        super(name, bitcodeID, symbolIndex, exported, externalWeak);
        this.name = name;
    }

    public static LLVMThreadLocalSymbol create(String symbolName, IDGenerater.BitcodeID bitcodeID, int symbolIndex, boolean exported, boolean externalWeak) {
        return new LLVMThreadLocalSymbol(symbolName, bitcodeID, symbolIndex, exported, externalWeak);
    }

    @Override
    public boolean isFunction() {
        return false;
    }

    @Override
    public boolean isGlobalVariable() {
        return true;
    }

    @Override
    public boolean isAlias() {
        return false;
    }

    @Override
    public LLVMFunction asFunction() {
        throw new IllegalStateException("Thread local global " + name + " is not a function.");
    }

    @Override
    public LLVMGlobal asGlobalVariable() {
        throw new IllegalStateException("Thread local global " + name + " is not a global.");
    }

    @Override
    public boolean isElemPtrExpression() {
        return false;
    }

    @Override
    public boolean isThreadLocalSymbol() {
        return true;
    }

    @Override
    public LLVMElemPtrSymbol asElemPtrExpression() {
        throw new IllegalStateException("Thread local global " + name + " is not a GetElementPointer symbol.");
    }

    @Override
    public LLVMThreadLocalSymbol asThreadLocalSymbol() {
        return this;
    }
}
