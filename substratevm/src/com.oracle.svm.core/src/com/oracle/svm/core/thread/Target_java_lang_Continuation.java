/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationImpl;
import com.oracle.svm.core.jdk.LoomJDK;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

@TargetClass(className = "java.lang.Continuation", onlyWith = LoomJDK.class)
public final class Target_java_lang_Continuation {
    @Alias//
    Runnable target;
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public StoredContinuation internalContinuation;

    @Alias//
    short cs;
    @Alias//
    Object yieldInfo;
    @Alias//
    byte flags;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true)//
    // Checkstyle: stop
    static byte FLAG_SAFEPOINT_YIELD = 1 << 1;
    // Checkstyle: resume

    /**
     * Frame pointer of
     * {@link Target_java_lang_Continuation#enterSpecial(Target_java_lang_Continuation, boolean)} if
     * continuation is running, else frame pointer of
     * {@link JavaContinuations#yield(Target_java_lang_Continuation)}.
     */
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Pointer sp = WordFactory.nullPointer();
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    CodePointer ip = WordFactory.nullPointer();

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    int monitorBefore;

    @SuppressWarnings("unused")
    @Alias
    public Target_java_lang_Continuation(Target_java_lang_ContinuationScope scope, Runnable target) {
    }

    @Alias
    public static native boolean yield(Target_java_lang_ContinuationScope scope);

    @Alias//
    boolean done;

    @Substitute
    private boolean isEmpty() {
        return sp.isNull();
    }

    @Alias
    public native Target_java_lang_ContinuationScope getScope();

    @Alias
    public native Target_java_lang_Continuation getParent();

    /**
     * Entry point of continuation.
     * 
     * Callees: {@link Target_java_lang_Continuation#mount()};
     * {@link Target_java_lang_Continuation#enterSpecial(Target_java_lang_Continuation, boolean)}};
     * {@link Target_java_lang_Continuation#unmount()};
     * {@link Target_java_lang_Continuation#postYieldCleanup(int)}.
     */
    @Alias
    public native void run();

    /**
     * Yield API of {@link Target_java_lang_Continuation}, calls
     * {@link Target_java_lang_Continuation#doYield(int)}.
     */
    @Alias
    native boolean yield0(Target_java_lang_ContinuationScope scope, Target_java_lang_Continuation child);

    @Alias
    private native void setMounted(boolean mounted);

    @Alias
    private native void postYieldCleanup(int origRefSP);

    @Substitute
    private void mount() {
        setMounted(true);
    }

    @Substitute
    private void unmount() {
        setMounted(false);
    }

    @Alias
    public native boolean isPreempted();

    @SuppressWarnings("unused")
    @Substitute
    @NeverInline("access stack pointer")
    private static int isPinned0(Target_java_lang_ContinuationScope scope) {
        return JavaContinuations.isPinned(
                        SubstrateUtil.cast(Thread.currentThread(), Target_java_lang_Thread.class),
                        scope, true);
    }

    @Substitute
    boolean isStarted() {
        return this.sp.isNonNull();
    }

    @Alias
    native boolean isDone();

    @Alias
    @TargetElement(onlyWith = LoomJDK.class)
    public static native Target_java_lang_Continuation getCurrentContinuation(Target_java_lang_ContinuationScope scope);

    @Substitute
    private static void enterSpecial(Target_java_lang_Continuation cont, boolean isContinue) {
        enter(cont, isContinue);
    }

    /**
     * Yields to nth continuation as the nth nearest enterSpecial returns.
     */
    @Substitute
    private static int doYield(int scopes) {
        assert scopes == 0;
        Target_java_lang_Thread tjlt = SubstrateUtil.cast(Thread.currentThread(), Target_java_lang_Thread.class);
        Target_java_lang_Continuation cont = JavaContinuations.getContinuation(tjlt);

        int pinnedReason = JavaContinuations.isPinned(tjlt, cont.getScope(), true);
        if (pinnedReason != 0) {
            return pinnedReason;
        }

        return JavaContinuations.yield(cont);
    }

    @Substitute
    private void finish() {
        done = true;
        sp = WordFactory.nullPointer();
        ip = WordFactory.nullPointer();
        assert isEmpty();
    }

    @Substitute
    @NeverInline("access stack pointer")
    @Uninterruptible(reason = "write stack", calleeMustBe = false)
    private static void enter(Target_java_lang_Continuation cont, boolean isContinue) {
        Pointer currentSP = KnownIntrinsics.readCallerStackPointer();
        CodePointer currentIP = KnownIntrinsics.readReturnAddress();

        if (isContinue) {
            assert cont.internalContinuation != null;
            assert cont.ip.isNonNull();

            byte[] buf = StoredContinuationImpl.allocateBuf(cont.internalContinuation);
            StoredContinuationImpl.writeBuf(cont.internalContinuation, buf);

            for (int i = 0; i < buf.length; i++) {
                currentSP.writeByte(i - buf.length, buf[i]);
            }

            CodePointer ip = cont.ip;

            cont.internalContinuation = null;
            cont.sp = currentSP;
            cont.ip = currentIP;
            KnownIntrinsics.farReturn(0, currentSP.subtract(buf.length), ip, false);
        } else {
            assert cont.sp.isNull() && cont.ip.isNull() && cont.internalContinuation == null;
            cont.monitorBefore = 0;
            cont.sp = currentSP;
            cont.ip = currentIP;

            cont.enter0();
        }
    }

    @Substitute
    private void enter0() {
        try {
            target.run();
        } finally {
            finish();
        }
    }

    @Substitute
    private int tryForceYield0(Target_java_lang_Thread thread) {
        Target_java_lang_Continuation cont = thread.getContinuation();
        Target_java_lang_Continuation innermost = cont;
        while (cont != null && cont != this) {
            cont = cont.getParent();
        }
        if (cont == null) {
            return -1;
        }

        if (innermost != cont) {
            Target_java_lang_ContinuationScope scope = cont.getScope();
            int pinned = JavaContinuations.isPinned(thread, cont.getScope(), false);
            if (pinned != 0) {
                return pinned;
            }
            cont.yieldInfo = scope;
        }
        int preemptResult = JavaContinuations.tryPreempt(this, SubstrateUtil.cast(thread, Thread.class));
        if (preemptResult == 0) {
            flags = FLAG_SAFEPOINT_YIELD;
        }
        return preemptResult;
    }
}

@TargetClass(className = "java.lang.Continuation", innerClass = "Pinned", onlyWith = LoomJDK.class)
final class Target_java_lang_Continuation_Pinned {
}

@TargetClass(className = "java.lang.Continuation", innerClass = "PreemptStatus", onlyWith = LoomJDK.class)
final class Target_java_lang_Continuation_PreemptStatus {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.None, isFinal = true)//
    Target_java_lang_Continuation_Pinned pinned;
}
