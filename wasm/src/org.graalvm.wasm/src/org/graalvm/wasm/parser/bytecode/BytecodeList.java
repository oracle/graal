/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.wasm.collection.ByteArrayList;

import org.graalvm.wasm.constants.Bytecode;
import org.graalvm.wasm.constants.SegmentMode;

/**
 * A list for generating the GraalWasm runtime bytecode.
 */
public class BytecodeList {
    private final ByteArrayList bytecode;

    public BytecodeList() {
        bytecode = new ByteArrayList();
    }

    private static boolean fitsIntoSignedByte(int value) {
        return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
    }

    private static boolean fitsIntoSignedByte(long value) {
        return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
    }

    private static boolean fitsIntoUnsignedValue(int value, int maxValue) {
        return Integer.compareUnsigned(value, maxValue) <= 0;
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

    private void add1(int value) {
        bytecode.add((byte) value);
    }

    private void add1(long value) {
        bytecode.add((byte) value);
    }

    private void addProfile() {
        bytecode.add((byte) 0);
        bytecode.add((byte) 0);
    }

    private void add2(int value) {
        bytecode.add((byte) (value & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 8) & 0x0000_00FF));
    }

    private void add2(long value) {
        bytecode.add((byte) (value & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 8) & 0x0000_00FF));
    }

    private void add4(int value) {
        bytecode.add((byte) (value & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 8) & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 16) & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 24) & 0x0000_00FF));
    }

    private void add4(long value) {
        bytecode.add((byte) (value & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 8) & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 16) & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 24) & 0x0000_00FF));
    }

    private void add8(long value) {
        bytecode.add((byte) (value & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 8) & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 16) & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 24) & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 32) & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 40) & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 48) & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 56) & 0x0000_00FF));
    }

    /**
     * Adds an instruction to the bytecode. See {@link Bytecode} for a list of instructions.
     * 
     * @param instruction The instruction
     */
    public void add(int instruction) {
        add1(instruction);
    }

    /**
     * Adds an instruction and an i32 immediate value to the bytecode. See {@link Bytecode} for a
     * list of instructions.
     * 
     * @param instruction The instruction
     * @param value The immediate value
     */
    public void add(int instruction, int value) {
        add1(instruction);
        add4(value);
    }

    /**
     * Adds an instruction and an i64 immediate value to the bytecode. See {@link Bytecode} for a
     * list of instructions.
     * 
     * @param instruction The instruction
     * @param value The immediate value
     */
    public void add(int instruction, long value) {
        add1(instruction);
        add8(value);
    }

    /**
     * Adds an instruction and two i32 immediate values to the bytecode. See {@link Bytecode} for a
     * list of instructions.
     * 
     * @param instruction The instruction
     * @param value1 The first immediate value
     * @param value2 The second immediate value
     */
    public void add(int instruction, int value1, int value2) {
        add1(instruction);
        add4(value1);
        add4(value2);
    }

    /**
     * Adds an instruction and an immediate value to the bytecode. If the value fits into a signed
     * i8 value, the i8 instruction and an i8 value are added. Otherwise, the i32 instruction and an
     * i32 value are added.
     * 
     * @param i8Instruction The i8 instruction
     * @param i32Instruction The i32 instruction
     * @param value The immediate value
     */
    public void addSigned(int i8Instruction, int i32Instruction, int value) {
        if (fitsIntoSignedByte(value)) {
            add1(i8Instruction);
            add1(value);
        } else {
            add1(i32Instruction);
            add4(value);
        }
    }

    /**
     * Adds an instruction and an immediate value to the bytecode. If the value fits into an i8
     * value, the i8 instruction and an i8 value are added. Otherwise, the i64 instruction and an
     * i64 value are added.
     *
     * @param i8Instruction The i8 instruction
     * @param i64Instruction The i64 instruction
     * @param value The immediate value
     */
    public void addSigned(int i8Instruction, int i64Instruction, long value) {
        if (fitsIntoSignedByte(value)) {
            add1(i8Instruction);
            add1(value);
        } else {
            add1(i64Instruction);
            add8(value);
        }
    }

    /**
     * Adds an instruction and an immediate value to the bytecode. If the value fits into a u8
     * value, the u8 instruction and a u8 value are added. Otherwise, the i32 instruction and an i32
     * value are added.
     *
     * @param u8Instruction The u8 instruction
     * @param i32Instruction The i32 instruction
     * @param value The immediate value
     */
    public void addUnsignedImmediateInstruction(int u8Instruction, int i32Instruction, int value) {
        if (fitsIntoUnsignedByte(value)) {
            add1(u8Instruction);
            add1(value);
        } else {
            add1(i32Instruction);
            add4(value);
        }
    }

    /**
     * Adds an instruction and an immediate value to the bytecode. If the value fits into a u8
     * value, the u8 instruction and a u8 value are added. If the value fits into a u32 value, the
     * u32 instruction and a u32 value are added. Otherwise, the i64 instruction and an i64 value
     * are added.
     * 
     * @param u8Instruction The u8 instruction
     * @param u32Instruction The u32 instruction
     * @param i64Instruction The i64 instruction
     * @param value The immediate value
     */
    public void addUnsignedImmediateInstruction(int u8Instruction, int u32Instruction, int i64Instruction, long value) {
        if (fitsIntoUnsignedByte(value)) {
            add1(u8Instruction);
            add1(value);
        } else if (fitsIntoUnsignedInt(value)) {
            add1(u32Instruction);
            add4(value);
        } else {
            add1(i64Instruction);
            add8(value);
        }
    }

    private int addLabel(int resultCount, int stackSize, int commonResultType, int label) {
        final int location;
        if (resultCount <= 1 && stackSize <= 15) {
            add1(Bytecode.SKIP_LABEL);
            location = bytecode.size();
            add1(label);
            add1(resultCount << 7 | commonResultType | stackSize);
        } else if (resultCount <= 15 && fitsIntoUnsignedByte(stackSize)) {
            add1(Bytecode.SKIP_LABEL_U8);
            add1(4);
            location = bytecode.size();
            add1(label);
            add1(0x40 | commonResultType | resultCount);
            add1(stackSize);
        } else {
            final boolean resultFitsIntoByte = fitsIntoUnsignedByte(resultCount);
            final boolean stackFitsIntoByte = fitsIntoUnsignedByte(stackSize);
            add1(Bytecode.SKIP_LABEL_U8);
            add1(3 + (resultFitsIntoByte ? 1 : 4) + (stackFitsIntoByte ? 1 : 4));
            location = bytecode.size();
            add1(label);
            add1(0xC0 | commonResultType | (resultFitsIntoByte ? 0 : 0x04) | (stackFitsIntoByte ? 0 : 0x01));
            if (resultFitsIntoByte) {
                add1(resultCount);
            } else {
                add4(resultCount);
            }
            if (stackFitsIntoByte) {
                add1(stackSize);
            } else {
                add4(stackSize);
            }
        }
        return location;
    }

    /**
     * Adds a branch label to the bytecode.
     * 
     * @param resultCount The number of results of the block.
     * @param stackSize The stack size at the start of the block.
     * @param commonResultType The most common result type of the result types of the block.
     * @return The location of the label in the bytecode.
     */
    public int addLabel(int resultCount, int stackSize, int commonResultType) {
        return addLabel(resultCount, stackSize, commonResultType, Bytecode.LABEL);
    }

    /**
     * Adds a loop label to the bytecode.
     *
     * @param resultCount The number of results of the loop.
     * @param stackSize The stack size at the start of the loop.
     * @param commonResultType The most common result type of the result types of the loop.
     * @return The location of the label in the bytecode.
     */
    public int addLoopLabel(int resultCount, int stackSize, int commonResultType) {
        return addLabel(resultCount, stackSize, commonResultType, Bytecode.LOOP_LABEL);
    }

    public int addIfLocation() {
        add1(Bytecode.IF);
        final int location = bytecode.size();
        // target
        add4(0);
        // profile
        addProfile();
        return location;
    }

    /**
     * Adds a branch instruction to the bytecode. If the jump offset fits into a signed i8 value, a
     * br_i8 and i8 jump offset is added. Otherwise, a br_i32 and i32 jump offset is added.
     * 
     * @param location The target location of the branch.
     */
    public void addBranch(int location) {
        final int relativeOffset = location - (bytecode.size() + 1);
        if (fitsIntoSignedByte(relativeOffset)) {
            add1(Bytecode.BR_I8);
            add1(relativeOffset);
        } else {
            add1(Bytecode.BR_I32);
            add4(relativeOffset);
        }
    }

    /**
     * Adds a br_i32 instruction to the bytecode and reserves an i32 value for the jump offset.
     * 
     * @return The location of the jump offset to be patched later. (see
     *         {@link #patchLocation(int, int)})
     */
    public int addBranchLocation() {
        add1(Bytecode.BR_I32);
        final int location = bytecode.size();
        add4(0);
        return location;
    }

    /**
     * Adds a conditional branch instruction to the bytecode. If the jump offset fits into a signed
     * i8 value, a br_if_i8 and i8 jump offset is added. Otherwise, a br_if_i32 and i32 jump offset
     * is added. In both cases a profile with a size of 2-byte is added.
     * 
     * @param location The target location of the branch.
     */
    public void addBranchIf(int location) {
        final int relativeOffset = location - (bytecode.size() + 1);
        if (fitsIntoSignedByte(relativeOffset)) {
            add1(Bytecode.BR_IF_I8);
            // target
            add1(relativeOffset);
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
     * Adds a br_if_i32 instruction to the bytecode and reserves an i32 value for the jump offset.
     * In addition, a profile with a size of 2-byte is added.
     * 
     * @return The location of the jump offset to be patched later. (see
     *         {@link #patchLocation(int, int)})
     */
    public int addBranchIfLocation() {
        add1(Bytecode.BR_IF_I32);
        final int location = bytecode.size();
        // target
        add4(0);
        // profile
        addProfile();
        return location;
    }

    /**
     * Adds a branch table instruction to the bytecode. If the size fits into an unsigned i8 value,
     * a br_table_I8 and i8 size is added. Otherwise, a br_table_i32 and i32 size is added. In both
     * cases a profile with a size of 2-byte is added.
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
     *         {@link #patchLocation(int, int)})
     */
    public int addBranchTableItemLocation() {
        final int location = bytecode.size();
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
        bytecode.set(jumpOffsetLocation, (byte) (relativeOffset & 0x0000_00FF));
        bytecode.set(jumpOffsetLocation + 1, (byte) ((relativeOffset >>> 8) & 0x0000_00FF));
        bytecode.set(jumpOffsetLocation + 2, (byte) ((relativeOffset >>> 16) & 0x0000_00FF));
        bytecode.set(jumpOffsetLocation + 3, (byte) ((relativeOffset >>> 24) & 0x0000_00FF));
    }

    /**
     * @return The current location in the bytecode.
     */
    public int location() {
        return bytecode.size();
    }

    /**
     * Adds a call instruction to the bytecode. If the nodeIndex and functionIndex both fit into an
     * unsigned i8 value, a call_i8 and two i8 values are added. Otherwise, a call_i32 and two i32
     * value are added.
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
     * tableIndex all fit into an unsigned i8 value, a call_indirect_i8 and three i8 values are
     * added. Otherwise, a call_indirect_i32 and three i32 values are added. In both cases, a 2-byte
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

    private void addDataHeader(int mode, int length, int globalIndex, long offsetAddress) {
        assert mode == SegmentMode.ACTIVE || mode == SegmentMode.PASSIVE;
        int location = bytecode.size();
        add1(0);
        int flags = mode;
        if (fitsIntoUnsignedByte(length)) {
            flags |= Bytecode.DATA_SEG_LENGTH_U8;
            add1(length);
        } else if (fitsIntoUnsignedShort(length)) {
            flags |= Bytecode.DATA_SEG_LENGTH_U16;
            add2(length);
        } else {
            flags |= Bytecode.DATA_SEG_LENGTH_I32;
            add4(length);
        }
        if (globalIndex != -1) {
            if (fitsIntoUnsignedByte(globalIndex)) {
                flags |= Bytecode.DATA_SEG_GLOBAL_INDEX_U8;
                add1(globalIndex);
            } else if (fitsIntoUnsignedShort(globalIndex)) {
                flags |= Bytecode.DATA_SEG_GLOBAL_INDEX_U16;
                add2(globalIndex);
            } else {
                flags |= Bytecode.DATA_SEG_GLOBAL_INDEX_I32;
                add4(globalIndex);
            }
        }
        if (offsetAddress != -1) {
            if (fitsIntoUnsignedByte(offsetAddress)) {
                flags |= Bytecode.DATA_SEG_OFFSET_ADDRESS_U8;
                add1(offsetAddress);
            } else if (fitsIntoUnsignedShort(offsetAddress)) {
                flags |= Bytecode.DATA_SEG_OFFSET_ADDRESS_U16;
                add2(offsetAddress);
            } else if (fitsIntoUnsignedInt(offsetAddress)) {
                flags |= Bytecode.DATA_SEG_OFFSET_ADDRESS_U32;
                add4(offsetAddress);
            } else {
                flags |= Bytecode.DATA_SEG_OFFSET_ADDRESS_U64;
            }
        }
        bytecode.set(location, (byte) flags);
    }

    /**
     * Adds the header of a data segment to the bytecode.
     * 
     * @param length The length of the data segment
     * @param globalIndex The global index of the data segment, -1 if missing
     * @param offsetAddress The offset address of the data segment, -1 if missing
     */
    public void addDataHeader(int length, int globalIndex, long offsetAddress) {
        addDataHeader(SegmentMode.ACTIVE, length, globalIndex, offsetAddress);
    }

    /**
     * Adds the header of a non-active data segment to the bytecode.
     * 
     * @param mode The segment mode of the data segment
     * @param length The length of the data segment
     */
    public void addDataHeader(int mode, int length) {
        addDataHeader(mode, length, -1, -1);
    }

    /**
     * Adds the runtime header of a data segment to the bytecode.
     * 
     * @param length The length of the data segment
     * @param unsafeMemory If unsafe memory is enabled
     */
    public void addDataRuntimeHeader(int length, boolean unsafeMemory) {
        int location = bytecode.size();
        add1(0);
        int flags = 0;
        if (fitsIntoUnsignedValue(length, 31)) {
            flags |= length << 3;
        } else if (fitsIntoUnsignedByte(length)) {
            flags |= Bytecode.DATA_SEG_RUNTIME_LENGTH_U8;
            add1(length);
        } else if (fitsIntoUnsignedShort(length)) {
            flags |= Bytecode.DATA_SEG_RUNTIME_LENGTH_U16;
            add2(length);
        } else {
            flags |= Bytecode.DATA_SEG_RUNTIME_LENGTH_I32;
            add4(length);
        }
        if (unsafeMemory) {
            add8(0);
        }
        bytecode.set(location, (byte) flags);
    }

    /**
     * Adds the header of an elem segment to the bytecode.
     * 
     * @param mode The segment mode of the elem segment
     * @param count The number of elements in the elem segment
     * @param elemType The type of the elements in the elem segment
     * @param tableIndex The table index of the elem segment
     * @param globalIndex The global index of the elem segment, -1 if missing
     * @param offsetAddress The offset address of the elem segment, -1 if missing
     * @return The location after the header in the bytecode
     */
    public int addElemHeader(int mode, int count, byte elemType, int tableIndex, int globalIndex, int offsetAddress) {
        int location = bytecode.size();
        add1(0);
        final int type;
        switch (elemType) {
            case WasmType.FUNCREF_TYPE:
                type = Bytecode.ELEM_SEG_TYPE_FUNREF;
                break;
            case WasmType.EXTERNREF_TYPE:
                type = Bytecode.ELEM_SEG_TYPE_EXTERNREF;
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        add1(type << 4 | mode);

        int flags = 0;
        if (fitsIntoUnsignedByte(count)) {
            flags |= Bytecode.ELEM_SEG_COUNT_U8;
            add1(count);
        } else if (fitsIntoUnsignedShort(count)) {
            flags |= Bytecode.ELEM_SEG_COUNT_U16;
            add2(count);
        } else {
            flags |= Bytecode.ELEM_SEG_COUNT_I32;
            add4(count);
        }
        if (tableIndex != 0) {
            if (fitsIntoUnsignedByte(tableIndex)) {
                flags |= Bytecode.ELEM_SEG_TABLE_INDEX_U8;
                add1(tableIndex);
            } else if (fitsIntoUnsignedShort(tableIndex)) {
                flags |= Bytecode.ELEM_SEG_TABLE_INDEX_U16;
                add2(tableIndex);
            } else {
                flags |= Bytecode.ELEM_SEG_TABLE_INDEX_I32;
                add4(tableIndex);
            }
        }
        if (globalIndex != -1) {
            if (fitsIntoUnsignedByte(globalIndex)) {
                flags |= Bytecode.ELEM_SEG_GLOBAL_INDEX_U8;
                add1(globalIndex);
            } else if (fitsIntoUnsignedShort(globalIndex)) {
                flags |= Bytecode.ELEM_SEG_GLOBAL_INDEX_U16;
                add2(globalIndex);
            } else {
                flags |= Bytecode.ELEM_SEG_GLOBAL_INDEX_I32;
                add4(globalIndex);
            }
        }
        if (offsetAddress != -1) {
            if (fitsIntoUnsignedByte(offsetAddress)) {
                flags |= Bytecode.ELEM_SEG_OFFSET_ADDRESS_U8;
                add1(offsetAddress);
            } else if (fitsIntoUnsignedShort(offsetAddress)) {
                flags |= Bytecode.ELEM_SEG_OFFSET_ADDRESS_U16;
                add2(offsetAddress);
            } else {
                flags |= Bytecode.ELEM_SEG_OFFSET_ADDRESS_I32;
                add4(offsetAddress);
            }
        }
        bytecode.set(location, (byte) flags);
        return bytecode.size();
    }

    /**
     * Adds a single byte to the bytecode.
     * 
     * @param value The byte that should be added
     */
    public void addByte(byte value) {
        bytecode.add(value);
    }

    /**
     * Adds a null entry to the data of an elem segment.
     */
    public void addElemNull() {
        add1(Bytecode.ELEM_ITEM_TYPE_FUNCTION_INDEX | Bytecode.ELEM_ITEM_NULL_FLAG);
    }

    /**
     * Adds a function index entry to the data of an elem segment.
     * 
     * @param functionIndex The function index of the element in the elem segment
     */
    public void addElemFunctionIndex(int functionIndex) {
        if (functionIndex >= 0 && functionIndex <= 15) {
            add1(Bytecode.ELEM_ITEM_TYPE_FUNCTION_INDEX | Bytecode.ELEM_ITEM_LENGTH_U4 | functionIndex);
        } else if (fitsIntoUnsignedByte(functionIndex)) {
            add1(Bytecode.ELEM_ITEM_TYPE_FUNCTION_INDEX | Bytecode.ELEM_ITEM_LENGTH_U8);
            add1(functionIndex);
        } else if (fitsIntoUnsignedShort(functionIndex)) {
            add1(Bytecode.ELEM_ITEM_TYPE_FUNCTION_INDEX | Bytecode.ELEM_ITEM_LENGTH_U16);
            add2(functionIndex);
        } else {
            add1(Bytecode.ELEM_ITEM_TYPE_FUNCTION_INDEX | Bytecode.ELEM_ITEM_LENGTH_I32);
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
            add1(Bytecode.ELEM_ITEM_TYPE_GLOBAL_INDEX | Bytecode.ELEM_ITEM_LENGTH_U4 | globalIndex);
        } else if (fitsIntoUnsignedByte(globalIndex)) {
            add1(Bytecode.ELEM_ITEM_TYPE_GLOBAL_INDEX | Bytecode.ELEM_ITEM_LENGTH_U8);
            add1(globalIndex);
        } else if (fitsIntoUnsignedShort(globalIndex)) {
            add1(Bytecode.ELEM_ITEM_TYPE_GLOBAL_INDEX | Bytecode.ELEM_ITEM_LENGTH_U16);
            add2(globalIndex);
        } else {
            add1(Bytecode.ELEM_ITEM_TYPE_GLOBAL_INDEX | Bytecode.ELEM_ITEM_LENGTH_I32);
            add4(globalIndex);
        }
    }

    /**
     * Adds additional information about a code entry to the bytecode.
     * 
     * @param functionIndex The function index
     * @param maxStackSize The maximum stack size
     * @param bytecodeStartOffset The start offset in the bytecode
     * @param localCount The number of local values (parameters + locals) of the function
     * @param resultCount The number of result values of the function
     */
    public void addCodeEntry(int functionIndex, int maxStackSize, int bytecodeStartOffset, int localCount, int resultCount) {
        final int location = bytecode.size();
        add1(0);
        int flags = 0;
        if (functionIndex != 0) {
            if (fitsIntoUnsignedByte(functionIndex)) {
                flags |= Bytecode.CODE_ENTRY_FUNCTION_INDEX_U8;
                add1(functionIndex);
            } else if (fitsIntoUnsignedShort(functionIndex)) {
                flags |= Bytecode.CODE_ENTRY_FUNCTION_INDEX_U16;
                add2(functionIndex);
            } else {
                flags |= Bytecode.CODE_ENTRY_FUNCTION_INDEX_I32;
                add4(functionIndex);
            }
        }
        if (maxStackSize != 0) {
            if (fitsIntoUnsignedByte(maxStackSize)) {
                flags |= Bytecode.CODE_ENTRY_MAX_STACK_SIZE_U8;
                add1(maxStackSize);
            } else if (fitsIntoUnsignedShort(maxStackSize)) {
                flags |= Bytecode.CODE_ENTRY_MAX_STACK_SIZE_U16;
                add2(maxStackSize);
            } else {
                flags |= Bytecode.CODE_ENTRY_MAX_STACK_SIZE_I32;
                add4(maxStackSize);
            }
        }
        if (fitsIntoUnsignedByte(bytecodeStartOffset)) {
            flags |= Bytecode.CODE_ENTRY_START_OFFSET_U8;
            add1(bytecodeStartOffset);
        } else if (fitsIntoUnsignedShort(bytecodeStartOffset)) {
            flags |= Bytecode.CODE_ENTRY_START_OFFSET_U16;
            add2(bytecodeStartOffset);
        } else {
            flags |= Bytecode.CODE_ENTRY_START_OFFSET_I32;
            add4(bytecodeStartOffset);
        }
        if (localCount != 0) {
            flags |= Bytecode.CODE_ENTRY_LOCALS_FLAG;
        }
        if (resultCount != 0) {
            flags |= Bytecode.CODE_ENTRY_RESULT_FLAG;
        }
        bytecode.set(location, (byte) flags);
    }

    /**
     * @return A byte array representation of the bytecode.
     */
    public byte[] toArray() {
        return bytecode.toArray();
    }
}
