/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.Assert.assertByteEqual;
import static org.graalvm.wasm.Assert.assertIntEqual;
import static org.graalvm.wasm.Assert.assertTrue;
import static org.graalvm.wasm.Assert.assertUnsignedIntLess;
import static org.graalvm.wasm.WasmMath.maxUnsigned;
import static org.graalvm.wasm.WasmMath.minUnsigned;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.constants.ImportIdentifier;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryFactory;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Contains the symbol information of a module.
 */
public abstract class SymbolTable {
    private static final int INITIAL_GLOBALS_SIZE = 64;
    private static final int INITIAL_GLOBALS_BYTECODE_SIZE = INITIAL_GLOBALS_SIZE / 4;
    private static final int INITIAL_TABLE_SIZE = 1;
    private static final int INITIAL_MEMORY_SIZE = 1;
    private static final int INITIAL_DATA_SIZE = 512;
    private static final int INITIAL_TYPE_SIZE = 128;
    private static final int INITIAL_FUNCTION_TYPES_SIZE = 128;
    private static final byte GLOBAL_MUTABLE_BIT = 0x01;
    private static final byte GLOBAL_EXPORT_BIT = 0x02;
    private static final byte GLOBAL_INITIALIZED_BIT = 0x04;
    private static final byte GLOBAL_IMPORTED_BIT = 0x10;
    private static final byte GLOBAL_FUNCTION_INITIALIZER_BIT = 0x20;

    public static final int UNINITIALIZED_ADDRESS = Integer.MIN_VALUE;
    private static final int NO_EQUIVALENCE_CLASS = 0;
    static final int FIRST_EQUIVALENCE_CLASS = NO_EQUIVALENCE_CLASS + 1;

    public static class FunctionType {
        private final byte[] paramTypes;
        private final byte[] resultTypes;
        private final int hashCode;

        FunctionType(byte[] paramTypes, byte[] resultTypes) {
            this.paramTypes = paramTypes;
            this.resultTypes = resultTypes;
            this.hashCode = Arrays.hashCode(paramTypes) ^ Arrays.hashCode(resultTypes);
        }

        public byte[] paramTypes() {
            return paramTypes;
        }

