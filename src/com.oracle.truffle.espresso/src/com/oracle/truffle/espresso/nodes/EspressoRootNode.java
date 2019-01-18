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

import java.util.Arrays;

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
import com.oracle.truffle.espresso.types.SignatureDescriptor;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Bytecode interpreter loop.
 *
 * Calling convention use Java primitive types although internally the VM basic types (e.g. sub-word
 * types are coerced to int) are used with conversions at the boundaries.
 */
public final class EspressoRootNode extends RootNode implements LinkedNode {

    @Children private InvokeNode[] nodes = InvokeNode.EMPTY_ARRAY;

    private final MethodInfo method;
    private final InterpreterToVM vm;

// private static final DebugCounter bytecodesExecuted = DebugCounter.create("Bytecodes executed");
// private static final DebugCounter newInstances = DebugCounter.create("New instances");
// private static final DebugCounter fieldWrites = DebugCounter.create("Field writes");
// private static final DebugCounter fieldReads = DebugCounter.create("Field reads");

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final FrameSlot[] locals;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final FrameSlot[] stackSlots;

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
        super(language, initFrameDescriptor(method.getMaxLocals() + method.getMaxStackSize()));
        CompilerAsserts.neverPartOfCompilation();
        this.method = method;
        this.vm = vm;
        this.bs = new BytecodeStream(method.getCode());
        FrameSlot[] slots = getFrameDescriptor().getSlots().toArray(new FrameSlot[0]);
        this.locals = Arrays.copyOfRange(slots, 0, method.getMaxLocals());
        this.stackSlots = Arrays.copyOfRange(slots, method.getMaxLocals(), method.getMaxLocals() + method.getMaxStackSize());
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

    private int peekInt(VirtualFrame frame, int slot) {
        return FrameUtil.getIntSafe(frame, stackSlots[slot]);
    }

    private StaticObject peekObject(VirtualFrame frame, int slot) {
        Object result = FrameUtil.getObjectSafe(frame, stackSlots[slot]);
        assert result instanceof StaticObject;
        return (StaticObject) result;
    }

    // Boxed value.
    private Object peekValue(VirtualFrame frame, int slot) {
        return frame.getValue(stackSlots[slot]);
    }

    private float peekFloat(VirtualFrame frame, int slot) {
        return FrameUtil.getFloatSafe(frame, stackSlots[slot]);
    }

    private long peekLong(VirtualFrame frame, int slot) {
        return FrameUtil.getLongSafe(frame, stackSlots[slot]);
    }

    private double peekDouble(VirtualFrame frame, int slot) {
        return FrameUtil.getDoubleSafe(frame, stackSlots[slot]);
    }

    private Object peekReturnAddressOrObject(VirtualFrame frame, int slot) {
        Object result = FrameUtil.getObjectSafe(frame, stackSlots[slot]);
        assert result instanceof StaticObject || result instanceof ReturnAddress;
        return result;
    }

    private void putReturnAddress(VirtualFrame frame, int slot, int targetBCI) {
        frame.setObject(stackSlots[slot], ReturnAddress.create(targetBCI));
    }

    private void putObject(VirtualFrame frame, int slot, StaticObject value) {
        frame.setObject(stackSlots[slot], value);
    }

    private void putInt(VirtualFrame frame, int slot, int value) {
        frame.setInt(stackSlots[slot], value);
    }

    private void putFloat(VirtualFrame frame, int slot, float value) {
        frame.setFloat(stackSlots[slot], value);
    }

    private void putLong(VirtualFrame frame, int slot, long value) {
        frame.setObject(stackSlots[slot], StaticObject.NULL);
        frame.setLong(stackSlots[slot + 1], value);
    }

    private void putDouble(VirtualFrame frame, int slot, double value) {
        frame.setObject(stackSlots[slot], StaticObject.NULL);
        frame.setDouble(stackSlots[slot + 1], value);
    }

