package com.oracle.truffle.llvm.runtime.interop.export;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMForeignDirectSuperElemPtrNode extends LLVMForeignGetSuperElemPtrNode {
    @Override
    public abstract LLVMPointer execute(LLVMPointer receiver, long offset);

    @Specialization
    public LLVMPointer doResolve(LLVMPointer receiver, long offset) {
        return receiver.increment(offset);
    }
}
