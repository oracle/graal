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

import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.others.LLVMSelectNodeFactory.LLVM80BitFloatSelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMSelectNodeFactory.LLVMAddressSelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMSelectNodeFactory.LLVMDoubleSelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMSelectNodeFactory.LLVMFloatSelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMSelectNodeFactory.LLVMFunctionSelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMSelectNodeFactory.LLVMI16SelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMSelectNodeFactory.LLVMI1SelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMSelectNodeFactory.LLVMI32SelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMSelectNodeFactory.LLVMI64SelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMSelectNodeFactory.LLVMI8SelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMVectorSelectNodeFactory.LLVMI32VectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.types.LLVMBaseType;

final class LLVMSelectFactory {

    static LLVMExpressionNode createSelect(LLVMBaseType llvmType, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue) {
        switch (llvmType) {
            case I1:
                return LLVMI1SelectNodeGen.create(condition, trueValue, falseValue);
            case I8:
                return LLVMI8SelectNodeGen.create(condition, trueValue, falseValue);
            case I16:
                return LLVMI16SelectNodeGen.create(condition, trueValue, falseValue);
            case I32:
                return LLVMI32SelectNodeGen.create(condition, trueValue, falseValue);
            case I64:
                return LLVMI64SelectNodeGen.create(condition, trueValue, falseValue);
            case FLOAT:
                return LLVMFloatSelectNodeGen.create(condition, trueValue, falseValue);
            case DOUBLE:
                return LLVMDoubleSelectNodeGen.create(condition, trueValue, falseValue);
            case X86_FP80:
                return LLVM80BitFloatSelectNodeGen.create(condition, trueValue, falseValue);
            case ADDRESS:
                return LLVMAddressSelectNodeGen.create(condition, trueValue, falseValue);
            case FUNCTION_ADDRESS:
                return LLVMFunctionSelectNodeGen.create(condition, trueValue, falseValue);
            default:
                throw new AssertionError(llvmType);
        }
    }

    static LLVMExpressionNode createSelectVector(LLVMBaseType llvmType, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue) {
        switch (llvmType) {
            case I32_VECTOR:
                return LLVMI32VectorSelectNodeGen.create(condition, trueValue, falseValue);
            default:
                throw new AssertionError(llvmType);
        }
    }

}
