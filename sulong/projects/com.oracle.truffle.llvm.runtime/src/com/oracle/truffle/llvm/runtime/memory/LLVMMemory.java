/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.config.LLVMCapability;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVMMemory implements LLVMCapability {

    /** Use {@link com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode} instead. */
    @Deprecated
    public abstract void memset(Node location, LLVMNativePointer address, long size, byte value);

    /** Use {@link com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode} instead. */
    @Deprecated
    public abstract void copyMemory(Node location, long sourceAddress, long targetAddress, long length);

    public final void free(Node location, LLVMNativePointer address) {
        free(location, address.asNative());
    }

    public abstract void free(Node location, long address);

    public abstract LLVMNativePointer allocateMemory(Node location, long size);

    /**
     * Use a realloc node instead.
     */
    @Deprecated
    public abstract LLVMNativePointer reallocateMemory(Node location, LLVMNativePointer addr, long size);

    public final boolean getI1(Node location, LLVMNativePointer addr) {
        return getI1(location, addr.asNative());
    }

    public abstract boolean getI1(Node location, long ptr);

    public final byte getI8(Node location, LLVMNativePointer addr) {
        return getI8(location, addr.asNative());
    }

    public abstract byte getI8(Node location, long ptr);

    public final short getI16(Node location, LLVMNativePointer addr) {
        return getI16(location, addr.asNative());
    }

    public abstract short getI16(Node location, long ptr);

    public final int getI32(Node location, LLVMNativePointer addr) {
        return getI32(location, addr.asNative());
    }

    public abstract int getI32(Node location, long ptr);

    public abstract LLVMIVarBit getIVarBit(Node location, LLVMNativePointer addr, int bitWidth);

    public final long getI64(Node location, LLVMNativePointer addr) {
        return getI64(location, addr.asNative());
    }

    public abstract long getI64(Node location, long ptr);

    public final float getFloat(Node location, LLVMNativePointer addr) {
        return getFloat(location, addr.asNative());
    }

    public abstract float getFloat(Node location, long ptr);

    public final double getDouble(Node location, LLVMNativePointer addr) {
        return getDouble(location, addr.asNative());
    }

    public abstract double getDouble(Node location, long ptr);

    public abstract LLVM80BitFloat get80BitFloat(Node location, LLVMNativePointer addr);

    public final LLVMNativePointer getPointer(Node location, LLVMNativePointer addr) {
        return getPointer(location, addr.asNative());
    }

    public abstract LLVMNativePointer getPointer(Node location, long ptr);

    public final void putI1(Node location, LLVMNativePointer addr, boolean value) {
        putI1(location, addr.asNative(), value);
    }

    public abstract void putI1(Node location, long ptr, boolean value);

    public final void putI8(Node location, LLVMNativePointer addr, byte value) {
        putI8(location, addr.asNative(), value);
    }

    public abstract void putI8(Node location, long ptr, byte value);

    public final void putI16(Node location, LLVMNativePointer addr, short value) {
        putI16(location, addr.asNative(), value);
    }

    public abstract void putI16(Node location, long ptr, short value);

    public final void putI32(Node location, LLVMNativePointer addr, int value) {
        putI32(location, addr.asNative(), value);
    }

    public abstract void putI32(Node location, long ptr, int value);

    public final void putI64(Node location, LLVMNativePointer addr, long value) {
        putI64(location, addr.asNative(), value);
    }

    public abstract void putI64(Node location, long ptr, long value);

    public abstract void putIVarBit(Node location, LLVMNativePointer addr, LLVMIVarBit value);

    public final void putFloat(Node location, LLVMNativePointer addr, float value) {
        putFloat(location, addr.asNative(), value);
    }

    public abstract void putFloat(Node location, long ptr, float value);

    public final void putDouble(Node location, LLVMNativePointer addr, double value) {
        putDouble(location, addr.asNative(), value);
    }

    public abstract void putDouble(Node location, long ptr, double value);

    public final void put80BitFloat(Node location, LLVMNativePointer addr, LLVM80BitFloat value) {
        put80BitFloat(location, addr.asNative(), value);
    }

    public abstract void put80BitFloat(Node location, long ptr, LLVM80BitFloat value);

    public final void putPointer(Node location, LLVMNativePointer addr, LLVMNativePointer value) {
        putPointer(location, addr.asNative(), value.asNative());
    }

    public final void putPointer(Node location, LLVMNativePointer addr, long ptrValue) {
        putPointer(location, addr.asNative(), ptrValue);
    }

    public final void putPointer(Node location, long ptr, LLVMNativePointer value) {
        putPointer(location, ptr, value.asNative());
    }

    public abstract void putPointer(Node location, long ptr, long ptrValue);

    public final void putByteArray(Node location, LLVMNativePointer addr, byte[] bytes) {
        putByteArray(location, addr.asNative(), bytes);
    }

    public abstract void putByteArray(Node location, long ptr, byte[] bytes);

    public abstract CMPXCHGI32 compareAndSwapI32(Node location, LLVMNativePointer p, int comparisonValue, int newValue);

    public abstract CMPXCHGI64 compareAndSwapI64(Node location, LLVMNativePointer p, long comparisonValue, long newValue);

    public abstract CMPXCHGI8 compareAndSwapI8(Node location, LLVMNativePointer p, byte comparisonValue, byte newValue);

    public abstract CMPXCHGI16 compareAndSwapI16(Node location, LLVMNativePointer p, short comparisonValue, short newValue);

    public abstract long getAndSetI64(Node location, LLVMNativePointer address, long value);

    public abstract long getAndAddI64(Node location, LLVMNativePointer address, long value);

    public abstract long getAndSubI64(Node location, LLVMNativePointer address, long value);

    public abstract long getAndOpI64(Node location, LLVMNativePointer address, long value, LongBinaryOperator f);

    public abstract int getAndSetI32(Node location, LLVMNativePointer address, int value);

    public abstract int getAndAddI32(Node location, LLVMNativePointer address, int value);

    public abstract int getAndSubI32(Node location, LLVMNativePointer address, int value);

    public abstract int getAndOpI32(Node location, LLVMNativePointer address, int value, IntBinaryOperator f);

    @FunctionalInterface
    public interface ShortBinaryOperator {

        short apply(short a, short b);
    }

    public abstract short getAndOpI16(Node location, LLVMNativePointer address, short value, ShortBinaryOperator f);

    @FunctionalInterface
    public interface ByteBinaryOperator {

        byte apply(byte a, byte b);
    }

    public abstract byte getAndOpI8(Node location, LLVMNativePointer address, byte value, ByteBinaryOperator f);

    @FunctionalInterface
    public interface BooleanBinaryOperator {

        boolean apply(boolean a, boolean b);
    }

    public abstract boolean getAndOpI1(Node location, LLVMNativePointer address, boolean value, BooleanBinaryOperator f);

    public abstract void fullFence();

    public abstract static class HandleContainer {

        public abstract LLVMNativePointer allocate(Node location, Object value);

        public abstract void free(Node location, long address);

        public abstract LLVMManagedPointer getValue(Node location, long address);

        public abstract boolean isHandle(long address);

    }

    public abstract HandleContainer createHandleContainer(boolean deref, Assumption noHandleAssumption);

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
