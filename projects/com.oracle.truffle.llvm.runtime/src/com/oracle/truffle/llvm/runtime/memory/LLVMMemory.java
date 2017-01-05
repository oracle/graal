/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

import sun.misc.Unsafe;

public abstract class LLVMMemory {

    static final Unsafe UNSAFE = getUnsafe();

    @SuppressWarnings("restriction")
    static Unsafe getUnsafe() {
        CompilerAsserts.neverPartOfCompilation();
        try {
            Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
            singleoneInstanceField.setAccessible(true);
            return (Unsafe) singleoneInstanceField.get(null);
        } catch (Exception e) {
            throw new AssertionError();
        }
    }

    static long extractAddrNullPointerAllowed(LLVMAddress addr) {
        return addr.getVal();
    }

    public static boolean getI1(LLVMAddress addr) {
        return UNSAFE.getByte(LLVMMemory.extractAddr(addr)) == 0 ? false : true;
    }

    public static byte getI8(LLVMAddress addr) {
        return UNSAFE.getByte(LLVMMemory.extractAddr(addr));
    }

    public static short getI16(LLVMAddress addr) {
        return UNSAFE.getShort(LLVMMemory.extractAddr(addr));
    }

    public static int getI32(LLVMAddress addr) {
        return UNSAFE.getInt(LLVMMemory.extractAddr(addr));
    }

    public static LLVMIVarBit getIVarBit(LLVMAddress addr, int bitWidth) {
        if (bitWidth % Byte.SIZE != 0) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError();
        }
        int bytes = bitWidth / Byte.SIZE;
        byte[] loadedBytes = new byte[bytes];
        LLVMAddress currentAddress = addr;
        for (int i = loadedBytes.length - 1; i >= 0; i--) {
            loadedBytes[i] = getI8(currentAddress);
            currentAddress = currentAddress.increment(Byte.BYTES);
        }
        return LLVMIVarBit.create(bitWidth, loadedBytes);
    }

    public static long getI64(LLVMAddress addr) {
        return UNSAFE.getLong(LLVMMemory.extractAddr(addr));
    }

    public static float getFloat(LLVMAddress addr) {
        return UNSAFE.getFloat(LLVMMemory.extractAddr(addr));
    }

    public static double getDouble(LLVMAddress addr) {
        return UNSAFE.getDouble(LLVMMemory.extractAddr(addr));
    }

    public static LLVM80BitFloat get80BitFloat(LLVMAddress addr) {
        byte[] bytes = new byte[LLVM80BitFloat.BYTE_WIDTH];
        LLVMAddress currentAddress = addr;
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = getI8(currentAddress);
            currentAddress = currentAddress.increment(Byte.BYTES);
        }
        return LLVM80BitFloat.fromBytes(bytes);
    }

    static long extractAddr(LLVMAddress addr) {
        assert addr.getVal() != 0;
        return addr.getVal();
    }

    public static LLVMAddress getAddress(LLVMAddress addr) {
        return LLVMAddress.fromLong(UNSAFE.getAddress(extractAddr(addr)));
    }

    public static void putI1(LLVMAddress addr, boolean value) {
        UNSAFE.putByte(extractAddr(addr), (byte) (value ? 1 : 0));
    }

    public static void putI8(LLVMAddress addr, byte value) {
        UNSAFE.putByte(extractAddr(addr), value);
    }

    public static void putI16(LLVMAddress addr, short value) {
        UNSAFE.putShort(extractAddr(addr), value);
    }

    public static void putI32(LLVMAddress addr, int value) {
        UNSAFE.putInt(extractAddr(addr), value);
    }

    public static void putI64(LLVMAddress addr, long value) {
        UNSAFE.putLong(extractAddr(addr), value);
    }

    public static void putIVarBit(LLVMAddress addr, LLVMIVarBit value) {
        byte[] bytes = value.getBytes();
        LLVMAddress currentAddress = addr;
        for (int i = bytes.length - 1; i >= 0; i--) {
            putI8(currentAddress, bytes[i]);
            currentAddress = currentAddress.increment(Byte.BYTES);
        }
    }

    static void putByteArray(LLVMAddress addr, byte[] bytes) {
        LLVMAddress currentAddress = addr;
        for (int i = 0; i < bytes.length; i++) {
            putI8(currentAddress, bytes[i]);
            currentAddress = currentAddress.increment(Byte.BYTES);
        }
    }

    public static void putFloat(LLVMAddress addr, float value) {
        UNSAFE.putFloat(extractAddr(addr), value);
    }

    public static void putDouble(LLVMAddress addr, double value) {
        UNSAFE.putDouble(extractAddr(addr), value);
    }

    public static void put80BitFloat(LLVMAddress addr, LLVM80BitFloat value) {
        putByteArray(addr, value.getBytes());
    }

    public static void putAddress(LLVMAddress addr, LLVMAddress value) {
        UNSAFE.putAddress(extractAddr(addr), value.getVal());
    }

    public static LLVMI32Vector getI32Vector(LLVMAddress addr, int size) {
        return LLVMI32Vector.create(addr, size);
    }

    public static LLVMI8Vector getI8Vector(LLVMAddress addr, int size) {
        return LLVMI8Vector.create(addr, size);
    }

    public static LLVMI1Vector getI1Vector(LLVMAddress addr, int size) {
        return LLVMI1Vector.create(addr, size);
    }

    public static LLVMI16Vector getI16Vector(LLVMAddress addr, int size) {
        return LLVMI16Vector.create(addr, size);
    }

    public static LLVMI64Vector getI64Vector(LLVMAddress addr, int size) {
        return LLVMI64Vector.create(addr, size);
    }

    public static LLVMFloatVector getFloatVector(LLVMAddress addr, int size) {
        return LLVMFloatVector.createFloatVector(addr, size);
    }

    public static LLVMDoubleVector getDoubleVector(LLVMAddress addr, int size) {
        return LLVMDoubleVector.createDoubleVector(addr, size);
    }

    public static void putStruct(LLVMAddress address, LLVMAddress value, int structSize) {
        LLVMHeap.memCopy(address, value, structSize);
    }

    // watch out for casts such as I32* to I32Vector* when changing the way how vectors are
    // implemented
    public static void putVector(LLVMAddress addr, LLVMDoubleVector vector) {
        LLVMHeap.memCopy(addr, vector.getAddress(), vector.getVectorByteSize());
    }

    public static void putVector(LLVMAddress addr, LLVMFloatVector vector) {
        LLVMHeap.memCopy(addr, vector.getAddress(), vector.getVectorByteSize());
    }

    public static void putVector(LLVMAddress addr, LLVMI16Vector vector) {
        LLVMHeap.memCopy(addr, vector.getAddress(), vector.getVectorByteSize());
    }

    public static void putVector(LLVMAddress addr, LLVMI1Vector vector) {
        LLVMHeap.memCopy(addr, vector.getAddress(), vector.getVectorByteSize());
    }

    public static void putVector(LLVMAddress addr, LLVMI32Vector vector) {
        LLVMHeap.memCopy(addr, vector.getAddress(), vector.getVectorByteSize());
    }

    public static void putVector(LLVMAddress addr, LLVMI64Vector vector) {
        LLVMHeap.memCopy(addr, vector.getAddress(), vector.getVectorByteSize());
    }

    public static void putVector(LLVMAddress addr, LLVMI8Vector vector) {
        LLVMHeap.memCopy(addr, vector.getAddress(), vector.getVectorByteSize());
    }

}
