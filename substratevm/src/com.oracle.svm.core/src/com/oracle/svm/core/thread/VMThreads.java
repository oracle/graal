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
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.ForceFixedRegisterReads;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicWord;
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

    /**
     * Only use this mutex if it is absolutely necessary to operate on the linked list of
     * {@link IsolateThread}s. This mutex is especially dangerous because it is used by the
     * application, the GC, and the safepoint mechanism. To avoid potential deadlocks, all places
     * that acquire this mutex must do one of the following:
     *
     * <ol type="a">
     * <li>Acquire the mutex within a VM operation: this is safe because it fixes the order in which
     * the mutexes are acquired (VMOperation queue mutex first, {@link #THREAD_MUTEX} second). If
     * the VM operation causes a safepoint, then it is possible that the {@link #THREAD_MUTEX} was
     * already acquired for safepoint reasons.</li>
     * <li>Acquire the mutex outside of a VM operation but only execute uninterruptible code. This
     * is safe as the uninterruptible code cannot trigger a safepoint.</li>
     * <li>Acquire the mutex from a thread that previously called
     * {@link StatusSupport#setStatusIgnoreSafepoints()}.</li>
     * </ol>
     *
     * Deadlock example 1:
     * <ul>
     * <li>Thread A acquires the {@link #THREAD_MUTEX}.</li>
     * <li>Thread B queues a VM operation and therefore holds the corresponding VM operation queue
     * mutex.</li>
     * <li>Thread A allocates an object and the allocation wants to trigger a GC. So, a VM operation
     * needs to be queued, and thread A tries to acquire the VM operation queue mutex. Thread A is
     * blocked because thread B holds that mutex.</li>
     * <li>Thread B needs to initiate a safepoint before executing the VM operation. So, it tries to
     * acquire the {@link #THREAD_MUTEX} and is blocked because thread A holds that mutex.</li>
     * </ul>
     *
     * Deadlock example 2:
     * <ul>
     * <li>Thread A acquires the {@link #THREAD_MUTEX}.</li>
     * <li>Thread A allocates an object and the allocation wants to trigger a GC. So, a VM operation
     * is queued and thread A blocks until the VM operation is completed.</li>
     * <li>The dedicated VM operation thread needs to initiate a safepoint for the execution of the
     * VM operation. So, it tries to acquire {@link #THREAD_MUTEX} and is blocked because thread A
     * still holds that mutex.</li>
     * </ul>
     */
    protected static final VMMutex THREAD_MUTEX = new VMMutex();

    /**
     * A condition variable for waiting for and notifying on changes to the {@link IsolateThread}
     * list.
     */
    protected static final VMCondition THREAD_LIST_CONDITION = new VMCondition(THREAD_MUTEX);

    /** The first element in the linked list of {@link IsolateThread}s. */
    private static IsolateThread head;

    /** The next element in the linked list of {@link IsolateThread}s. */
    private static final FastThreadLocalWord<IsolateThread> nextTL = FastThreadLocalFactory.createWord();
    private static final FastThreadLocalWord<ComparableWord> OSThreadIdTL = FastThreadLocalFactory.createWord();
    public static final FastThreadLocalWord<Isolate> IsolateTL = FastThreadLocalFactory.createWord();

    private static final int STATE_UNINITIALIZED = 1;
    private static final int STATE_INITIALIZING = 2;
    private static final int STATE_INITIALIZED = 3;
    private static final int STATE_TEARING_DOWN = 4;
    private static final UninterruptibleUtils.AtomicInteger initializationState = new UninterruptibleUtils.AtomicInteger(STATE_UNINITIALIZED);

    @Uninterruptible(reason = "Called from uninterruptible code. Too early for safepoints.")
    public static boolean isInitialized() {
        return initializationState.get() >= STATE_INITIALIZED;
    }

    /** Is threading being torn down? */
    @Uninterruptible(reason = "Called from uninterruptible code during tear down.")
    public static boolean isTearingDown() {
        return initializationState.get() >= STATE_TEARING_DOWN;
    }

    /** Note that threading is being torn down. */
    protected static void setTearingDown() {
        initializationState.set(STATE_TEARING_DOWN);
    }

    /**
     * Make sure the runtime is initialized for threading.
     */
    @Uninterruptible(reason = "Called from uninterruptible code. Too early for safepoints.")
    public static boolean ensureInitialized() {
        boolean result = true;
        if (initializationState.compareAndSet(STATE_UNINITIALIZED, STATE_INITIALIZING)) {
            /*
             * We claimed the initialization lock, so we are now responsible for doing all the
             * initialization.
             */
            result = singleton().initializeOnce();

            initializationState.set(STATE_INITIALIZED);
        } else {
            /* Already initialized, or some other thread claimed the initialization lock. */
            while (initializationState.get() < STATE_INITIALIZED) {
                /* Busy wait until the other thread finishes the initialization. */
            }
        }
        return result;
    }

    /**
     * Invoked exactly once early during the startup of an isolate. Subclasses can perform
     * initialization of native OS resources.
     */
    @Uninterruptible(reason = "Called from uninterruptible code. Too early for safepoints.")
    protected abstract boolean initializeOnce();

    /**
     * Allocate native memory for a {@link IsolateThread}. The returned memory must be initialized
     * to 0.
     */
    @Uninterruptible(reason = "Thread state not set up.")
    public abstract IsolateThread allocateIsolateThread(int isolateThreadSize);

    /**
     * Free the native memorry allocated by {@link #allocateIsolateThread}.
     */
    @Uninterruptible(reason = "Thread state not set up.")
    public abstract void freeIsolateThread(IsolateThread thread);

    /**
     * Report a fatal error to the user and exit. This method must not return.
     */
    @Uninterruptible(reason = "Unknown thread state.")
    public abstract void failFatally(int code, CCharPointer message);

    /**
     * Iteration of all {@link IsolateThread}s that are currently running. VMThreads.THREAD_MUTEX
     * should be held when iterating the list.
     *
     * Use the following pattern to iterate all running threads. It is allocation free and can
     * therefore be used during GC:
     *
     * <pre>
     * for (VMThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
     * </pre>
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static IsolateThread firstThread() {
        return head;
    }

    /**
     * Iteration of all {@link IsolateThread}s that are currently running. See
     * {@link #firstThread()} for details.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static IsolateThread nextThread(IsolateThread cur) {
        return nextTL.get(cur);
    }

    /**
     * Creates a new {@link IsolateThread} and adds it to the list of running threads. This method
     * must be the first method called in every thread.
     */
    @Uninterruptible(reason = "Reason: Thread register not yet set up.")
    public void attachThread(IsolateThread thread) {
        assert StatusSupport.isStatusCreated(thread) : "Status should be initialized on creation.";
        OSThreadIdTL.set(thread, getCurrentOSThreadId());

        /* Set initial values for safepointRequested before making the thread visible. */
        assert !ThreadingSupportImpl.isRecurringCallbackRegistered(thread);
        Safepoint.setSafepointRequested(thread, Safepoint.SafepointRequestValues.RESET);
        Safepoint.setSafepointRequestedValueBeforeSafepoint(thread, Safepoint.SafepointRequestValues.RESET);

        /*
         * Manipulating the VMThread list requires the lock, but the IsolateThread is not set up
         * yet, so the locking must be without transitions. Not using try-with-resources to avoid
         * implicitly calling addSuppressed(), which is not uninterruptible.
         */
        VMThreads.THREAD_MUTEX.lockNoTransitionUnspecifiedOwner();
        try {
            nextTL.set(thread, head);
            head = thread;
            StatusSupport.setStatusNative(thread);
            VMThreads.THREAD_LIST_CONDITION.broadcast();
        } finally {
            VMThreads.THREAD_MUTEX.unlockNoTransitionUnspecifiedOwner();
        }
    }

    /**
     * Remove a {@link IsolateThread} from the list of VMThreads. This method must be the last
     * method called in every thread.
     */
    @Uninterruptible(reason = "Manipulates the threads list; broadcasts on changes.")
    public static void detachThread(IsolateThread current) {
        assert current.equal(CurrentIsolate.getCurrentThread()) : "Cannot detach different thread with this method";

        /*
         * Make me immune to safepoints (the safepoint mechanism ignores me). We are calling
         * functions that are not marked as @Uninterruptible during the detach process. We hold the
         * THREAD_MUTEX, so we know that we are not going to be interrupted by a safepoint. But a
         * safepoint can already be requested, or our safepoint counter can reach 0 - so it is still
         * possible that we enter the safepoint slow path.
         */
        StatusSupport.setStatusIgnoreSafepoints();

        // try-finally because try-with-resources can call interruptible code
        THREAD_MUTEX.lockNoTransition();
        try {
            detachThreadInSafeContext(current);
        } finally {
            THREAD_MUTEX.unlock();
        }
    }

    @Uninterruptible(reason = "Manipulates the threads list; broadcasts on changes.")
    private static void detachThreadInSafeContext(IsolateThread thread) {
        detachJavaThread(thread);

        // Run down the current list and remove the given VMThread.
        IsolateThread previous = WordFactory.nullPointer();
        IsolateThread current = head;
        while (current.isNonNull()) {
            IsolateThread next = nextTL.get(current);
            if (current == thread) {
                // Splice the current element out of the list.
                if (previous.isNull()) {
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
        THREAD_LIST_CONDITION.broadcast();
        // At this point, it is guaranteed that all other threads were detached.

        singleton().freeIsolateThread(thread);
    }

    @Uninterruptible(reason = "For calling interruptible code from uninterruptible code.", calleeMustBe = false)
    private static void detachJavaThread(IsolateThread thread) {
        JavaThreads.detachThread(thread);
    }

    public static void detachThreads(IsolateThread[] threads) {
        JavaVMOperation.enqueueBlockingSafepoint("detachThreads", () -> {
            for (IsolateThread thread : threads) {
                assert !thread.equal(CurrentIsolate.getCurrentThread()) : "Cannot detach current thread with this method";
                detachThreadInSafeContext(thread);
            }
        });
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract ComparableWord getCurrentOSThreadId();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public IsolateThread findIsolateThreadforCurrentOSThread() {
        ComparableWord id = getCurrentOSThreadId();
        /*
         * Accessing the VMThread list requires the lock, but locking must be without transitions
         * because the IsolateThread is not set up yet.
         */
        VMThreads.THREAD_MUTEX.lockNoTransitionUnspecifiedOwner();
        try {
            IsolateThread thread;
            for (thread = firstThread(); thread.isNonNull() && OSThreadIdTL.get(thread).notEqual(id); thread = nextThread(thread)) {
            }
            return thread;
        } finally {
            VMThreads.THREAD_MUTEX.unlockNoTransitionUnspecifiedOwner();
        }
    }

    public static void guaranteeOwnsThreadMutex(String message) {
        THREAD_MUTEX.guaranteeIsOwner(message);
    }

    /*
     * Access to platform-specific implementations.
     */

    /** A thread-local enum giving the thread status of a VMThread. And supporting methods. */
    public static class StatusSupport {

        /** The status of a {@link IsolateThread}. */
        public static final FastThreadLocalInt statusTL = FastThreadLocalFactory.createInt();

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
        public static final int STATUS_CREATED = 0;
        /** The thread is running in Java code. */
        public static final int STATUS_IN_JAVA = STATUS_CREATED + 1;
        /** The thread has been requested to stop at a safepoint. */
        public static final int STATUS_IN_SAFEPOINT = STATUS_IN_JAVA + 1;
        /** The thread is running in native code. */
        public static final int STATUS_IN_NATIVE = STATUS_IN_SAFEPOINT + 1;

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

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int getStatusVolatile(IsolateThread vmThread) {
            return statusTL.getVolatile(vmThread);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isStatusCreated(IsolateThread vmThread) {
            return (statusTL.getVolatile(vmThread) == STATUS_CREATED);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isStatusSafepoint(IsolateThread vmThread) {
            return (statusTL.getVolatile(vmThread) == STATUS_IN_SAFEPOINT);
        }

        /** There is no unguarded change to safepoint. */
        public static boolean compareAndSetNativeToSafepoint(IsolateThread vmThread) {
            return statusTL.compareAndSet(vmThread, STATUS_IN_NATIVE, STATUS_IN_SAFEPOINT);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isStatusJava() {
            return (statusTL.getVolatile() == STATUS_IN_JAVA);
        }

        /** An <em>unguarded</em> transition to Java. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void setStatusJavaUnguarded(IsolateThread vmThread) {
            statusTL.setVolatile(vmThread, STATUS_IN_JAVA);
        }

        /** A guarded transition from native to Java. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @ForceFixedRegisterReads
        public static boolean compareAndSetNativeToJava() {
            return statusTL.compareAndSet(STATUS_IN_NATIVE, STATUS_IN_JAVA);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isStatusIgnoreSafepoints(IsolateThread vmThread) {
            return safepointsDisabledTL.getVolatile(vmThread) == 1;
        }

        /**
         * Make myself immune to safepoints. Set the thread status to ensure that the safepoint
         * mechanism ignores me. It is not necessary to clear a pending safepoint request (i.e., to
         * reset the safepoint counter) because the safepoint slow path is going to do that in case.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void setStatusIgnoreSafepoints() {
            safepointsDisabledTL.setVolatile(1);
        }
    }
}
