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
package com.oracle.truffle.llvm.parser.nodes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.parser.LLVMLabelList;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.SulongNodeFactory;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionKind;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BinaryOperationConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BlockAddressConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CompareConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.GetElementPointerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.MetadataConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.StringConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.UndefinedConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.VectorConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.DoubleConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.FloatConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.FloatingPointConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.X86FP80Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;
import com.oracle.truffle.llvm.runtime.types.symbols.ValueSymbol;

public final class LLVMSymbolResolver {

    private final LLVMParserRuntime runtime;

    private final LLVMLabelList labels;

    public LLVMSymbolResolver(LLVMLabelList labels, LLVMParserRuntime runtime) {
        this.labels = labels;
        this.runtime = runtime;
    }

    public static Integer evaluateIntegerConstant(Symbol constant) {
        if (constant instanceof IntegerConstant) {
            if (constant.getType() == PrimitiveType.I64) {
                LLVMLogger.info("GEP index overflow (still parse as int");
            }
            return (int) ((IntegerConstant) constant).getValue();

        } else if (constant instanceof BigIntegerConstant) {
            LLVMLogger.info("GEP index overflow (still parse as int");
            return ((BigIntegerConstant) constant).getValue().intValueExact();

        } else if (constant instanceof NullConstant) {
            return 0;

        } else {
            return null;
        }
    }

    private LLVMExpressionNode toMetaData(MetadataConstant constant) {
        // TODO: point to Metadata
        return runtime.getNodeFactoryFacade().createLiteral(runtime, constant.getValue(), PrimitiveType.I64);
    }

    public LLVMExpressionNode resolveElementPointer(Symbol base, List<Symbol> indices) {
        LLVMExpressionNode currentAddress = resolve(base);
        Type currentType = base.getType();

        for (int i = 0, indicesSize = indices.size(); i < indicesSize; i++) {
            final Symbol indexSymbol = indices.get(i);
            final Type indexType = indexSymbol.getType();

            final Integer indexInteger = LLVMSymbolResolver.evaluateIntegerConstant(indexSymbol);
            if (indexInteger == null) {
                // the index is determined at runtime
                if (currentType instanceof StructureType) {
                    // according to http://llvm.org/docs/LangRef.html#getelementptr-instruction
                    throw new IllegalStateException("Indices on structs must be constant integers!");
                }
                AggregateType aggregate = (AggregateType) currentType;
                final int indexedTypeLength = runtime.getIndexOffset(1, aggregate);
                currentType = aggregate.getElementType(1);
                final LLVMExpressionNode indexNode = resolve(indexSymbol);
                currentAddress = runtime.getNodeFactoryFacade().createTypedElementPointer(runtime, currentAddress, indexNode, indexedTypeLength, currentType);
            } else {
                // the index is a constant integer
                AggregateType aggregate = (AggregateType) currentType;
                final int addressOffset = runtime.getIndexOffset(indexInteger, aggregate);
                currentType = aggregate.getElementType(indexInteger);

                // creating a pointer inserts type information, this needs to happen for the address
                // computed by getelementptr even if it is the same as the basepointer
                if (addressOffset != 0 || i == indicesSize - 1) {
                    final LLVMExpressionNode indexNode;
                    if (indexType == PrimitiveType.I32) {
                        indexNode = runtime.getNodeFactoryFacade().createLiteral(runtime, 1, PrimitiveType.I32);
                    } else if (indexType == PrimitiveType.I64) {
                        indexNode = runtime.getNodeFactoryFacade().createLiteral(runtime, 1L, PrimitiveType.I64);
                    } else {
                        throw new AssertionError(indexType);
                    }
                    currentAddress = runtime.getNodeFactoryFacade().createTypedElementPointer(runtime, currentAddress, indexNode, addressOffset, currentType);
                }
            }
        }

        return currentAddress;
    }

