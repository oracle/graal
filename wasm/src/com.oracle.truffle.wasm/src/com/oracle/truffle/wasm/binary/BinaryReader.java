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


import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.wasm.collection.ByteList;

import java.util.ArrayList;

import static com.oracle.truffle.wasm.binary.Instructions.BLOCK;
import static com.oracle.truffle.wasm.binary.Instructions.DROP;
import static com.oracle.truffle.wasm.binary.Instructions.END;
import static com.oracle.truffle.wasm.binary.Instructions.F32_ADD;
import static com.oracle.truffle.wasm.binary.Instructions.F32_CONST;
import static com.oracle.truffle.wasm.binary.Instructions.F64_CONST;
import static com.oracle.truffle.wasm.binary.Instructions.I32_ADD;
import static com.oracle.truffle.wasm.binary.Instructions.I32_AND;
import static com.oracle.truffle.wasm.binary.Instructions.I32_CONST;
import static com.oracle.truffle.wasm.binary.Instructions.I32_DIV_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_DIV_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_MUL;
import static com.oracle.truffle.wasm.binary.Instructions.I32_OR;
import static com.oracle.truffle.wasm.binary.Instructions.I32_REM_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_REM_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_SUB;
import static com.oracle.truffle.wasm.binary.Instructions.I32_XOR;
import static com.oracle.truffle.wasm.binary.Instructions.I64_CONST;
import static com.oracle.truffle.wasm.binary.Instructions.NOP;
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
        int numLocals = readCodeEntryLocals();

        /* Create the necessary objects for the code entry */
        int expressionSize = codeEntrySize - (offset - startOffset);
        byte returnTypeId = wasmModule.symbolTable().function(funcIndex).returnType();
        ExecutionState state = new ExecutionState();
        WasmBlockNode block = new WasmBlockNode(codeEntry, offset, expressionSize, returnTypeId, state.stackSize());
        WasmRootNode rootNode = new WasmRootNode(wasmLanguage, codeEntry, block);

        // TODO: Push a frame slot to the frame descriptor for every local.

        // Abstractly interpret the code entry block.
        readBlock(block, state, returnTypeId);

        // Initialize the Truffle-related components required for execution.
        initTruffleForCodeEntry(codeEntry, numLocals, rootNode, state, funcIndex);

        // TODO: For structured code, we need to set the expressionSize later.
    }

    private int readCodeEntryLocals() {
        int numLocals = readVectorLength();
        for (int local = 0; local < numLocals; local++) {
            throw new RuntimeException("Not implemented");
        }
        return numLocals;
    }

    private void initTruffleForCodeEntry(WasmCodeEntry codeEntry, int numLocals, WasmRootNode rootNode, ExecutionState state, int funcIndex) {
        codeEntry.initLocalSlots(rootNode.getFrameDescriptor(), numLocals);
        codeEntry.initStackSlots(rootNode.getFrameDescriptor(), state.maxStackSize);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        wasmModule.symbolTable().function(funcIndex).setCallTarget(callTarget);
    }

    private void checkValidStateOnBlockExit(byte returnTypeId, ExecutionState state, int initialStackSize) {
        if (returnTypeId == 0x40) {
            Assert.assertEquals(state.stackSize(), initialStackSize, "Void function left values in the stack");
        } else {
            Assert.assertEquals(state.stackSize(), initialStackSize + 1, "Function left more than 1 values left in stack");
        }
    }

    private void readBlock(WasmBlockNode currentBlock, ExecutionState state, byte returnTypeId) {
        ByteList constantLengthTable = new ByteList();
        ArrayList<WasmNode> nestedControlTable = new ArrayList<>();
        int opcode;
        int startStackSize = state.stackSize();
        do {
            opcode = read1() & 0xFF;
            switch (opcode) {
                case NOP:
                    break;
                case BLOCK:
                    byte blockTypeId = readBlockType();
                    int startOffset = offset();
                    WasmBlockNode blockNode = new WasmBlockNode(currentBlock.codeEntry(), offset(), -1, blockTypeId, state.stackSize());
                    readBlock(blockNode, state, blockTypeId);
                    blockNode.setSize(offset() - startOffset);
                    nestedControlTable.add(blockNode);
                    break;
                case END:
                    break;
                case DROP:
                    state.pop();
                    break;
                case I32_CONST:
                    readSignedInt32(bytesConsumed);
                    constantLengthTable.add(bytesConsumed[0]);
                    state.push();
                    break;
                case I64_CONST:
                    Assert.fail("Not implemented");
                    break;
                case F32_CONST:
                    readFloatAsInt32();
                    state.push();
                    break;
                case F64_CONST:
                    readFloatAsInt64();
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
                case F32_ADD:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                default:
                    Assert.fail(Assert.format("Unknown opcode: 0x%02x", opcode));
                    break;
            }
        } while (opcode != END);
        currentBlock.constantLengthTable = constantLengthTable.toArray();
        currentBlock.nestedControlTable = nestedControlTable.toArray(new WasmNode[nestedControlTable.size()]);
        checkValidStateOnBlockExit(returnTypeId, state, startStackSize);
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
}
