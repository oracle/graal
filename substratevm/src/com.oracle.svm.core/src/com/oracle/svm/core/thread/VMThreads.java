/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
    public static void attachThread(IsolateThread vmThread) {
        assert StatusSupport.isStatusCreated(vmThread) : "Status should be initialized on creation.";
        // Manipulating the VMThread list requires the lock for
        // changing the status and for notification.
        // But the VMThread for the current thread is not set up yet,
        // so the locking must be without transitions.
        attachThreadUnderLock(vmThread);
    }

    /**
     * The body of {@link #attachThread} that is executed under the VMThreads mutex. With that mutex
     * held, callees do not need to be annotated with {@link Uninterruptible}.
     * <p>
     * Using try-finally rather than a try-with-resources to avoid implicitly calling
     * {@link Throwable#addSuppressed(Throwable)}, which I can not annotate as uninterruptible.
     */
    @Uninterruptible(reason = "Reason: Thread register not yet set up.")
    public static void attachThreadUnderLock(IsolateThread vmThread) {
        VMMutex lock = VMThreads.THREAD_MUTEX;
        try {
            lock.lockNoTransition();
            VMThreads.THREAD_MUTEX.guaranteeIsLocked("Must hold the VMThreads lock.");
            nextTL.set(vmThread, head);
            head = vmThread;
            VMThreads.THREAD_LIST_CONDITION.broadcast();
        } finally {
            lock.unlock();
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
        /** The thread has exited its run method. */
        private static final int STATUS_IGNORE_SAFEPOINTS = STATUS_IN_NATIVE + 1;
        /** The thread has exited its run method. */
        private static final int STATUS_EXITED = STATUS_IGNORE_SAFEPOINTS + 1;

        private static String statusToString(int status) {
            switch (status) {
                case STATUS_CREATED:
                    return "STATUS_CREATED";
                case STATUS_IN_JAVA:
                    return "STATUS_IN_JAVA";
                case STATUS_IN_SAFEPOINT:
                    return "STATUS_IN_SAFEPOINT";
                case STATUS_IN_NATIVE:
                    return "STATUS_IN_NATIVE";
                case STATUS_EXITED:
                    return "STATUS_EXITED";
                case STATUS_IGNORE_SAFEPOINTS:
                    return "STATUS_IGNORE_SAFEPOINTS";
                default:
                    return "STATUS error";
            }
        }

        /* Access methods to treat VMThreads.statusTL as a volatile int. */

        /** For debugging. */
        public static String getStatusString(IsolateThread vmThread) {
            return statusToString(statusTL.getVolatile(vmThread));
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
            return (statusTL.getVolatile(vmThread) == STATUS_IGNORE_SAFEPOINTS);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static boolean isStatusExited(IsolateThread vmThread) {
            return (statusTL.getVolatile(vmThread) == STATUS_EXITED);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static void setStatusExited() {
            statusTL.setVolatile(STATUS_EXITED);
        }

        /**
         * Make myself immune to safepoints. Set the thread status to ensure that the safepoint
         * mechanism ignores me, and clear any pending safepoint requests, since I will not honor
         * them. Use with care. It causes code to run slower in case of safepoint-requests (due to
         * subsequent slow-path code execution).
         */
        @Uninterruptible(reason = "Called from uninterruptible code.")
        public static void setStatusIgnoreSafepoints() {
            statusTL.setVolatile(STATUS_IGNORE_SAFEPOINTS);
            Safepoint.setSafepointRequested(Safepoint.SafepointRequestValues.RESET);
        }
    }
}
