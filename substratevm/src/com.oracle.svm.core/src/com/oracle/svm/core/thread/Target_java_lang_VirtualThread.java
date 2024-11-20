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
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK21OrEarlier;
import com.oracle.svm.core.jdk.JDKLatest;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.monitor.MonitorInflationCause;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;

@TargetClass(className = "java.lang.VirtualThread")
public final class Target_java_lang_VirtualThread {
    // Checkstyle: stop
    @Alias static int NEW;
    @Alias static int STARTED;
    @TargetElement(onlyWith = JDK21OrEarlier.class) @Alias static int RUNNABLE;
    @Alias static int RUNNING;
    @Alias static int PARKING;
    @Alias static int PARKED;
    @Alias static int PINNED;
    @Alias static int YIELDING;
    @TargetElement(onlyWith = JDKLatest.class) @Alias static int YIELDED;
    @Alias static int TERMINATED;
    @Alias static int SUSPENDED;
    @TargetElement(onlyWith = JDKLatest.class) @Alias static int TIMED_PARKING;
    @TargetElement(onlyWith = JDKLatest.class) @Alias static int TIMED_PARKED;
    @TargetElement(onlyWith = JDKLatest.class) @Alias static int TIMED_PINNED;
    @TargetElement(onlyWith = JDKLatest.class) @Alias static int UNPARKED;
    @TargetElement(onlyWith = JDKLatest.class) @Alias static int BLOCKING;
    @TargetElement(onlyWith = JDKLatest.class) @Alias static int BLOCKED;
    @TargetElement(onlyWith = JDKLatest.class) @Alias static int UNBLOCKED;
    @TargetElement(onlyWith = JDKLatest.class) @Alias static int WAITING;
    @TargetElement(onlyWith = JDKLatest.class) @Alias static int WAIT;
    @TargetElement(onlyWith = JDKLatest.class) @Alias static int TIMED_WAITING;
    @TargetElement(onlyWith = JDKLatest.class) @Alias static int TIMED_WAIT;
    @Alias static Target_jdk_internal_vm_ContinuationScope VTHREAD_SCOPE;

    /**
     * (Re)initialize the default scheduler at runtime so that it does not reference any platform
     * threads of the image builder and uses the respective number of CPUs and system properties.
     */
    @Alias //
    @InjectAccessors(DefaultSchedulerAccessor.class) //
    public static ForkJoinPool DEFAULT_SCHEDULER;

    /**
     * (Re)initialize the unparker at runtime so that it does not reference any platform threads of
     * the image builder.
     */
    @Alias //
    @InjectAccessors(UnparkerAccessor.class) //
    @TargetElement(onlyWith = JDK21OrEarlier.class) //
    private static ScheduledExecutorService UNPARKER;

    @Alias //
    @InjectAccessors(DelayedTaskSchedulersAccessor.class) //
    @TargetElement(onlyWith = JDKLatest.class) //
    private static ScheduledExecutorService[] DELAYED_TASK_SCHEDULERS;

    /** Go through {@link #nondefaultScheduler}. */
    @Alias //
    @InjectAccessors(SchedulerAccessor.class) //
    public Executor scheduler;

