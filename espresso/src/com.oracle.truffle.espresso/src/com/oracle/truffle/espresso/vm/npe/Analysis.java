/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vm.npe;

import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.AALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.AASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ACONST_NULL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ANEWARRAY;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ARETURN;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ARRAYLENGTH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ATHROW;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.BALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.BASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.BIPUSH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.CALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.CASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.D2F;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.D2I;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.D2L;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DADD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DCMPG;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DCMPL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DCONST_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DCONST_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DDIV;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DMUL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DNEG;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DREM;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DRETURN;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSUB;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DUP;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DUP2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DUP2_X1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DUP2_X2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DUP_X1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DUP_X2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.F2D;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.F2I;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.F2L;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FADD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FCMPG;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FCMPL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FCONST_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FCONST_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FCONST_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FDIV;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FMUL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FNEG;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FREM;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FRETURN;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSUB;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GETFIELD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GOTO;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.GOTO_W;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.I2B;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.I2C;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.I2D;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.I2F;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.I2L;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.I2S;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IADD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IAND;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_4;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_5;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ICONST_M1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IDIV;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFEQ;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFGE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFGT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFLE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFLT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFNE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFNONNULL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IFNULL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ACMPEQ;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ACMPNE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ICMPEQ;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ICMPGE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ICMPGT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ICMPLE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ICMPLT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IF_ICMPNE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IINC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IMUL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INEG;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INSTANCEOF;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEINTERFACE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKESTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IOR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IREM;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IRETURN;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISHL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISHR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISUB;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IUSHR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IXOR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.JSR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.JSR_W;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.L2D;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.L2F;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.L2I;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LADD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LAND;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LCMP;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LCONST_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LCONST_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LDC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LDC2_W;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LDC_W;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LDIV;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LMUL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LNEG;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LOOKUPSWITCH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LOR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LREM;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LRETURN;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSHL;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSHR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSUB;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LUSHR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LXOR;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.MONITORENTER;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.MONITOREXIT;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.MULTIANEWARRAY;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.NEW;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.NEWARRAY;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.POP;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.POP2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.RET;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.RETURN;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.SALOAD;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.SASTORE;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.SIPUSH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.SWAP;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.TABLESWITCH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.stackEffectOf;
import static com.oracle.truffle.espresso.vm.npe.StackObject.UNKNOWN_BCI;
import static com.oracle.truffle.espresso.vm.npe.StackType.OBJECT;
import static com.oracle.truffle.espresso.vm.npe.StackType.rtype;

import java.util.ArrayList;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ExceptionHandler;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeSwitch;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.impl.LanguageAccess;
import com.oracle.truffle.espresso.impl.Method;

final class Analysis implements LanguageAccess {

    private final EspressoLanguage lang;
    final Method m;
    private final int targetBci;
    private final int maxStack;

    final BytecodeStream bs;
    private final SimulatedStack[] stacks;

    // Control of the return condition of the analysis
    private boolean allDone = false;
    // Whether a new stack has been added during the last step.
    private boolean newStackInfo = false;

    // Keep track of how many stack objects are being remembered, so we can bail out in case of
    // unreasonably big methods.
    private int entries = 0;
    private static final int MAX_ENTRIES = 100_000;

    static Analysis analyze(Method m, int bci) {
        return new Analysis(m, bci);
    }

    String buildMessage() {
        return MessageBuildHelper.buildCause(this, targetBci);
    }

    @Override
    public EspressoLanguage getLanguage() {
        return lang;
    }

    private Analysis(Method m, int targetBci) {
        this.m = m;
        this.targetBci = targetBci;
        this.bs = new BytecodeStream(m.getOriginalCode());
        this.stacks = new SimulatedStack[bs.endBCI()];
        this.maxStack = m.getMethodVersion().getMaxStackSize();
        this.lang = m.getLanguage();
        analyze();
    }

    private void analyze() {
        // Initialize stack at bci 0
        registerStack(new SimulatedStack(maxStack), 0);
        // Initialize stack at exception handlers
        for (ExceptionHandler handler : m.getSymbolicExceptionHandlers()) {
            int bci = handler.getHandlerBCI();
            registerStack(new SimulatedStack(maxStack).push(UNKNOWN_BCI, OBJECT), bci);
        }
        do {
            allDone = true;
            newStackInfo = false;

            int bci = 0;
            int nextBci;
            while (bci < bs.endBCI()) {
                nextBci = bs.nextBCI(bci);
                processInstr(bci, nextBci);
                if (entries > MAX_ENTRIES) {
                    return;
                }
                bci = nextBci;
            }

        } while (!allDone && newStackInfo);
    }

