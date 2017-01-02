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
package com.oracle.truffle.llvm.parser.factories;

import com.oracle.truffle.llvm.context.LLVMContext;
import com.oracle.truffle.llvm.context.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMAllocInstruction.LLVMAllocaInstruction;
import com.oracle.truffle.llvm.nodes.memory.LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMAllocInstructionFactory.LLVMI32AllocaInstructionNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMAllocInstructionFactory.LLVMI64AllocaInstructionNodeGen;
import com.oracle.truffle.llvm.parser.api.util.LLVMParserRuntime;
import com.oracle.truffle.llvm.runtime.types.LLVMBaseType;

public class LLVMAllocFactory {

    public static LLVMExpressionNode createAlloc(LLVMParserRuntime runtime, LLVMBaseType llvmType, LLVMExpressionNode numElements, int byteSize, int alignment, String name) {
        LLVMContext context = LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0());
        switch (llvmType) {
            case I32:
                return LLVMI32AllocaInstructionNodeGen.create(numElements, byteSize, alignment, context, runtime.getStackPointerSlot(), name);
            case I64:
                return LLVMI64AllocaInstructionNodeGen.create(numElements, byteSize, alignment, context, runtime.getStackPointerSlot(), name);
            default:
                throw new AssertionError(llvmType);
        }
    }

    public static LLVMAllocaInstruction createAlloc(LLVMParserRuntime runtime, int byteSize, int alignment, String name) {
        LLVMContext context = LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0());
        return LLVMAllocaInstructionNodeGen.create(byteSize, alignment, context, runtime.getStackPointerSlot(), name);
    }

}
