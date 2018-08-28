/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

//Checkstyle: allow reflection

import static com.oracle.svm.core.SubstrateOptions.MultiThreaded;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readReturnAddress;

import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointContext;

import com.oracle.svm.core.MonitorSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.FeebleReferenceList;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.JDK9OrLater;
import com.oracle.svm.core.jdk.Package_jdk_internal_misc;
import com.oracle.svm.core.jdk.StackTraceBuilder;
import com.oracle.svm.core.jdk.Target_jdk_internal_misc_VM;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicReference;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.XOptions;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.ParkEvent.WaitResult;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

public abstract class JavaThreads {

    @Fold
    public static JavaThreads singleton() {
        return ImageSingletons.lookup(JavaThreads.class);
    }

    /**
     * The {@link java.lang.Thread} for the {@link IsolateThread}. It can be null if the
     * {@link Thread} has never been accessed. The only possible transition is from null to the
     * {@link Thread}, after that initialization (which must use atomic operations) the value never
     * changes again. Therefore, reads do not need to be volatile reads.
     */
    protected static final FastThreadLocalObject<Thread> currentThread = FastThreadLocalFactory.createObject(Thread.class);

    protected final AtomicLong totalThreads = new AtomicLong();
    protected final AtomicInteger peakThreads = new AtomicInteger();
    protected final AtomicInteger liveThreads = new AtomicInteger();
    protected final AtomicInteger daemonThreads = new AtomicInteger();
    protected final AtomicInteger nonDaemonThreads = new AtomicInteger();

    /** The group we use for VM threads. */
    final ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
    /*
     * By using the current thread group as the SVM root group we are preserving runtime environment
     * of a generated image, which is necessary as the current thread group is available to static
     * initializers and we are allowing ThreadGroups and unstarted Threads in the image heap.
     *
     * There are tests in place to make sure that we are using the JVM's "main" group during image
     * generation and that we are not leaking any thread groups.
     */

    /**
     * The single thread object we use when building a VM without
     * {@link SubstrateOptions#MultiThreaded thread support}.
     */
    final Thread singleThread = new Thread("SVM");

    /** For Thread.nextThreadID(). */
    final AtomicLong threadSeqNumber = new AtomicLong();

    /** For Thread.nextThreadNum(). */
    final AtomicInteger threadInitNumber = new AtomicInteger();

    /* Accessor functions for private fields of java.lang.Thread that we alias or inject. */

    @SuppressFBWarnings(value = "BC", justification = "Cast for @TargetClass")
    static Thread fromTarget(Target_java_lang_Thread thread) {
        return Thread.class.cast(thread);
    }

    @SuppressFBWarnings(value = "BC", justification = "Cast for @TargetClass")
    private static Target_java_lang_Thread toTarget(Thread thread) {
        return Target_java_lang_Thread.class.cast(thread);
    }

    public static int getThreadStatus(Thread thread) {
        return toTarget(thread).threadStatus;
    }

    public static void setThreadStatus(Thread thread, int threadStatus) {
        toTarget(thread).threadStatus = threadStatus;
    }

    protected static AtomicReference<ParkEvent> getUnsafeParkEvent(Thread thread) {
        return toTarget(thread).unsafeParkEvent;
    }

    protected static AtomicReference<ParkEvent> getSleepParkEvent(Thread thread) {
        return toTarget(thread).sleepParkEvent;
    }

    /* End of accessor functions. */

    public Thread fromVMThread(IsolateThread vmThread) {
        return currentThread.get(vmThread);
    }

    @Uninterruptible(reason = "Called from uninterruptible codet st.", calleeMustBe = false)
    public static Thread getCurrentThread() {
        return currentThread.get();
    }

    public Thread createIfNotExisting(IsolateThread vmThread) {
        if (!MultiThreaded.getValue()) {
            return singleThread;
        }

        Thread result = currentThread.get(vmThread);
        if (result == null) {
            result = createThread(vmThread);
        }
        return result;
    }

    public long getTotalThreads() {
        return totalThreads.get();
    }

    public int getPeakThreads() {
        return peakThreads.get();
    }

    public int getLiveThreads() {
        return liveThreads.get();
    }

    public int getDaemonThreads() {
        return daemonThreads.get();
    }

    @SuppressFBWarnings(value = "BC", justification = "Cast for @TargetClass")
    static Target_java_lang_ThreadGroup toTarget(ThreadGroup threadGroup) {
        return Target_java_lang_ThreadGroup.class.cast(threadGroup);
    }

