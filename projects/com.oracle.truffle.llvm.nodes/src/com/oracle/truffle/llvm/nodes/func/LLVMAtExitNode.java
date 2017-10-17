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
import com.oracle.truffle.llvm.nodes.memory.LLVMForceLLVMAddressNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMForceLLVMAddressNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext.DestructorStackElement;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public final class LLVMAtExitNode extends LLVMExpressionNode {

    @CompilationFinal private LinkedList<DestructorStackElement> destructorStack;
    @Child private LLVMExpressionNode destructor;
    @Child private LLVMExpressionNode thiz;
    @Child private LLVMExpressionNode dsoHandle;
    @Child private LLVMForceLLVMAddressNode forceToAddress;

    public LLVMAtExitNode(LLVMExpressionNode destructor, LLVMExpressionNode thiz, LLVMExpressionNode dsoHandle) {
        this.destructor = destructor;
        this.thiz = thiz;
        this.dsoHandle = dsoHandle;
        this.forceToAddress = LLVMForceLLVMAddressNodeGen.create();
    }

    public LinkedList<DestructorStackElement> getDestructorStack() {
        if (destructorStack == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.destructorStack = getContext().getDestructorStack();
        }
        return destructorStack;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        try {
            LLVMFunctionDescriptor d = destructor.executeLLVMFunctionDescriptor(frame);
            LLVMAddress t = forceToAddress.executeWithTarget(frame, thiz.executeGeneric(frame));
            LLVMAddress h = forceToAddress.executeWithTarget(frame, thiz.executeGeneric(frame));
            addDestructorStackElement(d, t, h);
        } catch (Throwable t) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(t);
        }
        return 0;
    }

    @TruffleBoundary
    private void addDestructorStackElement(LLVMFunctionDescriptor d, LLVMAddress t, @SuppressWarnings("unused") LLVMAddress h) {
        getDestructorStack().push(new DestructorStackElement(d, t));
    }

}
