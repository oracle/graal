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

public final class LLVMI32Vector extends LLVMVector<Integer> {

    private static final int I32_SIZE = 4;

    public static LLVMI32Vector fromI32Array(LLVMAddress target, int[] vals) {
        LLVMAddress currentTarget = target;
        for (int i = 0; i < vals.length; i++) {
            LLVMMemory.putI32(currentTarget, vals[i]);
            currentTarget = currentTarget.increment(I32_SIZE);
        }
        return new LLVMI32Vector(target, vals.length);
    }

    public LLVMI32Vector(LLVMAddress address, int nrElements) {
        super(address, nrElements);
    }

    public LLVMI32Vector add(LLVMAddress addr, LLVMI32Vector right) {
        return performOperation(addr, right, (a, b) -> a + b);
    }

    public LLVMI32Vector mul(LLVMAddress addr, LLVMI32Vector right) {
        return performOperation(addr, right, (a, b) -> a * b);
    }

    public LLVMI32Vector sub(LLVMAddress addr, LLVMI32Vector right) {
        return performOperation(addr, right, (a, b) -> a - b);
    }

    public LLVMI32Vector div(LLVMAddress addr, LLVMI32Vector right) {
        return performOperation(addr, right, (a, b) -> a / b);
    }

    public LLVMI32Vector divUnsigned(LLVMAddress addr, LLVMI32Vector right) {
        return performOperation(addr, right, (a, b) -> Integer.divideUnsigned(a, b));
    }

    public LLVMI32Vector rem(LLVMAddress addr, LLVMI32Vector right) {
        return performOperation(addr, right, (a, b) -> a % b);
    }

    public LLVMI32Vector remUnsigned(LLVMAddress addr, LLVMI32Vector right) {
        return performOperation(addr, right, (a, b) -> Integer.remainderUnsigned(a, b));
    }

    public LLVMI32Vector and(LLVMAddress addr, LLVMI32Vector right) {
        return performOperation(addr, right, (a, b) -> a & b);
    }

    public LLVMI32Vector or(LLVMAddress addr, LLVMI32Vector right) {
        return performOperation(addr, right, (a, b) -> a | b);
    }

    public LLVMI32Vector leftShift(LLVMAddress addr, LLVMI32Vector right) {
        return performOperation(addr, right, (a, b) -> a << b);
    }

    public LLVMI32Vector logicalRightShift(LLVMAddress addr, LLVMI32Vector right) {
        return performOperation(addr, right, (a, b) -> a >>> b);
    }

    public LLVMI32Vector arithmeticRightShift(LLVMAddress addr, LLVMI32Vector right) {
        return performOperation(addr, right, (a, b) -> a >> b);
    }

    public LLVMI32Vector xor(LLVMAddress addr, LLVMI32Vector right) {
        return (LLVMI32Vector) performOperation(addr, right, (a, b) -> a ^ b);
    }

    public static LLVMI32Vector createI32Vector(LLVMAddress addr, int size) {
        return new LLVMI32Vector(addr, size);
    }

    @Override
    public int getElementByteSize() {
        return I32_SIZE;
    }

    @Override
    protected LLVMI32Vector create(LLVMAddress addr, int length) {
        return new LLVMI32Vector(addr, length);
    }

    @Override
    public Integer getValue(LLVMAddress addr) {
        return LLVMMemory.getI32(addr);
    }

    @Override
    public void setValue(LLVMAddress address, Integer value) {
        LLVMMemory.putI32(address, value);
    }

}
