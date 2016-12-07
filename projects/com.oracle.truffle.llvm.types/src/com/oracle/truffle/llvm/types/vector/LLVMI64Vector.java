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

import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

public final class LLVMI64Vector {

    private static final int I64_SIZE = 8;
    private final LLVMAddress address;
    private final int nrElements;

    protected LLVMI64Vector(LLVMAddress addr, int nrElements) {
        this.address = addr;
        this.nrElements = nrElements;
    }

    public static LLVMI64Vector fromI64Array(LLVMAddress target, long[] vals) {
        LLVMAddress currentTarget = target;
        for (int i = 0; i < vals.length; i++) {
            LLVMMemory.putI64(currentTarget, vals[i]);
            currentTarget = currentTarget.increment(I64_SIZE);
        }
        return new LLVMI64Vector(target, vals.length);
    }

    public static LLVMI64Vector create(LLVMAddress addr, int length) {
        return new LLVMI64Vector(addr, length);
    }

    public long[] getValues() {
        long[] values = new long[nrElements];
        for (int i = 0; i < values.length; i++) {
            values[i] = getValue(i);
        }
        return values;
    }

    public long getValue(int index) {
        int offset = index * I64_SIZE;
        LLVMAddress increment = address.increment(offset);
        return LLVMMemory.getI64(increment);
    }

    public LLVMI64Vector insert(LLVMAddress target, long element, int index) {
        LLVMHeap.memCopy(target, address, nrElements * I64_SIZE);
        LLVMAddress elementAddress = target.increment(index * I64_SIZE);
        LLVMMemory.putI64(elementAddress, element);
        return create(target, nrElements);
    }

    public int getLength() {
        return nrElements;
    }

    public LLVMI64Vector add(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = (getValue(i) + right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI64Vector mul(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = (getValue(i) * right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI64Vector sub(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = (getValue(i) - right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI64Vector div(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = (getValue(i) / right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI64Vector divUnsigned(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = Long.divideUnsigned(getValue(i), right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI64Vector rem(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = (getValue(i) % right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI64Vector remUnsigned(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = Long.remainderUnsigned(getValue(i), right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI64Vector and(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = (getValue(i) & right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI64Vector or(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = (getValue(i) | right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI64Vector leftShift(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = (getValue(i) << right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI64Vector logicalRightShift(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = (getValue(i) >>> right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI64Vector arithmeticRightShift(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = (getValue(i) >> right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI64Vector xor(LLVMAddress addr, LLVMI64Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            long elementResult = (getValue(i) ^ right.getValue(i));
            LLVMMemory.putI64(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I64_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMAddress getAddress() {
        return address;
    }

    public int getVectorByteSize() {
        return I64_SIZE * nrElements;
    }

}
