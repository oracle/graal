/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.deopt;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.lang.ref.WeakReference;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoDecoder;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.core.log.StringBuilderLog;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.nodes.FrameState;
import jdk.vm.ci.code.InstalledCode;

/**
 * Deoptimized frame representation for methods that resume through
 * {@link DeoptimizedFrame.DeoptTargetTier#BaselineCompiledCode} target code.
 *
 * This class is used for both eager and lazy deoptimization and stores the target stack rewrite
 * state that is materialized back into {@link VirtualFrame} content.
 */
public final class DeoptimizedBaselineCompiledFrame extends DeoptimizedFrame {

    private final long sourceEncodedFrameSize;
    private final WeakReference<SubstrateInstalledCode> sourceInstalledCode;
    private final VirtualFrame topFrame;
    private final Deoptimizer.TargetContent targetContent;
    private final RelockObjectData[] relockedObjects;
    private final PinnedObject pin;
    private final CodePointer sourcePC;
    private final char[] completedMessage;
    /**
     * This flag indicates this DeoptimizedFrame corresponds to a state where
     * {@link FrameState#rethrowException()} is set. Within the execution, this marks a spot where
     * we have an exception and are now starting to walk the exceptions handlers to see if execution
     * should continue in a matching handler or unwind.
     */
    private final boolean rethrowException;

    DeoptimizedBaselineCompiledFrame(long sourceEncodedFrameSize, SubstrateInstalledCode sourceInstalledCode, VirtualFrame topFrame, int targetContentSize,
                    RelockObjectData[] relockedObjects, CodePointer sourcePC, boolean rethrowException, boolean isEagerDeopt) {
        this.sourceEncodedFrameSize = sourceEncodedFrameSize;
        this.topFrame = topFrame;
        this.targetContent = new Deoptimizer.TargetContent(targetContentSize, SubstrateTarget.getArchitecture().getByteOrder());
        this.relockedObjects = relockedObjects;
        this.sourceInstalledCode = sourceInstalledCode == null ? null : new WeakReference<>(sourceInstalledCode);
        this.sourcePC = sourcePC;
        // We assume that the frame will be pinned if and only if we are deoptimizing eagerly
        this.pin = isEagerDeopt ? PinnedObject.create(this) : null;
        StringBuilderLog sbl = new StringBuilderLog();
        sbl.string("deoptStub: completed ").string(isEagerDeopt ? "eagerly" : "lazily").string(" for DeoptimizedFrame at ").hex(Word.objectToUntrackedPointer(this)).newline();
        this.completedMessage = sbl.getResult().toCharArray();
        this.rethrowException = rethrowException;
    }

    @Override
    public PinnedObject getPin() {
        return pin;
    }

    /**
     * The frame size of the deoptimized method. This is the size of the physical stack frame that
     * is still present on the stack until the actual stack frame rewriting happens.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public long getSourceEncodedFrameSize() {
        return sourceEncodedFrameSize;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public long getSourceTotalFrameSize() {
        return CodeInfoQueryResult.getTotalFrameSize(sourceEncodedFrameSize);
    }

    /**
     * Returns the {@link InstalledCode} of the deoptimized method, or {@code null}. If a runtime
     * compiled method has been invalidated, the {@link InstalledCode} is no longer available. No
     * {@link InstalledCode} is available for native image methods (which are only deoptimized
     * during deoptimization testing).
     */
    @Override
    public SubstrateInstalledCode getSourceInstalledCode() {
        return sourceInstalledCode == null ? null : sourceInstalledCode.get();
    }

    /**
     * The top frame, i.e., the innermost callee of the inlining hierarchy.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public VirtualFrame getTopFrame() {
        return topFrame;
    }

    /**
     * The new stack content for the target methods. In the second step of deoptimization this
     * content is built from the entries of {@link VirtualFrame}s.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected Deoptimizer.TargetContent getTargetContent() {
        return targetContent;
    }

    /**
     * Releases the {@link PinnedObject} that ensures that this {@link DeoptimizedFrame} is not
     * moved by the GC after eager deoptimization. The {@link DeoptimizedFrame} is accessed during
     * GC when walking the stack after being installed during eager deoptimization. For lazy
     * deoptimization, the pin is not needed, and in that case this method must not be called.
     */
    @Uninterruptible(reason = "Object pinning internals must never end up in interruptible code.")
    public void unpin() {
        assert pin != null;
        pin.close();
    }

