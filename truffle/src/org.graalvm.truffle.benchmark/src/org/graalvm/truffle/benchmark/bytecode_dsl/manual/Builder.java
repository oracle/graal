/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark.bytecode_dsl.manual;

import static org.graalvm.truffle.benchmark.bytecode_dsl.manual.AccessToken.PUBLIC_TOKEN;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.truffle.benchmark.bytecode_dsl.manual.nodes.AddNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.nodes.ArrayIndexNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.nodes.ArrayLengthNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.nodes.DivNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.nodes.EqNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.nodes.LtNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.nodes.ModNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.nodes.MultNode;

import com.oracle.truffle.api.bytecode.BytecodeDSLAccess;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;

public class Builder {

    static final BytecodeDSLAccess UFA = BytecodeDSLAccess.lookup(PUBLIC_TOKEN, true);
    static final ByteArraySupport BYTES = UFA.getByteArraySupport();

    byte[] output = new byte[16];
    int bci = 0;
    int sp = 0;
    int numLocals = 0;
    int maxSp = 0;
    int numConditionalBranches = 0;
    Map<Object, Integer> constants = new HashMap<>();
    List<Node> nodes = new ArrayList<>();

    public final int createLocal() {
        return numLocals++;
    }

    protected final int createConditionalBranch() {
        return numConditionalBranches++;
    }

    protected void writeByte(byte b) {
        output = ensureSize(output, bci + 1);
        BYTES.putByte(output, bci, b);
        bci += 1;
    }

    protected void writeShort(short s) {
        output = ensureSize(output, bci + 2);
        BYTES.putShort(output, bci, s);
        bci += 2;
    }

    protected void writeInt(int i) {
        output = ensureSize(output, bci + 4);
        BYTES.putInt(output, bci, i);
        bci += 4;
    }

    private static byte[] ensureSize(byte[] arr, int size) {
        if (arr.length >= size) {
            return arr;
        } else {
            return Arrays.copyOf(arr, Math.max(size, arr.length * 2));
        }
    }

    public int currentBci() {
        return bci;
    }

    protected void updateSp(int delta) {
        sp = sp + delta;
        if (sp < 0) {
            throw new AssertionError("negative stack pointer");
        } else if (sp > maxSp) {
            maxSp = sp;
        }
    }

    public void loadConstant(Object constant) {
        Integer index = constants.get(constant);
        if (index == null) {
            index = constants.size();
            constants.put(constant, index);
        }
        writeShort(Opcodes.OP_CONST);
        writeInt(index);
        updateSp(1);
    }

    public void loadArg(int index) {
        writeShort(Opcodes.OP_LD_ARG);
        writeInt(index);
        updateSp(1);
    }

    public void storeLocal(int local) {
        writeShort(Opcodes.OP_ST_LOC);
        writeInt(local);
        updateSp(-1);
    }

    public void loadLocal(int local) {
        writeShort(Opcodes.OP_LD_LOC);
        writeInt(local);
        updateSp(1);
    }

    public void emitAdd() {
        writeShort(Opcodes.OP_ADD);
        writeInt(addNode(AddNode.create()));
        updateSp(-1);
    }

    public void emitMult() {
        writeShort(Opcodes.OP_MULT);
        writeInt(addNode(MultNode.create()));
        updateSp(-1);
    }

    public void emitDiv() {
        writeShort(Opcodes.OP_DIV);
        writeInt(addNode(DivNode.create()));
        updateSp(-1);
    }

    public void emitMod() {
        writeShort(Opcodes.OP_MOD);
        writeInt(addNode(ModNode.create()));
        updateSp(-1);
    }

    public void emitLessThan() {
        writeShort(Opcodes.OP_LESS);
        writeInt(addNode(LtNode.create()));
        updateSp(-1);
    }

    public void emitEq() {
        writeShort(Opcodes.OP_EQ);
        writeInt(addNode(EqNode.create()));
        updateSp(-1);
    }

    public void emitArrayLength() {
        writeShort(Opcodes.OP_ARRAY_LEN);
        writeInt(addNode(ArrayLengthNode.create()));
    }

    public void emitArrayIndex() {
        writeShort(Opcodes.OP_ARRAY_INDEX);
        writeInt(addNode(ArrayIndexNode.create()));
        updateSp(-1);
    }

    public void emitJump(int target) {
        writeShort(Opcodes.OP_JUMP);
        writeInt(target);
    }

    public int emitJump() {
        int jumpBci = currentBci();
        writeShort(Opcodes.OP_JUMP);
        writeInt(-1);
        return jumpBci; // to patch later
    }

    public void patchJump(int jumpBci, int target) {
        if (BYTES.getShort(output, jumpBci) != Opcodes.OP_JUMP) {
            throw new AssertionError("Tried to patch jump target for non-jump instruction.");
        }
        BYTES.putInt(output, jumpBci + 2, target);
    }

    public void emitJumpFalse(int target) {
        writeShort(Opcodes.OP_JUMP_FALSE);
        writeInt(target);
        writeInt(createConditionalBranch());
        updateSp(-1);

    }

    public int emitJumpFalse() {
        int jumpFalseBci = currentBci();
        writeShort(Opcodes.OP_JUMP_FALSE);
        writeInt(-1);
        writeInt(createConditionalBranch());
        updateSp(-1);
        return jumpFalseBci; // to patch later
    }

    public void patchJumpFalse(int jumpFalseBci, int target) {
        if (BYTES.getShort(output, jumpFalseBci) != Opcodes.OP_JUMP_FALSE) {
            throw new AssertionError("Tried to patch jump target for non-jump instruction.");
        }
        BYTES.putInt(output, jumpFalseBci + 2, target);
    }

    public void emitReturn() {
        writeShort(Opcodes.OP_RETURN);
        updateSp(-1);
    }

    public void emitUnreachable() {
        writeShort(Opcodes.OP_UNREACHABLE);
    }

    public byte[] getBytecode() {
        return Arrays.copyOf(output, bci);
    }

    public FrameDescriptor getFrameDescriptor() {
        FrameDescriptor.Builder fdb = FrameDescriptor.newBuilder();
        fdb.addSlots(numLocals + maxSp, FrameSlotKind.Illegal);
        return fdb.build();
    }

    int addNode(Node n) {
        int index = nodes.size();
        nodes.add(n);
        return index;
    }

    public Object[] getConstants() {
        Object[] result = new Object[constants.size()];
        for (var entry : constants.entrySet()) {
            result[entry.getValue()] = entry.getKey();
        }
        return result;
    }

    public Node[] getNodes() {
        return nodes.toArray(new Node[0]);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

}
