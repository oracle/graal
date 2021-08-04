/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMGetStackFromThreadNode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

/**
 * Wrapper object for LLVMFunctionDescriptor that is used when functions are passed to the NFI. This
 * is used because arguments have to be handled slightly differently in that case.
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class LLVMNativeWrapper implements TruffleObject {

    final LLVMFunctionCode code;

    public LLVMNativeWrapper(LLVMFunctionCode code) {
        assert code.isLLVMIRFunction() || code.isIntrinsicFunctionSlowPath();
        this.code = code;
    }

    @Override
    public String toString() {
        return code.getLLVMFunction().toString();
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    static final class Execute {

        @Specialization(limit = "1", guards = "wrapper == callbackHelper.wrapper")
        static Object doExecute(LLVMNativeWrapper wrapper, Object[] args,
                        @Cached("create(wrapper)") CallbackHelperNode callbackHelper) {
            assert wrapper == callbackHelper.wrapper;
            return callbackHelper.execute(args);
        }

        /**
         * This should never happen. This code is only called from the NFI, and we explicitly
         * instruct the NFI to create a separate CallTarget for every distinct LLVMNativeWrapper
         * object, so we should never see uncached calls or multiple instances at one call site.
         *
         * @param wrapper
         * @param args
         */
        @Specialization(replaces = "doExecute")
        static Object doError(LLVMNativeWrapper wrapper, Object[] args) {
            throw CompilerDirectives.shouldNotReachHere("unexpected generic case in LLVMNativeWrapper");
        }
    }

    @ImportStatic(LLVMLanguage.class)
    abstract static class CallbackHelperNode extends LLVMNode {

        final LLVMNativeWrapper wrapper;

        CallbackHelperNode(LLVMNativeWrapper wrapper) {
            this.wrapper = wrapper;
        }

        abstract Object execute(Object[] args);

        @Specialization
        Object doCached(Object[] args,
                        @Cached LLVMGetStackFromThreadNode getStack,
                        @Cached("createCallNode()") DirectCallNode call,
                        @Cached("createFromNativeNodes()") LLVMNativeConvertNode[] convertArgs,
                        @Cached("createToNative(wrapper.code.getLLVMFunction().getType().getReturnType())") LLVMNativeConvertNode convertRet) {
            LLVMStack stack = getStack.executeWithTarget(getContext().getThreadingStack(), Thread.currentThread());
            Object[] preparedArgs = prepareCallbackArguments(stack, args, convertArgs);
            Object ret = call.call(preparedArgs);
            return convertRet.executeConvert(ret);
        }

        DirectCallNode createCallNode() {
            LLVMFunctionCode functionCode = wrapper.code;
            CallTarget callTarget;
            if (functionCode.isLLVMIRFunction()) {
                callTarget = functionCode.getLLVMIRFunctionSlowPath();
            } else if (functionCode.isIntrinsicFunctionSlowPath()) {
                callTarget = functionCode.getIntrinsicSlowPath().cachedCallTarget(functionCode.getLLVMFunction().getType());
            } else {
                throw new IllegalStateException("unexpected function: " + functionCode.getLLVMFunction().toString());
            }
            return DirectCallNode.create(callTarget);
        }

        protected LLVMNativeConvertNode[] createFromNativeNodes() {
            FunctionType type = wrapper.code.getLLVMFunction().getType();
            LLVMNativeConvertNode[] ret = new LLVMNativeConvertNode[type.getNumberOfArguments()];
            for (int i = 0; i < type.getNumberOfArguments(); i++) {
                ret[i] = LLVMNativeConvertNode.createFromNative(type.getArgumentType(i));
            }
            return ret;
        }

        @ExplodeLoop
        private static Object[] prepareCallbackArguments(LLVMStack stack, Object[] arguments, LLVMNativeConvertNode[] fromNative) {
            Object[] callbackArgs = new Object[fromNative.length + 1];
            callbackArgs[0] = stack;
            for (int i = 0; i < fromNative.length; i++) {
                callbackArgs[i + 1] = fromNative[i].executeConvert(arguments[i]);
            }
            return callbackArgs;
        }
    }
}
