/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.SubstrateOptions.MultiThreaded;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;

import java.lang.Thread.UncaughtExceptionHandler;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceHandler;
import com.oracle.svm.core.heap.ReferenceHandlerThread;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.jdk.Target_jdk_internal_misc_VM;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

import sun.misc.Unsafe;

public abstract class JavaThreads {
    @Fold
    public static JavaThreads singleton() {
        return ImageSingletons.lookup(JavaThreads.class);
    }

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    /** The platform {@link java.lang.Thread} for the {@link IsolateThread}. */
    static final FastThreadLocalObject<Thread> platformThread = FastThreadLocalFactory.createObject(Thread.class, "JavaThreads.platformThread").setMaxOffset(FastThreadLocal.BYTE_OFFSET);

    /**
     * A thread-local helper object for locking. Use only if each {@link Thread} corresponds to an
     * {@link IsolateThread}, otherwise use {@link Target_java_lang_Thread#lockHelper}.
     */
    private static final FastThreadLocalObject<Object> lockHelper = FastThreadLocalFactory.createObject(Object.class, "JavaThreads.lockHelper").setMaxOffset(FastThreadLocal.BYTE_OFFSET);

    /**
     * The number of running non-daemon threads. The initial value accounts for the main thread,
     * which is implicitly running when the isolate is created.
     */
    private static final UninterruptibleUtils.AtomicInteger nonDaemonThreads = new UninterruptibleUtils.AtomicInteger(1);

    /**
     * Tracks the number of threads that have been started, but are not yet executing Java code. For
     * a small window of time, threads are still accounted for in this count while they are already
     * attached. We use this counter to avoid missing threads during tear-down.
     */
    private final AtomicInteger unattachedStartedThreads = new AtomicInteger(0);

    /** For Thread.nextThreadID(). */
    final AtomicLong threadSeqNumber = new AtomicLong();
    /** For Thread.nextThreadNum(). */
    final AtomicInteger threadInitNumber = new AtomicInteger();

    /** The default group for new Threads that are attached without an explicit group. */
    final ThreadGroup mainGroup;
    /** The root group for all threads. */
    public final ThreadGroup systemGroup;
    /**
     * The preallocated thread object for the main thread, to avoid expensive allocations and
     * ThreadGroup operations immediately at startup.
     *
     * We cannot put the main thread in a "running" state during image generation, but we still want
     * it in "running" state at run time without running state transition code. Therefore, we use
     * field value recomputations to put the thread in "running" state as part of the image heap
     * writing.
     */
    final Thread mainThread;
    final Thread[] mainGroupThreadsArray;

    /* Accessor functions for private fields of java.lang.Thread that we alias or inject. */

    @Platforms(Platform.HOSTED_ONLY.class)
    protected JavaThreads() {
        /*
         * By using the current thread group as the SVM root group we are preserving runtime
         * environment of a generated image, which is necessary as the current thread group is
         * available to static initializers and we are allowing ThreadGroups and unstarted Threads
         * in the image heap.
         */
        mainGroup = Thread.currentThread().getThreadGroup();
        VMError.guarantee(mainGroup.getName().equals("main"), "Wrong ThreadGroup for main");
        systemGroup = mainGroup.getParent();
        VMError.guarantee(systemGroup.getParent() == null && systemGroup.getName().equals("system"), "Wrong ThreadGroup for system");

        /*
         * The mainThread's contextClassLoader is set to the current thread's contextClassLoader
         * which is a NativeImageClassLoader. The ClassLoaderFeature object replacer will unwrap the
         * original AppClassLoader from the NativeImageClassLoader.
         */
        mainThread = new Thread(mainGroup, "main");
        mainThread.setDaemon(false);

        /* The ThreadGroup uses 4 as the initial array length. */
        mainGroupThreadsArray = new Thread[4];
        mainGroupThreadsArray[0] = mainThread;
    }

