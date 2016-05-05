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

@ValueType
public abstract class LLVMVector<T> {

    // TODO: lambdas and PE?

    private final LLVMAddress address;
    private final int nrElements;

    protected LLVMVector(LLVMAddress addr, int nrElements) {
        this.address = addr;
        this.nrElements = nrElements;
    }

    public abstract int getElementByteSize();

    public int getVectorByteSize() {
        return getElementByteSize() * getLength();
    }

    public int getLength() {
        return nrElements;
    }

    public LLVMAddress getAddress() {
        return address;
    }

    public T getValue(int index) {
        int offset = index * getElementByteSize();
        LLVMAddress increment = address.increment(offset);
        return getValue(increment);
    }

    protected abstract LLVMVector<T> create(LLVMAddress addr, int length);

    public abstract T getValue(LLVMAddress addr);

    public abstract void setValue(LLVMAddress addr, T value);

    interface Operation<T> {
        T op(T left, T right);
    }

    public <V extends LLVMVector<T>> V performOperation(LLVMAddress addr, LLVMVector<T> other, Operation<T> op) {
        LLVMAddress currentAddr = addr;
        for (int i = 0; i < getLength(); i++) {
            T elementResult = op.op(getValue(i), other.getValue(i));
            setValue(currentAddr, elementResult);
            currentAddr = currentAddr.increment(getElementByteSize());
        }
        @SuppressWarnings("unchecked")
        V result = (V) create(addr, getLength());
        return result;
    }

    public <V extends LLVMVector<T>> V insert(LLVMAddress target, T element, int index) {
        LLVMHeap.memCopy(target, getAddress(), getLength() * getElementByteSize());
        LLVMAddress elementAddress = target.increment(index * getElementByteSize());
        setValue(elementAddress, element);
        @SuppressWarnings("unchecked")
        V result = (V) create(target, getLength());
        return result;
    }

    public T[] getValues() {
        @SuppressWarnings("unchecked")
        T[] values = (T[]) new Object[getLength()];
        for (int i = 0; i < values.length; i++) {
            values[i] = getValue(i);
        }
        return values;
    }

}