    /**
     * Joins all non-daemon threads. If the current thread is itself a non-daemon thread, it does
     * not attempt to join itself.
     */
    @SuppressWarnings("try")
    public void joinAllNonDaemons() {
        int expected = 0;
        if (currentThread.get() != null && !currentThread.get().isDaemon()) {
            expected = 1;
        }
        try (VMMutex ignored = VMThreads.THREAD_MUTEX.lock()) {
            while (nonDaemonThreads.get() > expected) {
                VMThreads.THREAD_LIST_CONDITION.block();
            }
        }
    }

    @NeverInline("Truffle compilation must not inline this method")
    private static Thread createThread(IsolateThread isolateThread) {
        /*
         * Either the main thread, or VMThread was started a different way. Create a new Thread
         * object and remember it for future calls, so that currentThread always returns the same
         * object.
         */

        // The thread has not been launched as java.lang.Thread, so we consider it a daemon thread.
        boolean isDaemon = true;

        final Thread thread = JavaThreads.fromTarget(new Target_java_lang_Thread(null, null, isDaemon));
        if (!assignJavaThread(isolateThread, thread, true)) {
            return currentThread.get(isolateThread);
        }
        return thread;
    }

    /** Signal that a thread was started by calling Thread.start(). */
    public void signalNonDaemonThreadStart() {
        nonDaemonThreads.incrementAndGet();
    }

    /**
     * Create a {@link Thread} object for the current thread. The current thread must have already
     * been attached {@link VMThreads} as an {@link IsolateThread}.
     *
     * @param name the thread's name, or {@code null} for a default name.
     * @param group the thread group, or {@code null} for the default thread group.
     * @return true if successful; false if a {@link Thread} object has already been assigned.
     */
    public boolean assignJavaThread(String name, ThreadGroup group, boolean asDaemon) {
        final Thread thread = JavaThreads.fromTarget(new Target_java_lang_Thread(name, group, asDaemon));
        return assignJavaThread(CEntryPointContext.getCurrentIsolateThread(), thread, true);
    }

    /**
     * Assign a {@link Thread} object to the current thread, which must have already been attached
     * {@link VMThreads} as an {@link IsolateThread}.
     *
     * The manuallyStarted parameter is true if this thread was started directly by calling
     * assignJavaThread(Thread). It is false when the thread is started using
     * PosixJavaThreads.pthreadStartRoutine, e.g., called from PosixJavaThreads.start0.
     *
     * @return true if successful; false if a {@link Thread} object has already been assigned.
     */
    public boolean assignJavaThread(Thread thread, boolean manuallyStarted) {
        return assignJavaThread(CEntryPointContext.getCurrentIsolateThread(), thread, manuallyStarted);
    }

    private static boolean assignJavaThread(IsolateThread isolateThread, Thread thread, boolean manuallyStarted) {
        if (!currentThread.compareAndSet(isolateThread, null, thread)) {
            return false;
        }
        ThreadGroup group = thread.getThreadGroup();
        toTarget(group).addUnstarted();
        toTarget(group).add(thread);
        if (!thread.isDaemon() && manuallyStarted) {
            assert isolateThread.equal(CEntryPointContext.getCurrentIsolateThread()) : "Non-daemon threads must call this method themselves, or they can detach incompletely in a race";
            singleton().nonDaemonThreads.incrementAndGet();
        }
        return true;
    }

    /**
     * Tear down the VMThreads.
     * <p>
     * This is called from an {@link CEntryPoint} exit action.
     * <p>
     * Returns true if the VM has been torn down, false otherwise.
     */
    public boolean tearDownVM() {
        /* If the VM is single-threaded then this is the last (and only) thread. */
        if (!MultiThreaded.getValue()) {
            return true;
        }
        /* Tell all the threads that the VM is being torn down. */
        return tearDownIsolateThreads();
    }

    private static void detachParkEvent(AtomicReference<ParkEvent> ref) {
        ParkEvent event = ref.get();
        if (event != null) {
            ref.set(null);
            ParkEvent.release(event);
        }
    }

