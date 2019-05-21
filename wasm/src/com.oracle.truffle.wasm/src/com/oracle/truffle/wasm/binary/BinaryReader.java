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


import static com.oracle.truffle.wasm.binary.Instructions.BLOCK;
import static com.oracle.truffle.wasm.binary.Instructions.DROP;
import static com.oracle.truffle.wasm.binary.Instructions.ELSE;
import static com.oracle.truffle.wasm.binary.Instructions.END;
import static com.oracle.truffle.wasm.binary.Instructions.F32_ADD;
import static com.oracle.truffle.wasm.binary.Instructions.F32_CONST;
import static com.oracle.truffle.wasm.binary.Instructions.F32_EQ;
import static com.oracle.truffle.wasm.binary.Instructions.F32_GE;
import static com.oracle.truffle.wasm.binary.Instructions.F32_GT;
import static com.oracle.truffle.wasm.binary.Instructions.F32_LE;
import static com.oracle.truffle.wasm.binary.Instructions.F32_LT;
import static com.oracle.truffle.wasm.binary.Instructions.F32_NE;
import static com.oracle.truffle.wasm.binary.Instructions.F64_CONST;
import static com.oracle.truffle.wasm.binary.Instructions.F64_EQ;
import static com.oracle.truffle.wasm.binary.Instructions.F64_GE;
import static com.oracle.truffle.wasm.binary.Instructions.F64_GT;
import static com.oracle.truffle.wasm.binary.Instructions.F64_LE;
import static com.oracle.truffle.wasm.binary.Instructions.F64_LT;
import static com.oracle.truffle.wasm.binary.Instructions.F64_NE;
import static com.oracle.truffle.wasm.binary.Instructions.I32_ADD;
import static com.oracle.truffle.wasm.binary.Instructions.I32_AND;
import static com.oracle.truffle.wasm.binary.Instructions.I32_CLZ;
import static com.oracle.truffle.wasm.binary.Instructions.I32_CONST;
import static com.oracle.truffle.wasm.binary.Instructions.I32_CTZ;
import static com.oracle.truffle.wasm.binary.Instructions.I32_DIV_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_DIV_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_EQ;
import static com.oracle.truffle.wasm.binary.Instructions.I32_EQZ;
import static com.oracle.truffle.wasm.binary.Instructions.I32_GE_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_GE_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_GT_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_GT_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_LE_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_LE_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_LT_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_LT_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_MUL;
import static com.oracle.truffle.wasm.binary.Instructions.I32_NE;
import static com.oracle.truffle.wasm.binary.Instructions.I32_OR;
import static com.oracle.truffle.wasm.binary.Instructions.I32_POPCNT;
import static com.oracle.truffle.wasm.binary.Instructions.I32_REM_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_REM_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_ROTL;
import static com.oracle.truffle.wasm.binary.Instructions.I32_ROTR;
import static com.oracle.truffle.wasm.binary.Instructions.I32_SHL;
import static com.oracle.truffle.wasm.binary.Instructions.I32_SHR_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_SHR_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_SUB;
import static com.oracle.truffle.wasm.binary.Instructions.I32_XOR;
import static com.oracle.truffle.wasm.binary.Instructions.I64_ADD;
import static com.oracle.truffle.wasm.binary.Instructions.I64_AND;
import static com.oracle.truffle.wasm.binary.Instructions.I64_CLZ;
import static com.oracle.truffle.wasm.binary.Instructions.I64_CONST;
import static com.oracle.truffle.wasm.binary.Instructions.I64_CTZ;
import static com.oracle.truffle.wasm.binary.Instructions.I64_DIV_S;
import static com.oracle.truffle.wasm.binary.Instructions.I64_DIV_U;
import static com.oracle.truffle.wasm.binary.Instructions.I64_MUL;
import static com.oracle.truffle.wasm.binary.Instructions.I64_OR;
import static com.oracle.truffle.wasm.binary.Instructions.I64_POPCNT;
import static com.oracle.truffle.wasm.binary.Instructions.I64_REM_S;
import static com.oracle.truffle.wasm.binary.Instructions.I64_REM_U;
import static com.oracle.truffle.wasm.binary.Instructions.I64_ROTL;
import static com.oracle.truffle.wasm.binary.Instructions.I64_ROTR;
import static com.oracle.truffle.wasm.binary.Instructions.I64_SHL;
import static com.oracle.truffle.wasm.binary.Instructions.I64_SHR_S;
import static com.oracle.truffle.wasm.binary.Instructions.I64_SHR_U;
import static com.oracle.truffle.wasm.binary.Instructions.I64_SUB;
import static com.oracle.truffle.wasm.binary.Instructions.I64_XOR;
import static com.oracle.truffle.wasm.binary.Instructions.IF;
import static com.oracle.truffle.wasm.binary.Instructions.LOCAL_GET;
import static com.oracle.truffle.wasm.binary.Instructions.LOCAL_SET;
import static com.oracle.truffle.wasm.binary.Instructions.LOCAL_TEE;
import static com.oracle.truffle.wasm.binary.Instructions.I64_EQ;
import static com.oracle.truffle.wasm.binary.Instructions.I64_EQZ;
import static com.oracle.truffle.wasm.binary.Instructions.I64_GE_S;
import static com.oracle.truffle.wasm.binary.Instructions.I64_GE_U;
import static com.oracle.truffle.wasm.binary.Instructions.I64_GT_S;
import static com.oracle.truffle.wasm.binary.Instructions.I64_GT_U;
import static com.oracle.truffle.wasm.binary.Instructions.I64_LE_S;
import static com.oracle.truffle.wasm.binary.Instructions.I64_LE_U;
import static com.oracle.truffle.wasm.binary.Instructions.I64_LT_S;
import static com.oracle.truffle.wasm.binary.Instructions.I64_LT_U;
import static com.oracle.truffle.wasm.binary.Instructions.I64_NE;
import static com.oracle.truffle.wasm.binary.Instructions.NOP;
import static com.oracle.truffle.wasm.binary.Instructions.UNREACHABLE;
import static com.oracle.truffle.wasm.binary.Sections.CODE;
import static com.oracle.truffle.wasm.binary.Sections.CUSTOM;
import static com.oracle.truffle.wasm.binary.Sections.DATA;
import static com.oracle.truffle.wasm.binary.Sections.ELEMENT;
import static com.oracle.truffle.wasm.binary.Sections.EXPORT;
import static com.oracle.truffle.wasm.binary.Sections.FUNCTION;
import static com.oracle.truffle.wasm.binary.Sections.GLOBAL;
import static com.oracle.truffle.wasm.binary.Sections.IMPORT;
import static com.oracle.truffle.wasm.binary.Sections.MEMORY;
import static com.oracle.truffle.wasm.binary.Sections.START;
import static com.oracle.truffle.wasm.binary.Sections.TABLE;
import static com.oracle.truffle.wasm.binary.Sections.TYPE;
import static com.oracle.truffle.wasm.binary.ValueTypes.VOID_TYPE;