        public byte[] resultTypes() {
            return resultTypes;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof FunctionType)) {
                return false;
            }
            FunctionType that = (FunctionType) object;
            if (this.paramTypes.length != that.paramTypes.length) {
                return false;
            }
            for (int i = 0; i < this.paramTypes.length; i++) {
                if (this.paramTypes[i] != that.paramTypes[i]) {
                    return false;
                }
            }
            if (this.resultTypes.length != that.resultTypes.length) {
                return false;
            }
            for (int i = 0; i < this.resultTypes.length; i++) {
                if (this.resultTypes[i] != that.resultTypes[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            String[] paramNames = new String[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                paramNames[i] = WasmType.toString(paramTypes[i]);
            }
            String[] resultNames = new String[resultTypes.length];
            for (int i = 0; i < resultTypes.length; i++) {
                resultNames[i] = WasmType.toString(resultTypes[i]);
            }
            return Arrays.toString(paramNames) + " -> " + Arrays.toString(resultNames);
        }
    }

    public static class TableInfo {
        /**
         * Lower bound on table size.
         */
        public final int initialSize;

        /**
         * Upper bound on table size.
         * <p>
         * <em>Note:</em> this is the upper bound defined by the module. A table instance might have
         * a lower internal max allowed size in practice.
         */
        public final int maximumSize;

        /**
         * The element type of the table.
         */
        public final byte elemType;

        public TableInfo(int initialSize, int maximumSize, byte elemType) {
            this.initialSize = initialSize;
            this.maximumSize = maximumSize;
            this.elemType = elemType;
        }
    }

    public static class MemoryInfo {
        /**
         * Lower bound on memory size.
         */
        public final long initialSize;

        /**
         * Upper bound on memory size.
         * <p>
         * <em>Note:</em> this is the upper bound defined by the module. A memory instance might
         * have a lower internal max allowed size in practice.
         */
        public final long maximumSize;

        /**
         * If the memory uses index type 64.
         */
        public final boolean indexType64;

        /**
         * Whether the memory is shared (modifications are visible to other threads).
         */
        public final boolean shared;

        public final Class<? extends WasmMemory> memoryImpl;

        public MemoryInfo(long initialSize, long maximumSize, boolean indexType64, boolean shared, Class<? extends WasmMemory> memoryImpl) {
            this.initialSize = initialSize;
            this.maximumSize = maximumSize;
            this.indexType64 = indexType64;
            this.shared = shared;
            this.memoryImpl = Objects.requireNonNull(memoryImpl);
        }
    }

    /**
     * Encodes the parameter and result types of each function type.
     * <p>
     * Given a function type index, the {@link #typeOffsets} array indicates where the encoding for
     * that function type begins in this array.
     * <p>
     * For a function type starting at index i, the encoding is the following
     * <p>
     * <code>
     *   i     i+1   i+2+0        i+2+na-1  i+2+na+0        i+2+na+nr-1
     * +-----+-----+-------+-----+--------+----------+-----+-----------+
     * | na  |  nr | par 1 | ... | par na | result 1 | ... | result nr |
     * +-----+-----+-------+-----+--------+----------+-----+-----------+
     * </code>
     * <p>
     * where `na` is the number of parameters, and `nr` is the number of result values.
     * <p>
     * This array is monotonically populated from left to right during parsing. Any code that uses
     * this array should only access the locations in the array that have already been populated.
     */
    @CompilationFinal(dimensions = 1) private int[] typeData;

    /**
     * Stores the offset of each function type into the {@link #typeData} array.
     * <p>
     * This array is monotonically populated from left to right during parsing. Any code that uses
     * this array should only access the locations in the array that have already been populated.
     */
    @CompilationFinal(dimensions = 1) private int[] typeOffsets;

    /**
     * Stores the type equivalence class.
     * <p>
     * Since multiple types have the same shape, each type is mapped to an equivalence class, so
     * that two types can be quickly compared.
     * <p>
     * The equivalence classes are computed globally for all the modules, during linking.
     */
    @CompilationFinal(dimensions = 1) private int[] typeEquivalenceClasses;

    @CompilationFinal private int typeDataSize;
    @CompilationFinal private int typeCount;

    /**
     * List of the descriptors of all the imported symbols.
     */
    private final List<ImportDescriptor> importedSymbols;

    /**
     * List of the names of all the exported symbols.
     */
    private final List<String> exportedSymbols;

    /**
     * Stores the function objects for a WebAssembly module.
     * <p>
     * This array is monotonically populated from left to right during parsing. Any code that uses
     * this array should only access the locations in the array that have already been populated.
     */
    @CompilationFinal(dimensions = 1) private WasmFunction[] functions;
    @CompilationFinal private int numFunctions;

    /**
     * List of all imported functions.
     */
    private final List<WasmFunction> importedFunctions;

    @CompilationFinal private int numImportedFunctions;

    /**
     * Map from exported function names to respective functions.
     */
    private final EconomicMap<String, WasmFunction> exportedFunctions;

    /**
     * Map from function indices to the exported names of respective functions.
     */
    private final EconomicMap<Integer, String> exportedFunctionsByIndex;

    /**
     * Index of the start function if it exists, or -1 otherwise.
     */
    @CompilationFinal private int startFunctionIndex;

    /**
     * A global type is the value type of the global, followed by its mutability. This is encoded as
     * two bytes -- the lowest (0th) byte is the value type. The 1st byte is organized like this:
     * <p>
     * <code>
     * | . | . | . | functionOrIndex flag | reference flag | initialized flag | exported flag | mutable flag |
     * </code>
     */
    @CompilationFinal(dimensions = 1) private byte[] globalTypes;

    /**
     * The values or indices used for initializing globals.
     */
    @CompilationFinal(dimensions = 1) private Object[] globalInitializers;

    /**
     * The bytecodes used for initializing globals.
     */
    @CompilationFinal(dimensions = 2) private byte[][] globalInitializersBytecode;

    /**
     * A mapping between the indices of the imported globals and their import specifiers.
     */
    @CompilationFinal private final EconomicMap<Integer, ImportDescriptor> importedGlobals;

    /**
     * A mapping between the names and the indices of the exported globals.
     */
    @CompilationFinal private final EconomicMap<String, Integer> exportedGlobals;

    /**
     * Number of globals in the module.
     */
    @CompilationFinal private int numGlobals;

    /**
     * Number of globals that need a bytecode initializer.
     */
    @CompilationFinal private int numGlobalInitializersBytecode;

    /**
     * The descriptor of the table of this module.
     * <p>
     * In the current WebAssembly specification, a module can use at most one table. The value
     * {@code null} denotes that this module uses no table.
     */
    @CompilationFinal(dimensions = 1) private TableInfo[] tables;

    @CompilationFinal private int tableCount;

    /**
     * The table used in this module.
     */
    @CompilationFinal private final EconomicMap<Integer, ImportDescriptor> importedTables;

    /**
     * The name(s) of the exported table of this module, if any.
     */
    @CompilationFinal private final EconomicMap<String, Integer> exportedTables;

    /**
     * The descriptors of the memory of this module.
     */
    @CompilationFinal(dimensions = 1) private MemoryInfo[] memories;

    @CompilationFinal private int memoryCount;

    /**
     * The memory used in this module.
     */
    @CompilationFinal private final EconomicMap<Integer, ImportDescriptor> importedMemories;

    /**
     * The name(s) of the exported memory of this module, if any.
     */
    @CompilationFinal private final EconomicMap<String, Integer> exportedMemories;

    /**
     * List of all custom sections.
     */
    private final List<WasmCustomSection> customSections;

    @CompilationFinal private int elemSegmentCount;

    /**
     * The offsets of the data instances in the bytecode.
     */
    @CompilationFinal(dimensions = 1) private int[] dataInstances;

    /**
     * The offsets of the elem instances in the bytecode.
     */
    @CompilationFinal(dimensions = 1) private long[] elemInstances;

    /**
     * The offset of the code entries in the bytecode.
     */
    @CompilationFinal(dimensions = 1) private int[] codeEntries;
    @CompilationFinal private boolean dataCountExists;
    @CompilationFinal private int dataSegmentCount;

    /**
     * Offset representing dropped data instances.
     */
    @CompilationFinal private int droppedDataInstanceOffset;

    @CompilationFinal private int codeEntryCount;

    /**
     * All function indices that can be references via
     * {@link org.graalvm.wasm.constants.Instructions#REF_FUNC}.
     */
    @CompilationFinal private EconomicSet<Integer> functionReferences;

    SymbolTable() {
        CompilerAsserts.neverPartOfCompilation();
        this.typeData = new int[INITIAL_DATA_SIZE];
        this.typeOffsets = new int[INITIAL_TYPE_SIZE];
        this.typeEquivalenceClasses = new int[INITIAL_TYPE_SIZE];
        this.typeDataSize = 0;
        this.typeCount = 0;
        this.importedSymbols = new ArrayList<>();
        this.exportedSymbols = new ArrayList<>();
        this.functions = new WasmFunction[INITIAL_FUNCTION_TYPES_SIZE];
        this.numFunctions = 0;
        this.importedFunctions = new ArrayList<>();
        this.numImportedFunctions = 0;
        this.exportedFunctions = EconomicMap.create();
        this.exportedFunctionsByIndex = EconomicMap.create();
        this.startFunctionIndex = -1;
        this.globalTypes = new byte[2 * INITIAL_GLOBALS_SIZE];
        this.globalInitializers = new Object[INITIAL_GLOBALS_SIZE];
        this.globalInitializersBytecode = new byte[INITIAL_GLOBALS_BYTECODE_SIZE][];
        this.importedGlobals = EconomicMap.create();
        this.exportedGlobals = EconomicMap.create();
        this.numGlobals = 0;
        this.tables = new TableInfo[INITIAL_TABLE_SIZE];
        this.tableCount = 0;
        this.importedTables = EconomicMap.create();
        this.exportedTables = EconomicMap.create();
        this.memories = new MemoryInfo[INITIAL_MEMORY_SIZE];
        this.memoryCount = 0;
        this.importedMemories = EconomicMap.create();
        this.exportedMemories = EconomicMap.create();
        this.customSections = new ArrayList<>();
        this.elemSegmentCount = 0;
        this.dataCountExists = false;
        this.dataSegmentCount = 0;
        this.functionReferences = EconomicSet.create();
        this.dataInstances = null;
    }

    private void checkNotParsed() {
        CompilerAsserts.neverPartOfCompilation();
        // The symbol table must be read-only after the module gets linked.
        if (module().isParsed()) {
            throw CompilerDirectives.shouldNotReachHere("The engine tried to modify the symbol table after parsing.");
        }
    }

    private void checkUniqueExport(String name) {
        CompilerAsserts.neverPartOfCompilation();
        if (exportedFunctions.containsKey(name) || exportedGlobals.containsKey(name) || exportedMemories.containsKey(name) || exportedTables.containsKey(name)) {
            throw WasmException.create(Failure.DUPLICATE_EXPORT, "All export names must be different, but '" + name + "' is exported twice.");
        }
    }

    public void checkFunctionIndex(int funcIndex) {
        assertUnsignedIntLess(funcIndex, numFunctions, Failure.UNKNOWN_FUNCTION);
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
     * <p>
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
     * Ensure that the {@link #typeOffsets} and {@link #typeEquivalenceClasses} arrays have enough
     * space to store the data for the type at {@code index}. If there is not enough space, then a
     * reallocation of the array takes place, doubling its capacity.
     * <p>
     * No synchronisation is required for this method, as it is only called during parsing, which is
     * carried out by a single thread.
     */
    private void ensureTypeCapacity(int index) {
        if (typeOffsets.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * typeOffsets.length);
            typeOffsets = reallocate(typeOffsets, typeCount, newLength);
            typeEquivalenceClasses = reallocate(typeEquivalenceClasses, typeCount, newLength);
        }
    }

    int allocateFunctionType(int paramCount, int resultCount, boolean isMultiValue) {
        checkNotParsed();
        ensureTypeCapacity(typeCount);
        int typeIdx = typeCount++;
        typeOffsets[typeIdx] = typeDataSize;

        if (!isMultiValue && resultCount != 0 && resultCount != 1) {
            throw WasmException.create(Failure.INVALID_RESULT_ARITY, "A function can return at most one result.");
        }

        int size = 2 + paramCount + resultCount;
        ensureTypeDataCapacity(typeDataSize + size);
        typeData[typeDataSize + 0] = paramCount;
        typeData[typeDataSize + 1] = resultCount;
        typeDataSize += size;
        return typeIdx;
    }

    public int allocateFunctionType(byte[] paramTypes, byte[] resultTypes, boolean isMultiValue) {
        checkNotParsed();
        final int typeIdx = allocateFunctionType(paramTypes.length, resultTypes.length, isMultiValue);
        for (int i = 0; i < paramTypes.length; i++) {
            registerFunctionTypeParameterType(typeIdx, i, paramTypes[i]);
        }
        for (int i = 0; i < resultTypes.length; i++) {
            registerFunctionTypeResultType(typeIdx, i, resultTypes[i]);
        }
        return typeIdx;
    }

    void registerFunctionTypeParameterType(int funcTypeIdx, int paramIdx, byte type) {
        checkNotParsed();
        int idx = 2 + typeOffsets[funcTypeIdx] + paramIdx;
        typeData[idx] = type;
    }

    void registerFunctionTypeResultType(int funcTypeIdx, int resultIdx, byte type) {
        checkNotParsed();
        int idx = 2 + typeOffsets[funcTypeIdx] + typeData[typeOffsets[funcTypeIdx]] + resultIdx;
        typeData[idx] = type;
    }

    public int equivalenceClass(int typeIndex) {
        return typeEquivalenceClasses[typeIndex];
    }

    void setEquivalenceClass(int index, int eqClass) {
        checkNotParsed();
        if (typeEquivalenceClasses[index] != NO_EQUIVALENCE_CLASS) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Type at index " + index + " already has an equivalence class.");
        }
        typeEquivalenceClasses[index] = eqClass;
    }

    private void ensureFunctionsCapacity(int index) {
        if (functions.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * functions.length);
            functions = reallocate(functions, numFunctions, newLength);
        }
    }

