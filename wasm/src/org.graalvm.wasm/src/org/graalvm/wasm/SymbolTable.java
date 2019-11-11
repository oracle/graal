/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.constants.GlobalResolution;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.UnsafeWasmMemory;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryException;
import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.collection.LongArrayList;

/**
 * Contains the symbol information of a module.
 */
public class SymbolTable {
    private static final int INITIAL_DATA_SIZE = 512;
    private static final int INITIAL_OFFSET_SIZE = 128;
    private static final int INITIAL_FUNCTION_TYPES_SIZE = 128;
    private static final int INITIAL_GLOBALS_SIZE = 128;
    private static final int GLOBAL_EXPORT_BIT = 1 << 24;
    private static final int UNINITIALIZED_TABLE_BIT = 0x8000_0000;

    @CompilationFinal private WasmModule module;

    /**
     * Encodes the arguments and return types of each function type.
     *
     * Given a function type index, the {@link #offsets} array indicates where the encoding for that
     * function type begins in this array.
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
     * where na: the number of arguments nr: the number of return values
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

    /**
     * List of all imported functions.
     */
    private final ArrayList<WasmFunction> importedFunctions;

    /**
     * Map from exported function names to respective functions.
     */
    private final LinkedHashMap<String, WasmFunction> exportedFunctions;

    /**
     * Map from function indices to the exported names of respective functions.
     */
    private final HashMap<Integer, String> exportedFunctionsByIndex;

    /**
     * Index of the start function if it exists, or -1 otherwise.
     */
    @CompilationFinal private int startFunctionIndex;

    /**
     * This array is monotonically populated from the left. An index i denotes the i-th global in
     * this module. The value at the index i denotes the address of the global in the memory space
     * for all the globals from all the modules (see {@link Globals}).
     *
     * This separation of global indices is done because the index spaces of the globals are
     * module-specific, and the globals can be imported across modules. Thus, the address-space of
     * the globals is not the same as the module-specific index-space.
     */
    @CompilationFinal(dimensions = 1) int[] globalAddresses;

    /**
     * A global type is the value type of the global, followed by its mutability. This is encoded as
     * two bytes -- the lowest (0th) byte is the value type, the 1st byte is the mutability of the
     * global variable, and the 2nd byte is the global's resolution status (see
     * {@link GlobalModifier}, {@link GlobalResolution} and {@link ValueTypes}).
     */
    @CompilationFinal(dimensions = 1) int[] globalTypes;

    /**
     * Tracks all the globals that have not yet been resolved, and will be resolved once the modules
     * are fully linked. The lower 4 bytes are the index of the unresolved global, whereas the
     * higher 4 bytes are the index of the global whose value this global should be initialized with
     * (assuming that this global was declared with a {@code GLOBAL_GET} expression).
     */
    @CompilationFinal private final LongArrayList unresolvedGlobals;

    /**
     * A mapping between the indices of the imported globals and their import specifiers.
     */
    @CompilationFinal private final LinkedHashMap<Integer, ImportDescriptor> importedGlobals;

    /**
     * A mapping between the names and the indices of the exported globals.
     */
    @CompilationFinal private final LinkedHashMap<String, Integer> exportedGlobals;

    /**
     * The greatest index of a global in the module.
     */
    @CompilationFinal private int maxGlobalIndex;

    /**
     * The index of the table from the context-specific table space, which this module is using.
     *
     * In the current WebAssembly specification, a module can use at most one table. The value
     * {@link SymbolTable#UNINITIALIZED_TABLE_BIT} denotes that this module uses no table.
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

    /**
     * Memory that this module is using.
     *
     * In the current WebAssembly specification, a module can use at most one memory. The value
     * {@code null} denotes that this module uses no memory.
     */
    @CompilationFinal private WasmMemory memory;

    /**
     * The memory used in this module.
     */
    @CompilationFinal private ImportDescriptor importedMemoryDescriptor;

    /**
     * The name of the exported memory of this module, if any.
     */
    @CompilationFinal private String exportedMemory;

