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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.wasm.binary.constants.GlobalModifier;
import com.oracle.truffle.wasm.binary.constants.GlobalResolution;
import com.oracle.truffle.wasm.binary.exception.WasmException;
import com.oracle.truffle.wasm.binary.memory.WasmMemoryException;
import com.oracle.truffle.wasm.collection.ByteArrayList;
import com.oracle.truffle.wasm.collection.LongArrayList;

public class SymbolTable {
    private static final int INITIAL_DATA_SIZE = 512;
    private static final int INITIAL_OFFSET_SIZE = 128;
    private static final int INITIAL_FUNCTION_TYPES_SIZE = 128;
    private static final int INITIAL_GLOBALS_SIZE = 128;
    private static final int GLOBAL_EXPORT_BIT = 1 << 24;
    public static final int UNINITIALIZED_TABLE_BIT = 0x8000_0000;

    @CompilationFinal private WasmModule module;

    /**
     * Encodes the arguments and return types of each function type.
     *
     * Given a function type index, the {@link #offsets} array indicates where the encoding
     * for that function type begins in this array.
     *
     * For a function type starting at index i, the encoding is the following
     *
     * <code>
     *   i     i+1   i+2+0        i+2+na-1  i+2+na+0        i+2+na+nr-1
     * +-----+-----+-------+-----+--------+----------+-----+-----------+
     * | na  |  nr | arg 1 | ... | arg na | return 1 | ... | return nr |
     * +-----+-----+-------+-----+--------+----------+-----+-----------+
     * </code>
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

    @CompilationFinal private ArrayList<WasmFunction> importedFunctions;
    @CompilationFinal private LinkedHashMap<String, WasmFunction> exportedFunctions;
    @CompilationFinal private int startFunctionIndex;

    /**
     * This array is monotonically populated from the left.
     * An index i denotes the i-th global in this module.
     * The value at the index i denotes the address of the global
     * in the memory space for all the globals from all the modules
     * (see {@link Globals}).
     *
     * This separation of global indices is done because the index spaces
     * of the globals are module-specific, and the globals can be imported
     * across modules.
     * Thus, the address-space of the globals is not the same as the
     * module-specific index-space.
     */
    @CompilationFinal(dimensions = 1) int[] globalAddresses;

    /**
     * A global type is the value type of the global, followed by its mutability.
     * This is encoded as two bytes -- the lowest (0th) byte is the value type,
     * the 1st byte is the mutability of the global variable,
     * and the 2nd byte is the global's resolution status
     * (see {@link GlobalModifier}, {@link GlobalResolution} and {@link ValueTypes}).
     */
    @CompilationFinal(dimensions = 1) int[] globalTypes;

    /**
     * Tracks all the globals that have not yet been resolved,
     * and will be resolved once the modules are fully linked.
     * The lower 4 bytes are the index of the unresolved global,
     * whereas the higher 4 bytes are the index of the global
     * whose value this global should be initialized with
     * (assuming that this global was declared with a {@code GLOBAL_GET} expression).
     */
    @CompilationFinal private final LongArrayList unresolvedGlobals;

    /**
     * A mapping between the indices of the imported globals and their import specifiers.
     */
    @CompilationFinal private final HashMap<Integer, ImportDescriptor> importedGlobals;

    /**
     * A mapping between the names and the indices of the exported globals.
     */
    @CompilationFinal private final Map<String, Integer> exportedGlobals;

    /**
     * The greatest index of a global in the module.
     */
    @CompilationFinal private int maxGlobalIndex;

    /**
     * The index of the table from the global table space, which this module is using.
     *
     * In the current WebAssembly specification, a module can use at most one table.
     * The value {@link SymbolTable#UNINITIALIZED_TABLE_BIT} denotes that this module uses no table.
     */
    @CompilationFinal private int tableIndex;

    /**
     * The table used in this module.
     */
    @CompilationFinal private ImportDescriptor importedTableDescriptor;

    /**
     * The name of the exported table of this module, if any.
     */
    @CompilationFinal private String exportedTable;

