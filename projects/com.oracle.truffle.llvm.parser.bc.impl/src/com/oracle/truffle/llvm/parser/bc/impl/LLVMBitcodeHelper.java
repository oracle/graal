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
package com.oracle.truffle.llvm.parser.bc.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMFunctionLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMAddressLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMDoubleLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMFloatLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI16LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI1LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI32LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI64LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI8LiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMIVarBitLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstruction.LLVMAllocaInstruction;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMAddressArrayCopyNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMAddressArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMDoubleArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMFloatArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMFunctionArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMI16ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMI32ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMI64ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory.LLVMI8ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMAddressStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMCompoundStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMDoubleStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMEmptyStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMFloatStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMFunctionStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMI16StructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMI1StructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMI32StructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMI64StructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMI8StructWriteNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.factories.LLVMArithmeticFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMCastsFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMComparisonFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMGetElementPtrFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMLiteralFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMLogicalFactory;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMFloatComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMIntegerComparisonType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.parser.util.LLVMTypeHelper;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.LLVMIVarBit;

import uk.ac.man.cs.llvm.ir.model.FunctionDeclaration;
import uk.ac.man.cs.llvm.ir.model.FunctionDefinition;
import uk.ac.man.cs.llvm.ir.model.GlobalValueSymbol;
import uk.ac.man.cs.llvm.ir.model.Symbol;
import uk.ac.man.cs.llvm.ir.model.ValueSymbol;
import uk.ac.man.cs.llvm.ir.model.constants.ArrayConstant;
import uk.ac.man.cs.llvm.ir.model.constants.BinaryOperationConstant;
import uk.ac.man.cs.llvm.ir.model.constants.CastConstant;
import uk.ac.man.cs.llvm.ir.model.constants.CompareConstant;
import uk.ac.man.cs.llvm.ir.model.constants.Constant;
import uk.ac.man.cs.llvm.ir.model.constants.FloatingPointConstant;
import uk.ac.man.cs.llvm.ir.model.constants.GetElementPointerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.IntegerConstant;
import uk.ac.man.cs.llvm.ir.model.constants.NullConstant;
import uk.ac.man.cs.llvm.ir.model.constants.StringConstant;
import uk.ac.man.cs.llvm.ir.model.constants.StructureConstant;
import uk.ac.man.cs.llvm.ir.model.constants.UndefinedConstant;
import uk.ac.man.cs.llvm.ir.model.enums.BinaryOperator;
import uk.ac.man.cs.llvm.ir.model.enums.CastOperator;
import uk.ac.man.cs.llvm.ir.model.enums.CompareOperator;
import uk.ac.man.cs.llvm.ir.types.ArrayType;
import uk.ac.man.cs.llvm.ir.types.FloatingPointType;
import uk.ac.man.cs.llvm.ir.types.FunctionType;
import uk.ac.man.cs.llvm.ir.types.IntegerType;
import uk.ac.man.cs.llvm.ir.types.MetaType;
import uk.ac.man.cs.llvm.ir.types.PointerType;
import uk.ac.man.cs.llvm.ir.types.StructureType;
import uk.ac.man.cs.llvm.ir.types.Type;
import uk.ac.man.cs.llvm.ir.types.VectorType;

public final class LLVMBitcodeHelper {

    private LLVMBitcodeHelper() {
    }

    public static final String FUNCTION_RETURN_VALUE_FRAME_SLOT_ID = "<function return value>";

    public static final String STACK_ADDRESS_FRAME_SLOT_ID = "<stack pointer>";

    public static int getAlignment(Symbol symbol, int align) {
        return getAlignment(symbol.getType(), align);
    }

    public static int getAlignment(Type type, int align) {
        int definedAlignment = type.getAlignment();
        int defaultAlignment = align == 0 ? Integer.MAX_VALUE : 1 << (align - 1);
        return Math.min(definedAlignment, defaultAlignment);
    }

    public static int getPaddingSize(Symbol symbol, int align, int address) {
        return getPaddingSize(symbol.getType(), align, address);
    }

    public static int getPaddingSize(Type type, int align, int address) {
        int alignment = getAlignment(type, align);
        if (alignment == 1) {
            return 0;
        }
        int mask = alignment - 1;
        return (alignment - (address & mask)) & mask;
    }

    public static int getSize(Symbol symbol, int align) {
        return getSize(symbol.getType(), align);
    }

    public static int getSize(Type type, int align) {
        if (align == 0) {
            return type.sizeof();
        } else {
            return type.sizeof(1 << (align - 1));
        }
    }

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

