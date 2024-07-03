/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.thread.ThreadStatus.JVMTI_THREAD_STATE_TERMINATED;

import java.lang.Thread.UncaughtExceptionHandler;
import java.security.AccessControlContext;
import java.util.Map;
import java.util.Objects;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK21OrEarlier;
import com.oracle.svm.core.jdk.JDKLatest;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.replacements.ReplacementsUtil;

@TargetClass(Thread.class)
@SuppressWarnings({"unused"})
public final class Target_java_lang_Thread {

    // Checkstyle: stop
    @Delete //
    static StackTraceElement[] EMPTY_STACK_TRACE;

    @Alias //
    static int NO_INHERIT_THREAD_LOCALS;

    @Alias //
    static Object NEW_THREAD_BINDINGS;
    // Checkstyle: resume

    /** This field is initialized when the thread actually starts executing. */
    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    IsolateThread isolateThread;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    volatile boolean interrupted;

    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    long parentThreadId;

    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    public boolean jfrExcluded;

    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ThreadData.class)//
    UnacquiredThreadData threadData;

    @Alias//
    ClassLoader contextClassLoader;

    @Alias//
    volatile String name;

    @Alias//
    Target_java_lang_ThreadLocal_ThreadLocalMap threadLocals = null;

    @Alias//
    Target_java_lang_ThreadLocal_ThreadLocalMap inheritableThreadLocals = null;

    @Alias //
    Target_java_lang_Thread_FieldHolder holder;

    /* Thread ID */
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadIdRecomputation.class) //
    public long tid;

    /*
     * For unstarted threads created during image generation like the main thread, we do not want to
     * inherit a (more or less random) access control context.
     */
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    public AccessControlContext inheritedAccessControlContext;

    @Alias //
    Object interruptLock;

    @Alias //
    volatile Target_sun_nio_ch_Interruptible nioBlocker;

    /** @see JavaThreads#setCurrentThreadLockHelper */
    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    Object lockHelper;

    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    Object[] scopedValueCache;

    @Alias //
    Object scopedValueBindings;

    @Alias
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    native void setPriority(int newPriority);

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public native boolean isVirtual();

    @Alias
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    public native void exit();

    Target_java_lang_Thread(String withName, ThreadGroup withGroup, boolean asDaemon) {
        /*
         * Raw creation of a thread without calling init(). Used to create a Thread object for an
         * already running thread.
         */
        this.threadData = new ThreadData();

        ThreadGroup nonnullGroup = (withGroup != null) ? withGroup : PlatformThreads.singleton().mainGroup;
        JavaThreads.initThreadFields(this, nonnullGroup, null, 0, Thread.NORM_PRIORITY, asDaemon);
        PlatformThreads.setThreadStatus(JavaThreads.fromTarget(this), ThreadStatus.RUNNABLE);

        tid = Target_java_lang_Thread_ThreadIdentifiers.next();
        interruptLock = new Object();
        name = (withName != null) ? withName : ("System-" + JavaThreads.nextThreadNum());
        contextClassLoader = ClassLoader.getSystemClassLoader();
    }

    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    public long getId() {
        return tid;
    }

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public native String getName();

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static native ThreadGroup virtualThreadGroup();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @AnnotateOriginal
    public native boolean isDaemon();

    @Substitute
    static Thread currentCarrierThread() {
        Thread thread = PlatformThreads.currentThread.get();
        assert thread != null : "Thread has not been set yet";
        return thread;
    }

    /** On HotSpot, a field in C++ class {@code JavaThread}. Loads and stores are unordered. */
    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Thread vthread = null;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Substitute
    static Thread currentThread() {
        Thread thread = JavaThreads.getCurrentThreadOrNull();
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.dynamicAssert(thread != null, "Thread has not been set yet");
        } else {
            assert thread != null : "Thread has not been set yet";
        }
        return thread;
    }

    @SuppressWarnings("static-method")
    @Substitute
    void setCurrentThread(Thread thread) {
        JavaThreads.setCurrentThread(JavaThreads.fromTarget(this), thread);
    }

    @Substitute
    @SuppressWarnings({"unused"})
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
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

        String nameLocal = (name != null) ? name : genThreadName();
        boolean inheritThreadLocals = (characteristics & NO_INHERIT_THREAD_LOCALS) == 0;
        JavaThreads.initializeNewThread(this, g, target, nameLocal, stackSize, acc, inheritThreadLocals);

        this.scopedValueBindings = NEW_THREAD_BINDINGS;
    }

    @Substitute
    static String genThreadName() {
        int threadNum = JavaThreads.JavaThreadNumberSingleton.singleton().threadInitNumber.incrementAndGet();
        return "Thread-" + threadNum;
    }

    /** This constructor is called only by {@code VirtualThread}. */
    @Substitute
    private Target_java_lang_Thread(String name, int characteristics, boolean bound) {
        if (bound) {
            this.threadData = new ThreadData();
        }

        /* Non-0 instance field initialization. */
        this.interruptLock = new Object();

        this.name = (name != null) ? name : "";
        this.tid = Target_java_lang_Thread_ThreadIdentifiers.next();
        this.inheritedAccessControlContext = Target_java_lang_Thread_Constants.NO_PERMISSIONS_ACC;

        boolean inheritThreadLocals = (characteristics & NO_INHERIT_THREAD_LOCALS) == 0;
        JavaThreads.initNewThreadLocalsAndLoader(this, inheritThreadLocals, Thread.currentThread());

        this.scopedValueBindings = NEW_THREAD_BINDINGS;

        if (bound) {
            ThreadGroup g = Target_java_lang_Thread_Constants.VTHREAD_GROUP;
            int pri = Thread.NORM_PRIORITY;
            JavaThreads.initThreadFields(this, g, null, -1, pri, true);

            PlatformThreads.setThreadStatus(JavaThreads.fromTarget(this), ThreadStatus.NEW);
        }
    }

    @SuppressWarnings("hiding")
    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private void start0() {
        parentThreadId = JavaThreads.getThreadId(Thread.currentThread());
        long stackSize = PlatformThreads.getRequestedStackSize(JavaThreads.fromTarget(this));
        try {
            PlatformThreads.singleton().startThread(JavaThreads.fromTarget(this), stackSize);
        } catch (Throwable t) {
            parentThreadId = 0; // should not be accessed if thread could not start, but reset still
            throw t;
        }
        /*
         * The threadStatus must be RUNNABLE before returning so the caller can safely use
         * Thread.join() on the launched thread, but the thread could also already have changed its
         * state itself. Atomically switch from NEW to RUNNABLE if it has not.
         */
        PlatformThreads.compareAndSetThreadStatus(JavaThreads.fromTarget(this), ThreadStatus.NEW, ThreadStatus.RUNNABLE);
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    protected void setNativeName(String name) {
        PlatformThreads.singleton().setNativeName(JavaThreads.fromTarget(this), name);
    }

    @Substitute
    private void setPriority0(int priority) {
    }

    /**
     * Avoid in VM-internal contexts: this method is not {@code final} and can be overridden with
     * code that does locking or performs other actions that can be unsafe in a specific context.
     * Use {@link JavaThreads#isInterrupted} instead.
     */
    @Substitute
    @SuppressWarnings("static-method")
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    public boolean isInterrupted() {
        return JavaThreads.isInterrupted(JavaThreads.fromTarget(this));
    }

    /**
     * Marks the thread as interrupted and wakes it up.
     *
     * See {@link PlatformThreads#parkCurrentPlatformOrCarrierThread},
     * {@link PlatformThreads#unpark} and {@link PlatformThreads#sleep} for vital aspects of the
     * underlying mechanisms.
     */
    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    void interrupt0() {
        /*
         * The interrupted flag is maintained by the JDK in Java code, i.e., already set by the
         * caller. So we do not need to set any flag.
         */
        Thread thread = JavaThreads.fromTarget(this);
        PlatformThreads.interruptSleep(thread);
        /*
         * This may unpark the thread unnecessarily (e.g., the interrupt above could have already
         * resumed the thread execution, so the thread could now be parked for some other reason).
         * However, this is not a correctness issue as the unpark will only be a spurious wakeup.
         */
        PlatformThreads.unpark(thread);
        /*
         * Must be executed after setting interrupted to true, see
         * HeapImpl.waitForReferencePendingList().
         */
        PlatformThreads.wakeUpVMConditionWaiters(thread);
    }

    @Substitute
    @TargetElement(onlyWith = JDK21OrEarlier.class)
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
    private boolean alive() {
        return PlatformThreads.isAlive(JavaThreads.fromTarget(this));
    }

    @Substitute
    private static void yield0() {
        // Virtual threads are handled in yield()
        PlatformThreads.singleton().yieldCurrent();
    }

    @Substitute
    @TargetElement(onlyWith = JDK21OrEarlier.class)
    private static void sleep0(long nanos) throws InterruptedException {
        // Virtual threads are handled in sleep()
        PlatformThreads.sleep(nanos);
    }

    @Substitute
    @TargetElement(onlyWith = JDKLatest.class)
    private static void sleepNanos0(long nanos) throws InterruptedException {
        // Virtual threads are handled in sleep()
        PlatformThreads.sleep(nanos);
    }

    /**
     * Returns <tt>true</tt> if and only if the current thread holds the monitor lock on the
     * specified object.
     */
    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private static boolean holdsLock(Object obj) {
        Objects.requireNonNull(obj);
        return MonitorSupport.singleton().isLockedByCurrentThread(obj);
    }

    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private StackTraceElement[] getStackTrace() {
        return JavaThreads.getStackTrace(false, JavaThreads.fromTarget(this));
    }

    @Delete
    @SuppressWarnings({"static-method"})
    private native Object getStackTrace0();

    /** @see com.oracle.svm.core.jdk.StackTraceUtils#asyncGetStackTrace */
    @Delete
    native StackTraceElement[] asyncGetStackTrace();

    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        return PlatformThreads.getAllStackTraces();
    }

    @Substitute
    private static Thread[] getAllThreads() {
        return PlatformThreads.getAllThreads();
    }

    /**
     * In the JDK, this is a no-op except on Windows where the JDK resets the interrupt event used
     * by {@code Process.waitFor} with {@code ResetEvent((HANDLE) JVM_GetThreadInterruptEvent());}
     * Our implementation in {@code WindowsPlatformThreads} already handles this.
     */
    @Substitute
    private static void clearInterruptEvent() {
    }

    @Alias
    native void setInterrupt();

    @Alias //
    native void clearInterrupt();

    /** Carrier threads only: the current innermost continuation. */
    @Alias //
    Target_jdk_internal_vm_Continuation cont;

    @Substitute
    static Object[] scopedValueCache() {
        return JavaThreads.toTarget(currentCarrierThread()).scopedValueCache;
    }

    @Substitute
    static void setScopedValueCache(Object[] cache) {
        JavaThreads.toTarget(currentCarrierThread()).scopedValueCache = cache;
    }

    @Alias
    static native Object scopedValueBindings();

    /**
     * This method is used to set and revert {@code ScopedValue} bindings as follows:
     *
     * {@code setScopedValueBindings(b); try { work(); } finally { setScopedValueBindings(previous);
     * }}
     *
     * If a stack overflow or a throwing safepoint action (e.g. recurring callback) disrupts the
     * second call, ScopedValue bindings can leak out of their scope. Therefore, we require this
     * method and its direct callers to be uninterruptible. Both calls should be in a single same
     * caller, which is the case for the usages in the JDK, and those are expected to remain the
     * only direct usages. Because turning methods uninterruptible prevents inlining through them,
     * we would prefer another approach such as force-inlining this method instead, but that would
     * not prevent a throwing safepoint action in the {@code finally} block of the above pattern.
     *
     * {@code ScopedValue.Carrier} calls this method through the implementation of
     * {@code JavaLangAccess}, which is an anonymous class that we cannot substitute, so we also
     * substitute the calling class to invoke this method directly in
     * {@link Target_java_lang_ScopedValue_Carrier}.
     */
    @Substitute
    @Uninterruptible(reason = "Must not call other methods which can trigger a stack overflow.", callerMustBe = true)
    static void setScopedValueBindings(Object bindings) {
        Target_java_lang_Thread thread = SubstrateUtil.cast(PlatformThreads.currentThread.get(), Target_java_lang_Thread.class);
        if (thread.vthread != null) {
            thread = SubstrateUtil.cast(thread.vthread, Target_java_lang_Thread.class);
        }
        thread.scopedValueBindings = bindings;
    }

    /**
     * On HotSpot, this method determines the correct ScopedValue bindings for the current context
     * by finding the top {@code runWith} invocation on the stack and extracting the bindings object
     * parameter from the frame. It is used following stack overflows and other situations that
     * could result in bindings leaking to another scope, during which {@link #scopedValueBindings}
     * is cleared as a precaution. We don't have the means to extract the bindings object from the
     * stack, but we ensure that {@link #setScopedValueBindings} does not trigger stack overflows
     * and substitute {@link Target_java_lang_ScopedValue#scopedValueBindings} to never call this
     * method.
     */
    @Delete
    static native Object findScopedValueBindings();

    @Substitute
    @TargetElement(name = "blockedOn", onlyWith = JDK21OrEarlier.class)
    static void blockedOnJDK22(Target_sun_nio_ch_Interruptible b) {
        JavaThreads.blockedOn(b);
    }

    @Substitute
    @TargetElement(onlyWith = JDKLatest.class)
    @SuppressWarnings("static-method")
    void blockedOn(Target_sun_nio_ch_Interruptible b) {
        JavaThreads.blockedOn(b);
    }

    @Alias
    native Thread.State threadState();

    /**
     * This is needed to make {@link Thread#getThreadGroup()} uninterruptible.
     * {@link Thread#getThreadGroup()} checks for {@link #isTerminated()}, which calls
     * {@link #threadState()}. Instead of making {@link #threadState()} uninterruptible, which would
     * be difficult, we duplicate the code that determines the terminated state.
     *
     * Not that {@link Target_java_lang_VirtualThread} overrides
     * {@link Target_java_lang_VirtualThread#isTerminated()} with a trivial implementation that can
     * be made uninterruptible.
     */
    @Substitute
    boolean isTerminated() {
        return (holder.threadStatus & JVMTI_THREAD_STATE_TERMINATED) != 0;
    }

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    static volatile UncaughtExceptionHandler defaultUncaughtExceptionHandler;

    @Alias
    native Target_jdk_internal_vm_ThreadContainer threadContainer();

    @Alias
    native long threadId();

    @Delete
    static native long getNextThreadIdOffset();
}

