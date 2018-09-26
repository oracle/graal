/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.nodes.cast.LLVMTo80BitFloatingNodeFactory.LLVMBitcastToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMTo80BitFloatingNodeFactory.LLVMSignedCastToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMTo80BitFloatingNodeFactory.LLVMUnsignedCastToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToAddressNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToDoubleNodeGen.LLVMBitcastToDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToDoubleNodeGen.LLVMSignedCastToDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToDoubleNodeGen.LLVMUnsignedCastToDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToFloatNodeGen.LLVMBitcastToFloatNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToFloatNodeGen.LLVMSignedCastToFloatNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToFloatNodeGen.LLVMUnsignedCastToFloatNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI16NodeGen.LLVMBitcastToI16NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI16NodeGen.LLVMSignedCastToI16NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI16NodeGen.LLVMUnsignedCastToI16NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI1NodeGen.LLVMBitcastToI1NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI1NodeGen.LLVMSignedCastToI1NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI32NodeGen.LLVMBitcastToI32NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI32NodeGen.LLVMSignedCastToI32NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI32NodeGen.LLVMUnsignedCastToI32NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI8NodeGen.LLVMBitcastToI8NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI8NodeGen.LLVMSignedCastToI8NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI8NodeGen.LLVMUnsignedCastToI8NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVarINodeFactory.LLVMBitcastToIVarNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVarINodeFactory.LLVMSignedCastToIVarNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVarINodeFactory.LLVMUnsignedCastToIVarNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToDoubleVectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToFloatVectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToI16VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToI1VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToI32VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToI64VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToI8VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToDoubleVectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToFloatVectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToI16VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToI1VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToI32VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToI64VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToI8VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorZeroExtNodeFactory.LLVMUnsignedCastToI16VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorZeroExtNodeFactory.LLVMUnsignedCastToI1VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorZeroExtNodeFactory.LLVMUnsignedCastToI32VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorZeroExtNodeFactory.LLVMUnsignedCastToI64VectorNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVectorZeroExtNodeFactory.LLVMUnsignedCastToI8VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;

final class LLVMCastsFactory {
    public static LLVMExpressionNode signedCast(BasicNodeFactory nodeFactory, LLVMExpressionNode fromNode, Type targetType) {
        // does a signed cast (either sign extend or truncate) from (int or FP) to (int or FP). for
        // vectors, the number of elements in source and target must match.
        //
        // @formatter:off
        // source: ([vector] int, | ([vector] FP,   | ([vector] sint, | ([vector] FP, | (ptr,
        // target:  [vector] int) |  [vector] sint) |  [vector] FP)   |  [vector] FP) |  int)
        // @formatter:on
        if (targetType instanceof PrimitiveType) {
            switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                case I1:
                    return LLVMSignedCastToI1NodeGen.create(fromNode);
                case I8:
                    return LLVMSignedCastToI8NodeGen.create(fromNode);
                case I16:
                    return LLVMSignedCastToI16NodeGen.create(fromNode);
                case I32:
                    return LLVMSignedCastToI32NodeGen.create(fromNode);
                case I64:
                    return nodeFactory.createSignedCastToI64(fromNode);
                case FLOAT:
                    return LLVMSignedCastToFloatNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMSignedCastToDoubleNodeGen.create(fromNode);
                case X86_FP80:
                    return LLVMSignedCastToLLVM80BitFloatNodeGen.create(fromNode);
            }
        } else if (targetType instanceof VariableBitWidthType) {
            return LLVMSignedCastToIVarNodeGen.create(fromNode, getBits(targetType));
        } else if (targetType instanceof VectorType) {
            VectorType vectorType = (VectorType) targetType;
            Type elemType = vectorType.getElementType();
            int vectorLength = vectorType.getNumberOfElements();
            if (elemType instanceof PrimitiveType) {
                switch (((PrimitiveType) ((VectorType) targetType).getElementType()).getPrimitiveKind()) {
                    case I1:
                        return LLVMSignedCastToI1VectorNodeGen.create(fromNode, vectorLength);
                    case I8:
                        return LLVMSignedCastToI8VectorNodeGen.create(fromNode, vectorLength);
                    case I16:
                        return LLVMSignedCastToI16VectorNodeGen.create(fromNode, vectorLength);
                    case I32:
                        return LLVMSignedCastToI32VectorNodeGen.create(fromNode, vectorLength);
                    case I64:
                        return LLVMSignedCastToI64VectorNodeGen.create(fromNode, vectorLength);
                    case FLOAT:
                        return LLVMSignedCastToFloatVectorNodeGen.create(fromNode, vectorLength);
                    case DOUBLE:
                        return LLVMSignedCastToDoubleVectorNodeGen.create(fromNode, vectorLength);
                }
            }
        }