    public static LLVMBaseType toBaseType(final Type type) {
        if (type == MetaType.VOID) {
            return LLVMBaseType.VOID;
        }
        if (type instanceof IntegerType) {
            switch (((IntegerType) type).getBitCount()) {
                case 1:
                    return LLVMBaseType.I1;
                case Byte.SIZE:
                    return LLVMBaseType.I8;
                case Short.SIZE:
                    return LLVMBaseType.I16;
                case Integer.SIZE:
                    return LLVMBaseType.I32;
                case Long.SIZE:
                    return LLVMBaseType.I64;
                default:
                    return LLVMBaseType.I_VAR_BITWIDTH;
            }
        }
        if (type instanceof FloatingPointType) {
            switch (((FloatingPointType) type)) {
                case HALF:
                    return LLVMBaseType.HALF;
                case FLOAT:
                    return LLVMBaseType.FLOAT;
                case DOUBLE:
                    return LLVMBaseType.DOUBLE;
                case X86_FP80:
                    return LLVMBaseType.X86_FP80;
                default:
                    throw new RuntimeException("Unsupported type " + type);
            }
        }
        if (type instanceof PointerType) {
            Type pointee = ((PointerType) type).getPointeeType();
            return pointee instanceof FunctionType ? LLVMBaseType.FUNCTION_ADDRESS : LLVMBaseType.ADDRESS;
        }
        if (type instanceof StructureType) {
            return LLVMBaseType.STRUCT;
        }
        if (type instanceof ArrayType) {
            return LLVMBaseType.ARRAY;
        }
        if (type instanceof FunctionType) {
            return LLVMBaseType.FUNCTION_ADDRESS;
        }
        if (type instanceof VectorType) {
            Type base = ((VectorType) type).getElementType();
            switch (toBaseType(base)) {
                case I1:
                    return LLVMBaseType.I1_VECTOR;
                case I8:
                    return LLVMBaseType.I8_VECTOR;
                case I16:
                    return LLVMBaseType.I16_VECTOR;
                case I32:
                    return LLVMBaseType.I32_VECTOR;
                case I64:
                    return LLVMBaseType.I64_VECTOR;
                case FLOAT:
                    return LLVMBaseType.FLOAT_VECTOR;
                case DOUBLE:
                    return LLVMBaseType.DOUBLE_VECTOR;
                default:
                    throw new RuntimeException("Unsupported type " + type);
            }
        }
        throw new RuntimeException("Unsupported type " + type);
    }

    public static LLVMBaseType[] toBaseTypes(final Type[] types) {
        LLVMBaseType[] llvmtypes = new LLVMBaseType[types.length];

        for (int i = 0; i < types.length; i++) {
            llvmtypes[i] = toBaseType(types[i]);
        }

        return llvmtypes;
    }

    public static LLVMExpressionNode toBinaryOperatorNode(BinaryOperator operator, LLVMBaseType type, LLVMExpressionNode lhs, LLVMExpressionNode rhs) {
        LLVMArithmeticInstructionType opA = LLVMBitcodeHelper.toArithmeticInstructionType(operator);
        if (opA != null) {
            return LLVMArithmeticFactory.createArithmeticOperation(lhs, rhs, opA, type, null);
        }

        LLVMLogicalInstructionType opL = toLogicalInstructionType(operator);
        if (opL != null) {
            return LLVMLogicalFactory.createLogicalOperation(lhs, rhs, opL, type, null);
        }

        throw new RuntimeException("Missed a binary operator");
    }

    public static LLVMExpressionNode toCompareNode(CompareOperator operator, Type type, LLVMExpressionNode lhs, LLVMExpressionNode rhs) {
        return toCompareVectorNode(operator, type, null, lhs, rhs);
    }

