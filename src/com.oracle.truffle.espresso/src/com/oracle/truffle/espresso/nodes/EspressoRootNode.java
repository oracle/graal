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

import java.lang.reflect.Modifier;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.bytecode.DualStack;
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
import com.oracle.truffle.espresso.runtime.ReturnAddress;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.types.SignatureDescriptor;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.object.DebugCounter;

public class EspressoRootNode extends RootNode implements LinkedNode {
    private final MethodInfo method;
    private final InterpreterToVM vm;

    private static final DebugCounter bytecodesExecuted = DebugCounter.create("Bytecodes executed");
    private static final DebugCounter methodInvokes = DebugCounter.create("Method invokes");
    private static final DebugCounter newInstances = DebugCounter.create("New instances");
    private static final DebugCounter fieldWrites = DebugCounter.create("Field writes");
    private static final DebugCounter fieldReads = DebugCounter.create("Field reads");

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final FrameSlot[] locals;

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
        this.method = method;
        this.vm = vm;
        this.bs = new BytecodeStream(method.getCode());
        this.locals = getFrameDescriptor().getSlots().toArray(new FrameSlot[0]);
    }

    public MethodInfo getMethod() {
        return method;
    }

    @ExplodeLoop
    private static FrameDescriptor initFrameDescriptor(MethodInfo method) {
        FrameDescriptor descriptor = new FrameDescriptor();
        int maxLocals = method.getMaxLocals();
        for (int i = 0; i < maxLocals; ++i) {
            descriptor.addFrameSlot(i); // illegal by default
        }

        boolean hasReceiver = !method.isStatic();
        int argCount = method.getSignature().getParameterCount(false);
        FrameSlot[] locals = descriptor.getSlots().toArray(new FrameSlot[0]);

        int n = 0;
        if (hasReceiver) {
            descriptor.setFrameSlotKind(locals[0], FrameSlotKind.Object);
            n += JavaKind.Object.getSlotCount();
        }
        for (int i = 0; i < argCount; ++i) {
            JavaKind expectedkind = method.getSignature().getParameterKind(i);
            switch (expectedkind) {
                case Boolean:
                case Byte:
                case Short:
                case Char:
                case Int:
                    descriptor.setFrameSlotKind(locals[n], FrameSlotKind.Int);
                    break;
                case Float:
                    descriptor.setFrameSlotKind(locals[n], FrameSlotKind.Float);
                    break;
                case Long:
                    descriptor.setFrameSlotKind(locals[n], FrameSlotKind.Long);
                    break;
                case Double:
                    descriptor.setFrameSlotKind(locals[n], FrameSlotKind.Double);
                    break;
                case Object:
                    descriptor.setFrameSlotKind(locals[n], FrameSlotKind.Object);
                    break;
                case Void:
                case Illegal:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere();
            }
            n += expectedkind.getSlotCount();
        }

        return descriptor;
    }

    @Override
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    public Object execute(VirtualFrame frame) {
        int curBCI = 0;
        final OperandStack stack = new DualStack(method.getMaxStackSize());
        initArguments(frame);

        loop: while (true) {
            try {
                bytecodesExecuted.inc();

                CompilerAsserts.partialEvaluationConstant(bs.currentBC(curBCI));
                CompilerAsserts.partialEvaluationConstant(curBCI);

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
                        stack.pushInt(getIntLocal(frame, bs.readLocalIndex(curBCI)));
                        break;
                    case LLOAD:
                        stack.pushLong(getLongLocal(frame, bs.readLocalIndex(curBCI)));
                        break;
                    case FLOAD:
                        stack.pushFloat(getFloatLocal(frame, bs.readLocalIndex(curBCI)));
                        break;
                    case DLOAD:
                        stack.pushDouble(getDoubleLocal(frame, bs.readLocalIndex(curBCI)));
                        break;
                    case ALOAD:
                        stack.pushObject(getObjectLocal(frame, bs.readLocalIndex(curBCI)));
                        break;
                    case ILOAD_0:
                        stack.pushInt(getIntLocal(frame, 0));
                        break;
                    case ILOAD_1:
                        stack.pushInt(getIntLocal(frame, 1));
                        break;
                    case ILOAD_2:
                        stack.pushInt(getIntLocal(frame, 2));
                        break;
                    case ILOAD_3:
                        stack.pushInt(getIntLocal(frame, 3));
                        break;
                    case LLOAD_0:
                        stack.pushLong(getLongLocal(frame, 0));
                        break;
                    case LLOAD_1:
                        stack.pushLong(getLongLocal(frame, 1));
                        break;
                    case LLOAD_2:
                        stack.pushLong(getLongLocal(frame, 2));
                        break;
                    case LLOAD_3:
                        stack.pushLong(getLongLocal(frame, 3));
                        break;
                    case FLOAD_0:
                        stack.pushFloat(getFloatLocal(frame, 0));
                        break;
                    case FLOAD_1:
                        stack.pushFloat(getFloatLocal(frame, 1));
                        break;
                    case FLOAD_2:
                        stack.pushFloat(getFloatLocal(frame, 2));
                        break;
                    case FLOAD_3:
                        stack.pushFloat(getFloatLocal(frame, 3));
                        break;
                    case DLOAD_0:
                        stack.pushDouble(getDoubleLocal(frame, 0));
                        break;
                    case DLOAD_1:
                        stack.pushDouble(getDoubleLocal(frame, 1));
                        break;
                    case DLOAD_2:
                        stack.pushDouble(getDoubleLocal(frame, 2));
                        break;
                    case DLOAD_3:
                        stack.pushDouble(getDoubleLocal(frame, 3));
                        break;
                    case ALOAD_0:
                        stack.pushObject(getObjectLocal(frame, 0));
                        break;
                    case ALOAD_1:
                        stack.pushObject(getObjectLocal(frame, 1));
                        break;
                    case ALOAD_2:
                        stack.pushObject(getObjectLocal(frame, 2));
                        break;
                    case ALOAD_3:
                        stack.pushObject(getObjectLocal(frame, 3));
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
                        setIntLocal(frame, bs.readLocalIndex(curBCI), stack.popInt());
                        break;
                    case LSTORE:
                        setLongLocal(frame, bs.readLocalIndex(curBCI), stack.popLong());
                        break;
                    case FSTORE:
                        setFloatLocal(frame, bs.readLocalIndex(curBCI), stack.popFloat());
                        break;
                    case DSTORE:
                        setDoubleLocal(frame, bs.readLocalIndex(curBCI), stack.popDouble());
                        break;
                    case ASTORE:
                        setObjectLocal(frame, bs.readLocalIndex(curBCI), stack.popObject());
                        break;
                    case ISTORE_0:
                        setIntLocal(frame, 0, stack.popInt());
                        break;
                    case ISTORE_1:
                        setIntLocal(frame, 1, stack.popInt());
                        break;
                    case ISTORE_2:
                        setIntLocal(frame, 2, stack.popInt());
                        break;
                    case ISTORE_3:
                        setIntLocal(frame, 3, stack.popInt());
                        break;
                    case LSTORE_0:
                        setLongLocal(frame, 0, stack.popLong());
                        break;
                    case LSTORE_1:
                        setLongLocal(frame, 1, stack.popLong());
                        break;
                    case LSTORE_2:
                        setLongLocal(frame, 2, stack.popLong());
                        break;
                    case LSTORE_3:
                        setLongLocal(frame, 3, stack.popLong());
                        break;
                    case FSTORE_0:
                        setFloatLocal(frame, 0, stack.popFloat());
                        break;
                    case FSTORE_1:
                        setFloatLocal(frame, 1, stack.popFloat());
                        break;
                    case FSTORE_2:
                        setFloatLocal(frame, 2, stack.popFloat());
                        break;
                    case FSTORE_3:
                        setFloatLocal(frame, 3, stack.popFloat());
                        break;
                    case DSTORE_0:
                        setDoubleLocal(frame, 0, stack.popDouble());
                        break;
                    case DSTORE_1:
                        setDoubleLocal(frame, 1, stack.popDouble());
                        break;
                    case DSTORE_2:
                        setDoubleLocal(frame, 2, stack.popDouble());
                        break;
                    case DSTORE_3:
                        setDoubleLocal(frame, 3, stack.popDouble());
                        break;
                    case ASTORE_0:
                        setObjectLocal(frame, 0, stack.popObject());
                        break;
                    case ASTORE_1:
                        setObjectLocal(frame, 1, stack.popObject());
                        break;
                    case ASTORE_2:
                        setObjectLocal(frame, 2, stack.popObject());
                        break;
                    case ASTORE_3:
                        setObjectLocal(frame, 3, stack.popObject());
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
                        vm.setArrayObject(stack.popObject(), stack.popInt(), (StaticObjectArray) nullCheck(stack.popObject()));
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
                        setIntLocal(frame, bs.readLocalIndex(curBCI), getIntLocal(frame, bs.readLocalIndex(curBCI)) + bs.readIncrement(curBCI));
                        break;
                    case I2L:
                        stack.pushLong(stack.popInt());
                        break;
                    case I2F:
                        stack.pushFloat(stack.popInt());
                        break;
                    case I2D:
                        stack.pushDouble(stack.popInt());
                        break;
                    case L2I:
                        stack.pushInt((int) stack.popLong());
                        break;
                    case L2F:
                        stack.pushFloat(stack.popLong());
                        break;
                    case L2D:
                        stack.pushDouble(stack.popLong());
                        break;
                    case F2I:
                        stack.pushInt((int) stack.popFloat());
                        break;
                    case F2L:
                        stack.pushLong((long) stack.popFloat());
                        break;
                    case F2D:
                        stack.pushDouble(stack.popFloat());
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
                        stack.pushReturnAddress(bs.nextBCI(curBCI));
                        curBCI = bs.readBranchDest(curBCI);
                        continue loop;
                    case RET:
                        curBCI = getReturnAddressLocal(frame, bs.readLocalIndex(curBCI));
                        continue loop;
                    case TABLESWITCH: {
                        // curBCI = tableSwitch(bs, curBCI, stack.popInt());
                        int index = stack.popInt();
                        BytecodeTableSwitch switchHelper = bs.getBytecodeTableSwitch();
                        int low = switchHelper.lowKey(curBCI);
                        CompilerAsserts.partialEvaluationConstant(low);
                        int high = switchHelper.highKey(curBCI);
                        CompilerAsserts.partialEvaluationConstant(high);
                        CompilerAsserts.partialEvaluationConstant(switchHelper);
                        assert low <= high;

                        // Interpreter uses direct lookup.
                        if (CompilerDirectives.inInterpreter()) {
                            if (low <= index && index <= high) {
                                curBCI = switchHelper.targetAt(curBCI, index - low);
                            } else {
                                curBCI = switchHelper.defaultTarget(curBCI);
                            }
                            continue loop;
                        }

                        for (int i = low; i <= high; ++i) {
                            if (i == index) {
                                CompilerAsserts.partialEvaluationConstant(i);
                                CompilerAsserts.partialEvaluationConstant(i - low);
                                CompilerAsserts.partialEvaluationConstant(switchHelper.targetAt(curBCI, i - low));
                                curBCI = switchHelper.targetAt(curBCI, i - low);
                                continue loop;
                            }
                        }

                        CompilerAsserts.partialEvaluationConstant(switchHelper.defaultTarget(curBCI));
                        curBCI = switchHelper.defaultTarget(curBCI);
                        continue loop;
                    }
                    case LOOKUPSWITCH: {
                        // curBCI = lookupSwitch(bs, curBCI, stack.popInt());
                        int key = stack.popInt();
                        BytecodeLookupSwitch switchHelper = bs.getBytecodeLookupSwitch();
                        int low = 0;
                        int high = switchHelper.numberOfCases(curBCI) - 1;
                        CompilerAsserts.partialEvaluationConstant(switchHelper);
                        while (low <= high) {
                            int mid = (low + high) >>> 1;
                            int midVal = switchHelper.keyAt(curBCI, mid);

                            if (midVal < key) {
                                low = mid + 1;
                            } else if (midVal > key) {
                                high = mid - 1;
                            } else {
                                curBCI = curBCI + switchHelper.offsetAt(curBCI, mid); // key found.
                                continue loop;
                            }
                        }
                        curBCI = switchHelper.defaultTarget(curBCI); // key not found.
                        continue loop;
                    }
                    case IRETURN:
                        return exitMethodAndReturn(stack.popInt());
                    case LRETURN:
                        return exitMethodAndReturnObject(stack.popLong());
                    case FRETURN:
                        return exitMethodAndReturnObject(stack.popFloat());
                    case DRETURN:
                        return exitMethodAndReturnObject(stack.popDouble());
                    case ARETURN:
                        return exitMethodAndReturnObject(stack.popObject());
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
                        newInstances.inc();
                        stack.pushObject(allocateInstance(resolveType(bs.currentBC(curBCI), bs.readCPI(curBCI))));
                        break;
                    case NEWARRAY:
                        newInstances.inc();
                        stack.pushObject(InterpreterToVM.allocateNativeArray(bs.readByte(curBCI), stack.popInt()));
                        break;
                    case ANEWARRAY:
                        newInstances.inc();
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
                        newInstances.inc();
                        stack.pushObject(
                                        allocateMultiArray(stack,
                                                        resolveType(bs.currentBC(curBCI), bs.readCPI(curBCI)),
                                                        bs.readUByte(curBCI + 3)));
                        break;
                    case IFNULL:
                        if (StaticObject.isNull(stack.popObject())) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case IFNONNULL:
                        if (StaticObject.notNull(stack.popObject())) {
                            curBCI = bs.readBranchDest(curBCI);
                            continue loop;
                        }
                        break;
                    case BREAKPOINT:
                        throw new UnsupportedOperationException("breakpoints not supported.");
                    case INVOKEDYNAMIC:
                        throw new UnsupportedOperationException("invokedynamic not supported.");
                }
            } catch (EspressoException e) {
                CompilerDirectives.transferToInterpreter();
                ExceptionHandler handler = resolveExceptionHandlers(curBCI, e.getException());
                if (handler != null) {
                    stack.clear();
                    stack.pushObject(e.getException());
                    curBCI = handler.getHandlerBCI();
                    continue loop; // skip bs.next()
                } else {
                    throw e;
                }
            } catch (VirtualMachineError e) {
                // TODO(peterssen): Host should not throw invalid VME (not in the boot classpath).
                CompilerDirectives.transferToInterpreter();
                Meta meta = EspressoLanguage.getCurrentContext().getMeta();
                StaticObject ex = meta.initEx(e.getClass());
                ExceptionHandler handler = resolveExceptionHandlers(curBCI, ex);
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

    @ExplodeLoop
    private ExceptionHandler resolveExceptionHandlers(int bci, StaticObject ex) {
        CompilerAsserts.partialEvaluationConstant(bci);
        ExceptionHandler[] handlers = getMethod().getExceptionHandlers();
        for (ExceptionHandler handler : handlers) {
            if (bci >= handler.getStartBCI() && bci < handler.getEndBCI()) {
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

    void setIntLocal(VirtualFrame frame, int n, int value) {
        FrameDescriptor descriptor = getFrameDescriptor();
        if (descriptor.getFrameSlotKind(locals[n]) != FrameSlotKind.Int) {
            descriptor.setFrameSlotKind(locals[n], FrameSlotKind.Int);
        }
        frame.setInt(locals[n], value);
    }

    void setFloatLocal(VirtualFrame frame, int n, float value) {
        FrameDescriptor descriptor = getFrameDescriptor();
        if (descriptor.getFrameSlotKind(locals[n]) != FrameSlotKind.Float) {
            descriptor.setFrameSlotKind(locals[n], FrameSlotKind.Float);
        }
        frame.setFloat(locals[n], value);
    }

    void setDoubleLocal(VirtualFrame frame, int n, double value) {
        FrameDescriptor descriptor = getFrameDescriptor();
        if (descriptor.getFrameSlotKind(locals[n]) != FrameSlotKind.Double) {
            descriptor.setFrameSlotKind(locals[n], FrameSlotKind.Double);
        }
        frame.setDouble(locals[n], value);
    }

    void setLongLocal(VirtualFrame frame, int n, long value) {
        FrameDescriptor descriptor = getFrameDescriptor();
        if (descriptor.getFrameSlotKind(locals[n]) != FrameSlotKind.Long) {
            descriptor.setFrameSlotKind(locals[n], FrameSlotKind.Long);
        }
        frame.setLong(locals[n], value);
    }

    void setObjectLocal(VirtualFrame frame, int n, Object value) {
        FrameDescriptor descriptor = getFrameDescriptor();
        if (descriptor.getFrameSlotKind(locals[n]) != FrameSlotKind.Object) {
            descriptor.setFrameSlotKind(locals[n], FrameSlotKind.Object);
        }
        frame.setObject(locals[n], value);
    }

    int getIntLocal(VirtualFrame frame, int n) {
        return FrameUtil.getIntSafe(frame, locals[n]);
    }

    float getFloatLocal(VirtualFrame frame, int n) {
        return FrameUtil.getFloatSafe(frame, locals[n]);
    }

    double getDoubleLocal(VirtualFrame frame, int n) {
        return FrameUtil.getDoubleSafe(frame, locals[n]);
    }

    long getLongLocal(VirtualFrame frame, int n) {
        return FrameUtil.getLongSafe(frame, locals[n]);
    }

    Object getObjectLocal(VirtualFrame frame, int n) {
        Object result = FrameUtil.getObjectSafe(frame, locals[n]);
        assert !(result instanceof ReturnAddress) : "use getReturnAddressLocal";
        return result;
    }

    int getReturnAddressLocal(VirtualFrame frame, int n) {
        return ((ReturnAddress) FrameUtil.getObjectSafe(frame, locals[n])).getBci();
    }

    @ExplodeLoop
    private static Object[] copyOfRange(Object[] src, int from, int toExclusive) {
        int len = toExclusive - from;
        Object[] dst = new Object[len];
        for (int i = 0; i < len; ++i) {
            dst[i] = src[i + from];
        }
        return dst;
    }

    @ExplodeLoop
    private void initArguments(final VirtualFrame frame) {
        boolean hasReceiver = !getMethod().isStatic();
        int argCount = method.getSignature().getParameterCount(false);

        CompilerAsserts.partialEvaluationConstant(argCount);
        CompilerAsserts.partialEvaluationConstant(locals.length);

        Object[] frameArguments = frame.getArguments();
        Object[] arguments;
        if (hasReceiver) {
            arguments = copyOfRange(frameArguments, 1, argCount + 1);
        } else {
            arguments = frameArguments;
        }

        assert arguments.length == argCount;

        int n = 0;
        if (hasReceiver) {
            setObjectLocal(frame, n, frameArguments[0]);
            n += JavaKind.Object.getSlotCount();
        }
        for (int i = 0; i < argCount; ++i) {
            JavaKind expectedkind = method.getSignature().getParameterKind(i);
            switch (expectedkind) {
                case Boolean:
                    setIntLocal(frame, n, ((boolean) arguments[i]) ? 1 : 0);
                    break;
                case Byte:
                    setIntLocal(frame, n, ((byte) arguments[i]));
                    break;
                case Short:
                    setIntLocal(frame, n, ((short) arguments[i]));
                    break;
                case Char:
                    setIntLocal(frame, n, ((char) arguments[i]));
                    break;
                case Int:
                    setIntLocal(frame, n, (int) arguments[i]);
                    break;
                case Float:
                    setFloatLocal(frame, n, (float) arguments[i]);
                    break;
                case Long:
                    setLongLocal(frame, n, (long) arguments[i]);
                    break;
                case Double:
                    setDoubleLocal(frame, n, (double) arguments[i]);
                    break;
                case Object:
                    setObjectLocal(frame, n, arguments[i]);
                    break;
                case Void:
                case Illegal:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere();
            }
            n += expectedkind.getSlotCount();
        }
    }

    private void invokeInterface(OperandStack stack, MethodInfo target) {
        CompilerAsserts.partialEvaluationConstant(target);
        resolveAndInvoke(stack, target);
    }

    private void invokeSpecial(OperandStack stack, MethodInfo target) {
        if (!Modifier.isStatic(target.getModifiers())) {
            // TODO(peterssen): Intercept/hook methods on primitive arrays e.g. int[].clone().
            Object receiver = nullCheck(stack.peekReceiver(target));
            invoke(stack, target, receiver, !target.isStatic(), target.getSignature());
        } else {
            invoke(stack, target, null, !target.isStatic(), target.getSignature());
        }
    }

    private static void invokeStatic(OperandStack stack, MethodInfo target) {
        target.getDeclaringClass().initialize();
        invoke(stack, target, null, !target.isStatic(), target.getSignature());
    }

    private void invokeVirtual(OperandStack stack, MethodInfo resolutionSeed) {
        if (resolutionSeed.isFinal()) {
            // TODO(peterssen): Intercept/hook methods on primitive arrays e.g. int[].clone().
            // Receiver can be a primitive array (not a StaticObject).
            Object receiver = nullCheck(stack.peekReceiver(resolutionSeed));
            invoke(stack, resolutionSeed, receiver, !resolutionSeed.isStatic(), resolutionSeed.getSignature());
        } else {
            resolveAndInvoke(stack, resolutionSeed);
        }
    }

    private MethodInfo resolveMethod(int opcode, char cpi) {
        CompilerAsserts.partialEvaluationConstant(cpi);
        CompilerAsserts.partialEvaluationConstant(opcode);
        ConstantPool pool = getConstantPool();
        MethodInfo methodInfo = pool.methodAt(cpi).resolve(pool, cpi);
        return methodInfo;
    }

    private MethodInfo resolveInterfaceMethod(int opcode, char cpi) {
        CompilerAsserts.partialEvaluationConstant(cpi);
        CompilerAsserts.partialEvaluationConstant(opcode);
        ConstantPool pool = getConstantPool();
        MethodInfo methodInfo = pool.interfaceMethodAt(cpi).resolve(pool, cpi);
        CompilerAsserts.partialEvaluationConstant(methodInfo);
        return methodInfo;
    }

    private static void invoke(OperandStack stack, MethodInfo targetMethod, Object receiver, boolean hasReceiver, SignatureDescriptor signature) {
        methodInvokes.inc();
        CallTarget callTarget = targetMethod.getCallTarget();
        // In bytecode boolean, byte, char and short are just plain ints.
        // When a method is intrinsified it will obey Java types, so we need to convert the
        // (boolean, byte, char, short) result back to int.
        // (pop)Arguments have proper Java types.
        Object[] arguments = popArguments(stack, hasReceiver, signature);
        assert receiver == null || arguments[0] == receiver;
        JavaKind resultKind = signature.getReturnTypeDescriptor().toKind();
        Object result = callTarget.call(arguments);
        pushKind(stack, result, resultKind);
    }

    private void resolveAndInvoke(OperandStack stack, MethodInfo originalMethod) {
        CompilerAsserts.partialEvaluationConstant(originalMethod);
        // TODO(peterssen): Ignore return type on method signature.
        // TODO(peterssen): Intercept/hook methods on primitive arrays e.g. int[].clone().
        // We could call a virtual method on a primitive array. e.g. ((Object)new byte[1]).clone();
        Object receiver = nullCheck(stack.peekReceiver(originalMethod));
        // Resolve
        MethodInfo targetMethod = methodLookup(originalMethod, receiver);
        invoke(stack, targetMethod, receiver, !originalMethod.isStatic(), originalMethod.getSignature());
    }

    @CompilerDirectives.TruffleBoundary
    private static MethodInfo methodLookup(MethodInfo originalMethod, Object receiver) {
        StaticObjectClass clazz = (StaticObjectClass) EspressoLanguage.getCurrentContext().getJNI().GetObjectClass(receiver);
        return clazz.getMirror().findConcreteMethod(originalMethod.getName(), originalMethod.getSignature());
    }

    @CompilerDirectives.TruffleBoundary
    private StaticObject allocateArray(Klass componentType, int length) {
        assert !componentType.isPrimitive();
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

    private boolean instanceOf(Object instance, Klass typeToCheck) {
        return vm.instanceOf(instance, typeToCheck);
    }

    private Object exitMethodAndReturn(int result) {
        switch (method.getReturnType().getJavaKind()) {
            case Boolean:
                return result != 0;
            case Byte:
                return (byte) result;
            case Short:
                return (short) result;
            case Char:
                return (char) result;
            case Int:
                return result;
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    private static Object exitMethodAndReturnObject(Object result) {
        return result;
    }

    private static Object exitMethodAndReturn() {
        return exitMethodAndReturnObject(StaticObject.VOID);
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

    /**
     * Binary search implementation for the lookup switch.
     */
    @SuppressWarnings("unused")
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE)
    private static int lookupSearch(BytecodeStream bs, int curBCI, int key) {
        BytecodeLookupSwitch switchHelper = bs.getBytecodeLookupSwitch();
        int low = 0;
        int high = switchHelper.numberOfCases(curBCI) - 1;
        CompilerAsserts.partialEvaluationConstant(switchHelper);
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = switchHelper.keyAt(curBCI, mid);

            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return curBCI + switchHelper.offsetAt(curBCI, mid); // key found.
            }
        }
        return switchHelper.defaultTarget(curBCI); // key not found.
    }

    /**
     * The table switch lookup can be efficiently implemented using a constant lookup. It was
     * intentionally replaced by a linear search to help partial evaluation infer control flow
     * structure correctly.
     */
    @SuppressWarnings("unused")
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private static int tableSwitch(BytecodeStream bs, int curBCI, int index) {
        BytecodeTableSwitch switchHelper = bs.getBytecodeTableSwitch();
        int low = switchHelper.lowKey(curBCI);
        CompilerAsserts.partialEvaluationConstant(low);
        int high = switchHelper.highKey(curBCI);
        CompilerAsserts.partialEvaluationConstant(high);
        CompilerAsserts.partialEvaluationConstant(switchHelper);
        assert low <= high;

        // Interpreter uses direct lookup.
        if (CompilerDirectives.inInterpreter()) {
            if (low <= index && index <= high) {
                return switchHelper.targetAt(curBCI, index - low);
            } else {
                return switchHelper.defaultTarget(curBCI);
            }
        }

        for (int i = low; i <= high; ++i) {
            if (i == index) {
                CompilerAsserts.partialEvaluationConstant(i);
                CompilerAsserts.partialEvaluationConstant(i - low);
                CompilerAsserts.partialEvaluationConstant(switchHelper.targetAt(curBCI, i - low));
                return switchHelper.targetAt(curBCI, i - low);
            }
        }

        CompilerAsserts.partialEvaluationConstant(switchHelper.defaultTarget(curBCI));
        return switchHelper.defaultTarget(curBCI);
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
        if (StaticObject.isNull(value)) {
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

    private Klass resolveType(@SuppressWarnings("unused") int opcode, char cpi) {
        // TODO(peterssen): Check opcode.
        ConstantPool pool = getConstantPool();
        return pool.classAt(cpi).resolve(pool, cpi);
    }

    private FieldInfo resolveField(@SuppressWarnings("unused") int opcode, char cpi) {
        // TODO(peterssen): Check opcode.
        ConstantPool pool = getConstantPool();
        return pool.fieldAt(cpi).resolve(pool, cpi);
    }

    private void putField(OperandStack stack, FieldInfo field, boolean isStatic) {
        fieldWrites.inc();
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

    private void getField(OperandStack stack, FieldInfo field, boolean isStatic) {
        fieldReads.inc();
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

    @ExplodeLoop
    private static Object[] popArguments(OperandStack stack, boolean hasReceiver, SignatureDescriptor signature) {
        // TODO(peterssen): Check parameter count.
        int argCount = signature.getParameterCount(false);

        int extraParam = hasReceiver ? 1 : 0;
        Object[] arguments = new Object[argCount + extraParam];

        CompilerAsserts.partialEvaluationConstant(argCount);
        CompilerAsserts.partialEvaluationConstant(signature);
        CompilerAsserts.partialEvaluationConstant(hasReceiver);

        for (int i = argCount - 1; i >= 0; --i) {
            JavaKind expectedKind = signature.getParameterKind(i);
            switch (expectedKind) {
                case Boolean:
                    int b = stack.popInt();
                    assert b == 0 || b == 1;
                    arguments[i + extraParam] = (b != 0);
                    break;
                case Byte:
                    arguments[i + extraParam] = (byte) stack.popInt();
                    break;
                case Short:
                    arguments[i + extraParam] = (short) stack.popInt();
                    break;
                case Char:
                    arguments[i + extraParam] = (char) stack.popInt();
                    break;
                case Int:
                    arguments[i + extraParam] = stack.popInt();
                    break;
                case Float:
                    arguments[i + extraParam] = stack.popFloat();
                    break;
                case Long:
                    arguments[i + extraParam] = stack.popLong();
                    break;
                case Double:
                    arguments[i + extraParam] = stack.popDouble();
                    break;
                case Object:
                    arguments[i + extraParam] = stack.popObject();
                    break;
                case Void:
                case Illegal:
                    throw EspressoError.shouldNotReachHere();
            }
        }
        if (hasReceiver) {
            arguments[0] = stack.popObject();
        }
        return arguments;
    }

    // This follows bytecode types, everything < int is pushed as int.
    // The may not be spec compliant, but the spec is not soft
    private static void pushKind(OperandStack stack, Object returnValue, JavaKind kind) {
        switch (kind) {
            case Boolean:
                stack.pushInt(((boolean) returnValue) ? 1 : 0);
                break;
            case Byte:
                stack.pushInt((byte) returnValue);
                break;
            case Short:
                stack.pushInt((short) returnValue);
                break;
            case Char:
                stack.pushInt((char) returnValue);
                break;
            case Int:
                stack.pushInt((int) returnValue);
                break;
            case Float:
                stack.pushFloat((float) returnValue);
                break;
            case Long:
                stack.pushLong((long) returnValue);
                break;
            case Double:
                stack.pushDouble((double) returnValue);
                break;
            case Object:
                stack.pushObject(returnValue);
                break;
            case Void:
                // do not push
                break;
            case Illegal:
                throw EspressoError.shouldNotReachHere();
        }
    }

    @Override
    public String toString() {
        return method.getDeclaringClass().getName() + "." + method.getName() + method.getSignature().toString();
    }

    @Override
    public Meta.Method getOriginalMethod() {
        return Meta.meta(method);
    }
}
