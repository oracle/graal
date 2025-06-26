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

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Pthread.pthread_attr_t;
import com.oracle.svm.core.posix.headers.Pthread.pthread_cond_t;
import com.oracle.svm.core.posix.headers.Pthread.pthread_mutex_t;
import com.oracle.svm.core.posix.headers.Sched;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.darwin.DarwinPthread;
import com.oracle.svm.core.posix.headers.linux.LinuxPthread;
import com.oracle.svm.core.posix.pthread.PthreadConditionUtils;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.Parker;
import com.oracle.svm.core.thread.Parker.ParkerFactory;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMThreads.OSThreadHandle;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
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

            UnsignedWord threadStackSize = Word.unsigned(stackSize);
            /* If there is a chosen stack size, use it as the stack size. */
            if (threadStackSize.notEqual(Word.zero())) {
                /* Make sure the chosen stack size is large enough. */
                threadStackSize = UnsignedUtils.max(threadStackSize, Pthread.PTHREAD_STACK_MIN());
                /* Make sure the chosen stack size is a multiple of the system page size. */
                threadStackSize = UnsignedUtils.roundUp(threadStackSize, Word.unsigned(Unistd.getpagesize()));

                if (Pthread.pthread_attr_setstacksize(attributes, threadStackSize) != 0) {
                    return false;
                }
            }

            /*
             * Prevent stack overflow errors so that starting the thread and reverting back to a
             * safe state (in case of an error) works reliably.
             */
            StackOverflowCheck.singleton().makeYellowZoneAvailable();
            try {
                return doStartThread0(thread, attributes);
            } finally {
                StackOverflowCheck.singleton().protectYellowZone();
            }
        } finally {
            Pthread.pthread_attr_destroy(attributes);
        }
    }

    /** Starts a thread to the point so that it is executing. */
    @NeverInline("Workaround for GR-51925 - prevent that reads float from this method into the caller.")
    private boolean doStartThread0(Thread thread, pthread_attr_t attributes) {
        ThreadStartData startData = prepareStart(thread, SizeOf.get(ThreadStartData.class));
        try {
            Pthread.pthread_tPointer newThread = UnsafeStackValue.get(Pthread.pthread_tPointer.class);
            if (Pthread.pthread_create(newThread, attributes, threadStartRoutine.getFunctionPointer(), startData) != 0) {
                undoPrepareStartOnError(thread, startData);
                return false;
            }
            return true;
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere("No exception must be thrown after creating the thread start data.", e);
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
             * The thread was a. not started yet, b. not started from Java code (i.e., only attached
             * to SVM). We do not want to interfere with such threads.
             */
            return;
        }

        if (Platform.includedIn(Platform.DARWIN.class) && thread != Thread.currentThread()) {
            /* Darwin only allows setting the name of the current thread. */
            return;
        }

        /* Use at most 15 characters from the right end of the name. */
        final int startIndex = Math.max(0, name.length() - 15);
        final String pthreadName = name.substring(startIndex);
        assert pthreadName.length() < 16 : "thread name for pthread has a maximum length of 16 characters including the terminating 0";
        try (CCharPointerHolder threadNameHolder = CTypeConversion.toCString(pthreadName)) {
            if (Platform.includedIn(Platform.LINUX.class)) {
                LinuxPthread.pthread_setname_np(getPthreadIdentifier(thread), threadNameHolder.get());
            } else if (Platform.includedIn(Platform.DARWIN.class)) {
                assert thread == Thread.currentThread() : "Darwin only allows setting the name of the current thread";
                DarwinPthread.pthread_setname_np(threadNameHolder.get());
            } else {
                VMError.unsupportedFeature("PosixPlatformThreads.setNativeName() on unexpected OS: " + ImageSingletons.lookup(Platform.class).getOS());
            }
        }
    }

    @Override
    protected void yieldCurrent() {
        Sched.sched_yield();
    }

    /**
     * Note that this method is only executed for Java threads that are started via
     * {@link Thread#start()}. It is not executed if an existing native thread is attached to an
     * isolate.
     */
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
            return Word.nullPointer();
        }
        try {
            status = Pthread.pthread_attr_setdetachstate_no_transition(attributes, Pthread.PTHREAD_CREATE_JOINABLE());
            if (status != 0) {
                return Word.nullPointer();
            }

            UnsignedWord threadStackSize = Word.unsigned(stackSize);
            /* If there is a chosen stack size, use it as the stack size. */
            if (threadStackSize.notEqual(Word.zero())) {
                /* Make sure the chosen stack size is large enough. */
                threadStackSize = UnsignedUtils.max(threadStackSize, Pthread.PTHREAD_STACK_MIN());
                /* Make sure the chosen stack size is a multiple of the system page size. */
                threadStackSize = UnsignedUtils.roundUp(threadStackSize, Word.unsigned(Unistd.NoTransitions.getpagesize()));

                status = Pthread.pthread_attr_setstacksize_no_transition(attributes, threadStackSize);
                if (status != 0) {
                    return Word.nullPointer();
                }
            }

            Pthread.pthread_tPointer newThread = StackValue.get(Pthread.pthread_tPointer.class);

            status = Pthread.pthread_create_no_transition(newThread, attributes, threadRoutine, userData);
            if (status != 0) {
                return Word.nullPointer();
            }

            return newThread.read();
        } finally {
            Pthread.pthread_attr_destroy_no_transition(attributes);
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean joinThreadUnmanaged(OSThreadHandle threadHandle, WordPointer threadExitStatus) {
        int status = Pthread.pthread_join_no_transition((Pthread.pthread_t) threadHandle, threadExitStatus);
        return status == 0;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ThreadLocalKey createUnmanagedThreadLocal() {
        Pthread.pthread_key_tPointer key = StackValue.get(Pthread.pthread_key_tPointer.class);
        PosixUtils.checkStatusIs0(Pthread.pthread_key_create(key, Word.nullPointer()), "pthread_key_create(key, keyDestructor): failed.");
        return (ThreadLocalKey) key.read();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void deleteUnmanagedThreadLocal(ThreadLocalKey key) {
        int resultCode = Pthread.pthread_key_delete((Pthread.pthread_key_t) key);
        PosixUtils.checkStatusIs0(resultCode, "pthread_key_delete(key): failed.");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @SuppressWarnings("unchecked")
    public <T extends WordBase> T getUnmanagedThreadLocalValue(ThreadLocalKey key) {
        /*
         * Although this method is not async-signal-safe in general we rely on
         * implementation-specific behavior here.
         */
        return (T) Pthread.pthread_getspecific((Pthread.pthread_key_t) key);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setUnmanagedThreadLocalValue(ThreadLocalKey key, WordBase value) {
        int resultCode = Pthread.pthread_setspecific((Pthread.pthread_key_t) key, (VoidPointer) value);
        PosixUtils.checkStatusIs0(resultCode, "pthread_setspecific(key, value): wrong arguments.");
    }
}

@TargetClass(Thread.class)
final class Target_java_lang_Thread {
    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    boolean hasPthreadIdentifier;

    /**
     * Every thread started by {@link PosixPlatformThreads#doStartThread} has an opaque pthread_t.
     */
    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Pthread.pthread_t pthreadIdentifier;
}

final class PosixParker extends Parker {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long EVENT_OFFSET = U.objectFieldOffset(PosixParker.class, "event");

    private pthread_mutex_t mutex;
    private pthread_cond_t relativeCond;
    private pthread_cond_t absoluteCond;

    /** The condition on which the owning thread is currently blocked. Guarded by {@link #mutex}. */
    private pthread_cond_t currentCond;

    /** Permit: 1 if an unpark is pending, otherwise 0. */
    private volatile int event = 0;

    PosixParker() {
        // Allocate mutex and condition in a single step so that they are adjacent in memory.
        UnsignedWord mutexSize = SizeOf.unsigned(pthread_mutex_t.class);
        UnsignedWord condSize = SizeOf.unsigned(pthread_cond_t.class);
        Pointer memory = NativeMemory.malloc(mutexSize.add(condSize.multiply(2)), NmtCategory.Threading);
        mutex = (pthread_mutex_t) memory;
        relativeCond = (pthread_cond_t) memory.add(mutexSize);
        absoluteCond = (pthread_cond_t) memory.add(mutexSize).add(condSize);

        final Pthread.pthread_mutexattr_t mutexAttr = Word.nullPointer();
        PosixUtils.checkStatusIs0(Pthread.pthread_mutex_init(mutex, mutexAttr), "mutex initialization");
        PosixUtils.checkStatusIs0(PthreadConditionUtils.initConditionWithRelativeTime(relativeCond), "relative-time condition variable initialization");
        PosixUtils.checkStatusIs0(PthreadConditionUtils.initConditionWithAbsoluteTime(absoluteCond), "absolute-time condition variable initialization");
    }

    @Override
    protected void reset() {
        event = 0;
    }

    @Override
    protected boolean tryFastPark() {
        // We depend on getAndSet having full barrier semantics since we are not locking
        return U.getAndSetInt(this, EVENT_OFFSET, 0) != 0;
    }

    @Override
    protected void park(boolean isAbsolute, long time) {
        assert time >= 0 && !(isAbsolute && time == 0) : "must not be called otherwise";
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            park0(isAbsolute, time);
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/os/posix/os_posix.cpp#L1830-L1906")
    private void park0(boolean isAbsolute, long time) {
        int status = Pthread.pthread_mutex_trylock_no_transition(mutex);
        if (status == Errno.EBUSY()) {
            /* Another thread is unparking us, so don't wait. This may cause spurious wakeups. */
            return;
        }
        PosixUtils.checkStatusIs0(status, "park: mutex_trylock");

        try {
            if (event == 0) {
                assert currentCond.isNull();
                try {
                    if (time == 0) {
                        currentCond = relativeCond;
                        status = Pthread.pthread_cond_wait(currentCond, mutex);
                    } else {
                        currentCond = isAbsolute ? absoluteCond : relativeCond;
                        Time.timespec deadline = UnsafeStackValue.get(Time.timespec.class);
                        PthreadConditionUtils.fillTimespec(deadline, time, isAbsolute);
                        status = Pthread.pthread_cond_timedwait(currentCond, mutex, deadline);
                    }
                    assert status == 0 || status == Errno.ETIMEDOUT();
                } finally {
                    currentCond = Word.nullPointer();
                }
            }
            event = 0;
        } finally {
            PosixUtils.checkStatusIs0(Pthread.pthread_mutex_unlock(mutex), "park: mutex_unlock");
            /*
             * Paranoia to ensure our locked and lock-free paths interact correctly with each other
             * and Java-level accesses.
             */
            U.fullFence();
        }
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/os/posix/os_posix.cpp#L1908-L1931")
    protected void unpark() {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            int s;
            pthread_cond_t p;
            int status = Pthread.pthread_mutex_trylock_no_transition(mutex);
            if (status == Errno.EBUSY()) { // more expensive transition when potentially blocking:
                status = Pthread.pthread_mutex_lock(mutex);
            }
            PosixUtils.checkStatusIs0(status, "PosixParker.unpark(): mutex lock");
            try {
                s = event;
                event = 1;
                p = currentCond;
            } finally {
                PosixUtils.checkStatusIs0(Pthread.pthread_mutex_unlock(mutex), "PosixParker.unpark(): mutex unlock");
            }
            if (s == 0 && p.isNonNull()) {
                /*
                 * Signal without holding the mutex, which is safe and avoids futile wakeups if the
                 * platform does not implement wait morphing.
                 */
                PosixUtils.checkStatusIs0(Pthread.pthread_cond_signal(p), "PosixParker.unpark(): condition variable signal");
            }
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void release() {
        /* The conditions and the mutex are allocated with a single malloc. */
        int status = Pthread.pthread_cond_destroy(relativeCond);
        assert status == 0;
        relativeCond = Word.nullPointer();

        status = Pthread.pthread_cond_destroy(absoluteCond);
        assert status == 0;
        absoluteCond = Word.nullPointer();

        status = Pthread.pthread_mutex_destroy(mutex);
        assert status == 0;
        NativeMemory.free(mutex);
        mutex = Word.nullPointer();
    }
}

@AutomaticallyRegisteredImageSingleton(ParkerFactory.class)
class PosixParkerFactory implements Parker.ParkerFactory {
    @Override
    public Parker acquire() {
        return new PosixParker();
    }
}
