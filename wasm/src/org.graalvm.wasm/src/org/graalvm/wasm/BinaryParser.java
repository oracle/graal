/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.source.Source;
import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.constants.CallIndirect;
import org.graalvm.wasm.constants.ExportIdentifier;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.constants.ImportIdentifier;
import org.graalvm.wasm.constants.Instructions;
import org.graalvm.wasm.constants.LimitsPrefix;
import org.graalvm.wasm.constants.Section;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.parser.ir.BlockNode;
import org.graalvm.wasm.parser.ir.CallNode;
import org.graalvm.wasm.parser.ir.CodeEntry;
import org.graalvm.wasm.parser.ir.IfNode;
import org.graalvm.wasm.parser.ir.LoopNode;
import org.graalvm.wasm.parser.ir.ParserNode;
import org.graalvm.wasm.parser.module.WasmDataDefinition;
import org.graalvm.wasm.parser.module.WasmElementDefinition;
import org.graalvm.wasm.parser.module.WasmExternalValue;
import org.graalvm.wasm.parser.module.WasmFunctionDefinition;
import org.graalvm.wasm.parser.module.WasmGlobalDefinition;
import org.graalvm.wasm.parser.module.WasmMemoryDefinition;
import org.graalvm.wasm.parser.module.WasmModule;
import org.graalvm.wasm.parser.module.WasmTableDefinition;
import org.graalvm.wasm.parser.module.imports.WasmFunctionImport;
import org.graalvm.wasm.parser.module.imports.WasmGlobalImport;
import org.graalvm.wasm.parser.module.imports.WasmMemoryImport;
import org.graalvm.wasm.parser.module.imports.WasmTableImport;
import org.graalvm.wasm.parser.validation.ValidationErrors;
import org.graalvm.wasm.parser.validation.ValidationState;
import org.graalvm.wasm.runtime.WasmFunctionType;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.graalvm.wasm.Assert.assertByteEqual;
import static org.graalvm.wasm.Assert.assertIntEqual;
import static org.graalvm.wasm.Assert.assertIntLessOrEqual;
import static org.graalvm.wasm.Assert.assertUnsignedIntLess;
import static org.graalvm.wasm.Assert.assertUnsignedIntLessOrEqual;
import static org.graalvm.wasm.Assert.fail;
import static org.graalvm.wasm.WasmType.F32_TYPE;
import static org.graalvm.wasm.WasmType.F64_TYPE;
import static org.graalvm.wasm.WasmType.I32_TYPE;
import static org.graalvm.wasm.WasmType.I64_TYPE;
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_DECLARATION_SIZE;
import static org.graalvm.wasm.constants.Sizes.MAX_TABLE_DECLARATION_SIZE;

/**
 * Simple recursive-descend parser for the binary WebAssembly format.
 */
public class BinaryParser extends BinaryStreamParser {

    private static final int MAGIC = 0x6d736100;
    private static final int VERSION = 0x00000001;

    private final WasmContext wasmContext;
    private final int[] limitsResult;

    private final ModuleLimits limits;

    @CompilerDirectives.TruffleBoundary
    public BinaryParser(WasmContext context, byte[] moduleData, ModuleLimits limits) {
        super(moduleData);
        this.wasmContext = context;
        this.limitsResult = new int[2];
        this.limits = limits == null ? ModuleLimits.DEFAULTS : limits;
    }

    @CompilerDirectives.TruffleBoundary
    public WasmModule readModule(String name, Source source) {
        limits.checkModuleSize(data.length);
        validateMagicNumberAndVersion();
        final WasmModule module = new WasmModule(name, source, data.length);
        readSymbolSections(module);
        return module;
    }

    private void validateMagicNumberAndVersion() {
        assertIntEqual(read4(), MAGIC, Failure.INVALID_MAGIC_NUMBER);
        assertIntEqual(read4(), VERSION, Failure.INVALID_VERSION_NUMBER);
    }

    private void readSymbolSections(final WasmModule module) {
        int lastNonCustomSection = -1;
        boolean hasCodeSection = false;
        while (!isEOF()) {
            final byte sectionID = read1();

            if (sectionID != Section.CUSTOM) {
                if (sectionID > lastNonCustomSection) {
                    lastNonCustomSection = sectionID;
                } else if (lastNonCustomSection == sectionID) {
                    throw WasmException.create(Failure.DUPLICATED_SECTION, "Duplicated section " + sectionID);
                } else {
                    throw WasmException.create(Failure.INVALID_SECTION_ORDER, "Section " + sectionID + " defined after section " + lastNonCustomSection);
                }
            }

            final int size = readLength();
            final int startOffset = offset;
            switch (sectionID) {
                case Section.CUSTOM:
                    readCustomSection(size, module);
                    break;
                case Section.TYPE:
                    readTypeSection(module);
                    break;
                case Section.IMPORT:
                    readImportSection(module);
                    break;
                case Section.FUNCTION:
                    readFunctionSection(module);
                    break;
                case Section.TABLE:
                    readTableSection(module);
                    break;
                case Section.MEMORY:
                    readMemorySection(module);
                    break;
                case Section.GLOBAL:
                    readGlobalSection(module);
                    break;
                case Section.EXPORT:
                    readExportSection(module);
                    break;
                case Section.START:
                    readStartSection(module);
                    break;
                case Section.ELEMENT:
                    readElementSection(module);
                    break;
                case Section.CODE:
                    readCodeSection(module);
                    hasCodeSection = true;
                    break;
                case Section.DATA:
                    readDataSection(module);
                    break;
                default:
                    fail(Failure.MALFORMED_SECTION_ID, "invalid section ID: " + sectionID);
            }
            assertIntEqual(offset - startOffset, size, String.format("Declared section (0x%02X) size is incorrect", sectionID), Failure.SECTION_SIZE_MISMATCH);
        }
        if (!hasCodeSection) {
            assertIntEqual(module.getFunctionCount(), module.getFunctionImportCount(), Failure.FUNCTIONS_CODE_INCONSISTENT_LENGTHS);
        }
    }

