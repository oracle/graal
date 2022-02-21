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

import java.lang.Thread.UncaughtExceptionHandler;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.jdk.Target_jdk_internal_misc_VM;
import com.oracle.svm.core.snippets.KnownIntrinsics;

/**
 * Implements operations on {@linkplain Target_java_lang_Thread Java threads}, which are on a higher
 * abstraction level than {@link IsolateThread}s. This class distinguishes these types of threads:
 *
 * <ul>
 * <li><em>Platform threads</em> are typical Java threads which correspond to an OS thread.</li>
 * <li><em>Virtual threads</em> are light-weight threads that exist as Java objects, but are not
 * associated with an OS thread. They are temporarily mounted (scheduled) on one or several,
 * potentially different, platform threads.</li>
 * <li><em>Carrier thread</em> is the term for the platform thread on which a virtual thread is
 * currently mounted (if it is mounted). Typically there is a pool of potential carrier threads
 * which are available to execute virtual threads.</li>
 * </ul>
 *
 * Methods with <em>platform</em> or <em>carrier</em> in their names must be called <em>only</em>
 * for that type of thread. Methods without that designation distinguish between the thread types
 * and choose the appropriate action.
 *
 * @see VirtualThreads
 * @see <a href="https://openjdk.java.net/projects/loom/">Wiki and source code of Project Loom on
 *      which concepts of virtual threads, carrier threads, etc. are modeled</a>
 */
public final class JavaThreads {
    /** For Thread.nextThreadID(). */
    static final AtomicLong threadSeqNumber = new AtomicLong();
    /** For Thread.nextThreadNum(). */
    static final AtomicInteger threadInitNumber = new AtomicInteger();

    private JavaThreads() {
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
        return getInterruptedFlag(thread);
    }

    private static boolean getInterruptedFlag(Thread thread) {
        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            return toTarget(thread).interruptedJDK17OrLater;
        }
        return toTarget(thread).interruptedJDK11OrEarlier;
    }

    static boolean getAndClearInterrupt(Thread thread) {
        if (supportsVirtual() && isVirtual(thread)) {
            return VirtualThreads.singleton().getAndClearInterrupt(thread);
        }
        return getAndClearInterruptedFlag(thread);
    }

    static boolean getAndClearInterruptedFlag(Thread thread) {
        /*
         * As we don't use a lock, it is possible to observe any kinds of races with other threads
         * that try to set the interrupted status to true. However, those races don't cause any
         * correctness issues as we only reset it to false if we observed that it was true earlier.
         * There also can't be any problematic races with other calls to check the interrupt status
         * because it is cleared only by the current thread.
         */
        boolean oldValue = isInterrupted(thread);
        if (oldValue) {
            writeInterruptedFlag(thread, false);
        }
        return oldValue;
    }

    static void writeInterruptedFlag(Thread thread, boolean value) {
        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            toTarget(thread).interruptedJDK17OrLater = value;
        } else {
            toTarget(thread).interruptedJDK11OrEarlier = value;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getParentThreadId(Thread thread) {
        return toTarget(thread).parentThreadId;
    }

    @Fold
    static boolean supportsVirtual() {
        return VirtualThreads.isSupported();
    }

    static boolean isVirtual(Thread thread) {
        return supportsVirtual() && VirtualThreads.singleton().isVirtual(thread);
    }

    private static boolean isVirtualDisallowLoom(Thread thread) {
        if (LoomSupport.isEnabled()) {
            assert !isVirtual(thread) : "should not see Loom virtual thread objects here";
            return false;
        }
        return isVirtual(thread);
    }

    @SuppressFBWarnings(value = "BC", justification = "Cast for @TargetClass")
    static Target_java_lang_ThreadGroup toTarget(ThreadGroup threadGroup) {
        return Target_java_lang_ThreadGroup.class.cast(threadGroup);
    }

    static void join(Thread thread, long millis) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        if (supportsVirtual() && isVirtual(thread)) {
            VirtualThreads.singleton().join(thread, millis);
        } else {
            PlatformThreads.join(thread, millis);
        }
    }

    static void yieldCurrent() {
        if (supportsVirtual() && isVirtualDisallowLoom(Thread.currentThread())) {
            VirtualThreads.singleton().yield();
        } else {
            PlatformThreads.singleton().yieldCurrent();
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
        if (isVirtual(thread)) {
            return Target_java_lang_Thread.EMPTY_STACK_TRACE;
        }
        return PlatformThreads.getStackTrace(thread);
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

        final Thread parent = PlatformThreads.currentThread.get();
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

        if (!LoomSupport.isEnabled() && !(VirtualThreads.isSupported() && VirtualThreads.singleton().isVirtual(fromTarget(tjlt)))) {
            JavaThreads.toTarget(group).addUnstarted();
        }

        tjlt.contextClassLoader = parent.getContextClassLoader();

        tjlt.inheritedAccessControlContext = acc != null ? acc : AccessController.getContext();

        /* Set thread ID */
        tjlt.tid = Target_java_lang_Thread.nextThreadID();
    }

    static void sleep(long millis) throws InterruptedException {
        if (supportsVirtual() && isVirtualDisallowLoom(Thread.currentThread())) {
            VirtualThreads.singleton().sleepMillis(millis);
        } else {
            PlatformThreads.sleep(millis);
        }
    }

    static boolean isAlive(Thread thread) {
        if (supportsVirtual() && isVirtualDisallowLoom(thread)) {
            return VirtualThreads.singleton().isAlive(thread);
        }
        return PlatformThreads.isAlive(thread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void setCurrentThreadLockHelper(Object helper) {
        if (supportsVirtual()) {
            toTarget(Thread.currentThread()).lockHelper = helper;
        } else {
            PlatformThreads.lockHelper.set(helper);
        }
    }

    @AlwaysInline("Locking fast path.")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Object getCurrentThreadLockHelper() {
        if (supportsVirtual()) {
            return toTarget(Thread.currentThread()).lockHelper;
        }
        return PlatformThreads.lockHelper.get();
    }
}
