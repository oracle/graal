/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.Intrinsic;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;

public class LLVMForeignIntrinsicCallNode extends RootNode {

    public static LLVMForeignIntrinsicCallNode create(LLVMLanguage language, Intrinsic intrinsic, FunctionType type, LLVMInteropType.Function interopType) {
        int argCount = interopType.getNumberOfParameters() + 1;
        LLVMExpressionNode[] args = new LLVMExpressionNode[argCount];

        // intrinsics shouldn't need the stack argument
        args[0] = null;

        for (int i = 1; i < argCount; i++) {
            LLVMInteropType.Value argType = (LLVMInteropType.Value) interopType.getParameter(i - 1);
            args[i] = new ForeignIntrinsicArgNode(i - 1, argType);
        }

        LLVMExpressionNode intrinsicNode = intrinsic.createIntrinsicNode(args, type.getArgumentTypes().toArray(Type.EMPTY_ARRAY));
        return new LLVMForeignIntrinsicCallNode(language, intrinsicNode, (LLVMInteropType.Value) interopType.getReturnType());
    }

    @Child LLVMExpressionNode intrinsicNode;
    @Child LLVMDataEscapeNode dataEscape;

    private final LLVMInteropType.Structured retType;

    protected LLVMForeignIntrinsicCallNode(LLVMLanguage language, LLVMExpressionNode intrinsic, LLVMInteropType.Value retType) {
        super(language);
        this.intrinsicNode = intrinsic;
        this.dataEscape = LLVMDataEscapeNode.create(retType.kind.foreignToLLVMType);
        this.retType = retType.baseType;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object ret = intrinsicNode.executeGeneric(frame);
        return dataEscape.executeWithType(ret, retType);
    }

    static class ForeignIntrinsicArgNode extends LLVMExpressionNode {

        private final int argIdx;
        @Child ForeignToLLVM toLLVM;

        ForeignIntrinsicArgNode(int argIdx, LLVMInteropType.Value argType) {
            this.argIdx = argIdx;
            this.toLLVM = CommonNodeFactory.createForeignToLLVM(argType);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return toLLVM.executeWithTarget(frame.getArguments()[argIdx]);
        }
    }
}