    @Override
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    public Object execute(VirtualFrame frame) {
        int curBCI = 0;
        int top = 0;

        initArguments(frame);

        loop: while (true) {
            int curOpcode = -1;
            int nextBCI = -1;
            try {
                // bytecodesExecuted.inc();
                curOpcode = bs.currentBC(curBCI);
                CompilerAsserts.partialEvaluationConstant(curBCI);
                CompilerAsserts.partialEvaluationConstant(curOpcode);
                // @formatter:off
                // Checkstyle: stop
                exit_switch: switch (curOpcode) {
                    case NOP         : break;
                    case ACONST_NULL : putObject(frame, top, StaticObject.NULL); break;

                    case ICONST_M1   : // fall through
                    case ICONST_0    : // fall through
                    case ICONST_1    : // fall through
                    case ICONST_2    : // fall through
                    case ICONST_3    : // fall through
                    case ICONST_4    : // fall through
                    case ICONST_5    : putInt(frame, top, curOpcode - ICONST_0); break;

                    case LCONST_0    : // fall through
                    case LCONST_1    : putLong(frame, top, curOpcode - LCONST_0); break;

                    case FCONST_0    : // fall through
                    case FCONST_1    : // fall through
                    case FCONST_2    : putFloat(frame, top,curOpcode - FCONST_0); break;

                    case DCONST_0    : // fall through
                    case DCONST_1    : putDouble(frame, top,curOpcode - DCONST_0); break;

                    case BIPUSH      : putInt(frame, top, bs.readByte(curBCI)); break;
                    case SIPUSH      : putInt(frame, top, bs.readShort(curBCI)); break;
                    case LDC         : // fall through
                    case LDC_W       : // fall through
                    case LDC2_W      : putPoolConstant(frame, top, bs.readCPI(curBCI), curOpcode); break;

                    case ILOAD       : putInt(frame, top, FrameUtil.getIntSafe(frame, locals[bs.readLocalIndex(curBCI)])); break;
                    case LLOAD       : putLong(frame, top, FrameUtil.getLongSafe(frame, locals[bs.readLocalIndex(curBCI)])); break;
                    case FLOAD       : putFloat(frame, top, FrameUtil.getFloatSafe(frame, locals[bs.readLocalIndex(curBCI)])); break;
                    case DLOAD       : putDouble(frame, top, FrameUtil.getDoubleSafe(frame, locals[bs.readLocalIndex(curBCI)])); break;
                    case ALOAD       : putObject(frame, top, (StaticObject) FrameUtil.getObjectSafe(frame, locals[bs.readLocalIndex(curBCI)])); break;

                    case ILOAD_0     : // fall through
                    case ILOAD_1     : // fall through
                    case ILOAD_2     : // fall through
                    case ILOAD_3     : putInt(frame, top, FrameUtil.getIntSafe(frame, locals[curOpcode - ILOAD_0])); break;
                    case LLOAD_0     : // fall through
                    case LLOAD_1     : // fall through
                    case LLOAD_2     : // fall through
                    case LLOAD_3     : putLong(frame, top, FrameUtil.getLongSafe(frame, locals[curOpcode - LLOAD_0])); break;
                    case FLOAD_0     : // fall through
                    case FLOAD_1     : // fall through
                    case FLOAD_2     : // fall through
                    case FLOAD_3     : putFloat(frame, top, FrameUtil.getFloatSafe(frame, locals[curOpcode - FLOAD_0])); break;
                    case DLOAD_0     : // fall through
                    case DLOAD_1     : // fall through
                    case DLOAD_2     : // fall through
                    case DLOAD_3     : putDouble(frame, top, FrameUtil.getDoubleSafe(frame, locals[curOpcode - DLOAD_0])); break;
                    case ALOAD_0     : // fall through
                    case ALOAD_1     : // fall through
                    case ALOAD_2     : // fall through
                    case ALOAD_3     : putObject(frame, top, (StaticObject) FrameUtil.getObjectSafe(frame, locals[curOpcode - ALOAD_0])); break;

                    case IALOAD      : putInt(frame, top - 2, vm.getArrayInt(peekInt(frame, top - 1), nullCheck(peekObject(frame, top - 2)))); break;
                    case LALOAD      : putLong(frame, top - 2, vm.getArrayLong(peekInt(frame, top - 1), nullCheck(peekObject(frame, top - 2)))); break;
                    case FALOAD      : putFloat(frame, top - 2, vm.getArrayFloat(peekInt(frame, top - 1), nullCheck(peekObject(frame, top - 2)))); break;
                    case DALOAD      : putDouble(frame, top - 2, vm.getArrayDouble(peekInt(frame, top - 1), nullCheck(peekObject(frame, top - 2)))); break;
                    case AALOAD      : putObject(frame, top - 2, vm.getArrayObject(peekInt(frame, top - 1), nullCheck(peekObject(frame, top - 2)))); break;
                    case BALOAD      : putInt(frame, top - 2, vm.getArrayByte(peekInt(frame, top - 1), nullCheck(peekObject(frame, top - 2)))); break;
                    case CALOAD      : putInt(frame, top - 2, vm.getArrayChar(peekInt(frame, top - 1), nullCheck(peekObject(frame, top - 2)))); break;
                    case SALOAD      : putInt(frame, top - 2, vm.getArrayShort(peekInt(frame, top - 1), nullCheck(peekObject(frame, top - 2)))); break;

                    case ISTORE      : frame.setInt(locals[bs.readLocalIndex(curBCI)], peekInt(frame, top - 1)); break;
                    case LSTORE      : frame.setLong(locals[bs.readLocalIndex(curBCI)], peekLong(frame, top - 1)); break;
                    case FSTORE      : frame.setFloat(locals[bs.readLocalIndex(curBCI)], peekFloat(frame, top - 1)); break;
                    case DSTORE      : frame.setDouble(locals[bs.readLocalIndex(curBCI)], peekDouble(frame, top - 1)); break;
                    case ASTORE      : frame.setObject(locals[bs.readLocalIndex(curBCI)], peekReturnAddressOrObject(frame, top - 1)); break;

                    case ISTORE_0    : // fall through
                    case ISTORE_1    : // fall through
                    case ISTORE_2    : // fall through
                    case ISTORE_3    : frame.setInt(locals[curOpcode - ISTORE_0], peekInt(frame, top - 1)); break;
                    case LSTORE_0    : // fall through
                    case LSTORE_1    : // fall through
                    case LSTORE_2    : // fall through
                    case LSTORE_3    : frame.setLong(locals[curOpcode - LSTORE_0], peekLong(frame, top - 1)); break;
                    case FSTORE_0    : // fall through
                    case FSTORE_1    : // fall through
                    case FSTORE_2    : // fall through
                    case FSTORE_3    : frame.setFloat(locals[curOpcode - FSTORE_0], peekFloat(frame, top - 1)); break;
                    case DSTORE_0    : // fall through
                    case DSTORE_1    : // fall through
                    case DSTORE_2    : // fall through
                    case DSTORE_3    : frame.setDouble(locals[curOpcode - DSTORE_0], peekDouble(frame, top - 1)); break;
                    case ASTORE_0    : // fall through
                    case ASTORE_1    : // fall through
                    case ASTORE_2    : // fall through
                    case ASTORE_3    : frame.setObject(locals[curOpcode - ASTORE_0], peekReturnAddressOrObject(frame, top - 1)); break;

                    case IASTORE     : vm.setArrayInt(peekInt(frame, top - 1), peekInt(frame, top - 2), nullCheck(peekObject(frame, top - 3))); break;
                    case LASTORE     : vm.setArrayLong(peekLong(frame, top - 1), peekInt(frame, top - 3), nullCheck(peekObject(frame, top - 4))); break;
                    case FASTORE     : vm.setArrayFloat(peekFloat(frame, top - 1), peekInt(frame, top - 2), nullCheck(peekObject(frame, top - 3))); break;
                    case DASTORE     : vm.setArrayDouble(peekDouble(frame, top - 1), peekInt(frame, top - 3), nullCheck(peekObject(frame, top - 4))); break;
                    case AASTORE     : vm.setArrayObject(peekObject(frame, top - 1), peekInt(frame, top - 2), (StaticObjectArray) nullCheck(peekObject(frame, top - 3))); break;
                    case BASTORE     : vm.setArrayByte((byte) peekInt(frame, top - 1), peekInt(frame, top - 2), nullCheck(peekObject(frame, top - 3))); break;
                    case CASTORE     : vm.setArrayChar((char) peekInt(frame, top - 1), peekInt(frame, top - 2), nullCheck(peekObject(frame, top - 3))); break;
                    case SASTORE     : vm.setArrayShort((short) peekInt(frame, top - 1), peekInt(frame, top - 2), nullCheck(peekObject(frame, top - 3))); break;

                    case POP         : // fall through
                    case POP2        : break;

                    // TODO(peterssen): Stack shuffling is expensive.
                    case DUP         : dup1(frame, top); break;
                    case DUP_X1      : dupx1(frame, top); break;
                    case DUP_X2      : dupx2(frame, top); break;
                    case DUP2        : dup2(frame, top); break;
                    case DUP2_X1     : dup2x1(frame, top); break;
                    case DUP2_X2     : dup2x2(frame, top); break;
                    case SWAP        : swapSingle(frame, top); break;

                    case IADD        : putInt(frame, top - 2, peekInt(frame, top - 1) + peekInt(frame, top - 2)); break;
                    case LADD        : putLong(frame, top - 4, peekLong(frame, top - 1) + peekLong(frame, top - 3)); break;
                    case FADD        : putFloat(frame, top - 2, peekFloat(frame, top - 1) + peekFloat(frame, top - 2)); break;
                    case DADD        : putDouble(frame, top - 4, peekDouble(frame, top - 1) + peekDouble(frame, top - 3)); break;

                    case ISUB        : putInt(frame, top - 2, -peekInt(frame, top - 1) + peekInt(frame, top - 2)); break;
                    case LSUB        : putLong(frame, top - 4,-peekLong(frame, top - 1) + peekLong(frame, top - 3)); break;
                    case FSUB        : putFloat(frame, top - 2,-peekFloat(frame, top - 1) + peekFloat(frame, top - 2)); break;
                    case DSUB        : putDouble(frame, top - 4, -peekDouble(frame, top - 1) + peekDouble(frame, top - 3)); break;

                    case IMUL        : putInt(frame,top - 2,peekInt(frame, top - 1) * peekInt(frame, top - 2)); break;
                    case LMUL        : putLong(frame, top - 4, peekLong(frame, top - 1) * peekLong(frame, top - 3)); break;
                    case FMUL        : putFloat(frame, top - 2,peekFloat(frame, top - 1) * peekFloat(frame, top - 2)); break;
                    case DMUL        : putDouble(frame,top - 4, peekDouble(frame, top - 1) * peekDouble(frame, top - 3)); break;

                    case IDIV        : putInt(frame, top - 2, divInt(checkNonZero(peekInt(frame, top - 1)), peekInt(frame, top - 2))); break;
                    case LDIV        : putLong(frame,top - 4, divLong(checkNonZero(peekLong(frame, top - 1)), peekLong(frame, top - 3))); break;
                    case FDIV        : putFloat(frame, top - 2, divFloat(peekFloat(frame, top - 1), peekFloat(frame, top - 2))); break;
                    case DDIV        : putDouble(frame,top - 4, divDouble(peekDouble(frame, top - 1), peekDouble(frame, top - 3))); break;

                    case IREM        : putInt(frame, top - 2, remInt(checkNonZero(peekInt(frame, top - 1)), peekInt(frame, top - 2))); break;
                    case LREM        : putLong(frame, top - 4,remLong(checkNonZero(peekLong(frame, top - 1)), peekLong(frame, top - 3))); break;
                    case FREM        : putFloat(frame, top - 2, remFloat(peekFloat(frame, top - 1), peekFloat(frame, top - 2))); break;
                    case DREM        : putDouble(frame, top - 4, remDouble(peekDouble(frame, top - 1), peekDouble(frame, top - 3))); break;

                    case INEG        : putInt(frame, top - 1, -peekInt(frame, top - 1)); break;
                    case LNEG        : putLong(frame, top - 2, -peekLong(frame, top - 1)); break;
                    case FNEG        : putFloat(frame, top - 1, -peekFloat(frame, top - 1)); break;
                    case DNEG        : putDouble(frame, top - 2, -peekDouble(frame, top - 1)); break;

                    case ISHL        : putInt(frame, top - 2, shiftLeftInt(peekInt(frame, top - 1), peekInt(frame, top - 2))); break;
                    case LSHL        : putLong(frame, top - 3, shiftLeftLong(peekInt(frame, top - 1), peekLong(frame, top - 2))); break;
                    case ISHR        : putInt(frame, top - 2, shiftRightSignedInt(peekInt(frame, top - 1), peekInt(frame, top - 2))); break;
                    case LSHR        : putLong(frame, top - 3, shiftRightSignedLong(peekInt(frame, top - 1), peekLong(frame, top - 2))); break;
                    case IUSHR       : putInt(frame, top - 2, shiftRightUnsignedInt(peekInt(frame, top - 1), peekInt(frame, top - 2))); break;
                    case LUSHR       : putLong(frame, top - 3, shiftRightUnsignedLong(peekInt(frame, top - 1), peekLong(frame, top - 2))); break;

                    case IAND        : putInt(frame, top - 2, peekInt(frame, top - 1) & peekInt(frame, top - 2)); break;
                    case LAND        : putLong(frame, top - 4, peekLong(frame, top - 1) & peekLong(frame, top - 3)); break;

                    case IOR         : putInt(frame, top - 2,peekInt(frame, top - 1) | peekInt(frame, top - 2)); break;
                    case LOR         : putLong(frame, top - 4, peekLong(frame, top - 1) | peekLong(frame, top - 3)); break;

                    case IXOR        : putInt(frame, top - 2, peekInt(frame, top - 1) ^ peekInt(frame, top - 2)); break;
                    case LXOR        : putLong(frame, top - 4, peekLong(frame, top - 1) ^ peekLong(frame, top - 3)); break;

                    case IINC        : frame.setInt(locals[bs.readLocalIndex(curBCI)], FrameUtil.getIntSafe(frame, locals[bs.readLocalIndex(curBCI)]) + bs.readIncrement(curBCI)); break;

                    case I2L         : putLong(frame, top - 1, peekInt(frame, top - 1)); break;
                    case I2F         : putFloat(frame, top - 1, peekInt(frame, top - 1)); break;
                    case I2D         : putDouble(frame, top - 1, peekInt(frame, top - 1)); break;

                    case L2I         : putInt(frame, top - 2, (int) peekLong(frame, top - 1)); break;
                    case L2F         : putFloat(frame, top - 2, peekLong(frame, top - 1)); break;
                    case L2D         : putDouble(frame, top - 2, peekLong(frame, top - 1)); break;

                    case F2I         : putInt(frame, top - 1, (int) peekFloat(frame, top - 1)); break;
                    case F2L         : putLong(frame, top - 1, (long) peekFloat(frame, top - 1)); break;
                    case F2D         : putDouble(frame, top - 1, peekFloat(frame, top - 1)); break;

                    case D2I         : putInt(frame, top - 2, (int) peekDouble(frame, top - 1)); break;
                    case D2L         : putLong(frame, top - 2, (long) peekDouble(frame, top - 1)); break;
                    case D2F         : putFloat(frame, top - 2, (float) peekDouble(frame, top - 1)); break;

                    case I2B         : putInt(frame, top - 1, (byte) peekInt(frame, top - 1)); break;
                    case I2C         : putInt(frame, top - 1, (char) peekInt(frame, top - 1)); break;
                    case I2S         : putInt(frame, top - 1, (short) peekInt(frame, top - 1)); break;

                    case LCMP        : putInt(frame, top - 4, compareLong(peekLong(frame, top - 1), peekLong(frame, top - 3))); break;
                    case FCMPL       : putInt(frame, top - 2, compareFloatLess(peekFloat(frame, top - 1), peekFloat(frame, top - 2))); break;
                    case FCMPG       : putInt(frame, top - 2, compareFloatGreater(peekFloat(frame, top - 1), peekFloat(frame, top - 2))); break;
                    case DCMPL       : putInt(frame, top - 4, compareDoubleLess(peekDouble(frame, top - 1), peekDouble(frame, top - 3))); break;
                    case DCMPG       : putInt(frame, top - 4, compareDoubleGreater(peekDouble(frame, top - 1), peekDouble(frame, top - 3))); break;

                    case IFEQ        : if (peekInt(frame, top - 1) == 0) { nextBCI = bs.readBranchDest(curBCI); } break;
                    case IFNE        : if (peekInt(frame, top - 1) != 0) { nextBCI = bs.readBranchDest(curBCI); } break;
                    case IFLT        : if (peekInt(frame, top - 1)  < 0) { nextBCI = bs.readBranchDest(curBCI); } break;
                    case IFGE        : if (peekInt(frame, top - 1) >= 0) { nextBCI = bs.readBranchDest(curBCI); } break;
                    case IFGT        : if (peekInt(frame, top - 1)  > 0) { nextBCI = bs.readBranchDest(curBCI); } break;
                    case IFLE        : if (peekInt(frame, top - 1) <= 0) { nextBCI = bs.readBranchDest(curBCI); } break;

                    case IF_ICMPEQ   : if (peekInt(frame, top - 1) == peekInt(frame, top - 2)) { nextBCI = bs.readBranchDest(curBCI); } break;
                    case IF_ICMPNE   : if (peekInt(frame, top - 1) != peekInt(frame, top - 2)) { nextBCI = bs.readBranchDest(curBCI); } break;
                    case IF_ICMPLT   : if (peekInt(frame, top - 1)  > peekInt(frame, top - 2)) { nextBCI = bs.readBranchDest(curBCI); } break;
                    case IF_ICMPGE   : if (peekInt(frame, top - 1) <= peekInt(frame, top - 2)) { nextBCI = bs.readBranchDest(curBCI); } break;
                    case IF_ICMPGT   : if (peekInt(frame, top - 1)  < peekInt(frame, top - 2)) { nextBCI = bs.readBranchDest(curBCI); } break;
                    case IF_ICMPLE   : if (peekInt(frame, top - 1) >= peekInt(frame, top - 2)) { nextBCI = bs.readBranchDest(curBCI); } break;

                    case IF_ACMPEQ   : if (peekObject(frame, top - 1) == peekObject(frame, top - 2)) { nextBCI = bs.readBranchDest(curBCI); } break;
                    case IF_ACMPNE   : if (peekObject(frame, top - 1) != peekObject(frame, top - 2)) { nextBCI = bs.readBranchDest(curBCI); } break;

                    case GOTO        : // fall through
                    case GOTO_W      : nextBCI = bs.readBranchDest(curBCI); break;

                    case JSR         : // fall through
                    case JSR_W       :
                        CompilerDirectives.bailout("JSR/RET bytecodes not supported");
                        putReturnAddress(frame, top, bs.nextBCI(curBCI));
                        nextBCI = bs.readBranchDest(curBCI);
                        break;
                    case RET         :
                        CompilerDirectives.bailout("JSR/RET bytecodes not supported");
                        nextBCI = ((ReturnAddress) FrameUtil.getObjectSafe(frame, locals[bs.readLocalIndex(curBCI)])).getBci();
                        break;

                    // @formatter:on
                    // Checkstyle: resume
                    case TABLESWITCH: {
                        int index = peekInt(frame, top - 1);
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
                                nextBCI = switchHelper.targetAt(curBCI, index - low);
                            } else {
                                nextBCI = switchHelper.defaultTarget(curBCI);
                            }
                            break exit_switch; // break
                        }

                        for (int i = low; i <= high; ++i) {
                            if (i == index) {
                                CompilerAsserts.partialEvaluationConstant(i);
                                CompilerAsserts.partialEvaluationConstant(i - low);
                                CompilerAsserts.partialEvaluationConstant(switchHelper.targetAt(curBCI, i - low));
                                nextBCI = switchHelper.targetAt(curBCI, i - low);
                                break exit_switch; // break
                            }
                        }

                        CompilerAsserts.partialEvaluationConstant(switchHelper.defaultTarget(curBCI));
                        nextBCI = switchHelper.defaultTarget(curBCI);
                        break;
                    }
                    case LOOKUPSWITCH: {
                        int key = peekInt(frame, top - 1);
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
                                nextBCI = curBCI + switchHelper.offsetAt(curBCI, mid); // key found.
                                break exit_switch; // break
                            }
                        }
                        nextBCI = switchHelper.defaultTarget(curBCI); // key not found.
                        break;
                    }
                    // @formatter:off
                    // Checkstyle: stop
                    case IRETURN               : return exitMethodAndReturn(peekInt(frame, top - 1));
                    case LRETURN               : return exitMethodAndReturnObject(peekLong(frame, top - 1));
                    case FRETURN               : return exitMethodAndReturnObject(peekFloat(frame, top - 1));
                    case DRETURN               : return exitMethodAndReturnObject(peekDouble(frame, top - 1));
                    case ARETURN               : return exitMethodAndReturnObject(peekObject(frame, top - 1));
                    case RETURN                : return exitMethodAndReturn();

