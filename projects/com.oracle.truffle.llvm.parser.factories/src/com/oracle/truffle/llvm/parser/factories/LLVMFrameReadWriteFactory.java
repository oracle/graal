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

import com.intel.llvm.ireditor.types.ResolvedType;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMIVarBitNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMVectorNode;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadNodeFactory.LLVM80BitFloatReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadNodeFactory.LLVMAddressReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadNodeFactory.LLVMDoubleReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadNodeFactory.LLVMFloatReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadNodeFactory.LLVMFunctionReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadNodeFactory.LLVMI16ReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadNodeFactory.LLVMI1ReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadNodeFactory.LLVMI32ReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadNodeFactory.LLVMI64ReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadNodeFactory.LLVMI8ReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadNodeFactory.LLVMIReadVarBitNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadVectorNodeFactory.LLVMDoubleVectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadVectorNodeFactory.LLVMFloatVectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadVectorNodeFactory.LLVMI16VectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadVectorNodeFactory.LLVMI1VectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadVectorNodeFactory.LLVMI32VectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadVectorNodeFactory.LLVMI64VectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadVectorNodeFactory.LLVMI8VectorReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteNodeFactory.LLVMWrite80BitFloatingNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteNodeFactory.LLVMWriteAddressNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteNodeFactory.LLVMWriteDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteNodeFactory.LLVMWriteFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteNodeFactory.LLVMWriteFunctionNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteNodeFactory.LLVMWriteI16NodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteNodeFactory.LLVMWriteI1NodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteNodeFactory.LLVMWriteI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteNodeFactory.LLVMWriteI64NodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteNodeFactory.LLVMWriteI8NodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteNodeFactory.LLVMWriteIVarBitNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteVectorNodeGen;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.base.util.LLVMTypeHelperImpl;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;

public final class LLVMFrameReadWriteFactory {

    private LLVMFrameReadWriteFactory() {
    }

    public static LLVMExpressionNode createFrameRead(LLVMBaseType llvmType, FrameSlot frameSlot) {
        switch (llvmType) {
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
            case I_VAR_BITWIDTH:
                return LLVMIReadVarBitNodeGen.create(frameSlot);
            case FLOAT:
                return LLVMFloatReadNodeGen.create(frameSlot);
            case DOUBLE:
                return LLVMDoubleReadNodeGen.create(frameSlot);
            case X86_FP80:
                return LLVM80BitFloatReadNodeGen.create(frameSlot);
            case ADDRESS:
                return LLVMAddressReadNodeGen.create(frameSlot);
            case FUNCTION_ADDRESS:
                return LLVMFunctionReadNodeGen.create(frameSlot);
            case I1_VECTOR:
                return LLVMI1VectorReadNodeGen.create(frameSlot);
            case I8_VECTOR:
                return LLVMI8VectorReadNodeGen.create(frameSlot);
            case I16_VECTOR:
                return LLVMI16VectorReadNodeGen.create(frameSlot);
            case I32_VECTOR:
                return LLVMI32VectorReadNodeGen.create(frameSlot);
            case I64_VECTOR:
                return LLVMI64VectorReadNodeGen.create(frameSlot);
            case FLOAT_VECTOR:
                return LLVMFloatVectorReadNodeGen.create(frameSlot);
            case DOUBLE_VECTOR:
                return LLVMDoubleVectorReadNodeGen.create(frameSlot);
            case STRUCT:
            case ARRAY:
                return LLVMAddressReadNodeGen.create(frameSlot);
            case VOID:
                throw new LLVMUnsupportedException(UnsupportedReason.PARSER_ERROR_VOID_SLOT);
            default:
                throw new AssertionError(llvmType + " for " + frameSlot.getIdentifier());
        }
    }

    public static LLVMNode createFrameWrite(LLVMBaseType llvmType, LLVMExpressionNode result, FrameSlot slot) {
        if (LLVMTypeHelperImpl.isVectorType(llvmType)) {
            return LLVMWriteVectorNodeGen.create((LLVMVectorNode) result, slot);
        }
        switch (llvmType) {
            case I1:
                return LLVMWriteI1NodeGen.create((LLVMI1Node) result, slot);
            case I8:
                return LLVMWriteI8NodeGen.create((LLVMI8Node) result, slot);
            case I16:
                return LLVMWriteI16NodeGen.create((LLVMI16Node) result, slot);
            case I32:
                return LLVMWriteI32NodeGen.create((LLVMI32Node) result, slot);
            case I64:
                return LLVMWriteI64NodeGen.create((LLVMI64Node) result, slot);
            case I_VAR_BITWIDTH:
                return LLVMWriteIVarBitNodeGen.create((LLVMIVarBitNode) result, slot);
            case FLOAT:
                return LLVMWriteFloatNodeGen.create((LLVMFloatNode) result, slot);
            case DOUBLE:
                return LLVMWriteDoubleNodeGen.create((LLVMDoubleNode) result, slot);
            case X86_FP80:
                return LLVMWrite80BitFloatingNodeGen.create((LLVM80BitFloatNode) result, slot);
            case ADDRESS:
                return LLVMWriteAddressNodeGen.create(result, slot);
            case FUNCTION_ADDRESS:
                return LLVMWriteFunctionNodeGen.create((LLVMFunctionNode) result, slot);
            case STRUCT:
            case ARRAY:
                return LLVMWriteAddressNodeGen.create(result, slot);
            default:
                throw new AssertionError(llvmType);
        }
    }

    public static FrameSlotKind getFrameSlotKind(ResolvedType type) {
        LLVMBaseType llvmType = LLVMTypeHelperImpl.getLLVMType(type).getType();
        return LLVMFrameReadWriteFactory.getFrameSlotKind(llvmType);
    }

    private static FrameSlotKind getFrameSlotKind(LLVMBaseType llvmType) {
        switch (llvmType) {
            case I1:
                return FrameSlotKind.Boolean;
            case I8:
                return FrameSlotKind.Byte;
            case I16:
                return FrameSlotKind.Int; // no short type?
            case I32:
                return FrameSlotKind.Int;
            case I64:
                return FrameSlotKind.Long;
            case FLOAT:
                return FrameSlotKind.Float;
            case DOUBLE:
                return FrameSlotKind.Double;
            case I_VAR_BITWIDTH:
            case X86_FP80:
            case I1_VECTOR:
            case I8_VECTOR:
            case I16_VECTOR:
            case I32_VECTOR:
            case I64_VECTOR:
            case FLOAT_VECTOR:
            case DOUBLE_VECTOR:
            case STRUCT:
            case FUNCTION_ADDRESS:
            case ADDRESS:
            case ARRAY:
                return FrameSlotKind.Object;
            case VOID:
                throw new LLVMUnsupportedException(UnsupportedReason.PARSER_ERROR_VOID_SLOT);
            default:
                throw new AssertionError(llvmType);
        }
    }

}
