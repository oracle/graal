/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.types;

import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public interface Type {

    int getBitSize();

    void accept(TypeVisitor visitor);

    int getAlignment(DataSpecConverter targetDataLayout);

    int getSize(DataSpecConverter targetDataLayout);

    static Type getIntegerType(int size) {
        switch (size) {
            case 1:
                return PrimitiveType.I1;
            case 8:
                return PrimitiveType.I8;
            case 16:
                return PrimitiveType.I16;
            case 32:
                return PrimitiveType.I32;
            case 64:
                return PrimitiveType.I64;
            default:
                return new VariableBitWidthType(size);
        }
    }

    static boolean isFunctionOrFunctionPointer(Type type) {
        return type instanceof FunctionType || (type instanceof PointerType && ((PointerType) type).getPointeeType() instanceof FunctionType);
    }

    static Type createConstantForType(Type type, Object value) {
        if (type instanceof PrimitiveType) {
            return new PrimitiveType(((PrimitiveType) type).getKind(), value);
        } else {
            return new VariableBitWidthType(((VariableBitWidthType) type).getBitSize(), value);
        }
    }

    static boolean isIntegerType(Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType primitive = (PrimitiveType) type;
            PrimitiveKind kind = primitive.getKind();
            return kind == PrimitiveKind.I1 || kind == PrimitiveKind.I8 || kind == PrimitiveKind.I16 || kind == PrimitiveKind.I32 || kind == PrimitiveKind.I64;
        }
        return type instanceof VariableBitWidthType;
    }

    static boolean isFloatingpointType(Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType primitive = (PrimitiveType) type;
            PrimitiveKind kind = primitive.getKind();
            return kind == PrimitiveKind.F128 || kind == PrimitiveKind.FLOAT || kind == PrimitiveKind.HALF || kind == PrimitiveKind.PPC_FP128 ||
                            kind == PrimitiveKind.X86_FP80 || kind == PrimitiveKind.DOUBLE;
        }
        return false;
    }

    static FrameSlotKind getFrameSlotKind(Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType primitive = (PrimitiveType) type;
            PrimitiveKind kind = primitive.getKind();
            switch (kind) {
                case FLOAT:
                    return FrameSlotKind.Float;
                case DOUBLE:
                    return FrameSlotKind.Double;
                case I1:
                    return FrameSlotKind.Boolean;
                case I16:
                case I32:
                    return FrameSlotKind.Int;
                case I64:
                    return FrameSlotKind.Long;
                case I8:
                    return FrameSlotKind.Byte;
                default:
                    return FrameSlotKind.Object;

            }
        } else if (type instanceof VariableBitWidthType) {
            switch (type.getBitSize()) {
                case 1:
                    return FrameSlotKind.Boolean;
                case 8:
                    return FrameSlotKind.Byte;
                case 16:
                case 32:
                    return FrameSlotKind.Int;
                case 64:
                    return FrameSlotKind.Long;
                default:
                    return FrameSlotKind.Object;
            }
        }
        return FrameSlotKind.Object;
    }

    static int getPadding(int offset, int alignment) {
        return alignment == 0 ? 0 : (alignment - (offset % alignment)) % alignment;
    }

    static int getPadding(int offset, Type type, DataSpecConverter targetDataLayout) {
        final int alignment = type.getAlignment(targetDataLayout);
        return getPadding(offset, alignment);
    }

}
