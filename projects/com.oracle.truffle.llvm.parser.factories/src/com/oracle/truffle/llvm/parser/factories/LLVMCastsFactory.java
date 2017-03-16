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
import com.oracle.truffle.llvm.nodes.cast.LLVMTo80BitFloatingNodeFactory.LLVMSignedToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMTo80BitFloatingNodeFactory.LLVMUnsignedToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToAddressNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToDoubleNodeFactory.LLVMToDoubleBitNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToDoubleNodeFactory.LLVMToDoubleNoZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToDoubleNodeFactory.LLVMToDoubleUnsignedNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToDoubleNodeFactory.LLVMToDoubleZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToFloatNodeFactory.LLVMToFloatBitNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToFloatNodeFactory.LLVMToFloatNoZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToFloatNodeFactory.LLVMToFloatUnsignedNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToFloatNodeFactory.LLVMToFloatZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToFunctionNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI16NodeFactory.LLVMToI16NoZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI16NodeFactory.LLVMToI16ZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI1NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI32NodeFactory.LLVMToI32BitNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI32NodeFactory.LLVMToI32NoZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI32NodeFactory.LLVMToI32ZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI32NodeFactory.LLVMToUnsignedI32NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI64NodeFactory.LLVMToI64BitNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI64NodeFactory.LLVMToI64NoZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI64NodeFactory.LLVMToI64ZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI64NodeFactory.LLVMToUnsignedI64NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI8NodeFactory.LLVMToI8NoZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI8NodeFactory.LLVMToI8ZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVarINodeFactory.LLVMToIVarNoZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToVarINodeFactory.LLVMToIVarZeroExtNodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMVectorToVectorNodeFactory.LLVMAnyVectorToI8VectorNodeGen;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;

final class LLVMCastsFactory {

    private LLVMCastsFactory() {
    }

    private Type targetType;
    @SuppressWarnings("unused") private Type fromType;
    private LLVMConversionType conv;
    private int bits;

    private LLVMCastsFactory(Type targetType, Type fromType, LLVMConversionType conv) {
        this.fromType = fromType;
        this.targetType = targetType;
        this.conv = conv;
        this.bits = 0;
        if (Type.isIntegerType(targetType)) {
            bits = targetType.getBitSize();
        }
    }

    private LLVMCastsFactory(Type targetType, LLVMConversionType conv, int bits) {
        this.fromType = null;
        this.targetType = targetType;
        this.conv = conv;
        this.bits = bits;
    }

    static LLVMExpressionNode cast(LLVMExpressionNode fromNode, Type targetType, Type fromType, LLVMConversionType conv) {
        if (fromNode == null || targetType == null || fromType == null || conv == null) {
            throw new AssertionError();
        }
        return cast(new LLVMCastsFactory(targetType, fromType, conv), fromType, fromNode);
    }

    private static LLVMExpressionNode cast(LLVMCastsFactory factory, Type fromType, LLVMExpressionNode fromNode) {
        if (fromType instanceof PrimitiveType) {
            return handlePrimitive(factory, fromType, fromNode);
        } else if (fromType instanceof VariableBitWidthType) {
            return factory.castFromIVar(fromNode);
        } else if (fromType instanceof VectorType) {
            return factory.castVector(fromNode);
        } else if (Type.isFunctionOrFunctionPointer(fromType)) {
            return factory.castFromFunctionPointer(fromNode);
        } else if (fromType instanceof PointerType) {
            return factory.castFromPointer(fromNode);
        } else {
            throw new AssertionError(fromType + " ==> " + factory.targetType);
        }
    }

    private static LLVMExpressionNode handlePrimitive(LLVMCastsFactory factory, Type fromType, LLVMExpressionNode fromNode) throws AssertionError {
        switch (((PrimitiveType) fromType).getPrimitiveKind()) {
            case I1:
                return factory.castFromI1(fromNode);
            case I8:
                return factory.castFromI8(fromNode);
            case I16:
                return factory.castFromI16(fromNode);
            case I32:
                return factory.castFromI32(fromNode);
            case I64:
                return factory.castFromI64(fromNode);
            case FLOAT:
                return factory.castFromFloat(fromNode);
            case DOUBLE:
                return factory.castFromDouble(fromNode);
            case X86_FP80:
                return factory.castFrom80BitFloat(fromNode);
            default:
                throw new AssertionError(fromType + " ==> " + factory.targetType);
        }
    }

