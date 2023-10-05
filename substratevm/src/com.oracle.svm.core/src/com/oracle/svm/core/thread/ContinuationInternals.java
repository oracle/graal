/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.util.VMError;

/** Implementation of and access to {@link Target_jdk_internal_vm_Continuation} internals. */
@InternalVMMethod
public final class ContinuationInternals {
    public static Pointer getBaseSP(Target_jdk_internal_vm_Continuation c) {
        return c.baseSP;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static StoredContinuation getStoredContinuation(Target_jdk_internal_vm_Continuation c) {
        return c.stored;
    }

    public static void setStoredContinuation(Target_jdk_internal_vm_Continuation cont, StoredContinuation instance) {
        cont.stored = instance;
    }

    @NeverInline("Needs a frame to return to when yielding.")
    static void enterSpecial0(Target_jdk_internal_vm_Continuation c, boolean isContinue) {
        // Note that Java-to-Java calls use only caller-saved registers, so we don't need to save
        // any register values which aren't spilled already and restore them when yielding
        enterSpecial1(c, isContinue);
    }

    /**
     * This method's frame is not part of the continuation stack. We can determine and store the SP
     * and IP of {@link #enterSpecial0} here and use them to directly return there from
     * {@link #doYield0}. Yielding destroys our frame, and when {@link #enterSpecial1} is invoked
     * again to resume the continuation, it creates a new frame at the base of the continuation
     * stack. When the continuation eventually finishes, {@link #enterSpecial2} would return to
     * {@link #enterSpecial1} at the instruction after its call site. However, the new frame
     * contains different data than that of the {@link #enterSpecial1} call that originally started
     * the continuation, which would lead to undefined behavior. Instead, {@link #enterSpecial2}
     * must have its own frame that is part of the continuation stack and, like yielding, must
     * return directly to {@link #enterSpecial0}.
     *
     * @return {@link Object} because we return to the caller via {@link KnownIntrinsics#farReturn},
     *         which passes an object result.
     */
    @NeverInline("Accesses caller stack pointer and return address.")
    private static Object enterSpecial1(Target_jdk_internal_vm_Continuation c, boolean isContinue) {
        Pointer callerSP = KnownIntrinsics.readCallerStackPointer();
        CodePointer callerIP = KnownIntrinsics.readReturnAddress();

        assert c.sp.isNull() && c.ip.isNull() && c.baseSP.isNull();
        if (isContinue) {
            StoredContinuation stored = c.stored;
            assert stored != null;
            c.ip = callerIP;
            c.sp = callerSP;
            c.baseSP = KnownIntrinsics.readStackPointer();
            c.stored = null;

            int framesSize = StoredContinuationAccess.getFramesSizeInBytes(stored);
            Pointer topSP = KnownIntrinsics.readStackPointer().subtract(framesSize);
            if (!StackOverflowCheck.singleton().isWithinBounds(topSP)) {
                throw ImplicitExceptions.CACHED_STACK_OVERFLOW_ERROR;
            }

            Object preparedData = ImageSingletons.lookup(ContinuationSupport.class).prepareCopy(stored);
            ContinuationSupport.enter(stored, topSP, preparedData);
            throw VMError.shouldNotReachHereAtRuntime();
        } else {
            assert c.stored == null;
            c.ip = callerIP;
            c.sp = callerSP;
            c.baseSP = KnownIntrinsics.readStackPointer();

            enterSpecial2(c);
            throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    @NeverInline("Needs a separate frame which is part of the continuation stack that we can eventually return to.")
    private static void enterSpecial2(Target_jdk_internal_vm_Continuation c) {
        try {
            c.enter0();
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere(t);
        }

        Pointer returnSP = c.sp;
        CodePointer returnIP = c.ip;

        c.ip = WordFactory.nullPointer();
        c.sp = WordFactory.nullPointer();
        c.baseSP = WordFactory.nullPointer();
        assert c.isEmpty();

        KnownIntrinsics.farReturn(null, returnSP, returnIP, false);
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @NeverInline("Needs a frame to resume the continuation at.")
    static Integer doYield0(Target_jdk_internal_vm_Continuation c) {
        // Note that Java-to-Java calls use only caller-saved registers, so we don't need to save
        // any register values which aren't spilled already and restore them when yielding
        return doYield1(c);
    }

    /**
     * @return {@link Integer} because we return here via {@link KnownIntrinsics#farReturn} and pass
     *         boxed {@link ContinuationSupport#FREEZE_OK} as result code.
     */
    @NeverInline("Accesses caller stack pointer and return address.")
    private static Integer doYield1(Target_jdk_internal_vm_Continuation c) {
        Pointer leafSP = KnownIntrinsics.readCallerStackPointer();
        CodePointer leafIP = KnownIntrinsics.readReturnAddress();

        Pointer returnSP = c.sp;
        CodePointer returnIP = c.ip;

        int preemptStatus = StoredContinuationAccess.allocateToYield(c, c.baseSP, leafSP, leafIP);
        if (preemptStatus != 0) {
            return preemptStatus;
        }

        c.ip = WordFactory.nullPointer();
        c.sp = WordFactory.nullPointer();
        c.baseSP = WordFactory.nullPointer();

        KnownIntrinsics.farReturn(null, returnSP, returnIP, false);
        throw VMError.shouldNotReachHereAtRuntime();
    }
}