    private void processInstr(int bci, int nextBci) {
        SimulatedStack origStack = stacks[bci];
        if (origStack == null) {
            // No stack info, cannot process yet
            allDone = false;
            return;
        }
        // Work on a local copy
        SimulatedStack stack = new SimulatedStack(origStack, maxStack);
        ArrayList<Integer> branches = new ArrayList<>(2);
        boolean endOfFlow = false;

        int opcode = bs.currentBC(bci);

        // @formatter:off
        switch (opcode) {
            case ACONST_NULL:
            case ALOAD:
            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3:
            case NEW:
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case BIPUSH:
            case SIPUSH:
            case ILOAD:
            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3:
            case LCONST_0:
            case LCONST_1:
            case LLOAD:
            case LLOAD_0:
            case LLOAD_1:
            case LLOAD_2:
            case LLOAD_3:
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
            case FLOAD:
            case FLOAD_0:
            case FLOAD_1:
            case FLOAD_2:
            case FLOAD_3:
            case DCONST_0:
            case DCONST_1:
            case DLOAD:
            case DLOAD_0:
            case DLOAD_1:
            case DLOAD_2:
            case DLOAD_3:
                stack.push(bci, rtype(opcode)); break;
            case BALOAD:
            case CALOAD:
            case SALOAD:
            case IALOAD:
            case LALOAD:
            case FALOAD:
            case DALOAD:
            case AALOAD:
                stack.pop(); stack.pop();
                stack.push(bci, rtype(opcode)); break;
            case ISTORE:
            case LSTORE:
            case FSTORE:
            case DSTORE:
            case ASTORE:
                stack.setLocalSlotWritten(bs.readLocalIndex(bci));
                stack.pop(-Bytecodes.stackEffectOf(opcode)); break;
            case ISTORE_0:
            case LSTORE_0:
            case FSTORE_0:
            case DSTORE_0:
            case ASTORE_0:
                stack.setLocalSlotWritten(0);
                stack.pop(-Bytecodes.stackEffectOf(opcode)); break;
            case ISTORE_1:
            case LSTORE_1:
            case FSTORE_1:
            case DSTORE_1:
            case ASTORE_1:
                stack.setLocalSlotWritten(1);
                stack.pop(-Bytecodes.stackEffectOf(opcode)); break;
            case ISTORE_2:
            case LSTORE_2:
            case FSTORE_2:
            case DSTORE_2:
            case ASTORE_2:
                stack.setLocalSlotWritten(2);
                stack.pop(-Bytecodes.stackEffectOf(opcode)); break;
            case ISTORE_3:
            case LSTORE_3:
            case FSTORE_3:
            case DSTORE_3:
            case ASTORE_3:
                stack.setLocalSlotWritten(3);
                stack.pop(-Bytecodes.stackEffectOf(opcode)); break;
            case IASTORE:
            case LASTORE:
            case FASTORE:
            case DASTORE:
            case AASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE:
            case POP:
            case POP2:
            case MONITORENTER:
            case MONITOREXIT:
                stack.pop(-Bytecodes.stackEffectOf(opcode)); break;
            case DUP:
                stack.pushRaw(stack.top(0)); break;
            case DUP_X1: {
                    StackObject top0 = stack.top(0);
                    StackObject top1 = stack.top(1);
                    stack.pop(2);
                    stack.pushRaw(top0).pushRaw(top1).pushRaw(top0);
                    break;
                }
            case DUP_X2: {
                    StackObject top0 = stack.top(0);
                    StackObject top1 = stack.top(1);
                    StackObject top2 = stack.top(2);
                    stack.pop(3);
                    stack.pushRaw(top0).pushRaw(top2).pushRaw(top1).pushRaw(top0);
                    break;
                }
            case DUP2:
                stack.pushRaw(stack.top(1));
                // former top is now at depth 1.
                stack.pushRaw(stack.top(1));
                break;
            case DUP2_X1: {
                StackObject top0 = stack.top(0);
                StackObject top1 = stack.top(1);
                StackObject top2 = stack.top(2);
                stack.pop(3);
                stack.pushRaw(top1).pushRaw(top0).pushRaw(top2).pushRaw(top1).pushRaw(top0);
                break;
            }
            case DUP2_X2: {
                StackObject top0 = stack.top(0);
                StackObject top1 = stack.top(1);
                StackObject top2 = stack.top(2);
                StackObject top3 = stack.top(3);
                stack.pop(4);
                stack.pushRaw(top1).pushRaw(top0).pushRaw(top3).pushRaw(top2).pushRaw(top1).pushRaw(top0);
                break;
            }
            case SWAP: {
                StackObject top0 = stack.top(0);
                StackObject top1 = stack.top(1);
                stack.pop(2);
                stack.pushRaw(top0).pushRaw(top1);
                break;
            }
            case IADD:
            case LADD:
            case FADD:
            case DADD:
            case ISUB:
            case LSUB:
            case FSUB:
            case DSUB:
            case IMUL:
            case LMUL:
            case FMUL:
            case DMUL:
            case IDIV:
            case LDIV:
            case FDIV:
            case DDIV:
            case IREM:
            case LREM:
            case FREM:
            case DREM:
            case INEG:
            case LNEG:
            case FNEG:
            case DNEG:
            case ISHL:
            case LSHL:
            case ISHR:
            case LSHR:
            case IUSHR:
            case LUSHR:
            case IAND:
            case LAND:
            case IOR:
            case LOR:
            case IXOR:
            case LXOR:
            case IINC:
            case I2L:
            case I2F:
            case I2D:
            case L2I:
            case L2F:
            case L2D:
            case F2I:
            case F2L:
            case F2D:
            case D2I:
            case D2L:
            case D2F:
            case I2B:
            case I2C:
            case I2S:
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG: {
                StackType rtype = rtype(opcode);
                stack.pop(-stackEffectOf(opcode) + rtype.slots());
                stack.push(bci, rtype); break;
            }
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case IFNULL:
            case IFNONNULL:
                stack.pop(-Bytecodes.stackEffectOf(opcode));
                branches.add(bs.readBranchDest(bci)); break;
            case GOTO:
            case GOTO_W:
                branches.add(bs.readBranchDest(bci));
                endOfFlow = true; break;
            case JSR:
            case JSR_W:
                stack.push(bci, StackType.ADDRESS);
                branches.add(bs.readBranchDest(bci));
                endOfFlow = true; break;
            case RET:
                // We don't track local variables, so we cannot know were we
                // return. This makes the stacks imprecise, but we have to
                // live with that.
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
            case RETURN:
            case ATHROW:
                endOfFlow = true; break;
            case TABLESWITCH:
            case LOOKUPSWITCH: {
                stack.pop();
                BytecodeSwitch helper = BytecodeSwitch.get(opcode);
                assert helper != null;
                branches.add(helper.defaultOffset(bs, bci));
                for (int i = 0; i < helper.numberOfCases(bs, bci); i++) {
                    branches.add(helper.targetAt(bs, bci, i));
                }
                break;
            }
            case NEWARRAY:
            case ANEWARRAY:
            case INSTANCEOF:
            case ARRAYLENGTH:
                stack.pop();
                stack.push(bci, rtype(opcode)); break;
            case MULTIANEWARRAY:
                stack.pop(bs.readUByte(bci + 3));
                stack.push(bci, StackType.ARRAY); break;
            case LDC:
            case LDC_W:
            case LDC2_W: {
                int cpi = bs.readCPI(bci);
                ConstantPool.Tag tag = m.getConstantPool().tagAt(cpi);
                stack.push(bci, StackType.forTag(tag));
                break;
            }
            case GETSTATIC:
            case GETFIELD: {
                Symbol<Type> type = getFieldType(bci);
                stack.pop(-Bytecodes.stackEffectOf(opcode));
                stack.push(bci, StackType.forType(type));
                break;
            }
            case PUTSTATIC:
            case PUTFIELD: {
                Symbol<Type> type = getFieldType(bci);
                int slots = StackType.forType(type).slots();
                stack.pop(-Bytecodes.stackEffectOf(opcode) + slots);
                break;
            }
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case INVOKEINTERFACE:
            case INVOKEDYNAMIC: {
                Symbol<Type>[] parsed = getInvokeSignature(bci, opcode);
                stack.pop(SignatureSymbols.slotsForParameters(parsed));
                if (!(opcode == INVOKESTATIC || opcode == INVOKEDYNAMIC)) {
                    stack.pop(); // receiver
                }
                stack.push(bci, StackType.forType(SignatureSymbols.returnType(parsed)));
                break;
            }

            default:
                break;
        }
        // @formatter:on
        if (!endOfFlow) {
            // Update stack info for the next operation.
            registerStack(stack, nextBci);
        }
        for (int branch : branches) {
            registerStack(stack, branch);
        }
    }