    private WasmFunction allocateFunction(int typeIndex, ImportDescriptor importDescriptor) {
        checkNotParsed();
        ensureFunctionsCapacity(numFunctions);
        assertUnsignedIntLess(typeIndex, typeCount(), Failure.UNKNOWN_TYPE);
        final WasmFunction function = new WasmFunction(this, numFunctions, typeIndex, importDescriptor);
        functions[numFunctions] = function;
        numFunctions++;
        return function;
    }

    WasmFunction declareFunction(int typeIndex) {
        checkNotParsed();
        return allocateFunction(typeIndex, null);
    }

    public WasmFunction declareExportedFunction(int typeIndex, String exportedName) {
        checkNotParsed();
        final WasmFunction function = declareFunction(typeIndex);
        exportFunction(function.index(), exportedName);
        return function;
    }

    String exportedFunctionName(int index) {
        return exportedFunctionsByIndex.get(index);
    }

    void setStartFunction(int functionIndex) {
        checkNotParsed();
        WasmFunction start = function(functionIndex);
        if (start.paramCount() != 0) {
            throw WasmException.create(Failure.START_FUNCTION_PARAMS, "Start function cannot take parameters.");
        }
        if (start.resultCount() != 0) {
            throw WasmException.create(Failure.START_FUNCTION_RESULT_VALUE, "Start function cannot return a value.");
        }
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
        return exportedFunctions.get(exportName);
    }

