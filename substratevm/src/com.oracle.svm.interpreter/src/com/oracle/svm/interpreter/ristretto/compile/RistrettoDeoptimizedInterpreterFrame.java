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

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.code.FrameInfoDecoder;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.deopt.VirtualFrame;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.core.log.StringBuilderLog;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.JavaKind;

/**
 * Deoptimized-frame implementation for transitions from Ristretto compiled code back to the Crema
 * interpreter.
 *
 * <p>
 * Normal returns and exception delivery are resumed by executing the reconstructed
 * {@link RistrettoVirtualInterpreterFrame} chain in Java. This heap object only bridges the tiny
 * gap where the deopt stub still owns the physical compiled frame but Java resume code has not
 * started yet.
 *
 * <pre>
 * deopt stub sees source stack frame + gp/fp return registers
 *   -> snapshot pending exception or pending top-frame result into this object
 *   -> tear down the compiled frame and jump to InterpreterDeoptEntryPoints
 *   -> Java resume code consumes the one-shot payload and continues in the interpreter
 * </pre>
 */
public class RistrettoDeoptimizedInterpreterFrame extends DeoptimizedFrame {
    private final long frameSize;
    private final PinnedObject pin;
    private final RistrettoVirtualInterpreterFrame bottomFrame;
    private final RistrettoInstalledCode rCode;

    /* Carries the pending exception object across the deopt handoff. */
    private Object pendingExceptionObject;

    /*
     * Pending invoke-result handoff for the physical top frame:
     *
     * <pre> compiled frame: invoke already finished, but the caller slot is not written yet machine
     * state: gp/fp return registers still hold the raw return bits deopt stub: snapshots those raw
     * bits into this object Java resume: executeInterpreterFrames writes the result into the
     * interpreter frame </pre>
     *
     * The raw register payload lives in one plain long field, not a Word object: - the relevant
     * GP/FP return register is just a transient bit container here - this object lives on the Java
     * heap, not in raw pointer arithmetic - object returns are converted eagerly into
     * pendingObjectReturnValue because only object identity, not raw register bits, matters once
     * the stub exits - one boxed Object carrier would require allocation while the deopt stub is
     * still running in uninterruptible code, which is not allowed, and would lose the exact
     * float/double bit-pattern too early
     */
    private JavaKind pendingReturnKind = JavaKind.Illegal;
    private long pendingPrimitiveReturnValue;
    private Object pendingObjectReturnValue;

    private CodePointer interpEntryPoint;
    private boolean hasPendingException = false;
    private final CodePointer sourcePC;
    private final char[] completedMessage;

    @SuppressWarnings("this-escape")
    public RistrettoDeoptimizedInterpreterFrame(long frameSize, RistrettoVirtualInterpreterFrame bottomFrame, RistrettoInstalledCode rCode, CodePointer sourcePC, boolean pinFrame) {
        this.frameSize = frameSize;
        this.pin = pinFrame ? PinnedObject.create(this) : null;
        this.bottomFrame = bottomFrame;
        this.rCode = rCode;
        this.sourcePC = sourcePC;
        StringBuilderLog sbl = new StringBuilderLog();
        sbl.string("deoptStub: completed ").string(pinFrame ? "eagerly" : "lazily").string(" for DeoptimizedFrame at ").hex(Word.objectToUntrackedPointer(this)).newline();
        this.completedMessage = sbl.getResult().toCharArray();
    }

    @Override
    public SubstrateInstalledCode getSourceInstalledCode() {
        return rCode;
    }

