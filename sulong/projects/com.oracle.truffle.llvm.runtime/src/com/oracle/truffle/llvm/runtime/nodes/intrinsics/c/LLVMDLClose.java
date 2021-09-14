package com.oracle.truffle.llvm.runtime.nodes.intrinsics.c;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMDLClose extends LLVMIntrinsic {

    @Specialization(guards = "isLLVMLibrary(libraryHandle)")
    protected int doOp(@SuppressWarnings("unused") LLVMManagedPointer libraryHandle) {
        return 0;
    }

    protected boolean isLLVMLibrary(LLVMManagedPointer library) {
        return library.getObject() instanceof LLVMDLOpen.LLVMDLHandler;
    }

}