    public int functionTypeParamCount(int typeIndex) {
        int typeOffset = typeOffsets[typeIndex];
        return typeData[typeOffset + 0];
    }

    public int functionTypeResultCount(int typeIndex) {
        int typeOffset = typeOffsets[typeIndex];
        return typeData[typeOffset + 1];
    }

    public WasmFunction startFunction() {
        if (startFunctionIndex == -1) {
            return null;
        }
        return function(startFunctionIndex);
    }

    protected abstract WasmModule module();

    public byte functionTypeParamTypeAt(int typeIndex, int i) {
        int typeOffset = typeOffsets[typeIndex];
        return (byte) typeData[typeOffset + 2 + i];
    }

    public byte functionTypeResultTypeAt(int typeIndex, int resultIndex) {
        int typeOffset = typeOffsets[typeIndex];
        int paramCount = typeData[typeOffset];
        return (byte) typeData[typeOffset + 2 + paramCount + resultIndex];
    }

    private byte[] functionTypeParamTypesAsArray(int typeIndex) {
        int paramCount = functionTypeParamCount(typeIndex);
        byte[] paramTypes = new byte[paramCount];
        for (int i = 0; i < paramCount; ++i) {
            paramTypes[i] = functionTypeParamTypeAt(typeIndex, i);
        }
        return paramTypes;
    }

    private byte[] functionTypeResultTypesAsArray(int typeIndex) {
        int resultTypeCount = functionTypeResultCount(typeIndex);
        byte[] resultTypes = new byte[resultTypeCount];
        for (int i = 0; i < resultTypeCount; i++) {
            resultTypes[i] = functionTypeResultTypeAt(typeIndex, i);
        }
        return resultTypes;
    }

    int typeCount() {
        return typeCount;
    }

    public FunctionType typeAt(int index) {
        return new FunctionType(functionTypeParamTypesAsArray(index), functionTypeResultTypesAsArray(index));
    }

    public void importSymbol(ImportDescriptor descriptor) {
        checkNotParsed();
        assert importedSymbols.size() == descriptor.importedSymbolIndex;
        importedSymbols.add(descriptor);
    }

    public List<ImportDescriptor> importedSymbols() {
        return importedSymbols;
    }

    public int numImportedSymbols() {
        return importedSymbols.size();
    }

    protected void exportSymbol(String name) {
        checkNotParsed();
        checkUniqueExport(name);
        exportedSymbols.add(name);
    }

    public List<String> exportedSymbols() {
        return exportedSymbols;
    }

    public void exportFunction(int functionIndex, String exportName) {
        checkNotParsed();
        exportSymbol(exportName);
        exportedFunctions.put(exportName, functions[functionIndex]);
        exportedFunctionsByIndex.put(functionIndex, exportName);
        module().addLinkAction((context, instance, imports) -> {
            context.linker().resolveFunctionExport(module(), functionIndex, exportName);
        });
    }

    public EconomicMap<String, WasmFunction> exportedFunctions() {
        return exportedFunctions;
    }

