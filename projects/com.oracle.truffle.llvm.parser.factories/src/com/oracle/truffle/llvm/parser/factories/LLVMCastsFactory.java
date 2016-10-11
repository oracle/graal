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

import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
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
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMFloatVectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI32VectorNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMI8VectorNode;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMTo80BitFloatingNodeFactory.LLVMDoubleToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMTo80BitFloatingNodeFactory.LLVMFloatToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMTo80BitFloatingNodeFactory.LLVMI32ToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMTo80BitFloatingNodeFactory.LLVMI32ToLLVM80BitFloatUnsignedNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMTo80BitFloatingNodeFactory.LLVMI64ToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMTo80BitFloatingNodeFactory.LLVMI64ToLLVM80BitFloatUnsignedNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMTo80BitFloatingNodeFactory.LLVMI8ToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToAddressNodeFactory.LLVMFunctionToAddressNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToAddressNodeFactory.LLVMI64ToAddressNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToDoubleNodeFactory.LLVM80BitFloatToDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToDoubleNodeFactory.LLVMFloatToDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToDoubleNodeFactory.LLVMI16ToDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToDoubleNodeFactory.LLVMI16ToDoubleZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToDoubleNodeFactory.LLVMI32ToDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToDoubleNodeFactory.LLVMI32ToDoubleUnsignedNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToDoubleNodeFactory.LLVMI64ToDoubleBitNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToDoubleNodeFactory.LLVMI64ToDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToDoubleNodeFactory.LLVMI64ToDoubleUnsignedNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToDoubleNodeFactory.LLVMI8ToDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToDoubleNodeFactory.LLVMI8ToDoubleZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFloatNodeFactory.LLVM80BitFloatToFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFloatNodeFactory.LLVMDoubleToFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFloatNodeFactory.LLVMI16ToFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFloatNodeFactory.LLVMI16ToFloatZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFloatNodeFactory.LLVMI32ToFloatBitNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFloatNodeFactory.LLVMI32ToFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFloatNodeFactory.LLVMI32ToFloatUnsignedNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFloatNodeFactory.LLVMI64ToFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFloatNodeFactory.LLVMI64ToFloatUnsignedNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFloatNodeFactory.LLVMI8ToFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFloatNodeFactory.LLVMI8ToFloatZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFunctionNodeFactory.LLVMAddressToFunctionNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToFunctionNodeFactory.LLVMI64ToFunctionNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI16NodeFactory.LLVMDoubleToI16NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI16NodeFactory.LLVMFloatToI16NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI16NodeFactory.LLVMI1ToI16NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI16NodeFactory.LLVMI1ToI16ZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI16NodeFactory.LLVMI32ToI16NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI16NodeFactory.LLVMI64ToI16NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI16NodeFactory.LLVMI8ToI16NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI16NodeFactory.LLVMI8ToI16ZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI1NodeFactory.LLVMI16ToI1NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI1NodeFactory.LLVMI32ToI1NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI1NodeFactory.LLVMI64ToI1NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI1NodeFactory.LLVMI8ToI1NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVM80BitFloatToI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMAddressToI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMDoubleToI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMDoubleToUnsignedI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMFloatToI32BitNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMFloatToI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMFunctionToI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMI16ToI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMI16ToI32ZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMI1ToI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMI1ToI32ZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMI64ToI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMI8ToI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMI8ToI32ZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMIVarBitToI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI32NodeFactory.LLVMIVarBitToI32ZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI64NodeFactory.LLVMAnyToI64NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI64NodeFactory.LLVMDoubleToI64BitCastNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI64NodeFactory.LLVMToI64ZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI8NodeFactory.LLVM80BitFloatToI8NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI8NodeFactory.LLVMAddressToI8NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI8NodeFactory.LLVMDoubleToI8NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI8NodeFactory.LLVMFloatToI8NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI8NodeFactory.LLVMI16ToI8NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI8NodeFactory.LLVMI1ToI8NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI8NodeFactory.LLVMI1ToI8ZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI8NodeFactory.LLVMI32ToI8NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI8NodeFactory.LLVMI64ToI8NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI8NodeFactory.LLVMIVarToI8NodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI8VectorNodeFactory.LLVMI32VectorToI8VectorNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToVarINodeFactory.LLVMI16ToIVarNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToVarINodeFactory.LLVMI32ToIVarNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToVarINodeFactory.LLVMI32ToIVarZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToVarINodeFactory.LLVMI64ToIVarNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToVarINodeFactory.LLVMI8ToIVarNodeGen;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToVarINodeFactory.LLVMIVarToIVarNodeGen;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.base.model.types.IntegerType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;

