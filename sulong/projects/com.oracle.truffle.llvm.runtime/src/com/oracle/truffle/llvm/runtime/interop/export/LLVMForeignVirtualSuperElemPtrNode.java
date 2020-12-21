package com.oracle.truffle.llvm.runtime.interop.export;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMForeignVirtualSuperElemPtrNode extends LLVMForeignGetSuperElemPtrNode {
    @Override
    public abstract LLVMPointer execute(LLVMPointer receiver, long offset);

    @Specialization
    public LLVMPointer doResolve(LLVMPointer receiver, long offset,
                    @Cached LLVMForeignReadNode read) {
        Object vtablePointer = read.execute(receiver, LLVMInteropType.ValueKind.POINTER.type);
        LLVMPointer parent = LLVMPointer.cast(vtablePointer).increment(-offset);
        Object parentOffset = read.execute(parent, LLVMInteropType.ValueKind.I64.type);
        return receiver.increment((long) parentOffset);
    }
}