    public WasmFunction importFunction(String moduleName, String functionName, int typeIndex) {
        checkNotParsed();
        final ImportDescriptor descriptor = new ImportDescriptor(moduleName, functionName, ImportIdentifier.FUNCTION, numFunctions, numImportedSymbols());
        importSymbol(descriptor);
        WasmFunction function = allocateFunction(typeIndex, descriptor);
        assert function.index() == descriptor.index;
        importedFunctions.add(function);
        numImportedFunctions++;
        module().addLinkAction((context, instance, imports) -> {
            context.linker().resolveFunctionImport(context, instance, function, imports);
        });
        return function;
    }

    public List<WasmFunction> importedFunctions() {
        return importedFunctions;
    }

    public int numImportedFunctions() {
        return numImportedFunctions;
    }

    public WasmFunction importedFunction(ImportDescriptor descriptor) {
        return functions[descriptor.index];
    }

    private void ensureGlobalsCapacity(int index) {
        while (index >= globalInitializers.length) {
            final byte[] nGlobalTypes = new byte[globalTypes.length * 2];
            final Object[] nGlobalInitializers = new Object[globalInitializers.length * 2];
            System.arraycopy(globalTypes, 0, nGlobalTypes, 0, globalTypes.length);
            System.arraycopy(globalInitializers, 0, nGlobalInitializers, 0, globalInitializers.length);
            globalTypes = nGlobalTypes;
            globalInitializers = nGlobalInitializers;
        }
    }

    private void ensureGlobalInitializersBytecodeCapacity(int index) {
        while (index >= globalInitializersBytecode.length) {
            final byte[][] nGlobalInitializersBytecode = new byte[globalInitializersBytecode.length * 2][];
            System.arraycopy(globalInitializersBytecode, 0, nGlobalInitializersBytecode, 0, globalInitializersBytecode.length);
            globalInitializersBytecode = nGlobalInitializersBytecode;
        }
    }

    /**
     * Allocates a global index in the symbol table, for a global variable that was already
     * allocated.
     */
    void allocateGlobal(int index, byte valueType, byte mutability, boolean initialized, boolean imported, byte[] initBytecode, Object initialValue) {
        assert (valueType & 0xff) == valueType;
        checkNotParsed();
        ensureGlobalsCapacity(index);
        numGlobals = maxUnsigned(index + 1, numGlobals);
        byte flags;
        if (mutability == GlobalModifier.CONSTANT) {
            flags = 0;
        } else if (mutability == GlobalModifier.MUTABLE) {
            flags = GLOBAL_MUTABLE_BIT;
        } else {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Invalid mutability: " + mutability);
        }
        if (initialized) {
            flags |= GLOBAL_INITIALIZED_BIT;
        }
        if (imported) {
            flags |= GLOBAL_IMPORTED_BIT;
        }
        if (initBytecode == null) {
            flags |= GLOBAL_FUNCTION_INITIALIZER_BIT;
            globalInitializers[index] = initialValue;
        } else {
            int initBytecodeIndex = numGlobalInitializersBytecode++;
            ensureGlobalInitializersBytecodeCapacity(initBytecodeIndex);
            globalInitializersBytecode[initBytecodeIndex] = initBytecode;
            globalInitializers[index] = initBytecodeIndex;
        }
        globalTypes[2 * index] = valueType;
        globalTypes[2 * index + 1] = flags;
    }

    void declareGlobal(int index, byte valueType, byte mutability, boolean initialized, byte[] initBytecode, Object initialValue) {
        allocateGlobal(index, valueType, mutability, initialized, false, initBytecode, initialValue);
        module().addLinkAction((context, instance, imports) -> {
            final int address = context.globals().allocateGlobal();
            instance.setGlobalAddress(index, address);
        });
    }

    void importGlobal(String moduleName, String globalName, int index, byte valueType, byte mutability) {
        final ImportDescriptor descriptor = new ImportDescriptor(moduleName, globalName, ImportIdentifier.GLOBAL, index, numImportedSymbols());
        importedGlobals.put(index, descriptor);
        importSymbol(descriptor);
        allocateGlobal(index, valueType, mutability, false, true, null, null);
        module().addLinkAction((context, instance, imports) -> {
            instance.setGlobalAddress(index, UNINITIALIZED_ADDRESS);
        });
        module().addLinkAction((context, instance, imports) -> {
            context.linker().resolveGlobalImport(context, instance, descriptor, index, valueType, mutability, imports);
        });
    }

    public EconomicMap<Integer, ImportDescriptor> importedGlobals() {
        return importedGlobals;
    }

    public EconomicMap<ImportDescriptor, Integer> importedGlobalDescriptors() {
        final EconomicMap<ImportDescriptor, Integer> reverseMap = EconomicMap.create();
        MapCursor<Integer, ImportDescriptor> cursor = importedGlobals.getEntries();
        while (cursor.advance()) {
            reverseMap.put(cursor.getValue(), cursor.getKey());
        }
        return reverseMap;
    }

    public int numGlobals() {
        return numGlobals;
    }

    public byte globalMutability(int index) {
        if ((globalTypes[2 * index + 1] & GLOBAL_MUTABLE_BIT) != 0) {
            return GlobalModifier.MUTABLE;
        } else {
            return GlobalModifier.CONSTANT;
        }
    }

    public boolean isGlobalMutable(int index) {
        return globalMutability(index) == GlobalModifier.MUTABLE;
    }

