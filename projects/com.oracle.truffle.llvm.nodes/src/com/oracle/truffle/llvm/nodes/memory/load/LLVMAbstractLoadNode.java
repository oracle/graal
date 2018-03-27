package com.oracle.truffle.llvm.nodes.memory.load;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;

abstract class LLVMAbstractLoadNode extends LLVMLoadNode {

    @CompilationFinal private LLVMMemory llvmMemory;
    @Child private LLVMDerefHandleGetReceiverNode derefHandleGetReceiverNode;
    @Child private LLVMForeignReadNode foreignReadNode;

    private Assumption getNoDerefHandleAssumption() {
        return getLLVMMemoryCached().getNoDerefHandleAssumption();
    }

    protected LLVMDerefHandleGetReceiverNode getDerefHandleGetReceiverNode() {
        if (derefHandleGetReceiverNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            derefHandleGetReceiverNode = insert(LLVMDerefHandleGetReceiverNode.create(LLVMMemory.getObjectMask()));
        }
        return derefHandleGetReceiverNode;
    }

    protected LLVMForeignReadNode getForeignReadNode() {
        if (foreignReadNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            foreignReadNode = insert(createForeignRead());
        }
        return foreignReadNode;
    }

    protected boolean isAutoDerefHandle(LLVMAddress addr) {
        return !getNoDerefHandleAssumption().isValid() && LLVMMemory.isDerefMemory(addr);
    }

    LLVMForeignReadNode createForeignRead() {
        throw new AssertionError("should not reach here");
    }

    protected LLVMMemory getLLVMMemoryCached() {
        if (llvmMemory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            llvmMemory = getLLVMMemory();
        }
        return llvmMemory;
    }

}
