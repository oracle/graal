/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.snippets;

// Checkstyle: allow reflection

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.DeoptimizationSourcePositionDecoder;
import com.oracle.svm.core.deopt.DeoptTester;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.jdk.JDKUtils;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

public class SnippetRuntime {

    public static final SubstrateForeignCallDescriptor REPORT_TYPE_ASSERTION_ERROR = findForeignCall(SnippetRuntime.class, "reportTypeAssertionError", true, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor UNREACHED_CODE = findForeignCall(SnippetRuntime.class, "unreachedCode", true, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor UNRESOLVED = findForeignCall(SnippetRuntime.class, "unresolved", true, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor DEOPTIMIZE = findForeignCall(SnippetRuntime.class, "deoptimize", true, LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor DEOPTTEST = findForeignCall(DeoptTester.class, "deoptTest", false, LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor UNWIND_EXCEPTION = findForeignCall(SnippetRuntime.class, "unwindException", true, LocationIdentity.any());

    /* Implementation of runtime calls defined in a VM-independent way by Graal. */
    public static final SubstrateForeignCallDescriptor REGISTER_FINALIZER = findForeignCall(SnippetRuntime.class, "registerFinalizer", true);

    public static final SubstrateForeignCallDescriptor FATAL_RUNTIME_ASSERTION = findForeignCall(SnippetRuntime.class, "reportRuntimeAssertionFatal", true);
    public static final SubstrateForeignCallDescriptor FATAL_RUNTIME_ASSERTION_OBJ = findForeignCall(SnippetRuntime.class, "reportRuntimeAssertionFatalObj", true);
    public static final SubstrateForeignCallDescriptor FATAL_RUNTIME_ASSERTION_OBJ_OBJ = findForeignCall(SnippetRuntime.class, "reportRuntimeAssertionFatalObjObj", true);
    public static final SubstrateForeignCallDescriptor FATAL_RUNTIME_ASSERTION_INT = findForeignCall(SnippetRuntime.class, "reportRuntimeAssertionFatalInt", true);
    public static final SubstrateForeignCallDescriptor FATAL_RUNTIME_ASSERTION_LONG = findForeignCall(SnippetRuntime.class, "reportRuntimeAssertionFatalLong", true);
    public static final SubstrateForeignCallDescriptor FATAL_RUNTIME_ASSERTION_FLOAT = findForeignCall(SnippetRuntime.class, "reportRuntimeAssertionFatalFloat", true);
    public static final SubstrateForeignCallDescriptor FATAL_RUNTIME_ASSERTION_DOUBLE = findForeignCall(SnippetRuntime.class, "reportRuntimeAssertionFatalDouble", true);

    /*
     * Graal-defined math functions where we have optimized machine code sequences: We just register
     * the original Math function as the foreign call. The backend will emit the machine code
     * sequence.
     */
    public static final SubstrateForeignCallDescriptor ARITHMETIC_SIN = findForeignCall(UnaryOperation.SIN.foreignCallDescriptor.getName(), Math.class, "sin", true);
    public static final SubstrateForeignCallDescriptor ARITHMETIC_COS = findForeignCall(UnaryOperation.COS.foreignCallDescriptor.getName(), Math.class, "cos", true);
    public static final SubstrateForeignCallDescriptor ARITHMETIC_TAN = findForeignCall(UnaryOperation.TAN.foreignCallDescriptor.getName(), Math.class, "tan", true);
    public static final SubstrateForeignCallDescriptor ARITHMETIC_LOG = findForeignCall(UnaryOperation.LOG.foreignCallDescriptor.getName(), Math.class, "log", true);
    public static final SubstrateForeignCallDescriptor ARITHMETIC_LOG10 = findForeignCall(UnaryOperation.LOG10.foreignCallDescriptor.getName(), Math.class, "log10", true);
    /*
     * Graal-defined math functions where we do not have optimized code sequences: StrictMath is the
     * always-available fall-back.
     */
    public static final SubstrateForeignCallDescriptor ARITHMETIC_EXP = findForeignCall(UnaryOperation.EXP.foreignCallDescriptor.getName(), StrictMath.class, "exp", true);
    public static final SubstrateForeignCallDescriptor ARITHMETIC_POW = findForeignCall(BinaryOperation.POW.foreignCallDescriptor.getName(), StrictMath.class, "pow", true);

    /*
     * These methods are intrinsified as nodes at first, but can then lowered back to a call. Ensure
     * they are seen as reachable.
     */
    public static final SubstrateForeignCallDescriptor OBJECT_CLONE = findForeignCall(Object.class, "clone", false, LocationIdentity.any());

    public static List<SubstrateForeignCallDescriptor> getRuntimeCalls() {
        List<SubstrateForeignCallDescriptor> result = new ArrayList<>();
        try {
            for (Field field : SnippetRuntime.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == SubstrateForeignCallDescriptor.class) {
                    result.add(((SubstrateForeignCallDescriptor) field.get(null)));
                }
            }
        } catch (IllegalAccessException ex) {
            throw new Error(ex);
        }
        return result;
    }

    public static SubstrateForeignCallDescriptor findForeignCall(Class<?> declaringClass, String methodName, boolean isReexecutable, LocationIdentity... killedLocations) {
        return findForeignCall(methodName, declaringClass, methodName, isReexecutable, killedLocations);
    }

    private static SubstrateForeignCallDescriptor findForeignCall(String descriptorName, Class<?> declaringClass, String methodName, boolean isReexecutable, LocationIdentity... killedLocations) {
        Method foundMethod = null;
        for (Method method : declaringClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                assert foundMethod == null : "found more than one method " + declaringClass.getName() + "." + methodName;
                foundMethod = method;
            }
        }
        assert foundMethod != null : "did not find method " + declaringClass.getName() + "." + methodName;

        /*
         * We cannot annotate methods from the JDK, but all other foreign call targets we want to be
         * annotated for documentation, and to avoid stripping.
         */
        VMError.guarantee(declaringClass.getName().startsWith("java.lang") || foundMethod.getAnnotation(SubstrateForeignCallTarget.class) != null,
                        "Add missing @SubstrateForeignCallTarget to " + declaringClass.getName() + "." + methodName);

        return new SubstrateForeignCallDescriptor(descriptorName, foundMethod, isReexecutable, killedLocations);
    }

    public static class SubstrateForeignCallDescriptor extends ForeignCallDescriptor {

        private final Class<?> declaringClass;
        private final String methodName;
        private final boolean isReexecutable;
        private final LocationIdentity[] killedLocations;

        SubstrateForeignCallDescriptor(String descriptorName, Method method, boolean isReexecutable, LocationIdentity[] killedLocations) {
            super(descriptorName, method.getReturnType(), method.getParameterTypes());
            this.declaringClass = method.getDeclaringClass();
            this.methodName = method.getName();
            this.isReexecutable = isReexecutable;
            this.killedLocations = killedLocations;
        }

        public Class<?> getDeclaringClass() {
            return declaringClass;
        }

        public boolean isReexecutable() {
            return isReexecutable;
        }

        public ResolvedJavaMethod findMethod(MetaAccessProvider metaAccess) {
            for (Method method : declaringClass.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    return metaAccess.lookupJavaMethod(method);
                }
            }
            throw VMError.shouldNotReachHere("method " + methodName + " not found");
        }

        public LocationIdentity[] getKilledLocations() {
            return killedLocations;
        }
    }

    /** Foreign call: {@link #REPORT_TYPE_ASSERTION_ERROR}. */
    @SubstrateForeignCallTarget
    private static void reportTypeAssertionError(byte[] msg, Object object) {
        Log.log().string(msg).string(object == null ? "null" : object.getClass().getName()).newline();
        throw VMError.shouldNotReachHere("type assertion error");
    }

    /** Foreign call: {@link #UNREACHED_CODE}. */
    @SubstrateForeignCallTarget
    private static void unreachedCode() {
        throw VMError.unsupportedFeature("Code that was considered unreachable by closed-world analysis was reached");
    }

    /** Foreign call: {@link #UNRESOLVED}. */
    @SubstrateForeignCallTarget
    private static void unresolved(String sourcePosition) {
        throw VMError.unsupportedFeature("Unresolved element found " + (sourcePosition != null ? sourcePosition : ""));
    }

    /** Foreign call: {@link #DEOPTIMIZE}. */
    @SubstrateForeignCallTarget
    private static void deoptimize(long actionAndReason, SpeculationReason speculation) {
        Pointer sp = KnownIntrinsics.readCallerStackPointer();
        DeoptimizationAction action = Deoptimizer.decodeDeoptAction(actionAndReason);

        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Log log = Log.log().string("[Deoptimization initiated").newline();

            CodePointer ip = KnownIntrinsics.readReturnAddress();
            SubstrateInstalledCode installedCode = CodeInfoTable.lookupInstalledCode(ip);
            if (installedCode != null) {
                log.string("    name: ").string(installedCode.getName()).newline();
            }
            log.string("    sp: ").hex(sp).string("  ip: ").hex(ip).newline();

            DeoptimizationReason reason = Deoptimizer.decodeDeoptReason(actionAndReason);
            log.string("    reason: ").string(reason.toString()).string("  action: ").string(action.toString()).newline();

            int debugId = Deoptimizer.decodeDebugId(actionAndReason);
            log.string("    debugId: ").signed(debugId).string("  speculation: ").string(Objects.toString(speculation)).newline();

            CodeInfoQueryResult info = CodeInfoTable.lookupCodeInfoQueryResult(ip);
            if (info != null) {
                NodeSourcePosition sourcePosition = DeoptimizationSourcePositionDecoder.decode(debugId, info);
                if (sourcePosition != null) {
                    log.string("    stack trace that triggered deoptimization:").newline();
                    NodeSourcePosition cur = sourcePosition;
                    while (cur != null) {
                        log.string("        at ");
                        if (cur.getMethod() != null) {
                            StackTraceElement element = cur.getMethod().asStackTraceElement(cur.getBCI());
                            if (element.getFileName() != null && element.getLineNumber() >= 0) {
                                log.string(element.toString());
                            } else {
                                log.string(cur.getMethod().format("%H.%n(%p)")).string(" bci ").signed(cur.getBCI());
                            }
                        } else {
                            log.string("[unknown method]");
                        }
                        log.newline();

                        cur = cur.getCaller();
                    }
                }
            }
        }

        if (action.doesInvalidateCompilation()) {
            Deoptimizer.invalidateMethodOfFrame(sp, speculation);
        } else {
            Deoptimizer.deoptimizeFrame(sp, false, speculation);
        }

        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Log.log().string("]").newline();
        }
    }