    /**
     * Detach the provided Java thread. Note that the Java thread might not have been created, in
     * which case it is null and we have nothing to do.
     */
    public static void detachThread(IsolateThread vmThread) {
        /*
         * Caller must hold the lock or I, and my callees, would have to be annotated as
         * Uninterruptible.
         */
        VMThreads.THREAD_MUTEX.assertIsLocked("Should hold the VMThreads mutex.");
        // Disable thread-local allocation for this thread.
        Heap.getHeap().disableAllocation(vmThread);

        // Detach ParkEvents for this thread, if any.

        final Thread thread = currentThread.get(vmThread);
        if (thread == null) {
            return;
        }

        detachParkEvent(getUnsafeParkEvent(thread));
        detachParkEvent(getSleepParkEvent(thread));
        if (!thread.isDaemon()) {
            singleton().nonDaemonThreads.decrementAndGet();
        }
    }

    /** Have each thread, except this one, tear itself down. */
    private static boolean tearDownIsolateThreads() {
        final Log trace = Log.noopLog().string("[JavaThreads.tearDownIsolateThreads:").newline().flush();
        /* Prevent new threads from starting. */
        VMThreads.singleton().setTearingDown();
        /* Make a list of all the threads. */
        final ArrayList<Thread> threadList = new ArrayList<>();
        ThreadListOperation operation = new ThreadListOperation(threadList);
        operation.enqueue();
        /* Interrupt the other threads. */
        for (Thread thread : threadList) {
            if (thread == Thread.currentThread()) {
                continue;
            }
            if (thread != null) {
                trace.string("  interrupting: ").string(thread.getName()).newline().flush();
                thread.interrupt();
            }
        }
        final boolean result = waitForTearDown();
        trace.string("  returns: ").bool(result).string("]").newline().flush();
        return result;
    }

    /** Wait (im)patiently for the VMThreads list to drain. */
    private static boolean waitForTearDown() {
        final Log trace = Log.noopLog().string("[JavaThreads.waitForTearDown:").newline();
        final long warningNanos = SubstrateOptions.getTearDownWarningNanos();
        final String warningMessage = "JavaThreads.waitForTearDown is taking too long.";
        final long failureNanos = SubstrateOptions.getTearDownFailureNanos();
        final String failureMessage = "JavaThreads.waitForTearDown took too long.";
        final long startNanos = System.nanoTime();
        long loopNanos = startNanos;
        final AtomicBoolean printLaggards = new AtomicBoolean(false);
        final Log counterLog = ((warningNanos == 0) ? trace : Log.log());
        final VMThreadCounterOperation operation = new VMThreadCounterOperation(counterLog, printLaggards);

        for (; /* return */;) {
            final long previousLoopNanos = loopNanos;
            operation.enqueue();
            if (operation.getCount() == 1) {
                /* If I am the only thread, then the VM is ready to be torn down. */
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
         *
         * Checkstyle: allow synchronization
         */
        synchronized (thread) { // Checkstyle: disallow synchronization
            thread.notifyAll();
        }
    }

    protected abstract void start0(Thread thread, long stackSize);

    protected abstract void setNativeName(String name);

    protected abstract void yield();

    protected static void interruptVMCondVars() {
        /*
         * On Thread.interrupt, notify anyone who is waiting on a VMCondition (on the
         * VMThreads.THREAD_MUTEX. Notify in a VMOperation so I only have to grab the VMThreads
         * mutex once.
         */
        VMOperation.enqueueBlockingNoSafepoint("Util_java_lang_Thread.notifyVMMutexConditionsOnThreadInterrupt()", JavaThreads::interruptUnderVMMutex);
    }

    /** The list of methods to be called under the VMThreads mutex when interrupting a thread. */
    private static void interruptUnderVMMutex() {
        VMThreads.THREAD_MUTEX.guaranteeIsLocked("Should hold VMThreads lock when interrupting.");
        FeebleReferenceList.signalWaiters();
    }

    static StackTraceElement[] getStackTrace(Thread thread) {
        StackTraceElement[][] result = new StackTraceElement[1][0];
        VMOperation.enqueueBlockingSafepoint("getStackTrace", () -> {
            for (IsolateThread cur = VMThreads.firstThread(); cur.isNonNull(); cur = VMThreads.nextThread(cur)) {
                if (JavaThreads.singleton().fromVMThread(cur) == thread) {
                    result[0] = getStackTrace(cur);
                    break;
                }
            }
        });
        return result[0];
    }

    static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        Map<Thread, StackTraceElement[]> result = new HashMap<>();
        VMOperation.enqueueBlockingSafepoint("getAllStackTraces", () -> {
            for (IsolateThread cur = VMThreads.firstThread(); cur.isNonNull(); cur = VMThreads.nextThread(cur)) {
                result.put(JavaThreads.singleton().createIfNotExisting(cur), getStackTrace(cur));
            }
        });
        return result;
    }

    private static StackTraceElement[] getStackTrace(IsolateThread thread) {
        if (thread == CEntryPointContext.getCurrentIsolateThread()) {
            /*
             * Internal frames from the VMOperation handling show up in the stack traces, but we are
             * OK with that.
             */
            StackTraceBuilder stackTraceBuilder = new StackTraceBuilder(false);
            JavaStackWalker.walkCurrentThread(readCallerStackPointer(), readReturnAddress(), stackTraceBuilder);
            return stackTraceBuilder.getTrace();
        } else {
            StackTraceBuilder stackTraceBuilder = new StackTraceBuilder(false);
            JavaStackWalker.walkThread(thread, stackTraceBuilder);
            return stackTraceBuilder.getTrace();
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
            /* Checkstyle: stop (printStackTrace below is going to write to System.err too). */
            System.err.print("Exception in thread \"" + Thread.currentThread().getName() + "\" ");
            // Checkstyle: resume
            throwable.printStackTrace();
        }
    }

    /** Initialize thread ID and autonumber sequences in the image heap. */
    @AutomaticFeature
    private static class SequenceInitializingFeature implements Feature {

        private final CopyOnWriteArraySet<Thread> collectedThreads = new CopyOnWriteArraySet<>();

        private final MethodHandle threadSeqNumberMH = createFieldMH(Thread.class, "threadSeqNumber");
        private final MethodHandle threadInitNumberMH = createFieldMH(Thread.class, "threadInitNumber");

        @Override
        public void duringSetup(DuringSetupAccess access) {
            access.registerObjectReplacer(this::collectThreads);
        }

        private Object collectThreads(Object original) {
            if (original instanceof Thread) {
                collectedThreads.add((Thread) original);
            }
            return original;
        }

        @Override
        public void beforeCompilation(BeforeCompilationAccess access) {
            /*
             * If there are unstarted threads in the image heap, initialize image version of both
             * sequences with current values. Otherwise, they'll be restarted from 0.
             */
            if (!collectedThreads.isEmpty()) {
                try {
                    JavaThreads.singleton().threadSeqNumber.set((long) threadSeqNumberMH.invokeExact());
                    JavaThreads.singleton().threadInitNumber.set((int) threadInitNumberMH.invokeExact());
                } catch (Throwable t) {
                    throw VMError.shouldNotReachHere(t);
                }
            }
        }

        private static MethodHandle createFieldMH(Class<?> declaringClass, String fieldName) {
            try {
                Field field = declaringClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return MethodHandles.lookup().unreflectGetter(field);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere(t);
            }
        }
    }
}

@TargetClass(Thread.class)
@SuppressWarnings({"unused"})
final class Target_java_lang_Thread {