    @SuppressFBWarnings(value = "BC", justification = "Cast for @TargetClass")
    static Thread fromTarget(Target_java_lang_Thread thread) {
        return Thread.class.cast(thread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @SuppressFBWarnings(value = "BC", justification = "Cast for @TargetClass")
    static Target_java_lang_Thread toTarget(Thread thread) {
        return Target_java_lang_Thread.class.cast(thread);
    }

    public static int getThreadStatus(Thread thread) {
        return LoomSupport.CompatibilityUtil.getThreadStatus(toTarget(thread));
    }

    public static void setThreadStatus(Thread thread, int threadStatus) {
        LoomSupport.CompatibilityUtil.setThreadStatus(toTarget(thread), threadStatus);
    }

    @Uninterruptible(reason = "Thread locks/holds the THREAD_MUTEX.")
    public static long getThreadAllocatedBytes(long javaThreadId) {
        // Accessing the value for the current thread is fast.
        Thread curThread = JavaThreads.platformThread.get();
        if (curThread != null && curThread.getId() == javaThreadId) {
            return Heap.getHeap().getThreadAllocatedMemory(CurrentIsolate.getCurrentThread());
        }

        // If the value of another thread is accessed, then we need to do a slow lookup.
        VMThreads.lockThreadMutexInNativeCode();
        try {
            IsolateThread isolateThread = VMThreads.firstThread();
            while (isolateThread.isNonNull()) {
                Thread javaThread = JavaThreads.platformThread.get(isolateThread);
                if (javaThread != null && javaThread.getId() == javaThreadId) {
                    return Heap.getHeap().getThreadAllocatedMemory(isolateThread);
                }
                isolateThread = VMThreads.nextThread(isolateThread);
            }
            return -1;
        } finally {
            VMThreads.THREAD_MUTEX.unlock();
        }
    }

    @Uninterruptible(reason = "Thread locks/holds the THREAD_MUTEX.")
    public static void getThreadAllocatedBytes(long[] javaThreadIds, long[] result) {
        VMThreads.lockThreadMutexInNativeCode();
        try {
            IsolateThread isolateThread = VMThreads.firstThread();
            while (isolateThread.isNonNull()) {
                Thread javaThread = JavaThreads.platformThread.get(isolateThread);
                if (javaThread != null) {
                    for (int i = 0; i < javaThreadIds.length; i++) {
                        if (javaThread.getId() == javaThreadIds[i]) {
                            result[i] = Heap.getHeap().getThreadAllocatedMemory(isolateThread);
                            break;
                        }
                    }
                }
                isolateThread = VMThreads.nextThread(isolateThread);
            }
        } finally {
            VMThreads.THREAD_MUTEX.unlock();
        }
    }

    /** Safe method to get a thread's internal state since {@link Thread#getState} is not final. */
    static Thread.State getThreadState(Thread thread) {
        return Target_jdk_internal_misc_VM.toThreadState(getThreadStatus(thread));
    }

    /**
     * Safe method to check whether a thread has been interrupted.
     *
     * Use instead of {@link Thread#isInterrupted()}, which is not {@code final} and can be
     * overridden with code that does locking or performs other actions that are unsafe especially
     * in VM-internal contexts.
     */
    static boolean isInterrupted(Thread thread) {
        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            return toTarget(thread).interruptedJDK17OrLater;
        }
        return toTarget(thread).interruptedJDK11OrEarlier;
    }

    static boolean getAndClearInterrupt(Thread thread) {
        if (supportsVirtual() && isVirtual(thread)) {
            return VirtualThreads.get().getAndClearInterrupt(thread);
        }
        return platformGetAndClearInterrupt(thread);
    }

    static boolean platformGetAndClearInterrupt(Thread thread) {
        assert !isVirtual(thread);
        /*
         * As we don't use a lock, it is possible to observe any kinds of races with other threads
         * that try to set interrupted to true. However, those races don't cause any correctness
         * issues as we only reset interrupted to false if we observed that it was true earlier.
         * There also can't be any problematic races with other calls to isInterrupted as
         * clearInterrupted may only be true if this method is being executed by the current thread.
         */
        return getAndWriteInterruptedFlag(thread, false);
    }

    static void platformSetInterrupt(Thread thread) {
        assert !isVirtual(thread);
        boolean oldValue = getAndWriteInterruptedFlag(thread, true);
        if (!oldValue) {
            toTarget(thread).interrupt0();
        }
    }

    static boolean getAndWriteInterruptedFlag(Thread thread, boolean newValue) {
        Target_java_lang_Thread tjlt = toTarget(thread);
        boolean oldValue;
        if (JavaVersionUtil.JAVA_SPEC <= 11) {
            oldValue = tjlt.interruptedJDK11OrEarlier;
            if (oldValue != newValue) {
                tjlt.interruptedJDK11OrEarlier = newValue;
            }
        } else {
            oldValue = tjlt.interruptedJDK17OrLater;
            if (oldValue != newValue) {
                tjlt.interruptedJDK17OrLater = newValue;
            }
        }
        return oldValue;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected static boolean wasStartedByCurrentIsolate(IsolateThread thread) {
        Thread javaThread = platformThread.get(thread);
        return wasStartedByCurrentIsolate(javaThread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected static boolean wasStartedByCurrentIsolate(Thread thread) {
        return toTarget(thread).wasStartedByCurrentIsolate;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getParentThreadId(Thread thread) {
        return toTarget(thread).parentThreadId;
    }

    /* End of accessor functions. */

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Thread fromVMThread(IsolateThread thread) {
        assert CurrentIsolate.getCurrentThread() == thread || VMOperation.isInProgressAtSafepoint() || VMThreads.THREAD_MUTEX.isOwner() ||
                        SubstrateDiagnostics.isFatalErrorHandlingThread() : "must prevent the isolate thread from exiting";
        return platformThread.get(thread);
    }

    @Fold
    static boolean supportsVirtual() {
        return VirtualThreads.isSupported();
    }

    private static boolean isVirtual(Thread thread) {
        return supportsVirtual() && VirtualThreads.get().isVirtual(thread);
    }

    private static boolean isVirtualDisallowLoom(Thread thread) {
        if (LoomSupport.isEnabled()) {
            assert !isVirtual(thread) : "should not see Loom virtual thread objects here";
            return false;
        }
        return isVirtual(thread);
    }

    /**
     * Returns the isolate thread associated with a Java thread. The Java thread must currently be
     * alive (started) and remain alive during the execution of this method and then for as long as
     * the returned {@link IsolateThread} pointer is used.
     */
    public static IsolateThread getIsolateThreadUnsafe(Thread t) {
        return toTarget(t).isolateThread;
    }

    /**
     * Returns the isolate thread associated with a Java thread. The caller must own the
     * {@linkplain VMThreads#THREAD_MUTEX threads mutex} and release it only after it has finished
     * using the returned {@link IsolateThread} pointer.
     */
    public static IsolateThread getIsolateThread(Thread t) {
        VMThreads.guaranteeOwnsThreadMutex("Threads mutex must be locked before accessing/iterating the thread list.");
        VMError.guarantee(t.isAlive(), "Only running java.lang.Thread objects have a IsolateThread");
        return getIsolateThreadUnsafe(t);
    }

    @SuppressFBWarnings(value = "BC", justification = "Cast for @TargetClass")
    static Target_java_lang_ThreadGroup toTarget(ThreadGroup threadGroup) {
        return Target_java_lang_ThreadGroup.class.cast(threadGroup);
    }

    /** Before detaching a thread, run any Java cleanup code. */
    static void cleanupBeforeDetach(IsolateThread thread) {
        VMError.guarantee(thread.equal(CurrentIsolate.getCurrentThread()), "Cleanup must execute in detaching thread");

        Target_java_lang_Thread javaThread = SubstrateUtil.cast(platformThread.get(thread), Target_java_lang_Thread.class);
        javaThread.exit();
        ThreadListenerSupport.get().afterThreadExit(CurrentIsolate.getCurrentThread(), platformThread.get(thread));
    }

    static void join(Thread thread, long millis) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        if (supportsVirtual() && isVirtual(thread)) {
            VirtualThreads.get().join(thread, millis);
            return;
        }

        // Checkstyle: allow synchronization
        synchronized (thread) {
            if (millis > 0) {
                if (thread.isAlive()) {
                    final long startTime = System.nanoTime();
                    long delay = millis;
                    do {
                        thread.wait(delay);
                    } while (thread.isAlive() && (delay = millis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)) > 0);
                }
            } else {
                while (thread.isAlive()) {
                    thread.wait(0);
                }
            }
        }
        // Checkstyle: disallow synchronization
    }

    /**
     * Joins all non-daemon threads. If the current thread is itself a non-daemon thread, it does
     * not attempt to join itself.
     */
    public void joinAllNonDaemons() {
        int expectedNonDaemonThreads = Thread.currentThread().isDaemon() ? 0 : 1;
        joinAllNonDaemonsTransition(expectedNonDaemonThreads);
    }

    /**
     * We must not lock the {@link VMThreads#THREAD_MUTEX} while in Java mode, otherwise we can
     * deadlock when a safepoint is requested concurrently. Therefore, we transition the thread
     * manually from Java into native mode. This makes the lock / block / unlock atomic with respect
     * to safepoints.
     *
     * The garbage collector will not see (or update) any object references in the stack called by
     * this method while the thread is in native mode. Therefore, the uninterruptible code must only
     * reference objects that are in the image heap.
     */
    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode")
    private static void joinAllNonDaemonsTransition(int expectedNonDaemonThreads) {
        CFunctionPrologueNode.cFunctionPrologue(StatusSupport.STATUS_IN_NATIVE);
        joinAllNonDaemonsInNative(expectedNonDaemonThreads);
        CFunctionEpilogueNode.cFunctionEpilogue(StatusSupport.STATUS_IN_NATIVE);
    }

    @Uninterruptible(reason = "Must not stop while in native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static void joinAllNonDaemonsInNative(int expectedNonDaemonThreads) {
        VMThreads.THREAD_MUTEX.lockNoTransition();
        try {
            /*
             * nonDaemonThreads is allocated during image generation and therefore a never-moving
             * object in the image heap.
             */
            while (nonDaemonThreads.get() > expectedNonDaemonThreads) {
                VMThreads.THREAD_LIST_CONDITION.blockNoTransition();
            }
        } finally {
            VMThreads.THREAD_MUTEX.unlock();
        }
    }

    /**
     * Returns the thread size requested for this thread; otherwise, if there are no expectations,
     * then returns 0.
     */
    public static long getRequestedThreadSize(Thread thread) {
        /* Return a stack size based on parameters and command line flags. */
        long stackSize;
        long threadSpecificStackSize = LoomSupport.CompatibilityUtil.getStackSize(toTarget(thread));
        if (threadSpecificStackSize != 0) {
            /* If the user set a thread stack size at thread creation, then use that. */
            stackSize = threadSpecificStackSize;
        } else {
            /* If the user set a thread stack size on the command line, then use that. */
            stackSize = SubstrateOptions.StackSize.getValue();
        }

        if (stackSize != 0) {
            /*
             * Add the yellow+red zone size: This area of the stack is not accessible to the user's
             * Java code, so it would be surprising if we gave the user less stack space to use than
             * explicitly requested. In particular, a size less than the yellow+red size would lead
             * to an immediate StackOverflowError.
             */
            stackSize += StackOverflowCheck.singleton().yellowAndRedZoneSize();
        }
        return stackSize;
    }

    /**
     * Returns true if the {@link Thread} object for the current thread exists. This method only
     * returns false in the very early initialization stages of a newly attached thread.
     */
    public static boolean currentPlatformThreadInitialized() {
        return platformThread.get() != null;
    }

    /**
     * Ensures that a {@link Thread} object for the current thread exists. If a {@link Thread}
     * already exists, this method is a no-op. The current thread must have already been attached.
     *
     * @return true if a new thread was created; false if a {@link Thread} object had already been
     *         assigned.
     */
    public static boolean ensurePlatformThread() {
        /*
         * The thread was manually attached and started as a java.lang.Thread, so we consider it a
         * daemon thread.
         */
        return ensurePlatformThread(null, null, true);
    }

    /**
     * Ensures that a {@link Thread} object for the current thread exists. If a {@link Thread}
     * already exists, this method is a no-op. The current thread must have already been attached.
     *
     * @param name the thread's name, or {@code null} for a default name.
     * @param group the thread group, or {@code null} for the default thread group.
     * @param asDaemon the daemon status of the new thread.
     * @return true if a new thread was created; false if a {@link Thread} object had already been
     *         assigned.
     */
    public static boolean ensurePlatformThread(String name, ThreadGroup group, boolean asDaemon) {
        if (platformThread.get() == null) {
            assignPlatformThread(JavaThreads.fromTarget(new Target_java_lang_Thread(name, group, asDaemon)), true);
            return true;
        }
        return false;
    }

    /**
     * Assign a {@link Thread} object to the current thread, which must have already been attached
     * {@link VMThreads} as an {@link IsolateThread}.
     *
     * The manuallyStarted parameter is true if this thread was started directly by calling
     * {@link #ensurePlatformThread(String, ThreadGroup, boolean)}. It is false when the thread is
     * started via {@link #doStartThread} and {@link #threadStartRoutine}.
     */
    public static void assignPlatformThread(Thread thread, boolean manuallyStarted) {
        /*
         * First of all, ensure we are in RUNNABLE state. If !manuallyStarted, we race with the
         * thread that launched us to set the status and we could still be in status NEW.
         */
        setThreadStatus(thread, ThreadStatus.RUNNABLE);

        assignPlatformThread0(thread);

        /* If the thread was manually started, finish initializing it. */
        if (manuallyStarted) {
            final ThreadGroup group = thread.getThreadGroup();
            toTarget(group).addUnstarted();
            toTarget(group).add(thread);

            if (!thread.isDaemon()) {
                nonDaemonThreads.incrementAndGet();
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void assignPlatformThread0(Thread thread) {
        VMError.guarantee(platformThread.get() == null, "overwriting existing java.lang.Thread");
        platformThread.set(thread);

        assert toTarget(thread).isolateThread.isNull();
        toTarget(thread).isolateThread = CurrentIsolate.getCurrentThread();
        ThreadListenerSupport.get().beforeThreadStart(CurrentIsolate.getCurrentThread(), thread);
    }

    static void setCurrentThread(Thread carrier, Thread thread) {
        Thread currentCarrierThread = platformThread.get();
        assert currentCarrierThread == carrier;
        toTarget(carrier).vthread = (thread != currentCarrierThread) ? thread : null;
    }

    @Uninterruptible(reason = "Called during isolate initialization")
    public void initializeIsolate() {
        /* The thread that creates the isolate is considered the "main" thread. */
        assignPlatformThread0(mainThread);
    }

    /**
     * Tear down all application threads (except the current one). This is called from an
     * {@link CEntryPoint} exit action.
     *
     * @return true if the application threads have been torn down, false otherwise.
     */
    public boolean tearDown() {
        /* If the VM is single-threaded then this is the last (and only) thread. */
        if (!MultiThreaded.getValue()) {
            return true;
        }
        /* Tell all the threads that the VM is being torn down. */
        return tearDownPlatformThreads();
    }

    @Uninterruptible(reason = "Thread is detaching and holds the THREAD_MUTEX.")
    public static void detachThread(IsolateThread vmThread) {
        VMThreads.THREAD_MUTEX.assertIsOwner("Must hold the THREAD_MUTEX.");

        Thread thread = platformThread.get(vmThread);
        toTarget(thread).threadData.detach();
        toTarget(thread).isolateThread = WordFactory.nullPointer();
        if (!thread.isDaemon()) {
            nonDaemonThreads.decrementAndGet();
        }
    }

    /** Have each thread, except this one, tear itself down. */
    private static boolean tearDownPlatformThreads() {
        final Log trace = Log.noopLog().string("[JavaThreads.tearDownIsolateThreads:").newline().flush();

        /*
         * Set tear-down flag for new Java threads that have already been started on an OS level,
         * but are not attached yet. Threads will check this flag and self-interrupt in Java code.
         *
         * We still allow native threads to attach (via JNI, for example) and delay the tear-down
         * that way. If this causes problems, applications need to handle this in their code.
         */
        VMThreads.setTearingDown();

        /* Fetch all running application threads and interrupt them. */
        ArrayList<Thread> threads = new ArrayList<>();
        FetchApplicationThreadsOperation operation = new FetchApplicationThreadsOperation(threads);
        operation.enqueue();

        for (Thread thread : threads) {
            if (thread == Thread.currentThread()) {
                continue;
            }
            if (thread != null) {
                Log.noopLog().string("  interrupting: ").string(thread.getName()).newline().flush();
                thread.interrupt();
            }
        }

        final boolean result = waitForTearDown();
        trace.string("  returns: ").bool(result).string("]").newline().flush();
        return result;
    }

    /** Wait (im)patiently for the VMThreads list to drain. */
    private static boolean waitForTearDown() {
        assert isApplicationThread(CurrentIsolate.getCurrentThread()) : "we count the application threads until only the current one remains";

        final Log trace = Log.noopLog().string("[JavaThreads.waitForTearDown:").newline();
        final long warningNanos = SubstrateOptions.getTearDownWarningNanos();
        final String warningMessage = "JavaThreads.waitForTearDown is taking too long.";
        final long failureNanos = SubstrateOptions.getTearDownFailureNanos();
        final String failureMessage = "JavaThreads.waitForTearDown took too long.";
        final long startNanos = System.nanoTime();
        long loopNanos = startNanos;
        final AtomicBoolean printLaggards = new AtomicBoolean(false);
        final Log counterLog = ((warningNanos == 0) ? trace : Log.log());
        final CheckReadyForTearDownOperation operation = new CheckReadyForTearDownOperation(counterLog, printLaggards);

        for (; /* return */;) {
            final long previousLoopNanos = loopNanos;
            operation.enqueue();
            if (operation.isReadyForTearDown()) {
                trace.string("  returns true]").newline();
                return true;
            }
            loopNanos = TimeUtils.doNotLoopTooLong(startNanos, loopNanos, warningNanos, warningMessage);
            final boolean fatallyTooLong = TimeUtils.maybeFatallyTooLong(startNanos, failureNanos, failureMessage);
            if (fatallyTooLong) {
                /* I took too long to tear down the VM. */
                trace.string("Took too long to tear down the VM.").newline();
                /*
                 * Debugging tip: Insert a `BreakpointNode.breakpoint()` here to stop in gdb or get
                 * a core file with the thread stacks. Be careful about believing the stack traces,
                 * though.
                 */
                return false;
            }
            /* If I took too long, print the laggards next time around. */
            printLaggards.set(previousLoopNanos != loopNanos);
            /* Loop impatiently waiting for threads to exit. */
            Thread.yield();
        }
    }

    private static boolean isApplicationThread(IsolateThread isolateThread) {
        return !VMOperationControl.isDedicatedVMOperationThread(isolateThread);
    }

    @SuppressFBWarnings(value = "NN", justification = "notifyAll is necessary for Java semantics, no shared state needs to be modified beforehand")
    protected static void exit(Thread thread) {
        /*
         * First call Thread.exit(). This allows waiters on the thread object to observe that a
         * daemon ThreadGroup is destroyed as well if this thread happens to be the last thread of a
         * daemon group.
         */
        toTarget(thread).exit();
        /*
         * Then set the threadStatus to TERMINATED. This makes Thread.isAlive() return false and
         * allows Thread.join() to complete once we notify all the waiters below.
         */
        setThreadStatus(thread, ThreadStatus.TERMINATED);
        /*
         * And finally, wake up any threads waiting to join this one.
         */
        synchronized (thread) {
            thread.notifyAll();
        }
    }

    @RawStructure
    protected interface ThreadStartData extends PointerBase {

        @RawField
        ObjectHandle getThreadHandle();

        @RawField
        void setThreadHandle(ObjectHandle handle);

        @RawField
        Isolate getIsolate();

        @RawField
        void setIsolate(Isolate vm);
    }

    protected <T extends ThreadStartData> T prepareStart(Thread thread, int startDataSize) {
        T startData = UnmanagedMemory.malloc(startDataSize);
        startData.setIsolate(CurrentIsolate.getIsolate());
        startData.setThreadHandle(ObjectHandles.getGlobal().create(thread));
        if (!thread.isDaemon()) {
            nonDaemonThreads.incrementAndGet();
        }
        return startData;
    }

    /** GR-36413: for compatibility with legacy code, to be removed. Call prepareStart instead. */
    protected void prepareStartData(Thread thread, ThreadStartData startData) {
        startData.setIsolate(CurrentIsolate.getIsolate());
        startData.setThreadHandle(ObjectHandles.getGlobal().create(thread));

        if (!thread.isDaemon()) {
            nonDaemonThreads.incrementAndGet();
        }
    }

    protected void undoPrepareStartOnError(Thread thread, ThreadStartData startData) {
        if (!thread.isDaemon()) {
            undoPrepareNonDaemonStartOnError();
        }
        freeStartData(startData);
    }

    @Uninterruptible(reason = "Holding threads lock.")
    private static void undoPrepareNonDaemonStartOnError() {
        VMThreads.lockThreadMutexInNativeCode();
        try {
            nonDaemonThreads.decrementAndGet();
            VMThreads.THREAD_LIST_CONDITION.broadcast();
        } finally {
            VMThreads.THREAD_MUTEX.unlock();
        }
    }

    protected static void freeStartData(ThreadStartData startData) {
        UnmanagedMemory.free(startData);
    }

    void startThread(Thread thread, long stackSize) {
        unattachedStartedThreads.incrementAndGet();
        boolean started = startOSThread(thread, stackSize);
        if (!started) {
            unattachedStartedThreads.decrementAndGet();
            throw new OutOfMemoryError("unable to create native thread: possibly out of memory or process/resource limits reached");
        }
    }

    /**
     * Start a new OS thread. The implementation must call {@link #prepareStart} after preparations
     * and before starting the thread. The new OS thread must call {@link #threadStartRoutine}.
     *
     * @return {@code false} if the thread could not be started, {@code true} on success.
     */
    protected boolean startOSThread(Thread thread, long stackSize) {
        doStartThread(thread, stackSize);
        return true;
    }

    @SuppressWarnings("unused")
    protected void doStartThread(Thread thread, long stackSize) {
        throw VMError.shouldNotReachHere("GR-36413: for compatibility with legacy code, to be removed. Override startOSThread instead.");
    }

    @SuppressFBWarnings(value = "Ru", justification = "We really want to call Thread.run and not Thread.start because we are in the low-level thread start routine")
    protected static void threadStartRoutine(ObjectHandle threadHandle) {
        Thread thread = ObjectHandles.getGlobal().get(threadHandle);
        assignPlatformThread(thread, false);
        ObjectHandles.getGlobal().destroy(threadHandle);

        singleton().unattachedStartedThreads.decrementAndGet();
        singleton().beforeThreadRun(thread);

        try {
            if (VMThreads.isTearingDown()) {
                /*
                 * As a newly started thread, we might not have been interrupted like the Java
                 * threads that existed when initiating the tear-down, so we self-interrupt.
                 */
                Thread.currentThread().interrupt();
            }

            thread.run();
        } catch (Throwable ex) {
            dispatchUncaughtException(thread, ex);
        } finally {
            exit(thread);
        }
    }

    /** Hook for subclasses. */
    protected void beforeThreadRun(@SuppressWarnings("unused") Thread thread) {
    }

    /**
     * Set the OS-level name of the thread. This functionality is optional, i.e., if the OS does not
     * support thread names the implementation can remain empty.
     */
    protected abstract void setNativeName(Thread thread, String name);

    protected abstract void platformYield();

    void yield() {
        if (supportsVirtual() && isVirtualDisallowLoom(Thread.currentThread())) {
            VirtualThreads.get().yield();
        } else {
            platformYield();
        }
    }

    /**
     * Wake a thread which is waiting by other means, such as VM-internal condition variables, so
     * that they can check their interrupted status.
     */
    protected static void wakeUpVMConditionWaiters(Thread thread) {
        if (ReferenceHandler.useDedicatedThread() && ReferenceHandlerThread.isReferenceHandlerThread(thread)) {
            Heap.getHeap().wakeUpReferencePendingListWaiters();
        }
    }

    @NeverInline("Starting a stack walk in the caller frame")
    static StackTraceElement[] getStackTrace(Thread thread) {
        if (thread == Thread.currentThread()) {
            /*
             * We can walk our own stack without a VMOperation. Note that it is intentional that we
             * read the caller stack pointer in this helper method: The Thread.getStackTrace method
             * itself needs to be included in the result.
             */
            return StackTraceUtils.getStackTrace(false, KnownIntrinsics.readCallerStackPointer());
        }

        StackTraceElement[][] result = new StackTraceElement[1][0];
        JavaVMOperation.enqueueBlockingSafepoint("getStackTrace", () -> {
            if (thread.isAlive()) {
                result[0] = getStackTrace(getIsolateThread(thread));
            } else {
                result[0] = Target_java_lang_Thread.EMPTY_STACK_TRACE;
            }
        });
        return result[0];
    }

    static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        Map<Thread, StackTraceElement[]> result = new HashMap<>();
        JavaVMOperation.enqueueBlockingSafepoint("getAllStackTraces", () -> {
            for (IsolateThread cur = VMThreads.firstThread(); cur.isNonNull(); cur = VMThreads.nextThread(cur)) {
                result.put(JavaThreads.fromVMThread(cur), getStackTrace(cur));
            }
        });
        return result;
    }

    @NeverInline("Starting a stack walk in the caller frame")
    private static StackTraceElement[] getStackTrace(IsolateThread thread) {
        if (thread == CurrentIsolate.getCurrentThread()) {
            /*
             * Internal frames from the VMOperation handling show up in the stack traces, but we are
             * OK with that.
             */
            return StackTraceUtils.getStackTrace(false, readCallerStackPointer());
        } else {
            return StackTraceUtils.getStackTrace(false, thread);
        }
    }

    /** If there is an uncaught exception handler, call it. */
    public static void dispatchUncaughtException(Thread thread, Throwable throwable) {
        /* Get the uncaught exception handler for the Thread, or the default one. */
        UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
        if (handler == null) {
            handler = Thread.getDefaultUncaughtExceptionHandler();
        }
        if (handler != null) {
            try {
                handler.uncaughtException(thread, throwable);
            } catch (Throwable t) {
                /*
                 * The JavaDoc for {@code Thread.UncaughtExceptionHandler.uncaughtException} says
                 * the VM ignores any exceptions thrown.
                 */
            }
        } else {
            /* If no uncaught exception handler is present, then just report the throwable. */
            System.err.print("Exception in thread \"" + Thread.currentThread().getName() + "\" ");
            throwable.printStackTrace();
        }
    }

    /**
     * Thread instance initialization.
     *
     * This method is a copy of the implementation of the JDK 8 method
     *
     * <code>Thread.init(ThreadGroup g, Runnable target, String name, long stackSize)</code>
     *
     * and the JDK 11 constructor
     *
     * <code>Thread(ThreadGroup g, Runnable target, String name, long stackSize,
     * AccessControlContext acc, boolean inheritThreadLocals)</code>
     *
     * with these unsupported features removed:
     * <ul>
     * <li>No security manager: using the ContextClassLoader of the parent.</li>
     * <li>Not implemented: inheritableThreadLocals.</li>
     * </ul>
     */
    @SuppressWarnings({"deprecation"}) // AccessController is deprecated starting JDK 17
    static void initializeNewThread(
                    Target_java_lang_Thread tjlt,
                    ThreadGroup groupArg,
                    Runnable target,
                    String name,
                    long stackSize,
                    AccessControlContext acc) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        tjlt.name = name;

        final Thread parent = platformThread.get();
        final ThreadGroup group = ((groupArg != null) ? groupArg : parent.getThreadGroup());

        int priority;
        boolean daemon;
        if (JavaThreads.toTarget(parent) == tjlt) {
            priority = Thread.NORM_PRIORITY;
            daemon = false;
        } else {
            priority = parent.getPriority();
            daemon = parent.isDaemon();
        }
        LoomSupport.CompatibilityUtil.initThreadFields(tjlt, group, target, stackSize, priority, daemon, ThreadStatus.NEW);

        if (!LoomSupport.isEnabled()) {
            JavaThreads.toTarget(group).addUnstarted();
        }

        tjlt.contextClassLoader = parent.getContextClassLoader();

        tjlt.inheritedAccessControlContext = acc != null ? acc : AccessController.getContext();

        /* Set thread ID */
        tjlt.tid = Target_java_lang_Thread.nextThreadID();
    }

    /** Interruptibly park the current thread. */
    static void platformPark() {
        VMOperationControl.guaranteeOkayToBlock("[JavaThreads.park(): Should not park when it is not okay to block.]");
        Thread thread = Thread.currentThread();
        assert !isVirtual(thread);
        if (isInterrupted(thread)) { // avoid state changes and synchronization
            return;
        }

        ParkEvent parkEvent = getCurrentThreadData().ensureUnsafeParkEvent();
        // Change the Java thread state while parking.
        int oldStatus = JavaThreads.getThreadStatus(thread);
        int newStatus = MonitorSupport.singleton().maybeAdjustNewParkStatus(ThreadStatus.PARKED);
        JavaThreads.setThreadStatus(thread, newStatus);
        try {
            /*
             * If another thread interrupted this thread in the meanwhile, then the call below won't
             * block because Thread.interrupt() modifies the ParkEvent.
             */
            parkEvent.condWait();
        } finally {
            JavaThreads.setThreadStatus(thread, oldStatus);
        }
    }

    /** Interruptibly park the current thread for the given number of nanoseconds. */
    static void platformPark(long delayNanos) {
        VMOperationControl.guaranteeOkayToBlock("[JavaThreads.park(long): Should not park when it is not okay to block.]");
        Thread thread = Thread.currentThread();
        assert !isVirtual(thread);
        if (isInterrupted(thread)) { // avoid state changes and synchronization
            return;
        }

        ParkEvent parkEvent = getCurrentThreadData().ensureUnsafeParkEvent();
        int oldStatus = JavaThreads.getThreadStatus(thread);
        int newStatus = MonitorSupport.singleton().maybeAdjustNewParkStatus(ThreadStatus.PARKED_TIMED);
        JavaThreads.setThreadStatus(thread, newStatus);
        try {
            /*
             * If another thread interrupted this thread in the meanwhile, then the call below won't
             * block because Thread.interrupt() modifies the ParkEvent.
             */
            parkEvent.condTimedWait(delayNanos);
        } finally {
            JavaThreads.setThreadStatus(thread, oldStatus);
        }
    }

    /**
     * Unpark a Thread.
     *
     * @see #platformPark()
     * @see #platformPark(long)
     */
    static void platformUnpark(Thread thread) {
        assert !isVirtual(thread);
        ThreadData threadData = acquireThreadData(thread);
        if (threadData != null) {
            try {
                threadData.ensureUnsafeParkEvent().unpark();
            } finally {
                threadData.release();
            }
        }
    }

    static void sleep(long millis) throws InterruptedException {
        if (supportsVirtual() && isVirtualDisallowLoom(Thread.currentThread())) {
            VirtualThreads.get().sleepMillis(millis);
        } else {
            platformSleep(millis);
        }
    }

    /** Sleep for the given number of nanoseconds, dealing with early wakeups and interruptions. */
    private static void platformSleep(long millis) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        platformSleep0(TimeUtils.millisToNanos(millis));
        if (Thread.interrupted()) { // clears the interrupted flag as required of Thread.sleep()
            throw new InterruptedException();
        }
    }

    private static void platformSleep0(long delayNanos) {
        VMOperationControl.guaranteeOkayToBlock("[JavaThreads.platformSleep(long): Should not sleep when it is not okay to block.]");
        Thread thread = Thread.currentThread();
        ParkEvent sleepEvent = getCurrentThreadData().ensureSleepParkEvent();
        sleepEvent.reset();

        /*
         * It is critical to reset the event *before* checking for an interrupt to avoid losing a
         * wakeup in the race. This requires that updates to the event's unparked status and updates
         * to the thread's interrupt status cannot be reordered with regard to each other.
         *
         * Another important aspect is that the thread must have a sleepParkEvent assigned to it
         * *before* the interrupted check because if not, the interrupt code will not assign one and
         * the wakeup will be lost.
         */
        UNSAFE.fullFence();

        if (isInterrupted(thread)) {
            return; // likely leaves a stale unpark which will be reset before the next sleep()
        }
        final int oldStatus = JavaThreads.getThreadStatus(thread);
        JavaThreads.setThreadStatus(thread, ThreadStatus.SLEEPING);
        try {
            /*
             * If another thread interrupted this thread in the meanwhile, then the call below won't
             * block because Thread.interrupt() modifies the ParkEvent.
             */
            sleepEvent.condTimedWait(delayNanos);
        } finally {
            JavaThreads.setThreadStatus(thread, oldStatus);
        }
    }

    /**
     * Interrupt a sleeping thread.
     *
     * @see #platformSleep(long)
     */
    static void platformInterrupt(Thread thread) {
        assert !isVirtual(thread);
        ThreadData threadData = acquireThreadData(thread);
        if (threadData != null) {
            try {
                ParkEvent sleepEvent = threadData.getSleepParkEvent();
                if (sleepEvent != null) {
                    sleepEvent.unpark();
                }
            } finally {
                threadData.release();
            }
        }
    }

    static boolean isAlive(Thread thread) {
        if (supportsVirtual() && isVirtualDisallowLoom(thread)) {
            return VirtualThreads.get().isAlive(thread);
        }
        return platformIsAlive(thread);
    }

    static boolean platformIsAlive(Thread thread) {
        assert !isVirtual(thread);
        int threadStatus = LoomSupport.CompatibilityUtil.getThreadStatus(toTarget(thread));
        return !(threadStatus == ThreadStatus.NEW || threadStatus == ThreadStatus.TERMINATED);
    }

    private static ThreadData acquireThreadData(Thread thread) {
        return toTarget(thread).threadData.acquire();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static ThreadData getCurrentThreadData() {
        return (ThreadData) toTarget(Thread.currentThread()).threadData;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void setCurrentThreadLockHelper(Object root) {
        if (supportsVirtual()) {
            toTarget(Thread.currentThread()).lockHelper = root;
        } else {
            lockHelper.set(root);
        }
    }

    @AlwaysInline("Locking fast path.")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Object getCurrentThreadLockHelper() {
        if (supportsVirtual()) {
            return toTarget(Thread.currentThread()).lockHelper;
        }
        return lockHelper.get();
    }

    /**
     * Builds a list of all application threads. This must be done in a VM operation because only
     * there we are allowed to allocate Java memory while holding the {@link VMThreads#THREAD_MUTEX}
     */
    private static class FetchApplicationThreadsOperation extends JavaVMOperation {
        private final List<Thread> list;

        FetchApplicationThreadsOperation(List<Thread> list) {
            super("FetchApplicationThreads", SystemEffect.NONE);
            this.list = list;
        }

        @Override
        public void operate() {
            list.clear();
            VMMutex lock = VMThreads.THREAD_MUTEX.lock();
            try {
                for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
                    if (isApplicationThread(isolateThread)) {
                        final Thread thread = JavaThreads.fromVMThread(isolateThread);
                        if (thread != null) {
                            list.add(thread);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Determines if the VM is ready for tear down, which is when only the current application
     * thread is attached and no threads have been started which have yet to attach. This must be
     * done in a VM operation because only there we are allowed to allocate Java memory while
     * holding the {@link VMThreads#THREAD_MUTEX}.
     */
    private static class CheckReadyForTearDownOperation extends JavaVMOperation {
        private final Log trace;
        private final AtomicBoolean printLaggards;
        private boolean readyForTearDown;

        CheckReadyForTearDownOperation(Log trace, AtomicBoolean printLaggards) {
            super("CheckReadyForTearDown", SystemEffect.NONE);
            this.trace = trace;
            this.printLaggards = printLaggards;
        }

        boolean isReadyForTearDown() {
            return readyForTearDown;
        }

        @Override
        public void operate() {
            int attachedCount = 0;
            int unattachedStartedCount;
            VMMutex lock = VMThreads.THREAD_MUTEX.lock();
            try {
                for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
                    if (isApplicationThread(isolateThread)) {
                        attachedCount++;
                        if (printLaggards.get() && trace.isEnabled() && isolateThread != queuingThread) {
                            trace.string("  laggard isolateThread: ").hex(isolateThread);
                            final Thread thread = JavaThreads.fromVMThread(isolateThread);
                            if (thread != null) {
                                final String name = thread.getName();
                                final Thread.State status = thread.getState();
                                final boolean interruptedStatus = isInterrupted(thread);
                                trace.string("  thread.getName(): ").string(name)
                                                .string("  interruptedStatus: ").bool(interruptedStatus)
                                                .string("  getState(): ").string(status.name());
                            }
                            trace.newline().flush();
                        }
                    }
                }

                /*
                 * Note: our counter for unattached started threads is not guarded by the threads
                 * mutex and its count could change or have changed within this block. Still, it is
                 * important that we hold the threads mutex when querying the counter value: a
                 * thread might start another thread and exit immediately after. By holding the
                 * threads lock, we prevent the exiting thread from detaching, and/or the starting
                 * thread from attaching, so we will never consider being ready for tear-down.
                 */
                unattachedStartedCount = singleton().unattachedStartedThreads.get();
            } finally {
                lock.unlock();
            }
            readyForTearDown = (attachedCount == 1 && unattachedStartedCount == 0);
        }
    }
}
