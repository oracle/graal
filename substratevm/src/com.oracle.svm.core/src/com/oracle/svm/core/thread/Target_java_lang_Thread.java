/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessControlContext;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.SubstrateOptions;
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
import com.oracle.svm.core.jdk.ContinuationsNotSupported;
import com.oracle.svm.core.jdk.ContinuationsSupported;
import com.oracle.svm.core.jdk.JDK11OrEarlier;
import com.oracle.svm.core.jdk.JDK17OrEarlier;
import com.oracle.svm.core.jdk.JDK17OrLater;
import com.oracle.svm.core.jdk.JDK19OrLater;
import com.oracle.svm.core.jdk.LoomJDK;
import com.oracle.svm.core.jdk.NotLoomJDK;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.util.VMError;

@TargetClass(Thread.class)
@SuppressWarnings({"unused"})
public final class Target_java_lang_Thread {

    // Checkstyle: stop
    @Alias //
    public static StackTraceElement[] EMPTY_STACK_TRACE;

    @Alias //
    @TargetElement(onlyWith = JDK19OrLater.class) //
    static int NO_THREAD_LOCALS;

    @Alias //
    @TargetElement(onlyWith = JDK19OrLater.class) //
    static int NO_INHERIT_THREAD_LOCALS;
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

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    long parentThreadId;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ThreadData.class)//
    UnacquiredThreadData threadData;

    @Alias//
    ClassLoader contextClassLoader;

    @Alias//
    volatile String name;

    @Alias @TargetElement(onlyWith = JDK17OrEarlier.class)//
    int priority;

    /* Whether or not the thread is a daemon . */
    @Alias @TargetElement(onlyWith = JDK17OrEarlier.class)//
    boolean daemon;

    /* What will be run. */
    @Alias @TargetElement(onlyWith = JDK17OrEarlier.class)//
    Runnable target;

    /* The group of this thread */
    @Alias @TargetElement(onlyWith = JDK17OrEarlier.class)//
    ThreadGroup group;

    @Alias//
    Target_java_lang_ThreadLocal_ThreadLocalMap threadLocals = null;

    @Alias//
    Target_java_lang_ThreadLocal_ThreadLocalMap inheritableThreadLocals = null;

    /*
     * The requested stack size for this thread, or 0 if the creator did not specify a stack size.
     * It is up to the VM to do whatever it likes with this number; some VMs will ignore it.
     */
    @Alias @TargetElement(onlyWith = JDK17OrEarlier.class)//
    long stackSize;

    @Alias @TargetElement(onlyWith = JDK19OrLater.class)//
    Target_java_lang_Thread_FieldHolder holder;

    /* Thread ID */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadIdRecomputation.class) //
    public long tid;

    /** We have our own atomic number in {@link JavaThreads#threadSeqNumber}. */
    @Delete @TargetElement(onlyWith = JDK17OrEarlier.class)//
    static long threadSeqNumber;
    /** We have our own atomic number in {@link JavaThreads#threadInitNumber}. */
    @Delete//
    @TargetElement(onlyWith = JDK17OrEarlier.class) //
    static int threadInitNumber;

    /*
     * For unstarted threads created during image generation like the main thread, we do not want to
     * inherit a (more or less random) access control context.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    public AccessControlContext inheritedAccessControlContext;

    @Alias @TargetElement(onlyWith = JDK17OrEarlier.class) //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadStatusRecomputation.class) //
    volatile int threadStatus;

    @Alias @TargetElement(onlyWith = JDK17OrEarlier.class) //
    Object blockerLock;
    @Alias @TargetElement(onlyWith = JDK19OrLater.class) //
    Object interruptLock;

    @Alias @TargetElement(onlyWith = JDK17OrEarlier.class) //
    volatile Target_sun_nio_ch_Interruptible blocker;

    @Alias @TargetElement(onlyWith = JDK19OrLater.class) //
    volatile Target_sun_nio_ch_Interruptible nioBlocker;

    /** @see JavaThreads#setCurrentThreadLockHelper */
    @Inject @TargetElement(onlyWith = ContinuationsSupported.class) //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    Object lockHelper;

    @Inject @TargetElement(onlyWith = LoomJDK.class) //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    Object[] extentLocalCache;

    @Alias
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    native void setPriority(int newPriority);

    @Alias
    @TargetElement(onlyWith = JDK19OrLater.class)
    static native boolean isSupportedClassLoader(ClassLoader loader);

    @Substitute
    public ClassLoader getContextClassLoader() {
        if (JavaVersionUtil.JAVA_SPEC >= 19 && !isSupportedClassLoader(contextClassLoader)) {
            return ClassLoader.getSystemClassLoader();
        }
        return contextClassLoader;
    }

    @Substitute
    public void setContextClassLoader(ClassLoader cl) {
        if (JavaVersionUtil.JAVA_SPEC >= 19 && !isSupportedClassLoader(contextClassLoader)) {
            throw new UnsupportedOperationException("The context class loader cannot be set");
        }
        contextClassLoader = cl;
    }

    /** Replace "synchronized" modifier with delegation to an atomic increment. */
    @Substitute
    @TargetElement(onlyWith = JDK17OrEarlier.class) //
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    static long nextThreadID() {
        return JavaThreads.threadSeqNumber.incrementAndGet();
    }

    /** Replace "synchronized" modifier with delegation to an atomic increment. */
    @Substitute
    @TargetElement(onlyWith = JDK17OrEarlier.class) //
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private static int nextThreadNum() {
        return JavaThreads.threadInitNumber.incrementAndGet();
    }

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @TargetElement(onlyWith = JDK19OrLater.class)
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

        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            tid = Target_java_lang_Thread_ThreadIdentifiers.next();
            interruptLock = new Object();
        } else {
            tid = nextThreadID();
            blockerLock = new Object();
        }
        name = (withName != null) ? withName : ("System-" + nextThreadNum());
        contextClassLoader = ClassLoader.getSystemClassLoader();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
    public native ThreadGroup getThreadGroup();

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @TargetElement(onlyWith = JDK19OrLater.class)
    static native ThreadGroup virtualThreadGroup();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @AnnotateOriginal
    public native boolean isDaemon();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Substitute
    @TargetElement(onlyWith = ContinuationsNotSupported.class)
    static Thread currentThread() {
        Thread thread = PlatformThreads.currentThread.get();
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.dynamicAssert(thread != null, "Thread has not been set yet");
        } else {
            assert thread != null : "Thread has not been set yet";
        }
        return thread;
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    static Thread currentCarrierThread() {
        Thread thread = PlatformThreads.currentThread.get();
        assert thread != null : "Thread has not been set yet";
        return thread;
    }

    /** On HotSpot, a field in C++ class {@code JavaThread}. Loads and stores are unordered. */
    @Inject @TargetElement(onlyWith = ContinuationsSupported.class)//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Thread vthread = null;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Substitute
    @TargetElement(name = "currentThread", onlyWith = ContinuationsSupported.class)
    static Thread currentVThread() {
        Thread thread = PlatformThreads.currentThread.get();
        Target_java_lang_Thread tjlt = SubstrateUtil.cast(thread, Target_java_lang_Thread.class);
        return (tjlt.vthread != null) ? tjlt.vthread : thread;
    }

    @SuppressWarnings("static-method")
    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    void setCurrentThread(Thread thread) {
        PlatformThreads.setCurrentThread(JavaThreads.fromTarget(this), thread);
    }

    @Substitute
    @SuppressWarnings({"unused"})
    @TargetElement(onlyWith = JDK17OrEarlier.class)
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
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
        /* Initialize the rest of the Thread object. */
        JavaThreads.initializeNewThread(this, g, target, name, stackSize, acc, true, inheritThreadLocals);
    }

    @Substitute
    @SuppressWarnings({"unused"})
    @TargetElement(onlyWith = JDK19OrLater.class)
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
        boolean allowThreadLocals = (characteristics & NO_THREAD_LOCALS) == 0;
        boolean inheritThreadLocals = (characteristics & NO_INHERIT_THREAD_LOCALS) == 0;
        JavaThreads.initializeNewThread(this, g, target, nameLocal, stackSize, acc, allowThreadLocals, inheritThreadLocals);
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    static String genThreadName() {
        return "Thread-" + JavaThreads.threadInitNumber.incrementAndGet();
    }

    /** This constructor is called only by {@code VirtualThread}. */
    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    private Target_java_lang_Thread(String name, int characteristics, boolean bound) {
        VMError.guarantee(!bound, "Bound virtual threads are not supported");

        /* Non-0 instance field initialization. */
        this.interruptLock = new Object();

        this.name = (name != null) ? name : "";
        this.tid = Target_java_lang_Thread_ThreadIdentifiers.next();
        this.inheritedAccessControlContext = Target_java_lang_Thread_Constants.NO_PERMISSIONS_ACC;

        boolean allowThreadLocals = (characteristics & NO_THREAD_LOCALS) == 0;
        boolean inheritThreadLocals = (characteristics & NO_INHERIT_THREAD_LOCALS) == 0;
        JavaThreads.initNewThreadLocalsAndLoader(this, allowThreadLocals, inheritThreadLocals, Thread.currentThread());
    }

    @SuppressWarnings("hiding")
    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private void start0() {
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            throw VMError.unsupportedFeature("Single-threaded VM cannot create new threads");
        }

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

    @Substitute
    @TargetElement(onlyWith = JDK17OrEarlier.class)
    public static boolean interrupted() {
        return JavaThreads.getAndClearInterrupt(Thread.currentThread());
    }

    @Delete
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private native boolean isInterrupted(boolean clearInterrupted);

    /**
     * Marks the thread as interrupted and wakes it up.
     *
     * See {@link PlatformThreads#parkCurrentPlatformOrCarrierThread()},
     * {@link PlatformThreads#unpark} and {@link JavaThreads#sleep} for vital aspects of the
     * underlying mechanisms.
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
        PlatformThreads.interrupt(thread);
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
    @TargetElement(onlyWith = JDK17OrEarlier.class)
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private boolean isAlive() {
        return JavaThreads.isAlive(JavaThreads.fromTarget(this));
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    private boolean isAlive0() {
        return PlatformThreads.isAlive(JavaThreads.fromTarget(this));
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrEarlier.class)
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private static void yield() {
        JavaThreads.yieldCurrent();
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    private static void yield0() {
        // Loom virtual threads are handled in yield()
        JavaThreads.yieldCurrent();
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrEarlier.class)
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private static void sleep(long millis) throws InterruptedException {
        JavaThreads.sleep(millis);
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    private static void sleep0(long millis) throws InterruptedException {
        // Loom virtual threads are handled in sleep()
        JavaThreads.sleep(millis);
    }

    @Substitute
    @TargetElement
    public void join(long millis) throws InterruptedException {
        JavaThreads.join(JavaThreads.fromTarget(this), millis);
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

    /** @see com.oracle.svm.core.jdk.StackTraceUtils#asyncGetStackTrace */
    @Delete
    @TargetElement(onlyWith = JDK19OrLater.class)
    native StackTraceElement[] asyncGetStackTrace();

    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        return PlatformThreads.getAllStackTraces();
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    private static Thread[] getAllThreads() {
        return PlatformThreads.getAllThreads();
    }

    /**
     * In the JDK, this is a no-op except on Windows where the JDK resets the interrupt event used
     * by {@code Process.waitFor} with {@code ResetEvent((HANDLE) JVM_GetThreadInterruptEvent());}
     * Our implementation in {@code WindowsPlatformThreads} already handles this.
     */
    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    private static void clearInterruptEvent() {
    }

    @Alias
    @TargetElement(onlyWith = JDK19OrLater.class)
    native void setInterrupt();

    @Alias
    @TargetElement(onlyWith = JDK19OrLater.class)
    native void clearInterrupt();

    /** Carrier threads only: the current innermost continuation. */
    @Alias @TargetElement(onlyWith = LoomJDK.class) //
    Target_jdk_internal_vm_Continuation cont;

    @Alias
    @TargetElement(onlyWith = LoomJDK.class)
    public static native Target_java_lang_Thread_Builder ofVirtual();

    /** This method being reachable fails the image build, see {@link ContinuationsFeature}. */
    @Substitute
    @TargetElement(name = "ofVirtual", onlyWith = {JDK19OrLater.class, NotLoomJDK.class})
    public static Target_java_lang_Thread_Builder ofVirtualWithoutLoom() {
        throw VMError.shouldNotReachHere();
    }

    /** This method being reachable fails the image build, see {@link ContinuationsFeature}. */
    @Substitute
    @TargetElement(onlyWith = {JDK19OrLater.class, NotLoomJDK.class})
    static Thread startVirtualThread(Runnable task) {
        throw VMError.shouldNotReachHere();
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    static Object[] extentLocalCache() {
        return JavaThreads.toTarget(currentCarrierThread()).extentLocalCache;
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    static void setExtentLocalCache(Object[] cache) {
        JavaThreads.toTarget(currentCarrierThread()).extentLocalCache = cache;
    }

    @Substitute
    static void blockedOn(Target_sun_nio_ch_Interruptible b) {
        JavaThreads.blockedOn(b);
    }

    @Alias
    @TargetElement(onlyWith = JDK19OrLater.class)
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
    @TargetElement(onlyWith = JDK19OrLater.class)
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isTerminated() {
        return (holder.threadStatus & JVMTI_THREAD_STATE_TERMINATED) != 0;
    }

    @Alias
    @TargetElement(onlyWith = JDK19OrLater.class)
    native Target_jdk_internal_vm_ThreadContainer threadContainer();

    @Alias
    @TargetElement(onlyWith = JDK19OrLater.class)
    native long threadId();
}

@TargetClass(value = Thread.class, innerClass = "Builder", onlyWith = JDK19OrLater.class)
interface Target_java_lang_Thread_Builder {
    @Alias
    ThreadFactory factory();
}

@TargetClass(value = Thread.class, innerClass = "Constants", onlyWith = JDK19OrLater.class)
final class Target_java_lang_Thread_Constants {
    // Checkstyle: stop
    @SuppressWarnings("removal") @Alias static AccessControlContext NO_PERMISSIONS_ACC;

    @Alias static ClassLoader NOT_SUPPORTED_CLASSLOADER;
    // Checkstyle: resume
}

@TargetClass(value = Thread.class, innerClass = "FieldHolder", onlyWith = JDK19OrLater.class)
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
@TargetClass(value = Thread.class, innerClass = "ThreadIdentifiers", onlyWith = LoomJDK.class)
final class Target_java_lang_Thread_ThreadIdentifiers {
    @Substitute//
    static long next() {
        return JavaThreads.threadSeqNumber.incrementAndGet();
    }
}

@TargetClass(className = "sun.nio.ch.Interruptible")
interface Target_sun_nio_ch_Interruptible {
    @Alias
    void interrupt(Thread t);
}
