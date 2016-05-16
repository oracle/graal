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
package com.oracle.truffle.llvm.parser.util;

import com.intel.llvm.ireditor.lLVM_IR.StructureConstant;
import com.intel.llvm.ireditor.lLVM_IR.TypedConstant;
import com.intel.llvm.ireditor.types.ResolvedAnyIntegerType;
import com.intel.llvm.ireditor.types.ResolvedArrayType;
import com.intel.llvm.ireditor.types.ResolvedFloatingType;
import com.intel.llvm.ireditor.types.ResolvedIntegerType;
import com.intel.llvm.ireditor.types.ResolvedNamedType;
import com.intel.llvm.ireditor.types.ResolvedOpaqueType;
import com.intel.llvm.ireditor.types.ResolvedPointerType;
import com.intel.llvm.ireditor.types.ResolvedStructType;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.intel.llvm.ireditor.types.ResolvedVectorType;
import com.intel.llvm.ireditor.types.ResolvedVoidType;
import com.intel.llvm.ireditor.types.TypeResolver;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;

public class LLVMTypeHelper {

    private static LLVMParserRuntime runtime;

    public static int getByteSize(ResolvedType type) {
        int bits = type.getBits().intValue();
        if (type instanceof ResolvedIntegerType || type instanceof ResolvedFloatingType) {
            return Math.max(1, bits / Byte.SIZE);
        } else if (type instanceof ResolvedPointerType) {
            if (type.getContainedType(0).isFunction()) {
                return LLVMHeap.FUNCTION_PTR_SIZE_BYTE;
            } else {
                return LLVMAddress.WORD_LENGTH_BIT / Byte.SIZE;
            }
        } else if (type instanceof ResolvedArrayType) {
            ResolvedArrayType arrayType = (ResolvedArrayType) type;
            int arraySize = arrayType.getSize();
            if (arraySize == 0) {
                return 0;
            } else {
                return arraySize * getByteSize(arrayType.getContainedType(0));
            }
        } else if (type instanceof ResolvedStructType) {
            return getStructSizeByte((ResolvedStructType) type);
        } else if (type instanceof ResolvedNamedType) {
            return getByteSize(((ResolvedNamedType) type).getReferredType());
        } else if (type instanceof ResolvedOpaqueType) {
            return 0;
        } else if (type instanceof ResolvedVectorType) {
            return type.getBits().intValue() / Byte.SIZE;
        } else {
            throw new AssertionError(type);
        }
    }

    public static int getStructureSizeByte(StructureConstant structure, TypeResolver typeResolver) {
        int sumByte = 0;
        int largestAlignment = 0;
        for (TypedConstant constant : structure.getList().getTypedConstants()) {
            ResolvedType type = typeResolver.resolve(constant.getType());
            int alignmentByte = getAlignmentByte(type);
            sumByte += computePaddingByte(sumByte, type);
            sumByte += getByteSize(type);
            largestAlignment = Math.max(alignmentByte, largestAlignment);
        }
        int padding;
        if (structure.getPacked() != null || sumByte == 0) {
            padding = 0;
        } else {
            padding = computePadding(sumByte, largestAlignment);
        }
        int totalSizeByte = sumByte + padding;
        return totalSizeByte;
    }

    private static int getStructSizeByte(ResolvedStructType type) {
        int sumByte = 0;
        for (ResolvedType field : type.getFieldTypes()) {
            if (!type.isPacked()) {
                sumByte += computePaddingByte(sumByte, field);
            }
            sumByte += getByteSize(field);
        }
        int padding;
        if (type.isPacked() || sumByte == 0) {
            padding = 0;
        } else {
            padding = computePadding(sumByte, largestAlignmentByte(type));
        }
        int totalSizeByte = sumByte + padding;
        return totalSizeByte;
    }

    private static int computePadding(int offset, int alignment) {
        if (alignment == 0) {
            throw new AssertionError();
        }
        int padding = (alignment - (offset % alignment)) % alignment;
        return padding;
    }

    private static int largestAlignmentByte(ResolvedStructType structType) {
        int largestAlignment = 0;
        for (ResolvedType field : structType.getFieldTypes()) {
            int alignment = getAlignmentByte(field);
            largestAlignment = Math.max(largestAlignment, alignment);
        }
        return largestAlignment;
    }

    // Checkstyle: stop magic number name check
    public static LLVMBaseType getLLVMType(ResolvedType elementType) {
        if (elementType instanceof ResolvedIntegerType) {
            switch (elementType.getBits().intValue()) {
                case 1:
                    return LLVMBaseType.I1;
                case 8:
                    return LLVMBaseType.I8;
                case 16:
                    return LLVMBaseType.I16;
                case 32:
                    return LLVMBaseType.I32;
                case 64:
                    return LLVMBaseType.I64;
                default:
                    return LLVMBaseType.I_VAR_BITWIDTH;
            }
        } else if (elementType instanceof ResolvedFloatingType) {
            switch (elementType.getBits().intValue()) {
                case 32:
                    return LLVMBaseType.FLOAT;
                case 64:
                    return LLVMBaseType.DOUBLE;
                case 80:
                    return LLVMBaseType.X86_FP80;
                default:
                    throw new LLVMUnsupportedException(UnsupportedReason.FLOAT_OTHER_TYPE_NOT_IMPLEMENTED);
            }
        } else if (elementType.isPointer()) {
            if (elementType.getContainedType(0).isFunction()) {
                return LLVMBaseType.FUNCTION_ADDRESS;
            } else {
                return LLVMBaseType.ADDRESS;
            }
        } else if (elementType instanceof ResolvedVoidType) {
            return LLVMBaseType.VOID;
        } else if (elementType instanceof ResolvedArrayType) {
            return LLVMBaseType.ARRAY;
        } else if (elementType.isStruct()) {
            return LLVMBaseType.STRUCT;
        } else if (elementType instanceof ResolvedVectorType) {
            switch (getLLVMType(elementType.getContainedType(-1))) {
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
                case ADDRESS:
                    return LLVMBaseType.ADDRESS_VECTOR;
                default:
                    throw new AssertionError(elementType);
            }
        } else if (elementType instanceof ResolvedAnyIntegerType) {
            return LLVMBaseType.I32;
        }

        else {
            throw new LLVMUnsupportedException(UnsupportedReason.OTHER_TYPE_NOT_IMPLEMENTED);
        }
    }// Checkstyle: resume magic number name check

