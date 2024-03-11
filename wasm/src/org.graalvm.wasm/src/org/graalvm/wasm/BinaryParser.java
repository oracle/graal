/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.wasm.Assert.assertIntLessOrEqual;
import static org.graalvm.wasm.Assert.assertTrue;
import static org.graalvm.wasm.Assert.assertUnsignedIntLess;
import static org.graalvm.wasm.Assert.assertUnsignedIntLessOrEqual;
import static org.graalvm.wasm.Assert.assertUnsignedLongLessOrEqual;
import static org.graalvm.wasm.Assert.fail;
import static org.graalvm.wasm.WasmType.EXTERNREF_TYPE;
import static org.graalvm.wasm.WasmType.F32_TYPE;
import static org.graalvm.wasm.WasmType.F64_TYPE;
import static org.graalvm.wasm.WasmType.FUNCREF_TYPE;
import static org.graalvm.wasm.WasmType.I32_TYPE;
import static org.graalvm.wasm.WasmType.I64_TYPE;
import static org.graalvm.wasm.WasmType.NULL_TYPE;
import static org.graalvm.wasm.WasmType.V128_TYPE;
import static org.graalvm.wasm.WasmType.VOID_TYPE;
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_64_DECLARATION_SIZE;
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_DECLARATION_SIZE;
import static org.graalvm.wasm.constants.Sizes.MAX_TABLE_DECLARATION_SIZE;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.api.Vector128Shape;
import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.constants.Bytecode;
import org.graalvm.wasm.constants.ExportIdentifier;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.constants.ImportIdentifier;
import org.graalvm.wasm.constants.Instructions;
import org.graalvm.wasm.constants.LimitsPrefix;
import org.graalvm.wasm.constants.Section;
import org.graalvm.wasm.constants.SegmentMode;
import org.graalvm.wasm.debugging.parser.DebugUtil;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.parser.bytecode.BytecodeGen;
import org.graalvm.wasm.parser.bytecode.BytecodeParser;
import org.graalvm.wasm.parser.bytecode.RuntimeBytecodeGen;
import org.graalvm.wasm.parser.ir.CallNode;
import org.graalvm.wasm.parser.ir.CodeEntry;
import org.graalvm.wasm.parser.validation.ParserState;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ExceptionType;

/**
 * Simple recursive-descend parser for the binary WebAssembly format.
 */
public class BinaryParser extends BinaryStreamParser {

    private static final int MAGIC = 0x6d736100;
    private static final int VERSION = 0x00000001;

    private final WasmModule module;
    private final WasmContext wasmContext;
    private final int[] multiResult;
    private final long[] longMultiResult;
    private final boolean[] booleanMultiResult;

    private final boolean multiValue;
    private final boolean bulkMemoryAndRefTypes;
    private final boolean memory64;
    private final boolean multiMemory;
    private final boolean threads;
    private final boolean simd;

    private final boolean unsafeMemory;

    @TruffleBoundary
    public BinaryParser(WasmModule module, WasmContext context, byte[] data) {
        super(data);
        this.module = module;
        this.wasmContext = context;
        this.multiResult = new int[2];
        this.longMultiResult = new long[2];
        this.booleanMultiResult = new boolean[2];
        this.multiValue = context.getContextOptions().supportMultiValue();
        this.bulkMemoryAndRefTypes = context.getContextOptions().supportBulkMemoryAndRefTypes();
        this.memory64 = context.getContextOptions().supportMemory64();
        this.multiMemory = context.getContextOptions().supportMultiMemory();
        this.threads = context.getContextOptions().supportThreads();
        this.simd = context.getContextOptions().supportSIMD();
        this.unsafeMemory = context.getContextOptions().useUnsafeMemory();
    }

    @TruffleBoundary
    public void readModule() {
        module.limits().checkModuleSize(data.length);
        validateMagicNumberAndVersion();
        readSymbolSections();
    }

    private void validateMagicNumberAndVersion() {
        assertIntEqual(read4(), MAGIC, Failure.INVALID_MAGIC_NUMBER);
        assertIntEqual(read4(), VERSION, Failure.INVALID_VERSION_NUMBER);
    }

    private void readSymbolSections() {
        int lastNonCustomSection = 0;
        int codeSectionOffset = -1;
        int codeSectionLength = 0;
        final RuntimeBytecodeGen bytecode = new RuntimeBytecodeGen();
        final BytecodeGen customData = new BytecodeGen();
        final BytecodeGen functionDebugData = new BytecodeGen();
        while (!isEOF()) {
            final byte sectionID = read1();

            if (sectionID != Section.CUSTOM) {
                if (Section.isNextSectionOrderValid(sectionID, lastNonCustomSection)) {
                    if (Integer.compareUnsigned(sectionID, Section.LAST_SECTION_ID) > 0) {
                        fail(Failure.MALFORMED_SECTION_ID, "invalid section ID: " + sectionID);
                    }
                    lastNonCustomSection = sectionID;
                } else if (Integer.compareUnsigned(sectionID, Section.LAST_SECTION_ID) > 0 || lastNonCustomSection == sectionID) {
                    throw WasmException.create(Failure.UNEXPECTED_CONTENT_AFTER_LAST_SECTION);
                } else {
                    throw WasmException.create(Failure.INVALID_SECTION_ORDER, "Section " + sectionID + " defined after section " + lastNonCustomSection);
                }
            }

            final int size = readLength();
            final int startOffset = offset;
            switch (sectionID) {
                case Section.CUSTOM:
                    readCustomSection(size, customData);
                    break;
                case Section.TYPE:
                    readTypeSection();
                    break;
                case Section.IMPORT:
                    readImportSection();
                    break;
                case Section.FUNCTION:
                    readFunctionSection();
                    break;
                case Section.TABLE:
                    readTableSection();
                    break;
                case Section.MEMORY:
                    readMemorySection();
                    break;
                case Section.GLOBAL:
                    readGlobalSection();
                    break;
                case Section.EXPORT:
                    readExportSection();
                    break;
                case Section.START:
                    readStartSection();
                    break;
                case Section.ELEMENT:
                    readElementSection(bytecode);
                    break;
                case Section.DATA_COUNT:
                    if (bulkMemoryAndRefTypes) {
                        readDataCountSection(size);
                    } else {
                        fail(Failure.MALFORMED_SECTION_ID, "invalid section ID: " + sectionID);
                    }
                    break;
                case Section.CODE:
                    codeSectionOffset = offset;
                    readCodeSection(bytecode, functionDebugData);
                    codeSectionLength = size;
                    break;
                case Section.DATA:
                    readDataSection(bytecode);
                    break;
            }
            assertIntEqual(offset - startOffset, size, String.format("Declared section (0x%02X) size is incorrect", sectionID), Failure.SECTION_SIZE_MISMATCH);
        }
        if (codeSectionOffset == -1) {
            assertIntEqual(module.numFunctions(), module.importedFunctions().size(), Failure.FUNCTIONS_CODE_INCONSISTENT_LENGTHS);
            codeSectionOffset = 0;
        }
        module.setBytecode(bytecode.toArray());
        module.removeFunctionReferences();
        module.setCustomData(customData.toArray());
        if (module.hasDebugInfo()) {
            functionDebugData.add(codeSectionLength);
            final byte[] codeSection = new byte[codeSectionLength + functionDebugData.location()];
            System.arraycopy(data, codeSectionOffset, codeSection, 0, codeSectionLength);
            System.arraycopy(functionDebugData.toArray(), 0, codeSection, codeSectionLength, functionDebugData.location());
            module.setCodeSection(codeSection);
        }
    }

    private void readCustomSection(int size, BytecodeGen customData) {
        final int sectionEndOffset = offset + size;
        final String name = readName();
        Assert.assertUnsignedIntLessOrEqual(sectionEndOffset, data.length, Failure.LENGTH_OUT_OF_BOUNDS);
        Assert.assertUnsignedIntLessOrEqual(offset, sectionEndOffset, Failure.UNEXPECTED_END);
        final int customDataSection = customData.location();
        final int sectionSize = sectionEndOffset - offset;
        customData.addBytes(data, offset, sectionSize);
        module.allocateCustomSection(name, customDataSection, sectionSize);
        if ("name".equals(name)) {
            try {
                readNameSection();
            } catch (WasmException ex) {
                // Malformed name section should not result in invalidation of the module
                assert ex.getExceptionType() == ExceptionType.PARSE_ERROR;
            }
        } else {
            readDebugSection(name, customDataSection, sectionSize, customData);
        }
        offset = sectionEndOffset;
    }

    /**
     * Reads possible debug sections and stores their offset in the custom data array.
     *
     * @param name the name of the custom section
     * @param size the size of the custom section excluding the name
     * @param customData the custom data
     */
    private void readDebugSection(String name, int sectionOffset, int size, BytecodeGen customData) {
        switch (name) {
            case DebugUtil.ABBREV_NAME:
                DebugUtil.setAbbrevOffset(customData, allocateDebugOffsets(customData), sectionOffset, size);
                break;
            case DebugUtil.INFO_NAME:
                DebugUtil.setInfo(customData, allocateDebugOffsets(customData), sectionOffset, size);
                break;
            case DebugUtil.LINE_NAME:
                DebugUtil.setLineOffset(customData, allocateDebugOffsets(customData), sectionOffset, size);
                break;
            case DebugUtil.LOC_NAME:
                DebugUtil.setLocOffset(customData, allocateDebugOffsets(customData), sectionOffset, size);
                break;
            case DebugUtil.RANGES_NAME:
                DebugUtil.setRangesOffset(customData, allocateDebugOffsets(customData), sectionOffset, size);
                break;
            case DebugUtil.STR_NAME:
                DebugUtil.setStrOffset(customData, allocateDebugOffsets(customData), sectionOffset, size);
                break;
        }
    }

    /**
     * Allocates space for the debug information in the custom data array.
     */
    private int allocateDebugOffsets(BytecodeGen customData) {
        if (module.hasDebugInfo()) {
            return module.debugInfoOffset();
        }
        final int location = customData.location();
        customData.allocate(DebugUtil.CUSTOM_DATA_SIZE);
        DebugUtil.initializeData(customData, location);
        module.setDebugInfoOffset(location);
        return location;
    }

    /**
     * @see <a href=
     *      "https://webassembly.github.io/spec/core/appendix/custom.html#binary-namesubsection"><code>namedata</code>
     *      binary specification</a>
     */
    private void readNameSection() {
        if (!isEOF() && peek1() == 0) {
            readModuleName();
        }
        if (!isEOF() && peek1() == 1) {
            readFunctionNames();
        }
        if (!isEOF() && peek1() == 2) {
            readLocalNames();
        }
    }

    /**
     * @see <a href=
     *      "https://webassembly.github.io/spec/core/appendix/custom.html#binary-modulenamesec"><code>modulenamesubsec</code>
     *      binary specification</a>
     */
    private void readModuleName() {
        final int subsectionId = read1();
        assert subsectionId == 0;
        final int size = readLength();
        // We don't currently use debug module name.
        offset += size;
    }

    /**
     * @see <a href=
     *      "https://webassembly.github.io/spec/core/appendix/custom.html#binary-funcnamesec"><code>funcnamesubsec</code>
     *      binary specification</a>
     */
    private void readFunctionNames() {
        final int subsectionId = read1();
        assert subsectionId == 1;
        final int size = readLength();
        final int startOffset = offset;
        final int length = readLength();
        final int maxFunctionIndex = module.numFunctions() - 1;
        for (int i = 0; i < length; ++i) {
            final int functionIndex = readFunctionIndex();
            assertIntLessOrEqual(0, functionIndex, "Negative function index", Failure.UNSPECIFIED_MALFORMED);
            assertIntLessOrEqual(functionIndex, maxFunctionIndex, "Function index too large", Failure.UNSPECIFIED_MALFORMED);
            final String functionName = readName();
            module.function(functionIndex).setDebugName(functionName);
        }
        assertIntEqual(offset - startOffset, size, Failure.SECTION_SIZE_MISMATCH);
    }

    /**
     * @see <a href=
     *      "https://webassembly.github.io/spec/core/appendix/custom.html#local-names"><code>localnamesubsec</code>
     *      binary specification</a>
     */
    private void readLocalNames() {
        final int subsectionId = read1();
        assert subsectionId == 2;
        final int size = readLength();
        // We don't currently use debug local names.
        offset += size;
    }

