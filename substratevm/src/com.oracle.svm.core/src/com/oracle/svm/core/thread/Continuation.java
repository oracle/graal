/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.util.VMError;

/**
 * Foundation for continuation support via {@link SubstrateVirtualThread} or
 * {@linkplain Target_jdk_internal_vm_Continuation Project Loom}.
 */
@InternalVMMethod
public final class Continuation {
    @Fold
    public static boolean isSupported() {
        return SubstrateOptions.SupportContinuations.getValue() || LoomSupport.isEnabled();
    }

    public static final int YIELDING = -2;
    public static final int FREEZE_OK = LoomSupport.FREEZE_OK;

    private final Runnable target;

    public StoredContinuation stored;

    /** Frame pointer to return to when yielding, {@code null} if not executing. */
    private Pointer sp;

    /** While executing, where to return to when yielding, {@code null} if not executing. */
    private CodePointer ip;

    /** While executing, frame pointer of initial frame of continuation, {@code null} otherwise. */
    private Pointer baseSP;

    private boolean done;
    private int overflowCheckState;

    Continuation(Runnable target) {
        this.target = target;
    }

    public Pointer getBaseSP() {
        return baseSP;
    }

    void enter() {
        int stateBefore = StackOverflowCheck.singleton().getState();
        VMError.guarantee(!StackOverflowCheck.singleton().isYellowZoneAvailable());

        boolean isContinue = (stored != null);
        if (isContinue) {
            StackOverflowCheck.singleton().setState(overflowCheckState);
        }
        try {
            enter0(isContinue);
        } catch (StackOverflowError e) {
            throw (e == ImplicitExceptions.CACHED_STACK_OVERFLOW_ERROR) ? new StackOverflowError() : e;
        } finally {
            overflowCheckState = StackOverflowCheck.singleton().getState();
            StackOverflowCheck.singleton().setState(stateBefore);

            assert sp.isNull() && ip.isNull() && baseSP.isNull();
        }
    }

    @NeverInline("Needs a frame to return to when yielding.")
    private void enter0(boolean isContinue) {
        // Note that Java-to-Java calls use only caller-saved registers, so we don't need to save
        // any register values which aren't spilled already and restore them when yielding
        enter1(isContinue);
    }

    /**
     * This method's frame is not part of the continuation stack. We can determine and store the SP
     * and IP of {@link #enter0} here and use them to directly return there from {@link #yield}.
     * Yielding destroys our frame, and when {@link #enter1} is invoked again to resume the
     * continuation, it creates a new frame at the base of the continuation stack. When the
     * continuation eventually finishes, {@link #enter2} would return to {@link #enter1} at the
     * instruction after its call site. However, the new frame contains different data than that of
     * the {@link #enter1} call that originally started the continuation, which would lead to
     * undefined behavior. Instead, {@link #enter2} must have its own frame that is part of the
     * continuation stack and, like yielding, must return directly to {@link #enter0}.
     *
     * @return {@link Object} because we return to the caller via {@link KnownIntrinsics#farReturn},
     *         which passes an object result.
     */
    @NeverInline("Accesses caller stack pointer and return address.")
    private Object enter1(boolean isContinue) {
        Pointer callerSP = KnownIntrinsics.readCallerStackPointer();
        CodePointer callerIP = KnownIntrinsics.readReturnAddress();
        Pointer currentSP = KnownIntrinsics.readStackPointer();

        assert sp.isNull() && ip.isNull() && baseSP.isNull();
        if (isContinue) {
            StoredContinuation cont = this.stored;
            assert cont != null;
            this.ip = callerIP;
            this.sp = callerSP;
            this.baseSP = currentSP;
            this.stored = null;

            int framesSize = StoredContinuationAccess.getFramesSizeInBytes(cont);
            Pointer topSP = currentSP.subtract(framesSize);
            if (!StackOverflowCheck.singleton().isWithinBounds(topSP)) {
                throw ImplicitExceptions.CACHED_STACK_OVERFLOW_ERROR;
            }

            Object preparedData = ImageSingletons.lookup(ContinuationSupport.class).prepareCopy(cont);
            ContinuationSupport.enter(cont, topSP, preparedData);
            throw VMError.shouldNotReachHere();
        } else {
            assert stored == null;
            this.ip = callerIP;
            this.sp = callerSP;
            this.baseSP = currentSP;

            enter2();
            throw VMError.shouldNotReachHere();
        }
    }

