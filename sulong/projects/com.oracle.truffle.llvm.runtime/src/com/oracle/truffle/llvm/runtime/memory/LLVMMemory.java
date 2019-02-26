/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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

import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVMMemory {

    /** Use {@link com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode} instead. */
    @Deprecated
    public abstract void memset(LLVMNativePointer address, long size, byte value);

    /** Use {@link com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode} instead. */
    @Deprecated
    public abstract void copyMemory(long sourceAddress, long targetAddress, long length);

    public final void free(LLVMNativePointer address) {
        free(address.asNative());
    }

    public abstract void free(long address);

    public abstract LLVMNativePointer allocateMemory(long size);

    /**
     * Use a realloc node instead.
     */
    @Deprecated
    public abstract LLVMNativePointer reallocateMemory(LLVMNativePointer addr, long size);

    public final boolean getI1(LLVMNativePointer addr) {
        return getI1(addr.asNative());
    }

    public abstract boolean getI1(long ptr);

    public final byte getI8(LLVMNativePointer addr) {
        return getI8(addr.asNative());
    }

    public abstract byte getI8(long ptr);

    public final short getI16(LLVMNativePointer addr) {
        return getI16(addr.asNative());
    }

    public abstract short getI16(long ptr);

    public final int getI32(LLVMNativePointer addr) {
        return getI32(addr.asNative());
    }

    public abstract int getI32(long ptr);

    public abstract LLVMIVarBit getIVarBit(LLVMNativePointer addr, int bitWidth);

    public final long getI64(LLVMNativePointer addr) {
        return getI64(addr.asNative());
    }

    public abstract long getI64(long ptr);

    public final float getFloat(LLVMNativePointer addr) {
        return getFloat(addr.asNative());
    }

    public abstract float getFloat(long ptr);

    public final double getDouble(LLVMNativePointer addr) {
        return getDouble(addr.asNative());
    }

    public abstract double getDouble(long ptr);

    public abstract LLVM80BitFloat get80BitFloat(LLVMNativePointer addr);

    public final LLVMNativePointer getPointer(LLVMNativePointer addr) {
        return getPointer(addr.asNative());
    }

    public abstract LLVMNativePointer getPointer(long ptr);

    public final void putI1(LLVMNativePointer addr, boolean value) {
        putI1(addr.asNative(), value);
    }

    public abstract void putI1(long ptr, boolean value);

    public final void putI8(LLVMNativePointer addr, byte value) {
        putI8(addr.asNative(), value);
    }

    public abstract void putI8(long ptr, byte value);

    public final void putI16(LLVMNativePointer addr, short value) {
        putI16(addr.asNative(), value);
    }

    public abstract void putI16(long ptr, short value);

    public final void putI32(LLVMNativePointer addr, int value) {
        putI32(addr.asNative(), value);
    }

    public abstract void putI32(long ptr, int value);

    public final void putI64(LLVMNativePointer addr, long value) {
        putI64(addr.asNative(), value);
    }

    public abstract void putI64(long ptr, long value);

    public abstract void putIVarBit(LLVMNativePointer addr, LLVMIVarBit value);

    public final void putFloat(LLVMNativePointer addr, float value) {
        putFloat(addr.asNative(), value);
    }

    public abstract void putFloat(long ptr, float value);

    public final void putDouble(LLVMNativePointer addr, double value) {
        putDouble(addr.asNative(), value);
    }

    public abstract void putDouble(long ptr, double value);

    public final void put80BitFloat(LLVMNativePointer addr, LLVM80BitFloat value) {
        put80BitFloat(addr.asNative(), value);
    }

    public abstract void put80BitFloat(long ptr, LLVM80BitFloat value);

    public final void putPointer(LLVMNativePointer addr, LLVMNativePointer value) {
        putPointer(addr.asNative(), value.asNative());
    }

    public final void putPointer(LLVMNativePointer addr, long ptrValue) {
        putPointer(addr.asNative(), ptrValue);
    }

    public final void putPointer(long ptr, LLVMNativePointer value) {
        putPointer(ptr, value.asNative());
    }

    public abstract void putPointer(long ptr, long ptrValue);

    public final void putByteArray(LLVMNativePointer addr, byte[] bytes) {
        putByteArray(addr.asNative(), bytes);
    }

    public abstract void putByteArray(long ptr, byte[] bytes);

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

    @FunctionalInterface
    public interface ShortBinaryOperator {

        short apply(short a, short b);
    }

    public abstract short getAndOpI16(LLVMNativePointer address, short value, ShortBinaryOperator f);

    @FunctionalInterface
    public interface ByteBinaryOperator {

        byte apply(byte a, byte b);
    }

    public abstract byte getAndOpI8(LLVMNativePointer address, byte value, ByteBinaryOperator f);

    @FunctionalInterface
    public interface BooleanBinaryOperator {

        boolean apply(boolean a, boolean b);
    }

    public abstract boolean getAndOpI1(LLVMNativePointer address, boolean value, BooleanBinaryOperator f);

    public abstract void fullFence();

    public abstract long allocateHandle(boolean autoDeref);

    public abstract boolean isHandleMemory(long addr);

    public abstract boolean isDerefHandleMemory(long addr);

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
