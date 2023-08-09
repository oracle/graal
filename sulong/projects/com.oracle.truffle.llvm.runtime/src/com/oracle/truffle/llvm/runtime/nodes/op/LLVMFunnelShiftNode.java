/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.op;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesLongPointer;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin.TypedBuiltinFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativePointerSupport;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshl_I16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshl_I16VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshl_I32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshl_I32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshl_I64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshl_I64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshl_I8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshl_I8VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshr_I16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshr_I16VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshr_I32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshr_I32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshr_I64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshr_I64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshr_I8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNodeFactory.Fshr_I8VectorNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild("left")
@NodeChild("right")
@NodeChild("shift")
public abstract class LLVMFunnelShiftNode extends LLVMExpressionNode {

    public static TypedBuiltinFactory getFshlFactory(PrimitiveKind type) {
        switch (type) {
            case I8:
                return TypedBuiltinFactory.vector3(Fshl_I8NodeGen::create, Fshl_I8VectorNodeGen::create);
            case I16:
                return TypedBuiltinFactory.vector3(Fshl_I16NodeGen::create, Fshl_I16VectorNodeGen::create);
            case I32:
                return TypedBuiltinFactory.vector3(Fshl_I32NodeGen::create, Fshl_I32VectorNodeGen::create);
            case I64:
                return TypedBuiltinFactory.vector3(Fshl_I64NodeGen::create, Fshl_I64VectorNodeGen::create);
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getFshrFactory(PrimitiveKind type) {
        switch (type) {
            case I8:
                return TypedBuiltinFactory.vector3(Fshr_I8NodeGen::create, Fshr_I8VectorNodeGen::create);
            case I16:
                return TypedBuiltinFactory.vector3(Fshr_I16NodeGen::create, Fshr_I16VectorNodeGen::create);
            case I32:
                return TypedBuiltinFactory.vector3(Fshr_I32NodeGen::create, Fshr_I32VectorNodeGen::create);
            case I64:
                return TypedBuiltinFactory.vector3(Fshr_I64NodeGen::create, Fshr_I64VectorNodeGen::create);
            default:
                return null;
        }
    }

    public abstract static class Fshl_I8 extends LLVMFunnelShiftNode {

        @Specialization
        byte doFshl(byte left, byte right, byte shift) {
            return (byte) ((left << shift) | (byte) ((right & 0xFF) >>> (Byte.SIZE - shift)));
        }
    }

    public abstract static class Fshl_I8Vector extends LLVMFunnelShiftNode {

        private final int vectorSize;

        Fshl_I8Vector(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        LLVMI8Vector doFshl(LLVMI8Vector left, LLVMI8Vector right, LLVMI8Vector shift) {
            assert left.getLength() == vectorSize && right.getLength() == vectorSize && shift.getLength() == vectorSize;
            byte[] ret = new byte[vectorSize];
            for (int i = 0; i < vectorSize; i++) {
                int sh = shift.getValue(i);
                ret[i] = (byte) ((left.getValue(i) << sh) | (byte) ((right.getValue(i) & 0xFF) >>> (Byte.SIZE - sh)));
            }
            return LLVMI8Vector.create(ret);
        }
    }

    public abstract static class Fshl_I16 extends LLVMFunnelShiftNode {

        @Specialization
        short doFshl(short left, short right, short shift) {
            return (short) ((left << shift) | (short) ((right & 0xFFFF) >>> (Short.SIZE - shift)));
        }
    }

    public abstract static class Fshl_I16Vector extends LLVMFunnelShiftNode {

        private final int vectorSize;

        Fshl_I16Vector(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        LLVMI16Vector doFshl(LLVMI16Vector left, LLVMI16Vector right, LLVMI16Vector shift) {
            assert left.getLength() == vectorSize && right.getLength() == vectorSize && shift.getLength() == vectorSize;
            short[] ret = new short[vectorSize];
            for (int i = 0; i < vectorSize; i++) {
                int sh = shift.getValue(i);
                ret[i] = (short) ((left.getValue(i) << sh) | (short) ((right.getValue(i) & 0xFFFF) >>> (Short.SIZE - sh)));
            }
            return LLVMI16Vector.create(ret);
        }
    }

    public abstract static class Fshl_I32 extends LLVMFunnelShiftNode {

        @Specialization
        int doFshl(int left, int right, int shift) {
            return (left << shift) | (right >>> (Integer.SIZE - shift));
        }
    }

    public abstract static class Fshl_I32Vector extends LLVMFunnelShiftNode {

        private final int vectorSize;

        Fshl_I32Vector(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        LLVMI32Vector doFshl(LLVMI32Vector left, LLVMI32Vector right, LLVMI32Vector shift) {
            assert left.getLength() == vectorSize && right.getLength() == vectorSize && shift.getLength() == vectorSize;
            int[] ret = new int[vectorSize];
            for (int i = 0; i < vectorSize; i++) {
                int sh = shift.getValue(i);
                ret[i] = (left.getValue(i) << sh) | (right.getValue(i) >>> (Integer.SIZE - sh));
            }
            return LLVMI32Vector.create(ret);
        }
    }

    @TypeSystemReference(LLVMTypesLongPointer.class)
    public abstract static class Fshl_I64 extends LLVMFunnelShiftNode {

        @Specialization
        long doFshl(long left, long right, long shift) {
            return (left << shift) | (right >>> (Long.SIZE - shift));
        }

        @Specialization
        long doFshl(LLVMPointer left, LLVMPointer right, long shift,
                        @Cached LLVMNativePointerSupport.ToNativePointerNode toNativePointerLeft,
                        @Cached LLVMNativePointerSupport.ToNativePointerNode toNativePointerRight) {
            return doFshl(toNativePointerLeft.execute(left).asNative(), toNativePointerRight.execute(right).asNative(), shift);
        }
    }

    public abstract static class Fshl_I64Vector extends LLVMFunnelShiftNode {

        private final int vectorSize;

        Fshl_I64Vector(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        LLVMI64Vector doFshl(LLVMI64Vector left, LLVMI64Vector right, LLVMI64Vector shift) {
            assert left.getLength() == vectorSize && right.getLength() == vectorSize && shift.getLength() == vectorSize;
            long[] ret = new long[vectorSize];
            for (int i = 0; i < vectorSize; i++) {
                long sh = shift.getValue(i);
                ret[i] = (left.getValue(i) << sh) | (right.getValue(i) >>> (Long.SIZE - sh));
            }
            return LLVMI64Vector.create(ret);
        }
    }

    public abstract static class Fshr_I8 extends LLVMFunnelShiftNode {

        @Specialization
        byte doFshl(byte left, byte right, byte shift) {
            return (byte) ((left << (Byte.SIZE - shift)) | (byte) ((right & 0xFF) >>> shift));
        }
    }

    public abstract static class Fshr_I8Vector extends LLVMFunnelShiftNode {

        private final int vectorSize;

        Fshr_I8Vector(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        LLVMI8Vector doFshl(LLVMI8Vector left, LLVMI8Vector right, LLVMI8Vector shift) {
            assert left.getLength() == vectorSize && right.getLength() == vectorSize && shift.getLength() == vectorSize;
            byte[] ret = new byte[vectorSize];
            for (int i = 0; i < vectorSize; i++) {
                int sh = shift.getValue(i);
                ret[i] = (byte) ((left.getValue(i) << (Byte.SIZE - sh)) | (byte) ((right.getValue(i) & 0xFF) >>> sh));
            }
            return LLVMI8Vector.create(ret);
        }
    }

    public abstract static class Fshr_I16 extends LLVMFunnelShiftNode {

        @Specialization
        short doFshl(short left, short right, short shift) {
            return (short) ((left << (Short.SIZE - shift)) | (short) ((right & 0xFFFF) >>> shift));
        }
    }

    public abstract static class Fshr_I16Vector extends LLVMFunnelShiftNode {

        private final int vectorSize;

        Fshr_I16Vector(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        LLVMI16Vector doFshl(LLVMI16Vector left, LLVMI16Vector right, LLVMI16Vector shift) {
            assert left.getLength() == vectorSize && right.getLength() == vectorSize && shift.getLength() == vectorSize;
            short[] ret = new short[vectorSize];
            for (int i = 0; i < vectorSize; i++) {
                int sh = shift.getValue(i);
                ret[i] = (short) ((left.getValue(i) << (Short.SIZE - sh)) | (short) ((right.getValue(i) & 0xFFFF) >>> sh));
            }
            return LLVMI16Vector.create(ret);
        }
    }

    public abstract static class Fshr_I32 extends LLVMFunnelShiftNode {

        @Specialization
        int doFshl(int left, int right, int shift) {
            return (left << (Integer.SIZE - shift)) | (right >>> shift);
        }
    }

    public abstract static class Fshr_I32Vector extends LLVMFunnelShiftNode {

        private final int vectorSize;

        Fshr_I32Vector(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        LLVMI32Vector doFshl(LLVMI32Vector left, LLVMI32Vector right, LLVMI32Vector shift) {
            assert left.getLength() == vectorSize && right.getLength() == vectorSize && shift.getLength() == vectorSize;
            int[] ret = new int[vectorSize];
            for (int i = 0; i < vectorSize; i++) {
                int sh = shift.getValue(i);
                ret[i] = (left.getValue(i) << (Integer.SIZE - sh)) | (right.getValue(i) >>> sh);
            }
            return LLVMI32Vector.create(ret);
        }
    }

    public abstract static class Fshr_I64 extends LLVMFunnelShiftNode {

        @Specialization
        long doFshl(long left, long right, long shift) {
            return (left << (Long.SIZE - shift)) | (right >>> shift);
        }
    }

    public abstract static class Fshr_I64Vector extends LLVMFunnelShiftNode {

        private final int vectorSize;

        Fshr_I64Vector(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        LLVMI64Vector doFshl(LLVMI64Vector left, LLVMI64Vector right, LLVMI64Vector shift) {
            assert left.getLength() == vectorSize && right.getLength() == vectorSize && shift.getLength() == vectorSize;
            long[] ret = new long[vectorSize];
            for (int i = 0; i < vectorSize; i++) {
                long sh = shift.getValue(i);
                ret[i] = (left.getValue(i) << (Long.SIZE - sh)) | (right.getValue(i) >>> sh);
            }
            return LLVMI64Vector.create(ret);
        }
    }
}