    /**
     * Returns the {@link PinnedObject} that ensures that this {@link DeoptimizedFrame} is not moved
     * by the GC. The {@link DeoptimizedFrame} is accessed during GC when walking the stack.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public PinnedObject getPin() {
        return pin;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public VirtualFrame getTopFrame() {
        /*
         * The linked frame chain is stored bottom-to-top. Stack walking, pending-result capture,
         * and trace logging all need the innermost active frame first, so walk the callee chain to
         * the end here.
         */
        RistrettoVirtualInterpreterFrame top = bottomFrame;
        while (top.hasCallee()) {
            top = top.getCallee();
        }
        return top;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public DeoptTargetTier getTargetTier() {
        return DeoptTargetTier.Interpreter;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public long getSourceEncodedFrameSize() {
        /* For interpreter deopt frames, encoded and total frame sizes are equivalent. */
        return frameSize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public long getSourceTotalFrameSize() {
        return frameSize;
    }

    @Override
    public CodePointer getSourcePC() {
        return sourcePC;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public void takeException() {
        assert !hasPendingException;
        assert pendingExceptionObject == null;
        hasPendingException = true;
    }

    public boolean hasPendingException() {
        return hasPendingException;
    }

    public RistrettoVirtualInterpreterFrame getBottomFrame() {
        return bottomFrame;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public CodePointer getInterpreterEntry() {
        return interpEntryPoint;
    }

    public void setInterpreterEntry(CodePointer p) {
        this.interpEntryPoint = p;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setPendingExceptionObject(Object pendingExceptionObject) {
        assert hasPendingException;
        this.pendingExceptionObject = pendingExceptionObject;
    }

    public Object getPendingExceptionObject() {
        assert hasPendingException : "Must have a pending exception";
        assert pendingExceptionObject != null : "Pending exception must not be null";
        return pendingExceptionObject;
    }

    /**
     * Snapshots the physical top-frame machine return registers. Called only from
     * {@link #continueInterpreterDeoptimization(Pointer, UnsignedWord, UnsignedWord, boolean, Object)}
     * while the compiled frame is still the active machine frame. Once the deopt stub restores the
     * caller-visible stack shape and tail-jumps into the typed Java entry point, those ABI return
     * registers are no longer a stable source for the pending invoke result.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setPendingReturnValue(JavaKind returnKind, UnsignedWord gpResult, UnsignedWord fpResult, Object gpResultObject) {
        assert pendingReturnKind == JavaKind.Illegal;
        VMError.guarantee(returnKind != JavaKind.Illegal && returnKind != JavaKind.Void, "Pending return value requires a non-void return kind");
        pendingReturnKind = returnKind;
        if (returnKind == JavaKind.Object) {
            /*
             * Lazy object-return deoptimization already materializes gpResultObject before the
             * interruptible frame-construction window. Reuse that rooted object instead of
             * reinterpreting gpResult after a GC may have moved the object.
             */
            pendingObjectReturnValue = gpResultObject != null ? gpResultObject : (Deoptimizer.isNonNullObjectValue(gpResult) ? ((Pointer) gpResult).toObject() : null);
        } else {
            switch (returnKind) {
                case Float, Double:
                    pendingPrimitiveReturnValue = fpResult.rawValue();
                    break;
                case Int, Boolean, Byte, Short, Char, Long:
                    pendingPrimitiveReturnValue = gpResult.rawValue();
                    break;
                default:
                    VMError.guarantee(false, "Unexpected primitive pending return kind");
            }
        }
    }

    public boolean hasPendingReturnValue() {
        return pendingReturnKind != JavaKind.Illegal;
    }

    /**
     * Reboxes the one-shot saved machine return value for
     * {@link InterpreterDeoptEntryPoints#executeInterpreterFrames(RistrettoDeoptimizedInterpreterFrame, RistrettoVirtualInterpreterFrame, Object, boolean)}
     * and then clears the handoff state. This boxing happens only after control has returned to
     * normal Java code; the earlier uninterruptible deopt-stub path must not allocate.
     */
    public Object consumePendingReturnValue() {
        VMError.guarantee(hasPendingReturnValue(), "Must have a pending return value");
        Object result = switch (pendingReturnKind) {
            case Int -> (int) pendingPrimitiveReturnValue;
            case Boolean -> pendingPrimitiveReturnValue != 0;
            case Byte -> (byte) pendingPrimitiveReturnValue;
            case Short -> (short) pendingPrimitiveReturnValue;
            case Char -> (char) pendingPrimitiveReturnValue;
            case Long -> pendingPrimitiveReturnValue;
            case Float -> Float.intBitsToFloat((int) pendingPrimitiveReturnValue);
            case Double -> Double.longBitsToDouble(pendingPrimitiveReturnValue);
            case Object -> pendingObjectReturnValue;
            case Void, Illegal -> throw VMError.shouldNotReachHere("Unexpected pending return kind: " + pendingReturnKind);
        };

        pendingReturnKind = JavaKind.Illegal;
        pendingPrimitiveReturnValue = 0;
        pendingObjectReturnValue = null;
        return result;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    protected char[] getCompletedMessage() {
        return completedMessage;
    }

    /**
     * Continues the active deoptimization by tail-jumping into the typed interpreter entry stub for
     * this reconstructed frame chain.
     *
     * <p>
     * This is the last point where the original compiled frame is still the active machine stack
     * frame, so it is also the last chance to harvest a pending exception or pending invoke result
     * from the machine return registers before control transfers into normal Java code again. After
     * the stub restores {@code revertSp} and jumps into
     * {@link InterpreterDeoptEntryPoints#jumpToInterpreterEntryPoint(RistrettoDeoptimizedInterpreterFrame, Pointer, CodePointer, CodePointer, Pointer)},
     * the resume path retains only this deoptimized frame object, not a reliable view of the old
     * compiled-frame return registers.
     */
    @Uninterruptible(reason = "Custom deopt-stub epilogue rewrites the active stack frame.")
    public UnsignedWord continueInterpreterDeoptimization(Pointer originalStackPointer, UnsignedWord gpResult, UnsignedWord fpResult, boolean hasException, Object gpResultObject) {
        IsolateThread targetThread = CurrentIsolate.getCurrentThread();

        /* Caller stack pointer after the compiled source frame has been removed from the stack. */
        Pointer revertSp = originalStackPointer.add(WordFactory.unsigned(getSourceTotalFrameSize()));
        JavaFrameAnchors.verifyTopFrameAnchor(revertSp);
        CodePointer returnAddressOfDeoptedMethod = FrameAccess.singleton().readReturnAddress(targetThread, revertSp);
        Pointer basePointerOfDeoptedMethod = revertSp.readWord(-(FrameAccess.returnAddressSize() + Deoptimizer.savedBasePointerSize()));

        if (hasException) {
            assert hasPendingException;
            setPendingExceptionObject(gpResultObject != null ? gpResultObject : (Deoptimizer.isNonNullObjectValue(gpResult) ? ((Pointer) gpResult).toObject() : null));
        } else {
            assert !hasPendingException;
            /*
             * Inlined callees produce their results by executing their reconstructed child frames
             * in Java. For a call to a non-inlined callee on the physical top frame, deoptimization
             * may happen after the callee already returned but before the caller wrote that result
             * into its interpreter operand stack, so preserve the machine return registers in this
             * frame object now. Once the stub dismantles the compiled frame and resumes through the
             * Java entry point, normal execution may already reuse those registers before the
             * interpreter asks to inject the result.
             */
            RistrettoVirtualInterpreterFrame topFrame = (RistrettoVirtualInterpreterFrame) getTopFrame();
            if (topFrame.hasPendingCallResult()) {
                setPendingReturnValue(topFrame.getCompiledReturnKind(), gpResult, fpResult, gpResultObject);
            }
        }
        if (pin != null) {
            /*
             * The pin is only needed while the stub owns the frame purely through raw stack data.
             * After the tail jump below, this frame is also reachable as a normal Java argument
             * passed through jumpToInterpreterEntryPoint(...) into the typed interpreter entry
             * method, so keeping the object pinned any longer would only over-constrain the GC.
             */
            pin.close();
        }
        InterpreterDeoptEntryPoints.jumpToInterpreterEntryPoint(this, revertSp, getInterpreterEntry(), returnAddressOfDeoptedMethod, basePointerOfDeoptedMethod);
        throw VMError.shouldNotReachHere("At this point, this frame should not be on the stack anymore");
    }

    @Override
    public void logTraceDeoptMessage(Log log, FrameInfoQueryResult sourceTopFrame, boolean printOnlyTopFrames) {

        FrameInfoQueryResult virtualSourceFrame = sourceTopFrame;
        RistrettoVirtualInterpreterFrame targetFrame = (RistrettoVirtualInterpreterFrame) getTopFrame();

        while (virtualSourceFrame != null) {
            log.string("        at ");
            log.string("[Interpreter]Method ").string(targetFrame.getMethod().format("%H.%n(%p)"));
            log.string(" bci ");
            FrameInfoDecoder.logReadableBci(log, virtualSourceFrame.getEncodedBci());

            log.string("  return address ");
            var ret = targetFrame.getReturnAddress();
            if (ret == null) {
                log.string("null <==> not bottom frame").newline();
            } else {
                log.zhex(ret.getReturnAddress()).newline();
            }

            if (Deoptimizer.Options.TraceDeoptimizationDetails.getValue()) {
                Deoptimizer.printVirtualFrame(log, targetFrame);
            }

            virtualSourceFrame = virtualSourceFrame.getCaller();
            targetFrame = (RistrettoVirtualInterpreterFrame) targetFrame.getCaller();
        }
    }
}
