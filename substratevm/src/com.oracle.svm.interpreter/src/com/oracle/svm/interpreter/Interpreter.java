/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.interpreter.InterpreterFrameUtil.clear;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.dup1;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.dup2;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.dup2x1;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.dup2x2;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.dupx1;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.dupx2;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.getLocalDouble;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.getLocalFloat;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.getLocalInt;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.getLocalLong;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.getLocalObject;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.getLocalReturnAddress;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.peekObject;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.popDouble;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.popFloat;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.popInt;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.popLong;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.popObject;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.popReturnAddressOrObject;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.putDouble;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.putFloat;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.putInt;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.putLong;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.putObject;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.putReturnAddress;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.setLocalDouble;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.setLocalFloat;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.setLocalInt;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.setLocalLong;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.setLocalObject;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.setLocalObjectOrReturnAddress;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.startingStackOffset;
import static com.oracle.svm.interpreter.InterpreterFrameUtil.swapSingle;
import static com.oracle.svm.interpreter.InterpreterOptions.InterpreterTraceSupport;
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
import static com.oracle.svm.interpreter.metadata.Bytecodes.QUICK_GETFIELD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.QUICK_GETSTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.QUICK_PUTFIELD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.QUICK_PUTSTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.RET;
import static com.oracle.svm.interpreter.metadata.Bytecodes.RETURN;
import static com.oracle.svm.interpreter.metadata.Bytecodes.SALOAD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.SASTORE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.SIPUSH;
import static com.oracle.svm.interpreter.metadata.Bytecodes.SWAP;
import static com.oracle.svm.interpreter.metadata.Bytecodes.TABLESWITCH;
import static com.oracle.svm.interpreter.metadata.Bytecodes.WIDE;
import static com.oracle.svm.interpreter.metadata.CremaTypeAccess.symbolToJvmciKind;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;

