/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.bytecode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.MethodRefConstant;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;

import java.util.Arrays;

/**
 * A utility class that makes iterating over bytecodes and reading operands simpler and less error
 * prone. For example, it handles the {@link Bytecodes#WIDE} instruction and wide variants of
 * instructions internally.
 */
public final class BytecodeStream {

    @CompilationFinal(dimensions = 1) //
    private final byte[] code;

    private final BytecodeLookupSwitch bytecodeLookupSwitch;
    private final BytecodeTableSwitch bytecodeTableSwitch;

    /**
     * Creates a new {@code BytecodeStream} for the specified bytecode.
     *
     * @param code the array of bytes that contains the bytecode
     */
    public BytecodeStream(final byte[] code) {
        assert code != null;
        this.code = code;
        this.bytecodeLookupSwitch = new BytecodeLookupSwitch(this);
        this.bytecodeTableSwitch = new BytecodeTableSwitch(this);
    }

    /**
     * Advances to the next bytecode.
     */
    public int next(int curBCI) {
        return nextBCI(curBCI);
    }

    /**
     * Gets the next bytecode index (no side-effects).
     *
     * @return the next bytecode index
     */
    public int nextBCI(int curBCI) {
        if (curBCI < code.length) {
            return curBCI + lengthOf(curBCI);
        } else {
            return curBCI;
        }
    }

    /**
     * Gets the bytecode index of the end of the code.
     *
     * @return the index of the end of the code
     */
    public int endBCI() {
        return code.length;
    }

