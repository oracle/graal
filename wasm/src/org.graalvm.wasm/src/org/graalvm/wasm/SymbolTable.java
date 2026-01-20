/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.Assert.assertIntEqual;
import static org.graalvm.wasm.Assert.assertTrue;
import static org.graalvm.wasm.Assert.assertUnsignedIntLess;
import static org.graalvm.wasm.WasmMath.maxUnsigned;
import static org.graalvm.wasm.WasmMath.minUnsigned;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.wasm.constants.Mutability;
import org.graalvm.wasm.constants.ImportIdentifier;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryFactory;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticShape;
import org.graalvm.wasm.struct.WasmStruct;
import org.graalvm.wasm.struct.WasmStructAccess;
import org.graalvm.wasm.struct.WasmStructFactory;
import org.graalvm.wasm.types.AbstractHeapType;
import org.graalvm.wasm.types.ArrayType;
import org.graalvm.wasm.types.CompositeType;
import org.graalvm.wasm.types.DefinedType;
import org.graalvm.wasm.types.FieldType;
import org.graalvm.wasm.types.FunctionType;
import org.graalvm.wasm.types.HeapType;
import org.graalvm.wasm.types.NumberType;
import org.graalvm.wasm.types.PackedType;
import org.graalvm.wasm.types.RecursiveTypes;
import org.graalvm.wasm.types.ReferenceType;
import org.graalvm.wasm.types.StorageType;
import org.graalvm.wasm.types.StructType;
import org.graalvm.wasm.types.SubType;
import org.graalvm.wasm.types.ValueType;
import org.graalvm.wasm.types.VectorType;

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
    private static final int INITIAL_TAG_TYPE_SIZE = 1;
    private static final byte GLOBAL_MUTABLE_BIT = 0x01;
    private static final byte GLOBAL_EXPORTED_BIT = 0x02;
    private static final byte GLOBAL_INITIALIZED_BIT = 0x04;
    private static final byte GLOBAL_IMPORTED_BIT = 0x10;
    private static final byte GLOBAL_FUNCTION_INITIALIZER_BIT = 0x20;

    public static final int UNINITIALIZED_ADDRESS = Integer.MIN_VALUE;
    public static final int NO_EQUIVALENCE_CLASS = 0;
    public static final int FIRST_EQUIVALENCE_CLASS = NO_EQUIVALENCE_CLASS + 1;

    private static final int FINAL_MASK = 1 << 31;
    private static final int SUPERTYPE_MASK = FINAL_MASK - 1;
    private static final int NO_SUPERTYPE = SUPERTYPE_MASK;

    public static final byte ARRAY_KIND = 1;
    public static final byte STRUCT_KIND = 2;
    public static final byte FUNCTION_KIND = 3;

    /**
     * @param initialSize Lower bound on table size.
     * @param maximumSize Upper bound on table size.
     *            <p>
     *            <em>Note:</em> this is the upper bound defined by the module. A table instance
     *            might have a lower internal max allowed size in practice.
     * @param elemType The element type of the table.
     * @param initValue The initial value of the table's elements, can be {@code null} if no
     *            initializer present
     * @param initBytecode The bytecode of the table's initializer expression, can be {@code null}
     *            if no initializer present
     */
    public record TableInfo(int initialSize, int maximumSize, int elemType, Object initValue, byte[] initBytecode) {
    }

    /**
     * @param initialSize Lower bound on memory size (in pages of 64 kiB).
     * @param maximumSize Upper bound on memory size (in pages of 64 kiB).
     *            <p>
     *            <em>Note:</em> this is the upper bound defined by the module. A memory instance
     *            might have a lower internal max allowed size in practice.
     * @param indexType64 If the memory uses index type 64.
     * @param shared Whether the memory is shared (modifications are visible to other threads).
     */
    public record MemoryInfo(long initialSize, long maximumSize, boolean indexType64, boolean shared) {
    }

    /**
     * @param attribute Attribute of the tag.
     * @param typeIndex The type index of the tag.
     */
    public record TagInfo(byte attribute, int typeIndex) {
    }

    /**
     * Encodes the structure of each defined type.
     * <p>
     * Given a defined type index, the {@link #typeOffsets} array indicates where the encoding for
     * that defined type begins in this array.
     * <p>
     * For an array type starting at index i, the encoding is the following
     * <p>
     * <code>
     *   i               i+1
     * +--------------+------------+
     * | element type | mutability |
     * +--------------+------------+
     * </code>
     * <p>
     * For a struct type starting at index i, the encoding is the following
     * <p>
     * <code>
     *   i     i+1       i+2                  i+1+2*(nf-1)  i+1+2*(nf-1)+1
     * +-----+---------+--------------+-----+-------------+---------------+
     * | nf  |  type 1 | mutability 1 | ... | type nf     | mutability nf |
     * +-----+---------+--------------+-----+-------------+---------------+
     * </code>
     * <p>
     * where `nf` is the number of fields.
     * <p>
     * For a function type starting at index i, the encoding is the following
     * <p>
     * <code>
     *   i     i+1   i+2+0        i+2+na-1  i+2+na+0        i+2+na+nr-1
     * +-----+-----+-------+-----+--------+----------+-----+-----------+
     * | np  |  nr | par 1 | ... | par np | result 1 | ... | result nr |
     * +-----+-----+-------+-----+--------+----------+-----+-----------+
     * </code>
     * <p>
     * where `np` is the number of parameters, and `nr` is the number of result values.
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
     * Stores for each typed defined in {@link #typeData} the kind of the type (array, struct or
     * function). The values stored are always one of {@link #ARRAY_KIND}, {@link #STRUCT_KIND} or
     * {@link #FUNCTION_KIND}.
     */
    @CompilationFinal(dimensions = 1) private byte[] typeKinds;

    /**
     * Stores the closed forms of all the types defined in this module. Closed forms replace type
     * indices with the definitions of the referenced types, resulting in a tree-like data
     * structure.
     */
    @CompilationFinal(dimensions = 1) private DefinedType[] closedTypes;

    /**
     * Stores metadata relevant for runtime type checks. For every defined type in
     * {@link #typeData}, there is one {@code int} value with the following bit pattern:
     * <p>
     * <code>
     *   31      30 . . . . . . 0
     * +-------+-----------------+
     * | final | supertype index |
     * +-------+-----------------+
     * </code>
     */
    @CompilationFinal(dimensions = 1) private int[] superTypes;
    private int[] superTypeDepth;

    @CompilationFinal(dimensions = 1) private WasmStructAccess[] structAccesses;

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
     * Value types of globals.
     */
    @CompilationFinal(dimensions = 1) private int[] globalTypes;

    /**
     * Mutability flags of globals. These are encoded like this:
     * <p>
     * <code>
     * | . | . | . | functionOrIndex flag | reference flag | initialized flag | exported flag | mutable flag |
     * </code>
     */
    @CompilationFinal(dimensions = 1) private byte[] globalFlags;

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
     * Number of "external" (imported or exported) globals in the module.
     */
    @CompilationFinal private int numExternalGlobals;

    /**
     * A mapping between the indices of the globals and their corresponding global instance slot.
     * Negative addresses point into the external globals array, where {@code index = -address-1},
     * while 0 and positive addresses point into the internal globals array.
     * <p>
     * This array is monotonically populated from the left. Index i denotes the i-th global in this
     * module. The value at index i denotes the address of the global in the memory space for all
     * the globals of this module (see {@link GlobalRegistry}).
     * <p>
     * This mapping of global indices is done because, while the index and address spaces of the
     * globals are module-specific, the address space of the globals is split up into two regions
     * based on whether they have internal (local) or external (imported and/or exported) linkage.
     * <p>
     * Global addresses are assigned after the symbol table is fully parsed.
     *
     * @see #finishSymbolTable()
     */
    @CompilationFinal(dimensions = 1) private int[] globalAddresses;

    /**
     * Number of globals that need a bytecode initializer.
     */
    @CompilationFinal private int numGlobalInitializersBytecode;

    /**
     * The descriptor of the tables of this module.
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
     * The descriptors of the memories of this module.
     */
    @CompilationFinal(dimensions = 1) private MemoryInfo[] memories;

    @CompilationFinal private int memoryCount;

    /**
     * The memories used in this module.
     */
    @CompilationFinal private final EconomicMap<Integer, ImportDescriptor> importedMemories;

    /**
     * The name(s) of the exported memories of this module, if any.
     */
    @CompilationFinal private final EconomicMap<String, Integer> exportedMemories;

    /**
     * The descriptors of the tags of this module.
     */
    @CompilationFinal(dimensions = 1) private TagInfo[] tags;

    @CompilationFinal private int tagCount;

    /**
     * The tags used in this module.
     */
    @CompilationFinal private final EconomicMap<Integer, ImportDescriptor> importedTags;

    /**
     * The name(s) of the exported tags of this module, if any.
     */
    @CompilationFinal private final EconomicMap<String, Integer> exportedTags;

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

    @CompilationFinal private int codeEntryCount;

    /**
     * All function indices that can be referenced via
     * {@link org.graalvm.wasm.constants.Instructions#REF_FUNC}.
     */
    @CompilationFinal private EconomicSet<Integer> functionReferences;

    SymbolTable() {
        CompilerAsserts.neverPartOfCompilation();
        this.typeData = new int[INITIAL_DATA_SIZE];
        this.typeOffsets = new int[INITIAL_TYPE_SIZE];
        this.typeKinds = new byte[INITIAL_TYPE_SIZE];
        this.closedTypes = new DefinedType[INITIAL_TYPE_SIZE];
        this.superTypes = new int[INITIAL_TYPE_SIZE];
        Arrays.fill(superTypes, NO_SUPERTYPE);
        this.superTypeDepth = new int[INITIAL_TYPE_SIZE];
        this.structAccesses = new WasmStructAccess[INITIAL_TYPE_SIZE];
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
        this.globalTypes = new int[INITIAL_GLOBALS_SIZE];
        this.globalFlags = new byte[INITIAL_GLOBALS_SIZE];
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
        this.tags = new TagInfo[INITIAL_TAG_TYPE_SIZE];
        this.importedTags = EconomicMap.create();
        this.exportedTags = EconomicMap.create();
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
        if (exportedFunctions.containsKey(name) || exportedGlobals.containsKey(name) || exportedMemories.containsKey(name) || exportedTables.containsKey(name) || exportedTags.containsKey(name)) {
            throw WasmException.create(Failure.DUPLICATE_EXPORT, "All export names must be different, but '" + name + "' is exported twice.");
        }
    }

    public void checkFunctionIndex(int funcIndex) {
        assertUnsignedIntLess(funcIndex, numFunctions, Failure.UNKNOWN_FUNCTION);
    }

    /**
     * Ensure that the {@link #typeData} array has enough space to store {@code index}. If there is
     * not enough space, then a reallocation of the array takes place, doubling its capacity.
     * <p>
     * No synchronisation is required for this method, as it is only called during parsing, which is
     * carried out by a single thread.
     */
    private void ensureTypeDataCapacity(int index) {
        if (typeData.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * typeData.length);
            typeData = Arrays.copyOf(typeData, newLength);
        }
    }

    /**
     * Ensure that the {@link #typeOffsets}, {@link #closedTypes} and {@link #superTypes} arrays
     * have enough space to store the data for the type at {@code index}. If there is not enough
     * space, then a reallocation of the array takes place, doubling its capacity.
     * <p>
     * No synchronisation is required for this method, as it is only called during parsing, which is
     * carried out by a single thread.
     */
    private void ensureTypeCapacity(int index) {
        int oldLength = typeOffsets.length;
        if (typeOffsets.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * oldLength);
            typeOffsets = Arrays.copyOf(typeOffsets, newLength);
            typeKinds = Arrays.copyOf(typeKinds, newLength);
            closedTypes = Arrays.copyOf(closedTypes, newLength);
            superTypes = Arrays.copyOf(superTypes, newLength);
            Arrays.fill(superTypes, oldLength, newLength, NO_SUPERTYPE);
            superTypeDepth = Arrays.copyOf(superTypeDepth, newLength);
            structAccesses = Arrays.copyOf(structAccesses, newLength);
        }
    }

    void declareRecursiveTypeGroup(int subTypeCount) {
        checkNotParsed();
        ensureTypeCapacity(typeCount + subTypeCount - 1);
        typeCount += subTypeCount;
    }

    void registerFinalType(int typeIdx, boolean finalType) {
        checkNotParsed();
        ensureTypeCapacity(typeIdx);
        if (finalType) {
            superTypes[typeIdx] |= FINAL_MASK;
        } else {
            superTypes[typeIdx] &= ~FINAL_MASK;
        }
    }

    public boolean isFinalType(int typeIdx) {
        return (superTypes[typeIdx] & FINAL_MASK) != 0;
    }

    void registerSuperType(int typeIdx, int superTypeIdx) {
        assert (superTypeIdx & SUPERTYPE_MASK) == superTypeIdx;
        checkNotParsed();
        ensureTypeCapacity(typeIdx);
        superTypes[typeIdx] = superTypes[typeIdx] & ~SUPERTYPE_MASK | superTypeIdx;
        superTypeDepth[typeIdx] = superTypeDepth[superTypeIdx] + 1;
    }

    public boolean hasSuperType(int typeIdx) {
        return (superTypes[typeIdx] & SUPERTYPE_MASK) != NO_SUPERTYPE;
    }

    public int superType(int typeIdx) {
        assert hasSuperType(typeIdx);
        return superTypes[typeIdx] & SUPERTYPE_MASK;
    }

    public int superTypeDepth(int typeIdx) {
        checkNotParsed();
        return superTypeDepth[typeIdx];
    }

    void registerArrayType(int typeIdx, int elemType, byte mutability) {
        checkNotParsed();
        ensureTypeCapacity(typeIdx);
        typeOffsets[typeIdx] = typeDataSize;
        typeKinds[typeIdx] = ARRAY_KIND;

        int size = 2;
        ensureTypeDataCapacity(typeDataSize + size);
        typeData[typeDataSize] = elemType;
        typeData[typeDataSize + 1] = mutability;
        typeDataSize += size;
    }

    void registerStructType(int typeIdx, int fieldCount) {
        checkNotParsed();
        ensureTypeCapacity(typeIdx);
        typeOffsets[typeIdx] = typeDataSize;
        typeKinds[typeIdx] = STRUCT_KIND;

        int size = 1 + 2 * fieldCount;
        ensureTypeDataCapacity(typeDataSize + size);
        typeData[typeDataSize] = fieldCount;
        typeDataSize += size;
    }

    void registerFunctionType(int typeIdx, int paramCount, int resultCount, boolean isMultiValue) {
        checkNotParsed();
        ensureTypeCapacity(typeIdx);
        typeOffsets[typeIdx] = typeDataSize;
        typeKinds[typeIdx] = FUNCTION_KIND;

        if (!isMultiValue && resultCount != 0 && resultCount != 1) {
            throw WasmException.create(Failure.INVALID_RESULT_ARITY, "A function can return at most one result.");
        }

        int size = 2 + paramCount + resultCount;
        ensureTypeDataCapacity(typeDataSize + size);
        typeData[typeDataSize + 0] = paramCount;
        typeData[typeDataSize + 1] = resultCount;
        typeDataSize += size;
    }

    public int allocateFunctionType(int[] paramTypes, int[] resultTypes, boolean isMultiValue, WasmLanguage language) {
        checkNotParsed();
        final int typeIdx = typeCount;
        declareRecursiveTypeGroup(1);
        registerFinalType(typeIdx, true);
        registerFunctionType(typeIdx, paramTypes.length, resultTypes.length, isMultiValue);
        for (int i = 0; i < paramTypes.length; i++) {
            registerFunctionTypeParameterType(typeIdx, i, paramTypes[i]);
        }
        for (int i = 0; i < resultTypes.length; i++) {
            registerFunctionTypeResultType(typeIdx, i, resultTypes[i]);
        }
        finishRecursiveTypeGroup(typeIdx, language);
        return typeIdx;
    }

    ArrayType finishArrayType(int arrayTypeIdx, int recursiveTypeGroupStart) {
        StorageType storageType = closedStorageTypeOf(arrayTypeElemType(arrayTypeIdx), recursiveTypeGroupStart);
        byte mutability = arrayTypeMutability(arrayTypeIdx);
        FieldType fieldType = new FieldType(storageType, mutability);
        return new ArrayType(fieldType);
    }

    StructType finishStructType(int structTypeIdx, int recursiveTypeGroupStart, WasmLanguage language) {
        StaticShape.Builder shapeBuilder = StaticShape.newBuilder(language);
        FieldType[] fieldTypes = new FieldType[structTypeFieldCount(structTypeIdx)];
        StaticProperty[] properties = new StaticProperty[structTypeFieldCount(structTypeIdx)];
        WasmStructAccess superTypeAccess = hasSuperType(structTypeIdx) && isStructType(superType(structTypeIdx)) ? structTypeAccess(superType(structTypeIdx)) : null;
        int superFieldCount = superTypeAccess != null ? superTypeAccess.properties().length : 0;
        for (int i = 0; i < fieldTypes.length; i++) {
            StorageType storageType = closedStorageTypeOf(structTypeFieldTypeAt(structTypeIdx, i), recursiveTypeGroupStart);
            byte mutability = structTypeFieldMutabilityAt(structTypeIdx, i);
            fieldTypes[i] = new FieldType(storageType, mutability);
            if (i < superFieldCount) {
                properties[i] = superTypeAccess.properties()[i];
            } else {
                properties[i] = new DefaultStaticProperty(Integer.toString(i));
                shapeBuilder.property(properties[i], fieldTypes[i].javaClass(), mutability == Mutability.CONSTANT);
            }
        }
        StaticShape<WasmStructFactory> shape;
        if (superTypeAccess != null) {
            shape = shapeBuilder.build(superTypeAccess.shape());
        } else {
            shape = shapeBuilder.build(WasmStruct.class, WasmStructFactory.class);
        }
        structAccesses[structTypeIdx] = new WasmStructAccess(shape, properties);
        return new StructType(fieldTypes);
    }

    void registerStructTypeField(int structTypeIdx, int fieldIdx, int fieldType, byte fieldMutability) {
        checkNotParsed();
        int idx = typeOffsets[structTypeIdx] + 1 + 2 * fieldIdx;
        typeData[idx] = fieldType;
        typeData[idx + 1] = fieldMutability;
    }

    void registerFunctionTypeParameterType(int funcTypeIdx, int paramIdx, int type) {
        checkNotParsed();
        int idx = 2 + typeOffsets[funcTypeIdx] + paramIdx;
        typeData[idx] = type;
    }

    void registerFunctionTypeResultType(int funcTypeIdx, int resultIdx, int type) {
        checkNotParsed();
        int idx = 2 + typeOffsets[funcTypeIdx] + typeData[typeOffsets[funcTypeIdx]] + resultIdx;
        typeData[idx] = type;
    }

    FunctionType finishFunctionType(int funcTypeIdx, int recursiveTypeGroupStart) {
        ValueType[] paramTypes = new ValueType[functionTypeParamCount(funcTypeIdx)];
        for (int i = 0; i < paramTypes.length; i++) {
            paramTypes[i] = closedTypeOf(functionTypeParamTypeAt(funcTypeIdx, i), recursiveTypeGroupStart);
        }
        ValueType[] resultTypes = new ValueType[functionTypeResultCount(funcTypeIdx)];
        for (int i = 0; i < resultTypes.length; i++) {
            resultTypes[i] = closedTypeOf(functionTypeResultTypeAt(funcTypeIdx, i), recursiveTypeGroupStart);
        }
        return new FunctionType(paramTypes, resultTypes);
    }

    void finishRecursiveTypeGroup(int recursiveTypeGroupStart, WasmLanguage language) {
        SubType[] subTypes = new SubType[typeCount - recursiveTypeGroupStart];
        for (int typeIndex = recursiveTypeGroupStart; typeIndex < typeCount; typeIndex++) {
            CompositeType compositeType = switch (typeKind(typeIndex)) {
                case ARRAY_KIND -> finishArrayType(typeIndex, recursiveTypeGroupStart);
                case STRUCT_KIND -> finishStructType(typeIndex, recursiveTypeGroupStart, language);
                case FUNCTION_KIND -> finishFunctionType(typeIndex, recursiveTypeGroupStart);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            DefinedType superType;
            if (hasSuperType(typeIndex)) {
                int superTypeIndex = superType(typeIndex);
                if (superTypeIndex >= recursiveTypeGroupStart) {
                    superType = DefinedType.makeRecursiveReference(superTypeIndex - recursiveTypeGroupStart);
                } else {
                    superType = closedTypes[superTypeIndex];
                }
            } else {
                superType = null;
            }
            subTypes[typeIndex - recursiveTypeGroupStart] = new SubType(isFinalType(typeIndex), superType, compositeType);
        }
        RecursiveTypes recursiveTypes = new RecursiveTypes(subTypes);
        for (int typeIndex = recursiveTypeGroupStart; typeIndex < typeCount; typeIndex++) {
            DefinedType type = DefinedType.makeTopLevelType(recursiveTypes, typeIndex - recursiveTypeGroupStart);
            int equivalenceClass = language.equivalenceClassFor(type);
            type.setTypeEquivalenceClass(equivalenceClass);
            if (isStructType(typeIndex)) {
                type.setStructAccess(structTypeAccess(typeIndex));
            }
            closedTypes[typeIndex] = type;
        }
        for (int subTypeIndex = 0; subTypeIndex < subTypes.length; subTypeIndex++) {
            subTypes[subTypeIndex].unroll(recursiveTypes);
            if (hasSuperType(recursiveTypeGroupStart + subTypeIndex)) {
                subTypes[subTypeIndex].superType().setTypeEquivalenceClass(closedTypeAt(superType(recursiveTypeGroupStart + subTypeIndex)).typeEquivalenceClass());
            }
        }
    }

    private void ensureFunctionsCapacity(int index) {
        if (functions.length <= index) {
            int newLength = Math.max(Integer.highestOneBit(index) << 1, 2 * functions.length);
            functions = Arrays.copyOf(functions, newLength);
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

    public WasmFunction declareFunction(int typeIndex) {
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
        assert isFunctionType(typeIndex);
        int typeOffset = typeOffsets[typeIndex];
        return typeData[typeOffset + 0];
    }

    public int functionTypeResultCount(int typeIndex) {
        assert isFunctionType(typeIndex);
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

    public byte typeKind(int typeIndex) {
        return typeKinds[typeIndex];
    }

    public boolean isArrayType(int typeIndex) {
        return typeKind(typeIndex) == ARRAY_KIND;
    }

    public boolean isStructType(int typeIndex) {
        return typeKind(typeIndex) == STRUCT_KIND;
    }

    public boolean isFunctionType(int typeIndex) {
        return typeKind(typeIndex) == FUNCTION_KIND;
    }

    public int arrayTypeElemType(int typeIndex) {
        assert isArrayType(typeIndex);
        int typeOffset = typeOffsets[typeIndex];
        return typeData[typeOffset];
    }

    public byte arrayTypeMutability(int typeIndex) {
        assert isArrayType(typeIndex);
        int typeOffset = typeOffsets[typeIndex];
        return (byte) typeData[typeOffset + 1];
    }

    public int structTypeFieldCount(int typeIndex) {
        assert isStructType(typeIndex);
        int typeOffset = typeOffsets[typeIndex];
        return typeData[typeOffset];
    }

    public int structTypeFieldTypeAt(int typeIndex, int fieldIndex) {
        assert isStructType(typeIndex);
        assert fieldIndex < structTypeFieldCount(typeIndex);
        int typeOffset = typeOffsets[typeIndex];
        return typeData[typeOffset + 1 + 2 * fieldIndex];
    }

    public byte structTypeFieldMutabilityAt(int typeIndex, int fieldIndex) {
        assert isStructType(typeIndex);
        assert fieldIndex < structTypeFieldCount(typeIndex);
        int typeOffset = typeOffsets[typeIndex];
        return (byte) typeData[typeOffset + 1 + 2 * fieldIndex + 1];
    }

    public WasmStructAccess structTypeAccess(int typeIndex) {
        assert isStructType(typeIndex);
        return structAccesses[typeIndex];
    }

    public int functionTypeParamTypeAt(int typeIndex, int paramIndex) {
        assert isFunctionType(typeIndex);
        assert paramIndex < functionTypeParamCount(typeIndex);
        int typeOffset = typeOffsets[typeIndex];
        return typeData[typeOffset + 2 + paramIndex];
    }

    public int functionTypeResultTypeAt(int typeIndex, int resultIndex) {
        assert isFunctionType(typeIndex);
        assert resultIndex < functionTypeResultCount(typeIndex);
        int typeOffset = typeOffsets[typeIndex];
        int paramCount = typeData[typeOffset];
        return typeData[typeOffset + 2 + paramCount + resultIndex];
    }

    public int[] functionTypeParamTypesAsArray(int typeIndex) {
        assert isFunctionType(typeIndex);
        int paramCount = functionTypeParamCount(typeIndex);
        int[] paramTypes = new int[paramCount];
        for (int i = 0; i < paramCount; ++i) {
            paramTypes[i] = functionTypeParamTypeAt(typeIndex, i);
        }
        return paramTypes;
    }

    public int[] functionTypeResultTypesAsArray(int typeIndex) {
        assert isFunctionType(typeIndex);
        int resultTypeCount = functionTypeResultCount(typeIndex);
        int[] resultTypes = new int[resultTypeCount];
        for (int i = 0; i < resultTypeCount; i++) {
            resultTypes[i] = functionTypeResultTypeAt(typeIndex, i);
        }
        return resultTypes;
    }

    public int typeCount() {
        return typeCount;
    }

    /**
     * Fetches the closed form of a type defined in this module at index {@code typeIndex}.
     * 
     * @param typeIndex index of a type defined in this module
     */
    public DefinedType closedTypeAt(int typeIndex) {
        return closedTypes[typeIndex];
    }

    /**
     * A convenient way of calling {@link #closedTypeOf(int, SymbolTable)} when a
     * {@link SymbolTable} is present.
     * 
     * @see #closedTypeOf(int, SymbolTable)
     */
    public ValueType closedTypeOf(int type) {
        return SymbolTable.closedTypeOf(type, this, Integer.MAX_VALUE);
    }

    /**
     * A convenient way of calling {@link #closedTypeOf(int, SymbolTable, int)} when a
     * {@link SymbolTable} is present.
     *
     * @see #closedTypeOf(int, SymbolTable)
     */
    private ValueType closedTypeOf(int type, int recursiveTypeGroupStart) {
        return SymbolTable.closedTypeOf(type, this, recursiveTypeGroupStart);
    }

    /**
     * Maps a type encoded as an {@code int} (as per {@link WasmType}) into its closed form,
     * represented as a {@link ValueType}. Any type indices in the type are resolved using the
     * provided symbol table.
     * <p>
     * It is legal to call this function with a null {@code symbolTable}. This is used in cases
     * where we need to map a predefined value type to the closed type data representation (i.e. we
     * know the type is already closed anyway and so it does not contain any type indices).
     * </p>
     *
     * @param type the {@code int}-encoded Wasm type to be expanded
     * @param symbolTable used for lookup of type definitions when expanding type indices
     */
    public static ValueType closedTypeOf(int type, SymbolTable symbolTable) {
        return closedTypeOf(type, symbolTable, Integer.MAX_VALUE);
    }

    /**
     * This overload of {@link #closedTypeOf(int, SymbolTable)} can detect recursive references and
     * emit specially marked {@link DefinedType}s that are later unrolled.
     *
     * @param recursiveTypeGroupStart the type index of the first type of the current group of
     *            mutually recursive types (this lets us detect recursive references)
     */
    private static ValueType closedTypeOf(int type, SymbolTable symbolTable, int recursiveTypeGroupStart) {
        return switch (type) {
            case WasmType.I32_TYPE -> NumberType.I32;
            case WasmType.I64_TYPE -> NumberType.I64;
            case WasmType.F32_TYPE -> NumberType.F32;
            case WasmType.F64_TYPE -> NumberType.F64;
            case WasmType.V128_TYPE -> VectorType.V128;
            default -> {
                assert WasmType.isReferenceType(type);
                boolean nullable = WasmType.isNullable(type);
                int heapType = WasmType.getHeapType(type);
                yield new ReferenceType(nullable, closedHeapTypeOf(heapType, symbolTable, recursiveTypeGroupStart));
            }
        };
    }

    /**
     * Like {@link #closedTypeOf(int)}, but for mapping heap types (both abstract and concrete) to
     * {@link HeapType} objects.
     */
    public HeapType closedHeapTypeOf(int type) {
        return closedHeapTypeOf(type, this, Integer.MAX_VALUE);
    }

    /**
     * Like {@link #closedTypeOf(int, SymbolTable, int)}, but for mapping heap types (both abstract
     * and concrete) to {@link HeapType} objects.
     */
    private static HeapType closedHeapTypeOf(int heapType, SymbolTable symbolTable, int recursiveTypeGroupStart) {
        return switch (heapType) {
            case WasmType.NOEXN_HEAPTYPE -> AbstractHeapType.NOEXN;
            case WasmType.NOFUNC_HEAPTYPE -> AbstractHeapType.NOFUNC;
            case WasmType.NOEXTERN_HEAPTYPE -> AbstractHeapType.NOEXTERN;
            case WasmType.NONE_HEAPTYPE -> AbstractHeapType.NONE;
            case WasmType.FUNC_HEAPTYPE -> AbstractHeapType.FUNC;
            case WasmType.EXTERN_HEAPTYPE -> AbstractHeapType.EXTERN;
            case WasmType.ANY_HEAPTYPE -> AbstractHeapType.ANY;
            case WasmType.EQ_HEAPTYPE -> AbstractHeapType.EQ;
            case WasmType.I31_HEAPTYPE -> AbstractHeapType.I31;
            case WasmType.STRUCT_HEAPTYPE -> AbstractHeapType.STRUCT;
            case WasmType.ARRAY_HEAPTYPE -> AbstractHeapType.ARRAY;
            case WasmType.EXN_HEAPTYPE -> AbstractHeapType.EXN;
            default -> {
                assert WasmType.isConcreteReferenceType(heapType);
                assert symbolTable != null;
                if (heapType >= recursiveTypeGroupStart) {
                    yield DefinedType.makeRecursiveReference(heapType - recursiveTypeGroupStart);
                } else {
                    yield symbolTable.closedTypeAt(heapType);
                }
            }
        };
    }

    /**
     * A version of {@link #closedTypeOf(int)} that also handles storage types
     * ({@link WasmType#I8_TYPE} and {@link WasmType#I16_TYPE}).
     */
    public StorageType closedStorageTypeOf(int type) {
        return closedStorageTypeOf(type, Integer.MAX_VALUE);
    }

    /**
     * A version of {@link #closedTypeOf(int, int)} that also handles storage types
     * ({@link WasmType#I8_TYPE} and {@link WasmType#I16_TYPE}).
     */
    private StorageType closedStorageTypeOf(int type, int recursiveTypeGroupStart) {
        return switch (type) {
            case WasmType.I8_TYPE -> PackedType.I8;
            case WasmType.I16_TYPE -> PackedType.I16;
            default -> closedTypeOf(type, recursiveTypeGroupStart);
        };
    }

    /**
     * Returns the most general abstract heap type that is a supertype of the input heap type.
     */
    public int topHeapTypeOf(int heapType) {
        return switch (heapType) {
            case WasmType.BOT -> WasmType.TOP;
            case WasmType.NOEXN_HEAPTYPE, WasmType.EXN_HEAPTYPE -> WasmType.EXN_HEAPTYPE;
            case WasmType.NOFUNC_HEAPTYPE, WasmType.FUNC_HEAPTYPE -> WasmType.FUNC_HEAPTYPE;
            case WasmType.NOEXTERN_HEAPTYPE, WasmType.EXTERN_HEAPTYPE -> WasmType.EXTERN_HEAPTYPE;
            case WasmType.NONE_HEAPTYPE, WasmType.ANY_HEAPTYPE, WasmType.EQ_HEAPTYPE, WasmType.I31_HEAPTYPE, WasmType.STRUCT_HEAPTYPE, WasmType.ARRAY_HEAPTYPE -> WasmType.ANY_HEAPTYPE;
            default -> {
                assert WasmType.isConcreteReferenceType(heapType);
                yield switch (typeKind(heapType)) {
                    case ARRAY_KIND, STRUCT_KIND -> WasmType.ANY_HEAPTYPE;
                    case FUNCTION_KIND -> WasmType.FUNC_HEAPTYPE;
                    default -> throw CompilerDirectives.shouldNotReachHere();
                };
            }
        };
    }

    /**
     * Checks whether the type {@code actualType} matches the type {@code expectedType}. This is the
     * case when {@code actualType} is a subtype of {@code expectedType}.
     */
    public boolean matchesType(int expectedType, int actualType) {
        switch (expectedType) {
            case WasmType.BOT -> {
                return false;
            }
            case WasmType.TOP -> {
                return true;
            }
        }
        switch (actualType) {
            case WasmType.BOT -> {
                return true;
            }
            case WasmType.TOP -> {
                return false;
            }
        }
        StorageType closedExpectedType = closedStorageTypeOf(expectedType);
        StorageType closedActualType = closedStorageTypeOf(actualType);
        return closedExpectedType.equals(closedActualType) || closedActualType.isSubtypeOf(closedExpectedType);
    }

    /**
     * An alternative wording of {@link #matchesType} which is more natural when expressing
     * subtyping constraints in type judgments.
     */
    public boolean isSubtypeOf(int subType, int superType) {
        return matchesType(superType, subType);
    }

    public void importSymbol(ImportDescriptor descriptor) {
        checkNotParsed();
        assert importedSymbols.size() == descriptor.importedSymbolIndex();
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
        module().addLinkAction((context, store, instance, imports) -> {
            store.linker().resolveFunctionExport(module(), functionIndex, exportName);
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
        assert function.index() == descriptor.targetIndex();
        importedFunctions.add(function);
        numImportedFunctions++;
        module().addLinkAction((context, store, instance, imports) -> {
            store.linker().resolveFunctionImport(store, instance, function, imports);
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
        return functions[descriptor.targetIndex()];
    }

    private void ensureGlobalsCapacity(int index) {
        while (index >= globalInitializers.length) {
            final int[] nGlobalTypes = new int[globalTypes.length * 2];
            final byte[] nGlobalFlags = new byte[globalFlags.length * 2];
            final Object[] nGlobalInitializers = new Object[globalInitializers.length * 2];
            System.arraycopy(globalTypes, 0, nGlobalTypes, 0, globalTypes.length);
            System.arraycopy(globalFlags, 0, nGlobalFlags, 0, globalFlags.length);
            System.arraycopy(globalInitializers, 0, nGlobalInitializers, 0, globalInitializers.length);
            globalTypes = nGlobalTypes;
            globalFlags = nGlobalFlags;
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
    void allocateGlobal(int index, int valueType, byte mutability, boolean initialized, boolean imported, byte[] initBytecode, Object initialValue) {
        checkNotParsed();
        ensureGlobalsCapacity(index);
        numGlobals = maxUnsigned(index + 1, numGlobals);
        byte flags;
        if (mutability == Mutability.CONSTANT) {
            flags = 0;
        } else if (mutability == Mutability.MUTABLE) {
            flags = GLOBAL_MUTABLE_BIT;
        } else {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Invalid mutability: " + mutability);
        }
        if (initialized) {
            flags |= GLOBAL_INITIALIZED_BIT;
        }
        if (imported) {
            flags |= GLOBAL_IMPORTED_BIT;
            numExternalGlobals++;
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
        globalTypes[index] = valueType;
        globalFlags[index] = flags;
    }

    /**
     * Declares a non-imported global defined in this module. The global will be internal by
     * default, but may be exported using {@link #exportGlobal}. Imported globals are declared using
     * {@link #importGlobal} instead. This method may only be called during parsing, before linking.
     */
    void declareGlobal(int index, int valueType, byte mutability, boolean initialized, byte[] initBytecode, Object initialValue) {
        assert initialized == (initBytecode == null) : index;
        allocateGlobal(index, valueType, mutability, initialized, false, initBytecode, initialValue);
        module().addLinkAction((context, store, instance, imports) -> {
            store.linker().resolveGlobalInitialization(instance, index, initBytecode, initialValue);
        });
    }

    /**
     * Declares an imported global. May be re-exported.
     */
    void importGlobal(String moduleName, String globalName, int index, int valueType, byte mutability) {
        final ImportDescriptor descriptor = new ImportDescriptor(moduleName, globalName, ImportIdentifier.GLOBAL, index, numImportedSymbols());
        importedGlobals.put(index, descriptor);
        importSymbol(descriptor);
        allocateGlobal(index, valueType, mutability, false, true, null, null);
        module().addLinkAction((context, store, instance, imports) -> {
            store.linker().resolveGlobalImport(store, instance, descriptor, index, valueType, mutability, imports);
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

    public int numInternalGlobals() {
        return numGlobals - numExternalGlobals;
    }

    public int numExternalGlobals() {
        return numExternalGlobals;
    }

    public final int globalAddress(int index) {
        return globalAddresses[index];
    }

    public byte globalMutability(int index) {
        if ((globalFlags(index) & GLOBAL_MUTABLE_BIT) != 0) {
            return Mutability.MUTABLE;
        } else {
            return Mutability.CONSTANT;
        }
    }

    public boolean isGlobalMutable(int index) {
        return globalMutability(index) == Mutability.MUTABLE;
    }

    public int globalValueType(int index) {
        return globalTypes[index];
    }

    private byte globalFlags(int index) {
        return globalFlags[index];
    }

    public boolean globalInitialized(int index) {
        return (globalFlags(index) & GLOBAL_INITIALIZED_BIT) != 0;
    }

    public byte[] globalInitializerBytecode(int index) {
        if ((globalFlags(index) & GLOBAL_FUNCTION_INITIALIZER_BIT) != 0) {
            return null;
        } else {
            return globalInitializersBytecode[(int) globalInitializers[index]];
        }
    }

    public Object globalInitialValue(int index) {
        if ((globalFlags(index) & GLOBAL_FUNCTION_INITIALIZER_BIT) != 0) {
            return globalInitializers[index];
        } else {
            return 0;
        }
    }

    public boolean globalImported(int index) {
        return (globalFlags(index) & GLOBAL_IMPORTED_BIT) != 0;
    }

    public boolean globalExported(int index) {
        return (globalFlags(index) & GLOBAL_EXPORTED_BIT) != 0;
    }

    public boolean globalExternal(int index) {
        return (globalFlags(index) & (GLOBAL_IMPORTED_BIT | GLOBAL_EXPORTED_BIT)) != 0;
    }

    public EconomicMap<String, Integer> exportedGlobals() {
        return exportedGlobals;
    }

    void exportGlobal(String name, int index) {
        checkNotParsed();
        exportSymbol(name);
        if (!globalExternal(index)) {
            numExternalGlobals++;
        }
        globalFlags[index] |= GLOBAL_EXPORTED_BIT;
        exportedGlobals.put(name, index);
        module().addLinkAction((context, store, instance, imports) -> {
            store.linker().resolveGlobalExport(instance.module(), name, index);
        });
    }

    public void declareExportedGlobalWithValue(String name, int index, int valueType, byte mutability, Object value) {
        checkNotParsed();
        declareGlobal(index, valueType, mutability, true, null, value);
        exportGlobal(name, index);
    }

    private void ensureTableCapacity(int index) {
        if (index >= tables.length) {
            final TableInfo[] nTables = new TableInfo[Math.max(Integer.highestOneBit(index) << 1, 2 * tables.length)];
            System.arraycopy(tables, 0, nTables, 0, tables.length);
            tables = nTables;
        }
    }

    public void declareTable(int index, int declaredMinSize, int declaredMaxSize, int elemType, byte[] initBytecode, Object initValue, boolean referenceTypes) {
        checkNotParsed();
        addTable(index, declaredMinSize, declaredMaxSize, elemType, initValue, initBytecode, referenceTypes);
        module().addLinkAction((context, store, instance, imports) -> {
            final int maxAllowedSize = minUnsigned(declaredMaxSize, module().limits().tableInstanceSizeLimit());
            module().limits().checkTableInstanceSize(declaredMinSize);
            final WasmTable wasmTable;
            if (context.getContextOptions().memoryOverheadMode()) {
                // Initialize an empty table in memory overhead mode.
                wasmTable = new WasmTable(0, 0, 0, elemType, this);
            } else {
                wasmTable = new WasmTable(declaredMinSize, declaredMaxSize, maxAllowedSize, elemType, this);
            }
            instance.setTable(index, wasmTable);

            store.linker().resolveTableInitialization(instance, index, initBytecode, initValue);
        });
    }

    void importTable(String moduleName, String tableName, int index, int initSize, int maxSize, int elemType, boolean referenceTypes) {
        checkNotParsed();
        addTable(index, initSize, maxSize, elemType, null, null, referenceTypes);
        final ImportDescriptor importedTable = new ImportDescriptor(moduleName, tableName, ImportIdentifier.TABLE, index, numImportedSymbols());
        importedTables.put(index, importedTable);
        importSymbol(importedTable);
        module().addLinkAction((context, store, instance, imports) -> {
            instance.setTable(index, null);
            store.linker().resolveTableImport(store, instance, importedTable, index, initSize, maxSize, elemType, imports);
        });
    }

    void addTable(int index, int minSize, int maxSize, int elemType, Object initValue, byte[] initBytecode, boolean referenceTypes) {
        if (!referenceTypes) {
            assertTrue(importedTables.isEmpty(), "A table has already been imported in the module.", Failure.MULTIPLE_TABLES);
            assertTrue(tableCount == 0, "A table has already been declared in the module.", Failure.MULTIPLE_TABLES);
        }
        ensureTableCapacity(index);
        final TableInfo table = new TableInfo(minSize, maxSize, elemType, initValue, initBytecode);
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
        module().addLinkAction((context, store, instance, imports) -> {
            store.linker().resolveTableExport(module(), tableIndex, name);
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

    public int tableElementType(int index) {
        final TableInfo table = tables[index];
        assert table != null;
        return table.elemType;
    }

    public Object tableInitialValue(int index) {
        final TableInfo table = tables[index];
        assert table != null;
        return table.initValue;
    }

    public byte[] tableInitializerBytecode(int index) {
        final TableInfo table = tables[index];
        assert table != null;
        return table.initBytecode;
    }

    private void ensureMemoryCapacity(int index) {
        if (index >= memories.length) {
            final MemoryInfo[] nMemories = new MemoryInfo[Math.max(Integer.highestOneBit(index) << 1, 2 * memories.length)];
            System.arraycopy(memories, 0, nMemories, 0, memories.length);
            memories = nMemories;
        }
    }

    public void allocateMemory(int index, long declaredMinSize, long declaredMaxSize, boolean indexType64, boolean shared, boolean multiMemory, boolean useUnsafeMemory,
                    boolean directByteBufferMemoryAccess) {
        checkNotParsed();
        addMemory(index, declaredMinSize, declaredMaxSize, indexType64, shared, multiMemory);
        module().addLinkAction((context, store, instance, imports) -> {
            module().limits().checkMemoryInstanceSize(declaredMinSize, indexType64);
            final WasmMemory wasmMemory;
            if (context.getContextOptions().memoryOverheadMode()) {
                // Initialize an empty memory when in memory overhead mode.
                wasmMemory = WasmMemoryFactory.createMemory(0, 0, false, false, useUnsafeMemory, directByteBufferMemoryAccess, context);
            } else {
                wasmMemory = WasmMemoryFactory.createMemory(declaredMinSize, declaredMaxSize, indexType64, shared, useUnsafeMemory, directByteBufferMemoryAccess, context);
            }
            instance.setMemory(index, wasmMemory);
        });
    }

    public void importMemory(String moduleName, String memoryName, int index, long initSize, long maxSize, boolean typeIndex64, boolean shared, boolean multiMemory) {
        checkNotParsed();
        addMemory(index, initSize, maxSize, typeIndex64, shared, multiMemory);
        final ImportDescriptor importedMemory = new ImportDescriptor(moduleName, memoryName, ImportIdentifier.MEMORY, index, numImportedSymbols());
        importedMemories.put(index, importedMemory);
        importSymbol(importedMemory);
        module().addLinkAction((context, store, instance, imports) -> {
            store.linker().resolveMemoryImport(store, instance, importedMemory, index, initSize, maxSize, typeIndex64, shared, imports);
        });
    }

    void addMemory(int index, long minSize, long maxSize, boolean indexType64, boolean shared, boolean multiMemory) {
        if (!multiMemory) {
            assertTrue(importedMemories.isEmpty(), "A memory has already been imported in the module.", Failure.MULTIPLE_MEMORIES);
            assertTrue(memoryCount == 0, "A memory has already been declared in the module.", Failure.MULTIPLE_MEMORIES);
        }
        ensureMemoryCapacity(index);
        final MemoryInfo memory = new MemoryInfo(minSize, maxSize, indexType64, shared);
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
        module().addLinkAction((context, store, instance, imports) -> {
            store.linker().resolveMemoryExport(instance, memoryIndex, name);
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

    private void ensureTagCapacity(int index) {
        if (index >= tags.length) {
            final TagInfo[] nTags = new TagInfo[Math.max(Integer.highestOneBit(index) << 1, 2 * tags.length)];
            System.arraycopy(tags, 0, nTags, 0, tags.length);
            tags = nTags;
        }
    }

    public void allocateTag(int index, byte attribute, int typeIndex) {
        checkNotParsed();
        addTag(index, attribute, typeIndex);
        module().addLinkAction((context, store, instance, imports) -> {
            final WasmTag tag = new WasmTag(closedTypeAt(typeIndex));
            instance.setTag(index, tag);
        });
    }

    public void importTag(String moduleName, String tagName, int index, byte attribute, int typeIndex) {
        checkNotParsed();
        addTag(index, attribute, typeIndex);
        final ImportDescriptor importedTag = new ImportDescriptor(moduleName, tagName, ImportIdentifier.TAG, index, numImportedSymbols());
        final DefinedType type = closedTypeAt(typeIndex);
        importedTags.put(index, importedTag);
        importSymbol(importedTag);
        module().addLinkAction((context, store, instance, imports) -> {
            store.linker().resolveTagImport(store, instance, importedTag, index, type, imports);
        });
    }

    void addTag(int index, byte attribute, int typeIndex) {
        assertIntEqual(functionTypeResultCount(typeIndex), 0, Failure.NON_EMPTY_TAG_RESULT_TYPE);
        ensureTagCapacity(index);
        final TagInfo tag = new TagInfo(attribute, typeIndex);
        tags[index] = tag;
        tagCount++;
    }

    public void checkTagIndex(int tagIndex) {
        assertUnsignedIntLess(tagIndex, tagCount, Failure.UNKNOWN_TAG);
    }

    public void exportTag(int tagIndex, String name) {
        checkNotParsed();
        exportSymbol(name);
        if (!checkExistingTagIndex(tagIndex)) {
            throw WasmException.create(Failure.UNKNOWN_TAG, "No tag with the specified index has been declared or imported, so it cannot be exported.");
        }
        exportedTags.put(name, tagIndex);
        module().addLinkAction((context, store, instance, imports) -> {
            store.linker().resolveTagExport(instance, tagIndex, name);
        });
    }

    public int tagCount() {
        return tagCount;
    }

    public ImportDescriptor importedTag(int index) {
        return importedTags.get(index);
    }

    public EconomicMap<ImportDescriptor, Integer> importTagDescriptors() {
        final EconomicMap<ImportDescriptor, Integer> reverseMap = EconomicMap.create();
        final MapCursor<Integer, ImportDescriptor> cursor = importedTags.getEntries();
        while (cursor.advance()) {
            reverseMap.put(cursor.getValue(), cursor.getKey());
        }
        return reverseMap;
    }

    public EconomicMap<String, Integer> exportedTags() {
        return exportedTags;
    }

    private boolean checkExistingTagIndex(int index) {
        return Integer.compareUnsigned(index, tagCount) < 0;
    }

    public int tagTypeIndex(int index) {
        assert index < tags.length;
        return tags[index].typeIndex();
    }

    public byte tagAttribute(int index) {
        assert index < tags.length;
        return tags[index].attribute();
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

    public void checkElemIndex(int elemIndex) {
        assertUnsignedIntLess(elemIndex, elemSegmentCount, Failure.UNKNOWN_ELEM_SEGMENT);
    }

    public void checkElemType(int elemIndex, int expectedType) {
        Assert.assertTrue(matchesType(expectedType, (int) elemInstances[elemIndex]), Failure.TYPE_MISMATCH);
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

    void setElemInstance(int index, int offset, int elemType) {
        ensureElemInstanceCapacity(index);
        elemInstances[index] = (long) offset << 32 | (elemType & 0xFFFF_FFFFL);
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

    /**
     * Assigns global addresses and trims symbol table after parsing.
     */
    public void finishSymbolTable() {
        CompilerAsserts.neverPartOfCompilation();
        assignGlobalAddresses();
        removeFunctionReferences();
    }

    private void assignGlobalAddresses() {
        CompilerAsserts.neverPartOfCompilation();
        assert numGlobals() == numInternalGlobals() + numExternalGlobals();
        this.globalAddresses = new int[numGlobals];
        int internalGlobalCount = 0;
        int externalGlobalCount = 0;
        for (int i = 0; i < numGlobals; i++) {
            if (!globalExternal(i)) {
                globalAddresses[i] = internalGlobalCount;
                internalGlobalCount++;
            } else {
                globalAddresses[i] = -externalGlobalCount - 1;
                externalGlobalCount++;
            }
        }
        assert internalGlobalCount == numInternalGlobals();
        assert externalGlobalCount == numExternalGlobals();
    }

    private void removeFunctionReferences() {
        CompilerAsserts.neverPartOfCompilation();
        functionReferences = null;
    }
}
