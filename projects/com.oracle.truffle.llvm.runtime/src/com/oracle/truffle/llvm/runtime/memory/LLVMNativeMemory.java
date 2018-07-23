/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

import static com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode.ADDRESS_SIZE_IN_BYTES;
import static com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode.DOUBLE_SIZE_IN_BYTES;
import static com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode.FLOAT_SIZE_IN_BYTES;
import static com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode.I16_SIZE_IN_BYTES;
import static com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode.I1_SIZE_IN_BYTES;
import static com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode.I32_SIZE_IN_BYTES;
import static com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode.I64_SIZE_IN_BYTES;
import static com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode.I8_SIZE_IN_BYTES;

import java.lang.reflect.Field;
import java.util.function.BinaryOperator;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
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

import sun.misc.Unsafe;

public final class LLVMNativeMemory extends LLVMMemory {
    /* must be a power of 2 */
    private static final long DEREF_HANDLE_OBJECT_SIZE = 1L << 20;
    private static final long DEREF_HANDLE_OBJECT_MASK = (1L << 20) - 1L;

    private static final long DEREF_HANDLE_SPACE_START = 0x0FFFFFFFFFFFFFFFL & ~DEREF_HANDLE_OBJECT_MASK;
    private static final long DEREF_HANDLE_SPACE_END = 0x0FFF800000000000L & ~DEREF_HANDLE_OBJECT_MASK;

    private static final Unsafe unsafe = getUnsafe();

    private final Object freeListLock = new Object();
    private FreeListNode freeList;

    private final Object derefSpaceTopLock = new Object();
    private long derefSpaceTop = DEREF_HANDLE_SPACE_START;

    private final Assumption noDerefHandleAssumption = Truffle.getRuntime().createAssumption("no deref handle assumption");

    private static final class FreeListNode {
        protected FreeListNode(long address, FreeListNode next) {
            this.address = address;
            this.next = next;
        }

        private final long address;
        private final FreeListNode next;
    }

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

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public void memset(LLVMNativePointer address, long size, byte value) {
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
        unsafe.copyMemory(sourceAddress, targetAddress, length);
    }

    @Override
    public void free(LLVMNativePointer address) {
        free(address.asNative());
    }

