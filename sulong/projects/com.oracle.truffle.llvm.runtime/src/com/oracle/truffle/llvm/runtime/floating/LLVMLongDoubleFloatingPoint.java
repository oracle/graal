package com.oracle.truffle.llvm.runtime.floating;

import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;

public abstract class LLVMLongDoubleFloatingPoint extends LLVMInternalTruffleObject {

    public abstract double toDoubleValue();

}