    private void readCustomSection(int size, WasmModule module) {
        final int sectionEndOffset = offset + size;
        final String name = readName();
        Assert.assertUnsignedIntLessOrEqual(offset, sectionEndOffset, Failure.UNEXPECTED_END);
        Assert.assertUnsignedIntLessOrEqual(sectionEndOffset, data.length, Failure.UNEXPECTED_END);
        module.addCustomSection(new WasmCustomSection(name, offset, sectionEndOffset - offset, data));
        if ("name".equals(name)) {
            try {
                readNameSection(module);
            } catch (WasmException ex) {
                // Malformed name section should not result in invalidation of the module
                assert ex.getExceptionType() == ExceptionType.PARSE_ERROR;
            }
        }
        offset = sectionEndOffset;
    }

    /**
     * @see <a href=
     *      "https://webassembly.github.io/spec/core/appendix/custom.html#binary-namesubsection"><code>namedata</code>
     *      binary specification</a>
     */
    private void readNameSection(WasmModule module) {
        if (!isEOF() && peek1() == 0) {
            readModuleName();
        }
        if (!isEOF() && peek1() == 1) {
            readFunctionNames(module);
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
    private void readFunctionNames(WasmModule module) {
        final int subsectionId = read1();
        assert subsectionId == 1;
        final int size = readLength();
        final int startOffset = offset;
        final int length = readLength();
        final int maxFunctionIndex = module.getFunctionCount() - 1;
        for (int i = 0; i < length; ++i) {
            final int functionIndex = readFunctionIndex();
            assertIntLessOrEqual(0, functionIndex, "Negative function index", Failure.UNSPECIFIED_MALFORMED);
            assertIntLessOrEqual(functionIndex, maxFunctionIndex, "Function index too large", Failure.UNSPECIFIED_MALFORMED);
            String functionName = readName();
            module.setFunctionName(functionIndex, functionName);
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

    private void readTypeSection(WasmModule module) {
        final int numTypes = readLength();
        limits.checkTypeCount(numTypes);
        WasmFunctionType[] functionTypes = new WasmFunctionType[numTypes];
        for (int t = 0; t != numTypes; ++t) {
            final byte type = read1();
            switch (type) {
                case 0x60:
                    functionTypes[t] = readFunctionType();
                    break;
                default:
                    fail(Failure.UNSPECIFIED_MALFORMED, "Only function types are supported in the type section");
            }
        }
        module.setFunctionTypes(functionTypes);
    }

    private void readImportSection(WasmModule module) {
        assertIntEqual(module.getGlobalCount(), 0,
                        "The global index should be -1 when the import section is first read.", Failure.UNSPECIFIED_INVALID);
        int numImports = readLength();
        limits.checkImportCount(numImports);

        List<WasmFunctionImport> functionImports = new ArrayList<>();
        List<WasmTableImport> tableImports = new ArrayList<>();
        List<WasmMemoryImport> memoryImports = new ArrayList<>();
        List<WasmGlobalImport> globalImports = new ArrayList<>();
        for (int i = 0; i != numImports; ++i) {
            String moduleName = readName();
            String memberName = readName();
            byte importType = readImportType();
            switch (importType) {
                case ImportIdentifier.FUNCTION: {
                    int typeIndex = readTypeIndex(module);
                    WasmFunctionType functionType = module.getFunctionTypeAtIndex(typeIndex);
                    functionImports.add(new WasmFunctionImport(i, moduleName, memberName, functionType));
                    break;
                }
                case ImportIdentifier.TABLE: {
                    byte elemType = readElemType();
                    assertIntEqual(elemType, ReferenceTypes.FUNCREF, "Invalid element type for table import", Failure.UNSPECIFIED_MALFORMED);
                    readTableLimits(limitsResult);
                    assertIntEqual(tableImports.size() + 1, 1, "A table has already been defined", Failure.MULTIPLE_TABLES);
                    tableImports.add(new WasmTableImport(i, moduleName, memberName, limitsResult[0], limitsResult[1]));
                    break;
                }
                case ImportIdentifier.MEMORY: {
                    readMemoryLimits(limitsResult);
                    assertIntEqual(memoryImports.size() + 1, 1, "A memory has already been defined", Failure.MULTIPLE_MEMORIES);
                    memoryImports.add(new WasmMemoryImport(i, moduleName, memberName, limitsResult[0], limitsResult[1]));
                    break;
                }
                case ImportIdentifier.GLOBAL: {
                    byte type = readValueType();
                    byte mutability = readMutability();
                    globalImports.add(new WasmGlobalImport(i, moduleName, memberName, mutability, type));
                    break;
                }
                default: {
                    fail(Failure.UNSPECIFIED_MALFORMED, String.format("Invalid import type identifier: 0x%02X", importType));
                }
            }
        }
        module.setImports(functionImports.toArray(new WasmFunctionImport[0]), tableImports.toArray(new WasmTableImport[0]), memoryImports.toArray(new WasmMemoryImport[0]),
                        globalImports.toArray(new WasmGlobalImport[0]));
    }

    private void readFunctionSection(WasmModule module) {
        int numFunctions = readLength();
        WasmFunctionDefinition[] functions = new WasmFunctionDefinition[numFunctions];
        limits.checkFunctionCount(numFunctions);
        for (int i = 0; i != numFunctions; ++i) {
            int functionTypeIndex = readTypeIndex(module);
            functions[i] = new WasmFunctionDefinition(i, module.getFunctionTypeAtIndex(functionTypeIndex), data);
        }
        module.setFunctions(functions);
    }

    private void readTableSection(WasmModule module) {
        final int numTables = readLength();
        // Since in the current version of WebAssembly supports at most one table instance per
        // module, this loop should be executed at most once.
        WasmTableDefinition[] tables = new WasmTableDefinition[numTables];
        for (byte tableIndex = 0; tableIndex != numTables; ++tableIndex) {
            assertIntEqual(tableIndex + module.getTableCount() + 1, 1, "A table has already been defined", Failure.MULTIPLE_TABLES);
            final byte elemType = readElemType();
            assertIntEqual(elemType, ReferenceTypes.FUNCREF, "Invalid element type for table", Failure.UNSPECIFIED_MALFORMED);
            readTableLimits(limitsResult);
            tables[tableIndex] = new WasmTableDefinition(limitsResult[0], limitsResult[1]);
        }
        module.setTables(tables);
    }

    private void readMemorySection(WasmModule module) {
        final int numMemories = readLength();
        // Since in the current version of WebAssembly supports at most one memory instance per
        // module, this loop should be executed at most once.
        WasmMemoryDefinition[] memories = new WasmMemoryDefinition[numMemories];
        for (byte memoryIndex = 0; memoryIndex != numMemories; ++memoryIndex) {
            assertIntEqual(memoryIndex + module.getMemoryCount() + 1, 1, "A memory has already been defined", Failure.MULTIPLE_MEMORIES);
            readMemoryLimits(limitsResult);
            memories[memoryIndex] = new WasmMemoryDefinition(limitsResult[0], limitsResult[1]);
        }
        module.setMemories(memories);
    }

    private void readCodeSection(WasmModule module) {
        final int numCodeEntries = readLength();
        assertIntEqual(numCodeEntries, module.getLocalFunctionCount(), Failure.FUNCTIONS_CODE_INCONSISTENT_LENGTHS);
        for (int entryIndex = 0; entryIndex != numCodeEntries; ++entryIndex) {
            final int codeEntrySize = readUnsignedInt32();
            final int startOffset = offset;
            limits.checkFunctionSize(codeEntrySize);
            final ByteArrayList locals = readCodeEntryLocals();
            final WasmFunctionDefinition function = module.getLocalFunction(entryIndex);
            final int localCount = locals.size() + function.getFunctionType().getParameterCount();
            limits.checkLocalCount(localCount);
            function.setBody(readCodeEntry(function, locals, module));
            assertIntEqual(offset - startOffset, codeEntrySize, String.format("Code entry %d size is incorrect", entryIndex), Failure.UNSPECIFIED_MALFORMED);
        }
    }

    private CodeEntry readCodeEntry(WasmFunctionDefinition function, ByteArrayList locals, WasmModule module) {
        final WasmFunctionType functionType = function.getFunctionType();
        int numberOfArguments = functionType.getParameterCount();
        byte[] localTypes = new byte[numberOfArguments + locals.size()];
        System.arraycopy(functionType.getParameterTypes(), 0, localTypes, 0, numberOfArguments);
        for (int i = 0; i < locals.size(); i++) {
            localTypes[i + numberOfArguments] = locals.get(i);
        }
        final byte returnType = functionType.getReturnType();
        final int returnTypeLength = functionType.getReturnCount();

        ValidationState state = new ValidationState();
        BlockNode functionBody = readCodeBlock(state, localTypes, returnType, Instructions.BLOCK, module);
        final CodeEntry codeEntry = new CodeEntry(functionBody, state.currentProfileCount(), state.maxStackSize(), state.intConstants(), state.branchTables(), localTypes);

        assertIntEqual(state.valueStackSize(), returnTypeLength,
                        "Stack size must match the return type length at the function end", Failure.TYPE_MISMATCH);
        return codeEntry;
    }

    private ByteArrayList readCodeEntryLocals() {
        final int numLocalsGroups = readLength();
        final ByteArrayList localTypes = new ByteArrayList();
        int localsLength = 0;
        for (int localGroup = 0; localGroup < numLocalsGroups; localGroup++) {
            final int groupLength = readUnsignedInt32();
            localsLength += groupLength;
            limits.checkLocalCount(localsLength);
            final byte t = readValueType();
            for (int i = 0; i != groupLength; ++i) {
                localTypes.add(t);
            }
        }
        return localTypes;
    }

    private BlockNode readCodeBlock(ValidationState state, byte[] locals, byte returnType, int blockOpcode, WasmModule module) {
        final ArrayList<ParserNode> children = new ArrayList<>();
        final int startOffset = offset;
        final int initialStackPointer = state.valueStackSize();
        final int initialIntConstantOffset = state.intConstantSize();
        final int initialBranchTableOffset = state.branchTableSize();
        final int initialProfileOffset = state.currentProfileCount();
        state.enterBlock(blockOpcode, returnType);
        int opcode;
        do {
            opcode = read1() & 0xFF;
            switch (opcode) {
                case Instructions.UNREACHABLE:
                    state.setUnreachable();
                    break;
                case Instructions.NOP:
                    break;
                case Instructions.BLOCK: {
                    final byte blockReturnType = readBlockType();
                    BlockNode blockNode = readCodeBlock(state, locals, blockReturnType, opcode, module);
                    children.add(blockNode);
                    break;
                }
                case Instructions.LOOP: {
                    final byte loopReturnType = readBlockType();
                    BlockNode blockNode = readCodeBlock(state, locals, loopReturnType, opcode, module);
                    LoopNode loopNode = new LoopNode(blockNode);
                    children.add(loopNode);
                    break;
                }
                case Instructions.IF: {
                    int stackSizeBeforeCondition = state.valueStackSize();
                    state.popChecked(I32_TYPE); // condition
                    final byte ifReturnType = readBlockType();
                    BlockNode thenNode = readCodeBlock(state, locals, ifReturnType, opcode, module);
                    BlockNode elseNode = null;
                    int nextOpcode = read1() & 0xFF;
                    if (nextOpcode == Instructions.ELSE) {
                        elseNode = readCodeBlock(state, locals, ifReturnType, nextOpcode, module);
                    } else if (ifReturnType != WasmType.VOID_TYPE) {
                        throw WasmException.format(Failure.TYPE_MISMATCH, "Expected else branch. If with result value requires then and else branch.");
                    }
                    IfNode ifNode = new IfNode(stackSizeBeforeCondition, thenNode, elseNode);
                    children.add(ifNode);
                    break;
                }
                case Instructions.END: {
                    break;
                }
                case Instructions.ELSE: {
                    // We handle the else instruction in the same way as the end instruction.
                    // The if instruction handles starting the else block.
                    Assert.assertTrue(blockOpcode == Instructions.IF, "Expected then branch. Else branch requires preceding then branch.", Failure.TYPE_MISMATCH);
                    break;
                }
                case Instructions.BR: {
                    final int branchLabel = readTargetOffset();
                    state.checkLabelExists(branchLabel);
                    final int targetStackSize = state.getValueStackSize(branchLabel);
                    state.useIntConstant(targetStackSize);
                    final byte[] labelTypes = state.getBlock(branchLabel).getLabelTypes();
                    state.useIntConstant(labelTypes.length);
                    state.popAll(labelTypes);

                    // This instruction is stack-polymorphic
                    state.setUnreachable();
                    break;
                }
                case Instructions.BR_IF: {
                    final int branchLabel = readTargetOffset();
                    state.checkLabelExists(branchLabel);
                    final int targetStackSize = state.getValueStackSize(branchLabel);
                    state.useIntConstant(targetStackSize);
                    state.popChecked(I32_TYPE); // condition
                    final byte[] labelTypes = state.getBlock(branchLabel).getLabelTypes();
                    state.useIntConstant(labelTypes.length);
                    state.popAll(labelTypes);
                    state.pushAll(labelTypes);
                    state.incrementProfileCount();
                    break;
                }
                case Instructions.BR_TABLE: {
                    state.popChecked(I32_TYPE); // index
                    final int length = readLength();

                    // We need to save three tables here, to maintain the mapping target -> state
                    // mapping:
                    // - the length of the return type
                    // - a table containing the branch targets for the instruction
                    // - a table containing the stack state for each corresponding branch target
                    // We encode this in a single array

                    final int[] branchTable = new int[2 * (length + 1) + 1];
                    for (int i = 0; i != length + 1; ++i) {
                        final int branchLabel = readTargetOffset();
                        state.checkLabelExists(branchLabel);
                        branchTable[1 + 2 * i + 0] = branchLabel;
                        branchTable[1 + 2 * i + 1] = state.getValueStackSize(branchLabel);
                    }

                    // get last branch label as type checking reference
                    int branchLabel = branchTable[1 + 2 * length];

                    byte[] branchLabelReturnType = state.getBlock(branchLabel).getLabelTypes();
                    for (int i = 0; i != length; ++i) {
                        int otherBranchLabel = branchTable[1 + 2 * i];
                        // Check return type equality
                        byte[] otherBranchLabelReturnTypes = state.getBlock(otherBranchLabel).getLabelTypes();
                        state.checkLabelTypes(branchLabelReturnType, otherBranchLabelReturnTypes);

                        state.pushAll(state.popAll(otherBranchLabelReturnTypes));
                    }
                    state.popAll(branchLabelReturnType);
                    branchTable[0] = branchLabelReturnType.length;
                    state.saveBranchTable(branchTable);

                    // This instruction is stack-polymorphic
                    state.setUnreachable();
                    break;
                }
                case Instructions.RETURN: {
                    byte[] resultTypes = state.getRootBlock().getResultTypes();
                    assertIntLessOrEqual(resultTypes.length, 1, Failure.INVALID_RESULT_ARITY);
                    state.checkReturnTypes(state.getRootBlock());
                    state.useIntConstant(state.controlStackSize());
                    state.useIntConstant(resultTypes.length);

                    // This instruction is stack-polymorphic
                    state.setUnreachable();
                    break;
                }
                case Instructions.CALL: {
                    final int callFunctionIndex = readDeclaredFunctionIndex(module);

                    // Pop arguments
                    final WasmFunctionType functionType = module.getFunctionType(callFunctionIndex);
                    state.checkParameterTypes(functionType.getParameterTypes());

                    // Push return value
                    assertIntLessOrEqual(functionType.getReturnCount(), 1, Failure.INVALID_RESULT_ARITY);
                    if (functionType.getReturnCount() == 1) {
                        state.push(functionType.getReturnType());
                    }
                    children.add(new CallNode(callFunctionIndex));
                    break;
                }
                case Instructions.CALL_INDIRECT: {
                    if (module.getTableCount() == 0) {
                        throw ValidationErrors.createMissingTable();
                    }

                    int expectedFunctionTypeIndex = readUnsignedInt32();

                    // Pop the function index to call
                    state.popChecked(I32_TYPE);
                    state.checkFunctionTypeExists(expectedFunctionTypeIndex, module.getFunctionTypeCount());
                    WasmFunctionType functionType = module.getFunctionTypeAtIndex(expectedFunctionTypeIndex);

                    // Pop arguments
                    state.popAll(functionType.getParameterTypes());

                    // Push return value
                    final int returnLength = functionType.getReturnCount();
                    assertIntLessOrEqual(returnLength, 1, Failure.INVALID_RESULT_ARITY);
                    if (returnLength == 1) {
                        state.push(functionType.getReturnType());
                    }
                    state.incrementProfileCount();
                    children.add(new CallNode());
                    final int tableIndex = read1();
                    assertIntEqual(tableIndex, CallIndirect.ZERO_TABLE, "CALL_INDIRECT: Instruction must end with 0x00", Failure.ZERO_FLAG_EXPECTED);
                    break;
                }
                case Instructions.DROP:
                    state.pop();
                    break;
                case Instructions.SELECT:
                    state.popChecked(I32_TYPE); // condition
                    final byte t = state.pop(); // first operand
                    state.popChecked(t); // second operand
                    state.push(t);
                    break;
                case Instructions.LOCAL_GET: {
                    final int localIndex = readLocalIndex();
                    assertUnsignedIntLess(localIndex, locals.length, Failure.UNKNOWN_LOCAL);
                    state.push(locals[localIndex]);
                    break;
                }
                case Instructions.LOCAL_SET: {
                    final int localIndex = readLocalIndex();
                    assertUnsignedIntLess(localIndex, locals.length, Failure.UNKNOWN_LOCAL);
                    state.popChecked(locals[localIndex]);
                    break;
                }
                case Instructions.LOCAL_TEE: {
                    final int localIndex = readLocalIndex();
                    assertUnsignedIntLess(localIndex, locals.length, Failure.UNKNOWN_LOCAL);
                    state.popChecked(locals[localIndex]);
                    state.push(locals[localIndex]);
                    break;
                }
                case Instructions.GLOBAL_GET: {
                    final int index = readGlobalIndex(module);
                    state.push(module.getGlobalValueType(index));
                    break;
                }
                case Instructions.GLOBAL_SET: {
                    final int index = readGlobalIndex(module);
                    final byte mutability = module.getGlobalMutability(index);
                    final byte valueType = module.getGlobalValueType(index);
                    // Assert that the global is mutable.
                    assertByteEqual(mutability, (byte) GlobalModifier.MUTABLE,
                                    "Immutable globals cannot be set: " + index, Failure.IMMUTABLE_GLOBAL_WRITE);
                    state.popChecked(valueType);
                    break;
                }
                case Instructions.F32_LOAD:
                    load(state, F32_TYPE, 32, module);
                    break;
                case Instructions.F64_LOAD:
                    load(state, F64_TYPE, 64, module);
                    break;
                case Instructions.I32_LOAD:
                    load(state, I32_TYPE, 32, module);
                    break;
                case Instructions.I32_LOAD8_S:
                case Instructions.I32_LOAD8_U:
                    load(state, I32_TYPE, 8, module);
                    break;
                case Instructions.I32_LOAD16_S:
                case Instructions.I32_LOAD16_U:
                    load(state, I32_TYPE, 16, module);
                    break;
                case Instructions.I64_LOAD:
                    load(state, I64_TYPE, 64, module);
                    break;
                case Instructions.I64_LOAD8_S:
                case Instructions.I64_LOAD8_U:
                    load(state, I64_TYPE, 8, module);
                    break;
                case Instructions.I64_LOAD16_S:
                case Instructions.I64_LOAD16_U:
                    load(state, I64_TYPE, 16, module);
                    break;
                case Instructions.I64_LOAD32_S:
                case Instructions.I64_LOAD32_U:
                    load(state, I64_TYPE, 32, module);
                    break;
                case Instructions.F32_STORE:
                    store(state, F32_TYPE, 32, module);
                    break;
                case Instructions.F64_STORE:
                    store(state, F64_TYPE, 64, module);
                    break;
                case Instructions.I32_STORE:
                    store(state, I32_TYPE, 32, module);
                    break;
                case Instructions.I32_STORE_8:
                    store(state, I32_TYPE, 8, module);
                    break;
                case Instructions.I32_STORE_16:
                    store(state, I32_TYPE, 16, module);
                    break;
                case Instructions.I64_STORE:
                    store(state, I64_TYPE, 64, module);
                    break;
                case Instructions.I64_STORE_8:
                    store(state, I64_TYPE, 8, module);
                    break;
                case Instructions.I64_STORE_16:
                    store(state, I64_TYPE, 16, module);
                    break;
                case Instructions.I64_STORE_32:
                    store(state, I64_TYPE, 32, module);
                    break;
                case Instructions.MEMORY_SIZE: {
                    final int flag = read1();
                    assertIntEqual(flag, 0, Failure.ZERO_FLAG_EXPECTED);
                    checkMemoryIndex(0, module);
                    state.push(I32_TYPE);
                    break;
                }
                case Instructions.MEMORY_GROW: {
                    final int flag = read1();
                    assertIntEqual(flag, 0, Failure.ZERO_FLAG_EXPECTED);
                    checkMemoryIndex(0, module);
                    state.popChecked(I32_TYPE);
                    state.push(I32_TYPE);
                    break;
                }
                default:
                    readNumericInstructions(state, opcode);
                    break;

            }
        } while (opcode != Instructions.END && opcode != Instructions.ELSE);

        state.exitBlock();
        final int endOffset = offset;
        final int endIntConstantOffset = state.intConstantSize();
        final int endBranchTableOffset = state.branchTableSize();
        final int endProfileOffset = state.currentProfileCount();
        if (blockOpcode == Instructions.IF) {
            offset--;
        }
        return new BlockNode(returnType, startOffset, initialStackPointer, initialIntConstantOffset, initialBranchTableOffset, initialProfileOffset, endOffset, endIntConstantOffset,
                        endBranchTableOffset, endProfileOffset, children);
    }

    private void readNumericInstructions(ValidationState state, int opcode) {
        switch (opcode) {
            case Instructions.I32_CONST:
                readSignedInt32();
                state.push(I32_TYPE);
                break;
            case Instructions.I64_CONST:
                readSignedInt64();
                state.push(I64_TYPE);
                break;
            case Instructions.F32_CONST:
                read4();
                state.push(F32_TYPE);
                break;
            case Instructions.F64_CONST:
                read8();
                state.push(F64_TYPE);
                break;
            case Instructions.I32_EQZ:
                state.popChecked(I32_TYPE);
                state.push(I32_TYPE);
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
                state.popChecked(I32_TYPE);
                state.popChecked(I32_TYPE);
                state.push(I32_TYPE);
                break;
            case Instructions.I64_EQZ:
                state.popChecked(I64_TYPE);
                state.push(I32_TYPE);
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
                break;
            case Instructions.I32_CLZ:
            case Instructions.I32_CTZ:
            case Instructions.I32_POPCNT:
                state.popChecked(I32_TYPE);
                state.push(I32_TYPE);
                break;
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
                break;
            case Instructions.I64_CLZ:
            case Instructions.I64_CTZ:
            case Instructions.I64_POPCNT:
                state.popChecked(I64_TYPE);
                state.push(I64_TYPE);
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
                break;
            case Instructions.I32_WRAP_I64:
                state.popChecked(I64_TYPE);
                state.push(I32_TYPE);
                break;
            case Instructions.I32_TRUNC_F32_S:
            case Instructions.I32_TRUNC_F32_U:
                state.popChecked(F32_TYPE);
                state.push(I32_TYPE);
                break;
            case Instructions.I32_TRUNC_F64_S:
            case Instructions.I32_TRUNC_F64_U:
                state.popChecked(F64_TYPE);
                state.push(I32_TYPE);
                break;
            case Instructions.I64_EXTEND_I32_S:
            case Instructions.I64_EXTEND_I32_U:
                state.popChecked(I32_TYPE);
                state.push(I64_TYPE);
                break;
            case Instructions.I64_TRUNC_F32_S:
            case Instructions.I64_TRUNC_F32_U:
                state.popChecked(F32_TYPE);
                state.push(I64_TYPE);
                break;
            case Instructions.I64_TRUNC_F64_S:
            case Instructions.I64_TRUNC_F64_U:
                state.popChecked(F64_TYPE);
                state.push(I64_TYPE);
                break;
            case Instructions.F32_CONVERT_I32_S:
            case Instructions.F32_CONVERT_I32_U:
                state.popChecked(I32_TYPE);
                state.push(F32_TYPE);
                break;
            case Instructions.F32_CONVERT_I64_S:
            case Instructions.F32_CONVERT_I64_U:
                state.popChecked(I64_TYPE);
                state.push(F32_TYPE);
                break;
            case Instructions.F32_DEMOTE_F64:
                state.popChecked(F64_TYPE);
                state.push(F32_TYPE);
                break;
            case Instructions.F64_CONVERT_I32_S:
            case Instructions.F64_CONVERT_I32_U:
                state.popChecked(I32_TYPE);
                state.push(F64_TYPE);
                break;
            case Instructions.F64_CONVERT_I64_S:
            case Instructions.F64_CONVERT_I64_U:
                state.popChecked(I64_TYPE);
                state.push(F64_TYPE);
                break;
            case Instructions.F64_PROMOTE_F32:
                state.popChecked(F32_TYPE);
                state.push(F64_TYPE);
                break;
            case Instructions.I32_REINTERPRET_F32:
                state.popChecked(F32_TYPE);
                state.push(I32_TYPE);
                break;
            case Instructions.I64_REINTERPRET_F64:
                state.popChecked(F64_TYPE);
                state.push(I64_TYPE);
                break;
            case Instructions.F32_REINTERPRET_I32:
                state.popChecked(I32_TYPE);
                state.push(F32_TYPE);
                break;
            case Instructions.F64_REINTERPRET_I64:
                state.popChecked(I64_TYPE);
                state.push(F64_TYPE);
                break;
            case Instructions.MISC:
                int miscOpcode = read1() & 0xFF;
                switch (miscOpcode) {
                    case Instructions.I32_TRUNC_SAT_F32_S:
                    case Instructions.I32_TRUNC_SAT_F32_U:
                        checkSaturatingFloatToIntSupport(miscOpcode);
                        state.popChecked(F32_TYPE);
                        state.push(I32_TYPE);
                        break;
                    case Instructions.I32_TRUNC_SAT_F64_S:
                    case Instructions.I32_TRUNC_SAT_F64_U:
                        checkSaturatingFloatToIntSupport(miscOpcode);
                        state.popChecked(F64_TYPE);
                        state.push(I32_TYPE);
                        break;
                    case Instructions.I64_TRUNC_SAT_F32_S:
                    case Instructions.I64_TRUNC_SAT_F32_U:
                        checkSaturatingFloatToIntSupport(miscOpcode);
                        state.popChecked(F32_TYPE);
                        state.push(I64_TYPE);
                        break;
                    case Instructions.I64_TRUNC_SAT_F64_S:
                    case Instructions.I64_TRUNC_SAT_F64_U:
                        checkSaturatingFloatToIntSupport(miscOpcode);
                        state.popChecked(F64_TYPE);
                        state.push(I64_TYPE);
                        break;
                    default:
                        fail(Failure.UNSPECIFIED_MALFORMED, "Unknown opcode: 0xFC 0x%02x", miscOpcode);
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
        checkContextOption(wasmContext.getContextOptions().isSaturatingFloatToInt(), "Saturating float-to-int conversion is not enabled (opcode: 0xFC 0x%02x)", opcode);
    }

    private void store(ValidationState state, byte type, int n, WasmModule module) {
        assertIntEqual(module.getMemoryCount(), 1, Failure.UNKNOWN_MEMORY);

        // We don't store the `align` literal, as our implementation does not make use
        // of it, but we need to store its byte length, so that we can skip it
        // during the execution.
        readAlignHint(n); // align hint
        readUnsignedInt32(); // store offset
        state.popChecked(type); // value to store
        state.popChecked(I32_TYPE); // base address
    }

    private void load(ValidationState state, byte type, int n, WasmModule module) {
        assertIntEqual(module.getMemoryCount(), 1, Failure.UNKNOWN_MEMORY);

        // We don't store the `align` literal, as our implementation does not make use
        // of it, but we need to store its byte length, so that we can skip it
        // during execution.
        readAlignHint(n); // align hint
        readUnsignedInt32(); // load offset
        state.popChecked(I32_TYPE); // base address
        state.push(type); // loaded value
    }

    private void readElementSection(WasmModule module) {
        int numElements = readLength();
        limits.checkElementSegmentCount(numElements);
        WasmElementDefinition[] elementDefinitions = new WasmElementDefinition[numElements];

        for (int elemSegmentId = 0; elemSegmentId != numElements; ++elemSegmentId) {
            // Support for different table indices and "segment flags" might be added in the future:
            // https://github.com/WebAssembly/bulk-memory-operations/blob/master/proposals/bulk-memory-operations/Overview.md#element-segments).
            readTableIndex(module);
            assertIntEqual(module.getTableCount(), 1, Failure.UNKNOWN_TABLE);

            // Table offset expression must be a constant expression with result type i32.
            // https://webassembly.github.io/spec/core/syntax/modules.html#element-segments
            // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions

            // Read the offset expression.
            byte instruction = read1();

            // Read the offset expression.
            int offsetOrIndex;
            boolean initialized;
            switch (instruction) {
                case Instructions.I32_CONST:
                    offsetOrIndex = readSignedInt32();
                    initialized = true;
                    break;
                case Instructions.GLOBAL_GET:
                    offsetOrIndex = readGlobalIndex(module);
                    initialized = false;
                    break;
                default:
                    throw WasmException.format(Failure.TYPE_MISMATCH, "Invalid instruction for table offset expression: 0x%02X", instruction);
            }

            readEnd();

            final int segmentLength = readLength();
            final int[] functionIndices = new int[segmentLength];
            for (int index = 0; index != segmentLength; ++index) {
                functionIndices[index] = readDeclaredFunctionIndex(module);
            }

            elementDefinitions[elemSegmentId] = new WasmElementDefinition(initialized, offsetOrIndex, functionIndices);
        }
        module.setElementDefinitions(elementDefinitions);
    }

    private void readEnd() {
        final byte instruction = read1();
        assertByteEqual(instruction, (byte) Instructions.END, Failure.TYPE_MISMATCH);
    }

    private void readStartSection(WasmModule module) {
        int startFunctionIndex = readDeclaredFunctionIndex(module);
        WasmFunctionType start = module.getFunctionType(startFunctionIndex);
        if (start.getParameterCount() != 0) {
            throw WasmException.create(Failure.START_FUNCTION_ARGUMENTS, "Start function cannot take arguments.");
        }
        if (start.getReturnCount() != 0) {
            throw WasmException.create(Failure.START_FUNCTION_RETURN_VALUE, "Start function cannot return a value.");
        }
        module.setStartFunctionIndex(startFunctionIndex);
    }

    private void readExportSection(WasmModule module) {
        int numExports = readLength();
        limits.checkExportCount(numExports);

        Set<String> names = new HashSet<>();
        String[] exportNames = new String[numExports];
        WasmExternalValue[] exports = new WasmExternalValue[numExports];
        for (int i = 0; i != numExports; ++i) {
            String exportName = readName();
            if (names.contains(exportName)) {
                throw WasmException.create(Failure.DUPLICATE_EXPORT, "All export names must be different, but '" + exportName + "' is exported twice.");
            } else {
                names.add(exportName);
            }
            exportNames[i] = exportName;
            byte exportType = readExportType();
            switch (exportType) {
                case ExportIdentifier.FUNCTION: {
                    int functionIndex = readDeclaredFunctionIndex(module);
                    exports[i] = new WasmExternalValue(i, exportType, functionIndex);
                    module.setFunctionName(functionIndex, exportName);
                    break;
                }
                case ExportIdentifier.TABLE: {
                    int tableIndex = readTableIndex(module);
                    exports[i] = new WasmExternalValue(i, exportType, tableIndex);
                    break;
                }
                case ExportIdentifier.MEMORY: {
                    int memoryIndex = readMemoryIndex(module);
                    exports[i] = new WasmExternalValue(i, exportType, memoryIndex);
                    break;
                }
                case ExportIdentifier.GLOBAL: {
                    int index = readGlobalIndex(module);
                    exports[i] = new WasmExternalValue(i, exportType, index);
                    break;
                }
                default: {
                    fail(Failure.UNSPECIFIED_MALFORMED, String.format("Invalid export type identifier: 0x%02X", exportType));
                }
            }
        }
        module.setExports(exportNames, exports);
    }

    private void readGlobalSection(WasmModule module) {
        final int numGlobals = readLength();
        limits.checkGlobalCount(numGlobals);

        WasmGlobalDefinition[] globals = new WasmGlobalDefinition[numGlobals];
        for (int globalIndex = 0; globalIndex != numGlobals; globalIndex++) {
            final byte type = readValueType();
            // 0x00 means const, 0x01 means var
            final byte mutability = readMutability();
            long valueOrIndex;
            final byte instruction = read1();
            boolean isInitialized;
            // Global initialization expressions must be constant expressions:
            // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions
            switch (instruction) {
                case Instructions.I32_CONST:
                    assertByteEqual(type, I32_TYPE, Failure.TYPE_MISMATCH);
                    valueOrIndex = readSignedInt32();
                    isInitialized = true;
                    break;
                case Instructions.I64_CONST:
                    assertByteEqual(type, I64_TYPE, Failure.TYPE_MISMATCH);
                    valueOrIndex = readSignedInt64();
                    isInitialized = true;
                    break;
                case Instructions.F32_CONST:
                    assertByteEqual(type, F32_TYPE, Failure.TYPE_MISMATCH);
                    valueOrIndex = readFloatAsInt32();
                    isInitialized = true;
                    break;
                case Instructions.F64_CONST:
                    assertByteEqual(type, F64_TYPE, Failure.TYPE_MISMATCH);
                    valueOrIndex = readFloatAsInt64();
                    isInitialized = true;
                    break;
                case Instructions.GLOBAL_GET:
                    int existingIndex = readGlobalIndex(module);
                    assertUnsignedIntLess(existingIndex, module.getGlobalImportCount(), Failure.UNKNOWN_GLOBAL);
                    final byte valueType = module.getGlobalValueType(existingIndex);
                    assertByteEqual(type, valueType, Failure.TYPE_MISMATCH);
                    isInitialized = false;
                    valueOrIndex = existingIndex;
                    break;
                default:
                    throw WasmException.create(Failure.TYPE_MISMATCH);
            }
            readEnd();
            globals[globalIndex] = new WasmGlobalDefinition(mutability, type, isInitialized, valueOrIndex);
        }
        module.setGlobals(globals);
    }

    private void readDataSection(WasmModule module) {
        final int numDataSegments = readLength();
        limits.checkDataSegmentCount(numDataSegments);

        WasmDataDefinition[] dataDefinitions = new WasmDataDefinition[numDataSegments];
        for (int dataSegmentId = 0; dataSegmentId != numDataSegments; ++dataSegmentId) {
            readMemoryIndex(module);
            assertIntEqual(module.getMemoryCount(), 1, Failure.UNKNOWN_MEMORY);

            // Data dataOffset expression must be a constant expression with result type i32.
            // https://webassembly.github.io/spec/core/syntax/modules.html#data-segments
            // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions

            // Read the offset expression.
            byte instruction = read1();

            // Read the offset expression.
            int offsetOrIndex;
            boolean initialized;

            switch (instruction) {
                case Instructions.I32_CONST:
                    offsetOrIndex = readSignedInt32();
                    initialized = true;
                    break;
                case Instructions.GLOBAL_GET:
                    offsetOrIndex = readGlobalIndex(module);
                    initialized = false;
                    break;
                default:
                    throw WasmException.format(Failure.TYPE_MISMATCH, "Invalid instruction for table offset expression: 0x%02X", instruction);
            }

            readEnd();

            final int byteLength = readLength();
            final byte[] dataSegment = new byte[byteLength];
            for (int writeOffset = 0; writeOffset != byteLength; ++writeOffset) {
                byte b = read1();
                dataSegment[writeOffset] = b;
            }
            dataDefinitions[dataSegmentId] = new WasmDataDefinition(initialized, offsetOrIndex, dataSegment);
        }
        module.setDataDefinitions(dataDefinitions);
    }

    private WasmFunctionType readFunctionType() {
        int paramsLength = readLength();
        int resultLength = value(peekUnsignedInt32AndLength(data, offset + paramsLength));
        resultLength = (resultLength == 0x40) ? 0 : resultLength;

        limits.checkParamCount(paramsLength);
        limits.checkReturnCount(resultLength);
        assertIntLessOrEqual(resultLength, 1, "A function can return at most one result", Failure.INVALID_RESULT_ARITY);
        byte[] parameters = readParameterList(paramsLength);
        byte results = readResultList();
        return wasmContext.getFunctionType(parameters, results);
    }

    private byte[] readParameterList(int numParams) {
        byte[] parameters = new byte[numParams];
        for (int paramIdx = 0; paramIdx != numParams; ++paramIdx) {
            byte type = readValueType();
            parameters[paramIdx] = type;
        }
        return parameters;
    }

    // Specification seems ambiguous:
    // https://webassembly.github.io/spec/core/binary/types.html#result-types
    // According to the spec, the result type can only be 0x40 (void) or 0xtt, where tt is a value
    // type.
    // However, the Wasm binary compiler produces binaries with either 0x00 or 0x01 0xtt. Therefore,
    // we support both.
    private byte readResultList() {
        byte b = read1();
        switch (b) {
            case WasmType.VOID_TYPE:  // special byte indicating empty return type (same as above)
            case 0x00:  // empty vector
                break;
            case 0x01:  // vector with one element (produced by the Wasm binary compiler)
                return readValueType();
            default:
                fail(Failure.MALFORMED_VALUE_TYPE, String.format("Invalid return value specifier: 0x%02X", b));
        }
        return WasmType.VOID_TYPE;
    }

    private boolean isEOF() {
        return offset == data.length;
    }

    private int readDeclaredFunctionIndex(WasmModule module) {
        final int index = readUnsignedInt32();
        assertUnsignedIntLess(index, module.getFunctionCount(), Failure.UNKNOWN_FUNCTION);
        return index;
    }

    private int readTypeIndex(WasmModule module) {
        final int result = readUnsignedInt32();
        assertUnsignedIntLess(result, module.getFunctionTypeCount(), Failure.UNKNOWN_TYPE);
        return result;
    }

    private int readFunctionIndex() {
        return readUnsignedInt32();
    }

    private int readTableIndex(WasmModule module) {
        final int index = readUnsignedInt32();
        // At the moment, WebAssembly (1.0, MVP) only supports one table instance, thus the only
        // valid table index is 0.
        assertIntEqual(index, 0, Failure.UNKNOWN_TABLE);
        assertIntEqual(module.getTableCount(), 1, Failure.UNKNOWN_TABLE);
        return index;
    }

    private int readMemoryIndex(WasmModule module) {
        return checkMemoryIndex(readUnsignedInt32(), module);
    }

    private static int checkMemoryIndex(int index, WasmModule module) {
        assertIntEqual(module.getMemoryCount(), 1, Failure.UNKNOWN_MEMORY);
        assertIntEqual(index, 0, Failure.UNKNOWN_MEMORY);
        return index;
    }

    private int readGlobalIndex(WasmModule module) {
        final int index = readUnsignedInt32();
        assertUnsignedIntLess(index, module.getGlobalCount(), Failure.UNKNOWN_GLOBAL);
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

    private byte readElemType() {
        return read1();
    }

    private void readTableLimits(int[] out) {
        readLimits(out, MAX_TABLE_DECLARATION_SIZE);
        assertUnsignedIntLessOrEqual(out[0], out[1], Failure.LIMIT_MINIMUM_GREATER_THAN_MAXIMUM);
    }

    private void readMemoryLimits(int[] out) {
        readLimits(out, MAX_MEMORY_DECLARATION_SIZE);
        assertUnsignedIntLessOrEqual(out[0], MAX_MEMORY_DECLARATION_SIZE, Failure.MEMORY_SIZE_LIMIT_EXCEEDED);
        assertUnsignedIntLessOrEqual(out[1], MAX_MEMORY_DECLARATION_SIZE, Failure.MEMORY_SIZE_LIMIT_EXCEEDED);
        assertUnsignedIntLessOrEqual(out[0], out[1], Failure.LIMIT_MINIMUM_GREATER_THAN_MAXIMUM);
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
                fail(Failure.UNSPECIFIED_MALFORMED, String.format("Invalid limits prefix (expected 0x00 or 0x01, got 0x%02X", limitsPrefix));
        }
    }

    private byte readLimitsPrefix() {
        return read1();
    }

    private String readName() {
        int nameLength = readLength();
        assertUnsignedIntLessOrEqual(offset + nameLength, data.length, Failure.UNEXPECTED_END);

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

    private long readSignedInt64() {
        final long value = peekSignedInt64(data, offset, true);
        final byte length = peekLeb128Length(data, offset);
        offset += length;
        return value;
    }
}
