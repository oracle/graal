/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.CErrorNumber;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.IsDefined;
import com.oracle.svm.core.posix.headers.CSunMiscSignal;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Signal.SignalDispatcher;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.util.VMError;

@Platforms(Platform.HOSTED_ONLY.class)
class Package_jdk_internal_misc implements Function<TargetClass, String> {
    @Override
    public String apply(TargetClass annotation) {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            return "sun.misc." + annotation.className();
        } else {
            return "jdk.internal.misc." + annotation.className();
        }
    }
}

@TargetClass(classNameProvider = Package_jdk_internal_misc.class, className = "Signal")
final class Target_jdk_internal_misc_Signal {

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private static /* native */ int findSignal(String signalName) {
        return Util_jdk_internal_misc_Signal.numberFromName(signalName);
    }

    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static /* native */ int findSignal0(String signalName) {
        return Util_jdk_internal_misc_Signal.numberFromName(signalName);
    }

    @Substitute
    private static long handle0(int sig, long nativeH) {
        if (ImageInfo.isSharedLibrary()) {
            throw new IllegalArgumentException("Installing signal handlers is not allowed for native-image shared libraries.");
        }
        return Util_jdk_internal_misc_Signal.handle0(sig, nativeH);
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
final class Util_jdk_internal_misc_Signal {

    /** A thread to dispatch signals as they are raised. */
    private static Thread dispatchThread = null;

    /** A lock to synchronize runtime initialization. */
    private static final ReentrantLock initializationLock = new ReentrantLock();
    /** An initialization flag. */
    private static volatile boolean initialized = false;

    /** A map from signal numbers to handlers. */
    private static SignalState[] signalState = null;

    private Util_jdk_internal_misc_Signal() {
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
        CIntPointer sigset = StackValue.get(CIntPointer.class);
        sigset.write(1 << (sig - 1));
        Signal.sigprocmask(Signal.SIG_UNBLOCK(), (Signal.sigset_tPointer) sigset, WordFactory.nullPointer());
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
                        final int openErrno = CErrorNumber.getCErrorNumber();
                        /* Check for the C signal handling mechanism already being open. */
                        if (openErrno == Errno.EBUSY()) {
                            throw new IllegalArgumentException("C signal handling mechanism is in use.");
                        }
                        /* Report other failure. */
                        Log.log().string("Util_sun_misc_Signal.ensureInitialized: CSunMiscSignal.create() failed.")
                                        .string("  errno: ").signed(openErrno).string("  ").string(Errno.strerror(openErrno)).newline();
                        throw VMError.shouldNotReachHere("Util_sun_misc_Signal.ensureInitialized: CSunMiscSignal.open() failed.");
                    }

                    /* Initialize the table of signal states. */
                    signalState = createSignalStateTable();

                    /* Create and start a daemon thread to dispatch to Java signal handlers. */
                    dispatchThread = new Thread(new DispatchThread());
                    dispatchThread.setName("Signal Dispatcher");
                    dispatchThread.setDaemon(true);
                    dispatchThread.start();
                    RuntimeSupport.getRuntimeSupport().addTearDownHook(() -> DispatchThread.interrupt(dispatchThread));

                    /* Initialization is complete. */
                    initialized = true;
                }
            } finally {
                initializationLock.unlock();
            }
        }
    }

    /**
     * Create a table of signal states. This would be straightforward, except for the
     * platform-specific signals. See GR-7858: @Platform @CEnum members.
     */
    private static SignalState[] createSignalStateTable() {
        /* Fill in the table. */
        List<SignalState> signalStateList = new ArrayList<>();
        for (Signal.SignalEnum value : Signal.SignalEnum.values()) {
            signalStateList.add(new SignalState(value.name(), value.getCValue()));
        }
        if (IsDefined.isLinux()) {
            for (Signal.LinuxSignalEnum value : Signal.LinuxSignalEnum.values()) {
                signalStateList.add(new SignalState(value.name(), value.getCValue()));
            }
        }
        if (IsDefined.isDarwin()) {
            for (Signal.DarwinSignalEnum value : Signal.DarwinSignalEnum.values()) {
                signalStateList.add(new SignalState(value.name(), value.getCValue()));
            }
        }
        final SignalState[] result = signalStateList.toArray(new SignalState[0]);
        return result;
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

        static void interrupt(Thread thread) {
            thread.interrupt();
            SignalState.wakeUp();
        }

        /**
         * Wait to be notified that a signal has been raised in the C signal handler, then find any
         * that were raised and dispatch to the Java signal handler. The C signal handler increments
         * the counts and this method decrements them.
         */
        @Override
        public void run() {
            while (!Thread.interrupted()) {
                /*
                 * Block waiting for one or more signals to be raised. Or a wake up for termination.
                 */
                SignalState.await();
                if (Thread.interrupted()) {
                    /* Thread was interrupted for termination. */
                    break;
                }
                /* Find any counters that are non-zero. */
                for (final SignalState entry : signalState) {
                    final SignalDispatcher dispatcher = entry.getDispatcher();
                    /* If the handler is the Java signal handler ... */
                    if (dispatcher.equal(CSunMiscSignal.countingHandlerFunctionPointer())) {
                        /* ... and if there are outstanding signals to be dispatched. */
                        if (entry.decrementCount() > 0L) {
                            Target_jdk_internal_misc_Signal.dispatch(entry.getNumber());
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

        protected static void wakeUp() {
            final int awaitResult = CSunMiscSignal.post();
            PosixUtils.checkStatusIs0(awaitResult, "Util_sun_misc_Signal.SignalState.post(): CSunMiscSignal.post() failed.");
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

@AutomaticFeature
class IgnoreSIGPIPEFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {

        RuntimeSupport.getRuntimeSupport().addStartupHook(new Runnable() {

            @Override
            /**
             * Ignore SIGPIPE. Reading from a closed pipe, instead of delivering a process-wide
             * signal whose default action is to terminate the process, will instead return an error
             * code from the specific write operation.
             *
             * From pipe(7}: If all file descriptors referring to the read end of a pipe have been
             * closed, then a write(2) will cause a SIGPIPE signal to be generated for the calling
             * process. If the calling process is ignoring this signal, then write(2) fails with the
             * error EPIPE.
             */
            public void run() {
                final Signal.SignalDispatcher signalResult = Signal.signal(Signal.SignalEnum.SIGPIPE.getCValue(), Signal.SIG_IGN());
                VMError.guarantee(signalResult != Signal.SIG_ERR(), "IgnoreSIGPIPEFeature.run: Could not ignore SIGPIPE");
            }
        });
    }
}

@TargetClass(className = "jdk.internal.misc.VM", onlyWith = JDK11OrLater.class)
final class Target_jdk_internal_misc_VM {

    /* Implementation from src/hotspot/share/prims/jvm.cpp#L286 translated to Java. */
    @Substitute
    public static long getNanoTimeAdjustment(long offsetInSeconds) {
        final long maxDiffSecs = 0x0100000000L;
        final long minDiffSecs = -maxDiffSecs;

        Time.timeval tv = StackValue.get(Time.timeval.class);
        int status = Time.gettimeofday(tv, WordFactory.nullPointer());
        assert status != -1 : "linux error";
        long seconds = tv.tv_sec();
        long nanos = tv.tv_usec() * 1000;

        long diff = seconds - offsetInSeconds;
        if (diff >= maxDiffSecs || diff <= minDiffSecs) {
            return -1;
        }
        return diff * 1000000000 + nanos;
    }
}

/** Dummy class to have a class with the file's name. */
public final class SunMiscSubstitutions {
}
