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

import static com.oracle.truffle.wasm.binary.constants.GlobalResolution.DECLARED;
import static com.oracle.truffle.wasm.binary.constants.GlobalResolution.UNRESOLVED_GET;
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
import static com.oracle.truffle.wasm.binary.constants.Section.CODE;
import static com.oracle.truffle.wasm.binary.constants.Section.CUSTOM;
import static com.oracle.truffle.wasm.binary.constants.Section.DATA;
import static com.oracle.truffle.wasm.binary.constants.Section.ELEMENT;
import static com.oracle.truffle.wasm.binary.constants.Section.EXPORT;
import static com.oracle.truffle.wasm.binary.constants.Section.FUNCTION;
import static com.oracle.truffle.wasm.binary.constants.Section.GLOBAL;
import static com.oracle.truffle.wasm.binary.constants.Section.IMPORT;
import static com.oracle.truffle.wasm.binary.constants.Section.MEMORY;
import static com.oracle.truffle.wasm.binary.constants.Section.START;
import static com.oracle.truffle.wasm.binary.constants.Section.TABLE;
import static com.oracle.truffle.wasm.binary.constants.Section.TYPE;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.wasm.binary.constants.CallIndirect;
import com.oracle.truffle.wasm.binary.constants.ExportIdentifier;
import com.oracle.truffle.wasm.binary.constants.GlobalModifier;
import com.oracle.truffle.wasm.binary.constants.GlobalResolution;
import com.oracle.truffle.wasm.binary.constants.ImportIdentifier;
import com.oracle.truffle.wasm.binary.constants.LimitsPrefix;
import com.oracle.truffle.wasm.binary.exception.WasmException;
import com.oracle.truffle.wasm.binary.exception.WasmLinkerException;
import com.oracle.truffle.wasm.binary.memory.WasmMemory;
import com.oracle.truffle.wasm.collection.ByteArrayList;

/**
 * Simple recursive-descend parser for the binary WebAssembly format.
 */
public class BinaryReader extends BinaryStreamReader {

    private static final int MAGIC = 0x6d736100;
    private static final int VERSION = 0x00000001;

    private WasmLanguage language;
    private WasmModule module;
    private byte[] bytesConsumed;

    /**
     * Modules may import, as well as define their own functions.
     * Function IDs are shared among imported and defined functions.
     * This variable keeps track of the function indices, so that imported and parsed code
     * entries can be correctly associated to their respective functions and types.
     */
    // TODO: We should remove this to reduce complexity - codeEntry state should be sufficient
    //  to track the current largest function index.
    private int moduleFunctionIndex;

