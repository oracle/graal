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
import org.graalvm.wasm.WasmType;

import org.graalvm.wasm.constants.Bytecode;
import org.graalvm.wasm.constants.BytecodeBitEncoding;
import org.graalvm.wasm.constants.SegmentMode;

/**
 * A data structure for generating the GraalWasm runtime bytecode.
 */
public class RuntimeBytecodeGen extends BytecodeGen {

    private static boolean fitsIntoSignedByte(int value) {
        return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
    }

    private static boolean fitsIntoSignedByte(long value) {
        return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
    }

    private static boolean fitsIntoSixBits(int value) {
        return Integer.compareUnsigned(value, 63) <= 0;
    }

    private static boolean fitsIntoUnsignedByte(int value) {
        return Integer.compareUnsigned(value, 255) <= 0;
    }

    private static boolean fitsIntoUnsignedByte(long value) {
        return Long.compareUnsigned(value, 255) <= 0;
    }

    private static boolean fitsIntoUnsignedShort(int value) {
        return Integer.compareUnsigned(value, 65535) <= 0;
    }

    private static boolean fitsIntoUnsignedShort(long value) {
        return Long.compareUnsigned(value, 65535) <= 0;
    }

    private static boolean fitsIntoUnsignedInt(long value) {
        return Long.compareUnsigned(value, 4294967295L) <= 0;
    }

    private void addProfile() {
        add2(0);
    }

    /**
     * Adds an opcode to the bytecode. See {@link Bytecode} for a list of opcodes.
     * 
     * @param opcode The opcode
     */
    @Override
    public void add(int opcode) {
        assert fitsIntoUnsignedByte(opcode) : "opcode does not fit into byte";
        add1(opcode);
    }

    /**
     * Adds an opcode and an i32 immediate value to the bytecode. See {@link Bytecode} for a list of
     * opcode.
     * 
     * @param opcode The opcode
     * @param value The immediate value
     */
    public void add(int opcode, int value) {
        assert fitsIntoUnsignedByte(opcode) : "opcode does not fit into byte";
        add1(opcode);
        add4(value);
    }

    /**
     * Adds an opcode and an i64 immediate value to the bytecode. See {@link Bytecode} for a list of
     * opcodes.
     * 
     * @param opcode The opcode
     * @param value The immediate value
     */
    public void add(int opcode, long value) {
        assert fitsIntoUnsignedByte(opcode) : "opcode does not fit into byte";
        add1(opcode);
        add8(value);
    }

    /**
     * Adds an opcode and two i32 immediate values to the bytecode. See {@link Bytecode} for a list
     * of opcodes.
     * 
     * @param opcode The opcode
     * @param value1 The first immediate value
     * @param value2 The second immediate value
     */
    public void add(int opcode, int value1, int value2) {
        assert fitsIntoUnsignedByte(opcode) : "opcode does not fit into byte";
        add1(opcode);
        add4(value1);
        add4(value2);
    }

    /**
     * Adds an opcode and an immediate value to the bytecode. If the value fits into a signed i8
     * value, the i8 opcode and an i8 value are added. Otherwise, the i32 opcode and an i32 value
     * are added. See {@link Bytecode} for a list of opcode.
     * 
     * @param opcodeI8 The i8 opcode
     * @param opcodeI32 The i32 opcode
     * @param value The immediate value
     */
    public void addSigned(int opcodeI8, int opcodeI32, int value) {
        assert fitsIntoUnsignedByte(opcodeI8) && fitsIntoUnsignedByte(opcodeI32) : "opcode does not fit into byte";
        if (fitsIntoSignedByte(value)) {
            add1(opcodeI8);
            add1(value);
        } else {
            add1(opcodeI32);
            add4(value);
        }
    }

