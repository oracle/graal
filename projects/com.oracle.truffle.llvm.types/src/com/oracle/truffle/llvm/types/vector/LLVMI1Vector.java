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
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

public final class LLVMI1Vector extends LLVMVector<Boolean> {

    private static final int I1_SIZE = 1;

    protected LLVMI1Vector(LLVMAddress addr, int nrElements) {
        super(addr, nrElements);
    }

    public static LLVMI1Vector fromI1Array(LLVMAddress target, boolean[] vals) {
        LLVMAddress currentTarget = target;
        for (int i = 0; i < vals.length; i++) {
            LLVMMemory.putI1(currentTarget, vals[i]);
            currentTarget = currentTarget.increment(I1_SIZE);
        }
        return new LLVMI1Vector(target, vals.length);
    }

    @Override
    public int getElementByteSize() {
        return I1_SIZE;
    }

    @Override
    protected LLVMVector<Boolean> create(LLVMAddress addr, int length) {
        return new LLVMI1Vector(addr, length);
    }

    @Override
    public Boolean getValue(LLVMAddress addr) {
        return LLVMMemory.getI1(addr);
    }

    @Override
    public void setValue(LLVMAddress addr, Boolean value) {
        LLVMMemory.putI1(addr, value);
    }

    public static LLVMI1Vector createI1Vector(LLVMAddress addr, int size) {
        return new LLVMI1Vector(addr, size);
    }

    public LLVMI1Vector and(LLVMAddress target, LLVMI1Vector right) {
        return performOperation(target, right, (a, b) -> a & b);
    }

    public LLVMI1Vector or(LLVMAddress target, LLVMI1Vector right) {
        return performOperation(target, right, (a, b) -> a | b);
    }

    public LLVMI1Vector xor(LLVMAddress target, LLVMI1Vector right) {
        return performOperation(target, right, (a, b) -> a ^ b);
    }

}
