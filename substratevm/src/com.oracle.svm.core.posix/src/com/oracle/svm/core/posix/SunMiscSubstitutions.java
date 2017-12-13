/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.core.posix;

import java.io.Console;
import java.nio.charset.Charset;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Semaphore;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Signal.SignalDispatcher;
import com.oracle.svm.core.posix.headers.Stat;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

@TargetClass(sun.misc.SharedSecrets.class)
final class Target_sun_misc_SharedSecrets {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static sun.misc.JavaIOAccess javaIOAccess;

    @Substitute
    public static sun.misc.JavaIOAccess getJavaIOAccess() {
        if (javaIOAccess == null) {
            javaIOAccess = new sun.misc.JavaIOAccess() {
                @Override
                public Console console() {
                    if (Target_java_io_Console.istty()) {
                        if (Target_java_lang_System.cons == null) {
                            Target_java_lang_System.cons = KnownIntrinsics.unsafeCast(new Target_java_io_Console(), Console.class);
                        }
                        return Target_java_lang_System.cons;
                    }
                    return null;
                }

                @Override
                public Charset charset() {
                    // This method is called in sun.security.util.Password,
                    // cons already exists when this method is called
                    return KnownIntrinsics.unsafeCast(Target_java_lang_System.cons, Target_java_io_Console.class).cs;
                }
            };
        }
        return javaIOAccess;
    }
}

@TargetClass(sun.misc.Signal.class)
final class Target_sun_misc_Signal {

    @Substitute
    private static int findSignal(String signalName) {
        return Util_sun_misc_Signal.numberFromName(signalName);
    }

    @Substitute
    private static long handle0(int sig, long nativeH) {
        return Util_sun_misc_Signal.handle0(sig, nativeH);
    }

    /** The Java side of raising a signal calls the C side of raising a signal. */
    @Substitute
    private static void raise0(int signalNumber) {
        Signal.raise(signalNumber);
    }

    /**
     * Called by the VM to execute Java signal handlers. Except that in sun.misc.Signal, this method
     * is private.
     */
    @Alias
    static native void dispatch(int number);
}

/** Support for Target_sun_misc_Signal. */
final class Util_sun_misc_Signal {

    /** A thread to dispatch signals as they are raised. */
    private static Thread dispatchThread = null;

    /** A lock to synchronize runtime initialization. */
    private static final ReentrantLock initializationLock = new ReentrantLock();
    /** An initialization flag. */
    private static volatile boolean initialized = false;

    /**
     * A table of signals, handlers and whether a signal has been raised. Allocated during image
     * building, but re-initialized at runtime.
     */
    private static SignalState[] signalState = allocateSignalState();

    private Util_sun_misc_Signal() {
        /* All-static class. */
    }

    /** Constants for the longs from sun.misc.Signal. */
    private static final long sunMiscSignalDefaultHandler = 0;
    private static final long sunMiscSignalIgnoreHandler = 1;
    private static final long sunMiscSignalDispatchHandler = 2;
    private static final long sunMiscSignalErrorHandler = -1;

    /**
     * Register a Java signal handler with the the C signal handling mechanism.
     *
     * This implementation does not complain (by returning -1) about registering signal handlers for
     * signals that the VM itself uses.
     */
    protected static long handle0(int sig, long nativeH) {
        ensureInitialized();
        final Signal.SignalDispatcher newDispatcher = nativeHToDispatcher(nativeH);
        updateDispatcher(sig, newDispatcher);
        final Signal.SignalDispatcher oldDispatcher = Signal.signal(sig, newDispatcher);
        final long result = dispatcherToNativeH(oldDispatcher);
        return result;
    }

    /**
     * Allocation of the signal state has to be done during image building so it does not move
     * during collections.
     */
    private static SignalState[] allocateSignalState() {
        final Signal.SignalEnum[] valueArray = Signal.SignalEnum.values();
        final SignalState[] result = new SignalState[valueArray.length];
        for (int index = 0; index < valueArray.length; index += 1) {
            result[index] = new SignalState();
        }
        return result;
    }

