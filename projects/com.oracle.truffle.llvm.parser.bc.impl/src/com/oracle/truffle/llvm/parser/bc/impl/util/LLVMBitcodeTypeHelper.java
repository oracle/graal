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
package com.oracle.truffle.llvm.parser.bc.impl.util;

import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMType;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.parser.base.datalayout.DataLayoutConverter;
import uk.ac.man.cs.llvm.ir.model.enums.BinaryOperator;
import uk.ac.man.cs.llvm.ir.model.enums.CastOperator;
import uk.ac.man.cs.llvm.ir.types.AggregateType;
import uk.ac.man.cs.llvm.ir.types.ArrayType;
import uk.ac.man.cs.llvm.ir.types.BigIntegerConstantType;
import uk.ac.man.cs.llvm.ir.types.FloatingPointType;
import uk.ac.man.cs.llvm.ir.types.FunctionType;
import uk.ac.man.cs.llvm.ir.types.IntegerType;
import uk.ac.man.cs.llvm.ir.types.MetaType;
import uk.ac.man.cs.llvm.ir.types.PointerType;
import uk.ac.man.cs.llvm.ir.types.StructureType;
import uk.ac.man.cs.llvm.ir.types.Type;
import uk.ac.man.cs.llvm.ir.types.VectorType;

import java.util.List;

public class LLVMBitcodeTypeHelper {

    private final DataLayoutConverter.DataSpecConverter targetDataLayout;

    public LLVMBitcodeTypeHelper(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        this.targetDataLayout = targetDataLayout;
    }