    SymbolTable(WasmModule module) {
        this.module = module;
        this.typeData = new int[INITIAL_DATA_SIZE];
        this.offsets = new int[INITIAL_OFFSET_SIZE];
        this.typeDataSize = 0;
        this.offsetsSize = 0;
        this.functions = new WasmFunction[INITIAL_FUNCTION_TYPES_SIZE];
        this.numFunctions = 0;
        this.importedFunctions = new ArrayList<>();
        this.exportedFunctions = new LinkedHashMap<>();
        this.exportedFunctionsByIndex = new HashMap<>();
        this.startFunctionIndex = -1;
        this.globalAddresses = new int[INITIAL_GLOBALS_SIZE];
        this.globalTypes = new int[INITIAL_GLOBALS_SIZE];
        this.unresolvedGlobals = new LongArrayList();
        this.importedGlobals = new LinkedHashMap<>();
        this.exportedGlobals = new LinkedHashMap<>();
        this.maxGlobalIndex = -1;
        this.tableIndex = UNINITIALIZED_TABLE_BIT;
        this.importedTableDescriptor = null;
        this.exportedTable = null;
        this.memory = null;
        this.importedMemoryDescriptor = null;
        this.exportedMemory = null;
    }

    private void checkNotLinked() {
        // The symbol table must be read-only after the module gets linked.
        if (module.isLinked()) {
            throw new WasmException("The engine tried to modify the symbol table after parsing.");
        }
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
     * Ensure that the {@link #typeData} array has enough space to store {@code index}. If there is
     * no enough space, then a reallocation of the array takes place, doubling its capacity.
     *
     * No synchronisation is required for this method, as it is only called during parsing, which is
     * carried out by a single thread.
     */
    private void ensureTypeDataCapacity(int index) {
        if (typeData.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * typeData.length);
            typeData = reallocate(typeData, typeDataSize, newLength);
        }
    }

    /**
     * Ensure that the {@link #offsets} array has enough space to store {@code index}. If there is
     * no enough space, then a reallocation of the array takes place, doubling its capacity.
     *
     * No synchronisation is required for this method, as it is only called during parsing, which is
     * carried out by a single thread.
     */
    private void ensureOffsetsCapacity(int index) {
        if (offsets.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * offsets.length);
            offsets = reallocate(offsets, offsetsSize, newLength);
        }
    }

    int allocateFunctionType(int numParameterTypes, int numReturnTypes) {
        checkNotLinked();
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
        checkNotLinked();
        final int typeIdx = allocateFunctionType(parameterTypes.length, returnTypes.length);
        for (int i = 0; i < parameterTypes.length; i++) {
            registerFunctionTypeParameterType(typeIdx, i, parameterTypes[i]);
        }
        for (int i = 0; i < returnTypes.length; i++) {
            registerFunctionTypeReturnType(typeIdx, i, returnTypes[i]);
        }
        return typeIdx;
    }

    void registerFunctionTypeParameterType(int funcTypeIdx, int paramIdx, byte type) {
        checkNotLinked();
        int idx = 2 + offsets[funcTypeIdx] + paramIdx;
        typeData[idx] = type;
    }

