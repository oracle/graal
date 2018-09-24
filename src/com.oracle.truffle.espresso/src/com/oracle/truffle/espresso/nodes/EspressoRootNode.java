/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

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
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RET;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SIPUSH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SWAP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.TABLESWITCH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.WIDE;
import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.bytecode.InterpreterToVM;
import com.oracle.truffle.espresso.bytecode.OperandStack;
import com.oracle.truffle.espresso.classfile.ClassConstant;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.DoubleConstant;
import com.oracle.truffle.espresso.classfile.FloatConstant;
import com.oracle.truffle.espresso.classfile.IntegerConstant;
import com.oracle.truffle.espresso.classfile.LongConstant;
import com.oracle.truffle.espresso.classfile.PoolConstant;
import com.oracle.truffle.espresso.classfile.StringConstant;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class EspressoRootNode extends RootNode {
    private final TruffleLanguage<EspressoContext> language;
    private final MethodInfo method;
    private final InterpreterToVM vm;

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final FrameSlot[] locals;

    private final FrameSlot stackSlot;

    private final BytecodeStream bs;

    @Override
    public String getName() {
        // TODO(peterssen): Set proper location.
        return getMethod().getDeclaringClass().getName() +
                        "." + getMethod().getName() +
                        " " + getMethod().getSignature().toString();

    }

    @CompilerDirectives.TruffleBoundary
    public EspressoRootNode(TruffleLanguage<EspressoContext> language, MethodInfo method, InterpreterToVM vm) {
        super(language, initFrameDescriptor(method));
        this.language = language;
        this.method = method;
        this.vm = vm;
        this.bs = new BytecodeStream(method.getCode());

        FrameSlot[] slots = getFrameDescriptor().getSlots().toArray(new FrameSlot[0]);
        locals = Arrays.copyOf(slots, slots.length - 1);
        stackSlot = slots[slots.length - 1];
    }

    public InterpreterToVM getVm() {
        return vm;
    }

    public MethodInfo getMethod() {
        return method;
    }

    private static FrameDescriptor initFrameDescriptor(MethodInfo method) {
        FrameDescriptor descriptor = new FrameDescriptor();
        int maxLocals = method.getMaxLocals();
        for (int i = 0; i < maxLocals; ++i) {
            descriptor.addFrameSlot(i);
        }
        // Operand stack
        descriptor.addFrameSlot(maxLocals);
        return descriptor;
    }

    @Override
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    public Object execute(VirtualFrame frame) {
        // TODO(peterssen): Inline this object.
        int curBCI = 0;

        // slots = locals... + stack
        final OperandStack stack = new OperandStack(method.getMaxStackSize());
        frame.setObject(stackSlot, stack);

        initArguments(frame, locals);

        loop: while (true) {
            try {
                switch (bs.currentBC(curBCI)) {
                    case NOP:
                        break;
                    case ACONST_NULL:
                        stack.pushObject(StaticObject.NULL);
                        break;
                    case ICONST_M1:
                        stack.pushInt(-1);
                        break;
                    case ICONST_0:
                        stack.pushInt(0);
                        break;
                    case ICONST_1:
                        stack.pushInt(1);
                        break;
                    case ICONST_2:
                        stack.pushInt(2);
                        break;
                    case ICONST_3:
                        stack.pushInt(3);
                        break;
                    case ICONST_4:
                        stack.pushInt(4);
                        break;
                    case ICONST_5:
                        stack.pushInt(5);
                        break;
                    case LCONST_0:
                        stack.pushLong(0L);
                        break;
                    case LCONST_1:
                        stack.pushLong(1L);
                        break;
                    case FCONST_0:
                        stack.pushFloat(0.0F);
                        break;
                    case FCONST_1:
                        stack.pushFloat(1.0F);
                        break;
                    case FCONST_2:
                        stack.pushFloat(2.0F);
                        break;
                    case DCONST_0:
                        stack.pushDouble(0.0D);
                        break;
                    case DCONST_1:
                        stack.pushDouble(1.0D);
                        break;
                    case BIPUSH:
                        stack.pushInt(bs.readByte(curBCI));
                        break;
                    case SIPUSH:
                        stack.pushInt(bs.readShort(curBCI));
                        break;
                    case LDC:
                    case LDC_W:
                    case LDC2_W:
                        pushPoolConstant(stack, bs.readCPI(curBCI));
                        break;
                    case ILOAD:
                        stack.pushInt(frame.getInt(locals[bs.readLocalIndex(curBCI)]));
                        break;
                    case LLOAD:
                        stack.pushLong(frame.getLong(locals[bs.readLocalIndex(curBCI)]));
                        break;
                    case FLOAD:
                        stack.pushFloat(frame.getFloat(locals[bs.readLocalIndex(curBCI)]));
                        break;
                    case DLOAD:
                        stack.pushDouble(frame.getDouble(locals[bs.readLocalIndex(curBCI)]));
                        break;
                    case ALOAD:
                        stack.pushObject(frame.getObject(locals[bs.readLocalIndex(curBCI)]));
                        break;
                    case ILOAD_0:
                        stack.pushInt(frame.getInt(locals[0]));
                        break;
                    case ILOAD_1:
                        stack.pushInt(frame.getInt(locals[1]));
                        break;
                    case ILOAD_2:
                        stack.pushInt(frame.getInt(locals[2]));
                        break;
                    case ILOAD_3:
                        stack.pushInt(frame.getInt(locals[3]));
                        break;
                    case LLOAD_0:
                        stack.pushLong(frame.getLong(locals[0]));
                        break;
                    case LLOAD_1:
                        stack.pushLong(frame.getLong(locals[1]));
                        break;
                    case LLOAD_2:
                        stack.pushLong(frame.getLong(locals[2]));
                        break;
                    case LLOAD_3:
                        stack.pushLong(frame.getLong(locals[3]));
                        break;
                    case FLOAD_0:
                        stack.pushFloat(frame.getFloat(locals[0]));
                        break;
                    case FLOAD_1:
                        stack.pushFloat(frame.getFloat(locals[1]));
                        break;
                    case FLOAD_2:
                        stack.pushFloat(frame.getFloat(locals[2]));
                        break;
                    case FLOAD_3:
                        stack.pushFloat(frame.getFloat(locals[3]));
                        break;
                    case DLOAD_0:
                        stack.pushDouble(frame.getDouble(locals[0]));
                        break;
                    case DLOAD_1:
                        stack.pushDouble(frame.getDouble(locals[1]));
                        break;
                    case DLOAD_2:
                        stack.pushDouble(frame.getDouble(locals[2]));
                        break;
                    case DLOAD_3:
                        stack.pushDouble(frame.getDouble(locals[3]));
                        break;
                    case ALOAD_0:
                        stack.pushObject(frame.getObject(locals[0]));
                        break;
                    case ALOAD_1:
                        stack.pushObject(frame.getObject(locals[1]));
                        break;
                    case ALOAD_2:
                        stack.pushObject(frame.getObject(locals[2]));
                        break;
                    case ALOAD_3:
                        stack.pushObject(frame.getObject(locals[3]));
                        break;
                    case IALOAD:
                        stack.pushInt(vm.getArrayInt(stack.popInt(), nullCheck(stack.popObject())));
                        break;
                    case LALOAD:
                        stack.pushLong(vm.getArrayLong(stack.popInt(), nullCheck(stack.popObject())));
                        break;
                    case FALOAD:
                        stack.pushFloat(vm.getArrayFloat(stack.popInt(), nullCheck(stack.popObject())));
                        break;
                    case DALOAD:
                        stack.pushDouble(vm.getArrayDouble(stack.popInt(), nullCheck(stack.popObject())));
                        break;
                    case AALOAD:
                        stack.pushObject(vm.getArrayObject(stack.popInt(), nullCheck(stack.popObject())));
                        break;
                    case BALOAD:
                        stack.pushInt(vm.getArrayByte(stack.popInt(), nullCheck(stack.popObject())));
                        break;
                    case CALOAD:
                        stack.pushInt(vm.getArrayChar(stack.popInt(), nullCheck(stack.popObject())));
                        break;
                    case SALOAD:
                        stack.pushInt(vm.getArrayShort(stack.popInt(), nullCheck(stack.popObject())));
                        break;
                    case ISTORE:
                        frame.setInt(locals[bs.readLocalIndex(curBCI)], stack.popInt());
                        break;
                    case LSTORE:
                        frame.setLong(locals[bs.readLocalIndex(curBCI)], stack.popLong());
                        break;
                    case FSTORE:
                        frame.setFloat(locals[bs.readLocalIndex(curBCI)], stack.popFloat());
                        break;
                    case DSTORE:
                        frame.setDouble(locals[bs.readLocalIndex(curBCI)], stack.popDouble());
                        break;
                    case ASTORE:
                        frame.setObject(locals[bs.readLocalIndex(curBCI)], stack.popObject());
                        break;
                    case ISTORE_0:
                        frame.setInt(locals[0], stack.popInt());
                        break;
                    case ISTORE_1:
                        frame.setInt(locals[1], stack.popInt());
                        break;
                    case ISTORE_2:
                        frame.setInt(locals[2], stack.popInt());
                        break;
                    case ISTORE_3:
                        frame.setInt(locals[3], stack.popInt());
                        break;
                    case LSTORE_0:
                        frame.setLong(locals[0], stack.popLong());
                        break;
                    case LSTORE_1:
                        frame.setLong(locals[1], stack.popLong());
                        break;
                    case LSTORE_2:
                        frame.setLong(locals[2], stack.popLong());
                        break;
                    case LSTORE_3:
                        frame.setLong(locals[3], stack.popLong());
                        break;
                    case FSTORE_0:
                        frame.setFloat(locals[0], stack.popFloat());
                        break;
                    case FSTORE_1:
                        frame.setFloat(locals[1], stack.popFloat());
                        break;
                    case FSTORE_2:
                        frame.setFloat(locals[2], stack.popFloat());
                        break;
                    case FSTORE_3:
                        frame.setFloat(locals[3], stack.popFloat());
                        break;
                    case DSTORE_0:
                        frame.setDouble(locals[0], stack.popDouble());
                        break;
                    case DSTORE_1:
                        frame.setDouble(locals[1], stack.popDouble());
                        break;
                    case DSTORE_2:
                        frame.setDouble(locals[2], stack.popDouble());
                        break;
                    case DSTORE_3:
                        frame.setDouble(locals[3], stack.popDouble());
                        break;
                    case ASTORE_0:
                        frame.setObject(locals[0], stack.popObject());
                        break;
                    case ASTORE_1:
                        frame.setObject(locals[1], stack.popObject());
                        break;
                    case ASTORE_2:
                        frame.setObject(locals[2], stack.popObject());
                        break;
                    case ASTORE_3:
                        frame.setObject(locals[3], stack.popObject());
                        break;
                    case IASTORE:
                        vm.setArrayInt(stack.popInt(), stack.popInt(), nullCheck(stack.popObject()));
                        break;
                    case LASTORE:
                        vm.setArrayLong(stack.popLong(), stack.popInt(), nullCheck(stack.popObject()));
                        break;
                    case FASTORE:
                        vm.setArrayFloat(stack.popFloat(), stack.popInt(), nullCheck(stack.popObject()));
                        break;
                    case DASTORE:
                        vm.setArrayDouble(stack.popDouble(), stack.popInt(), nullCheck(stack.popObject()));
                        break;
                    case AASTORE:
                        vm.setArrayObject(stack.popObject(), stack.popInt(), nullCheck(stack.popObject()));
                        break;
                    case BASTORE:
                        vm.setArrayByte((byte) stack.popInt(), stack.popInt(), nullCheck(stack.popObject()));
                        break;
                    case CASTORE:
                        vm.setArrayChar((char) stack.popInt(), stack.popInt(), nullCheck(stack.popObject()));
                        break;
                    case SASTORE:
                        vm.setArrayShort((short) stack.popInt(), stack.popInt(), nullCheck(stack.popObject()));
                        break;
                    case POP:
                        stack.popVoid(1);
                        break;
                    case POP2:
                        stack.popVoid(2);
                        break;
                    case DUP:
                        stack.dup1();
                        break;
                    case DUP_X1:
                        stack.dupx1();
                        break;
                    case DUP_X2:
                        stack.dupx2();
                        break;
                    case DUP2:
                        stack.dup2();
                        break;
                    case DUP2_X1:
                        stack.dup2x1();
                        break;
                    case DUP2_X2:
                        stack.dup2x2();
                        break;
                    case SWAP:
                        stack.swapSingle();
                        break;
                    case IADD:
                        stack.pushInt(stack.popInt() + stack.popInt());
                        break;
                    case LADD:
                        stack.pushLong(stack.popLong() + stack.popLong());
                        break;
                    case FADD:
                        stack.pushFloat(stack.popFloat() + stack.popFloat());
                        break;
                    case DADD:
                        stack.pushDouble(stack.popDouble() + stack.popDouble());
                        break;
                    case ISUB:
                        stack.pushInt(-stack.popInt() + stack.popInt());
                        break;
                    case LSUB:
                        stack.pushLong(-stack.popLong() + stack.popLong());
                        break;
                    case FSUB:
                        stack.pushFloat(-stack.popFloat() + stack.popFloat());
                        break;
                    case DSUB:
                        stack.pushDouble(-stack.popDouble() + stack.popDouble());
                        break;
                    case IMUL:
                        stack.pushInt(stack.popInt() * stack.popInt());
                        break;
                    case LMUL:
                        stack.pushLong(stack.popLong() * stack.popLong());
                        break;
                    case FMUL:
                        stack.pushFloat(stack.popFloat() * stack.popFloat());
                        break;
                    case DMUL:
                        stack.pushDouble(stack.popDouble() * stack.popDouble());
                        break;
                    case IDIV:
                        stack.pushInt(divInt(checkNonZero(stack.popInt()), stack.popInt()));
                        break;
                    case LDIV:
                        stack.pushLong(divLong(checkNonZero(stack.popLong()), stack.popLong()));
                        break;
                    case FDIV:
                        stack.pushFloat(divFloat(stack.popFloat(), stack.popFloat()));
                        break;
                    case DDIV:
                        stack.pushDouble(divDouble(stack.popDouble(), stack.popDouble()));
                        break;
                    case IREM:
                        stack.pushInt(remInt(checkNonZero(stack.popInt()), stack.popInt()));
                        break;
                    case LREM:
                        stack.pushLong(remLong(checkNonZero(stack.popLong()), stack.popLong()));
                        break;
                    case FREM:
                        stack.pushFloat(remFloat(stack.popFloat(), stack.popFloat()));
                        break;
                    case DREM:
                        stack.pushDouble(remDouble(stack.popDouble(), stack.popDouble()));
                        break;
                    case INEG:
                        stack.pushInt(-stack.popInt());
                        break;
                    case LNEG:
                        stack.pushLong(-stack.popLong());
                        break;
                    case FNEG:
                        stack.pushFloat(-stack.popFloat());
                        break;
                    case DNEG:
                        stack.pushDouble(-stack.popDouble());
                        break;
                    case ISHL:
                        stack.pushInt(shiftLeftInt(stack.popInt(), stack.popInt()));
                        break;
                    case LSHL:
                        stack.pushLong(shiftLeftLong(stack.popInt(), stack.popLong()));
                        break;
                    case ISHR:
                        stack.pushInt(shiftRightSignedInt(stack.popInt(), stack.popInt()));
                        break;
                    case LSHR:
                        stack.pushLong(shiftRightSignedLong(stack.popInt(), stack.popLong()));
                        break;
                    case IUSHR:
                        stack.pushInt(shiftRightUnsignedInt(stack.popInt(), stack.popInt()));
                        break;
                    case LUSHR:
                        stack.pushLong(shiftRightUnsignedLong(stack.popInt(), stack.popLong()));
                        break;
                    case IAND:
                        stack.pushInt(stack.popInt() & stack.popInt());
                        break;
                    case LAND:
                        stack.pushLong(stack.popLong() & stack.popLong());
                        break;
                    case IOR:
                        stack.pushInt(stack.popInt() | stack.popInt());
                        break;
                    case LOR:
                        stack.pushLong(stack.popLong() | stack.popLong());
                        break;
                    case IXOR:
                        stack.pushInt(stack.popInt() ^ stack.popInt());
                        break;
                    case LXOR:
                        stack.pushLong(stack.popLong() ^ stack.popLong());
                        break;
                    case IINC:
                        iinc(locals, frame, bs, curBCI);
                        break;
                    case I2L:
                        stack.pushLong(stack.popInt());
                        break;
                    case I2F:
                        stack.pushFloat((float) stack.popInt());
                        break;
                    case I2D:
                        stack.pushDouble((double) stack.popInt());
                        break;
                    case L2I:
                        stack.pushInt((int) stack.popLong());
                        break;
                    case L2F:
                        stack.pushFloat((float) stack.popLong());
                        break;
                    case L2D:
                        stack.pushDouble((double) stack.popLong());
                        break;
                    case F2I:
                        stack.pushInt((int) stack.popFloat());
                        break;
                    case F2L:
                        stack.pushLong((long) stack.popFloat());
                        break;
                    case F2D:
                        stack.pushDouble((double) stack.popFloat());
                        break;
                    case D2I:
                        stack.pushInt((int) stack.popDouble());
                        break;
                    case D2L:
                        stack.pushLong((long) stack.popDouble());
                        break;
                    case D2F:
                        stack.pushFloat((float) stack.popDouble());
                        break;
                    case I2B:
                        stack.pushInt((byte) stack.popInt());
                        break;
                    case I2C:
                        stack.pushInt((char) stack.popInt());
                        break;
                    case I2S:
                        stack.pushInt((short) stack.popInt());
                        break;
                    case LCMP:
                        stack.pushInt(compareLong(stack.popLong(), stack.popLong()));
                        break;
                    case FCMPL:
                        stack.pushInt(compareFloatLess(stack.popFloat(), stack.popFloat()));
                        break;
                    case FCMPG:
                        stack.pushInt(compareFloatGreater(stack.popFloat(), stack.popFloat()));
                        break;
                    case DCMPL:
                        stack.pushInt(compareDoubleLess(stack.popDouble(), stack.popDouble()));
                        break;
                    case DCMPG:
                        stack.pushInt(compareDoubleGreater(stack.popDouble(), stack.popDouble()));
                        break;
                    case IFEQ:
                        if (stack.popInt() == 0) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IFNE:
                        if (stack.popInt() != 0) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IFLT:
                        if (stack.popInt() < 0) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IFGE:
                        if (stack.popInt() >= 0) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IFGT:
                        if (stack.popInt() > 0) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IFLE:
                        if (stack.popInt() <= 0) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IF_ICMPEQ:
                        if (stack.popInt() == stack.popInt()) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IF_ICMPNE:
                        if (stack.popInt() != stack.popInt()) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IF_ICMPLT:
                        if (stack.popInt() > stack.popInt()) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IF_ICMPGE:
                        if (stack.popInt() <= stack.popInt()) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IF_ICMPGT:
                        if (stack.popInt() < stack.popInt()) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IF_ICMPLE:
                        if (stack.popInt() >= stack.popInt()) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IF_ACMPEQ:
                        if (stack.popObject() == stack.popObject()) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IF_ACMPNE:
                        if (stack.popObject() != stack.popObject()) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case GOTO:
                    case GOTO_W:
                        curBCI = bs.readBranchDest(curBCI);
                        continue loop;
                    case JSR:
                    case JSR_W:
                        stack.pushInt(bs.currentBCI(curBCI));
                        curBCI = bs.readBranchDest(curBCI);
                        continue loop;
                    case RET:
                        curBCI = frame.getInt(locals[bs.readLocalIndex(curBCI)]);
                        continue loop;
                    case TABLESWITCH:
                        // TODO(peterssen): Inline this.
                        curBCI = tableSwitch(stack, bs, curBCI);
                        continue loop;
                    case LOOKUPSWITCH:
                        curBCI = lookupSwitch(stack, bs, curBCI);
                        continue loop;
                    case IRETURN:
                        return exitMethodAndReturn(stack.popInt());
                    case LRETURN:
                        return exitMethodAndReturn(stack.popLong());
                    case FRETURN:
                        return exitMethodAndReturn(stack.popFloat());
                    case DRETURN:
                        return exitMethodAndReturn(stack.popDouble());
                    case ARETURN:
                        return exitMethodAndReturn(stack.popObject());
                    case RETURN:
                        return exitMethodAndReturn();
                    case GETSTATIC:
                        getField(stack, resolveField(bs.currentBC(curBCI), bs.readCPI(curBCI)), true);
                        break;
                    case PUTSTATIC:
                        putField(stack, resolveField(bs.currentBC(curBCI), bs.readCPI(curBCI)), true);
                        break;
                    case GETFIELD:
                        getField(stack, resolveField(bs.currentBC(curBCI), bs.readCPI(curBCI)), false);
                        break;
                    case PUTFIELD:
                        putField(stack, resolveField(bs.currentBC(curBCI), bs.readCPI(curBCI)), false);
                        break;
                    case INVOKEVIRTUAL:
                        invokeVirtual(stack, resolveMethod(bs.currentBC(curBCI), bs.readCPI(curBCI)));
                        break;
                    case INVOKESPECIAL:
                        invokeSpecial(stack, resolveMethod(bs.currentBC(curBCI), bs.readCPI(curBCI)));
                        break;
                    case INVOKESTATIC:
                        invokeStatic(stack, resolveMethod(bs.currentBC(curBCI), bs.readCPI(curBCI)));
                        break;
                    case INVOKEINTERFACE:
                        invokeInterface(stack, resolveInterfaceMethod(bs.currentBC(curBCI), bs.readCPI(curBCI)));
                        break;
                    case NEW:
                        stack.pushObject(allocateInstance(resolveType(bs.currentBC(curBCI), bs.readCPI(curBCI))));
                        break;
                    case NEWARRAY:
                        stack.pushObject(vm.allocateNativeArray(bs.readByte(curBCI), stack.popInt()));
                        break;
                    case ANEWARRAY:
                        stack.pushObject(
                                        allocateArray(resolveType(bs.currentBC(curBCI), bs.readCPI(curBCI)), stack.popInt()));
                        break;
                    case ARRAYLENGTH:
                        stack.pushInt(vm.arrayLength(nullCheck(stack.popObject())));
                        break;
                    case ATHROW:
                        CompilerDirectives.transferToInterpreter();
                        throw new EspressoException((StaticObject) nullCheck(stack.popObject()));
                    case CHECKCAST:
                        // TODO(peterssen): Implement check cast for arrays and primitive arrays.
                        stack.pushObject(checkCast(stack.popObject(), resolveType(bs.currentBC(curBCI), bs.readCPI(curBCI))));
                        break;
                    case INSTANCEOF:
                        stack.pushInt(
                                        instanceOf(stack.popObject(), resolveType(bs.currentBC(curBCI), bs.readCPI(curBCI))) ? 1 : 0);
                        break;
                    case MONITORENTER:
                        vm.monitorEnter(nullCheck(stack.popObject()));
                        break;
                    case MONITOREXIT:
                        vm.monitorExit(nullCheck(stack.popObject()));
                        break;
                    case WIDE:
                        // should not get here ByteCodeStream.currentBC() should never return this
                        // bytecode.
                        throw EspressoError.shouldNotReachHere();
                    case MULTIANEWARRAY:
                        stack.pushObject(
                                        allocateMultiArray(stack,
                                                        resolveType(bs.currentBC(curBCI), bs.readCPI(curBCI)),
                                                        bs.readUByte(bs.currentBCI(curBCI) + 3)));
                        break;
                    case IFNULL:
                        if (stack.popObject() == StaticObject.NULL) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IFNONNULL:
                        if (stack.popObject() != StaticObject.NULL) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case BREAKPOINT:
                        throw new UnsupportedOperationException("breakpoints not supported.");
                    case INVOKEDYNAMIC:
                        throw new UnsupportedOperationException("invokedynamic not supported."); // design
                                                                                                 // fart
                }
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new RuntimeException(e);
            } catch (EspressoException e) {
                CompilerDirectives.transferToInterpreter();
                ExceptionHandler handler = resolveExceptionHandlers(bs.currentBCI(curBCI), e.getException());
                if (handler != null) {
                    stack.clear();
                    stack.pushObject(e.getException());
                    curBCI = handler.getHandlerBCI();
                    continue loop; // skip bs.next()
                } else {
                    throw e;
                }
            } catch (ArrayIndexOutOfBoundsException | StackOverflowError e) {
                // TODO(peterssen): Handle VM errors like SOE or OutOfMemoryError avoiding allocations and calls.
                CompilerDirectives.transferToInterpreter();
                Meta meta = EspressoLanguage.getCurrentContext().getMeta();
                StaticObject ex;
                if (e instanceof StackOverflowError) {
                    ex = meta.STACK_OVERFLOW_ERROR.allocateInstance();
                    meta(ex).method("<init>", void.class).invokeDirect();
                } else {
                    ex = meta.createEx(ArrayIndexOutOfBoundsException.class);
                }
                ExceptionHandler handler = resolveExceptionHandlers(bs.currentBCI(curBCI), ex);
                if (handler != null) {
                    stack.clear();
                    stack.pushObject(ex);
                    curBCI = handler.getHandlerBCI();
                    continue loop; // skip bs.next()
                } else {
                    throw new EspressoException(ex);
                }
            }
            curBCI = bs.next(curBCI);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private ExceptionHandler resolveExceptionHandlers(int bci, StaticObject ex) {
        ExceptionHandler[] handlers = getMethod().getExceptionHandlers();
        for (ExceptionHandler handler : handlers) {
            if (bci >= handler.getStartBCI() && bci <= handler.getEndBCI()) {
                Klass catchType = null;
                if (!handler.isCatchAll()) {
                    // exception handlers are similar to instanceof bytecodes, so we pass instanceof
                    catchType = resolveType(Bytecodes.INSTANCEOF, (char) handler.catchTypeCPI());
                }
                if (catchType == null || vm.instanceOf(ex, catchType)) {
                    // the first found exception handler is our exception handler
                    return handler;
                }
            }
        }
        return null;
    }

    @ExplodeLoop
    private void initArguments(final VirtualFrame frame, final FrameSlot[] locals) {
        boolean hasReceiver = !Modifier.isStatic(method.getModifiers());
        int argCount = method.getSignature().getParameterCount(!method.isStatic());

        CompilerAsserts.partialEvaluationConstant(argCount);
        CompilerAsserts.partialEvaluationConstant(locals.length);

        Object[] arguments = frame.getArguments();
        int n = 0;
        if (hasReceiver) {
            frame.setObject(locals[n], arguments[0]);
            n += JavaKind.Object.getSlotCount();
        }
        for (int i = hasReceiver ? 1 : 0; i < argCount; ++i) {
            JavaKind expectedkind = method.getSignature().getParameterKind(i - (hasReceiver ? 1 : 0));
            switch (expectedkind) {
                case Boolean:
                    frame.setInt(locals[n], ((boolean) frame.getArguments()[i]) ? 1 : 0);
                    break;
                case Byte:
                    frame.setInt(locals[n], (int) ((byte) frame.getArguments()[i]));
                    break;
                case Short:
                    frame.setInt(locals[n], (int) ((short) frame.getArguments()[i]));
                    break;
                case Char:
                    frame.setInt(locals[n], (int) ((char) frame.getArguments()[i]));
                    break;
                case Int:
                    frame.setInt(locals[n], (int) frame.getArguments()[i]);
                    break;
                case Float:
                    frame.setFloat(locals[n], (float) frame.getArguments()[i]);
                    break;
                case Long:
                    frame.setLong(locals[n], (long) frame.getArguments()[i]);
                    break;
                case Double:
                    frame.setDouble(locals[n], (double) frame.getArguments()[i]);
                    break;
                case Object:
                    frame.setObject(locals[n], frame.getArguments()[i]);
                    break;
                case Void:
                case Illegal:
                    EspressoError.shouldNotReachHere();
            }
            n += expectedkind.getSlotCount();
        }
    }

    private void invokeInterface(OperandStack stack, MethodInfo method) {
        resolveAndInvoke(stack, method);
        // invoke(stack, method, null);
    }

    private void invokeSpecial(OperandStack stack, MethodInfo method) {
        if (!Modifier.isStatic(method.getModifiers())) {
            // TODO(peterssen): Intercept/hook methods on primitive arrays e.g. int[].clone().
            StaticObject receiver = (StaticObject) nullCheck(stack.peekReceiver(method));
            invoke(stack, method, receiver);
        } else {
            invoke(stack, method, null);
        }
    }

    private void invokeStatic(OperandStack stack, MethodInfo method) {
        method.getDeclaringClass().initialize();
        invoke(stack, method, null);
    }

    private void invokeVirtual(OperandStack stack, MethodInfo method) {
        if (Modifier.isFinal(method.getModifiers())) {
            // TODO(peterssen): Intercept/hook methods on primitive arrays e.g. int[].clone().
            StaticObject receiver = (StaticObject) nullCheck(stack.peekReceiver(method));
            invoke(stack, method, receiver);
        } else {
            resolveAndInvoke(stack, method);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private MethodInfo resolveMethod(int opcode, char cpi) {
        ConstantPool pool = getConstantPool();
        MethodInfo methodInfo = pool.methodAt(cpi).resolve(pool, cpi);
        return methodInfo;
    }

    @CompilerDirectives.TruffleBoundary
    private MethodInfo resolveInterfaceMethod(int opcode, char cpi) {
        assert opcode == INVOKEINTERFACE;
        ConstantPool pool = getConstantPool();
        MethodInfo methodInfo = pool.interfaceMethodAt(cpi).resolve(pool, cpi);
        return methodInfo;
    }

    private void invoke(OperandStack stack, MethodInfo method, StaticObject receiver) {
        CallTarget redirectedMethod = vm.getIntrinsic(method);
        if (redirectedMethod != null) {
            invokeRedirectedMethodViaVM(stack, method, redirectedMethod);
        } else {
            Object[] arguments = stack.popArguments(method);
            CallTarget target = method.getCallTarget();
            assert receiver == null || arguments[0] == receiver;
            JavaKind resultKind = method.getSignature().resultKind();
            Object result = target.call(arguments);
            stack.pushKind(result, resultKind);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private void resolveAndInvoke(OperandStack stack, MethodInfo method) {
        // TODO(peterssen): Ignore return type on method signature.
        // TODO(peterssen): Intercept/hook methods on primitive arrays e.g. int[].clone().
        StaticObject receiver = (StaticObject) nullCheck(stack.peekReceiver(method));
        // Resolve
        MethodInfo target = receiver.getKlass().findConcreteMethod(method.getName(), method.getSignature());
        invoke(stack, target, receiver);
    }

    @CompilerDirectives.TruffleBoundary
    private void invokeRedirectedMethodViaVM(OperandStack stack, MethodInfo originalMethod, CallTarget intrinsic) {
        Object[] originalCalleeParameters = stack.popArguments(originalMethod);
        Object returnValue = intrinsic.call(originalCalleeParameters);
        stack.pushKindIntrinsic(returnValue, originalMethod.getSignature().resultKind());
    }

    @CompilerDirectives.TruffleBoundary
    private StaticObject allocateArray(Klass componentType, int length) {
        // assert !componentType.isPrimitive();
        return vm.newArray(componentType, length);
    }

    @CompilerDirectives.TruffleBoundary
    private StaticObject allocateMultiArray(OperandStack stack, Klass klass, int allocatedDimensions) {
        assert klass.isArray();
        int[] dimensions = new int[allocatedDimensions];
        for (int i = allocatedDimensions - 1; i >= 0; i--) {
            dimensions[i] = stack.popInt();
        }
        return vm.newMultiArray(klass, dimensions);
    }

    @CompilerDirectives.TruffleBoundary
    private void pushPoolConstant(OperandStack stack, char cpi) {
        ConstantPool pool = getConstantPool();
        PoolConstant constant = pool.at(cpi);
        if (constant instanceof IntegerConstant) {
            stack.pushInt(((IntegerConstant) constant).value());
        } else if (constant instanceof LongConstant) {
            stack.pushLong(((LongConstant) constant).value());
        } else if (constant instanceof DoubleConstant) {
            stack.pushDouble(((DoubleConstant) constant).value());
        } else if (constant instanceof FloatConstant) {
            stack.pushFloat(((FloatConstant) constant).value());
        } else if (constant instanceof StringConstant) {
            // TODO(peterssen): Must be interned once, on creation.
            stack.pushObject(((StringConstant) constant).intern(pool));
        } else if (constant instanceof ClassConstant) {
            Klass klass = ((ClassConstant) constant).resolve(getConstantPool(), cpi);
            stack.pushObject(klass.mirror());
        }
    }

    private ConstantPool getConstantPool() {
        return this.method.getConstantPool();
    }

    @CompilerDirectives.TruffleBoundary
    private boolean instanceOf(Object instance, Klass typeToCheck) {
        return vm.instanceOf(instance, typeToCheck);
    }

    private void iinc(FrameSlot[] locals, VirtualFrame frame, BytecodeStream bs, int curBCI) throws FrameSlotTypeException {
        int index = bs.readLocalIndex(curBCI);
        frame.setInt(locals[index], frame.getInt(locals[index]) + bs.readIncrement(curBCI));
    }

    private static Object exitMethodAndReturn(Object result) {
        // do something
        return result;
    }

    private void enterMethod() {
        if (method.isSynchronized()) {
            vm.monitorEnter(method.getDeclaringClass());
        }
    }

    private void exitMethod() {
        if (method.isSynchronized()) {
            vm.monitorExit(method.getDeclaringClass());
        }
    }

    private static Object exitMethodAndReturn() {
        return exitMethodAndReturn(StaticObject.VOID);
    }

    private static int divInt(int divisor, int dividend) {
        return dividend / divisor;
    }

    private static long divLong(long divisor, long dividend) {
        return dividend / divisor;
    }

    private static float divFloat(float divisor, float dividend) {
        return dividend / divisor;
    }

    private static double divDouble(double divisor, double dividend) {
        return dividend / divisor;
    }

    private static int remInt(int divisor, int dividend) {
        return dividend % divisor;
    }

    private static long remLong(long divisor, long dividend) {
        return dividend % divisor;
    }

    private static float remFloat(float divisor, float dividend) {
        return dividend % divisor;
    }

    private static double remDouble(double divisor, double dividend) {
        return dividend % divisor;
    }

    private static int shiftLeftInt(int bits, int value) {
        return value << bits;
    }

    private static long shiftLeftLong(int bits, long value) {
        return value << bits;
    }

    private static int shiftRightSignedInt(int bits, int value) {
        return value >> bits;
    }

    private static long shiftRightSignedLong(int bits, long value) {
        return value >> bits;
    }

    private static int shiftRightUnsignedInt(int bits, int value) {
        return value >>> bits;
    }

    private static long shiftRightUnsignedLong(int bits, long value) {
        return value >>> bits;
    }

    private int lookupSwitch(OperandStack stack, BytecodeStream bs, int curBCI) {
        return lookupSearch(new BytecodeLookupSwitch(bs, bs.currentBCI(curBCI)), stack.popInt());
    }

    /**
     * Binary search implementation for the lookup switch.
     */
    private static int lookupSearch(BytecodeLookupSwitch switchHelper, int key) {
        int low = 0;
        int high = switchHelper.numberOfCases() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = switchHelper.keyAt(mid);

            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return switchHelper.bci() + switchHelper.offsetAt(mid); // key found.
            }
        }
        return switchHelper.defaultTarget(); // key not found.
    }

    @ExplodeLoop
    private static int tableSwitch(OperandStack stack, BytecodeStream bs, int curBCI) {
        BytecodeTableSwitch switchHelper = new BytecodeTableSwitch(bs, bs.currentBCI(curBCI));
        int low = switchHelper.lowKey();
        CompilerAsserts.partialEvaluationConstant(low);
        int high = switchHelper.highKey();
        CompilerAsserts.partialEvaluationConstant(high);
        assert low <= high;
        int index = stack.popInt();
        for (int i = low; i <= high; ++i) {
            if (i == index) {
                CompilerAsserts.partialEvaluationConstant(switchHelper.targetAt(i - low));
                return switchHelper.targetAt(i - low);
            }
        }
        CompilerAsserts.partialEvaluationConstant(switchHelper.defaultTarget());
        return switchHelper.defaultTarget();

//        if (index < low || index > high) {
//            return switchHelper.defaultTarget();
//        } else {
//            return switchHelper.targetAt(index - low);
//        }
    }

    private Object checkCast(Object instance, Klass typeToCheck) {
        return vm.checkCast(instance, typeToCheck);
    }

    private Object allocateInstance(Klass klass) {
        klass.initialize();
        return vm.newObject(klass);
    }

    // x compare y
    private static int compareLong(long y, long x) {
        return Long.compare(x, y);
    }

    private static int compareFloatGreater(float y, float x) {
        return (x < y ? -1 : ((x == y) ? 0 : 1));
    }

    private static int compareFloatLess(float y, float x) {
        return (x > y ? 1 : ((x == y) ? 0 : -1));
    }

    private static int compareDoubleGreater(double y, double x) {
        return (x < y ? -1 : ((x == y) ? 0 : 1));
    }

    private static int compareDoubleLess(double y, double x) {
        return (x > y ? 1 : ((x == y) ? 0 : -1));
    }

    private Object nullCheck(Object value) {
        assert value != null;
        if (value == StaticObject.NULL) {
            CompilerDirectives.transferToInterpreter();
            // TODO(peterssen): Profile whether null was hit or not.
            Meta meta = method.getDeclaringClass().getContext().getMeta();
            throw meta.throwEx(NullPointerException.class);
        }
        return value;
    }

    private static int checkNonZero(int value) {
        if (value != 0) {
            return value;
        }
        throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArithmeticException.class, "/ by zero");
    }

    private static long checkNonZero(long value) {
        if (value != 0L) {
            return value;
        }
        throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArithmeticException.class, "/ by zero");
    }

    // endregion

    @CompilerDirectives.TruffleBoundary
    private Klass resolveType(int opcode, char cpi) {
        // TODO(peterssen): Check opcode.
        ConstantPool pool = getConstantPool();
        return pool.classAt(cpi).resolve(pool, cpi);
    }

    @CompilerDirectives.TruffleBoundary
    private FieldInfo resolveField(int opcode, char cpi) {
        // TODO(peterssen): Check opcode.
        ConstantPool pool = getConstantPool();
        return pool.fieldAt(cpi).resolve(pool, cpi);
    }

    @CompilerDirectives.TruffleBoundary
    private void putField(OperandStack stack, FieldInfo field, boolean isStatic) {
        assert Modifier.isStatic(field.getFlags()) == isStatic;

        // Arrays do not have fields, the receiver can only be a StaticObject.
        if (isStatic) {
            field.getDeclaringClass().initialize();
        }

        Supplier<StaticObject> receiver = () -> isStatic
                        ? (field.getDeclaringClass()).getStatics() /* static storage */
                        : (StaticObject) nullCheck(stack.popObject());

        switch (field.getKind()) {
            case Boolean:
                vm.setFieldBoolean((stack.popInt() == 1 ? true : false), receiver.get(), field);
                break;
            case Byte:
                vm.setFieldByte((byte) stack.popInt(), receiver.get(), field);
                break;
            case Char:
                vm.setFieldChar((char) stack.popInt(), receiver.get(), field);
                break;
            case Short:
                vm.setFieldShort((short) stack.popInt(), receiver.get(), field);
                break;
            case Int:
                vm.setFieldInt(stack.popInt(), receiver.get(), field);
                break;
            case Double:
                vm.setFieldDouble(stack.popDouble(), receiver.get(), field);
                break;
            case Float:
                vm.setFieldFloat(stack.popFloat(), receiver.get(), field);
                break;
            case Long:
                vm.setFieldLong(stack.popLong(), receiver.get(), field);
                break;
            case Object:
                // Arrays do not have fields, the receiver can only be a StaticObject.
                vm.setFieldObject(stack.popObject(), receiver.get(), field);
                break;
            default:
                assert false : "unexpected kind";
        }
    }

    @CompilerDirectives.TruffleBoundary
    private void getField(OperandStack stack, FieldInfo field, boolean isStatic) {
        if (isStatic) {
            field.getDeclaringClass().initialize();
        }
        assert Modifier.isStatic(field.getFlags()) == isStatic;
        // Arrays do not have fields, the receiver can only be a StaticObject.
        StaticObject receiver = isStatic
                        ? field.getDeclaringClass().getStatics() /* static storage */
                        : (StaticObject) nullCheck(stack.popObject());
        switch (field.getKind()) {
            case Boolean:
                stack.pushInt(vm.getFieldBoolean(receiver, field) ? 1 : 0);
                break;
            case Byte:
                stack.pushInt(vm.getFieldByte(receiver, field));
                break;
            case Char:
                stack.pushInt(vm.getFieldChar(receiver, field));
                break;
            case Short:
                stack.pushInt(vm.getFieldShort(receiver, field));
                break;
            case Int:
                stack.pushInt(vm.getFieldInt(receiver, field));
                break;
            case Double:
                stack.pushDouble(vm.getFieldDouble(receiver, field));
                break;
            case Float:
                stack.pushFloat(vm.getFieldFloat(receiver, field));
                break;
            case Long:
                stack.pushLong(vm.getFieldLong(receiver, field));
                break;
            case Object:
                stack.pushObject(vm.getFieldObject(receiver, field));
                break;
            default:
                assert false : "unexpected kind";
        }
    }

    @Override
    public String toString() {
        return method.getDeclaringClass().getName() + "." + method.getName() + method.getSignature().toString();
    }
}