                    // TODO(peterssen): Order shuffled.
                    case GETSTATIC             : // fall through
                    case GETFIELD              : top += getField(frame, top, resolveField(curOpcode, bs.readCPI(curBCI)), curOpcode); break;
                    case PUTSTATIC             : // fall through
                    case PUTFIELD              : top += putField(frame, top, resolveField(curOpcode, bs.readCPI(curBCI)), curOpcode); break;

                    // TODO(peterssen): De-duplicate quickening logic. Single QUICK(nodeIndex) bytecode?.
                    case INVOKEVIRTUAL         : top += 1 + quickenAndCallInvokeVirtual(frame, top, curBCI, resolveMethod(curOpcode, bs.readCPI(curBCI))); break;
                    case INVOKESPECIAL         : top += 1 + quickenAndCallInvokeSpecial(frame, top, curBCI, resolveMethod(curOpcode, bs.readCPI(curBCI))); break;
                    case INVOKESTATIC          : top += quickenAndCallInvokeStatic(frame, top, curBCI, resolveMethod(curOpcode, bs.readCPI(curBCI))); break;
                    case INVOKEINTERFACE       : top += 1 + quickenAndCallInvokeInterface(frame, top, curBCI, resolveMethod(curOpcode, bs.readCPI(curBCI))); break;

