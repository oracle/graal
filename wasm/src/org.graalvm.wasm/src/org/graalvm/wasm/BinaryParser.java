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

import static org.graalvm.wasm.TableRegistry.Table;
import static org.graalvm.wasm.WasmUtil.unsignedInt32ToLong;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.constants.CallIndirect;
import org.graalvm.wasm.constants.ExportIdentifier;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.constants.ImportIdentifier;
import org.graalvm.wasm.constants.Instructions;
import org.graalvm.wasm.constants.LimitsPrefix;
import org.graalvm.wasm.constants.Section;
import org.graalvm.wasm.exception.WasmLinkerException;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.nodes.WasmBlockNode;
import org.graalvm.wasm.nodes.WasmCallStubNode;
import org.graalvm.wasm.nodes.WasmIfNode;
import org.graalvm.wasm.nodes.WasmIndirectCallNode;
import org.graalvm.wasm.nodes.WasmNode;
import org.graalvm.wasm.nodes.WasmRootNode;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;

/**
 * Simple recursive-descend parser for the binary WebAssembly format.
 */
public class BinaryParser extends BinaryStreamParser {

    private static final int MAGIC = 0x6d736100;
    private static final int VERSION = 0x00000001;
    // Java indices cannot be bigger than 2^31 - 1.
    private static final long TABLE_MAX_SIZE = Integer.MAX_VALUE;
    private static final long MEMORY_MAX_PAGES = 1 << 16;

    private final WasmLanguage language;
    private final WasmModule module;
    private final WasmContext context;
    private final int[] limitsResult;

    /**
     * Modules may import, as well as define their own functions. Function IDs are shared among
     * imported and defined functions. This variable keeps track of the function indices, so that
     * imported and parsed code entries can be correctly associated to their respective functions
     * and types.
     */
    // TODO: We should remove this to reduce complexity - codeEntry state should be sufficient
    // to track the current largest function index.
    private int moduleFunctionIndex;

    BinaryParser(WasmLanguage language, WasmModule module, WasmContext context, byte[] data) {
        super(data);
        this.language = language;
        this.module = module;
        this.context = context;
        this.limitsResult = new int[2];
        this.moduleFunctionIndex = 0;
    }

    WasmModule readModule() {
        validateMagicNumberAndVersion();
        readSections();
        return module;
    }

    private void validateMagicNumberAndVersion() {
        Assert.assertIntEqual(read4(), MAGIC, "Invalid MAGIC number");
        Assert.assertIntEqual(read4(), VERSION, "Invalid VERSION number");
    }

