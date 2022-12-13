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
import static com.oracle.svm.core.thread.JavaThreads.fromTarget;
import static com.oracle.svm.core.thread.JavaThreads.isCurrentThreadVirtual;
import static com.oracle.svm.core.thread.JavaThreads.isVirtual;
import static com.oracle.svm.core.thread.JavaThreads.toTarget;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceHandler;
import com.oracle.svm.core.heap.ReferenceHandlerThread;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.jdk.Target_jdk_internal_misc_VM;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.sampler.ProfilingSampler;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;

/**
 * Implements operations on platform threads, which are typical {@link Thread Java threads} which
 * correspond to an OS thread.
 *
 * @see JavaThreads
 */
public abstract class PlatformThreads {
    private static final Field FIELDHOLDER_STATUS_FIELD = (JavaVersionUtil.JAVA_SPEC >= 19 && ImageInfo.inImageCode())
                    ? ReflectionUtil.lookupField(Target_java_lang_Thread_FieldHolder.class, "threadStatus")
                    : null;
    private static final Field THREAD_STATUS_FIELD = (JavaVersionUtil.JAVA_SPEC < 19 && ImageInfo.inImageCode())
                    ? ReflectionUtil.lookupField(Target_java_lang_Thread.class, "threadStatus")
                    : null;

    @Fold
    public static PlatformThreads singleton() {
        return ImageSingletons.lookup(PlatformThreads.class);
    }

    /** The platform {@link java.lang.Thread} for the {@link IsolateThread}. */
    static final FastThreadLocalObject<Thread> currentThread = FastThreadLocalFactory.createObject(Thread.class, "PlatformThreads.currentThread").setMaxOffset(FastThreadLocal.BYTE_OFFSET);

    /**
     * The {@linkplain JavaThreads#getThreadId thread id} of the {@link Thread#currentThread()},
     * which can be a {@linkplain Target_java_lang_Thread#vthread virtual thread} or the
     * {@linkplain #currentThread platform thread itself}.
     */
    static final FastThreadLocalLong currentVThreadId = FastThreadLocalFactory.createLong("PlatformThreads.currentVThreadId").setMaxOffset(FastThreadLocal.BYTE_OFFSET);