    public byte globalValueType(int index) {
        return globalTypes[2 * index];
    }

    public boolean globalInitialized(int index) {
        return (globalTypes[2 * index + 1] & GLOBAL_INITIALIZED_BIT) != 0;
    }

    public byte[] globalInitializerBytecode(int index) {
        if ((globalTypes[2 * index + 1] & GLOBAL_FUNCTION_INITIALIZER_BIT) != 0) {
            return null;
        } else {
            return globalInitializersBytecode[(int) globalInitializers[index]];
        }
    }

    public Object globalInitialValue(int index) {
        if ((globalTypes[2 * index + 1] & GLOBAL_FUNCTION_INITIALIZER_BIT) != 0) {
            return globalInitializers[index];
        } else {
            return 0;
        }
    }

    public boolean globalImported(int index) {
        return (globalTypes[2 * index + 1] & GLOBAL_IMPORTED_BIT) != 0;
    }

    public EconomicMap<String, Integer> exportedGlobals() {
        return exportedGlobals;
    }

    void exportGlobal(String name, int index) {
        checkNotParsed();
        exportSymbol(name);
        globalTypes[2 * index + 1] |= GLOBAL_EXPORT_BIT;
        exportedGlobals.put(name, index);
        module().addLinkAction((context, instance, imports) -> {
            context.linker().resolveGlobalExport(instance.module(), name, index);
        });
    }

    public void declareExportedGlobalWithValue(String name, int index, byte valueType, byte mutability, Object value) {
        checkNotParsed();
        declareGlobal(index, valueType, mutability, true, null, value);
        exportGlobal(name, index);
        module().addLinkAction((context, instance, imports) -> context.globals().store(valueType, instance.globalAddress(index), value));
    }

    private void ensureTableCapacity(int index) {
        if (index >= tables.length) {
            final TableInfo[] nTables = new TableInfo[Math.max(Integer.highestOneBit(index) << 1, 2 * tables.length)];
            System.arraycopy(tables, 0, nTables, 0, tables.length);
            tables = nTables;
        }
    }

    public void allocateTable(int index, int declaredMinSize, int declaredMaxSize, byte elemType, boolean referenceTypes) {
        checkNotParsed();
        addTable(index, declaredMinSize, declaredMaxSize, elemType, referenceTypes);
        module().addLinkAction((context, instance, imports) -> {
            final int maxAllowedSize = minUnsigned(declaredMaxSize, module().limits().tableInstanceSizeLimit());
            module().limits().checkTableInstanceSize(declaredMinSize);
            final WasmTable wasmTable;
            if (context.getContextOptions().memoryOverheadMode()) {
                // Initialize an empty table in memory overhead mode.
                wasmTable = new WasmTable(0, 0, 0, elemType);
            } else {
                wasmTable = new WasmTable(declaredMinSize, declaredMaxSize, maxAllowedSize, elemType);
            }
            final int address = context.tables().register(wasmTable);
            instance.setTableAddress(index, address);
        });
    }

    void importTable(String moduleName, String tableName, int index, int initSize, int maxSize, byte elemType, boolean referenceTypes) {
        checkNotParsed();
        addTable(index, initSize, maxSize, elemType, referenceTypes);
        final ImportDescriptor importedTable = new ImportDescriptor(moduleName, tableName, ImportIdentifier.TABLE, index, numImportedSymbols());
        importedTables.put(index, importedTable);
        importSymbol(importedTable);
        module().addLinkAction((context, instance, imports) -> {
            instance.setTableAddress(index, UNINITIALIZED_ADDRESS);
        });
        module().addLinkAction((context, instance, imports) -> {
            context.linker().resolveTableImport(context, instance, importedTable, index, initSize, maxSize, elemType, imports);
        });
    }

    void addTable(int index, int minSize, int maxSize, byte elemType, boolean referenceTypes) {
        if (!referenceTypes) {
            assertTrue(importedTables.size() == 0, "A table has already been imported in the module.", Failure.MULTIPLE_TABLES);
            assertTrue(tableCount == 0, "A table has already been declared in the module.", Failure.MULTIPLE_TABLES);
        }
        ensureTableCapacity(index);
        final TableInfo table = new TableInfo(minSize, maxSize, elemType);
        tables[index] = table;
        tableCount++;
    }

    boolean checkTableIndex(int tableIndex) {
        return Integer.compareUnsigned(tableIndex, tableCount) < 0;
    }

