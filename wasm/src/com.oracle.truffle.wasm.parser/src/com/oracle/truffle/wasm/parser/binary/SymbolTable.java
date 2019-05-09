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
package com.oracle.truffle.wasm.parser.binary;

public class SymbolTable {
    private static final int INITIAL_DATA_SIZE = 512;
    private static final int INITIAL_OFFSET_SIZE = 128;
    private static final int INITIAL_FUNCTION_IDXS_SIZE = 128;

    private int[] typeData;
    private int[] offsets;
    private int typeDataSize;
    private int offsetsSize;

    private int[] functionTypeIdxs;
    private int functionTypeSize;

    public SymbolTable() {
        this.typeData = new int[INITIAL_DATA_SIZE];
        this.offsets = new int[INITIAL_OFFSET_SIZE];
        this.typeDataSize = 0;
        this.offsetsSize = 0;
        this.functionTypeIdxs = new int[INITIAL_FUNCTION_IDXS_SIZE];
        this.functionTypeSize = 0;
    }

    private int[] reallocate(int[] array, int currentSize, int newLength) {
        int[] newArray = new int[newLength];
        System.arraycopy(array, 0, newArray, 0, currentSize);
        return newArray;
    }

    private void ensureTypeDataCapacity(int index) {
        if (typeData.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * typeData.length);
            typeData = reallocate(typeData, typeDataSize, newLength);
        }
    }

    private void ensureOffsetsCapacity(int index) {
        if (offsets.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * offsets.length);
            offsets = reallocate(offsets, offsetsSize, newLength);
        }
    }

    public int allocateFunctionType(int numArgTypes, int numReturnTypes) {
        ensureOffsetsCapacity(offsetsSize);
        int typeIdx = offsetsSize++;
        offsets[typeIdx] = typeDataSize;

        Assert.assertInRange(numReturnTypes, 0, 1, "Invalid return value size");
        int size = 2 + numArgTypes + numReturnTypes;
        ensureTypeDataCapacity(typeDataSize + size);
        typeData[typeDataSize + 0] = numArgTypes;
        typeData[typeDataSize + 1] = numReturnTypes;
        typeDataSize += size;
        return typeIdx;
    }

    public void registerFunctionTypeParameter(int funcTypeIdx, int paramIdx, byte type) {
        int idx = 2 + offsets[funcTypeIdx] + paramIdx;
        typeData[idx] = type;
    }

    public void registerFunctionTypeReturnType(int funcTypeIdx, int paramIdx, byte type) {
        int idx = 2 + offsets[funcTypeIdx] + typeData[offsets[funcTypeIdx]] + paramIdx;
        typeData[idx] = type;
    }

    private void ensureFunctionTypeCapacity(int index) {
        if (functionTypeIdxs.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * functionTypeIdxs.length);
            functionTypeIdxs = reallocate(functionTypeIdxs, functionTypeSize, newLength);
        }
    }

    public void allocateFunctionIndex(int typeIndex) {
        ensureFunctionTypeCapacity(typeIndex);
        functionTypeIdxs[functionTypeSize] = typeIndex;
        ++functionTypeSize;
    }
}
