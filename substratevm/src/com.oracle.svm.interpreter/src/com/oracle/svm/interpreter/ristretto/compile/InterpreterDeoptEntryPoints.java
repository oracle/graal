/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.compile;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.guest.staging.jdk.InternalVMMethod;
import com.oracle.svm.interpreter.Interpreter;
import com.oracle.svm.interpreter.InterpreterFrame;
import com.oracle.svm.interpreter.InterpreterFrameUtil;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.Bytecodes;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaKind;

/**
 * Typed entry points that resume execution in the Crema interpreter after deoptimization of a
 * Ristretto frame has already been committed and the stub epilogue has restored the caller-visible
 * stack shape.
 *
 * <p>
 * The eager versus lazy split happens before control reaches this class. With eager deoptimization,
 * a {@link RistrettoDeoptimizedInterpreterFrame} is constructed and pinned ahead of time, installed
 * at {@code SP[0]} as the active {@link DeoptimizedFrame}, and then consumed by the eager deopt
 * stub. With lazy deoptimization, {@code SP[0]} still holds the original return address while the
 * lazy stub constructs that same interpreter-target frame immediately before the handoff. In both
 * cases, the stub eventually restores {@code revertSp}, reinstalls the original return edge, and
 * tail-jumps into one of the typed methods below with the prepared frame passed as a Java argument.
 *
 * <p>
 * By the time one of the typed methods starts, {@code SP[0]} from the deoptimized compiled frame is
 * no longer consulted. Stack walkers instead follow the restored AOT caller frames while the
 * {@link RistrettoDeoptimizedInterpreterFrame} stays strongly reachable through the Java argument
 * passed down the stub and entry-method call chain.
 */
@InternalVMMethod
public class InterpreterDeoptEntryPoints {

    @Fold
    public static Log logger() {
        return Log.log();
    }

