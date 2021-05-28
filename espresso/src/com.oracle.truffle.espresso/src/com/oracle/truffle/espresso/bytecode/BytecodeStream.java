/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintStream;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.constantpool.ClassConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvokeDynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodRefConstant;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * A utility class that makes iterating over bytecodes and reading operands simpler and less error
 * prone. For example, it handles the {@link Bytecodes#WIDE} instruction and wide variants of
 * instructions internally.
 *
 * Some accessors have a suffix indicating the type of bytecode it handles, these do <b>NOT</b>
 * handle the {@link Bytecodes#WIDE} modifier. Methods without the numeric suffix will handle the
 * {@link Bytecodes#WIDE} modifier internally, but may be slower.
 */
public final class BytecodeStream {

    @CompilationFinal(dimensions = 1) //
    private final byte[] code;

    /**
     * Creates a new {@code BytecodeStream} for the specified bytecode.
     *
     * @param code the array of bytes that contains the bytecode
     */
    public BytecodeStream(final byte[] code) {
        assert code != null;
        this.code = code;
    }

    /**
     * Gets the next bytecode index (no side-effects).
     *
     * @return the next bytecode index
     */
    public int nextBCI(int curBCI) {
        return curBCI + lengthOf(curBCI);
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
     * @return the current opcode;
     * @see #opcode(int)
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
     * Gets the current opcode in a volatile fashion. This method will never return the
     * {@link Bytecodes#WIDE WIDE} opcode, but will instead return the opcode that is modified by
     * the {@code WIDE} opcode.
     *
     * @return the current opcode; {@link Bytecodes#END} if at or beyond the end of the code
     */
    public int currentVolatileBC(int curBCI) {
        int opcode = volatileOpcode(curBCI);
        if (opcode == Bytecodes.WIDE) {
            return Bytes.volatileBeU1(code, curBCI + 1);
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
     * Reads the index of a local variable for one of the load or store instructions. The
     * {@link Bytecodes#WIDE} modifier is handled internally.
     *
     * @return the index of the local variable
     */
    public int readLocalIndex1(int curBCI) {
        // read local variable index for load/store
        return Bytes.beU1(code, curBCI + 1);
    }

    /**
     * Reads the index of a local variable for one of the load or store instructions. The
     * {@link Bytecodes#WIDE} modifier is <b>NOT</b> handled internally.
     *
     * @return the index of the local variable
     */
    public int readLocalIndex2(int curBCI) {
        // read local variable index for load/store
        return Bytes.beU2(code, curBCI + 2);
    }

    /**
     * Read the delta for an {@link Bytecodes#IINC} bytecode. The {@link Bytecodes#WIDE} is handled.
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
     * Read the delta for an {@link Bytecodes#IINC} bytecode. The {@link Bytecodes#WIDE} modifier is
     * <b>NOT</b>handled internally.
     *
     * @return the delta for the {@code IINC}
     */
    public int readIncrement1(int curBCI) {
        // read the delta for the iinc bytecode
        return Bytes.beS1(code, curBCI + 2);
    }

    /**
     * Read the delta for a {@link Bytecodes#WIDE} + {@link Bytecodes#IINC} bytecode.
     *
     * @return the delta for the {@code WIDE IINC}
     */
    public int readIncrement2(int curBCI) {
        // read the delta for the iinc bytecode
        return Bytes.beS2(code, curBCI + 4);
    }

    /**
     * Read the destination of a {@link Bytecodes#GOTO} or {@code IF} instructions. Wide bytecodes:
     * {@link Bytecodes#GOTO_W} {@link Bytecodes#JSR_W}, are handled internally.
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
     * Read the destination of a {@link Bytecodes#GOTO_W} or {@code JSR_W} instructions.
     *
     * @return the destination bytecode index
     */
    public int readBranchDest4(int curBCI) {
        // reads the destination for a branch bytecode
        return curBCI + Bytes.beS4(code, curBCI + 1);
    }

    /**
     * Read the destination of a {@link Bytecodes#GOTO} or {@code IF} instructions.
     *
     * @return the destination bytecode index
     */
    public int readBranchDest2(int curBCI) {
        // reads the destination for a branch bytecode
        return curBCI + Bytes.beS2(code, curBCI + 1);
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
     * Reads a constant pool index for the current instruction. Wide and short bytecodes are handled
     * internally.
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
     * Reads a constant pool index for the current instruction.
     *
     * @return the constant pool index
     */
    public static char readCPI(byte[] code, int curBCI) {
        if (opcode(code, curBCI) == Bytecodes.LDC) {
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

    /**
     * Reads an unsigned byte for the current instruction (e.g. SIPUSH). The {@link Bytecodes#WIDE}
     * modifier is <b>NOT</b> handled internally.
     *
     * @return the short value
     */
    public int opcode(int curBCI) {
        // opcode validity is performed at verification time.
        return Bytes.beU1(code, curBCI);
    }

    private static int opcode(byte[] code, int curBCI) {
        // opcode validity is performed at verification time.
        return Bytes.beU1(code, curBCI);
    }

    public int volatileOpcode(int curBCI) {
        // opcode validity is performed at verification time.
        return Bytes.volatileBeU1(code, curBCI);
    }

    /**
     * Gets the length of the current bytecode. It takes into account bytecodes with non-constant
     * size and the {@link Bytecodes#WIDE} bytecode.
     */
    private int lengthOf(int curBCI) {
        int length = Bytecodes.lengthOf(opcode(curBCI));
        if (length == 0) {
            switch (opcode(curBCI)) {
                case Bytecodes.TABLESWITCH: {
                    return BytecodeTableSwitch.INSTANCE.size(this, curBCI);
                }
                case Bytecodes.LOOKUPSWITCH: {
                    return BytecodeLookupSwitch.INSTANCE.size(this, curBCI);
                }
                case Bytecodes.WIDE: {
                    int opc = Bytes.beU1(code, curBCI + 1);
                    if (opc == Bytecodes.IINC) {
                        return 6;
                    } else {
                        return 4; // a load or store bytecode
                    }
                }
                default:
                    // Should rather be CompilerAsserts.neverPartOfCompilation() but this is
                    // reachable in SVM.
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw error(opcode(curBCI));
            }
        }
        return length;
    }

    @TruffleBoundary
    private static EspressoError error(int opcode) {
        throw EspressoError.shouldNotReachHere("unknown variable-length bytecode: " + opcode);
    }

    public void printBytecode(Klass klass, PrintStream out) {
        try {
            ConstantPool pool = klass.getConstantPool();
            int bci = 0;
            int nextBCI = 0;
            StringBuilder str = new StringBuilder();
            while (nextBCI < endBCI()) {
                str.setLength(0);
                bci = nextBCI;
                int opcode = currentBC(bci);
                str.append(bci).append(": ").append(Bytecodes.nameOf(opcode)).append(" ");
                nextBCI = nextBCI(bci);
                if (Bytecodes.isBranch(opcode)) {
                    // {bci}: {branch bytecode} {target}
                    str.append(readBranchDest(bci));
                } else if (opcode == Bytecodes.NEW) {
                    // {bci}: new {class name}
                    int cpi = readCPI(bci);
                    ClassConstant cc = (ClassConstant) pool.at(cpi);
                    str.append(cc.getName(pool));
                } else if (opcode == Bytecodes.INVOKEDYNAMIC) {
                    // {bci}: #{bootstrap method index} -> {name}:{signature}
                    int cpi = readCPI(bci);
                    InvokeDynamicConstant idc = (InvokeDynamicConstant) pool.at(cpi);
                    str.append("#").append(idc.getBootstrapMethodAttrIndex()).append(" -> ").append(idc.getName(pool)).append(":").append(idc.getSignature(pool));
                } else if (Bytecodes.isInvoke(opcode)) {
                    // {bci}: invoke{} {class}.{method name}:{method signature}
                    int cpi = readCPI(bci);
                    MethodRefConstant mrc = (MethodRefConstant) pool.at(cpi);
                    str.append(mrc.getHolderKlassName(pool)).append(".").append(mrc.getName(pool)).append(":").append(mrc.getDescriptor(pool));
                } else if (opcode == Bytecodes.TABLESWITCH) {
                    // @formatter:off
                    // checkstyle: stop

                    // {bci}: tableswitch
                    //      {key1}: {target1}
                    //      ...
                    //      {keyN}: {targetN}

                    // @formatter:on
                    // Checkstyle: resume
                    str.append('\n');
                    BytecodeTableSwitch helper = BytecodeTableSwitch.INSTANCE;
                    int low = helper.lowKey(this, bci);
                    int high = helper.highKey(this, bci);
                    for (int i = low; i != high + 1; i++) {
                        str.append('\t').append(i).append(": ").append(helper.targetAt(this, bci, i)).append('\n');
                    }
                    str.append("\tdefault: ").append(helper.defaultTarget(this, bci));
                } else if (opcode == Bytecodes.LOOKUPSWITCH) {
                    // @formatter:off
                    // checkstyle: stop

                    // {bci}: lookupswitch
                    //      {key1}: {target1}
                    //      ...
                    //      {keyN}: {targetN}

                    // @formatter:on
                    // Checkstyle: resume
                    str.append('\n');
                    BytecodeLookupSwitch helper = BytecodeLookupSwitch.INSTANCE;
                    int low = 0;
                    int high = helper.numberOfCases(this, bci) - 1;
                    for (int i = low; i <= high; i++) {
                        str.append('\t').append(helper.keyAt(this, bci, i)).append(": ").append(helper.targetAt(this, bci, i));
                    }
                    str.append("\tdefault: ").append(helper.defaultTarget(this, bci));
                } else if (opcode == Bytecodes.IINC) {
                    str.append(" ").append(readLocalIndex(bci)).append(" ").append(readIncrement(bci));
                } else {
                    // {bci}: {opcode} {corresponding value}
                    if (nextBCI - bci == 2) {
                        str.append(readUByte(bci + 1));
                    }
                    if (nextBCI - bci == 3) {
                        str.append(readShort(bci));
                    }
                    if (nextBCI - bci == 5) {
                        str.append(readInt(bci + 1));
                    }
                }
                out.println(str.toString());
            }
        } catch (Throwable e) {
            throw EspressoError.unexpected("Exception thrown during bytecode printing, aborting...", e);
        }
    }

    public void printRawBytecode(PrintStream out) {
        out.println(Arrays.toString(code));
    }
}