    /**
     * Adds an opcode and an immediate value to the bytecode. If the value fits into an i8 value,
     * the i8 opcode and an i8 value are added. Otherwise, the i64 opcode and an i64 value are
     * added. See {@link Bytecode} for a list of opcode.
     *
     * @param opcodeI8 The i8 opcode
     * @param opcodeI64 The i64 opcode
     * @param value The immediate value
     */
    public void addSigned(int opcodeI8, int opcodeI64, long value) {
        assert fitsIntoUnsignedByte(opcodeI8) && fitsIntoUnsignedByte(opcodeI64) : "opcode does not fit into byte";
        if (fitsIntoSignedByte(value)) {
            add1(opcodeI8);
            add1(value);
        } else {
            add1(opcodeI64);
            add8(value);
        }
    }

    /**
     * Adds an opcode and an immediate value to the bytecode. If the value fits into a u8 value, the
     * u8 opcode and a u8 value are added. Otherwise, the i32 opcode and an i32 value are added. See
     * {@link Bytecode} for a list of opcode.
     *
     * @param opcodeU8 The u8 opcode
     * @param opcodeI32 The i32 opcode
     * @param value The immediate value
     */
    public void addUnsigned(int opcodeU8, int opcodeI32, int value) {
        assert fitsIntoUnsignedByte(opcodeU8) && fitsIntoSignedByte(opcodeI32) : "opcode does not fit into byte";
        if (fitsIntoUnsignedByte(value)) {
            add1(opcodeU8);
            add1(value);
        } else {
            add1(opcodeI32);
            add4(value);
        }
    }

    /**
     * Adds a memory access instruction to the bytecode. If the value fits into a u8 value and
     * indexType64 is false, the u8 opcode and a u8 value are added. If the value fits into a i32
     * value and indexType64 is false, the i32 opcode and an i32 value are added. Otherwise, the
     * generic opcode and data encoding are added. See {@link Bytecode} for a list of opcode.
     * 
     * @param opcode The generic memory opcode
     * @param opcodeU8 The u8 memory opcode
     * @param opcodeI32 The i32 memory opcode
     * @param offset The offset value
     * @param indexType64 If the accessed memory has index type 64.
     */
    public void addMemoryInstruction(int opcode, int opcodeU8, int opcodeI32, int memoryIndex, long offset, boolean indexType64) {
        assert fitsIntoUnsignedByte(opcode) && fitsIntoUnsignedByte(opcodeU8) && fitsIntoUnsignedByte(opcodeI32) : "opcode does not fit into byte";
        if (!indexType64 && memoryIndex == 0) {
            if (fitsIntoUnsignedByte(offset)) {
                add1(opcodeU8);
                add1(offset);
            } else if (fitsIntoUnsignedInt(offset)) {
                add1(opcodeI32);
                add4(offset);
            } else {
                add1(opcode);
                add1(BytecodeBitEncoding.MEMORY_OFFSET_I64);
                add8(offset);
            }
        } else {
            add1(opcode);
            final int location = location();
            add1(0);
            int flags;
            if (indexType64) {
                flags = BytecodeBitEncoding.MEMORY_64_FLAG;
            } else {
                flags = 0;
            }
            add4(memoryIndex);
            if (fitsIntoUnsignedByte(offset)) {
                flags |= BytecodeBitEncoding.MEMORY_OFFSET_U8;
                add1(offset);
            } else if (fitsIntoUnsignedInt(offset)) {
                flags |= BytecodeBitEncoding.MEMORY_OFFSET_U32;
                add4(offset);
            } else {
                flags |= BytecodeBitEncoding.MEMORY_OFFSET_I64;
                add8(offset);
            }
            set(location, (byte) flags);
        }
    }

    /**
     * Adds an atomic memory access instruction to the bytecode.
     *
     * @param opcode The atomic memory opcode
     * @param offset The offset value
     * @param indexType64 If the accessed memory has index type 64.
     */
    public void addAtomicMemoryInstruction(int opcode, int memoryIndex, long offset, boolean indexType64) {
        assert fitsIntoUnsignedByte(opcode) : "opcode does not fit into byte";
        if (!indexType64) {
            assert fitsIntoUnsignedInt(offset) : "offset does not fit into int";
            add1(opcode);
            add1(0);
            add4(memoryIndex);
            add4(offset);
        } else {
            add1(opcode);
            final int location = location();
            add1(0);
            add4(memoryIndex);
            add8(offset);
            final int flags = BytecodeBitEncoding.MEMORY_64_FLAG;
            set(location, (byte) flags);
        }
    }