    @Override
    public void free(long address) {
        if (address <= DEREF_HANDLE_SPACE_START && address > DEREF_HANDLE_SPACE_END) {
            assert isAllocated(address) : "double-free of " + Long.toHexString(address);
            synchronized (freeListLock) {
                // We need to mask because we allow creating handles with an offset.
                freeList = new FreeListNode(address & ~DEREF_HANDLE_OBJECT_MASK, freeList);
            }
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

    /**
     * Allocates {@code #OBJECT_SIZE} bytes in the Kernel space.
     */
    @Override
    public LLVMNativePointer allocateDerefMemory() {
        noDerefHandleAssumption.invalidate();

        // preferably consume from free list
        synchronized (freeListLock) {
            if (freeList != null) {
                FreeListNode n = freeList;
                freeList = n.next;
                return LLVMNativePointer.create(n.address);
            }
        }

        synchronized (derefSpaceTopLock) {
            LLVMNativePointer addr = LLVMNativePointer.create(derefSpaceTop);
            assert derefSpaceTop > 0L;
            derefSpaceTop -= DEREF_HANDLE_OBJECT_SIZE;
            if (derefSpaceTop < DEREF_HANDLE_SPACE_END) {
                CompilerDirectives.transferToInterpreter();
                throw new OutOfMemoryError();
            }
            return addr;
        }
    }

    @Override
    public boolean getI1(LLVMNativePointer addr) {
        return getI1(addr.asNative());
    }

    @Override
    public boolean getI1(long ptr) {
        assert ptr != 0;
        return unsafe.getByte(ptr) != 0;
    }

    @Override
    public byte getI8(LLVMNativePointer addr) {
        return getI8(addr.asNative());
    }

    @Override
    public byte getI8(long ptr) {
        assert ptr != 0;
        return unsafe.getByte(ptr);
    }

    @Override
    public short getI16(LLVMNativePointer addr) {
        return getI16(addr.asNative());
    }

    @Override
    public short getI16(long ptr) {
        assert ptr != 0;
        return unsafe.getShort(ptr);
    }

    @Override
    public int getI32(LLVMNativePointer addr) {
        return getI32(addr.asNative());
    }

    @Override
    public int getI32(long ptr) {
        assert ptr != 0;
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
    public long getI64(LLVMNativePointer addr) {
        return getI64(addr.asNative());
    }

    @Override
    public long getI64(long ptr) {
        assert ptr != 0;
        return unsafe.getLong(ptr);
    }

    @Override
    public float getFloat(LLVMNativePointer addr) {
        return getFloat(addr.asNative());
    }

    @Override
    public float getFloat(long ptr) {
        assert ptr != 0;
        return unsafe.getFloat(ptr);
    }

    @Override
    public double getDouble(LLVMNativePointer addr) {
        return getDouble(addr.asNative());
    }

    @Override
    public double getDouble(long ptr) {
        assert ptr != 0;
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
    public LLVMNativePointer getPointer(LLVMNativePointer addr) {
        return getPointer(addr.asNative());
    }

    @Override
    public LLVMNativePointer getPointer(long ptr) {
        assert ptr != 0;
        return LLVMNativePointer.create(unsafe.getAddress(ptr));
    }

    @Override
    public void putI1(LLVMNativePointer addr, boolean value) {
        putI1(addr.asNative(), value);
    }

    @Override
    public void putI1(long ptr, boolean value) {
        assert ptr != 0;
        unsafe.putByte(ptr, (byte) (value ? 1 : 0));
    }

    @Override
    public void putI8(LLVMNativePointer addr, byte value) {
        putI8(addr.asNative(), value);
    }

    @Override
    public void putI8(long ptr, byte value) {
        assert ptr != 0;
        unsafe.putByte(ptr, value);
    }

    @Override
    public void putI16(LLVMNativePointer addr, short value) {
        putI16(addr.asNative(), value);
    }

    @Override
    public void putI16(long ptr, short value) {
        assert ptr != 0;
        unsafe.putShort(ptr, value);
    }

    @Override
    public void putI32(LLVMNativePointer addr, int value) {
        putI32(addr.asNative(), value);
    }

    @Override
    public void putI32(long ptr, int value) {
        assert ptr != 0;
        unsafe.putInt(ptr, value);
    }

    @Override
    public void putI64(LLVMNativePointer addr, long value) {
        putI64(addr.asNative(), value);
    }

    @Override
    public void putI64(long ptr, long value) {
        assert ptr != 0;
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

    private void putByteArray(LLVMNativePointer addr, byte[] bytes) {
        putByteArray(addr.asNative(), bytes);
    }

    private void putByteArray(long ptr, byte[] bytes) {
        long currentptr = ptr;
        for (int i = 0; i < bytes.length; i++) {
            putI8(currentptr, bytes[i]);
            currentptr += Byte.BYTES;
        }
    }

    @Override
    public void putFloat(LLVMNativePointer addr, float value) {
        putFloat(addr.asNative(), value);
    }

    @Override
    public void putFloat(long ptr, float value) {
        assert ptr != 0;
        unsafe.putFloat(ptr, value);
    }

    @Override
    public void putDouble(LLVMNativePointer addr, double value) {
        putDouble(addr.asNative(), value);
    }

    @Override
    public void putDouble(long ptr, double value) {
        assert ptr != 0;
        unsafe.putDouble(ptr, value);
    }

    @Override
    public void put80BitFloat(LLVMNativePointer addr, LLVM80BitFloat value) {
        putByteArray(addr, value.getBytes());
    }

    @Override
    public void put80BitFloat(long ptr, LLVM80BitFloat value) {
        putByteArray(ptr, value.getBytes());
    }

    @Override
    public void putPointer(LLVMNativePointer addr, LLVMNativePointer value) {
        putPointer(addr.asNative(), value);
    }

    @Override
    public void putPointer(LLVMNativePointer addr, long ptrValue) {
        putPointer(addr.asNative(), ptrValue);
    }

    @Override
    public void putPointer(long ptr, LLVMNativePointer value) {
        putPointer(ptr, value.asNative());
    }

    @Override
    public void putPointer(long ptr, long ptrValue) {
        assert ptr != 0;
        unsafe.putAddress(ptr, ptrValue);
    }

    @Override
    @ExplodeLoop
    public LLVMI32Vector getI32Vector(LLVMNativePointer address, int vectorLength) {
        int[] vector = new int[vectorLength];
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            vector[i] = getI32(currentPtr);
            currentPtr += I32_SIZE_IN_BYTES;
        }
        return LLVMI32Vector.create(vector);
    }

    @Override
    @ExplodeLoop
    public LLVMI8Vector getI8Vector(LLVMNativePointer address, int vectorLength) {
        byte[] vector = new byte[vectorLength];
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            vector[i] = getI8(currentPtr);
            currentPtr += I8_SIZE_IN_BYTES;
        }
        return LLVMI8Vector.create(vector);
    }

    @Override
    @ExplodeLoop
    public LLVMI1Vector getI1Vector(LLVMNativePointer address, int vectorLength) {
        boolean[] vector = new boolean[vectorLength];
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            vector[i] = getI1(currentPtr);
            currentPtr += I1_SIZE_IN_BYTES;
        }
        return LLVMI1Vector.create(vector);
    }

    @Override
    @ExplodeLoop
    public LLVMI16Vector getI16Vector(LLVMNativePointer address, int vectorLength) {
        short[] vector = new short[vectorLength];
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            vector[i] = getI16(currentPtr);
            currentPtr += I16_SIZE_IN_BYTES;
        }
        return LLVMI16Vector.create(vector);
    }

    @Override
    @ExplodeLoop
    public LLVMI64Vector getI64Vector(LLVMNativePointer address, int vectorLength) {
        long[] vector = new long[vectorLength];
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            vector[i] = getI64(currentPtr);
            currentPtr += I64_SIZE_IN_BYTES;
        }
        return LLVMI64Vector.create(vector);
    }