    void registerFunctionTypeReturnType(int funcTypeIdx, int returnIdx, byte type) {
        checkNotLinked();
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

    WasmFunction declareFunction(int typeIndex) {
        checkNotLinked();
        final WasmFunction function = allocateFunction(typeIndex, null);
        return function;
    }

    public WasmFunction declareExportedFunction(int typeIndex, String exportedName) {
        checkNotLinked();
        final WasmFunction function = declareFunction(typeIndex);
        exportFunction(exportedName, function.index());
        return function;
    }

    public String exportedFunctionName(int index) {
        return exportedFunctionsByIndex.get(index);
    }

    void setStartFunction(int functionIndex) {
        checkNotLinked();
        this.startFunctionIndex = functionIndex;
    }

    int numFunctions() {
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

    public int functionTypeArgumentCount(int typeIndex) {
        int typeOffset = offsets[typeIndex];
        int numArgs = typeData[typeOffset + 0];
        return numArgs;
    }

    public byte functionTypeReturnType(int typeIndex) {
        int typeOffset = offsets[typeIndex];
        int numArgTypes = typeData[typeOffset + 0];
        int numReturnTypes = typeData[typeOffset + 1];
        return numReturnTypes == 0 ? (byte) 0x40 : (byte) typeData[typeOffset + 2 + numArgTypes];
    }

    int functionTypeReturnTypeLength(int typeIndex) {
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

    public byte functionTypeArgumentTypeAt(int typeIndex, int i) {
        int typeOffset = offsets[typeIndex];
        return (byte) typeData[typeOffset + 2 + i];
    }

    public byte functionTypeReturnTypeAt(int typeIndex, int i) {
        int typeOffset = offsets[typeIndex];
        int numArgs = typeData[typeOffset];
        return (byte) typeData[typeOffset + 2 + numArgs + i];
    }

    ByteArrayList functionTypeArgumentTypes(int typeIndex) {
        ByteArrayList types = new ByteArrayList();
        for (int i = 0; i != functionTypeArgumentCount(typeIndex); ++i) {
            types.add(functionTypeArgumentTypeAt(typeIndex, i));
        }
        return types;
    }

    void exportFunction(String exportName, int functionIndex) {
        checkNotLinked();
        exportedFunctions.put(exportName, functions[functionIndex]);
        exportedFunctionsByIndex.put(functionIndex, exportName);
    }

    Map<String, WasmFunction> exportedFunctions() {
        return exportedFunctions;
    }

    WasmFunction importFunction(String moduleName, String functionName, int typeIndex) {
        checkNotLinked();
        WasmFunction function = allocateFunction(typeIndex, new ImportDescriptor(moduleName, functionName));
        importedFunctions.add(function);
        return function;
    }

    List<WasmFunction> importedFunctions() {
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

    /**
     * Allocates a global index in the symbol table, for a global variable that was already
     * allocated.
     */
    private void allocateGlobal(int index, int valueType, int mutability, GlobalResolution resolution, int address) {
        assert (valueType & 0xff) == valueType;
        assert (mutability & 0xff) == mutability;
        checkNotLinked();
        ensureGlobalsCapacity(index);
        maxGlobalIndex = Math.max(maxGlobalIndex, index);
        globalAddresses[index] = address;
        int globalType = (resolution.ordinal() << 16) | ((mutability << 8) | valueType);
        globalTypes[index] = globalType;
    }

    int declareGlobal(WasmContext context, int index, int valueType, int mutability, GlobalResolution resolution) {
        assert !resolution.isImported();
        final Globals globals = context.globals();
        final int address = globals.allocateGlobal();
        allocateGlobal(index, valueType, mutability, resolution, address);
        return address;
    }

    void importGlobal(String moduleName, String globalName, int index, int valueType, int mutability, GlobalResolution resolution, int address) {
        assert resolution.isImported();
        importedGlobals.put(index, new ImportDescriptor(moduleName, globalName));
        allocateGlobal(index, valueType, mutability, resolution, address);
    }

    LinkedHashMap<Integer, ImportDescriptor> importedGlobals() {
        return importedGlobals;
    }

    public int maxGlobalIndex() {
        return maxGlobalIndex;
    }

    public int globalAddress(int index) {
        return globalAddresses[index];
    }

    boolean globalExported(int index) {
        final int exportStatus = globalTypes[index] & GLOBAL_EXPORT_BIT;
        return exportStatus != 0;
    }

    public GlobalResolution globalResolution(int index) {
        final int resolutionValue = (globalTypes[index] >>> 16) & 0xff;
        return GlobalResolution.VALUES[resolutionValue];
    }

    byte globalMutability(int index) {
        return (byte) ((globalTypes[index] >>> 8) & 0xff);
    }

    public byte globalValueType(int index) {
        return (byte) (globalTypes[index] & 0xff);
    }

    private void addUnresolvedGlobal(long unresolvedEntry) {
        checkNotLinked();
        unresolvedGlobals.add(unresolvedEntry);
    }

    /**
     * Tracks an unresolved imported global. The global must have been previously allocated.
     */
    public void trackUnresolvedGlobal(int globalIndex) {
        checkNotLinked();
        assertGlobalAllocated(globalIndex);
        addUnresolvedGlobal(globalIndex);
    }

    /**
     * Tracks an unresolved declared global, which depends on an unresolved imported global. The
     * global must have been previously allocated.
     */
    void trackUnresolvedGlobal(int globalIndex, int dependentGlobal) {
        checkNotLinked();
        assertGlobalAllocated(globalIndex);
        long encoding = ((long) dependentGlobal << 32) | globalIndex;
        addUnresolvedGlobal(encoding);
    }

    private void assertGlobalAllocated(int globalIndex) {
        if (globalIndex >= maxGlobalIndex || globalTypes[globalIndex] == 0) {
            throw new RuntimeException("Cannot track non-allocated global: " + globalIndex);
        }
    }

    Map<String, Integer> exportedGlobals() {
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

    void exportGlobal(String name, int index) {
        checkNotLinked();
        if (globalExported(index)) {
            throw new WasmMemoryException("Global " + index + " already exported under the name: " + nameOfExportedGlobal(index));
        }
        globalTypes[index] |= GLOBAL_EXPORT_BIT;
        // TODO: Invoke Linker to link together any modules with pending unresolved globals.
        exportedGlobals.put(name, index);
    }

    public int declareExportedGlobal(WasmContext context, String name, int index, int valueType, int mutability, GlobalResolution resolution) {
        checkNotLinked();
        int address = declareGlobal(context, index, valueType, mutability, resolution);
        exportGlobal(name, index);
        return address;
    }

    public void allocateTable(WasmContext context, int initSize, int maxSize) {
        checkNotLinked();
        validateSingleTable();
        tableIndex = context.tables().allocateTable(initSize, maxSize);
    }

    void importTable(WasmContext context, String moduleName, String tableName, int initSize, int maxSize) {
        checkNotLinked();
        validateSingleTable();
        context.linker().importTable(context, module, moduleName, tableName, initSize, maxSize);
    }

    private void validateSingleTable() {
        if (importedTableDescriptor != null) {
            throw new WasmException("A table has been already imported in the module.");
        }
        if ((tableIndex & UNINITIALIZED_TABLE_BIT) == 0) {
            throw new WasmException("A table has been already declared in the module.");
        }
    }

    boolean tableExists() {
        return importedTableDescriptor != null || (tableIndex & UNINITIALIZED_TABLE_BIT) == 0;
    }

    public void exportTable(String name) {
        checkNotLinked();
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

    int tableCount() {
        return tableExists() ? 1 : 0;
    }

    void setTableIndex(int i) {
        checkNotLinked();
        tableIndex = i;
    }

    public ImportDescriptor importedTable() {
        return importedTableDescriptor;
    }

    void setImportedTable(ImportDescriptor descriptor) {
        importedTableDescriptor = descriptor;
    }

    String exportedTable() {
        return exportedTable;
    }

    void initializeTableWithFunctions(WasmContext context, int offset, int[] contents) {
        checkNotLinked();
        context.tables().ensureSizeAtLeast(tableIndex, offset + contents.length);
        final Object[] table = context.tables().table(tableIndex);
        for (int i = 0; i < contents.length; i++) {
            final int functionIndex = contents[i];
            final WasmFunction function = function(functionIndex);
            table[offset + i] = function;
        }
    }

    public WasmMemory allocateMemory(WasmContext context, int initSize, int maxSize) {
        checkNotLinked();
        validateSingleMemory();
        memory = new UnsafeWasmMemory(initSize, maxSize);
        context.memories().allocateMemory(memory);
        return memory;
    }

    void importMemory(WasmContext context, String moduleName, String memoryName, int initSize, int maxSize) {
        checkNotLinked();
        validateSingleMemory();
        importedMemoryDescriptor = new ImportDescriptor(moduleName, memoryName);
        memory = context.linker().tryResolveMemory(context, module, moduleName, memoryName, initSize, maxSize);
    }

    private void validateSingleMemory() {
        if (importedMemoryDescriptor != null) {
            throw new WasmException("Memory has been already imported in the module.");
        }
        if (memory != null) {
            throw new WasmException("Memory has been already declared in the module.");
        }
    }

    boolean memoryExists() {
        return importedMemoryDescriptor != null || memory != null;
    }

    public void exportMemory(String name) {
        checkNotLinked();
        if (exportedMemory != null) {
            throw new WasmException("A memory has been already exported from this module.");
        }
        if (!memoryExists()) {
            throw new WasmException("No memory has been declared or imported, so memory cannot be exported.");
        }
        exportedMemory = name;
    }

    public WasmMemory memory() {
        return memory;
    }

    public int memoryCount() {
        return memoryExists() ? 1 : 0;
    }

    ImportDescriptor importedMemory() {
        return importedMemoryDescriptor;
    }

    String exportedMemory() {
        return exportedMemory;
    }
}