@TargetClass(value = Thread.class, innerClass = "Constants")
final class Target_java_lang_Thread_Constants {
    // Checkstyle: stop
    @SuppressWarnings("removal") //
    @Alias static AccessControlContext NO_PERMISSIONS_ACC;

    @Alias static ThreadGroup VTHREAD_GROUP;
    // Checkstyle: resume
}

@TargetClass(value = Thread.class, innerClass = "FieldHolder")
@SuppressWarnings("unused")
final class Target_java_lang_Thread_FieldHolder {
    @Alias //
    ThreadGroup group;
    @Alias //
    Runnable task;
    @Alias //
    long stackSize;
    @Alias //
    volatile int priority;
    @Alias //
    volatile boolean daemon;
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadHolderRecomputation.class) //
    volatile int threadStatus;

    @Alias
    Target_java_lang_Thread_FieldHolder(ThreadGroup group,
                    Runnable task,
                    long stackSize,
                    int priority,
                    boolean daemon) {
    }
}

@Substitute//
@TargetClass(value = Thread.class, innerClass = "ThreadIdentifiers")
final class Target_java_lang_Thread_ThreadIdentifiers {
    @Substitute//
    static long next() {
        return JavaThreads.JavaThreadNumberSingleton.singleton().threadSeqNumber.incrementAndGet();
    }
}

@TargetClass(className = "sun.nio.ch.Interruptible")
interface Target_sun_nio_ch_Interruptible {
    @Alias
    void interrupt(Thread t);
}
