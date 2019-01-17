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
import static com.oracle.truffle.espresso.bytecode.Bytecodes.QUICK_INVOKEINTERFACE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.QUICK_INVOKESPECIAL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.QUICK_INVOKESTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.QUICK_INVOKEVIRTUAL;
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.bytecode.FrameOperandStack;
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
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Bytecode interpreter loop.
 *
 * Calling convention use Java primitive types although internally the VM basic types (e.g. sub-word
 * types are coerced to int) are used with conversions at the boundaries.
 */
public final class EspressoRootNode extends RootNode implements FrameOperandStack, LinkedNode {

    @Children private InvokeNode[] nodes = new InvokeNode[0];

    private final MethodInfo method;
    private final InterpreterToVM vm;

// private static final DebugCounter bytecodesExecuted = DebugCounter.create("Bytecodes executed");
// private static final DebugCounter newInstances = DebugCounter.create("New instances");
// private static final DebugCounter fieldWrites = DebugCounter.create("Field writes");
// private static final DebugCounter fieldReads = DebugCounter.create("Field reads");

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final FrameSlot[] locals;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final FrameSlot[] stackSlots;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final FrameSlot[] tagSlots;
    private final FrameSlot tosSlot;

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
        super(language, initFrameDescriptor(method.getMaxLocals() + 2 * method.getMaxStackSize() + 1));
        this.method = method;
        this.vm = vm;
        this.bs = new BytecodeStream(method.getCode());
        FrameSlot[] slots = getFrameDescriptor().getSlots().toArray(new FrameSlot[0]);
        this.locals = Arrays.copyOfRange(slots, 0, method.getMaxLocals());
        this.stackSlots = Arrays.copyOfRange(slots, method.getMaxLocals(), method.getMaxLocals() + method.getMaxStackSize());
        this.tagSlots = Arrays.copyOfRange(slots, method.getMaxLocals() + method.getMaxStackSize(), method.getMaxLocals() + 2 * method.getMaxStackSize());
        this.tosSlot = slots[slots.length - 1];
    }

    public MethodInfo getMethod() {
        return method;
    }

    private static FrameDescriptor initFrameDescriptor(int slotCount) {
        FrameDescriptor descriptor = new FrameDescriptor();
        for (int i = 0; i < slotCount; ++i) {
            descriptor.addFrameSlot(i);
        }
        return descriptor;
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
            frame.setObject(locals[n], frameArguments[0]);
            n += JavaKind.Object.getSlotCount();
        }
        for (int i = 0; i < argCount; ++i) {
            JavaKind expectedkind = method.getSignature().getParameterKind(i);
            // @formatter:off
            // Checkstyle: stop
            switch (expectedkind) {
                case Boolean : frame.setInt(locals[n], ((boolean) arguments[i]) ? 1 : 0); break;
                case Byte    : frame.setInt(locals[n], ((byte) arguments[i]));            break;
                case Short   : frame.setInt(locals[n], ((short) arguments[i]));           break;
                case Char    : frame.setInt(locals[n], ((char) arguments[i]));            break;
                case Int     : frame.setInt(locals[n], (int) arguments[i]);               break;
                case Float   : frame.setFloat(locals[n], (float) arguments[i]);           break;
                case Long    : frame.setLong(locals[n], (long) arguments[i]);             break;
                case Double  : frame.setDouble(locals[n], (double) arguments[i]);         break;
                case Object  : frame.setObject(locals[n], arguments[i]);                  break;
                default      : throw EspressoError.shouldNotReachHere("unexpected kind");
            }
            // @formatter:on
            // Checkstyle: resume
            n += expectedkind.getSlotCount();
        }
    }

    @Override
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    public Object execute(VirtualFrame frame) {
        int curBCI = 0;
        initArguments(frame);

        frame.setInt(tosSlot, 0);

        loop: while (true) {
            try {
                // bytecodesExecuted.inc();
                int curOpcode = bs.currentBC(curBCI);
                CompilerAsserts.partialEvaluationConstant(curBCI);
                CompilerAsserts.partialEvaluationConstant(curOpcode);
                // @formatter:off
                // Checkstyle: stop
                switch (curOpcode) {
                    case NOP         : break;
                    case ACONST_NULL : pushObject(frame, StaticObject.NULL); break;
                    case ICONST_M1   : // fall through
                    case ICONST_0    : // fall through
                    case ICONST_1    : // fall through
                    case ICONST_2    : // fall through
                    case ICONST_3    : // fall through
                    case ICONST_4    : // fall through
                    case ICONST_5    : pushInt(frame, curOpcode - ICONST_0); break;
                    case LCONST_0    : pushLong(frame, 0L); break;
                    case LCONST_1    : pushLong(frame, 1L); break;
                    case FCONST_0    : pushFloat(frame, 0.0F); break;
                    case FCONST_1    : pushFloat(frame, 1.0F); break;
                    case FCONST_2    : pushFloat(frame, 2.0F); break;
                    case DCONST_0    : pushDouble(frame, 0.0D); break;
                    case DCONST_1    : pushDouble(frame, 1.0D); break;
                    case BIPUSH      : pushInt(frame, bs.readByte(curBCI)); break;
                    case SIPUSH      : pushInt(frame, bs.readShort(curBCI)); break;
                    case LDC         : // fall through
                    case LDC_W       : // fall through
                    case LDC2_W      : pushPoolConstant(frame, bs.readCPI(curBCI)); break;
                    case ILOAD       : pushInt(frame, FrameUtil.getIntSafe(frame, locals[bs.readLocalIndex(curBCI)])); break;
                    case LLOAD       : pushLong(frame, FrameUtil.getLongSafe(frame, locals[bs.readLocalIndex(curBCI)])); break;
                    case FLOAD       : pushFloat(frame, FrameUtil.getFloatSafe(frame, locals[bs.readLocalIndex(curBCI)])); break;
                    case DLOAD       : pushDouble(frame, FrameUtil.getDoubleSafe(frame, locals[bs.readLocalIndex(curBCI)])); break;
                    case ALOAD       : pushObject(frame, (StaticObject) FrameUtil.getObjectSafe(frame, locals[bs.readLocalIndex(curBCI)])); break;
                    case ILOAD_0     : // fall through
                    case ILOAD_1     : // fall through
                    case ILOAD_2     : // fall through
                    case ILOAD_3     : pushInt(frame, FrameUtil.getIntSafe(frame, locals[curOpcode - ILOAD_0])); break;
                    case LLOAD_0     : // fall through
                    case LLOAD_1     : // fall through
                    case LLOAD_2     : // fall through
                    case LLOAD_3     : pushLong(frame, FrameUtil.getLongSafe(frame, locals[curOpcode - LLOAD_0])); break;
                    case FLOAD_0     : // fall through
                    case FLOAD_1     : // fall through
                    case FLOAD_2     : // fall through
                    case FLOAD_3     : pushFloat(frame, FrameUtil.getFloatSafe(frame, locals[curOpcode - FLOAD_0])); break;
                    case DLOAD_0     : // fall through
                    case DLOAD_1     : // fall through
                    case DLOAD_2     : // fall through
                    case DLOAD_3     : pushDouble(frame, FrameUtil.getDoubleSafe(frame, locals[curOpcode - DLOAD_0])); break;
                    case ALOAD_0     : // fall through
                    case ALOAD_1     : // fall through
                    case ALOAD_2     : // fall through
                    case ALOAD_3     : pushObject(frame, (StaticObject) FrameUtil.getObjectSafe(frame, locals[curOpcode - ALOAD_0])); break;
                    case IALOAD      :
                        stack[stackIndex - 2] = vm.getArrayInt(stack[stackIndex - 1], nullCheck(stack[stackIndex - 2]));
                        stackIndex--;
                    case LALOAD      : pushLong(frame, vm.getArrayLong(popInt(frame), nullCheck(popObject(frame)))); break;
                    case FALOAD      : pushFloat(frame, vm.getArrayFloat(popInt(frame), nullCheck(popObject(frame)))); break;
                    case DALOAD      : pushDouble(frame, vm.getArrayDouble(popInt(frame), nullCheck(popObject(frame)))); break;
                    case AALOAD      : pushObject(frame, vm.getArrayObject(popInt(frame), nullCheck(popObject(frame)))); break;
                    case BALOAD      : pushInt(frame, vm.getArrayByte(popInt(frame), nullCheck(popObject(frame)))); break;
                    case CALOAD      : pushInt(frame, vm.getArrayChar(popInt(frame), nullCheck(popObject(frame)))); break;
                    case SALOAD      : pushInt(frame, vm.getArrayShort(popInt(frame), nullCheck(popObject(frame)))); break;
                    case ISTORE      : frame.setInt(locals[bs.readLocalIndex(curBCI)], popInt(frame)); break;
                    case LSTORE      : frame.setLong(locals[bs.readLocalIndex(curBCI)], popLong(frame)); break;
                    case FSTORE      : frame.setFloat(locals[bs.readLocalIndex(curBCI)], popFloat(frame)); break;
                    case DSTORE      : frame.setDouble(locals[bs.readLocalIndex(curBCI)], popDouble(frame)); break;
                    case ASTORE      : frame.setObject(locals[bs.readLocalIndex(curBCI)], popReturnAddressOrObject(frame)); break;
                    case ISTORE_0    : // fall through
                    case ISTORE_1    : // fall through
                    case ISTORE_2    : // fall through
                    case ISTORE_3    : frame.setInt(locals[curOpcode - ISTORE_0], popInt(frame)); break;
                    case LSTORE_0    : // fall through
                    case LSTORE_1    : // fall through
                    case LSTORE_2    : // fall through
                    case LSTORE_3    : frame.setLong(locals[curOpcode - LSTORE_0], popLong(frame)); break;
                    case FSTORE_0    : // fall through
                    case FSTORE_1    : // fall through
                    case FSTORE_2    : // fall through
                    case FSTORE_3    : frame.setFloat(locals[curOpcode - FSTORE_0], popFloat(frame)); break;
                    case DSTORE_0    : // fall through
                    case DSTORE_1    : // fall through
                    case DSTORE_2    : // fall through
                    case DSTORE_3    : frame.setDouble(locals[curOpcode - DSTORE_0], popDouble(frame)); break;
                    case ASTORE_0    : // fall through
                    case ASTORE_1    : // fall through
                    case ASTORE_2    : // fall through
                    case ASTORE_3    : frame.setObject(locals[curOpcode - ASTORE_0], popReturnAddressOrObject(frame)); break;
                    case IASTORE     : vm.setArrayInt(popInt(frame), popInt(frame), nullCheck(popObject(frame))); break;
                    case LASTORE     : vm.setArrayLong(popLong(frame), popInt(frame), nullCheck(popObject(frame))); break;
                    case FASTORE     : vm.setArrayFloat(popFloat(frame), popInt(frame), nullCheck(popObject(frame))); break;
                    case DASTORE     : vm.setArrayDouble(popDouble(frame), popInt(frame), nullCheck(popObject(frame))); break;
                    case AASTORE     : vm.setArrayObject(popObject(frame), popInt(frame), (StaticObjectArray) nullCheck(popObject(frame))); break;
                    case BASTORE     : vm.setArrayByte((byte) popInt(frame), popInt(frame), nullCheck(popObject(frame))); break;
                    case CASTORE     : vm.setArrayChar((char) popInt(frame), popInt(frame), nullCheck(popObject(frame))); break;
                    case SASTORE     : vm.setArrayShort((short) popInt(frame), popInt(frame), nullCheck(popObject(frame))); break;
                    case POP         : popVoid(frame, 1); break;
                    case POP2        : popVoid(frame, 2); break;
                    case DUP         : dup1(frame); break;
                    case DUP_X1      : dupx1(frame); break;
                    case DUP_X2      : dupx2(frame); break;
                    case DUP2        : dup2(frame); break;
                    case DUP2_X1     : dup2x1(frame); break;
                    case DUP2_X2     : dup2x2(frame); break;
                    case SWAP        : swapSingle(frame); break;
                    case IADD        : pushInt(frame, popInt(frame) + popInt(frame)); break;
                    case LADD        : pushLong(frame, popLong(frame) + popLong(frame)); break;
                    case FADD        : pushFloat(frame, popFloat(frame) + popFloat(frame)); break;
                    case DADD        : pushDouble(frame, popDouble(frame) + popDouble(frame)); break;
                    case ISUB        : pushInt(frame, -popInt(frame) + popInt(frame)); break;
                    case LSUB        : pushLong(frame, -popLong(frame) + popLong(frame)); break;
                    case FSUB        : pushFloat(frame, -popFloat(frame) + popFloat(frame)); break;
                    case DSUB        : pushDouble(frame, -popDouble(frame) + popDouble(frame)); break;
                    case IMUL        : pushInt(frame, popInt(frame) * popInt(frame)); break;
                    case LMUL        : pushLong(frame, popLong(frame) * popLong(frame)); break;
                    case FMUL        : pushFloat(frame, popFloat(frame) * popFloat(frame)); break;
                    case DMUL        : pushDouble(frame, popDouble(frame) * popDouble(frame)); break;
                    case IDIV        : pushInt(frame, divInt(checkNonZero(popInt(frame)), popInt(frame))); break;
                    case LDIV        : pushLong(frame, divLong(checkNonZero(popLong(frame)), popLong(frame))); break;
                    case FDIV        : pushFloat(frame, divFloat(popFloat(frame), popFloat(frame))); break;
                    case DDIV        : pushDouble(frame, divDouble(popDouble(frame), popDouble(frame))); break;
                    case IREM        : pushInt(frame, remInt(checkNonZero(popInt(frame)), popInt(frame))); break;
                    case LREM        : pushLong(frame, remLong(checkNonZero(popLong(frame)), popLong(frame))); break;
                    case FREM        : pushFloat(frame, remFloat(popFloat(frame), popFloat(frame))); break;
                    case DREM        : pushDouble(frame, remDouble(popDouble(frame), popDouble(frame))); break;
                    case INEG        : pushInt(frame, -popInt(frame)); break;
                    case LNEG        : pushLong(frame, -popLong(frame)); break;
                    case FNEG        : pushFloat(frame, -popFloat(frame)); break;
                    case DNEG        : pushDouble(frame, -popDouble(frame)); break;
                    case ISHL        : pushInt(frame, shiftLeftInt(popInt(frame), popInt(frame))); break;
                    case LSHL        : pushLong(frame, shiftLeftLong(popInt(frame), popLong(frame))); break;
                    case ISHR        : pushInt(frame, shiftRightSignedInt(popInt(frame), popInt(frame))); break;
                    case LSHR        : pushLong(frame, shiftRightSignedLong(popInt(frame), popLong(frame))); break;
                    case IUSHR       : pushInt(frame, shiftRightUnsignedInt(popInt(frame), popInt(frame))); break;
                    case LUSHR       : pushLong(frame, shiftRightUnsignedLong(popInt(frame), popLong(frame))); break;
                    case IAND        : pushInt(frame, popInt(frame) & popInt(frame)); break;
                    case LAND        : pushLong(frame, popLong(frame) & popLong(frame)); break;
                    case IOR         : pushInt(frame, popInt(frame) | popInt(frame)); break;
                    case LOR         : pushLong(frame, popLong(frame) | popLong(frame)); break;
                    case IXOR        : pushInt(frame, popInt(frame) ^ popInt(frame)); break;
                    case LXOR        : pushLong(frame, popLong(frame) ^ popLong(frame)); break;
                    case IINC        : frame.setInt(locals[bs.readLocalIndex(curBCI)], FrameUtil.getIntSafe(frame, locals[bs.readLocalIndex(curBCI)]) + bs.readIncrement(curBCI)); break;
                    case I2L         : pushLong(frame, popInt(frame)); break;
                    case I2F         : pushFloat(frame, popInt(frame)); break;
                    case I2D         : pushDouble(frame, popInt(frame)); break;
                    case L2I         : pushInt(frame, (int) popLong(frame)); break;
                    case L2F         : pushFloat(frame, popLong(frame)); break;
                    case L2D         : pushDouble(frame, popLong(frame)); break;
                    case F2I         : pushInt(frame, (int) popFloat(frame)); break;
                    case F2L         : pushLong(frame, (long) popFloat(frame)); break;
                    case F2D         : pushDouble(frame, popFloat(frame)); break;
                    case D2I         : pushInt(frame, (int) popDouble(frame)); break;
                    case D2L         : pushLong(frame, (long) popDouble(frame)); break;
                    case D2F         : pushFloat(frame, (float) popDouble(frame)); break;
                    case I2B         : pushInt(frame, (byte) popInt(frame)); break;
                    case I2C         : pushInt(frame, (char) popInt(frame)); break;
                    case I2S         : pushInt(frame, (short) popInt(frame)); break;
                    case LCMP        : pushInt(frame, compareLong(popLong(frame), popLong(frame))); break;
                    case FCMPL       : pushInt(frame, compareFloatLess(popFloat(frame), popFloat(frame))); break;
                    case FCMPG       : pushInt(frame, compareFloatGreater(popFloat(frame), popFloat(frame))); break;
                    case DCMPL       : pushInt(frame, compareDoubleLess(popDouble(frame), popDouble(frame))); break;
                    case DCMPG       : pushInt(frame, compareDoubleGreater(popDouble(frame), popDouble(frame))); break;
                    case IFEQ        : if (popInt(frame) == 0) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IFNE        : if (popInt(frame) != 0) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IFLT        : if (popInt(frame) < 0) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IFGE        : if (popInt(frame) >= 0) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IFGT        : if (popInt(frame) > 0) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IFLE        : if (popInt(frame) <= 0) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IF_ICMPEQ   : if (popInt(frame) == popInt(frame)) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IF_ICMPNE   : if (popInt(frame) != popInt(frame)) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IF_ICMPLT   : if (popInt(frame) > popInt(frame)) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IF_ICMPGE   : if (popInt(frame) <= popInt(frame)) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IF_ICMPGT   : if (popInt(frame) < popInt(frame)) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IF_ICMPLE   : if (popInt(frame) >= popInt(frame)) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IF_ACMPEQ   : if (popObject(frame) == popObject(frame)) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IF_ACMPNE   : if (popObject(frame) != popObject(frame)) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case GOTO        : // fall through
                    case GOTO_W      : checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop;
                    case JSR         : // fall through
                    case JSR_W       : pushReturnAddress(frame, bs.nextBCI(curBCI)); curBCI = bs.readBranchDest(curBCI); continue loop;
                    case RET         : curBCI = ((ReturnAddress) FrameUtil.getObjectSafe(frame, locals[bs.readLocalIndex(curBCI)])).getBci(); continue loop;
                        // @formatter:on
                    // Checkstyle: resume
                    case TABLESWITCH: {
                        int index = popInt(frame);
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
                        int key = popInt(frame);
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
                    // @formatter:off
                    // Checkstyle: stop
                    case IRETURN               : return exitMethodAndReturn(popInt(frame));
                    case LRETURN               : return exitMethodAndReturnObject(popLong(frame));
                    case FRETURN               : return exitMethodAndReturnObject(popFloat(frame));
                    case DRETURN               : return exitMethodAndReturnObject(popDouble(frame));
                    case ARETURN               : return exitMethodAndReturnObject(popObject(frame));
                    case RETURN                : return exitMethodAndReturn();
                    case GETSTATIC             : getField(frame, resolveField(curOpcode, bs.readCPI(curBCI)), true); break;
                    case PUTSTATIC             : putField(frame, resolveField(curOpcode, bs.readCPI(curBCI)), true); break;
                    case GETFIELD              : getField(frame, resolveField(curOpcode, bs.readCPI(curBCI)), false); break;
                    case PUTFIELD              : putField(frame, resolveField(curOpcode, bs.readCPI(curBCI)), false); break;
                    case INVOKEVIRTUAL         : quickenAndCallInvokeVirtual(frame, curBCI, resolveMethod(curOpcode, bs.readCPI(curBCI))); break;
                    case INVOKESPECIAL         : quickenAndCallInvokeSpecial(frame, curBCI, resolveMethod(curOpcode, bs.readCPI(curBCI))); break;
                    case INVOKESTATIC          : quickenAndCallInvokeStatic(frame, curBCI, resolveMethod(curOpcode, bs.readCPI(curBCI))); break;
                    case INVOKEINTERFACE       : quickenAndCallInvokeInterface(frame, curBCI, resolveMethod(curOpcode, bs.readCPI(curBCI))); break;
                    case NEW                   : pushObject(frame, allocateInstance(resolveType(curOpcode, bs.readCPI(curBCI)))); break;
                    case NEWARRAY              : pushObject(frame, InterpreterToVM.allocatePrimitiveArray(bs.readByte(curBCI), popInt(frame))); break;
                    case ANEWARRAY             : pushObject(frame, allocateArray(resolveType(curOpcode, bs.readCPI(curBCI)), popInt(frame))); break;
                    case ARRAYLENGTH           : pushInt(frame, vm.arrayLength(nullCheck(popObject(frame)))); break;
                    case ATHROW                : CompilerDirectives.transferToInterpreter(); throw new EspressoException(nullCheck(popObject(frame)));
                    case CHECKCAST             : pushObject(frame, checkCast(popObject(frame), resolveType(curOpcode, bs.readCPI(curBCI)))); break;
                    case INSTANCEOF            : pushInt(frame, instanceOf(popObject(frame), resolveType(curOpcode, bs.readCPI(curBCI))) ? 1  : 0); break;
                    case MONITORENTER          : vm.monitorEnter(nullCheck(popObject(frame))); break;
                    case MONITOREXIT           : vm.monitorExit(nullCheck(popObject(frame))); break;
                    case WIDE                  : throw EspressoError.shouldNotReachHere(); // ByteCodeStream.currentBC() should never return this bytecode.
                    case MULTIANEWARRAY        : pushObject(frame, allocateMultiArray(frame, resolveType(curOpcode, bs.readCPI(curBCI)), bs.readUByte(curBCI + 3))); break;
                    case IFNULL                : if (StaticObject.isNull(popObject(frame))) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case IFNONNULL             : if (StaticObject.notNull(popObject(frame))) { checkLoop(curBCI, frame); curBCI = bs.readBranchDest(curBCI); continue loop; } break;
                    case BREAKPOINT            : throw EspressoError.unimplemented(Bytecodes.nameOf(curOpcode) + " not supported.");
                    case INVOKEDYNAMIC         : throw EspressoError.unimplemented(Bytecodes.nameOf(curOpcode) + " not supported.");
                    case QUICK_INVOKESPECIAL   : // fall through
                    case QUICK_INVOKESTATIC    : // fall through
                    case QUICK_INVOKEVIRTUAL   : // fall through
                    case QUICK_INVOKEINTERFACE : nodes[bs.readCPI(curBCI)].invoke(frame); break;
                    default                    : throw EspressoError.shouldNotReachHere(Bytecodes.nameOf(curOpcode));
                }
                // @formatter:on
                // Checkstyle: resume
            } catch (EspressoException e) {
                CompilerDirectives.transferToInterpreter();
                ExceptionHandler handler = resolveExceptionHandlers(curBCI, e.getException());
                if (handler != null) {
                    clear(frame);
                    pushObject(frame, e.getException());
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
                    clear(frame);
                    pushObject(frame, ex);
                    curBCI = handler.getHandlerBCI();
                    continue loop; // skip bs.next()
                } else {
                    throw new EspressoException(ex);
                }
            }
            curBCI = bs.next(curBCI);
        }
    }

    private void checkLoop(int curBCI, VirtualFrame frame) {
        if (bs.readBranchDest(curBCI) < curBCI) {
            if (tos(frame) != 0) {
                CompilerDirectives.transferToInterpreter();
                CompilerDirectives.bailout("non-0 back-edges");
            } else {
                frame.setInt(tosSlot, 0);
            }
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

    @ExplodeLoop
    private static Object[] copyOfRange(Object[] src, int from, int toExclusive) {
        int len = toExclusive - from;
        Object[] dst = new Object[len];
        for (int i = 0; i < len; ++i) {
            dst[i] = src[i + from];
        }
        return dst;
    }

    private void pushPoolConstant(final VirtualFrame frame, char cpi) {
        ConstantPool pool = getConstantPool();
        PoolConstant constant = pool.at(cpi);
        if (constant instanceof IntegerConstant) {
            pushInt(frame, ((IntegerConstant) constant).value());
        } else if (constant instanceof LongConstant) {
            pushLong(frame, ((LongConstant) constant).value());
        } else if (constant instanceof DoubleConstant) {
            pushDouble(frame, ((DoubleConstant) constant).value());
        } else if (constant instanceof FloatConstant) {
            pushFloat(frame, ((FloatConstant) constant).value());
        } else if (constant instanceof StringConstant) {
            // TODO(peterssen): Must be interned once, on creation.
            pushObject(frame, ((StringConstant) constant).intern(pool));
        } else if (constant instanceof ClassConstant) {
            Klass klass = ((ClassConstant) constant).resolve(getConstantPool(), cpi);
            pushObject(frame, klass.mirror());
        }
    }

    private ConstantPool getConstantPool() {
        return this.method.getConstantPool();
    }

    // region Bytecode quickening

    private char addInvokeNode(InvokeNode node) {
        CompilerAsserts.neverPartOfCompilation();
        nodes = Arrays.copyOf(nodes, nodes.length + 1);
        int nodeIndex = nodes.length - 1; // latest empty slot
        nodes[nodeIndex] = insert(node);
        return (char) nodeIndex;
    }

    private void patchBci(int bci, byte opcode, char nodeIndex) {
        assert Bytecodes.isQuickened(opcode);
        byte[] code = getMethod().getCode();
        code[bci] = opcode;
        code[bci + 1] = (byte) ((nodeIndex >> 8) & 0xFF);
        code[bci + 2] = (byte) ((nodeIndex) & 0xFF);
    }

    private void quickenAndCallInvokeStatic(final VirtualFrame frame, int curBCI, MethodInfo resolvedMethod) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        int nodeIndex = addInvokeNode(new InvokeStaticNode(resolvedMethod));
        patchBci(curBCI, (byte) QUICK_INVOKESTATIC, (char) nodeIndex);
        nodes[nodeIndex].invoke(frame);
    }

    private void quickenAndCallInvokeSpecial(final VirtualFrame frame, int curBCI, MethodInfo resolvedMethod) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        int nodeIndex = addInvokeNode(new InvokeSpecialNode(resolvedMethod));
        patchBci(curBCI, (byte) QUICK_INVOKESPECIAL, (char) nodeIndex);
        nodes[nodeIndex].invoke(frame);
    }

    private void quickenAndCallInvokeInterface(final VirtualFrame frame, int curBCI, MethodInfo resolutionSeed) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        int nodeIndex = addInvokeNode(InvokeInterfaceNodeGen.create(resolutionSeed));
        patchBci(curBCI, (byte) QUICK_INVOKEINTERFACE, (char) nodeIndex);
        nodes[nodeIndex].invoke(frame);
    }

    // TODO(peterssen): Remove duplicated methods.
    private void quickenAndCallInvokeVirtual(final VirtualFrame frame, int curBCI, MethodInfo resolutionSeed) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (resolutionSeed.isFinal() || resolutionSeed.getDeclaringClass().isFinalFlagSet()) {
            quickenAndCallInvokeSpecial(frame, curBCI, resolutionSeed);
            return;
        }
        int nodeIndex = addInvokeNode(InvokeVirtualNodeGen.create(resolutionSeed));
        patchBci(curBCI, (byte) QUICK_INVOKEVIRTUAL, (char) nodeIndex);
        nodes[nodeIndex].invoke(frame);
    }

    // endregion Bytecode quickening

    // region Class/Method/Field resolution

    private Klass resolveType(@SuppressWarnings("unused") int opcode, char cpi) {
        // TODO(peterssen): Check opcode.
        ConstantPool pool = getConstantPool();
        return pool.classAt(cpi).resolve(pool, cpi);
    }

    private MethodInfo resolveMethod(int opcode, char cpi) {
        CompilerAsserts.partialEvaluationConstant(cpi);
        CompilerAsserts.partialEvaluationConstant(opcode);
        ConstantPool pool = getConstantPool();
        MethodInfo methodInfo = pool.methodAt(cpi).resolve(pool, cpi);
        return methodInfo;
    }

    private FieldInfo resolveField(@SuppressWarnings("unused") int opcode, char cpi) {
        // TODO(peterssen): Check opcode.
        ConstantPool pool = getConstantPool();
        return pool.fieldAt(cpi).resolve(pool, cpi);
    }

    // endregion Class/Method/Field resolution

    // region Instance/array allocation

    @CompilerDirectives.TruffleBoundary
    private StaticObjectArray allocateArray(Klass componentType, int length) {
        assert !componentType.isPrimitive();
        return vm.newArray(componentType, length);
    }

    private StaticObject allocateMultiArray(final VirtualFrame frame, Klass klass, int allocatedDimensions) {
        assert klass.isArray();
        int[] dimensions = new int[allocatedDimensions];
        for (int i = allocatedDimensions - 1; i >= 0; i--) {
            dimensions[i] = popInt(frame);
        }
        return vm.newMultiArray(klass, dimensions);
    }

    private StaticObject allocateInstance(Klass klass) {
        klass.initialize();
        return vm.newObject(klass);
    }

    // endregion Instance/array allocation

    // region Method return

    private Object exitMethodAndReturn(int result) {
        // @formatter:off
        // Checkstyle: stop
        switch (method.getReturnType().getJavaKind()) {
            case Boolean : return result != 0;
            case Byte    : return (byte) result;
            case Short   : return (short) result;
            case Char    : return (char) result;
            case Int     : return result;
            default      : throw EspressoError.shouldNotReachHere("unexpected kind");
        }
        // @formatter:on
        // Checkstyle: resume
    }

    private static Object exitMethodAndReturnObject(Object result) {
        return result;
    }

    private static Object exitMethodAndReturn() {
        return exitMethodAndReturnObject(StaticObject.VOID);
    }

    // endregion Method return

    // region Arithmetic/binary operations

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

    // endregion Arithmetic/binary operations

    // region Type checks

    private boolean instanceOf(StaticObject instance, Klass typeToCheck) {
        return vm.instanceOf(instance, typeToCheck);
    }

    private StaticObject checkCast(StaticObject instance, Klass typeToCheck) {
        return vm.checkCast(instance, typeToCheck);
    }

    // endregion Type checks

    // region Comparisons

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

    // endregion Comparisons

    // region Misc. checks

    private StaticObject nullCheck(StaticObject value) {
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
        CompilerDirectives.transferToInterpreter();
        throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArithmeticException.class, "/ by zero");
    }

    private static long checkNonZero(long value) {
        if (value != 0L) {
            return value;
        }
        CompilerDirectives.transferToInterpreter();
        throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArithmeticException.class, "/ by zero");
    }

    // endregion Misc. checks

    // region Field read/write

    private void putField(final VirtualFrame frame, FieldInfo field, boolean isStatic) {
        // fieldWrites.inc();
        assert Modifier.isStatic(field.getFlags()) == isStatic;

        // Arrays do not have fields, the receiver can only be a StaticObject.
        if (isStatic) {
            field.getDeclaringClass().initialize();
        }

        Supplier<StaticObject> receiver = new Supplier<StaticObject>() {
            @Override
            public StaticObject get() {
                return isStatic
                                ? (field.getDeclaringClass()).getStatics() /* static storage */
                                : nullCheck(popObject(frame));
            }
        };

        // @formatter:off
        // Checkstyle: stop
        switch (field.getKind()) {
            case Boolean : vm.setFieldBoolean(popInt(frame) == 1, receiver.get(), field);  break;
            case Byte    : vm.setFieldByte((byte) popInt(frame), receiver.get(), field);   break;
            case Char    : vm.setFieldChar((char) popInt(frame), receiver.get(), field);   break;
            case Short   : vm.setFieldShort((short) popInt(frame), receiver.get(), field); break;
            case Int     : vm.setFieldInt(popInt(frame), receiver.get(), field);           break;
            case Double  : vm.setFieldDouble(popDouble(frame), receiver.get(), field);     break;
            case Float   : vm.setFieldFloat(popFloat(frame), receiver.get(), field);       break;
            case Long    : vm.setFieldLong(popLong(frame), receiver.get(), field);         break;
            case Object  : vm.setFieldObject(popObject(frame), receiver.get(), field);     break;
            default      : throw EspressoError.shouldNotReachHere("unexpected kind");
        }
        // @formatter:on
        // Checkstyle: resume
    }

    private void getField(final VirtualFrame frame, FieldInfo field, boolean isStatic) {
        // fieldReads.inc();
        if (isStatic) {
            field.getDeclaringClass().initialize();
        }
        assert Modifier.isStatic(field.getFlags()) == isStatic;
        // Arrays do not have fields, the receiver can only be a StaticObject.
        StaticObject receiver = isStatic
                        ? field.getDeclaringClass().getStatics() /* static storage */
                        : nullCheck(popObject(frame));

        // @formatter:off
        // Checkstyle: stop
        switch (field.getKind()) {
            case Boolean : pushInt(frame, vm.getFieldBoolean(receiver, field) ? 1 : 0); break;
            case Byte    : pushInt(frame, vm.getFieldByte(receiver, field));      break;
            case Char    : pushInt(frame, vm.getFieldChar(receiver, field));      break;
            case Short   : pushInt(frame, vm.getFieldShort(receiver, field));     break;
            case Int     : pushInt(frame, vm.getFieldInt(receiver, field));       break;
            case Double  : pushDouble(frame, vm.getFieldDouble(receiver, field)); break;
            case Float   : pushFloat(frame, vm.getFieldFloat(receiver, field));   break;
            case Long    : pushLong(frame, vm.getFieldLong(receiver, field));     break;
            case Object  : pushObject(frame, vm.getFieldObject(receiver, field)); break;
            default      : throw EspressoError.shouldNotReachHere("unexpected kind");
        }
        // @formatter:on
        // Checkstyle: resume
    }

    // endregion Field read/write

    @Override
    public String toString() {
        return method.getDeclaringClass().getName() + "." + method.getName() + method.getSignature().toString();
    }

    @Override
    public Meta.Method getOriginalMethod() {
        return meta(method);
    }

    private int tos(VirtualFrame frame) {
        return FrameUtil.getIntSafe(frame, tosSlot);
    }

    private int tosGetAndIncrement(VirtualFrame frame) {
        int tos = tos(frame);
        frame.setInt(tosSlot, tos + 1);
        return tos;
    }

    private int tosDecrementAndGet(VirtualFrame frame) {
        int tos = tos(frame);
        --tos;
        frame.setInt(tosSlot, tos);
        return tos;
    }

    @Override
    public void popVoid(final VirtualFrame frame, int slots) {
        assert slots == 1 || slots == 2;
        frame.setInt(tosSlot, tos(frame) - slots);
        assert tos(frame) >= 0;
    }

    @Override
    public void pushObject(final VirtualFrame frame, StaticObject value) {
        assert value != null;
        int tos = FrameUtil.getIntSafe(frame, tosSlot);
        frame.setByte(tagSlots[tos], (byte) JavaKind.Object.ordinal());
        frame.setObject(stackSlots[tos++], value);
        frame.setInt(tosSlot, tos);
    }

    @Override
    public void pushReturnAddress(final VirtualFrame frame, int bci) {
        assert bci >= 0;
        int tos = FrameUtil.getIntSafe(frame, tosSlot);
        frame.setByte(tagSlots[tos], (byte) JavaKind.Object.ordinal());
        frame.setObject(stackSlots[tosGetAndIncrement(frame)], ReturnAddress.create(bci));
    }

    @Override
    public void pushInt(final VirtualFrame frame, int value) {
        int tos = FrameUtil.getIntSafe(frame, tosSlot);
        frame.setByte(tagSlots[tos], (byte) JavaKind.Int.ordinal());
        frame.setInt(stackSlots[tosGetAndIncrement(frame)], value);
    }

    private void pushIllegal(final VirtualFrame frame) {
        int tos = FrameUtil.getIntSafe(frame, tosSlot);
        frame.setByte(tagSlots[tos], (byte) JavaKind.Illegal.ordinal());
        frame.setObject(stackSlots[tosGetAndIncrement(frame)], null);
    }

    @Override
    public void pushLong(final VirtualFrame frame, long value) {
        pushIllegal(frame);
        int tos = FrameUtil.getIntSafe(frame, tosSlot);
        frame.setByte(tagSlots[tos], (byte) JavaKind.Long.ordinal());
        frame.setLong(stackSlots[tosGetAndIncrement(frame)], value);
    }

    @Override
    public void pushFloat(final VirtualFrame frame, float value) {
        int tos = FrameUtil.getIntSafe(frame, tosSlot);
        frame.setByte(tagSlots[tos], (byte) JavaKind.Float.ordinal());
        frame.setFloat(stackSlots[tosGetAndIncrement(frame)], value);
    }

    @Override
    public void pushDouble(final VirtualFrame frame, double value) {
        pushIllegal(frame);
        int tos = FrameUtil.getIntSafe(frame, tosSlot);
        frame.setByte(tagSlots[tos], (byte) JavaKind.Double.ordinal());
        frame.setDouble(stackSlots[tosGetAndIncrement(frame)], value);
    }

    private JavaKind peekTag(VirtualFrame frame) {
        return KIND_VALUES.get(FrameUtil.getByteSafe(frame, tagSlots[tos(frame) - 1]));
    }

    @Override
    public StaticObject popObject(final VirtualFrame frame) {
        assert peekTag(frame) == JavaKind.Object;
        Object top = FrameUtil.getObjectSafe(frame, stackSlots[tosDecrementAndGet(frame)]);
        assert top != null : "Use StaticObject.NULL";
        return (StaticObject) top;
    }

    @Override
    public Object popReturnAddressOrObject(final VirtualFrame frame) {
        assert peekTag(frame) == JavaKind.Object || peekTag(frame) == JavaKind.ReturnAddress;
        Object top = FrameUtil.getObjectSafe(frame, stackSlots[tosDecrementAndGet(frame)]);
        assert top != null : "Use StaticObject.NULL";
        assert top instanceof StaticObject || top instanceof ReturnAddress;
        return top;
    }

    @Override
    public int popInt(final VirtualFrame frame) {
        assert peekTag(frame) == JavaKind.Int;
        return FrameUtil.getIntSafe(frame, stackSlots[tosDecrementAndGet(frame)]);
    }

    @Override
    public float popFloat(final VirtualFrame frame) {
        assert peekTag(frame) == JavaKind.Float;
        return FrameUtil.getFloatSafe(frame, stackSlots[tosDecrementAndGet(frame)]);
    }

    @Override
    public long popLong(final VirtualFrame frame) {
        assert peekTag(frame) == JavaKind.Long;
        long ret = FrameUtil.getLongSafe(frame, stackSlots[tosDecrementAndGet(frame)]);
        popIllegal(frame);
        return ret;
    }

    @Override
    public double popDouble(final VirtualFrame frame) {
        assert peekTag(frame) == JavaKind.Double;
        double ret = FrameUtil.getDoubleSafe(frame, stackSlots[tosDecrementAndGet(frame)]);
        popIllegal(frame);
        return ret;
    }

    private void popIllegal(VirtualFrame frame) {
        assert peekTag(frame) == JavaKind.Illegal;
        assert tos(frame) > 0;
        tosDecrementAndGet(frame);
    }

    private static int numberOfSlots(JavaKind kind) {
        assert kind != null;
        if (kind == JavaKind.Long || kind == JavaKind.Double) {
            return 2;
        }
        // Illegal take 1 slot.
        return 1;
    }

    @Override
    public void dup1(final VirtualFrame frame) {
        assert numberOfSlots(peekTag(frame)) == 1;
        pushUnsafe1(frame, peekUnsafe1(frame), peekTag(frame));
    }

    private Object popUnsafe1(final VirtualFrame frame) {
        return frame.getValue(stackSlots[tosDecrementAndGet(frame)]);
    }

    private Object peekUnsafe1(final VirtualFrame frame) {
        return frame.getValue(stackSlots[tos(frame) - 1]);
    }

    private void pushUnsafe1(final VirtualFrame frame, Object value, JavaKind kind) {
        frame.setByte(tagSlots[tos(frame)], (byte) kind.ordinal());
        // @formatter:off
        // Checkstyle: stop
        switch (kind) {
            case Int     : pushInt(frame, (int) value);             break;
            case Float   : pushFloat(frame, (float) value);         break;
            case Object  : pushObject(frame, (StaticObject) value); break;
            case Long    : frame.setLong(stackSlots[tosGetAndIncrement(frame)], (long) value); break;
            case Double  : frame.setDouble(stackSlots[tosGetAndIncrement(frame)], (double) value); break;
            case Illegal : pushIllegal(frame);                      break;
            default      : throw EspressoError.shouldNotReachHere(kind.toString());
        }
        // @formatter:on
        // Checkstyle: resume
    }

    @Override
    public void swapSingle(final VirtualFrame frame) {
        // value2, value1 → value1, value2
        JavaKind tag1 = peekTag(frame);
        assert numberOfSlots(tag1) == 1;
        Object elem1 = popUnsafe1(frame);

        JavaKind tag2 = peekTag(frame);
        assert numberOfSlots(tag2) == 1;
        Object elem2 = popUnsafe1(frame);

        pushUnsafe1(frame, elem1, tag1);
        pushUnsafe1(frame, elem2, tag2);
    }

    @Override
    public void dupx1(final VirtualFrame frame) {
        // value2, value1 → value1, value2, value1
        JavaKind tag1 = peekTag(frame);
        assert numberOfSlots(tag1) == 1;
        Object elem1 = popUnsafe1(frame);

        JavaKind tag2 = peekTag(frame);
        assert numberOfSlots(tag2) == 1;
        Object elem2 = popUnsafe1(frame);

        pushUnsafe1(frame, elem1, tag1);
        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
    }

    @Override
    public void dupx2(final VirtualFrame frame) {
        // value3, value2, value1 → value1, value3, value2, value1
        JavaKind tag1 = peekTag(frame);
        assert numberOfSlots(tag1) == 1;
        Object elem1 = popUnsafe1(frame);

        JavaKind tag2 = peekTag(frame);
        Object elem2 = popUnsafe1(frame);

        JavaKind tag3 = peekTag(frame);
        Object elem3 = popUnsafe1(frame);

        pushUnsafe1(frame, elem1, tag1);
        pushUnsafe1(frame, elem3, tag3);
        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
    }

    @Override
    public void dup2(final VirtualFrame frame) {
        // {value2, value1} → {value2, value1}, {value2, value1}
        JavaKind tag1 = peekTag(frame);
        Object elem1 = popUnsafe1(frame);

        JavaKind tag2 = peekTag(frame);
        Object elem2 = popUnsafe1(frame);

        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
    }

    @Override
    public void dup2x1(final VirtualFrame frame) {
        // value3, {value2, value1} → {value2, value1}, value3, {value2, value1}
        JavaKind tag1 = peekTag(frame);
        Object elem1 = popUnsafe1(frame);

        JavaKind tag2 = peekTag(frame);
        Object elem2 = popUnsafe1(frame);

        JavaKind tag3 = peekTag(frame);
        assert numberOfSlots(tag3) == 1;
        Object elem3 = popUnsafe1(frame);

        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
        pushUnsafe1(frame, elem3, tag3);
        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
    }

    @Override
    public void dup2x2(final VirtualFrame frame) {
        // {value4, value3}, {value2, value1} → {value2, value1}, {value4, value3}, {value2, value1}
        JavaKind tag1 = peekTag(frame);
        Object elem1 = popUnsafe1(frame);
        JavaKind tag2 = peekTag(frame);
        Object elem2 = popUnsafe1(frame);
        JavaKind tag3 = peekTag(frame);
        Object elem3 = popUnsafe1(frame);
        JavaKind tag4 = peekTag(frame);
        Object elem4 = popUnsafe1(frame);
        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
        pushUnsafe1(frame, elem4, tag4);
        pushUnsafe1(frame, elem3, tag3);
        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
    }

    @Override
    public StaticObject peekReceiver(final VirtualFrame frame, MethodInfo method) {
        assert !method.isStatic();
        int slots = method.getSignature().getNumberOfSlotsForParameters();
        StaticObject receiver = (StaticObject) FrameUtil.getObjectSafe(frame, stackSlots[tos(frame) - slots - 1]);
        return receiver;
    }

    @Override
    public void clear(final VirtualFrame frame) {
        frame.setInt(tosSlot, 0);
    }
}
