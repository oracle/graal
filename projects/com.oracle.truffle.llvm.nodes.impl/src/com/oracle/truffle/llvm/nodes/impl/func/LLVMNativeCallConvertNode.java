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
package com.oracle.truffle.llvm.nodes.impl.func;

import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNode.LLVMResolvedDirectNativeCallNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;

/**
 * This node converts the results of native functions to primitives or objects, that other Sulong
 * nodes expect.
 */
public abstract class LLVMNativeCallConvertNode {

    // FIXME: do not use inheritance
    public static class LLVMResolvedNativeAddressCallNode extends LLVMResolvedDirectNativeCallNode {

        public LLVMResolvedNativeAddressCallNode(LLVMFunctionDescriptor function, NativeFunctionHandle nativeFunctionHandle, LLVMExpressionNode[] args, LLVMContext context) {
            super(function, nativeFunctionHandle, args, context);
            assert function.getReturnType() == LLVMRuntimeType.ADDRESS;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            long addr = (long) super.executeGeneric(frame);
            return LLVMAddress.fromLong(addr);
        }
    }

    public static class LLVMResolvedNative80BitFloatCallNode extends LLVMResolvedDirectNativeCallNode {

        public LLVMResolvedNative80BitFloatCallNode(LLVMFunctionDescriptor function, NativeFunctionHandle nativeFunctionHandle, LLVMExpressionNode[] args, LLVMContext context) {
            super(function, nativeFunctionHandle, args, context);
            assert function.getReturnType() == LLVMRuntimeType.X86_FP80;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            throw new AssertionError("not yet supported!");
        }

    }

}
