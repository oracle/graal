/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.truffle.test;

import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

public class BytecodeInterpreterPartialEvaluationTest extends PartialEvaluationTest {

    public static class Bytecode {
        public static final byte CONST = 0;
        public static final byte RETURN = 1;
        public static final byte ADD = 2;
        public static final byte IFZERO = 3;
        public static final byte POP = 4;
        public static final byte JMP = 5;
        public static final byte DUP = 6;
    }

    public static boolean TRACE = false;

    public static class Program extends RootNode {
        private final String name;
        @CompilationFinal private final byte[] bytecodes;
        @CompilationFinal private final FrameSlot[] locals;
        @CompilationFinal private final FrameSlot[] stack;

        public Program(String name, byte[] bytecodes, int maxLocals, int maxStack) {
            this.name = name;
            this.bytecodes = bytecodes;
            locals = new FrameSlot[maxLocals];
            stack = new FrameSlot[maxStack];
            for (int i = 0; i < maxLocals; ++i) {
                locals[i] = this.getFrameDescriptor().addFrameSlot("local" + i);
                locals[i].setKind(FrameSlotKind.Int);
            }
            for (int i = 0; i < maxStack; ++i) {
                stack[i] = this.getFrameDescriptor().addFrameSlot("stack" + i);
                stack[i].setKind(FrameSlotKind.Int);
            }
        }

        protected void setInt(VirtualFrame frame, int stackIndex, int value) {
            frame.setInt(stack[stackIndex], value);
        }

        protected int getInt(VirtualFrame frame, int stackIndex) {
            try {
                return frame.getInt(stack[stackIndex]);
            } catch (FrameSlotTypeException e) {
                throw new IllegalStateException("Error accessing stack slot " + stackIndex);
            }
        }

        @Override
        public String toString() {
            return name;
        }

        public void trace(String format, Object... args) {
            if (CompilerDirectives.inInterpreter() && TRACE) {
                System.out.println(String.format(format, args));
            }
        }

        @Override
        @ExplodeLoop(merge = true)
        public Object execute(VirtualFrame frame) {
            trace("Start program");
            int topOfStack = -1;
            int bci = 0;
            while (true) {
                CompilerAsserts.partialEvaluationConstant(bci);
                byte bc = bytecodes[bci];
                byte value = 0;
                switch (bc) {
                    case Bytecode.CONST:
                        value = bytecodes[bci + 1];
                        trace("%d (%d): CONST %s", bci, topOfStack, value);
                        setInt(frame, ++topOfStack, value);
                        bci = bci + 2;
                        continue;

                    case Bytecode.RETURN:
                        trace("%d (%d): RETURN", bci, topOfStack);
                        return getInt(frame, topOfStack);

                    case Bytecode.ADD: {
                        int left = getInt(frame, topOfStack);
                        int right = getInt(frame, topOfStack - 1);
                        trace("%d (%d): ADD %d %d", bci, topOfStack, left, right);
                        setInt(frame, topOfStack - 1, left + right);
                        topOfStack--;
                        bci = bci + 1;
                        continue;
                    }

                    case Bytecode.IFZERO:
                        trace("%d (%d): IFZERO", bci, topOfStack);
                        if (getInt(frame, topOfStack--) == 0) {
                            bci = bytecodes[bci + 1];
                            continue;
                        } else {
                            bci = bci + 2;
                            continue;
                        }

                    case Bytecode.POP:
                        trace("%d (%d): POP", bci, topOfStack);
                        topOfStack--;
                        bci++;
                        continue;

                    case Bytecode.JMP:
                        trace("%d (%d): JMP", bci, topOfStack);
                        bci = bytecodes[bci + 1];
                        continue;

                    case Bytecode.DUP:
                        trace("%d (%d): DUP", bci, topOfStack);
                        setInt(frame, topOfStack + 1, getInt(frame, topOfStack));
                        topOfStack++;
                        bci++;
                        continue;
                }
            }
        }
    }

