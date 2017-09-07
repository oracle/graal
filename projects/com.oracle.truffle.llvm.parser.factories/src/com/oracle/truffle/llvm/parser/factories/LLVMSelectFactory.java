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
import com.oracle.truffle.llvm.nodes.others.LLVMVectorSelectNodeFactory.LLVMDoubleVectorSelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMVectorSelectNodeFactory.LLVMFloatVectorSelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMVectorSelectNodeFactory.LLVMI16VectorSelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMVectorSelectNodeFactory.LLVMI1VectorSelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMVectorSelectNodeFactory.LLVMI32VectorSelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMVectorSelectNodeFactory.LLVMI64VectorSelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMVectorSelectNodeFactory.LLVMI8VectorSelectNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMVectorSelectNodeFactory.LLVMAddressVectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;

final class LLVMSelectFactory {

    static LLVMExpressionNode createSelect(Type llvmType, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue) {
        if (Type.isFunctionOrFunctionPointer(llvmType)) {
            return LLVMFunctionSelectNodeGen.create(condition, trueValue, falseValue);
        } else if (llvmType instanceof PointerType) {
            return LLVMAddressSelectNodeGen.create(condition, trueValue, falseValue);
        } else if (llvmType instanceof PrimitiveType) {
            return handlePrimitive(llvmType, condition, trueValue, falseValue);
        } else {
            throw new AssertionError(llvmType);
        }
    }

    private static LLVMExpressionNode handlePrimitive(Type llvmType, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue) throws AssertionError {
        switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
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
            default:
                throw new AssertionError(llvmType);
        }
    }

    static LLVMExpressionNode createSelectVector(Type llvmType, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue) {
        if (llvmType instanceof VectorType) {
            final Type elementType = ((VectorType) llvmType).getElementType();
            if (elementType == PrimitiveType.I1) {
                return LLVMI1VectorSelectNodeGen.create(condition, trueValue, falseValue);
            } else if (elementType == PrimitiveType.I8) {
                return LLVMI8VectorSelectNodeGen.create(condition, trueValue, falseValue);
            } else if (elementType == PrimitiveType.I16) {
                return LLVMI16VectorSelectNodeGen.create(condition, trueValue, falseValue);
            } else if (elementType == PrimitiveType.I32) {
                return LLVMI32VectorSelectNodeGen.create(condition, trueValue, falseValue);
            } else if (elementType == PrimitiveType.I64) {
                return LLVMI64VectorSelectNodeGen.create(condition, trueValue, falseValue);
            } else if (elementType == PrimitiveType.FLOAT) {
                return LLVMFloatVectorSelectNodeGen.create(condition, trueValue, falseValue);
            } else if (elementType == PrimitiveType.DOUBLE) {
                return LLVMDoubleVectorSelectNodeGen.create(condition, trueValue, falseValue);
            } else if (elementType instanceof PointerType) {
                return LLVMAddressVectorSelectNodeGen.create(condition, trueValue, falseValue);
            }
        }
        throw new AssertionError("Cannot create vector select for type: " + llvmType);
    }

}
