/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.interpreter.metadata;

import static com.oracle.svm.interpreter.metadata.Bytecodes.ANEWARRAY;
import static com.oracle.svm.interpreter.metadata.Bytecodes.CHECKCAST;
import static com.oracle.svm.interpreter.metadata.Bytecodes.GETFIELD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.GETSTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INSTANCEOF;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEINTERFACE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKESPECIAL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKESTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDC2_W;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDC_W;
import static com.oracle.svm.interpreter.metadata.Bytecodes.MULTIANEWARRAY;
import static com.oracle.svm.interpreter.metadata.Bytecodes.NEW;
import static com.oracle.svm.interpreter.metadata.Bytecodes.PUTFIELD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.PUTSTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.WIDE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;

/**
 * A utility class that makes iterating over bytecodes and reading operands simpler and less
 * error-prone. For example, it handles the {@link Bytecodes#WIDE} instruction and wide variants of
 * instructions internally.
 *
 * Some accessors have a suffix indicating the type of bytecode it handles, these do <b>NOT</b>
 * handle the {@link Bytecodes#WIDE} modifier. Methods without the numeric suffix will handle their
 * {@link Bytecodes#WIDE} modifier internally, but may be slower.
 */
public final class BytecodeStream {

    private BytecodeStream() {
        throw VMError.shouldNotReachHere("private constructor");
    }

    /**
     * Gets the next bytecode index (no side-effects).
     *
     * @return the next bytecode index
     */
    public static int nextBCI(byte[] code, int curBCI) {
        return curBCI + lengthOf(code, curBCI);
    }

    /**
     * Gets the bytecode index of the end of the code.
     *
     * @return the index of the end of the code
     */
    public static int endBCI(byte[] code) {
        return code.length;
    }

    /**
     * Gets the current opcode. This method will never return the {@link Bytecodes#WIDE WIDE}
     * opcode, but will instead return the opcode that is modified by the {@code WIDE} opcode.
     *
     * @return the current opcode;
     * @see #opcode(byte[], int)
     */
    public static int currentBC(byte[] code, int curBCI) {
        int opcode = opcode(code, curBCI);
        if (opcode == WIDE) {
            return ByteUtils.beU1(code, curBCI + 1);
        } else {
            return opcode;
        }
    }

    /**
     * Gets the current opcode in a volatile fashion. This method will never return the
     * {@link Bytecodes#WIDE WIDE} opcode, but will instead return the opcode that is modified by
     * the {@code WIDE} opcode.
     *
     * @return the current opcode; {@link Bytecodes#END} if at or beyond the end of the code
     */
    public static int currentVolatileBC(byte[] code, int curBCI) {
        int opcode = volatileOpcode(code, curBCI);
        if (opcode == WIDE) {
            return ByteUtils.volatileBeU1(code, curBCI + 1);
        } else {
            return opcode;
        }
    }

    /**
     * Reads the index of a local variable for one of the load or store instructions. The WIDE
     * modifier is handled internally.
     *
     * @return the index of the local variable
     */
    public static int readLocalIndex(byte[] code, int curBCI) {
        // read local variable index for load/store
        if (opcode(code, curBCI) == WIDE) {
            return ByteUtils.beU2(code, curBCI + 2);
        }
        return ByteUtils.beU1(code, curBCI + 1);
    }

    /**
     * Reads the index of a local variable for one of the load or store instructions. The
     * {@link Bytecodes#WIDE} modifier is handled internally.
     *
     * @return the index of the local variable
     */
    public static int readLocalIndex1(byte[] code, int curBCI) {
        // read local variable index for load/store
        return ByteUtils.beU1(code, curBCI + 1);
    }

    /**
     * Reads the index of a local variable for one of the load or store instructions. The
     * {@link Bytecodes#WIDE} modifier is <b>NOT</b> handled internally.
     *
     * @return the index of the local variable
     */
    public static int readLocalIndex2(byte[] code, int curBCI) {
        // read local variable index for load/store
        return ByteUtils.beU2(code, curBCI + 2);
    }

    /**
     * Read the delta for an {@link Bytecodes#IINC} bytecode. The {@link Bytecodes#WIDE} is handled.
     *
     * @return the delta for the {@code IINC}
     */
    public static int readIncrement(byte[] code, int curBCI) {
        // read the delta for the iinc bytecode
        if (opcode(code, curBCI) == WIDE) {
            return ByteUtils.beS2(code, curBCI + 4);
        }
        return ByteUtils.beS1(code, curBCI + 2);
    }