    private void readTypeSection() {
        final int typeCount = readLength();
        module.limits().checkTypeCount(typeCount);
        for (int typeIndex = 0; typeIndex != typeCount; typeIndex++) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            final byte type = read1();
            if (type == 0x60) {
                readFunctionType();
            } else {
                // According to the official tests this should be an integer presentation too long
                // error
                fail(Failure.INTEGER_REPRESENTATION_TOO_LONG, "Only function types are supported in the type section");
            }
        }
    }

    private void readImportSection() {
        assertIntEqual(module.symbolTable().numGlobals(), 0,
                        "The global index should be -1 when the import section is first read.", Failure.UNSPECIFIED_INVALID);
        int importCount = readLength();

        module.limits().checkImportCount(importCount);
        for (int importIndex = 0; importIndex != importCount; importIndex++) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            String moduleName = readName();
            String memberName = readName();
            byte importType = readImportType();
            switch (importType) {
                case ImportIdentifier.FUNCTION: {
                    int typeIndex = readTypeIndex();
                    module.symbolTable().importFunction(moduleName, memberName, typeIndex);
                    break;
                }
                case ImportIdentifier.TABLE: {
                    final byte elemType = readRefType();
                    if (!bulkMemoryAndRefTypes) {
                        assertByteEqual(elemType, FUNCREF_TYPE, "Invalid element type for table import", Failure.UNSPECIFIED_MALFORMED);
                    }
                    readTableLimits(multiResult);
                    final int tableIndex = module.tableCount();
                    module.symbolTable().importTable(moduleName, memberName, tableIndex, multiResult[0], multiResult[1], elemType, bulkMemoryAndRefTypes);
                    break;
                }
                case ImportIdentifier.MEMORY: {
                    readMemoryLimits(longMultiResult, booleanMultiResult);
                    final int memoryIndex = module.memoryCount();
                    final boolean is64Bit = booleanMultiResult[0];
                    final boolean isShared = booleanMultiResult[1];
                    final boolean useUnsafeMemory = wasmContext.getContextOptions().useUnsafeMemory();
                    module.symbolTable().importMemory(moduleName, memberName, memoryIndex, longMultiResult[0], longMultiResult[1], is64Bit, isShared, multiMemory, useUnsafeMemory);
                    break;
                }
                case ImportIdentifier.GLOBAL: {
                    byte type = readValueType(bulkMemoryAndRefTypes, simd);
                    byte mutability = readMutability();
                    int index = module.symbolTable().numGlobals();
                    module.symbolTable().importGlobal(moduleName, memberName, index, type, mutability);
                    break;
                }
                default: {
                    fail(Failure.MALFORMED_IMPORT_KIND, String.format("Invalid import type identifier: 0x%02X", importType));
                }
            }
        }
    }

    private void readFunctionSection() {
        int functionCount = readLength();
        module.limits().checkFunctionCount(functionCount);
        for (int functionIndex = 0; functionIndex != functionCount; functionIndex++) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            int functionTypeIndex = readUnsignedInt32();
            module.symbolTable().declareFunction(functionTypeIndex);
        }
    }

    private void readTableSection() {
        final int tableCount = readLength();
        final int startingTableIndex = module.tableCount();
        module.limits().checkTableCount(startingTableIndex + tableCount);
        for (int tableIndex = startingTableIndex; tableIndex != startingTableIndex + tableCount; tableIndex++) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            final byte elemType = readRefType();
            readTableLimits(multiResult);
            module.symbolTable().allocateTable(tableIndex, multiResult[0], multiResult[1], elemType, bulkMemoryAndRefTypes);
        }
    }

    private void readMemorySection() {
        final int memoryCount = readLength();
        final int startingMemoryIndex = module.memoryCount();
        module.limits().checkMemoryCount(startingMemoryIndex + memoryCount, multiMemory);
        for (int memoryIndex = startingMemoryIndex; memoryIndex != startingMemoryIndex + memoryCount; memoryIndex++) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            readMemoryLimits(longMultiResult, booleanMultiResult);
            final boolean is64Bit = booleanMultiResult[0];
            final boolean isShared = booleanMultiResult[1];
            final boolean useUnsafeMemory = wasmContext.getContextOptions().useUnsafeMemory();
            module.symbolTable().allocateMemory(memoryIndex, longMultiResult[0], longMultiResult[1], is64Bit, isShared, multiMemory, useUnsafeMemory);
        }
    }

    private void readCodeSection(RuntimeBytecodeGen bytecode, BytecodeGen functionDebugData) {
        final int codeSectionOffset = offset;
        final int importedFunctionCount = module.importedFunctions().size();
        final int codeEntryCount = readLength();
        final int expectedCodeEntryCount = module.numFunctions() - importedFunctionCount;
        assertIntEqual(codeEntryCount, expectedCodeEntryCount, Failure.FUNCTIONS_CODE_INCONSISTENT_LENGTHS);
        final CodeEntry[] codeEntries = new CodeEntry[codeEntryCount];
        for (int entryIndex = 0; entryIndex != codeEntryCount; entryIndex++) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            final int codeEntrySize = readUnsignedInt32();
            final int startOffset = offset;
            module.limits().checkFunctionSize(codeEntrySize);
            final ByteArrayList locals = readCodeEntryLocals();
            final int localCount = locals.size() + module.function(importedFunctionCount + entryIndex).paramCount();
            module.limits().checkLocalCount(localCount);
            // Store the function start offset, instruction start offset, and function end offset.
            functionDebugData.add(startOffset - codeSectionOffset);
            functionDebugData.add(offset - codeSectionOffset);
            codeEntries[entryIndex] = readCodeEntry(importedFunctionCount + entryIndex, locals, startOffset + codeEntrySize, entryIndex < codeEntryCount - 1, bytecode, entryIndex);
            functionDebugData.add(offset - codeSectionOffset);
            assertIntEqual(offset - startOffset, codeEntrySize, String.format("Code entry %d size is incorrect", entryIndex), Failure.UNSPECIFIED_MALFORMED);
        }
        module.setCodeEntries(codeEntries);
    }

    private CodeEntry readCodeEntry(int functionIndex, ByteArrayList locals, int endOffset, boolean hasNextFunction, RuntimeBytecodeGen bytecode, int codeEntryIndex) {
        final WasmFunction function = module.symbolTable().function(functionIndex);
        int paramCount = function.paramCount();
        byte[] localTypes = new byte[function.paramCount() + locals.size()];
        for (int index = 0; index != paramCount; index++) {
            localTypes[index] = function.paramTypeAt(index);
        }
        for (int index = 0; index != locals.size(); index++) {
            localTypes[index + paramCount] = locals.get(index);
        }
        byte[] resultTypes = new byte[function.resultCount()];
        for (int index = 0; index != resultTypes.length; index++) {
            resultTypes[index] = function.resultTypeAt(index);
        }
        return readFunction(functionIndex, localTypes, resultTypes, endOffset, hasNextFunction, bytecode, codeEntryIndex, null);
    }

    private ByteArrayList readCodeEntryLocals() {
        final int localsGroupCount = readLength();
        final ByteArrayList localTypes = new ByteArrayList();
        int localsLength = 0;
        for (int localGroup = 0; localGroup != localsGroupCount; localGroup++) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            final int groupLength = readUnsignedInt32();
            localsLength += groupLength;
            module.limits().checkLocalCount(localsLength);
            final byte t = readValueType(bulkMemoryAndRefTypes, simd);
            for (int i = 0; i != groupLength; ++i) {
                localTypes.add(t);
            }
        }
        return localTypes;
    }

    private byte[] extractBlockParamTypes(int typeIndex) {
        int paramCount = module.functionTypeParamCount(typeIndex);
        byte[] params = new byte[paramCount];
        for (int i = 0; i < paramCount; i++) {
            params[i] = module.functionTypeParamTypeAt(typeIndex, i);
        }
        return params;
    }

    private byte[] extractBlockResultTypes(int typeIndex) {
        int resultCount = module.functionTypeResultCount(typeIndex);
        byte[] results = new byte[resultCount];
        for (int i = 0; i < resultCount; i++) {
            results[i] = module.functionTypeResultTypeAt(typeIndex, i);
        }
        return results;
    }

    private static byte[] encapsulateResultType(int type) {
        switch (type) {
            case VOID_TYPE:
                return WasmType.VOID_TYPE_ARRAY;
            case I32_TYPE:
                return WasmType.I32_TYPE_ARRAY;
            case I64_TYPE:
                return WasmType.I64_TYPE_ARRAY;
            case F32_TYPE:
                return WasmType.F32_TYPE_ARRAY;
            case F64_TYPE:
                return WasmType.F64_TYPE_ARRAY;
            case V128_TYPE:
                return WasmType.V128_TYPE_ARRAY;
            case FUNCREF_TYPE:
                return WasmType.FUNCREF_TYPE_ARRAY;
            case EXTERNREF_TYPE:
                return WasmType.EXTERNREF_TYPE_ARRAY;
            default:
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL);
        }
    }

    private CodeEntry readFunction(int functionIndex, byte[] locals, byte[] resultTypes, int sourceCodeEndOffset, boolean hasNextFunction, RuntimeBytecodeGen bytecode,
                    int codeEntryIndex, EconomicMap<Integer, Integer> sourceLocationToLineMap) {
        final ParserState state = new ParserState(bytecode);
        final ArrayList<CallNode> callNodes = new ArrayList<>();
        final int bytecodeStartOffset = bytecode.location();
        state.enterFunction(resultTypes);

        int opcode;
        while (offset < sourceCodeEndOffset) {
            // Insert a debug instruction if a line mapping exists.
            if (sourceLocationToLineMap != null) {
                if (sourceLocationToLineMap.containsKey(offset)) {
                    bytecode.addNotify(sourceLocationToLineMap.get(offset), offset);
                }
            }

            opcode = read1() & 0xFF;
            switch (opcode) {
                case Instructions.UNREACHABLE:
                    state.setUnreachable();
                    state.addInstruction(Bytecode.UNREACHABLE);
                    break;
                case Instructions.NOP:
                    state.addInstruction(Bytecode.NOP);
                    break;
                case Instructions.BLOCK: {
                    final byte[] blockParamTypes;
                    final byte[] blockResultTypes;
                    readBlockType(multiResult, bulkMemoryAndRefTypes, simd);
                    // Extract value based on result arity.
                    if (multiResult[1] == SINGLE_RESULT_VALUE) {
                        blockParamTypes = WasmType.VOID_TYPE_ARRAY;
                        blockResultTypes = encapsulateResultType(multiResult[0]);
                    } else if (multiValue) {
                        blockParamTypes = extractBlockParamTypes(multiResult[0]);
                        blockResultTypes = extractBlockResultTypes(multiResult[0]);
                    } else {
                        throw WasmException.create(Failure.DISABLED_MULTI_VALUE);
                    }
                    state.popAll(blockParamTypes);
                    state.enterBlock(blockParamTypes, blockResultTypes);
                    break;
                }
                case Instructions.LOOP: {
                    // Jumps are targeting the loop instruction for OSR.
                    final byte[] loopParamTypes;
                    final byte[] loopResultTypes;
                    readBlockType(multiResult, bulkMemoryAndRefTypes, simd);
                    // Extract value based on result arity.
                    if (multiResult[1] == SINGLE_RESULT_VALUE) {
                        loopParamTypes = WasmType.VOID_TYPE_ARRAY;
                        loopResultTypes = encapsulateResultType(multiResult[0]);
                    } else if (multiValue) {
                        loopParamTypes = extractBlockParamTypes(multiResult[0]);
                        loopResultTypes = extractBlockResultTypes(multiResult[0]);
                    } else {
                        throw WasmException.create(Failure.DISABLED_MULTI_VALUE);
                    }
                    state.popAll(loopParamTypes);
                    state.enterLoop(loopParamTypes, loopResultTypes);
                    break;
                }
                case Instructions.IF: {
                    state.popChecked(I32_TYPE); // condition
                    final byte[] ifParamTypes;
                    final byte[] ifResultTypes;
                    readBlockType(multiResult, bulkMemoryAndRefTypes, simd);
                    // Extract value based on result arity.
                    if (multiResult[1] == SINGLE_RESULT_VALUE) {
                        ifParamTypes = WasmType.VOID_TYPE_ARRAY;
                        ifResultTypes = encapsulateResultType(multiResult[0]);
                    } else if (multiValue) {
                        ifParamTypes = extractBlockParamTypes(multiResult[0]);
                        ifResultTypes = extractBlockResultTypes(multiResult[0]);
                    } else {
                        throw WasmException.create(Failure.DISABLED_MULTI_VALUE);
                    }
                    state.popAll(ifParamTypes);
                    state.enterIf(ifParamTypes, ifResultTypes);
                    break;
                }
                case Instructions.END: {
                    state.exit(multiValue);
                    break;
                }
                case Instructions.ELSE: {
                    state.enterElse();
                    break;
                }
                case Instructions.BR: {
                    final int branchLabel = readTargetOffset();
                    state.addUnconditionalBranch(branchLabel);

                    // This instruction is stack-polymorphic
                    state.setUnreachable();
                    break;
                }
                case Instructions.BR_IF: {
                    final int branchLabel = readTargetOffset();
                    state.popChecked(I32_TYPE); // condition
                    state.addConditionalBranch(branchLabel);

                    break;
                }
                case Instructions.BR_TABLE: {
                    state.popChecked(I32_TYPE); // index
                    final int length = readLength();

                    final int[] branchTable = new int[length + 1];
                    for (int i = 0; i != length + 1; ++i) {
                        final int branchLabel = readTargetOffset();
                        branchTable[i] = branchLabel;
                    }
                    state.addBranchTable(branchTable);

                    // This instruction is stack-polymorphic
                    state.setUnreachable();
                    break;
                }
                case Instructions.RETURN: {
                    state.addReturn(multiValue);

                    // This instruction is stack-polymorphic
                    state.setUnreachable();
                    break;
                }
                case Instructions.CALL: {
                    final int callFunctionIndex = readDeclaredFunctionIndex();

                    // Pop parameters
                    final WasmFunction function = module.function(callFunctionIndex);
                    byte[] params = new byte[function.paramCount()];
                    for (int i = function.paramCount() - 1; i >= 0; --i) {
                        params[i] = function.paramTypeAt(i);
                    }
                    state.checkParamTypes(params);

                    // Push result values
                    if (!multiValue) {
                        assertIntLessOrEqual(function.resultCount(), 1, Failure.INVALID_RESULT_ARITY);
                    }
                    state.pushAll(function.type().resultTypes());
                    state.addCall(callNodes.size(), callFunctionIndex);
                    callNodes.add(new CallNode(callFunctionIndex));
                    break;
                }
                case Instructions.CALL_INDIRECT: {
                    final int expectedFunctionTypeIndex = readUnsignedInt32();
                    final int tableIndex = readTableIndex();
                    // Pop the function index to call
                    state.popChecked(I32_TYPE);
                    state.checkFunctionTypeExists(expectedFunctionTypeIndex, module.typeCount());
                    assertByteEqual(FUNCREF_TYPE, module.tableElementType(tableIndex), Failure.TYPE_MISMATCH);

                    // Pop parameters
                    for (int i = module.functionTypeParamCount(expectedFunctionTypeIndex) - 1; i >= 0; --i) {
                        state.popChecked(module.functionTypeParamTypeAt(expectedFunctionTypeIndex, i));
                    }
                    // Push result values
                    final int resultCount = module.functionTypeResultCount(expectedFunctionTypeIndex);
                    if (!multiValue) {
                        assertIntLessOrEqual(resultCount, 1, Failure.INVALID_RESULT_ARITY);
                    }
                    byte[] callResultTypes = new byte[resultCount];
                    for (int i = 0; i < resultCount; i++) {
                        callResultTypes[i] = module.functionTypeResultTypeAt(expectedFunctionTypeIndex, i);
                    }
                    state.pushAll(callResultTypes);
                    state.addIndirectCall(callNodes.size(), expectedFunctionTypeIndex, tableIndex);
                    callNodes.add(new CallNode());
                    break;
                }
                case Instructions.DROP:
                    final byte type = state.pop();
                    if (WasmType.isNumberType(type)) {
                        state.addInstruction(Bytecode.DROP);
                    } else {
                        state.addInstruction(Bytecode.DROP_OBJ);
                    }
                    break;
                case Instructions.SELECT: {
                    state.popChecked(I32_TYPE); // condition
                    final byte t1 = state.pop(); // first operand
                    final byte t2 = state.pop(); // second operand
                    assertTrue(WasmType.isNumberType(t1) && WasmType.isNumberType(t2), Failure.TYPE_MISMATCH);
                    assertTrue(t1 == t2 || t1 == WasmType.UNKNOWN_TYPE || t2 == WasmType.UNKNOWN_TYPE, Failure.TYPE_MISMATCH);
                    state.push(t1 == WasmType.UNKNOWN_TYPE ? t2 : t1);
                    state.addInstruction(Bytecode.SELECT);
                    break;
                }
                case Instructions.SELECT_T: {
                    checkBulkMemoryAndRefTypesSupport(opcode);
                    final int length = readLength();
                    assertIntEqual(length, 1, Failure.INVALID_RESULT_ARITY);
                    final byte t = readValueType(bulkMemoryAndRefTypes, simd);
                    state.popChecked(I32_TYPE);
                    state.popChecked(t);
                    state.popChecked(t);
                    state.push(t);
                    if (WasmType.isNumberType(t)) {
                        state.addInstruction(Bytecode.SELECT);
                    } else {
                        state.addInstruction(Bytecode.SELECT_OBJ);
                    }
                    break;
                }
                case Instructions.LOCAL_GET: {
                    final int localIndex = readLocalIndex();
                    assertUnsignedIntLess(localIndex, locals.length, Failure.UNKNOWN_LOCAL);
                    final byte localType = locals[localIndex];
                    state.push(localType);
                    if (WasmType.isNumberType(localType)) {
                        state.addUnsignedInstruction(Bytecode.LOCAL_GET_U8, localIndex);
                    } else {
                        state.addUnsignedInstruction(Bytecode.LOCAL_GET_OBJ_U8, localIndex);
                    }
                    break;
                }
                case Instructions.LOCAL_SET: {
                    final int localIndex = readLocalIndex();
                    assertUnsignedIntLess(localIndex, locals.length, Failure.UNKNOWN_LOCAL);
                    final byte localType = locals[localIndex];
                    state.popChecked(localType);
                    if (WasmType.isNumberType(localType)) {
                        state.addUnsignedInstruction(Bytecode.LOCAL_SET_U8, localIndex);
                    } else {
                        state.addUnsignedInstruction(Bytecode.LOCAL_SET_OBJ_U8, localIndex);
                    }
                    break;
                }
                case Instructions.LOCAL_TEE: {
                    final int localIndex = readLocalIndex();
                    assertUnsignedIntLess(localIndex, locals.length, Failure.UNKNOWN_LOCAL);
                    final byte localType = locals[localIndex];
                    state.popChecked(localType);
                    state.push(localType);
                    if (WasmType.isNumberType(localType)) {
                        state.addUnsignedInstruction(Bytecode.LOCAL_TEE_U8, localIndex);
                    } else {
                        state.addUnsignedInstruction(Bytecode.LOCAL_TEE_OBJ_U8, localIndex);
                    }
                    break;
                }
                case Instructions.GLOBAL_GET: {
                    final int index = readGlobalIndex();
                    state.push(module.symbolTable().globalValueType(index));
                    state.addUnsignedInstruction(Bytecode.GLOBAL_GET_U8, index);
                    break;
                }
                case Instructions.GLOBAL_SET: {
                    final int index = readGlobalIndex();
                    // Assert that the global is mutable.
                    assertByteEqual(module.symbolTable().globalMutability(index), GlobalModifier.MUTABLE,
                                    "Immutable globals cannot be set: " + index, Failure.IMMUTABLE_GLOBAL_WRITE);
                    state.popChecked(module.symbolTable().globalValueType(index));
                    state.addUnsignedInstruction(Bytecode.GLOBAL_SET_U8, index);
                    break;
                }
                case Instructions.TABLE_GET: {
                    checkBulkMemoryAndRefTypesSupport(opcode);
                    final int index = readTableIndex();
                    final byte elementType = module.tableElementType(index);
                    state.popChecked(I32_TYPE);
                    state.push(elementType);
                    state.addInstruction(Bytecode.TABLE_GET, index);
                    break;
                }
                case Instructions.TABLE_SET: {
                    checkBulkMemoryAndRefTypesSupport(opcode);
                    final int index = readTableIndex();
                    final byte elementType = module.tableElementType(index);
                    state.popChecked(elementType);
                    state.popChecked(I32_TYPE);
                    state.addInstruction(Bytecode.TABLE_SET, index);
                    break;
                }
                case Instructions.F32_LOAD:
                    load(state, F32_TYPE, 32, longMultiResult);
                    state.addMemoryInstruction(Bytecode.F32_LOAD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.F64_LOAD:
                    load(state, F64_TYPE, 64, longMultiResult);
                    state.addMemoryInstruction(Bytecode.F64_LOAD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I32_LOAD:
                    load(state, I32_TYPE, 32, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I32_LOAD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I32_LOAD8_S:
                    load(state, I32_TYPE, 8, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I32_LOAD8_S, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I32_LOAD8_U:
                    load(state, I32_TYPE, 8, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I32_LOAD8_U, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I32_LOAD16_S:
                    load(state, I32_TYPE, 16, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I32_LOAD16_S, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I32_LOAD16_U:
                    load(state, I32_TYPE, 16, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I32_LOAD16_U, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I64_LOAD:
                    load(state, I64_TYPE, 64, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I64_LOAD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I64_LOAD8_S:
                    load(state, I64_TYPE, 8, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I64_LOAD8_S, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I64_LOAD8_U:
                    load(state, I64_TYPE, 8, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I64_LOAD8_U, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I64_LOAD16_S:
                    load(state, I64_TYPE, 16, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I64_LOAD16_S, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I64_LOAD16_U:
                    load(state, I64_TYPE, 16, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I64_LOAD16_U, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I64_LOAD32_S:
                    load(state, I64_TYPE, 32, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I64_LOAD32_S, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I64_LOAD32_U:
                    load(state, I64_TYPE, 32, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I64_LOAD32_U, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.F32_STORE:
                    store(state, F32_TYPE, 32, longMultiResult);
                    state.addMemoryInstruction(Bytecode.F32_STORE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.F64_STORE:
                    store(state, F64_TYPE, 64, longMultiResult);
                    state.addMemoryInstruction(Bytecode.F64_STORE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I32_STORE:
                    store(state, I32_TYPE, 32, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I32_STORE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I32_STORE_8:
                    store(state, I32_TYPE, 8, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I32_STORE_8, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I32_STORE_16:
                    store(state, I32_TYPE, 16, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I32_STORE_16, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I64_STORE:
                    store(state, I64_TYPE, 64, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I64_STORE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I64_STORE_8:
                    store(state, I64_TYPE, 8, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I64_STORE_8, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I64_STORE_16:
                    store(state, I64_TYPE, 16, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I64_STORE_16, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.I64_STORE_32:
                    store(state, I64_TYPE, 32, longMultiResult);
                    state.addMemoryInstruction(Bytecode.I64_STORE_32, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                    break;
                case Instructions.MEMORY_SIZE: {
                    final int memoryIndex;
                    if (multiMemory) {
                        memoryIndex = readMemoryIndex();
                    } else {
                        memoryIndex = read1();
                        assertIntEqual(memoryIndex, 0, Failure.ZERO_BYTE_EXPECTED);
                        checkMemoryIndex(0);
                    }
                    if (module.memoryHasIndexType64(memoryIndex) && memory64) {
                        state.push(I64_TYPE);
                        state.addMiscFlag();
                        state.addInstruction(Bytecode.MEMORY64_SIZE, memoryIndex);
                    } else {
                        state.push(I32_TYPE);
                        state.addInstruction(Bytecode.MEMORY_SIZE, memoryIndex);
                    }
                    break;
                }
                case Instructions.MEMORY_GROW: {
                    final int memoryIndex;
                    if (multiMemory) {
                        memoryIndex = readMemoryIndex();
                    } else {
                        memoryIndex = read1();
                        assertIntEqual(memoryIndex, 0, Failure.ZERO_BYTE_EXPECTED);
                        checkMemoryIndex(0);
                    }
                    if (module.memoryHasIndexType64(memoryIndex) && memory64) {
                        state.popChecked(I64_TYPE);
                        state.push(I64_TYPE);
                        state.addMiscFlag();
                        state.addInstruction(Bytecode.MEMORY64_GROW, memoryIndex);
                    } else {
                        state.popChecked(I32_TYPE);
                        state.push(I32_TYPE);
                        state.addInstruction(Bytecode.MEMORY_GROW, memoryIndex);
                    }
                    break;
                }
                default:
                    readNumericInstructions(state, opcode);
                    break;

            }
        }
        assertIntEqual(state.valueStackSize(), resultTypes.length,
                        "Stack size must match the return type length at the function end", Failure.TYPE_MISMATCH);
        if (hasNextFunction) {
            assertIntEqual(state.controlStackSize(), 0, Failure.END_OPCODE_EXPECTED);
        } else {
            if (state.controlStackSize() != 0) {
                // Check if we reached the end of the binary
                peek1();
                fail(Failure.SECTION_SIZE_MISMATCH, "END opcode expected");
            }
        }
        final int bytecodeEndOffset = bytecode.location();
        bytecode.addCodeEntry(functionIndex, state.maxStackSize(), bytecodeEndOffset - bytecodeStartOffset, locals.length, resultTypes.length);
        for (byte local : locals) {
            bytecode.addByte(local);
        }
        if (locals.length != 0) {
            bytecode.addByte((byte) 0);
        }
        for (byte result : resultTypes) {
            bytecode.addByte(result);
        }
        if (resultTypes.length != 0) {
            bytecode.addByte((byte) 0);
        }
        if (sourceLocationToLineMap == null) {
            // Do not override the code entry offset when rereading the function.
            module.setCodeEntryOffset(codeEntryIndex, bytecodeEndOffset);
        }
        return new CodeEntry(functionIndex, state.maxStackSize(), locals, resultTypes, callNodes, bytecodeStartOffset, bytecodeEndOffset);
    }

    private void readNumericInstructions(ParserState state, int opcode) {
        switch (opcode) {
            case Instructions.I32_CONST: {
                final int value = readSignedInt32();
                state.push(I32_TYPE);
                state.addSignedInstruction(Bytecode.I32_CONST_I8, value);
                break;
            }
            case Instructions.I64_CONST: {
                final long value = readSignedInt64();
                state.push(I64_TYPE);
                state.addSignedInstruction(Bytecode.I64_CONST_I8, value);
                break;
            }
            case Instructions.F32_CONST: {
                final int value = read4();
                state.push(F32_TYPE);
                state.addInstruction(Bytecode.F32_CONST, value);
                break;
            }
            case Instructions.F64_CONST: {
                final long value = read8();
                state.push(F64_TYPE);
                state.addInstruction(Bytecode.F64_CONST, value);
                break;
            }
            case Instructions.I32_EQZ:
                state.popChecked(I32_TYPE);
                state.push(I32_TYPE);
                state.addInstruction(Bytecode.I32_EQZ);
                break;
            case Instructions.I32_EQ:
            case Instructions.I32_NE:
            case Instructions.I32_LT_S:
            case Instructions.I32_LT_U:
            case Instructions.I32_GT_S:
            case Instructions.I32_GT_U:
            case Instructions.I32_LE_S:
            case Instructions.I32_LE_U:
            case Instructions.I32_GE_S:
            case Instructions.I32_GE_U:
            case Instructions.I32_ADD:
            case Instructions.I32_SUB:
            case Instructions.I32_MUL:
            case Instructions.I32_DIV_S:
            case Instructions.I32_DIV_U:
            case Instructions.I32_REM_S:
            case Instructions.I32_REM_U:
            case Instructions.I32_AND:
            case Instructions.I32_OR:
            case Instructions.I32_XOR:
            case Instructions.I32_SHL:
            case Instructions.I32_SHR_S:
            case Instructions.I32_SHR_U:
            case Instructions.I32_ROTL:
            case Instructions.I32_ROTR:
                state.popChecked(I32_TYPE);
                state.popChecked(I32_TYPE);
                state.push(I32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.I64_EQZ:
                state.popChecked(I64_TYPE);
                state.push(I32_TYPE);
                state.addInstruction(Bytecode.I64_EQZ);
                break;
            case Instructions.I64_EQ:
            case Instructions.I64_NE:
            case Instructions.I64_LT_S:
            case Instructions.I64_LT_U:
            case Instructions.I64_GT_S:
            case Instructions.I64_GT_U:
            case Instructions.I64_LE_S:
            case Instructions.I64_LE_U:
            case Instructions.I64_GE_S:
            case Instructions.I64_GE_U:
                state.popChecked(I64_TYPE);
                state.popChecked(I64_TYPE);
                state.push(I32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.F32_EQ:
            case Instructions.F32_NE:
            case Instructions.F32_LT:
            case Instructions.F32_GT:
            case Instructions.F32_LE:
            case Instructions.F32_GE:
                state.popChecked(F32_TYPE);
                state.popChecked(F32_TYPE);
                state.push(I32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.F64_EQ:
            case Instructions.F64_NE:
            case Instructions.F64_LT:
            case Instructions.F64_GT:
            case Instructions.F64_LE:
            case Instructions.F64_GE:
                state.popChecked(F64_TYPE);
                state.popChecked(F64_TYPE);
                state.push(I32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.I32_CLZ:
            case Instructions.I32_CTZ:
            case Instructions.I32_POPCNT:
                state.popChecked(I32_TYPE);
                state.push(I32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.I64_CLZ:
            case Instructions.I64_CTZ:
            case Instructions.I64_POPCNT:
                state.popChecked(I64_TYPE);
                state.push(I64_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.I64_ADD:
            case Instructions.I64_SUB:
            case Instructions.I64_MUL:
            case Instructions.I64_DIV_S:
            case Instructions.I64_DIV_U:
            case Instructions.I64_REM_S:
            case Instructions.I64_REM_U:
            case Instructions.I64_AND:
            case Instructions.I64_OR:
            case Instructions.I64_XOR:
            case Instructions.I64_SHL:
            case Instructions.I64_SHR_S:
            case Instructions.I64_SHR_U:
            case Instructions.I64_ROTL:
            case Instructions.I64_ROTR:
                state.popChecked(I64_TYPE);
                state.popChecked(I64_TYPE);
                state.push(I64_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.F32_ABS:
            case Instructions.F32_NEG:
            case Instructions.F32_CEIL:
            case Instructions.F32_FLOOR:
            case Instructions.F32_TRUNC:
            case Instructions.F32_NEAREST:
            case Instructions.F32_SQRT:
                state.popChecked(F32_TYPE);
                state.push(F32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.F32_ADD:
            case Instructions.F32_SUB:
            case Instructions.F32_MUL:
            case Instructions.F32_DIV:
            case Instructions.F32_MIN:
            case Instructions.F32_MAX:
            case Instructions.F32_COPYSIGN:
                state.popChecked(F32_TYPE);
                state.popChecked(F32_TYPE);
                state.push(F32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.F64_ABS:
            case Instructions.F64_NEG:
            case Instructions.F64_CEIL:
            case Instructions.F64_FLOOR:
            case Instructions.F64_TRUNC:
            case Instructions.F64_NEAREST:
            case Instructions.F64_SQRT:
                state.popChecked(F64_TYPE);
                state.push(F64_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.F64_ADD:
            case Instructions.F64_SUB:
            case Instructions.F64_MUL:
            case Instructions.F64_DIV:
            case Instructions.F64_MIN:
            case Instructions.F64_MAX:
            case Instructions.F64_COPYSIGN:
                state.popChecked(F64_TYPE);
                state.popChecked(F64_TYPE);
                state.push(F64_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.I32_WRAP_I64:
                state.popChecked(I64_TYPE);
                state.push(I32_TYPE);
                state.addInstruction(Bytecode.I32_WRAP_I64);
                break;
            case Instructions.I32_TRUNC_F32_S:
            case Instructions.I32_TRUNC_F32_U:
            case Instructions.I32_REINTERPRET_F32:
                state.popChecked(F32_TYPE);
                state.push(I32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.I32_TRUNC_F64_S:
            case Instructions.I32_TRUNC_F64_U:
                state.popChecked(F64_TYPE);
                state.push(I32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.I64_EXTEND_I32_S:
            case Instructions.I64_EXTEND_I32_U:
                state.popChecked(I32_TYPE);
                state.push(I64_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.I64_TRUNC_F32_S:
            case Instructions.I64_TRUNC_F32_U:
                state.popChecked(F32_TYPE);
                state.push(I64_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.I64_TRUNC_F64_S:
            case Instructions.I64_TRUNC_F64_U:
            case Instructions.I64_REINTERPRET_F64:
                state.popChecked(F64_TYPE);
                state.push(I64_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.F32_CONVERT_I32_S:
            case Instructions.F32_CONVERT_I32_U:
            case Instructions.F32_REINTERPRET_I32:
                state.popChecked(I32_TYPE);
                state.push(F32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.F32_CONVERT_I64_S:
            case Instructions.F32_CONVERT_I64_U:
                state.popChecked(I64_TYPE);
                state.push(F32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.F32_DEMOTE_F64:
                state.popChecked(F64_TYPE);
                state.push(F32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.F64_CONVERT_I32_S:
            case Instructions.F64_CONVERT_I32_U:
                state.popChecked(I32_TYPE);
                state.push(F64_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.F64_CONVERT_I64_S:
            case Instructions.F64_CONVERT_I64_U:
            case Instructions.F64_REINTERPRET_I64:
                state.popChecked(I64_TYPE);
                state.push(F64_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.F64_PROMOTE_F32:
                state.popChecked(F32_TYPE);
                state.push(F64_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.MISC:
                int miscOpcode = readUnsignedInt32();
                switch (miscOpcode) {
                    case Instructions.I32_TRUNC_SAT_F32_S:
                    case Instructions.I32_TRUNC_SAT_F32_U:
                        checkSaturatingFloatToIntSupport(miscOpcode);
                        state.popChecked(F32_TYPE);
                        state.push(I32_TYPE);
                        state.addMiscFlag();
                        state.addInstruction(miscOpcode);
                        break;
                    case Instructions.I32_TRUNC_SAT_F64_S:
                    case Instructions.I32_TRUNC_SAT_F64_U:
                        checkSaturatingFloatToIntSupport(miscOpcode);
                        state.popChecked(F64_TYPE);
                        state.push(I32_TYPE);
                        state.addMiscFlag();
                        state.addInstruction(miscOpcode);
                        break;
                    case Instructions.I64_TRUNC_SAT_F32_S:
                    case Instructions.I64_TRUNC_SAT_F32_U:
                        checkSaturatingFloatToIntSupport(miscOpcode);
                        state.popChecked(F32_TYPE);
                        state.push(I64_TYPE);
                        state.addMiscFlag();
                        state.addInstruction(miscOpcode);
                        break;
                    case Instructions.I64_TRUNC_SAT_F64_S:
                    case Instructions.I64_TRUNC_SAT_F64_U:
                        checkSaturatingFloatToIntSupport(miscOpcode);
                        state.popChecked(F64_TYPE);
                        state.push(I64_TYPE);
                        state.addMiscFlag();
                        state.addInstruction(miscOpcode);
                        break;
                    case Instructions.MEMORY_INIT: {
                        checkBulkMemoryAndRefTypesSupport(miscOpcode);
                        final int dataIndex = readUnsignedInt32();
                        final int memoryIndex;
                        if (multiMemory) {
                            memoryIndex = readMemoryIndex();
                        } else {
                            read1();
                            memoryIndex = 0;
                            checkMemoryIndex(0);
                        }
                        module.checkDataSegmentIndex(dataIndex);
                        state.popChecked(I32_TYPE);
                        state.popChecked(I32_TYPE);
                        if (module.memoryHasIndexType64(memoryIndex) && memory64) {
                            state.popChecked(I64_TYPE);
                            state.addMiscFlag();
                            if (unsafeMemory) {
                                state.addInstruction(Bytecode.MEMORY64_INIT_UNSAFE, dataIndex, memoryIndex);
                            } else {
                                state.addInstruction(Bytecode.MEMORY64_INIT, dataIndex, memoryIndex);
                            }
                        } else {
                            state.popChecked(I32_TYPE);
                            state.addMiscFlag();
                            if (unsafeMemory) {
                                state.addInstruction(Bytecode.MEMORY_INIT_UNSAFE, dataIndex, memoryIndex);
                            } else {
                                state.addInstruction(Bytecode.MEMORY_INIT, dataIndex, memoryIndex);
                            }
                        }
                        break;
                    }
                    case Instructions.DATA_DROP: {
                        checkBulkMemoryAndRefTypesSupport(miscOpcode);
                        final int dataIndex = readUnsignedInt32();
                        module.checkDataSegmentIndex(dataIndex);
                        state.addMiscFlag();
                        if (unsafeMemory) {
                            state.addInstruction(Bytecode.DATA_DROP_UNSAFE, dataIndex);
                        } else {
                            state.addInstruction(Bytecode.DATA_DROP, dataIndex);
                        }
                        break;
                    }
                    case Instructions.MEMORY_COPY: {
                        checkBulkMemoryAndRefTypesSupport(miscOpcode);
                        final int destMemoryIndex;
                        final int srcMemoryIndex;
                        if (multiMemory) {
                            destMemoryIndex = readMemoryIndex();
                            srcMemoryIndex = readMemoryIndex();
                        } else {
                            read1();
                            read1();
                            destMemoryIndex = 0;
                            srcMemoryIndex = 0;
                            checkMemoryIndex(0);
                        }
                        if (module.memoryHasIndexType64(destMemoryIndex) && module.memoryHasIndexType64(srcMemoryIndex) && memory64) {
                            state.popChecked(I64_TYPE);
                            state.popChecked(I64_TYPE);
                            state.popChecked(I64_TYPE);
                            state.addMiscFlag();
                            state.addInstruction(Bytecode.MEMORY64_COPY_D64_S64, destMemoryIndex, srcMemoryIndex);
                        } else if (module.memoryHasIndexType64(destMemoryIndex) && !module.memoryHasIndexType64(srcMemoryIndex) && memory64) {
                            state.popChecked(I32_TYPE);
                            state.popChecked(I32_TYPE);
                            state.popChecked(I64_TYPE);
                            state.addMiscFlag();
                            state.addInstruction(Bytecode.MEMORY64_COPY_D64_S32, destMemoryIndex, srcMemoryIndex);
                        } else if (!module.memoryHasIndexType64(destMemoryIndex) && module.memoryHasIndexType64(srcMemoryIndex) && memory64) {
                            state.popChecked(I32_TYPE);
                            state.popChecked(I64_TYPE);
                            state.popChecked(I32_TYPE);
                            state.addMiscFlag();
                            state.addInstruction(Bytecode.MEMORY64_COPY_D32_S64, destMemoryIndex, srcMemoryIndex);
                        } else {
                            state.popChecked(I32_TYPE);
                            state.popChecked(I32_TYPE);
                            state.popChecked(I32_TYPE);
                            state.addMiscFlag();
                            state.addInstruction(Bytecode.MEMORY_COPY, destMemoryIndex, srcMemoryIndex);
                        }
                        break;
                    }
                    case Instructions.MEMORY_FILL: {
                        checkBulkMemoryAndRefTypesSupport(miscOpcode);
                        final int memoryIndex;
                        if (multiMemory) {
                            memoryIndex = readMemoryIndex();
                        } else {
                            read1();
                            memoryIndex = 0;
                            checkMemoryIndex(0);
                        }
                        if (module.memoryHasIndexType64(memoryIndex) && memory64) {
                            state.popChecked(I64_TYPE);
                            state.popChecked(I32_TYPE);
                            state.popChecked(I64_TYPE);
                            state.addMiscFlag();
                            state.addInstruction(Bytecode.MEMORY64_FILL, memoryIndex);
                        } else {
                            state.popChecked(I32_TYPE);
                            state.popChecked(I32_TYPE);
                            state.popChecked(I32_TYPE);
                            state.addMiscFlag();
                            state.addInstruction(Bytecode.MEMORY_FILL, memoryIndex);
                        }
                        break;
                    }
                    case Instructions.TABLE_INIT: {
                        checkBulkMemoryAndRefTypesSupport(miscOpcode);
                        final int elementIndex = readUnsignedInt32();
                        final int tableIndex = readTableIndex();
                        module.checkElemIndex(elementIndex);
                        final byte elementType = module.tableElementType(tableIndex);
                        module.checkElemType(elementIndex, elementType);
                        state.popChecked(I32_TYPE);
                        state.popChecked(I32_TYPE);
                        state.popChecked(I32_TYPE);
                        state.addMiscFlag();
                        state.addInstruction(Bytecode.TABLE_INIT, elementIndex, tableIndex);
                        break;
                    }
                    case Instructions.ELEM_DROP: {
                        checkBulkMemoryAndRefTypesSupport(miscOpcode);
                        final int elementIndex = readUnsignedInt32();
                        module.checkElemIndex(elementIndex);
                        state.addMiscFlag();
                        state.addInstruction(Bytecode.ELEM_DROP, elementIndex);
                        break;
                    }
                    case Instructions.TABLE_COPY:
                        checkBulkMemoryAndRefTypesSupport(miscOpcode);
                        final int destinationTableIndex = readTableIndex();
                        final byte destinationElementType = module.tableElementType(destinationTableIndex);
                        final int sourceTableIndex = readTableIndex();
                        final byte sourceElementType = module.tableElementType(sourceTableIndex);
                        assertByteEqual(sourceElementType, destinationElementType, Failure.TYPE_MISMATCH);
                        state.popChecked(I32_TYPE);
                        state.popChecked(I32_TYPE);
                        state.popChecked(I32_TYPE);
                        state.addMiscFlag();
                        state.addInstruction(Bytecode.TABLE_COPY, sourceTableIndex, destinationTableIndex);
                        break;
                    case Instructions.TABLE_SIZE: {
                        checkBulkMemoryAndRefTypesSupport(miscOpcode);
                        final int tableIndex = readTableIndex();
                        state.push(I32_TYPE);
                        state.addMiscFlag();
                        state.addInstruction(Bytecode.TABLE_SIZE, tableIndex);
                        break;
                    }
                    case Instructions.TABLE_GROW: {
                        checkBulkMemoryAndRefTypesSupport(miscOpcode);
                        final int tableIndex = readTableIndex();
                        final byte elementType = module.tableElementType(tableIndex);
                        state.popChecked(I32_TYPE);
                        state.popChecked(elementType);
                        state.push(I32_TYPE);
                        state.addMiscFlag();
                        state.addInstruction(Bytecode.TABLE_GROW, tableIndex);
                        break;
                    }
                    case Instructions.TABLE_FILL: {
                        checkBulkMemoryAndRefTypesSupport(miscOpcode);
                        final int tableIndex = readTableIndex();
                        final byte elementType = module.tableElementType(tableIndex);
                        state.popChecked(I32_TYPE);
                        state.popChecked(elementType);
                        state.popChecked(I32_TYPE);
                        state.addMiscFlag();
                        state.addInstruction(Bytecode.TABLE_FILL, tableIndex);
                        break;
                    }
                    default:
                        fail(Failure.UNSPECIFIED_MALFORMED, "Unknown opcode: 0xFC 0x%02x", miscOpcode);
                }
                break;
            case Instructions.I32_EXTEND8_S:
            case Instructions.I32_EXTEND16_S:
                checkSignExtensionOpsSupport(opcode);
                state.popChecked(I32_TYPE);
                state.push(I32_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.I64_EXTEND8_S:
            case Instructions.I64_EXTEND16_S:
            case Instructions.I64_EXTEND32_S:
                checkSignExtensionOpsSupport(opcode);
                state.popChecked(I64_TYPE);
                state.push(I64_TYPE);
                state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                break;
            case Instructions.REF_NULL:
                checkBulkMemoryAndRefTypesSupport(opcode);
                final byte type = readRefType();
                state.push(type);
                state.addInstruction(Bytecode.REF_NULL);
                break;
            case Instructions.REF_IS_NULL:
                checkBulkMemoryAndRefTypesSupport(opcode);
                state.popReferenceTypeChecked();
                state.push(I32_TYPE);
                state.addInstruction(Bytecode.REF_IS_NULL);
                break;
            case Instructions.REF_FUNC:
                checkBulkMemoryAndRefTypesSupport(opcode);
                final int functionIndex = readDeclaredFunctionIndex();
                module.checkFunctionReference(functionIndex);
                state.push(FUNCREF_TYPE);
                state.addInstruction(Bytecode.REF_FUNC, functionIndex);
                break;
            case Instructions.ATOMIC:
                checkThreadsSupport(opcode);
                int atomicOpcode = read1() & 0xFF;
                state.addAtomicFlag();
                switch (atomicOpcode) {
                    case Instructions.ATOMIC_NOTIFY:
                        atomicNotify(state, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_NOTIFY, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_WAIT32:
                        atomicWait(state, I32_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_WAIT32, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_WAIT64:
                        atomicWait(state, I64_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_WAIT64, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_FENCE:
                        read1();
                        state.addInstruction(Bytecode.ATOMIC_FENCE);
                        break;
                    case Instructions.ATOMIC_I32_LOAD:
                        atomicLoad(state, I32_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_LOAD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_LOAD:
                        atomicLoad(state, I64_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_LOAD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_LOAD8_U:
                        atomicLoad(state, I32_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_LOAD8_U, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_LOAD16_U:
                        atomicLoad(state, I32_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_LOAD16_U, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_LOAD8_U:
                        atomicLoad(state, I64_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_LOAD8_U, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_LOAD16_U:
                        atomicLoad(state, I64_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_LOAD16_U, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_LOAD32_U:
                        atomicLoad(state, I64_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_LOAD32_U, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_STORE:
                        atomicStore(state, I32_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_STORE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_STORE:
                        atomicStore(state, I64_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_STORE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_STORE8:
                        atomicStore(state, I32_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_STORE8, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_STORE16:
                        atomicStore(state, I32_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_STORE16, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_STORE8:
                        atomicStore(state, I64_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_STORE8, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_STORE16:
                        atomicStore(state, I64_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_STORE16, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_STORE32:
                        atomicStore(state, I64_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_STORE32, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW_ADD:
                        atomicReadModifyWrite(state, I32_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW_ADD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW_ADD:
                        atomicReadModifyWrite(state, I64_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW_ADD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW8_U_ADD:
                        atomicReadModifyWrite(state, I32_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW8_U_ADD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW16_U_ADD:
                        atomicReadModifyWrite(state, I32_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW16_U_ADD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW8_U_ADD:
                        atomicReadModifyWrite(state, I64_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW8_U_ADD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW16_U_ADD:
                        atomicReadModifyWrite(state, I64_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW16_U_ADD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW32_U_ADD:
                        atomicReadModifyWrite(state, I64_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW32_U_ADD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW_SUB:
                        atomicReadModifyWrite(state, I32_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW_SUB, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW_SUB:
                        atomicReadModifyWrite(state, I64_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW_SUB, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW8_U_SUB:
                        atomicReadModifyWrite(state, I32_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW8_U_SUB, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW16_U_SUB:
                        atomicReadModifyWrite(state, I32_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW16_U_SUB, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW8_U_SUB:
                        atomicReadModifyWrite(state, I64_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW8_U_SUB, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW16_U_SUB:
                        atomicReadModifyWrite(state, I64_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW16_U_SUB, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW32_U_SUB:
                        atomicReadModifyWrite(state, I64_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW32_U_SUB, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW_AND:
                        atomicReadModifyWrite(state, I32_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW_AND, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW_AND:
                        atomicReadModifyWrite(state, I64_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW_AND, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW8_U_AND:
                        atomicReadModifyWrite(state, I32_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW8_U_AND, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW16_U_AND:
                        atomicReadModifyWrite(state, I32_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW16_U_AND, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW8_U_AND:
                        atomicReadModifyWrite(state, I64_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW8_U_AND, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW16_U_AND:
                        atomicReadModifyWrite(state, I64_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW16_U_AND, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW32_U_AND:
                        atomicReadModifyWrite(state, I64_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW32_U_AND, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW_OR:
                        atomicReadModifyWrite(state, I32_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW_OR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW_OR:
                        atomicReadModifyWrite(state, I64_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW_OR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW8_U_OR:
                        atomicReadModifyWrite(state, I32_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW8_U_OR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW16_U_OR:
                        atomicReadModifyWrite(state, I32_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW16_U_OR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW8_U_OR:
                        atomicReadModifyWrite(state, I64_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW8_U_OR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW16_U_OR:
                        atomicReadModifyWrite(state, I64_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW16_U_OR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW32_U_OR:
                        atomicReadModifyWrite(state, I64_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW32_U_OR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW_XOR:
                        atomicReadModifyWrite(state, I32_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW_XOR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW_XOR:
                        atomicReadModifyWrite(state, I64_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW_XOR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW8_U_XOR:
                        atomicReadModifyWrite(state, I32_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW8_U_XOR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW16_U_XOR:
                        atomicReadModifyWrite(state, I32_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW16_U_XOR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW8_U_XOR:
                        atomicReadModifyWrite(state, I64_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW8_U_XOR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW16_U_XOR:
                        atomicReadModifyWrite(state, I64_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW16_U_XOR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW32_U_XOR:
                        atomicReadModifyWrite(state, I64_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW32_U_XOR, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW_XCHG:
                        atomicReadModifyWrite(state, I32_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW_XCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW_XCHG:
                        atomicReadModifyWrite(state, I64_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW_XCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW8_U_XCHG:
                        atomicReadModifyWrite(state, I32_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW8_U_XCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW16_U_XCHG:
                        atomicReadModifyWrite(state, I32_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW16_U_XCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW8_U_XCHG:
                        atomicReadModifyWrite(state, I64_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW8_U_XCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW16_U_XCHG:
                        atomicReadModifyWrite(state, I64_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW16_U_XCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW32_U_XCHG:
                        atomicReadModifyWrite(state, I64_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW32_U_XCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW_CMPXCHG:
                        atomicCompareExchange(state, I32_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW_CMPXCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW_CMPXCHG:
                        atomicCompareExchange(state, I64_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW_CMPXCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW8_U_CMPXCHG:
                        atomicCompareExchange(state, I32_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW8_U_CMPXCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I32_RMW16_U_CMPXCHG:
                        atomicCompareExchange(state, I32_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I32_RMW16_U_CMPXCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW8_U_CMPXCHG:
                        atomicCompareExchange(state, I64_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW8_U_CMPXCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW16_U_CMPXCHG:
                        atomicCompareExchange(state, I64_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW16_U_CMPXCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.ATOMIC_I64_RMW32_U_CMPXCHG:
                        atomicCompareExchange(state, I64_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.ATOMIC_I64_RMW32_U_CMPXCHG, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    default:
                        fail(Failure.UNSPECIFIED_MALFORMED, "Unknown opcode: 0xFE 0x%02x", atomicOpcode);
                }
                break;
            case Instructions.VECTOR:
                checkSIMDSupport();
                int vectorOpcode = read1() & 0xFF;
                state.addVectorFlag();
                switch (vectorOpcode) {
                    case Instructions.VECTOR_V128_LOAD:
                        load(state, V128_TYPE, 128, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.VECTOR_V128_LOAD, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.VECTOR_V128_LOAD8X8_S:
                    case Instructions.VECTOR_V128_LOAD8X8_U:
                    case Instructions.VECTOR_V128_LOAD16X4_S:
                    case Instructions.VECTOR_V128_LOAD16X4_U:
                    case Instructions.VECTOR_V128_LOAD32X2_S:
                    case Instructions.VECTOR_V128_LOAD32X2_U:
                        load(state, V128_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(vectorOpcode, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.VECTOR_V128_LOAD8_SPLAT:
                        load(state, V128_TYPE, 8, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.VECTOR_V128_LOAD8_SPLAT, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.VECTOR_V128_LOAD16_SPLAT:
                        load(state, V128_TYPE, 16, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.VECTOR_V128_LOAD16_SPLAT, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.VECTOR_V128_LOAD32_SPLAT:
                        load(state, V128_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.VECTOR_V128_LOAD32_SPLAT, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.VECTOR_V128_LOAD64_SPLAT:
                        load(state, V128_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.VECTOR_V128_LOAD64_SPLAT, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.VECTOR_V128_LOAD32_ZERO:
                        load(state, V128_TYPE, 32, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.VECTOR_V128_LOAD32_ZERO, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.VECTOR_V128_LOAD64_ZERO:
                        load(state, V128_TYPE, 64, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.VECTOR_V128_LOAD64_ZERO, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.VECTOR_V128_STORE:
                        store(state, V128_TYPE, 128, longMultiResult);
                        state.addExtendedMemoryInstruction(Bytecode.VECTOR_V128_STORE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]));
                        break;
                    case Instructions.VECTOR_V128_LOAD8_LANE: {
                        state.popChecked(V128_TYPE);
                        load(state, V128_TYPE, 8, longMultiResult);
                        final byte laneIndex = read1();
                        if (Byte.toUnsignedInt(laneIndex) >= 16) {
                            fail(Failure.INVALID_LANE_INDEX, "Lane index %d out of bounds for v128.load8_lane", Byte.toUnsignedInt(laneIndex));
                        }
                        state.addVectorMemoryLaneInstruction(Bytecode.VECTOR_V128_LOAD8_LANE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]),
                                        laneIndex);
                        break;
                    }
                    case Instructions.VECTOR_V128_LOAD16_LANE: {
                        state.popChecked(V128_TYPE);
                        load(state, V128_TYPE, 16, longMultiResult);
                        final byte laneIndex = read1();
                        if (Byte.toUnsignedInt(laneIndex) >= 8) {
                            fail(Failure.INVALID_LANE_INDEX, "Lane index %d out of bounds for v128.load16_lane", Byte.toUnsignedInt(laneIndex));
                        }
                        state.addVectorMemoryLaneInstruction(Bytecode.VECTOR_V128_LOAD16_LANE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]),
                                        laneIndex);
                        break;
                    }
                    case Instructions.VECTOR_V128_LOAD32_LANE: {
                        state.popChecked(V128_TYPE);
                        load(state, V128_TYPE, 32, longMultiResult);
                        final byte laneIndex = read1();
                        if (Byte.toUnsignedInt(laneIndex) >= 4) {
                            fail(Failure.INVALID_LANE_INDEX, "Lane index %d out of bounds for v128.load32_lane", Byte.toUnsignedInt(laneIndex));
                        }
                        state.addVectorMemoryLaneInstruction(Bytecode.VECTOR_V128_LOAD32_LANE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]),
                                        laneIndex);
                        break;
                    }
                    case Instructions.VECTOR_V128_LOAD64_LANE: {
                        state.popChecked(V128_TYPE);
                        load(state, V128_TYPE, 64, longMultiResult);
                        final byte laneIndex = read1();
                        if (Byte.toUnsignedInt(laneIndex) >= 2) {
                            fail(Failure.INVALID_LANE_INDEX, "Lane index %d out of bounds for v128.load64_lane", Byte.toUnsignedInt(laneIndex));
                        }
                        state.addVectorMemoryLaneInstruction(Bytecode.VECTOR_V128_LOAD64_LANE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]),
                                        laneIndex);
                        break;
                    }
                    case Instructions.VECTOR_V128_STORE8_LANE: {
                        store(state, V128_TYPE, 8, longMultiResult);
                        final byte laneIndex = read1();
                        if (Byte.toUnsignedInt(laneIndex) >= 16) {
                            fail(Failure.INVALID_LANE_INDEX, "Lane index %d out of bounds for v128.store8_lane", Byte.toUnsignedInt(laneIndex));
                        }
                        state.addVectorMemoryLaneInstruction(Bytecode.VECTOR_V128_STORE8_LANE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]),
                                        laneIndex);
                        break;
                    }
                    case Instructions.VECTOR_V128_STORE16_LANE: {
                        store(state, V128_TYPE, 16, longMultiResult);
                        final byte laneIndex = read1();
                        if (Byte.toUnsignedInt(laneIndex) >= 8) {
                            fail(Failure.INVALID_LANE_INDEX, "Lane index %d out of bounds for v128.store16_lane", Byte.toUnsignedInt(laneIndex));
                        }
                        state.addVectorMemoryLaneInstruction(Bytecode.VECTOR_V128_STORE16_LANE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]),
                                        laneIndex);
                        break;
                    }
                    case Instructions.VECTOR_V128_STORE32_LANE: {
                        store(state, V128_TYPE, 32, longMultiResult);
                        final byte laneIndex = read1();
                        if (Byte.toUnsignedInt(laneIndex) >= 4) {
                            fail(Failure.INVALID_LANE_INDEX, "Lane index %d out of bounds for v128.store32_lane", Byte.toUnsignedInt(laneIndex));
                        }
                        state.addVectorMemoryLaneInstruction(Bytecode.VECTOR_V128_STORE32_LANE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]),
                                        laneIndex);
                        break;
                    }
                    case Instructions.VECTOR_V128_STORE64_LANE: {
                        store(state, V128_TYPE, 64, longMultiResult);
                        final byte laneIndex = read1();
                        if (Byte.toUnsignedInt(laneIndex) >= 2) {
                            fail(Failure.INVALID_LANE_INDEX, "Lane index %d out of bounds for v128.store64_lane", Byte.toUnsignedInt(laneIndex));
                        }
                        state.addVectorMemoryLaneInstruction(Bytecode.VECTOR_V128_STORE64_LANE, (int) longMultiResult[0], longMultiResult[1], module.memoryHasIndexType64((int) longMultiResult[0]),
                                        laneIndex);
                        break;
                    }
                    case Instructions.VECTOR_V128_CONST: {
                        final Vector128 value = readUnsignedInt128();
                        state.push(V128_TYPE);
                        state.addInstruction(Bytecode.VECTOR_V128_CONST, value);
                        break;
                    }
                    case Instructions.VECTOR_I8X16_SHUFFLE: {
                        final Vector128 indices = readUnsignedInt128();
                        for (byte index : indices.asBytes()) {
                            if (Byte.toUnsignedInt(index) >= 32) {
                                fail(Failure.INVALID_LANE_INDEX, "i8x16.shuffle lane index %d out of bounds", Byte.toUnsignedInt(index));
                            }
                        }
                        state.popChecked(V128_TYPE);
                        state.popChecked(V128_TYPE);
                        state.push(V128_TYPE);
                        state.addInstruction(vectorOpcode, indices);
                        break;
                    }
                    case Instructions.VECTOR_I8X16_EXTRACT_LANE_S:
                    case Instructions.VECTOR_I8X16_EXTRACT_LANE_U:
                    case Instructions.VECTOR_I16X8_EXTRACT_LANE_S:
                    case Instructions.VECTOR_I16X8_EXTRACT_LANE_U:
                    case Instructions.VECTOR_I32X4_EXTRACT_LANE:
                    case Instructions.VECTOR_I64X2_EXTRACT_LANE:
                    case Instructions.VECTOR_F32X4_EXTRACT_LANE:
                    case Instructions.VECTOR_F64X2_EXTRACT_LANE: {
                        final byte laneIndex = read1();
                        Vector128Shape shape = Vector128Shape.ofInstruction(vectorOpcode);
                        if (Byte.toUnsignedInt(laneIndex) >= shape.getDimension()) {
                            fail(Failure.INVALID_LANE_INDEX, "Lane index %d out of bounds for shape %s", Byte.toUnsignedInt(laneIndex), shape.toString());
                        }
                        state.popChecked(V128_TYPE);
                        state.push(shape.getUnpackedType());
                        state.addVectorLaneInstruction(vectorOpcode, laneIndex);
                        break;
                    }
                    case Instructions.VECTOR_I8X16_REPLACE_LANE:
                    case Instructions.VECTOR_I16X8_REPLACE_LANE:
                    case Instructions.VECTOR_I32X4_REPLACE_LANE:
                    case Instructions.VECTOR_I64X2_REPLACE_LANE:
                    case Instructions.VECTOR_F32X4_REPLACE_LANE:
                    case Instructions.VECTOR_F64X2_REPLACE_LANE: {
                        final byte laneIndex = read1();
                        Vector128Shape shape = Vector128Shape.ofInstruction(vectorOpcode);
                        if (Byte.toUnsignedInt(laneIndex) >= shape.getDimension()) {
                            fail(Failure.INVALID_LANE_INDEX, "Lane index %d out of bounds for shape %s", Byte.toUnsignedInt(laneIndex), shape.toString());
                        }
                        state.popChecked(shape.getUnpackedType());
                        state.popChecked(V128_TYPE);
                        state.push(V128_TYPE);
                        state.addVectorLaneInstruction(vectorOpcode, laneIndex);
                        break;
                    }
                    case Instructions.VECTOR_I8X16_SPLAT:
                    case Instructions.VECTOR_I16X8_SPLAT:
                    case Instructions.VECTOR_I32X4_SPLAT:
                    case Instructions.VECTOR_I64X2_SPLAT:
                    case Instructions.VECTOR_F32X4_SPLAT:
                    case Instructions.VECTOR_F64X2_SPLAT: {
                        Vector128Shape shape = Vector128Shape.ofInstruction(vectorOpcode);
                        state.popChecked(shape.getUnpackedType());
                        state.push(V128_TYPE);
                        state.addInstruction(vectorOpcode);
                        break;
                    }
                    case Instructions.VECTOR_V128_ANY_TRUE:
                    case Instructions.VECTOR_I8X16_ALL_TRUE:
                    case Instructions.VECTOR_I8X16_BITMASK:
                    case Instructions.VECTOR_I16X8_ALL_TRUE:
                    case Instructions.VECTOR_I16X8_BITMASK:
                    case Instructions.VECTOR_I32X4_ALL_TRUE:
                    case Instructions.VECTOR_I32X4_BITMASK:
                    case Instructions.VECTOR_I64X2_ALL_TRUE:
                    case Instructions.VECTOR_I64X2_BITMASK:
                        state.popChecked(V128_TYPE);
                        state.push(I32_TYPE);
                        state.addInstruction(vectorOpcode);
                        break;
                    case Instructions.VECTOR_V128_NOT:
                    case Instructions.VECTOR_I8X16_ABS:
                    case Instructions.VECTOR_I8X16_NEG:
                    case Instructions.VECTOR_I8X16_POPCNT:
                    case Instructions.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_S:
                    case Instructions.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_U:
                    case Instructions.VECTOR_I16X8_ABS:
                    case Instructions.VECTOR_I16X8_NEG:
                    case Instructions.VECTOR_I16X8_EXTEND_LOW_I8X16_S:
                    case Instructions.VECTOR_I16X8_EXTEND_HIGH_I8X16_S:
                    case Instructions.VECTOR_I16X8_EXTEND_LOW_I8X16_U:
                    case Instructions.VECTOR_I16X8_EXTEND_HIGH_I8X16_U:
                    case Instructions.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_S:
                    case Instructions.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_U:
                    case Instructions.VECTOR_I32X4_ABS:
                    case Instructions.VECTOR_I32X4_NEG:
                    case Instructions.VECTOR_I32X4_EXTEND_LOW_I16X8_S:
                    case Instructions.VECTOR_I32X4_EXTEND_HIGH_I16X8_S:
                    case Instructions.VECTOR_I32X4_EXTEND_LOW_I16X8_U:
                    case Instructions.VECTOR_I32X4_EXTEND_HIGH_I16X8_U:
                    case Instructions.VECTOR_I64X2_ABS:
                    case Instructions.VECTOR_I64X2_NEG:
                    case Instructions.VECTOR_I64X2_EXTEND_LOW_I32X4_S:
                    case Instructions.VECTOR_I64X2_EXTEND_HIGH_I32X4_S:
                    case Instructions.VECTOR_I64X2_EXTEND_LOW_I32X4_U:
                    case Instructions.VECTOR_I64X2_EXTEND_HIGH_I32X4_U:
                    case Instructions.VECTOR_F32X4_CEIL:
                    case Instructions.VECTOR_F32X4_FLOOR:
                    case Instructions.VECTOR_F32X4_TRUNC:
                    case Instructions.VECTOR_F32X4_NEAREST:
                    case Instructions.VECTOR_F32X4_ABS:
                    case Instructions.VECTOR_F32X4_NEG:
                    case Instructions.VECTOR_F32X4_SQRT:
                    case Instructions.VECTOR_F64X2_CEIL:
                    case Instructions.VECTOR_F64X2_FLOOR:
                    case Instructions.VECTOR_F64X2_TRUNC:
                    case Instructions.VECTOR_F64X2_NEAREST:
                    case Instructions.VECTOR_F64X2_ABS:
                    case Instructions.VECTOR_F64X2_NEG:
                    case Instructions.VECTOR_F64X2_SQRT:
                    case Instructions.VECTOR_I32X4_TRUNC_SAT_F32X4_S:
                    case Instructions.VECTOR_I32X4_TRUNC_SAT_F32X4_U:
                    case Instructions.VECTOR_F32X4_CONVERT_I32X4_S:
                    case Instructions.VECTOR_F32X4_CONVERT_I32X4_U:
                    case Instructions.VECTOR_I32X4_TRUNC_SAT_F64X2_S_ZERO:
                    case Instructions.VECTOR_I32X4_TRUNC_SAT_F64X2_U_ZERO:
                    case Instructions.VECTOR_F64X2_CONVERT_LOW_I32X4_S:
                    case Instructions.VECTOR_F64X2_CONVERT_LOW_I32X4_U:
                    case Instructions.VECTOR_F32X4_DEMOTE_F64X2_ZERO:
                    case Instructions.VECTOR_F64X2_PROMOTE_LOW_F32X4:
                        state.popChecked(V128_TYPE);
                        state.push(V128_TYPE);
                        state.addInstruction(vectorOpcode);
                        break;
                    case Instructions.VECTOR_I8X16_SWIZZLE:
                    case Instructions.VECTOR_I8X16_EQ:
                    case Instructions.VECTOR_I8X16_NE:
                    case Instructions.VECTOR_I8X16_LT_S:
                    case Instructions.VECTOR_I8X16_LT_U:
                    case Instructions.VECTOR_I8X16_GT_S:
                    case Instructions.VECTOR_I8X16_GT_U:
                    case Instructions.VECTOR_I8X16_LE_S:
                    case Instructions.VECTOR_I8X16_LE_U:
                    case Instructions.VECTOR_I8X16_GE_S:
                    case Instructions.VECTOR_I8X16_GE_U:
                    case Instructions.VECTOR_I16X8_EQ:
                    case Instructions.VECTOR_I16X8_NE:
                    case Instructions.VECTOR_I16X8_LT_S:
                    case Instructions.VECTOR_I16X8_LT_U:
                    case Instructions.VECTOR_I16X8_GT_S:
                    case Instructions.VECTOR_I16X8_GT_U:
                    case Instructions.VECTOR_I16X8_LE_S:
                    case Instructions.VECTOR_I16X8_LE_U:
                    case Instructions.VECTOR_I16X8_GE_S:
                    case Instructions.VECTOR_I16X8_GE_U:
                    case Instructions.VECTOR_I32X4_EQ:
                    case Instructions.VECTOR_I32X4_NE:
                    case Instructions.VECTOR_I32X4_LT_S:
                    case Instructions.VECTOR_I32X4_LT_U:
                    case Instructions.VECTOR_I32X4_GT_S:
                    case Instructions.VECTOR_I32X4_GT_U:
                    case Instructions.VECTOR_I32X4_LE_S:
                    case Instructions.VECTOR_I32X4_LE_U:
                    case Instructions.VECTOR_I32X4_GE_S:
                    case Instructions.VECTOR_I32X4_GE_U:
                    case Instructions.VECTOR_I64X2_EQ:
                    case Instructions.VECTOR_I64X2_NE:
                    case Instructions.VECTOR_I64X2_LT_S:
                    case Instructions.VECTOR_I64X2_GT_S:
                    case Instructions.VECTOR_I64X2_LE_S:
                    case Instructions.VECTOR_I64X2_GE_S:
                    case Instructions.VECTOR_F32X4_EQ:
                    case Instructions.VECTOR_F32X4_NE:
                    case Instructions.VECTOR_F32X4_LT:
                    case Instructions.VECTOR_F32X4_GT:
                    case Instructions.VECTOR_F32X4_LE:
                    case Instructions.VECTOR_F32X4_GE:
                    case Instructions.VECTOR_F64X2_EQ:
                    case Instructions.VECTOR_F64X2_NE:
                    case Instructions.VECTOR_F64X2_LT:
                    case Instructions.VECTOR_F64X2_GT:
                    case Instructions.VECTOR_F64X2_LE:
                    case Instructions.VECTOR_F64X2_GE:
                    case Instructions.VECTOR_V128_AND:
                    case Instructions.VECTOR_V128_ANDNOT:
                    case Instructions.VECTOR_V128_OR:
                    case Instructions.VECTOR_V128_XOR:
                    case Instructions.VECTOR_I8X16_NARROW_I16X8_S:
                    case Instructions.VECTOR_I8X16_NARROW_I16X8_U:
                    case Instructions.VECTOR_I8X16_ADD:
                    case Instructions.VECTOR_I8X16_ADD_SAT_S:
                    case Instructions.VECTOR_I8X16_ADD_SAT_U:
                    case Instructions.VECTOR_I8X16_SUB:
                    case Instructions.VECTOR_I8X16_SUB_SAT_S:
                    case Instructions.VECTOR_I8X16_SUB_SAT_U:
                    case Instructions.VECTOR_I8X16_MIN_S:
                    case Instructions.VECTOR_I8X16_MIN_U:
                    case Instructions.VECTOR_I8X16_MAX_S:
                    case Instructions.VECTOR_I8X16_MAX_U:
                    case Instructions.VECTOR_I8X16_AVGR_U:
                    case Instructions.VECTOR_I16X8_Q15MULR_SAT_S:
                    case Instructions.VECTOR_I16X8_NARROW_I32X4_S:
                    case Instructions.VECTOR_I16X8_NARROW_I32X4_U:
                    case Instructions.VECTOR_I16X8_ADD:
                    case Instructions.VECTOR_I16X8_ADD_SAT_S:
                    case Instructions.VECTOR_I16X8_ADD_SAT_U:
                    case Instructions.VECTOR_I16X8_SUB:
                    case Instructions.VECTOR_I16X8_SUB_SAT_S:
                    case Instructions.VECTOR_I16X8_SUB_SAT_U:
                    case Instructions.VECTOR_I16X8_MUL:
                    case Instructions.VECTOR_I16X8_MIN_S:
                    case Instructions.VECTOR_I16X8_MIN_U:
                    case Instructions.VECTOR_I16X8_MAX_S:
                    case Instructions.VECTOR_I16X8_MAX_U:
                    case Instructions.VECTOR_I16X8_AVGR_U:
                    case Instructions.VECTOR_I16X8_EXTMUL_LOW_I8X16_S:
                    case Instructions.VECTOR_I16X8_EXTMUL_HIGH_I8X16_S:
                    case Instructions.VECTOR_I16X8_EXTMUL_LOW_I8X16_U:
                    case Instructions.VECTOR_I16X8_EXTMUL_HIGH_I8X16_U:
                    case Instructions.VECTOR_I32X4_ADD:
                    case Instructions.VECTOR_I32X4_SUB:
                    case Instructions.VECTOR_I32X4_MUL:
                    case Instructions.VECTOR_I32X4_MIN_S:
                    case Instructions.VECTOR_I32X4_MIN_U:
                    case Instructions.VECTOR_I32X4_MAX_S:
                    case Instructions.VECTOR_I32X4_MAX_U:
                    case Instructions.VECTOR_I32X4_DOT_I16X8_S:
                    case Instructions.VECTOR_I32X4_EXTMUL_LOW_I16X8_S:
                    case Instructions.VECTOR_I32X4_EXTMUL_HIGH_I16X8_S:
                    case Instructions.VECTOR_I32X4_EXTMUL_LOW_I16X8_U:
                    case Instructions.VECTOR_I32X4_EXTMUL_HIGH_I16X8_U:
                    case Instructions.VECTOR_I64X2_ADD:
                    case Instructions.VECTOR_I64X2_SUB:
                    case Instructions.VECTOR_I64X2_MUL:
                    case Instructions.VECTOR_I64X2_EXTMUL_LOW_I32X4_S:
                    case Instructions.VECTOR_I64X2_EXTMUL_HIGH_I32X4_S:
                    case Instructions.VECTOR_I64X2_EXTMUL_LOW_I32X4_U:
                    case Instructions.VECTOR_I64X2_EXTMUL_HIGH_I32X4_U:
                    case Instructions.VECTOR_F32X4_ADD:
                    case Instructions.VECTOR_F32X4_SUB:
                    case Instructions.VECTOR_F32X4_MUL:
                    case Instructions.VECTOR_F32X4_DIV:
                    case Instructions.VECTOR_F32X4_MIN:
                    case Instructions.VECTOR_F32X4_MAX:
                    case Instructions.VECTOR_F32X4_PMIN:
                    case Instructions.VECTOR_F32X4_PMAX:
                    case Instructions.VECTOR_F64X2_ADD:
                    case Instructions.VECTOR_F64X2_SUB:
                    case Instructions.VECTOR_F64X2_MUL:
                    case Instructions.VECTOR_F64X2_DIV:
                    case Instructions.VECTOR_F64X2_MIN:
                    case Instructions.VECTOR_F64X2_MAX:
                    case Instructions.VECTOR_F64X2_PMIN:
                    case Instructions.VECTOR_F64X2_PMAX:
                        state.popChecked(V128_TYPE);
                        state.popChecked(V128_TYPE);
                        state.push(V128_TYPE);
                        state.addInstruction(vectorOpcode);
                        break;
                    case Instructions.VECTOR_I8X16_SHL:
                    case Instructions.VECTOR_I8X16_SHR_S:
                    case Instructions.VECTOR_I8X16_SHR_U:
                    case Instructions.VECTOR_I16X8_SHL:
                    case Instructions.VECTOR_I16X8_SHR_S:
                    case Instructions.VECTOR_I16X8_SHR_U:
                    case Instructions.VECTOR_I32X4_SHL:
                    case Instructions.VECTOR_I32X4_SHR_S:
                    case Instructions.VECTOR_I32X4_SHR_U:
                    case Instructions.VECTOR_I64X2_SHL:
                    case Instructions.VECTOR_I64X2_SHR_S:
                    case Instructions.VECTOR_I64X2_SHR_U:
                        state.popChecked(I32_TYPE);
                        state.popChecked(V128_TYPE);
                        state.push(V128_TYPE);
                        state.addInstruction(vectorOpcode);
                        break;
                    case Instructions.VECTOR_V128_BITSELECT:
                        state.popChecked(V128_TYPE);
                        state.popChecked(V128_TYPE);
                        state.popChecked(V128_TYPE);
                        state.push(V128_TYPE);
                        state.addInstruction(vectorOpcode);
                        break;
                    default:
                        fail(Failure.UNSPECIFIED_MALFORMED, "Unknown opcode: 0xFD 0x%02x", vectorOpcode);
                }
                break;
            default:
                fail(Failure.UNSPECIFIED_MALFORMED, "Unknown opcode: 0x%02x", opcode);
                break;
        }
    }

    private static void checkContextOption(boolean option, String message, Object... args) {
        if (!option) {
            fail(Failure.UNSPECIFIED_MALFORMED, message, args);
        }
    }

    private void checkSaturatingFloatToIntSupport(int opcode) {
        checkContextOption(wasmContext.getContextOptions().supportSaturatingFloatToInt(), "Saturating float-to-int conversion is not enabled (opcode: 0xFC 0x%02x)", opcode);
    }

    private void checkSignExtensionOpsSupport(int opcode) {
        checkContextOption(wasmContext.getContextOptions().supportSignExtensionOps(), "Sign-extension operators are not enabled (opcode: 0x%02x)", opcode);
    }

    private void checkBulkMemoryAndRefTypesSupport(int opcode) {
        checkContextOption(wasmContext.getContextOptions().supportBulkMemoryAndRefTypes(), "Bulk memory operations and reference types are not enabled (opcode: 0x%02x)", opcode);
    }

    private void checkThreadsSupport(int opcode) {
        checkContextOption(wasmContext.getContextOptions().supportThreads(), "Threads and atomics are not enabled (opcode: 0x%02x)", opcode);
    }

    private void checkSIMDSupport() {
        checkContextOption(wasmContext.getContextOptions().supportSIMD(), "Vector instructions are not enabled (opcode: 0x%02x)", Instructions.VECTOR);
    }

    private void store(ParserState state, byte type, int n, long[] result) {
        int alignHint = readAlignHint(n);
        final int memoryIndex = readMemoryIndexFromAlignHint(alignHint);
        final long memoryOffset = readBaseMemoryOffset();
        state.popChecked(type); // value to store
        if (module.memoryHasIndexType64(memoryIndex) && memory64) {
            state.popChecked(I64_TYPE);
        } else {
            state.popChecked(I32_TYPE); // 32-bit base address
        }
        result[0] = memoryIndex;
        result[1] = memoryOffset;
    }

    private void load(ParserState state, byte type, int n, long[] result) {
        final int alignHint = readAlignHint(n);
        final int memoryIndex = readMemoryIndexFromAlignHint(alignHint);
        final long memoryOffset = readBaseMemoryOffset();
        if (module.memoryHasIndexType64(memoryIndex) && memory64) {
            state.popChecked(I64_TYPE); // 64-bit base address
        } else {
            state.popChecked(I32_TYPE); // 32-bit base address
        }
        state.push(type); // loaded value
        result[0] = memoryIndex;
        result[1] = memoryOffset;
    }

    private void atomicStore(ParserState state, byte type, int n, long[] result) {
        int alignHint = readAtomicAlignHint(n);
        final int memoryIndex = readMemoryIndexFromAlignHint(alignHint);
        final long memoryOffset = readBaseMemoryOffset();
        state.popChecked(type); // value to store
        if (module.memoryHasIndexType64(memoryIndex) && memory64) {
            state.popChecked(I64_TYPE);
        } else {
            state.popChecked(I32_TYPE); // 32-bit base address
        }
        result[0] = memoryIndex;
        result[1] = memoryOffset;
    }

    private void atomicLoad(ParserState state, byte type, int n, long[] result) {
        final int alignHint = readAtomicAlignHint(n);
        final int memoryIndex = readMemoryIndexFromAlignHint(alignHint);
        final long memoryOffset = readBaseMemoryOffset();
        if (module.memoryHasIndexType64(memoryIndex) && memory64) {
            state.popChecked(I64_TYPE); // 64-bit base address
        } else {
            state.popChecked(I32_TYPE); // 32-bit base address
        }
        state.push(type); // loaded value
        result[0] = memoryIndex;
        result[1] = memoryOffset;
    }

    private void atomicReadModifyWrite(ParserState state, byte type, int n, long[] result) {
        final int alignHint = readAtomicAlignHint(n);
        final int memoryIndex = readMemoryIndexFromAlignHint(alignHint);
        final long memoryOffset = readBaseMemoryOffset();
        state.popChecked(type); // RMW value
        if (module.memoryHasIndexType64(memoryIndex) && memory64) {
            state.popChecked(I64_TYPE); // 64-bit base address
        } else {
            state.popChecked(I32_TYPE); // 32-bit base address
        }
        state.push(type); // loaded value
        result[0] = memoryIndex;
        result[1] = memoryOffset;
    }

    private void atomicCompareExchange(ParserState state, byte type, int n, long[] result) {
        final int alignHint = readAtomicAlignHint(n);
        final int memoryIndex = readMemoryIndexFromAlignHint(alignHint);
        final long memoryOffset = readBaseMemoryOffset();
        state.popChecked(type); // replacement value
        state.popChecked(type); // expected value
        if (module.memoryHasIndexType64(memoryIndex) && memory64) {
            state.popChecked(I64_TYPE); // 64-bit base address
        } else {
            state.popChecked(I32_TYPE); // 32-bit base address
        }
        state.push(type); // loaded value
        result[0] = memoryIndex;
        result[1] = memoryOffset;
    }

    private void atomicNotify(ParserState state, long[] result) {
        final int alignHint = readAtomicAlignHint(32);
        final int memoryIndex = readMemoryIndexFromAlignHint(alignHint);
        final long memoryOffset = readBaseMemoryOffset();
        state.popChecked(I32_TYPE); // 32-bit count (number of threads to notify)
        if (module.memoryHasIndexType64(memoryIndex) && memory64) {
            state.popChecked(I64_TYPE); // 64-bit base address
        } else {
            state.popChecked(I32_TYPE); // 32-bit base address
        }
        state.push(I32_TYPE); // 32-bit count (number of threads notified)
        result[0] = memoryIndex;
        result[1] = memoryOffset;
    }

    private void atomicWait(ParserState state, byte type, int n, long[] result) {
        final int alignHint = readAtomicAlignHint(n);
        final int memoryIndex = readMemoryIndexFromAlignHint(alignHint);
        final long memoryOffset = readBaseMemoryOffset();
        state.popChecked(I64_TYPE); // 64-bit relative timeout
        state.popChecked(type); // expected value
        if (module.memoryHasIndexType64(memoryIndex) && memory64) {
            state.popChecked(I64_TYPE); // 64-bit base address
        } else {
            state.popChecked(I32_TYPE); // 32-bit base address
        }
        state.push(I32_TYPE); // 32-bit return value (0, 1, 2)
        result[0] = memoryIndex;
        result[1] = memoryOffset;
    }

    private Pair<Integer, byte[]> readOffsetExpression() {
        // Table offset expression must be a constant expression with result type i32.
        // https://webassembly.github.io/spec/core/syntax/modules.html#element-segments
        // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions
        Pair<Object, byte[]> result = readConstantExpression(I32_TYPE, false);
        if (result.getRight() == null) {
            return Pair.create((int) result.getLeft(), null);
        } else {
            return Pair.create(-1, result.getRight());
        }
    }

    private Pair<Long, byte[]> readLongOffsetExpression() {
        Pair<Object, byte[]> result = readConstantExpression(I64_TYPE, false);
        if (result.getRight() == null) {
            return Pair.create((long) result.getLeft(), null);
        } else {
            return Pair.create(-1L, result.getRight());
        }
    }

    private Pair<Object, byte[]> readConstantExpression(byte resultType, boolean onlyImportedGlobals) {
        // Read the constant expression.
        // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions
        final RuntimeBytecodeGen bytecode = new RuntimeBytecodeGen();
        final ParserState state = new ParserState(bytecode);

        final List<Object> stack = new ArrayList<>();
        boolean calculable = true;

        state.enterFunction(new byte[]{resultType});
        int opcode;
        while ((opcode = read1() & 0xFF) != Instructions.END) {
            switch (opcode) {
                case Instructions.I32_CONST: {
                    final int value = readSignedInt32();
                    state.push(I32_TYPE);
                    state.addSignedInstruction(Bytecode.I32_CONST_I8, value);
                    if (calculable) {
                        stack.add(value);
                    }
                    break;
                }
                case Instructions.I64_CONST: {
                    final long value = readSignedInt64();
                    state.push(I64_TYPE);
                    state.addSignedInstruction(Bytecode.I64_CONST_I8, value);
                    if (calculable) {
                        stack.add(value);
                    }
                    break;
                }
                case Instructions.F32_CONST: {
                    final int rawValue = readFloatAsInt32();
                    final float value = Float.intBitsToFloat(rawValue);
                    state.push(F32_TYPE);
                    state.addInstruction(Bytecode.F32_CONST, rawValue);
                    if (calculable) {
                        stack.add(value);
                    }
                    break;
                }
                case Instructions.F64_CONST: {
                    final long rawValue = readFloatAsInt64();
                    final double value = Double.longBitsToDouble(rawValue);
                    state.push(F64_TYPE);
                    state.addInstruction(Bytecode.F64_CONST, rawValue);
                    if (calculable) {
                        stack.add(value);
                    }
                    break;
                }
                case Instructions.REF_NULL:
                    checkBulkMemoryAndRefTypesSupport(opcode);
                    final byte type = readRefType();
                    state.push(type);
                    state.addInstruction(Bytecode.REF_NULL);
                    if (calculable) {
                        stack.add(WasmConstant.NULL);
                    }
                    break;
                case Instructions.REF_FUNC:
                    checkBulkMemoryAndRefTypesSupport(opcode);
                    final int functionIndex = readDeclaredFunctionIndex();
                    module.addFunctionReference(functionIndex);
                    state.push(FUNCREF_TYPE);
                    state.addInstruction(Bytecode.REF_FUNC, functionIndex);
                    calculable = false;
                    break;
                case Instructions.GLOBAL_GET: {
                    final int index = readGlobalIndex();
                    if (onlyImportedGlobals) {
                        // The current WebAssembly spec says constant expressions can only refer to
                        // imported globals. We can easily remove this restriction in the future.
                        assertUnsignedIntLess(index, module.symbolTable().importedGlobals().size(), Failure.UNSPECIFIED_MALFORMED,
                                        "The initializer for global " + index + " in module '" + module.name() + "' refers to a non-imported global.");
                    }
                    assertIntEqual(module.globalMutability(index), GlobalModifier.CONSTANT, Failure.CONSTANT_EXPRESSION_REQUIRED);
                    state.push(module.symbolTable().globalValueType(index));
                    state.addUnsignedInstruction(Bytecode.GLOBAL_GET_U8, index);
                    calculable = false;
                    break;
                }
                case Instructions.I32_ADD:
                case Instructions.I32_SUB:
                case Instructions.I32_MUL:
                    if (!wasmContext.getContextOptions().supportExtendedConstExpressions()) {
                        fail(Failure.TYPE_MISMATCH, "Invalid instruction for constant expression: 0x%02X", opcode);
                    }
                    state.popChecked(I32_TYPE);
                    state.popChecked(I32_TYPE);
                    state.push(I32_TYPE);
                    state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                    if (calculable) {
                        int x = (int) stack.remove(stack.size() - 1);
                        int y = (int) stack.remove(stack.size() - 1);
                        stack.add(switch (opcode) {
                            case Instructions.I32_ADD -> y + x;
                            case Instructions.I32_SUB -> y - x;
                            case Instructions.I32_MUL -> y * x;
                            default -> throw CompilerDirectives.shouldNotReachHere();
                        });
                    }
                    break;
                case Instructions.I64_ADD:
                case Instructions.I64_SUB:
                case Instructions.I64_MUL:
                    if (!wasmContext.getContextOptions().supportExtendedConstExpressions()) {
                        fail(Failure.TYPE_MISMATCH, "Invalid instruction for constant expression: 0x%02X", opcode);
                    }
                    state.popChecked(I64_TYPE);
                    state.popChecked(I64_TYPE);
                    state.push(I64_TYPE);
                    state.addInstruction(opcode + Bytecode.COMMON_BYTECODE_OFFSET);
                    if (calculable) {
                        long x = (long) stack.remove(stack.size() - 1);
                        long y = (long) stack.remove(stack.size() - 1);
                        stack.add(switch (opcode) {
                            case Instructions.I64_ADD -> y + x;
                            case Instructions.I64_SUB -> y - x;
                            case Instructions.I64_MUL -> y * x;
                            default -> throw CompilerDirectives.shouldNotReachHere();
                        });
                    }
                    break;
                case Instructions.VECTOR:
                    checkSIMDSupport();
                    int vectorOpcode = read1() & 0xFF;
                    state.addVectorFlag();
                    switch (vectorOpcode) {
                        case Instructions.VECTOR_V128_CONST: {
                            final Vector128 value = readUnsignedInt128();
                            state.push(V128_TYPE);
                            state.addInstruction(Bytecode.VECTOR_V128_CONST, value);
                            if (calculable) {
                                stack.add(value);
                            }
                            break;
                        }
                        default:
                            fail(Failure.TYPE_MISMATCH, "Invalid instruction for constant expression: 0x%02X 0x%02X", opcode, vectorOpcode);
                            break;
                    }
                    break;
                default:
                    fail(Failure.TYPE_MISMATCH, "Invalid instruction for constant expression: 0x%02X", opcode);
                    break;
            }
        }
        assertIntEqual(state.valueStackSize(), 1, "Multiple results on stack at constant expression end", Failure.TYPE_MISMATCH);
        state.exit(multiValue);
        if (calculable) {
            return Pair.create(stack.remove(stack.size() - 1), null);
        } else {
            return Pair.create(null, bytecode.toArray());
        }
    }

    private long[] readFunctionIndices() {
        final int functionIndexCount = readLength();
        final long[] functionIndices = new long[functionIndexCount];
        for (int index = 0; index != functionIndexCount; index++) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            final int functionIndex = readDeclaredFunctionIndex();
            module.addFunctionReference(functionIndex);
            functionIndices[index] = ((long) FUNCREF_TYPE << 32) | functionIndex;
        }
        return functionIndices;
    }

    private void checkElemKind() {
        final byte elementKind = read1();
        if (elementKind != 0x00) {
            throw WasmException.format(Failure.TYPE_MISMATCH, "Invalid element kind: 0x%02X", elementKind);
        }
    }

    private long[] readElemExpressions(byte elemType) {
        final int expressionCount = readLength();
        final long[] functionIndices = new long[expressionCount];
        for (int index = 0; index != expressionCount; index++) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            int opcode = read1() & 0xFF;
            switch (opcode) {
                case Instructions.I32_CONST:
                case Instructions.I64_CONST:
                case Instructions.F32_CONST:
                case Instructions.F64_CONST:
                    throw WasmException.format(Failure.TYPE_MISMATCH, "Invalid constant expression for table elem expression: 0x%02X", opcode);
                case Instructions.I32_ADD:
                case Instructions.I32_SUB:
                case Instructions.I32_MUL:
                case Instructions.I64_ADD:
                case Instructions.I64_SUB:
                case Instructions.I64_MUL:
                    if (wasmContext.getContextOptions().supportExtendedConstExpressions()) {
                        throw WasmException.format(Failure.TYPE_MISMATCH, "Invalid constant expression for table elem expression: 0x%02X", opcode);
                    } else {
                        throw WasmException.format(Failure.ILLEGAL_OPCODE, "Illegal opcode for constant expression: 0x%02X", opcode);
                    }
                case Instructions.REF_NULL:
                    final byte type = readRefType();
                    if (bulkMemoryAndRefTypes && type != elemType) {
                        fail(Failure.TYPE_MISMATCH, "Invalid ref.null type: 0x%02X", type);
                    }
                    functionIndices[index] = ((long) NULL_TYPE << 32);
                    break;
                case Instructions.REF_FUNC:
                    if (elemType != FUNCREF_TYPE) {
                        fail(Failure.TYPE_MISMATCH, "Invalid element type: 0x%02X", FUNCREF_TYPE);
                    }
                    final int functionIndex = readDeclaredFunctionIndex();
                    module.addFunctionReference(functionIndex);
                    functionIndices[index] = ((long) FUNCREF_TYPE << 32) | functionIndex;
                    break;
                case Instructions.GLOBAL_GET:
                    final int globalIndex = readGlobalIndex();
                    assertIntEqual(module.globalMutability(globalIndex), GlobalModifier.CONSTANT, Failure.CONSTANT_EXPRESSION_REQUIRED);
                    final byte valueType = module.globalValueType(globalIndex);
                    assertByteEqual(valueType, elemType, Failure.TYPE_MISMATCH);
                    functionIndices[index] = ((long) I32_TYPE << 32) | globalIndex;
                    break;
                case Instructions.VECTOR:
                    checkSIMDSupport();
                    int vectorOpcode = read1() & 0xFF;
                    switch (vectorOpcode) {
                        case Instructions.VECTOR_V128_CONST:
                            throw WasmException.format(Failure.TYPE_MISMATCH, "Invalid constant expression for table elem expression: 0x%02X 0x%02X", opcode, vectorOpcode);
                        default:
                            throw WasmException.format(Failure.ILLEGAL_OPCODE, "Illegal opcode for constant expression: 0x%02X 0x%02X", opcode, vectorOpcode);
                    }
                default:
                    throw WasmException.format(Failure.ILLEGAL_OPCODE, "Illegal opcode for constant expression: 0x%02X", opcode);
            }
            readEnd();
        }
        return functionIndices;
    }

    private void readElementSection(RuntimeBytecodeGen bytecode) {
        int elemSegmentCount = readLength();
        module.limits().checkElementSegmentCount(elemSegmentCount);
        for (int elemSegmentIndex = 0; elemSegmentIndex != elemSegmentCount; elemSegmentIndex++) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            int mode;
            final int currentOffsetAddress;
            final byte[] currentOffsetBytecode;
            final long[] elements;
            final int tableIndex;
            final byte elemType;
            if (bulkMemoryAndRefTypes) {
                final int sectionType = readUnsignedInt32();
                mode = sectionType & 0b001;
                final boolean useTableIndex = (sectionType & 0b010) != 0;
                final boolean useExpressions = (sectionType & 0b100) != 0;
                final boolean useType = (sectionType & 0b011) != 0;
                if (mode == SegmentMode.ACTIVE) {
                    if (useTableIndex) {
                        tableIndex = readTableIndex();
                    } else {
                        tableIndex = 0;
                    }
                    Pair<Integer, byte[]> offsetExpression = readOffsetExpression();
                    currentOffsetAddress = offsetExpression.getLeft();
                    currentOffsetBytecode = offsetExpression.getRight();
                } else {
                    mode = useTableIndex ? SegmentMode.DECLARATIVE : SegmentMode.PASSIVE;
                    tableIndex = 0;
                    currentOffsetAddress = -1;
                    currentOffsetBytecode = null;
                }
                if (useExpressions) {
                    if (useType) {
                        elemType = readRefType();
                    } else {
                        elemType = FUNCREF_TYPE;
                    }
                    elements = readElemExpressions(elemType);
                } else {
                    if (useType) {
                        checkElemKind();
                    }
                    elemType = FUNCREF_TYPE;
                    elements = readFunctionIndices();
                }
            } else {
                mode = SegmentMode.ACTIVE;
                tableIndex = readTableIndex();
                Pair<Integer, byte[]> offsetExpression = readOffsetExpression();
                currentOffsetAddress = offsetExpression.getLeft();
                currentOffsetBytecode = offsetExpression.getRight();
                elements = readFunctionIndices();
                elemType = FUNCREF_TYPE;
            }

            // Copy the contents, or schedule a linker task for this.
            final int currentElemSegmentId = elemSegmentIndex;
            final int elementCount = elements.length;
            final int headerOffset = bytecode.location();
            final int bytecodeOffset = bytecode.addElemHeader(mode, elementCount, elemType, tableIndex, currentOffsetBytecode, currentOffsetAddress);
            module.setElemInstance(currentElemSegmentId, headerOffset, elemType);
            if (mode == SegmentMode.ACTIVE) {
                assertTrue(module.checkTableIndex(tableIndex), Failure.UNKNOWN_TABLE);
                module.addLinkAction(((context, instance) -> context.linker().resolveElemSegment(context, instance, tableIndex, currentElemSegmentId, currentOffsetAddress,
                                currentOffsetBytecode, bytecodeOffset, elementCount)));
            } else if (mode == SegmentMode.PASSIVE) {
                module.addLinkAction(((context, instance) -> context.linker().resolvePassiveElemSegment(context, instance, currentElemSegmentId, bytecodeOffset, elementCount)));
            }
            for (long element : elements) {
                final int initType = (int) (element >> 32);
                switch (initType) {
                    case NULL_TYPE:
                        bytecode.addElemNull();
                        break;
                    case FUNCREF_TYPE:
                        final int functionIndex = (int) element;
                        bytecode.addElemFunctionIndex(functionIndex);
                        break;
                    case I32_TYPE:
                        final int globalIndex = (int) element;
                        bytecode.addElemGlobalIndex(globalIndex);
                        break;
                }
            }
        }
    }

    private void readEnd() {
        final byte instruction = read1();
        assertByteEqual(instruction, (byte) Instructions.END, Failure.TYPE_MISMATCH);
    }

    private void readStartSection() {
        int startFunctionIndex = readDeclaredFunctionIndex();
        module.symbolTable().setStartFunction(startFunctionIndex);
    }

    private void readExportSection() {
        int exportsCount = readLength();

        module.limits().checkExportCount(exportsCount);
        for (int exportIndex = 0; exportIndex != exportsCount; ++exportIndex) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            String exportName = readName();
            byte exportType = readExportType();
            switch (exportType) {
                case ExportIdentifier.FUNCTION: {
                    int functionIndex = readDeclaredFunctionIndex();
                    module.symbolTable().exportFunction(functionIndex, exportName);
                    module.addFunctionReference(functionIndex);
                    break;
                }
                case ExportIdentifier.TABLE: {
                    final int tableIndex = readTableIndex();
                    module.symbolTable().exportTable(tableIndex, exportName);
                    break;
                }
                case ExportIdentifier.MEMORY: {
                    final int memoryIndex = readMemoryIndex();
                    module.symbolTable().exportMemory(memoryIndex, exportName);
                    break;
                }
                case ExportIdentifier.GLOBAL: {
                    int index = readGlobalIndex();
                    module.symbolTable().exportGlobal(exportName, index);
                    break;
                }
                default: {
                    fail(Failure.UNSPECIFIED_MALFORMED, String.format("Invalid export type identifier: 0x%02X", exportType));
                }
            }
        }
    }

    private void readGlobalSection() {
        final int globalCount = readLength();
        module.limits().checkGlobalCount(globalCount);
        final int startingGlobalIndex = module.symbolTable().numGlobals();
        for (int globalIndex = startingGlobalIndex; globalIndex != startingGlobalIndex + globalCount; globalIndex++) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            final byte type = readValueType(bulkMemoryAndRefTypes, simd);
            // 0x00 means const, 0x01 means var
            final byte mutability = readMutability();
            // Global initialization expressions must be constant expressions:
            // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions
            Pair<Object, byte[]> initExpression = readConstantExpression(type, true);
            final Object initValue = initExpression.getLeft();
            final byte[] initBytecode = initExpression.getRight();
            final boolean isInitialized = initBytecode == null;

            module.symbolTable().declareGlobal(globalIndex, type, mutability, isInitialized, initBytecode, initValue);
            final int currentGlobalIndex = globalIndex;
            module.addLinkAction((context, instance) -> {
                if (isInitialized) {
                    context.globals().store(type, instance.globalAddress(currentGlobalIndex), initValue);
                    context.linker().resolveGlobalInitialization(instance, currentGlobalIndex);
                } else {
                    context.linker().resolveGlobalInitialization(context, instance, currentGlobalIndex, initBytecode);
                }
            });
        }
    }

    private void readDataCountSection(int size) {
        if (size == 0) {
            module.setDataSegmentCount(0);
        } else {
            module.setDataSegmentCount(readUnsignedInt32());
        }
    }

    private void readDataSection(RuntimeBytecodeGen bytecode) {
        final int dataSegmentCount = readLength();
        module.limits().checkDataSegmentCount(dataSegmentCount);
        if (bulkMemoryAndRefTypes && dataSegmentCount != 0) {
            module.checkDataSegmentCount(dataSegmentCount);
        }
        final int droppedDataInstanceOffset = bytecode.location();
        module.setDroppedDataInstanceOffset(droppedDataInstanceOffset);
        bytecode.add(Bytecode.UNREACHABLE);
        for (int dataSegmentIndex = 0; dataSegmentIndex != dataSegmentCount; ++dataSegmentIndex) {
            assertTrue(!isEOF(), Failure.LENGTH_OUT_OF_BOUNDS);
            final int mode;
            long offsetAddress;
            final byte[] offsetBytecode;
            final int memoryIndex;
            if (bulkMemoryAndRefTypes) {
                final int sectionType = readUnsignedInt32();
                mode = sectionType & 0b01;
                final boolean useMemoryIndex = (sectionType & 0b10) != 0;
                if (useMemoryIndex && multiMemory) {
                    memoryIndex = readMemoryIndex();
                } else if (useMemoryIndex) {
                    readMemoryIndex();
                    memoryIndex = 0;
                } else {
                    memoryIndex = 0;
                }
                if (mode == SegmentMode.ACTIVE) {
                    checkMemoryIndex(memoryIndex);
                    if (module.memoryHasIndexType64(memoryIndex)) {
                        Pair<Long, byte[]> offsetExpression = readLongOffsetExpression();
                        offsetAddress = offsetExpression.getLeft();
                        offsetBytecode = offsetExpression.getRight();
                    } else {
                        Pair<Integer, byte[]> offsetExpression = readOffsetExpression();
                        offsetAddress = offsetExpression.getLeft();
                        offsetBytecode = offsetExpression.getRight();
                    }
                } else {
                    offsetAddress = -1;
                    offsetBytecode = null;
                }
            } else {
                mode = SegmentMode.ACTIVE;
                if (multiMemory) {
                    memoryIndex = readMemoryIndex();
                } else {
                    readMemoryIndex();
                    memoryIndex = 0;
                }
                if (module.memoryHasIndexType64(memoryIndex)) {
                    Pair<Long, byte[]> offsetExpression = readLongOffsetExpression();
                    offsetAddress = offsetExpression.getLeft();
                    offsetBytecode = offsetExpression.getRight();
                } else {
                    Pair<Integer, byte[]> offsetExpression = readOffsetExpression();
                    offsetAddress = offsetExpression.getLeft();
                    offsetBytecode = offsetExpression.getRight();
                }
            }

            final int byteLength = readLength();
            final int currentDataSegmentId = dataSegmentIndex;

            final int headerOffset = bytecode.location();
            if (mode == SegmentMode.ACTIVE) {
                checkMemoryIndex(memoryIndex);
                final long currentOffsetAddress = offsetAddress;
                bytecode.addDataHeader(byteLength, offsetBytecode, currentOffsetAddress, memoryIndex);
                final int bytecodeOffset = bytecode.location();
                module.setDataInstance(currentDataSegmentId, headerOffset);
                module.addLinkAction(
                                (context, instance) -> context.linker().resolveDataSegment(context, instance, currentDataSegmentId, memoryIndex, currentOffsetAddress, offsetBytecode, byteLength,
                                                bytecodeOffset, droppedDataInstanceOffset));
            } else {
                bytecode.addDataHeader(mode, byteLength);
                final int bytecodeOffset = bytecode.location();
                bytecode.addDataRuntimeHeader(byteLength, unsafeMemory);
                module.setDataInstance(currentDataSegmentId, headerOffset);
                module.addLinkAction((context, instance) -> context.linker().resolvePassiveDataSegment(context, instance, currentDataSegmentId, bytecodeOffset, byteLength));
            }
            // Add the data section to the bytecode.
            for (int i = 0; i < byteLength; i++) {
                bytecode.addByte(read1());
            }
        }
    }

    private void readFunctionType() {
        int paramCount = readLength();
        long resultCountAndValue = peekUnsignedInt32AndLength(data, offset + paramCount);
        int resultCount = value(resultCountAndValue);
        resultCount = (resultCount == 0x40) ? 0 : resultCount;

        module.limits().checkParamCount(paramCount);
        module.limits().checkResultCount(resultCount, multiValue);
        int idx = module.symbolTable().allocateFunctionType(paramCount, resultCount, multiValue);
        readParameterList(idx, paramCount);
        offset += length(resultCountAndValue);
        readResultList(idx, resultCount);
    }

    private void readParameterList(int funcTypeIdx, int paramCount) {
        for (int paramIdx = 0; paramIdx != paramCount; ++paramIdx) {
            byte type = readValueType(bulkMemoryAndRefTypes, simd);
            module.symbolTable().registerFunctionTypeParameterType(funcTypeIdx, paramIdx, type);
        }
    }

    private void readResultList(int funcTypeIdx, int resultCount) {
        for (int resultIdx = 0; resultIdx != resultCount; resultIdx++) {
            byte type = readValueType(bulkMemoryAndRefTypes, simd);
            module.symbolTable().registerFunctionTypeResultType(funcTypeIdx, resultIdx, type);
        }
    }

    private int readMemoryIndexFromAlignHint(int alignHint) {
        // if bit 6 (the MSB of the first LEB byte) is set, then an i32 memory index follows after
        // the alignment bitfield
        final int memoryIndex;
        if (multiMemory && (alignHint & 0b0100_0000) != 0) {
            memoryIndex = readMemoryIndex();
        } else {
            memoryIndex = 0;
            checkMemoryIndex(0);
        }
        return memoryIndex;
    }

    private long readBaseMemoryOffset() {
        final long memoryOffset;
        if (memory64) {
            memoryOffset = readUnsignedInt64(); // 64-bit store offset
        } else {
            memoryOffset = Integer.toUnsignedLong(readUnsignedInt32()); // 32-bit store offset
        }
        return memoryOffset;
    }

    private boolean isEOF() {
        return offset == data.length;
    }

    private int readDeclaredFunctionIndex() {
        final int index = readUnsignedInt32();
        module.symbolTable().checkFunctionIndex(index);
        return index;
    }

    private int readTypeIndex() {
        final int result = readUnsignedInt32();
        assertUnsignedIntLess(result, module.symbolTable().typeCount(), Failure.UNKNOWN_TYPE);
        return result;
    }

    private int readFunctionIndex() {
        return readUnsignedInt32();
    }

    private int readTableIndex() {
        final int index = readUnsignedInt32();
        assertTrue(module.symbolTable().checkTableIndex(index), Failure.UNKNOWN_TABLE);
        return index;
    }

    private int readMemoryIndex() {
        return checkMemoryIndex(readUnsignedInt32());
    }

    private int checkMemoryIndex(int index) {
        assertUnsignedIntLess(index, module.symbolTable().memoryCount(), Failure.UNKNOWN_MEMORY);
        return index;
    }

    private int readGlobalIndex() {
        final int index = readUnsignedInt32();
        assertUnsignedIntLess(index, module.symbolTable().numGlobals(), Failure.UNKNOWN_GLOBAL);
        return index;
    }

    private int readLocalIndex() {
        return readUnsignedInt32();
    }

    private int readTargetOffset() {
        return readUnsignedInt32();
    }

    private byte readExportType() {
        return read1();
    }

    private byte readImportType() {
        return read1();
    }

    private byte readRefType() {
        final byte refType = read1();
        switch (refType) {
            case FUNCREF_TYPE:
            case EXTERNREF_TYPE:
                break;
            default:
                fail(Failure.MALFORMED_REFERENCE_TYPE, "Unexpected reference type");
                break;
        }
        return refType;
    }

    private void readTableLimits(int[] out) {
        readLimits(out, MAX_TABLE_DECLARATION_SIZE);
        assertUnsignedIntLessOrEqual(out[0], out[1], Failure.LIMIT_MINIMUM_GREATER_THAN_MAXIMUM);
    }

    private void readMemoryLimits(long[] longOut, boolean[] boolOut) {
        readLongLimits(longOut, boolOut, MAX_MEMORY_DECLARATION_SIZE, MAX_MEMORY_64_DECLARATION_SIZE);
        final boolean is64Bit = boolOut[0];
        if (is64Bit) {
            assertUnsignedLongLessOrEqual(longOut[0], MAX_MEMORY_64_DECLARATION_SIZE, Failure.MEMORY_64_SIZE_LIMIT_EXCEEDED);
            assertUnsignedLongLessOrEqual(longOut[1], MAX_MEMORY_64_DECLARATION_SIZE, Failure.MEMORY_64_SIZE_LIMIT_EXCEEDED);
            assertUnsignedLongLessOrEqual(longOut[0], longOut[1], Failure.LIMIT_MINIMUM_GREATER_THAN_MAXIMUM);
        } else {
            assertUnsignedIntLessOrEqual((int) longOut[0], MAX_MEMORY_DECLARATION_SIZE, Failure.MEMORY_SIZE_LIMIT_EXCEEDED);
            assertUnsignedIntLessOrEqual((int) longOut[1], MAX_MEMORY_DECLARATION_SIZE, Failure.MEMORY_SIZE_LIMIT_EXCEEDED);
            assertUnsignedIntLessOrEqual((int) longOut[0], (int) longOut[1], Failure.LIMIT_MINIMUM_GREATER_THAN_MAXIMUM);
        }
    }

    private void readLimits(int[] out, int max) {
        final byte limitsPrefix = readLimitsPrefix();
        switch (limitsPrefix) {
            case LimitsPrefix.NO_MAX: {
                out[0] = readUnsignedInt32();
                out[1] = max;
                break;
            }
            case LimitsPrefix.WITH_MAX: {
                out[0] = readUnsignedInt32();
                out[1] = readUnsignedInt32();
                break;
            }
            default:
                if (limitsPrefix < 0) {
                    fail(Failure.INTEGER_REPRESENTATION_TOO_LONG, String.format("Invalid limits prefix (expected 0x00 or 0x01, got 0x%02X)", limitsPrefix));
                } else {
                    fail(Failure.INTEGER_TOO_LARGE, String.format("Invalid limits prefix (expected 0x00 or 0x01, got 0x%02X)", limitsPrefix));
                }
        }
    }

    private void readLongLimits(long[] longOut, boolean[] boolOut, int max32Bit, long max64Bit) {
        final byte limitsPrefix = readLimitsPrefix();
        switch (limitsPrefix) {
            case 0x00: {
                longOut[0] = readUnsignedInt32();
                longOut[1] = max32Bit;
                boolOut[0] = false; // not 64-bit
                boolOut[1] = false; // not shared
                break;
            }
            case 0x01: {
                longOut[0] = readUnsignedInt32();
                longOut[1] = readUnsignedInt32();
                boolOut[0] = false;
                boolOut[1] = false;
                break;
            }
            case 0x04: {
                longOut[0] = readUnsignedInt64();
                longOut[1] = max64Bit;
                boolOut[0] = true;
                boolOut[1] = false;
                break;
            }
            case 0x05: {
                longOut[0] = readUnsignedInt64();
                longOut[1] = readUnsignedInt64();
                boolOut[0] = true;
                boolOut[1] = false;
                break;
            }
            default: {
                if (!threads) {
                    if (limitsPrefix < 0) {
                        fail(Failure.INTEGER_REPRESENTATION_TOO_LONG, String.format("Invalid limits prefix (expected 0x00, 0x01, 0x04, or 0x05, got 0x%02X)", limitsPrefix));
                    } else {
                        fail(Failure.INTEGER_TOO_LARGE, String.format("Invalid limits prefix (expected 0x00, 0x01, 0x04, or 0x05, got 0x%02X)", limitsPrefix));
                    }
                } else {
                    switch (limitsPrefix) {
                        case 0x02:
                        case 0x06: {
                            fail(Failure.SHARED_MEMORY_MUST_HAVE_MAXIMUM, String.format("Limits prefix implies shared memory without meximum (got 0x%02X)", limitsPrefix));
                            break;
                        }
                        case 0x03: {
                            longOut[0] = readUnsignedInt32();
                            longOut[1] = readUnsignedInt32();
                            boolOut[0] = false;
                            boolOut[1] = true;
                            break;
                        }
                        case 0x07: {
                            longOut[0] = readUnsignedInt64();
                            longOut[1] = readUnsignedInt64();
                            boolOut[0] = true;
                            boolOut[1] = true;
                            break;
                        }
                        default:
                            if (limitsPrefix < 0) {
                                fail(Failure.INTEGER_REPRESENTATION_TOO_LONG, String.format("Invalid limits prefix (expected 0x00-0x07, got 0x%02X)", limitsPrefix));
                            } else {
                                fail(Failure.INTEGER_TOO_LARGE, String.format("Invalid limits prefix (expected 0x00-0x07, got 0x%02X)", limitsPrefix));
                            }
                    }
                }
            }
        }
    }

    private byte readLimitsPrefix() {
        return read1();
    }

    private String readName() {
        int nameLength = readLength();
        assertUnsignedIntLessOrEqual(offset + nameLength, data.length, Failure.LENGTH_OUT_OF_BOUNDS);

        // Decode and verify UTF-8 encoding of the name
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer result;
        try {
            result = decoder.decode(ByteBuffer.wrap(data, offset, nameLength));
        } catch (CharacterCodingException ex) {
            throw WasmException.format(Failure.MALFORMED_UTF8, "Invalid UTF-8 encoding of the name at: %d", offset);
        }
        offset += nameLength;
        return result.toString();
    }

    protected int readLength() {
        final int value = readUnsignedInt32();
        assertUnsignedIntLessOrEqual(value, data.length, Failure.LENGTH_OUT_OF_BOUNDS);
        return value;
    }

    protected int readAlignHint(int n) {
        final int value = readUnsignedInt32();
        assertUnsignedIntLessOrEqual(1 << value, n / 8, Failure.ALIGNMENT_LARGER_THAN_NATURAL);
        return value;
    }

    protected int readAtomicAlignHint(int n) {
        final int value = readUnsignedInt32();
        assertIntEqual(1 << value, n / 8, Failure.ATOMIC_ALIGNMENT_NOT_NATURAL);
        return value;
    }

    protected int readUnsignedInt32() {
        final long valueLength = peekUnsignedInt32AndLength(data, offset);
        offset += length(valueLength);
        return value(valueLength);
    }

    protected int readSignedInt32() {
        final long valueLength = peekSignedInt32AndLength(data, offset);
        offset += length(valueLength);
        return value(valueLength);
    }

    protected long readUnsignedInt64() {
        final long value = peekUnsignedInt64(data, offset, true);
        final byte length = peekLeb128Length(data, offset);
        offset += length;
        return value;
    }

    private long readSignedInt64() {
        final long value = peekSignedInt64(data, offset, true);
        final byte length = peekLeb128Length(data, offset);
        offset += length;
        return value;
    }

    private Vector128 readUnsignedInt128() {
        byte[] bytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            bytes[i] = read1();
        }
        return Vector128.ofBytes(bytes);
    }

    /**
     * Creates a runtime bytecode of a function with added debug opcodes.
     *
     * @param functionIndex the function index
     * @param sourceLocationToLineMap a mapping from source code locations to source code lines
     *            numbers (extracted from debugging information)
     */
    @TruffleBoundary
    public byte[] createFunctionDebugBytecode(int functionIndex, EconomicMap<Integer, Integer> sourceLocationToLineMap) {
        final RuntimeBytecodeGen bytecode = new RuntimeBytecodeGen();
        final int codeEntryIndex = functionIndex - module.numImportedFunctions();
        final CodeEntry codeEntry = BytecodeParser.readCodeEntry(module, module.bytecode(), codeEntryIndex);
        offset = module.functionSourceCodeInstructionOffset(functionIndex);
        final int endOffset = module.functionSourceCodeEndOffset(functionIndex);
        readFunction(functionIndex, codeEntry.localTypes(), codeEntry.resultTypes(), endOffset, true, bytecode, codeEntryIndex, sourceLocationToLineMap);
        return bytecode.toArray();
    }
}
