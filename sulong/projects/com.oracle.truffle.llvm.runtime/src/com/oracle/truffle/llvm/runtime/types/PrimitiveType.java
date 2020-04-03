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
package com.oracle.truffle.llvm.runtime.types;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public final class PrimitiveType extends Type {

    public static final PrimitiveType I1 = new PrimitiveType(PrimitiveKind.I1, null);
    public static final PrimitiveType I8 = new PrimitiveType(PrimitiveKind.I8, null);
    public static final PrimitiveType I16 = new PrimitiveType(PrimitiveKind.I16, null);
    public static final PrimitiveType I32 = new PrimitiveType(PrimitiveKind.I32, null);
    public static final PrimitiveType I64 = new PrimitiveType(PrimitiveKind.I64, null);
    public static final PrimitiveType HALF = new PrimitiveType(PrimitiveKind.HALF, null);
    public static final PrimitiveType FLOAT = new PrimitiveType(PrimitiveKind.FLOAT, null);
    public static final PrimitiveType DOUBLE = new PrimitiveType(PrimitiveKind.DOUBLE, null);
    public static final PrimitiveType F128 = new PrimitiveType(PrimitiveKind.F128, null);
    public static final PrimitiveType X86_FP80 = new PrimitiveType(PrimitiveKind.X86_FP80, null);
    public static final PrimitiveType PPC_FP128 = new PrimitiveType(PrimitiveKind.PPC_FP128, null);

    public enum PrimitiveKind {
        I1(1),
        I8(8),
        I16(16),
        I32(32),
        I64(64),
        HALF(16),
        FLOAT(32),
        DOUBLE(64),
        F128(128),
        X86_FP80(80),
        PPC_FP128(128);

        private final int sizeInBits;

        PrimitiveKind(int sizeInBits) {
            this.sizeInBits = sizeInBits;
        }

        public int getSizeInBits() {
            return sizeInBits;
        }
    }

    public static PrimitiveType forKind(PrimitiveKind kind) {
        switch (kind) {
            case I1:
                return PrimitiveType.I1;
            case I8:
                return PrimitiveType.I8;
            case I16:
                return PrimitiveType.I16;
            case I32:
                return PrimitiveType.I32;
            case I64:
                return PrimitiveType.I64;
            case HALF:
                return PrimitiveType.HALF;
            case FLOAT:
                return PrimitiveType.FLOAT;
            case DOUBLE:
                return PrimitiveType.DOUBLE;
            case F128:
                return PrimitiveType.F128;
            case X86_FP80:
                return PrimitiveType.X86_FP80;
            case PPC_FP128:
                return PrimitiveType.PPC_FP128;
            default:
                throw new IllegalStateException("Unknown type");
        }
    }

    private final PrimitiveKind kind;
    private final Object constant;

    PrimitiveType(PrimitiveKind kind, Object constant) {
        this.kind = kind;
        this.constant = constant;
    }

    public boolean isConstant() {
        return constant != null;
    }

    public Object getConstant() {
        return constant;
    }

    public PrimitiveKind getPrimitiveKind() {
        return kind;
    }

    @Override
    public long getBitSize() {
        return kind.sizeInBits;
    }

    @Override
    public void accept(TypeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int getAlignment(DataLayout targetDataLayout) {
        if (targetDataLayout != null) {
            return targetDataLayout.getBitAlignment(this) / Byte.SIZE;

        } else if (getBitSize() <= Byte.SIZE) {
            return Byte.BYTES;

        } else if (getBitSize() <= Short.SIZE) {
            return Short.BYTES;

        } else if (getBitSize() <= Integer.SIZE) {
            return Integer.BYTES;

        } else {
            return Long.BYTES;
        }
    }

    @Override
    public long getSize(DataLayout targetDataLayout) {
        try {
            return targetDataLayout.getSize(this);
        } catch (TypeOverflowException e) {
            // should not reach here
            throw new AssertionError(e);
        }
    }

    @Override
    @TruffleBoundary
    public String toString() {
        if (Type.isIntegerType(this)) {
            return String.format("i%d", getBitSize());
        } else {
            return kind.name().toLowerCase();
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((constant == null) ? 0 : constant.hashCode());
        result = prime * result + ((kind == null) ? 0 : kind.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PrimitiveType other = (PrimitiveType) obj;
        if (constant == null) {
            if (other.constant != null) {
                return false;
            }
        } else if (!constant.equals(other.constant)) {
            return false;
        }
        if (kind != other.kind) {
            return false;
        }
        return true;
    }
}
