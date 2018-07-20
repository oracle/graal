/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.memory;

import java.util.function.BinaryOperator;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

public abstract class LLVMMemory {

    /** Use {@link com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode} instead. */
    @Deprecated
    public abstract void memset(LLVMNativePointer address, long size, byte value);

    /** Use {@link com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode} instead. */
    @Deprecated
    public abstract void copyMemory(long sourceAddress, long targetAddress, long length);

    public abstract void free(LLVMNativePointer address);

    public abstract void free(long address);

    public abstract LLVMNativePointer allocateMemory(long size);

    public abstract LLVMNativePointer reallocateMemory(LLVMNativePointer addr, long size);

    /**
     * Allocates {@code #OBJECT_SIZE} bytes in the Kernel space.
     */
    public abstract LLVMNativePointer allocateDerefMemory();

    public abstract boolean getI1(LLVMNativePointer addr);

    public abstract boolean getI1(long ptr);

    public abstract byte getI8(LLVMNativePointer addr);

    public abstract byte getI8(long ptr);

    public abstract short getI16(LLVMNativePointer addr);

    public abstract short getI16(long ptr);

    public abstract int getI32(LLVMNativePointer addr);

    public abstract int getI32(long ptr);

    public abstract LLVMIVarBit getIVarBit(LLVMNativePointer addr, int bitWidth);

    public abstract long getI64(LLVMNativePointer addr);

    public abstract long getI64(long ptr);

    public abstract float getFloat(LLVMNativePointer addr);

    public abstract float getFloat(long ptr);

    public abstract double getDouble(LLVMNativePointer addr);

    public abstract double getDouble(long ptr);

    public abstract LLVM80BitFloat get80BitFloat(LLVMNativePointer addr);

    public abstract LLVMNativePointer getPointer(LLVMNativePointer addr);

    public abstract LLVMNativePointer getPointer(long ptr);

    public abstract void putI1(LLVMNativePointer addr, boolean value);

    public abstract void putI1(long ptr, boolean value);

    public abstract void putI8(LLVMNativePointer addr, byte value);

    public abstract void putI8(long ptr, byte value);

    public abstract void putI16(LLVMNativePointer addr, short value);

    public abstract void putI16(long ptr, short value);

    public abstract void putI32(LLVMNativePointer addr, int value);

    public abstract void putI32(long ptr, int value);

    public abstract void putI64(LLVMNativePointer addr, long value);

    public abstract void putI64(long ptr, long value);

    public abstract void putIVarBit(LLVMNativePointer addr, LLVMIVarBit value);

    public abstract void putFloat(LLVMNativePointer addr, float value);

    public abstract void putFloat(long ptr, float value);

    public abstract void putDouble(LLVMNativePointer addr, double value);

    public abstract void putDouble(long ptr, double value);

    public abstract void put80BitFloat(LLVMNativePointer addr, LLVM80BitFloat value);

    public abstract void put80BitFloat(long ptr, LLVM80BitFloat value);

    public abstract void putPointer(LLVMNativePointer addr, LLVMNativePointer value);

    public abstract void putPointer(LLVMNativePointer addr, long ptrValue);

    public abstract void putPointer(long ptr, LLVMNativePointer value);

    public abstract void putPointer(long ptr, long ptrValue);

    public abstract LLVMI32Vector getI32Vector(LLVMNativePointer address, int vectorLength);

    public abstract LLVMI8Vector getI8Vector(LLVMNativePointer address, int vectorLength);

    public abstract LLVMI1Vector getI1Vector(LLVMNativePointer address, int vectorLength);

    public abstract LLVMI16Vector getI16Vector(LLVMNativePointer address, int vectorLength);

    public abstract LLVMI64Vector getI64Vector(LLVMNativePointer address, int vectorLength);

    public abstract LLVMFloatVector getFloatVector(LLVMNativePointer address, int vectorLength);

    public abstract LLVMDoubleVector getDoubleVector(LLVMNativePointer address, int vectorLength);

    public abstract LLVMPointerVector getPointerVector(LLVMNativePointer address, int vectorLength);

