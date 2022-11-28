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

import java.lang.Thread.UncaughtExceptionHandler;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.events.ThreadSleepEvent;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.util.ReflectionUtil;

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

    /**
     * Returns the unique identifier of this thread. This method is necessary because
     * {@code Thread#getId()} is a non-final method that can be overridden.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getThreadId(Thread thread) {
        if (SubstrateUtil.HOSTED) {
            return ReflectionUtil.readField(Thread.class, "tid", thread);
        } else {
            return toTarget(thread).tid;
        }
    }

    /**
     * Safe method to check whether a thread has been interrupted.
     *
     * Use instead of {@link Thread#isInterrupted()}, which is not {@code final} and can be
     * overridden with code that does locking or performs other actions that are unsafe especially
     * in VM-internal contexts.
     */
    public static boolean isInterrupted(Thread thread) {
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

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public static boolean isVirtual(Thread thread) {
        return supportsVirtual() && VirtualThreads.singleton().isVirtual(thread);
    }

    /** @see PlatformThreads#setCurrentThread */
    public static boolean isCurrentThreadVirtual() {
        if (!supportsVirtual()) {
            return false;
        }
        Thread thread = PlatformThreads.currentThread.get();
        return thread != null && toTarget(thread).vthread != null;
    }

    @AlwaysInline("Enable constant folding in case of Loom.")
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
        if (supportsVirtual() && isVirtualDisallowLoom(Thread.currentThread()) && !LoomSupport.isEnabled()) {
            VirtualThreads.singleton().yield();
        } else {
            PlatformThreads.singleton().yieldCurrent();
        }
    }

    @NeverInline("Starting a stack walk in the caller frame")
    public static StackTraceElement[] getStackTrace(boolean filterExceptions, Thread thread) {
        /*
         * If our own thread's stack was requested, we can walk it without a VMOperation using a
         * stack pointer. It is intentional that we use the caller stack pointer: the calling
         * Thread.getStackTrace method itself needs to be included in the result.
         */
        Pointer callerSP = KnownIntrinsics.readCallerStackPointer();

        if (supportsVirtual()) { // NOTE: also for platform threads!
            return VirtualThreads.singleton().getVirtualOrPlatformThreadStackTrace(filterExceptions, thread, callerSP);
        }
        return PlatformThreads.getStackTrace(filterExceptions, thread, callerSP);
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
     * </ul>
     */
    @SuppressWarnings({"deprecation", "removal"}) // AccessController is deprecated starting JDK 17
    static void initializeNewThread(
                    Target_java_lang_Thread tjlt,
                    ThreadGroup groupArg,
                    Runnable target,
                    String name,
                    long stackSize,
                    AccessControlContext acc,
                    boolean allowThreadLocals,
                    boolean inheritThreadLocals) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        tjlt.name = name;

        final Thread parent = Thread.currentThread();
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

        initThreadFields(tjlt, group, target, stackSize, priority, daemon);

        if (!VirtualThreads.isSupported() || !VirtualThreads.singleton().isVirtual(fromTarget(tjlt))) {
            PlatformThreads.setThreadStatus(fromTarget(tjlt), ThreadStatus.NEW);
            if (JavaVersionUtil.JAVA_SPEC < 19) {
                JavaThreads.toTarget(group).addUnstarted();
            }
        }

        tjlt.inheritedAccessControlContext = acc != null ? acc : AccessController.getContext();

        initNewThreadLocalsAndLoader(tjlt, allowThreadLocals, inheritThreadLocals, parent);

        /* Set thread ID */
        tjlt.tid = Target_java_lang_Thread.nextThreadID();
    }

    static void initThreadFields(Target_java_lang_Thread tjlt, ThreadGroup group, Runnable target, long stackSize, int priority, boolean daemon) {
        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            assert tjlt.holder == null;
            tjlt.holder = new Target_java_lang_Thread_FieldHolder(group, target, stackSize, priority, daemon);
        } else {
            tjlt.group = group;
            tjlt.priority = priority;
            tjlt.daemon = daemon;
            tjlt.target = target;
            tjlt.setPriority(priority);

            /* Stash the specified stack size in case the VM cares */
            tjlt.stackSize = stackSize;
        }
    }

    static void initNewThreadLocalsAndLoader(Target_java_lang_Thread tjlt, boolean allowThreadLocals, boolean inheritThreadLocals, Thread parent) {
        if (JavaVersionUtil.JAVA_SPEC >= 19 && !allowThreadLocals) {
            tjlt.threadLocals = Target_java_lang_ThreadLocal_ThreadLocalMap.NOT_SUPPORTED;
            tjlt.inheritableThreadLocals = Target_java_lang_ThreadLocal_ThreadLocalMap.NOT_SUPPORTED;
            tjlt.contextClassLoader = Target_java_lang_Thread_Constants.NOT_SUPPORTED_CLASSLOADER;
        } else if (inheritThreadLocals) {
            Target_java_lang_ThreadLocal_ThreadLocalMap parentMap = toTarget(parent).inheritableThreadLocals;
            if (parentMap != null && (JavaVersionUtil.JAVA_SPEC < 19 || (parentMap != Target_java_lang_ThreadLocal_ThreadLocalMap.NOT_SUPPORTED && parentMap.size() > 0))) {
                tjlt.inheritableThreadLocals = Target_java_lang_ThreadLocal.createInheritedMap(parentMap);
            }
            ClassLoader parentLoader = parent.getContextClassLoader();
            if (JavaVersionUtil.JAVA_SPEC < 19 || parentLoader != Target_java_lang_Thread_Constants.NOT_SUPPORTED_CLASSLOADER) {
                tjlt.contextClassLoader = parentLoader;
            } else {
                tjlt.contextClassLoader = ClassLoader.getSystemClassLoader();
            }
        } else {
            tjlt.contextClassLoader = ClassLoader.getSystemClassLoader();
        }
    }

    static void sleep(long millis) throws InterruptedException {
        long startTicks = com.oracle.svm.core.jfr.JfrTicks.elapsedTicks();
        if (supportsVirtual() && isVirtualDisallowLoom(Thread.currentThread()) && !LoomSupport.isEnabled()) {
            VirtualThreads.singleton().sleepMillis(millis);
        } else {
            PlatformThreads.sleep(millis);
        }
        ThreadSleepEvent.emit(millis, startTicks);
    }

    static boolean isAlive(Thread thread) {
        if (supportsVirtual() && isVirtualDisallowLoom(thread) && !LoomSupport.isEnabled()) {
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

    public static void blockedOn(Target_sun_nio_ch_Interruptible b) {
        if (supportsVirtual() && isCurrentThreadVirtual()) {
            VirtualThreads.singleton().blockedOn(b);
        } else {
            PlatformThreads.blockedOn(b);
        }
    }

    /**
     * Returns the result of calling {@link #getThreadId} on {@link Thread#currentThread}, but from
     * a thread-local cache with potentially fewer accesses.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getCurrentThreadId() {
        long id = PlatformThreads.currentVThreadId.get();
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.dynamicAssert(id == getThreadId(Thread.currentThread()), "ids must match");
        } else {
            assert id == getThreadId(Thread.currentThread());
        }
        return id;
    }
}
