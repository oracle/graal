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

import static com.oracle.svm.core.thread.VirtualThreadHelper.asTarget;
import static com.oracle.svm.core.thread.VirtualThreadHelper.asThread;

import java.util.Locale;
import java.util.concurrent.Executor;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.LoomJDK;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.util.VMError;

@TargetClass(className = "java.lang.VirtualThread", onlyWith = LoomJDK.class)
public final class Target_java_lang_VirtualThread {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static boolean notifyJvmtiEvents;

    // Checkstyle: stop
    @Alias static int NEW;
    @Alias static int STARTED;
    @Alias static int RUNNABLE;
    @Alias static int RUNNING;
    @Alias static int PARKING;
    @Alias static int PARKED;
    @Alias static int PINNED;
    @Alias static int YIELDING;
    @Alias static int TERMINATED;
    @Alias static int RUNNABLE_SUSPENDED;
    @Alias static int PARKED_SUSPENDED;
    @Alias static Target_jdk_internal_vm_ContinuationScope VTHREAD_SCOPE;
    // Checkstyle: resume

    @Substitute
    private static void registerNatives() {
    }

    @Alias Executor scheduler;

    @Alias volatile Thread carrierThread;

    @Alias volatile Target_sun_nio_ch_Interruptible nioBlocker;

    @Alias volatile boolean interrupted;

    @Alias
    public static native Target_jdk_internal_vm_ContinuationScope continuationScope();

    @Alias
    native boolean joinNanos(long nanos) throws InterruptedException;

    @Delete
    native StackTraceElement[] asyncGetStackTrace();

    @Alias
    native StackTraceElement[] tryGetStackTrace();

    @Substitute
    boolean getAndClearInterrupt() {
        assert Thread.currentThread() == SubstrateUtil.cast(this, Object.class);
        Object token = VirtualThreadHelper.acquireInterruptLockMaybeSwitch(this);
        try {
            boolean oldValue = interrupted;
            if (oldValue) {
                interrupted = false;
            }
            asTarget(carrierThread).clearInterrupt();
            return oldValue;
        } finally {
            VirtualThreadHelper.releaseInterruptLockMaybeSwitchBack(this, token);
        }
    }

    @Alias
    private native void setCarrierThread(Target_java_lang_Thread carrier);

    @Substitute
    void mount() {
        Target_java_lang_Thread carrier = asTarget(Target_java_lang_Thread.currentCarrierThread());
        setCarrierThread(carrier);

        if (interrupted) {
            carrier.setInterrupt();
            // Checkstyle: allow Thread.isInterrupted: as in JDK
        } else if (carrier.isInterrupted()) {
            // Checkstyle: disallow Thread.isInterrupted
            Object token = VirtualThreadHelper.acquireInterruptLockMaybeSwitch(this);
            try {
                if (!interrupted) {
                    carrier.clearInterrupt();
                }
            } finally {
                VirtualThreadHelper.releaseInterruptLockMaybeSwitchBack(this, token);
            }
        }

        carrier.setCurrentThread(asThread(this));
    }

    @Substitute
    void unmount() {
        Target_java_lang_Thread carrier = asTarget(this.carrierThread);
        carrier.setCurrentThread(asThread(carrier));

        Object token = VirtualThreadHelper.acquireInterruptLockMaybeSwitch(this);
        try {
            setCarrierThread(null);
        } finally {
            VirtualThreadHelper.releaseInterruptLockMaybeSwitchBack(this, token);
        }
        carrier.clearInterrupt();
    }

    @Alias
    native int state();

    @Substitute
    Thread.State threadState() {
        int state = state();
        if (state == NEW) {
            return Thread.State.NEW;
        } else if (state == STARTED) {
            if (asTarget(this).threadContainer() == null) {
                return Thread.State.NEW;
            } else {
                return Thread.State.RUNNABLE;
            }
        } else if (state == RUNNABLE || state == RUNNABLE_SUSPENDED) {
            return Thread.State.RUNNABLE;
        } else if (state == RUNNING) {
            Object token = VirtualThreadHelper.acquireInterruptLockMaybeSwitch(this);
            try {
                Thread carrier = this.carrierThread;
                if (carrier != null) {
                    return asTarget(carrier).threadState();
                }
            } finally {
                VirtualThreadHelper.releaseInterruptLockMaybeSwitchBack(this, token);
            }
            return Thread.State.RUNNABLE;
        } else if (state == PARKING || state == YIELDING) {
            return Thread.State.RUNNABLE;
        } else if (state == PARKED || state == PARKED_SUSPENDED || state == PINNED) {
            switch (MonitorSupport.singleton().getParkedThreadStatus(asThread(this), false)) {
                case ThreadStatus.BLOCKED_ON_MONITOR_ENTER:
                    return Thread.State.BLOCKED;
                case ThreadStatus.PARKED:
                case ThreadStatus.IN_OBJECT_WAIT:
                    return Thread.State.WAITING;
                default:
                    throw VMError.shouldNotReachHere();
            }
        } else if (state == TERMINATED) {
            return Thread.State.TERMINATED;
        }
        throw new InternalError();
    }

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    native boolean isTerminated();

