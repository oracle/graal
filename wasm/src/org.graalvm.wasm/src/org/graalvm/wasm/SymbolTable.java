/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import org.graalvm.wasm.api.ValueType;
import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.constants.ImportIdentifier;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.ByteArrayWasmMemory;
import org.graalvm.wasm.memory.UnsafeWasmMemory;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import static org.graalvm.wasm.WasmUtil.unsignedInt32ToLong;

/**
 * Contains the symbol information of a module.
 */
public abstract class SymbolTable {
    private static final int INITIAL_GLOBALS_SIZE = 64;
    private static final int INITIAL_DATA_SIZE = 512;
    private static final int INITIAL_TYPE_SIZE = 128;
    private static final int INITIAL_FUNCTION_TYPES_SIZE = 128;
    private static final int GLOBAL_MUTABLE_BIT = 0x0100;
    private static final int GLOBAL_EXPORT_BIT = 0x0200;
    private static final int UNINITIALIZED_GLOBAL_ADDRESS = -1;
    private static final int NO_EQUIVALENCE_CLASS = 0;
    static final int FIRST_EQUIVALENCE_CLASS = NO_EQUIVALENCE_CLASS + 1;

    public static class FunctionType {
        private final byte[] paramTypes;
        private final byte returnType;
        private final int hashCode;

        FunctionType(byte[] paramTypes, byte returnType) {
            this.paramTypes = paramTypes;
            this.returnType = returnType;
            this.hashCode = Arrays.hashCode(paramTypes) ^ Byte.hashCode(returnType);
        }

        public byte[] paramTypes() {
            return paramTypes;
        }

