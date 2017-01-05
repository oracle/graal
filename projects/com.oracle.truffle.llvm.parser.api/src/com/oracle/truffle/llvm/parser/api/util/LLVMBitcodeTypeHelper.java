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
package com.oracle.truffle.llvm.parser.api.util;

import com.oracle.truffle.llvm.parser.api.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.api.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.api.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.parser.api.model.enums.BinaryOperator;
import com.oracle.truffle.llvm.parser.api.model.enums.CastOperator;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.types.Type;

public class LLVMBitcodeTypeHelper {

    public static LLVMArithmeticInstructionType toArithmeticInstructionType(BinaryOperator operator) {
        switch (operator) {
            case INT_ADD:
            case FP_ADD:
                return LLVMArithmeticInstructionType.ADDITION;
            case INT_SUBTRACT:
            case FP_SUBTRACT:
                return LLVMArithmeticInstructionType.SUBTRACTION;
            case INT_MULTIPLY:
            case FP_MULTIPLY:
                return LLVMArithmeticInstructionType.MULTIPLICATION;
            case INT_UNSIGNED_DIVIDE:
                return LLVMArithmeticInstructionType.UNSIGNED_DIVISION;
            case INT_SIGNED_DIVIDE:
            case FP_DIVIDE:
                return LLVMArithmeticInstructionType.DIVISION;
            case INT_UNSIGNED_REMAINDER:
                return LLVMArithmeticInstructionType.UNSIGNED_REMAINDER;
            case INT_SIGNED_REMAINDER:
            case FP_REMAINDER:
                return LLVMArithmeticInstructionType.REMAINDER;
            default:
                return null;
        }
    }

    public static LLVMConversionType toConversionType(CastOperator operator) {
        switch (operator) {
            case FP_TO_UNSIGNED_INT:
                return LLVMConversionType.FLOAT_TO_UINT;
            case ZERO_EXTEND:
            case UNSIGNED_INT_TO_FP:
            case INT_TO_PTR:
                return LLVMConversionType.ZERO_EXTENSION;
            case SIGN_EXTEND:
            case FP_TO_SIGNED_INT:
            case SIGNED_INT_TO_FP:
            case FP_EXTEND:
                return LLVMConversionType.SIGN_EXTENSION;
            case TRUNCATE:
            case PTR_TO_INT:
            case FP_TRUNCATE:
                return LLVMConversionType.TRUNC;
            case BITCAST:
                return LLVMConversionType.BITCAST;
            case ADDRESS_SPACE_CAST:
            default:
                return null;
        }
    }

    public static LLVMLogicalInstructionType toLogicalInstructionType(BinaryOperator operator) {
        switch (operator) {
            case INT_SHIFT_LEFT:
                return LLVMLogicalInstructionType.SHIFT_LEFT;
            case INT_LOGICAL_SHIFT_RIGHT:
                return LLVMLogicalInstructionType.LOGICAL_SHIFT_RIGHT;
            case INT_ARITHMETIC_SHIFT_RIGHT:
                return LLVMLogicalInstructionType.ARITHMETIC_SHIFT_RIGHT;
            case INT_AND:
                return LLVMLogicalInstructionType.AND;
            case INT_OR:
                return LLVMLogicalInstructionType.OR;
            case INT_XOR:
                return LLVMLogicalInstructionType.XOR;
            default:
                return null;
        }
    }

    public static LLVMFunctionDescriptor.LLVMRuntimeType[] toRuntimeTypes(Type[] types) {
        final LLVMFunctionDescriptor.LLVMRuntimeType[] llvmtypes = new LLVMFunctionDescriptor.LLVMRuntimeType[types.length];
        for (int i = 0; i < types.length; i++) {
            llvmtypes[i] = types[i].getType().getRuntimeType();
        }
        return llvmtypes;
    }

}
