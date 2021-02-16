/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.convert;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public abstract class ForeignToLLVM extends LLVMNode {

    public abstract Object executeWithTarget(Object value);

    public abstract Object executeWithType(Object value, LLVMInteropType.Structured type);

    public abstract Object executeWithForeignToLLVMType(Object value, LLVMInteropType.Structured type, ForeignToLLVMType ftlType);

    protected char getSingleStringCharacter(String value) {
        if (value.length() == 1) {
            return value.charAt(0);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(this, "Expected number but got string.");
        }
    }

    public enum ForeignToLLVMType {
        I1(1),
        I8(1),
        I16(2),
        I32(4),
        I64(8),
        FLOAT(4),
        DOUBLE(8),
        POINTER(8),
        VECTOR(-1),
        ARRAY(-1),
        STRUCT(-1),
        ANY(-1),
        VOID(-1);

        private final int size;

        ForeignToLLVMType(int size) {
            this.size = size;
        }

        public static ForeignToLLVMType getIntegerType(int bitWidth) {
            switch (bitWidth) {
                case 1:
                    return ForeignToLLVMType.I1;
                case 8:
                    return ForeignToLLVMType.I8;
                case 16:
                    return ForeignToLLVMType.I16;
                case 32:
                    return ForeignToLLVMType.I32;
                case 64:
                    return ForeignToLLVMType.I64;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException("There is no integer type with " + bitWidth + " bits defined");
            }
        }

        public int getSizeInBytes() {
            assert size > 0;
            return size;
        }

        public boolean isI1() {
            return this == ForeignToLLVMType.I1;
        }

        public boolean isI8() {
            return this == ForeignToLLVMType.I8;
        }

        public boolean isI16() {
            return this == ForeignToLLVMType.I16;
        }

        public boolean isI32() {
            return this == ForeignToLLVMType.I32;
        }

        public boolean isI64() {
            return this == ForeignToLLVMType.I64;
        }

        public boolean isFloat() {
            return this == ForeignToLLVMType.FLOAT;
        }

        public boolean isDouble() {
            return this == ForeignToLLVMType.DOUBLE;
        }

        public boolean isPointer() {
            return this == ForeignToLLVMType.POINTER;
        }

        public static Object getDefaultValue(ForeignToLLVMType type) {
            switch (type) {
                case I1:
                    return false;
                case I8:
                    return (byte) 0;
                case I16:
                    return (short) 0;
                case I32:
                    return 0;
                case I64:
                case POINTER:
                    return 0L;
                case FLOAT:
                    return 0f;
                case DOUBLE:
                    return 0d;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException("Unexpected value: " + type);
            }
        }
    }

    public static ForeignToLLVMType convert(Type type) {
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return ForeignToLLVMType.I1;
                case I8:
                    return ForeignToLLVMType.I8;
                case I16:
                    return ForeignToLLVMType.I16;
                case I32:
                    return ForeignToLLVMType.I32;
                case I64:
                    return ForeignToLLVMType.I64;
                case FLOAT:
                    return ForeignToLLVMType.FLOAT;
                case DOUBLE:
                    return ForeignToLLVMType.DOUBLE;
                default:
                    throw new IllegalStateException("unexpected primitive kind " + ((PrimitiveType) type).getPrimitiveKind());
            }
        } else if (type instanceof PointerType) {
            return ForeignToLLVMType.POINTER;
        } else if (type instanceof VoidType) {
            return ForeignToLLVMType.VOID;
        } else if (type instanceof VectorType) {
            return ForeignToLLVMType.VECTOR;
        } else if (type instanceof ArrayType) {
            return ForeignToLLVMType.ARRAY;
        } else if (type instanceof StructureType) {
            return ForeignToLLVMType.STRUCT;
        } else {
            throw new IllegalStateException("unexpected type " + type);
        }
    }

    public static SlowPathForeignToLLVM getUncached() {
        return SlowPathForeignToLLVM.INSTANCE;
    }

    public static final class SlowPathForeignToLLVM extends ForeignToLLVM {

        private static final SlowPathForeignToLLVM INSTANCE = new SlowPathForeignToLLVM();

        @TruffleBoundary
        public Object convert(Type type, Object value, LLVMInteropType.Structured interopType) throws UnsupportedTypeException {
            return convert(ForeignToLLVM.convert(type), value, interopType);
        }

        @TruffleBoundary
        public Object convert(ForeignToLLVMType type, Object value, LLVMInteropType.Structured interopType) throws UnsupportedTypeException {
            if (type == ForeignToLLVMType.ANY) {
                return ToAnyLLVM.slowPathPrimitiveConvert(value);
            } else if (type == ForeignToLLVMType.POINTER) {
                return ToPointer.slowPathPrimitiveConvert(value, interopType);
            } else {
                switch (type) {
                    case DOUBLE:
                        return ToDouble.slowPathPrimitiveConvert(this, value);
                    case FLOAT:
                        return ToFloat.slowPathPrimitiveConvert(this, value);
                    case I1:
                        return ToI1.slowPathPrimitiveConvert(this, value);
                    case I16:
                        return ToI16.slowPathPrimitiveConvert(this, value);
                    case I32:
                        return ToI32.slowPathPrimitiveConvert(this, value);
                    case I64:
                        return ToI64.slowPathPrimitiveConvert(this, value);
                    case I8:
                        return ToI8.slowPathPrimitiveConvert(this, value);
                    default:
                        throw new IllegalStateException(type.toString());
                }
            }
        }

        @Override
        public Object executeWithTarget(Object value) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Use convert method.");
        }

        @Override
        public Object executeWithType(Object value, LLVMInteropType.Structured type) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Use convert method.");
        }

        @Override
        public Object executeWithForeignToLLVMType(Object value, LLVMInteropType.Structured type, ForeignToLLVMType ftlType) {
            try {
                return convert(ftlType, value, type);
            } catch (UnsupportedTypeException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(this, "Unexpected foreign object type.");
            }
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }
    }
}
