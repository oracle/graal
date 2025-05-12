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
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.CHECKCAST;
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
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.NOP;
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

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.analysis.frame.EspressoFrameDescriptor.Builder;
import com.oracle.truffle.espresso.analysis.liveness.LivenessAnalysis;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.ExceptionHandler;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.attributes.StackMapTableAttribute;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeSwitch;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.constantpool.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.shared.verifier.StackMapFrameParser;
import com.oracle.truffle.espresso.shared.verifier.VerificationException;
import com.oracle.truffle.espresso.shared.verifier.VerificationTypeInfo;

/**
 * Statically analyses bytecodes to produce a {@link EspressoFrameDescriptor frame description} for
 * the given BCI.
 */
public final class FrameAnalysis implements StackMapFrameParser.FrameBuilder<Builder, FrameAnalysis> {
    private final EspressoLanguage lang;
    private final Method.MethodVersion m;
    private final Function<Symbol<Type>, Klass> klassResolver;

    private final LivenessAnalysis la;
    private final BytecodeStream bs;
    private final RuntimeConstantPool pool;

    private final int targetBci;

    private final Builder[] states;
    private final BitSet branchTargets;
    private final BitSet processStatus;

    private final ArrayDeque<Integer> queue = new ArrayDeque<>(2);

    // Having stack maps means we can trust operands in the record to be as precise as needed.
    boolean withStackMaps;

    @TruffleBoundary
    public static EspressoFrameDescriptor apply(Method.MethodVersion m, int bci, LivenessAnalysis la) {
        try {
            return new FrameAnalysis(bci, m, la).apply();
        } catch (Exception e) {
            throw EspressoError.shouldNotReachHere(String.format("Failed suspension during frame analysis of method '%s'", m), e);
        }
    }

    public ConstantPool pool() {
        return pool;
    }

    public ObjectKlass targetKlass() {
        return m.getDeclaringKlass();
    }

    public BytecodeStream stream() {
        return bs;
    }

    private FrameAnalysis(int targetBci, Method.MethodVersion m, LivenessAnalysis la) {
        this.lang = m.getMethod().getLanguage();
        this.la = la;
        this.bs = new BytecodeStream(m.getOriginalCode());
        this.targetBci = targetBci;
        this.states = new Builder[bs.endBCI()];
        this.pool = m.getPool();
        this.m = m;
        this.branchTargets = new BitSet(bs.endBCI());
        this.processStatus = new BitSet(bs.endBCI());
        ObjectKlass declaringKlass = m.getDeclaringKlass();
        this.klassResolver = (type) -> declaringKlass.getMeta().resolveSymbolOrFail(type, declaringKlass.getDefiningClassLoader(), declaringKlass.protectionDomain());
    }

    private static void popSignature(Symbol<Type>[] sig, boolean isStatic, Builder frame) {
        for (Symbol<Type> t : SignatureSymbols.iterable(sig, true, false)) {
            JavaKind k = TypeSymbols.getJavaKind(t).getStackKind();
            frame.pop(k);
        }
        if (!isStatic) {
            frame.pop(JavaKind.Object);
        }
    }

