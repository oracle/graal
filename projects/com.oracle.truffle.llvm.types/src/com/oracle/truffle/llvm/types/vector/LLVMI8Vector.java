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

public final class LLVMI8Vector extends LLVMVector<Byte> {

    private static final int I1_SIZE = 1;

    protected LLVMI8Vector(LLVMAddress addr, int nrElements) {
        super(addr, nrElements);
    }

    @Override
    public int getElementByteSize() {
        return I1_SIZE;
    }

    @Override
    protected LLVMVector<Byte> create(LLVMAddress addr, int length) {
        return new LLVMI8Vector(addr, length);
    }

    @Override
    public Byte getValue(LLVMAddress addr) {
        return LLVMMemory.getI8(addr);
    }

    @Override
    public void setValue(LLVMAddress addr, Byte value) {
        LLVMMemory.putI8(addr, value);
    }

    public static LLVMI8Vector createI8Vector(LLVMAddress addr, int size) {
        return new LLVMI8Vector(addr, size);
    }

    public static LLVMI8Vector fromI8Array(LLVMAddress target, byte[] vals) {
        LLVMAddress currentTarget = target;
        for (int i = 0; i < vals.length; i++) {
            LLVMMemory.putI8(currentTarget, vals[i]);
            currentTarget = currentTarget.increment(I1_SIZE);
        }
        return new LLVMI8Vector(target, vals.length);
    }

    public LLVMI8Vector add(LLVMAddress addr, LLVMI8Vector right) {
        return performOperation(addr, right, (a, b) -> (byte) (a + b));
    }

    public LLVMI8Vector mul(LLVMAddress addr, LLVMI8Vector right) {
        return performOperation(addr, right, (a, b) -> (byte) (a * b));
    }

    public LLVMI8Vector sub(LLVMAddress addr, LLVMI8Vector right) {
        return performOperation(addr, right, (a, b) -> (byte) (a - b));
    }

    public LLVMI8Vector div(LLVMAddress addr, LLVMI8Vector right) {
        return performOperation(addr, right, (a, b) -> (byte) (a / b));
    }

    public LLVMI8Vector rem(LLVMAddress addr, LLVMI8Vector right) {
        return performOperation(addr, right, (a, b) -> (byte) (a % b));
    }

    public LLVMI8Vector and(LLVMAddress addr, LLVMI8Vector right) {
        return performOperation(addr, right, (a, b) -> (byte) (a & b));
    }

    public LLVMI8Vector or(LLVMAddress addr, LLVMI8Vector right) {
        return performOperation(addr, right, (a, b) -> (byte) (a | b));
    }

    public LLVMI8Vector leftShift(LLVMAddress addr, LLVMI8Vector right) {
        return performOperation(addr, right, (a, b) -> (byte) (a << b));
    }

    public LLVMI8Vector logicalRightShift(LLVMAddress addr, LLVMI8Vector right) {
        return performOperation(addr, right, (a, b) -> (byte) (a >>> b));
    }

    public LLVMI8Vector arithmeticRightShift(LLVMAddress addr, LLVMI8Vector right) {
        return performOperation(addr, right, (a, b) -> (byte) (a >> b));
    }

    public LLVMI8Vector xor(LLVMAddress addr, LLVMI8Vector right) {
        return (LLVMI8Vector) performOperation(addr, right, (a, b) -> (byte) (a ^ b));
    }

}
