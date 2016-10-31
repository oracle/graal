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
package com.oracle.truffle.llvm.parser.base.util;

import com.intel.llvm.ireditor.lLVM_IR.StructureConstant;
import com.intel.llvm.ireditor.lLVM_IR.TypedConstant;
import com.intel.llvm.ireditor.types.ResolvedAnyIntegerType;
import com.intel.llvm.ireditor.types.ResolvedArrayType;
import com.intel.llvm.ireditor.types.ResolvedFloatingType;
import com.intel.llvm.ireditor.types.ResolvedIntegerType;
import com.intel.llvm.ireditor.types.ResolvedNamedType;
import com.intel.llvm.ireditor.types.ResolvedPointerType;
import com.intel.llvm.ireditor.types.ResolvedStructType;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.intel.llvm.ireditor.types.ResolvedVectorType;
import com.intel.llvm.ireditor.types.ResolvedVoidType;
import com.intel.llvm.ireditor.types.TypeResolver;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMType;
import com.oracle.truffle.llvm.parser.base.model.LLVMToBitcodeAdapter;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.PointerType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;

public class LLVMTypeHelper {

    private final LLVMParserRuntime runtime;

    public LLVMTypeHelper(LLVMParserRuntime runtime) {
        this.runtime = runtime;
    }

    public int getByteSize(ResolvedType type) {
        return runtime.getByteSize(LLVMToBitcodeAdapter.resolveType(type));
    }

    public int getStructureSizeByte(StructureConstant structure, TypeResolver typeResolver) {
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

    private int getStructSizeByte(ResolvedStructType type) {
        return runtime.getByteSize(LLVMToBitcodeAdapter.resolveType(type));
    }

    private static int computePadding(int offset, int alignment) {
        if (alignment == 0) {
            throw new AssertionError();
        }
        int padding = (alignment - (offset % alignment)) % alignment;
        return padding;
    }

    public static LLVMType getLLVMType(Type type) {
        if (type instanceof PointerType) {
            final Type pointeeType = ((PointerType) type).getPointeeType();
            if (pointeeType instanceof FunctionType) {
                return new LLVMType(LLVMBaseType.FUNCTION_ADDRESS);
            } else {
                return new LLVMType(LLVMBaseType.ADDRESS, getLLVMType(pointeeType));
            }
        }
        return new LLVMType(type.getLLVMBaseType());
    }

    // Checkstyle: stop magic number name check
    public static LLVMType getLLVMType(ResolvedType elementType) {
        if (elementType instanceof ResolvedIntegerType) {
            switch (elementType.getBits().intValue()) {
                case 1:
                    return new LLVMType(LLVMBaseType.I1);
                case 8:
                    return new LLVMType(LLVMBaseType.I8);
                case 16:
                    return new LLVMType(LLVMBaseType.I16);
                case 32:
                    return new LLVMType(LLVMBaseType.I32);
                case 64:
                    return new LLVMType(LLVMBaseType.I64);
                default:
                    return new LLVMType(LLVMBaseType.I_VAR_BITWIDTH);
            }
        } else if (elementType instanceof ResolvedFloatingType) {
            switch (elementType.getBits().intValue()) {
                case 32:
                    return new LLVMType(LLVMBaseType.FLOAT);
                case 64:
                    return new LLVMType(LLVMBaseType.DOUBLE);
                case 80:
                    return new LLVMType(LLVMBaseType.X86_FP80);
                default:
                    throw new LLVMUnsupportedException(UnsupportedReason.FLOAT_OTHER_TYPE_NOT_IMPLEMENTED);
            }
        } else if (elementType.isPointer()) {
            if (elementType.getContainedType(0).isFunction()) {
                return new LLVMType(LLVMBaseType.FUNCTION_ADDRESS);
            } else {
                try {
                    return new LLVMType(LLVMBaseType.ADDRESS, getLLVMType(elementType.getContainedType(0)));
                } catch (LLVMUnsupportedException e) {
                    // generic pointer
                    return new LLVMType(LLVMBaseType.ADDRESS, new LLVMType(LLVMBaseType.VOID));
                }
            }
        } else if (elementType instanceof ResolvedVoidType) {
            return new LLVMType(LLVMBaseType.VOID);
        } else if (elementType instanceof ResolvedArrayType) {
            return new LLVMType(LLVMBaseType.ARRAY);
        } else if (elementType.isStruct()) {
            return new LLVMType(LLVMBaseType.STRUCT);
        } else if (elementType instanceof ResolvedVectorType) {
            switch (getLLVMType(elementType.getContainedType(-1)).getType()) {
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
                case ADDRESS:
                    return new LLVMType(LLVMBaseType.ADDRESS_VECTOR, getLLVMType(elementType.getContainedType(-1)).getPointee());
                default:
                    throw new AssertionError(elementType);
            }
        } else if (elementType instanceof ResolvedAnyIntegerType) {
            return new LLVMType(LLVMBaseType.I32);
        }

        else {
            throw new LLVMUnsupportedException(UnsupportedReason.OTHER_TYPE_NOT_IMPLEMENTED);
        }
    }// Checkstyle: resume magic number name check

    public int goIntoTypeGetLengthByte(Type type, int index) {
        return goIntoTypeGetLengthByte(LLVMToBitcodeAdapter.unresolveType(type), index);
    }

    public int goIntoTypeGetLengthByte(ResolvedType currentType, int index) {
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
            if (!isPackedStructType(currentType) && getStructSizeByte((ResolvedStructType) currentType) > sum) {
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

    public int computePaddingByte(int currentOffset, ResolvedType type) {
        return runtime.getBytePadding(currentOffset, LLVMToBitcodeAdapter.resolveType(type));
    }

    public int getAlignmentByte(ResolvedType field) {
        return runtime.getByteAlignment(LLVMToBitcodeAdapter.resolveType(field));
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

    public static LLVMRuntimeType[] convertTypes(LLVMType... llvmParamTypes) {
        LLVMRuntimeType[] types = new LLVMRuntimeType[llvmParamTypes.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = convertType(llvmParamTypes[i]);
        }
        return types;
    }

    public static LLVMRuntimeType convertType(LLVMType llvmReturnType) {
        if (llvmReturnType.isPointer()) {
            switch (llvmReturnType.getPointee().getType()) {
                case I1:
                    return LLVMRuntimeType.I1_POINTER;
                case I8:
                    return LLVMRuntimeType.I8_POINTER;
                case I16:
                    return LLVMRuntimeType.I16_POINTER;
                case I32:
                    return LLVMRuntimeType.I32_POINTER;
                case I64:
                    return LLVMRuntimeType.I64_POINTER;
                case HALF:
                    return LLVMRuntimeType.HALF_POINTER;
                case FLOAT:
                    return LLVMRuntimeType.FLOAT_POINTER;
                case DOUBLE:
                    return LLVMRuntimeType.DOUBLE_POINTER;
                default:
                    return LLVMRuntimeType.ADDRESS;
            }
        } else {
            return LLVMRuntimeType.valueOf(llvmReturnType.getType().toString());
        }
    }

    public static boolean isCompoundType(LLVMBaseType type) {
        return type == LLVMBaseType.ARRAY || type == LLVMBaseType.STRUCT || isVectorType(type);
    }

}
