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
public final class LLVMFloatVector {

    private static final int FLOAT_SIZE = 4;
    private final LLVMAddress address;
    private final int nrElements;

    private LLVMFloatVector(LLVMAddress addr, int nrElements) {
        this.address = addr;
        this.nrElements = nrElements;
    }

    public static LLVMFloatVector fromFloatArray(LLVMAddress target, float[] vals) {
        LLVMAddress currentTarget = target;
        for (int i = 0; i < vals.length; i++) {
            LLVMMemory.putFloat(currentTarget, vals[i]);
            currentTarget = currentTarget.increment(FLOAT_SIZE);
        }
        return new LLVMFloatVector(target, vals.length);
    }

    private static LLVMFloatVector create(LLVMAddress addr, int length) {
        return new LLVMFloatVector(addr, length);
    }

    public static LLVMFloatVector createFloatVector(LLVMAddress addr, int size) {
        return new LLVMFloatVector(addr, size);
    }

    public float[] getValues() {
        float[] values = new float[nrElements];
        for (int i = 0; i < values.length; i++) {
            values[i] = getValue(i);
        }
        return values;
    }

    public LLVMFloatVector add(LLVMAddress addr, LLVMFloatVector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            float elementResult = getValue(i) + right.getValue(i);
            LLVMMemory.putFloat(currentAddr, elementResult);
            currentAddr = currentAddr.increment(FLOAT_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMFloatVector mul(LLVMAddress addr, LLVMFloatVector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            float elementResult = getValue(i) * right.getValue(i);
            LLVMMemory.putFloat(currentAddr, elementResult);
            currentAddr = currentAddr.increment(FLOAT_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMFloatVector sub(LLVMAddress addr, LLVMFloatVector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            float elementResult = getValue(i) - right.getValue(i);
            LLVMMemory.putFloat(currentAddr, elementResult);
            currentAddr = currentAddr.increment(FLOAT_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMFloatVector div(LLVMAddress addr, LLVMFloatVector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            float elementResult = getValue(i) / right.getValue(i);
            LLVMMemory.putFloat(currentAddr, elementResult);
            currentAddr = currentAddr.increment(FLOAT_SIZE);
        }
        return create(addr, nrElements);
    }

    public LLVMFloatVector rem(LLVMAddress addr, LLVMFloatVector right) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < nrElements; i++) {
            float elementResult = getValue(i) % right.getValue(i);
            LLVMMemory.putFloat(currentAddr, elementResult);
            currentAddr = currentAddr.increment(FLOAT_SIZE);
        }
        return create(addr, nrElements);
    }

    public float getValue(int index) {
        int offset = index * FLOAT_SIZE;
        LLVMAddress increment = address.increment(offset);
        return LLVMMemory.getFloat(increment);
    }

    public LLVMFloatVector insert(LLVMAddress target, float element, int index) {
        LLVMHeap.memCopy(target, address, nrElements * FLOAT_SIZE);
        LLVMAddress elementAddress = target.increment(index * FLOAT_SIZE);
        LLVMMemory.putFloat(elementAddress, element);
        return create(target, nrElements);
    }

    public LLVMAddress getAddress() {
        return address;
    }

    public int getVectorByteSize() {
        return FLOAT_SIZE * nrElements;
    }
}