    /**
     * Read the delta for an {@link Bytecodes#IINC} bytecode. The {@link Bytecodes#WIDE} modifier is
     * <b>NOT</b>handled internally.
     *
     * @return the delta for the {@code IINC}
     */
    public static int readIncrement1(byte[] code, int curBCI) {
        // read the delta for the iinc bytecode
        return ByteUtils.beS1(code, curBCI + 2);
    }

    /**
     * Read the delta for a {@link Bytecodes#WIDE} + {@link Bytecodes#IINC} bytecode.
     *
     * @return the delta for the {@code WIDE IINC}
     */
    public static int readIncrement2(byte[] code, int curBCI) {
        // read the delta for the iinc bytecode
        return ByteUtils.beS2(code, curBCI + 4);
    }

    /**
     * Read the destination of a {@link Bytecodes#GOTO} or {@code IF} instructions. Wide bytecodes:
     * {@link Bytecodes#GOTO_W} {@link Bytecodes#JSR_W}, are handled internally.
     *
     * @return the destination bytecode index
     */
    public static int readBranchDest(byte[] code, int curBCI) {
        // reads the destination for a branch bytecode
        int opcode = opcode(code, curBCI);
        if (opcode == Bytecodes.GOTO_W || opcode == Bytecodes.JSR_W) {
            return curBCI + ByteUtils.beS4(code, curBCI + 1);
        } else {
            return curBCI + ByteUtils.beS2(code, curBCI + 1);
        }
    }

    /**
     * Read the destination of a {@link Bytecodes#GOTO_W} or {@code JSR_W} instructions.
     *
     * @return the destination bytecode index
     */
    public static int readBranchDest4(byte[] code, int curBCI) {
        // reads the destination for a branch bytecode
        return curBCI + ByteUtils.beS4(code, curBCI + 1);
    }

    /**
     * Read the destination of a {@link Bytecodes#GOTO} or {@code IF} instructions.
     *
     * @return the destination bytecode index
     */
    public static int readBranchDest2(byte[] code, int curBCI) {
        // reads the destination for a branch bytecode
        return curBCI + ByteUtils.beS2(code, curBCI + 1);
    }

    /**
     * Read a signed 4-byte integer from the bytecode stream at the specified bytecode index.
     *
     * @param bci the bytecode index
     * @return the integer value
     */
    public static int readInt(byte[] code, int bci) {
        // reads a 4-byte signed value
        return ByteUtils.beS4(code, bci);
    }

    /**
     * Reads an unsigned, 1-byte value from the bytecode stream at the specified bytecode index.
     *
     * @param bci the bytecode index
     * @return the byte
     */
    public static int readUByte(byte[] code, int bci) {
        return ByteUtils.beU1(code, bci);
    }

    /**
     * Reads a 1-byte constant pool index for the current instruction. Used by
     * {@link Bytecodes#LDC}.
     *
     * @return the constant pool index
     */
    public static char readCPI1(byte[] code, int curBCI) {
        return (char) ByteUtils.beU1(code, curBCI + 1);
    }

    /**
     * Reads a 2-byte constant pool index for the current instruction.
     *
     * @return the constant pool index
     */
    public static char readCPI2(byte[] code, int curBCI) {
        return (char) ByteUtils.beU2(code, curBCI + 1);
    }

    /**
     * Reads a constant pool index for the current instruction.
     *
     * @return the constant pool index
     */
    public static char readCPI(byte[] code, int curBCI) {
        if (opcode(code, curBCI) == LDC) {
            return (char) ByteUtils.beU1(code, curBCI + 1);
        }
        return (char) ByteUtils.beU2(code, curBCI + 1);
    }

    /**
     * Reads a constant pool index for an invokedynamic instruction.
     *
     * @return the constant pool index
     */
    public static int readCPI4(byte[] code, int curBCI) {
        assert opcode(code, curBCI) == Bytecodes.INVOKEDYNAMIC;
        return ByteUtils.beS4(code, curBCI + 1);
    }

    /**
     * Reads a signed, 1-byte value for the current instruction (e.g. BIPUSH).
     *
     * @return the byte
     */
    public static byte readByte(byte[] code, int curBCI) {
        return code[curBCI + 1];
    }

    /**
     * Reads a signed, 2-byte short for the current instruction (e.g. SIPUSH).
     *
     * @return the short value
     */
    public static short readShort(byte[] code, int curBCI) {
        return (short) ByteUtils.beS2(code, curBCI + 1);
    }

