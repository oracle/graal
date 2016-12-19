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
public final class LLVMI1Vector {

    private static final int I1_SIZE = 1;
    private final LLVMAddress address;
    private final int nrElements;

    private LLVMI1Vector(LLVMAddress addr, int nrElements) {
        this.address = addr;
        this.nrElements = nrElements;
    }

    public static LLVMI1Vector fromI1Array(LLVMAddress target, boolean[] vals) {
        LLVMAddress currentTarget = target;
        for (int i = 0; i < vals.length; i++) {
            LLVMMemory.putI1(currentTarget, vals[i]);
            currentTarget = currentTarget.increment(I1_SIZE);
        }
        return new LLVMI1Vector(target, vals.length);
    }

    public LLVMI1Vector and(LLVMAddress addr, LLVMI1Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            boolean elementResult = (getValue(i) & right.getValue(i));
            LLVMMemory.putI1(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I1_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI1Vector or(LLVMAddress addr, LLVMI1Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            boolean elementResult = (getValue(i) | right.getValue(i));
            LLVMMemory.putI1(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I1_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMI1Vector xor(LLVMAddress addr, LLVMI1Vector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            boolean elementResult = (getValue(i) ^ right.getValue(i));
            LLVMMemory.putI1(currentAddr, elementResult);
            currentAddr = currentAddr.increment(I1_SIZE);
        }
        return create(addr, nrElements);
    }

    public static LLVMI1Vector create(LLVMAddress addr, int length) {
        return new LLVMI1Vector(addr, length);
    }

    public boolean[] getValues() {
        boolean[] values = new boolean[nrElements];
        for (int i = 0; i < values.length; i++) {
            values[i] = getValue(i);
        }
        return values;
    }

    public boolean getValue(int index) {
        int offset = index * I1_SIZE;
        LLVMAddress increment = address.increment(offset);
        return LLVMMemory.getI1(increment);
    }

    public LLVMI1Vector insert(LLVMAddress target, boolean element, int index) {
        LLVMHeap.memCopy(target, address, nrElements * I1_SIZE);
        LLVMAddress elementAddress = target.increment(index * I1_SIZE);
        LLVMMemory.putI1(elementAddress, element);
        return create(target, nrElements);
    }

    public int getLength() {
        return nrElements;
    }

    public LLVMAddress getAddress() {
        return address;
    }

    public int getVectorByteSize() {
        return I1_SIZE * nrElements;
    }
}
