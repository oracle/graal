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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.util.VMError;

/**
 * In a JDK that supports Project Loom virtual threads (JEP 425, starting with JDK 19 as preview
 * feature), code specific to virtual threads is part of {@link Thread} methods, e.g. {@code yield}
 * or {@code sleep}, and the implementation for platform threads is generally in methods named
 * {@code yield0} or {@code sleep0}, so we only substitute that platform thread code in
 * {@link Target_java_lang_Thread}, and do not expect several methods here to be reachable.
 *
 * @see <a href="https://openjdk.java.net/projects/loom/">Project Loom (Wiki, code, etc.)</a>
 */
final class LoomVirtualThreads implements VirtualThreads {
    private static Target_java_lang_VirtualThread cast(Thread thread) {
        return SubstrateUtil.cast(thread, Target_java_lang_VirtualThread.class);
    }

    @Override
    public ThreadFactory createFactory() {
        return Target_java_lang_Thread.ofVirtual().factory();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public boolean isVirtual(Thread thread) {
        return Target_java_lang_VirtualThread.class.isInstance(thread);
    }

    @Override
    public void join(Thread thread, long millis) throws InterruptedException {
        if (thread.isAlive()) {
            long nanos = MILLISECONDS.toNanos(millis);
            cast(thread).joinNanos(nanos);
        }
    }

    @Platforms({}) // fails image build if reachable
    private static RuntimeException unreachable() {
        return VMError.shouldNotReachHere();
    }

    @Override
    public boolean getAndClearInterrupt(Thread thread) {
        return cast(thread).getAndClearInterrupt();
    }

    @Override
    public void yield() {
        throw unreachable();
    }

    @Override
    public void sleepMillis(long millis) {
        throw unreachable();
    }

    @Override
    public boolean isAlive(Thread thread) {
        throw unreachable();
    }

    @Override
    public void unpark(Thread thread) {
        throw unreachable();
    }

    @Override
    public void park() {
        throw unreachable();
    }

    @Override
    public void parkNanos(long nanos) {
        throw unreachable();
    }

    @Override
    public void parkUntil(long deadline) {
        throw unreachable();
    }

    @Override
    public void pinCurrent() {
        Target_jdk_internal_vm_Continuation.pin();
    }

    @Override
    public void unpinCurrent() {
        Target_jdk_internal_vm_Continuation.unpin();
    }

    @Override
    public Executor getScheduler(Thread thread) {
        return cast(thread).scheduler;
    }

    @Override
    public void blockedOn(Target_sun_nio_ch_Interruptible b) {
        VirtualThreadHelper.blockedOn(b);
    }

    @Override
    public StackTraceElement[] getVirtualOrPlatformThreadStackTrace(boolean filterExceptions, Thread thread, Pointer callerSP) {
        if (!isVirtual(thread)) {
            return getPlatformThreadStackTrace(filterExceptions, thread, callerSP);
        }
        if (thread == Thread.currentThread()) {
            return getVirtualThreadStackTrace(filterExceptions, thread, callerSP);
        }
        assert !filterExceptions : "exception stack traces can be taken only for the current thread";
        return asyncGetStackTrace(cast(thread));
    }

    @Override
    public StackTraceElement[] getVirtualOrPlatformThreadStackTraceAtSafepoint(Thread thread, Pointer callerSP) {
        if (!isVirtual(thread)) {
            return getPlatformThreadStackTraceAtSafepoint(thread, callerSP);
        }
        return getVirtualThreadStackTrace(false, thread, callerSP);
    }

    private static StackTraceElement[] getVirtualThreadStackTrace(boolean filterExceptions, Thread thread, Pointer callerSP) {
        Thread carrier = cast(thread).carrierThread;
        if (carrier == null) {
            return null;
        }
        Pointer endSP = getCarrierSPOrElse(carrier, WordFactory.nullPointer());
        if (endSP.isNull()) {
            return null;
        }
        if (carrier == PlatformThreads.currentThread.get()) {
            return StackTraceUtils.getStackTrace(filterExceptions, callerSP, endSP);
        }
        assert VMOperation.isInProgressAtSafepoint();
        return StackTraceUtils.getThreadStackTraceAtSafepoint(PlatformThreads.getIsolateThread(carrier), endSP);
    }

    private static Pointer getCarrierSPOrElse(Thread carrier, Pointer other) {
        Target_jdk_internal_vm_Continuation cont = JavaThreads.toTarget(carrier).cont;
        while (cont != null) {
            if (cont.getScope() == Target_java_lang_VirtualThread.VTHREAD_SCOPE) {
                return cont.internal.getBaseSP();
            }
            cont = cont.getParent();
        }
        return other;
    }

    /** Adapted from {@code VirtualThread.asyncGetStackTrace()}. */
    private static StackTraceElement[] asyncGetStackTrace(Target_java_lang_VirtualThread thread) {
        StackTraceElement[] stackTrace;
        do {
            if (thread.carrierThread != null) {
                stackTrace = asyncMountedGetStackTrace(thread);
            } else {
                stackTrace = thread.tryGetStackTrace();
            }
            if (stackTrace == null) {
                Thread.yield();
            }
        } while (stackTrace == null);
        return stackTrace;
    }

    @SuppressFBWarnings(value = "BC_IMPOSSIBLE_CAST", justification = "substitution hides acual type")
    private static StackTraceElement[] asyncMountedGetStackTrace(Target_java_lang_VirtualThread thread) {
        return StackTraceUtils.asyncGetStackTrace(SubstrateUtil.cast(thread, Thread.class));
    }

    private static StackTraceElement[] getPlatformThreadStackTrace(boolean filterExceptions, Thread thread, Pointer callerSP) {
        if (thread == PlatformThreads.currentThread.get()) {
            Pointer startSP = getCarrierSPOrElse(thread, callerSP);
            return StackTraceUtils.getStackTrace(filterExceptions, startSP, WordFactory.nullPointer());
        }
        assert !filterExceptions : "exception stack traces can be taken only for the current thread";
        return StackTraceUtils.asyncGetStackTrace(thread);
    }

    private static StackTraceElement[] getPlatformThreadStackTraceAtSafepoint(Thread thread, Pointer callerSP) {
        Pointer carrierSP = getCarrierSPOrElse(thread, WordFactory.nullPointer());
        IsolateThread isolateThread = PlatformThreads.getIsolateThread(thread);
        if (isolateThread == CurrentIsolate.getCurrentThread()) {
            Pointer startSP = carrierSP.isNonNull() ? carrierSP : callerSP;
            /*
             * Internal frames from the VMOperation handling show up in the stack traces, but we are
             * OK with that.
             */
            return StackTraceUtils.getStackTrace(false, startSP, WordFactory.nullPointer());
        }
        if (carrierSP.isNonNull()) { // mounted virtual thread, skip its frames
            CodePointer carrierIP = FrameAccess.singleton().readReturnAddress(carrierSP);
            return StackTraceUtils.getThreadStackTraceAtSafepoint(carrierSP, WordFactory.nullPointer(), carrierIP);
        }
        return StackTraceUtils.getThreadStackTraceAtSafepoint(isolateThread, WordFactory.nullPointer());
    }

}
