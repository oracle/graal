/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode.writeCurrentVMThread;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CFunctionOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicWord;
import com.oracle.svm.core.layeredimagesingleton.RuntimeOnlyImageSingleton;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMLockSupport;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.threadlocal.VMThreadLocalSupport;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.replacements.nodes.AssertionNode;

/**
 * Utility methods for the manipulation and iteration of {@link IsolateThread}s.
 */
public abstract class VMThreads implements RuntimeOnlyImageSingleton {

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
     * <li>Acquire the mutex from a thread that is not yet attached
     * ({@link StatusSupport#STATUS_CREATED}).</li>
     * <li>Acquire the mutex from a thread that is in native code
     * ({@link StatusSupport#STATUS_IN_NATIVE}). This is also possible from a thread that is in Java
     * state by doing an explicit transition to native, see
     * {@link #lockThreadMutexInNativeCode}.</li>
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
    protected static final VMMutex THREAD_MUTEX = new VMMutex("thread");

    /**
     * A condition variable for waiting for and notifying on changes to the {@link IsolateThread}
     * list.
     */
    protected static final VMCondition THREAD_LIST_CONDITION = new VMCondition(THREAD_MUTEX);

    /**
     * The first element in the linked list of {@link IsolateThread}s. Protected by
     * {@link #THREAD_MUTEX}.
     */
    private static IsolateThread head;
    /** The number of attached threads. Protected by {@link #THREAD_MUTEX}. */
    private static int numAttachedThreads = 0;
    /**
     * This field is used to guarantee that all isolate threads that were started by SVM have exited
     * on the operating system level before tearing down an isolate. This is necessary to prevent
     * the case that a shared library native image is unloaded while there are still running
     * threads.
     *
     * If a thread is referenced by this field, then it was started by the current isolate and has
     * already finished execution on the Java-level. However, without checking explicitly, we can't
     * say for sure if a thread has exited on the operating system level as well.
     */
    private static final AtomicWord<OSThreadHandle> detachedOsThreadToCleanup = new AtomicWord<>();

    /**
     * The next element in the linked list of {@link IsolateThread}s. A thread points to itself with
     * this field after being removed from the linked list.
     */
    public static final FastThreadLocalWord<IsolateThread> nextTL = FastThreadLocalFactory.createWord("VMThreads.nextTL");
    private static final FastThreadLocalWord<OSThreadId> OSThreadIdTL = FastThreadLocalFactory.createWord("VMThreads.OSThreadIdTL");
    public static final FastThreadLocalWord<OSThreadHandle> OSThreadHandleTL = FastThreadLocalFactory.createWord("VMThreads.OSThreadHandleTL");
    public static final FastThreadLocalWord<Isolate> IsolateTL = FastThreadLocalFactory.createWord("VMThreads.IsolateTL");
    /** The highest stack address. 0 if not available on this platform. */
    public static final FastThreadLocalWord<UnsignedWord> StackBase = FastThreadLocalFactory.createWord("VMThreads.StackBase");
    /**
     * The lowest stack address. Note that this value does not necessarily match the value that is
     * used for the stack overflow check.
     */
    public static final FastThreadLocalWord<UnsignedWord> StackEnd = FastThreadLocalFactory.createWord("VMThreads.StackEnd");
    /**
     * Tracks whether this thread was started by the current isolate or if it was an externally
     * started thread which was attached to the isolate. This distinction determines the teardown
     * process for the thread.
     */
    private static final FastThreadLocalBytes<Pointer> StartedByCurrentIsolate = FastThreadLocalFactory.createBytes(() -> 1, "VMThreads.StartedByCurrentIsolate");

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
    static void setTearingDown() {
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
    protected boolean initializeOnce() {
        return VMLockSupport.singleton().initialize();
    }

    /**
     * Must be called once during isolate teardown. Subclasses can perform destroying of native OS
     * resources. Please note that this method is not called until we fix GR-39879.
     */
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    protected boolean destroy() {
        return VMLockSupport.singleton().destroy();
    }

    /*
     * Stores the unaligned memory address returned by calloc, so that we can properly free the
     * memory again.
     */
    private static final FastThreadLocalWord<Pointer> unalignedIsolateThreadMemoryTL = FastThreadLocalFactory.createWord("VMThreads.unalignedIsolateThreadMemoryTL");

    /**
     * Allocate native memory for a {@link IsolateThread}. The returned memory must be initialized
     * to 0.
     */
    @Uninterruptible(reason = "Thread state not set up.")
    public IsolateThread allocateIsolateThread(int isolateThreadSize) {
        /*
         * We prefer to have the IsolateThread aligned on cache-line boundary, to avoid false
         * sharing with native memory allocated before it. But until we have the real cache line
         * size from the OS, we just use a hard-coded best guess. Using an inaccurate value does not
         * lead to correctness problems.
         */
        UnsignedWord alignment = WordFactory.unsigned(64);

        UnsignedWord memorySize = WordFactory.unsigned(isolateThreadSize).add(alignment);
        Pointer memory = UntrackedNullableNativeMemory.calloc(memorySize);
        if (memory.isNull()) {
            return WordFactory.nullPointer();
        }

        IsolateThread isolateThread = (IsolateThread) UnsignedUtils.roundUp(memory, alignment);
        unalignedIsolateThreadMemoryTL.set(isolateThread, memory);
        return isolateThread;
    }

    @Uninterruptible(reason = "Thread state no longer set up.")
    public void freeCurrentIsolateThread() {
        freeIsolateThread(CurrentIsolate.getCurrentThread());
        writeCurrentVMThread(WordFactory.nullPointer());
    }

    /** Free the native memory allocated by {@link #allocateIsolateThread}. */
    @Uninterruptible(reason = "Thread state no longer set up.")
    protected void freeIsolateThread(IsolateThread thread) {
        Pointer memory = unalignedIsolateThreadMemoryTL.get(thread);
        UntrackedNullableNativeMemory.free(memory);
    }

    /**
     * Report a fatal error to the user and exit. This method must not return.
     */
    @Uninterruptible(reason = "Unknown thread state.")
    public abstract void failFatally(int code, CCharPointer message);

    /**
     * Iteration of all {@link IsolateThread}s that are currently running. {@link #THREAD_MUTEX}
     * must be held when iterating the list.
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
        guaranteeOwnsThreadMutex("Threads mutex must be locked before accessing/iterating the thread list.");
        return firstThreadUnsafe();
    }

    /**
     * Like {@link #firstThread()} but without the check that {@link #THREAD_MUTEX} is locked by the
     * current thread. Only use this method if absolutely necessary (e.g., for printing diagnostics
     * on a fatal error).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static IsolateThread firstThreadUnsafe() {
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
    @Uninterruptible(reason = "Thread is not attached yet.")
    public int attachThread(IsolateThread thread, boolean startedByCurrentIsolate) {
        StartedByCurrentIsolate.getAddress().writeByte(0, (byte) (startedByCurrentIsolate ? 1 : 0));
        return attachThread(thread);
    }

    /* Needs to be protected due to legacy code. */
    @Uninterruptible(reason = "Thread is not attached yet.")
    protected int attachThread(IsolateThread thread) {
        assert StatusSupport.isStatusCreated(thread) : "Status should be initialized on creation.";
        OSThreadIdTL.set(thread, getCurrentOSThreadId());
        OSThreadHandleTL.set(thread, getCurrentOSThreadHandle());

        /* Set initial values for safepointRequested before making the thread visible. */
        assert !ThreadingSupportImpl.isRecurringCallbackRegistered(thread);
        Safepoint.setSafepointRequested(thread, Safepoint.THREAD_REQUEST_RESET);

        THREAD_MUTEX.lockNoTransition();
        try {
            nextTL.set(thread, head);
            head = thread;
            numAttachedThreads++;
            assert numAttachedThreads > 0;

            if (!wasStartedByCurrentIsolate(thread)) {
                /* Treat attached threads as non-daemon threads until we know better. */
                PlatformThreads.incrementNonDaemonThreads();
            }

            Heap.getHeap().attachThread(CurrentIsolate.getCurrentThread());
            /* On the initial transition to java code this thread should be synchronized. */
            ActionOnTransitionToJavaSupport.setSynchronizeCode(thread);
            StatusSupport.setStatusNative(thread);
            THREAD_LIST_CONDITION.broadcast();
        } finally {
            THREAD_MUTEX.unlock();
        }
        return CEntryPointErrors.NO_ERROR;
    }

    /**
     * Detaches the current thread from the isolate and frees the {@link IsolateThread} data
     * structure.
     */
    @Uninterruptible(reason = "IsolateThread will be freed.")
    public void detachCurrentThread() {
        threadExit();
        detachThread(CurrentIsolate.getCurrentThread(), true);
        writeCurrentVMThread(WordFactory.nullPointer());
    }

    /**
     * Detaches a thread from the isolate and frees the {@link IsolateThread} data structure.
     *
     * When modifying this method, please double-check if the handling of the last isolate thread
     * needs to be modified as well, see
     * {@link com.oracle.svm.core.graal.snippets.CEntryPointSnippets#tearDownIsolate}.
     */
    @Uninterruptible(reason = "IsolateThread will be freed. Holds the THREAD_MUTEX.")
    protected void detachThread(IsolateThread thread, boolean currentThread) {
        assert currentThread == (thread == CurrentIsolate.getCurrentThread());
        assert currentThread || VMOperation.isInProgressAtSafepoint();

        OSThreadHandle threadToCleanup = WordFactory.nullPointer();
        if (currentThread) {
            lockThreadMutexInNativeCode(false);
        }
        try {
            removeFromThreadList(thread);
            PlatformThreads.detach(thread);
            Heap.getHeap().detachThread(thread);

            /*
             * After detaching from the heap and removing the thread from the thread list, this
             * thread may only access image heap objects. It also must not execute any write
             * barriers.
             */
            OSThreadHandle threadHandle = getOSThreadHandle(thread);
            if (wasStartedByCurrentIsolate(thread)) {
                /* Some other thread will close the thread handle of this thread. */
                threadToCleanup = detachedOsThreadToCleanup.getAndSet(threadHandle);
            } else {
                /* Attached threads always close their own thread handle right away. */
                PlatformThreads.singleton().closeOSThreadHandle(threadHandle);
            }
        } finally {
            if (currentThread) {
                THREAD_MUTEX.unlock();
            }
        }

        /*
         * After unlocking the THREAD_MUTEX, only threads that were started by the current isolate
         * may still access the image heap (we guarantee that the image heap is not unmapped as long
         * as such threads are alive on the OS-level). Also note that the GC won't visit the stack
         * of this thread anymore.
         */
        cleanupExitedOsThread(threadToCleanup);
        freeIsolateThread(thread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean wasStartedByCurrentIsolate(IsolateThread thread) {
        return StartedByCurrentIsolate.getAddress(thread).readByte(0) != 0;
    }

    @Uninterruptible(reason = "Thread locks/holds the THREAD_MUTEX.", callerMustBe = true)
    static void lockThreadMutexInNativeCode() {
        lockThreadMutexInNativeCode(false);
    }

    @Uninterruptible(reason = "Thread locks/holds the THREAD_MUTEX.", callerMustBe = true)
    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode.")
    private static void lockThreadMutexInNativeCode(boolean unspecifiedOwner) {
        CFunctionPrologueNode.cFunctionPrologue(StatusSupport.STATUS_IN_NATIVE);
        lockThreadMutexInNativeCode0(unspecifiedOwner);
        CFunctionEpilogueNode.cFunctionEpilogue(StatusSupport.STATUS_IN_NATIVE);
    }

    @Uninterruptible(reason = "Must not stop while in native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static void lockThreadMutexInNativeCode0(boolean unspecifiedOwner) {
        if (unspecifiedOwner) {
            THREAD_MUTEX.lockNoTransitionUnspecifiedOwner();
        } else {
            THREAD_MUTEX.lockNoTransition();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void cleanupExitedOsThreads() {
        OSThreadHandle threadToCleanup = detachedOsThreadToCleanup.getAndSet(WordFactory.nullPointer());
        cleanupExitedOsThread(threadToCleanup);
    }

    /**
     * This builds a dependency chain: if the current thread (n) exits, then it is guaranteed that
     * the previous thread (n-1) exited on the operating-system level as well (because thread n
     * joins thread n-1).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void cleanupExitedOsThread(OSThreadHandle threadToCleanup) {
        if (threadToCleanup.isNonNull()) {
            joinNoTransition(threadToCleanup);
        }
    }

    @Uninterruptible(reason = "Thread is detaching and holds the THREAD_MUTEX.")
    private static void removeFromThreadList(IsolateThread thread) {
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
                // Set to the sentinel value denoting the thread is detached
                nextTL.set(thread, thread);
                numAttachedThreads--;
                assert numAttachedThreads >= 0;
                break;
            } else {
                previous = current;
                current = next;
            }
        }

        THREAD_LIST_CONDITION.broadcast();
    }

    @Uninterruptible(reason = "Called from uninterruptible code, but still safe at this point.", calleeMustBe = false)
    public void threadExit() {
        Thread javaThread = PlatformThreads.currentThread.get();
        if (javaThread != null) {
            PlatformThreads.exit(javaThread);
        }
        /* Only uninterruptible code may be executed from now on. */
        PlatformThreads.afterThreadExit(CurrentIsolate.getCurrentThread());
    }

    @Uninterruptible(reason = "Only uninterruptible code may be executed after VMThreads#threadExit.")
    public void waitUntilDetachedThreadsExitedOnOSLevel() {
        cleanupExitedOsThreads();
    }

    /**
     * Detaches all manually attached native threads, but not those threads that were launched from
     * Java, which must be notified to individually exit in the immediately following tear-down.
     *
     * We cannot clean up the threads we detach here because cleanup code needs to run in the
     * detaching thread itself. We assume that this is tolerable considering the immediately
     * following tear-down.
     */
    public static void detachAllThreadsExceptCurrentWithoutCleanupForTearDown() {
        DetachAllExternallyStartedThreadsExceptCurrentOperation vmOp = new DetachAllExternallyStartedThreadsExceptCurrentOperation();
        vmOp.enqueue();
    }

    /**
     * Executes a non-multithreading-safe low-level (i.e., non-Java-level) join operation on the
     * given native thread. If the thread hasn't yet exited on the operating system level, this
     * method blocks until the thread exits on the operating system level. After successfully
     * joining a thread, the operating system may free resources and recycle/reuse the given thread
     * id for other newly started threads.
     *
     * As this method is marked as uninterruptible, it may only be used for joining threads that
     * were already detached from SVM. Otherwise, this could result in deadlocks.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract void joinNoTransition(OSThreadHandle osThreadHandle);

    /**
     * Returns a platform-specific handle to the current thread. This handle can for example be used
     * for joining a thread. Depending on the platform, it can be necessary to explicitly free the
     * handle when it is no longer used. To avoid leaking resources, this method should therefore
     * only be called by {@link #attachThread}. All other places should access the thread local
     * {@link #OSThreadHandleTL} instead.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract OSThreadHandle getCurrentOSThreadHandle();

    /**
     * Returns a unique identifier for the current thread.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract OSThreadId getCurrentOSThreadId();

    /**
     * Puts this thread to sleep on the operating-system level and does not care about Java
     * semantics. May only be used in very specific situations, e.g., when printing diagnostics.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void nativeSleep(@SuppressWarnings("unused") int milliseconds) {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void yield() {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    // Should not be implemented and will be removed with GR-34388.
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean supportsNativeYieldAndSleep() {
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean verifyThreadIsAttached(IsolateThread thread) {
        return nextThread(thread) != thread;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean verifyIsCurrentThread(IsolateThread thread) {
        OSThreadId osThreadId = getCurrentOSThreadId();
        return OSThreadIdTL.get(thread).equal(osThreadId);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @SuppressFBWarnings(value = "UC", justification = "FB does not know that VMMutex objects are replaced, i.e., that the lock/unlock methods do not throw an error at run time.")
    public IsolateThread findIsolateThreadForCurrentOSThread(boolean inCrashHandler) {
        ThreadLookup threadLookup = ImageSingletons.lookup(ThreadLookup.class);
        ComparableWord identifier = threadLookup.getThreadIdentifier();

        /*
         * This code can execute during the prologue of a crash handler for a thread that already
         * owns the lock. Trying to reacquire the lock here would result in a deadlock.
         */
        boolean needsLock = !inCrashHandler;
        if (needsLock) {
            THREAD_MUTEX.lockNoTransitionUnspecifiedOwner();
        }
        try {
            IsolateThread thread;
            for (thread = firstThreadUnsafe(); thread.isNonNull() && threadLookup.matchesThread(thread, identifier); thread = nextThread(thread)) {
            }
            return thread;
        } finally {
            if (needsLock) {
                THREAD_MUTEX.unlockNoTransitionUnspecifiedOwner();
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static OSThreadHandle getOSThreadHandle(IsolateThread isolateThread) {
        return OSThreadHandleTL.get(isolateThread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static OSThreadId getOSThreadId(IsolateThread isolateThread) {
        return OSThreadIdTL.get(isolateThread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void guaranteeOwnsThreadMutex(String message) {
        THREAD_MUTEX.guaranteeIsOwner(message);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void guaranteeOwnsThreadMutex(String message, boolean allowUnspecifiedOwner) {
        THREAD_MUTEX.guaranteeIsOwner(message, allowUnspecifiedOwner);
    }

    public static boolean printLocationInfo(Log log, UnsignedWord value, boolean allowUnsafeOperations) {
        if (!allowUnsafeOperations && !VMOperation.isInProgressAtSafepoint()) {
            /*
             * Iterating the threads or accessing thread locals of other threads is unsafe if we are
             * outside a VM operation because the IsolateThread data structure could be freed at any
             * time (we can't use any locking to prevent races).
             */
            return false;
        }

        for (IsolateThread thread = firstThreadUnsafe(); thread.isNonNull(); thread = nextThread(thread)) {
            if (thread.equal(value)) {
                log.string("is a thread");
                return true;
            }

            UnsignedWord stackBase = StackBase.get(thread);
            UnsignedWord stackEnd = StackEnd.get(thread);
            if (value.belowThan(stackBase) && value.aboveOrEqual(stackEnd)) {
                log.string("points into the stack for thread ").zhex(thread);
                return true;
            }

            int sizeOfThreadLocals = ImageSingletons.lookup(VMThreadLocalSupport.class).vmThreadSize;
            UnsignedWord endOfThreadLocals = ((UnsignedWord) thread).add(sizeOfThreadLocals);
            if (value.aboveOrEqual((UnsignedWord) thread) && value.belowThan(endOfThreadLocals)) {
                log.string("points into the thread locals for thread ").zhex(thread);
                return true;
            }
        }
        return false;
    }

    private static class DetachAllExternallyStartedThreadsExceptCurrentOperation extends JavaVMOperation {
        DetachAllExternallyStartedThreadsExceptCurrentOperation() {
            super(VMOperationInfos.get(DetachAllExternallyStartedThreadsExceptCurrentOperation.class, "Detach all externally started threads except current", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate() {
            IsolateThread currentThread = CurrentIsolate.getCurrentThread();
            IsolateThread thread = firstThread();
            while (thread.isNonNull()) {
                IsolateThread next = nextThread(thread);
                if (thread.notEqual(currentThread) && !wasStartedByCurrentIsolate(thread)) {
                    /*
                     * The code below is similar to VMThreads.detachCurrentThread() except that it
                     * doesn't call VMThreads.threadExit(). We assume that this is tolerable
                     * considering the immediately following tear-down.
                     */
                    VMThreads.singleton().detachThread(thread, false);
                }
                thread = next;
            }
        }
    }

    /*
     * Access to platform-specific implementations.
     */

    /** A thread-local enum giving the thread status of a VMThread. And supporting methods. */
    public static class StatusSupport {

        /** The status of a {@link IsolateThread}. */
        public static final FastThreadLocalInt statusTL = FastThreadLocalFactory.createInt("StatusSupport.statusTL").setMaxOffset(FastThreadLocal.FIRST_CACHE_LINE);

        /** An illegal thread state for places where we need to pass a value. */
        public static final int STATUS_ILLEGAL = -1;
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
        /** The thread is running in trusted native code that was linked into the image. */
        public static final int STATUS_IN_VM = STATUS_IN_NATIVE + 1;
        private static final int MAX_STATUS = STATUS_IN_VM;

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
                case STATUS_IN_VM:
                    return "STATUS_IN_VM";
                default:
                    return "STATUS error";
            }
        }

        /* Access methods to treat VMThreads.statusTL as a volatile int. */

        /** For debugging. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static String getStatusString(IsolateThread vmThread) {
            return statusToString(statusTL.getVolatile(vmThread));
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int getStatusVolatile(IsolateThread vmThread) {
            return statusTL.getVolatile(vmThread);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int getStatusVolatile() {
            return statusTL.getVolatile();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void setStatusNative() {
            statusTL.setVolatile(STATUS_IN_NATIVE);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void setStatusNative(IsolateThread vmThread) {
            statusTL.setVolatile(vmThread, STATUS_IN_NATIVE);
        }

        /** There is no unguarded change to safepoint. */
        public static boolean compareAndSetNativeToSafepoint(IsolateThread vmThread) {
            return statusTL.compareAndSet(vmThread, STATUS_IN_NATIVE, STATUS_IN_SAFEPOINT);
        }

        /** An <em>unguarded</em> transition to Java. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void setStatusJavaUnguarded() {
            statusTL.setVolatile(STATUS_IN_JAVA);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void setStatusVM() {
            statusTL.setVolatile(STATUS_IN_VM);
        }

        /** A guarded transition from native to another status. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean compareAndSetNativeToNewStatus(int newStatus) {
            return statusTL.compareAndSet(STATUS_IN_NATIVE, newStatus);
        }

        /*
         * When querying and checking the thread status, be careful that the status is read only
         * once. Reading the status multiple times is prone to race conditions. For example, the
         * condition 'isStatusSafepoint() || isStatusNative()' could return false if another thread
         * requests a safepoint after the first check was already executed. The condition
         * 'isStatusNative() || isStatusSafepoint()' could return false if the safepoint is released
         * after the first condition was checked.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isStatusCreated(IsolateThread vmThread) {
            return (statusTL.getVolatile(vmThread) == STATUS_CREATED);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isStatusNativeOrSafepoint(IsolateThread vmThread) {
            int status = statusTL.getVolatile(vmThread);
            return status == STATUS_IN_NATIVE || status == STATUS_IN_SAFEPOINT;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isStatusNativeOrSafepoint() {
            int status = statusTL.getVolatile();
            return status == STATUS_IN_NATIVE || status == STATUS_IN_SAFEPOINT;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isStatusVM() {
            return statusTL.getVolatile() == STATUS_IN_VM;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isStatusJava() {
            return statusTL.getVolatile() == STATUS_IN_JAVA;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void assertStatusJava() {
            String msg = "Thread status must be 'Java'.";
            if (GraalDirectives.inIntrinsic()) {
                if (ReplacementsUtil.REPLACEMENTS_ASSERTIONS_ENABLED) {
                    AssertionNode.dynamicAssert(isStatusJava(), msg);
                }
            } else {
                assert isStatusJava() : msg;
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void assertStatusNativeOrSafepoint() {
            String msg = "Thread status must be 'native' or 'safepoint'.";
            if (GraalDirectives.inIntrinsic()) {
                if (ReplacementsUtil.REPLACEMENTS_ASSERTIONS_ENABLED) {
                    AssertionNode.dynamicAssert(isStatusNativeOrSafepoint(), msg);
                }
            } else {
                assert isStatusNativeOrSafepoint() : msg;
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void assertStatusVM() {
            String msg = "Thread status must be 'VM'.";
            if (GraalDirectives.inIntrinsic()) {
                if (ReplacementsUtil.REPLACEMENTS_ASSERTIONS_ENABLED) {
                    AssertionNode.dynamicAssert(isStatusVM(), msg);
                }
            } else {
                assert isStatusVM() : msg;
            }
        }

        public static boolean isValidStatus(int status) {
            return status > STATUS_ILLEGAL && status <= MAX_STATUS;
        }

        public static int getNewThreadStatus(CFunction.Transition transition) {
            switch (transition) {
                case NO_TRANSITION:
                    return StatusSupport.STATUS_ILLEGAL;
                case TO_NATIVE:
                    return StatusSupport.STATUS_IN_NATIVE;
                default:
                    throw VMError.shouldNotReachHere("Unknown transition type " + transition);
            }
        }

        public static int getNewThreadStatus(CFunctionOptions.Transition transition) {
            switch (transition) {
                case TO_VM:
                    return StatusSupport.STATUS_IN_VM;
                default:
                    throw VMError.shouldNotReachHere("Unknown transition type " + transition);
            }
        }
    }

    public static class SafepointBehavior {
        /** Determines how this thread interacts with the safepoint handling. */
        private static final FastThreadLocalInt safepointBehaviorTL = FastThreadLocalFactory.createInt("StatusSupport.safepointBehaviorTL");

        /** The thread will freeze as soon as possible if a safepoint is requested. */
        public static final int ALLOW_SAFEPOINT = 0;

        /**
         * The thread won't freeze at a safepoint, and will actively prevent the VM from reaching a
         * safepoint (regardless of the thread status).
         */
        public static final int PREVENT_VM_FROM_REACHING_SAFEPOINT = 1;

        /**
         * The thread won't freeze at a safepoint and the safepoint handling will ignore the thread.
         * So, the VM will be able to reach a safepoint regardless of the status of this thread.
         */
        public static final int THREAD_CRASHED = 2;

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean ignoresSafepoints() {
            return safepointBehaviorTL.getVolatile() != ALLOW_SAFEPOINT;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean ignoresSafepoints(IsolateThread vmThread) {
            return safepointBehaviorTL.getVolatile(vmThread) != ALLOW_SAFEPOINT;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int getSafepointBehaviorVolatile(IsolateThread vmThread) {
            return safepointBehaviorTL.getVolatile(vmThread);
        }

        /**
         * Changes the safepoint behavior so that this thread won't freeze at a safepoint. The
         * thread will also actively prevent the VM from reaching a safepoint (regardless of its
         * thread status).
         * 
         * NOTE: Be careful with this method and make sure that this thread does not allocate any
         * Java objects as this could result deadlocks. This method will only prevent safepoints
         * reliably if it is called from a thread with {@link StatusSupport#STATUS_IN_JAVA}.
         */
        @Uninterruptible(reason = "May only be called from uninterruptible code to prevent races with the safepoint handling.", callerMustBe = true)
        public static void preventSafepoints() {
            // It would be nice if we could retire the TLAB but that wouldn't work reliably.
            safepointBehaviorTL.setVolatile(PREVENT_VM_FROM_REACHING_SAFEPOINT);
        }

        /**
         * Marks the thread as crashed. This method may only be used in places where it is not
         * possible to safely detach a thread.
         * 
         * Changes the safepoint behavior so that this thread won't freeze at a safepoint. The
         * safepoint handling will ignore the thread so that the VM can reach a safepoint regardless
         * of the status of this thread.
         *
         * NOTE: Be careful with this. If a thread is ignored by the safepoint handling, it means
         * that it can continue executing while a safepoint (and therefore a GC) is in progress. So,
         * make sure that this thread does not allocate or access any movable heap objects (even
         * executing write barriers can already cause issues).
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void markThreadAsCrashed() {
            // It would be nice if we could retire the TLAB here but that wouldn't work reliably.
            safepointBehaviorTL.setVolatile(THREAD_CRASHED);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isCrashedThread(IsolateThread thread) {
            return safepointBehaviorTL.getVolatile(thread) == THREAD_CRASHED;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static String toString(int safepointBehavior) {
            switch (safepointBehavior) {
                case ALLOW_SAFEPOINT:
                    return "ALLOW_SAFEPOINT";
                case PREVENT_VM_FROM_REACHING_SAFEPOINT:
                    return "PREVENT_VM_FROM_REACHING_SAFEPOINT";
                case THREAD_CRASHED:
                    return "THREAD_CRASHED";
                default:
                    return "Invalid safepoint behavior";
            }
        }
    }

    /**
     * A thread-local enum conveying any actions needed before thread begins executing Java code.
     */
    public static class ActionOnTransitionToJavaSupport {

        /** The actions to be performed. */
        private static final FastThreadLocalInt actionTL = FastThreadLocalFactory.createInt("ActionOnTransitionToJavaSupport.actionTL");

        /** The thread does not need to take any action. */
        private static final int NO_ACTION = 0;
        /** Code synchronization should be performed due to newly installed code. */
        private static final int SYNCHRONIZE_CODE = NO_ACTION + 1;

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isActionPending() {
            return actionTL.getVolatile() != NO_ACTION;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isSynchronizeCode() {
            return actionTL.getVolatile() == SYNCHRONIZE_CODE;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void clearActions() {
            actionTL.setVolatile(NO_ACTION);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static void setSynchronizeCode(IsolateThread vmThread) {
            assert StatusSupport.isStatusCreated(vmThread) || VMOperation.isInProgressAtSafepoint() : "Invariant to avoid races between setting and clearing.";
            actionTL.setVolatile(vmThread, SYNCHRONIZE_CODE);
        }

        public static void requestAllThreadsSynchronizeCode() {
            final IsolateThread myself = CurrentIsolate.getCurrentThread();
            for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                if (myself == vmThread) {
                    continue;
                }
                setSynchronizeCode(vmThread);
            }
        }
    }

    public interface OSThreadHandle extends PointerBase {
    }

    public interface OSThreadId extends PointerBase {
    }

    public static class ThreadLookup {
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public ComparableWord getThreadIdentifier() {
            return VMThreads.singleton().getCurrentOSThreadId();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean matchesThread(IsolateThread thread, ComparableWord identifier) {
            return OSThreadIdTL.get(thread).notEqual(identifier);
        }
    }
}

@AutomaticallyRegisteredFeature
class ThreadLookupFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (!ImageSingletons.contains(VMThreads.ThreadLookup.class)) {
            ImageSingletons.add(VMThreads.ThreadLookup.class, new VMThreads.ThreadLookup());
        }
    }
}
