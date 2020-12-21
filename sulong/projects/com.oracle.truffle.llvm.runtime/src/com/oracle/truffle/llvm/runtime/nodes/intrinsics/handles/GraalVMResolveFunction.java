package com.oracle.truffle.llvm.runtime.nodes.intrinsics.handles;

import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class GraalVMResolveFunction extends LLVMIntrinsic {

    @Specialization
    protected Object doNativeResolve(LLVMNativePointer pointer,
                    @CachedContext(LLVMLanguage.class) LLVMContext context) {
        return context.getFunctionDescriptor(pointer);
    }

    @Specialization(guards = "isFunctionDescriptor(pointer)")
    protected Object doManagedResolve(LLVMManagedPointer pointer) {
        return pointer.getObject();
    }

    @Fallback
    protected Object doError(Object pointer) {
        throw new LLVMPolyglotException(this, "Cannot resolve pointer %s to a function.", pointer);
    }

    protected boolean isFunctionDescriptor(LLVMManagedPointer pointer) {
        return pointer.getObject() instanceof LLVMFunctionDescriptor;
    }
}
