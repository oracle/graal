/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.posix.linux;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.posix.PosixSubstrateSigprofHandler;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.linux.LinuxTime;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.util.TimeUtils;

/**
 * Linux supports both types of timers (see {@link PosixSubstrateSigprofHandler}). We use the
 * per-thread timer as it increases the number of recorded samples and provides more reliable
 * sampling.
 */
public final class LinuxSubstrateSigprofHandler extends PosixSubstrateSigprofHandler {

    private static final int INITIAL_SAMPLER_TIMER_ID = -1;
    private static final FastThreadLocalWord<LinuxTime.timer_t> samplerTimerId = FastThreadLocalFactory.createWord("LinuxSubstrateSigprofHandler.samplerTimerId");

    @Platforms(Platform.HOSTED_ONLY.class)
    public LinuxSubstrateSigprofHandler() {
    }

    @Override
    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    public void beforeThreadStart(IsolateThread isolateThread, Thread javaThread) {
        setSamplerTimerId(isolateThread, WordFactory.signed(INITIAL_SAMPLER_TIMER_ID));
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
        assert !isSamplerTimerSet(thread);

        Signal.sigevent sigevent = StackValue.get(Signal.sigevent.class);
        sigevent.sigev_notify(Signal.SIGEV_SIGNAL());
        sigevent.sigev_signo(Signal.SIGPROF());
        LinuxTime.timer_tPointer timerPointer = StackValue.get(LinuxTime.timer_tPointer.class);

        int status = LinuxTime.NoTransitions.timer_create(LinuxTime.CLOCK_MONOTONIC(), sigevent, timerPointer);
        PosixUtils.checkStatusIs0(status, "timer_create(clockid, sevp, timerid): wrong arguments.");
        setSamplerTimerId(thread, timerPointer.read());
        updateInterval(thread);
    }

    @Override
    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    protected void uninstall0(IsolateThread thread) {
        assert isSamplerTimerSet(thread);

        int status = LinuxTime.NoTransitions.timer_delete(getSamplerTimerId(thread));
        PosixUtils.checkStatusIs0(status, "timer_delete(clockid): wrong arguments.");
        setSamplerTimerId(thread, WordFactory.signed(INITIAL_SAMPLER_TIMER_ID));
    }

    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    private void updateInterval(IsolateThread thread) {
        assert isSamplerTimerSet(thread);

        long ns = TimeUtils.millisToNanos(newIntervalMillis);
        LinuxTime.itimerspec newTimerSpec = UnsafeStackValue.get(LinuxTime.itimerspec.class);
        newTimerSpec.it_value().set_tv_sec(ns / TimeUtils.nanosPerSecond);
        newTimerSpec.it_value().set_tv_nsec(ns % TimeUtils.nanosPerSecond);
        newTimerSpec.it_interval().set_tv_sec(ns / TimeUtils.nanosPerSecond);
        newTimerSpec.it_interval().set_tv_nsec(ns % TimeUtils.nanosPerSecond);

        int status = LinuxTime.NoTransitions.timer_settime(getSamplerTimerId(thread), 0, newTimerSpec, WordFactory.nullPointer());
        PosixUtils.checkStatusIs0(status, "timer_settime(timerid, flags, newTimerSpec, oldValue): wrong arguments.");
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isSamplerTimerSet(IsolateThread thread) {
        return getSamplerTimerId(thread).notEqual(WordFactory.signed(INITIAL_SAMPLER_TIMER_ID));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static LinuxTime.timer_t getSamplerTimerId(IsolateThread thread) {
        assert CurrentIsolate.getCurrentThread() == thread || VMOperation.isInProgressAtSafepoint();
        return samplerTimerId.get(thread);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void setSamplerTimerId(IsolateThread thread, LinuxTime.timer_t timerId) {
        assert CurrentIsolate.getCurrentThread() == thread || VMOperation.isInProgressAtSafepoint();
        samplerTimerId.set(thread, timerId);
    }
}
