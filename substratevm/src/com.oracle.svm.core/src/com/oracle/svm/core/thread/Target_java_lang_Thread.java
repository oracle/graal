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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.MonitorSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.JavaLangSubstitutions;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicReference;
import com.oracle.svm.core.option.XOptions;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.ParkEvent.WaitResult;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

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
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadIdRecomputation.class) //
    long tid;

    /** We have our own atomic number in {@link JavaThreads#threadSeqNumber}. */
    @Delete//
    static long threadSeqNumber;
    /** We have our own atomic number in {@link JavaThreads#threadInitNumber}. */
    @Delete//
    static int threadInitNumber;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadStatusRecomputation.class) //
    int threadStatus;

    @Alias//
    /* private */ /* final */ Object blockerLock;

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
        return JavaThreads.singleton().threadInitNumber.incrementAndGet();
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
        group = (withGroup != null) ? withGroup : JavaThreads.singleton().mainGroup;
        priority = Thread.NORM_PRIORITY;
        contextClassLoader = SubstrateUtil.cast(ImageSingletons.lookup(JavaLangSubstitutions.ClassLoaderSupport.class).systemClassLoader, ClassLoader.class);
        blockerLock = new Object();
        daemon = asDaemon;
    }

    @Substitute
    static Thread currentThread() {
        Thread result = JavaThreads.currentThread.get();
        assert result != null : "java.lang.Thread not assigned when thread was attached to the VM";
        return result;
    }

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private void init(ThreadGroup groupArg, Runnable targetArg, String nameArg, long stackSizeArg) {
        /* Injected Target_java_lang_Thread instance field initialization. */
        this.unsafeParkEvent = new AtomicReference<>();
        this.sleepParkEvent = new AtomicReference<>();
        /* Initialize the rest of the Thread object. */
        JavaThreads.initializeNewThread(this, groupArg, targetArg, nameArg, stackSizeArg);
    }

    @Substitute
    @SuppressWarnings({"unused"})
    @TargetElement(onlyWith = JDK11OrLater.class)
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
        this.unsafeParkEvent = new AtomicReference<>();
        this.sleepParkEvent = new AtomicReference<>();
        /* Initialize the rest of the Thread object, ignoring `acc` and `inheritThreadLocals`. */
        JavaThreads.initializeNewThread(this, g, target, name, stackSize);
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

        if (chosenStackSize != 0) {
            /*
             * Add the yellow+red zone size: This area of the stack is not accessible to the user's
             * Java code, so it would be surprising if we gave the user less stack space to use than
             * explicitly requested. In particular, a size less than the yellow+red size would lead
             * to an immediate StackOverflowError.
             */
            chosenStackSize += StackOverflowCheck.singleton().yellowAndRedZoneSize();
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
        JavaThreads.singleton().doStartThread(JavaThreads.fromTarget(this), chosenStackSize);
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    protected void setNativeName(String name) {
        JavaThreads.singleton().setNativeName(JavaThreads.fromTarget(this), name);
    }

    @Substitute
    private void setPriority0(int priority) {
    }

    @Substitute
    private boolean isInterrupted(boolean clearInterrupted) {
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
        JavaThreads.interrupt(JavaThreads.fromTarget(this));
        JavaThreads.unpark(JavaThreads.fromTarget(this));
        /* Interrupt anyone waiting on a VMCondVar. */
        JavaThreads.interruptVMCondVars();
    }

    @Substitute
    private boolean isAlive() {
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
        WaitResult sleepResult = JavaThreads.sleep(TimeUtils.millisToNanos(millis));
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
    @NeverInline("Starting a stack walk in the caller frame")
    private StackTraceElement[] getStackTrace() {
        if (JavaThreads.fromTarget(this) == Thread.currentThread()) {
            /* We can walk our own stack without a VMOperation. */
            return StackTraceUtils.getStackTrace(false, KnownIntrinsics.readCallerStackPointer());
        } else {
            return JavaThreads.getStackTrace(JavaThreads.fromTarget(this));
        }
    }

    @Substitute
    private static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        return JavaThreads.getAllStackTraces();
    }
}
