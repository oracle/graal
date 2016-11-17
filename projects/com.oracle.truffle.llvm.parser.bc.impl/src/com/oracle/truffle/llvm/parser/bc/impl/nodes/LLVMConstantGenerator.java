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

package com.oracle.truffle.llvm.parser.bc.impl.nodes;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.context.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMFunctionLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAddressZeroNode;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstructionFactory;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.base.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.base.model.BCToTextConverter;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.floatingpoint.X86FP80Constant;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMLabelList;
import com.oracle.truffle.llvm.parser.base.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.parser.base.util.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.factories.LLVMCastsFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMComparisonFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMGetElementPtrFactory;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.Symbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.BinaryOperationConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.BlockAddressConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.CompareConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.floatingpoint.FloatingPointConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.GetElementPointerConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.StringConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.UndefinedConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.aggregate.VectorConstant;
import com.oracle.truffle.llvm.parser.base.model.types.ArrayType;
import com.oracle.truffle.llvm.parser.base.model.types.FloatingPointType;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.IntegerType;
import com.oracle.truffle.llvm.parser.base.model.types.PointerType;
import com.oracle.truffle.llvm.parser.base.model.types.StructureType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.model.types.VectorType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class LLVMConstantGenerator {

    private LLVMConstantGenerator() {
    }

    public static LLVMExpressionNode toConstantNode(Symbol value, int align, Function<GlobalValueSymbol, LLVMExpressionNode> variables, LLVMContext context, FrameSlot stackSlot, LLVMLabelList labels,
                    LLVMParserRuntime runtime) {
        if (value instanceof GlobalValueSymbol) {
            return variables.apply((GlobalValueSymbol) value);

        } else if (value instanceof FunctionDefinition || value instanceof FunctionDeclaration) {

            final FunctionType functionType = (FunctionType) value;
            final LLVMFunctionDescriptor.LLVMRuntimeType returnType = functionType.getReturnType().getRuntimeType();
            final boolean hasVarArgs = functionType.isVarArg();
            final LLVMFunctionDescriptor.LLVMRuntimeType[] paramTypes = new LLVMFunctionDescriptor.LLVMRuntimeType[functionType.getArgumentTypes().length];
            for (int i = 0; i < paramTypes.length; i++) {
                paramTypes[i] = functionType.getArgumentTypes()[i].getRuntimeType();
            }

            final LLVMFunction function = runtime.getNodeFactoryFacade().createAndRegisterFunctionDescriptor(functionType.getName(), returnType, hasVarArgs, paramTypes);
            return runtime.getNodeFactoryFacade().createLiteral(function, LLVMBaseType.FUNCTION_ADDRESS);

        } else if (value instanceof StringConstant) {
            final StringConstant constant = (StringConstant) value;
            final String chars = constant.getString();

            final NodeFactoryFacade factoryFacade = runtime.getNodeFactoryFacade();
            final List<LLVMExpressionNode> values = new ArrayList<>(chars.length());
            for (int i = 0; i < chars.length(); i++) {
                values.add(factoryFacade.createLiteral((byte) chars.charAt(i), LLVMBaseType.I8));
            }
            if (constant.isCString()) {
                values.add(factoryFacade.createLiteral((byte) 0, LLVMBaseType.I8));
            }

            return factoryFacade.createArrayLiteral(values, constant.getType());

        } else if (value instanceof ArrayConstant) {
            return toArrayConstant((ArrayConstant) value, align, variables, context, stackSlot, labels, runtime);

        } else if (value instanceof StructureConstant) {
            return toStructureConstant((StructureConstant) value, align, variables, context, stackSlot, labels, runtime);

        } else if (value instanceof NullConstant) {
            return LLVMConstantGenerator.toConstantZeroNode(value.getType(), context, stackSlot, runtime);

        } else if (value instanceof UndefinedConstant) {
            return LLVMConstantGenerator.toConstantZeroNode(value.getType(), context, stackSlot, runtime);

        } else if (value instanceof BinaryOperationConstant) {
            final BinaryOperationConstant operation = (BinaryOperationConstant) value;
            final LLVMExpressionNode lhs = toConstantNode(operation.getLHS(), align, variables, context, stackSlot, labels, runtime);
            final LLVMExpressionNode rhs = toConstantNode(operation.getRHS(), align, variables, context, stackSlot, labels, runtime);
            final LLVMBaseType type = operation.getType().getLLVMBaseType();
            return LLVMNodeGenerator.generateBinaryOperatorNode(operation.getOperator(), type, lhs, rhs);

        } else if (value instanceof CastConstant) {
            final CastConstant cast = (CastConstant) value;
            final LLVMConversionType type = LLVMBitcodeTypeHelper.toConversionType(cast.getOperator());
            final LLVMExpressionNode fromNode = toConstantNode(cast.getValue(), align, variables, context, stackSlot, labels, runtime);
            final LLVMBaseType from = cast.getValue().getType().getLLVMBaseType();
            final LLVMBaseType to = cast.getType().getLLVMBaseType();
            return LLVMCastsFactory.cast(fromNode, to, from, type);

        } else if (value instanceof CompareConstant) {
            final CompareConstant compare = (CompareConstant) value;
            final LLVMExpressionNode lhs = toConstantNode(compare.getLHS(), align, variables, context, stackSlot, labels, runtime);
            final LLVMExpressionNode rhs = toConstantNode(compare.getRHS(), align, variables, context, stackSlot, labels, runtime);
            return LLVMComparisonFactory.toCompareNode(compare.getOperator(), compare.getLHS().getType(), lhs, rhs);

        } else if (value instanceof GetElementPointerConstant) {
            return toGetElementPointerConstant((GetElementPointerConstant) value, align, variables, context, stackSlot, labels, runtime);

        } else if (value instanceof IntegerConstant) {
            final IntegerConstant constant = (IntegerConstant) value;
            final Type type = value.getType();
            final String stringValue = String.valueOf(constant.getValue());
            return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(stringValue, type.getLLVMBaseType(), type);

        } else if (value instanceof FloatingPointConstant) {
            final FloatingPointConstant constant = (FloatingPointConstant) value;
            return toFloatingPointConstant(constant, runtime);

        } else if (value instanceof BlockAddressConstant) {
            return toBlockAddressConstant((BlockAddressConstant) value, labels, runtime);

        } else if (value instanceof VectorConstant) {
            final VectorConstant constant = (VectorConstant) value;

            final List<LLVMExpressionNode> values = new ArrayList<>();
            for (int i = 0; i < constant.getLength(); i++) {
                values.add(toConstantNode(constant.getElement(i), align, variables, context, stackSlot, labels, runtime));
            }

            final LLVMExpressionNode target = runtime.allocateVectorResult(constant.getType());
            return runtime.getNodeFactoryFacade().createVectorLiteralNode(values, target, constant.getType().getLLVMBaseType());

        } else {
            throw new UnsupportedOperationException("Unsupported Constant: " + value);
        }
    }

    private static LLVMExpressionNode toArrayConstant(ArrayConstant array, int align, Function<GlobalValueSymbol, LLVMExpressionNode> variables, LLVMContext context, FrameSlot stackSlot,
                    LLVMLabelList labels, LLVMParserRuntime runtime) {

        final List<LLVMExpressionNode> values = new ArrayList<>(array.getElementCount());
        for (int i = 0; i < array.getElementCount(); i++) {
            values.add(toConstantNode(array.getElement(i), align, variables, context, stackSlot, labels, runtime));
        }
        final Type arrayType = array.getType();
        return runtime.getNodeFactoryFacade().createArrayLiteral(values, arrayType);
    }

    private static LLVMExpressionNode toBlockAddressConstant(BlockAddressConstant blockAddressConstant, LLVMLabelList labels, LLVMParserRuntime runtime) {
        final FunctionDefinition function = blockAddressConstant.getFunction();
        final Map<String, Integer> innerLabels = labels.labels(function.getName());
        for (Map.Entry<String, Integer> labelEntry : innerLabels.entrySet()) {
            if (labelEntry.getValue().equals(blockAddressConstant.getBlock())) {
                String blockName = labelEntry.getKey();
                for (int i = 0; i < function.getBlockCount(); i++) {
                    if (function.getBlock(i).getName().equals(blockName)) {
                        return runtime.getNodeFactoryFacade().createLiteral(LLVMAddress.fromLong(i), LLVMBaseType.ADDRESS);
                    }
                }
            }
        }

        throw new AssertionError("Could not find Block: " + blockAddressConstant.getBlock());
    }

    static LLVMExpressionNode toFloatingPointConstant(FloatingPointConstant constant, LLVMParserRuntime runtime) {
        if (constant instanceof X86FP80Constant) {
            // TODO implement getStringValue() in X86FP80Constant correctly
            return new LLVMSimpleLiteralNode.LLVM80BitFloatLiteralNode(LLVM80BitFloat.fromBytes(((X86FP80Constant) constant).getValue()));
        }

        final Type type = constant.getType();
        final String stringValue = constant.getStringValue();
        return runtime.getNodeFactoryFacade().createSimpleConstantNoArray(stringValue, type.getLLVMBaseType(), type);
    }

    private static LLVMExpressionNode toGetElementPointerConstant(GetElementPointerConstant constant, int align, Function<GlobalValueSymbol, LLVMExpressionNode> variables, LLVMContext context,
                    FrameSlot stackSlot, LLVMLabelList labels, LLVMParserRuntime runtime) {
        LLVMAddressNode currentAddress = (LLVMAddressNode) toConstantNode(constant.getBasePointer(), align, variables, context, stackSlot, labels, runtime);
        Type currentType = constant.getBasePointer().getType();
        Type parentType = null;
        int currentOffset = 0;

        for (final Symbol index : constant.getIndices()) {
            final Integer indexVal = LLVMNodeGenerator.evaluateIntegerConstant(index);
            if (indexVal == null) {
                throw new IllegalStateException("Invalid index: " + index);
            }

            currentOffset += runtime.getIndexOffset(indexVal, currentType);
            parentType = currentType;
            currentType = currentType.getIndexType(indexVal);
        }

        if (currentType != null && !((parentType instanceof StructureType) && (((StructureType) parentType).isPacked()))) {
            currentOffset += runtime.getBytePadding(currentOffset, currentType);
        }

        if (currentOffset != 0) {
            currentAddress = LLVMGetElementPtrFactory.create(LLVMBaseType.I32, currentAddress, new LLVMSimpleLiteralNode.LLVMI32LiteralNode(1), currentOffset);
        }

        return currentAddress;
    }

    private static LLVMStructWriteNode createStructWriteNode(LLVMExpressionNode parsedConstant, LLVMBaseType baseType, int byteSize) {
        switch (baseType) {
            case I1:
                return new StructLiteralNode.LLVMI1StructWriteNode((LLVMI1Node) parsedConstant);
            case I8:
                return new StructLiteralNode.LLVMI8StructWriteNode((LLVMI8Node) parsedConstant);
            case I16:
                return new StructLiteralNode.LLVMI16StructWriteNode((LLVMI16Node) parsedConstant);
            case I32:
                return new StructLiteralNode.LLVMI32StructWriteNode((LLVMI32Node) parsedConstant);
            case I64:
                return new StructLiteralNode.LLVMI64StructWriteNode((LLVMI64Node) parsedConstant);
            case FLOAT:
                return new StructLiteralNode.LLVMFloatStructWriteNode((LLVMFloatNode) parsedConstant);
            case DOUBLE:
                return new StructLiteralNode.LLVMDoubleStructWriteNode((LLVMDoubleNode) parsedConstant);
            case X86_FP80:
                return new StructLiteralNode.LLVM80BitFloatStructWriteNode((LLVM80BitFloatNode) parsedConstant);
            case ARRAY:
            case STRUCT:
                if (byteSize == 0) {
                    return new StructLiteralNode.LLVMEmptyStructWriteNode();
                } else {
                    return new StructLiteralNode.LLVMCompoundStructWriteNode((LLVMAddressNode) parsedConstant, byteSize);
                }
            case ADDRESS:
                return new StructLiteralNode.LLVMAddressStructWriteNode((LLVMAddressNode) parsedConstant);
            case FUNCTION_ADDRESS:
                return new StructLiteralNode.LLVMFunctionStructWriteNode((LLVMFunctionNode) parsedConstant);
            default:
                throw new AssertionError("Invalid BaseType for StructWriteNode: " + baseType);
        }
    }

    private static LLVMExpressionNode toStructureConstant(StructureConstant constant, int align, Function<GlobalValueSymbol, LLVMExpressionNode> variables, LLVMContext context, FrameSlot stackSlot,
                    LLVMLabelList labels, LLVMParserRuntime runtime) {

        final int elementCount = constant.getElementCount();

        final boolean packed = constant.isPacked();
        final int[] offsets = new int[elementCount];
        final LLVMStructWriteNode[] nodes = new LLVMStructWriteNode[elementCount];

        final StructureType structureType = (StructureType) constant.getType();
        final int structSize = runtime.getByteSize(structureType);
        final int structAlignment = runtime.getByteAlignment(structureType);
        final LLVMAddressNode allocation = LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(structSize, structAlignment, context, stackSlot);

        int currentOffset = 0;
        for (int i = 0; i < elementCount; i++) {
            final Type elementType = constant.getElementType(i);
            if (!packed) {
                currentOffset += runtime.getBytePadding(currentOffset, elementType);
            }
            offsets[i] = currentOffset;
            final int byteSize = runtime.getByteSize(elementType);
            final LLVMExpressionNode resolvedConstant = toConstantNode(constant.getElement(i), align, variables, context, stackSlot, labels, runtime);
            nodes[i] = createStructWriteNode(resolvedConstant, elementType.getLLVMBaseType(), byteSize);
            currentOffset += byteSize;

        }

        return new StructLiteralNode(offsets, nodes, allocation);

        // final Type[] types = new Type[elementCount];
        // final LLVMExpressionNode[] resolvedConstants = new LLVMExpressionNode[elementCount];
        // for (int i = 0; i < constant.getElementCount(); i++) {
        // types[i] = constant.getElementType(i);
        // resolvedConstants[i] = toConstantNode(constant.getElement(i), align, variables, context,
        // stackSlot, labels, runtime);
        // }
        // return runtime.getNodeFactoryFacade().createStructureConstantNode(constant.getType(),
        // packed, types, resolvedConstants);
    }

    private static LLVMExpressionNode toStructZeroNode(StructureType structureType, LLVMContext context, FrameSlot stackSlot, LLVMParserRuntime runtime) {
        final int size = runtime.getByteSize(structureType);
        if (size == 0) {
            final LLVMAddress minusOneNode = LLVMAddress.fromLong(-1);
            return runtime.getNodeFactoryFacade().createLiteral(minusOneNode, LLVMBaseType.ADDRESS);
        } else {
            final int alignment = runtime.getByteAlignment(structureType);
            // final LLVMExpressionNode target =
            // runtime.allocateFunctionLifetime(LLVMToBitcodeAdapter.unresolveType(structureType),
            // size, alignment);
            // return runtime.getNodeFactoryFacade().createZeroNode(target, size);

            final LLVMAddressNode addressNode = LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(size, alignment, context, stackSlot);
            return new LLVMAddressZeroNode(addressNode, size);
        }
    }

    private static LLVMExpressionNode toArrayZeroNode(ArrayType type, LLVMParserRuntime runtime) {
        final int size = runtime.getByteSize(type);
        if (size == 0) {
            return null;
        } else {
            final LLVMExpressionNode target = runtime.allocateFunctionLifetime(BCToTextConverter.convert(type), runtime.getByteSize(type), runtime.getByteAlignment(type));
            return runtime.getNodeFactoryFacade().createZeroNode(target, size);
        }
    }

    public static LLVMExpressionNode toConstantZeroNode(Type type, LLVMContext context, FrameSlot stack, LLVMParserRuntime runtime) {
        if (type instanceof IntegerType) {
            if (type.getBits() == 1) {
                return runtime.getNodeFactoryFacade().createSimpleConstantNoArray("false", LLVMBaseType.I1, type);
            } else {
                return runtime.getNodeFactoryFacade().createSimpleConstantNoArray("0", type.getLLVMBaseType(), type);
            }

        } else if (type instanceof FloatingPointType) {

            final FloatingPointType floatingPointType = (FloatingPointType) type;
            if (floatingPointType == FloatingPointType.X86_FP80) {
                // TODO find the correct String for this case
                return new LLVMSimpleLiteralNode.LLVM80BitFloatLiteralNode(LLVM80BitFloat.fromInt(0));
            } else {
                return runtime.getNodeFactoryFacade().createSimpleConstantNoArray("0.0", floatingPointType.getLLVMBaseType(), floatingPointType);
            }

        } else if (type instanceof PointerType) {
            if (((PointerType) type).getPointeeType() instanceof FunctionType) {
                final LLVMFunctionDescriptor functionDescriptor = (LLVMFunctionDescriptor) context.getFunctionRegistry().createZeroFunctionDescriptor();
                return LLVMFunctionLiteralNodeGen.create(functionDescriptor);
            } else {
                return new LLVMSimpleLiteralNode.LLVMAddressLiteralNode(LLVMAddress.fromLong(0));
            }

        } else if (type instanceof ArrayType) {
            return toArrayZeroNode((ArrayType) type, runtime);

        } else if (type instanceof VectorType) {
            final VectorType vectorType = (VectorType) type.getType();
            final int nrElements = vectorType.getLength();
            final LLVMExpressionNode target = runtime.allocateVectorResult(vectorType);
            final LLVMBaseType baseType = vectorType.getLLVMBaseType();
            return runtime.getNodeFactoryFacade().createZeroVectorInitializer(nrElements, target, baseType);

            // final LLVMAddressNode target =
            // LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(runtime.getByteSize(type.getType()),
            // runtime.getByteAlignment(type.getType()), context,
            // stack);
            // final LLVMExpressionNode[] zeroes = new LLVMExpressionNode[vectorType.getLength()];
            // Arrays.fill(zeroes, toConstantZeroNode(vectorType.getElementType(), context, stack,
            // runtime));
            // return LLVMLiteralFactory.createVectorLiteralNode(Arrays.asList(zeroes), target,
            // vectorType.getLLVMBaseType());

        } else if (type instanceof FunctionType) {
            final LLVMFunctionDescriptor functionDescriptor = (LLVMFunctionDescriptor) context.getFunctionRegistry().createFunctionDescriptor("<zero function>",
                            LLVMFunctionDescriptor.LLVMRuntimeType.ILLEGAL,
                            new LLVMFunctionDescriptor.LLVMRuntimeType[0], false);
            return LLVMFunctionLiteralNodeGen.create(functionDescriptor);

        } else if (type instanceof StructureType) {
            final StructureType structureType = (StructureType) type;
            return toStructZeroNode(structureType, context, stack, runtime);

        } else {
            throw new AssertionError("Unsupported Type for Zero Constant: " + type);
        }
    }
}
