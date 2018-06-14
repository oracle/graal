/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;

/**
 * Utility methods for the manipulation and iteration of {@link IsolateThread}s.
 */
public abstract class VMThreads {

    @Fold
    public static VMThreads singleton() {
        return ImageSingletons.lookup(VMThreads.class);
    }

    /** A mutex for operations on {@link IsolateThread}s. */
    public static final VMMutex THREAD_MUTEX = new VMMutex();

    /**
     * A condition variable for waiting for and notifying on changes to the {@link IsolateThread}
     * list.
     */
    public static final VMCondition THREAD_LIST_CONDITION = new VMCondition(THREAD_MUTEX);

    /** The first element in the linked list of {@link IsolateThread}s. */
    private static IsolateThread head;

    /** The next element in the linked list of {@link IsolateThread}s. */
    private static final FastThreadLocalWord<IsolateThread> nextTL = FastThreadLocalFactory.createWord();

    /** The value of a {@code null} {@link IsolateThread}. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    // TODO: This could be @Fold instead of just @Uninterruptible.
    public static IsolateThread nullThread() {
        return WordFactory.nullPointer();
    }

    /** A predicate for the {@code null} {@link IsolateThread}. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static boolean isNullThread(IsolateThread vmThread) {
        return vmThread.isNull();
    }

    /** A predicate for the {@code non-null} {@link IsolateThread}. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static boolean isNonNullThread(IsolateThread vmThread) {
        return vmThread.isNonNull();
    }

    /**
     * Iteration of all {@link IsolateThread}s that are currently running. VMThreads.THREAD_MUTEX
     * should be held when iterating the list.
     *
     * Use the following pattern to iterate all running threads. It is allocation free and can
     * therefore be used during GC:
     *
     * <pre>
     * for (VMThread thread = VMThreads.firstThread(); VMThreads.isNonNullThread(thread); thread = VMThreads.nextThread(thread)) {
     * </pre>
     */
    public static IsolateThread firstThread() {
        return head;
    }

    /**
     * Iteration of all {@link IsolateThread}s that are currently running. See
     * {@link #firstThread()} for details.
     */
    public static IsolateThread nextThread(IsolateThread cur) {
        return nextTL.get(cur);
    }

    /** Iteration of all {@link IsolateThread}s on a stolen list. */
    public static IsolateThread nextThreadFromList(IsolateThread cur) {
        return nextTL.get(cur);
    }

    /**
     * Creates a new {@link IsolateThread} and adds it to the list of running threads. This method
     * must be the first method called in every thread.
     */
    @Uninterruptible(reason = "Reason: Thread register not yet set up.")
    public static void attachThread(IsolateThread thread) {
        assert StatusSupport.isStatusCreated(thread) : "Status should be initialized on creation.";
        // Manipulating the VMThread list requires the lock, but the IsolateThread is not set up
        // yet, so the locking must be without transitions. Not using try-with-resources to avoid
        // implicitly calling addSuppressed(), which is not uninterruptible.
        VMThreads.THREAD_MUTEX.lockNoTransition();
        try {
            VMThreads.THREAD_MUTEX.guaranteeIsLocked("Must hold the VMThreads lock.");
            nextTL.set(thread, head);
            head = thread;
            StatusSupport.setStatusNative(thread);
            VMThreads.THREAD_LIST_CONDITION.broadcast();
        } finally {
            VMThreads.THREAD_MUTEX.unlock();
        }
    }

    /**
     * Remove a {@link IsolateThread} from the list of VMThreads. This method must be the last
     * method called in every thread.
     */
    @Uninterruptible(reason = "Manipulates the threads list; broadcasts on changes.")
    public static void detachThread(IsolateThread vmThread) {
        // Manipulating the VMThread list requires the lock for
        // changing the status and for notification.
        VMThreads.THREAD_MUTEX.guaranteeIsLocked("Must hold the VMThreads mutex.");
        // Run down the current list and remove the given VMThread.
        IsolateThread previous = nullThread();
        IsolateThread current = head;
        while (isNonNullThread(current)) {
            IsolateThread next = nextTL.get(current);
            if (current == vmThread) {
                // Splice the current element out of the list.
                if (isNullThread(previous)) {
                    head = next;
                } else {
                    nextTL.set(previous, next);
                }
                break;
            } else {
                previous = current;
                current = next;
            }
        }
        // Signal that the VMThreads list has changed.
        VMThreads.THREAD_LIST_CONDITION.broadcast();
    }