    public static Type goIntoType(Type parent, int index) {
        if (parent instanceof AggregateType) {
            return ((AggregateType) parent).getElementType(index);
        } else if (parent instanceof PointerType) {
            return ((PointerType) parent).getPointeeType();
        } else {
            throw new IllegalStateException("Cannot index type: " + parent);
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

    private static LLVMType toBaseType(final Type type) {
        if (type == MetaType.VOID) {
            return new LLVMType(LLVMBaseType.VOID);

        } else if (type instanceof IntegerType) {
            switch (((IntegerType) type).getBitCount()) {
                case 1:
                    return new LLVMType(LLVMBaseType.I1);
                case Byte.SIZE:
                    return new LLVMType(LLVMBaseType.I8);
                case Short.SIZE:
                    return new LLVMType(LLVMBaseType.I16);
                case Integer.SIZE:
                    return new LLVMType(LLVMBaseType.I32);
                case Long.SIZE:
                    return new LLVMType(LLVMBaseType.I64);
                default:
                    return new LLVMType(LLVMBaseType.I_VAR_BITWIDTH);
            }

        } else if (type instanceof FloatingPointType) {
            switch (((FloatingPointType) type)) {
                case HALF:
                    return new LLVMType(LLVMBaseType.HALF);
                case FLOAT:
                    return new LLVMType(LLVMBaseType.FLOAT);
                case DOUBLE:
                    return new LLVMType(LLVMBaseType.DOUBLE);
                case X86_FP80:
                    return new LLVMType(LLVMBaseType.X86_FP80);
                default:
                    throw new RuntimeException("Unsupported type " + type);
            }

        } else if (type instanceof PointerType) {
            Type pointee = ((PointerType) type).getPointeeType();
            if (pointee instanceof FunctionType) {
                return new LLVMType(LLVMBaseType.FUNCTION_ADDRESS);
            }
            return new LLVMType(LLVMBaseType.ADDRESS, toBaseType(pointee));

        } else if (type instanceof StructureType) {
            return new LLVMType(LLVMBaseType.STRUCT);

        } else if (type instanceof ArrayType) {
            return new LLVMType(LLVMBaseType.ARRAY);

        } else if (type instanceof FunctionType) {
            return new LLVMType(LLVMBaseType.FUNCTION_ADDRESS);

        } else if (type instanceof VectorType) {
            Type base = ((VectorType) type).getElementType();
            switch (toBaseType(base).getType()) {
                case I1:
                    return new LLVMType(LLVMBaseType.I1_VECTOR);
                case I8:
                    return new LLVMType(LLVMBaseType.I8_VECTOR);
                case I16:
                    return new LLVMType(LLVMBaseType.I16_VECTOR);
                case I32:
                    return new LLVMType(LLVMBaseType.I32_VECTOR);
                case I64:
                    return new LLVMType(LLVMBaseType.I64_VECTOR);
                case FLOAT:
                    return new LLVMType(LLVMBaseType.FLOAT_VECTOR);
                case DOUBLE:
                    return new LLVMType(LLVMBaseType.DOUBLE_VECTOR);
                default:
                    throw new RuntimeException("Unsupported type " + type);
            }

        } else if (type == MetaType.OPAQUE) {
            // named generic Type
            return new LLVMType(LLVMBaseType.ADDRESS, new LLVMType(LLVMBaseType.VOID));

        } else {
            throw new RuntimeException("Unsupported type " + type);
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

    public static FrameSlotKind toFrameSlotKind(Type type) {
        if (type == MetaType.VOID) {
            throw new LLVMUnsupportedException(LLVMUnsupportedException.UnsupportedReason.PARSER_ERROR_VOID_SLOT);

        } else if (type instanceof IntegerType) {
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

        } else if (type instanceof FloatingPointType) {
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

    public static LLVMFunctionDescriptor.LLVMRuntimeType toRuntimeType(final Type type) {
        if (type == MetaType.VOID) {
            return LLVMFunctionDescriptor.LLVMRuntimeType.VOID;

        } else if (type instanceof IntegerType) {
            switch (((IntegerType) type).getBitCount()) {
                case 1:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.I1;
                case Byte.SIZE:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.I8;
                case Short.SIZE:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.I16;
                case Integer.SIZE:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.I32;
                case Long.SIZE:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.I64;
                default:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.I_VAR_BITWIDTH;
            }

        } else if (type instanceof FloatingPointType) {
            switch (((FloatingPointType) type)) {
                case HALF:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.HALF;
                case FLOAT:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.FLOAT;
                case DOUBLE:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.DOUBLE;
                case X86_FP80:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.X86_FP80;
                default:
                    throw new RuntimeException("Unsupported type " + type);
            }

        } else if (type instanceof PointerType) {
            final Type pointee = ((PointerType) type).getPointeeType();
            if (pointee instanceof FunctionType) {
                return LLVMFunctionDescriptor.LLVMRuntimeType.FUNCTION_ADDRESS;

            } else if (pointee instanceof IntegerType) {
                switch (((IntegerType) pointee).getBitCount()) {
                    case 1:
                        return LLVMFunctionDescriptor.LLVMRuntimeType.I1_POINTER;
                    case Byte.SIZE:
                        return LLVMFunctionDescriptor.LLVMRuntimeType.I8_POINTER;
                    case Short.SIZE:
                        return LLVMFunctionDescriptor.LLVMRuntimeType.I16_POINTER;
                    case Integer.SIZE:
                        return LLVMFunctionDescriptor.LLVMRuntimeType.I32_POINTER;
                    case Long.SIZE:
                        return LLVMFunctionDescriptor.LLVMRuntimeType.I64_POINTER;
                    default:
                        return LLVMFunctionDescriptor.LLVMRuntimeType.ADDRESS;
                }

            } else if (pointee instanceof FloatingPointType) {
                switch (((FloatingPointType) pointee)) {
                    case HALF:
                        return LLVMFunctionDescriptor.LLVMRuntimeType.HALF_POINTER;
                    case FLOAT:
                        return LLVMFunctionDescriptor.LLVMRuntimeType.FLOAT_POINTER;
                    case DOUBLE:
                        return LLVMFunctionDescriptor.LLVMRuntimeType.DOUBLE_POINTER;
                    case X86_FP80:
                    default:
                        return LLVMFunctionDescriptor.LLVMRuntimeType.ADDRESS;
                }

            } else {
                return LLVMFunctionDescriptor.LLVMRuntimeType.ADDRESS;
            }

        } else if (type instanceof StructureType) {
            return LLVMFunctionDescriptor.LLVMRuntimeType.STRUCT;

        } else if (type instanceof ArrayType) {
            return LLVMFunctionDescriptor.LLVMRuntimeType.ARRAY;

        } else if (type instanceof FunctionType) {
            return LLVMFunctionDescriptor.LLVMRuntimeType.FUNCTION_ADDRESS;

        } else if (type instanceof VectorType) {
            final Type base = ((VectorType) type).getElementType();
            switch (toRuntimeType(base)) {
                case I1:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.I1_VECTOR;
                case I8:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.I8_VECTOR;
                case I16:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.I16_VECTOR;
                case I32:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.I32_VECTOR;
                case I64:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.I64_VECTOR;
                case FLOAT:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.FLOAT_VECTOR;
                case DOUBLE:
                    return LLVMFunctionDescriptor.LLVMRuntimeType.DOUBLE_VECTOR;
                default:
                    throw new RuntimeException("Unsupported type " + type);
            }

        } else {
            throw new RuntimeException("Unsupported type " + type);
        }
    }

    public static LLVMFunctionDescriptor.LLVMRuntimeType[] toRuntimeTypes(Type[] types) {
        final LLVMFunctionDescriptor.LLVMRuntimeType[] llvmtypes = new LLVMFunctionDescriptor.LLVMRuntimeType[types.length];
        for (int i = 0; i < types.length; i++) {
            llvmtypes[i] = toRuntimeType(types[i].getType());
        }
        return llvmtypes;
    }

    public static LLVMFunctionDescriptor.LLVMRuntimeType[] toRuntimeTypes(List<? extends Type> types) {
        final LLVMFunctionDescriptor.LLVMRuntimeType[] llvmtypes = new LLVMFunctionDescriptor.LLVMRuntimeType[types.size()];
        for (int i = 0; i < types.size(); i++) {
            llvmtypes[i] = toRuntimeType(types.get(i).getType());
        }
        return llvmtypes;
    }

    public int getPadding(int offset, int alignment) {
        if (alignment == 0) {
            throw new AssertionError();
        }
        return (alignment - (offset % alignment)) % alignment;
    }

    public int getPadding(int offset, Type type) {
        final int alignment = getAlignment(type);
        return alignment == 0 ? 0 : getPadding(offset, alignment);
    }

    public int getByteSize(Type type) {
        if (type instanceof IntegerType) {
            return Math.max(1, ((IntegerType) type).getBitCount() / Byte.SIZE);

        } else if (type instanceof FloatingPointType) {
            return Math.max(1, ((FloatingPointType) type).width() / Byte.SIZE);

        } else if (type instanceof BigIntegerConstantType) {
            return getByteSize(type.getType());

        } else if (type instanceof PointerType) {
            if (((PointerType) type).getPointeeType() instanceof FunctionType) {
                return LLVMHeap.FUNCTION_PTR_SIZE_BYTE;
            } else {
                return LLVMAddress.WORD_LENGTH_BIT / Byte.SIZE;
            }

        } else if (type instanceof FunctionType) {
            return 0;

        } else if (type instanceof ArrayType) {
            final ArrayType arrayType = (ArrayType) type;
            if (arrayType.getElementCount() == 0) {
                return 0;
            } else {
                return arrayType.getElementCount() * getByteSize(arrayType.getElementType());
            }

        } else if (type instanceof StructureType) {
            return getStructByteSize((StructureType) type);

        } else if (type instanceof VectorType) {
            final VectorType vectorType = (VectorType) type;
            if (vectorType.getElementCount() == 0) {
                return 0;
            } else {
                int sum = 0;
                for (int i = 0; i < vectorType.getElementCount(); i++) {
                    sum += getByteSize(vectorType.getElementType(i));
                }
                return sum;
            }

        } else if (type == MetaType.X86_MMX || type == MetaType.OPAQUE) {
            return 0;

        } else {
            throw new AssertionError("Cannot compute size of type: " + type);
        }
    }

    private int getStructByteSize(StructureType structureType) {
        int sumByte = 0;
        for (int i = 0; i < structureType.getElementCount(); i++) {
            final Type elemType = structureType.getElementType(i);
            if (!structureType.isPacked()) {
                sumByte += getStructPaddingByteSize(sumByte, elemType);
            }
            sumByte += getByteSize(elemType);
        }

        int padding = 0;
        if (!structureType.isPacked() && sumByte != 0) {
            padding = getPadding(sumByte, getLargestAlignment(structureType));
        }

        return sumByte + padding;
    }

    private int getStructPaddingByteSize(int currentOffset, Type elemType) {
        final int alignment = getAlignment(elemType);
        if (alignment == 0) {
            return 0;
        } else {
            return getPadding(currentOffset, alignment);
        }
    }

    public int getAlignment(Type type) {
        if (type instanceof StructureType) {
            return getLargestAlignment((StructureType) type);

        } else if (type instanceof ArrayType) {
            return getAlignment(((ArrayType) type).getElementType());

        } else if (type instanceof VectorType) {
            return getAlignment(((VectorType) type).getElementType());

        } else if (targetDataLayout != null) {
            return targetDataLayout.getBitAlignment(getLLVMBaseType(type)) / Byte.SIZE;

        } else {
            return type.getAlignment();
        }
    }

    private int getLargestAlignment(StructureType structureType) {
        int largestAlignment = 0;
        for (int i = 0; i < structureType.getElementCount(); i++) {
            largestAlignment = Math.max(largestAlignment, getAlignment(structureType.getElementType(i)));
        }
        return largestAlignment;
    }

    public static LLVMBaseType getLLVMBaseType(Type type) {
        return toBaseType(type).getType();
    }

    public int goIntoTypeGetLength(Type type, int index) {
        if (type == null) {
            throw new IllegalStateException("Cannot go into null!");

        } else if (type instanceof PointerType) {
            return getByteSize(((PointerType) type).getPointeeType()) * index;

        } else if (type instanceof ArrayType) {
            return getByteSize(((ArrayType) type).getElementType()) * index;

        } else if (type instanceof VectorType) {
            return getByteSize(((VectorType) type).getElementType()) * index;

        } else if (type instanceof StructureType) {
            final StructureType structureType = (StructureType) type;
            int offset = 0;

            for (int i = 0; i < index; i++) {
                final Type elemType = structureType.getElementType(i);
                offset += getByteSize(elemType);
                if (!structureType.isPacked()) {
                    offset += getPadding(offset, elemType);
                }
            }

            if (!structureType.isPacked() && getStructByteSize(structureType) > offset) {
                offset += getPadding(offset, structureType.getElementType(index));
            }

            return offset;

        } else {
            throw new UnsupportedOperationException("Cannot compute offset in type: " + type);
        }
    }

}