    @NeverInline("Needs a separate frame which is part of the continuation stack that we can eventually return to.")
    private void enter2() {
        try {
            target.run();
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere(t);
        }

        Pointer returnSP = sp;
        CodePointer returnIP = ip;

        done = true;
        ip = WordFactory.nullPointer();
        sp = WordFactory.nullPointer();
        baseSP = WordFactory.nullPointer();
        assert isEmpty();

        KnownIntrinsics.farReturn(null, returnSP, returnIP, false);
        throw VMError.shouldNotReachHere();
    }

    int tryPreempt(Thread thread) {
        TryPreemptOperation vmOp = new TryPreemptOperation(this, thread);
        vmOp.enqueue();
        return vmOp.preemptStatus;
    }

    @NeverInline("Needs a frame to resume the continuation at.")
    Integer yield() {
        // Note that Java-to-Java calls use only caller-saved registers, so we don't need to save
        // any register values which aren't spilled already and restore them when yielding
        return yield0();
    }

    /**
     * @return {@link Integer} because we return here via {@link KnownIntrinsics#farReturn} and pass
     *         boxed {@link #FREEZE_OK} as result code.
     */
    @NeverInline("Accesses caller stack pointer and return address.")
    private Integer yield0() {
        Pointer leafSP = KnownIntrinsics.readCallerStackPointer();
        CodePointer leafIP = KnownIntrinsics.readReturnAddress();

        Pointer returnSP = sp;
        CodePointer returnIP = ip;

        int preemptStatus = StoredContinuationAccess.allocateToYield(this, baseSP, leafSP, leafIP);
        if (preemptStatus != 0) {
            return preemptStatus;
        }

        ip = WordFactory.nullPointer();
        sp = WordFactory.nullPointer();
        baseSP = WordFactory.nullPointer();

        KnownIntrinsics.farReturn(null, returnSP, returnIP, false);
        throw VMError.shouldNotReachHere();
    }

    public boolean isStarted() {
        return stored != null || ip.isNonNull();
    }

    public boolean isEmpty() {
        return stored == null;
    }

    public boolean isDone() {
        return done;
    }

    private static final class TryPreemptOperation extends JavaVMOperation {
        int preemptStatus = FREEZE_OK;

        final Continuation cont;
        final Thread thread;

        TryPreemptOperation(Continuation cont, Thread thread) {
            super(VMOperationInfos.get(TryPreemptOperation.class, "Try to preempt continuation", SystemEffect.SAFEPOINT));
            this.cont = cont;
            this.thread = thread;
        }

        @Override
        public void operate() {
            IsolateThread vmThread = PlatformThreads.getIsolateThread(thread);
            Pointer baseSP = cont.baseSP;
            Pointer returnSP = cont.sp;
            CodePointer returnIP = cont.ip;
            preemptStatus = StoredContinuationAccess.allocateToPreempt(cont, baseSP, vmThread);
            if (preemptStatus == FREEZE_OK) {
                cont.sp = WordFactory.nullPointer();
                cont.baseSP = WordFactory.nullPointer();
                cont.ip = WordFactory.nullPointer();
                VMThreads.ActionOnExitSafepointSupport.setSwitchStack(vmThread);
                VMThreads.ActionOnExitSafepointSupport.setSwitchStackTarget(vmThread, returnSP, returnIP);
            }
        }
    }
}
