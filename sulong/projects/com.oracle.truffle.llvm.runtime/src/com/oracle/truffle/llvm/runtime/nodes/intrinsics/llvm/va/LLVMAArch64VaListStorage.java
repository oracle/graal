package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va;

import java.util.function.Supplier;

import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class LLVMAArch64VaListStorage {
    Supplier<LLVMExpressionNode> allocaNodeFactory;

    public LLVMAArch64VaListStorage(Supplier<LLVMExpressionNode> allocaNodeFactory) {
        this.allocaNodeFactory = allocaNodeFactory;
    }
}
