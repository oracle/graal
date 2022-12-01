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

    private static boolean fitsIntoUnsignedByte(int value) {
        return Integer.compareUnsigned(value, 255) <= 0;
    }

    private static boolean fitsIntoUnsignedShort(int value) {
        return Integer.compareUnsigned(value, 65535) <= 0;
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

    private void add4(int value) {
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
     * i8 value, the i8Instruction and an i8 value is added. Otherwise, the i32Instruction and an
     * i32 value is added.
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
     * Adds an instruction and an immediate value to the bytecode. If the value fits into a signed
     * i8 value, the i8Instruction and an i8 value is added. Otherwise, the i64Instruction and an
     * i64 value is added.
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
     * Adds an instruction and an immediate value to the bytecode. If the value fits into an
     * unsigned i8 value, the i8Instruction and an i8 value is added. Otherwise, the i32Instruction
     * and an i32 value is added.
     *
     * @param i8Instruction The i8 instruction
     * @param i32Instruction The i32 instruction
     * @param value The immediate value
     */
    public void addUnsignedImmediateInstruction(int i8Instruction, int i32Instruction, int value) {
        if (fitsIntoUnsignedByte(value)) {
            add1(i8Instruction);
            add1(value);
        } else {
            add1(i32Instruction);
            add4(value);
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
            add1(Bytecode.SKIP_LABEL_I8);
            add1(4);
            location = bytecode.size();
            add1(label);
            add1(0x40 | commonResultType | resultCount);
            add1(stackSize);
        } else {
            final boolean resultFitsIntoByte = fitsIntoUnsignedByte(resultCount);
            final boolean stackFitsIntoByte = fitsIntoUnsignedByte(stackSize);
            add1(Bytecode.SKIP_LABEL_I8);
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
            add1(Bytecode.BR_TABLE_I8);
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
            add1(Bytecode.CALL_I8);
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
            add1(Bytecode.CALL_INDIRECT_I8);
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

    private int addDataHeader(int mode, int length, int globalIndex, int offsetAddress) {
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
            flags |= Bytecode.DATA_SEG_LENGTH_U32;
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
                flags |= Bytecode.DATA_SEG_GLOBAL_INDEX_U32;
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
            } else {
                flags |= Bytecode.DATA_SEG_OFFSET_ADDRESS_U32;
                add4(offsetAddress);
            }
        }
        bytecode.set(location, (byte) flags);
        return bytecode.size();
    }

    public int addDataHeader(int length, int globalIndex, int offsetAddress) {
        return addDataHeader(SegmentMode.ACTIVE, length, globalIndex, offsetAddress);
    }

    public int addDataHeader(int mode, int length) {
        return addDataHeader(mode, length, -1, -1);
    }

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
            flags |= Bytecode.ELEM_SEG_COUNT_U32;
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
                flags |= Bytecode.ELEM_SEG_TABLE_INDEX_U32;
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
                flags |= Bytecode.ELEM_SEG_GLOBAL_INDEX_U32;
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
                flags |= Bytecode.ELEM_SEG_OFFSET_ADDRESS_U32;
                add4(offsetAddress);
            }
        }
        bytecode.set(location, (byte) flags);
        return bytecode.size();
    }

    public void addByte(byte value) {
        bytecode.add(value);
    }

    public void addElemNull() {
        add1(Bytecode.ELEM_ITEM_TYPE_FUNCTION_INDEX | Bytecode.ELEM_ITEM_NULL_FLAG);
    }

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
            add1(Bytecode.ELEM_ITEM_TYPE_FUNCTION_INDEX | Bytecode.ELEM_ITEM_LENGTH_U32);
            add4(functionIndex);
        }
    }

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
            add1(Bytecode.ELEM_ITEM_TYPE_GLOBAL_INDEX | Bytecode.ELEM_ITEM_LENGTH_U32);
            add4(globalIndex);
        }
    }

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
                flags |= Bytecode.CODE_ENTRY_FUNCTION_INDEX_U32;
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
                flags |= Bytecode.CODE_ENTRY_MAX_STACK_SIZE_U32;
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
            flags |= Bytecode.CODE_ENTRY_START_OFFSET_U32;
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