        throw unsupportedCast(targetType);
    }

    public static LLVMExpressionNode unsignedCast(BasicNodeFactory nodeFactory, LLVMExpressionNode fromNode, Type targetType) {
        // does an unsigned cast (zero extension or FP to uint). for vectors, the number of elements
        // in source and target must match.
        //
        // @formatter:off
        // source: ([vector] int, | ([vector] uint, | ([vector] FP,  | (int,
        // target:  [vector] int) |  [vector] FP)   |  [vector] uint |  ptr)
        // @formatter:on
        if (targetType instanceof PrimitiveType) {
            switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                case I8:
                    return LLVMUnsignedCastToI8NodeGen.create(fromNode);
                case I16:
                    return LLVMUnsignedCastToI16NodeGen.create(fromNode);
                case I32:
                    return LLVMUnsignedCastToI32NodeGen.create(fromNode);
                case I64:
                    return nodeFactory.createUnsignedCastToI64(fromNode);
                case FLOAT:
                    return LLVMUnsignedCastToFloatNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMUnsignedCastToDoubleNodeGen.create(fromNode);
                case X86_FP80:
                    return LLVMUnsignedCastToLLVM80BitFloatNodeGen.create(fromNode);
            }
        } else if (targetType instanceof PointerType || targetType instanceof FunctionType) {
            return LLVMToAddressNodeGen.create(fromNode);
        } else if (targetType instanceof VariableBitWidthType) {
            return LLVMUnsignedCastToIVarNodeGen.create(fromNode, getBits(targetType));
        } else if (targetType instanceof VectorType) {
            VectorType vectorType = (VectorType) targetType;
            Type elemType = vectorType.getElementType();
            int vectorLength = vectorType.getNumberOfElements();
            if (elemType instanceof PrimitiveType) {
                switch (((PrimitiveType) elemType).getPrimitiveKind()) {
                    case I1:
                        return LLVMUnsignedCastToI1VectorNodeGen.create(fromNode, vectorLength);
                    case I8:
                        return LLVMUnsignedCastToI8VectorNodeGen.create(fromNode, vectorLength);
                    case I16:
                        return LLVMUnsignedCastToI16VectorNodeGen.create(fromNode, vectorLength);
                    case I32:
                        return LLVMUnsignedCastToI32VectorNodeGen.create(fromNode, vectorLength);
                    case I64:
                        return LLVMUnsignedCastToI64VectorNodeGen.create(fromNode, vectorLength);
                }
            }
        }

        throw unsupportedCast(targetType);
    }

    public static LLVMExpressionNode bitCast(BasicNodeFactory nodeFactory, LLVMExpressionNode fromNode, Type targetType) {
        // does a reinterpreting cast between pretty much anything as long as source and target have
        // the same bit width.
        if (targetType instanceof PrimitiveType) {
            switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                case I1:
                    return LLVMBitcastToI1NodeGen.create(fromNode);
                case I8:
                    return LLVMBitcastToI8NodeGen.create(fromNode);
                case I16:
                    return LLVMBitcastToI16NodeGen.create(fromNode);
                case I32:
                    return LLVMBitcastToI32NodeGen.create(fromNode);
                case I64:
                    return nodeFactory.createBitcastToI64(fromNode);
                case FLOAT:
                    return LLVMBitcastToFloatNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMBitcastToDoubleNodeGen.create(fromNode);
                case X86_FP80:
                    return LLVMBitcastToLLVM80BitFloatNodeGen.create(fromNode);
            }
        } else if (targetType instanceof PointerType || targetType instanceof FunctionType) {
            return LLVMToAddressNodeGen.create(fromNode);
        } else if (targetType instanceof VariableBitWidthType) {
            return LLVMBitcastToIVarNodeGen.create(fromNode, targetType.getBitSize());
        } else if (targetType instanceof VectorType) {
            VectorType vectorType = (VectorType) targetType;
            Type elemType = vectorType.getElementType();
            int vectorLength = vectorType.getNumberOfElements();
            if (elemType instanceof PrimitiveType) {
                switch (((PrimitiveType) elemType).getPrimitiveKind()) {
                    case I1:
                        return LLVMBitcastToI1VectorNodeGen.create(fromNode, vectorLength);
                    case I8:
                        return LLVMBitcastToI8VectorNodeGen.create(fromNode, vectorLength);
                    case I16:
                        return LLVMBitcastToI16VectorNodeGen.create(fromNode, vectorLength);
                    case I32:
                        return LLVMBitcastToI32VectorNodeGen.create(fromNode, vectorLength);
                    case I64:
                        return LLVMBitcastToI64VectorNodeGen.create(fromNode, vectorLength);
                    case FLOAT:
                        return LLVMBitcastToFloatVectorNodeGen.create(fromNode, vectorLength);
                    case DOUBLE:
                        return LLVMBitcastToDoubleVectorNodeGen.create(fromNode, vectorLength);
                }
            }
        }

        throw unsupportedCast(targetType);
    }

    private static AssertionError unsupportedCast(Type targetType) {
        throw new IllegalStateException("Parser Error: Cannot do convert to " + targetType);
    }

    private static int getBits(Type targetType) {
        return targetType.getBitSize();
    }
}