    /**
     * Adds a branch label to the bytecode.
     *
     * @param resultCount The number of results of the block.
     * @param stackSize The stack size at the start of the block.
     * @param commonResultType The most common result type of the result types of the block. See
     *            {@link WasmType#getCommonValueType(byte[])}.
     * @return The location of the label in the bytecode.
     */
    public int addLabel(int resultCount, int stackSize, int commonResultType) {
        assert commonResultType == WasmType.NONE_COMMON_TYPE || commonResultType == WasmType.NUM_COMMON_TYPE || commonResultType == WasmType.REF_COMMON_TYPE ||
                        commonResultType == WasmType.MIX_COMMON_TYPE : "invalid result type";
        final int location;
        if (resultCount == 0 && stackSize <= 63) {
            add1(Bytecode.SKIP_LABEL_U8);
            location = location();
            add1(Bytecode.LABEL_U8);
            add1(stackSize);
        } else if (resultCount == 1 && stackSize <= 63) {
            assert commonResultType != BytecodeBitEncoding.LABEL_RESULT_TYPE_MIX : "Single result value must either have number or reference type.";
            add1(Bytecode.SKIP_LABEL_U8);
            location = location();
            add1(Bytecode.LABEL_U8);
            if (commonResultType == BytecodeBitEncoding.LABEL_RESULT_TYPE_NUM) {
                add1(BytecodeBitEncoding.LABEL_U8_RESULT_NUM | stackSize);
            } else if (commonResultType == BytecodeBitEncoding.LABEL_RESULT_TYPE_REF) {
                add1(BytecodeBitEncoding.LABEL_U8_RESULT_REF | stackSize);
            }
        } else if (resultCount <= 63 && fitsIntoUnsignedByte(stackSize)) {
            add1(Bytecode.SKIP_LABEL_U16);
            location = location();
            add1(Bytecode.LABEL_U16);
            add1(commonResultType << BytecodeBitEncoding.LABEL_U16_RESULT_TYPE_SHIFT | resultCount);
            add1(stackSize);
        } else {
            add1(Bytecode.SKIP_LABEL_I32);
            location = location();
            add1(Bytecode.LABEL_I32);
            add1(commonResultType);
            add4(resultCount);
            add4(stackSize);
        }
        return location;
    }

    /**
     * Adds a loop label to the bytecode.
     *
     * @param resultCount The number of results of the loop.
     * @param stackSize The stack size at the start of the loop.
     * @param commonResultType The most common result type of the result types of the loop. See
     *            {@link WasmType#getCommonValueType(byte[])}.
     * @return The location of the loop label in the bytecode.
     */
    public int addLoopLabel(int resultCount, int stackSize, int commonResultType) {
        int loopLabel = addLabel(resultCount, stackSize, commonResultType);
        add(Bytecode.LOOP);
        return loopLabel;
    }

    /**
     * Adds an if opcode to the bytecode and reserves an i32 value for the jump offset and a 2-byte
     * profile.
     * 
     * @return The location of the jump offset to be patched later. (see
     *         {@link #patchLocation(int, int)}.
     */
    public int addIfLocation() {
        add1(Bytecode.IF);
        final int location = location();
        // target
        add4(0);
        // profile
        addProfile();
        return location;
    }

    /**
     * Adds a branch opcode to the bytecode. If the negative jump offset fits into a u8 value, a
     * br_u8 and u8 jump offset is added (The jump offset is encoded as a positive value).
     * Otherwise, a br_i32 and i32 jump offset is added.
     * 
     * @param location The target location of the branch.
     */
    public void addBranch(int location) {
        assert location >= 0;
        final int relativeOffset = location - (location() + 1);
        if (relativeOffset <= 0 && relativeOffset >= -255) {
            add1(Bytecode.BR_U8);
            add1(-relativeOffset);
        } else {
            add1(Bytecode.BR_I32);
            add4(relativeOffset);
        }
    }

