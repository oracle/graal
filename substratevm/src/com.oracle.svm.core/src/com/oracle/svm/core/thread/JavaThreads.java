/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.imagelayer.LastImageBuildPredicate;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.ApplicationLayerOnly;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.word.Word;

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
 */
public final class JavaThreads {
    /**
     * The {@linkplain JavaThreads#getThreadId thread id} of the {@link Thread#currentThread()},
     * which can be a {@linkplain Target_java_lang_Thread#vthread virtual thread} or a
     * {@linkplain PlatformThreads#currentThread platform thread}.
     *
     * As the value of the thread local can change over the thread lifetime (see carrier threads),
     * it should only be accessed by the owning thread (via {@link FastThreadLocalLong#get()} and
     * {@link FastThreadLocalLong#set(long)}).
     */
    static final FastThreadLocalLong currentVThreadId = FastThreadLocalFactory.createLong("JavaThreads.currentVThreadId").setMaxOffset(FastThreadLocal.BYTE_OFFSET);

    @AutomaticallyRegisteredImageSingleton(onlyWith = LastImageBuildPredicate.class)
    @SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = ApplicationLayerOnly.class)
    public static class JavaThreadNumberSingleton {

        public static JavaThreadNumberSingleton singleton() {
            return ImageSingletons.lookup(JavaThreadNumberSingleton.class);
        }

        /** For Thread.nextThreadID(). */
        final AtomicLong threadSeqNumber;
        /** For Thread.nextThreadNum(). */
        final AtomicInteger threadInitNumber;

        public JavaThreadNumberSingleton() {
            this.threadSeqNumber = new AtomicLong();
            this.threadInitNumber = new AtomicInteger();
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public void setThreadNumberInfo(long seqNumber, int initNumber) {
            this.threadSeqNumber.set(seqNumber);
            this.threadInitNumber.set(initNumber);
        }
    }

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

    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    static long nextThreadID() {
        return JavaThreadNumberSingleton.singleton().threadSeqNumber.incrementAndGet();
    }

    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    static int nextThreadNum() {
        return JavaThreadNumberSingleton.singleton().threadInitNumber.incrementAndGet();
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
     * Similar to {@link Thread#getThreadGroup()} but without any of the extra checks that the JDK
     * code does (e.g., the method below does not check for virtual threads or
     * {@code Thread.isTerminated()}).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static ThreadGroup getRawThreadGroup(Thread thread) {
        Target_java_lang_Thread t = SubstrateUtil.cast(thread, Target_java_lang_Thread.class);
        Target_java_lang_Thread_FieldHolder holder = t.holder;
        if (holder != null) {
            return holder.group;
        }
        return null;
    }

    /**
     * Safe method to check whether a thread has been interrupted.
     *
     * Use instead of {@link Thread#isInterrupted()}, which is not {@code final} and can be
     * overridden with code that does locking or performs other actions that are unsafe especially
     * in VM-internal contexts.
     */
    public static boolean isInterrupted(Thread thread) {
        return toTarget(thread).interrupted;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getParentThreadId(Thread thread) {
        return toTarget(thread).parentThreadId;
    }

    /**
     * Indicates whether a thread is <em>truly</em> virtual, whereas {@link Thread#isVirtual()} also
     * returns {@code true} for platform threads of type {@code BoundVirtualThread}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isVirtual(Thread thread) {
        return Target_java_lang_VirtualThread.class.isInstance(thread);
    }

    /** @see #isVirtual */
    public static boolean isCurrentThreadVirtual() {
        Thread thread = PlatformThreads.currentThread.get();
        return thread != null && toTarget(thread).vthread != null;
    }

    /**
     * Indicates whether the current thread is <em>truly</em> virtual (see {@link #isVirtual}) and
     * currently pinned to its carrier thread.
     */
    @NeverInline("Prevent a reference to the current carrier thread from leaking into the caller frame.")
    public static boolean isCurrentThreadVirtualAndPinned() {
        Target_java_lang_Thread carrier = JavaThreads.toTarget(Target_java_lang_Thread.currentCarrierThread());
        return carrier != null && carrier.vthread != null && Target_jdk_internal_vm_Continuation.isPinned(carrier.cont.getScope());
    }

    @SuppressFBWarnings(value = "BC", justification = "Cast for @TargetClass")
    static Target_java_lang_ThreadGroup toTarget(ThreadGroup threadGroup) {
        return Target_java_lang_ThreadGroup.class.cast(threadGroup);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @SuppressFBWarnings(value = "BC", justification = "Cast for @TargetClass")
    public static Target_java_lang_VirtualThread toVirtualTarget(Thread thread) {
        return Target_java_lang_VirtualThread.class.cast(thread);
    }

    @NeverInline("Starting a stack walk in the caller frame")
    public static StackTraceElement[] getStackTrace(boolean filterExceptions, Thread thread) {
        /*
         * If our own thread's stack was requested, we can walk it without a VMOperation using a
         * stack pointer. It is intentional that we use the caller stack pointer: the calling
         * Thread.getStackTrace method itself needs to be included in the result.
         */
        Pointer callerSP = KnownIntrinsics.readCallerStackPointer();

        if (isVirtual(thread)) {
            if (thread == Thread.currentThread()) {
                return getMountedVirtualThreadStackTrace(filterExceptions, thread, callerSP);
            }
            assert !filterExceptions : "exception stack traces can be taken only for the current thread";
            return asyncGetVirtualThreadStackTrace(toVirtualTarget(thread));
        } else {
            return PlatformThreads.getStackTrace(filterExceptions, thread, callerSP);
        }
    }

    private static StackTraceElement[] getMountedVirtualThreadStackTrace(boolean filterExceptions, Thread thread, Pointer callerSP) {
        Thread carrier = toVirtualTarget(thread).carrierThread;
        if (carrier == null) {
            return null;
        }
        Pointer endSP = PlatformThreads.getCarrierSPOrElse(carrier, Word.nullPointer());
        if (endSP.isNull()) {
            return null;
        }
        if (carrier == PlatformThreads.currentThread.get()) {
            return StackTraceUtils.getCurrentThreadStackTrace(filterExceptions, callerSP, endSP);
        }
        assert VMOperation.isInProgressAtSafepoint();
        return StackTraceUtils.getStackTraceAtSafepoint(PlatformThreads.getIsolateThread(carrier), endSP);
    }

    public static StackTraceElement[] getStackTraceAtSafepoint(Thread thread, Pointer callerSP) {
        if (isVirtual(thread)) {
            return getMountedVirtualThreadStackTrace(false, thread, callerSP);
        } else {
            return PlatformThreads.getStackTraceAtSafepoint(thread, callerSP);
        }
    }

    /** Adapted from {@code VirtualThread.asyncGetStackTrace()}. */
    private static StackTraceElement[] asyncGetVirtualThreadStackTrace(Target_java_lang_VirtualThread thread) {
        StackTraceElement[] stackTrace;
        do {
            if (thread.carrierThread != null) {
                stackTrace = StackTraceUtils.asyncGetStackTrace(SubstrateUtil.cast(thread, Thread.class));
            } else {
                stackTrace = thread.tryGetStackTrace();
            }
            if (stackTrace == null) {
                Thread.yield();
            }
        } while (stackTrace == null);
        return stackTrace;
    }

    @NeverInline("Starting a stack walk in the caller frame")
    public static void visitCurrentStackFrames(StackFrameVisitor visitor) {
        /*
         * If our own thread's stack was requested, we can walk it without a VMOperation using a
         * stack pointer. It is intentional that we use the caller stack pointer: the calling
         * Thread.getStackTrace method itself needs to be included in the result.
         */
        Pointer callerSP = KnownIntrinsics.readCallerStackPointer();

        if (isVirtual(Thread.currentThread())) {
            visitCurrentVirtualThreadStackFrames(callerSP, visitor);
        } else {
            PlatformThreads.visitCurrentStackFrames(callerSP, visitor);
        }
    }

    private static void visitCurrentVirtualThreadStackFrames(Pointer callerSP, StackFrameVisitor visitor) {
        Thread carrier = toVirtualTarget(Thread.currentThread()).carrierThread;
        if (carrier != null) {
            Pointer endSP = PlatformThreads.getCarrierSPOrElse(carrier, Word.nullPointer());
            if (endSP.isNonNull()) {
                StackTraceUtils.visitCurrentThreadStackFrames(callerSP, endSP, visitor);
            }
        }
    }

    public static void dispatchUncaughtException(Thread thread, Throwable throwable) {
        try {
            /* Get the uncaught exception handler for the Thread, or the default one. */
            UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
            if (handler == null) {
                handler = Thread.getDefaultUncaughtExceptionHandler();
            }

            if (handler != null) {
                handler.uncaughtException(thread, throwable);
            } else {
                /*
                 * If no uncaught exception handler is present, then just report the Throwable in
                 * the same way as it is done by ThreadGroup.uncaughtException().
                 */
                System.err.print("Exception in thread \"" + thread.getName() + "\" ");
                throwable.printStackTrace(System.err);
            }
        } catch (Throwable e) {
            /* See JavaThread::exit() in HotSpot. */
            Log.log().newline().string("Exception: ").string(e.getClass().getName()).string(" thrown from the UncaughtExceptionHandler in thread \"").string(thread.getName()).string("\"").newline();
        }
    }

    static void initThreadFields(Target_java_lang_Thread tjlt, ThreadGroup group, Runnable target, long stackSize, int priority, boolean daemon) {
        assert tjlt.holder == null;
        tjlt.holder = new Target_java_lang_Thread_FieldHolder(group, target, stackSize, priority, daemon);
    }

    static void initNewThreadLocalsAndLoader(Target_java_lang_Thread tjlt, boolean inheritThreadLocals, Thread parent) {
        if (inheritThreadLocals) {
            Target_java_lang_ThreadLocal_ThreadLocalMap parentMap = toTarget(parent).inheritableThreadLocals;
            if (parentMap != null && parentMap.size() > 0) {
                tjlt.inheritableThreadLocals = Target_java_lang_ThreadLocal.createInheritedMap(parentMap);
            }
            tjlt.contextClassLoader = parent.getContextClassLoader();
        } else {
            tjlt.contextClassLoader = ClassLoader.getSystemClassLoader();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setCurrentThreadLockHelper(Object helper) {
        toTarget(Thread.currentThread()).lockHelper = helper;
    }

    @AlwaysInline("Locking fast path.")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Object getCurrentThreadLockHelper() {
        return toTarget(Thread.currentThread()).lockHelper;
    }

    /**
     * Returns the result of calling {@link #getThreadId} on {@link Thread#currentThread}, but from
     * a thread-local cache with potentially fewer accesses.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getCurrentThreadId() {
        long id = currentVThreadId.get();
        if (GraalDirectives.inIntrinsic()) {
            // The condition may throw so we must manually wrap the assert in this if, otherwise the
            // compiler is not allowed to remove the evaluation
            if (ReplacementsUtil.REPLACEMENTS_ASSERTIONS_ENABLED) {
                ReplacementsUtil.dynamicAssert(id != 0 && id == getThreadId(Thread.currentThread()), "ids must match");
            }
        } else {
            assert id != 0 && id == getThreadId(Thread.currentThread());
        }
        return id;
    }

    /**
     * Similar to {@link #getCurrentThreadId()} but returns 0 if the thread id is not present. There
     * is a small number of situations where the thread id might not be available, e.g., when a
     * freshly attached thread causes a GC (before it initializes its {@link java.lang.Thread}
     * object) or when a VM operation is enqueued by a non-Java thread.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getCurrentThreadIdOrZero() {
        if (CurrentIsolate.getCurrentThread().isNonNull()) {
            return currentVThreadId.get();
        }
        return 0L;
    }

    @Uninterruptible(reason = "Ensure consistency of vthread and cached vthread id.")
    static void setCurrentThread(Thread carrier, Thread thread) {
        assert carrier == PlatformThreads.currentThread.get();
        assert thread == carrier || isVirtual(thread);
        toTarget(carrier).vthread = (thread != carrier) ? thread : null;
        currentVThreadId.set(getThreadId(thread));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Thread getCurrentThreadOrNull() {
        Thread thread = PlatformThreads.currentThread.get();
        if (thread == null) {
            return null;
        }

        Target_java_lang_Thread tjlt = SubstrateUtil.cast(thread, Target_java_lang_Thread.class);
        return (tjlt.vthread != null) ? tjlt.vthread : thread;
    }
}