    /** Every thread has a boolean for noting whether this thread is interrupted. */
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    volatile boolean interrupted;

    /**
     * Every thread has a {@link ParkEvent} for {@link sun.misc.Unsafe#park} and
     * {@link sun.misc.Unsafe#unpark}. Lazily initialized.
     */
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = AtomicReference.class)//
    AtomicReference<ParkEvent> unsafeParkEvent;

    /** Every thread has a {@link ParkEvent} for {@link Thread#sleep}. Lazily initialized. */
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = AtomicReference.class)//
    AtomicReference<ParkEvent> sleepParkEvent;

    @Alias//
    ClassLoader contextClassLoader;

    @Alias//
    volatile String name;

    @Alias//
    int priority;

    /* Whether or not the thread is a daemon . */
    @Alias//
    boolean daemon;

    /* What will be run. */
    @Alias//
    Runnable target;

    /* The group of this thread */
    @Alias//
    ThreadGroup group;

    /*
     * The requested stack size for this thread, or 0 if the creator did not specify a stack size.
     * It is up to the VM to do whatever it likes with this number; some VMs will ignore it.
     */
    @Alias//
    long stackSize;

    /* Thread ID */
    @Alias//
    long tid;

    /** We have our own atomic sequence numbers in {@link JavaThreads}. */
    @Delete//
    static long threadSeqNumber;
    @Delete//
    static int threadInitNumber;

    @Alias//
    public volatile int threadStatus;

    @Alias//
    private /* final */ Object blockerLock;

    @Alias
    native void setPriority(int newPriority);

    @Substitute
    public ClassLoader getContextClassLoader() {
        return contextClassLoader;
    }

    @Substitute
    public void setContextClassLoader(ClassLoader cl) {
        contextClassLoader = cl;
    }

    /** Replace "synchronized" modifier with delegation to an atomic increment. */
    @Substitute
    static long nextThreadID() {
        return JavaThreads.singleton().threadSeqNumber.incrementAndGet();
    }

    /** Replace "synchronized" modifier with delegation to an atomic increment. */
    @Substitute
    private static int nextThreadNum() {
        return JavaThreads.singleton().threadInitNumber.getAndIncrement();
    }

    @Alias
    public native void exit();

    Target_java_lang_Thread(String withName, ThreadGroup withGroup, boolean asDaemon) {
        /*
         * Raw creation of a thread without calling init(). Used to create a Thread object for an
         * already running thread.
         */

        this.unsafeParkEvent = new AtomicReference<>();
        this.sleepParkEvent = new AtomicReference<>();

        tid = nextThreadID();
        threadStatus = ThreadStatus.RUNNABLE;
        name = (withName != null) ? withName : ("System-" + nextThreadNum());
        group = (withGroup != null) ? withGroup : JavaThreads.singleton().rootGroup;
        priority = Thread.NORM_PRIORITY;
        blockerLock = new Object();
        daemon = asDaemon;
    }

    @Substitute
    static Thread currentThread() {
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            return JavaThreads.singleton().singleThread;
        }
        IsolateThread vmThread = CEntryPointContext.getCurrentIsolateThread();
        return JavaThreads.singleton().createIfNotExisting(vmThread);
    }

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private void init(ThreadGroup groupArg, Runnable targetArg, String nameArg, long stackSizeArg) {
        Util_java_lang_Thread.initialize(this, groupArg, targetArg, nameArg, stackSizeArg);
    }

    @Substitute
    @TargetElement(onlyWith = JDK9OrLater.class)
    private Target_java_lang_Thread(ThreadGroup g, Runnable target, String name, long stackSize) {
        Util_java_lang_Thread.initialize(this, g, target, name, stackSize);
    }

    @Substitute
    private void start0() {
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            throw VMError.unsupportedFeature("Single-threaded VM cannot create new threads");
        }

        /* Choose a stack size based on parameters, command line flags, and system restrictions. */
        long chosenStackSize = 0L;
        if (stackSize != 0) {
            /* If the user set a thread stack size at thread creation, then use that. */
            chosenStackSize = stackSize;
        } else {
            /* If the user set a thread stack size on the command line, then use that. */
            final int defaultThreadStackSize = (int) XOptions.getXss().getValue();
            if (defaultThreadStackSize != 0L) {
                chosenStackSize = defaultThreadStackSize;
            }
        }
        /*
         * The threadStatus must be set to RUNNABLE by the parent thread and before the child thread
         * starts because we are creating child threads asynchronously (there is no coordination
         * between parent and child threads).
         *
         * Otherwise, a call to Thread.join() in the parent thread could succeed even before the
         * child thread starts, or it could hang in case that the child thread is already dead.
         */
        threadStatus = ThreadStatus.RUNNABLE;
        JavaThreads.singleton().start0(JavaThreads.fromTarget(this), chosenStackSize);
    }

    @Substitute
    private long getId() {
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            return 1;
        }

        return tid;
    }

    @Substitute
    private Thread.State getState() {
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            if (JavaThreads.fromTarget(this) == JavaThreads.singleton().singleThread) {
                return Thread.State.RUNNABLE;
            } else {
                return Thread.State.NEW;
            }
        }

        return Target_jdk_internal_misc_VM.toThreadState(threadStatus);
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    protected void setNativeName(String name) {
        JavaThreads.singleton().setNativeName(name);
    }

    @Substitute
    private void setPriority0(int priority) {
    }

    @Substitute
    private boolean isInterrupted(boolean clearInterrupted) {
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            return false;
        }

        final boolean result = interrupted;
        if (clearInterrupted) {
            interrupted = false;
        }
        return result;
    }

    @Substitute
    void interrupt0() {
        /* Set the interrupt status of the thread. */
        interrupted = true;

        if (!SubstrateOptions.MultiThreaded.getValue()) {
            /* If the VM is single-threaded, this thread can not be blocked. */
            return;
        }

        // Cf. os::interrupt(Thread*) from HotSpot, which unparks all of:
        // (1) thread->_SleepEvent,
        // (2) ((JavaThread*)thread)->parker()
        // (3) thread->_ParkEvent
        SleepSupport.interrupt(JavaThreads.fromTarget(this));
        UnsafeParkSupport.unpark(JavaThreads.fromTarget(this));
        /* Interrupt anyone waiting on a VMCondVar. */
        JavaThreads.interruptVMCondVars();
    }

    @Substitute
    private boolean isAlive() {
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            return JavaThreads.fromTarget(this) == JavaThreads.singleton().singleThread;
        }

        // There are fewer cases that are not-alive.
        return !(threadStatus == ThreadStatus.NEW || threadStatus == ThreadStatus.TERMINATED);
    }

    @Substitute
    private static void yield() {
        JavaThreads.singleton().yield();
    }

    @Substitute
    private static void sleep(long millis) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        WaitResult sleepResult = SleepSupport.sleep(TimeUtils.millisToNanos(millis));
        /*
         * If the sleep did not time out, I was interrupted. The interrupted flag of the thread must
         * be cleared when an InterruptedException is thrown (see JavaDoc of Thread.sleep), so we
         * call Thread.interrupted() unconditionally.
         */
        boolean interrupted = Thread.interrupted();
        /* The common case is interruption is UNPARKED: Check it first. */
        if ((sleepResult == WaitResult.UNPARKED) || (sleepResult == WaitResult.INTERRUPTED) || interrupted) {
            throw new InterruptedException();
        }
    }

    /**
     * Returns <tt>true</tt> if and only if the current thread holds the monitor lock on the
     * specified object.
     */
    @Substitute
    private static boolean holdsLock(Object obj) {
        Objects.requireNonNull(obj);
        return ImageSingletons.lookup(MonitorSupport.class).holdsLock(obj);
    }

    @Substitute
    @NeverInline("Immediate caller must show up in stack trace and so needs its own stack frame")
    private StackTraceElement[] getStackTrace() {
        if (JavaThreads.fromTarget(this) == Thread.currentThread()) {
            /* We can walk our own stack without a VMOperation. */
            StackTraceBuilder stackTraceBuilder = new StackTraceBuilder(false);
            JavaStackWalker.walkCurrentThread(KnownIntrinsics.readCallerStackPointer(), KnownIntrinsics.readReturnAddress(), stackTraceBuilder);
            return stackTraceBuilder.getTrace();

        } else {
            return JavaThreads.getStackTrace(JavaThreads.fromTarget(this));
        }
    }

    @Substitute
    private static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        return JavaThreads.getAllStackTraces();
    }
}