    /**
     * Adds a br_i32 instruction to the bytecode and reserves an i32 value for the jump offset.
     * 
     * @return The location of the jump offset to be patched later. (see
     *         {@link #patchLocation(int, int)}).
     */
    public int addBranchLocation() {
        add1(Bytecode.BR_I32);
        final int location = location();
        add4(0);
        return location;
    }

    /**
     * Adds a conditional branch opcode to the bytecode. If the jump offset fits into a signed i8
     * value, a br_if_i8 and i8 jump offset is added. Otherwise, a br_if_i32 and i32 jump offset is
     * added. In both cases, a profile with a size of 2-byte is added.
     * 
     * @param location The target location of the branch.
     */
    public void addBranchIf(int location) {
        assert location >= 0;
        final int relativeOffset = location - (location() + 1);
        if (relativeOffset <= 0 && relativeOffset >= -255) {
            add1(Bytecode.BR_IF_U8);
            // target
            add1(-relativeOffset);
            // profile
            addProfile();
        } else {
            add1(Bytecode.BR_IF_I32);
            // target
            add4(relativeOffset);
            // profile
            addProfile();
        }
    }

    /**
     * Adds a br_if_i32 opcode to the bytecode and reserves an i32 value for the jump offset. In
     * addition, a profile with a size of 2-byte is added.
     * 
     * @return The location of the jump offset to be patched later. (see
     *         {@link #patchLocation(int, int)})
     */
    public int addBranchIfLocation() {
        add1(Bytecode.BR_IF_I32);
        final int location = location();
        // target
        add4(0);
        // profile
        addProfile();
        return location;
    }

    /**
     * Adds a branch table opcode to the bytecode. If the size fits into an u8 value, a br_table_u8
     * and u8 size are added. Otherwise, a br_table_i32 and i32 size are added. In both cases, a
     * profile with a size of 2-byte is added.
     * 
     * @param size The number of items in the branch table.
     */
    public void addBranchTable(int size) {
        if (fitsIntoUnsignedByte(size)) {
            add1(Bytecode.BR_TABLE_U8);
            add1(size);
            // profile
            addProfile();
        } else {
            add1(Bytecode.BR_TABLE_I32);
            add4(size);
            // profile
            addProfile();
        }
    }

    /**
     * Reserves an i32 jump offset location and 2-byte profile for a branch table item.
     * 
     * @return The location of the jump offset to be patched later. (see
     *         {@link #patchLocation(int, int)}).
     */
    public int addBranchTableItemLocation() {
        final int location = location();
        // target
        add4(0);
        // profile
        addProfile();
        return location;
    }

    /**
     * Patches a jump offset location based on a given target location.
     *
     * @param jumpOffsetLocation The jump offset location
     * @param targetLocation The target location
     */
    public void patchLocation(int jumpOffsetLocation, int targetLocation) {
        final int relativeOffset = targetLocation - jumpOffsetLocation;
        set(jumpOffsetLocation, (byte) (relativeOffset & 0x0000_00FF));
        set(jumpOffsetLocation + 1, (byte) ((relativeOffset >>> 8) & 0x0000_00FF));
        set(jumpOffsetLocation + 2, (byte) ((relativeOffset >>> 16) & 0x0000_00FF));
        set(jumpOffsetLocation + 3, (byte) ((relativeOffset >>> 24) & 0x0000_00FF));
    }