    /**
     * Runtime initialization. This method is called every time someone registers a Java signal
     * handler.
     */
    private static void ensureInitialized() {
        /* Ask if initialization is needed. */
        if (!initialized) {
            /*
             * Lock so initialization only happens once, and so initialization finishes before any
             * uses, e.g., from other threads.
             */
            initializationLock.lock();
            try {
                /* Ask if initialization is *still* needed now that I have the lock. */
                if (!initialized) {
                    /* Initialize the semaphore for notifications. */
                    SignalSemaphore.initialize();

                    /* Initialize the table of signal states. */
                    final Signal.SignalEnum[] valueArray = Signal.SignalEnum.values();
                    for (int index = 0; index < valueArray.length; index += 1) {
                        final Signal.SignalEnum value = valueArray[index];
                        final int cValue = value.getCValue();
                        signalState[index].initialize(cValue);
                    }

                    /* Create and start a daemon thread to dispatch to Java signal handlers. */
                    dispatchThread = new Thread(new DispatchThread());
                    dispatchThread.setDaemon(true);
                    dispatchThread.start();

                    /* Initialization is complete. */
                    initialized = true;
                }
            } finally {
                initializationLock.unlock();
            }
        }
    }

    /** Map from a Java signal name to a signal number. */
    protected static int numberFromName(String signalName) {
        /* Java deals in signal names without the leading "SIG" prefix, but C uses it. */
        final String sigSignalName = "SIG" + signalName;
        for (Signal.SignalEnum s : Signal.SignalEnum.values()) {
            if (s.name().equals(sigSignalName)) {
                return s.getCValue();
            }
        }
        /* {@link sun.misc.Signal#findSignal(String)} expects a -1 on failure. */
        return -1;
    }

    /** Update the dispatcher of an entry in the signal state table. */
    private static void updateDispatcher(int sig, Signal.SignalDispatcher dispatcher) {
        final Signal.SignalEnum[] valueArray = Signal.SignalEnum.values();
        for (int index = 0; index < valueArray.length; index += 1) {
            final SignalState entry = signalState[index];
            if (entry.getNumber() == sig) {
                entry.setDispatcher(dispatcher);
            }
        }
    }

    /** Map from the handler numbers Java uses to the function pointers that C uses. */
    private static Signal.SignalDispatcher nativeHToDispatcher(long nativeH) {
        final Signal.SignalDispatcher result;
        if (nativeH == sunMiscSignalDefaultHandler) {
            result = Signal.SIG_DFL();
        } else if (nativeH == sunMiscSignalIgnoreHandler) {
            result = Signal.SIG_IGN();
        } else if (nativeH == sunMiscSignalDispatchHandler) {
            result = signalDispatcher.getFunctionPointer();
        } else if (nativeH == sunMiscSignalErrorHandler) {
            result = Signal.SIG_ERR();
        } else {
            result = WordFactory.pointer(nativeH);
        }
        return result;
    }

    /** Map from the function pointers that C uses to the numbers that Java uses. */
    private static long dispatcherToNativeH(Signal.SignalDispatcher handler) {
        final long result;
        if (handler == Signal.SIG_DFL()) {
            result = sunMiscSignalDefaultHandler;
        } else if (handler == Signal.SIG_IGN()) {
            result = sunMiscSignalIgnoreHandler;
        } else if (handler == signalDispatcher.getFunctionPointer()) {
            result = sunMiscSignalDispatchHandler;
        } else if (handler == Signal.SIG_ERR()) {
            result = sunMiscSignalErrorHandler;
        } else {
            result = handler.rawValue();
        }
        return result;
    }

    /**
     * This method is registered as a C signal handler for signals handled by Java code.
     * <p>
     * This method gets called on top of an existing thread when the corresponding signal is raised.
     * An alternative would be to set up an alternate signal handler stack on which to run this
     * signal handler. That seems not to be necessary. The given signal is normally blocked from
     * being re-raised while this signal handler is running, but different signals arriving while
     * this handler is running may run on top of the original handler.
     * <p>
     * This method only records that the signal was raised: it does not make the up-call to the Java
     * signal handler. The address of this method, {@link #signalDispatcher}, is used in
     * {@link DispatchThread#run()} to indicate that the up-call should be made the the Java signal
     * handler.
     * <p>
     * This method is uninterruptible because it is running on a borrowed thread, does not have a
     * VMThread pointer or any VMThreadLocal state.
     * <p>
     * Since this code may run while a garbage collection is in progress, it may not access anything
     * that might be moved by a garbage collection.
     */
    @CEntryPoint
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    @Uninterruptible(reason = "Can not check for safepoints because I am running on a borrowed thread.")
    private static void dispatch(int signalNumber) {
        /* Good practice: Save errno around the signal handler. */
        final int savedErrno = Errno.errno();
        try {
            boolean needBroadcast = false;
            for (int index = 0; index < signalState.length; index += 1) {
                final SignalState entry = signalState[index];
                if (entry.getNumber() == signalNumber) {
                    entry.incrementRaised();
                    needBroadcast = true;
                }
            }
            if (needBroadcast) {
                SignalSemaphore.broadcast();
            }
        } finally {
            /* Good practice: Restore errno around the signal handler. */
            Errno.set_errno(savedErrno);
        }
    }

