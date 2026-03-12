/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import static com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterHandlerConfig.Argument.ExpansionKind.MATERIALIZED;
import static com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterHandlerConfig.Argument.ExpansionKind.VIRTUAL;
import static jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilerImpl.Options.OutlineTruffleInterpreterBytecodeHandler;

import java.util.Objects;

import org.junit.Test;

import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterFetchOpcode;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterHandler;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterHandlerConfig;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterHandlerConfig.Argument;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterHandlerConfig.Argument.Field;
import com.oracle.truffle.api.Truffle;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.test.AddExports;
import jdk.internal.misc.Unsafe;

@AddExports("java.base/jdk.internal.misc")
public class SimpleInterpreterTest extends GraalCompilerTest {

    // Define opcodes
    private static final short LOAD_CONST = 0;
    private static final short ADD = 1;
    private static final short SUB = 2;
    private static final short JMP = 3;
    private static final short IF_JMP = 4;
    private static final short MUL = 5;
    private static final short DUP = 6;
    private static final short DIV = 7;
    private static final short SHIFTL = 8;
    private static final short SHIFTR = 9;
    private static final short LOAD_CONST_0 = 10;
    private static final short LOAD_CONST_1 = 11;
    private static final short RET = 12;
    // Unsafe instance
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    private static final long shortArrayBaseOffset = unsafe.arrayBaseOffset(short[].class);
    private static final long intArrayBaseOffset = unsafe.arrayBaseOffset(int[].class);
    private static final int shortArrayIndexScale = unsafe.arrayIndexScale(short[].class);
    private static final int intArrayIndexScale = unsafe.arrayIndexScale(int[].class);

    private static final int STACK_SIZE = 1024;
    private static final Frame preAllocatedFrame = new Frame(new int[STACK_SIZE]);

    static {
        // Initialize TruffleHostEnvironment
        Truffle.getRuntime();
    }

    public static class State {
        long sp; // Stack pointer

        public State(long sp) {
            this.sp = sp;
        }
    }

    public static class Frame {
        final int[] stack;
        Object[] locals;

        public Frame(int[] stack) {
            this.stack = stack;
            this.locals = null;
        }
    }

    private static short getShortRawOffseted(short[] array, long index) {
        return unsafe.getShort(array, index);
    }

    private static short getShortRawOffseted(short[] array, long index, long offset) {
        return unsafe.getShort(array, offset + index);
    }

    private static int getIntRawOffseted(int[] array, long index) {
        return unsafe.getInt(array, index);
    }

    private static int getIntRawOffseted(int[] array, long index, long offset) {
        return unsafe.getInt(array, offset + index);
    }

    private static void putIntRawOffseted(int[] array, long index, int value) {
        unsafe.putInt(array, index, value);
    }

    private static void putIntRawOffseted(int[] array, long index, long offset, int value) {
        unsafe.putInt(array, offset + index, value);
    }

    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(LOAD_CONST)
    public long loadConstHandler(long pc, State s, short[] bytecode, Frame f) {
        int value = getShortRawOffseted(bytecode, pc, shortArrayIndexScale);
        putIntRawOffseted(f.stack, s.sp, value);
        s.sp = s.sp + intArrayIndexScale;
        return pc + 2L * shortArrayIndexScale;
    }

    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(LOAD_CONST_0)
    public long loadConst0Handler(long pc, State s, short[] bytecode, Frame f) {
        putIntRawOffseted(f.stack, s.sp, 0);
        s.sp = s.sp + intArrayIndexScale;
        return pc + shortArrayIndexScale;
    }

    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(LOAD_CONST_1)
    public long loadConst1Handler(long pc, State s, short[] bytecode, Frame f) {
        putIntRawOffseted(f.stack, s.sp, 1);
        s.sp = s.sp + intArrayIndexScale;
        return pc + shortArrayIndexScale;
    }

    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(ADD)
    public long addHandler(long pc, State s, short[] bytecode, Frame f) {
        int b = getIntRawOffseted(f.stack, s.sp, -intArrayIndexScale);
        int a = getIntRawOffseted(f.stack, s.sp, -(2L * intArrayIndexScale));
        putIntRawOffseted(f.stack, s.sp, -(2L * intArrayIndexScale), a + b);
        s.sp = s.sp - intArrayIndexScale;
        return pc + shortArrayIndexScale;
    }

    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(DUP)
    public long dupHandler(long pc, State s, short[] bytecode, Frame f) {
        int value = getIntRawOffseted(f.stack, s.sp, -intArrayIndexScale);
        putIntRawOffseted(f.stack, s.sp, value);
        s.sp = s.sp + intArrayIndexScale;
        return pc + shortArrayIndexScale;
    }

    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(SUB)
    public long subHandler(long pc, State s, short[] bytecode, Frame f) {
        int b = getIntRawOffseted(f.stack, s.sp, -intArrayIndexScale);
        int a = getIntRawOffseted(f.stack, s.sp, -(2L * intArrayIndexScale));
        putIntRawOffseted(f.stack, s.sp, -(2L * intArrayIndexScale), a - b);
        s.sp = s.sp - intArrayIndexScale;
        return pc + shortArrayIndexScale;
    }

    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(MUL)
    public long mulHandler(long pc, State s, short[] bytecode, Frame f) {
        int b = getIntRawOffseted(f.stack, s.sp, -intArrayIndexScale);
        int a = getIntRawOffseted(f.stack, s.sp, -(2L * intArrayIndexScale));
        putIntRawOffseted(f.stack, s.sp, -(2L * intArrayIndexScale), a * b);
        s.sp = s.sp - intArrayIndexScale;
        return pc + shortArrayIndexScale;
    }

    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(DIV)
    public long divHandler(long pc, State s, short[] bytecode, Frame f) {
        int b = getIntRawOffseted(f.stack, s.sp, -intArrayIndexScale);
        int a = getIntRawOffseted(f.stack, s.sp, -(2L * intArrayIndexScale));
        putIntRawOffseted(f.stack, s.sp, -(2L * intArrayIndexScale), a / b);
        s.sp = s.sp - intArrayIndexScale;
        return pc + shortArrayIndexScale;
    }

    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(SHIFTL)
    public long shiftlHandler(long pc, State s, short[] bytecode, Frame f) {
        int b = getIntRawOffseted(f.stack, s.sp, -intArrayIndexScale);
        int a = getIntRawOffseted(f.stack, s.sp, -(2L * intArrayIndexScale));
        putIntRawOffseted(f.stack, s.sp, -(2L * intArrayIndexScale), a << b);
        s.sp = s.sp - intArrayIndexScale;
        return pc + shortArrayIndexScale;
    }

    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(SHIFTR)
    public long shiftrHandler(long pc, State s, short[] bytecode, Frame f) {
        int b = getIntRawOffseted(f.stack, s.sp, -intArrayIndexScale);
        int a = getIntRawOffseted(f.stack, s.sp, -(2L * intArrayIndexScale));
        putIntRawOffseted(f.stack, s.sp, -(2L * intArrayIndexScale), a >> b);
        s.sp = s.sp - intArrayIndexScale;
        return pc + shortArrayIndexScale;
    }