    /**
     * Gets the current opcode. This method will never return the {@link Bytecodes#WIDE WIDE}
     * opcode, but will instead return the opcode that is modified by the {@code WIDE} opcode.
     *
     * @return the current opcode; {@link Bytecodes#END} if at or beyond the end of the code
     */
    public int currentBC(int curBCI) {
        int opcode = opcode(curBCI);
        if (opcode == Bytecodes.WIDE) {
            return Bytes.beU1(code, curBCI + 1);
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
    public int readLocalIndex(int curBCI) {
        // read local variable index for load/store
        if (opcode(curBCI) == Bytecodes.WIDE) {
            return Bytes.beU2(code, curBCI + 2);
        }
        return Bytes.beU1(code, curBCI + 1);
    }

    /**
     * Read the delta for an {@link Bytecodes#IINC} bytecode.
     *
     * @return the delta for the {@code IINC}
     */
    public int readIncrement(int curBCI) {
        // read the delta for the iinc bytecode
        if (opcode(curBCI) == Bytecodes.WIDE) {
            return Bytes.beS2(code, curBCI + 4);
        }
        return Bytes.beS1(code, curBCI + 2);
    }

    /**
     * Read the destination of a {@link Bytecodes#GOTO} or {@code IF} instructions.
     *
     * @return the destination bytecode index
     */
    public int readBranchDest(int curBCI) {
        // reads the destination for a branch bytecode
        int opcode = opcode(curBCI);
        if (opcode == Bytecodes.GOTO_W || opcode == Bytecodes.JSR_W) {
            return curBCI + Bytes.beS4(code, curBCI + 1);
        } else {
            return curBCI + Bytes.beS2(code, curBCI + 1);
        }
    }

    /**
     * Read a signed 4-byte integer from the bytecode stream at the specified bytecode index.
     *
     * @param bci the bytecode index
     * @return the integer value
     */
    public int readInt(int bci) {
        // reads a 4-byte signed value
        return Bytes.beS4(code, bci);
    }

    /**
     * Reads an unsigned, 1-byte value from the bytecode stream at the specified bytecode index.
     *
     * @param bci the bytecode index
     * @return the byte
     */
    public int readUByte(int bci) {
        return Bytes.beU1(code, bci);
    }

    /**
     * Reads a constant pool index for the current instruction.
     *
     * @return the constant pool index
     */
    public char readCPI(int curBCI) {
        if (opcode(curBCI) == Bytecodes.LDC) {
            return (char) Bytes.beU1(code, curBCI + 1);
        }
        return (char) Bytes.beU2(code, curBCI + 1);
    }

    /**
     * Reads a constant pool index for an invokedynamic instruction.
     *
     * @return the constant pool index
     */
    public int readCPI4(int curBCI) {
        assert opcode(curBCI) == Bytecodes.INVOKEDYNAMIC;
        return Bytes.beS4(code, curBCI + 1);
    }

    /**
     * Reads a signed, 1-byte value for the current instruction (e.g. BIPUSH).
     *
     * @return the byte
     */
    public byte readByte(int curBCI) {
        return code[curBCI + 1];
    }

    /**
     * Reads a signed, 2-byte short for the current instruction (e.g. SIPUSH).
     *
     * @return the short value
     */
    public short readShort(int curBCI) {
        return (short) Bytes.beS2(code, curBCI + 1);
    }

    public int opcode(int curBCI) {
        if (curBCI < code.length) {
            // opcode validity is performed at verification time.
            return Bytes.beU1(code, curBCI);
        } else {
            return Bytecodes.END;
        }
    }

    /**
     * Gets the length of the current bytecode.
     */
    private int lengthOf(int curBCI) {
        int length = Bytecodes.lengthOf(opcode(curBCI));
        if (length == 0) {
            switch (opcode(curBCI)) {
                case Bytecodes.TABLESWITCH: {
                    return getBytecodeTableSwitch().size(curBCI);
                }
                case Bytecodes.LOOKUPSWITCH: {
                    return getBytecodeLookupSwitch().size(curBCI);
                }
                case Bytecodes.WIDE: {
                    int opc = Bytes.beU1(code, curBCI + 1);
                    if (opc == Bytecodes.RET) {
                        return 4;
                    } else if (opc == Bytecodes.IINC) {
                        return 6;
                    } else {
                        return 4; // a load or store bytecode
                    }
                }
                default:
                    throw error(opcode(curBCI));
            }
        }
        return length;
    }

    public BytecodeTableSwitch getBytecodeTableSwitch() {
        return bytecodeTableSwitch;
    }

    public BytecodeLookupSwitch getBytecodeLookupSwitch() {
        return bytecodeLookupSwitch;
    }

    @CompilerDirectives.TruffleBoundary
    private static EspressoError error(int opcode) {
        throw EspressoError.shouldNotReachHere("unknown variable-length bytecode: " + opcode);
    }

    public void printBytecode(Klass klass) {
        try {
            int bci = 0;
            int nextBCI = 0;
            String str;
            while (nextBCI < endBCI()) {
                bci = nextBCI;
                int opcode = currentBC(bci);
                str = bci + ": " + Bytecodes.nameOf(opcode) + " ";
                nextBCI = nextBCI(bci);
                if (Bytecodes.isBranch(opcode)) {
                    str = str + readBranchDest(bci);
                } else if (Bytecodes.isInvoke(opcode)) {
                    int CPI = readCPI(bci);
                    ConstantPool pool = klass.getConstantPool();
                    MethodRefConstant mrc = (MethodRefConstant) pool.at(CPI);
                    str = str + mrc.getHolderKlassName(pool) + "." + mrc.getName(pool) + ":" + mrc.getDescriptor(pool);
                } else {
                    if (nextBCI - bci == 2) {
                        str = str + readUByte(bci + 1);
                    }
                    if (nextBCI - bci == 3) {
                        str = str + readShort(bci);
                    }
                    if (nextBCI - bci == 5) {
                        str = str + readInt(bci + 1);
                    }
                }
                System.err.println(str);
            }
        } catch (Throwable e) {
            System.err.println("Exception arised during bytecode printing, aborting...");
        }
    }

    public void printRawBytecode() {
        System.err.println(Arrays.toString(code));
    }
}