    /**
     * Adds a call instruction to the bytecode. If the nodeIndex and functionIndex both fit into a
     * u8 value, a call_u8 and two u8 values are added. Otherwise, a call_i32 and two i32 value are
     * added.
     * 
     * @param nodeIndex The node index of the call
     * @param functionIndex The function index of the call
     */
    public void addCall(int nodeIndex, int functionIndex) {
        if (fitsIntoUnsignedByte(nodeIndex) && fitsIntoUnsignedByte(functionIndex)) {
            add1(Bytecode.CALL_U8);
            add1(nodeIndex);
            add1(functionIndex);
        } else {
            add1(Bytecode.CALL_I32);
            add4(nodeIndex);
            add4(functionIndex);
        }
    }

    /**
     * Adds an indirect call instruction to the bytecode. If the nodeIndex, typeIndex, and
     * tableIndex all fit into a u8 value, a call_indirect_u8 and three u8 values are added.
     * Otherwise, a call_indirect_i32 and three i32 values are added. In both cases, a 2-byte
     * profile is added.
     * 
     * @param nodeIndex The node index of the indirect call
     * @param typeIndex The type index of the indirect call
     * @param tableIndex The table index of the indirect call
     */
    public void addIndirectCall(int nodeIndex, int typeIndex, int tableIndex) {
        if (fitsIntoUnsignedByte(nodeIndex) && fitsIntoUnsignedByte(typeIndex) && fitsIntoUnsignedByte(tableIndex)) {
            add1(Bytecode.CALL_INDIRECT_U8);
            add1(nodeIndex);
            add1(typeIndex);
            add1(tableIndex);
            // profile
            addProfile();
        } else {
            add1(Bytecode.CALL_INDIRECT_I32);
            add4(nodeIndex);
            add4(typeIndex);
            add4(tableIndex);
            // profile
            addProfile();
        }
    }

    private void addDataHeader(int mode, int length, byte[] offsetBytecode, long offsetAddress, int memoryIndex) {
        assert offsetBytecode == null || offsetAddress == -1 : "data header does not allow offset bytecode and offset address";
        assert mode == SegmentMode.ACTIVE || mode == SegmentMode.PASSIVE : "invalid segment mode in data header";
        int firstByteLocation = location();
        add1(0);
        int firstByteFlags = mode;
        if (fitsIntoUnsignedByte(length)) {
            firstByteFlags |= BytecodeBitEncoding.DATA_SEG_LENGTH_U8;
            add1(length);
        } else if (fitsIntoUnsignedShort(length)) {
            firstByteFlags |= BytecodeBitEncoding.DATA_SEG_LENGTH_U16;
            add2(length);
        } else {
            firstByteFlags |= BytecodeBitEncoding.DATA_SEG_LENGTH_I32;
            add4(length);
        }
        if (offsetBytecode != null) {
            firstByteFlags |= BytecodeBitEncoding.DATA_SEG_BYTECODE;
            if (fitsIntoUnsignedByte(offsetBytecode.length)) {
                firstByteFlags |= BytecodeBitEncoding.DATA_SEG_VALUE_U8;
                add1(offsetBytecode.length);
            } else if (fitsIntoUnsignedShort(offsetBytecode.length)) {
                firstByteFlags |= BytecodeBitEncoding.DATA_SEG_VALUE_U16;
                add2(offsetBytecode.length);
            } else {
                firstByteFlags |= BytecodeBitEncoding.DATA_SEG_VALUE_U32;
                add4(offsetBytecode.length);
            }
            addBytes(offsetBytecode, 0, offsetBytecode.length);
        }
        if (offsetAddress != -1) {
            firstByteFlags |= BytecodeBitEncoding.DATA_SEG_OFFSET;
            if (fitsIntoUnsignedByte(offsetAddress)) {
                firstByteFlags |= BytecodeBitEncoding.DATA_SEG_VALUE_U8;
                add1(offsetAddress);
            } else if (fitsIntoUnsignedShort(offsetAddress)) {
                firstByteFlags |= BytecodeBitEncoding.DATA_SEG_VALUE_U16;
                add2(offsetAddress);
            } else if (fitsIntoUnsignedInt(offsetAddress)) {
                firstByteFlags |= BytecodeBitEncoding.DATA_SEG_VALUE_U32;
                add4(offsetAddress);
            } else {
                firstByteFlags |= BytecodeBitEncoding.DATA_SEG_VALUE_I64;
                add8(offsetAddress);
            }
        }
        if (memoryIndex == 0) {
            firstByteFlags |= BytecodeBitEncoding.DATA_SEG_HAS_MEMORY_INDEX_ZERO;
        }
        set(firstByteLocation, (byte) firstByteFlags);

        if (memoryIndex != -1 && memoryIndex != 0) {
            int secondByteLocation = location();
            add1(0);
            int secondByteFlags = 0;
            if (fitsIntoSixBits(memoryIndex)) {
                secondByteFlags |= BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_U6;
                secondByteFlags |= memoryIndex;
            } else if (fitsIntoUnsignedByte(memoryIndex)) {
                secondByteFlags |= BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_U8;
                add1(memoryIndex);
            } else if (fitsIntoUnsignedShort(memoryIndex)) {
                secondByteFlags |= BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_U16;
                add2(memoryIndex);
            } else {
                secondByteFlags |= BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_I32;
                add4(memoryIndex);
            }
            set(secondByteLocation, (byte) secondByteFlags);
        }
    }