    private LLVMExpressionNode castVector(LLVMExpressionNode fromNode) {
        if (targetType == PrimitiveType.I64) {
            return LLVMToI64NoZeroExtNodeGen.create(fromNode);
        } else if (targetType instanceof VectorType && ((VectorType) targetType).getElementType() == PrimitiveType.I8) {
            return LLVMAnyVectorToI8VectorNodeGen.create(fromNode);
        } else {
            throw new AssertionError(targetType + " " + conv);
        }
    }

    private LLVMExpressionNode castFromIVar(LLVMExpressionNode fromNode) {
        if (hasJavaCastSemantics()) {
            if (targetType == PrimitiveType.I8) {
                return LLVMToI8NoZeroExtNodeGen.create(fromNode);
            } else if (targetType == PrimitiveType.I32) {
                return LLVMToI32NoZeroExtNodeGen.create(fromNode);
            } else if (targetType == PrimitiveType.I64) {
                return LLVMToI64NoZeroExtNodeGen.create(fromNode);
            } else if (targetType instanceof VariableBitWidthType) {
                return LLVMToIVarNoZeroExtNodeGen.create(fromNode, bits == 0 ? targetType.getBitSize() : bits);
            } else {
                throw new AssertionError(targetType + " " + conv);
            }
        } else {
            if (targetType == PrimitiveType.I32) {
                return LLVMToI32ZeroExtNodeGen.create(fromNode);
            } else if (targetType == PrimitiveType.I64) {
                return LLVMToI64ZeroExtNodeGen.create(fromNode);
            } else {
                throw new AssertionError(targetType + " " + conv);
            }
        }
    }

    private LLVMExpressionNode castFrom80BitFloat(LLVMExpressionNode fromNode) {
        if (targetType == PrimitiveType.X86_FP80) {
            return fromNode;
        }
        switch (((PrimitiveType) targetType).getPrimitiveKind()) {
            case I8:
                return LLVMToI8NoZeroExtNodeGen.create(fromNode);
            case I32:
                return LLVMToI32NoZeroExtNodeGen.create(fromNode);
            case I64:
                return LLVMToI64NoZeroExtNodeGen.create(fromNode);
            case DOUBLE:
                return LLVMToDoubleNoZeroExtNodeGen.create(fromNode);
            case FLOAT:
                return LLVMToFloatNoZeroExtNodeGen.create(fromNode);
            default:
                throw new AssertionError(targetType);
        }
    }

    private LLVMExpressionNode castFromFunctionPointer(LLVMExpressionNode fromNode) {
        if (Type.isFunctionOrFunctionPointer(targetType)) {
            return fromNode;
        } else if (targetType instanceof PointerType) {
            return LLVMToAddressNodeGen.create(fromNode, targetType);
        } else if (targetType == PrimitiveType.I32) {
            return LLVMToI32NoZeroExtNodeGen.create(fromNode);
        } else if (targetType == PrimitiveType.I64) {
            return LLVMToI64NoZeroExtNodeGen.create(fromNode);
        }
        throw new AssertionError(targetType);
    }

    private LLVMExpressionNode castFromFloat(LLVMExpressionNode fromNode) {
        if (targetType == PrimitiveType.FLOAT) {
            return fromNode;
        }
        if (hasJavaCastSemantics() || conv == LLVMConversionType.ZERO_EXTENSION || conv == LLVMConversionType.FLOAT_TO_UINT) {
            switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                case I8:
                    return LLVMToI8NoZeroExtNodeGen.create(fromNode);
                case I16:
                    return LLVMToI16NoZeroExtNodeGen.create(fromNode);
                case I32:
                    return LLVMToI32NoZeroExtNodeGen.create(fromNode);
                case I64:
                    return LLVMToI64NoZeroExtNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMToDoubleNoZeroExtNodeGen.create(fromNode);
                case X86_FP80:
                    return LLVMSignedToLLVM80BitFloatNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.BITCAST) {
            if (targetType == PrimitiveType.I32) {
                return LLVMToI32BitNodeGen.create(fromNode);
            } else {
                throw new AssertionError(targetType);
            }
        } else {
            throw new AssertionError(targetType + " " + conv);
        }
    }

