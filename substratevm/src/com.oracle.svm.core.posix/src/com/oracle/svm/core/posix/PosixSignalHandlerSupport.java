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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
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
import com.oracle.svm.core.thread.NativeSpinLockUtils;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;

/**
 * The signal handler mechanism exists only once per process. We allow multiple isolates to install
 * native signal handlers but only a single isolate at a time may use the Java signal handler
 * mechanism.
 *
 * For the Java signal handler mechanism, we install a single native signal handler that is written
 * in C (see {@link CSunMiscSignal} and the corresponding C implementation). Once it receives a
 * signal, it increments a counter and notifies the {@link DispatcherThread}. This Java thread
 * (which is started on demand) then calls into the JDK, which eventually executes the actual Java
 * signal handler code in another thread.
 *
 * NOTE: when installing or querying native signal handlers, we use a process-wide lock to avoid
 * races between isolates.
 */
@AutomaticallyRegisteredImageSingleton({SignalHandlerSupport.class, PosixSignalHandlerSupport.class})
public final class PosixSignalHandlerSupport implements SignalHandlerSupport {
    static final CGlobalData<CIntPointer> LOCK = CGlobalDataFactory.createBytes(() -> SizeOf.get(CIntPointer.class));

    /**
     * Note that aliases are allowed in this map, i.e., different signal names may have the same C
     * signal number.
     */
    private Map<String, Integer> signalNameToSignalNum;
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

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("hiding")
    void setData(Map<String, Integer> signalNameToSignalNum, boolean[] supportedSignals) {
        assert this.signalNameToSignalNum == null && this.supportedSignals == null;
        this.signalNameToSignalNum = signalNameToSignalNum;
        this.supportedSignals = supportedSignals;
    }

    public int findSignal(String signalName) {
        Integer result = signalNameToSignalNum.get(signalName);
        if (result != null) {
            return result;
        }
        return -1;
    }

    @Override
    public long installJavaSignalHandler(int sig, long nativeH) {
        assert MonitorSupport.singleton().isLockedByCurrentThread(Target_jdk_internal_misc_Signal.class);
        ensureInitialized();

        return installJavaSignalHandler0(sig, nativeH, SubstrateOptions.EnableSignalHandling.getValue());
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private static long installJavaSignalHandler0(int sig, long nativeH, boolean isSignalHandlingAllowed) {
        CIntPointer lock = LOCK.get();
        NativeSpinLockUtils.lockNoTransition(lock);
        try {
            /* If the dispatcher is the C signal handler, then check if the signal is in range. */
            SignalDispatcher newDispatcher = handlerToDispatcher(nativeH);
            if (newDispatcher == CSunMiscSignal.signalHandlerFunctionPointer() && !CSunMiscSignal.signalRangeCheck(sig)) {
                return ERROR_HANDLER;
            }

            /*
             * If the segfault handler is registered, then the user cannot override this handler
             * within Java code.
             */
            if (sig == SignalEnum.SIGSEGV.getCValue() || sig == SignalEnum.SIGBUS.getCValue()) {
                PointerBase currentDispatcher = getCurrentDispatcher(sig);
                if (currentDispatcher.equal(PosixSubstrateSegfaultHandler.SIGNAL_HANDLER.getFunctionPointer())) {
                    return ERROR_HANDLER;
                }
            }

            /*
             * If the following signals are ignored, then a handler should not be registered for
             * them.
             */
            if (sig == SignalEnum.SIGHUP.getCValue() || sig == SignalEnum.SIGINT.getCValue() || sig == SignalEnum.SIGTERM.getCValue()) {
                PointerBase currentDispatcher = getCurrentDispatcher(sig);
                if (currentDispatcher == Signal.SIG_IGN()) {
                    return IGNORE_HANDLER;
                }
            }

            /* Install the signal handler and unblock the signal. */
            SignalDispatcher oldDispatcher = installNativeSignalHandler0(sig, newDispatcher, Signal.SA_RESTART(), isSignalHandlingAllowed);

            sigset_tPointer sigset = UnsafeStackValue.get(sigset_tPointer.class);
            Signal.NoTransitions.sigemptyset(sigset);
            Signal.NoTransitions.sigaddset(sigset, sig);
            Signal.NoTransitions.sigprocmask(Signal.SIG_UNBLOCK(), sigset, Word.nullPointer());

            return dispatcherToHandler(oldDispatcher);
        } finally {
            NativeSpinLockUtils.unlock(lock);
        }
    }

    private void ensureInitialized() throws IllegalArgumentException {
        assert MonitorSupport.singleton().isLockedByCurrentThread(Target_jdk_internal_misc_Signal.class);
        if (initialized) {
            return;
        }

        int code = CSunMiscSignal.open();
        if (code == 0) {
            startDispatcherThread();
            initialized = true;
        } else if (code == 1) {
            throw new IllegalArgumentException("Java signal handler mechanism is already used by another isolate.");
        } else {
            int errno = LibC.errno();
            Log.log().string("CSunMiscSignal.open() failed.").string("  errno: ").signed(errno).string("  ").string(Errno.strerror(errno)).newline();
            throw VMError.shouldNotReachHere("CSunMiscSignal.open() failed.");
        }
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
                resetSignalHandler(i);
            }
        }

