/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.ClassSynchronizationSupport;
import com.oracle.svm.core.hub.ClassSynchronizationSupport.ClassSynchronizationTarget;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

/**
 * Implementation of synchronized-related operations.
 *
 * Current implementation until GR-6980 is implemented: Most non-array objects used in
 * synchronization operations have a dedicated memory in the object to store a {@link ReentrantLock}
 * and a {@link Condition}. The static analysis finds out which classes are used for synchronization
 * (and need a monitor), and which classes are used in wait/notify (and need a condition).
 *
 * There are a few exceptions: {@link String} and {@link DynamicHub} never have monitor fields
 * because we want instances in the image heap to be immutable. Arrays never have monitor fields
 * because it would increase the size of every array and it is not possible to distinguish between
 * arrays with different header sizes.
 *
 * Synchronization on {@link DynamicHub} (= {@link java.lang.Class}) is really disallowed. We
 * support "static synchronized" methods by replacing the {@link Class} with a
 * {@link ClassSynchronizationTarget}, see documentation in {@link ClassSynchronizationSupport}. If
 * someone synchronizes manually on a {@link Class}, an error is raised in {@link #filterClass}.
 *
 * Synchronization on {@link String}, arrays, and other types not detected by the static analysis
 * (like synchronization via JNI) fall back to a monitor stored in a {@link MonitorSupport
 * concurrent map}. This is a memory leak: The key of the map is a strong reference, so the GC will
 * see it as alive forever. But it is better than disallowing synchronization until GR-6980 is done.
 */
public class MonitorSupport {

    final ConcurrentMap<Object, ReentrantLock> additionalMonitors = new ConcurrentHashMap<>();
    final ConcurrentMap<Object, Condition> additionalConditions = new ConcurrentHashMap<>();

    /**
     * Implements the monitorenter bytecode. The null check for the parameter must have already been
     * done beforehand.
     *
     * This is a static method so that it can be called directly via a foreign call from snippets.
     */
    @SubstrateForeignCallTarget
    public static void monitorEnter(Object obj) {
        assert obj != null;
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            /* Synchronization is a no-op in single threaded mode. */
            return;
        }

