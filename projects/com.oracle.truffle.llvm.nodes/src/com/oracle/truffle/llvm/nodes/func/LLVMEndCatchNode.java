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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
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
    @CompilationFinal private LinkedList<LLVMAddress> caughtExceptionStack;
    @CompilationFinal private LLVMContext cachedContext;

    public LLVMContext getCachedContext() {
        if (cachedContext == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.cachedContext = getContext();
        }
        return cachedContext;
    }

    public LLVMEndCatchNode(LLVMExpressionNode stackPointer) {
        this.stackPointer = stackPointer;
        this.dispatch = LLVMLookupDispatchNodeGen.create(new FunctionType(VoidType.INSTANCE, new Type[]{new PointerType(null)}, false));
    }

    public LinkedList<LLVMAddress> getCaughtExceptionStack() {
        if (caughtExceptionStack == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.caughtExceptionStack = getContext().getCaughtExceptionStack();
        }
        return caughtExceptionStack;
    }

    public LLVMNativeFunctions.SulongDecrementHandlerCountNode getDecHandlerCount() {
        if (decHandlerCount == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.decHandlerCount = insert(getContext().getNativeFunctions().createDecrementHandlerCount());
        }
        return decHandlerCount;
    }

    public LLVMNativeFunctions.SulongGetHandlerCountNode getGetHandlerCount() {
        if (getHandlerCount == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.getHandlerCount = insert(getContext().getNativeFunctions().createGetHandlerCount());
        }
        return getHandlerCount;
    }

    public LLVMNativeFunctions.SulongGetDestructorNode getGetDestructor() {
        if (getDestructor == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.getDestructor = insert(getContext().getNativeFunctions().createGetDestructor());
        }
        return getDestructor;
    }

    public LLVMNativeFunctions.SulongGetThrownObjectNode getGetThrownObject() {
        if (getThrownObject == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.getThrownObject = insert(getContext().getNativeFunctions().createGetThrownObject());
        }
        return getThrownObject;
    }

    public LLVMNativeFunctions.SulongSetHandlerCountNode getSetHandlerCount() {
        if (setHandlerCount == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.setHandlerCount = insert(getContext().getNativeFunctions().createSetHandlerCount());
        }
        return setHandlerCount;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        try {
            LLVMAddress ptr = popExceptionToStack();
            int handlerCount = getGetHandlerCount().get(ptr);
            if (handlerCount == LLVMRethrowNode.RETHROWN_MARKER) {
                // exception was re-thrown, do nothing but reset marker
                getSetHandlerCount().set(ptr, 0);
                return 0;
            }
            getDecHandlerCount().dec(ptr);
            LLVMAddress destructorAddress = getGetDestructor().get(ptr);
            if (getGetHandlerCount().get(ptr) <= 0 && destructorAddress.getVal() != 0) {
                LLVMFunctionHandle destructor = new LLVMFunctionHandle(destructorAddress.getVal());
                dispatch.executeDispatch(frame, destructor, new Object[]{stackPointer.executeLLVMAddress(frame), getGetThrownObject().getThrownObject(ptr)});
            }
            return null;
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @TruffleBoundary
    private LLVMAddress popExceptionToStack() {
        return getCaughtExceptionStack().pop();
    }

}
