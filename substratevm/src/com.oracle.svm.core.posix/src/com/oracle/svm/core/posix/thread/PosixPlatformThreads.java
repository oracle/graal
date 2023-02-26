/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.thread;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveDetachThreadEpilogue;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.monitor.JavaMonitor;
import com.oracle.svm.core.os.IsDefined;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Pthread.pthread_attr_t;
import com.oracle.svm.core.posix.headers.Sched;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.darwin.DarwinPthread;
import com.oracle.svm.core.posix.headers.linux.LinuxPthread;
import com.oracle.svm.core.posix.pthread.PthreadConditionUtils;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.ParkEvent;
import com.oracle.svm.core.thread.ParkEvent.ParkEventFactory;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.internal.misc.Unsafe;

@AutomaticallyRegisteredImageSingleton(PlatformThreads.class)
public final class PosixPlatformThreads extends PlatformThreads {

    @SuppressFBWarnings(value = "BC", justification = "Cast for @TargetClass")
    private static Target_java_lang_Thread toTarget(Thread thread) {
        return Target_java_lang_Thread.class.cast(thread);
    }

    @Platforms(HOSTED_ONLY.class)
    PosixPlatformThreads() {
    }

    @Override
    protected boolean doStartThread(Thread thread, long stackSize) {
        pthread_attr_t attributes = UnsafeStackValue.get(pthread_attr_t.class);
        if (Pthread.pthread_attr_init(attributes) != 0) {
            return false;
        }
        try {
            if (Pthread.pthread_attr_setdetachstate(attributes, Pthread.PTHREAD_CREATE_JOINABLE()) != 0) {
                return false;
            }

            UnsignedWord threadStackSize = WordFactory.unsigned(stackSize);
            /* If there is a chosen stack size, use it as the stack size. */
            if (threadStackSize.notEqual(WordFactory.zero())) {
                /* Make sure the chosen stack size is large enough. */
                threadStackSize = UnsignedUtils.max(threadStackSize, Pthread.PTHREAD_STACK_MIN());
                /* Make sure the chosen stack size is a multiple of the system page size. */
                threadStackSize = UnsignedUtils.roundUp(threadStackSize, WordFactory.unsigned(Unistd.getpagesize()));

                if (Pthread.pthread_attr_setstacksize(attributes, threadStackSize) != 0) {
                    return false;
                }
            }

            ThreadStartData startData = prepareStart(thread, SizeOf.get(ThreadStartData.class));

            Pthread.pthread_tPointer newThread = UnsafeStackValue.get(Pthread.pthread_tPointer.class);
            if (Pthread.pthread_create(newThread, attributes, PosixPlatformThreads.pthreadStartRoutine.getFunctionPointer(), startData) != 0) {
                undoPrepareStartOnError(thread, startData);
                return false;
            }

            setPthreadIdentifier(thread, newThread.read());
            return true;
        } finally {
            Pthread.pthread_attr_destroy(attributes);
        }
    }

    private static void setPthreadIdentifier(Thread thread, Pthread.pthread_t pthread) {
        toTarget(thread).hasPthreadIdentifier = true;
        toTarget(thread).pthreadIdentifier = pthread;
    }

    static Pthread.pthread_t getPthreadIdentifier(Thread thread) {
        return toTarget(thread).pthreadIdentifier;
    }

    static boolean hasThreadIdentifier(Thread thread) {
        return toTarget(thread).hasPthreadIdentifier;
    }

    /**
     * Try to set the native name of the current thread.
     *
     * Failures are ignored.
     */
    @Override
    protected void setNativeName(Thread thread, String name) {
        if (!hasThreadIdentifier(thread)) {
            /*
             * The thread was not started from Java code, but started from C code and attached
             * manually to SVM. We do not want to interfere with such threads.
             */
            return;
        }

        if (IsDefined.isDarwin() && thread != Thread.currentThread()) {
            /* Darwin only allows setting the name of the current thread. */
            return;
        }

        /* Use at most 15 characters from the right end of the name. */
        final int startIndex = Math.max(0, name.length() - 15);
        final String pthreadName = name.substring(startIndex);
        assert pthreadName.length() < 16 : "thread name for pthread has a maximum length of 16 characters including the terminating 0";
        try (CCharPointerHolder threadNameHolder = CTypeConversion.toCString(pthreadName)) {
            if (IsDefined.isLinux()) {
                LinuxPthread.pthread_setname_np(getPthreadIdentifier(thread), threadNameHolder.get());
            } else if (IsDefined.isDarwin()) {
                assert thread == Thread.currentThread() : "Darwin only allows setting the name of the current thread";
                DarwinPthread.pthread_setname_np(threadNameHolder.get());
            } else {
                VMError.unsupportedFeature("PosixPlatformThreads.setNativeName on unknown OS");
            }
        }
    }

    @Override
    protected void yieldCurrent() {
        Sched.sched_yield();
    }

