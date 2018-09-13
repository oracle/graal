/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMIRFunction;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMTruffleDecorateFunction extends LLVMIntrinsic {

    protected static class DecoratedRoot extends RootNode {
        @Child private DirectCallNode funcCallNode;
        @Child private DirectCallNode wrapperCallNode;

        protected DecoratedRoot(TruffleLanguage<?> language, LLVMFunctionDescriptor func, LLVMFunctionDescriptor wrapper) {
            super(language);
            this.funcCallNode = Truffle.getRuntime().createDirectCallNode(func.getLLVMIRFunction());
            this.wrapperCallNode = Truffle.getRuntime().createDirectCallNode(wrapper.getLLVMIRFunction());
            this.funcCallNode.cloneCallTarget();
            this.wrapperCallNode.cloneCallTarget();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            Object result;
            try (StackPointer sp = ((StackPointer) arguments[0]).newFrame()) {
                result = funcCallNode.call(arguments);
            }
            Object[] wrapperArgs = new Object[]{arguments[0], result};
            try (StackPointer sp = ((StackPointer) arguments[0]).newFrame()) {
                return wrapperCallNode.call(wrapperArgs);
            }
        }

    }

    @Specialization
    protected Object decorate(LLVMNativePointer func, LLVMNativePointer wrapper,
                    @Cached("getContextReference()") ContextReference<LLVMContext> contextRef) {
        return decorate(contextRef.get(), contextRef.get().getFunctionDescriptor(func), contextRef.get().getFunctionDescriptor(wrapper));
    }

    @Specialization(guards = "isFunctionDescriptor(wrapper.getObject())")
    protected Object decorate(LLVMNativePointer func, LLVMManagedPointer wrapper,
                    @Cached("getContextReference()") ContextReference<LLVMContext> contextRef) {
        return decorate(contextRef.get(), contextRef.get().getFunctionDescriptor(func), (LLVMFunctionDescriptor) wrapper.getObject());
    }

    @Specialization(guards = "isFunctionDescriptor(func.getObject())")
    protected Object decorate(LLVMManagedPointer func, LLVMNativePointer wrapper,
                    @Cached("getContextReference()") ContextReference<LLVMContext> contextRef) {
        return decorate(contextRef.get(), (LLVMFunctionDescriptor) func.getObject(), contextRef.get().getFunctionDescriptor(wrapper));
    }

    @Specialization(guards = {"isFunctionDescriptor(func.getObject())", "isFunctionDescriptor(wrapper.getObject())"})
    protected Object decorate(LLVMManagedPointer func, LLVMManagedPointer wrapper,
                    @Cached("getContextReference()") ContextReference<LLVMContext> contextRef) {
        return decorate(contextRef.get(), (LLVMFunctionDescriptor) func.getObject(), (LLVMFunctionDescriptor) wrapper.getObject());
    }

    private Object decorate(LLVMContext context, LLVMFunctionDescriptor function, LLVMFunctionDescriptor wrapperFunction) {
        assert function != null && wrapperFunction != null;
        FunctionType newFunctionType = new FunctionType(wrapperFunction.getType().getReturnType(), function.getType().getArgumentTypes(), function.getType().isVarargs());
        DecoratedRoot decoratedRoot = new DecoratedRoot(getLLVMLanguage(), function, wrapperFunction);
        LLVMFunctionDescriptor wrappedFunction = LLVMFunctionDescriptor.createDescriptor(context, "<wrapper>", newFunctionType, -1);
        wrappedFunction.define(function.getLibrary(), new LLVMIRFunction(Truffle.getRuntime().createCallTarget(decoratedRoot), null));
        return LLVMManagedPointer.create(wrappedFunction);
    }
}
