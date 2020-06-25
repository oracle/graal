/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMMemoryException;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

import sun.misc.Unsafe;

public final class LLVMNativeMemory extends LLVMMemory {
    private static final int HANDLE_OBJECT_SIZE_BITS = 30;
    private static final long HANDLE_OBJECT_SIZE = 1L << HANDLE_OBJECT_SIZE_BITS; // 0.5 GB
    private static final int HANDLE_OBJECT_ADDRESS_BITS = Integer.SIZE; // use int cast as mask
    private static final long HANDLE_HEADER_MASK = -1L << (HANDLE_OBJECT_SIZE_BITS + HANDLE_OBJECT_ADDRESS_BITS);
    private static final long HANDLE_OFFSET_MASK = HANDLE_OBJECT_SIZE - 1;

    private static final long HANDLE_SPACE_START = 0x8000000000000000L;
    private static final long HANDLE_SPACE_END = 0xC000000000000000L;
    private static final long DEREF_HANDLE_SPACE_START = HANDLE_SPACE_END;
    private static final long DEREF_HANDLE_SPACE_END = 0x0000000000000000L;

    static {
        assert (DEREF_HANDLE_SPACE_START & HANDLE_HEADER_MASK) != (DEREF_HANDLE_SPACE_END & HANDLE_HEADER_MASK);
        assert (HANDLE_SPACE_START & HANDLE_HEADER_MASK) != (HANDLE_SPACE_END & HANDLE_HEADER_MASK);
        assert (DEREF_HANDLE_SPACE_START & HANDLE_HEADER_MASK) != 0;
        assert (HANDLE_SPACE_START & HANDLE_HEADER_MASK) != 0;

        // (using temporary variable to avoid warnings)
        long tmp = HANDLE_HEADER_MASK;
        // for efficient checks for deref handles in compiled code
        assert DEREF_HANDLE_SPACE_START == tmp;
        tmp = HANDLE_SPACE_START;
        // for efficient checks for any handle in compiled code
        assert (DEREF_HANDLE_SPACE_START & tmp) == HANDLE_SPACE_START;
    }

