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
public final class LLVMI16Vector {

    private static final int I16_SIZE = 2;
    private static final int MASK = 0xffff;

    private final LLVMAddress address;
    private final int nrElements;

    private LLVMI16Vector(LLVMAddress addr, int nrElements) {
        this.address = addr;
        this.nrElements = nrElements;
    }

    public LLVMI16Vector add(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) (getValue(i) + right.getValue(i));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI16Vector mul(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) (getValue(i) * right.getValue(i));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI16Vector sub(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) (getValue(i) - right.getValue(i));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI16Vector div(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) (getValue(i) / right.getValue(i));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI16Vector rem(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) (getValue(i) % right.getValue(i));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI16Vector and(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) (getValue(i) & right.getValue(i));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI16Vector or(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) (getValue(i) | right.getValue(i));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI16Vector leftShift(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) (getValue(i) << right.getValue(i));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI16Vector logicalRightShift(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) (getValue(i) >>> right.getValue(i));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI16Vector arithmeticRightShift(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) (getValue(i) >> right.getValue(i));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI16Vector xor(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) (getValue(i) ^ right.getValue(i));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI16Vector udiv(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) ((getValue(i) & MASK) / (right.getValue(i) & MASK));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI16Vector urem(LLVMAddress addr, LLVMI16Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            short elementResult = (short) ((getValue(i) & MASK) % (right.getValue(i) & MASK));
            LLVMMemory.putI16(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I16_SIZE);
        }
        return create(addr, nrElements);
    }

    public static LLVMI16Vector fromI16Array(LLVMAddress target, short[] vals) {
        LLVMAddress currentTarget = target;
        for (int i = 0; i < vals.length; i++) {
            LLVMMemory.putI16(currentTarget, vals[i]);
            currentTarget = currentTarget.increment(I16_SIZE);
        }
        return new LLVMI16Vector(target, vals.length);
    }

    public static LLVMI16Vector create(LLVMAddress addr, int length) {
        return new LLVMI16Vector(addr, length);
    }

    public short[] getValues() {
        short[] values = new short[nrElements];
        for (int i = 0; i < values.length; i++) {
            values[i] = getValue(i);
        }
        return values;
    }

    public short getValue(int index) {
        int offset = index * I16_SIZE;
        LLVMAddress increment = address.increment(offset);
        return LLVMMemory.getI16(increment);
    }

    public LLVMI16Vector insert(LLVMAddress target, short element, int index) {
        LLVMHeap.memCopy(target, address, nrElements * I16_SIZE);
        LLVMAddress elementAddress = target.increment(index * I16_SIZE);
        LLVMMemory.putI16(elementAddress, element);
        return create(target, nrElements);
    }

    public LLVMAddress getAddress() {
        return address;
    }

    public int getVectorByteSize() {
        return I16_SIZE * nrElements;
    }

    public int getLength() {
        return nrElements;
    }

}
