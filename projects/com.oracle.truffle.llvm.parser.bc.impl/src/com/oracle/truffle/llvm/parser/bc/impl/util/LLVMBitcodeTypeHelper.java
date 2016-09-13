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

import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMType;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
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

        } else {
            throw new RuntimeException("Unsupported type " + type);
        }
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

    public LLVMBaseType getLLVMBaseType(Type type) {
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
