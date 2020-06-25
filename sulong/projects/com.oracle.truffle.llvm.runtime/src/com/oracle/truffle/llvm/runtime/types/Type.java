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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.except.LLVMException;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLazyException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.visitors.RecursiveTypeCheckVisitor;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

import java.util.Arrays;
import java.util.List;

public abstract class Type {

    /**
     * Checks whether adding {@code type} to {@code this} would create a cycle. The only allowed
     * cycles are via pointers to named structs.
     */
    protected boolean verifyCycleFree(Type type) {
        RecursiveTypeCheckVisitor.check(this, type);
        return true;
    }

    /**
     * Indicates that calculations based on properties {@link Type} would overflow. For example,
     * calculating the {@link VectorType#getSize size of a vector} containing huge arrays will
     * easily overflow the value range of {@code long}. The exceptions should be caught and
     * presented to the user in a meaningful way, e.g., by wrapping it in an
     * {@link LLVMUnsupportedException} with reason {@code UNSUPPORTED_VALUE_RANGE}.
     */
    public static final class TypeOverflowException extends Exception {
        private static final long serialVersionUID = 2239196977333486425L;

        public TypeOverflowException(Throwable cause) {
            super(cause);
        }

        public TypeOverflowException(String message) {
            super(message);
        }
    }

    /**
     * Wraps an {@code TypeOverflowException} in a @code {@link RuntimeException} so that it can be
     * used in {@code Streams}.
     */
    protected static final class TypeOverflowExceptionUnchecked extends RuntimeException {
        private static final long serialVersionUID = 1284366666528782360L;

        public TypeOverflowExceptionUnchecked(TypeOverflowException cause) {
            super(cause);
        }

        @Override
        public synchronized TypeOverflowException getCause() {
            return (TypeOverflowException) super.getCause();
        }
    }

    public static final Type[] EMPTY_ARRAY = {};

    /**
     * Encapsulates an array of {@link Type types}. Use to ensure that the array reference is not
     * leaked and modified out of place. The {@link TypeArrayBuilder} is never stored in a
     * {@link Type} but only used for construction.
     */
    public static class TypeArrayBuilder {
        private Type[] types;

        public TypeArrayBuilder(int size) {
            this.types = new Type[size];
        }

        public void set(int idx, Type type) {
            check();
            types[idx] = type;
        }

        public Type get(int idx) {
            check();
            return types[idx];
        }

        public int size() {
            check();
            return types.length;
        }

        public List<Type> asList() {
            return Arrays.asList(getRawArray());
        }

        private Type[] getRawArray() {
            check();
            Type[] ret = this.types;
            this.types = null;
            return ret;
        }

        private void check() {
            if (types == null) {
                throw new IllegalStateException("TypeArray already finalized");
            }
        }
    }

    /**
     * Gets the raw type array stored in a {@link TypeArrayBuilder}. This method "finalizes" the
     * type array, i.e., the elements can no longer be {@linkplain TypeArrayBuilder#get accessed} or
     * {@linkplain TypeArrayBuilder#set modified}. This method can only be called once for a give
     * {@link TypeArrayBuilder}.
     */
    public static Type[] getRawTypeArray(TypeArrayBuilder types) {
        return types.getRawArray();
    }

    public abstract long getBitSize() throws TypeOverflowException;

    /**
     * Wrapped version of {@code #getBitSize} for use within {@code Streams}. Surround usages with
     * try-catch and rethrow the {@code TypeOverflowExceptionUnchecked#getCause}.
     */
    protected final long getBitSizeUnchecked() {
        try {
            return getBitSize();
        } catch (TypeOverflowException e) {
            throw new TypeOverflowExceptionUnchecked(e);
        }
    }

    public abstract void accept(TypeVisitor visitor);

    public abstract int getAlignment(DataLayout targetDataLayout);

    public abstract long getSize(DataLayout targetDataLayout) throws TypeOverflowException;

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    public static Type getIntegerType(int size) {
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

    public static Type createConstantForType(Type type, Object value) {
        if (type instanceof PrimitiveType) {
            return new PrimitiveType(((PrimitiveType) type).getPrimitiveKind(), value);
        } else {
            return new VariableBitWidthType(((VariableBitWidthType) type).getBitSizeInt(), value);
        }
    }

    public static boolean isIntegerType(Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType primitive = (PrimitiveType) type;
            PrimitiveKind kind = primitive.getPrimitiveKind();
            return kind == PrimitiveKind.I1 || kind == PrimitiveKind.I8 || kind == PrimitiveKind.I16 || kind == PrimitiveKind.I32 || kind == PrimitiveKind.I64;
        }
        return type instanceof VariableBitWidthType;
    }

