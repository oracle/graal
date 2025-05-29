/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark.bytecode.manual;

import static org.graalvm.truffle.benchmark.bytecode.manual.AccessToken.PUBLIC_TOKEN;

import java.util.Arrays;

import org.graalvm.truffle.benchmark.bytecode.BenchmarkLanguage;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeDSLAccess;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class ManualBytecodeInterpreters {

    static final short OP_CONST = 1;
    static final short OP_ST_LOC = 2;
    static final short OP_LD_LOC = 3;
    static final short OP_ADD = 4;
    static final short OP_MOD = 5;
    static final short OP_LESS = 6;
    static final short OP_JUMP = 7;
    static final short OP_JUMP_FALSE = 8;
    static final short OP_RETURN = 9;

    public static Builder newBuilder() {
        return new Builder();
    }

    @SuppressWarnings("hiding")
    public static class Builder {

        static final BytecodeDSLAccess UFA = BytecodeDSLAccess.lookup(PUBLIC_TOKEN, true);
        static final ByteArraySupport BYTES = UFA.getByteArraySupport();

        byte[] output = new byte[16];
        int bci = 0;
        int sp = 0;
        int numLocals = 0;
        int maxSp = 0;
        int numConditionalBranches = 0;

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
            if (!(constant instanceof Integer i)) {
                throw new AssertionError("Constant is not an integer: " + constant);
            }
            writeShort(OP_CONST);
            writeInt(i);
            updateSp(1);
        }

        public void storeLocal(int local) {
            writeShort(OP_ST_LOC);
            writeInt(local);
            updateSp(-1);
        }

        public void loadLocal(int local) {
            writeShort(OP_LD_LOC);
            writeInt(local);
            updateSp(1);
        }

        public void emitAdd() {
            writeShort(OP_ADD);
            updateSp(-1);
        }

        public void emitMod() {
            writeShort(OP_MOD);
            updateSp(-1);
        }

        public void emitLessThan() {
            writeShort(OP_LESS);
            updateSp(-1);
        }

        public void emitJump(int target) {
            writeShort(OP_JUMP);
            writeInt(target);
        }

        public int emitJump() {
            int jumpBci = currentBci();
            writeShort(OP_JUMP);
            writeInt(-1);
            return jumpBci; // to patch later
        }

        public void patchJump(int bci, int target) {
            if (BYTES.getShort(output, bci) != OP_JUMP) {
                throw new AssertionError("Tried to patch jump target for non-jump instruction.");
            }
            BYTES.putInt(output, bci + 2, target);
        }

        public void emitJumpFalse(int target) {
            writeShort(OP_JUMP_FALSE);
            writeInt(target);
            writeInt(createConditionalBranch());
            updateSp(-1);

        }

        public int emitJumpFalse() {
            int jumpFalseBci = currentBci();
            writeShort(OP_JUMP_FALSE);
            writeInt(-1);
            writeInt(createConditionalBranch());
            updateSp(-1);
            return jumpFalseBci; // to patch later
        }

        public void patchJumpFalse(int bci, int target) {
            if (BYTES.getShort(output, bci) != OP_JUMP_FALSE) {
                throw new AssertionError("Tried to patch jump target for non-jump instruction.");
            }
            BYTES.putInt(output, bci + 2, target);
        }

        public void emitReturn() {
            writeShort(OP_RETURN);
            updateSp(-1);
        }

        public byte[] getBytecode() {
            return Arrays.copyOf(output, bci);
        }

        public FrameDescriptor getFrameDescriptor() {
            FrameDescriptor.Builder fdb = FrameDescriptor.newBuilder();
            fdb.addSlots(numLocals + maxSp, FrameSlotKind.Illegal);
            return fdb.build();
        }
    }

    public static class ManualBytecodeInterpreter extends UncheckedBytecodeInterpreter {

        protected ManualBytecodeInterpreter(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, byte[] bytes, int numLocals, int numConditionalBranches) {
            super(language, frameDescriptor, bytes, numLocals, numConditionalBranches);
        }

        public static ManualBytecodeInterpreter create(BenchmarkLanguage language, Builder builder) {
            return new ManualBytecodeInterpreter(language, builder.getFrameDescriptor(), builder.getBytecode(), builder.numLocals, builder.numConditionalBranches);
        }

        @Override
        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        protected Object executeAt(VirtualFrame frame, int startBci, int startSp) throws UnexpectedResultException {
            byte[] localBc = bc;
            int[] localBranchProfiles = branchProfiles;
            int bci = startBci;
            int sp = startSp;

            Counter loopCounter = new Counter();

            loop: while (true) {
                short opcode = BYTES.getShort(localBc, bci);
                CompilerAsserts.partialEvaluationConstant(opcode);
                switch (opcode) {
                    // ( -- i)
                    case OP_CONST: {
                        FRAMES.setInt(frame, sp, BYTES.getIntUnaligned(localBc, bci + 2));
                        sp += 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i -- )
                    case OP_ST_LOC: {
                        FRAMES.copy(frame, sp - 1, BYTES.getIntUnaligned(localBc, bci + 2));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // ( -- i)
                    case OP_LD_LOC: {
                        FRAMES.copy(frame, BYTES.getIntUnaligned(localBc, bci + 2), sp);
                        sp += 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i1 i2 -- i3)
                    case OP_ADD: {
                        int lhs = FRAMES.expectInt(frame, sp - 2);
                        int rhs = FRAMES.expectInt(frame, sp - 1);
                        FRAMES.setInt(frame, sp - 2, lhs + rhs);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 2;
                        continue loop;
                    }
                    // (i1 i2 -- i3)
                    case OP_MOD: {
                        int lhs = FRAMES.expectInt(frame, sp - 2);
                        int rhs = FRAMES.expectInt(frame, sp - 1);
                        FRAMES.setInt(frame, sp - 2, lhs % rhs);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 2;
                        continue loop;
                    }
                    // (i1 i2 -- b)
                    case OP_LESS: {
                        int lhs = FRAMES.expectInt(frame, sp - 2);
                        int rhs = FRAMES.expectInt(frame, sp - 1);
                        FRAMES.setBoolean(frame, sp - 2, lhs < rhs);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 2;
                        continue loop;
                    }
                    // ( -- )
                    case OP_JUMP: {
                        int nextBci = BYTES.getIntUnaligned(localBc, bci + 2);
                        CompilerAsserts.partialEvaluationConstant(nextBci);
                        if (nextBci <= bci) {
                            Object result = backwardsJumpCheck(frame, sp, loopCounter, nextBci);
                            if (result != null) {
                                return result;
                            }
                        }
                        bci = nextBci;
                        continue loop;
                    }
                    // (b -- )
                    case OP_JUMP_FALSE: {
                        boolean cond = FRAMES.expectBoolean(frame, sp - 1);
                        int profileIdx = BYTES.getIntUnaligned(localBc, bci + 6);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        if (profileBranch(localBranchProfiles, profileIdx, !cond)) {
                            bci = BYTES.getIntUnaligned(localBc, bci + 2);
                            continue loop;
                        } else {
                            bci += 10;
                            continue loop;
                        }
                    }
                    // (i -- )
                    case OP_RETURN: {
                        return FRAMES.expectInt(frame, sp - 1);
                    }
                    default:
                        CompilerDirectives.shouldNotReachHere();
                }
            }
        }
    }

    public static class ManualCheckedBytecodeInterpreter extends CheckedBytecodeInterpreter {

        protected ManualCheckedBytecodeInterpreter(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, byte[] bytes, int numLocals, int numConditionalBranches) {
            super(language, frameDescriptor, bytes, numLocals, numConditionalBranches);
        }

        public static ManualCheckedBytecodeInterpreter create(BenchmarkLanguage language, Builder builder) {
            return new ManualCheckedBytecodeInterpreter(language, builder.getFrameDescriptor(), builder.getBytecode(), builder.numLocals, builder.numConditionalBranches);
        }

        @Override
        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        protected Object executeAt(VirtualFrame frame, int startBci, int startSp) throws UnexpectedResultException {
            byte[] localBc = bc;
            int[] localBranchProfiles = branchProfiles;
            int bci = startBci;
            int sp = startSp;

            Counter loopCounter = new Counter();

            loop: while (true) {
                short opcode = BYTES.getShort(localBc, bci);
                CompilerAsserts.partialEvaluationConstant(opcode);
                switch (opcode) {
                    // ( -- i)
                    case OP_CONST: {
                        FRAMES.setInt(frame, sp, BYTES.getIntUnaligned(localBc, bci + 2));
                        sp += 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i -- )
                    case OP_ST_LOC: {
                        FRAMES.copy(frame, sp - 1, BYTES.getIntUnaligned(localBc, bci + 2));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // ( -- i)
                    case OP_LD_LOC: {
                        FRAMES.copy(frame, BYTES.getIntUnaligned(localBc, bci + 2), sp);
                        sp += 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i1 i2 -- i3)
                    case OP_ADD: {
                        int lhs = FRAMES.expectInt(frame, sp - 2);
                        int rhs = FRAMES.expectInt(frame, sp - 1);
                        FRAMES.setInt(frame, sp - 2, lhs + rhs);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 2;
                        continue loop;
                    }
                    // (i1 i2 -- i3)
                    case OP_MOD: {
                        int lhs = FRAMES.expectInt(frame, sp - 2);
                        int rhs = FRAMES.expectInt(frame, sp - 1);
                        FRAMES.setInt(frame, sp - 2, lhs % rhs);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 2;
                        continue loop;
                    }
                    // (i1 i2 -- b)
                    case OP_LESS: {
                        int lhs = FRAMES.expectInt(frame, sp - 2);
                        int rhs = FRAMES.expectInt(frame, sp - 1);
                        FRAMES.setBoolean(frame, sp - 2, lhs < rhs);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 2;
                        continue loop;
                    }
                    // ( -- )
                    case OP_JUMP: {
                        int nextBci = BYTES.getIntUnaligned(localBc, bci + 2);
                        CompilerAsserts.partialEvaluationConstant(nextBci);
                        if (nextBci <= bci) {
                            Object result = backwardsJumpCheck(frame, sp, loopCounter, nextBci);
                            if (result != null) {
                                return result;
                            }
                        }
                        bci = nextBci;
                        continue loop;
                    }
                    // (b -- )
                    case OP_JUMP_FALSE: {
                        boolean cond = FRAMES.expectBoolean(frame, sp - 1);
                        int profileIdx = BYTES.getIntUnaligned(localBc, bci + 6);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        if (profileBranch(localBranchProfiles, profileIdx, !cond)) {
                            bci = BYTES.getIntUnaligned(localBc, bci + 2);
                            continue loop;
                        } else {
                            bci += 10;
                            continue loop;
                        }
                    }
                    // (i -- )
                    case OP_RETURN: {
                        return FRAMES.expectInt(frame, sp - 1);
                    }
                    default:
                        CompilerDirectives.shouldNotReachHere();
                }
            }
        }
    }

    public static class ManualBytecodeInterpreterWithoutBE extends UncheckedBytecodeInterpreter {

        protected ManualBytecodeInterpreterWithoutBE(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, byte[] bc, int numLocals, int numConditionalBranches) {
            super(language, frameDescriptor, bc, numLocals, numConditionalBranches);
        }

        public static ManualBytecodeInterpreterWithoutBE create(BenchmarkLanguage language, Builder builder) {
            return new ManualBytecodeInterpreterWithoutBE(language, builder.getFrameDescriptor(), builder.getBytecode(), builder.numLocals, builder.numConditionalBranches);
        }

        @Override
        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        protected Object executeAt(VirtualFrame frame, int startBci, int startSp) {
            byte[] localBc = bc;
            int[] localBranchProfiles = branchProfiles;
            int bci = startBci;
            int sp = startSp;

            Counter loopCounter = new Counter();

            loop: while (true) {
                short opcode = BYTES.getShort(localBc, bci);
                CompilerAsserts.partialEvaluationConstant(opcode);
                switch (opcode) {
                    // ( -- i)
                    case OP_CONST: {
                        FRAMES.setObject(frame, sp, BYTES.getIntUnaligned(localBc, bci + 2));
                        sp += 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i -- )
                    case OP_ST_LOC: {
                        FRAMES.copy(frame, sp - 1, BYTES.getIntUnaligned(localBc, bci + 2));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // ( -- i)
                    case OP_LD_LOC: {
                        FRAMES.copy(frame, BYTES.getIntUnaligned(localBc, bci + 2), sp);
                        sp += 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i1 i2 -- i3)
                    case OP_ADD: {
                        int lhs = (int) FRAMES.getObject(frame, sp - 2);
                        int rhs = (int) FRAMES.getObject(frame, sp - 1);
                        FRAMES.setObject(frame, sp - 2, lhs + rhs);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 2;
                        continue loop;
                    }
                    // (i1 i2 -- i3)
                    case OP_MOD: {
                        int lhs = (int) FRAMES.getObject(frame, sp - 2);
                        int rhs = (int) FRAMES.getObject(frame, sp - 1);
                        FRAMES.setObject(frame, sp - 2, lhs % rhs);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 2;
                        continue loop;
                    }
                    // (i1 i2 -- b)
                    case OP_LESS: {
                        int lhs = (int) FRAMES.getObject(frame, sp - 2);
                        int rhs = (int) FRAMES.getObject(frame, sp - 1);
                        FRAMES.setObject(frame, sp - 2, lhs < rhs);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 2;
                        continue loop;
                    }
                    // ( -- )
                    case OP_JUMP: {
                        int nextBci = BYTES.getIntUnaligned(localBc, bci + 2);
                        CompilerAsserts.partialEvaluationConstant(nextBci);
                        if (nextBci <= bci) {
                            Object result = backwardsJumpCheck(frame, sp, loopCounter, nextBci);
                            if (result != null) {
                                return result;
                            }
                        }
                        bci = nextBci;
                        continue loop;
                    }
                    // (b -- )
                    case OP_JUMP_FALSE: {
                        boolean cond = FRAMES.getObject(frame, sp - 1) == Boolean.TRUE;
                        int profileIdx = BYTES.getIntUnaligned(localBc, bci + 6);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        if (profileBranch(localBranchProfiles, profileIdx, !cond)) {
                            bci = BYTES.getIntUnaligned(localBc, bci + 2);
                            continue loop;
                        } else {
                            bci += 10;
                            continue loop;
                        }
                    }
                    // (i -- )
                    case OP_RETURN: {
                        return FRAMES.getObject(frame, sp - 1);
                    }
                    default:
                        CompilerDirectives.shouldNotReachHere();
                }
            }
        }
    }
}