final class Util_java_lang_Thread {

    static void initialize(Target_java_lang_Thread tjlt, ThreadGroup groupArg, Runnable target, String name, long stackSize) {
        /*
         * This method is a copy of the implementation of
         *
         * Thread.init(ThreadGroup g, Runnable target, String name, long stackSize)
         *
         * with unsupported features removed. It is used as the body of `init` in JDK8OrEarlier, and
         * as the body of the `Thread` constructor in JDK9OrLater.
         */

        tjlt.unsafeParkEvent = new AtomicReference<>();
        tjlt.sleepParkEvent = new AtomicReference<>();

        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        tjlt.name = name;

        final Thread parent = Target_java_lang_Thread.currentThread();
        final ThreadGroup group = ((groupArg != null) ? groupArg : parent.getThreadGroup());

        JavaThreads.toTarget(group).addUnstarted();

        tjlt.group = group;
        tjlt.daemon = parent.isDaemon();
        tjlt.contextClassLoader = parent.getContextClassLoader();
        tjlt.priority = parent.getPriority();
        tjlt.target = target;
        tjlt.setPriority(tjlt.priority);

        /* Stash the specified stack size in case the VM cares */
        tjlt.stackSize = stackSize;

        /* Set thread ID */
        tjlt.tid = Target_java_lang_Thread.nextThreadID();
    }
}