public final class LLVMCastsFactory {

    private LLVMCastsFactory() {
    }

    private LLVMBaseType targetType;
    // TODO: we need these types later
    private Type resolvedType;
    @SuppressWarnings("unused") private Type fromType;
    private LLVMConversionType conv;
    private int bits;

    private LLVMCastsFactory(Type targetType, Type fromType, LLVMConversionType conv) {
        this.fromType = fromType;
        this.targetType = targetType.getLLVMBaseType();
        this.resolvedType = targetType;
        this.conv = conv;
        this.bits = 0;
        if (targetType instanceof IntegerType) {
            bits = targetType.getBits();
        }
    }

    private LLVMCastsFactory(LLVMBaseType targetType, LLVMConversionType conv, int bits) {
        this.fromType = null;
        this.targetType = targetType;
        this.resolvedType = null;
        this.conv = conv;
        this.bits = bits;
    }

    public static LLVMExpressionNode cast(LLVMExpressionNode fromNode, Type targetType, Type fromType, LLVMConversionType conv) {
        if (fromNode == null || targetType == null || fromType == null || conv == null) {
            throw new AssertionError();
        }
        return cast(new LLVMCastsFactory(targetType, fromType, conv), fromType.getLLVMBaseType(), fromNode);
    }

    public static LLVMExpressionNode cast(LLVMExpressionNode fromNode, LLVMBaseType targetType, LLVMBaseType fromType, LLVMConversionType conv) {
        return cast(fromNode, targetType, fromType, conv, 0);
    }

    public static LLVMExpressionNode cast(LLVMExpressionNode fromNode, LLVMBaseType targetType, LLVMBaseType fromType, LLVMConversionType conv, int bits) {
        if (fromNode == null || targetType == null || fromType == null || conv == null) {
            throw new AssertionError();
        }
        return cast(new LLVMCastsFactory(targetType, conv, bits), fromType, fromNode);
    }

    private static LLVMExpressionNode cast(LLVMCastsFactory factory, LLVMBaseType fromType, LLVMExpressionNode fromNode) {
        switch (fromType) {
            case I1:
                return factory.castFromI1((LLVMI1Node) fromNode);
            case I8:
                return factory.castFromI8((LLVMI8Node) fromNode);
            case I16:
                return factory.castFromI16((LLVMI16Node) fromNode);
            case I32:
                return factory.castFromI32((LLVMI32Node) fromNode);
            case I_VAR_BITWIDTH:
                return factory.castFromIVar((LLVMIVarBitNode) fromNode);
            case I64:
                return factory.castFromI64((LLVMI64Node) fromNode);
            case FLOAT:
                return factory.castFromFloat((LLVMFloatNode) fromNode);
            case DOUBLE:
                return factory.castFromDouble((LLVMDoubleNode) fromNode);
            case X86_FP80:
                return factory.castFrom80BitFloat((LLVM80BitFloatNode) fromNode);
            case ADDRESS:
                return factory.castFromPointer((LLVMAddressNode) fromNode);
            case FUNCTION_ADDRESS:
                return factory.castFromFunctionPointer((LLVMFunctionNode) fromNode);
            case FLOAT_VECTOR:
                return factory.castFromFloatVector((LLVMFloatVectorNode) fromNode);
            default:
                throw new AssertionError(fromType);
        }
    }

    private LLVMExpressionNode castFromFloatVector(LLVMFloatVectorNode fromNode) {
        switch (targetType) {
            case I64:
                return LLVMAnyToI64NodeGen.create(fromNode);
            default:
                throw new AssertionError(targetType + " " + conv);
        }
    }

    public static LLVMExpressionNode castVector(LLVMBaseType fromType, LLVMExpressionNode fromNode, LLVMAddressNode target, LLVMBaseType targetType, LLVMConversionType conv) {
        if (fromNode == null || targetType == null || fromType == null || conv == null) {
            throw new AssertionError();
        }
        LLVMCastsFactory factory = new LLVMCastsFactory(targetType, conv, 0);
        switch (fromType) {
            case I8_VECTOR:
                return factory.castFromI8Vector(target, (LLVMI8VectorNode) fromNode);
            case I32_VECTOR:
                return factory.castFromI32Vector(target, (LLVMI32VectorNode) fromNode);
            case I1_VECTOR:
            case I16_VECTOR:
            case I64_VECTOR:
            case FLOAT_VECTOR:
            case DOUBLE_VECTOR:
            default:
                throw new LLVMUnsupportedException(UnsupportedReason.VECTOR_CAST);
        }
    }

