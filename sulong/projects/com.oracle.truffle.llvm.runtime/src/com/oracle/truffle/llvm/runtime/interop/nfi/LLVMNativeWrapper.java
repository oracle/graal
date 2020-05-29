/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.interop.nfi;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMGetStackNode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

/**
 * Wrapper object for LLVMFunctionDescriptor that is used when functions are passed to the NFI. This
 * is used because arguments have to be handled slightly differently in that case.
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class LLVMNativeWrapper implements TruffleObject {

    private final LLVMFunctionDescriptor descriptor;

    public LLVMNativeWrapper(LLVMFunctionDescriptor descriptor) {
        assert descriptor.getFunctionCode().isLLVMIRFunction() || descriptor.getFunctionCode().isIntrinsicFunctionSlowPath();
        this.descriptor = descriptor;
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof LLVMNativeWrapper;
    }

    @Override
    public String toString() {
        return descriptor.toString();
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object execute(Object[] args,
                    @Cached CallbackHelperNode callbackHelper) {
        return callbackHelper.execute(descriptor, args);
    }

    @GenerateUncached
    @ImportStatic(LLVMLanguage.class)
    abstract static class CallbackHelperNode extends LLVMNode {

        abstract Object execute(LLVMFunctionDescriptor function, Object[] args);

        @Specialization(guards = "function == cachedFunction")
        Object doCached(@SuppressWarnings("unused") LLVMFunctionDescriptor function, Object[] args,
                        @Cached("function") @SuppressWarnings("unused") LLVMFunctionDescriptor cachedFunction,
                        @Cached LLVMGetStackNode getStack,
                        @CachedContext(LLVMLanguage.class) ContextReference<LLVMContext> ctxRef,
                        @Cached("createCallNode(cachedFunction)") DirectCallNode call,
                        @Cached("createFromNativeNodes(cachedFunction.getLLVMFunction().getType())") LLVMNativeConvertNode[] convertArgs,
                        @Cached("createToNative(cachedFunction.getLLVMFunction().getType().getReturnType())") LLVMNativeConvertNode convertRet) {
            try (StackPointer stackPointer = newStackFrame(getStack, ctxRef)) {
                Object[] preparedArgs = prepareCallbackArguments(stackPointer, args, convertArgs);
                Object ret = call.call(preparedArgs);
                return convertRet.executeConvert(ret);
            }
        }

        @Specialization(replaces = "doCached")
        @SuppressWarnings("unused")
        Object doGeneric(LLVMFunctionDescriptor function, Object[] args) {
            /*
             * This should never happen. This node is only called from the NFI, and the NFI creates
             * a separate CallTarget for every distinct callback object, so we should never see more
             * than one distinct LLVMFunctionDescriptor.
             */
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("unexpected generic case in LLVMNativeCallback");
        }

        DirectCallNode createCallNode(LLVMFunctionDescriptor function) {
            CallTarget callTarget;
            LLVMFunctionCode functionCode = function.getFunctionCode();
            if (functionCode.isLLVMIRFunction()) {
                callTarget = functionCode.getLLVMIRFunctionSlowPath();
            } else if (functionCode.isIntrinsicFunctionSlowPath()) {
                callTarget = functionCode.getIntrinsicSlowPath().cachedCallTarget(function.getLLVMFunction().getType());
            } else {
                throw new IllegalStateException("unexpected function: " + function.toString());
            }
            return DirectCallNode.create(callTarget);
        }

        private StackPointer newStackFrame(LLVMGetStackNode getStack, ContextReference<LLVMContext> ctxRef) {
            LLVMThreadingStack threadingStack = ctxRef.get().getThreadingStack();
            return getStack.executeWithTarget(threadingStack, Thread.currentThread()).newFrame();
        }

        protected static LLVMNativeConvertNode[] createFromNativeNodes(FunctionType type) {
            LLVMNativeConvertNode[] ret = new LLVMNativeConvertNode[type.getNumberOfArguments()];
            for (int i = 0; i < type.getNumberOfArguments(); i++) {
                ret[i] = LLVMNativeConvertNode.createFromNative(type.getArgumentType(i));
            }
            return ret;
        }

        @ExplodeLoop
        private static Object[] prepareCallbackArguments(StackPointer stackPointer, Object[] arguments, LLVMNativeConvertNode[] fromNative) {
            Object[] callbackArgs = new Object[fromNative.length + 1];
            callbackArgs[0] = stackPointer;
            for (int i = 0; i < fromNative.length; i++) {
                callbackArgs[i + 1] = fromNative[i].executeConvert(arguments[i]);
            }
            return callbackArgs;
        }
    }
}