@TargetClass(ThreadGroup.class)
final class Target_java_lang_ThreadGroup {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private int nthreads;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private Thread[] threads;

    @Alias
    native void addUnstarted();

    @Alias
    native void add(Thread t);
}

/** Methods in support of the injected field Target_java_lang_Thread.unsafeParkEvent. */
final class UnsafeParkSupport {

    /** All static methods: no instances. */
    private UnsafeParkSupport() {
    }

    /** Interruptibly park the current thread. */
    static WaitResult park() {
        VMOperationControl.guaranteeOkayToBlock("[UnsafeParkSupport.park(): Should not park when it is not okay to block.]");
        final Thread thread = Thread.currentThread();
        final ParkEvent parkEvent = ensureParkEvent(thread);

        // Change the Java thread state while parking.
        final int oldStatus = JavaThreads.getThreadStatus(thread);
        JavaThreads.setThreadStatus(thread, ThreadStatus.PARKED);
        try {
            return parkEvent.condWait();
        } finally {
            JavaThreads.setThreadStatus(thread, oldStatus);
        }
    }

    /** Interruptibly park the current thread for the given number of nanoseconds. */
    static WaitResult park(long delayNanos) {
        VMOperationControl.guaranteeOkayToBlock("[UnsafeParkSupport.park(long): Should not park when it is not okay to block.]");
        final Thread thread = Thread.currentThread();
        final ParkEvent parkEvent = ensureParkEvent(thread);

        final long startNanos = System.nanoTime();
        /* Can not park past the end of a 64-bit nanosecond epoch. */
        final long endNanos = TimeUtils.addOrMaxValue(startNanos, delayNanos);

        final int oldStatus = JavaThreads.getThreadStatus(thread);
        JavaThreads.setThreadStatus(thread, ThreadStatus.PARKED_TIMED);
        try {
            // How much longer should I sleep?
            long remainingNanos = delayNanos;
            while (0L < remainingNanos) {
                WaitResult result = parkEvent.condTimedWait(remainingNanos);
                if (result == WaitResult.INTERRUPTED || result == WaitResult.UNPARKED) {
                    return result;
                }
                // If the sleep returns early, how much longer should I delay?
                remainingNanos = endNanos - System.nanoTime();
            }
            return WaitResult.TIMED_OUT;

        } finally {
            JavaThreads.setThreadStatus(thread, oldStatus);
        }
    }