    public static Object constant42() {
        return 42;
    }

    private static void assertReturns42(RootNode program) {
        Assert.assertEquals(Integer.valueOf(42), Truffle.getRuntime().createCallTarget(program).call());
    }

    private void assertPartialEvalEqualsAndRunsCorrect(RootNode program) {
        assertReturns42(program);
        assertPartialEvalEquals("constant42", program);
    }

    @Test
    public void constReturnProgram() {
        byte[] bytecodes = new byte[]{
        /* 0: */Bytecode.CONST,
        /* 1: */42,
        /* 2: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("constReturnProgram", bytecodes, 0, 2));
    }

    @Test
    public void constAddProgram() {
        byte[] bytecodes = new byte[]{
        /* 0: */Bytecode.CONST,
        /* 1: */40,
        /* 2: */Bytecode.CONST,
        /* 3: */2,
        /* 4: */Bytecode.ADD,
        /* 5: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("constAddProgram", bytecodes, 0, 2));
    }

    @Test
    public void simpleIfProgram() {
        byte[] bytecodes = new byte[]{
        /* 0: */Bytecode.CONST,
        /* 1: */40,
        /* 2: */Bytecode.CONST,
        /* 3: */1,
        /* 4: */Bytecode.IFZERO,
        /* 5: */8,
        /* 6: */Bytecode.CONST,
        /* 7: */42,
        /* 8: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("simpleIfProgram", bytecodes, 0, 3));
    }

    @Test
    public void ifAndPopProgram() {
        byte[] bytecodes = new byte[]{
        /* 0: */Bytecode.CONST,
        /* 1: */40,
        /* 2: */Bytecode.CONST,
        /* 3: */1,
        /* 4: */Bytecode.IFZERO,
        /* 5: */9,
        /* 6: */Bytecode.POP,
        /* 7: */Bytecode.CONST,
        /* 8: */42,
        /* 9: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("ifAndPopProgram", bytecodes, 0, 3));
    }

    @Test
    public void simpleLoopProgram() {
        byte[] bytecodes = new byte[]{
        /* 0: */Bytecode.CONST,
        /* 1: */42,
        /* 2: */Bytecode.CONST,
        /* 3: */-12,
        /* 4: */Bytecode.CONST,
        /* 5: */1,
        /* 6: */Bytecode.ADD,
        /* 7: */Bytecode.DUP,
        /* 8: */Bytecode.IFZERO,
        /* 9: */12,
        /* 10: */Bytecode.JMP,
        /* 11: */4,
        /* 12: */Bytecode.POP,
        /* 13: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("simpleLoopProgram", bytecodes, 0, 3));
    }

    @Test
    public void nestedLoopsProgram() {
        byte[] bytecodes = new byte[]{
        /* 0: */Bytecode.CONST,
        /* 1: */42,
        /* 2: */Bytecode.CONST,
        /* 3: */-2,
        /* 4: */Bytecode.CONST,
        /* 5: */1,
        /* 6: */Bytecode.ADD,
        /* 7: */Bytecode.DUP,
        /* 8: */Bytecode.CONST,
        /* 9: */-2,
        /* 10: */Bytecode.CONST,
        /* 11: */1,
        /* 12: */Bytecode.ADD,
        /* 13: */Bytecode.DUP,
        /* 14: */Bytecode.IFZERO,
        /* 15: */18,
        /* 16: */Bytecode.JMP,
        /* 17: */10,
        /* 18: */Bytecode.POP,
        /* 19: */Bytecode.IFZERO,
        /* 20: */23,
        /* 21: */Bytecode.JMP,
        /* 22: */4,
        /* 23: */Bytecode.POP,
        /* 24: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("nestedLoopsProgram", bytecodes, 0, 6));
    }

    @Test(timeout = 1000)
    public void manyIfsProgram() {
        byte[] bytecodes = new byte[]{
        /* 0: */Bytecode.CONST,
        /* 1: */40,
        /* 2: */Bytecode.CONST,
        /* 3: */1,
        /* 4: */Bytecode.IFZERO,
        /* 5: */8,
        /* 6: */Bytecode.CONST,
        /* 7: */1,
        /* 8: */Bytecode.IFZERO,
        /* 9: */12,
        /* 10: */Bytecode.CONST,
        /* 11: */1,
        /* 12: */Bytecode.IFZERO,
        /* 13: */16,
        /* 14: */Bytecode.CONST,
        /* 15: */1,
        /* 16: */Bytecode.IFZERO,
        /* 17: */20,
        /* 18: */Bytecode.CONST,
        /* 19: */1,
        /* 20: */Bytecode.IFZERO,
        /* 21: */24,
        /* 22: */Bytecode.CONST,
        /* 23: */1,
        /* 24: */Bytecode.IFZERO,
        /* 25: */28,
        /* 26: */Bytecode.CONST,
        /* 27: */1,
        /* 28: */Bytecode.IFZERO,
        /* 29: */32,
        /* 30: */Bytecode.CONST,
        /* 31: */1,
        /* 32: */Bytecode.IFZERO,
        /* 33: */36,
        /* 34: */Bytecode.CONST,
        /* 35: */1,
        /* 36: */Bytecode.IFZERO,
        /* 37: */40,
        /* 38: */Bytecode.CONST,
        /* 39: */1,
        /* 40: */Bytecode.IFZERO,
        /* 41: */44,
        /* 42: */Bytecode.CONST,
        /* 43: */42,
        /* 44: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("manyIfsProgram", bytecodes, 0, 3));
    }

    public abstract static class Inst {
        public abstract boolean execute(VirtualFrame frame);

        public abstract int getTrueSucc();

        public abstract int getFalseSucc();

        public static class Const extends Inst {
            private final FrameSlot slot;
            private final int value;
            private final int next;

            public Const(FrameSlot slot, int value, int next) {
                this.slot = slot;
                this.value = value;
                this.next = next;
            }

            @Override
            public boolean execute(VirtualFrame frame) {
                frame.setInt(slot, value);
                return true;
            }

            @Override
            public int getTrueSucc() {
                return next;
            }

            @Override
            public int getFalseSucc() {
                return next;
            }
        }

        public static class Return extends Inst {
            public Return() {
            }

            @Override
            public boolean execute(VirtualFrame frame) {
                return true;
            }

            @Override
            public int getTrueSucc() {
                return -1;
            }

            @Override
            public int getFalseSucc() {
                return -1;
            }
        }

        public static class IfZero extends Inst {
            private final FrameSlot slot;
            private final int thenInst;
            private final int elseInst;

            public IfZero(FrameSlot slot, int thenInst, int elseInst) {
                this.slot = slot;
                this.thenInst = thenInst;
                this.elseInst = elseInst;
            }

            @Override
            public boolean execute(VirtualFrame frame) {
                return (FrameUtil.getIntSafe(frame, slot) == 0);
            }

            @Override
            public int getTrueSucc() {
                return thenInst;
            }

            @Override
            public int getFalseSucc() {
                return elseInst;
            }
        }

        public static class IfLt extends Inst {
            private final FrameSlot slot1;
            private final FrameSlot slot2;
            private final int thenInst;
            private final int elseInst;

            public IfLt(FrameSlot slot1, FrameSlot slot2, int thenInst, int elseInst) {
                this.slot1 = slot1;
                this.slot2 = slot2;
                this.thenInst = thenInst;
                this.elseInst = elseInst;
            }

            @Override
            public boolean execute(VirtualFrame frame) {
                return (FrameUtil.getIntSafe(frame, slot1) < FrameUtil.getIntSafe(frame, slot2));
            }

            @Override
            public int getTrueSucc() {
                return thenInst;
            }

            @Override
            public int getFalseSucc() {
                return elseInst;
            }
        }
    }

    public static class InstArrayProgram extends RootNode {
        private final String name;
        @CompilationFinal protected final Inst[] inst;
        protected final FrameSlot returnSlot;

        public InstArrayProgram(String name, Inst[] inst, FrameSlot returnSlot, FrameDescriptor fd) {
            super(null, fd);
            this.name = name;
            this.inst = inst;
            this.returnSlot = returnSlot;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        @ExplodeLoop(merge = true)
        public Object execute(VirtualFrame frame) {
            int ip = 0;
            while (ip != -1) {
                CompilerAsserts.partialEvaluationConstant(ip);
                if (inst[ip].execute(frame)) {
                    ip = inst[ip].getTrueSucc();
                } else {
                    ip = inst[ip].getFalseSucc();
                }
            }
            return FrameUtil.getIntSafe(frame, returnSlot);
        }
    }

    @Test
    public void instArraySimpleIfProgram() {
        FrameDescriptor fd = new FrameDescriptor();
        FrameSlot valueSlot = fd.addFrameSlot("value", FrameSlotKind.Int);
        FrameSlot returnSlot = fd.addFrameSlot("return", FrameSlotKind.Int);
        Inst[] inst = new Inst[]{
        /* 0: */new Inst.Const(valueSlot, 1, 1),
        /* 1: */new Inst.IfZero(valueSlot, 2, 4),
        /* 2: */new Inst.Const(returnSlot, 41, 3),
        /* 3: */new Inst.Return(),
        /* 4: */new Inst.Const(returnSlot, 42, 5),
        /* 5: */new Inst.Return()};
        assertPartialEvalEqualsAndRunsCorrect(new InstArrayProgram("instArraySimpleIfProgram", inst, returnSlot, fd));
    }