                    case NEW                   : putObject(frame, top, allocateInstance(resolveType(curOpcode, bs.readCPI(curBCI)))); break;
                    case NEWARRAY              : putObject(frame, top - 1, InterpreterToVM.allocatePrimitiveArray(bs.readByte(curBCI), peekInt(frame, top - 1))); break;
                    case ANEWARRAY             : putObject(frame, top - 1, allocateArray(resolveType(curOpcode, bs.readCPI(curBCI)), peekInt(frame, top - 1))); break;
                    case ARRAYLENGTH           : putInt(frame, top - 1, vm.arrayLength(nullCheck(peekObject(frame, top - 1)))); break;

                    case ATHROW                : CompilerDirectives.transferToInterpreter(); throw new EspressoException(nullCheck(peekObject(frame, top - 1)));

                    case CHECKCAST             : putObject(frame, top - 1, checkCast(peekObject(frame, top - 1), resolveType(curOpcode, bs.readCPI(curBCI)))); break;
                    case INSTANCEOF            : putInt(frame, top - 1, instanceOf(peekObject(frame, top - 1), resolveType(curOpcode, bs.readCPI(curBCI))) ? 1  : 0); break;

                    case MONITORENTER          : vm.monitorEnter(nullCheck(peekObject(frame, top - 1))); break;
                    case MONITOREXIT           : vm.monitorExit(nullCheck(peekObject(frame, top - 1))); break;