    /**
     * The address of the signal handler for signals handled by Java code. {@link #dispatch(int)}.
     */
    private static final CEntryPointLiteral<SignalDispatcher> signalDispatcher = CEntryPointLiteral.create(Util_sun_misc_Signal.class, "dispatch", int.class);

    /** A wrapper around a Posix semaphore, for notification that a signal has been raised. */
    protected static final class SignalSemaphore {

        /**
         * A Posix semaphore for notifications.
         *
         * Allocated by {@link Semaphore#sem_open} in native memory.
         */
        private static Semaphore.sem_t semaphore;

        /* No instances. */
        private SignalSemaphore() {
        }

        static void initialize() {
            /*
             * On Darwin, a semaphore name seems to be limited to 30 characters, including the
             * leading '/'. 'UsmS.SS' stands for 'Util_sun_misc_Signal.SignalSemaphore' which is
             * otherwise too long. I want the process identifier in there, too, to make the
             * semaphore name process-specific, since the semaphore is for signals delivered to this
             * process.
             */
            final String semaphoreName = "/UsmS.SS-" + Integer.toString(PosixUtils.getpid());
            /*
             * I would also use Fcntl.O_EXCL() to make sure the semaphore did not exist before,
             * except that semaphores persist across calls to 'exec', so the semaphore for this
             * process might already exist.
             */
            final int oflag = (Fcntl.O_CREAT());
            final int mode = (Stat.S_IRUSR() | Stat.S_IWUSR());
            try (CCharPointerHolder nameHolder = CTypeConversion.toCString(semaphoreName)) {
                semaphore = Semaphore.sem_open(nameHolder.get(), oflag, mode, 0);
                if (semaphore == Semaphore.SEM_FAILED()) {
                    final int semOpenErrno = Errno.errno();
                    Log.log().string("Util_sun_misc_Signal.SignalSemaphore.initialize: sem_open failed.")
                                    .string("  errno: ").signed(semOpenErrno).string("  ").string(Errno.strerror(semOpenErrno)).newline();
                    VMError.guarantee(false, "Util_sun_misc_Signal.SignalSemaphore.initialize: sem_open failed.");
                }
                /* Unlink the semaphore so it does not persist. */
                final int unlinkResult = Semaphore.sem_unlink(nameHolder.get());
                if (unlinkResult != 0) {
                    final int semUnlinkErrno = Errno.errno();
                    Log.log().string("Util_sun_misc_Signal.SignalSemaphore.initialize: sem_unlink failed.")
                                    .string("  errno: ").signed(semUnlinkErrno).string("  ").string(Errno.strerror(semUnlinkErrno)).newline();
                    VMError.guarantee(false, "Util_sun_misc_Signal.SignalSemaphore.initialize: sem_unlink failed.");
                }
            }
        }