    Symbol<Name> getFieldName(int bci) {
        int cpi = bs.readCPI(bci);
        return m.getConstantPool().fieldName(cpi);
    }

    Symbol<Type> getFieldType(int bci) {
        int cpi = bs.readCPI(bci);
        return m.getConstantPool().fieldType(cpi);
    }

    Symbol<Name> getInvokeName(int bci, int opcode) {
        assert Bytecodes.isInvoke(opcode);
        int cpi = bs.readCPI(bci);
        if (opcode == INVOKEDYNAMIC) {
            return m.getConstantPool().invokeDynamicName(cpi);
        } else {
            return m.getConstantPool().methodName(cpi);
        }
    }

    Symbol<Type>[] getInvokeSignature(int bci, int opcode) {
        assert Bytecodes.isInvoke(opcode);
        int cpi = bs.readCPI(bci);
        Symbol<Signature> sig;
        if (opcode == INVOKEDYNAMIC) {
            sig = m.getConstantPool().invokeDynamicSignature(cpi);
        } else {
            sig = m.getConstantPool().methodSignature(cpi);
        }
        return getSignatures().parsed(sig);
    }

    SimulatedStack stackAt(int bci) {
        return stacks[bci];
    }

    private void registerStack(SimulatedStack stack, int nextBci) {
        SimulatedStack oldStack = stacks[nextBci];
        if (oldStack == null) {
            newStackInfo = true;
            entries += stack.size();
        }
        stacks[nextBci] = SimulatedStack.merge(stack, oldStack);
    }

}
