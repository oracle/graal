/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.parser.bytecode;

import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.wasm.Assert;
import org.graalvm.wasm.BinaryStreamParser;
import org.graalvm.wasm.GlobalRegistry;
import org.graalvm.wasm.Linker;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.constants.Bytecode;
import org.graalvm.wasm.constants.BytecodeBitEncoding;
import org.graalvm.wasm.constants.SegmentMode;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.memory.NativeDataInstanceUtil;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.parser.ir.CallNode;
import org.graalvm.wasm.parser.ir.CodeEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.graalvm.wasm.BinaryStreamParser.rawPeekI32;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekI64;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekU16;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekU8;

/**
 * Allows to parse the runtime bytecode and reset modules.
 */
public abstract class BytecodeParser {
    /**
     * Reset the state of the globals in a module that had already been parsed and linked.
     */
    public static void resetGlobalState(WasmContext context, WasmModule module, WasmInstance instance) {
        final GlobalRegistry globals = context.globals();
        for (int i = 0; i < module.numGlobals(); i++) {
            if (module.globalImported(i)) {
                continue;
            }
            if (module.globalInitialized(i)) {
                globals.store(module.globalValueType(i), instance.globalAddress(i), module.globalInitialValue(i));
            } else {
                Linker.initializeGlobal(context, instance, i, module.globalInitializerBytecode(i));
            }
        }
    }

