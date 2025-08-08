/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.interpreter;

import static com.oracle.svm.interpreter.EspressoFrame.clear;
import static com.oracle.svm.interpreter.EspressoFrame.dup1;
import static com.oracle.svm.interpreter.EspressoFrame.dup2;
import static com.oracle.svm.interpreter.EspressoFrame.dup2x1;
import static com.oracle.svm.interpreter.EspressoFrame.dup2x2;
import static com.oracle.svm.interpreter.EspressoFrame.dupx1;
import static com.oracle.svm.interpreter.EspressoFrame.dupx2;
import static com.oracle.svm.interpreter.EspressoFrame.getLocalDouble;
import static com.oracle.svm.interpreter.EspressoFrame.getLocalFloat;
import static com.oracle.svm.interpreter.EspressoFrame.getLocalInt;
import static com.oracle.svm.interpreter.EspressoFrame.getLocalLong;
import static com.oracle.svm.interpreter.EspressoFrame.getLocalObject;
import static com.oracle.svm.interpreter.EspressoFrame.getLocalReturnAddress;
import static com.oracle.svm.interpreter.EspressoFrame.peekObject;
import static com.oracle.svm.interpreter.EspressoFrame.popDouble;
import static com.oracle.svm.interpreter.EspressoFrame.popFloat;
import static com.oracle.svm.interpreter.EspressoFrame.popInt;
import static com.oracle.svm.interpreter.EspressoFrame.popLong;
import static com.oracle.svm.interpreter.EspressoFrame.popObject;
import static com.oracle.svm.interpreter.EspressoFrame.popReturnAddressOrObject;
import static com.oracle.svm.interpreter.EspressoFrame.putDouble;
import static com.oracle.svm.interpreter.EspressoFrame.putFloat;
import static com.oracle.svm.interpreter.EspressoFrame.putInt;
import static com.oracle.svm.interpreter.EspressoFrame.putLong;
import static com.oracle.svm.interpreter.EspressoFrame.putObject;
import static com.oracle.svm.interpreter.EspressoFrame.putReturnAddress;
import static com.oracle.svm.interpreter.EspressoFrame.setLocalDouble;
import static com.oracle.svm.interpreter.EspressoFrame.setLocalFloat;
import static com.oracle.svm.interpreter.EspressoFrame.setLocalInt;
import static com.oracle.svm.interpreter.EspressoFrame.setLocalLong;
import static com.oracle.svm.interpreter.EspressoFrame.setLocalObject;
import static com.oracle.svm.interpreter.EspressoFrame.setLocalObjectOrReturnAddress;
import static com.oracle.svm.interpreter.EspressoFrame.startingStackOffset;
import static com.oracle.svm.interpreter.EspressoFrame.swapSingle;
import static com.oracle.svm.interpreter.InterpreterToVM.nullCheck;
import static com.oracle.svm.interpreter.InterpreterUtil.traceInterpreter;
import static com.oracle.svm.interpreter.metadata.Bytecodes.AALOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.AASTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ACONST_NULL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ALOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ALOAD_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ALOAD_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ALOAD_2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ALOAD_3;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ANEWARRAY;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ARETURN;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ARRAYLENGTH;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ASTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ASTORE_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ASTORE_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ASTORE_2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ASTORE_3;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ATHROW;
import static com.oracle.svm.interpreter.metadata.Bytecodes.BALOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.BASTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.BIPUSH;
import static com.oracle.svm.interpreter.metadata.Bytecodes.BREAKPOINT;
import static com.oracle.svm.interpreter.metadata.Bytecodes.CALOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.CASTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.CHECKCAST;
import static com.oracle.svm.interpreter.metadata.Bytecodes.D2F;
import static com.oracle.svm.interpreter.metadata.Bytecodes.D2I;
import static com.oracle.svm.interpreter.metadata.Bytecodes.D2L;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DADD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DALOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DASTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DCMPG;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DCMPL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DCONST_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DCONST_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DDIV;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DLOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DLOAD_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DLOAD_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DLOAD_2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DLOAD_3;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DMUL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DNEG;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DREM;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DRETURN;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DSTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DSTORE_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DSTORE_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DSTORE_2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DSTORE_3;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DSUB;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DUP;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DUP2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DUP2_X1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DUP2_X2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DUP_X1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.DUP_X2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.F2D;
import static com.oracle.svm.interpreter.metadata.Bytecodes.F2I;
import static com.oracle.svm.interpreter.metadata.Bytecodes.F2L;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FADD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FALOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FASTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FCMPG;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FCMPL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FCONST_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FCONST_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FCONST_2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FDIV;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FLOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FLOAD_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FLOAD_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FLOAD_2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FLOAD_3;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FMUL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FNEG;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FREM;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FRETURN;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FSTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FSTORE_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FSTORE_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FSTORE_2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FSTORE_3;
import static com.oracle.svm.interpreter.metadata.Bytecodes.FSUB;
import static com.oracle.svm.interpreter.metadata.Bytecodes.GETFIELD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.GETSTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.GOTO;
import static com.oracle.svm.interpreter.metadata.Bytecodes.GOTO_W;
import static com.oracle.svm.interpreter.metadata.Bytecodes.I2B;
import static com.oracle.svm.interpreter.metadata.Bytecodes.I2C;
import static com.oracle.svm.interpreter.metadata.Bytecodes.I2D;
import static com.oracle.svm.interpreter.metadata.Bytecodes.I2F;
import static com.oracle.svm.interpreter.metadata.Bytecodes.I2L;
import static com.oracle.svm.interpreter.metadata.Bytecodes.I2S;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IADD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IALOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IAND;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IASTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ICONST_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ICONST_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ICONST_2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ICONST_3;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ICONST_4;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ICONST_5;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ICONST_M1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IDIV;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IFEQ;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IFGE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IFGT;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IFLE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IFLT;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IFNE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IFNONNULL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IFNULL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IF_ACMPEQ;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IF_ACMPNE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IF_ICMPEQ;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IF_ICMPGE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IF_ICMPGT;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IF_ICMPLE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IF_ICMPLT;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IF_ICMPNE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IINC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ILOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ILOAD_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ILOAD_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ILOAD_2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ILOAD_3;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IMUL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INEG;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INSTANCEOF;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEINTERFACE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKESPECIAL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKESTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IOR;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IREM;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IRETURN;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ISHL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ISHR;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ISTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ISTORE_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ISTORE_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ISTORE_2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ISTORE_3;
import static com.oracle.svm.interpreter.metadata.Bytecodes.ISUB;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IUSHR;
import static com.oracle.svm.interpreter.metadata.Bytecodes.IXOR;
import static com.oracle.svm.interpreter.metadata.Bytecodes.JSR;
import static com.oracle.svm.interpreter.metadata.Bytecodes.JSR_W;
import static com.oracle.svm.interpreter.metadata.Bytecodes.L2D;
import static com.oracle.svm.interpreter.metadata.Bytecodes.L2F;
import static com.oracle.svm.interpreter.metadata.Bytecodes.L2I;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LADD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LALOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LAND;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LASTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LCMP;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LCONST_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LCONST_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDC2_W;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDC_W;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDIV;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LLOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LLOAD_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LLOAD_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LLOAD_2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LLOAD_3;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LMUL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LNEG;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LOOKUPSWITCH;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LOR;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LREM;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LRETURN;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LSHL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LSHR;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LSTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LSTORE_0;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LSTORE_1;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LSTORE_2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LSTORE_3;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LSUB;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LUSHR;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LXOR;
import static com.oracle.svm.interpreter.metadata.Bytecodes.MONITORENTER;
import static com.oracle.svm.interpreter.metadata.Bytecodes.MONITOREXIT;
import static com.oracle.svm.interpreter.metadata.Bytecodes.MULTIANEWARRAY;
import static com.oracle.svm.interpreter.metadata.Bytecodes.NEW;
import static com.oracle.svm.interpreter.metadata.Bytecodes.NEWARRAY;
import static com.oracle.svm.interpreter.metadata.Bytecodes.NOP;
import static com.oracle.svm.interpreter.metadata.Bytecodes.POP;
import static com.oracle.svm.interpreter.metadata.Bytecodes.POP2;
import static com.oracle.svm.interpreter.metadata.Bytecodes.PUTFIELD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.PUTSTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.RET;
import static com.oracle.svm.interpreter.metadata.Bytecodes.RETURN;
import static com.oracle.svm.interpreter.metadata.Bytecodes.SALOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.SASTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.SIPUSH;
import static com.oracle.svm.interpreter.metadata.Bytecodes.SWAP;
import static com.oracle.svm.interpreter.metadata.Bytecodes.TABLESWITCH;
import static com.oracle.svm.interpreter.metadata.Bytecodes.WIDE;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ConstantPool;
import com.oracle.svm.interpreter.debug.DebuggerEvents;
import com.oracle.svm.interpreter.debug.EventKind;
import com.oracle.svm.interpreter.debug.SteppingControl;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.Bytecodes;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;
import com.oracle.svm.interpreter.metadata.LookupSwitch;
import com.oracle.svm.interpreter.metadata.MetadataUtil;
import com.oracle.svm.interpreter.metadata.ReferenceConstant;
import com.oracle.svm.interpreter.metadata.TableSwitch;
import com.oracle.svm.interpreter.metadata.UnsupportedResolutionException;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Bytecode interpreter loop.
 */