    public SymbolTable(WasmModule module) {
        this.module = module;
        this.typeData = new int[INITIAL_DATA_SIZE];
        this.offsets = new int[INITIAL_OFFSET_SIZE];
        this.typeDataSize = 0;
        this.offsetsSize = 0;
        this.functions = new WasmFunction[INITIAL_FUNCTION_TYPES_SIZE];
        this.numFunctions = 0;
        this.importedFunctions = new ArrayList<>();
        this.exportedFunctions = new LinkedHashMap<>();
        this.startFunctionIndex = -1;
        this.globalAddresses = new int[INITIAL_GLOBALS_SIZE];
        this.globalTypes = new int[INITIAL_GLOBALS_SIZE];
        this.unresolvedGlobals = new LongArrayList();
        this.importedGlobals = new HashMap<>();
        this.exportedGlobals = new LinkedHashMap<>();
        this.maxGlobalIndex = -1;
        this.tableIndex = UNINITIALIZED_TABLE_BIT;
        this.importedTableDescriptor = null;
        this.exportedTable = null;
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

    public int allocateFunctionType(int numParameterTypes, int numReturnTypes) {
        ensureOffsetsCapacity(offsetsSize);
        int typeIdx = offsetsSize++;
        offsets[typeIdx] = typeDataSize;

        assert 0 <= numReturnTypes && numReturnTypes <= 1;
        int size = 2 + numParameterTypes + numReturnTypes;
        ensureTypeDataCapacity(typeDataSize + size);
        typeData[typeDataSize + 0] = numParameterTypes;
        typeData[typeDataSize + 1] = numReturnTypes;
        typeDataSize += size;
        return typeIdx;
    }

    public int allocateFunctionType(byte[] parameterTypes, byte[] returnTypes) {
        final int typeIdx = allocateFunctionType(parameterTypes.length, returnTypes.length);
        for (int i = 0; i < parameterTypes.length; i++) {
            registerFunctionTypeParameterType(typeIdx, i, parameterTypes[i]);
        }
        for (int i = 0; i < returnTypes.length; i++) {
            registerFunctionTypeReturnType(typeIdx, i, returnTypes[i]);
        }
        return typeIdx;
    }

    public void registerFunctionTypeParameterType(int funcTypeIdx, int paramIdx, byte type) {
        int idx = 2 + offsets[funcTypeIdx] + paramIdx;
        typeData[idx] = type;
    }

    public void registerFunctionTypeReturnType(int funcTypeIdx, int returnIdx, byte type) {
        int idx = 2 + offsets[funcTypeIdx] + typeData[offsets[funcTypeIdx]] + returnIdx;
        typeData[idx] = type;
    }

    private void ensureFunctionsCapacity(int index) {
        if (functions.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * functions.length);
            functions = reallocate(functions, numFunctions, newLength);
        }
    }

    private WasmFunction allocateFunction(int typeIndex, ImportDescriptor importDescriptor) {
        ensureFunctionsCapacity(numFunctions);
        final WasmFunction function = new WasmFunction(this, numFunctions, typeIndex, importDescriptor);
        functions[numFunctions] = function;
        numFunctions++;
        return function;
    }

    public WasmFunction declareFunction(int typeIndex) {
        final WasmFunction function = allocateFunction(typeIndex, null);
        return function;
    }