    public abstract void putVector(LLVMNativePointer address, LLVMDoubleVector vector, int vectorLength);

    public abstract void putVector(LLVMNativePointer address, LLVMFloatVector vector, int vectorLength);

    public abstract void putVector(LLVMNativePointer address, LLVMI16Vector vector, int vectorLength);

    public abstract void putVector(LLVMNativePointer address, LLVMI1Vector vector, int vectorLength);

    public abstract void putVector(LLVMNativePointer address, LLVMI32Vector vector, int vectorLength);

    public abstract void putVector(LLVMNativePointer address, LLVMI64Vector vector, int vectorLength);

    public abstract void putVector(LLVMNativePointer address, LLVMI8Vector vector, int vectorLength);

    public abstract void putVector(LLVMNativePointer address, LLVMPointerVector vector, int vectorLength);

    public abstract LLVMNativePointer allocateCString(String string);

    public abstract void putFunctionPointer(LLVMNativePointer address, long functionIndex);

    public abstract void putFunctionPointer(long ptr, long functionIndex);

    public abstract long getFunctionPointer(LLVMNativePointer addr);

    public abstract CMPXCHGI32 compareAndSwapI32(LLVMNativePointer p, int comparisonValue, int newValue);

    public abstract CMPXCHGI64 compareAndSwapI64(LLVMNativePointer p, long comparisonValue, long newValue);

    public abstract CMPXCHGI8 compareAndSwapI8(LLVMNativePointer p, byte comparisonValue, byte newValue);

    public abstract CMPXCHGI16 compareAndSwapI16(LLVMNativePointer p, short comparisonValue, short newValue);

    public abstract long getAndSetI64(LLVMNativePointer address, long value);

    public abstract long getAndAddI64(LLVMNativePointer address, long value);

    public abstract long getAndSubI64(LLVMNativePointer address, long value);

    public abstract long getAndOpI64(LLVMNativePointer address, long value, LongBinaryOperator f);

    public abstract int getAndSetI32(LLVMNativePointer address, int value);

    public abstract int getAndAddI32(LLVMNativePointer address, int value);

    public abstract int getAndSubI32(LLVMNativePointer address, int value);

    public abstract int getAndOpI32(LLVMNativePointer address, int value, IntBinaryOperator f);

    public abstract short getAndOpI16(LLVMNativePointer address, short value, BinaryOperator<Short> f);

    public abstract byte getAndOpI8(LLVMNativePointer address, byte value, BinaryOperator<Byte> f);

    public abstract boolean getAndOpI1(LLVMNativePointer address, boolean value, BinaryOperator<Boolean> f);

    public abstract void fullFence();

    public abstract boolean isDerefMemory(LLVMNativePointer addr);

    public abstract boolean isDerefMemory(long addr);

    @ValueType
    public static final class CMPXCHGI8 {
        private final byte value;
        private final boolean swap;

        public CMPXCHGI8(byte value, boolean swap) {
            this.value = value;
            this.swap = swap;
        }

        public byte getValue() {
            return value;
        }

        public boolean isSwap() {
            return swap;
        }
    }

    @ValueType
    public static final class CMPXCHGI16 {
        private final short value;
        private final boolean swap;

        public CMPXCHGI16(short value, boolean swap) {
            this.value = value;
            this.swap = swap;
        }

        public short getValue() {
            return value;
        }

        public boolean isSwap() {
            return swap;
        }
    }

    @ValueType
    public static final class CMPXCHGI32 {
        private final int value;
        private final boolean swap;

        public CMPXCHGI32(int value, boolean swap) {
            this.value = value;
            this.swap = swap;
        }

        public int getValue() {
            return value;
        }

        public boolean isSwap() {
            return swap;
        }
    }

    @ValueType
    public static final class CMPXCHGI64 {
        private final long value;
        private final boolean swap;

        public CMPXCHGI64(long value, boolean swap) {
            this.value = value;
            this.swap = swap;
        }

        public long getValue() {
            return value;
        }

        public boolean isSwap() {
            return swap;
        }
    }
}