    static class ExceptionStackFrameVisitor implements StackFrameVisitor {
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when unwinding the stack.")
        @Override
        public boolean visitFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptFrame) {
            CodePointer continueIP;
            if (deoptFrame != null) {
                /* Deoptimization entry points always have an exception handler. */
                deoptFrame.takeException();
                continueIP = ip;

            } else {
                long handler = CodeInfoTable.lookupExceptionOffset(ip);
                if (handler == 0) {
                    /* No handler found in this frame, walk to caller frame. */
                    return true;
                }
                continueIP = (CodePointer) ((UnsignedWord) ip).add(WordFactory.signed(handler));
            }

            Throwable exception = currentException.get();
            currentException.set(null);

            KnownIntrinsics.farReturn(exception, sp, continueIP);
            /*
             * The intrinsic performs a jump to the specified instruction pointer, so this code is
             * unreachable.
             */
            return false;
        }
    }

    /*
     * The stack walking objects must be stateless (no instance fields), because multiple threads
     * can use them simultaneously. All state must be in separate VMThreadLocals.
     */
    private static final ExceptionStackFrameVisitor exceptionStackFrameVisitor = new ExceptionStackFrameVisitor();

    protected static final FastThreadLocalObject<Throwable> currentException = FastThreadLocalFactory.createObject(Throwable.class);

    /** Foreign call: {@link #UNWIND_EXCEPTION}. */
    @SubstrateForeignCallTarget
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when unwinding the stack.")
    private static void unwindException(Throwable exception, Pointer callerSP, CodePointer callerIP) {
        if (currentException.get() != null) {
            /*
             * Exception unwinding cannot be called recursively. The most likely reason to end up
             * here is an exception being thrown while walking the stack to find an exception
             * handler.
             */
            Log.log().string("Fatal error: recursion in exception handling: ").string(exception.getClass().getName());
            Log.log().string(" thrown while unwinding ").string(currentException.get().getClass().getName()).newline();
            ImageSingletons.lookup(LogHandler.class).fatalError();
            return;
        }
        currentException.set(exception);

        /*
         * callerSP and callerIP identify already the caller of the frame that wants to unwind an
         * exception. So we can start looking for the exception handler immediately in that frame,
         * without skipping any frames in between.
         */
        JavaStackWalker.walkCurrentThread(callerSP, callerIP, exceptionStackFrameVisitor);

        /*
         * The stack walker does not return if an exception handler is found, but instead performs a
         * direct jump to the handler. So when we reach this point, we can just report an unhandled
         * exception.
         */
        reportUnhandledExceptionRaw(exception);
    }

    public static void reportUnhandledExceptionRaw(Throwable exception) {
        Log.log().string(exception.getClass().getName());
        String detail = JDKUtils.getRawMessage(exception);
        if (detail != null) {
            Log.log().string(": ").string(detail);
        }
        Log.log().newline();
        ImageSingletons.lookup(LogHandler.class).fatalError();
    }

    /** Foreign call: {@link #REGISTER_FINALIZER}. */
    @SubstrateForeignCallTarget
    private static void registerFinalizer(@SuppressWarnings("unused") Object obj) {
        // We do not support finalizers, so nothing to do.
    }

    private static String assertionErrorName() {
        return AssertionError.class.getName();
    }

    private static Log runtimeAssertionPrefix() {
        return Log.log().string(assertionErrorName()).string(": ");
    }

    /** Foreign call: {@link #FATAL_RUNTIME_ASSERTION}. */
    @SubstrateForeignCallTarget
    private static void reportRuntimeAssertionFatal(@SuppressWarnings("unused") Object obj) {
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    /** Foreign call: {@link #FATAL_RUNTIME_ASSERTION_OBJ}. */
    @SubstrateForeignCallTarget
    private static void reportRuntimeAssertionFatalObj(@SuppressWarnings("unused") Object obj, Object detailMessage) {
        if (detailMessage instanceof String) {
            runtimeAssertionPrefix().string((String) detailMessage).newline();
        } else {
            /*
             * We do not want to convert detailMessage to a string, since that requires allocation.
             */
            runtimeAssertionPrefix().string(detailMessage.getClass().getName()).newline();
        }
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    /** Foreign call: {@link #FATAL_RUNTIME_ASSERTION_OBJ_OBJ}. */
    @SubstrateForeignCallTarget
    private static void reportRuntimeAssertionFatalObjObj(@SuppressWarnings("unused") Object obj, String detailMessage, @SuppressWarnings("unused") Throwable cause) {
        runtimeAssertionPrefix().string(detailMessage).newline();
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    /** Foreign call: {@link #FATAL_RUNTIME_ASSERTION_INT}. */
    @SubstrateForeignCallTarget
    private static void reportRuntimeAssertionFatalInt(@SuppressWarnings("unused") Object obj, int val) {
        runtimeAssertionPrefix().signed(val).newline();
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    /** Foreign call: {@link #FATAL_RUNTIME_ASSERTION_LONG}. */
    @SubstrateForeignCallTarget
    private static void reportRuntimeAssertionFatalLong(@SuppressWarnings("unused") Object obj, long val) {
        runtimeAssertionPrefix().signed(val).newline();
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    /** Foreign call: {@link #FATAL_RUNTIME_ASSERTION_FLOAT}. */
    @SubstrateForeignCallTarget
    private static void reportRuntimeAssertionFatalFloat(@SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") float val) {
        runtimeAssertionPrefix().string("[float number supressed]").newline();
        throw VMError.shouldNotReachHere(assertionErrorName());
    }

    /** Foreign call: {@link #FATAL_RUNTIME_ASSERTION_DOUBLE}. */
    @SubstrateForeignCallTarget
    private static void reportRuntimeAssertionFatalDouble(@SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") double val) {
        runtimeAssertionPrefix().string("[double number supressed]").newline();
        throw VMError.shouldNotReachHere(assertionErrorName());
    }
}
