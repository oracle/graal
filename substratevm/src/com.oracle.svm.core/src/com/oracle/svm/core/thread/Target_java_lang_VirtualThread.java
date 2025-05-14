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

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

@TargetClass(className = "java.lang.VirtualThread")
public final class Target_java_lang_VirtualThread {
    // Checkstyle: stop
    @Alias static int NEW;
    @Alias static int STARTED;
    @Alias static int RUNNING;
    @Alias static int PARKING;
    @Alias static int PARKED;
    @Alias static int PINNED;
    @Alias static int YIELDING;
    @Alias static int YIELDED;
    @Alias static int TERMINATED;
    @Alias static int SUSPENDED;
    @Alias static int TIMED_PARKING;
    @Alias static int TIMED_PARKED;
    @Alias static int TIMED_PINNED;
    @Alias static int UNPARKED;
    @Alias static int BLOCKING;
    @Alias static int BLOCKED;
    @Alias static int UNBLOCKED;
    @Alias static int WAITING;
    @Alias static int WAIT;
    @Alias static int TIMED_WAITING;
    @Alias static int TIMED_WAIT;
    @Alias static Target_jdk_internal_vm_ContinuationScope VTHREAD_SCOPE;

    /**
     * (Re)initialize the default scheduler at runtime so that it does not reference any platform
     * threads of the image builder and uses the respective number of CPUs and system properties.
     */
    @Alias //
    @InjectAccessors(DefaultSchedulerAccessor.class) //
    public static ForkJoinPool DEFAULT_SCHEDULER;

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

    @Alias volatile int state;

    // With our monitor implementation, we do not use these fields.
    @Delete volatile Target_java_lang_VirtualThread next;
    @Alias @InjectAccessors(AlwaysFalseAccessor.class) boolean blockPermit;
    @Alias @InjectAccessors(AlwaysFalseAccessor.class) boolean onWaitingList;
    @Alias @InjectAccessors(AlwaysFalseAccessor.class) boolean notified;
    // Checkstyle: resume

    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    public long jfrEpochId;

    @Alias
    private static native ForkJoinPool createDefaultScheduler();

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
    @SuppressWarnings({"static-method", "unused"})
    private static void notifyJvmtiDisableSuspend(boolean enter) {
        // unimplemented (GR-51158)
    }

    @Substitute
    @SuppressWarnings("unused")
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

    @Alias
    native void disableSuspendAndPreempt();

    @Alias
    native void enableSuspendAndPreempt();

    @Alias
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

    @Alias
    native int state();

    @Substitute
    void setState(int s) {
        assert s != BLOCKING && s != BLOCKED && s != UNBLOCKED && s != WAITING && s != WAIT && s != TIMED_WAIT && s != TIMED_WAITING //
                        : "states should never be reached with our monitor implementation";
        state = s;
    }

    @Substitute
    @SuppressWarnings({"static-method", "unused"})
    void waitTimeoutExpired(byte seqNo) {
        throw VMError.shouldNotReachHere("not used in our monitor implementation");
    }

    @Delete
    static native void unblockVirtualThreads();

    @Delete
    private static native Target_java_lang_VirtualThread takeVirtualThreadListToUnblock();

    /** Needed for handling monitor-specific states. */
    @Substitute
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

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    native boolean isTerminated();
}

final class VirtualThreadHelper {

    static Target_java_lang_Thread asTarget(Object obj) {
        return (Target_java_lang_Thread) obj;
    }

    static Thread asThread(Object obj) {
        return (Thread) obj;
    }

    private VirtualThreadHelper() {
    }
}
