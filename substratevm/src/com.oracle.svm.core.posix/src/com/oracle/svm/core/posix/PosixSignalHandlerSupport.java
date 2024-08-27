/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.jdk.Target_jdk_internal_misc_Signal.Constants.DEFAULT_HANDLER;
import static com.oracle.svm.core.jdk.Target_jdk_internal_misc_Signal.Constants.DISPATCH_HANDLER;
import static com.oracle.svm.core.jdk.Target_jdk_internal_misc_Signal.Constants.ERROR_HANDLER;
import static com.oracle.svm.core.jdk.Target_jdk_internal_misc_Signal.Constants.IGNORE_HANDLER;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateSegfaultHandler;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jdk.RuntimeSupportFeature;
import com.oracle.svm.core.jdk.SignalHandlerSupport;
import com.oracle.svm.core.jdk.Target_jdk_internal_misc_Signal;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.posix.headers.CSunMiscSignal;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Signal.SignalDispatcher;
import com.oracle.svm.core.posix.headers.Signal.SignalEnum;
import com.oracle.svm.core.posix.headers.Signal.sigset_tPointer;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.nodes.extended.MembarNode;

/**
 * The signal handler mechanism exists only once per process. So, only a single isolate at a time
 * can do signal handling. The OS-level signal handler is written in C (see {@link CSunMiscSignal}
 * and the corresponding C implementation). Once it receives a signal, it increments a counter and
 * notifies the {@link DispatcherThread}. This Java thread (which is started on demand) then calls
 * the actual Java Signal handler code.
 */
@AutomaticallyRegisteredImageSingleton({SignalHandlerSupport.class, PosixSignalHandlerSupport.class})
public final class PosixSignalHandlerSupport implements SignalHandlerSupport {
    /**
     * Note that aliases are allowed in this map, i.e., different signal names may have the same C
     * signal number.
     */
    private EconomicMap<String, Integer> signalNameToSignalNum;
    private boolean[] supportedSignals;
    private DispatcherThread dispatcherThread;
    private boolean initialized;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PosixSignalHandlerSupport() {
    }

    @Fold
    public static PosixSignalHandlerSupport singleton() {
        return ImageSingletons.lookup(PosixSignalHandlerSupport.class);
    }

    /** Returns whether the currently installed signal handler matched the passed dispatcher. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean isCurrentDispatcher(int sig, SignalDispatcher dispatcher) {
        Signal.sigaction handler = UnsafeStackValue.get(Signal.sigaction.class);
        Signal.sigaction(sig, WordFactory.nullPointer(), handler);
        return handler.sa_handler() == dispatcher;
    }

    public int findSignal0(String signalName) {
        initSupportedSignals();

        Integer result = signalNameToSignalNum.get(signalName);
        if (result != null) {
            return result;
        }
        return -1;
    }

    @Override
    public long installSignalHandler(int sig, long nativeH) {
        assert MonitorSupport.singleton().isLockedByCurrentThread(Target_jdk_internal_misc_Signal.class);
        ensureInitialized();

        /* If the dispatcher is the C signal handler, then check if the signal is in range. */
        SignalDispatcher newDispatcher = handlerToDispatcher(nativeH);
        if (newDispatcher == CSunMiscSignal.signalHandlerFunctionPointer() && !CSunMiscSignal.signalRangeCheck(sig)) {
            return ERROR_HANDLER;
        }

        /*
         * If the segfault handler is registered, then the user cannot override this handler within
         * Java code.
         */
        if (SubstrateSegfaultHandler.isInstalled() && (sig == SignalEnum.SIGSEGV.getCValue() || sig == SignalEnum.SIGBUS.getCValue())) {
            return ERROR_HANDLER;
        }

        /*
         * If the following signals are ignored, then a handler should not be registered for them.
         */
        if (sig == SignalEnum.SIGHUP.getCValue() || sig == SignalEnum.SIGINT.getCValue() || sig == SignalEnum.SIGTERM.getCValue()) {
            if (isCurrentDispatcher(sig, Signal.SIG_IGN())) {
                return IGNORE_HANDLER;
            }
        }

        /* Install the signal handler and unblock the signal. */
        SignalDispatcher oldDispatcher = PosixUtils.installSignalHandler(sig, newDispatcher, Signal.SA_RESTART());

        sigset_tPointer sigset = UnsafeStackValue.get(sigset_tPointer.class);
        Signal.sigemptyset(sigset);
        Signal.sigaddset(sigset, sig);
        Signal.sigprocmask(Signal.SIG_UNBLOCK(), sigset, WordFactory.nullPointer());