    private LLVMExpressionNode toInteger(IntegerConstant constant) {
        final Type type = constant.getType();
        final long lVal = constant.getValue();
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, lVal != 0, type);
                case I8:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, (byte) lVal, type);
                case I16:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, (short) lVal, type);
                case I32:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, (int) lVal, type);
                case I64:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, lVal, type);
                default:
                    throw new UnsupportedOperationException("Unsupported IntegerConstant: " + type);
            }
        } else if (type instanceof VariableBitWidthType) {
            return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, lVal, type);
        } else {
            throw new UnsupportedOperationException("Unsupported IntegerConstant: " + type);
        }

    }

    private LLVMExpressionNode toBigInteger(BigIntegerConstant constant) {
        final Type type = constant.getType();
        if (type.getBitSize() <= Long.SIZE) {
            return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, constant.getValue().longValueExact(), type);
        } else {
            return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, constant.getValue(), type);
        }
    }

    private LLVMExpressionNode toFloat(FloatingPointConstant constant) {
        final Type type = constant.getType();
        switch (((PrimitiveType) type).getPrimitiveKind()) {
            case FLOAT:
                final float fVal = ((FloatConstant) constant).getValue();
                return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, fVal, type);
            case DOUBLE:
                final double dVal = ((DoubleConstant) constant).getValue();
                return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, dVal, type);
            case X86_FP80:
                final byte[] xVal = ((X86FP80Constant) constant).getValue();
                return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, xVal, type);
            default:
                throw new UnsupportedOperationException("Unsupported FloatingPointConstant: " + constant);
        }
    }

    private LLVMExpressionNode toStr(StringConstant constant) {
        final String chars = constant.getString();

        final SulongNodeFactory factoryFacade = runtime.getNodeFactoryFacade();
        final List<LLVMExpressionNode> values = new ArrayList<>(chars.length());
        for (int i = 0; i < chars.length(); i++) {
            values.add(factoryFacade.createLiteral(runtime, (byte) chars.charAt(i), PrimitiveType.I8));
        }
        if (constant.isCString()) {
            values.add(factoryFacade.createLiteral(runtime, (byte) 0, PrimitiveType.I8));
        }

        return factoryFacade.createArrayLiteral(runtime, values, constant.getType());
    }

    private LLVMExpressionNode toStruct(StructureConstant constant) {
        final int elementCount = constant.getElementCount();
        final Type[] types = new Type[elementCount];
        final LLVMExpressionNode[] constants = new LLVMExpressionNode[elementCount];
        for (int i = 0; i < elementCount; i++) {
            types[i] = constant.getElementType(i);
            constants[i] = resolve(constant.getElement(i));
        }
        return runtime.getNodeFactoryFacade().createStructureConstantNode(runtime, constant.getType(), constant.isPacked(), types, constants);
    }

    private LLVMExpressionNode toArray(ArrayConstant array) {

        final List<LLVMExpressionNode> values = new ArrayList<>(array.getElementCount());
        for (int i = 0; i < array.getElementCount(); i++) {
            values.add(resolve(array.getElement(i)));
        }
        final Type arrayType = array.getType();
        return runtime.getNodeFactoryFacade().createArrayLiteral(runtime, values, arrayType);
    }

    private LLVMExpressionNode toVector(VectorConstant constant) {
        final List<LLVMExpressionNode> values = new ArrayList<>();
        for (int i = 0; i < constant.getLength(); i++) {
            values.add(resolve(constant.getElement(i)));
        }

        return runtime.getNodeFactoryFacade().createVectorLiteralNode(runtime, values, constant.getType());
    }

    private LLVMExpressionNode toFunction(FunctionDefinition function) {
        final LLVMFunction llvmFunction = runtime.getNodeFactoryFacade().createAndRegisterFunctionDescriptor(runtime, function.getName(), function.getType());
        return runtime.getNodeFactoryFacade().createLiteral(runtime, llvmFunction, function.getType());
    }

    private LLVMExpressionNode toFunction(FunctionDeclaration function) {
        final LLVMFunction llvmFunction = runtime.getNodeFactoryFacade().createAndRegisterFunctionDescriptor(runtime, function.getName(), function.getType());
        return runtime.getNodeFactoryFacade().createLiteral(runtime, llvmFunction, function.getType());
    }

    private LLVMExpressionNode toBinaryOperation(BinaryOperationConstant operation) {
        final LLVMExpressionNode lhs = resolve(operation.getLHS());
        final LLVMExpressionNode rhs = resolve(operation.getRHS());
        final Type baseType = operation.getType();

        final LLVMArithmeticInstructionType arithmeticInstructionType = LLVMBitcodeTypeHelper.toArithmeticInstructionType(operation.getOperator());
        if (arithmeticInstructionType != null) {
            return runtime.getNodeFactoryFacade().createArithmeticOperation(runtime, lhs, rhs, arithmeticInstructionType, baseType);
        }

        final LLVMLogicalInstructionKind logicalInstructionType = LLVMBitcodeTypeHelper.toLogicalInstructionType(operation.getOperator());
        if (logicalInstructionType != null) {
            return runtime.getNodeFactoryFacade().createLogicalOperation(runtime, lhs, rhs, logicalInstructionType, baseType);
        }

        throw new UnsupportedOperationException("Unsupported Binary Operator: " + operation.getOperator());
    }

    private LLVMExpressionNode toBlockAddress(BlockAddressConstant constant) {
        final int val = labels.labels(constant.getFunction().getName()).get(constant.getInstructionBlock().getName());
        return runtime.getNodeFactoryFacade().createLiteral(runtime, LLVMAddress.fromLong(val), new PointerType(null));
    }

    private LLVMExpressionNode toElementPointer(GetElementPointerConstant constant) {
        return resolveElementPointer(constant.getBasePointer(), constant.getIndices());
    }

    private LLVMExpressionNode toComparison(CompareConstant compare) {
        final LLVMExpressionNode lhs = resolve(compare.getLHS());
        final LLVMExpressionNode rhs = resolve(compare.getRHS());
        return runtime.getNodeFactoryFacade().createComparison(runtime, compare.getOperator(), compare.getLHS().getType(), lhs, rhs);
    }

    private LLVMExpressionNode toCast(CastConstant constant) {
        final LLVMConversionType type = LLVMBitcodeTypeHelper.toConversionType(constant.getOperator());
        final LLVMExpressionNode fromNode = resolve(constant.getValue());
        return runtime.getNodeFactoryFacade().createCast(runtime, fromNode, constant.getType(), constant.getValue().getType(), type);
    }

    private LLVMExpressionNode toNullValue(Type type) {
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, false, type);
                case I8:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, (byte) 0, type);
                case I16:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, (short) 0, type);
                case I32:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, 0, type);
                case I64:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, 0L, type);
                case FLOAT:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, 0.0f, type);
                case DOUBLE:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, 0.0d, type);
                case X86_FP80:
                    return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, null, type);
                default:
                    throw new UnsupportedOperationException("Unsupported Type for Zero Constant: " + type);
            }
        } else if (type instanceof VariableBitWidthType) {
            return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, BigInteger.ZERO, type);
        } else if (type instanceof PointerType || type instanceof FunctionType) {
            return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(runtime, null, type);
        } else if (type instanceof StructureType) {
            final StructureType structureType = (StructureType) type;
            final int structSize = runtime.getByteSize(structureType);
            if (structSize == 0) {
                final LLVMAddress minusOneNode = LLVMAddress.fromLong(-1);
                return runtime.getNodeFactoryFacade().createLiteral(runtime, minusOneNode, new PointerType(structureType));
            } else {
                final int alignment = runtime.getByteAlignment(structureType);
                final LLVMExpressionNode addressnode = runtime.allocateFunctionLifetime(structureType, structSize, alignment);
                return runtime.getNodeFactoryFacade().createZeroNode(runtime, addressnode, structSize);
            }
        } else if (type instanceof ArrayType) {
            final int arraySize = runtime.getByteSize(type);
            if (arraySize == 0) {
                return null;
            } else {
                final LLVMExpressionNode target = runtime.allocateFunctionLifetime(type, runtime.getByteSize(type), runtime.getByteAlignment(type));
                return runtime.getNodeFactoryFacade().createZeroNode(runtime, target, arraySize);
            }
        } else if (type instanceof VectorType) {
            final VectorType vectorType = (VectorType) type;
            final int nrElements = vectorType.getNumberOfElements();
            return runtime.getNodeFactoryFacade().createZeroVectorInitializer(runtime, nrElements, (VectorType) type);
        } else {
            throw new UnsupportedOperationException("Unsupported Type for Zero Constant: " + type);
        }

    }

    public LLVMExpressionNode resolve(Symbol symbol) {
        if (symbol instanceof ValueInstruction || symbol instanceof FunctionParameter) {
            final FrameSlot slot = runtime.getMethodFrameDescriptor().findFrameSlot(((ValueSymbol) symbol).getName());
            return runtime.getNodeFactoryFacade().createFrameRead(runtime, symbol.getType(), slot);

        } else if (symbol instanceof GlobalValueSymbol) {
            return (LLVMExpressionNode) runtime.getGlobalAddress((GlobalValueSymbol) symbol);

        } else if (symbol instanceof NullConstant || symbol instanceof UndefinedConstant) {
            return toNullValue(symbol.getType());

        } else if (symbol instanceof IntegerConstant) {
            return toInteger((IntegerConstant) symbol);

        } else if (symbol instanceof BigIntegerConstant) {
            return toBigInteger((BigIntegerConstant) symbol);

        } else if (symbol instanceof FloatingPointConstant) {
            return toFloat((FloatingPointConstant) symbol);

        } else if (symbol instanceof StringConstant) {
            return toStr((StringConstant) symbol);

        } else if (symbol instanceof StructureConstant) {
            return toStruct((StructureConstant) symbol);

        } else if (symbol instanceof ArrayConstant) {
            return toArray((ArrayConstant) symbol);

        } else if (symbol instanceof VectorConstant) {
            return toVector((VectorConstant) symbol);

        } else if (symbol instanceof FunctionDefinition) {
            return toFunction((FunctionDefinition) symbol);

        } else if (symbol instanceof FunctionDeclaration) {
            return toFunction((FunctionDeclaration) symbol);

        } else if (symbol instanceof BinaryOperationConstant) {
            return toBinaryOperation((BinaryOperationConstant) symbol);

        } else if (symbol instanceof GetElementPointerConstant) {
            return toElementPointer((GetElementPointerConstant) symbol);

        } else if (symbol instanceof BlockAddressConstant) {
            return toBlockAddress((BlockAddressConstant) symbol);

        } else if (symbol instanceof CompareConstant) {
            return toComparison((CompareConstant) symbol);

        } else if (symbol instanceof CastConstant) {
            return toCast((CastConstant) symbol);

        } else if (symbol instanceof MetadataConstant) {
            return toMetaData((MetadataConstant) symbol);

        } else {
            throw new AssertionError("Cannot resolve symbol: " + symbol);
        }
    }
}