    private void readSections() {
        while (!isEOF()) {
            byte sectionID = read1();
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
                    readCodeSection();
                    break;
                case Section.DATA:
                    readDataSection();
                    break;
                default:
                    Assert.fail("invalid section ID: " + sectionID);
            }
            Assert.assertIntEqual(offset - startOffset, size, String.format("Declared section (0x%02X) size is incorrect", sectionID));
        }
    }

    private void readCustomSection(int size) {
        // TODO: We skip the custom section for now, but we should see what we could typically pick
        // up here.
        offset += size;
    }

    private void readTypeSection() {
        int numTypes = readVectorLength();
        for (int t = 0; t != numTypes; ++t) {
            byte type = read1();
            switch (type) {
                case 0x60:
                    readFunctionType();
                    break;
                default:
                    Assert.fail("Only function types are supported in the type section");
            }
        }
    }

    private void readImportSection() {
        Assert.assertIntEqual(module.symbolTable().maxGlobalIndex(), -1,
                        "The global index should be -1 when the import section is first read.");
        int numImports = readVectorLength();
        for (int i = 0; i != numImports; ++i) {
            String moduleName = readName();
            String memberName = readName();
            byte importType = readImportType();
            switch (importType) {
                case ImportIdentifier.FUNCTION: {
                    int typeIndex = readTypeIndex();
                    module.symbolTable().importFunction(context, moduleName, memberName, typeIndex);
                    moduleFunctionIndex++;
                    break;
                }
                case ImportIdentifier.TABLE: {
                    byte elemType = readElemType();
                    Assert.assertIntEqual(elemType, ReferenceTypes.FUNCREF, "Invalid element type for table import");
                    readTableLimits(limitsResult);
                    module.symbolTable().importTable(context, moduleName, memberName, limitsResult[0], limitsResult[1]);
                    break;
                }
                case ImportIdentifier.MEMORY: {
                    readMemoryLimits(limitsResult);
                    module.symbolTable().importMemory(context, moduleName, memberName, limitsResult[0], limitsResult[1]);
                    break;
                }
                case ImportIdentifier.GLOBAL: {
                    byte type = readValueType();
                    byte mutability = readMutability();
                    int index = module.symbolTable().maxGlobalIndex() + 1;
                    module.symbolTable().importGlobal(context, moduleName, memberName, index, type, mutability);
                    break;
                }
                default: {
                    Assert.fail(String.format("Invalid import type identifier: 0x%02X", importType));
                }
            }
        }
    }

    private void readFunctionSection() {
        int numFunctions = readVectorLength();
        for (int i = 0; i != numFunctions; ++i) {
            int functionTypeIndex = readUnsignedInt32();
            module.symbolTable().declareFunction(functionTypeIndex);
        }
    }

    private void readTableSection() {
        int numTables = readVectorLength();
        Assert.assertIntLessOrEqual(module.symbolTable().tableCount() + numTables, 1, "Can import or declare at most one table per module");
        // Since in the current version of WebAssembly supports at most one table instance per
        // module.
        // this loop should be executed at most once.
        for (byte tableIndex = 0; tableIndex != numTables; ++tableIndex) {
            byte elemType = readElemType();
            Assert.assertIntEqual(elemType, ReferenceTypes.FUNCREF, "Invalid element type for table");
            readTableLimits(limitsResult);
            module.symbolTable().allocateTable(context, limitsResult[0], limitsResult[1]);
        }
    }

    private void readMemorySection() {
        int numMemories = readVectorLength();
        Assert.assertIntLessOrEqual(module.symbolTable().memoryCount() + numMemories, 1, "Can import or declare at most one memory per module");
        // Since in the current version of WebAssembly supports at most one table instance per
        // module.
        // this loop should be executed at most once.
        for (int i = 0; i != numMemories; ++i) {
            readMemoryLimits(limitsResult);
            module.symbolTable().allocateMemory(context, limitsResult[0], limitsResult[1]);
        }
    }

    private void readCodeSection() {
        int numCodeEntries = readVectorLength();
        WasmRootNode[] rootNodes = new WasmRootNode[numCodeEntries];
        for (int entry = 0; entry != numCodeEntries; ++entry) {
            rootNodes[entry] = createCodeEntry(moduleFunctionIndex + entry);
        }
        for (int entryIndex = 0; entryIndex != numCodeEntries; ++entryIndex) {
            int codeEntrySize = readUnsignedInt32();
            int startOffset = offset;
            readCodeEntry(moduleFunctionIndex + entryIndex, rootNodes[entryIndex]);
            Assert.assertIntEqual(offset - startOffset, codeEntrySize, String.format("Code entry %d size is incorrect", entryIndex));
            context.linker().resolveCodeEntry(module, entryIndex);
        }
        moduleFunctionIndex += numCodeEntries;
    }

    private WasmRootNode createCodeEntry(int funcIndex) {
        final WasmFunction function = module.symbolTable().function(funcIndex);
        WasmCodeEntry codeEntry = new WasmCodeEntry(function, data);
        function.setCodeEntry(codeEntry);

        /*
         * Create the root node and create and set the call target for the body. This needs to be
         * done before reading the body block, because we need to be able to create direct call
         * nodes {@see TruffleRuntime#createDirectCallNode} during parsing.
         */
        WasmRootNode rootNode = new WasmRootNode(language, codeEntry);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        function.setCallTarget(callTarget);

        return rootNode;
    }

    private void readCodeEntry(int funcIndex, WasmRootNode rootNode) {
        /*
         * Initialise the code entry local variables (which contain the parameters and the locals).
         */
        initCodeEntryLocals(funcIndex);

        /* Read (parse) and abstractly interpret the code entry */
        final WasmFunction function = module.symbolTable().function(funcIndex);
        final byte returnTypeId = function.returnType();
        final int returnTypeLength = function.returnTypeLength();
        ExecutionState state = new ExecutionState();
        state.pushStackState(0);
        WasmBlockNode bodyBlock = readBlockBody(rootNode.codeEntry(), state, returnTypeId, returnTypeId);
        state.popStackState();
        Assert.assertIntEqual(state.stackSize(), returnTypeLength,
                        "Stack size must match the return type length at the function end");
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
        rootNode.codeEntry().initStackSlots(rootNode.getFrameDescriptor(), state.maxStackSize());
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

    @SuppressWarnings("unused")
    private static void checkValidStateOnBlockExit(byte returnTypeId, ExecutionState state, int initialStackSize) {
        if (returnTypeId == ValueTypes.VOID_TYPE) {
            Assert.assertIntEqual(state.stackSize(), initialStackSize, "Void function left values in the stack");
        } else {
            Assert.assertIntEqual(state.stackSize(), initialStackSize + 1, "Function left more than 1 values left in stack");
        }
    }

    private WasmBlockNode readBlock(WasmCodeEntry codeEntry, ExecutionState state) {
        byte blockTypeId = readBlockType();
        return readBlockBody(codeEntry, state, blockTypeId, blockTypeId);
    }

    private LoopNode readLoop(WasmCodeEntry codeEntry, ExecutionState state) {
        byte blockTypeId = readBlockType();
        return readLoop(codeEntry, state, blockTypeId);
    }

    private WasmBlockNode readBlockBody(WasmCodeEntry codeEntry, ExecutionState state, byte returnTypeId, byte continuationTypeId) {
        ArrayList<Node> children = new ArrayList<>();
        int startStackSize = state.stackSize();
        int startOffset = offset();
        int startByteConstantOffset = state.byteConstantOffset();
        int startIntConstantOffset = state.intConstantOffset();
        int startLongConstantOffset = state.longConstantOffset();
        int startBranchTableOffset = state.branchTableOffset();
        int startProfileCount = state.profileCount();
        WasmBlockNode currentBlock = new WasmBlockNode(module, codeEntry, startOffset, returnTypeId, continuationTypeId, startStackSize,
                        startByteConstantOffset, startIntConstantOffset, startLongConstantOffset, startBranchTableOffset, startProfileCount);

        // Push the type length of the current block's continuation.
        // Used when branching out of nested blocks (br and br_if instructions).
        state.pushContinuationReturnLength(currentBlock.continuationTypeLength());

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
                    // Save the current block's stack pointer, in case we branch out of
                    // the nested block (continuation stack pointer).
                    int stackSize = state.stackSize();
                    state.pushStackState(stackSize);
                    WasmBlockNode nestedBlock = readBlock(codeEntry, state);
                    children.add(nestedBlock);
                    state.popStackState();
                    state.setReachable(reachable);
                    break;
                }
                case Instructions.LOOP: {
                    // Store the reachability of the current block, to restore it later.
                    boolean reachable = state.isReachable();
                    // Save the current block's stack pointer, in case we branch out of
                    // the nested block (continuation stack pointer).
                    state.pushStackState(state.stackSize());
                    LoopNode loopBlock = readLoop(codeEntry, state);
                    children.add(loopBlock);
                    state.popStackState();
                    state.setReachable(reachable);
                    break;
                }
                case Instructions.IF: {
                    // Pop the condition.
                    state.pop();
                    // Store the reachability of the current block, to restore it later.
                    boolean reachable = state.isReachable();
                    // Save the current block's stack pointer, in case we branch out of
                    // the nested block (continuation stack pointer).
                    // For the if block, we save the stack size reduced by 1, because of the
                    // condition value that will be popped before executing the if statement.
                    state.pushStackState(state.stackSize());
                    WasmIfNode ifNode = readIf(codeEntry, state);
                    children.add(ifNode);
                    state.popStackState();
                    state.setReachable(reachable);
                    break;
                }
                case Instructions.ELSE:
                    // We handle the else instruction in the same way as the end instruction.
                case Instructions.END:
                    // If the end instruction is not reachable, then the stack size must be adjusted
                    // to match the stack size at the continuation point.
                    if (!state.isReachable()) {
                        state.setStackSize(state.getStackState(0) + state.getContinuationReturnLength(0));
                    }
                    // After the end instruction, the semantics of Wasm stack size require
                    // that we consider the code again reachable.
                    state.setReachable(true);
                    break;
                case Instructions.BR: {
                    // TODO: restore check
                    // This check was here to validate the stack size before branching and make sure
                    // that the block that is currently executing produced as many values as it
                    // was meant to before branching.
                    // We now have to postpone this check, as the target of a branch instruction may
                    // be more than one levels up, so the amount of values it should leave in
                    // the stack depends on the branch target.
                    // Assert.assertEquals(state.stackSize() - startStackSize,
                    // currentBlock.returnTypeLength(), "Invalid stack state on BR instruction");
                    final int unwindLevel = readTargetOffset(state);
                    final int targetStackSize = state.getStackState(unwindLevel);
                    state.useIntConstant(targetStackSize);
                    final int continuationReturnLength = state.getContinuationReturnLength(unwindLevel);
                    state.useIntConstant(continuationReturnLength);
                    // This instruction is stack-polymorphic.
                    state.setReachable(false);
                    break;
                }
                case Instructions.BR_IF: {
                    state.pop();  // The branch condition.
                    // TODO: restore check
                    // This check was here to validate the stack size before branching and make sure
                    // that the block that is currently executing produced as many values as it
                    // was meant to before branching.
                    // We now have to postpone this check, as the target of a branch instruction may
                    // be more than one levels up, so the amount of values it should leave in the
                    // stack depends on the branch target.
                    // Assert.assertEquals(state.stackSize() - startStackSize,
                    // currentBlock.returnTypeLength(), "Invalid stack state on BR instruction");
                    final int unwindLevel = readTargetOffset(state);
                    state.useIntConstant(state.getStackState(unwindLevel));
                    state.useIntConstant(state.getContinuationReturnLength(unwindLevel));
                    state.incrementProfileCount();
                    break;
                }
                case Instructions.BR_TABLE: {
                    state.pop();
                    int numLabels = readVectorLength();
                    // We need to save three tables here, to maintain the mapping target -> state
                    // mapping:
                    // - the length of the return type
                    // - a table containing the branch targets for the instruction
                    // - a table containing the stack state for each corresponding branch target
                    // We encode this in a single array.
                    int[] branchTable = new int[2 * (numLabels + 1) + 1];
                    int returnLength = -1;
                    // The BR_TABLE instruction behaves like a 'switch' statement.
                    // There is one extra label for the 'default' case.
                    for (int i = 0; i != numLabels + 1; ++i) {
                        final int unwindLevel = readTargetOffset();
                        branchTable[1 + 2 * i + 0] = unwindLevel;
                        branchTable[1 + 2 * i + 1] = state.getStackState(unwindLevel);
                        final int blockReturnLength = state.getContinuationReturnLength(unwindLevel);
                        if (returnLength == -1) {
                            returnLength = blockReturnLength;
                        } else {
                            Assert.assertIntEqual(returnLength, blockReturnLength,
                                            "All target blocks in br.table must have the same return type length.");
                        }
                    }
                    branchTable[0] = returnLength;
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
                    state.useIntConstant(state.stackStateCount());
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
                    context.linker().resolveCallsite(module, currentBlock, children.size() - 1, function);

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
                    Assert.assertIntEqual(read1(), CallIndirect.ZERO_TABLE, "CALL_INDIRECT: Instruction must end with 0x00");
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
                    Assert.assertIntLessOrEqual(localIndex, codeEntry.numLocals(), "Invalid local index for local.get");
                    state.push();
                    break;
                }
                case Instructions.LOCAL_SET: {
                    int localIndex = readLocalIndex(state);
                    // Assert localIndex exists.
                    Assert.assertIntLessOrEqual(localIndex, codeEntry.numLocals(), "Invalid local index for local.set");
                    // Assert there is a value on the top of the stack.
                    Assert.assertIntGreater(state.stackSize(), 0, "local.set requires at least one element in the stack");
                    state.pop();
                    break;
                }
                case Instructions.LOCAL_TEE: {
                    int localIndex = readLocalIndex(state);
                    // Assert localIndex exists.
                    Assert.assertIntLessOrEqual(localIndex, codeEntry.numLocals(), "Invalid local index for local.tee");
                    // Assert there is a value on the top of the stack.
                    Assert.assertIntGreater(state.stackSize(), 0, "local.tee requires at least one element in the stack");
                    break;
                }
                case Instructions.GLOBAL_GET: {
                    int index = readGlobalIndex(state);
                    Assert.assertIntLessOrEqual(index, module.symbolTable().maxGlobalIndex(),
                                    "Invalid global index for global.get.");
                    state.push();
                    break;
                }
                case Instructions.GLOBAL_SET: {
                    int index = readGlobalIndex(state);
                    // Assert localIndex exists.
                    Assert.assertIntLessOrEqual(index, module.symbolTable().maxGlobalIndex(),
                                    "Invalid global index for global.set.");
                    // Assert that the global is mutable.
                    Assert.assertTrue(module.symbolTable().globalMutability(index) == GlobalModifier.MUTABLE,
                                    "Immutable globals cannot be set: " + index);
                    // Assert there is a value on the top of the stack.
                    Assert.assertIntGreater(state.stackSize(), 0, "global.set requires at least one element in the stack");
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
                    Assert.assertIntGreater(state.stackSize(), 0, String.format("load instruction 0x%02X requires at least one element in the stack", opcode));
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
                    Assert.assertIntGreater(state.stackSize(), 1, String.format("store instruction 0x%02X requires at least two elements in the stack", opcode));
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
                    Assert.fail(Assert.format("Unknown opcode: 0x%02x", opcode));
                    break;
            }
        } while (opcode != Instructions.END && opcode != Instructions.ELSE);
        currentBlock.initialize(toArray(children),
                        offset() - startOffset, state.byteConstantOffset() - startByteConstantOffset,
                        state.intConstantOffset() - startIntConstantOffset, state.longConstantOffset() - startLongConstantOffset,
                        state.branchTableOffset() - startBranchTableOffset, state.profileCount() - startProfileCount);
        // TODO: Restore this check, when we fix the case where the block contains a return
        // instruction.
        // checkValidStateOnBlockExit(returnTypeId, state, startStackSize);

        // Pop the current block return length in the return lengths stack.
        // Used when branching out of nested blocks (br and br_if instructions).
        state.popContinuationReturnLength();

        return currentBlock;
    }

    static Node[] toArray(ArrayList<Node> list) {
        if (list.size() == 0) {
            return null;
        }
        return list.toArray(new Node[list.size()]);
    }

    private LoopNode readLoop(WasmCodeEntry codeEntry, ExecutionState state, byte returnTypeId) {
        int initialStackPointer = state.stackSize();
        WasmBlockNode loopBlock = readBlockBody(codeEntry, state, returnTypeId, ValueTypes.VOID_TYPE);

        // TODO: Hack to correctly set the stack pointer for abstract interpretation.
        // If a block has branch instructions that target "shallower" blocks which return no value,
        // then it can leave no values in the stack, which is invalid for our abstract
        // interpretation.
        // Correct the stack pointer to the value it would have in case there were no branch
        // instructions.
        state.setStackSize(returnTypeId != ValueTypes.VOID_TYPE ? initialStackPointer + 1 : initialStackPointer);

        return Truffle.getRuntime().createLoopNode(loopBlock);
    }

    private WasmIfNode readIf(WasmCodeEntry codeEntry, ExecutionState state) {
        byte blockTypeId = readBlockType();
        // Note: the condition value was already popped at this point.
        int stackSizeAfterCondition = state.stackSize();

        // Read true branch.
        int startOffset = offset();
        WasmBlockNode trueBranchBlock = readBlockBody(codeEntry, state, blockTypeId, blockTypeId);

        // If a block has branch instructions that target "shallower" blocks which return no value,
        // then it can leave no values in the stack, which is invalid for our abstract
        // interpretation.
        // Correct the stack pointer to the value it would have in case there were no branch
        // instructions.
        state.setStackSize(stackSizeAfterCondition);

        // Read false branch, if it exists.
        WasmNode falseBranchBlock = null;
        if (peek1(-1) == Instructions.ELSE) {
            falseBranchBlock = readBlockBody(codeEntry, state, blockTypeId, blockTypeId);
        } else if (blockTypeId != ValueTypes.VOID_TYPE) {
            Assert.fail("An if statement without an else branch block cannot return values.");
        }
        int stackSizeBeforeCondition = stackSizeAfterCondition + 1;
        return new WasmIfNode(module, codeEntry, trueBranchBlock, falseBranchBlock, offset() - startOffset, blockTypeId, stackSizeBeforeCondition);
    }

    private void readElementSection() {
        int numElements = readVectorLength();
        for (int elemSegmentId = 0; elemSegmentId != numElements; ++elemSegmentId) {
            int tableIndex = readUnsignedInt32();
            // At the moment, WebAssembly (1.0, MVP) only supports one table instance, thus the only
            // valid table index is 0.
            // Support for different table indices and "segment flags" might be added in the future
            // (see
            // https://github.com/WebAssembly/bulk-memory-operations/blob/master/proposals/bulk-memory-operations/Overview.md#element-segments).
            Assert.assertIntEqual(tableIndex, 0, "Invalid table index");

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
                    Assert.fail(String.format("Invalid instruction for table offset expression: 0x%02X", instruction));
                }
            }

            // Copy the contents, or schedule a linker task for this.
            int segmentLength = readVectorLength();
            final SymbolTable symbolTable = module.symbolTable();
            final Table table = symbolTable.table();
            if (table == null || offsetGlobalIndex == -1) {
                // Note: we do not check if the earlier element segments were executed,
                // and we do not try to execute the element segments in order,
                // as we do with data sections and the memory.
                // Instead, if any table element is written more than once, we report an error.
                // Thus, the order in which the element sections are loaded is not important
                // (also, I did not notice the toolchains overriding the same element slots,
                // or anything in the spec about that).
                WasmFunction[] elements = new WasmFunction[segmentLength];
                for (int index = 0; index != segmentLength; ++index) {
                    final int functionIndex = readDeclaredFunctionIndex();
                    final WasmFunction function = symbolTable.function(functionIndex);
                    elements[index] = function;
                }
                context.linker().resolveElemSegment(context, module, elemSegmentId, offsetAddress, offsetGlobalIndex, segmentLength, elements);
            } else {
                table.ensureSizeAtLeast(offsetAddress + segmentLength);
                for (int index = 0; index != segmentLength; ++index) {
                    final int functionIndex = readDeclaredFunctionIndex();
                    final WasmFunction function = symbolTable.function(functionIndex);
                    table.set(offsetAddress + index, function);
                }
            }
        }
    }

    private void readEnd() {
        byte instruction = read1();
        Assert.assertByteEqual(instruction, (byte) Instructions.END, "Initialization expression must end with an END");
    }

    private void readStartSection() {
        int startFunctionIndex = readDeclaredFunctionIndex();
        module.symbolTable().setStartFunction(startFunctionIndex);
    }

    private void readExportSection() {
        int numExports = readVectorLength();
        for (int i = 0; i != numExports; ++i) {
            String exportName = readName();
            byte exportType = readExportType();
            switch (exportType) {
                case ExportIdentifier.FUNCTION: {
                    int functionIndex = readDeclaredFunctionIndex();
                    module.symbolTable().exportFunction(context, functionIndex, exportName);
                    break;
                }
                case ExportIdentifier.TABLE: {
                    int tableIndex = readTableIndex();
                    Assert.assertTrue(module.symbolTable().tableExists(), "No table was imported or declared, so cannot export a table");
                    Assert.assertIntEqual(tableIndex, 0, "Cannot export table index different than zero (only one table per module allowed)");
                    module.symbolTable().exportTable(context, exportName);
                    break;
                }
                case ExportIdentifier.MEMORY: {
                    readMemoryIndex();
                    module.symbolTable().exportMemory(context, exportName);
                    break;
                }
                case ExportIdentifier.GLOBAL: {
                    int index = readGlobalIndex();
                    module.symbolTable().exportGlobal(context, exportName, index);
                    break;
                }
                default: {
                    Assert.fail(String.format("Invalid export type identifier: 0x%02X", exportType));
                }
            }
        }
    }

    private void readGlobalSection() {
        final GlobalRegistry globals = context.globals();
        int numGlobals = readVectorLength();
        int startingGlobalIndex = module.symbolTable().maxGlobalIndex() + 1;
        for (int globalIndex = startingGlobalIndex; globalIndex != startingGlobalIndex + numGlobals; globalIndex++) {
            byte type = readValueType();
            // 0x00 means const, 0x01 means var
            byte mutability = readMutability();
            long value = 0;
            int existingIndex = -1;
            byte instruction = read1();
            boolean isInitialized;
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
                    throw Assert.fail(String.format("Invalid instruction for global initialization: 0x%02X", instruction));
            }
            instruction = read1();
            Assert.assertByteEqual(instruction, (byte) Instructions.END, "Global initialization must end with END");
            final int address = module.symbolTable().declareGlobal(context, globalIndex, type, mutability);
            if (isInitialized) {
                globals.storeLong(address, value);
                context.linker().resolveGlobalInitialization(module, globalIndex);
            } else {
                if (!module.symbolTable().importedGlobals().containsKey(existingIndex)) {
                    // The current WebAssembly spec says constant expressions can only refer to
                    // imported globals. We can easily remove this restriction in the future.
                    Assert.fail("The initializer for global " + globalIndex + " in module '" + module.name() +
                                    "' refers to a non-imported global.");
                }
                context.linker().resolveGlobalInitialization(context, module, globalIndex, existingIndex);
            }
        }
    }

    private void readDataSection() {
        int numDataSegments = readVectorLength();
        boolean allDataSectionsResolved = true;
        for (int dataSegmentId = 0; dataSegmentId != numDataSegments; ++dataSegmentId) {
            int memIndex = readUnsignedInt32();
            // At the moment, WebAssembly only supports one memory instance, thus the only valid
            // memory index is 0.
            Assert.assertIntEqual(memIndex, 0, "Invalid memory index, only the memory index 0 is currently supported.");
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
                    Assert.fail(String.format("Invalid instruction for data offset expression: 0x%02X", instruction));
            }
            // Try to immediately resolve the global's value, if that global is initialized.
            // Test functions that re-read the data section to reset the memory depend on this,
            // since they need to avoid re-linking.
            if (offsetGlobalIndex != -1 && module.symbolTable().isGlobalInitialized(offsetGlobalIndex)) {
                int offsetGlobalAddress = module.symbolTable().globalAddress(offsetGlobalIndex);
                offsetAddress = context.globals().loadAsInt(offsetGlobalAddress);
                offsetGlobalIndex = -1;
            }

            // Copy the contents, or schedule a linker task for this.
            int byteLength = readVectorLength();
            final WasmMemory memory = module.symbolTable().memory();
            if (memory == null || !allDataSectionsResolved || offsetGlobalIndex != -1) {
                // A data section can only be resolved after the memory is resolved.
                // If the data section is offset by a global variable,
                // then the data section can only be resolved after the global is resolved.
                // When some data section is not resolved, all the later data sections must be
                // resolved after it.
                byte[] dataSegment = new byte[byteLength];
                for (int writeOffset = 0; writeOffset != byteLength; ++writeOffset) {
                    byte b = read1();
                    dataSegment[writeOffset] = b;
                }
                context.linker().resolveDataSegment(context, module, dataSegmentId, offsetAddress, offsetGlobalIndex, byteLength, dataSegment, allDataSectionsResolved);
                allDataSectionsResolved = false;
            } else {
                // A data section can be loaded directly into memory only if there are no prior
                // unresolved data sections.
                memory.validateAddress(null, offsetAddress, byteLength);
                for (int writeOffset = 0; writeOffset != byteLength; ++writeOffset) {
                    byte b = read1();
                    memory.store_i32_8(null, offsetAddress + writeOffset, b);
                }
            }
        }
    }

    private void readFunctionType() {
        int paramsLength = readVectorLength();
        int resultLength = peekUnsignedInt32(paramsLength);
        resultLength = (resultLength == 0x40) ? 0 : resultLength;
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
            case ValueTypes.VOID_TYPE:  // special byte indicating empty return type (same as above)
                break;
            case 0x00:  // empty vector
                break;
            case 0x01:  // vector with one element (produced by the Wasm binary compiler)
                byte type = readValueType();
                module.symbolTable().registerFunctionTypeReturnType(funcTypeIdx, 0, type);
                break;
            default:
                Assert.fail(String.format("Invalid return value specifier: 0x%02X", b));
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
        int value = readUnsignedInt32(state);
        return value;
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
        readLimits(TABLE_MAX_SIZE, "initial table size", "max table size", out);
    }

    private void readMemoryLimits(int[] out) {
        readLimits(MEMORY_MAX_PAGES, "initial memory size", "max memory size", out);
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
                Assert.fail(String.format("Invalid limits prefix (expected 0x00 or 0x01, got 0x%02X", limitsPrefix));
        }

        // Convert min and max to longs to avoid checking bounds on overflowed values.
        long longMin = unsignedInt32ToLong(out[0]);
        long longMax = unsignedInt32ToLong(out[1]);
        Assert.assertLongLessOrEqual(longMin, upperBound, "Invalid " + minName + ", must be less than upper bound");
        if (out[1] != -1) {
            Assert.assertLongLessOrEqual(longMax, upperBound, "Invalid " + maxName + ", must be less than upper bound");
            Assert.assertLongLessOrEqual(longMin, longMax, "Invalid " + minName + ", must be less than " + maxName);
        }
    }

    private byte readLimitsPrefix() {
        return read1();
    }

    private String readName() {
        int nameLength = readVectorLength();
        byte[] name = new byte[nameLength];
        for (int i = 0; i != nameLength; ++i) {
            name[i] = read1();
        }
        return new String(name, StandardCharsets.US_ASCII);
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

    protected long readSignedInt64(ExecutionState state) {
        long value = peekSignedInt64(data, offset);
        byte length = peekLeb128Length(data, offset);
        if (state != null && mustPoolLeb128()) {
            state.useLongConstant(value);
            state.useByteConstant(length);
        }
        offset += length;
        return value;
    }

    public boolean mustPoolLeb128() {
        return mustPoolLeb128(data, offset, module.storeConstantsPolicy);
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
    void resetGlobalState() {
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
                            throw new WasmLinkerException("Cannot reset imports of mutable global variables (not implemented).");
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
                            throw new WasmLinkerException("Cannot reset global variables that were initialized " +
                                            "with a non-constant global variable (not implemented).");
                        }
                        final int existingAddress = module.symbolTable().globalAddress(existingIndex);
                        value = globals.loadAsLong(existingAddress);
                        break;
                    }
                }
                // Read END.
                read1();
                final int address = module.symbolTable().globalAddress(globalIndex);
                globals.storeLong(address, value);
            }
        }
    }

    void resetMemoryState(boolean zeroMemory) {
        final WasmMemory memory = module.symbolTable().memory();
        if (memory != null && zeroMemory) {
            memory.clear();
        }
        if (tryJumpToSection(Section.DATA)) {
            readDataSection();
        }
    }
}
