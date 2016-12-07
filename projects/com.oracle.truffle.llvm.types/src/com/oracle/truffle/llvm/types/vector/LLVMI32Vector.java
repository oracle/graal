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
package com.oracle.truffle.llvm.types.vector;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

@ValueType
public final class LLVMI32Vector {

    private static final int I32_SIZE = 4;
    private final LLVMAddress address;
    private final int nrElements;

    public static LLVMI32Vector fromI32Array(LLVMAddress target, int[] vals) {
        LLVMAddress currentTarget = target;
        for (int i = 0; i < vals.length; i++) {
            LLVMMemory.putI32(currentTarget, vals[i]);
            currentTarget = currentTarget.increment(I32_SIZE);
        }
        return new LLVMI32Vector(target, vals.length);
    }

    private LLVMI32Vector(LLVMAddress addr, int nrElements) {
        this.address = addr;
        this.nrElements = nrElements;
    }

    public LLVMI32Vector add(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = (getValue(i) + right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI32Vector mul(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = (getValue(i) * right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI32Vector sub(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = (getValue(i) - right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI32Vector div(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = (getValue(i) / right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI32Vector divUnsigned(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = Integer.divideUnsigned(getValue(i), right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI32Vector rem(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = (getValue(i) % right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI32Vector remUnsigned(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = Integer.remainderUnsigned(getValue(i), right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI32Vector and(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = (getValue(i) & right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI32Vector or(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = (getValue(i) | right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI32Vector leftShift(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = (getValue(i) << right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI32Vector logicalRightShift(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = (getValue(i) >>> right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI32Vector arithmeticRightShift(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = (getValue(i) >> right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI32Vector xor(LLVMAddress addr, LLVMI32Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            int elementResult = (getValue(i) ^ right.getValue(i));
            LLVMMemory.putI32(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I32_SIZE);
        }
        return create(addr, nrElements);
    }

    public static LLVMI32Vector create(LLVMAddress addr, int length) {
        return new LLVMI32Vector(addr, length);
    }

    public int[] getValues() {
        int[] values = new int[nrElements];
        for (int i = 0; i < values.length; i++) {
            values[i] = getValue(i);
        }
        return values;
    }

    public int getValue(int index) {
        int offset = index * I32_SIZE;
        LLVMAddress increment = address.increment(offset);
        return LLVMMemory.getI32(increment);
    }

    public LLVMI32Vector insert(LLVMAddress target, int element, int index) {
        LLVMHeap.memCopy(target, address, nrElements * I32_SIZE);
        LLVMAddress elementAddress = target.increment(index * I32_SIZE);
        LLVMMemory.putI32(elementAddress, element);
        return create(target, nrElements);
    }

    public int getLength() {
        return nrElements;
    }

    public LLVMAddress getAddress() {
        return address;
    }

    public int getVectorByteSize() {
        return I32_SIZE * nrElements;
    }

}
