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


import static com.oracle.truffle.wasm.binary.constants.Instructions.BLOCK;
import static com.oracle.truffle.wasm.binary.constants.Instructions.BR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.BR_IF;
import static com.oracle.truffle.wasm.binary.constants.Instructions.BR_TABLE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.CALL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.CALL_INDIRECT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.DROP;
import static com.oracle.truffle.wasm.binary.constants.Instructions.ELSE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.END;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_ABS;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_ADD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_CEIL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_CONST;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_CONVERT_I32_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_CONVERT_I32_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_CONVERT_I64_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_CONVERT_I64_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_COPYSIGN;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_DEMOTE_F64;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_DIV;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_EQ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_FLOOR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_GE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_GT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_LE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_LOAD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_LT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_MAX;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_MIN;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_MUL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_NE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_NEAREST;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_NEG;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_REINTERPRET_I32;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_SQRT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_STORE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_SUB;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_TRUNC;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_ABS;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_ADD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_CEIL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_CONST;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_CONVERT_I32_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_CONVERT_I32_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_CONVERT_I64_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_CONVERT_I64_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_COPYSIGN;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_DIV;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_EQ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_FLOOR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_GE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_GT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_LE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_LOAD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_LT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_MAX;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_MIN;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_MUL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_NE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_NEAREST;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_NEG;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_PROMOTE_F32;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_REINTERPRET_I64;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_SQRT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_STORE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_SUB;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_TRUNC;
import static com.oracle.truffle.wasm.binary.constants.Instructions.GLOBAL_GET;
import static com.oracle.truffle.wasm.binary.constants.Instructions.GLOBAL_SET;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_ADD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_AND;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_CLZ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_CONST;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_CTZ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_DIV_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_DIV_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_EQ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_EQZ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_GE_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_GE_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_GT_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_GT_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LE_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LE_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LOAD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LOAD16_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LOAD16_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LOAD8_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LOAD8_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LT_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LT_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_MUL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_NE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_OR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_POPCNT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_REINTERPRET_F32;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_REM_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_REM_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_ROTL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_ROTR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_SHL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_SHR_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_SHR_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_STORE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_STORE_16;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_STORE_8;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_SUB;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_TRUNC_F32_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_TRUNC_F32_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_TRUNC_F64_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_TRUNC_F64_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_WRAP_I64;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_XOR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_ADD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_AND;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_CLZ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_CONST;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_CTZ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_DIV_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_DIV_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_EQ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_EQZ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_EXTEND_I32_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_EXTEND_I32_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_GE_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_GE_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_GT_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_GT_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LE_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LE_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD16_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD16_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD32_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD32_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD8_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD8_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LT_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LT_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_MUL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_NE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_OR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_POPCNT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_REINTERPRET_F64;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_REM_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_REM_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_ROTL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_ROTR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_SHL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_SHR_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_SHR_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_STORE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_STORE_16;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_STORE_32;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_STORE_8;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_SUB;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_TRUNC_F32_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_TRUNC_F32_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_TRUNC_F64_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_TRUNC_F64_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_XOR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.IF;
import static com.oracle.truffle.wasm.binary.constants.Instructions.LOCAL_GET;
import static com.oracle.truffle.wasm.binary.constants.Instructions.LOCAL_SET;
import static com.oracle.truffle.wasm.binary.constants.Instructions.LOCAL_TEE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.LOOP;
import static com.oracle.truffle.wasm.binary.constants.Instructions.MEMORY_GROW;
import static com.oracle.truffle.wasm.binary.constants.Instructions.MEMORY_SIZE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.NOP;
import static com.oracle.truffle.wasm.binary.constants.Instructions.RETURN;
import static com.oracle.truffle.wasm.binary.constants.Instructions.SELECT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.UNREACHABLE;
import static com.oracle.truffle.wasm.binary.constants.Sections.CODE;
import static com.oracle.truffle.wasm.binary.constants.Sections.CUSTOM;
import static com.oracle.truffle.wasm.binary.constants.Sections.DATA;
import static com.oracle.truffle.wasm.binary.constants.Sections.ELEMENT;
import static com.oracle.truffle.wasm.binary.constants.Sections.EXPORT;
import static com.oracle.truffle.wasm.binary.constants.Sections.FUNCTION;
import static com.oracle.truffle.wasm.binary.constants.Sections.GLOBAL;
import static com.oracle.truffle.wasm.binary.constants.Sections.IMPORT;
import static com.oracle.truffle.wasm.binary.constants.Sections.MEMORY;
import static com.oracle.truffle.wasm.binary.constants.Sections.START;
import static com.oracle.truffle.wasm.binary.constants.Sections.TABLE;
import static com.oracle.truffle.wasm.binary.constants.Sections.TYPE;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.wasm.binary.constants.ExportIdentifier;
import com.oracle.truffle.wasm.binary.constants.GlobalModifier;
import com.oracle.truffle.wasm.binary.constants.ImportIdentifier;
import com.oracle.truffle.wasm.binary.memory.WasmMemory;
import com.oracle.truffle.wasm.collection.ByteArrayList;

