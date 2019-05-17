/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.meta.EspressoError;

import java.util.ArrayList;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.AALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.AASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ACONST_NULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ANEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ARETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ARRAYLENGTH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ATHROW;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BIPUSH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BREAKPOINT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CHECKCAST;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCMPG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCMPL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP2_X1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP2_X2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP_X1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP_X2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCMPG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCMPL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCONST_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GETFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GOTO;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GOTO_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2B;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2C;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2S;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IAND;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_4;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_5;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_M1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFGE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFGT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFLE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFLT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNONNULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ACMPEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ACMPNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPGE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPGT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IINC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INSTANCEOF;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEINTERFACE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKESTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISHL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IUSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IXOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.JSR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.JSR_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LAND;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCMP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDC2_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDC_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LOOKUPSWITCH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSHL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LUSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LXOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MONITORENTER;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MONITOREXIT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MULTIANEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NEW;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NOP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.POP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.POP2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.QUICK;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RET;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SIPUSH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SWAP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.TABLESWITCH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.WIDE;

/**
 * Extremely light-weight java bytecode verifier. Performs only very basic verifications.
 *
 * In particular, it checks stack consistency (ie: checks that instructions do not pop an empty
 * stack, or that they do not push further than maxStack) This verifier also performs primitive type
 * checks, where all references are considered to be of the Object class.
 *
 * The primary goal of this verifier is to detect class files that are blatantly ill-formed, and
 * does not replace a complete bytecode verifier.
 */
public class MethodVerifier {
    private final BytecodeStream code;
    private final int maxStack;
    private final ConstantPool pool;
    private final byte[] verified;

    private enum Operand {
        Int,
        Float,
        Double,
        Long,
        Object,
        Void;

        @Override
        public String toString() {
            return opToString(this);
        }
    }