        try {
            ImageSingletons.lookup(MonitorSupport.class).getOrCreateMonitor(obj, true).lock();

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
            VMError.shouldNotReachHere(ex);
        }
    }

    /**
     * Implements the monitorexit bytecode. The null check for the parameter must have already been
     * done beforehand.
     *
     * This is a static method so that it can be called directly via a foreign call from snippets.
     */
    @SubstrateForeignCallTarget
    public static void monitorExit(Object obj) {
        assert obj != null;
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            /* Synchronization is a no-op in single threaded mode. */
            return;
        }

        try {
            ImageSingletons.lookup(MonitorSupport.class).getOrCreateMonitor(obj, true).unlock();

        } catch (Throwable ex) {
            /*
             * The foreign call from snippets to this method does not have an exception edge. So we
             * could miss an exception handler if we unwind an exception from this method.
             *
             * Graal enforces structured locking and unlocking. This is a restriction compared to
             * the Java Virtual Machine Specification, but it ensures that we never need to throw an
             * IllegalMonitorStateException.
             */
            VMError.shouldNotReachHere(ex);
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

        Target_java_util_concurrent_locks_ReentrantLock lock = KnownIntrinsics.unsafeCast(getOrCreateMonitor(obj, true), Target_java_util_concurrent_locks_ReentrantLock.class);
        Target_java_util_concurrent_locks_AbstractOwnableSynchronizer sync = KnownIntrinsics.unsafeCast(lock.sync, Target_java_util_concurrent_locks_AbstractOwnableSynchronizer.class);

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
        Condition condition = getOrCreateCondition(obj, lock, true);
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
        Condition condition = getOrCreateCondition(obj, lock, false);
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

    /* Method is public so that white-box test cases can use it. */
    public ReentrantLock getOrCreateMonitor(Object obj, boolean createIfNotExisting) {
        DynamicHub hub = ObjectHeader.readDynamicHubFromObject(obj);
        int monitorOffset = hub.getMonitorOffset();

        if (monitorOffset != 0) {
            /* The common case: memory for the monitor reserved in the object. */
            ReentrantLock existingMonitor = KnownIntrinsics.convertUnknownValue(BarrieredAccess.readObject(obj, monitorOffset), ReentrantLock.class);
            if (existingMonitor != null || !createIfNotExisting) {
                return existingMonitor;
            }

            /* Atomically put a new lock in place instead of the null at the monitorOffset. */
            ReentrantLock newMonitor = new ReentrantLock();
            if (UnsafeAccess.UNSAFE.compareAndSwapObject(obj, monitorOffset, null, newMonitor)) {
                return newMonitor;
            }
            /* We lost the race, use the lock some other thread installed. */
            return KnownIntrinsics.convertUnknownValue(BarrieredAccess.readObject(obj, monitorOffset), ReentrantLock.class);

        } else {
            filterClass(obj);

            /* No memory reserved for the lock in the object, fall back to our secondary storage. */
            ReentrantLock existingMonitor = additionalMonitors.get(obj);
            if (existingMonitor != null || !createIfNotExisting) {
                return existingMonitor;
            }

            /* Atomically put a new lock into the secondary storage. */
            ReentrantLock newMonitor = new ReentrantLock();
            existingMonitor = additionalMonitors.putIfAbsent(obj, newMonitor);
            if (existingMonitor == null) {
                return newMonitor;
            }
            /* We lost the race, use the lock some other thread installed. */
            return existingMonitor;
        }
    }

    private Condition getOrCreateCondition(Object obj, ReentrantLock lock, boolean createIfNotExisting) {
        DynamicHub hub = ObjectHeader.readDynamicHubFromObject(obj);
        int conditionOffset = hub.getWaitNotifyOffset();

        if (conditionOffset != 0) {
            /* The common case: memory for the condition reserved in the object. */

            Condition existingCondition = KnownIntrinsics.convertUnknownValue(BarrieredAccess.readObject(obj, conditionOffset), Condition.class);
            if (existingCondition != null || !createIfNotExisting) {
                return existingCondition;
            }

            /*
             * Atomically put a new condition in place instead of the null at the conditionOffset.
             */
            Condition newCondition = lock.newCondition();
            if (UnsafeAccess.UNSAFE.compareAndSwapObject(obj, conditionOffset, null, newCondition)) {
                return newCondition;
            }
            /* We lost the race, use the condition some other thread installed. */
            return KnownIntrinsics.convertUnknownValue(BarrieredAccess.readObject(obj, conditionOffset), Condition.class);

        } else {
            filterClass(obj);

            /*
             * No memory reserved for the condition in the object, fall back to our secondary
             * storage.
             */
            Condition existingCondition = additionalConditions.get(obj);
            if (existingCondition != null || !createIfNotExisting) {
                return existingCondition;
            }

            /* Atomically put a new condition into the secondary storage. */
            Condition newCondition = lock.newCondition();
            existingCondition = additionalConditions.putIfAbsent(obj, newCondition);
            if (existingCondition == null) {
                return newCondition;
            }
            /* We lost the race, use the condition some other thread installed. */
            return existingCondition;
        }
    }

    private static void filterClass(Object obj) {
        if (obj instanceof Class) {
            throw VMError.unsupportedFeature(
                            "Manual synchronization, wait, and notify on java.lang.Class is not yet supported on Substrate VM. However, 'static synchronized' methods are supported.");
        }
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

@TargetClass(ReentrantLock.class)
final class Target_java_util_concurrent_locks_ReentrantLock {
    @Alias//
    Target_java_util_concurrent_locks_ReentrantLock_Sync sync;
}
