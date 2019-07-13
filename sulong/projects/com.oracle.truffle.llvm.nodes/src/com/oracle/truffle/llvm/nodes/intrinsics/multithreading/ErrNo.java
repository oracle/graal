package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;

public class ErrNo {
    private static LLVMContext ctx;
    private static int EBUSY;
    private static boolean ebusyLoaded = false;
    private static int EINVAL;
    private static boolean einvalLoaded = false;


    static int getEbusy() {
        if (ebusyLoaded) {
            return EBUSY;
        }
        if (ctx == null) {
            ctx = LLVMLanguage.getLLVMContextReference().get();
        }
        RootCallTarget callTarget = ctx.getGlobalScope().getFunction("@__sulong_getEbusy").getLLVMIRFunction();
        LLVMStack.StackPointer sp1 =  ctx.getThreadingStack().getStack().newFrame();
        EBUSY = (int) callTarget.call(sp1);
        ebusyLoaded = true;
        return EBUSY;
    }

    static int getEinval() {
        if (einvalLoaded) {
            return EINVAL;
        }
        if (ctx == null) {
            ctx = LLVMLanguage.getLLVMContextReference().get();
        }
        RootCallTarget callTarget = ctx.getGlobalScope().getFunction("@__sulong_getEinval").getLLVMIRFunction();
        LLVMStack.StackPointer sp1 =  ctx.getThreadingStack().getStack().newFrame();
        EINVAL = (int) callTarget.call(sp1);
        einvalLoaded = true;
        return EINVAL;
    }
}
