package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;

public class ErrNo {
    private static LLVMContext ctx;

    static int getEbusy() {
        if (ctx == null) {
            ctx = LLVMLanguage.getLLVMContextReference().get();
        }
        RootCallTarget callTarget = ctx.getGlobalScope().getFunction("@__sulong_getEbusy").getLLVMIRFunction();
        LLVMStack.StackPointer sp1 =  ctx.getThreadingStack().getStack().newFrame();
        return (int) callTarget.call(sp1);
    }

    static int getEinval() {
        if (ctx == null) {
            ctx = LLVMLanguage.getLLVMContextReference().get();
        }
        RootCallTarget callTarget = ctx.getGlobalScope().getFunction("@__sulong_getEinval").getLLVMIRFunction();
        LLVMStack.StackPointer sp1 =  ctx.getThreadingStack().getStack().newFrame();
        return (int) callTarget.call(sp1);
    }
}