    @Override
    @ExplodeLoop
    public LLVMFloatVector getFloatVector(LLVMNativePointer address, int vectorLength) {
        float[] vector = new float[vectorLength];
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            vector[i] = getFloat(currentPtr);
            currentPtr += FLOAT_SIZE_IN_BYTES;
        }
        return LLVMFloatVector.create(vector);
    }

    @Override
    @ExplodeLoop
    public LLVMDoubleVector getDoubleVector(LLVMNativePointer address, int vectorLength) {
        double[] vector = new double[vectorLength];
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            vector[i] = getDouble(currentPtr);
            currentPtr += DOUBLE_SIZE_IN_BYTES;
        }
        return LLVMDoubleVector.create(vector);
    }

    @Override
    @ExplodeLoop
    public LLVMPointerVector getPointerVector(LLVMNativePointer address, int vectorLength) {
        LLVMNativePointer[] vector = new LLVMNativePointer[vectorLength];
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            vector[i] = getPointer(currentPtr);
            currentPtr += ADDRESS_SIZE_IN_BYTES;
        }
        return LLVMPointerVector.create(vector);
    }

    // watch out for casts such as I32* to I32Vector* when changing the way how vectors are
    // implemented
    @Override
    @ExplodeLoop
    public void putVector(LLVMNativePointer address, LLVMDoubleVector vector, int vectorLength) {
        assert vector.getLength() == vectorLength;
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            putDouble(currentPtr, vector.getValue(i));
            currentPtr += DOUBLE_SIZE_IN_BYTES;
        }
    }

    @Override
    @ExplodeLoop
    public void putVector(LLVMNativePointer address, LLVMFloatVector vector, int vectorLength) {
        assert vector.getLength() == vectorLength;
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            putFloat(currentPtr, vector.getValue(i));
            currentPtr += FLOAT_SIZE_IN_BYTES;
        }
    }

    @Override
    @ExplodeLoop
    public void putVector(LLVMNativePointer address, LLVMI16Vector vector, int vectorLength) {
        assert vector.getLength() == vectorLength;
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            putI16(currentPtr, vector.getValue(i));
            currentPtr += I16_SIZE_IN_BYTES;
        }
    }

    @Override
    @ExplodeLoop
    public void putVector(LLVMNativePointer address, LLVMI1Vector vector, int vectorLength) {
        assert vector.getLength() == vectorLength;
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            putI1(currentPtr, vector.getValue(i));
            currentPtr += I1_SIZE_IN_BYTES;
        }
    }

    @Override
    @ExplodeLoop
    public void putVector(LLVMNativePointer address, LLVMI32Vector vector, int vectorLength) {
        assert vector.getLength() == vectorLength;
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            putI32(currentPtr, vector.getValue(i));
            currentPtr += I32_SIZE_IN_BYTES;
        }
    }

    @Override
    @ExplodeLoop
    public void putVector(LLVMNativePointer address, LLVMI64Vector vector, int vectorLength) {
        assert vector.getLength() == vectorLength;
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            putI64(currentPtr, vector.getValue(i));
            currentPtr += I64_SIZE_IN_BYTES;
        }
    }

    @Override
    @ExplodeLoop
    public void putVector(LLVMNativePointer address, LLVMI8Vector vector, int vectorLength) {
        assert vector.getLength() == vectorLength;
        long currentPtr = address.asNative();
        for (int i = 0; i < vectorLength; i++) {
            putI8(currentPtr, vector.getValue(i));
            currentPtr += I8_SIZE_IN_BYTES;
        }
    }

    @Override
    public LLVMNativePointer allocateCString(String string) {
        LLVMNativePointer basePointer = allocateMemory(string.length() + 1);
        long currentPointer = basePointer.asNative();
        for (int i = 0; i < string.length(); i++) {
            byte c = (byte) string.charAt(i);
            putI8(currentPointer, c);
            currentPointer++;
        }
        putI8(currentPointer, (byte) 0);
        return basePointer;
    }

    @Override
    public void putFunctionPointer(LLVMNativePointer address, long functionIndex) {
        putI64(address, functionIndex);
    }

    @Override
    public void putFunctionPointer(long ptr, long functionIndex) {
        putI64(ptr, functionIndex);
    }

    @Override
    public long getFunctionPointer(LLVMNativePointer addr) {
        return getI64(addr);
    }

    @Override
    public CMPXCHGI32 compareAndSwapI32(LLVMNativePointer p, int comparisonValue, int newValue) {
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
        return unsafe.getAndSetLong(null, address.asNative(), value);
    }

    @Override
    public long getAndAddI64(LLVMNativePointer address, long value) {
        return unsafe.getAndAddLong(null, address.asNative(), value);
    }

    @Override
    public long getAndSubI64(LLVMNativePointer address, long value) {
        return unsafe.getAndAddLong(null, address.asNative(), -value);
    }

    @Override
    public long getAndOpI64(LLVMNativePointer address, long value, LongBinaryOperator f) {
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
        return unsafe.getAndSetInt(null, address.asNative(), value);
    }

    @Override
    public int getAndAddI32(LLVMNativePointer address, int value) {
        return unsafe.getAndAddInt(null, address.asNative(), value);
    }

    @Override
    public int getAndSubI32(LLVMNativePointer address, int value) {
        return unsafe.getAndAddInt(null, address.asNative(), -value);
    }

    @Override
    public int getAndOpI32(LLVMNativePointer address, int value, IntBinaryOperator f) {
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
    public short getAndOpI16(LLVMNativePointer address, short value, BinaryOperator<Short> f) {
        short old;
        short nevv;
        do {
            old = getI16(address);
            nevv = f.apply(old, value);
        } while (!compareAndSwapI16(address, old, nevv).isSwap());
        return old;
    }

    @Override
    public byte getAndOpI8(LLVMNativePointer address, byte value, BinaryOperator<Byte> f) {
        byte old;
        byte nevv;
        do {
            old = getI8(address);
            nevv = f.apply(old, value);
        } while (!compareAndSwapI8(address, old, nevv).isSwap());
        return old;
    }

    @Override
    public boolean getAndOpI1(LLVMNativePointer address, boolean value, BinaryOperator<Boolean> f) {
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

    @Override
    public boolean isDerefMemory(LLVMNativePointer addr) {
        return isDerefMemory(addr.asNative());
    }

    @Override
    public boolean isDerefMemory(long addr) {
        return !noDerefHandleAssumption.isValid() && addr > DEREF_HANDLE_SPACE_END;
    }

    public static long getDerefHandleObjectMask() {
        return DEREF_HANDLE_OBJECT_SIZE - 1;
    }

    private boolean isAllocated(long address) {
        synchronized (derefSpaceTopLock) {
            if (address <= derefSpaceTop) {
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

}
