/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.truffle.test;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.MetricKey;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.RootNode;

public class BytecodeInterpreterPartialEvaluationTest extends PartialEvaluationTest {

    public static class Bytecode {
        public static final byte CONST = 0;
        public static final byte RETURN = 1;
        public static final byte ADD = 2;
        public static final byte IFZERO = 3;
        public static final byte POP = 4;
        public static final byte JMP = 5;
        public static final byte DUP = 6;
        public static final byte SWITCH = 7;
    }

    public static boolean TRACE = false;

    /*
     * A method with a non-exploded loop, which goes away after loop unrolling as long as the
     * parameter is a compilation constant. The method is called from multiple places to inject
     * additional loops into the test cases, i.e., to stress the partial evaluator and compiler
     * optimizations.
     */
    static int nonExplodedLoop(int x) {
        if (x >= 0 && x < 50) {
            int result = 0;
            for (int i = 0; i < x; i++) {
                result++;
                if (result > 100) {
                    /* Dead branch because result < 50, just to complicate the loop structure. */
                    CompilerDirectives.transferToInterpreter();
                }
            }
            if (result > 100) {
                /* Dead branch, just to have exception-throwing calls during partial evaluation. */
                try {
                    boundary();
                    boundary();
                } catch (ControlFlowException ex) {
                    CompilerDirectives.transferToInterpreter();
                } catch (RuntimeException ex) {
                    /* A complicated exception handler to stress the loop detection. */
                    for (int i = 0; i < result; i++) {
                        if (i == 42) {
                            throw ex;
                        }
                    }
                    CompilerDirectives.transferToInterpreter();
                }
            }
            return result;
        } else {
            return x;
        }
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    static void boundary() {
    }

    public static class Program extends RootNode {
        private final String name;
        @CompilationFinal(dimensions = 1) private final byte[] bytecodes;
        @CompilationFinal(dimensions = 1) private final FrameSlot[] locals;
        @CompilationFinal(dimensions = 1) private final FrameSlot[] stack;

        public Program(String name, byte[] bytecodes, int maxLocals, int maxStack) {
            super(null);
            this.name = name;
            this.bytecodes = bytecodes;
            locals = new FrameSlot[maxLocals];
            stack = new FrameSlot[maxStack];
            for (int i = 0; i < maxLocals; ++i) {
                locals[i] = this.getFrameDescriptor().addFrameSlot("local" + i);
                this.getFrameDescriptor().setFrameSlotKind(locals[i], FrameSlotKind.Int);
            }
            for (int i = 0; i < maxStack; ++i) {
                stack[i] = this.getFrameDescriptor().addFrameSlot("stack" + i);
                this.getFrameDescriptor().setFrameSlotKind(stack[i], FrameSlotKind.Int);
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
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public Object execute(VirtualFrame frame) {
            trace("Start program");
            int topOfStack = -1;
            int bci = 0;
            int result = 0;
            boolean running = true;
            outer: while (running) {
                CompilerAsserts.partialEvaluationConstant(bci);
                switch (bytecodes[bci]) {
                    case Bytecode.CONST: {
                        byte value = bytecodes[bci + 1];
                        trace("%d (%d): CONST %d", bci, topOfStack, value);

                        topOfStack++;
                        setInt(frame, topOfStack, nonExplodedLoop(value));
                        bci = bci + 2;
                        continue;
                    }
                    case Bytecode.RETURN: {
                        int value = getInt(frame, topOfStack);
                        trace("%d (%d): RETURN %d", bci, topOfStack, value);

                        result = nonExplodedLoop(value);
                        running = false;
                        continue;
                    }
                    case Bytecode.ADD: {
                        int left = getInt(frame, topOfStack);
                        int right = getInt(frame, topOfStack - 1);
                        trace("%d (%d): ADD %d %d", bci, topOfStack, left, right);

                        topOfStack--;
                        setInt(frame, topOfStack, left + right);
                        bci = bci + 1;
                        continue;
                    }
                    case Bytecode.IFZERO: {
                        int value = getInt(frame, topOfStack);
                        byte trueBci = bytecodes[bci + 1];
                        trace("%d (%d): IFZERO %d to %d", bci, topOfStack, value, trueBci);

                        topOfStack--;
                        if (value == 0) {
                            bci = trueBci;
                        } else {
                            bci = bci + 2;
                        }
                        continue;
                    }
                    case Bytecode.SWITCH: {
                        int value = getInt(frame, topOfStack);
                        byte numCases = bytecodes[bci + 1];
                        trace("%d (%d): SWITCH", bci, topOfStack);

                        topOfStack--;
                        for (int i = 0; i < numCases; ++i) {
                            if (value == i) {
                                bci = bytecodes[bci + i + 2];
                                continue outer;
                            }
                        }
                        // Continue with the code after the switch.
                        bci += numCases + 2;
                        continue;
                    }
                    case Bytecode.POP: {
                        int value = getInt(frame, topOfStack);
                        trace("%d (%d): POP %d", bci, topOfStack, value);

                        topOfStack--;
                        bci++;
                        continue;
                    }
                    case Bytecode.JMP: {
                        byte newBci = bytecodes[bci + 1];
                        trace("%d (%d): JMP to %d", bci, topOfStack, newBci);

                        bci = newBci;
                        continue;
                    }
                    case Bytecode.DUP: {
                        int dupValue = getInt(frame, topOfStack);
                        trace("%d (%d): DUP %d", bci, topOfStack, dupValue);

                        topOfStack++;
                        setInt(frame, topOfStack, dupValue);
                        bci++;
                        continue;
                    }
                }
            }
            return nonExplodedLoop(result);
        }
    }

    public static Object constant42() {
        return 42;
    }

    private static void assertReturns42(RootNode program) {
        Object result = Truffle.getRuntime().createCallTarget(program).call();
        Assert.assertEquals(Integer.valueOf(42), result);
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

    @Test
    public void nestedLoopsProgram2() {
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
                        /* 11: */0,
                        /* 12: */Bytecode.IFZERO,
                        /* 13: */17,
                        /* 14: */Bytecode.POP,
                        /* 15: */Bytecode.JMP,
                        /* 16: */30,

                        /* 17: */Bytecode.CONST,
                        /* 18: */1,
                        /* 19: */Bytecode.ADD,
                        /* 10: */Bytecode.DUP,
                        /* 21: */Bytecode.IFZERO,
                        /* 22: */25,
                        /* 23: */Bytecode.JMP,
                        /* 24: */10,
                        /* 25: */Bytecode.POP,
                        /* 26: */Bytecode.IFZERO,
                        /* 27: */30,
                        /* 28: */Bytecode.JMP,
                        /* 29: */4,
                        /* 30: */Bytecode.POP,
                        /* 31: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("nestedLoopsProgram2", bytecodes, 0, 6));
    }

    @Test
    public void nestedLoopsProgram3() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */42,
                        /* 2: */Bytecode.CONST,
                        /* 3: */-2,
                        /* 4: */Bytecode.DUP,

                        /* 5: */Bytecode.POP,
                        /* 6: */Bytecode.DUP,
                        /* 7: */Bytecode.IFZERO,
                        /* 8: */22,

                        /* 9: */Bytecode.CONST,
                        /* 10: */1,
                        /* 11: */Bytecode.ADD,
                        /* 12: */Bytecode.CONST,
                        /* 13: */-2,

                        /* 14: */Bytecode.DUP,
                        /* 15: */Bytecode.IFZERO,
                        /* 16: */5,
                        /* 17: */Bytecode.CONST,
                        /* 18: */1,
                        /* 19: */Bytecode.ADD,
                        /* 20: */Bytecode.JMP,
                        /* 21: */14,

                        /* 22: */Bytecode.POP,
                        /* 23: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("nestedLoopsProgram", bytecodes, 0, 8));
    }

    @Test
    public void irreducibleLoop01() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */0,
                        /* 2: */Bytecode.IFZERO,
                        /* 3: */7,
                        /* 4: */Bytecode.CONST,
                        /* 5: */1,
                        /* 6: */Bytecode.POP,
                        /* 7: */Bytecode.CONST,
                        /* 8: */1,
                        /* 9: */Bytecode.IFZERO,
                        /* 10: */4,
                        /* 11: */Bytecode.CONST,
                        /* 12: */42,
                        /* 13: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("irreducibleLoop01", bytecodes, 0, 3));
    }

    @Test
    public void irreducibleLoop02() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */0,
                        /* 2: */Bytecode.IFZERO,
                        /* 3: */7,
                        /* 4: */Bytecode.CONST,
                        /* 5: */1,
                        /* 6: */Bytecode.POP,
                        /* 7: */Bytecode.CONST,
                        /* 8: */1,
                        /* 9: */Bytecode.IFZERO,
                        /* 10: */4,
                        /* 11: */Bytecode.CONST,
                        /* 12: */1,
                        /* 13: */Bytecode.IFZERO,
                        /* 14: */0,
                        /* 15: */Bytecode.CONST,
                        /* 16: */42,
                        /* 17: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("irreducibleLoop02", bytecodes, 0, 3));
    }

    @Test
    public void irreducibleLoop03() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */1,
                        /* 2: */Bytecode.POP,
                        /* 3: */Bytecode.CONST,
                        /* 4: */0,
                        /* 5: */Bytecode.IFZERO,
                        /* 6: */10,
                        /* 7: */Bytecode.CONST,
                        /* 8: */1,
                        /* 9: */Bytecode.POP,
                        /* 10: */Bytecode.CONST,
                        /* 11: */1,
                        /* 12: */Bytecode.IFZERO,
                        /* 13: */7,
                        /* 14: */Bytecode.CONST,
                        /* 15: */1,
                        /* 16: */Bytecode.IFZERO,
                        /* 17: */3,

                        /* 18: */Bytecode.CONST,
                        /* 19: */1,
                        /* 20: */Bytecode.IFZERO,
                        /* 21: */18,

                        /* 22: */Bytecode.CONST,
                        /* 23: */42,
                        /* 24: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("irreducibleLoop03", bytecodes, 0, 3));
    }

    @Test
    public void irreducibleLoop04() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */0,
                        /* 2: */Bytecode.IFZERO,
                        /* 3: */7,
                        /* 4: */Bytecode.CONST,
                        /* 5: */1,
                        /* 6: */Bytecode.POP,

                        /* 7: */Bytecode.CONST,
                        /* 8: */1,
                        /* 9: */Bytecode.IFZERO,
                        /* 10: */7,

                        /* 11: */Bytecode.CONST,
                        /* 12: */1,
                        /* 13: */Bytecode.IFZERO,
                        /* 14: */4,

                        /* 15: */Bytecode.CONST,
                        /* 16: */1,
                        /* 17: */Bytecode.IFZERO,
                        /* 18: */15,

                        /* 19: */Bytecode.CONST,
                        /* 20: */42,
                        /* 21: */Bytecode.RETURN};
        assertPartialEvalEqualsAndRunsCorrect(new Program("irreducibleLoop04", bytecodes, 0, 3));
    }

    @Test
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
        long[] times = new long[5];
        String[] topPhases = new String[times.length];
        for (int i = 0; i < times.length; i++) {
            long start = System.currentTimeMillis();
            assertPartialEvalEqualsAndRunsCorrect(new Program("manyIfsProgram", bytecodes, 0, 3));
            long duration = System.currentTimeMillis() - start;
            times[i] = duration;
            Map<MetricKey, Long> metrics = lastDebug.getMetricsSnapshot();
            List<Map.Entry<MetricKey, Long>> entries = new ArrayList<>(metrics.entrySet());
            entries.sort((o1, o2) -> ((int) (o2.getValue() - o1.getValue())));
            int printed = 0;
            Formatter buf = new Formatter();
            for (Map.Entry<MetricKey, Long> e : entries) {
                if (printed++ > 20) {
                    break;
                }
                MetricKey key = e.getKey();
                if (key instanceof TimerKey) {
                    TimerKey timer = (TimerKey) key;
                    long value = e.getValue();
                    long ms = timer.getTimeUnit().toMillis(value);
                    buf.format("  %s ms\t%s%n", ms, key.getName());
                }
            }
            topPhases[i] = buf.toString();
        }
        int limit = 15000;
        for (int i = 0; i < times.length; i++) {
            if (times[i] > limit) {
                Formatter msg = new Formatter();
                msg.format("manyIfsProgram iteration %d took %d ms which is longer than the limit of %d ms%n", i, times[i], limit);
                msg.format("%nDetailed info for each iteration%n");
                for (int j = 0; j < times.length; j++) {
                    msg.format("%nIteration %d took %d ms%n", i, times[i]);
                    msg.format("Top phase times in iteration %d:%n%s%n", i, topPhases[i]);
                }
                throw new AssertionError(msg.toString());

            }
        }
        long maxDuration = 0L;
        if (maxDuration > limit) {
            throw new AssertionError("manyIfsProgram took " + maxDuration + " ms which is longer than the limit of " + limit + " ms");
        }
    }

    @Override
    protected OptionValues getOptions() {
        return new OptionValues(super.getOptions(), DebugOptions.Count, "", DebugOptions.Time, "");
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
                frame.setInt(slot, nonExplodedLoop(value));
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
        @CompilationFinal(dimensions = 1) protected final Inst[] inst;
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
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
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
            return nonExplodedLoop(FrameUtil.getIntSafe(frame, returnSlot));
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

    @Test
    @SuppressWarnings("try")
    public void simpleSwitchProgram() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */1,
                        /* 2: */Bytecode.SWITCH,
                        /* 3: */2,
                        /* 4: */9,
                        /* 5: */12,
                        /* 6: */Bytecode.CONST,
                        /* 7: */40,
                        /* 8: */Bytecode.RETURN,
                        /* 9: */Bytecode.CONST,
                        /* 10: */41,
                        /* 11: */Bytecode.RETURN,
                        /* 12: */Bytecode.CONST,
                        /* 13: */42,
                        /* 14: */Bytecode.RETURN};
        Program program = new Program("simpleSwitchProgram", bytecodes, 0, 3);
        assertPartialEvalEqualsAndRunsCorrect(program);
    }

    @Test
    @SuppressWarnings("try")
    public void loopSwitchProgram() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */1,
                        /* 2: */Bytecode.SWITCH,
                        /* 3: */2,
                        /* 4: */0,
                        /* 5: */9,
                        /* 6: */Bytecode.CONST,
                        /* 7: */40,
                        /* 8: */Bytecode.RETURN,
                        /* 9: */Bytecode.CONST,
                        /* 10: */42,
                        /* 11: */Bytecode.RETURN};
        Program program = new Program("loopSwitchProgram", bytecodes, 0, 3);
        assertPartialEvalEqualsAndRunsCorrect(program);
    }
}