    public static boolean isFloatingpointType(Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType primitive = (PrimitiveType) type;
            PrimitiveKind kind = primitive.getPrimitiveKind();
            return kind == PrimitiveKind.F128 || kind == PrimitiveKind.FLOAT || kind == PrimitiveKind.HALF || kind == PrimitiveKind.PPC_FP128 ||
                            kind == PrimitiveKind.X86_FP80 || kind == PrimitiveKind.DOUBLE;
        }
        return false;
    }

    public static FrameSlotKind getFrameSlotKind(Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveType primitive = (PrimitiveType) type;
            PrimitiveKind kind = primitive.getPrimitiveKind();
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
            long bitSize = ((VariableBitWidthType) type).getBitSize();
            if (fitsIntoUnsignedInt(bitSize)) {
                switch (toUnsignedInt(bitSize)) {
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
        }
        return FrameSlotKind.Object;
    }

    public static int getPadding(long offset, int alignment) {
        assert (alignment == 0 ? 0 : (alignment - (offset % alignment)) % alignment) == (int) (alignment == 0 ? 0 : (alignment - (offset % alignment)) % alignment);
        return (int) (alignment == 0 ? 0 : (alignment - (offset % alignment)) % alignment);
    }

    public static int getPadding(long offset, Type type, DataLayout targetDataLayout) {
        final int alignment = type.getAlignment(targetDataLayout);
        return getPadding(offset, alignment);
    }

    public static boolean fitsIntoUnsignedInt(long l) {
        return (l & 0xFFFF_FFFF_0000_0000L) == 0;
    }

    public static int toUnsignedInt(long l) {
        assert fitsIntoUnsignedInt(l);
        return (int) l; // drop 32 MSB
    }

    public static long multiplyUnsignedExact(long x, long y) throws TypeOverflowException {
        long res = x * y;
        long overflow = ExactMath.multiplyHighUnsigned(x, y);
        if (overflow != 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new TypeOverflowException("unsigned multiplication overflow");
        }
        return res;
    }

    public static long multiplySignedExact(long x, long y) throws TypeOverflowException {
        try {
            return Math.multiplyExact(x, y);
        } catch (ArithmeticException e) {
            throw new TypeOverflowException(e);
        }
    }

    public static long addUnsignedExact(long x, long y) throws TypeOverflowException {
        long res = x + y;
        if (Long.compareUnsigned(res, x) < 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new TypeOverflowException("unsigned addition overflow");
        }
        return res;
    }

    public static long subUnsignedExact(long x, long y) throws TypeOverflowException {
        if (Long.compareUnsigned(x, y) < 0) {
            throw new TypeOverflowException("unsigned subtraction underflow");
        }
        return x - y;
    }

    /**
     * Wrapped version of {@code #addExact} for use within {@code Streams}. Surround usages with
     * try-catch and rethrow the {@code TypeOverflowExceptionUnchecked#getCause}.
     */
    protected static long addUnsignedExactUnchecked(long x, long y) {
        try {
            return addUnsignedExact(x, y);
        } catch (TypeOverflowException e) {
            throw new TypeOverflowExceptionUnchecked(e);
        }
    }

    /**
     * Creates an {@link LLVMExpressionNode} that will throw an {@link LLVMUnsupportedException}
     * when executed.
     */
    public static LLVMExpressionNode handleOverflowExpression(TypeOverflowException e) {
        return LLVMLazyException.createExpressionNode(Type::throwOverflowExceptionAsLLVMException, e);
    }

    /**
     * Creates an {@link LLVMStatementNode} that will throw an {@link LLVMUnsupportedException} when
     * executed.
     */
    public static LLVMStatementNode handleOverflowStatement(TypeOverflowException e) {
        return LLVMLazyException.createStatementNode(Type::throwOverflowExceptionAsLLVMException, e);
    }

    /**
     * Creates an {@link LLVMAllocateNode} that will throw an {@link LLVMUnsupportedException} when
     * executed.
     */
    public static LLVMAllocateNode handleOverflowAllocate(TypeOverflowException e) {
        return LLVMLazyException.createAllocateNode(Type::throwOverflowExceptionAsLLVMException, e);
    }

    public static LLVMException throwOverflowExceptionAsLLVMException(Node node, TypeOverflowException e) {
        CompilerDirectives.transferToInterpreter();
        throw new LLVMUnsupportedException(node, LLVMUnsupportedException.UnsupportedReason.UNSUPPORTED_VALUE_RANGE, e);
    }
}
