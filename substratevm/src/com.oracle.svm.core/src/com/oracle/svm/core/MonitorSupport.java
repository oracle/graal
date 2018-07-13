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

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

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
 * Because so few objects are receivers of {@link #wait()} and {@link #notify()} calls[citation
 * needed], condition variables for those objects are kept in {@link #additionalConditions}.
 */
public class MonitorSupport {

    /**
     * Secondary storage for monitor slots.
     *
     * Synchronized to prevent concurrent access and modification.
     */
    private final Map<Object, ReentrantLock> additionalMonitors = new WeakHashMap<>();
    private final ReentrantLock additionalMonitorsLock = new ReentrantLock();

    /**
     * Secondary storage for condition variable slots.
     *
     * Synchronized to prevent concurrent access and modification.
     */
    private final Map<Object, Condition> additionalConditions = new WeakHashMap<>();
    private final ReentrantLock additionalConditionsLock = new ReentrantLock();

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

        ReentrantLock lockObject = null;
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
            throw shouldNotReachHere("monitorEnter", obj, lockObject, ex);
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

        ReentrantLock lockObject = null;
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
            throw shouldNotReachHere("monitorExit", obj, lockObject, ex);
        }
    }

    private static RuntimeException shouldNotReachHere(String label, Object obj, ReentrantLock lockObject, Throwable ex) {
        StringBuilder msg = new StringBuilder();
        msg.append("Unexpected exception in MonitorSupport.").append(label);

        if (obj != null) {
            msg.append("  object: ");
            appendObject(msg, obj);
        }
        if (lockObject != null) {
            msg.append("  lock: ");
            appendObject(msg, lockObject);

            Target_java_util_concurrent_locks_ReentrantLock lockObjectTarget = KnownIntrinsics.unsafeCast(lockObject, Target_java_util_concurrent_locks_ReentrantLock.class);
            Target_java_util_concurrent_locks_AbstractOwnableSynchronizer sync = KnownIntrinsics.unsafeCast(lockObjectTarget.sync, Target_java_util_concurrent_locks_AbstractOwnableSynchronizer.class);

            if (sync != null) {
                msg.append("  sync: ");
                appendObject(msg, sync);

                Thread thread = sync.getExclusiveOwnerThread();
                if (thread == null) {
                    msg.append("  no exclusiveOwnerThread");
                } else {
                    msg.append("  exclusiveOwnerThread: ");
                    appendObject(msg, thread);
                }
            }
        }
        msg.append("  raw exception: ");
        throw VMError.shouldNotReachHere(msg.toString(), ex);
    }

    private static void appendObject(StringBuilder msg, Object obj) {
        msg.append(obj.getClass().getName()).append("@").append(Long.toHexString(Word.objectToUntrackedPointer(obj).rawValue()));
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
    private ReentrantLock getOrCreateMonitor(Object obj, boolean createIfNotExisting) {
        final DynamicHub hub = ObjectHeader.readDynamicHubFromObject(obj);
        final int monitorOffset = hub.getMonitorOffset();
        if (monitorOffset != 0) {
            /* The common case: memory for the monitor reserved in the object. */
            final ReentrantLock existingMonitor = KnownIntrinsics.convertUnknownValue(BarrieredAccess.readObject(obj, monitorOffset), ReentrantLock.class);
            if (existingMonitor != null || !createIfNotExisting) {
                return existingMonitor;
            }
            /* Atomically put a new lock in place of the null at the monitorOffset. */
            final ReentrantLock newMonitor = new ReentrantLock();
            if (UnsafeAccess.UNSAFE.compareAndSwapObject(obj, monitorOffset, null, newMonitor)) {
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
            additionalMonitorsLock.lock();
            try {
                final ReentrantLock existingEntry = additionalMonitors.get(obj);
                if (existingEntry != null || !createIfNotExisting) {
                    return existingEntry;
                }
                final ReentrantLock newEntry = new ReentrantLock();
                final ReentrantLock previousEntry = additionalMonitors.put(obj, newEntry);
                VMError.guarantee(previousEntry == null, "MonitorSupport.getOrCreateMonitor: Replaced monitor");
                return newEntry;
            } finally {
                additionalMonitorsLock.unlock();
            }
        }
    }

    public ReentrantLock getMonitorForTesting(Object obj) {
        return getOrCreateMonitor(obj, false);
    }

    private Condition getOrCreateCondition(Object obj, ReentrantLock lock, boolean createIfNotExisting) {
        /* No memory reserved for a condition in the object, use secondary storage. */
        /*
         * Lock the condition map and maybe add a condition for this object. This serialization
         * might be a scalability problem.
         */
        additionalConditionsLock.lock();
        try {
            final Condition existingEntry = additionalConditions.get(obj);
            if (existingEntry != null || !createIfNotExisting) {
                return existingEntry;
            }
            final Condition newEntry = lock.newCondition();
            final Condition previousEntry = additionalConditions.put(obj, newEntry);
            VMError.guarantee(previousEntry == null, "MonitorSupport.getOrCreateCondition: Replaced condition");
            return newEntry;
        } finally {
            additionalConditionsLock.unlock();
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