    private static String opToString(Operand o) {
        switch (o) {
            case Int:
                return "I";
            case Float:
                return "F";
            case Double:
                return "D";
            case Long:
                return "L";
            case Object:
                return "A";
            case Void:
                return "V";
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    static private final Operand Int = Operand.Int;
    static private final Operand Float = Operand.Float;
    static private final Operand Double = Operand.Double;
    static private final Operand Long = Operand.Long;
    static private final Operand Object = Operand.Object;
    static private final Operand Void = Operand.Void;

    static private final byte UNREACHABLE = 0;
    static private final byte UNSEEN = 1;
    static private final byte DONE = 2;

    /**
     * Instanciates a MethodVerifier for the given method
     *
     * @param maxStack Maximum amount of operand on the stack during execution
     * @param code Raw bytecode representation for the method to verify
     * @param pool The constant pool to be used with this method.
     */
    private MethodVerifier(int maxStack, byte[] code, ConstantPool pool) {
        this.code = new BytecodeStream(code);
        this.maxStack = maxStack;
        this.pool = pool;
        this.verified = new byte[code.length];
    }

    /**
     * Utility for ease of use in Espresso
     *
     * @param codeAttribute The code attribute of the verified method
     * @param pool The constant pool of the declaring class
     * @return true, or throws ClassFormatError or VerifyError.
     */
    public static boolean verify(CodeAttribute codeAttribute, ConstantPool pool) {
        if (codeAttribute == null) {
            return true;
        }
        return new MethodVerifier(codeAttribute.getMaxStack(), codeAttribute.getCode(), pool).verify();
    }

    /**
     * Performs the verification for the method associated with this MethodVerifier.
     *
     * @return true or throws ClassFormatError or VerifyError
     */
    private synchronized boolean verify() {
        clear();
        if (code.endBCI() == 0) {
            throw new VerifyError("Control flow falls through code end");
        }
        int nextBCI = 0;
        Stack stack = new Stack(maxStack);
        while (verified[nextBCI] != DONE) {
            nextBCI = verifySafe(nextBCI, stack);
            if (nextBCI >= code.endBCI()) {
                throw new VerifyError("Control flow falls through code end");
            }
        }
        return true;
    }

    private void clear() {
        for (int i = 0; i < verified.length; i++) {
            verified[i] = UNREACHABLE;
        }
        int bci = 0;
        while (bci < code.endBCI()) {
            int bc = code.currentBC(bci);
            if (bc > QUICK) {
                throw new VerifyError("invalid bytecode: " + bc);
            }
            verified[bci] = UNSEEN;
            bci = code.nextBCI(bci);
        }
    }

    private boolean branch(int BCI, Stack stack) {
        if (verified[BCI] == UNREACHABLE) {
            throw new VerifyError("Jump to the middle of an instruction: " + BCI);
        }
        if (verified[BCI] == DONE) {
            return true;
        }
        // TODO(garcia) verify stack merging.
        Stack newStack = stack.copy();
        int nextBCI = BCI;
        if (nextBCI >= code.endBCI()) {
            throw new VerifyError("Control flow falls through code end");
        }
        while (verified[nextBCI] != DONE) {
            nextBCI = verifySafe(nextBCI, newStack);
            if (nextBCI >= code.endBCI()) {
                throw new VerifyError("Control flow falls through code end");
            }
        }
        if (verified[BCI] == UNREACHABLE) {
            throw new VerifyError("Control flow falls between instructions: " + BCI);
        }
        return true;
    }

    private int verifySafe(int BCI, Stack stack) {
        try {
            return verify(BCI, stack);
        } catch (IndexOutOfBoundsException e) {
            throw new VerifyError("Inconsistent Stack access!" + e.getMessage());
        }
    }

    private static Operand fromTag(ConstantPool.Tag tag) {
        switch (tag) {
            case INTEGER:
                return Int;
            case FLOAT:
                return Float;
            case LONG:
                return Long;
            case DOUBLE:
                return Double;
            case CLASS:
            case STRING:
            case METHODHANDLE:
            case METHODTYPE:
                return Object;
            default:
                throw new VerifyError("invalid CP load: " + tag);
        }
    }

    private int verify(int BCI, Stack stack) {
        if (verified[BCI] == UNREACHABLE) {
            throw new VerifyError("Jump to the middle of an instruction: " + BCI);
        }
        verified[BCI] = DONE;
        int curOpcode;
        curOpcode = code.currentBC(BCI);
        if (!(curOpcode <= QUICK)) {
            throw new VerifyError("invalid bytecode: " + code.readByte(BCI - 1));
        }
        // @formatter:off
        // Checkstyle: stop
        switch (curOpcode) {
            case NOP: break;
            case ACONST_NULL: stack.pushObj(); break;

            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5: stack.pushInt(); break;

            case LCONST_0:
            case LCONST_1: stack.pushLong(); break;

            case FCONST_0:
            case FCONST_1:
            case FCONST_2: stack.pushFloat(); break;

            case DCONST_0:
            case DCONST_1: stack.pushDouble(); break;

            case BIPUSH: stack.pushInt(); break;
            case SIPUSH: stack.pushInt(); break;

            case LDC:
            case LDC_W:
            case LDC2_W:
                stack.push(fromTag(pool.at(code.readCPI(BCI)).tag()));
                break;

            case ILOAD: stack.pushInt(); break;
            case LLOAD: stack.pushLong(); break;
            case FLOAD: stack.pushFloat(); break;
            case DLOAD: stack.pushDouble(); break;
            case ALOAD: stack.pushObj(); break;

            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3: stack.pushInt(); break;
            case LLOAD_0:
            case LLOAD_1:
            case LLOAD_2:
            case LLOAD_3: stack.pushLong(); break;
            case FLOAD_0:
            case FLOAD_1:
            case FLOAD_2:
            case FLOAD_3: stack.pushFloat(); break;
            case DLOAD_0:
            case DLOAD_1:
            case DLOAD_2:
            case DLOAD_3: stack.pushDouble(); break;
            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3: stack.pushObj(); break;

            case IALOAD: stack.popInt(); stack.popObj(); stack.pushInt(); break;
            case LALOAD: stack.popInt(); stack.popObj(); stack.pushLong(); break;
            case FALOAD: stack.popInt(); stack.popObj(); stack.pushFloat(); break;
            case DALOAD: stack.popInt(); stack.popObj(); stack.pushDouble(); break;
            case AALOAD: stack.popInt(); stack.popObj(); stack.pushObj(); break;
            case BALOAD: stack.popInt(); stack.popObj(); stack.pushInt(); break;
            case CALOAD: stack.popInt(); stack.popObj(); stack.pushInt(); break;
            case SALOAD: stack.popInt(); stack.popObj(); stack.pushInt(); break;

            case ISTORE: stack.popInt(); break;
            case LSTORE: stack.popLong(); break;
            case FSTORE: stack.popFloat(); break;
            case DSTORE: stack.popDouble(); break;
            case ASTORE: stack.popObj(); break;

            case ISTORE_0:
            case ISTORE_1:
            case ISTORE_2:
            case ISTORE_3: stack.popInt(); break;
            case LSTORE_0:
            case LSTORE_1:
            case LSTORE_2:
            case LSTORE_3: stack.popLong(); break;
            case FSTORE_0:
            case FSTORE_1:
            case FSTORE_2:
            case FSTORE_3: stack.popFloat(); break;
            case DSTORE_0:
            case DSTORE_1:
            case DSTORE_2:
            case DSTORE_3: stack.popDouble(); break;
            case ASTORE_0:
            case ASTORE_1:
            case ASTORE_2:
            case ASTORE_3: stack.popObj(); break;

            case IASTORE: stack.popInt(); stack.popInt(); stack.popObj(); break;
            case LASTORE: stack.popLong(); stack.popInt(); stack.popObj(); break;
            case FASTORE: stack.popFloat(); stack.popInt(); stack.popObj(); break;
            case DASTORE: stack.popDouble(); stack.popInt(); stack.popObj(); break;
            case AASTORE: stack.popObj(); stack.popInt(); stack.popObj(); break;
            case BASTORE: stack.popInt(); stack.popInt(); stack.popObj(); break;
            case CASTORE: stack.popInt(); stack.popInt(); stack.popObj(); break;
            case SASTORE: stack.popInt(); stack.popInt(); stack.popObj(); break;

            case POP: stack.pop(); break;
            case POP2: stack.pop2(); break;

            case DUP: stack.dup(); break;
            case DUP_X1: stack.dupx1(); break;
            case DUP_X2: stack.dupx2(); break;
            case DUP2: stack.dup2(); break;
            case DUP2_X1: stack.dup2x1(); break;
            case DUP2_X2: stack.dup2x2(); break;
            case SWAP: stack.swap(); break;

            case IADD: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LADD: stack.popLong(); stack.popLong(); stack.pushLong();break;
            case FADD: stack.popFloat(); stack.popFloat(); stack.pushFloat();break;
            case DADD: stack.popDouble(); stack.popDouble(); stack.pushDouble();break;

            case ISUB: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LSUB: stack.popLong(); stack.popLong(); stack.pushLong();break;
            case FSUB: stack.popFloat(); stack.popFloat(); stack.pushFloat();break;
            case DSUB: stack.popDouble(); stack.popDouble(); stack.pushDouble();break;

            case IMUL: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LMUL: stack.popLong(); stack.popLong(); stack.pushLong();break;
            case FMUL: stack.popFloat(); stack.popFloat(); stack.pushFloat();break;
            case DMUL: stack.popDouble(); stack.popDouble(); stack.pushDouble();break;

            case IDIV: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LDIV: stack.popLong(); stack.popLong(); stack.pushLong();break;
            case FDIV: stack.popFloat(); stack.popFloat(); stack.pushFloat();break;
            case DDIV: stack.popDouble(); stack.popDouble(); stack.pushDouble();break;

            case IREM: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LREM: stack.popLong(); stack.popLong(); stack.pushLong();break;
            case FREM: stack.popFloat(); stack.popFloat(); stack.pushFloat();break;
            case DREM: stack.popDouble(); stack.popDouble(); stack.pushDouble();break;

            case INEG:  stack.popInt(); stack.pushInt(); break;
            case LNEG:  stack.popLong(); stack.pushLong();break;
            case FNEG:  stack.popFloat(); stack.pushFloat();break;
            case DNEG:  stack.popDouble(); stack.pushDouble();break;

            case ISHL: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LSHL: stack.popInt(); stack.popLong(); stack.pushLong();break;
            case ISHR: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LSHR: stack.popInt(); stack.popLong(); stack.pushLong();break;
            case IUSHR: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LUSHR: stack.popInt(); stack.popLong(); stack.pushLong();break;

            case IAND: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LAND: stack.popLong(); stack.popLong(); stack.pushLong();break;

            case IOR: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LOR: stack.popLong(); stack.popLong(); stack.pushLong();break;

            case IXOR: stack.popInt(); stack.popInt(); stack.pushInt(); break;
            case LXOR: stack.popLong(); stack.popLong(); stack.pushLong();break;

            case IINC: break;

            case I2L: stack.popInt(); stack.pushLong(); break;
            case I2F: stack.popInt(); stack.pushFloat(); break;
            case I2D: stack.popInt(); stack.pushDouble(); break;

            case L2I: stack.popLong(); stack.pushInt(); break;
            case L2F: stack.popLong(); stack.pushFloat(); break;
            case L2D: stack.popLong(); stack.pushDouble(); break;

            case F2I: stack.popFloat(); stack.pushInt(); break;
            case F2L: stack.popFloat(); stack.pushLong();break;
            case F2D: stack.popFloat(); stack.pushDouble();break;

            case D2I: stack.popDouble(); stack.pushInt();break;
            case D2L: stack.popDouble(); stack.pushLong();break;
            case D2F: stack.popDouble(); stack.pushFloat();break;

            case I2B: stack.popInt(); stack.pushInt();break;
            case I2C: stack.popInt(); stack.pushInt();break;
            case I2S: stack.popInt(); stack.pushInt();break;

            case LCMP: stack.popLong(); stack.popLong(); stack.pushInt();break;
            case FCMPL:
            case FCMPG: stack.popFloat(); stack.popFloat(); stack.pushInt();break;
            case DCMPL:
            case DCMPG: stack.popDouble(); stack.popDouble(); stack.pushInt();break;

            case IFEQ: // fall through
            case IFNE: // fall through
            case IFLT: // fall through
            case IFGE: // fall through
            case IFGT: // fall through
            case IFLE:
                stack.popInt();
                branch(code.readBranchDest(BCI), stack);
                break;
            case IF_ICMPEQ: // fall through
            case IF_ICMPNE: // fall through
            case IF_ICMPLT: // fall through
            case IF_ICMPGE: // fall through
            case IF_ICMPGT: // fall through
            case IF_ICMPLE:
                stack.popInt(); stack.popInt();
                branch(code.readBranchDest(BCI), stack);
                break;
            case IF_ACMPEQ: // fall through
            case IF_ACMPNE:
                stack.popObj(); stack.popObj();
                branch(code.readBranchDest(BCI), stack);
                break;

            case GOTO:
            case GOTO_W:
                branch(code.readBranchDest(BCI), stack);
                return BCI;
            case IFNULL: // fall through
            case IFNONNULL:
                stack.popObj();
                branch(code.readBranchDest(BCI), stack);
                break;
            case JSR: // fall through
            case JSR_W: {
                // Ignore JSR
//                stack.pushObj();
//                branch(code.readBranchDest(BCI), stack);
                break;
            }
            case RET: {
                return BCI;
            }

            // @formatter:on
            // Checkstyle: resume
            case TABLESWITCH: {
                stack.popInt();
                BytecodeTableSwitch switchHelper = code.getBytecodeTableSwitch();
                int low = switchHelper.lowKey(BCI);
                int high = switchHelper.highKey(BCI);
                for (int i = low; i < high; i++) {
                    branch(switchHelper.targetAt(BCI, i - low), stack);
                }
                return switchHelper.defaultTarget(BCI);
            }
            case LOOKUPSWITCH: {
                stack.popInt();
                BytecodeLookupSwitch switchHelper = code.getBytecodeLookupSwitch();
                int low = 0;
                int high = switchHelper.numberOfCases(BCI) - 1;
                for (int i = low; i <= high; i++) {
                    branch(BCI + switchHelper.offsetAt(BCI, i), stack);
                }
                return switchHelper.defaultTarget(BCI);
            }
            // @formatter:off
            // Checkstyle: stop
            case IRETURN: stack.popInt(); return BCI;
            case LRETURN: stack.popLong(); return BCI;
            case FRETURN: stack.popFloat(); return BCI;
            case DRETURN: stack.popDouble(); return BCI;
            case ARETURN: stack.popObj(); return BCI;
            case RETURN: return BCI;

            case GETSTATIC:
            case GETFIELD:
            {
                PoolConstant pc = pool.at(code.readCPI(BCI));
                if (!(pc instanceof FieldRefConstant)) {
                    throw new VerifyError();
                }
                Operand kind = stringToKind(((FieldRefConstant) pc).getType(pool).toString());
                if (curOpcode == GETFIELD) {
                    stack.popObj();
                }
                stack.push(kind);
                break;
            }
            case PUTSTATIC:
            case PUTFIELD: {
                PoolConstant pc = pool.at(code.readCPI(BCI));
                if (!(pc instanceof FieldRefConstant)) {
                    throw new VerifyError();
                }
                Operand kind = stringToKind(((FieldRefConstant) pc).getType(pool).toString());
                stack.pop(kind);
                if (curOpcode == PUTFIELD) {
                    stack.popObj();
                }
                break;
            }

            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case INVOKEINTERFACE: {
                PoolConstant pc = pool.at(code.readCPI(BCI));
                if (!(pc instanceof MethodRefConstant)) {
                    throw new VerifyError("Invalid CP constant for a MethodRef: " + pc.getClass().getName());
                }
                Operand[] parsedSig = parseSig((((MethodRefConstant) pc).getSignature(pool)).toString());
                if (parsedSig.length == 0) {
                    throw new ClassFormatError("Method ref with no return value !");
                }
                for (int i = parsedSig.length - 2; i>=0; i--) {
                    stack.pop(parsedSig[i]);
                }
                if (curOpcode != INVOKESTATIC) {
                    stack.popObj();
                }
                Operand returnKind = parsedSig[parsedSig.length - 1];
                if (!(returnKind == Void)) {
                    stack.push(returnKind);
                }
                break;
            }

            case NEW: stack.pushObj(); break;
            case NEWARRAY: stack.popInt(); stack.pushObj(); break;
            case ANEWARRAY: stack.popInt(); stack.pushObj(); break;
            case ARRAYLENGTH: stack.popObj(); stack.pushInt(); break;

            case ATHROW: stack.popObj(); return BCI;

            case CHECKCAST: stack.popObj(); stack.pushObj(); break;
            case INSTANCEOF: stack.popObj(); stack.pushInt(); break;

            case MONITORENTER: stack.popObj(); break;
            case MONITOREXIT: stack.popObj(); break;

            case WIDE: break;
            case MULTIANEWARRAY:
                int dim = code.readUByte(BCI + 3);
                for (int i = 0; i<dim; i++) {
                    stack.popInt();
                }
                stack.pushObj();
                break;

            case BREAKPOINT: break;

            case INVOKEDYNAMIC: {
                PoolConstant pc = pool.at(code.readCPI(BCI));
                if (!(pc instanceof InvokeDynamicConstant)) {
                    throw new VerifyError("Invalid CP constant for an InvokeDynamic: " + pc.getClass().getName());
                }

                InvokeDynamicConstant idc = (InvokeDynamicConstant) pc;
                if (!(pool.at(idc.getNameAndTypeIndex()).tag() == ConstantPool.Tag.NAME_AND_TYPE)) {
                    throw new ClassFormatError("Invalid constant pool !");
                }
                Operand[] parsedSig = parseSig(idc.getSignature(pool).toString());
                if (parsedSig.length == 0) {
                    throw new ClassFormatError("No return descriptor for method");
                }
                for (int i = parsedSig.length - 2; i >= 0; i--) {
                    stack.pop(parsedSig[i]);
                }
                Operand returnKind = parsedSig[parsedSig.length - 1];
                if (returnKind != Void) {
                    stack.push(returnKind);
                }
                break;
            }
            case QUICK: break;
            default:
        // @formatter:on
                // Checkstyle: resume
        }
        return code.nextBCI(BCI);
    }

    private static Operand[] parseSig(String sig) {
        boolean opened = false;
        ArrayList<Operand> res = new ArrayList<>();
        int i = 0;
        int sigLen = sig.length();
        while (i < sigLen) {
            switch (sig.charAt(i)) {
                case '(':
                    i++;
                    opened = true;
                    break;
                case ')':
                    i++;
                    opened = false;
                    break;
                case '[':
                    res.add(Object);
                    while (i < sigLen && sig.charAt(i) == '[') {
                        i++;
                    }
                    if (i == sigLen) {
                        throw new ClassFormatError("Invalid method signature: " + sig);
                    }
                    char arrayT = sig.charAt(i);
                    if (arrayT == 'L') {
                        i = sig.indexOf(';', i) + 1;
                        if (i == 0) {
                            throw new ClassFormatError("Invalid method signature: " + sig);
                        }
                    } else {
                        if (arrayT == '(' || arrayT == ')') {
                            throw new ClassFormatError("Invalid method signature: " + sig);
                        }
                        i++;
                    }
                    break;
                case 'L':
                    res.add(Object);
                    i = sig.indexOf(';', i) + 1;
                    if (i == 0) {
                        throw new ClassFormatError("Invalid method signature: " + sig);
                    }
                    break;
                case 'Z':
                case 'C':
                case 'B':
                case 'S':
                case 'I':
                    res.add(Int);
                    i++;
                    break;
                case 'F':
                    res.add(Float);
                    i++;
                    break;
                case 'D':
                    res.add(Double);
                    i++;
                    break;
                case 'J':
                    res.add(Long);
                    i++;
                    break;
                case 'V':
                    res.add(Void);
                    i++;
                    break;
                default:
                    throw new ClassFormatError("Invalid method signature: " + sig);
            }
        }
        if (opened) {
            throw new ClassFormatError("Invalid method signature: " + sig);
        }
        return res.toArray(new Operand[0]);
    }

    private static Operand stringToKind(String s) {
        int sLen = s.length();
        if (sLen == 0) {
            throw new ClassFormatError("invalid standalone type: " + s);
        }
        char c = s.charAt(0);
        switch (c) {
            case '(':
            case ')':
                throw new ClassFormatError("Invalid standalone type: " + c);
            case '[':
                int i = 1;
                while (i < sLen && s.charAt(i) == '[') {
                    i++;
                }
                if (i == sLen) {
                    throw new ClassFormatError("invalid standalone type: " + s);
                }
                Operand o = stringToKind(s.substring(i));
                if (o == null) {
                    throw new ClassFormatError("invalid standalone type: " + s);
                }
                return Object;
            case 'L':
                int index = s.indexOf(';');
                if (index == -1 || index != sLen - 1) {
                    throw new ClassFormatError("invalid standalone type: " + s);
                }
                return Object;
            case 'Z':
            case 'C':
            case 'B':
            case 'S':
            case 'I':
                return Int;
            case 'F':
                return Float;
            case 'D':
                return Double;
            case 'J':
                return Long;
            case 'V':
                return Void;
            default:
                throw new ClassFormatError("Invalid standalone type: " + c);
        }
    }

    private class Stack {
        private final Operand[] stack;
        private int top;

        private Stack(Stack copy) {
            this.stack = new Operand[copy.stack.length];
            this.top = copy.top;
            System.arraycopy(copy.stack, 0, this.stack, 0, top);
        }

        Stack(int maxStack) {
            this.stack = new Operand[maxStack];
            this.top = 0;
        }

        Stack copy() {
            return new Stack(this);
        }

        void pushInt() {
            stack[top++] = Int;
        }

        void pushObj() {
            stack[top++] = Object;
        }

        void pushFloat() {
            stack[top++] = Float;
        }

        void pushDouble() {
            stack[top++] = Double;
        }

        void pushLong() {
            stack[top++] = Long;
        }

        void push(Operand kind) {
            stack[top++] = kind;
        }

        void popInt() {
            if (!(stack[--top] == Int)) {
                throw new VerifyError(stack[top] + " on stack, required: " + Int);
            }
        }

        void popObj() {
            if (!(stack[--top] == Object)) {
                throw new VerifyError(stack[top] + " on stack, required: " + Object);
            }
        }

        void popFloat() {
            if (!(stack[--top] == Float)) {
                throw new VerifyError(stack[top] + " on stack, required: " + Float);
            }
        }

        void popDouble() {
            if (!(stack[--top] == Double)) {
                throw new VerifyError(stack[top] + " on stack, required: " + Double);
            }
        }

        void popLong() {
            if (!(stack[--top] == Long)) {
                throw new VerifyError(stack[top] + " on stack, required: " + Long);
            }
        }

        void pop(Operand k) {
            if (!(stack[--top] == k)) {
                throw new VerifyError(stack[top] + " on stack, required: " + k);
            }

        }

        void dup() {
            if (isType2(stack[top - 1])) {
                throw new VerifyError("type 2 operand for dup.");
            }
            stack[top] = stack[top - 1];
            top++;
        }

        void pop() {
            Operand v1 = stack[top - 1];
            if (isType2(v1)) {
                throw new VerifyError("type 2 operand for pop.");
            }
            top--;
        }

        void pop2() {
            Operand v1 = stack[top - 1];
            if (isType2(v1)) {
                top--;
                return;
            }
            Operand v2 = stack[top - 2];
            if (isType2(v2)) {
                throw new VerifyError("type 2 second operand for pop2.");
            }
            top = top - 2;
        }

        void dupx1() {
            Operand v1 = stack[top - 1];
            if (isType2(v1) || isType2(stack[top - 2])) {
                throw new VerifyError("type 2 operand for dupx1.");
            }
            System.arraycopy(stack, top - 2, stack, top - 1, 2);
            top++;
            stack[top - 3] = v1;
        }

        void dupx2() {
            Operand v1 = stack[top - 1];
            if (isType2(v1)) {
                throw new VerifyError("type 2 first operand for dupx2.");
            }
            Operand v2 = stack[top - 2];
            if (isType2(v2)) {
                System.arraycopy(stack, top - 2, stack, top - 1, 2);
                top++;
                stack[top - 3] = v1;
            } else {
                if (isType2(stack[top - 3])) {
                    throw new VerifyError("type 2 third operand for dupx2.");
                }
                System.arraycopy(stack, top - 3, stack, top - 2, 3);
                top++;
                stack[top - 4] = v1;
            }
        }

        void dup2() {
            Operand v1 = stack[top - 1];
            if (isType2(v1)) {
                stack[top] = v1;
                top++;
            } else {
                if (isType2(stack[top - 1])) {
                    throw new VerifyError("type 2 second operand for dup2.");
                }
                System.arraycopy(stack, top - 2, stack, top, 2);
                top = top + 2;
            }
        }

        void dup2x1() {
            Operand v1 = stack[top - 1];
            Operand v2 = stack[top - 2];
            if (isType2(v2)) {
                throw new VerifyError("type 2 second operand for dup2x1");
            }
            if (isType2(v1)) {
                System.arraycopy(stack, top - 2, stack, top - 1, 2);
                top++;
                stack[top - 3] = v1;
                return;
            }
            if (isType2(stack[top - 3])) {
                throw new VerifyError("type 2 third operand for dup2x1.");
            }
            System.arraycopy(stack, top - 3, stack, top - 1, 3);
            top = top + 2;
            stack[top - 5] = v2;
            stack[top - 4] = v1;
        }

        void dup2x2() {
            Operand v1 = stack[top - 1];
            Operand v2 = stack[top - 2];
            boolean b1 = isType2(v1);
            boolean b2 = isType2(v2);

            if (b1 && b2) {
                System.arraycopy(stack, top - 2, stack, top - 1, 2);
                stack[top - 2] = v1;
                top++;
                return;
            }
            Operand v3 = stack[top - 3];
            boolean b3 = isType2(v3);
            if (!b1 && !b2 && b3) {
                System.arraycopy(stack, top - 3, stack, top - 1, 3);
                stack[top - 3] = v2;
                stack[top - 2] = v1;
                top = top + 2;
                return;
            }
            if (b1 && !b2 && !b3) {
                System.arraycopy(stack, top - 3, stack, top - 2, 3);
                stack[top - 3] = v1;
                top++;
                return;
            }
            Operand v4 = stack[top - 4];
            boolean b4 = isType2(v4);
            if (!b1 && !b2 && !b3 && !b4) {
                System.arraycopy(stack, top - 4, stack, top - 2, 4);
                stack[top - 4] = v2;
                stack[top - 3] = v1;
                top = top + 2;
                return;
            }
            throw new VerifyError("Seriously, what are you doing with dup2x2 ?");

        }

        void swap() {
            Operand v1 = stack[top - 1];
            Operand v2 = stack[top - 2];
            boolean b1 = isType2(v1);
            boolean b2 = isType2(v2);
            if (!b1 && !b2) {
                stack[top - 1] = v2;
                stack[top - 2] = v1;
                return;
            }
            throw new VerifyError("Type 2 operand for SWAP");
        }

        private boolean isType2(Operand k) {
            return k == Long || k == Double;
        }
    }
}