    /**
     * {@code null} if using {@link #DEFAULT_SCHEDULER}, otherwise a specific {@link Executor}. This
     * avoids references to the {@link #DEFAULT_SCHEDULER} of the image builder which can reference
     * platform threads and fail the image build.
     */
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = NondefaultSchedulerSupplier.class) //
    private Executor nondefaultScheduler;

    @TargetElement(onlyWith = JDKLatest.class) @Alias volatile int state;

    // With our monitor implementation, we do not use these fields.
    @TargetElement(onlyWith = JDKLatest.class) @Delete volatile Target_java_lang_VirtualThread next;
    @TargetElement(onlyWith = JDKLatest.class) @Alias @InjectAccessors(AlwaysFalseAccessor.class) boolean blockPermit;
    @TargetElement(onlyWith = JDKLatest.class) @Alias @InjectAccessors(AlwaysFalseAccessor.class) boolean onWaitingList;
    @TargetElement(onlyWith = JDKLatest.class) @Alias @InjectAccessors(AlwaysFalseAccessor.class) boolean notified;
    // Checkstyle: resume

    @Alias
    private static native ForkJoinPool createDefaultScheduler();

    @Alias
    @TargetElement(onlyWith = JDK21OrEarlier.class)
    private static native ScheduledExecutorService createDelayedTaskScheduler();

    @Alias
    @TargetElement(onlyWith = JDKLatest.class)
    private static native ScheduledExecutorService[] createDelayedTaskSchedulers();

    @SuppressWarnings("unused")
    private static final class AlwaysFalseAccessor {
        static boolean get(Target_java_lang_VirtualThread vt) {
            return false;
        }

        static void set(Target_java_lang_VirtualThread vt, boolean value) {
            assert !value;
        }
    }

    private static final class DefaultSchedulerAccessor {
        private static volatile ForkJoinPool defaultScheduler;

        public static ForkJoinPool get() {
            ForkJoinPool result = defaultScheduler;
            if (result == null) {
                result = initializeDefaultScheduler();
            }
            return result;
        }

        private static synchronized ForkJoinPool initializeDefaultScheduler() {
            ForkJoinPool result = defaultScheduler;
            if (result == null) {
                result = createDefaultScheduler();
                defaultScheduler = result;
            }
            return result;
        }
    }

    private static final class UnparkerAccessor {
        private static volatile ScheduledExecutorService delayedTaskScheduler;

        @SuppressWarnings("unused")
        public static ScheduledExecutorService get() {
            ScheduledExecutorService result = delayedTaskScheduler;
            if (result == null) {
                result = initializeDelayedTaskScheduler();
            }
            return result;
        }

        private static synchronized ScheduledExecutorService initializeDelayedTaskScheduler() {
            ScheduledExecutorService result = delayedTaskScheduler;
            if (result == null) {
                result = createDelayedTaskScheduler();
                delayedTaskScheduler = result;
            }
            return result;
        }
    }

    private static final class DelayedTaskSchedulersAccessor {
        private static volatile ScheduledExecutorService[] delayedTaskSchedulers;

        @SuppressWarnings("unused")
        public static ScheduledExecutorService[] get() {
            ScheduledExecutorService[] result = delayedTaskSchedulers;
            if (result == null) {
                result = initializeDelayedTaskScheduler();
            }
            return result;
        }

        private static synchronized ScheduledExecutorService[] initializeDelayedTaskScheduler() {
            ScheduledExecutorService[] result = delayedTaskSchedulers;
            if (result == null) {
                result = createDelayedTaskSchedulers();
                delayedTaskSchedulers = result;
            }
            return result;
        }
    }

    @SuppressWarnings("unused")
    private static final class SchedulerAccessor {
        static Executor get(Target_java_lang_VirtualThread self) {
            Executor scheduler = self.nondefaultScheduler;
            if (scheduler == null) {
                scheduler = DefaultSchedulerAccessor.get();
            }
            return scheduler;
        }

        static void set(Target_java_lang_VirtualThread self, Executor executor) {
            assert self.nondefaultScheduler == null;
            if (executor != DefaultSchedulerAccessor.get()) {
                self.nondefaultScheduler = executor;
            }
        }
    }

    private static final class NondefaultSchedulerSupplier implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            Class<?> vthreadClass = ReflectionUtil.lookupClass(false, "java.lang.VirtualThread");
            Object defaultScheduler = ReflectionUtil.readStaticField(vthreadClass, "DEFAULT_SCHEDULER");
            return (originalValue == defaultScheduler) ? null : originalValue;
        }
    }

    @Substitute
    private static void registerNatives() {
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    private void notifyJvmtiStart() {
        // unimplemented (GR-46126)
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    private void notifyJvmtiEnd() {
        // unimplemented (GR-46126)
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    private void notifyJvmtiMount(boolean hide) {
        // unimplemented (GR-45392)
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    private void notifyJvmtiUnmount(boolean hide) {
        // unimplemented (GR-45392)
    }

    @Substitute
    @TargetElement(name = "notifyJvmtiHideFrames", onlyWith = JDK21OrEarlier.class)
    @SuppressWarnings({"static-method", "unused"})
    private void notifyJvmtiHideFramesJDK22(boolean hide) {
        // unimplemented (GR-45392)
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    @TargetElement(onlyWith = JDKLatest.class)
    private static void notifyJvmtiDisableSuspend(boolean enter) {
        // unimplemented (GR-51158)
    }

    @Substitute
    @SuppressWarnings("unused")
    @TargetElement(onlyWith = JDKLatest.class)
    private static void postPinnedEvent(String op) {
    }

    @Alias volatile Thread carrierThread;

    @Alias volatile Target_sun_nio_ch_Interruptible nioBlocker;

    @Alias volatile boolean interrupted;

    @Alias
    public static native Target_jdk_internal_vm_ContinuationScope continuationScope();

    @Delete
    native StackTraceElement[] asyncGetStackTrace();

    @Alias
    native StackTraceElement[] tryGetStackTrace();

    @Substitute // not needed on newer JDKs that use safe disableSuspendAndPreempt()
    @TargetElement(name = "getAndClearInterrupt", onlyWith = JDK21OrEarlier.class)
    boolean getAndClearInterruptJDK21() {
        assert Thread.currentThread() == SubstrateUtil.cast(this, Object.class);
        boolean oldValue = interrupted;
        if (oldValue) {
            Object token = VirtualThreadHelper.acquireInterruptLockMaybeSwitch(this);
            try {
                interrupted = false;
                asTarget(carrierThread).clearInterrupt();
            } finally {
                VirtualThreadHelper.releaseInterruptLockMaybeSwitchBack(this, token);
            }
        }
        return oldValue;
    }

    @Alias
    @TargetElement(onlyWith = JDKLatest.class)
    native void disableSuspendAndPreempt();

    @Alias
    @TargetElement(onlyWith = JDKLatest.class)
    native void enableSuspendAndPreempt();

    @Alias
    @TargetElement(onlyWith = JDKLatest.class)
    native Object carrierThreadAccessLock();

    @Alias
    private native void setCarrierThread(Target_java_lang_Thread carrier);

    /*
     * GR-57064: substitution should not be needed (acquireInterruptLockMaybeSwitch should not have
     * been necessary here), but currently cannot be removed because of the JFR registration.
     */
    @Substitute
    void mount() {
        Target_java_lang_Thread carrier = asTarget(Target_java_lang_Thread.currentCarrierThread());
        setCarrierThread(carrier);

        if (interrupted) {
            carrier.setInterrupt();
            // Checkstyle: allow Thread.isInterrupted: as in JDK
        } else if (carrier.isInterrupted()) {
            // Checkstyle: disallow Thread.isInterrupted
            synchronized (asTarget(this).interruptLock) {
                if (!interrupted) {
                    carrier.clearInterrupt();
                }
            }
        }

        carrier.setCurrentThread(asThread(this));
        if (HasJfrSupport.get()) {
            SubstrateJVM.getThreadRepo().registerThread(asThread(this));
        }
    }

    @Substitute // not needed on newer JDKs that use safe disableSuspendAndPreempt()
    @TargetElement(name = "unmount", onlyWith = JDK21OrEarlier.class)
    void unmountJDK21() {
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
    @TargetElement(onlyWith = JDKLatest.class)
    void setState(int s) {
        assert s != BLOCKING && s != BLOCKED && s != UNBLOCKED && s != WAITING && s != WAIT && s != TIMED_WAIT && s != TIMED_WAITING //
                        : "states should never be reached with our monitor implementation";
        state = s;
    }

    @Substitute
    @TargetElement(onlyWith = JDKLatest.class)
    @SuppressWarnings({"static-method", "unused"})
    void waitTimeoutExpired(byte seqNo) {
        throw VMError.shouldNotReachHere("not used in our monitor implementation");
    }

    @Delete
    @TargetElement(onlyWith = JDKLatest.class)
    static native void unblockVirtualThreads();

    @Delete
    @TargetElement(onlyWith = JDKLatest.class)
    private static native Target_java_lang_VirtualThread takeVirtualThreadListToUnblock();

    /** Needed for handling monitor-specific states. */
    @Substitute
    @TargetElement(onlyWith = JDKLatest.class)
    @SuppressWarnings("hiding")
    Thread.State threadState() {
        int state = state() & ~SUSPENDED;
        if (state == NEW) {
            return Thread.State.NEW;
        } else if (state == STARTED) {
            if (asTarget(this).threadContainer() == null) {
                return Thread.State.NEW;
            } else {
                return Thread.State.RUNNABLE;
            }
        } else if (state == UNPARKED || state == YIELDED) {
            return Thread.State.RUNNABLE;
        } else if (state == RUNNING) {
            if (Thread.currentThread() != asThread(this)) {
                disableSuspendAndPreempt();
                try {
                    synchronized (carrierThreadAccessLock()) {
                        Thread carrier = this.carrierThread;
                        if (carrier != null) {
                            return asTarget(carrier).threadState();
                        }
                    }
                } finally {
                    enableSuspendAndPreempt();
                }
            }
            return Thread.State.RUNNABLE;
        } else if (state == PARKING || state == YIELDING) {
            return Thread.State.RUNNABLE;
        } else if (state == PARKED || state == PINNED || state == TIMED_PARKED || state == TIMED_PINNED) {
            boolean timed = (state == TIMED_PARKED || state == TIMED_PINNED);
            int parkedThreadStatus = MonitorSupport.singleton().getParkedThreadStatus(asThread(this), timed);
            switch (parkedThreadStatus) {
                case ThreadStatus.BLOCKED_ON_MONITOR_ENTER:
                    return Thread.State.BLOCKED;
                case ThreadStatus.PARKED:
                case ThreadStatus.IN_OBJECT_WAIT:
                    return Thread.State.WAITING;
                case ThreadStatus.PARKED_TIMED:
                case ThreadStatus.IN_OBJECT_WAIT_TIMED:
                    return Thread.State.TIMED_WAITING;
                default:
                    throw VMError.shouldNotReachHereUnexpectedInput(parkedThreadStatus); // ExcludeFromJacocoGeneratedReport
            }
        } else if (state == TERMINATED) {
            return Thread.State.TERMINATED;
        } else if (state == TIMED_PARKING) {
            return Thread.State.RUNNABLE;
        }
        throw new InternalError();
    }

    /** Needed because {@link #disableSuspendAndPreempt()} does not exist on JDK 21. */
    @Substitute
    @TargetElement(name = "threadState", onlyWith = JDK21OrEarlier.class)
    @SuppressWarnings("hiding")
    Thread.State threadStateJDK21() {
        int state = state() & ~SUSPENDED;
        if (state == NEW) {
            return Thread.State.NEW;
        } else if (state == STARTED) {
            if (asTarget(this).threadContainer() == null) {
                return Thread.State.NEW;
            } else {
                return Thread.State.RUNNABLE;
            }
        } else if (state == RUNNABLE) {
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
        } else if (state == PARKED || state == PINNED) {
            int parkedThreadStatus = MonitorSupport.singleton().getParkedThreadStatus(asThread(this), false);
            switch (parkedThreadStatus) {
                case ThreadStatus.BLOCKED_ON_MONITOR_ENTER:
                    return Thread.State.BLOCKED;
                case ThreadStatus.PARKED:
                case ThreadStatus.IN_OBJECT_WAIT:
                    return Thread.State.WAITING;
                default:
                    throw VMError.shouldNotReachHereUnexpectedInput(parkedThreadStatus); // ExcludeFromJacocoGeneratedReport
            }
        } else if (state == TERMINATED) {
            return Thread.State.TERMINATED;
        }
        throw new InternalError();
    }

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    native boolean isTerminated();

    @Substitute // not needed on newer JDKs that use safe disableSuspendAndPreempt()
    @TargetElement(name = "toString", onlyWith = JDK21OrEarlier.class)
    public String toStringJDK21() {
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
            String stateAsString = threadStateJDK21().toString();
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
        assert JavaVersionUtil.JAVA_SPEC <= 21 : "blockedOn in newer JDKs uses safe disableSuspendAndPreempt";

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
        assert JavaVersionUtil.JAVA_SPEC <= 21 : "newer JDKs provide disableSuspendAndPreempt";

        Object token = null;
        if (SubstrateUtil.cast(self, Object.class) == Thread.currentThread()) {
            /*
             * If we block on our interrupt lock, we yield, for which we first unmount. Unmounting
             * also tries to acquire our interrupt lock, so we likely block again, this time on the
             * carrier thread. Then, the virtual thread cannot continue to yield, and the carrier
             * thread might never get unparked, in which case both threads are stuck.
             */
            Thread carrier = self.carrierThread;
            JavaThreads.setCurrentThread(carrier, carrier);
            token = self;
        }
        Object lock = asTarget(self).interruptLock;
        MonitorSupport.singleton().monitorEnter(lock, MonitorInflationCause.VM_INTERNAL);
        return token;
    }

    /** @see #acquireInterruptLockMaybeSwitch */
    static void releaseInterruptLockMaybeSwitchBack(Target_java_lang_VirtualThread self, Object token) {
        assert JavaVersionUtil.JAVA_SPEC <= 21 : "newer JDKs provide enableSuspendAndPreempt";

        Object lock = asTarget(self).interruptLock;
        MonitorSupport.singleton().monitorExit(lock, MonitorInflationCause.VM_INTERNAL);
        if (token != null) {
            assert token == self;
            Thread carrier = asVTarget(token).carrierThread;
            assert Thread.currentThread() == carrier;
            JavaThreads.setCurrentThread(carrier, asThread(token));
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
