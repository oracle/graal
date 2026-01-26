/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.posix.cosmo;

import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.posix.cosmo.headers.Signal;
import com.oracle.svm.core.posix.cosmo.headers.Time;
import com.oracle.svm.core.sampler.SubstrateSigprofHandler;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.UserError;
import org.graalvm.word.impl.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.posix.PosixSubstrateSigprofHandler.Options.SignalHandlerBasedExecutionSampler;

/**
 * <p>
 * This class serves as the core for POSIX-based SIGPROF signal handlers.
 * </p>
 *
 * <p>
 * POSIX supports two types of timers: the global timer and per-thread timer. Both timers can
 * interrupt threads that are blocked. This may result in situations where the VM operation changes
 * unexpectedly while a thread executes signal handler code:
 * <ul>
 * <li>Thread A requests a safepoint.
 * <li>Thread B is blocked because of the safepoint but the VM did not start executing the VM
 * operation yet (i.e., there is no VM operation in progress).
 * <li>Thread B receives a SIGPROF signal and starts executing the signal handler.
 * <li>The VM reaches a safepoint and thread A starts executing the VM operation.
 * <li>Thread B continues executing the signal handler while the VM operation is now suddenly in
 * progress.
 * </ul>
 * </p>
 */
public final class CosmoSubstrateSigprofHandler extends SubstrateSigprofHandler {
    private static final long MARKER = 1L << (Long.SIZE - 1);
    private static final FastThreadLocalWord<UnsignedWord> samplerTimerId = FastThreadLocalFactory.createWord("LinuxSubstrateSigprofHandler.samplerTimerId");

    private static final CEntryPointLiteral<Signal.AdvancedSignalDispatcher> advancedSignalDispatcher = CEntryPointLiteral.create(CosmoSubstrateSigprofHandler.class,
                    "dispatch", int.class, Signal.siginfo_t.class, Signal.ucontext_t.class);

    @SuppressWarnings("unused")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = CEntryPointOptions.NoPrologue.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in sigprof signal handler.")
    @Uninterruptible(reason = "Signal handler may only execute uninterruptible code.")
    private static void dispatch(@SuppressWarnings("unused") int signalNumber, @SuppressWarnings("unused") Signal.siginfo_t sigInfo, Signal.ucontext_t uContext) {
        /* We need to keep the code in this method to a minimum to avoid races. */
        int savedErrno = LibC.errno();
        try {
            if (tryEnterIsolate()) {
                CodePointer ip = (CodePointer) RegisterDumper.singleton().getIP(uContext);
                Pointer sp = (Pointer) RegisterDumper.singleton().getSP(uContext);
                tryUninterruptibleStackWalk(ip, sp, true);
            }
        } finally {
            LibC.setErrno(savedErrno);
        }
    }

    @Override
    protected void installSignalHandler() {
        CosmoSignalHandlerSupport.installNativeSignalHandler(Signal.SignalEnum.SIGPROF, advancedSignalDispatcher.getFunctionPointer(), Signal.SA_RESTART(),
                        SubstrateOptions.isSignalHandlingAllowed());
    }

    static boolean isSignalHandlerBasedExecutionSamplerEnabled() {
        if (SignalHandlerBasedExecutionSampler.hasBeenSet()) {
            return SignalHandlerBasedExecutionSampler.getValue();
        } else {
            return isPlatformSupported();
        }
    }

    private static boolean isPlatformSupported() {
        return SubstrateOptions.isSignalHandlingAllowed();
    }

    private static void validateSamplerOption(HostedOptionKey<Boolean> isSamplerEnabled) {
        if (isSamplerEnabled.hasBeenSet() && isSamplerEnabled.getValue()) {
            UserError.guarantee(isPlatformSupported(),
                            "The %s cannot be used to profile on this platform.",
                            SubstrateOptionsParser.commandArgument(isSamplerEnabled, "+"));
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public CosmoSubstrateSigprofHandler() {
    }

    @Override
    protected void updateInterval() {
        for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            updateInterval(thread);
        }
    }

    @Override
    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    protected void install0(IsolateThread thread) {
        assert !hasSamplerTimerId(thread);
        assert SizeOf.get(Time.timer_t.class) == Integer.BYTES || SizeOf.get(Time.timer_t.class) == Long.BYTES;

        com.oracle.svm.core.posix.cosmo.headers.Signal.sigevent sigevent = StackValue.get(com.oracle.svm.core.posix.cosmo.headers.Signal.sigevent.class);
        sigevent.sigev_notify(com.oracle.svm.core.posix.cosmo.headers.Signal.SIGEV_SIGNAL());
        sigevent.sigev_signo(com.oracle.svm.core.posix.cosmo.headers.Signal.SignalEnum.SIGPROF.getCValue());
        WordPointer timerPointer = StackValue.get(WordPointer.class);
        timerPointer.write(Word.zero());

        int status = Time.NoTransitions.timer_create(Time.CLOCK_MONOTONIC(), sigevent, timerPointer);
        CosmoUtils.checkStatusIs0(status, "timer_create(clockid, sevp, timerid): wrong arguments.");
        setSamplerTimerId(thread, timerPointer.read());
        updateInterval(thread);
    }

    @Override
    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    protected void uninstall0(IsolateThread thread) {
        assert hasSamplerTimerId(thread);

        int status = Time.NoTransitions.timer_delete(getSamplerTimerId(thread));
        CosmoUtils.checkStatusIs0(status, "timer_delete(clockid): wrong arguments.");
        clearSamplerTimerId(thread);
    }

    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    private void updateInterval(IsolateThread thread) {
        assert hasSamplerTimerId(thread);

        long ns = TimeUtils.millisToNanos(newIntervalMillis);
        Time.itimerspec newTimerSpec = UnsafeStackValue.get(Time.itimerspec.class);
        newTimerSpec.it_value().set_tv_sec(ns / TimeUtils.nanosPerSecond);
        newTimerSpec.it_value().set_tv_nsec(ns % TimeUtils.nanosPerSecond);
        newTimerSpec.it_interval().set_tv_sec(ns / TimeUtils.nanosPerSecond);
        newTimerSpec.it_interval().set_tv_nsec(ns % TimeUtils.nanosPerSecond);

        int status = Time.NoTransitions.timer_settime(getSamplerTimerId(thread), 0, newTimerSpec, Word.nullPointer());
        CosmoUtils.checkStatusIs0(status, "timer_settime(timerid, flags, newTimerSpec, oldValue): wrong arguments.");
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean hasSamplerTimerId(IsolateThread thread) {
        assert CurrentIsolate.getCurrentThread() == thread || VMOperation.isInProgressAtSafepoint();
        return samplerTimerId.get(thread).and(Word.unsigned(MARKER)).notEqual(0);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord getSamplerTimerId(IsolateThread thread) {
        assert hasSamplerTimerId(thread);
        return samplerTimerId.get(thread).and(Word.unsigned(~MARKER));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void setSamplerTimerId(IsolateThread thread, UnsignedWord timerId) {
        assert !hasSamplerTimerId(thread) && timerId.and(Word.unsigned(MARKER)).equal(0);
        samplerTimerId.set(thread, timerId.or(Word.unsigned(MARKER)));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void clearSamplerTimerId(IsolateThread thread) {
        assert hasSamplerTimerId(thread);
        samplerTimerId.set(thread, Word.zero());
    }
}