    public static int goIntoTypeGetLengthByte(ResolvedType currentType, int index) {
        if (currentType == null) {
            return 0; // TODO: better throw an exception
        } else if (currentType instanceof ResolvedPointerType) {
            return getByteSize(currentType.getContainedType(0)) * index;
        } else if (currentType instanceof ResolvedArrayType) {
            return getByteSize(((ResolvedArrayType) currentType).getContainedType(-1)) * index;
        } else if (currentType instanceof ResolvedVectorType) {
            return getByteSize(((ResolvedVectorType) currentType).getContainedType(-1)) * index;
        } else if (currentType instanceof ResolvedStructType) {
            int sum = 0;
            for (int i = 0; i < index; i++) {
                ResolvedType containedType = currentType.getContainedType(i);
                sum += getByteSize(containedType);
                if (!isPackedStructType(currentType)) {
                    sum += computePaddingByte(sum, containedType);
                }
            }
            if (!isPackedStructType(currentType) && LLVMTypeHelper.getStructSizeByte((ResolvedStructType) currentType) > sum) {
                sum += computePaddingByte(sum, currentType.getContainedType(index));
            }
            return sum;
        } else if (currentType instanceof ResolvedNamedType) {
            return goIntoTypeGetLengthByte(((ResolvedNamedType) currentType).getReferredType(), index);
        } else {
            throw new AssertionError(currentType);
        }
    }

    public static ResolvedType goIntoType(ResolvedType currentType, int index) {
        return currentType.getContainedType(index);
    }

    public static boolean isPackedStructType(ResolvedType currentType) {
        if (currentType instanceof ResolvedNamedType) {
            return isPackedStructType(((ResolvedNamedType) currentType).getReferredType());
        }
        if (!(currentType instanceof ResolvedStructType)) {
            return false;
        }
        return ((ResolvedStructType) currentType).isPacked();
    }

    public static int computePaddingByte(int currentOffset, ResolvedType type) {
        int alignmentByte = getAlignmentByte(type);
        if (alignmentByte == 0) {
            return 0;
        } else {
            return computePadding(currentOffset, alignmentByte);
        }
    }

    interface LayoutConverter {
        int getBitAlignment(LLVMBaseType type);
    }

    public static void setParserRuntime(LLVMParserRuntime runtime) {
        LLVMTypeHelper.runtime = runtime;
    }

    public static int getAlignmentByte(ResolvedType field) {
        if (field instanceof ResolvedNamedType) {
            return getAlignmentByte(((ResolvedNamedType) field).getReferredType());
        } else if (field instanceof ResolvedStructType) {
            return largestAlignmentByte((ResolvedStructType) field);
        } else if (field instanceof ResolvedArrayType) {
            return getAlignmentByte(((ResolvedArrayType) field).getContainedType(-1));
        } else if (field instanceof ResolvedVectorType) {
            return getAlignmentByte(field.getContainedType(-1));
        } else {
            LLVMBaseType type = getLLVMType(field);
            return runtime.getBitAlignment(type) / Byte.SIZE;
        }
    }

    public static boolean isVectorType(LLVMBaseType llvmType) {
        switch (llvmType) {
            case I1_VECTOR:
            case I8_VECTOR:
            case I16_VECTOR:
            case I32_VECTOR:
            case I64_VECTOR:
            case FLOAT_VECTOR:
            case DOUBLE_VECTOR:
                return true;
            case ARRAY:
            case DOUBLE:
            case F128:
            case FLOAT:
            case FUNCTION_ADDRESS:
            case HALF:
            case I1:
            case I16:
            case I32:
            case I64:
            case I8:
            case ADDRESS:
            case PPC_FP128:
            case STRUCT:
            case VOID:
            case X86_FP80:
            case I_VAR_BITWIDTH:
                return false;
            default:
                throw new AssertionError(llvmType);
        }
    }

    public static LLVMRuntimeType[] convertTypes(LLVMBaseType... llvmParamTypes) {
        LLVMRuntimeType[] types = new LLVMRuntimeType[llvmParamTypes.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = convertType(llvmParamTypes[i]);
        }
        return types;
    }

    public static LLVMRuntimeType convertType(LLVMBaseType llvmReturnType) {
        return LLVMRuntimeType.valueOf(llvmReturnType.toString());
    }

    public static boolean isCompoundType(LLVMBaseType type) {
        return type == LLVMBaseType.ARRAY || type == LLVMBaseType.STRUCT || isVectorType(type);
    }

}