    /**
     * Slightly modified version to expose a partial evaluation bug with ExplodeLoop(merge=true).
     */
    public static class InstArrayProgram2 extends InstArrayProgram {
        public InstArrayProgram2(String name, Inst[] inst, FrameSlot returnSlot, FrameDescriptor fd) {
            super(name, inst, returnSlot, fd);
        }

        @Override
        @ExplodeLoop(merge = true)
        public Object execute(VirtualFrame frame) {
            int ip = 0;
            while (ip != -1) {
                CompilerAsserts.partialEvaluationConstant(ip);
                if (inst[ip].execute(frame)) {
                    ip = inst[ip].getTrueSucc();
                } else {
                    ip = inst[ip].getFalseSucc();
                }
            }
            if (frame.getArguments().length > 0) {
                return new Random();
            } else {
                return FrameUtil.getIntSafe(frame, returnSlot);
            }
        }
    }

    @Ignore("produces a bad graph")
    @Test
    public void instArraySimpleIfProgram2() {
        FrameDescriptor fd = new FrameDescriptor();
        FrameSlot value1Slot = fd.addFrameSlot("value1", FrameSlotKind.Int);
        FrameSlot value2Slot = fd.addFrameSlot("value2", FrameSlotKind.Int);
        FrameSlot returnSlot = fd.addFrameSlot("return", FrameSlotKind.Int);
        Inst[] inst = new Inst[]{
        /* 0: */new Inst.Const(value1Slot, 100, 1),
        /* 1: */new Inst.Const(value2Slot, 100, 2),
        /* 2: */new Inst.IfLt(value1Slot, value2Slot, 3, 5),
        /* 3: */new Inst.Const(returnSlot, 41, 4),
        /* 4: */new Inst.Return(),
        /* 5: */new Inst.Const(returnSlot, 42, 6),
        /* 6: */new Inst.Return()};
        InstArrayProgram program = new InstArrayProgram2("instArraySimpleIfProgram2", inst, returnSlot, fd);
        program.execute(Truffle.getRuntime().createVirtualFrame(new Object[0], fd));
        program.execute(Truffle.getRuntime().createVirtualFrame(new Object[1], fd));
        assertPartialEvalEqualsAndRunsCorrect(program);
    }
}
