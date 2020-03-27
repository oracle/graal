/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.AbstractQueuedSynchronizer.ConditionObject;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.RestrictHeapAccess.Access;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.ThreadStatus;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.util.VMError;

//Checkstyle: stop
import sun.misc.Unsafe;
//Checkstyle resume

/**
 * Implementation of synchronized-related operations.
 * <p>
 * Most objects used in synchronization operations have a dedicated memory in the object to store a
 * {@link ReentrantLock}. The static analysis finds out which classes are used for synchronization
 * (and thus need a monitor) and assigns a monitor offset to point to the slot for the monitor. The
 * monitor is implemented with a {@link ReentrantLock}.
 * <p>
 * There are a few exceptions: {@link String} and {@link DynamicHub} objects never have monitor
 * fields because we want instances in the image heap to be immutable. Arrays never have monitor
 * fields because it would increase the size of every array and it is not possible to distinguish
 * between arrays with different header sizes. See
 * UniverseBuilder.canHaveMonitorFields(AnalysisType) for details.
 * <p>
 * Synchronization on {@link String}, arrays, and other types not detected by the static analysis
 * (like synchronization via JNI) fall back to a monitor stored in {@link #additionalMonitors}.
 * <p>
 * {@link Condition} objects are used to implement {@link #wait()} and {@link #notify()}. When an
 * object monitor needs a condition object, it is atomically swapped into its
 * {@link Target_java_util_concurrent_locks_ReentrantLock_NonfairSync#objectMonitorCondition} field.
 */
public class MonitorSupport {

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    /**
     * {@link Target_java_util_concurrent_locks_ReentrantLock_NonfairSync#objectMonitorCondition}
     * marker to indicate that the associated lock is an object monitor, but does not have a
     * Condition yet. This marker value is needed to identify monitor conditions for
     * {@link #maybeAdjustNewParkStatus}.
     */
    static final ConditionObject MONITOR_WITHOUT_CONDITION = (ConditionObject) new ReentrantLock().newCondition();

    /** Substituted in {@link Target_com_oracle_svm_core_MonitorSupport} */
    static long SYNC_MONITOR_CONDITION_FIELD_OFFSET = -1;

    /**
     * Secondary storage for monitor slots.
     *
     * Synchronized to prevent concurrent access and modification.
     */
    private final Map<Object, ReentrantLock> additionalMonitors = new WeakIdentityHashMap<>();
    private final ReentrantLock additionalMonitorsLock = new ReentrantLock();

    /**
     * Called from {@code Unsafe.park} when changing the current thread's state before parking the
     * thread. When the thread is parked due to a monitor operation via {@link ReentrantLock}, we
     * need to alter the new thread state so {@link Thread#getState()} gives the expected result.
     */
    public static int maybeAdjustNewParkStatus(int status) {
        Object blocker = LockSupport.getBlocker(Thread.currentThread());
        if (isMonitorCondition(blocker)) {
            // Blocked on one of the condition objects we use to implement Object.wait()
            if (status == ThreadStatus.PARKED_TIMED) {
                return ThreadStatus.IN_OBJECT_WAIT_TIMED;
            }
            return ThreadStatus.IN_OBJECT_WAIT;
        } else if (isMonitorLockSynchronizer(blocker)) { // Blocked directly on the lock
            return ThreadStatus.BLOCKED_ON_MONITOR_ENTER;
        }
        return status;
    }