    public void exportTable(int tableIndex, String name) {
        checkNotParsed();
        exportSymbol(name);
        if (!checkTableIndex(tableIndex)) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "No table has been declared or imported, so a table cannot be exported.");
        }
        exportedTables.put(name, tableIndex);
        module().addLinkAction((context, instance, imports) -> {
            context.linker().resolveTableExport(module(), tableIndex, name);
        });
    }

    public int tableCount() {
        return tableCount;
    }

    public ImportDescriptor importedTable(int index) {
        return importedTables.get(index);
    }

    public EconomicMap<ImportDescriptor, Integer> importedTableDescriptors() {
        final EconomicMap<ImportDescriptor, Integer> reverseMap = EconomicMap.create();
        MapCursor<Integer, ImportDescriptor> cursor = importedTables.getEntries();
        while (cursor.advance()) {
            reverseMap.put(cursor.getValue(), cursor.getKey());
        }
        return reverseMap;
    }

    public EconomicMap<String, Integer> exportedTables() {
        return exportedTables;
    }

    public int tableInitialSize(int index) {
        final TableInfo table = tables[index];
        assert table != null;
        return table.initialSize;
    }

    public int tableMaximumSize(int index) {
        final TableInfo table = tables[index];
        assert table != null;
        return table.maximumSize;
    }

    public byte tableElementType(int index) {
        final TableInfo table = tables[index];
        assert table != null;
        return table.elemType;
    }

    private void ensureMemoryCapacity(int index) {
        if (index >= memories.length) {
            final MemoryInfo[] nMemories = new MemoryInfo[Math.max(Integer.highestOneBit(index) << 1, 2 * memories.length)];
            System.arraycopy(memories, 0, nMemories, 0, memories.length);
            memories = nMemories;
        }
    }

    private long maxAllowedSize(long declaredMaxSize, boolean indexType64) {
        return minUnsigned(declaredMaxSize, module().limits().memoryInstanceSizeLimit(indexType64));
    }

    public void allocateMemory(int index, long declaredMinSize, long declaredMaxSize, boolean indexType64, boolean shared, boolean multiMemory, boolean useUnsafeMemory) {
        checkNotParsed();
        final long maxAllowedSize = maxAllowedSize(declaredMaxSize, indexType64);
        addMemory(index, declaredMinSize, declaredMaxSize, maxAllowedSize, indexType64, shared, multiMemory, useUnsafeMemory);
        module().addLinkAction((context, instance, imports) -> {
            module().limits().checkMemoryInstanceSize(declaredMinSize, indexType64);
            final WasmMemory wasmMemory;
            if (context.getContextOptions().memoryOverheadMode()) {
                // Initialize an empty memory when in memory overhead mode.
                wasmMemory = WasmMemoryFactory.createMemory(0, 0, 0, false, false, useUnsafeMemory);
            } else {
                wasmMemory = WasmMemoryFactory.createMemory(declaredMinSize, declaredMaxSize, maxAllowedSize, indexType64, shared, useUnsafeMemory);
            }
            final int memoryAddress = context.memories().register(wasmMemory);
            final WasmMemory allocatedMemory = context.memories().memory(memoryAddress);
            instance.setMemory(index, allocatedMemory);
        });
    }

    public void importMemory(String moduleName, String memoryName, int index, long initSize, long maxSize, boolean typeIndex64, boolean shared, boolean multiMemory, boolean useUnsafeMemory) {
        checkNotParsed();
        addMemory(index, initSize, maxSize, maxAllowedSize(maxSize, typeIndex64), typeIndex64, shared, multiMemory, useUnsafeMemory);
        final ImportDescriptor importedMemory = new ImportDescriptor(moduleName, memoryName, ImportIdentifier.MEMORY, index, numImportedSymbols());
        importedMemories.put(index, importedMemory);
        importSymbol(importedMemory);
        module().addLinkAction((context, instance, imports) -> {
            context.linker().resolveMemoryImport(context, instance, importedMemory, index, initSize, maxSize, typeIndex64, shared, imports);
        });
    }

    void addMemory(int index, long minSize, long maxSize, long maxAllowedSize, boolean indexType64, boolean shared, boolean multiMemory, boolean useUnsafeMemory) {
        if (!multiMemory) {
            assertTrue(importedMemories.size() == 0, "A memory has already been imported in the module.", Failure.MULTIPLE_MEMORIES);
            assertTrue(memoryCount == 0, "A memory has already been declared in the module.", Failure.MULTIPLE_MEMORIES);
        }
        ensureMemoryCapacity(index);
        var memoryImpl = WasmMemoryFactory.getMemoryImplementation(maxAllowedSize, useUnsafeMemory);
        final MemoryInfo memory = new MemoryInfo(minSize, maxSize, indexType64, shared, memoryImpl);
        memories[index] = memory;
        memoryCount++;
    }

    boolean checkMemoryIndex(int memoryIndex) {
        return Integer.compareUnsigned(memoryIndex, memoryCount) < 0;
    }

    public void exportMemory(int memoryIndex, String name) {
        checkNotParsed();
        exportSymbol(name);
        if (!checkMemoryIndex(memoryIndex)) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "No memory with the specified index has been declared or imported, so it cannot be exported.");
        }
        exportedMemories.put(name, memoryIndex);
        module().addLinkAction((context, instance, imports) -> {
            context.linker().resolveMemoryExport(instance, memoryIndex, name);
        });
    }

    public int memoryCount() {
        return memoryCount;
    }

    public ImportDescriptor importedMemory(int index) {
        return importedMemories.get(index);
    }

    public EconomicMap<ImportDescriptor, Integer> importedMemoryDescriptors() {
        final EconomicMap<ImportDescriptor, Integer> reverseMap = EconomicMap.create();
        MapCursor<Integer, ImportDescriptor> cursor = importedMemories.getEntries();
        while (cursor.advance()) {
            reverseMap.put(cursor.getValue(), cursor.getKey());
        }
        return reverseMap;
    }

    public EconomicMap<String, Integer> exportedMemories() {
        return exportedMemories;
    }

    public long memoryInitialSize(int index) {
        final MemoryInfo memory = memories[index];
        return memory.initialSize;
    }

    public long memoryMaximumSize(int index) {
        final MemoryInfo memory = memories[index];
        return memory.maximumSize;
    }

    public boolean memoryHasIndexType64(int index) {
        final MemoryInfo memory = memories[index];
        return memory.indexType64;
    }

    public boolean memoryIsShared(int index) {
        final MemoryInfo memory = memories[index];
        return memory.shared;
    }

    public final WasmMemory castMemory(WasmMemory memoryInstance, int index) {
        final MemoryInfo memory = memories[index];
        return CompilerDirectives.castExact(memoryInstance, memory.memoryImpl);
    }

    public final WasmMemory memory(WasmInstance moduleInstance, int index) {
        final WasmMemory memoryInstance = moduleInstance.memory(index);
        return castMemory(memoryInstance, index);
    }

    void allocateCustomSection(String name, int offset, int length) {
        customSections.add(new WasmCustomSection(name, offset, length));
    }

    public List<WasmCustomSection> customSections() {
        return customSections;
    }

    public void checkDataSegmentIndex(int dataIndex) {
        assertTrue(dataCountExists, Failure.DATA_COUNT_SECTION_REQUIRED);
        assertUnsignedIntLess(dataIndex, dataSegmentCount, Failure.UNKNOWN_DATA_SEGMENT);
    }

    public void setDataSegmentCount(int count) {
        this.dataSegmentCount = count;
        this.dataCountExists = true;
    }

    /**
     * Checks whether the actual number of data segments corresponds with the number defined in the
     * data count section.
     */
    public void checkDataSegmentCount(int numberOfDataSegments) {
        if (dataCountExists) {
            assertIntEqual(numberOfDataSegments, this.dataSegmentCount, Failure.DATA_COUNT_MISMATCH);
        }
    }

    public void addFunctionReference(int functionIndex) {
        functionReferences.add(functionIndex);
    }

    public void checkFunctionReference(int functionIndex) {
        assertTrue(functionReferences.contains(functionIndex), Failure.UNDECLARED_FUNCTION_REFERENCE);
    }

    private void ensureDataInstanceCapacity(int index) {
        if (dataInstances == null) {
            dataInstances = new int[Math.max(Integer.highestOneBit(index) << 1, 2)];
        } else if (index >= dataInstances.length) {
            final int[] nDataInstances = new int[Math.max(Integer.highestOneBit(index) << 1, 2 * dataInstances.length)];
            System.arraycopy(dataInstances, 0, nDataInstances, 0, dataInstances.length);
            dataInstances = nDataInstances;
        }
    }

    void setDataInstance(int index, int offset) {
        ensureDataInstanceCapacity(index);
        dataInstances[index] = offset;
        if (!dataCountExists) {
            dataSegmentCount++;
        }
    }

    public int dataInstanceOffset(int index) {
        return dataInstances[index];
    }

    public int dataInstanceCount() {
        return dataSegmentCount;
    }

    void setDroppedDataInstanceOffset(int address) {
        droppedDataInstanceOffset = address;
    }

    public int droppedDataInstanceOffset() {
        return droppedDataInstanceOffset;
    }

    public void checkElemIndex(int elemIndex) {
        assertUnsignedIntLess(elemIndex, elemSegmentCount, Failure.UNKNOWN_ELEM_SEGMENT);
    }

    public void checkElemType(int elemIndex, byte expectedType) {
        assertByteEqual(expectedType, (byte) elemInstances[elemIndex], Failure.TYPE_MISMATCH);
    }

    private void ensureElemInstanceCapacity(int index) {
        if (elemInstances == null) {
            elemInstances = new long[Math.max(Integer.highestOneBit(index) << 1, 2)];
        } else if (index >= elemInstances.length) {
            final long[] nElementInstances = new long[Math.max(Integer.highestOneBit(index) << 1, 2 * elemInstances.length)];
            System.arraycopy(elemInstances, 0, nElementInstances, 0, elemInstances.length);
            elemInstances = nElementInstances;
        }
    }

    void setElemInstance(int index, int offset, byte elemType) {
        ensureElemInstanceCapacity(index);
        elemInstances[index] = (long) offset << 32 | (elemType & 0xFF);
        elemSegmentCount++;
    }

    public int elemInstanceOffset(int index) {
        return (int) (elemInstances[index] >>> 32);
    }

    public int elemInstanceCount() {
        return elemSegmentCount;
    }

    private void ensureCodeEntriesCapacity(int index) {
        if (codeEntries == null) {
            codeEntries = new int[Math.max(Integer.highestOneBit(index) << 1, 2)];
        } else if (index >= codeEntries.length) {
            final int[] nCodeEntries = new int[Math.max(Integer.highestOneBit(index) << 1, 2 * codeEntries.length)];
            System.arraycopy(codeEntries, 0, nCodeEntries, 0, codeEntries.length);
            codeEntries = nCodeEntries;
        }
    }

    void setCodeEntryOffset(int index, int offset) {
        ensureCodeEntriesCapacity(index);
        codeEntries[index] = offset;
        codeEntryCount++;
    }

    public int codeEntryOffset(int index) {
        return codeEntries[index];
    }

    public int codeEntryCount() {
        return codeEntryCount;
    }

    @CompilerDirectives.TruffleBoundary
    public void removeFunctionReferences() {
        functionReferences = null;
    }
}