    /*
     * Access to platform-specific implementations.
     */

    protected abstract void setTearingDown();

    /** A thread-local enum giving the thread status of a VMThread. And supporting methods. */
    public static class StatusSupport {

        /** The status of a {@link IsolateThread}. */
        private static final FastThreadLocalInt statusTL = FastThreadLocalFactory.createInt();

        /**
         * Boolean flag whether safepoints are disabled. This is a separate thread local in addition
         * to the {@link #statusTL} because we need the disabled flag to be "sticky": once
         * safepoints are disabled, they must never be enabled again. Either the thread is getting
         * detached, or a fatal error occurred and we are printing diagnostics before killing the
         * VM.
         */
        private static final FastThreadLocalInt safepointsDisabledTL = FastThreadLocalFactory.createInt();

        /**
         * {@link IsolateThread} memory has been allocated for the thread, but the thread is not on
         * the VMThreads list yet.
         */
        private static final int STATUS_CREATED = 0;
        /** The thread is running in Java code. */
        private static final int STATUS_IN_JAVA = STATUS_CREATED + 1;
        /** The thread has been requested to stop at a safepoint. */
        private static final int STATUS_IN_SAFEPOINT = STATUS_IN_JAVA + 1;
        /** The thread is running in native code. */
        private static final int STATUS_IN_NATIVE = STATUS_IN_SAFEPOINT + 1;

        private static String statusToString(int status, boolean safepointsDisabled) {
            switch (status) {
                case STATUS_CREATED:
                    return safepointsDisabled ? "STATUS_CREATED (safepoints disabled)" : "STATUS_CREATED";
                case STATUS_IN_JAVA:
                    return safepointsDisabled ? "STATUS_IN_JAVA (safepoints disabled)" : "STATUS_IN_JAVA";
                case STATUS_IN_SAFEPOINT:
                    return safepointsDisabled ? "STATUS_IN_SAFEPOINT (safepoints disabled)" : "STATUS_IN_SAFEPOINT";
                case STATUS_IN_NATIVE:
                    return safepointsDisabled ? "STATUS_IN_NATIVE (safepoints disabled)" : "STATUS_IN_NATIVE";
                default:
                    return "STATUS error";
            }
        }

        /* Access methods to treat VMThreads.statusTL as a volatile int. */

        /** For debugging. */
        public static String getStatusString(IsolateThread vmThread) {
            return statusToString(statusTL.getVolatile(vmThread), isStatusIgnoreSafepoints(vmThread));
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static boolean isStatusCreated(IsolateThread vmThread) {
            return (statusTL.getVolatile(vmThread) == STATUS_CREATED);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static boolean isStatusNative(IsolateThread vmThread) {
            return (statusTL.getVolatile(vmThread) == STATUS_IN_NATIVE);
        }

        public static void setStatusNative() {
            statusTL.set(STATUS_IN_NATIVE);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void setStatusNative(IsolateThread vmThread) {
            statusTL.setVolatile(vmThread, STATUS_IN_NATIVE);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static boolean isStatusSafepoint(IsolateThread vmThread) {
            return (statusTL.getVolatile(vmThread) == STATUS_IN_SAFEPOINT);
        }

        /** There is no unguarded change to safepoint. */
        public static boolean compareAndSetNativeToSafepoint(IsolateThread vmThread) {
            return statusTL.compareAndSet(vmThread, STATUS_IN_NATIVE, STATUS_IN_SAFEPOINT);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static boolean isStatusJava() {
            return (statusTL.getVolatile() == STATUS_IN_JAVA);
        }

        /** An <em>unguarded</em> transition to Java. */
        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static void setStatusJavaUnguarded(IsolateThread vmThread) {
            statusTL.setVolatile(vmThread, STATUS_IN_JAVA);
        }

        /** A guarded transition from native to Java. */
        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static boolean compareAndSetNativeToJava() {
            return statusTL.compareAndSet(STATUS_IN_NATIVE, STATUS_IN_JAVA);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static boolean isStatusIgnoreSafepoints(IsolateThread vmThread) {
            return safepointsDisabledTL.getVolatile(vmThread) == 1;
        }

        /**
         * Make myself immune to safepoints. Set the thread status to ensure that the safepoint
         * mechanism ignores me. It is not necessary to clear a pending safepoint request (i.e., to
         * reset the safepoint counter) because the safepoint slow path is going to do that in case.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static void setStatusIgnoreSafepoints() {
            safepointsDisabledTL.setVolatile(1);
        }
    }
}