@InternalVMMethod
public final class Interpreter {
    static final String FAILURE_CONSTANT_NOT_PART_OF_IMAGE_HEAP = "Trying to load constant that is not part of the Native Image heap";

    private Interpreter() {
        throw VMError.shouldNotReachHere("private constructor");
    }

    private static void initArguments(InterpreterFrame frame, InterpreterResolvedJavaMethod method) {
        Object[] arguments = frame.getArguments();

        boolean hasReceiver = !method.isStatic();
        int receiverSlot = hasReceiver ? 1 : 0;
        int curSlot = 0;
        if (hasReceiver) {
            assert arguments[0] != null : "null receiver in init arguments !";
            Object receiver = arguments[0];
            setLocalObject(frame, curSlot, receiver);
            curSlot += JavaKind.Object.getSlotCount();
        }

        InterpreterUnresolvedSignature methodSignature = method.getSignature();
        for (int i = 0; i < methodSignature.getParameterCount(false); ++i) {
            JavaKind argType = methodSignature.getParameterKind(i);
            // @formatter:off
            switch (argType) {
                case Boolean: setLocalInt(frame, curSlot, ((boolean) arguments[i + receiverSlot]) ? 1 : 0); break;
                case Byte:    setLocalInt(frame, curSlot, ((byte) arguments[i + receiverSlot]));            break;
                case Short:   setLocalInt(frame, curSlot, ((short) arguments[i + receiverSlot]));           break;
                case Char:    setLocalInt(frame, curSlot, ((char) arguments[i + receiverSlot]));            break;
                case Int:     setLocalInt(frame, curSlot, (int) arguments[i + receiverSlot]);               break;
                case Float:   setLocalFloat(frame, curSlot, (float) arguments[i + receiverSlot]);           break;
                case Long:    setLocalLong(frame, curSlot, (long) arguments[i + receiverSlot]);     ++curSlot; break;
                case Double:  setLocalDouble(frame, curSlot, (double) arguments[i + receiverSlot]); ++curSlot; break;
                case Object:
                    // Reference type.
                    Object argument = arguments[i + receiverSlot];
                    setLocalObject(frame, curSlot, argument);
                    break;
                default :
                    throw VMError.shouldNotReachHereAtRuntime();
            }
            // @formatter:on
            ++curSlot;
        }
    }

    public static void initializeFrame(InterpreterFrame frame, InterpreterResolvedJavaMethod method) {
        initArguments(frame, method);
    }

    public static Object execute(InterpreterResolvedJavaMethod method, Object[] args) {
        return execute(method, args, false);
    }

    public static Object execute(InterpreterResolvedJavaMethod method, InterpreterFrame frame) {
        return execute0(method, frame, false);
    }

    public static Object execute(InterpreterResolvedJavaMethod method, Object[] args, boolean forceStayInInterpreter) {
        InterpreterFrame frame = EspressoFrame.allocate(method.getMaxLocals(), method.getMaxStackSize(), args);

        InterpreterUtil.guarantee(!method.isNative(), "trying to interpret native method %s", method);

        initializeFrame(frame, method);
        return execute0(method, frame, forceStayInInterpreter);
    }

    private static Object execute0(InterpreterResolvedJavaMethod method, InterpreterFrame frame, boolean stayInInterpreter) {
        try {
            if (method.isSynchronized()) {
                Object lockTarget = method.isStatic()
                                ? method.getDeclaringClass().getJavaClass()
                                : EspressoFrame.getThis(frame);
                assert lockTarget != null;
                InterpreterToVM.monitorEnter(frame, nullCheck(lockTarget));
            }
            int startTop = startingStackOffset(method.getMaxLocals());
            return Root.executeBodyFromBCI(frame, method, 0, startTop, stayInInterpreter);
        } finally {
            InterpreterToVM.releaseInterpreterFrameLocks(frame);
        }
    }

    public static final ThreadLocal<Integer> logIndent = ThreadLocal.withInitial(() -> 0);

    private static int getLogIndent() {
        if (InterpreterOptions.InterpreterTraceSupport.getValue()) {
            return logIndent.get();
        }
        return 0;
    }

    private static void setLogIndent(int indent) {
        if (InterpreterOptions.InterpreterTraceSupport.getValue()) {
            logIndent.set(indent);
        }
    }

    private static void traceInterpreterEnter(InterpreterResolvedJavaMethod method, int indent, int curBCI, int top) {
        /* arguments to Log methods might have side-effects */
        if (!InterpreterOptions.InterpreterTraceSupport.getValue()) {
            return;
        }

        setLogIndent(indent + 2);
        traceInterpreter(" ".repeat(indent)) //
                        .string("[interp] Entered ") //
                        .string(method.getDeclaringClass().getName()) //
                        .string("::") //
                        .string(method.getName()) //
                        .string(method.getSignature().toMethodDescriptor()) //
                        .string(" with bci=").unsigned(curBCI) //
                        .string("/top=").unsigned(top).newline();
    }

    private static void traceInterpreterReturn(InterpreterResolvedJavaMethod method, int indent, int curBCI, int top) {
        /* arguments to Log methods might have side-effects */
        if (!InterpreterOptions.InterpreterTraceSupport.getValue()) {
            return;
        }

        setLogIndent(indent);
        traceInterpreter(" ".repeat(indent));
        traceInterpreter("[interp] Leave ") //
                        .string(method.getDeclaringClass().getName()) //
                        .string("::") //
                        .string(method.getName()) //
                        .string(method.getSignature().toMethodDescriptor()) //
                        .string(" with bci=").unsigned(curBCI) //
                        .string("/top=").unsigned(top).newline();
    }

    private static void traceInterpreterInstruction(InterpreterFrame frame, int indent, int curBCI, int top, int curOpcode) {
        /* arguments to Log methods might have side-effects */
        if (!InterpreterOptions.InterpreterTraceSupport.getValue()) {
            return;
        }

        traceInterpreter(" ".repeat(indent)) //
                        .string("bci=").unsigned(curBCI).string(" ") //
                        .string(Bytecodes.nameOf(curOpcode));
        for (int slot = top - 1; slot >= 0; slot--) {
            traceInterpreter(", s").unsigned(slot).string("=").hex(frame.getLongStatic(slot)).string("/").object(frame.getObjectStatic(slot));
        }
        traceInterpreter("").newline();
    }

    private static void traceInterpreterException(InterpreterResolvedJavaMethod method, int indent, int curBCI, int top) {
        /* arguments to Log methods might have side-effects */
        if (!InterpreterOptions.InterpreterTraceSupport.getValue()) {
            return;
        }

        setLogIndent(indent);
        traceInterpreter(" ".repeat(indent)) //
                        .string("[interp] Exception ") //
                        .string(method.getDeclaringClass().getName()) //
                        .string("::") //
                        .string(method.getName()) //
                        .string(method.getSignature().toMethodDescriptor()) //
                        .string(" with bci=").unsigned(curBCI) //
                        .string("/top=").unsigned(top).newline();
    }

    public static final class Root {

