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

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVM80BitFloatReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMAddressReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMDoubleReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMFloatReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMFunctionReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMI16ReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMI1ReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMI32ReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMI64ReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMI8ReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMIReadVarBitNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadVectorNodeFactory.LLVMDoubleVectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadVectorNodeFactory.LLVMFloatVectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadVectorNodeFactory.LLVMI16VectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadVectorNodeFactory.LLVMI1VectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadVectorNodeFactory.LLVMI32VectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadVectorNodeFactory.LLVMI64VectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadVectorNodeFactory.LLVMI8VectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWrite80BitFloatingNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteAddressNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteFloatNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteFunctionNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI16NodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI1NodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI32NodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI64NodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI8NodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteIVarBitNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteVectorNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;

final class LLVMFrameReadWriteFactory {

    private LLVMFrameReadWriteFactory() {
    }

    static LLVMExpressionNode createFrameRead(Type llvmType, FrameSlot frameSlot) {
        if (llvmType instanceof PrimitiveType) {
            switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
                case I1:
                    return LLVMI1ReadNodeGen.create(frameSlot);
                case I8:
                    return LLVMI8ReadNodeGen.create(frameSlot);
                case I16:
                    return LLVMI16ReadNodeGen.create(frameSlot);
                case I32:
                    return LLVMI32ReadNodeGen.create(frameSlot);
                case I64:
                    return LLVMI64ReadNodeGen.create(frameSlot);
                case FLOAT:
                    return LLVMFloatReadNodeGen.create(frameSlot);
                case DOUBLE:
                    return LLVMDoubleReadNodeGen.create(frameSlot);
                case X86_FP80:
                    return LLVM80BitFloatReadNodeGen.create(frameSlot);
            }
        } else if (llvmType instanceof VectorType) {
            switch (((VectorType) llvmType).getElementType().getPrimitiveKind()) {
                case I1:
                    return LLVMI1VectorReadNodeGen.create(frameSlot);
                case I8:
                    return LLVMI8VectorReadNodeGen.create(frameSlot);
                case I16:
                    return LLVMI16VectorReadNodeGen.create(frameSlot);
                case I32:
                    return LLVMI32VectorReadNodeGen.create(frameSlot);
                case I64:
                    return LLVMI64VectorReadNodeGen.create(frameSlot);
                case FLOAT:
                    return LLVMFloatVectorReadNodeGen.create(frameSlot);
                case DOUBLE:
                    return LLVMDoubleVectorReadNodeGen.create(frameSlot);
            }
        } else if (llvmType instanceof VariableBitWidthType) {
            return LLVMIReadVarBitNodeGen.create(frameSlot);
        } else if (Type.isFunctionOrFunctionPointer(llvmType)) {
            return LLVMFunctionReadNodeGen.create(frameSlot);
        } else if (llvmType instanceof PointerType) {
            return LLVMAddressReadNodeGen.create(frameSlot);
        } else if (llvmType instanceof StructureType || llvmType instanceof ArrayType) {
            return LLVMAddressReadNodeGen.create(frameSlot);
        } else if (llvmType instanceof VoidType) {
            throw new LLVMUnsupportedException(UnsupportedReason.PARSER_ERROR_VOID_SLOT);
        }
        throw new AssertionError(llvmType + " for " + frameSlot.getIdentifier());
    }

    static LLVMExpressionNode createFrameWrite(Type llvmType, LLVMExpressionNode result, FrameSlot slot) {
        if (llvmType instanceof VectorType) {
            return LLVMWriteVectorNodeGen.create(result, slot);
        } else if (llvmType instanceof PrimitiveType) {
            return handlePrimitive(llvmType, result, slot);
        } else if (llvmType instanceof VariableBitWidthType) {
            return LLVMWriteIVarBitNodeGen.create(result, slot);
        } else if (Type.isFunctionOrFunctionPointer(llvmType)) {
            return LLVMWriteFunctionNodeGen.create(result, slot);
        } else if (llvmType instanceof PointerType) {
            return LLVMWriteAddressNodeGen.create(result, slot);
        } else if (llvmType instanceof StructureType || llvmType instanceof ArrayType) {
            return LLVMWriteAddressNodeGen.create(result, slot);
        }
        throw new AssertionError(llvmType);
    }

    private static LLVMExpressionNode handlePrimitive(Type llvmType, LLVMExpressionNode result, FrameSlot slot) throws AssertionError {
        switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
            case I1:
                return LLVMWriteI1NodeGen.create(result, slot);
            case I8:
                return LLVMWriteI8NodeGen.create(result, slot);
            case I16:
                return LLVMWriteI16NodeGen.create(result, slot);
            case I32:
                return LLVMWriteI32NodeGen.create(result, slot);
            case I64:
                return LLVMWriteI64NodeGen.create(result, slot);
            case FLOAT:
                return LLVMWriteFloatNodeGen.create(result, slot);
            case DOUBLE:
                return LLVMWriteDoubleNodeGen.create(result, slot);
            case X86_FP80:
                return LLVMWrite80BitFloatingNodeGen.create(result, slot);
            default:
                throw new AssertionError(llvmType);
        }
    }

}
