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

import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.compiler.word.BarrieredAccess;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

/**
 * A collection of foreign calls to implement the slow paths of monitorEnter and monitorExit.
 */
public class MonitorUtils {

    @SubstrateForeignCallTarget
    public static void monitorEnter(Object obj) {
        try {
            final Log trace = Log.noopLog().string("[MonitorUtils.monitorEnter:").string("  obj: ").object(obj);
            final DynamicHub hub = ObjectHeader.readDynamicHubFromObject(obj);
            final int monitorOffset = hub.getMonitorOffset();
            if (monitorOffset == 0) {
                Log.log().string("monitorEnter called on an object without a monitor field:").string("  object: ").object(obj).newline();
                reportAndExit("monitorEnter called on an object without a monitor field.");
            }
            trace.string("  monitorOffset: ").signed(monitorOffset);
            final Object monitorOffsetField = BarrieredAccess.readObject(obj, monitorOffset);
            ReentrantLock lockObject;
            if (monitorOffsetField == null) {
                trace.string(" lock created");
                // Make up a new lock object.
                lockObject = new ReentrantLock();
                // CompareAndSwap it in place of the null at the monitorOffset.
                if (!UnsafeAccess.UNSAFE.compareAndSwapObject(obj, monitorOffset, null, lockObject)) {
                    // If I lose the race, use the lock some other thread installed.
                    final Object readObject = BarrieredAccess.readObject(obj, monitorOffset);
                    lockObject = KnownIntrinsics.unsafeCast(readObject, ReentrantLock.class);
                }
            } else {
                final Object readObject = BarrieredAccess.readObject(obj, monitorOffset);
                lockObject = KnownIntrinsics.unsafeCast(readObject, ReentrantLock.class);
            }
            lockObject.lock();
            trace.string("]").newline();

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

    @SubstrateForeignCallTarget
    public static void monitorExit(Object obj) {
        try {
            final Log trace = Log.noopLog().string("[MonitorUtils.monitorExit:").string("  obj: ").object(obj);
            final DynamicHub hub = ObjectHeader.readDynamicHubFromObject(obj);
            final int monitorOffset = hub.getMonitorOffset();
            if (monitorOffset == 0) {
                Log.log().string("monitorExit called on an object without a monitor field:  object: ").object(obj).newline();
                reportAndExit("monitorExit called on an object without a monitor field.");
            }
            final Object monitorOffsetField = BarrieredAccess.readObject(obj, monitorOffset);
            if (monitorOffsetField == null) {
                Log.log().string("monitorExit called on an object with a null field:  object: ").object(obj).newline();
                reportAndExit("monitorExit called on an object with a null monitor field.");
            } else {
                trace.string("  monitorOffset: ").unsigned(monitorOffset);
                final Object readObject = BarrieredAccess.readObject(obj, monitorOffset);
                final ReentrantLock lockObject = KnownIntrinsics.unsafeCast(readObject, ReentrantLock.class);
                lockObject.unlock();
                trace.string("]").newline();
            }

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
    public static void setExclusiveOwnerThread(Object obj, Thread thread) {
        VMOperation.guaranteeInProgress("patching a lock while not being at a safepoint is too dangerous");

        final Log trace = Log.noopLog().string("[MonitorUtils.setExclusiveOwner:").string("  obj: ").object(obj);
        final DynamicHub hub = ObjectHeader.readDynamicHubFromObject(obj);
        final int monitorOffset = hub.getMonitorOffset();
        if (monitorOffset == 0) {
            Log.log().string("setExclusiveOwnerThread called on an object without a monitor field:").string("  object: ").object(obj).newline();
            reportAndExit("setExclusiveOwnerThread called on an object without a monitor field.");
        }
        trace.string("  monitorOffset: ").signed(monitorOffset);
        Target_java_util_concurrent_locks_ReentrantLock lockObject;
        final Object readObject = BarrieredAccess.readObject(obj, monitorOffset);
        lockObject = KnownIntrinsics.unsafeCast(readObject, Target_java_util_concurrent_locks_ReentrantLock.class);
        Target_java_util_concurrent_locks_AbstractOwnableSynchronizer sync = KnownIntrinsics.unsafeCast(lockObject.sync, Target_java_util_concurrent_locks_AbstractOwnableSynchronizer.class);
        VMError.guarantee(sync.getExclusiveOwnerThread() != null, "Cannot patch the exclusiveOwnerThread of an object that is not locked");
        sync.setExclusiveOwnerThread(thread);
        trace.string("]").newline();
    }

    private static void reportAndExit(String msg) {
        throw VMError.shouldNotReachHere(msg);
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
