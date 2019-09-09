package com.oracle.truffle.llvm.runtime.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;

import java.util.concurrent.ConcurrentHashMap;

public class UtilCConstants {
    private final ConcurrentHashMap<CConstant, Integer> valueMap;
    private final LLVMContext ctx;

    public UtilCConstants(LLVMContext ctx) {
        this.ctx = ctx;
        this.valueMap = new ConcurrentHashMap<>();
    }

    public enum CConstant {
        EBUSY("EBUSY"),
        EDEADLK("EDEADLK"),
        EINVAL("EINVAL"),
        EPERM("EPERM"),
        PTHREAD_MUTEX_DEFAULT("PTHREAD_MUTEX_DEFAULT"),
        PTHREAD_MUTEX_ERRORCHECK("PTHREAD_MUTEX_ERRORCHECK"),
        PTHREAD_MUTEX_NORMAL("PTHREAD_MUTEX_NORMAL"),
        PTHREAD_MUTEX_RECURSIVE("PTHREAD_MUTEX_RECURSIVE");

        public final String value;

        CConstant(String val) {
            this.value = val;
        }
    }

    public int getConstant(CConstant constant) {
        if (valueMap.containsKey(constant)) {
            return valueMap.get(constant);
        }
        int value;
        RootCallTarget callTarget = ctx.getGlobalScope().getFunction("@__sulong_get" + constant.value).getLLVMIRFunction();
        try (LLVMStack.StackPointer sp =  ctx.getThreadingStack().getStack().newFrame()) {
            value = (int) callTarget.call(sp);
        }
        valueMap.put(constant, value);
        return value;
    }
}