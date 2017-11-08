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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.memory.LLVMForceLLVMAddressNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMForceLLVMAddressNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMException;
import com.oracle.truffle.llvm.runtime.LLVMNativeFunctions;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public final class LLVMThrowExceptionNode extends LLVMExpressionNode {

    @Child private LLVMExpressionNode exceptionInfo;
    @Child private LLVMForceLLVMAddressNode exceptionInfoToLLVM;
    @Child private LLVMExpressionNode thrownTypeID;
    @Child private LLVMForceLLVMAddressNode thrownTypeIDToLLVM;
    @Child private LLVMExpressionNode destructor;
    @Child private LLVMForceLLVMAddressNode destructorToLLVM;
    @Child private LLVMNativeFunctions.SulongThrowNode exceptionInitializaton;

    public LLVMThrowExceptionNode(LLVMExpressionNode arg1, LLVMExpressionNode arg2, LLVMExpressionNode arg3) {
        this.exceptionInfo = arg1;
        this.exceptionInfoToLLVM = getForceLLVMAddressNode();
        this.thrownTypeID = arg2;
        this.thrownTypeIDToLLVM = getForceLLVMAddressNode();
        this.destructor = arg3;
        this.destructorToLLVM = getForceLLVMAddressNode();
    }

    public LLVMNativeFunctions.SulongThrowNode getExceptionInitializaton() {
        if (exceptionInitializaton == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMContext context = getContextReference().get();
            NFIContextExtension nfiContextExtension = context.getContextExtension(NFIContextExtension.class);
            this.exceptionInitializaton = insert(nfiContextExtension.getNativeSulongFunctions().createSulongThrow(context));
        }
        return exceptionInitializaton;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        LLVMAddress thrownObject = exceptionInfoToLLVM.executeWithTarget(frame, exceptionInfo.executeGeneric(frame));
        LLVMAddress thrownType = thrownTypeIDToLLVM.executeWithTarget(frame, thrownTypeID.executeGeneric(frame));
        LLVMAddress dest = destructorToLLVM.executeWithTarget(frame, destructor.executeGeneric(frame));
        getExceptionInitializaton().throvv(thrownObject, thrownType, dest, LLVMAddress.nullPointer(), LLVMAddress.nullPointer());
        throw new LLVMException(thrownObject);
    }

    private static LLVMForceLLVMAddressNode getForceLLVMAddressNode() {
        return LLVMForceLLVMAddressNodeGen.create();
    }

}