import com.oracle.svm.core.ForeignSupport;
import com.oracle.svm.core.NeverInlineTrivial;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.core.methodhandles.MethodHandleInterpreterUtils;
import com.oracle.svm.espresso.classfile.ConstantPool;
import com.oracle.svm.espresso.shared.meta.SignaturePolymorphicIntrinsic;
import com.oracle.svm.espresso.shared.resolver.CallKind;
import com.oracle.svm.espresso.shared.resolver.CallSiteType;
import com.oracle.svm.espresso.shared.resolver.ResolvedCall;
import com.oracle.svm.guest.staging.jdk.InternalVMMethod;
import com.oracle.svm.interpreter.debug.DebuggerEvents;
import com.oracle.svm.interpreter.debug.EventKind;
import com.oracle.svm.interpreter.debug.SteppingControl;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.Bytecodes;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool.LinkedInvoke;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedInvokeGenericJavaMethod;
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
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;
import com.oracle.svm.interpreter.ristretto.RistrettoOSRSupport;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoProfileSupport;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterFetchOpcode;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandler;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig;
import jdk.graal.compiler.api.replacements.Fold;
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
 * Executes methods represented by Crema interpreter metadata.
 *
 * <p>
 * This class is the Java entry point for interpreter execution. It creates or accepts an
 * {@link InterpreterFrame}, initializes the local variables from Java-call arguments, checks that
 * the target method has interpreter bytecodes, and then dispatches either to the ordinary bytecode
 * loop in {@link Root} or to the intrinsic path used for signature-polymorphic method-handle
 * operations.
 *
 * <p>
 * Guest Java exceptions are kept distinct from interpreter implementation failures. Bytecodes that
 * semantically throw a Java exception use {@link SemanticJavaException} while execution is inside
 * the interpreter so exception handlers can be resolved against the interpreted method. Exceptions
 * that escape back to compiled or reflective callers are rethrown as the original guest exception.
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
            InterpreterUtil.assertion(arguments[0] != null, "null receiver in init arguments !");
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
        checkExecutable(method);
        return execute0(method, frame, false);
    }

    public static Object execute(InterpreterResolvedJavaMethod method, Object[] args, boolean forceStayInInterpreter) {
        InterpreterFrame frame = InterpreterFrameUtil.allocate(method.getMaxLocals(), method.getMaxStackSize(), args);
        checkExecutable(method);
        initializeFrame(frame, method);
        return execute0(method, frame, forceStayInInterpreter);
    }

    private static void checkExecutable(InterpreterResolvedJavaMethod method) {
        if (method.hasBytecodes() || method.getSignaturePolymorphicIntrinsic() != null) {
            return;
        }
        InterpreterResolvedObjectType declaringClass = method.getDeclaringClass();
        if (!declaringClass.getHub().isRuntimeLoaded()) {
            if (method.isNative()) {
                if (!declaringClass.getHub().isPreserved()) {
                    throw VMError.shouldNotReachHere(MetadataUtil.fmt("Trying to interpret AOT native method: %s.%nConsider using '-H:Preserve=package=%s'", method,
                                    declaringClass.getHub().getPackageName()));
                } else {
                    throw VMError.shouldNotReachHere(MetadataUtil.fmt("Should not reach interpreter for AOT native method in preserved type: %s", method));
                }
            } else if (!method.isAbstract()) {
                if (!declaringClass.getHub().isPreserved()) {
                    throw VMError.shouldNotReachHere(MetadataUtil.fmt("Trying to interpret AOT method with no preserved bytecodes: %s.%nConsider using '-H:Preserve=package=%s'", method,
                                    declaringClass.getHub().getPackageName()));
                } else {
                    throw VMError.shouldNotReachHere(MetadataUtil.fmt("Should not reach interpreter for AOT method in preserved type: %s", method));
                }
            } else {
                throw VMError.shouldNotReachHere(MetadataUtil.fmt("Should not reach interpreter for AOT abstract method %s", method));
            }
        } else {
            if (method.isNative()) {
                throw VMError.shouldNotReachHere(MetadataUtil.fmt("Runtime native method should have been dispatched earlier: %s", method));
            } else if (!method.isAbstract()) {
                throw VMError.shouldNotReachHere(MetadataUtil.fmt("Missing bytecode for run-time-loaded method %s", method));
            } else {
                throw VMError.shouldNotReachHere(MetadataUtil.fmt("Should not reach interpreter for run-time-loaded abstract method %s", method));
            }

        }
    }

    public static Object execute(InterpreterResolvedJavaMethod method, InterpreterFrame frame, int startBCI, int startTOP) {
        checkExecutable(method);
        return execute0(method, frame, startBCI, startTOP);
    }

    /**
     * Returns the monitor object for a synchronized method at normal interpreter entry.
     * <p>
     * Static synchronized methods lock their declaring class mirror. Instance synchronized methods
     * lock local 0 ({@code this}), which must be live on normal entry because the interpreter is
     * about to acquire the monitor itself.
     */
    private static Object getSynchronizedMethodLock(InterpreterResolvedJavaMethod method, InterpreterFrame frame) {
        return method.isStatic()
                        ? method.getDeclaringClass().getJavaClass()
                        : frame.getObjectStatic(0);
    }

    private static Object execute0(InterpreterResolvedJavaMethod method, InterpreterFrame frame, int startBCI, int startTop) {
        boolean releaseSynchronizedMethodLock = false;
        boolean releaseInterpreterFrameLocks = true;
        try {
            int executeBCI = startBCI;
            if (startBCI == jdk.vm.ci.code.BytecodeFrame.BEFORE_BCI) {
                executeBCI = 0;
                if (method.isSynchronized()) {
                    Object synchronizedMethodLock = getSynchronizedMethodLock(method, frame);
                    assert synchronizedMethodLock != null;
                    InterpreterToVM.monitorEnter(frame, synchronizedMethodLock);
                    releaseSynchronizedMethodLock = true;
                }
            } else if (method.isSynchronized()) {
                releaseSynchronizedMethodLock = true;
            }
            assert method.getInterpretedCode() != null : "no bytecode stream for " + method;
            return Root.executeBodyFromBCI(frame, method, executeBCI, startTop, false);
        } catch (OSRReturn e) {
            releaseInterpreterFrameLocks = false;
            return e.result();
        } catch (OSRException e) {
            releaseInterpreterFrameLocks = false;
            throw uncheckedThrow(e.exception());
        } finally {
            if (releaseInterpreterFrameLocks) {
                InterpreterToVM.releaseInterpreterFrameLocks(frame, releaseSynchronizedMethodLock);
            }
        }
    }

    private static Object execute0(InterpreterResolvedJavaMethod method, InterpreterFrame frame, boolean stayInInterpreter) {
        boolean releaseSynchronizedMethodLock = false;
        boolean releaseInterpreterFrameLocks = true;
        try {
            assert method.isStatic() || InterpreterFrameUtil.getThis(frame) != null;
            if (method.isSynchronized()) {
                Object synchronizedMethodLock = getSynchronizedMethodLock(method, frame);
                assert synchronizedMethodLock != null;
                InterpreterToVM.monitorEnter(frame, synchronizedMethodLock);
                releaseSynchronizedMethodLock = true;
            }
            SignaturePolymorphicIntrinsic intrinsic = method.getSignaturePolymorphicIntrinsic();
            if (intrinsic != null) {
                return IntrinsicRoot.execute(frame, method, intrinsic, stayInInterpreter);
            } else {
                assert method.getInterpretedCode() != null : "no bytecode stream for " + method;
                int startTop = startingStackOffset(method.getMaxLocals());
                return Root.executeBodyFromBCI(frame, method, 0, startTop, stayInInterpreter);
            }
        } catch (OSRReturn e) {
            releaseInterpreterFrameLocks = false;
            return e.result();
        } catch (OSRException e) {
            releaseInterpreterFrameLocks = false;
            throw uncheckedThrow(e.exception());
        } finally {
            if (releaseInterpreterFrameLocks) {
                InterpreterToVM.releaseInterpreterFrameLocks(frame, releaseSynchronizedMethodLock);
            }
        }
    }

    public static final ThreadLocal<Integer> logIndent = ThreadLocal.withInitial(() -> 0);

    private static int getLogIndent() {
        if (InterpreterTraceSupport.getValue()) {
            return logIndent.get();
        }
        return 0;
    }

    private static void setLogIndent(int indent) {
        if (InterpreterTraceSupport.getValue()) {
            logIndent.set(indent);
        }
    }

    private static void traceInterpreterEnter(InterpreterResolvedJavaMethod method, int indent, int curBCI, int top) {
        /* arguments to Log methods might have side-effects */
        if (!InterpreterTraceSupport.getValue()) {
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
        if (!InterpreterTraceSupport.getValue()) {
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

    /**
     * Completes a successful interpreter return by performing the trace and debugger notifications
     * that are part of the interpreter's return-side effects.
     * <p>
     * Ristretto OSR returns bypass this helper because the OSR continuation has already left the
     * interpreter and returns as runtime-compiled code.
     */
    private static void returnFromInterpreter(InterpreterResolvedJavaMethod method, int indent, int curBCI, int top, Object returnValue) {
        traceInterpreterReturn(method, indent, curBCI, top);
        Thread currentThread = Thread.currentThread();
        if (Root.debuggerEventsSupported() && DebuggerEvents.singleton().isEventEnabled(currentThread, EventKind.METHOD_EXIT)) {
            if (method.getDeclaringClass().isMethodExitEvent()) {
                int flags = EventKind.METHOD_EXIT.getFlag() | EventKind.METHOD_EXIT_WITH_RETURN_VALUE.getFlag();
                DebuggerEvents.singleton().getEventHandler().onEventAt(currentThread, method, curBCI, returnValue, flags);
            }
        }
    }

    private static void traceInterpreterInstruction(InterpreterFrame frame, int indent, int curBCI, int top, int curOpcode) {
        /* arguments to Log methods might have side-effects */
        if (!InterpreterTraceSupport.getValue()) {
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
        if (!InterpreterTraceSupport.getValue()) {
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

    private static void traceIntrinsicEnter(InterpreterResolvedJavaMethod method, int indent, SignaturePolymorphicIntrinsic intrinsic) {
        /* arguments to Log methods might have side-effects */
        if (!InterpreterTraceSupport.getValue()) {
            return;
        }

        setLogIndent(indent + 2);
        traceInterpreter(" ".repeat(indent)) //
                        .string("[interp] Intrinsic Entered ") //
                        .string(method.getDeclaringClass().getName()) //
                        .string("::") //
                        .string(method.getName()) //
                        .string(method.getSignature().toMethodDescriptor()) //
                        .string(" with iid=").string(intrinsic.name()) //
                        .newline();
    }

    private static void traceInvokeBasic(InterpreterResolvedJavaMethod target, int indent) {
        /* arguments to Log methods might have side-effects */
        if (!InterpreterTraceSupport.getValue()) {
            return;
        }

        traceInterpreter(" ".repeat(indent)) //
                        .string("invokeBasic target=") //
                        .string(target.getDeclaringClass().getName()) //
                        .string("::") //
                        .string(target.getName()) //
                        .string(target.getSignature().toMethodDescriptor()) //
                        .newline();
    }

    private static void traceLinkTo(InterpreterResolvedJavaMethod target, SignaturePolymorphicIntrinsic intrinsic, int indent) {
        /* arguments to Log methods might have side-effects */
        if (!InterpreterTraceSupport.getValue()) {
            return;
        }

        traceInterpreter(" ".repeat(indent)) //
                        .string(intrinsic.name()).string(" target=") //
                        .string(target.getDeclaringClass().getName()) //
                        .string("::") //
                        .string(target.getName()) //
                        .string(target.getSignature().toMethodDescriptor()) //
                        .newline();
    }

    public static final class JNIDowncallRoot {
        @NeverInline("needed for JNI caller-sensitive stack walks")
        public static Object execute(InterpreterResolvedJavaMethod seedMethod, Object[] args) throws Throwable {
            return InterpreterStubSection.leaveInterpreterJNI(seedMethod, args);
        }
    }

    public static final class IntrinsicRoot {
        @NeverInline("needed far stack walking")
        public static Object execute(InterpreterFrame frame, InterpreterResolvedJavaMethod method, SignaturePolymorphicIntrinsic intrinsic, boolean forceStayInInterpreter) {
            int indent = getLogIndent();
            traceIntrinsicEnter(method, indent, intrinsic);
            return switch (intrinsic) {
                case InvokeBasic -> {
                    MethodHandle mh = (MethodHandle) InterpreterFrameUtil.getThis(frame);
                    Target_java_lang_invoke_MemberName vmentry = MethodHandleInterpreterUtils.extractVMEntry(mh);
                    InterpreterResolvedJavaMethod target = InterpreterResolvedJavaMethod.fromMemberName(vmentry);
                    InterpreterUnresolvedSignature signature = method.getSignature();
                    Object[] calleeArgs = rebasic(frame.getArguments(), signature, !method.isStatic());
                    // This should integrate with the debugger GR-70801
                    boolean preferStayInInterpreter = forceStayInInterpreter;
                    traceInvokeBasic(target, indent);
                    try {
                        Object result = InterpreterToVM.dispatchInvocation(target, calleeArgs, CallKind.DIRECT, forceStayInInterpreter, preferStayInInterpreter, false);
                        yield unbasic(result, signature.getReturnKind());
                    } catch (SemanticJavaException e) {
                        throw uncheckedThrow(e.getCause());
                    }
                }
                case LinkToStatic, LinkToSpecial, LinkToVirtual, LinkToInterface -> {
                    InterpreterResolvedJavaMethod resolutionSeed = getLinkToTarget(frame);
                    InterpreterUnresolvedSignature signature = resolutionSeed.getSignature();
                    boolean hasReceiver = intrinsic != SignaturePolymorphicIntrinsic.LinkToStatic;
                    Object[] basicArgs = unbasic(frame, signature, hasReceiver);
                    // This should integrate with the debugger GR-70801
                    boolean preferStayInInterpreter = forceStayInInterpreter;
                    traceLinkTo(resolutionSeed, intrinsic, indent);
                    try {
                        Object result = InterpreterToVM.dispatchInvocation(resolutionSeed, basicArgs, intrinsic.getCallKind(), forceStayInInterpreter, preferStayInInterpreter, false);
                        yield rebasic(result, signature.getReturnKind());
                    } catch (SemanticJavaException e) {
                        throw uncheckedThrow(e.getCause());
                    }
                }
                case LinkToNative -> {
                    if (!ForeignSupport.isAvailable()) {
                        throw VMError.unsupportedFeature("The foreign downcalls feature is not available. Please use -H:+ForeignAPISupport or leave this option default");
                    }
                    try {
                        yield ForeignSupport.singleton().linkToNative(frame.getArguments());
                    } catch (Throwable e) {
                        throw uncheckedThrow(e);
                    }
                }
                default -> throw VMError.shouldNotReachHere(Objects.toString(intrinsic));
            };
        }
    }

    private static InterpreterResolvedJavaMethod getLinkToTarget(InterpreterFrame frame) {
        Object[] arguments = frame.getArguments();
        Target_java_lang_invoke_MemberName memberName = (Target_java_lang_invoke_MemberName) arguments[arguments.length - 1];
        return InterpreterResolvedJavaMethod.fromMemberName(memberName);
    }

    private static Object[] unbasic(InterpreterFrame frame, InterpreterUnresolvedSignature targetSig, boolean inclReceiver) {
        return unbasic(frame.getArguments(), targetSig, inclReceiver);
    }

    static Object[] unbasic(Object[] arguments, InterpreterUnresolvedSignature targetSig, boolean inclReceiver) {
        int parameterCount = targetSig.getParameterCount(inclReceiver);
        Object[] res = new Object[parameterCount];
        int start = 0;
        if (inclReceiver) {
            res[start++] = arguments[0];
        }
        for (int i = start; i < parameterCount; i++) {
            JavaKind kind = targetSig.getParameterKind(i - start);
            res[i] = unbasic(arguments[i], kind);
        }
        return res;
    }

    static Object[] rebasic(Object[] arguments, InterpreterUnresolvedSignature srcSig, boolean inclReceiver) {
        int parameterCount = srcSig.getParameterCount(inclReceiver);
        Object[] res = new Object[parameterCount];
        int start = 0;
        if (inclReceiver) {
            res[start++] = arguments[0];
        }
        for (int i = start; i < parameterCount; i++) {
            JavaKind kind = srcSig.getParameterKind(i - start);
            res[i] = rebasic(arguments[i], kind);
        }
        return res;
    }

    /**
     * Convert ints to sub-words.
     */
    private static Object unbasic(Object arg, JavaKind kind) {
        return switch (kind) {
            case Boolean -> (int) arg != 0;
            case Byte -> (byte) (int) arg;
            case Char -> (char) (int) arg;
            case Short -> (short) (int) arg;
            default -> arg;
        };
    }

    /**
     * Convert sub-words to int.
     */
    static Object rebasic(Object value, JavaKind returnType) {
        return switch (returnType) {
            case Boolean -> ((boolean) value) ? 1 : 0;
            case Byte -> (int) (byte) value;
            case Short -> (int) (short) value;
            case Char -> (int) (char) value;
            case Int, Long, Float, Double, Object -> value;
            case Void -> null; // void
            default -> throw VMError.shouldNotReachHereAtRuntime();
        };
    }

    /**
     * Entry point for executing the ordinary bytecode body of an interpreted method.
     *
     * <p>
     * The loop keeps the current bytecode index and operand-stack top as local variables while the
     * {@link InterpreterFrame} stores locals, arguments, and stack slots. Each iteration reads the
     * current opcode, handles debugger events that must be reported at that bytecode index,
     * executes the bytecode, and then advances the bytecode index and stack top using the bytecode
     * metadata.
     *
     * <p>
     * Exceptions thrown by the guest Java code are wrapped by {@link SemanticJavaException} so they
     * can be routed to guest exception handlers. If such an exception needs to unwind the current
     * interpreter frame and be thrown to the caller, the {@link SemanticJavaException} is unwrapped
     * and {@link #executeBodyFromBCI} throws the unwrapped exception. Other throwables that reach
     * this loop are treated as interpreter implementation bugs unless they are VM errors that can
     * be thrown by normal Java execution, such as {@link OutOfMemoryError} or
     * {@link StackOverflowError}.
     *
     * <p>
     * This nested class is annotated separately because {@link InternalVMMethod} is not inherited
     * from {@link Interpreter}. Stack walks expose the reconstructed guest frame and hide this
     * physical root together with the threaded-handler methods declared below.
     */
    @InternalVMMethod
    public static final class Root {
        /**
         * Holds interpreter state that should be fully expanded in outlined bytecode handlers.
         */
        static final class ExpandedState {
            int top;

            ExpandedState(int top) {
                this.top = top;
            }
        }

        /**
         * Holds interpreter state that is shared across outlined bytecode handlers without full
         * expansion.
         */
        static final class State {
            final byte[] code;
            final InterpreterResolvedJavaMethod method;
            final MethodProfile methodProfile;
            final boolean forceStayInInterpreter;
            int debuggerEventFlags;
            int opcode;
            final int indent;

            State(byte[] code, InterpreterResolvedJavaMethod method, MethodProfile methodProfile, boolean forceStayInInterpreter, int debuggerEventFlags, int indent) {
                this.code = code;
                this.method = method;
                this.methodProfile = methodProfile;
                this.forceStayInInterpreter = forceStayInInterpreter;
                this.debuggerEventFlags = debuggerEventFlags;
                this.indent = indent;
                this.opcode = -1;
            }
        }

        @NeverInline("needed for stack walking")
        @BytecodeInterpreterHandlerConfig(maximumOperationCode = QUICK_PUTFIELD, arguments = {
                        @BytecodeInterpreterHandlerConfig.Argument(returnValue = true),
                        @BytecodeInterpreterHandlerConfig.Argument(expand = BytecodeInterpreterHandlerConfig.Argument.ExpansionKind.VIRTUAL),
                        @BytecodeInterpreterHandlerConfig.Argument(expand = BytecodeInterpreterHandlerConfig.Argument.ExpansionKind.MATERIALIZED, fields = {
                                        @BytecodeInterpreterHandlerConfig.Argument.Field(name = "code")
                        }),
                        @BytecodeInterpreterHandlerConfig.Argument(expand = BytecodeInterpreterHandlerConfig.Argument.ExpansionKind.MATERIALIZED, fields = {
                                        @BytecodeInterpreterHandlerConfig.Argument.Field(name = "primitives"),
                                        @BytecodeInterpreterHandlerConfig.Argument.Field(name = "references")
                        })
        })
        private static Object executeBodyFromBCI(InterpreterFrame frame, InterpreterResolvedJavaMethod method, int startBCI, int startTop,
                        boolean forceStayInInterpreter) {
            /*
             * SubstrateOptions.useRistretto() is a hosted @Fold switch. When Ristretto is disabled,
             * graph building sees the false branch below, initializes profiling to an inert
             * constant, and folds away the profile-entry and profile-site paths.
             */
            final MethodProfile methodProfile;
            if (SubstrateOptions.useRistretto()) {
                methodProfile = RistrettoProfileSupport.profileMethodEntry(method);
            } else {
                methodProfile = null;
            }

            int curBCI = startBCI;
            ExpandedState expandedState = new ExpandedState(startTop);
            byte[] code = method.getInterpretedCode();
            int debuggerEventFlags = 0;
            if (debuggerEventsSupported()) {
                DebuggerEvents debuggerEvents = DebuggerEvents.singleton();
                if (debuggerEvents.isEventEnabled(Thread.currentThread(), EventKind.METHOD_ENTRY) && method.getDeclaringClass().isMethodEnterEvent()) {
                    debuggerEventFlags |= EventKind.METHOD_ENTRY.getFlag();
                }
            }
            int indent = getLogIndent();
            State state = new State(code, method, methodProfile, forceStayInInterpreter, debuggerEventFlags, indent);

            InterpreterUtil.guarantee(code != null, "no bytecode stream for %s", method);

            traceInterpreterEnter(method, indent, curBCI, expandedState.top);
            prepareOpcodeForDispatch(curBCI, expandedState, state, frame);

            while (true) {
                int curOpcode = fetchOpcode(curBCI, expandedState, state, frame);

                try {
                    // @formatter:off
                    switch (GraalDirectives.markThreadedSwitch(curOpcode)) {
                        case NOP: curBCI = nopHandler(curBCI, expandedState, state, frame); break;
                        case ACONST_NULL: curBCI = aconstNullHandler(curBCI, expandedState, state, frame); break;

                        case ICONST_M1: curBCI = iconstM1Handler(curBCI, expandedState, state, frame); break;
                        case ICONST_0: curBCI = iconst0Handler(curBCI, expandedState, state, frame); break;
                        case ICONST_1: curBCI = iconst1Handler(curBCI, expandedState, state, frame); break;
                        case ICONST_2: curBCI = iconst2Handler(curBCI, expandedState, state, frame); break;
                        case ICONST_3: curBCI = iconst3Handler(curBCI, expandedState, state, frame); break;
                        case ICONST_4: curBCI = iconst4Handler(curBCI, expandedState, state, frame); break;
                        case ICONST_5: curBCI = iconst5Handler(curBCI, expandedState, state, frame); break;

                        case LCONST_0: curBCI = lconst0Handler(curBCI, expandedState, state, frame); break;
                        case LCONST_1: curBCI = lconst1Handler(curBCI, expandedState, state, frame); break;

                        case FCONST_0: curBCI = fconst0Handler(curBCI, expandedState, state, frame); break;
                        case FCONST_1: curBCI = fconst1Handler(curBCI, expandedState, state, frame); break;
                        case FCONST_2: curBCI = fconst2Handler(curBCI, expandedState, state, frame); break;

                        case DCONST_0: curBCI = dconst0Handler(curBCI, expandedState, state, frame); break;
                        case DCONST_1: curBCI = dconst1Handler(curBCI, expandedState, state, frame); break;

                        case BIPUSH: curBCI = bipushHandler(curBCI, expandedState, state, frame); break;
                        case SIPUSH: curBCI = sipushHandler(curBCI, expandedState, state, frame); break;

                        case LDC: curBCI = ldcHandler(curBCI, expandedState, state, frame); break;
                        case LDC_W: curBCI = ldcWHandler(curBCI, expandedState, state, frame); break;
                        case LDC2_W: curBCI = ldc2WHandler(curBCI, expandedState, state, frame); break;

                        case ILOAD: curBCI = iloadHandler(curBCI, expandedState, state, frame); break;
                        case LLOAD: curBCI = lloadHandler(curBCI, expandedState, state, frame); break;
                        case FLOAD: curBCI = floadHandler(curBCI, expandedState, state, frame); break;
                        case DLOAD: curBCI = dloadHandler(curBCI, expandedState, state, frame); break;
                        case ALOAD: curBCI = aloadHandler(curBCI, expandedState, state, frame); break;

                        case ILOAD_0: curBCI = iload0Handler(curBCI, expandedState, state, frame); break;
                        case ILOAD_1: curBCI = iload1Handler(curBCI, expandedState, state, frame); break;
                        case ILOAD_2: curBCI = iload2Handler(curBCI, expandedState, state, frame); break;
                        case ILOAD_3: curBCI = iload3Handler(curBCI, expandedState, state, frame); break;

                        case LLOAD_0: curBCI = lload0Handler(curBCI, expandedState, state, frame); break;
                        case LLOAD_1: curBCI = lload1Handler(curBCI, expandedState, state, frame); break;
                        case LLOAD_2: curBCI = lload2Handler(curBCI, expandedState, state, frame); break;
                        case LLOAD_3: curBCI = lload3Handler(curBCI, expandedState, state, frame); break;

                        case FLOAD_0: curBCI = fload0Handler(curBCI, expandedState, state, frame); break;
                        case FLOAD_1: curBCI = fload1Handler(curBCI, expandedState, state, frame); break;
                        case FLOAD_2: curBCI = fload2Handler(curBCI, expandedState, state, frame); break;
                        case FLOAD_3: curBCI = fload3Handler(curBCI, expandedState, state, frame); break;

                        case DLOAD_0: curBCI = dload0Handler(curBCI, expandedState, state, frame); break;
                        case DLOAD_1: curBCI = dload1Handler(curBCI, expandedState, state, frame); break;
                        case DLOAD_2: curBCI = dload2Handler(curBCI, expandedState, state, frame); break;
                        case DLOAD_3: curBCI = dload3Handler(curBCI, expandedState, state, frame); break;

                        case ALOAD_0: curBCI = aload0Handler(curBCI, expandedState, state, frame); break;
                        case ALOAD_1: curBCI = aload1Handler(curBCI, expandedState, state, frame); break;
                        case ALOAD_2: curBCI = aload2Handler(curBCI, expandedState, state, frame); break;
                        case ALOAD_3: curBCI = aload3Handler(curBCI, expandedState, state, frame); break;

                        case IALOAD: curBCI = ialoadHandler(curBCI, expandedState, state, frame); break;
                        case LALOAD: curBCI = laloadHandler(curBCI, expandedState, state, frame); break;
                        case FALOAD: curBCI = faloadHandler(curBCI, expandedState, state, frame); break;
                        case DALOAD: curBCI = daloadHandler(curBCI, expandedState, state, frame); break;
                        case BALOAD: curBCI = baloadHandler(curBCI, expandedState, state, frame); break;
                        case CALOAD: curBCI = caloadHandler(curBCI, expandedState, state, frame); break;
                        case SALOAD: curBCI = saloadHandler(curBCI, expandedState, state, frame); break;
                        case AALOAD: curBCI = aaloadHandler(curBCI, expandedState, state, frame); break;

                        case ISTORE: curBCI = istoreHandler(curBCI, expandedState, state, frame); break;
                        case LSTORE: curBCI = lstoreHandler(curBCI, expandedState, state, frame); break;
                        case FSTORE: curBCI = fstoreHandler(curBCI, expandedState, state, frame); break;
                        case DSTORE: curBCI = dstoreHandler(curBCI, expandedState, state, frame); break;
                        case ASTORE: curBCI = astoreHandler(curBCI, expandedState, state, frame); break;

                        case ISTORE_0: curBCI = istore0Handler(curBCI, expandedState, state, frame); break;
                        case ISTORE_1: curBCI = istore1Handler(curBCI, expandedState, state, frame); break;
                        case ISTORE_2: curBCI = istore2Handler(curBCI, expandedState, state, frame); break;
                        case ISTORE_3: curBCI = istore3Handler(curBCI, expandedState, state, frame); break;

                        case LSTORE_0: curBCI = lstore0Handler(curBCI, expandedState, state, frame); break;
                        case LSTORE_1: curBCI = lstore1Handler(curBCI, expandedState, state, frame); break;
                        case LSTORE_2: curBCI = lstore2Handler(curBCI, expandedState, state, frame); break;
                        case LSTORE_3: curBCI = lstore3Handler(curBCI, expandedState, state, frame); break;

                        case FSTORE_0: curBCI = fstore0Handler(curBCI, expandedState, state, frame); break;
                        case FSTORE_1: curBCI = fstore1Handler(curBCI, expandedState, state, frame); break;
                        case FSTORE_2: curBCI = fstore2Handler(curBCI, expandedState, state, frame); break;
                        case FSTORE_3: curBCI = fstore3Handler(curBCI, expandedState, state, frame); break;

                        case DSTORE_0: curBCI = dstore0Handler(curBCI, expandedState, state, frame); break;
                        case DSTORE_1: curBCI = dstore1Handler(curBCI, expandedState, state, frame); break;
                        case DSTORE_2: curBCI = dstore2Handler(curBCI, expandedState, state, frame); break;
                        case DSTORE_3: curBCI = dstore3Handler(curBCI, expandedState, state, frame); break;

                        case ASTORE_0: curBCI = astore0Handler(curBCI, expandedState, state, frame); break;
                        case ASTORE_1: curBCI = astore1Handler(curBCI, expandedState, state, frame); break;
                        case ASTORE_2: curBCI = astore2Handler(curBCI, expandedState, state, frame); break;
                        case ASTORE_3: curBCI = astore3Handler(curBCI, expandedState, state, frame); break;

                        case IASTORE: curBCI = iastoreHandler(curBCI, expandedState, state, frame); break;
                        case LASTORE: curBCI = lastoreHandler(curBCI, expandedState, state, frame); break;
                        case FASTORE: curBCI = fastoreHandler(curBCI, expandedState, state, frame); break;
                        case DASTORE: curBCI = dastoreHandler(curBCI, expandedState, state, frame); break;
                        case AASTORE: curBCI = aastoreHandler(curBCI, expandedState, state, frame); break;
                        case BASTORE: curBCI = bastoreHandler(curBCI, expandedState, state, frame); break;
                        case CASTORE: curBCI = castoreHandler(curBCI, expandedState, state, frame); break;
                        case SASTORE: curBCI = sastoreHandler(curBCI, expandedState, state, frame); break;

                        case POP2: curBCI = pop2Handler(curBCI, expandedState, state, frame); break;
                        case POP: curBCI = popHandler(curBCI, expandedState, state, frame); break;

                        case DUP: curBCI = dupHandler(curBCI, expandedState, state, frame); break;
                        case DUP_X1: curBCI = dupX1Handler(curBCI, expandedState, state, frame); break;
                        case DUP_X2: curBCI = dupX2Handler(curBCI, expandedState, state, frame); break;
                        case DUP2: curBCI = dup2Handler(curBCI, expandedState, state, frame); break;
                        case DUP2_X1: curBCI = dup2X1Handler(curBCI, expandedState, state, frame); break;
                        case DUP2_X2: curBCI = dup2X2Handler(curBCI, expandedState, state, frame); break;
                        case SWAP: curBCI = swapHandler(curBCI, expandedState, state, frame); break;

                        case IADD: curBCI = iaddHandler(curBCI, expandedState, state, frame); break;
                        case LADD: curBCI = laddHandler(curBCI, expandedState, state, frame); break;
                        case FADD: curBCI = faddHandler(curBCI, expandedState, state, frame); break;
                        case DADD: curBCI = daddHandler(curBCI, expandedState, state, frame); break;

                        case ISUB: curBCI = isubHandler(curBCI, expandedState, state, frame); break;
                        case LSUB: curBCI = lsubHandler(curBCI, expandedState, state, frame); break;
                        case FSUB: curBCI = fsubHandler(curBCI, expandedState, state, frame); break;
                        case DSUB: curBCI = dsubHandler(curBCI, expandedState, state, frame); break;

                        case IMUL: curBCI = imulHandler(curBCI, expandedState, state, frame); break;
                        case LMUL: curBCI = lmulHandler(curBCI, expandedState, state, frame); break;
                        case FMUL: curBCI = fmulHandler(curBCI, expandedState, state, frame); break;
                        case DMUL: curBCI = dmulHandler(curBCI, expandedState, state, frame); break;

                        case IDIV: curBCI = idivHandler(curBCI, expandedState, state, frame); break;
                        case LDIV: curBCI = ldivHandler(curBCI, expandedState, state, frame); break;
                        case FDIV: curBCI = fdivHandler(curBCI, expandedState, state, frame); break;
                        case DDIV: curBCI = ddivHandler(curBCI, expandedState, state, frame); break;

                        case IREM: curBCI = iremHandler(curBCI, expandedState, state, frame); break;
                        case LREM: curBCI = lremHandler(curBCI, expandedState, state, frame); break;
                        case FREM: curBCI = fremHandler(curBCI, expandedState, state, frame); break;
                        case DREM: curBCI = dremHandler(curBCI, expandedState, state, frame); break;

                        case INEG: curBCI = inegHandler(curBCI, expandedState, state, frame); break;
                        case LNEG: curBCI = lnegHandler(curBCI, expandedState, state, frame); break;
                        case FNEG: curBCI = fnegHandler(curBCI, expandedState, state, frame); break;
                        case DNEG: curBCI = dnegHandler(curBCI, expandedState, state, frame); break;

                        case ISHL: curBCI = ishlHandler(curBCI, expandedState, state, frame); break;
                        case LSHL: curBCI = lshlHandler(curBCI, expandedState, state, frame); break;
                        case ISHR: curBCI = ishrHandler(curBCI, expandedState, state, frame); break;
                        case LSHR: curBCI = lshrHandler(curBCI, expandedState, state, frame); break;
                        case IUSHR: curBCI = iushrHandler(curBCI, expandedState, state, frame); break;
                        case LUSHR: curBCI = lushrHandler(curBCI, expandedState, state, frame); break;

                        case IAND: curBCI = iandHandler(curBCI, expandedState, state, frame); break;
                        case LAND: curBCI = landHandler(curBCI, expandedState, state, frame); break;

                        case IOR: curBCI = iorHandler(curBCI, expandedState, state, frame); break;
                        case LOR: curBCI = lorHandler(curBCI, expandedState, state, frame); break;

                        case IXOR: curBCI = ixorHandler(curBCI, expandedState, state, frame); break;
                        case LXOR: curBCI = lxorHandler(curBCI, expandedState, state, frame); break;

                        case IINC: curBCI = iincHandler(curBCI, expandedState, state, frame); break;

                        case I2L: curBCI = i2lHandler(curBCI, expandedState, state, frame); break;
                        case I2F: curBCI = i2fHandler(curBCI, expandedState, state, frame); break;
                        case I2D: curBCI = i2dHandler(curBCI, expandedState, state, frame); break;

                        case L2I: curBCI = l2iHandler(curBCI, expandedState, state, frame); break;
                        case L2F: curBCI = l2fHandler(curBCI, expandedState, state, frame); break;
                        case L2D: curBCI = l2dHandler(curBCI, expandedState, state, frame); break;

                        case F2I: curBCI = f2iHandler(curBCI, expandedState, state, frame); break;
                        case F2L: curBCI = f2lHandler(curBCI, expandedState, state, frame); break;
                        case F2D: curBCI = f2dHandler(curBCI, expandedState, state, frame); break;

                        case D2I: curBCI = d2iHandler(curBCI, expandedState, state, frame); break;
                        case D2L: curBCI = d2lHandler(curBCI, expandedState, state, frame); break;
                        case D2F: curBCI = d2fHandler(curBCI, expandedState, state, frame); break;

                        case I2B: curBCI = i2bHandler(curBCI, expandedState, state, frame); break;
                        case I2C: curBCI = i2cHandler(curBCI, expandedState, state, frame); break;
                        case I2S: curBCI = i2sHandler(curBCI, expandedState, state, frame); break;

                        case LCMP: curBCI = lcmpHandler(curBCI, expandedState, state, frame); break;
                        case FCMPL: curBCI = fcmplHandler(curBCI, expandedState, state, frame); break;
                        case FCMPG: curBCI = fcmpgHandler(curBCI, expandedState, state, frame); break;
                        case DCMPL: curBCI = dcmplHandler(curBCI, expandedState, state, frame); break;
                        case DCMPG: curBCI = dcmpgHandler(curBCI, expandedState, state, frame); break;

                        // @formatter:on
                        case IFEQ:
                            curBCI = ifeqHandler(curBCI, expandedState, state, frame);
                            break;
                        case IFNE:
                            curBCI = ifneHandler(curBCI, expandedState, state, frame);
                            break;
                        case IFLT:
                            curBCI = ifltHandler(curBCI, expandedState, state, frame);
                            break;
                        case IFGE:
                            curBCI = ifgeHandler(curBCI, expandedState, state, frame);
                            break;
                        case IFGT:
                            curBCI = ifgtHandler(curBCI, expandedState, state, frame);
                            break;
                        case IFLE:
                            curBCI = ifleHandler(curBCI, expandedState, state, frame);
                            break;

                        case IF_ICMPEQ:
                            curBCI = ifIcmpeqHandler(curBCI, expandedState, state, frame);
                            break;
                        case IF_ICMPNE:
                            curBCI = ifIcmpneHandler(curBCI, expandedState, state, frame);
                            break;
                        case IF_ICMPLT:
                            curBCI = ifIcmpltHandler(curBCI, expandedState, state, frame);
                            break;
                        case IF_ICMPGE:
                            curBCI = ifIcmpgeHandler(curBCI, expandedState, state, frame);
                            break;
                        case IF_ICMPGT:
                            curBCI = ifIcmpgtHandler(curBCI, expandedState, state, frame);
                            break;
                        case IF_ICMPLE:
                            curBCI = ifIcmpleHandler(curBCI, expandedState, state, frame);
                            break;

                        case IF_ACMPEQ:
                            curBCI = ifAcmpeqHandler(curBCI, expandedState, state, frame);
                            break;
                        case IF_ACMPNE:
                            curBCI = ifAcmpneHandler(curBCI, expandedState, state, frame);
                            break;

                        case IFNULL:
                            curBCI = ifnullHandler(curBCI, expandedState, state, frame);
                            break;
                        case IFNONNULL:
                            curBCI = ifnonnullHandler(curBCI, expandedState, state, frame);
                            break;

                        case GOTO:
                            curBCI = gotoHandler(curBCI, expandedState, state, frame);
                            break;
                        case GOTO_W:
                            curBCI = gotoWHandler(curBCI, expandedState, state, frame);
                            break;

                        case JSR:
                            curBCI = jsrHandler(curBCI, expandedState, state, frame);
                            break;
                        case JSR_W:
                            curBCI = jsrWHandler(curBCI, expandedState, state, frame);
                            break;

                        case RET:
                            curBCI = retHandler(curBCI, expandedState, state, frame);
                            break;

                        case TABLESWITCH:
                            curBCI = tableswitchHandler(curBCI, expandedState, state, frame);
                            break;
                        case LOOKUPSWITCH:
                            curBCI = lookupswitchHandler(curBCI, expandedState, state, frame);
                            break;

                        case IRETURN: // fall through
                        case LRETURN: // fall through
                        case FRETURN: // fall through
                        case DRETURN: // fall through
                        case ARETURN: // fall through
                        case RETURN: {
                            Object returnValue = getReturnValueAsObject(frame, method, expandedState.top);
                            returnFromInterpreter(method, indent, curBCI, expandedState.top, returnValue);
                            return returnValue;
                        }
                        // @formatter:off
                        // Bytecodes order is shuffled.
                        case GETSTATIC      : curBCI = getstaticHandler(curBCI, expandedState, state, frame); break;
                        case GETFIELD       : curBCI = getfieldHandler(curBCI, expandedState, state, frame); break;
                        case PUTSTATIC      : curBCI = putstaticHandler(curBCI, expandedState, state, frame); break;
                        case PUTFIELD       : curBCI = putfieldHandler(curBCI, expandedState, state, frame); break;
                        case QUICK_GETSTATIC : curBCI = quickGetstaticHandler(curBCI, expandedState, state, frame); break;
                        case QUICK_GETFIELD  : curBCI = quickGetfieldHandler(curBCI, expandedState, state, frame); break;
                        case QUICK_PUTSTATIC : curBCI = quickPutstaticHandler(curBCI, expandedState, state, frame); break;
                        case QUICK_PUTFIELD  : curBCI = quickPutfieldHandler(curBCI, expandedState, state, frame); break;

                        case INVOKEVIRTUAL  : curBCI = invokevirtualHandler(curBCI, expandedState, state, frame); break;
                        case INVOKESPECIAL  : curBCI = invokespecialHandler(curBCI, expandedState, state, frame); break;
                        case INVOKESTATIC   : curBCI = invokestaticHandler(curBCI, expandedState, state, frame); break;
                        case INVOKEINTERFACE: curBCI = invokeinterfaceHandler(curBCI, expandedState, state, frame); break;
                        case INVOKEDYNAMIC  : curBCI = invokedynamicHandler(curBCI, expandedState, state, frame); break;

                        case NEW:
                            curBCI = newHandler(curBCI, expandedState, state, frame);
                            break;
                        case NEWARRAY:
                            curBCI = newarrayHandler(curBCI, expandedState, state, frame);
                            break;
                        case ANEWARRAY:
                            curBCI = anewarrayHandler(curBCI, expandedState, state, frame);
                            break;
                        case ARRAYLENGTH:
                            curBCI = arraylengthHandler(curBCI, expandedState, state, frame);
                            break;
                        case ATHROW:
                            curBCI = athrowHandler(curBCI, expandedState, state, frame);
                            break;

                        case CHECKCAST:
                            curBCI = checkcastHandler(curBCI, expandedState, state, frame);
                            break;
                        case INSTANCEOF:
                            curBCI = instanceofHandler(curBCI, expandedState, state, frame);
                            break;
                        case MONITORENTER: curBCI = monitorenterHandler(curBCI, expandedState, state, frame); break;
                        case MONITOREXIT: curBCI = monitorexitHandler(curBCI, expandedState, state, frame); break;

                        case WIDE:
                            curBCI = wideHandler(curBCI, expandedState, state, frame);
                            break;
                        // @formatter:on

                        case MULTIANEWARRAY:
                            curBCI = multianewarrayHandler(curBCI, expandedState, state, frame);
                            break;

                        default:
                            throw VMError.shouldNotReachHere(Bytecodes.nameOf(curOpcode));
                    }
                } catch (OSRReturn | OSRException e) {
                    /*
                     * Internal OSR control markers must bypass both bytecode exception dispatch and the
                     * generic host-exception guard below. The execute0 boundary unwraps them.
                     */
                    throw e;
                } catch (SemanticJavaException | OutOfMemoryError | StackOverflowError e) {
                    // Semantic Java exception thrown by interpreted code.
                    Throwable exception = e instanceof SemanticJavaException ? e.getCause() : e;
                    ExceptionHandler handler = resolveExceptionHandler(method, curBCI, exception);
                    if (handler != null) {
                        clearOperandStack(frame, method, expandedState.top);
                        expandedState.top = startingStackOffset(method.getMaxLocals());
                        putObject(frame, expandedState.top, exception);
                        expandedState.top++;
                        curBCI = beforeJumpChecks(methodProfile, method, frame, forceStayInInterpreter, curBCI, handler.getHandlerBCI(), expandedState.top);
                        prepareOpcodeForDispatch(curBCI, expandedState, state, frame);
                        continue;
                    } else {
                        traceInterpreterException(method, indent, curBCI, expandedState.top);
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

            } // loop
        }

        /**
         * Returns whether debugger event processing can be removed from the interpreter at image
         * build time.
         */
        @Fold
        static boolean debuggerEventsSupported() {
            return DebuggerEvents.singleton().supportsEvents();
        }

        /**
         * Prepares the bytecode at {@code curBCI} for dispatch when debugging or instruction
         * tracing is included in the image.
         *
         * <p>
         * This is the per-bytecode work that the non-threaded interpreter performs between entering
         * the dispatch loop and executing the switch. Threaded handlers can tail-call one another
         * without returning to that loop, so each outgoing handler performs this work after it has
         * established the next BCI and operand-stack state. The first bytecode is prepared before
         * entering the loop, and explicit control-flow and exception transitions prepare their
         * selected target in the same way. Consequently, every dispatched bytecode that requires
         * preparation is prepared exactly once and before its handler executes. In configurations
         * without debugging or tracing, this method folds to a no-op and
         * {@link #fetchOpcode(int, ExpandedState, State, InterpreterFrame)} reads the opcode
         * directly.
         *
         * <p>
         * Debugger preparation performs the opaque opcode read required for breakpoint
         * installation, processes single-step and breakpoint events, replaces
         * {@link Bytecodes#BREAKPOINT} with the original semantic opcode, delivers pending debugger
         * events, and stores the semantic opcode in {@link State#opcode}. Tracing-only
         * configurations do not store the opcode.
         */
        @AlwaysInline("Keep the interpreter fast path call-free")
        private static void prepareOpcodeForDispatch(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            boolean debuggerEventsSupported = debuggerEventsSupported();
            if (!debuggerEventsSupported && !InterpreterOptions.InterpreterTraceSupport.getValue()) {
                return;
            }

            int opcode = BytecodeStream.opaqueOpcode(state.code, curBCI);
            if (debuggerEventsSupported) {
                InterpreterResolvedJavaMethod method = state.method;
                int debuggerEventFlags = state.debuggerEventFlags;

                if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY,
                                DebuggerEvents.singleton().isEventEnabled(Thread.currentThread(), EventKind.SINGLE_STEP))) {
                    debuggerEventFlags = processSingleStepForDispatch(curBCI, method, debuggerEventFlags);
                }
                if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, opcode == BREAKPOINT)) {
                    long breakpointResult = processBreakpointForDispatch(curBCI, method, debuggerEventFlags);
                    opcode = (int) (breakpointResult >>> Integer.SIZE);
                    debuggerEventFlags = (int) breakpointResult;
                }
                if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, debuggerEventFlags != 0)) {
                    processDebuggerEventsForDispatch(curBCI, method, debuggerEventFlags, frame);
                    state.debuggerEventFlags = 0;
                }
            }
            if (InterpreterOptions.InterpreterTraceSupport.getValue()) {
                traceInterpreterInstruction(frame, state.indent, curBCI, expandedState.top, opcode);
            }
            if (debuggerEventsSupported) {
                state.opcode = opcode;
            }
        }

        /**
         * Adds a single-step event when the active stepping request applies at {@code curBCI}.
         */
        @NeverInline("dispatch preparation slow path")
        private static int processSingleStepForDispatch(int curBCI, InterpreterResolvedJavaMethod method, int initialDebuggerEventFlags) {
            int debuggerEventFlags = initialDebuggerEventFlags;
            Thread currentThread = Thread.currentThread();
            SteppingControl steppingControl = DebuggerEvents.singleton().getSteppingControl(currentThread);
            if (steppingControl != null && steppingControl.isActiveAtCurrentFrameDepth()) {
                int stepSize = steppingControl.getSize();
                if (stepSize == SteppingControl.STEP_MIN ||
                                (stepSize == SteppingControl.STEP_LINE && !steppingControl.withinSameLine(method, curBCI))) {
                    debuggerEventFlags |= EventKind.SINGLE_STEP.getFlag();
                }
            }
            return debuggerEventFlags;
        }

        /**
         * Resolves a breakpoint bytecode to its original opcode and adds a breakpoint event when
         * breakpoint reporting is enabled.
         *
         * @return the semantic opcode and updated event flags packed into one value
         */
        @NeverInline("dispatch preparation slow path")
        private static long processBreakpointForDispatch(int curBCI, InterpreterResolvedJavaMethod method, int initialDebuggerEventFlags) {
            int debuggerEventFlags = initialDebuggerEventFlags;
            if (DebuggerEvents.singleton().isEventEnabled(Thread.currentThread(), EventKind.BREAKPOINT)) {
                debuggerEventFlags |= EventKind.BREAKPOINT.getFlag();
            }
            return packDispatchPreparationResult(method.getOriginalOpcodeAt(curBCI), debuggerEventFlags);
        }

        /**
         * Delivers debugger events for the bytecode about to be dispatched. The enclosing handler
         * still carries the preceding BCI, so the event BCI is published for stack walking while
         * the callback is active.
         */
        @NeverInline("dispatch preparation slow path")
        private static void processDebuggerEventsForDispatch(int curBCI, InterpreterResolvedJavaMethod method, int debuggerEventFlags, InterpreterFrame frame) {
            // We have possibly: method enter, step before statement/expression, breakpoint
            frame.publishDebuggerEventBCI(curBCI);
            try {
                DebuggerEvents.singleton().getEventHandler().onEventAt(Thread.currentThread(), method, curBCI, null, debuggerEventFlags);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere("Debugger event handler failed", t);
            } finally {
                frame.clearDebuggerEventBCI();
            }
        }

        /** Packs the semantic opcode and debugger flags returned by the breakpoint slow path. */
        private static long packDispatchPreparationResult(int opcode, int debuggerEventFlags) {
            return (((long) opcode) << Integer.SIZE) | Integer.toUnsignedLong(debuggerEventFlags);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = NOP, safepoint = false)
        private static int nopHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            expandedState.top += ConstantBytecodes.stackEffectOf(NOP);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(NOP);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ACONST_NULL, safepoint = false)
        private static int aconstNullHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putObject(frame, expandedState.top, null);
            expandedState.top += ConstantBytecodes.stackEffectOf(ACONST_NULL);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ACONST_NULL);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        /** Returns the semantic opcode for the current BCI. */
        @SuppressWarnings("unused")
        @AlwaysInline("Keep semantic opcode replay on the fast path")
        @BytecodeInterpreterFetchOpcode
        private static int fetchOpcode(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            if (debuggerEventsSupported()) {
                /*
                 * Debugger preparation resolves BREAKPOINT to its original semantic opcode. Use
                 * that prepared value instead of reading the breakpoint opcode from the bytecode.
                 */
                return state.opcode;
            }
            // Without debugger support, the bytecode contains the semantic opcode directly.
            return BytecodeStream.opcode(state.code, curBCI);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_M1, safepoint = false)
        private static int iconstM1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, -1);
            expandedState.top += ConstantBytecodes.stackEffectOf(ICONST_M1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_M1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_0, safepoint = false)
        private static int iconst0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, 0);
            expandedState.top += ConstantBytecodes.stackEffectOf(ICONST_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_1, safepoint = false)
        private static int iconst1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, 1);
            expandedState.top += ConstantBytecodes.stackEffectOf(ICONST_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_2, safepoint = false)
        private static int iconst2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, 2);
            expandedState.top += ConstantBytecodes.stackEffectOf(ICONST_2);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_2);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_3, safepoint = false)
        private static int iconst3Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, 3);
            expandedState.top += ConstantBytecodes.stackEffectOf(ICONST_3);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_3);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_4, safepoint = false)
        private static int iconst4Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, 4);
            expandedState.top += ConstantBytecodes.stackEffectOf(ICONST_4);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_4);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_5, safepoint = false)
        private static int iconst5Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, 5);
            expandedState.top += ConstantBytecodes.stackEffectOf(ICONST_5);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_5);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LCONST_0, safepoint = false)
        private static int lconst0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top, 0L);
            expandedState.top += ConstantBytecodes.stackEffectOf(LCONST_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LCONST_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LCONST_1, safepoint = false)
        private static int lconst1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top, 1L);
            expandedState.top += ConstantBytecodes.stackEffectOf(LCONST_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LCONST_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FCONST_0, safepoint = false)
        private static int fconst0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top, 0.0f);
            expandedState.top += ConstantBytecodes.stackEffectOf(FCONST_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FCONST_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FCONST_1, safepoint = false)
        private static int fconst1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top, 1.0f);
            expandedState.top += ConstantBytecodes.stackEffectOf(FCONST_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FCONST_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FCONST_2, safepoint = false)
        private static int fconst2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top, 2.0f);
            expandedState.top += ConstantBytecodes.stackEffectOf(FCONST_2);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FCONST_2);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DCONST_0, safepoint = false)
        private static int dconst0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top, 0.0d);
            expandedState.top += ConstantBytecodes.stackEffectOf(DCONST_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(DCONST_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DCONST_1, safepoint = false)
        private static int dconst1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top, 1.0d);
            expandedState.top += ConstantBytecodes.stackEffectOf(DCONST_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(DCONST_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = BIPUSH, safepoint = false)
        private static int bipushHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, BytecodeStream.readByte(state.code, curBCI));
            expandedState.top += ConstantBytecodes.stackEffectOf(BIPUSH);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(BIPUSH);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = SIPUSH, safepoint = false)
        private static int sipushHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, BytecodeStream.readShort(state.code, curBCI));
            expandedState.top += ConstantBytecodes.stackEffectOf(SIPUSH);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(SIPUSH);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LDC, safepoint = false)
        private static int ldcHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            loadConstant(frame, state.method, expandedState.top, BytecodeStream.readCPI1(state.code, curBCI), LDC);
            expandedState.top += ConstantBytecodes.stackEffectOf(LDC);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LDC);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LDC_W, safepoint = false)
        private static int ldcWHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            loadConstant(frame, state.method, expandedState.top, BytecodeStream.readCPI2(state.code, curBCI), LDC_W);
            expandedState.top += ConstantBytecodes.stackEffectOf(LDC_W);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LDC_W);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LDC2_W, safepoint = false)
        private static int ldc2WHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            loadConstant(frame, state.method, expandedState.top, BytecodeStream.readCPI2(state.code, curBCI), LDC2_W);
            expandedState.top += ConstantBytecodes.stackEffectOf(LDC2_W);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LDC2_W);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ILOAD, safepoint = false)
        private static int iloadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, getLocalInt(frame, BytecodeStream.readLocalIndex1(state.code, curBCI)));
            expandedState.top += ConstantBytecodes.stackEffectOf(ILOAD);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ILOAD);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LLOAD, safepoint = false)
        private static int lloadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top, getLocalLong(frame, BytecodeStream.readLocalIndex1(state.code, curBCI)));
            expandedState.top += ConstantBytecodes.stackEffectOf(LLOAD);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LLOAD);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FLOAD, safepoint = false)
        private static int floadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top, getLocalFloat(frame, BytecodeStream.readLocalIndex1(state.code, curBCI)));
            expandedState.top += ConstantBytecodes.stackEffectOf(FLOAD);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FLOAD);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DLOAD, safepoint = false)
        private static int dloadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top, getLocalDouble(frame, BytecodeStream.readLocalIndex1(state.code, curBCI)));
            expandedState.top += ConstantBytecodes.stackEffectOf(DLOAD);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(DLOAD);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ALOAD, safepoint = false)
        private static int aloadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putObject(frame, expandedState.top, getLocalObject(frame, BytecodeStream.readLocalIndex1(state.code, curBCI)));
            expandedState.top += ConstantBytecodes.stackEffectOf(ALOAD);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ALOAD);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ILOAD_0, safepoint = false)
        private static int iload0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, getLocalInt(frame, 0));
            expandedState.top += ConstantBytecodes.stackEffectOf(ILOAD_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ILOAD_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ILOAD_1, safepoint = false)
        private static int iload1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, getLocalInt(frame, 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(ILOAD_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ILOAD_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ILOAD_2, safepoint = false)
        private static int iload2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, getLocalInt(frame, 2));
            expandedState.top += ConstantBytecodes.stackEffectOf(ILOAD_2);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ILOAD_2);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ILOAD_3, safepoint = false)
        private static int iload3Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top, getLocalInt(frame, 3));
            expandedState.top += ConstantBytecodes.stackEffectOf(ILOAD_3);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ILOAD_3);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LLOAD_0, safepoint = false)
        private static int lload0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top, getLocalLong(frame, 0));
            expandedState.top += ConstantBytecodes.stackEffectOf(LLOAD_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LLOAD_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LLOAD_1, safepoint = false)
        private static int lload1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top, getLocalLong(frame, 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(LLOAD_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LLOAD_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LLOAD_2, safepoint = false)
        private static int lload2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top, getLocalLong(frame, 2));
            expandedState.top += ConstantBytecodes.stackEffectOf(LLOAD_2);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LLOAD_2);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LLOAD_3, safepoint = false)
        private static int lload3Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top, getLocalLong(frame, 3));
            expandedState.top += ConstantBytecodes.stackEffectOf(LLOAD_3);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LLOAD_3);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FLOAD_0, safepoint = false)
        private static int fload0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top, getLocalFloat(frame, 0));
            expandedState.top += ConstantBytecodes.stackEffectOf(FLOAD_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FLOAD_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FLOAD_1, safepoint = false)
        private static int fload1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top, getLocalFloat(frame, 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(FLOAD_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FLOAD_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FLOAD_2, safepoint = false)
        private static int fload2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top, getLocalFloat(frame, 2));
            expandedState.top += ConstantBytecodes.stackEffectOf(FLOAD_2);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FLOAD_2);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FLOAD_3, safepoint = false)
        private static int fload3Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top, getLocalFloat(frame, 3));
            expandedState.top += ConstantBytecodes.stackEffectOf(FLOAD_3);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FLOAD_3);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DLOAD_0, safepoint = false)
        private static int dload0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top, getLocalDouble(frame, 0));
            expandedState.top += ConstantBytecodes.stackEffectOf(DLOAD_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(DLOAD_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DLOAD_1, safepoint = false)
        private static int dload1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top, getLocalDouble(frame, 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(DLOAD_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(DLOAD_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DLOAD_2, safepoint = false)
        private static int dload2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top, getLocalDouble(frame, 2));
            expandedState.top += ConstantBytecodes.stackEffectOf(DLOAD_2);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(DLOAD_2);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DLOAD_3, safepoint = false)
        private static int dload3Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top, getLocalDouble(frame, 3));
            expandedState.top += ConstantBytecodes.stackEffectOf(DLOAD_3);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(DLOAD_3);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ALOAD_0, safepoint = false)
        private static int aload0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putObject(frame, expandedState.top, getLocalObject(frame, 0));
            expandedState.top += ConstantBytecodes.stackEffectOf(ALOAD_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ALOAD_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ALOAD_1, safepoint = false)
        private static int aload1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putObject(frame, expandedState.top, getLocalObject(frame, 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(ALOAD_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ALOAD_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ALOAD_2, safepoint = false)
        private static int aload2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putObject(frame, expandedState.top, getLocalObject(frame, 2));
            expandedState.top += ConstantBytecodes.stackEffectOf(ALOAD_2);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ALOAD_2);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ALOAD_3, safepoint = false)
        private static int aload3Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putObject(frame, expandedState.top, getLocalObject(frame, 3));
            expandedState.top += ConstantBytecodes.stackEffectOf(ALOAD_3);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ALOAD_3);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISTORE, safepoint = false)
        private static int istoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalInt(frame, BytecodeStream.readLocalIndex1(state.code, curBCI), popInt(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(ISTORE);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ISTORE);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSTORE, safepoint = false)
        private static int lstoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalLong(frame, BytecodeStream.readLocalIndex1(state.code, curBCI), popLong(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(LSTORE);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LSTORE);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FSTORE, safepoint = false)
        private static int fstoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalFloat(frame, BytecodeStream.readLocalIndex1(state.code, curBCI), popFloat(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(FSTORE);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FSTORE);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DSTORE, safepoint = false)
        private static int dstoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalDouble(frame, BytecodeStream.readLocalIndex1(state.code, curBCI), popDouble(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(DSTORE);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(DSTORE);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ASTORE, safepoint = false)
        private static int astoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalObjectOrReturnAddress(frame, BytecodeStream.readLocalIndex1(state.code, curBCI), popReturnAddressOrObject(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(ASTORE);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ASTORE);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISTORE_0, safepoint = false)
        private static int istore0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalInt(frame, 0, popInt(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(ISTORE_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ISTORE_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISTORE_1, safepoint = false)
        private static int istore1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalInt(frame, 1, popInt(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(ISTORE_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ISTORE_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISTORE_2, safepoint = false)
        private static int istore2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalInt(frame, 2, popInt(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(ISTORE_2);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ISTORE_2);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISTORE_3, safepoint = false)
        private static int istore3Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalInt(frame, 3, popInt(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(ISTORE_3);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ISTORE_3);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSTORE_0, safepoint = false)
        private static int lstore0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalLong(frame, 0, popLong(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(LSTORE_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LSTORE_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSTORE_1, safepoint = false)
        private static int lstore1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalLong(frame, 1, popLong(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(LSTORE_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LSTORE_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSTORE_2, safepoint = false)
        private static int lstore2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalLong(frame, 2, popLong(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(LSTORE_2);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LSTORE_2);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSTORE_3, safepoint = false)
        private static int lstore3Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalLong(frame, 3, popLong(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(LSTORE_3);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(LSTORE_3);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FSTORE_0, safepoint = false)
        private static int fstore0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalFloat(frame, 0, popFloat(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(FSTORE_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FSTORE_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FSTORE_1, safepoint = false)
        private static int fstore1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalFloat(frame, 1, popFloat(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(FSTORE_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FSTORE_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FSTORE_2, safepoint = false)
        private static int fstore2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalFloat(frame, 2, popFloat(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(FSTORE_2);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FSTORE_2);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FSTORE_3, safepoint = false)
        private static int fstore3Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalFloat(frame, 3, popFloat(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(FSTORE_3);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(FSTORE_3);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DSTORE_0, safepoint = false)
        private static int dstore0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalDouble(frame, 0, popDouble(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(DSTORE_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(DSTORE_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DSTORE_1, safepoint = false)
        private static int dstore1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalDouble(frame, 1, popDouble(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(DSTORE_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(DSTORE_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DSTORE_2, safepoint = false)
        private static int dstore2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalDouble(frame, 2, popDouble(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(DSTORE_2);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(DSTORE_2);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DSTORE_3, safepoint = false)
        private static int dstore3Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalDouble(frame, 3, popDouble(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(DSTORE_3);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(DSTORE_3);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ASTORE_0, safepoint = false)
        private static int astore0Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalObjectOrReturnAddress(frame, 0, popReturnAddressOrObject(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(ASTORE_0);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ASTORE_0);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ASTORE_1, safepoint = false)
        private static int astore1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalObjectOrReturnAddress(frame, 1, popReturnAddressOrObject(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(ASTORE_1);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ASTORE_1);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ASTORE_2, safepoint = false)
        private static int astore2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalObjectOrReturnAddress(frame, 2, popReturnAddressOrObject(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(ASTORE_2);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ASTORE_2);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ASTORE_3, safepoint = false)
        private static int astore3Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalObjectOrReturnAddress(frame, 3, popReturnAddressOrObject(frame, expandedState.top - 1));
            expandedState.top += ConstantBytecodes.stackEffectOf(ASTORE_3);
            int nextBCI = curBCI + ConstantBytecodes.lengthOf(ASTORE_3);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IALOAD, safepoint = false)
        private static int ialoadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayLoad(frame, state.methodProfile, curBCI, expandedState.top, IALOAD);
            return advanceToNextBytecode(curBCI, IALOAD, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LALOAD, safepoint = false)
        private static int laloadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayLoad(frame, state.methodProfile, curBCI, expandedState.top, LALOAD);
            return advanceToNextBytecode(curBCI, LALOAD, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FALOAD, safepoint = false)
        private static int faloadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayLoad(frame, state.methodProfile, curBCI, expandedState.top, FALOAD);
            return advanceToNextBytecode(curBCI, FALOAD, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DALOAD, safepoint = false)
        private static int daloadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayLoad(frame, state.methodProfile, curBCI, expandedState.top, DALOAD);
            return advanceToNextBytecode(curBCI, DALOAD, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = AALOAD, safepoint = false)
        private static int aaloadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayLoad(frame, state.methodProfile, curBCI, expandedState.top, AALOAD);
            return advanceToNextBytecode(curBCI, AALOAD, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = BALOAD, safepoint = false)
        private static int baloadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayLoad(frame, state.methodProfile, curBCI, expandedState.top, BALOAD);
            return advanceToNextBytecode(curBCI, BALOAD, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = CALOAD, safepoint = false)
        private static int caloadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayLoad(frame, state.methodProfile, curBCI, expandedState.top, CALOAD);
            return advanceToNextBytecode(curBCI, CALOAD, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = SALOAD, safepoint = false)
        private static int saloadHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayLoad(frame, state.methodProfile, curBCI, expandedState.top, SALOAD);
            return advanceToNextBytecode(curBCI, SALOAD, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IASTORE, safepoint = false)
        private static int iastoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayStore(frame, state.methodProfile, curBCI, expandedState.top, IASTORE);
            return advanceToNextBytecode(curBCI, IASTORE, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LASTORE, safepoint = false)
        private static int lastoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayStore(frame, state.methodProfile, curBCI, expandedState.top, LASTORE);
            return advanceToNextBytecode(curBCI, LASTORE, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FASTORE, safepoint = false)
        private static int fastoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayStore(frame, state.methodProfile, curBCI, expandedState.top, FASTORE);
            return advanceToNextBytecode(curBCI, FASTORE, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DASTORE, safepoint = false)
        private static int dastoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayStore(frame, state.methodProfile, curBCI, expandedState.top, DASTORE);
            return advanceToNextBytecode(curBCI, DASTORE, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = AASTORE, safepoint = false)
        private static int aastoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayStore(frame, state.methodProfile, curBCI, expandedState.top, AASTORE);
            return advanceToNextBytecode(curBCI, AASTORE, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = BASTORE, safepoint = false)
        private static int bastoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayStore(frame, state.methodProfile, curBCI, expandedState.top, BASTORE);
            return advanceToNextBytecode(curBCI, BASTORE, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = CASTORE, safepoint = false)
        private static int castoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayStore(frame, state.methodProfile, curBCI, expandedState.top, CASTORE);
            return advanceToNextBytecode(curBCI, CASTORE, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = SASTORE, safepoint = false)
        private static int sastoreHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            arrayStore(frame, state.methodProfile, curBCI, expandedState.top, SASTORE);
            return advanceToNextBytecode(curBCI, SASTORE, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = POP2, safepoint = false)
        private static int pop2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            clear(frame, expandedState.top - 1);
            clear(frame, expandedState.top - 2);
            return advanceToNextBytecode(curBCI, POP2, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = POP, safepoint = false)
        private static int popHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            clear(frame, expandedState.top - 1);
            return advanceToNextBytecode(curBCI, POP, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DUP, safepoint = false)
        private static int dupHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            dup1(frame, expandedState.top);
            return advanceToNextBytecode(curBCI, DUP, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DUP_X1, safepoint = false)
        private static int dupX1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            dupx1(frame, expandedState.top);
            return advanceToNextBytecode(curBCI, DUP_X1, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DUP_X2, safepoint = false)
        private static int dupX2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            dupx2(frame, expandedState.top);
            return advanceToNextBytecode(curBCI, DUP_X2, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DUP2, safepoint = false)
        private static int dup2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            dup2(frame, expandedState.top);
            return advanceToNextBytecode(curBCI, DUP2, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DUP2_X1, safepoint = false)
        private static int dup2X1Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            dup2x1(frame, expandedState.top);
            return advanceToNextBytecode(curBCI, DUP2_X1, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DUP2_X2, safepoint = false)
        private static int dup2X2Handler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            dup2x2(frame, expandedState.top);
            return advanceToNextBytecode(curBCI, DUP2_X2, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = SWAP, safepoint = false)
        private static int swapHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            swapSingle(frame, expandedState.top);
            return advanceToNextBytecode(curBCI, SWAP, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IADD, safepoint = false)
        private static int iaddHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, popInt(frame, expandedState.top - 1) + popInt(frame, expandedState.top - 2));
            return advanceToNextBytecode(curBCI, IADD, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LADD, safepoint = false)
        private static int laddHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 4, popLong(frame, expandedState.top - 1) + popLong(frame, expandedState.top - 3));
            return advanceToNextBytecode(curBCI, LADD, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FADD, safepoint = false)
        private static int faddHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top - 2, popFloat(frame, expandedState.top - 1) + popFloat(frame, expandedState.top - 2));
            return advanceToNextBytecode(curBCI, FADD, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DADD, safepoint = false)
        private static int daddHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top - 4, popDouble(frame, expandedState.top - 1) + popDouble(frame, expandedState.top - 3));
            return advanceToNextBytecode(curBCI, DADD, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISUB, safepoint = false)
        private static int isubHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, popInt(frame, expandedState.top - 2) - popInt(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, ISUB, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSUB, safepoint = false)
        private static int lsubHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 4, popLong(frame, expandedState.top - 3) - popLong(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, LSUB, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FSUB, safepoint = false)
        private static int fsubHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top - 2, popFloat(frame, expandedState.top - 2) - popFloat(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, FSUB, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DSUB, safepoint = false)
        private static int dsubHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top - 4, popDouble(frame, expandedState.top - 3) - popDouble(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, DSUB, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IMUL, safepoint = false)
        private static int imulHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, popInt(frame, expandedState.top - 1) * popInt(frame, expandedState.top - 2));
            return advanceToNextBytecode(curBCI, IMUL, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LMUL, safepoint = false)
        private static int lmulHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 4, popLong(frame, expandedState.top - 1) * popLong(frame, expandedState.top - 3));
            return advanceToNextBytecode(curBCI, LMUL, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FMUL, safepoint = false)
        private static int fmulHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top - 2, popFloat(frame, expandedState.top - 1) * popFloat(frame, expandedState.top - 2));
            return advanceToNextBytecode(curBCI, FMUL, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DMUL, safepoint = false)
        private static int dmulHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top - 4, popDouble(frame, expandedState.top - 1) * popDouble(frame, expandedState.top - 3));
            return advanceToNextBytecode(curBCI, DMUL, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IDIV, safepoint = false)
        private static int idivHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, divInt(popInt(frame, expandedState.top - 1), popInt(frame, expandedState.top - 2)));
            return advanceToNextBytecode(curBCI, IDIV, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LDIV, safepoint = false)
        private static int ldivHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 4, divLong(popLong(frame, expandedState.top - 1), popLong(frame, expandedState.top - 3)));
            return advanceToNextBytecode(curBCI, LDIV, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FDIV, safepoint = false)
        private static int fdivHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top - 2, divFloat(popFloat(frame, expandedState.top - 1), popFloat(frame, expandedState.top - 2)));
            return advanceToNextBytecode(curBCI, FDIV, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DDIV, safepoint = false)
        private static int ddivHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top - 4, divDouble(popDouble(frame, expandedState.top - 1), popDouble(frame, expandedState.top - 3)));
            return advanceToNextBytecode(curBCI, DDIV, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IREM, safepoint = false)
        private static int iremHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, remInt(popInt(frame, expandedState.top - 1), popInt(frame, expandedState.top - 2)));
            return advanceToNextBytecode(curBCI, IREM, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LREM, safepoint = false)
        private static int lremHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 4, remLong(popLong(frame, expandedState.top - 1), popLong(frame, expandedState.top - 3)));
            return advanceToNextBytecode(curBCI, LREM, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FREM, safepoint = false)
        private static int fremHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top - 2, remFloat(popFloat(frame, expandedState.top - 1), popFloat(frame, expandedState.top - 2)));
            return advanceToNextBytecode(curBCI, FREM, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DREM, safepoint = false)
        private static int dremHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top - 4, remDouble(popDouble(frame, expandedState.top - 1), popDouble(frame, expandedState.top - 3)));
            return advanceToNextBytecode(curBCI, DREM, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INEG, safepoint = false)
        private static int inegHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 1, -popInt(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, INEG, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LNEG, safepoint = false)
        private static int lnegHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 2, -popLong(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, LNEG, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FNEG, safepoint = false)
        private static int fnegHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top - 1, -popFloat(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, FNEG, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DNEG, safepoint = false)
        private static int dnegHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top - 2, -popDouble(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, DNEG, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISHL, safepoint = false)
        private static int ishlHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, shiftLeftInt(popInt(frame, expandedState.top - 1), popInt(frame, expandedState.top - 2)));
            return advanceToNextBytecode(curBCI, ISHL, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSHL, safepoint = false)
        private static int lshlHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 3, shiftLeftLong(popInt(frame, expandedState.top - 1), popLong(frame, expandedState.top - 2)));
            return advanceToNextBytecode(curBCI, LSHL, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISHR, safepoint = false)
        private static int ishrHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, shiftRightSignedInt(popInt(frame, expandedState.top - 1), popInt(frame, expandedState.top - 2)));
            return advanceToNextBytecode(curBCI, ISHR, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSHR, safepoint = false)
        private static int lshrHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 3, shiftRightSignedLong(popInt(frame, expandedState.top - 1), popLong(frame, expandedState.top - 2)));
            return advanceToNextBytecode(curBCI, LSHR, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IUSHR, safepoint = false)
        private static int iushrHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, shiftRightUnsignedInt(popInt(frame, expandedState.top - 1), popInt(frame, expandedState.top - 2)));
            return advanceToNextBytecode(curBCI, IUSHR, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LUSHR, safepoint = false)
        private static int lushrHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 3, shiftRightUnsignedLong(popInt(frame, expandedState.top - 1), popLong(frame, expandedState.top - 2)));
            return advanceToNextBytecode(curBCI, LUSHR, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IAND, safepoint = false)
        private static int iandHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, popInt(frame, expandedState.top - 1) & popInt(frame, expandedState.top - 2));
            return advanceToNextBytecode(curBCI, IAND, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LAND, safepoint = false)
        private static int landHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 4, popLong(frame, expandedState.top - 1) & popLong(frame, expandedState.top - 3));
            return advanceToNextBytecode(curBCI, LAND, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IOR, safepoint = false)
        private static int iorHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, popInt(frame, expandedState.top - 1) | popInt(frame, expandedState.top - 2));
            return advanceToNextBytecode(curBCI, IOR, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LOR, safepoint = false)
        private static int lorHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 4, popLong(frame, expandedState.top - 1) | popLong(frame, expandedState.top - 3));
            return advanceToNextBytecode(curBCI, LOR, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IXOR, safepoint = false)
        private static int ixorHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, popInt(frame, expandedState.top - 1) ^ popInt(frame, expandedState.top - 2));
            return advanceToNextBytecode(curBCI, IXOR, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LXOR, safepoint = false)
        private static int lxorHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 4, popLong(frame, expandedState.top - 1) ^ popLong(frame, expandedState.top - 3));
            return advanceToNextBytecode(curBCI, LXOR, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IINC, safepoint = false)
        private static int iincHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            setLocalInt(frame, BytecodeStream.readLocalIndex1(state.code, curBCI),
                            getLocalInt(frame, BytecodeStream.readLocalIndex1(state.code, curBCI)) + BytecodeStream.readIncrement1(state.code, curBCI));
            return advanceToNextBytecode(curBCI, IINC, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = I2L, safepoint = false)
        private static int i2lHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 1, popInt(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, I2L, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = I2F, safepoint = false)
        private static int i2fHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top - 1, popInt(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, I2F, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = I2D, safepoint = false)
        private static int i2dHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top - 1, popInt(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, I2D, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = L2I, safepoint = false)
        private static int l2iHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, (int) popLong(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, L2I, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = L2F, safepoint = false)
        private static int l2fHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top - 2, popLong(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, L2F, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = L2D, safepoint = false)
        private static int l2dHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top - 2, popLong(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, L2D, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = F2I, safepoint = false)
        private static int f2iHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 1, (int) popFloat(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, F2I, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = F2L, safepoint = false)
        private static int f2lHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 1, (long) popFloat(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, F2L, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = F2D, safepoint = false)
        private static int f2dHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putDouble(frame, expandedState.top - 1, popFloat(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, F2D, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = D2I, safepoint = false)
        private static int d2iHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, (int) popDouble(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, D2I, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = D2L, safepoint = false)
        private static int d2lHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putLong(frame, expandedState.top - 2, (long) popDouble(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, D2L, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = D2F, safepoint = false)
        private static int d2fHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putFloat(frame, expandedState.top - 2, (float) popDouble(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, D2F, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = I2B, safepoint = false)
        private static int i2bHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 1, (byte) popInt(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, I2B, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = I2C, safepoint = false)
        private static int i2cHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 1, (char) popInt(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, I2C, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = I2S, safepoint = false)
        private static int i2sHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 1, (short) popInt(frame, expandedState.top - 1));
            return advanceToNextBytecode(curBCI, I2S, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LCMP, safepoint = false)
        private static int lcmpHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 4, compareLong(popLong(frame, expandedState.top - 1), popLong(frame, expandedState.top - 3)));
            return advanceToNextBytecode(curBCI, LCMP, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FCMPL, safepoint = false)
        private static int fcmplHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, compareFloatLess(popFloat(frame, expandedState.top - 1), popFloat(frame, expandedState.top - 2)));
            return advanceToNextBytecode(curBCI, FCMPL, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FCMPG, safepoint = false)
        private static int fcmpgHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 2, compareFloatGreater(popFloat(frame, expandedState.top - 1), popFloat(frame, expandedState.top - 2)));
            return advanceToNextBytecode(curBCI, FCMPG, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DCMPL, safepoint = false)
        private static int dcmplHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 4, compareDoubleLess(popDouble(frame, expandedState.top - 1), popDouble(frame, expandedState.top - 3)));
            return advanceToNextBytecode(curBCI, DCMPL, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DCMPG, safepoint = false)
        private static int dcmpgHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 4, compareDoubleGreater(popDouble(frame, expandedState.top - 1), popDouble(frame, expandedState.top - 3)));
            return advanceToNextBytecode(curBCI, DCMPG, expandedState, state, frame);
        }

        @AlwaysInline("Fold primitive branch opcode in individual handlers")
        private static int primitive1Branch(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame, int curOpcode) {
            boolean branchTaken = takeBranchPrimitive1(popInt(frame, expandedState.top - 1), curOpcode);
            profileBranch(state.methodProfile, curBCI, branchTaken);
            if (branchTaken) {
                expandedState.top += Bytecodes.stackEffectOf(curOpcode);
                return finishJump(curBCI, BytecodeStream.readBranchDest2(state.code, curBCI), expandedState, state, frame);
            }
            return advanceToNextBytecode(curBCI, curOpcode, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFEQ, safepoint = false)
        private static int ifeqHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return primitive1Branch(curBCI, expandedState, state, frame, IFEQ);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFNE, safepoint = false)
        private static int ifneHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return primitive1Branch(curBCI, expandedState, state, frame, IFNE);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFLT, safepoint = false)
        private static int ifltHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return primitive1Branch(curBCI, expandedState, state, frame, IFLT);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFGE, safepoint = false)
        private static int ifgeHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return primitive1Branch(curBCI, expandedState, state, frame, IFGE);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFGT, safepoint = false)
        private static int ifgtHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return primitive1Branch(curBCI, expandedState, state, frame, IFGT);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFLE, safepoint = false)
        private static int ifleHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return primitive1Branch(curBCI, expandedState, state, frame, IFLE);
        }

        @AlwaysInline("Fold primitive compare branch opcode in individual handlers")
        private static int primitive2Branch(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame, int curOpcode) {
            boolean branchTaken = takeBranchPrimitive2(popInt(frame, expandedState.top - 1), popInt(frame, expandedState.top - 2), curOpcode);
            profileBranch(state.methodProfile, curBCI, branchTaken);
            if (branchTaken) {
                expandedState.top += Bytecodes.stackEffectOf(curOpcode);
                return finishJump(curBCI, BytecodeStream.readBranchDest2(state.code, curBCI), expandedState, state, frame);
            }
            return advanceToNextBytecode(curBCI, curOpcode, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ICMPEQ, safepoint = false)
        private static int ifIcmpeqHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return primitive2Branch(curBCI, expandedState, state, frame, IF_ICMPEQ);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ICMPNE, safepoint = false)
        private static int ifIcmpneHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return primitive2Branch(curBCI, expandedState, state, frame, IF_ICMPNE);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ICMPLT, safepoint = false)
        private static int ifIcmpltHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return primitive2Branch(curBCI, expandedState, state, frame, IF_ICMPLT);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ICMPGE, safepoint = false)
        private static int ifIcmpgeHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return primitive2Branch(curBCI, expandedState, state, frame, IF_ICMPGE);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ICMPGT, safepoint = false)
        private static int ifIcmpgtHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return primitive2Branch(curBCI, expandedState, state, frame, IF_ICMPGT);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ICMPLE, safepoint = false)
        private static int ifIcmpleHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return primitive2Branch(curBCI, expandedState, state, frame, IF_ICMPLE);
        }

        @AlwaysInline("Fold reference branch opcode in individual handlers")
        private static int ref2Branch(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame, int curOpcode) {
            boolean branchTaken = takeBranchRef2(popObject(frame, expandedState.top - 1), popObject(frame, expandedState.top - 2), curOpcode);
            profileBranch(state.methodProfile, curBCI, branchTaken);
            if (branchTaken) {
                expandedState.top += Bytecodes.stackEffectOf(curOpcode);
                return finishJump(curBCI, BytecodeStream.readBranchDest2(state.code, curBCI), expandedState, state, frame);
            }
            return advanceToNextBytecode(curBCI, curOpcode, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ACMPEQ, safepoint = false)
        private static int ifAcmpeqHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return ref2Branch(curBCI, expandedState, state, frame, IF_ACMPEQ);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ACMPNE, safepoint = false)
        private static int ifAcmpneHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return ref2Branch(curBCI, expandedState, state, frame, IF_ACMPNE);
        }

        @AlwaysInline("Fold null branch opcode in individual handlers")
        private static int ref1Branch(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame, int curOpcode) {
            boolean branchTaken = takeBranchRef1(popObject(frame, expandedState.top - 1), curOpcode);
            profileBranch(state.methodProfile, curBCI, branchTaken);
            if (branchTaken) {
                expandedState.top += Bytecodes.stackEffectOf(curOpcode);
                return finishJump(curBCI, BytecodeStream.readBranchDest2(state.code, curBCI), expandedState, state, frame);
            }
            return advanceToNextBytecode(curBCI, curOpcode, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFNULL, safepoint = false)
        private static int ifnullHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return ref1Branch(curBCI, expandedState, state, frame, IFNULL);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFNONNULL, safepoint = false)
        private static int ifnonnullHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return ref1Branch(curBCI, expandedState, state, frame, IFNONNULL);
        }

        @AlwaysInline("Fold jump width in individual handlers")
        private static int gotoBytecode(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame, int curOpcode) {
            int targetBCI = curOpcode == GOTO ? BytecodeStream.readBranchDest2(state.code, curBCI) : BytecodeStream.readBranchDest4(state.code, curBCI);
            return finishJump(curBCI, targetBCI, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = GOTO, safepoint = false)
        private static int gotoHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return gotoBytecode(curBCI, expandedState, state, frame, GOTO);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = GOTO_W, safepoint = false)
        private static int gotoWHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return gotoBytecode(curBCI, expandedState, state, frame, GOTO_W);
        }

        @AlwaysInline("Fold JSR width in individual handlers")
        private static int jsrBytecode(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame, int curOpcode) {
            // JSR/JSR_W have an incorrect stack effect of 0 in the compiler sources.
            expandedState.top += 1;
            int targetBCI;
            if (curOpcode == JSR) {
                putReturnAddress(frame, expandedState.top - 1, curBCI + ConstantBytecodes.lengthOf(JSR));
                targetBCI = BytecodeStream.readBranchDest2(state.code, curBCI);
            } else {
                putReturnAddress(frame, expandedState.top - 1, curBCI + ConstantBytecodes.lengthOf(JSR_W));
                targetBCI = BytecodeStream.readBranchDest4(state.code, curBCI);
            }
            return finishJump(curBCI, targetBCI, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = JSR, safepoint = false)
        private static int jsrHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return jsrBytecode(curBCI, expandedState, state, frame, JSR);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = JSR_W, safepoint = false)
        private static int jsrWHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return jsrBytecode(curBCI, expandedState, state, frame, JSR_W);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = RET, safepoint = false)
        private static int retHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            expandedState.top += ConstantBytecodes.stackEffectOf(RET);
            return finishJump(curBCI, getLocalReturnAddress(frame, BytecodeStream.readLocalIndex1(state.code, curBCI)), expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = TABLESWITCH, safepoint = false)
        private static int tableswitchHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            int index = popInt(frame, expandedState.top - 1);
            int low = TableSwitch.lowKey(state.code, curBCI);
            int high = TableSwitch.highKey(state.code, curBCI);
            assert low <= high;

            int targetBCI;
            if (low <= index && index <= high) {
                targetBCI = TableSwitch.targetAt(state.code, curBCI, index - low);
            } else {
                targetBCI = TableSwitch.defaultTarget(state.code, curBCI);
            }
            expandedState.top += ConstantBytecodes.stackEffectOf(TABLESWITCH);
            return finishJump(curBCI, targetBCI, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LOOKUPSWITCH, safepoint = false)
        private static int lookupswitchHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            int key = popInt(frame, expandedState.top - 1);
            int low = 0;
            int high = LookupSwitch.numberOfCases(state.code, curBCI) - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int midVal = LookupSwitch.keyAt(state.code, curBCI, mid);
                if (midVal < key) {
                    low = mid + 1;
                } else if (midVal > key) {
                    high = mid - 1;
                } else {
                    expandedState.top += ConstantBytecodes.stackEffectOf(LOOKUPSWITCH);
                    return finishJump(curBCI, curBCI + LookupSwitch.offsetAt(state.code, curBCI, mid), expandedState, state, frame);
                }
            }

            expandedState.top += ConstantBytecodes.stackEffectOf(LOOKUPSWITCH);
            return finishJump(curBCI, LookupSwitch.defaultTarget(state.code, curBCI), expandedState, state, frame);
        }

        @AlwaysInline("Fold field get opcode in individual handlers")
        private static int fieldGetBytecode(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame, int curOpcode) {
            expandedState.top += getField(frame, expandedState.top, resolveField(state.method, curOpcode, state.code, curBCI), curOpcode);
            return advanceToNextBytecode(curBCI, curOpcode, expandedState, state, frame);
        }

        @AlwaysInline("Fold quickened field get opcode in individual handlers")
        private static int quickenedFieldGetBytecode(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame, int quickOpcode, int originalOpcode) {
            expandedState.top += getField(frame, expandedState.top, resolveQuickenedField(state.method, originalOpcode, BytecodeStream.readCPI2(state.code, curBCI)), originalOpcode);
            return advanceToNextBytecode(curBCI, quickOpcode, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = GETSTATIC)
        private static int getstaticHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return fieldGetBytecode(curBCI, expandedState, state, frame, GETSTATIC);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = GETFIELD)
        private static int getfieldHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return fieldGetBytecode(curBCI, expandedState, state, frame, GETFIELD);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = QUICK_GETSTATIC)
        private static int quickGetstaticHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return quickenedFieldGetBytecode(curBCI, expandedState, state, frame, QUICK_GETSTATIC, GETSTATIC);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = QUICK_GETFIELD)
        private static int quickGetfieldHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return quickenedFieldGetBytecode(curBCI, expandedState, state, frame, QUICK_GETFIELD, GETFIELD);
        }

        @AlwaysInline("Fold field put opcode in individual handlers")
        private static int fieldPutBytecode(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame, int curOpcode) {
            expandedState.top += putField(frame, expandedState.top, resolveField(state.method, curOpcode, state.code, curBCI), curOpcode);
            return advanceToNextBytecode(curBCI, curOpcode, expandedState, state, frame);
        }

        @AlwaysInline("Fold quickened field put opcode in individual handlers")
        private static int quickenedFieldPutBytecode(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame, int quickOpcode, int originalOpcode) {
            expandedState.top += putField(frame, expandedState.top, resolveQuickenedField(state.method, originalOpcode, BytecodeStream.readCPI2(state.code, curBCI)), originalOpcode);
            return advanceToNextBytecode(curBCI, quickOpcode, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = PUTSTATIC)
        private static int putstaticHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return fieldPutBytecode(curBCI, expandedState, state, frame, PUTSTATIC);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = PUTFIELD)
        private static int putfieldHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return fieldPutBytecode(curBCI, expandedState, state, frame, PUTFIELD);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = QUICK_PUTSTATIC)
        private static int quickPutstaticHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return quickenedFieldPutBytecode(curBCI, expandedState, state, frame, QUICK_PUTSTATIC, PUTSTATIC);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = QUICK_PUTFIELD)
        private static int quickPutfieldHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return quickenedFieldPutBytecode(curBCI, expandedState, state, frame, QUICK_PUTFIELD, PUTFIELD);
        }

        @AlwaysInline("Fold invoke opcode in individual handlers")
        private static int invokeBytecode(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame, int curOpcode) {
            boolean preferStayInInterpreter = state.forceStayInInterpreter;
            SteppingControl steppingControl = null;
            boolean stepEventDisabled = false;
            if (debuggerEventsSupported()) {
                Thread currentThread = Thread.currentThread();
                if (DebuggerEvents.singleton().isEventEnabled(currentThread, EventKind.SINGLE_STEP)) {
                    // Disable stepping for inner frames, except for step into, where we must force
                    // interpreter execution.
                    steppingControl = DebuggerEvents.singleton().getSteppingControl(currentThread);
                    if (steppingControl != null) {
                        steppingControl.pushFrame();
                        if (!steppingControl.isActiveAtCurrentFrameDepth()) {
                            DebuggerEvents.singleton().setEventEnabled(currentThread, EventKind.SINGLE_STEP, false);
                            stepEventDisabled = true;
                        }
                        if (steppingControl.getDepth() == SteppingControl.STEP_INTO) {
                            // For now force the callee to stay in interpreter.
                            preferStayInInterpreter = true;
                        }
                    }
                }
            }

            try {
                expandedState.top += invoke(frame, state.methodProfile, state.method, state.code, expandedState.top, curBCI, curOpcode, state.forceStayInInterpreter, preferStayInInterpreter);
            } finally {
                if (debuggerEventsSupported()) {
                    Thread currentThread = Thread.currentThread();
                    SteppingControl newSteppingControl = DebuggerEvents.singleton().getSteppingControl(currentThread);
                    if (newSteppingControl != null) {
                        if (DebuggerEvents.singleton().isEventEnabled(currentThread, EventKind.SINGLE_STEP)) {
                            newSteppingControl.popFrame();
                        } else if (steppingControl == newSteppingControl && stepEventDisabled) {
                            // Re-enable stepping events that could have been disabled by step
                            // outer/out into inner frames.
                            DebuggerEvents.singleton().setEventEnabled(currentThread, EventKind.SINGLE_STEP, true);
                            newSteppingControl.popFrame();
                        }
                    }
                }
            }
            return advanceToNextBytecode(curBCI, curOpcode, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INVOKEVIRTUAL)
        private static int invokevirtualHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return invokeBytecode(curBCI, expandedState, state, frame, INVOKEVIRTUAL);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INVOKESPECIAL)
        private static int invokespecialHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return invokeBytecode(curBCI, expandedState, state, frame, INVOKESPECIAL);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INVOKESTATIC)
        private static int invokestaticHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return invokeBytecode(curBCI, expandedState, state, frame, INVOKESTATIC);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INVOKEINTERFACE)
        private static int invokeinterfaceHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return invokeBytecode(curBCI, expandedState, state, frame, INVOKEINTERFACE);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INVOKEDYNAMIC)
        private static int invokedynamicHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return invokeBytecode(curBCI, expandedState, state, frame, INVOKEDYNAMIC);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = NEW)
        private static int newHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putObject(frame, expandedState.top, InterpreterToVM.createNewReference(resolveType(state.method, NEW, BytecodeStream.readCPI2(state.code, curBCI))));
            return advanceToNextBytecode(curBCI, NEW, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = NEWARRAY)
        private static int newarrayHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putObject(frame, expandedState.top - 1, InterpreterToVM.createNewPrimitiveArray(BytecodeStream.readByte(state.code, curBCI), popInt(frame, expandedState.top - 1)));
            return advanceToNextBytecode(curBCI, NEWARRAY, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ANEWARRAY)
        private static int anewarrayHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putObject(frame, expandedState.top - 1,
                            InterpreterToVM.createNewReferenceArray(resolveType(state.method, ANEWARRAY, BytecodeStream.readCPI2(state.code, curBCI)), popInt(frame, expandedState.top - 1)));
            return advanceToNextBytecode(curBCI, ANEWARRAY, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ARRAYLENGTH, safepoint = false)
        private static int arraylengthHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            putInt(frame, expandedState.top - 1, InterpreterToVM.arrayLength(nullCheck(popObject(frame, expandedState.top - 1))));
            return advanceToNextBytecode(curBCI, ARRAYLENGTH, expandedState, state, frame);
        }

        @SuppressWarnings("unused")
        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ATHROW)
        private static int athrowHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            throw SemanticJavaException.raise((Throwable) nullCheck(popObject(frame, expandedState.top - 1)));
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = CHECKCAST)
        private static int checkcastHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            Object receiver = peekObject(frame, expandedState.top - 1);
            profileType(state.methodProfile, curBCI, receiver);
            if (receiver != null) {
                InterpreterToVM.checkCast(receiver, resolveType(state.method, CHECKCAST, BytecodeStream.readCPI2(state.code, curBCI)));
            }
            return advanceToNextBytecode(curBCI, CHECKCAST, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INSTANCEOF)
        private static int instanceofHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            Object receiver = popObject(frame, expandedState.top - 1);
            profileType(state.methodProfile, curBCI, receiver);
            putInt(frame, expandedState.top - 1,
                            (receiver != null && InterpreterToVM.instanceOf(receiver, resolveType(state.method, INSTANCEOF, BytecodeStream.readCPI2(state.code, curBCI)))) ? 1 : 0);
            return advanceToNextBytecode(curBCI, INSTANCEOF, expandedState, state, frame);
        }

        @AlwaysInline("Fold monitor opcode in individual handlers")
        private static int monitorBytecode(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame, int curOpcode) {
            Object receiver = nullCheck(popObject(frame, expandedState.top - 1));
            if (curOpcode == MONITORENTER) {
                InterpreterToVM.monitorEnter(frame, receiver);
            } else {
                InterpreterToVM.monitorExit(frame, receiver);
            }
            return advanceToNextBytecode(curBCI, curOpcode, expandedState, state, frame);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = MONITORENTER)
        private static int monitorenterHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return monitorBytecode(curBCI, expandedState, state, frame, MONITORENTER);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = MONITOREXIT)
        private static int monitorexitHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            return monitorBytecode(curBCI, expandedState, state, frame, MONITOREXIT);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = WIDE, safepoint = false)
        private static int wideHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            int wideOpcode = BytecodeStream.opcode(state.code, curBCI + 1);
            switch (wideOpcode) {
                case ILOAD -> putInt(frame, expandedState.top, getLocalInt(frame, BytecodeStream.readLocalIndex2(state.code, curBCI)));
                case LLOAD -> putLong(frame, expandedState.top, getLocalLong(frame, BytecodeStream.readLocalIndex2(state.code, curBCI)));
                case FLOAD -> putFloat(frame, expandedState.top, getLocalFloat(frame, BytecodeStream.readLocalIndex2(state.code, curBCI)));
                case DLOAD -> putDouble(frame, expandedState.top, getLocalDouble(frame, BytecodeStream.readLocalIndex2(state.code, curBCI)));
                case ALOAD -> putObject(frame, expandedState.top, getLocalObject(frame, BytecodeStream.readLocalIndex2(state.code, curBCI)));

                case ISTORE -> setLocalInt(frame, BytecodeStream.readLocalIndex2(state.code, curBCI), popInt(frame, expandedState.top - 1));
                case LSTORE -> setLocalLong(frame, BytecodeStream.readLocalIndex2(state.code, curBCI), popLong(frame, expandedState.top - 1));
                case FSTORE -> setLocalFloat(frame, BytecodeStream.readLocalIndex2(state.code, curBCI), popFloat(frame, expandedState.top - 1));
                case DSTORE -> setLocalDouble(frame, BytecodeStream.readLocalIndex2(state.code, curBCI), popDouble(frame, expandedState.top - 1));
                case ASTORE -> setLocalObjectOrReturnAddress(frame, BytecodeStream.readLocalIndex2(state.code, curBCI), popReturnAddressOrObject(frame, expandedState.top - 1));
                case IINC -> setLocalInt(frame, BytecodeStream.readLocalIndex2(state.code, curBCI),
                                getLocalInt(frame, BytecodeStream.readLocalIndex2(state.code, curBCI)) + BytecodeStream.readIncrement2(state.code, curBCI));
                case RET -> {
                    expandedState.top += ConstantBytecodes.stackEffectOf(RET);
                    return finishJump(curBCI, getLocalReturnAddress(frame, BytecodeStream.readLocalIndex2(state.code, curBCI)), expandedState, state, frame);
                }
                default -> throw VMError.shouldNotReachHere(Bytecodes.nameOf(wideOpcode));
            }
            expandedState.top += Bytecodes.stackEffectOf(wideOpcode);
            int nextBCI = curBCI + ((wideOpcode == IINC) ? 6 : 4);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = MULTIANEWARRAY)
        private static int multianewarrayHandler(int curBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            expandedState.top += allocateMultiArray(frame, expandedState.top, resolveType(state.method, MULTIANEWARRAY, BytecodeStream.readCPI2(state.code, curBCI)),
                            BytecodeStream.readUByte(state.code, curBCI + 3));
            return advanceToNextBytecode(curBCI, MULTIANEWARRAY, expandedState, state, frame);
        }

        /**
         * Completes a bytecode that transfers control to {@code targetBCI}. The caller must apply
         * the bytecode's stack effect before invoking this helper. This performs the profiling,
         * safepoint, and OSR checks associated with the transfer and prepares the selected target
         * opcode for dispatch.
         *
         * @return the checked target BCI
         */
        @AlwaysInline("Keep branch completion on the fast path")
        private static int finishJump(int curBCI, int targetBCI, ExpandedState expandedState, State state, InterpreterFrame frame) {
            int nextBCI = beforeJumpChecks(state.methodProfile, state.method, frame, state.forceStayInInterpreter, curBCI, targetBCI, expandedState.top);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }

        /**
         * Completes a bytecode that continues at its sequential successor. This applies the
         * bytecode's stack effect to the interpreter operand-stack pointer, advances the BCI by the
         * encoded bytecode length, and prepares the opcode at the resulting BCI for dispatch.
         *
         * <p>
         * This helper is only suitable when {@link Bytecodes#stackEffectOf(int)} and
         * {@link Bytecodes#lengthOf(int)} describe the complete transition to the next bytecode.
         * Branches and other bytecodes with a separately selected successor must prepare that
         * target explicitly.
         *
         * @return the BCI of the prepared successor bytecode
         */
        @AlwaysInline("Keep common opcode completion on the fast path")
        private static int advanceToNextBytecode(int curBCI, int curOpcode, ExpandedState expandedState, State state, InterpreterFrame frame) {
            expandedState.top += Bytecodes.stackEffectOf(curOpcode);
            int nextBCI = curBCI + Bytecodes.lengthOf(curOpcode);
            prepareOpcodeForDispatch(nextBCI, expandedState, state, frame);
            return nextBCI;
        }
    }

    @AlwaysInline("Profile-site guards must fold away when Ristretto is disabled in the hosted image.")
    private static void profileType(MethodProfile methodProfile, int bci, Object o) {
        if (SubstrateOptions.useRistretto() && methodProfile != null) {
            methodProfile.profileReceiver(bci, o);
        }
    }

    @AlwaysInline("Profile-site guards must fold away when Ristretto is disabled in the hosted image.")
    private static void profileBranch(MethodProfile methodProfile, int curBCI, boolean branchTaken1) {
        if (SubstrateOptions.useRistretto() && methodProfile != null) {
            methodProfile.profileBranch(curBCI, branchTaken1);
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

    public static void clearOperandStack(InterpreterFrame frame, InterpreterResolvedJavaMethod method, int top) {
        int stackStart = startingStackOffset(method.getMaxLocals());
        for (int slot = top - 1; slot >= stackStart; --slot) {
            clear(frame, slot);
        }
    }

    private static boolean takeBranchRef1(Object operand, int opcode) {
        assert IFNULL <= opcode && opcode <= IFNONNULL : Bytecodes.nameOf(opcode);
        return switch (opcode) {
            case IFNULL -> operand == null;
            case IFNONNULL -> operand != null;
            default -> throw VMError.shouldNotReachHereAtRuntime();
        };
    }

    private static boolean takeBranchPrimitive1(int operand, int opcode) {
        assert IFEQ <= opcode && opcode <= IFLE : Bytecodes.nameOf(opcode);
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
        assert IF_ICMPEQ <= opcode && opcode <= IF_ICMPLE : Bytecodes.nameOf(opcode);
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
        assert IF_ACMPEQ <= opcode && opcode <= IF_ACMPNE : Bytecodes.nameOf(opcode);
        return switch (opcode) {
            case IF_ACMPEQ -> operand1 == operand2;
            case IF_ACMPNE -> operand1 != operand2;
            default -> throw VMError.shouldNotReachHereAtRuntime();
        };
    }

    @AlwaysInline("Fold array load opcode in individual handlers")
    private static void arrayLoad(InterpreterFrame frame, MethodProfile methodProfile, int bci, int top, int loadOpcode) {
        assert IALOAD <= loadOpcode && loadOpcode <= SALOAD : Bytecodes.nameOf(loadOpcode);
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
            case AALOAD -> {
                Object o = InterpreterToVM.getArrayObject(index, (Object[]) array);
                profileType(methodProfile, bci, o);
                putObject(frame, top - 2, o);
            }
            default -> throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    @AlwaysInline("Fold array store opcode in individual handlers")
    private static void arrayStore(InterpreterFrame frame, MethodProfile methodProfile, int bci, int top, int storeOpcode) {
        assert IASTORE <= storeOpcode && storeOpcode <= SASTORE : Bytecodes.nameOf(storeOpcode);
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
            case AASTORE -> {
                Object o = popObject(frame, top - 1);
                profileType(methodProfile, bci, o);
                InterpreterToVM.setArrayObject(o, index, (Object[]) array);
            }
            default -> throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    /**
     * Performs the checks that must run before control leaves the current bytecode for another BCI.
     *
     * <pre>
     * if targetBCI is a backward branch:
     *     if the caller allows runtime compilation:
     *         update the per-target OSR backedge state
     *         submit or enter OSR-compiled code when its threshold has been reached
     * return targetBCI
     * </pre>
     *
     * The compatibility overload below is used by callers that only need the target BCI and must stay
     * in the interpreter.
     */
    @SuppressWarnings("unused")
    private static int beforeJumpChecks(MethodProfile methodProfile, InterpreterResolvedJavaMethod method, InterpreterFrame frame, boolean forceStayInInterpreter, int curBCI, int targetBCI, int top) {
        if (targetBCI <= curBCI) {
            GraalDirectives.safepoint();
            if (SubstrateOptions.useRistretto() && !forceStayInInterpreter) {
                OSRResult result = RistrettoOSRSupport.tryOSR(method, methodProfile, frame, targetBCI, top);
                if (result != null) {
                    if (result.exception() != null) {
                        throw new OSRException(result.exception());
                    }
                    throw new OSRReturn(result.value());
                }
            }
        }
        return targetBCI;
    }

    /**
     * Internal control-transfer marker used when OSR compiled code throws out of the compiled
     * continuation.
     *
     * The throwing bytecode executed in compiled code, not at the interpreter backedge that initiated
     * OSR. The exception must therefore bypass bytecode exception dispatch in the old interpreter
     * frame; dispatching it against the old backedge BCI can match the wrong in-method handler.
     */
    private static final class OSRException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Throwable exception;

        private OSRException(Throwable exception) {
            this.exception = exception;
        }

        private Throwable exception() {
            return exception;
        }

        @Override
        @SuppressWarnings("sync-override")
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * Internal control-transfer marker used after OSR compiled code has produced the method result.
     *
     * The compiled OSR entry returns to the Java interpreter frame that initiated OSR. At that point
     * the interpreter must leave its bytecode dispatch loop immediately and return the compiled result
     * to its caller. The
     * existing dispatch helpers only return the next BCI, so this marker bubbles the result to the
     * {@code execute0} boundary without being treated as a guest Java exception.
     */
    @SuppressWarnings("serial")
    private static final class OSRReturn extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Object result;

        private OSRReturn(Object result) {
            this.result = result;
        }

        private Object result() {
            return result;
        }

        @Override
        @SuppressWarnings("sync-override")
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * Internal carrier for a compiled OSR continuation's logical Java outcome.
     *
     * The implementation-specific OSR support owns the transfer state and compiled entry call, but
     * the interpreter owns the control-flow markers that leave the old bytecode dispatch frame.
     * Keeping this result type here keeps that ownership boundary explicit.
     */
    public static final class OSRResult {
        private final Object value;
        private final Throwable exception;

        private OSRResult(Object value, Throwable exception) {
            this.value = value;
            this.exception = exception;
        }

        public static OSRResult forValue(Object value) {
            return new OSRResult(value, null);
        }

        public static OSRResult forException(Throwable exception) {
            return new OSRResult(null, exception);
        }

        public Object value() {
            return value;
        }

        public Throwable exception() {
            return exception;
        }

    }

    public static int beforeJumpChecks(InterpreterFrame frame, int curBCI, int targetBCI, int top) {
        return beforeJumpChecks(null, null, frame, true, curBCI, targetBCI, top);
    }

    public static ExceptionHandler resolveExceptionHandler(InterpreterResolvedJavaMethod method, int bci, Throwable ex) {
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
        if (opcode == LDC2_W) {
            VMError.guarantee(cpi != 0);
            InterpreterConstantPool pool = getConstantPool(method);
            ConstantPool.Tag tag = pool.tagAt(cpi);
            switch (tag) {
                case LONG -> putLong(frame, top, pool.longAt(cpi));
                case DOUBLE -> putDouble(frame, top, pool.doubleAt(cpi));
                default -> resolveConstantAtSlowPath(frame, method, top, cpi, opcode, pool, tag);
            }
        } else {
            assert opcode == LDC || opcode == LDC_W;
            if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, cpi == 0)) {
                throw noClassDefFoundError(opcode, null);
            }
            InterpreterConstantPool pool = getConstantPool(method);
            ConstantPool.Tag tag = pool.tagAt(cpi);
            switch (tag) {
                case INTEGER -> putInt(frame, top, pool.intAt(cpi));
                case FLOAT -> putFloat(frame, top, pool.floatAt(cpi));
                default -> resolveConstantAtSlowPath(frame, method, top, cpi, opcode, pool, tag);
            }
        }
    }

    /**
     * Resolves non-primitive constant-pool entries that can execute arbitrary Java code.
     */
    @NeverInline("Keep constant resolution out of the bytecode-handler stubs")
    private static void resolveConstantAtSlowPath(InterpreterFrame frame, InterpreterResolvedJavaMethod method, int top, char cpi, int opcode, InterpreterConstantPool pool,
                    ConstantPool.Tag tag) {
        switch (tag) {
            case CLASS -> {
                InterpreterResolvedJavaType resolvedType = resolveType(method, opcode, cpi);
                putObject(frame, top, resolvedType.getJavaClass());
            }
            case STRING -> {
                String string = pool.resolveStringAt(cpi);
                putObject(frame, top, string);
            }
            case METHODTYPE -> {
                putObject(frame, top, resolveMethodType(pool, method, opcode, cpi));
            }
            case METHODHANDLE -> {
                putObject(frame, top, resolveMethodHandle(pool, method, opcode, cpi));
            }
            case DYNAMIC -> {
                Object constant = resolveDynamicConstant(pool, method, opcode, cpi);
                switch (symbolToJvmciKind(pool.dynamicType(cpi))) {
                    case Boolean -> putInt(frame, top, (Boolean) constant ? 1 : 0);
                    case Byte -> putInt(frame, top, (Byte) constant);
                    case Short -> putInt(frame, top, (Short) constant);
                    case Char -> putInt(frame, top, (Character) constant);
                    case Int -> putInt(frame, top, (Integer) constant);
                    case Float -> putFloat(frame, top, (Float) constant);
                    case Long -> putLong(frame, top, (Long) constant);
                    case Double -> putDouble(frame, top, (Double) constant);
                    case Object -> putObject(frame, top, constant);
                    default -> throw VMError.shouldNotReachHere("Unexpected dynamic constant type " + pool.dynamicType(cpi));
                }
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

    private static int invoke(InterpreterFrame callerFrame, MethodProfile methodProfile, InterpreterResolvedJavaMethod method, byte[] code, int top, int curBCI, int opcode,
                    boolean forceStayInInterpreter,
                    boolean preferStayInInterpreter) {
        int invokeTop = top;

        InterpreterResolvedJavaType symbolicHolder = null;
        InterpreterResolvedJavaMethod seedMethod;
        InterpreterUnresolvedSignature seedSignature;
        CallKind callKind;
        JavaKind returnKind;
        int parameterSlots;
        boolean hasReceiver;
        boolean requiresSymbolicTypeCheck = false;

        if (opcode == INVOKEDYNAMIC) {
            int fullCPI = BytecodeStream.readCPI4(code, curBCI);
            if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, fullCPI == 0)) {
                // This can happen for the debugger
                throw noSuchMethodError(opcode, null);
            }
            int indyCPI = fullCPI >>> 16;
            int extraCPI = fullCPI & 0xFFFF;
            Object indyEntry = method.getConstantPool().resolvedAt(indyCPI, method.getDeclaringClass());
            Object appendix;
            if (indyEntry instanceof ResolvedInvokeDynamicConstant invokeDynamicConstant) {
                // runtime-loaded case
                if (extraCPI == 0) {
                    // This call site is not linked yet
                    try {
                        extraCPI = invokeDynamicConstant.link((RuntimeInterpreterConstantPool) method.getConstantPool(), method.getDeclaringClass().getJavaClass(), method, curBCI);
                        assert extraCPI != 0;
                    } catch (Throwable e) {
                        throw SemanticJavaException.raise(e);
                    }
                    method.patchInvokeDynamicExtraCPI(curBCI, extraCPI);
                    assert BytecodeStream.readIndyExtraCPIVolatile(code, curBCI) == extraCPI;
                    assert BytecodeStream.readCPI2(code, curBCI) == indyCPI;
                }
                CallSiteLink link = invokeDynamicConstant.getCallSiteLink(method, code, curBCI, extraCPI);
                if (link instanceof SuccessfulCallSiteLink successfulCallSiteLink) {
                    appendix = successfulCallSiteLink.getUnboxedAppendix();
                    seedMethod = successfulCallSiteLink.getInvoker();
                } else {
                    throw SemanticJavaException.raise(((FailedCallSiteLink) link).getFailure());
                }
            } else if (indyEntry instanceof InterpreterResolvedJavaMethod entryMethod) {
                // AOT case
                seedMethod = entryMethod;
                Object appendixEntry = method.getConstantPool().resolvedAt(extraCPI, method.getDeclaringClass());
                if (JavaConstant.NULL_POINTER.equals(appendixEntry)) {
                    // The appendix is deliberately null.
                    appendix = null;
                } else if (appendixEntry instanceof ReferenceConstant<?> referenceConstant) {
                    appendix = referenceConstant.getReferent();
                    if (appendix == null) {
                        throw SemanticJavaException.raise(new IncompatibleClassChangeError("INVOKEDYNAMIC appendix was not included in the image heap"));
                    }
                } else {
                    throw VMError.shouldNotReachHere("Unexpected INVOKEDYNAMIC appendix constant: " + appendixEntry);
                }
            } else {
                throw VMError.shouldNotReachHere("Unexpected INVOKEDYNAMIC constant: " + indyEntry);
            }
            InterpreterFrameUtil.putObject(callerFrame, top, appendix);
            invokeTop = top + 1;
            callKind = CallKind.DIRECT;
            seedSignature = seedMethod.getSignature();
            returnKind = seedSignature.getReturnKind();
            hasReceiver = !seedMethod.isStatic();
            parameterSlots = seedSignature.slotsForParameters(hasReceiver);
            requiresSymbolicTypeCheck = false;
        } else {
            LinkedInvoke linkedInvoke = getOrLinkInvoke(method, code, curBCI, opcode);
            symbolicHolder = linkedInvoke.symbolicHolder;
            seedMethod = linkedInvoke.seedMethod;
            callKind = linkedInvoke.callKind;
            seedSignature = linkedInvoke.signature;
            returnKind = linkedInvoke.returnKind;
            hasReceiver = linkedInvoke.hasReceiver;
            parameterSlots = linkedInvoke.parameterSlots;
            requiresSymbolicTypeCheck = linkedInvoke.requiresSymbolicTypeCheck;
            if (linkedInvoke.appendix != null) {
                InterpreterFrameUtil.putObject(callerFrame, top, linkedInvoke.appendix);
                invokeTop = top + 1;
            }
        }

        int resultAt = invokeTop - parameterSlots;
        // The stack effect is wrt. the original top-of-the-stack.
        int retStackEffect = resultAt - top;

        Object[] calleeArgs = InterpreterFrameUtil.popArguments(callerFrame, invokeTop, hasReceiver, seedSignature);
        if (hasReceiver) {
            Object receiver = calleeArgs[0];
            profileType(methodProfile, curBCI, receiver);
            receiver = nullCheck(receiver);
            calleeArgs[0] = receiver;
            if (requiresSymbolicTypeCheck) {
                ResolvedJavaType receiverType = DynamicHub.fromClass(receiver.getClass()).getInterpreterType();
                if (symbolicHolder != null && !symbolicHolder.isAssignableFrom(receiverType)) {
                    throw SemanticJavaException.raise(new IncompatibleClassChangeError(
                                    MetadataUtil.fmt("Class %s does not implement the requested interface %s",
                                                    receiverType.toJavaName(),
                                                    symbolicHolder.toJavaName())));
                }
            }
        }

        Object retObj = InterpreterToVM.dispatchInvocation(seedMethod, calleeArgs, callKind, forceStayInInterpreter, preferStayInInterpreter, false);

        retStackEffect += InterpreterFrameUtil.putKind(callerFrame, resultAt, retObj, returnKind);

        /* instructions have fixed stack effect encoded */
        return retStackEffect - Bytecodes.stackEffectOf(opcode);
    }

    private static LinkedInvoke getOrLinkInvoke(InterpreterResolvedJavaMethod method, byte[] code, int curBCI, int opcode) {
        char cpi = BytecodeStream.readCPI2(code, curBCI);
        assert opcode == INVOKEVIRTUAL || opcode == INVOKESPECIAL || opcode == INVOKESTATIC || opcode == INVOKEINTERFACE : Bytecodes.nameOf(opcode);
        InterpreterConstantPool constantPool = getConstantPool(method);
        LinkedInvoke linkedInvoke = constantPool.peekLinkedInvoke(cpi, opcode);
        if (linkedInvoke != null) {
            return linkedInvoke;
        }
        return linkInvoke(method, opcode, cpi);
    }

    private static LinkedInvoke linkInvoke(InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        InterpreterResolvedJavaMethod symbolicResolution = Interpreter.resolveMethod(method, opcode, cpi);
        InterpreterResolvedJavaType symbolicHolder = Interpreter.resolveSymbolicHolder(method, opcode, cpi);
        if (symbolicHolder == null) {
            if (InterpreterTraceSupport.getValue()) {
                traceInterpreter().string("Failed to resolve symbolic holder during call site resolution for seed ").string(symbolicResolution.toString()).string(" in caller method ").string(
                                method.toString()).newline();
            }
            // If unresolvable, provide symbolic resolution's holder as best-effort.
            symbolicHolder = symbolicResolution.getDeclaringClass();
        }

        InterpreterResolvedJavaMethod seedMethod;
        CallKind callKind;

        // Ensure receivers of an interface method call actually implement the declared
        // interface. This is not checked by the verifier, so we need to dynamically
        // check that property. Note: this condition covers both INVOKEINTERFACE, and
        // INVOKESPECIAL of an interface method.
        boolean requiresSymbolicTypeCheck = getConstantPool(method).tagAt(cpi) == ConstantPool.Tag.INTERFACE_METHOD_REF;

        try {
            ResolvedCall<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> resolvedCall = CremaLinkResolver.resolveCallSiteOrThrow(
                            CremaRuntimeAccess.getInstance(),
                            method.getDeclaringClass(),
                            symbolicResolution,
                            CallSiteType.fromOpCode(opcode),
                            symbolicHolder);

            seedMethod = resolvedCall.getResolvedMethod();
            callKind = resolvedCall.getCallKind();
        } catch (Throwable e) {
            throw SemanticJavaException.raise(e);
        }

        Object appendix = null;
        if (seedMethod instanceof InterpreterResolvedInvokeGenericJavaMethod invokeGenericJavaMethod) {
            appendix = invokeGenericJavaMethod.getAppendix();
            seedMethod = invokeGenericJavaMethod.getInvoker();
            callKind = CallKind.DIRECT;
        }
        if (InterpreterTraceSupport.getValue()) {
            traceInterpreter().string("Linking for call site of ").string(Bytecodes.nameOf(opcode)).string(" with resolved cp entry ").string(symbolicResolution.toString()).string(":").newline();
            traceInterpreter().string("  ").string(callKind.toString()).string(": ").string(seedMethod.toString()).newline();
        }

        LinkedInvoke linkedInvoke = new LinkedInvoke(symbolicHolder, seedMethod, callKind, appendix, requiresSymbolicTypeCheck);
        linkedInvoke = getConstantPool(method).cacheLinkedInvoke(cpi, opcode, linkedInvoke);
        return linkedInvoke;
    }

    private static MethodType resolveMethodType(InterpreterConstantPool pool, InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        assert opcode == LDC || opcode == LDC_W : Bytecodes.nameOf(opcode);
        try {
            return pool.resolvedMethodTypeAt(cpi, method.getDeclaringClass());
        } catch (Throwable t) {
            throw SemanticJavaException.raise(t);
        }
    }

    private static MethodHandle resolveMethodHandle(InterpreterConstantPool pool, InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        assert opcode == LDC || opcode == LDC_W : Bytecodes.nameOf(opcode);
        try {
            return pool.resolvedMethodHandleAt(cpi, method.getDeclaringClass());
        } catch (Throwable t) {
            throw SemanticJavaException.raise(t);
        }
    }

    private static Object resolveDynamicConstant(InterpreterConstantPool pool, InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        assert opcode == LDC || opcode == LDC_W : Bytecodes.nameOf(opcode);
        try {
            return pool.resolvedDynamicConstantAt(cpi, method.getDeclaringClass());
        } catch (Throwable t) {
            throw SemanticJavaException.raise(t);
        }
    }

    // region Class/Method/Field resolution

    private static InterpreterResolvedJavaType resolveType(InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        assert opcode == INSTANCEOF || opcode == CHECKCAST || opcode == NEW || opcode == ANEWARRAY || opcode == MULTIANEWARRAY || opcode == LDC || opcode == LDC_W : Bytecodes.nameOf(opcode);
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
        } catch (Throwable t) {
            throw SemanticJavaException.raise(t);
        }
    }

    private static InterpreterResolvedJavaType resolveTypeOrNullIfUnresolvable(InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        assert opcode == INSTANCEOF || opcode == CHECKCAST || opcode == NEW || opcode == ANEWARRAY || opcode == MULTIANEWARRAY || opcode == LDC || opcode == LDC_W : Bytecodes.nameOf(opcode);
        if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, cpi == 0)) {
            return null; // CPI 0 is a marker for unresolvable AND unknown entry
        }
        try {
            return getConstantPool(method).resolvedTypeAt(method.getDeclaringClass(), cpi);
        } catch (UnsupportedResolutionException e) {
            return null;
        } catch (Throwable t) {
            throw SemanticJavaException.raise(t);
        }
    }

    /**
     * For a member constant ({@code CONSTANT_Methodref_info},
     * {@code CONSTANT_InterfaceMethodref_info}, or {@code CONSTANT_Fieldref_info}) entry in the
     * constant pool at index {@code cpi}, resolves the class entry at index {@code class_index}.
     * <p>
     * Note that this <i>does not</i> resolve the member constant itself, only its holder class.
     *
     * @return The resolved class constant if successful, or {@code null} if the AOT constant pool
     *         of the {@code caller} did not record the necessary entries.
     * @throws SemanticJavaException Any exception thrown during resolution will be rethrown wrapped
     *             in this exception type.
     */
    public static InterpreterResolvedJavaType resolveSymbolicHolder(InterpreterResolvedJavaMethod caller, int opcode, char cpi) {
        if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, cpi == 0)) {
            return null; // CPI 0 is a marker for unresolvable AND unknown entry
        }
        assert Bytecodes.isInvoke(opcode) : "wrong opcode for resolving symbolic holder: " + Bytecodes.nameOf(opcode);
        int holderCpi = getConstantPool(caller).memberClassIndex(cpi);
        if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, holderCpi == 0)) {
            return null; // CPI 0 is a marker for unresolvable AND unknown entry
        }
        try {
            return getConstantPool(caller).resolvedTypeAt(caller.getDeclaringClass(), holderCpi);
        } catch (UnsupportedResolutionException e) {
            return null;
        } catch (Throwable t) {
            throw SemanticJavaException.raise(t);
        }
    }

    public static InterpreterResolvedJavaMethod resolveMethod(InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        assert Bytecodes.isInvoke(opcode) : Bytecodes.nameOf(opcode);
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
        } catch (Throwable t) {
            throw SemanticJavaException.raise(t);
        }
    }

    private static InterpreterResolvedJavaField resolveField(InterpreterResolvedJavaMethod method, int opcode, byte[] code, int bci) {
        assert opcode == GETFIELD || opcode == GETSTATIC || opcode == PUTFIELD || opcode == PUTSTATIC : Bytecodes.nameOf(opcode);
        char cpi = BytecodeStream.readCPI2(code, bci);
        if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, cpi == 0)) {
            throw noSuchFieldError(opcode, null);
        }
        try {
            InterpreterResolvedJavaField field = getConstantPool(method).resolvedFieldAt(method.getDeclaringClass(), cpi);
            // Apply the opcode-specific field rules after symbolic resolution.
            CremaLinkResolver.checkFieldAccessOrThrow(CremaRuntimeAccess.getInstance(), field, opcode, method.getDeclaringClass(), method);
            quickenFieldAccess(code, bci, opcode);
            return field;
        } catch (UnsupportedResolutionException e) {
            // CP does not support resolution, try to provide a hint of the non-resolvable entry.
            UnresolvedJavaField missingField = null;
            if (getConstantPool(method).peekCachedEntry(cpi) instanceof UnresolvedJavaField unresolvedJavaField) {
                missingField = unresolvedJavaField;
            }
            throw noSuchFieldError(opcode, missingField);
        } catch (Throwable t) {
            throw SemanticJavaException.raise(t);
        }
    }

    private static InterpreterResolvedJavaField resolveQuickenedField(InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        assert opcode == GETFIELD || opcode == GETSTATIC || opcode == PUTFIELD || opcode == PUTSTATIC : Bytecodes.nameOf(opcode);
        assert cpi != 0 : "Quickened field access requires a resolved constant pool index";
        try {
            // The first execution cached the resolved field after applying opcode-specific access checks.
            return (InterpreterResolvedJavaField) getConstantPool(method).peekCachedEntry(cpi);
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere("Quickened field access must use an already resolved field entry", t);
        }
    }

    private static void quickenFieldAccess(byte[] code, int bci, int opcode) {
        // Patch only the opcode: the CPI operand and BCI layout stay identical.
        BytecodeStream.patchOpcodeOpaque(code, bci, Bytecodes.quickenedFieldAccess(opcode));
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
        assert opcode == PUTFIELD || opcode == PUTSTATIC : Bytecodes.nameOf(opcode);
        assert field.isStatic() == (opcode == PUTSTATIC);
        assert !field.isUnmaterializedConstant();
        JavaKind kind = field.getJavaKind();
        assert kind != JavaKind.Illegal;

        int slotCount = kind.getSlotCount();
        Object receiver = (opcode == PUTSTATIC)
                        ? field.getDeclaringClass().getStaticStorage(kind.isPrimitive(), field.getInstalledLayerNum())
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
        assert opcode == GETFIELD || opcode == GETSTATIC : Bytecodes.nameOf(opcode);
        assert field.isStatic() == (opcode == GETSTATIC);
        JavaKind kind = field.getJavaKind();
        assert kind != JavaKind.Illegal;

        Object receiver = opcode == GETSTATIC
                        ? field.getDeclaringClass().getStaticStorage(kind.isPrimitive(), field.getInstalledLayerNum())
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
