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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
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
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.nodes.WasmBlockNode;
import org.graalvm.wasm.nodes.WasmCallStubNode;
import org.graalvm.wasm.nodes.WasmIfNode;
import org.graalvm.wasm.nodes.WasmIndirectCallNode;
import org.graalvm.wasm.nodes.WasmNode;
import org.graalvm.wasm.nodes.WasmRootNode;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.graalvm.wasm.WasmUtil.unsignedInt32ToLong;

/**
 * Simple recursive-descend parser for the binary WebAssembly format.
 */
public class BinaryParser extends BinaryStreamParser {
    private class ParsingExceptionHandler implements Thread.UncaughtExceptionHandler {
        private Throwable parsingException = null;

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            this.parsingException = e;
        }

        public Throwable parsingException() {
            return parsingException;
        }
    }

    private static final int MIN_DEFAULT_STACK_SIZE = 1_000_000;
    private static final int MAX_DEFAULT_ASYNC_STACK_SIZE = 10_000_000;

    private static final int MAGIC = 0x6d736100;
    private static final int VERSION = 0x00000001;
    // Java indices cannot be bigger than 2^31 - 1.
    private static final long TABLE_MAX_SIZE = Integer.MAX_VALUE;
    private static final long MEMORY_MAX_PAGES = 1 << 16;

    private final WasmLanguage language;
    private final WasmModule module;
    private final ModuleLimits moduleLimits;
    private final int[] limitsResult;

    public BinaryParser(WasmLanguage language, WasmModule module) {
        this(language, module, null);
    }

    @CompilerDirectives.TruffleBoundary
    public BinaryParser(WasmLanguage language, WasmModule module, ModuleLimits moduleLimits) {
        super(module.data());
        this.language = language;
        this.module = module;
        this.limitsResult = new int[2];
        this.moduleLimits = moduleLimits;
    }

    @CompilerDirectives.TruffleBoundary
    public void readModule() {
        if (moduleLimits != null) {
            moduleLimits.checkModuleSize(data.length);
        }
        validateMagicNumberAndVersion();
        readSymbolSections();
    }

    @CompilerDirectives.TruffleBoundary
    public void readInstance(WasmContext context, WasmInstance instance) {
        int binarySize = instance.module().data().length;
        final int asyncParsingBinarySize = WasmOptions.AsyncParsingBinarySize.getValue(context.environment().getOptions());
        if (binarySize < asyncParsingBinarySize) {
            readInstanceSynchronously(context, instance);
        } else {
            final Runnable parsing = new Runnable() {
                @Override
                public void run() {
                    readInstanceSynchronously(context, instance);
                }
            };
            final String name = "wasm-parsing-thread(" + instance.name() + ")";
            final int requestedSize = WasmOptions.AsyncParsingStackSize.getValue(context.environment().getOptions()) * 1000;
            final int defaultSize = Math.max(MIN_DEFAULT_STACK_SIZE, Math.min(2 * binarySize, MAX_DEFAULT_ASYNC_STACK_SIZE));
            final int stackSize = requestedSize != 0 ? requestedSize : defaultSize;
            final Thread parsingThread = new Thread(null, parsing, name, stackSize);
            final ParsingExceptionHandler handler = new ParsingExceptionHandler();
            parsingThread.setUncaughtExceptionHandler(handler);
            parsingThread.start();
            try {
                parsingThread.join();
                if (handler.parsingException() != null) {
                    throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Asynchronous parsing failed.");
                }
            } catch (InterruptedException e) {
                throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Asynchronous parsing interrupted.");
            }
        }
    }

    private void readInstanceSynchronously(WasmContext context, WasmInstance instance) {
        if (tryJumpToSection(Section.CODE)) {
            readCodeSection(context, instance);
        }
    }

    private void validateMagicNumberAndVersion() {
        Assert.assertIntEqual(read4(), MAGIC, "Invalid MAGIC number", Failure.UNSPECIFIED_MALFORMED);
        Assert.assertIntEqual(read4(), VERSION, "Invalid VERSION number", Failure.UNSPECIFIED_MALFORMED);
    }

    private void readSymbolSections() {
        int lastNonCustomSection = -1;
        while (!isEOF()) {
            byte sectionID = read1();

            if (sectionID != Section.CUSTOM) {
                if (sectionID > lastNonCustomSection) {
                    lastNonCustomSection = sectionID;
                } else if (lastNonCustomSection == sectionID) {
                    Assert.fail("Duplicated section " + sectionID, Failure.UNSPECIFIED_MALFORMED);
                } else {
                    Assert.fail("Section " + sectionID + " defined after section " + lastNonCustomSection, Failure.UNSPECIFIED_MALFORMED);
                }
            }

            int size = readUnsignedInt32();
            int startOffset = offset;
            switch (sectionID) {
                case Section.CUSTOM:
                    readCustomSection(size);
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
                    readElementSection();
                    break;
                case Section.CODE:
                    skipCodeSection();
                    break;
                case Section.DATA:
                    readDataSection(null, null);
                    break;
                default:
                    Assert.fail("invalid section ID: " + sectionID, Failure.UNSPECIFIED_MALFORMED);
            }
            Assert.assertIntEqual(offset - startOffset, size, String.format("Declared section (0x%02X) size is incorrect", sectionID), Failure.UNSPECIFIED_MALFORMED);
        }
    }

    private void readCustomSection(int size) {
        int nextSectionOffset = offset + size;
        String name = readName();
        int dataLength = Math.max(0, nextSectionOffset - offset);
        module.allocateCustomSection(name, offset, dataLength);
        offset += dataLength;
    }

    private void readTypeSection() {
        int numTypes = readVectorLength();
        if (moduleLimits != null) {
            moduleLimits.checkTypeCount(numTypes);
        }
        for (int t = 0; t != numTypes; ++t) {
            byte type = read1();
            switch (type) {
                case 0x60:
                    readFunctionType();
                    break;
                default:
                    Assert.fail("Only function types are supported in the type section", Failure.UNSPECIFIED_MALFORMED);
            }
        }
    }

    private void readImportSection() {
        Assert.assertIntEqual(module.symbolTable().maxGlobalIndex(), -1,
                        "The global index should be -1 when the import section is first read.", Failure.UNSPECIFIED_INVALID);
        int numImports = readVectorLength();
        if (moduleLimits != null) {
            moduleLimits.checkImportCount(numImports);
        }
        for (int i = 0; i != numImports; ++i) {
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
                    byte elemType = readElemType();
                    Assert.assertIntEqual(elemType, ReferenceTypes.FUNCREF, "Invalid element type for table import", Failure.UNSPECIFIED_MALFORMED);
                    readTableLimits(limitsResult);
                    module.symbolTable().importTable(moduleName, memberName, limitsResult[0], limitsResult[1]);
                    break;
                }
                case ImportIdentifier.MEMORY: {
                    readMemoryLimits(limitsResult);
                    module.symbolTable().importMemory(moduleName, memberName, limitsResult[0], limitsResult[1]);
                    break;
                }
                case ImportIdentifier.GLOBAL: {
                    byte type = readValueType();
                    byte mutability = readMutability();
                    int index = module.symbolTable().maxGlobalIndex() + 1;
                    module.symbolTable().importGlobal(moduleName, memberName, index, type, mutability);
                    break;
                }
                default: {
                    Assert.fail(String.format("Invalid import type identifier: 0x%02X", importType), Failure.UNSPECIFIED_MALFORMED);
                }
            }
        }
    }

    private void readFunctionSection() {
        int numFunctions = readVectorLength();
        if (moduleLimits != null) {
            moduleLimits.checkFunctionCount(numFunctions);
        }
        for (int i = 0; i != numFunctions; ++i) {
            int functionTypeIndex = readUnsignedInt32();
            module.symbolTable().declareFunction(functionTypeIndex);
        }
    }

    private void readTableSection() {
        int numTables = readVectorLength();
        Assert.assertIntLessOrEqual(module.symbolTable().tableCount() + numTables, 1, "Can import or declare at most one table per module", Failure.UNSPECIFIED_MALFORMED);
        // Since in the current version of WebAssembly supports at most one table instance per
        // module.
        // this loop should be executed at most once.
        for (byte tableIndex = 0; tableIndex != numTables; ++tableIndex) {
            byte elemType = readElemType();
            Assert.assertIntEqual(elemType, ReferenceTypes.FUNCREF, "Invalid element type for table", Failure.UNSPECIFIED_MALFORMED);
            readTableLimits(limitsResult);
            module.symbolTable().allocateTable(limitsResult[0], limitsResult[1]);
        }
    }

    private void readMemorySection() {
        int numMemories = readVectorLength();
        Assert.assertIntLessOrEqual(module.symbolTable().memoryCount() + numMemories, 1, "Can import or declare at most one memory per module", Failure.UNSPECIFIED_MALFORMED);
        // Since in the current version of WebAssembly supports at most one table instance per
        // module.
        // this loop should be executed at most once.
        for (int i = 0; i != numMemories; ++i) {
            readMemoryLimits(limitsResult);
            module.symbolTable().allocateMemory(limitsResult[0], limitsResult[1]);
        }
    }

    private void skipCodeSection() {
        int numImportedFunctions = module.importedFunctions().size();
        int expectedNumCodeEntries = module.numFunctions() - numImportedFunctions;
        int numCodeEntries = readVectorLength();
        if (expectedNumCodeEntries != numCodeEntries) {
            throw WasmException.format(Failure.UNSPECIFIED_INVALID, null, "Unexpected number of code entries: %d (%d expected).", numCodeEntries, expectedNumCodeEntries);
        }
        for (int entryIndex = 0; entryIndex != numCodeEntries; ++entryIndex) {
            int codeEntrySize = readUnsignedInt32();
            int nextCodeEntryOffset = offset + codeEntrySize;
            if (moduleLimits != null) {
                moduleLimits.checkFunctionSize(codeEntrySize);
                int localCount = readCodeEntryLocals().size() + module.function(numImportedFunctions + entryIndex).numArguments();
                moduleLimits.checkLocalCount(localCount);
            }
            offset = nextCodeEntryOffset;
        }
    }

    private void readCodeSection(WasmContext context, WasmInstance instance) {
        final int functionIndexOffset = instance.module().importedFunctions().size();
        final int numCodeEntries = readVectorLength();
        WasmRootNode[] rootNodes = new WasmRootNode[numCodeEntries];
        for (int entry = 0; entry != numCodeEntries; ++entry) {
            rootNodes[entry] = createCodeEntry(instance, functionIndexOffset + entry);
        }
        for (int entryIndex = 0; entryIndex != numCodeEntries; ++entryIndex) {
            int codeEntrySize = readUnsignedInt32();
            int startOffset = offset;
            readCodeEntry(instance, functionIndexOffset + entryIndex, rootNodes[entryIndex]);
            Assert.assertIntEqual(offset - startOffset, codeEntrySize, String.format("Code entry %d size is incorrect", entryIndex), Failure.UNSPECIFIED_MALFORMED);
            final int currentEntryIndex = entryIndex;
            context.linker().resolveCodeEntry(module, currentEntryIndex);
        }
    }

    private WasmRootNode createCodeEntry(WasmInstance instance, int funcIndex) {
        final WasmFunction function = module.symbolTable().function(funcIndex);
        WasmCodeEntry codeEntry = new WasmCodeEntry(function, data);
        function.setCodeEntry(codeEntry);

        /*
         * Create the root node and create and set the call target for the body. This needs to be
         * done before reading the body block, because we need to be able to create direct call
         * nodes {@see TruffleRuntime#createDirectCallNode} during parsing.
         */
        WasmRootNode rootNode = new WasmRootNode(language, instance, codeEntry);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        instance.setTarget(funcIndex, callTarget);

        return rootNode;
    }

    private void readCodeEntry(WasmInstance instance, int funcIndex, WasmRootNode rootNode) {
        /*
         * Initialise the code entry local variables (which contain the parameters and the locals).
         */
        initCodeEntryLocals(funcIndex);

        /* Read (parse) and abstractly interpret the code entry */
        final WasmFunction function = module.symbolTable().function(funcIndex);
        final byte returnTypeId = function.returnType();
        final int returnTypeLength = function.returnTypeLength();
        ExecutionState state = new ExecutionState();
        WasmBlockNode bodyBlock = readBlockBody(instance, rootNode.codeEntry(), state, returnTypeId, false);
        Assert.assertIntEqual(state.stackSize(), returnTypeLength,
                        "Stack size must match the return type length at the function end", Failure.RETURN_SIZE_MISMATCH);
        rootNode.setBody(bodyBlock);

        /* Push a frame slot to the frame descriptor for every local. */
        rootNode.codeEntry().initLocalSlots(rootNode.getFrameDescriptor());

        /* Initialize the Truffle-related components required for execution. */
        if (state.byteConstants().length > 0) {
            rootNode.codeEntry().setByteConstants(state.byteConstants());
        }
        if (state.intConstants().length > 0) {
            rootNode.codeEntry().setIntConstants(state.intConstants());
        }
        if (state.longConstants().length > 0) {
            rootNode.codeEntry().setLongConstants(state.longConstants());
        }
        if (state.branchTables().length > 0) {
            rootNode.codeEntry().setBranchTables(state.branchTables());
        }
        rootNode.codeEntry().setProfileCount(state.profileCount());
        rootNode.codeEntry().initStack(rootNode.getFrameDescriptor(), state.maxStackSize());
    }

    private ByteArrayList readCodeEntryLocals() {
        int numLocalsGroups = readVectorLength();
        ByteArrayList localTypes = new ByteArrayList();
        for (int localGroup = 0; localGroup < numLocalsGroups; localGroup++) {
            int groupLength = readVectorLength();
            byte t = readValueType();
            for (int i = 0; i != groupLength; ++i) {
                localTypes.add(t);
            }
        }
        return localTypes;
    }

    private void initCodeEntryLocals(int funcIndex) {
        WasmCodeEntry codeEntry = module.symbolTable().function(funcIndex).codeEntry();
        int typeIndex = module.symbolTable().function(funcIndex).typeIndex();
        ByteArrayList argumentTypes = module.symbolTable().functionTypeArgumentTypes(typeIndex);
        ByteArrayList localTypes = readCodeEntryLocals();
        byte[] allLocalTypes = ByteArrayList.concat(argumentTypes, localTypes);
        codeEntry.setLocalTypes(allLocalTypes);
    }

    private WasmBlockNode readBlock(WasmInstance instance, WasmCodeEntry codeEntry, ExecutionState state) {
        byte blockTypeId = readBlockType();
        return readBlockBody(instance, codeEntry, state, blockTypeId, false);
    }

    private LoopNode readLoop(WasmInstance instance, WasmCodeEntry codeEntry, ExecutionState state) {
        byte blockTypeId = readBlockType();
        return readLoop(instance, codeEntry, state, blockTypeId);
    }

    private WasmBlockNode readBlockBody(WasmInstance instance, WasmCodeEntry codeEntry, ExecutionState state, byte returnTypeId, boolean isLoopBody) {
        ArrayList<Node> children = new ArrayList<>();
        int startStackSize = state.stackSize();
        int startOffset = offset();
        int startByteConstantOffset = state.byteConstantOffset();
        int startIntConstantOffset = state.intConstantOffset();
        int startLongConstantOffset = state.longConstantOffset();
        int startBranchTableOffset = state.branchTableOffset();
        int startProfileCount = state.profileCount();
        final WasmBlockNode currentBlock = new WasmBlockNode(instance, codeEntry, startOffset, returnTypeId, startStackSize, startByteConstantOffset, startIntConstantOffset, startLongConstantOffset,
                        startBranchTableOffset, startProfileCount);

        state.startBlock(currentBlock, isLoopBody);

        int opcode;
        do {
            opcode = read1() & 0xFF;
            switch (opcode) {
                case Instructions.UNREACHABLE:
                    state.setReachable(false);
                    break;
                case Instructions.NOP:
                    break;
                case Instructions.BLOCK: {
                    // Store the reachability of the current block, to restore it later.
                    boolean reachable = state.isReachable();
                    WasmBlockNode nestedBlock = readBlock(instance, codeEntry, state);
                    children.add(nestedBlock);
                    state.setReachable(reachable);
                    break;
                }
                case Instructions.LOOP: {
                    // Store the reachability of the current block, to restore it later.
                    boolean reachable = state.isReachable();
                    LoopNode loopBlock = readLoop(instance, codeEntry, state);
                    children.add(loopBlock);
                    state.setReachable(reachable);
                    break;
                }
                case Instructions.IF: {
                    // Pop the condition.
                    state.pop();
                    // Store the reachability of the current block, to restore it later.
                    boolean reachable = state.isReachable();
                    WasmIfNode ifNode = readIf(instance, codeEntry, state);
                    children.add(ifNode);
                    state.setReachable(reachable);
                    break;
                }
                case Instructions.ELSE:
                    // We handle the else instruction in the same way as the end instruction.
                case Instructions.END:
                    // If the end instruction is reachable, then we check that the correct number of
                    // operands are stored on the stack. Otherwise then the stack size must be
                    // adjusted to match the stack size at the continuation point.
                    if (state.isReachable()) {
                        Assert.assertIntEqual(state.stackSize() - startStackSize, currentBlock.returnLength(), "Wrong number of values left on the stack at the end of block",
                                        Failure.RETURN_SIZE_MISMATCH);
                    } else {
                        state.setStackSize(state.getStackSize(0) + currentBlock.returnLength());
                    }
                    break;
                case Instructions.BR: {
                    final int unwindLevel = readTargetOffset(state);
                    final int targetStackSize = state.getStackSize(unwindLevel);
                    state.useIntConstant(targetStackSize);
                    final int continuationReturnLength = state.getContinuationLength(unwindLevel);
                    state.useIntConstant(continuationReturnLength);
                    state.assertStackSizeGreaterOrEqual(continuationReturnLength);
                    // This instruction is stack-polymorphic.
                    state.setReachable(false);
                    break;
                }
                case Instructions.BR_IF: {
                    state.pop(); // condition
                    final int unwindLevel = readTargetOffset(state);
                    final int targetStackSize = state.getStackSize(unwindLevel);
                    state.useIntConstant(targetStackSize);
                    final int continuationReturnLength = state.getContinuationLength(unwindLevel);
                    state.useIntConstant(continuationReturnLength);
                    state.assertStackSizeGreaterOrEqual(continuationReturnLength);
                    state.incrementProfileCount();
                    break;
                }
                case Instructions.BR_TABLE: {
                    state.pop(); // index
                    final int numLabels = readVectorLength();
                    // We need to save three tables here, to maintain the mapping target -> state
                    // mapping:
                    // - the length of the return type
                    // - a table containing the branch targets for the instruction
                    // - a table containing the stack state for each corresponding branch target
                    // We encode this in a single array.
                    final int[] branchTable = new int[2 * (numLabels + 1) + 1];
                    int continuationReturnLength = -1;
                    // The BR_TABLE instruction behaves like a 'switch' statement.
                    // There is one extra label for the 'default' case.
                    for (int i = 0; i != numLabels + 1; ++i) {
                        final int unwindLevel = readTargetOffset();
                        branchTable[1 + 2 * i + 0] = unwindLevel;
                        branchTable[1 + 2 * i + 1] = state.getStackSize(unwindLevel);
                        final int targetContinuationLength = state.getContinuationLength(unwindLevel);
                        if (continuationReturnLength == -1) {
                            continuationReturnLength = targetContinuationLength;
                            state.assertStackSizeGreaterOrEqual(continuationReturnLength);
                        } else {
                            Assert.assertIntEqual(continuationReturnLength, targetContinuationLength,
                                            "All target blocks in br.table must have the same return type length.", Failure.TABLE_TARGET_MISMATCH);
                        }
                    }
                    branchTable[0] = continuationReturnLength;
                    // The offset to the branch table.
                    state.saveBranchTable(branchTable);
                    // This instruction is stack-polymorphic.
                    state.setReachable(false);
                    break;
                }
                case Instructions.RETURN: {
                    // Pop the stack values used as the return values.
                    for (int i = 0; i < codeEntry.function().returnTypeLength(); i++) {
                        state.pop();
                    }
                    state.useIntConstant(state.depth());
                    state.useIntConstant(state.getRootBlockReturnLength());
                    // This instruction is stack-polymorphic.
                    state.setReachable(false);
                    break;
                }
                case Instructions.CALL: {
                    int functionIndex = readFunctionIndex(state);
                    WasmFunction function = module.symbolTable().function(functionIndex);
                    state.pop(function.numArguments());
                    state.push(function.returnTypeLength());

                    // We deliberately do not create the call node during parsing,
                    // because the call target is only created after the code entry is parsed.
                    // The code entry might not be yet parsed when we encounter this call.
                    //
                    // Furthermore, if the call target is imported from another module,
                    // then that other module might not have been parsed yet.
                    // Therefore, the call node will be created lazily during linking,
                    // after the call target from the other module exists.
                    children.add(new WasmCallStubNode(function));
                    final int stubIndex = children.size() - 1;
                    module.addLinkAction((context, inst) -> context.linker().resolveCallsite(inst, currentBlock, stubIndex, function));

                    break;
                }
                case Instructions.CALL_INDIRECT: {
                    int expectedFunctionTypeIndex = readTypeIndex(state);
                    int numArguments = module.symbolTable().functionTypeArgumentCount(expectedFunctionTypeIndex);
                    int returnLength = module.symbolTable().functionTypeReturnTypeLength(expectedFunctionTypeIndex);

                    // Pop the function index to call, then pop the arguments and push the return
                    // value.
                    state.pop();
                    state.pop(numArguments);
                    state.push(returnLength);
                    children.add(WasmIndirectCallNode.create());
                    Assert.assertIntEqual(read1(), CallIndirect.ZERO_TABLE, "CALL_INDIRECT: Instruction must end with 0x00", Failure.UNSPECIFIED_MALFORMED);
                    break;
                }
                case Instructions.DROP:
                    state.pop();
                    break;
                case Instructions.SELECT:
                    // Pop three values from the stack: the condition and the values to select
                    // between.
                    state.pop(3);
                    state.push();
                    break;
                case Instructions.LOCAL_GET: {
                    int localIndex = readLocalIndex(state);
                    // Assert localIndex exists.
                    Assert.assertIntLessOrEqual(localIndex, codeEntry.numLocals(), "Invalid local index for local.get", Failure.UNSPECIFIED_MALFORMED);
                    state.push();
                    break;
                }
                case Instructions.LOCAL_SET: {
                    int localIndex = readLocalIndex(state);
                    // Assert localIndex exists.
                    Assert.assertIntLessOrEqual(localIndex, codeEntry.numLocals(), "Invalid local index for local.set", Failure.UNSPECIFIED_MALFORMED);
                    state.pop();
                    break;
                }
                case Instructions.LOCAL_TEE: {
                    int localIndex = readLocalIndex(state);
                    // Assert localIndex exists.
                    Assert.assertIntLessOrEqual(localIndex, codeEntry.numLocals(), "Invalid local index for local.tee", Failure.UNSPECIFIED_MALFORMED);
                    state.pop();
                    state.push();
                    break;
                }
                case Instructions.GLOBAL_GET: {
                    int index = readGlobalIndex(state);
                    Assert.assertIntLessOrEqual(index, module.symbolTable().maxGlobalIndex(),
                                    "Invalid global index for global.get.", Failure.UNSPECIFIED_MALFORMED);
                    state.push();
                    break;
                }
                case Instructions.GLOBAL_SET: {
                    int index = readGlobalIndex(state);
                    // Assert localIndex exists.
                    Assert.assertIntLessOrEqual(index, module.symbolTable().maxGlobalIndex(),
                                    "Invalid global index for global.set.", Failure.UNSPECIFIED_MALFORMED);
                    // Assert that the global is mutable.
                    Assert.assertTrue(module.symbolTable().globalMutability(index) == GlobalModifier.MUTABLE,
                                    "Immutable globals cannot be set: " + index, Failure.UNSPECIFIED_MALFORMED);
                    state.pop();
                    break;
                }
                case Instructions.I32_LOAD:
                case Instructions.I64_LOAD:
                case Instructions.F32_LOAD:
                case Instructions.F64_LOAD:
                case Instructions.I32_LOAD8_S:
                case Instructions.I32_LOAD8_U:
                case Instructions.I32_LOAD16_S:
                case Instructions.I32_LOAD16_U:
                case Instructions.I64_LOAD8_S:
                case Instructions.I64_LOAD8_U:
                case Instructions.I64_LOAD16_S:
                case Instructions.I64_LOAD16_U:
                case Instructions.I64_LOAD32_S:
                case Instructions.I64_LOAD32_U: {
                    // We don't store the `align` literal, as our implementation does not make use
                    // of it, but we need to store its byte length, so that we can skip it
                    // during execution.
                    if (mustPoolLeb128()) {
                        state.useByteConstant(peekLeb128Length(data, offset));
                    }
                    readUnsignedInt32(); // align
                    readUnsignedInt32(state); // load offset
                    state.pop();   // Base address.
                    state.push();  // Loaded value.
                    break;
                }
                case Instructions.I32_STORE:
                case Instructions.I64_STORE:
                case Instructions.F32_STORE:
                case Instructions.F64_STORE:
                case Instructions.I32_STORE_8:
                case Instructions.I32_STORE_16:
                case Instructions.I64_STORE_8:
                case Instructions.I64_STORE_16:
                case Instructions.I64_STORE_32: {
                    // We don't store the `align` literal, as our implementation does not make use
                    // of it, but we need to store its byte length, so that we can skip it
                    // during the execution.
                    if (mustPoolLeb128()) {
                        state.useByteConstant(peekLeb128Length(data, offset));
                    }
                    readUnsignedInt32(); // align
                    readUnsignedInt32(state); // store offset
                    state.pop();  // Value to store.
                    state.pop();  // Base address.
                    break;
                }
                case Instructions.MEMORY_SIZE: {
                    // Skip the constant 0x00.
                    read1();
                    state.push();
                    break;
                }
                case Instructions.MEMORY_GROW: {
                    // Skip the constant 0x00.
                    read1();
                    state.pop();
                    state.push();
                    break;
                }
                case Instructions.I32_CONST: {
                    readSignedInt32(state);
                    state.push();
                    break;
                }
                case Instructions.I64_CONST: {
                    readSignedInt64(state);
                    state.push();
                    break;
                }
                case Instructions.F32_CONST: {
                    read4();
                    state.push();
                    break;
                }
                case Instructions.F64_CONST: {
                    read8();
                    state.push();
                    break;
                }
                case Instructions.I32_EQZ:
                    state.pop();
                    state.push();
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
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case Instructions.I64_EQZ:
                    state.pop();
                    state.push();
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
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case Instructions.F32_EQ:
                case Instructions.F32_NE:
                case Instructions.F32_LT:
                case Instructions.F32_GT:
                case Instructions.F32_LE:
                case Instructions.F32_GE:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case Instructions.F64_EQ:
                case Instructions.F64_NE:
                case Instructions.F64_LT:
                case Instructions.F64_GT:
                case Instructions.F64_LE:
                case Instructions.F64_GE:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case Instructions.I32_CLZ:
                case Instructions.I32_CTZ:
                case Instructions.I32_POPCNT:
                    state.pop();
                    state.push();
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
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case Instructions.I64_CLZ:
                case Instructions.I64_CTZ:
                case Instructions.I64_POPCNT:
                    state.pop();
                    state.push();
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
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case Instructions.F32_ABS:
                case Instructions.F32_NEG:
                case Instructions.F32_CEIL:
                case Instructions.F32_FLOOR:
                case Instructions.F32_TRUNC:
                case Instructions.F32_NEAREST:
                case Instructions.F32_SQRT:
                    state.pop();
                    state.push();
                    break;
                case Instructions.F32_ADD:
                case Instructions.F32_SUB:
                case Instructions.F32_MUL:
                case Instructions.F32_DIV:
                case Instructions.F32_MIN:
                case Instructions.F32_MAX:
                case Instructions.F32_COPYSIGN:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case Instructions.F64_ABS:
                case Instructions.F64_NEG:
                case Instructions.F64_CEIL:
                case Instructions.F64_FLOOR:
                case Instructions.F64_TRUNC:
                case Instructions.F64_NEAREST:
                case Instructions.F64_SQRT:
                    state.pop();
                    state.push();
                    break;
                case Instructions.F64_ADD:
                case Instructions.F64_SUB:
                case Instructions.F64_MUL:
                case Instructions.F64_DIV:
                case Instructions.F64_MIN:
                case Instructions.F64_MAX:
                case Instructions.F64_COPYSIGN:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case Instructions.I32_WRAP_I64:
                case Instructions.I32_TRUNC_F32_S:
                case Instructions.I32_TRUNC_F32_U:
                case Instructions.I32_TRUNC_F64_S:
                case Instructions.I32_TRUNC_F64_U:
                case Instructions.I64_EXTEND_I32_S:
                case Instructions.I64_EXTEND_I32_U:
                case Instructions.I64_TRUNC_F32_S:
                case Instructions.I64_TRUNC_F32_U:
                case Instructions.I64_TRUNC_F64_S:
                case Instructions.I64_TRUNC_F64_U:
                case Instructions.F32_CONVERT_I32_S:
                case Instructions.F32_CONVERT_I32_U:
                case Instructions.F32_CONVERT_I64_S:
                case Instructions.F32_CONVERT_I64_U:
                case Instructions.F32_DEMOTE_F64:
                case Instructions.F64_CONVERT_I32_S:
                case Instructions.F64_CONVERT_I32_U:
                case Instructions.F64_CONVERT_I64_S:
                case Instructions.F64_CONVERT_I64_U:
                case Instructions.F64_PROMOTE_F32:
                case Instructions.I32_REINTERPRET_F32:
                case Instructions.I64_REINTERPRET_F64:
                case Instructions.F32_REINTERPRET_I32:
                case Instructions.F64_REINTERPRET_I64:
                    state.pop();
                    state.push();
                    break;
                default:
                    Assert.fail(Assert.format("Unknown opcode: 0x%02x", opcode), Failure.UNSPECIFIED_MALFORMED);
                    break;
            }
        } while (opcode != Instructions.END && opcode != Instructions.ELSE);
        currentBlock.initialize(toArray(children),
                        offset() - startOffset, state.byteConstantOffset() - startByteConstantOffset,
                        state.intConstantOffset() - startIntConstantOffset, state.longConstantOffset() - startLongConstantOffset,
                        state.branchTableOffset() - startBranchTableOffset, state.profileCount() - startProfileCount);

        state.endBlock();

        return currentBlock;
    }

    static Node[] toArray(ArrayList<Node> list) {
        if (list.size() == 0) {
            return null;
        }
        return list.toArray(new Node[list.size()]);
    }

    private LoopNode readLoop(WasmInstance instance, WasmCodeEntry codeEntry, ExecutionState state, byte returnTypeId) {
        final int initialStackPointer = state.stackSize();

        WasmBlockNode loopBlock = readBlockBody(instance, codeEntry, state, returnTypeId, true);

        // TODO: Hack to correctly set the stack pointer for abstract interpretation.
        // If a block has branch instructions that target "shallower" blocks which return no value,
        // then it can leave no values in the stack, which is invalid for our abstract
        // interpretation.
        // Correct the stack pointer to the value it would have in case there were no branch
        // instructions.
        state.setStackSize(returnTypeId != WasmType.VOID_TYPE ? initialStackPointer + 1 : initialStackPointer);

        return Truffle.getRuntime().createLoopNode(loopBlock);
    }

    private WasmIfNode readIf(WasmInstance instance, WasmCodeEntry codeEntry, ExecutionState state) {
        byte blockTypeId = readBlockType();
        // Note: the condition value was already popped at this point.
        int stackSizeAfterCondition = state.stackSize();

        // Read true branch.
        int startOffset = offset();
        WasmBlockNode trueBranchBlock = readBlockBody(instance, codeEntry, state, blockTypeId, false);

        // If a block has branch instructions that target "shallower" blocks which return no value,
        // then it can leave no values in the stack, which is invalid for our abstract
        // interpretation.
        // Correct the stack pointer to the value it would have in case there were no branch
        // instructions.
        state.setStackSize(stackSizeAfterCondition);

        // Read false branch, if it exists.
        WasmNode falseBranchBlock = null;
        if (peek1(-1) == Instructions.ELSE) {
            falseBranchBlock = readBlockBody(instance, codeEntry, state, blockTypeId, false);
        } else if (blockTypeId != WasmType.VOID_TYPE) {
            Assert.fail("An if statement without an else branch block cannot return values.", Failure.UNSPECIFIED_MALFORMED);
        }
        int stackSizeBeforeCondition = stackSizeAfterCondition + 1;
        return new WasmIfNode(instance, codeEntry, trueBranchBlock, falseBranchBlock, offset() - startOffset, blockTypeId, stackSizeBeforeCondition);
    }

    private void readElementSection() {
        int numElements = readVectorLength();
        if (moduleLimits != null) {
            moduleLimits.checkElementSegmentCount(numElements);
        }
        for (int elemSegmentId = 0; elemSegmentId != numElements; ++elemSegmentId) {
            int tableIndex = readUnsignedInt32();
            // At the moment, WebAssembly (1.0, MVP) only supports one table instance, thus the only
            // valid table index is 0.
            // Support for different table indices and "segment flags" might be added in the future
            // (see
            // https://github.com/WebAssembly/bulk-memory-operations/blob/master/proposals/bulk-memory-operations/Overview.md#element-segments).
            Assert.assertIntEqual(tableIndex, 0, "Invalid table index", Failure.UNSPECIFIED_MALFORMED);

            // Table offset expression must be a constant expression with result type i32.
            // https://webassembly.github.io/spec/core/syntax/modules.html#element-segments
            // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions

            // Read the offset expression.
            byte instruction = read1();
            int offsetAddress = -1;
            int offsetGlobalIndex = -1;
            switch (instruction) {
                case Instructions.I32_CONST: {
                    offsetAddress = readSignedInt32();
                    readEnd();
                    break;
                }
                case Instructions.GLOBAL_GET: {
                    offsetGlobalIndex = readGlobalIndex();
                    readEnd();
                    break;
                }
                default: {
                    Assert.fail(String.format("Invalid instruction for table offset expression: 0x%02X", instruction), Failure.UNSPECIFIED_MALFORMED);
                }
            }

            // Copy the contents, or schedule a linker task for this.
            int segmentLength = readVectorLength();
            final SymbolTable symbolTable = module.symbolTable();
            final int currentElemSegmentId = elemSegmentId;
            final int currentOffsetAddress = offsetAddress;
            final int currentOffsetGlobalIndex = offsetGlobalIndex;
            final int[] functionIndices = new int[segmentLength];
            for (int index = 0; index != segmentLength; ++index) {
                final int functionIndex = readDeclaredFunctionIndex();
                functionIndices[index] = functionIndex;
            }
            module.addLinkAction((context, instance) -> {
                // Note: we do not check if the earlier element segments were executed,
                // and we do not try to execute the element segments in order,
                // as we do with data sections and the memory.
                // Instead, if any table element is written more than once, we report an error.
                // Thus, the order in which the element sections are loaded is not important
                // (also, I did not notice the toolchains overriding the same element slots,
                // or anything in the spec about that).
                WasmFunction[] elements = new WasmFunction[segmentLength];
                for (int index = 0; index != segmentLength; ++index) {
                    final int functionIndex = functionIndices[index];
                    final WasmFunction function = symbolTable.function(functionIndex);
                    elements[index] = function;
                }
                context.linker().resolveElemSegment(context, instance, currentElemSegmentId, currentOffsetAddress, currentOffsetGlobalIndex, segmentLength, elements);
            });
        }
    }

    private void readEnd() {
        byte instruction = read1();
        Assert.assertByteEqual(instruction, (byte) Instructions.END, "Initialization expression must end with an END", Failure.UNSPECIFIED_MALFORMED);
    }

    private void readStartSection() {
        int startFunctionIndex = readDeclaredFunctionIndex();
        module.symbolTable().setStartFunction(startFunctionIndex);
    }

    private void readExportSection() {
        int numExports = readVectorLength();
        if (moduleLimits != null) {
            moduleLimits.checkExportCount(numExports);
        }
        for (int i = 0; i != numExports; ++i) {
            String exportName = readName();
            byte exportType = readExportType();
            switch (exportType) {
                case ExportIdentifier.FUNCTION: {
                    int functionIndex = readDeclaredFunctionIndex();
                    module.symbolTable().exportFunction(functionIndex, exportName);
                    break;
                }
                case ExportIdentifier.TABLE: {
                    int tableIndex = readTableIndex();
                    Assert.assertTrue(module.symbolTable().tableExists(), "No table was imported or declared, so cannot export a table", Failure.UNSPECIFIED_MALFORMED);
                    Assert.assertIntEqual(tableIndex, 0, "Cannot export table index different than zero (only one table per module allowed)", Failure.UNSPECIFIED_MALFORMED);
                    module.symbolTable().exportTable(exportName);
                    break;
                }
                case ExportIdentifier.MEMORY: {
                    readMemoryIndex();
                    module.symbolTable().exportMemory(exportName);
                    break;
                }
                case ExportIdentifier.GLOBAL: {
                    int index = readGlobalIndex();
                    module.symbolTable().exportGlobal(exportName, index);
                    break;
                }
                default: {
                    Assert.fail(String.format("Invalid export type identifier: 0x%02X", exportType), Failure.UNSPECIFIED_MALFORMED);
                }
            }
        }
    }

    private void readGlobalSection() {
        int numGlobals = readVectorLength();
        if (moduleLimits != null) {
            moduleLimits.checkGlobalCount(numGlobals);
        }
        int startingGlobalIndex = module.symbolTable().maxGlobalIndex() + 1;
        for (int globalIndex = startingGlobalIndex; globalIndex != startingGlobalIndex + numGlobals; globalIndex++) {
            byte type = readValueType();
            // 0x00 means const, 0x01 means var
            byte mutability = readMutability();
            long value = 0;
            int existingIndex = -1;
            byte instruction = read1();
            final boolean isInitialized;
            // Global initialization expressions must be constant expressions:
            // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions
            switch (instruction) {
                case Instructions.I32_CONST:
                    value = readSignedInt32();
                    isInitialized = true;
                    break;
                case Instructions.I64_CONST:
                    value = readSignedInt64();
                    isInitialized = true;
                    break;
                case Instructions.F32_CONST:
                    value = readFloatAsInt32();
                    isInitialized = true;
                    break;
                case Instructions.F64_CONST:
                    value = readFloatAsInt64();
                    isInitialized = true;
                    break;
                case Instructions.GLOBAL_GET:
                    existingIndex = readGlobalIndex();
                    isInitialized = false;
                    break;
                default:
                    throw Assert.fail(String.format("Invalid instruction for global initialization: 0x%02X", instruction), Failure.UNSPECIFIED_MALFORMED);
            }
            instruction = read1();
            Assert.assertByteEqual(instruction, (byte) Instructions.END, "Global initialization must end with END", Failure.UNSPECIFIED_MALFORMED);
            module.symbolTable().declareGlobal(globalIndex, type, mutability);
            final int currentGlobalIndex = globalIndex;
            final int currentExistingIndex = existingIndex;
            final long currentValue = value;
            module.addLinkAction((context, instance) -> {
                final GlobalRegistry globals = context.globals();
                final int address = instance.globalAddress(currentGlobalIndex);
                if (isInitialized) {
                    globals.storeLong(address, currentValue);
                    context.linker().resolveGlobalInitialization(instance, currentGlobalIndex);
                } else {
                    if (!module.symbolTable().importedGlobals().containsKey(currentExistingIndex)) {
                        // The current WebAssembly spec says constant expressions can only refer to
                        // imported globals. We can easily remove this restriction in the future.
                        Assert.fail("The initializer for global " + currentGlobalIndex + " in module '" + module.name() +
                                        "' refers to a non-imported global.", Failure.UNSPECIFIED_MALFORMED);
                    }
                    context.linker().resolveGlobalInitialization(context, instance, currentGlobalIndex, currentExistingIndex);
                }
            });
        }
    }

    private void readDataSection(WasmContext linkedContext, WasmInstance linkedInstance) {
        int numDataSegments = readVectorLength();
        if (moduleLimits != null) {
            moduleLimits.checkDataSegmentCount(numDataSegments);
        }
        for (int dataSegmentId = 0; dataSegmentId != numDataSegments; ++dataSegmentId) {
            int memIndex = readUnsignedInt32();
            // At the moment, WebAssembly only supports one memory instance, thus the only valid
            // memory index is 0.
            Assert.assertIntEqual(memIndex, 0, "Invalid memory index, only the memory index 0 is currently supported.", Failure.UNSPECIFIED_MALFORMED);
            byte instruction = read1();

            // Data dataOffset expression must be a constant expression with result type i32.
            // https://webassembly.github.io/spec/core/syntax/modules.html#data-segments
            // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions

            // Read the offset expression.
            int offsetAddress = -1;
            int offsetGlobalIndex = -1;
            switch (instruction) {
                case Instructions.I32_CONST:
                    offsetAddress = readSignedInt32();
                    readEnd();
                    break;
                case Instructions.GLOBAL_GET:
                    offsetGlobalIndex = readGlobalIndex();
                    readEnd();
                    break;
                default:
                    Assert.fail(String.format("Invalid instruction for data offset expression: 0x%02X", instruction), Failure.UNSPECIFIED_MALFORMED);
            }

            int byteLength = readVectorLength();

            if (linkedInstance != null) {
                if (offsetGlobalIndex != -1) {
                    int offsetGlobalAddress = linkedInstance.globalAddress(offsetGlobalIndex);
                    offsetAddress = linkedContext.globals().loadAsInt(offsetGlobalAddress);
                }

                // Reading of the data segment is called after linking, so initialize the memory
                // directly.
                final WasmMemory memory = linkedInstance.memory();
                for (int writeOffset = 0; writeOffset != byteLength; ++writeOffset) {
                    byte b = read1();
                    memory.store_i32_8(null, offsetAddress + writeOffset, b);
                }
            } else {
                // Reading of the data segment occurs during parsing, so add a linker action.
                byte[] dataSegment = new byte[byteLength];
                for (int writeOffset = 0; writeOffset != byteLength; ++writeOffset) {
                    byte b = read1();
                    dataSegment[writeOffset] = b;
                }
                final int currentDataSegmentId = dataSegmentId;
                final int currentOffsetAddress = offsetAddress;
                final int currentOffsetGlobalIndex = offsetGlobalIndex;
                module.addLinkAction((context, instance) -> context.linker().resolveDataSegment(context, instance, currentDataSegmentId, currentOffsetAddress, currentOffsetGlobalIndex, byteLength,
                                dataSegment));
            }
        }
    }

    private void readFunctionType() {
        int paramsLength = readVectorLength();
        int resultLength = peekUnsignedInt32(paramsLength);
        resultLength = (resultLength == 0x40) ? 0 : resultLength;
        if (moduleLimits != null) {
            moduleLimits.checkParamCount(paramsLength);
            moduleLimits.checkReturnCount(resultLength);
        }
        int idx = module.symbolTable().allocateFunctionType(paramsLength, resultLength);
        readParameterList(idx, paramsLength);
        readResultList(idx);
    }

    private void readParameterList(int funcTypeIdx, int numParams) {
        for (int paramIdx = 0; paramIdx != numParams; ++paramIdx) {
            byte type = readValueType();
            module.symbolTable().registerFunctionTypeParameterType(funcTypeIdx, paramIdx, type);
        }
    }

    // Specification seems ambiguous:
    // https://webassembly.github.io/spec/core/binary/types.html#result-types
    // According to the spec, the result type can only be 0x40 (void) or 0xtt, where tt is a value
    // type.
    // However, the Wasm binary compiler produces binaries with either 0x00 or 0x01 0xtt. Therefore,
    // we support both.
    private void readResultList(int funcTypeIdx) {
        byte b = read1();
        switch (b) {
            case WasmType.VOID_TYPE:  // special byte indicating empty return type (same as above)
                break;
            case 0x00:  // empty vector
                break;
            case 0x01:  // vector with one element (produced by the Wasm binary compiler)
                byte type = readValueType();
                module.symbolTable().registerFunctionTypeReturnType(funcTypeIdx, 0, type);
                break;
            default:
                Assert.fail(String.format("Invalid return value specifier: 0x%02X", b), Failure.UNSPECIFIED_MALFORMED);
        }
    }

    private boolean isEOF() {
        return offset == data.length;
    }

    private int readVectorLength() {
        return readUnsignedInt32();
    }

    private int readDeclaredFunctionIndex() {
        final int index = readUnsignedInt32();
        module.symbolTable().checkFunctionIndex(index);
        return index;
    }

    private int readTypeIndex() {
        return readUnsignedInt32();
    }

    private int readTypeIndex(ExecutionState state) {
        return readUnsignedInt32(state);
    }

    private int readFunctionIndex(ExecutionState state) {
        return readUnsignedInt32(state);
    }

    private int readTableIndex() {
        return readUnsignedInt32();
    }

    private int readMemoryIndex() {
        return readUnsignedInt32();
    }

    private int readGlobalIndex() {
        return readUnsignedInt32();
    }

    private int readGlobalIndex(ExecutionState state) {
        return readUnsignedInt32(state);
    }

    private int readLocalIndex(ExecutionState state) {
        return readUnsignedInt32(state);
    }

    private int readTargetOffset() {
        return readUnsignedInt32(null);
    }

    private int readTargetOffset(ExecutionState state) {
        return readUnsignedInt32(state);
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
        long upperBound = (moduleLimits == null) ? TABLE_MAX_SIZE : moduleLimits.getTableSizeLimit();
        readLimits(upperBound, "initial table size", "max table size", out);
    }

    private void readMemoryLimits(int[] out) {
        long upperBound = (moduleLimits == null) ? MEMORY_MAX_PAGES : moduleLimits.getMemorySizeLimit();
        readLimits(upperBound, "initial memory size", "max memory size", out);
    }

    private void readLimits(long upperBound, String minName, String maxName, int[] out) {
        byte limitsPrefix = readLimitsPrefix();
        switch (limitsPrefix) {
            case LimitsPrefix.NO_MAX: {
                out[0] = readUnsignedInt32();
                out[1] = -1;
                break;
            }
            case LimitsPrefix.WITH_MAX: {
                out[0] = readUnsignedInt32();
                out[1] = readUnsignedInt32();
                break;
            }
            default:
                Assert.fail(String.format("Invalid limits prefix (expected 0x00 or 0x01, got 0x%02X", limitsPrefix), Failure.UNSPECIFIED_MALFORMED);
        }

        // Convert min and max to longs to avoid checking bounds on overflowed values.
        long longMin = unsignedInt32ToLong(out[0]);
        long longMax = unsignedInt32ToLong(out[1]);
        Assert.assertLongLessOrEqual(longMin, upperBound, "Invalid " + minName + ", must be less than upper bound", Failure.UNSPECIFIED_MALFORMED);
        if (out[1] != -1) {
            Assert.assertLongLessOrEqual(longMax, upperBound, "Invalid " + maxName + ", must be less than upper bound", Failure.UNSPECIFIED_MALFORMED);
            Assert.assertLongLessOrEqual(longMin, longMax, "Invalid " + minName + ", must be less than " + maxName, Failure.UNSPECIFIED_MALFORMED);
        }
    }

    private byte readLimitsPrefix() {
        return read1();
    }

    private String readName() {
        int nameLength = readVectorLength();
        int afterNameOffset = nameLength + offset;
        if (afterNameOffset < 0 || data.length < afterNameOffset) {
            throw WasmException.format(Failure.UNSPECIFIED_MALFORMED, "The binary is truncated at: %d", data.length);
        }

        // Decode and verify UTF-8 encoding of the name
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer result;
        try {
            result = decoder.decode(ByteBuffer.wrap(data, offset, nameLength));
        } catch (CharacterCodingException ex) {
            throw WasmException.format(Failure.UNSPECIFIED_MALFORMED, "Invalid UTF-8 encoding of the name at: %d", offset);
        }
        offset += nameLength;
        return result.toString();
    }

    protected int readUnsignedInt32() {
        return readUnsignedInt32(null);
    }

    protected int readSignedInt32() {
        return readSignedInt32(null);
    }

    protected long readSignedInt64() {
        return readSignedInt64(null);
    }

    protected int readUnsignedInt32(ExecutionState state) {
        int value = peekUnsignedInt32(data, offset);
        byte length = peekLeb128Length(data, offset);
        if (state != null && mustPoolLeb128()) {
            state.useIntConstant(value);
            state.useByteConstant(length);
        }
        offset += length;
        return value;
    }

    protected int readSignedInt32(ExecutionState state) {
        int value = peekSignedInt32(data, offset);
        byte length = peekLeb128Length(data, offset);
        if (state != null && mustPoolLeb128()) {
            state.useIntConstant(value);
            state.useByteConstant(length);
        }
        offset += length;
        return value;
    }

    private long readSignedInt64(ExecutionState state) {
        long value = peekSignedInt64(data, offset);
        byte length = peekLeb128Length(data, offset);
        if (state != null && mustPoolLeb128()) {
            state.useLongConstant(value);
            state.useByteConstant(length);
        }
        offset += length;
        return value;
    }

    private boolean mustPoolLeb128() {
        return mustPoolLeb128(data, offset, module.storeConstantsPolicy());
    }

    private boolean tryJumpToSection(int targetSectionId) {
        offset = 0;
        validateMagicNumberAndVersion();
        while (!isEOF()) {
            byte sectionID = read1();
            int size = readUnsignedInt32();
            if (sectionID == targetSectionId) {
                return true;
            }
            offset += size;
        }
        return false;
    }

    /**
     * Reset the state of the globals in a module that had already been parsed and linked.
     */
    @SuppressWarnings("unused")
    public void resetGlobalState(WasmContext context, WasmInstance instance) {
        int globalIndex = 0;
        if (tryJumpToSection(Section.IMPORT)) {
            int numImports = readVectorLength();
            for (int i = 0; i != numImports; ++i) {
                String moduleName = readName();
                String memberName = readName();
                byte importType = readImportType();
                switch (importType) {
                    case ImportIdentifier.FUNCTION: {
                        readTableIndex();
                        break;
                    }
                    case ImportIdentifier.TABLE: {
                        readElemType();
                        readTableLimits(limitsResult);
                        break;
                    }
                    case ImportIdentifier.MEMORY: {
                        readMemoryLimits(limitsResult);
                        break;
                    }
                    case ImportIdentifier.GLOBAL: {
                        readValueType();
                        byte mutability = readMutability();
                        if (mutability == GlobalModifier.MUTABLE) {
                            throw WasmException.create(Failure.UNSPECIFIED_UNLINKABLE, "Cannot reset imports of mutable global variables (not implemented).");
                        }
                        globalIndex++;
                        break;
                    }
                    default: {
                        // The module should have been parsed already.
                    }
                }
            }
        }
        if (tryJumpToSection(Section.GLOBAL)) {
            final GlobalRegistry globals = context.globals();
            int numGlobals = readVectorLength();
            int startingGlobalIndex = globalIndex;
            for (; globalIndex != startingGlobalIndex + numGlobals; globalIndex++) {
                readValueType();
                // Read mutability;
                read1();
                byte instruction = read1();
                long value = 0;
                switch (instruction) {
                    case Instructions.I32_CONST: {
                        value = readSignedInt32();
                        break;
                    }
                    case Instructions.I64_CONST: {
                        value = readSignedInt64();
                        break;
                    }
                    case Instructions.F32_CONST: {
                        value = readFloatAsInt32();
                        break;
                    }
                    case Instructions.F64_CONST: {
                        value = readFloatAsInt64();
                        break;
                    }
                    case Instructions.GLOBAL_GET: {
                        int existingIndex = readGlobalIndex();
                        if (module.symbolTable().globalMutability(existingIndex) == GlobalModifier.MUTABLE) {
                            throw WasmException.create(Failure.UNSPECIFIED_UNLINKABLE, "Cannot reset global variables that were initialized " +
                                            "with a non-constant global variable (not implemented).");
                        }
                        final int existingAddress = instance.globalAddress(existingIndex);
                        value = globals.loadAsLong(existingAddress);
                        break;
                    }
                }
                // Read END.
                read1();
                final int address = instance.globalAddress(globalIndex);
                globals.storeLong(address, value);
            }
        }
    }

    public void resetMemoryState(WasmContext context, WasmInstance instance) {
        if (tryJumpToSection(Section.DATA)) {
            readDataSection(context, instance);
        }
    }
}
