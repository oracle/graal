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
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMException;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions;

public final class LLVMThrowExceptionNode extends LLVMExpressionNode {

    @Child private LLVMExpressionNode exceptionInfo;
    @Child private LLVMExpressionNode thrownTypeID;
    @Child private LLVMExpressionNode destructor;
    @Child private LLVMNativeFunctions.SulongThrowNode exceptionInitializaton;

    public LLVMThrowExceptionNode(LLVMExpressionNode arg1, LLVMExpressionNode arg2, LLVMExpressionNode arg3) {
        this.exceptionInfo = arg1;
        this.thrownTypeID = arg2;
        this.destructor = arg3;
    }

    public LLVMNativeFunctions.SulongThrowNode getExceptionInitializaton() {
        if (exceptionInitializaton == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.exceptionInitializaton = insert(getContext().getNativeFunctions().createSulongThrow());
        }
        return exceptionInitializaton;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        LLVMAddress thrownObject = exceptionInfo.enforceLLVMAddress(frame);
        LLVMAddress thrownType = thrownTypeID.enforceLLVMAddress(frame);
        LLVMAddress dest = destructor.enforceLLVMAddress(frame);
        getExceptionInitializaton().throvv(thrownObject, thrownType, dest, LLVMAddress.nullPointer(), LLVMAddress.nullPointer());
        throw new LLVMException(thrownObject);
    }

}