        public byte[] returnTypes() {
            return new byte[]{returnType};
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
            if (this.returnType != that.returnType) {
                return false;
            }
            for (int i = 0; i < paramTypes.length; i++) {
                if (this.paramTypes[i] != that.paramTypes[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class TableInfo {
        public final int initialSize;
        public final int maximumSize;

        public TableInfo(int initialSize, int maximumSize) {
            this.initialSize = initialSize;
            this.maximumSize = maximumSize;
        }
    }

    public static class MemoryInfo {
        public final int initialSize;
        public final int maximumSize;

        public MemoryInfo(int initialSize, int maximumSize) {
            this.initialSize = initialSize;
            this.maximumSize = maximumSize;
        }
    }

    /**
     * Encodes the arguments and return types of each function type.
     *
     * Given a function type index, the {@link #typeOffsets} array indicates where the encoding for
     * that function type begins in this array.
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
     * where `na` is the number of arguments, and `nr` is the number of return values.
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
    @CompilationFinal(dimensions = 1) private int[] typeOffsets;

    /**
     * Stores the type equivalence class.
     *
     * Since multiple types have the same shape, each type is mapped to an equivalence class, so
     * that two types can be quickly compared.
     *
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
     *
     * This array is monotonically populated from left to right during parsing. Any code that uses
     * this array should only access the locations in the array that have already been populated.
     */
    @CompilationFinal(dimensions = 1) private WasmFunction[] functions;
    @CompilationFinal private int numFunctions;

    /**
     * List of all imported functions.
     */
    private final List<WasmFunction> importedFunctions;

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
     * A global type is the value type of the global, followed by its mutability. This is encoded as
     * two bytes -- the lowest (0th) byte is the value type. The 1st byte is organized like this:
     *
     * <code>
     * | . | . | . | . | . | initialized flag | exported flag | mutable flag |
     * </code>
     */
    @CompilationFinal(dimensions = 1) short[] globalTypes;

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
     * The descriptor of the table of this module.
     *
     * In the current WebAssembly specification, a module can use at most one table. The value
     * {@code null} denotes that this module uses no table.
     */
    @CompilationFinal private TableInfo table;

    /**
     * The table used in this module.
     */
    @CompilationFinal private ImportDescriptor importedTableDescriptor;

    /**
     * The name of the exported table of this module, if any.
     */
    @CompilationFinal private String exportedTable;

    /**
     * The descriptor of the memory of this module.
     *
     * In the current WebAssembly specification, a module can use at most one memory. The value
     * {@code null} denotes that this module uses no memory.
     */
    @CompilationFinal private MemoryInfo memory;

    /**
     * The memory used in this module.
     */
    @CompilationFinal private ImportDescriptor importedMemoryDescriptor;

    /**
     * The name of the exported memory of this module, if any.
     */
    @CompilationFinal private String exportedMemory;

    /**
     * List of all custom sections.
     */
    private final List<WasmCustomSection> customSections;

    SymbolTable() {
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
        this.exportedFunctions = new LinkedHashMap<>();
        this.exportedFunctionsByIndex = new HashMap<>();
        this.startFunctionIndex = -1;
        this.globalTypes = new short[INITIAL_GLOBALS_SIZE];
        this.importedGlobals = new LinkedHashMap<>();
        this.exportedGlobals = new LinkedHashMap<>();
        this.maxGlobalIndex = -1;
        this.table = null;
        this.importedTableDescriptor = null;
        this.exportedTable = null;
        this.memory = null;
        this.importedMemoryDescriptor = null;
        this.exportedMemory = null;
        this.customSections = new ArrayList<>();
    }

    private void checkNotParsed() {
        // The symbol table must be read-only after the module gets linked.
        if (module().isParsed()) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "The engine tried to modify the symbol table after parsing.");
        }
    }

    private void checkUniqueExport(String name) {
        if (exportedFunctions.containsKey(name) || exportedGlobals.containsKey(name) || Objects.equals(exportedMemory, name) || Objects.equals(exportedTable, name)) {
            throw WasmException.create(Failure.UNSPECIFIED_UNLINKABLE, "All export names must be different, but '" + name + "' is exported twice.");
        }
    }

    public void checkFunctionIndex(int funcIndex) {
        if (funcIndex < 0 || funcIndex >= numFunctions) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, String.format("Function index out of bounds: %d should be < %d.", unsignedInt32ToLong(funcIndex), numFunctions));
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
     * Ensure that the {@link #typeOffsets} and {@link #typeEquivalenceClasses} arrays have enough
     * space to store the data for the type at {@code index}. If there is not enough space, then a
     * reallocation of the array takes place, doubling its capacity.
     *
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

    int allocateFunctionType(int numParameterTypes, int numReturnTypes) {
        checkNotParsed();
        ensureTypeCapacity(typeCount);
        int typeIdx = typeCount++;
        typeOffsets[typeIdx] = typeDataSize;

        if (numReturnTypes != 0 && numReturnTypes != 1) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "A function can return at most one result.");
        }

        int size = 2 + numParameterTypes + numReturnTypes;
        ensureTypeDataCapacity(typeDataSize + size);
        typeData[typeDataSize + 0] = numParameterTypes;
        typeData[typeDataSize + 1] = numReturnTypes;
        typeDataSize += size;
        return typeIdx;
    }

    public int allocateFunctionType(byte[] parameterTypes, byte[] returnTypes) {
        checkNotParsed();
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
        checkNotParsed();
        int idx = 2 + typeOffsets[funcTypeIdx] + paramIdx;
        typeData[idx] = type;
    }

    void registerFunctionTypeReturnType(int funcTypeIdx, int returnIdx, byte type) {
        checkNotParsed();
        int idx = 2 + typeOffsets[funcTypeIdx] + typeData[typeOffsets[funcTypeIdx]] + returnIdx;
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
        if (typeIndex < 0 || typeIndex >= typeCount) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, String.format("Function type out of bounds: %d should be < %d.", unsignedInt32ToLong(typeIndex), typeCount));
        }
        final WasmFunction function = new WasmFunction(this, numFunctions, typeIndex, importDescriptor);
        functions[numFunctions] = function;
        numFunctions++;
        return function;
    }