    private static final Unsafe unsafe = getUnsafe();

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
     *             {@link LLVMLanguage#getLLVMMemory() } instead."
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

    @TruffleBoundary
    private static void memsetBoundary(long address, long size, byte value) {
        unsafe.setMemory(address, size, value);
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public void memset(Node location, LLVMNativePointer address, long size, byte value) {
        assert size == 0 || checkPointer(address.asNative());
        try {
            memsetBoundary(address.asNative(), size, value);
        } catch (Throwable e) {
            // this avoids unnecessary exception edges in the compiled code
            CompilerDirectives.transferToInterpreter();
            throw e;
        }
    }

    @TruffleBoundary
    private static void copyMemoryBoundary(long sourceAddress, long targetAddress, long length) {
        unsafe.copyMemory(sourceAddress, targetAddress, length);
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public void copyMemory(Node location, long sourceAddress, long targetAddress, long length) {
        assert length == 0 || checkPointer(sourceAddress) && checkPointer(targetAddress);
        copyMemoryBoundary(sourceAddress, targetAddress, length);
    }

    @TruffleBoundary
    private static void freeBoundary(long address) {
        unsafe.freeMemory(address);
    }

    @Override
    public void free(Node location, long address) {
        try {
            freeBoundary(address);
        } catch (Throwable e) {
            // this avoids unnecessary exception edges in the compiled code
            CompilerDirectives.transferToInterpreter();
            throw e;
        }
    }

    @TruffleBoundary
    private static long allocateMemoryBoundary(long size) {
        return unsafe.allocateMemory(size);
    }

    @Override
    public LLVMNativePointer allocateMemory(Node location, long size) {
        try {
            return LLVMNativePointer.create(allocateMemoryBoundary(size));
        } catch (Throwable e) {
            // this avoids unnecessary exception edges in the compiled code
            CompilerDirectives.transferToInterpreter();
            throw e;
        }
    }

    @TruffleBoundary
    private static long reallocateMemoryBoundary(long addr, long size) {
        return unsafe.reallocateMemory(addr, size);
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public LLVMNativePointer reallocateMemory(Node location, LLVMNativePointer addr, long size) {
        // a null pointer is a valid argument
        try {
            return LLVMNativePointer.create(reallocateMemoryBoundary(addr.asNative(), size));
        } catch (Throwable e) {
            // this avoids unnecessary exception edges in the compiled code
            CompilerDirectives.transferToInterpreter();
            throw e;
        }
    }

    @Override
    public boolean getI1(Node location, long ptr) {
        assert checkPointer(ptr);
        return unsafe.getByte(ptr) != 0;
    }

    @Override
    public byte getI8(Node location, long ptr) {
        assert checkPointer(ptr);
        return unsafe.getByte(ptr);
    }

    @Override
    public short getI16(Node location, long ptr) {
        assert checkPointer(ptr);
        return unsafe.getShort(ptr);
    }

    @Override
    public int getI32(Node location, long ptr) {
        assert checkPointer(ptr);
        return unsafe.getInt(ptr);
    }

    @Override
    public LLVMIVarBit getIVarBit(Node location, LLVMNativePointer addr, int bitWidth) {
        if (bitWidth % Byte.SIZE != 0) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError();
        }
        int bytes = bitWidth / Byte.SIZE;
        byte[] loadedBytes = new byte[bytes];
        long currentAddressPtr = addr.asNative();
        for (int i = loadedBytes.length - 1; i >= 0; i--) {
            loadedBytes[i] = getI8(location, currentAddressPtr);
            currentAddressPtr += Byte.BYTES;
        }
        return LLVMIVarBit.create(bitWidth, loadedBytes, bitWidth, false);
    }

    @Override
    public long getI64(Node location, long ptr) {
        assert checkPointer(ptr);
        return unsafe.getLong(ptr);
    }

    @Override
    public float getFloat(Node location, long ptr) {
        assert checkPointer(ptr);
        return unsafe.getFloat(ptr);
    }

    @Override
    public double getDouble(Node location, long ptr) {
        assert checkPointer(ptr);
        return unsafe.getDouble(ptr);
    }

    @Override
    public LLVM80BitFloat get80BitFloat(Node location, LLVMNativePointer addr) {
        byte[] bytes = new byte[LLVM80BitFloat.BYTE_WIDTH];
        long currentPtr = addr.asNative();
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = getI8(location, currentPtr);
            currentPtr += Byte.BYTES;
        }
        return LLVM80BitFloat.fromBytes(bytes);
    }

    @Override
    public LLVMNativePointer getPointer(Node location, long ptr) {
        assert checkPointer(ptr);
        return LLVMNativePointer.create(unsafe.getAddress(ptr));
    }

    @Override
    public void putI1(Node location, long ptr, boolean value) {
        assert checkPointer(ptr);
        unsafe.putByte(ptr, (byte) (value ? 1 : 0));
    }

    @Override
    public void putI8(Node location, long ptr, byte value) {
        assert checkPointer(ptr);
        unsafe.putByte(ptr, value);
    }

    @Override
    public void putI16(Node location, long ptr, short value) {
        assert checkPointer(ptr);
        unsafe.putShort(ptr, value);
    }

    @Override
    public void putI32(Node location, long ptr, int value) {
        assert checkPointer(ptr);
        unsafe.putInt(ptr, value);
    }

    @Override
    public void putI64(Node location, long ptr, long value) {
        assert checkPointer(ptr);
        unsafe.putLong(ptr, value);
    }

    @Override
    public void putIVarBit(Node location, LLVMNativePointer addr, LLVMIVarBit value) {
        byte[] bytes = value.getBytes();
        long currentptr = addr.asNative();
        for (int i = bytes.length - 1; i >= 0; i--) {
            putI8(location, currentptr, bytes[i]);
            currentptr += Byte.BYTES;
        }
    }

    @Override
    public void putByteArray(Node location, long ptr, byte[] bytes) {
        long currentptr = ptr;
        for (int i = 0; i < bytes.length; i++) {
            putI8(location, currentptr, bytes[i]);
            currentptr += Byte.BYTES;
        }
    }

    @Override
    public void putFloat(Node location, long ptr, float value) {
        assert checkPointer(ptr);
        unsafe.putFloat(ptr, value);
    }

    @Override
    public void putDouble(Node location, long ptr, double value) {
        assert checkPointer(ptr);
        unsafe.putDouble(ptr, value);
    }

    @Override
    public void put80BitFloat(Node location, long ptr, LLVM80BitFloat value) {
        putByteArray(location, ptr, value.getBytes());
    }

    @Override
    public void putPointer(Node location, long ptr, long ptrValue) {
        assert ptr != 0;
        unsafe.putAddress(ptr, ptrValue);
    }

    @Override
    public CMPXCHGI32 compareAndSwapI32(Node location, LLVMNativePointer p, int comparisonValue, int newValue) {
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
    public CMPXCHGI64 compareAndSwapI64(Node location, LLVMNativePointer p, long comparisonValue, long newValue) {
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
    public CMPXCHGI8 compareAndSwapI8(Node location, LLVMNativePointer p, byte comparisonValue, byte newValue) {
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
    public CMPXCHGI16 compareAndSwapI16(Node location, LLVMNativePointer p, short comparisonValue, short newValue) {
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
    public long getAndSetI64(Node location, LLVMNativePointer address, long value) {
        assert checkPointer(address.asNative());
        return unsafe.getAndSetLong(null, address.asNative(), value);
    }

    @Override
    public long getAndAddI64(Node location, LLVMNativePointer address, long value) {
        assert checkPointer(address.asNative());
        return unsafe.getAndAddLong(null, address.asNative(), value);
    }

    @Override
    public long getAndSubI64(Node location, LLVMNativePointer address, long value) {
        assert checkPointer(address.asNative());
        return unsafe.getAndAddLong(null, address.asNative(), -value);
    }

    @Override
    public long getAndOpI64(Node location, LLVMNativePointer address, long value, LongBinaryOperator f) {
        assert checkPointer(address.asNative());
        long addr = address.asNative();
        long old;
        long nevv;
        do {
            old = getI64(location, address);
            nevv = f.applyAsLong(old, value);
        } while (!unsafe.compareAndSwapLong(null, addr, old, nevv));
        return old;
    }

    @Override
    public int getAndSetI32(Node location, LLVMNativePointer address, int value) {
        assert checkPointer(address.asNative());
        return unsafe.getAndSetInt(null, address.asNative(), value);
    }

    @Override
    public int getAndAddI32(Node location, LLVMNativePointer address, int value) {
        assert checkPointer(address.asNative());
        return unsafe.getAndAddInt(null, address.asNative(), value);
    }

    @Override
    public int getAndSubI32(Node location, LLVMNativePointer address, int value) {
        assert checkPointer(address.asNative());
        return unsafe.getAndAddInt(null, address.asNative(), -value);
    }

    @Override
    public int getAndOpI32(Node location, LLVMNativePointer address, int value, IntBinaryOperator f) {
        assert checkPointer(address.asNative());
        long addr = address.asNative();
        int old;
        int nevv;
        do {
            old = getI32(location, address);
            nevv = f.applyAsInt(old, value);
        } while (!unsafe.compareAndSwapInt(null, addr, old, nevv));
        return old;
    }

    @Override
    public short getAndOpI16(Node location, LLVMNativePointer address, short value, ShortBinaryOperator f) {
        short old;
        short nevv;
        do {
            old = getI16(location, address);
            nevv = f.apply(old, value);
        } while (!compareAndSwapI16(location, address, old, nevv).isSwap());
        return old;
    }

    @Override
    public byte getAndOpI8(Node location, LLVMNativePointer address, byte value, ByteBinaryOperator f) {
        byte old;
        byte nevv;
        do {
            old = getI8(location, address);
            nevv = f.apply(old, value);
        } while (!compareAndSwapI8(location, address, old, nevv).isSwap());
        return old;
    }

    @Override
    public boolean getAndOpI1(Node location, LLVMNativePointer address, boolean value, BooleanBinaryOperator f) {
        byte old;
        boolean nevv;
        do {
            old = getI8(location, address);
            nevv = f.apply(old != 0, value);
        } while (!compareAndSwapI8(location, address, old, (byte) (nevv ? 1 : 0)).isSwap());
        return old != 0;
    }

    @Override
    public void fullFence() {
        unsafe.fullFence();
    }

    /**
     * A fast bit-check if the provided address is within the handle space.
     */
    public static boolean isHandleMemory(long address) {
        return (address & HANDLE_SPACE_START) != 0;
    }

    /**
     * A fast bit-check if the provided address is within the normal handle space.
     */
    public static boolean isCommonHandleMemory(long address) {
        return ((address & HANDLE_HEADER_MASK) == HANDLE_SPACE_START);
    }

    /**
     * A fast bit-check if the provided address is within the auto-deref handle space.
     */
    public static boolean isDerefHandleMemory(long address) {
        return ((address & HANDLE_HEADER_MASK) == DEREF_HANDLE_SPACE_START);
    }

    @Override
    public HandleContainer createHandleContainer(boolean deref, Assumption noHandleAssumption) {
        return deref ? new DerefHandleContainer(noHandleAssumption) : new CommonHandleContainer(noHandleAssumption);
    }

    private abstract static class AbstractHandleContainer extends HandleContainer {

        private final Assumption noHandleAssumption;
        private final ArrayDeque<Long> freeList = new ArrayDeque<>();
        private final EconomicMap<Object, Handle> handleFromManaged = EconomicMap.create();
        private Handle[] handleFromPointer = new Handle[1024];
        private long top = getStart(); // address of the next handle

        AbstractHandleContainer(Assumption noHandleAssumption) {
            this.noHandleAssumption = noHandleAssumption;
        }

        protected abstract long getStart();

        protected abstract long getEnd();

        private int indexFromPointer(long address) {
            return (int) (((address - getStart()) >> HANDLE_OBJECT_SIZE_BITS));
        }

        @Override
        @TruffleBoundary
        public synchronized LLVMNativePointer allocate(Node location, Object value) {
            Handle handle = handleFromManaged.get(value);
            if (handle == null) {
                Long free = freeList.pollFirst();
                long address;
                if (free != null) {
                    address = free;
                } else {
                    noHandleAssumption.invalidate();
                    if (top >= getEnd()) {
                        throw new LLVMMemoryException(location, new OutOfMemoryError("handle space exhausted"));
                    }
                    address = top;
                    top += HANDLE_OBJECT_SIZE;
                }
                handle = new Handle(LLVMNativePointer.create(address), value);
                int index = indexFromPointer(address);
                if (handleFromPointer.length <= index) {
                    handleFromPointer = Arrays.copyOf(handleFromPointer, handleFromPointer.length * 2);
                }
                handleFromPointer[index] = handle;
                handleFromManaged.put(value, handle);
            }
            handle.refcnt++;
            return handle.pointer;
        }

        @Override
        @TruffleBoundary
        public synchronized void free(Node location, long address) {
            if ((address & HANDLE_OFFSET_MASK) != 0) {
                throw new LLVMMemoryException(location, new UnsupportedOperationException("Cannot resolve invalid native handle: " + address));
            }
            if ((address & HANDLE_HEADER_MASK) != getStart()) {
                throw new LLVMMemoryException(location, new UnsupportedOperationException("Cannot resolve invalid native handle: " + address));
            }
            int index = indexFromPointer(address);
            if (index < 0 || index >= handleFromPointer.length) {
                throw new LLVMMemoryException(location, new UnsupportedOperationException("Cannot resolve native handle: " + address));
            }
            Handle handle = handleFromPointer[index];
            if (handle == null) {
                throw new LLVMMemoryException(location, new UnsupportedOperationException("Cannot resolve native handle (double-free?): " + address));
            }
            if (--handle.refcnt == 0) {
                handleFromPointer[index] = null;
                handleFromManaged.removeKey(handle.managed);
                freeList.addLast(address);
            }
        }

        @Override
        public boolean isHandle(long address) {
            if ((address & HANDLE_HEADER_MASK) != getStart()) {
                return false;
            }
            int index = indexFromPointer(address);
            Handle[] array = handleFromPointer;
            return index >= 0 && index < array.length && array[index] != null;
        }

        @Override
        public LLVMManagedPointer getValue(Node location, long address) {
            return LLVMManagedPointer.create(handleFromPointer[indexFromPointer(address)].managed, address & HANDLE_OFFSET_MASK);
        }
    }

    private static final class Handle {

        private int refcnt;
        private final LLVMNativePointer pointer;
        private final Object managed;

        private Handle(LLVMNativePointer pointer, Object managed) {
            this.refcnt = 0;
            this.pointer = pointer;
            this.managed = managed;
        }
    }

    private static final class CommonHandleContainer extends AbstractHandleContainer {

        CommonHandleContainer(Assumption noHandleAssumption) {
            super(noHandleAssumption);
        }

        @Override
        protected long getStart() {
            return HANDLE_SPACE_START;
        }

        @Override
        protected long getEnd() {
            return HANDLE_SPACE_END;
        }
    }

    private static final class DerefHandleContainer extends AbstractHandleContainer {

        DerefHandleContainer(Assumption noHandleAssumption) {
            super(noHandleAssumption);
        }

        @Override
        protected long getStart() {
            return DEREF_HANDLE_SPACE_START;
        }

        @Override
        protected long getEnd() {
            return DEREF_HANDLE_SPACE_END;
        }
    }
}