    private EspressoFrameDescriptor apply() {
        markBranchTargets();

        buildInitialFrames();

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
        // Since the state is before the return, execution in BytecodeNode wouldn't have been able
        // to update its 'top', so we use the top from before having popped arguments.
        assert Bytecodes.isInvoke(opcode);
        int top = state.top();
        handleInvoke(state, targetBci, opcode, false, opcode == INVOKEDYNAMIC ? ConstantPool.Tag.INVOKEDYNAMIC : ConstantPool.Tag.METHOD_REF);
        return state.build(EspressoFrame.startingStackOffset(m.getMaxLocals()) + top);
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
                for (int i = 0; i < helper.numberOfCases(bs, bci); i++) {
                    branchTargets.set(helper.targetAt(bs, bci, i));
                }
                branchTargets.set(helper.defaultTarget(bs, bci));
            }
            if (bci == targetBci && Bytecodes.isInvoke(opcode)) {
                validTarget = true;
            }
            bci = bs.nextBCI(bci);
        }

        EspressoFrameDescriptor.guarantee(validTarget, "Target bci is not a valid bytecode.", m.getDeclaringKlass().getMeta());

        ExceptionHandler[] handlers = m.getExceptionHandlers();
        for (ExceptionHandler handler : handlers) {
            branchTargets.set(handler.getHandlerBCI());
        }
    }

    private void buildInitialFrames() {
        Builder frame = new Builder(m.getMaxLocals(), m.getMaxStackSize());
        Symbol<Type>[] sig = m.getMethod().getParsedSignature();
        int receiverShift = 0;
        if (!m.isStatic()) {
            frame.putLocal(0, FrameType.forType(m.getDeclaringKlass().getType()));
            receiverShift = 1;
        }
        int localPos = 0;
        for (int sigPos = 0; sigPos < SignatureSymbols.parameterCount(sig); sigPos++) {
            Symbol<Type> type = SignatureSymbols.parameterType(sig, sigPos);
            FrameType ft;
            ft = FrameType.forType(type);
            frame.putLocal(receiverShift + localPos, ft);
            if (ft.kind().needsTwoSlots()) {
                localPos++;
                frame.putLocal(receiverShift + localPos, FrameType.ILLEGAL);
            }
            localPos++;
        }
        la.onStart(frame);
        frame.setBci(0);
        assert frame.isRecord();
        states[0] = frame;
        StackMapTableAttribute stackMapFrame = m.getCodeAttribute().getStackMapFrame();
        if (m.getPool().getMajorVersion() == ClassfileParser.JAVA_6_VERSION ||
                        stackMapFrame == null || stackMapFrame == StackMapTableAttribute.EMPTY) {
            // No stack maps: older class files, or classes that skips verification
            // Note, we do not trust classfile ver == 50, as they can have wrong maps, but pass
            // verification due to the fallback mechanism.
            return;
        }
        withStackMaps = true;
        // localPos overshoots by 1
        int lastLocal = receiverShift + localPos - 1;
        try {
            StackMapFrameParser.parse(this, stackMapFrame, frame, lastLocal);
        } catch (VerificationException e) {
            throw EspressoError.shouldNotReachHere("Class should have been verified!");
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
                    break;
                case CHECKCAST: {
                    frame.pop();
                    Symbol<Type> type = queryPoolType(bs.readCPI(bci), ConstantPool.Tag.CLASS);
                    frame.push(FrameType.forType(type));
                    break;
                }
                case ACONST_NULL:
                    frame.push(FrameType.NULL);
                    break;
                case ALOAD: {
                    FrameType ft = frame.getLocal(bs.readLocalIndex(bci));
                    frame.push(ft);
                    break;
                }
                case ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3: {
                    FrameType ft = frame.getLocal(opcode - ALOAD_0);
                    frame.push(ft);
                    break;
                }
                case NEW: {
                    Symbol<Type> type = queryPoolType(bs.readCPI(bci), ConstantPool.Tag.CLASS);
                    frame.push(FrameType.forType(type));
                    break;
                }
                case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5: // fallthrough
                case BIPUSH, SIPUSH: // fallthrough
                case ILOAD, ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3:
                    frame.push(FrameType.INT);
                    break;
                case LCONST_0, LCONST_1, LLOAD, LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3:
                    frame.push(FrameType.LONG);
                    break;
                case FCONST_0, FCONST_1, FCONST_2, FLOAD, FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3:
                    frame.push(FrameType.FLOAT);
                    break;
                case DCONST_0, DCONST_1, DLOAD, DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3:
                    frame.push(FrameType.DOUBLE);
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
                    frame.push(FrameType.INT);
                    break;
                case LALOAD:
                    frame.pop();
                    frame.pop();
                    frame.push(FrameType.LONG);
                    break;
                case FALOAD, FADD, FSUB, FMUL, FDIV, FREM:
                    frame.pop();
                    frame.pop();
                    frame.push(FrameType.FLOAT);
                    break;
                case DALOAD:
                    frame.pop();
                    frame.pop();
                    frame.push(FrameType.DOUBLE);
                    break;
                case AALOAD: {
                    frame.pop();
                    FrameType array = frame.pop();
                    FrameType ft = FrameType.forType(lang.getTypes().getComponentType(array.type()));
                    frame.push(ft);
                    break;
                }
                case ISTORE:
                    frame.pop();
                    frame.putLocal(bs.readLocalIndex(bci), FrameType.INT);
                    break;
                case LSTORE:
                    frame.pop2();
                    frame.putLocal(bs.readLocalIndex(bci), FrameType.LONG);
                    break;
                case FSTORE:
                    frame.pop();
                    frame.putLocal(bs.readLocalIndex(bci), FrameType.FLOAT);
                    break;
                case DSTORE:
                    frame.pop2();
                    frame.putLocal(bs.readLocalIndex(bci), FrameType.DOUBLE);
                    break;
                case ASTORE: {
                    FrameType ft = frame.pop();
                    frame.putLocal(bs.readLocalIndex(bci), ft);
                    break;
                }
                case ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3:
                    frame.pop();
                    frame.putLocal(opcode - ISTORE_0, FrameType.INT);
                    break;
                case LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3:
                    frame.pop2();
                    frame.putLocal(opcode - LSTORE_0, FrameType.LONG);
                    break;
                case FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3:
                    frame.pop();
                    frame.putLocal(opcode - FSTORE_0, FrameType.FLOAT);
                    break;
                case DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3:
                    frame.pop2();
                    frame.putLocal(opcode - DSTORE_0, FrameType.DOUBLE);
                    break;
                case ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3: {
                    FrameType ft = frame.pop();
                    frame.putLocal(opcode - ASTORE_0, ft);
                    break;
                }
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
                    FrameType k = frame.pop();
                    frame.push(k, false);
                    frame.push(k, false);
                    break;
                }
                case DUP_X1: {
                    FrameType v1 = frame.pop();
                    FrameType v2 = frame.pop();
                    frame.push(v1, false);
                    frame.push(v2, false);
                    frame.push(v1, false);
                    break;
                }
                case DUP_X2: {
                    FrameType v1 = frame.pop();
                    FrameType v2 = frame.pop();
                    FrameType v3 = frame.pop();
                    frame.push(v1, false);
                    frame.push(v3, false);
                    frame.push(v2, false);
                    frame.push(v1, false);
                    break;
                }
                case DUP2: {
                    FrameType v1 = frame.pop();
                    FrameType v2 = frame.pop();
                    frame.push(v2, false);
                    frame.push(v1, false);
                    frame.push(v2, false);
                    frame.push(v1, false);
                    break;
                }
                case DUP2_X1: {
                    FrameType v1 = frame.pop();
                    FrameType v2 = frame.pop();
                    FrameType v3 = frame.pop();
                    frame.push(v2, false);
                    frame.push(v1, false);
                    frame.push(v3, false);
                    frame.push(v2, false);
                    frame.push(v1, false);
                    break;
                }
                case DUP2_X2: {
                    FrameType v1 = frame.pop();
                    FrameType v2 = frame.pop();
                    FrameType v3 = frame.pop();
                    FrameType v4 = frame.pop();
                    frame.push(v2, false);
                    frame.push(v1, false);
                    frame.push(v4, false);
                    frame.push(v3, false);
                    frame.push(v2, false);
                    frame.push(v1, false);
                    break;
                }
                case SWAP: {
                    FrameType k1 = frame.pop();
                    FrameType k2 = frame.pop();
                    frame.push(k1, false);
                    frame.push(k2, false);
                    break;
                }
                case LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR:
                    frame.pop2();
                    frame.pop2();
                    frame.push(FrameType.LONG);
                    break;
                case LSHL, LSHR, LUSHR:
                    frame.pop();
                    frame.pop2();
                    frame.push(FrameType.LONG);
                    break;
                case DADD, DSUB, DMUL, DDIV, DREM:
                    frame.pop2();
                    frame.pop2();
                    frame.push(FrameType.DOUBLE);
                    break;
                case INEG, F2I, I2B, I2C, I2S: // fallthrough
                case ARRAYLENGTH: // fallthrough
                case INSTANCEOF:
                    frame.pop();
                    frame.push(FrameType.INT);
                    break;
                case LNEG, D2L:
                    frame.pop2();
                    frame.push(FrameType.LONG);
                    break;
                case FNEG, I2F:
                    frame.pop();
                    frame.push(FrameType.FLOAT);
                    break;
                case DNEG, L2D:
                    frame.pop2();
                    frame.push(FrameType.DOUBLE);
                    break;
                case I2L, F2L:
                    frame.pop();
                    frame.push(FrameType.LONG);
                    break;
                case I2D, F2D:
                    frame.pop();
                    frame.push(FrameType.DOUBLE);
                    break;
                case L2I, D2I:
                    frame.pop2();
                    frame.push(FrameType.INT);
                    break;
                case L2F, D2F:
                    frame.pop2();
                    frame.push(FrameType.FLOAT);
                    break;
                case LCMP, DCMPL, DCMPG:
                    frame.pop2();
                    frame.pop2();
                    frame.push(FrameType.INT);
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
                    BytecodeSwitch bytecodeSwitch = BytecodeSwitch.get(opcode);
                    for (int i = 0; i < bytecodeSwitch.numberOfCases(bs, bci); i++) {
                        branch(bci, bytecodeSwitch.targetAt(bs, bci, i), frame);
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
                    Symbol<Type> type = queryPoolType(bs.readCPI(bci), ConstantPool.Tag.FIELD_REF);
                    if (opcode == GETFIELD) {
                        frame.pop();
                    }
                    frame.push(FrameType.forType(type));
                    break;
                }
                case PUTSTATIC, PUTFIELD: {
                    Symbol<Type> type = queryPoolType(bs.readCPI(bci), ConstantPool.Tag.FIELD_REF);
                    if (TypeSymbols.getJavaKind(type).needsTwoSlots()) {
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
                    handleInvoke(frame, bci, opcode, true, ConstantPool.Tag.METHOD_REF);
                    break;
                }
                case NEWARRAY:
                    frame.pop(); // dim
                    frame.push(newPrimitiveArray(bs.readByte(bci)));
                    break;
                case ANEWARRAY: {
                    frame.pop(); // dim
                    Symbol<Type> type = queryPoolType(bs.readCPI(bci), ConstantPool.Tag.CLASS);
                    frame.push(FrameType.forType(lang.getTypes().arrayOf(type)));
                    break;
                }
                case MULTIANEWARRAY: {
                    int dim = bs.readUByte(bci + 3);
                    for (int i = 0; i < dim; i++) {
                        frame.pop();
                    }
                    Symbol<Type> type = queryPoolType(bs.readCPI(bci), ConstantPool.Tag.CLASS);
                    frame.push(FrameType.forType(type));
                    break;
                }
                case INVOKEDYNAMIC: {
                    handleInvoke(frame, bci, opcode, true, ConstantPool.Tag.INVOKEDYNAMIC);
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
                        Symbol<Type> catchType = handler.getCatchType();
                        copy.push(catchType == null ? FrameType.THROWABLE : FrameType.forType(handler.getCatchType()));
                        branch(bci, handler.getHandlerBCI(), copy);
                    }
                }
            }
            int next = bs.nextBCI(bci);
            la.performOnEdge(frame, bci, next);
            bci = next;
        }
    }

    private static FrameType newPrimitiveArray(byte b) {
        switch (b) {
            case Constants.JVM_ArrayType_Boolean:
                return FrameType.forType(Types._boolean_array);
            case Constants.JVM_ArrayType_Char:
                return FrameType.forType(Types._char_array);
            case Constants.JVM_ArrayType_Float:
                return FrameType.forType(Types._float_array);
            case Constants.JVM_ArrayType_Double:
                return FrameType.forType(Types._double_array);
            case Constants.JVM_ArrayType_Byte:
                return FrameType.forType(Types._byte_array);
            case Constants.JVM_ArrayType_Short:
                return FrameType.forType(Types._short_array);
            case Constants.JVM_ArrayType_Int:
                return FrameType.forType(Types._int_array);
            case Constants.JVM_ArrayType_Long:
                return FrameType.forType(Types._long_array);
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    private void handleInvoke(Builder frame, int bci, int opcode, boolean pushResult, ConstantPool.Tag tag) {
        Symbol<Type>[] sig = queryPoolSignature(bs.readCPI(bci), tag);
        popSignature(sig, opcode == INVOKESTATIC || opcode == INVOKEDYNAMIC, frame);
        if (pushResult && SignatureSymbols.returnKind(sig) != JavaKind.Void) {
            frame.push(FrameType.forType(SignatureSymbols.returnType(sig)));
        }
    }

    private boolean merge(int bci, Builder frame) {
        Builder targetState = states[bci];
        if (targetState == null) {
            registerState(bci, frame.copy());
            return false;
        }
        Builder merged = frame.mergeInto(targetState, bci, withStackMaps, klassResolver);
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
            case INTEGER:
                frame.push(FrameType.INT);
                break;
            case FLOAT:
                frame.push(FrameType.FLOAT);
                break;
            case LONG:
                frame.push(FrameType.LONG);
                break;
            case DOUBLE:
                frame.push(FrameType.DOUBLE);
                break;
            case CLASS:
                frame.push(FrameType.forType(Types.java_lang_Class));
                break;
            case STRING:
                frame.push(FrameType.forType(Types.java_lang_String));
                break;
            case METHODHANDLE:
                frame.push(FrameType.forType(Types.java_lang_invoke_MethodHandle));
                break;
            case METHODTYPE:
                frame.push(FrameType.forType(Types.java_lang_invoke_MethodType));
                break;
            case DYNAMIC: {
                Symbol<Type> t = pool.dynamicType(cpi);
                frame.push(FrameType.forType(t));
                break;
            }
            default:
                throw EspressoError.shouldNotReachHere(tag.toString());
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
        Builder merged = f.mergeInto(targetState, target, withStackMaps, klassResolver);
        la.performOnEdge(merged, from, target);
        if (merged == targetState) {
            if (!processStatus.get(target)) {
                push(target);
            }
            return;
        }
        /*
         * We can only run into the case that some slots fail to merge if liveness analysis was not
         * enabled and stack maps are not in use. Both liveness analysis and stack maps should have
         * already made such slots illegal.
         */
        assert la.isEmpty();
        assert !withStackMaps;
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

    @Override
    public void registerStackMapFrame(int bci, Builder frame) {
        registerState(bci, frame);
    }

    @Override
    public StackMapFrameParser.FrameAndLocalEffect<Builder, FrameAnalysis> newFullFrame(VerificationTypeInfo[] stack, VerificationTypeInfo[] locals, int lastLocal) {
        Builder fullFrame = new Builder(m.getMaxLocals(), m.getMaxStackSize());
        for (VerificationTypeInfo vti : stack) {
            FrameType k = EspressoFrameDescriptor.fromTypeInfo(vti, this);
            fullFrame.push(k);
        }
        int pos = 0;
        for (VerificationTypeInfo vti : locals) {
            FrameType k = EspressoFrameDescriptor.fromTypeInfo(vti, this);
            fullFrame.putLocal(pos, k);
            if (k.kind().needsTwoSlots()) {
                pos++;
                fullFrame.clear(pos);
            }
            pos++;
        }
        return new StackMapFrameParser.FrameAndLocalEffect<>(fullFrame,
                        // pos overshoots the actual last local position by one.
                        (pos - 1) - lastLocal);
    }

    @Override
    public String toExternalString() {
        return m.getDeclaringKlass().getExternalName() + '.' + m.getMethod().getNameAsString();
    }

    // Since we are in runtime, we can shortcut parsing if some constants are already resolved.

    private Symbol<Type> queryPoolType(int cpi, ConstantPool.Tag tag) {
        switch (tag) {
            case CLASS: {
                if (pool.isResolutionSuccessAt(cpi)) {
                    return pool.resolvedKlassAt(m.getDeclaringKlass(), cpi).getType();
                }
                return lang.getTypes().fromClassNameEntry(pool.className(cpi));
            }
            case FIELD_REF: {
                if (pool.isResolutionSuccessAt(cpi)) {
                    return pool.resolvedFieldAt(m.getDeclaringKlass(), cpi).getType();
                }
                return pool.fieldType(cpi);
            }
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    private Symbol<Type>[] queryPoolSignature(int cpi, ConstantPool.Tag tag) {
        switch (tag) {
            case METHOD_REF: {
                if (pool.isResolutionSuccessAt(cpi)) {
                    return pool.resolvedMethodAt(m.getDeclaringKlass(), cpi).getParsedSignature();
                }
                return lang.getSignatures().parsed(pool.methodSignature(cpi));
            }
            case INVOKEDYNAMIC: {
                if (pool.isResolutionSuccessAt(cpi)) {
                    return pool.peekResolvedInvokeDynamic(cpi).getParsedSignature();
                }
                return lang.getSignatures().parsed(pool.invokeDynamicSignature(cpi));
            }
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }
}