        /** Clean up resources. */
        static void destructor() {
            if (isActive()) {
                /* Do not bother to check the return status: What would I do if it failed? */
                Semaphore.sem_close(semaphore);
                semaphore = WordFactory.nullPointer();
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        static boolean isActive() {
            return (semaphore.isNonNull() && (semaphore != Semaphore.SEM_FAILED()));
        }

        static void await() {
            if (isActive()) {
                final int status = Semaphore.sem_wait(semaphore);
                /* Interrupt (by a signal handler) is treated like a notification. */
                if (status == Errno.EINTR()) {
                    return;
                }
                PosixUtils.checkStatusIs0(status, "Util_sun_misc_Signal.SignalSemaphore.awaitInNative(): sem_wait failed.");
            }
        }

        /**
         * Post that a signal is available. This is called from
         * {@link Util_sun_misc_Signal#dispatch(int)}, on a borrowed thread, with no VMThread data.
         */
        @Uninterruptible(reason = "Can not check for safepoints because I am running on a borrowed thread.")
        static void broadcast() {
            if (isActive()) {
                final int postResult = Semaphore.sem_post_no_transition(semaphore);
                if (postResult != 0) {
                    /* Not much I can do if the sem_post failed. */
                    LibC.exit(postResult);
                }
            }
        }
    }

    /** A runnable to notice when signals have been raised. */
    protected static final class DispatchThread implements Runnable {

        protected DispatchThread() {
            /* Nothing to do. */
        }

        /**
         * Wait to be notified that a signal has been raised, then find any that were raised and
         * dispatch to the Java signal handler. This method may race with
         * {@link Util_sun_misc_Signal#dispatch(int)} which is why an AtomicInteger is used.
         */
        @Override
        public void run() {
            for (; /* break */;) {
                /* Check if this thread was interrupted before blocking. */
                if (Thread.interrupted()) {
                    break;
                }
                /* Block waiting for one or more signals to be raised. Or a random wake up. */
                SignalSemaphore.await();
                for (int index = 0; index < signalState.length; index += 1) {
                    final SignalState entry = signalState[index];
                    /* If there are signals to be raised ... */
                    if (entry.getRaised() > 0) {
                        final Signal.SignalDispatcher dispatcher = entry.getDispatcher();
                        /* ... and if the dispatcher is the Java signal dispatcher. */
                        if (dispatcher.equal(Util_sun_misc_Signal.signalDispatcher.getFunctionPointer())) {
                            /* then up-call to the Java signal handler. */
                            Target_sun_misc_Signal.dispatch(entry.getNumber());
                        }
                        entry.decrementRaised();
                    }
                }
            }
            /* If this thread is exiting, then the semaphore is no longer needed. */
            SignalSemaphore.destructor();
        }
    }

    /**
     * An entry in a table of signal numbers, handlers, and especially, whether a signal has been
     * raised.
     */
    private static final class SignalState {

        /** The C signal number this state represents. */
        private int number;
        /** The C signal handler for this signal number. */
        private Signal.SignalDispatcher dispatcher;
        /** A count of outstanding raises of this signal. */
        private UninterruptibleUtils.AtomicInteger raised;

        /** This just allocates an entry. The entry is initialized at runtime. */
        protected SignalState() {
            this.raised = new UninterruptibleUtils.AtomicInteger(0);
        }

        protected void initialize(int cValue) {
            number = cValue;
            dispatcher = Signal.SIG_DFL();
        }

        /* Access methods. */

        @Uninterruptible(reason = "Called from uninterruptible code.")
        protected int getNumber() {
            return number;
        }

        protected Signal.SignalDispatcher getDispatcher() {
            return dispatcher;
        }

        protected void setDispatcher(Signal.SignalDispatcher value) {
            dispatcher = value;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        protected int getRaised() {
            return raised.get();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        protected void incrementRaised() {
            raised.incrementAndGet();
        }

        protected void decrementRaised() {
            raised.decrementAndGet();
        }
    }
}

/** Translated from: jdk/src/share/native/sun/misc/NativeSignalHandler.c?v=Java_1.8.0_40_b10. */
@TargetClass(className = "sun.misc.NativeSignalHandler")
final class Target_sun_misc_NativeSignalHandler {

    /**
     * This method gets called from the runnable created in the dispatch(int) method of
     * {@link sun.misc.Signal}. It is running in a Java thread, but the handler is a C function. So
     * I transition to native before making the call, and transition back to Java after the call.
     *
     * This looks really dangerous: Taking a long parameter and calling through it. If the only way
     * to get a NativeSignalHandler is from previously-registered native signal handler (see
     * {@link sun.misc.Signal#handle(sun.misc.Signal, sun.misc.SignalHandler)} then maybe this is
     * not quite as dangerous as it first seems.
     */
    // 033 typedef void (*sig_handler_t)(jint, void *, void *);
    // 034
    // 035 JNIEXPORT void JNICALL
    // 036 Java_sun_misc_NativeSignalHandler_handle0(JNIEnv *env, jclass cls, jint sig, jlong f) {
    @Substitute
    static void handle0(int sig, long f) {
        // 038 /* We've lost the siginfo and context */
        // 039 (*(sig_handler_t)jlong_to_ptr(f))(sig, NULL, NULL);
        final Signal.AdvancedSignalDispatcher handler = WordFactory.pointer(f);
        Util_sun_misc_NativeSignalHandler.handle0WithTransition(handler, sig);
    }
}

final class Util_sun_misc_NativeSignalHandler {

    /** Transition to native for the call of the handler. */
    static void handle0WithTransition(Signal.AdvancedSignalDispatcher functionPointer, int sig) {
        CFunctionPrologueNode.cFunctionPrologue();
        handle0InNative(functionPointer, sig);
        CFunctionEpilogueNode.cFunctionEpilogue();
    }

    /**
     * This method is called after a transition to native. It can not access anything on the Java
     * heap.
     */
    @Uninterruptible(reason = "Must not stop while in native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static void handle0InNative(Signal.AdvancedSignalDispatcher functionPointer, int sig) {
        functionPointer.dispatch(sig, WordFactory.nullPointer(), WordFactory.nullPointer());
    }
}

/** Dummy class to have a class with the file's name. */
public final class SunMiscSubstitutions {
}