    /**
     * Implements the monitorenter bytecode. The null check for the parameter must have already been
     * done beforehand.
     *
     * This is a static method so that it can be called directly via a foreign call from snippets.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "Avoid stack overflow error before yellow zone has been activated", calleeMustBe = false)
    public static void monitorEnter(Object obj) {
        /*
         * A stack overflow error in the locking code would be reported as a fatal error, since
         * there must not be any exceptions flowing out of the monitor code. Enabling the yellow
         * zone prevents stack overflows.
         */
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            VMOperationControl.guaranteeOkayToBlock("No Java synchronization must be performed within a VMOperation: if the object is already locked, the VM is at a deadlock");
            ThreadingSupportImpl.pauseRecurringCallback("No exception must flow out of the monitor code.");
            try {
                monitorEnterWithoutBlockingCheck(obj);
            } finally {
                ThreadingSupportImpl.resumeRecurringCallbackAtNextSafepoint();
            }
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @RestrictHeapAccess(reason = "No longer uninterruptible", overridesCallers = true, access = Access.UNRESTRICTED)
    @SuppressWarnings("try")
    public static void monitorEnterWithoutBlockingCheck(Object obj) {
        assert !ThreadingSupportImpl.isRecurringCallbackSupported() || ThreadingSupportImpl.isRecurringCallbackPaused();
        assert obj != null;
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            /* Synchronization is a no-op in single threaded mode. */
            return;
        }