import java.util.ArrayList;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.wasm.collection.ByteList;

/** Simple recursive-descend parser for the binary WebAssembly format.
 */
public class BinaryReader extends BinaryStreamReader {

    private static final int MAGIC = 0x6d736100;
    private static final int VERSION = 0x00000001;

    private WasmLanguage wasmLanguage;
    private WasmModule wasmModule;
    private byte[] bytesConsumed;

    public BinaryReader(WasmLanguage wasmLanguage, String moduleName, byte[] data) {
        super(data);
        this.wasmLanguage = wasmLanguage;
        this.wasmModule = new WasmModule(moduleName);
        this.bytesConsumed = new byte[1];
    }

    public void readModule() {
        Assert.assertEquals(read4(), MAGIC, "Invalid MAGIC number");
        Assert.assertEquals(read4(), VERSION, "Invalid VERSION number");
        readSections();
        wasmLanguage.getContextReference().get().registerModule(wasmModule);
    }

    public void readSections() {
        while (!isEOF()) {
            byte sectionID = read1();
            int size = readUnsignedInt32();
            int startOffset = offset;
            switch(sectionID) {
                case CUSTOM:
                    readCustomSection();
                    break;
                case TYPE:
                    readTypeSection();
                    break;
                case IMPORT:
                    readImportSection();
                    break;
                case FUNCTION:
                    readFunctionSection();
                    break;
                case TABLE:
                    readTableSection();
                    break;
                case MEMORY:
                    readMemorySection();
                    break;
                case GLOBAL:
                    readGlobalSection();
                    break;
                case EXPORT:
                    readExportSection();
                    break;
                case START:
                    readStartSection();
                    break;
                case ELEMENT:
                    readElementSection();
                    break;
                case CODE:
                    readCodeSection();
                    break;
                case DATA:
                    readDataSection();
                    break;
                default:
                    Assert.fail("invalid section ID: " + sectionID);
            }
            Assert.assertEquals(offset - startOffset, size, String.format("Declared section (0x%02X) size is incorrect", sectionID));
        }
    }