    /** Unpark a Thread. */
    static void unpark(Thread thread) {
        ensureParkEvent(thread).unpark();
    }

    /** Get the Park event for a thread, initializing it if necessary. */
    private static ParkEvent ensureParkEvent(Thread thread) {
        return ParkEvent.initializeOnce(JavaThreads.getUnsafeParkEvent(thread), false);
    }
}

/** Methods in support of the injected field Target_java_lang_Thread.sleepParkEvent. */
final class SleepSupport {

    /** All static methods: no instances. */
    private SleepSupport() {
    }

    /** Sleep for the given number of nanoseconds, dealing with early wakeups and interruptions. */
    protected static WaitResult sleep(long delayNanos) {
        VMOperationControl.guaranteeOkayToBlock("[SleepSupport.sleep(long): Should not sleep when it is not okay to block.]");
        final Thread thread = Thread.currentThread();
        final ParkEvent sleepEvent = ensureSleepEvent(thread);

        final long startNanos = System.nanoTime();
        /* Can not sleep past the end of a 64-bit nanosecond epoch. */
        final long endNanos = TimeUtils.addOrMaxValue(startNanos, delayNanos);

        final int oldStatus = JavaThreads.getThreadStatus(thread);
        JavaThreads.setThreadStatus(thread, ThreadStatus.SLEEPING);
        try {
            // How much longer should I sleep?
            long remainingNanos = delayNanos;
            while (0L < remainingNanos) {
                final WaitResult result = sleepEvent.condTimedWait(remainingNanos);
                if (result == WaitResult.INTERRUPTED || result == WaitResult.UNPARKED) {
                    return result;
                }
                // If the sleep returns early, how much longer should I delay?
                remainingNanos = endNanos - System.nanoTime();
            }
            return WaitResult.TIMED_OUT;

        } finally {
            JavaThreads.setThreadStatus(thread, oldStatus);
        }
    }

    /** Interrupt a sleeping thread. */
    protected static void interrupt(Thread thread) {
        final ParkEvent sleepEvent = JavaThreads.getSleepParkEvent(thread).get();
        if (sleepEvent != null) {
            sleepEvent.unpark();
        }
    }

    /** Get the Sleep event for a thread, lazily initializing if needed. */
    private static ParkEvent ensureSleepEvent(Thread thread) {
        return ParkEvent.initializeOnce(JavaThreads.getSleepParkEvent(thread), true);
    }
}

@TargetClass(classNameProvider = Package_jdk_internal_misc.class, className = "Unsafe")
@SuppressWarnings({"static-method"})
final class Target_jdk_internal_misc_Unsafe {