    /**
     * Reset the state of the memory in a module that had already been parsed and linked.
     */
    public static void resetMemoryState(WasmContext context, WasmModule module, WasmInstance instance) {
        final boolean unsafeMemory = context.getContextOptions().useUnsafeMemory();
        final byte[] bytecode = module.bytecode();
        for (int i = 0; i < module.dataInstanceCount(); i++) {
            if (unsafeMemory) {
                // free all memory allocated for data instances
                instance.dropUnsafeDataInstance(i);
            }

            final int dataOffset = module.dataInstanceOffset(i);
            final int flags = bytecode[dataOffset];
            int effectiveOffset = dataOffset + 1;

            final int dataMode = flags & BytecodeBitEncoding.DATA_SEG_MODE_VALUE;
            final int dataLength;
            switch (flags & BytecodeBitEncoding.DATA_SEG_LENGTH_MASK) {
                case BytecodeBitEncoding.DATA_SEG_LENGTH_U8:
                    dataLength = rawPeekU8(bytecode, effectiveOffset);
                    effectiveOffset++;
                    break;
                case BytecodeBitEncoding.DATA_SEG_LENGTH_U16:
                    dataLength = rawPeekU16(bytecode, effectiveOffset);
                    effectiveOffset += 2;
                    break;
                case BytecodeBitEncoding.DATA_SEG_LENGTH_I32:
                    dataLength = rawPeekI32(bytecode, effectiveOffset);
                    effectiveOffset += 4;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            if (dataMode == SegmentMode.ACTIVE) {
                final long value;
                switch (flags & BytecodeBitEncoding.DATA_SEG_VALUE_MASK) {
                    case BytecodeBitEncoding.DATA_SEG_VALUE_UNDEFINED:
                        value = -1;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_VALUE_U8:
                        value = rawPeekU8(bytecode, effectiveOffset);
                        effectiveOffset++;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_VALUE_U16:
                        value = rawPeekU16(bytecode, effectiveOffset);
                        effectiveOffset += 2;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_VALUE_U32:
                        value = rawPeekI32(bytecode, effectiveOffset);
                        effectiveOffset += 4;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_VALUE_I64:
                        value = rawPeekI64(bytecode, effectiveOffset);
                        effectiveOffset += 8;
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
                final byte[] offsetBytecode;
                long offsetAddress;
                if ((flags & BytecodeBitEncoding.DATA_SEG_BYTECODE_OR_OFFSET_MASK) == BytecodeBitEncoding.DATA_SEG_BYTECODE) {
                    int offsetBytecodeLength = (int) value;
                    offsetBytecode = Arrays.copyOfRange(bytecode, effectiveOffset, effectiveOffset + offsetBytecodeLength);
                    effectiveOffset += offsetBytecodeLength;
                    offsetAddress = -1;
                } else {
                    offsetBytecode = null;
                    offsetAddress = value;
                }
                if (offsetBytecode != null) {
                    offsetAddress = ((Number) Linker.evalConstantExpression(context, instance, offsetBytecode)).longValue();
                }

                final int memoryIndex;
                if ((flags & BytecodeBitEncoding.DATA_SEG_HAS_MEMORY_INDEX_ZERO) != 0) {
                    memoryIndex = 0;
                } else {
                    final int memoryIndexEncoding = bytecode[effectiveOffset];
                    effectiveOffset++;
                    switch (memoryIndexEncoding & BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_MASK) {
                        case BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_U6:
                            memoryIndex = memoryIndexEncoding & BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_VALUE;
                            break;
                        case BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_U8:
                            memoryIndex = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                            effectiveOffset++;
                            break;
                        case BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_U16:
                            memoryIndex = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                            effectiveOffset += 2;
                            break;
                        case BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_I32:
                            memoryIndex = BinaryStreamParser.rawPeekI32(bytecode, effectiveOffset);
                            effectiveOffset += 4;
                            break;
                        default:
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                }

                // Reading of the data segment is called after linking, so initialize the memory
                // directly.
                final WasmMemory memory = instance.memory(memoryIndex);

                Assert.assertUnsignedLongLessOrEqual(offsetAddress, memory.byteSize(), Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);
                Assert.assertUnsignedLongLessOrEqual(offsetAddress + dataLength, memory.byteSize(), Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);
                memory.initialize(module.bytecode(), effectiveOffset, offsetAddress, dataLength);
            } else {
                if (unsafeMemory) {
                    final int length = switch (bytecode[effectiveOffset] & BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_MASK) {
                        case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_INLINE -> 0;
                        case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_U8 -> 1;
                        case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_U16 -> 2;
                        case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_I32 -> 4;
                        default -> throw CompilerDirectives.shouldNotReachHere();
                    };
                    final long instanceAddress = NativeDataInstanceUtil.allocateNativeInstance(bytecode,
                                    effectiveOffset + BytecodeBitEncoding.DATA_SEG_RUNTIME_HEADER_LENGTH + length + BytecodeBitEncoding.DATA_SEG_RUNTIME_UNSAFE_ADDRESS_LENGTH, dataLength);
                    BinaryStreamParser.writeI64(bytecode, effectiveOffset + BytecodeBitEncoding.DATA_SEG_RUNTIME_HEADER_LENGTH + length, instanceAddress);
                }
                instance.setDataInstance(i, effectiveOffset);
            }
        }
    }

    /**
     * Reset the state of the tables in a module that had already been parsed and linked.
     */
    public static void resetTableState(WasmContext context, WasmModule module, WasmInstance instance) {
        final byte[] bytecode = module.bytecode();
        for (int i = 0; i < module.elemInstanceCount(); i++) {
            final int elemOffset = module.elemInstanceOffset(i);
            final int flags = bytecode[elemOffset];
            final int typeAndMode = bytecode[elemOffset + 1];
            int effectiveOffset = elemOffset + 2;

            final int elemMode = typeAndMode & BytecodeBitEncoding.ELEM_SEG_MODE_VALUE;

            final int elemCount;
            switch (flags & BytecodeBitEncoding.ELEM_SEG_COUNT_MASK) {
                case BytecodeBitEncoding.ELEM_SEG_COUNT_U8:
                    elemCount = rawPeekU8(bytecode, effectiveOffset);
                    effectiveOffset++;
                    break;
                case BytecodeBitEncoding.ELEM_SEG_COUNT_U16:
                    elemCount = rawPeekU16(bytecode, effectiveOffset);
                    effectiveOffset += 2;
                    break;
                case BytecodeBitEncoding.ELEM_SEG_COUNT_I32:
                    elemCount = rawPeekI32(bytecode, effectiveOffset);
                    effectiveOffset += 4;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            final Linker linker = Objects.requireNonNull(context.linker());
            if (elemMode == SegmentMode.ACTIVE) {
                final int tableIndex;
                switch (flags & BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_MASK) {
                    case BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_ZERO:
                        tableIndex = 0;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_U8:
                        tableIndex = rawPeekU8(bytecode, effectiveOffset);
                        effectiveOffset++;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_U16:
                        tableIndex = rawPeekU16(bytecode, effectiveOffset);
                        effectiveOffset += 2;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_I32:
                        tableIndex = rawPeekI32(bytecode, effectiveOffset);
                        effectiveOffset += 4;
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
                final byte[] offsetBytecode;
                switch (flags & BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_MASK) {
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_UNDEFINED:
                        offsetBytecode = null;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_LENGTH_U8: {
                        int offsetBytecodeLength = rawPeekU8(bytecode, effectiveOffset);
                        effectiveOffset++;
                        offsetBytecode = Arrays.copyOfRange(bytecode, effectiveOffset, effectiveOffset + offsetBytecodeLength);
                        effectiveOffset += offsetBytecodeLength;
                        break;
                    }
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_LENGTH_U16: {
                        int offsetBytecodeLength = rawPeekU16(bytecode, effectiveOffset);
                        effectiveOffset += 2;
                        offsetBytecode = Arrays.copyOfRange(bytecode, effectiveOffset, effectiveOffset + offsetBytecodeLength);
                        effectiveOffset += offsetBytecodeLength;
                        break;
                    }
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_LENGTH_I32: {
                        int offsetBytecodeLength = rawPeekI32(bytecode, effectiveOffset);
                        effectiveOffset += 4;
                        offsetBytecode = Arrays.copyOfRange(bytecode, effectiveOffset, effectiveOffset + offsetBytecodeLength);
                        effectiveOffset += offsetBytecodeLength;
                        break;
                    }
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
                final int offsetAddress;
                switch (flags & BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_MASK) {
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_UNDEFINED:
                        offsetAddress = -1;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_U8:
                        offsetAddress = rawPeekU8(bytecode, effectiveOffset);
                        effectiveOffset++;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_U16:
                        offsetAddress = rawPeekU16(bytecode, effectiveOffset);
                        effectiveOffset += 2;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_I32:
                        offsetAddress = rawPeekI32(bytecode, effectiveOffset);
                        effectiveOffset += 4;
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
                linker.immediatelyResolveElemSegment(context, instance, tableIndex, offsetAddress, offsetBytecode, effectiveOffset, elemCount);
            } else if (elemMode == SegmentMode.PASSIVE) {
                linker.immediatelyResolvePassiveElementSegment(context, instance, i, effectiveOffset, elemCount);
            }
        }
    }

    /**
     * Rereads the code entries in a module that had already been parsed and linked.
     */
    public static void readCodeEntries(WasmModule module) {
        final byte[] bytecode = module.bytecode();
        CodeEntry[] codeEntries = new CodeEntry[module.codeEntryCount()];
        for (int i = 0; i < module.codeEntryCount(); i++) {
            codeEntries[i] = readCodeEntry(module, bytecode, i);
        }
        module.setCodeEntries(codeEntries);
    }

    /**
     * Rereads a code entry in a module that has already been parsed and linked.
     */
    public static CodeEntry readCodeEntry(WasmModule module, byte[] bytecode, int codeEntryIndex) {
        final int codeEntryOffset = module.codeEntryOffset(codeEntryIndex);
        final int flags = bytecode[codeEntryOffset];
        int effectiveOffset = codeEntryOffset + 1;

        final int functionIndex;
        switch (flags & BytecodeBitEncoding.CODE_ENTRY_FUNCTION_INDEX_MASK) {
            case BytecodeBitEncoding.CODE_ENTRY_FUNCTION_INDEX_ZERO:
                functionIndex = 0;
                break;
            case BytecodeBitEncoding.CODE_ENTRY_FUNCTION_INDEX_U8:
                functionIndex = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                effectiveOffset++;
                break;
            case BytecodeBitEncoding.CODE_ENTRY_FUNCTION_INDEX_U16:
                functionIndex = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                effectiveOffset += 2;
                break;
            case BytecodeBitEncoding.CODE_ENTRY_FUNCTION_INDEX_I32:
                functionIndex = BinaryStreamParser.rawPeekI32(bytecode, effectiveOffset);
                effectiveOffset += 4;
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        final int maxStackSize;
        switch (flags & BytecodeBitEncoding.CODE_ENTRY_MAX_STACK_SIZE_MASK) {
            case BytecodeBitEncoding.CODE_ENTRY_MAX_STACK_SIZE_ZERO:
                maxStackSize = 0;
                break;
            case BytecodeBitEncoding.CODE_ENTRY_MAX_STACK_SIZE_U8:
                maxStackSize = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                effectiveOffset++;
                break;
            case BytecodeBitEncoding.CODE_ENTRY_MAX_STACK_SIZE_U16:
                maxStackSize = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                effectiveOffset += 2;
                break;
            case BytecodeBitEncoding.CODE_ENTRY_MAX_STACK_SIZE_I32:
                maxStackSize = BinaryStreamParser.rawPeekI32(bytecode, effectiveOffset);
                effectiveOffset += 4;
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        final int length;
        switch (flags & BytecodeBitEncoding.CODE_ENTRY_LENGTH_MASK) {
            case BytecodeBitEncoding.CODE_ENTRY_LENGTH_U8:
                length = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                effectiveOffset++;
                break;
            case BytecodeBitEncoding.CODE_ENTRY_LENGTH_U16:
                length = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                effectiveOffset += 2;
                break;
            case BytecodeBitEncoding.CODE_ENTRY_LENGTH_I32:
                length = BinaryStreamParser.rawPeekI32(bytecode, effectiveOffset);
                effectiveOffset += 4;
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        final byte[] locals;
        if ((flags & BytecodeBitEncoding.CODE_ENTRY_LOCALS_FLAG) != 0) {
            ByteArrayList localsList = new ByteArrayList();
            for (; bytecode[effectiveOffset] != 0; effectiveOffset++) {
                localsList.add(bytecode[effectiveOffset]);
            }
            effectiveOffset++;
            locals = localsList.toArray();
        } else {
            locals = Bytecode.EMPTY_BYTES;
        }
        final byte[] results;
        if ((flags & BytecodeBitEncoding.CODE_ENTRY_RESULT_FLAG) != 0) {
            ByteArrayList resultsList = new ByteArrayList();
            for (; bytecode[effectiveOffset] != 0; effectiveOffset++) {
                resultsList.add(bytecode[effectiveOffset]);
            }
            results = resultsList.toArray();
        } else {
            results = Bytecode.EMPTY_BYTES;
        }
        List<CallNode> callNodes = readCallNodes(bytecode, codeEntryOffset - length, codeEntryOffset);
        return new CodeEntry(functionIndex, maxStackSize, locals, results, callNodes, codeEntryOffset - length, codeEntryOffset);
    }

    /**
     * Rereads the code section entries for all functions based on the bytecode of the module and
     * adds the resulting call nodes to the entries.
     */
    private static List<CallNode> readCallNodes(byte[] bytecode, int startOffset, int endOffset) {
        int offset = startOffset;
        ArrayList<CallNode> callNodes = new ArrayList<>();
        while (offset < endOffset) {
            int opcode = BinaryStreamParser.rawPeekU8(bytecode, offset);
            offset++;
            switch (opcode) {
                case Bytecode.CALL_U8: {
                    final int functionIndex = rawPeekU8(bytecode, offset + 1);
                    callNodes.add(new CallNode(functionIndex));
                    offset += 2;
                    break;
                }
                case Bytecode.CALL_I32: {
                    final int functionIndex = rawPeekI32(bytecode, offset + 4);
                    callNodes.add(new CallNode(functionIndex));
                    offset += 8;
                    break;
                }
                case Bytecode.CALL_INDIRECT_U8: {
                    callNodes.add(new CallNode());
                    offset += 5;
                    break;
                }
                case Bytecode.CALL_INDIRECT_I32: {
                    callNodes.add(new CallNode());
                    offset += 14;
                    break;
                }
                case Bytecode.UNREACHABLE:
                case Bytecode.NOP:
                case Bytecode.RETURN:
                case Bytecode.LOOP:
                case Bytecode.DROP:
                case Bytecode.DROP_OBJ:
                case Bytecode.SELECT:
                case Bytecode.SELECT_OBJ:
                case Bytecode.I32_EQZ:
                case Bytecode.I32_EQ:
                case Bytecode.I32_NE:
                case Bytecode.I32_LT_S:
                case Bytecode.I32_LT_U:
                case Bytecode.I32_GT_S:
                case Bytecode.I32_GT_U:
                case Bytecode.I32_LE_S:
                case Bytecode.I32_LE_U:
                case Bytecode.I32_GE_S:
                case Bytecode.I32_GE_U:
                case Bytecode.I64_EQZ:
                case Bytecode.I64_EQ:
                case Bytecode.I64_NE:
                case Bytecode.I64_LT_S:
                case Bytecode.I64_LT_U:
                case Bytecode.I64_GT_S:
                case Bytecode.I64_GT_U:
                case Bytecode.I64_LE_S:
                case Bytecode.I64_LE_U:
                case Bytecode.I64_GE_S:
                case Bytecode.I64_GE_U:
                case Bytecode.F32_EQ:
                case Bytecode.F32_NE:
                case Bytecode.F32_LT:
                case Bytecode.F32_GT:
                case Bytecode.F32_LE:
                case Bytecode.F32_GE:
                case Bytecode.F64_EQ:
                case Bytecode.F64_NE:
                case Bytecode.F64_LT:
                case Bytecode.F64_GT:
                case Bytecode.F64_LE:
                case Bytecode.F64_GE:
                case Bytecode.I32_CLZ:
                case Bytecode.I32_CTZ:
                case Bytecode.I32_POPCNT:
                case Bytecode.I32_ADD:
                case Bytecode.I32_SUB:
                case Bytecode.I32_MUL:
                case Bytecode.I32_DIV_S:
                case Bytecode.I32_DIV_U:
                case Bytecode.I32_REM_S:
                case Bytecode.I32_REM_U:
                case Bytecode.I32_AND:
                case Bytecode.I32_OR:
                case Bytecode.I32_XOR:
                case Bytecode.I32_SHL:
                case Bytecode.I32_SHR_S:
                case Bytecode.I32_SHR_U:
                case Bytecode.I32_ROTL:
                case Bytecode.I32_ROTR:
                case Bytecode.I64_CLZ:
                case Bytecode.I64_CTZ:
                case Bytecode.I64_POPCNT:
                case Bytecode.I64_ADD:
                case Bytecode.I64_SUB:
                case Bytecode.I64_MUL:
                case Bytecode.I64_DIV_S:
                case Bytecode.I64_DIV_U:
                case Bytecode.I64_REM_S:
                case Bytecode.I64_REM_U:
                case Bytecode.I64_AND:
                case Bytecode.I64_OR:
                case Bytecode.I64_XOR:
                case Bytecode.I64_SHL:
                case Bytecode.I64_SHR_S:
                case Bytecode.I64_SHR_U:
                case Bytecode.I64_ROTL:
                case Bytecode.I64_ROTR:
                case Bytecode.F32_ABS:
                case Bytecode.F32_NEG:
                case Bytecode.F32_CEIL:
                case Bytecode.F32_FLOOR:
                case Bytecode.F32_TRUNC:
                case Bytecode.F32_NEAREST:
                case Bytecode.F32_SQRT:
                case Bytecode.F32_ADD:
                case Bytecode.F32_SUB:
                case Bytecode.F32_MUL:
                case Bytecode.F32_DIV:
                case Bytecode.F32_MIN:
                case Bytecode.F32_MAX:
                case Bytecode.F32_COPYSIGN:
                case Bytecode.F64_ABS:
                case Bytecode.F64_NEG:
                case Bytecode.F64_CEIL:
                case Bytecode.F64_FLOOR:
                case Bytecode.F64_TRUNC:
                case Bytecode.F64_NEAREST:
                case Bytecode.F64_SQRT:
                case Bytecode.F64_ADD:
                case Bytecode.F64_SUB:
                case Bytecode.F64_MUL:
                case Bytecode.F64_DIV:
                case Bytecode.F64_MIN:
                case Bytecode.F64_MAX:
                case Bytecode.F64_COPYSIGN:
                case Bytecode.I32_WRAP_I64:
                case Bytecode.I32_TRUNC_F32_S:
                case Bytecode.I32_TRUNC_F32_U:
                case Bytecode.I32_TRUNC_F64_S:
                case Bytecode.I32_TRUNC_F64_U:
                case Bytecode.I64_EXTEND_I32_S:
                case Bytecode.I64_EXTEND_I32_U:
                case Bytecode.I64_TRUNC_F32_S:
                case Bytecode.I64_TRUNC_F32_U:
                case Bytecode.I64_TRUNC_F64_S:
                case Bytecode.I64_TRUNC_F64_U:
                case Bytecode.F32_CONVERT_I32_S:
                case Bytecode.F32_CONVERT_I32_U:
                case Bytecode.F32_CONVERT_I64_S:
                case Bytecode.F32_CONVERT_I64_U:
                case Bytecode.F32_DEMOTE_F64:
                case Bytecode.F64_CONVERT_I32_S:
                case Bytecode.F64_CONVERT_I32_U:
                case Bytecode.F64_CONVERT_I64_S:
                case Bytecode.F64_CONVERT_I64_U:
                case Bytecode.F64_PROMOTE_F32:
                case Bytecode.I32_REINTERPRET_F32:
                case Bytecode.I64_REINTERPRET_F64:
                case Bytecode.F32_REINTERPRET_I32:
                case Bytecode.F64_REINTERPRET_I64:
                case Bytecode.I32_EXTEND8_S:
                case Bytecode.I32_EXTEND16_S:
                case Bytecode.I64_EXTEND8_S:
                case Bytecode.I64_EXTEND16_S:
                case Bytecode.I64_EXTEND32_S:
                case Bytecode.REF_NULL:
                case Bytecode.REF_IS_NULL: {
                    break;
                }
                case Bytecode.SKIP_LABEL_U8:
                case Bytecode.SKIP_LABEL_U16:
                case Bytecode.SKIP_LABEL_I32:
                    offset += opcode;
                    break;
                case Bytecode.LABEL_U8:
                case Bytecode.BR_U8:
                case Bytecode.LOCAL_GET_U8:
                case Bytecode.LOCAL_GET_OBJ_U8:
                case Bytecode.LOCAL_SET_U8:
                case Bytecode.LOCAL_SET_OBJ_U8:
                case Bytecode.LOCAL_TEE_U8:
                case Bytecode.LOCAL_TEE_OBJ_U8:
                case Bytecode.GLOBAL_GET_U8:
                case Bytecode.GLOBAL_SET_U8:
                case Bytecode.I32_LOAD_U8:
                case Bytecode.I64_LOAD_U8:
                case Bytecode.F32_LOAD_U8:
                case Bytecode.F64_LOAD_U8:
                case Bytecode.I32_LOAD8_S_U8:
                case Bytecode.I32_LOAD8_U_U8:
                case Bytecode.I32_LOAD16_S_U8:
                case Bytecode.I32_LOAD16_U_U8:
                case Bytecode.I64_LOAD8_S_U8:
                case Bytecode.I64_LOAD8_U_U8:
                case Bytecode.I64_LOAD16_S_U8:
                case Bytecode.I64_LOAD16_U_U8:
                case Bytecode.I64_LOAD32_S_U8:
                case Bytecode.I64_LOAD32_U_U8:
                case Bytecode.I32_STORE_U8:
                case Bytecode.I64_STORE_U8:
                case Bytecode.F32_STORE_U8:
                case Bytecode.F64_STORE_U8:
                case Bytecode.I32_STORE_8_U8:
                case Bytecode.I32_STORE_16_U8:
                case Bytecode.I64_STORE_8_U8:
                case Bytecode.I64_STORE_16_U8:
                case Bytecode.I64_STORE_32_U8:
                case Bytecode.I32_CONST_I8:
                case Bytecode.I64_CONST_I8: {
                    offset++;
                    break;
                }
                case Bytecode.LABEL_U16:
                    offset += 2;
                    break;
                case Bytecode.BR_IF_U8: {
                    offset += 3;
                    break;
                }
                case Bytecode.MEMORY_SIZE:
                case Bytecode.MEMORY_GROW:
                case Bytecode.BR_I32:
                case Bytecode.LOCAL_GET_I32:
                case Bytecode.LOCAL_GET_OBJ_I32:
                case Bytecode.LOCAL_SET_I32:
                case Bytecode.LOCAL_SET_OBJ_I32:
                case Bytecode.LOCAL_TEE_I32:
                case Bytecode.LOCAL_TEE_OBJ_I32:
                case Bytecode.GLOBAL_GET_I32:
                case Bytecode.GLOBAL_SET_I32:
                case Bytecode.I32_LOAD_I32:
                case Bytecode.I64_LOAD_I32:
                case Bytecode.F32_LOAD_I32:
                case Bytecode.F64_LOAD_I32:
                case Bytecode.I32_LOAD8_S_I32:
                case Bytecode.I32_LOAD8_U_I32:
                case Bytecode.I32_LOAD16_S_I32:
                case Bytecode.I32_LOAD16_U_I32:
                case Bytecode.I64_LOAD8_S_I32:
                case Bytecode.I64_LOAD8_U_I32:
                case Bytecode.I64_LOAD16_S_I32:
                case Bytecode.I64_LOAD16_U_I32:
                case Bytecode.I64_LOAD32_S_I32:
                case Bytecode.I64_LOAD32_U_I32:
                case Bytecode.I32_STORE_I32:
                case Bytecode.I64_STORE_I32:
                case Bytecode.F32_STORE_I32:
                case Bytecode.F64_STORE_I32:
                case Bytecode.I32_STORE_8_I32:
                case Bytecode.I32_STORE_16_I32:
                case Bytecode.I64_STORE_8_I32:
                case Bytecode.I64_STORE_16_I32:
                case Bytecode.I64_STORE_32_I32:
                case Bytecode.I32_CONST_I32:
                case Bytecode.F32_CONST:
                case Bytecode.REF_FUNC:
                case Bytecode.TABLE_GET:
                case Bytecode.TABLE_SET: {
                    offset += 4;
                    break;
                }
                case Bytecode.IF:
                case Bytecode.BR_IF_I32: {
                    offset += 6;
                    break;
                }
                case Bytecode.I64_CONST_I64:
                case Bytecode.F64_CONST:
                case Bytecode.NOTIFY:
                    offset += 8;
                    break;
                case Bytecode.LABEL_I32:
                    offset += 9;
                    break;
                case Bytecode.BR_TABLE_U8: {
                    final int size = rawPeekU8(bytecode, offset);
                    offset += 3 + size * 6;
                    break;
                }
                case Bytecode.BR_TABLE_I32: {
                    final int size = rawPeekI32(bytecode, offset);
                    offset += 6 + size * 6;
                    break;
                }
                case Bytecode.I32_LOAD:
                case Bytecode.I64_LOAD:
                case Bytecode.F32_LOAD:
                case Bytecode.F64_LOAD:
                case Bytecode.I32_LOAD8_S:
                case Bytecode.I32_LOAD8_U:
                case Bytecode.I32_LOAD16_S:
                case Bytecode.I32_LOAD16_U:
                case Bytecode.I64_LOAD8_S:
                case Bytecode.I64_LOAD8_U:
                case Bytecode.I64_LOAD16_S:
                case Bytecode.I64_LOAD16_U:
                case Bytecode.I64_LOAD32_S:
                case Bytecode.I64_LOAD32_U:
                case Bytecode.I32_STORE:
                case Bytecode.I64_STORE:
                case Bytecode.F32_STORE:
                case Bytecode.F64_STORE:
                case Bytecode.I32_STORE_8:
                case Bytecode.I32_STORE_16:
                case Bytecode.I64_STORE_8:
                case Bytecode.I64_STORE_16:
                case Bytecode.I64_STORE_32: {
                    final int flags = rawPeekU8(bytecode, offset);
                    offset++;
                    final int offsetLength = flags & BytecodeBitEncoding.MEMORY_OFFSET_MASK;
                    offset += 4;
                    switch (offsetLength) {
                        case BytecodeBitEncoding.MEMORY_OFFSET_U8:
                            offset++;
                            break;
                        case BytecodeBitEncoding.MEMORY_OFFSET_U32:
                            offset += 4;
                            break;
                        case BytecodeBitEncoding.MEMORY_OFFSET_I64:
                            offset += 8;
                            break;
                        default:
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                    break;
                }
                case Bytecode.MISC:
                    int miscOpcode = rawPeekU8(bytecode, offset);
                    offset++;
                    switch (miscOpcode) {
                        case Bytecode.I32_TRUNC_SAT_F32_S:
                        case Bytecode.I32_TRUNC_SAT_F32_U:
                        case Bytecode.I32_TRUNC_SAT_F64_S:
                        case Bytecode.I32_TRUNC_SAT_F64_U:
                        case Bytecode.I64_TRUNC_SAT_F32_S:
                        case Bytecode.I64_TRUNC_SAT_F32_U:
                        case Bytecode.I64_TRUNC_SAT_F64_S:
                        case Bytecode.I64_TRUNC_SAT_F64_U: {
                            break;
                        }
                        case Bytecode.MEMORY_FILL:
                        case Bytecode.MEMORY64_FILL:
                        case Bytecode.MEMORY64_SIZE:
                        case Bytecode.MEMORY64_GROW:
                        case Bytecode.DATA_DROP:
                        case Bytecode.DATA_DROP_UNSAFE:
                        case Bytecode.ELEM_DROP:
                        case Bytecode.TABLE_GROW:
                        case Bytecode.TABLE_SIZE:
                        case Bytecode.TABLE_FILL: {
                            offset += 4;
                            break;
                        }
                        case Bytecode.MEMORY_INIT:
                        case Bytecode.MEMORY_INIT_UNSAFE:
                        case Bytecode.MEMORY64_INIT:
                        case Bytecode.MEMORY64_INIT_UNSAFE:
                        case Bytecode.MEMORY_COPY:
                        case Bytecode.MEMORY64_COPY_D32_S64:
                        case Bytecode.MEMORY64_COPY_D64_S32:
                        case Bytecode.MEMORY64_COPY_D64_S64:
                        case Bytecode.TABLE_INIT:
                        case Bytecode.TABLE_COPY: {
                            offset += 8;
                            break;
                        }
                        default:
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                    break;
                case Bytecode.ATOMIC:
                    final int atomicOpcode = rawPeekU8(bytecode, offset);
                    offset++;
                    if (atomicOpcode == Bytecode.ATOMIC_FENCE) {
                        break;
                    }
                    switch (atomicOpcode) {
                        case Bytecode.ATOMIC_NOTIFY:
                        case Bytecode.ATOMIC_WAIT32:
                        case Bytecode.ATOMIC_WAIT64:
                        case Bytecode.ATOMIC_I32_LOAD:
                        case Bytecode.ATOMIC_I64_LOAD:
                        case Bytecode.ATOMIC_I32_LOAD8_U:
                        case Bytecode.ATOMIC_I32_LOAD16_U:
                        case Bytecode.ATOMIC_I64_LOAD8_U:
                        case Bytecode.ATOMIC_I64_LOAD16_U:
                        case Bytecode.ATOMIC_I64_LOAD32_U:
                        case Bytecode.ATOMIC_I32_STORE:
                        case Bytecode.ATOMIC_I64_STORE:
                        case Bytecode.ATOMIC_I32_STORE8:
                        case Bytecode.ATOMIC_I32_STORE16:
                        case Bytecode.ATOMIC_I64_STORE8:
                        case Bytecode.ATOMIC_I64_STORE16:
                        case Bytecode.ATOMIC_I64_STORE32:
                        case Bytecode.ATOMIC_I32_RMW_ADD:
                        case Bytecode.ATOMIC_I64_RMW_ADD:
                        case Bytecode.ATOMIC_I32_RMW8_U_ADD:
                        case Bytecode.ATOMIC_I32_RMW16_U_ADD:
                        case Bytecode.ATOMIC_I64_RMW8_U_ADD:
                        case Bytecode.ATOMIC_I64_RMW16_U_ADD:
                        case Bytecode.ATOMIC_I64_RMW32_U_ADD:
                        case Bytecode.ATOMIC_I32_RMW_SUB:
                        case Bytecode.ATOMIC_I64_RMW_SUB:
                        case Bytecode.ATOMIC_I32_RMW8_U_SUB:
                        case Bytecode.ATOMIC_I32_RMW16_U_SUB:
                        case Bytecode.ATOMIC_I64_RMW8_U_SUB:
                        case Bytecode.ATOMIC_I64_RMW16_U_SUB:
                        case Bytecode.ATOMIC_I64_RMW32_U_SUB:
                        case Bytecode.ATOMIC_I32_RMW_AND:
                        case Bytecode.ATOMIC_I64_RMW_AND:
                        case Bytecode.ATOMIC_I32_RMW8_U_AND:
                        case Bytecode.ATOMIC_I32_RMW16_U_AND:
                        case Bytecode.ATOMIC_I64_RMW8_U_AND:
                        case Bytecode.ATOMIC_I64_RMW16_U_AND:
                        case Bytecode.ATOMIC_I64_RMW32_U_AND:
                        case Bytecode.ATOMIC_I32_RMW_OR:
                        case Bytecode.ATOMIC_I64_RMW_OR:
                        case Bytecode.ATOMIC_I32_RMW8_U_OR:
                        case Bytecode.ATOMIC_I32_RMW16_U_OR:
                        case Bytecode.ATOMIC_I64_RMW8_U_OR:
                        case Bytecode.ATOMIC_I64_RMW16_U_OR:
                        case Bytecode.ATOMIC_I64_RMW32_U_OR:
                        case Bytecode.ATOMIC_I32_RMW_XOR:
                        case Bytecode.ATOMIC_I64_RMW_XOR:
                        case Bytecode.ATOMIC_I32_RMW8_U_XOR:
                        case Bytecode.ATOMIC_I32_RMW16_U_XOR:
                        case Bytecode.ATOMIC_I64_RMW8_U_XOR:
                        case Bytecode.ATOMIC_I64_RMW16_U_XOR:
                        case Bytecode.ATOMIC_I64_RMW32_U_XOR:
                        case Bytecode.ATOMIC_I32_RMW_XCHG:
                        case Bytecode.ATOMIC_I64_RMW_XCHG:
                        case Bytecode.ATOMIC_I32_RMW8_U_XCHG:
                        case Bytecode.ATOMIC_I32_RMW16_U_XCHG:
                        case Bytecode.ATOMIC_I64_RMW8_U_XCHG:
                        case Bytecode.ATOMIC_I64_RMW16_U_XCHG:
                        case Bytecode.ATOMIC_I64_RMW32_U_XCHG:
                        case Bytecode.ATOMIC_I32_RMW_CMPXCHG:
                        case Bytecode.ATOMIC_I64_RMW_CMPXCHG:
                        case Bytecode.ATOMIC_I32_RMW8_U_CMPXCHG:
                        case Bytecode.ATOMIC_I32_RMW16_U_CMPXCHG:
                        case Bytecode.ATOMIC_I64_RMW8_U_CMPXCHG:
                        case Bytecode.ATOMIC_I64_RMW16_U_CMPXCHG:
                        case Bytecode.ATOMIC_I64_RMW32_U_CMPXCHG: {
                            final int encoding = rawPeekU8(bytecode, offset);
                            offset++;
                            final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
                            offset += 4;
                            if (indexType64 == 0) {
                                offset += 4;
                            } else {
                                offset += 8;
                            }
                            break;
                        }
                        default:
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                    break;
                case Bytecode.VECTOR:
                    final int vectorOpcode = rawPeekU8(bytecode, offset);
                    offset++;
                    switch (vectorOpcode) {
                        case Bytecode.VECTOR_V128_LOAD: {
                            final int encoding = rawPeekU8(bytecode, offset);
                            offset++;
                            final int indexType64 = encoding & BytecodeBitEncoding.MEMORY_64_FLAG;
                            offset += 4;
                            if (indexType64 == 0) {
                                offset += 4;
                            } else {
                                offset += 8;
                            }
                            break;
                        }
                        case Bytecode.VECTOR_V128_CONST:
                            offset += 16;
                            break;
                        case Bytecode.VECTOR_I16X8_EQ:
                        case Bytecode.VECTOR_I16X8_NE:
                        case Bytecode.VECTOR_I16X8_LT_S:
                        case Bytecode.VECTOR_I16X8_LT_U:
                        case Bytecode.VECTOR_I16X8_GT_S:
                        case Bytecode.VECTOR_I16X8_GT_U:
                        case Bytecode.VECTOR_I16X8_LE_S:
                        case Bytecode.VECTOR_I16X8_LE_U:
                        case Bytecode.VECTOR_I16X8_GE_S:
                        case Bytecode.VECTOR_I16X8_GE_U:
                        case Bytecode.VECTOR_I32X4_EQ:
                        case Bytecode.VECTOR_I32X4_NE:
                        case Bytecode.VECTOR_I32X4_LT_S:
                        case Bytecode.VECTOR_I32X4_LT_U:
                        case Bytecode.VECTOR_I32X4_GT_S:
                        case Bytecode.VECTOR_I32X4_GT_U:
                        case Bytecode.VECTOR_I32X4_LE_S:
                        case Bytecode.VECTOR_I32X4_LE_U:
                        case Bytecode.VECTOR_I32X4_GE_S:
                        case Bytecode.VECTOR_I32X4_GE_U:
                        case Bytecode.VECTOR_I64X2_EQ:
                        case Bytecode.VECTOR_I64X2_NE:
                        case Bytecode.VECTOR_I64X2_LT_S:
                        case Bytecode.VECTOR_I64X2_GT_S:
                        case Bytecode.VECTOR_I64X2_LE_S:
                        case Bytecode.VECTOR_I64X2_GE_S:
                        case Bytecode.VECTOR_F32X4_EQ:
                        case Bytecode.VECTOR_F32X4_NE:
                        case Bytecode.VECTOR_F32X4_LT:
                        case Bytecode.VECTOR_F32X4_GT:
                        case Bytecode.VECTOR_F32X4_LE:
                        case Bytecode.VECTOR_F32X4_GE:
                        case Bytecode.VECTOR_F64X2_EQ:
                        case Bytecode.VECTOR_F64X2_NE:
                        case Bytecode.VECTOR_F64X2_LT:
                        case Bytecode.VECTOR_F64X2_GT:
                        case Bytecode.VECTOR_F64X2_LE:
                        case Bytecode.VECTOR_F64X2_GE:
                        case Bytecode.VECTOR_V128_ANY_TRUE:
                        case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8x16_S:
                        case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8x16_U:
                        case Bytecode.VECTOR_I16X8_ABS:
                        case Bytecode.VECTOR_I16X8_NEG:
                        case Bytecode.VECTOR_I16X8_Q15MULR_SAT_S:
                        case Bytecode.VECTOR_I16X8_ALL_TRUE:
                        case Bytecode.VECTOR_I16X8_BITMASK:
                        case Bytecode.VECTOR_I16X8_NARROW_I32X4_S:
                        case Bytecode.VECTOR_I16X8_NARROW_I32X4_U:
                        case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8x16_S:
                        case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8x16_S:
                        case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8x16_U:
                        case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8x16_U:
                        case Bytecode.VECTOR_I16X8_SHL:
                        case Bytecode.VECTOR_I16X8_SHR_S:
                        case Bytecode.VECTOR_I16X8_SHR_U:
                        case Bytecode.VECTOR_I16X8_ADD:
                        case Bytecode.VECTOR_I16X8_ADD_SAT_S:
                        case Bytecode.VECTOR_I16X8_ADD_SAT_U:
                        case Bytecode.VECTOR_I16X8_SUB:
                        case Bytecode.VECTOR_I16X8_SUB_SAT_S:
                        case Bytecode.VECTOR_I16X8_SUB_SAT_U:
                        case Bytecode.VECTOR_I16X8_MUL:
                        case Bytecode.VECTOR_I16X8_MIN_S:
                        case Bytecode.VECTOR_I16X8_MIN_U:
                        case Bytecode.VECTOR_I16X8_MAX_S:
                        case Bytecode.VECTOR_I16X8_MAX_U:
                        case Bytecode.VECTOR_I16X8_AVGR_U:
                        case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8x16_S:
                        case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8x16_S:
                        case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8x16_U:
                        case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8x16_U:
                        case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_S:
                        case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_U:
                        case Bytecode.VECTOR_I32X4_ABS:
                        case Bytecode.VECTOR_I32X4_NEG:
                        case Bytecode.VECTOR_I32X4_ALL_TRUE:
                        case Bytecode.VECTOR_I32X4_BITMASK:
                        case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_S:
                        case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_S:
                        case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_U:
                        case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_U:
                        case Bytecode.VECTOR_I32X4_SHL:
                        case Bytecode.VECTOR_I32X4_SHR_S:
                        case Bytecode.VECTOR_I32X4_SHR_U:
                        case Bytecode.VECTOR_I32X4_ADD:
                        case Bytecode.VECTOR_I32X4_SUB:
                        case Bytecode.VECTOR_I32X4_MUL:
                        case Bytecode.VECTOR_I32X4_MIN_S:
                        case Bytecode.VECTOR_I32X4_MIN_U:
                        case Bytecode.VECTOR_I32X4_MAX_S:
                        case Bytecode.VECTOR_I32X4_MAX_U:
                        case Bytecode.VECTOR_I32X4_DOT_I16X8_S:
                        case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_S:
                        case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_S:
                        case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_U:
                        case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_U:
                        case Bytecode.VECTOR_I64X2_ABS:
                        case Bytecode.VECTOR_I64X2_NEG:
                        case Bytecode.VECTOR_I64X2_ALL_TRUE:
                        case Bytecode.VECTOR_I64X2_BITMASK:
                        case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_S:
                        case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_S:
                        case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_U:
                        case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_U:
                        case Bytecode.VECTOR_I64X2_SHL:
                        case Bytecode.VECTOR_I64X2_SHR_S:
                        case Bytecode.VECTOR_I64X2_SHR_U:
                        case Bytecode.VECTOR_I64X2_ADD:
                        case Bytecode.VECTOR_I64X2_SUB:
                        case Bytecode.VECTOR_I64X2_MUL:
                        case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_S:
                        case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_S:
                        case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_U:
                        case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_U:
                        case Bytecode.VECTOR_F32X4_CEIL:
                        case Bytecode.VECTOR_F32X4_FLOOR:
                        case Bytecode.VECTOR_F32X4_TRUNC:
                        case Bytecode.VECTOR_F32X4_NEAREST:
                        case Bytecode.VECTOR_F32X4_ABS:
                        case Bytecode.VECTOR_F32X4_NEG:
                        case Bytecode.VECTOR_F32X4_SQRT:
                        case Bytecode.VECTOR_F32X4_ADD:
                        case Bytecode.VECTOR_F32X4_SUB:
                        case Bytecode.VECTOR_F32X4_MUL:
                        case Bytecode.VECTOR_F32X4_DIV:
                        case Bytecode.VECTOR_F32X4_MIN:
                        case Bytecode.VECTOR_F32X4_MAX:
                        case Bytecode.VECTOR_F32X4_PMIN:
                        case Bytecode.VECTOR_F32X4_PMAX:
                        case Bytecode.VECTOR_F64X2_CEIL:
                        case Bytecode.VECTOR_F64X2_FLOOR:
                        case Bytecode.VECTOR_F64X2_TRUNC:
                        case Bytecode.VECTOR_F64X2_NEAREST:
                        case Bytecode.VECTOR_F64X2_ABS:
                        case Bytecode.VECTOR_F64X2_NEG:
                        case Bytecode.VECTOR_F64X2_SQRT:
                        case Bytecode.VECTOR_F64X2_ADD:
                        case Bytecode.VECTOR_F64X2_SUB:
                        case Bytecode.VECTOR_F64X2_MUL:
                        case Bytecode.VECTOR_F64X2_DIV:
                        case Bytecode.VECTOR_F64X2_MIN:
                        case Bytecode.VECTOR_F64X2_MAX:
                        case Bytecode.VECTOR_F64X2_PMIN:
                        case Bytecode.VECTOR_F64X2_PMAX:
                        case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_S:
                        case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_U:
                        case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_S_ZERO:
                        case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_U_ZERO:
                            break;
                        default:
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
        return callNodes;
    }
}
