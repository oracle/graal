package com.oracle.truffle.llvm.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;

public class ErrNo {
    private static LLVMContext ctx;
    private static int EBUSY;
    private static boolean loadedEBUSY = false;
    private static int EINVAL;
    private static boolean loadedEINVAL = false;
    private static int EDEADLK;
    private static boolean loadedEDEADLK = false;
    private static int EPERM;
    private static boolean loadedEPERM = false;

    static int getEBUSY() {
        if (loadedEBUSY) {
            return EBUSY;
        }
        if (ctx == null) {
            ctx = LLVMLanguage.getLLVMContextReference().get();
        }
        RootCallTarget callTarget = ctx.getGlobalScope().getFunction("@__sulong_getEBUSY").getLLVMIRFunction();
        LLVMStack.StackPointer sp1 =  ctx.getThreadingStack().getStack().newFrame();
        EBUSY = (int) callTarget.call(sp1);
        loadedEBUSY = true;
        return EBUSY;
    }

    static int getEINVAL() {
        if (loadedEINVAL) {
            return EINVAL;
        }
        if (ctx == null) {
            ctx = LLVMLanguage.getLLVMContextReference().get();
        }
        RootCallTarget callTarget = ctx.getGlobalScope().getFunction("@__sulong_getEINVAL").getLLVMIRFunction();
        LLVMStack.StackPointer sp1 =  ctx.getThreadingStack().getStack().newFrame();
        EINVAL = (int) callTarget.call(sp1);
        loadedEINVAL = true;
        return EINVAL;
    }

    static int getEDEADLK() {
        if (loadedEDEADLK) {
            return EDEADLK;
        }
        if (ctx == null) {
            ctx = LLVMLanguage.getLLVMContextReference().get();
        }
        RootCallTarget callTarget = ctx.getGlobalScope().getFunction("@__sulong_getEDEADLK").getLLVMIRFunction();
        LLVMStack.StackPointer sp1 =  ctx.getThreadingStack().getStack().newFrame();
        EDEADLK = (int) callTarget.call(sp1);
        loadedEDEADLK = true;
        return EDEADLK;
    }

    static int getEPERM() {
        if (loadedEPERM) {
            return EPERM;
        }
        if (ctx == null) {
            ctx = LLVMLanguage.getLLVMContextReference().get();
        }
        RootCallTarget callTarget = ctx.getGlobalScope().getFunction("@__sulong_getEPERM").getLLVMIRFunction();
        LLVMStack.StackPointer sp1 =  ctx.getThreadingStack().getStack().newFrame();
        EPERM = (int) callTarget.call(sp1);
        loadedEPERM = true;
        return EPERM;
    }
}
