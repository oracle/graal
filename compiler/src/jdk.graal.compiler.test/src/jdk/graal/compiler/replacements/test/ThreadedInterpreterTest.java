/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.test.AddExports;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

@AddExports("java.base/jdk.internal.misc")
public class ThreadedInterpreterTest extends GraalCompilerTest {

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
    private static final Unsafe unsafe;
    private static final long shortArrayBaseOffset;
    private static final long intArrayBaseOffset;
    private static final int shortArrayIndexScale;
    private static final int intArrayIndexScale;

    static {
        unsafe = Unsafe.getUnsafe();
        // Get base offsets and index scales for arrays
        shortArrayBaseOffset = unsafe.arrayBaseOffset(short[].class);
        intArrayBaseOffset = unsafe.arrayBaseOffset(int[].class);
        shortArrayIndexScale = unsafe.arrayIndexScale(short[].class);
        intArrayIndexScale = unsafe.arrayIndexScale(int[].class);
    }

    private static final int STACK_SIZE = 1024;

    static short[] bytecode = {
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
    static int[] stack = new int[STACK_SIZE];

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

    public static int execute() {
        short[] bytecode = GraalDirectives.opaque(ThreadedInterpreterTest.bytecode);
        int[] stack = GraalDirectives.opaque(ThreadedInterpreterTest.stack);

        long sp = intArrayBaseOffset;    // Stack pointer
        long pc = shortArrayBaseOffset;  // Program counter

        while (true) {
            sp = GraalDirectives.positivePi(sp);
            pc = GraalDirectives.positivePi(pc);

            short opcode = getShortRawOffseted(bytecode, pc);

            switch (GraalDirectives.markThreadedSwitch(opcode)) {
                case LOAD_CONST: {
                    int value = getShortRawOffseted(bytecode, pc, shortArrayIndexScale);
                    putIntRawOffseted(stack, sp, value);

                    pc = pc + 2L * shortArrayIndexScale;
                    sp = sp + intArrayIndexScale;
                    break;
                }
                case LOAD_CONST_0: {
                    putIntRawOffseted(stack, sp, 0);

                    pc = pc + shortArrayIndexScale;
                    sp = sp + intArrayIndexScale;
                    break;
                }
                case LOAD_CONST_1: {
                    putIntRawOffseted(stack, sp, 1);

                    pc = pc + shortArrayIndexScale;
                    sp = sp + intArrayIndexScale;
                    break;
                }
                case ADD: {
                    int b = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    int a = getIntRawOffseted(stack, sp, -(2L * intArrayIndexScale));

                    putIntRawOffseted(stack, sp, -(2L * intArrayIndexScale), a + b);

                    pc = pc + shortArrayIndexScale;
                    sp = sp - intArrayIndexScale;
                    break;
                }
                case DUP: {
                    int value = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    putIntRawOffseted(stack, sp, value);

                    pc = pc + shortArrayIndexScale;
                    sp = sp + intArrayIndexScale;
                    break;
                }
                case SUB: {
                    int b = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    int a = getIntRawOffseted(stack, sp, -(2L * intArrayIndexScale));

                    putIntRawOffseted(stack, sp, -(2L * intArrayIndexScale), a - b);

                    pc = pc + shortArrayIndexScale;
                    sp = sp - intArrayIndexScale;
                    break;
                }
                case MUL: {
                    int b = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    int a = getIntRawOffseted(stack, sp, -(2L * intArrayIndexScale));

                    putIntRawOffseted(stack, sp, -(2L * intArrayIndexScale), a * b);

                    pc = pc + shortArrayIndexScale;
                    sp = sp - intArrayIndexScale;
                    break;
                }
                case DIV: {
                    int b = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    int a = getIntRawOffseted(stack, sp, -(2L * intArrayIndexScale));

                    putIntRawOffseted(stack, sp, -(2L * intArrayIndexScale), a / b);

                    pc = pc + shortArrayIndexScale;
                    sp = sp - intArrayIndexScale;
                    break;
                }
                case SHIFTL: {
                    int b = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    int a = getIntRawOffseted(stack, sp, -(2L * intArrayIndexScale));

                    putIntRawOffseted(stack, sp, -(2L * intArrayIndexScale), a << b);

                    pc = pc + shortArrayIndexScale;
                    sp = sp - intArrayIndexScale;
                    break;
                }
                case SHIFTR: {
                    int b = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    int a = getIntRawOffseted(stack, sp, -(2L * intArrayIndexScale));

                    putIntRawOffseted(stack, sp, -(2L * intArrayIndexScale), a >> b);

                    pc = pc + shortArrayIndexScale;
                    sp = sp - intArrayIndexScale;
                    break;
                }
                case JMP: {
                    long target = getShortRawOffseted(bytecode, pc, shortArrayIndexScale);
                    pc = shortArrayBaseOffset + target * shortArrayIndexScale;

                    break;
                }
                case IF_JMP: {
                    int value = getIntRawOffseted(stack, sp, -intArrayIndexScale);

                    if (value != 0) {
                        long target = getShortRawOffseted(bytecode, pc, shortArrayIndexScale);
                        pc = shortArrayBaseOffset + target * shortArrayIndexScale;
                    } else {
                        pc = pc + 2L * shortArrayIndexScale;
                    }

                    sp = sp - intArrayIndexScale;
                    break;
                }
                case RET:
                    return getIntRawOffseted(stack, sp);
                default:
                    throw new IllegalArgumentException("Unknown opcode");
            }
        }
    }

    public static int executeWithoutThreading() {
        short[] bytecode = GraalDirectives.opaque(ThreadedInterpreterTest.bytecode);
        int[] stack = GraalDirectives.opaque(ThreadedInterpreterTest.stack);

        long sp = intArrayBaseOffset;    // Stack pointer
        long pc = shortArrayBaseOffset;  // Program counter

        while (true) {
            sp = GraalDirectives.positivePi(sp);
            pc = GraalDirectives.positivePi(pc);

            short opcode = getShortRawOffseted(bytecode, pc);

            switch (opcode) {
                case LOAD_CONST: {
                    int value = getShortRawOffseted(bytecode, pc, shortArrayIndexScale);
                    putIntRawOffseted(stack, sp, value);

                    pc = pc + 2L * shortArrayIndexScale;
                    sp = sp + intArrayIndexScale;
                    break;
                }
                case LOAD_CONST_0: {
                    putIntRawOffseted(stack, sp, 0);

                    pc = pc + shortArrayIndexScale;
                    sp = sp + intArrayIndexScale;
                    break;
                }
                case LOAD_CONST_1: {
                    putIntRawOffseted(stack, sp, 1);

                    pc = pc + shortArrayIndexScale;
                    sp = sp + intArrayIndexScale;
                    break;
                }
                case ADD: {
                    int b = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    int a = getIntRawOffseted(stack, sp, -(2L * intArrayIndexScale));

                    putIntRawOffseted(stack, sp, -(2L * intArrayIndexScale), a + b);

                    pc = pc + shortArrayIndexScale;
                    sp = sp - intArrayIndexScale;
                    break;
                }
                case DUP: {
                    int value = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    putIntRawOffseted(stack, sp, value);

                    pc = pc + shortArrayIndexScale;
                    sp = sp + intArrayIndexScale;
                    break;
                }
                case SUB: {
                    int b = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    int a = getIntRawOffseted(stack, sp, -(2L * intArrayIndexScale));

                    putIntRawOffseted(stack, sp, -(2L * intArrayIndexScale), a - b);

                    pc = pc + shortArrayIndexScale;
                    sp = sp - intArrayIndexScale;
                    break;
                }
                case MUL: {
                    int b = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    int a = getIntRawOffseted(stack, sp, -(2L * intArrayIndexScale));

                    putIntRawOffseted(stack, sp, -(2L * intArrayIndexScale), a * b);

                    pc = pc + shortArrayIndexScale;
                    sp = sp - intArrayIndexScale;
                    break;
                }
                case DIV: {
                    int b = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    int a = getIntRawOffseted(stack, sp, -(2L * intArrayIndexScale));

                    putIntRawOffseted(stack, sp, -(2L * intArrayIndexScale), a / b);

                    pc = pc + shortArrayIndexScale;
                    sp = sp - intArrayIndexScale;
                    break;
                }
                case SHIFTL: {
                    int b = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    int a = getIntRawOffseted(stack, sp, -(2L * intArrayIndexScale));

                    putIntRawOffseted(stack, sp, -(2L * intArrayIndexScale), a << b);

                    pc = pc + shortArrayIndexScale;
                    sp = sp - intArrayIndexScale;
                    break;
                }
                case SHIFTR: {
                    int b = getIntRawOffseted(stack, sp, -intArrayIndexScale);
                    int a = getIntRawOffseted(stack, sp, -(2L * intArrayIndexScale));

                    putIntRawOffseted(stack, sp, -(2L * intArrayIndexScale), a >> b);

                    pc = pc + shortArrayIndexScale;
                    sp = sp - intArrayIndexScale;
                    break;
                }
                case JMP: {
                    long target = getShortRawOffseted(bytecode, pc, shortArrayIndexScale);
                    pc = shortArrayBaseOffset + target * shortArrayIndexScale;

                    break;
                }
                case IF_JMP: {
                    int value = getIntRawOffseted(stack, sp, -intArrayIndexScale);

                    if (value != 0) {
                        long target = getShortRawOffseted(bytecode, pc, shortArrayIndexScale);
                        pc = shortArrayBaseOffset + target * shortArrayIndexScale;
                    } else {
                        pc = pc + 2L * shortArrayIndexScale;
                    }

                    sp = sp - intArrayIndexScale;
                    break;
                }
                case RET:
                    return getIntRawOffseted(stack, sp);
                default:
                    throw new IllegalArgumentException("Unknown opcode");
            }
        }
    }

    @Test
    public void testCompilation() throws InvalidInstalledCodeException {
        InstalledCode executeWithThreading = getCode(getResolvedJavaMethod("execute"));
        executeWithThreading.executeVarargs();
        int jumpTableJumpsWithThreading = countPotentialJumpTableJumps(executeWithThreading.getCode());

        InstalledCode executeWithoutThreading = getCode(getResolvedJavaMethod("executeWithoutThreading"));
        executeWithoutThreading.executeVarargs();
        int jumpTableJumpsWithoutThreading = countPotentialJumpTableJumps(executeWithoutThreading.getCode());

        assertTrue(jumpTableJumpsWithThreading > jumpTableJumpsWithoutThreading);
    }

    private int countPotentialJumpTableJumps(byte[] code) {
        int count = 0;
        Architecture arch = getArchitecture();
        if (arch instanceof AArch64) {
            for (int i = 0; i < code.length; i += 4) {
                int instruction = Unsafe.getUnsafe().getInt(code, i + Unsafe.ARRAY_BYTE_BASE_OFFSET);
                if ((instruction & 0x1F000000) == 0x10000000) {
                    // This is an adr instruction, which is used for jump table implementation
                    count++;
                }
            }
        } else {
            Assume.assumeTrue(arch instanceof AMD64);
            boolean opcodeJmpReg = false;
            for (byte b : code) {
                if ((b & 0xFF) == 0xFF) {
                    opcodeJmpReg = true;
                } else {
                    if (opcodeJmpReg) {
                        // preceding byte is 0xFF
                        if ((b & 0xC2) == 0xC2) {
                            count++;
                        }
                    }
                    opcodeJmpReg = false;
                }
            }
        }
        return count;
    }
}