    public static LLVMExpressionNode toCompareVectorNode(CompareOperator operator, Type type, LLVMAddressNode target, LLVMExpressionNode lhs, LLVMExpressionNode rhs) {
        LLVMBaseType llvmtype = toBaseType(type);

        switch (operator) {
            case FP_FALSE:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.FALSE);
            case FP_ORDERED_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED_AND_EQUALS);
            case FP_ORDERED_GREATER_THAN:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED_AND_GREATER_THAN);
            case FP_ORDERED_GREATER_OR_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED_AND_GREATER_EQUALS);
            case FP_ORDERED_LESS_THAN:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED_AND_LESS_THAN);
            case FP_ORDERED_LESS_OR_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED_AND_LESS_EQUALS);
            case FP_ORDERED_NOT_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED_AND_NOT_EQUALS);

            case FP_ORDERED:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.ORDERED);
            case FP_UNORDERED:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED);
            case FP_UNORDERED_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED_OR_EQUALS);
            case FP_UNORDERED_GREATER_THAN:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED_OR_GREATER_THAN);
            case FP_UNORDERED_GREATER_OR_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED_OR_GREATER_EQUALS);
            case FP_UNORDERED_LESS_THAN:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED_OR_LESS_THAN);
            case FP_UNORDERED_LESS_OR_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED_OR_LESS_EQUALS);
            case FP_UNORDERED_NOT_EQUAL:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.UNORDERED_OR_NOT_EQUALS);
            case FP_TRUE:
                return LLVMComparisonFactory.createFloatComparison(lhs, rhs, llvmtype, LLVMFloatComparisonType.TRUE);
            default:
                break;
        }

        LLVMIntegerComparisonType comparison;
        switch (operator) {
            case INT_EQUAL:
                comparison = LLVMIntegerComparisonType.EQUALS;
                break;
            case INT_NOT_EQUAL:
                comparison = LLVMIntegerComparisonType.NOT_EQUALS;
                break;
            case INT_UNSIGNED_GREATER_THAN:
                comparison = LLVMIntegerComparisonType.UNSIGNED_GREATER_THAN;
                break;
            case INT_UNSIGNED_GREATER_OR_EQUAL:
                comparison = LLVMIntegerComparisonType.UNSIGNED_GREATER_EQUALS;
                break;
            case INT_UNSIGNED_LESS_THAN:
                comparison = LLVMIntegerComparisonType.UNSIGNED_LESS_THAN;
                break;
            case INT_UNSIGNED_LESS_OR_EQUAL:
                comparison = LLVMIntegerComparisonType.UNSIGNED_LESS_EQUALS;
                break;
            case INT_SIGNED_GREATER_THAN:
                comparison = LLVMIntegerComparisonType.SIGNED_GREATER_THAN;
                break;
            case INT_SIGNED_GREATER_OR_EQUAL:
                comparison = LLVMIntegerComparisonType.SIGNED_GREATER_EQUALS;
                break;
            case INT_SIGNED_LESS_THAN:
                comparison = LLVMIntegerComparisonType.SIGNED_LESS_THAN;
                break;
            case INT_SIGNED_LESS_OR_EQUAL:
                comparison = LLVMIntegerComparisonType.SIGNED_LESS_EQUALS;
                break;

            default:
                throw new RuntimeException("Missed a compare operator");
        }

        if (LLVMTypeHelper.isVectorType(llvmtype)) {
            return LLVMComparisonFactory.createVectorComparison(target, lhs, rhs, llvmtype, comparison);
        } else {
            return LLVMComparisonFactory.createIntegerComparison(lhs, rhs, llvmtype, comparison);
        }
    }

    public static LLVMExpressionNode toConstantLiteralNode(Symbol value, @SuppressWarnings("unused") int align) {
        if (value instanceof IntegerConstant) {
            IntegerConstant constant = (IntegerConstant) value;
            int bits = ((IntegerType) (constant).getType()).getBitCount();
            switch (bits) {
                case 1:
                    return new LLVMI1LiteralNode(constant.getValue() != 0);
                case Byte.SIZE:
                    return new LLVMI8LiteralNode((byte) constant.getValue());
                case Short.SIZE:
                    return new LLVMI16LiteralNode((short) constant.getValue());
                case Integer.SIZE:
                    return new LLVMI32LiteralNode((int) constant.getValue());
                case Long.SIZE:
                    return new LLVMI64LiteralNode(constant.getValue());
                default:
                    return new LLVMIVarBitLiteralNode(LLVMIVarBit.fromLong(bits, constant.getValue()));
            }
        }
        if (value instanceof FloatingPointConstant) {
            FloatingPointConstant constant = (FloatingPointConstant) value;
            switch (((FloatingPointType) constant.getType())) {
                case FLOAT:
                    return new LLVMFloatLiteralNode(constant.toFloat());
                case DOUBLE:
                    return new LLVMDoubleLiteralNode(constant.toDouble());
                default:
                    break;
            }
        }
        throw new RuntimeException("Unsupported literal constant " + value);
    }

    public static LLVMExpressionNode toConstantNode(Symbol value, int align, Function<GlobalValueSymbol, LLVMExpressionNode> variables, LLVMContext context, FrameSlot stack) {
        if (value instanceof GlobalValueSymbol) {
            return variables.apply((GlobalValueSymbol) value);
        }
        if (value instanceof FunctionDefinition || value instanceof FunctionDeclaration) {
            String name = ((ValueSymbol) value).getName();
            FunctionType type = (FunctionType) value;

            LLVMRuntimeType returnType = LLVMBitcodeHelper.toRuntimeType(type.getReturnType());
            LLVMRuntimeType[] argTypes = LLVMBitcodeHelper.toRuntimeTypes(type.getArgumentTypes());

            return LLVMFunctionLiteralNodeGen.create(context.getFunctionRegistry().createFunctionDescriptor(name, returnType, argTypes, type.isVarArg()));
        }
        if (value instanceof StringConstant) {
            StringConstant constant = (StringConstant) value;
            List<LLVMExpressionNode> values = new ArrayList<>();
            String chars = constant.getString();
            for (int i = 0; i < chars.length(); i++) {
                values.add(new LLVMI8LiteralNode((byte) chars.charAt(i)));
            }
            if (constant.isCString()) {
                values.add(new LLVMI8LiteralNode((byte) 0));
            }
            return toArray(new IntegerType(Byte.SIZE), 1, values, context, stack);
        }
        if (value instanceof ArrayConstant) {
            ArrayConstant array = (ArrayConstant) value;

            Type subtype = array.getType().getElementType();

            LLVMBaseType llvmsubtype = LLVMBitcodeHelper.toBaseType(subtype);
            int stride = getSize(subtype, align);

            LLVMAllocaInstruction allocation = LLVMAllocaInstructionNodeGen.create(getSize(array, align), getAlignment(array, align), context, stack);

            switch (llvmsubtype) {
                case I8: {
                    LLVMI8Node[] elements = new LLVMI8Node[array.getElementCount()];
                    for (int i = 0; i < elements.length; i++) {
                        elements[i] = (LLVMI8Node) toConstantNode(array.getElement(i), align, variables, context, stack);
                    }
                    return LLVMI8ArrayLiteralNodeGen.create(elements, stride, allocation);
                }
                case I16: {
                    LLVMI16Node[] elements = new LLVMI16Node[array.getElementCount()];
                    for (int i = 0; i < elements.length; i++) {
                        elements[i] = (LLVMI16Node) toConstantNode(array.getElement(i), align, variables, context, stack);
                    }
                    return LLVMI16ArrayLiteralNodeGen.create(elements, stride, allocation);
                }
                case I32: {
                    LLVMI32Node[] elements = new LLVMI32Node[array.getElementCount()];
                    for (int i = 0; i < elements.length; i++) {
                        elements[i] = (LLVMI32Node) toConstantNode(array.getElement(i), align, variables, context, stack);
                    }
                    return LLVMI32ArrayLiteralNodeGen.create(elements, stride, allocation);
                }
                case I64: {
                    LLVMI64Node[] elements = new LLVMI64Node[array.getElementCount()];
                    for (int i = 0; i < elements.length; i++) {
                        elements[i] = (LLVMI64Node) toConstantNode(array.getElement(i), align, variables, context, stack);
                    }
                    return LLVMI64ArrayLiteralNodeGen.create(elements, stride, allocation);
                }
                case FLOAT: {
                    LLVMFloatNode[] elements = new LLVMFloatNode[array.getElementCount()];
                    for (int i = 0; i < elements.length; i++) {
                        elements[i] = (LLVMFloatNode) toConstantNode(array.getElement(i), align, variables, context, stack);
                    }
                    return LLVMFloatArrayLiteralNodeGen.create(elements, stride, allocation);
                }
                case DOUBLE: {
                    LLVMDoubleNode[] elements = new LLVMDoubleNode[array.getElementCount()];
                    for (int i = 0; i < elements.length; i++) {
                        elements[i] = (LLVMDoubleNode) toConstantNode(array.getElement(i), align, variables, context, stack);
                    }
                    return LLVMDoubleArrayLiteralNodeGen.create(elements, stride, allocation);
                }
                case ARRAY:
                case STRUCT: {
                    LLVMAddressNode[] elements = new LLVMAddressNode[array.getElementCount()];
                    for (int i = 0; i < elements.length; i++) {
                        elements[i] = (LLVMAddressNode) toConstantNode(array.getElement(i), align, variables, context, stack);
                    }
                    return LLVMAddressArrayCopyNodeGen.create(elements, stride, allocation);
                }
                case ADDRESS: {
                    LLVMAddressNode[] elements = new LLVMAddressNode[array.getElementCount()];
                    for (int i = 0; i < elements.length; i++) {
                        elements[i] = (LLVMAddressNode) toConstantNode(array.getElement(i), align, variables, context, stack);
                    }
                    return LLVMAddressArrayLiteralNodeGen.create(elements, stride, allocation);
                }
                case FUNCTION_ADDRESS: {
                    LLVMFunctionNode[] elements = new LLVMFunctionNode[array.getElementCount()];
                    for (int i = 0; i < elements.length; i++) {
                        elements[i] = (LLVMFunctionNode) toConstantNode(array.getElement(i), align, variables, context, stack);
                    }
                    return LLVMFunctionArrayLiteralNodeGen.create(elements, stride, allocation);
                }
                default:
                    throw new AssertionError(llvmsubtype);
            }
        }
        if (value instanceof StructureConstant) {
            StructureConstant structure = (StructureConstant) value;

            LLVMAllocaInstruction allocation = LLVMAllocaInstructionNodeGen.create(getSize(value, align), getAlignment(structure, align), context, stack);

            LLVMStructWriteNode[] nodes = new LLVMStructWriteNode[structure.getElementCount()];
            int[] offsets = new int[structure.getElementCount()];
            int offset = 0;

            for (int i = 0; i < structure.getElementCount(); i++) {
                Constant element = structure.getElement(i);

                if (!structure.isPacked()) {
                    offset += LLVMBitcodeHelper.getPaddingSize(element, align, offset);
                }
                offsets[i] = offset;

                LLVMExpressionNode elementNode = toConstantNode(element, align, variables, context, stack);
                LLVMBaseType elementBaseType = toBaseType(element.getType());
                int elementSize = getSize(element, align);

                offset += elementSize;

                switch (elementBaseType) {
                    case I1:
                        nodes[i] = new LLVMI1StructWriteNode((LLVMI1Node) elementNode);
                        break;
                    case I8:
                        nodes[i] = new LLVMI8StructWriteNode((LLVMI8Node) elementNode);
                        break;
                    case I16:
                        nodes[i] = new LLVMI16StructWriteNode((LLVMI16Node) elementNode);
                        break;
                    case I32:
                        nodes[i] = new LLVMI32StructWriteNode((LLVMI32Node) elementNode);
                        break;
                    case I64:
                        nodes[i] = new LLVMI64StructWriteNode((LLVMI64Node) elementNode);
                        break;
                    case FLOAT:
                        nodes[i] = new LLVMFloatStructWriteNode((LLVMFloatNode) elementNode);
                        break;
                    case DOUBLE:
                        nodes[i] = new LLVMDoubleStructWriteNode((LLVMDoubleNode) elementNode);
                        break;
                    case ARRAY:
                    case STRUCT:
                        if (elementSize == 0) {
                            nodes[i] = new LLVMEmptyStructWriteNode();
                        } else {
                            nodes[i] = new LLVMCompoundStructWriteNode((LLVMAddressNode) elementNode, elementSize);
                        }
                        break;
                    case ADDRESS:
                        nodes[i] = new LLVMAddressStructWriteNode((LLVMAddressNode) elementNode);
                        break;
                    case FUNCTION_ADDRESS:
                        nodes[i] = new LLVMFunctionStructWriteNode((LLVMFunctionNode) elementNode);
                        break;
                    default:
                        throw new AssertionError(elementBaseType);
                }
            }
            return new StructLiteralNode(offsets, nodes, allocation);
        }
        if (value instanceof NullConstant) {
            return toConstantZeroNode(value.getType(), align, context, stack);
        }
        if (value instanceof UndefinedConstant) {
            return toConstantZeroNode(value.getType(), align, context, stack);
        }
        if (value instanceof BinaryOperationConstant) {
            BinaryOperationConstant operation = (BinaryOperationConstant) value;
            LLVMExpressionNode lhs = toConstantNode(operation.getLHS(), align, variables, context, stack);
            LLVMExpressionNode rhs = toConstantNode(operation.getRHS(), align, variables, context, stack);
            LLVMBaseType type = toBaseType(operation.getType());

            return toBinaryOperatorNode(operation.getOperator(), type, lhs, rhs);
        }
        if (value instanceof CastConstant) {
            CastConstant cast = (CastConstant) value;
            LLVMConversionType type = toConversionType(cast.getOperator());
            LLVMExpressionNode fromNode = toConstantNode(cast.getValue(), align, variables, context, stack);
            LLVMBaseType from = toBaseType(cast.getValue().getType());
            LLVMBaseType to = toBaseType(cast.getType());

            return LLVMCastsFactory.cast(fromNode, to, from, type);
        }
        if (value instanceof CompareConstant) {
            CompareConstant compare = (CompareConstant) value;
            LLVMExpressionNode lhs = toConstantNode(compare.getLHS(), align, variables, context, stack);
            LLVMExpressionNode rhs = toConstantNode(compare.getRHS(), align, variables, context, stack);

            return toCompareNode(compare.getOperator(), compare.getLHS().getType(), lhs, rhs);
        }
        if (value instanceof GetElementPointerConstant) {
            GetElementPointerConstant ptr = (GetElementPointerConstant) value;

            LLVMAddressNode baseNode = (LLVMAddressNode) toConstantNode(ptr.getBasePointer(), align, variables, context, stack);
            LLVMAddressNode currentAddress = baseNode;

            Type type = ptr.getBasePointer().getType();

            for (int i = 0; i < ptr.getIndexCount(); i++) {
                Symbol index = ptr.getIndex(i);
                int idx = index instanceof NullConstant ? 0 : (int) ((IntegerConstant) index).getValue();

                if (type instanceof ArrayType) {
                    type = ((ArrayType) type).getElementType();
                } else if (type instanceof PointerType) {
                    type = ((PointerType) type).getPointeeType();
                } else {
                    int offset = 0;
                    for (int j = 0; j < idx; j++) {
                        Type t = ((StructureType) type).getElementType(j);
                        offset = offset + LLVMBitcodeHelper.getPaddingSize(t, align, offset) + LLVMBitcodeHelper.getSize(t, align);
                    }
                    type = ((StructureType) type).getElementType(idx);
                    offset += LLVMBitcodeHelper.getPaddingSize(type, align, offset);
                    if (offset != 0) {
                        currentAddress = LLVMGetElementPtrFactory.create(
                                        LLVMBaseType.I32,
                                        currentAddress,
                                        new LLVMI32LiteralNode(1),
                                        offset);
                    }
                    continue;
                }

                if (idx != 0) {
                    currentAddress = LLVMGetElementPtrFactory.create(
                                    LLVMBaseType.I32,
                                    currentAddress,
                                    new LLVMI32LiteralNode(idx),
                                    LLVMBitcodeHelper.getSize(type, align));
                }
            }

            return currentAddress;
        }
        return toConstantLiteralNode(value, align);
    }

    public static LLVMExpressionNode toConstantZeroNode(Type value, int align, LLVMContext context, FrameSlot stack) {
        if (value instanceof IntegerType) {
            int vbr = ((IntegerType) value).getBitCount();
            switch (vbr) {
                case 1:
                    return new LLVMI1LiteralNode(false);
                case Byte.SIZE:
                    return new LLVMI8LiteralNode((byte) 0);
                case Short.SIZE:
                    return new LLVMI16LiteralNode((short) 0);
                case Integer.SIZE:
                    return new LLVMI32LiteralNode(0);
                case Long.SIZE:
                    return new LLVMI64LiteralNode(0L);
                default:
                    return new LLVMIVarBitLiteralNode(LLVMIVarBit.fromLong(vbr, 0L));
            }
        }
        if (value instanceof FloatingPointType) {
            FloatingPointType type = (FloatingPointType) value;
            switch (type) {
                case FLOAT:
                    return new LLVMFloatLiteralNode(0.0f);
                case DOUBLE:
                    return new LLVMDoubleLiteralNode(0.0);
                default:
                    break;
            }
        }
        if (value instanceof PointerType) {
            PointerType pointer = (PointerType) value;
            if (pointer.getPointeeType() instanceof FunctionType) {
                LLVMFunctionDescriptor functionDescriptor = context.getFunctionRegistry().createFunctionDescriptor("<zero function>", LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false);
                return LLVMFunctionLiteralNodeGen.create(functionDescriptor);
            } else {
                return new LLVMAddressLiteralNode(LLVMAddress.fromLong(0));
            }
        }
        if (value instanceof ArrayType) {
            return LLVMAllocaInstructionNodeGen.create(getSize(value, align), getAlignment(value.getType(), align), context, stack);
        }
        if (value instanceof VectorType) {
            VectorType vector = (VectorType) value.getType();

            LLVMAddressNode target = LLVMAllocaInstructionNodeGen.create(getSize(value, align), getAlignment(value.getType(), align), context, stack);
            LLVMExpressionNode[] zeroes = new LLVMExpressionNode[vector.getElementCount()];
            Arrays.fill(zeroes, toConstantZeroNode(vector.getElementType(), align, context, stack));
            return LLVMLiteralFactory.createVectorLiteralNode(Arrays.asList(zeroes), target, toBaseType(vector));
        }
        if (value instanceof FunctionType) {
            LLVMFunctionDescriptor functionDescriptor = context.getFunctionRegistry().createFunctionDescriptor("<zero function>", LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false);
            return LLVMFunctionLiteralNodeGen.create(functionDescriptor);
        }
        if (value instanceof StructureType) {
            StructureType structure = (StructureType) value;

            LLVMAllocaInstruction allocation = LLVMAllocaInstructionNodeGen.create(getSize(structure.getType(), align), getAlignment(structure.getType(), align), context, stack);

            LLVMStructWriteNode[] nodes = new LLVMStructWriteNode[structure.getElementCount()];
            int[] offsets = new int[structure.getElementCount()];
            int offset = 0;

            for (int i = 0; i < structure.getElementCount(); i++) {
                Type element = structure.getElementType(i);

                if (!structure.isPacked()) {
                    offset += LLVMBitcodeHelper.getPaddingSize(element, align, offset);
                }
                offsets[i] = offset;

                LLVMBaseType elementBaseType = toBaseType(element);
                int elementSize = getSize(element, align);

                offset += elementSize;

                switch (elementBaseType) {
                    case I1:
                        nodes[i] = new LLVMI1StructWriteNode(new LLVMI1LiteralNode(false));
                        break;
                    case I8:
                        nodes[i] = new LLVMI8StructWriteNode(new LLVMI8LiteralNode((byte) 0));
                        break;
                    case I16:
                        nodes[i] = new LLVMI16StructWriteNode(new LLVMI16LiteralNode((short) 0));
                        break;
                    case I32:
                        nodes[i] = new LLVMI32StructWriteNode(new LLVMI32LiteralNode(0));
                        break;
                    case I64:
                        nodes[i] = new LLVMI64StructWriteNode(new LLVMI64LiteralNode(0L));
                        break;
                    case FLOAT:
                        nodes[i] = new LLVMFloatStructWriteNode(new LLVMFloatLiteralNode(0.0f));
                        break;
                    case DOUBLE:
                        nodes[i] = new LLVMDoubleStructWriteNode(new LLVMDoubleLiteralNode(0.0));
                        break;
                    case ARRAY:
                        nodes[i] = new LLVMCompoundStructWriteNode((LLVMAddressNode) toConstantZeroNode(element, align, context, stack), elementSize);
                        break;
                    case STRUCT:
                        if (elementSize == 0) {
                            nodes[i] = new LLVMEmptyStructWriteNode();
                        } else {
                            LLVMExpressionNode struct = toConstantZeroNode(element, align, context, stack);
                            nodes[i] = new LLVMCompoundStructWriteNode((LLVMAddressNode) struct, elementSize);
                        }
                        break;
                    case ADDRESS:
                        nodes[i] = new LLVMAddressStructWriteNode(new LLVMAddressLiteralNode(LLVMAddress.fromLong(0)));
                        break;
                    case FUNCTION_ADDRESS: {
                        LLVMFunctionDescriptor functionDescriptor = context.getFunctionRegistry().createFunctionDescriptor("<zero function>", LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false);
                        nodes[i] = new LLVMFunctionStructWriteNode(LLVMFunctionLiteralNodeGen.create(functionDescriptor));
                        break;
                    }
                    default:
                        throw new AssertionError(elementBaseType);
                }
            }
            return new StructLiteralNode(offsets, nodes, allocation);
        }
        throw new RuntimeException("Unsupported zero constant " + value);
    }

    public static LLVMConversionType toConversionType(CastOperator operator) {
        switch (operator) {
            case ZERO_EXTEND:
            case FP_TO_UNSIGNED_INT:
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

    public static FrameSlotKind toFrameSlotKind(Type type) {
        if (type == MetaType.VOID) {
            throw new LLVMUnsupportedException(UnsupportedReason.PARSER_ERROR_VOID_SLOT);
        }
        if (type instanceof IntegerType) {
            switch (((IntegerType) type).getBitCount()) {
                case 1:
                    return FrameSlotKind.Boolean;
                case Byte.SIZE:
                    return FrameSlotKind.Byte;
                case Short.SIZE:
                case Integer.SIZE:
                    return FrameSlotKind.Int;
                case Long.SIZE:
                    return FrameSlotKind.Long;
                default:
                    break;
            }
        }
        if (type instanceof FloatingPointType) {
            switch (((FloatingPointType) type)) {
                case FLOAT:
                    return FrameSlotKind.Float;
                case DOUBLE:
                    return FrameSlotKind.Double;
                default:
                    break;
            }
        }
        return FrameSlotKind.Object;
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

    public static LLVMRuntimeType toRuntimeType(final Type type) {
        if (type == MetaType.VOID) {
            return LLVMRuntimeType.VOID;
        }
        if (type instanceof IntegerType) {
            switch (((IntegerType) type).getBitCount()) {
                case 1:
                    return LLVMRuntimeType.I1;
                case Byte.SIZE:
                    return LLVMRuntimeType.I8;
                case Short.SIZE:
                    return LLVMRuntimeType.I16;
                case Integer.SIZE:
                    return LLVMRuntimeType.I32;
                case Long.SIZE:
                    return LLVMRuntimeType.I64;
                default:
                    return LLVMRuntimeType.I_VAR_BITWIDTH;
            }
        }
        if (type instanceof FloatingPointType) {
            switch (((FloatingPointType) type)) {
                case HALF:
                    return LLVMRuntimeType.HALF;
                case FLOAT:
                    return LLVMRuntimeType.FLOAT;
                case DOUBLE:
                    return LLVMRuntimeType.DOUBLE;
                case X86_FP80:
                    return LLVMRuntimeType.X86_FP80;
                default:
                    throw new RuntimeException("Unsupported type " + type);
            }
        }
        if (type instanceof PointerType) {
            Type pointee = ((PointerType) type).getPointeeType();
            return pointee instanceof FunctionType ? LLVMRuntimeType.FUNCTION_ADDRESS : LLVMRuntimeType.ADDRESS;
        }
        if (type instanceof StructureType) {
            return LLVMRuntimeType.STRUCT;
        }
        if (type instanceof ArrayType) {
            return LLVMRuntimeType.ARRAY;
        }
        if (type instanceof FunctionType) {
            return LLVMRuntimeType.FUNCTION_ADDRESS;
        }
        if (type instanceof VectorType) {
            Type base = ((VectorType) type).getElementType();
            switch (toRuntimeType(base)) {
                case I1:
                    return LLVMRuntimeType.I1_VECTOR;
                case I8:
                    return LLVMRuntimeType.I8_VECTOR;
                case I16:
                    return LLVMRuntimeType.I16_VECTOR;
                case I32:
                    return LLVMRuntimeType.I32_VECTOR;
                case I64:
                    return LLVMRuntimeType.I64_VECTOR;
                case FLOAT:
                    return LLVMRuntimeType.FLOAT_VECTOR;
                case DOUBLE:
                    return LLVMRuntimeType.DOUBLE_VECTOR;
                default:
                    throw new RuntimeException("Unsupported type " + type);
            }
        }
        throw new RuntimeException("Unsupported type " + type);
    }

    public static LLVMRuntimeType[] toRuntimeTypes(Type[] types) {
        LLVMRuntimeType[] llvmtypes = new LLVMRuntimeType[types.length];

        for (int i = 0; i < types.length; i++) {
            llvmtypes[i] = toRuntimeType(types[i].getType());
        }

        return llvmtypes;
    }

    public static LLVMRuntimeType[] toRuntimeTypes(List<? extends Type> types) {
        LLVMRuntimeType[] llvmtypes = new LLVMRuntimeType[types.size()];

        for (int i = 0; i < types.size(); i++) {
            llvmtypes[i] = toRuntimeType(types.get(i).getType());
        }

        return llvmtypes;
    }

    private static LLVMAddressNode toArray(Type type, int alignment, List<LLVMExpressionNode> values, LLVMContext context, FrameSlot stack) {
        LLVMBaseType llvmElementType = toBaseType(type);
        int baseTypeSize = type.sizeof();
        int nrElements = values.size();
        int size = nrElements * baseTypeSize;
        if (size == 0) {
            throw new AssertionError(llvmElementType + " has size of 0!");
        }
        LLVMAllocaInstruction target = LLVMAllocaInstructionNodeGen.create(size, alignment, context, stack);
        switch (llvmElementType) {
            case I8:
                return LLVMStoreNodeFactory.LLVMI8ArrayLiteralNodeGen.create(values.toArray(new LLVMI8Node[nrElements]), baseTypeSize, target);
            case I16:
                return LLVMStoreNodeFactory.LLVMI16ArrayLiteralNodeGen.create(values.toArray(new LLVMI16Node[nrElements]), baseTypeSize, target);
            case I32:
                return LLVMStoreNodeFactory.LLVMI32ArrayLiteralNodeGen.create(values.toArray(new LLVMI32Node[nrElements]), baseTypeSize, target);
            case I64:
                return LLVMStoreNodeFactory.LLVMI64ArrayLiteralNodeGen.create(values.toArray(new LLVMI64Node[nrElements]), baseTypeSize, target);
            case FLOAT:
                return LLVMStoreNodeFactory.LLVMFloatArrayLiteralNodeGen.create(values.toArray(new LLVMFloatNode[nrElements]), baseTypeSize, target);
            case DOUBLE:
                return LLVMStoreNodeFactory.LLVMDoubleArrayLiteralNodeGen.create(values.toArray(new LLVMDoubleNode[nrElements]), baseTypeSize, target);
            // case X86_FP80:
            // return LLVM80BitFloatArrayLiteralNodeGen.create(values.toArray(new
            // LLVM80BitFloatNode[nrElements]), baseTypeSize, arrayAlloc);
            // case ARRAY:
            // case STRUCT:
            // return LLVMAddressArrayCopyNodeGen.create(arrayValues.toArray(new
            // LLVMAddressNode[nrElements]), baseTypeSize, arrayAlloc);
            // case ADDRESS:
            // return LLVMAddressArrayLiteralNodeGen.create(arrayValues.toArray(new
            // LLVMAddressNode[nrElements]), baseTypeSize, arrayAlloc);
            // case FUNCTION_ADDRESS:
            // return LLVMFunctionArrayLiteralNodeGen.create(arrayValues.toArray(new
            // LLVMFunctionNode[nrElements]), baseTypeSize, arrayAlloc);
            default:
                throw new AssertionError(llvmElementType);
        }
    }
}