    @Substitute
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("VirtualThread[#");
        sb.append(asTarget(this).threadId());
        String name = asThread(this).getName();
        if (!name.isEmpty()) {
            sb.append(",");
            sb.append(name);
        }
        sb.append("]/");
        Thread carrier = carrierThread;
        if (carrier != null) {
            // include the carrier thread state and name when mounted
            Object token = VirtualThreadHelper.acquireInterruptLockMaybeSwitch(this);
            try {
                carrier = carrierThread;
                if (carrier != null) {
                    String stateAsString = asTarget(carrier).threadState().toString();
                    sb.append(stateAsString.toLowerCase(Locale.ROOT));
                    sb.append('@');
                    sb.append(carrier.getName());
                }
            } finally {
                VirtualThreadHelper.releaseInterruptLockMaybeSwitchBack(this, token);
            }
        }
        // include virtual thread state when not mounted
        if (carrier == null) {
            String stateAsString = threadState().toString();
            sb.append(stateAsString.toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /**
     * Only uses the interrupt lock when called from a different thread, therefore does not need to
     * be substituted to use {@link VirtualThreadHelper#acquireInterruptLockMaybeSwitch}.
     */
    @Alias
    native void interrupt();

    /**
     * Only uses the interrupt lock when pinned, therefore does not need to be substituted to use
     * {@link VirtualThreadHelper#acquireInterruptLockMaybeSwitch}.
     */
    @Alias
    native void unpark();
}

final class VirtualThreadHelper {
    static void blockedOn(Target_sun_nio_ch_Interruptible b) {
        Target_java_lang_VirtualThread self = asVTarget(Thread.currentThread());
        Object token = acquireInterruptLockMaybeSwitch(self);
        try {
            self.nioBlocker = b;
        } finally {
            releaseInterruptLockMaybeSwitchBack(self, token);
        }
    }

    /**
     * Must be used instead of {@code synchronized(interruptLock)} in all contexts where it is
     * possible that {@code VirtualThread.this == Thread.currentThread()} in order to avoid a
     * deadlock between virtual thread and carrier thread.
     *
     * @see #releaseInterruptLockMaybeSwitchBack
     */
    static Object acquireInterruptLockMaybeSwitch(Target_java_lang_VirtualThread self) {
        Object token = null;
        if (SubstrateUtil.cast(self, Object.class) == Thread.currentThread()) {
            /*
             * If we block on our interrupt lock, we yield, for which we first unmount. Unmounting
             * also tries to acquire our interrupt lock, so we likely block again, this time on the
             * carrier thread. Then, the virtual thread cannot continue to yield, and the carrier
             * thread might never get unparked, in which case both threads are stuck.
             */
            Thread carrier = self.carrierThread;
            PlatformThreads.setCurrentThread(carrier, carrier);
            token = self;
        }
        Object lock = asTarget(self).interruptLock;
        MonitorSupport.singleton().monitorEnter(lock);
        return token;
    }

    /** @see #acquireInterruptLockMaybeSwitch */
    static void releaseInterruptLockMaybeSwitchBack(Target_java_lang_VirtualThread self, Object token) {
        Object lock = asTarget(self).interruptLock;
        MonitorSupport.singleton().monitorExit(lock);
        if (token != null) {
            assert token == self;
            Thread carrier = asVTarget(token).carrierThread;
            assert Thread.currentThread() == carrier;
            PlatformThreads.setCurrentThread(carrier, asThread(token));
        }
    }

    static Target_java_lang_VirtualThread asVTarget(Object obj) {
        return (Target_java_lang_VirtualThread) obj;
    }

    static Target_java_lang_Thread asTarget(Object obj) {
        return (Target_java_lang_Thread) obj;
    }

    static Thread asThread(Object obj) {
        return (Thread) obj;
    }

    private VirtualThreadHelper() {
    }
}
