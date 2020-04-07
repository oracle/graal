/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;

/**
 * Node for 1:1 mapping of primitive bitcode parameters to interop parameters.
 */
public abstract class LLVMGetInteropPrimitiveParamNode extends LLVMGetInteropParamNode {
    public static LLVMGetInteropPrimitiveParamNode create(int index, ForeignToLLVM.ForeignToLLVMType type) {
        return LLVMGetInteropPrimitiveParamNodeGen.create(index, type);
    }

    public static LLVMGetInteropPrimitiveParamNode create(int index, LLVMInteropType.Value type) {
        return LLVMGetInteropPrimitiveParamNodeGen.create(index, type);
    }

    private final int index;
    @Child ForeignToLLVM toLLVM;

    LLVMGetInteropPrimitiveParamNode(int index, ForeignToLLVM.ForeignToLLVMType type) {
        this.index = index;
        this.toLLVM = CommonNodeFactory.createForeignToLLVM(type);
    }

    LLVMGetInteropPrimitiveParamNode(int index, LLVMInteropType.Value type) {
        this.index = index;
        this.toLLVM = CommonNodeFactory.createForeignToLLVM(type);
    }

    @Specialization
    Object getParam(Object[] arguments) {
        return toLLVM.executeWithTarget(arguments[this.index]);
    }
}