    BinaryReader(WasmLanguage language, WasmModule module, byte[] data) {
        super(data);
        this.language = language;
        this.module = module;
        this.bytesConsumed = new byte[1];
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
                case CUSTOM:
                    readCustomSection(size);
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
            Assert.assertIntEqual(offset - startOffset, size, String.format("Declared section (0x%02X) size is incorrect", sectionID));
        }
    }

    private void readCustomSection(int size) {
        // TODO: We skip the custom section for now, but we should see what we could typically pick up here.
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
        final WasmContext context = language.getContextReference().get();
        int numImports = readVectorLength();
        for (int i = 0; i != numImports; ++i) {
            String moduleName = readName();
            String memberName = readName();
            byte importType = readImportType();
            switch (importType) {
                case ImportIdentifier.FUNCTION: {
                    int typeIndex = readTypeIndex();
                    module.symbolTable().importFunction(moduleName, memberName, typeIndex);
                    moduleFunctionIndex++;
                    break;
                }
                case ImportIdentifier.TABLE: {
                    byte elemType = readElemType();
                    Assert.assertIntEqual(elemType, ReferenceTypes.FUNCREF, "Invalid element type for table import");
                    byte limitsPrefix = read1();
                    switch (limitsPrefix) {
                        case LimitsPrefix.NO_MAX: {
                            int initSize = readUnsignedInt32();  // initial size (in number of entries)
                            module.symbolTable().importTable(context, moduleName, memberName, initSize, -1);
                            break;
                        }
                        case LimitsPrefix.WITH_MAX: {
                            int initSize = readUnsignedInt32();  // initial size (in number of entries)
                            int maxSize = readUnsignedInt32();  // max size (in number of entries)
                            module.symbolTable().importTable(context, moduleName, memberName, initSize, maxSize);
                            break;
                        }
                        default:
                            Assert.fail(String.format("Invalid limits prefix for imported table (expected 0x00 or 0x01, got 0x%02X", limitsPrefix));
                    }
                    break;
                }
                case ImportIdentifier.MEMORY: {
                    byte limitsPrefix = read1();
                    switch (limitsPrefix) {
                        case LimitsPrefix.NO_MAX: {
                            // Read initial size (in number of entries).
                            int initSize = readUnsignedInt32();
                            int maxSize = -1;
                            module.symbolTable().importMemory(context, moduleName, memberName, initSize, maxSize);
                            break;
                        }
                        case LimitsPrefix.WITH_MAX: {
                            // Read initial size (in number of entries).
                            int initSize = readUnsignedInt32();
                            // Read max size (in number of entries).
                            int maxSize = readUnsignedInt32();
                            module.symbolTable().importMemory(context, moduleName, memberName, initSize, maxSize);
                            break;
                        }
                        default:
                            Assert.fail(String.format("Invalid limits prefix for imported memory (expected 0x00 or 0x01, got 0x%02X", limitsPrefix));
                    }
                    break;
                }
                case ImportIdentifier.GLOBAL: {
                    byte type = readValueType();
                    // See GlobalModifier.
                    byte mutability = read1();
                    int index = module.symbolTable().maxGlobalIndex() + 1;
                    context.linker().importGlobal(module, index, moduleName, memberName, type, mutability);
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
        Assert.assertIntLessOrEqual(module.symbolTable().tableCount() + numTables, 1, "Can import or declare at most one table per module.");
        // Since in the current version of WebAssembly supports at most one table instance per module.
        // this loop should be executed at most once.
        for (byte tableIndex = 0; tableIndex != numTables; ++tableIndex) {
            byte elemType = readElemType();
            Assert.assertIntEqual(elemType, ReferenceTypes.FUNCREF, "Invalid element type for table");
            byte limitsPrefix = readLimitsPrefix();
            switch (limitsPrefix) {
                case LimitsPrefix.NO_MAX: {
                    int initSize = readUnsignedInt32();  // initial size (in number of entries)
                    module.symbolTable().allocateTable(language.getContextReference().get(), initSize, -1);
                    break;
                }
                case LimitsPrefix.WITH_MAX: {
                    int initSize = readUnsignedInt32();  // initial size (in number of entries)
                    int maxSize = readUnsignedInt32();  // max size (in number of entries)
                    Assert.assertIntLessOrEqual(initSize, maxSize, "Initial table size must be smaller or equal than maximum size");
                    module.symbolTable().allocateTable(language.getContextReference().get(), initSize, maxSize);
                    break;
                }
                default:
                    Assert.fail(String.format("Invalid limits prefix for table (expected 0x00 or 0x01, got 0x%02X", limitsPrefix));
            }
        }
    }

    private void readMemorySection() {
        int numMemories = readVectorLength();
        Assert.assertIntLessOrEqual(module.symbolTable().tableCount() + numMemories, 1, "Can import or declare at most one memory per module.");
        // Since in the current version of WebAssembly supports at most one table instance per module.
        // this loop should be executed at most once.
        for (int i = 0; i != numMemories; ++i) {
            byte limitsPrefix = readLimitsPrefix();
            switch (limitsPrefix) {
                case LimitsPrefix.NO_MAX: {
                    // Read initial size (in Wasm pages).
                    int initSize = readUnsignedInt32();
                    int maxSize = -1;
                    module.symbolTable().allocateMemory(language.getContextReference().get(), initSize, maxSize);
                    break;
                }
                case LimitsPrefix.WITH_MAX: {
                    // Read initial size (in Wasm pages).
                    int initSize = readUnsignedInt32();
                    // Read max size (in Wasm pages).
                    int maxSize = readUnsignedInt32();
                    module.symbolTable().allocateMemory(language.getContextReference().get(), initSize, maxSize);
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
            Assert.assertIntEqual(offset - startOffset, codeEntrySize, String.format("Code entry %d size is incorrect", entry));
        }
        moduleFunctionIndex += numCodeEntries;
    }

    private WasmRootNode createCodeEntry(int funcIndex) {
        WasmCodeEntry codeEntry = new WasmCodeEntry(funcIndex, data);
        module.symbolTable().function(funcIndex).setCodeEntry(codeEntry);

        /*
         * Create the root node and create and set the call target for the body.
         * This needs to be done before reading the body block, because we need to be able to
         * create direct call nodes {@see TruffleRuntime#createDirectCallNode} during parsing.
         */
        WasmRootNode rootNode = new WasmRootNode(language, codeEntry);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        module.symbolTable().function(funcIndex).setCallTarget(callTarget);

        return rootNode;
    }

    private void readCodeEntry(int funcIndex, WasmRootNode rootNode) {
        /* Initialise the code entry local variables (which contain the parameters and the locals). */
        initCodeEntryLocals(funcIndex);

        /* Read (parse) and abstractly interpret the code entry */
        byte returnTypeId = module.symbolTable().function(funcIndex).returnType();
        ExecutionState state = new ExecutionState();
        WasmBlockNode bodyBlock = readBlockBody(rootNode.codeEntry(), state, returnTypeId, returnTypeId);
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
        WasmCodeEntry codeEntry = module.symbolTable().function(funcIndex).codeEntry();
        int typeIndex = module.symbolTable().function(funcIndex).typeIndex();
        ByteArrayList argumentTypes = module.symbolTable().getFunctionTypeArgumentTypes(typeIndex);
        ByteArrayList localTypes = readCodeEntryLocals();
        byte[] allLocalTypes = ByteArrayList.concat(argumentTypes, localTypes);
        codeEntry.setLocalTypes(allLocalTypes);
    }

    private void checkValidStateOnBlockExit(byte returnTypeId, ExecutionState state, int initialStackSize) {
        if (returnTypeId == 0x40) {
            Assert.assertIntEqual(state.stackSize(), initialStackSize, "Void function left values in the stack");
        } else {
            Assert.assertIntEqual(state.stackSize(), initialStackSize + 1, "Function left more than 1 values left in stack");
        }
    }

    private WasmBlockNode readBlock(WasmCodeEntry codeEntry, ExecutionState state) {
        byte blockTypeId = readBlockType();
        return readBlockBody(codeEntry, state, blockTypeId, blockTypeId);
    }

    private WasmLoopNode readLoop(WasmCodeEntry codeEntry, ExecutionState state) {
        byte blockTypeId = readBlockType();
        return readLoop(codeEntry, state, blockTypeId);
    }

    private WasmBlockNode readBlockBody(WasmCodeEntry codeEntry, ExecutionState state, byte returnTypeId, byte continuationTypeId) {
        ArrayList<WasmNode> nestedControlTable = new ArrayList<>();
        ArrayList<Node> callNodes = new ArrayList<>();
        int startStackSize = state.stackSize();
        int startOffset = offset();
        int startByteConstantOffset = state.byteConstantOffset();
        int startIntConstantOffset = state.intConstantOffset();
        int startNumericLiteralOffset = state.numericLiteralOffset();
        int startBranchTableOffset = state.branchTableOffset();
        WasmBlockNode currentBlock = new WasmBlockNode(module, codeEntry, startOffset, returnTypeId, continuationTypeId, startStackSize,
                        startByteConstantOffset, startIntConstantOffset, startNumericLiteralOffset, startBranchTableOffset);

        // Push the type length of the current block's continuation.
        // Used when branching out of nested blocks (br and br_if instructions).
        state.pushContinuationReturnLength(currentBlock.continuationTypeLength());

        int opcode;
        do {
            opcode = read1() & 0xFF;
            switch (opcode) {
                case UNREACHABLE:
                    break;
                case NOP:
                    break;
                case BLOCK: {
                    // Save the current block's stack pointer, in case we branch out of
                    // the nested block (continuation stack pointer).
                    state.pushStackState(state.stackSize());
                    WasmBlockNode nestedBlock = readBlock(codeEntry, state);
                    nestedControlTable.add(nestedBlock);
                    state.popStackState();
                    break;
                }
                case LOOP: {
                    // Save the current block's stack pointer, in case we branch out of
                    // the nested block (continuation stack pointer).
                    state.pushStackState(state.stackSize());
                    WasmLoopNode loopBlock = readLoop(codeEntry, state);
                    nestedControlTable.add(loopBlock);
                    state.popStackState();
                    break;
                }
                case IF: {
                    // Save the current block's stack pointer, in case we branch out of
                    // the nested block (continuation stack pointer).
                    // For the if block, we save the stack size reduced by 1, because of the
                    // condition value that will be popped before executing the if statement.
                    state.pushStackState(state.stackSize() - 1);
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
                    state.useIntConstant(state.getContinuationReturnLength(unwindLevel));
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
                    state.useIntConstant(state.getContinuationReturnLength(unwindLevel));
                    break;
                }
                case BR_TABLE: {
                    int numLabels = readVectorLength();
                    // We need to save three tables here, to maintain the mapping target -> state mapping:
                    // - a table containing the branch targets for the instruction
                    // - a table containing the stack state for each corresponding branch target
                    // - the length of the return type
                    // We encode this in a single array.
                    int[] branchTable = new int[2 * (numLabels + 1) + 1];
                    int returnLength = -1;
                    // The BR_TABLE instruction behaves like a 'switch' statement.
                    // There is one extra label for the 'default' case.
                    for (int i = 0; i != numLabels + 1; ++i) {
                        final int targetLabel = readLabelIndex();
                        branchTable[1 + 2 * i + 0] = targetLabel;
                        branchTable[1 + 2 * i + 1] = state.getStackState(targetLabel);
                        final int blockReturnLength = state.getContinuationReturnLength(targetLabel);
                        if (returnLength == -1) {
                            returnLength = blockReturnLength;
                        } else {
                            Assert.assertIntEqual(returnLength, blockReturnLength,
                                            "All target blocks in br.table must have the same return type length.");
                        }
                    }
                    branchTable[0] = returnLength;
                    // TODO: Maybe move this pop up for consistency.
                    state.pop();
                    // The offset to the branch table.
                    state.saveBranchTable(branchTable);
                    break;
                }
                case RETURN: {
                    state.useIntConstant(state.getRootBlockReturnLength());
                    break;
                }
                case CALL: {
                    int functionIndex = readFunctionIndex(bytesConsumed);
                    state.saveNumericLiteral(functionIndex);
                    state.useByteConstant(bytesConsumed[0]);
                    WasmFunction function = module.symbolTable().function(functionIndex);
                    state.pop(function.numArguments());
                    state.push(function.returnTypeLength());

                    // We deliberately do not create the call node during parsing,
                    // because the call target is only created after the code entry is parsed.
                    // The code entry might not be yet parsed when we encounter this call.
                    //
                    // Furthermore, if the call target is imported from another module,
                    // then that other module might not have been parsed yet.
                    // Therefore, the call node must be created lazily,
                    // i.e. during the first execution.
                    // Therefore, we store the WasmFunction the corresponding index,
                    // which is replaced with the call node during the first execution.
                    callNodes.add(new WasmCallStubNode(function));

                    break;
                }
                case CALL_INDIRECT: {
                    int expectedFunctionTypeIndex = readTypeIndex(bytesConsumed);
                    state.saveNumericLiteral(expectedFunctionTypeIndex);
                    state.useByteConstant(bytesConsumed[0]);
                    int numArguments = module.symbolTable().functionTypeArgumentCount(expectedFunctionTypeIndex);
                    int returnLength = module.symbolTable().getFunctionTypeReturnTypeLength(expectedFunctionTypeIndex);

                    // Pop the function index to call, then pop the arguments and push the return value.
                    state.pop();
                    state.pop(numArguments);
                    state.push(returnLength);
                    callNodes.add(Truffle.getRuntime().createIndirectCallNode());
                    Assert.assertIntEqual(read1(), CallIndirect.ZERO_TABLE, "CALL_INDIRECT: Instruction must end with 0x00");
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
                    Assert.assertIntLessOrEqual(localIndex, codeEntry.numLocals(), "Invalid local index for local.get");
                    state.push();
                    break;
                }
                case LOCAL_SET: {
                    int localIndex = readLocalIndex(bytesConsumed);
                    state.saveNumericLiteral(localIndex);
                    state.useByteConstant(bytesConsumed[0]);
                    // Assert localIndex exists.
                    Assert.assertIntLessOrEqual(localIndex, codeEntry.numLocals(), "Invalid local index for local.set");
                    // Assert there is a value on the top of the stack.
                    Assert.assertIntGreater(state.stackSize(), 0, "local.set requires at least one element in the stack");
                    state.pop();
                    break;
                }
                case LOCAL_TEE: {
                    int localIndex = readLocalIndex(bytesConsumed);
                    state.saveNumericLiteral(localIndex);
                    state.useByteConstant(bytesConsumed[0]);
                    // Assert localIndex exists.
                    Assert.assertIntLessOrEqual(localIndex, codeEntry.numLocals(), "Invalid local index for local.tee");
                    // Assert there is a value on the top of the stack.
                    Assert.assertIntGreater(state.stackSize(), 0, "local.tee requires at least one element in the stack");
                    break;
                }
                case GLOBAL_GET: {
                    int index = readLocalIndex(bytesConsumed);
                    state.saveNumericLiteral(index);
                    state.useByteConstant(bytesConsumed[0]);
                    Assert.assertIntLessOrEqual(index, module.symbolTable().maxGlobalIndex(),
                                    "Invalid global index for global.get.");
                    state.push();
                    break;
                }
                case GLOBAL_SET: {
                    int index = readLocalIndex(bytesConsumed);
                    state.saveNumericLiteral(index);
                    state.useByteConstant(bytesConsumed[0]);
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
                    Assert.assertIntGreater(state.stackSize(), 0, String.format("load instruction 0x%02X requires at least one element in the stack", opcode));
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
                    Assert.assertIntGreater(state.stackSize(), 1, String.format("store instruction 0x%02X requires at least two elements in the stack", opcode));
                    state.pop();  // Value to store.
                    state.pop();  // Base address.
                    break;
                }
                case MEMORY_SIZE: {
                    // Skip the constant 0x00.
                    read1();
                    state.push();
                    break;
                }
                case MEMORY_GROW: {
                    // Skip the constant 0x00.
                    read1();
                    state.pop();
                    state.push();
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
        // TODO: Restore this check, when we fix the case where the block contains a return instruction.
        // checkValidStateOnBlockExit(returnTypeId, state, startStackSize);

        // Pop the current block return length in the return lengths stack.
        // Used when branching out of nested blocks (br and br_if instructions).
        state.popContinuationReturnLength();

        return currentBlock;
    }

    private WasmLoopNode readLoop(WasmCodeEntry codeEntry, ExecutionState state, byte returnTypeId) {
        int initialStackPointer = state.stackSize();
        WasmBlockNode loopBlock = readBlockBody(codeEntry, state, returnTypeId, ValueTypes.VOID_TYPE);

        // TODO: Hack to correctly set the stack pointer for abstract interpretation.
        // If a block has branch instructions that target "shallower" blocks which return no value,
        // then it can leave no values in the stack, which is invalid for our abstract interpretation.
        // Correct the stack pointer to the value it would have in case there were no branch instructions.
        state.setStackPointer(returnTypeId != ValueTypes.VOID_TYPE ? initialStackPointer + 1 : initialStackPointer);

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
        WasmBlockNode trueBranchBlock = readBlockBody(codeEntry, state, blockTypeId, blockTypeId);

        // TODO: Hack to correctly set the stack pointer for abstract interpretation.
        // If a block has branch instructions that target "shallower" blocks which return no value,
        // then it can leave no values in the stack, which is invalid for our abstract interpretation.
        // Correct the stack pointer to the value it would have in case there were no branch instructions.
        state.setStackPointer(blockTypeId != ValueTypes.VOID_TYPE ? initialStackPointer : initialStackPointer - 1);

        // Read false branch, if it exists.
        WasmNode falseBranchBlock;
        if (peek1(-1) == ELSE) {
            // If the if instruction has a true and a false branch, and it has non-void type, then each one of the two
            // readBlockBody above and below would push once, hence we need to pop once to compensate for the extra push.
            if (blockTypeId != ValueTypes.VOID_TYPE) {
                state.pop();
            }

            falseBranchBlock = readBlockBody(codeEntry, state, blockTypeId, blockTypeId);

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
            falseBranchBlock = new WasmEmptyNode(module, codeEntry, 0);
        }

        return new WasmIfNode(module, codeEntry, trueBranchBlock, falseBranchBlock, offset() - startOffset, blockTypeId, initialStackPointer,
                state.byteConstantOffset() - initialByteConstantOffset, state.numericLiteralOffset() - initialNumericLiteralOffset);
    }

    private void readElementSection() {
        final WasmContext context = language.getContextReference().get();
        int numElements = readVectorLength();
        for (int i = 0; i != numElements; ++i) {
            int tableIndex = readUnsignedInt32();
            // At the moment, WebAssembly only supports one table instance, thus the only valid table index is 0.
            Assert.assertIntEqual(tableIndex, 0, "Invalid table index");

            // Read the offset expression.
            byte instruction = read1();
            // Table offset expression must be a constant expression with result type i32.
            // https://webassembly.github.io/spec/core/syntax/modules.html#element-segments
            // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions
            switch (instruction) {
                case I32_CONST: {
                    int offset = readSignedInt32();
                    readEnd();
                    // Read the contents.
                    int[] contents = readElemContents();
                    module.symbolTable().initializeTableWithFunctions(context, offset, contents);
                    break;
                }
                case GLOBAL_GET: {
                    int index = readGlobalIndex();
                    readEnd();
                    int[] contents = readElemContents();
                    final Linker linker = context.linker();
                    linker.tryInitializeElements(context, module, index, contents);
                    break;
                }
                default: {
                    Assert.fail(String.format("Invalid instruction for table offset expression: 0x%02X", instruction));
                }
            }
        }
    }

    private void readEnd() {
        byte instruction = read1();
        Assert.assertByteEqual(instruction, (byte) END, "Initialization expression must end with an END");
    }

    private int[] readElemContents() {
        int contentLength = readUnsignedInt32();
        int[] contents = new int[contentLength];
        for (int funcIdx = 0; funcIdx != contentLength; ++funcIdx) {
            contents[funcIdx] = readFunctionIndex();
        }
        return contents;
    }

    private void readStartSection() {
        int startFunctionIndex = readFunctionIndex();
        module.symbolTable().setStartFunction(startFunctionIndex);
    }

    private void readExportSection() {
        int numExports = readVectorLength();
        for (int i = 0; i != numExports; ++i) {
            String exportName = readName();
            byte exportType = readExportType();
            switch (exportType) {
                case ExportIdentifier.FUNCTION: {
                    int functionIndex = readFunctionIndex();
                    module.symbolTable().exportFunction(exportName, functionIndex);
                    break;
                }
                case ExportIdentifier.TABLE: {
                    int tableIndex = readTableIndex();
                    Assert.assertTrue(module.symbolTable().tableExists(), "No table was imported or declared, so cannot export a table");
                    Assert.assertIntEqual(tableIndex, 0, "Cannot export table index different than zero (only one table per module allowed)");
                    module.symbolTable().exportTable(exportName);
                    break;
                }
                case ExportIdentifier.MEMORY: {
                    int memoryIndex = readMemoryIndex();
                    // TODO: Store the export information somewhere (e.g. in the symbol table).
                    break;
                }
                case ExportIdentifier.GLOBAL: {
                    int index = readGlobalIndex();
                    module.symbolTable().exportGlobal(exportName, index);
                    break;
                }
                default: {
                    Assert.fail(String.format("Invalid export type identifier: 0x%02X", exportType));
                }
            }
        }
    }

    private void readGlobalSection() {
        final Globals globals = language.getContextReference().get().globals();
        int numGlobals = readVectorLength();
        int startingGlobalIndex = module.symbolTable().maxGlobalIndex() + 1;
        for (int i = startingGlobalIndex; i != startingGlobalIndex + numGlobals; i++) {
            byte type = readValueType();
            // 0x00 means const, 0x01 means var
            byte mutability = read1();
            long value = 0;
            GlobalResolution resolution;
            int existingIndex = -1;
            byte instruction = read1();
            // Global initialization expressions must be constant expressions:
            // https://webassembly.github.io/spec/core/valid/instructions.html#constant-expressions
            switch (instruction) {
                case I32_CONST:
                    value = readSignedInt32();
                    resolution = DECLARED;
                    break;
                case I64_CONST:
                    value = readSignedInt64();
                    resolution = DECLARED;
                    break;
                case F32_CONST:
                    value = readFloatAsInt32();
                    resolution = DECLARED;
                    break;
                case F64_CONST:
                    value = readFloatAsInt64();
                    resolution = DECLARED;
                    break;
                case GLOBAL_GET:
                    existingIndex = readGlobalIndex();
                    final GlobalResolution existingResolution = module.symbolTable().globalResolution(existingIndex);
                    Assert.assertTrue(existingResolution.isImported(),
                                    String.format("Global %d is not initialized with an imported global.", i));
                    if (existingResolution.isResolved()) {
                        final byte existingType = module.symbolTable().globalValueType(existingIndex);
                        Assert.assertByteEqual(type, existingType,
                                        String.format("The types of the globals must be consistent: 0x%02X vs 0x%02X", type, existingType));
                        final int existingAddress = module.symbolTable().globalAddress(existingIndex);
                        value = globals.loadAsLong(existingAddress);
                        resolution = DECLARED;
                    } else {
                        // The imported module with the referenced global was not yet parsed and resolved,
                        // so it is not possible to initialize the current global.
                        // The resolution state is set accordingly, until it gets resolved later.
                        resolution = UNRESOLVED_GET;
                    }
                    break;
                default:
                    throw Assert.fail(String.format("Invalid instruction for global initialization: 0x%02X", instruction));
            }
            instruction = read1();
            Assert.assertByteEqual(instruction, (byte) END, "Global initialization must end with END");
            final int address = module.symbolTable().declareGlobal(language.getContextReference().get(), i, type, mutability, resolution);
            if (resolution.isResolved()) {
                globals.storeLong(address, value);
            } else {
                module.symbolTable().trackUnresolvedGlobal(i, existingIndex);
            }
        }
    }

    private void readDataSection() {
        WasmMemory memory = module.symbolTable().memory();
        Assert.assertNotNull(memory, "No memory declared or imported in the module.");
        int numDataSections = readVectorLength();
        for (int i = 0; i != numDataSections; ++i) {
            int memIndex = readUnsignedInt32();
            // At the moment, WebAssembly only supports one memory instance, thus the only valid memory index is 0.
            Assert.assertIntEqual(memIndex, 0, "Invalid memory index, only the memory index 0 is currently supported");
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
                        int index = readGlobalIndex();
                        // TODO: Implement GLOBAL_GET case for data sections (and add tests).
                        throw new WasmException("GLOBAL_GET in data section not implemented.");
                        // offset = module.globals().getAsInt(index);
                        // break;
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

    private int readLocalIndex() {
        return readUnsignedInt32();
    }

    private int readLocalIndex(byte[] bytesConsumed) {
        return readUnsignedInt32(bytesConsumed);
    }

    private int readLabelIndex() {
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

    private byte readElemType() {
        return read1();
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
        if (tryJumpToSection(IMPORT)) {
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
                        byte limitsPrefix = read1();
                        switch (limitsPrefix) {
                            case LimitsPrefix.NO_MAX: {
                                readUnsignedInt32();
                                break;
                            }
                            case LimitsPrefix.WITH_MAX: {
                                readUnsignedInt32();
                                readUnsignedInt32();
                                break;
                            }
                        }
                        break;
                    }
                    case ImportIdentifier.MEMORY: {
                        byte limitsPrefix = read1();
                        switch (limitsPrefix) {
                            case LimitsPrefix.NO_MAX: {
                                readUnsignedInt32();
                                break;
                            }
                            case LimitsPrefix.WITH_MAX: {
                                readUnsignedInt32();
                                readUnsignedInt32();
                                break;
                            }
                        }
                        break;
                    }
                    case ImportIdentifier.GLOBAL: {
                        readValueType();
                        byte mutability = read1();
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
        if (tryJumpToSection(GLOBAL)) {
            final Globals globals = language.getContextReference().get().globals();
            int numGlobals = readVectorLength();
            int startingGlobalIndex = globalIndex;
            for (; globalIndex != startingGlobalIndex + numGlobals; globalIndex++) {
                readValueType();
                // Read mutability;
                read1();
                byte instruction = read1();
                long value = 0;
                switch (instruction) {
                    case I32_CONST: {
                        value = readSignedInt32();
                        break;
                    }
                    case I64_CONST: {
                        value = readSignedInt64();
                        break;
                    }
                    case F32_CONST: {
                        value = readFloatAsInt32();
                        break;
                    }
                    case F64_CONST: {
                        value = readFloatAsInt64();
                        break;
                    }
                    case GLOBAL_GET: {
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
        if (tryJumpToSection(DATA)) {
            readDataSection();
        }
    }
}