    /**
     * A thread-local helper object for locking. Use only if each {@link Thread} corresponds to an
     * {@link IsolateThread}, otherwise use {@link Target_java_lang_Thread#lockHelper}.
     */
    static final FastThreadLocalObject<Object> lockHelper = FastThreadLocalFactory.createObject(Object.class, "PlatformThreads.lockHelper").setMaxOffset(FastThreadLocal.BYTE_OFFSET);

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
    protected PlatformThreads() {
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

    @Uninterruptible(reason = "Thread locks/holds the THREAD_MUTEX.")
    public static long getThreadAllocatedBytes(long javaThreadId) {
        // Accessing the value for the current thread is fast.
        Thread curThread = PlatformThreads.currentThread.get();
        if (curThread != null && JavaThreads.getThreadId(curThread) == javaThreadId) {
            return Heap.getHeap().getThreadAllocatedMemory(CurrentIsolate.getCurrentThread());
        }

        // If the value of another thread is accessed, then we need to do a slow lookup.
        VMThreads.lockThreadMutexInNativeCode();
        try {
            IsolateThread isolateThread = VMThreads.firstThread();
            while (isolateThread.isNonNull()) {
                Thread javaThread = PlatformThreads.currentThread.get(isolateThread);
                if (javaThread != null && JavaThreads.getThreadId(javaThread) == javaThreadId) {
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
    public static long getThreadCpuTime(long javaThreadId, boolean includeSystemTime) {
        if (!ImageSingletons.contains(ThreadCpuTimeSupport.class)) {
            return -1;
        }
        // Accessing the value for the current thread is fast.
        Thread curThread = PlatformThreads.currentThread.get();
        if (curThread != null && JavaThreads.getThreadId(curThread) == javaThreadId) {
            return ThreadCpuTimeSupport.getInstance().getCurrentThreadCpuTime(includeSystemTime);
        }

        // If the value of another thread is accessed, then we need to do a slow lookup.
        VMThreads.lockThreadMutexInNativeCode();
        try {
            IsolateThread isolateThread = VMThreads.firstThread();
            while (isolateThread.isNonNull()) {
                Thread javaThread = PlatformThreads.currentThread.get(isolateThread);
                if (javaThread != null && JavaThreads.getThreadId(javaThread) == javaThreadId) {
                    return ThreadCpuTimeSupport.getInstance().getThreadCpuTime(VMThreads.findOSThreadHandleForIsolateThread(isolateThread), includeSystemTime);
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
                Thread javaThread = PlatformThreads.currentThread.get(isolateThread);
                if (javaThread != null) {
                    for (int i = 0; i < javaThreadIds.length; i++) {
                        if (JavaThreads.getThreadId(javaThread) == javaThreadIds[i]) {
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

    static void setInterrupt(Thread thread) {
        assert !isVirtual(thread);
        if (!JavaThreads.isInterrupted(thread)) {
            JavaThreads.writeInterruptedFlag(thread, true);
            toTarget(thread).interrupt0();
        }
    }

    /* End of accessor functions. */

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Thread fromVMThread(IsolateThread thread) {
        assert CurrentIsolate.getCurrentThread() == thread || VMOperation.isInProgressAtSafepoint() || VMThreads.THREAD_MUTEX.isOwner() ||
                        SubstrateDiagnostics.isFatalErrorHandlingThread() : "must prevent the isolate thread from exiting";
        return currentThread.get(thread);
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

    /** Before detaching a thread, run any Java cleanup code. */
    static void threadExit(IsolateThread thread) {
        VMError.guarantee(thread.equal(CurrentIsolate.getCurrentThread()), "Cleanup must execute in detaching thread");

        Thread javaThread = currentThread.get(thread);
        if (javaThread != null) {
            toTarget(javaThread).exit();
        }
    }

    @Uninterruptible(reason = "Only uninterruptible code may be executed after Thread.exit.")
    static void afterThreadExit(IsolateThread thread) {
        VMError.guarantee(thread.equal(CurrentIsolate.getCurrentThread()), "Cleanup must execute in detaching thread");

        Thread javaThread = currentThread.get(thread);
        if (javaThread != null) {
            ThreadListenerSupport.get().afterThreadExit(thread, javaThread);
        }
    }

    static void join(Thread thread, long millis) throws InterruptedException {
        assert !isVirtual(thread);
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
        int expectedNonDaemonThreads = currentThread.get().isDaemon() ? 0 : 1;
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
     * Returns the stack size requested for this thread; otherwise, if there are no expectations,
     * then returns 0.
     */
    public static long getRequestedStackSize(Thread thread) {
        /* Return a stack size based on parameters and command line flags. */
        long stackSize;
        Target_java_lang_Thread tjlt = toTarget(thread);
        long threadSpecificStackSize = (JavaVersionUtil.JAVA_SPEC >= 19) ? tjlt.holder.stackSize : tjlt.stackSize;
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
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isCurrentAssigned() {
        return currentThread.get() != null;
    }

    /**
     * Ensures that a {@link Thread} object for the current thread exists. If a {@link Thread}
     * already exists, this method is a no-op. The current thread must have already been attached.
     *
     * @return true if a new thread was created; false if a {@link Thread} object had already been
     *         assigned.
     */
    public static boolean ensureCurrentAssigned() {
        /*
         * The thread was manually attached and started as a java.lang.Thread, so we consider it a
         * daemon thread.
         */
        return ensureCurrentAssigned(null, null, true);
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
    public static boolean ensureCurrentAssigned(String name, ThreadGroup group, boolean asDaemon) {
        if (currentThread.get() == null) {
            assignCurrent(fromTarget(new Target_java_lang_Thread(name, group, asDaemon)), true);
            return true;
        }
        return false;
    }

    /**
     * Assign a {@link Thread} object to the current thread, which must have already been attached
     * {@link VMThreads} as an {@link IsolateThread}.
     *
     * The manuallyStarted parameter is true if this thread was started directly by calling
     * {@link #ensureCurrentAssigned(String, ThreadGroup, boolean)}. It is false when the thread is
     * started via {@link #doStartThread} and {@link #threadStartRoutine}.
     */
    static void assignCurrent(Thread thread, boolean manuallyStarted) {
        /*
         * First of all, ensure we are in RUNNABLE state. If !manuallyStarted, we race with the
         * thread that launched us to set the status and we could still be in status NEW.
         */
        setThreadStatus(thread, ThreadStatus.RUNNABLE);

        assignCurrent0(thread);

        /* If the thread was manually started, finish initializing it. */
        if (manuallyStarted) {
            final ThreadGroup group = thread.getThreadGroup();
            if (JavaVersionUtil.JAVA_SPEC < 19 && !(VirtualThreads.isSupported() && VirtualThreads.singleton().isVirtual(thread))) {
                toTarget(group).addUnstarted();
                toTarget(group).add(thread);
            }

            if (!thread.isDaemon()) {
                nonDaemonThreads.incrementAndGet();
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void assignCurrent0(Thread thread) {
        VMError.guarantee(currentThread.get() == null, "overwriting existing java.lang.Thread");
        currentVThreadId.set(JavaThreads.getThreadId(thread));
        currentThread.set(thread);

        assert toTarget(thread).isolateThread.isNull();
        toTarget(thread).isolateThread = CurrentIsolate.getCurrentThread();
        ThreadListenerSupport.get().beforeThreadStart(CurrentIsolate.getCurrentThread(), thread);
    }

    @Uninterruptible(reason = "Ensure consistency of vthread and cached vthread id.")
    static void setCurrentThread(Thread carrier, Thread thread) {
        assert carrier == currentThread.get();
        assert thread == carrier || (VirtualThreads.isSupported() && VirtualThreads.singleton().isVirtual(thread));
        toTarget(carrier).vthread = (thread != carrier) ? thread : null;
        currentVThreadId.set(JavaThreads.getThreadId(thread));
    }

    @Uninterruptible(reason = "Called during isolate initialization")
    public void initializeIsolate() {
        /* The thread that creates the isolate is considered the "main" thread. */
        assignCurrent0(mainThread);
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

        Thread thread = currentThread.get(vmThread);
        if (thread != null) {
            toTarget(thread).threadData.detach();
            toTarget(thread).isolateThread = WordFactory.nullPointer();
            if (!thread.isDaemon()) {
                nonDaemonThreads.decrementAndGet();
            }
        }
    }

    @SuppressWarnings("unused")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public OSThreadHandle startThreadUnmanaged(CFunctionPointer threadRoutine, PointerBase userData, int stackSize) {
        throw VMError.shouldNotReachHere("Shouldn't call PlatformThreads.startThreadUnmanaged directly.");
    }

    @SuppressWarnings("unused")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean joinThreadUnmanaged(OSThreadHandle threadHandle, WordPointer threadExitStatus) {
        throw VMError.shouldNotReachHere("Shouldn't call PlatformThreads.joinThreadUnmanaged directly.");
    }

    @SuppressWarnings("unused")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void closeOSThreadHandle(OSThreadHandle threadHandle) {
        throw VMError.shouldNotReachHere("Shouldn't call PlatformThreads.closeOSThreadHandle directly.");
    }

    static final Method FORK_JOIN_POOL_TRY_TERMINATE_METHOD;

    static {
        VMError.guarantee(ImageInfo.inImageBuildtimeCode(), PlatformThreads.class.getSimpleName() + " must be initialized at build time.");
        FORK_JOIN_POOL_TRY_TERMINATE_METHOD = ReflectionUtil.lookupMethod(ForkJoinPool.class, "tryTerminate", boolean.class, boolean.class);
    }

    /** Have each thread, except this one, tear itself down. */
    private static boolean tearDownPlatformThreads() {
        final Log trace = Log.noopLog().string("[PlatformThreads.tearDownPlatformThreads:").newline().flush();

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

        Set<ExecutorService> pools = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ExecutorService> poolsWithNonDaemons = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Thread thread : threads) {
            if (thread == null || thread == currentThread.get()) {
                continue;
            }

            trace.string("  interrupting: ").string(thread.getName()).newline();
            try {
                thread.interrupt(); // not final and subclasses can unexpectedly throw
            } catch (Throwable t) {
                trace.string(" threw (ignored): ").exception(t);
            }
            trace.newline().flush();

            /*
             * Pool worker threads ignore interrupts unless their pool is shutting down. We try to
             * get their pool and shut it down, but are defensive and do so only for pool classes we
             * know (shutdown methods can be overridden) and only if all of a pool's threads are
             * daemons (which is the default for ForkJoinPool; for ThreadPoolExecutor, it depends on
             * its ThreadFactory). For consistency, we still interrupt each individual thread above,
             * especially if we end up not shutting down its pool if we find a non-daemon thread.
             */
            Set<ExecutorService> set = thread.isDaemon() ? pools : poolsWithNonDaemons;
            if (thread instanceof ForkJoinWorkerThread) {
                // read field "pool" directly to bypass getPool(), which is not final
                ForkJoinPool pool = SubstrateUtil.cast(thread, Target_java_util_concurrent_ForkJoinWorkerThread.class).pool;
                if (pool != null && pool.getClass() == ForkJoinPool.class) {
                    set.add(pool);
                }
            } else {
                Target_java_lang_Thread tjlt = toTarget(thread);
                Runnable target = JavaVersionUtil.JAVA_SPEC >= 19 ? tjlt.holder.task : tjlt.target;
                if (Target_java_util_concurrent_ThreadPoolExecutor_Worker.class.isInstance(target)) {
                    ThreadPoolExecutor executor = SubstrateUtil.cast(target, Target_java_util_concurrent_ThreadPoolExecutor_Worker.class).executor;
                    if (executor != null && (executor.getClass() == ThreadPoolExecutor.class || executor.getClass() == ScheduledThreadPoolExecutor.class)) {
                        set.add(executor);
                    }
                }
            }
        }

        pools.removeAll(poolsWithNonDaemons);
        for (ExecutorService pool : pools) {
            trace.string("  initiating shutdown: ").object(pool);
            try {
                if (pool == ForkJoinPool.commonPool()) {
                    FORK_JOIN_POOL_TRY_TERMINATE_METHOD.invoke(pool, true, true);
                } else {
                    pool.shutdownNow();
                }
            } catch (Throwable t) {
                trace.string(" threw (ignored): ").exception(t);
            }
            trace.newline().flush();
            trace.string("  shutdown initiated: ").object(pool).newline().flush();
        }

        final boolean result = waitForTearDown();
        trace.string("  returns: ").bool(result).string("]").newline().flush();
        return result;
    }

    /** Wait (im)patiently for the VMThreads list to drain. */
    private static boolean waitForTearDown() {
        assert isApplicationThread(CurrentIsolate.getCurrentThread()) : "we count the application threads until only the current one remains";

        final Log trace = Log.noopLog().string("[PlatformThreads.waitForTearDown:").newline();
        final long warningNanos = SubstrateOptions.getTearDownWarningNanos();
        final String warningMessage = "PlatformThreads.waitForTearDown is taking too long.";
        final long failureNanos = SubstrateOptions.getTearDownFailureNanos();
        final String failureMessage = "PlatformThreads.waitForTearDown took too long.";
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

    /** This method should only be used to exit the main thread. */
    @SuppressFBWarnings(value = "NN", justification = "notifyAll is necessary for Java semantics, no shared state needs to be modified beforehand")
    public static void exit(Thread thread) {
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
        boolean started = doStartThread(thread, stackSize);
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
    protected abstract boolean doStartThread(Thread thread, long stackSize);

    @SuppressFBWarnings(value = "Ru", justification = "We really want to call Thread.run and not Thread.start because we are in the low-level thread start routine")
    protected static void threadStartRoutine(ObjectHandle threadHandle) {
        Thread thread = ObjectHandles.getGlobal().get(threadHandle);
        assignCurrent(thread, false);
        ObjectHandles.getGlobal().destroy(threadHandle);

        singleton().unattachedStartedThreads.decrementAndGet();
        singleton().beforeThreadRun(thread);

        if (ImageSingletons.contains(ProfilingSampler.class)) {
            ImageSingletons.lookup(ProfilingSampler.class).registerSampler();
        }

        try {
            if (VMThreads.isTearingDown()) {
                /*
                 * As a newly started thread, we might not have been interrupted like the Java
                 * threads that existed when initiating the tear-down, so we self-interrupt.
                 */
                currentThread.get().interrupt();
            }

            thread.run();
        } catch (Throwable ex) {
            JavaThreads.dispatchUncaughtException(thread, ex);
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

    protected abstract void yieldCurrent();

    /**
     * Wake a thread which is waiting by other means, such as VM-internal condition variables, so
     * that they can check their interrupted status.
     */
    protected static void wakeUpVMConditionWaiters(Thread thread) {
        if (ReferenceHandler.useDedicatedThread() && ReferenceHandlerThread.isReferenceHandlerThread(thread)) {
            Heap.getHeap().wakeUpReferencePendingListWaiters();
        }
    }

    static StackTraceElement[] getStackTrace(boolean filterExceptions, Thread thread, Pointer callerSP) {
        assert !isVirtual(thread);
        if (thread == currentThread.get()) {
            return StackTraceUtils.getStackTrace(filterExceptions, callerSP, WordFactory.nullPointer());
        }
        assert !filterExceptions : "exception stack traces can be taken only for the current thread";
        return StackTraceUtils.asyncGetStackTrace(thread);
    }

    public static StackTraceElement[] getStackTraceAtSafepoint(Thread thread, Pointer callerSP) {
        assert !isVirtual(thread);
        IsolateThread isolateThread = getIsolateThread(thread);
        if (isolateThread == CurrentIsolate.getCurrentThread()) {
            /*
             * Internal frames from the VMOperation handling show up in the stack traces, but we are
             * OK with that.
             */
            return StackTraceUtils.getStackTrace(false, callerSP, WordFactory.nullPointer());
        }
        return StackTraceUtils.getThreadStackTraceAtSafepoint(isolateThread, WordFactory.nullPointer());
    }

    static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        GetAllStackTracesOperation vmOp = new GetAllStackTracesOperation();
        vmOp.enqueue();
        return vmOp.result;
    }

    static Thread[] getAllThreads() {
        GetAllThreadsOperation vmOp = new GetAllThreadsOperation();
        vmOp.enqueue();
        return vmOp.result.toArray(new Thread[0]);
    }

    /** Interruptibly park the current thread. */
    static void parkCurrentPlatformOrCarrierThread() {
        parkCurrentPlatformOrCarrierThread(false, 0);
    }

    /** Interruptibly park the current thread, indefinitely or with a timeout. */
    static void parkCurrentPlatformOrCarrierThread(boolean isAbsolute, long time) {
        VMOperationControl.guaranteeOkayToBlock("[PlatformThreads.parkCurrentPlatformOrCarrierThread: Should not park when it is not okay to block.]");

        if (time < 0 || (isAbsolute && time == 0)) {
            return; // don't wait at all
        }
        boolean timed = (time != 0);

        Thread thread = currentThread.get();
        if (JavaThreads.isInterrupted(thread)) { // avoid state changes and synchronization
            return;
        }

        ParkEvent parkEvent = getCurrentThreadData().ensureUnsafeParkEvent();
        if (parkEvent.tryFastPark()) {
            return;
        }
        int oldStatus = getThreadStatus(thread);
        int newStatus = MonitorSupport.singleton().getParkedThreadStatus(currentThread.get(), timed);
        setThreadStatus(thread, newStatus);
        try {
            /*
             * If another thread interrupted this thread in the meanwhile, then the call below won't
             * block because Thread.interrupt() modifies the ParkEvent.
             */
            parkEvent.park(isAbsolute, time);
        } finally {
            setThreadStatus(thread, oldStatus);
        }
    }

    /**
     * Unpark a Thread.
     *
     * @see #parkCurrentPlatformOrCarrierThread()
     * @see #parkCurrentPlatformOrCarrierThread(boolean, long)
     */
    static void unpark(Thread thread) {
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

    /** Sleep for the given number of nanoseconds, dealing with early wakeups and interruptions. */
    static void sleep(long millis) throws InterruptedException {
        assert !isCurrentThreadVirtual();
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        sleep0(TimeUtils.millisToNanos(millis));
        if (Thread.interrupted()) { // clears the interrupted flag as required of Thread.sleep()
            throw new InterruptedException();
        }
    }

    private static void sleep0(long durationNanos) {
        VMOperationControl.guaranteeOkayToBlock("[PlatformThreads.sleep(long): Should not sleep when it is not okay to block.]");
        Thread thread = currentThread.get();
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
        Unsafe.getUnsafe().fullFence();

        if (JavaThreads.isInterrupted(thread)) {
            return; // likely leaves a stale unpark which will be reset before the next sleep()
        }
        final int oldStatus = getThreadStatus(thread);
        setThreadStatus(thread, ThreadStatus.SLEEPING);
        try {
            long remainingNanos = durationNanos;
            long startNanos = System.nanoTime();
            while (remainingNanos > 0) {
                /*
                 * If another thread interrupted this thread in the meanwhile, then the call below
                 * won't block because Thread.interrupt() modifies the ParkEvent.
                 */
                sleepEvent.condTimedWait(remainingNanos);
                if (JavaThreads.isInterrupted(thread)) {
                    return;
                }
                remainingNanos = durationNanos - (System.nanoTime() - startNanos);
            }
        } finally {
            setThreadStatus(thread, oldStatus);
        }
    }

    /**
     * Interrupt a sleeping thread.
     *
     * @see #sleep(long)
     */
    static void interrupt(Thread thread) {
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

    public static int getThreadStatus(Thread thread) {
        assert !isVirtual(thread);
        return (JavaVersionUtil.JAVA_SPEC >= 19) ? toTarget(thread).holder.threadStatus : toTarget(thread).threadStatus;
    }

    /** Safe method to get a thread's internal state since {@link Thread#getState} is not final. */
    static Thread.State getThreadState(Thread thread) {
        return Target_jdk_internal_misc_VM.toThreadState(getThreadStatus(thread));
    }

    public static void setThreadStatus(Thread thread, int threadStatus) {
        assert !isVirtual(thread);
        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            toTarget(thread).holder.threadStatus = threadStatus;
        } else {
            toTarget(thread).threadStatus = threadStatus;
        }
    }

    static boolean compareAndSetThreadStatus(Thread thread, int expectedStatus, int newStatus) {
        assert !isVirtual(thread);
        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            return Unsafe.getUnsafe().compareAndSetInt(toTarget(thread).holder, Unsafe.getUnsafe().objectFieldOffset(FIELDHOLDER_STATUS_FIELD), expectedStatus, newStatus);
        } else {
            return Unsafe.getUnsafe().compareAndSetInt(thread, Unsafe.getUnsafe().objectFieldOffset(THREAD_STATUS_FIELD), expectedStatus, newStatus);
        }
    }

    static boolean isAlive(Thread thread) {
        int threadStatus = getThreadStatus(thread);
        return !(threadStatus == ThreadStatus.NEW || threadStatus == ThreadStatus.TERMINATED);
    }

    private static ThreadData acquireThreadData(Thread thread) {
        return toTarget(thread).threadData.acquire();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static ThreadData getCurrentThreadData() {
        return (ThreadData) toTarget(currentThread.get()).threadData;
    }

    private static class GetAllStackTracesOperation extends JavaVMOperation {
        private final Map<Thread, StackTraceElement[]> result;

        GetAllStackTracesOperation() {
            super(VMOperationInfos.get(GetAllStackTracesOperation.class, "Get all stack traces", SystemEffect.SAFEPOINT));
            result = new HashMap<>();
        }

        @Override
        protected void operate() {
            for (IsolateThread cur = VMThreads.firstThread(); cur.isNonNull(); cur = VMThreads.nextThread(cur)) {
                Thread thread = PlatformThreads.fromVMThread(cur);
                result.put(thread, StackTraceUtils.getStackTraceAtSafepoint(thread));
            }
        }
    }

    private static class GetAllThreadsOperation extends JavaVMOperation {
        private final ArrayList<Thread> result;

        GetAllThreadsOperation() {
            super(VMOperationInfos.get(GetAllThreadsOperation.class, "Get all threads", SystemEffect.SAFEPOINT));
            result = new ArrayList<>();
        }

        @Override
        protected void operate() {
            for (IsolateThread cur = VMThreads.firstThread(); cur.isNonNull(); cur = VMThreads.nextThread(cur)) {
                Thread thread = PlatformThreads.fromVMThread(cur);
                if (thread != null) {
                    result.add(thread);
                }
            }
        }
    }

    /**
     * Builds a list of all application threads. This must be done in a VM operation because only
     * there we are allowed to allocate Java memory while holding the {@link VMThreads#THREAD_MUTEX}
     */
    private static class FetchApplicationThreadsOperation extends JavaVMOperation {
        private final List<Thread> list;

        FetchApplicationThreadsOperation(List<Thread> list) {
            super(VMOperationInfos.get(FetchApplicationThreadsOperation.class, "Fetch application threads", SystemEffect.NONE));
            this.list = list;
        }

        @Override
        public void operate() {
            list.clear();
            VMMutex lock = VMThreads.THREAD_MUTEX.lock();
            try {
                for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
                    if (isApplicationThread(isolateThread)) {
                        final Thread thread = PlatformThreads.fromVMThread(isolateThread);
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
            super(VMOperationInfos.get(CheckReadyForTearDownOperation.class, "Check ready for teardown", SystemEffect.NONE));
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
                            final Thread thread = PlatformThreads.fromVMThread(isolateThread);
                            if (thread != null) {
                                final String name = thread.getName();
                                final Thread.State status = thread.getState();
                                final boolean interruptedStatus = JavaThreads.isInterrupted(thread);
                                trace.string("  thread.getName(): ").string(name)
                                                .string("  interruptedStatus: ").bool(interruptedStatus)
                                                .string("  getState(): ").string(status.name()).newline();
                                for (StackTraceElement e : thread.getStackTrace()) {
                                    trace.string(e.toString()).newline();
                                }
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

    static void blockedOn(Target_sun_nio_ch_Interruptible b) {
        assert !isCurrentThreadVirtual();
        Target_java_lang_Thread me = toTarget(currentThread.get());
        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            synchronized (me.interruptLock) {
                me.nioBlocker = b;
            }
        } else {
            synchronized (me.blockerLock) {
                me.blocker = b;
            }
        }
    }

    public interface OSThreadHandle extends PointerBase {
    }
}

@TargetClass(value = ThreadPoolExecutor.class, innerClass = "Worker")
final class Target_java_util_concurrent_ThreadPoolExecutor_Worker {
    @Alias @TargetElement(name = "this$0") //
    ThreadPoolExecutor executor;
}

@TargetClass(ForkJoinWorkerThread.class)
final class Target_java_util_concurrent_ForkJoinWorkerThread {
    @Alias //
    ForkJoinPool pool;
}