        ReentrantLock lockObject;
        try {
            lockObject = ImageSingletons.lookup(MonitorSupport.class).getOrCreateMonitor(obj, true);
            lockObject.lock();
        } catch (Throwable ex) {
            /*
             * The foreign call from snippets to this method does not have an exception edge. So we
             * could miss an exception handler if we unwind an exception from this method.
             *
             * The only exception that the monitorenter bytecode is specified to throw is a
             * NullPointerException, and the null check already happens beforehand in the snippet.
             * So any exception would be surprising to users anyway.
             *
             * Finally, it would not be clear whether the monitor is locked or unlocked in case of
             * an exception.
             */
            throw VMError.shouldNotReachHere("Unexpected exception in MonitorSupport.monitorEnter", ex);
        }
    }

    /**
     * Implements the monitorexit bytecode. The null check for the parameter must have already been
     * done beforehand.
     *
     * This is a static method so that it can be called directly via a foreign call from snippets.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "Avoid stack overflow error before yellow zone has been activated", calleeMustBe = false)
    public static void monitorExit(Object obj) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            ThreadingSupportImpl.pauseRecurringCallback("No exception must flow out of the monitor code.");
            try {
                monitorExit0(obj);
            } finally {
                ThreadingSupportImpl.resumeRecurringCallbackAtNextSafepoint();
            }
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @RestrictHeapAccess(reason = "No longer uninterruptible", overridesCallers = true, access = Access.UNRESTRICTED)
    @SuppressWarnings("try")
    private static void monitorExit0(Object obj) {
        assert !ThreadingSupportImpl.isRecurringCallbackSupported() || ThreadingSupportImpl.isRecurringCallbackPaused();
        assert obj != null;
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            /* Synchronization is a no-op in single threaded mode. */
            return;
        }

        ReentrantLock lockObject;
        try {
            lockObject = ImageSingletons.lookup(MonitorSupport.class).getOrCreateMonitor(obj, true);
            lockObject.unlock();
        } catch (Throwable ex) {
            /*
             * The foreign call from snippets to this method does not have an exception edge. So we
             * could miss an exception handler if we unwind an exception from this method.
             *
             * Graal enforces structured locking and unlocking. This is a restriction compared to
             * the Java Virtual Machine Specification, but it ensures that we never need to throw an
             * IllegalMonitorStateException.
             */
            throw VMError.shouldNotReachHere("Unexpected exception in MonitorSupport.monitorExit", ex);
        }
    }

    /**
     * This is a highly unsafe method that patches the existing lock of an object so that the object
     * appears as if it has been locked from a different thread. It is only safe to call when the
     * object is locked, but no other threads are waiting, i.e., on a freshly allocated and freshly
     * locked object.
     */
    public void setExclusiveOwnerThread(Object obj, Thread thread) {
        assert obj != null;
        VMOperation.guaranteeInProgress("patching a lock while not being at a safepoint is too dangerous");
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            /* Synchronization is a no-op in single threaded mode. */
            return;
        }

        Target_java_util_concurrent_locks_ReentrantLock lock = SubstrateUtil.cast(getOrCreateMonitor(obj, true), Target_java_util_concurrent_locks_ReentrantLock.class);
        Target_java_util_concurrent_locks_AbstractOwnableSynchronizer sync = SubstrateUtil.cast(lock.sync, Target_java_util_concurrent_locks_AbstractOwnableSynchronizer.class);

        VMError.guarantee(sync.getExclusiveOwnerThread() != null, "Cannot patch the exclusiveOwnerThread of an object that is not locked");
        sync.setExclusiveOwnerThread(thread);
    }

    /**
     * Implements {@link Thread#holdsLock}.
     */
    public boolean holdsLock(Object obj) {
        assert obj != null;
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            /*
             * Since monitorenter and monitorexit are no-ops, we do not know the real answer. But
             * the current thread has exclusive access to the object, true is a correct answer.
             * Callers of holdsLock usually want to ensure that synchronization has occurred, i.e.,
             * assert that the returned value is true.
             */
            return true;
        }
        ReentrantLock lockObject = getOrCreateMonitor(obj, false);
        return lockObject != null && lockObject.isHeldByCurrentThread();
    }

    /**
     * Implements {@link Object#wait}.
     */
    @SuppressFBWarnings(value = {"WA_AWAIT_NOT_IN_LOOP"}, justification = "This method is a wait implementation.")
    public void wait(Object obj, long timeoutMillis) throws InterruptedException {
        assert obj != null;
        /* Required checks on the arguments. */
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("Timeout is negative.");
        }

        if (!SubstrateOptions.MultiThreaded.getValue()) {
            /*
             * Single-threaded wait. There is no other thread that can interrupt it, so it is just
             * sleeping. It is questionable whether this implementation is useful, especially
             * waiting without a timeout. But it is the best thing we can do.
             */
            Thread.sleep(timeoutMillis == 0 ? Long.MAX_VALUE : timeoutMillis);
            return;
        }

        /*
         * Ensure that the current thread holds the lock. Required by the specification of
         * Object.wait, and also required for our implementation.
         */
        ReentrantLock lock = ensureLocked(obj);
        Condition condition = getOrCreateCondition(lock, true);
        if (timeoutMillis == 0L) {
            condition.await();
        } else {
            condition.await(timeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Implements {@link Object#notify} and {@link Object#notifyAll}.
     */
    public void notify(Object obj, boolean notifyAll) {
        assert obj != null;
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            /* Single-threaded notify is a no-op. */
            return;
        }

        /* Make sure the current thread holds the lock on the receiver. */
        ReentrantLock lock = ensureLocked(obj);
        /* Find the wait/notify condition field of the receiver. */
        Condition condition = getOrCreateCondition(lock, false);
        /* If the receiver does not have a condition field, then it has not been waited on. */
        if (condition != null) {
            if (notifyAll) {
                condition.signalAll();
            } else {
                condition.signal();
            }
        }
    }

    /** Return the lock of the receiver. */
    private ReentrantLock ensureLocked(Object receiver) {
        ReentrantLock lockObject = getOrCreateMonitor(receiver, false);
        /*
         * If the monitor field is null then it has not been locked by this thread. If there is a
         * monitor, make sure it is locked by this thread.
         */
        if (lockObject == null || !lockObject.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("Receiver is not locked by the current thread.");
        }
        return lockObject;
    }

    private ReentrantLock getOrCreateMonitor(Object obj, boolean createIfNotExisting) {
        final DynamicHub hub = ObjectHeader.readDynamicHubFromObject(obj);
        final int monitorOffset = hub.getMonitorOffset();
        if (monitorOffset != 0) {
            /* The common case: memory for the monitor reserved in the object. */
            final ReentrantLock existingMonitor = KnownIntrinsics.convertUnknownValue(BarrieredAccess.readObject(obj, monitorOffset), ReentrantLock.class);
            if (existingMonitor != null || !createIfNotExisting) {
                assert existingMonitor == null || isMonitorLock(existingMonitor);
                return existingMonitor;
            }
            /* Atomically put a new lock in place of the null at the monitorOffset. */
            final ReentrantLock newMonitor = newMonitorLock();
            if (UNSAFE.compareAndSwapObject(obj, monitorOffset, null, newMonitor)) {
                return newMonitor;
            }
            /* We lost the race, use the lock some other thread installed. */
            return KnownIntrinsics.convertUnknownValue(BarrieredAccess.readObject(obj, monitorOffset), ReentrantLock.class);
        } else {
            /* No memory reserved for a lock in the object, fall back to our secondary storage. */
            /*
             * Lock the monitor map and maybe add a monitor for this object. This serialization
             * might be a scalability problem.
             */
            assert hub != DynamicHub.fromClass(Target_java_lang_ref_ReferenceQueue_Lock.class) : "ReferenceQueue.Lock must have a monitor field or we can deadlock accessing WeakIdentityHashMap below";
            additionalMonitorsLock.lock();
            try {
                final ReentrantLock existingEntry = additionalMonitors.get(obj);
                if (existingEntry != null) {
                    assert isMonitorLock(existingEntry);
                    return existingEntry;
                }
                if (!createIfNotExisting) {
                    return null;
                }
                final ReentrantLock newEntry = newMonitorLock();
                final ReentrantLock previousEntry = additionalMonitors.put(obj, newEntry);
                VMError.guarantee(previousEntry == null, "MonitorSupport.getOrCreateMonitor: Replaced monitor");
                return newEntry;
            } finally {
                additionalMonitorsLock.unlock();
            }
        }
    }

    private static ReentrantLock newMonitorLock() {
        final ReentrantLock newMonitor = new ReentrantLock();
        Target_java_util_concurrent_locks_ReentrantLock lock = SubstrateUtil.cast(newMonitor, Target_java_util_concurrent_locks_ReentrantLock.class);
        Target_java_util_concurrent_locks_ReentrantLock_NonfairSync sync = SubstrateUtil.cast(lock.sync, Target_java_util_concurrent_locks_ReentrantLock_NonfairSync.class);
        sync.objectMonitorCondition = SubstrateUtil.cast(MONITOR_WITHOUT_CONDITION, Target_java_util_concurrent_locks_AbstractQueuedSynchronizer_ConditionObject.class);
        assert isMonitorLock(newMonitor);
        return newMonitor;
    }

    private static boolean isMonitorLock(ReentrantLock lock) {
        return lock != null && isMonitorLockSynchronizer(SubstrateUtil.cast(lock, Target_java_util_concurrent_locks_ReentrantLock.class).sync);
    }

    private static boolean isMonitorLockSynchronizer(Object obj) {
        if (obj != null && obj.getClass() == Target_java_util_concurrent_locks_ReentrantLock_NonfairSync.class) {
            Target_java_util_concurrent_locks_ReentrantLock_NonfairSync sync = SubstrateUtil.cast(obj, Target_java_util_concurrent_locks_ReentrantLock_NonfairSync.class);
            return sync.objectMonitorCondition != null; // contains marker or actual condition
        }
        return false;
    }

    public ReentrantLock getMonitorForTesting(Object obj) {
        return getOrCreateMonitor(obj, false);
    }

    private static ConditionObject getOrCreateCondition(ReentrantLock monitorLock, boolean createIfNotExisting) {
        assert isMonitorLock(monitorLock);
        Target_java_util_concurrent_locks_ReentrantLock lock = SubstrateUtil.cast(monitorLock, Target_java_util_concurrent_locks_ReentrantLock.class);
        Target_java_util_concurrent_locks_ReentrantLock_NonfairSync sync = SubstrateUtil.cast(lock.sync, Target_java_util_concurrent_locks_ReentrantLock_NonfairSync.class);
        ConditionObject existingCondition = SubstrateUtil.cast(sync.objectMonitorCondition, ConditionObject.class);
        if (existingCondition == MONITOR_WITHOUT_CONDITION) {
            existingCondition = null;
        }
        if (existingCondition != null || !createIfNotExisting) {
            assert existingCondition == null || isMonitorCondition(existingCondition);
            return existingCondition;
        }
        ConditionObject newCondition = (ConditionObject) monitorLock.newCondition();
        if (!UNSAFE.compareAndSwapObject(sync, SYNC_MONITOR_CONDITION_FIELD_OFFSET, MONITOR_WITHOUT_CONDITION, newCondition)) {
            newCondition = SubstrateUtil.cast(sync.objectMonitorCondition, ConditionObject.class);
            assert isMonitorCondition(newCondition) : "race winner must have installed valid condition";
        }
        return newCondition;
    }

    private static boolean isMonitorCondition(Object obj) {
        if (obj != null && obj.getClass() == Target_java_util_concurrent_locks_AbstractQueuedSynchronizer_ConditionObject.class) {
            Target_java_util_concurrent_locks_AbstractQueuedSynchronizer enclosing = SubstrateUtil.cast(obj, Target_java_util_concurrent_locks_AbstractQueuedSynchronizer_ConditionObject.class).this$0;
            if (enclosing.getClass() == (Class<?>) Target_java_util_concurrent_locks_ReentrantLock_NonfairSync.class) {
                Target_java_util_concurrent_locks_ReentrantLock_NonfairSync sync = SubstrateUtil.cast(enclosing, Target_java_util_concurrent_locks_ReentrantLock_NonfairSync.class);
                return obj == sync.objectMonitorCondition;
            }
        }
        return false;
    }
}

@AutomaticFeature
class MonitorFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(MonitorSupport.class, new MonitorSupport());
    }
}