    public void readCustomSection() {
    }

    public void readTypeSection() {
        int numTypes = readVectorLength();
        for (int t = 0; t != numTypes; ++t) {
            byte type = read1();
            switch(type) {
                case 0x60:
                    readFunctionType();
                    break;
                default:
                    Assert.fail("Only function types are supported in the type section");
            }
        }
    }

    public void readImportSection() {

    }

    public void readFunctionSection() {
        int numFunctionTypeIdxs = readVectorLength();
        for (byte t = 0; t != numFunctionTypeIdxs; ++t) {
            int funcTypeIdx = readUnsignedInt32();
            wasmModule.symbolTable().allocateFunction(funcTypeIdx);
        }
    }

    public void readTableSection() {
    }

    public void readMemorySection() {
    }

    private void readDataSection() {
    }

    private void readCodeSection() {
        int numCodeEntries = readVectorLength();
        for (int entry = 0; entry < numCodeEntries; entry++) {
            int codeEntrySize = readUnsignedInt32();
            int startOffset = offset;
            // TODO: Offset the entry by the number of already parsed code entries
            readCodeEntry(codeEntrySize, entry);
            Assert.assertEquals(offset - startOffset, codeEntrySize, String.format("Code entry %d size is incorrect", entry));
        }
    }

    private void readCodeEntry(int codeEntrySize, int funcIndex) {
        int startOffset = offset;

        /* Read code entry (function) locals */
        WasmCodeEntry codeEntry = new WasmCodeEntry(data);
        wasmModule.symbolTable().function(funcIndex).setCodeEntry(codeEntry);
        readCodeEntryLocals(codeEntry);

        /* Create the necessary objects for the code entry */
        int expressionSize = codeEntrySize - (offset - startOffset);
        byte returnTypeId = wasmModule.symbolTable().function(funcIndex).returnType();
        ExecutionState state = new ExecutionState();
        WasmBlockNode block = new WasmBlockNode(codeEntry, offset, expressionSize, returnTypeId, state.stackSize(), 0, -1); // TODO: The byte constant offset length will be fixed after readBlock refactoring.
        WasmRootNode rootNode = new WasmRootNode(wasmLanguage, codeEntry, block);

        // Push a frame slot to the frame descriptor for every local.
        codeEntry.initLocalSlots(rootNode.getFrameDescriptor());

        // Abstractly interpret the code entry block.
        readBlock(block, state, returnTypeId);

        // Initialize the Truffle-related components required for execution.
        codeEntry.setByteConstants(state.byteConstants());
        codeEntry.initStackSlots(rootNode.getFrameDescriptor(), state.maxStackSize());
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        wasmModule.symbolTable().function(funcIndex).setCallTarget(callTarget);
    }

    private int readCodeEntryLocals(WasmCodeEntry codeEntry) {
        int numLocalsGroups = readVectorLength();
        int numLocals = 0;
        ByteList locals = new ByteList();
        for (int localGroup = 0; localGroup < numLocalsGroups; localGroup++) {
            int groupLength = readVectorLength();
            byte t = readValueType();
            for (int i = 0; i != groupLength; ++i) {
                locals.add(t);
            }
            numLocals += groupLength;
        }
        codeEntry.setLocalTypes(locals.toArray());
        return numLocals;
    }

    private void checkValidStateOnBlockExit(byte returnTypeId, ExecutionState state, int initialStackSize) {
        if (returnTypeId == 0x40) {
            Assert.assertEquals(state.stackSize(), initialStackSize, "Void function left values in the stack");
        } else {
            Assert.assertEquals(state.stackSize(), initialStackSize + 1, "Function left more than 1 values left in stack");
        }
    }

