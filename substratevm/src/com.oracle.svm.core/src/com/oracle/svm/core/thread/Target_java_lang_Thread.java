/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessControlContext;
import java.util.Map;
import java.util.Objects;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.JDK11OrEarlier;
import com.oracle.svm.core.jdk.JDK17OrLater;
import com.oracle.svm.core.jdk.LoomJDK;
import com.oracle.svm.core.jdk.NotLoomJDK;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.util.VMError;

@TargetClass(Thread.class)
@SuppressWarnings({"unused"})
public final class Target_java_lang_Thread {

    // Checkstyle: stop
    @Alias //
    static StackTraceElement[] EMPTY_STACK_TRACE;
    // Checkstyle: resume

    /** This field is initialized when the thread actually starts executing. */
    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    IsolateThread isolateThread;

    /**
     * Every thread has a boolean for noting whether this thread is interrupted.
     *
     * After JDK 11, a field with same name has been introduced and the logic to set / reset it has
     * moved into Java code. So this injected field and the substitutions that maintain it are no
     * longer necessary. See {@link #interruptedJDK17OrLater}.
     */
    @Inject //
    @TargetElement(onlyWith = JDK11OrEarlier.class) //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    volatile boolean interruptedJDK11OrEarlier;

    @Alias //
    @TargetElement(name = "interrupted", onlyWith = JDK17OrLater.class) //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    volatile boolean interruptedJDK17OrLater;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    boolean wasStartedByCurrentIsolate;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    long parentThreadId;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ThreadData.class)//
    UnacquiredThreadData threadData;

    @Alias//
    ClassLoader contextClassLoader;

    @Alias//
    volatile String name;

    @Alias @TargetElement(onlyWith = NotLoomJDK.class)//
    int priority;

    /* Whether or not the thread is a daemon . */
    @Alias @TargetElement(onlyWith = NotLoomJDK.class)//
    boolean daemon;

    /* What will be run. */
    @Alias @TargetElement(onlyWith = NotLoomJDK.class)//
    Runnable target;

    /* The group of this thread */
    @Alias @TargetElement(onlyWith = NotLoomJDK.class)//
    ThreadGroup group;

    /*
     * The requested stack size for this thread, or 0 if the creator did not specify a stack size.
     * It is up to the VM to do whatever it likes with this number; some VMs will ignore it.
     */
    @Alias @TargetElement(onlyWith = NotLoomJDK.class)//
    long stackSize;

    @Alias @TargetElement(onlyWith = LoomJDK.class)//
    Target_java_lang_Thread_FieldHolder holder;

