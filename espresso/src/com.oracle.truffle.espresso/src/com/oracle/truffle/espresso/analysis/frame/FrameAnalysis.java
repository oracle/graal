/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.analysis.frame;

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
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RET;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SIPUSH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SWAP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.TABLESWITCH;

import java.util.ArrayDeque;
import java.util.BitSet;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.analysis.liveness.LivenessAnalysis;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.BytecodeSwitch;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.constantpool.DynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvokeDynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodRefConstant;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.vm.continuation.EspressoFrameDescriptor;
import com.oracle.truffle.espresso.vm.continuation.EspressoFrameDescriptor.Builder;

/**
 * Statically analyses bytecodes to produce a {@link EspressoFrameDescriptor frame description} for
 * the given BCI.
 */
public final class FrameAnalysis {
    private final EspressoLanguage lang;
    private final Method.MethodVersion m;

    private final LivenessAnalysis la;
    private final BytecodeStream bs;
    private final ConstantPool pool;
    private final int targetBci;

    private final Builder[] states;
    private final BitSet branchTargets;
    private final BitSet processStatus;

    private final ArrayDeque<Integer> queue = new ArrayDeque<>(2);

    public static EspressoFrameDescriptor apply(Method.MethodVersion m, int bci) {
        return new FrameAnalysis(bci, m).apply();
    }

    private FrameAnalysis(int targetBci, Method.MethodVersion m) {
        this.lang = m.getMethod().getLanguage();
        this.la = m.getLivenessAnalysis();
        this.bs = new BytecodeStream(m.getOriginalCode());
        this.targetBci = targetBci;
        this.states = new Builder[bs.endBCI()];
        this.pool = m.getDeclaringKlass().getLinkedKlass().getParserKlass().getConstantPool();
        this.m = m;
        this.branchTargets = new BitSet(bs.endBCI());
        this.processStatus = new BitSet(bs.endBCI());
    }

    private static void popSignature(Symbol<Type>[] sig, boolean isStatic, Builder frame) {
        for (Symbol<Type> t : Signatures.iterable(sig, true, false)) {
            JavaKind k = Types.getJavaKind(t).getStackKind();
            frame.pop(k);
        }
        if (!isStatic) {
            frame.pop(JavaKind.Object);
        }
    }

    private EspressoFrameDescriptor apply() {
        markBranchTargets();

        buildInitialFrame();
        int startBci = 0;
        push(startBci);

        while (!queue.isEmpty()) {
            startBci = pop();
            buildStates(startBci);
        }

        Builder state = states[targetBci];
        int opcode = bs.opcode(targetBci);
        // For continuations purposes, we need to mutate the state to having popped arguments, but
        // not having yet pushed a result.
        assert Bytecodes.isInvoke(opcode);
        handleInvoke(state, targetBci, opcode, false);
        return state.build();
    }

    private void buildInitialFrame() {
        Builder frame = new Builder(m.getMaxLocals(), m.getMaxStackSize());
        Symbol<Type>[] sig = m.getMethod().getParsedSignature();
        int receiverShift = 0;
        if (!m.isStatic()) {
            frame.putLocal(0, JavaKind.Object);
            receiverShift = 1;
        }
        int localPos = 0;
        for (int sigPos = 0; sigPos < Signatures.parameterCount(sig); sigPos++) {
            JavaKind k = Signatures.parameterKind(sig, sigPos);
            if (k.isStackInt()) {
                k = JavaKind.Int;
            }
            frame.putLocal(receiverShift + localPos, k);
            if (k.needsTwoSlots()) {
                localPos++;
                frame.putLocal(receiverShift + localPos, JavaKind.Illegal);
            }
            localPos++;
        }
        la.onStart(frame);
        frame.setBci(0);
        assert frame.isRecord();
        states[0] = frame;
    }

    private void markBranchTargets() {
        int bci = 0;
        boolean validTarget = false;
        while (bci < bs.endBCI()) {
            int opcode = bs.opcode(bci);
            if (Bytecodes.isBranch(opcode)) {
                branchTargets.set(bs.readBranchDest(bci));
            } else if (opcode == TABLESWITCH || opcode == LOOKUPSWITCH) {
                BytecodeSwitch helper = BytecodeSwitch.get(opcode);
                for (int i = 0; i < helper.numberOfCases(bs, opcode); i++) {
                    branchTargets.set(helper.targetAt(bs, bci, i));
                }
                branchTargets.set(helper.defaultTarget(bs, bci));
            }
            if (bci == targetBci) {
                validTarget = true;
            }
            bci = bs.nextBCI(bci);
        }

        if (!validTarget) {
            Meta meta = m.getDeclaringKlass().getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Target bci is not a valid bytecode.");
        }

        ExceptionHandler[] handlers = m.getExceptionHandlers();
        for (ExceptionHandler handler : handlers) {
            branchTargets.set(handler.getHandlerBCI());
        }
    }

