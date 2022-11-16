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

    private void addProfile() {
        bytecode.add((byte) 0);
        bytecode.add((byte) 0);
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

    private static boolean fitsIntoSignedByte(int value) {
        return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
    }

    private static boolean fitsIntoUnsignedByte(int value) {
        return Integer.compareUnsigned(value, 255) <= 0;
    }

    private static boolean fitsIntoSignedByte(long value) {
        return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
    }

    public void addInstruction(int instruction) {
        add1(instruction);
    }

    private int addLabel(int results, int stack, int resultType, int label) {
        final int location;
        if (results <= 1 && stack <= 15) {
            add1(Bytecode.SKIP_LABEL);
            location = bytecode.size();
            add1(label);
            add1(results << 7 | resultType | stack);
        } else if (results <= 15 && fitsIntoUnsignedByte(stack)) {
            add1(Bytecode.SKIP_LABEL_I8);
            add1(4);
            location = bytecode.size();
            add1(label);
            add1(0x40 | resultType | results);
            add1(stack);
        } else {
            final boolean resultFitsIntoByte = fitsIntoUnsignedByte(results);
            final boolean stackFitsIntoByte = fitsIntoUnsignedByte(stack);
            add1(Bytecode.SKIP_LABEL_I8);
            add1(3 + (resultFitsIntoByte ? 1 : 4) + (stackFitsIntoByte ? 1 : 4));
            location = bytecode.size();
            add1(label);
            add1(0xC0 | resultType | (resultFitsIntoByte ? 0 : 0x04) | (stackFitsIntoByte ? 0 : 0x01));
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

    public int addLabel(int results, int stack, int resultType) {
        return addLabel(results, stack, resultType, Bytecode.LABEL);
    }

    public int addLoopLabel(int results, int stack, int resultType) {
        return addLabel(results, stack, resultType, Bytecode.LOOP_LABEL);
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
            addProfile();
        } else {
            add1(Bytecode.BR_IF_I32);
            // target
            add4(relativeOffset);
            // profile
            addProfile();
        }
    }

    public int addBranchIfLocation() {
        add1(Bytecode.BR_IF_I32);
        final int location = bytecode.size();
        // target
        add4(0);
        // profile
        addProfile();
        return location;
    }

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

    public int addBranchTableElementLocation() {
        final int location = bytecode.size();
        // target
        add4(0);
        // profile
        addProfile();
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

    public void addImmediateInstruction(int instruction, int value) {
        add1(instruction);
        add4(value);
    }

    public void addImmediateInstruction(int instruction, long value) {
        add1(instruction);
        add8(value);
    }

    public void addImmediateInstruction(int instruction, int value1, int value2) {
        add1(instruction);
        add4(value1);
        add4(value2);
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