/** Simple recursive-descend parser for the binary WebAssembly format.
 */
public class BinaryReader extends BinaryStreamReader {

    private static final int MAGIC = 0x6d736100;
    private static final int VERSION = 0x00000001;

    private WasmLanguage wasmLanguage;
    private WasmModule wasmModule;
    private byte[] bytesConsumed;

    /**
     * Modules may import, as well as define their own functions.
     * Function IDs are shared among imported and defined functions.
     * This variable keeps track of the function indices, so that imported and parsed code
     * entries can be correctly associated to their respective functions and types.
     */
    private int moduleFunctionIndex;

    BinaryReader(WasmLanguage wasmLanguage, String moduleName, byte[] data) {
        super(data);
        this.wasmLanguage = wasmLanguage;
        this.wasmModule = new WasmModule(moduleName);
        this.bytesConsumed = new byte[1];
        this.moduleFunctionIndex = 0;
    }

    void readModule() {
        Assert.assertEquals(read4(), MAGIC, "Invalid MAGIC number");
        Assert.assertEquals(read4(), VERSION, "Invalid VERSION number");
        readSections();
        wasmLanguage.getContextReference().get().registerModule(wasmModule);
    }

    private void readSections() {
        while (!isEOF()) {
            byte sectionID = read1();
            int size = readUnsignedInt32();
            int startOffset = offset;
            switch (sectionID) {
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

    private void readCustomSection() {
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
        int numImports = readVectorLength();
        for (int i = 0; i != numImports; ++i) {
            String moduleName = readName();
            String importName = readName();
            byte importType = readImportType();
            switch (importType) {
                case ImportIdentifier.FUNCTION: {
                    int typeIndex = readTypeIndex();
                    wasmModule.symbolTable().importFunction(wasmLanguage, moduleName, importName, typeIndex);
                    moduleFunctionIndex++;
                    break;
                }
                case ImportIdentifier.TABLE: {
                    // TODO: This table is normally supposed to be provided by the external environment (e.g. JS).
                    byte elemType = read1();
                    Assert.assertEquals(elemType, 0x70, "Invalid element type for table import");
                    byte limitsPrefix = read1();
                    switch (limitsPrefix) {
                        case 0x00: {
                            int initSize = readUnsignedInt32();  // initial size (in number of entries)
                            wasmModule.table().initialize(initSize);
                            break;
                        }
                        case 0x01: {
                            int initSize = readUnsignedInt32();  // initial size (in number of entries)
                            int maxSize = readUnsignedInt32();  // max size (in number of entries)
                            wasmModule.table().initialize(initSize, maxSize);
                            break;
                        }
                        default:
                            Assert.fail(String.format("Invalid limits prefix for imported table (expected 0x00 or 0x01, got 0x%02X", limitsPrefix));
                    }
                    break;
                }
                case ImportIdentifier.MEMORY: {
                    // TODO: This memory is normally supposed to be provided by the external environment (e.g. JS).
                    byte limitsPrefix = read1();
                    switch (limitsPrefix) {
                        case 0x00: {
                            readUnsignedInt32();  // initial size (in number of entries)
                            break;
                        }
                        case 0x01: {
                            readUnsignedInt32();  // initial size (in number of entries)
                            readUnsignedInt32();  // max size (in number of entries)
                            break;
                        }
                        default:
                            Assert.fail(String.format("Invalid limits prefix for imported memory (expected 0x00 or 0x01, got 0x%02X", limitsPrefix));
                    }
                    break;
                }
                case ImportIdentifier.GLOBAL: {
                    byte type = readValueType();
                    byte mut = read1();  // 0x00 means const, 0x01 means var
                    wasmModule.globals().registerImported(importName, type, mut != GlobalModifier.CONSTANT);
                    // TODO: Store the imported global.
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
            wasmModule.symbolTable().allocateFunction(wasmLanguage, functionTypeIndex);
        }
    }

    private void readTableSection() {
        int numTables = readVectorLength();
        // Since in the current version of WebAssembly supports at most one table instance,
        // this loop should be executed at most once.
        for (byte tableIndex = 0; tableIndex != numTables; ++tableIndex) {
            byte elemType = read1();
            Assert.assertEquals(elemType, 0x70, "Invalid element type for table");
            byte limitsPrefix = read1();
            switch (limitsPrefix) {
                case 0x00: {
                    int initSize = readUnsignedInt32();  // initial size (in number of entries)
                    wasmModule.table().initialize(initSize);
                    break;
                }
                case 0x01: {
                    int initSize = readUnsignedInt32();  // initial size (in number of entries)
                    int maxSize = readUnsignedInt32();  // max size (in number of entries)
                    wasmModule.table().initialize(initSize, maxSize);
                    break;
                }
                default:
                    Assert.fail(String.format("Invalid limits prefix for table (expected 0x00 or 0x01, got 0x%02X", limitsPrefix));
            }
        }
    }

    private void readMemorySection() {
        int numMemories = readVectorLength();
        for (int i = 0; i != numMemories; ++i) {
            byte limitsPrefix = read1();
            switch (limitsPrefix) {
                case 0x00: {
                    /* Return value ignored, as we don't rely on the memory definition for the memory size. */
                    readUnsignedInt32();  // initial size (in Wasm pages)
                    break;
                }
                case 0x01: {
                    /* Return values ignored, as we don't rely on the memory definition for the memory size. */
                    readUnsignedInt32();  // initial size (in Wasm pages)
                    readUnsignedInt32();  // max size (in Wasm pages)
                    break;
                }
                default:
                    Assert.fail(String.format("Invalid limits prefix for memory (expected 0x00 or 0x01, got 0x%02X", limitsPrefix));
            }
        }
    }

    private void readCodeSection() {
        int numCodeEntries = readVectorLength();
        WasmRootNode[] rootNodes = new WasmRootNode[numCodeEntries];
        for (int entry = 0; entry != numCodeEntries; ++entry) {
            rootNodes[entry] = createCodeEntry(moduleFunctionIndex + entry);
        }
        for (int entry = 0; entry != numCodeEntries; ++entry) {
            int codeEntrySize = readUnsignedInt32();
            int startOffset = offset;
            readCodeEntry(moduleFunctionIndex + entry, rootNodes[entry]);
            Assert.assertEquals(offset - startOffset, codeEntrySize, String.format("Code entry %d size is incorrect", entry));
        }
        moduleFunctionIndex += numCodeEntries;
    }

    private WasmRootNode createCodeEntry(int funcIndex) {
        WasmCodeEntry codeEntry = new WasmCodeEntry(funcIndex, data);
        wasmModule.symbolTable().function(funcIndex).setCodeEntry(codeEntry);

        /*
         * Create the root node and create and set the call target for the body.
         * This needs to be done before reading the body block, because we need to be able to
         * create direct call nodes {@see TruffleRuntime#createDirectCallNode} during parsing.
         */
        WasmRootNode rootNode = new WasmRootNode(wasmLanguage, codeEntry);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        wasmModule.symbolTable().function(funcIndex).setCallTarget(callTarget);

        return rootNode;
    }

    private void readCodeEntry(int funcIndex, WasmRootNode rootNode) {
        /* Initialise the code entry local variables (which contain the parameters and the locals). */
        initCodeEntryLocals(funcIndex);

        /* Read (parse) and abstractly interpret the code entry */
        byte returnTypeId = wasmModule.symbolTable().function(funcIndex).returnType();
        ExecutionState state = new ExecutionState();
        WasmBlockNode bodyBlock = readBlock(rootNode.codeEntry(), state, returnTypeId);
        rootNode.setBody(bodyBlock);

        /* Push a frame slot to the frame descriptor for every local. */
        rootNode.codeEntry().initLocalSlots(rootNode.getFrameDescriptor());

        /* Initialize the Truffle-related components required for execution. */
        rootNode.codeEntry().setByteConstants(state.byteConstants());
        rootNode.codeEntry().setIntConstants(state.intConstants());
        rootNode.codeEntry().setNumericLiterals(state.numericLiterals());
        rootNode.codeEntry().setBranchTables(state.branchTables());
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
        WasmCodeEntry codeEntry = wasmModule.symbolTable().function(funcIndex).codeEntry();
        int typeIndex = wasmModule.symbolTable().function(funcIndex).typeIndex();
        ByteArrayList argumentTypes = wasmModule.symbolTable().getFunctionTypeArgumentTypes(typeIndex);
        ByteArrayList localTypes = readCodeEntryLocals();
        byte[] allLocalTypes = ByteArrayList.concat(argumentTypes, localTypes);
        codeEntry.setLocalTypes(allLocalTypes);
    }

    private void checkValidStateOnBlockExit(byte returnTypeId, ExecutionState state, int initialStackSize) {
        if (returnTypeId == 0x40) {
            Assert.assertEquals(state.stackSize(), initialStackSize, "Void function left values in the stack");
        } else {
            Assert.assertEquals(state.stackSize(), initialStackSize + 1, "Function left more than 1 values left in stack");
        }
    }

    private WasmBlockNode readBlock(WasmCodeEntry codeEntry, ExecutionState state) {
        byte blockTypeId = readBlockType();
        return readBlock(codeEntry, state, blockTypeId);
    }

    private WasmLoopNode readLoop(WasmCodeEntry codeEntry, ExecutionState state) {
        byte blockTypeId = readBlockType();
        return readLoop(codeEntry, state, blockTypeId);
    }

    private WasmBlockNode readBlock(WasmCodeEntry codeEntry, ExecutionState state, byte returnTypeId) {
        ArrayList<WasmNode> nestedControlTable = new ArrayList<>();
        ArrayList<Node> callNodes = new ArrayList<>();
        int startStackSize = state.stackSize();
        int startOffset = offset();
        int startByteConstantOffset = state.byteConstantOffset();
        int startIntConstantOffset = state.intConstantOffset();
        int startNumericLiteralOffset = state.numericLiteralOffset();
        int startBranchTableOffset = state.branchTableOffset();
        WasmBlockNode currentBlock = new WasmBlockNode(wasmModule, codeEntry, startOffset, returnTypeId, startStackSize, startByteConstantOffset, startIntConstantOffset, startNumericLiteralOffset, startBranchTableOffset);
        int opcode;
        do {
            opcode = read1() & 0xFF;
            switch (opcode) {
                case UNREACHABLE:
                case NOP:
                    break;
                case BLOCK: {
                    state.pushStackState();
                    WasmBlockNode nestedBlock = readBlock(codeEntry, state);
                    nestedControlTable.add(nestedBlock);
                    state.popStackState();
                    break;
                }
                case LOOP: {
                    state.pushStackState();
                    WasmLoopNode loopBlock = readLoop(codeEntry, state);
                    nestedControlTable.add(loopBlock);
                    state.popStackState();
                    break;
                }
                case IF: {
                    state.pushStackState();
                    WasmIfNode ifNode = readIf(codeEntry, state);
                    nestedControlTable.add(ifNode);
                    state.popStackState();
                    break;
                }
                case ELSE:
                    break;
                case END:
                    break;
                case BR: {
                    // TODO: restore check
                    // This check was here to validate the stack size before branching and make sure that the block that
                    // is currently executing produced as many values as it was meant to before branching.
                    // We now have to postpone this check, as the target of a branch instruction may be more than one
                    // levels up, so the amount of values it should leave in the stack depends on the branch target.
                    // Assert.assertEquals(state.stackSize() - startStackSize, currentBlock.returnTypeLength(), "Invalid stack state on BR instruction");
                    int unwindLevel = readLabelIndex(bytesConsumed);
                    state.saveNumericLiteral(unwindLevel);
                    state.useByteConstant(bytesConsumed[0]);
                    state.useIntConstant(state.getStackState(unwindLevel));
                    break;
                }
                case BR_IF: {
                    state.pop();  // The branch condition.
                    // TODO: restore check
                    // This check was here to validate the stack size before branching and make sure that the block that
                    // is currently executing produced as many values as it was meant to before branching.
                    // We now have to postpone this check, as the target of a branch instruction may be more than one
                    // levels up, so the amount of values it should leave in the stack depends on the branch target.
                    // Assert.assertEquals(state.stackSize() - startStackSize, currentBlock.returnTypeLength(), "Invalid stack state on BR instruction");
                    int unwindLevel = readLabelIndex(bytesConsumed);
                    state.saveNumericLiteral(unwindLevel);
                    state.useByteConstant(bytesConsumed[0]);
                    state.useIntConstant(state.getStackState(unwindLevel));
                    break;
                }
                case BR_TABLE: {
                    int numLabels = readVectorLength();
                    // We need to save two tables here, to maintain the mapping target -> state mapping:
                    // - a table containing the branch targets for the instruction
                    // - a table containing the stack state for each corresponding branch target
                    int[] branchTable = new int[numLabels + 1];
                    int[] stackStates = new int[numLabels + 1];
                    // The BR_TABLE instruction behaves like a 'switch' statement.
                    // There is one extra label for the 'default' case.
                    for (int i = 0; i != numLabels + 1; ++i) {
                        branchTable[i] = readLabelIndex();
                        stackStates[i] = state.getStackState(branchTable[i]);
                    }
                    state.pop();  // The offset to the branch table.
                    state.saveBranchTable(branchTable);
                    state.saveBranchTable(stackStates);
                    break;
                }
                case RETURN: {
                    break;
                }
                case CALL: {
                    int functionIndex = readFunctionIndex(bytesConsumed);
                    state.saveNumericLiteral(functionIndex);
                    state.useByteConstant(bytesConsumed[0]);
                    WasmFunction function = wasmModule.symbolTable().function(functionIndex);
                    state.pop(function.numArguments());
                    state.push(function.returnTypeLength());
                    callNodes.add(Truffle.getRuntime().createDirectCallNode(function.getCallTarget()));
                    break;
                }
                case CALL_INDIRECT: {
                    int expectedFunctionTypeIndex = readTypeIndex(bytesConsumed);
                    state.saveNumericLiteral(expectedFunctionTypeIndex);
                    state.useByteConstant(bytesConsumed[0]);
                    int numArguments = wasmModule.symbolTable().getFunctionTypeNumArguments(expectedFunctionTypeIndex);
                    int returnLength = wasmModule.symbolTable().getFunctionTypeReturnTypeLength(expectedFunctionTypeIndex);
                    state.pop();  // The function index to call.
                    state.pop(numArguments);
                    state.push(returnLength);
                    callNodes.add(Truffle.getRuntime().createIndirectCallNode());
                    Assert.assertEquals(read1(), 0x00, "CALL_INDIRECT: Instruction must end with 0x00");
                    break;
                }
                case DROP:
                    state.pop();
                    break;
                case SELECT:
                    // Pop three values from the stack: the condition and the values to select between.
                    state.pop(3);
                    state.push();
                    break;
                case LOCAL_GET: {
                    int localIndex = readLocalIndex(bytesConsumed);
                    state.saveNumericLiteral(localIndex);
                    state.useByteConstant(bytesConsumed[0]);
                    // Assert localIndex exists.
                    Assert.assertLessEqual(localIndex, codeEntry.numLocals(), "Invalid local index for local.get");
                    state.push();
                    break;
                }
                case LOCAL_SET: {
                    int localIndex = readLocalIndex(bytesConsumed);
                    state.saveNumericLiteral(localIndex);
                    state.useByteConstant(bytesConsumed[0]);
                    // Assert localIndex exists.
                    Assert.assertLessEqual(localIndex, codeEntry.numLocals(), "Invalid local index for local.set");
                    // Assert there is a value on the top of the stack.
                    Assert.assertLarger(state.stackSize(), 0, "local.set requires at least one element in the stack");
                    state.pop();
                    break;
                }
                case LOCAL_TEE: {
                    int localIndex = readLocalIndex(bytesConsumed);
                    state.saveNumericLiteral(localIndex);
                    state.useByteConstant(bytesConsumed[0]);
                    // Assert localIndex exists.
                    Assert.assertLessEqual(localIndex, codeEntry.numLocals(), "Invalid local index for local.tee");
                    // Assert there is a value on the top of the stack.
                    Assert.assertLarger(state.stackSize(), 0, "local.tee requires at least one element in the stack");
                    break;
                }
                case GLOBAL_GET: {
                    int globalIndex = readLocalIndex(bytesConsumed);
                    state.saveNumericLiteral(globalIndex);
                    state.useByteConstant(bytesConsumed[0]);
                    Assert.assertLessEqual(globalIndex, wasmModule.globals().size(), "Invalid global index for global.get");
                    state.push();
                    break;
                }
                case GLOBAL_SET: {
                    int globalIndex = readLocalIndex(bytesConsumed);
                    state.saveNumericLiteral(globalIndex);
                    state.useByteConstant(bytesConsumed[0]);
                    // Assert localIndex exists.
                    Assert.assertLessEqual(globalIndex, wasmModule.globals().size(), "Invalid global index for global.set");
                    // Assert there is a value on the top of the stack.
                    Assert.assertLarger(state.stackSize(), 0, "global.set requires at least one element in the stack");
                    state.pop();
                    break;
                }
                case I32_LOAD:
                case I64_LOAD:
                case F32_LOAD:
                case F64_LOAD:
                case I32_LOAD8_S:
                case I32_LOAD8_U:
                case I32_LOAD16_S:
                case I32_LOAD16_U:
                case I64_LOAD8_S:
                case I64_LOAD8_U:
                case I64_LOAD16_S:
                case I64_LOAD16_U:
                case I64_LOAD32_S:
                case I64_LOAD32_U: {
                    readUnsignedInt32(bytesConsumed);  // align
                    // We don't store the `align` literal, as our implementation does not make use of it,
                    // but we need to store it's byte length, so that we can skip it during execution.
                    state.useByteConstant(bytesConsumed[0]);
                    int offset = readUnsignedInt32(bytesConsumed);  // offset
                    state.saveNumericLiteral(offset);
                    state.useByteConstant(bytesConsumed[0]);
                    Assert.assertLarger(state.stackSize(), 0, String.format("load instruction 0x%02X requires at least one element in the stack", opcode));
                    state.pop();   // Base address.
                    state.push();  // Loaded value.
                    break;
                }
                case I32_STORE:
                case I64_STORE:
                case F32_STORE:
                case F64_STORE:
                case I32_STORE_8:
                case I32_STORE_16:
                case I64_STORE_8:
                case I64_STORE_16:
                case I64_STORE_32: {
                    readUnsignedInt32(bytesConsumed);  // align
                    // We don't store the `align` literal, as our implementation does not make use of it,
                    // but we need to store it's byte length, so that we can skip it during execution.
                    state.useByteConstant(bytesConsumed[0]);
                    int offset = readUnsignedInt32(bytesConsumed);  // offset
                    state.saveNumericLiteral(offset);
                    state.useByteConstant(bytesConsumed[0]);
                    Assert.assertLarger(state.stackSize(), 1, String.format("store instruction 0x%02X requires at least two elements in the stack", opcode));
                    state.pop();  // Value to store.
                    state.pop();  // Base address.
                    break;
                }
                case MEMORY_SIZE: {
                    read1();  // 0x00
                    break;
                }
                case MEMORY_GROW: {
                    read1();  // 0x00
                    break;
                }
                case I32_CONST: {
                    int value = readSignedInt32(bytesConsumed);
                    state.saveNumericLiteral(value);
                    state.useByteConstant(bytesConsumed[0]);
                    state.push();
                    break;
                }
                case I64_CONST: {
                    long value = readSignedInt64(bytesConsumed);
                    state.saveNumericLiteral(value);
                    state.useByteConstant(bytesConsumed[0]);
                    state.push();
                    break;
                }
                case F32_CONST: {
                    int value = readFloatAsInt32();
                    state.saveNumericLiteral(value);
                    state.push();
                    break;
                }
                case F64_CONST: {
                    long value = readFloatAsInt64();
                    state.saveNumericLiteral(value);
                    state.push();
                    break;
                }
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
                case F32_ABS:
                case F32_NEG:
                case F32_CEIL:
                case F32_FLOOR:
                case F32_TRUNC:
                case F32_NEAREST:
                case F32_SQRT:
                    state.pop();
                    state.push();
                    break;
                case F32_ADD:
                case F32_SUB:
                case F32_MUL:
                case F32_DIV:
                case F32_MIN:
                case F32_MAX:
                case F32_COPYSIGN:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case F64_ABS:
                case F64_NEG:
                case F64_CEIL:
                case F64_FLOOR:
                case F64_TRUNC:
                case F64_NEAREST:
                case F64_SQRT:
                    state.pop();
                    state.push();
                    break;
                case F64_ADD:
                case F64_SUB:
                case F64_MUL:
                case F64_DIV:
                case F64_MIN:
                case F64_MAX:
                case F64_COPYSIGN:
                    state.pop();
                    state.pop();
                    state.push();
                    break;
                case I32_WRAP_I64:
                case I32_TRUNC_F32_S:
                case I32_TRUNC_F32_U:
                case I32_TRUNC_F64_S:
                case I32_TRUNC_F64_U:
                case I64_EXTEND_I32_S:
                case I64_EXTEND_I32_U:
                case I64_TRUNC_F32_S:
                case I64_TRUNC_F32_U:
                case I64_TRUNC_F64_S:
                case I64_TRUNC_F64_U:
                case F32_CONVERT_I32_S:
                case F32_CONVERT_I32_U:
                case F32_CONVERT_I64_S:
                case F32_CONVERT_I64_U:
                case F32_DEMOTE_F64:
                case F64_CONVERT_I32_S:
                case F64_CONVERT_I32_U:
                case F64_CONVERT_I64_S:
                case F64_CONVERT_I64_U:
                case F64_PROMOTE_F32:
                case I32_REINTERPRET_F32:
                case I64_REINTERPRET_F64:
                case F32_REINTERPRET_I32:
                case F64_REINTERPRET_I64:
                    state.pop();
                    state.push();
                    break;
                default:
                    Assert.fail(Assert.format("Unknown opcode: 0x%02x", opcode));
                    break;
            }
        } while (opcode != END && opcode != ELSE);
        currentBlock.nestedControlTable = nestedControlTable.toArray(new WasmNode[nestedControlTable.size()]);
        currentBlock.callNodeTable = callNodes.toArray(new Node[callNodes.size()]);
        currentBlock.setByteLength(offset() - startOffset);
        currentBlock.setByteConstantLength(state.byteConstantOffset() - startByteConstantOffset);
        currentBlock.setIntConstantLength(state.intConstantOffset() - startIntConstantOffset);
        currentBlock.setNumericLiteralLength(state.numericLiteralOffset() - startNumericLiteralOffset);
        currentBlock.setBranchTableLength(state.branchTableOffset() - startBranchTableOffset);
        checkValidStateOnBlockExit(returnTypeId, state, startStackSize);
        return currentBlock;
    }

    private WasmLoopNode readLoop(WasmCodeEntry codeEntry, ExecutionState state, byte returnTypeId) {
       WasmBlockNode loopBlock = readBlock(codeEntry, state, returnTypeId);
       return new WasmLoopNode(loopBlock);
    }

    private WasmIfNode readIf(WasmCodeEntry codeEntry, ExecutionState state) {
        byte blockTypeId = readBlockType();
        int initialStackPointer = state.stackSize();
        int initialByteConstantOffset = state.byteConstantOffset();
        int initialNumericLiteralOffset = state.numericLiteralOffset();

        // Pop the condition value from the stack.
        state.pop();

        // Read true branch.
        int startOffset = offset();
        WasmBlockNode trueBranchBlock = readBlock(codeEntry, state, blockTypeId);

        // Read false branch, if it exists.
        WasmNode falseBranch;
        if (peek1(-1) == ELSE) {
            // If the if instruction has a true and a false branch, and it has non-void type, then each one of the two
            // readBlock above and below would push once, hence we need to pop once to compensate for the extra push.
            if (blockTypeId != ValueTypes.VOID_TYPE) {
                state.pop();
            }

            falseBranch = readBlock(codeEntry, state, blockTypeId);

            if (blockTypeId != ValueTypes.VOID_TYPE) {
                // TODO: Hack to correctly set the stack pointer for abstract interpretation.
                // If a block has branch instructions that target "shallower" blocks which return no value,
                // then it can leave no values in the stack, which is invalid for our abstract interpretation.
                // Correct the stack pointer to the value it would have in case there were no branch instructions.
                state.setStackPointer(initialStackPointer);
            }
        } else {
            if (blockTypeId != ValueTypes.VOID_TYPE) {
                Assert.fail("An if statement without an else branch block cannot return values.");
            }
            falseBranch = new WasmEmptyNode(wasmModule, codeEntry, 0);
        }

        return new WasmIfNode(wasmModule, codeEntry, trueBranchBlock, falseBranch, offset() - startOffset, blockTypeId, initialStackPointer,
                state.byteConstantOffset() - initialByteConstantOffset, state.numericLiteralOffset() - initialNumericLiteralOffset);
    }

    private void readElementSection() {
        int numElements = readVectorLength();
        for (int i = 0; i != numElements; ++i) {
            int tableIndex = readUnsignedInt32();
            // At the moment, WebAssembly only supports one table instance, thus the only valid table index is 0.
            Assert.assertEquals(tableIndex, 0, "Invalid table index");
            int offset = 0;
            byte instruction;

            // Read the offset expression.
            do {
                instruction = read1();
                // Table offset expression must be a constant expression with result type i32.
                // https://webassembly.github.io/spec/core/syntax/modules.html#element-segments
                // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions

                switch (instruction) {
                    case I32_CONST:
                        offset = readSignedInt32();
                        break;
                    case GLOBAL_GET:
                        // The global.get instructions in constant expressions are only allowed to refer to
                        // imported globals, which are not yet supported in our implementation.
                        throw new NotImplementedException();
                    case END:
                        break;
                    default:
                        Assert.fail(String.format("Invalid instruction for table offset expression: 0x%02X", instruction));
                }
            } while (instruction != END);

            // Read the contents.
            int contentLength = readUnsignedInt32();
            int[] contents = new int[contentLength];
            for (int funcIdx = 0; funcIdx != contentLength; ++funcIdx) {
                contents[i] = readFunctionIndex();
            }
            wasmModule.table().initializeContents(offset, contents);
        }
    }

    private void readStartSection() {
        int startFunctionIndex = readFunctionIndex();
        wasmModule.symbolTable().setStartFunction(startFunctionIndex);
    }

    private void readExportSection() {
        int numExports = readVectorLength();
        for (int i = 0; i != numExports; ++i) {
            String exportName = readName();
            byte exportType = readExportType();
            switch (exportType) {
                case ExportIdentifier.FUNCTION: {
                    int functionIndex = readFunctionIndex();
                    wasmModule.symbolTable().markFunctionAsExported(exportName, functionIndex);
                    break;
                }
                case ExportIdentifier.TABLE: {
                    int tableIndex = readTableIndex();
                    // TODO: Store the export information somewhere (e.g. in the symbol table).
                    break;
                }
                case ExportIdentifier.MEMORY: {
                    int memoryIndex = readMemoryIndex();
                    // TODO: Store the export information somewhere (e.g. in the symbol table).
                    break;
                }
                case ExportIdentifier.GLOBAL: {
                    int globalIndex = readGlobalIndex();
                    // TODO: Store the export information somewhere (e.g. in the symbol table).
                    break;
                }
                default: {
                    Assert.fail(String.format("Invalid export type identifier: 0x%02X", exportType));
                }
            }
        }
    }

    private void readGlobalSection() {
        int numGlobals = readVectorLength();
        wasmModule.globals().initialize(numGlobals);
        for (int globalIndex = 0; globalIndex != numGlobals; globalIndex++) {
            byte type = readValueType();
            byte mut = read1();  // 0x00 means const, 0x01 means var
            long value = 0;
            byte instruction;
            do {
                instruction = read1();
                // Global initialization expressions must be constant expressions:
                // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions
                switch (instruction) {
                    case I32_CONST:
                        value = readSignedInt32();
                        break;
                    case I64_CONST:
                        value = readSignedInt64();
                        break;
                    case F32_CONST:
                        value = readFloatAsInt32();
                        break;
                    case F64_CONST:
                        value = readFloatAsInt64();
                        break;
                    case GLOBAL_GET:
                        // The global.get instructions in constant expressions are only allowed to refer to
                        // imported globals, which are not yet supported in our implementation.
                        throw new NotImplementedException();
                    case END:
                        break;
                    default:
                        Assert.fail(String.format("Invalid instruction for global initialization: 0x%02X", instruction));
                        break;
                }
            } while (instruction != END);
            wasmModule.globals().register(globalIndex, value, type, mut != GlobalModifier.CONSTANT);
        }
    }

    private void readDataSection() {
        WasmMemory memory = WasmLanguage.getCurrentContext().memory();
        int numDataSections = readVectorLength();
        for (int i = 0; i != numDataSections; ++i) {
            int memIndex = readUnsignedInt32();
            // At the moment, WebAssembly only supports one memory instance, thus the only valid memory index is 0.
            Assert.assertEquals(memIndex, 0, "Invalid memory index");
            long offset = 0;
            byte instruction;
            do {
                instruction = read1();
                // Data offset expression must be a constant expression with result type i32.
                // https://webassembly.github.io/spec/core/syntax/modules.html#data-segments
                // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions

                switch (instruction) {
                    case I32_CONST:
                        offset = readSignedInt32();
                        break;
                    case GLOBAL_GET:
                        // The global.get instructions in constant expressions are only allowed to refer to
                        // imported globals, which are not yet supported in our implementation.
                        throw new NotImplementedException();
                    case END:
                        break;
                    default:
                        Assert.fail(String.format("Invalid instruction for data offset expression: 0x%02X", instruction));
                }
            } while (instruction != END);
            int byteLength = readVectorLength();

            long baseAddress = offset;
            memory.validateAddress(baseAddress, byteLength);

            for (int writeOffset = 0; writeOffset != byteLength; ++writeOffset) {
                byte b = read1();
                memory.store_i32_8(baseAddress + writeOffset, b);
            }
        }
    }

    private void readFunctionType() {
        int paramsLength = readVectorLength();
        int resultLength = peekUnsignedInt32(paramsLength);
        resultLength = (resultLength == 0x40) ? 0 : resultLength;
        int idx = wasmModule.symbolTable().allocateFunctionType(paramsLength, resultLength);
        readParameterList(idx, paramsLength);
        readResultList(idx);
    }

    private void readParameterList(int funcTypeIdx, int numParams) {
        for (int paramIdx = 0; paramIdx != numParams; ++paramIdx) {
            byte type = readValueType();
            wasmModule.symbolTable().registerFunctionTypeParameter(funcTypeIdx, paramIdx, type);
        }
    }

    // Specification seems ambiguous: https://webassembly.github.io/spec/core/binary/types.html#result-types
    // According to the spec, the result type can only be 0x40 (void) or 0xtt, where tt is a value type.
    // However, the Wasm binary compiler produces binaries with either 0x00 or 0x01 0xtt. Therefore, we support both.
    private void readResultList(int funcTypeIdx) {
        byte b = read1();
        switch (b) {
            case ValueTypes.VOID_TYPE:  // special byte indicating empty return type (same as above)
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

    private boolean isEOF() {
        return offset == data.length;
    }

    private int readVectorLength() {
        return readUnsignedInt32();
    }

    private int readFunctionIndex() {
        return readUnsignedInt32();
    }

    private int readTypeIndex() {
        return readUnsignedInt32();
    }

    private int readTypeIndex(byte[] bytesConsumed) {
        return readUnsignedInt32(bytesConsumed);
    }

    private int readFunctionIndex(byte[] bytesConsumed) {
        return readUnsignedInt32(bytesConsumed);
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

    public int readLocalIndex() {
        return readUnsignedInt32();
    }

    private int readLocalIndex(byte[] bytesConsumed) {
        return readUnsignedInt32(bytesConsumed);
    }

    public int readLabelIndex() {
        return readUnsignedInt32();
    }

    private int readLabelIndex(byte[] bytesConsumed) {
        return readUnsignedInt32(bytesConsumed);
    }

    private byte readExportType() {
        return read1();
    }

    private byte readImportType() {
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
}