    public WasmFunction declareExportedFunction(int typeIndex, String exportedName) {
        final WasmFunction function = declareFunction(typeIndex);
        exportFunction(exportedName, function.index());
        return function;
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

    public byte getFunctionTypeReturnTypeAt(int typeIndex, int i) {
        int typeOffset = offsets[typeIndex];
        int numArgs = typeData[typeOffset];
        return (byte) typeData[typeOffset + 2 + numArgs + i];
    }

    public ByteArrayList getFunctionTypeArgumentTypes(int typeIndex) {
        ByteArrayList types = new ByteArrayList();
        for (int i = 0; i != getFunctionTypeNumArguments(typeIndex); ++i) {
            types.add(getFunctionTypeArgumentTypeAt(typeIndex, i));
        }
        return types;
    }

    public void exportFunction(String exportName, int functionIndex) {
        exportedFunctions.put(exportName, functions[functionIndex]);
    }

    public Map<String, WasmFunction> exportedFunctions() {
        return exportedFunctions;
    }

    public WasmFunction importFunction(String moduleName, String functionName, int typeIndex) {
        WasmFunction function = allocateFunction(typeIndex, new ImportDescriptor(moduleName, functionName));
        importedFunctions.add(function);
        return function;
    }

    public List<WasmFunction> importedFunctions() {
        return importedFunctions;
    }

    private void ensureGlobalsCapacity(int index) {
        while (index >= globalAddresses.length) {
            final int[] nGlobalIndices = new int[globalAddresses.length * 2];
            System.arraycopy(globalAddresses, 0, nGlobalIndices, 0, globalAddresses.length);
            globalAddresses = nGlobalIndices;
            final int[] nGlobalTypes = new int[globalTypes.length * 2];
            System.arraycopy(globalTypes, 0, nGlobalTypes, 0, globalTypes.length);
            globalTypes = nGlobalTypes;
        }
    }

    private int allocateGlobal(WasmContext context, int index, int valueType, int mutability, GlobalResolution resolution) {
        assert (valueType & 0xff) == valueType;
        assert (mutability & 0xff) == mutability;
        ensureGlobalsCapacity(index);
        maxGlobalIndex = Math.max(maxGlobalIndex, index);
        final Globals globals = context.globals();
        final int address = globals.allocateGlobal();
        globalAddresses[index] = address;
        int globalType = (resolution.ordinal() << 16) | ((mutability << 8) | valueType);
        globalTypes[index] = globalType;
        return address;
    }

    public int declareGlobal(WasmContext context, int index, int valueType, int mutability, GlobalResolution resolution) {
        assert !resolution.isImported();
        return allocateGlobal(context, index, valueType, mutability, resolution);
    }

    public int importGlobal(WasmContext context, String moduleName, String globalName, int index, int valueType, int mutability, GlobalResolution resolution) {
        assert resolution.isImported();
        final int address = allocateGlobal(context, index, valueType, mutability, resolution);
        importedGlobals.put(index, new ImportDescriptor(moduleName, globalName));
        return address;
    }

    public int maxGlobalIndex() {
        return maxGlobalIndex;
    }

    public int globalAddress(int index) {
        return globalAddresses[index];
    }

    public boolean globalExported(int index) {
        final int exportStatus = globalTypes[index] & GLOBAL_EXPORT_BIT;
        return exportStatus != 0;
    }

    public GlobalResolution globalResolution(int index) {
        final int resolutionValue = (globalTypes[index] >>> 16) & 0xff;
        return GlobalResolution.VALUES[resolutionValue];
    }

    public byte globalMutability(int index) {
        return (byte) ((globalTypes[index] >>> 8) & 0xff);
    }

    public byte globalValueType(int index) {
        return (byte) (globalTypes[index] & 0xff);
    }

    private void addUnresolvedGlobal(long unresolvedEntry) {
        unresolvedGlobals.add(unresolvedEntry);
    }

    /**
     * Tracks an unresolved imported global.
     * The global must have been previously allocated.
     */
    public void trackUnresolvedGlobal(int globalIndex) {
        assertGlobalAllocated(globalIndex);
        addUnresolvedGlobal(globalIndex);
    }

    /**
     * Tracks an unresolved declared global, which depends on an unresolved imported global.
     * The global must have been previously allocated.
     */
    public void trackUnresolvedGlobal(int globalIndex, int dependentGlobal) {
        assertGlobalAllocated(globalIndex);
        addUnresolvedGlobal((dependentGlobal << 32) | globalIndex);
    }

    private void assertGlobalAllocated(int globalIndex) {
        if (globalIndex >= maxGlobalIndex || globalTypes[globalIndex] == 0) {
            throw new RuntimeException("Cannot track non-allocated global: " + globalIndex);
        }
    }

    public Map<String, Integer> exportedGlobals() {
        return exportedGlobals;
    }

    private String nameOfExportedGlobal(int index) {
        for (Map.Entry<String, Integer> entry : exportedGlobals.entrySet()) {
            if (entry.getValue() == index) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void exportGlobal(String name, int index) {
        if (globalExported(index)) {
            throw new WasmMemoryException("Global " + index + " already exported under the name: " + nameOfExportedGlobal(index));
        }
        globalTypes[index] |= GLOBAL_EXPORT_BIT;
        // TODO: Invoke Linker to link together any modules with pending unresolved globals.
        exportedGlobals.put(name, index);
    }

    public int declareExportedGlobal(WasmContext context, String name, int index, int valueType, int mutability, GlobalResolution resolution) {
        int address = declareGlobal(context, index, valueType, mutability, resolution);
        exportGlobal(name, index);
        return address;
    }

    public void allocateTable(WasmContext context, int initSize, int maxSize) {
        validateSingleTable();
        tableIndex = context.tables().allocateTable(initSize, maxSize);
    }

    public void importTable(WasmContext context, String moduleName, String tableName, int initSize, int maxSize) {
        validateSingleTable();
        importedTableDescriptor = new ImportDescriptor(moduleName, tableName);
        tableIndex = context.linker().tryResolveTable(context, module, moduleName, tableName, initSize, maxSize);
    }

    private void validateSingleTable() {
        // TODO: Check if multiple table imports are actually allowed.
        if (importedTableDescriptor != null) {
            throw new WasmException("A table has been already imported in the module.");
        }
        if ((tableIndex & UNINITIALIZED_TABLE_BIT) == 0) {
            throw new WasmException("A table has been already declared in the module.");
        }
    }

    public boolean tableExists() {
        return importedTableDescriptor != null || (tableIndex & UNINITIALIZED_TABLE_BIT) == 0;
    }

    public void exportTable(String name) {
        if (exportedTable != null) {
            throw new WasmException("A table has been already exported from this module.");
        }
        if (!tableExists()) {
            throw new WasmException("No table has been declared or imported, so a table cannot be exported.");
        }
        exportedTable = name;
    }

    public int tableIndex() {
        return tableIndex;
    }

    public ImportDescriptor importedTable() {
        return importedTableDescriptor;
    }

    public String exportedTable() {
        return exportedTable;
    }

    public void initializeTableWithFunctions(WasmContext context, int offset, int[] contents) {
        context.tables().ensureSizeAtLeast(tableIndex, offset + contents.length);
        final Object[] table = context.tables().table(tableIndex);
        for (int i = 0; i < contents.length; i++) {
            final int functionIndex = contents[i];
            final WasmFunction function = function(functionIndex);
            table[offset + i] = function;
        }
    }
}