        /* Shut down the signal handler mechanism so that another isolate can use it. */
        int code = CSunMiscSignal.close();
        PosixUtils.checkStatusIs0(code, "CSunMiscSignal.close() failed.");
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private static void resetSignalHandler(int sigNum) {
        CIntPointer lock = LOCK.get();
        NativeSpinLockUtils.lockNoTransition(lock);
        try {
            PointerBase currentDispatcher = getCurrentDispatcher(sigNum);
            if (currentDispatcher == CSunMiscSignal.signalHandlerFunctionPointer()) {
                SignalDispatcher newDispatcher = getDefaultDispatcher(sigNum);
                SignalDispatcher signalResult = installNativeSignalHandler0(sigNum, newDispatcher, Signal.SA_RESTART(), true);
                if (signalResult == Signal.SIG_ERR()) {
                    throw VMError.shouldNotReachHere("Failed to reset Java signal handler during isolate teardown.");
                }
            }
        } finally {
            NativeSpinLockUtils.unlock(lock);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static SignalDispatcher getDefaultDispatcher(int sigNum) {
        if (sigNum == SignalEnum.SIGPIPE.getCValue() || sigNum == SignalEnum.SIGXFSZ.getCValue()) {
            return IgnoreSignalsStartupHook.NOOP_SIGNAL_HANDLER.getFunctionPointer();
        }
        return Signal.SIG_DFL();
    }

    /** Map Java handler numbers to C function pointers. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
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
            return Word.pointer(nativeH);
        }
    }

    /** Map C function pointers to the Java handler numbers. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
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

    /**
     * Registers a native signal handler. This method ensures that no races can happen with the Java
     * signal handling. However, there can still be races with native code that registers signals.
     *
     * Note that signal handlers must be written in a way that they do NOT destroy the libc
     * {@code errno} value (i.e., typically, each signal needs to save and restore errno).
     *
     * WARNING: methods related to signal handling must not be called from an initialization hook
     * because the runtime option {@code EnableSignalHandling} is not necessarily initialized yet
     * when initialization hooks run. So, startup hooks are currently the earliest point where
     * execution is possible.
     */
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public static Signal.SignalDispatcher installNativeSignalHandler(int signum, Signal.SignalDispatcher handler, int flags, boolean isSignalHandlingAllowed) {
        CIntPointer lock = LOCK.get();
        NativeSpinLockUtils.lockNoTransition(lock);
        try {
            return installNativeSignalHandler0(signum, handler, flags, isSignalHandlingAllowed);
        } finally {
            NativeSpinLockUtils.unlock(lock);
        }
    }

    /** See {@link #installNativeSignalHandler(int, Signal.SignalDispatcher, int, boolean)}. */
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public static void installNativeSignalHandler(Signal.SignalEnum signum, Signal.AdvancedSignalDispatcher handler, int flags, boolean isSignalHandlingAllowed) {
        CIntPointer lock = LOCK.get();
        NativeSpinLockUtils.lockNoTransition(lock);
        try {
            installNativeSignalHandler0(signum.getCValue(), handler, flags, isSignalHandlingAllowed);
        } finally {
            NativeSpinLockUtils.unlock(lock);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static SignalDispatcher installNativeSignalHandler0(int signum, SignalDispatcher handler, int flags, boolean isSignalHandlingAllowed) {
        assert NativeSpinLockUtils.isLocked(LOCK.get());

        int structSigActionSize = SizeOf.get(Signal.sigaction.class);
        Signal.sigaction act = UnsafeStackValue.get(structSigActionSize);
        LibC.memset(act, Word.signed(0), Word.unsigned(structSigActionSize));
        act.sa_flags(flags);
        act.sa_handler(handler);

        Signal.sigaction old = UnsafeStackValue.get(Signal.sigaction.class);

        int result = sigaction(signum, act, old, isSignalHandlingAllowed);
        if (result != 0) {
            return Signal.SIG_ERR();
        }
        return old.sa_handler();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void installNativeSignalHandler0(int signum, Signal.AdvancedSignalDispatcher handler, int flags, boolean isSignalHandlingAllowed) {
        assert NativeSpinLockUtils.isLocked(LOCK.get());

        int structSigActionSize = SizeOf.get(Signal.sigaction.class);
        Signal.sigaction act = UnsafeStackValue.get(structSigActionSize);
        LibC.memset(act, Word.signed(0), Word.unsigned(structSigActionSize));
        act.sa_flags(Signal.SA_SIGINFO() | flags);
        act.sa_sigaction(handler);

        int result = sigaction(signum, act, Word.nullPointer(), isSignalHandlingAllowed);
        PosixUtils.checkStatusIs0(result, "sigaction failed in installSignalHandler().");
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static PointerBase getCurrentDispatcher(int sig) {
        assert NativeSpinLockUtils.isLocked(LOCK.get());

        Signal.sigaction handler = UnsafeStackValue.get(Signal.sigaction.class);
        Signal.sigaction(sig, Word.nullPointer(), handler);
        if ((handler.sa_flags() & Signal.SA_SIGINFO()) != Signal.SA_SIGINFO()) {
            return handler.sa_sigaction();
        }
        return handler.sa_handler();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int sigaction(int signum, Signal.sigaction structSigAction, Signal.sigaction old, boolean isSignalHandlingAllowed) {
        assert NativeSpinLockUtils.isLocked(LOCK.get());
        VMError.guarantee(isSignalHandlingAllowed, "Trying to install a signal handler while signal handling is disabled.");

        return Signal.sigaction(signum, structSigAction, old);
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
    public void duringSetup(DuringSetupAccess access) {
        setSignalData();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSupport.getRuntimeSupport().addStartupHook(new IgnoreSignalsStartupHook());
    }

    private static void setSignalData() {
        int maxSigNum = 0;
        Map<String, Integer> signalNameToSignalNum = new HashMap<>();
        for (SignalEnum sig : SignalEnum.values()) {
            int sigNum = sig.getCValue();
            maxSigNum = Math.max(sigNum, maxSigNum);
            signalNameToSignalNum.put(getJavaSignalName(sig.name()), sigNum);
        }

        if (Platform.includedIn(Platform.LINUX.class)) {
            for (Signal.LinuxSignalEnum sig : Signal.LinuxSignalEnum.values()) {
                int sigNum = sig.getCValue();
                maxSigNum = Math.max(sigNum, maxSigNum);
                signalNameToSignalNum.put(getJavaSignalName(sig.name()), sigNum);
            }
        } else if (Platform.includedIn(Platform.DARWIN.class)) {
            for (Signal.DarwinSignalEnum sig : Signal.DarwinSignalEnum.values()) {
                int sigNum = sig.getCValue();
                maxSigNum = Math.max(sigNum, maxSigNum);
                signalNameToSignalNum.put(getJavaSignalName(sig.name()), sigNum);
            }
        }

        boolean[] supportedSignals = new boolean[maxSigNum + 1];
        for (var entry : signalNameToSignalNum.entrySet()) {
            supportedSignals[entry.getValue()] = true;
        }

        /* Copy the map data into an unmodifiable map (safer and more compact). */
        Map<String, Integer> map = Map.copyOf(signalNameToSignalNum);
        PosixSignalHandlerSupport.singleton().setData(map, supportedSignals);
    }

    private static String getJavaSignalName(String name) {
        assert name.startsWith("SIG");
        return name.substring(3);
    }
}

/**
 * Ideally, this should be executed as an isolate initialization hook or even earlier during
 * startup. However, this doesn't work because some Truffle code sets the runtime option
 * {@link SubstrateOptions#EnableSignalHandling} after the isolate initialization already finished.
 */
final class IgnoreSignalsStartupHook implements RuntimeSupport.Hook {
    private static final CGlobalData<Pointer> NOOP_HANDLERS_INSTALLED = CGlobalDataFactory.createWord();
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
        boolean isSignalHandlingAllowed = SubstrateOptions.EnableSignalHandling.getValue();
        if (isSignalHandlingAllowed && isFirst()) {
            installNoopHandler(SignalEnum.SIGPIPE, isSignalHandlingAllowed);
            installNoopHandler(SignalEnum.SIGXFSZ, isSignalHandlingAllowed);
        }
    }

    private static boolean isFirst() {
        Word expected = Word.zero();
        Word actual = NOOP_HANDLERS_INSTALLED.get().compareAndSwapWord(0, expected, Word.unsigned(1), LocationIdentity.ANY_LOCATION);
        return expected == actual;
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private static void installNoopHandler(SignalEnum signal, boolean isSignalHandlingAllowed) {
        CIntPointer lock = PosixSignalHandlerSupport.LOCK.get();
        NativeSpinLockUtils.lockNoTransition(lock);
        try {
            int signum = signal.getCValue();
            PointerBase currentDispatcher = PosixSignalHandlerSupport.getCurrentDispatcher(signum);
            if (currentDispatcher == Signal.SIG_DFL()) {
                /* Replace with no-op signal handler if no custom one has already been installed. */
                SignalDispatcher newDispatcher = PosixSignalHandlerSupport.getDefaultDispatcher(signum);
                assert newDispatcher == NOOP_SIGNAL_HANDLER.getFunctionPointer();
                SignalDispatcher signalResult = PosixSignalHandlerSupport.installNativeSignalHandler0(signum, newDispatcher, Signal.SA_RESTART(), isSignalHandlingAllowed);
                VMError.guarantee(signalResult != Signal.SIG_ERR(), "IgnoreSignalsStartupHook: Could not install signal handler");
            }
        } finally {
            NativeSpinLockUtils.unlock(lock);
        }
    }
}
