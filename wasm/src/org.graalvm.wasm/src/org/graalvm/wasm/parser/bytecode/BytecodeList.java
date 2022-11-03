package org.graalvm.wasm.parser.bytecode;

import org.graalvm.wasm.collection.ByteArrayList;

import com.oracle.truffle.api.CompilerDirectives;
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

    private void add3(int value) {
        bytecode.add((byte) (value & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 8) & 0x0000_00FF));
        bytecode.add((byte) ((value >>> 16) & 0x0000_00FF));
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

    private void addVariable(int value, int size) {
        switch (size) {
            case 0:
                break;
            case 1:
                add1(value);
                break;
            case 2:
                add2(value);
                break;
            case 3:
                add3(value);
                break;
            case 4:
                add4(value);
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private int numberOfBytes(int value) {
        int number = 0;
        int currentValue = value;
        while (currentValue != 0) {
            number++;
            currentValue = currentValue >> 8;
        }
        return number;
    }

    private boolean fitsIntoByte(int value) {
        return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
    }

    private boolean fitsIntoByte(long value) {
        return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
    }

    public void addInstruction(int instruction) {
        add1(instruction);
    }

    private boolean isLabelType0(int results, int stack) {
        return results <= 1 && stack <= 31;
    }

    private boolean isLabelType1(int results) {
        return results <= 31;
    }

    private int addLabel(int label, int results, int stack) {
        add1(Bytecode.SKIP_LABEL);
        if (isLabelType0(results, stack)) {
            add1(3);
            final int location = bytecode.size();
            add1(label);
            add1(stack << 3 | results << 2);
            return location;
        }
        if (isLabelType1(results)) {
            final int location;
            if (fitsIntoByte(stack)) {
                add1(4);
                location = bytecode.size();
                add1(label);
                add1(results << 3 | 0b001);
                add1(stack);
            } else {
                add1(7);
                location = bytecode.size();
                add1(label);
                add1(results << 3 | 0b101);
                add4(stack);
            }
            return location;
        }
        final int location;
        if (fitsIntoByte(stack)) {
            if (fitsIntoByte(results)) {
                add1(5);
                location = bytecode.size();
                add1(label);
                add1(0b0000_0010);
                add1(results);
                add1(stack);
            } else {
                add1(8);
                location = bytecode.size();
                add1(label);
                add1(0b0000_1010);
                add4(results);
                add1(stack);
            }
        } else {
            if(fitsIntoByte(results)) {
                add1(8);
                location = bytecode.size();
                add1(label);
                add1(0b0000_0110);
                add1(results);
                add4(stack);
            } else {
                add1(11);
                location = bytecode.size();
                add1(label);
                add1(0b0000_1110);
                add4(results);
                add4(stack);
            }
        }
        return location;
    }

    public int addPrimitiveLabel(int results, int stack) {
        return addLabel(Bytecode.LABEL, results, stack);
    }

    public int addReferenceLabel(int results, int stack) {
        return addLabel(Bytecode.LABEL_REF, results, stack);
    }

    public int addUnknownLabel(int results, int stack) {
        return addLabel(Bytecode.LABEL_UNKNOWN, results, stack);
    }

    public int addPrimitiveLoopLabel(int results, int stack) {
        return addLabel(Bytecode.LOOP_LABEL, results, stack);
    }

    public int addReferenceLoopLabel(int results, int stack) {
        return addLabel(Bytecode.LOOP_LABEL_REF, results, stack);
    }

    public int addUnknownLoopLabel(int results, int stack) {
        return addLabel(Bytecode.LOOP_LABEL_UNKNOWN, results, stack);
    }

    public void addBranch(int offset) {
        final int relativeOffset = offset - (bytecode.size() + 1);
        if (fitsIntoByte(relativeOffset)) {
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
        if (fitsIntoByte(relativeOffset)) {
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
        if (fitsIntoByte(size)) {
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
        if (fitsIntoByte(nodeIndex) && fitsIntoByte(functionIndex)) {
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
        if (fitsIntoByte(nodeIndex) && fitsIntoByte(typeIndex) && fitsIntoByte(tableIndex)) {
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

    public void addImmediateInstruction(int i8, int i32, int value) {
        if (fitsIntoByte(value)) {
            add1(i8);
            add1(value);
        } else {
            add1(i32);
            add4(value);
        }
    }

    public void addImmediateInstruction(int i8, int i64, long value) {
        if (fitsIntoByte(value)) {
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
