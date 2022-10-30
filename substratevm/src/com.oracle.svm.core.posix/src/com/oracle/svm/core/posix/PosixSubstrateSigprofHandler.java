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

package com.oracle.svm.core.posix;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.sampler.SubstrateSigprofHandler;

@AutomaticallyRegisteredImageSingleton(SubstrateSigprofHandler.class)
public class PosixSubstrateSigprofHandler extends SubstrateSigprofHandler {

    public static final long INTERVAL_S = 0;
    public static final long INTERVAL_uS = 20_000;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PosixSubstrateSigprofHandler() {
    }

    /** The address of the signal handler for signals handled by Java code, below. */
    private static final CEntryPointLiteral<Signal.AdvancedSignalDispatcher> advancedSignalDispatcher = CEntryPointLiteral.create(PosixSubstrateSigprofHandler.class,
                    "dispatch", int.class, Signal.siginfo_t.class, Signal.ucontext_t.class);

    @SuppressWarnings("unused")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = CEntryPointOptions.NoPrologue.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in sigprof signal handler.")
    @Uninterruptible(reason = "Signal handler may only execute uninterruptible code.")
    private static void dispatch(@SuppressWarnings("unused") int signalNumber, @SuppressWarnings("unused") Signal.siginfo_t sigInfo, Signal.ucontext_t uContext) {
        if (tryEnterIsolate()) {
            doUninterruptibleStackWalk(uContext);
        }
    }

    private static void registerSigprofSignal() {
        int structSigActionSize = SizeOf.get(Signal.sigaction.class);
        Signal.sigaction structSigAction = UnsafeStackValue.get(structSigActionSize);
        LibC.memset(structSigAction, WordFactory.signed(0), WordFactory.unsigned(structSigActionSize));

        /* Register sa_sigaction signal handler */
        structSigAction.sa_flags(Signal.SA_SIGINFO() | Signal.SA_NODEFER() | Signal.SA_RESTART());
        structSigAction.sa_sigaction(advancedSignalDispatcher.getFunctionPointer());
        Signal.sigaction(Signal.SignalEnum.SIGPROF.getCValue(), structSigAction, WordFactory.nullPointer());
    }

    private static int callSetitimer() {
        /* Call setitimer to start profiling. */
        Time.itimerval newValue = UnsafeStackValue.get(Time.itimerval.class);
        Time.itimerval oldValue = UnsafeStackValue.get(Time.itimerval.class);

        newValue.it_value().set_tv_sec(INTERVAL_S);
        newValue.it_value().set_tv_usec(INTERVAL_uS);
        newValue.it_interval().set_tv_sec(INTERVAL_S);
        newValue.it_interval().set_tv_usec(INTERVAL_uS);

        return Time.NoTransitions.setitimer(Time.TimerTypeEnum.ITIMER_PROF, newValue, oldValue);
    }

    @Override
    protected void install0() {
        registerSigprofSignal();
        PosixUtils.checkStatusIs0(callSetitimer(), "setitimer(which, newValue, oldValue): wrong arguments.");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected UnsignedWord createThreadLocalKey() {
        Pthread.pthread_key_tPointer key = StackValue.get(Pthread.pthread_key_tPointer.class);
        PosixUtils.checkStatusIs0(Pthread.pthread_key_create(key, WordFactory.nullPointer()), "pthread_key_create(key, keyDestructor): failed.");
        return key.read();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void deleteThreadLocalKey(UnsignedWord key) {
        int resultCode = Pthread.pthread_key_delete((Pthread.pthread_key_t) key);
        PosixUtils.checkStatusIs0(resultCode, "pthread_key_delete(key): failed.");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void setThreadLocalKeyValue(UnsignedWord key, IsolateThread value) {
        int resultCode = Pthread.pthread_setspecific((Pthread.pthread_key_t) key, (VoidPointer) value);
        PosixUtils.checkStatusIs0(resultCode, "pthread_setspecific(key, value): wrong arguments.");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected IsolateThread getThreadLocalKeyValue(UnsignedWord key) {
        /*
         * Although this method is not async-signal-safe in general we rely on
         * implementation-specific behavior here.
         */
        return (IsolateThread) Pthread.pthread_getspecific((Pthread.pthread_key_t) key);
    }
}
