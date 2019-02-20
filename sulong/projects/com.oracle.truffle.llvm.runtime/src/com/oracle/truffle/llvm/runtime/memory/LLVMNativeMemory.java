/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

import java.lang.reflect.Field;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

import sun.misc.Unsafe;

public final class LLVMNativeMemory extends LLVMMemory {
    /* must be a power of 2 */
    private static final long DEREF_HANDLE_OBJECT_SIZE = 1L << 20;

    private static final long DEREF_HANDLE_SPACE_START = 0x8000000000000000L;
    public static final long DEREF_HANDLE_SPACE_END = 0xC000000000000000L;
    private static final long HANDLE_SPACE_START = DEREF_HANDLE_SPACE_END;
    private static final long HANDLE_SPACE_END = 0xD000000000000000L;

    private static final Unsafe unsafe = getUnsafe();

    private final HandleContainer derefHandleContainer = new DerefHandleContainer(DEREF_HANDLE_SPACE_START, DEREF_HANDLE_SPACE_END, DEREF_HANDLE_OBJECT_SIZE);
    private final HandleContainer handleContainer = new CommonHandleContainer(HANDLE_SPACE_START, HANDLE_SPACE_END, Long.BYTES);

    private final Assumption noDerefHandleAssumption = Truffle.getRuntime().createAssumption("no deref handle assumption");