    private LLVMExpressionNode castFromI16(LLVMExpressionNode fromNode) {
        if (targetType == PrimitiveType.I16) {
            return fromNode;
        }
        if (hasJavaCastSemantics()) {
            if (targetType instanceof PrimitiveType) {
                switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                    case I1:
                        return LLVMToI1NodeGen.create(fromNode);
                    case I8:
                        return LLVMToI8NoZeroExtNodeGen.create(fromNode);
                    case I16:
                        return LLVMToI16NoZeroExtNodeGen.create(fromNode);
                    case I32:
                        return LLVMToI32NoZeroExtNodeGen.create(fromNode);
                    case I64:
                        return LLVMToI64NoZeroExtNodeGen.create(fromNode);
                    case FLOAT:
                        return LLVMToFloatNoZeroExtNodeGen.create(fromNode);
                    case DOUBLE:
                        return LLVMToDoubleNoZeroExtNodeGen.create(fromNode);
                    default:
                        throw new AssertionError(targetType + " " + conv);
                }
            } else if (targetType instanceof VariableBitWidthType) {
                return LLVMToIVarNoZeroExtNodeGen.create(fromNode, bits == 0 ? targetType.getBitSize() : bits);
            } else {
                throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.ZERO_EXTENSION) {
            switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                case I32:
                    return LLVMToI32ZeroExtNodeGen.create(fromNode);
                case I64:
                    return LLVMToI64ZeroExtNodeGen.create(fromNode);
                case FLOAT:
                    return LLVMToFloatZeroExtNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMToDoubleZeroExtNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

    private boolean hasJavaCastSemantics() {
        return conv == LLVMConversionType.SIGN_EXTENSION || conv == LLVMConversionType.TRUNC;
    }

    private LLVMExpressionNode castFromPointer(LLVMExpressionNode fromNode) {
        if (Type.isFunctionOrFunctionPointer(targetType)) {
            return LLVMToFunctionNodeGen.create(fromNode);
        } else if (targetType instanceof PointerType) {
            return fromNode;
        }

        if (hasJavaCastSemantics() || conv == LLVMConversionType.BITCAST) {
            if (targetType == PrimitiveType.I8) {
                return LLVMToI8NoZeroExtNodeGen.create(fromNode);
            } else if (targetType == PrimitiveType.I32) {
                return LLVMToI32NoZeroExtNodeGen.create(fromNode);
            } else if (targetType == PrimitiveType.I64) {
                return LLVMToI64NoZeroExtNodeGen.create(fromNode);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

    private LLVMExpressionNode castFromI64(LLVMExpressionNode fromNode) {
        if (targetType == PrimitiveType.I64) {
            return fromNode;
        }
        if (hasJavaCastSemantics()) {
            if (targetType instanceof PrimitiveType) {
                switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                    case I1:
                        return LLVMToI1NodeGen.create(fromNode);
                    case I8:
                        return LLVMToI8NoZeroExtNodeGen.create(fromNode);
                    case I16:
                        return LLVMToI16NoZeroExtNodeGen.create(fromNode);
                    case I32:
                        return LLVMToI32NoZeroExtNodeGen.create(fromNode);
                    case FLOAT:
                        return LLVMToFloatNoZeroExtNodeGen.create(fromNode);
                    case DOUBLE:
                        return LLVMToDoubleNoZeroExtNodeGen.create(fromNode);
                    case X86_FP80:
                        return LLVMSignedToLLVM80BitFloatNodeGen.create(fromNode);
                    default:
                        throw new AssertionError(targetType + " " + conv);
                }
            } else if (targetType instanceof PointerType) {
                return LLVMToAddressNodeGen.create(fromNode, targetType);
            } else {
                throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.ZERO_EXTENSION) {
            if (targetType instanceof PrimitiveType) {
                switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                    case FLOAT:
                        return LLVMToFloatUnsignedNodeGen.create(fromNode);
                    case DOUBLE:
                        return LLVMToDoubleUnsignedNodeGen.create(fromNode);
                    case X86_FP80:
                        return LLVMUnsignedToLLVM80BitFloatNodeGen.create(fromNode);
                    default:
                        throw new AssertionError(targetType + " " + conv);
                }
            } else if (targetType instanceof VariableBitWidthType) {
                return LLVMToIVarNoZeroExtNodeGen.create(fromNode, bits == 0 ? targetType.getBitSize() : bits);
            } else if (Type.isFunctionOrFunctionPointer(targetType)) {
                return LLVMToFunctionNodeGen.create(fromNode);
            } else if (targetType instanceof PointerType) {
                return LLVMToAddressNodeGen.create(fromNode, targetType);
            } else {
                throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.BITCAST) {
            if (targetType instanceof PrimitiveType) {
                switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                    case DOUBLE:
                        return LLVMToDoubleBitNodeGen.create(fromNode);
                    default:
                        throw new AssertionError(targetType);
                }
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

    private LLVMExpressionNode castFromI8(LLVMExpressionNode fromNode) {
        if (targetType == PrimitiveType.I8) {
            return fromNode;
        }
        if (hasJavaCastSemantics()) {
            if (targetType instanceof PrimitiveType) {
                switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                    case I1:
                        return LLVMToI1NodeGen.create(fromNode);
                    case I16:
                        return LLVMToI16NoZeroExtNodeGen.create(fromNode);
                    case I32:
                        return LLVMToI32NoZeroExtNodeGen.create(fromNode);
                    case I64:
                        return LLVMToI64NoZeroExtNodeGen.create(fromNode);
                    case FLOAT:
                        return LLVMToFloatNoZeroExtNodeGen.create(fromNode);
                    case DOUBLE:
                        return LLVMToDoubleNoZeroExtNodeGen.create(fromNode);
                    case X86_FP80:
                        return LLVMSignedToLLVM80BitFloatNodeGen.create(fromNode);
                    default:
                        throw new AssertionError(targetType + " " + conv);
                }
            } else if (targetType instanceof VariableBitWidthType) {
                return LLVMToIVarNoZeroExtNodeGen.create(fromNode, bits == 0 ? targetType.getBitSize() : bits);
            }
        } else if (conv == LLVMConversionType.ZERO_EXTENSION) {
            switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                case I16:
                    return LLVMToI16ZeroExtNodeGen.create(fromNode);
                case I32:
                    return LLVMToI32ZeroExtNodeGen.create(fromNode);
                case I64:
                    return LLVMToI64ZeroExtNodeGen.create(fromNode);
                case FLOAT:
                    return LLVMToFloatZeroExtNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMToDoubleZeroExtNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

    private LLVMExpressionNode castFromDouble(LLVMExpressionNode fromNode) {
        if (targetType == PrimitiveType.DOUBLE) {
            return fromNode;
        }
        if (hasJavaCastSemantics() || conv == LLVMConversionType.ZERO_EXTENSION) {
            switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                case I8:
                    return LLVMToI8NoZeroExtNodeGen.create(fromNode);
                case I16:
                    return LLVMToI16NoZeroExtNodeGen.create(fromNode);
                case I32:
                    return LLVMToI32NoZeroExtNodeGen.create(fromNode);
                case I64:
                    return LLVMToI64NoZeroExtNodeGen.create(fromNode);
                case FLOAT:
                    return LLVMToFloatNoZeroExtNodeGen.create(fromNode);
                case X86_FP80:
                    return LLVMSignedToLLVM80BitFloatNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.BITCAST) {
            switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                case I64:
                    return LLVMToI64BitNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.FLOAT_TO_UINT) {
            switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                case I8:
                    return LLVMToI8NoZeroExtNodeGen.create(fromNode);
                case I16:
                    return LLVMToI16NoZeroExtNodeGen.create(fromNode);
                case I32:
                    return LLVMToUnsignedI32NodeGen.create(fromNode);
                case I64:
                    return LLVMToUnsignedI64NodeGen.create(fromNode);
                case X86_FP80: // TODO fix the unsigned case, see the I32 case
                    return LLVMUnsignedToLLVM80BitFloatNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

    private LLVMExpressionNode castFromI32(LLVMExpressionNode fromNode) {
        if (targetType == PrimitiveType.I32) {
            return fromNode;
        }
        if (hasJavaCastSemantics()) {
            if (targetType instanceof PrimitiveType) {
                switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                    case I1:
                        return LLVMToI1NodeGen.create(fromNode);
                    case I8:
                        return LLVMToI8NoZeroExtNodeGen.create(fromNode);
                    case I16:
                        return LLVMToI16NoZeroExtNodeGen.create(fromNode);
                    case I64:
                        return LLVMToI64NoZeroExtNodeGen.create(fromNode);
                    case FLOAT:
                        return LLVMToFloatNoZeroExtNodeGen.create(fromNode);
                    case DOUBLE:
                        return LLVMToDoubleNoZeroExtNodeGen.create(fromNode);
                    case X86_FP80:
                        return LLVMSignedToLLVM80BitFloatNodeGen.create(fromNode);
                }
            } else if (targetType instanceof VariableBitWidthType) {
                return LLVMToIVarNoZeroExtNodeGen.create(fromNode, bits == 0 ? targetType.getBitSize() : bits);
            } else {
                throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.ZERO_EXTENSION) {
            if (targetType instanceof PrimitiveType) {
                switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                    case I64:
                        return LLVMToI64ZeroExtNodeGen.create(fromNode);
                    case FLOAT:
                        return LLVMToFloatUnsignedNodeGen.create(fromNode);
                    case DOUBLE:
                        return LLVMToDoubleUnsignedNodeGen.create(fromNode);
                    case X86_FP80:
                        return LLVMUnsignedToLLVM80BitFloatNodeGen.create(fromNode);
                    default:
                        throw new AssertionError(targetType + " " + conv);
                }
            } else if (targetType instanceof VariableBitWidthType) {
                return LLVMToIVarZeroExtNodeGen.create(fromNode, bits == 0 ? targetType.getBitSize() : bits);
            } else {
                throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.BITCAST) {
            if (targetType instanceof PrimitiveType) {
                switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                    case FLOAT:
                        return LLVMToFloatBitNodeGen.create(fromNode);
                    default:
                        throw new AssertionError(targetType + " " + conv);
                }
            } else {
                throw new AssertionError(targetType + " " + conv);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

    private LLVMExpressionNode castFromI1(LLVMExpressionNode fromNode) {
        if (targetType == PrimitiveType.I1) {
            return fromNode;
        }
        if (hasJavaCastSemantics()) {
            switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                case I8:
                    return LLVMToI8NoZeroExtNodeGen.create(fromNode);
                case I16:
                    return LLVMToI16NoZeroExtNodeGen.create(fromNode);
                case I32:
                    return LLVMToI32NoZeroExtNodeGen.create(fromNode);
                case I64:
                    return LLVMToI64NoZeroExtNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        } else if (conv == LLVMConversionType.ZERO_EXTENSION) {
            switch (((PrimitiveType) targetType).getPrimitiveKind()) {
                case I8:
                    return LLVMToI8ZeroExtNodeGen.create(fromNode);
                case I16:
                    return LLVMToI16ZeroExtNodeGen.create(fromNode);
                case I32:
                    return LLVMToI32ZeroExtNodeGen.create(fromNode);
                case I64:
                    return LLVMToI64ZeroExtNodeGen.create(fromNode);
                case DOUBLE:
                    return LLVMToDoubleZeroExtNodeGen.create(fromNode);
                default:
                    throw new AssertionError(targetType + " " + conv);
            }
        }
        throw new AssertionError(targetType + " " + conv);
    }

}
