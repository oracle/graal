package org.graalvm.wasm.parser.bytecode;

import org.graalvm.wasm.collection.ByteArrayList;

import org.graalvm.wasm.constants.Bytecode;

public class BytecodeList {
    private final ByteArrayList bytecode;

    public BytecodeList() {
        bytecode = new ByteArrayList();
    }

    private void add1(int value) {
        bytecode.add((byte) value);
    }

    private void add1(long value) {
        bytecode.add((byte) value);
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

    private boolean fitsIntoSignedByte(int value) {
        return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
    }

    private boolean fitsIntoUnsignedByte(int value) {
        return value <= 255;
    }

    private boolean fitsIntoSignedByte(long value) {
        return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
    }

    public void addInstruction(int instruction) {
        add1(instruction);
    }

    public int addLabel(int results, int stack) {
        final int location;
        if (results <= 1 && stack <= 15) {
            add1(Bytecode.SKIP_LABEL);
            location = bytecode.size();
            add1(Bytecode.LABEL);
            add1(results << 7 | stack);
        } else if (results <= 15 && fitsIntoUnsignedByte(stack)) {
            add1(Bytecode.SKIP_LABEL_I8);
            add1(4);
            location = bytecode.size();
            add1(Bytecode.LABEL);
            add1(0x40 | results);
            add1(stack);
        } else {
            final boolean resultFitsIntoByte = fitsIntoUnsignedByte(results);
            final boolean stackFitsIntoByte = fitsIntoUnsignedByte(stack);
            add1(Bytecode.SKIP_LABEL_I8);
            add1(3 + (resultFitsIntoByte ? 1 : 4) + (stackFitsIntoByte ? 1 : 4));
            location = bytecode.size();
            add1(Bytecode.LABEL);
            add1(0xC0 | (resultFitsIntoByte ? 0 : 0x04) | (stackFitsIntoByte ? 0 : 0x01));
            if (resultFitsIntoByte) {
                add1(results);
            } else {
                add4(results);
            }
            if (stackFitsIntoByte) {
                add1(stack);
            } else {
                add4(stack);
            }
        }
        return location;
    }

    public int addLoopLabel(int stack) {
        final int location;
        if (stack <= 31) {
            add1(Bytecode.SKIP_LABEL);
            location = bytecode.size();
            add1(Bytecode.LOOP_LABEL);
            add1(stack);
        } else {
            final boolean stackFitsIntoByte = fitsIntoUnsignedByte(stack);
            add1(Bytecode.SKIP_LABEL_I8);
            add1(3 + (stackFitsIntoByte ? 1 : 4));
            location = bytecode.size();
            add1(Bytecode.LOOP_LABEL);
            add1(0x80 | (stackFitsIntoByte ? 0 : 0x01));
            if (stackFitsIntoByte) {
                add1(stack);
            } else {
                add4(stack);
            }
        }
        return location;
    }

    public void addBranch(int offset) {
        final int relativeOffset = offset - (bytecode.size() + 1);
        if (fitsIntoSignedByte(relativeOffset)) {
            add1(Bytecode.BR_I8);
            add1(relativeOffset);
        } else {
            add1(Bytecode.BR_I32);
            add4(relativeOffset);
        }
    }

    public int addBranchLocation() {
        add1(Bytecode.BR_I32);
        final int location = bytecode.size();
        add4(0);
        return location;
    }

    public void addBranchIf(int offset) {
        final int relativeOffset = offset - (bytecode.size() + 1);
        if (fitsIntoSignedByte(relativeOffset)) {
            add1(Bytecode.BR_IF_I8);
            // target
            add1(relativeOffset);
            // profile
            add2(0);
        } else {
            add1(Bytecode.BR_IF_I32);
            // target
            add4(relativeOffset);
            // profile
            add2(0);
        }
    }

    public int addBranchIfLocation() {
        add1(Bytecode.BR_IF_I32);
        final int location = bytecode.size();
        // target
        add4(0);
        // profile
        add2(0);
        return location;
    }

    public void addBranchTable(int size) {
        if (fitsIntoSignedByte(size)) {
            add1(Bytecode.BR_TABLE_I8);
            add1(size);
            // profile
            add2(0);
        } else {
            add1(Bytecode.BR_TABLE_I32);
            add4(size);
            // profile
            add2(0);
        }
    }

    public int addBranchTableElementLocation() {
        final int location = bytecode.size();
        // target
        add4(0);
        // profile
        add2(0);
        return location;
    }

    public void patchLocation(int location, int offset) {
        final int relativeOffset = offset - location;
        bytecode.set(location, (byte) (relativeOffset & 0x0000_00FF));
        bytecode.set(location + 1, (byte) ((relativeOffset >>> 8) & 0x0000_00FF));
        bytecode.set(location + 2, (byte) ((relativeOffset >>> 16) & 0x0000_00FF));
        bytecode.set(location + 3, (byte) ((relativeOffset >>> 24) & 0x0000_00FF));
    }

    public int location() {
        return bytecode.size();
    }

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

    public void addIndirectCall(int nodeIndex, int typeIndex, int tableIndex) {
        if (fitsIntoUnsignedByte(nodeIndex) && fitsIntoUnsignedByte(typeIndex) && fitsIntoUnsignedByte(tableIndex)) {
            add1(Bytecode.CALL_INDIRECT_I8);
            add1(nodeIndex);
            add1(typeIndex);
            add1(tableIndex);
            // profile
            add2(0);
        } else {
            add1(Bytecode.CALL_INDIRECT_I32);
            add4(nodeIndex);
            add4(typeIndex);
            add4(tableIndex);
            // profile
            add2(0);
        }
    }

    public void addImmediateInstruction(int instruction, int value) {
        add1(instruction);
        add4(value);
    }

    public void addImmediateInstruction(int instruction, long value) {
        add1(instruction);
        add8(value);
    }

    public void addSignedImmediateInstruction(int i8, int i32, int value) {
        if (fitsIntoSignedByte(value)) {
            add1(i8);
            add1(value);
        } else {
            add1(i32);
            add4(value);
        }
    }

    public void addUnsignedImmediateInstruction(int i8, int i32, int value) {
        if (fitsIntoUnsignedByte(value)) {
            add1(i8);
            add1(value);
        } else {
            add1(i32);
            add4(value);
        }
    }

    public void addSignedImmediateInstruction(int i8, int i64, long value) {
        if (fitsIntoSignedByte(value)) {
            add1(i8);
            add1(value);
        } else {
            add1(i64);
            add8(value);
        }
    }

    public byte[] toArray() {
        return bytecode.toArray();
    }
}