@TargetClass(value = AbstractOwnableSynchronizer.class)
final class Target_java_util_concurrent_locks_AbstractOwnableSynchronizer {

    @Alias
    protected native Thread getExclusiveOwnerThread();

    @Alias
    protected native void setExclusiveOwnerThread(Thread thread);
}

@TargetClass(value = ReentrantLock.class, innerClass = "Sync")
final class Target_java_util_concurrent_locks_ReentrantLock_Sync {
}

@TargetClass(value = ReentrantLock.class, innerClass = "NonfairSync")
final class Target_java_util_concurrent_locks_ReentrantLock_NonfairSync {
    /**
     * If this is a monitor's synchronizer, either {@link MonitorSupport#MONITOR_WITHOUT_CONDITION}
     * to mark it as part of a monitor that currently has no condition variable, or otherwise, a
     * specific {@link ConditionObject} that provides conditional waiting for this monitor. If
     * {@code null}, not associated with a monitor.
     */
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    volatile Target_java_util_concurrent_locks_AbstractQueuedSynchronizer_ConditionObject objectMonitorCondition;
}

@TargetClass(MonitorSupport.class)
final class Target_com_oracle_svm_core_MonitorSupport {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "objectMonitorCondition", declClass = Target_java_util_concurrent_locks_ReentrantLock_NonfairSync.class) //
    static long SYNC_MONITOR_CONDITION_FIELD_OFFSET;
}

@TargetClass(ReentrantLock.class)
final class Target_java_util_concurrent_locks_ReentrantLock {
    @Alias//
    Target_java_util_concurrent_locks_ReentrantLock_Sync sync;
}

@TargetClass(AbstractQueuedSynchronizer.class)
final class Target_java_util_concurrent_locks_AbstractQueuedSynchronizer {
}

@TargetClass(value = ConditionObject.class)
final class Target_java_util_concurrent_locks_AbstractQueuedSynchronizer_ConditionObject {
    /** Enclosing {@link AbstractQueuedSynchronizer} of this nested class. */
    @Alias Target_java_util_concurrent_locks_AbstractQueuedSynchronizer this$0;
}

@TargetClass(value = ReferenceQueue.class, innerClass = "Lock")
final class Target_java_lang_ref_ReferenceQueue_Lock {
}
