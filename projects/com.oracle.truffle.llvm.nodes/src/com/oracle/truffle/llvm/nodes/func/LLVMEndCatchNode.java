/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.func;

import java.util.LinkedList;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public final class LLVMEndCatchNode extends LLVMExpressionNode {

    @Child private LLVMExpressionNode stackPointer;
    @Child private LLVMLookupDispatchNode dispatch;
    @Child private LLVMNativeFunctions.SulongDecrementHandlerCountNode decHandlerCount;
    @Child private LLVMNativeFunctions.SulongGetHandlerCountNode getHandlerCount;
    @Child private LLVMNativeFunctions.SulongGetThrownObjectNode getThrownObject;
    @Child private LLVMNativeFunctions.SulongGetDestructorNode getDestructor;
    @Child private LLVMNativeFunctions.SulongSetHandlerCountNode setHandlerCount;
    private final LinkedList<LLVMAddress> caughtExceptionStack;

    public LLVMEndCatchNode(LLVMContext context, LLVMExpressionNode stackPointer) {
        this.stackPointer = stackPointer;
        this.caughtExceptionStack = context.getCaughtExceptionStack();
        this.dispatch = LLVMLookupDispatchNodeGen.create(context, new FunctionType(VoidType.INSTANCE, new Type[]{new PointerType(null)}, false));
        this.decHandlerCount = context.getNativeFunctions().createDecrementHandlerCount();
        this.getHandlerCount = context.getNativeFunctions().createGetHandlerCount();
        this.getDestructor = context.getNativeFunctions().createGetDestructor();
        this.getThrownObject = context.getNativeFunctions().createGetThrownObject();
        this.setHandlerCount = context.getNativeFunctions().createSetHandlerCount();
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        try {
            LLVMAddress ptr = popExceptionToStack();
            int handlerCount = getHandlerCount.get(ptr);
            if (handlerCount == LLVMRethrowNode.RETHROWN_MARKER) {
                // exception was re-thrown, do nothing but reset marker
                setHandlerCount.set(ptr, 0);
                return 0;
            }
            decHandlerCount.dec(ptr);
            LLVMAddress destructorAddress = getDestructor.get(ptr);
            if (getHandlerCount.get(ptr) <= 0 && destructorAddress.getVal() != 0) {
                LLVMFunctionHandle destructor = new LLVMFunctionHandle((int) destructorAddress.getVal());
                dispatch.executeDispatch(frame, destructor, new Object[]{stackPointer.executeLLVMAddress(frame), getThrownObject.getThrownObject(ptr)});
            }
            return null;
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @TruffleBoundary
    private LLVMAddress popExceptionToStack() {
        return caughtExceptionStack.pop();
    }

}