    /**
     * Tail-jumps from the custom deopt stub into the typed interpreter entry point after restoring
     * the caller stack shape.
     *
     * <p>
     * The backend-specific epilogue restores {@code revertSp}, optionally restores the caller base
     * pointer, reinstalls {@code oldReturnAddress}, and then jumps to {@code interpEntryPoint}.
     */
    @Deoptimizer.DeoptStub(stubType = Deoptimizer.StubType.InterpreterDeoptEntryPointStub)
    @Uninterruptible(reason = "Custom deopt-stub epilogue rewrites the active stack frame.")
    @NeverInline("custom prologue and epilogue")
    @SuppressWarnings("unused")
    public static void jumpToInterpreterEntryPoint(RistrettoDeoptimizedInterpreterFrame frame, Pointer revertSp, CodePointer interpEntryPoint, CodePointer oldReturnAddress,
                    Pointer oldBasePointer) {
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException uncheckedThrow(Throwable e) throws T {
        throw (T) e;
    }

    @NeverInline("Trace logging is slow-path code.")
    private static void logCurrentCallerFrame(String logPrefix, Pointer baseSp, CodePointer ra) {
        logger().string(logPrefix).string(" current sp=         ").hex(baseSp).newline();
        logger().string(logPrefix).string(" current return-addr=").hex(ra).newline();
    }

    private static void traceCurrentCallerFrame(String logPrefix, Pointer baseSp) {
        IsolateThread targetThread = CurrentIsolate.getCurrentThread();
        CodePointer ra = FrameAccess.singleton().readReturnAddress(targetThread, baseSp);
        logCurrentCallerFrame(logPrefix, baseSp, ra);
    }

    private static Object executeEntry(RistrettoDeoptimizedInterpreterFrame deoptFrame, String tracePrefix) throws Throwable {
        assert deoptFrame != null;
        RistrettoVirtualInterpreterFrame bottomFrame = deoptFrame.getBottomFrame();
        VMError.guarantee(bottomFrame != null, "Deoptimized interpreter frame must keep a bottom frame");

        final boolean hasPendingException = deoptFrame.hasPendingException();
        Object pendingExceptionObject = null;
        if (hasPendingException) {
            pendingExceptionObject = deoptFrame.getPendingExceptionObject();
        }

        try {
            Object returnValue = executeInterpreterFrames(deoptFrame, bottomFrame, pendingExceptionObject, hasPendingException);
            if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                logger().string(tracePrefix).string(" leaving").newline();
            }
            return returnValue;
        } catch (Throwable ex) {
            if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                logger().string(tracePrefix).string(" caught exception with class ").string(ex.getClass().getName()).string(" will rethrow now").newline();
            }
            throw uncheckedThrow(ex);
        }
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static void entryVoid(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryVoid]", baseSp);
        }
        executeEntry(deoptFrame, "[buf/entryVoid]");
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static int entryInt(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryInt]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryInt]");
        assert returnValue instanceof Integer;
        return (int) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static long entryLong(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryLong]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryLong]");
        assert returnValue instanceof Long;
        return (long) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static float entryFloat(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryFloat]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryFloat]");
        assert returnValue instanceof Float;
        return (float) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static double entryDouble(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryDouble]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryDouble]");
        assert returnValue instanceof Double;
        return (double) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static boolean entryBoolean(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryBoolean]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryBoolean]");
        assert returnValue instanceof Boolean;
        return (boolean) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static byte entryByte(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryByte]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryByte]");
        assert returnValue instanceof Byte;
        return (byte) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static short entryShort(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryShort]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryShort]");
        assert returnValue instanceof Short;
        return (short) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static char entryChar(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryChar]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryChar]");
        assert returnValue instanceof Character;
        return (char) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static Object entryObject(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryObject]", baseSp);
        }
        return executeEntry(deoptFrame, "[buf/entryObject]");
    }

    /**
     * Recreates execution through the deoptimized interpreter-frame chain.
     *
     * <p>
     * Resume starts at the innermost deoptimized callee and then continues caller-by-caller in the
     * same order ordinary interpreter execution would observe. For one caller/callee pair the
     * control flow looks like this:
     *
     *
     * Compiled view: {@code  caller@invoke(current frame) -> inlined callee}.
     *
     * Java resume order:
     * <ol>
     * <li>execute callee frame</li>
     * <li>if it returns, write the value into the caller operand stack</li>
     * <li>if it throws, let the caller run exception-table lookup</li>
     * <li>continue the caller in the interpreter at targetBci</li>
     * </ol>
     *
     * <br>
     * Machine return registers: only the physical top frame may still carry one pending normal
     * return value from a previous non-inlined callee when the deopt source frame state is AfterPop
     * and the caller operand stack has not received that value yet. </br>
     * This resume path stays entirely in interpreter mode: each frame executes in interpreter
     * order, pending exceptions go through interpreter exception tables, and normal returns are
     * written back into the caller operand stack before that caller continues.
     */
    public static Object executeInterpreterFrames(RistrettoDeoptimizedInterpreterFrame deoptFrame, RistrettoVirtualInterpreterFrame current, Object pendingExceptionObject, boolean hasPendingException)
                    throws Throwable {
        Object returnValue = null;
        Throwable pendingException = null;
        boolean inject = false;

        if (hasPendingException) {
            VMError.guarantee(pendingExceptionObject instanceof Throwable, "Pending exception payload must be a Throwable");
            pendingException = (Throwable) pendingExceptionObject;
        }

        if (current.hasCallee()) {
            try {
                /*
                 * Execute the innermost callee first. A normal return becomes a value to inject
                 * into the current frame; an exception is re-thrown here so the current frame can
                 * consult its own exception table exactly like ordinary interpreter execution
                 * would.
                 */
                returnValue = executeInterpreterFrames(deoptFrame, current.getCallee(), pendingExceptionObject, hasPendingException);
                /* if we return properly, the pending exception got handled */
                pendingException = null;
                inject = true;
            } catch (Throwable e) {
                if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                    Log.log().string("[InterpreterDeoptEntryPoints] Caught exception with message=").string(e.getMessage()).string(" and class=")
                                    .string(e.getClass().getName()).string(" when executing ").string(current.getMethod().getName()).string(" will set pending interpreter exception now")
                                    .newline();
                }

                pendingException = e;
            }
        }

        // in crema locals and expression stack are in the same array, stack[0]<==> array[maxLocals]
        int startTop = InterpreterFrameUtil.startingStackOffset(current.getMethod().getMaxLocals()) + current.getNumStack();
        int targetBci = current.getTargetBci();
        if (pendingException == null && current.isRethrowException()) {
            /*
             * A compiled Rethrow frame already represents the synthetic "throw this Throwable
             * again" edge. Deopt can reconstruct that state before any Java exception is currently
             * propagating through this helper, so the Throwable must be recovered from the
             * reconstructed single stack slot instead of from pendingException.
             */
            VMError.guarantee(current.getNumStack() == 1, "Rethrow frame must carry exactly one pending exception");
            Object exceptionObject = InterpreterFrameUtil.peekObject(current.getFrame(), startTop - 1);
            VMError.guarantee(exceptionObject instanceof Throwable, "Rethrow frame must carry a Throwable");
            pendingException = (Throwable) exceptionObject;
        }
        boolean hasPendingReturnValue = deoptFrame.hasPendingReturnValue();
        if (hasPendingReturnValue) {
            VMError.guarantee(!inject, "Pending return value must not coexist with an already injected callee result");
            VMError.guarantee(!current.hasCallee(), "Pending return value must belong to the innermost reconstructed frame");
            VMError.guarantee(pendingException == null, "Pending return value must not coexist with a pending exception");
        }
        if (pendingException != null) {
            if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                Log.log().string("[InterpreterDeoptEntryPoints] Handling pending exception with message=").string(pendingException.getMessage()).string(" and class=")
                                .string(pendingException.getClass().getName()).string(" when deopting frame portion ").string(current.getMethod().getName()).newline();
            }
            ExceptionHandler handler = Interpreter.resolveExceptionHandler(current.getMethod(),
                            current.getCurrentBci(), pendingException);
            if (handler == null) {
                /* Propagate the exception to the caller frame. */
                throw pendingException;
            } else {
                /*
                 * The current frame is about to continue at its catch handler as if the bytecode
                 * had just thrown. Clear any stale operand-stack state and rebuild the handler
                 * entry stack, which is exactly one Throwable.
                 */
                Interpreter.clearOperandStack(current.getFrame(), current.getMethod(), startTop);
                startTop = InterpreterFrameUtil.startingStackOffset(current.getMethod().getMaxLocals());
                InterpreterFrameUtil.putObject(current.getFrame(), startTop, pendingException);
                startTop++;
                targetBci = Interpreter.beforeJumpChecks(current.getFrame(), targetBci, handler.getHandlerBCI(),
                                startTop);
            }
        } else {
            if (!inject && hasPendingReturnValue) {
                /*
                 * We are at the innermost reconstructed frame for this deoptimized physical frame.
                 * If compiled execution had already completed a call to a non-inlined callee before
                 * deoptimization, that callee result may still be waiting in the saved machine
                 * return registers instead of in this frame's operand stack. The deopt stub had to
                 * snapshot those registers into {@code deoptFrame} before it restored the caller
                 * stack and jumped into Java, because by the time we run here there is no reliable
                 * late register read left to perform. Consume that one-shot saved machine result
                 * now and treat it like a normal callee return.
                 */
                VMError.guarantee(current.isAfterPop(), "Saved machine return values require an AfterPop top frame");
                returnValue = deoptFrame.consumePendingReturnValue();
                inject = true;
            }
            if (inject) {
                startTop += injectReturnValue(current, startTop, returnValue);
            }
        }

        return Interpreter.execute(current.getMethod(), current.getFrame(), targetBci, startTop);
    }

    private static void logInjectedReturnValue(JavaKind returnKind, Object returnValue, int slot) {
        if (!Deoptimizer.Options.TraceDeoptimization.getValue()) {
            return;
        }
        logger().string("[interpEntry] Writing returnValue=");
        switch (returnKind) {
            case Long -> logger().hex((Long) returnValue);
            case Float -> logger().hex(Float.floatToRawIntBits((Float) returnValue));
            case Double -> logger().hex(Double.doubleToRawLongBits((Double) returnValue));
            case Boolean -> logger().hex(((Boolean) returnValue) ? 1 : 0);
            case Byte -> logger().hex(((Byte) returnValue).byteValue());
            case Short -> logger().hex(((Short) returnValue).shortValue());
            case Char -> logger().hex(((Character) returnValue).charValue());
            case Int -> logger().hex(((Integer) returnValue).intValue());
            default -> logger().string(String.valueOf(returnValue));
        }
        logger().string(" at slot=").signed(slot).newline();
    }

    /**
     * Writes a completed callee result into the caller frame exactly where normal interpreter
     * execution would have left it, and returns the resulting stack delta from {@code callerTop}.
     *
     * <pre>
     * BeforePop: caller-side frame state still carries receiver/args -> write result at the
     *            post-pop slot and clear the consumed argument range
     * AfterPop : caller-side frame state already consumed receiver/args -> write result at the
     *            current top
     * Rethrow  : no normal return path -> handled on the exception path instead
     * </pre>
     *
     * <p>
     * The slot calculation intentionally reuses
     * {@link RistrettoDeoptimizationSupport#resolveDeoptInvokeSiteLayout(InterpreterResolvedJavaMethod, int)}
     * so the deopt resume path keeps one local source of truth for invoke layout, return-kind
     * selection, and result placement.
     */
    private static int injectReturnValue(RistrettoVirtualInterpreterFrame chainedFrame, int callerTop, Object returnValue) {
        InterpreterFrame interpreterFrame = chainedFrame.getFrame();
        InterpreterResolvedJavaMethod interpreterMethod = chainedFrame.getMethod();

        int callsiteBci = chainedFrame.getCurrentBci();
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            logger().string("caller method: ").string(interpreterMethod.toString()).newline();
        }

        int opcode = BytecodeStream.opcode(interpreterMethod.getCode(), callsiteBci);
        if (!Bytecodes.isInvoke(opcode)) {
            throw VMError.shouldNotReachHere("Return-value injection expects an invoke bytecode at BCI " + callsiteBci);
        }
        RistrettoDeoptimizationSupport.CallSiteLayout invokeLayout = RistrettoDeoptimizationSupport.resolveDeoptInvokeSiteLayout(interpreterMethod, callsiteBci);
        JavaKind returnKind = invokeLayout.getReturnKind();
        /*
         * AfterPop frames already reflect the post-invoke stack shape, so the result belongs at the
         * current top. BeforePop frames still contain the invoke arguments, so we must place the
         * result into the original post-pop slot below those arguments and clear the now-consumed
         * operand-stack range so stale object references do not linger above the new top. Rethrow
         * frames do not consume normal return values at all.
         */
        VMError.guarantee(!chainedFrame.isRethrowException(), "Cannot inject a return value into a rethrow frame");
        int calleeReturnValueSlot = chainedFrame.isAfterPop() ? callerTop : callerTop - invokeLayout.getArgumentSlotCount();
        int newTop = calleeReturnValueSlot + returnKind.getSlotCount();
        if (!chainedFrame.isAfterPop()) {
            clearConsumedInvokeArguments(interpreterFrame, newTop, callerTop);
        }
        int stackDelta = calleeReturnValueSlot - callerTop;
        switch (returnKind) {
            case Void:
                /* nothing to do */
                return stackDelta;
            case Int:
                assert returnValue instanceof Integer;
                int calleeIntReturnValue = (Integer) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                InterpreterFrameUtil.putInt(interpreterFrame, calleeReturnValueSlot, calleeIntReturnValue);
                return stackDelta + 1;
            case Boolean:
                assert returnValue instanceof Boolean;
                int calleeBooleanReturnValue = ((Boolean) returnValue) ? 1 : 0;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                InterpreterFrameUtil.putInt(interpreterFrame, calleeReturnValueSlot, calleeBooleanReturnValue);
                return stackDelta + 1;
            case Byte:
                assert returnValue instanceof Byte;
                int calleeByteReturnValue = (Byte) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                InterpreterFrameUtil.putInt(interpreterFrame, calleeReturnValueSlot, calleeByteReturnValue);
                return stackDelta + 1;
            case Short:
                assert returnValue instanceof Short;
                int calleeShortReturnValue = (Short) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                InterpreterFrameUtil.putInt(interpreterFrame, calleeReturnValueSlot, calleeShortReturnValue);
                return stackDelta + 1;
            case Char:
                assert returnValue instanceof Character;
                int calleeCharReturnValue = (Character) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                InterpreterFrameUtil.putInt(interpreterFrame, calleeReturnValueSlot, calleeCharReturnValue);
                return stackDelta + 1;
            case Long:
                assert returnValue instanceof Long;
                long calleeLongReturnValue = (Long) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                InterpreterFrameUtil.putLong(interpreterFrame, calleeReturnValueSlot, calleeLongReturnValue);
                return stackDelta + 2;
            case Float:
                assert returnValue instanceof Float;
                float calleeFloatReturnValue = (Float) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                InterpreterFrameUtil.putFloat(interpreterFrame, calleeReturnValueSlot, calleeFloatReturnValue);
                return stackDelta + 1;
            case Double:
                assert returnValue instanceof Double;
                double calleeDoubleReturnValue = (Double) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                InterpreterFrameUtil.putDouble(interpreterFrame, calleeReturnValueSlot, calleeDoubleReturnValue);
                return stackDelta + 2;
            case Object:
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                InterpreterFrameUtil.putObject(interpreterFrame, calleeReturnValueSlot, returnValue);
                return stackDelta + 1;
            default:
                throw VMError.shouldNotReachHere("entrypoint: unsupported return kind: " + returnKind);
        }
    }

    private static void clearConsumedInvokeArguments(InterpreterFrame interpreterFrame, int newTop, int callerTop) {
        for (int slot = callerTop - 1; slot >= newTop; --slot) {
            InterpreterFrameUtil.clear(interpreterFrame, slot);
        }
    }

}