    WasmFunction declareFunction(int typeIndex) {
        checkNotParsed();
        final WasmFunction function = allocateFunction(typeIndex, null);
        return function;
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
        if (start.numArguments() != 0) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Start function cannot take arguments.");
        }
        if (start.returnTypeLength() != 0) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Start function cannot return a value.");
        }
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
        int typeOffset = typeOffsets[typeIndex];
        int numArgs = typeData[typeOffset + 0];
        return numArgs;
    }

    public byte functionTypeReturnType(int typeIndex) {
        int typeOffset = typeOffsets[typeIndex];
        int numArgTypes = typeData[typeOffset + 0];
        int numReturnTypes = typeData[typeOffset + 1];
        return numReturnTypes == 0 ? (byte) 0x40 : (byte) typeData[typeOffset + 2 + numArgTypes];
    }

    int functionTypeReturnTypeLength(int typeIndex) {
        int typeOffset = typeOffsets[typeIndex];
        int numReturnTypes = typeData[typeOffset + 1];
        return numReturnTypes;
    }

    public WasmFunction startFunction() {
        if (startFunctionIndex == -1) {
            return null;
        }
        return function(startFunctionIndex);
    }

    protected abstract WasmModule module();

    public byte functionTypeArgumentTypeAt(int typeIndex, int i) {
        int typeOffset = typeOffsets[typeIndex];
        return (byte) typeData[typeOffset + 2 + i];
    }

    public byte functionTypeReturnTypeAt(int typeIndex, int i) {
        int typeOffset = typeOffsets[typeIndex];
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

    int typeCount() {
        return typeCount;
    }

    FunctionType typeAt(int index) {
        return new FunctionType(functionTypeArgumentTypes(index).toArray(), functionTypeReturnType(index));
    }

    public void importSymbol(ImportDescriptor descriptor) {
        checkNotParsed();
        importedSymbols.add(descriptor);
    }

    public List<ImportDescriptor> importedSymbols() {
        return importedSymbols;
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
        module().addLinkAction((context, instance) -> context.linker().resolveFunctionExport(module(), functionIndex, exportName));
    }

    public Map<String, WasmFunction> exportedFunctions() {
        return exportedFunctions;
    }

    public WasmFunction importFunction(String moduleName, String functionName, int typeIndex) {
        checkNotParsed();
        final ImportDescriptor descriptor = new ImportDescriptor(moduleName, functionName, ImportIdentifier.FUNCTION);
        importSymbol(descriptor);
        WasmFunction function = allocateFunction(typeIndex, descriptor);
        importedFunctions.add(function);
        module().addLinkAction((context, instance) -> context.linker().resolveFunctionImport(context, instance, function));
        return function;
    }

    public List<WasmFunction> importedFunctions() {
        return importedFunctions;
    }

    public WasmFunction importedFunction(String name) {
        for (WasmFunction f : importedFunctions) {
            if (f.name().equals(name)) {
                return f;
            }
        }
        return null;
    }

    public WasmFunction importedFunction(ImportDescriptor descriptor) {
        for (WasmFunction f : importedFunctions) {
            if (f.importDescriptor().equals(descriptor)) {
                return f;
            }
        }
        return null;
    }

    private void ensureGlobalsCapacity(int index) {
        while (index >= globalTypes.length) {
            final short[] nGlobalTypes = new short[globalTypes.length * 2];
            System.arraycopy(globalTypes, 0, nGlobalTypes, 0, globalTypes.length);
            globalTypes = nGlobalTypes;
        }
    }

    /**
     * Allocates a global index in the symbol table, for a global variable that was already
     * allocated.
     */
    void allocateGlobal(int index, byte valueType, byte mutability) {
        assert (valueType & 0xff) == valueType;
        checkNotParsed();
        ensureGlobalsCapacity(index);
        maxGlobalIndex = Math.max(maxGlobalIndex, index);
        final int mutabilityBit;
        if (mutability == GlobalModifier.CONSTANT) {
            mutabilityBit = 0;
        } else if (mutability == GlobalModifier.MUTABLE) {
            mutabilityBit = GLOBAL_MUTABLE_BIT;
        } else {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Invalid mutability: " + mutability);
        }
        short globalType = (short) (mutabilityBit | valueType);
        globalTypes[index] = globalType;
    }

    void declareExternalGlobal(int index, Object global) {
        final InteropLibrary lib = InteropLibrary.getUncached();
        try {
            final Object descriptor = lib.readMember(global, "descriptor");
            final byte valueType = ValueType.parse((String) lib.readMember(descriptor, "value")).byteValue();
            final byte mutability = (byte) ((boolean) lib.readMember(descriptor, "mutable") ? GlobalModifier.MUTABLE : GlobalModifier.CONSTANT);
            allocateGlobal(index, valueType, mutability);
            module().addLinkAction((context, instance) -> {
                final GlobalRegistry globals = context.globals();
                final int address = globals.allocateExternalGlobal(global);
                instance.setGlobalAddress(index, address);
            });
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, "Global does not have a valid descriptor: " + global);
        }
    }

    void declareGlobal(int index, byte valueType, byte mutability) {
        allocateGlobal(index, valueType, mutability);
        module().addLinkAction((context, instance) -> {
            final GlobalRegistry globals = context.globals();
            final int address = globals.allocateGlobal();
            instance.setGlobalAddress(index, address);
        });
    }

    void importGlobal(String moduleName, String globalName, int index, byte valueType, byte mutability) {
        final ImportDescriptor descriptor = new ImportDescriptor(moduleName, globalName, ImportIdentifier.GLOBAL);
        importedGlobals.put(index, descriptor);
        importSymbol(descriptor);
        allocateGlobal(index, valueType, mutability);
        module().addLinkAction((context, instance) -> instance.setGlobalAddress(index, UNINITIALIZED_GLOBAL_ADDRESS));
        module().addLinkAction((context, instance) -> context.linker().resolveGlobalImport(context, instance, descriptor, index, valueType, mutability));
    }

    public LinkedHashMap<Integer, ImportDescriptor> importedGlobals() {
        return importedGlobals;
    }

    public LinkedHashMap<ImportDescriptor, Integer> importedGlobalDescriptors() {
        final LinkedHashMap<ImportDescriptor, Integer> reverseMap = new LinkedHashMap<>();
        for (Map.Entry<Integer, ImportDescriptor> entry : importedGlobals.entrySet()) {
            reverseMap.put(entry.getValue(), entry.getKey());
        }
        return reverseMap;
    }

    public int maxGlobalIndex() {
        return maxGlobalIndex;
    }

    private boolean globalExported(int index) {
        final int exportStatus = globalTypes[index] & GLOBAL_EXPORT_BIT;
        return exportStatus != 0;
    }

    byte globalMutability(int index) {
        final short globalType = globalTypes[index];
        if ((globalType & GLOBAL_MUTABLE_BIT) != 0) {
            return GlobalModifier.MUTABLE;
        } else {
            return GlobalModifier.CONSTANT;
        }
    }

    public boolean isGlobalMutable(int index) {
        return globalMutability(index) == GlobalModifier.MUTABLE;
    }

    public byte globalValueType(int index) {
        return (byte) (globalTypes[index] & 0xff);
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

    void exportGlobal(String name, int index) {
        checkNotParsed();
        exportSymbol(name);
        if (globalExported(index)) {
            throw new WasmMemoryException("Global " + index + " already exported with the name: " + nameOfExportedGlobal(index));
        }
        globalTypes[index] |= GLOBAL_EXPORT_BIT;
        exportedGlobals.put(name, index);
        module().addLinkAction((context, instance) -> context.linker().resolveGlobalExport(module(), name, index));
    }

    public void declareExportedExternalGlobal(String name, int index, Object global) {
        checkNotParsed();
        declareExternalGlobal(index, global);
        exportGlobal(name, index);
    }

    public void declareExportedGlobalWithValue(String name, int index, byte valueType, byte mutability, long value) {
        checkNotParsed();
        declareGlobal(index, valueType, mutability);
        exportGlobal(name, index);
        module().addLinkAction((context, instance) -> {
            final int address = instance.globalAddress(index);
            context.globals().storeLong(address, value);
        });
    }

    public void allocateTable(int initSize, int maxSize) {
        checkNotParsed();
        validateSingleTable();
        table = new TableInfo(initSize, maxSize);
        module().addLinkAction((context, instance) -> {
            final int index = context.tables().allocateTable(initSize, maxSize);
            instance.setTable(context.tables().table(index));
        });
    }

    public void allocateExternalTable(WasmTable externalTable) {
        checkNotParsed();
        validateSingleTable();
        table = new TableInfo(externalTable.size(), externalTable.maxSize());
        module().addLinkAction((context, instance) -> {
            final int index = context.tables().allocateExternalTable(externalTable);
            instance.setTable(context.tables().table(index));
        });
    }

    void importTable(String moduleName, String tableName, int initSize, int maxSize) {
        checkNotParsed();
        validateSingleTable();
        importedTableDescriptor = new ImportDescriptor(moduleName, tableName, ImportIdentifier.TABLE);
        importSymbol(importedTableDescriptor);
        module().addLinkAction((context, instance) -> context.linker().resolveTableImport(context, instance, importedTableDescriptor, initSize, maxSize));
    }

    private void validateSingleTable() {
        if (importedTableDescriptor != null) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "A table has been already imported in the module.");
        }
        if (table != null) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "A table has been already declared in the module.");
        }
    }

    boolean tableExists() {
        return importedTableDescriptor != null || table != null;
    }

    public void exportTable(String name) {
        checkNotParsed();
        exportSymbol(name);
        if (exportedTable != null) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "A table has been already exported from this module.");
        }
        if (!tableExists()) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "No table has been declared or imported, so a table cannot be exported.");
        }
        exportedTable = name;
        module().addLinkAction((context, instance) -> context.linker().resolveTableExport(module(), exportedTable));
    }

    int tableCount() {
        return tableExists() ? 1 : 0;
    }

    public ImportDescriptor importedTable() {
        return importedTableDescriptor;
    }

    public String exportedTable() {
        return exportedTable;
    }

    public void allocateMemory(int initSize, int maxSize) {
        checkNotParsed();
        validateSingleMemory();
        memory = new MemoryInfo(initSize, maxSize);
        module().addLinkAction((context, instance) -> {
            final BiFunction<Integer, Integer, WasmMemory> makeMemory = context.environment().getOptions().get(WasmOptions.UseUnsafeMemory) ? UnsafeWasmMemory::new : ByteArrayWasmMemory::new;
            final int memoryIndex = context.memories().allocateMemory(memory, makeMemory);
            final WasmMemory allocatedMemory = context.memories().memory(memoryIndex);
            instance.setMemory(allocatedMemory);
        });
    }

    public void allocateExternalMemory(WasmMemory externalMemory) {
        checkNotParsed();
        validateSingleMemory();
        memory = new MemoryInfo(externalMemory.pageSize(), externalMemory.maxPageSize());
        module().addLinkAction((context, instance) -> {
            final int memoryIndex = context.memories().allocateExternalMemory(externalMemory);
            final WasmMemory allocatedMemory = context.memories().memory(memoryIndex);
            instance.setMemory(allocatedMemory);
        });
    }

    public void importMemory(String moduleName, String memoryName, int initSize, int maxSize) {
        checkNotParsed();
        validateSingleMemory();
        importedMemoryDescriptor = new ImportDescriptor(moduleName, memoryName, ImportIdentifier.MEMORY);
        importSymbol(importedMemoryDescriptor);
        module().addLinkAction((context, instance) -> context.linker().resolveMemoryImport(context, instance, importedMemoryDescriptor, initSize, maxSize));
    }

    private void validateSingleMemory() {
        if (importedMemoryDescriptor != null) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Memory has been already imported in the module.");
        }
        if (memory != null) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Memory has been already declared in the module.");
        }
    }

    boolean memoryExists() {
        return importedMemoryDescriptor != null || memory != null;
    }

    public void exportMemory(String name) {
        checkNotParsed();
        exportSymbol(name);
        if (exportedMemory != null) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "A memory has been already exported from this module.");
        }
        if (!memoryExists()) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "No memory has been declared or imported, so memory cannot be exported.");
        }
        exportedMemory = name;
        module().addLinkAction((context, instance) -> context.linker().resolveMemoryExport(instance, name));
    }

    int memoryCount() {
        return memoryExists() ? 1 : 0;
    }

    public ImportDescriptor importedMemory() {
        return importedMemoryDescriptor;
    }

    public String exportedMemory() {
        return exportedMemory;
    }

    void allocateCustomSection(String name, int offset, int length) {
        customSections.add(new WasmCustomSection(name, offset, length));
    }

    public List<WasmCustomSection> customSections() {
        return customSections;
    }

}