    private LLVMExpressionNode castFromIVar(LLVMIVarBitNode fromNode) {
        if (hasJavaCastSemantics()) {
            switch (targetType) {
                case I8:
                    return LLVMIVarToI8NodeGen.create(fromNode);
                case I32:
                    return LLVMIVarBitToI32NodeGen.create(fromNode);
                case I64:
                    return LLVMAnyToI64NodeGen.create(fromNode);
                case I_VAR_BITWIDTH:
                    return LLVMIVarToIVarNodeGen.create(fromNode, bits == 0 ? resolvedType.getBits() : bits);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else {
            switch (targetType) {
                case I32:
                    return LLVMIVarBitToI32ZeroExtNodeGen.create(fromNode);
                case I64:
                    return LLVMToI64ZeroExtNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        }
    }

    private LLVMExpressionNode castFrom80BitFloat(LLVM80BitFloatNode fromNode) {
        if (targetType == LLVMBaseType.X86_FP80) {
            return fromNode;
        }
        switch (targetType) {
            case I8:
                return LLVM80BitFloatToI8NodeGen.create(fromNode);
            case I32:
                return LLVM80BitFloatToI32NodeGen.create(fromNode);
            case I64:
                return LLVMAnyToI64NodeGen.create(fromNode);
            case DOUBLE:
                return LLVM80BitFloatToDoubleNodeGen.create(fromNode);
            case FLOAT:
                return LLVM80BitFloatToFloatNodeGen.create(fromNode);
            default:
                throw new AssertionError(targetType);
        }
    }

    private LLVMExpressionNode castFromI8Vector(@SuppressWarnings("unused") LLVMAddressNode target, LLVMI8VectorNode fromNode) {
        if (targetType == LLVMBaseType.I8_VECTOR) {
            return fromNode;
        }
        switch (targetType) {
            default:
                throw new LLVMUnsupportedException(UnsupportedReason.VECTOR_CAST);
        }
    }

    private LLVMExpressionNode castFromI32Vector(LLVMAddressNode target, LLVMI32VectorNode fromNode) {
        if (targetType == LLVMBaseType.I32_VECTOR) {
            return fromNode;
        }
        switch (targetType) {
            case I8_VECTOR:
                return LLVMI32VectorToI8VectorNodeGen.create(target, fromNode);
            default:
                throw new LLVMUnsupportedException(UnsupportedReason.VECTOR_CAST);
        }
    }

    private LLVMExpressionNode castFromFunctionPointer(LLVMFunctionNode fromNode) {
        if (targetType == LLVMBaseType.FUNCTION_ADDRESS) {
            return fromNode;
        }
        switch (targetType) {
            case FUNCTION_ADDRESS:
                return fromNode;
            case I32:
                return LLVMFunctionToI32NodeGen.create(fromNode);
            case I64:
                return LLVMAnyToI64NodeGen.create(fromNode);
            case ADDRESS:
                return LLVMFunctionToAddressNodeGen.create(fromNode);
            default:
                throw new AssertionError(targetType);
        }
    }

    private LLVMExpressionNode castFromFloat(LLVMFloatNode fromNode) {
        if (targetType == LLVMBaseType.FLOAT) {
            return fromNode;
        }
        if (hasJavaCastSemantics() || conv == LLVMConversionType.ZERO_EXTENSION || conv == LLVMConversionType.FLOAT_TO_UINT) {
            switch (targetType) {
                case I8:
                    return LLVMFloatToI8NodeGen.create(fromNode);
                case I16:
                    return LLVMFloatToI16NodeGen.create(fromNode);
                case I32:
                    return LLVMFloatToI32NodeGen.create(fromNode);
                case I64:
                    return LLVMAnyToI64NodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMFloatToDoubleNodeGen.create(fromNode);
                case X86_FP80:
                    return LLVMFloatToLLVM80BitFloatNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.BITCAST) {
            switch (targetType) {
                case I32:
                    return LLVMFloatToI32BitNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType);
            }
        } else {
            throw new AssertionError(targetType + " " + conv);
        }
    }

    private LLVMExpressionNode castFromI16(LLVMI16Node fromNode) {
        if (targetType == LLVMBaseType.I16) {
            return fromNode;
        }
        if (hasJavaCastSemantics()) {
            switch (targetType) {
                case I1:
                    return LLVMI16ToI1NodeGen.create(fromNode);
                case I8:
                    return LLVMI16ToI8NodeGen.create(fromNode);
                case I16:
                    return LLVMI16ToI1NodeGen.create(fromNode);
                case I32:
                    return LLVMI16ToI32NodeGen.create(fromNode);
                case I64:
                    return LLVMAnyToI64NodeGen.create(fromNode);
                case I_VAR_BITWIDTH:
                    return LLVMI16ToIVarNodeGen.create(fromNode, bits == 0 ? resolvedType.getBits() : bits);
                case FLOAT:
                    return LLVMI16ToFloatNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMI16ToDoubleNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.ZERO_EXTENSION) {
            switch (targetType) {
                case I32:
                    return LLVMI16ToI32ZeroExtNodeGen.create(fromNode);
                case I64:
                    return LLVMToI64ZeroExtNodeGen.create(fromNode);
                case FLOAT:
                    return LLVMI16ToFloatZeroExtNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMI16ToDoubleZeroExtNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

    private boolean hasJavaCastSemantics() {
        return conv == LLVMConversionType.SIGN_EXTENSION || conv == LLVMConversionType.TRUNC;
    }

    private LLVMExpressionNode castFromPointer(LLVMAddressNode fromNode) {
        if (targetType == LLVMBaseType.ADDRESS) {
            return fromNode;
        }
        if (hasJavaCastSemantics() || conv == LLVMConversionType.BITCAST) {
            switch (targetType) {
                case I8:
                    return LLVMAddressToI8NodeGen.create(fromNode);
                case I32:
                    return LLVMAddressToI32NodeGen.create(fromNode);
                case I64:
                    return LLVMAnyToI64NodeGen.create(fromNode);
                case ADDRESS:
                    // at the moment we still can directly cast from pointer to pointer (e.g. from
                    // I32* to I32Vector*)
                    return fromNode;
                case FUNCTION_ADDRESS:
                    return LLVMAddressToFunctionNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

    private LLVMExpressionNode castFromI64(LLVMI64Node fromNode) {
        if (targetType == LLVMBaseType.I64) {
            return fromNode;
        }
        if (hasJavaCastSemantics()) {
            switch (targetType) {
                case I1:
                    return LLVMI64ToI1NodeGen.create(fromNode);
                case I8:
                    return LLVMI64ToI8NodeGen.create(fromNode);
                case I16:
                    return LLVMI64ToI16NodeGen.create(fromNode);
                case I32:
                    return LLVMI64ToI32NodeGen.create(fromNode);
                case FLOAT:
                    return LLVMI64ToFloatNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMI64ToDoubleNodeGen.create(fromNode);
                case X86_FP80:
                    return LLVMI64ToLLVM80BitFloatNodeGen.create(fromNode);
                case ADDRESS:
                    return LLVMI64ToAddressNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.ZERO_EXTENSION) {
            switch (targetType) {
                case I_VAR_BITWIDTH:
                    return LLVMI64ToIVarNodeGen.create(fromNode, bits == 0 ? resolvedType.getBits() : bits);
                case FLOAT:
                    return LLVMI64ToFloatUnsignedNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMI64ToDoubleUnsignedNodeGen.create(fromNode);
                case X86_FP80:
                    return LLVMI64ToLLVM80BitFloatUnsignedNodeGen.create(fromNode);
                case ADDRESS:
                    return LLVMI64ToAddressNodeGen.create(fromNode);
                case FUNCTION_ADDRESS:
                    return LLVMI64ToFunctionNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.BITCAST) {
            switch (targetType) {
                case DOUBLE:
                    return LLVMI64ToDoubleBitNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

    private LLVMExpressionNode castFromI8(LLVMI8Node fromNode) {
        if (targetType == LLVMBaseType.I8) {
            return fromNode;
        }
        if (hasJavaCastSemantics()) {
            switch (targetType) {
                case I1:
                    return LLVMI8ToI1NodeGen.create(fromNode);
                case I16:
                    return LLVMI8ToI16NodeGen.create(fromNode);
                case I32:
                    return LLVMI8ToI32NodeGen.create(fromNode);
                case I64:
                    return LLVMAnyToI64NodeGen.create(fromNode);
                case I_VAR_BITWIDTH:
                    return LLVMI8ToIVarNodeGen.create(fromNode, bits == 0 ? resolvedType.getBits() : bits);
                case FLOAT:
                    return LLVMI8ToFloatNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMI8ToDoubleNodeGen.create(fromNode);
                case X86_FP80:
                    return LLVMI8ToLLVM80BitFloatNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.ZERO_EXTENSION) {
            switch (targetType) {
                case I16:
                    return LLVMI8ToI16ZeroExtNodeGen.create(fromNode);
                case I32:
                    return LLVMI8ToI32ZeroExtNodeGen.create(fromNode);
                case I64:
                    return LLVMToI64ZeroExtNodeGen.create(fromNode);
                case FLOAT:
                    return LLVMI8ToFloatZeroExtNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMI8ToDoubleZeroExtNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

    private LLVMExpressionNode castFromDouble(LLVMDoubleNode fromNode) {
        if (targetType == LLVMBaseType.DOUBLE) {
            return fromNode;
        }
        if (hasJavaCastSemantics() || conv == LLVMConversionType.ZERO_EXTENSION) {
            switch (targetType) {
                case I8:
                    return LLVMDoubleToI8NodeGen.create(fromNode);
                case I16:
                    return LLVMDoubleToI16NodeGen.create(fromNode);
                case I32:
                    return LLVMDoubleToI32NodeGen.create(fromNode);
                case I64:
                    return LLVMAnyToI64NodeGen.create(fromNode);
                case FLOAT:
                    return LLVMDoubleToFloatNodeGen.create(fromNode);
                case X86_FP80:
                    return LLVMDoubleToLLVM80BitFloatNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.BITCAST) {
            switch (targetType) {
                case I64:
                    return LLVMDoubleToI64BitCastNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.FLOAT_TO_UINT) {
            switch (targetType) {
                case I8:
                    return LLVMDoubleToI8NodeGen.create(fromNode);
                case I16:
                    return LLVMDoubleToI16NodeGen.create(fromNode);
                case I32:
                    return LLVMDoubleToUnsignedI32NodeGen.create(fromNode);
                case I64:
                    return LLVMAnyToI64NodeGen.create(fromNode);
                case X86_FP80: // TODO fix the unsigned case, see the I32 case
                    return LLVMDoubleToLLVM80BitFloatNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

    private LLVMExpressionNode castFromI32(LLVMI32Node fromNode) {
        if (targetType == LLVMBaseType.I32) {
            return fromNode;
        }
        if (hasJavaCastSemantics()) {
            switch (targetType) {
                case I1:
                    return LLVMI32ToI1NodeGen.create(fromNode);
                case I8:
                    return LLVMI32ToI8NodeGen.create(fromNode);
                case I16:
                    return LLVMI32ToI16NodeGen.create(fromNode);
                case I64:
                    return LLVMAnyToI64NodeGen.create(fromNode);
                case I_VAR_BITWIDTH:
                    return LLVMI32ToIVarNodeGen.create(fromNode, bits == 0 ? resolvedType.getBits() : bits);
                case FLOAT:
                    return LLVMI32ToFloatNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMI32ToDoubleNodeGen.create(fromNode);
                case X86_FP80:
                    return LLVMI32ToLLVM80BitFloatNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.ZERO_EXTENSION) {
            switch (targetType) {
                case I_VAR_BITWIDTH:
                    return LLVMI32ToIVarZeroExtNodeGen.create(fromNode, bits == 0 ? resolvedType.getBits() : bits);
                case I64:
                    return LLVMToI64ZeroExtNodeGen.create(fromNode);
                case FLOAT:
                    return LLVMI32ToFloatUnsignedNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMI32ToDoubleUnsignedNodeGen.create(fromNode);
                case X86_FP80:
                    return LLVMI32ToLLVM80BitFloatUnsignedNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.BITCAST) {
            switch (targetType) {
                case FLOAT:
                    return LLVMI32ToFloatBitNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

    private LLVMExpressionNode castFromI1(LLVMI1Node fromNode) {
        if (targetType == LLVMBaseType.I1) {
            return fromNode;
        }
        if (hasJavaCastSemantics()) {
            switch (targetType) {
                case I8:
                    return LLVMI1ToI8NodeGen.create(fromNode);
                case I16:
                    return LLVMI1ToI16NodeGen.create(fromNode);
                case I32:
                    return LLVMI1ToI32NodeGen.create(fromNode);
                case I64:
                    return LLVMAnyToI64NodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.ZERO_EXTENSION) {
            switch (targetType) {
                case I8:
                    return LLVMI1ToI8ZeroExtNodeGen.create(fromNode);
                case I16:
                    return LLVMI1ToI16ZeroExtNodeGen.create(fromNode);
                case I32:
                    return LLVMI1ToI32ZeroExtNodeGen.create(fromNode);
                case I64:
                    return LLVMToI64ZeroExtNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

}