    /**
     * Reads an unsigned byte for the current instruction (e.g. SIPUSH). The {@link Bytecodes#WIDE}
     * modifier is <b>NOT</b> handled internally.
     *
     * @return the short value
     */
    public static int opcode(byte[] code, int curBCI) {
        // opcode validity is performed at verification time.
        return ByteUtils.beU1(code, curBCI);
    }

    /**
     * Reads an unsigned byte for the current instruction (e.g. SIPUSH). The {@link Bytecodes#WIDE}
     * modifier is <b>NOT</b> handled internally. It performs an opaque memory access.
     */
    public static int opaqueOpcode(byte[] code, int curBCI) {
        // opcode validity is performed at verification time.
        return ByteUtils.opaqueBeU1(code, curBCI);
    }

    public static int volatileOpcode(byte[] code, int curBCI) {
        // opcode validity is performed at verification time.
        return ByteUtils.volatileBeU1(code, curBCI);
    }

    /**
     * Gets the length of the current bytecode. It takes into account bytecodes with non-constant
     * size and the {@link Bytecodes#WIDE} bytecode.
     */
    private static int lengthOf(byte[] code, int curBCI) {
        int opcode = opcode(code, curBCI);
        int length = Bytecodes.lengthOf(opcode);
        if (length == 0) {
            switch (opcode) {
                case Bytecodes.TABLESWITCH: {
                    return TableSwitch.size(code, curBCI);
                }
                case Bytecodes.LOOKUPSWITCH: {
                    return LookupSwitch.size(code, curBCI);
                }
                case WIDE: {
                    int opc = ByteUtils.beU1(code, curBCI + 1);
                    if (opc == Bytecodes.IINC) {
                        return 6;
                    } else {
                        return 4; // a load or store bytecode
                    }
                }
                default:
                    // Should rather be CompilerAsserts.neverPartOfCompilation() but this is
                    // reachable in SVM.
                    throw VMError.shouldNotReachHere(unknownVariableLengthBytecodeMessage(opcode));
            }
        }
        return length;
    }

    private static String unknownVariableLengthBytecodeMessage(int opcode) {
        return "unknown variable-length bytecode: " + opcode;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void patchAppendixCPI(byte[] code, int curBCI, int appendixCPI) {
        int opcode = opcode(code, curBCI);
        switch (opcode) {
            case INVOKEDYNAMIC:
                code[curBCI + 3] = (byte) ((appendixCPI >> 8) & 0xFF);
                code[curBCI + 4] = (byte) (appendixCPI & 0xFF);
                break;
            default:
                throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    /**
     * No CPI patching at runtime.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void patchCPI(byte[] code, int curBCI, int newCPI) {
        int opcode = opcode(code, curBCI);
        switch (opcode) {
            case WIDE:
                throw VMError.intentionallyUnimplemented();
            case LDC: {
                // Ensure LDC CPI fits in a byte.
                VMError.guarantee(0 <= newCPI && newCPI <= 0xFF);
                code[curBCI + 1] = (byte) newCPI;
                break;
            }
            // @formatter:off
            case INVOKEVIRTUAL:  // fall-through
            case INVOKEINTERFACE:// fall-through
            case INVOKESTATIC:   // fall-through
            case INVOKESPECIAL:  // fall-through
            case INVOKEDYNAMIC:  // fall-through

            case PUTFIELD:       // fall-through
            case PUTSTATIC:      // fall-through

            case GETFIELD:       // fall-through
            case GETSTATIC:      // fall-through
            case LDC2_W:         // fall-through
            case LDC_W:          // fall-through
            case ANEWARRAY:      // fall-through
            case NEW:            // fall-through
            case MULTIANEWARRAY: // fall-through
            case INSTANCEOF:     // fall-through
            case CHECKCAST: {
                VMError.guarantee(0 <= newCPI && newCPI <= 0xFFFF);
                code[curBCI + 1] = (byte) ((newCPI >> 8) & 0xFF);
                code[curBCI + 2] = (byte) (newCPI & 0xFF);
                break;
            }
            // @formatter:on
            default:
                throw VMError.intentionallyUnimplemented();
        }
    }

    /**
     * Updates the specified array with opaque memory order semantics.
     */
    public static void patchOpcodeOpaque(byte[] code, int curBCI, int newOpcode) {
        assert 0 <= newOpcode && newOpcode < Bytecodes.END;
        ByteUtils.opaqueWrite(code, curBCI, (byte) newOpcode);
    }
}
