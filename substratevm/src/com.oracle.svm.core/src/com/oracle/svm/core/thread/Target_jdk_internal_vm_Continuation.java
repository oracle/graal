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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK19OrLater;
import com.oracle.svm.core.jdk.LoomJDK;
import com.oracle.svm.core.jdk.NotLoomJDK;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.util.VMError;

@TargetClass(className = "Continuation", classNameProvider = Package_jdk_internal_vm_helper.class, onlyWith = {JDK19OrLater.class, NotLoomJDK.class})
@Substitute
@SuppressWarnings("unused")
final class Target_jdk_internal_vm_Continuation__WithoutLoom {
    @Substitute
    static boolean yield(Target_jdk_internal_vm_ContinuationScope scope) {
        throw VMError.shouldNotReachHere();
    }

    @Substitute
    static void pin() {
        throw VMError.shouldNotReachHere();
    }

    @Substitute
    static void unpin() {
        throw VMError.shouldNotReachHere();
    }
}

@TargetClass(className = "Continuation", classNameProvider = Package_jdk_internal_vm_helper.class, onlyWith = LoomJDK.class)
public final class Target_jdk_internal_vm_Continuation {
    @Substitute
    private static void registerNatives() {
    }

    @Alias//
    Runnable target;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Continuation internal;

    /** Treated as unsigned, located in native class {@code ContinuationEntry} in JDK code. */
    @Inject//
    int pinCount;

    // Checkstyle: resume

    @Substitute
    private boolean isEmpty() {
        return internal == null || internal.isEmpty();
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
                    return LoomSupport.FREEZE_PINNED_CS;
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
            if (anchor.isNonNull() && cont.internal.getBaseSP().aboveThan(anchor.getLastJavaSP())) {
                return LoomSupport.FREEZE_PINNED_NATIVE;
            }
        }
        return LoomSupport.FREEZE_OK;
    }

    @Substitute
    boolean isStarted() {
        return internal != null && internal.isStarted();
    }

    @Alias
    public static native Target_jdk_internal_vm_Continuation getCurrentContinuation(Target_jdk_internal_vm_ContinuationScope scope);

    @Substitute
    static void enterSpecial(Target_jdk_internal_vm_Continuation c, boolean isContinue, boolean isVirtualThread) {
        assert isVirtualThread;
        if (!isContinue) {
            assert c.internal == null;
            c.internal = new Continuation(c::enter0);
        }
        c.internal.enter();
    }

    @Substitute
    private static int doYield() {
        Target_java_lang_Thread carrier = JavaThreads.toTarget(Target_java_lang_Thread.currentCarrierThread());
        Target_jdk_internal_vm_Continuation cont = carrier.cont;
        int pinnedReason = isPinned0(cont.getScope());
        if (pinnedReason != 0) {
            return pinnedReason;
        }
        return cont.internal.yield();
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
    private void enter0() {
        /*
         * Normally enter would call enter0 and not the other way around, but our internals are
         * slightly different and we do this so we have "enter" on the bottom of the observable
         * continuation stack trace as required by tests.
         */
        enter(this, false);
    }

    @Substitute
    static void pin() {
        Target_java_lang_Thread carrier = JavaThreads.toTarget(Target_java_lang_Thread.currentCarrierThread());
        if (carrier.cont != null) {
            if (carrier.cont.pinCount + 1 == 0) { // unsigned arithmetic
                throw new IllegalStateException("pin overflow");
            }
            carrier.cont.pinCount++;
        }
    }

    @Substitute
    static void unpin() {
        Target_java_lang_Thread carrier = JavaThreads.toTarget(Target_java_lang_Thread.currentCarrierThread());
        if (carrier.cont != null) {
            if (carrier.cont.pinCount == 0) {
                throw new IllegalStateException("pin underflow");
            }
            carrier.cont.pinCount--;
        }
    }
}