    private static final CEntryPointLiteral<CFunctionPointer> pthreadStartRoutine = CEntryPointLiteral.create(PosixPlatformThreads.class, "pthreadStartRoutine", ThreadStartData.class);

    private static class PthreadStartRoutinePrologue implements CEntryPointOptions.Prologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString("Failed to attach a newly launched thread.");

        @SuppressWarnings("unused")
        @Uninterruptible(reason = "prologue")
        static void enter(ThreadStartData data) {
            int code = CEntryPointActions.enterAttachThread(data.getIsolate(), true, false);
            if (code != CEntryPointErrors.NO_ERROR) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = PthreadStartRoutinePrologue.class, epilogue = LeaveDetachThreadEpilogue.class)
    static WordBase pthreadStartRoutine(ThreadStartData data) {
        ObjectHandle threadHandle = data.getThreadHandle();
        freeStartData(data);

        threadStartRoutine(threadHandle);

        return WordFactory.nullPointer();
    }

    @Override
    protected void beforeThreadRun(Thread thread) {
        /* Complete the initialization of the thread, now that it is (nearly) running. */
        setPthreadIdentifier(thread, Pthread.pthread_self());
        setNativeName(thread, thread.getName());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public OSThreadHandle startThreadUnmanaged(CFunctionPointer threadRoutine, PointerBase userData, int stackSize) {
        pthread_attr_t attributes = StackValue.get(pthread_attr_t.class);
        int status = Pthread.pthread_attr_init_no_transition(attributes);
        if (status != 0) {
            return WordFactory.nullPointer();
        }
        try {
            status = Pthread.pthread_attr_setdetachstate_no_transition(attributes, Pthread.PTHREAD_CREATE_JOINABLE());
            if (status != 0) {
                return WordFactory.nullPointer();
            }

            UnsignedWord threadStackSize = WordFactory.unsigned(stackSize);
            /* If there is a chosen stack size, use it as the stack size. */
            if (threadStackSize.notEqual(WordFactory.zero())) {
                /* Make sure the chosen stack size is large enough. */
                threadStackSize = UnsignedUtils.max(threadStackSize, Pthread.PTHREAD_STACK_MIN());
                /* Make sure the chosen stack size is a multiple of the system page size. */
                threadStackSize = UnsignedUtils.roundUp(threadStackSize, WordFactory.unsigned(Unistd.NoTransitions.getpagesize()));

                status = Pthread.pthread_attr_setstacksize_no_transition(attributes, threadStackSize);
                if (status != 0) {
                    return WordFactory.nullPointer();
                }
            }

            Pthread.pthread_tPointer newThread = StackValue.get(Pthread.pthread_tPointer.class);

            status = Pthread.pthread_create_no_transition(newThread, attributes, threadRoutine, userData);
            if (status != 0) {
                return WordFactory.nullPointer();
            }

            return (OSThreadHandle) newThread.read();
        } finally {
            Pthread.pthread_attr_destroy_no_transition(attributes);
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean joinThreadUnmanaged(OSThreadHandle threadHandle, WordPointer threadExitStatus) {
        int status = Pthread.pthread_join_no_transition((Pthread.pthread_t) threadHandle, threadExitStatus);
        if (status != 0) {
            return false;
        }
        return true;
    }

    @Override
    @SuppressWarnings("unused")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void closeOSThreadHandle(OSThreadHandle threadHandle) {
        // pthread_t doesn't need closing
    }
}

@TargetClass(Thread.class)
final class Target_java_lang_Thread {
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    boolean hasPthreadIdentifier;

    /**
     * Every thread started by {@link PosixPlatformThreads#doStartThread} has an opaque pthread_t.
     */
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Pthread.pthread_t pthreadIdentifier;
}

/**
 * {@link PosixParkEvent} is based on HotSpot class {@code Parker} in {@code os_posix.cpp}, as of
 * JDK 19 (git commit hash: f640fc5a1eb876a657d0de011dcd9b9a42b88eec, JDK tag: jdk-19+30).
 * <p>
 * HotSpot has two constructs with a similar purpose: {@code ParkEvent} and {@code Parker}. The
 * latter implements JSR 166 synchronization primitives {@link Unsafe#park} and
 * {@link Unsafe#unpark}, just like we do here, therefore we base this implementation on
 * {@code Parker}. Our implementation of Java object monitors, {@link JavaMonitor}, uses the JSR 166
 * primitives, so it can potentially experience interference from unrelated calls to
 * {@link Unsafe#unpark}. This is a difference to HotSpot's {@code ObjectMonitor}, which uses a
 * separate HotSpot {@code ParkEvent} instance. Another difference is that {@code Parker} and the
 * code below return control to the caller on spurious wakeups, unlike HotSpot's {@code ParkEvent}.
 * This does not affect correctness.
 */
final class PosixParkEvent extends ParkEvent {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long EVENT_OFFSET = U.objectFieldOffset(PosixParkEvent.class, "event");

    private Pthread.pthread_mutex_t mutex;
    private Pthread.pthread_cond_t cond;

    /** Permit: 1 if an unpark is pending, otherwise 0. */
    private volatile int event = 0;

    /** Whether the owner is currently parked. Guarded by {@link #mutex}. */
    private boolean parked = false;

    PosixParkEvent() {
        // Allocate mutex and condition in a single step so that they are adjacent in memory.
        UnsignedWord mutexSize = SizeOf.unsigned(Pthread.pthread_mutex_t.class);
        Pointer memory = UnmanagedMemory.malloc(mutexSize.add(SizeOf.unsigned(Pthread.pthread_cond_t.class)));
        mutex = (Pthread.pthread_mutex_t) memory;
        cond = (Pthread.pthread_cond_t) memory.add(mutexSize);

        final Pthread.pthread_mutexattr_t mutexAttr = WordFactory.nullPointer();
        PosixUtils.checkStatusIs0(Pthread.pthread_mutex_init(mutex, mutexAttr), "mutex initialization");
        PosixUtils.checkStatusIs0(PthreadConditionUtils.initCondition(cond), "condition variable initialization");
        // Note: HotSpot has another pthread_cond_t without CLOCK_MONOTONIC for absolute timed waits
    }

    @Override
    protected void reset() {
        event = 0;
    }

    @Override
    protected void condWait() {
        park(false, 0);
    }

    @Override
    protected void condTimedWait(long durationNanos) {
        if (durationNanos > 0) {
            park(false, durationNanos);
        }
    }

    @Override
    protected boolean tryFastPark() {
        // We depend on getAndSet having full barrier semantics since we are not locking
        return U.getAndSetInt(this, EVENT_OFFSET, 0) != 0;
    }

    @Override
    protected void park(boolean isAbsolute, long time) {
        if (time < 0 || (isAbsolute && time == 0)) {
            return; // don't wait at all
        }
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            int status = Pthread.pthread_mutex_trylock_no_transition(mutex);
            if (status == Errno.EBUSY()) {
                return; // can only mean another thread is unparking us: don't wait
            }
            PosixUtils.checkStatusIs0(status, "park: mutex trylock");
            try {
                if (event == 0) {
                    assert !parked;
                    parked = true;
                    if (!isAbsolute && time == 0) {
                        status = Pthread.pthread_cond_wait(cond, mutex);
                        PosixUtils.checkStatusIs0(status, "park(): condition variable wait");
                    } else {
                        long durationNanos = TimeUtils.durationNanos(isAbsolute, time);
                        Time.timespec deadlineTimespec = UnsafeStackValue.get(Time.timespec.class);
                        PthreadConditionUtils.durationNanosToDeadlineTimespec(durationNanos, deadlineTimespec);

                        status = Pthread.pthread_cond_timedwait(cond, mutex, deadlineTimespec);
                        if (status != 0 && status != Errno.ETIMEDOUT()) {
                            Log.log().newline()
                                            .string("[PosixParkEvent.park(durationNanos: ").signed(durationNanos).string("): Should not reach here.")
                                            .string("  mutex: ").hex(mutex)
                                            .string("  cond: ").hex(cond)
                                            .string("  deadlineTimeSpec.tv_sec: ").signed(deadlineTimespec.tv_sec())
                                            .string("  deadlineTimespec.tv_nsec: ").signed(deadlineTimespec.tv_nsec())
                                            .string("  status: ").signed(status).string(" ").string(Errno.strerror(status))
                                            .string("]").newline();
                            PosixUtils.checkStatusIs0(status, "park(boolean, long): condition variable timed wait");
                        }
                    }
                    parked = false;
                }
                event = 0;

            } finally {
                PosixUtils.checkStatusIs0(Pthread.pthread_mutex_unlock(mutex), "park: mutex unlock");

                // Paranoia to ensure our locked and lock-free paths interact
                // correctly with each other and Java-level accesses.
                U.fullFence();
            }
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Override
    protected void unpark() {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            int s;
            boolean p;
            PosixUtils.checkStatusIs0(Pthread.pthread_mutex_lock(mutex), "PosixParkEvent.unpark(): mutex lock");
            try {
                s = event;
                event = 1;
                p = parked;
            } finally {
                PosixUtils.checkStatusIs0(Pthread.pthread_mutex_unlock(mutex), "PosixParkEvent.unpark(): mutex unlock");
            }
            if (s == 0 && p) {
                /*
                 * Signal without holding the mutex, which is safe and avoids futile wakeups if the
                 * platform does not implement wait morphing.
                 */
                PosixUtils.checkStatusIs0(Pthread.pthread_cond_signal(cond), "PosixParkEvent.unpark(): condition variable signal");
            }
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void release() {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(mutex);
        mutex = WordFactory.nullPointer();
        cond = WordFactory.nullPointer(); // allocated and freed together with mutex
    }
}

@AutomaticallyRegisteredImageSingleton(ParkEventFactory.class)
class PosixParkEventFactory implements ParkEventFactory {
    @Override
    public ParkEvent acquire() {
        return new PosixParkEvent();
    }
}