    @SuppressWarnings("unused")
    @BytecodeInterpreterHandler(JMP)
    public long jmpHandler(long pc, State s, short[] bytecode, Frame f) {
        long target = getShortRawOffseted(bytecode, pc, shortArrayIndexScale);
        return shortArrayBaseOffset + target * shortArrayIndexScale;
    }

    @BytecodeInterpreterHandler(IF_JMP)
    public long ifJmpHandler(long pc, State s, short[] bytecode, Frame f) {
        int value = getIntRawOffseted(f.stack, s.sp, -intArrayIndexScale);
        s.sp = s.sp - intArrayIndexScale;

        if (value != 0) {
            long target = getShortRawOffseted(bytecode, pc, shortArrayIndexScale);
            return shortArrayBaseOffset + target * shortArrayIndexScale;
        } else {
            return pc + 2L * shortArrayIndexScale;
        }
    }

    // no side effect
    @SuppressWarnings("unused")
    @BytecodeInterpreterFetchOpcode
    public int nextOpcode(long pc, State s, short[] bytecode, Frame f) {
        return getShortRawOffseted(bytecode, pc);
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = RET, arguments = {
                    @Argument(nonNull = true),
                    @Argument(returnValue = true),
                    @Argument(expand = VIRTUAL),
                    @Argument(nonNull = true),
                    @Argument(expand = MATERIALIZED, nonNull = true, fields = {
                                    @Field(name = "stack", nonNull = true)
                    })
    })
    public int execute(short[] bytecode) {
        Objects.requireNonNull(bytecode);

        long sp = intArrayBaseOffset;    // Stack pointer
        long pc = shortArrayBaseOffset;  // Program counter
        State s = new State(sp);

        while (true) {
            switch (HostCompilerDirectives.markThreadedSwitch(nextOpcode(pc, s, bytecode, preAllocatedFrame))) {
                case LOAD_CONST:
                    pc = loadConstHandler(pc, s, bytecode, preAllocatedFrame);
                    break;
                case LOAD_CONST_0:
                    pc = loadConst0Handler(pc, s, bytecode, preAllocatedFrame);
                    break;
                case LOAD_CONST_1:
                    pc = loadConst1Handler(pc, s, bytecode, preAllocatedFrame);
                    break;
                case ADD:
                    pc = addHandler(pc, s, bytecode, preAllocatedFrame);
                    break;
                case DUP:
                    pc = dupHandler(pc, s, bytecode, preAllocatedFrame);
                    break;
                case SUB:
                    pc = subHandler(pc, s, bytecode, preAllocatedFrame);
                    break;
                case MUL:
                    pc = mulHandler(pc, s, bytecode, preAllocatedFrame);
                    break;
                case DIV:
                    pc = divHandler(pc, s, bytecode, preAllocatedFrame);
                    break;
                case SHIFTL:
                    pc = shiftlHandler(pc, s, bytecode, preAllocatedFrame);
                    break;
                case SHIFTR:
                    pc = shiftrHandler(pc, s, bytecode, preAllocatedFrame);
                    break;
                case JMP:
                    pc = jmpHandler(pc, s, bytecode, preAllocatedFrame);
                    break;
                case IF_JMP:
                    pc = ifJmpHandler(pc, s, bytecode, preAllocatedFrame);
                    break;
                case RET:
                    return getIntRawOffseted(preAllocatedFrame.stack, s.sp);
                default:
                    throw new IllegalArgumentException("Unknown opcode");
            }
        }
    }

    @Test
    public void testExecute() {
        short[] bytecode = {
                        LOAD_CONST, 30000,
                        // Loop start
                        LOAD_CONST, 1,
                        SUB,
                        DUP,
                        LOAD_CONST_1,
                        MUL,
                        LOAD_CONST, 1,
                        DIV,
                        LOAD_CONST_0,
                        SHIFTL,
                        LOAD_CONST, 0,
                        SHIFTR,
                        IF_JMP, 2,        // If counter > 0, jump back to loop start
                        RET// Loop end
        };

        test(new OptionValues(getInitialOptions(), OutlineTruffleInterpreterBytecodeHandler, true), "execute", new Object[]{bytecode});
    }
}
