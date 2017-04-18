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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions.MemCopyNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
@NodeField(type = int.class, name = "numberExplicitArguments")
public abstract class LLVMX86_64BitVACopy extends LLVMExpressionNode {

    public abstract int getNumberExplicitArguments();

    @Child private MemCopyNode memCopy;

    public MemCopyNode getMemCopy() {
        if (memCopy == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            memCopy = insert(getContext().getNativeFunctions().createMemCopyNode());
        }
        return memCopy;
    }

    @Specialization
    public Object executeVoid(LLVMGlobalVariable dest, LLVMGlobalVariable source) {
        return executeVoid(dest.getNativeLocation(), source.getNativeLocation());
    }

    @Specialization
    public Object executeVoid(LLVMAddress dest, LLVMAddress source) {

        /*
         * COPY THIS: typedef struct { unsigned int gp_offset; unsigned int fp_offset; void
         * *overflow_arg_area; void *reg_save_area; } va_list[1];
         */
        LLVMMemory.putI32(dest, LLVMMemory.getI32(source));
        LLVMMemory.putI32(dest.increment(X86_64BitVarArgs.FP_OFFSET), LLVMMemory.getI32(source.increment(X86_64BitVarArgs.FP_OFFSET)));
        LLVMMemory.putI64(dest.increment(X86_64BitVarArgs.OVERFLOW_ARG_AREA), LLVMMemory.getI64(source.increment(X86_64BitVarArgs.OVERFLOW_ARG_AREA)));
        LLVMMemory.putI64(dest.increment(X86_64BitVarArgs.REG_SAVE_AREA), LLVMMemory.getI64(source.increment(X86_64BitVarArgs.REG_SAVE_AREA)));
        return null;
    }

}
