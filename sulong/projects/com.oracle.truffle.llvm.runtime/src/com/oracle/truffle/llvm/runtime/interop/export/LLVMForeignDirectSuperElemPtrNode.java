package com.oracle.truffle.llvm.runtime.interop.export;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMForeignDirectSuperElemPtrNode extends LLVMForeignGetSuperElemPtrNode {
    @Override
    public abstract LLVMPointer execute(LLVMPointer receiver);

    final long offset;

    LLVMForeignDirectSuperElemPtrNode(long offset) {
        this.offset = offset;
    }

    @Specialization
    public LLVMPointer doResolve(LLVMPointer receiver) {
        return receiver.increment(offset);
    }
}
