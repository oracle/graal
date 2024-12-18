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

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.UnsignedWord;

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

    /*
     * The samplerTimerId thread-local variable is initialized by default to 0. Since
     * LinuxTime.NoTransitions#timer_create can return 0 as a valid timer id, we introduced a
     * MARKER. This marker sets the most significant bit, allowing us to distinguish between zero as
     * not initialized and 0 as a valid timer value.
     *
     * The size of samplerTimerId could be either 4 bytes (i.e., an integer counter) or a word-sized
     * value (i.e., pointer to timer structure) depending on the OS implementation.
     */
    private static final long MARKER = 1L << (Long.SIZE - 1);
    private static final FastThreadLocalWord<UnsignedWord> samplerTimerId = FastThreadLocalFactory.createWord("LinuxSubstrateSigprofHandler.samplerTimerId");

    @Platforms(Platform.HOSTED_ONLY.class)
    public LinuxSubstrateSigprofHandler() {
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
        assert SizeOf.get(LinuxTime.timer_t.class) == Integer.BYTES || SizeOf.get(LinuxTime.timer_t.class) == Long.BYTES;

        Signal.sigevent sigevent = StackValue.get(Signal.sigevent.class);
        sigevent.sigev_notify(Signal.SIGEV_SIGNAL());
        sigevent.sigev_signo(Signal.SignalEnum.SIGPROF.getCValue());
        WordPointer timerPointer = StackValue.get(WordPointer.class);
        timerPointer.write(Word.zero());

        int status = LinuxTime.NoTransitions.timer_create(LinuxTime.CLOCK_MONOTONIC(), sigevent, timerPointer);
        PosixUtils.checkStatusIs0(status, "timer_create(clockid, sevp, timerid): wrong arguments.");
        setSamplerTimerId(thread, timerPointer.read());
        updateInterval(thread);
    }

    @Override
    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    protected void uninstall0(IsolateThread thread) {
        assert hasSamplerTimerId(thread);

        int status = LinuxTime.NoTransitions.timer_delete(getSamplerTimerId(thread));
        PosixUtils.checkStatusIs0(status, "timer_delete(clockid): wrong arguments.");
        clearSamplerTimerId(thread);
    }

    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    private void updateInterval(IsolateThread thread) {
        assert hasSamplerTimerId(thread);

        long ns = TimeUtils.millisToNanos(newIntervalMillis);
        LinuxTime.itimerspec newTimerSpec = UnsafeStackValue.get(LinuxTime.itimerspec.class);
        newTimerSpec.it_value().set_tv_sec(ns / TimeUtils.nanosPerSecond);
        newTimerSpec.it_value().set_tv_nsec(ns % TimeUtils.nanosPerSecond);
        newTimerSpec.it_interval().set_tv_sec(ns / TimeUtils.nanosPerSecond);
        newTimerSpec.it_interval().set_tv_nsec(ns % TimeUtils.nanosPerSecond);

        int status = LinuxTime.NoTransitions.timer_settime(getSamplerTimerId(thread), 0, newTimerSpec, Word.nullPointer());
        PosixUtils.checkStatusIs0(status, "timer_settime(timerid, flags, newTimerSpec, oldValue): wrong arguments.");
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