    private static Unsafe getUnsafe() {
        CompilerAsserts.neverPartOfCompilation();
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        } catch (Exception e) {
            throw new AssertionError();
        }
    }

    private static final LLVMNativeMemory INSTANCE = new LLVMNativeMemory();

    /**
     * @deprecated "This method should not be called directly. Use
     *             {@link LLVMLanguage#getCapability(Class)} instead."
     */
    @Deprecated
    public static LLVMNativeMemory getInstance() {
        return INSTANCE;
    }

    private LLVMNativeMemory() {
    }

    /**
     * Checks for pointers that are in the negative range or below 1mb, to detect common invalid
     * addresses before they cause a segmentation fault.
     */
    private static boolean checkPointer(long ptr) {
        assert ptr > 0x100000 : "trying to access invalid address: " + ptr + " 0x" + Long.toHexString(ptr);
        return true;
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public void memset(LLVMNativePointer address, long size, byte value) {
        assert size == 0 || checkPointer(address.asNative());
        try {
            unsafe.setMemory(address.asNative(), size, value);
        } catch (Throwable e) {
            // this avoids unnecessary exception edges in the compiled code
            CompilerDirectives.transferToInterpreter();
            throw e;
        }
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public void copyMemory(long sourceAddress, long targetAddress, long length) {
        assert length == 0 || checkPointer(sourceAddress) && checkPointer(targetAddress);
        unsafe.copyMemory(sourceAddress, targetAddress, length);
    }

    @Override
    public void free(long address) {
        if (!noDerefHandleAssumption.isValid() && derefHandleContainer.accept(address)) {
            derefHandleContainer.free(address);
        } else if (handleContainer.accept(address)) {
            handleContainer.free(address);
        } else {
            try {
                unsafe.freeMemory(address);
            } catch (Throwable e) {
                // this avoids unnecessary exception edges in the compiled code
                CompilerDirectives.transferToInterpreter();
                throw e;
            }
        }

    }

    @Override
    public LLVMNativePointer allocateMemory(long size) {
        try {
            return LLVMNativePointer.create(unsafe.allocateMemory(size));
        } catch (Throwable e) {
            // this avoids unnecessary exception edges in the compiled code
            CompilerDirectives.transferToInterpreter();
            throw e;
        }
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public LLVMNativePointer reallocateMemory(LLVMNativePointer addr, long size) {
        // a null pointer is a valid argument
        try {
            return LLVMNativePointer.create(unsafe.reallocateMemory(addr.asNative(), size));
        } catch (Throwable e) {
            // this avoids unnecessary exception edges in the compiled code
            CompilerDirectives.transferToInterpreter();
            throw e;
        }
    }

    @Override
    public long allocateHandle(boolean autoDeref) {
        if (autoDeref) {
            noDerefHandleAssumption.invalidate();
            return derefHandleContainer.allocate();
        }
        return handleContainer.allocate();
    }

    @Override
    public boolean getI1(long ptr) {
        assert checkPointer(ptr);
        return unsafe.getByte(ptr) != 0;
    }

    @Override
    public byte getI8(long ptr) {
        assert checkPointer(ptr);
        return unsafe.getByte(ptr);
    }

    @Override
    public short getI16(long ptr) {
        assert checkPointer(ptr);
        return unsafe.getShort(ptr);
    }

    @Override
    public int getI32(long ptr) {
        assert checkPointer(ptr);
        return unsafe.getInt(ptr);
    }

    @Override
    public LLVMIVarBit getIVarBit(LLVMNativePointer addr, int bitWidth) {
        if (bitWidth % Byte.SIZE != 0) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError();
        }
        int bytes = bitWidth / Byte.SIZE;
        byte[] loadedBytes = new byte[bytes];
        long currentAddressPtr = addr.asNative();
        for (int i = loadedBytes.length - 1; i >= 0; i--) {
            loadedBytes[i] = getI8(currentAddressPtr);
            currentAddressPtr += Byte.BYTES;
        }
        return LLVMIVarBit.create(bitWidth, loadedBytes, bitWidth, false);
    }

    @Override
    public long getI64(long ptr) {
        assert checkPointer(ptr);
        return unsafe.getLong(ptr);
    }

    @Override
    public float getFloat(long ptr) {
        assert checkPointer(ptr);
        return unsafe.getFloat(ptr);
    }

    @Override
    public double getDouble(long ptr) {
        assert checkPointer(ptr);
        return unsafe.getDouble(ptr);
    }

    @Override
    public LLVM80BitFloat get80BitFloat(LLVMNativePointer addr) {
        byte[] bytes = new byte[LLVM80BitFloat.BYTE_WIDTH];
        long currentPtr = addr.asNative();
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = getI8(currentPtr);
            currentPtr += Byte.BYTES;
        }
        return LLVM80BitFloat.fromBytes(bytes);
    }

    @Override
    public LLVMNativePointer getPointer(long ptr) {
        assert checkPointer(ptr);
        return LLVMNativePointer.create(unsafe.getAddress(ptr));
    }

    @Override
    public void putI1(long ptr, boolean value) {
        assert checkPointer(ptr);
        unsafe.putByte(ptr, (byte) (value ? 1 : 0));
    }

    @Override
    public void putI8(long ptr, byte value) {
        assert checkPointer(ptr);
        unsafe.putByte(ptr, value);
    }

    @Override
    public void putI16(long ptr, short value) {
        assert checkPointer(ptr);
        unsafe.putShort(ptr, value);
    }

    @Override
    public void putI32(long ptr, int value) {
        assert checkPointer(ptr);
        unsafe.putInt(ptr, value);
    }

    @Override
    public void putI64(long ptr, long value) {
        assert checkPointer(ptr);
        unsafe.putLong(ptr, value);
    }

    @Override
    public void putIVarBit(LLVMNativePointer addr, LLVMIVarBit value) {
        byte[] bytes = value.getBytes();
        long currentptr = addr.asNative();
        for (int i = bytes.length - 1; i >= 0; i--) {
            putI8(currentptr, bytes[i]);
            currentptr += Byte.BYTES;
        }
    }

    @Override
    public void putByteArray(long ptr, byte[] bytes) {
        long currentptr = ptr;
        for (int i = 0; i < bytes.length; i++) {
            putI8(currentptr, bytes[i]);
            currentptr += Byte.BYTES;
        }
    }

    @Override
    public void putFloat(long ptr, float value) {
        assert checkPointer(ptr);
        unsafe.putFloat(ptr, value);
    }

    @Override
    public void putDouble(long ptr, double value) {
        assert checkPointer(ptr);
        unsafe.putDouble(ptr, value);
    }

    @Override
    public void put80BitFloat(long ptr, LLVM80BitFloat value) {
        putByteArray(ptr, value.getBytes());
    }

    @Override
    public void putPointer(long ptr, long ptrValue) {
        assert ptr != 0;
        unsafe.putAddress(ptr, ptrValue);
    }

    @Override
    public CMPXCHGI32 compareAndSwapI32(LLVMNativePointer p, int comparisonValue, int newValue) {
        assert checkPointer(p.asNative());
        while (true) {
            boolean b = unsafe.compareAndSwapInt(null, p.asNative(), comparisonValue, newValue);
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, b)) {
                return new CMPXCHGI32(comparisonValue, b);
            } else {
                int t = unsafe.getIntVolatile(null, p.asNative());
                if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, t == comparisonValue)) {
                    continue;
                } else {
                    return new CMPXCHGI32(t, b);
                }
            }
        }
    }

    @Override
    public CMPXCHGI64 compareAndSwapI64(LLVMNativePointer p, long comparisonValue, long newValue) {
        assert checkPointer(p.asNative());
        while (true) {
            boolean b = unsafe.compareAndSwapLong(null, p.asNative(), comparisonValue, newValue);
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, b)) {
                return new CMPXCHGI64(comparisonValue, b);
            } else {
                long t = unsafe.getLongVolatile(null, p.asNative());
                if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, t == comparisonValue)) {
                    continue;
                } else {
                    return new CMPXCHGI64(t, b);
                }
            }
        }
    }

    private static long alignToI32(long address) {
        long mask = 3;
        return (address & ~mask);
    }

    private static int getI8Index(long address) {
        long mask = 3;
        return (int) (address & mask);
    }

    private static byte getI8At(int value, int index) {
        return (byte) ((value >> (8 * index)) & 0xff);
    }

    private static int replaceI8(int index, int value, byte replaceByte) {
        return (value & ~(0xFF << (index * 8))) | ((replaceByte & 0xFF) << (index * 8));
    }

    @Override
    public CMPXCHGI8 compareAndSwapI8(LLVMNativePointer p, byte comparisonValue, byte newValue) {
        assert checkPointer(p.asNative());
        int byteIndex = getI8Index(p.asNative());
        long address = alignToI32(p.asNative());
        while (true) {
            int t = unsafe.getIntVolatile(null, address);
            byte b = getI8At(t, byteIndex);
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, b != comparisonValue)) {
                return new CMPXCHGI8(b, false);
            } else {
                int newVal = replaceI8(byteIndex, t, newValue);
                boolean c = unsafe.compareAndSwapInt(null, address, t, newVal);
                if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, c)) {
                    return new CMPXCHGI8(comparisonValue, true);
                } else {
                    continue;
                }
            }
        }
    }

    private static int getI16Index(long address) {
        long mask = 3;
        return (int) (address & mask) >> 1;
    }

    private static short getI16At(int value, int index) {
        return (short) ((value >> (16 * index)) & 0xFFFF);
    }

    private static int replaceI16(int index, int value, short replace) {
        return (value & ~(0xFFFF << (index * 16))) | ((replace & 0xFFFF) << (index * 16));
    }

    @Override
    public CMPXCHGI16 compareAndSwapI16(LLVMNativePointer p, short comparisonValue, short newValue) {
        assert checkPointer(p.asNative());
        int idx = getI16Index(p.asNative());
        long address = alignToI32(p.asNative());
        while (true) {
            int t = unsafe.getIntVolatile(null, address);
            short b = getI16At(t, idx);
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, b != comparisonValue)) {
                return new CMPXCHGI16(b, false);
            } else {
                int newVal = replaceI16(idx, t, newValue);
                boolean c = unsafe.compareAndSwapInt(null, address, t, newVal);
                if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, c)) {
                    return new CMPXCHGI16(comparisonValue, true);
                } else {
                    continue;
                }
            }
        }
    }

    @Override
    public long getAndSetI64(LLVMNativePointer address, long value) {
        assert checkPointer(address.asNative());
        return unsafe.getAndSetLong(null, address.asNative(), value);
    }

    @Override
    public long getAndAddI64(LLVMNativePointer address, long value) {
        assert checkPointer(address.asNative());
        return unsafe.getAndAddLong(null, address.asNative(), value);
    }

    @Override
    public long getAndSubI64(LLVMNativePointer address, long value) {
        assert checkPointer(address.asNative());
        return unsafe.getAndAddLong(null, address.asNative(), -value);
    }

    @Override
    public long getAndOpI64(LLVMNativePointer address, long value, LongBinaryOperator f) {
        assert checkPointer(address.asNative());
        long addr = address.asNative();
        long old;
        long nevv;
        do {
            old = getI64(address);
            nevv = f.applyAsLong(old, value);
        } while (!unsafe.compareAndSwapLong(null, addr, old, nevv));
        return old;
    }

    @Override
    public int getAndSetI32(LLVMNativePointer address, int value) {
        assert checkPointer(address.asNative());
        return unsafe.getAndSetInt(null, address.asNative(), value);
    }

    @Override
    public int getAndAddI32(LLVMNativePointer address, int value) {
        assert checkPointer(address.asNative());
        return unsafe.getAndAddInt(null, address.asNative(), value);
    }

    @Override
    public int getAndSubI32(LLVMNativePointer address, int value) {
        assert checkPointer(address.asNative());
        return unsafe.getAndAddInt(null, address.asNative(), -value);
    }

    @Override
    public int getAndOpI32(LLVMNativePointer address, int value, IntBinaryOperator f) {
        assert checkPointer(address.asNative());
        long addr = address.asNative();
        int old;
        int nevv;
        do {
            old = getI32(address);
            nevv = f.applyAsInt(old, value);
        } while (!unsafe.compareAndSwapInt(null, addr, old, nevv));
        return old;
    }

    @Override
    public short getAndOpI16(LLVMNativePointer address, short value, ShortBinaryOperator f) {
        short old;
        short nevv;
        do {
            old = getI16(address);
            nevv = f.apply(old, value);
        } while (!compareAndSwapI16(address, old, nevv).isSwap());
        return old;
    }

    @Override
    public byte getAndOpI8(LLVMNativePointer address, byte value, ByteBinaryOperator f) {
        byte old;
        byte nevv;
        do {
            old = getI8(address);
            nevv = f.apply(old, value);
        } while (!compareAndSwapI8(address, old, nevv).isSwap());
        return old;
    }

    @Override
    public boolean getAndOpI1(LLVMNativePointer address, boolean value, BooleanBinaryOperator f) {
        byte old;
        boolean nevv;
        do {
            old = getI8(address);
            nevv = f.apply(old != 0, value);
        } while (!compareAndSwapI8(address, old, (byte) (nevv ? 1 : 0)).isSwap());
        return old != 0;
    }

    @Override
    public void fullFence() {
        unsafe.fullFence();
    }

    /**
     * A fast check if the provided address is within the handle space.
     */
    @Override
    public boolean isHandleMemory(long addr) {
        return addr < HANDLE_SPACE_END;
    }

    /**
     * A fast check if the provided address is within the auto-deref handle space.
     */
    @Override
    public boolean isDerefHandleMemory(long addr) {
        return !noDerefHandleAssumption.isValid() && derefHandleContainer.accept(addr);
    }

    public static long getDerefHandleObjectMask() {
        return DEREF_HANDLE_OBJECT_SIZE - 1;
    }

    private abstract static class HandleContainer {
        protected final long rangeStart;
        protected final long rangeEnd;
        protected final long objectSize;

        private long top;
        private FreeListNode freeList;

        private final Object freeListLock = new Object();
        private final Object topLock = new Object();

        HandleContainer(long startAddr, long endAddr, long objectSize) {
            this.rangeStart = startAddr;
            this.rangeEnd = endAddr;
            this.top = startAddr;

            assert isPowerOfTwo(objectSize);
            this.objectSize = objectSize;
        }

        protected static final class FreeListNode {
            protected FreeListNode(long address, FreeListNode next) {
                this.address = address;
                this.next = next;
            }

            private final long address;
            private final FreeListNode next;
        }

        abstract boolean accept(long address);

        long allocate() {

            // preferably consume from free list
            synchronized (freeListLock) {
                if (freeList != null) {
                    FreeListNode n = freeList;
                    freeList = n.next;
                    return n.address;
                }
            }

            synchronized (topLock) {
                long addr = top;
                assert top >= rangeStart;
                top += objectSize;
                if (!accept(top)) {
                    CompilerDirectives.transferToInterpreter();
                    throw new OutOfMemoryError();
                }
                return addr;
            }
        }

        boolean isAllocated(long address) {
            synchronized (topLock) {
                if (!(address >= rangeStart && address < top)) {
                    return false;
                }
            }

            synchronized (freeListLock) {
                for (FreeListNode cur = freeList; cur != null; cur = cur.next) {
                    if (cur.address == address) {
                        return false;
                    }
                }
            }
            return true;
        }

        @TruffleBoundary
        void free(long address) {
            if (!isAllocated(address)) {
                throw new IllegalStateException("double-free of " + Long.toHexString(address));
            }
            synchronized (freeListLock) {
                // We need to mask because we allow creating handles with an offset.
                freeList = new FreeListNode(address & ~getObjectMask(), freeList);
            }
        }

        private long getObjectMask() {
            return objectSize - 1;
        }

        static boolean isPowerOfTwo(long x) {
            return x > 0 && (x & (x - 1)) == 0;
        }
    }

    private static final class DerefHandleContainer extends HandleContainer {

        DerefHandleContainer(long startAddr, long endAddr, long objectSize) {
            super(startAddr, endAddr, objectSize);
        }

        @Override
        boolean accept(long address) {
            return address < rangeEnd;
        }

    }

    private static final class CommonHandleContainer extends HandleContainer {

        CommonHandleContainer(long startAddr, long endAddr, long objectSize) {
            super(startAddr, endAddr, objectSize);
        }

        @Override
        boolean accept(long address) {
            return rangeStart <= address && address < rangeEnd;
        }

    }

}
