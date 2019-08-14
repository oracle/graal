/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm.binary;

import com.oracle.truffle.wasm.collection.ByteArrayList;
import com.oracle.truffle.wasm.collection.IntArrayList;
import com.oracle.truffle.wasm.collection.LongArrayList;

public class ExecutionState {
    private int stackSize;
    private int maxStackSize;
    private ByteArrayList byteConstants;
    private IntArrayList intConstants;
    private IntArrayList stackStates;
    private LongArrayList numericLiterals;

    public ExecutionState() {
        this.stackSize = 0;
        this.maxStackSize = 0;
        this.byteConstants = new ByteArrayList();
        this.intConstants = new IntArrayList();
        this.stackStates = new IntArrayList();
        this.numericLiterals = new LongArrayList();
    }

    public void push() {
        stackSize++;
        maxStackSize = Math.max(stackSize, maxStackSize);
    }

    public void push(int n) {
        stackSize += n;
        maxStackSize = Math.max(stackSize, maxStackSize);
    }

    public void pop() {
        stackSize--;
    }

    public void pop(int n) {
        stackSize -= n;
    }

    public void useByteConstant(byte constant) {
        byteConstants.add(constant);
    }

    public void useIntConstant(int constant) {
        intConstants.add(constant);
    }

    public void pushStackState() {
        stackStates.add(stackSize);
    }

    public void popStackState() {
        stackStates.popBack();
    }

    public int getStackState(int level) {
        return stackStates.get(stackStates.size() - level - 1);
    }

    public int stackSize() {
        return stackSize;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public int byteConstantOffset() {
        return byteConstants.size();
    }

    public int intConstantOffset() {
        return intConstants.size();
    }

    public byte[] byteConstants() {
        return byteConstants.toArray();
    }

    public int[] intConstants() {
        return intConstants.toArray();
    }

    public void saveNumericLiteral(long literal) {
        numericLiterals.add(literal);
    }

    public int numericLiteralOffset() {
        return numericLiterals.size();
    }

    public long[] numericLiterals() {
        return numericLiterals.toArray();
    }
}