        private static Object executeBodyFromBCI(InterpreterFrame frame, InterpreterResolvedJavaMethod method, int startBCI, int startTop,
                        boolean forceStayInInterpreter) {
            int curBCI = startBCI;
            int top = startTop;
            byte[] code = method.getInterpretedCode();

            assert method.isStatic() || EspressoFrame.getThis(frame) != null;

            InterpreterUtil.guarantee(code != null, "no bytecode stream for %s", method);

            int indent = getLogIndent();
            traceInterpreterEnter(method, indent, curBCI, top);

            int debuggerEventFlags = 0;
            if (DebuggerEvents.singleton().isEventEnabled(Thread.currentThread(), EventKind.METHOD_ENTRY)) {
                if (method.getDeclaringClass().isMethodEnterEvent()) {
                    debuggerEventFlags |= EventKind.METHOD_ENTRY.getFlag();
                }
            }

            loop: while (true) {
                /*
                 * Opaque read ensuring that BREAKPOINT opcodes are eventually read. Opaque == plain
                 * on x86/64, but on other architectures the read must be eventually guaranteed.
                 */
                int curOpcode = BytecodeStream.opaqueOpcode(code, curBCI);

                if (DebuggerEvents.singleton().isEventEnabled(Thread.currentThread(), EventKind.SINGLE_STEP)) {
                    // Check that stepping "depth" and "size" are respected.
                    Thread currentThread = Thread.currentThread();
                    SteppingControl steppingControl = DebuggerEvents.singleton().getSteppingControl(currentThread);
                    if (steppingControl != null && steppingControl.isActiveAtCurrentFrameDepth()) {
                        int stepSize = steppingControl.getSize();
                        if (stepSize == SteppingControl.STEP_MIN ||
                                        (stepSize == SteppingControl.STEP_LINE && !steppingControl.withinSameLine(method, curBCI))) {
                            debuggerEventFlags |= EventKind.SINGLE_STEP.getFlag();
                        }
                    }
                }

                if (curOpcode == BREAKPOINT) {
                    if (DebuggerEvents.singleton().isEventEnabled(Thread.currentThread(), EventKind.BREAKPOINT)) {
                        debuggerEventFlags |= EventKind.BREAKPOINT.getFlag();
                    }
                    curOpcode = method.getOriginalOpcodeAt(curBCI);
                }
                if (debuggerEventFlags != 0) {
                    // We have possibly: method enter, step before statement/expression, breakpoint
                    DebuggerEvents.singleton().getEventHandler().onEventAt(Thread.currentThread(), method, curBCI, null, debuggerEventFlags);
                    debuggerEventFlags = 0;
                }

                try {
                    traceInterpreterInstruction(frame, indent, curBCI, top, curOpcode);

                    // @formatter:off
                    switch (curOpcode) {
                        case NOP: break;
                        case ACONST_NULL: putObject(frame, top, null); break;

                        case ICONST_M1: // fall through
                        case ICONST_0: // fall through
                        case ICONST_1: // fall through
                        case ICONST_2: // fall through
                        case ICONST_3: // fall through
                        case ICONST_4: // fall through
                        case ICONST_5: putInt(frame, top, curOpcode - ICONST_0); break;

                        case LCONST_0: // fall through
                        case LCONST_1: putLong(frame, top, curOpcode - LCONST_0); break;

                        case FCONST_0: // fall through
                        case FCONST_1: // fall through
                        case FCONST_2: putFloat(frame, top, curOpcode - FCONST_0); break;

                        case DCONST_0: // fall through
                        case DCONST_1: putDouble(frame, top, curOpcode - DCONST_0); break;

                        case BIPUSH: putInt(frame, top, BytecodeStream.readByte(code, curBCI)); break;
                        case SIPUSH: putInt(frame, top, BytecodeStream.readShort(code, curBCI)); break;

                        case LDC   : loadConstant(frame, method, top, BytecodeStream.readCPI1(code, curBCI), curOpcode); break;
                        case LDC_W : // fall through
                        case LDC2_W: loadConstant(frame, method, top, BytecodeStream.readCPI2(code, curBCI), curOpcode); break;

                        case ILOAD: putInt(frame, top, getLocalInt(frame, BytecodeStream.readLocalIndex1(code, curBCI))); break;
                        case LLOAD: putLong(frame, top, getLocalLong(frame, BytecodeStream.readLocalIndex1(code, curBCI))); break;
                        case FLOAD: putFloat(frame, top, getLocalFloat(frame, BytecodeStream.readLocalIndex1(code, curBCI))); break;
                        case DLOAD: putDouble(frame, top, getLocalDouble(frame, BytecodeStream.readLocalIndex1(code, curBCI))); break;
                        case ALOAD: putObject(frame, top, getLocalObject(frame, BytecodeStream.readLocalIndex1(code, curBCI))); break;

                        case ILOAD_0: // fall through
                        case ILOAD_1: // fall through
                        case ILOAD_2: // fall through
                        case ILOAD_3: putInt(frame, top, getLocalInt(frame, curOpcode - ILOAD_0)); break;

                        case LLOAD_0: // fall through
                        case LLOAD_1: // fall through
                        case LLOAD_2: // fall through
                        case LLOAD_3: putLong(frame, top, getLocalLong(frame, curOpcode - LLOAD_0)); break;

                        case FLOAD_0: // fall through
                        case FLOAD_1: // fall through
                        case FLOAD_2: // fall through
                        case FLOAD_3: putFloat(frame, top, getLocalFloat(frame, curOpcode - FLOAD_0)); break;

                        case DLOAD_0: // fall through
                        case DLOAD_1: // fall through
                        case DLOAD_2: // fall through
                        case DLOAD_3: putDouble(frame, top, getLocalDouble(frame, curOpcode - DLOAD_0)); break;

                        case ALOAD_0: putObject(frame, top, getLocalObject(frame, 0)); break;
                        case ALOAD_1: // fall through
                        case ALOAD_2: // fall through
                        case ALOAD_3: putObject(frame, top, getLocalObject(frame, curOpcode - ALOAD_0)); break;

                        case IALOAD: // fall through
                        case LALOAD: // fall through
                        case FALOAD: // fall through
                        case DALOAD: // fall through
                        case BALOAD: // fall through
                        case CALOAD: // fall through
                        case SALOAD: // fall through
                        case AALOAD: arrayLoad(frame, top, curOpcode); break;

                        case ISTORE: setLocalInt(frame, BytecodeStream.readLocalIndex1(code, curBCI), popInt(frame, top - 1)); break;
                        case LSTORE: setLocalLong(frame, BytecodeStream.readLocalIndex1(code, curBCI), popLong(frame, top - 1)); break;
                        case FSTORE: setLocalFloat(frame, BytecodeStream.readLocalIndex1(code, curBCI), popFloat(frame, top - 1)); break;
                        case DSTORE: setLocalDouble(frame, BytecodeStream.readLocalIndex1(code, curBCI), popDouble(frame, top - 1)); break;
                        case ASTORE: setLocalObjectOrReturnAddress(frame, BytecodeStream.readLocalIndex1(code, curBCI), popReturnAddressOrObject(frame, top - 1)); break;

                        case ISTORE_0: // fall through
                        case ISTORE_1: // fall through
                        case ISTORE_2: // fall through
                        case ISTORE_3: setLocalInt(frame, curOpcode - ISTORE_0, popInt(frame, top - 1)); break;

                        case LSTORE_0: // fall through
                        case LSTORE_1: // fall through
                        case LSTORE_2: // fall through
                        case LSTORE_3: setLocalLong(frame, curOpcode - LSTORE_0, popLong(frame, top - 1)); break;

                        case FSTORE_0: // fall through
                        case FSTORE_1: // fall through
                        case FSTORE_2: // fall through
                        case FSTORE_3: setLocalFloat(frame, curOpcode - FSTORE_0, popFloat(frame, top - 1)); break;

                        case DSTORE_0: // fall through
                        case DSTORE_1: // fall through
                        case DSTORE_2: // fall through
                        case DSTORE_3: setLocalDouble(frame, curOpcode - DSTORE_0, popDouble(frame, top - 1)); break;

                        case ASTORE_0: // fall through
                        case ASTORE_1: // fall through
                        case ASTORE_2: // fall through
                        case ASTORE_3: setLocalObjectOrReturnAddress(frame, curOpcode - ASTORE_0, popReturnAddressOrObject(frame, top - 1)); break;

                        case IASTORE: // fall through
                        case LASTORE: // fall through
                        case FASTORE: // fall through
                        case DASTORE: // fall through
                        case AASTORE: // fall through
                        case BASTORE: // fall through
                        case CASTORE: // fall through
                        case SASTORE: arrayStore(frame, top, curOpcode); break;

                        case POP2:
                            clear(frame, top - 1);
                            clear(frame, top - 2);
                            break;

                        case POP: clear(frame, top - 1); break;

                        case DUP     : dup1(frame, top);       break;
                        case DUP_X1  : dupx1(frame, top);      break;
                        case DUP_X2  : dupx2(frame, top);      break;
                        case DUP2    : dup2(frame, top);       break;
                        case DUP2_X1 : dup2x1(frame, top);     break;
                        case DUP2_X2 : dup2x2(frame, top);     break;
                        case SWAP    : swapSingle(frame, top); break;

                        case IADD: putInt(frame, top - 2, popInt(frame, top - 1) + popInt(frame, top - 2)); break;
                        case LADD: putLong(frame, top - 4, popLong(frame, top - 1) + popLong(frame, top - 3)); break;
                        case FADD: putFloat(frame, top - 2, popFloat(frame, top - 1) + popFloat(frame, top - 2)); break;
                        case DADD: putDouble(frame, top - 4, popDouble(frame, top - 1) + popDouble(frame, top - 3)); break;

                        case ISUB: putInt(frame, top - 2, popInt(frame, top - 2) - popInt(frame, top - 1)); break;
                        case LSUB: putLong(frame, top - 4, popLong(frame, top - 3) - popLong(frame, top - 1)); break;
                        case FSUB: putFloat(frame, top - 2, popFloat(frame, top - 2) - popFloat(frame, top - 1)); break;
                        case DSUB: putDouble(frame, top - 4, popDouble(frame, top - 3) - popDouble(frame, top - 1)); break;

                        case IMUL: putInt(frame, top - 2, popInt(frame, top - 1) * popInt(frame, top - 2)); break;
                        case LMUL: putLong(frame, top - 4, popLong(frame, top - 1) * popLong(frame, top - 3)); break;
                        case FMUL: putFloat(frame, top - 2, popFloat(frame, top - 1) * popFloat(frame, top - 2)); break;
                        case DMUL: putDouble(frame, top - 4, popDouble(frame, top - 1) * popDouble(frame, top - 3)); break;

                        case IDIV: putInt(frame, top - 2, divInt(popInt(frame, top - 1), popInt(frame, top - 2))); break;
                        case LDIV: putLong(frame, top - 4, divLong(popLong(frame, top - 1), popLong(frame, top - 3))); break;
                        case FDIV: putFloat(frame, top - 2, divFloat(popFloat(frame, top - 1), popFloat(frame, top - 2))); break;
                        case DDIV: putDouble(frame, top - 4, divDouble(popDouble(frame, top - 1), popDouble(frame, top - 3))); break;

                        case IREM: putInt(frame, top - 2, remInt(popInt(frame, top - 1), popInt(frame, top - 2))); break;
                        case LREM: putLong(frame, top - 4, remLong(popLong(frame, top - 1), popLong(frame, top - 3))); break;
                        case FREM: putFloat(frame, top - 2, remFloat(popFloat(frame, top - 1), popFloat(frame, top - 2))); break;
                        case DREM: putDouble(frame, top - 4, remDouble(popDouble(frame, top - 1), popDouble(frame, top - 3))); break;

                        case INEG: putInt(frame, top - 1, -popInt(frame, top - 1)); break;
                        case LNEG: putLong(frame, top - 2, -popLong(frame, top - 1)); break;
                        case FNEG: putFloat(frame, top - 1, -popFloat(frame, top - 1)); break;
                        case DNEG: putDouble(frame, top - 2, -popDouble(frame, top - 1)); break;

                        case ISHL: putInt(frame, top - 2, shiftLeftInt(popInt(frame, top - 1), popInt(frame, top - 2))); break;
                        case LSHL: putLong(frame, top - 3, shiftLeftLong(popInt(frame, top - 1), popLong(frame, top - 2))); break;
                        case ISHR: putInt(frame, top - 2, shiftRightSignedInt(popInt(frame, top - 1), popInt(frame, top - 2))); break;
                        case LSHR: putLong(frame, top - 3, shiftRightSignedLong(popInt(frame, top - 1), popLong(frame, top - 2))); break;
                        case IUSHR: putInt(frame, top - 2, shiftRightUnsignedInt(popInt(frame, top - 1), popInt(frame, top - 2))); break;
                        case LUSHR: putLong(frame, top - 3, shiftRightUnsignedLong(popInt(frame, top - 1), popLong(frame, top - 2))); break;

                        case IAND: putInt(frame, top - 2, popInt(frame, top - 1) & popInt(frame, top - 2)); break;
                        case LAND: putLong(frame, top - 4, popLong(frame, top - 1) & popLong(frame, top - 3)); break;

                        case IOR: putInt(frame, top - 2, popInt(frame, top - 1) | popInt(frame, top - 2)); break;
                        case LOR: putLong(frame, top - 4, popLong(frame, top - 1) | popLong(frame, top - 3)); break;

                        case IXOR: putInt(frame, top - 2, popInt(frame, top - 1) ^ popInt(frame, top - 2)); break;
                        case LXOR: putLong(frame, top - 4, popLong(frame, top - 1) ^ popLong(frame, top - 3)); break;

                        case IINC:
                            setLocalInt(frame, BytecodeStream.readLocalIndex1(code, curBCI), getLocalInt(frame, BytecodeStream.readLocalIndex1(code, curBCI)) + BytecodeStream.readIncrement1(code, curBCI));
                            break;

                        case I2L: putLong(frame, top - 1, popInt(frame, top - 1)); break;
                        case I2F: putFloat(frame, top - 1, popInt(frame, top - 1)); break;
                        case I2D: putDouble(frame, top - 1, popInt(frame, top - 1)); break;

                        case L2I: putInt(frame, top - 2, (int) popLong(frame, top - 1)); break;
                        case L2F: putFloat(frame, top - 2, popLong(frame, top - 1)); break;
                        case L2D: putDouble(frame, top - 2, popLong(frame, top - 1)); break;

                        case F2I: putInt(frame, top - 1, (int) popFloat(frame, top - 1)); break;
                        case F2L: putLong(frame, top - 1, (long) popFloat(frame, top - 1)); break;
                        case F2D: putDouble(frame, top - 1, popFloat(frame, top - 1)); break;

                        case D2I: putInt(frame, top - 2, (int) popDouble(frame, top - 1)); break;
                        case D2L: putLong(frame, top - 2, (long) popDouble(frame, top - 1)); break;
                        case D2F: putFloat(frame, top - 2, (float) popDouble(frame, top - 1)); break;

                        case I2B: putInt(frame, top - 1, (byte) popInt(frame, top - 1)); break;
                        case I2C: putInt(frame, top - 1, (char) popInt(frame, top - 1)); break;
                        case I2S: putInt(frame, top - 1, (short) popInt(frame, top - 1)); break;

                        case LCMP : putInt(frame, top - 4, compareLong(popLong(frame, top - 1), popLong(frame, top - 3))); break;
                        case FCMPL: putInt(frame, top - 2, compareFloatLess(popFloat(frame, top - 1), popFloat(frame, top - 2))); break;
                        case FCMPG: putInt(frame, top - 2, compareFloatGreater(popFloat(frame, top - 1), popFloat(frame, top - 2))); break;
                        case DCMPL: putInt(frame, top - 4, compareDoubleLess(popDouble(frame, top - 1), popDouble(frame, top - 3))); break;
                        case DCMPG: putInt(frame, top - 4, compareDoubleGreater(popDouble(frame, top - 1), popDouble(frame, top - 3))); break;

                        // @formatter:on
                        case IFEQ: // fall through
                        case IFNE: // fall through
                        case IFLT: // fall through
                        case IFGE: // fall through
                        case IFGT: // fall through
                        case IFLE:
                            if (takeBranchPrimitive1(popInt(frame, top - 1), curOpcode)) {
                                top += ConstantBytecodes.stackEffectOf(IFLE);
                                curBCI = beforeJumpChecks(frame, curBCI, BytecodeStream.readBranchDest2(code, curBCI), top);
                                continue loop;
                            }
                            break;

                        case IF_ICMPEQ: // fall through
                        case IF_ICMPNE: // fall through
                        case IF_ICMPLT: // fall through
                        case IF_ICMPGE: // fall through
                        case IF_ICMPGT: // fall through
                        case IF_ICMPLE:
                            if (takeBranchPrimitive2(popInt(frame, top - 1), popInt(frame, top - 2), curOpcode)) {
                                top += ConstantBytecodes.stackEffectOf(IF_ICMPLE);
                                curBCI = beforeJumpChecks(frame, curBCI, BytecodeStream.readBranchDest2(code, curBCI), top);
                                continue loop;
                            }
                            break;

                        case IF_ACMPEQ: // fall through
                        case IF_ACMPNE:
                            if (takeBranchRef2(popObject(frame, top - 1), popObject(frame, top - 2), curOpcode)) {
                                top += ConstantBytecodes.stackEffectOf(IF_ACMPNE);
                                curBCI = beforeJumpChecks(frame, curBCI, BytecodeStream.readBranchDest2(code, curBCI), top);
                                continue loop;
                            }
                            break;

                        case IFNULL: // fall through
                        case IFNONNULL:
                            if (takeBranchRef1(popObject(frame, top - 1), curOpcode)) {
                                top += ConstantBytecodes.stackEffectOf(IFNONNULL);
                                curBCI = beforeJumpChecks(frame, curBCI, BytecodeStream.readBranchDest2(code, curBCI), top);
                                continue loop;
                            }
                            break;

                        case GOTO:
                            curBCI = beforeJumpChecks(frame, curBCI, BytecodeStream.readBranchDest2(code, curBCI), top);
                            continue loop;

                        case GOTO_W:
                            curBCI = beforeJumpChecks(frame, curBCI, BytecodeStream.readBranchDest4(code, curBCI), top);
                            continue loop;

                        case JSR: {
                            putReturnAddress(frame, top, curBCI + ConstantBytecodes.lengthOf(JSR));
                            // The JSR stack effect is incorrectly set to 0 in the compiler sources.
                            // To keep interpreter and compiler in sync, the correct stack effect is
                            // hardcoded here.
                            int stackEffect = 1; // Bytecodes.stackEffectOf(JSR)
                            top += stackEffect;
                            curBCI = beforeJumpChecks(frame, curBCI, BytecodeStream.readBranchDest2(code, curBCI), top);
                            continue loop;
                        }
                        case JSR_W: {
                            putReturnAddress(frame, top, curBCI + ConstantBytecodes.lengthOf(JSR_W));
                            // The JSR_W stack effect is incorrectly set to 0 in the compiler
                            // sources. To keep interpreter and compiler in sync, the correct stack
                            // effect is hardcoded here.
                            int stackEffect = 1; // Bytecodes.stackEffectOf(JSR_W)
                            top += stackEffect;
                            curBCI = beforeJumpChecks(frame, curBCI, BytecodeStream.readBranchDest4(code, curBCI), top);
                            continue loop;
                        }
                        case RET: {
                            top += ConstantBytecodes.stackEffectOf(RET);
                            curBCI = beforeJumpChecks(frame, curBCI, getLocalReturnAddress(frame, BytecodeStream.readLocalIndex1(code, curBCI)), top);
                            continue loop;
                        }

                        case TABLESWITCH: {
                            int index = popInt(frame, top - 1);
                            int low = TableSwitch.lowKey(code, curBCI);
                            int high = TableSwitch.highKey(code, curBCI);
                            assert low <= high;

                            int targetBCI;
                            if (low <= index && index <= high) {
                                targetBCI = TableSwitch.targetAt(code, curBCI, index - low);
                            } else {
                                targetBCI = TableSwitch.defaultTarget(code, curBCI);
                            }
                            top += ConstantBytecodes.stackEffectOf(TABLESWITCH);
                            curBCI = beforeJumpChecks(frame, curBCI, targetBCI, top);
                            continue loop;
                        }
                        case LOOKUPSWITCH: {
                            int key = popInt(frame, top - 1);
                            int low = 0;
                            int high = LookupSwitch.numberOfCases(code, curBCI) - 1;
                            while (low <= high) {
                                int mid = (low + high) >>> 1;
                                int midVal = LookupSwitch.keyAt(code, curBCI, mid);
                                if (midVal < key) {
                                    low = mid + 1;
                                } else if (midVal > key) {
                                    high = mid - 1;
                                } else {
                                    // Key found.
                                    int targetBCI = curBCI + LookupSwitch.offsetAt(code, curBCI, mid);
                                    top += ConstantBytecodes.stackEffectOf(LOOKUPSWITCH);
                                    curBCI = beforeJumpChecks(frame, curBCI, targetBCI, top);
                                    continue loop;
                                }
                            }

                            // Key not found.
                            int targetBCI = LookupSwitch.defaultTarget(code, curBCI);
                            top += ConstantBytecodes.stackEffectOf(LOOKUPSWITCH);
                            curBCI = beforeJumpChecks(frame, curBCI, targetBCI, top);
                            continue loop;
                        }

                        case IRETURN: // fall through
                        case LRETURN: // fall through
                        case FRETURN: // fall through
                        case DRETURN: // fall through
                        case ARETURN: // fall through
                        case RETURN: {
                            Object returnValue = getReturnValueAsObject(frame, method, top);
                            traceInterpreterReturn(method, indent, curBCI, top);
                            if (DebuggerEvents.singleton().isEventEnabled(Thread.currentThread(), EventKind.METHOD_EXIT)) {
                                if (method.getDeclaringClass().isMethodExitEvent()) {
                                    int flags = EventKind.METHOD_EXIT.getFlag() | EventKind.METHOD_EXIT_WITH_RETURN_VALUE.getFlag();
                                    DebuggerEvents.singleton().getEventHandler().onEventAt(Thread.currentThread(), method, curBCI, returnValue, flags);
                                }
                            }
                            return returnValue;
                        }
                        // @formatter:off
                        // Bytecodes order is shuffled.
                        case GETSTATIC : // fall through
                        case GETFIELD  : top += getField(frame, top, resolveField(method, curOpcode, BytecodeStream.readCPI2(code, curBCI)), curOpcode); break;
                        case PUTSTATIC : // fall through
                        case PUTFIELD  : top += putField(frame, top, resolveField(method, curOpcode, BytecodeStream.readCPI2(code, curBCI)), curOpcode); break;

                        case INVOKEVIRTUAL   : // fall through
                        case INVOKESPECIAL   : // fall through
                        case INVOKESTATIC    : // fall through
                        case INVOKEINTERFACE : // fall through
                        case INVOKEDYNAMIC   : {
                            boolean preferStayInInterpreter = forceStayInInterpreter;
                            SteppingControl steppingControl = null;
                            boolean stepEventDisabled = false;
                            Thread currentThread = Thread.currentThread();
                            if (DebuggerEvents.singleton().isEventEnabled(currentThread, EventKind.SINGLE_STEP)) {
                                // Disable stepping for inner frames, except for step into, where we must force interpreter execution.
                                steppingControl = DebuggerEvents.singleton().getSteppingControl(currentThread);
                                if (steppingControl != null) {
                                    // If step events can be ignored at frame n => can be also ignored at inner frame n + 1.
                                    steppingControl.pushFrame();
                                    if (!steppingControl.isActiveAtCurrentFrameDepth()) {
                                        DebuggerEvents.singleton().setEventEnabled(currentThread, EventKind.SINGLE_STEP, false);
                                        stepEventDisabled = true;
                                    }
                                    if (steppingControl.getDepth() == SteppingControl.STEP_INTO) {
                                        // For now force the callee to stay in interpreter.
                                        // If this is not possible, the next step event will be triggered only after returning.
                                        // From the debugger's perspective there's almost no difference between a compiled method and a native method.
                                        preferStayInInterpreter = true;
                                    }
                                }
                            }

                            try {
                                top += invoke(frame, method, code, top, curBCI, curOpcode, forceStayInInterpreter, preferStayInInterpreter);
                            } finally {
                                SteppingControl newSteppingControl = DebuggerEvents.singleton().getSteppingControl(currentThread);
                                if (newSteppingControl != null) {
                                    if (DebuggerEvents.singleton().isEventEnabled(currentThread, EventKind.SINGLE_STEP)) {
                                        newSteppingControl.popFrame();
                                    } else if (steppingControl == newSteppingControl && stepEventDisabled) {
                                        // Re-enable stepping events that could have been disabled by step outer/out into inner frames.
                                        DebuggerEvents.singleton().setEventEnabled(currentThread, EventKind.SINGLE_STEP, true);
                                        newSteppingControl.popFrame();
                                    }
                                }
                            }
                            break;
                        }

                        case NEW       : putObject(frame, top, InterpreterToVM.createNewReference(resolveType(method, NEW, BytecodeStream.readCPI2(code, curBCI)))); break;
                        case NEWARRAY  : putObject(frame, top - 1, InterpreterToVM.createNewPrimitiveArray(BytecodeStream.readByte(code, curBCI), popInt(frame, top - 1))); break;
                        case ANEWARRAY : putObject(frame, top - 1, InterpreterToVM.createNewReferenceArray(resolveType(method, ANEWARRAY, BytecodeStream.readCPI2(code, curBCI)), popInt(frame, top - 1))); break;
                        case ARRAYLENGTH : putInt(frame, top - 1, InterpreterToVM.arrayLength(nullCheck(popObject(frame, top - 1)))); break;
                        case ATHROW :
                            throw SemanticJavaException.raise((Throwable) nullCheck(popObject(frame, top - 1)));

                        case CHECKCAST : {
                            Object receiver = peekObject(frame, top - 1);
                            // Resolve type iff receiver != null.
                            if (receiver != null) {
                                InterpreterToVM.checkCast(receiver, resolveType(method, CHECKCAST, BytecodeStream.readCPI2(code, curBCI)));
                            }
                            break;
                        }
                        case INSTANCEOF : {
                            Object receiver = popObject(frame, top - 1);
                            // Resolve type iff receiver != null.
                            putInt(frame, top - 1, (receiver != null && InterpreterToVM.instanceOf(receiver, resolveType(method, INSTANCEOF, BytecodeStream.readCPI2(code, curBCI)))) ? 1 : 0);
                            break;
                        }
                        case MONITORENTER: InterpreterToVM.monitorEnter(frame, nullCheck(popObject(frame, top - 1))); break;
                        case MONITOREXIT : InterpreterToVM.monitorExit(frame, nullCheck(popObject(frame, top - 1))); break;

                        case WIDE: {
                            // The next opcode is never patched, plain access is fine.
                            int wideOpcode = BytecodeStream.opcode(code, curBCI + 1);
                            switch (wideOpcode) {
                                case ILOAD: putInt(frame, top, getLocalInt(frame, BytecodeStream.readLocalIndex2(code, curBCI))); break;
                                case LLOAD: putLong(frame, top, getLocalLong(frame, BytecodeStream.readLocalIndex2(code, curBCI))); break;
                                case FLOAD: putFloat(frame, top, getLocalFloat(frame, BytecodeStream.readLocalIndex2(code, curBCI))); break;
                                case DLOAD: putDouble(frame, top, getLocalDouble(frame, BytecodeStream.readLocalIndex2(code, curBCI))); break;
                                case ALOAD: putObject(frame, top, getLocalObject(frame, BytecodeStream.readLocalIndex2(code, curBCI))); break;

                                case ISTORE: setLocalInt(frame, BytecodeStream.readLocalIndex2(code, curBCI), popInt(frame, top - 1)); break;
                                case LSTORE: setLocalLong(frame, BytecodeStream.readLocalIndex2(code, curBCI), popLong(frame, top - 1)); break;
                                case FSTORE: setLocalFloat(frame, BytecodeStream.readLocalIndex2(code, curBCI), popFloat(frame, top - 1)); break;
                                case DSTORE: setLocalDouble(frame, BytecodeStream.readLocalIndex2(code, curBCI), popDouble(frame, top - 1)); break;
                                case ASTORE: setLocalObjectOrReturnAddress(frame, BytecodeStream.readLocalIndex2(code, curBCI), popReturnAddressOrObject(frame, top - 1)); break;
                                case IINC: setLocalInt(frame, BytecodeStream.readLocalIndex2(code, curBCI), getLocalInt(frame, BytecodeStream.readLocalIndex2(code, curBCI)) + BytecodeStream.readIncrement2(code, curBCI)); break;

                                case RET: {
                                    top += ConstantBytecodes.stackEffectOf(RET);
                                    curBCI = beforeJumpChecks(frame, curBCI, getLocalReturnAddress(frame, BytecodeStream.readLocalIndex2(code, curBCI)), top);
                                    continue loop;
                                }
                                default:
                                    throw VMError.shouldNotReachHere(Bytecodes.nameOf(curOpcode));
                            }
                            top += Bytecodes.stackEffectOf(wideOpcode);
                            curBCI += (wideOpcode == IINC) ? 6 : /* wide store/load */ 4;
                            continue loop;
                        }
                        // @formatter:on

                        case MULTIANEWARRAY:
                            top += allocateMultiArray(frame, top, resolveType(method, MULTIANEWARRAY, BytecodeStream.readCPI2(code, curBCI)), BytecodeStream.readUByte(code, curBCI + 3));
                            break;

                        default:
                            throw VMError.shouldNotReachHere(Bytecodes.nameOf(curOpcode));
                    }
                } catch (SemanticJavaException | OutOfMemoryError | StackOverflowError e) {
                    // Semantic Java exception thrown by interpreted code.
                    Throwable exception = e instanceof SemanticJavaException ? e.getCause() : e;
                    ExceptionHandler handler = resolveExceptionHandler(method, curBCI, exception);
                    if (handler != null) {
                        clearOperandStack(frame, method, top);
                        top = startingStackOffset(method.getMaxLocals());
                        putObject(frame, top, exception);
                        top++;
                        curBCI = beforeJumpChecks(frame, curBCI, handler.getHandlerBCI(), top);
                        continue loop;
                    } else {
                        traceInterpreterException(method, indent, curBCI, top);
                        throw uncheckedThrow(exception);
                    }
                } catch (Throwable e) {
                    // Exceptions other than SemanticJavaException (and OutOfMemoryError and
                    // StackoverflowError) are considered internal errors, a bug in the
                    // interpreter.
                    // VMError.shouldNotReachHere does not print the passed exception stack trace,
                    // so it's printed before panicking to help diagnose interpreter bugs.
                    e.printStackTrace();
                    throw VMError.shouldNotReachHere("Unexpected host exception reached the interpreter", e);
                }

                assert curOpcode != WIDE && curOpcode != LOOKUPSWITCH && curOpcode != TABLESWITCH;

                top += Bytecodes.stackEffectOf(curOpcode);
                curBCI += Bytecodes.lengthOf(curOpcode);
            } // loop
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException uncheckedThrow(Throwable e) throws T {
        throw (T) e;
    }

    private static Object getReturnValueAsObject(InterpreterFrame frame, InterpreterResolvedJavaMethod method, int top) {
        JavaKind returnType = method.getSignature().getReturnKind();
        // @formatter:off
        return switch (returnType) {
            case Boolean -> stackIntToBoolean(popInt(frame, top - 1));
            case Byte    -> (byte) popInt(frame, top - 1);
            case Short   -> (short) popInt(frame, top - 1);
            case Char    -> (char) popInt(frame, top - 1);
            case Int     -> popInt(frame, top - 1);
            case Long    -> popLong(frame, top - 1);
            case Float   -> popFloat(frame, top - 1);
            case Double  -> popDouble(frame, top - 1);
            case Void    -> null; // void
            case Object  -> popObject(frame, top - 1);
            default      -> throw VMError.shouldNotReachHereAtRuntime();
        };
        // @formatter:on
    }

    private static void clearOperandStack(InterpreterFrame frame, InterpreterResolvedJavaMethod method, int top) {
        int stackStart = startingStackOffset(method.getMaxLocals());
        for (int slot = top - 1; slot >= stackStart; --slot) {
            clear(frame, slot);
        }
    }

    private static boolean takeBranchRef1(Object operand, int opcode) {
        assert IFNULL <= opcode && opcode <= IFNONNULL;
        return switch (opcode) {
            case IFNULL -> operand == null;
            case IFNONNULL -> operand != null;
            default -> throw VMError.shouldNotReachHereAtRuntime();
        };
    }

    private static boolean takeBranchPrimitive1(int operand, int opcode) {
        assert IFEQ <= opcode && opcode <= IFLE;
        return switch (opcode) {
            case IFEQ -> operand == 0;
            case IFNE -> operand != 0;
            case IFLT -> operand < 0;
            case IFGE -> operand >= 0;
            case IFGT -> operand > 0;
            case IFLE -> operand <= 0;
            default -> throw VMError.shouldNotReachHereAtRuntime();
        };
    }

    private static boolean takeBranchPrimitive2(int operand1, int operand2, int opcode) {
        assert IF_ICMPEQ <= opcode && opcode <= IF_ICMPLE;
        return switch (opcode) {
            case IF_ICMPEQ -> operand1 == operand2;
            case IF_ICMPNE -> operand1 != operand2;
            case IF_ICMPLT -> operand1 > operand2;
            case IF_ICMPGE -> operand1 <= operand2;
            case IF_ICMPGT -> operand1 < operand2;
            case IF_ICMPLE -> operand1 >= operand2;
            default -> throw VMError.shouldNotReachHereAtRuntime();
        };
    }

    private static boolean takeBranchRef2(Object operand1, Object operand2, int opcode) {
        assert IF_ACMPEQ <= opcode && opcode <= IF_ACMPNE;
        return switch (opcode) {
            case IF_ACMPEQ -> operand1 == operand2;
            case IF_ACMPNE -> operand1 != operand2;
            default -> throw VMError.shouldNotReachHereAtRuntime();
        };
    }

    private static void arrayLoad(InterpreterFrame frame, int top, int loadOpcode) {
        assert IALOAD <= loadOpcode && loadOpcode <= SALOAD;
        int index = popInt(frame, top - 1);
        Object array = nullCheck(popObject(frame, top - 2));
        switch (loadOpcode) {
            case BALOAD -> putInt(frame, top - 2, InterpreterToVM.getArrayByte(index, array));
            case SALOAD -> putInt(frame, top - 2, InterpreterToVM.getArrayShort(index, (short[]) array));
            case CALOAD -> putInt(frame, top - 2, InterpreterToVM.getArrayChar(index, (char[]) array));
            case IALOAD -> putInt(frame, top - 2, InterpreterToVM.getArrayInt(index, (int[]) array));
            case FALOAD -> putFloat(frame, top - 2, InterpreterToVM.getArrayFloat(index, (float[]) array));
            case LALOAD -> putLong(frame, top - 2, InterpreterToVM.getArrayLong(index, (long[]) array));
            case DALOAD -> putDouble(frame, top - 2, InterpreterToVM.getArrayDouble(index, (double[]) array));
            case AALOAD -> putObject(frame, top - 2, InterpreterToVM.getArrayObject(index, (Object[]) array));
            default -> throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    private static void arrayStore(InterpreterFrame frame, int top, int storeOpcode) {
        assert IASTORE <= storeOpcode && storeOpcode <= SASTORE;
        int offset = (storeOpcode == LASTORE || storeOpcode == DASTORE) ? 2 : 1;
        int index = popInt(frame, top - 1 - offset);
        Object array = nullCheck(popObject(frame, top - 2 - offset));
        switch (storeOpcode) {
            case BASTORE -> InterpreterToVM.setArrayByte((byte) popInt(frame, top - 1), index, array);
            case SASTORE -> InterpreterToVM.setArrayShort((short) popInt(frame, top - 1), index, (short[]) array);
            case CASTORE -> InterpreterToVM.setArrayChar((char) popInt(frame, top - 1), index, (char[]) array);
            case IASTORE -> InterpreterToVM.setArrayInt(popInt(frame, top - 1), index, (int[]) array);
            case FASTORE -> InterpreterToVM.setArrayFloat(popFloat(frame, top - 1), index, (float[]) array);
            case LASTORE -> InterpreterToVM.setArrayLong(popLong(frame, top - 1), index, (long[]) array);
            case DASTORE -> InterpreterToVM.setArrayDouble(popDouble(frame, top - 1), index, (double[]) array);
            case AASTORE -> InterpreterToVM.setArrayObject(popObject(frame, top - 1), index, (Object[]) array);
            default -> throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    @SuppressWarnings("unused")
    private static int beforeJumpChecks(InterpreterFrame frame, int curBCI, int targetBCI, int top) {
        if (targetBCI <= curBCI) {
            // GR-55055: Safepoint poll needed?
        }
        return targetBCI;
    }

    private static ExceptionHandler resolveExceptionHandler(InterpreterResolvedJavaMethod method, int bci, Throwable ex) {
        ExceptionHandler[] handlers = method.getExceptionHandlers();
        ExceptionHandler resolved = null;
        for (ExceptionHandler toCheck : handlers) {
            if (bci >= toCheck.getStartBCI() && bci < toCheck.getEndBCI()) {
                JavaType catchType = null;
                if (!toCheck.isCatchAll()) {
                    // exception handlers are similar to instanceof bytecodes, so we pass instanceof
                    catchType = resolveTypeOrNullIfUnresolvable(method, INSTANCEOF, (char) toCheck.catchTypeCPI());
                    if (catchType == null) {
                        /*
                         * TODO(peterssen): GR-68575 Depending on the constraints, this should
                         * either panic or just propagate the class resolution error. This happens
                         * when there's a missing or purely symbolic entry in a pre-resolved
                         * constant pool. Exception type is not reachable/resolvable, skip handler.
                         */
                        continue;
                    }
                }
                if (catchType == null || InterpreterToVM.instanceOf(ex, (InterpreterResolvedObjectType) catchType)) {
                    // the first found exception handler is our exception handler
                    resolved = toCheck;
                    break;
                }
            }
        }
        return resolved;
    }

    private static SemanticJavaException noClassDefFoundError(int opcode, JavaType javaType) {
        String message = (javaType != null)
                        ? javaType.toJavaName()
                        : MetadataUtil.fmt("%s: (cpi = 0) unknown type", Bytecodes.nameOf(opcode));
        throw SemanticJavaException.raise(new NoClassDefFoundError(message));
    }

    private static SemanticJavaException noSuchMethodError(int opcode, JavaMethod javaMethod) {
        String message = (javaMethod != null)
                        ? javaMethod.format("%H.%n(%P)")
                        : MetadataUtil.fmt("%s: (cpi = 0) unknown method", Bytecodes.nameOf(opcode));
        throw SemanticJavaException.raise(new NoSuchMethodError(message));
    }

    private static SemanticJavaException noSuchFieldError(int opcode, JavaField javaField) {
        String message = (javaField != null)
                        ? javaField.format("%H.%n")
                        : MetadataUtil.fmt("%s: (cpi = 0) unknown field", Bytecodes.nameOf(opcode));
        throw SemanticJavaException.raise(new NoSuchFieldError(message));
    }

    private static void loadConstant(InterpreterFrame frame, InterpreterResolvedJavaMethod method, int top, char cpi, int opcode) {
        assert opcode == LDC || opcode == LDC_W || opcode == LDC2_W;
        if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, cpi == 0)) {
            VMError.guarantee(opcode != LDC2_W);
            throw noClassDefFoundError(opcode, null);
        }
        InterpreterConstantPool pool = getConstantPool(method);
        ConstantPool.Tag tag = pool.tagAt(cpi);
        switch (tag) {
            case INTEGER -> putInt(frame, top, pool.intAt(cpi));
            case FLOAT -> putFloat(frame, top, pool.floatAt(cpi));
            case LONG -> putLong(frame, top, pool.longAt(cpi));
            case DOUBLE -> putDouble(frame, top, pool.doubleAt(cpi));
            case CLASS -> {
                InterpreterResolvedJavaType resolvedType = resolveType(method, opcode, cpi);
                putObject(frame, top, resolvedType.getJavaClass());
            }
            case STRING -> {
                String string = pool.resolveStringAt(cpi);
                putObject(frame, top, string);
            }
            case INVOKEDYNAMIC -> {
                // TODO(peterssen): GR-68576 Storing the pre-resolved appendix in the CP is a
                // workaround for the JDWP debugger until proper INVOKEDYNAMIC resolution is
                // implemented.
                Object appendix = pool.resolvedAt(cpi, null);
                if (appendix instanceof ReferenceConstant<?> referenceConstant) {
                    VMError.guarantee(referenceConstant.isNonNull(), FAILURE_CONSTANT_NOT_PART_OF_IMAGE_HEAP);
                    Object constantValue = referenceConstant.getReferent();
                    putObject(frame, top, constantValue);
                } else {
                    // Raw object.
                    putObject(frame, top, appendix);
                }
            }
            default -> throw VMError.unimplemented("LDC* constant pool type " + tag);
        }
    }

    private static InterpreterConstantPool getConstantPool(InterpreterResolvedJavaMethod method) {
        return method.getConstantPool();
    }

    private static int invoke(InterpreterFrame callerFrame, InterpreterResolvedJavaMethod method, byte[] code, int top, int curBCI, int opcode, boolean forceStayInInterpreter,
                    boolean preferStayInInterpreter) {
        int invokeTop = top;

        char cpi = BytecodeStream.readCPI2(code, curBCI);
        InterpreterResolvedJavaMethod seedMethod = Interpreter.resolveMethod(method, opcode, cpi);

        boolean hasReceiver = !seedMethod.isStatic();
        boolean isVirtual = opcode == INVOKEVIRTUAL || opcode == INVOKEINTERFACE;

        if (opcode == INVOKEDYNAMIC) {
            int appendixCPI = BytecodeStream.readCPI4(code, curBCI) & 0xFFFF;
            if (appendixCPI != 0) {
                Object appendixEntry = method.getConstantPool().resolvedAt(appendixCPI, method.getDeclaringClass());
                Object appendix;
                if (JavaConstant.NULL_POINTER.equals(appendixEntry)) {
                    // The appendix is deliberately null.
                    appendix = null;
                } else {
                    if (appendixEntry instanceof ReferenceConstant<?> referenceConstant) {
                        appendix = referenceConstant.getReferent();
                    } else {
                        throw VMError.shouldNotReachHere("Unexpected INVOKEDYNAMIC appendix constant: " + appendixEntry);
                    }
                    if (appendix == null) {
                        throw SemanticJavaException.raise(new IncompatibleClassChangeError("INVOKEDYNAMIC appendix was not included in the image heap"));
                    }
                }
                EspressoFrame.putObject(callerFrame, top, appendix);
                invokeTop = top + 1;
            } else {
                throw VMError.shouldNotReachHere("Appendix-less INVOKEDYNAMIC");
            }
        }

        InterpreterUnresolvedSignature seedSignature = seedMethod.getSignature();
        int resultAt = invokeTop - seedSignature.slotsForParameters(hasReceiver);
        // The stack effect is wrt. the original top-of-the-stack.
        int retStackEffect = resultAt - top;

        Object[] calleeArgs = EspressoFrame.popArguments(callerFrame, invokeTop, hasReceiver, seedSignature);
        if (!seedMethod.isStatic()) {
            nullCheck(calleeArgs[0]);
        }
        Object retObj = InterpreterToVM.dispatchInvocation(seedMethod, calleeArgs, isVirtual, forceStayInInterpreter, preferStayInInterpreter, opcode == INVOKEINTERFACE);

        retStackEffect += EspressoFrame.putKind(callerFrame, resultAt, retObj, seedSignature.getReturnKind());

        /* instructions have fixed stack effect encoded */
        return retStackEffect - Bytecodes.stackEffectOf(opcode);
    }

    // region Class/Method/Field resolution

    private static InterpreterResolvedJavaType resolveType(InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        assert opcode == INSTANCEOF || opcode == CHECKCAST || opcode == NEW || opcode == ANEWARRAY || opcode == MULTIANEWARRAY || opcode == LDC || opcode == LDC_W;
        if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, cpi == 0)) {
            throw noClassDefFoundError(opcode, null);
        }
        try {
            return getConstantPool(method).resolvedTypeAt(method.getDeclaringClass(), cpi);
        } catch (UnsupportedResolutionException e) {
            // CP does not support resolution, try to provide a hint of the non-resolvable entry.
            UnresolvedJavaType missingType = null;
            if (getConstantPool(method).peekCachedEntry(cpi) instanceof UnresolvedJavaType unresolvedJavaType) {
                missingType = unresolvedJavaType;
            }
            throw noClassDefFoundError(opcode, missingType);
        } catch (ClassFormatError e) {
            // Out-of-bounds CPI or mis-matching tag.
            throw SemanticJavaException.raise(e);
        }
    }

    private static InterpreterResolvedJavaType resolveTypeOrNullIfUnresolvable(InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        assert opcode == INSTANCEOF || opcode == CHECKCAST || opcode == NEW || opcode == ANEWARRAY || opcode == MULTIANEWARRAY || opcode == LDC || opcode == LDC_W;
        if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, cpi == 0)) {
            return null; // CPI 0 is a marker for unresolvable AND unknown entry
        }
        try {
            return getConstantPool(method).resolvedTypeAt(method.getDeclaringClass(), cpi);
        } catch (UnsupportedResolutionException e) {
            return null;
        } catch (ClassFormatError e) {
            // Out-of-bounds CPI or mis-matching tag.
            // Unrelated to resolution, just propagate the error.
            throw SemanticJavaException.raise(e);
        }
    }