    /* Thread ID */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadIdRecomputation.class) //
    long tid;

    /** We have our own atomic number in {@link JavaThreads#threadSeqNumber}. */
    @Delete @TargetElement(onlyWith = NotLoomJDK.class)//
    static long threadSeqNumber;
    /** We have our own atomic number in {@link JavaThreads#threadInitNumber}. */
    @Delete//
    static int threadInitNumber;

    /*
     * For unstarted threads created during image generation like the main thread, we do not want to
     * inherit a (more or less random) access control context.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private AccessControlContext inheritedAccessControlContext;

    @Alias @TargetElement(onlyWith = NotLoomJDK.class) //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadStatusRecomputation.class) //
    volatile int threadStatus;

    @Alias @TargetElement(onlyWith = NotLoomJDK.class) //
    /* private */ /* final */ Object blockerLock;
    @Alias @TargetElement(onlyWith = LoomJDK.class) //
    Object interruptLock;

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
    @TargetElement(onlyWith = NotLoomJDK.class) //
    static long nextThreadID() {
        return JavaThreads.singleton().threadSeqNumber.incrementAndGet();
    }

    /** Replace "synchronized" modifier with delegation to an atomic increment. */
    @Substitute
    private static int nextThreadNum() {
        return JavaThreads.singleton().threadInitNumber.incrementAndGet();
    }

    @Alias
    @TargetElement(onlyWith = LoomJDK.class)
    public native boolean isVirtual();

    @Alias
    public native void exit();

    Target_java_lang_Thread(String withName, ThreadGroup withGroup, boolean asDaemon) {
        /*
         * Raw creation of a thread without calling init(). Used to create a Thread object for an
         * already running thread.
         */
        this.threadData = new ThreadData();

        JavaContinuations.LoomCompatibilityUtil.initThreadFields(this,
                        (withGroup != null) ? withGroup : JavaThreads.singleton().mainGroup,
                        null, 0,
                        Thread.NORM_PRIORITY, asDaemon, ThreadStatus.RUNNABLE);

        if (JavaContinuations.useLoom()) {
            tid = Target_java_lang_Thread_ThreadIdentifiers.next();
        } else {
            tid = nextThreadID();
            blockerLock = new Object();
        }
        name = (withName != null) ? withName : ("System-" + nextThreadNum());
        contextClassLoader = ClassLoader.getSystemClassLoader();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Substitute
    public long getId() {
        return tid;
    }

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public native String getName();

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public native ThreadGroup getThreadGroup();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Substitute
    public boolean isDaemon() {
        return daemon;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Substitute
    @TargetElement(onlyWith = NotLoomJDK.class)
    static Thread currentThread() {
        Thread thread = JavaThreads.currentThread.get();
        assert thread != null : "Thread has not been set yet";
        return thread;
    }

    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    @Substitute
    @TargetElement(onlyWith = LoomJDK.class)
    private static Thread currentThread0() {
        Thread thread = JavaThreads.currentThread.get();
        assert thread != null : "Thread has not been set yet";
        return thread;
    }

    @Inject @TargetElement(onlyWith = LoomJDK.class)//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Thread vthread = null;

    /**
     * `do_vthread` intrinsic.
     *
     * Ref: "Intrinsify currentThread()"
     * https://github.com/openjdk/loom/commit/457195f6460f6bb713a4a3c02879143073bed0c7
     */
    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    @Substitute
    @TargetElement(name = "currentThread", onlyWith = LoomJDK.class)
    static Thread currentVThread() {
        Target_java_lang_Thread thread = SubstrateUtil.cast(currentThread0(),
                        Target_java_lang_Thread.class);
        if (thread.vthread != null) {
            return thread.vthread;
        } else {
            return SubstrateUtil.cast(thread, Thread.class);
        }
    }

    @SuppressWarnings("static-method")
    @Substitute
    @TargetElement(onlyWith = LoomJDK.class)
    void setCurrentThread(Thread thread) {
        Thread currentCarrierThread = currentThread0();
        assert SubstrateUtil.cast(currentCarrierThread, Target_java_lang_Thread.class) == this;
        if (thread == currentCarrierThread) {
            vthread = null;
        } else {
            vthread = thread;
        }
    }

    @Alias
    @TargetElement(onlyWith = LoomJDK.class)
    private static native void checkCharacteristics(int characteristics);

    @Substitute
    @SuppressWarnings({"unused"})
    @TargetElement(onlyWith = NotLoomJDK.class)
    private Target_java_lang_Thread(
                    ThreadGroup g,
                    Runnable target,
                    String name,
                    long stackSize,
                    AccessControlContext acc,
                    boolean inheritThreadLocals) {
        /* Non-0 instance field initialization. */
        this.blockerLock = new Object();
        /* Injected Target_java_lang_Thread instance field initialization. */
        this.threadData = new ThreadData();
        /* Initialize the rest of the Thread object, ignoring `acc` and `inheritThreadLocals`. */
        JavaThreads.initializeNewThread(this, g, target, name, stackSize);
    }

    @Substitute
    @SuppressWarnings({"unused"})
    @TargetElement(onlyWith = LoomJDK.class)
    private Target_java_lang_Thread(
                    ThreadGroup g,
                    String name,
                    int characteristics,
                    Runnable target,
                    long stackSize,
                    AccessControlContext acc) {
        /* Non-0 instance field initialization. */
        this.interruptLock = new Object();
        /* Injected Target_java_lang_Thread instance field initialization. */
        this.threadData = new ThreadData();

        checkCharacteristics(characteristics);

        /* Initialize the rest of the Thread object, ignoring `acc` and `characteristics`. */
        JavaThreads.initializeNewThread(this, g, target, name, stackSize);
    }

    /**
     * This constructor is only called by `VirtualThread#VirtualThread(Executor, String, int,
     * Runnable)`.
     */
    @Substitute
    @TargetElement(onlyWith = LoomJDK.class)
    private Target_java_lang_Thread(String name, int characteristics) {
        /* Non-0 instance field initialization. */
        this.interruptLock = new Object();

        this.name = (name != null) ? name : "<unnamed>";
        this.tid = Target_java_lang_Thread_ThreadIdentifiers.next();
        this.contextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @SuppressWarnings("hiding")
    @Substitute
    private void start0() {
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            throw VMError.unsupportedFeature("Single-threaded VM cannot create new threads");
        }

        /*
         * The threadStatus must be set to RUNNABLE by the parent thread and before the child thread
         * starts because we are creating child threads asynchronously (there is no coordination
         * between parent and child threads).
         *
         * Otherwise, a call to Thread.join() in the parent thread could succeed even before the
         * child thread starts, or it could hang in case that the child thread is already dead.
         */
        JavaContinuations.LoomCompatibilityUtil.setThreadStatus(this, ThreadStatus.RUNNABLE);
        wasStartedByCurrentIsolate = true;
        parentThreadId = Thread.currentThread().getId();
        long stackSize = JavaThreads.getRequestedThreadSize(JavaThreads.fromTarget(this));
        JavaThreads.singleton().startThread(JavaThreads.fromTarget(this), stackSize);
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    protected void setNativeName(String name) {
        JavaThreads.singleton().setNativeName(JavaThreads.fromTarget(this), name);
    }

    @Substitute
    private void setPriority0(int priority) {
    }

    /**
     * Avoid in VM-internal contexts: this method is not {@code final} and can be overridden with
     * code that does locking or performs other actions that can be unsafe in a specific context.
     * Use {@link JavaThreads#isInterrupted} instead.
     */
    @Alias
    public native boolean isInterrupted();

    @Substitute
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    private boolean isInterrupted(boolean clearInterrupted) {
        final boolean result = interruptedJDK11OrEarlier;
        if (result && clearInterrupted) {
            /*
             * As we don't use a lock, it is possible to observe any kinds of races with other
             * threads that try to set interrupted to true. However, those races don't cause any
             * correctness issues as we only reset interrupted to false if we observed that it was
             * true earlier. There also can't be any problematic races with other calls to
             * isInterrupted as clearInterrupted may only be true if this method is being executed
             * by the current thread.
             */
            interruptedJDK11OrEarlier = false;
        }
        return result;
    }

    /**
     * Marks the thread as interrupted and wakes it up.
     *
     * See {@link JavaThreads#park()}, {@link JavaThreads#park(long)} and {@link JavaThreads#sleep}
     * for vital aspects of the underlying mechanisms.
     */
    @Substitute
    void interrupt0() {
        if (JavaVersionUtil.JAVA_SPEC <= 11) {
            interruptedJDK11OrEarlier = true;
        } else {
            /*
             * After JDK 11, the interrupted flag is maintained by the JDK in Java code, i.e.,
             * already set by the caller. So we do not need to set any flag.
             */
        }

        if (!SubstrateOptions.MultiThreaded.getValue()) {
            /* If the VM is single-threaded, this thread can not be blocked. */
            return;
        }

        Thread thread = JavaThreads.fromTarget(this);
        JavaThreads.interrupt(thread);
        /*
         * This may unpark the thread unnecessarily (e.g., the interrupt above could have already
         * resumed the thread execution, so the thread could now be parked for some other reason).
         * However, this is not a correctness issue as the unpark will only be a spurious wakeup.
         */
        JavaThreads.unpark(thread);
        /*
         * Must be executed after setting interrupted to true, see
         * HeapImpl.waitForReferencePendingList().
         */
        JavaThreads.wakeUpVMConditionWaiters(thread);
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    private void stop0(Object o) {
        throw VMError.unsupportedFeature("The deprecated method Thread.stop is not supported");
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    private void suspend0() {
        throw VMError.unsupportedFeature("The deprecated method Thread.suspend is not supported");
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    private void resume0() {
        throw VMError.unsupportedFeature("The deprecated method Thread.resume is not supported");
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    private int countStackFrames() {
        throw VMError.unsupportedFeature("The deprecated method Thread.countStackFrames is not supported");
    }

    /*
     * We are defensive and also handle private native methods by marking them as deleted. If they
     * are reachable, the user is certainly doing something wrong. But we do not want to fail with a
     * linking error.
     */

    @Delete
    private static native void registerNatives();

    @Delete
    private static native StackTraceElement[][] dumpThreads(Thread[] threads);

    @Delete
    private static native Thread[] getThreads();

    @Substitute
    @TargetElement(onlyWith = NotLoomJDK.class)
    private boolean isAlive() {
        // There are fewer cases that are not-alive.
        return JavaThreads.isAlive(threadStatus);
    }

    @Substitute
    @TargetElement(onlyWith = LoomJDK.class)
    private boolean isAlive0() {
        return JavaThreads.isAlive(holder.threadStatus);
    }

    @Substitute
    @TargetElement(onlyWith = NotLoomJDK.class)
    private static void yield() {
        JavaThreads.singleton().yield();
    }

    /**
     * copy of {@link Target_java_lang_Thread#yield()}.
     */
    @Substitute
    @TargetElement(onlyWith = LoomJDK.class)
    private static void yield0() {
        JavaThreads.singleton().yield();
    }

    @Substitute
    @TargetElement(onlyWith = NotLoomJDK.class)
    private static void sleep(long millis) throws InterruptedException {
        JavaThreads.sleep(millis);
    }

    /**
     * copy of {@link Target_java_lang_Thread#sleep(long)}.
     */
    @Substitute
    @TargetElement(onlyWith = LoomJDK.class)
    private static void sleep0(long millis) throws InterruptedException {
        JavaThreads.sleep(millis);
    }

    /**
     * Returns <tt>true</tt> if and only if the current thread holds the monitor lock on the
     * specified object.
     */
    @Substitute
    private static boolean holdsLock(Object obj) {
        Objects.requireNonNull(obj);
        return MonitorSupport.singleton().isLockedByCurrentThread(obj);
    }

    @Substitute
    private StackTraceElement[] getStackTrace() {
        if (JavaThreads.isVirtual(JavaThreads.fromTarget(this))) {
            return asyncGetStackTrace();
        }
        return JavaThreads.getStackTrace(JavaThreads.fromTarget(this));
    }

    @SuppressWarnings("static-method")
    @Substitute
    @TargetElement(onlyWith = LoomJDK.class)
    StackTraceElement[] asyncGetStackTrace() {
        throw VMError.shouldNotReachHere("only `VirtualThread.asyncGetStackTrace` should be called.");
    }

    @Substitute
    private static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        return JavaThreads.getAllStackTraces();
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    private static void clearInterruptEvent() {
        /*
         * In the JDK, this is a no-op except on Windows. The JDK resets the interrupt event used by
         * Process.waitFor ResetEvent((HANDLE) JVM_GetThreadInterruptEvent()); Our implementation in
         * WindowsJavaThreads.java takes care of this ResetEvent.
         */
    }

    @Substitute
    @TargetElement(onlyWith = LoomJDK.class)
    static Object[] scopedCache() {
        throw VMError.unimplemented();
    }

    @Substitute
    @TargetElement(onlyWith = LoomJDK.class)
    static void setScopedCache(Object[] cache) {
        throw VMError.unimplemented();
    }

    @Alias @TargetElement(onlyWith = LoomJDK.class) //
    Target_java_lang_Continuation cont;

    @Alias
    @TargetElement(onlyWith = LoomJDK.class)
    native Target_java_lang_Continuation getContinuation();

    @Alias
    @TargetElement(onlyWith = LoomJDK.class)
    public static native Thread startVirtualThread(Runnable task);

}

@TargetClass(value = Thread.class, innerClass = "FieldHolder", onlyWith = LoomJDK.class)
final class Target_java_lang_Thread_FieldHolder {
    @Alias //
    ThreadGroup group;
    @Alias //
    Runnable task;
    @Alias //
    long stackSize;
    @Alias //
    int priority;
    @Alias //
    boolean daemon;
    @Alias //
    int threadStatus;

    Target_java_lang_Thread_FieldHolder(
                    ThreadGroup group,
                    Runnable task,
                    long stackSize,
                    int priority,
                    boolean daemon) {
        this.group = group;
        this.task = task;
        this.stackSize = stackSize;
        this.priority = priority;
        this.daemon = daemon;
    }

}

@Substitute//
@TargetClass(value = Thread.class, innerClass = "ThreadIdentifiers", onlyWith = LoomJDK.class)
final class Target_java_lang_Thread_ThreadIdentifiers {
    @Substitute//
    static long next() {
        return JavaThreads.singleton().threadSeqNumber.incrementAndGet();
    }
}

@TargetClass(value = Thread.class, innerClass = "VirtualThreads", onlyWith = LoomJDK.class)
final class Target_java_lang_Thread_VirtualThreads {
}
