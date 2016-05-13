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

public final class LLVMI16Vector extends LLVMVector<Short> {

    private static final int I16_SIZE = 2;
    private static final int MASK = 0xffff;

    protected LLVMI16Vector(LLVMAddress addr, int nrElements) {
        super(addr, nrElements);
    }

    public static LLVMI16Vector fromI16Array(LLVMAddress target, short[] vals) {
        LLVMAddress currentTarget = target;
        for (int i = 0; i < vals.length; i++) {
            LLVMMemory.putI16(currentTarget, vals[i]);
            currentTarget = currentTarget.increment(I16_SIZE);
        }
        return new LLVMI16Vector(target, vals.length);
    }

    @Override
    public int getElementByteSize() {
        return I16_SIZE;
    }

    @Override
    protected LLVMVector<Short> create(LLVMAddress addr, int length) {
        return new LLVMI16Vector(addr, length);
    }

    @Override
    public Short getValue(LLVMAddress addr) {
        return LLVMMemory.getI16(addr);
    }

    @Override
    public void setValue(LLVMAddress addr, Short value) {
        LLVMMemory.putI16(addr, value);
    }

    public static LLVMI16Vector createI16Vector(LLVMAddress addr, int size) {
        return new LLVMI16Vector(addr, size);
    }

    public LLVMI16Vector add(LLVMAddress addr, LLVMI16Vector right) {
        return performOperation(addr, right, (a, b) -> (short) (a + b));
    }

    public LLVMI16Vector mul(LLVMAddress addr, LLVMI16Vector right) {
        return performOperation(addr, right, (a, b) -> (short) (a * b));
    }

    public LLVMI16Vector sub(LLVMAddress addr, LLVMI16Vector right) {
        return performOperation(addr, right, (a, b) -> (short) (a - b));
    }

    public LLVMI16Vector div(LLVMAddress addr, LLVMI16Vector right) {
        return performOperation(addr, right, (a, b) -> (short) (a / b));
    }

    public LLVMI16Vector rem(LLVMAddress addr, LLVMI16Vector right) {
        return performOperation(addr, right, (a, b) -> (short) (a % b));
    }

    public LLVMI16Vector and(LLVMAddress addr, LLVMI16Vector right) {
        return performOperation(addr, right, (a, b) -> (short) (a & b));
    }

    public LLVMI16Vector or(LLVMAddress addr, LLVMI16Vector right) {
        return performOperation(addr, right, (a, b) -> (short) (a | b));
    }

    public LLVMI16Vector leftShift(LLVMAddress addr, LLVMI16Vector right) {
        return performOperation(addr, right, (a, b) -> (short) (a << b));
    }

    public LLVMI16Vector logicalRightShift(LLVMAddress addr, LLVMI16Vector right) {
        return performOperation(addr, right, (a, b) -> (short) (a >>> b));
    }

    public LLVMI16Vector arithmeticRightShift(LLVMAddress addr, LLVMI16Vector right) {
        return performOperation(addr, right, (a, b) -> (short) (a >> b));
    }

    public LLVMI16Vector xor(LLVMAddress addr, LLVMI16Vector right) {
        return performOperation(addr, right, (a, b) -> (short) (a ^ b));
    }

    public LLVMI16Vector udiv(LLVMAddress target, LLVMI16Vector right) {
        return performOperation(target, right, (a, b) -> (short) ((a & MASK) / (b & MASK)));
    }

    public LLVMI16Vector urem(LLVMAddress target, LLVMI16Vector right) {
        return performOperation(target, right, (a, b) -> (short) ((a & MASK) % (b & MASK)));
    }

}
