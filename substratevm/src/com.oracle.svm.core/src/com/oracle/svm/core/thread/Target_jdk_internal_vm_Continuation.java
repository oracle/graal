/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.util.VMError;

@TargetClass(className = "jdk.internal.vm.Continuation")
public final class Target_jdk_internal_vm_Continuation {
    @Substitute
    private static void registerNatives() {
    }

    @Alias //
    Runnable target;

    /** Treated as unsigned, located in native class {@code ContinuationEntry} in JDK code. */
    @Inject //
    int pinCount;

    /** Stored execution state if not executing. */
    @Inject //
    StoredContinuation stored;

    /** Replaced by {@link #stored}. */
    @Delete //
    Target_jdk_internal_vm_StackChunk tail;

    /** Frame pointer to return to when yielding, {@code null} if not executing. */
    @Inject //
    Pointer sp;

    /** While executing, where to return to when yielding, {@code null} if not executing. */
    @Inject //
    CodePointer ip;

    /** While executing, frame pointer of initial frame of continuation, {@code null} otherwise. */
    @Inject //
    Pointer baseSP;

    @Inject //
    int overflowCheckState;

    @Substitute
    boolean isEmpty() {
        return stored == null;
    }

    @Alias
    public native Target_jdk_internal_vm_ContinuationScope getScope();

    @Alias
    public native Target_jdk_internal_vm_Continuation getParent();

    @SuppressWarnings("unused")
    @Substitute
    @NeverInline("access stack pointer")
    private static int isPinned0(Target_jdk_internal_vm_ContinuationScope scope) {
        Target_java_lang_Thread carrier = JavaThreads.toTarget(Target_java_lang_Thread.currentCarrierThread());
        Target_jdk_internal_vm_Continuation cont = carrier.cont;
        if (cont != null) {
            while (true) {
                if (cont.pinCount != 0) {
                    return ContinuationSupport.FREEZE_PINNED_CS;
                }
                if (cont.getParent() == null) {
                    break;
                }
                if (cont.getScope() == scope) {
                    break;
                }
                cont = cont.getParent();
            }
            JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor(CurrentIsolate.getCurrentThread());
            if (anchor.isNonNull() && cont.baseSP.aboveThan(anchor.getLastJavaSP())) {
                return ContinuationSupport.FREEZE_PINNED_NATIVE;
            }
        }
        return ContinuationSupport.FREEZE_OK;
    }

    @Substitute
    boolean isStarted() {
        return stored != null;
    }

    @Alias
    public static native Target_jdk_internal_vm_Continuation getCurrentContinuation(Target_jdk_internal_vm_ContinuationScope scope);

    @Substitute
    static void enterSpecial(Target_jdk_internal_vm_Continuation c, @SuppressWarnings("unused") boolean isContinue, boolean isVirtualThread) {
        assert isVirtualThread;

        int stateBefore = StackOverflowCheck.singleton().getState();
        VMError.guarantee(!StackOverflowCheck.singleton().isYellowZoneAvailable());

        assert isContinue == (c.stored != null);
        if (isContinue) {
            StackOverflowCheck.singleton().setState(c.overflowCheckState);
        }
        try {
            ContinuationInternals.enterSpecial0(c, isContinue);
        } catch (StackOverflowError e) {
            throw (e == ImplicitExceptions.CACHED_STACK_OVERFLOW_ERROR) ? new StackOverflowError() : e;
        } finally {
            c.overflowCheckState = StackOverflowCheck.singleton().getState();
            StackOverflowCheck.singleton().setState(stateBefore);

            assert c.sp.isNull() && c.ip.isNull() && c.baseSP.isNull();
        }
    }

    @Substitute
    private static int doYield() {
        Target_java_lang_Thread carrier = JavaThreads.toTarget(Target_java_lang_Thread.currentCarrierThread());
        Target_jdk_internal_vm_Continuation cont = carrier.cont;
        int pinnedReason = isPinned0(cont.getScope());
        if (pinnedReason != 0) {
            return pinnedReason;
        }
        return ContinuationInternals.doYield0(cont);
    }

    @Alias
    private native void finish();

    @Substitute
    private static void enter(Target_jdk_internal_vm_Continuation c, @SuppressWarnings("unused") boolean isContinue) {
        try {
            c.target.run();
        } finally {
            c.finish();
        }
    }

    @Substitute
    void enter0() {
        /*
         * Normally enter would call enter0 and not the other way around, but our internals are
         * slightly different and we do this so we have "enter" on the bottom of the observable
         * continuation stack trace as required by tests.
         */
        enter(this, false);
    }

    @Substitute
    public static void pin() {
        Target_java_lang_Thread carrier = JavaThreads.toTarget(Target_java_lang_Thread.currentCarrierThread());
        if (carrier.cont != null) {
            if (carrier.cont.pinCount + 1 == 0) { // unsigned arithmetic
                throw new IllegalStateException("Pin overflow");
            }
            carrier.cont.pinCount++;
        }
    }

    @Substitute
    public static void unpin() {
        Target_java_lang_Thread carrier = JavaThreads.toTarget(Target_java_lang_Thread.currentCarrierThread());
        if (carrier.cont != null) {
            if (carrier.cont.pinCount == 0) {
                throw new IllegalStateException("Pin underflow");
            }
            carrier.cont.pinCount--;
        }
    }

    @Alias
    static native boolean isPinned(Target_jdk_internal_vm_ContinuationScope scope);

    /** Accesses {@link #tail}. */
    @Substitute
    void postYieldCleanup() {
    }

    /** Accesses {@link #tail}, no known callers. */
    @Delete
    native void dump();
}

@TargetClass(className = "jdk.internal.vm.StackChunk")
@Delete
final class Target_jdk_internal_vm_StackChunk {
}