        return dispatcherToHandler(oldDispatcher);
    }

    private void ensureInitialized() throws IllegalArgumentException {
        assert MonitorSupport.singleton().isLockedByCurrentThread(Target_jdk_internal_misc_Signal.class);
        if (initialized) {
            return;
        }

        initSupportedSignals();

        int code = CSunMiscSignal.open();
        if (code == 0) {
            startDispatcherThread();
            initialized = true;
        } else if (code == 1) {
            throw new IllegalArgumentException("C signal handling mechanism is in use.");
        } else {
            int errno = LibC.errno();
            Log.log().string("CSunMiscSignal.open() failed.").string("  errno: ").signed(errno).string("  ").string(Errno.strerror(errno)).newline();
            throw VMError.shouldNotReachHere("CSunMiscSignal.open() failed.");
        }
    }

    /** May be executed by multiple threads but each thread will compute the same data. */
    private void initSupportedSignals() {
        if (signalNameToSignalNum != null && supportedSignals != null) {
            return;
        }

        int maxSigNum = 0;
        EconomicMap<String, Integer> map = EconomicMap.create();
        for (SignalEnum value : SignalEnum.values()) {
            maxSigNum = Math.max(value.getCValue(), maxSigNum);
            map.put(getJavaSignalName(value.name()), value.getCValue());
        }

        if (Platform.includedIn(Platform.LINUX.class)) {
            for (Signal.LinuxSignalEnum value : Signal.LinuxSignalEnum.values()) {
                maxSigNum = Math.max(value.getCValue(), maxSigNum);
                map.put(getJavaSignalName(value.name()), value.getCValue());
            }
        } else if (Platform.includedIn(Platform.DARWIN.class)) {
            for (Signal.DarwinSignalEnum value : Signal.DarwinSignalEnum.values()) {
                maxSigNum = Math.max(value.getCValue(), maxSigNum);
                map.put(getJavaSignalName(value.name()), value.getCValue());
            }
        }

        boolean[] signals = new boolean[maxSigNum + 1];
        MapCursor<String, Integer> cursor = map.getEntries();
        while (cursor.advance()) {
            signals[cursor.getValue()] = true;
        }

        MembarNode.memoryBarrier(MembarNode.FenceKind.STORE_STORE);
        signalNameToSignalNum = map;
        supportedSignals = signals;
    }

    private static String getJavaSignalName(String name) {
        assert name.startsWith("SIG");
        return name.substring(3);
    }

    private void startDispatcherThread() {
        dispatcherThread = new DispatcherThread();
        dispatcherThread.start();
    }

    @Override
    public void stopDispatcherThread() {
        if (!initialized) {
            return;
        }

        /* Notify the dispatcher thread that it should stop and wait until it exits. */
        dispatcherThread.stopped = true;

        int code = CSunMiscSignal.signalSemaphore();
        PosixUtils.checkStatusIs0(code, "CSunMiscSignal.signalSemaphore() failed.");

        try {
            dispatcherThread.join();
        } catch (InterruptedException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public void onIsolateTeardown() {
        if (!initialized) {
            return;
        }

        /*
         * Unregister all Java signal handlers. Note that native signal handlers (e.g., segfault
         * handler, SIGPIPE noop handler) remain installed though. This can cause problems when
         * unloading shared libraries (see GR-57977).
         */
        for (int i = 0; i < supportedSignals.length; i++) {
            if (supportedSignals[i]) {
                boolean hasJavaSignalHandler = isCurrentDispatcher(i, CSunMiscSignal.signalHandlerFunctionPointer());
                if (hasJavaSignalHandler) {
                    SignalDispatcher dispatcher = getDefaultDispatcher(i);
                    SignalDispatcher signalResult = PosixUtils.installSignalHandlerUnsafe(i, dispatcher, Signal.SA_RESTART(), true);
                    if (signalResult == Signal.SIG_ERR()) {
                        throw VMError.shouldNotReachHere("Failed to unregister Java signal handlers during isolate teardown.");
                    }
                }
            }
        }

        /* Shut down the signal handling mechanism so that another isolate can use it. */
        int code = CSunMiscSignal.close();
        PosixUtils.checkStatusIs0(code, "CSunMiscSignal.close() failed.");
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static SignalDispatcher getDefaultDispatcher(int sigNum) {
        if (sigNum == SignalEnum.SIGPIPE.getCValue() || sigNum == SignalEnum.SIGXFSZ.getCValue()) {
            return IgnoreSignalsStartupHook.NOOP_SIGNAL_HANDLER.getFunctionPointer();
        }
        return Signal.SIG_DFL();
    }

    /** Map Java handler numbers to C function pointers. */
    private static SignalDispatcher handlerToDispatcher(long nativeH) {
        if (nativeH == DEFAULT_HANDLER) {
            return Signal.SIG_DFL();
        } else if (nativeH == IGNORE_HANDLER) {
            return Signal.SIG_IGN();
        } else if (nativeH == DISPATCH_HANDLER) {
            return CSunMiscSignal.signalHandlerFunctionPointer();
        } else if (nativeH == ERROR_HANDLER) {
            return Signal.SIG_ERR();
        } else {
            return WordFactory.pointer(nativeH);
        }
    }

    /** Map C function pointers to the Java handler numbers. */
    private static long dispatcherToHandler(SignalDispatcher handler) {
        if (handler == Signal.SIG_DFL()) {
            return DEFAULT_HANDLER;
        } else if (handler == Signal.SIG_IGN()) {
            return IGNORE_HANDLER;
        } else if (handler == CSunMiscSignal.signalHandlerFunctionPointer()) {
            return DISPATCH_HANDLER;
        } else if (handler == Signal.SIG_ERR()) {
            return ERROR_HANDLER;
        } else {
            return handler.rawValue();
        }
    }

    private static class DispatcherThread extends Thread {
        private volatile boolean stopped;

        DispatcherThread() {
            super(PlatformThreads.singleton().systemGroup, "Signal Dispatcher");
            this.setDaemon(true);
        }

        @Override
        public void run() {
            while (!stopped) {
                int code = CSunMiscSignal.awaitSemaphore();
                PosixUtils.checkStatusIs0(code, "CSunMiscSignal.awaitSemaphore() failed.");

                int sigNum = CSunMiscSignal.checkPendingSignal();
                if (sigNum >= 0) {
                    com.oracle.svm.core.jdk.Target_jdk_internal_misc_Signal.dispatch(sigNum);
                }
            }
        }
    }
}

@AutomaticallyRegisteredFeature
class PosixSignalHandlerFeature implements InternalFeature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(RuntimeSupportFeature.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSupport.getRuntimeSupport().addStartupHook(new IgnoreSignalsStartupHook());
    }
}

/**
 * Ideally, this should be executed as an isolate initialization hook or even earlier during
 * startup. However, this doesn't work because some Truffle code sets the runtime option
 * {@link SubstrateOptions#EnableSignalHandling} after the isolate initialization already finished.
 */
final class IgnoreSignalsStartupHook implements RuntimeSupport.Hook {
    static final CEntryPointLiteral<SignalDispatcher> NOOP_SIGNAL_HANDLER = CEntryPointLiteral.create(IgnoreSignalsStartupHook.class, "noopSignalHandler", int.class);

    @CEntryPoint(publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    @Uninterruptible(reason = "empty signal handler, Isolate is not set up")
    static void noopSignalHandler(@SuppressWarnings("unused") int sig) {
        /* noop - so no need to save/restore errno because its value can't be destroyed. */
    }

    /**
     * HotSpot ignores the SIGPIPE and SIGXFSZ signals (see <a
     * href=https://github.com/openjdk/jdk/blob/fc76687c2fac39fcbf706c419bfa170b8efa5747/src/hotspot/os/posix/signals_posix.cpp#L608>signals_posix.cpp</a>).
     * When signal handling is enabled we do the same thing.
     * <p>
     * Ignore SIGPIPE. Reading from a closed pipe, instead of delivering a process-wide signal whose
     * default action is to terminate the process, will instead return an error code from the
     * specific write operation.
     * <p>
     * From pipe(7): If all file descriptors referring to the read end of a pipe have been closed,
     * then a write(2) will cause a SIGPIPE signal to be generated for the calling process. If the
     * calling process is ignoring this signal, then write(2) fails with the error EPIPE.
     * <p>
     * Note that the handler must be an empty function and not SIG_IGN. The problem is SIG_IGN is
     * inherited to subprocess but we only want to affect the current process.
     * <p>
     * From signal(7): A child created via fork(2) inherits a copy of its parent's signal
     * dispositions. During an execve(2), the dispositions of handled signals are reset to the
     * default; the dispositions of ignored signals are left unchanged.
     */
    @Override
    public void execute(boolean isFirstIsolate) {
        // TEMP (chaeubl): think about this - must be executed for each isolate
        if (SubstrateOptions.EnableSignalHandling.getValue()) {
            /* Synchronized to avoid races with Signal.handle0(...) */
            synchronized (Target_jdk_internal_misc_Signal.class) {
                installNoopHandler(SignalEnum.SIGPIPE);
                installNoopHandler(SignalEnum.SIGXFSZ);
            }
        }
    }

    private static void installNoopHandler(SignalEnum signal) {
        int signum = signal.getCValue();
        if (PosixSignalHandlerSupport.isCurrentDispatcher(signum, Signal.SIG_DFL())) {
            /* Replace with no-op signal handler if a custom one has not already been installed. */
            SignalDispatcher dispatcher = PosixSignalHandlerSupport.getDefaultDispatcher(signum);
            assert dispatcher == NOOP_SIGNAL_HANDLER.getFunctionPointer();
            SignalDispatcher signalResult = PosixUtils.installSignalHandler(signum, dispatcher, Signal.SA_RESTART());
            if (signalResult == Signal.SIG_ERR()) {
                throw VMError.shouldNotReachHere("IgnoreSignalsStartupHook: Could not install signal: " + signal);
            }
        }
    }
}