    private void readBlock(WasmBlockNode currentBlock, ExecutionState state, byte returnTypeId) {
        ArrayList<WasmNode> nestedControlTable = new ArrayList<>();
        int opcode;
        int startStackSize = state.stackSize();
        do {
            opcode = read1() & 0xFF;
            switch (opcode) {
                case UNREACHABLE:
                case NOP:
                    break;
                case BLOCK: {
                    byte blockTypeId = readBlockType();
                    int startOffset = offset();
                    int startByteConstantOffset = state.byteConstantOffset();
                    WasmBlockNode blockNode = new WasmBlockNode(currentBlock.codeEntry(), offset(), -1, blockTypeId, state.stackSize(), state.byteConstantOffset(), -1);
                    readBlock(blockNode, state, blockTypeId);
                    blockNode.setByteConstantLength(state.byteConstantOffset() - startByteConstantOffset);
                    blockNode.setByteLength(offset() - startOffset);
                    nestedControlTable.add(blockNode);
                    break;
                }
                case IF: {
                    WasmIfNode ifNode = readIf(currentBlock.codeEntry(), state);
                    nestedControlTable.add(ifNode);
                    break;
                }
                case ELSE:
                    break;
                case END:
                    break;
                case DROP:
                    state.pop();
                    break;
                case LOCAL_GET: {
                    int localIndex = readLocalIndex(bytesConsumed);
                    state.useByteConstant(bytesConsumed[0]);
                    // TODO: Assert localIndex exists.
                    currentBlock.codeEntry().localSlot(localIndex);
                    state.push();
                    break;
                }
                case LOCAL_SET: {
                    int localIndex = readLocalIndex(bytesConsumed);
                    state.useByteConstant(bytesConsumed[0]);
                    // TODO: Assert localIndex exists
                    currentBlock.codeEntry().localSlot(localIndex);
                    // Assert there is a value on the top of the stack.
                    Assert.assertLarger(state.stackSize(), 0, "local.set requires at least one element in the stack");
                    state.pop();
                    break;
                }
                case LOCAL_TEE: {
                    int localIndex = readLocalIndex(bytesConsumed);
                    state.useByteConstant(bytesConsumed[0]);
                    // TODO: Assert localIndex exists
                    currentBlock.codeEntry().localSlot(localIndex);
                    // Assert there is a value on the top of the stack.
                    Assert.assertLarger(state.stackSize(), 0, "local.tee requires at least one element in the stack");
                    state.push();
                    break;
                }
                case I32_CONST:
                    readSignedInt32(bytesConsumed);
                    state.useByteConstant(bytesConsumed[0]);
                    state.push();
                    break;
                case I64_CONST:
                    readSignedInt64(bytesConsumed);
                    state.useByteConstant(bytesConsumed[0]);
                    state.push();
                    break;
                case F32_CONST:
                    readFloatAsInt32();
                    state.push();
                    break;
                case F64_CONST:
                    readFloatAsInt64();
                    state.push();
                    break;
                case I32_EQZ:
                    state.pop();
                    state.push();
                    break;
                case I32_EQ:
                case I32_NE:
                case I32_LT_S:
                case I32_LT_U:
                case I32_GT_S:
                case I32_GT_U:
                case I32_LE_S:
                case I32_LE_U:
                case I32_GE_S:
                case I32_GE_U:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case I64_EQZ:
                    state.pop();
                    state.push();
                    break;
                case I64_EQ:
                case I64_NE:
                case I64_LT_S:
                case I64_LT_U:
                case I64_GT_S:
                case I64_GT_U:
                case I64_LE_S:
                case I64_LE_U:
                case I64_GE_S:
                case I64_GE_U:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case F32_EQ:
                case F32_NE:
                case F32_LT:
                case F32_GT:
                case F32_LE:
                case F32_GE:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case F64_EQ:
                case F64_NE:
                case F64_LT:
                case F64_GT:
                case F64_LE:
                case F64_GE:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case I32_CLZ:
                case I32_CTZ:
                case I32_POPCNT:
                    state.pop();
                    state.push();
                    break;
                case I32_ADD:
                case I32_SUB:
                case I32_MUL:
                case I32_DIV_S:
                case I32_DIV_U:
                case I32_REM_S:
                case I32_REM_U:
                case I32_AND:
                case I32_OR:
                case I32_XOR:
                case I32_SHL:
                case I32_SHR_S:
                case I32_SHR_U:
                case I32_ROTL:
                case I32_ROTR:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case I64_CLZ:
                case I64_CTZ:
                case I64_POPCNT:
                    state.pop();
                    state.push();
                    break;
                case I64_ADD:
                case I64_SUB:
                case I64_MUL:
                case I64_DIV_S:
                case I64_DIV_U:
                case I64_REM_S:
                case I64_REM_U:
                case I64_AND:
                case I64_OR:
                case I64_XOR:
                case I64_SHL:
                case I64_SHR_S:
                case I64_SHR_U:
                case I64_ROTL:
                case I64_ROTR:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case F32_ADD:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                default:
                    Assert.fail(Assert.format("Unknown opcode: 0x%02x", opcode));
                    break;
            }
        } while (opcode != END && opcode != ELSE);
        currentBlock.nestedControlTable = nestedControlTable.toArray(new WasmNode[nestedControlTable.size()]);
        checkValidStateOnBlockExit(returnTypeId, state, startStackSize);
    }

