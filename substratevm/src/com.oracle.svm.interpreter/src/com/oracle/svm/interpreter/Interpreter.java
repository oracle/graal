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

import static com.oracle.svm.interpreter.InterpreterOptions.InterpreterTraceSupport;
import static com.oracle.svm.interpreter.InterpreterToVM.nullCheck;
import static com.oracle.svm.interpreter.InterpreterUtil.invalidOpcode;
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
import static jdk.graal.compiler.api.directives.GraalDirectives.uncheckedCast;

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

import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterFetchOpcode;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandler;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig;
import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
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

    public static Object execute(InterpreterResolvedJavaMethod method, Object[] args) {
        return execute(method, args, false);
    }

    public static Object execute(InterpreterFrame frame) {
        checkExecutable(frame.method);
        return execute0(frame, false);
    }

    public static Object execute(InterpreterResolvedJavaMethod method, Object[] args, boolean forceStayInInterpreter) {
        InterpreterFrame frame = InterpreterFrame.create(method, args);
        checkExecutable(method);
        frame.initializeLocals();
        return execute0(frame, forceStayInInterpreter);
    }

    @AlwaysInline("Keep the executable-method predicate on the interpreter entry fast path")
    private static void checkExecutable(InterpreterResolvedJavaMethod method) {
        if (method.hasBytecodes() || method.getSignaturePolymorphicIntrinsic() != null) {
            return;
        }
        executableCheckFailed(method);
    }

    @NeverInline("Keep interpreter entry diagnostics off the executable-method fast path")
    private static void executableCheckFailed(InterpreterResolvedJavaMethod method) {
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

    public static Object execute(InterpreterFrame frame, int startBCI, int startTOP) {
        checkExecutable(frame.method);
        return execute0(frame, startBCI, startTOP);
    }

    private static Object execute0(InterpreterFrame frame, int startBCI, int startTop) {
        InterpreterResolvedJavaMethod method = frame.method;
        boolean releaseSynchronizedMethodLock = false;
        boolean releaseInterpreterFrameLocks = true;
        try {
            int executeBCI = startBCI;
            if (startBCI == jdk.vm.ci.code.BytecodeFrame.BEFORE_BCI) {
                executeBCI = 0;
                if (method.isSynchronized()) {
                    Object synchronizedMethodLock = frame.getSynchronizedMethodLock();
                    assert synchronizedMethodLock != null;
                    InterpreterToVM.monitorEnter(frame, synchronizedMethodLock);
                    releaseSynchronizedMethodLock = true;
                }
            } else if (method.isSynchronized()) {
                releaseSynchronizedMethodLock = true;
            }
            assert frame.code != null : "no bytecode stream for " + method;
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

    private static Object execute0(InterpreterFrame frame, boolean stayInInterpreter) {
        InterpreterResolvedJavaMethod method = frame.method;
        boolean releaseSynchronizedMethodLock = false;
        boolean releaseInterpreterFrameLocks = true;
        try {
            assert method.isStatic() || frame.getThis() != null;
            if (method.isSynchronized()) {
                Object synchronizedMethodLock = frame.getSynchronizedMethodLock();
                assert synchronizedMethodLock != null;
                InterpreterToVM.monitorEnter(frame, synchronizedMethodLock);
                releaseSynchronizedMethodLock = true;
            }
            SignaturePolymorphicIntrinsic intrinsic = method.getSignaturePolymorphicIntrinsic();
            if (intrinsic != null) {
                return IntrinsicRoot.execute(frame, method, intrinsic, stayInInterpreter);
            } else {
                assert frame.code != null : "no bytecode stream for " + method;
                int startTop = method.getMaxLocals();
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

    private static void traceInterpreterEnter(InterpreterResolvedJavaMethod method, int indent, long curBCI, long top) {
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

    private static void traceInterpreterReturn(InterpreterResolvedJavaMethod method, int indent, long curBCI, long top) {
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
    private static void returnFromInterpreter(InterpreterResolvedJavaMethod method, int indent, long curBCI, long top, Object returnValue) {
        traceInterpreterReturn(method, indent, curBCI, top);
        Thread currentThread = Thread.currentThread();
        if (Root.debuggerEventsSupported() && DebuggerEvents.singleton().isEventEnabled(currentThread, EventKind.METHOD_EXIT)) {
            if (method.getDeclaringClass().isMethodExitEvent()) {
                int flags = EventKind.METHOD_EXIT.getFlag() | EventKind.METHOD_EXIT_WITH_RETURN_VALUE.getFlag();
                DebuggerEvents.singleton().getEventHandler().onEventAt(currentThread, method, (int) curBCI, returnValue, flags);
            }
        }
    }

    private static void traceInterpreterInstruction(InterpreterFrame frame, int indent, long curBCI, long top, int curOpcode) {
        /* arguments to Log methods might have side-effects */
        if (!InterpreterTraceSupport.getValue()) {
            return;
        }

        traceInterpreter(" ".repeat(indent)) //
                        .string("bci=").unsigned(curBCI).string(" ") //
                        .string(Bytecodes.nameOf(curOpcode));
        for (long slot = top - 1; slot >= 0; slot--) {
            traceInterpreter(", s").unsigned(slot).string("=").hex(frame.getPrimitive(slot, 0)).string("/").object(frame.getReference(slot, 0));
        }
        traceInterpreter("").newline();
    }

    private static void traceInterpreterException(InterpreterResolvedJavaMethod method, int indent, long curBCI, long top) {
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
            return InterpreterStubSection.leaveInterpreterForJNIDowncall(seedMethod, args);
        }
    }

    public static final class IntrinsicRoot {
        @NeverInline("needed far stack walking")
        public static Object execute(InterpreterFrame frame, InterpreterResolvedJavaMethod method, SignaturePolymorphicIntrinsic intrinsic, boolean forceStayInInterpreter) {
            int indent = getLogIndent();
            traceIntrinsicEnter(method, indent, intrinsic);
            return switch (intrinsic) {
                case InvokeBasic -> {
                    MethodHandle mh = (MethodHandle) frame.getThis();
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
                    Object[] arguments = frame.getArguments();
                    Target_java_lang_invoke_MemberName memberName = (Target_java_lang_invoke_MemberName) arguments[arguments.length - 1];
                    InterpreterResolvedJavaMethod resolutionSeed = InterpreterResolvedJavaMethod.fromMemberName(memberName);
                    InterpreterUnresolvedSignature signature = resolutionSeed.getSignature();
                    boolean hasReceiver = intrinsic != SignaturePolymorphicIntrinsic.LinkToStatic;
                    Object[] basicArgs = unbasic(arguments, signature, hasReceiver);
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

        @NeverInline("needed for stack walking")
        @BytecodeInterpreterHandlerConfig(maximumOperationCode = QUICK_PUTFIELD, arguments = {
                        @BytecodeInterpreterHandlerConfig.Argument(returnValue = true),
                        @BytecodeInterpreterHandlerConfig.Argument(expand = BytecodeInterpreterHandlerConfig.Argument.ExpansionKind.MATERIALIZED, fields = {
                                        @BytecodeInterpreterHandlerConfig.Argument.Field(name = "code"),
                                        @BytecodeInterpreterHandlerConfig.Argument.Field(name = "primitives"),
                                        @BytecodeInterpreterHandlerConfig.Argument.Field(name = "references")
                        }),
                        @BytecodeInterpreterHandlerConfig.Argument(expand = BytecodeInterpreterHandlerConfig.Argument.ExpansionKind.VIRTUAL)
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

            long curBCI = startBCI;
            InterpreterOperandStack virtualStack = new InterpreterOperandStack(startTop);
            int debuggerEventFlags = 0;
            if (debuggerEventsSupported()) {
                DebuggerEvents debuggerEvents = DebuggerEvents.singleton();
                if (debuggerEvents.isEventEnabled(Thread.currentThread(), EventKind.METHOD_ENTRY) && method.getDeclaringClass().isMethodEnterEvent()) {
                    debuggerEventFlags |= EventKind.METHOD_ENTRY.getFlag();
                }
            }
            int indent = getLogIndent();
            frame.installState(methodProfile, forceStayInInterpreter, debuggerEventFlags, indent);

            InterpreterUtil.guarantee(frame.code != null, "no bytecode stream for %s", method);

            traceInterpreterEnter(method, indent, curBCI, virtualStack.topForFrameStackOperation());
            prepareOpcodeForDispatch(curBCI, frame, virtualStack);

            while (true) {
                int curOpcode = fetchOpcode(curBCI, frame, virtualStack);

                try {
                    // @formatter:off
                    switch (GraalDirectives.markThreadedSwitch(curOpcode)) {
                        case NOP: curBCI = nopHandler(curBCI, frame, virtualStack); break;
                        case ACONST_NULL: curBCI = aconstNullHandler(curBCI, frame, virtualStack); break;

                        case ICONST_M1: curBCI = iconstM1Handler(curBCI, frame, virtualStack); break;
                        case ICONST_0: curBCI = iconst0Handler(curBCI, frame, virtualStack); break;
                        case ICONST_1: curBCI = iconst1Handler(curBCI, frame, virtualStack); break;
                        case ICONST_2: curBCI = iconst2Handler(curBCI, frame, virtualStack); break;
                        case ICONST_3: curBCI = iconst3Handler(curBCI, frame, virtualStack); break;
                        case ICONST_4: curBCI = iconst4Handler(curBCI, frame, virtualStack); break;
                        case ICONST_5: curBCI = iconst5Handler(curBCI, frame, virtualStack); break;

                        case LCONST_0: curBCI = lconst0Handler(curBCI, frame, virtualStack); break;
                        case LCONST_1: curBCI = lconst1Handler(curBCI, frame, virtualStack); break;

                        case FCONST_0: curBCI = fconst0Handler(curBCI, frame, virtualStack); break;
                        case FCONST_1: curBCI = fconst1Handler(curBCI, frame, virtualStack); break;
                        case FCONST_2: curBCI = fconst2Handler(curBCI, frame, virtualStack); break;

                        case DCONST_0: curBCI = dconst0Handler(curBCI, frame, virtualStack); break;
                        case DCONST_1: curBCI = dconst1Handler(curBCI, frame, virtualStack); break;

                        case BIPUSH: curBCI = bipushHandler(curBCI, frame, virtualStack); break;
                        case SIPUSH: curBCI = sipushHandler(curBCI, frame, virtualStack); break;

                        case LDC: curBCI = ldcHandler(curBCI, frame, virtualStack); break;
                        case LDC_W: curBCI = ldcWHandler(curBCI, frame, virtualStack); break;
                        case LDC2_W: curBCI = ldc2WHandler(curBCI, frame, virtualStack); break;

                        case ILOAD: curBCI = iloadHandler(curBCI, frame, virtualStack); break;
                        case LLOAD: curBCI = lloadHandler(curBCI, frame, virtualStack); break;
                        case FLOAD: curBCI = floadHandler(curBCI, frame, virtualStack); break;
                        case DLOAD: curBCI = dloadHandler(curBCI, frame, virtualStack); break;
                        case ALOAD: curBCI = aloadHandler(curBCI, frame, virtualStack); break;

                        case ILOAD_0: curBCI = iload0Handler(curBCI, frame, virtualStack); break;
                        case ILOAD_1: curBCI = iload1Handler(curBCI, frame, virtualStack); break;
                        case ILOAD_2: curBCI = iload2Handler(curBCI, frame, virtualStack); break;
                        case ILOAD_3: curBCI = iload3Handler(curBCI, frame, virtualStack); break;

                        case LLOAD_0: curBCI = lload0Handler(curBCI, frame, virtualStack); break;
                        case LLOAD_1: curBCI = lload1Handler(curBCI, frame, virtualStack); break;
                        case LLOAD_2: curBCI = lload2Handler(curBCI, frame, virtualStack); break;
                        case LLOAD_3: curBCI = lload3Handler(curBCI, frame, virtualStack); break;

                        case FLOAD_0: curBCI = fload0Handler(curBCI, frame, virtualStack); break;
                        case FLOAD_1: curBCI = fload1Handler(curBCI, frame, virtualStack); break;
                        case FLOAD_2: curBCI = fload2Handler(curBCI, frame, virtualStack); break;
                        case FLOAD_3: curBCI = fload3Handler(curBCI, frame, virtualStack); break;

                        case DLOAD_0: curBCI = dload0Handler(curBCI, frame, virtualStack); break;
                        case DLOAD_1: curBCI = dload1Handler(curBCI, frame, virtualStack); break;
                        case DLOAD_2: curBCI = dload2Handler(curBCI, frame, virtualStack); break;
                        case DLOAD_3: curBCI = dload3Handler(curBCI, frame, virtualStack); break;

                        case ALOAD_0: curBCI = aload0Handler(curBCI, frame, virtualStack); break;
                        case ALOAD_1: curBCI = aload1Handler(curBCI, frame, virtualStack); break;
                        case ALOAD_2: curBCI = aload2Handler(curBCI, frame, virtualStack); break;
                        case ALOAD_3: curBCI = aload3Handler(curBCI, frame, virtualStack); break;

                        case IALOAD: curBCI = ialoadHandler(curBCI, frame, virtualStack); break;
                        case LALOAD: curBCI = laloadHandler(curBCI, frame, virtualStack); break;
                        case FALOAD: curBCI = faloadHandler(curBCI, frame, virtualStack); break;
                        case DALOAD: curBCI = daloadHandler(curBCI, frame, virtualStack); break;
                        case BALOAD: curBCI = baloadHandler(curBCI, frame, virtualStack); break;
                        case CALOAD: curBCI = caloadHandler(curBCI, frame, virtualStack); break;
                        case SALOAD: curBCI = saloadHandler(curBCI, frame, virtualStack); break;
                        case AALOAD: curBCI = aaloadHandler(curBCI, frame, virtualStack); break;

                        case ISTORE: curBCI = istoreHandler(curBCI, frame, virtualStack); break;
                        case LSTORE: curBCI = lstoreHandler(curBCI, frame, virtualStack); break;
                        case FSTORE: curBCI = fstoreHandler(curBCI, frame, virtualStack); break;
                        case DSTORE: curBCI = dstoreHandler(curBCI, frame, virtualStack); break;
                        case ASTORE: curBCI = astoreHandler(curBCI, frame, virtualStack); break;

                        case ISTORE_0: curBCI = istore0Handler(curBCI, frame, virtualStack); break;
                        case ISTORE_1: curBCI = istore1Handler(curBCI, frame, virtualStack); break;
                        case ISTORE_2: curBCI = istore2Handler(curBCI, frame, virtualStack); break;
                        case ISTORE_3: curBCI = istore3Handler(curBCI, frame, virtualStack); break;

                        case LSTORE_0: curBCI = lstore0Handler(curBCI, frame, virtualStack); break;
                        case LSTORE_1: curBCI = lstore1Handler(curBCI, frame, virtualStack); break;
                        case LSTORE_2: curBCI = lstore2Handler(curBCI, frame, virtualStack); break;
                        case LSTORE_3: curBCI = lstore3Handler(curBCI, frame, virtualStack); break;

                        case FSTORE_0: curBCI = fstore0Handler(curBCI, frame, virtualStack); break;
                        case FSTORE_1: curBCI = fstore1Handler(curBCI, frame, virtualStack); break;
                        case FSTORE_2: curBCI = fstore2Handler(curBCI, frame, virtualStack); break;
                        case FSTORE_3: curBCI = fstore3Handler(curBCI, frame, virtualStack); break;

                        case DSTORE_0: curBCI = dstore0Handler(curBCI, frame, virtualStack); break;
                        case DSTORE_1: curBCI = dstore1Handler(curBCI, frame, virtualStack); break;
                        case DSTORE_2: curBCI = dstore2Handler(curBCI, frame, virtualStack); break;
                        case DSTORE_3: curBCI = dstore3Handler(curBCI, frame, virtualStack); break;

                        case ASTORE_0: curBCI = astore0Handler(curBCI, frame, virtualStack); break;
                        case ASTORE_1: curBCI = astore1Handler(curBCI, frame, virtualStack); break;
                        case ASTORE_2: curBCI = astore2Handler(curBCI, frame, virtualStack); break;
                        case ASTORE_3: curBCI = astore3Handler(curBCI, frame, virtualStack); break;

                        case IASTORE: curBCI = iastoreHandler(curBCI, frame, virtualStack); break;
                        case LASTORE: curBCI = lastoreHandler(curBCI, frame, virtualStack); break;
                        case FASTORE: curBCI = fastoreHandler(curBCI, frame, virtualStack); break;
                        case DASTORE: curBCI = dastoreHandler(curBCI, frame, virtualStack); break;
                        case AASTORE: curBCI = aastoreHandler(curBCI, frame, virtualStack); break;
                        case BASTORE: curBCI = bastoreHandler(curBCI, frame, virtualStack); break;
                        case CASTORE: curBCI = castoreHandler(curBCI, frame, virtualStack); break;
                        case SASTORE: curBCI = sastoreHandler(curBCI, frame, virtualStack); break;

                        case POP: curBCI = popHandler(curBCI, frame, virtualStack); break;
                        case POP2: curBCI = pop2Handler(curBCI, frame, virtualStack); break;

                        case DUP: curBCI = dupHandler(curBCI, frame, virtualStack); break;
                        case DUP_X1: curBCI = dupX1Handler(curBCI, frame, virtualStack); break;
                        case DUP_X2: curBCI = dupX2Handler(curBCI, frame, virtualStack); break;
                        case DUP2: curBCI = dup2Handler(curBCI, frame, virtualStack); break;
                        case DUP2_X1: curBCI = dup2X1Handler(curBCI, frame, virtualStack); break;
                        case DUP2_X2: curBCI = dup2X2Handler(curBCI, frame, virtualStack); break;
                        case SWAP: curBCI = swapHandler(curBCI, frame, virtualStack); break;

                        case IADD: curBCI = iaddHandler(curBCI, frame, virtualStack); break;
                        case LADD: curBCI = laddHandler(curBCI, frame, virtualStack); break;
                        case FADD: curBCI = faddHandler(curBCI, frame, virtualStack); break;
                        case DADD: curBCI = daddHandler(curBCI, frame, virtualStack); break;

                        case ISUB: curBCI = isubHandler(curBCI, frame, virtualStack); break;
                        case LSUB: curBCI = lsubHandler(curBCI, frame, virtualStack); break;
                        case FSUB: curBCI = fsubHandler(curBCI, frame, virtualStack); break;
                        case DSUB: curBCI = dsubHandler(curBCI, frame, virtualStack); break;

                        case IMUL: curBCI = imulHandler(curBCI, frame, virtualStack); break;
                        case LMUL: curBCI = lmulHandler(curBCI, frame, virtualStack); break;
                        case FMUL: curBCI = fmulHandler(curBCI, frame, virtualStack); break;
                        case DMUL: curBCI = dmulHandler(curBCI, frame, virtualStack); break;

                        case IDIV: curBCI = idivHandler(curBCI, frame, virtualStack); break;
                        case LDIV: curBCI = ldivHandler(curBCI, frame, virtualStack); break;
                        case FDIV: curBCI = fdivHandler(curBCI, frame, virtualStack); break;
                        case DDIV: curBCI = ddivHandler(curBCI, frame, virtualStack); break;

                        case IREM: curBCI = iremHandler(curBCI, frame, virtualStack); break;
                        case LREM: curBCI = lremHandler(curBCI, frame, virtualStack); break;
                        case FREM: curBCI = fremHandler(curBCI, frame, virtualStack); break;
                        case DREM: curBCI = dremHandler(curBCI, frame, virtualStack); break;

                        case INEG: curBCI = inegHandler(curBCI, frame, virtualStack); break;
                        case LNEG: curBCI = lnegHandler(curBCI, frame, virtualStack); break;
                        case FNEG: curBCI = fnegHandler(curBCI, frame, virtualStack); break;
                        case DNEG: curBCI = dnegHandler(curBCI, frame, virtualStack); break;

                        case ISHL: curBCI = ishlHandler(curBCI, frame, virtualStack); break;
                        case LSHL: curBCI = lshlHandler(curBCI, frame, virtualStack); break;
                        case ISHR: curBCI = ishrHandler(curBCI, frame, virtualStack); break;
                        case LSHR: curBCI = lshrHandler(curBCI, frame, virtualStack); break;
                        case IUSHR: curBCI = iushrHandler(curBCI, frame, virtualStack); break;
                        case LUSHR: curBCI = lushrHandler(curBCI, frame, virtualStack); break;

                        case IAND: curBCI = iandHandler(curBCI, frame, virtualStack); break;
                        case LAND: curBCI = landHandler(curBCI, frame, virtualStack); break;

                        case IOR: curBCI = iorHandler(curBCI, frame, virtualStack); break;
                        case LOR: curBCI = lorHandler(curBCI, frame, virtualStack); break;

                        case IXOR: curBCI = ixorHandler(curBCI, frame, virtualStack); break;
                        case LXOR: curBCI = lxorHandler(curBCI, frame, virtualStack); break;

                        case IINC: curBCI = iincHandler(curBCI, frame, virtualStack); break;

                        case I2L: curBCI = i2lHandler(curBCI, frame, virtualStack); break;
                        case I2F: curBCI = i2fHandler(curBCI, frame, virtualStack); break;
                        case I2D: curBCI = i2dHandler(curBCI, frame, virtualStack); break;

                        case L2I: curBCI = l2iHandler(curBCI, frame, virtualStack); break;
                        case L2F: curBCI = l2fHandler(curBCI, frame, virtualStack); break;
                        case L2D: curBCI = l2dHandler(curBCI, frame, virtualStack); break;

                        case F2I: curBCI = f2iHandler(curBCI, frame, virtualStack); break;
                        case F2L: curBCI = f2lHandler(curBCI, frame, virtualStack); break;
                        case F2D: curBCI = f2dHandler(curBCI, frame, virtualStack); break;

                        case D2I: curBCI = d2iHandler(curBCI, frame, virtualStack); break;
                        case D2L: curBCI = d2lHandler(curBCI, frame, virtualStack); break;
                        case D2F: curBCI = d2fHandler(curBCI, frame, virtualStack); break;

                        case I2B: curBCI = i2bHandler(curBCI, frame, virtualStack); break;
                        case I2C: curBCI = i2cHandler(curBCI, frame, virtualStack); break;
                        case I2S: curBCI = i2sHandler(curBCI, frame, virtualStack); break;

                        case LCMP: curBCI = lcmpHandler(curBCI, frame, virtualStack); break;
                        case FCMPL: curBCI = fcmplHandler(curBCI, frame, virtualStack); break;
                        case FCMPG: curBCI = fcmpgHandler(curBCI, frame, virtualStack); break;
                        case DCMPL: curBCI = dcmplHandler(curBCI, frame, virtualStack); break;
                        case DCMPG: curBCI = dcmpgHandler(curBCI, frame, virtualStack); break;

                        // @formatter:on
                        case IFEQ:
                            curBCI = ifeqHandler(curBCI, frame, virtualStack);
                            break;
                        case IFNE:
                            curBCI = ifneHandler(curBCI, frame, virtualStack);
                            break;
                        case IFLT:
                            curBCI = ifltHandler(curBCI, frame, virtualStack);
                            break;
                        case IFGE:
                            curBCI = ifgeHandler(curBCI, frame, virtualStack);
                            break;
                        case IFGT:
                            curBCI = ifgtHandler(curBCI, frame, virtualStack);
                            break;
                        case IFLE:
                            curBCI = ifleHandler(curBCI, frame, virtualStack);
                            break;

                        case IF_ICMPEQ:
                            curBCI = ifIcmpeqHandler(curBCI, frame, virtualStack);
                            break;
                        case IF_ICMPNE:
                            curBCI = ifIcmpneHandler(curBCI, frame, virtualStack);
                            break;
                        case IF_ICMPLT:
                            curBCI = ifIcmpltHandler(curBCI, frame, virtualStack);
                            break;
                        case IF_ICMPGE:
                            curBCI = ifIcmpgeHandler(curBCI, frame, virtualStack);
                            break;
                        case IF_ICMPGT:
                            curBCI = ifIcmpgtHandler(curBCI, frame, virtualStack);
                            break;
                        case IF_ICMPLE:
                            curBCI = ifIcmpleHandler(curBCI, frame, virtualStack);
                            break;

                        case IF_ACMPEQ:
                            curBCI = ifAcmpeqHandler(curBCI, frame, virtualStack);
                            break;
                        case IF_ACMPNE:
                            curBCI = ifAcmpneHandler(curBCI, frame, virtualStack);
                            break;

                        case IFNULL:
                            curBCI = ifnullHandler(curBCI, frame, virtualStack);
                            break;
                        case IFNONNULL:
                            curBCI = ifnonnullHandler(curBCI, frame, virtualStack);
                            break;

                        case GOTO:
                            curBCI = gotoHandler(curBCI, frame, virtualStack);
                            break;
                        case GOTO_W:
                            curBCI = gotoWHandler(curBCI, frame, virtualStack);
                            break;

                        case JSR:
                            curBCI = jsrHandler(curBCI, frame, virtualStack);
                            break;
                        case JSR_W:
                            curBCI = jsrWHandler(curBCI, frame, virtualStack);
                            break;

                        case RET:
                            curBCI = retHandler(curBCI, frame, virtualStack);
                            break;

                        case TABLESWITCH:
                            curBCI = tableswitchHandler(curBCI, frame, virtualStack);
                            break;
                        case LOOKUPSWITCH:
                            curBCI = lookupswitchHandler(curBCI, frame, virtualStack);
                            break;

                        case IRETURN: // fall through
                        case LRETURN: // fall through
                        case FRETURN: // fall through
                        case DRETURN: // fall through
                        case ARETURN: // fall through
                        case RETURN: {
                            Object returnValue = virtualStack.peekKindAsObject(frame, method.getSignature().getReturnKind());
                            returnFromInterpreter(method, indent, curBCI, virtualStack.topForFrameStackOperation(), returnValue);
                            return returnValue;
                        }
                        // @formatter:off
                        // Bytecodes order is shuffled.
                        case GETSTATIC      : curBCI = getstaticHandler(curBCI, frame, virtualStack); break;
                        case GETFIELD       : curBCI = getfieldHandler(curBCI, frame, virtualStack); break;
                        case PUTSTATIC      : curBCI = putstaticHandler(curBCI, frame, virtualStack); break;
                        case PUTFIELD       : curBCI = putfieldHandler(curBCI, frame, virtualStack); break;
                        case QUICK_GETSTATIC : curBCI = quickGetstaticHandler(curBCI, frame, virtualStack); break;
                        case QUICK_GETFIELD  : curBCI = quickGetfieldHandler(curBCI, frame, virtualStack); break;
                        case QUICK_PUTSTATIC : curBCI = quickPutstaticHandler(curBCI, frame, virtualStack); break;
                        case QUICK_PUTFIELD  : curBCI = quickPutfieldHandler(curBCI, frame, virtualStack); break;

                        case INVOKEVIRTUAL  : curBCI = invokevirtualHandler(curBCI, frame, virtualStack); break;
                        case INVOKESPECIAL  : curBCI = invokespecialHandler(curBCI, frame, virtualStack); break;
                        case INVOKESTATIC   : curBCI = invokestaticHandler(curBCI, frame, virtualStack); break;
                        case INVOKEINTERFACE: curBCI = invokeinterfaceHandler(curBCI, frame, virtualStack); break;
                        case INVOKEDYNAMIC  : curBCI = invokedynamicHandler(curBCI, frame, virtualStack); break;

                        case NEW:
                            curBCI = newHandler(curBCI, frame, virtualStack);
                            break;
                        case NEWARRAY:
                            curBCI = newarrayHandler(curBCI, frame, virtualStack);
                            break;
                        case ANEWARRAY:
                            curBCI = anewarrayHandler(curBCI, frame, virtualStack);
                            break;
                        case ARRAYLENGTH:
                            curBCI = arraylengthHandler(curBCI, frame, virtualStack);
                            break;
                        case ATHROW:
                            curBCI = athrowHandler(curBCI, frame, virtualStack);
                            break;

                        case CHECKCAST:
                            curBCI = checkcastHandler(curBCI, frame, virtualStack);
                            break;
                        case INSTANCEOF:
                            curBCI = instanceofHandler(curBCI, frame, virtualStack);
                            break;
                        case MONITORENTER: curBCI = monitorenterHandler(curBCI, frame, virtualStack); break;
                        case MONITOREXIT: curBCI = monitorexitHandler(curBCI, frame, virtualStack); break;

                        case WIDE:
                            curBCI = wideHandler(curBCI, frame, virtualStack);
                            break;
                        // @formatter:on

                        case MULTIANEWARRAY:
                            curBCI = multianewarrayHandler(curBCI, frame, virtualStack);
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
                    ExceptionHandler handler = resolveExceptionHandler(method, (int) curBCI, exception);
                    if (handler != null) {
                        virtualStack.clearOperandStack(frame);
                        virtualStack.pushObject(frame, exception);
                        curBCI = beforeJumpChecks(frame, curBCI, handler.getHandlerBCI(), virtualStack.topForFrameStackOperation());
                        prepareOpcodeForDispatch(curBCI, frame, virtualStack);
                        continue;
                    } else {
                        traceInterpreterException(method, indent, curBCI, virtualStack.topForFrameStackOperation());
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
         * {@link #fetchOpcode(long, InterpreterFrame, InterpreterOperandStack)} reads the
         * opcode directly.
         *
         * <p>
         * Debugger preparation performs the opaque opcode read required for breakpoint
         * installation, processes single-step and breakpoint events, replaces
         * {@link Bytecodes#BREAKPOINT} with the original semantic opcode, delivers pending debugger
         * events, and stores the semantic opcode in {@link InterpreterFrame.DebugState#opcode}.
         * Tracing-only configurations do not store the opcode.
         */
        @AlwaysInline("Keep the interpreter fast path call-free")
        private static void prepareOpcodeForDispatch(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            boolean debuggerEventsSupported = debuggerEventsSupported();
            if (!debuggerEventsSupported && !InterpreterOptions.InterpreterTraceSupport.getValue()) {
                return;
            }

            int opcode = BytecodeStream.opaqueOpcode(frame.code, curBCI);
            if (debuggerEventsSupported) {
                int dispatchBCI = (int) curBCI;
                InterpreterResolvedJavaMethod method = frame.method;
                int debuggerEventFlags = frame.debugState.debuggerEventFlags;

                if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY,
                                DebuggerEvents.singleton().isEventEnabled(Thread.currentThread(), EventKind.SINGLE_STEP))) {
                    debuggerEventFlags = processSingleStepForDispatch(dispatchBCI, method, debuggerEventFlags);
                }
                if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, opcode == BREAKPOINT)) {
                    long breakpointResult = processBreakpointForDispatch(dispatchBCI, method, debuggerEventFlags);
                    opcode = (int) (breakpointResult >>> Integer.SIZE);
                    debuggerEventFlags = (int) breakpointResult;
                }
                if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, debuggerEventFlags != 0)) {
                    processDebuggerEventsForDispatch(dispatchBCI, method, debuggerEventFlags, frame);
                    frame.debugState.debuggerEventFlags = 0;
                }
            }
            if (InterpreterOptions.InterpreterTraceSupport.getValue()) {
                traceInterpreterInstruction(frame, frame.debugState.indent, curBCI, virtualStack.topForFrameStackOperation(), opcode);
            }
            if (debuggerEventsSupported) {
                frame.debugState.opcode = opcode;
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
            frame.debugState.publishDebuggerEventBCI(curBCI);
            try {
                DebuggerEvents.singleton().getEventHandler().onEventAt(Thread.currentThread(), method, curBCI, null, debuggerEventFlags);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere("Debugger event handler failed", t);
            } finally {
                frame.debugState.clearDebuggerEventBCI();
            }
        }

        /** Packs the semantic opcode and debugger flags returned by the breakpoint slow path. */
        private static long packDispatchPreparationResult(int opcode, int debuggerEventFlags) {
            return (((long) opcode) << Integer.SIZE) | Integer.toUnsignedLong(debuggerEventFlags);
        }

        /** Returns the semantic opcode for the current BCI. */
        @SuppressWarnings("unused")
        @AlwaysInline("Keep semantic opcode replay on the fast path")
        @BytecodeInterpreterFetchOpcode
        private static int fetchOpcode(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            if (debuggerEventsSupported()) {
                /*
                 * Debugger preparation resolves BREAKPOINT to its original semantic opcode. Use
                 * that prepared value instead of reading the breakpoint opcode from the bytecode.
                 */
                return frame.debugState.opcode;
            }
            // Without debugger support, the bytecode contains the semantic opcode directly.
            return BytecodeStream.uncheckedOpcode(frame.code, curBCI);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = NOP, safepoint = false)
        private static long nopHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(NOP);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ACONST_NULL, safepoint = false)
        private static long aconstNullHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushObject(frame, null);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ACONST_NULL);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_M1, safepoint = false)
        private static long iconstM1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushInt(frame, -1);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_M1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_0, safepoint = false)
        private static long iconst0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushInt(frame, 0);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_1, safepoint = false)
        private static long iconst1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushInt(frame, 1);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_2, safepoint = false)
        private static long iconst2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushInt(frame, 2);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_2);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_3, safepoint = false)
        private static long iconst3Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushInt(frame, 3);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_3);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_4, safepoint = false)
        private static long iconst4Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushInt(frame, 4);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_4);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ICONST_5, safepoint = false)
        private static long iconst5Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushInt(frame, 5);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ICONST_5);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LCONST_0, safepoint = false)
        private static long lconst0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushLong(frame, 0L);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LCONST_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LCONST_1, safepoint = false)
        private static long lconst1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushLong(frame, 1L);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LCONST_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FCONST_0, safepoint = false)
        private static long fconst0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushFloat(frame, 0.0f);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FCONST_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FCONST_1, safepoint = false)
        private static long fconst1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushFloat(frame, 1.0f);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FCONST_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FCONST_2, safepoint = false)
        private static long fconst2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushFloat(frame, 2.0f);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FCONST_2);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DCONST_0, safepoint = false)
        private static long dconst0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushDouble(frame, 0.0d);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(DCONST_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DCONST_1, safepoint = false)
        private static long dconst1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pushDouble(frame, 1.0d);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(DCONST_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = BIPUSH, safepoint = false)
        private static long bipushHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            byte value = BytecodeStream.uncheckedReadByte(frame.code, curBCI);
            virtualStack.pushInt(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(BIPUSH);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = SIPUSH, safepoint = false)
        private static long sipushHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            short value = BytecodeStream.uncheckedReadShort(frame.code, curBCI);
            virtualStack.pushInt(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(SIPUSH);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LDC, safepoint = false)
        private static long ldcHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            /*
             * Keep the unsigned one-byte CPI in one 32-bit interval. Without this opaque boundary,
             * lowering creates separate zero- and sign-extended CPI intervals, increasing register
             * pressure and potentially causing stack spills.
             */
            int cpi = GraalDirectives.opaque(BytecodeStream.uncheckedReadCPI1(frame.code, curBCI));
            if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, cpi == 0)) {
                throw noClassDefFoundError(LDC, null);
            }
            long top = virtualStack.topForFrameStackOperation();
            loadConstant(frame, top, cpi, LDC);
            virtualStack.applyFrameStackOperationDelta(1);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LDC);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LDC_W, safepoint = false)
        private static long ldcWHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int cpi = GraalDirectives.opaque(BytecodeStream.uncheckedReadCPI2(frame.code, curBCI));
            if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, cpi == 0)) {
                throw noClassDefFoundError(LDC_W, null);
            }
            long top = virtualStack.topForFrameStackOperation();
            loadConstant(frame, top, cpi, LDC_W);
            virtualStack.applyFrameStackOperationDelta(1);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LDC_W);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LDC2_W, safepoint = false)
        private static long ldc2WHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int cpi = GraalDirectives.opaque(BytecodeStream.uncheckedReadCPI2(frame.code, curBCI));
            long top = virtualStack.topForFrameStackOperation();
            loadConstant2(frame, top, cpi);
            virtualStack.applyFrameStackOperationDelta(2);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LDC2_W);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ILOAD, safepoint = false)
        private static long iloadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int index = BytecodeStream.uncheckedReadLocalIndex1(frame.code, curBCI);
            int value = frame.getLocalInt(index);
            virtualStack.pushInt(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ILOAD);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LLOAD, safepoint = false)
        private static long lloadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int index = BytecodeStream.uncheckedReadLocalIndex1(frame.code, curBCI);
            long value = frame.getLocalLong(index);
            virtualStack.pushLong(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LLOAD);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FLOAD, safepoint = false)
        private static long floadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int index = BytecodeStream.uncheckedReadLocalIndex1(frame.code, curBCI);
            float value = frame.getLocalFloat(index);
            virtualStack.pushFloat(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FLOAD);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DLOAD, safepoint = false)
        private static long dloadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int index = BytecodeStream.uncheckedReadLocalIndex1(frame.code, curBCI);
            double value = frame.getLocalDouble(index);
            virtualStack.pushDouble(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(DLOAD);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ALOAD, safepoint = false)
        private static long aloadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int index = BytecodeStream.uncheckedReadLocalIndex1(frame.code, curBCI);
            Object value = frame.getLocalObject(index);
            virtualStack.pushObject(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ALOAD);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ILOAD_0, safepoint = false)
        private static long iload0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = frame.getLocalInt(0);
            virtualStack.pushInt(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ILOAD_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ILOAD_1, safepoint = false)
        private static long iload1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = frame.getLocalInt(1);
            virtualStack.pushInt(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ILOAD_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ILOAD_2, safepoint = false)
        private static long iload2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = frame.getLocalInt(2);
            virtualStack.pushInt(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ILOAD_2);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ILOAD_3, safepoint = false)
        private static long iload3Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = frame.getLocalInt(3);
            virtualStack.pushInt(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ILOAD_3);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LLOAD_0, safepoint = false)
        private static long lload0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long value = frame.getLocalLong(0);
            virtualStack.pushLong(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LLOAD_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LLOAD_1, safepoint = false)
        private static long lload1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long value = frame.getLocalLong(1);
            virtualStack.pushLong(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LLOAD_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LLOAD_2, safepoint = false)
        private static long lload2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long value = frame.getLocalLong(2);
            virtualStack.pushLong(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LLOAD_2);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LLOAD_3, safepoint = false)
        private static long lload3Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long value = frame.getLocalLong(3);
            virtualStack.pushLong(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LLOAD_3);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FLOAD_0, safepoint = false)
        private static long fload0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float value = frame.getLocalFloat(0);
            virtualStack.pushFloat(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FLOAD_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FLOAD_1, safepoint = false)
        private static long fload1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float value = frame.getLocalFloat(1);
            virtualStack.pushFloat(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FLOAD_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FLOAD_2, safepoint = false)
        private static long fload2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float value = frame.getLocalFloat(2);
            virtualStack.pushFloat(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FLOAD_2);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FLOAD_3, safepoint = false)
        private static long fload3Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float value = frame.getLocalFloat(3);
            virtualStack.pushFloat(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FLOAD_3);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DLOAD_0, safepoint = false)
        private static long dload0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double value = frame.getLocalDouble(0);
            virtualStack.pushDouble(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(DLOAD_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DLOAD_1, safepoint = false)
        private static long dload1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double value = frame.getLocalDouble(1);
            virtualStack.pushDouble(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(DLOAD_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DLOAD_2, safepoint = false)
        private static long dload2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double value = frame.getLocalDouble(2);
            virtualStack.pushDouble(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(DLOAD_2);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DLOAD_3, safepoint = false)
        private static long dload3Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double value = frame.getLocalDouble(3);
            virtualStack.pushDouble(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(DLOAD_3);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ALOAD_0, safepoint = false)
        private static long aload0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object value = frame.getLocalObject(0);
            virtualStack.pushObject(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ALOAD_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ALOAD_1, safepoint = false)
        private static long aload1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object value = frame.getLocalObject(1);
            virtualStack.pushObject(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ALOAD_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ALOAD_2, safepoint = false)
        private static long aload2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object value = frame.getLocalObject(2);
            virtualStack.pushObject(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ALOAD_2);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ALOAD_3, safepoint = false)
        private static long aload3Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object value = frame.getLocalObject(3);
            virtualStack.pushObject(frame, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ALOAD_3);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISTORE, safepoint = false)
        private static long istoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int index = BytecodeStream.uncheckedReadLocalIndex1(frame.code, curBCI);
            int value = virtualStack.popInt(frame);
            frame.setLocalInt(index, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ISTORE);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSTORE, safepoint = false)
        private static long lstoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int index = BytecodeStream.uncheckedReadLocalIndex1(frame.code, curBCI);
            long value = virtualStack.popLong(frame);
            frame.setLocalLong(index, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LSTORE);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FSTORE, safepoint = false)
        private static long fstoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int index = BytecodeStream.uncheckedReadLocalIndex1(frame.code, curBCI);
            float value = virtualStack.popFloat(frame);
            frame.setLocalFloat(index, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FSTORE);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DSTORE, safepoint = false)
        private static long dstoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int index = BytecodeStream.uncheckedReadLocalIndex1(frame.code, curBCI);
            double value = virtualStack.popDouble(frame);
            frame.setLocalDouble(index, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(DSTORE);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ASTORE, safepoint = false)
        private static long astoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int index = BytecodeStream.uncheckedReadLocalIndex1(frame.code, curBCI);
            Object value = virtualStack.popObject(frame);
            frame.setLocalObject(index, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ASTORE);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISTORE_0, safepoint = false)
        private static long istore0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = virtualStack.popInt(frame);
            frame.setLocalInt(0, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ISTORE_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISTORE_1, safepoint = false)
        private static long istore1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = virtualStack.popInt(frame);
            frame.setLocalInt(1, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ISTORE_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISTORE_2, safepoint = false)
        private static long istore2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = virtualStack.popInt(frame);
            frame.setLocalInt(2, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ISTORE_2);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISTORE_3, safepoint = false)
        private static long istore3Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = virtualStack.popInt(frame);
            frame.setLocalInt(3, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ISTORE_3);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSTORE_0, safepoint = false)
        private static long lstore0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long value = virtualStack.popLong(frame);
            frame.setLocalLong(0, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LSTORE_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSTORE_1, safepoint = false)
        private static long lstore1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long value = virtualStack.popLong(frame);
            frame.setLocalLong(1, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LSTORE_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSTORE_2, safepoint = false)
        private static long lstore2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long value = virtualStack.popLong(frame);
            frame.setLocalLong(2, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LSTORE_2);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSTORE_3, safepoint = false)
        private static long lstore3Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long value = virtualStack.popLong(frame);
            frame.setLocalLong(3, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(LSTORE_3);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FSTORE_0, safepoint = false)
        private static long fstore0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float value = virtualStack.popFloat(frame);
            frame.setLocalFloat(0, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FSTORE_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FSTORE_1, safepoint = false)
        private static long fstore1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float value = virtualStack.popFloat(frame);
            frame.setLocalFloat(1, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FSTORE_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FSTORE_2, safepoint = false)
        private static long fstore2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float value = virtualStack.popFloat(frame);
            frame.setLocalFloat(2, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FSTORE_2);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FSTORE_3, safepoint = false)
        private static long fstore3Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float value = virtualStack.popFloat(frame);
            frame.setLocalFloat(3, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(FSTORE_3);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DSTORE_0, safepoint = false)
        private static long dstore0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double value = virtualStack.popDouble(frame);
            frame.setLocalDouble(0, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(DSTORE_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DSTORE_1, safepoint = false)
        private static long dstore1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double value = virtualStack.popDouble(frame);
            frame.setLocalDouble(1, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(DSTORE_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DSTORE_2, safepoint = false)
        private static long dstore2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double value = virtualStack.popDouble(frame);
            frame.setLocalDouble(2, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(DSTORE_2);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DSTORE_3, safepoint = false)
        private static long dstore3Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double value = virtualStack.popDouble(frame);
            frame.setLocalDouble(3, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(DSTORE_3);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ASTORE_0, safepoint = false)
        private static long astore0Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object value = virtualStack.popObject(frame);
            frame.setLocalObject(0, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ASTORE_0);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ASTORE_1, safepoint = false)
        private static long astore1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object value = virtualStack.popObject(frame);
            frame.setLocalObject(1, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ASTORE_1);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ASTORE_2, safepoint = false)
        private static long astore2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object value = virtualStack.popObject(frame);
            frame.setLocalObject(2, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ASTORE_2);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ASTORE_3, safepoint = false)
        private static long astore3Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object value = virtualStack.popObject(frame);
            frame.setLocalObject(3, value);
            long nextBCI = curBCI + ConstantBytecodes.lengthOf(ASTORE_3);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IALOAD, safepoint = false)
        private static long ialoadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -2);
            Object nonNullReceiver = nullCheck(receiver);
            int[] array = uncheckedCast(nonNullReceiver, int[].class);
            int index = virtualStack.peekInt(frame, -1);
            int value = InterpreterToVM.getArrayInt(index, array);
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame);
            virtualStack.pushInt(frame, value);
            return advanceToNextBytecode(curBCI, IALOAD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LALOAD, safepoint = false)
        private static long laloadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -2);
            Object nonNullReceiver = nullCheck(receiver);
            long[] array = uncheckedCast(nonNullReceiver, long[].class);
            int index = virtualStack.peekInt(frame, -1);
            long value = InterpreterToVM.getArrayLong(index, array);
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame);
            virtualStack.pushLong(frame, value);
            return advanceToNextBytecode(curBCI, LALOAD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FALOAD, safepoint = false)
        private static long faloadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -2);
            Object nonNullReceiver = nullCheck(receiver);
            float[] array = uncheckedCast(nonNullReceiver, float[].class);
            int index = virtualStack.peekInt(frame, -1);
            float value = InterpreterToVM.getArrayFloat(index, array);
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame);
            virtualStack.pushFloat(frame, value);
            return advanceToNextBytecode(curBCI, FALOAD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DALOAD, safepoint = false)
        private static long daloadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -2);
            Object nonNullReceiver = nullCheck(receiver);
            double[] array = uncheckedCast(nonNullReceiver, double[].class);
            int index = virtualStack.peekInt(frame, -1);
            double value = InterpreterToVM.getArrayDouble(index, array);
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame);
            virtualStack.pushDouble(frame, value);
            return advanceToNextBytecode(curBCI, DALOAD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = AALOAD, safepoint = false)
        private static long aaloadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -2);
            Object nonNullReceiver = nullCheck(receiver);
            Object[] array = uncheckedCast(nonNullReceiver, Object[].class);
            int index = virtualStack.peekInt(frame, -1);
            Object value = InterpreterToVM.getArrayObject(index, array);
            profileType(frame.methodProfile, curBCI, value);
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame);
            virtualStack.pushObject(frame, value);
            return advanceToNextBytecode(curBCI, AALOAD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = BALOAD, safepoint = false)
        private static long baloadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -2);
            Object nonNullReceiver = nullCheck(receiver);
            int index = virtualStack.peekInt(frame, -1);
            int value;
            if (nonNullReceiver instanceof byte[] byteArray) {
                value = InterpreterToVM.getArrayByteInternal(index, byteArray);
            } else {
                boolean[] booleanArray = uncheckedCast(nonNullReceiver, boolean[].class);
                value = InterpreterToVM.getArrayBooleanInternal(index, booleanArray);
            }
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame);
            virtualStack.pushInt(frame, value);
            return advanceToNextBytecode(curBCI, BALOAD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = CALOAD, safepoint = false)
        private static long caloadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -2);
            Object nonNullReceiver = nullCheck(receiver);
            char[] array = uncheckedCast(nonNullReceiver, char[].class);
            int index = virtualStack.peekInt(frame, -1);
            int value = InterpreterToVM.getArrayChar(index, array);
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame);
            virtualStack.pushInt(frame, value);
            return advanceToNextBytecode(curBCI, CALOAD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = SALOAD, safepoint = false)
        private static long saloadHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -2);
            Object nonNullReceiver = nullCheck(receiver);
            short[] array = uncheckedCast(nonNullReceiver, short[].class);
            int index = virtualStack.peekInt(frame, -1);
            int value = InterpreterToVM.getArrayShort(index, array);
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame);
            virtualStack.pushInt(frame, value);
            return advanceToNextBytecode(curBCI, SALOAD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IASTORE, safepoint = false)
        private static long iastoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -3);
            Object nonNullReceiver = nullCheck(receiver);
            int[] array = uncheckedCast(nonNullReceiver, int[].class);
            int index = virtualStack.peekInt(frame, -2);
            int length = array.length;
            if (Integer.compareUnsigned(index, length) >= 0) {
                throw SemanticJavaException.raiseArrayIndexOutOfBoundsException(index, length);
            }
            int value = virtualStack.peekInt(frame, -1);
            InterpreterToVM.setArrayInt(value, index, array);
            virtualStack.pop2(frame, false);
            virtualStack.pop1(frame);
            return advanceToNextBytecode(curBCI, IASTORE, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LASTORE, safepoint = false)
        private static long lastoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -4);
            Object nonNullReceiver = nullCheck(receiver);
            long[] array = uncheckedCast(nonNullReceiver, long[].class);
            int index = virtualStack.peekInt(frame, -3);
            int length = array.length;
            if (Integer.compareUnsigned(index, length) >= 0) {
                throw SemanticJavaException.raiseArrayIndexOutOfBoundsException(index, length);
            }
            long value = virtualStack.peekLong(frame, -1);
            InterpreterToVM.setArrayLong(value, index, array);
            virtualStack.pop2(frame, false);
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame);
            return advanceToNextBytecode(curBCI, LASTORE, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FASTORE, safepoint = false)
        private static long fastoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -3);
            Object nonNullReceiver = nullCheck(receiver);
            float[] array = uncheckedCast(nonNullReceiver, float[].class);
            int index = virtualStack.peekInt(frame, -2);
            int length = array.length;
            if (Integer.compareUnsigned(index, length) >= 0) {
                throw SemanticJavaException.raiseArrayIndexOutOfBoundsException(index, length);
            }
            float value = virtualStack.peekFloat(frame, -1);
            InterpreterToVM.setArrayFloat(value, index, array);
            virtualStack.pop2(frame, false);
            virtualStack.pop1(frame);
            return advanceToNextBytecode(curBCI, FASTORE, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DASTORE, safepoint = false)
        private static long dastoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -4);
            Object nonNullReceiver = nullCheck(receiver);
            double[] array = uncheckedCast(nonNullReceiver, double[].class);
            int index = virtualStack.peekInt(frame, -3);
            int length = array.length;
            if (Integer.compareUnsigned(index, length) >= 0) {
                throw SemanticJavaException.raiseArrayIndexOutOfBoundsException(index, length);
            }
            double value = virtualStack.peekDouble(frame, -1);
            InterpreterToVM.setArrayDouble(value, index, array);
            virtualStack.pop2(frame, false);
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame);
            return advanceToNextBytecode(curBCI, DASTORE, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = AASTORE, safepoint = false)
        private static long aastoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -3);
            Object nonNullReceiver = nullCheck(receiver);
            Object[] array = uncheckedCast(nonNullReceiver, Object[].class);
            int index = virtualStack.peekInt(frame, -2);
            int length = array.length;
            if (Integer.compareUnsigned(index, length) >= 0) {
                throw SemanticJavaException.raiseArrayIndexOutOfBoundsException(index, length);
            }
            Object value = virtualStack.peekObject(frame, -1);
            profileType(frame.methodProfile, curBCI, value);
            InterpreterToVM.setArrayObject(value, index, array);
            virtualStack.pop1(frame);
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame);
            return advanceToNextBytecode(curBCI, AASTORE, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = BASTORE, safepoint = false)
        private static long bastoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -3);
            Object nonNullReceiver = nullCheck(receiver);
            int index = virtualStack.peekInt(frame, -2);
            byte value = (byte) virtualStack.peekInt(frame, -1);
            if (nonNullReceiver instanceof byte[] byteArray) {
                InterpreterToVM.setArrayByteInternal(value, index, byteArray);
            } else {
                boolean[] booleanArray = uncheckedCast(nonNullReceiver, boolean[].class);
                InterpreterToVM.setArrayBooleanInternal(value, index, booleanArray);
            }
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame, false);
            virtualStack.pop1(frame);
            return advanceToNextBytecode(curBCI, BASTORE, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = CASTORE, safepoint = false)
        private static long castoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -3);
            Object nonNullReceiver = nullCheck(receiver);
            char[] array = uncheckedCast(nonNullReceiver, char[].class);
            int index = virtualStack.peekInt(frame, -2);
            int length = array.length;
            if (Integer.compareUnsigned(index, length) >= 0) {
                throw SemanticJavaException.raiseArrayIndexOutOfBoundsException(index, length);
            }
            char value = (char) virtualStack.peekInt(frame, -1);
            InterpreterToVM.setArrayChar(value, index, array);
            virtualStack.pop2(frame, false);
            virtualStack.pop1(frame);
            return advanceToNextBytecode(curBCI, CASTORE, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = SASTORE, safepoint = false)
        private static long sastoreHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -3);
            Object nonNullReceiver = nullCheck(receiver);
            short[] array = uncheckedCast(nonNullReceiver, short[].class);
            int index = virtualStack.peekInt(frame, -2);
            int length = array.length;
            if (Integer.compareUnsigned(index, length) >= 0) {
                throw SemanticJavaException.raiseArrayIndexOutOfBoundsException(index, length);
            }
            short value = (short) virtualStack.peekInt(frame, -1);
            InterpreterToVM.setArrayShort(value, index, array);
            virtualStack.pop2(frame, false);
            virtualStack.pop1(frame);
            return advanceToNextBytecode(curBCI, SASTORE, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = POP, safepoint = false)
        private static long popHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pop1(frame);
            return advanceToNextBytecode(curBCI, POP, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = POP2, safepoint = false)
        private static long pop2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.pop2(frame);
            return advanceToNextBytecode(curBCI, POP2, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DUP, safepoint = false)
        private static long dupHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.dup1(frame);
            return advanceToNextBytecode(curBCI, DUP, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DUP_X1, safepoint = false)
        private static long dupX1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.dupx1(frame);
            return advanceToNextBytecode(curBCI, DUP_X1, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DUP_X2, safepoint = false)
        private static long dupX2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.dupx2(frame);
            return advanceToNextBytecode(curBCI, DUP_X2, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DUP2, safepoint = false)
        private static long dup2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.dup2(frame);
            return advanceToNextBytecode(curBCI, DUP2, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DUP2_X1, safepoint = false)
        private static long dup2X1Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.dup2x1(frame);
            return advanceToNextBytecode(curBCI, DUP2_X1, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DUP2_X2, safepoint = false)
        private static long dup2X2Handler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.dup2x2(frame);
            return advanceToNextBytecode(curBCI, DUP2_X2, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = SWAP, safepoint = false)
        private static long swapHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            virtualStack.swap(frame);
            return advanceToNextBytecode(curBCI, SWAP, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IADD, safepoint = false)
        private static long iaddHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int b = virtualStack.popInt(frame);
            int a = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, a + b);
            return advanceToNextBytecode(curBCI, IADD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LADD, safepoint = false)
        private static long laddHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long b = virtualStack.popLong(frame);
            long a = virtualStack.popLong(frame);
            virtualStack.pushLong(frame, a + b);
            return advanceToNextBytecode(curBCI, LADD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FADD, safepoint = false)
        private static long faddHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float b = virtualStack.popFloat(frame);
            float a = virtualStack.popFloat(frame);
            virtualStack.pushFloat(frame, a + b);
            return advanceToNextBytecode(curBCI, FADD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DADD, safepoint = false)
        private static long daddHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double b = virtualStack.popDouble(frame);
            double a = virtualStack.popDouble(frame);
            virtualStack.pushDouble(frame, a + b);
            return advanceToNextBytecode(curBCI, DADD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISUB, safepoint = false)
        private static long isubHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int b = virtualStack.popInt(frame);
            int a = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, a - b);
            return advanceToNextBytecode(curBCI, ISUB, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSUB, safepoint = false)
        private static long lsubHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long b = virtualStack.popLong(frame);
            long a = virtualStack.popLong(frame);
            virtualStack.pushLong(frame, a - b);
            return advanceToNextBytecode(curBCI, LSUB, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FSUB, safepoint = false)
        private static long fsubHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float b = virtualStack.popFloat(frame);
            float a = virtualStack.popFloat(frame);
            virtualStack.pushFloat(frame, a - b);
            return advanceToNextBytecode(curBCI, FSUB, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DSUB, safepoint = false)
        private static long dsubHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double b = virtualStack.popDouble(frame);
            double a = virtualStack.popDouble(frame);
            virtualStack.pushDouble(frame, a - b);
            return advanceToNextBytecode(curBCI, DSUB, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IMUL, safepoint = false)
        private static long imulHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int b = virtualStack.popInt(frame);
            int a = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, a * b);
            return advanceToNextBytecode(curBCI, IMUL, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LMUL, safepoint = false)
        private static long lmulHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long b = virtualStack.popLong(frame);
            long a = virtualStack.popLong(frame);
            virtualStack.pushLong(frame, a * b);
            return advanceToNextBytecode(curBCI, LMUL, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FMUL, safepoint = false)
        private static long fmulHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float b = virtualStack.popFloat(frame);
            float a = virtualStack.popFloat(frame);
            virtualStack.pushFloat(frame, a * b);
            return advanceToNextBytecode(curBCI, FMUL, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DMUL, safepoint = false)
        private static long dmulHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double b = virtualStack.popDouble(frame);
            double a = virtualStack.popDouble(frame);
            virtualStack.pushDouble(frame, a * b);
            return advanceToNextBytecode(curBCI, DMUL, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IDIV, safepoint = false)
        private static long idivHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int divisor = virtualStack.peekInt(frame, -1);
            int dividend = virtualStack.peekInt(frame, -2);
            int result = divInt(divisor, dividend);
            virtualStack.pop2(frame, false);
            virtualStack.pushInt(frame, result);
            return advanceToNextBytecode(curBCI, IDIV, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LDIV, safepoint = false)
        private static long ldivHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long divisor = virtualStack.peekLong(frame, -1);
            long dividend = virtualStack.peekLong(frame, -3);
            long result = divLong(divisor, dividend);
            virtualStack.pop2(frame, false);
            virtualStack.pop2(frame, false);
            virtualStack.pushLong(frame, result);
            return advanceToNextBytecode(curBCI, LDIV, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FDIV, safepoint = false)
        private static long fdivHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float divisor = virtualStack.popFloat(frame);
            float dividend = virtualStack.popFloat(frame);
            virtualStack.pushFloat(frame, dividend / divisor);
            return advanceToNextBytecode(curBCI, FDIV, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DDIV, safepoint = false)
        private static long ddivHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double divisor = virtualStack.popDouble(frame);
            double dividend = virtualStack.popDouble(frame);
            virtualStack.pushDouble(frame, dividend / divisor);
            return advanceToNextBytecode(curBCI, DDIV, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IREM, safepoint = false)
        private static long iremHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int divisor = virtualStack.peekInt(frame, -1);
            int dividend = virtualStack.peekInt(frame, -2);
            int result = remInt(divisor, dividend);
            virtualStack.pop2(frame, false);
            virtualStack.pushInt(frame, result);
            return advanceToNextBytecode(curBCI, IREM, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LREM, safepoint = false)
        private static long lremHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long divisor = virtualStack.peekLong(frame, -1);
            long dividend = virtualStack.peekLong(frame, -3);
            long result = remLong(divisor, dividend);
            virtualStack.pop2(frame, false);
            virtualStack.pop2(frame, false);
            virtualStack.pushLong(frame, result);
            return advanceToNextBytecode(curBCI, LREM, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FREM, safepoint = false)
        private static long fremHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float divisor = virtualStack.popFloat(frame);
            float dividend = virtualStack.popFloat(frame);
            virtualStack.pushFloat(frame, dividend % divisor);
            return advanceToNextBytecode(curBCI, FREM, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DREM, safepoint = false)
        private static long dremHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double divisor = virtualStack.popDouble(frame);
            double dividend = virtualStack.popDouble(frame);
            virtualStack.pushDouble(frame, dividend % divisor);
            return advanceToNextBytecode(curBCI, DREM, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INEG, safepoint = false)
        private static long inegHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, -value);
            return advanceToNextBytecode(curBCI, INEG, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LNEG, safepoint = false)
        private static long lnegHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long value = virtualStack.popLong(frame);
            virtualStack.pushLong(frame, -value);
            return advanceToNextBytecode(curBCI, LNEG, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FNEG, safepoint = false)
        private static long fnegHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float value = virtualStack.popFloat(frame);
            virtualStack.pushFloat(frame, -value);
            return advanceToNextBytecode(curBCI, FNEG, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DNEG, safepoint = false)
        private static long dnegHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double value = virtualStack.popDouble(frame);
            virtualStack.pushDouble(frame, -value);
            return advanceToNextBytecode(curBCI, DNEG, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISHL, safepoint = false)
        private static long ishlHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int bits = virtualStack.popInt(frame);
            int value = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, value << bits);
            return advanceToNextBytecode(curBCI, ISHL, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSHL, safepoint = false)
        private static long lshlHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int bits = virtualStack.popInt(frame);
            long value = virtualStack.popLong(frame);
            virtualStack.pushLong(frame, value << bits);
            return advanceToNextBytecode(curBCI, LSHL, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ISHR, safepoint = false)
        private static long ishrHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int bits = virtualStack.popInt(frame);
            int value = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, value >> bits);
            return advanceToNextBytecode(curBCI, ISHR, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LSHR, safepoint = false)
        private static long lshrHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int bits = virtualStack.popInt(frame);
            long value = virtualStack.popLong(frame);
            virtualStack.pushLong(frame, value >> bits);
            return advanceToNextBytecode(curBCI, LSHR, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IUSHR, safepoint = false)
        private static long iushrHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int bits = virtualStack.popInt(frame);
            int value = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, value >>> bits);
            return advanceToNextBytecode(curBCI, IUSHR, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LUSHR, safepoint = false)
        private static long lushrHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int bits = virtualStack.popInt(frame);
            long value = virtualStack.popLong(frame);
            virtualStack.pushLong(frame, value >>> bits);
            return advanceToNextBytecode(curBCI, LUSHR, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IAND, safepoint = false)
        private static long iandHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int b = virtualStack.popInt(frame);
            int a = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, a & b);
            return advanceToNextBytecode(curBCI, IAND, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LAND, safepoint = false)
        private static long landHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long b = virtualStack.popLong(frame);
            long a = virtualStack.popLong(frame);
            virtualStack.pushLong(frame, a & b);
            return advanceToNextBytecode(curBCI, LAND, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IOR, safepoint = false)
        private static long iorHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int b = virtualStack.popInt(frame);
            int a = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, a | b);
            return advanceToNextBytecode(curBCI, IOR, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LOR, safepoint = false)
        private static long lorHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long b = virtualStack.popLong(frame);
            long a = virtualStack.popLong(frame);
            virtualStack.pushLong(frame, a | b);
            return advanceToNextBytecode(curBCI, LOR, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IXOR, safepoint = false)
        private static long ixorHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int b = virtualStack.popInt(frame);
            int a = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, a ^ b);
            return advanceToNextBytecode(curBCI, IXOR, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LXOR, safepoint = false)
        private static long lxorHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long b = virtualStack.popLong(frame);
            long a = virtualStack.popLong(frame);
            virtualStack.pushLong(frame, a ^ b);
            return advanceToNextBytecode(curBCI, LXOR, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IINC, safepoint = false)
        private static long iincHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int localIndex = BytecodeStream.uncheckedReadLocalIndex1(frame.code, curBCI);
            int increment = BytecodeStream.uncheckedReadIncrement1(frame.code, curBCI);
            frame.incrementLocalInt(localIndex, increment);
            return advanceToNextBytecode(curBCI, IINC, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = I2L, safepoint = false)
        private static long i2lHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = virtualStack.popInt(frame);
            virtualStack.pushLong(frame, value);
            return advanceToNextBytecode(curBCI, I2L, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = I2F, safepoint = false)
        private static long i2fHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = virtualStack.popInt(frame);
            virtualStack.pushFloat(frame, value);
            return advanceToNextBytecode(curBCI, I2F, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = I2D, safepoint = false)
        private static long i2dHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = virtualStack.popInt(frame);
            virtualStack.pushDouble(frame, value);
            return advanceToNextBytecode(curBCI, I2D, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = L2I, safepoint = false)
        private static long l2iHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long value = virtualStack.popLong(frame);
            virtualStack.pushInt(frame, (int) value);
            return advanceToNextBytecode(curBCI, L2I, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = L2F, safepoint = false)
        private static long l2fHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long value = virtualStack.popLong(frame);
            virtualStack.pushFloat(frame, value);
            return advanceToNextBytecode(curBCI, L2F, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = L2D, safepoint = false)
        private static long l2dHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long value = virtualStack.popLong(frame);
            virtualStack.pushDouble(frame, value);
            return advanceToNextBytecode(curBCI, L2D, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = F2I, safepoint = false)
        private static long f2iHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float value = virtualStack.popFloat(frame);
            virtualStack.pushInt(frame, (int) value);
            return advanceToNextBytecode(curBCI, F2I, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = F2L, safepoint = false)
        private static long f2lHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float value = virtualStack.popFloat(frame);
            virtualStack.pushLong(frame, (long) value);
            return advanceToNextBytecode(curBCI, F2L, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = F2D, safepoint = false)
        private static long f2dHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float value = virtualStack.popFloat(frame);
            virtualStack.pushDouble(frame, value);
            return advanceToNextBytecode(curBCI, F2D, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = D2I, safepoint = false)
        private static long d2iHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double value = virtualStack.popDouble(frame);
            virtualStack.pushInt(frame, (int) value);
            return advanceToNextBytecode(curBCI, D2I, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = D2L, safepoint = false)
        private static long d2lHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double value = virtualStack.popDouble(frame);
            virtualStack.pushLong(frame, (long) value);
            return advanceToNextBytecode(curBCI, D2L, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = D2F, safepoint = false)
        private static long d2fHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double value = virtualStack.popDouble(frame);
            virtualStack.pushFloat(frame, (float) value);
            return advanceToNextBytecode(curBCI, D2F, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = I2B, safepoint = false)
        private static long i2bHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, (byte) value);
            return advanceToNextBytecode(curBCI, I2B, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = I2C, safepoint = false)
        private static long i2cHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, (char) value);
            return advanceToNextBytecode(curBCI, I2C, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = I2S, safepoint = false)
        private static long i2sHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int value = virtualStack.popInt(frame);
            virtualStack.pushInt(frame, (short) value);
            return advanceToNextBytecode(curBCI, I2S, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LCMP, safepoint = false)
        private static long lcmpHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long y = virtualStack.popLong(frame);
            long x = virtualStack.popLong(frame);
            virtualStack.pushInt(frame, Long.compare(x, y));
            return advanceToNextBytecode(curBCI, LCMP, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FCMPL, safepoint = false)
        private static long fcmplHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float y = virtualStack.popFloat(frame);
            float x = virtualStack.popFloat(frame);
            virtualStack.pushInt(frame, compareFloatLess(y, x));
            return advanceToNextBytecode(curBCI, FCMPL, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = FCMPG, safepoint = false)
        private static long fcmpgHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            float y = virtualStack.popFloat(frame);
            float x = virtualStack.popFloat(frame);
            virtualStack.pushInt(frame, compareFloatGreater(y, x));
            return advanceToNextBytecode(curBCI, FCMPG, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DCMPL, safepoint = false)
        private static long dcmplHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double y = virtualStack.popDouble(frame);
            double x = virtualStack.popDouble(frame);
            virtualStack.pushInt(frame, compareDoubleLess(y, x));
            return advanceToNextBytecode(curBCI, DCMPL, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = DCMPG, safepoint = false)
        private static long dcmpgHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            double y = virtualStack.popDouble(frame);
            double x = virtualStack.popDouble(frame);
            virtualStack.pushInt(frame, compareDoubleGreater(y, x));
            return advanceToNextBytecode(curBCI, DCMPG, frame, virtualStack);
        }

        @AlwaysInline("Fold branch opcode in individual handlers")
        private static long branch(long curBCI, InterpreterFrame frame, int curOpcode, boolean branchTaken, InterpreterOperandStack virtualStack) {
            profileBranch(frame.methodProfile, curBCI, branchTaken);
            if (branchTaken) {
                long targetBCI = BytecodeStream.uncheckedReadBranchDest2(frame.code, curBCI);
                return finishJump(curBCI, targetBCI, frame, virtualStack);
            }
            return advanceToNextBytecode(curBCI, curOpcode, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFEQ, safepoint = false)
        private static long ifeqHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int operand = virtualStack.popInt(frame);
            boolean branchTaken = operand == 0;
            return branch(curBCI, frame, IFEQ, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFNE, safepoint = false)
        private static long ifneHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int operand = virtualStack.popInt(frame);
            boolean branchTaken = operand != 0;
            return branch(curBCI, frame, IFNE, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFLT, safepoint = false)
        private static long ifltHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int operand = virtualStack.popInt(frame);
            boolean branchTaken = operand < 0;
            return branch(curBCI, frame, IFLT, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFGE, safepoint = false)
        private static long ifgeHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int operand = virtualStack.popInt(frame);
            boolean branchTaken = operand >= 0;
            return branch(curBCI, frame, IFGE, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFGT, safepoint = false)
        private static long ifgtHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int operand = virtualStack.popInt(frame);
            boolean branchTaken = operand > 0;
            return branch(curBCI, frame, IFGT, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFLE, safepoint = false)
        private static long ifleHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int operand = virtualStack.popInt(frame);
            boolean branchTaken = operand <= 0;
            return branch(curBCI, frame, IFLE, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ICMPEQ, safepoint = false)
        private static long ifIcmpeqHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int rhs = virtualStack.popInt(frame);
            int lhs = virtualStack.popInt(frame);
            boolean branchTaken = lhs == rhs;
            return branch(curBCI, frame, IF_ICMPEQ, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ICMPNE, safepoint = false)
        private static long ifIcmpneHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int rhs = virtualStack.popInt(frame);
            int lhs = virtualStack.popInt(frame);
            boolean branchTaken = lhs != rhs;
            return branch(curBCI, frame, IF_ICMPNE, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ICMPLT, safepoint = false)
        private static long ifIcmpltHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int rhs = virtualStack.popInt(frame);
            int lhs = virtualStack.popInt(frame);
            boolean branchTaken = lhs < rhs;
            return branch(curBCI, frame, IF_ICMPLT, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ICMPGE, safepoint = false)
        private static long ifIcmpgeHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int rhs = virtualStack.popInt(frame);
            int lhs = virtualStack.popInt(frame);
            boolean branchTaken = lhs >= rhs;
            return branch(curBCI, frame, IF_ICMPGE, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ICMPGT, safepoint = false)
        private static long ifIcmpgtHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int rhs = virtualStack.popInt(frame);
            int lhs = virtualStack.popInt(frame);
            boolean branchTaken = lhs > rhs;
            return branch(curBCI, frame, IF_ICMPGT, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ICMPLE, safepoint = false)
        private static long ifIcmpleHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int rhs = virtualStack.popInt(frame);
            int lhs = virtualStack.popInt(frame);
            boolean branchTaken = lhs <= rhs;
            return branch(curBCI, frame, IF_ICMPLE, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ACMPEQ, safepoint = false)
        private static long ifAcmpeqHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object rhs = virtualStack.popObject(frame);
            Object lhs = virtualStack.popObject(frame);
            boolean branchTaken = lhs == rhs;
            return branch(curBCI, frame, IF_ACMPEQ, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IF_ACMPNE, safepoint = false)
        private static long ifAcmpneHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object rhs = virtualStack.popObject(frame);
            Object lhs = virtualStack.popObject(frame);
            boolean branchTaken = lhs != rhs;
            return branch(curBCI, frame, IF_ACMPNE, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFNULL, safepoint = false)
        private static long ifnullHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object operand = virtualStack.popObject(frame);
            boolean branchTaken = operand == null;
            return branch(curBCI, frame, IFNULL, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = IFNONNULL, safepoint = false)
        private static long ifnonnullHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object operand = virtualStack.popObject(frame);
            boolean branchTaken = operand != null;
            return branch(curBCI, frame, IFNONNULL, branchTaken, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = GOTO, safepoint = false)
        private static long gotoHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long targetBCI = BytecodeStream.uncheckedReadBranchDest2(frame.code, curBCI);
            return finishJump(curBCI, targetBCI, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = GOTO_W, safepoint = false)
        private static long gotoWHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long targetBCI = BytecodeStream.uncheckedReadBranchDest4(frame.code, curBCI);
            return finishJump(curBCI, targetBCI, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = JSR, safepoint = false)
        private static long jsrHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int returnBCI = (int) (curBCI + ConstantBytecodes.lengthOf(JSR));
            virtualStack.pushReturnAddress(frame, returnBCI);
            long targetBCI = BytecodeStream.uncheckedReadBranchDest2(frame.code, curBCI);
            return finishJump(curBCI, targetBCI, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = JSR_W, safepoint = false)
        private static long jsrWHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int returnBCI = (int) (curBCI + ConstantBytecodes.lengthOf(JSR_W));
            virtualStack.pushReturnAddress(frame, returnBCI);
            long targetBCI = BytecodeStream.uncheckedReadBranchDest4(frame.code, curBCI);
            return finishJump(curBCI, targetBCI, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = RET, safepoint = false)
        private static long retHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int localIndex = BytecodeStream.uncheckedReadLocalIndex1(frame.code, curBCI);
            int targetBCI = frame.getLocalReturnAddress(localIndex);
            return finishJump(curBCI, targetBCI, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = TABLESWITCH, safepoint = false)
        private static long tableswitchHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int index = virtualStack.peekInt(frame, -1);
            int low = TableSwitch.uncheckedLowKey(frame.code, curBCI);
            int high = TableSwitch.uncheckedHighKey(frame.code, curBCI);
            assert low <= high;

            long targetBCI;
            if (low <= index && index <= high) {
                targetBCI = TableSwitch.uncheckedTargetAt(frame.code, curBCI, index - low);
            } else {
                targetBCI = TableSwitch.uncheckedDefaultTarget(frame.code, curBCI);
            }
            virtualStack.pop1(frame, false);
            return finishJump(curBCI, targetBCI, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = LOOKUPSWITCH, safepoint = false)
        private static long lookupswitchHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int key = virtualStack.peekInt(frame, -1);
            int low = 0;
            int high = LookupSwitch.uncheckedNumberOfCases(frame.code, curBCI) - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                int midVal = LookupSwitch.uncheckedKeyAt(frame.code, curBCI, mid);
                if (midVal < key) {
                    low = mid + 1;
                } else if (midVal > key) {
                    high = mid - 1;
                } else {
                    virtualStack.pop1(frame, false);
                    return finishJump(curBCI, curBCI + LookupSwitch.uncheckedOffsetAt(frame.code, curBCI, mid), frame, virtualStack);
                }
            }

            virtualStack.pop1(frame, false);
            return finishJump(curBCI, LookupSwitch.uncheckedDefaultTarget(frame.code, curBCI), frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = GETSTATIC)
        private static long getstaticHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            InterpreterResolvedJavaField resolvedJavaField = resolveField(frame.method, GETSTATIC, frame.code, curBCI);
            getStaticField(frame, resolvedJavaField, virtualStack);
            return advanceToNextBytecode(curBCI, GETSTATIC, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = GETFIELD)
        private static long getfieldHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            InterpreterResolvedJavaField resolvedJavaField = resolveField(frame.method, GETFIELD, frame.code, curBCI);
            getInstanceField(frame, resolvedJavaField, virtualStack);
            return advanceToNextBytecode(curBCI, GETFIELD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = QUICK_GETSTATIC)
        private static long quickGetstaticHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            InterpreterResolvedJavaField resolvedJavaField = resolveQuickenedField(frame.method, GETSTATIC, BytecodeStream.uncheckedReadCPI2(frame.code, curBCI));
            getStaticField(frame, resolvedJavaField, virtualStack);
            return advanceToNextBytecode(curBCI, QUICK_GETSTATIC, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = QUICK_GETFIELD)
        private static long quickGetfieldHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            InterpreterResolvedJavaField resolvedJavaField = resolveQuickenedField(frame.method, GETFIELD, BytecodeStream.uncheckedReadCPI2(frame.code, curBCI));
            getInstanceField(frame, resolvedJavaField, virtualStack);
            return advanceToNextBytecode(curBCI, QUICK_GETFIELD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = PUTSTATIC)
        private static long putstaticHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            InterpreterResolvedJavaField field = resolveField(frame.method, PUTSTATIC, frame.code, curBCI);
            putStaticField(frame, field, virtualStack);
            return advanceToNextBytecode(curBCI, PUTSTATIC, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = PUTFIELD)
        private static long putfieldHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            InterpreterResolvedJavaField field = resolveField(frame.method, PUTFIELD, frame.code, curBCI);
            putInstanceField(frame, field, virtualStack);
            return advanceToNextBytecode(curBCI, PUTFIELD, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = QUICK_PUTSTATIC)
        private static long quickPutstaticHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            InterpreterResolvedJavaField field = resolveQuickenedField(frame.method, PUTSTATIC, BytecodeStream.uncheckedReadCPI2(frame.code, curBCI));
            putStaticField(frame, field, virtualStack);
            return advanceToNextBytecode(curBCI, QUICK_PUTSTATIC, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = QUICK_PUTFIELD)
        private static long quickPutfieldHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            InterpreterResolvedJavaField field = resolveQuickenedField(frame.method, PUTFIELD, BytecodeStream.uncheckedReadCPI2(frame.code, curBCI));
            putInstanceField(frame, field, virtualStack);
            return advanceToNextBytecode(curBCI, QUICK_PUTFIELD, frame, virtualStack);
        }

        @AlwaysInline("Fold invoke opcode in individual handlers")
        private static long invokeBytecode(long curBCI, InterpreterFrame frame, int curOpcode, InterpreterOperandStack virtualStack) {
            boolean preferStayInInterpreter = frame.forceStayInInterpreter;
            if (debuggerEventsSupported()) {
                preferStayInInterpreter |= frame.debugState.beforeInvoke();
            }

            try {
                invoke(frame, frame.methodProfile, frame.method, frame.code, (int) curBCI, curOpcode, frame.forceStayInInterpreter, preferStayInInterpreter, virtualStack);
            } finally {
                if (debuggerEventsSupported()) {
                    frame.debugState.afterInvoke();
                }
            }
            return advanceToNextBytecode(curBCI, curOpcode, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INVOKEVIRTUAL)
        private static long invokevirtualHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            return invokeBytecode(curBCI, frame, INVOKEVIRTUAL, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INVOKESPECIAL)
        private static long invokespecialHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            return invokeBytecode(curBCI, frame, INVOKESPECIAL, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INVOKESTATIC)
        private static long invokestaticHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            return invokeBytecode(curBCI, frame, INVOKESTATIC, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INVOKEINTERFACE)
        private static long invokeinterfaceHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            return invokeBytecode(curBCI, frame, INVOKEINTERFACE, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INVOKEDYNAMIC)
        private static long invokedynamicHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            boolean preferStayInInterpreter = frame.forceStayInInterpreter;
            if (debuggerEventsSupported()) {
                preferStayInInterpreter |= frame.debugState.beforeInvoke();
            }

            try {
                long top = virtualStack.topForFrameStackOperation();
                int slotDelta = invokeDynamicBytecode((int) curBCI, frame, top, preferStayInInterpreter);
                virtualStack.applyFrameStackOperationDelta(slotDelta);
            } finally {
                if (debuggerEventsSupported()) {
                    frame.debugState.afterInvoke();
                }
            }
            return advanceToNextBytecode(curBCI, INVOKEDYNAMIC, frame, virtualStack);
        }

        @NeverInline("Keep stack-consuming INVOKEDYNAMIC invocation out of bytecode-handler stubs")
        private static int invokeDynamicBytecode(int curBCI, InterpreterFrame frame, long top, boolean preferStayInInterpreter) {
            int fullCPI = BytecodeStream.uncheckedReadCPI4(frame.code, curBCI);
            if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, fullCPI == 0)) {
                // This can happen for the debugger
                throw noSuchMethodError(INVOKEDYNAMIC, null);
            }

            Object appendix;
            InterpreterResolvedJavaMethod seedMethod;

            int indyCPI = fullCPI >>> 16;
            int extraCPI = fullCPI & 0xFFFF;
            InterpreterResolvedJavaMethod method = frame.method;
            Object indyEntry = method.getConstantPool().uncheckedResolvedAt(indyCPI, method.getDeclaringClass());
            if (indyEntry instanceof ResolvedInvokeDynamicConstant invokeDynamicConstant) {
                // runtime-loaded case
                if (extraCPI == 0) {
                    extraCPI = linkInvokeDynamicCallSite(invokeDynamicConstant, method, frame.code, curBCI, indyCPI);
                }
                CallSiteLink link = invokeDynamicConstant.getCallSiteLink(method, frame.code, curBCI, extraCPI);
                if (link instanceof SuccessfulCallSiteLink successfulCallSiteLink) {
                    appendix = successfulCallSiteLink.getUnboxedAppendix();
                    seedMethod = successfulCallSiteLink.getInvoker();
                } else {
                    throw SemanticJavaException.raise(((FailedCallSiteLink) link).getFailure());
                }
            } else if (indyEntry instanceof InterpreterResolvedJavaMethod entryMethod) {
                // AOT case
                seedMethod = entryMethod;
                Object appendixEntry = method.getConstantPool().uncheckedResolvedAt(extraCPI, method.getDeclaringClass());
                if (JavaConstant.NULL_POINTER.equals(appendixEntry)) {
                    // The appendix is deliberately null.
                    appendix = null;
                } else if (appendixEntry instanceof ReferenceConstant<?> referenceConstant) {
                    appendix = referenceConstant.getReferent();
                    if (appendix == null) {
                        throw SemanticJavaException.raiseIncompatibleClassChangeError("INVOKEDYNAMIC appendix was not included in the image heap");
                    }
                } else {
                    throw unexpectedInvokeDynamicAppendixConstant(appendixEntry);
                }
            } else {
                throw unexpectedInvokeDynamicConstant(indyEntry);
            }

            InterpreterUnresolvedSignature seedSignature = seedMethod.getSignature();
            boolean hasReceiver = !seedMethod.isStatic();

            InterpreterOperandStack materializedStack = new InterpreterOperandStack(top);
            Object[] calleeArgs = materializedStack.popArgumentsWithAppendix(frame, hasReceiver, seedSignature, appendix);
            if (hasReceiver) {
                Object receiver = calleeArgs[0];
                profileType(frame.methodProfile, curBCI, receiver);
                receiver = nullCheck(receiver);
                calleeArgs[0] = receiver;
            }

            Object retObj = InterpreterToVM.dispatchInvocation(seedMethod, calleeArgs, CallKind.DIRECT, frame.forceStayInInterpreter, preferStayInInterpreter, false);
            materializedStack.pushKind(frame, retObj, seedSignature.getReturnKind());
            return materializedStack.slotDeltaFrom(top);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = NEW)
        private static long newHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object value = InterpreterToVM.createNewReference(resolveType(frame.method, NEW, BytecodeStream.uncheckedReadCPI2(frame.code, curBCI)));
            virtualStack.pushObject(frame, value);
            return advanceToNextBytecode(curBCI, NEW, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = NEWARRAY)
        private static long newarrayHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int length = virtualStack.peekInt(frame, -1);
            byte primitiveType = BytecodeStream.uncheckedReadByte(frame.code, curBCI);
            Object array = InterpreterToVM.createNewPrimitiveArray(primitiveType, length);
            virtualStack.pop1(frame, false);
            virtualStack.pushObject(frame, array);
            return advanceToNextBytecode(curBCI, NEWARRAY, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ANEWARRAY)
        private static long anewarrayHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int length = virtualStack.peekInt(frame, -1);
            Object array = InterpreterToVM.createNewReferenceArray(resolveType(frame.method, ANEWARRAY, BytecodeStream.uncheckedReadCPI2(frame.code, curBCI)), length);
            virtualStack.pop1(frame, false);
            virtualStack.pushObject(frame, array);
            return advanceToNextBytecode(curBCI, ANEWARRAY, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ARRAYLENGTH, safepoint = false)
        private static long arraylengthHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object array = virtualStack.peekObject(frame, -1);
            Object nonNullArray = nullCheck(array);
            int length = InterpreterToVM.arrayLength(nonNullArray);
            virtualStack.pop1(frame);
            virtualStack.pushInt(frame, length);
            return advanceToNextBytecode(curBCI, ARRAYLENGTH, frame, virtualStack);
        }

        @SuppressWarnings("unused")
        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = ATHROW)
        private static long athrowHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object exception = virtualStack.popObject(frame);
            Object nonNullException = nullCheck(exception);
            throw SemanticJavaException.raise((Throwable) nonNullException);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = CHECKCAST)
        private static long checkcastHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.peekObject(frame, -1);
            profileType(frame.methodProfile, curBCI, receiver);
            if (receiver != null) {
                InterpreterResolvedJavaType type = resolveType(frame.method, CHECKCAST, BytecodeStream.uncheckedReadCPI2(frame.code, curBCI));
                InterpreterToVM.checkCast(receiver, type.getJavaClass());
            }
            return advanceToNextBytecode(curBCI, CHECKCAST, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = INSTANCEOF)
        private static long instanceofHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = virtualStack.popObject(frame);
            profileType(frame.methodProfile, curBCI, receiver);
            int result = (receiver != null && InterpreterToVM.instanceOf(receiver, resolveType(frame.method, INSTANCEOF, BytecodeStream.uncheckedReadCPI2(frame.code, curBCI)))) ? 1 : 0;
            virtualStack.pushInt(frame, result);
            return advanceToNextBytecode(curBCI, INSTANCEOF, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = MONITORENTER)
        private static long monitorenterHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = nullCheck(virtualStack.peekObject(frame, -1));
            InterpreterToVM.monitorEnter(frame, receiver);
            virtualStack.pop1(frame);
            return advanceToNextBytecode(curBCI, MONITORENTER, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = MONITOREXIT)
        private static long monitorexitHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            Object receiver = nullCheck(virtualStack.peekObject(frame, -1));
            InterpreterToVM.monitorExit(frame, receiver);
            virtualStack.pop1(frame);
            return advanceToNextBytecode(curBCI, MONITOREXIT, frame, virtualStack);
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = WIDE, safepoint = false)
        private static long wideHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            int wideOpcode = BytecodeStream.uncheckedOpcode(frame.code, curBCI + 1);
            switch (wideOpcode) {
                case ILOAD -> {
                    int index = BytecodeStream.uncheckedReadLocalIndex2(frame.code, curBCI);
                    int value = frame.getLocalInt(index);
                    virtualStack.pushInt(frame, value);
                }
                case LLOAD -> {
                    int index = BytecodeStream.uncheckedReadLocalIndex2(frame.code, curBCI);
                    long value = frame.getLocalLong(index);
                    virtualStack.pushLong(frame, value);
                }
                case FLOAD -> {
                    int index = BytecodeStream.uncheckedReadLocalIndex2(frame.code, curBCI);
                    float value = frame.getLocalFloat(index);
                    virtualStack.pushFloat(frame, value);
                }
                case DLOAD -> {
                    int index = BytecodeStream.uncheckedReadLocalIndex2(frame.code, curBCI);
                    double value = frame.getLocalDouble(index);
                    virtualStack.pushDouble(frame, value);
                }
                case ALOAD -> {
                    int index = BytecodeStream.uncheckedReadLocalIndex2(frame.code, curBCI);
                    Object value = frame.getLocalObject(index);
                    virtualStack.pushObject(frame, value);
                }

                case ISTORE -> {
                    int index = BytecodeStream.uncheckedReadLocalIndex2(frame.code, curBCI);
                    int value = virtualStack.popInt(frame);
                    frame.setLocalInt(index, value);
                }
                case LSTORE -> {
                    int index = BytecodeStream.uncheckedReadLocalIndex2(frame.code, curBCI);
                    long value = virtualStack.popLong(frame);
                    frame.setLocalLong(index, value);
                }
                case FSTORE -> {
                    int index = BytecodeStream.uncheckedReadLocalIndex2(frame.code, curBCI);
                    float value = virtualStack.popFloat(frame);
                    frame.setLocalFloat(index, value);
                }
                case DSTORE -> {
                    int index = BytecodeStream.uncheckedReadLocalIndex2(frame.code, curBCI);
                    double value = virtualStack.popDouble(frame);
                    frame.setLocalDouble(index, value);
                }
                case ASTORE -> {
                    int index = BytecodeStream.uncheckedReadLocalIndex2(frame.code, curBCI);
                    Object value = virtualStack.popObject(frame);
                    frame.setLocalObject(index, value);
                }
                case IINC -> {
                    int index = BytecodeStream.uncheckedReadLocalIndex2(frame.code, curBCI);
                    int increment = BytecodeStream.uncheckedReadIncrement2(frame.code, curBCI);
                    frame.incrementLocalInt(index, increment);
                }
                case RET -> {
                    int index = BytecodeStream.uncheckedReadLocalIndex2(frame.code, curBCI);
                    int targetBCI = frame.getLocalReturnAddress(index);
                    return finishJump(curBCI, targetBCI, frame, virtualStack);
                }
                default -> throw invalidOpcode(wideOpcode);
            }
            long nextBCI = curBCI + ((wideOpcode == IINC) ? 6 : 4);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        @NeverInlineTrivial(reason = "BytecodeInterpreterHandler")
        @BytecodeInterpreterHandler(value = MULTIANEWARRAY)
        private static long multianewarrayHandler(long curBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long top = virtualStack.topForFrameStackOperation();
            int slotDelta = allocateMultiArray(frame, top, curBCI);
            virtualStack.applyFrameStackOperationDelta(slotDelta);
            return advanceToNextBytecode(curBCI, MULTIANEWARRAY, frame, virtualStack);
        }

        /**
         * Completes a bytecode that transfers control to {@code targetBCI}. The caller must
         * complete the bytecode's operand-stack operations before invoking this helper. This
         * performs the profiling, safepoint, and OSR checks associated with the transfer and
         * prepares the selected target opcode for dispatch.
         *
         * @return the checked target BCI
         */
        @AlwaysInline("Keep branch completion on the fast path")
        private static long finishJump(long curBCI, long targetBCI, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long nextBCI = beforeJumpChecks(frame, curBCI, targetBCI, virtualStack.topForFrameStackOperation());
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }

        /**
         * Completes a bytecode that continues at its sequential successor. The caller must
         * complete the bytecode's operand-stack operations before invoking this helper. This
         * advances the BCI by the encoded bytecode length and prepares the opcode at the resulting
         * BCI for dispatch.
         *
         * <p>
         * This helper is only suitable when {@link Bytecodes#lengthOf(int)} describes the
         * transition to the next bytecode. Branches and other bytecodes with a separately selected
         * successor must prepare that target explicitly.
         *
         * @return the BCI of the prepared successor bytecode
         */
        @AlwaysInline("Keep common opcode completion on the fast path")
        private static long advanceToNextBytecode(long curBCI, int curOpcode, InterpreterFrame frame, InterpreterOperandStack virtualStack) {
            long nextBCI = curBCI + Bytecodes.lengthOf(curOpcode);
            prepareOpcodeForDispatch(nextBCI, frame, virtualStack);
            return nextBCI;
        }
    }

    @AlwaysInline("Profile-site guards must fold away when Ristretto is disabled in the hosted image.")
    private static void profileType(MethodProfile methodProfile, long bci, Object o) {
        if (SubstrateOptions.useRistretto() && methodProfile != null) {
            methodProfile.profileReceiver((int) bci, o);
        }
    }

    @AlwaysInline("Profile-site guards must fold away when Ristretto is disabled in the hosted image.")
    private static void profileBranch(MethodProfile methodProfile, long curBCI, boolean branchTaken1) {
        if (SubstrateOptions.useRistretto() && methodProfile != null) {
            methodProfile.profileBranch((int) curBCI, branchTaken1);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException uncheckedThrow(Throwable e) throws T {
        throw (T) e;
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
     * {@link #beforeJumpSafepoint(int, int)} is used by callers that only need the target BCI and
     * must stay in the interpreter.
     */
    @SuppressWarnings("unused")
    private static long beforeJumpChecks(InterpreterFrame frame, long curBCI, long targetBCI, long stackTop) {
        if (targetBCI <= curBCI) {
            GraalDirectives.safepoint();
            if (SubstrateOptions.useRistretto() && !frame.forceStayInInterpreter) {
                OSRResult result = RistrettoOSRSupport.tryOSR(frame.method, frame.methodProfile, frame, (int) targetBCI, (int) stackTop);
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

    public static int beforeJumpSafepoint(int curBCI, int targetBCI) {
        if (targetBCI <= curBCI) {
            GraalDirectives.safepoint();
        }
        return targetBCI;
    }

    public static ExceptionHandler resolveExceptionHandler(InterpreterResolvedJavaMethod method, int bci, Throwable ex) {
        ExceptionHandler[] handlers = method.getExceptionHandlers();
        ExceptionHandler resolved = null;
        for (ExceptionHandler toCheck : handlers) {
            if (bci >= toCheck.getStartBCI() && bci < toCheck.getEndBCI()) {
                JavaType catchType = null;
                if (!toCheck.isCatchAll()) {
                    // Exception-handler catch types are resolved like INSTANCEOF types.
                    char cpi = (char) toCheck.catchTypeCPI();
                    // CPI 0 is a marker for unresolvable AND unknown entry
                    if (cpi != 0) {
                        try {
                            catchType = getConstantPool(method).uncheckedResolvedTypeAt(method.getDeclaringClass(), cpi);
                        } catch (UnsupportedResolutionException e) {
                            // Leave catchType null and skip the unresolvable handler.
                        } catch (Throwable t) {
                            throw SemanticJavaException.raise(t);
                        }
                    }
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

    @NeverInline("Exception slow path")
    private static SemanticJavaException noClassDefFoundError(int opcode, JavaType javaType) {
        String message = (javaType != null)
                        ? javaType.toJavaName()
                        : MetadataUtil.fmt("%s: (cpi = 0) unknown type", Bytecodes.nameOf(opcode));
        throw SemanticJavaException.raiseInlined(new NoClassDefFoundError(message));
    }

    @NeverInline("Exception slow path")
    private static SemanticJavaException noSuchMethodError(int opcode, JavaMethod javaMethod) {
        String message = (javaMethod != null)
                        ? javaMethod.format("%H.%n(%P)")
                        : MetadataUtil.fmt("%s: (cpi = 0) unknown method", Bytecodes.nameOf(opcode));
        throw SemanticJavaException.raiseInlined(new NoSuchMethodError(message));
    }

    @NeverInline("Keep incompatible-receiver exception construction out of bytecode-handler stubs")
    private static SemanticJavaException incompatibleInvokeReceiver(ResolvedJavaType receiverType, InterpreterResolvedJavaType symbolicHolder) {
        String message = MetadataUtil.fmt("Class %s does not implement the requested interface %s",
                        receiverType.toJavaName(),
                        symbolicHolder.toJavaName());
        throw SemanticJavaException.raiseInlined(new IncompatibleClassChangeError(message));
    }

    private static void loadConstant(InterpreterFrame frame, long top, int cpi, int opcode) {
        assert opcode == LDC || opcode == LDC_W;
        InterpreterConstantPool pool = getConstantPool(frame.method);
        byte numericTag = pool.uncheckedTagValueAt(cpi);
        if (GraalDirectives.injectBranchProbability(GraalDirectives.UNLIKELY_PROBABILITY,
                        numericTag == ConstantPool.CONSTANT_Integer)) {
            frame.setStackInt(top, pool.uncheckedIntAt(cpi));
            return;
        }
        if (GraalDirectives.injectBranchProbability(GraalDirectives.FASTPATH_PROBABILITY,
                        numericTag == ConstantPool.CONSTANT_Float)) {
            frame.setStackFloat(top, pool.uncheckedFloatAt(cpi));
            return;
        }
        resolveConstantAtSlowPath(frame, top, cpi, opcode, pool);
    }

    private static void loadConstant2(InterpreterFrame frame, long top, int cpi) {
        VMError.guarantee(cpi != 0);
        InterpreterConstantPool pool = getConstantPool(frame.method);
        byte numericTag = pool.uncheckedTagValueAt(cpi);
        if (numericTag == ConstantPool.CONSTANT_Long) {
            frame.setStackLong(top, pool.uncheckedLongAt(cpi));
            return;
        }
        if (GraalDirectives.injectBranchProbability(GraalDirectives.FASTPATH_PROBABILITY,
                        numericTag == ConstantPool.CONSTANT_Double)) {
            frame.setStackDouble(top, pool.uncheckedDoubleAt(cpi));
            return;
        }
        resolveConstantAtSlowPath(frame, top, cpi, LDC2_W, pool);
    }

    /**
     * Resolves non-primitive constant-pool entries that can execute arbitrary Java code.
     */
    @NeverInline("Keep constant resolution out of the bytecode-handler stubs")
    private static void resolveConstantAtSlowPath(InterpreterFrame frame, long top, int cpi, int opcode, InterpreterConstantPool pool) {
        InterpreterResolvedJavaMethod method = frame.method;
        char narrowCpi = (char) cpi;
        ConstantPool.Tag tag = pool.uncheckedTagAt(cpi);
        switch (tag) {
            case CLASS -> {
                InterpreterResolvedJavaType resolvedType = resolveType(method, opcode, narrowCpi);
                frame.setStackObject(top, resolvedType.getJavaClass());
            }
            case STRING -> {
                String string = pool.resolveStringAt(cpi);
                frame.setStackObject(top, string);
            }
            case METHODTYPE -> {
                frame.setStackObject(top, resolveMethodType(pool, method, opcode, narrowCpi));
            }
            case METHODHANDLE -> {
                frame.setStackObject(top, resolveMethodHandle(pool, method, opcode, narrowCpi));
            }
            case DYNAMIC -> {
                Object constant = resolveDynamicConstant(pool, method, opcode, narrowCpi);
                switch (symbolToJvmciKind(pool.dynamicType(cpi))) {
                    case Boolean -> frame.setStackInt(top, (Boolean) constant ? 1 : 0);
                    case Byte -> frame.setStackInt(top, (Byte) constant);
                    case Short -> frame.setStackInt(top, (Short) constant);
                    case Char -> frame.setStackInt(top, (Character) constant);
                    case Int -> frame.setStackInt(top, (Integer) constant);
                    case Float -> frame.setStackFloat(top, (Float) constant);
                    case Long -> frame.setStackLong(top, (Long) constant);
                    case Double -> frame.setStackDouble(top, (Double) constant);
                    case Object -> frame.setStackObject(top, constant);
                    default -> throw VMError.shouldNotReachHere("Unexpected dynamic constant type " + pool.dynamicType(cpi));
                }
            }
            case INVOKEDYNAMIC -> {
                // TODO(peterssen): GR-68576 Storing the pre-resolved appendix in the CP is a
                // workaround for the JDWP debugger until proper INVOKEDYNAMIC resolution is
                // implemented.
                Object appendix = pool.uncheckedResolvedAt(cpi, null);
                if (appendix instanceof ReferenceConstant<?> referenceConstant) {
                    VMError.guarantee(referenceConstant.isNonNull(), FAILURE_CONSTANT_NOT_PART_OF_IMAGE_HEAP);
                    Object constantValue = referenceConstant.getReferent();
                    frame.setStackObject(top, constantValue);
                } else {
                    // Raw object.
                    frame.setStackObject(top, appendix);
                }
            }
            default -> throw VMError.unimplemented("LDC* constant pool type " + tag);
        }
    }

    private static InterpreterConstantPool getConstantPool(InterpreterResolvedJavaMethod method) {
        return method.getConstantPool();
    }

    @AlwaysInline("Keep invocation stack transitions in bytecode-handler stubs")
    private static void invoke(InterpreterFrame callerFrame, MethodProfile methodProfile, InterpreterResolvedJavaMethod method, byte[] code, int curBCI, int opcode,
                    boolean forceStayInInterpreter,
                    boolean preferStayInInterpreter, InterpreterOperandStack virtualStack) {
        LinkedInvoke linkedInvoke = getOrLinkInvoke(method, code, curBCI, opcode);
        boolean hasReceiver = opcode != INVOKESTATIC && linkedInvoke.hasReceiver;
        Object appendix = linkedInvoke.appendix;
        Object[] calleeArgs = virtualStack.popArguments(callerFrame, hasReceiver, linkedInvoke.signature, appendix);
        if (hasReceiver) {
            Object receiver = calleeArgs[0];
            profileType(methodProfile, curBCI, receiver);
            receiver = nullCheck(receiver);
            calleeArgs[0] = receiver;
            if (linkedInvoke.requiresSymbolicTypeCheck) {
                ResolvedJavaType receiverType = DynamicHub.fromClass(receiver.getClass()).getInterpreterType();
                if (linkedInvoke.symbolicHolder != null && !linkedInvoke.symbolicHolder.isAssignableFrom(receiverType)) {
                    throw incompatibleInvokeReceiver(receiverType, linkedInvoke.symbolicHolder);
                }
            }
        }

        Object retObj = InterpreterToVM.dispatchInvocation(linkedInvoke.seedMethod, calleeArgs, linkedInvoke.callKind, forceStayInInterpreter, preferStayInInterpreter, false);
        virtualStack.pushKind(callerFrame, retObj, linkedInvoke.returnKind);
    }

    @NeverInline("Keep INVOKEDYNAMIC first-link work out of the bytecode-handler stub")
    private static int linkInvokeDynamicCallSite(ResolvedInvokeDynamicConstant invokeDynamicConstant, InterpreterResolvedJavaMethod method, byte[] code, int curBCI, int indyCPI) {
        int extraCPI;
        try {
            extraCPI = invokeDynamicConstant.link((RuntimeInterpreterConstantPool) method.getConstantPool(), method.getDeclaringClass().getJavaClass(), method, curBCI);
            assert extraCPI != 0;
        } catch (Throwable e) {
            throw SemanticJavaException.raiseInlined(e);
        }
        method.patchInvokeDynamicExtraCPI(curBCI, extraCPI);
        assert BytecodeStream.readIndyExtraCPIVolatile(code, curBCI) == extraCPI;
        assert BytecodeStream.uncheckedReadCPI2(code, curBCI) == indyCPI;
        return extraCPI;
    }

    @NeverInline("Keep unexpected INVOKEDYNAMIC appendix diagnostics out of the bytecode-handler stub")
    private static RuntimeException unexpectedInvokeDynamicAppendixConstant(Object appendixEntry) {
        return VMError.shouldNotReachHere("Unexpected INVOKEDYNAMIC appendix constant: " + appendixEntry);
    }

    @NeverInline("Keep unexpected INVOKEDYNAMIC constant diagnostics out of the bytecode-handler stub")
    private static RuntimeException unexpectedInvokeDynamicConstant(Object indyEntry) {
        return VMError.shouldNotReachHere("Unexpected INVOKEDYNAMIC constant: " + indyEntry);
    }

    private static LinkedInvoke getOrLinkInvoke(InterpreterResolvedJavaMethod method, byte[] code, int curBCI, int opcode) {
        char cpi = BytecodeStream.uncheckedReadCPI2(code, curBCI);
        assert opcode == INVOKEVIRTUAL || opcode == INVOKESPECIAL || opcode == INVOKESTATIC || opcode == INVOKEINTERFACE : Bytecodes.nameOf(opcode);
        InterpreterConstantPool constantPool = getConstantPool(method);
        LinkedInvoke linkedInvoke = constantPool.uncheckedPeekLinkedInvoke(cpi, opcode);
        if (linkedInvoke != null) {
            return linkedInvoke;
        }
        return linkInvoke(method, opcode, cpi);
    }

    @NeverInline("Keep invoke resolution out of bytecode-handler stubs")
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
        boolean requiresSymbolicTypeCheck = getConstantPool(method).uncheckedTagAt(cpi) == ConstantPool.Tag.INTERFACE_METHOD_REF;

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
            throw SemanticJavaException.raiseInlined(e);
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
            return pool.uncheckedResolvedDynamicConstantAt(cpi, method.getDeclaringClass());
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
            return getConstantPool(method).uncheckedResolvedTypeAt(method.getDeclaringClass(), cpi);
        } catch (UnsupportedResolutionException e) {
            // CP does not support resolution, try to provide a hint of the non-resolvable entry.
            UnresolvedJavaType missingType = null;
            if (getConstantPool(method).uncheckedPeekCachedEntry(cpi) instanceof UnresolvedJavaType unresolvedJavaType) {
                missingType = unresolvedJavaType;
            }
            throw noClassDefFoundError(opcode, missingType);
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
            return getConstantPool(caller).uncheckedResolvedTypeAt(caller.getDeclaringClass(), holderCpi);
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
            return getConstantPool(method).uncheckedResolvedMethodAt(method.getDeclaringClass(), cpi);
        } catch (UnsupportedResolutionException e) {
            // CP does not support resolution, try to provide a hint of the non-resolvable entry.
            UnresolvedJavaMethod missingMethod = null;
            if (getConstantPool(method).uncheckedPeekCachedEntry(cpi) instanceof UnresolvedJavaMethod unresolvedJavaMethod) {
                missingMethod = unresolvedJavaMethod;
            }
            throw noSuchMethodError(opcode, missingMethod);
        } catch (Throwable t) {
            throw SemanticJavaException.raise(t);
        }
    }

    @NeverInline("Not yet quickened slow path")
    private static InterpreterResolvedJavaField resolveField(InterpreterResolvedJavaMethod method, int opcode, byte[] code, long bci) {
        assert opcode == GETFIELD || opcode == GETSTATIC || opcode == PUTFIELD || opcode == PUTSTATIC : Bytecodes.nameOf(opcode);
        char cpi = BytecodeStream.uncheckedReadCPI2(code, bci);
        if (GraalDirectives.injectBranchProbability(GraalDirectives.SLOWPATH_PROBABILITY, cpi == 0)) {
            String message = MetadataUtil.fmt("%s: (cpi = 0) unknown field", Bytecodes.nameOf(opcode));
            throw SemanticJavaException.raiseInlined(new NoSuchFieldError(message));
        }
        try {
            InterpreterResolvedJavaField field = getConstantPool(method).uncheckedResolvedFieldAt(method.getDeclaringClass(), cpi);

            // Apply the opcode-specific field rules after symbolic resolution.
            CremaLinkResolver.checkFieldAccessOrThrow(CremaRuntimeAccess.getInstance(), field, opcode, method.getDeclaringClass(), method);

            if (opcode == GETFIELD || opcode == GETSTATIC) {
                InterpreterUtil.guarantee(!field.isUndefined(), "Cannot load undefined field: %s", field);
            }
            if (opcode == PUTFIELD || opcode == PUTSTATIC) {
                InterpreterToVM.ensureMaterialized(field);
            }

            quickenFieldAccess(code, bci, opcode);
            return field;
        } catch (UnsupportedResolutionException e) {
            // CP does not support resolution, try to provide a hint of the non-resolvable entry.
            String message;
            if (getConstantPool(method).uncheckedPeekCachedEntry(cpi) instanceof UnresolvedJavaField unresolvedJavaField) {
                message = unresolvedJavaField.format("%H.%n");
            } else {
                message = MetadataUtil.fmt("%s: (cpi = 0) unknown field", Bytecodes.nameOf(opcode));
            }
            throw SemanticJavaException.raiseInlined(new NoSuchFieldError(message));
        } catch (SemanticJavaException e) {
            throw e;
        } catch (Throwable t) {
            throw SemanticJavaException.raiseInlined(t);
        }
    }

    private static InterpreterResolvedJavaField resolveQuickenedField(InterpreterResolvedJavaMethod method, int opcode, char cpi) {
        assert opcode == GETFIELD || opcode == GETSTATIC || opcode == PUTFIELD || opcode == PUTSTATIC : Bytecodes.nameOf(opcode);
        assert cpi != 0 : "Quickened field access requires a resolved constant pool index";
        try {
            // The first execution cached the resolved field after applying opcode-specific access checks.
            return (InterpreterResolvedJavaField) getConstantPool(method).uncheckedPeekCachedEntry(cpi);
        } catch (Throwable t) {
            throw InterpreterUtil.shouldNotReachHere("Quickened field access must use an already resolved field entry", t);
        }
    }

    private static void quickenFieldAccess(byte[] code, long bci, int opcode) {
        // Patch only the opcode: the CPI operand and BCI layout stay identical.
        BytecodeStream.patchOpcodeOpaque(code, bci, Bytecodes.quickenedFieldAccess(opcode));
    }

    // endregion Class/Field/Method resolution

    @NeverInline("Keep multi-array allocation out of bytecode-handler stubs")
    private static int allocateMultiArray(InterpreterFrame frame, long top, long bci) {
        ResolvedJavaType multiArrayType = resolveType(frame.method, MULTIANEWARRAY, BytecodeStream.uncheckedReadCPI2(frame.code, bci));
        int allocatedDimensions = BytecodeStream.uncheckedReadUByte(frame.code, bci + 3);
        assert multiArrayType.isArray() : multiArrayType;
        assert allocatedDimensions > 0 : allocatedDimensions;
        assert multiArrayType.getElementalType().getJavaKind() != JavaKind.Void;
        int[] dimensions = new int[allocatedDimensions];
        InterpreterOperandStack materializedStack = new InterpreterOperandStack(top);
        for (int i = allocatedDimensions - 1; i >= 0; --i) {
            dimensions[i] = materializedStack.popInt(frame);
        }
        Object value = InterpreterToVM.createMultiArray((InterpreterResolvedJavaType) multiArrayType, dimensions);
        materializedStack.pushObject(frame, value);
        return materializedStack.slotDeltaFrom(top);
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

    // endregion Arithmetic/binary operations

    // region Comparisons

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
     * Pops the value from the operand stack and stores it in a static field.
     * The field must already be resolved and verified.
     */
    @AlwaysInline("Keep stack access in the bytecode-handler stub")
    private static void putStaticField(InterpreterFrame frame, InterpreterResolvedJavaField field, InterpreterOperandStack virtualStack) {
        assert field.isStatic();
        assert !field.isUnmaterializedConstant();
        InterpreterToVM.ensureClassInitialized(field.getDeclaringClass());

        JavaKind kind = field.getJavaKind();
        Object receiver = field.getDeclaringClass().getStaticStorage(kind.isPrimitive(), field.getInstalledLayerNum());

        putFieldImpl(frame, field, kind, receiver, virtualStack);
    }

    /**
     * Pops and null-checks the receiver below the field value on the operand stack, then pops the
     * value and stores it in an instance field.
     * The field must already be resolved and verified.
     */
    @AlwaysInline("Keep stack access in the bytecode-handler stub")
    private static void putInstanceField(InterpreterFrame frame, InterpreterResolvedJavaField field, InterpreterOperandStack virtualStack) {
        assert !field.isStatic();
        assert !field.isUnmaterializedConstant();

        JavaKind kind = field.getJavaKind();
        int slotCount = kind.getSlotCount();
        Object receiver = nullCheck(virtualStack.peekObject(frame, -slotCount - 1));

        putFieldImpl(frame, field, kind, receiver, virtualStack);
        virtualStack.pop1(frame);
    }

    @AlwaysInline("Keep stack access in the bytecode-handler stub")
    private static void putFieldImpl(InterpreterFrame frame, InterpreterResolvedJavaField field, JavaKind kind, Object receiver, InterpreterOperandStack virtualStack) {
        switch (kind) {
            case Boolean -> {
                InterpreterToVM.setFieldBoolean(stackIntToBoolean(virtualStack.peekInt(frame, -1)), receiver, field, true);
                virtualStack.pop1(frame, false);
            }
            case Byte -> {
                InterpreterToVM.setFieldByte((byte) virtualStack.peekInt(frame, -1), receiver, field, true);
                virtualStack.pop1(frame, false);
            }
            case Char -> {
                InterpreterToVM.setFieldChar((char) virtualStack.peekInt(frame, -1), receiver, field, true);
                virtualStack.pop1(frame, false);
            }
            case Short -> {
                InterpreterToVM.setFieldShort((short) virtualStack.peekInt(frame, -1), receiver, field, true);
                virtualStack.pop1(frame, false);
            }
            case Int -> {
                InterpreterToVM.setFieldInt(virtualStack.peekInt(frame, -1), receiver, field, true);
                virtualStack.pop1(frame, false);
            }
            case Double -> {
                InterpreterToVM.setFieldDouble(virtualStack.peekDouble(frame, -1), receiver, field, true);
                virtualStack.pop2(frame, false);
            }
            case Float -> {
                InterpreterToVM.setFieldFloat(virtualStack.peekFloat(frame, -1), receiver, field, true);
                virtualStack.pop1(frame, false);
            }
            case Long -> {
                InterpreterToVM.setFieldLong(virtualStack.peekLong(frame, -1), receiver, field, true);
                virtualStack.pop2(frame, false);
            }
            case Object -> {
                InterpreterToVM.setFieldObject(virtualStack.peekObject(frame, -1), receiver, field, true);
                virtualStack.pop1(frame);
            }
            default -> throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
    }

    /**
     * Loads a static field and stores its value on the operand stack.
     * The field must already be resolved and verified.
     */
    @AlwaysInline("Keep stack access in the bytecode-handler stub")
    private static void getStaticField(InterpreterFrame frame, InterpreterResolvedJavaField field, InterpreterOperandStack virtualStack) {
        assert field.isStatic();
        InterpreterToVM.ensureClassInitialized(field.getDeclaringClass());

        JavaKind kind = field.getJavaKind();
        Object receiver = field.getDeclaringClass().getStaticStorage(kind.isPrimitive(), field.getInstalledLayerNum());

        // @formatter:off
        switch (kind) {
            case Boolean -> virtualStack.pushInt(frame, InterpreterToVM.getFieldBoolean(receiver, field, true) ? 1 : 0);
            case Byte    -> virtualStack.pushInt(frame, InterpreterToVM.getFieldByte(receiver, field, true));
            case Char    -> virtualStack.pushInt(frame, InterpreterToVM.getFieldChar(receiver, field, true));
            case Short   -> virtualStack.pushInt(frame, InterpreterToVM.getFieldShort(receiver, field, true));
            case Int     -> virtualStack.pushInt(frame, InterpreterToVM.getFieldInt(receiver, field, true));
            case Double  -> virtualStack.pushDouble(frame, InterpreterToVM.getFieldDouble(receiver, field, true));
            case Float   -> virtualStack.pushFloat(frame, InterpreterToVM.getFieldFloat(receiver, field, true));
            case Long    -> virtualStack.pushLong(frame, InterpreterToVM.getFieldLong(receiver, field, true));
            case Object  -> virtualStack.pushObject(frame, InterpreterToVM.getFieldObject(receiver, field, true));
            default      -> throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        // @formatter:on
    }

    /**
     * Pops and null-checks the receiver, then stores the loaded instance field value on the operand
     * stack in place of the receiver.
     * The field must already be resolved and verified.
     */
    @AlwaysInline("Keep stack access in the bytecode-handler stub")
    private static void getInstanceField(InterpreterFrame frame, InterpreterResolvedJavaField field, InterpreterOperandStack virtualStack) {
        assert !field.isStatic();

        Object receiver = nullCheck(virtualStack.peekObject(frame, -1));

        JavaKind kind = field.getJavaKind();
        switch (kind) {
            case Boolean -> {
                int value = InterpreterToVM.getFieldBoolean(receiver, field, true) ? 1 : 0;
                virtualStack.pop1(frame);
                virtualStack.pushInt(frame, value);
            }
            case Byte -> {
                int value = InterpreterToVM.getFieldByte(receiver, field, true);
                virtualStack.pop1(frame);
                virtualStack.pushInt(frame, value);
            }
            case Char -> {
                int value = InterpreterToVM.getFieldChar(receiver, field, true);
                virtualStack.pop1(frame);
                virtualStack.pushInt(frame, value);
            }
            case Short -> {
                int value = InterpreterToVM.getFieldShort(receiver, field, true);
                virtualStack.pop1(frame);
                virtualStack.pushInt(frame, value);
            }
            case Int -> {
                int value = InterpreterToVM.getFieldInt(receiver, field, true);
                virtualStack.pop1(frame);
                virtualStack.pushInt(frame, value);
            }
            case Double -> {
                double value = InterpreterToVM.getFieldDouble(receiver, field, true);
                virtualStack.pop1(frame);
                virtualStack.pushDouble(frame, value);
            }
            case Float -> {
                float value = InterpreterToVM.getFieldFloat(receiver, field, true);
                virtualStack.pop1(frame);
                virtualStack.pushFloat(frame, value);
            }
            case Long -> {
                long value = InterpreterToVM.getFieldLong(receiver, field, true);
                virtualStack.pop1(frame);
                virtualStack.pushLong(frame, value);
            }
            case Object -> {
                Object value = InterpreterToVM.getFieldObject(receiver, field, true);
                virtualStack.pop1(frame);
                virtualStack.pushObject(frame, value);
            }
            default -> throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    // endregion Field read/write

}
