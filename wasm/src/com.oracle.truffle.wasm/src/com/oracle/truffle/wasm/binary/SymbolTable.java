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

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.wasm.collection.ByteArrayList;

public class SymbolTable {
    private static final int INITIAL_DATA_SIZE = 512;
    private static final int INITIAL_OFFSET_SIZE = 128;
    private static final int INITIAL_FUNCTION_TYPES_SIZE = 128;

    @CompilationFinal private WasmModule module;

    /**
     * Encodes the arguments and return types of each function type.
     *
     * Given a function type index, the {@link #offsets} array indicates where the encoding
     * for that function type begins in this array.
     *
     * For a function type starting at index i, the encoding is the following
     *
     *   i     i+1   i+2+0        i+2+na-1  i+2+na+0        i+2+na+nr-1
     * +-----+-----+-------+-----+--------+----------+-----+-----------+
     * | na  |  nr | arg 1 | ... | arg na | return 1 | ... | return nr |
     * +-----+-----+-------+-----+--------+----------+-----+-----------+
     *
     * where
     *   na: the number of arguments
     *   nr: the number of return values
     *
     * This array is monotonically populated from left to right during parsing. Any code that uses
     * this array should only access the locations in the array that have already been populated.
     */
    @CompilationFinal(dimensions = 1) private int[] typeData;

    /**
     * Stores the offset of each function type into the {@link #typeData} array.
     *
     * This array is monotonically populated from left to right during parsing. Any code that uses
     * this array should only access the locations in the array that have already been populated.
     */
    @CompilationFinal(dimensions = 1) private int[] offsets;

    @CompilationFinal private int typeDataSize;
    @CompilationFinal private int offsetsSize;

    /**
     * Stores the function objects for a WebAssembly module.
     *
     * This array is monotonically populated from left to right during parsing. Any code that uses
     * this array should only access the locations in the array that have already been populated.
     */
    @CompilationFinal(dimensions = 1) private WasmFunction[] functions;
    @CompilationFinal private int numFunctions;

    @CompilationFinal private Map<String, WasmFunction> exportedFunctions;
    @CompilationFinal private int startFunctionIndex;

    public SymbolTable(WasmModule module) {
        this.module = module;
        this.typeData = new int[INITIAL_DATA_SIZE];
        this.offsets = new int[INITIAL_OFFSET_SIZE];
        this.typeDataSize = 0;
        this.offsetsSize = 0;
        this.functions = new WasmFunction[INITIAL_FUNCTION_TYPES_SIZE];
        this.numFunctions = 0;
        this.exportedFunctions = new HashMap<>();
        this.startFunctionIndex = -1;
    }

    private static int[] reallocate(int[] array, int currentSize, int newLength) {
        int[] newArray = new int[newLength];
        System.arraycopy(array, 0, newArray, 0, currentSize);
        return newArray;
    }

    private static WasmFunction[] reallocate(WasmFunction[] array, int currentSize, int newLength) {
        WasmFunction[] newArray = new WasmFunction[newLength];
        System.arraycopy(array, 0, newArray, 0, currentSize);
        return newArray;
    }

    /**
     * Ensure that the {@link #typeData} array has enough space to store {@code index}.
     * If there is no enough space, then a reallocation of the array takes place, doubling its capacity.
     *
     * No synchronisation is required for this method, as it is only called during parsing,
     * which is carried out by a single thread.
     */
    private void ensureTypeDataCapacity(int index) {
        if (typeData.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * typeData.length);
            typeData = reallocate(typeData, typeDataSize, newLength);
        }
    }

    /**
     * Ensure that the {@link #offsets} array has enough space to store {@code index}.
     * If there is no enough space, then a reallocation of the array takes place, doubling its capacity.
     *
     * No synchronisation is required for this method, as it is only called during parsing,
     * which is carried out by a single thread.
     */
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

        assert 0 <= numReturnTypes && numReturnTypes <= 1;
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

    private void ensureFunctionsCapacity(int index) {
        if (functions.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * functions.length);
            functions = reallocate(functions, numFunctions, newLength);
        }
    }

    public void allocateFunction(WasmLanguage language, int typeIndex) {
        ensureFunctionsCapacity(numFunctions);
        functions[numFunctions] = new WasmFunction(this, language, numFunctions, typeIndex);
        numFunctions++;
    }

    public void markFunctionAsExported(String exportName, int functionIndex) {
        exportedFunctions.put(exportName, functions[functionIndex]);
    }

    public void setStartFunction(int functionIndex) {
        this.startFunctionIndex = functionIndex;
    }

    public int numFunctions() {
        return numFunctions;
    }

    public WasmFunction function(int funcIndex) {
        assert 0 <= funcIndex && funcIndex <= numFunctions() - 1;
        return functions[funcIndex];
    }

    public WasmFunction function(String exportName) {
        WasmFunction function = exportedFunctions.get(exportName);
        assert function != null;
        return function;
    }

    public int getFunctionTypeNumArguments(int typeIndex) {
        int typeOffset = offsets[typeIndex];
        int numArgs = typeData[typeOffset + 0];
        return numArgs;
    }

    public byte getFunctionTypeReturnType(int typeIndex) {
        int typeOffset = offsets[typeIndex];
        int numArgTypes = typeData[typeOffset + 0];
        int numReturnTypes = typeData[typeOffset + 1];
        return numReturnTypes == 0 ? (byte) 0x40 : (byte) typeData[typeOffset + 2 + numArgTypes];
    }

    public int getFunctionTypeReturnTypeLength(int typeIndex) {
        int typeOffset = offsets[typeIndex];
        int numReturnTypes = typeData[typeOffset + 1];
        return numReturnTypes;
    }

    public WasmFunction startFunction() {
        if (startFunctionIndex == -1) {
            return null;
        }
        return functions[startFunctionIndex];
    }

    WasmModule module() {
        return module;
    }

    public byte getFunctionTypeArgumentTypeAt(int typeIndex, int i) {
        int typeOffset = offsets[typeIndex];
        return (byte) typeData[typeOffset + 2 + i];
    }

    public ByteArrayList getFunctionTypeArgumentTypes(int typeIndex) {
        ByteArrayList types = new ByteArrayList();
        for (int i = 0; i != getFunctionTypeNumArguments(typeIndex); ++i) {
            types.add(getFunctionTypeArgumentTypeAt(typeIndex, i));
        }
        return types;
    }
}