    /**
     * Block current thread, returning when a balancing <tt>unpark</tt> occurs, or a balancing
     * <tt>unpark</tt> has already occurred, or the thread is interrupted, or, if not absolute and
     * time is not zero, the given time nanoseconds have elapsed, or if absolute, the given deadline
     * in milliseconds since Epoch has passed, or spuriously (i.e., returning for no "reason").
     * Note: This operation is in the Unsafe class only because <tt>unpark</tt> is, so it would be
     * strange to place it elsewhere.
     */
    @Substitute
    private void park(boolean isAbsolute, long time) {
        /* Decide what kind of park I am doing. */
        if (!isAbsolute && time == 0L) {
            /* Park without deadline. */
            UnsafeParkSupport.park();
        } else {
            /* Park with deadline. */
            final long delayNanos = TimeUtils.delayNanos(isAbsolute, time);
            UnsafeParkSupport.park(delayNanos);
        }
        // sun.misc.Unsafe.park does not distinguish between
        // timing out, being unparked, and being interrupted.
    }

    /**
     * Unblock the given thread blocked on <tt>park</tt>, or, if it is not blocked, cause the
     * subsequent call to <tt>park</tt> not to block. Note: this operation is "unsafe" solely
     * because the caller must somehow ensure that the thread has not been destroyed. Nothing
     * special is usually required to ensure this when called from Java (in which there will
     * ordinarily be a live reference to the thread) but this is not nearly-automatically so when
     * calling from native code.
     *
     * @param threadObj the thread to unpark.
     */
    @Substitute
    private void unpark(Object threadObj) {
        if (threadObj == null) {
            throw new NullPointerException("Unsafe.unpark(thread == null)");
        } else if (!(threadObj instanceof Thread)) {
            throw new IllegalArgumentException("Unsafe.unpark(!(thread instanceof Thread))");
        }
        Thread thread = (Thread) threadObj;
        UnsafeParkSupport.unpark(thread);
    }
}

/** A VMOperation to build a list of all the threads. */
class ThreadListOperation extends VMOperation {

    private final List<Thread> list;

    ThreadListOperation(List<Thread> list) {
        super("ReqeustTearDownOperation", CallerEffect.BLOCKS_CALLER, SystemEffect.CAUSES_SAFEPOINT);
        this.list = list;
    }

    @Override
    public void operate() {
        final Log trace = Log.noopLog().string("[ThreadListOperation.operate:")
                        .string("  queuingVMThread: ").hex(getQueuingVMThread())
                        .string("  currentVMThread: ").hex(CEntryPointContext.getCurrentIsolateThread())
                        .flush();
        list.clear();
        for (IsolateThread isolateThread = VMThreads.firstThread(); VMThreads.isNonNullThread(isolateThread); isolateThread = VMThreads.nextThread(isolateThread)) {
            final Thread thread = JavaThreads.singleton().fromVMThread(isolateThread);
            if (thread != null) {
                list.add(thread);
            }
        }
        trace.string("]").newline().flush();
    }
}

/** A VMOperation to count how many threads are still on the VMThreads list. */
class VMThreadCounterOperation extends VMOperation {

    private Log trace;
    private AtomicBoolean printLaggards;
    private int count;

    VMThreadCounterOperation(Log trace, AtomicBoolean printLaggards) {
        super("VMThreadCounterOperation", CallerEffect.BLOCKS_CALLER, SystemEffect.DOES_NOT_CAUSE_SAFEPOINT);
        this.trace = trace;
        this.printLaggards = printLaggards;
        this.count = 0;
    }

    int getCount() {
        return count;
    }

    @Override
    public void operate() {
        count = 0;
        for (IsolateThread isolateThread = VMThreads.firstThread(); VMThreads.isNonNullThread(isolateThread); isolateThread = VMThreads.nextThread(isolateThread)) {
            count += 1;
            if (printLaggards.get() && trace.isEnabled() && (isolateThread != getQueuingVMThread())) {
                trace.string("  laggard isolateThread: ").hex(isolateThread);
                final Thread thread = JavaThreads.singleton().fromVMThread(isolateThread);
                if (thread != null) {
                    final String name = thread.getName();
                    final Thread.State status = thread.getState();
                    final boolean interruptedStatus = thread.isInterrupted();
                    trace.string("  thread.getName(): ").string(name)
                                    .string("  interruptedStatus: ").bool(interruptedStatus)
                                    .string("  getState(): ").string(status.name());
                }
                trace.newline().flush();
            }
        }
    }
}
