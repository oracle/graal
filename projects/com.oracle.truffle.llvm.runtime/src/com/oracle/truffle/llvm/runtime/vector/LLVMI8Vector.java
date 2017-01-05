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
package com.oracle.truffle.llvm.runtime.vector;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeap;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

@ValueType
public final class LLVMI8Vector {

    private static final int I8_SIZE = 1;
    private final LLVMAddress address;
    private final int nrElements;

    protected LLVMI8Vector(LLVMAddress addr, int nrElements) {
        this.address = addr;
        this.nrElements = nrElements;
    }

    public static LLVMI8Vector fromI8Array(LLVMAddress target, byte[] vals) {
        LLVMAddress currentTarget = target;
        for (int i = 0; i < vals.length; i++) {
            LLVMMemory.putI8(currentTarget, vals[i]);
            currentTarget = currentTarget.increment(I8_SIZE);
        }
        return new LLVMI8Vector(target, vals.length);
    }

    public static LLVMI8Vector create(LLVMAddress addr, int length) {
        return new LLVMI8Vector(addr, length);
    }

    public byte[] getValues() {
        byte[] values = new byte[nrElements];
        for (int i = 0; i < values.length; i++) {
            values[i] = getValue(i);
        }
        return values;
    }

    public byte getValue(int index) {
        int offset = index * I8_SIZE;
        LLVMAddress increment = address.increment(offset);
        return LLVMMemory.getI8(increment);
    }

    public LLVMI8Vector insert(LLVMAddress target, byte element, int index) {
        LLVMHeap.memCopy(target, address, nrElements * I8_SIZE);
        LLVMAddress elementAddress = target.increment(index * I8_SIZE);
        LLVMMemory.putI8(elementAddress, element);
        return create(target, nrElements);
    }

    public int getLength() {
        return nrElements;
    }

    public LLVMI8Vector add(LLVMAddress addr, LLVMI8Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            byte elementResult = (byte) (getValue(i) + right.getValue(i));
            LLVMMemory.putI8(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I8_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI8Vector mul(LLVMAddress addr, LLVMI8Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            byte elementResult = (byte) (getValue(i) * right.getValue(i));
            LLVMMemory.putI8(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I8_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI8Vector sub(LLVMAddress addr, LLVMI8Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            byte elementResult = (byte) (getValue(i) - right.getValue(i));
            LLVMMemory.putI8(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I8_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI8Vector div(LLVMAddress addr, LLVMI8Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            byte elementResult = (byte) (getValue(i) / right.getValue(i));
            LLVMMemory.putI8(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I8_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI8Vector rem(LLVMAddress addr, LLVMI8Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            byte elementResult = (byte) (getValue(i) % right.getValue(i));
            LLVMMemory.putI8(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I8_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI8Vector and(LLVMAddress addr, LLVMI8Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            byte elementResult = (byte) (getValue(i) & right.getValue(i));
            LLVMMemory.putI8(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I8_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI8Vector or(LLVMAddress addr, LLVMI8Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            byte elementResult = (byte) (getValue(i) | right.getValue(i));
            LLVMMemory.putI8(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I8_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI8Vector leftShift(LLVMAddress addr, LLVMI8Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            byte elementResult = (byte) (getValue(i) << right.getValue(i));
            LLVMMemory.putI8(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I8_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI8Vector logicalRightShift(LLVMAddress addr, LLVMI8Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            byte elementResult = (byte) (getValue(i) >>> right.getValue(i));
            LLVMMemory.putI8(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I8_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI8Vector arithmeticRightShift(LLVMAddress addr, LLVMI8Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            byte elementResult = (byte) (getValue(i) >> right.getValue(i));
            LLVMMemory.putI8(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I8_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI8Vector xor(LLVMAddress addr, LLVMI8Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            byte elementResult = (byte) (getValue(i) ^ right.getValue(i));
            LLVMMemory.putI8(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I8_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMAddress getAddress() {
        return address;
    }

    public int getVectorByteSize() {
        return I8_SIZE * nrElements;
    }

}