    private void buildStates(int startBci) {
        Builder frame = states[startBci].copy();
        int bci = startBci;
        while (bci < bs.endBCI()) {
            if (branchTargets.get(bci)) {
                Builder registered = states[bci];
                assert (registered == null) || frame.sameTop(registered);
                if (merge(bci, frame) && processStatus.get(bci)) {
                    // If already processed, and merge succeeded, we can stop process for this
                    // block.
                    return;
                }
                // Verification is already run, so we know that processing will succeed.
                processStatus.set(bci);
                // keep going with updated frame, as it is be the most precise.
                frame = registered == null ? frame : states[bci].copy();
            }
            if (bci == targetBci) {
                // At this point, the current working frame can only be more precise.
                registerState(bci, frame);
                if (!la.isEmpty()) {
                    // With LA, merging should always succeed.
                    queue.clear();
                    return;
                }
            }
            assert frame.isWorking();
            int opcode = bs.currentBC(bci);
            switch (opcode) {
                case NOP: // fallthrough
                case IINC: // fallthrough
                case CHECKCAST:
                    break;
                case ACONST_NULL, ALOAD, ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3, NEW:
                    frame.push(JavaKind.Object);
                    break;
                case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5: // fallthrough
                case BIPUSH, SIPUSH: // fallthrough
                case ILOAD, ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3:
                    frame.push(JavaKind.Int);
                    break;
                case LCONST_0, LCONST_1, LLOAD, LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3:
                    frame.push(JavaKind.Long);
                    break;
                case FCONST_0, FCONST_1, FCONST_2, FLOAD, FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3:
                    frame.push(JavaKind.Float);
                    break;
                case DCONST_0, DCONST_1, DLOAD, DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3:
                    frame.push(JavaKind.Double);
                    break;
                case LDC, LDC_W, LDC2_W: {
                    ldc(bci, frame);
                    break;
                }
                case IALOAD, BALOAD, CALOAD, SALOAD: // fallthrough
                case IADD, ISUB, IMUL, IDIV, IREM, ISHL, ISHR, IUSHR, IAND, IOR, IXOR: // fallthrough
                case FCMPL, FCMPG:
                    frame.pop();
                    frame.pop();
                    frame.push(JavaKind.Int);
                    break;
                case LALOAD:
                    frame.pop();
                    frame.pop();
                    frame.push(JavaKind.Long);
                    break;
                case FALOAD, FADD, FSUB, FMUL, FDIV, FREM:
                    frame.pop();
                    frame.pop();
                    frame.push(JavaKind.Float);
                    break;
                case DALOAD:
                    frame.pop();
                    frame.pop();
                    frame.push(JavaKind.Double);
                    break;
                case AALOAD:
                    frame.pop();
                    frame.pop();
                    frame.push(JavaKind.Object);
                    break;
                case ISTORE:
                    frame.pop();
                    frame.putLocal(bs.readLocalIndex(bci), JavaKind.Int);
                    break;
                case LSTORE:
                    frame.pop2();
                    frame.putLocal(bs.readLocalIndex(bci), JavaKind.Long);
                    break;
                case FSTORE:
                    frame.pop();
                    frame.putLocal(bs.readLocalIndex(bci), JavaKind.Float);
                    break;
                case DSTORE:
                    frame.pop2();
                    frame.putLocal(bs.readLocalIndex(bci), JavaKind.Double);
                    break;
                case ASTORE:
                    frame.pop();
                    frame.putLocal(bs.readLocalIndex(bci), JavaKind.Object);
                    break;
                case ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3:
                    frame.pop();
                    frame.putLocal(opcode - ISTORE_0, JavaKind.Int);
                    break;
                case LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3:
                    frame.pop2();
                    frame.putLocal(opcode - LSTORE_0, JavaKind.Long);
                    break;
                case FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3:
                    frame.pop();
                    frame.putLocal(opcode - FSTORE_0, JavaKind.Float);
                    break;
                case DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3:
                    frame.pop2();
                    frame.putLocal(opcode - DSTORE_0, JavaKind.Double);
                    break;
                case ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3:
                    frame.pop();
                    frame.putLocal(opcode - ASTORE_0, JavaKind.Object);
                    break;
                case IASTORE, FASTORE, AASTORE, BASTORE, CASTORE, SASTORE:
                    frame.pop();
                    frame.pop();
                    frame.pop();
                    break;
                case LASTORE, DASTORE:
                    frame.pop2();
                    frame.pop();
                    frame.pop();
                    break;
                case POP, MONITORENTER, MONITOREXIT:
                    frame.pop();
                    break;
                case POP2:
                    frame.pop();
                    frame.pop();
                    break;
                case DUP: {
                    JavaKind k = frame.pop();
                    frame.push(k, false);
                    frame.push(k, false);
                    break;
                }
                case DUP_X1: {
                    JavaKind v1 = frame.pop();
                    JavaKind v2 = frame.pop();
                    frame.push(v1, false);
                    frame.push(v2, false);
                    frame.push(v1, false);
                    break;
                }
                case DUP_X2: {
                    JavaKind v1 = frame.pop();
                    JavaKind v2 = frame.pop();
                    JavaKind v3 = frame.pop();
                    frame.push(v1, false);
                    frame.push(v3, false);
                    frame.push(v2, false);
                    frame.push(v1, false);
                    break;
                }
                case DUP2: {
                    JavaKind v1 = frame.pop();
                    JavaKind v2 = frame.pop();
                    frame.push(v2, false);
                    frame.push(v1, false);
                    frame.push(v2, false);
                    frame.push(v1, false);
                    break;
                }
                case DUP2_X1: {
                    JavaKind v1 = frame.pop();
                    JavaKind v2 = frame.pop();
                    JavaKind v3 = frame.pop();
                    frame.push(v2, false);
                    frame.push(v1, false);
                    frame.push(v3, false);
                    frame.push(v2, false);
                    frame.push(v1, false);
                    break;
                }
                case DUP2_X2: {
                    JavaKind v1 = frame.pop();
                    JavaKind v2 = frame.pop();
                    JavaKind v3 = frame.pop();
                    JavaKind v4 = frame.pop();
                    frame.push(v2, false);
                    frame.push(v1, false);
                    frame.push(v4, false);
                    frame.push(v3, false);
                    frame.push(v2, false);
                    frame.push(v1, false);
                    break;
                }
                case SWAP: {
                    JavaKind k1 = frame.pop();
                    JavaKind k2 = frame.pop();
                    frame.push(k1, false);
                    frame.push(k2, false);
                    break;
                }
                case LADD, LSUB, LMUL, LDIV, LREM, LSHL, LSHR, LUSHR, LAND, LOR, LXOR:
                    frame.pop2();
                    frame.pop2();
                    frame.push(JavaKind.Long);
                    break;
                case DADD, DSUB, DMUL, DDIV, DREM:
                    frame.pop2();
                    frame.pop2();
                    frame.push(JavaKind.Double);
                    break;
                case INEG, F2I, I2B, I2C, I2S: // fallthrough
                case ARRAYLENGTH: // fallthrough
                case INSTANCEOF:
                    frame.pop();
                    frame.push(JavaKind.Int);
                    break;
                case LNEG, D2L:
                    frame.pop2();
                    frame.push(JavaKind.Long);
                    break;
                case FNEG, I2F:
                    frame.pop();
                    frame.push(JavaKind.Float);
                    break;
                case DNEG, L2D:
                    frame.pop2();
                    frame.push(JavaKind.Double);
                    break;
                case I2L, F2L:
                    frame.pop();
                    frame.push(JavaKind.Long);
                    break;
                case I2D, F2D:
                    frame.pop();
                    frame.push(JavaKind.Double);
                    break;
                case L2I, D2I:
                    frame.pop2();
                    frame.push(JavaKind.Int);
                    break;
                case L2F, D2F:
                    frame.pop2();
                    frame.push(JavaKind.Float);
                    break;
                case LCMP, DCMPL, DCMPG:
                    frame.pop2();
                    frame.pop2();
                    frame.push(JavaKind.Int);
                    break;
                case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL:
                    frame.pop();
                    branch(bci, bs.readBranchDest(bci), frame);
                    break;
                case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE:
                    frame.pop();
                    frame.pop();
                    branch(bci, bs.readBranchDest(bci), frame);
                    break;
                case GOTO, GOTO_W:
                    branch(bci, bs.readBranchDest(bci), frame);
                    return;
                case JSR, JSR_W, RET:
                    throw EspressoError.shouldNotReachHere("Should have prevented jsr/ret");
                case TABLESWITCH, LOOKUPSWITCH: {
                    frame.pop();
                    BytecodeSwitch bytecodeSwitch = BytecodeSwitch.get(bci);
                    for (int i = 0; i <= bytecodeSwitch.numberOfCases(bs, bci); i++) {
                        branch(bci, i, frame);
                    }
                    branch(bci, bytecodeSwitch.defaultTarget(bs, bci), frame);
                    return;
                }
                case IRETURN, FRETURN, ARETURN, ATHROW:
                    frame.pop();
                    return;
                case LRETURN, DRETURN:
                    frame.pop2();
                    return;
                case RETURN:
                    return;
                case GETSTATIC, GETFIELD: {
                    Symbol<Type> type = pool.fieldAt(bs.readCPI(bci)).getType(pool);
                    if (opcode == GETFIELD) {
                        frame.pop();
                    }
                    frame.push(Types.getJavaKind(type));
                    break;
                }
                case PUTSTATIC, PUTFIELD: {
                    Symbol<Type> type = pool.fieldAt(bs.readCPI(bci)).getType(pool);
                    if (Types.getJavaKind(type).needsTwoSlots()) {
                        frame.pop2();
                    } else {
                        frame.pop();
                    }
                    if (opcode == PUTFIELD) {
                        frame.pop();
                    }
                    break;
                }
                case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE: {
                    handleInvoke(frame, bci, opcode, true);
                    break;
                }
                case NEWARRAY, ANEWARRAY:
                    frame.pop();
                    frame.push(JavaKind.Object);
                    break;
                case MULTIANEWARRAY: {
                    int dim = bs.readUByte(bci + 3);
                    for (int i = 0; i < dim; i++) {
                        frame.pop();
                    }
                    frame.push(JavaKind.Object);
                    break;
                }
                case INVOKEDYNAMIC: {
                    InvokeDynamicConstant indy = pool.indyAt(bs.readCPI(bci));
                    Symbol<Type>[] sig = lang.getSignatures().parsed(indy.getSignature(pool));
                    popSignature(sig, true, frame);
                    frame.push(Signatures.returnKind(sig));
                    break;
                }
                default:
                    throw EspressoError.shouldNotReachHere(Bytecodes.nameOf(opcode));

            }
            la.performPostBCI(frame, bci);
            if (Bytecodes.canTrap(opcode)) {
                ExceptionHandler[] handlers = m.getExceptionHandlers();
                for (ExceptionHandler handler : handlers) {
                    if (handler.covers(bci)) {
                        Builder copy = frame.copy();
                        copy.clearStack();
                        copy.push(JavaKind.Object);
                        branch(bci, handler.getHandlerBCI(), copy);
                    }
                }
            }
            int next = bs.nextBCI(bci);
            la.performOnEdge(frame, bci, next);
            bci = next;
        }
    }