                    case WIDE                  : throw EspressoError.shouldNotReachHere("BytecodeStream.currentBC() should never return this bytecode.");
                    case MULTIANEWARRAY        : top += allocateMultiArray(frame, top, resolveType(curOpcode, bs.readCPI(curBCI)), bs.readUByte(curBCI + 3)); break;

                    case IFNULL                : if (StaticObject.isNull(peekObject(frame, top - 1)))  { nextBCI = bs.readBranchDest(curBCI); } break;
                    case IFNONNULL             : if (StaticObject.notNull(peekObject(frame, top - 1))) { nextBCI = bs.readBranchDest(curBCI); } break;

                    case BREAKPOINT            : throw EspressoError.unimplemented(Bytecodes.nameOf(curOpcode) + " not supported.");
                    case INVOKEDYNAMIC         : throw EspressoError.unimplemented(Bytecodes.nameOf(curOpcode) + " not supported.");

                    case QUICK_INVOKESPECIAL   : // fall through
                    case QUICK_INVOKESTATIC    : // fall through
                    case QUICK_INVOKEVIRTUAL   : // fall through
                    case QUICK_INVOKEINTERFACE : top += nodes[bs.readCPI(curBCI)].invoke(frame, top); break;
                    default                    : throw EspressoError.shouldNotReachHere(Bytecodes.nameOf(curOpcode));
                }
                // @formatter:on
                // Checkstyle: resume
            } catch (EspressoException e) {
                CompilerDirectives.transferToInterpreter();
                ExceptionHandler handler = resolveExceptionHandlers(curBCI, e.getException());
                if (handler != null) {
                    top = 0;
                    putObject(frame, 0, e.getException());
                    top++;
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
                    top = 0;
                    putObject(frame, 0, ex);
                    top++;
                    curBCI = handler.getHandlerBCI();
                    continue loop; // skip bs.next()
                } else {
                    throw new EspressoException(ex);
                }
            }
            top += Bytecodes.stackEffectOf(curOpcode);
            assert nextBCI < 0 || (curOpcode == TABLESWITCH || curOpcode == LOOKUPSWITCH || Bytecodes.isBranch(curOpcode)) : "illegal branching";
            // This is very nice property to have (seems to always hold for javac), but is not in
            // the spec. Should bailout.
            // nextBCI < curBCI => empty operand stack
            assert nextBCI < 0 || (nextBCI >= curBCI || top == 0) : "back-edge with non-empty operand stack";

            if (nextBCI < 0) {
                nextBCI = bs.next(curBCI);
            }

            if (nextBCI < curBCI) {
                if (top != 0) {
                    CompilerDirectives.bailout("back-edge with non-empty operand stack");
                }
                top = 0;
            }

            curBCI = nextBCI;
        }
    }

    private JavaKind peekKind(VirtualFrame frame, int slot) {
        if (frame.isObject(stackSlots[slot])) {
            return JavaKind.Object;
        }
        if (frame.isInt(stackSlots[slot])) {
            return JavaKind.Int;
        }
        if (frame.isFloat(stackSlots[slot])) {
            return JavaKind.Float;
        }
        if (frame.isDouble(stackSlots[slot])) {
            return JavaKind.Double;
        }
        if (frame.isLong(stackSlots[slot])) {
            return JavaKind.Long;
        }
        throw EspressoError.shouldNotReachHere();
    }

    private void dup1(VirtualFrame frame, int top) {
        // value1 -> value1, value1
        JavaKind k1 = peekKind(frame, top - 1);
        assert k1.getSlotCount() == 1;
        Object v1 = peekValue(frame, top - 1);
        putKindUnsafe1(frame, top, v1, k1);
    }

    private void dupx1(VirtualFrame frame, int top) {
        // value2, value1 -> value1, value2, value1
        JavaKind k1 = peekKind(frame, top - 1);
        JavaKind k2 = peekKind(frame, top - 2);
        assert k1.getSlotCount() == 1;
        assert k2.getSlotCount() == 1;
        Object v1 = peekValue(frame, top - 1);
        Object v2 = peekValue(frame, top - 2);
        putKindUnsafe1(frame, top - 2, v1, k1);
        putKindUnsafe1(frame, top - 1, v2, k2);
        putKindUnsafe1(frame, top, v1, k1);
    }

    private void dupx2(VirtualFrame frame, int top) {
        // value3, value2, value1 -> value1, value3, value2, value1
        JavaKind k1 = peekKind(frame, top - 1);
        JavaKind k2 = peekKind(frame, top - 2);
        JavaKind k3 = peekKind(frame, top - 3);
        Object v1 = peekValue(frame, top - 1);
        Object v2 = peekValue(frame, top - 2);
        Object v3 = peekValue(frame, top - 3);
        putKindUnsafe1(frame, top - 3, v1, k1);
        putKindUnsafe1(frame, top - 2, v3, k3);
        putKindUnsafe1(frame, top - 1, v2, k2);
        putKindUnsafe1(frame, top, v1, k1);
    }

    private void dup2(VirtualFrame frame, int top) {
        // {value2, value1} -> {value2, value1}, {value2, value1}
        JavaKind k1 = peekKind(frame, top - 1);
        JavaKind k2 = peekKind(frame, top - 2);
        Object v1 = peekValue(frame, top - 1);
        Object v2 = peekValue(frame, top - 2);
        putKindUnsafe1(frame, top, v2, k2);
        putKindUnsafe1(frame, top + 1, v1, k1);
    }

    private void swapSingle(VirtualFrame frame, int top) {
        // value2, value1 -> value1, value2
        JavaKind k1 = peekKind(frame, top - 1);
        JavaKind k2 = peekKind(frame, top - 2);
        Object v1 = peekValue(frame, top - 1);
        Object v2 = peekValue(frame, top - 2);
        putKindUnsafe1(frame, top - 1, v2, k2);
        putKindUnsafe1(frame, top - 2, v1, k1);
    }

    private void dup2x1(VirtualFrame frame, int top) {
        // value3, {value2, value1} -> {value2, value1}, value3, {value2, value1}
        JavaKind k1 = peekKind(frame, top - 1);
        JavaKind k2 = peekKind(frame, top - 2);
        JavaKind k3 = peekKind(frame, top - 3);
        Object v1 = peekValue(frame, top - 1);
        Object v2 = peekValue(frame, top - 2);
        Object v3 = peekValue(frame, top - 3);
        putKindUnsafe1(frame, top - 3, v2, k2);
        putKindUnsafe1(frame, top - 2, v1, k1);
        putKindUnsafe1(frame, top - 1, v3, k3);
        putKindUnsafe1(frame, top - 0, v2, k2);
        putKindUnsafe1(frame, top + 1, v1, k1);
    }

    private void dup2x2(VirtualFrame frame, int top) {
        // {value4, value3}, {value2, value1} -> {value2, value1}, {value4, value3}, {value2,
        // value1}
        JavaKind k1 = peekKind(frame, top - 1);
        JavaKind k2 = peekKind(frame, top - 2);
        JavaKind k3 = peekKind(frame, top - 3);
        JavaKind k4 = peekKind(frame, top - 4);
        Object v1 = peekValue(frame, top - 1);
        Object v2 = peekValue(frame, top - 2);
        Object v3 = peekValue(frame, top - 3);
        Object v4 = peekValue(frame, top - 4);
        putKindUnsafe1(frame, top - 3, v2, k2);
        putKindUnsafe1(frame, top - 2, v1, k1);
        putKindUnsafe1(frame, top - 1, v4, k4);
        putKindUnsafe1(frame, top - 0, v3, k3);
        putKindUnsafe1(frame, top + 1, v2, k2);
        putKindUnsafe1(frame, top + 2, v1, k1);
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

    private void putPoolConstant(final VirtualFrame frame, int top, char cpi, int opcode) {
        assert opcode == LDC || opcode == LDC_W || opcode == LDC2_W;
        ConstantPool pool = getConstantPool();
        PoolConstant constant = pool.at(cpi);
        if (constant instanceof IntegerConstant) {
            assert opcode == LDC || opcode == LDC_W;
            putInt(frame, top, ((IntegerConstant) constant).value());
        } else if (constant instanceof LongConstant) {
            assert opcode == LDC2_W;
            putLong(frame, top, ((LongConstant) constant).value());
        } else if (constant instanceof DoubleConstant) {
            assert opcode == LDC2_W;
            putDouble(frame, top, ((DoubleConstant) constant).value());
        } else if (constant instanceof FloatConstant) {
            assert opcode == LDC || opcode == LDC_W;
            putFloat(frame, top, ((FloatConstant) constant).value());
        } else if (constant instanceof StringConstant) {
            assert opcode == LDC || opcode == LDC_W;
            // TODO(peterssen): Must be interned once, on creation.
            putObject(frame, top, ((StringConstant) constant).intern(pool));
        } else if (constant instanceof ClassConstant) {
            assert opcode == LDC || opcode == LDC_W;
            Klass klass = ((ClassConstant) constant).resolve(pool, cpi);
            putObject(frame, top, klass.mirror());
        } else {
            throw EspressoError.unimplemented(constant.toString());
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

    private int quickenAndCallInvokeStatic(final VirtualFrame frame, int top, int curBCI, MethodInfo resolvedMethod) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        int nodeIndex = addInvokeNode(new InvokeStaticNode(resolvedMethod));
        patchBci(curBCI, (byte) QUICK_INVOKESTATIC, (char) nodeIndex);
        return nodes[nodeIndex].invoke(frame, top);
    }

    private int quickenAndCallInvokeSpecial(final VirtualFrame frame, int top, int curBCI, MethodInfo resolvedMethod) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        int nodeIndex = addInvokeNode(new InvokeSpecialNode(resolvedMethod));
        patchBci(curBCI, (byte) QUICK_INVOKESPECIAL, (char) nodeIndex);
        return nodes[nodeIndex].invoke(frame, top);
    }

    private int quickenAndCallInvokeInterface(final VirtualFrame frame, int top, int curBCI, MethodInfo resolutionSeed) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        int nodeIndex = addInvokeNode(InvokeInterfaceNodeGen.create(resolutionSeed));
        patchBci(curBCI, (byte) QUICK_INVOKEINTERFACE, (char) nodeIndex);
        return nodes[nodeIndex].invoke(frame, top);
    }

    // TODO(peterssen): Remove duplicated methods.
    private int quickenAndCallInvokeVirtual(final VirtualFrame frame, int top, int curBCI, MethodInfo resolutionSeed) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (resolutionSeed.isFinal() || resolutionSeed.getDeclaringClass().isFinalFlagSet()) {
            return quickenAndCallInvokeSpecial(frame, top, curBCI, resolutionSeed);
        }
        int nodeIndex = addInvokeNode(InvokeVirtualNodeGen.create(resolutionSeed));
        patchBci(curBCI, (byte) QUICK_INVOKEVIRTUAL, (char) nodeIndex);
        return nodes[nodeIndex].invoke(frame, top);
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

    @ExplodeLoop
    private int allocateMultiArray(final VirtualFrame frame, int top, Klass klass, int allocatedDimensions) {
        assert klass.isArray();
        CompilerAsserts.partialEvaluationConstant(allocatedDimensions);
        CompilerAsserts.partialEvaluationConstant(klass);
        int[] dimensions = new int[allocatedDimensions];
        for (int i = 0; i < allocatedDimensions; ++i) {
            dimensions[i] = peekInt(frame, top - allocatedDimensions + i);
        }
        putObject(frame, top - allocatedDimensions, vm.newMultiArray(klass, dimensions));
        return -allocatedDimensions; // Does not include the created (pushed) array.
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

    private int putField(final VirtualFrame frame, int top, FieldInfo field, int opcode) {
        assert opcode == PUTFIELD || opcode == PUTSTATIC;
        assert field.isStatic() == (opcode == PUTSTATIC);
        StaticObject receiver = field.isStatic()
                        ? field.getDeclaringClass().tryInitializeAndGetStatics()
                        : nullCheck(peekObject(frame, top - field.getKind().getSlotCount() - 1)); // -receiver
        // @formatter:off
        // Checkstyle: stop
        switch (field.getKind()) {
            case Boolean : vm.setFieldBoolean(peekInt(frame, top - 1) == 1, receiver, field);  break;
            case Byte    : vm.setFieldByte((byte) peekInt(frame, top - 1), receiver, field);   break;
            case Char    : vm.setFieldChar((char) peekInt(frame, top - 1), receiver, field);   break;
            case Short   : vm.setFieldShort((short) peekInt(frame, top - 1), receiver, field); break;
            case Int     : vm.setFieldInt(peekInt(frame, top - 1), receiver, field);           break;
            case Double  : vm.setFieldDouble(peekDouble(frame, top - 1), receiver, field);     break;
            case Float   : vm.setFieldFloat(peekFloat(frame, top - 1), receiver, field);       break;
            case Long    : vm.setFieldLong(peekLong(frame, top - 1), receiver, field);         break;
            case Object  : vm.setFieldObject(peekObject(frame, top - 1), receiver, field);     break;
            default      : throw EspressoError.shouldNotReachHere("unexpected kind");
        }
        // @formatter:on
        // Checkstyle: resume
        /**
         * Returns the stack effect (slot delta) that cannot be inferred solely from the bytecode.
         * e.g. GETFIELD always pops the receiver, but the (read) result size (1 or 2) is unknown.
         * 
         * <pre>
         *   top += putField(frame, top, resolveField(...)); break; // stack effect that depends on the field
         *   top += Bytecodes.stackEffectOf(curOpcode); // stack effect that depends solely on PUTFIELD.
         *   // at this point `top` must have the correct value.
         *   curBCI = bs.next(curBCI);
         * </pre>
         */
        return -field.getKind().getSlotCount();
    }

    private int getField(final VirtualFrame frame, int top, FieldInfo field, int opcode) {
        assert opcode == GETFIELD || opcode == GETSTATIC;
        assert field.isStatic() == (opcode == GETSTATIC);
        CompilerAsserts.partialEvaluationConstant(field);

        StaticObject receiver = field.isStatic()
                        ? field.getDeclaringClass().tryInitializeAndGetStatics()
                        : nullCheck(peekObject(frame, top - 1));

        int resultAt = field.isStatic() ? top : (top - 1);
        // @formatter:off
        // Checkstyle: stop
        switch (field.getKind()) {
            case Boolean : putInt(frame, resultAt, vm.getFieldBoolean(receiver, field) ? 1 : 0); break;
            case Byte    : putInt(frame, resultAt, vm.getFieldByte(receiver, field));      break;
            case Char    : putInt(frame, resultAt, vm.getFieldChar(receiver, field));      break;
            case Short   : putInt(frame, resultAt, vm.getFieldShort(receiver, field));     break;
            case Int     : putInt(frame, resultAt, vm.getFieldInt(receiver, field));       break;
            case Double  : putDouble(frame, resultAt, vm.getFieldDouble(receiver, field)); break;
            case Float   : putFloat(frame, resultAt, vm.getFieldFloat(receiver, field));   break;
            case Long    : putLong(frame, resultAt, vm.getFieldLong(receiver, field));     break;
            case Object  : putObject(frame, resultAt, vm.getFieldObject(receiver, field)); break;
            default      : throw EspressoError.shouldNotReachHere("unexpected kind");
        }
        // @formatter:on
        // Checkstyle: resume
        /**
         * Returns the stack effect (slot delta) that cannot be inferred solely from the bytecode.
         * e.g. PUTFIELD always pops the receiver, but the result size (1 or 2) is unknown.
         *
         * <pre>
         *   top += getField(frame, top, resolveField(...)); break; // stack effect that depends on the field
         *   top += Bytecodes.stackEffectOf(curOpcode); // stack effect that depends solely on GETFIELD.
         *   // at this point `top` must have the correct value.
         *   curBCI = bs.next(curBCI);
         * </pre>
         */
        return field.getKind().getSlotCount();
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

    @ExplodeLoop
    public Object[] peekArguments(VirtualFrame frame, int top, boolean hasReceiver, SignatureDescriptor signature) {
        int argCount = signature.getParameterCount(false);

        int extraParam = hasReceiver ? 1 : 0;
        final Object[] args = new Object[argCount + extraParam];

        CompilerAsserts.partialEvaluationConstant(argCount);
        CompilerAsserts.partialEvaluationConstant(signature);
        CompilerAsserts.partialEvaluationConstant(hasReceiver);

        int argAt = top - 1;
        for (int i = argCount - 1; i >= 0; --i) {
            JavaKind kind = signature.getParameterKind(i);
            // @formatter:off
            // Checkstyle: stop
            switch (kind) {
                case Boolean : args[i + extraParam] = (peekInt(frame, argAt) != 0);  break;
                case Byte    : args[i + extraParam] = (byte) peekInt(frame, argAt);  break;
                case Short   : args[i + extraParam] = (short) peekInt(frame, argAt); break;
                case Char    : args[i + extraParam] = (char) peekInt(frame, argAt);  break;
                case Int     : args[i + extraParam] = peekInt(frame, argAt);         break;
                case Float   : args[i + extraParam] = peekFloat(frame, argAt);       break;
                case Long    : args[i + extraParam] = peekLong(frame, argAt);        break;
                case Double  : args[i + extraParam] = peekDouble(frame, argAt);      break;
                case Object  : args[i + extraParam] = peekObject(frame, argAt);      break;
                default      : throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
            // Checkstyle: resume
            argAt -= kind.getSlotCount();
        }
        if (hasReceiver) {
            args[0] = peekObject(frame, argAt);
        }
        return args;
    }

    /**
     * Puts a value in the operand stack. This method follows the JVM spec, where sub-word types (<
     * int) are always treated as int.
     *
     * Returns the number of used slots.
     *
     * @param value value to push
     * @param kind kind to push
     */
    public int putKind(VirtualFrame frame, int top, Object value, JavaKind kind) {
        // @formatter:off
        // Checkstyle: stop
        switch (kind) {
            case Boolean : putInt(frame, top, ((boolean) value) ? 1 : 0); break;
            case Byte    : putInt(frame, top, (byte) value);              break;
            case Short   : putInt(frame, top, (short) value);             break;
            case Char    : putInt(frame, top, (char) value);              break;
            case Int     : putInt(frame, top, (int) value);               break;
            case Float   : putFloat(frame, top, (float) value);           break;
            case Long    : putLong(frame, top, (long) value);             break;
            case Double  : putDouble(frame, top, (double) value);         break;
            case Object  : putObject(frame, top, (StaticObject) value);   break;
            case Void    : /* ignore */                                   break;
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
        return kind.getSlotCount();
    }

    // internal
    private void putKindUnsafe1(VirtualFrame frame, int slot, Object value, JavaKind kind) {
        // @formatter:off
        // Checkstyle: stop
        switch (kind) {
            case Int     : putInt(frame, slot, (int) value);                break;
            case Float   : putFloat(frame, slot, (float) value);            break;
            case Long    : putLong(frame, slot - 1, (long) value);     break;
            case Double  : putDouble(frame, slot - 1, (double) value); break;
            case Object  : putObject(frame, slot, (StaticObject) value);    break;
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
    }

    public StaticObject peekReceiver(final VirtualFrame frame, int top, MethodInfo m) {
        assert !m.isStatic();
        int skipSlots = m.getSignature().getNumberOfSlotsForParameters();
        return peekObject(frame, top - skipSlots - 1);
    }
}