    private WasmIfNode readIf(WasmCodeEntry codeEntry, ExecutionState state) {
        byte blockTypeId = readBlockType();
        int initialStackPointer = state.stackSize();
        int initialByteConstantOffset = state.byteConstantOffset();

        // Pop the condition value from the stack.
        state.pop();

        // Read true branch.
        int startOffset = offset();
        int initialTrueByteConstantOffset = state.byteConstantOffset();
        WasmBlockNode trueBranchBlock = new WasmBlockNode(codeEntry, offset(), -1, blockTypeId, state.stackSize(), state.byteConstantOffset(), -1);
        trueBranchBlock.setByteConstantLength(state.byteConstantOffset() - initialTrueByteConstantOffset);
        readBlock(trueBranchBlock, state, blockTypeId);
        trueBranchBlock.setByteLength(offset() - startOffset);

        // Read false branch, if it exists.
        WasmNode falseBranch;
        if (peek1(-1) == ELSE) {
            int stackSizeAfterTrueBlock = state.stackSize();

            // If the if instruction has a true and a false branch, and it has non-void type, then each one of the two
            // readBlock above and below would push once, hence we need to pop once to compensate for the extra push.
            if (blockTypeId != VOID_TYPE) {
                state.pop();
            }

            int initialFalseOffset = offset();
            int initialFalseByteConstantOffset = state.byteConstantOffset();
            WasmBlockNode falseBranchBlock = new WasmBlockNode(codeEntry, offset(), -1, blockTypeId, state.stackSize(), state.byteConstantOffset(), -1);
            readBlock(falseBranchBlock, state, blockTypeId);
            falseBranchBlock.setByteConstantLength(state.byteConstantOffset() - initialFalseByteConstantOffset);
            falseBranchBlock.setByteLength(offset() - initialFalseOffset);
            falseBranch = falseBranchBlock;

            Assert.assertEquals(stackSizeAfterTrueBlock, state.stackSize(), "Stack sizes must be equal after both branches of an if statement.");
        } else {
            if (blockTypeId != VOID_TYPE) {
                Assert.fail("An if statement without an else branch block cannot return values.");
            }
            falseBranch = new WasmEmptyNode(codeEntry, 0);
        }

        return new WasmIfNode(codeEntry, trueBranchBlock, falseBranch, offset() - startOffset, blockTypeId, initialStackPointer, state.byteConstantOffset() - initialByteConstantOffset);
    }

    private void readElementSection() {
    }

    private void readStartSection() {
    }

    private void readExportSection() {
    }

    private void readGlobalSection() {
    }

    public void readFunctionType() {
        int paramsLength = readVectorLength();
        int resultLength = peekUnsignedInt32(paramsLength);
        resultLength = (resultLength == 0x40) ? 0 : resultLength;
        int idx = wasmModule.symbolTable().allocateFunctionType(paramsLength, resultLength);
        readParameterList(idx, paramsLength);
        readResultList(idx);
    }

    public void readParameterList(int funcTypeIdx, int numParams) {
        for (int paramIdx = 0; paramIdx != numParams; ++paramIdx) {
            byte type = readValueType();
            wasmModule.symbolTable().registerFunctionTypeParameter(funcTypeIdx, paramIdx, type);
        }
    }

    // Specification seems ambiguous: https://webassembly.github.io/spec/core/binary/types.html#result-types
    // According to the spec, the result type can only be 0x40 (void) or 0xtt, where tt is a value type.
    // However, the Wasm binary compiler produces binaries with either 0x00 or 0x01 0xtt. Therefore, we support both.
    public void readResultList(int funcTypeIdx) {
        byte b = read1();
        switch (b) {
            case VOID_TYPE:  // special byte indicating empty return type (same as above)
                break;
            case 0x00:  // empty vector
                break;
            case 0x01:  // vector with one element (produced by the Wasm binary compiler)
                byte type = readValueType();
                wasmModule.symbolTable().registerFunctionTypeReturnType(funcTypeIdx, 0, type);
                break;
            default:
                Assert.fail(String.format("Invalid return value specifier: 0x%02X", b));
        }
    }

    public boolean isEOF() {
        return offset == data.length;
    }

    public int readVectorLength() {
        return readUnsignedInt32();
    }

    public int readLocalIndex() {
        return readUnsignedInt32();
    }

    public int readLocalIndex(byte[] bytesConsumed) {
        return readUnsignedInt32(bytesConsumed);
    }
}