    private void handleInvoke(Builder frame, int bci, int opcode, boolean pushResult) {
        MethodRefConstant ref = pool.methodAt(bs.readCPI(bci));
        Symbol<Type>[] sig = lang.getSignatures().parsed(ref.getSignature(pool));
        popSignature(sig, opcode == INVOKESTATIC, frame);
        if (pushResult) {
            frame.push(Signatures.returnKind(sig));
        }
    }

    private boolean merge(int bci, Builder frame) {
        Builder targetState = states[bci];
        if (targetState == null) {
            registerState(bci, frame.copy());
            return false;
        }
        Builder merged = frame.mergeInto(targetState, bci);
        if (merged == targetState) {
            return true;
        }
        assert la.isEmpty();
        registerState(bci, merged);
        return false;
    }

    private void ldc(int bci, Builder frame) {
        char cpi = bs.readCPI(bci);
        ConstantPool.Tag tag = pool.tagAt(cpi);
        switch (tag) {
            case INTEGER -> frame.push(JavaKind.Int);
            case FLOAT -> frame.push(JavaKind.Float);
            case LONG -> frame.push(JavaKind.Long);
            case DOUBLE -> frame.push(JavaKind.Double);
            case CLASS, STRING, METHODHANDLE, METHODTYPE -> frame.push(JavaKind.Object);
            case DYNAMIC -> frame.push(Types.getJavaKind(((DynamicConstant) pool.at(cpi)).getTypeSymbol(pool)));
            default -> throw EspressoError.shouldNotReachHere(tag.toString());
        }
    }

    private void branch(int from, int target, Builder f) {
        assert f.isWorking();
        Builder targetState = states[target];
        if (targetState == null) {
            Builder newState = f.copy();
            la.performOnEdge(newState, from, target);
            registerState(target, newState);
            push(target);
            return;
        }
        // The state stored in the states has already been applied liveness analysis.
        assert targetState.isRecord();
        Builder merged = f.mergeInto(targetState, target);
        if (merged == targetState) {
            return;
        }
        assert la.isEmpty();
        registerState(target, merged);
        processStatus.clear(target);
        push(target);
    }

    private void registerState(int target, Builder newState) {
        assert newState.isWorking();
        assert (states[target] == null || (newState.sameTop(states[target])));
        Builder copy = newState.copy();
        copy.setBci(target);
        assert copy.isRecord();
        states[target] = copy;
    }

    private void push(int bci) {
        assert states[bci] != null;
        assert states[bci].isRecord();
        queue.push(bci);
    }

    private int pop() {
        int next = queue.pop();
        assert states[next] != null && states[next].isRecord();
        return next;
    }
}