    /**
     * Adds the header of a data segment to the bytecode.
     * 
     * @param length The length of the data segment
     * @param offsetBytecode The offset bytecode of the data segment, null if missing
     * @param offsetAddress The offset address of the data segment, -1 if missing
     */
    public void addDataHeader(int length, byte[] offsetBytecode, long offsetAddress, int memoryIndex) {
        addDataHeader(SegmentMode.ACTIVE, length, offsetBytecode, offsetAddress, memoryIndex);
    }

    /**
     * Adds the header of a non-active data segment to the bytecode.
     * 
     * @param mode The segment mode of the data segment
     * @param length The length of the data segment
     */
    public void addDataHeader(int mode, int length) {
        assert mode != SegmentMode.ACTIVE : "invalid active segment mode in passive data header";
        addDataHeader(mode, length, null, -1, -1);
    }

    /**
     * Adds the runtime header of a data segment to the bytecode.
     * 
     * @param length The length of the data segment
     * @param unsafeMemory If unsafe memory is enabled
     */
    public void addDataRuntimeHeader(int length, boolean unsafeMemory) {
        int location = location();
        add1(0);
        int flags = 0;
        if (length <= 63) {
            flags = length;
        } else if (fitsIntoUnsignedByte(length)) {
            flags |= BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_U8;
            add1(length);
        } else if (fitsIntoUnsignedShort(length)) {
            flags |= BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_U16;
            add2(length);
        } else {
            flags |= BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_I32;
            add4(length);
        }
        if (unsafeMemory) {
            add8(0);
        }
        set(location, (byte) flags);
    }