    /**
     * The code address inside the source method (= the method to deoptimize).
     */
    @Override
    public CodePointer getSourcePC() {
        return sourcePC;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    protected char[] getCompletedMessage() {
        return completedMessage;
    }

    /**
     * Fills the target content from the {@link VirtualFrame virtual frame} information. This method
     * must be uninterruptible.
     *
     * @param newSp the new stack pointer where execution will eventually continue
     */
    @Uninterruptible(reason = "Reads pointer values from the stack frame to unmanaged storage.")
    void buildContent(Pointer newSp) {

        VirtualFrame cur = topFrame;
        do {
            cur.getReturnAddress().write(targetContent);
            if (cur.getSavedBasePointer() != null) {
                cur.getSavedBasePointer().write(targetContent, newSp);
            }
            for (int i = 0; i < cur.values.length; i++) {
                if (cur.values[i] != null) {
                    cur.values[i].write(targetContent);
                }
            }
            cur = cur.getCaller();
        } while (cur != null);

        if (relockedObjects != null) {
            for (RelockObjectData relockedObject : relockedObjects) {
                MonitorSupport.singleton().doRelockObject(relockedObject.object, relockedObject.lockData);
            }
        }
    }

    /**
     * Rewrites the first return address entry to the exception handler. This lets the
     * deoptimization stub return to the exception handler instead of the regular return address of
     * the deoptimization target.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public void takeException() {
        if (rethrowException) {
            /*
             * This frame already corresponds to the start of an execution handler walk. Nothing
             * needs to be done.
             */
            return;
        }

        ReturnAddress firstAddressEntry = topFrame.getReturnAddress();
        CodePointer ip = Word.pointer(firstAddressEntry.returnAddress);
        CodeInfo info = CodeInfoTable.getImageCodeInfo(ip);
        SimpleCodeInfoQueryResult codeInfoQueryResult = UnsafeStackValue.get(SimpleCodeInfoQueryResult.class);
        CodeInfoAccess.lookupCodeInfo(info, ip, codeInfoQueryResult);
        long handler = codeInfoQueryResult.getExceptionOffset();
        if (handler == 0) {
            throwMissingExceptionHandler(info, firstAddressEntry);
        }
        firstAddressEntry.returnAddress += handler;
    }

    @Uninterruptible(reason = "Does not need to be uninterruptible because it throws a fatal error.", calleeMustBe = false, mayBeInlined = true)
    private static void throwMissingExceptionHandler(CodeInfo info, ReturnAddress firstAddressEntry) {
        throwMissingExceptionHandler0(info, firstAddressEntry);
    }

    private static void throwMissingExceptionHandler0(CodeInfo info, ReturnAddress firstAddressEntry) {
        CodeInfoQueryResult detailedQueryResult = new CodeInfoQueryResult();
        CodeInfoAccess.lookupCodeInfo(info, Word.pointer(firstAddressEntry.returnAddress), detailedQueryResult);
        FrameInfoQueryResult frameInfo = detailedQueryResult.getFrameInfo();
        throw Deoptimizer.fatalDeoptimizationError("No exception handler registered for deopt target", frameInfo);
    }

    @Override
    public void logTraceDeoptMessage(Log log, FrameInfoQueryResult sourceTopFrame, boolean printOnlyTopFrames) {
        FrameInfoQueryResult sourceFrame = sourceTopFrame;
        VirtualFrame targetFrame = getTopFrame();
        int count = 0;
        while (sourceFrame != null) {
            SharedMethod deoptMethod = sourceFrame.getDeoptMethod();

            log.string("        at ");
            if (deoptMethod != null) {
                StackTraceElement element = deoptMethod.asStackTraceElement(sourceFrame.getBci());
                if (element.getFileName() != null && element.getLineNumber() >= 0) {
                    log.string(element.toString());
                } else {
                    log.string(deoptMethod.format("%H.%n(%p)"));
                }
            } else {
                log.string("method at ").zhex(sourceFrame.getDeoptMethodAddress());
            }
            log.string(" bci ");
            FrameInfoDecoder.logReadableBci(log, sourceFrame.getEncodedBci());
            log.string("  return address ").zhex(targetFrame.getReturnAddress().returnAddress).newline();

            if (printOnlyTopFrames || Deoptimizer.Options.TraceDeoptimizationDetails.getValue()) {
                Deoptimizer.printVirtualFrame(log, targetFrame);
            }

            count++;
            if (printOnlyTopFrames && count >= 4) {
                break;
            }

            sourceFrame = sourceFrame.getCaller();
            targetFrame = targetFrame.getCaller();
        }
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public DeoptTargetTier getTargetTier() {
        return DeoptTargetTier.BaselineCompiledCode;
    }
}