    private static InterpreterResolvedJavaMethod resolveMethod(InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        assert Bytecodes.isInvoke(opcode);
        if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, cpi == 0)) {
            throw noSuchMethodError(opcode, null);
        }
        try {
            return getConstantPool(method).resolvedMethodAt(method.getDeclaringClass(), cpi);
        } catch (UnsupportedResolutionException e) {
            // CP does not support resolution, try to provide a hint of the non-resolvable entry.
            UnresolvedJavaMethod missingMethod = null;
            if (getConstantPool(method).peekCachedEntry(cpi) instanceof UnresolvedJavaMethod unresolvedJavaMethod) {
                missingMethod = unresolvedJavaMethod;
            }
            throw noSuchMethodError(opcode, missingMethod);
        } catch (ClassFormatError e) {
            // Out-of-bounds CPI or mis-matching tag.
            throw SemanticJavaException.raise(e);
        }
    }

    private static InterpreterResolvedJavaField resolveField(InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        assert opcode == GETFIELD || opcode == GETSTATIC || opcode == PUTFIELD || opcode == PUTSTATIC;
        if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, cpi == 0)) {
            throw noSuchFieldError(opcode, null);
        }
        try {
            return getConstantPool(method).resolvedFieldAt(method.getDeclaringClass(), cpi);
        } catch (UnsupportedResolutionException e) {
            // CP does not support resolution, try to provide a hint of the non-resolvable entry.
            UnresolvedJavaField missingField = null;
            if (getConstantPool(method).peekCachedEntry(cpi) instanceof UnresolvedJavaField unresolvedJavaField) {
                missingField = unresolvedJavaField;
            }
            throw noSuchFieldError(opcode, missingField);
        } catch (ClassFormatError e) {
            // Out of bounds CPI or mis-matching tag.
            throw SemanticJavaException.raise(e);
        }
    }

    // endregion Class/Field/Method resolution

    private static int allocateMultiArray(InterpreterFrame frame, int top, ResolvedJavaType multiArrayType, int allocatedDimensions) {
        assert multiArrayType.isArray() : multiArrayType;
        assert allocatedDimensions > 0 : allocatedDimensions;
        assert multiArrayType.getElementalType().getJavaKind() != JavaKind.Void;
        int[] dimensions = new int[allocatedDimensions];
        for (int i = 0; i < allocatedDimensions; ++i) {
            dimensions[i] = popInt(frame, top - allocatedDimensions + i);
        }
        Object value = InterpreterToVM.createMultiArray((InterpreterResolvedJavaType) multiArrayType, dimensions);
        putObject(frame, top - allocatedDimensions, value);
        return -allocatedDimensions; // Does not include the created (pushed) array.
    }

    private static boolean stackIntToBoolean(int result) {
        return (result & 1) != 0;
    }

    // region Arithmetic/binary operations

    private static int divInt(int divisor, int dividend) {
        try {
            return dividend / divisor;
        } catch (ArithmeticException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    private static long divLong(long divisor, long dividend) {
        try {
            return dividend / divisor;
        } catch (ArithmeticException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    private static float divFloat(float divisor, float dividend) {
        return dividend / divisor;
    }

    private static double divDouble(double divisor, double dividend) {
        return dividend / divisor;
    }

    private static int remInt(int divisor, int dividend) {
        try {
            return dividend % divisor;
        } catch (ArithmeticException e) {
            throw SemanticJavaException.raise(e);
        }
    }

    private static long remLong(long divisor, long dividend) {
        try {
            return dividend % divisor;
        } catch (ArithmeticException e) {
            throw SemanticJavaException.raise(e);
        }
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

    // region Field read/write

    /**
     * Returns the offset adjustment, depending on how many slots are needed for the value that
     * complete the {@link Bytecodes#stackEffectOf(int) stack effect} for the opcode.
     *
     * <pre>
     *   top += putField(frame, top, resolveField(...)); break; // stack effect adjust
     *   ...
     *   top += Bytecodes.stackEffectOf(curOpcode);
     *   // at this point `top` must have the correct value.
     *   curBCI = bs.next(curBCI);
     * </pre>
     */
    private static int putField(InterpreterFrame frame, int top, InterpreterResolvedJavaField field, int opcode) {
        assert opcode == PUTFIELD || opcode == PUTSTATIC;
        assert field.isStatic() == (opcode == PUTSTATIC);
        assert !field.isUnmaterializedConstant();
        JavaKind kind = field.getJavaKind();
        assert kind != JavaKind.Illegal;

        int slotCount = kind.getSlotCount();
        Object receiver = (opcode == PUTSTATIC)
                        ? (kind.isPrimitive() ? StaticFieldsSupport.getStaticPrimitiveFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER)
                                        : StaticFieldsSupport.getStaticObjectFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER))
                        : nullCheck(popObject(frame, top - slotCount - 1));

        if (field.isStatic()) {
            InterpreterToVM.ensureClassInitialized(field.getDeclaringClass());
        }

        // @formatter:off
        switch (kind) {
            case Boolean -> InterpreterToVM.setFieldBoolean(stackIntToBoolean(popInt(frame, top - 1)), receiver, field);
            case Byte    -> InterpreterToVM.setFieldByte((byte) popInt(frame, top - 1), receiver, field);
            case Char    -> InterpreterToVM.setFieldChar((char) popInt(frame, top - 1), receiver, field);
            case Short   -> InterpreterToVM.setFieldShort((short) popInt(frame, top - 1), receiver, field);
            case Int     -> InterpreterToVM.setFieldInt(popInt(frame, top - 1), receiver, field);
            case Double  -> InterpreterToVM.setFieldDouble(popDouble(frame, top - 1), receiver, field);
            case Float   -> InterpreterToVM.setFieldFloat(popFloat(frame, top - 1), receiver, field);
            case Long    -> InterpreterToVM.setFieldLong(popLong(frame, top - 1), receiver, field);
            case Object  -> InterpreterToVM.setFieldObject(popObject(frame, top - 1), receiver, field);
            default      -> throw VMError.shouldNotReachHereAtRuntime();
        }
        // @formatter:on
        return -slotCount + 1;
    }

    /**
     * Returns the offset adjustment, depending on how many slots are needed for the value that
     * complete the {@link Bytecodes#stackEffectOf(int) stack effect} for the opcode.
     *
     * <pre>
     *   top += getField(frame, top, resolveField(...)); break; // stack effect adjustment that depends on the field
     *   ...
     *   top += Bytecodes.stackEffectOf(curOpcode); // minimum stack effect
     *   // at this point `top` must have the correct value.
     *   curBCI = bs.next(curBCI);
     * </pre>
     */
    private static int getField(InterpreterFrame frame, int top, InterpreterResolvedJavaField field, int opcode) {
        assert opcode == GETFIELD || opcode == GETSTATIC;
        assert field.isStatic() == (opcode == GETSTATIC);
        JavaKind kind = field.getJavaKind();
        assert kind != JavaKind.Illegal;

        Object receiver = opcode == GETSTATIC
                        ? (kind.isPrimitive() ? StaticFieldsSupport.getStaticPrimitiveFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER)
                                        : StaticFieldsSupport.getStaticObjectFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER))
                        : nullCheck(popObject(frame, top - 1));

        if (field.isStatic()) {
            InterpreterToVM.ensureClassInitialized(field.getDeclaringClass());
        }

        int resultAt = field.isStatic() ? top : (top - 1);

        // @formatter:off
        switch (kind) {
            case Boolean -> putInt(frame, resultAt, InterpreterToVM.getFieldBoolean(receiver, field) ? 1 : 0);
            case Byte    -> putInt(frame, resultAt, InterpreterToVM.getFieldByte(receiver, field));
            case Char    -> putInt(frame, resultAt, InterpreterToVM.getFieldChar(receiver, field));
            case Short   -> putInt(frame, resultAt, InterpreterToVM.getFieldShort(receiver, field));
            case Int     -> putInt(frame, resultAt, InterpreterToVM.getFieldInt(receiver, field));
            case Double  -> putDouble(frame, resultAt, InterpreterToVM.getFieldDouble(receiver, field));
            case Float   -> putFloat(frame, resultAt, InterpreterToVM.getFieldFloat(receiver, field));
            case Long    -> putLong(frame, resultAt, InterpreterToVM.getFieldLong(receiver, field));
            case Object  -> putObject(frame, resultAt, InterpreterToVM.getFieldObject(receiver, field));
            default      -> throw VMError.shouldNotReachHereAtRuntime();
        }
        // @formatter:on
        return kind.getSlotCount() - 1;
    }

    // endregion Field read/write

}