    /**
     * Adds the header of an elem segment to the bytecode.
     * 
     * @param mode The segment mode of the elem segment
     * @param count The number of elements in the elem segment
     * @param elemType The type of the elements in the elem segment
     * @param tableIndex The table index of the elem segment
     * @param offsetBytecode The offset bytecode of the elem segment, null if missing
     * @param offsetAddress The offset address of the elem segment, -1 if missing
     * @return The location after the header in the bytecode
     */
    public int addElemHeader(int mode, int count, byte elemType, int tableIndex, byte[] offsetBytecode, int offsetAddress) {
        assert offsetBytecode == null || offsetAddress == -1 : "elem header does not allow offset bytecode and offset address";
        assert mode == SegmentMode.ACTIVE || mode == SegmentMode.PASSIVE || mode == SegmentMode.DECLARATIVE : "invalid segment mode in elem header";
        assert elemType == WasmType.FUNCREF_TYPE || elemType == WasmType.EXTERNREF_TYPE : "invalid elem type in elem header";
        int location = location();
        add1(0);
        final int type;
        switch (elemType) {
            case WasmType.FUNCREF_TYPE:
                type = BytecodeBitEncoding.ELEM_SEG_TYPE_FUNREF;
                break;
            case WasmType.EXTERNREF_TYPE:
                type = BytecodeBitEncoding.ELEM_SEG_TYPE_EXTERNREF;
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        add1(type | mode);

        int flags = 0;
        if (fitsIntoUnsignedByte(count)) {
            flags |= BytecodeBitEncoding.ELEM_SEG_COUNT_U8;
            add1(count);
        } else if (fitsIntoUnsignedShort(count)) {
            flags |= BytecodeBitEncoding.ELEM_SEG_COUNT_U16;
            add2(count);
        } else {
            flags |= BytecodeBitEncoding.ELEM_SEG_COUNT_I32;
            add4(count);
        }
        if (tableIndex != 0) {
            if (fitsIntoUnsignedByte(tableIndex)) {
                flags |= BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_U8;
                add1(tableIndex);
            } else if (fitsIntoUnsignedShort(tableIndex)) {
                flags |= BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_U16;
                add2(tableIndex);
            } else {
                flags |= BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_I32;
                add4(tableIndex);
            }
        }
        if (offsetBytecode != null) {
            if (fitsIntoUnsignedByte(offsetBytecode.length)) {
                flags |= BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_LENGTH_U8;
                add1(offsetBytecode.length);
            } else if (fitsIntoUnsignedShort(offsetBytecode.length)) {
                flags |= BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_LENGTH_U16;
                add2(offsetBytecode.length);
            } else {
                flags |= BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_LENGTH_I32;
                add4(offsetBytecode.length);
            }
            addBytes(offsetBytecode, 0, offsetBytecode.length);
        }
        if (offsetAddress != -1) {
            if (fitsIntoUnsignedByte(offsetAddress)) {
                flags |= BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_U8;
                add1(offsetAddress);
            } else if (fitsIntoUnsignedShort(offsetAddress)) {
                flags |= BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_U16;
                add2(offsetAddress);
            } else {
                flags |= BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_I32;
                add4(offsetAddress);
            }
        }
        set(location, (byte) flags);
        return location();
    }

    /**
     * Adds a single byte to the bytecode.
     * 
     * @param value The byte that should be added
     */
    public void addByte(byte value) {
        add1(value);
    }

    /**
     * Adds a null entry to the data of an elem segment.
     */
    public void addElemNull() {
        add1(BytecodeBitEncoding.ELEM_ITEM_TYPE_FUNCTION_INDEX | BytecodeBitEncoding.ELEM_ITEM_NULL_FLAG);
    }

    /**
     * Adds a function index entry to the data of an elem segment.
     * 
     * @param functionIndex The function index of the element in the elem segment
     */
    public void addElemFunctionIndex(int functionIndex) {
        if (functionIndex >= 0 && functionIndex <= 15) {
            add1(BytecodeBitEncoding.ELEM_ITEM_TYPE_FUNCTION_INDEX | BytecodeBitEncoding.ELEM_ITEM_LENGTH_INLINE | functionIndex);
        } else if (fitsIntoUnsignedByte(functionIndex)) {
            add1(BytecodeBitEncoding.ELEM_ITEM_TYPE_FUNCTION_INDEX | BytecodeBitEncoding.ELEM_ITEM_LENGTH_U8);
            add1(functionIndex);
        } else if (fitsIntoUnsignedShort(functionIndex)) {
            add1(BytecodeBitEncoding.ELEM_ITEM_TYPE_FUNCTION_INDEX | BytecodeBitEncoding.ELEM_ITEM_LENGTH_U16);
            add2(functionIndex);
        } else {
            add1(BytecodeBitEncoding.ELEM_ITEM_TYPE_FUNCTION_INDEX | BytecodeBitEncoding.ELEM_ITEM_LENGTH_I32);
            add4(functionIndex);
        }
    }

    /**
     * Adds a global index entry to the data of an elem segment.
     * 
     * @param globalIndex The global index of the element in the elem segment
     */
    public void addElemGlobalIndex(int globalIndex) {
        if (globalIndex >= 0 && globalIndex <= 15) {
            add1(BytecodeBitEncoding.ELEM_ITEM_TYPE_GLOBAL_INDEX | BytecodeBitEncoding.ELEM_ITEM_LENGTH_INLINE | globalIndex);
        } else if (fitsIntoUnsignedByte(globalIndex)) {
            add1(BytecodeBitEncoding.ELEM_ITEM_TYPE_GLOBAL_INDEX | BytecodeBitEncoding.ELEM_ITEM_LENGTH_U8);
            add1(globalIndex);
        } else if (fitsIntoUnsignedShort(globalIndex)) {
            add1(BytecodeBitEncoding.ELEM_ITEM_TYPE_GLOBAL_INDEX | BytecodeBitEncoding.ELEM_ITEM_LENGTH_U16);
            add2(globalIndex);
        } else {
            add1(BytecodeBitEncoding.ELEM_ITEM_TYPE_GLOBAL_INDEX | BytecodeBitEncoding.ELEM_ITEM_LENGTH_I32);
            add4(globalIndex);
        }
    }

    /**
     * Adds information about a code entry to the bytecode.
     * 
     * @param functionIndex The function index of the code entry
     * @param maxStackSize The maximum stack size of the code entry
     * @param length The length of the function in the bytecode
     * @param localCount The number of local values (parameters + locals) of the function
     * @param resultCount The number of result values of the function
     */
    public void addCodeEntry(int functionIndex, int maxStackSize, int length, int localCount, int resultCount) {
        final int location = location();
        add1(0);
        int flags = 0;
        if (functionIndex != 0) {
            if (fitsIntoUnsignedByte(functionIndex)) {
                flags |= BytecodeBitEncoding.CODE_ENTRY_FUNCTION_INDEX_U8;
                add1(functionIndex);
            } else if (fitsIntoUnsignedShort(functionIndex)) {
                flags |= BytecodeBitEncoding.CODE_ENTRY_FUNCTION_INDEX_U16;
                add2(functionIndex);
            } else {
                flags |= BytecodeBitEncoding.CODE_ENTRY_FUNCTION_INDEX_I32;
                add4(functionIndex);
            }
        }
        if (maxStackSize != 0) {
            if (fitsIntoUnsignedByte(maxStackSize)) {
                flags |= BytecodeBitEncoding.CODE_ENTRY_MAX_STACK_SIZE_U8;
                add1(maxStackSize);
            } else if (fitsIntoUnsignedShort(maxStackSize)) {
                flags |= BytecodeBitEncoding.CODE_ENTRY_MAX_STACK_SIZE_U16;
                add2(maxStackSize);
            } else {
                flags |= BytecodeBitEncoding.CODE_ENTRY_MAX_STACK_SIZE_I32;
                add4(maxStackSize);
            }
        }
        if (fitsIntoUnsignedByte(length)) {
            flags |= BytecodeBitEncoding.CODE_ENTRY_LENGTH_U8;
            add1(length);
        } else if (fitsIntoUnsignedShort(length)) {
            flags |= BytecodeBitEncoding.CODE_ENTRY_LENGTH_U16;
            add2(length);
        } else {
            flags |= BytecodeBitEncoding.CODE_ENTRY_LENGTH_I32;
            add4(length);
        }
        if (localCount != 0) {
            flags |= BytecodeBitEncoding.CODE_ENTRY_LOCALS_FLAG;
        }
        if (resultCount != 0) {
            flags |= BytecodeBitEncoding.CODE_ENTRY_RESULT_FLAG;
        }
        set(location, (byte) flags);
    }

    /**
     * Adds a notify instruction used for instrumentation.
     * 
     * @param lineNumber the line number in the source code
     * @param sourceCodeLocation the location in the source
     */
    public void addNotify(int lineNumber, int sourceCodeLocation) {
        add(Bytecode.NOTIFY);
        add4(lineNumber);
        add4(sourceCodeLocation);
    }
}
