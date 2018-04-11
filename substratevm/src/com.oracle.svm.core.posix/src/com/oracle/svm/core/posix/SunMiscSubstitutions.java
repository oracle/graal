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

import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.os.IsDefined;
import com.oracle.svm.core.posix.headers.CSunMiscSignal;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Signal;
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
                            Target_java_lang_System.cons = Util_java_io_Console.toConsole(new Target_java_io_Console());
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

    @Alias private static sun.misc.JavaLangAccess javaLangAccess;

    @Substitute
    public static sun.misc.JavaLangAccess getJavaLangAccess() {
        return javaLangAccess;
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

    /** A map from signal numbers to handlers. */
    private static SignalState[] signalState = null;

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
        /* If the dispatcher is the CSunMiscSignal handler, then check if the signal is in range. */
        if ((newDispatcher == CSunMiscSignal.countingHandlerFunctionPointer()) && (CSunMiscSignal.signalRangeCheck(sig) != 1)) {
            return sunMiscSignalErrorHandler;
        }
        updateDispatcher(sig, newDispatcher);
        final Signal.SignalDispatcher oldDispatcher = Signal.signal(sig, newDispatcher);
        final long result = dispatcherToNativeH(oldDispatcher);
        return result;
    }

    /** Runtime initialization. */
    private static void ensureInitialized() throws IllegalArgumentException {
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
                    /* Open the C signal handling mechanism. */
                    final int openResult = CSunMiscSignal.open();
                    if (openResult != 0) {
                        final int openErrno = Errno.errno();
                        /* Check for the C signal handling mechanism already being open. */
                        if (openErrno == Errno.EBUSY()) {
                            throw new IllegalArgumentException("C signal handling mechanism is in use.");
                        }
                        /* Report other failure. */
                        Log.log().string("Util_sun_misc_Signal.ensureInitialized: CSunMiscSignal.create() failed.")
                                        .string("  errno: ").signed(openErrno).string("  ").string(Errno.strerror(openErrno)).newline();
                        VMError.guarantee(false, "Util_sun_misc_Signal.ensureInitialized: CSunMiscSignal.open() failed.");
                    }

                    /* Allocate the table of signal states. */
                    final int signalCount = Signal.SignalEnum.values().length;
                    /* Workaround for GR-7858: @Platform @CEnum members. */
                    final int linuxSignalCount = IsDefined.isLinux() ? Signal.LinuxSignalEnum.values().length : 0;
                    /* Initialize the table of signal states. */
                    signalState = new SignalState[signalCount + linuxSignalCount];
                    for (int index = 0; index < signalCount; index += 1) {
                        final Signal.SignalEnum value = Signal.SignalEnum.values()[index];
                        signalState[index] = new SignalState(value.name(), value.getCValue());
                    }
                    /* Workaround for GR-7858: @Platform @CEnum members. */
                    if (IsDefined.isLinux()) {
                        for (int index = 0; index < linuxSignalCount; index += 1) {
                            final Signal.LinuxSignalEnum value = Signal.LinuxSignalEnum.values()[index];
                            signalState[signalCount + index] = new SignalState(value.name(), value.getCValue());
                        }
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
    protected static int numberFromName(String javaSignalName) {
        ensureInitialized();
        /* Java deals in signal names without the leading "SIG" prefix, but C uses it. */
        final String cSignalName = "SIG" + javaSignalName;
        for (int index = 0; index < signalState.length; index += 1) {
            final SignalState entry = signalState[index];
            if (entry.getName().equals(cSignalName)) {
                return entry.getNumber();
            }
        }
        /* {@link sun.misc.Signal#findSignal(String)} expects a -1 on failure. */
        return -1;
    }

    /** Update the dispatcher of an entry in the signal state table. */
    private static void updateDispatcher(int sig, Signal.SignalDispatcher dispatcher) {
        for (int index = 0; index < signalState.length; index += 1) {
            final SignalState entry = signalState[index];
            if (entry.getNumber() == sig) {
                entry.setDispatcher(dispatcher);
                return;
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
            result = CSunMiscSignal.countingHandlerFunctionPointer();
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
        } else if (handler == CSunMiscSignal.countingHandlerFunctionPointer()) {
            result = sunMiscSignalDispatchHandler;
        } else if (handler == Signal.SIG_ERR()) {
            result = sunMiscSignalErrorHandler;
        } else {
            result = handler.rawValue();
        }
        return result;
    }

    /** A runnable to notice when signals have been raised. */
    protected static final class DispatchThread implements Runnable {

        protected DispatchThread() {
            /* Nothing to do. */
        }

        /**
         * Wait to be notified that a signal has been raised in the C signal handler, then find any
         * that were raised and dispatch to the Java signal handler. The C signal handler increments
         * the counts and this method decrements them.
         */
        @Override
        public void run() {
            for (; /* break */;) {
                /* Check if this thread was interrupted before blocking. */
                if (Thread.interrupted()) {
                    break;
                }
                /* Block waiting for one or more signals to be raised. Or a random wake up. */
                SignalState.await();
                /* Find any counters that are non-zero. */
                for (int index = 0; index < signalState.length; index += 1) {
                    final SignalState entry = signalState[index];
                    final Signal.SignalDispatcher dispatcher = entry.getDispatcher();
                    /* If the handler is the Java signal handler ... */
                    if (dispatcher.equal(CSunMiscSignal.countingHandlerFunctionPointer())) {
                        /* ... and if there are outstanding signals to be dispatched. */
                        if (entry.decrementCount() > 0L) {
                            Target_sun_misc_Signal.dispatch(entry.getNumber());
                        }
                    }
                }
            }
            /* If this thread is exiting, then the C signal handling mechanism can be closed. */
            CSunMiscSignal.close();
        }
    }

    /**
     * An entry in a table of signal numbers and handlers. There is a parallel table of counts of
     * outstanding signals maintained in C. There are convenience method here for accessing those
     * counts.
     */
    private static final class SignalState {

        /** The C signal name. */
        private final String name;
        /** The C signal number. */
        private final int number;
        /** The C signal handler. */
        private Signal.SignalDispatcher dispatcher;

        /** This just allocates an entry. The entry is initialized at runtime. */
        protected SignalState(String cName, int cValue) {
            this.name = cName;
            this.number = cValue;
            this.dispatcher = Signal.SIG_DFL();
        }

        protected String getName() {
            return name;
        }

        protected int getNumber() {
            return number;
        }

        protected Signal.SignalDispatcher getDispatcher() {
            return dispatcher;
        }

        protected void setDispatcher(Signal.SignalDispatcher value) {
            dispatcher = value;
        }

        /*
         * Convenient access to C functions, with checks for success.
         */

        protected static void await() {
            final int awaitResult = CSunMiscSignal.await();
            PosixUtils.checkStatusIs0(awaitResult, "Util_sun_misc_Signal.SignalState.await(): CSunMiscSignal.await() failed.");
        }

        /*
         * Decrement a counter towards zero. Returns the original value, or -1 if the signal number
         * is out of range.
         */
        protected long decrementCount() {
            /* Not checking the result. */
            return CSunMiscSignal.decrementCount(number);
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
