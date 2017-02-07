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
package com.oracle.truffle.llvm.parser.util;

import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.runtime.types.LLVMBaseType;
import com.oracle.truffle.llvm.runtime.types.LLVMType;

public final class LLVMTypeHelper {

    public static boolean isVectorType(LLVMBaseType llvmType) {
        switch (llvmType) {
            case I1_VECTOR:
            case I8_VECTOR:
            case I16_VECTOR:
            case I32_VECTOR:
            case I64_VECTOR:
            case FLOAT_VECTOR:
            case DOUBLE_VECTOR:
                return true;
            case ARRAY:
            case DOUBLE:
            case F128:
            case FLOAT:
            case FUNCTION_ADDRESS:
            case HALF:
            case I1:
            case I16:
            case I32:
            case I64:
            case I8:
            case ADDRESS:
            case PPC_FP128:
            case STRUCT:
            case VOID:
            case X86_FP80:
            case I_VAR_BITWIDTH:
                return false;
            default:
                throw new AssertionError(llvmType);
        }
    }

    public static LLVMRuntimeType convertType(LLVMType llvmReturnType) {
        if (llvmReturnType.isPointer()) {
            switch (llvmReturnType.getPointee().getType()) {
                case I1:
                    return LLVMRuntimeType.I1_POINTER;
                case I8:
                    return LLVMRuntimeType.I8_POINTER;
                case I16:
                    return LLVMRuntimeType.I16_POINTER;
                case I32:
                    return LLVMRuntimeType.I32_POINTER;
                case I64:
                    return LLVMRuntimeType.I64_POINTER;
                case HALF:
                    return LLVMRuntimeType.HALF_POINTER;
                case FLOAT:
                    return LLVMRuntimeType.FLOAT_POINTER;
                case DOUBLE:
                    return LLVMRuntimeType.DOUBLE_POINTER;
                default:
                    return LLVMRuntimeType.ADDRESS;
            }
        } else {
            return LLVMRuntimeType.valueOf(llvmReturnType.getType().toString());
        }
    }

}
